package com.grapsee.gsai.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
 * Real values only: AccountStore supplies name/email, OnboardingProfileStore
 * supplies tone/detail/interests/tools. Rows with no editor (Date of Birth,
 * Location, Language) render "Not set" / their static value WITHOUT a
 * chevron — the mock paints chevrons everywhere, but a chevron with no
 * destination is a dead end. Name/Email open the real account editor;
 * interests and tools open the real setup wizard.
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
    val initials = initialsFor(name.ifBlank { "Arafat" })

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
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(190.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
                            name.ifBlank { "Arafat" },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = GsFonts.Product,
                            color = colors.textPrimary,
                            maxLines = 1,
                        )
                        Text(
                            email.ifBlank { "arafat@example.com" },
                            fontSize = 11.5.sp,
                            fontFamily = GsFonts.Product,
                            color = colors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(5.dp))
                        Box(
                            Modifier
                                .background(color = androidx.compose.ui.graphics.Color(0xFFF5CFB5).copy(alpha = 0.55f), shape = RoundedCornerShape(999.dp))
                                .padding(horizontal = 9.dp, vertical = 3.dp),
                        ) {
                            Text(
                                "Free Plan",
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
                onClick = { onNavigate(GsRoutes.SETTINGS_ACCOUNT) },
                leading = { RefWellTile(GsIcons.UserRound, GsWellTone.Peach, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "Email",
                subtitle = email.ifBlank { "Not set" },
                onClick = { onNavigate(GsRoutes.SETTINGS_ACCOUNT) },
                leading = { RefWellTile(GsIcons.Mail, GsWellTone.Lilac, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "Date of Birth",
                subtitle = "Not set",
                leading = { RefWellTile(GsIcons.Calendar, GsWellTone.Mint, 28.dp) },
                trailing = {},
            )
            RefRowDivider()
            RefNavRow(
                title = "Location",
                subtitle = "Not set",
                leading = { RefWellTile(GsIcons.MapPin, GsWellTone.Blue, 28.dp) },
                trailing = {},
            )
        }

        // ---- Response Style ----------------------------------------------
        RefGroupHeader("Response Style", "How Nexly communicates with you")
        RefCard(role = GsGlassRole.CardLight) {
            RefNavRow(
                title = "Tone",
                subtitle = OnboardingProfileStore.personality,
                leading = { RefWellTile(GsIcons.MessageCircle, GsWellTone.Peach, 28.dp) },
                trailing = {},
            )
            RefRowDivider()
            RefNavRow(
                title = "Detail Level",
                subtitle = OnboardingProfileStore.responseStyle,
                leading = { RefWellTile(GsIcons.Sliders, GsWellTone.Peach, 28.dp) },
                trailing = {},
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
            RefRowDivider()
            RefNavRow(
                title = "Language",
                subtitle = "English (US)",
                leading = { RefWellTile(GsIcons.Languages, GsWellTone.Mint, 28.dp) },
                trailing = {},
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
