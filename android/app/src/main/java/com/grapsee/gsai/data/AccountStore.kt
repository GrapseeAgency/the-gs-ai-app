package com.grapsee.gsai.data

import android.content.Context

/**
 * Account identity — the one real name/email source.
 *
 * The name and email the user actually types at sign-up/sign-in are persisted
 * here and read by the Home greeting (first name, neutral fallback when
 * absent) and the drawer account header (name, email, initials). Nothing is
 * invented: nothing stored means nothing is displayed, and both surfaces
 * degrade to neutral lines instead of fabricating an identity.
 *
 * Backed by SharedPreferences like the rest of the local stores; swap for the
 * real account profile when the backend session API lands.
 */
object AccountStore {
    private const val PREFS = "gs_account"
    private const val KEY_DISPLAY_NAME = "gs.account.displayName"
    private const val KEY_EMAIL = "gs.account.email"

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

    /** The stored account email, or "" when the user never gave one. */
    fun email(context: Context): String =
        prefs(context).getString(KEY_EMAIL, null)?.trim().orEmpty()

    /** Persist a (possibly empty → clears) account email; whitespace-trimmed. */
    fun setEmail(context: Context, value: String) {
        val trimmed = value.trim()
        prefs(context).edit()
            .putString(KEY_EMAIL, trimmed.ifEmpty { null })
            .apply()
    }
}
