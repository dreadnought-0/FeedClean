package io.github.dreadnought0.feedclean.curate

import io.github.dreadnought0.feedclean.block.PermitOutcome
import io.github.dreadnought0.feedclean.block.RateLimiter
import io.github.dreadnought0.feedclean.block.RejectReason
import io.github.dreadnought0.feedclean.block.acceptUserIntent
import io.github.dreadnought0.feedclean.block.awaitScreen
import io.github.dreadnought0.feedclean.block.kindName
import io.github.dreadnought0.feedclean.block.reportProgress
import io.github.dreadnought0.feedclean.data.ActionKind
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot.FeedVideo
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot.LongPressMenu
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot.ShareSheet
import io.github.dreadnought0.feedclean.detect.UiNode
import io.github.dreadnought0.feedclean.service.ScreenDriver
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import io.github.dreadnought0.feedclean.settings.NotInterestedReason
import kotlin.coroutines.cancellation.CancellationException

/**
 * Sends TikTok's own "Not interested" feedback for the feed video on screen, verifying each screen:
 *
 * ```
 * CHECK_RATE_LIMIT → FIND_VIDEO → LONG_PRESS (menu with "Not interested")
 *   → TAP_NOT_INTERESTED → [CHOOSE_REASON if TikTok asks] → VERIFY (feed is back, any page)
 * ```
 *
 * Guarantees:
 *  - **Started by the user, or by armed automation only.** [NotInterestedTrigger.Manual] needs a fresh, unused
 *    [io.github.dreadnought0.feedclean.block.UserIntentToken] and manual mode switched on;
 *    [NotInterestedTrigger.Automated] is rejected unless automated Not-interested is armed.
 *  - **Rate-limited** by the shared [RateLimiter], recorded as [ActionKind.NOT_INTERESTED] whether it succeeds
 *    or fails. When rate-limited it returns at once without even reading the screen.
 *  - **Reads only [LongPressMenu] snapshot fields**: no TikTok strings here.
 *  - **Never retries, never loops.** It presses Back at most once per run (to close a menu it opened, or to
 *    dismiss a reason picker for [NotInterestedReason.SKIP_REASON]).
 *  - **Cancellation** stops it immediately without further taps.
 *
 * @param settings current settings (reason preference, manual switch, automation arming).
 * @param isShoppingReason whether a reason label is about shopping, for [NotInterestedReason.PREFER_SHOPPING].
 *   The default is a plain keyword match; production should pass the selector config's `longpress.reasonShop`
 *   matcher.
 * @param clock wall-clock epoch millis, the same clock used to create tokens.
 */
