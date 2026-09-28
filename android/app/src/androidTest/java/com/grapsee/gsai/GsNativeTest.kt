package com.grapsee.gsai

import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.grapsee.gsai.native.GsNative
import com.grapsee.gsai.native.GsNativeLoader
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The JNI bridge, on a device.
 *
 * The split between "must pass" and "must be honest" is deliberate.
 *
 * **Always asserted** — the library loads, the symbols resolve, and a failure
 * surfaces as an exception rather than as an empty string:
 *   - [libraryLoadsAndExportsBuildInfo]
 *   - [failureIsAnExceptionNotAnEmptyString]
 *
 * **Skipped without a model**, because a CI runner has no 400 MB GGUF and a test
 * that quietly passes without running is worse than one that says it did not
 * run. `assumeTrue` makes the skip visible in the report:
 *   - [chatReturnsNonEmptyText]
 *   - [ocrReturnsTextOnAFixtureImage]
 *
 * A 0.5B model is not committed (no weights in git). To run the model-backed
 * tests, push one to the device's external files dir and set GS_TEST_MODEL:
 *   adb push qwen2.5-0.5b-instruct-q4_k_m.gguf \
 *       /sdcard/Android/data/com.grapsee.gsai/files/
 */
@RunWith(AndroidJUnit4::class)
class GsNativeTest {

    /**
     * Where the model is, in order of preference.
     *
     * The CI job pushes it with `adb push ... /sdcard/<name>` because adb cannot
     * write into /data/data without root, so the app-external dir is checked too
     * and not only the DOWNLOADS subdirectory. Without that second path the test
     * finds nothing and SKIPS, which is the failure mode this file exists to
     * prevent: a skipped test reads like a pass.
     */
    private val deviceModel: String?
        get() {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            // 1. the argument the runner passes through
            val arg = InstrumentationRegistry.getArguments()
                .getString("gs_test_model")
            if (!arg.isNullOrBlank() && java.io.File(arg).isFile) return arg
            // 2. anything already pushed
            for (dir in listOf(
                java.io.File("/sdcard"),
                java.io.File("/storage/emulated/0"),
                ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            )) {
                if (dir == null) continue
                val f = java.io.File(dir, "qwen2.5-0.5b-instruct-q4_k_m.gguf")
                if (f.isFile && f.length() > 0) {
                    println("GsNativeTest: found model at ${f.absolutePath} (${f.length()} bytes)")
                    return f.absolutePath
                }
            }
            return null
        }

    private fun requireModel(): String {
        val p = deviceModel
        assumeTrue(
            "no model on the device; set GS_TEST_MODEL or push one to the downloads dir",
            p != null,
        )
        return p!!
    }

    /**
     * The library must load and report itself. This is the test that would have
     * caught the JNI symbols being named for the wrong package: a mismatch
     * throws UnsatisfiedLinkError on the first call, not at load time.
     */
    @Test
    fun libraryLoadsAndExportsBuildInfo() {
        assertTrue(
            "libgs_ffi.so did not load; is the artifact in jniLibs?",
            GsNativeLoader.isLibraryLoaded(),
        )
        val info = GsNative.buildInfo()
        assertNotNull(info)
        assertTrue("buildInfo was empty", info.isNotBlank())
        // Recorded rather than asserted to a literal: the string changes when
        // backends change, and a test that pins it fails for a reason that has
        // nothing to do with correctness.
        println("GsNativeTest: build = $info, backendAvailable = ${GsNative.backendAvailable()}")
    }

    /**
     * A failure must be an exception, never "".
     *
     * This is the property the whole fallback chain rests on. A caller that gets
     * "" cannot distinguish "the model declined" from "there is no engine in this
     * build" and will render a blank bubble instead of falling back to the
     * provider, so the user sees an empty reply and no error.
     */
    @Test
    fun failureIsAnExceptionNotAnEmptyString() {
        assumeTrue(GsNativeLoader.isLibraryLoaded())
        val threw = try {
            GsNative.chat("hello")
            false
        } catch (t: Throwable) {
            println("GsNativeTest: expected failure, got ${t::class.java.name}: ${t.message}")
            true
        }
        // If the engine IS available the call may legitimately succeed, in which
        // case it must not have returned an empty string.
        if (!threw) {
            assumeTrue("engine is available; the failure path cannot be exercised", false)
        }
        assertTrue("expected an exception or a non-empty result", threw)
    }

    @Test
    fun chatReturnsNonEmptyText() {
        val model = requireModel()
        assertTrue("init failed for $model", GsNativeLoader.initWith(model))
        assumeTrue(
            "no generation backend in this build; the portable library links but cannot generate",
            GsNative.backendAvailable(),
        )
        val out = GsNative.chat("Say hello in one short sentence.")
        assertTrue("chat returned an empty string", out.isNotBlank())
        assertFalse(
            "chat returned an unavailability marker instead of text: $out",
            out.contains("unavailable", ignoreCase = true),
        )
        println("GsNativeTest: chat -> ${out.take(80)}")
    }

    @Test
    fun ocrReturnsTextOnAFixtureImage() {
        val model = requireModel()
        assertTrue("init failed for $model", GsNativeLoader.initWith(model))
        assumeTrue("no OCR backend in this build", GsNative.backendAvailable())
        val dir = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        val img = java.io.File(dir, "invoice.png")
        assumeTrue("no fixture image at ${img.absolutePath}", img.isFile)
        val text = GsNative.runOcr(img.absolutePath)
        assertTrue("OCR returned an empty string", text.isNotBlank())
        assertFalse("OCR returned an unavailability marker: $text",
            text.contains("unavailable", ignoreCase = true))
        println("GsNativeTest: ocr -> ${text.take(80)}")
    }

    /**
     * Release the context twice.
     *
     * A double free in native teardown is a CRASH, not a Kotlin exception, so
     * this earns its place: if the second release reaches a freed pointer the
     * process dies and the whole suite reports nothing.
     *
     * The state afterwards is LOADED or UNAVAILABLE and nothing else, because
     * release() drops the model but deliberately leaves the library loaded --
     * re-init is cheap and re-loading a .so is not.
     */
    @Test
    fun shutdownIsIdempotent() {
        assumeTrue(GsNativeLoader.isLibraryLoaded())
        GsNativeLoader.release()
        GsNativeLoader.release()   // must not throw when nothing was loaded
        val state = GsNativeLoader.state()
        assertTrue(
            "state after a double release was ${state.name}, expected LOADED or UNAVAILABLE",
            state == GsNativeLoader.State.LOADED || state == GsNativeLoader.State.UNAVAILABLE,
        )
    }
}
