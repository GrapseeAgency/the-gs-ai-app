package com.grapsee.gsai

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.grapsee.gsai.data.SessionStore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * GsScreenshotSuite — drives the app through the eight design-pass screens and
 * captures one PNG per screen into the app's external files dir.
 *
 * WHY UIAUTOMATOR AND NOT THE COMPOSE TEST RULE: the chat surface runs
 * perpetual animations (the activity orb, the aurora). A ComposeTestRule waits
 * for recomposition quiescence and hangs on exactly those surfaces. UiAutomator
 * has no idle contract — it matches accessibility text and grabs whatever is
 * on screen, which is what a screenshot run needs.
 *
 * Every step is defensive: a screen that fails to open still produces a
 * screenshot of wherever the app actually is, so a failed navigation is
 * visible evidence rather than a missing file.
 */
@RunWith(AndroidJUnit4::class)
class GsScreenshotSuite {

    private val device: UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val ctx: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext
    /**
     * Screenshot dir: the INSTRUMENTATION context's own external files dir.
     * The test package (com.grapsee.gsai.test) cannot write into the app's
     * Android/data directory on API 30 scoped storage — writes there fail
     * silently, which is exactly what the first run did. The instrumentation
     * context's own dir is writable by the test and readable via adb pull.
     */
    private val shotCtx: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    private fun shot(name: String) {
        val dir = File(shotCtx.getExternalFilesDir(null), "screens").apply { mkdirs() }
        val out = File(dir, "$name.png")
        val ok = runCatching { device.takeScreenshot(out, 1.0f, 19) }.isSuccess
        println("GS-SCREENSHOT $name ok=$ok -> ${out.absolutePath} (${out.length()} bytes)")
    }

    private fun resetSession(active: Boolean, onboarded: Boolean) {
        ctx.getSharedPreferences("gs_session", Context.MODE_PRIVATE).edit().clear().commit()
        if (active) SessionStore.activateSession(ctx)
        SessionStore.setOnboarded(ctx, onboarded)
    }

    private fun launchApp() {
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        InstrumentationRegistry.getInstrumentation().startActivitySync(intent)
        device.waitForIdle(4_000)
    }

    private fun dismissConsentIfPresent() {
        val notNow = device.findObject(By.textContains("Not now"))
        if (notNow != null) {
            runCatching { notNow.click() }
            device.waitForIdle(2_000)
        }
    }

    private fun tapText(text: String, timeout: Long = 6_000): Boolean {
        val node = device.wait(Until.findObject(By.textContains(text)), timeout) ?: return false
        return runCatching { node.click(); device.waitForIdle(2_000); true }.getOrDefault(false)
    }

    private fun tapDesc(desc: String, timeout: Long = 6_000): Boolean {
        val node = device.wait(Until.findObject(By.descContains(desc)), timeout) ?: return false
        return runCatching { node.click(); device.waitForIdle(2_000); true }.getOrDefault(false)
    }

    private fun goHome() {
        device.pressBack()
        device.waitForIdle(1_500)
    }

    @Test
    fun captureDesignPassScreens() {
        // 1. Splash + first-launch gate: cold start with NO session.
        resetSession(active = false, onboarded = false)
        launchApp()
        shot("10-splash-first-launch")
        device.wait(Until.hasObject(By.textContains("GS")), 8_000)
        device.waitForIdle(2_000)
        shot("11-auth-gate")

        // 2. Onboarding (session active, onboarding pending).
        resetSession(active = true, onboarded = false)
        launchApp()
        device.wait(Until.hasObject(By.textContains("GS")), 8_000)
        device.waitForIdle(2_000)
        shot("12-onboarding")

        // 3. Empty chat workspace.
        resetSession(active = true, onboarded = true)
        launchApp()
        device.wait(Until.hasObject(By.textContains("GS")), 8_000)
        dismissConsentIfPresent()
        device.waitForIdle(2_000)
        shot("20-empty-chat")

        // 4. Model / mode picker sheet.
        if (tapText("Auto") || tapText("Thinking")) {
            device.waitForIdle(1_500)
            shot("21-mode-picker-sheet")
            goHome()
        } else {
            shot("21-mode-picker-sheet-MISSING-TRIGGER")
        }

        // 5. Image attachment flow: the attach sheet the composer opens.
        if (tapDesc("Attach")) {
            device.waitForIdle(1_500)
            shot("22-attach-sheet")
            goHome()
        } else {
            shot("22-attach-sheet-MISSING-TRIGGER")
        }

        // 6. Settings.
        if (tapDesc("Open menu")) {
            if (tapText("Settings")) {
                device.waitForIdle(2_000)
                shot("30-settings")
                goHome()
            } else {
                shot("30-settings-MISSING-ROW")
            }
        } else {
            shot("30-settings-MISSING-MENU")
        }

        // 7. Active chat with a streaming reply, then the settled rich reply.
        // The prompt demands a table + a code block so the settled shot shows
        // the structured renderer end to end. The emulator reaches the same
        // public origin the phone ships with.
        val input = device.findObject(By.clazz(android.widget.EditText::class.java))
        if (input != null) {
            runCatching { input.setText("Show a small markdown table of the first 3 planets (name, moons) and a short kotlin code block that prints hello.") }
            device.waitForIdle(1_000)
            if (tapDesc("Send")) {
                SystemClock.sleep(1_200)
                shot("40-active-chat-streaming")
                // Wait for the stream to settle: the inline stop control
                // disappears and the send slot returns. Cap at two minutes;
                // capture whatever state exists either way.
                device.wait(Until.gone(By.desc("Stop generating")), 120_000)
                SystemClock.sleep(2_500)
                shot("41-rich-reply")
            } else {
                shot("40-streaming-MISSING-SEND")
            }
        } else {
            shot("40-streaming-MISSING-INPUT")
        }

        val dir = File(shotCtx.getExternalFilesDir(null), "screens")
        println("GS-SCREENSHOT suite complete (${dir.list()?.size ?: 0} files): ${dir.list()?.joinToString()}")
    }
}
