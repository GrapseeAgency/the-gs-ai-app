package com.grapsee.gsai.data.local

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Diffusion consent and install state, deliberately SEPARATE from [ModelStore].
 *
 * The separation is the feature. Both stores ask a user to let the app put
 * hundreds of megabytes on their phone forever, and in every other respect they
 * are different decisions:
 *
 *  - a user who wants offline chat has not agreed to offline image generation,
 *    and the reverse is equally true
 *  - granting one must not imply the other, so `DiffusionStore.updateConsent`
 *    writes its own preference and touches nothing in [ModelStore]
 *  - declining diffusion must not un-grant chat, and a "clear downloaded models"
 *    action has to be able to name which of the two it is removing
 *
 * An earlier version of this plan folded both into one prompt and one flag. That
 * version cannot express "yes to chat, no to images", which is a state a real
 * user reaches in one tap.
 *
 * The consent gate itself is [mayDownload], and it is a REFUSAL by default: an
 * unverified digest, a metered connection without permission, a device with too
 * little RAM, and an absent grant all produce [Result.Refused] with a reason. The
 * reasons are distinct because the fixes are distinct.
 */
object DiffusionStore {

    private const val TAG = "DiffusionStore"

    /** Its own file, so clearing the LLM's preferences cannot clear this. */
    private const val PREFS = "gsai_diffusion"

    /** Reuses [ModelStore.Consent] rather than declaring a second identical enum. */
    typealias Consent = ModelStore.Consent

    private object K {
        const val CONSENT = "diffusion_consent"
        const val PROMPT_SHOWN = "diffusion_prompt_shown"
        const val INSTALLED_PATH = "diffusion_installed_path"
        const val INSTALLED_ID = "diffusion_installed_id"
    }

    sealed class Result {
        data object Ok : Result()
        data class Refused(val reason: String) : Result()
    }

    var consent by mutableStateOf(Consent.UNDECIDED)
        private set
    var installedPath by mutableStateOf<String?>(null)
        private set
    var installedId by mutableStateOf<String?>(null)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set
    var wifiOnly by mutableStateOf(true)
        private set

    private var prefs: android.content.SharedPreferences? = null
    private var app: Context? = null

    fun init(context: Context) {
        val a = context.applicationContext
        app = a
        prefs = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        consent = runCatching {
            prefs!!.getString(K.CONSENT, null)
        }.getOrNull()?.let { name ->
            // An unknown stored name is UNDECIDED rather than a crash. A schema
            // change must not make the app unlaunchable over a consent flag.
            Consent.entries.firstOrNull { it.name == name }
        } ?: Consent.UNDECIDED
        wifiOnly = runCatching { prefs!!.getBoolean(K.wifiOnly, true) }.getOrDefault(true)
        refreshInstalled()
    }

    /** The one checkpoint, for now. */
    fun target(): DiffusionCatalog.Checkpoint = DiffusionCatalog.SDXS_512

    /**
     * Every reason this download may not start, checked before a single byte
     * moves. [Result.Refused] names WHICH one, because "cannot download" with
     * three different fixes behind it is the least useful message in the app.
     */
    fun mayDownload(): Result {
        val c = target()
        if (!DiffusionCatalog.fitsDevice(app ?: return Result.Refused("no context"))) {
            return Result.Refused(
                "this device has too little RAM for ${c.sizeLabel()} of diffusion " +
                    "weights, or is not 64-bit",
            )
        }
        if (consent != Consent.GRANTED) {
            return Result.Refused("on-device image generation has not been enabled")
        }
        if (wifiOnly && isCellular()) {
            return Result.Refused(
                "on a mobile data connection; enable 'download on mobile data' first",
            )
        }
        if (c.sha256.isBlank()) {
            return Result.Refused("${c.displayName} has no verified checksum yet")
        }
        return Result.Ok
    }

    fun updateConsent(value: Consent) {
        consent = value
        prefs?.edit()?.putString(K.CONSENT, value.name)?.apply()
        prefs?.edit()?.putBoolean(K.PROMPT_SHOWN, true)?.apply()
        Log.i(TAG, "on-device image generation consent: $value")
    }

    fun updateWifiOnly(value: Boolean) {
        wifiOnly = value
        prefs?.edit()?.putBoolean(K.wifiOnly, value)?.apply()
    }

    /**
     * True when the dialog should appear.
     *
     * Includes [DiffusionCatalog.fitsDevice] so a device that could never run it
     * is never asked about it, and records the prompt as shown only when the
     * answer is recorded -- otherwise a user who is asked on a 2 GB device and
     * later gets a bigger one would never be asked again.
     */
    fun shouldPrompt(): Boolean {
        if (consent != Consent.UNDECIDED) return false
        if (promptHasBeenShown()) return false
        return app?.let { DiffusionCatalog.fitsDevice(it) } ?: false
    }

    private fun promptHasBeenShown(): Boolean =
        runCatching { prefs?.getBoolean(K.PROMPT_SHOWN, false) == true }.getOrDefault(false)

    /** The consent text, from the catalog so there is one wording. */
    fun consentMessage(): String =
        DiffusionCatalog.consentMessage(target())

    fun destination(): java.io.File? =
        app?.let { DiffusionCatalog.destination(it, target()) }

    fun partialDestination(): java.io.File? =
        app?.let { DiffusionCatalog.partialDestination(it, target()) }

    /**
     * Re-derive the installed state from the FILESYSTEM, not from the stored
     * path. A recorded path that no longer exists is worse than no record: it
     * reads as installed and every subsequent call tries to load a missing file.
     */
    fun refreshInstalled() {
        val dest = destination()
        if (dest != null && dest.isFile && dest.length() == target().bytes) {
            installedPath = dest.absolutePath
            installedId = target().id
            return
        }
        installedPath = null
        installedId = null
    }

    fun recordInstalled(file: java.io.File) {
        refreshInstalled()
        if (installedPath == null) {
            lastError = "recorded a download that is not at the expected path: $file"
            Log.w(TAG, lastError!!)
            return
        }
        prefs?.edit()?.putString(K.INSTALLED_PATH, installedPath)?.apply()
        prefs?.edit()?.putString(K.INSTALLED_ID, installedId)?.apply()
    }

    /** Removes the weights. Says so if there was nothing to remove. */
    fun removeInstalled(): Boolean {
        val dest = destination() ?: return false
        val had = dest.isFile
        dest.delete()
        partialDestination()?.delete()
        refreshInstalled()
        prefs?.edit()?.putString(K.INSTALLED_PATH, null)?.apply()
        prefs?.edit()?.putString(K.INSTALLED_ID, null)?.apply()
        return had
    }

    fun updateLastError(reason: String?) {
        lastError = reason
    }

    /**
     * Unmetered, not Wi-Fi.
     *
     * `NET_CAPABILITY_NOT_METERED` rather than a WIFI transport test: a transport
     * test treats a metered hotspot as Wi-Fi, which would send 882 MB out over
     * someone's phone data while the app believes it is on Wi-Fi. Same rule as
     * [ModelStore], for the same reason at 1.8x the size.
     */
    private fun isCellular(): Boolean {
        val a = app ?: return false
        return runCatching {
            val cm = a.getSystemService(android.net.ConnectivityManager::class.java)
            val net = cm?.activeNetwork ?: return@runCatching false
            val caps = cm.getNetworkCapabilities(net) ?: return@runCatching false
            !caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }.getOrDefault(false)
    }
}
