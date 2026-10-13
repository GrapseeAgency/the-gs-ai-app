package com.grapsee.gsai.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * TASK 1 — link routing for reply content.
 *
 * Contract: a TAP opens the in-app browser (Chrome Custom Tabs with the app's
 * monochrome toolbar); the EXTERNAL browser happens only on the user's
 * explicit long-press (or via Custom Tabs' own overflow menu). No reply link
 * ever leaves the app by accident.
 *
 * Custom Tabs was chosen over a WebView surface deliberately: it is the
 * platform browser process (zero extra app chrome to maintain, shares the
 * user's session, and its overflow menu already carries "Open in Browser").
 */
object GsLinks {

    /** Tap path: in-app browser, falling back to the external browser. */
    fun openInApp(context: Context, url: String) {
        val uri = parseHttp(url) ?: return
        runCatching {
            val intent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(false)
                .build()
            intent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.launchUrl(context, uri)
        }.onFailure {
            openExternal(context, url)
        }
    }

    /** Explicit long-press path: the user asked to leave the app. */
    fun openExternal(context: Context, url: String) {
        val uri = parseHttp(url) ?: return
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: ActivityNotFoundException) {
            // No external browser exists. Honest no-op: the in-app path above
            // is the one that matters; there is nothing to fall back TO.
        }
    }

    private fun parseHttp(url: String): Uri? {
        val uri = Uri.parse(url) ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        return if (scheme == "http" || scheme == "https") uri else null
    }
}
