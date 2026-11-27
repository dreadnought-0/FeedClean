package io.github.dreadnought0.feedclean.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.BlockMethod
import io.github.dreadnought0.feedclean.data.model.HandleSource
import io.github.dreadnought0.feedclean.data.model.TagNames
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActionLogAndBlockTest : DbTest() {

    @Test
    fun countSinceCountsEveryAttemptVerifiedOrNot() = runTest {
        val a = accounts.upsertFromFeed("Seller", null, 0)
        assertNull(actionLog.lastActionAt())
        assertEquals(0, actionLog.countSince(0))

        actionLog.record(ActionKind.BLOCK, 1_000, a, verified = true)
        actionLog.record(ActionKind.NOT_INTERESTED, 2_000, null, verified = false)
        actionLog.record(ActionKind.BLOCK, 3_000, a, verified = false)

        assertEquals(3, actionLog.countSince(0))
        assertEquals(2, actionLog.countSince(2_000))
        assertEquals(0, actionLog.countSince(3_001))
        assertEquals(3_000L, actionLog.lastActionAt())
        assertEquals("BLOCK", rawString("SELECT kind FROM action_log ORDER BY at LIMIT 1"))
    }

    @Test
    fun clearAllDataKeepsTheRecentRateLimitBudget() = runTest {
        val now = 10 * DataLayer.RATE_LIMIT_RETENTION_MILLIS
        val a = accounts.ensureHandleAccount("someone", HandleSource.PROFILE, "Someone", now)
        actionLog.record(ActionKind.BLOCK, now - 2 * DataLayer.RATE_LIMIT_RETENTION_MILLIS, a, verified = true)
        actionLog.record(ActionKind.BLOCK, now - 30 * 60 * 1000L, a, verified = true)
        actionLog.record(ActionKind.NOT_INTERESTED, now - 60_000, null, verified = false)

        data.clearAllData(now)

        assertNull(accounts.getAccount(a))
        assertTrue(accounts.observeAccounts().first().isEmpty())
        assertEquals(2, actionLog.countSince(0))
        assertEquals(now - 60_000, actionLog.lastActionAt())
        assertNull(rawString("SELECT account_id FROM action_log WHERE account_id IS NOT NULL"))
        assertEquals(TagNames.DEFAULTS.map { it.first }.sorted(), accounts.observeAllTagNames().first())
    }

    @Test
    fun verifiedBlockMarksAccountBlocked() = runTest {
        val a = accounts.ensureHandleAccount("target", HandleSource.PROFILE, "Target", 0)
        blockEvents.record(a, 100, BlockMethod.ASSISTED, verified = false, failureReason = "timeout", stepReached = "OPEN_PROFILE")
        assertEquals(AccountStatus.SEEN, accounts.getAccount(a)?.status)
        assertNull(blockEvents.latestVerifiedBlock(a))

        blockEvents.record(a, 200, BlockMethod.ASSISTED, verified = true)
        blockEvents.record(a, 300, BlockMethod.MANUAL, verified = true)
        assertEquals(AccountStatus.BLOCKED, accounts.getAccount(a)?.status)
        assertEquals(300L, blockEvents.latestVerifiedBlock(a)?.attemptedAt)

        val events = blockEvents.observeForAccount(a).first()
        assertEquals(listOf(300L, 200L, 100L), events.map { it.attemptedAt })
        assertEquals("timeout", events.last().failureReason)
        assertEquals("OPEN_PROFILE", events.last().stepReached)
    }
}
