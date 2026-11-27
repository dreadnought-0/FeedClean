package io.github.dreadnought0.feedclean.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate

class DecisionLogStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val today: LocalDate = LocalDate.of(2026, 11, 25)

    private fun store(maxBytes: Long = DecisionLogStore.DEFAULT_MAX_TOTAL_BYTES) =
        DecisionLogStore(File(tmp.root, DecisionLogStore.DIR_NAME), maxBytes)

    private fun line(t: Long) = DecisionLogCodec.encode(DecisionLogEntry(atMillis = t, event = DecisionEvent.PAGE)) + "\n"

    @Test
    fun appendsOneFilePerDayNamedByDate() {
        val s = store()
        s.append(today, line(1) + line(2))
        s.append(today, line(3))
        s.append(today.minusDays(1), line(0))
        assertEquals(listOf("2026-11-24.jsonl", "2026-11-25.jsonl"), s.files().map { it.file.name })
        assertEquals(listOf(0L, 1L, 2L, 3L), s.readAll().map { it.atMillis })
    }

    @Test
    fun retentionKeepsTheLastSevenDaysIncludingToday() {
        val s = store()
        (0L..9L).forEach { s.append(today.minusDays(it), line(it)) }
        val deleted = s.prune(today)
        assertEquals(3, deleted)
        assertEquals((6L downTo 0L).map { today.minusDays(it) }, s.files().map { it.date })
    }

    @Test
    fun sizeCapDropsTheOldestFilesFirst() {
        val s = store(maxBytes = 1_000)
        s.append(today.minusDays(2), "a".repeat(400) + "\n")
        s.append(today.minusDays(1), "b".repeat(400) + "\n")
        s.append(today, "c".repeat(400) + "\n")
        s.prune(today)
        assertEquals(listOf(today.minusDays(1), today), s.files().map { it.date })
        assertTrue(s.totalBytes() <= 1_000)
    }

    @Test
    fun aSingleFileOverTheCapKeepsItsNewestCompleteLines() {
        val s = store(maxBytes = 2_000)
        val lines = (1L..100L).map(::line)
        s.append(today, lines.joinToString(""))
        assertTrue(s.totalBytes() > 2_000)
        s.prune(today)
        val kept = s.readAll().map { it.atMillis }
        assertTrue("trimmed to about half the cap", s.totalBytes() <= 1_000)
        assertTrue(kept.isNotEmpty())
        assertEquals("newest lines survive, in order", (101L - kept.size..100L).toList(), kept)
        assertEquals("only whole lines", kept.size, File(s.dir, "$today.jsonl").readLines().size)
    }

    @Test
    fun foreignFilesAreIgnoredAndKept() {
        val s = store()
        s.dir.mkdirs()
        val other = File(s.dir, "notes.txt").apply { writeText("keep me") }
        val badName = File(s.dir, "yesterday.jsonl").apply { writeText("x") }
        s.append(today, line(1))
        s.prune(today.plusDays(30))
        assertTrue(other.exists())
        assertTrue(badName.exists())
        assertTrue(s.files().isEmpty())
    }

    @Test
    fun exportConcatenatesOldestFirstAndClearDeletesEverything() {
        val s = store()
        s.append(today, line(2))
        s.append(today.minusDays(1), line(1))
        val out = ByteArrayOutputStream()
        s.exportTo(out)
        assertEquals(line(1) + line(2), out.toString(Charsets.UTF_8))

        assertEquals(2, s.clear())
        assertTrue(s.files().isEmpty())
        assertTrue(s.readAll().isEmpty())
        s.append(today, line(3)) // writing again after a clear just works
        assertEquals(1, s.readAll().size)
    }

    @Test
    fun damagedLinesAreSkippedWhenReading() {
        val s = store()
        s.append(today, line(1) + "{broken\n" + "\n" + line(2))
        assertEquals(listOf(1L, 2L), s.readAll().map { it.atMillis })
    }

    @Test
    fun missingDirectoryIsEmpty() {
        val s = store()
        assertTrue(s.files().isEmpty())
        assertEquals(0, s.prune(today))
        assertEquals(0, s.clear())
    }
}
