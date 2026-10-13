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
 * GsScreenshotSuite — drives the app through the design-pass screens and
 * captures one PNG per screen.
 *
 * WHY UIAUTOMATOR AND NOT THE COMPOSE TEST RULE: the chat surface runs
 * perpetual animations (the activity orb, the aurora). A ComposeTestRule waits
 * for recomposition quiescence and hangs on exactly those surfaces. UiAutomator
 * has no idle contract — it matches accessibility text and grabs whatever is
 * on screen, which is what a screenshot run needs.
 *
 * STORAGE (run 38421472933 lesson): screenshots go to the INSTRUMENTATION
 * package's INTERNAL files dir (context.filesDir) — always writable, and the
 * runner pulls them with `adb exec-out run-as com.grapsee.gsai.test cat`.
 * getExternalFilesDir proved unreliable from the test process, and the app's
 * own Android/data dir is not writable by the test on API 30 scoped storage.
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
    private val shotCtx: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    private fun shot(name: String) {
        val dir = File(shotCtx.filesDir, "screens").apply { mkdirs() }
        val out = File(dir, "$name.png")
        val ok = runCatching { device.takeScreenshot(out, 1.0f, 19) }.isSuccess
        println("GS-SCREENSHOT $name ok=$ok bytes=${out.length()}")
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
        SystemClock.sleep(1_500)
    }

    private fun dismissConsentIfPresent() {
        val notNow = device.findObject(By.textContains("Not now"))
        if (notNow != null) {
            runCatching { notNow.click() }
            device.waitForIdle(2_000)
        }
    }

    private fun tapText(text: String, timeout: Long = 8_000): Boolean {
        val node = device.wait(Until.findObject(By.textContains(text)), timeout) ?: return false
        return runCatching { node.click(); device.waitForIdle(2_000); true }.getOrDefault(false)
    }

    private fun tapDesc(desc: String, timeout: Long = 8_000): Boolean {
        val node = device.wait(Until.findObject(By.descContains(desc)), timeout) ?: return false
        return runCatching { node.click(); device.waitForIdle(2_000); true }.getOrDefault(false)
    }

    private fun findComposerInput(): androidx.test.uiautomator.UiObject2? {
        val direct = device.findObject(By.clazz(android.widget.EditText::class.java))
        if (direct != null) return direct
        // Compose fields can need a focus tap first: the composer sits at the
        // bottom of the screen.
        device.click(device.displayWidth / 2, device.displayHeight - 220)
        device.waitForIdle(1_500)
        return device.wait(Until.findObject(By.clazz(android.widget.EditText::class.java)), 6_000)
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

        // 3. Empty chat workspace — the home surface for the rest of the run.
        resetSession(active = true, onboarded = true)
        launchApp()
        device.wait(Until.hasObject(By.textContains("GS")), 8_000)
        dismissConsentIfPresent()
        device.waitForIdle(2_000)
        shot("20-empty-chat")

        // 4 + 5. Active chat with a streaming reply, then the settled rich
        // reply. Done FIRST while the state is pristine. The prompt demands a
        // table + a code block so the settled shot shows the structured
        // renderer end to end. The emulator reaches the same public origin the
        // phone ships with.
        val input = findComposerInput()
        if (input != null) {
            runCatching {
                input.setText("Show a small markdown table of the first 3 planets (name, moons) and a short kotlin code block that prints hello.")
            }
            device.waitForIdle(1_000)
            if (tapDesc("Send")) {
                SystemClock.sleep(1_400)
                shot("40-active-chat-streaming")
                // Wait for the stream to settle: the inline stop control
                // disappears. Cap at two minutes; capture either way.
                device.wait(Until.gone(By.desc("Stop generating")), 120_000)
                SystemClock.sleep(2_500)
                shot("41-rich-reply")
            } else {
                shot("40-streaming-MISSING-SEND")
            }
        } else {
            shot("40-streaming-MISSING-INPUT")
        }

        // 5. Attach + mode sheet: on this app the composer's attach control
        // opens ONE sheet carrying both the attachment tiles and the mode
        // picker, so one shot is the honest evidence for both screens.
        if (tapDesc("Attach")) {
            SystemClock.sleep(1_200)
            shot("21-attach-mode-sheet")
            shot("22-attach-sheet")
            device.pressBack()
            device.waitForIdle(1_500)
        } else {
            shot("21-attach-mode-sheet-MISSING-TRIGGER")
        }

        // 6. Settings, through the drawer.
        if (tapDesc("Open menu")) {
            SystemClock.sleep(1_200)
            if (tapText("Settings")) {
                SystemClock.sleep(1_500)
                shot("30-settings")
                device.pressBack()
                device.waitForIdle(1_500)
            } else {
                shot("30-settings-MISSING-ROW")
            }
        } else {
            shot("30-settings-MISSING-MENU")
        }

        val dir = File(shotCtx.filesDir, "screens")
        println("GS-SCREENSHOT suite complete (${dir.list()?.size ?: 0} files): ${dir.list()?.joinToString()}")
    }
}
