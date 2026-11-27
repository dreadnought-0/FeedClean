package io.github.dreadnought0.feedclean.detect

import io.github.dreadnought0.feedclean.selectors.SelectorConfig
import io.github.dreadnought0.feedclean.selectors.SelectorKeys
import io.github.dreadnought0.feedclean.selectors.SelectorMatch
import io.github.dreadnought0.feedclean.selectors.SelectorMatcher
import java.util.IdentityHashMap

/**
 * Turns the current TikTok window forest into a [ScreenSnapshot]. Pure, stateless, thread-safe.
 *
 * Classification priority (topmost overlay first): BlockDialog > LongPressMenu > ShareSheet > DismissibleSheet >
 * Profile > FeedVideo > Unknown. A bottom sheet or dialog that is none of the known kinds yields
 * [ScreenSnapshot.Unknown], or [ScreenSnapshot.DismissibleSheet] when it is a modal "Bottom sheet" in the topmost
 * window with a clickable outside-touch scrim (see [parseDismissibleSheet]). Either way automation never acts on
 * the feed while TikTok shows something on top of it: neither is a [ScreenSnapshot.FeedVideo].
 *
 * Every [ScreenSnapshot.FeedVideo] carries a [FeedContext] (For You, another Home tab, search results, other),
 * decided from the screen's chrome only (nodes outside the feed pager), never from what a video shows.
 *
 * Only shown nodes (`isVisibleToUser` and non-empty bounds) of windows whose package is the config's TikTok
 * package are considered; FeedClean's own overlay and system windows are ignored. For the feed, only nodes inside
 * the visible pager page count, so the pre-loaded next/previous video never leaks signals.
 *
 * A pager page that is an in-feed LIVE preview (`feed.liveVideo`: the page's `long_press_layout` says "LIVE", or
 * `feed.liveTips` "Tap to watch LIVE") is also a [ScreenSnapshot.FeedVideo], with [Signals.isLivePreview] set and
 * NO avatar or display-name node: on a LIVE preview, tapping the streamer's name or the preview enters the LIVE
 * room, so executors are never handed a node that would do that.
 *
 * @param config selectors, e.g. from [io.github.dreadnought0.feedclean.selectors.loadBundledSelectors].
 * @param localeTag UI language of TikTok, e.g. "en" (falls back to "en").
 */
class ScreenParser(config: SelectorConfig, localeTag: String = SelectorConfig.DEFAULT_LOCALE) {
    private val targetPackage: String = config.packageName

    /** The matcher used by this parser (exposed for executors that need ad-hoc lookups). */
    val matcher: SelectorMatcher = SelectorMatcher(config.forLocale(localeTag))
    private val signalDetector = SignalDetector(matcher)

    /**
     * Parses one snapshot.
     *
     * @param roots root nodes of all interactive windows, topmost first.
     * @param nowMillis capture time (wall clock), stored in [ScreenSnapshot.capturedAtMillis].
     */
    fun parse(roots: List<UiNode>, nowMillis: Long): ScreenSnapshot {
        val targetRoots = roots.filter(::isTargetRoot)
        if (targetRoots.isEmpty()) return ScreenSnapshot.Unknown(nowMillis, note = "No TikTok window")
        val index = NodeIndex.build(targetRoots)
        val ctx = Ctx(index, targetRoots, index.nodes.filter { it.isShown }, nowMillis)

        parseBlockDialog(ctx)?.let { return it }
        parseLongPressMenu(ctx)?.let { return it }
        parseShareSheet(ctx)?.let { return it }
        parseDismissibleSheet(ctx)?.let { return it }
        unrecognizedOverlay(ctx)?.let { return it }
        val feed = locateFeed(ctx)
        parseProfile(ctx, feed)?.let { return it }
        if (feed != null) return buildFeedVideo(ctx, feed)
        return ScreenSnapshot.Unknown(nowMillis, note = "No recognized TikTok screen")
    }

    /** True if [label] (a reason option or menu entry) is about shopping/products (`longpress.reasonShop`). */
    fun isShoppingReason(label: String?): Boolean = matcher.matchesText(SelectorKeys.LONGPRESS_REASON_SHOP, label)

    /** True if [text] (a toast or on-screen message) confirms a "Not interested" tap (`verify.notInterested`). */
    fun isNotInterestedConfirmation(text: String?): Boolean =
        matcher.matchesText(SelectorKeys.VERIFY_NOT_INTERESTED, text)

