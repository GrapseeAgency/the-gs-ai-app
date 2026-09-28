package com.grapsee.gsai.data.local

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Downloads a GGUF to app-private storage, with resume, progress and cancel.
 *
 * ## Resume
 *
 * Hugging Face serves `Accept-Ranges: bytes`, so an interrupted download
 * continues from where it stopped with an HTTP Range request instead of starting
 * over. On a 1.1 GB model over a flaky phone connection that is the difference
 * between one retry and four, and the reason this is not a convenience.
 *
 * The partial file is the source of truth for how far we got, not a counter in
 * preferences: a counter and the file can disagree (killed between write and
 * flush), and the file is what actually exists.
 *
 * ## Verification
 *
 * SHA-256 after the bytes land, before the file is promoted out of `.part`.
 * Three rules that are not negotiable:
 *
 *  1. An EMPTY expected hash refuses the download. There is no model this repo
 *     has not fetched and hashed, and accepting an unverified one would make
 *     the integrity check decorative.
 *  2. A mismatch deletes the partial file. Resuming onto corrupt bytes produces
 *     a file that is the right size and wrong, which is the worst outcome.
 *  3. The verified file is renamed into place atomically. A reader can see the
 *     complete model or no model, never a half-written one.
 *
 * ## Network policy
 *
 * The caller must already hold consent. This class does not prompt and does not
 * check consent, because a downloader that can be talked into skipping the check
 * is one refactor away from a policy violation. [ModelStore] owns that.
 */
object ModelDownloader {

    private const val TAG = "ModelDownloader"
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val BUF = 1 shl 16

    sealed class Result {
        data class Complete(val file: File, val resumed: Boolean, val bytes: Long) : Result()
        data class Failed(val reason: String, val recoverable: Boolean) : Result()
    }

    /**
     * @param onProgress receives (bytesSoFar, totalBytes). `totalBytes` is 0
     *   when the server does not send Content-Length, which happens on a
     *   chunked response; the UI shows an indeterminate bar rather than a
     *   fabricated percentage.
     */
    suspend fun download(
        model: ModelCatalog.Model,
        dest: File,
        partial: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Result = withContext(Dispatchers.IO) {
        try {
            if (model.sha256.isBlank()) {
                return@withContext Result.Failed(
                    "no verified SHA-256 for ${model.id}; refusing to download an " +
                        "unverifiable model",
                    recoverable = false,
                )
            }
            dest.parentFile?.mkdirs()

            // Already there and already verified? Do not re-download 500 MB.
            if (dest.isFile && dest.length() == model.bytes) {
                if (sha256(dest) == model.sha256) {
                    return@withContext Result.Complete(dest, resumed = false, bytes = dest.length())
                }
                Log.w(TAG, "${model.id} is present but its hash does not match; re-downloading")
                dest.delete()
            }

            val haveBytes = if (partial.isFile) partial.length() else 0L
            if (haveBytes > model.bytes) {
                // The partial is bigger than the real file, so it is not a
                // prefix of it. Resuming would produce a corrupt result.
                Log.w(TAG, "partial ($haveBytes) is larger than the model (${model.bytes}); restarting")
                partial.delete()
            }

            val conn = open(model.url, haveBytes)
            try {
                val code = conn.responseCode
                // 200 to a Range request means the server ignored the range and
                // is sending the whole file. Restarting is correct; appending
                // would double the file.
                val resuming = haveBytes > 0 && code == HttpURLConnection.HTTP_PARTIAL
                if (haveBytes > 0 && !resuming) {
                    Log.w(TAG, "server replied $code to a Range request; restarting from zero")
                    partial.delete()
                }
                if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                    return@withContext Result.Failed("HTTP $code from ${model.url}", recoverable = true)
                }

                val declared = conn.contentLength.toLong()
                val total = if (declared > 0) {
                    if (resuming) declared + haveBytes else declared
                } else {
                    0L
                }
                onProgress(haveBytes, total)

                var written = if (resuming) haveBytes else 0L
                RandomAccessFile(partial, "rw").use { raf ->
                    raf.setLength(written)      // truncate any junk past haveBytes
                    raf.seek(written)
                    conn.inputStream.use { input ->
                        val buf = ByteArray(BUF)
                        while (true) {
                            // Cancellation is checked every buffer, so a cancel
                            // takes effect within ~64 KB rather than after the
                            // whole model.
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            raf.write(buf, 0, n)
                            written += n
                            onProgress(written, total)
                        }
                    }
                    // Durability before verification: a hash computed over bytes
                    // still in the page cache can pass and then be lost.
                    raf.fd.sync()
                }

                if (partial.length() != model.bytes) {
                    return@withContext Result.Failed(
                        "incomplete: got ${partial.length()} of ${model.bytes} bytes",
                        recoverable = true,
                    )
                }

                val actual = sha256(partial)
                if (!actual.equals(model.sha256, ignoreCase = true)) {
                    partial.delete()
                    return@withContext Result.Failed(
                        "SHA-256 mismatch: expected ${model.sha256}, got $actual. " +
                            "The partial file has been deleted so the next attempt starts clean.",
                        recoverable = true,
                    )
                }

                if (dest.exists() && !dest.delete()) {
                    return@withContext Result.Failed("could not replace the existing model", true)
                }
                if (!partial.renameTo(dest)) {
                    return@withContext Result.Failed("could not move the verified model into place", true)
                }
                Result.Complete(dest, resumed = resuming, bytes = dest.length())
            } finally {
                conn.disconnect()
            }
        } catch (c: CancellationException) {
            // The partial file is deliberately KEPT. That is what makes resume
            // work: cancelling must not throw away progress.
            throw c
        } catch (e: IOException) {
            Result.Failed("network: ${e.message ?: e.javaClass.simpleName}", recoverable = true)
        } catch (t: Throwable) {
            Log.e(TAG, "download failed", t)
            Result.Failed("${t.javaClass.simpleName}: ${t.message}", recoverable = false)
        }
    }

    private fun open(url: String, from: Long): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = CONNECT_TIMEOUT_MS
        c.readTimeout = READ_TIMEOUT_MS
        c.instanceFollowRedirects = true
        if (from > 0) {
            // The whole point of resume. Range is inclusive on both ends.
            c.setRequestProperty("Range", "bytes=$from-")
        }
        return c
    }

    /** Streaming SHA-256. A 1.1 GB file does not fit in a sensible buffer. */
    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(BUF)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Delete a partial download. Exposed so a "cancel" that means "forget it" can. */
    fun discard(partial: File): Boolean = partial.isFile && partial.delete()
}
