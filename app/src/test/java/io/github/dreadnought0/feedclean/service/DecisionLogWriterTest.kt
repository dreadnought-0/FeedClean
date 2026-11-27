package io.github.dreadnought0.feedclean.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class DecisionLogWriterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val day = LocalDate.of(2026, 11, 25)
    private val noonUtc = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 12 * 3_600_000L

    private class Harness(scope: TestScope, val store: DecisionLogStore, var today: LocalDate) {
        var enabled = true
        val warnings = mutableListOf<String>()
        val writer = DecisionLogWriter(
            store = store,
            scope = scope.backgroundScope,
            enabled = { enabled },
            warn = { message, _ -> warnings += message },
            dispatcher = StandardTestDispatcher(scope.testScheduler),
            zone = { ZoneOffset.UTC },
            today = { today },
        )
    }

    private fun TestScope.harness(dir: File = File(tmp.root, DecisionLogStore.DIR_NAME)) = Harness(this, DecisionLogStore(dir), day)

    private fun entry(t: Long) = DecisionLogEntry(atMillis = t, event = DecisionEvent.PAGE, gate = "go")

    @Test
    fun recordIsAsyncAndLandsInTheEntrysLocalDayFile() = runTest {
        val h = harness()
        h.writer.record(entry(noonUtc))
        h.writer.record(entry(noonUtc - 24 * 3_600_000L)) // yesterday's (e.g. queued just before midnight)
        assertTrue("nothing written on the caller's thread", h.store.files().isEmpty())

        runCurrent()

        assertEquals(listOf(day.minusDays(1), day), h.store.files().map { it.date })
        assertEquals(listOf(noonUtc - 24 * 3_600_000L, noonUtc), h.store.readAll().map { it.atMillis })
    }

    @Test
    fun nothingIsRecordedWhileDisabled() = runTest {
        val h = harness()
        h.enabled = false
        h.writer.record(entry(noonUtc))
        runCurrent()
        assertTrue(h.store.readAll().isEmpty())

        h.enabled = true
        h.writer.record(entry(noonUtc + 1))
        runCurrent()
        assertEquals(listOf(noonUtc + 1), h.store.readAll().map { it.atMillis })
    }

    @Test
    fun aFullQueueDropsTheOldestInsteadOfBlocking() = runTest {
        val h = harness()
        val n = DecisionLogWriter.QUEUE_CAPACITY + 500
        repeat(n) { h.writer.record(entry(noonUtc + it)) } // the writer hasn't run yet: must not block
        runCurrent()
        val written = h.store.readAll().map { it.atMillis }
        assertTrue(written.size <= DecisionLogWriter.QUEUE_CAPACITY + 1)
        assertEquals("the newest entry is kept", noonUtc + n - 1, written.last())
    }

    @Test
    fun ioFailuresAreReportedAndNeverThrown() = runTest {
        val blocker = tmp.newFile("not-a-dir") // the log "directory" is a file: every write fails
        val h = harness(dir = blocker)
        h.writer.record(entry(noonUtc))
        runCurrent()
        h.writer.record(entry(noonUtc + 1))
        runCurrent()
        assertTrue(h.warnings.any { it.contains("write") })
        assertFalse("the writer is still alive", h.writer.job.isCompleted)
    }

    @Test
    fun closeStillWritesWhatWasQueued() = runTest {
        val h = harness()
        h.writer.record(entry(noonUtc))
        h.writer.close()
        h.writer.record(entry(noonUtc + 1)) // after close: dropped
        runCurrent()
        assertEquals(listOf(noonUtc), h.store.readAll().map { it.atMillis })
        assertTrue(h.writer.job.isCompleted)
    }

    @Test
    fun retentionRunsOnStartAndWhenTheDayChanges() = runTest {
        val dir = File(tmp.root, DecisionLogStore.DIR_NAME)
        val store = DecisionLogStore(dir)
        store.append(day.minusDays(10), "{\"t\":1,\"ev\":\"page\"}\n")
        store.append(day.minusDays(6), "{\"t\":2,\"ev\":\"page\"}\n")
        val h = Harness(this, store, day)
        runCurrent()
        assertEquals("pruned on start", listOf(day.minusDays(6)), store.files().map { it.date })

        h.today = day.plusDays(1) // the service ran past midnight
        h.writer.record(entry(noonUtc + 24 * 3_600_000L))
        runCurrent()
        assertEquals(listOf(day.plusDays(1)), store.files().map { it.date })
    }
}
