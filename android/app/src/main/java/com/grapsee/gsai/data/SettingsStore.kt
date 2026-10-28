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
    /**
     * The master switch on Notifications & Alerts. It keeps the key the app has
     * always used (`settings.pushNotifications`) so no existing writer orients
     * around an orphan; what changed is that [com.grapsee.gsai.data.notifications.GsNotifier]
     * now consults it on the way to every `notify()`. Until then the switch
     * persisted and nothing read it, which is the exact failure this file's
     * HONESTY CONTRACT exists to catch.
     */
    var pushNotifications by mutableStateOf(true); private set
    /** Per-type switches — the mock's five "Notification Types" rows. Each one
     *  is read by GsNotifier.allows() before a notice of that kind posts. */
    var noticeMessages by mutableStateOf(true); private set
    var noticeAiUpdates by mutableStateOf(true); private set
    var noticeTips by mutableStateOf(true); private set
    var noticeAccountActivity by mutableStateOf(true); private set
    var noticeProductUpdates by mutableStateOf(true); private set
    /** The "In-App Notifications" channel row. Read by the in-app notice
     *  surfaces (the LiveUpdate card on Your chats) to decide whether to show
     *  themselves, so it is a live switch rather than a second master. */
    var noticeInApp by mutableStateOf(true); private set
    /** Quiet Hours: the window itself is remembered, and GsNotifier suppresses
     *  live posts inside it. Off by default — a switch that hushes the phone
     *  without being asked is the wrong default. */
    var quietHoursEnabled by mutableStateOf(false); private set
    var quietStartMinute by mutableStateOf(22 * 60); private set
    var quietEndMinute by mutableStateOf(7 * 60); private set
    /** Biometric Login. Read by the app-lock gate in GsNavHost, which refuses
     *  to show the shell until BiometricPrompt succeeds — a lock that nothing
     *  checks would be the dead switch this file's contract forbids. */
    var biometricUnlock by mutableStateOf(false); private set
    /** When the last successful biometric check happened, epoch millis; 0 when
     *  there has never been one. The Account & Security closing card reports
     *  this instead of an invented "Today, 9:12 AM". */
    var lastSecurityCheckMillis by mutableStateOf(0L); private set
    /** Memory: whether Nexly keeps and surfaces saved memories. iOS twin:
     *  SettingsStore.memory. The Memory Management mock's "Memory is ON" pill
     *  reads and writes this; default ON because the app already remembers. */
    var memory by mutableStateOf(true); private set
    // Accessibility
    var fontScale by mutableStateOf(1.0f); private set
    var highContrast by mutableStateOf(false); private set
    var reduceMotion by mutableStateOf(false); private set
    var screenReaderHints by mutableStateOf(false); private set
    var haptics by mutableStateOf(true); private set

    // ---- Model selection (docs/reference/pending/model-selection.png) ----
    /** The model radio the reference draws. Persisted because the screen used
     *  to hold this in a local `remember`, so the tick jumped back to Pro on
     *  every visit. NOTE: the backend ignores a client-chosen model
     *  (`src/app/api/v1/conversations/[id]/route.ts:20`), so this is a stored
     *  preference, not routing. The screen says so in one footnote. */
    var preferredModel by mutableStateOf("pro"); private set
    /** The effort tile. Kept separate from [chatMode] on purpose: the wire only
     *  has three modes, so Minimal and Light would both read `flash` and two
     *  tiles would light up at once. */
    var reasoningEffort by mutableStateOf("standard"); private set
    /** "Remember my choices" — decides whether a new chat keeps the last mode. */
    var rememberChoices by mutableStateOf(true); private set

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
        noticeMessages = p.bool(K.noticeMessages, noticeMessages)
        noticeAiUpdates = p.bool(K.noticeAiUpdates, noticeAiUpdates)
        noticeTips = p.bool(K.noticeTips, noticeTips)
        noticeAccountActivity = p.bool(K.noticeAccountActivity, noticeAccountActivity)
        noticeProductUpdates = p.bool(K.noticeProductUpdates, noticeProductUpdates)
        noticeInApp = p.bool(K.noticeInApp, noticeInApp)
        quietHoursEnabled = p.bool(K.quietHoursEnabled, quietHoursEnabled)
        quietStartMinute = p.integer(K.quietStartMinute, quietStartMinute)
        quietEndMinute = p.integer(K.quietEndMinute, quietEndMinute)
        biometricUnlock = p.bool(K.biometricUnlock, biometricUnlock)
        lastSecurityCheckMillis = p.long(K.lastSecurityCheckMillis, lastSecurityCheckMillis)
        memory = p.bool(K.memory, memory)
        fontScale = p.flt(K.fontScale, fontScale)
        highContrast = p.bool(K.highContrast, highContrast)
        reduceMotion = p.bool(K.reduceMotion, reduceMotion)
        screenReaderHints = p.bool(K.screenReaderHints, screenReaderHints)
        haptics = p.bool(K.haptics, haptics)
        preferredModel = p.string(K.preferredModel, preferredModel)
        reasoningEffort = p.string(K.reasoningEffort, reasoningEffort)
        rememberChoices = p.bool(K.rememberChoices, rememberChoices)
    }

    private fun SharedPreferences.bool(key: String, fallback: Boolean): Boolean =
        runCatching { getBoolean(key, fallback) }.getOrDefault(fallback)

    private fun SharedPreferences.string(key: String, fallback: String): String =
        runCatching { getString(key, null) ?: fallback }.getOrDefault(fallback)

    private fun SharedPreferences.flt(key: String, fallback: Float): Float =
        runCatching { getFloat(key, fallback) }.getOrDefault(fallback)

    private fun SharedPreferences.integer(key: String, fallback: Int): Int =
        runCatching { getInt(key, fallback) }.getOrDefault(fallback)

    private fun SharedPreferences.long(key: String, fallback: Long): Long =
        runCatching { getLong(key, fallback) }.getOrDefault(fallback)

    private object K {
        const val themeMode = "settings.themeMode"
        const val accentId = "settings.accentId"
        const val backgroundStyle = "settings.backgroundStyle"
        const val reduceAnimations = "settings.reduceAnimations"
        const val preferLocal = "settings.preferLocal"
        const val pushNotifications = "settings.pushNotifications"
        const val noticeMessages = "settings.notice.messages"
        const val noticeAiUpdates = "settings.notice.aiUpdates"
        const val noticeTips = "settings.notice.tips"
        const val noticeAccountActivity = "settings.notice.accountActivity"
        const val noticeProductUpdates = "settings.notice.productUpdates"
        const val noticeInApp = "settings.notice.inApp"
        const val quietHoursEnabled = "settings.quietHours.enabled"
        const val quietStartMinute = "settings.quietHours.start"
        const val quietEndMinute = "settings.quietHours.end"
        const val biometricUnlock = "settings.biometricUnlock"
        const val lastSecurityCheckMillis = "settings.lastSecurityCheck"
        const val memory = "settings.memory"
        const val fontScale = "settings.fontScale"
        const val highContrast = "settings.highContrast"
        const val reduceMotion = "settings.reduceMotion"
        const val screenReaderHints = "settings.screenReaderHints"
        const val haptics = "settings.haptics"
        const val preferredModel = "settings.preferredModel"
        const val reasoningEffort = "settings.reasoningEffort"
        const val rememberChoices = "settings.rememberChoices"
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

    private fun put(key: String, value: Int) {
        prefs?.edit()?.putInt(key, value)?.apply()
    }

    private fun put(key: String, value: Long) {
        prefs?.edit()?.putLong(key, value)?.apply()
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
        // "Remember my choices" (model-selection reference) governs the NEW-chat
        // default only. A per-conversation choice belongs to that conversation
        // and is never suppressed — otherwise reopening a chat would silently
        // change how it answers.
        if (conversationId == null && !rememberChoices) {
            put(key, "flash")
            return
        }
        put(key, mode)
    }

    // ---- Model selection -------------------------------------------------
    /** Effort tile -> wire mode.
     *
     *  The wire accepts exactly `flash | thinking | auto`
     *  (`src/lib/model-capabilities.ts:515-521`), so **Minimal and Light are the
     *  same mode today**: the tile remembers which one was tapped, the server
     *  cannot yet tell them apart. Widening this is a backend change, not a UI
     *  one.
     *
     *  The timings printed on the tiles ("~ 1–2 seconds" … "~ 10–30 seconds")
     *  are the owner's copy from the reference art, NOT measurements of this
     *  build. They are a label on the choice, not a promise. */
    fun wireModeForEffort(effort: String): String = when (effort) {
        "minimal", "light" -> "flash"
        "high" -> "thinking"
        else -> "auto"
    }

    /** Inverse of [wireModeForEffort], used when a stored mode is all we have.
     *  `flash` maps to `light` because that is the tier's own name; `minimal`
     *  can only come back from the tile the user tapped. */
    fun effortForMode(mode: String): String = when (mode) {
        "thinking" -> "high"
        "auto" -> "standard"
        else -> "light"
    }

    fun updatePreferredModel(v: String) { preferredModel = v; put(K.preferredModel, v) }

    /** Writes the tile AND the wire mode, so "the chip I tapped" and "what the
     *  next reply does" never disagree. */
    fun updateReasoningEffort(v: String) {
        reasoningEffort = v
        put(K.reasoningEffort, v)
        setChatMode(null, wireModeForEffort(v))
    }

    fun updateRememberChoices(v: Boolean) { rememberChoices = v; put(K.rememberChoices, v) }

    // Appearance
    fun updateThemeMode(v: String) { themeMode = v; put(K.themeMode, v) }
    fun updateAccentId(v: String) { accentId = v; put(K.accentId, v) }
    fun updateBackgroundStyle(v: String) { backgroundStyle = v; put(K.backgroundStyle, v) }
    fun updateReduceAnimations(v: Boolean) { reduceAnimations = v; put(K.reduceAnimations, v) }

    /** Commit 1(b). See the field for why the default is off. */
    fun updatePreferLocal(v: Boolean) { preferLocal = v; put(K.preferLocal, v) }
    // Notifications
    fun updatePushNotifications(v: Boolean) { pushNotifications = v; put(K.pushNotifications, v) }

    /** Which per-type switch belongs to a `GsNotifier.Kind` id. The mapping
     *  lives here rather than on the enum so the store keeps no dependency on
     *  the notifications package. */
    fun noticeEnabled(kindId: String): Boolean = when (kindId) {
        "messages" -> noticeMessages
        "ai-updates" -> noticeAiUpdates
        "tips" -> noticeTips
        "account-activity" -> noticeAccountActivity
        "product-updates" -> noticeProductUpdates
        // An id with no row must not silently default to on.
        else -> false
    }

    fun updateNoticeEnabled(kindId: String, v: Boolean) {
        when (kindId) {
            "messages" -> { noticeMessages = v; put(K.noticeMessages, v) }
            "ai-updates" -> { noticeAiUpdates = v; put(K.noticeAiUpdates, v) }
            "tips" -> { noticeTips = v; put(K.noticeTips, v) }
            "account-activity" -> { noticeAccountActivity = v; put(K.noticeAccountActivity, v) }
            "product-updates" -> { noticeProductUpdates = v; put(K.noticeProductUpdates, v) }
        }
    }

    fun updateNoticeInApp(v: Boolean) { noticeInApp = v; put(K.noticeInApp, v) }

    fun updateQuietHoursEnabled(v: Boolean) {
        quietHoursEnabled = v; put(K.quietHoursEnabled, v)
    }

    fun updateQuietWindow(startMinute: Int, endMinute: Int) {
        quietStartMinute = startMinute; put(K.quietStartMinute, startMinute)
        quietEndMinute = endMinute; put(K.quietEndMinute, endMinute)
    }

    /** The app-lock gate reads [biometricUnlock]; nothing else may write it. */
    fun updateBiometricUnlock(v: Boolean) { biometricUnlock = v; put(K.biometricUnlock, v) }

    fun updateLastSecurityCheck(millis: Long) {
        lastSecurityCheckMillis = millis; put(K.lastSecurityCheckMillis, millis)
    }
    fun updateMemory(v: Boolean) { memory = v; put(K.memory, v) }
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
