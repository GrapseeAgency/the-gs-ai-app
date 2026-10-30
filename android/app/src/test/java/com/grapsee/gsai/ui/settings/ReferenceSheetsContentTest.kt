package com.grapsee.gsai.ui.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.grapsee.gsai.data.PlanStore
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.integrations.ConnectedAppsScreen
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.subscription.SubscriptionUsageScreen
import com.grapsee.gsai.ui.support.HelpSupportScreen
import com.grapsee.gsai.ui.theme.TheGsAiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The six owner sheets' copy and order, asserted against the mocks rather than
 * eyeballed in a capture: every section, row and closing card must exist, in
 * the mock's order, with the truthful deltas this build cannot remove.
 *
 * This is the always-on half of the evidence. `ReferenceSheetsScreenshotTest`
 * shows what the screen looks like; this fails when a row is deleted, renamed,
 * reordered, or quietly turned back into a live control it cannot back.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class ReferenceSheetsContentTest {

    @get:Rule val compose = createComposeRule()

    @Before
    fun resetStores() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        SettingsStore.init(ctx)
        SettingsStore.updatePushNotifications(true)
        SettingsStore.updateNoticeInApp(true)
        PlanStore.init(ctx)
        PlanStore.clearActivation(ctx)
    }

    private fun render(content: @Composable () -> Unit) {
        compose.setContent { TheGsAiTheme(darkTheme = false) { content() } }
        compose.waitForIdle()
    }

    private fun assertPresent(vararg copy: String) {
        for (text in copy) {
            assertTrue(
                "the sheet is missing its mock copy: \"$text\"",
                compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }

    private fun assertAbsent(vararg copy: String) {
        for (text in copy) {
            assertEquals(
                "the sheet still draws a value the build cannot back: \"$text\"",
                0,
                compose.onAllNodesWithText(text).fetchSemanticsNodes().size,
            )
        }
    }

    private fun assertAbove(first: String, second: String) {
        val a = compose.onAllNodesWithText(first)[0].fetchSemanticsNode()
        val b = compose.onAllNodesWithText(second)[0].fetchSemanticsNode()
        assertTrue(
            "\"$first\" must sit above \"$second\" (${a.boundsInRoot.top} vs ${b.boundsInRoot.top})",
            a.boundsInRoot.top < b.boundsInRoot.top,
        )
    }

    /** For tiles that share a row: the mock's order is left to right. */
    private fun assertLeftOf(first: String, second: String) {
        val a = compose.onAllNodesWithText(first)[0].fetchSemanticsNode()
        val b = compose.onAllNodesWithText(second)[0].fetchSemanticsNode()
        assertTrue(
            "\"$first\" must sit left of \"$second\" (${a.boundsInRoot.left} vs ${b.boundsInRoot.left})",
            a.boundsInRoot.left < b.boundsInRoot.left,
        )
    }

    @Test
    fun alertsCarriesEveryRowInTheMocksOrder() {
        render { NotificationsAlertsScreen(onBack = {}) }

        assertPresent(
            "Notifications", "Control what you want to be notified about.",
            "Notification Types", "Choose what to receive",
            "New Messages", "AI Updates", "Tips & Suggestions",
            "Account Activity", "Product Updates",
            "Notification Channels", "How you want to be notified",
            "Push Notifications", "Email Notifications", "In-App Notifications",
            "Quiet Hours", "Pause notifications when you need rest",
            "Do Not Disturb",
            "You're in control. Notifications help you stay connected, without getting in the way.",
        )
        assertAbove("New Messages", "AI Updates")
        assertAbove("AI Updates", "Tips & Suggestions")
        assertAbove("Tips & Suggestions", "Account Activity")
        assertAbove("Account Activity", "Product Updates")
        assertAbove("Push Notifications", "Email Notifications")
        assertAbove("Email Notifications", "In-App Notifications")
        assertAbove("Quiet Hours", "Do Not Disturb")

        // No mail is sent from this build: the row keeps its slot, disabled.
        compose.onNodeWithText("Email Notifications").assertIsNotEnabled()
    }

    @Test
    fun securityCarriesEveryRowAndKeepsTheUnbackedControlsOff() {
        render { AccountSecurityScreen(onBack = {}, onNavigate = {}) }

        assertPresent(
            "Account & Security", "Keep your account safe and in your control",
            "Sign-in Methods", "Manage how you sign in",
            "Grapsee Mail", "Primary", "Google", "Apple",
            "Add another sign-in method",
            "Password & Authentication", "Manage your security",
            "Change Password", "Two-Factor Authentication", "Biometric Login",
            "Active Sessions", "See where you're logged in",
            "Current Session", "Other Sessions",
            "Manage Devices", "Control your devices",
            "All Devices", "Your account is protected",
        )
        // No password store, no second factor server: both drawn disabled.
        compose.onNodeWithText("Two-Factor Authentication").assertIsNotEnabled()
        assertAbove("Google", "Apple")
        assertAbove("Change Password", "Two-Factor Authentication")
        assertAbove("Current Session", "Other Sessions")
    }

    @Test
    fun subscriptionChargesTheOwnerPriceAndKeepsTheMissingServicesHonest() {
        render { SubscriptionUsageScreen(onBack = {}, onNavigate = {}) }

        assertPresent(
            "Subscription & Usage", "Your plan, usage, and billing details",
            "Nexly Pro", "Not activated", "Manage Plan",
            "Plan Details", "Current plan information",
            "Plan Name", "Billing Cycle", "Next Billing Date", "Price",
            "Usage Overview", "Your current month's usage",
            "Messages", "Images", "File Uploads", "Advanced Reasoning",
            "Billing & Payment", "Manage your payment method",
            "Payment Method", "Not set", "Billing History",
            "Upgrade Options", "View Plan", "Need help?",
        )
        assertPresent("\$12.99 / month")
        // The mock's price and its invented card/invoice cannot appear.
        assertAbsent("\$19.99", "\$19.99 / month", "Visa", "Active")
        assertAbove("Plan Details", "Usage Overview")
        assertAbove("Usage Overview", "Billing & Payment")
        assertAbove("Billing & Payment", "Upgrade Options")
    }

    @Test
    fun helpCarriesEveryCategoryTopicAndContactRoute() {
        render { HelpSupportScreen(onBack = {}, onNavigate = {}) }

        assertPresent(
            "Help & Support", "Browse by Category", "View all",
            "FAQs", "Troubleshooting", "Account & Billing",
            "Privacy & Security", "Feature Guides", "Technical Support",
            "Popular Help Topics",
            "How do I change my AI model?",
            "Where is my Grapsee Mail address?",
            "Manage my subscription",
            "Fix image generation issues",
            "Still need help?",
            "Contact Support", "Report a Problem", "Live Chat",
            "Your satisfaction matters to us",
        )
        // The mock's password-reset article does not exist in this build; its
        // slot now explains where the Grapsee Mail address comes from.
        assertAbsent("Reset my password")
        assertAbove("FAQs", "Privacy & Security")
        assertLeftOf("Privacy & Security", "Feature Guides")
    }

    @Test
    fun connectedAppsDrawsSixProvidersAndFiveAvailableTiles() {
        render { ConnectedAppsScreen(onBack = {}, onNavigate = {}) }

        assertPresent(
            "Connected Apps & Integrations",
            "More possibilities together",
            "Your Connected Apps", "Manage Permissions",
            "Google", "GitHub", "Notion", "Spotify", "Slack", "Microsoft",
            "Available Integrations", "Browse All",
            "Dropbox", "Figma", "Trello", "Discord", "Zoom",
            "Your data, your control", "Connect New App or Service",
        )
        // No OAuth client, token store or adapter exists: every provider is
        // honestly disconnected, and the mock's green/blue pills are gone.
        val pills = compose.onAllNodesWithText("Not Connected").fetchSemanticsNodes().size
        assertTrue(
            "every provider must carry exactly one Not Connected pill (found $pills)",
            pills == 6,
        )
        assertAbsent("Connected", "Limited Access")
        assertAbove("Google", "GitHub")
        assertAbove("Slack", "Microsoft")
        assertLeftOf("Dropbox", "Figma")
    }

    @Test
    fun helpSearchFiltersTheTopicsForReal() {
        render { HelpSupportScreen(onBack = {}, onNavigate = {}) }

        compose.onNodeWithTag("help-search").performTextInput("model")
        compose.waitForIdle()

        assertTrue(
            "the model topic must survive its own search",
            compose.onAllNodesWithText("How do I change my AI model?")
                .fetchSemanticsNodes().isNotEmpty(),
        )
        assertEquals(
            "a non-matching topic must leave the filtered list",
            0,
            compose.onAllNodesWithText("Manage my subscription")
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun subscriptionManagePlanAndSupportResolveToRealRoutes() {
        val routes = mutableListOf<String>()
        render { SubscriptionUsageScreen(onBack = {}, onNavigate = { routes += it }) }

        compose.onNodeWithText("Manage Plan").performScrollTo().performClick()
        compose.onNodeWithText("Need help?").performScrollTo().performClick()

        assertEquals(
            listOf(GsRoutes.BILLING, GsRoutes.SETTINGS_HELP),
            routes,
        )
    }

    @Test
    fun privacyDataControlsCarriesItsGroups() {
        render { PrivacySettingsScreen(onBack = {}, onNavigate = {}) }

        assertPresent(
            "Privacy & Data Controls", "Your data, your control",
            "Your privacy matters",
            "Data & Permissions", "Manage what Nexly can access",
            "App Permissions", "Personalization & Ads", "Third-Party Integrations",
            "Conversation & Activity", "Control your chat data",
            "Chat History", "Search History", "Data Retention",
            "Export & Deletion", "Download or delete your data",
            "Export Your Data", "Delete Your Account",
            "We respect your privacy",
        )
    }

    @Test
    fun theSettingsHubReachesTheFourSettingsSheets() {
        val routes = mutableListOf<String>()
        render { SettingsHubScreen(onBack = {}, onNavigate = { routes += it }) }

        compose.onNodeWithText("Notifications").performScrollTo().performClick()
        compose.onNodeWithText("Device & Account").performScrollTo().performClick()
        // These three were registered routes with no entry point until the hub
        // gained them; the click proves each one is now reachable, not just
        // composed.
        compose.onNodeWithText("Chat & Conversations").performScrollTo().performClick()
        compose.onNodeWithText("Voice & Speech").performScrollTo().performClick()
        compose.onNodeWithText("Accessibility").performScrollTo().performClick()
        compose.onNodeWithText("Help & Support").performScrollTo().performClick()
        compose.onNodeWithText("View plan").performScrollTo().performClick()

        assertEquals(
            listOf(
                GsRoutes.SETTINGS_NOTIFICATIONS,
                GsRoutes.SETTINGS_ACCOUNT,
                GsRoutes.SETTINGS_CHAT,
                GsRoutes.SETTINGS_VOICE,
                GsRoutes.SETTINGS_ACCESSIBILITY,
                GsRoutes.SETTINGS_HELP,
                GsRoutes.SETTINGS_PLAN,
            ),
            routes,
        )
    }

    @Test
    fun privacyReachesConnectedApps() {
        var route: String? = null
        render { PrivacySettingsScreen(onBack = {}, onNavigate = { route = it }) }

        compose.onNodeWithText("Third-Party Integrations")
            .performScrollTo()
            .performClick()

        assertEquals(GsRoutes.SETTINGS_INTEGRATIONS, route)
    }
}
