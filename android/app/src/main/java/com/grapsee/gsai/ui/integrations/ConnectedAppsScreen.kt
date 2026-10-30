package com.grapsee.gsai.ui.integrations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.ui.chat.content.NexlyGlyph
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefBrandWell
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefDotsButton
import com.grapsee.gsai.ui.components.RefGlyphWell
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefHeroCard
import com.grapsee.gsai.ui.components.RefInfoCard
import com.grapsee.gsai.ui.components.RefSheetPillButton
import com.grapsee.gsai.ui.components.RefQuietWashCard
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefSheetRow
import com.grapsee.gsai.ui.components.RefSheetTile
import com.grapsee.gsai.ui.components.RefTagPill
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.components.TileCorner
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme

/**
 * One provider row. `status` is always the truth for this build.
 *
 * `tint` is only the brand's own ink where a single colour identifies it
 * (Spotify green, Discord blurple, Trello blue, Zoom blue). Marks whose
 * identity is a multi-colour lockup (Google, Slack, Microsoft, Figma) stay in
 * the app's ink rather than being recoloured into something that is neither.
 */
private data class Provider(
    val name: String,
    val scope: String,
    val glyph: String,
    val tint: Color? = null,
)

/**
 * Connected Apps & Integrations — docs/reference/pending/connected-apps.png.
 *
 * The sheet's groups, provider rows, status pills, "Available Integrations"
 * tiles and both closing cards are reproduced in its order and at its own
 * scale. The statuses are NOT the sheet's.
 *
 * This build ships no OAuth client, no token store, no permissions registry and
 * no provider adapters — verify it: there is no integration code anywhere in
 * this repository. Google, GitHub, Notion, Spotify, Slack, Microsoft, Dropbox,
 * Figma, Trello, Discord and Zoom are all "Not Connected" because that is what
 * they are, and nothing here can make them otherwise.
 *
 * The sheet's green "Connected" and blue "Limited Access" pills would have been
 * the most fabricated thing in the app — a claim of a live link to eleven
 * third-party accounts, none of which has ever been linked. The owner's rule is
 * "no mock data no fake data", and that rule costs this screen its pills.
 *
 * Every control still resolves: "..." opens that provider's real status sheet,
 * "+ Connect" opens the same sheet, "Learn More" explains what a connection
 * would require, "Manage Permissions" states what is granted (nothing), and
 * "Your data, your control" is a real route to Privacy Settings.
 */
