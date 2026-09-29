package com.grapsee.gsai.native

/**
 * A failure from the native engine, with the reason the engine gave.
 *
 * This class did not exist, and the JNI bridge has been throwing it by name since
 * the bridge was written:
 *
 *     env.find_class("com/grapsee/gsai/native/GsNativeException")
 *
 * The comment beside that call said the missing class was survivable:
 *
 *     "If the class is missing, the entry points still return their explicit
 *      failure value, so Kotlin never sees a success-shaped null."
 *
 * That is not what happened. The entry points that return a Java `String` return
 * **null** on failure, and a `null` from a function declared `: String` is not an
 * explicit failure value -- it is a null that arrives in Kotlin code expecting a
 * string. Raw, run 36576882672, on the first device run that got as far as
 * calling OCR:
 *
 *     java.lang.ClassNotFoundException: Didn't find class
 *       "com.grapsee.gsai.native.GsNativeException" on path: DexPathList[...]
 *
 * So every native failure so far has been a null reaching a caller, and the
 * reason -- which the native side formats carefully as
 * "GsNative.<what>: <reason>" -- has been discarded every time.
 */
class GsNativeException @JvmOverloads constructor(
    /** The entry point that failed, e.g. "runOcr". */
    val operation: String,
    /** The reason the native engine gave. Never blank when thrown by the bridge. */
    val reason: String,
    cause: Throwable? = null,
) : RuntimeException("GsNative.$operation: $reason", cause)