    /**
     * True if the long-press menu offers an ads-only option such as "About this ad" (`longpress.adOption`).
     * Corroborating paid-ad evidence only; the feed's "Ad" label stays authoritative.
     */
    fun isAdMenu(menu: ScreenSnapshot.LongPressMenu): Boolean =
        menu.optionLabels.any { matcher.matchesText(SelectorKeys.LONGPRESS_AD_OPTION, it) }

    // ── Overlays ────────────────────────────────────────────────────────────

    private fun parseBlockDialog(ctx: Ctx): ScreenSnapshot.BlockDialog? {
        val scopes = containerScopes(ctx, SelectorKeys.DIALOG_CONTAINER)
            .ifEmpty { listOfNotNull(windowScopeOf(ctx, SelectorKeys.DIALOG_TITLE)) }
        for ((container, scope) in scopes) {
            val title = matcher.first(SelectorKeys.DIALOG_TITLE, scope)
            val confirm = matcher.first(SelectorKeys.DIALOG_CONFIRM, scope)
            val cancel = matcher.first(SelectorKeys.DIALOG_CANCEL, scope)
            if (title == null && (confirm == null || cancel == null)) continue
            val name = matcher.first(SelectorKeys.DIALOG_DISPLAY_NAME, scope)
            val m = linkedMapOf<String, String>()
            m.record(SelectorKeys.DIALOG_CONTAINER, container)
            m.record(SelectorKeys.DIALOG_TITLE, title)
            m.record(SelectorKeys.DIALOG_DISPLAY_NAME, name)
            m.record(SelectorKeys.DIALOG_CONFIRM, confirm)
            m.record(SelectorKeys.DIALOG_CANCEL, cancel)
            return ScreenSnapshot.BlockDialog(
                capturedAtMillis = ctx.nowMillis,
                matchedStrategies = m,
                titleText = NameNormalizer.clean(title?.node?.text),
                displayName = name?.value,
                handle = NameNormalizer.normalizeHandle(title?.value),
                confirmNode = confirm?.node,
                cancelNode = cancel?.node,
            )
        }
        return null
    }

    private fun parseLongPressMenu(ctx: Ctx): ScreenSnapshot.LongPressMenu? {
        val scopes = containerScopes(ctx, SelectorKeys.LONGPRESS_CONTAINER)
            .ifEmpty { listOfNotNull(windowScopeOf(ctx, SelectorKeys.LONGPRESS_NOT_INTERESTED)) }
        for ((container, scope) in scopes) {
            val notInterested = matcher.first(SelectorKeys.LONGPRESS_NOT_INTERESTED, scope) ?: continue
            val m = linkedMapOf<String, String>()
            m.record(SelectorKeys.LONGPRESS_CONTAINER, container)
            m.record(SelectorKeys.LONGPRESS_NOT_INTERESTED, notInterested)
            val options = optionRow(ctx, notInterested.node, container?.node)
            val adOption = matcher.first(SelectorKeys.LONGPRESS_AD_OPTION, options)
            m.record(SelectorKeys.LONGPRESS_AD_OPTION, adOption)
            return ScreenSnapshot.LongPressMenu(
                capturedAtMillis = ctx.nowMillis,
                matchedStrategies = m,
                notInterestedNode = notInterested.node,
                optionLabels = options.mapNotNull { labelOf(ctx, it) }.distinct(),
                isAdMenu = adOption != null,
            )
        }
        // Reason picker shown after tapping "Not interested" (selectors unverified; see tiktok.json).
        val title = matcher.first(SelectorKeys.LONGPRESS_REASON_TITLE, ctx.shown) ?: return null
        val scope = windowScopeOf(ctx, SelectorKeys.LONGPRESS_REASON_TITLE)?.nodes ?: return null
        val reasons = linkedMapOf<String, UiNode>()
        val reasonMatches = matcher.all(SelectorKeys.LONGPRESS_REASON_OPTION, scope)
        for (r in reasonMatches) {
            val label = r.value ?: labelOf(ctx, r.node) ?: continue
            reasons.putIfAbsent(label, r.node)
        }
        val m = linkedMapOf<String, String>()
        m.record(SelectorKeys.LONGPRESS_REASON_TITLE, title)
        m.record(SelectorKeys.LONGPRESS_REASON_OPTION, reasonMatches.firstOrNull())
        return ScreenSnapshot.LongPressMenu(
            capturedAtMillis = ctx.nowMillis,
            matchedStrategies = m,
            notInterestedNode = null,
            optionLabels = reasons.keys.toList(),
            reasonNodes = reasons,
        )
    }

