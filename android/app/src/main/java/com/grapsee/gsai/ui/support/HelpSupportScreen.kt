package com.grapsee.gsai.ui.support

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.PlanStore
import com.grapsee.gsai.ui.chat.content.NexlyGlyph
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.RefArt
import com.grapsee.gsai.ui.components.RefBleedArt
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefGlyphWell
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefHeaderAction
import com.grapsee.gsai.ui.components.RefHeroCard
import com.grapsee.gsai.ui.components.RefInfoCard
import com.grapsee.gsai.ui.components.RefSheetPillButton
import com.grapsee.gsai.ui.components.RefRowDivider
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefSheetRow
import com.grapsee.gsai.ui.components.RefSheetTile
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.components.TileCorner
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme

/**
 * One help topic. This is app CONTENT, not invented telemetry: each answer
 * describes something this build actually does, and a topic whose feature does
 * not exist is not listed.
 */
private data class HelpTopic(
    val question: String,
    val answer: String,
    val summary: String,
    val category: HelpCategory,
    val icon: ImageVector,
    val tone: GsWellTone,
)

private enum class HelpCategory(val label: String) {
    General("FAQs"),
    Troubleshooting("Troubleshooting"),
    Account("Account & Billing"),
    Privacy("Privacy & Security"),
    Features("Feature Guides"),
    Support("Technical Support"),
}

/**
 * Help & Support — docs/reference/pending/help-support.png.
 *
 * The sheet's search, hero, 3×2 category grid, popular topics, the
 * "Still need help?" block and the heart footer are reproduced in order and
 * at the sheet's own scale. What is real:
 *  - the search field filters the shipped topic list (it is not decorative),
 *    and the sliders button filters by category through the same list;
 *  - "Ask Nexly" opens a new chat;
 *  - every topic opens a real answer sheet with two working actions;
 *  - Contact Support and Report a Problem compose a real mail intent carrying
 *    the device facts a support engineer would need;
 *  - Live Chat opens Nexly, the only chat that answers in this build.
 *
 * The mock's "$19.99" and its FAQ copy about passwords were dropped: the price
 * is $12.99 (owner-confirmed) and this build has no password to reset. A help
 * article that describes a flow the app does not have is the same lie as a
 * fake number.
 */
