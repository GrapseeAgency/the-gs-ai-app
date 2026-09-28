package com.grapsee.gsai.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.grapsee.gsai.native.GsNativeLoader
import java.io.File

/**
 * Consent, the device-class decision, and the installed model path.
 *
 * This is the only place that decides whether a download may happen. That is
 * deliberate: the rule "never download without consent, and never on cellular
 * without consent" is enforceable in one file and unenforceable if every caller
 * re-implements it. [ModelDownloader] deliberately knows nothing about consent.
 *
 * ## The three consent states
 *
 *     UNDECIDED   the user has not been asked. No download, no prompt on its own.
 *     GRANTED     the user said yes. Downloads proceed, including on cellular.
 *     DECLINED    the user said no. No download, ever, and the dialog does not
 *                 reappear on its own.
 *
 * `UNSEEN` is separate from `UNDECIDED` and is the difference between "ask me
 * once" and "ask me until I answer": once the dialog has been shown, the state
 * moves to GRANTED or DECLINED and never back to UNDECIDED, so a user who
 * declined is not prompted on every launch.
 */
object ModelStore {

    private const val TAG = "ModelStore"
    private const val PREFS = "gs_local_model"

    enum class Consent { UNDECIDED, GRANTED, DECLINED }

    var consent by mutableStateOf(Consent.UNDECIDED); private set
    var tier by mutableStateOf(ModelCatalog.Tier.LOW); private set
    var installedPath by mutableStateOf<String?>(null); private set
    var installedModelId by mutableStateOf<String?>(null); private set
    var lastError by mutableStateOf<String?>(null); private set

    /** The UI reports download failures here so the reason survives a recompose. */
    fun setLastError(reason: String?) {
        lastError = reason
    }

    private var prefs: android.content.SharedPreferences? = null
    private var appContext: Context? = null

