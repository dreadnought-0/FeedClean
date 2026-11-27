package io.github.dreadnought0.feedclean.block

import io.github.dreadnought0.feedclean.data.ActionKind
import io.github.dreadnought0.feedclean.settings.BlockStyle
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import io.github.dreadnought0.feedclean.testutil.FakeActionLogRepository
import io.github.dreadnought0.feedclean.testutil.FakeActionLogRepository.Entry
import io.github.dreadnought0.feedclean.testutil.FakeScreenDriver
import io.github.dreadnought0.feedclean.testutil.FakeScreenDriver.Companion.DEFAULT_HANDLE
import io.github.dreadnought0.feedclean.testutil.FakeScreenDriver.Companion.DEFAULT_NAME
import io.github.dreadnought0.feedclean.testutil.FakeScreenDriver.ProductSheet
import io.github.dreadnought0.feedclean.testutil.FakeScreenDriver.Screen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BlockFlowExecutorTest {

    private class Harness(scope: TestScope) {
        val clock = { scope.testScheduler.currentTime }
        val driver = FakeScreenDriver(clock)
        val log = FakeActionLogRepository()
        val limiter = RateLimiter(log, { FeedCleanSettings() }, clock)
        val steps = mutableListOf<BlockStep>()

        fun executor(
            config: BlockFlowConfig = BlockFlowConfig(),
            namesMatch: (String, String) -> Boolean = BlockFlowExecutor.DEFAULT_NAMES_MATCH,
        ) = BlockFlowExecutor(driver, limiter, clock, namesMatch, config)

        fun token() = UserIntentToken.fromUserClick("test.block", clock())

        suspend fun run(
            style: BlockStyle = BlockStyle.ONE_TAP_FULL_FLOW,
            target: BlockTarget = TARGET,
            token: UserIntentToken? = token(),
            executor: BlockFlowExecutor = executor(),
        ): BlockResult = executor.run(token, target, style) { steps += it }
    }

    private fun harness(scope: TestScope, configure: FakeScreenDriver.() -> Unit = {}) =
        Harness(scope).apply { driver.configure() }

    // ── Happy paths ────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun oneTapFullFlowBlocksVerifiesAndReturns() = runTest {
        val h = harness(this)

        val result = h.run()

        assertEquals(BlockResult.Blocked(DEFAULT_HANDLE, DEFAULT_NAME, alreadyBlocked = false, returnedToFeed = true), result)
        assertEquals(listOf(AVATAR, SHARE, SHEET_BLOCK, CONFIRM, BACK), h.driver.actions)
        assertEquals(
            listOf(
                BlockStep.CHECK_RATE_LIMIT, BlockStep.OPEN_PROFILE, BlockStep.READ_HANDLE, BlockStep.OPEN_SHARE_SHEET,
                BlockStep.TAP_BLOCK, BlockStep.CHECK_DIALOG, BlockStep.CONFIRM, BlockStep.VERIFY, BlockStep.RETURN,
            ),
            h.steps,
        )
        assertTrue(h.driver.blocked)
        assertEquals(Screen.FEED, h.driver.screen)
        assertEquals(listOf(Entry(ActionKind.BLOCK, h.clock(), ACCOUNT_ID, true)), h.log.entries)
    }

    @Test
    fun expectedHandleIsAcceptedWhenItMatches() = runTest {
        val h = harness(this)

        val result = h.run(target = BlockTarget(ACCOUNT_ID, null, "@Creator.One"))

        assertTrue(result is BlockResult.Blocked)
    }

    @Test
    fun liveCreatorIsOpenedByNameNotAvatar() = runTest {
        val h = harness(this) { feedIsLive = true }

        val result = h.run()

        assertTrue(result is BlockResult.Blocked)
        assertEquals("click:${FakeScreenDriver.FEED_NAME}", h.driver.actions.first())
    }

    @Test
    fun injectedNamesMatchIsUsed() = runTest {
        val h = harness(this) { profileDisplayName = "‎𝐂𝐫𝐞𝐚𝐭𝐨𝐫 𝐎𝐧𝐞" }
        val normalized = { s: String -> java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC).replace("‎", "") }

        val result = h.run(executor = h.executor(namesMatch = { a, b -> normalized(a) == normalized(b) }))

        assertTrue(result is BlockResult.Blocked)
    }

    @Test
    fun stopAtDialogWaitsForTheUsersOwnTap() = runTest {
        val h = harness(this) { userTapsDialogBlock(atMillis = 5_000) }

        val result = h.run(style = BlockStyle.STOP_AT_TIKTOK_DIALOG)

        assertEquals(BlockResult.Blocked(DEFAULT_HANDLE, DEFAULT_NAME, alreadyBlocked = false, returnedToFeed = true), result)
        assertEquals("the executor never taps TikTok's Block", listOf(AVATAR, SHARE, SHEET_BLOCK, BACK), h.driver.actions)
        assertEquals(listOf("user:block"), h.driver.userActions)
        assertTrue(BlockStep.AWAITING_USER_CONFIRMATION in h.steps)
        assertFalse(BlockStep.CONFIRM in h.steps)
        assertEquals(listOf(true), h.log.entries.map { it.verified })
    }

    @Test
    fun stopAtDialogUserCancels() = runTest {
        val h = harness(this) { userTapsDialogCancel(atMillis = 4_000) }

        val result = h.run(style = BlockStyle.STOP_AT_TIKTOK_DIALOG)

        assertEquals(BlockResult.UserCancelled(DEFAULT_HANDLE, returnedToFeed = true), result)
        assertEquals(listOf(AVATAR, SHARE, SHEET_BLOCK, BACK), h.driver.actions)
        assertFalse(h.driver.blocked)
        assertEquals(listOf(false), h.log.entries.map { it.verified })
    }

    @Test
    fun stopAtDialogTimesOutAndLeavesTheDialogAlone() = runTest {
        val h = harness(this)

        val result = h.run(style = BlockStyle.STOP_AT_TIKTOK_DIALOG)

        assertEquals(BlockResult.TimedOut(DEFAULT_HANDLE), result)
        assertEquals(listOf(AVATAR, SHARE, SHEET_BLOCK), h.driver.actions)
        assertEquals(Screen.BLOCK_DIALOG, h.driver.screen)
        assertTrue(h.clock() >= 30_000)
        assertEquals(listOf(false), h.log.entries.map { it.verified })
    }

    @Test
    fun stopAtDialogIgnoresAnUnknownFlicker() = runTest {
        val h = harness(this) {
            at(3_000) { screen = Screen.UNKNOWN }
            at(3_400) { screen = Screen.BLOCK_DIALOG }
        }

        val result = h.run(style = BlockStyle.STOP_AT_TIKTOK_DIALOG)

        assertEquals(BlockResult.TimedOut(DEFAULT_HANDLE), result)
        assertEquals(listOf(AVATAR, SHARE, SHEET_BLOCK), h.driver.actions)
    }

    @Test
    fun alreadyBlockedTapsNothingButAvatarAndBack() = runTest {
        val h = harness(this) { blocked = true }

        val result = h.run()

        assertEquals(BlockResult.Blocked(DEFAULT_HANDLE, DEFAULT_NAME, alreadyBlocked = true, returnedToFeed = true), result)
        assertEquals(listOf(AVATAR, BACK), h.driver.actions)
        assertEquals(listOf(BlockStep.CHECK_RATE_LIMIT, BlockStep.OPEN_PROFILE, BlockStep.READ_HANDLE, BlockStep.RETURN), h.steps)
        assertEquals(listOf(true), h.log.entries.map { it.verified })
    }

    // ── Aborts: each one cleans up exactly what it opened and taps nothing else ───────────────────────────

    @Test
    fun abortsWhenNotOnAFeedVideo() = runTest {
        val h = harness(this) { screen = Screen.UNKNOWN }

        val result = h.run()

        assertAborted(result, BlockStep.OPEN_PROFILE, BlockAbortReason.NOT_ON_FEED_VIDEO, handle = null, onFeed = false)
        assertEquals(emptyList<String>(), h.driver.actions)
    }

    @Test
    fun abortsWhenTheFeedShowsAnotherAccount() = runTest {
        val h = harness(this) { feedDisplayName = "Someone Else" }

        val result = h.run()

        assertAborted(result, BlockStep.OPEN_PROFILE, BlockAbortReason.FEED_SHOWS_DIFFERENT_ACCOUNT, handle = null, onFeed = true)
        assertEquals(emptyList<String>(), h.driver.actions)
    }

    @Test
    fun abortsWhenThereIsNothingToOpenTheProfileWith() = runTest {
        val h = harness(this) {
            feedHasAvatar = false
            feedHasNameNode = false
        }

        val result = h.run()

        assertAborted(result, BlockStep.OPEN_PROFILE, BlockAbortReason.NO_PROFILE_BUTTON, handle = null, onFeed = true)
        assertEquals(emptyList<String>(), h.driver.actions)
    }

    @Test
    fun abortsWhenTheAvatarTapFails() = runTest {
        val h = harness(this) { failingTags = setOf(FakeScreenDriver.FEED_AVATAR) }

        val result = h.run()

        assertAborted(result, BlockStep.OPEN_PROFILE, BlockAbortReason.TAP_FAILED, handle = null, onFeed = true)
        assertEquals(listOf(AVATAR), h.driver.actions)
    }

    @Test
    fun abortsWhenTheProfileNeverOpens() = runTest {
        val h = harness(this) { neverAppears = setOf(Screen.PROFILE) }

        val result = h.run()

        assertAborted(result, BlockStep.READ_HANDLE, BlockAbortReason.PROFILE_DID_NOT_OPEN, handle = null, onFeed = true)
        assertEquals("still on the feed: no Back", listOf(AVATAR), h.driver.actions)
    }

    @Test
    fun stepTimeoutIsConfigurable() = runTest {
        val h = harness(this) { neverAppears = setOf(Screen.PROFILE) }

        h.run(executor = h.executor(config = BlockFlowConfig(stepTimeoutMillis = 1_000)))

        assertTrue("took ${h.clock()} ms", h.clock() in 1_000..1_200)
    }

    @Test
    fun abortsWhenTheProfileHasNoHandle() = runTest {
        val h = harness(this) { profileHandle = null }

        val result = h.run()

        assertAborted(result, BlockStep.READ_HANDLE, BlockAbortReason.PROFILE_UNREADABLE, handle = null, onFeed = true)
        assertEquals(listOf(AVATAR, BACK), h.driver.actions)
    }

    @Test
    fun abortsWhenTheProfileNameDiffers() = runTest {
        val h = harness(this) { profileDisplayName = "Creator 0ne" }

        val result = h.run()

        assertAborted(result, BlockStep.READ_HANDLE, BlockAbortReason.PROFILE_NAME_MISMATCH, handle = null, onFeed = true)
        assertEquals(listOf(AVATAR, BACK), h.driver.actions)
    }

    @Test
    fun abortsWhenTheProfileHandleDiffers() = runTest {
        val h = harness(this)

        val result = h.run(target = BlockTarget(ACCOUNT_ID, DEFAULT_NAME, "creator.one.real"))

        assertAborted(result, BlockStep.READ_HANDLE, BlockAbortReason.PROFILE_HANDLE_MISMATCH, handle = null, onFeed = true)
        assertEquals(listOf(AVATAR, BACK), h.driver.actions)
    }

    @Test
    fun abortsWhenTheBlockedMessageNamesAnotherHandle() = runTest {
        val h = harness(this) {
            blocked = true
            blockedMessageHandleOverride = "someone.else"
        }

        val result = h.run()

        assertAborted(result, BlockStep.READ_HANDLE, BlockAbortReason.BLOCKED_MESSAGE_MISMATCH, DEFAULT_HANDLE, onFeed = true)
        assertEquals(listOf(AVATAR, BACK), h.driver.actions)
    }

    @Test
    fun abortsWhenTheProfileHasNoShareButton() = runTest {
        val h = harness(this) { profileHasShare = false }

        val result = h.run()

        assertAborted(result, BlockStep.OPEN_SHARE_SHEET, BlockAbortReason.NO_SHARE_BUTTON, DEFAULT_HANDLE, onFeed = true)
        assertEquals(listOf(AVATAR, BACK), h.driver.actions)
    }

    @Test
    fun abortsWhenTheShareSheetNeverOpens() = runTest {
        val h = harness(this) { neverAppears = setOf(Screen.SHARE_SHEET) }

        val result = h.run()

        assertAborted(result, BlockStep.OPEN_SHARE_SHEET, BlockAbortReason.SHARE_SHEET_DID_NOT_OPEN, DEFAULT_HANDLE, onFeed = true)
        assertEquals(listOf(AVATAR, SHARE, BACK), h.driver.actions)
    }

    @Test
    fun abortsWhenTheShareSheetLacksBlock() = runTest {
        val h = harness(this) { sheetHasBlock = false }

        val result = h.run()

        assertAborted(result, BlockStep.TAP_BLOCK, BlockAbortReason.SHARE_SHEET_HAS_NO_BLOCK, DEFAULT_HANDLE, onFeed = true)
        assertEquals(listOf(AVATAR, SHARE, SHEET_CLOSE, BACK), h.driver.actions)
    }

    @Test
    fun shareSheetWithoutCloseButtonIsClosedWithBack() = runTest {
        val h = harness(this) {
            sheetHasBlock = false
            sheetHasClose = false
        }

        val result = h.run()

        assertAborted(result, BlockStep.TAP_BLOCK, BlockAbortReason.SHARE_SHEET_HAS_NO_BLOCK, DEFAULT_HANDLE, onFeed = true)
        assertEquals(listOf(AVATAR, SHARE, BACK, BACK), h.driver.actions)
    }

    @Test
    fun abortsWhenTheDialogNeverOpens() = runTest {
        val h = harness(this) { neverAppears = setOf(Screen.BLOCK_DIALOG) }

        val result = h.run()

        assertAborted(result, BlockStep.CHECK_DIALOG, BlockAbortReason.BLOCK_DIALOG_DID_NOT_OPEN, DEFAULT_HANDLE, onFeed = true)
        assertEquals(listOf(AVATAR, SHARE, SHEET_BLOCK, SHEET_CLOSE, BACK), h.driver.actions)
    }

    @Test
    fun abortsAndCancelsWhenTheDialogNamesAnotherHandle() = runTest {
        val h = harness(this) { dialogHandleOverride = "creator.one_copy" }

        val result = h.run()

        assertAborted(result, BlockStep.CHECK_DIALOG, BlockAbortReason.DIALOG_HANDLE_MISMATCH, DEFAULT_HANDLE, onFeed = true)
        assertEquals(listOf(AVATAR, SHARE, SHEET_BLOCK, CANCEL, BACK), h.driver.actions)
        assertFalse(h.driver.blocked)
    }

    @Test
    fun dialogHandleMismatchAbortsInStopModeToo() = runTest {
        val h = harness(this) { dialogHandleOverride = "creator.one_copy" }

        val result = h.run(style = BlockStyle.STOP_AT_TIKTOK_DIALOG)

        assertAborted(result, BlockStep.CHECK_DIALOG, BlockAbortReason.DIALOG_HANDLE_MISMATCH, DEFAULT_HANDLE, onFeed = true)
        assertFalse(BlockStep.AWAITING_USER_CONFIRMATION in h.steps)
        assertEquals(listOf(AVATAR, SHARE, SHEET_BLOCK, CANCEL, BACK), h.driver.actions)
    }

    @Test
    fun abortsAndCancelsWhenTheDialogHandleIsUnreadable() = runTest {
        val h = harness(this) { dialogHandleMissing = true }

        val result = h.run()

        assertAborted(result, BlockStep.CHECK_DIALOG, BlockAbortReason.DIALOG_UNREADABLE, DEFAULT_HANDLE, onFeed = true)
        assertEquals(listOf(AVATAR, SHARE, SHEET_BLOCK, CANCEL, BACK), h.driver.actions)
    }

    @Test
    fun dialogWithoutCancelIsClosedWithBack() = runTest {
        val h = harness(this) {
            dialogHandleOverride = "creator.one_copy"
            dialogHasCancel = false
        }

        val result = h.run()

        assertAborted(result, BlockStep.CHECK_DIALOG, BlockAbortReason.DIALOG_HANDLE_MISMATCH, DEFAULT_HANDLE, onFeed = true)
        assertEquals(listOf(AVATAR, SHARE, SHEET_BLOCK, BACK, BACK), h.driver.actions)
    }

    @Test
    fun abortsAndCancelsWhenTheDialogHasNoBlockButton() = runTest {
        val h = harness(this) { dialogHasConfirm = false }

        val result = h.run()

        assertAborted(result, BlockStep.CONFIRM, BlockAbortReason.DIALOG_HAS_NO_CONFIRM, DEFAULT_HANDLE, onFeed = true)
        assertEquals(listOf(AVATAR, SHARE, SHEET_BLOCK, CANCEL, BACK), h.driver.actions)
    }

    @Test
    fun abortsWhenTheBlockIsNotVerified() = runTest {
        val h = harness(this) { confirmTakesEffect = false }

        val result = h.run()

        assertAborted(result, BlockStep.VERIFY, BlockAbortReason.BLOCK_NOT_VERIFIED, DEFAULT_HANDLE, onFeed = true)
        assertTrue((result as BlockResult.Aborted).mayHaveBlocked)
        assertEquals("exactly one confirm, then cancel", listOf(AVATAR, SHARE, SHEET_BLOCK, CONFIRM, CANCEL, BACK), h.driver.actions)
        assertEquals(listOf(false), h.log.entries.map { it.verified })
    }

    @Test
    fun driverErrorAbortsWithoutFurtherTaps() = runTest {
        val h = harness(this) { throwingTag = FakeScreenDriver.PROFILE_SHARE }

        val result = h.run()

        assertAborted(result, BlockStep.OPEN_SHARE_SHEET, BlockAbortReason.ERROR, DEFAULT_HANDLE, onFeed = false)
        assertEquals(listOf(AVATAR, SHARE), h.driver.actions)
        assertEquals(listOf(false), h.log.entries.map { it.verified })
    }

    @Test
    fun failingProgressCallbackDoesNotDerailTheFlow() = runTest {
        val h = harness(this)

        val result = h.executor().run(h.token(), TARGET, BlockStyle.ONE_TAP_FULL_FLOW) { error("UI bug") }

        assertTrue(result is BlockResult.Blocked)
    }

    @Test
    fun everyAbortIsRecordedAsAnUnverifiedAttempt() = runTest {
        val h = harness(this) { profileDisplayName = "Someone Else" }

        h.run()

        assertEquals(listOf(Entry(ActionKind.BLOCK, h.clock(), ACCOUNT_ID, false)), h.log.entries)
    }

    // ── A product sheet covering the profile (TikTok 47.9.3: a Shop advertiser's avatar opens both) ────────

    @Test
    fun productSheetAfterTheAvatarIsDismissedOnceThenTheBlockCompletes() = runTest {
        val h = harness(this) { productSheet = ProductSheet.WITH_PROFILE }

        val result = h.run()

        assertEquals(BlockResult.Blocked(DEFAULT_HANDLE, DEFAULT_NAME, alreadyBlocked = false, returnedToFeed = true), result)
        assertEquals(listOf(AVATAR, SCRIM, SHARE, SHEET_BLOCK, CONFIRM, BACK), h.driver.actions)
        assertEquals("only the scrim is touched while the sheet shows", listOf(SCRIM), h.driver.sheetActions)
        assertEquals(listOf(SCRIM), h.driver.actions.filter { it.startsWith("click:product.") })
        assertEquals(
            listOf(
                BlockStep.CHECK_RATE_LIMIT, BlockStep.OPEN_PROFILE, BlockStep.READ_HANDLE, BlockStep.DISMISS_OVERLAY,
                BlockStep.OPEN_SHARE_SHEET, BlockStep.TAP_BLOCK, BlockStep.CHECK_DIALOG, BlockStep.CONFIRM,
                BlockStep.VERIFY, BlockStep.RETURN,
            ),
            h.steps,
        )
        assertTrue(h.driver.blocked)
        assertEquals(Screen.FEED, h.driver.screen)
        assertEquals(listOf(true), h.log.entries.map { it.verified })
    }

    @Test
    fun productSheetSlidingInBeforeTheShareTapIsDismissedOnce() = runTest {
        val h = harness(this) { productSheet = ProductSheet.AFTER_PROFILE_READ }

        val result = h.run()

        assertEquals(BlockResult.Blocked(DEFAULT_HANDLE, DEFAULT_NAME, alreadyBlocked = false, returnedToFeed = true), result)
        assertEquals("Share is tapped on the uncovered profile", listOf(AVATAR, SCRIM, SHARE, SHEET_BLOCK, CONFIRM, BACK), h.driver.actions)
        assertEquals(listOf(SCRIM), h.driver.sheetActions)
        assertEquals(
            listOf(
                BlockStep.CHECK_RATE_LIMIT, BlockStep.OPEN_PROFILE, BlockStep.READ_HANDLE, BlockStep.OPEN_SHARE_SHEET,
                BlockStep.DISMISS_OVERLAY, BlockStep.OPEN_SHARE_SHEET, BlockStep.TAP_BLOCK, BlockStep.CHECK_DIALOG,
                BlockStep.CONFIRM, BlockStep.VERIFY, BlockStep.RETURN,
            ),
            h.steps,
        )
    }

    @Test
    fun productSheetWithoutAScrimIsClosedWithExactlyOneBack() = runTest {
        val h = harness(this) {
            productSheet = ProductSheet.WITH_PROFILE
            productSheetHasDismissNode = false
        }

        val result = h.run()

        assertEquals(BlockResult.Blocked(DEFAULT_HANDLE, DEFAULT_NAME, alreadyBlocked = false, returnedToFeed = true), result)
        assertEquals(listOf(AVATAR, BACK, SHARE, SHEET_BLOCK, CONFIRM, BACK), h.driver.actions)
        assertEquals(listOf(BACK), h.driver.sheetActions)
    }

    @Test
    fun nonClickableScrimIsNeverTappedBackIsUsedInstead() = runTest {
        val h = harness(this) {
            productSheet = ProductSheet.WITH_PROFILE
            productSheetDismissNodeClickable = false
        }

        val result = h.run()

        assertTrue(result is BlockResult.Blocked)
        assertEquals("no gesture tap into the sheet", listOf(BACK), h.driver.sheetActions)
        assertEquals(listOf(AVATAR, BACK, SHARE, SHEET_BLOCK, CONFIRM, BACK), h.driver.actions)
    }

    @Test
    fun racingProductSheetWithoutAScrimIsClosedWithOneBack() = runTest {
        val h = harness(this) {
            productSheet = ProductSheet.AFTER_PROFILE_READ
            productSheetHasDismissNode = false
        }

        val result = h.run()

        assertTrue(result is BlockResult.Blocked)
        assertEquals(listOf(AVATAR, BACK, SHARE, SHEET_BLOCK, CONFIRM, BACK), h.driver.actions)
    }

    @Test
    fun productSheetThatWontCloseAbortsAfterOneDismissalWithoutTappingShare() = runTest {
        val h = harness(this) {
            productSheet = ProductSheet.WITH_PROFILE
            productSheetCloses = false
        }

        val result = h.run()

        assertAborted(result, BlockStep.DISMISS_OVERLAY, BlockAbortReason.OVERLAY_NOT_DISMISSED, DEFAULT_HANDLE, onFeed = false)
        assertFalse((result as BlockResult.Aborted).mayHaveBlocked)
        assertEquals("one dismissal, no Share, no cleanup on the sheet", listOf(AVATAR, SCRIM), h.driver.actions)
        assertEquals(Screen.PRODUCT_SHEET, h.driver.screen)
        assertFalse(h.driver.blocked)
        assertEquals(listOf(false), h.log.entries.map { it.verified })
    }

    @Test
    fun productSheetThatWontCloseWithBackAbortsAfterOneBack() = runTest {
        val h = harness(this) {
            productSheet = ProductSheet.WITH_PROFILE
            productSheetHasDismissNode = false
            productSheetCloses = false
        }

        val result = h.run()

        assertAborted(result, BlockStep.DISMISS_OVERLAY, BlockAbortReason.OVERLAY_NOT_DISMISSED, DEFAULT_HANDLE, onFeed = false)
        assertEquals(listOf(AVATAR, BACK), h.driver.actions)
    }

    @Test
    fun racingProductSheetThatWontCloseAbortsAfterOneDismissal() = runTest {
        val h = harness(this) {
            productSheet = ProductSheet.AFTER_PROFILE_READ
            productSheetCloses = false
        }

        val result = h.run()

        assertAborted(result, BlockStep.DISMISS_OVERLAY, BlockAbortReason.OVERLAY_NOT_DISMISSED, DEFAULT_HANDLE, onFeed = false)
        assertEquals(listOf(AVATAR, SCRIM), h.driver.actions)
    }

    @Test
    fun productSheetThatComesBackIsNeverDismissedTwice() = runTest {
        val h = harness(this) {
            productSheet = ProductSheet.WITH_PROFILE
            productSheetReturns = true
        }

        val result = h.run()

        assertAborted(result, BlockStep.DISMISS_OVERLAY, BlockAbortReason.OVERLAY_NOT_DISMISSED, DEFAULT_HANDLE, onFeed = false)
        assertEquals("exactly one dismissal and no Share tap", listOf(AVATAR, SCRIM), h.driver.actions)
        assertEquals(Screen.PRODUCT_SHEET, h.driver.screen)
    }

    @Test
    fun failedScrimTapAbortsWithoutAnotherAttempt() = runTest {
        val h = harness(this) {
            productSheet = ProductSheet.WITH_PROFILE
            failingTags = setOf(FakeScreenDriver.PRODUCT_SCRIM)
        }

        val result = h.run()

        assertAborted(result, BlockStep.DISMISS_OVERLAY, BlockAbortReason.OVERLAY_NOT_DISMISSED, DEFAULT_HANDLE, onFeed = false)
        assertEquals("no Back as a second dismissal", listOf(AVATAR, SCRIM), h.driver.actions)
    }

    @Test
    fun alreadyBlockedUnderAProductSheetTapsNothingAfterReading() = runTest {
        val h = harness(this) {
            productSheet = ProductSheet.WITH_PROFILE
            blocked = true
        }

        val result = h.run()

        assertEquals(BlockResult.Blocked(DEFAULT_HANDLE, DEFAULT_NAME, alreadyBlocked = true, returnedToFeed = false), result)
        assertEquals(listOf(AVATAR), h.driver.actions)
        assertEquals(listOf(BlockStep.CHECK_RATE_LIMIT, BlockStep.OPEN_PROFILE, BlockStep.READ_HANDLE, BlockStep.RETURN), h.steps)
        assertEquals(listOf(true), h.log.entries.map { it.verified })
    }

    @Test
    fun blockedMessageMismatchUnderAProductSheetTapsNothingAfterReading() = runTest {
        val h = harness(this) {
            productSheet = ProductSheet.WITH_PROFILE
            blocked = true
            blockedMessageHandleOverride = "someone.else"
        }

        val result = h.run()

        assertAborted(result, BlockStep.READ_HANDLE, BlockAbortReason.BLOCKED_MESSAGE_MISMATCH, DEFAULT_HANDLE, onFeed = false)
        assertEquals(listOf(AVATAR), h.driver.actions)
    }

    @Test
    fun nameMismatchUnderAProductSheetAbortsWithoutDismissing() = runTest {
        val h = harness(this) {
            productSheet = ProductSheet.WITH_PROFILE
            profileDisplayName = "Creator 0ne"
        }

        val result = h.run()

        assertAborted(result, BlockStep.READ_HANDLE, BlockAbortReason.PROFILE_NAME_MISMATCH, handle = null, onFeed = false)
        assertEquals(listOf(AVATAR), h.driver.actions)
        assertEquals(emptyList<String>(), h.driver.sheetActions)
        assertFalse(BlockStep.DISMISS_OVERLAY in h.steps)
    }

    @Test
    fun handleMismatchUnderAProductSheetAbortsWithoutDismissing() = runTest {
        val h = harness(this) { productSheet = ProductSheet.WITH_PROFILE }

        val result = h.run(target = BlockTarget(ACCOUNT_ID, DEFAULT_NAME, "creator.one.real"))

        assertAborted(result, BlockStep.READ_HANDLE, BlockAbortReason.PROFILE_HANDLE_MISMATCH, handle = null, onFeed = false)
        assertEquals(listOf(AVATAR), h.driver.actions)
    }

    @Test
    fun productSheetWithNoReadableProfileBelowMeansTheProfileDidNotOpen() = runTest {
        val h = harness(this) {
            productSheet = ProductSheet.WITH_PROFILE
            productSheetShowsProfile = false
        }

        val result = h.run()

        assertAborted(result, BlockStep.READ_HANDLE, BlockAbortReason.PROFILE_DID_NOT_OPEN, handle = null, onFeed = false)
        assertEquals(listOf(AVATAR), h.driver.actions)
    }

    @Test
    fun racingSheetOverAnUnreadableScreenIsNotDismissed() = runTest {
        val h = harness(this) {
            productSheet = ProductSheet.AFTER_PROFILE_READ
            productSheetShowsProfile = false
        }

        val result = h.run()

        assertAborted(result, BlockStep.DISMISS_OVERLAY, BlockAbortReason.OVERLAY_NOT_DISMISSED, DEFAULT_HANDLE, onFeed = false)
        assertEquals(listOf(AVATAR), h.driver.actions)
    }

    @Test
    fun stopAtDialogUnderAProductSheetDismissesOnceAndLeavesTikToksBlockToTheUser() = runTest {
        val h = harness(this) {
            productSheet = ProductSheet.WITH_PROFILE
            userTapsDialogBlock(atMillis = 5_000)
        }

        val result = h.run(style = BlockStyle.STOP_AT_TIKTOK_DIALOG)

        assertEquals(BlockResult.Blocked(DEFAULT_HANDLE, DEFAULT_NAME, alreadyBlocked = false, returnedToFeed = true), result)
        assertEquals(listOf(AVATAR, SCRIM, SHARE, SHEET_BLOCK, BACK), h.driver.actions)
        assertEquals(listOf("user:block"), h.driver.userActions)
        assertFalse(BlockStep.CONFIRM in h.steps)
    }

    @Test
    fun stopAtDialogRunsTheSameStepsAsOneTapUpToTheDialog() = runTest {
        val oneTap = harness(this) { productSheet = ProductSheet.AFTER_PROFILE_READ }
        oneTap.run()
        val stop = harness(this) { productSheet = ProductSheet.AFTER_PROFILE_READ }

        val result = stop.run(style = BlockStyle.STOP_AT_TIKTOK_DIALOG)

        assertEquals(BlockResult.TimedOut(DEFAULT_HANDLE), result)
        assertEquals(listOf(AVATAR, SCRIM, SHARE, SHEET_BLOCK), stop.driver.actions)
        val upToDialog = { steps: List<BlockStep> -> steps.take(steps.indexOf(BlockStep.CHECK_DIALOG) + 1) }
        assertEquals(upToDialog(oneTap.steps), upToDialog(stop.steps))
    }

    @Test
    fun blockIsVerifiedThroughASheetThatReappearsAndTheSheetIsLeftAlone() = runTest {
        val h = harness(this) { productSheetAfterBlock = true }

        val result = h.run()

        assertEquals(BlockResult.Blocked(DEFAULT_HANDLE, DEFAULT_NAME, alreadyBlocked = false, returnedToFeed = false), result)
        assertEquals(listOf(AVATAR, SHARE, SHEET_BLOCK, CONFIRM), h.driver.actions)
        assertEquals(emptyList<String>(), h.driver.sheetActions)
        assertEquals(listOf(true), h.log.entries.map { it.verified })
    }

    @Test
    fun stopAtDialogVerifiesThroughASheetThatReappears() = runTest {
        val h = harness(this) {
            productSheetAfterBlock = true
            userTapsDialogBlock(atMillis = 5_000)
        }

        val result = h.run(style = BlockStyle.STOP_AT_TIKTOK_DIALOG)

        assertEquals(BlockResult.Blocked(DEFAULT_HANDLE, DEFAULT_NAME, alreadyBlocked = false, returnedToFeed = false), result)
        assertEquals(listOf(AVATAR, SHARE, SHEET_BLOCK), h.driver.actions)
    }

    @Test
    fun onlyStepsAfterTheDialogCheckMayHaveBlocked() {
        assertEquals(
            setOf(BlockStep.AWAITING_USER_CONFIRMATION, BlockStep.CONFIRM, BlockStep.VERIFY, BlockStep.RETURN),
            BlockStep.entries.filter { it.isAfterDialogCheck }.toSet(),
        )
    }

    // ── Rejections and rate limiting: nothing touched, nothing logged ──────────────────────────────────────

    @Test
    fun missingTokenIsRejected() = runTest {
        val h = harness(this)

        val result = h.run(token = null)

        assertEquals(BlockResult.Rejected(RejectReason.MISSING_TOKEN), result)
        assertUntouched(h)
    }

    @Test
    fun staleTokenIsRejected() = runTest {
        val h = harness(this)
        val token = h.token()
        testScheduler.advanceTimeBy(10_001)

        val result = h.run(token = token)

        assertEquals(BlockResult.Rejected(RejectReason.STALE_TOKEN), result)
        assertUntouched(h)
    }

    @Test
    fun reusedTokenIsRejected() = runTest {
        val h = harness(this)
        val token = h.token()
        h.run(token = token)
        val actionsAfterFirstRun = h.driver.actions.toList()

        val result = h.run(token = token)

        assertEquals(BlockResult.Rejected(RejectReason.TOKEN_ALREADY_USED), result)
        assertEquals(actionsAfterFirstRun, h.driver.actions)
        assertEquals(1, h.log.entries.size)
    }

    @Test
    fun targetWithoutIdentityIsRejected() = runTest {
        val h = harness(this)

        val result = h.run(target = BlockTarget(ACCOUNT_ID, " ", null))

        assertEquals(BlockResult.Rejected(RejectReason.NO_EXPECTED_IDENTITY), result)
        assertUntouched(h)
    }

    @Test
    fun rateLimitedRunTouchesNothing() = runTest {
        val h = harness(this)
        h.log.seed(h.clock() - 1_000)

        val result = h.run()

        assertEquals(BlockResult.RateLimited(4_000, DenyReason.MIN_INTERVAL), result)
        assertEquals(0, h.driver.snapshotCount)
        assertEquals(emptyList<String>(), h.driver.actions)
        assertEquals(1, h.log.entries.size)
        assertEquals(listOf(BlockStep.CHECK_RATE_LIMIT), h.steps)
    }

    @Test
    fun secondBlockRightAfterTheFirstIsRateLimited() = runTest {
        val h = harness(this)
        h.run()
        h.driver.actions.clear()
        h.driver.blocked = false

        val result = h.run()

        assertTrue(result is BlockResult.RateLimited)
        assertEquals(emptyList<String>(), h.driver.actions)
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────────────────────────

    private fun assertAborted(
        result: BlockResult,
        step: BlockStep,
        reason: BlockAbortReason,
        handle: String?,
        onFeed: Boolean,
    ) {
        assertTrue("expected Aborted but was $result", result is BlockResult.Aborted)
        result as BlockResult.Aborted
        assertEquals(step, result.step)
        assertEquals(reason, result.reason)
        assertEquals(handle, result.handleLearned)
        assertEquals(onFeed, result.returnedToFeed)
    }

    private fun assertUntouched(h: Harness) {
        assertEquals(0, h.driver.snapshotCount)
        assertEquals(emptyList<String>(), h.driver.actions)
        assertEquals(emptyList<Entry>(), h.log.entries)
        assertEquals(emptyList<BlockStep>(), h.steps)
    }

    private companion object {
        const val ACCOUNT_ID = 42L
        val TARGET = BlockTarget(ACCOUNT_ID, DEFAULT_NAME, null)

        const val AVATAR = "click:${FakeScreenDriver.FEED_AVATAR}"
        const val SHARE = "click:${FakeScreenDriver.PROFILE_SHARE}"
        const val SHEET_BLOCK = "click:${FakeScreenDriver.SHEET_BLOCK}"
        const val SHEET_CLOSE = "click:${FakeScreenDriver.SHEET_CLOSE}"
        const val CONFIRM = "click:${FakeScreenDriver.DIALOG_CONFIRM}"
        const val CANCEL = "click:${FakeScreenDriver.DIALOG_CANCEL}"
        const val SCRIM = "click:${FakeScreenDriver.PRODUCT_SCRIM}"
        const val BACK = "back"
    }
}