    private fun parseShareSheet(ctx: Ctx): ScreenSnapshot.ShareSheet? {
        for ((container, scope) in containerScopes(ctx, SelectorKeys.SHEET_CONTAINER)) {
            val block = matcher.first(SelectorKeys.SHEET_BLOCK, scope)
            val title = matcher.first(SelectorKeys.SHEET_TITLE, scope)
            if (block == null && title == null) continue
            val close = matcher.first(SelectorKeys.SHEET_CLOSE, scope)
            val m = linkedMapOf<String, String>()
            m.record(SelectorKeys.SHEET_CONTAINER, container)
            m.record(SelectorKeys.SHEET_TITLE, title)
            m.record(SelectorKeys.SHEET_BLOCK, block)
            m.record(SelectorKeys.SHEET_CLOSE, close)
            return ScreenSnapshot.ShareSheet(ctx.nowMillis, m, blockNode = block?.node, closeNode = close?.node)
        }
        return null
    }

    /**
     * A modal "Bottom sheet" (`sheet.container`) that FeedClean doesn't act inside (as seen in TikTok 47.9.3: the
     * PRODUCT sheet that opens over a Shop advertiser's profile). Required, or the screen stays Unknown:
     *  - the sheet is in the TOPMOST TikTok window (something else on top of it = Unknown);
     *  - a clickable outside-touch scrim (`sheet.dismissScrim`, Material's `touch_outside`) is a direct child of one
     *    of the container's ancestors: that ancestor (the dialog's coordinator) is the sheet's scope, and the scrim
     *    is [ScreenSnapshot.DismissibleSheet.dismissNode], the ONLY node handed out;
     *  - the scope holds none of the known sheets' markers ("Not interested", "Block", "Send to"; those sheets
     *    are matched earlier, so this only guards against a half-rendered known sheet).
     *
     * [ScreenSnapshot.DismissibleSheet.isCommerce] = `sheet.commerce` anywhere in the scope (the product sheet's
     * "Add to cart" / "Buy now" bar sits next to the container, not inside it).
     *
     * [ScreenSnapshot.DismissibleSheet.underlyingProfile] is parsed from the OTHER windows only, so nothing in the
     * sheet (its lowercase "share", its store name) can become the profile's Share node, handle, or name. It is
     * read-only: its `shareNode` and `backNode` are always null, because the sheet covers them (a gesture tap would
     * land on the scrim). Dismiss the sheet and re-snapshot to get an actionable Profile. Over the feed (or any
     * non-profile screen) it is null, and the result is still NOT a FeedVideo.
     */
    private fun parseDismissibleSheet(ctx: Ctx): ScreenSnapshot.DismissibleSheet? {
        for (container in matcher.all(SelectorKeys.SHEET_CONTAINER, ctx.shown)) {
            val sheetRoot = ctx.index.rootIndexOf(container.node)
            if (sheetRoot != TOPMOST_ROOT) continue
            val windowNodes = ctx.shown.filter { ctx.index.rootIndexOf(it) == sheetRoot }
            val scrim = matcher.all(SelectorKeys.SHEET_DISMISS_SCRIM, windowNodes)
                .filter { s ->
                    val host = ctx.index.parentOf(s.node)
                    host != null && !ctx.index.isWithin(s.node, container.node) &&
                        ctx.index.isWithin(container.node, host)
                }
                .maxByOrNull { ctx.index.depthOf(it.node) }
                ?: continue
            val host = ctx.index.parentOf(scrim.node) ?: continue
            val sheetNodes = ctx.index.subtree(host).filter { it.isShown }
            if (KNOWN_SHEET_MARKERS.any { matcher.first(it, sheetNodes) != null }) continue
            val commerce = matcher.first(SelectorKeys.SHEET_COMMERCE, sheetNodes)

            val m = linkedMapOf<String, String>()
            m.record(SelectorKeys.SHEET_CONTAINER, container)
            m.record(SelectorKeys.SHEET_DISMISS_SCRIM, scrim)
            m.record(SelectorKeys.SHEET_COMMERCE, commerce)
            return ScreenSnapshot.DismissibleSheet(
                capturedAtMillis = ctx.nowMillis,
                matchedStrategies = m,
                dismissNode = scrim.node,
                isCommerce = commerce != null,
                underlyingProfile = underlyingProfile(ctx, sheetRoot),
            )
        }
        return null
    }

