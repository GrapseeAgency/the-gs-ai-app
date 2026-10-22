package com.grapsee.gsai.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The answers the setup wizard actually uses.
 *
 * The first wizard pass collected interests, capabilities, response style and
 * personality into screen-local state and threw them away on finish. That is
 * indistinguishable from not asking. This store hydrates once at app start
 * (same contract as [SettingsStore]) and is read by the surfaces that can
 * honour each answer:
 *
 *  - Home + the chat empty state choose their suggestion cards from
 *    [interests] / [capabilities].
 *
 * What is READ today, honestly: [capabilities] is read by Home and the chat
 * empty state, which is the whole reason this store exists. [interests],
 * [responseStyle] and [personality] are written by the wizard and have NO
 * reader on either platform yet — the responder/prompt-prefix that would shape
 * tone from them is not built, and there is no Profile screen showing them or
 * "Retake setup" re-opening the wizard prefilled. They are persisted because
 * the alternative is throwing a person's answers away on exit, which is the bug
 * this file was written to fix. The three bullets above described consumers that
 * do not exist; a contract comment nobody can execute is not a contract, so the
 * claim is replaced by the gap.
 *
 * Tracked as a follow-up, not silently dropped: land the readers or delete the
 * fields. iOS twin: Networking/OnboardingProfileStore.swift, same wording.
 */
object OnboardingProfileStore {
    private const val PREFS = "gs_profile"
    private var prefs: SharedPreferences? = null

    var interests by mutableStateOf<Set<String>>(emptySet()); private set
    var capabilities by mutableStateOf<Set<String>>(emptySet()); private set
    /** Concise | Standard | Detailed */
    var responseStyle by mutableStateOf("Standard"); private set
    /** Professional | Friendly | Playful */
    var personality by mutableStateOf("Friendly"); private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val p = prefs ?: return
        interests = p.csv(K.interests)
        capabilities = p.csv(K.capabilities)
        responseStyle = p.safeString(K.responseStyle, responseStyle)
        personality = p.safeString(K.personality, personality)
    }

    fun save(
        interests: Collection<String>,
        capabilities: Collection<String>,
        responseStyle: String,
        personality: String,
    ) {
        this.interests = interests.toSet()
        this.capabilities = capabilities.toSet()
        this.responseStyle = responseStyle
        this.personality = personality
        prefs?.edit()
            ?.putString(K.interests, this.interests.joinToString(","))
            ?.putString(K.capabilities, this.capabilities.joinToString(","))
            ?.putString(K.responseStyle, responseStyle)
            ?.putString(K.personality, personality)
            ?.apply()
    }

    fun clear() {
        interests = emptySet()
        capabilities = emptySet()
        responseStyle = "Standard"
        personality = "Friendly"
        prefs?.edit()?.clear()?.apply()
    }

    /** A short, honest description for Profile; null when setup was skipped. */
    fun summary(): String? {
        if (interests.isEmpty() && capabilities.isEmpty()) return null
        val topics = interests.take(3).joinToString(", ")
        return when {
            topics.isNotBlank() && capabilities.isNotEmpty() ->
                "$topics · ${capabilities.size} tools pinned"
            topics.isNotBlank() -> topics
            else -> "${capabilities.size} tools pinned"
        }
    }

    private object K {
        const val interests = "profile.interests"
        const val capabilities = "profile.capabilities"
        const val responseStyle = "profile.responseStyle"
        const val personality = "profile.personality"
    }

    private fun SharedPreferences.safeString(key: String, fallback: String): String =
        runCatching { getString(key, null) ?: fallback }.getOrDefault(fallback)

    private fun SharedPreferences.csv(key: String): Set<String> =
        runCatching {
            getString(key, null)
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                .orEmpty()
        }.getOrDefault(emptySet())
}
