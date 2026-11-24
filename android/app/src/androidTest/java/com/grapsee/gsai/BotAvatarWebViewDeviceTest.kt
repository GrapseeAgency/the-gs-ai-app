package com.grapsee.gsai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.grapsee.gsai.data.AssistantAvatarStyle
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.chat.AssistantMessage
import com.grapsee.gsai.ui.chat.ChatUiMessage
import com.grapsee.gsai.ui.chat.ComposerRow
import com.grapsee.gsai.ui.chat.UserMessage
import com.grapsee.gsai.ui.chat.content.nexlyPearlBackdrop
import com.grapsee.gsai.ui.theme.TheGsAiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Uses the actual production transcript and composer, not a fabricated
 * Text("Working") sample. Verify the original renderer's *visible* pixels,
 * presentation cadence and sustained frame changes during a live state
 * transition. Capture the real Android view hierarchy for review.
 */
@RunWith(AndroidJUnit4::class)
class BotAvatarWebViewDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val screenshots get() = File(
        compose.activity.filesDir, "native-avatar-evidence"
    ).apply { mkdirs() }
    private val working = mutableStateOf(false)

    @Composable
    private fun NativeChat() {
        TheGsAiTheme(darkTheme = false) {
            Box(Modifier.fillMaxSize().nexlyPearlBackdrop()) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    UserMessage(
                        message = ChatUiMessage(
                            id = "avatar-user", role = "user",
                            content = "Can you summarise the thread with Acme?",
                            createdAt = "2026-11-23T16:23:00Z"
                        )
                    )
                    Box(Modifier.testTag("native.reply")) {
                        AssistantMessage(
                            message = ChatUiMessage(
                                id = "avatar-assistant", role = "assistant",
                                content = if (working.value) "" else
                                    "They only sign annual, Dana approves, and pricing is the same thread as last quarter.",
                                isStreaming = working.value,
                                createdAt = "2026-11-23T16:24:00Z"
                            ),
                            showIdentity = true,
                            isSpeaking = false,
                            onCopy = {},
                            onRegenerate = {}
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.testTag("native.composer")) {
                        ComposerRow(
                            draftText = { "" }, onDraftChange = {},
                            onSend = {}, onAttach = {}, isStreaming = working.value,
                            onStop = {}, queuedText = { "" }, onQueue = {},
                            onUnqueue = {}, onVoice = {}
                        )
                    }
                }
            }
        }
    }

    private fun findView(view: View, predicate: (View) -> Boolean): View? {
        if (predicate(view)) return view
        if (view is ViewGroup) for (n in 0 until view.childCount) {
            findView(view.getChildAt(n), predicate)?.let { return it }
        }
        return null
    }

    private fun javascript(expression: String): String {
        val latch = CountDownLatch(1)
        var result = ""
        instrumentation.runOnMainSync {
            val browser = findView(compose.activity.window.decorView) { it is WebView }
                as? WebView ?: error("Original WebView is absent")
            browser.evaluateJavascript(expression) {
                result = it
                latch.countDown()
            }
        }
        assertTrue("JavaScript diagnostics timed out", latch.await(10, TimeUnit.SECONDS))
        return result
    }

    private fun diagnostics() = javascript(
        "window.AndroidAvatarFrames && window.AndroidAvatarFrames.diagnostics()"
    )

    private fun presented(): Int =
        Regex("presented=([0-9]+)").find(diagnostics())
            ?.groupValues?.get(1)?.toInt() ?: 0

    private fun awaitPaint() {
        repeat(60) {
            if (presented() > 0) return
            Thread.sleep(250)
        }
        error("No real vendor canvas reached the native ImageView: " + diagnostics() +
            "; " + javascript("window.gsAvatarFrameDiagnostics && window.gsAvatarFrameDiagnostics()"))
    }

    private fun assertPlacement(working: Boolean) {
        val avatar = compose.onNodeWithContentDescription(
            if (working) "Clover bot, working" else "Clover bot, idle"
        )
        avatar.assertIsDisplayed()
        val avatarBounds = avatar.fetchSemanticsNode().boundsInRoot
        val reply = compose.onNodeWithTag("native.reply").fetchSemanticsNode().boundsInRoot
        val composer = compose.onNodeWithTag("native.composer").fetchSemanticsNode().boundsInRoot
        assertTrue("Avatar belongs beside the production assistant reply",
            avatarBounds.top >= reply.top && avatarBounds.bottom <= reply.bottom)
        assertTrue("Avatar cannot be in composer", avatarBounds.bottom < composer.top)
        instrumentation.runOnMainSync {
            val root = compose.activity.window.decorView
            val browser = findView(root) { it is WebView }!!
            val image = findView(root) { it.tag == "gs-original-avatar-image" }!!
            assertEquals("There must not be two superimposed avatar layers",
                0f, browser.alpha, 0f)
            assertTrue("Native avatar must keep its square aspect ratio", image.width == image.height)
        }
    }

    private data class Frame(val full: Bitmap, val crop: Bitmap, val signature: Int, val green: Int)

    private fun captureFrame(): Frame {
        val root = compose.activity.window.decorView
        var full: Bitmap? = null
        var crop: Bitmap? = null
        instrumentation.runOnMainSync {
            val image = findView(root) { it.tag == "gs-original-avatar-image" }
                ?: error("Native original avatar ImageView missing")
            val rootOffset = IntArray(2)
            val imageOffset = IntArray(2)
            root.getLocationOnScreen(rootOffset)
            image.getLocationOnScreen(imageOffset)
            val x = imageOffset[0] - rootOffset[0]
            val y = imageOffset[1] - rootOffset[1]
            val screenshot = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(screenshot))
            full = screenshot
            crop = Bitmap.createBitmap(screenshot, x, y, image.width, image.height)
        }
        val visible = crop!!
        val pixels = IntArray(visible.width * visible.height)
        visible.getPixels(pixels, 0, visible.width, 0, 0, visible.width, visible.height)
        var green = 0
        for (pixel in pixels) {
            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)
            if (g > r + 20 && g > b + 10 && g > 70) green++
        }
        return Frame(full!!, visible, pixels.contentHashCode(), green)
    }

    private fun save(bitmap: Bitmap, name: String) {
        File(screenshots, name).outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private fun assertVisible(frame: Frame) {
        assertTrue("Actual visible native avatar is blank: " + diagnostics(),
            frame.green > 25)
    }

    @Test fun authenticCanvasIsPaintedAndWorkingFramesMove() {
        instrumentation.runOnMainSync {
            SettingsStore.updateAssistantAvatar(AssistantAvatarStyle.CLOVER)
            compose.activity.setContent { NativeChat() }
        }
        assertPlacement(false)
        awaitPaint()
        val idle = captureFrame()
        save(idle.full, "original-avatar-idle.png")
        assertVisible(idle)
        idle.full.recycle()
        idle.crop.recycle()

        // State change in the same mounted real reply; don't create a second
        // WebView and mistake a remount for an animation.
        instrumentation.runOnMainSync { working.value = true }
        assertPlacement(true)
        repeat(20) {
            if (!javascript("window.gsAvatarGetState && window.gsAvatarGetState()")
                    .contains("working")) Thread.sleep(100)
        }
        assertTrue("Native working state failed to reach React",
            javascript("document.querySelector('canvas') && document.querySelector('canvas').dataset.state")
                .contains("working"))
        Thread.sleep(350)

        // Measure unique frames actually presented by the native ImageView.
        // A single differing screenshot is not proof of smooth animation.
        val startCount = presented()
        val start = SystemClock.elapsedRealtime()
        Thread.sleep(2500)
        val fps = (presented() - startCount) * 1000.0 /
            (SystemClock.elapsedRealtime() - start).coerceAtLeast(1)
        println("GS_AVATAR_PRESENTATION_FPS=" + fps + " " + diagnostics())
        assertTrue("Working avatar must sustain at least 20 presented frames/sec; actual=" +
            fps + " " + diagnostics(), fps >= 20.0)

        val signatures = mutableSetOf<Int>()
        for (n in 0 until 12) {
            Thread.sleep(125)
            val frame = captureFrame()
            assertVisible(frame)
            signatures.add(frame.signature)
            save(frame.crop, "avatar-motion-" + n.toString().padStart(2, '0') + ".png")
            if (n == 0) save(frame.full, "original-avatar-working-1.png")
            if (n == 11) save(frame.full, "original-avatar-working-2.png")
            frame.full.recycle()
            frame.crop.recycle()
        }
        println("GS_AVATAR_VISIBLE_UNIQUE_FRAMES=" + signatures.size + "/12")
        assertTrue("Visible avatar froze or only changed once: " + signatures.size + "/12",
            signatures.size >= 10)

        // Verify the same native reply can settle back to original idle.
        instrumentation.runOnMainSync { working.value = false }
        assertPlacement(false)
        Thread.sleep(350)
        assertTrue("Native settled state failed to reach React",
            javascript("document.querySelector('canvas') && document.querySelector('canvas').dataset.state")
                .contains("default"))
        val settled = captureFrame()
        save(settled.full, "original-avatar-settled.png")
        assertVisible(settled)
        settled.full.recycle()
        settled.crop.recycle()
    }
}
