package com.grapsee.gsai

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.grapsee.gsai.data.AssistantAvatarStyle
import com.grapsee.gsai.ui.components.GsBotAvatar
import com.grapsee.gsai.ui.theme.TheGsAiTheme
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Runs on a real Android emulator WebView; verifies the actual npm canvas. */
@RunWith(AndroidJUnit4::class)
class BotAvatarWebViewDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val screenshots get() = File(compose.activity.filesDir, "native-avatar-evidence").apply { mkdirs() }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (n in 0 until view.childCount) {
            val found = findWebView(view.getChildAt(n))
            if (found != null) return found
        }
        return null
    }

    private fun canvasHash(): String {
        val latch = CountDownLatch(1)
        var result = ""
        // Check actual canvas pixels, not an empty <canvas> shell or static PNG.
        val js = """(function(){
          var c=document.querySelector('canvas');
          if(!c||!c.width||!c.height)return 'NO_CANVAS';
          var p=c.getContext('2d').getImageData(0,0,c.width,c.height).data;
          var h=2166136261,n=0;
          for(var i=0;i<p.length;i+=16) {
            h=Math.imul(h^p[i],16777619);
            h=Math.imul(h^p[i+1],16777619);
            h=Math.imul(h^p[i+2],16777619);
            if(p[i+3]>0)n++;
          }
          return n+':'+(h>>>0);
        })()"""
        instrumentation.runOnMainSync {
            val browser = findWebView(compose.activity.window.decorView)
                ?: error("Actual Android WebView is missing")
            browser.evaluateJavascript(js) { value ->
                result = value
                latch.countDown()
            }
        }
        assertTrue("WebView JavaScript callback timed out", latch.await(15, TimeUnit.SECONDS))
        return result
    }

    private fun awaitPaint(): String {
        repeat(28) {
            val hash = canvasHash()
            if (hash.matches(Regex(".*[1-9][0-9]*:[0-9]+.*"))) return hash
            Thread.sleep(250)
        }
        error("Original bot-avatars did not paint an opaque canvas: " + canvasHash())
    }

    private fun capture(file: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
            ?: error("Native emulator screenshot unavailable")
        val output = File(screenshots, file)
        output.outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        assertTrue("Screenshot missing actual native screen", output.length() > 20000)
        bitmap.recycle()
    }

    private fun screen(working: Boolean) {
        instrumentation.runOnMainSync {
            compose.activity.setContent {
                TheGsAiTheme(darkTheme = false) {
                    Column(
                        Modifier.fillMaxSize()
                            .background(Color(0xFFF4E5F2))
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Text("Can you summarise the thread with Acme?")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GsBotAvatar(AssistantAvatarStyle.CLOVER, isWorking = working)
                            Text(
                                if (working) "Working…" else
                                    "They only sign annual, Dana approves, and pricing is unchanged.",
                                modifier = Modifier.padding(start = 10.dp)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text("Ask anything…")
                    }
                }
            }
        }
    }

    @Test fun authenticCanvasIsPaintedAndWorkingFramesMove() {
        screen(false)
        compose.onNodeWithContentDescription("Clover bot, idle").assertIsDisplayed()
        compose.onNodeWithText("Ask anything…").assertIsDisplayed()
        val idle = awaitPaint()
        capture("original-avatar-idle.png")

        screen(true)
        compose.onNodeWithContentDescription("Clover bot, working").assertIsDisplayed()
        compose.onNodeWithText("Ask anything…").assertIsDisplayed()
        val frame1 = awaitPaint()
        capture("original-avatar-working-1.png")
        Thread.sleep(900)
        val frame2 = canvasHash()
        capture("original-avatar-working-2.png")
        assertNotEquals("Original Libraries.dev canvas must animate while working",
            frame1, frame2)
        assertTrue("The original JS engine must paint a real image: " + idle,
            idle.contains(":"))
    }
}
