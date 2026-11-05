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
    private const val KEY_DOB = "gs.account.dateOfBirth"
    private const val KEY_LOCATION = "gs.account.location"

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

    /**
     * Date of birth and location.
     *
     * Profile used to print "Not set" under both, with no chevron and no
     * editor — a row that names a fact the app will never learn. They are
     * stored here, next to the rest of the identity, and edited on the same
     * page as the name and address.
     */
    fun dateOfBirth(context: Context): String =
        prefs(context).getString(KEY_DOB, null)?.trim().orEmpty()

    fun setDateOfBirth(context: Context, value: String) {
        val trimmed = value.trim()
        prefs(context).edit().putString(KEY_DOB, trimmed.ifEmpty { null }).apply()
    }

    fun location(context: Context): String =
        prefs(context).getString(KEY_LOCATION, null)?.trim().orEmpty()

    fun setLocation(context: Context, value: String) {
        val trimmed = value.trim()
        prefs(context).edit().putString(KEY_LOCATION, trimmed.ifEmpty { null }).apply()
    }
}
