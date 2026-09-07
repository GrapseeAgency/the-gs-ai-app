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
 * UI reads the store. Preference persistence is local-first: switches whose
 * subsystems arrive later (push, passcode, memory) already remember their
 * declared value — the same client-held preference shape the benchmark apps
 * use until their server half is reachable.
 */
object SettingsStore {
    private const val PREFS = "gs_settings"
    private var prefs: SharedPreferences? = null

    // Appearance
    var themeMode by mutableStateOf("System"); private set
    var reduceAnimations by mutableStateOf(false); private set
    // Chat
    var enterToSend by mutableStateOf(true); private set
    var autoTitleChats by mutableStateOf(true); private set
    var sendDoubleTap by mutableStateOf(false); private set
    // AI
    var memory by mutableStateOf(true); private set
    var personalisation by mutableStateOf(true); private set
    var reasoningEffort by mutableStateOf("Medium"); private set
    // Privacy
    var trainingOptIn by mutableStateOf(false); private set
    // Security
    var appPasscode by mutableStateOf(false); private set
    var biometricUnlock by mutableStateOf(false); private set
    // Notifications
    var pushNotifications by mutableStateOf(true); private set
    var sounds by mutableStateOf(true); private set
    var taskAlerts by mutableStateOf(true); private set
    var emailDigest by mutableStateOf(false); private set
    // Language
    var aiLanguage by mutableStateOf("EN"); private set
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
        themeMode = p.getString(K.themeMode, null) ?: themeMode
        reduceAnimations = p.getBoolean(K.reduceAnimations, reduceAnimations)
        enterToSend = p.getBoolean(K.enterToSend, enterToSend)
        autoTitleChats = p.getBoolean(K.autoTitleChats, autoTitleChats)
        sendDoubleTap = p.getBoolean(K.sendDoubleTap, sendDoubleTap)
        memory = p.getBoolean(K.memory, memory)
        personalisation = p.getBoolean(K.personalisation, personalisation)
        reasoningEffort = p.getString(K.reasoningEffort, null) ?: reasoningEffort
        trainingOptIn = p.getBoolean(K.trainingOptIn, trainingOptIn)
        appPasscode = p.getBoolean(K.appPasscode, appPasscode)
        biometricUnlock = p.getBoolean(K.biometricUnlock, biometricUnlock)
        pushNotifications = p.getBoolean(K.pushNotifications, pushNotifications)
        sounds = p.getBoolean(K.sounds, sounds)
        taskAlerts = p.getBoolean(K.taskAlerts, taskAlerts)
        emailDigest = p.getBoolean(K.emailDigest, emailDigest)
        aiLanguage = p.getString(K.aiLanguage, null) ?: aiLanguage
        fontScale = p.getFloat(K.fontScale, fontScale)
        highContrast = p.getBoolean(K.highContrast, highContrast)
        reduceMotion = p.getBoolean(K.reduceMotion, reduceMotion)
        screenReaderHints = p.getBoolean(K.screenReaderHints, screenReaderHints)
        haptics = p.getBoolean(K.haptics, haptics)
    }

    private object K {
        const val themeMode = "settings.themeMode"
        const val reduceAnimations = "settings.reduceAnimations"
        const val enterToSend = "settings.enterToSend"
        const val autoTitleChats = "settings.autoTitleChats"
        const val sendDoubleTap = "settings.sendDoubleTap"
        const val memory = "settings.memory"
        const val personalisation = "settings.personalisation"
        const val reasoningEffort = "settings.reasoningEffort"
        const val trainingOptIn = "settings.trainingOptIn"
        const val appPasscode = "settings.appPasscode"
        const val biometricUnlock = "settings.biometricUnlock"
        const val pushNotifications = "settings.pushNotifications"
        const val sounds = "settings.sounds"
        const val taskAlerts = "settings.taskAlerts"
        const val emailDigest = "settings.emailDigest"
        const val aiLanguage = "settings.aiLanguage"
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

    // Appearance
    fun updateThemeMode(v: String) { themeMode = v; put(K.themeMode, v) }
    fun updateReduceAnimations(v: Boolean) { reduceAnimations = v; put(K.reduceAnimations, v) }
    // Chat
    fun updateEnterToSend(v: Boolean) { enterToSend = v; put(K.enterToSend, v) }
    fun updateAutoTitleChats(v: Boolean) { autoTitleChats = v; put(K.autoTitleChats, v) }
    fun updateSendDoubleTap(v: Boolean) { sendDoubleTap = v; put(K.sendDoubleTap, v) }
    // AI
    fun updateMemory(v: Boolean) { memory = v; put(K.memory, v) }
    fun updatePersonalisation(v: Boolean) { personalisation = v; put(K.personalisation, v) }
    fun updateReasoningEffort(v: String) { reasoningEffort = v; put(K.reasoningEffort, v) }
    // Privacy
    fun updateTrainingOptIn(v: Boolean) { trainingOptIn = v; put(K.trainingOptIn, v) }
    // Security
    fun updateAppPasscode(v: Boolean) { appPasscode = v; put(K.appPasscode, v) }
    fun updateBiometricUnlock(v: Boolean) { biometricUnlock = v; put(K.biometricUnlock, v) }
    // Notifications
    fun updatePushNotifications(v: Boolean) { pushNotifications = v; put(K.pushNotifications, v) }
    fun updateSounds(v: Boolean) { sounds = v; put(K.sounds, v) }
    fun updateTaskAlerts(v: Boolean) { taskAlerts = v; put(K.taskAlerts, v) }
    fun updateEmailDigest(v: Boolean) { emailDigest = v; put(K.emailDigest, v) }
    // Language
    fun updateAiLanguage(v: String) { aiLanguage = v; put(K.aiLanguage, v) }
    // Accessibility
    fun updateFontScale(v: Float) { fontScale = v; put(K.fontScale, v) }
    fun updateHighContrast(v: Boolean) { highContrast = v; put(K.highContrast, v) }
    fun updateReduceMotion(v: Boolean) { reduceMotion = v; put(K.reduceMotion, v) }
    fun updateScreenReaderHints(v: Boolean) { screenReaderHints = v; put(K.screenReaderHints, v) }
    fun updateHaptics(v: Boolean) { haptics = v; put(K.haptics, v) }
}
