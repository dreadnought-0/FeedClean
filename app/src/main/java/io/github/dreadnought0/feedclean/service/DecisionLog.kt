package io.github.dreadnought0.feedclean.service

import io.github.dreadnought0.feedclean.curate.AutoSkipOutcome
import io.github.dreadnought0.feedclean.curate.NotInterestedResult
import io.github.dreadnought0.feedclean.curate.SkipGateResult
import io.github.dreadnought0.feedclean.detect.CurationDecision
import io.github.dreadnought0.feedclean.detect.FeedContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The on-phone curation decision log: answers "why wasn't this skipped?" after the fact (logcat only keeps minutes).
 * One JSON object per line in `filesDir/decision-log/YYYY-MM-DD.jsonl` (local date), kept 7 days and at most ~5 MB
 * ([DecisionLogStore]), written off the main thread only while
 * [io.github.dreadnought0.feedclean.settings.FeedCleanSettings.decisionLogEnabled] ([DecisionLogWriter]).
 *
 * PRIVACY: an entry carries ONLY FeedClean's own enums, counters, and timestamps. It has no field
 * for display names, handles, captions, sound titles, product/anchor/label text, or any other screen text, and its
 * label fields are forced to enum-name tokens on write ([DecisionLogCodec.safeToken]). It never leaves the phone
 * except through the user's own "Export log" (system file picker).
 *
 * Implementations must never block and never throw.
 */
fun interface DecisionLog {
    fun record(entry: DecisionLogEntry)

    companion object {
        /** Discards everything (tests, logging off). */
        val NONE: DecisionLog = DecisionLog { }
    }
}

/** What a [DecisionLogEntry] is about. */
@Serializable
enum class DecisionEvent {
    /** The first read of a new feed page: detection, decision, gate, and the auto-skip outcome if it ran. */
    @SerialName("page")
    PAGE,

    /** A later read of the same page inside its watch window changed something (late label, retried hold). */
    @SerialName("watch")
    WATCH,

    /** A result of one of the overlay card's buttons. */
    @SerialName("card")
    CARD,
}

/** Why a [DecisionEvent.WATCH] re-evaluation ran. */
enum class WatchTrigger {
    /** A detection signal (Ad label, Shop anchor, …) appeared after the page's first read. */
    SIGNALS_GREW,

    /** The first read held the skip for a reason that clears within a moment (pager not found yet). */
    TRANSIENT_HOLD,
}

/** Which overlay-card button a [DecisionEvent.CARD] entry is about. */
enum class DecisionCardAction { BLOCK, NOT_INTERESTED, SKIP }

/**
 * One decision-log line. JSON keys are short (≈150 bytes a line). Null fields are omitted. See [DecisionLog] for
 * the privacy contract: every field is an enum, a number, a boolean, or an enum-name token.
 *
 * @property atMillis epoch millis (for PAGE: the page's first read).
 * @property context the player the page was in (For You, other Home tab, search, other).
 * @property kinds detected kinds with the detection toggles applied, strongest first.
 * @property newKinds WATCH: kinds this read detected for the first time.
 * @property decision `keep` or `skip:<SkipReason>`.
 * @property gate `go` or `hold:<SkipHold>`.
 * @property outcome the auto-skip outcome when it ran: `skipped`, `skipped:NOT_ADVANCED`, `advanced` (TikTok moved on
 *   after Not interested), `cancelled:<SkipCancel>`, or `refused:<SkipHold>`.
 * @property notInterested automated Not-interested result when it ran, e.g. `Done:VERIFIED`, `RateLimited:<DenyReason>`.
 * @property logged PAGE/WATCH: a sighting was logged (or updated) for the page.
 * @property sinceFirstReadMillis WATCH: ms since the page's first read.
 * @property result CARD: result type or enum name, e.g. `Blocked`, `Aborted`, `SKIPPED`.
 * @property step CARD: executor step enum name (BlockStep / NotInterestedStep).
 * @property reason CARD: reason enum name (BlockAbortReason, NotInterestedAbortReason, DenyReason, RejectReason, …).
 */
