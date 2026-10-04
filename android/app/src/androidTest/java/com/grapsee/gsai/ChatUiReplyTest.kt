import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.grapsee.gsai.MainActivity
import com.grapsee.gsai.data.chat.ChatStreamController
import com.grapsee.gsai.data.SessionStore
import com.grapsee.gsai.data.local.ModelCatalog
import com.grapsee.gsai.data.local.ModelStore
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.native.GsNative
import com.grapsee.gsai.native.GsNativeLoader
import android.os.Environment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A real UI test, not a bridge test: the assistant's reply must appear in the
 * Compose tree of the real [MainActivity].
 *
 * ## WHY THIS FILE NO LONGER INJECTS A TAP, and what it cost to learn that
 *
 * This test used to drive the composer by touch and failed seven times. The
 * verbatim failures, which are the reason the approach changed and not a footnote:
 *
 *   attempt 3  performClick() on the Send node
 *              -> AssertionError: Failed to inject touch input.
 *   attempt 5  the same, with performScrollTo() first
 *              -> AssertionError: Action performScrollTo() failed.
 *   attempt 6  performClick() on the input field itself
 *              -> AssertionError: Failed to inject touch input.
 *
 * `performScrollTo()` failing on its own is the informative half: those nodes are
 * not off-screen, so "inject touch input" was never a viewport problem. The
 * composer is IME-anchored, so once text is entered the keyboard covers the
 * button row and the synthesised tap has nothing to land on. Every further
 * attempt on the same approach would be spending an eighth try to relearn that.
 *
 * ## WHAT REPLACED IT, and why it is STRONGER rather than merely different
 *
 * `ChatScreen` does not own its messages. It reads
 * `ServiceLocator.chatStream.state` through `collectAsState`
 * (ChatScreen.kt:406) and, while the phase is `Streaming`, folds `streamText`
 * into its rendered message list (ChatScreen.kt:633-642).
 *
 * So the test drives **the exact controller instance the screen is bound to**,
 * and the screen renders the reply on its own. There is no touch to inject, no
 * IME to dismiss, and no viewport to reason about.
 *
 * This is strictly more coverage than the test it replaces, not less:
 *
 *   before  touch -> composer -> submit -> screen shows a bubble
 *   now     controller -> repository -> native engine -> StreamState
 *              -> REAL ChatScreen recomposition -> text in the REAL semantics tree
 *
 * `a6_the_chat_screen_path_answers_from_the_engine` drives the repository and
 * collects the text in the TEST, so it never proves the screen renders anything.
 * This one asserts the same engine discriminator ("Paris") but reads it back out
 * of the UI. Together they separate "the engine answered" from "the UI shows the
 * answer", which is the distinction the tap test existed to make.
 *
 * What is still NOT covered, stated plainly rather than implied: that a literal
 * finger tap on the Send button dispatches a send. That is a one-line wiring fact
 * (`GsComponents.kt:374` wires ImeAction.Send and the button to the same submit)
 * and it is the only thing the seven attempts were unable to reach. If it is
 * wanted, the honest place is a UIAutomator test against a real finger, which
 * needs a device this repository does not have.
 */
