package io.github.dreadnought0.feedclean.testutil

import io.github.dreadnought0.feedclean.detect.ScreenSnapshot
import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.detect.UiNode
import io.github.dreadnought0.feedclean.service.ScreenDriver
import kotlinx.coroutines.delay

/**
 * A small simulated TikTok for executor tests, driven through [ScreenDriver] only.
 *
 * Screens: Feed → Profile → ShareSheet → BlockDialog → (blocked) Profile, and
 * Feed → LongPressMenu → (ReasonPicker) → Feed. Optionally a product sheet ([Screen.PRODUCT_SHEET], parsed as a
 * [ScreenSnapshot.DismissibleSheet]) covers the profile: see [productSheet]. Every executor action is appended to
 * [actions] ("click:<tag>", "longPress:<tag>", "back", …) so tests can assert exactly what was tapped; actions
 * taken while the product sheet showed are also in [sheetActions]. Screen changes take [latencyMillis] of virtual
 * time; use it with `runTest` and pass `{ testScheduler.currentTime }` as [clock] so timeouts elapse instantly.
 *
 * Faults are opt-in through the public vars; the defaults model the observed happy path.
 */
class FakeScreenDriver(private val clock: () -> Long) : ScreenDriver {

    enum class Screen {
        FEED, PROFILE, SHARE_SHEET, BLOCK_DIALOG, LONG_PRESS_MENU, OTHER_MENU, REASON_PICKER, UNKNOWN,

        /** TikTok's product sheet over the profile ("Add to cart" / "Buy now"); the profile's Share is covered. */
        PRODUCT_SHEET,
    }

    /** When the product sheet covers the profile (TikTok 47.9.3, Shop advertisers' profiles). */
    enum class ProductSheet {
        NONE,

        /** The avatar tap opens the profile with the sheet already on top. */
        WITH_PROFILE,

        /** The profile opens uncovered; the sheet slides in right after its first snapshot (the race before Share). */
        AFTER_PROFILE_READ,
    }

    // ── Scenario and faults ────────────────────────────────────────────────────────────────────────────────

    /** Current screen. Set it before a run to start somewhere else. */
    var screen: Screen = Screen.FEED
    var latencyMillis: Long = 300

    var feedDisplayName: String? = DEFAULT_NAME
    var feedIsLive: Boolean = false

    /** The feed page is an in-feed LIVE preview (a tap/long-press there would enter the live room). */
    var feedIsLivePreview: Boolean = false
    var feedHasAvatar: Boolean = true
    var feedHasNameNode: Boolean = true
    var feedHasVideoNode: Boolean = true

    /** Index of the feed page; changes when TikTok moves to the next video. Page signature is "page-<index>". */
    var pageIndex: Int = 0

    var profileDisplayName: String? = DEFAULT_NAME
    var profileHandle: String? = DEFAULT_HANDLE
    var profileHasShare: Boolean = true

    /** Whether the account is blocked by me. Set true before a run for an already-blocked profile. */
    var blocked: Boolean = false

    /** Handle shown in "You blocked …"; null means the profile's handle. */
    var blockedMessageHandleOverride: String? = null

    var sheetHasBlock: Boolean = true
    var sheetHasClose: Boolean = true

    /** Handle in the dialog title; null means the profile's handle. Ignored when [dialogHandleMissing]. */
    var dialogHandleOverride: String? = null
    var dialogHandleMissing: Boolean = false
    var dialogHasConfirm: Boolean = true
    var dialogHasCancel: Boolean = true

    /** When false, tapping the dialog's Block does nothing (the block never takes effect). */
    var confirmTakesEffect: Boolean = true

    /** What a long-press on the video opens: LONG_PRESS_MENU, or OTHER_MENU (a menu without Not interested). */
    var longPressOpens: Screen = Screen.LONG_PRESS_MENU

    /** When false, tapping Not interested leaves the menu open. */
    var notInterestedClosesMenu: Boolean = true

    /** Reasons TikTok asks for after Not interested, in on-screen order. Empty = no picker. */
    var reasons: List<String> = emptyList()

    /** Screens that never appear: navigation to them is silently dropped (the step times out). */
    var neverAppears: Set<Screen> = emptySet()

    /** Node tags whose click/long-press returns false and has no effect. */
    var failingTags: Set<String> = emptySet()

    /** Node tag whose click throws. */
    var throwingTag: String? = null

    // Product sheet (a DismissibleSheet over the profile).

    /** When the product sheet covers the profile after the avatar tap. */
    var productSheet: ProductSheet = ProductSheet.NONE

