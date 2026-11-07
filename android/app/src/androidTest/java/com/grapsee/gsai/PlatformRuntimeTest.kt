package com.grapsee.gsai

import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiDevice
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.data.notifications.AppForeground
import com.grapsee.gsai.data.notifications.ReplyNotifier
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.theme.GsHaptics
import com.grapsee.gsai.ui.theme.TheGsAiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real OS notification/lifecycle + injected touch tests. No model or network required.
 * A delayed main-loop callback is background CONTINUATION, not a scheduled wake.
 * The recording View proves haptic dispatch/gating, not vibration on physical hardware.
 */
@RunWith(AndroidJUnit4::class)
class PlatformRuntimeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun backgroundReplyAppearsInOsAndSettingsSuppressIt() {
        val context = instrumentation.targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val thread = "platform-runtime-${System.nanoTime()}"
        val id = thread.hashCode()
        val oldMaster = SettingsStore.pushNotifications
        val oldMessages = SettingsStore.noticeEnabled("messages")
        val oldQuiet = SettingsStore.quietHoursEnabled
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName, android.Manifest.permission.POST_NOTIFICATIONS
            )
        }
        fun post() = ReplyNotifier.postReplyReady(context, thread, "Runtime reply from the test callback")
        try {
            compose.runOnIdle {
                SettingsStore.updatePushNotifications(true)
                SettingsStore.updateNoticeEnabled("messages", true)
                SettingsStore.updateQuietHoursEnabled(false)
            }
            awaitObserved("initial activity foreground") { AppForeground.inForeground }
            post()
            assertFalse("Foreground reply must not post", manager.activeNotifications.any { it.id == id })

            // Home drives MainActivity.ON_STOP; do NOT assign AppForeground in the test.
            assertTrue(UiDevice.getInstance(instrumentation).pressHome())
            awaitObserved("Home backgrounds activity") { !AppForeground.inForeground }
            val callback = CountDownLatch(1)
            Handler(Looper.getMainLooper()).postDelayed({ post(); callback.countDown() }, 1_000)
            assertTrue("Background callback never ran", callback.await(10, TimeUnit.SECONDS))
            awaitObserved("background reply becomes an active OS notification") {
                manager.activeNotifications.any { it.id == id }
            }
            val notice = manager.activeNotifications.single { it.id == id }.notification
            assertEquals("Reply ready", notice.extras.getString(Notification.EXTRA_TITLE))
            assertEquals("Runtime reply from the test callback", notice.extras.getString(Notification.EXTRA_TEXT))
            assertEquals(ReplyNotifier.CHANNEL_ID, notice.channelId)
            assertNotNull("Reply must route back to its conversation", notice.contentIntent)
            // A real shade tap is a user-initiated launch. Calling send() on
            // the PendingIntent from a background test is NOT equivalent:
            // Android 14 correctly applies background activity launch limits.
            val device = UiDevice.getInstance(instrumentation)
            assertTrue("Could not open notification shade", device.openNotification())
            val shadeRow = device.wait(Until.findObject(By.text("Runtime reply from the test callback")), 5_000)
            assertNotNull("OS shade did not expose the actual reply", shadeRow)
            shadeRow!!.click()
            awaitObserved("notification tap returns to foreground") { AppForeground.inForeground }
            awaitObserved("notification auto-cancels after real shade tap") {
                manager.activeNotifications.none { it.id == id }
            }
            assertTrue(device.pressHome())
            awaitObserved("second Home backgrounds activity") { !AppForeground.inForeground }

            instrumentation.runOnMainSync { SettingsStore.updatePushNotifications(false) }
            post()
            assertFalse(manager.activeNotifications.any { it.id == id })
            instrumentation.runOnMainSync {
                SettingsStore.updatePushNotifications(true)
                SettingsStore.updateNoticeEnabled("messages", false)
            }
            post()
            assertFalse(manager.activeNotifications.any { it.id == id })
        } finally {
            manager.cancel(id)
            instrumentation.runOnMainSync {
                SettingsStore.updatePushNotifications(oldMaster)
                SettingsStore.updateNoticeEnabled("messages", oldMessages)
                SettingsStore.updateQuietHoursEnabled(oldQuiet)
            }
        }
    }

    private fun awaitObserved(stage: String, condition: () -> Boolean) {
        try { compose.waitUntil(10_000, condition) }
        catch (error: Throwable) {
            throw AssertionError("Runtime stage failed: $stage; foreground=${AppForeground.inForeground}", error)
        }
    }

    @Test fun realCardDistinguishesTapFromLongPress() {
        var taps = 0
        var holds = 0
        compose.activity.setContentForPlatformTest {
            TheGsAiTheme {
                GsCard(Modifier.testTag("runtime-card"), onClick = { taps++ }, onLongClick = { holds++ }) {
                    Text("Runtime gesture target")
                }
            }
        }
        compose.onNodeWithTag("runtime-card").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, taps); assertEquals(0, holds) }
        compose.onNodeWithTag("runtime-card").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(1, taps); assertEquals(1, holds) }
    }

    @Test fun hapticDispatchUsesPlatformConstantsAndHonorsToggle() {
        val old = SettingsStore.haptics
        val calls = mutableListOf<Int>()
        try {
            compose.runOnIdle {
                val view = object : View(compose.activity) {
                    override fun performHapticFeedback(constant: Int): Boolean {
                        calls.add(constant)
                        return super.performHapticFeedback(constant)
                    }
                }
                SettingsStore.updateHaptics(true)
                GsHaptics.press(view)
                GsHaptics.success(view)
                GsHaptics.warning(view)
                GsHaptics.error(view)
                assertEquals(listOf(HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.CONFIRM, HapticFeedbackConstants.GESTURE_END,
                    HapticFeedbackConstants.REJECT), calls)
                SettingsStore.updateHaptics(false)
                GsHaptics.press(view); GsHaptics.success(view); GsHaptics.warning(view); GsHaptics.error(view)
                assertEquals("Disabled haptics must not dispatch", 4, calls.size)
            }
        } finally { compose.runOnIdle { SettingsStore.updateHaptics(old) } }
    }
}

private fun MainActivity.setContentForPlatformTest(content: @androidx.compose.runtime.Composable () -> Unit) {
    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
        setContent(content = content)
    }
}
