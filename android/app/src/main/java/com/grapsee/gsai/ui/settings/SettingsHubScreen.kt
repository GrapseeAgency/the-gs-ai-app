package com.grapsee.gsai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.AccountStore
import com.grapsee.gsai.ui.components.RefArt
import com.grapsee.gsai.ui.components.RefArtwork
import com.grapsee.gsai.ui.components.RefAvatar
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefDivider
import com.grapsee.gsai.ui.components.RefFootnote
import com.grapsee.gsai.ui.components.RefPage
import com.grapsee.gsai.ui.components.RefRow
import com.grapsee.gsai.ui.components.RefSectionLabel
import com.grapsee.gsai.ui.components.RefStatusPill
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsTheme

/**
 * SETTINGS HUB — the reference's front door.
 *
 * The previous screen was nine bordered cards that each expanded in place.
 * That is a database with a scrollbar: the reader never knows whether a row
 * is a destination or a disclosure, and the page grows to four screens tall.
 *
 * The reference model is flatter and faster: an identity card, then six
 * groups of one-line destinations. Every row pushes a page. Every group is
 * one glass card. The reader can see the whole surface in two thumb-scrolls
 * and the back gesture always means "up one level", never "collapse".
 */
@Composable
fun SettingsHubScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onLocalAiRequested: () -> Unit = {},
) {
    val context = LocalContext.current
    val colors = GsTheme.colors
    val name = AccountStore.displayName(context)
    val email = AccountStore.email(context)

    RefPage(
        title = "Settings",
        subtitle = "Customize your GS experience.",
        onBack = onBack,
        largeTitle = true
    ) {
        // Identity — the reference's Nexly orb card.
        RefCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClickLabel = "Account"
                    ) { onNavigate(GsRoutes.SETTINGS_ACCOUNT) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFFFE3CE), Color(0xFFFFC9A8), Color(0xFFFFB38F))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    RefArtwork(art = RefArt.Bloom, size = 38.dp, contentDescription = null)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Nexly",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "AI Companion",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary
                    )
                }
                Icon(
                    GsIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        RefSectionLabel("Account")
        RefCard {
            RefRow(
                title = "Profile",
                subtitle = "Manage your profile",
                icon = GsIcons.UserRound,
                onClick = { onNavigate(GsRoutes.PROFILE) }
            )
            RefDivider()
            RefRow(
                title = "Subscription",
                subtitle = "Free Plan",
                icon = GsIcons.CreditCard,
                onClick = { onNavigate(GsRoutes.BILLING) }
            )
        }

        RefSectionLabel("Chat Settings")
        RefCard {
            RefRow(
                title = "AI Model",
                subtitle = "Nexly Core",
                icon = GsIcons.Brain,
                onClick = { onNavigate(GsRoutes.SETTINGS_AI) }
            )
            RefDivider()
            RefRow(
                title = "Response Style",
                subtitle = "Balanced",
                icon = GsIcons.Sparkles,
                onClick = { onNavigate(GsRoutes.SETTINGS_CHAT) }
            )
            RefDivider()
            RefRow(
                title = "Language",
                subtitle = "English",
                icon = GsIcons.Globe,
                onClick = { onNavigate(GsRoutes.SETTINGS_CHAT) }
            )
        }

        RefSectionLabel("Appearance")
        RefCard {
            RefRow(
                title = "Theme",
                subtitle = "Light",
                icon = GsIcons.Palette,
                onClick = { onNavigate(GsRoutes.SETTINGS_THEME) }
            )
            RefDivider()
            RefRow(
                title = "Chat Background",
                subtitle = "Default",
                icon = GsIcons.ImagePlus,
                onClick = { onNavigate(GsRoutes.SETTINGS_APPEARANCE) }
            )
            RefDivider()
            RefRow(
                title = "Motion effects",
                subtitle = "On",
                icon = GsIcons.Sparkles,
                onClick = { onNavigate(GsRoutes.SETTINGS_APPEARANCE) }
            )
        }

        RefSectionLabel("Privacy & Security")
        RefCard {
            RefRow(
                title = "Data & Privacy",
                subtitle = "Your data, your control",
                icon = GsIcons.Lock,
                onClick = { onNavigate(GsRoutes.SETTINGS_PRIVACY) }
            )
            RefDivider()
            RefRow(
                title = "Blocked Users",
                subtitle = "Manage blocked users",
                icon = GsIcons.UsersRound,
                onClick = { onNavigate(GsRoutes.SETTINGS_PRIVACY) }
            )
            RefDivider()
            RefRow(
                title = "Chat History",
                subtitle = "Manage your conversations",
                icon = GsIcons.History,
                onClick = { onNavigate(GsRoutes.SETTINGS_CHAT) }
            )
        }

        RefSectionLabel("Notifications")
        RefCard {
            RefRow(
                title = "Responses and updates",
                subtitle = "New messages and replies",
                icon = GsIcons.Bell,
                onClick = { onNavigate(GsRoutes.SETTINGS_NOTIFICATIONS) }
            )
            RefDivider()
            RefRow(
                title = "Sound & Vibration",
                subtitle = "Feedback on interactions",
                icon = GsIcons.AudioLines,
                onClick = { onNavigate(GsRoutes.SETTINGS_NOTIFICATIONS) }
            )
        }

        RefSectionLabel("Memory & Voice")
        RefCard {
            RefRow(
                title = "Saved memories",
                subtitle = "What Nexly remembers about you",
                icon = GsIcons.Layers,
                onClick = { onNavigate(GsRoutes.SETTINGS_MEMORY) }
            )
            RefDivider()
            RefRow(
                title = "Voice & Speech",
                subtitle = "Choose your voice and hands-free mode",
                icon = GsIcons.AudioLines,
                onClick = { onNavigate(GsRoutes.SETTINGS_VOICE) }
            )
            RefDivider()
            RefRow(
                title = "Accessibility & Reduced Motion",
                subtitle = "High contrast, larger text, calmer motion",
                icon = GsIcons.Accessibility,
                onClick = { onNavigate(GsRoutes.SETTINGS_ACCESSIBILITY) }
            )
        }

        RefSectionLabel("Support")
        RefCard {
            RefRow(
                title = "Help & Support",
                subtitle = "FAQs, contact and feedback",
                icon = GsIcons.MessageCircle,
                onClick = { onNavigate(GsRoutes.SETTINGS_HELP) }
            )
            RefDivider()
            RefRow(
                title = "About Nexly",
                subtitle = "Smarter conversations. A brighter you.",
                icon = GsIcons.Info,
                onClick = { onNavigate(GsRoutes.SETTINGS_ABOUT) }
            )
        }

        RefFootnote("Every switch on these pages is read by the app. Nothing here is decoration.")
        Spacer(Modifier.height(10.dp))
    }
}

internal fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "GS"
        parts.size == 1 -> parts.first().take(2).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}
