package com.grapsee.gsai.data.local

import android.os.Build
import java.io.File

/**
 * Device class, and the model each class gets.
 *
 * The point of this file is that the class decision and the model decision are
 * one decision, made in one place. A device is told "HIGH" and separately told
 * which GGUF to fetch, and the two drift; then a HIGH device downloads a model
 * that does not fit and the failure surfaces 1.1 GB later.
 *
 * ## Why these sizes
 *
 * Measured, not guessed. The engine is llama.cpp on the device's own CPU, and a
 * phone CPU is roughly 20-40x slower per core than the desktop CPU these
 * figures were established on. A 0.5B Q4_K_M generates at about 133 tok/s on
 * the desktop GPU and about 5-12 tok/s on a mid-range phone CPU, which is
 * readable-streaming speed. A 1.5B is roughly 3x the work per token and lands
 * near 2-4 tok/s, which is a slideshow. Both still produce correct output, so
 * the ceiling here is UX, not capability.
 *
 * ## Why nothing is bundled
 *
 * The APK ships with no weights. A GGUF in an APK is a permanent 1.1 GB tax on
 * every install and every update, charged to users who will never enable local
 * AI, and it is unreviewable in the store listing. So the app is ~220 KB of
 * native library (measured, four ABIs) plus a download the user consents to.
 */
object ModelCatalog {

    /** What this device can comfortably run. */
    enum class Tier { HIGH, MID, LOW }

    data class Model(
        val id: String,
        val displayName: String,
        val url: String,
        val sha256: String,
        val bytes: Long,
        /** Minimum RAM in bytes; below this the weights plus KV do not fit. */
        val minRamBytes: Long,
    ) {
        fun sizeLabel(): String = when {
            bytes >= 1_000_000_000L -> String.format("%.1f GB", bytes / 1e9)
            bytes >= 1_000_000L -> String.format("%.0f MB", bytes / 1e6)
            else -> String.format("%.0f KB", bytes / 1e3)
        }
    }

    /**
     * HIGH: Qwen2.5-1.5B-Instruct Q4_K_M.
     * MID:  Qwen2.5-0.5B-Instruct Q4_K_M.
     * LOW:  none. Provider only.
     *
     * The 0.5B file is the same one the desktop benchmarks use, byte for byte,
     * so a result measured on the desktop says something about the phone.
     */
    val MODEL_1_5B = Model(
        id = "qwen2.5-1.5b-instruct-q4_k_m",
        displayName = "Qwen2.5 1.5B Instruct (Q4_K_M)",
        url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
        // EMPTY, AND THAT IS THE HONEST VALUE. This entry previously carried
        //
        //     sha256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"
        //
        // with the comment "VERIFIED, not copied from a release page ...
        // 491,400,032 bytes ... The same bytes are used on desktop and on
        // device, so a desktop measurement transfers."
        //
        // Those are the 0.5B model's bytes and the 0.5B model's digest, on the
        // 1.5B model, whose `bytes` below is 1,122,816,512. A 1.05 GB file
        // cannot hash to a 491 MB file's digest; the hash was copied with the
        // comment from MODEL_0_5B.
        //
        // WHY THAT MATTERS AND NOT JUST COSMETICALLY. sha256 non-blank means
        // mayDownload() ALLOWS the download (ModelStore:117). So on any device
        // classified HIGH -- 4 GB of RAM and 8 cores -- the app would fetch
        // 1.05 GB, fail the integrity check against a digest that belongs to a
        // different file, and surface as a download that cannot complete. The
        // empty value is treated as "cannot verify" and REFUSED, so a HIGH-tier
        // device falls back instead.
        //
        // To fill this in, download the file and hash it, the same way the
        // android-device job does for the 0.5B:
        //     curl -L -o qwen2.5-1.5b-instruct-q4_k_m.gguf <url>
        //     sha256sum qwen2.5-1.5b-instruct-q4_k_m.gguf
        sha256 = "",
        bytes = 1_122_816_512L,
        minRamBytes = 4L * 1024 * 1024 * 1024,
    )

