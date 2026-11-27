package io.github.dreadnought0.feedclean.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent
import io.github.dreadnought0.feedclean.appContainer
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * FeedClean's accessibility service, scoped to TikTok (accessibility_service_config.xml). Thin: it owns the
 * service-scoped coroutine scope and forwards lifecycle and events to [ServiceRuntime].
 *
 *  - onServiceConnected → service connected, new scope (SupervisorJob + Main.immediate), runtime started.
 *  - onAccessibilityEvent → the runtime's debounced parse.
 *  - onInterrupt → stop any screen action in progress (cover dropped); observation continues.
 *  - onUnbind / onDestroy → overlays removed, scope cancelled, session closed, service disconnected.
 */
class FeedCleanAccessibilityService : AccessibilityService() {
    private var scope: CoroutineScope? = null
    private var runtime: ServiceRuntime? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        teardown("reconnect")
        ScreenStateHolder.setServiceConnected(true)
        val handler = CoroutineExceptionHandler { _, e -> FeedCleanLog.warn("runtime coroutine failed", e) }
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + handler)
        scope = newScope
        runtime = ServiceRuntime(this, appContainer, newScope).also { it.start() }
        FeedCleanLog.log("service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null) runtime?.onEvent()
    }

    override fun onInterrupt() {
        runtime?.interrupt()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        runtime?.onScreenChanged()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        teardown("unbind")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown("destroy")
        super.onDestroy()
    }

    private fun teardown(why: String) {
        val r = runtime ?: return
        runtime = null
        r.shutdown()
        scope?.cancel()
        scope = null
        ScreenStateHolder.setServiceConnected(false)
        FeedCleanLog.log("service stopped ($why)")
    }
}
