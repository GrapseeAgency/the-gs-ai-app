package com.grapsee.gsai.ui.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.theme.GsMotion

/**
 * Billing — the honest version. GS AI has no subscription, no payment
 * processing and no usage metering, so this screen renders none: the plan
 * cards, quotas, credits, payment methods and invoices that used to live
 * here were fabricated (the old "invoice download" even wrote fictional
 * documents into the real Library). One quiet card states the facts; if
 * paid plans ever launch, the real billing estate lands here.
 */
@Composable
fun BillingScreen(onBack: () -> Unit) {
    GsScreenScaffold(title = "Billing", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
        ) {
            GsCard {
                Text(
                    text = "GS AI is in early access",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(GsMotion.spaceS))
                Text(
                    text = "You don't need a subscription or payment to use GS AI. " +
                        "If paid plans ever launch, billing will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
