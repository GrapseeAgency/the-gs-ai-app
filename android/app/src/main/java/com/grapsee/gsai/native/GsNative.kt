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

    /** 1 when a generation backend is compiled into this build. */
    external fun backendAvailable(): Boolean

    /** Build identification, for matching a crash report to a commit. */
    external fun buildInfo(): String

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
}
