package com.grapsee.gsai.data

import android.content.Context

/**
 * Lightweight session gate.
 * First launch walks Auth → Onboarding before the command centre; later
 * launches go straight Home. Backed by SharedPreferences — swap for the
 * real auth/token store when the backend session API lands.
 */
object SessionStore {
    private const val PREFS = "gs_session"
    private const val KEY_SESSION_ACTIVE = "gs.session.active"
    private const val KEY_ONBOARDED = "gs.onboarded"
    private const val KEY_CLIENT_ID = "gs.client.id"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Stable per-install identifier sent as `x-session-id` on every request.
     * The platform edge REQUIRES it (session affinity — without the header the
     * invocation is rejected 400 before reaching the app), and a stable value
     * keeps this install affinity-pinned. First call mints and persists a UUID.
     */
    fun stableClientId(context: Context): String {
        prefs(context).getString(KEY_CLIENT_ID, null)?.let { return it }
        val fresh = java.util.UUID.randomUUID().toString()
        prefs(context).edit().putString(KEY_CLIENT_ID, fresh).apply()
        return fresh
    }

    fun isSessionActive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SESSION_ACTIVE, false)

    fun activateSession(context: Context) {
        prefs(context).edit().putBoolean(KEY_SESSION_ACTIVE, true).apply()
    }

    fun isOnboarded(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONBOARDED, false)

    fun setOnboarded(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ONBOARDED, value).apply()
    }
}
