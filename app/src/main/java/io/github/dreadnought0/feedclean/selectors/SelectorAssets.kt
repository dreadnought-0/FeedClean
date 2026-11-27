package io.github.dreadnought0.feedclean.selectors

import android.content.Context

/**
 * Loads the bundled `assets/selectors/tiktok.json`.
 *
 * Throws [SelectorConfigException] only if the bundled file is broken (a build problem, covered by unit tests).
 * Call off the main thread; it reads and parses ~10 KB.
 */
@Throws(SelectorConfigException::class)
fun loadBundledSelectors(context: Context): SelectorConfig =
    context.assets.open(SelectorConfigLoader.ASSET_PATH).use { SelectorConfigLoader.load(it) }