@Composable
fun ConnectedAppsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
) {
    val colors = GsTheme.colors

    val providers = listOf(
        Provider("Google", "Email, Drive, Calendar", "brand-google"),
        Provider("GitHub", "Code repositories & projects", "brand-github"),
        Provider("Notion", "Notes, docs & workspace", "brand-notion"),
        Provider("Spotify", "Music & media", "brand-spotify", Color(0xFF1DB954)),
        Provider("Slack", "Work communication", "brand-slack"),
        Provider("Microsoft", "OneDrive, Outlook & more", "brand-windows", Color(0xFF00A4EF)),
    )

    val available = listOf(
        Provider("Dropbox", "File storage", "brand-dropbox", Color(0xFF0061FF)),
        Provider("Figma", "Design & files", "brand-figma"),
        Provider("Trello", "Project management", "brand-trello", Color(0xFF0079BF)),
        Provider("Discord", "Community", "brand-discord", Color(0xFF5865F2)),
        Provider("Zoom", "Meetings", "brand-zoom", Color(0xFF2D8CFF)),
    )

    var sheetProvider by remember { mutableStateOf<Provider?>(null) }
    var showLearnMore by remember { mutableStateOf(false) }
    var showPermissions by remember { mutableStateOf(false) }
    var showBrowseAll by remember { mutableStateOf(false) }

    RefSettingsPage(
        title = "Connected Apps & Integrations",
        subtitle = "Link your favorite services and manage permissions for a more connected experience.",
        onBack = onBack,
    ) {
        // ---- Hero ---------------------------------------------------------
        RefHeroCard {
            RefGlyphWell(glyph = "link", tone = GsWellTone.Lilac, size = 44.dp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "More possibilities together",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GsFonts.Product,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    "No integration is wired in this build yet — connecting an app starts with a provider adapter.",
                    fontSize = 8.5.sp,
                    lineHeight = 11.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textTertiary,
                )
            }
            Spacer(Modifier.width(8.dp))
            RefSheetPillButton(
                label = "Learn More",
                onClick = { showLearnMore = true },
            )
        }

        // ---- Connected providers -----------------------------------------
        RefGroupHeader(
            title = "Your Connected Apps",
            caption = "Manage Permissions",
            onCaptionClick = { showPermissions = true },
        )
        // The mock draws each provider as its own 20-corner card with a 5pt
        // gap; one group card with hairlines read as a table, not six objects.
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            // The mock puts each provider's status on its own line under the
            // scope; only Microsoft, whose row also carries "+ Connect", puts
            // it in the trailing slot.
            val statusPill: @Composable () -> Unit = {
                RefTagPill("Not Connected", colors.textSecondary)
            }
            providers.forEach { provider ->
                RefCard(
                    role = GsGlassRole.CardLight,
                    shape = RoundedCornerShape(20.dp),
                    padding = PaddingValues(0.dp),
                ) {
                    RefSheetRow(
                        title = provider.name,
                        subtitle = provider.scope,
                        extra = if (provider.name == "Microsoft") null else statusPill,
                        // Nothing to open: there is no authorization screen,
                        // no token and no permission set behind any of these.
                        onClick = null,
                        leading = {
                            RefBrandWell(glyph = provider.glyph, size = 34.dp, tint = provider.tint)
                        },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (provider.name == "Microsoft") {
                                    statusPill()
                                    Spacer(Modifier.width(6.dp))
                                    RefConnectPill { sheetProvider = provider }
                                } else {
                                    RefDotsButton(
                                        onClick = { sheetProvider = provider },
                                        label = "${provider.name} status",
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }

        // ---- Available integrations --------------------------------------
        RefGroupHeader(
            title = "Available Integrations",
            caption = "Browse All",
            onCaptionClick = { showBrowseAll = true },
        )
        // IntrinsicSize.Min + fillMaxHeight: the mock's five tiles share one
        // height even when a caption wraps ("Design & files"), and the "+"
        // sits on that height's floor rather than under the shortest caption.
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            available.forEach { provider ->
                RefSheetTile(
                    title = provider.name,
                    caption = provider.scope,
                    brandGlyph = provider.glyph,
                    brandTint = provider.tint,
                    corner = TileCorner.Plus,
                    centered = true,
                    minHeight = 108.dp,
                    pinCorner = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = { sheetProvider = provider },
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.glassSurface.copy(alpha = 0.55f))
                .border(1.dp, colors.glassBorder, RoundedCornerShape(20.dp))
                .clickableNoIndication { onNavigate(GsRoutes.SETTINGS_PRIVACY) }
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RefWellTile(GsIcons.ShieldCheck, GsWellTone.Lilac, 34.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Your data, your control",
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = GsFonts.Product,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    "When an app is connected, you decide what it can read and you can revoke it here at any time.",
                    fontSize = 8.sp,
                    lineHeight = 10.5.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                GsIcons.ChevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(14.dp),
            )
        }

        Spacer(Modifier.height(10.dp))
        // ---- Closing card ------------------------------------------------
        RefQuietWashCard {
            RefGlyphWell(glyph = "link", tone = GsWellTone.Lilac, size = 34.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Connect New App or Service",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = GsFonts.Product,
                    color = colors.textPrimary,
                )
                Text(
                    "Integration support is not available in this build.",
                    fontSize = 8.5.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textTertiary,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                GsIcons.ChevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier
                    .size(14.dp)
                    .clickable { showLearnMore = true },
            )
        }
        Spacer(Modifier.height(10.dp))
    }

    // ---- Real sheets -------------------------------------------------------

    sheetProvider?.let { provider ->
        AlertDialog(
            onDismissRequest = { sheetProvider = null },
            title = {
                Text(
                    provider.name,
                    fontFamily = GsFonts.Product,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            },
            text = {
                Text(
                    "Not connected. Nexly has no authorization to ${provider.name}: no token is stored and no third-party data is read. A connection can exist only once a ${provider.name} adapter ships in the app.",
                    fontFamily = GsFonts.Product,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { sheetProvider = null }) { Text("Close") }
            },
        )
    }

    if (showLearnMore) {
        AlertDialog(
            onDismissRequest = { showLearnMore = false },
            title = {
                Text(
                    "How integrations work",
                    fontFamily = GsFonts.Product,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            },
            text = {
                Text(
                    "An integration needs four things this build does not ship: an OAuth client for the provider, a token store, a permissions registry and an adapter that reads the provider's data. Until all four exist, every app here stays Not Connected — and nothing on this screen claims otherwise.",
                    fontFamily = GsFonts.Product,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { showLearnMore = false }) { Text("Got it") }
            },
        )
    }

    if (showPermissions) {
        AlertDialog(
            onDismissRequest = { showPermissions = false },
            title = {
                Text(
                    "Manage Permissions",
                    fontFamily = GsFonts.Product,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            },
            text = {
                Text(
                    "No permissions are granted. There is no connected app, so there is nothing to allow, limit or revoke. Your data, your control keeps the on-device privacy switches in one place.",
                    fontFamily = GsFonts.Product,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissions = false
                    onNavigate(GsRoutes.SETTINGS_PRIVACY)
                }) { Text("Open Privacy") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissions = false }) { Text("Close") }
            },
        )
    }

    if (showBrowseAll) {
        AlertDialog(
            onDismissRequest = { showBrowseAll = false },
            title = {
                Column {
                    Text(
                        "Available Integrations",
                        fontFamily = GsFonts.Product,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Text(
                        "None can be connected in this build",
                        fontFamily = GsFonts.Product,
                        fontSize = 9.5.sp,
                        color = colors.textTertiary,
                    )
                }
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    available.forEach { provider ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    showBrowseAll = false
                                    sheetProvider = provider
                                }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RefBrandWell(glyph = provider.glyph, size = 30.dp, tint = provider.tint)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    provider.name,
                                    fontSize = 10.5.sp,
                                    fontFamily = GsFonts.Product,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    provider.scope,
                                    fontSize = 8.5.sp,
                                    fontFamily = GsFonts.Product,
                                    color = colors.textTertiary,
                                )
                            }
                            RefTagPill("Not Connected", colors.textSecondary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBrowseAll = false }) { Text("Close") }
            },
        )
    }
}

/** The mock's "+ Connect" control on the one row that draws it. */
@Composable
private fun RefConnectPill(onClick: () -> Unit) {
    val colors = GsTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NexlyGlyph(name = "plus", size = 9.dp, color = colors.textPrimary)
        Spacer(Modifier.width(3.dp))
        Text(
            "Connect",
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = GsFonts.Product,
            color = colors.textPrimary,
        )
    }
}

private fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier =
    this.then(
        Modifier.clickable(
            interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
            indication = null,
            onClick = onClick,
        )
    )