    /** The Profile shown in the windows below the sheet's window [sheetRoot], read-only (no nodes), or null. */
    private fun underlyingProfile(ctx: Ctx, sheetRoot: Int): ScreenSnapshot.Profile? {
        val below = Ctx(ctx.index, ctx.roots, ctx.shown.filter { ctx.index.rootIndexOf(it) != sheetRoot }, ctx.nowMillis)
        if (below.shown.isEmpty()) return null
        return parseProfile(below, locateFeed(below))?.copy(shareNode = null, backNode = null)
    }

    /** A dialog or bottom sheet we don't recognize (comments, other popups): stop here, report Unknown. */
    private fun unrecognizedOverlay(ctx: Ctx): ScreenSnapshot.Unknown? {
        for (key in listOf(SelectorKeys.DIALOG_CONTAINER, SelectorKeys.SHEET_CONTAINER, SelectorKeys.LONGPRESS_CONTAINER)) {
            val match = matcher.first(key, ctx.shown) ?: continue
            return ScreenSnapshot.Unknown(
                ctx.nowMillis,
                mapOf(key to match.description),
                note = "Unrecognized overlay ($key)",
            )
        }
        return null
    }

    // ── Profile ─────────────────────────────────────────────────────────────

    private fun parseProfile(ctx: Ctx, feed: FeedPage?): ScreenSnapshot.Profile? {
        val candidates = if (feed == null) {
            ctx.shown
        } else {
            val inFeed = IdentityHashMap<UiNode, Unit>().apply { feed.pageNodes.forEach { put(it, Unit) } }
            ctx.shown.filter { !inFeed.containsKey(it) }
        }
        val handle = matcher.first(SelectorKeys.PROFILE_HANDLE, candidates) ?: return null
        val root = ctx.index.rootIndexOf(handle.node)
        val scope = candidates.filter { ctx.index.rootIndexOf(it) == root }

        val name = matcher.first(SelectorKeys.PROFILE_DISPLAY_NAME, scope)
        val category = matcher.first(SelectorKeys.PROFILE_CATEGORY, scope)
        val shopCreator = matcher.first(SelectorKeys.PROFILE_SHOP_CREATOR, scope)
        val unblock = matcher.first(SelectorKeys.VERIFY_UNBLOCK, scope)
        val blockedMsg = matcher.first(SelectorKeys.VERIFY_BLOCKED_MSG, scope)
        val share = matcher.first(SelectorKeys.PROFILE_SHARE, scope)
        val back = matcher.first(SelectorKeys.PROFILE_BACK, scope)

        val m = linkedMapOf<String, String>()
        m.record(SelectorKeys.PROFILE_HANDLE, handle)
        m.record(SelectorKeys.PROFILE_DISPLAY_NAME, name)
        m.record(SelectorKeys.PROFILE_CATEGORY, category)
        m.record(SelectorKeys.PROFILE_SHOP_CREATOR, shopCreator)
        m.record(SelectorKeys.VERIFY_UNBLOCK, unblock)
        m.record(SelectorKeys.VERIFY_BLOCKED_MSG, blockedMsg)
        m.record(SelectorKeys.PROFILE_SHARE, share)
        m.record(SelectorKeys.PROFILE_BACK, back)
        return ScreenSnapshot.Profile(
            capturedAtMillis = ctx.nowMillis,
            matchedStrategies = m,
            displayName = name?.value,
            handle = NameNormalizer.normalizeHandle(handle.value),
            accountCategory = category?.value,
            isShopCreator = shopCreator != null ||
                matcher.isLabel(SelectorKeys.PROFILE_SHOP_CATEGORY_LABELS, category?.value),
            isBlockedByMe = unblock != null || blockedMsg != null,
            blockedMessageHandle = NameNormalizer.normalizeHandle(blockedMsg?.value),
            shareNode = share?.node,
            backNode = back?.node,
        )
    }

    // ── Feed ────────────────────────────────────────────────────────────────

