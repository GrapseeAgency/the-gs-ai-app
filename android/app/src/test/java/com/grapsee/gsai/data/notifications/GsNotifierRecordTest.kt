package com.grapsee.gsai.data.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.grapsee.gsai.data.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Android writer behind the in-app notice kinds. Onboarding is the Tips
 * producer; a sign-in is Account Activity; a model landing is AI Updates.
 * These tests pin the gate BETWEEN the producer and the Inbox: a kind the
 * user turned off must not write, and empty copy must never become a row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GsNotifierRecordTest {

    private lateinit var ctx: Context

    @Before
    fun reset() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("gs_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        ctx.getSharedPreferences("gs_notice_inbox", Context.MODE_PRIVATE)
            .edit().clear().commit()
        SettingsStore.init(ctx)
        SettingsStore.updatePushNotifications(true)
        SettingsStore.updateNoticeEnabled("tips", true)
    }

    @Test
    fun anAllowedTipsNoticeBecomesAnInboxRow() {
        val wrote = GsNotifier.record(
            context = ctx,
            kind = GsNotifier.Kind.TIPS,
            title = "Personalization applied",
            body = "Your preferences have been updated. We'll now tailor responses to your style and interests.",
        )

        assertTrue(wrote)
        val rows = NoticeInbox.read(ctx)
        assertEquals(1, rows.size)
        assertEquals("tips", rows.single().kindId)
        assertFalse(rows.single().read)
    }

    @Test
    fun theTipsSwitchStopsItsOwnProducer() {
        SettingsStore.updateNoticeEnabled("tips", false)

        assertFalse(
            GsNotifier.record(
                context = ctx,
                kind = GsNotifier.Kind.TIPS,
                title = "Personalization applied",
                body = "Your preferences have been updated.",
            )
        )
        assertTrue(NoticeInbox.read(ctx).isEmpty())
    }

    @Test
    fun theMasterSwitchStopsEveryKind() {
        SettingsStore.updatePushNotifications(false)

        assertFalse(
            GsNotifier.record(
                context = ctx,
                kind = GsNotifier.Kind.TIPS,
                title = "Personalization applied",
                body = "Your preferences have been updated.",
            )
        )
        assertTrue(NoticeInbox.read(ctx).isEmpty())
    }

    @Test
    fun emptyCopyNeverBecomesARow() {
        assertFalse(
            GsNotifier.record(ctx, GsNotifier.Kind.TIPS, "Personalization applied", "   ")
        )
        assertTrue(NoticeInbox.read(ctx).isEmpty())
    }
}
