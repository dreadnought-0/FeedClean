package io.github.dreadnought0.feedclean.service

import android.accessibilityservice.AccessibilityService
import android.os.PowerManager
import android.os.SystemClock
import io.github.dreadnought0.feedclean.AppContainer
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.curate.AutoSkipContext
import io.github.dreadnought0.feedclean.curate.AutoSkipController
import io.github.dreadnought0.feedclean.curate.AutoSkipOutcome
import io.github.dreadnought0.feedclean.curate.LoopGuard
import io.github.dreadnought0.feedclean.curate.NotInterestedFlow
import io.github.dreadnought0.feedclean.curate.NotInterestedRunner
import io.github.dreadnought0.feedclean.curate.SkipGateResult
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot
import io.github.dreadnought0.feedclean.detect.TreeXmlWriter
import io.github.dreadnought0.feedclean.overlay.ActionMessage
import io.github.dreadnought0.feedclean.overlay.BubbleLookMapper
import io.github.dreadnought0.feedclean.overlay.CardController
import io.github.dreadnought0.feedclean.overlay.OverlayManager
import io.github.dreadnought0.feedclean.overlay.OverlayTexts
import io.github.dreadnought0.feedclean.overlay.PxPoint
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException

/**
 * The live runtime behind [FeedCleanAccessibilityService]: wires the screen driver, the curation pipeline,
 * auto-skip, the overlays, and the card actions. Thin Android glue; the decisions live in pure
 * classes ([FeedCurator], [AutoSkipGate][io.github.dreadnought0.feedclean.curate.AutoSkipGate],
 * [AutoSkipController], [ForegroundTracker], [BubbleLookMapper], …).
 *
 * Coroutines, all in the service [scope] (SupervisorJob + Main.immediate; cancelled when the service stops):
 *  - **snapshot loop**: accessibility events → [EventDebouncer] (~250 ms quiet, ≤ 600 ms) → one parse on the
 *    driver's IPC thread → [ScreenStateHolder.publishSnapshot] → [FeedCurator] (DB writes, auto-skip inline).
 *  - **foreground loop**: waits for a TikTok event while TikTok is in the background; re-checks the window list
 *    every second while it is in front.
 *  - **settings loop**: kill-switch / disarm stop automation at once; bubble and session follow settings.
 *  - **one card action** at a time ([CardController]); only ever started by a card button.
 *  - **decision log** writer ([DecisionLogWriter], on the app scope + IO): one line per page decision / watch
 *    re-evaluation / card action result, enums only, while Settings → decision log is on.
 *
 * Screen actions can only come from: (1) [AutoSkipController] (one scroll per flagged page, behind every gate;
 * automated Not-interested only when the user armed it, rate-limited), and (2) the card's buttons, each with a
 * click-created [io.github.dreadnought0.feedclean.block.UserIntentToken].
 */
