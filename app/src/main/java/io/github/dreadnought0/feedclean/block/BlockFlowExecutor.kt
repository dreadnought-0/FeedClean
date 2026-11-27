package io.github.dreadnought0.feedclean.block

import io.github.dreadnought0.feedclean.data.ActionKind
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot.BlockDialog
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot.DismissibleSheet
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot.FeedVideo
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot.LongPressMenu
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot.Profile
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot.ShareSheet
import io.github.dreadnought0.feedclean.service.ScreenDriver
import io.github.dreadnought0.feedclean.settings.BlockStyle
import kotlin.coroutines.cancellation.CancellationException

/** Timeouts for [BlockFlowExecutor]. */
data class BlockFlowConfig(
    /** How long each step waits for its expected screen. */
    val stepTimeoutMillis: Long = 3_000,
    /** Snapshot polling interval while waiting. */
    val pollMillis: Long = 150,
    /** Stop-at-dialog style: how long to wait for the user to answer TikTok's dialog. */
    val userConfirmTimeoutMillis: Long = 30_000,
    /** Maximum age of the [UserIntentToken] when the run starts. */
    val tokenMaxAgeMillis: Long = UserIntentToken.DEFAULT_MAX_AGE_MILLIS,
) {
    init {
        require(stepTimeoutMillis > 0 && pollMillis > 0 && userConfirmTimeoutMillis > 0 && tokenMaxAgeMillis > 0) {
            "timeouts must be positive"
        }
    }
}

/**
 * Runs TikTok's own Block flow for one account, verifying every screen on the way:
 *
 * ```
 * CHECK_RATE_LIMIT → OPEN_PROFILE → READ_HANDLE ─(already blocked)→ RETURN
 *   [→ DISMISS_OVERLAY]   at most once per run, only if a sheet covers the profile (before or just before Share)
 *   → OPEN_SHARE_SHEET → TAP_BLOCK → CHECK_DIALOG
 *   → CONFIRM → VERIFY → RETURN                                   (ONE_TAP_FULL_FLOW)
 *   → AWAITING_USER_CONFIRMATION → VERIFY → RETURN                (STOP_AT_TIKTOK_DIALOG)
 * ```
 *
 * Guarantees:
 *  - **Only from a user click.** [run] requires a fresh, unused [UserIntentToken].
 *  - **Rate-limited.** The whole run holds the shared [RateLimiter] permit, and the attempt is recorded as
 *    [ActionKind.BLOCK] whether it succeeds or fails (not when rejected or rate-limited, since then nothing
 *    happened on screen).
 *  - **Verified, never blind.** Each step waits (up to [BlockFlowConfig.stepTimeoutMillis]) for the screen it
 *    expects, read only from [ScreenSnapshot] fields. The profile must be the expected account and TikTok's
 *    dialog must name the same @handle before Block is confirmed.
 *  - **Stops on anything unexpected.** It never retries and never loops. It backs out of only the screens it
 *    opened (dialog → Cancel, share sheet → close or Back, profile → Back), at most once each, and never
 *    presses Back on a screen it doesn't recognise.
 *  - **Covering sheets** ([DismissibleSheet], e.g. TikTok's product sheet on a Shop advertiser's profile): the
 *    profile underneath is read through [DismissibleSheet.underlyingProfile] with the same checks as a plain
 *    profile. The only action ever taken on such a sheet is ONE dismissal per run (ACTION_CLICK on its clickable
 *    scrim, else one Back), and only right before Share. Nothing inside the sheet is ever tapped, a sheet is
 *    never taken for the share sheet, and cleanup never touches one: if it stays, comes back, or covers something
 *    else, the run stops ([BlockAbortReason.OVERLAY_NOT_DISMISSED]) and leaves it for the user.
 *  - **Cancellation** (for example, the kill-switch) stops it immediately without further taps.
 *  - No database access: the caller persists the [BlockResult].
 *
 * @param namesMatch compares the expected display name (first argument) with one read from the screen. The
 *   default is trim + case-insensitive; production should pass a NameNormalizer-based comparison.
 * @param clock wall-clock epoch millis, the same clock used to create tokens.
 */
