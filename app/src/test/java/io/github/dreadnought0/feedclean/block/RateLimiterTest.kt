package io.github.dreadnought0.feedclean.block

import io.github.dreadnought0.feedclean.data.ActionKind
import io.github.dreadnought0.feedclean.data.ActionLogRepository
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import io.github.dreadnought0.feedclean.testutil.FakeActionLogRepository
import io.github.dreadnought0.feedclean.testutil.FakeActionLogRepository.Entry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RateLimiterTest {

    private val log = FakeActionLogRepository()
    private var now = BASE
    private var settings = FeedCleanSettings()
    private val limiter = newLimiter()

    private fun newLimiter() = RateLimiter(log, { settings }, { now })

    private suspend fun RateLimiter.runOk(kind: ActionKind = ActionKind.BLOCK, accountId: Long? = null) =
        runWithPermit(kind, accountId, isVerified = { it: Boolean -> it }) { true }

    @Test
    fun firstActionIsAllowed() = runTest {
        assertEquals(RateDecision.Allowed, limiter.check())
    }

    @Test
    fun defaultMinimumIntervalIsFiveSeconds() = runTest {
        log.seed(now - 4_999)
        assertEquals(RateDecision.Denied(1, DenyReason.MIN_INTERVAL), limiter.check())

        now += 1
        assertEquals(RateDecision.Allowed, limiter.check())
    }

    @Test
    fun minimumIntervalCountsFromTheLastFinishedAttempt() = runTest {
        val outcome = limiter.runWithPermit(ActionKind.BLOCK, null, isVerified = { _: Unit -> true }) {
            now += 4_000 // the action itself takes 4 s
        }
        assertTrue(outcome is PermitOutcome.Ran)
        assertEquals(BASE + 4_000, log.entries.single().atMillis)

        now += 4_999
        assertEquals(RateDecision.Denied(1, DenyReason.MIN_INTERVAL), limiter.check())
    }

    @Test
    fun budgetSurvivesARestart() = runTest {
        limiter.runOk()

        val afterRestart = newLimiter()

        assertEquals(RateDecision.Denied(5_000, DenyReason.MIN_INTERVAL), afterRestart.check())
    }

    @Test
    fun hourlyCapWithExactRetryTime() = runTest {
        settings = FeedCleanSettings(maxActionsPerHour = 3)
        log.seed(BASE, BASE + 10_000, BASE + 20_000)
        now = BASE + 30_000

        // The oldest attempt (at BASE) leaves the trailing hour at BASE + 1 h + 1 ms.
        val expectedRetry = BASE + 3_600_001 - now
        assertEquals(RateDecision.Denied(expectedRetry, DenyReason.HOURLY_CAP), limiter.check())

        now += expectedRetry - 1
        assertEquals(RateDecision.Denied(1, DenyReason.HOURLY_CAP), limiter.check())
        now += 1
        assertEquals(RateDecision.Allowed, limiter.check())
    }

    @Test
    fun floorsApplyEvenToUnsanitizedSettings() = runTest {
        settings = FeedCleanSettings(minSecondsBetweenActions = 0, maxActionsPerHour = 1_000)

        log.seed(now - 2_000)
        assertEquals(RateDecision.Denied(1_000, DenyReason.MIN_INTERVAL), limiter.check())

        now += 1_000
        assertEquals(RateDecision.Allowed, limiter.check())
    }

    @Test
    fun hourlyCapNeverExceeds120() = runTest {
        settings = FeedCleanSettings(maxActionsPerHour = 1_000)
        // 120 attempts in the last 20 minutes, the latest well outside the minimum interval.
        log.seed(*LongArray(120) { now - 1_200_000 + it * 10_000L - 60_000 })

        val decision = limiter.check()

        assertTrue(decision is RateDecision.Denied && decision.reason == DenyReason.HOURLY_CAP)
    }

    @Test
    fun deniedRequestNeitherRunsNorRecords() = runTest {
        log.seed(now - 1_000)
        var ran = false

        val outcome = limiter.runWithPermit(ActionKind.BLOCK, null, isVerified = { _: Unit -> true }) { ran = true }

        assertEquals(PermitOutcome.Denied(RateDecision.Denied(4_000, DenyReason.MIN_INTERVAL)), outcome)
        assertFalse(ran)
        assertEquals(1, log.entries.size)
    }

    @Test
    fun inFlightActionBlocksConcurrentAttempts() = runTest {
        val release = CompletableDeferred<Unit>()
        val first = async {
            limiter.runWithPermit(ActionKind.BLOCK, 1L, isVerified = { _: Unit -> true }) { release.await() }
        }
        runCurrent()

        assertTrue(limiter.isActionInFlight)
        assertEquals(RateDecision.Denied(5_000, DenyReason.ACTION_IN_PROGRESS), limiter.check())
        var secondRan = false
        val second = limiter.runWithPermit(ActionKind.NOT_INTERESTED, 2L, isVerified = { _: Unit -> true }) {
            secondRan = true
        }
        assertEquals(PermitOutcome.Denied(RateDecision.Denied(5_000, DenyReason.ACTION_IN_PROGRESS)), second)
        assertFalse(secondRan)

        release.complete(Unit)
        first.await()
        assertFalse(limiter.isActionInFlight)
        assertEquals(listOf(Entry(ActionKind.BLOCK, BASE, 1L, true)), log.entries)
    }

    @Test
    fun failedAttemptIsRecordedUnverified() = runTest {
        limiter.runWithPermit(ActionKind.BLOCK, 7L, isVerified = { it: Boolean -> it }) { false }

        assertEquals(listOf(Entry(ActionKind.BLOCK, BASE, 7L, false)), log.entries)
    }

    @Test
    fun throwingAttemptIsRecordedAndRethrown() = runTest {
        val result = runCatching {
            limiter.runWithPermit(ActionKind.BLOCK, null, isVerified = { _: Unit -> true }) {
                throw IllegalStateException("boom")
            }
        }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(listOf(Entry(ActionKind.BLOCK, BASE, null, false)), log.entries)
        assertFalse(limiter.isActionInFlight)
    }

    @Test
    fun cancelledAttemptIsRecordedAndReleasesThePermit() = runTest {
        val job = launch {
            limiter.runWithPermit(ActionKind.NOT_INTERESTED, null, isVerified = { _: Unit -> true }) {
                awaitCancellation()
            }
        }
        runCurrent()
        job.cancel()
        job.join()

        assertEquals(listOf(Entry(ActionKind.NOT_INTERESTED, BASE, null, false)), log.entries)
        assertFalse(limiter.isActionInFlight)
        assertEquals(RateDecision.Denied(5_000, DenyReason.MIN_INTERVAL), limiter.check())
    }

    @Test
    fun blocksAndNotInterestedShareOneBudget() = runTest {
        limiter.runOk(ActionKind.BLOCK)
        now += 2_000

        val outcome = limiter.runOk(ActionKind.NOT_INTERESTED)

        assertEquals(PermitOutcome.Denied(RateDecision.Denied(3_000, DenyReason.MIN_INTERVAL)), outcome)
    }

    @Test
    fun inMemoryAttemptsCountEvenIfThePersistedLogLags() = runTest {
        // A repository whose writes never land (e.g. a failing DB) must not reopen the gate.
        val lossyLog = object : ActionLogRepository by log {
            override suspend fun record(kind: ActionKind, atMillis: Long, accountId: Long?, verified: Boolean) = Unit
        }
        val lossyLimiter = RateLimiter(lossyLog, { settings }, { now })

        lossyLimiter.runOk()
        now += 1_000

        assertEquals(RateDecision.Denied(4_000, DenyReason.MIN_INTERVAL), lossyLimiter.check())
    }

    @Test
    fun settingsChangesApplyImmediately() = runTest {
        val flow = MutableStateFlow(FeedCleanSettings())
        val live = RateLimiter(log, flow, { now })
        log.seed(now - 6_000)
        assertEquals(RateDecision.Allowed, live.check())

        flow.value = FeedCleanSettings(minSecondsBetweenActions = 10)

        assertEquals(RateDecision.Denied(4_000, DenyReason.MIN_INTERVAL), live.check())
    }

    private companion object {
        const val BASE = 1_700_000_000_000L
    }
}
