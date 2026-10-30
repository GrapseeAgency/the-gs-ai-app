package com.grapsee.gsai.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The per-kind gate behind Notifications & Alerts. The screen writes these
 * switches and `GsNotifier` reads them; if an unknown id defaulted to ON, a
 * future kind could post without a row the user turned on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsNoticeKindsTest {

    private lateinit var ctx: Context

    @Before
    fun reset() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("gs_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        SettingsStore.init(ctx)
        // `init` falls back to the CURRENT field when a key is missing, so
        // clearing prefs alone does not restore the defaults another test may
        // have changed. Write them explicitly, as the settings suites do.
        SettingsStore.updatePushNotifications(true)
        for (kind in listOf("messages", "ai-updates", "tips", "account-activity", "product-updates")) {
            SettingsStore.updateNoticeEnabled(kind, true)
        }
    }

    @Test
    fun everyMockKindDefaultsOnAndCanBeTurnedOffAlone() {
        for (kind in listOf("messages", "ai-updates", "tips", "account-activity", "product-updates")) {
            assertTrue("$kind should default on", SettingsStore.noticeEnabled(kind))
        }

        SettingsStore.updateNoticeEnabled("messages", false)

        assertFalse(SettingsStore.noticeEnabled("messages"))
        for (kind in listOf("ai-updates", "tips", "account-activity", "product-updates")) {
            assertTrue("$kind must keep its own switch", SettingsStore.noticeEnabled(kind))
        }
    }

    @Test
    fun anUnknownKindNeverDefaultsOn() {
        SettingsStore.updateNoticeEnabled("does-not-exist", true)
        assertFalse(
            "an id with no row must not silently become a live kind",
            SettingsStore.noticeEnabled("does-not-exist"),
        )
    }

    @Test
    fun theMasterSwitchIsRemembered() {
        assertTrue(SettingsStore.pushNotifications)
        SettingsStore.updatePushNotifications(false)
        assertFalse(SettingsStore.pushNotifications)
    }
}
