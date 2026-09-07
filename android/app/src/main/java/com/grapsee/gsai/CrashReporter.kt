package com.grapsee.gsai

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.time.OffsetDateTime

/**
 * The last line of defence: an uncaught-exception handler that captures the
 * real stack trace on the device before the system dialog appears. Every
 * init in [GSApplication.onCreate] is individually guarded, but if anything
 * anywhere still throws — a corrupted preference, an OEM quirk, a subsystem
 * that misbehaves on one specific device — the trace lands in
 * files/crash-reports/last-crash.txt, and the Settings screen offers a
 * "Send crash report" row only while that file exists. Diagnosis closes the
 * loop with facts, not guesses.
 */
object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val DIR = "crash-reports"
    private const val FILE = "last-crash.txt"

    fun install(context: Context, versionName: String, versionCode: Int) {
        runCatching {
            val appContext = context.applicationContext
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching {
                    val dir = File(appContext.filesDir, DIR)
                    dir.mkdirs()
                    val device =
                        "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
                    File(dir, FILE).writeText(
                        buildString {
                            appendLine("GS AI App crash — ${OffsetDateTime.now()}")
                            appendLine("App: $versionName (build $versionCode)")
                            appendLine("Device: $device")
                            appendLine("Thread: ${thread.name}")
                            appendLine()
                            appendLine(Log.getStackTraceString(throwable))
                            if (throwable.cause != null) {
                                appendLine("CAUSE:")
                                appendLine(Log.getStackTraceString(throwable.cause))
                            }
                        }
                    )
                }
                // The system's own handler still runs — crash dialog and
                // process teardown stay exactly as the platform intends.
                previous?.uncaughtException(thread, throwable)
            }
        }.onFailure { Log.e(TAG, "handler install failed", it) }
    }

    /** The captured trace, or null when the last launch was clean. */
    fun lastReport(context: Context): String? {
        val file = File(File(context.filesDir, DIR), FILE)
        return if (file.isFile) runCatching { file.readText() }.getOrNull() else null
    }

    /** Called once the report leaves the device — the offer disappears. */
    fun clear(context: Context) {
        runCatching { File(File(context.filesDir, DIR), FILE).delete() }
    }
}
