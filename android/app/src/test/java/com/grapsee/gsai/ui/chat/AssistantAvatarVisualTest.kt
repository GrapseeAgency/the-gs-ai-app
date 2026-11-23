package com.grapsee.gsai.ui.chat

import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.grapsee.gsai.ui.chat.content.nexlyPearlBackdrop
import com.grapsee.gsai.ui.theme.TheGsAiTheme
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import android.graphics.BitmapFactory

/**
 * Native Roborazzi evidence for real ChatScreen components, not a React preview.
 *
 * Uses the actual UserMessage, AssistantMessage, GsBotAvatar and ComposerRow
 * components. The chat screen routes assistant messages with showIdentity=true;
 * this additionally tests the resulting pixels/semantics on a phone viewport.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AssistantAvatarVisualTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun setup() {
        assumeTrue(System.getProperty("gs.screenshots") == "true")
        File("build/screenshots").mkdirs()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Settings.Global.putFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    @Composable
    private fun nativeChat(working: Boolean) {
        TheGsAiTheme(darkTheme = false) {
            Box(Modifier.fillMaxSize().nexlyPearlBackdrop()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    UserMessage(
                        message = ChatUiMessage(
                            id = "native-user",
                            role = "user",
                            content = "Can you summarise the thread with Acme?",
                            createdAt = "2026-11-23T16:23:00Z"
                        )
                    )
                    Box(Modifier.testTag("native.reply")) {
                        AssistantMessage(
                            message = ChatUiMessage(
                                id = "native-assistant",
                                role = "assistant",
                                content = if (working) "" else
                                    "They only sign annual, Dana approves, and pricing is the same thread as last quarter.",
                                isStreaming = working,
                                createdAt = "2026-11-23T16:24:00Z"
                            ),
                            isSpeaking = false,
                            showIdentity = true,
                            onCopy = {},
                            onRegenerate = {}
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.testTag("native.composer")) {
                        ComposerRow(
                            draftText = { "" },
                            onDraftChange = {},
                            onSend = {},
                            onAttach = {},
                            isStreaming = working,
                            onStop = {},
                            queuedText = { "" },
                            onQueue = {},
                            onUnqueue = {},
                            onVoice = {}
                        )
                    }
                }
            }
        }
    }

    private fun capture(working: Boolean, name: String) {
        compose.setContent { nativeChat(working) }
        val avatar = compose.onNodeWithContentDescription(
            if (working) "Clover bot, working" else "Clover bot, idle"
        )
        avatar.assertIsDisplayed()
        val avatarBounds = avatar.fetchSemanticsNode().boundsInRoot
        val replyBounds = compose.onNodeWithTag("native.reply").fetchSemanticsNode().boundsInRoot
        val composerBounds = compose.onNodeWithTag("native.composer").fetchSemanticsNode().boundsInRoot
        assertTrue("Avatar must be inside the actual reply row", avatarBounds.top >= replyBounds.top &&
            avatarBounds.bottom <= replyBounds.bottom)
        assertTrue("Avatar must be ABOVE the composer, never inside the input capsule",
            avatarBounds.bottom < composerBounds.top)
        val path = "build/screenshots/$name.png"
        compose.onRoot().captureRoboImage(path)
        assertTrue("Native image must have nontrivial painted content", File(path).length() > 20_000)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun completedAssistantHasVisibleAvatarOutsideComposer() =
        capture(false, "android-avatar-reply-393x852")

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun streamingAssistantHasVisibleAvatarOutsideComposer() =
        capture(true, "android-avatar-working-393x852")

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun workingAvatarPaintsDifferentFrames() {
        compose.mainClock.autoAdvance = false
        compose.setContent { nativeChat(true) }
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        val first = "build/screenshots/android-avatar-working-frame-1.png"
        compose.onRoot().captureRoboImage(first)
        compose.mainClock.advanceTimeBy(720)
        compose.waitForIdle()
        val second = "build/screenshots/android-avatar-working-frame-2.png"
        compose.onRoot().captureRoboImage(second)

        val bounds = compose.onNodeWithContentDescription("Clover bot, working")
            .fetchSemanticsNode().boundsInRoot
        val one = BitmapFactory.decodeFile(first)
        val two = BitmapFactory.decodeFile(second)
        var pixelsChanged = 0
        val x0 = bounds.left.toInt().coerceAtLeast(0)
        val y0 = (bounds.top - 12).toInt().coerceAtLeast(0)
        val x1 = bounds.right.toInt().coerceAtMost(one.width)
        val y1 = (bounds.bottom + 12).toInt().coerceAtMost(one.height)
        for (y in y0 until y1) for (x in x0 until x1) {
            if (one.getPixel(x, y) != two.getPixel(x, y)) pixelsChanged++
        }
        assertTrue("Native avatar must PAINT different frames when working; not only declare animation",
            pixelsChanged > 80)
    }
}
