package com.grapsee.gsai.ui.profile

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.AccountStore
import com.grapsee.gsai.data.OnboardingProfileStore
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.components.RefAvatar
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefPage
import com.grapsee.gsai.ui.components.RefRow
import com.grapsee.gsai.ui.components.RefSection
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsAccents
import com.grapsee.gsai.ui.theme.GsTheme

/**
 * Profile — identity first, then the two things a person actually changes:
 * how GS sounds and how the app looks. Every row is a real destination.
 *
 * The old screen buried the account in a generic Settings row and invented
 * "Billing" copy. This one shows the real stored name/email, the setup answers
 * the wizard kept, and routes into the dedicated pages that edit them.
 */
@Composable
fun ProfileScreen(
    onNavigate: (String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val name = AccountStore.displayName(context)
    val email = AccountStore.email(context)
    val family = GsAccents.byId(SettingsStore.accentId)
    val summary = OnboardingProfileStore.summary()

    RefPage(
        title = "Profile",
        subtitle = "Your account and how GS fits around you.",
        onBack = onBack
    ) {
        RefCard(padding = androidx.compose.foundation.layout.PaddingValues(22.dp)) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RefAvatar(initials = initialsFor(name), size = 78.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = name.ifBlank { "GS account" },
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = GsTheme.colors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = email.ifBlank { "Signed in on this device" },
                    style = MaterialTheme.typography.labelMedium,
                    color = GsTheme.colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Early access",
                    style = MaterialTheme.typography.labelSmall,
                    color = GsTheme.colors.accentStrong,
                    modifier = Modifier
                        .padding(top = 2.dp)
                )
            }
        }

        RefSection(label = "About you") {
            RefRow(
                title = "Personalisation",
                subtitle = summary ?: "Pick interests, a voice and the tools you use",
                icon = GsIcons.Heart,
                onClick = { onNavigate(GsRoutes.ONBOARDING_SETUP) },
                onClickLabel = "Open personalisation setup"
            )
            com.grapsee.gsai.ui.components.RefDivider()
            RefRow(
                title = "Accent",
                subtitle = "${family.label} · used across buttons and highlights",
                icon = GsIcons.Palette,
                onClick = { onNavigate(GsRoutes.SETTINGS_APPEARANCE) },
                onClickLabel = "Open appearance settings"
            )
            com.grapsee.gsai.ui.components.RefDivider()
            RefRow(
                title = "Account details",
                subtitle = "Name, email and sign-in",
                icon = GsIcons.UserRound,
                onClick = { onNavigate(GsRoutes.SETTINGS_ACCOUNT) }
            )
        }

        RefSection(label = "GS") {
            RefRow(
                title = "Settings",
                subtitle = "Chat, privacy, accessibility and more",
                icon = GsIcons.Settings,
                onClick = { onNavigate(GsRoutes.SETTINGS) }
            )
            com.grapsee.gsai.ui.components.RefDivider()
            RefRow(
                title = "Billing",
                subtitle = "GS AI is free during early access",
                icon = GsIcons.CreditCard,
                onClick = { onNavigate(GsRoutes.BILLING) }
            )
        }

        Text(
            text = "Everything here belongs to this device until you sign in on another one.",
            style = MaterialTheme.typography.labelSmall,
            color = GsTheme.colors.textTertiary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 18.dp)
        )
    }
}

/**
 * Initials from the REAL stored name: the first letter of each of the first
 * two words, uppercased. No stored name → the neutral "GS" mark.
 */
private fun initialsFor(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return "GS"
    return words.take(2).map { it.first().uppercaseChar() }.joinToString("")
}
