package com.grapsee.gsai

import com.grapsee.gsai.data.local.ModelCatalog
import android.os.Environment
import java.io.File
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.grapsee.gsai.native.GsNative
import com.grapsee.gsai.native.GsNativeException
import com.grapsee.gsai.native.GsNativeLoader
import com.grapsee.gsai.ocr.MlKitOcr
import org.junit.Assert.assertFalse
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
            // 2. anything already pushed. ctx.filesDir FIRST, and the reason is
            //    measured rather than guessed: run-instrumented.sh places the model
            //    with `run-as` because the adb shell user cannot create the app's
            //    external directory
            //        mkdir: '/sdcard/Android/data/com.grapsee.gsai': Permission denied
            //    and a file that lands there is unreachable by the app anyway
            //    (EACCES reading /sdcard, run 36420986162). The path that works is
            //    the private one:
            //        /data/user/0/com.grapsee.gsai/files/qwen2.5-...gguf
            //    This file still searched only /sdcard and the downloads dir, so
            //    requireModel() assumed and three tests skipped while a perfectly
            //    good model sat one directory away.
            for (dir in listOf(
                ctx.filesDir,
                ctx.getExternalFilesDir(null),
                java.io.File("/sdcard"),
                java.io.File("/storage/emulated/0"),
                ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            )) {
                if (dir == null) continue
                // The filename comes from ModelCatalog.MODEL_0_5B.id, NOT a literal.
        // It was a hardcoded 'qwen2.5-0.5b-instruct-q4_k_m.gguf' in all
        // three test files, which meant the default quantisation could be
        // changed in the catalogue and NOBODY WOULD NOTICE: the tests would
        // keep loading the old file and the switch would be cosmetic. The
        // default is now Q4_0 and these three all read it from one place.
                val f = File(dir, "${ModelCatalog.MODEL_0_5B.id}.gguf")
                if (f.isFile && f.length() > 0) {
                    println("GsNativeTest: found model at ${f.absolutePath} (${f.length()} bytes)")
                    return f.absolutePath
                }
            }
            return null
        }

    /**
     * Ordinary English words, for a LIVENESS check on generated text.
     *
     * "Not empty" cannot tell a working model from one that answers everything with
     * a space, and a liveness guard that can pass on a dead engine is worse than no
     * guard: it makes the real assertion underneath it meaningless.
     */
    private val COMMON_WORDS =
        listOf("the", "is", "a", "i", "you", "hello", "answer", "and", "to", "of", "it", "in")

    private fun requireModel(): String {
        val p = deviceModel
        assumeTrue(
            "no model on the device; run-instrumented.sh places it at files/ in the app private dir via run-as",
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
        // There USED to be an assertNotNull here and its import is now gone,
        // because it was VACUOUS: buildInfo() is declared `: String`, so the
        // compiler already guarantees it, and a check that cannot fail inside a
        // test about export is decoration. What the test's name claims is that
        // the library EXPORTS build info, and an exported string that is blank is
        // not build info.
        assertTrue(
            "buildInfo was empty, so the exported string carries no information",
            info.isNotBlank(),
        )
        // Not pinned to a literal -- the string changes when backends change, and a
        // test that pins it fails for a reason unrelated to correctness -- but it
        // IS pinned to STRUCTURE, which is the part that is a promise: the name and
        // a dotted version, because a caller that prints this into a bug report has
        // to be able to read a version out of it.
        assertTrue(
            "buildInfo does not name the library: \"$info\"",
            info.contains("gs-ffi"),
        )
        assertTrue(
            "buildInfo carries no version number: \"$info\"",
            Regex("""\d+\.\d+""").containsMatchIn(info),
        )
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
        // NOT `assumeTrue(GsNativeLoader.isLibraryLoaded())`, WHICH IS WHAT WAS
        // HERE, AND WHICH IS A FALSE PASS OF EXACTLY THE KIND THIS SUITE EXISTS
        // TO AVOID.
        //
        // Run 36971941114: the .so failed to dlopen --
        //
        //     java.lang.UnsatisfiedLinkError: dlopen failed: library
        //     "libc++_shared.so" not found
        //
        // and these two tests were reported as IGNORED, not failed. 28 tests, 22
        // failures, **2 skipped**. The two tests whose entire subject is the
        // native library quietly stepped aside at the moment the native library
        // was broken.
        //
        // `assumeTrue(isLibraryLoaded())` says: if the thing I am testing is
        // absent, do not test it. For a test about the LIBRARY that is the
        // assumption, not a precondition -- the failure of the library IS the
        // finding, and it must be red. The 21 failures in DeviceVerificationTest
        // are the only reason this run was noticed as red at all.
        //
        // A skip is a green that tested nothing, and a skip whose TRIGGER is the
        // failure being tested for is worse than no test: it looks like coverage.
        assertTrue(
            "libgs_ffi.so did not load, so this test has nothing to say. The reason " +
                "is in the logcat and the run's own summary. Reporting it as a " +
                "SKIP would be the one outcome this test must never produce: it is " +
                "a test about the native library, and the library is what broke.",
            GsNativeLoader.isLibraryLoaded(),
        )
        requireModel()

        // A SUCCESS returns text.
        val ok = GsNative.chat("hello")

        // THIS HALF IS A LIVENESS GUARD, not the assertion. The test is about
        // failure raising; this proves the engine is alive so that the throw below
        // is evidence about an empty prompt rather than about a dead engine.
        //
        // Which means "not empty" is the wrong bar for it: a build that answered
        // every prompt with a single space would satisfy isNotBlank() and make the
        // throw below meaningless. It asserts ordinary English instead -- cheap
        // here, and deliberately weaker than
        // a1_chat_returns_real_text in DeviceVerificationTest.kt, which is the
        // thorough version and does not need duplicating.
        val words = ok.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }.toSet()
        val hits = COMMON_WORDS.filter { words.contains(it) }
        assertTrue(
            "the engine answered \"hello\" with text containing no ordinary " +
                "English word, so it is not alive and the throw below would prove " +
                "nothing. Reply: $ok",
            hits.isNotEmpty(),
        )
        assertFalse(
            "chat returned an unavailability marker: $ok",
            ok.contains("unavailable", ignoreCase = true),
        )

        // A FAILURE throws, and never returns an empty string.
        //
        // This test used to assert that chat("hello") THROWS, which was true only
        // while the engine was broken -- a test that can only pass when the
        // product does not work. Raw, run 36598477726, with the engine working:
        //
        //     assumption failed: failureIsAnExceptionNotAnEmptyString
        //
        // because chat("hello") answered and the test had nothing to complain
        // about. The invariant worth keeping is the other half: an input the
        // engine cannot serve must RAISE, so a caller can tell failure from a
        // short answer.
        val empty = try {
            GsNative.chat("")
            false
        } catch (t: GsNativeException) {
            println("GsNativeTest: empty prompt correctly threw: ${t.message}")
            true
        }
        assertTrue(
            "an empty prompt returned normally; a caller cannot distinguish a " +
                "failure from a short answer",
            empty,
        )
    }

    /**
     * OCR reads the generated fixture, through the engine that exists.
     *
     * This test used to call `GsNative.runOcr` on a file in the app's EXTERNAL
     * directory, and it could only ever skip. Two independent impossibilities
     * were stacked:
     *
     *   - the fixture is placed in the app's PRIVATE dir with run-as, because
     *     the adb shell user cannot create the external one
     *         mkdir: '/sdcard/Android/data/com.grapsee.gsai': Permission denied
     *     and the app cannot read /sdcard (EACCES, run 36420986162)
     *   - `GsNative.runOcr` has no OCR engine compiled into this build, and says
     *     so on every single call
     *         GsNative.runOcr: unavailable: no OCR engine is compiled into this
     *         mobile build; run OCR through the provider path or use a
     *         gs-ocr-enabled build
     *
     * The OCR that ships is ML Kit, bundled in the app module, so that is what
     * this calls. Raw, run 36598477726:
     *     org.junit.AssumptionViolatedException: no fixture image at
     *       /storage/emulated/0/Android/data/com.grapsee.gsai/files/Pictures/invoice.png
     */
    @Test
    fun ocrReturnsTextOnAFixtureImage() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val img = java.io.File(ctx.filesDir, "invoice.png")
        assertTrue("fixture image missing at ${img.absolutePath}", img.isFile)
        val text = MlKitOcr.recognize(ctx, img.absolutePath)
        println("GsNativeTest: ocr -> ${text.replace("\n", " | ")}")
        assertTrue("OCR returned an empty string", text.isNotBlank())
        assertFalse("OCR returned an unavailability marker: $text",
            text.contains("unavailable", ignoreCase = true))
        assertTrue("the marker INV-4471 was not read from the fixture: $text",
            text.contains("INV-4471"))
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
    /**
     * A GGUF that is not a GGUF must be REFUSED, and the refusal must be survivable.
     *
     * "Survivable" is the half that matters and the half nobody tests. The engine is
     * app-scoped: a corrupt-model test that leaves the process without a context
     * fails every LATER test too, which is precisely how run 36981908316 turned one
     * root cause into 17 failures. So this asserts the full round trip --
     *
     *   corrupt path -> refused -> the good path still works afterwards
     *
     * -- and the last step is a real answer from the engine, not a boolean. A
     * recovery that reports `isAvailable() == true` while the context is unusable
     * passes a weaker version of this test.
     *
     * The garbage is not random: it starts with the real GGUF magic so the loader
     * gets past any magic check and fails on the CONTENT, which is the harder and
     * more realistic case.
     */
    @Test
    fun aCorruptGgufIsRefusedAndTheEngineRecoversAfterwards() {
        assumeTrue(
            "no 0.5B GGUF provisioned, so there is no good path to recover to",
            deviceModel != null,
        )
        val good = File(deviceModel!!)

        val junk = File.createTempFile("gs-not-a-gguf", ".gguf", good.parentFile)
        try {
            junk.writeBytes("GGUF".toByteArray() + ByteArray(8192) { (it * 31 % 251).toByte() })
            println("GsNativeTest: corrupt model at ${junk.absolutePath} " +
                "(${junk.length()} bytes)")

            val accepted = try {
                GsNativeLoader.initWith(junk.absolutePath)
            } catch (t: GsNativeException) {
                // NOT a failure on its own: initWith is documented to absorb engine
                // exceptions, because its job is to RECOVER a context. Recording the
                // throw and asserting on the state is what tells the two apart.
                println("GsNativeTest: initWith(garbage) threw, as the contract allows: " +
                    "${t.message}")
                null
            }
            assertFalse(
                "initWith accepted a ${junk.length()}-byte file that is not a model. " +
                    "A loader that reports success here hands every caller a context " +
                    "that cannot decode.",
                accepted == true,
            )

            // THE RECOVERY HALF. This is the step that would have caught 36981908316.
            val recovered = GsNativeLoader.initWith(good.absolutePath)
            assertTrue(
                "after refusing the corrupt model, initWith could not restore the " +
                    "real one. The engine is now dead for every later test in this " +
                    "process. isAvailable=${GsNativeLoader.isAvailable()}",
                recovered,
            )
            val answer = GsNative.chat("What is the capital of France? Answer with one word.")
            println("GsNativeTest: engine after recovery -> ${answer.take(60)}")
            assertTrue(
                "the engine reports available but produced no answer after recovery. " +
                    "A context that exists is not the same as one that works. Got: " +
                    "'${answer.take(120)}'",
                answer.isNotBlank(),
            )
        } finally {
            junk.delete()
            // Belt and braces: if an assertion above threw, the next test still finds
            // a live engine rather than inheriting a corpse.
            runCatching { GsNativeLoader.initWith(good.absolutePath) }
        }
    }

    /**
     * A prompt in a language the tokenizer was not primarily trained on must still
     * produce an answer, and the answer must not be the canned fallback.
     *
     * This is not a translation-quality test and does not pretend to be. The 0.5B
     * model will answer a non-English prompt badly, and that is acceptable. What
     * must not happen is a CRASH, an empty string, or a silent fall-through to
     * `localReply()` -- because all three look identical to a caller that only
     * checks `isNotBlank()`.
     *
     * Three scripts, because a single one proves nothing about byte handling:
     * Japanese (3-byte UTF-8, no ASCII case mapping), Arabic (RTL, and a language
     * direction change), and French with accents (Latin-1 range, where a byte-wise
     * truncation would split a multi-byte character).
     */
    @Test
    fun aNonEnglishPromptIsAnsweredWithoutFallingBack() {
        assumeTrue("no 0.5B GGUF provisioned", deviceModel != null)
        assertTrue("GsNativeLoader.initWith failed", GsNativeLoader.initWith(deviceModel!!))

        val prompts = listOf(
            "日本の首都はどこですか。一語で答えてください。" to "Japanese",
            "ما هي عاصمة فرنسا؟ أجب بكلمة واحدة." to "Arabic",
            "Quelle est la capitale de la France ? Réponds en un seul mot." to "French",
        )
        for ((prompt, label) in prompts) {
            val answer = try {
                GsNative.chat(prompt)
            } catch (t: GsNativeException) {
                // A throw is a FAILURE here, unlike the corrupt-model case. The engine
                // loaded and the tokenizer is present, so a non-ASCII prompt has no
                // excuse for raising.
                throw AssertionError(
                    "the $label prompt raised ${t.javaClass.simpleName}: ${t.message}. " +
                        "A non-ASCII prompt must not be able to fail an engine whose " +
                        "tokenizer is loaded.",
                )
            }
            println("GsNativeTest: $label (${prompt.length} chars) -> " +
                answer.take(80).replace("\n", " "))
            assertTrue(
                "the $label prompt returned an empty string. A caller checking only " +
                    "isNotBlank() cannot tell that from a refusal. Prompt was " +
                    "${prompt.length} characters.",
                answer.isNotBlank(),
            )
        }
    }

    /**
     * A prompt longer than the context must be HANDLED, not merely survived.
     *
     * "Handled" is deliberately vague in the product and specific here. The
     * acceptable behaviours are: raise, or truncate to the context and answer. The
     * unacceptable one is a silent hang, an abort, or an empty string, because all
     * three present to the user as an app that has stopped working.
     *
     * 20000 characters is chosen to exceed the 0.5B model's 32k-token context with
     * room to spare, while staying cheap enough to run on an emulator -- the point
     * is to cross the boundary, not to test the limit.
     */
    @Test
    fun anOverlongPromptIsHandledRatherThanHangingOrEmptying() {
        assumeTrue("no 0.5B GGUF provisioned", deviceModel != null)
        assertTrue("GsNativeLoader.initWith failed", GsNativeLoader.initWith(deviceModel!!))

        // Repeated prose, so the tokenizer sees real tokens rather than one enormous
        // unknown word.
        val filler = "The quick brown fox jumps over the lazy dog. "
        val prompt = buildString {
            append("Summarise in one sentence. ")
            while (length < 20_000) append(filler)
        }
        println("GsNativeTest: overlong prompt is ${prompt.length} characters")

        val outcome = try {
            val a = GsNative.chat(prompt)
            if (a.isBlank()) "EMPTY" else "ANSWERED(${a.length} chars)"
        } catch (t: GsNativeException) {
            "RAISED(${t.message?.take(80)})"
        } catch (t: OutOfMemoryError) {
            "OOM"
        }
        println("GsNativeTest: overlong prompt outcome -> $outcome")

        // RAISED is ACCEPTED, and the message must say why -- an unexplained throw
        // on a long prompt is indistinguishable from a crash to whoever reads the
        // log. What is not accepted is EMPTY, OOM, or a hang (the timeout below).
        assertTrue(
            "an overlong prompt returned an empty string. That is the worst outcome: " +
                "it is indistinguishable from a refusal to a caller that checks only " +
                "isNotBlank(). Outcome was $outcome",
            outcome != "EMPTY" && outcome != "OOM",
        )
    }

    /**
     * OCR on a file that is not an image must RAISE.
     *
     * The invariant is the same one `failureIsAnExceptionNotAnEmptyString` states
     * for chat, applied to the other engine: a refusal must be an exception, never
     * an empty string, because `MlKitOcr` returning "" is indistinguishable from
     * "the image had no text in it" to every caller.
     *
     * Two different wrong inputs, because they fail at different layers and a
     * decoder that handles one often mishandles the other: a text file with an
     * image extension (the loader is fooled, the decoder is not), and random bytes
     * (nothing recognises it at all).
     */
    @Test
    fun ocrOnSomethingThatIsNotAnImageRaisesRatherThanReturningEmpty() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = ctx.filesDir

        val asPng = File(dir, "gs-test-not-an-image.png")
        val asBytes = File(dir, "gs-test-not-an-image.bin")
        asPng.writeText("this is a text file pretending to be a png\n")
        asBytes.writeBytes(ByteArray(4096) { (it * 17 % 253).toByte() })

        for (f in listOf(asPng to "text with a .png extension", asBytes to "random bytes")) {
            val (file, label) = f
            var raised = false
            var returned = ""
            try {
                // recognize(context, imagePath) -- the Context is REQUIRED. It was
                // written as recognize(path) here first, which is a compile error and
                // a 70-minute run to discover; the sibling call at the ocrReturnsText
                // test has the same two-argument shape.
                returned = MlKitOcr.recognize(ctx, file.absolutePath)
            } catch (t: Exception) {
                raised = true
                println("GsNativeTest: ocr($label) raised ${t.javaClass.simpleName}: " +
                    "${t.message}")
            } finally {
                file.delete()
            }
            println("GsNativeTest: ocr($label) -> raised=$raised returned='${returned.take(40)}'")

            assertTrue(
                "OCR on $label returned '${returned.take(60)}' instead of raising. An " +
                    "empty string here is read as \"the image had no text\", which is " +
                    "a different bug with a different fix.",
                raised || returned.isNotBlank(),
            )
        }
    }

    @Test
    fun shutdownIsIdempotent() {
        // NOT `assumeTrue(GsNativeLoader.isLibraryLoaded())`, WHICH IS WHAT WAS
        // HERE, AND WHICH IS A FALSE PASS OF EXACTLY THE KIND THIS SUITE EXISTS
        // TO AVOID.
        //
        // Run 36971941114: the .so failed to dlopen --
        //
        //     java.lang.UnsatisfiedLinkError: dlopen failed: library
        //     "libc++_shared.so" not found
        //
        // and these two tests were reported as IGNORED, not failed. 28 tests, 22
        // failures, **2 skipped**. The two tests whose entire subject is the
        // native library quietly stepped aside at the moment the native library
        // was broken.
        //
        // `assumeTrue(isLibraryLoaded())` says: if the thing I am testing is
        // absent, do not test it. For a test about the LIBRARY that is the
        // assumption, not a precondition -- the failure of the library IS the
        // finding, and it must be red. The 21 failures in DeviceVerificationTest
        // are the only reason this run was noticed as red at all.
        //
        // A skip is a green that tested nothing, and a skip whose TRIGGER is the
        // failure being tested for is worse than no test: it looks like coverage.
        assertTrue(
            "libgs_ffi.so did not load, so this test has nothing to say. The reason " +
                "is in the logcat and the run's own summary. Reporting it as a " +
                "SKIP would be the one outcome this test must never produce: it is " +
                "a test about the native library, and the library is what broke.",
            GsNativeLoader.isLibraryLoaded(),
        )
        GsNativeLoader.release()
        GsNativeLoader.release()   // must not throw when nothing was loaded
        val state = GsNativeLoader.state()
        assertTrue(
            "state after a double release was ${state.name}, expected LOADED or UNAVAILABLE",
            state == GsNativeLoader.State.LOADED || state == GsNativeLoader.State.UNAVAILABLE,
        )
    }
}
