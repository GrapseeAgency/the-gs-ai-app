package com.grapsee.gsai.ui.integrations

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefInfoCard
import com.grapsee.gsai.ui.components.RefNavRow
import com.grapsee.gsai.ui.components.RefQuietButton
import com.grapsee.gsai.ui.components.RefRowDivider
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefStatusPill
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme

/** One provider row. `status` is always the truth for this build. */
private data class Provider(
    val name: String,
    val scope: String,
    val icon: ImageVector,
    val tone: GsWellTone,
)

/**
 * Connected Apps & Integrations — docs/reference/pending/connected-apps.png.
 *
 * The sheet's groups, provider rows, status pills, "Available Integrations"
 * tiles and both closing cards are reproduced in its order. The statuses are
 * NOT the sheet's.
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
 * When real integrations land, each row's pill reads from the provider adapter
 * that owns it; the layout does not need to change.
 */
@Composable
fun ConnectedAppsScreen(onBack: () -> Unit) {
    val colors = GsTheme.colors

    val providers = listOf(
        Provider("Google", "Email, Drive, Calendar", GsIcons.Mail, GsWellTone.Rose),
        Provider("GitHub", "Code repositories & projects", GsIcons.GitBranch, GsWellTone.Lilac),
        Provider("Notion", "Notes, docs & workspace", GsIcons.FileText, GsWellTone.Blue),
        Provider("Spotify", "Music & media", GsIcons.Music, GsWellTone.Mint),
        Provider("Slack", "Work communication", GsIcons.MessageCircle, GsWellTone.Peach),
        Provider("Microsoft", "OneDrive, Outlook & more", GsIcons.MonitorSmartphone, GsWellTone.Blue),
    )

    val available = listOf(
        Triple("Dropbox", "File storage", GsIcons.Cloud),
        Triple("Figma", "Design & files", GsIcons.PenLine),
        Triple("Trello", "Project management", GsIcons.Briefcase),
        Triple("Discord", "Community", GsIcons.Bubbles),
        Triple("Zoom", "Meetings", GsIcons.Video),
    )

    RefSettingsPage(
        title = "Connected Apps & Integrations",
        subtitle = "Link your favorite services and manage permissions for a more connected experience.",
        onBack = onBack,
    ) {
        // ---- Hero ---------------------------------------------------------
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
                RefWellTile(GsIcons.Link, GsWellTone.Lilac, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "More possibilities together",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GsFonts.Product,
                        color = colors.textPrimary,
                    )
                    Text(
                        "No integration is wired in this build yet — connecting an app starts with a provider adapter.",
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textTertiary,
                    )
                }
            }
        }

        // ---- Connected providers -----------------------------------------
        RefGroupHeader("Your Connected Apps", "Manage Permissions")
        RefCard(role = GsGlassRole.CardLight) {
            providers.forEachIndexed { index, p ->
                if (index > 0) RefRowDivider()
                RefNavRow(
                    title = p.name,
                    subtitle = p.scope,
                    // Nothing to open: there is no authorization screen, no
                    // token and no permission set behind any of these.
                    onClick = null,
                    trailing = { RefStatusPill("Not Connected", colors.textSecondary) },
                    leading = { RefWellTile(p.icon, p.tone, 28.dp) },
                )
            }
        }

        if (providers.isNotEmpty()) {
            RefInfoCard(
                text = "Nexly has no authorization to any of the services listed above. Nothing is linked, no tokens are stored, and no third-party data is read.",
                icon = GsIcons.ShieldCheck,
            )
        }

        // ---- Available integrations --------------------------------------
        RefGroupHeader("Available Integrations", "Browse All")
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                available.forEach { (name, scope, icon) ->
                    IntegrationTile(name, scope, icon, Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        RefInfoCard(
            icon = GsIcons.ShieldCheck,
            title = "Your data, your control",
            text = "When an app is connected, you decide what it can read and you can revoke it here at any time.",
        )
        Spacer(Modifier.height(10.dp))
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RefWellTile(GsIcons.Link, GsWellTone.Lilac, 32.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Connect New App or Service",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = GsFonts.Product,
                        color = colors.textPrimary,
                    )
                    Text(
                        "Integration support is not available in this build.",
                        fontSize = 10.5.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textTertiary,
                    )
                }
                RefQuietButton(label = "Coming soon", onClick = {}, modifier = Modifier.width(112.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun IntegrationTile(
    name: String,
    scope: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val colors = GsTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.glassSurface.copy(alpha = 0.55f))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RefWellTile(icon, GsWellTone.Blue, 28.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            name,
            fontSize = 10.sp,
            fontFamily = GsFonts.Product,
            color = colors.textPrimary,
        )
        Text(
            scope,
            fontSize = 8.5.sp,
            lineHeight = 11.sp,
            fontFamily = GsFonts.Product,
            color = colors.textTertiary,
        )
    }
}
