package com.grapsee.gsai.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The settings contract — every switch, chip and slider on the Settings
 * screen is a real, remembered preference. State lives in snapshot objects
 * (observable from any composable, readable anywhere) and writes through to
 * SharedPreferences the moment it changes, so nothing forgets between
 * launches. init() hydrates from disk once, from GSApplication, before any
 * UI reads the store.
 *
 * HONESTY CONTRACT: a row exists here only if some runtime code path reads
 * it. The 0.77.7 sweep deleted nine fields (auto-title, double-tap send,
 * memory, personalisation, reasoning effort, training opt-in, passcode,
 * biometric unlock, AI language) whose toggles persisted but were read by
 * nothing — a switch that does nothing is worse than no switch. Remaining
 * keys are live; prefer-local and push-notifications have real consumers.
 */
object SettingsStore {
    private const val PREFS = "gs_settings"
    private var prefs: SharedPreferences? = null

    // Appearance
    var themeMode by mutableStateOf("System"); private set
    /** One of the six families in `GsAccents` — a real re-theme, not a swatch. */
    var accentId by mutableStateOf("apricot"); private set
    /** Blurred | Gradient | Solid — how much of the bloom backdrop is drawn. */
    var backgroundStyle by mutableStateOf("Blurred"); private set
    var reduceAnimations by mutableStateOf(false); private set
    // AI
    /**
     * Commit 1(b) of the mobile wiring: prefer the on-device engine over the
     * provider when a model is installed.
     *
     * DEFAULT OFF, deliberately. The local path is only faster than a provider
     * round trip on some prompts and some devices, it burns battery, and a
     * default that silently changes where a user's chat data goes is a decision
     * the user has to make. The device-class check runs before the network call
     * ONLY while this is true; with it off, routing is exactly as it was.
     */
    var preferLocal by mutableStateOf(false); private set
    // Notifications
    var pushNotifications by mutableStateOf(true); private set
    // Accessibility
    var fontScale by mutableStateOf(1.0f); private set
    var highContrast by mutableStateOf(false); private set
    var reduceMotion by mutableStateOf(false); private set
    var screenReaderHints by mutableStateOf(false); private set
    var haptics by mutableStateOf(true); private set

    /** Call once from GSApplication before any UI reads the store. */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val p = prefs ?: return
        // Per-key guards: a preference written by an older build under a
        // different type (or an OEM backup restore mangling one value) must
        // cost that ONE key its default — never the whole launch. A bare
        // getBoolean here throws ClassCastException inside Application
        // onCreate, which reads on the device as "app opens, instantly
        // closes, every single time".
        themeMode = p.string(K.themeMode, themeMode)
        accentId = p.string(K.accentId, accentId)
        backgroundStyle = p.string(K.backgroundStyle, backgroundStyle)
        reduceAnimations = p.bool(K.reduceAnimations, reduceAnimations)
        // false is the field's own value, so an unset key means OFF. Turning
        // local AI on without asking is the worse failure.
        preferLocal = p.bool(K.preferLocal, preferLocal)
        pushNotifications = p.bool(K.pushNotifications, pushNotifications)
        fontScale = p.flt(K.fontScale, fontScale)
        highContrast = p.bool(K.highContrast, highContrast)
        reduceMotion = p.bool(K.reduceMotion, reduceMotion)
        screenReaderHints = p.bool(K.screenReaderHints, screenReaderHints)
        haptics = p.bool(K.haptics, haptics)
    }

    private fun SharedPreferences.bool(key: String, fallback: Boolean): Boolean =
        runCatching { getBoolean(key, fallback) }.getOrDefault(fallback)

    private fun SharedPreferences.string(key: String, fallback: String): String =
        runCatching { getString(key, null) ?: fallback }.getOrDefault(fallback)

    private fun SharedPreferences.flt(key: String, fallback: Float): Float =
        runCatching { getFloat(key, fallback) }.getOrDefault(fallback)

    private object K {
        const val themeMode = "settings.themeMode"
        const val accentId = "settings.accentId"
        const val backgroundStyle = "settings.backgroundStyle"
        const val reduceAnimations = "settings.reduceAnimations"
        const val preferLocal = "settings.preferLocal"
        const val pushNotifications = "settings.pushNotifications"
        const val fontScale = "settings.fontScale"
        const val highContrast = "settings.highContrast"
        const val reduceMotion = "settings.reduceMotion"
        const val screenReaderHints = "settings.screenReaderHints"
        const val haptics = "settings.haptics"
    }

    private fun put(key: String, value: Boolean) {
        prefs?.edit()?.putBoolean(key, value)?.apply()
    }

    private fun put(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }

    private fun put(key: String, value: Float) {
        prefs?.edit()?.putFloat(key, value)?.apply()
    }

    /**
     * FLASH MODE (Phase 3) — the composer's thinking mode, persisted PER
     * CONVERSATION (key `mode.<conversationId>`; a brand-new chat falls back
     * to `mode.new` so the choice survives until the conversation exists).
     * Only the three wire values are honored; anything else degrades to
     * 'flash' (the backend default).
     */
    fun chatMode(conversationId: String?): String {
        val p = prefs ?: return "flash"
        val valid = setOf("flash", "thinking", "auto")
        val perChat = conversationId?.let { p.getString("mode.$it", null) }
        return when {
            perChat != null && perChat in valid -> perChat
            else -> p.getString("mode.new", null)?.takeIf { it in valid } ?: "flash"
        }
    }

    fun setChatMode(conversationId: String?, mode: String) {
        val key = if (conversationId != null) "mode.$conversationId" else "mode.new"
        put(key, mode)
    }

    // Appearance
    fun updateThemeMode(v: String) { themeMode = v; put(K.themeMode, v) }
    fun updateAccentId(v: String) { accentId = v; put(K.accentId, v) }
    fun updateBackgroundStyle(v: String) { backgroundStyle = v; put(K.backgroundStyle, v) }
    fun updateReduceAnimations(v: Boolean) { reduceAnimations = v; put(K.reduceAnimations, v) }

    /** Commit 1(b). See the field for why the default is off. */
    fun updatePreferLocal(v: Boolean) { preferLocal = v; put(K.preferLocal, v) }
    // Notifications
    fun updatePushNotifications(v: Boolean) { pushNotifications = v; put(K.pushNotifications, v) }
    // Accessibility
    /** Live stage during a slider drag: memory only, no disk write per frame.
     *  The commit lands once in [updateFontScale] on release. */
    fun stageFontScale(v: Float) { fontScale = v }
    fun updateFontScale(v: Float) { fontScale = v; put(K.fontScale, v) }
    fun updateHighContrast(v: Boolean) { highContrast = v; put(K.highContrast, v) }
    fun updateReduceMotion(v: Boolean) { reduceMotion = v; put(K.reduceMotion, v) }
    fun updateScreenReaderHints(v: Boolean) { screenReaderHints = v; put(K.screenReaderHints, v) }
    fun updateHaptics(v: Boolean) { haptics = v; put(K.haptics, v) }
}