class NotInterestedFlow(
    private val driver: ScreenDriver,
    private val rateLimiter: RateLimiter,
    private val settings: () -> FeedCleanSettings,
    private val clock: () -> Long = System::currentTimeMillis,
    private val isShoppingReason: (label: String) -> Boolean = DEFAULT_IS_SHOPPING_REASON,
    private val config: NotInterestedConfig = NotInterestedConfig(),
) {

    /**
     * Runs the flow once for the feed video on screen.
     *
     * @param accountId local account id of the creator, if known; recorded in the action log.
     * @param expectedPageSignature the [FeedVideo.pageSignature] the request was made for (the card's post, or
     *   the page auto-skip decided on). If given and the feed shows another page, the run aborts untouched.
     * @param onProgress called on this coroutine as each [NotInterestedStep] starts.
     */
    suspend fun run(
        trigger: NotInterestedTrigger,
        accountId: Long? = null,
        expectedPageSignature: String? = null,
        onProgress: (NotInterestedStep) -> Unit = {},
    ): NotInterestedResult {
        val current = settings()
        val rejection = when (trigger) {
            is NotInterestedTrigger.Manual ->
                if (!current.notInterestedManualEnabled) {
                    RejectReason.MANUAL_DISABLED
                } else {
                    acceptUserIntent(trigger.token, clock(), config.tokenMaxAgeMillis)
                }
            is NotInterestedTrigger.Automated ->
                if (trigger.settings.automatedNotInterestedArmed && current.automatedNotInterestedArmed) {
                    null
                } else {
                    RejectReason.AUTOMATION_NOT_ARMED
                }
        }
        if (rejection != null) return NotInterestedResult.Rejected(rejection)

        val attempt = Attempt(current.notInterestedReason, expectedPageSignature, onProgress)
        attempt.enter(NotInterestedStep.CHECK_RATE_LIMIT)
        val outcome = rateLimiter.runWithPermit(
            kind = ActionKind.NOT_INTERESTED,
            accountId = accountId,
            isVerified = { it is NotInterestedResult.Done && it.verified },
        ) { attempt.execute() }
        return when (outcome) {
            is PermitOutcome.Ran -> outcome.value
            is PermitOutcome.Denied ->
                NotInterestedResult.RateLimited(outcome.decision.retryAfterMillis, outcome.decision.reason)
        }
    }

    /** State of one run. */
    private inner class Attempt(
        private val reasonPreference: NotInterestedReason,
        private val expectedPageSignature: String?,
        private val onProgress: (NotInterestedStep) -> Unit,
    ) {
        private var step = NotInterestedStep.CHECK_RATE_LIMIT
        private var backUsed = false

        fun enter(next: NotInterestedStep) {
            step = next
            reportProgress { onProgress(next) }
        }

        suspend fun execute(): NotInterestedResult = try {
            proceed()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The driver is in an unknown state: don't touch the screen any further.
            NotInterestedResult.Aborted(step, NotInterestedAbortReason.ERROR, detail = e.javaClass.simpleName)
        }

        private suspend fun proceed(): NotInterestedResult {
            // FIND_VIDEO: nothing is touched unless the expected feed video is on screen.
            enter(NotInterestedStep.FIND_VIDEO)
            val feed = driver.snapshot()
            if (feed !is FeedVideo) {
                return NotInterestedResult.Aborted(step, NotInterestedAbortReason.NOT_ON_FEED_VIDEO, "screen=${feed.kindName}")
            }
            if (expectedPageSignature != null && feed.pageSignature != expectedPageSignature) {
                return NotInterestedResult.Aborted(step, NotInterestedAbortReason.PAGE_CHANGED)
            }
            if (feed.signals.isLivePreview) {
                // A long-press on a LIVE preview can enter the live room. Never touch it.
                return NotInterestedResult.Aborted(step, NotInterestedAbortReason.LIVE_PREVIEW)
            }
            val video = feed.videoNode
                ?: return NotInterestedResult.Aborted(step, NotInterestedAbortReason.NO_VIDEO_NODE)

            // LONG_PRESS: the menu must offer "Not interested".
            enter(NotInterestedStep.LONG_PRESS)
            if (!driver.longPress(video)) return abort(NotInterestedAbortReason.LONG_PRESS_FAILED)
            val menu = awaitStep<LongPressMenu> { it.notInterestedNode != null }
            if (menu == null) {
                val now = driver.snapshot()
                val reason = if (now is LongPressMenu) {
                    NotInterestedAbortReason.NOT_INTERESTED_MISSING
                } else {
                    NotInterestedAbortReason.MENU_DID_NOT_OPEN
                }
                return abort(reason, now)
            }

            // TAP_NOT_INTERESTED
            enter(NotInterestedStep.TAP_NOT_INTERESTED)
            if (!driver.click(checkNotNull(menu.notInterestedNode))) return abort(NotInterestedAbortReason.TAP_FAILED)
            // From here the feedback may have reached TikTok: failures are Done(verified = false), not Aborted.
            val next = driver.awaitSnapshot(config.stepTimeoutMillis, config.pollMillis) {
                it is FeedVideo || (it is LongPressMenu && it.reasonNodes.isNotEmpty())
            } ?: return unverified(reasonChosen = null)

            // CHOOSE_REASON, only if TikTok asked.
            var reasonChosen: String? = null
            if (next is LongPressMenu) {
                enter(NotInterestedStep.CHOOSE_REASON)
                if (reasonPreference == NotInterestedReason.SKIP_REASON) {
                    backOnce()
                } else {
                    val (label, node) = pickReason(next.reasonNodes)
                    if (!driver.click(node)) return unverified(reasonChosen = null)
                    reasonChosen = label
                }
            }

            // VERIFY: the feed is back (TikTok may already have moved on to the next video).
            enter(NotInterestedStep.VERIFY)
            if (next !is FeedVideo && awaitStep<FeedVideo> { true } == null) return unverified(reasonChosen)
            return NotInterestedResult.Done(verified = true, reasonChosen = reasonChosen)
        }

        /**
         * First reason in on-screen order (the parser's [LongPressMenu.reasonNodes] order), or for
         * PREFER_SHOPPING the first shopping-related one if any.
         */
        private fun pickReason(reasons: Map<String, UiNode>): Map.Entry<String, UiNode> {
            val entries = reasons.entries
            return if (reasonPreference == NotInterestedReason.PREFER_SHOPPING) {
                entries.firstOrNull { isShoppingReason(it.key) } ?: entries.first()
            } else {
                entries.first()
            }
        }

        private suspend fun unverified(reasonChosen: String?): NotInterestedResult.Done {
            closeMenuIfOpen(null)
            return NotInterestedResult.Done(verified = false, reasonChosen = reasonChosen)
        }

        private suspend fun abort(
            reason: NotInterestedAbortReason,
            current: ScreenSnapshot? = null,
        ): NotInterestedResult.Aborted {
            val screen = closeMenuIfOpen(current)
            return NotInterestedResult.Aborted(step, reason, detail = "screen=${screen.kindName}")
        }

        /** Presses Back (at most once per run) if a menu or sheet is showing. Never on the feed or unknown screens. */
        private suspend fun closeMenuIfOpen(current: ScreenSnapshot?): ScreenSnapshot {
            val screen = current ?: driver.snapshot()
            if (screen is LongPressMenu || screen is ShareSheet) backOnce()
            return screen
        }

        private suspend fun backOnce() {
            if (backUsed) return
            backUsed = true
            driver.back()
        }

        private suspend inline fun <reified T : ScreenSnapshot> awaitStep(crossinline accept: (T) -> Boolean): T? =
            driver.awaitScreen(config.stepTimeoutMillis, config.pollMillis, accept)
    }

    companion object {
        /** Default shopping-reason matcher for PREFER_SHOPPING: label mentions shop, shopping, or product. */
        val DEFAULT_IS_SHOPPING_REASON: (label: String) -> Boolean = { label ->
            listOf("shop", "shopping", "product").any { label.contains(it, ignoreCase = true) }
        }
    }
}
