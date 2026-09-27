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
    fun isAvailable(): Boolean = ensureLoaded() && GsNative.backendAvailable()

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
            if (pendingModelPath == modelPath && GsNative.backendAvailable()) return true
            return try {
                val ok = GsNative.init(modelPath)
                if (ok) pendingModelPath = modelPath
                ok
            } catch (t: Throwable) {
                // A GsNativeException here is information, not a crash: it
                // carries the reason the engine declined the model.
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
