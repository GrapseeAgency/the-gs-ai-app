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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.PlanStore
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefInfoCard
import com.grapsee.gsai.ui.components.RefNavRow
import com.grapsee.gsai.ui.components.RefQuietButton
import com.grapsee.gsai.ui.components.RefRowDivider
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme

/**
 * One help topic. This is app CONTENT, not invented telemetry: each answer
 * describes something this build actually does, and a topic whose feature does
 * not exist is not listed.
 */
private data class HelpTopic(val question: String, val answer: String)

/**
 * Help & Support — docs/reference/pending/help-support.png.
 *
 * The sheet's search, category grid, popular topics and the three "Still need
 * help?" tiles are reproduced in order. What is real:
 *  - search filters the shipped topic list (it is not decorative);
 *  - "Ask Nexly" opens a new chat;
 *  - Contact Support and Report a Problem compose a real mail intent carrying
 *    the device facts a support engineer would need.
 *
 * What is NOT real: Live Chat. There is no support backend and no agent on the
 * other end, so the tile keeps its slot and says so instead of opening a
 * window that would never answer.
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

    val topics = remember {
        listOf(
            HelpTopic(
                "How do I change my AI model?",
                "Settings → AI & Intelligence opens the model screen; the effort tile is remembered per new chat.",
            ),
            HelpTopic(
                "Where is my data stored?",
                "Conversations and settings live on this device. Privacy & Data Controls can export or erase them.",
            ),
            HelpTopic(
                "Manage my subscription",
                "Subscription & Usage shows the plan, its $12.99 monthly price and this month's real usage.",
            ),
            HelpTopic(
                "Notifications aren't arriving",
                "Check the master switch, the per-type switch and Android's own permission. Quiet hours also hold notices back.",
            ),
            HelpTopic(
                "How do I turn on Biometric Login?",
                "Account & Security → Biometric Login asks for your fingerprint or face, and only switches on once it succeeds.",
            ),
            HelpTopic(
                "Why does an update say it is available?",
                "The app checks its release feed and says so once per version, with a route straight to Your chats.",
            ),
        )
    }

    val visible = if (query.isBlank()) {
        topics
    } else {
        topics.filter {
            it.question.contains(query, ignoreCase = true) ||
                it.answer.contains(query, ignoreCase = true)
        }
    }

    fun mail(subject: String, body: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@grapsee.com")).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(
                Intent.EXTRA_TEXT,
                // Same truth as every other surface: a plan name is only
                // reported when an activation was actually recorded.
                body + "\n\n—\nPlan: " +
                    (if (PlanStore.active) PlanStore.planName else "Not subscribed") +
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
        // ---- Search (real filter over the shipped topics) -----------------
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.glassSurface)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RefWellTile(GsIcons.Search, GsWellTone.Blue, 24.dp)
            Spacer(Modifier.width(10.dp))
            // A real field, not a painted one: typing here filters the topic
            // list below, so the search the sheet draws actually searches.
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    fontSize = 12.5.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textPrimary,
                ),
                decorationBox = { inner ->
                    if (query.isBlank()) {
                        Text(
                            "Search for help, topics, or keywords…",
                            fontSize = 12.5.sp,
                            fontFamily = GsFonts.Product,
                            color = colors.textTertiary,
                        )
                    }
                    inner()
                },
            )
        }

        Spacer(Modifier.height(12.dp))
        // ---- Quick help ---------------------------------------------------
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            GsWellTone.Lilac.ink.copy(alpha = 0.14f),
                            GsWellTone.Rose.ink.copy(alpha = 0.10f),
                        )
                    )
                ),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RefWellTile(GsIcons.Sparkles, GsWellTone.Lilac, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Need quick help?",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GsFonts.Product,
                        color = colors.textPrimary,
                    )
                    Text(
                        "Try our AI assistant for instant answers to common questions.",
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textTertiary,
                    )
                }
                RefQuietButton(
                    label = "Ask Nexly ›",
                    onClick = { onNavigate(com.grapsee.gsai.ui.navigation.GsRoutes.chat(null)) },
                    // RefQuietButton bakes in fillMaxWidth(); inside a Row that
                    // starves the weighted column and the mock's copy vanishes.
                    modifier = Modifier.width(112.dp),
                )
            }
        }

        // ---- Browse by Category -------------------------------------------
        RefGroupHeader("Browse by Category")
        RefCard(role = GsGlassRole.CardLight) {
            CategoryRow(
                listOf(
                    Triple("FAQs", GsIcons.CircleHelp, GsWellTone.Lilac),
                    Triple("Troubleshooting", GsIcons.Wrench, GsWellTone.Blue),
                    Triple("Account & Billing", GsIcons.Receipt, GsWellTone.Peach),
                )
            )
            RefRowDivider(startIndent = 0.dp)
            CategoryRow(
                listOf(
                    Triple("Privacy & Security", GsIcons.ShieldCheck, GsWellTone.Mint),
                    Triple("Feature Guides", GsIcons.Settings, GsWellTone.Lilac),
                    Triple("Technical Support", GsIcons.MessageCircle, GsWellTone.Rose),
                )
            )
        }

        // ---- Popular topics ----------------------------------------------
        RefGroupHeader("Popular Help Topics", "Quick access to the most common issues")
        RefCard(role = GsGlassRole.CardLight) {
            if (visible.isEmpty()) {
                RefInfoCard(
                    text = "No topic matches \"$query\". Try a shorter word, or contact support below.",
                    icon = GsIcons.Search,
                )
            } else {
                visible.forEachIndexed { index, t ->
                    if (index > 0) RefRowDivider()
                    RefNavRow(
                        title = t.question,
                        subtitle = t.answer,
                        // Tapping expands nothing yet; the answer is already
                        // the subtitle. No chevron, so it promises no page.
                        onClick = null,
                        trailing = {},
                        leading = { RefWellTile(GsIcons.CircleHelp, GsWellTone.Lilac, 28.dp) },
                    )
                }
            }
        }

        // ---- Still need help ---------------------------------------------
        RefGroupHeader("Still need help?")
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HelpAction(
                    label = "Contact Support",
                    icon = GsIcons.Mail,
                    tone = GsWellTone.Peach,
                    modifier = Modifier.weight(1f),
                ) { mail("Nexly support", "Hi team,") }
                HelpAction(
                    label = "Report a Problem",
                    icon = GsIcons.Bug,
                    tone = GsWellTone.Rose,
                    modifier = Modifier.weight(1f),
                ) {
                    mail(
                        "Problem report",
                        "What happened:\n\nWhat I expected:\n\nSteps to reproduce:\n",
                    )
                }
                HelpAction(
                    label = "Live Chat",
                    icon = GsIcons.MessageCircle,
                    tone = GsWellTone.Blue,
                    modifier = Modifier.weight(1f),
                ) {
                    // No support backend exists, so this opens the same real
                    // mail path rather than a chat window nobody is behind.
                    mail("Nexly live chat request", "Hi team, I'd like to talk to someone.")
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        RefInfoCard(
            icon = GsIcons.Heart,
            text = "Your satisfaction matters to us. Replies are answered by the team that builds Nexly.",
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun CategoryRow(items: List<Triple<String, androidx.compose.ui.graphics.vector.ImageVector, GsWellTone>>) {
    val colors = GsTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (label, icon, tone) ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.glassSurface.copy(alpha = 0.55f))
                    .padding(10.dp),
            ) {
                RefWellTile(icon, tone, 30.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    label,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = GsFonts.Product,
                    color = colors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun HelpAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tone: GsWellTone,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = GsTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.glassSurface.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RefWellTile(icon, tone, 30.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            fontSize = 10.5.sp,
            fontFamily = GsFonts.Product,
            color = colors.textPrimary,
        )
    }
}