    private class FeedPage(
        val pager: SelectorMatch?,
        val pageBounds: Bounds,
        /** Shown nodes inside the visible page only. */
        val pageNodes: List<UiNode>,
        /** `feed.video`, or `feed.liveVideo` for a LIVE preview (null if only `feed.liveTips` identified it). */
        val video: SelectorMatch?,
        /** The page is an in-feed LIVE preview, not a video. */
        val isLivePreview: Boolean = false,
        /** `feed.liveTips` on a LIVE preview page. */
        val liveTips: SelectorMatch? = null,
    )

    private fun locateFeed(ctx: Ctx): FeedPage? {
        val pagers = matcher.all(SelectorKeys.FEED_PAGER, ctx.shown)
            .sortedByDescending { ctx.index.depthOf(it.node) } // innermost first
        for (pager in pagers) {
            val pagerBounds = pager.node.bounds
            val page = pager.node.children
                .filter { it.isShown && it.bounds.intersects(pagerBounds) }
                .maxByOrNull { it.bounds.intersect(pagerBounds).area }
                ?: continue
            val pageBounds = page.bounds.intersect(pagerBounds)
            val pageNodes = ctx.index.subtree(page).filter { it.isShown && it.bounds.intersects(pageBounds) }
            val video = matcher.first(SelectorKeys.FEED_VIDEO, pageNodes)
            if (video == null) {
                // A LIVE preview is checked before the display-name fallbacks, so it is never mistaken for a video.
                val liveVideo = matcher.first(SelectorKeys.FEED_LIVE_VIDEO, pageNodes)
                val liveTips = matcher.first(SelectorKeys.FEED_LIVE_TIPS, pageNodes)
                if (liveVideo != null || liveTips != null) {
                    return FeedPage(pager, pageBounds, pageNodes, liveVideo, isLivePreview = true, liveTips = liveTips)
                }
            }
            if (video != null || matcher.first(SelectorKeys.FEED_DISPLAY_NAME, pageNodes) != null) {
                return FeedPage(pager, pageBounds, pageNodes, video)
            }
        }
        // No pager matched: fall back to the shown video node's bounds as the page. (Never for LIVE previews: they
        // were only seen inside the pager, and a stray "LIVE" node must not turn another screen into a feed page.)
        val video = matcher.first(SelectorKeys.FEED_VIDEO, ctx.shown) ?: return null
        val root = ctx.index.rootIndexOf(video.node)
        val pageBounds = video.node.bounds
        val pageNodes = ctx.shown.filter { ctx.index.rootIndexOf(it) == root && it.bounds.intersects(pageBounds) }
        return FeedPage(null, pageBounds, pageNodes, video)
    }

    /**
     * An in-feed LIVE preview as a [ScreenSnapshot.FeedVideo]: the streamer's
     * name from `feed.liveNickname` as [ScreenSnapshot.FeedVideo.displayName], no handle/caption/sound, and NO
     * avatar or display-name node, because tapping the name (or the preview) enters the LIVE room. The page
     * signature is per stream: chat, gift, and auction lines change constantly and never feed it.
     */
    private fun buildLivePreview(ctx: Ctx, feed: FeedPage): ScreenSnapshot.FeedVideo {
        val nodes = feed.pageNodes
        val nickname = matcher.first(SelectorKeys.FEED_LIVE_NICKNAME, nodes)
        val liveNow = matcher.first(SelectorKeys.FEED_LIVE_NOW, nodes)
        val context = classifyContext(ctx, feed)
        val report = signalDetector.detectLivePreview(nodes)

        val m = linkedMapOf<String, String>()
        m.record(SelectorKeys.FEED_PAGER, feed.pager)
        m.record(SelectorKeys.FEED_LIVE_VIDEO, feed.video)
        m.record(SelectorKeys.FEED_LIVE_TIPS, feed.liveTips)
        m.record(SelectorKeys.FEED_LIVE_NOW, liveNow)
        m.record(SelectorKeys.FEED_LIVE_NICKNAME, nickname)
        context.recordInto(m)
        m.putAll(report.matchedStrategies)

        return ScreenSnapshot.FeedVideo(
            capturedAtMillis = ctx.nowMillis,
            matchedStrategies = m,
            displayName = nickname?.value,
            probableHandle = null,
            caption = null,
            soundDescription = null,
            signals = report.signals,
            pageSignature = SignalDetector.livePageSignature(nickname?.value),
            isForYouTab = context.context == FeedContext.FOR_YOU,
            avatarNode = null,
            displayNameNode = null,
            videoNode = feed.video?.node,
            pagerNode = feed.pager?.node,
            pageBounds = feed.pageBounds,
            adConfidence = report.adConfidence,
            feedContext = context.context,
        )
    }

