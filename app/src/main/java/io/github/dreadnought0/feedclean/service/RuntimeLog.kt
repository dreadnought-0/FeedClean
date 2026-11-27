package io.github.dreadnought0.feedclean.service

import android.util.Log

/**
 * Diagnostic log sink for the live runtime (service, overlay, curation).
 *
 * Messages carry selector keys, step names, outcomes, view-id names, and timings only: never display names,
 * handles, captions, contacts, or any other screen text.
 */
fun interface RuntimeLog {
    fun log(message: String)

    companion object {
        /** Discards everything (tests). */
        val NONE: RuntimeLog = RuntimeLog { }
    }
}

/** Logcat sink. Filter with `adb logcat -s FeedClean`. */
object FeedCleanLog : RuntimeLog {
    /** Logcat tag for everything the runtime logs. */
    const val TAG = "FeedClean"

    override fun log(message: String) {
        Log.i(TAG, message)
    }

    /** Logs a problem; [error] contributes its class and message only. */
    fun warn(message: String, error: Throwable? = null) {
        Log.w(TAG, if (error == null) message else "$message (${error.javaClass.simpleName}: ${error.message})")
    }
}
