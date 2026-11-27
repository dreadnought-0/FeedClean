package io.github.dreadnought0.feedclean.service

import io.github.dreadnought0.feedclean.block.BlockFlowExecutor
import io.github.dreadnought0.feedclean.block.BlockResult
import io.github.dreadnought0.feedclean.block.BlockStep
import io.github.dreadnought0.feedclean.block.BlockTarget
import io.github.dreadnought0.feedclean.block.RateLimiter
import io.github.dreadnought0.feedclean.block.UserIntentToken
import io.github.dreadnought0.feedclean.block.acceptUserIntent
import io.github.dreadnought0.feedclean.curate.NotInterestedFlow
import io.github.dreadnought0.feedclean.curate.NotInterestedResult
import io.github.dreadnought0.feedclean.curate.NotInterestedStep
import io.github.dreadnought0.feedclean.curate.NotInterestedTrigger
import io.github.dreadnought0.feedclean.curate.SkipCover
import io.github.dreadnought0.feedclean.detect.NameNormalizer
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot.FeedVideo
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/** Outcome of the card's manual Skip. */
enum class ManualSkipResult {
    /** One scroll was issued and the feed showed another page. */
    SKIPPED,

    /** One scroll was issued but the page didn't change within the timeout. */
    NOT_ADVANCED,

    /** The click token was missing, stale, or reused. Nothing was touched. */
    REJECTED,

    /** The feed no longer shows the card's post (the user already moved on). Nothing was touched. */
    PAGE_CHANGED,

    /** The screen is not a feed video. Nothing was touched. */
    NOT_ON_FEED,

    /** No pager to scroll. Nothing was touched. */
    NO_PAGER,

    /** The scroll action failed. */
    SCROLL_FAILED,
}

/**
 * The overlay card's actions on TikTok. Every method that touches the screen takes a
 * [UserIntentToken], which the caller must create inside the button's click handler: the block
 * and Not-interested executors enforce it themselves, and [skip] checks it the same way.
 *
 * Blocks and Not-interested taps go through the ONE shared [rateLimiter]. Results are persisted through
 * [persistence] and summarised in [decisionLog] (result/step/reason enum names only). No retries, no loops: one
 * click → at most one run.
 */