    /** Call once from GSApplication, alongside SettingsStore.init. */
    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        consent = runCatching { prefs!!.getString(K.consent, null) }
            .getOrNull()
            ?.let { runCatching { Consent.valueOf(it) }.getOrNull() }
            ?: Consent.UNDECIDED
        wifiOnlyConsent = runCatching { prefs!!.getBoolean(K.wifiOnly, true) }
            .getOrDefault(true)
        tier = ModelCatalog.classify(app)
        refreshInstalled()
    }

    private object K {
        const val consent = "local.consent"
        const val installedPath = "local.installedPath"
        const val installedModelId = "local.installedModelId"
        const val promptShown = "local.promptShown"
        const val wifiOnly = "local.wifiOnly"
    }

    /** The model this device would fetch, or null for LOW. */
    fun target(): ModelCatalog.Model? = ModelCatalog.modelFor(tier)

    /**
     * Whether cellular downloads are allowed. A SECONDARY consent, separate from
     * the "enable on-device AI" one, because 1.1 GB of someone's data plan is a
     * different decision from "yes, use a local model" and conflating them
     * produces either an unnecessary Wi-Fi-only app or an unexpected bill.
     */
    var wifiOnlyConsent by mutableStateOf(true); private set

    fun setWifiOnlyConsent(v: Boolean) {
        wifiOnlyConsent = v
        prefs?.edit()?.putBoolean(K.wifiOnly, v)?.apply()
    }

    /**
     * True when a download is permitted right now.
     *
     * Three separate refusals, and each one names itself in the reason so the UI
     * can say something specific instead of a generic failure.
     */
    fun mayDownload(): Result {
        val m = target() ?: return Result.Refused("this device does not run a local model")
        if (consent != Consent.GRANTED) {
            return Result.Refused("on-device AI has not been enabled")
        }
        if (isCellular() && !wifiOnlyConsent) {
            return Result.Refused("on a mobile data connection; enable 'download on mobile data' first")
        }
        if (m.sha256.isBlank()) {
            return Result.Refused("${m.displayName} has no verified checksum yet")
        }
        return Result.Allowed(m)
    }

    /**
     * Record the user's answer.
     *
     * Granting consent does NOT start a download. The two are separate calls so
     * that "yes" cannot accidentally mean "start fetching 1.1 GB right now" --
     * the UI asks about size and connectivity, and only then calls
     * [beginDownload].
     */
    fun setConsent(value: Consent) {
        consent = value
        prefs?.edit()?.putString(K.consent, value.name)?.apply()
        prefs?.edit()?.putBoolean(K.promptShown, true)?.apply()
        Log.i(TAG, "on-device AI consent: $value")
    }

    /** True once the dialog has been shown, whatever the answer. */
    fun promptHasBeenShown(): Boolean =
        runCatching { prefs?.getBoolean(K.promptShown, false) == true }.getOrDefault(false)

    /** True when the dialog should appear: never asked, and there is a model. */
    fun shouldPrompt(): Boolean = consent == Consent.UNDECIDED && !promptHasBeenShown() && target() != null

    /**
     * Unmetered network check.
     *
     * `NET_CAPABILITY_NOT_METERED` rather than a WIFI transport test: a
     * transport test treats a metered hotspot as Wi-Fi and would let 1.1 GB go
     * out over someone's phone data while the app believes it is on Wi-Fi.
     */
    fun isCellular(): Boolean {
        val ctx = appContext ?: return false
        return runCatching {
            val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
            val n = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(n) ?: return false
            val unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            !unmetered
        }.getOrDefault(false)
    }

    /**
     * Re-read what is on disk.
     *
     * The file, not the preference, decides. A restored backup can carry a
     * preference pointing at a file that is not there, and an OS cleanup can
     * remove a model the preference still claims exists. Both are ordinary
     * events, and both are caught by stat-ing the file.
     */
    fun refreshInstalled() {
        val ctx = appContext
        val recorded = runCatching { prefs?.getString(K.installedPath, null) }.getOrNull()
        val f = recorded?.let { File(it) }
        if (f != null && f.isFile && f.length() > 0) {
            installedPath = f.absolutePath
            installedModelId = runCatching { prefs?.getString(K.installedModelId, null) }.getOrNull()
            // The path exists, so the engine can be pointed at it. Cheap after
            // the first call, and a no-op if it is already loaded.
            GsNativeLoader.initWith(f.absolutePath)
            return
        }
        // Either nothing recorded, or recorded-and-missing. Look for a model on
        // disk that we recognise, so a cleared preference does not force a
        // second 500 MB download.
        val found = ctx?.let { c ->
            val dir = File(c.filesDir, "models")
            dir.listFiles { _, name -> name.endsWith(".gguf") }?.firstOrNull()
        }
        if (found != null) {
            installedPath = found.absolutePath
            installedModelId = found.nameWithoutExtension
            prefs?.edit()?.putString(K.installedPath, found.absolutePath)?.apply()
            prefs?.edit()?.putString(K.installedModelId, found.nameWithoutExtension)?.apply()
            GsNativeLoader.initWith(found.absolutePath)
        } else {
            installedPath = null
            installedModelId = null
        }
    }

    fun recordInstalled(file: File, modelId: String) {
        installedPath = file.absolutePath
        installedModelId = modelId
        prefs?.edit()?.putString(K.installedPath, file.absolutePath)?.apply()
        prefs?.edit()?.putString(K.installedModelId, modelId)?.apply()
        GsNativeLoader.initWith(file.absolutePath)
    }

    /** Remove the model and forget it. Consent is untouched. */
    fun removeInstalled(): Boolean {
        val p = installedPath ?: return false
        val ok = File(p).delete()
        File(File(p).parentFile, File(p).nameWithoutExtension + ".gguf.part").delete()
        installedPath = null
        installedModelId = null
        prefs?.edit()?.remove(K.installedPath)?.remove(K.installedModelId)?.apply()
        GsNativeLoader.release()
        return ok
    }

    sealed class Result {
        data class Allowed(val model: ModelCatalog.Model) : Result()
        data class Refused(val reason: String) : Result()
    }
}
