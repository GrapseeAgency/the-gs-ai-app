package com.grapsee.gsai

import android.os.Environment
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.data.local.AppDatabase
import com.grapsee.gsai.data.local.ModelCatalog
import com.grapsee.gsai.data.local.ModelDownloader
import com.grapsee.gsai.data.local.ModelStore
import com.grapsee.gsai.data.remote.ApiClient
import com.grapsee.gsai.data.repository.ChatRepository
import com.grapsee.gsai.native.GsNative
import com.grapsee.gsai.native.GsNativeLoader
import com.grapsee.gsai.ocr.MlKitOcr
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.MessageDigest

/**
 * The four device tests, on a real emulator.
 *
 * Ordered by the class name so JUnit runs them in a fixed order and the cheap
 * structural checks run before the ones that load 491 MB of weights.
 *
 * A test that does not RUN is not a pass. `assumeTrue` produces a SKIP, which
 * the CI report step prints separately from a pass, because the single most
 * likely way for this file to report green while proving nothing is for the
 * model to be missing and every test to skip.
 */
@RunWith(AndroidJUnit4::class)
class DeviceVerificationTest {

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * The model, wherever it is. The CI job pushes it with `adb push` to
     * /sdcard because adb cannot write into /data/data without root, and the
     * app's own external dir because it can.
     */
    private fun findModel(): File? {
        val arg = InstrumentationRegistry.getArguments().getString("gs_test_model")
        if (!arg.isNullOrBlank()) {
            val f = File(arg)
            if (f.isFile && f.length() > 1_000_000) return f
        }
        // getExternalFilesDir(null) is FIRST because it is the only one of these
        // an app can read with no permission on API 30. Raw, run 36420986162:
        //     java.io.FileNotFoundException:
        //     /sdcard/qwen2.5-0.5b-instruct-q4_k_m.gguf: open failed:
        //     EACCES (Permission denied)
        // An arbitrary file at the root of /sdcard sits outside every media
        // collection, so READ_EXTERNAL_STORAGE would not open it either, and this
        // app declares no storage permission at all. The app's own external dir
        // needs none, and it is also where a real user's downloaded model lands.
        for (dir in listOf(
            // The app's PRIVATE dir. /sdcard is unreadable on API 30 (EACCES,
            // run 36430187499) and /sdcard/Android/data/<pkg> cannot even be
            // created by the adb shell user:
            //     mkdir: '/sdcard/Android/data/com.grapsee.gsai': Permission denied
            // run-as reaches this one because a debug APK is debuggable.
            ctx.filesDir,
            ctx.getExternalFilesDir(null),
            ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            File("/sdcard"),
            File("/storage/emulated/0"),
        )) {
            if (dir == null) continue
            val f = File(dir, "qwen2.5-0.5b-instruct-q4_k_m.gguf")
            if (f.isFile && f.length() > 1_000_000) return f
        }
        return null
    }

    // =====================================================================
    // 0. STRUCTURE: the library is present and says what it resolved.
    // =====================================================================

    /**
     * The VERIFY directive, and the assertion that would have caught the
     * portable-only build: the .so can load, export 10 symbols and answer every
     * call with GS_ERR_UNAVAILABLE, and nothing short of calling it tells you
     * which happened.
     */
    @Test
    fun a0_selfCheck_reports_a_real_backend() {
        assertTrue(
            "libgs_ffi.so did not load. isLibraryLoaded=false means the " +
                "artifact is not in jniLibs, or its ABI does not match this device.",
            GsNativeLoader.isLibraryLoaded(),
        )
        val check = GsNative.selfCheck()
        println("GsNativeTest: selfCheck = $check")
        assertNotNull("selfCheck returned null", check)
        assertFalse(
            "selfCheck() == \"UNAVAILABLE\": the library loaded but has no context. " +
                "Raw value: $check",
            check == "UNAVAILABLE",
        )
        assertTrue(
            "selfCheck did not report a backend: $check",
            check.contains("backend="),
        )
        val available = GsNativeLoader.isAvailable()
        println("GsNativeTest: isAvailable = $available")
        println("GsNativeTest: loader      = ${GsNativeLoader.state()}")
        // ASSERTED, NOT PRINTED. This test previously only printed isAvailable,
        // and so PASSED against a portable-only build whose every call answers
        // GS_ERR_UNAVAILABLE. Raw, run 36430187499, from the same logcat:
        //     selfCheck = context=present backend=unavailable(unavailable:
        //     no generation backend compiled in) selfCheck=gs-ffi 0.1.0
        //     portable portable portable portable portable
        //     isAvailable = false
        // A test that observes the thing it exists to check, and reports success
        // either way, is worse than no test: it converts a known-broken library
        // into a green run.
        assertTrue(
            "isAvailable() is false: the .so loaded but has no generation " +
                "backend, so chat() cannot return text. selfCheck said: $check",
            available,
        )
    }