class AssistedActions(
    private val driver: ScreenDriver,
    private val rateLimiter: RateLimiter,
    private val settings: () -> FeedCleanSettings,
    private val persistence: CardActionPersistence,
    private val cover: SkipCover,
    private val isShoppingReason: (String) -> Boolean = NotInterestedFlow.DEFAULT_IS_SHOPPING_REASON,
    private val log: RuntimeLog = RuntimeLog.NONE,
    private val clock: () -> Long = System::currentTimeMillis,
    private val skipAdvanceTimeoutMillis: Long = 1_500,
    private val decisionLog: DecisionLog = DecisionLog.NONE,
) {

    /** Runs TikTok's Block flow for [target] (the card's Block button). */
    suspend fun block(token: UserIntentToken, target: CardTarget, onStep: (BlockStep) -> Unit = {}): BlockResult {
        val executor = BlockFlowExecutor(driver, rateLimiter, clock, namesMatch = NAMES_MATCH)
        val blockTarget = BlockTarget(target.accountId, target.displayName, target.knownHandle)
        var lastStep = BlockStep.CHECK_RATE_LIMIT
        log.log("block start source=${token.source} style=${settings().blockStyle}")
        val result = try {
            executor.run(token, blockTarget, settings().blockStyle) { step ->
                lastStep = step
                log.log("block step $step")
                onStep(step)
            }
        } catch (e: CancellationException) {
            log.log("block cancelled at $lastStep")
            recordCard(DecisionCardAction.BLOCK, target, "Cancelled", step = lastStep.name)
            withContext(NonCancellable) { persistence.persistBlockInterrupted(target, lastStep) }
            throw e
        }
        log.log("block result ${describe(result)}")
        recordBlock(target, result)
        persistence.persistBlock(target, result)
        return result
    }

    /** Sends TikTok's "Not interested" for the card's post (the card's Not interested button). */
    suspend fun notInterested(
        token: UserIntentToken,
        target: CardTarget,
        onStep: (NotInterestedStep) -> Unit = {},
    ): NotInterestedResult {
        val flow = NotInterestedFlow(driver, rateLimiter, settings, clock, isShoppingReason)
        log.log("notInterested start source=${token.source}")
        val result = flow.run(NotInterestedTrigger.Manual(token), target.accountId, target.pageSignature) { step ->
            log.log("notInterested step $step")
            onStep(step)
        }
        log.log("notInterested result ${result.javaClass.simpleName}")
        recordNotInterested(target, result)
        persistence.persistNotInterested(target, result)
        return result
    }

    /**
     * The card's Skip: ONE scroll forward on the pager, only if the feed still shows the card's post, with the
     * skip-cover if enabled. Navigation only, so no rate limit.
     */
    suspend fun skip(token: UserIntentToken, target: CardTarget): ManualSkipResult =
        runSkip(token, target).also { recordCard(DecisionCardAction.SKIP, target, it.name) }

    private suspend fun runSkip(token: UserIntentToken, target: CardTarget): ManualSkipResult {
        if (acceptUserIntent(token, clock(), UserIntentToken.DEFAULT_MAX_AGE_MILLIS) != null) return ManualSkipResult.REJECTED
        val current = driver.snapshot()
        if (current !is FeedVideo) return ManualSkipResult.NOT_ON_FEED
        if (current.pageSignature != target.pageSignature) return ManualSkipResult.PAGE_CHANGED
        val pager = current.pagerNode ?: return ManualSkipResult.NO_PAGER
        val s = settings()
        val withCover = s.skipCoverEnabled && !current.pageBounds.isEmpty
        try {
            if (withCover) cover.show(current.pageBounds, s.coverStyle, reason = null)
            if (!driver.scrollForward(pager)) return ManualSkipResult.SCROLL_FAILED
            val moved = driver.awaitSnapshot(skipAdvanceTimeoutMillis, SKIP_POLL_MILLIS) {
                it is FeedVideo && it.pageSignature != target.pageSignature
            }
            persistence.persistSkip(target, advanced = moved != null)
            return if (moved != null) ManualSkipResult.SKIPPED else ManualSkipResult.NOT_ADVANCED
        } finally {
            if (withCover) withContext(NonCancellable) { cover.hide() }
        }
    }

    private fun recordBlock(target: CardTarget, result: BlockResult) = when (result) {
        is BlockResult.Blocked ->
            recordCard(DecisionCardAction.BLOCK, target, "Blocked", reason = if (result.alreadyBlocked) "ALREADY_BLOCKED" else "VERIFIED")
        is BlockResult.Aborted ->
            recordCard(DecisionCardAction.BLOCK, target, "Aborted", step = result.step.name, reason = result.reason.name)
        is BlockResult.RateLimited -> recordCard(DecisionCardAction.BLOCK, target, "RateLimited", reason = result.reason.name)
        is BlockResult.UserCancelled ->
            recordCard(DecisionCardAction.BLOCK, target, "UserCancelled", step = BlockStep.AWAITING_USER_CONFIRMATION.name)
        is BlockResult.TimedOut ->
            recordCard(DecisionCardAction.BLOCK, target, "TimedOut", step = BlockStep.AWAITING_USER_CONFIRMATION.name)
        is BlockResult.Rejected -> recordCard(DecisionCardAction.BLOCK, target, "Rejected", reason = result.reason.name)
    }

    private fun recordNotInterested(target: CardTarget, result: NotInterestedResult) = when (result) {
        is NotInterestedResult.Done ->
            recordCard(DecisionCardAction.NOT_INTERESTED, target, "Done", reason = if (result.verified) "VERIFIED" else "UNVERIFIED")
        is NotInterestedResult.Aborted ->
            recordCard(DecisionCardAction.NOT_INTERESTED, target, "Aborted", step = result.step.name, reason = result.reason.name)
        is NotInterestedResult.RateLimited ->
            recordCard(DecisionCardAction.NOT_INTERESTED, target, "RateLimited", reason = result.reason.name)
        is NotInterestedResult.Rejected ->
            recordCard(DecisionCardAction.NOT_INTERESTED, target, "Rejected", reason = result.reason.name)
    }

    /** One CARD line in the decision log: enum names only, never the target's name, handle, or texts. */
    private fun recordCard(action: DecisionCardAction, target: CardTarget, result: String, step: String? = null, reason: String? = null) {
        decisionLog.record(
            DecisionLogEntry(
                atMillis = clock(),
                event = DecisionEvent.CARD,
                context = target.feedContext,
                kinds = DetectionRules.detectedKinds(target.signals, settings()),
                action = action,
                result = result,
                step = step,
                reason = reason,
            ),
        )
    }

    private fun describe(result: BlockResult): String = when (result) {
        is BlockResult.Blocked -> "Blocked already=${result.alreadyBlocked} returned=${result.returnedToFeed}"
        is BlockResult.Aborted -> "Aborted step=${result.step} reason=${result.reason} returned=${result.returnedToFeed}"
        is BlockResult.RateLimited -> "RateLimited ${result.reason} retryAfterMs=${result.retryAfterMillis}"
        is BlockResult.UserCancelled -> "UserCancelled"
        is BlockResult.TimedOut -> "TimedOut"
        is BlockResult.Rejected -> "Rejected ${result.reason}"
    }

    companion object {
        /** Display-name comparison for the block flow's identity checks. */
        val NAMES_MATCH: (String, String) -> Boolean = { a, b -> NameNormalizer.normalize(a) == NameNormalizer.normalize(b) }

        private const val SKIP_POLL_MILLIS = 100L
    }
}
