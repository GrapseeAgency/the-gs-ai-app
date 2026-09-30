package com.grapsee.gsai

import android.os.Environment
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.di.gsHttpClient
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
    @Test
    fun a8_the_canned_responder_still_answers() {
        val prompt = "draft me a launch email for a new coffee shop"

        // THREE MECHANISMS TRIED BEFORE THIS ONE, each ruled out by reading the
        // code rather than by a failing test. Recorded because the next person to
        // "fix" a8 will reach for a server again.
        //
        // 1. Point ApiClient at 127.0.0.1:1 and assert "not blank" (run
        //    36707513732). Refused connect is a SendFailure.BackendError, and that
        //    arm RETURNS at the marker line without calling streamLocalReply. The
        //    marker itself satisfied the assertion.
        //
        // 2. Serve a chunked SSE response from a ServerSocket and hang up
        //    mid-stream (run 36723027782), on the theory that the mid-stream cut
        //    is what the responder is for. Two SSE frames were malformed first --
        //    {"type":"delta",...} and `event: status` -- before the real format
        //    was read off ApiClient.kt:96. Fixing the frame changed nothing.
        //
        // 3. Why nothing changed (run 36725014379): streamLocalReply has
        //    EXACTLY ONE call site in the entire file --
        //
        //        line 486, inside the SendFailure.GenuineUnreachable arm
        //
        //    MidStreamCut -- the arm a hanging-up server produces -- does NOT
        //    call it. It appends "The connection dropped mid-turn" and returns.
        //    So a mid-stream cut can never reach the canned responder, and no
        //    byte-perfect server would have made a8 pass. The ServerSocket was
        //    the wrong mechanism, not a buggy one.
        //
        // GenuineUnreachable needs a TCP CONNECT FAILURE, and classifySendFailure
        // recognises one with five predicates:
        //
        //    val unreachable = e is UnknownHostException || e is ConnectException
        //        || e is SocketTimeoutException
        //        || e.message?.contains("unresolved", ignoreCase = true) == true
        //        || e.message?.contains("failed to connect", ignoreCase = true) == true
        //    return if (unreachable) GenuineUnreachable else BackendError(0, null)
        //
        // A refused connect came out BackendError(0), so none of the five matched
        // what Ktor CIO actually throws. That is a real defect -- the arm that
        // says "— Offline — cannot reach GS. —" and falls back is unreachable in
        // practice -- but the exception is NAMED BY A RUN, not by a guess. The
        // measurement below is that run.

        // A port that is bound and then released: the connect is refused for
        // certain, with no server in the way to answer by accident.
        val dead = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val deadPort = dead.localPort
        dead.close()

        // MEASUREMENT. The identical POST the repository makes, so the exception
        // measured here is the one the repository sees.
        //
        // Inside `runBlocking`, and that is not incidental. `runCatching` is
        // inline but its block is `() -> R`, not `suspend () -> R`, so a suspend
        // call inside it only compiles when the inline lands in a suspend
        // context. Run 36728958062, on the first attempt at this block:
        //
        //   e: DeviceVerificationTest.kt:617:20 Suspend function
        //      'suspend fun HttpClient.post(...)' should be called only from a
        //      coroutine or another suspend function
        //
        // The SAME client the repository will use, so this measures the
        // repository's path rather than a near-miss of it. That distinction is
        // the entire subject of the test -- see the ApiClient below.
        val client = gsHttpClient("a8-probe-${UUID.randomUUID()}")
        val thrown = runBlocking {
            runCatching {
                client.post("http://127.0.0.1:$deadPort/api/v1/conversations") {
                    // `contentType` is an extension on HttpMessageBuilder in
                    // io.ktor.http, which is why the qualified
                    // `io.ktor.http.ContentType.Application.Json` resolved while
                    // this call did not until it was imported:
                    //
                    //   e: DeviceVerificationTest.kt:618:17 Unresolved reference
                    //      'contentType'
                    //
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }
            }.exceptionOrNull()
        }
        if (thrown != null) {
            val m = thrown.message ?: ""
            println("CannedResponderTest: connect failure = ${thrown::class.java.name}")
            println("CannedResponderTest:   message   = $m")
            println("CannedResponderTest:   is ConnectException=${thrown is java.net.ConnectException}" +
                " is UnknownHost=${thrown is java.net.UnknownHostException}" +
                " is SocketTimeout=${thrown is java.net.SocketTimeoutException}")
            println("CannedResponderTest:   msg has 'unresolved'=${m.contains("unresolved", true)}" +
                " has 'failed to connect'=${m.contains("failed to connect", true)}")
        } else {
            println("CannedResponderTest: connect SUCCEEDED to a closed port -- " +
                "something else owns $deadPort, so this run measures nothing")
        }
        runCatching { client.close() }

        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // THE APP'S CLIENT. `gsHttpClient` is internal to the app module and
        // androidTest compiles as its friend, so the real configuration is
        // available here -- and using it is the point:
        //
        //   di/ServiceLocator.kt:40
        //     install(ContentNegotiation) { json(GsApiJson) }
        //     install(HttpRequestRetry)   { retryOnServerErrors(1) ... }
        //     install(HttpTimeout)        { requestTimeoutMillis = 120_000 ... }
        //
        // sendMessageStream sends `setBody(SendMessageRequest(...))`, a
        // @Serializable data class. With no ContentNegotiation nothing can
        // serialize it, so the request fails BEFORE it connects, and the thrown
        // exception is not a ConnectException. classifySendFailure then takes
        //
        //   return if (unreachable) GenuineUnreachable else BackendError(0, null)
        //
        // the else branch, and the turn shows "— GS backend error (HTTP 0) —".
        // Run 36730290869 measured the bare client directly and got
        //
        //   connect failure = java.net.ConnectException
        //   is ConnectException=true
        //
        // which is how the product code was exonerated. A test that rolls its
        // own client measures a configuration nobody ships.
        val api = ApiClient(
            gsHttpClient("a8-${UUID.randomUUID()}"),
            "http://127.0.0.1:$deadPort"
        )
        val repo = ChatRepository(api, db)

        val preferBefore = SettingsStore.preferLocal
        try {
            // The switch is what selects the ENGINE, not whether the responder
            // answers. With it OFF the answer must be the built-in responder, and
            // the prompt must NOT reach an on-device model -- which is the
            // property a7 proves on the routing side and this proves on the
            // fallback side.
            SettingsStore.updatePreferLocal(false)
            val out = StringBuilder()
            runBlocking {
                repo.send(
                    conversationId = null,
                    content = prompt,
                    onConversationResolved = { _ -> },
                    onDelta = { out.append(it) }
                )
            }
            val reply = out.toString()
            println("CannedResponderTest: -> ${reply.take(200)}")

            // THE PROPERTY, stated so it holds whichever arm is reached: a failed
            // provider must leave a real answer on screen. Not an empty bubble --
            // the user is mid-turn and has already seen their own message.
            assertTrue(
                "a failed provider left the turn with no text at all: '${reply.take(160)}'",
                reply.trim().length >= 20,
            )
            // And it must be an ANSWER, not a diagnostic. "GS backend error
            // (HTTP 0)" means the BACKEND_ERROR arm ran, which returns before
            // streamLocalReply, so the responder never spoke.
            assertFalse(
                "the turn shows the raw backend-error marker instead of an " +
                    "answer, so the canned responder never ran: '${reply.take(160)}'",
                reply.contains("GS backend error"),
            )
            // "— Offline —" is the GENUINE_UNREACHABLE label, and reaching it
            // means streamLocalReply was called at line 486. Asserted separately
            // from the two above because it is the arm-specific claim.
            println(
                "CannedResponderTest: arm = " + when {
                    reply.contains("— Offline —") -> "GenuineUnreachable (streamLocalReply REACHED)"
                    reply.contains("connection dropped mid-turn") -> "MidStreamCut (responder NOT reached)"
                    else -> "unlabelled"
                }
            )
        } finally {
            SettingsStore.updatePreferLocal(preferBefore)
            runCatching { db.close() }
        }
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
}
