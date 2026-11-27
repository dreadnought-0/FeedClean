package io.github.dreadnought0.feedclean.testutil

import io.github.dreadnought0.feedclean.service.DecisionEvent
import io.github.dreadnought0.feedclean.service.DecisionLog
import io.github.dreadnought0.feedclean.service.DecisionLogEntry

/** Collects decision-log entries in memory. */
class FakeDecisionLog : DecisionLog {
    val entries = mutableListOf<DecisionLogEntry>()

    override fun record(entry: DecisionLogEntry) {
        entries += entry
    }

    fun of(event: DecisionEvent): List<DecisionLogEntry> = entries.filter { it.event == event }
}
