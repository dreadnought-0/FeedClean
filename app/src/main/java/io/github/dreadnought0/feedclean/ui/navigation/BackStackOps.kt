package io.github.dreadnought0.feedclean.ui.navigation

import androidx.navigation3.runtime.NavKey

// Back-stack rules, kept as plain list operations so they are JVM-testable (NavBackStack is a MutableList).
//
// Shape: [Home] or [Home, <other tab>], followed by any pushed destinations. Home is always the root, so Back
// from any tab returns to Home before leaving the app.

/** The top-level tab that owns the screen currently shown (the last [TopLevelKey] in the stack). */
fun List<NavKey>.currentTopLevel(): TopLevelKey = lastOrNull { it is TopLevelKey } as? TopLevelKey ?: HomeKey

/**
 * Switches to the tab [key]. Selecting the current tab again pops back to that tab's root. Mutates the list
 * minimally (truncates to the common prefix, then appends), so the stack is never observed empty.
 */
fun MutableList<NavKey>.selectTopLevel(key: TopLevelKey) {
    val target: List<NavKey> = if (key == HomeKey) listOf(HomeKey) else listOf(HomeKey, key)
    var common = 0
    while (common < size && common < target.size && this[common] == target[common]) common++
    if (common == 0) {
        // Malformed stack (root is not Home): add the target first so the list never becomes empty.
        addAll(0, target)
        while (size > target.size) removeAt(lastIndex)
        return
    }
    while (size > common) removeAt(lastIndex)
    addAll(target.drop(common))
}

/** Pushes [key] unless it is already on top (guards against double taps). */
fun MutableList<NavKey>.push(key: NavKey) {
    if (lastOrNull() != key) add(key)
}

/** Pops the top destination. Returns false (and does nothing) when only the root is left. */
fun MutableList<NavKey>.pop(): Boolean {
    if (size <= 1) return false
    removeAt(lastIndex)
    return true
}