class BlockFlowExecutor(
    private val driver: ScreenDriver,
    private val rateLimiter: RateLimiter,
    private val clock: () -> Long = System::currentTimeMillis,
    private val namesMatch: (expected: String, actual: String) -> Boolean = DEFAULT_NAMES_MATCH,
    private val config: BlockFlowConfig = BlockFlowConfig(),
) {

    /**
     * Blocks [target], starting from the feed video currently on screen.
     *
     * @param token created in the click handler of the Block button; null, stale, or reused tokens are rejected.
     * @param style the user's block style setting.
     * @param onProgress called on this coroutine as each [BlockStep] starts (for the overlay card). With
     *   [BlockStyle.STOP_AT_TIKTOK_DIALOG] it reports [BlockStep.AWAITING_USER_CONFIRMATION] when the user should
     *   tap TikTok's own Block button.
     */
    suspend fun run(
        token: UserIntentToken?,
        target: BlockTarget,
        style: BlockStyle,
        onProgress: (BlockStep) -> Unit = {},
    ): BlockResult {
        if (target.expectedDisplayName.isNullOrBlank() && target.expectedHandle.isNullOrBlank()) {
            return BlockResult.Rejected(RejectReason.NO_EXPECTED_IDENTITY)
        }
        acceptUserIntent(token, clock(), config.tokenMaxAgeMillis)?.let { return BlockResult.Rejected(it) }

        val attempt = Attempt(target, style, onProgress)
        attempt.enter(BlockStep.CHECK_RATE_LIMIT)
        val outcome = rateLimiter.runWithPermit(
            kind = ActionKind.BLOCK,
            accountId = target.accountId,
            isVerified = { it is BlockResult.Blocked },
        ) { attempt.execute() }
        return when (outcome) {
            is PermitOutcome.Ran -> outcome.value
            is PermitOutcome.Denied ->
                BlockResult.RateLimited(outcome.decision.retryAfterMillis, outcome.decision.reason)
        }
    }

    /** Screens this executor can open, shallowest first. Cleanup never goes deeper than what was opened. */
    private enum class Layer { NOTHING, PROFILE, SHARE_SHEET, DIALOG }

    /** State of one run. */
    private inner class Attempt(
        private val target: BlockTarget,
        private val style: BlockStyle,
        private val onProgress: (BlockStep) -> Unit,
    ) {
        private var step = BlockStep.CHECK_RATE_LIMIT

        /** Deepest screen this run has tried to open; bounds what cleanup may dismiss. */
        private var opened = Layer.NOTHING

        /** The target's handle, set only after READ_HANDLE confirmed the profile is the expected account. */
        private var handle: String? = null

        /** The run's single covering-sheet dismissal has been used. */
        private var overlayDismissed = false

        private val expectedName = target.expectedDisplayName?.takeIf { it.isNotBlank() }
        private val expectedHandle = target.expectedHandle?.takeIf { it.isNotBlank() }?.let(::normalizeHandle)

        fun enter(next: BlockStep) {
            step = next
            reportProgress { onProgress(next) }
        }

        suspend fun execute(): BlockResult = try {
            proceed()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The driver is in an unknown state: don't touch the screen any further.
            BlockResult.Aborted(step, BlockAbortReason.ERROR, handle, returnedToFeed = false, detail = e.javaClass.simpleName)
        }

        private suspend fun proceed(): BlockResult {
            // OPEN_PROFILE: start from the feed video the user was looking at.
            enter(BlockStep.OPEN_PROFILE)
            val feed = driver.snapshot()
            if (feed !is FeedVideo) return abort(BlockAbortReason.NOT_ON_FEED_VIDEO, feed)
            val feedName = feed.displayName
            if (expectedName != null && feedName != null && !namesMatch(expectedName, feedName)) {
                return abort(BlockAbortReason.FEED_SHOWS_DIFFERENT_ACCOUNT, feed)
            }
            // A LIVE creator's avatar opens the LIVE room, not the profile: use the name instead.
            val profileButton = (if (feed.signals.isLive) feed.displayNameNode else feed.avatarNode ?: feed.displayNameNode)
                ?: return abort(BlockAbortReason.NO_PROFILE_BUTTON, feed)
            opened = Layer.PROFILE
            if (!driver.click(profileButton)) return abort(BlockAbortReason.TAP_FAILED)

            // READ_HANDLE: the profile must be the expected account. It may be covered by a sheet (Shop advertisers'
            // profiles open with a product sheet on top); the profile underneath gets exactly the same checks.
            enter(BlockStep.READ_HANDLE)
            val seen = driver.awaitSnapshot(config.stepTimeoutMillis, config.pollMillis) { s ->
                val p = profileIn(s)
                p != null && p.handle != null && (expectedName == null || p.displayName != null)
            } ?: return abortAfterTimeout {
                if (profileIn(it) != null) BlockAbortReason.PROFILE_UNREADABLE else BlockAbortReason.PROFILE_DID_NOT_OPEN
            }
            val profile = checkNotNull(profileIn(seen))
            val readHandle = normalizeHandle(checkNotNull(profile.handle))
            if (expectedName != null && !namesMatch(expectedName, checkNotNull(profile.displayName))) {
                return abort(BlockAbortReason.PROFILE_NAME_MISMATCH, seen)
            }
            if (expectedHandle != null && expectedHandle != readHandle) {
                return abort(BlockAbortReason.PROFILE_HANDLE_MISMATCH, seen)
            }
            handle = readHandle
            if (profile.isBlockedByMe) {
                if (!blockedMessageMatches(profile, readHandle)) {
                    return abort(BlockAbortReason.BLOCKED_MESSAGE_MISMATCH, seen)
                }
                // Already blocked: tap nothing more, just go back (a covering sheet is left alone).
                enter(BlockStep.RETURN)
                return BlockResult.Blocked(
                    handle = readHandle,
                    displayName = profile.displayName,
                    alreadyBlocked = true,
                    returnedToFeed = unwind(seen, Layer.PROFILE),
                )
            }

            // DISMISS_OVERLAY: about to act on the profile, but a sheet covers it.
            if (seen is DismissibleSheet) {
                dismissOverlay(seen, readHandle) ?: return abortOverlay()
            }

            // OPEN_SHARE_SHEET: re-read the screen right before tapping Share. A sheet can slide in after the profile
            // was read and swallow the tap (seen on TikTok 47.9.3); it gets the same single dismissal.
            enter(BlockStep.OPEN_SHARE_SHEET)
            val ready = driver.awaitSnapshot(config.stepTimeoutMillis, config.pollMillis) {
                isTargetProfile(it as? Profile, readHandle) ||
                    (it is DismissibleSheet && isTargetProfile(it.underlyingProfile, readHandle))
            }
            if (ready == null) {
                val now = driver.snapshot()
                return when {
                    // Covering something we can't confirm is the target's profile: don't dismiss it.
                    now is DismissibleSheet -> abortOverlay(now)
                    now is Profile && now.handle != null -> abort(BlockAbortReason.PROFILE_HANDLE_MISMATCH, now)
                    else -> abort(BlockAbortReason.NO_SHARE_BUTTON, now)
                }
            }
            val shareProfile: Profile = if (ready is DismissibleSheet) {
                if (overlayDismissed) return abortOverlay(ready) // Still, or again, covered: never a second dismissal.
                val uncovered = dismissOverlay(ready, readHandle) ?: return abortOverlay()
                enter(BlockStep.OPEN_SHARE_SHEET)
                uncovered
            } else {
                ready as Profile
            }
            val shareButton = shareProfile.shareNode ?: return abort(BlockAbortReason.NO_SHARE_BUTTON, shareProfile)
            opened = Layer.SHARE_SHEET
            if (!driver.click(shareButton)) return abort(BlockAbortReason.TAP_FAILED)
            val sheet = awaitStep<ShareSheet> { it.blockNode != null }
            if (sheet == null) {
                val now = driver.snapshot()
                return if (now is ShareSheet) {
                    abort(BlockAbortReason.SHARE_SHEET_HAS_NO_BLOCK, now, atStep = BlockStep.TAP_BLOCK)
                } else {
                    abort(BlockAbortReason.SHARE_SHEET_DID_NOT_OPEN, now)
                }
            }

            // TAP_BLOCK
            enter(BlockStep.TAP_BLOCK)
            opened = Layer.DIALOG
            if (!driver.click(checkNotNull(sheet.blockNode))) return abort(BlockAbortReason.TAP_FAILED)

            // CHECK_DIALOG: TikTok's dialog must name the handle we read on the profile.
            enter(BlockStep.CHECK_DIALOG)
            val dialog = awaitStep<BlockDialog> { it.handle != null }
                ?: return abortAfterTimeout {
                    if (it is BlockDialog) BlockAbortReason.DIALOG_UNREADABLE else BlockAbortReason.BLOCK_DIALOG_DID_NOT_OPEN
                }
            if (normalizeHandle(checkNotNull(dialog.handle)) != readHandle) {
                return abort(BlockAbortReason.DIALOG_HANDLE_MISMATCH, dialog)
            }

            return when (style) {
                BlockStyle.ONE_TAP_FULL_FLOW -> confirmAndVerify(dialog, readHandle, profile.displayName)
                BlockStyle.STOP_AT_TIKTOK_DIALOG -> awaitUserDecision(readHandle, profile.displayName)
            }
        }

        private suspend fun confirmAndVerify(dialog: BlockDialog, handle: String, profileName: String?): BlockResult {
            enter(BlockStep.CONFIRM)
            val confirm = dialog.confirmNode ?: return abort(BlockAbortReason.DIALOG_HAS_NO_CONFIRM, dialog)
            if (!driver.click(confirm)) return abort(BlockAbortReason.TAP_FAILED)

            enter(BlockStep.VERIFY)
            val shown = awaitBlockedProfile(handle) ?: return abort(BlockAbortReason.BLOCK_NOT_VERIFIED)

            enter(BlockStep.RETURN)
            return BlockResult.Blocked(
                handle = handle,
                displayName = checkNotNull(profileIn(shown)).displayName ?: profileName,
                alreadyBlocked = false,
                returnedToFeed = unwind(shown, Layer.PROFILE),
            )
        }

        private suspend fun awaitUserDecision(handle: String, profileName: String?): BlockResult {
            enter(BlockStep.AWAITING_USER_CONFIRMATION)
            // Wait for the dialog to give way to a recognised screen. Unknown frames (transitions, parse misses)
            // don't count, so a flickering parse is never mistaken for the user's answer.
            val answered = driver.awaitSnapshot(config.userConfirmTimeoutMillis, config.pollMillis) {
                it is Profile || it is FeedVideo || it is ShareSheet || it is LongPressMenu ||
                    (it is DismissibleSheet && it.underlyingProfile != null)
            } ?: return BlockResult.TimedOut(handle) // Leave TikTok's dialog for the user; tap nothing.

            enter(BlockStep.VERIFY)
            val shown = answered.takeIf { blockedProfileIn(it, handle) != null } ?: awaitBlockedProfile(handle)

            enter(BlockStep.RETURN)
            if (shown == null) {
                // The dialog belongs to the user now: close a sheet or a profile, but never tap a dialog.
                return BlockResult.UserCancelled(handle, returnedToFeed = unwind(null, Layer.SHARE_SHEET))
            }
            return BlockResult.Blocked(
                handle = handle,
                displayName = checkNotNull(profileIn(shown)).displayName ?: profileName,
                alreadyBlocked = false,
                returnedToFeed = unwind(shown, Layer.PROFILE),
            )
        }

        /**
         * DISMISS_OVERLAY: closes [sheet] with the run's single dismissal, then waits for the target's profile
         * (read again, uncovered). The scrim is clicked only if it is clickable, so the driver uses ACTION_CLICK and
         * never a gesture that could land inside the sheet; otherwise it is one Back. Returns the uncovered profile,
         * or null if the run must stop (dismissal already used or failed, or the profile didn't come back).
         */
        private suspend fun dismissOverlay(sheet: DismissibleSheet, targetHandle: String): Profile? {
            if (overlayDismissed) return null
            enter(BlockStep.DISMISS_OVERLAY)
            overlayDismissed = true
            val scrim = sheet.dismissNode?.takeIf { it.isClickable }
            val acted = if (scrim != null) driver.click(scrim) else driver.back()
            if (!acted) return null
            return awaitStep<Profile> { isTargetProfile(it, targetHandle) }
        }

        /** Stops with [BlockAbortReason.OVERLAY_NOT_DISMISSED]; cleanup leaves any covering sheet for the user. */
        private suspend fun abortOverlay(current: ScreenSnapshot? = null): BlockResult.Aborted =
            abort(BlockAbortReason.OVERLAY_NOT_DISMISSED, current, atStep = BlockStep.DISMISS_OVERLAY)

        /** Waits for the target's profile showing it blocked, uncovered or under a sheet. Returns that snapshot. */
        private suspend fun awaitBlockedProfile(targetHandle: String): ScreenSnapshot? =
            driver.awaitSnapshot(config.stepTimeoutMillis, config.pollMillis) { blockedProfileIn(it, targetHandle) != null }

        private fun blockedProfileIn(screen: ScreenSnapshot, targetHandle: String): Profile? =
            profileIn(screen)?.takeIf { isBlockedProfile(it, targetHandle) }

        private suspend fun abortAfterTimeout(reasonFor: (ScreenSnapshot) -> BlockAbortReason): BlockResult.Aborted {
            val now = driver.snapshot()
            return abort(reasonFor(now), now)
        }

        /** Stops the flow: backs out of what this run opened, then reports where and why it stopped. */
        private suspend fun abort(
            reason: BlockAbortReason,
            current: ScreenSnapshot? = null,
            atStep: BlockStep = step,
        ): BlockResult.Aborted {
            val screen = current ?: driver.snapshot()
            val onFeed = unwind(screen, opened)
            return BlockResult.Aborted(atStep, reason, handle, onFeed, detail = "screen=${screen.kindName}")
        }

        /**
         * Backs out of the screens this run opened, deepest first, until the feed shows. Bounded: each layer is
         * dismissed at most once (so at most three actions), never one deeper than [allowed], and nothing is
         * done on a screen it doesn't recognise. Returns true if the feed is showing at the end.
         */
        private suspend fun unwind(start: ScreenSnapshot?, allowed: Layer): Boolean {
            var screen = start ?: driver.snapshot()
            val dismissed = mutableSetOf<Layer>()
            repeat(MAX_UNWIND_ACTIONS) {
                if (screen is FeedVideo) return true
                val layer = screen.layer() ?: return false
                if (layer > allowed || !dismissed.add(layer)) return false
                when (val s = screen) {
                    is BlockDialog -> s.cancelNode?.let { driver.click(it) } ?: driver.back()
                    is ShareSheet -> s.closeNode?.let { driver.click(it) } ?: driver.back()
                    else -> driver.back()
                }
                screen = driver.awaitSnapshot(config.stepTimeoutMillis, config.pollMillis) {
                    it is FeedVideo || (it.layer() != null && it.layer() != layer)
                } ?: return false
            }
            return screen is FeedVideo
        }

        private fun ScreenSnapshot.layer(): Layer? = when (this) {
            is Profile -> Layer.PROFILE
            is ShareSheet -> Layer.SHARE_SHEET
            is BlockDialog -> Layer.DIALOG
            // Never unwound: closing a covering sheet is DISMISS_OVERLAY's single, deliberate action.
            is DismissibleSheet -> null
            else -> null
        }

        private suspend inline fun <reified T : ScreenSnapshot> awaitStep(crossinline accept: (T) -> Boolean): T? =
            driver.awaitScreen(config.stepTimeoutMillis, config.pollMillis, accept)
    }

    /** The profile on [screen]: a plain profile, or the one under a covering sheet. Null for anything else. */
    private fun profileIn(screen: ScreenSnapshot): Profile? = when (screen) {
        is Profile -> screen
        is DismissibleSheet -> screen.underlyingProfile
        else -> null
    }

    /** [profile] is readable and is the account whose handle READ_HANDLE confirmed. */
    private fun isTargetProfile(profile: Profile?, handle: String): Boolean =
        profile?.handle?.let { normalizeHandle(it) == handle } == true

    private fun blockedMessageMatches(profile: Profile, handle: String): Boolean =
        profile.blockedMessageHandle?.let { normalizeHandle(it) == handle } ?: true

    private fun isBlockedProfile(profile: Profile, handle: String): Boolean =
        profile.isBlockedByMe &&
            (profile.handle?.let { normalizeHandle(it) == handle } ?: true) &&
            blockedMessageMatches(profile, handle)

    companion object {
        /** Default display-name comparison: trimmed, case-insensitive equality. */
        val DEFAULT_NAMES_MATCH: (expected: String, actual: String) -> Boolean =
            { expected, actual -> expected.trim().equals(actual.trim(), ignoreCase = true) }

        /** One action per layer (dialog, share sheet, profile). */
        private const val MAX_UNWIND_ACTIONS = 3
    }
}
