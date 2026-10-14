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
import com.grapsee.gsai.di.ServiceLocator
import org.junit.Assert.assertTrue
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
 * runner pulls them with `adb exec-out run-as com.grapsee.gsai cat`.
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
    // am instrument runs this code INSIDE the target app's process (the
    // app's uid), so the ONLY guaranteed-writable dir is the TARGET package's
    // own filesDir — writes into the test package's data dir fail with
    // ENOENT-through-EACCES (run 38426476180 evidence: mkdirs silently false,
    // FileNotFoundException on open). The runner pulls with
    // `adb exec-out run-as com.grapsee.gsai cat ...` (debug APK = runnable).
    private val shotDir: File
        get() = File(ctx.filesDir, "screens")

    /** Capture health, asserted at the end of the capture test (lint contract:
     * every test can fail — check_tests_can_fail.py, android-app run
     * 38535241938). MISSING-name variants stay legitimate evidence (an
     * expected-absent element still frames the truth); a frame whose bitmap
     * or PNG write failed is NOT evidence, and a run that produced no frames
     * proves nothing. */
    private var shotOk = 0
    private var shotFailed = 0

    private fun shot(name: String) {
        val dir = shotDir.apply { mkdirs() }
        val out = File(dir, "$name.png")
        // Direct UiAutomation capture + manual PNG compress. Both
        // UiDevice.takeScreenshot overloads failed silently on the API 30
        // emulator (ok=true-but-0-bytes, then ok=false with no message — runs
        // 38422692350 / 38424705700). This route surfaces the real error and
        // does the compress write itself, so a failure is visible in logcat.
        val result = runCatching {
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                ?: error("uiAutomation.takeScreenshot() returned null")
            out.outputStream().use { fos ->
                if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)) {
                    error("PNG compress returned false")
                }
            }
        }
        println("GS-SCREENSHOT $name ok=${result.isSuccess} bytes=${out.length()} err=${result.exceptionOrNull()?.javaClass?.simpleName}:${result.exceptionOrNull()?.message}")
        if (result.isSuccess) shotOk++ else shotFailed++
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

    /**
     * Seeds a rich conversation straight into the app's Room DB. The
     * transcript then exercises the BLOCK RENDERER (headings, aligned table,
     * code card, blockquote, list, inline math) with ZERO network — the
     * deterministic renderer evidence that does not depend on the sandbox
     * origin being up.
     */
    private fun seedRichConversation() {
        val now = java.time.Instant.now().toString()
        kotlinx.coroutines.runBlocking {
            ServiceLocator.db.conversationDao().upsert(
                com.grapsee.gsai.data.local.ConversationEntity(
                    id = "shot-rich-1",
                    title = "Design pass sample",
                    modelId = null,
                    pinned = false,
                    archived = false,
                    updatedAt = now,
                    createdAt = now
                )
            )
            ServiceLocator.db.messageDao().insertAll(
                listOf(
                    com.grapsee.gsai.data.local.MessageEntity(
                        id = "shot-rich-1-u",
                        conversationId = "shot-rich-1",
                        role = "user",
                        content = "Show the first three planets with a table and a hello in kotlin.",
                        createdAt = now
                    ),
                    com.grapsee.gsai.data.local.MessageEntity(
                        id = "shot-rich-1-a",
                        conversationId = "shot-rich-1",
                        role = "assistant",
                        content = RICH_FIXTURE,
                        createdAt = now
                    )
                )
            )
        }
    }

    private val RICH_FIXTURE = """## The first three planets

| Planet | Moons | Orbit days |
| :--- | :---: | ---: |
| Mercury | 0 | 88 |
| Venus | 0 | 225 |
| Earth | 1 | 365 |

```kotlin
fun main() {
    println("hello")
}
```

> Distances grow fast: the Sun to Earth is 1 AU, to Mercury 0.39 AU.

- Mercury is closest to the Sun
- Venus rotates backwards
- Earth is the only known life

Inline `code` and math ${'$'}a^2 + b^2 = c^2$."""

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
        // The rich fixture seeds BEFORE this launch: the drawer's store reads
        // Room at startup, so the seeded row is real drawer content for the
        // 20b + 41 shots instead of a mid-session insert the snapshot misses.
        seedRichConversation()
        resetSession(active = true, onboarded = true)
        launchApp()
        device.wait(Until.hasObject(By.textContains("GS")), 8_000)
        dismissConsentIfPresent()
        device.waitForIdle(2_000)
        shot("20-empty-chat")

        // 3b. The drawer (design phase 2 screen 10): search pill + New chat
        // top, recents center, Voice row, account footer pinned bottom.
        if (tapDesc("Open menu")) {
            SystemClock.sleep(1_500)
            shot("20b-drawer")
            // BACK is not the drawer's close affordance on this emulator
            // (back exits to the launcher with the drawer open); the scrim
            // tap is the user's close gesture and the suite uses it too.
            device.click(device.displayWidth - 60, device.displayHeight / 2)
            device.waitForIdle(1_500)
        } else {
            shot("20b-drawer-MISSING-MENU")
        }

        // 5. The RICH RENDERER, deterministic: the conversation seeded before
        // launch renders through the block pipeline with no network dependency.
        if (tapDesc("Open menu") && tapText("Design pass sample")) {
            SystemClock.sleep(2_000)
            shot("41-rich-reply-rendered")
            device.pressBack()
            device.waitForIdle(1_500)
        } else {
            shot("41-rich-reply-rendered-MISSING-ROW")
        }

        // 4. Active chat with a streaming reply, network best effort. The
        // prompt demands a table + a code block so the settled shot shows the
        // structured renderer end to end. The emulator reaches the same public
        // origin the phone ships with.
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
                // The sandbox origin has flappy up-windows; a turn that lands
                // in a dead window answers with the honest error notice. The
                // suite REGENERATES through the flaps (up to four tries) so
                // the rich-reply shot shows the real renderer, not the error.
                var tries = 0
                while (tries < 4 && device.hasObject(By.textContains("GS backend error"))) {
                    tries++
                    println("GS-SCREENSHOT retry $tries: origin was down mid-turn, regenerating")
                    SystemClock.sleep(40_000)
                    if (!tapDesc("Regenerate")) break
                    device.wait(Until.gone(By.desc("Stop generating")), 120_000)
                    SystemClock.sleep(2_500)
                }
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

        // 6. Settings: the drawer footer's account row opens the account
        // sheet (design phase 2 reduced the More sheet away; Settings lives
        // one level in behind the pinned bottom identity row).
        if (tapDesc("Open menu") && tapDesc("Account options")) {
            if (device.wait(Until.hasObject(By.textContains("Settings")), 10_000) &&
                tapText("Settings")
            ) {
                SystemClock.sleep(1_500)
                shot("30-settings")
                device.pressBack()
                device.waitForIdle(1_500)
            } else {
                shot("30-settings-MISSING-ROW")
            }
        } else {
            shot("30-settings-MISSING-MORE")
        }

        val dir = shotDir
        println("GS-SCREENSHOT suite complete (${dir.list()?.size ?: 0} files): ${dir.list()?.joinToString()}")
        // The load-bearing assertion: the suite walks 11+ surfaces on a healthy
        // run; a capture path that silently dies (the exact silent-failure class
        // both UiDevice.takeScreenshot overloads showed) or writes no frames
        // must fail HERE, not pass as a green run with zero evidence.
        assertTrue(
            "screenshot capture is not evidence-bearing: $shotOk ok frame(s), $shotFailed failed",
            shotOk >= 8 && shotFailed == 0
        )
    }
}
