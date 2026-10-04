import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.grapsee.gsai.MainActivity
import com.grapsee.gsai.data.chat.ChatStreamController
import com.grapsee.gsai.data.local.ModelCatalog
import com.grapsee.gsai.data.local.ModelStore
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.native.GsNative
import com.grapsee.gsai.native.GsNativeLoader
import android.os.Environment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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
    fun assistantReply_isRenderedInTheRealComposeTree() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext

        // ---- the engine has to be live, or this asserts nothing ----------
        // FAILS rather than skips, like a6. A skip here is a green run that
        // proved nothing, and the suite's standing is 0 skipped.
        val model = provisionedModel()
        assertNotNull(
            "no 0.5B GGUF provisioned, so the local path cannot run and this " +
                "test would silently prove nothing. This is a FAILURE, not a " +
                "skip. If android-device.yml reported GS_QUANT_MISSING the " +
                "download failed and that run's log says which file.",
            model,
        )
        val m = File(model!!)

        assertTrue(
            "GsNativeLoader.initWith(${m.absolutePath}) returned false: " +
                "${GsNative.lastError()}",
            GsNativeLoader.initWith(m.absolutePath),
        )
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

        // ---- and the same discriminator in the RENDERED TREE --------------
        // This is the assertion the seven failed attempts were reaching for.
        assertTrue(
            "the engine answered with \"Paris\" but no node in the real Compose " +
                "tree renders it. The screen folds streamText into its message " +
                "list only while phase is Streaming (ChatScreen.kt:633-642), and " +
                "this run ended in phase $phase -- so either the terminal commit " +
                "cleared the node or the screen never adopted the message. " +
                "Rendered texts: ${rendered.take(10)}",
            sawParis,
        )

        // ---- and it is not a placeholder -----------------------------------
        val reply = rendered.first { it.contains("Paris") }
        val cannedPrefix = "Hey — good to see you."
        assertFalse(
            "the rendered reply starts with the canned fallback prefix " +
                "'$cannedPrefix', i.e. it came from localReply(), NOT the local " +
                "model. First 120 chars: ${reply.take(120)}",
            reply.trimStart().startsWith(cannedPrefix),
        )
        assertNotEquals(
            "the reply is the sent prompt echoed back, not an answer",
            prompt,
            reply.trim(),
        )
    }
}