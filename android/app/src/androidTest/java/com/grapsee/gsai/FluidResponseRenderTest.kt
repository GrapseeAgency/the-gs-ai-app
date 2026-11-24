package com.grapsee.gsai.ui.chat.content

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real Compose renderer on an emulator/device, not an animation model screenshot.
 * The deterministic append is a test fixture, not a simulated provider in the app.
 * The manual frame clock lets us capture the first real draw before its animation
 * settles. This is a pixel regression, not a physical-device FPS claim. */
@RunWith(AndroidJUnit4::class)
class FluidResponseRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun incomingInkChangesButCompletedLineDoesNot() {
        check(android.animation.ValueAnimator.areAnimatorsEnabled()) {
            "Animation evidence requires animator_duration_scale > 0"
        }
        val prefix = "Already read.\n"
        val answer = prefix + "New words flow into place."
        val source = mutableStateOf(prefix)
        val live = mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                val style = TextStyle(fontSize = 20.sp, lineHeight = 28.sp)
                val modifier = Modifier.fillMaxWidth().height(84.dp)
                    .background(Color.White).testTag("native-fluid-response")
                CompositionLocalProvider(LocalFluidResponseText provides live.value) {
                    FluidResponseText(AnnotatedString(source.value), style, Color.Black, modifier = modifier)
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val node = compose.onNodeWithTag("native-fluid-response")
        compose.runOnIdle { source.value = answer }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        node.assertTextEquals(answer)
        val early = node.captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        SystemClock.sleep(420)
        compose.mainClock.advanceTimeByFrame()
        val settled = node.captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        compose.runOnIdle { live.value = false }
        compose.mainClock.advanceTimeByFrame()
        val final = node.captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        val split = early.height / 3
        fun changed(a: Bitmap, b: Bitmap, top: Int, bottom: Int): Int {
            var count = 0
            for (y in top until bottom) for (x in 0 until a.width) {
                val av = a.getPixel(x, y); val bv = b.getPixel(x, y)
                if (kotlin.math.abs((av and 255) - (bv and 255)) > 18) count++
            }
            return count
        }
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "fluid-response-evidence")
        dir.mkdirs()
        listOf("early" to early, "settled" to settled, "final" to final).forEach { (name, image) ->
            File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val prefixDiff = changed(early, settled, 0, split)
        val tailDiff = changed(early, settled, split, early.height)
        val settledDiff = changed(settled, final, 0, final.height)
        File(dir, "pixel-report.txt").writeText("prefixChanged=$prefixDiff\ntailChanged=$tailDiff\nsettledChanged=$settledDiff\n")
        assertTrue("The incoming line must visibly animate; changed pixels=$tailDiff", tailDiff > 40)
        assertTrue("Completed line must not animate; changed pixels=$prefixDiff", prefixDiff < 12)
        assertTrue("Settled native text must match finished text; changed pixels=$settledDiff", settledDiff < 12)
    }
}
