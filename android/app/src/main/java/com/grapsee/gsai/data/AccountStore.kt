package com.grapsee.gsai.data

import android.content.Context

/**
 * Account identity — the one real display-name source.
 *
 * The name the user actually types at sign-up is persisted here and read by
 * the Home greeting (first name, neutral fallback when absent). Nothing is
 * invented: no name stored means no name is displayed, and the greeting
 * degrades to a plain time-of-day line.
 *
 * Backed by SharedPreferences like the rest of the local stores; swap for the
 * real account profile when the backend session API lands.
 */
object AccountStore {
    private const val PREFS = "gs_account"
    private const val KEY_DISPLAY_NAME = "gs.account.displayName"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The stored display name, or "" when the user never gave one. */
    fun displayName(context: Context): String =
        prefs(context).getString(KEY_DISPLAY_NAME, null)?.trim().orEmpty()

    /** Persist a (possibly empty → clears) display name; whitespace-trimmed. */
    fun setDisplayName(context: Context, value: String) {
        val trimmed = value.trim()
        prefs(context).edit()
            .putString(KEY_DISPLAY_NAME, trimmed.ifEmpty { null })
            .apply()
    }
}
