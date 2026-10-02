package com.grapsee.gsai

import android.os.Environment
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.fetchSemanticsNodes
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Blocker 4 — a real UI test, not a bridge test.
 *
 * Unlike [GsNativeTest], which talks to JNI directly, this one drives the app
 * the way a user does: tap the composer, type "hello", press Send, and wait
 * for an assistant bubble to appear. The assertion that matters is on the
 * RENDERED UI: a non-blank assistant reply must be visible within a bounded
 * time, and it must NOT be the canned greeting [localReply] would fabricate
 * when the on-device engine is absent or the provider failed.
 *
 * Model-backed by construction: with no 0.5B GGUF provisioned the app can only
 * ever render the canned fallback, and a test that "passed" on that would
 * prove nothing. So it SKIPS (visibly, via assumeTrue) exactly like
 * [GsNativeTest.chatReturnsNonEmptyText] — no fabricated pass.
 *
 * Framework: Compose UI test (createAndroidComposeRule), which the androidTest
 * source set did not carry before Blocker 4. See native/docs/ISSUE-LOG.md —
 * the integration is a one-line dependency add to android/app/build.gradle.kts.
 */
@RunWith(AndroidJUnit4::class)
class ChatUiReplyTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /** Same location rules as [GsNativeTest.deviceModel]: the CI/device push
     *  lands the GGUF in the app-private files dir via run-as. */
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
            val f = File(dir, "qwen2.5-0.5b-instruct-q4_k_m.gguf")
            if (f.isFile && f.length() > 0) return f.absolutePath
        }
        return null
    }

    /** Every semantics node carrying a non-blank Text entry. */
    private val hasAnyText = SemanticsMatcher("has non-blank text") { node ->
        node.config.getOrElse(SemanticsProperties.Text) { emptyList() }
            .any { it.text.isNotBlank() }
    }

    private fun visibleTexts(): List<String> =
        composeTestRule.onAllNodes(hasAnyText, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .mapNotNull { node ->
                node.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                    .firstOrNull()?.text
            }

    // Chrome that is on screen independently of any reply — never a reply.
    private val CHROME = setOf(
        "Ask anything…", "Send", "Attach", "Dictate with voice",
        "Stop generating", "hello",
    )

    @Test
    fun sendHello_rendersARealAssistantReply() {
        val model = provisionedModel()
        assumeTrue(
            "no 0.5B GGUF provisioned; the model-backed UI test skips exactly " +
                "like GsNativeTest's chat tests — never a fabricated pass",
            model != null,
        )

        // 1. tap the chat input, 2. type "hello", 3. send it.
        val input = composeTestRule.onAllNodes(hasSetTextAction()).onFirst()
        input.performClick()
        input.performTextInput("hello")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Send").performClick()

        // 4. a real reply bubble with non-blank assistant text within N s.
        val deadlineMs = 180_000L
        val start = System.currentTimeMillis()
        var replies: List<String> = emptyList()
        while (System.currentTimeMillis() - start < deadlineMs) {
            composeTestRule.waitForIdle()
            replies = visibleTexts().filter { it.trim() !in CHROME }
            if (replies.isNotEmpty()) break
            Thread.sleep(500)
        }

        assertTrue(
            "no assistant reply rendered within ${deadlineMs / 1000}s of sending " +
                "'hello'. On-screen texts at timeout: ${visibleTexts().take(10)}",
            replies.isNotEmpty(),
        )

        // 5. The reply is REAL: it is not the canned fallback the app
        //    fabricates when the engine is absent (localReply, ChatRepository.kt)
        //    or the canned offline marker. The local 0.5B model's answer to
        //    "hello" is free-form; the canned one starts with this prefix.
        val reply = replies.first { it.trim() !in CHROME }
        println("ChatUiReplyTest: reply bubble = ${reply.take(120).replace("\n", " ")}")
        val cannedPrefix = "Hey — good to see you."
        assertFalse(
            "the rendered reply starts with the canned fallback prefix " +
                "'$cannedPrefix', i.e. it came from localReply(), NOT the local " +
                "model. First 120 chars: ${reply.take(120)}",
            reply.trimStart().startsWith(cannedPrefix),
        )
        assertNotEquals(
            "the reply is the sent prompt echoed back, not an answer",
            "hello",
            reply.trim(),
        )
    }
}
