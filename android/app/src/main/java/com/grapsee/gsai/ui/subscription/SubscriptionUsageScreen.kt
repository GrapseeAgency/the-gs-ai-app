package com.grapsee.gsai.ui.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.PlanStore
import com.grapsee.gsai.data.UsageMeter
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefArt
import com.grapsee.gsai.ui.components.RefBleedArt
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefFactRow
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefHeroCard
import com.grapsee.gsai.ui.components.RefInfoCard
import com.grapsee.gsai.ui.components.RefProgressBar
import com.grapsee.gsai.ui.components.RefSheetPillButton
import com.grapsee.gsai.ui.components.RefQuietWashCard
import com.grapsee.gsai.ui.components.RefRowDivider
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefSheetRow
import com.grapsee.gsai.ui.components.RefTagPill
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Subscription & Usage — docs/reference/pending/subscription-usage.png.
 *
 * The sheet's groups, wells, gradient hero, artwork bleed, progress bars and
 * closing card are reproduced in its order and at its own scale. The numbers
 * are not: every counter comes from [UsageMeter], which counts real rows in
 * this device's database, and the plan facts come from [PlanStore] — including
 * the $12.99/month the owner confirmed. The mock's $19.99 is placeholder art
 * and is not drawn.
 *
 * WHAT IS TRUTHFUL-ABSENT
 * There is no payment processor in this build: no card, no invoice, no
 * purchase path. The "Visa •••• 4242" the sheet draws exists nowhere in this
 * codebase, so Payment Method and Billing History keep their slots and state
 * the reality: no method is stored, no invoice can exist, and no chevron
 * promises a page that would be empty.
 *
 * The "Go Pro" card keeps its gradient and its button, but there is only one
 * plan: the copy says so rather than advertising tiers that do not exist.
 */
