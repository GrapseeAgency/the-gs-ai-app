package com.grapsee.gsai.ui.subscription

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.PlanStore
import com.grapsee.gsai.data.UsageMeter
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefInfoCard
import com.grapsee.gsai.ui.components.RefQuietButton
import com.grapsee.gsai.ui.components.RefRowDivider
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefStatusPill
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Subscription & Usage — docs/reference/pending/subscription-usage.png.
 *
 * The sheet's groups, wells, gradient hero and progress bars are reproduced in
 * its order. The numbers are not: every counter comes from
 * [UsageMeter], which counts real rows in this device's database, and the plan
 * facts come from [PlanStore] — including the $12.99/month the owner confirmed.
 * The mock's $19.99 is placeholder art and is not drawn.
 *
 * WHAT IS TRUTHFUL-ABSENT
 * There is no payment processor in this build: no card, no invoice, no
 * purchase path. The "Visa •••• 4242" the sheet draws exists nowhere in this
 * codebase, so the Payment Method and Billing History rows keep their slots
 * and state the reality instead of inventing a card. That is the same
 * treatment the shipped Privacy screen uses for its unbackable rows.
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
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            GsWellTone.Lilac.ink.copy(alpha = 0.16f),
                            GsWellTone.Rose.ink.copy(alpha = 0.14f),
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
                RefWellTile(GsIcons.Crown, GsWellTone.Peach, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            PlanStore.planName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = GsFonts.Product,
                            color = GsTheme.colors.textPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        if (PlanStore.active) RefStatusPill("Active")
                    }
                    Text(
                        "Unlock more limits, advanced models, and premium features.",
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        fontFamily = GsFonts.Product,
                        color = GsTheme.colors.textTertiary,
                    )
                }
                RefQuietButton(label = "Manage Plan ›", onClick = { onNavigate(com.grapsee.gsai.ui.navigation.GsRoutes.BILLING) }, modifier = Modifier.width(112.dp))
            }
        }

        // ---- Plan Details -------------------------------------------------
        RefGroupHeader("Plan Details", "Current plan information")
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(14.dp)) {
            PlanFact("Plan Name", PlanStore.planName)
            PlanFact("Billing Cycle", PlanStore.cycleLabel)
            PlanFact("Next Billing Date", formatDate(PlanStore.nextBillingMillis()))
            PlanFact("Price", PlanStore.priceLabel)
        }

        // ---- Usage Overview ----------------------------------------------
        RefGroupHeader("Usage Overview", "Your current month's usage")
        RefCard(role = GsGlassRole.CardLight) {
            UsageRow(
                icon = GsIcons.MessageCircle,
                tone = GsWellTone.Lilac,
                title = "Messages",
                subtitle = "AI conversations",
                used = meters.messages,
                limit = PlanStore.LIMIT_MESSAGES,
            )
            RefRowDivider()
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
            RefRowDivider()
            UsageRow(
                icon = GsIcons.Upload,
                tone = GsWellTone.Mint,
                title = "File Uploads",
                subtitle = "Documents, files, and more",
                used = meters.uploads,
                limit = PlanStore.LIMIT_UPLOADS,
            )
            RefRowDivider()
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
        RefCard(role = GsGlassRole.CardLight) {
            RefInfoCard(
                text = "No payment method is stored on this device — Nexly has no payment processor in this build, so there is nothing to charge and nothing to keep.",
                icon = GsIcons.CreditCard,
            )
            RefRowDivider()
            RefInfoCard(
                text = "No invoices yet. Billing history appears only once payments are actually taken.",
                icon = GsIcons.Receipt,
            )
        }

        // ---- Upgrade Options ---------------------------------------------
        RefGroupHeader("Upgrade Options", "Compare plans")
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
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
                RefWellTile(GsIcons.Sparkles, GsWellTone.Lilac, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Go Pro (or higher)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GsFonts.Product,
                        color = GsTheme.colors.textPrimary,
                    )
                    Text(
                        "More usage, advanced models, exclusive features and priority support.",
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        fontFamily = GsFonts.Product,
                        color = GsTheme.colors.textTertiary,
                    )
                }
                RefQuietButton(label = "View Plans ›", onClick = { onNavigate(com.grapsee.gsai.ui.navigation.GsRoutes.BILLING) }, modifier = Modifier.width(104.dp))
            }
        }

        Spacer(Modifier.height(14.dp))
        RefInfoCard(
            icon = GsIcons.Crown,
            title = "Need help?",
            text = "Our support team is here to assist you with any billing or plan questions.",
        )
        Spacer(Modifier.height(10.dp))
    }
}

/** One label/value pair in the Plan Details card. */
@Composable
private fun PlanFact(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Text(
            label,
            modifier = Modifier.width(120.dp),
            fontSize = 12.sp,
            fontFamily = GsFonts.Product,
            color = GsTheme.colors.textSecondary,
        )
        Text(
            value,
            fontSize = 12.sp,
            fontFamily = GsFonts.Product,
            color = GsTheme.colors.textPrimary,
        )
    }
}

/**
 * A usage row. `used < 0` means the meter could not be counted; the row then
 * prints "—" and draws no bar rather than implying a real zero.
 */
@Composable
private fun UsageRow(
    icon: ImageVector,
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RefWellTile(icon, tone, 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = GsFonts.Product,
                    color = colors.textPrimary,
                )
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textTertiary,
                )
            }
            Text(
                if (used < 0) "—" else "$used / $limit",
                fontSize = 11.5.sp,
                fontFamily = GsFonts.Product,
                color = colors.textSecondary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.border.copy(alpha = 0.35f)),
        ) {
            if (used >= 0 && limit > 0) {
                val fraction = (used.toFloat() / limit).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(tone.ink.copy(alpha = 0.75f)),
                )
            }
        }
    }
}

/** Real date from the real activation stamp; "—" when there is none. */
private fun formatDate(millis: Long): String {
    if (millis <= 0L) return "—"
    return java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
        .format(java.util.Date(millis))
}