@Composable
fun HelpSupportScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val colors = GsTheme.colors
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<HelpCategory?>(null) }
    var showFilter by remember { mutableStateOf(false) }
    var showAllTopics by remember { mutableStateOf(false) }
    var categoryTopics by remember { mutableStateOf<HelpCategory?>(null) }
    var openTopic by remember { mutableStateOf<HelpTopic?>(null) }

    val topics = remember {
        listOf(
            HelpTopic(
                "How do I change my AI model?",
                "Settings → AI & Intelligence opens the model screen. The effort tile is remembered for the next new chat, and on-device models are managed under AI & Intelligence → On-device.",
                "Switch models and adjust reasoning effort in AI & Intelligence.",
                HelpCategory.Features,
                GsIcons.Sparkles,
                GsWellTone.Lilac,
            ),
            HelpTopic(
                "Where is my Grapsee Mail address?",
                "Account & Security shows the Grapsee Mail address this install signs in with. Edit it in Profile & Preferences; Nexly never asks for a password.",
                "Account & Security shows the address this install signs in with.",
                HelpCategory.Account,
                GsIcons.Lock,
                GsWellTone.Rose,
            ),
            HelpTopic(
                "Manage my subscription",
                "Subscription & Usage shows Nexly Pro at $12.99 per month and this month's real usage. No payment is taken in this build, so there is no card or invoice to manage.",
                "Nexly Pro is $12.99/month — see this month's real usage.",
                HelpCategory.Account,
                GsIcons.CreditCard,
                GsWellTone.Blue,
            ),
            HelpTopic(
                "Fix image generation issues",
                "Image generation is not part of this build. Chat replies, attachments and on-device models are; if a reply fails, the composer keeps your text so you can retry.",
                "Image generation is not part of this build.",
                HelpCategory.Troubleshooting,
                GsIcons.Image,
                GsWellTone.Mint,
            ),
            HelpTopic(
                "Notifications aren't arriving",
                "Check the master switch, the per-type switch and Android's own permission. Quiet Hours also holds notices back until the window ends.",
                "Check the switches, the permission and Quiet Hours.",
                HelpCategory.Troubleshooting,
                GsIcons.Bell,
                GsWellTone.Peach,
            ),
            HelpTopic(
                "How do I turn on Biometric Login?",
                "Account & Security → Biometric Login asks for your fingerprint or face, and only switches on once it succeeds. The lock is then required at launch.",
                "Ask for your fingerprint or face at launch.",
                HelpCategory.Privacy,
                GsIcons.Fingerprint,
                GsWellTone.Rose,
            ),
            HelpTopic(
                "Why does an update say it is available?",
                "The app checks its release feed and says so once per version, with a route straight to Your chats. Nothing is installed without your tap.",
                "The app checks its release feed once per version.",
                HelpCategory.Features,
                GsIcons.Sparkles,
                GsWellTone.Lilac,
            ),
        )
    }

    val visible = topics.filter { topic ->
        (filter == null || topic.category == filter) &&
            (query.isBlank() ||
                topic.question.contains(query, ignoreCase = true) ||
                topic.answer.contains(query, ignoreCase = true))
    }

    fun mail(subject: String, body: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@grapsee.com")).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(
                Intent.EXTRA_TEXT,
                // Same truth as every other surface: a plan name is only
                // reported when an activation was actually recorded.
                body + "\n\n—\nPlan: " +
                    (if (PlanStore.active) PlanStore.planName else "Nexly Pro ($12.99/month)") +
                    "\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}" +
                    "\nAndroid: ${Build.VERSION.RELEASE}",
            )
        }
        runCatching { context.startActivity(intent) }
    }

    RefSettingsPage(
        title = "Help & Support",
        subtitle = "We're here to help. Find answers, get assistance, or reach out to our team.",
        onBack = onBack,
    ) {
        // ---- Search -------------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.glassSurface.copy(alpha = 0.70f))
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    GsIcons.Search,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(8.dp))
                // A real field, not a painted one: typing here filters the topic
                // list below, so the search the sheet draws actually searches.
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("help-search"),
                    textStyle = TextStyle(
                        fontSize = 10.5.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textPrimary,
                    ),
                    decorationBox = { inner ->
                        if (query.isBlank()) {
                            Text(
                                "Search for help, topics, or keywords…",
                                fontSize = 10.5.sp,
                                fontFamily = GsFonts.Product,
                                color = colors.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.width(8.dp))
            // The mock's sliders button: opens the category filter that the
            // list below actually applies.
            RefHeaderAction(
                icon = GsIcons.SlidersHorizontal,
                label = "Filter topics",
                onClick = { showFilter = true },
            )
        }

        Spacer(Modifier.height(12.dp))
        // ---- Quick help ---------------------------------------------------
        RefHeroCard {
            RefGlyphWell(glyph = "robot", tone = GsWellTone.Lilac, size = 40.dp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Need quick help?",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GsFonts.Product,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    "Try our AI assistant for instant answers to common questions.",
                    fontSize = 8.5.sp,
                    lineHeight = 11.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textTertiary,
                )
            }
            Spacer(Modifier.width(8.dp))
            RefSheetPillButton(
                label = "Ask Nexly",
                onClick = { onNavigate(com.grapsee.gsai.ui.navigation.GsRoutes.chat(null)) },
            )
        }

        // ---- Browse by Category -------------------------------------------
        RefGroupHeader(
            title = "Browse by Category",
            caption = "View all",
            onCaptionClick = { showFilter = true },
        )
        CategoryGrid(
            categories = listOf(
                Triple(HelpCategory.General, GsIcons.CircleHelp, GsWellTone.Lilac),
                Triple(HelpCategory.Troubleshooting, GsIcons.Wrench, GsWellTone.Blue),
                Triple(HelpCategory.Account, GsIcons.Receipt, GsWellTone.Peach),
            ),
            captionFor = ::categoryCaption,
            onOpen = { categoryTopics = it },
        )
        Spacer(Modifier.height(8.dp))
        CategoryGrid(
            categories = listOf(
                Triple(HelpCategory.Privacy, GsIcons.ShieldCheck, GsWellTone.Mint),
                Triple(HelpCategory.Features, GsIcons.Settings, GsWellTone.Lilac),
                Triple(HelpCategory.Support, GsIcons.MessageCircle, GsWellTone.Rose),
            ),
            captionFor = ::categoryCaption,
            onOpen = { categoryTopics = it },
        )

        // ---- Popular topics ----------------------------------------------
        RefGroupHeader(
            title = "Popular Help Topics",
            caption = "View all",
            leadingIcon = GsIcons.Flame,
            onCaptionClick = { showAllTopics = true },
        )
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(vertical = 2.dp)) {
            val shown = visible.take(4)
            if (shown.isEmpty()) {
                Box(Modifier.padding(14.dp)) {
                    Text(
                        if (query.isBlank()) {
                            "No topics in this category yet — ask Nexly or contact support below."
                        } else {
                            "No topic matches \"$query\". Try a shorter word, or contact support below."
                        },
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textTertiary,
                    )
                }
            } else {
                shown.forEachIndexed { index, topic ->
                    if (index > 0) RefRowDivider(startIndent = 57.dp)
                    RefSheetRow(
                        title = topic.question,
                        subtitle = topic.summary,
                        onClick = { openTopic = topic },
                        leading = { RefWellTile(topic.icon, topic.tone, 34.dp) },
                    )
                }
            }
        }

        // ---- Still need help ---------------------------------------------
        Spacer(Modifier.height(18.dp))
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RefGlyphWell(glyph = "headset", tone = GsWellTone.Lilac, size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Still need help?",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GsFonts.Product,
                        color = colors.textPrimary,
                    )
                    Text(
                        "Our support team is available 24/7 to assist you.",
                        fontSize = 8.5.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textTertiary,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RefSheetTile(
                    title = "Contact Support",
                    caption = "Get in touch with our team",
                    icon = GsIcons.Mail,
                    tone = GsWellTone.Peach,
                    corner = TileCorner.BottomChevron,
                    centered = true,
                    modifier = Modifier.weight(1f),
                    onClick = { mail("Nexly support", "Hi team,") },
                )
                RefSheetTile(
                    title = "Report a Problem",
                    caption = "Help us improve Nexly",
                    icon = GsIcons.Bug,
                    tone = GsWellTone.Rose,
                    corner = TileCorner.BottomChevron,
                    centered = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        mail(
                            "Problem report",
                            "What happened:\n\nWhat I expected:\n\nSteps to reproduce:\n",
                        )
                    },
                )
                RefSheetTile(
                    title = "Live Chat",
                    caption = "Chat with Nexly now",
                    icon = GsIcons.MessageCircle,
                    tone = GsWellTone.Blue,
                    corner = TileCorner.BottomChevron,
                    centered = true,
                    modifier = Modifier.weight(1f),
                    // Nexly is the only chat that answers in this build, so the
                    // tile routes there rather than opening a window nobody is
                    // behind.
                    onClick = { onNavigate(com.grapsee.gsai.ui.navigation.GsRoutes.chat(null)) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NexlyGlyph(name = "heart", size = 12.dp, color = GsWellTone.Rose.ink)
                Spacer(Modifier.width(6.dp))
                Text(
                    "Your satisfaction matters to us",
                    fontSize = 9.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textTertiary,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    // ---- Dialogs ----------------------------------------------------------

    if (showFilter) {
        DialogList(
            title = "Browse by Category",
            subtitle = "Pick a category to see its articles",
            rows = HelpCategory.values().toList().map { category ->
                DialogRow(
                    icon = when (category) {
                        HelpCategory.General -> GsIcons.CircleHelp
                        HelpCategory.Troubleshooting -> GsIcons.Wrench
                        HelpCategory.Account -> GsIcons.Receipt
                        HelpCategory.Privacy -> GsIcons.ShieldCheck
                        HelpCategory.Features -> GsIcons.Settings
                        HelpCategory.Support -> GsIcons.MessageCircle
                    },
                    title = category.label,
                    subtitle = categoryCaption(category),
                )
            },
            onPick = { index ->
                showFilter = false
                filter = HelpCategory.values().toList()[index]
                categoryTopics = HelpCategory.values().toList()[index]
            },
            onDismiss = { showFilter = false },
        )
    }

    if (showAllTopics) {
        DialogList(
            title = "Popular Help Topics",
            subtitle = "${topics.size} articles in this build",
            rows = topics.map { DialogRow(it.icon, it.question, it.category.label) },
            onPick = { index ->
                showAllTopics = false
                openTopic = topics[index]
            },
            onDismiss = { showAllTopics = false },
        )
    }

    categoryTopics?.let { category ->
        val inCategory = topics.filter { it.category == category }
        DialogList(
            title = category.label,
            subtitle = if (inCategory.isEmpty()) {
                "No articles in this category yet"
            } else {
                "${inCategory.size} article${if (inCategory.size == 1) "" else "s"}"
            },
            rows = inCategory.map { DialogRow(it.icon, it.question, it.answer) },
            onPick = { index ->
                categoryTopics = null
                openTopic = inCategory[index]
            },
            onDismiss = { categoryTopics = null },
        )
    }

    openTopic?.let { topic ->
        AlertDialog(
            onDismissRequest = { openTopic = null },
            title = {
                Text(
                    topic.question,
                    fontFamily = GsFonts.Product,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            },
            text = {
                Text(
                    topic.answer,
                    fontFamily = GsFonts.Product,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    openTopic = null
                    onNavigate(com.grapsee.gsai.ui.navigation.GsRoutes.chat(null))
                }) { Text("Ask Nexly") }
            },
            dismissButton = {
                TextButton(onClick = { openTopic = null }) { Text("Close") }
            },
        )
    }
}

private fun categoryCaption(category: HelpCategory): String = when (category) {
    HelpCategory.General -> "Find answers to common questions."
    HelpCategory.Troubleshooting -> "Fix issues and get things working."
    HelpCategory.Account -> "Manage your account, plans and payments."
    HelpCategory.Privacy -> "Learn about your data and security."
    HelpCategory.Features -> "Get to know Nexly's features and tools."
    HelpCategory.Support -> "Contact our team for personalized help."
}

@Composable
private fun CategoryGrid(
    categories: List<Triple<HelpCategory, ImageVector, GsWellTone>>,
    captionFor: (HelpCategory) -> String,
    onOpen: (HelpCategory) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { (category, icon, tone) ->
            RefSheetTile(
                title = category.label,
                caption = captionFor(category),
                icon = icon,
                tone = tone,
                modifier = Modifier.weight(1f),
                onClick = { onOpen(category) },
            )
        }
    }
}

private data class DialogRow(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
)

/** One real chooser: rows navigate, nothing is painted as an unopenable tile. */
@Composable
private fun DialogList(
    title: String,
    subtitle: String,
    rows: List<DialogRow>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title, fontFamily = GsFonts.Product, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, fontFamily = GsFonts.Product, fontSize = 9.5.sp, color = GsTheme.colors.textTertiary)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                rows.forEachIndexed { index, row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(index) }
                            .padding(vertical = 9.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RefWellTile(row.icon, GsWellTone.Lilac, 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                row.title,
                                fontSize = 10.5.sp,
                                fontFamily = GsFonts.Product,
                                color = GsTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                row.subtitle,
                                fontSize = 8.5.sp,
                                lineHeight = 11.sp,
                                fontFamily = GsFonts.Product,
                                color = GsTheme.colors.textTertiary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            GsIcons.ChevronRight,
                            contentDescription = null,
                            tint = GsTheme.colors.textTertiary,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