class ServiceRuntime(
    private val service: AccessibilityService,
    private val container: AppContainer,
    private val scope: CoroutineScope,
) : OverlayManager.Listener {

    private val log = FeedCleanLog
    private val settings: FeedCleanSettings get() = container.settingsRepository.settings.value
    private val windows = WindowSource(service)
    private val overlays = OverlayManager(service, scope, log, this)
    private val driver = A11yScreenDriver(
        service = service,
        windows = windows,
        parser = { container.selectorProvider.parser.value },
        targetPackage = ::targetPackage,
        gestureGuard = overlays,
        log = log,
    )
    private val loopGuard = LoopGuard()
    private val decisionLog = DecisionLogWriter(
        store = DecisionLogStore.inFilesDir(service.filesDir),
        scope = container.applicationScope,
        enabled = { settings.decisionLogEnabled },
        warn = { message, error -> log.warn(message, error) },
    )
    private val session = SessionTracker(container.sessionRepository)
    private val persistence = CardActionPersistence(
        container.accountRepository,
        container.sightingRepository,
        container.blockEventRepository,
    )
    private val actions = AssistedActions(
        driver = driver,
        rateLimiter = container.rateLimiter,
        settings = { settings },
        persistence = persistence,
        cover = overlays.cover,
        isShoppingReason = ::isShoppingReason,
        log = log,
        decisionLog = decisionLog,
    )
    private val automatedNotInterested = NotInterestedRunner { trigger, accountId, signature ->
        NotInterestedFlow(driver, container.rateLimiter, { settings }, isShoppingReason = ::isShoppingReason)
            .run(trigger, accountId, signature)
    }
    private val autoSkip = AutoSkipController(
        driver = driver,
        cover = overlays.cover,
        loopGuard = loopGuard,
        settings = { settings },
        notInterested = automatedNotInterested,
        log = log::log,
    )
    private val curator = FeedCurator(
        pipeline = FeedPagePipeline(container.accountRepository, container.sightingRepository),
        autoSkip = autoSkip,
        loopGuard = loopGuard,
        sightings = container.sightingRepository,
        session = session,
        settings = { settings },
        context = ::autoSkipContext,
        listener = CuratorEvents(),
        log = log,
        decisionLog = decisionLog,
    )
    private val card = CardController(
        context = service,
        scope = scope,
        overlays = overlays,
        actions = actions,
        persistence = persistence,
        accounts = container.accountRepository,
        rateLimiter = container.rateLimiter,
        settings = { settings },
        loopGuardPaused = { loopGuard.isPaused },
        currentPage = { currentPage },
        capture = ::captureScreen,
        highlightConfirm = ::highlightConfirmButton,
        onChanged = ::renderBubble,
        onResumeAutoSkip = ::resumeAutoSkip,
        log = log,
    )
    private val foreground = ForegroundTracker(::targetPackage)
    private val power: PowerManager? = service.getSystemService(PowerManager::class.java)
    private val events = Channel<Unit>(Channel.CONFLATED)
    private val foregroundPokes = Channel<Unit>(Channel.CONFLATED)

    private var currentPage: ProcessedPage? = null
    private var lastScreenType: String? = null
    private var lastSettings: FeedCleanSettings? = null
    private var stopped = false

    /** Starts the loops. Call once, from onServiceConnected. */
    fun start() {
        overlays.cardCallbacks = card
        scope.launch { session.recoverOnStart() }
        scope.launch { snapshotLoop() }
        scope.launch { foregroundLoop() }
        scope.launch { settingsLoop() }
        scope.launch { commandLoop() }
        foregroundPokes.trySend(Unit)
        events.trySend(Unit)
        log.log("runtime started")
    }

    /** One accessibility event from TikTok (the only package the service listens to). */
    fun onEvent() {
        if (stopped) return
        events.trySend(Unit)
        foregroundPokes.trySend(Unit)
    }

    /**
     * onInterrupt: stop whatever is acting on the screen (auto-skip, card action), drop the cover and close the
     * card. Observation and the bubble stay: any app may call `AccessibilityManager.interrupt()`, and a full
     * teardown here would leave a bound but dead service until the user toggles it.
     */
    fun interrupt() {
        stopAutomation("interrupt")
        card.close()
    }

    /** Fold/unfold or rotation. */
    fun onScreenChanged() {
        overlays.onScreenChanged()
    }

    /** Service unbinding/destroyed: remove every overlay, stop everything, close the session. Idempotent. */
    fun shutdown() {
        if (stopped) return
        stopped = true
        stopAutomation("shutdown")
        card.cancelAll()
        overlays.hideAll()
        events.close()
        foregroundPokes.close()
        ScreenStateHolder.setTikTokForeground(false)
        ScreenStateHolder.publishAutoSkip(AutoSkipStatus())
        // The service scope is about to be cancelled; close the session on the app scope.
        container.applicationScope.launch { session.close() }
        decisionLog.close() // queued lines are still written (the writer runs on the app scope)
        log.log("runtime stopped")
    }

    // ── Loops ──────────────────────────────────────────────────────────────────────────────────────────────

    private suspend fun snapshotLoop() {
        EventDebouncer().run(events, SystemClock::uptimeMillis) {
            try {
                val captured = driver.capture()
                ScreenStateHolder.publishSnapshot(captured.snapshot)
                val type = when (val s = captured.snapshot) {
                    is ScreenSnapshot.Unknown -> "Unknown(${s.note})" // notes are FeedClean's own text, never screen content
                    else -> s.javaClass.simpleName
                }
                if (type != lastScreenType) {
                    lastScreenType = type
                    log.log("screen $type nodes=${captured.nodeCount} ms=${captured.tookMillis}")
                }
                if (captured.truncated) log.log("snapshot truncated nodes=${captured.nodeCount} ms=${captured.tookMillis}")
                if (foreground.isForeground) curator.onSnapshot(captured.snapshot, captured.truncated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("pipeline error", e)
            }
        }
    }

    private suspend fun foregroundLoop() {
        while (!stopped) {
            if (foreground.isForeground) {
                delay(FOREGROUND_POLL_MILLIS)
                foregroundPokes.tryReceive()
            } else if (foregroundPokes.receiveCatching().isClosed) {
                return
            }
            val facts = withContext(Dispatchers.IO) { windows.foregroundFacts() }
            foreground.update(facts)?.let(::onForegroundChanged)
        }
    }

    private suspend fun settingsLoop() {
        container.settingsRepository.settings.collect { s ->
            val prev = lastSettings
            lastSettings = s
            if (s.killSwitchEngaged && prev?.killSwitchEngaged != true && prev != null) stopAutomation("kill-switch")
            if (!s.autoSkipAllowedBySettings && prev?.autoSkipAllowedBySettings == true) curator.cancelActiveSkip()
            if (prev != null && s.bubbleX < 0 && (prev.bubbleX != s.bubbleX || prev.bubbleY != s.bubbleY)) {
                overlays.hideBubble() // "Reset bubble position": re-show at the default spot.
            }
            renderBubble()
            if (prev == null || prev.sessionTrackingEnabled != s.sessionTrackingEnabled) {
                session.sync(s.sessionTrackingEnabled, foreground.isForeground)
            }
        }
    }

    /** Taps in the app (Home) that the runtime acts on. */
    private suspend fun commandLoop() {
        ScreenStateHolder.commands.collect { command ->
            when (command) {
                RuntimeCommand.RESUME_AUTO_SKIP -> if (loopGuard.isPaused) resumeAutoSkip()
            }
        }
    }

    private fun onForegroundChanged(inFront: Boolean) {
        log.log("foreground=$inFront")
        ScreenStateHolder.setTikTokForeground(inFront)
        if (inFront) {
            events.trySend(Unit)
        } else {
            stopAutomation("tiktok left foreground")
            card.cancelAll()
            curator.reset()
            currentPage = null
            overlays.hideAll()
        }
        renderBubble()
        scope.launch { session.sync(settings.sessionTrackingEnabled, inFront) }
    }

    /** Kill-switch & co.: cancel every running screen action at once and drop the cover. */
    private fun stopAutomation(why: String) {
        val skip = curator.cancelActiveSkip()
        val action = card.stopAction()
        overlays.cover.hide()
        overlays.hideHighlight()
        if (skip || action) log.log("automation stopped ($why) skip=$skip action=$action")
        publishAutoSkipStatus()
    }

    // ── Bubble ─────────────────────────────────────────────────────────────────────────────────────────────

    private fun renderBubble() {
        val s = settings
        if (stopped || !foreground.isForeground || !s.showBubble) {
            card.close()
            overlays.hideBubble()
            // Home reads the safety-stop state from here, so keep publishing it with the bubble hidden too.
            if (!stopped) publishAutoSkipStatus()
            return
        }
        overlays.setBubbleLook(BubbleLookMapper.map(currentPage, s, loopGuard.isPaused, busy = card.isActionRunning))
        overlays.showBubble(s.bubbleX, s.bubbleY)
        overlays.setStickyHint(
            if (loopGuard.isPaused) service.getString(R.string.overlay_hint_paused_loop, loopGuard.pausedAfter) else null,
        )
        publishAutoSkipStatus()
    }

    override fun onBubbleTap() {
        when {
            card.isActionRunning -> card.stopAction()
            loopGuard.isPaused -> resumeAutoSkip()
            else -> card.toggle()
        }
    }

    override fun onBubbleLongPress() {
        scope.launch {
            var on = false
            container.settingsRepository.update {
                on = !it.killSwitchEngaged
                it.copy(killSwitchEngaged = on)
            }
            // Stop at once; the settings flow will confirm it a moment later.
            if (on) stopAutomation("kill-switch (bubble)")
            log.log("kill-switch=$on (bubble long-press)")
            val message = ActionMessage(if (on) ActionMessage.Type.KILL_SWITCH_ON else ActionMessage.Type.KILL_SWITCH_OFF, ok = !on)
            overlays.showTransientHint(OverlayTexts.message(service, message))
        }
    }

    override fun onBubbleMoved(position: PxPoint) {
        scope.launch { container.settingsRepository.update { it.copy(bubbleX = position.x, bubbleY = position.y) } }
    }

    override fun onCardDismissedByOutsideTouch() {
        card.onClosedExternally()
    }

    /** Resuming auto-skip after the loop guard paused it: a user click (bubble tap or card button). */
    private fun resumeAutoSkip() {
        loopGuard.resume()
        log.log("autoskip resumed by user")
        if (foreground.isForeground && settings.showBubble) {
            overlays.showTransientHint(OverlayTexts.message(service, ActionMessage(ActionMessage.Type.AUTO_SKIP_RESUMED, ok = true)))
        }
        renderBubble()
    }

    // ── Curator events ─────────────────────────────────────────────────────────────────────────────────────

    private inner class CuratorEvents : FeedCurator.Listener {
        override fun onPage(page: ProcessedPage, gate: SkipGateResult) {
            currentPage = page
            renderBubble()
            if (gate is SkipGateResult.Go) publishAutoSkipStatus(skipping = true)
        }

        /** A late label on the same page: the bubble and the card follow the grown detection. */
        override fun onPageUpdated(page: ProcessedPage, gate: SkipGateResult) = onPage(page, gate)

        override fun onAutoSkip(page: ProcessedPage, outcome: AutoSkipOutcome) {
            val summary = when (outcome) {
                is AutoSkipOutcome.Skipped ->
                    "skipped scrolled=${outcome.scrolled} advanced=${outcome.advanced}" +
                        (outcome.notInterested?.let { " ni=${it.javaClass.simpleName}" } ?: "")
                is AutoSkipOutcome.Cancelled -> "cancelled ${outcome.why}"
                is AutoSkipOutcome.Refused -> "refused ${outcome.why}"
            }
            ScreenStateHolder.addEvent("${LocalTime.now().format(TIME)} auto-skip: $summary")
            publishAutoSkipStatus(lastOutcome = summary)
            // The next page may already be on screen: look at it right away.
            events.trySend(Unit)
            renderBubble()
        }
    }

    private fun publishAutoSkipStatus(skipping: Boolean = curator.isSkipping, lastOutcome: String? = null) {
        val prev = ScreenStateHolder.autoSkip.value
        ScreenStateHolder.publishAutoSkip(
            AutoSkipStatus(
                pausedByLoopGuard = loopGuard.isPaused,
                pausedAfter = loopGuard.pausedAfter,
                consecutiveSkips = loopGuard.consecutiveSkips,
                skipping = skipping,
                lastOutcome = lastOutcome ?: prev.lastOutcome,
                lastOutcomeAtMillis = if (lastOutcome != null) System.currentTimeMillis() else prev.lastOutcomeAtMillis,
            ),
        )
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────────────────────────

    private fun autoSkipContext() = AutoSkipContext(
        tiktokForeground = foreground.isForeground,
        screenInteractive = power?.isInteractive ?: true,
        actionInProgress = card.isActionRunning || container.rateLimiter.isActionInFlight,
        cardOpen = overlays.isCardShowing,
    )

    private fun targetPackage(): String? = container.selectorProvider.config.value?.packageName?.takeIf { it.isNotBlank() }

    private fun isShoppingReason(label: String): Boolean =
        container.selectorProvider.parser.value?.isShoppingReason(label)
            ?: NotInterestedFlow.DEFAULT_IS_SHOPPING_REASON(label)

    /** Stop-at-dialog block style: ring around TikTok's own Block button (non-touchable). */
    private suspend fun highlightConfirmButton() {
        val dialog = driver.snapshot() as? ScreenSnapshot.BlockDialog ?: return
        dialog.confirmNode?.bounds?.let(overlays::showHighlight)
    }

    /** "Capture screen": TikTok's windows → redacted XML in filesDir/captures. Read-only; taps nothing. */
    private suspend fun captureScreen(): ActionMessage {
        val parser = container.selectorProvider.parser.value
            ?: return ActionMessage(ActionMessage.Type.CAPTURE_FAILED, ok = false)
        val (roots, truncated) = driver.readTargetRoots()
        if (roots.isEmpty()) return ActionMessage(ActionMessage.Type.CAPTURE_FAILED, ok = false)
        val file = withContext(Dispatchers.IO) {
            val xml = TreeXmlWriter.toXml(roots, SelectorRedactor(parser.matcher.selectors))
            CaptureStore.inFilesDir(service.filesDir).write(xml, System.currentTimeMillis())
        }
        log.log("capture saved roots=${roots.size} truncated=$truncated bytes=${file.length()}")
        return ActionMessage(ActionMessage.Type.CAPTURE_SAVED, file.name, ok = true)
    }

    private companion object {
        const val FOREGROUND_POLL_MILLIS = 1_000L
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
