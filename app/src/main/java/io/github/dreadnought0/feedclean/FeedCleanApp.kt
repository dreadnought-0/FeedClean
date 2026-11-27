package io.github.dreadnought0.feedclean

import android.app.Application

class FeedCleanApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Convenience accessor: `context.appContainer`. */
val android.content.Context.appContainer: AppContainer
    get() = (applicationContext as FeedCleanApp).container
