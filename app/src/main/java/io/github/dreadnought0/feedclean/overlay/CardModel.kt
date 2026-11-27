package io.github.dreadnought0.feedclean.overlay

import io.github.dreadnought0.feedclean.block.BlockAbortReason
import io.github.dreadnought0.feedclean.block.BlockResult
import io.github.dreadnought0.feedclean.block.RateDecision
import io.github.dreadnought0.feedclean.curate.NotInterestedResult
import io.github.dreadnought0.feedclean.data.model.TagNames
import io.github.dreadnought0.feedclean.service.BlockedMatch
import io.github.dreadnought0.feedclean.service.CardTarget
import io.github.dreadnought0.feedclean.service.DetectionRules
import io.github.dreadnought0.feedclean.service.ManualSkipResult
import io.github.dreadnought0.feedclean.service.ProcessedPage
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings

/** Which card action a progress/result belongs to. */
enum class CardActionKind { BLOCK, NOT_INTERESTED, SKIP, LOG, FALSE_POSITIVE, CAPTURE }

/** A result line shown on the card and in the bubble's hint. The UI turns it into text. */
data class ActionMessage(
    val type: Type,
    /** Extra detail: a handle, an enum name, seconds, or a file name, depending on [type]. */
    val arg: String? = null,
    val ok: Boolean,
) {
    enum class Type {
        BLOCKED, ALREADY_BLOCKED, BLOCK_STOPPED, BLOCK_USER_CANCELLED, BLOCK_TIMED_OUT, BLOCK_CANCELLED,
        NOT_INTERESTED_DONE, NOT_INTERESTED_UNVERIFIED, NOT_INTERESTED_STOPPED,
        RATE_LIMITED, REJECTED,
        SKIPPED, SKIP_NOT_ADVANCED, SKIP_NOT_DONE,
        LOGGED, FALSE_POSITIVE, CAPTURE_SAVED, CAPTURE_FAILED,
        ACTION_BUSY, STOPPED_BY_USER,
        KILL_SWITCH_ON, KILL_SWITCH_OFF, AUTO_SKIP_RESUMED,

        /** The card isn't attached to a feed post (e.g. opened on a profile or an unrecognised screen). */
        NO_POST,

        /** Block / Not interested are not offered on in-feed LIVE previews (a tap there enters the live room). */
        LIVE_PREVIEW_UNSUPPORTED,

        /** Block stopped because a sheet covering the profile (TikTok's product popup) wouldn't close. */
        BLOCK_OVERLAY_NOT_DISMISSED,
    }
}

/** Live progress of the card's action. */
sealed interface CardProgress {
    data object Idle : CardProgress

    /** [step] is the executor step name (BlockStep / NotInterestedStep), if any. */
    data class Running(val kind: CardActionKind, val step: String? = null) : CardProgress

    data class Finished(val kind: CardActionKind, val message: ActionMessage) : CardProgress
}

/** Database facts for the card's account. */
data class CardStats(val seenCount: Int = 0, val firstSeenAtMillis: Long? = null)

/**
 * Everything the overlay card shows. Pure data, built by [CardModel.build].
 *
 * @property target the post the buttons act on; null when no feed post is known (only Capture is offered).
 * @property kinds detected kinds of the post, strongest first (detection toggles applied).
 * @property rateLimitWaitMillis how long until Block / Not interested may run; null when allowed now.
 */