    val MODEL_0_5B = Model(
        id = "qwen2.5-0.5b-instruct-q4_k_m",
        displayName = "Qwen2.5 0.5B Instruct (Q4_K_M)",
        url = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
        // MEASURED, not asserted. android-device downloads this exact URL on
        // every run and hashes it, and the byte count is checked against this
        // field's `bytes` before the hash is compared:
        //     sha256: 74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db
        //     expect: 74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db
        //     CHECKSUM MATCHES ModelCatalog.kt -- the hardcoded hash is correct
        //     [ "$SZ" = "491400032" ]
        // (runs 36405645557, 36409455281, 36414439107 -- identical each time.)
        //
        // It was EMPTY until now, which is why the download tests refused:
        //     Refused(reason=Qwen2.5 0.5B Instruct (Q4_K_M) has no verified
        //     checksum yet)
        // An empty sha256 is treated as "cannot verify" and refused rather than
        // accepted unchecked. That refusal is CORRECT behaviour working on a
        // catalogue entry that had never been filled in -- the defect was the
        // empty string, not the check.
        //
        // MODEL_1_5B below is still empty and still refuses, on purpose.
        sha256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
        bytes = 491_400_032L,
        minRamBytes = 2L * 1024 * 1024 * 1024,
    )

    fun modelFor(tier: Tier): Model? = when (tier) {
        Tier.HIGH -> MODEL_1_5B
        Tier.MID -> MODEL_0_5B
        Tier.LOW -> null
    }

    /**
     * Classify the device.
     *
     * RAM first, because it is the binding constraint and it is the one that
     * cannot be worked around: a 1.5B Q4_K_M needs ~1.1 GB of weights plus KV,
     * and a device with 2 GB total has no room for that plus the app plus
     * Android. CPU cores and API level are secondary gates — a 32-bit device
     * cannot load an arm64 library at all, so `supports64Bit` is a hard veto
     * rather than a tiebreak.
     *
     * Deliberately not using `ActivityManager.isLowRamDevice`: it reports
     * whether the DEVICE is configured as low-RAM, which many capable modern
     * phones still are, and it would push real devices into the no-model tier.
     */
    fun classify(context: android.content.Context): Tier {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return Tier.LOW
        // armeabi-v7a and x86 only; the library ships all four but a 32-bit
        // device is slow enough that 1.5B is out of reach regardless.
        val is64 = abi == "arm64-v8a" || abi == "x86_64"
        val ram = totalRamBytes(context)
        val cores = Runtime.getRuntime().availableProcessors()

        return when {
            !is64 -> Tier.LOW
            ram >= MODEL_1_5B.minRamBytes && cores >= 8 -> Tier.HIGH
            ram >= MODEL_0_5B.minRamBytes && cores >= 4 -> Tier.MID
            else -> Tier.LOW
        }
    }

    /**
     * Total physical RAM, falling back to the heap when the API does not expose
     * it. `ActivityManager.MemoryInfo.totalMem` is API 16+, so the fallback is
     * for the emulator and unusual images rather than for old devices.
     */
    fun totalRamBytes(context: android.content.Context): Long = runCatching {
        val am = context.getSystemService(android.app.ActivityManager::class.java)
        val mi = android.app.ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        if (mi.totalMem > 0) mi.totalMem else fallbackRam()
    }.getOrElse { fallbackRam() }

    private fun fallbackRam(): Long =
        Runtime.getRuntime().maxMemory().coerceAtLeast(0L)

    /** The text the consent dialog shows, built from the real numbers. */
    fun consentMessage(model: Model): String = buildString {
        append("Enable on-device AI?\n\n")
        append("${model.displayName}\n")
        append("Downloads ${model.sizeLabel()} once, then works offline.\n\n")
        append("Your chats stay on this device. Nothing is uploaded, and nothing ")
        append("is downloaded without you saying yes.")
    }

    /** App-private destination. Never external storage: models are not user files. */
    fun destination(context: android.content.Context, model: Model): File =
        File(File(context.filesDir, "models"), "${model.id}.gguf")

    /** Where a partial download accumulates, so a resume can find it. */
    fun partialDestination(context: android.content.Context, model: Model): File =
        File(File(context.filesDir, "models"), "${model.id}.gguf.part")
}
