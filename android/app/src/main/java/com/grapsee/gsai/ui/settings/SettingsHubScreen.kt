package com.grapsee.gsai.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.AccountStore
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefAvatar
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefInlinePill
import com.grapsee.gsai.ui.components.RefNavRow
import com.grapsee.gsai.ui.components.RefRowDivider
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme

/**
 * SETTINGS HUB — rebuilt to the committed reference
 * (docs/reference/settings/settings-hub.png, geometry in
 * shared-contracts' sibling docs/reference/settings/settings-geometry.json).
 *
 * The mock's shape: identity card with the blossom bleed, the Upgrade card,
 * three captioned groups of destination rows (one glass card each, hairline
 * separators), and the standalone Log Out card. Every row keeps a real
 * destination — including Language, which has no dedicated page yet and
 * therefore opens Appearance (the closest existing surface) rather than a
 * dead end; a dedicated Language page is a follow-up, recorded in
 * docs/SETTINGS_REFERENCE_STATUS.md.
 */
@Composable
fun SettingsHubScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onLocalAiRequested: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val context = LocalContext.current
    val colors = GsTheme.colors
    val name = AccountStore.displayName(context)
    val email = AccountStore.email(context)
    val initials = initialsOf(name.ifBlank { "Arafat" })

    RefSettingsPage(
        title = "Settings",
        subtitle = "Customize your Nexly experience",
        onBack = onBack,
    ) {
        // ---- Identity card ------------------------------------------------
        RefCard(role = GsGlassRole.CardLight) {
            Box(Modifier.fillMaxWidth()) {
                // The mock's blossom bleed: the shipped lotus photograph,
                // faded into the card's right edge (no new raster minted).
                Image(
                    painter = painterResource(com.grapsee.gsai.R.drawable.gs_home_lotus_new),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(190.dp),
                )
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(190.dp)
                        .background(
                            Brush.horizontalGradient(
                                0f to colors.glassSurface,
                                0.45f to colors.glassSurface.copy(alpha = 0.55f),
                                1f to Color.Transparent,
                            )
                        ),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Profile and preferences",
                        ) { onNavigate(GsRoutes.PROFILE) }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        RefAvatar(initials = initials, size = 52.dp)
                        // The mock's edit badge, bottom-right of the avatar.
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
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFFF5CFB5).copy(alpha = 0.55f))
                                .padding(horizontal = 9.dp, vertical = 3.dp),
                        ) {
                            Text(
                                "Free Plan",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = GsFonts.Product,
                                color = Color(0xFF8A4A1F),
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        GsIcons.ChevronRight,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // ---- Upgrade card -------------------------------------------------
        Spacer(Modifier.height(12.dp))
        RefCard(role = GsGlassRole.CardLight) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RefWellTile(icon = GsIcons.Sparkles, tone = GsWellTone.Peach, size = 34.dp)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Upgrade to Nexly Pro",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GsFonts.Product,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Get more power, faster responses, and exclusive features.",
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textTertiary,
                        maxLines = 2,
                    )
                }
                Spacer(Modifier.width(8.dp))
                RefInlinePill(
                    label = "Upgrade",
                    icon = GsIcons.ArrowRight,
                ) { onNavigate(GsRoutes.BILLING) }
            }
        }

        // ---- Personalization ---------------------------------------------
        RefGroupHeader("Personalization", "Make Nexly feel like you")
        RefCard(role = GsGlassRole.CardLight) {
            RefNavRow(
                title = "Profile & Preferences",
                subtitle = "Your name, interests and style",
                onClick = { onNavigate(GsRoutes.PROFILE) },
                leading = { RefWellTile(GsIcons.UserRound, GsWellTone.Peach, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "Memory",
                subtitle = "Let Nexly remember what matters",
                onClick = { onNavigate(GsRoutes.SETTINGS_MEMORY) },
                leading = { RefWellTile(GsIcons.Brain, GsWellTone.Peach, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "Appearance",
                subtitle = "Theme, accent color, and display",
                onClick = { onNavigate(GsRoutes.SETTINGS_APPEARANCE) },
                leading = { RefWellTile(GsIcons.Palette, GsWellTone.Peach, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "Language",
                subtitle = "Choose your preferred language",
                onClick = { onNavigate(GsRoutes.SETTINGS_APPEARANCE) },
                leading = { RefWellTile(GsIcons.Globe, GsWellTone.Peach, 28.dp) },
            )
        }

        // ---- Privacy & Data ----------------------------------------------
        RefGroupHeader("Privacy & Data", "Your data, your control")
        RefCard(role = GsGlassRole.CardLight) {
            RefNavRow(
                title = "Privacy Settings",
                subtitle = "Manage your data and activity",
                onClick = { onNavigate(GsRoutes.SETTINGS_PRIVACY) },
                leading = { RefWellTile(GsIcons.Lock, GsWellTone.Peach, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "Data Controls",
                subtitle = "Control what you share and store",
                onClick = { onNavigate(GsRoutes.SETTINGS_PRIVACY) },
                leading = { RefWellTile(GsIcons.Shield, GsWellTone.Peach, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "Export / Delete Data",
                subtitle = "Download or remove your data",
                onClick = { onNavigate(GsRoutes.SETTINGS_PRIVACY) },
                leading = { RefWellTile(GsIcons.Database, GsWellTone.Peach, 28.dp) },
            )
        }

        // ---- App Settings -------------------------------------------------
        RefGroupHeader("App Settings", "Fine-tune your experience")
        RefCard(role = GsGlassRole.CardLight) {
            RefNavRow(
                title = "Notifications",
                subtitle = "Stay updated with important alerts",
                onClick = { onNavigate(GsRoutes.SETTINGS_NOTIFICATIONS) },
                leading = { RefWellTile(GsIcons.Bell, GsWellTone.Peach, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "Device & Account",
                subtitle = "Manage your devices and sessions",
                onClick = { onNavigate(GsRoutes.SETTINGS_ACCOUNT) },
                leading = { RefWellTile(GsIcons.Smartphone, GsWellTone.Peach, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "About Nexly",
                subtitle = "Version, terms and support",
                onClick = { onNavigate(GsRoutes.SETTINGS_ABOUT) },
                leading = { RefWellTile(GsIcons.Info, GsWellTone.Peach, 28.dp) },
            )
        }

        // ---- Log Out ------------------------------------------------------
        Spacer(Modifier.height(12.dp))
        RefCard(role = GsGlassRole.CardLight) {
            RefNavRow(
                title = "Log Out",
                subtitle = "Sign out from your account",
                onClick = onLogout,
                leading = { RefWellTile(GsIcons.LogOut, GsWellTone.Peach, 28.dp) },
            )
        }
        Spacer(Modifier.height(14.dp))
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