data class CardModel(
    val target: CardTarget?,
    val displayName: String?,
    val knownHandle: String?,
    val kinds: List<DetectionRules.Kind>,
    val stats: CardStats,
    val blockedMatch: BlockedMatch,
    val showNotInterested: Boolean,
    val manualTags: Set<String>,
    val rateLimitWaitMillis: Long?,
    val loopGuardPaused: Boolean,
    val progress: CardProgress,
) {
    /** Buttons that touch TikTok are disabled while something runs. */
    val busy: Boolean get() = progress is CardProgress.Running

    companion object {
        /** Quick manual tags offered as chips, in display order. */
        val QUICK_TAGS: List<String> = listOf(
            TagNames.AI_GENERATED,
            TagNames.SEXUAL_CONTENT,
            TagNames.IMPERSONATOR,
            TagNames.REPOSTED,
            TagNames.BOT_LIKE,
        )

        fun build(
            page: ProcessedPage?,
            settings: FeedCleanSettings,
            stats: CardStats = CardStats(),
            manualTags: Set<String> = emptySet(),
            rateLimit: RateDecision? = null,
            loopGuardPaused: Boolean = false,
            progress: CardProgress = CardProgress.Idle,
        ): CardModel = CardModel(
            target = page?.let(CardTarget::from),
            displayName = page?.page?.displayName,
            knownHandle = page?.knownHandle,
            kinds = page?.let { DetectionRules.detectedKinds(it.signals, settings) }.orEmpty(),
            stats = stats,
            blockedMatch = page?.blockedMatch ?: BlockedMatch.NONE,
            showNotInterested = settings.notInterestedManualEnabled,
            manualTags = manualTags.filter { it in QUICK_TAGS }.toSet(),
            rateLimitWaitMillis = (rateLimit as? RateDecision.Denied)?.retryAfterMillis?.takeIf { it > 0 },
            loopGuardPaused = loopGuardPaused,
            progress = progress,
        )
    }
}

/** Result → [ActionMessage] mappings. Pure; no screen content beyond the handle TikTok itself showed. */
object ActionMessages {
    fun forBlock(result: BlockResult): ActionMessage = when (result) {
        is BlockResult.Blocked -> ActionMessage(
            if (result.alreadyBlocked) ActionMessage.Type.ALREADY_BLOCKED else ActionMessage.Type.BLOCKED,
            arg = result.handle,
            ok = true,
        )
        is BlockResult.Aborted -> ActionMessage(
            if (result.reason == BlockAbortReason.OVERLAY_NOT_DISMISSED) {
                ActionMessage.Type.BLOCK_OVERLAY_NOT_DISMISSED
            } else {
                ActionMessage.Type.BLOCK_STOPPED
            },
            arg = "${result.step} · ${result.reason}",
            ok = false,
        )
        is BlockResult.RateLimited -> rateLimited(result.retryAfterMillis)
        is BlockResult.UserCancelled -> ActionMessage(ActionMessage.Type.BLOCK_USER_CANCELLED, ok = false)
        is BlockResult.TimedOut -> ActionMessage(ActionMessage.Type.BLOCK_TIMED_OUT, ok = false)
        is BlockResult.Rejected -> ActionMessage(ActionMessage.Type.REJECTED, result.reason.name, ok = false)
    }

    fun forNotInterested(result: NotInterestedResult): ActionMessage = when (result) {
        is NotInterestedResult.Done -> if (result.verified) {
            ActionMessage(ActionMessage.Type.NOT_INTERESTED_DONE, ok = true)
        } else {
            ActionMessage(ActionMessage.Type.NOT_INTERESTED_UNVERIFIED, ok = false)
        }
        is NotInterestedResult.Aborted ->
            ActionMessage(ActionMessage.Type.NOT_INTERESTED_STOPPED, "${result.step} · ${result.reason}", ok = false)
        is NotInterestedResult.RateLimited -> rateLimited(result.retryAfterMillis)
        is NotInterestedResult.Rejected -> ActionMessage(ActionMessage.Type.REJECTED, result.reason.name, ok = false)
    }

    fun forSkip(result: ManualSkipResult): ActionMessage = when (result) {
        ManualSkipResult.SKIPPED -> ActionMessage(ActionMessage.Type.SKIPPED, ok = true)
        ManualSkipResult.NOT_ADVANCED -> ActionMessage(ActionMessage.Type.SKIP_NOT_ADVANCED, ok = false)
        else -> ActionMessage(ActionMessage.Type.SKIP_NOT_DONE, result.name, ok = false)
    }

    /** Whole seconds to wait, rounded up (at least 1). */
    fun waitSeconds(millis: Long): Long = ((millis + 999) / 1000).coerceAtLeast(1)

    private fun rateLimited(millis: Long) =
        ActionMessage(ActionMessage.Type.RATE_LIMITED, waitSeconds(millis).toString(), ok = false)
}
