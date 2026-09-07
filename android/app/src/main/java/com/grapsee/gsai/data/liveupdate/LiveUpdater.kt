package com.grapsee.gsai.data.liveupdate

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import androidx.core.content.FileProvider
import com.grapsee.gsai.BuildConfig
import com.grapsee.gsai.data.remote.GsApiJson
import com.grapsee.gsai.di.ServiceLocator
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.cancel
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

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
    data class Available(
        val versionName: String,
        val versionCode: Int,
        val apkUrl: String,
        val notes: String?
    ) : LiveUpdateState()
    data class Downloading(val percent: Int) : LiveUpdateState()
    data object Ready : LiveUpdateState()
    data class Failed(val reason: String) : LiveUpdateState()
}

/**
 * GS LiveUpdate — the app's self-update engine ("Kotlin live changing" mode).
 *
 * Every time the Home canvas appears, [syncFrom] quietly fetches a version
 * manifest from GitHub. When a newer versionCode is published, a subtle
 * LiveUpdate pill surfaces on the canvas: one tap downloads the new APK from
 * the same GitHub URL and fires the system installer.
 *
 * Download hardening (the part that makes it feel native):
 *  - **Byte-range resume.** A dropped connection resumes from the exact byte
 *    it broke at (the GitHub CDN advertises `accept-ranges`), so a flaky
 *    network can never reset the pill back to 1%. Resume survives process
 *    death too — the `.part` file persists in cache.
 *  - **Single-flight.** Exactly one download coroutine can ever exist; taps
 *    during a download are absorbed instead of spawning a second writer into
 *    the same file.
 *  - **Integrity gate.** Nothing reaches the system installer until the file
 *    (a) matches the APK ZIP magic, (b) parses under the real PackageManager,
 *    (c) carries our own packageName and the exact expected versionCode, and
 *    (d) covers the full advertised length. A truncated or corrupt APK can
 *    never again surface as "problem parsing the package".
 *  - **Honest failure.** One automatic resume-retry, then the pill states the
 *    truth ("Update didn't finish · tap to retry") and every further tap
 *    resumes from where the bytes stopped.
 *
 * Signature stability is what makes the install step work: the APK is signed
 * with the committed `gs-live.keystore`, so every future build installs
 * straight over the previous one without an uninstall.
 */
object LiveUpdater {

    private const val MANIFEST_URL =
        "https://raw.githubusercontent.com/GrapseeAgency/the-gs-ai-app/main/download/update-manifest.json"
    private const val CHECK_THROTTLE_MS = 10 * 60_000L
    private const val AUTO_RETRY_DELAY_MS = 2_500L

    /** Every installable Android package starts with the ZIP local-file magic. */
    private val APK_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    private var appContext: Context? = null
    private var lastCheckMs = 0L
    private var downloadedFile: File? = null
    private var lastAvailable: LiveUpdateState.Available? = null

    private val _state = MutableStateFlow<LiveUpdateState>(LiveUpdateState.Idle)
    val state: StateFlow<LiveUpdateState> = _state.asStateFlow()

    /** Guards the download path: exactly one download coroutine can ever run. */
    private val inFlight = AtomicBoolean(false)

