package com.grapsee.gsai.ui.billing

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.components.RefArt
import com.grapsee.gsai.ui.components.RefArtwork
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefDivider
import com.grapsee.gsai.ui.components.RefFootnote
import com.grapsee.gsai.ui.components.RefPage
import com.grapsee.gsai.ui.components.RefSection
import com.grapsee.gsai.ui.components.RefStatusPill
import com.grapsee.gsai.ui.components.RefValueRow
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.components.RefPrimaryButton

/**
 * Billing — the honest version.
 *
 * GS AI has no subscription, payment processing or usage metering, so this
 * page states exactly that and shows no invented invoices, credits or plan
 * cards. The free tier is real; when paid plans launch the real billing
 * estate lands here.
 */
@Composable
fun BillingScreen(onBack: () -> Unit) {
    RefPage(
        title = "Billing",
        subtitle = "No plan, no card, nothing to manage.",
        onBack = onBack
    ) {
        RefCard(padding = androidx.compose.foundation.layout.PaddingValues(22.dp)) {
            RefArtwork(
                art = RefArt.Success,
                size = 96.dp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                text = "Free during early access",
                style = MaterialTheme.typography.headlineSmall,
                color = GsTheme.colors.textPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "You don't need a subscription or a payment method to use GS AI. There are no invoices, credits or renewal dates because there is no charge.",
                style = MaterialTheme.typography.bodyMedium,
                color = GsTheme.colors.textSecondary
            )
            Spacer(Modifier.height(14.dp))
            RefStatusPill("Early access")
        }

        RefSection(label = "Plan") {
            RefValueRow("Current plan", "Free")
            RefDivider()
            RefValueRow("Payment method", "None on file")
            RefDivider()
            RefValueRow("Invoices", "None")
            RefDivider()
            RefValueRow("Usage metering", "Not enabled")
        }

        RefFootnote("If paid plans launch, the price, renewal date and invoices will appear on this page. Nothing is charged silently.")
        Spacer(Modifier.height(10.dp))
        RefPrimaryButton(label = "Back to settings", onClick = onBack)
    }
}
