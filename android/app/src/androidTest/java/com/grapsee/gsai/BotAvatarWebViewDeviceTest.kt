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
import org.json.JSONObject
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
 * PixelCopy samples the hardware-composited window; View.draw(Canvas)
 * is not a valid substitute for a Chromium surface.
 */
@RunWith(AndroidJUnit4::class)
class BotAvatarWebViewDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity
    private val screenshots get() = File(activity.filesDir, "native-avatar-evidence").apply { mkdirs() }
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
                    AssistantMessage(
                        message = ChatUiMessage(
                            id = "avatar-assistant", role = "assistant",
                            content = if (working.value) "" else
                                "They only sign annual, Dana approves, and pricing is the same thread as last quarter.",
                            isStreaming = working.value,
                            createdAt = "2026-11-23T16:24:00Z"
                        ),
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

    private fun webViews(view: View): List<WebView> =
        if (view is WebView) listOf(view)
        else if (view is ViewGroup) (0 until view.childCount).flatMap { webViews(view.getChildAt(it)) }
        else emptyList()

    private fun browser(): WebView {
        var result: WebView? = null
        instrumentation.runOnMainSync {
            val views = webViews(activity.window.decorView)
            check(views.size == 1) { "Expected exactly one authentic renderer, got " + views.size }
            result = views.single()
        }
        return result!!
    }

    private fun diagnostics(view: WebView): JSONObject? {
        val latch = CountDownLatch(1)
        var result: String? = null
        instrumentation.runOnMainSync {
            view.evaluateJavascript("window.gsAvatarDiagnostics && window.gsAvatarDiagnostics()") {
                result = it
                latch.countDown()
            }
        }
        if (!latch.await(3, TimeUnit.SECONDS)) return null
        return runCatching { JSONObject(result ?: "") }.getOrNull()
    }

    private fun awaitState(view: WebView, state: String) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        var info: JSONObject? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            info = diagnostics(view)
            if (info?.optString("state") == state && info.optDouble("opacity", 0.0) >= 0.99) {
                val b = info.getJSONArray("bounds")
                val vp = info.getJSONArray("viewport")
                assertTrue("Vendor canvas is clipped: " + info,
                    b.getDouble(0) >= -1 && b.getDouble(1) >= -1 &&
                    b.getDouble(2) <= vp.getDouble(0) + 1 &&
                    b.getDouble(3) <= vp.getDouble(1) + 1)
                assertTrue("Vendor canvas resolution insufficient: " + info,
                    info.optInt("width") >= 72 && info.optInt("height") >= 72)
                assertTrue("Animation unexpectedly disabled: " + info,
                    !info.optBoolean("paused") && !info.optBoolean("reducedMotion"))
                println("GS_AVATAR_VENDOR_STATE=" + info)
                return
            }
            Thread.sleep(250)
        }
        error("Vendor canvas never became ready for " + state + ": " + info)
    }

    private fun avatarRect(view: WebView): Rect {
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

    private fun visible(view: WebView): Boolean {
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
            val view = browser()
            try {
                awaitState(view, "default")
            } catch (failure: Throwable) {
                // Preserve the hardware-composited screen even for a failed
                // bounds/readiness assertion, without weakening the test.
                runCatching { fullScreenshot("original-avatar-idle.png") }
                throw failure
            }
            fullScreenshot("original-avatar-idle.png")
            assertTrue("Idle avatar must be present in Android's hardware window", visible(view))

            instrumentation.runOnMainSync { working.value = true }
            awaitState(view, "working")
            assertTrue("The real renderer must survive the state transition", browser() === view)
            fullScreenshot("original-avatar-working-1.png")

            // Collect real compositor pixels, not JS canvas hashes or an
            // animated background. Sample a small avatar rect to keep
            // the Android emulator stable.
            val rect = avatarRect(view)
            val signatures = mutableSetOf<Int>()
            var changes = 0
            var previous: Int? = null
            var samples = 0
            val start = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - start < 3000) {
                val image = pixelCopy(rect)
                val fg = foreground(image)
                assertTrue("Visible avatar disappeared during working animation", fg.green > 25)
                if (previous != fg.signature) changes++
                previous = fg.signature
                signatures.add(fg.signature)
                if (samples % 5 == 0 && samples / 5 < 12) {
                    save(image, "avatar-motion-" + (samples / 5).toString().padStart(2, '0') + ".png")
                }
                image.recycle()
                samples++
                Thread.sleep(20)
            }
            val elapsed = (SystemClock.elapsedRealtime() - start).coerceAtLeast(1)
            val visibleHz = changes * 1000.0 / elapsed
            println("GS_AVATAR_VISIBLE_CHANGES_PER_SECOND=" + visibleHz +
                " samples=" + samples + " unique=" + signatures.size)
            fullScreenshot("original-avatar-working-2.png")
            assertTrue("Real compositor animation is too slow or frozen: " + visibleHz +
                " changes/sec, " + signatures.size + " distinct frames",
                visibleHz >= 15.0 && signatures.size >= 35)

            instrumentation.runOnMainSync { working.value = false }
            awaitState(view, "default")
            assertTrue("Settled avatar must remain visible", visible(view))
            fullScreenshot("original-avatar-settled.png")
        }
    }
}
