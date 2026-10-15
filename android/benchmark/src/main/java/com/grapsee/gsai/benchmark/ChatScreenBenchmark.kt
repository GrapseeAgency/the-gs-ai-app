/*
 * perf-4 — chat screen frame-budget harness (operator contract deliverable 1).
 *
 * Four methods, one screen:
 *   scrollJank()      500 frames scrolled, raw FrameTimingMetric out
 *   composerToggle()  100 IME open/close cycles over the composer field
 *   streamingAppend() send a turn, measure every frame for 3 seconds of reply
 *   coldStartup()     StartupTimingMetric, CompilationMode.None, COLD
 *
 * Threshold enforcement layer: the raw numbers are the deliverable (they land
 * in the instrumentation output and the CI artifact); pass/fail is decided on
 * those numbers in the report, not softened inside the harness.
 */
package com.grapsee.gsai.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatScreenBenchmark {

    private val benchmarkRule = MacrobenchmarkRule()

    private val device: UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private val packageName = "com.grapsee.gsai"

    private val cx: Int get() = device.displayWidth / 2
    private val cy: Int get() = device.displayHeight / 2
    private val composerY: Int get() = device.displayHeight - 140
    private val sendX: Int get() = device.displayWidth - 80

    @Before
    fun settleOnHome() {
        device.pressHome()
        device.waitForIdle()
    }

    private fun openComposerField() {
        device.findObjects(By.clazz("android.widget.EditText")).firstOrNull()?.click()
            ?: device.click(cx, composerY)
        device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 3_000)
    }

    /** TARGET 1 + 2 — scroll the transcript ~500 frames, assert on raw output. */
    @Test
    fun scrollJank() {
        benchmarkRule.measureRepeated(
            packageName = packageName,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Full(),
            iterations = 3,
            startupMode = StartupMode.WARM
        ) {
            startActivityAndWait()
            device.waitForIdle()
            repeat(10) {
                device.swipe(cx, cy + 400, cx, cy - 500, 40)
                device.swipe(cx, cy - 500, cx, cy + 400, 40)
            }
        }
    }

    /** TARGET 3 — composer expand/collapse, 100 cycles, frame timing out. */
    @Test
    fun composerToggle() {
        benchmarkRule.measureRepeated(
            packageName = packageName,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Full(),
            iterations = 1,
            startupMode = StartupMode.WARM
        ) {
            startActivityAndWait()
            device.waitForIdle()
            repeat(100) {
                openComposerField()
                device.pressBack()
            }
        }
    }

    /** TARGET 4 — send a turn, measure every frame across 3s of streaming. */
    @Test
    fun streamingAppend() {
        benchmarkRule.measureRepeated(
            packageName = packageName,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Full(),
            iterations = 1,
            startupMode = StartupMode.WARM
        ) {
            startActivityAndWait()
            device.waitForIdle()
            openComposerField()
            val field = device.findObject(By.clazz("android.widget.EditText"))
            field?.text = "Count from 1 to 20 slowly"
            device.click(sendX, composerY)
            // The measured window: the stream is live for these 3 seconds.
            Thread.sleep(3_000)
        }
    }

    /** TARGET 6 — cold start to first rendered frame, CompilationMode.None. */
    @Test
    fun coldStartup() {
        benchmarkRule.measureRepeated(
            packageName = packageName,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.None(),
            iterations = 3,
            startupMode = StartupMode.COLD
        ) {
            pressHome()
            startActivityAndWait()
        }
    }
}
