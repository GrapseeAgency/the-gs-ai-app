package com.grapsee.gsai.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.AccountStore
import com.grapsee.gsai.data.OnboardingProfileStore
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.LOTUS_BLEED_ALPHA
import com.grapsee.gsai.ui.components.RefAvatar
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefInfoCard
import com.grapsee.gsai.ui.components.RefNavRow
import com.grapsee.gsai.ui.components.RefRowDivider
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme

/**
 * PROFILE & PREFERENCES — rebuilt to
 * docs/reference/settings/profile-preferences.png.
 *
 * Real values only: AccountStore supplies name/email/date of birth/location,
 * OnboardingProfileStore supplies tone/detail/interests/tools.
 *
 * Every row on this page now goes somewhere, which was not true before:
 *  - Name, Email, Date of Birth and Location open the identity editor (the
 *    last two used to print "Not set" forever, with no chevron — a field the
 *    app names and can never learn);
 *  - Tone & Detail and Language open Response Style, where the two choices are
 *    actually editable and the chat turn actually reads them;
 *  - Interests and Content Preferences open the setup wizard.
 */
@Composable
fun ProfileScreen(
    onNavigate: (String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val colors = GsTheme.colors
    val name = AccountStore.displayName(context)
    val email = AccountStore.email(context)
    val initials = initialsFor(name).ifBlank { "GS" }

    RefSettingsPage(
        title = "Profile & Preferences",
        subtitle = "Make Nexly feel more like you",
        onBack = { onBack?.invoke() },
    ) {
        // ---- Identity card with the lotus bleed --------------------------
        RefCard(role = GsGlassRole.CardLight) {
            Box(Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(com.grapsee.gsai.R.drawable.gs_home_lotus_new),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(LOTUS_BLEED_ALPHA),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // The pencil badge was decoration: nothing opened an
                        // editor. The card is the way into the identity page.
                        .clickable { onNavigate(GsRoutes.SETTINGS_IDENTITY) }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        RefAvatar(initials = initials, size = 52.dp)
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 3.dp, y = 3.dp)
                                .size(18.dp)
                                .background(colors.raisedSurface, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                GsIcons.PenLine,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(10.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            name.ifBlank { "Your account" },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = GsFonts.Product,
                            color = colors.textPrimary,
                            maxLines = 1,
                        )
                        Text(
                            email.ifBlank { "No Grapsee address on this device" },
                            fontSize = 11.5.sp,
                            fontFamily = GsFonts.Product,
                            color = colors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier
                                .background(color = androidx.compose.ui.graphics.Color(0xFFF5CFB5).copy(alpha = 0.55f), shape = RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 2.5.dp),
                        ) {
                            Text(
                                com.grapsee.gsai.data.PlanStore.planName,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = GsFonts.Product,
                                color = androidx.compose.ui.graphics.Color(0xFF8A4A1F),
                            )
                        }
                    }
                }
            }
        }

        // ---- Profile ------------------------------------------------------
        RefGroupHeader("Profile", "Your basic information")
        RefCard(role = GsGlassRole.CardLight) {
            RefNavRow(
                title = "Name",
                subtitle = name.ifBlank { "Not set" },
                onClick = { onNavigate(GsRoutes.SETTINGS_IDENTITY) },
                leading = { RefWellTile(GsIcons.UserRound, GsWellTone.Peach, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "Email",
                subtitle = email.ifBlank { "Not set" },
                onClick = { onNavigate(GsRoutes.SETTINGS_IDENTITY) },
                leading = { RefWellTile(GsIcons.Mail, GsWellTone.Lilac, 28.dp) },
            )
            RefRowDivider()
            // These two rows printed "Not set" forever with no chevron: a
            // field the app names and can never learn. They now open the same
            // editor as the name and address, which stores both.
            RefNavRow(
                title = "Date of Birth",
                subtitle = AccountStore.dateOfBirth(context).ifBlank { "Not set" },
                onClick = { onNavigate(GsRoutes.SETTINGS_IDENTITY) },
                leading = { RefWellTile(GsIcons.Calendar, GsWellTone.Mint, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "Location",
                subtitle = AccountStore.location(context).ifBlank { "Not set" },
                onClick = { onNavigate(GsRoutes.SETTINGS_IDENTITY) },
                leading = { RefWellTile(GsIcons.MapPin, GsWellTone.Blue, 28.dp) },
            )
        }

        // ---- Response Style ----------------------------------------------
        // Tone and Detail used to be two rows that showed a value and offered
        // no way to change it; Language was a third. One page now owns all
        // three, and the two choices are read by the chat turn.
        RefGroupHeader("Response Style", "How Nexly communicates with you")
        RefCard(role = GsGlassRole.CardLight) {
            RefNavRow(
                title = "Tone & Detail",
                subtitle = "${OnboardingProfileStore.personality} · ${OnboardingProfileStore.responseStyle}",
                onClick = { onNavigate(GsRoutes.SETTINGS_RESPONSE_STYLE) },
                leading = { RefWellTile(GsIcons.MessageCircle, GsWellTone.Peach, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "Language",
                subtitle = "English (UK) — the only language this build ships",
                onClick = { onNavigate(GsRoutes.SETTINGS_RESPONSE_STYLE) },
                leading = { RefWellTile(GsIcons.Languages, GsWellTone.Mint, 28.dp) },
            )
        }

        // ---- Personalization Preferences ---------------------------------
        RefGroupHeader("Personalization Preferences", "Tailor your experience")
        RefCard(role = GsGlassRole.CardLight) {
            RefNavRow(
                title = "Interests",
                subtitle = joined(OnboardingProfileStore.interests),
                onClick = { onNavigate(GsRoutes.ONBOARDING_SETUP) },
                leading = { RefWellTile(GsIcons.Sparkles, GsWellTone.Peach, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "Content Preferences",
                subtitle = joined(OnboardingProfileStore.capabilities),
                onClick = { onNavigate(GsRoutes.ONBOARDING_SETUP) },
                leading = { RefWellTile(GsIcons.BookOpen, GsWellTone.Lilac, 28.dp) },
            )

        }

        Spacer(Modifier.height(14.dp))
        RefInfoCard(
            text = "Your preferences help Nexly give you better answers, more relevant suggestions, and a personalized experience.",
            icon = GsIcons.Sparkles,
        )
        Spacer(Modifier.height(10.dp))
    }
}

private fun joined(values: Set<String>): String =
    if (values.isEmpty()) "Not set" else values.sorted().joinToString(", ")

private fun initialsFor(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "A"
        parts.size == 1 -> parts.first().take(2).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}
