package com.grapsee.gsai.native

/**
 * The native engine, as a Kotlin surface. This class is declarations only — no
 * bodies, no state, no logic. Everything it exposes is implemented in Rust
 * (`native/crates/gs-ffi/src/jni.rs`) and reached through
 * `com.grapsee.gsai.native.GsNativeLoader`.
 *
 * Three rules the Rust side enforces and this file relies on:
 *
 *  1. **Failure is an exception, never an empty string.** A caller that gets `""`
 *     cannot tell "the model declined to answer" from "there is no engine in this
 *     build", and will render a blank bubble instead of falling back. Anything
 *     that can fail returns a value or throws.
 *  2. **The context is process-wide.** [init] is cheap after the first call and
 *     the weights stay resident; a phone app is one user.
 *  3. **Nothing here blocks the UI thread.** A generation on a phone takes
 *     seconds, not milliseconds. Call from `Dispatchers.IO`.
 */
object GsNative {

    /**
     * Load the model at [modelPath] into the process-wide context.
     *
     * Returns true when the context exists. It can be true while
     * [backendAvailable] is false: the portable build links cleanly and simply
     * has no generation backend, and reporting that as a load failure would
     * disable the bridge for a library that loaded perfectly well.
     */
    external fun init(modelPath: String): Boolean

    /**
     * [init] with the context size and thread count specified.
     *
     * [init] passes 2048 and 4, hardcoded, and those are the two knobs a
     * time-to-first-token measurement should move first: context size is how much
     * KV cache is allocated and therefore how much memory is touched before the
     * first token, and thread count is the decode parallelism.
     *
     * Named initTuned, NOT initWith, because GsNativeLoader.initWith(modelPath)
     * already exists and means something else -- "load this path". A second
     * initWith taking two extra arguments would read as that function with more
     * parameters, and someone would call it expecting its caching behaviour.
     *
     * A separate name at all so the production entry point's ABI is untouched.
     * The app calls [init]; benchmarks call this.
     */
    /**
     * [init] with the context size, thread count, and the three PERFORMANCE LEVERS
     * that were previously hardcoded in the wrapper and therefore unmeasurable:
     * the prompt-processing batch, the micro-batch, and flash attention.
     *
     * Each of the last three takes 0 for "the value this build already used",
     * which is NOT zero -- a batch of 0 would mean "process nothing". [init] is
     * this call with 0, 0, 0.
     *
     * [nBatch] is the TTFT knob for a long prompt: it is how many prompt tokens go
     * to `llama_decode` at once. [nUbatch] bounds a single graph and must not
     * exceed [nBatch] (the C++ side clamps rather than letting ggml abort).
     */
    external fun initTuned(
        modelPath: String,
        nCtx: Int,
        nThreads: Int,
        nBatch: Int,
        nUbatch: Int,
        flashAttn: Int,
    ): Boolean

    /** 1 when a generation backend is compiled into this build. */
    external fun backendAvailable(): Boolean

    /** Build identification, for matching a crash report to a commit. */
    external fun buildInfo(): String

    /**
     * What the library actually resolved at load time, as a readable string:
     * `context=present backend=available(llama.cpp+vulkan) ...`.
     *
     * Returns exactly `"UNAVAILABLE"` when there is no context at all, so a
     * caller can check for that one specific condition. A `chat` call that
     * returns nothing cannot distinguish "the .so did not load" from "no
     * generation backend" from "no model"; this names which.
     */
    external fun selfCheck(): String

    /**
     * Generate a completion with the default token budget.
     *
     * @throws GsNativeException on any failure, with the native reason attached.
     */
    external fun chat(prompt: String): String

    /**
     * Generate a completion with an explicit token budget.
     *
     * A separate method rather than an overload: JNI mangles by arity, so one
     * name cannot carry both signatures.
     */
    external fun chatWithBudget(prompt: String, maxTokens: Int): String

    /** OCR an image file. Throws rather than returning `""` on failure. */
    external fun runOcr(imagePath: String): String

    /**
     * Embed raw RGB pixels, three bytes per pixel, row-major.
     *
     * The native side checks the array length against `w * h * 3` and throws on a
     * mismatch, rather than reading past the end of the Java heap.
     */
    external fun embedImage(rgb: ByteArray, w: Int, h: Int): FloatArray

    /** Embed a text query. Throws rather than returning an empty vector. */
    external fun embedText(text: String): FloatArray

    /** Release the process-wide context. Safe to call when nothing was loaded. */
    external fun shutdown()

    // -----------------------------------------------------------------------
    // Image generation. IN-PROCESS -- no subprocess anywhere on this path, and
    // there is no sd-cli to find on a phone.
    //
    // TWO ROUTES, NOT ONE, because they have different requirements and
    // conflating them is how "image generation" becomes an untestable claim:
    //
    //   renderSvg   no weights, no GPU, no diffusion. Works in EVERY build.
    //   sdGenerate  real diffusion. Needs the library AND a checkpoint.
    //
    // -----------------------------------------------------------------------

    /**
     * Render an SVG diagram from a flat JSON spec. Needs no weights, no GPU and no
     * diffusion, so it works in every build of the library, including the portable
     * one with no backend at all.
     *
     * Throws [GsNativeException] on failure rather than returning false, per rule 1
     * above.
     */
    external fun renderSvg(specJson: String, outPath: String)

    /**
     * Load a diffusion model and return a handle, or 0 when it could not be loaded.
     *
     * 0 rather than an exception, because a caller ASKED a question by calling
     * this -- "is there a usable model here?" -- and the answer is allowed to be no.
     * [lastError] says why. Every other call in this file throws on failure
     * because a failure there is unexpected; here it is the expected outcome.
     *
     * A handle is a pointer, and it is freed by [sdFree] and by nothing else.
     * Dropping it without calling that leaks a model context -- for diffusion
     * that is gigabytes, not bytes.
     */
    external fun sdCreate(modelPath: String): Long

    /**
     * Whether diffusion can actually run on this handle.
     *
     * False means either the library is not linked or the checkpoint is absent,
     * and [sdBackendName] says which. Those are opposite problems with opposite
     * fixes, so the boolean is never the whole answer.
     */
    external fun sdAvailable(handle: Long): Boolean

    /**
     * Which backend answered: `"sd.cpp"`, or `"sd.cpp:not-compiled"` when the
     * library is absent from this build. Never empty -- a string that says nothing
     * is worse than one that says this.
     */
    external fun sdBackendName(handle: Long): String

    /**
     * Generate one image and WRITE IT AS A PNG to [outPath].
     *
     * Throws [GsNativeException] with the reason on failure, and **writes no file
     * when it fails**. That is not an implementation detail to be careful with: a
     * caller that decodes the PNG cannot tell a real generation from a grey
     * rectangle, and a grey rectangle that always appears is worse than no image
     * at all because it satisfies every check that only asks whether a file
     * appeared.
     */
    external fun sdGenerate(
        handle: Long,
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        outPath: String,
    )

    /**
     * Free a handle from [sdCreate]. Consumes it: calling this twice with the same
     * value is a use-after-free, so null the field after calling.
     */
    external fun sdFree(handle: Long)

    /**
     * The last native error for this thread, or `""` when none is pending.
     *
     * For the calls that deliberately do not throw -- [sdCreate] returning 0, and
     * anything a caller caught and discarded.
     */
    external fun lastError(): String
}
