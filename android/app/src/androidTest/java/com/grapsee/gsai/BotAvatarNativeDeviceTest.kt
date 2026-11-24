package com.grapsee.gsai

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import com.grapsee.gsai.data.chat.ChatStreamController
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
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
import com.grapsee.gsai.ui.components.OriginalBotAvatarView
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Real Android window compositor verification, using the production reply
 * and composer components. ActivityScenario avoids Compose idling waits
 * that can deadlock against a perpetually animated chat backdrop.
 * PixelCopy samples the hardware-composited window, not a browser or an atlas preview.
 */
@RunWith(AndroidJUnit4::class)
class BotAvatarNativeDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity
    private val screenshots get() = File(activity.filesDir, "native-avatar-evidence").apply { mkdirs() }
    private val working = mutableStateOf(true)
    private val stream = mutableStateOf<ChatStreamController.StreamState?>(
        ChatStreamController.StreamState(
            conversationId = null, assistantMessageId = "avatar-assistant",
            streamText = "", phase = ChatStreamController.Phase.Streaming
        )
    )

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
                    AssistantMessage(
                        message = ChatUiMessage(
                            id = "avatar-assistant", role = "assistant",
                            content = if (working.value) "" else
                                "They only sign annual, Dana approves, and pricing is the same thread as last quarter.",
                            isStreaming = working.value,
                            createdAt = "2026-11-23T16:24:00Z"
                        ),
                        live = if (working.value) stream else null,
                        showIdentity = true, isSpeaking = false, onCopy = {}, onRegenerate = {}
                    )
                    Spacer(Modifier.weight(1f))
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

    private fun avatarViews(view: View): List<OriginalBotAvatarView> =
        if (view is OriginalBotAvatarView) listOf(view)
        else if (view is ViewGroup) (0 until view.childCount).flatMap { avatarViews(view.getChildAt(it)) }
        else emptyList()

    private fun renderer(): OriginalBotAvatarView {
        var result: OriginalBotAvatarView? = null
        instrumentation.runOnMainSync {
            val views = avatarViews(activity.window.decorView)
            check(views.size == 1) { "Expected exactly one native avatar beside reply, got " + views.size }
            result = views.single()
        }
        return result!!
    }

    private fun awaitState(view: OriginalBotAvatarView, state: String) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            instrumentation.runOnMainSync {
                ready = view.animationReady && view.animationKey == "clover-" + state
            }
            if (ready) return
            Thread.sleep(150)
        }
        error("Original native atlas did not load for " + state)
    }

    private fun frameCount(view: OriginalBotAvatarView): Int {
        var count = 0
        instrumentation.runOnMainSync { count = view.presentedFrames }
        return count
    }

    private fun avatarRect(view: OriginalBotAvatarView): Rect {
        var r = Rect()
        instrumentation.runOnMainSync {
            val p = IntArray(2)
            view.getLocationInWindow(p)
            r = Rect(p[0], p[1], p[0] + view.width, p[1] + view.height)
            assertTrue("Avatar is not a visible square in the native reply", view.isShown &&
                r.width() > 30 && r.width() == r.height() &&
                r.top < activity.window.decorView.height / 2)
        }
        return r
    }

    private fun pixelCopy(rect: Rect? = null): Bitmap {
        val root = activity.window.decorView
        val w = rect?.width() ?: root.width
        val h = rect?.height() ?: root.height
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        var code = -1
        instrumentation.runOnMainSync {
            PixelCopy.request(activity.window, rect, bitmap, { value ->
                code = value
                latch.countDown()
            }, Handler(Looper.getMainLooper()))
        }
        assertTrue("Hardware window capture timed out", latch.await(4, TimeUnit.SECONDS))
        assertTrue("Hardware window capture failed: " + code, code == PixelCopy.SUCCESS)
        return bitmap
    }

    private data class Foreground(val green: Int, val signature: Int)
    private fun foreground(bitmap: Bitmap): Foreground {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var green = 0
        var hash = 1
        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val isGreen = g > r + 20 && g > b + 10 && g > 70
            if (isGreen) green++
            hash = 31 * hash + if (isGreen)
                ((r / 16) shl 8) or ((g / 16) shl 4) or (b / 16) else 0
        }
        return Foreground(green, hash)
    }

    private fun save(bitmap: Bitmap, name: String) {
        File(screenshots, name).outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private fun fullScreenshot(name: String) {
        val image = pixelCopy()
        save(image, name)
        image.recycle()
    }

    private fun visible(view: OriginalBotAvatarView): Boolean {
        val image = pixelCopy(avatarRect(view))
        val hasGreen = foreground(image).green > 25
        image.recycle()
        return hasGreen
    }

    @Test fun authenticCanvasIsPaintedAndWorkingFramesMove() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                activity = it
                SettingsStore.updateAssistantAvatar(AssistantAvatarStyle.CLOVER)
                it.setContent { NativeChat() }
            }
            Thread.sleep(1000)
            val view = renderer()
            awaitState(view, "working")
            fullScreenshot("original-avatar-waiting.png")
            assertTrue("Waiting card avatar must be visible in the native window", visible(view))
            val device = UiDevice.getInstance(instrumentation)
            val waitingTitle = device.findObject(By.text("Waiting for response"))
                ?: error("Actual waiting card title missing")
            val titleBounds = waitingTitle.visibleBounds
            val screenLocation = IntArray(2)
            instrumentation.runOnMainSync { view.getLocationOnScreen(screenLocation) }
            val avatarBounds = Rect(
                screenLocation[0], screenLocation[1],
                screenLocation[0] + view.width, screenLocation[1] + view.height
            )
            assertTrue("Avatar must occupy the spinner slot directly left of the waiting title",
                avatarBounds.right <= titleBounds.left &&
                titleBounds.left - avatarBounds.right < view.width &&
                avatarBounds.top <= titleBounds.bottom &&
                avatarBounds.bottom >= titleBounds.top)
            instrumentation.runOnMainSync {
                stream.value = stream.value!!.copy(generationStatus = "generating")
            }
            Thread.sleep(400)
            assertTrue("Generation card must retain exactly one original avatar", renderer() === view)
            fullScreenshot("original-avatar-working-1.png")
            // Native draw cadence plus independent hardware-window pixel samples.
            assertTrue("The native stop icon must be accessible",
                device.hasObject(By.desc("Stop generating")))
            assertTrue("The stop action must not paint a text label",
                !device.hasObject(By.text("Stop")))
            assertTrue("Generation status must not be duplicated near the composer",
                !device.hasObject(By.text("Generating answer…")))

            val rect = avatarRect(view)
            val signatures = mutableSetOf<Int>()
            // Measure uninterrupted native playback separately from expensive
            // PixelCopy and screenshot encoding on the emulated GPU.
            val countBefore = frameCount(view)
            val start = SystemClock.elapsedRealtime()
            Thread.sleep(2500)
            val elapsed = (SystemClock.elapsedRealtime() - start).coerceAtLeast(1)
            val visibleHz = (frameCount(view)-countBefore)*1000.0/elapsed
            println("GS_AVATAR_NATIVE_DRAW_FPS=" + visibleHz)
            for (n in 0 until 24) {
                val image = pixelCopy(rect)
                val fg = foreground(image)
                assertTrue("Original avatar disappeared on the native Android screen", fg.green > 25)
                signatures.add(fg.signature)
                if (n % 2 == 0) save(image, "avatar-motion-" + (n/2).toString().padStart(2,'0') + ".png")
                image.recycle()
                Thread.sleep(100)
            }
            println("GS_AVATAR_UNIQUE_HARDWARE_FRAMES=" + signatures.size)
            fullScreenshot("original-avatar-working-2.png")
            assertTrue("Original native atlas playback is too slow: " + visibleHz,
                visibleHz >= 20.0)
            assertTrue("Actual compositor pixels froze: " + signatures.size + "/24",
                signatures.size >= 12)

            instrumentation.runOnMainSync { working.value = false }
            Thread.sleep(500)
            instrumentation.runOnMainSync {
                assertTrue("No duplicate or outside avatar after the active card closes",
                    avatarViews(activity.window.decorView).isEmpty())
            }
            fullScreenshot("original-avatar-settled.png")
        }
    }
}
