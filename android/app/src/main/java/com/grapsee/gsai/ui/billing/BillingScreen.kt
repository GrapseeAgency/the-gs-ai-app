package com.grapsee.gsai.ui.billing

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.PlanStore
import com.grapsee.gsai.ui.components.RefArt
import com.grapsee.gsai.ui.components.RefArtwork
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefDivider
import com.grapsee.gsai.ui.components.RefFootnote
import com.grapsee.gsai.ui.components.RefPage
import com.grapsee.gsai.ui.components.RefPrimaryButton
import com.grapsee.gsai.ui.components.RefSection
import com.grapsee.gsai.ui.components.RefStatusPill
import com.grapsee.gsai.ui.components.RefValueRow
import com.grapsee.gsai.ui.theme.GsTheme

/**
 * Billing — the plan, told truthfully.
 *
 * This page used to say "Free during early access", which contradicted the
 * owner's two facts: there is no free tier, and the plan costs $12.99/month.
 * The price now comes from [PlanStore] so the number cannot drift from the
 * Subscription & Usage sheet.
 *
 * There is still no payment processor in this build, so no card, invoice or
 * renewal can exist. The rows keep their slots and say exactly that.
 */
@Composable
fun BillingScreen(onBack: () -> Unit) {
    RefPage(
        title = "Billing",
        subtitle = "Your plan and payment details.",
        onBack = onBack
    ) {
        RefCard(padding = PaddingValues(22.dp)) {
            RefArtwork(
                art = RefArt.Success,
                size = 96.dp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                text = PlanStore.planName,
                style = MaterialTheme.typography.headlineSmall,
                color = GsTheme.colors.textPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "One plan, ${PlanStore.priceLabel}. No payment is taken in this build: there is no card, no invoice and no purchase path, so nothing here can charge you silently.",
                style = MaterialTheme.typography.bodyMedium,
                color = GsTheme.colors.textSecondary
            )
            Spacer(Modifier.height(14.dp))
            if (PlanStore.active) {
                RefStatusPill("Active")
            } else {
                RefStatusPill("Not activated", GsTheme.colors.textSecondary)
            }
        }

        RefSection(label = "Plan") {
            RefValueRow("Current plan", PlanStore.planName)
            RefDivider()
            RefValueRow("Billing cycle", PlanStore.cycleLabel)
            RefDivider()
            RefValueRow("Price", PlanStore.priceLabel)
            RefDivider()
            RefValueRow(
                "Next billing date",
                if (PlanStore.nextBillingMillis() > 0L) {
                    java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
                        .format(java.util.Date(PlanStore.nextBillingMillis()))
                } else {
                    "—"
                }
            )
            RefDivider()
            RefValueRow("Payment method", "Not set — no processor in this build")
            RefDivider()
            RefValueRow("Invoices", "None can be issued")
        }

        RefFootnote("Subscription & Usage shows the plan, this month's real usage and the same $12.99 price. Nothing is charged silently.")
        Spacer(Modifier.height(10.dp))
        RefPrimaryButton(label = "Back to settings", onClick = onBack)
    }
}
