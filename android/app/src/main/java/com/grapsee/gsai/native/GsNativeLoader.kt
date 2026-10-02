package com.grapsee.gsai.native

import android.util.Log

/**
 * Loads `libgs_ffi.so` once and reports whether it is usable.
 *
 * The load is deferred rather than done in a static initialiser. A static
 * initialiser that throws takes the whole process down with it, so a phone with
 * an unexpected ABI would fail to launch rather than fall back to the network —
 * which is the exact opposite of what this bridge is for.
 *
 * Callers check [isAvailable] and fall through to the existing responder when it
 * is false. Nothing here ever throws at a caller.
 */
object GsNativeLoader {

    private const val TAG = "GsNativeLoader"
    private const val LIB = "gs_ffi"

    /** A model path handed to [GsNative.init] before anything loaded it. */
    @Volatile
    private var pendingModelPath: String? = null

    @Volatile
    private var state: State = State.NOT_TRIED

    enum class State {
        /** Nobody has asked yet. */
        NOT_TRIED,

        /** libgs_ffi.so is loaded and the JNI entry points resolved. */
        LOADED,

        /**
         * The library is absent or a symbol is missing. This build has no native
         * engine, which is a supported configuration: the portable library still
         * links and every backend-dependent call reports UNAVAILABLE with a
         * reason.
         */
        UNAVAILABLE
    }

    /**
     * True when the native engine is present and a context has been created.
     *
     * Deliberately the *whole* condition, not just "the .so loaded": a caller
     * asking this is about to make a decision about where a reply comes from,
     * and a loaded library with no context cannot answer.
     */
    @JvmStatic
    // A PREDICATE THAT THROWS IS A BUG, and this one did.
    //
    // `ensureLoaded() && GsNative.backendAvailable()` -- and backendAvailable()
    // raises GsNativeException("no native context; call init(modelPath) first")
    // rather than returning false. So the one function whose entire job is to
    // answer "is there a context?" threw instead of answering, in precisely the
    // situation where the answer is "no".
    //
    // android-device 36981908316 is a demonstration: every test that asked this
    // question after the context was torn down received an exception, and 15
    // tests failed with that exception as their reported reason rather than with
    // anything about the product.
    //
    // runCatching here, and pendingModelPath is NOT consulted: it can be stale,
    // and initWith's own fast path proved that a stale value is worse than none.
    fun isAvailable(): Boolean =
        runCatching { ensureLoaded() && GsNative.backendAvailable() }
            .getOrDefault(false)

    /** True when the .so is loaded, whether or not a generation backend exists. */
    @JvmStatic
    fun isLibraryLoaded(): Boolean = ensureLoaded()

    /** The state, for a diagnostics screen. */
    @JvmStatic
    fun state(): State = state.also { ensureLoaded() }

    /**
     * Load the model if a path is known. Safe to call repeatedly; only the first
     * call with a new path does anything.
     *
     * @return true when a context exists afterwards.
     */
    @JvmStatic
    fun initWith(modelPath: String): Boolean {
        if (!ensureLoaded()) return false
        synchronized(this) {
            // THE WHOLE BODY IS INSIDE THE TRY. It was not, and this function --
            // whose job is to RECOVER a context -- threw an exception in exactly
            // the state recovery is for.
            //
            // android-device 36981908316, 29 tests, 17 failures:
            //
            //   if (pendingModelPath == modelPath && GsNative.backendAvailable())
            //       return true
            //   return try { GsNative.init(modelPath) ... } catch (t) { false }
            //
            // `pendingModelPath` is cleared by release() but NOT by a direct
            // GsNative.shutdown(). So after one of those, pendingModelPath still
            // matches, the short-circuit does NOT save it, and backendAvailable()
            // is evaluated -- and it throws
            //     GsNativeException: GsNative.backendAvailable: no native context
            // at a point that is OUTSIDE the catch. The exception escaped
            // initWith, into whichever test called it, which failed there, and
            // the context was never restored. Every later test needing the engine
            // then failed the same way: 15 tests, one root cause, reported as 15.
            //
            // The consequence in the product is the same and needs no test suite:
            // if the context dies for any reason and the recorded path is stale,
            // the user gets an exception from the function whose contract is
            // "@return true when a context exists afterwards".
            //
            // Clearing pendingModelPath on failure is the other half: without it
            // a FAILED init leaves a stale path that makes the NEXT call take the
            // branch that throws.
            return try {
                if (pendingModelPath == modelPath && GsNative.backendAvailable()) {
                    true
                } else {
                    val ok = GsNative.init(modelPath)
                    pendingModelPath = if (ok) modelPath else null
                    ok
                }
            } catch (t: Throwable) {
                // A GsNativeException here is information, not a crash: it
                // carries the reason the engine declined the model.
                pendingModelPath = null
                Log.w(TAG, "native init declined $modelPath", t)
                false
            }
        }
    }

    /**
     * The model this process has loaded, or null. Used by the consent flow to
     * tell "downloaded but not opened" from "in use".
     */
    @JvmStatic
    fun loadedModelPath(): String? = pendingModelPath

    /** Release the context. The library stays loaded; re-init is cheap. */
    @JvmStatic
    fun release() {
        if (state != State.LOADED) return
        synchronized(this) {
            try {
                GsNative.shutdown()
            } catch (t: Throwable) {
                Log.w(TAG, "native shutdown failed", t)
            } finally {
                pendingModelPath = null
            }
        }
    }

    /**
     * Load the library at most once, for the life of the process.
     *
     * Idempotent and safe from any thread. A failure is cached: retrying a
     * missing .so on every keystroke would throw UnsatisfiedLinkError per call
     * and bury the real reason in log noise.
     */
    private fun ensureLoaded(): Boolean {
        if (state != State.NOT_TRIED) return state == State.LOADED
        synchronized(this) {
            if (state != State.NOT_TRIED) return state == State.LOADED
            state = try {
                System.loadLibrary(LIB)
                // A library that loads but whose symbols do not resolve is the
                // same failure from the caller's point of view, and the check
                // costs one call.
                GsNative.buildInfo()
                Log.i(TAG, "native engine loaded: ${GsNative.buildInfo()}")
                State.LOADED
            } catch (t: Throwable) {
                Log.w(TAG, "no native engine in this build; the app stays fully usable", t)
                State.UNAVAILABLE
            }
        }
        return state == State.LOADED
    }
}