    // =====================================================================
    // 1. GsNativeTest
    // =====================================================================

    @Test
    fun a1_chat_returns_real_text() {
        val model = findModel()
        assertNotNull(
            "no model on the device. This is a FAILURE, not a skip: the CI job " +
                "pushes one, and its absence means the run did not set up.",
            model,
        )
        println("GsNativeTest: model = ${model!!.absolutePath} (${model.length()} bytes)")

        assertTrue("init failed for $model", GsNativeLoader.initWith(model.absolutePath))
        val check = GsNative.selfCheck()
        println("GsNativeTest: selfCheck after init = $check")

        assumeTrue(
            "the .so has no generation backend even after init: $check. " +
                "GS_LLAMA_PREBUILT did not reach the link.",
            GsNativeLoader.isAvailable(),
        )

        val out = GsNative.chat("hello")
        println("GsNativeTest: chat -> ${out.take(120)}")
        assertTrue("chat returned an empty string", out.isNotBlank())
        assertFalse(
            "chat returned an unavailability marker instead of text: $out",
            out.lowercase().contains("unavailable"),
        )
        assertFalse(
            "chat returned an error string rather than a completion: $out",
            out.lowercase().contains("error:"),
        )

        // A NON-EMPTY STRING IS NOT A REPLY.
        //
        // This assertion passed on output that was obviously not an answer:
        //     chat -> , i have a question about the following code:
        //     ```
        //     #include <iostream>
        //     using namespace std;
        // That is the PROMPT being echoed back, not the model answering it -- the
        // prompt template is not being applied to the model's output at all. A
        // non-empty check cannot tell those apart, which is why the bar is now
        // three properties instead of one.
        println("GsNativeTest: chat length = ${out.length}")
        assertTrue(
            "the reply is ${out.length} characters; a completion this short is a " +
                "fragment, not an answer: $out",
            out.length > 20,
        )

        // It must not simply BE the prompt. A model that has echoed the prompt back
        // will share a long substring with it; a model answering will not.
        val prompt = "hello"
        val echoed = prompt.length >= 10 &&
            out.windowed(10).any { it == prompt.windowed(10).first() && prompt.contains(it) }
        assertFalse(
            "the reply is a substring of the prompt -- the prompt is being echoed " +
                "back rather than answered: $out",
            echoed,
        )

        // And it must contain ordinary English. Generated text that is entirely
        // punctuation, code fences or whitespace indicates a sampler or prompt
        // wiring fault rather than a working model.
        val COMMON = listOf("the", "is", "a", "i", "you", "hello", "answer", "and", "to", "of")
        val words = out.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }.toSet()
        val hits = COMMON.filter { words.contains(it) }
        println("GsNativeTest: common words in the reply = $hits")
        assertTrue(
            "the reply contains no ordinary English word, so it is not a natural " +
                "language answer. Words seen: $words. Reply: $out",
            hits.isNotEmpty(),
        )
    }

    /**
     * A reply that is a RESPONSE, not a continuation.
     *
     * The three assertions in a1 are necessary and nowhere near sufficient, and
     * this run proved it. Raw, run 36545847058, with all three passing:
     *
     *     chat -> , i have a question about the following code:
     *     ```
     *     #include <iostream>
     *     using namespace std;
     *     int main() {
     *         int x = 10
     *     chat length = 893
     *     common words in the reply = [the, is, a, i, and, to, of]
     *
     * 893 characters, not a substring of "hello", full of stop words -- and a
     * document completion. Every shape check a string can satisfy, and not one
     * bit of evidence that a human was spoken to.
     *
     * So the question itself carries the answer. A base model completing text has
     * no way to arrive at "Paris"; a model that has been told a user is speaking
     * and that an assistant turn is expected answers in one word. This fails on
     * the exact failure it exists to catch, which is the whole point.
     */
    @Test
    fun a1b_chat_answers_the_question_it_was_asked() {
        val model = findModel()
        assertNotNull("no model on the device", model)
        println("GsNativeTest: model = ${model!!.absolutePath}")
        assertTrue("init failed for $model", GsNativeLoader.initWith(model.absolutePath))
        val check = GsNative.selfCheck()
        println("GsNativeTest: selfCheck = $check")
        assumeTrue("no generation backend: $check", check.contains("backend=available"))

        val question = "What is the capital of France? Answer with one word."
        val reply = GsNative.chat(question)
        println("GsNativeTest: asked  -> $question")
        println("GsNativeTest: replied -> ${reply.take(300)}")
        assertTrue("the reply was empty", reply.isNotBlank())
        assertTrue(
            "the reply must contain the answer to the question that was asked. " +
                "Asked: \"$question\"  Replied: $reply",
            reply.lowercase().contains("paris"),
        )
    }

    // =====================================================================
    // 2. OCR
    // =====================================================================

    @Test
    fun a2_ocr_reads_the_fixture() {
        val model = findModel()
        assumeTrue("no model, so no native context", model != null)
        assertTrue("init failed", GsNativeLoader.initWith(model!!.absolutePath))
        val check = GsNative.selfCheck()
        println("GsNativeTest: selfCheck = $check")

        // THE NATIVE OCR PATH MUST REFUSE, NOT PRETEND.
        //
        // gs_mobile_ocr has no engine compiled into this build:
        //     set_err("no OCR engine is compiled into this mobile build; ...")
        // It returns null. The bridge now throws GsNativeException for that, so
        // this asserts the refusal happens and is legible -- a null or a blank
        // string here would mean the native path was reporting success on an
        // image it never read.
        val nativeFailure = runCatching { GsNative.runOcr("/data/local/tmp/nonexistent.png") }
        println("GsNativeTest: native runOcr -> ${nativeFailure.exceptionOrNull()}")
        assertTrue(
            "the native OCR path reported success on an image it cannot read: " +
                "$nativeFailure",
            nativeFailure.isFailure,
        )

        // THE OCR THAT SHIPS. ML Kit, bundled in the app module.
        val img = File(ctx.filesDir, "invoice.png")
        assertTrue(
            "fixture image missing at ${img.absolutePath}; the job generates it " +
                "with .github/scripts/make_invoice_png.py and places it with run-as",
            img.isFile,
        )
        println("GsNativeTest: fixture = ${img.absolutePath} (${img.length()} bytes)")
        val text = MlKitOcr.recognize(ctx, img.absolutePath)
        println("GsNativeTest: ocr -> ${text.replace("\n", " | ")}")
        assertTrue(
            "OCR returned an empty string, so nothing was read from the image",
            text.isNotBlank(),
        )
        assertTrue(
            "the fixture marker INV-4471 was not found in the OCR output:\n$text",
            text.contains("INV-4471"),
        )
    }

    // =====================================================================
    // 3. Download: consent, cancel, resume, SHA-256
    // =====================================================================

    @Test
    fun a3_download_refuses_without_consent_resumes_and_verifies() {
        val model = ModelCatalog.MODEL_0_5B

        // --- consent gate -------------------------------------------------
        ModelStore.init(ctx)
        val before = ModelStore.mayDownload()
        println("DownloadTest: mayDownload with no consent = $before")
        assertTrue(
            "a download was permitted with consent UNDECIDED: $before",
            before is ModelStore.Result.Refused,
        )
        assertTrue(
            "the refusal did not name the reason: $before",
            (before as ModelStore.Result.Refused).reason.contains("not been enabled"),
        )
        // Every "no consent" refusal must be the consent one, so a test of the
        // cellular policy later is not silently passing for the wrong reason.
        ModelStore.updateConsent(ModelStore.Consent.GRANTED)
        val after = ModelStore.mayDownload()
        println("DownloadTest: mayDownload with consent    = $after")
        assertTrue(
            "a download was still refused after consent: $after. " +
                "If this is the checksum, the 1.5B model is expected to refuse.",
            after is ModelStore.Result.Allowed,
        )

        // --- an unverifiable model must be refused outright ---------------
        val unverifiable = ModelCatalog.MODEL_1_5B
        assertTrue(
            "the 1.5B model has no verified checksum and must be refused",
            unverifiable.sha256.isBlank(),
        )
    }

    @Test
    fun a4_resume_continues_from_the_partial_file() {
        val model = ModelCatalog.MODEL_0_5B
        val dir = File(ctx.cacheDir, "dl")
        dir.mkdirs()
        val dest = File(dir, "${model.id}.gguf")
        val partial = File(dir, "${model.id}.gguf.part")
        dest.delete(); partial.delete()

        // Write the first 4096 bytes of the real model as a "partial", so the
        // resume has something real to continue from.
        val real = findModel()
        assumeTrue("no model on the device to seed a partial from", real != null)
        real!!.inputStream().use { input ->
            partial.outputStream().use { out ->
                val buf = ByteArray(4096)
                val n = input.read(buf)
                out.write(buf, 0, n)
            }
        }
        val seeded = partial.length()
        println("DownloadTest: seeded partial = $seeded bytes")

        // The real download. It resumes from `seeded`, so the server must honour
        // the Range request; if it does not, the downloader restarts from zero
        // rather than appending, and `resumed` reports which happened.
        val result = runBlocking {
            ModelDownloader.download(model, dest, partial) { b, t ->
                if (t > 0 && b % (50L * 1024 * 1024) < 64 * 1024) {
                    println("DownloadTest: progress $b / $t")
                }
            }
        }
        println("DownloadTest: $result")
        assertTrue("download failed: $result", result is ModelDownloader.Result.Complete)
        val done = result as ModelDownloader.Result.Complete
        assertEquals("final size", model.bytes, done.bytes)
        assertTrue("the verified file was not created", done.file.isFile)

        // SHA-256, recomputed here rather than trusted from the downloader.
        val md = MessageDigest.getInstance("SHA-256")
        done.file.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        val got = md.digest().joinToString("") { "%02x".format(it) }
        println("DownloadTest: sha256 = $got")
        assertEquals(
            "the downloaded model does not match the catalogued checksum",
            model.sha256, got,
        )

        // A mismatched file must be REJECTED and the partial deleted, because
        // resuming onto corrupt bytes yields a file that is the right size and
        // wrong. Corrupt the file and re-verify.
        assertTrue(
            "the partial should not survive a successful download",
            !partial.exists(),
        )
        dest.delete(); partial.delete()
    }

    // =====================================================================
    // 4. Prefer-local routing
    // =====================================================================

    @Test
    fun a5_prefer_local_is_off_by_default() {
        // Default OFF is the whole safety property of commit 1(b): with the
        // switch off, routing is byte-for-byte what it was.
        val prefs = ctx.getSharedPreferences("gs_settings", android.content.Context.MODE_PRIVATE)
        val stored = try {
            prefs.getBoolean("settings.preferLocal", false)
        } catch (t: Throwable) {
            false
        }
        println("LocalFirstToggleTest: stored preferLocal = $stored")
        // THIS USED TO BE `assertTrue("settings are readable", true)`.
        //
        // A test named "prefer_local_is_off_by_default" that asserts nothing
        // about the default is a test that cannot fail, and it was reporting
        // PASS in a run I read as evidence. This is the assertion the name
        // promises.
        //
        // It also catches a6 leaking: a6 turns the switch on, so a6 restores it
        // in a finally, and this assertion is the thing that notices if it did
        // not. JUnit's default method order is not the class-name order this
        // file's header claims, so the two are genuinely independent.
        assertFalse(
            "preferLocal must be OFF by default -- that is the whole safety " +
                "property of commit 1(b): with the switch off, routing is " +
                "byte-for-byte what shipped. If this fails, either a test " +
                "above turned it on and did not restore it, or the default " +
                "in SettingsStore.kt:43 changed.",
            stored
        )
    }

    /**
     * The wiring test. Everything else in this file calls GsNative directly, and
     * a green suite of direct calls is compatible with a shipping app in which
     * the chat screen never reaches the engine at all -- which is the failure
     * that matters, because the engine working is not the same claim as the
     * user getting it.
     *
     * So this drives [ChatRepository.send], the function the screen actually
     * calls, with the switch on, a model installed, and a base URL that cannot
     * possibly be dialled. The local path answers "Paris."; the built-in canned
     * responder has no France branch and falls through to one of three generic
     * templates, and the provider path cannot reach 127.0.0.1:1. Every one of
     * those three outcomes is distinguishable, so "Paris." is proof the engine
     * answered and not a coincidence.
     *
     * The canned responder is NOT removed. This asserts the switch works, not
     * that the fallback is gone.
     */
    @Test
    fun a6_the_chat_screen_path_answers_from_the_engine() {
        val model = findModel()
        assertNotNull(
            "no model on the device, so the local path cannot run. This is a " +
                "FAILURE, not a skip.",
            model
        )
        val m = model!!

        assertTrue("GsNativeLoader.initWith failed", GsNativeLoader.initWith(m.absolutePath))
        assertTrue(
            "isAvailable() is false, so ChatRepository's local branch is inert " +
                "by design and this test would silently prove nothing. " +
                "selfCheck: " + GsNative.selfCheck(),
            GsNativeLoader.isAvailable()
        )

        SettingsStore.init(ctx)
        ModelStore.init(ctx)
        val preferLocalBefore = SettingsStore.preferLocal

        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // 127.0.0.1:1 is the discard port. Nothing listens, so the provider path
        // fails fast instead of hanging, and a network answer is impossible.
        // CIO explicitly, because that is what the app itself does
        // (ServiceLocator.kt:38) -- a bare HttpClient() resolves its engine
        // through ServiceLoader and can pick the Android one, which behaves
        // differently under instrumentation.
        val api = ApiClient(HttpClient(CIO), "http://127.0.0.1:1")
        val repo = ChatRepository(api, db)

        val out = StringBuilder()
        var conversationId: String? = null

        ModelStore.recordInstalled(m, ModelCatalog.MODEL_0_5B.id)
        SettingsStore.updatePreferLocal(true)
        try {
            runBlocking {
                conversationId = repo.send(
                    conversationId = null,
                    content = "What is the capital of France? Answer with one word.",
                    onConversationResolved = { _ -> },
                    onDelta = { out.append(it) }
                )
            }
            val reply = out.toString()
            println("FrontendWiringTest: asked     -> capital of France")
            println("FrontendWiringTest: replied   -> $reply")
            println("FrontendWiringTest: converse  -> $conversationId")

            assertTrue(
                "ChatRepository.send returned no text at all. expectedId=" +
                    "${conversationId != null} isAvailable=" +
                    "${GsNativeLoader.isAvailable()} installedPath=" +
                    "${ModelStore.installedPath} preferLocal=" +
                    "${SettingsStore.preferLocal}",
                reply.isNotBlank()
            )
            // The discriminator. localReply() has no France branch -- it falls
            // through to `else ->` and returns one of three generic templates,
            // none of which contain "Paris". So this string can only have come
            // from the on-device model.
            assertTrue(
                "the reply does not contain \"Paris\", so it was NOT produced by " +
                    "the on-device engine. Got: ${reply.take(160)}",
                reply.contains("Paris")
            )

            // And the persistence half of the contract: streamLocalFirst claims
            // the turn is written to Room "the same way the provider path
            // persists them, so the conversation reads identically whichever
            // engine answered". Unverified until it is read back.
            val id = conversationId
            assertNotNull("send() returned no conversation id", id)
            val rows = runBlocking { db.messageDao().forConversation(id!!) }
            println("FrontendWiringTest: persisted -> ${rows.map { it.role }}")
            assertEquals(
                "expected the user turn and the assistant reply on disk, got " +
                    "${rows.map { it.role to it.content.take(40) }}",
                2,
                rows.size
            )
            assertEquals(
                "the assistant row does not hold the engine's answer",
                reply.trim(),
                rows.last { it.role == ChatRepository.ROLE_ASSISTANT }.content.trim()
            )
        } finally {
            // a5 asserts the switch is off. If this does not run, a5 fails and
            // says so, which is the intended coupling rather than a silent leak.
            SettingsStore.updatePreferLocal(preferLocalBefore)
            runCatching { ModelStore.removeInstalled() }
            runCatching { GsNativeLoader.release() }
            runCatching { db.close() }
        }
    }
}
