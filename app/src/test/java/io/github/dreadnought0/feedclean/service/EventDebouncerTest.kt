package io.github.dreadnought0.feedclean.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventDebouncerTest {

    @Test
    fun oneParsePerBurstAfterTheQuietPeriod() = runTest {
        val events = Channel<Unit>(Channel.CONFLATED)
        val fired = mutableListOf<Long>()
        val job = launch { EventDebouncer(250, 600).run(events, { testScheduler.currentTime }) { fired += testScheduler.currentTime } }
        runCurrent()

        events.send(Unit)
        advanceTimeBy(100)
        events.send(Unit)
        advanceTimeBy(100)
        events.send(Unit)
        advanceTimeBy(1_000)

        assertEquals(listOf(450L), fired)
        job.cancel()
    }

    @Test
    fun continuousEventsStillParseAtMaxWait() = runTest {
        val events = Channel<Unit>(Channel.CONFLATED)
        val fired = mutableListOf<Long>()
        val job = launch { EventDebouncer(250, 600).run(events, { testScheduler.currentTime }) { fired += testScheduler.currentTime } }
        val producer = launch {
            repeat(30) {
                events.send(Unit)
                delay(100)
            }
        }
        advanceTimeBy(3_000)

        assertEquals("first parse at the max wait, not starved", 600L, fired.first())
        assertTrue("a parse at least every 700 ms: $fired", fired.zipWithNext().all { (a, b) -> b - a <= 700 })
        assertTrue(fired.size >= 4)
        producer.cancel()
        job.cancel()
    }

    @Test
    fun eventsDuringTheCallbackStartTheNextBurst() = runTest {
        val events = Channel<Unit>(Channel.CONFLATED)
        val fired = mutableListOf<Long>()
        val job = launch {
            EventDebouncer(250, 600).run(events, { testScheduler.currentTime }) {
                fired += testScheduler.currentTime
                delay(500) // slow parse; events arriving now are conflated
            }
        }
        events.send(Unit)
        advanceTimeBy(300) // fires at 250
        events.send(Unit)
        events.send(Unit)
        advanceTimeBy(2_000)

        assertEquals(listOf(250L, 1_000L), fired)
        job.cancel()
    }

    @Test
    fun closingTheChannelEndsTheLoop() = runTest {
        val events = Channel<Unit>(Channel.CONFLATED)
        var count = 0
        val job = launch { EventDebouncer().run(events, { testScheduler.currentTime }) { count++ } }
        events.send(Unit)
        events.close()
        advanceTimeBy(1_000)
        assertTrue(job.isCompleted)
        assertEquals("the pending burst is still parsed", 1, count)
    }
}