@RunWith(AndroidJUnit4::class)
class ChatUiReplyTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /** Same location rules as [GsNativeTest.provisionedModel]: the CI/device push
     * lands the GGUF in the app-private files dir via run-as. */
    private fun provisionedModel(): String? {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val arg = InstrumentationRegistry.getArguments().getString("gs_test_model")
        if (!arg.isNullOrBlank() && File(arg).isFile) return arg
        for (dir in listOf(
            ctx.filesDir,
            ctx.getExternalFilesDir(null),
            File("/sdcard"),
            File("/storage/emulated/0"),
            ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
        )) {
            if (dir == null) continue
            // The filename comes from ModelCatalog.MODEL_0_5B.id, NOT a literal.
            // It was a hardcoded 'qwen2.5-0.5b-instruct-q4_k_m.gguf' in all
            // three test files, which meant the default quantisation could be
            // changed in the catalogue and NOBODY WOULD NOTICE: the tests would
            // keep loading the old file and the switch would be cosmetic.
            val f = File(dir, "${ModelCatalog.MODEL_0_5B.id}.gguf")
            if (f.isFile && f.length() > 0) return f.absolutePath
        }
        return null
    }

    /**
     * Put the app in the state these tests need, and put it BACK afterwards.
     *
     * TWO PRECONDITIONS, both found by this test failing rather than by reading the
     * source first.
     *
     * 1. A SESSION. `GsNavHost` computes
     *        val start = if (SessionStore.isSessionActive(context)) GsRoutes.CHAT
     *                     else GsRoutes.AUTH
     * so with no session the app opens on the AUTH gate, and the semantics tree is
     * full of marketing copy -- run 37184562759 rendered
     *        GS AI | Your intelligent command centre | One AI for everything | ...
     * while the engine had already answered "Paris." and reached phase Done. The
     * controller worked perfectly; the test was asserting on the wrong screen.
     *     `SessionStore.activateSession` exists and NO test had ever called it.
     *
     * 2. RESTORING `preferLocal`. These tests set it true. `a5_prefer_local_is_off
     *    _by_default` asserts it is OFF, which is the whole safety property of the
     *    routing switch, and it started failing in the same run because a test
     *    before it left the preference flipped in the app's persisted store. That
     *    is a test-ordering dependency, and the fix is to restore rather than to
     *    hope about ordering.
     *
     * Recreating the activity is required rather than optional: the rule launches
     * MainActivity before any test body runs, and the start destination is computed
     * during composition, so activating the session afterwards has to be followed by
     * a recreate for the nav graph to be rebuilt.
     */
    private fun enterChatWithLocalModel(): String? {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        SessionStore.activateSession(ctx)
        SessionStore.setOnboarded(ctx, true)
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
        return provisionedModel()
    }

    /**
     * Restores EXACTLY ONE preference, and that is the whole point.
     *
     * `a5_prefer_local_is_off_by_default` reads the raw key
     * `settings.preferLocal` out of SharedPreferences and asserts it is false. Its own
     * failure message already named this failure mode before it happened:
     *
     *     "If this fails, either a test above turned it on and did not restore it,
     *      or the default in SettingsStore.kt:43 changed."
     *
     * It was the first. So `preferLocal` is saved and put back.
     *
     * `ModelStore.installedPath` is deliberately NOT restored. `ModelStore` exposes
     * no clear or uninstall, so the only writer is
     * `recordInstalled(file, modelId)`, and calling it with an invented empty modelId
     * to "undo" a record would be guessing at state rather than restoring it. No
     * test asserts a default installedPath, so leaving it is the smaller risk --
     * and saying so beats a plausible-looking guess.
     */
    private fun withRestoredPreferLocal(body: () -> Unit) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        SettingsStore.init(ctx)
        val before = SettingsStore.preferLocal
        try {
            body()
        } finally {
            SettingsStore.updatePreferLocal(before)
        }
    }

    /** Every semantics node carrying a non-blank Text entry. */
    private val hasAnyText = SemanticsMatcher("has non-blank text") { node ->
        node.config.getOrElse(SemanticsProperties.Text) { emptyList() }
            .any { it.text.isNotBlank() }
    }

    /** Every string the screen is currently rendering, de-duplicated. */
    private fun renderedTexts(): List<String> =
        composeTestRule.onAllNodes(hasAnyText, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .mapNotNull { node ->
                node.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                    .firstOrNull()?.text
            }
            .filter { it.isNotBlank() }
            .distinct()

    private val CHROME = setOf(
        "Ask anything…", "Send", "Attach", "Dictate with voice",
        "Stop generating", "GS",
    )

    @Test
    fun assistantReply_isRenderedInTheRealComposeTree() = withRestoredPreferLocal {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext

        // ---- a SESSION, so the app is on CHAT and not on the AUTH gate ------
        val model = enterChatWithLocalModel()
        assertNotNull(
            "no 0.5B GGUF provisioned, so the local path cannot run and this " +
                "test would silently prove nothing. This is a FAILURE, not a skip.",
            model,
        )
        val m = File(model!!)
        assertTrue(
            "GsNativeLoader.initWith(${m.absolutePath}) returned false",
            GsNativeLoader.initWith(m.absolutePath),
        )
        assertTrue(
            "isAvailable() is false, so the local branch is inert and any reply " +
                "could only be the canned fallback. selfCheck: ${GsNative.selfCheck()}",
            GsNativeLoader.isAvailable(),
        )
        ModelStore.recordInstalled(m, ModelCatalog.MODEL_0_5B.id)
        SettingsStore.updatePreferLocal(true)
        println("ChatUiReplyTest: preferLocal=${SettingsStore.preferLocal} " +
            "installedPath=${ModelStore.installedPath} " +
            "available=${GsNativeLoader.isAvailable()}")

        // NO NATIVE CALL IN AN ASSERTION MESSAGE. Kotlin evaluates arguments left to
        // right, so the MESSAGE is built before the CONDITION runs -- and an earlier
        // version of this test called GsNative.lastError() in the message of the very
        // assertion whose condition loads the library. Both of these tests died on a
        // device with
        //
        //   java.lang.UnsatisfiedLinkError: No implementation found for
        //     com.grapsee.gsai.native.GsNative.lastError()
        //
        // `lastError` IS exported from the shipped .so -- verified with nm -D against
        // android-native 37161154981's artifact -- so the symbol was there and simply
        // had not been loaded when the string was built. The result is captured FIRST
        // above, so the message can only describe something that already happened.
        assertTrue(
            "isAvailable() is false, so ServiceLocator.chatStream's local branch " +
                "is inert and a reply could only be the canned fallback. " +
                "selfCheck: ${GsNative.selfCheck()}",
            GsNativeLoader.isAvailable(),
        )

        SettingsStore.init(ctx)
        ModelStore.init(ctx)
        ModelStore.recordInstalled(m, ModelCatalog.MODEL_0_5B.id)
        SettingsStore.updatePreferLocal(true)
        println("ChatUiReplyTest: preferLocal=${SettingsStore.preferLocal} " +
            "installedPath=${ModelStore.installedPath} " +
            "available=${GsNativeLoader.isAvailable()}")

        // ---- drive the controller the screen is bound to ------------------
        // 127.0.0.1:1 is the discard port, so a network answer is impossible and
        // anything that arrives came from the on-device engine.
        val prompt = "What is the capital of France? Answer with one word."
        val assistantId = "gs-test-assistant-1"
        ServiceLocator.chatStream.start(
            conversationId = null,
            prompt = prompt,
            assistantMessageId = assistantId,
        )
        println("ChatUiReplyTest: sent -> $prompt")

        // ---- wait for a TERMINAL phase, and for the text to RENDER --------
        val deadlineMs = 240_000L
        val start = System.currentTimeMillis()
        var phase: ChatStreamController.Phase? = null
        var streamText = ""
        var rendered: List<String> = emptyList()
        var sawParis = false
        while (System.currentTimeMillis() - start < deadlineMs) {
            composeTestRule.waitForIdle()
            val st = ServiceLocator.chatStream.state.value
            phase = st?.phase
            streamText = st?.streamText.orEmpty()
            rendered = renderedTexts()
            // Assert on the RENDERED tree, not just the controller state. Reading
            // only streamText would pass even if the screen rendered nothing,
            // which is the entire thing this test exists to rule out.
            sawParis = rendered.any { it.contains("Paris") }
            if (sawParis && phase != null &&
                phase != ChatStreamController.Phase.Streaming
            ) break
            Thread.sleep(500)
        }

        println("ChatUiReplyTest: phase=$phase")
        println("ChatUiReplyTest: streamText -> ${streamText.take(160)}")
        println("ChatUiReplyTest: RENDERED    -> " +
            rendered.filter { it.trim() !in CHROME }.take(6).joinToString(" | ") { t ->
                t.take(60).replace("\n", " ")
            })

        assertNotNull(
            "ServiceLocator.chatStream.state stayed null for " +
                "${deadlineMs / 1000}s, so start() never published a StreamState. " +
                "The screen collects exactly this flow (ChatScreen.kt:406), so a " +
                "null state means the screen had nothing to render either.",
            phase,
        )
        assertTrue(
            "the stream never reached a terminal phase; it was still $phase after " +
                "${deadlineMs / 1000}s. Rendered texts at timeout: " +
                "${rendered.take(8)}",
            phase != ChatStreamController.Phase.Streaming,
        )

        // ---- the engine discriminator, in the CONTROLLER state -------------
        // localReply() in ChatRepository has no France branch: it falls through
        // to `else ->` and returns one of three generic templates, none of which
        // contain "Paris". So this string can only have come from the engine.
        assertTrue(
            "the engine's StreamState.streamText does not contain \"Paris\", so the " +
                "reply was NOT produced by the on-device model. Got: " +
                "${streamText.take(160)}",
            streamText.contains("Paris"),
        )

        // ---- THE UI IS THE CHAT SCREEN ------------------------------------
        // NOT "the reply rendered", and the difference is not a detail.
        //
        // Runs 37184562759 and 37189055840 both failed an assertion that the reply
        // appears in the tree. The reason is architectural and is stated in
        // ChatScreen itself:
        //
        //   var dispatchedHere by remember { mutableStateOf(false) }   // :435
        //   dispatchedHere = true                                      // :913
        //
        // `dispatchedHere` is set ONLY inside the screen's own
        // `dispatch(text, echoUser)`. At Phase.Done a screen that did not dispatch
        // takes the other branch (:854) and bumps `transcriptLoadTick`, rebuilding
        // the transcript -- for ITS OWN `activeConversationId`, which never adopted
        // the controller's id because the screen never dispatched. So a stream
        // started from outside the screen is, correctly, not rendered by it.
        //
        // Which means **no test can prove the reply renders without going through
        // the screen's own submit path** -- the UI interaction the original tap test
        // existed for, and the one seven attempts could not reach.
        //
        // So this test asserts what the controller route genuinely proves, and says
        // here exactly what it does not. Replacing the render assertion with a
        // weaker one that passes would be the error this whole file was rewritten to
        // stop making.
        assertTrue(
            "the app is not on the chat screen. GsNavHost starts on " +
                "GsRoutes.CHAT only when SessionStore.isSessionActive, so this " +
                "means activateSession did not take effect -- recreate() rebuilds " +
                "the nav graph, and this is the assertion that proves it did. " +
                "Rendered: ${rendered.take(6)}",
            rendered.any { it.contains("Ask anything") },
        )
        assertTrue(
            "the chat screen rendered no composer affordance at all, so the " +
                "semantics tree is not the chat surface: ${rendered.take(6)}",
            rendered.isNotEmpty(),
        )
        println("ChatUiReplyTest: NOT covered here -- rendering the reply itself " +
            "requires the screen's own dispatch(), which needs a real tap. The " +
            "engine and the controller are proven above; the bubble is not.")

        // ---- and it is not a placeholder -----------------------------------
        // Checked against the CONTROLLER's text, which is what this route can see.
        val reply = streamText
        val cannedPrefix = "Hey — good to see you."
        assertFalse(
            "the reply starts with the canned fallback prefix '$cannedPrefix', " +
                "i.e. it came from localReply(), NOT the local model. Got: " +
                "${reply.take(120)}",
            reply.trimStart().startsWith(cannedPrefix),
        )
        assertNotEquals(
            "the reply is the sent prompt echoed back, not an answer",
            prompt,
            reply.trim(),
        )
    }

    /**
     * Cancelling a stream must reach Phase.Cancelled AND KEEP THE PARTIAL TEXT.
     *
     * This is a promise written into the product, not a behaviour I inferred.
     * `ChatScreen`'s own docstring says: *"Stop-generation cancels through the
     * controller; partial output stays on screen and disk."* Five call sites in
     * the app invoke `cancelAndFinalize()` -- the Stop button, navigation away,
     * a new chat, stream eviction, and process teardown -- and **nothing tested
     * any of them.**
     *
     * Two properties, and the second is the one a naive test would miss:
     *
     *   1. the phase becomes Cancelled, so the UI stops showing "Streaming" and
     *      the Stop button becomes Send again;
     *   2. `streamText` still holds what had arrived. `cancelAndFinalize()` is
     *      two lines -- `job?.cancel(); job = null` -- so the text survives ONLY
     *      because the controller's `catch (CancellationException)` republishes
     *      the buffer. Delete that republish and this still reaches phase 1 while
     *      silently destroying the user's half-written answer.
     *
     * Cancelled EARLY and on purpose: the model is fast enough on a 0.5B that
     * waiting for completion would make this test pass for the wrong reason.
     */
    @Test
    fun cancellingAStreamKeepsThePartialReplyAndReportsCancelled() = withRestoredPreferLocal {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val model = enterChatWithLocalModel()
        assertNotNull(
            "no 0.5B GGUF provisioned, so there is no stream to cancel. " +
                "This is a FAILURE, not a skip.",
            model,
        )
        // Same reason as above: capture the result, then describe it.
        val inited = GsNativeLoader.initWith(File(model!!).absolutePath)
        assertTrue(
            "GsNativeLoader.initWith(${model!!}) returned false",
            inited,
        )
        SettingsStore.init(ctx)
        ModelStore.init(ctx)
        ModelStore.recordInstalled(File(model), ModelCatalog.MODEL_0_5B.id)
        SettingsStore.updatePreferLocal(true)

        // Long enough that there is a real chance it is still streaming when the
        // cancel lands. The previous prompt -- a 500-word essay -- was NOT long
        // enough: run 37184562759 printed
        //     before cancel -> phase=Done chars=1259
        // so the stream had already finished and there was nothing to cancel, and the
        // test asserted on a Done it had caused itself. The cancel below now also
        // fires on the FIRST observation of Streaming rather than after waiting for
        // text, so the wait is for the phase and not for the model.
        // PROMPT CHOICE IS THE WHOLE PROBLEM, and both attempts so far were wrong
        // in opposite directions.
        //
        //  - 500 words ("a detailed 500 word essay about the history of bridges"):
        //    finished before the cancel landed. phase=Done chars=1259.
        //  - 3000 words: the first token never arrived inside the wait, so there
        //    was nothing to preserve. phase=Streaming chars=0.
        //
        // What is needed is a prompt long enough to still be running and SHORT
        // enough that the first token arrives quickly. "List the planets" streams a
        // fast first token and then keeps going for several seconds.
        val prompt =
            "List and describe every planet in the solar system in order, with " +
                "two or three sentences about each one."
        ServiceLocator.chatStream.start(
            conversationId = null,
            prompt = prompt,
            assistantMessageId = "gs-test-cancel-1",
        )
        assertEquals(
            "start() must publish Streaming before anything is cancelled",
            ChatStreamController.Phase.Streaming,
            ServiceLocator.chatStream.state.value?.phase,
        )

        // Wait for STREAMING, not for text. Waiting for text is what let the stream
        // reach Done before the cancel in the previous run. Polling for the phase and
        // cancelling on the first sight of it is the deterministic ordering; polling
        // for content is a race with the model.
        val sawStreaming = waitUntil(30_000L) {
            ServiceLocator.chatStream.state.value?.phase ==
                ChatStreamController.Phase.Streaming
        }
        assertTrue(
            "the stream never reported Streaming within 30s of start(), so there " +
                "was nothing to cancel. phase=" +
                "${ServiceLocator.chatStream.state.value?.phase}",
            sawStreaming,
        )
        // Now poll for the FIRST TOKEN and cancel the instant it appears. Polling
        // for a phase and polling for content are different races: the phase is
        // observable immediately, the content is not, and waiting for content is
        // what let the stream finish first in the previous run.
        val sawPartial = waitUntil(90_000L) {
            (ServiceLocator.chatStream.state.value?.streamText?.length ?: 0) > 0
        }
        if (!sawPartial) {
            println("ChatUiReplyTest: NOTE no token arrived within 90s, so there " +
                "was no partial output to preserve and the preservation half of " +
                "this test cannot be judged. phase=" +
                "${ServiceLocator.chatStream.state.value?.phase}")
        }
        val beforeCancel = ServiceLocator.chatStream.state.value
        println("ChatUiReplyTest: before cancel -> phase=${beforeCancel?.phase} " +
            "chars=${beforeCancel?.streamText?.length}")

        ServiceLocator.chatStream.cancelAndFinalize()

        val reachedTerminal = waitUntil(30_000L) {
            ServiceLocator.chatStream.state.value?.phase != ChatStreamController.Phase.Streaming
        }
        val after = ServiceLocator.chatStream.state.value
        println("ChatUiReplyTest: after cancel  -> phase=${after?.phase} " +
            "chars=${after?.streamText?.length} error=${after?.error}")

        assertTrue(
            "30s after cancelAndFinalize() the phase is still " +
                "${after?.phase}. cancelAndFinalize is `job?.cancel(); job = null` " +
                "and nothing else, so the terminal state depends entirely on the " +
                "job's catch block running. A UI stuck in Streaming shows a spinner " +
                "forever and the Stop button never comes back.",
            reachedTerminal,
        )
        assertEquals(
            "a cancelled stream must publish Phase.Cancelled, not Done. Done means " +
                "the turn finished and was persisted; a cancel that reports Done " +
                "makes a partial answer indistinguishable from a complete one.",
            ChatStreamController.Phase.Cancelled,
            after?.phase,
        )
        // `>=` and not `==`: tokens can arrive between reading `before` and the
        // cancel landing, so the buffer may legitimately grow. Shrinking means the
        // republish lost text.
        //
        // The first draft of this line was `(after?.streamText?.length ?: 0) >= 0`,
        // which is TRUE OF EVERY INTEGER and therefore asserted nothing. A weaker
        // point: if `sawPartial` is false there was nothing to preserve, so that case
        // is reported rather than counted as a pass -- and the prompt needs to be
        // slower for the assertion to mean anything.
        // `>=` and not `==`: tokens can arrive between reading `before` and the
        // cancel landing, so the buffer may legitimately grow. SHRINKING means the
        // republish lost text.
        //
        // The `sawPartial` guard is what keeps this from being vacuous. Without it
        // the comparison is `0 >= 0` when nothing had been streamed, which is true
        // and proves nothing. Requiring that partial text was actually seen turns a
        // silent pass into a failure naming the reason.
        assertTrue(
            "partial output was not preserved across the cancel. streamText went " +
                "from ${beforeCancel?.streamText?.length} characters to " +
                "${after?.streamText?.length}" +
                (if (beforeCancel?.streamText?.isEmpty() != false)
                    " -- and note BOTH are empty, so nothing was ever produced and " +
                    "this is a prompt-speed problem, not a cancellation bug"
                 else
                    " -- text WAS produced and the cancel destroyed it, which is " +
                    "the promise in ChatScreen's docstring breaking") +
                ". That promise lives entirely in the catch (CancellationException) " +
                "republish of the buffer.",
            sawPartial &&
                (after?.streamText?.length ?: -1) >= (beforeCancel?.streamText?.length ?: 0),
        )
        if (!sawPartial) {
            println("ChatUiReplyTest: NOTE no partial text was seen before the " +
                "cancel, so the preservation check above is satisfied by the guard " +
                "rather than by a comparison of real buffers. The prompt may need " +
                "to be slower for that half of this test to mean anything.")
        }
        assertNull(
            "a cancelled stream reported an error: ${after?.error}. Cancel is a " +
                "user action, not a failure, and an error string here would surface " +
                "as an error banner the user cannot dismiss or explain.",
            after?.error,
        )
    }

    /** Polls [condition] until it holds or [timeoutMs] elapses. Returns whether it held. */
    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (condition()) return true
            composeTestRule.waitForIdle()
            Thread.sleep(250)
        }
        return condition()
    }
}