    /** The sheet exposes its outside-touch scrim; when false, only Back closes it. */
    var productSheetHasDismissNode: Boolean = true

    /** The scrim node is clickable (ACTION_CLICK). When false the executor must use Back instead. */
    var productSheetDismissNodeClickable: Boolean = true

    /** When false, the scrim tap and Back leave the sheet open (it refuses to close). */
    var productSheetCloses: Boolean = true

    /** The sheet comes back once, right after the first profile snapshot following its dismissal. */
    var productSheetReturns: Boolean = false

    /** The parser can read the profile under the sheet ([ScreenSnapshot.DismissibleSheet.underlyingProfile]). */
    var productSheetShowsProfile: Boolean = true

    /** The sheet covers the profile again when TikTok returns to it after the block. */
    var productSheetAfterBlock: Boolean = false

    // ── Observations ───────────────────────────────────────────────────────────────────────────────────────

    /** Every executor action, in order. */
    val actions = mutableListOf<String>()

    /** The executor actions taken while the product sheet was on screen, in order (also in [actions]). */
    val sheetActions = mutableListOf<String>()

    /** Simulated user taps on TikTok's own UI (stop-at-dialog style). */
    val userActions = mutableListOf<String>()

    /** Number of snapshots taken (including those inside [awaitSnapshot]). */
    var snapshotCount: Int = 0
        private set

    /** Reason tapped in the reason picker, if any. */
    var chosenReason: String? = null
        private set

    // ── Scheduling ─────────────────────────────────────────────────────────────────────────────────────────

    private class Event(val atMillis: Long, val seq: Long, val apply: () -> Unit)

    private val events = mutableListOf<Event>()
    private var seq = 0L

    /** Runs [event] when virtual time reaches [atMillis] (applied lazily on the next driver call). */
    fun at(atMillis: Long, event: FakeScreenDriver.() -> Unit) {
        events += Event(atMillis, seq++) { event() }
    }

    /** The user taps Block in TikTok's dialog at [atMillis]: the dialog closes, the profile updates a bit later. */
    fun userTapsDialogBlock(atMillis: Long) = at(atMillis) {
        if (screen == Screen.BLOCK_DIALOG) {
            userActions += "user:block"
            screen = if (productSheetAfterBlock) Screen.PRODUCT_SHEET else Screen.PROFILE
            after(latencyMillis) { blocked = true }
        } else {
            userActions += "user:block(ignored)"
        }
    }

    /** The user taps Cancel in TikTok's dialog at [atMillis]. */
    fun userTapsDialogCancel(atMillis: Long) = at(atMillis) {
        if (screen == Screen.BLOCK_DIALOG) {
            userActions += "user:cancel"
            screen = Screen.PROFILE
        } else {
            userActions += "user:cancel(ignored)"
        }
    }

    private fun after(delayMillis: Long, apply: () -> Unit) {
        events += Event(clock() + delayMillis, seq++, apply)
    }

    /** Navigates to [target] after the latency, unless it [neverAppears]. [also] runs with the change. */
    private fun goTo(target: Screen, also: () -> Unit = {}) = after(latencyMillis) {
        if (target !in neverAppears) {
            also()
            screen = target
        }
    }

    /** Like `goTo(PROFILE)`, but the product sheet may be on top ([withSheet]). */
    private fun goToProfile(withSheet: Boolean = false, also: () -> Unit = {}) = after(latencyMillis) {
        if (Screen.PROFILE !in neverAppears) {
            also()
            screen = if (withSheet) Screen.PRODUCT_SHEET else Screen.PROFILE
        }
    }

    /** Set when the product sheet should slide in right after the next profile snapshot. */
    private var sheetAfterNextProfileSnapshot = false

    private fun closeProductSheet() = goToProfile {
        if (productSheetReturns) {
            productSheetReturns = false
            sheetAfterNextProfileSnapshot = true
        }
    }

    /** Records an executor action, noting it separately if the product sheet was showing. */
    private fun record(action: String) {
        actions += action
        if (screen == Screen.PRODUCT_SHEET) sheetActions += action
    }

    private fun profileSnapshot(now: Long, scope: String): ScreenSnapshot.Profile {
        fun node(tag: String, clickable: Boolean = true) = FakeUiNode(tag, scope = scope, isClickable = clickable)
        return ScreenSnapshot.Profile(
            capturedAtMillis = now,
            displayName = profileDisplayName,
            handle = profileHandle,
            accountCategory = null,
            isShopCreator = false,
            isBlockedByMe = blocked,
            blockedMessageHandle = if (blocked) blockedMessageHandleOverride ?: profileHandle else null,
            shareNode = if (profileHasShare) node(PROFILE_SHARE, clickable = false) else null,
            backNode = node(PROFILE_BACK),
        )
    }

