package io.github.dreadnought0.feedclean.ui.accounts

import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.data.model.BlockEvent
import io.github.dreadnought0.feedclean.data.model.Sighting
import io.github.dreadnought0.feedclean.data.model.SightingSource
import io.github.dreadnought0.feedclean.data.model.TimelineEntry
import io.github.dreadnought0.feedclean.data.model.UserAction
import io.github.dreadnought0.feedclean.ui.stats.UiText

/** What a timeline row is about (picks its icon). */
enum class TimelineKind { SIGHTING, BLOCK }

/** Whether the row's action was checked on screen. */
enum class TimelineOutcome {
    /** Confirmed on screen. */
    VERIFIED,

    /** The action was attempted and did not verify. */
    FAILED,

    /** No action, or verification not applicable / unknown. */
    NONE,
}

/** Display text of one timeline row; strings are resolved in Compose. */
data class TimelineRowText(
    /** Stable list key, unique across sightings and block events. */
    val key: String,
    val kind: TimelineKind,
    val atMillis: Long,
    val title: UiText,
    val outcome: TimelineOutcome,
    /** Signal labels (string resources), strongest first. */
    val signalLabels: List<Int>,
    /** Secondary lines: skip reason, verification, raw on-screen label, block method, failure step… */
    val details: List<UiText>,
)

/** Timeline rows, newest first (ties keep the repository's order). */
fun timelineRows(entries: List<TimelineEntry>): List<TimelineRowText> =
    entries.sortedByDescending { it.atMillis }.map { it.toRowText() }

/** Maps one timeline entry to its row text. */
fun TimelineEntry.toRowText(): TimelineRowText = when (this) {
    is TimelineEntry.SightingEntry -> sightingRow(sighting)
    is TimelineEntry.BlockEntry -> blockRow(event)
}

private fun sightingRow(sighting: Sighting): TimelineRowText {
    val acted = sighting.userAction != UserAction.NONE
    val details = buildList {
        sighting.skipReason?.let {
            add(UiText.Res(R.string.screens_timeline_reason, listOf(UiText.Res(it.labelRes()))))
        }
        if (acted) {
            when (sighting.actionVerified) {
                true -> add(UiText.Res(R.string.screens_timeline_verified))
                false -> add(UiText.Res(R.string.screens_timeline_not_verified))
                null -> Unit
            }
        }
        sighting.signals.disclosureLabel?.trim()?.takeIf { it.isNotEmpty() }?.let {
            add(UiText.Res(R.string.screens_timeline_label, listOf(it)))
        }
        sighting.signals.anchorText?.trim()?.takeIf { it.isNotEmpty() }?.let {
            add(UiText.Res(R.string.screens_timeline_anchor, listOf(it)))
        }
        if (sighting.source != SightingSource.OVERLAY) add(UiText.Res(sighting.source.labelRes()))
    }
    val outcome = when {
        !acted -> TimelineOutcome.NONE
        sighting.actionVerified == true -> TimelineOutcome.VERIFIED
        sighting.actionVerified == false -> TimelineOutcome.FAILED
        else -> TimelineOutcome.NONE
    }
    return TimelineRowText(
        key = "sighting-${sighting.id}",
        kind = TimelineKind.SIGHTING,
        atMillis = sighting.seenAt,
        title = UiText.Res(sighting.userAction.timelineTitleRes()),
        outcome = outcome,
        signalLabels = signalLabelRes(sighting.signals),
        details = details,
    )
}

private fun blockRow(event: BlockEvent): TimelineRowText {
    val details = buildList {
        add(UiText.Res(event.method.labelRes()))
        if (!event.verified) {
            event.stepReached?.trim()?.takeIf { it.isNotEmpty() }?.let {
                add(UiText.Res(R.string.screens_timeline_step, listOf(it)))
            }
            event.failureReason?.trim()?.takeIf { it.isNotEmpty() }?.let {
                add(UiText.Res(R.string.screens_timeline_failure, listOf(it)))
            }
        }
    }
    return TimelineRowText(
        key = "block-${event.id}",
        kind = TimelineKind.BLOCK,
        atMillis = event.attemptedAt,
        title = UiText.Res(if (event.verified) R.string.screens_timeline_blocked else R.string.screens_timeline_block_failed),
        outcome = if (event.verified) TimelineOutcome.VERIFIED else TimelineOutcome.FAILED,
        signalLabels = emptyList(),
        details = details,
    )
}
