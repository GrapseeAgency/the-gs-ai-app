package com.grapsee.gsai.ui.support

import androidx.compose.ui.graphics.vector.ImageVector
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.icons.GsIcons

/**
 * THE HELP CATALOGUE — one list, two surfaces.
 *
 * The topics used to be a `private` list inside [HelpSupportScreen], which is
 * why tapping one could only ever open a dialog: there was no way to address an
 * article by id, so there was nowhere for a page to live.
 *
 * Every answer here describes something this build actually does. A topic whose
 * feature does not exist is not listed — an article about image generation in a
 * build without image generation is the same lie as a fake number.
 */
enum class HelpCategory(val label: String) {
    General("FAQs"),
    Troubleshooting("Troubleshooting"),
    Account("Account & Billing"),
    Privacy("Privacy & Security"),
    Features("Feature Guides"),
    Support("Technical Support"),
}

data class HelpArticle(
    val id: String,
    val question: String,
    val answer: String,
    val summary: String,
    val category: HelpCategory,
    val icon: ImageVector,
    val tone: GsWellTone,
    /** The same answer, split into the steps a reader follows. */
    val steps: List<String> = emptyList(),
    /** Ids of the articles to read next. */
    val related: List<String> = emptyList(),
) {
    /** The label on the article page: a short, human category name. */
    val kicker: String get() = category.label
}

object HelpCatalog {

    val topics: List<HelpArticle> = listOf(
        HelpArticle(
            id = "change-model",
            question = "How do I change my AI model?",
            answer = "Settings → AI & Intelligence opens the model screen. The effort tile is remembered for the next new chat, and on-device models are managed under AI & Intelligence → On-device.",
            summary = "Switch models and adjust reasoning effort in AI & Intelligence.",
            category = HelpCategory.Features,
            icon = GsIcons.Sparkles,
            tone = GsWellTone.Lilac,
            steps = listOf(
                "Open Settings, then AI & Intelligence.",
                "Pick a model; the choice applies to the next new chat.",
                "Effort is a separate control — it changes how long the model may think.",
            ),
            related = listOf("update-available", "notifications"),
        ),
        HelpArticle(
            id = "mail-address",
            question = "Where is my Grapsee Mail address?",
            answer = "Account & Security shows the Grapsee Mail address this install signs in with. Edit it in Profile & Preferences; Nexly never asks for a password.",
            summary = "Account & Security shows the address this install signs in with.",
            category = HelpCategory.Account,
            icon = GsIcons.Lock,
            tone = GsWellTone.Rose,
            steps = listOf(
                "Settings → Account & Security.",
                "The address is the identity line at the top of the page.",
                "To change it, edit the identity card in Profile & Preferences.",
            ),
            related = listOf("biometric-login", "subscription"),
        ),
        HelpArticle(
            id = "subscription",
            question = "Manage my subscription",
            answer = "Subscription & Usage shows Nexly Pro at $12.99 per month and this month's real usage. No payment is taken in this build, so there is no card or invoice to manage.",
            summary = "Nexly Pro is $12.99/month — see this month's real usage.",
            category = HelpCategory.Account,
            icon = GsIcons.CreditCard,
            tone = GsWellTone.Blue,
            steps = listOf(
                "Settings → Plan, or Profile → Subscription & Usage.",
                "Usage counts messages and attachments actually sent this month.",
                "Nothing can be charged: there is no payment processor in this build.",
            ),
            related = listOf("mail-address"),
        ),
        HelpArticle(
            id = "image-generation",
            question = "Fix image generation issues",
            answer = "Image generation is not part of this build. Chat replies, attachments and on-device models are; if a reply fails, the composer keeps your text so you can retry.",
            summary = "Image generation is not part of this build.",
            category = HelpCategory.Troubleshooting,
            icon = GsIcons.Image,
            tone = GsWellTone.Mint,
            steps = listOf(
                "If a reply fails, the draft stays in the composer — retry from there.",
                "Check that the device is online; the transcript is stored locally either way.",
                "Image generation is not implemented, so no prompt will produce one.",
            ),
            related = listOf("notifications", "change-model"),
        ),
        HelpArticle(
            id = "notifications",
            question = "Notifications aren't arriving",
            answer = "Check the master switch, the per-type switch and Android's own permission. Quiet Hours also holds notices back until the window ends.",
            summary = "Check the switches, the permission and Quiet Hours.",
            category = HelpCategory.Troubleshooting,
            icon = GsIcons.Bell,
            tone = GsWellTone.Peach,
            steps = listOf(
                "Settings → Notifications: the master switch must be on.",
                "Each kind (messages, updates, account) has its own switch.",
                "If Quiet Hours is on, notices wait until the window closes.",
                "Finally, check the system permission for Nexly in Android settings.",
            ),
            related = listOf("change-model"),
        ),
        HelpArticle(
            id = "biometric-login",
            question = "How do I turn on Biometric Login?",
            answer = "Account & Security → Biometric Login asks for your fingerprint or face, and only switches on once it succeeds. The lock is then required at launch.",
            summary = "Ask for your fingerprint or face at launch.",
            category = HelpCategory.Privacy,
            icon = GsIcons.Fingerprint,
            tone = GsWellTone.Rose,
            steps = listOf(
                "Settings → Account & Security → Biometric Login.",
                "The switch only turns on after a successful scan.",
                "The lock is then required every time the app is opened.",
            ),
            related = listOf("mail-address"),
        ),
        HelpArticle(
            id = "update-available",
            question = "Why does an update say it is available?",
            answer = "The app checks its release feed and says so once per version, with a route straight to Your chats. Nothing is installed without your tap.",
            summary = "The app checks its release feed once per version.",
            category = HelpCategory.Features,
            icon = GsIcons.Sparkles,
            tone = GsWellTone.Lilac,
            steps = listOf(
                "The check runs once per app version, not on every launch.",
                "The notice appears in Your chats, where the install flow starts.",
                "Nothing downloads or installs until you tap it.",
            ),
            related = listOf("change-model"),
        ),
    )

    fun byId(id: String): HelpArticle? = topics.firstOrNull { it.id == id }

    fun related(article: HelpArticle): List<HelpArticle> =
        article.related.mapNotNull { byId(it) }

    fun countFor(category: HelpCategory): Int = topics.count { it.category == category }
}
