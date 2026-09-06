package com.grapsee.gsai.data.liveupdate

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.grapsee.gsai.BuildConfig
import com.grapsee.gsai.data.remote.GsApiJson
import com.grapsee.gsai.di.ServiceLocator
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File

/** The installable version manifest published next to the APK on GitHub. */
@Serializable
data class UpdateManifestDto(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val notes: String? = null
)

sealed class LiveUpdateState {
    data object Idle : LiveUpdateState()
    data class Available(val versionName: String, val apkUrl: String, val notes: String?) : LiveUpdateState()
    data class Downloading(val percent: Int) : LiveUpdateState()
    data object Ready : LiveUpdateState()
}

/**
 * GS LiveUpdate — the app's self-update engine ("Kotlin live changing" mode).
 *
 * Every time the Home canvas appears, [syncFrom] quietly fetches a version
 * manifest from GitHub. When a newer versionCode is published, a subtle
 * LiveUpdate pill surfaces on the canvas: one tap downloads the new APK from
 * the same GitHub URL and fires the system installer. No dialogs, no errors —
 * if GitHub or the network is away, nothing happens at all.
 *
 * Signature stability is what makes this work: the APK is signed with the
 * committed `gs-live.keystore`, so every future build installs straight over
 * the previous one without an uninstall.
 */
object LiveUpdater {

    private const val MANIFEST_URL =
        "https://raw.githubusercontent.com/GrapseeAgency/the-gs-ai-app/main/download/update-manifest.json"
    private const val CHECK_THROTTLE_MS = 10 * 60_000L

    private var appContext: Context? = null
    private var lastCheckMs = 0L
    private var downloadedFile: File? = null

    private val _state = MutableStateFlow<LiveUpdateState>(LiveUpdateState.Idle)
    val state: StateFlow<LiveUpdateState> = _state.asStateFlow()

    /** Dedicated client — long request timeout for a 20MB body over slow links. */
    private val downloadClient: HttpClient by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 600_000
                connectTimeoutMillis = 10_000
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Attach app context + quiet manifest check. Throttled; silent on any failure. */
    fun syncFrom(context: Context, force: Boolean = false) {
        val ctx = context.applicationContext
        appContext = ctx
        if (!force && System.currentTimeMillis() - lastCheckMs < CHECK_THROTTLE_MS) return
        if (_state.value != LiveUpdateState.Idle) return
        lastCheckMs = System.currentTimeMillis()
        scope.launch { check() }
    }

    private suspend fun check() {
        try {
            val response = ServiceLocator.http.get(MANIFEST_URL)
            if (!response.status.isSuccess()) return
            val manifest = runCatching {
                GsApiJson.decodeFromString<UpdateManifestDto>(response.bodyAsText())
            }.getOrNull() ?: return
            if (manifest.versionCode > BuildConfig.VERSION_CODE && manifest.apkUrl.isNotBlank()) {
                _state.value = LiveUpdateState.Available(
                    versionName = manifest.versionName,
                    apkUrl = manifest.apkUrl,
                    notes = manifest.notes
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Quiet by design: offline / GitHub away → the pill simply never shows.
        }
    }

    /** One tap on the LiveUpdate pill: download → ready → system installer. */
    fun beginInstallFlow() {
        val ctx = appContext ?: return
        when (val current = _state.value) {
            is LiveUpdateState.Available ->
                if (downloadedFile?.exists() == true) {
                    _state.value = LiveUpdateState.Ready
                    fireInstaller(ctx)
                } else {
                    scope.launch { download(ctx, current.apkUrl) }
                }
            is LiveUpdateState.Ready -> fireInstaller(ctx)
            else -> Unit
        }
    }

    private suspend fun download(ctx: Context, apkUrl: String) {
        try {
            val dir = File(ctx.cacheDir, "liveupdate").apply { mkdirs() }
            val target = File(dir, "gs-ai-update.apk")
            val partial = File(dir, "gs-ai-update.part")
            partial.delete()

            var total = -1L
            var done = 0L
            downloadClient.prepareGet(apkUrl).execute { response ->
                if (!response.status.isSuccess()) throw IllegalStateException("update fetch ${response.status.value}")
                total = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
                val channel = response.bodyAsChannel()
                partial.outputStream().use { out ->
                    while (!channel.isClosedForRead) {
                        val packet = channel.readRemaining(65536)
                        while (!packet.isEmpty) {
                            val bytes = packet.readBytes()
                            out.write(bytes)
                            done += bytes.size
                            if (total > 0) {
                                _state.value = LiveUpdateState.Downloading(
                                    ((done * 100) / total).toInt().coerceIn(1, 99)
                                )
                            }
                        }
                    }
                }
            }

            if (done > 0 && partial.renameTo(target)) {
                downloadedFile = target
                _state.value = LiveUpdateState.Ready
                fireInstaller(ctx)
            } else {
                partial.delete()
                _state.value = LiveUpdateState.Idle
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Quiet failure: the pill resets away; the app keeps working as-is.
            downloadedFile = null
            _state.value = LiveUpdateState.Idle
        }
    }

    private fun fireInstaller(ctx: Context) {
        val file = downloadedFile ?: return
        try {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (e: Exception) {
            // Installer refused (e.g. "install unknown apps" off for GS AI) — reset quietly.
            _state.value = LiveUpdateState.Idle
        }
    }
}