@Serializable
data class DecisionLogEntry(
    @SerialName("t") val atMillis: Long,
    @SerialName("ev") val event: DecisionEvent,
    @SerialName("ctx") val context: FeedContext? = null,
    @SerialName("kinds") val kinds: List<DetectionRules.Kind>? = null,
    @SerialName("new") val newKinds: List<DetectionRules.Kind>? = null,
    @SerialName("dec") val decision: String? = null,
    @SerialName("gate") val gate: String? = null,
    @SerialName("out") val outcome: String? = null,
    @SerialName("ni") val notInterested: String? = null,
    @SerialName("logged") val logged: Boolean? = null,
    @SerialName("dt") val sinceFirstReadMillis: Long? = null,
    @SerialName("why") val trigger: WatchTrigger? = null,
    @SerialName("act") val action: DecisionCardAction? = null,
    @SerialName("res") val result: String? = null,
    @SerialName("step") val step: String? = null,
    @SerialName("reason") val reason: String? = null,
)

/** Enum-name labels shared by the runtime log (logcat) and the decision log. Pure. */
object DecisionLabels {
    fun decision(decision: CurationDecision): String = when (decision) {
        is CurationDecision.Skip -> "skip:${decision.reason.name}"
        CurationDecision.Keep -> "keep"
    }

    fun gate(gate: SkipGateResult): String = when (gate) {
        is SkipGateResult.Go -> "go"
        is SkipGateResult.Hold -> "hold:${gate.why.name}"
    }

    fun outcome(outcome: AutoSkipOutcome): String = when (outcome) {
        is AutoSkipOutcome.Skipped -> when {
            outcome.scrolled && outcome.advanced -> "skipped"
            outcome.scrolled -> "skipped:NOT_ADVANCED"
            else -> "advanced"
        }
        is AutoSkipOutcome.Cancelled -> "cancelled:${outcome.why.name}"
        is AutoSkipOutcome.Refused -> "refused:${outcome.why.name}"
    }

    fun notInterested(result: NotInterestedResult): String = when (result) {
        is NotInterestedResult.Done -> if (result.verified) "Done:VERIFIED" else "Done:UNVERIFIED"
        is NotInterestedResult.Aborted -> "Aborted:${result.step.name}:${result.reason.name}"
        is NotInterestedResult.RateLimited -> "RateLimited:${result.reason.name}"
        is NotInterestedResult.Rejected -> "Rejected:${result.reason.name}"
    }
}

/** JSON-lines encoding of [DecisionLogEntry]. Pure. */
object DecisionLogCodec {
    /** Written instead of a label that isn't an enum-name token (defence in depth; FeedClean never builds one). */
    const val INVALID = "INVALID"

    private val json = Json { ignoreUnknownKeys = true }
    private val token = Regex("[A-Za-z0-9_:]{1,64}")

    /** One line, without the trailing newline. Label fields are passed through [safeToken] first. */
    fun encode(entry: DecisionLogEntry): String = json.encodeToString(DecisionLogEntry.serializer(), sanitized(entry))

    /** Parses one line; null for a blank, damaged, or incompatible line. */
    fun decode(line: String): DecisionLogEntry? {
        if (line.isBlank()) return null
        return try {
            json.decodeFromString(DecisionLogEntry.serializer(), line)
        } catch (e: IllegalArgumentException) { // SerializationException is one
            null
        }
    }

    /**
     * [value] if it is an enum-name token (`[A-Za-z0-9_:]`, ≤ 64 chars), else [INVALID]. Spaces, punctuation,
     * emoji, `@`, and `#` are rejected, so captions, sound titles, and product text can't get through even by mistake.
     */
    fun safeToken(value: String?): String? = value?.let { if (token.matches(it)) it else INVALID }

    private fun sanitized(e: DecisionLogEntry) = e.copy(
        decision = safeToken(e.decision),
        gate = safeToken(e.gate),
        outcome = safeToken(e.outcome),
        notInterested = safeToken(e.notInterested),
        result = safeToken(e.result),
        step = safeToken(e.step),
        reason = safeToken(e.reason),
    )
}
