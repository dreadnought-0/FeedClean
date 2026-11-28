package io.github.dreadnought0.feedclean.ui.stats

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import java.util.Locale

/**
 * Text decided by pure mapping code but resolved in Compose, so the mapping (which string, which arguments)
 * can be unit-tested without Android resources. Shared by the Stats, Accounts and Home screens.
 */
sealed interface UiText {
    /**
     * A string resource with format arguments. An argument that is itself a [UiText] is resolved first.
     */
    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** Literal text (data from the database, e.g. a raw on-screen label). */
    data class Plain(val text: String) : UiText
}

/** Resolves this text against the current resources. */
@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Plain -> text
    is UiText.Res -> {
        val resolvedArgs = args.map { arg -> if (arg is UiText) arg.resolve() else arg }
        stringResource(id, *resolvedArgs.toTypedArray())
    }
}

/** The first locale of the current configuration, for date and number formatting. */
@Composable
fun currentLocale(): Locale {
    // An empty locale list never happens on a real device; ROOT keeps this observable (no Locale.getDefault()).
    return LocalConfiguration.current.locales.get(0) ?: Locale.ROOT
}
