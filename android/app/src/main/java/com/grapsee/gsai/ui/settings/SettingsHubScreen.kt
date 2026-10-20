package com.grapsee.gsai.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.AccountStore
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
        RefCard(
            modifier = Modifier
                .padding(top = 6.dp)
                .then(Modifier),
            padding = androidx.compose.foundation.layout.PaddingValues(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RefAvatar(
                    initials = initialsOf(name),
                    size = 56.dp,
                    onClick = { onNavigate(GsRoutes.SETTINGS_ACCOUNT) }
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = name.ifBlank { "GS account" },
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = email.ifBlank { "Signed in" },
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                RefStatusPill("Early access")
                Spacer(Modifier.width(6.dp))
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
                title = "Account",
                subtitle = "Name, email and sign-in",
                icon = GsIcons.UserRound,
                onClick = { onNavigate(GsRoutes.SETTINGS_ACCOUNT) }
            )
            RefDivider()
            RefRow(
                title = "Chat & Conversations",
                subtitle = "Response mode, history and export",
                icon = GsIcons.MessageCircle,
                onClick = { onNavigate(GsRoutes.SETTINGS_CHAT) }
            )
            RefDivider()
            RefRow(
                title = "Appearance",
                subtitle = "Theme, accent, type and background",
                icon = GsIcons.Palette,
                onClick = { onNavigate(GsRoutes.SETTINGS_APPEARANCE) }
            )
            RefDivider()
            RefRow(
                title = "Notifications",
                subtitle = "Replies and haptic feedback",
                icon = GsIcons.Bell,
                onClick = { onNavigate(GsRoutes.SETTINGS_NOTIFICATIONS) }
            )
            RefDivider()
            RefRow(
                title = "Privacy & Security",
                subtitle = "Your data, exports and local storage",
                icon = GsIcons.Lock,
                onClick = { onNavigate(GsRoutes.SETTINGS_PRIVACY) }
            )
        }

        RefSectionLabel("Intelligence")
        RefCard {
            RefRow(
                title = "AI Intelligence",
                subtitle = "Where answers run and how they think",
                icon = GsIcons.Brain,
                onClick = { onNavigate(GsRoutes.SETTINGS_AI) }
            )
            RefDivider()
            RefRow(
                title = "Personalisation & Memory",
                subtitle = "What GS keeps on this device",
                icon = GsIcons.Layers,
                onClick = { onNavigate(GsRoutes.SETTINGS_MEMORY) }
            )
            RefDivider()
            RefRow(
                title = "Voice & Speech",
                subtitle = "Read-aloud and voice mode",
                icon = GsIcons.AudioLines,
                onClick = { onNavigate(GsRoutes.SETTINGS_VOICE) }
            )
            RefDivider()
            RefRow(
                title = "Accessibility",
                subtitle = "Contrast, text size and motion",
                icon = GsIcons.Accessibility,
                onClick = { onNavigate(GsRoutes.SETTINGS_ACCESSIBILITY) }
            )
        }

        RefSectionLabel("Support")
        RefCard {
            RefRow(
                title = "Help & Support",
                subtitle = "Answers, contact and feedback",
                icon = GsIcons.MessageCircle,
                onClick = { onNavigate(GsRoutes.SETTINGS_HELP) }
            )
            RefDivider()
            RefRow(
                title = "About GS AI",
                subtitle = "Version, credits and legal",
                icon = GsIcons.Info,
                onClick = { onNavigate(GsRoutes.SETTINGS_ABOUT) }
            )
            RefDivider()
            RefRow(
                title = "Plan & Billing",
                subtitle = "Early access — no payment required",
                icon = GsIcons.CreditCard,
                onClick = { onNavigate(GsRoutes.BILLING) }
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
