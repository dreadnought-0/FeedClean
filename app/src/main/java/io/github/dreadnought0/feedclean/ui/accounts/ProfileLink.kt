package io.github.dreadnought0.feedclean.ui.accounts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.data.model.Handles
import java.util.Locale

/**
 * The public profile URL for [handle], built from [template] (`R.string.screens_profile_url_template`,
 * one `%1$s` for the handle). Null when the handle is missing or contains anything other than letters,
 * digits, "." or "_", so nothing unexpected can end up in the link.
 */
fun profileUrl(handle: String?, template: String): String? {
    val normalized = Handles.normalize(handle) ?: return null
    if (!normalized.all { it.isLetterOrDigit() || it == '.' || it == '_' }) return null
    return String.format(Locale.ROOT, template, normalized)
}

/**
 * "Open in TikTok": fires a plain `ACTION_VIEW` for the account's public profile URL, addressed to the
 * TikTok package so the link is handled by TikTok itself (never a browser).
 *
 * - FeedClean makes no network request; TikTok opens its own profile screen through its link handling.
 * - Navigation only: nothing is tapped or blocked on the user's behalf. Blocking stays on
 *   TikTok's screen via the overlay card, where every block needs the user's tap.
 *
 * Only offered for accounts with a confirmed handle. Returns false when TikTok can't take the link (e.g.
 * not installed), so the caller can say so.
 */
fun openProfileInTikTok(context: Context, handle: String): Boolean {
    val url = profileUrl(handle, context.getString(R.string.screens_profile_url_template)) ?: return false
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        .setPackage(context.getString(R.string.ui_target_app_package))
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