    private fun settle() {
        val now = clock()
        while (true) {
            val due = events.filter { it.atMillis <= now }.minWithOrNull(compareBy({ it.atMillis }, { it.seq })) ?: return
            events.remove(due)
            due.apply()
        }
    }

    // ── ScreenDriver ───────────────────────────────────────────────────────────────────────────────────────

    override suspend fun snapshot(): ScreenSnapshot {
        settle()
        snapshotCount++
        val now = clock()
        val scope = screen.name
        fun node(tag: String, clickable: Boolean = true, scrollable: Boolean = false) =
            FakeUiNode(tag, scope = scope, isClickable = clickable, isScrollable = scrollable)

        if (screen == Screen.PROFILE && sheetAfterNextProfileSnapshot) {
            // This snapshot still shows the bare profile; the sheet is on screen from the next one.
            sheetAfterNextProfileSnapshot = false
            return profileSnapshot(now, scope).also { screen = Screen.PRODUCT_SHEET }
        }

        return when (screen) {
            Screen.FEED -> ScreenSnapshot.FeedVideo(
                capturedAtMillis = now,
                displayName = feedDisplayName,
                probableHandle = null,
                caption = "caption $pageIndex",
                soundDescription = null,
                signals = Signals(hasShopAnchor = true, isLive = feedIsLive || feedIsLivePreview, isLivePreview = feedIsLivePreview),
                pageSignature = "page-$pageIndex",
                isForYouTab = true,
                avatarNode = if (feedHasAvatar) node(FEED_AVATAR) else null,
                displayNameNode = if (feedHasNameNode) node(FEED_NAME) else null,
                videoNode = if (feedHasVideoNode) node(FEED_VIDEO) else null,
                pagerNode = node(FEED_PAGER, scrollable = true),
            )
            Screen.PROFILE -> profileSnapshot(now, scope)
            Screen.PRODUCT_SHEET -> ScreenSnapshot.DismissibleSheet(
                capturedAtMillis = now,
                dismissNode = if (productSheetHasDismissNode) {
                    node(PRODUCT_SCRIM, clickable = productSheetDismissNodeClickable)
                } else {
                    null
                },
                isCommerce = true,
                // Its nodes (Share, Back) are covered by the sheet: tapping them is swallowed.
                underlyingProfile = if (productSheetShowsProfile) profileSnapshot(now, scope) else null,
            )
            Screen.SHARE_SHEET -> ScreenSnapshot.ShareSheet(
                capturedAtMillis = now,
                blockNode = if (sheetHasBlock) node(SHEET_BLOCK) else null,
                closeNode = if (sheetHasClose) node(SHEET_CLOSE) else null,
            )
            Screen.BLOCK_DIALOG -> {
                val dialogHandle = if (dialogHandleMissing) null else dialogHandleOverride ?: profileHandle
                ScreenSnapshot.BlockDialog(
                    capturedAtMillis = now,
                    titleText = "Block $profileDisplayName ($dialogHandle)?",
                    displayName = profileDisplayName,
                    handle = dialogHandle,
                    confirmNode = if (dialogHasConfirm) node(DIALOG_CONFIRM) else null,
                    cancelNode = if (dialogHasCancel) node(DIALOG_CANCEL) else null,
                )
            }
            Screen.LONG_PRESS_MENU -> ScreenSnapshot.LongPressMenu(
                capturedAtMillis = now,
                notInterestedNode = node(MENU_NOT_INTERESTED),
                optionLabels = listOf("Not interested", "Save video"),
            )
            Screen.OTHER_MENU -> ScreenSnapshot.LongPressMenu(
                capturedAtMillis = now,
                notInterestedNode = null,
                optionLabels = listOf("Save video"),
            )
            Screen.REASON_PICKER -> ScreenSnapshot.LongPressMenu(
                capturedAtMillis = now,
                notInterestedNode = null,
                optionLabels = reasons,
                reasonNodes = reasons.associateWith { node(REASON_PREFIX + it) },
            )
            Screen.UNKNOWN -> ScreenSnapshot.Unknown(capturedAtMillis = now, note = "fake unknown screen")
        }
    }