@Composable
fun SubscriptionUsageScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
) {
    var meters by remember { mutableStateOf(UsageMeter.Zero) }

    // The counts are a real query over the message table, run off the main
    // thread. A meter that cannot be counted stays at -1 and renders as "—",
    // never as a plausible-looking zero.
    LaunchedEffect(Unit) {
        meters = withContext(Dispatchers.IO) {
            runCatching { UsageMeter.read(ServiceLocator.db.messageDao()) }
                .getOrDefault(UsageMeter.Unknown)
        }
    }

    RefSettingsPage(
        title = "Subscription & Usage",
        subtitle = "Your plan, usage, and billing details",
        onBack = onBack,
    ) {
        // ---- Hero ---------------------------------------------------------
        RefHeroCard {
            RefWellTile(GsIcons.Crown, GsWellTone.Peach, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        PlanStore.planName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GsFonts.Product,
                        color = GsTheme.colors.textPrimary,
                    )
                    Spacer(Modifier.width(7.dp))
                    if (PlanStore.active) {
                        RefTagPill("Active", GsWellTone.Mint.ink)
                    } else {
                        RefTagPill("Not activated", GsTheme.colors.textSecondary)
                    }
                }
                Spacer(Modifier.height(1.dp))
                Text(
                    "Unlock more limits, advanced models, and premium features.",
                    fontSize = 8.5.sp,
                    lineHeight = 11.sp,
                    fontFamily = GsFonts.Product,
                    color = GsTheme.colors.textTertiary,
                )
            }
            Spacer(Modifier.width(8.dp))
            RefSheetPillButton(
                label = "Manage Plan",
                onClick = { onNavigate(GsRoutes.BILLING) },
            )
        }

        // ---- Plan Details -------------------------------------------------
        RefGroupHeader("Plan Details", "Current plan information")
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    RefFactRow("Plan Name", PlanStore.planName)
                    RefFactRow("Billing Cycle", PlanStore.cycleLabel)
                    RefFactRow("Next Billing Date", formatDate(PlanStore.nextBillingMillis()))
                    RefFactRow("Price", PlanStore.priceLabel)
                }
                // The mock bleeds its crystal into this card's right edge;
                // the app's own pearl-lotus photograph is the artwork set
                // this build actually ships.
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(
                        com.grapsee.gsai.R.drawable.gs_home_lotus_new
                    ),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .alpha(0.85f)
                        .border(2.dp, Color.White.copy(alpha = 0.55f), CircleShape),
                )
            }
        }

        // ---- Usage Overview ----------------------------------------------
        RefGroupHeader("Usage Overview", "Your current month's usage")
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(vertical = 2.dp)) {
            UsageRow(
                icon = GsIcons.MessageCircle,
                tone = GsWellTone.Lilac,
                title = "Messages",
                subtitle = "AI conversations",
                used = meters.messages,
                limit = PlanStore.LIMIT_MESSAGES,
            )
            RefRowDivider(startIndent = 57.dp)
            // Image generation is not recorded anywhere in this build, so there
            // is nothing honest to count.
            UsageRow(
                icon = GsIcons.Image,
                tone = GsWellTone.Rose,
                title = "Images",
                subtitle = "Not metered in this build",
                used = -1,
                limit = PlanStore.LIMIT_IMAGES,
            )
            RefRowDivider(startIndent = 57.dp)
            UsageRow(
                icon = GsIcons.Upload,
                tone = GsWellTone.Mint,
                title = "File Uploads",
                subtitle = "Documents, files, and more",
                used = meters.uploads,
                limit = PlanStore.LIMIT_UPLOADS,
            )
            RefRowDivider(startIndent = 57.dp)
            // The wire only stores flash/thinking/auto per conversation, not per
            // message, so a per-month deep-thinking count cannot be derived.
            UsageRow(
                icon = GsIcons.BrainCircuit,
                tone = GsWellTone.Blue,
                title = "Advanced Reasoning",
                subtitle = "Not metered per message in this build",
                used = -1,
                limit = PlanStore.LIMIT_REASONING,
            )
        }

        // ---- Billing & Payment -------------------------------------------
        RefGroupHeader("Billing & Payment", "Manage your payment method")
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(vertical = 2.dp)) {
            RefSheetRow(
                title = "Payment Method",
                subtitle = "No card is stored — this build has no payment processor",
                onClick = null,
                leading = { RefWellTile(GsIcons.CreditCard, GsWellTone.Blue, 34.dp) },
                trailing = { RefTagPill("Not set", GsTheme.colors.textSecondary) },
            )
            RefRowDivider(startIndent = 57.dp)
            RefSheetRow(
                title = "Billing History",
                subtitle = "No invoices — none can be issued in this build",
                onClick = null,
                trailing = {},
                leading = { RefWellTile(GsIcons.Receipt, GsWellTone.Peach, 34.dp) },
            )
        }

        // ---- Upgrade Options ---------------------------------------------
        RefGroupHeader("Upgrade Options", "Your plan")
        RefQuietWashCard {
            RefWellTile(GsIcons.Sparkles, GsWellTone.Lilac, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Nexly Pro — everything included",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GsFonts.Product,
                    color = GsTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    "One plan, $12.99/month: more usage, advanced models, exclusive features and priority support.",
                    fontSize = 8.5.sp,
                    lineHeight = 11.sp,
                    fontFamily = GsFonts.Product,
                    color = GsTheme.colors.textTertiary,
                )
            }
            Spacer(Modifier.width(8.dp))
            RefSheetPillButton(
                label = "View Plan",
                onClick = { onNavigate(GsRoutes.BILLING) },
            )
        }

        Spacer(Modifier.height(14.dp))
        // The closing card is a real destination: the sheet's "Need help?" is
        // billing support, and this build's support page is where that lives.
        Box(Modifier.fillMaxWidth()) {
            RefInfoCard(
                icon = GsIcons.Crown,
                title = "Need help?",
                text = "Our support team is here to assist you with any billing or plan questions.",
                trailing = {
                    Icon(
                        GsIcons.ChevronRight,
                        contentDescription = null,
                        tint = GsTheme.colors.textTertiary,
                        modifier = Modifier.size(15.dp),
                    )
                },
            )
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Transparent)
                    .androidxClickable { onNavigate(GsRoutes.SETTINGS_HELP) },
            )
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** The sheet's tap wrapper: keeps the card's visual and makes it real. */
private fun Modifier.androidxClickable(onClick: () -> Unit): Modifier =
    this.then(
        Modifier.clickable(
            interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
            indication = null,
            onClick = onClick,
        )
    )

/**
 * A usage row: well, title/subtitle, right-aligned count, the mock's chevron
 * and the 4pt bar underneath. `used < 0` means the meter could not be counted;
 * the row then prints "—" and draws no bar rather than implying a real zero.
 */
@Composable
private fun UsageRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tone: GsWellTone,
    title: String,
    subtitle: String,
    used: Int,
    limit: Int,
) {
    val colors = GsTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RefWellTile(icon, tone, 34.dp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = GsFonts.Product,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                if (used < 0) "—" else "$used / ${limitLabel(limit)}",
                fontSize = 8.5.sp,
                fontFamily = GsFonts.Product,
                color = colors.textSecondary,
            )
            Spacer(Modifier.width(3.dp))
            Icon(
                GsIcons.ChevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(12.dp),
            )
        }
        Spacer(Modifier.height(5.dp))
        RefProgressBar(
            fraction = if (used < 0 || limit <= 0) null else used.toFloat() / limit,
            tone = tone.ink,
        )
    }
}

/** The mock prints "1,000" — a grouped count, not "1000". */
private fun limitLabel(limit: Int): String =
    String.format(java.util.Locale.US, "%,d", limit)

/** Real date from the real activation stamp; "—" when there is none. */
private fun formatDate(millis: Long): String {
    if (millis <= 0L) return "—"
    return java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
        .format(java.util.Date(millis))
}
