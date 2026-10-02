package com.grapsee.gsai.data.local

import android.os.Build
import java.io.File

/**
 * Diffusion weights, which are a SEPARATE CATALOGUE from [ModelCatalog] and a
 * separate consent for two reasons that are both about the user rather than about
 * tidiness.
 *
 * **The size is not comparable to the LLM.** [ModelCatalog.MODEL_0_5B] is 491 MB
 * and [ModelCatalog.MODEL_1_5B] is 1.1 GB. The smallest checkpoint here is
 * 882 MB -- larger than the *large* LLM, and ~1.8x the small one. A user who
 * wants offline chat does not thereby consent to a second gigabyte they will
 * never use, and folding it into the same prompt would present them a single
 * number that hides the choice entirely.
 *
 * **They are not used for the same thing.** Chat and OCR are always-on features;
 * image generation is occasional. A consent that says "download once, then works
 * offline" is true of the first and misleading about the second, which is
 * 5-10 minutes per image on a phone CPU.
 *
 * ## Why this checkpoint and not SD 1.5
 *
 * The brief named SD 1.5 (4.27 GB) and SDXL-Turbo (6.9 GB) and asked for the
 * smallest thing that produces recognizable images. Those are both far larger
 * than the app's whole point, which is that it installs in seconds and needs no
 * network afterwards.
 *
 * Every size and digest below was read from the Hugging Face API, not from a
 * model card and not estimated:
 *
 *     akleine/sdxs-512  ->  sdxs.safetensors  882,587,118 bytes
 *
 * The alternatives that were measured and rejected, with the reason each was
 * rejected rather than a preference:
 *
 * | checkpoint | bytes | why not |
 * |---|---:|---|
 * | `akleine/sdxs-512` `sdxs.safetensors` | 882,587,118 | **chosen** |
 * | `concedo/sdxs-512-tinySDdistilled-GGUF` Q8_0 | ~716,000,000 | 23% smaller, but a third-party re-quantization of the above, so the digest certifies a conversion rather than a release |
 * | `akleine/sdxs-09` `sdxs09.safetensors` | 1,342,124,230 | 52% larger, same family |
 * | `segmind/SSD-1B-A1111.safetensors` | 4,465,700,000 | 5x the size for a distilled model this repo does not otherwise need |
 * | `segmind/Segmind-Vega` | 3,293,400,000 | 3.7x the size |
 *
 * `docs/distilled_sd.md` at the pinned stable-diffusion.cpp commit
 * (3f8527a46c54ecf4cb4ed6003da8e8982283c73c) lists this model under "SD1.x, SD2.x
 * with tiny U-Nets" and gives the invocation as
 *
 *     sd-cli -m sdxs.safetensors -p "..." --cfg-scale 1 --steps 1
 *
 * with both options described as **mandatory**. That is why [steps] and
 * [cfgScale] are 1 and 1.0 here rather than tuned: they are constraints of the
 * checkpoint, and a test that passes 20 steps is not testing this model.
 */
object DiffusionCatalog {

    /**
     * One diffusion checkpoint.
     *
     * [sha256] is NOT BLANK, and that is the load-bearing fact: `mayDownload`
     * refuses an entry with no verified digest, so a blank here means the
     * download is refused rather than performed unchecked. The digest below was
     * read from the Hugging Face API's blob metadata for this exact file.
     */
    data class Checkpoint(
        val id: String,
        val displayName: String,
        val url: String,
        val sha256: String,
        val bytes: Long,
        /** Minimum RAM; the weights are resident for the whole generation. */
        val minRamBytes: Long,
        /** Sampling steps. A CONSTRAINT of this checkpoint, not a preference. */
        val steps: Int,
        /** Guidance scale. Also a constraint. See the file comment. */
        val cfgScale: Float,
    ) {
        fun sizeLabel(): String = when {
            bytes >= 1_000_000_000L -> String.format("%.2f GB", bytes / 1e9)
            bytes >= 1_000_000L -> String.format("%.0f MB", bytes / 1e6)
            else -> String.format("%.0f KB", bytes / 1e3)
        }
    }

