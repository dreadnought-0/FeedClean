package io.github.dreadnought0.feedclean.service

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The live [DecisionLog]: [record] only drops the entry into a bounded queue (never blocks the snapshot loop; when
 * the queue is full the oldest entry is dropped), and one coroutine on [dispatcher] writes batches to [store].
 *
 *  - Records nothing while [enabled] is false (Settings → Data & privacy → decision log).
 *  - Retention and the size cap ([DecisionLogStore.prune]) run on start, when the local date changes, and after
 *    every [CAP_CHECK_BYTES] written.
 *  - Any I/O failure is reported to [warn] and swallowed: the log can never crash the service.
 *
 * [close] stops accepting entries; the ones already queued are still written.
 */
class DecisionLogWriter(
    private val store: DecisionLogStore,
    scope: CoroutineScope,
    private val enabled: () -> Boolean,
    private val warn: (String, Throwable) -> Unit = { _, _ -> },
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val today: () -> LocalDate = { LocalDate.now(zone()) },
) : DecisionLog {
    private val queue = Channel<DecisionLogEntry>(QUEUE_CAPACITY, BufferOverflow.DROP_OLDEST)

    /** The writer coroutine (completes after [close] once the queue is drained). */
    val job: Job = scope.launch(dispatcher) { run() }

    override fun record(entry: DecisionLogEntry) {
        if (!isEnabled()) return
        queue.trySend(entry)
    }

    /** Stops accepting entries; queued ones are still written. Idempotent. */
    fun close() {
        queue.close()
    }

    private suspend fun run() {
        var maintainedOn = today()
        safely("prune") { store.prune(maintainedOn) }
        var sinceCheck = 0L
        for (first in queue) {
            val batch = ArrayList<DecisionLogEntry>().apply { add(first) }
            while (batch.size < MAX_BATCH) batch += queue.tryReceive().getOrNull() ?: break
            safely("write") {
                val day = today()
                if (day != maintainedOn) {
                    store.prune(day)
                    maintainedOn = day
                    sinceCheck = 0
                }
                batch.groupBy { dateOf(it.atMillis) }.forEach { (date, entries) ->
                    val text = buildString { entries.forEach { append(DecisionLogCodec.encode(it)).append('\n') } }
                    store.append(date, text)
                    sinceCheck += text.length
                }
                if (sinceCheck >= CAP_CHECK_BYTES) {
                    store.prune(day)
                    sinceCheck = 0
                }
            }
        }
    }

    private fun isEnabled(): Boolean = try {
        enabled()
    } catch (e: RuntimeException) {
        false
    }

    private fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate()

    private inline fun safely(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) { // IOException, SecurityException, …: never let the log take the service down
            warn("decision log $what failed", e)
        }
    }

    companion object {
        const val QUEUE_CAPACITY = 1_024
        const val MAX_BATCH = 256
        const val CAP_CHECK_BYTES = 64L * 1024
    }
}
