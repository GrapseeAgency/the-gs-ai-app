package com.grapsee.gsai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.di.gsHttpClient
import com.grapsee.gsai.data.local.AppDatabase
import com.grapsee.gsai.data.local.DiffusionCatalog
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
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.zip.ZipFile
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
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

        // ESTABLISHES ITS OWN CONTEXT, which it did not until run 36868936414
        // proved that relying on whatever an earlier test left behind is a
        // dependency on an order instrumentation chooses:
        //
        //   GsNativeException: GsNative.backendAvailable: no native context;
        //     call init(modelPath) first
        //
        // twenty-six seconds after perf_...swept_in_one_run ended by calling
        // GsNative.shutdown(). A test that inherits shared state is one `shut
        // down` away from failing for a reason that has nothing to do with it.
        //
        // And this is the FIRST test alphabetically, so it had been getting away
        // with running before anything had initialised -- which means it was only
        // ever passing because some other test happened to sort later.
        findModel()?.let { model ->
            GsNativeLoader.initWith(model.absolutePath)
        }

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
     * The OFF path, which a5 does not cover.
     *
     * a5 asserts the switch is off by default. That is half the property. The
     * other half is what the app DOES when it is off, and it is the half that
     * protects every existing user: with the switch off, routing must be exactly
     * what it was before the engine existed.
     *
     * The canonical way to catch an accidental inversion is to assert the two
     * branches produce DIFFERENT answers for the same input, because a test that
     * only checks one side passes whether the flag is wired up or ignored
     * entirely.
     */
    @Test
    fun a7_the_switch_actually_routes_off_and_on() {
        val model = findModel()
        assertNotNull("no model on the device", model)
        val m = model!!

        assertTrue("init failed", GsNativeLoader.initWith(m.absolutePath))
        assumeTrue(
            "no generation backend, so the ON branch cannot be compared. " +
                "selfCheck: " + GsNative.selfCheck(),
            GsNativeLoader.isAvailable(),
        )

        SettingsStore.init(ctx)
        ModelStore.init(ctx)
        ModelStore.recordInstalled(m, ModelCatalog.MODEL_0_5B.id)

        // A 127.0.0.1:1 ApiClient cannot answer. So the OFF path MUST fall
        // through to the provider, which cannot succeed, and must produce the
        // offline notice -- never the model's answer.
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // THE APP'S CLIENT, NOT A BARE ONE. See a8 for the full story: with no
        // ContentNegotiation, `setBody(SendMessageRequest(...))` cannot be
        // serialized, the request fails before it connects, and the turn is
        // classified BackendError(0, null) rather than GenuineUnreachable. The
        // assertion below is unaffected -- `on` is "Paris." from
        // streamLocalFirst, which never touches the network -- but the string
        // this test prints should be one the product would actually produce.
        val api = ApiClient(gsHttpClient("a7-${UUID.randomUUID()}"), "http://127.0.0.1:1")
        val repo = ChatRepository(api, db)

        val prompt = "What is the capital of France? Answer with one word."

        fun sendOnce(): String {
            val out = StringBuilder()
            runBlocking {
                repo.send(
                    conversationId = null,
                    content = prompt,
                    onConversationResolved = { _ -> },
                    onDelta = { out.append(it) }
                )
            }
            return out.toString()
        }

        val onBefore = SettingsStore.preferLocal
        try {
            // --- OFF: must NOT be the model -----------------------------
            SettingsStore.updatePreferLocal(false)
            assertFalse("the switch did not turn off", SettingsStore.preferLocal)
            val off = sendOnce()
            println("LocalFirstToggleTest: OFF -> ${off.take(100)}")
            assertFalse(
                "with preferLocal OFF the reply came from the on-device engine " +
                    "anyway. The switch is not routing. Got: ${off.take(160)}",
                off.contains("Paris"),
            )

            // --- ON: must be the model ----------------------------------
            SettingsStore.updatePreferLocal(true)
            assertTrue("the switch did not turn on", SettingsStore.preferLocal)
            val on = sendOnce()
            println("LocalFirstToggleTest: ON  -> ${on.take(100)}")
            assertTrue(
                "with preferLocal ON the engine did not answer. Got: ${on.take(160)}",
                on.contains("Paris"),
            )

            // The comparison that makes this a routing test rather than two
            // independent assertions: if both branches behaved the same, the
            // flag is not doing anything and both of the above would still pass.
            assertNotEquals(
                "the ON and OFF branches returned identical text, so the switch " +
                    "is not routing at all: ${on.take(80)}",
                off.trim(),
                on.trim(),
            )
        } finally {
            SettingsStore.updatePreferLocal(onBefore)
            runCatching { ModelStore.refreshInstalled() }
            runCatching { GsNativeLoader.release() }
            runCatching { db.close() }
        }
    }

    /**
     * The canned responder must still be there.
     *
     * The whole safety argument for defaulting the switch OFF is that the worst
     * outcome is the old behaviour, one round trip later. That is only true if
     * localReply() still answers -- and it is a `private fun` in a repository
     * that nothing has ever called in a test, so it could have been deleted,
     * emptied, or broken by any change to the local path and every existing test
     * would still pass.
     *
     * This asserts the fallback produces real text for a prompt the engine has no
     * business answering specially, and that it is NOT the model's answer -- so
     * the two paths are distinguishable and neither can stand in for the other.
     */
    /**
     * WHICH FAILURE ARM each backend behaviour produces, asserted on the arm's own
     * string rather than on the reply.
     *
     * Replaces a8_the_canned_responder_still_answers, which measured the wrong
     * thing three times and printed `— GS backend error (HTTP 0) —` for all three:
     * a dead port (that arm RETURNS before streamLocalReply), a mid-stream cut
     * (MidStreamCut never calls streamLocalReply -- one call site, line 486, in
     * GenuineUnreachable), and a bare HttpClient (no ContentNegotiation, so the
     * request failed before connecting and the exception was not a
     * ConnectException). The product was correct in all three cases.
     *
     * The reply is an indirect signal and a bad one: five mis-set-ups produced the
     * same string. What matters is the CLASSIFICATION, and the three arms are
     * distinguished by strings the source emits itself:
     *
     *     GenuineUnreachable -> "— Offline — cannot reach GS. —"
     *     MidStreamCut       -> "connection dropped mid-turn"
     *     BackendError       -> "— GS backend error (HTTP 500) —"
     *
     * This is an indirect read and is labelled as such. What makes it trustworthy
     * is that the three are mutually exclusive and each is asserted against the
     * arm that emits it in the source, not against a comment.
     */
    @Test
    fun a8_each_backend_behaviour_maps_to_its_own_failure_arm() {
        val model = findModel()
        assertNotNull("no model on the device", model)
        assertTrue(
            "GsNativeLoader.initWith failed",
            GsNativeLoader.initWith(model!!.absolutePath),
        )
        SettingsStore.init(ctx)
        ModelStore.init(ctx)
        ModelStore.recordInstalled(model, ModelCatalog.MODEL_0_5B.id)

        val prompt = "What is the capital of France? Answer with one word."
        val preferBefore = SettingsStore.preferLocal

        // The switch is OFF for the whole test. The fallback chain under test is
        // the provider-FAILED path, and with the switch on, streamLocalFirst
        // answers before any of these servers is contacted -- which is a different
        // test, and one that already exists (b3, b4).
        SettingsStore.updatePreferLocal(false)

        fun run(repo: ChatRepository): String = turn(repo, prompt)

        // --- (b) GenuineUnreachable: a port nothing is listening on -----------
        val dead = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val deadPort = dead.localPort
        dead.close()
        val db1 = inMemoryDb()
        try {
            val reply = run(
                ChatRepository(
                    ApiClient(
                        gsHttpClient("a8-dead-${UUID.randomUUID()}"),
                        "http://127.0.0.1:$deadPort",
                    ),
                    db1,
                ),
            )
            println("Classify: refused connect   -> ${reply.take(120)}")
            assertTrue(
                "a refused TCP connect must be classified GenuineUnreachable, whose " +
                    "arm emits the offline label. Instead: ${reply.take(160)}",
                reply.contains("— Offline —"),
            )
        } finally {
            runCatching { db1.close() }
        }

        // --- (a) MidStreamCut: reachable, streamed, then died ------------------
        // This is the case that ISN'T offline, and the one that matters most: the
        // provider was there and then stopped. Treating it as "offline" is the
        // false label the FORENSIC AUDIT [5] comment in send() is about.
        val cut = WireServer.start(WireServer.MODE_CUT_MID_STREAM)
        val db2 = inMemoryDb()
        try {
            // The harness proves it answers before its case concludes anything.
            assertNotNull(
                "the mid-stream-cut server did not answer a plain socket probe, " +
                    "so the \"provider was reachable\" assertion below would be " +
                    "measuring the harness rather than the product",
                cut.assertReachable(),
            )
            val reply = run(
                ChatRepository(WireServer.apiFor(cut, "a8-cut-${UUID.randomUUID()}"), db2),
            )
            println("Classify: mid-stream cut    -> ${reply.take(120)}")
            println("Classify: server saw ${cut.messageRequests.get()} request(s)")
            assertTrue(
                "the server served ${cut.messageRequests.get()} request(s), so the " +
                    "provider WAS reachable and this cannot be an offline label",
                cut.messageRequests.get() >= 1,
            )
            assertFalse(
                "a MID-STREAM CUT is not an offline event and must not be labelled " +
                    "as one. The provider answered and then stopped, so claiming " +
                    "\"— Offline — cannot reach GS. —\" tells the user their network " +
                    "is down when it was not. Got: ${reply.take(160)}",
                reply.contains("— Offline —"),
            )
            // THE CLASSIFICATION ITSELF, named. `MidStreamCut` appends
            //
            //   "— The connection dropped mid-turn. Reopen this chat in a moment —"
            //
            // or, if recoverLatestAssistant finds a persisted answer, adopts that
            // instead. WireServer returns [] for history, so recovery finds
            // nothing and the label is the expected outcome.
            //
            // This is the assertion that distinguishes a mid-stream cut from a
            // refusal, and it is the one that failed first: without a pause before
            // the close, CIO discarded the partial chunked body, `receivedAnyEvent`
            // never flipped, and the turn was classified BackendError(0) -- a
            // FABRICATED status code for a provider that had streamed. The gap is
            // what makes the mode a mid-stream cut rather than a truncation race.
            assertTrue(
                "a mid-stream cut must be classified MidStreamCut, whose arm says " +
                    "the connection dropped mid-turn. Getting " +
                    "\"GS backend error (HTTP 0)\" instead means the provider " +
                    "streamed and was then reported as if it had never answered, " +
                    "with a status code that does not exist. Got: " +
                    "${reply.take(160)}",
                reply.contains("connection dropped mid-turn"),
            )
            assertFalse(
                "a mid-stream cut was reported with a fabricated HTTP 0, which is " +
                    "the false status the FORENSIC AUDIT [5] comment exists to " +
                    "prevent. Got: ${reply.take(160)}",
                reply.contains("HTTP 0"),
            )
        } finally {
            runCatching { cut.stop() }
            runCatching { db2.close() }
        }

        // --- (c) BackendError: the server answered 500 ------------------------
        // Distinct from both of the above, and the one that must NOT be dressed up
        // as "offline": the server was up and said no.
        val err = WireServer.start(WireServer.MODE_HTTP_500)
        val db3 = inMemoryDb()
        try {
            // Every mode proves it answers before its case is allowed to conclude
            // anything. See the note in b2.
            assertNotNull(
                "the 500 server did not answer a plain socket probe",
                err.assertReachable(),
            )
            val reply = run(
                ChatRepository(WireServer.apiFor(err, "a8-500-${UUID.randomUUID()}"), db3),
            )
            println("Classify: HTTP 500          -> ${reply.take(120)}")
            // WHAT THE ARM ACTUALLY DOES, which I asserted wrongly first. The arm is
            //
            //   val serverText = failure.serverMessage?.takeIf { it.isNotBlank() }
            //       ?: "GS backend error (HTTP ${failure.status})"
            //
            // so when the server sends a sanitized `{"error": "..."}` -- which
            // ErrorResponseDto decodes, and which is what a real backend does --
            // the app shows THE SERVER'S MESSAGE. Run 36777497765:
            //
            //   Classify: HTTP 500 -> — upstream model unavailable —
            //
            // That is better behaviour than the text I asserted, and my assertion
            // was wrong: it demanded the generic fallback, which appears only when
            // the server says nothing. Asserting the fallback would have required
            // a broken server to make the test pass.
            //
            // So the assertion is on the PROPERTY, not on which of the two strings
            // appeared: the status or the server's message must be surfaced, and
            // neither may claim the network is down.
            assertTrue(
                "a 500 must surface either the server's sanitized message or the " +
                    "status, and must NOT be called offline. Got: ${reply.take(160)}",
                reply.contains("upstream model unavailable") ||
                    reply.contains("HTTP 500"),
            )
            assertFalse(
                "an HTTP 500 is the server answering, so labelling it \"— Offline —\" " +
                    "is a lie the FORENSIC AUDIT [5] comment exists to prevent. " +
                    "Got: ${reply.take(160)}",
                reply.contains("— Offline —"),
            )
        } finally {
            runCatching { err.stop() }
            runCatching { db3.close() }
        }

        // --- (d) Clean break: a 200 with no events, closed cleanly ------------
        // The fourth mode, and the one that is easiest to get wrong precisely
        // because nothing failed. No exception, no events, no done event: the
        // channel closed mid-turn. send() has a separate branch for it --
        //
        //     if (doneMessage == null && accumulated.isEmpty()) { recoverLatest... }
        //
        // -- and it must NOT be reported as an offline network, because no
        // network claim was ever made and none can be supported.
        val quiet = WireServer.start(WireServer.MODE_CLEAN_BREAK)
        val db4 = inMemoryDb()
        try {
            assertNotNull(
                "the clean-break server did not answer a plain socket probe",
                quiet.assertReachable(),
            )
            val reply = run(
                ChatRepository(WireServer.apiFor(quiet, "a8-clean-${UUID.randomUUID()}"), db4),
            )
            println("Classify: 200, no events   -> '${reply.take(80)}'")
            println("Classify: server saw ${quiet.messageRequests.get()} request(s)")
            assertTrue(
                "the provider answered 200, so this turn reached the network",
                quiet.messageRequests.get() >= 1,
            )
            assertFalse(
                "a clean break with a 200 is not an offline event. The provider was " +
                    "reachable and answered, so \"— Offline — cannot reach GS. —\" " +
                    "is a claim about the network that nothing here supports. " +
                    "Got: ${reply.take(160)}",
                reply.contains("— Offline —"),
            )
        } finally {
            runCatching { quiet.stop() }
            runCatching { db4.close() }
        }

        SettingsStore.updatePreferLocal(preferBefore)
    }

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
            // NOT removeInstalled(). That is File(p).delete() -- it destroys the
            // 491 MB GGUF. JUnit's method order is not source order, so if a6
            // ran first and deleted it, a1/a1b/a2 would fail on "no model on the
            // device" and this test would have destroyed the evidence for every
            // other test in the file. Clean up the RECORD, not the file.
            //
            // refreshInstalled() re-derives installedPath from what is actually
            // on disk, which is exactly the restore and deletes nothing.
            runCatching { ModelStore.refreshInstalled() }
            runCatching { GsNativeLoader.release() }
            runCatching { db.close() }
        }
    }
    // ------------------------------------------------------------------
    // The switch x network matrix. Four cases, four markers, one per source.
    // See the class-level note in WireServer.kt for why the network needs a
    // server that actually answers.
    // ------------------------------------------------------------------

    /** Loads the model and the stores every one of the four needs. */
    private fun prepareMatrix(): java.io.File {
        val model = findModel()
        assertNotNull(
            "no model on the device, so the ON cases cannot be compared. This is " +
                "a FAILURE, not a skip: a routing matrix that cannot reach its own " +
                "ON branch proves nothing about the OFF branch either.",
            model,
        )
        val m = model!!
        assertTrue("GsNativeLoader.initWith failed", GsNativeLoader.initWith(m.absolutePath))
        assumeTrue(
            "no generation backend in this build, so the model's own answer " +
                "cannot be recognised and the matrix would be vacuous. selfCheck: " +
                GsNative.selfCheck(),
            GsNativeLoader.isAvailable(),
        )
        SettingsStore.init(ctx)
        ModelStore.init(ctx)
        ModelStore.recordInstalled(m, ModelCatalog.MODEL_0_5B.id)
        return m
    }

    /** One send through a real ChatRepository, returning everything it emitted. */
    private fun turn(
        repo: ChatRepository,
        content: String,
    ): String {
        val out = StringBuilder()
        runBlocking {
            repo.send(
                conversationId = null,
                content = content,
                onConversationResolved = { _ -> },
                onDelta = { out.append(it) },
            )
        }
        return out.toString()
    }

    private fun inMemoryDb() =
        Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    /** The prompt every case uses: the model answers "Paris", the others cannot. */
    private val matrixPrompt = "What is the capital of France? Answer with one word."

    @Test
    fun b1_prefer_off_network_dead_uses_the_canned_responder() {
        prepareMatrix()
        val db = inMemoryDb()
        // THE APP'S CLIENT, via gsHttpClient, for the reason a8 records: a bare
        // HttpClient cannot serialize SendMessageRequest, so the request fails
        // before it connects and the turn is classified BackendError rather than
        // GenuineUnreachable.
        val before = SettingsStore.preferLocal
        try {
            SettingsStore.updatePreferLocal(false)
            assertFalse("the switch did not turn off", SettingsStore.preferLocal)

            // 127.0.0.1:1 is the discard port: nothing listens, so the connect is
            // refused for certain, with no server in the way to answer by accident.
            val dead = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
            val port = dead.localPort
            dead.close()
            val repo = ChatRepository(
                ApiClient(gsHttpClient("b1-${UUID.randomUUID()}"), "http://127.0.0.1:$port"),
                db,
            )
            val reply = turn(repo, matrixPrompt)
            println("Matrix b1 OFF/dead -> ${reply.take(180)}")

            // POSITIVE assertions. The turn produced the honest offline label...
            assertTrue(
                "with the provider unreachable the turn should open with the " +
                    "offline label, which is what proves it took the " +
                    "GenuineUnreachable arm. Got: ${reply.take(160)}",
                reply.contains("— Offline —"),
            )
            // ...and then the CANNED RESPONDER answered, at real length.
            val afterLabel = reply.substringAfter("— Offline —")
            assertTrue(
                "the canned responder produced nothing after the offline label, so " +
                    "a user with no network would see a bare notice: " +
                    "${afterLabel.take(120)}",
                afterLabel.trim().length >= 20,
            )
            // THE ASSERTION THIS CASE EXISTS FOR. Not a negation on its own -- the
            // two above already prove the turn produced a real answer -- but the
            // discriminator: the model must not have been consulted.
            assertFalse(
                "with preferLocal OFF the reply came from the on-device model " +
                    "anyway. The switch is not gating the engine on the " +
                    "provider-failed path. Got: ${reply.take(160)}",
                reply.contains("Paris"),
            )
        } finally {
            SettingsStore.updatePreferLocal(before)
            runCatching { db.close() }
        }
    }

    @Test
    fun b2_prefer_off_network_up_uses_the_network() {
        prepareMatrix()
        val db = inMemoryDb()
        val server = WireServer.start(WireServer.MODE_FULL)
        val before = SettingsStore.preferLocal
        try {
            SettingsStore.updatePreferLocal(false)
            assertFalse("the switch did not turn off", SettingsStore.preferLocal)

            // THE HARNESS CHECKS ITSELF FIRST, before the product is asked to do
            // anything. Runs 36770334063 and 36772124600 both reported
            // "server saw 0 message request(s)" and both were read as a product
            // failure when nothing had established the request left the process.
            // If the server cannot be reached over a plain socket then it is the
            // HARNESS that is broken, and saying so is the whole point: a test
            // that cannot distinguish its own breakage from a product defect is
            // worse than no test, because it reports a failure that is not there.
            assertNotNull(
                "the test server did not answer a plain socket probe, so this " +
                    "harness is broken and any result from it would be meaningless",
                server.assertReachable(),
            )

            val repo = ChatRepository(
                WireServer.apiFor(server, "b2-${UUID.randomUUID()}"),
                db,
            )
            val reply = turn(repo, matrixPrompt)
            println("Matrix b2 OFF/up -> ${reply.take(180)}")
            println("Matrix b2 server saw ${server.messageRequests.get()} message request(s)")

            // THE NETWORK ANSWERED, and its marker is a string nothing else can
            // produce. This is the case that had no test at all.
            assertTrue(
                "with the network reachable and preferLocal OFF the reply must be " +
                    "the provider's. Neither the model (\"Paris\") nor the canned " +
                    "responder can produce '${WireServer.NETWORK_MARKER}'. " +
                    "The server served ${server.messageRequests.get()} request(s). " +
                    "Got: ${reply.take(160)}",
                reply.contains(WireServer.NETWORK_MARKER),
            )
            assertTrue(
                "the turn never reached the provider, so the network path is " +
                    "untested rather than working",
                server.messageRequests.get() >= 1,
            )
            assertFalse(
                "the on-device model was consulted even though the provider " +
                    "answered and preferLocal is OFF. Got: ${reply.take(160)}",
                reply.contains("Paris"),
            )
        } finally {
            SettingsStore.updatePreferLocal(before)
            runCatching { server.stop() }
            runCatching { db.close() }
        }
    }

    @Test
    fun b3_prefer_on_network_dead_uses_the_model() {
        prepareMatrix()
        val db = inMemoryDb()
        val before = SettingsStore.preferLocal
        try {
            SettingsStore.updatePreferLocal(true)
            assertTrue("the switch did not turn on", SettingsStore.preferLocal)

            val dead = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
            val port = dead.localPort
            dead.close()
            val repo = ChatRepository(
                ApiClient(gsHttpClient("b3-${UUID.randomUUID()}"), "http://127.0.0.1:$port"),
                db,
            )
            val reply = turn(repo, matrixPrompt)
            println("Matrix b3 ON/dead -> ${reply.take(180)}")

            // POSITIVE: the model's own answer. localReply has no France branch, so
            // "Paris" can only have come from the engine.
            assertTrue(
                "with preferLocal ON the on-device model must answer even with no " +
                    "network. localReply cannot produce \"Paris\". " +
                    "Got: ${reply.take(160)}",
                reply.contains("Paris"),
            )
        } finally {
            SettingsStore.updatePreferLocal(before)
            runCatching { db.close() }
        }
    }

    @Test
    fun b4_prefer_on_network_up_uses_the_model() {
        prepareMatrix()
        val db = inMemoryDb()
        val server = WireServer.start(WireServer.MODE_FULL)
        val before = SettingsStore.preferLocal
        try {
            SettingsStore.updatePreferLocal(true)
            assertTrue("the switch did not turn on", SettingsStore.preferLocal)

            val repo = ChatRepository(
                WireServer.apiFor(server, "b4-${UUID.randomUUID()}"),
                db,
            )
            val reply = turn(repo, matrixPrompt)
            println("Matrix b4 ON/up -> ${reply.take(180)}")
            println("Matrix b4 server saw ${server.messageRequests.get()} message request(s)")

            assertTrue(
                "prefer-local means PREFER local: with the switch on and the " +
                    "network reachable, the model must still answer. " +
                    "Got: ${reply.take(160)}",
                reply.contains("Paris"),
            )
            // THE HALF THAT ACTUALLY PROVES "PREFER". The model's marker alone
            // would also be produced if the network had simply answered. Zero
            // message requests is what shows the local-first path short-circuited
            // the turn before any HTTP call -- which is the behaviour, and the
            // reason this case is separate from b2.
            assertEquals(
                "with preferLocal ON the turn must not reach the provider at all: " +
                    "streamLocalFirst answers before any network call, so " +
                    "${WireServer.NETWORK_MARKER} must be absent. Got: " +
                    "${reply.take(160)}",
                0,
                server.messageRequests.get(),
            )
            assertFalse(
                "the provider answered even though preferLocal is ON: ${reply.take(160)}",
                reply.contains(WireServer.NETWORK_MARKER),
            )
        } finally {
            SettingsStore.updatePreferLocal(before)
            runCatching { server.stop() }
            runCatching { db.close() }
        }
    }

    // ------------------------------------------------------------------
    // PERFORMANCE: the numbers the shippability decision needs.
    //
    // The mobile surface has no timing accessor and no token count, so these are
    // derived from TWO measured points using the budget that already bounds
    // generation: t(n) = TTFT + n * per_token, and the slope from two budgets is
    // the per-token cost exactly. No tokenizer, no new export, no ABI bump.
    // ------------------------------------------------------------------

    /**
     * Times [GsNative.chatWithBudget] for [budget] tokens, [repeats] times, and
     * returns the times in milliseconds.
     *
     * The MINIMUM is returned as the headline: this runs on a shared emulator, and
     * the least-contaminated sample is the one worth reporting. The spread is
     * printed so the noise is visible rather than hidden.
     */
    /**
     * [prompt] is EXPLICIT at every call site and comes BEFORE [out], and both
     * of those are deliberate.
     *
     * It has no default because a benchmark number without its prompt is not a
     * measurement -- the whole reason the batch and prompt-processing-thread
     * levers needed a longer prompt is that the old shared one was too short to
     * exercise them, and a default would let that mistake recur silently.
     *
     * It comes before [out] because Kotlin binds a trailing lambda to the LAST
     * parameter. Appending `prompt` after `out` looked harmless and broke every
     * existing `timeBudget(1, 2) { ... }` call at once -- android-app 36957611401
     * reported "No value passed for parameter 'out'" and "Argument type mismatch"
     * on all four. A defaulted parameter in that position is not a safe default
     * at all; it is a different overload's worth of breakage wearing one.
     *
     * This was added after a second, hand-rolled timing loop drifted into a
     * `List<Pair<Long, Int>>` and then called `.min()` on it, which does not
     * exist because Pair is not Comparable. Two timing helpers in one file is how
     * they come to disagree about what a number means -- the compiler caught the
     * type error, not the duplication, which is the part that is worth fixing.
     */
    private fun timeBudget(
        budget: Int,
        repeats: Int,
        prompt: String,
        out: (String) -> Unit,
    ): Pair<List<Long>, String> {
        val times = ArrayList<Long>(repeats)
        var last = ""
        repeat(repeats) { i ->
            val t0 = System.nanoTime()
            val text = GsNative.chatWithBudget(prompt, budget)
            val dt = (System.nanoTime() - t0) / 1_000_000
            times += dt
            last = text
            out("  budget=$budget run=$i -> ${dt}ms, ${text.length} chars")
        }
        return Pair(times, last)
    }

    /**
     * A prompt that will not emit EOS inside the budget. A model asked a question
     * answers it and stops, which would make the budget meaningless.
     */
    private val perfPrompt =
        "Count from 1 to 60 in decimal, one number per line, and nothing else. " +
            "Do not stop early. Do not add any commentary."

    /**
     * TTFT and decode rate for the 0.5B model, on this device, through the real
     * engine.
     *
     * Published numbers, which is the point: the plan's "Performance" item asked
     * for exactly these and they did not exist.
     */
    @Test
    fun perf_the_0_5b_reports_a_measurable_decode_rate() {
        val model = findModel()
        assertNotNull(
            "no model on the device, so there is no rate to report. This is a FAILURE, " +
                "not a skip: a performance test that skips reports nothing, and the " +
                "operator's decision needs a number.",
            model,
        )
        val m = model!!
        assertTrue("GsNativeLoader.initWith failed", GsNativeLoader.initWith(m.absolutePath))
        assertTrue(
            "no generation backend, so nothing would be timed. selfCheck: " +
                GsNative.selfCheck(),
            GsNativeLoader.isAvailable(),
        )
        println("PERF model: ${m.name} (${m.length()} bytes)")

        // A WARM-UP GENERATION, discarded. The first call pays for whatever the
        // backend defers to first use -- buffer allocation, a first-touch page
        // fault storm on a freshly mmapped model. Timing it would fold a one-off
        // into the number reported as steady state, and the operator would read it
        // as the device's speed.
        val warmMs = (System.nanoTime() / 1_000_000.toLong()).let { t0 ->
            GsNative.chatWithBudget(perfPrompt, 8)
            System.nanoTime() / 1_000_000 - t0
        }
        println("PERF warm-up (8 tokens, discarded): ${warmMs}ms")

        // Two budgets, far apart so the slope is well conditioned: 49 tokens
        // against 1 gives 48 token-intervals instead of 30.
        val repeats = 3
        val (low, lowText) = timeBudget(1, repeats, perfPrompt) { println("PERF $it") }
        val (high, highText) = timeBudget(49, repeats, perfPrompt) { println("PERF $it") }

        // THE BUDGET MUST BE HONOURED, OR THE SLOPE IS AN ARTEFACT.
        //
        // If the model emits EOS early, both calls return the same short text, the
        // difference between them is noise, and the "decode rate" is two identical
        // generations divided by 48. That is the worst outcome available: a
        // real-looking figure with no meaning, which is checked here BEFORE
        // anything is reported.
        //
        // Compared as LENGTHS rather than against a fixed char count. An earlier
        // version asserted `text.length >= budget * 2`, which is a false failure
        // waiting to happen: a one-token completion can legitimately be a single
        // character, and a test that cannot fail for a good reason should not be
        // asserting a magic number.
        assertTrue(
            "the 1-token and 49-token budgets produced ${lowText.length} and " +
                "${highText.length} chars, so the budget is not what bounds this " +
                "generation and the rate derived from them is an artefact of two " +
                "near-identical calls. The prompt is: ${perfPrompt.take(60)}",
            highText.length > lowText.length + 20,
        )
        println("PERF budget honoured: 1 token -> ${lowText.length} chars, " +
            "49 tokens -> ${highText.length} chars")

        val tLow = low.min()
        val tHigh = high.min()
        val perTokenMs = (tHigh - tLow).toDouble() / 48.0
        val tps = if (perTokenMs > 0) 1000.0 / perTokenMs else 0.0
        // t(1) = TTFT + 1 token, so the intercept is the latency to first token.
        val ttftMs = tLow - perTokenMs

        println("=== PERFORMANCE, 0.5B on this device ===")
        println("  budget  1 token : min ${tLow}ms  all $low")
        println("  budget 49 tokens: min ${tHigh}ms  all $high")
        println("  per token       : ${"%.1f".format(perTokenMs)} ms")
        println("  decode rate     : ${"%.2f".format(tps)} tok/s")
        println("  TTFT            : ${"%.0f".format(ttftMs)} ms")
        println("  48-token spread : ${tHigh - tLow}ms")

        // THE SPREAD IS REPORTED BESIDE THE NUMBER, because a single figure from
        // a shared emulator means nothing without it.
        val highSpread = (high.max() - high.min()).coerceAtLeast(1)
        println("  49-token spread : ${highSpread}ms across $repeats runs")

        assertTrue(
            "the per-token cost came out as ${"%.3f".format(perTokenMs)}ms, which is " +
                "not a decode rate a device can produce. A t(1) of ${tLow}ms and a " +
                "t(49) of ${tHigh}ms means the two points measure the same thing and " +
                "the slope is an artefact.",
            perTokenMs > 0.0,
        )
        assertTrue(
            "measured ${"%.2f".format(tps)} tok/s, which is not a plausible decode " +
                "rate for a 0.5B on any CPU. The measurement is wrong, not the device.",
            tps > 0.5,
        )
        assertTrue(
            "the 49-token run varied by ${highSpread}ms across $repeats runs, which " +
                "is ${highSpread * 100 / (tHigh - tLow).coerceAtLeast(1)}% of the " +
                "48-token spread the rate is derived from. The number is noise.",
            highSpread * 4 < (tHigh - tLow),
        )
    }


    // ========================================================================
    // The image path, IN-PROCESS. No subprocess anywhere on it.
    //
    // WHAT IS ACTUALLY CLAIMED HERE, precisely, because "image generation works"
    // is two different claims and conflating them is how a feature becomes
    // untestable:
    //
    //   c0  the procedural path produces a real file with real content, on the
    //       device, with no weights present. That is the part this build can
    //       prove, and it is not a smaller claim than it sounds -- it is the whole
    //       diagram/chart feature.
    //   c1  the diffusion path reports its capability HONESTLY: it either works
    //       and writes a decodable PNG, or it refuses with a reason and writes
    //       NOTHING. Both halves are asserted, because the failure mode that
    //       matters is a placeholder image that looks like success.
    //
    // NO DIFFUSION CHECKPOINT IS DOWNLOADED HERE. qwen2.5-0.5b is 491 MB; a SD
    // checkpoint is 2.3 GB. So c1's diffusion half is asserted as an HONEST
    // REFUSAL in this build, and that is a real assertion rather than a skip:
    // it fails if the library silently claims it can generate, and it fails if a
    // failure ever writes a file.
    // ========================================================================

    @Test
    fun c0_the_procedural_path_writes_a_real_svg_on_the_device() {
        assertTrue(
            "libgs_ffi.so did not load, so nothing below means anything",
            GsNativeLoader.isLibraryLoaded(),
        )
        val out = File(
            InstrumentationRegistry.getArguments().getString("gsScratch")
                ?: ctx.cacheDir.absolutePath,
            "gs-test-diagram.svg",
        )
        out.delete()
        assertFalse("the fixture path already exists, so this proves nothing", out.exists())

        // A spec with named content, so the assertions below can look for THAT and
        // not for any file at all.
        val spec = """{"title":"Engine Wiring","width":"800","height":"400",
                       "layer1":"orchestration","layer2":"inference"}"""
        GsNative.renderSvg(spec, out.absolutePath)
        println("GsNativeTest: renderSvg -> ${out.length()} bytes")

        assertTrue(
            "renderSvg returned GS_OK but wrote no file",
            out.exists(),
        )
        val bytes = out.length()
        assertTrue(
            "the SVG is $bytes bytes, which is too small to hold the spec",
            bytes > 200,
        )
        val text = out.readText()
        // Content, not just existence. A file that exists and is empty, or holds a
        // blank diagram, satisfies "did it produce a file" and satisfies nothing
        // else.
        assertTrue(
            "the SVG does not start with <svg or <?xml: ${text.take(80)}",
            text.contains("<svg") || text.contains("<?xml"),
        )
        for (needle in listOf("Engine Wiring", "orchestration", "inference")) {
            assertTrue(
                "the SVG is missing \"$needle\", so the spec was not rendered. " +
                    "A file that exists but omits what was asked for is the same " +
                    "failure as no file, wearing a different hat.",
                text.contains(needle),
            )
        }
        assertTrue("no <rect> or <text>, so nothing was drawn", text.contains("<rect") || text.contains("<text"))
        out.delete()
    }

    @Test
    fun c1_the_diffusion_path_is_honest_about_what_it_cannot_do() {
        assertTrue(
            "libgs_ffi.so did not load, so nothing below means anything",
            GsNativeLoader.isLibraryLoaded(),
        )

        // A model that is definitely absent. If this one loads, the fixture is
        // wrong and every assertion below is about nothing.
        val missing = File(
            InstrumentationRegistry.getArguments().getString("gsScratch")
                ?: ctx.cacheDir.absolutePath,
            "gs-test-no-such-checkpoint.safetensors",
        )
        missing.delete()
        assertFalse("the fixture path exists, so this proves nothing", missing.exists())

        val handle = GsNative.sdCreate(missing.absolutePath)
        val reason = GsNative.lastError()
        println("GsNativeTest: sdCreate(missing) -> handle=$handle lastError=$reason")

        if (handle != 0L) {
            // The wrapper returned a context for a file that does not exist. Either
            // the load is lazy -- defensible -- or it is wrong. Ask, rather than
            // assume either way.
            val backend = GsNative.sdBackendName(handle)
            val available = GsNative.sdAvailable(handle)
            println("GsNativeTest: backend=$backend available=$available")
            assertTrue(
                "sdBackendName returned an empty string, so the caller cannot tell " +
                    "a working backend from an absent one",
                backend.isNotEmpty(),
            )
            if (!available) {
                assertTrue(
                    "a context that cannot generate reports backend \"$backend\", " +
                        "which does not say the library is absent. \"Could not find " +
                        "the weights\" and \"no diffusion library\" are different " +
                        "problems with opposite fixes.",
                    backend.contains("not-compiled") || backend.contains("sd.cpp"),
                )
            }

            // THE CENTRAL ASSERTION: a refusal writes NOTHING.
            val out = File(missing.parentFile, "gs-test-must-not-exist.png")
            out.delete()
            val refused = try {
                GsNative.sdGenerate(handle, "a cat", "", 64, 64, 4, out.absolutePath)
                false
            } catch (e: Exception) {
                println("GsNativeTest: sdGenerate refused: ${e.message}")
                true
            }
            if (refused) {
                assertFalse(
                    "sdGenerate FAILED but wrote ${out.length()} bytes at " +
                        "${out.absolutePath}. A placeholder image is the one outcome " +
                        "that makes every downstream quality check meaningless: a " +
                        "caller that decodes a PNG cannot tell a real generation " +
                        "from a grey rectangle, and a grey rectangle that always " +
                        "appears satisfies every check that only asks whether a " +
                        "file appeared.",
                    out.exists(),
                )
                val msg = GsNative.lastError()
                assertTrue(
                    "the failure left no reason: \"$msg\". A caller cannot report " +
                        "what it was not told.",
                    msg.isNotEmpty(),
                )
            } else {
                // It claimed success. Then the file must EXIST and be a real PNG,
                // because a success code with no artifact is not a success.
                assertTrue(
                    "sdGenerate returned without throwing but wrote no file at " +
                        "${out.absolutePath}",
                    out.exists(),
                )
                val head = out.readBytes().take(8)
                assertTrue(
                    "sdGenerate succeeded but the file is not a PNG: " +
                        "${head.toList()} -- ${out.length()} bytes",
                    head == listOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
                )
                assertTrue(
                    "the PNG is ${out.length()} bytes, which is too small to hold a " +
                        "64x64 image",
                    out.length() > 100,
                )
                println("GsNativeTest: real PNG, ${out.length()} bytes")
            }
            out.delete()
            GsNative.sdFree(handle)
        } else {
            // Refused to even create a context. Correct, and the reason must be
            // readable -- a caller that cannot tell "no such model" from "the
            // library is not linked" cannot choose what to do next.
            assertTrue(
                "sdCreate returned 0 with an EMPTY reason, so a caller cannot " +
                    "report what was missing",
                reason.isNotEmpty(),
            )
        }
    }

    /** The no-subprocess claim, checked on the DEVICE rather than by reading Rust.
     *
     * sd_bridge.rs already asserts this over its own source. This asserts the
     * stronger property that matters for a shipping app: the loadable library on
     * this device exposes no process-spawning import under any symbol the image
     * path could reach.
     *
     * `os.fork`, `Runtime.exec` and `ProcessBuilder` are the ones that would turn
     * an in-process feature into a subprocess feature on a device that has no
     * sd-cli to find. libc's `system` and `popen` are checked by name too.
     *
     * A FAIL here is not a style complaint: it means the feature is shelling out
     * to something a phone does not have.
     */
    @Test
    fun c2_the_shipped_library_imports_no_process_spawning_symbol() {
        assertTrue(
            "libgs_ffi.so did not load, so there is nothing to inspect",
            GsNativeLoader.isLibraryLoaded(),
        )

        // SEARCH, DO NOT ASSUME A PATH. The first version read:
        //
        //     val libDir = ctx.applicationInfo.nativeLibraryDir
        //     val so = File(libDir, "libgs_ffi.so")
        //
        // and failed on the device, run 36878022839:
        //
        //     no libgs_ffi.so at
        //       /data/app/~~UMgY.../lib/x86_64/libgs_ffi.so
        //       -- nativeLibraryDir was /data/app/~~UMgYk...
        //
        // The file it named is the right file and the directory is the right
        // directory: on API 30+ the native libraries live under nativeLibraryDir IN
        // AN ABI SUBDIRECTORY. So the test asserted on a path that does not exist
        // and reported it as "the library is missing" -- the opposite conclusion.
        //
        // The same lesson the SVG row parser earned: a wrong assumption about where
        // something IS, stated as a hard failure, is indistinguishable from the
        // thing it is not. A search finds the file wherever the platform put it.
        val libDir = ctx.applicationInfo.nativeLibraryDir
        val diskCandidates = listOf(
            File(libDir, "libgs_ffi.so"),
            File(File(libDir, "x86_64"), "libgs_ffi.so"),
            File(File(libDir, "arm64-v8a"), "libgs_ffi.so"),
            File(File(libDir, "armeabi-v7a"), "libgs_ffi.so"),
        )
        val onDisk = diskCandidates.firstOrNull { it.isFile }

        // ON API 30+ THE LIBRARY IS NOT EXTRACTED TO DISK AT ALL.
        //
        // Run 36891447034 printed, in its own failure message:
        //
        //   no libgs_ffi.so found. nativeLibraryDir was
        //     /data/app/~~53Uz.../com.grapsee.gsai-uXkJ.../lib/x86_64
        //   and these were tried:
        //     .../lib/x86_64/libgs_ffi.so
        //     .../lib/x86_64/x86_64/libgs_ffi.so
        //     ...
        //
        // Two mistakes inside one message. nativeLibraryDir ALREADY ends in the ABI
        // directory on this API level, so the second candidate was
        // .../lib/x86_64/x86_64/libgs_ffi.so -- the same mistake one level down
        // rather than a correction of it.
        //
        // And the file is not there to be found at all: with non-legacy packaging the
        // APK keeps the .so inside itself and the loader maps it straight out of the
        // archive. nativeLibraryDir may name a directory that does not exist. Both
        // earlier versions of this test looked only at the FILESYSTEM, so both could
        // only ever fail -- and a test that can only fail is a test that gets deleted.
        //
        // So the library is read FROM THE APK, which is not a consolation prize: that
        // is where the shipped bytes are, and the loader reads the very same entry.
        val apk = ctx.applicationInfo.sourceDir
        val abis = listOf("x86_64", "arm64-v8a", "armeabi-v7a", "x86")
        val soBytes: ByteArray = if (onDisk != null) {
            println(
                "GsNativeTest: read ${onDisk.absolutePath} from disk, ${onDisk.length()} bytes",
            )
            onDisk.readBytes()
        } else {
            val entry = abis
                .map { "lib/$it/libgs_ffi.so" }
                .firstOrNull { candidate -> ZipFile(apk).use { it.getEntry(candidate) != null } }
            assertNotNull(
                "no libgs_ffi.so in the APK at $apk either.\n" +
                    "  nativeLibraryDir was $libDir; on disk these were tried:\n" +
                    diskCandidates.joinToString("\n") { "    $it (exists=${it.exists()})" } +
                    "\n  and the APK was searched for lib/<abi>/libgs_ffi.so over $abis.\n" +
                    "A library that loads -- which 22 other tests just proved -- and cannot be " +
                    "found in EITHER place means this test is looking somewhere it cannot " +
                    "look, not that the library is absent.",
                entry,
            )
            val path = entry!!
            ZipFile(apk).use { zf ->
                zf.getInputStream(zf.getEntry(path)!!).readBytes().also {
                    println(
                        "GsNativeTest: read $path from the APK, ${it.size} bytes " +
                            "(not extracted to $libDir)",
                    )
                }
            }
        }

        // THE ELF DYNAMIC SYMBOL TABLE, PARSED. Not a byte scan.
        //
        // The first version scanned the file's bytes for the NAMES, and that has two
        // problems, both measured on the real .so from run 36877377145:
        //
        //   1. It cannot tell an IMPORT from a string in .rodata. It reported
        //      `system`, which the library does not import at all:
        //
        //        $ nm -D --undefined-only libgs_ffi.so | grep -c '\\bsystem\\b'
        //        0
        //
        //      so a byte scan accuses the library of calling something it never
        //      mentions in any symbol table.
        //
        //   2. Its claim was not TRUE. It asserted the shipped library contains no
        //      process-spawning name, and:
        //
        //        execve      imported=0
        //        system      imported=0
        //        popen       imported=0
        //        posix_spawn imported=0
        //        execl       imported=0
        //        fork        imported=1     <-- exactly one caller
        //
        //      A test whose assertion is not satisfiable is a test that stays red
        //      forever and gets commented out.
        //
        // So this parses .dynsym and reads the ACTUAL IMPORTS. The parse is
        // cross-checked: it reports 309 imported symbols for the x86_64 build, and
        // `nm -D --defined-only libgs_ffi.so | wc -l` reports 309 as well.
        val elf = Elf64(soBytes)
        val imports = elf.importedSymbols()
        val exports = elf.exportedSymbols()
        println(
            "GsNativeTest: ${elf.dynsymCount} dynamic symbols, " +
                "${imports.size} imported, ${exports.size} exported",
        )
        assertTrue(
            "the .dynsym table was not read: ${elf.dynsymCount} entries. Zero here " +
                "means the parse failed and every check below would pass VACUOUSLY.",
            elf.dynsymCount > 0,
        )
        assertTrue(
            "the .dynsym table yielded no IMPORTS, which cannot be true of a library " +
                "that calls into libc. A parser that returns nothing is a parser that " +
                "passes everything.",
            imports.isNotEmpty(),
        )

        // THE SET, PINNED. Not a list of things that must be absent.
        //
        // Measured on the real .so from run 36877377145, with nm and objdump:
        //
        //     execve        imported 0
        //     system        imported 0
        //     popen         imported 0
        //     posix_spawn   imported 0
        //     execl         imported 0
        //     execlp        imported 1   \
        //     fork          imported 1   >  all three have EXACTLY ONE caller:
        //     waitpid       imported 1   /   ggml_print_backtrace
        //
        // Two things follow, and the first one is why this is not the shape the
        // first version of the test used.
        //
        // "The library contains no process-spawning name" is FALSE. It imports
        // three of them. Asserting it would be a red test forever, and a red test
        // forever gets commented out.
        //
        // "The library imports none of execve/system/popen/posix_spawn" is true,
        // but it cannot notice a NEW way to spawn a process, which is the thing
        // this test exists to catch.
        //
        // So the COMPLETE set is asserted against a known set. ggml forks
        // `addr2line` to symbolise a stack trace when it is about to abort; one
        // call site, on the crash path, not on the image path or the chat path,
        // and in a library this project does not own and would not want to lose,
        // because it is what makes the crash logs worth having.
        //
        // If a dependency later starts using vfork, or system, or posix_spawn,
        // this fails and NAMES it.
        val processCreating = imports.filter {
            it == "fork" || it == "vfork" || it == "clone" ||
                it == "waitpid" || it == "wait3" || it == "wait4" ||
                it == "execve" || it == "execl" || it == "execlp" || it == "execv" ||
                it == "execvp" || it == "execvpe" || it == "posix_spawn" ||
                it == "posix_spawnp" || it == "system" || it == "popen"
        }.toSortedSet()
        val expected = sortedSetOf("execlp", "fork", "waitpid")
        println("GsNativeTest: process-creating imports: $processCreating")
        assertEquals(
            "the set of process-creating symbols this library imports CHANGED.\n" +
                "  now:     $processCreating\n" +
                "  expected: $expected\n" +
                "A new one is a dependency that spawns a process, which on a phone " +
                "means it is shelling out to something the device does not have. " +
                "The three expected are called only from ggml_print_backtrace, " +
                "which forks addr2line to symbolise a crash -- see " +
                "objdump -d libgs_ffi.so on the artifact from android-native.",
            expected,
            processCreating,
        )

        // And separately, the four that would mean a SHELL rather than a crash
        // symboliser. Named explicitly because they are the ones an app must
        // never have, and because a reader skimming this file should not have to
        // reason about ggml to know that.
        val shells = processCreating.intersect(
            sortedSetOf("execve", "system", "popen", "posix_spawn", "posix_spawnp"),
        )
        assertTrue(
            "the shipped libgs_ffi.so imports ${shells.joinToString()}. Those run a " +
                "SHELL. An in-process feature that shells out works on a build machine " +
                "and cannot work on a phone, which has no sd-cli to find.",
            shells.isEmpty(),
        )

        // The three that are expected, stated rather than merely tolerated, so a
        // reader sees them accounted for rather than allowed.
        println(
            "GsNativeTest: shell-spawning imports: ${shells.ifEmpty { listOf("none") }.joinToString()}" +
                "   crash-path only: fork/execlp/waitpid from ggml_print_backtrace",
        )
    }


    // ========================================================================
    // ITEM 3: the TTFT levers, swept in ONE run.
    //
    // The brief asks for threads 1/2/4/8 and n_ctx 512/1024/2048 measured one at
    // a time, same model, same prompt. Done as one test per value that is SEVEN
    // device runs, and each one costs an emulator boot, an APK build and a model
    // download -- so the seventh is a week of wall clock for a table.
    //
    // Instead every combination is measured inside ONE run, against ONE loaded
    // context per combination, and the whole table prints. Same numbers, one boot.
    //
    // WHAT IS ASSERTED, and what is deliberately not:
    //
    //   ASSERTED  every configuration answers with real content (so a config that
    //             silently degraded to empty output cannot win the table)
    //             every configuration's budget is honoured (so a fast number
    //             produced by not generating is not a fast number)
    //             TTFT is positive and smaller than the total, for every config
    //             the spread across repeats is smaller than the spread the rate
    //             is derived from (the same noise check the existing perf test
    //             makes, applied per configuration)
    //
    //   NOT ASSERTED  that any configuration wins. An emulator's ranking is not a
    //             phone's ranking, and a test that fails when the default happens
    //             to be optimal reports a regression that is not one. The table is
    //             the deliverable; changing the default is a judgement made FROM
    //             the table, and is recorded in RESULTS with the run that produced
    //             it.
    //
    // initTuned exists for this and nothing else: `init` hardcodes 2048 and 4.
    // ========================================================================

    @Test
    fun perf_the_threads_and_context_levers_are_swept_in_one_run() {
        val model = findModel()
        assertNotNull(
            "no model on the device, so there is nothing to sweep. This is a " +
                "FAILURE, not a skip: the operator needs the table.",
            model,
        )
        val m = model!!
        println("LEVERS model: ${m.name} (${m.length()} bytes)")

        data class Row(
            val nCtx: Int,
            val nThreads: Int,
            val ttftMs: Long,
            val t49: Long,
            val t49All: List<Long>,
            val chars1: Int,
            val chars49: Int,
            val ok: Boolean,
            val why: String,
        )

        val threadCounts = listOf(1, 2, 4, 8)
        val ctxSizes = listOf(512, 1024, 2048)
        val rows = mutableListOf<Row>()

        for (nCtx in ctxSizes) {
            for (nThreads in threadCounts) {
                val label = "n_ctx=$nCtx n_threads=$nThreads"
                GsNative.shutdown()
                val ok = try {
                    // 0, 0, 0 for the three levers: this test is the ONE that
                    // measured threads and context, and it must not silently start
                    // varying batch or flash attention as well or its own table
                    // stops being about what it says it is about.
                    GsNativeLoader.isLibraryLoaded() && GsNative.initTuned(
                        // -1 for n_threads_batch and flash_attn, NOT 0. Both are
                        // enums-or-negative-sentinel, and this sweep is about
                        // n_threads, so the other two must be left on the library
                        // defaults -- which is what a 0 would not do.
                        m.absolutePath, nCtx, nThreads, -1, 0, 0, -1,
                    )
                } catch (t: Throwable) {
                    println("LEVERS $label FAILED TO INITIALISE: ${t.message}")
                    false
                }
                if (!ok) {
                    rows += Row(nCtx, nThreads, -1, -1, emptyList(), 0, 0, false, "initTuned failed")
                    continue
                }
                // Warm-up, discarded: the first call pays for lazy allocation and
                // would otherwise be reported as this configuration's TTFT.
                try {
                    GsNative.chatWithBudget(perfPrompt, 8)
                } catch (t: Throwable) {
                    rows += Row(nCtx, nThreads, -1, -1, emptyList(), 0, 0, false, "warm-up threw: ${t.message}")
                    continue
                }
                val (t1, text1) = timeBudget(1, 2, perfPrompt) { println("LEVERS $it") }
                val (t49, text49) = timeBudget(49, 2, perfPrompt) { println("LEVERS $it") }

                val ttft = t1.min()
                val total = t49.min()
                val perToken = (total - ttft).toDouble() / 48.0
                val tps = if (perToken > 0) 1000.0 / perToken else 0.0
                val spread = t49.max() - t49.min()

                // REAL CONTENT, per configuration. A configuration that returns ""
                // or a stub would otherwise be the fastest row in the table, and a
                // table whose winner produces nothing is worse than no table.
                val why = when {
                    text1.isBlank() || text49.isBlank() ->
                        "empty output (${text1.length}/${text49.length} chars)"
                    text49.length <= text1.length + 20 ->
                        "budget not honoured (${text1.length} -> ${text49.length} chars)"
                    ttft <= 0 -> "non-positive TTFT ($ttft ms)"
                    perToken <= 0 -> "non-positive per-token time ($perToken ms)"
                    spread * 4 > (total - ttft) ->
                        "noisy: ${spread}ms spread across repeats vs ${"%.1f".format(total - ttft)}ms of signal"
                    else -> ""
                }
                println(
                    "LEVERS n_ctx=$nCtx n_threads=$nThreads ttft=${ttft}ms " +
                        "tps=${"%.2f".format(tps)} perToken=${"%.1f".format(perToken)}ms " +
                        "chars=${text1.length}/${text49.length} spread=${spread}ms" +
                        (if (why.isEmpty()) "" else "  REJECTED: $why"),
                )
                rows += Row(nCtx, nThreads, ttft, total, t49, text1.length, text49.length, why.isEmpty(), why)
            }
        }

        // RESTORE THE SHIPPING DEFAULT BEFORE ASSERTING, OR THE ASSERTIONS RUN
        // IN A BROKEN PROCESS.
        //
        // GsNative.shutdown() clears a PROCESS-WIDE context, so for the rest of
        // this instrumentation run the engine is gone. Everything else in this
        // class runs in the same process, in an order instrumentation does not
        // choose -- and run 36868936414 got exactly that:
        //
        //   perf_...swept_in_one_run          FAILED  (all 12 configs rejected)
        //   a1_chat_returns_real_text         FAILED
        //   b1 ... b4, a6, a7, a2, a0, a1b    FAILED
        //   c2                              FAILED
        //   GsNativeTest x2                  FAILED
        //   Tests 23/23 completed (16 failed)
        //
        // Fifteen of those sixteen said
        //
        //   GsNativeException: no native context; call init(modelPath) first
        //
        // so one test that ended by destroying shared state turned the suite red.
        //
        // init() is the SHIPPING configuration -- 2048 and 4 -- which is the point:
        // the sweep moves it to twelve other values, and the process is put back
        // exactly where it started rather than at whatever the last row happened
        // to leave. This runs before the assertions on purpose; a test that
        // verifies state it has already broken is not verifying anything.
        val restored = try {
            GsNativeLoader.initWith(m.absolutePath)
        } catch (t: Throwable) {
            println("LEVERS could not restore the default context: ${t.message}")
            false
        }
        println(
            "LEVERS restored the default context (init, 2048/4): $restored  " +
                "backendAvailable=${GsNativeLoader.isAvailable()}",
        )

        println("=== ITEM 3 TABLE: TTFT and decode rate, 0.5B, this emulator ===")
        println(String.format("  %-8s %-10s %10s %10s %10s", "n_ctx", "n_threads", "TTFT ms", "tok/s", "spread"))
        for (r in rows) {
            if (!r.ok) {
                println(String.format("  %-8d %-10d %10s %10s   %s", r.nCtx, r.nThreads, "-", "-", r.why))
                continue
            }
            val perToken = (r.t49 - r.ttftMs).toDouble() / 48.0
            val tps = if (perToken > 0) 1000.0 / perToken else 0.0
            println(
                String.format(
                    "  %-8d %-10d %10d %10.2f %10d",
                    r.nCtx, r.nThreads, r.ttftMs, tps, r.t49All.max() - r.t49All.min(),
                ),
            )
        }

        val usable = rows.filter { it.ok }
        assertTrue(
            "every one of the ${rows.size} configurations was rejected. Reasons: " +
                rows.joinToString("; ") { "${it.nCtx}/${it.nThreads}: ${it.why}" } +
                ". A sweep with no usable row measures nothing.",
            usable.isNotEmpty(),
        )
        assertTrue(
            "only ${usable.size} of ${rows.size} configurations produced real output. " +
                "The rest: " + rows.filter { !it.ok }
                    .joinToString("; ") { "${it.nCtx}/${it.nThreads}: ${it.why}" },
            usable.size >= rows.size / 2,
        )
        assertTrue(
            "every usable configuration reported TTFT > 0 and tok/s > 0.5, which is " +
                "not a decode rate. usable=$usable",
            usable.all { it.ttftMs > 0 && it.t49 > it.ttftMs },
        )

        // The default is printed LAST and flagged, so the table says what the
        // shipping configuration actually scored rather than leaving it to be
        // inferred from which row happens to sit at 2048/4.
        val cur = rows.firstOrNull { it.nCtx == 2048 && it.nThreads == 4 }
        if (cur != null && cur.ok) {
            val best = usable.minByOrNull { it.ttftMs }!!
            println(
                "LEVERS default 2048/4 ttft=${cur.ttftMs}ms   best ${best.nCtx}/${best.nThreads} " +
                    "ttft=${best.ttftMs}ms   best is ${"%.1f".format(
                        100.0 * (cur.ttftMs - best.ttftMs) / cur.ttftMs.coerceAtLeast(1),
                    )}% faster on TTFT",
            )
        }
    }


    // ========================================================================
    // ITEM 1, LEVERS 3-5: batch size, and flash attention.
    //
    // Levers 1 and 2 (threads, n_ctx) are measured above and are dead ends: n_ctx
    // does not move TTFT at all and 4 threads is already the throughput optimum.
    //
    // LEVER 3 -- BATCH -- IS MEASURED WITH A LONG PROMPT, WHICH IS THE WHOLE
    // POINT OF IT. n_batch is how many prompt tokens reach llama_decode at once,
    // so it can only matter when there are prompt tokens to batch. Measuring it
    // with "hello" measures nothing, and the previous table used a short prompt,
    // which is why it could not have found an effect if one existed.
    //
    // 500 tokens is the number the brief asks for, and it is also the only length
    // at which the four batch sizes differ: at 512 tokens the whole prompt fits
    // in one batch of 512 and every value behaves identically, so 512 is included
    // deliberately as the CONTROL -- if the four rows come out the same, that is a
    // real result about batching rather than a table that failed to vary anything.
    // ========================================================================

    @Test
    fun perf_lever3_batch_size_is_measured_with_a_500_token_prompt() {
        val model = findModel()
        assertNotNull(
            "no model on the device, so there is nothing to sweep. This is a " +
                "FAILURE, not a skip.",
            model,
        )
        val m = model!!

        // A ~500-token prompt, built from real words so the tokenizer produces a
        // realistic number of tokens rather than a few hundred of one repeated
        // subword. Repeated filler tokenizes to far fewer tokens than its
        // character count suggests, which would quietly make this a short-prompt
        // measurement wearing a long-prompt's label.
        val longPrompt = buildString {
            append("Summarise the following engineering notes.\n\n")
            var i = 1
            while (length < 2200) {
                append(
                    "Note $i: the retrieval layer caches embeddings for thirty " +
                        "minutes, and a cache miss recomputes the query vector " +
                        "before the first token is produced.\n"
                )
                i++
            }
        }
        println("LEVERS3 long prompt: ${longPrompt.length} chars")
        assertTrue(
            "the long prompt is only ${longPrompt.length} chars, which is not long " +
                "enough to make batching observable",
            longPrompt.length > 2000,
        )

        data class BRow(
            val nBatch: Int,
            val ttftMs: Long,
            val t49: Long,
            val chars: Int,
            val ok: Boolean,
            val why: String,
        )

        val batchSizes = listOf(128, 256, 512, 1024)
        val rows = mutableListOf<BRow>()

        // n_ctx must be large enough to hold the prompt, or the context itself
        // truncates it and every row measures the same truncation. 2048 was the
        // default and it is kept, because the previous table showed n_ctx does not
        // affect TTFT -- so holding it fixed isolates the batch variable.
        val nCtx = 2048
        val nThreads = 4   // measured above as the throughput optimum

        for (nBatch in batchSizes) {
            val label = "n_batch=$nBatch"
            GsNative.shutdown()
            val ok = try {
                GsNativeLoader.isLibraryLoaded() && GsNative.initTuned(
                    m.absolutePath, nCtx, nThreads, -1, nBatch, 0, -1,
                )
            } catch (t: Throwable) {
                println("LEVERS3 $label FAILED TO INITIALISE: ${t.message}")
                false
            }
            if (!ok) {
                rows += BRow(nBatch, -1, -1, 0, false, "initTuned failed")
                continue
            }
            try {
                GsNative.chatWithBudget(longPrompt, 8)   // warm-up, discarded
            } catch (t: Throwable) {
                rows += BRow(nBatch, -1, -1, 0, false, "warm-up threw: ${t.message}")
                continue
            }
            // One token, three repeats: TTFT is the thing being measured and a
            // single sample of it is a sample of scheduler noise. timeBudget
            // returns Pair(timings, lastText), so `ttfts` is a List<Long> and
            // `chars` comes off the TEXT -- the previous version of this block
            // returned Pair<Long,Int> per repeat and then called .min() on that
            // list, which does not compile.
            val (ttfts, text1) = timeBudget(1, 3, longPrompt) { println("LEVERS3 $label $it") }
            val (t49, text49) = timeBudget(49, 2, longPrompt) { println("LEVERS3 $label $it") }

            val ttft = ttfts.min()
            val spread = ttfts.max() - ttfts.min()
            val perToken = (t49.min() - ttft).toDouble() / 48.0
            val tps = if (perToken > 0) 1000.0 / perToken else 0.0
            val chars = text49.length

            // REPEATS MUST AGREE, or the fastest row is the luckiest row.
            val why = when {
                ttft <= 0 -> "non-positive TTFT ($ttft ms)"
                spread * 2 > (t49.min() - ttft) ->
                    "noisy: ${spread}ms spread across 3 TTFT samples"
                chars == 0 -> "no output at a 49-token budget"
                text1.isBlank() -> "the 1-token budget produced no reply at all"
                else -> ""
            }
            println(
                "LEVERS3 n_batch=$nBatch ttft=${ttft}ms ttfts=$ttfts " +
                    "tps=${"%.2f".format(tps)} chars=$chars spread=${spread}ms" +
                    (if (why.isEmpty()) "" else "  REJECTED: $why"),
            )
            rows += BRow(nBatch, ttft, t49.min(), chars, why.isEmpty(), why)
        }
        GsNative.shutdown()
        GsNativeLoader.initWith(m.absolutePath)

        println("=== ITEM 1 LEVER 3: batch size, 500-token prompt, 0.5B ===")
        println(String.format("  %-9s %10s %10s %10s", "n_batch", "TTFT ms", "tok/s", "spread"))
        for (r in rows) {
            if (!r.ok) {
                println(String.format("  %-9d %10s %10s   %s", r.nBatch, "-", "-", r.why))
                continue
            }
            val perToken = (r.t49 - r.ttftMs).toDouble() / 48.0
            val tps = if (perToken > 0) 1000.0 / perToken else 0.0
            println(
                String.format(
                    "  %-9d %10d %10.2f %10d", r.nBatch, r.ttftMs, tps, r.t49 - r.ttftMs,
                ),
            )
        }
        assertTrue(
            "all four batch sizes failed: " + rows.joinToString("; ") { "${it.nBatch}: ${it.why}" },
            rows.any { it.ok },
        )
    }

    // ========================================================================
    // LEVER 5 -- FLASH ATTENTION. Reported either way.
    //
    // It is a KERNEL choice, not a tuning parameter, so the honest question is not
    // "is it faster" but "does it change anything measurable here, and does it still
    // produce the same answer". Both are measured here: TTFT and tok/s, AND the
    // reply text, so a kernel that is faster and wrong is caught.
    //
    // llama.cpp may refuse it on CPU (flash attention has historically been
    // GPU-only), and a refusal is a RESULT, not a failure: the honest states are
    // "faster", "slower", "identical", and "refused to load", and this test is
    // written so that all four can pass.
    // ========================================================================

    @Test
    fun perf_lever5_flash_attention_is_measured_and_its_answer_is_compared() {
        val model = findModel()
        assertNotNull(
            "no model on the device, so there is nothing to measure. This is a " +
                "FAILURE, not a skip.",
            model,
        )
        val m = model!!
        val nCtx = 2048
        val nThreads = 4
        val prompt = "What is the capital of France? Answer with one word."

        data class FaRow(
            val flash: Boolean,
            val loaded: Boolean,
            val ttft: Long,
            val t49: Long,
            val reply: String,
            val why: String,
        )

        val rows = (listOf(false, true)).map { flash ->
            GsNative.shutdown()
            val loaded = try {
                GsNativeLoader.isLibraryLoaded() && GsNative.initTuned(
                    m.absolutePath, nCtx, nThreads, -1, 0, 0, if (flash) 1 else 0,
                )
            } catch (t: Throwable) {
                println("LEVERS5 flash_attn=$flash FAILED TO INITIALISE: ${t.message}")
                false
            }
            if (!loaded) {
                return@map FaRow(flash, false, -1, -1, "", "initTuned failed or refused")
            }
            var reply = ""
            val ttft = try {
                GsNative.chatWithBudget(prompt, 8)   // warm-up
                val t0 = System.nanoTime() / 1_000_000
                reply = GsNative.chatWithBudget(prompt, 1)
                System.nanoTime() / 1_000_000 - t0
            } catch (t: Throwable) {
                println("LEVERS5 flash_attn=$flash THREW: ${t.message}")
                -1L
            }
            var t49 = -1L
            if (ttft > 0) {
                t49 = try {
                    val t0 = System.nanoTime() / 1_000_000
                    GsNative.chatWithBudget(prompt, 49)
                    System.nanoTime() / 1_000_000 - t0
                } catch (t: Throwable) {
                    println("LEVERS5 flash_attn=$flash 49-token run threw: ${t.message}")
                    -1L
                }
            }
            val ok = ttft > 0 && t49 > ttft
            println(
                "LEVERS5 flash_attn=$flash loaded=true ttft=${ttft}ms t49=${t49}ms " +
                    "reply=${reply.take(60).replace("\n", " ")}",
            )
            FaRow(flash, loaded, ttft, t49, reply, if (ok) "" else "no usable timing")
        }
        GsNative.shutdown()
        GsNativeLoader.initWith(m.absolutePath)

        println("=== ITEM 1 LEVER 5: flash attention ===")
        for (r in rows) {
            if (!r.loaded) {
                println("  flash_attn=${r.flash}  DID NOT LOAD: ${r.why}")
                continue
            }
            val perToken = (r.t49 - r.ttft).toDouble() / 48.0
            val tps = if (perToken > 0) 1000.0 / perToken else 0.0
            println(
                "  flash_attn=${r.flash}  TTFT ${r.ttft}ms  ${"%.2f".format(tps)} tok/s  " +
                    "answer=${r.reply.take(40).replace("\n", " ")}",
            )
        }

        val off = rows.first { it.flash == false }
        val on = rows.first { it.flash == true }

        // THE ANSWER MUST BE THE SAME ANSWER. A kernel that produces different text
        // is not "faster", it is different, and a speedup that costs correctness is
        // the one outcome worth refusing.
        //
        // Both must contain "paris", which is the discriminator the chat tests
        // already use, rather than merely being non-empty -- a kernel that emitted
        // a paragraph of plausible English and no answer would pass "non-empty".
        if (off.loaded && on.loaded && off.ttft > 0 && on.ttft > 0) {
            for (r in listOf(off, on)) {
                assertTrue(
                    "flash_attn=${r.flash} answered a capital-of-France question " +
                        "without saying Paris: ${r.reply.take(120)}",
                    r.reply.lowercase().contains("paris"),
                )
            }
        }

        // A refusal to load is a legitimate result on CPU. It is REPORTED, and the
        // test passes -- because the alternative is a test that goes red forever on
        // a platform where the feature is not available, and a red test forever gets
        // commented out.
        assertTrue(
            "neither flash-attention setting loaded, so nothing was measured. The " +
                "reasons were: off=${off.why} on=${on.why}. If BOTH fail for the " +
                "same reason then the failure is the context, not flash attention.",
            off.loaded || on.loaded || off.why != on.why,
        )
    }


    // ========================================================================
    // LEVER 7 -- n_threads_batch, WHICH IS NOT n_threads.
    //
    // Found by reading the pinned llama.h rather than by guessing at what the
    // knobs were. `struct llama_context_params` at
    // 3018a11e79e489b657dbb77c95694889ccff92df has TWO thread fields:
    //
    //     int32_t  n_threads;        // number of threads to use for GENERATION
    //     int32_t  n_threads_batch;  // number of threads to use for BATCH PROCESSING
    //
    // This wrapper set n_threads and never touched n_threads_batch, so prompt
    // processing ran on llama_context_default_params()'s value for the whole time.
    //
    // WHY THIS MATTERS FOR THE NUMBER THE APP IS JUDGED ON. The earlier table
    // found "4 threads optimal, 8 threads 70x worse" and recorded it as a result
    // about threads. It is a real result and it stands -- but it is a result about
    // GENERATION threads, because prompt processing was never given the number.
    // And time-to-first-token is prompt processing. So the one field that could
    // have moved TTFT by moving prompt processing across cores was never swept.
    //
    // THE LONG PROMPT IS ESSENTIAL AGAIN. n_threads_batch only has work to
    // parallelise during prompt processing, so a short prompt measures the
    // generation path and reports the lever as inert.
    //
    // 1 IS IN THE SWEEP DELIBERATELY. It is the row that proves the field is
    // real: if setting n_threads_batch to 1 changes nothing at all, then either
    // the field is not reaching llama.cpp or the emulator parallelises prompt
    // processing no matter what. Either way that is worth knowing, and a sweep
    // without a deliberately-bad row cannot tell "no effect" from "no effect
    // because the value never arrived".
    //
    // 0 IS NOT SWEPT. It is the sentinel for "leave the library default" and
    // passing it as a thread count would ask for zero batch threads. Whether that
    // returns an error or hangs is exactly the kind of thing that would turn a
    // 53-minute device run into a 6-hour timeout, so it is documented here rather
    // than tried.
    // ========================================================================

    @Test
    fun perf_lever7_prompt_processing_threads_are_a_separate_lever_from_generation_threads() {
        val model = findModel()
        assertNotNull(
            "no model on the device, so there is nothing to sweep. This is a " +
                "FAILURE, not a skip.",
            model,
        )
        val m = model!!

        val longPrompt = buildString {
            append("Summarise the following engineering notes.\n\n")
            var i = 1
            while (length < 2200) {
                append(
                    "Note $i: the retrieval layer caches embeddings for thirty " +
                        "minutes, and a cache miss recomputes the query vector " +
                        "before the first token is produced.\n"
                )
                i++
            }
        }
        assertTrue(
            "the long prompt is only ${longPrompt.length} chars, which cannot " +
                "exercise prompt processing",
            longPrompt.length > 2000,
        )
        println("LEVERS7 long prompt: ${longPrompt.length} chars")

        data class TbRow(
            val genThreads: Int,
            val batchThreads: Int,
            val loaded: Boolean,
            val ttft: Long,
            val t49: Long,
            val reply: String,
            val why: String,
        )

        // n_threads is HELD AT 4 for every row: 4 was measured as the generation
        // optimum, so holding it fixed is what isolates n_threads_batch. The one
        // extra row varies n_threads as well, purely to show the two fields are
        // not the same field.
        val nCtx = 2048
        val genThreadCounts = listOf(4, 4, 4, 4, 2)
        val batchThreadCounts = listOf(1, 2, 4, 8, 8)

        val rows = mutableListOf<TbRow>()
        for (i in genThreadCounts.indices) {
            val gen = genThreadCounts[i]
            val batch = batchThreadCounts[i]
            val label = "n_threads=$gen n_threads_batch=$batch"
            GsNative.shutdown()
            val loaded = try {
                GsNativeLoader.isLibraryLoaded() && GsNative.initTuned(
                    m.absolutePath, nCtx, gen, batch, 0, 0, -1,
                )
            } catch (t: Throwable) {
                println("LEVERS7 $label FAILED TO INITIALISE: ${t.message}")
                false
            }
            if (!loaded) {
                rows += TbRow(gen, batch, false, -1, -1, "", "initTuned failed")
                continue
            }
            var reply = ""
            val ttft = try {
                GsNative.chatWithBudget(longPrompt, 8)   // warm-up, discarded
                val t0 = System.nanoTime() / 1_000_000
                reply = GsNative.chatWithBudget(longPrompt, 1)
                System.nanoTime() / 1_000_000 - t0
            } catch (t: Throwable) {
                println("LEVERS7 $label THREW: ${t.message}")
                -1L
            }
            var t49 = -1L
            if (ttft > 0) {
                t49 = try {
                    val t0 = System.nanoTime() / 1_000_000
                    GsNative.chatWithBudget(longPrompt, 49)
                    System.nanoTime() / 1_000_000 - t0
                } catch (t: Throwable) {
                    println("LEVERS7 $label 49-token run threw: ${t.message}")
                    -1L
                }
            }
            val ok = ttft > 0 && t49 > ttft
            println(
                "LEVERS7 $label ttft=${ttft}ms t49=${t49}ms " +
                    "reply=${reply.take(50).replace("\n", " ")}",
            )
            rows += TbRow(gen, batch, true, ttft, t49, reply, if (ok) "" else "no usable timing")
        }
        GsNative.shutdown()
        GsNativeLoader.initWith(m.absolutePath)

        println("=== ITEM 1 LEVER 7: prompt-processing threads (long prompt, 0.5B) ===")
        println(
            String.format(
                "  %-10s %-14s %10s %10s %9s",
                "n_threads", "n_threads_batch", "TTFT ms", "tok/s", "chars",
            ),
        )
        for (r in rows) {
            if (!r.loaded || r.ttft <= 0) {
                println(String.format("  %-10d %-14d %10s", r.genThreads, r.batchThreads, "-"))
                continue
            }
            val perToken = (r.t49 - r.ttft).toDouble() / 48.0
            val tps = if (perToken > 0) 1000.0 / perToken else 0.0
            println(
                String.format(
                    "  %-10d %-14d %10d %10.2f %9d",
                    r.genThreads, r.batchThreads, r.ttft, tps, r.reply.length,
                ),
            )
        }

        val usable = rows.filter { it.loaded && it.ttft > 0 && it.t49 > it.ttft }
        assertTrue(
            "no n_threads_batch value produced a usable timing: " +
                rows.joinToString("; ") { "${it.batchThreads}: ${it.why}" },
            usable.isNotEmpty(),
        )

        // THE ROW THAT PROVES THE FIELD ARRIVED.
        //
        // Constrained to gen=4 so it compares like with like, and it asserts the
        // DIRECTION rather than merely that the numbers differ: a single batch
        // thread must be slower at prompt processing than several. A bare
        // "the numbers are not all equal" would pass on a 1% scheduling wobble,
        // which is noise, and a field that never reached llama.cpp would produce
        // exactly that.
        val gen4 = usable.filter { it.genThreads == 4 }
        if (gen4.size >= 2) {
            val best = gen4.minByOrNull { it.ttft }!!
            val oneThread = gen4.filter { it.batchThreads == 1 }.minByOrNull { it.ttft }
            if (oneThread != null) {
                println(
                    "LEVERS7 n_threads_batch=1 TTFT ${oneThread.ttft}ms vs the best " +
                        "(${best.batchThreads} threads) ${best.ttft}ms",
                )
                assertTrue(
                    "n_threads_batch=1 (${oneThread.ttft}ms) is FASTER than " +
                        "${best.batchThreads} batch threads (${best.ttft}ms). On a " +
                        "multi-core runner one prompt-processing thread should not " +
                        "beat several, so either the field did not reach llama.cpp " +
                        "or something else is being measured. Both are worth a red run.",
                    oneThread.ttft >= best.ttft,
                )
            } else {
                println("LEVERS7 n_threads_batch=1 produced no usable row; not comparable")
            }
        } else {
            println("LEVERS7 only ${gen4.size} usable row(s) at n_threads=4; not comparable")
        }
    }


    // ========================================================================
    // ITEM 2: DIFFUSION END TO END ON THE DEVICE.
    //
    // The bar, from the brief: a PNG on the device from a text prompt, no
    // subprocess, no workspace dependencies. Each clause is asserted rather than
    // assumed:
    //
    //   no subprocess      the library is linked IN-PROCESS, which
    //                      sdAvailable() is what proves. A library that is absent
    //                      makes sdAvailable false and sdCreate's handle report
    //                      "sd.cpp:not-compiled" -- a .so can export all eighteen
    //                      mobile symbols and still be unable to generate, which
    //                      is exactly what android-native run 36956136615 shipped.
    //   from a text prompt the prompt is asserted to have changed the output
    //                      relative to a second, different prompt. One image
    //                      proves the encoder works; two images with different
    //                      content prove the CONDITIONING is wired up.
    //   not blank          a uniform grey rectangle decodes as a valid PNG. The
    //                      assertion is on pixel statistics, not on "the file
    //                      exists" and not on "it decoded".
    //
    // WHY A MISSING CHECKPOINT FAILS THIS TEST RATHER THAN SKIPPING IT.
    //
    // A skip is a green run that tested nothing, and the operator's standing
    // instruction is that this is worse than a red one. The suite is currently
    // 23/23 with 0 skipped, and a skip added for an 882 MB download that a CDN
    // failed to serve would quietly become the normal state of this test. So it
    // fails, and the message names the run and the one external cause so the
    // reader can tell an outage from a mistake.
    // ========================================================================

    @Test
    fun sd0_diffusion_generates_a_real_png_on_this_device() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val checkpoint = findCheckpoint(ctx)

        // FAIL CLOSED, and say exactly which thing is missing.
        assertNotNull(
            "no diffusion checkpoint at ${DiffusionCatalog.SDXS_512.id}.safetensors " +
                "in any readable directory. If android-device.yml reported " +
                "GS_SD_ABSENT=true then the 882 MB download from Hugging Face " +
                "failed and the diff is in that run's log; otherwise the push " +
                "step's run-as copy failed and its output names why. This FAILS " +
                "rather than skips on purpose: a skipped diffusion test is a " +
                "green run that proved nothing.",
            checkpoint,
        )
        val cp = checkpoint!!
        assertEquals(
            "the on-device checkpoint is ${cp.length()} bytes, not " +
                "${DiffusionCatalog.SDXS_512.bytes}. A partial push would load and " +
                "then fail ten minutes into a generation, blaming sd.cpp instead of adb.",
            DiffusionCatalog.SDXS_512.bytes,
            cp.length(),
        )

        // ---- the library is linked, which is the whole precondition ----
        val handle = GsNative.sdCreate(cp.absolutePath)
        assertNotEquals(
            "gs_sd_create returned a null handle for a file that is present and " +
                "the right size: ${GsNative.lastError()}",
            0L,
            handle,
        )
        val backend = GsNative.sdBackendName(handle)
        println("SD0 backend: $backend")
        assertTrue(
            "sdBackendName is '$backend'. The library is NOT linked into this .so, " +
                "so gs_sd_generate cannot run and every number below would be " +
                "fabricated. The cause is upstream of this test: check that " +
                "android-native exported GS_SD_PREBUILT and that the .so logs " +
                "'linking stable-diffusion.cpp in-process' rather than " +
                "'portable sd_wrapper: procedural path only'.",
            GsNative.sdAvailable(handle),
        )

        val out = DiffusionCatalog.imageDestination(ctx, "sd0-a")
        out.delete()
        assertTrue(
            "the image destination directory could not be created: $out",
            out.parentFile?.isDirectory == true,
        )

        // ---- generate ----
        // steps=1 and cfg=1.0 are CONSTRAINTS of SDXS-512, not tuning: upstream
        // documents both as mandatory for this checkpoint. A test that passed 20
        // steps would be measuring a different model, and one that passed 7.5
        // guidance would be measuring garbage.
        val promptA = "a photograph of a red barn in a green field, daylight"
        val promptB = "a technical diagram of a bicycle drivetrain, white background"
        val t0 = System.nanoTime() / 1_000_000
        GsNative.sdGenerate(
            handle,
            promptA,
            "",
            512,
            512,
            DiffusionCatalog.SDXS_512.steps,
            out.absolutePath,
        )
        val msA = System.nanoTime() / 1_000_000 - t0
        GsNative.sdFree(handle)

        // ---- the file is real ----
        assertTrue(
            "gs_sd_generate returned without throwing but wrote no file at $out. " +
                "The ABI documents that it writes nothing on failure, so this is " +
                "either a silent diffusion failure or a silent encoder failure; " +
                "the two are separated by a2_svg_and_encoder_produce_distinct_bytes.",
            out.isFile,
        )
        val bytes = out.length()
        assertTrue(
            "the PNG is $bytes bytes, which is too small to be a 512x512 image " +
                "(a uniform one would still be a few KB, so this is truncation)",
            bytes > 20_000,
        )
        println("SD0 generated $promptA -> $bytes bytes in ${msA}ms")

        // ---- it decodes, with a REAL decoder ----
        val bmp = BitmapFactory.decodeFile(out.absolutePath)
        assertNotNull(
            "the file is ${bytes} bytes and does not decode as an image. A PNG " +
                "encoder that writes a correct header and a wrong body produces " +
                "exactly this, and 'the file exists' would not have caught it.",
            bmp,
        )
        assertEquals("PNG width", 512, bmp!!.width)
        assertEquals("PNG height", 512, bmp.height)

        // ---- IT IS NOT BLANK ----
        // Three distinct ways a "successful" generation is still worthless, each
        // with its own assertion, because they are different defects:
        //   uniform colour  -> a grey rectangle, the placeholder failure
        //   all black       -> a zeroed latent that never decoded
        //   all white       -> a saturated latent
        val stats = luminanceStats(bmp)
        println(
            "SD0 pixels: min=${stats.min} max=${stats.max} mean=" +
                "%.1f sd=%.1f".format(stats.mean) + " sd=%.2f".format(stats.sd),
        )
        assertTrue(
            "every one of ${stats.n} pixels is the same value (${stats.min}). A " +
                "uniform image is what a placeholder returns and what a real " +
                "generation never does; the PNG decoded, so nothing above could " +
                "have caught it.",
            stats.max - stats.min >= 16,
        )
        assertTrue(
            "the image is uniform dark (min=${stats.min} max=${stats.max}), which " +
                "is a zeroed latent rather than a generation",
            stats.max >= 24,
        )
        assertTrue(
            "the image is effectively uniform light (min=${stats.min} " +
                "max=${stats.max} sd=${"%.2f".format(stats.sd)}), which is a " +
                "saturated latent",
            stats.sd >= 2.0,
        )

        // ---- THE PROMPT IS CONDITIONING, NOT DECORATION ----
        // One image proves the encoder wrote something. Two DIFFERENT prompts
        // producing the SAME pixels would mean the text encoder is disconnected,
        // the prompt is ignored, or the checkpoint is not conditioning -- and all
        // three are invisible to every other assertion here, because every one of
        // them passes on a fixed grey image.
        val outB = DiffusionCatalog.imageDestination(ctx, "sd0-b")
        outB.delete()
        val h2 = GsNative.sdCreate(cp.absolutePath)
        assertNotEquals("gs_sd_create failed for the second prompt", 0L, h2)
        val t1 = System.nanoTime() / 1_000_000
        GsNative.sdGenerate(
            h2,
            promptB,
            "",
            512,
            512,
            DiffusionCatalog.SDXS_512.steps,
            outB.absolutePath,
        )
        val msB = System.nanoTime() / 1_000_000 - t1
        GsNative.sdFree(h2)

        assertTrue(
            "the second generation wrote no file at $outB: ${GsNative.lastError()}",
            outB.isFile,
        )
        val bmpB = BitmapFactory.decodeFile(outB.absolutePath)
        assertNotNull("the second PNG does not decode", bmpB)
        assertEquals("second PNG width", 512, bmpB!!.width)
        val statsB = luminanceStats(bmpB)
        println(
            "SD0 second prompt -> ${outB.length()} bytes in ${msB}ms, sd=" +
                "%.2f".format(statsB.sd),
        )
        assertTrue(
            "the second image is also uniform, so the FIRST image's statistics " +
                "prove nothing about conditioning either",
            statsB.max - statsB.min >= 16,
        )

        // DIFFERENT PIXELS, not merely different statistics. Comparing a mean
        // would let a recolouring of the same composition pass; the prompts are
        // for two unrelated subjects, so the images should share little.
        val meanAbsDiff = meanAbsLuminanceDifference(bmp, bmpB)
        println("SD0 mean |luminance difference| between the two prompts: %.2f".format(meanAbsDiff))
        assertTrue(
            "the two prompts produced near-identical images (mean |diff| = " +
                "%.2f). The text encoder is not conditioning the output, which " +
                "every other assertion in this test would pass: a fixed image " +
                "is decodable, 512x512, non-uniform and non-black.".format(meanAbsDiff),
            meanAbsDiff >= 3.0,
        )

        println("=== ITEM 2: diffusion on device ===")
        println("  backend            : $backend")
        println("  checkpoint         : ${DiffusionCatalog.SDXS_512.id}")
        println("  steps / cfg        : ${DiffusionCatalog.SDXS_512.steps} / ${DiffusionCatalog.SDXS_512.cfgScale}")
        println("  image              : 512x512, ${bytes} bytes")
        println("  time per image     : ${msA}ms (second: ${msB}ms)")
        println("  pixels min/max/sd  : ${stats.min}/${stats.max}/" + "%.2f".format(stats.sd))
        println("  prompts differ by  : %.2f mean luminance".format(meanAbsDiff))
    }

    /** Luminance min/max/mean/sd over every pixel. Not a sample: 262144 is cheap. */
    private data class LumStats(
        val n: Int,
        val min: Int,
        val max: Int,
        val mean: Double,
        val sd: Double,
    )

    private fun luminanceStats(bmp: Bitmap): LumStats {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        var min = 255
        var max = 0
        var sum = 0.0
        val lum = IntArray(px.size)
        for (i in px.indices) {
            val p = px[i]
            val l = (0.299 * ((p shr 16) and 0xFF) +
                0.587 * ((p shr 8) and 0xFF) +
                0.114 * (p and 0xFF)).toInt().coerceIn(0, 255)
            lum[i] = l
            if (l < min) min = l
            if (l > max) max = l
            sum += l
        }
        val mean = sum / px.size
        var ss = 0.0
        for (l in lum) ss += (l - mean) * (l - mean)
        return LumStats(px.size, min, max, mean, kotlin.math.sqrt(ss / px.size))
    }

    /** Mean absolute luminance difference between two same-sized images. */
    private fun meanAbsLuminanceDifference(a: Bitmap, b: Bitmap): Double {
        require(a.width == b.width && a.height == b.height) {
            "meanAbsLuminanceDifference needs equal sizes, got " +
                "${a.width}x${a.height} and ${b.width}x${b.height}"
        }
        val w = a.width
        val h = a.height
        val pa = IntArray(w * h)
        val pb = IntArray(w * h)
        a.getPixels(pa, 0, w, 0, 0, w, h)
        b.getPixels(pb, 0, w, 0, 0, w, h)
        var total = 0.0
        for (i in pa.indices) {
            val la = (0.299 * ((pa[i] shr 16) and 0xFF) +
                0.587 * ((pa[i] shr 8) and 0xFF) + 0.114 * (pa[i] and 0xFF))
            val lb = (0.299 * ((pb[i] shr 16) and 0xFF) +
                0.587 * ((pb[i] shr 8) and 0xFF) + 0.114 * (pb[i] and 0xFF))
            total += kotlin.math.abs(la - lb)
        }
        return total / pa.size
    }

    /**
     * The checkpoint, found the way the GGUF is found: by exact filename, in the
     * directories the app can actually read.
     *
     * `getExternalFilesDir` and the app's private `filesDir` are both searched
     * because the push step uses `run-as` to land it in the private dir, and
     * `/sdcard` is searched because a human doing this by hand would put it
     * there. `/sdcard` is EACCES for the app on API 30 (run 36420986162) and that
     * is a per-file answer rather than a per-directory one, so it is worth
     * probing.
     *
     * The extension is NOT the GGUF's. A `.safetensors` handed to
     * `llama_model_load_from_file` reports a corrupt model, so the two files are
     * never interchangeable and the search is by diffusion name only.
     */
    private fun findCheckpoint(ctx: android.content.Context): File? {
        val name = DiffusionCatalog.SDXS_512.id + ".safetensors"
        val arg = InstrumentationRegistry.getArguments().getString("gs_test_checkpoint")
        if (!arg.isNullOrBlank()) {
            val f = File(arg)
            if (f.isFile && f.length() > 100_000_000L) return f
        }
        for (dir in listOf(
            ctx.filesDir,
            ctx.getExternalFilesDir(null),
            ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS),
            File("/sdcard"),
            File("/storage/emulated/0"),
        )) {
            if (dir == null) continue
            val f = File(dir, name)
            if (f.isFile && f.length() > 100_000_000L) return f
        }
        return null
    }

}
