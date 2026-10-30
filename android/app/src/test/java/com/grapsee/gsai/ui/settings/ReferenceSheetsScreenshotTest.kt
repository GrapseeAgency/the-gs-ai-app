package com.grapsee.gsai.ui.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.grapsee.gsai.data.AccountStore
import com.grapsee.gsai.data.PlanStore
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.integrations.ConnectedAppsScreen
import com.grapsee.gsai.ui.notifications.NotificationsInboxScreen
import com.grapsee.gsai.ui.subscription.SubscriptionUsageScreen
import com.grapsee.gsai.ui.support.HelpSupportScreen
import com.grapsee.gsai.ui.theme.TheGsAiTheme
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The six owner sheets (docs/reference/pending) rendered on the JVM at the
 * mock's own canvas — 393×852 — so the layout can be compared with the PNGs
 * rather than described. Gated like the settings captures: nothing runs unless
 * `-Dgs.screenshots=true`.
 *
 * The inbox fixture is TEST-ONLY: the shipped store starts empty and the screen
 * says so. Seeding it here is how the row geometry (unread dot, category tag,
 * two-line body, 56pt pitch) can be seen at all without shipping invented data.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReferenceSheetsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    /**
     * A fixed 18:00 today. The inbox's Today/Earlier split and "2h ago" labels
     * are read from this, not the wall clock, so a capture run at 00:30 cannot
     * collapse three notices into Earlier and change the card count the gate
     * measures.
     */
    private var fixedNow: Long = 0L

    @Before
    fun onlyOnRequest() {
        assumeTrue(System.getProperty("gs.screenshots") == "true")
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("gs_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        ctx.getSharedPreferences("gs_notice_inbox", Context.MODE_PRIVATE)
            .edit().clear().commit()
        SettingsStore.init(ctx)
        PlanStore.init(ctx)
        // No install in this harness has been through a purchase path; keep a
        // stale activation on this machine from turning the hero "Active".
        PlanStore.clearActivation(ctx)
        AccountStore.setDisplayName(ctx, "Arafat")
        AccountStore.setEmail(ctx, "arafat@grapsee.com")
        fixedNow = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 18)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        seedInbox(ctx, fixedNow)
    }

    private fun shoot(path: String, content: @Composable () -> Unit) {
        compose.setContent { TheGsAiTheme(darkTheme = false) { content() } }
        compose.onRoot().captureRoboImage(path)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun notificationsAlerts() =
        shoot("build/screenshots/ref-notifications-alerts.png") {
            NotificationsAlertsScreen(onBack = {})
        }

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun notificationsInbox() =
        shoot("build/screenshots/ref-notifications-inbox.png") {
            NotificationsInboxScreen(onBack = {}, onNavigate = {}, now = fixedNow)
        }

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun accountSecurity() =
        shoot("build/screenshots/ref-account-security.png") {
            AccountSecurityScreen(onBack = {}, onNavigate = {})
        }

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun subscriptionUsage() =
        shoot("build/screenshots/ref-subscription-usage.png") {
            SubscriptionUsageScreen(onBack = {}, onNavigate = {})
        }

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun helpSupport() =
        shoot("build/screenshots/ref-help-support.png") {
            HelpSupportScreen(onBack = {}, onNavigate = {})
        }

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun connectedApps() =
        shoot("build/screenshots/ref-connected-apps.png") {
            ConnectedAppsScreen(onBack = {}, onNavigate = {})
        }

    private fun seedInbox(ctx: Context, now: Long) {
        val rows = listOf(
            Triple("ai-updates", "New feature available", "You can now use image generation in chat. Explore the new creative tools and bring your ideas to life.") to (2L * 3_600_000),
            Triple("account-activity", "Account update", "Your profile information has been updated. We've saved your latest changes successfully.") to (4L * 3_600_000),
            Triple("product-updates", "System update", "Nexly is now more powerful. Improved performance, better reasoning and more.") to (12L * 3_600_000),
            Triple("messages", "New message", "Alex mentioned you in a conversation. \"That's a great idea! Let's try it.\"") to (8L * 3_600_000),
            Triple("tips", "Personalization applied", "Your preferences have been updated. We'll now tailor responses to your style and interests.") to (24L * 3_600_000),
            Triple("account-activity", "Privacy reminder", "Review your data and privacy settings. Keep your information safe and in your control.") to (48L * 3_600_000),
        )
        val arr = JSONArray()
        rows.forEachIndexed { index, (row, age) ->
            val (kind, title, body) = row
            arr.put(
                JSONObject().apply {
                    put("id", now - age + index)
                    put("kindId", kind)
                    put("title", title)
                    put("body", body)
                    put("createdAt", now - age)
                    put("read", index % 2 == 1)
                }
            )
        }
        ctx.getSharedPreferences("gs_notice_inbox", Context.MODE_PRIVATE)
            .edit().putString("gs.notice.inbox.entries", arr.toString()).commit()
    }
}