    private fun buildFeedVideo(ctx: Ctx, feed: FeedPage): ScreenSnapshot.FeedVideo {
        if (feed.isLivePreview) return buildLivePreview(ctx, feed)
        val nodes = feed.pageNodes
        val displayName = matcher.first(SelectorKeys.FEED_DISPLAY_NAME, nodes)
        val avatar = matcher.first(SelectorKeys.FEED_AVATAR, nodes)
        val caption = matcher.first(SelectorKeys.FEED_CAPTION, nodes)
        val sound = matcher.first(SelectorKeys.FEED_SOUND, nodes)
        val probableHandle = matcher.first(
            SelectorKeys.FEED_PROBABLE_HANDLE,
            nodes,
            resolved = mapOf(SelectorKeys.FEED_DISPLAY_NAME to displayName?.value),
        )
        val context = classifyContext(ctx, feed)
        val report = signalDetector.detect(nodes)

        val m = linkedMapOf<String, String>()
        m.record(SelectorKeys.FEED_PAGER, feed.pager)
        m.record(SelectorKeys.FEED_VIDEO, feed.video)
        m.record(SelectorKeys.FEED_DISPLAY_NAME, displayName)
        m.record(SelectorKeys.FEED_AVATAR, avatar)
        m.record(SelectorKeys.FEED_CAPTION, caption)
        m.record(SelectorKeys.FEED_SOUND, sound)
        m.record(SelectorKeys.FEED_PROBABLE_HANDLE, probableHandle)
        context.recordInto(m)
        m.putAll(report.matchedStrategies)

        return ScreenSnapshot.FeedVideo(
            capturedAtMillis = ctx.nowMillis,
            matchedStrategies = m,
            displayName = displayName?.value,
            probableHandle = NameNormalizer.normalizeHandle(probableHandle?.value),
            caption = caption?.value,
            soundDescription = sound?.value,
            signals = report.signals,
            pageSignature = SignalDetector.pageSignature(displayName?.value, caption?.value, sound?.value),
            isForYouTab = context.context == FeedContext.FOR_YOU,
            avatarNode = avatar?.node,
            displayNameNode = displayName?.node,
            videoNode = feed.video?.node,
            pagerNode = feed.pager?.node,
            pageBounds = feed.pageBounds,
            adConfidence = report.adConfidence,
            feedContext = context.context,
        )
    }

    // ── Feed context ────────────────────────────────────────────────────────

    private inner class ContextMatch(
        val context: FeedContext,
        val forYou: SelectorMatch?,
        val homeTabs: SelectorMatch?,
        val searchBar: SelectorMatch?,
    ) {
        fun recordInto(m: MutableMap<String, String>) {
            m.record(SelectorKeys.FEED_FOR_YOU_TAB, forYou)
            m.record(SelectorKeys.FEED_HOME_TOP_TABS, homeTabs)
            m.record(SelectorKeys.FEED_SEARCH_PLAYER_BAR, searchBar)
        }
    }