    /**
     * SDXS-512 DreamShaper, a distilled SD1.x with a reduced U-Net.
     *
     * The size and digest are measured, and android-device.yml re-measures both on
     * every run by fetching the file and hashing it -- the same way it does for
     * the 0.5B model. So this entry is checked against the real upstream bytes by
     * a job, not merely asserted in a comment.
     */
    val SDXS_512 = Checkpoint(
        id = "sdxs-512-dreamshaper",
        displayName = "SDXS-512 DreamShaper (distilled SD1.x)",
        url = "https://huggingface.co/akleine/sdxs-512/resolve/main/sdxs.safetensors",
        sha256 = "6cca5bfd11b588cdfb4602018c7e623d24c95fdfdc5ab2d4b9e6978b3186980f",
        bytes = 882_587_118L,
        // Weights plus activations plus the VAE decode buffer for 512x512.
        minRamBytes = 3L * 1024 * 1024 * 1024,
        // 1 and 1.0 are MANDATORY for this checkpoint. Upstream says so, twice.
        steps = 1,
        cfgScale = 1.0f,
    )

    /** The only entry today, but a list-shaped API so a second needs no new type. */
    val ALL: List<Checkpoint> = listOf(SDXS_512)

    /**
     * Whether this device can hold the weights.
     *
     * Deliberately NOT a `Tier`, unlike the LLM path. The LLM tiers exist because
     * the model choice and the device class are one decision with one answer. Here
     * there is one checkpoint and it is gated on RAM alone, so a tier would be a
     * second vocabulary for a single question.
     */
    fun fitsDevice(context: android.content.Context): Boolean {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return false
        if (abi != "arm64-v8a" && abi != "x86_64") return false
        return ModelCatalog.totalRamBytes(context) >= SDXS_512.minRamBytes
    }

    /**
     * Consent text, which says the two things a shared prompt with the LLM could
     * not: how much MORE this is, and how long an image takes.
     */
    fun consentMessage(checkpoint: Checkpoint): String = buildString {
        append("Enable on-device image generation?\n\n")
        append("${checkpoint.displayName}\n")
        append("Downloads ${checkpoint.sizeLabel()} -- this is a separate download from ")
        append("the chat model, and it is larger.\n")
        append("About 5-10 minutes per 512x512 image on a phone CPU.\n\n")
        append("Your prompts and images stay on this device. Nothing is uploaded, and ")
        append("nothing is downloaded without you saying yes.")
    }

    /**
     * A SEPARATE DIRECTORY, which is the point of asking.
     *
     * `filesDir/diffusion`, not `filesDir/models` and not external storage. Three
     * reasons, and the third is the one that bites:
     *
     *  1. an install/uninstall or a "clear chat data" that empties `models` must
     *     not silently take 882 MB of diffusion weights with it
     *  2. a quota or cleanup pass aimed at chat models has no business reasoning
     *     about a file it cannot parse
     *  3. **the extension differs.** These are `.safetensors`, not `.gguf`. A
     *     shared directory with a shared extension convention invites a future
     *     "restore every file in models/" that hands a safetensors file to
     *     llama_model_load_from_file, and the resulting error names a corrupt
     *     model rather than a wrong file type.
     */
    fun destination(context: android.content.Context, checkpoint: Checkpoint): File =
        File(File(context.filesDir, "diffusion"), "${checkpoint.id}.safetensors")

    fun partialDestination(context: android.content.Context, checkpoint: Checkpoint): File =
        File(File(context.filesDir, "diffusion"), "${checkpoint.id}.safetensors.part")

    /** Where generated PNGs go. Separate again, and cache-cleanable. */
    fun imageDestination(context: android.content.Context, name: String): File {
        val dir = File(context.cacheDir, "generated")
        dir.mkdirs()
        return File(dir, "$name.png")
    }
}