    /** Dedicated client — long request timeout for a 20MB body over slow links,
     *  plus a per-read socket timeout so a stalled stream errors into a resume
     *  instead of hanging the pill forever. */
    private val downloadClient: HttpClient by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 600_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Attach app context + quiet manifest check. Throttled; silent on any failure. */
    fun syncFrom(context: Context, force: Boolean = false) {
        val ctx = context.applicationContext
        appContext = ctx
        if (!force && System.currentTimeMillis() - lastCheckMs < CHECK_THROTTLE_MS) return
        val current = _state.value
        if (current !is LiveUpdateState.Idle && current !is LiveUpdateState.Failed) return
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
                val available = LiveUpdateState.Available(
                    versionName = manifest.versionName,
                    versionCode = manifest.versionCode,
                    apkUrl = manifest.apkUrl,
                    notes = manifest.notes
                )
                lastAvailable = available
                _state.value = available
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Quiet by design: offline / GitHub away → the pill simply never shows.
        }
    }

    /** One tap on the LiveUpdate pill: serve cached → resume download → installer. */
    fun beginInstallFlow() {
        val ctx = appContext ?: return
        when (val current = _state.value) {
            is LiveUpdateState.Available -> serveOrDownload(ctx, current)
            is LiveUpdateState.Failed -> serveOrDownload(ctx, lastAvailable ?: return)
            is LiveUpdateState.Ready -> fireInstaller(ctx)
            else -> Unit
        }
    }

    private fun serveOrDownload(ctx: Context, update: LiveUpdateState.Available) {
        val dir = File(ctx.cacheDir, "liveupdate").apply { mkdirs() }
        val target = File(dir, "gs-ai-update.apk")
        if (target.exists()) {
            val verified = inspectArchive(ctx, target)
            if (verified != null &&
                verified.first == ctx.packageName &&
                verified.second == update.versionCode
            ) {
                downloadedFile = target
                _state.value = LiveUpdateState.Ready
                fireInstaller(ctx)
                return
            }
            // Stale or corrupt leftover from an older attempt — never install it.
            target.delete()
        }
        if (!inFlight.compareAndSet(false, true)) return
        _state.value = LiveUpdateState.Downloading(0)
        scope.launch {
            try {
                runDownload(ctx, update, autoRetry = true)
            } finally {
                inFlight.set(false)
            }
        }
    }

    /**
     * Streaming download with byte-range resume, a hard truncation gate and a
     * package-integrity gate before anything is handed to the installer.
     * On a transient failure it keeps the `.part` file and auto-resumes once;
     * after that it surfaces [LiveUpdateState.Failed] and every further tap
     * continues from the exact byte where the stream broke.
     */
    private suspend fun runDownload(
        ctx: Context,
        update: LiveUpdateState.Available,
        autoRetry: Boolean
    ) {
        val dir = File(ctx.cacheDir, "liveupdate").apply { mkdirs() }
        val partial = File(dir, "gs-ai-update.part")
        try {
            val resumeFrom = if (partial.exists()) partial.length() else 0L
            var total = -1L
            var done = 0L
            var append = false
            var restartClean = false
            var lastPercent = -1

            downloadClient.prepareGet(update.apkUrl) {
                if (resumeFrom > 0) headers { append(HttpHeaders.Range, "bytes=$resumeFrom-") }
            }.execute { response ->
                when {
                    resumeFrom > 0 && response.status == HttpStatusCode.PartialContent -> {
                        append = true
                        total = response.headers["Content-Range"]
                            ?.substringAfterLast('/')?.toLongOrNull() ?: -1L
                    }
                    response.status == HttpStatusCode.RequestedRangeNotSatisfiable -> {
                        // The partial already covers the whole body — drop it and fetch clean.
                        response.bodyAsChannel().cancel()
                        partial.delete()
                        restartClean = true
                    }
                    response.status.isSuccess() -> {
                        append = false
                        total = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
                    }
                    else -> throw IllegalStateException("GitHub returned HTTP ${response.status.value}")
                }
                if (!restartClean) {
                    done = if (append) resumeFrom else 0L
                    val channel = response.bodyAsChannel()
                    FileOutputStream(partial, append).use { out ->
                        while (!channel.isClosedForRead) {
                            val packet = channel.readRemaining(65536)
                            while (!packet.isEmpty) {
                                val bytes = packet.readBytes()
                                out.write(bytes)
                                done += bytes.size
                                if (total > 0) {
                                    val pct = ((done * 100) / total).toInt().coerceIn(1, 99)
                                    if (pct != lastPercent) {
                                        lastPercent = pct
                                        _state.value = LiveUpdateState.Downloading(pct)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (restartClean) return runDownload(ctx, update, autoRetry)

            // Truncation gate: a stream that dropped cleanly must never pose as a
            // complete APK. (When the length is genuinely unknown, the package
            // parse below still catches every truncation — ZIP directories sit
            // at the end of the file.)
            if (total > 0 && done != total) {
                throw IllegalStateException("Download interrupted at $done of $total bytes")
            }
            if (done <= 0L) throw IllegalStateException("Empty download")

            // Integrity gate: ZIP magic → real PackageManager parse → identity match.
            val verified = inspectArchive(ctx, partial) ?: run {
                partial.delete()
                throw IllegalStateException("Downloaded file is not a valid Android package")
            }
            if (verified.first != ctx.packageName || verified.second != update.versionCode) {
                partial.delete()
                throw IllegalStateException(
                    "Downloaded package is ${verified.first} v${verified.second}, expected " +
                        "${ctx.packageName} v${update.versionCode}"
                )
            }

            val target = File(dir, "gs-ai-update.apk")
            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) {
                throw IllegalStateException("Could not finalize the update package")
            }
            downloadedFile = target
            _state.value = LiveUpdateState.Ready
            fireInstaller(ctx)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (autoRetry) {
                // Keep the .part file — the retry resumes, it does not restart.
                delay(AUTO_RETRY_DELAY_MS)
                runDownload(ctx, update, autoRetry = false)
            } else {
                _state.value = LiveUpdateState.Failed(
                    reason = e.message ?: "Update failed"
                )
            }
        }
    }

    /**
     * Parse-check a downloaded file BEFORE the system installer ever sees it:
     * ZIP magic bytes first, then a real PackageManager parse. Returns
     * (packageName, versionCode) or null when the file is not a loadable APK.
     */
    @Suppress("DEPRECATION")
    private fun inspectArchive(ctx: Context, file: File): Pair<String, Int>? {
        runCatching {
            FileInputStream(file).use { input ->
                val magic = ByteArray(4)
                var read = 0
                while (read < 4) {
                    val n = input.read(magic, read, 4 - read)
                    if (n < 0) break
                    read += n
                }
                if (read < 4 || !magic.contentEquals(APK_MAGIC)) error("not an APK")
            }
        }.onFailure { return null }
        val info: PackageInfo? = ctx.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        val name = info?.packageName ?: return null
        return name to info.versionCode
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
            // Installer refused (e.g. "install unknown apps" off for GS AI) — say so.
            _state.value = LiveUpdateState.Failed(
                reason = e.message ?: "Installer unavailable"
            )
        }
    }
}