    /**
     * Which player [feed] is in (TikTok 47.9.3), decided from the screen's CHROME only: shown nodes outside the
     * feed pager's subtree (without a pager: outside the video node's subtree). Nothing a video shows can change the
     * context, e.g. the "Search · <suggestion>" bar that For You pages carry inside the page.
     *
     * 1. `feed.forYouTab` (the selected "For You" tab) → [FeedContext.FOR_YOU];
     * 2. else `feed.homeTopTabs` (the Home tab row: For You / Following, top of the window) → HOME_OTHER_TAB. Only
     *    WHICH tab is selected matters, never tab order or the "Shop" tab (text in 47.9.3, desc-only in 47.7.3);
     * 3. else `feed.searchPlayerBar` (the search-results player's top search bar, top of the window) → SEARCH;
     * 4. else OTHER (a profile's videos, hashtag/sound pages, …).
     *
     * The `topFraction` strategies are measured against the bounds of the window holding the feed.
     */
    private fun classifyContext(ctx: Ctx, feed: FeedPage): ContextMatch {
        val inner = feed.pager?.node ?: feed.video?.node
        val excluded = IdentityHashMap<UiNode, Unit>()
        inner?.let { ctx.index.subtree(it).forEach { n -> excluded[n] = Unit } }
        if (inner == null) feed.pageNodes.forEach { excluded[it] = Unit }
        val chrome = ctx.shown.filter { !excluded.containsKey(it) }
        val frame = inner?.let { ctx.rootBoundsOf(it) }

        val forYou = matcher.first(SelectorKeys.FEED_FOR_YOU_TAB, chrome, frame = frame)
        val homeTabs = matcher.first(SelectorKeys.FEED_HOME_TOP_TABS, chrome, frame = frame)
        val searchBar = if (forYou == null && homeTabs == null) {
            matcher.first(SelectorKeys.FEED_SEARCH_PLAYER_BAR, chrome, frame = frame)
        } else {
            null
        }
        val context = when {
            forYou != null -> FeedContext.FOR_YOU
            homeTabs != null -> FeedContext.HOME_OTHER_TAB
            searchBar != null -> FeedContext.SEARCH
            else -> FeedContext.OTHER
        }
        return ContextMatch(context, forYou, homeTabs, searchBar)
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private class Ctx(
        val index: NodeIndex,
        /** Target window roots, topmost first (indices match [NodeIndex.rootIndexOf]). */
        val roots: List<UiNode>,
        val shown: List<UiNode>,
        val nowMillis: Long,
    ) {
        /** Bounds of the window root holding [node], or null. */
        fun rootBoundsOf(node: UiNode): Bounds? = roots.getOrNull(index.rootIndexOf(node))?.bounds
    }

    /** Nodes to search, and the container match they came from (null = whole window, no container known). */
    private data class Scope(val container: SelectorMatch?, val nodes: List<UiNode>)

    /** For each container match of [key]: the container and its shown subtree. */
    private fun containerScopes(ctx: Ctx, key: String): List<Scope> =
        matcher.all(key, ctx.shown).map { c -> Scope(c, ctx.index.subtree(c.node).filter { it.isShown }) }

    /** If [key] matches anywhere, the shown nodes of the window that contains the match. */
    private fun windowScopeOf(ctx: Ctx, key: String): Scope? {
        val hit = matcher.first(key, ctx.shown) ?: return null
        val root = ctx.index.rootIndexOf(hit.node)
        return Scope(null, ctx.shown.filter { ctx.index.rootIndexOf(it) == root })
    }

    /**
     * The menu's option row: the clickable siblings of the clickable node holding [hit]. Contacts and other rows
     * live in different containers, so they never leak into option labels.
     */
    private fun optionRow(ctx: Ctx, hit: UiNode, container: UiNode?): List<UiNode> {
        var option: UiNode = hit
        while (!option.isClickable) {
            val parent = ctx.index.parentOf(option) ?: break
            if (container != null && !ctx.index.isWithin(parent, container)) break
            option = parent
        }
        val parent = ctx.index.parentOf(option) ?: return listOf(option)
        if (container != null && !ctx.index.isWithin(parent, container)) return listOf(option)
        return parent.children.filter { it.isShown && it.isClickable }.ifEmpty { listOf(option) }
    }

    /** Visible label of an option: its content-desc, else its text, else the first labelled descendant. */
    private fun labelOf(ctx: Ctx, node: UiNode): String? =
        ctx.index.subtree(node).asSequence()
            .filter { it.isShown }
            .mapNotNull { NameNormalizer.clean(it.contentDescription) ?: NameNormalizer.clean(it.text) }
            .firstOrNull()

    private fun isTargetRoot(root: UiNode): Boolean {
        if (targetPackage.isBlank()) return true
        val pkg = root.packageName ?: root.walk().take(PACKAGE_PROBE_LIMIT).firstNotNullOfOrNull { it.packageName }
        return pkg == targetPackage
    }

    private fun MutableMap<String, String>.record(key: String, match: SelectorMatch?) {
        if (match != null) put(key, match.description)
    }

    private companion object {
        const val PACKAGE_PROBE_LIMIT = 50

        /** Root index of the topmost TikTok window. */
        const val TOPMOST_ROOT = 0

        /** Markers of the sheets parsed before [parseDismissibleSheet]; a sheet showing one is never "dismissible". */
        val KNOWN_SHEET_MARKERS = listOf(
            SelectorKeys.LONGPRESS_NOT_INTERESTED, SelectorKeys.SHEET_BLOCK, SelectorKeys.SHEET_TITLE,
        )
    }
}