    override suspend fun click(node: UiNode): Boolean {
        settle()
        val fake = node as FakeUiNode
        val tag = fake.tag
        record("click:$tag")
        if (tag == throwingTag) throw IllegalStateException("fake driver failure")
        if (tag in failingTags) return false
        // While the product sheet shows, only its scrim reacts; a tap on anything it covers is swallowed and the
        // driver reports failure (the TAP_FAILED seen on TikTok 47.9.3).
        if (screen == Screen.PRODUCT_SHEET && tag != PRODUCT_SCRIM) return false
        if (fake.scope != screen.name) return true // Stale node: the tap lands on something else; ignored.
        when (tag) {
            FEED_AVATAR, FEED_NAME -> goToProfile(withSheet = productSheet == ProductSheet.WITH_PROFILE) {
                sheetAfterNextProfileSnapshot = productSheet == ProductSheet.AFTER_PROFILE_READ
            }
            PRODUCT_SCRIM -> if (productSheetCloses) closeProductSheet()
            PROFILE_SHARE -> goTo(Screen.SHARE_SHEET)
            PROFILE_BACK -> goTo(Screen.FEED)
            SHEET_BLOCK -> goTo(Screen.BLOCK_DIALOG)
            SHEET_CLOSE -> goTo(Screen.PROFILE)
            DIALOG_CONFIRM -> if (confirmTakesEffect) goToProfile(withSheet = productSheetAfterBlock) { blocked = true }
            DIALOG_CANCEL -> goTo(Screen.PROFILE)
            MENU_NOT_INTERESTED -> when {
                !notInterestedClosesMenu -> Unit
                reasons.isEmpty() -> goTo(Screen.FEED) { pageIndex++ }
                else -> goTo(Screen.REASON_PICKER)
            }
            else -> if (tag.startsWith(REASON_PREFIX)) {
                goTo(Screen.FEED) {
                    chosenReason = tag.removePrefix(REASON_PREFIX)
                    pageIndex++
                }
            }
        }
        return true
    }

    override suspend fun tap(x: Int, y: Int): Boolean {
        settle()
        record("tap:$x,$y")
        return true
    }

    override suspend fun longPress(node: UiNode): Boolean {
        settle()
        val fake = node as FakeUiNode
        record("longPress:${fake.tag}")
        if (fake.tag in failingTags) return false
        if (fake.scope == screen.name && fake.tag == FEED_VIDEO) goTo(longPressOpens)
        return true
    }

    override suspend fun scrollForward(node: UiNode): Boolean {
        settle()
        record("scrollForward:${(node as FakeUiNode).tag}")
        if (screen == Screen.FEED) goTo(Screen.FEED) { pageIndex++ }
        return true
    }

    override suspend fun back(): Boolean {
        settle()
        record("back")
        when (screen) {
            Screen.FEED -> goTo(Screen.UNKNOWN) // Left the feed: always a bug in these tests.
            Screen.PROFILE -> goTo(Screen.FEED)
            Screen.SHARE_SHEET, Screen.BLOCK_DIALOG -> goTo(Screen.PROFILE)
            Screen.LONG_PRESS_MENU, Screen.OTHER_MENU -> goTo(Screen.FEED)
            Screen.REASON_PICKER -> goTo(Screen.FEED) { pageIndex++ }
            Screen.PRODUCT_SHEET -> if (productSheetCloses) closeProductSheet()
            Screen.UNKNOWN -> Unit
        }
        return true
    }

    override suspend fun awaitSnapshot(
        timeoutMillis: Long,
        pollMillis: Long,
        predicate: (ScreenSnapshot) -> Boolean,
    ): ScreenSnapshot? {
        val deadline = clock() + timeoutMillis
        while (true) {
            val snapshot = snapshot()
            if (predicate(snapshot)) return snapshot
            val remaining = deadline - clock()
            if (remaining <= 0) return null
            delay(minOf(pollMillis, remaining))
        }
    }

    companion object {
        const val DEFAULT_NAME = "Creator One"
        const val DEFAULT_HANDLE = "creator.one"

        const val FEED_AVATAR = "feed.avatar"
        const val FEED_NAME = "feed.name"
        const val FEED_VIDEO = "feed.video"
        const val FEED_PAGER = "feed.pager"
        const val PROFILE_SHARE = "profile.share"
        const val PROFILE_BACK = "profile.back"
        const val SHEET_BLOCK = "sheet.block"
        const val SHEET_CLOSE = "sheet.close"
        const val DIALOG_CONFIRM = "dialog.confirm"
        const val DIALOG_CANCEL = "dialog.cancel"
        const val MENU_NOT_INTERESTED = "menu.notInterested"

        /** The product sheet's outside-touch scrim (the only sheet node an executor may click). */
        const val PRODUCT_SCRIM = "product.scrim"
        const val REASON_PREFIX = "reason:"
    }
}
