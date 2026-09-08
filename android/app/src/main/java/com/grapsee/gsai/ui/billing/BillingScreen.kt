package com.grapsee.gsai.ui.billing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsListItem
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.components.GsSectionHeader
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.kineticPress
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Fresh sample dates: the renewal anchor is the 12th of next month and the
 *  invoices are the three most recent completed billing months — computed so
 *  the samples never go stale (they used to be frozen at Aug 2025). */
private val renewalDateText: String =
    LocalDate.now().plusMonths(1).withDayOfMonth(12)
        .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))

private val recentInvoiceMonths: List<String> =
    (1..3).map { offset ->
        LocalDate.now().minusMonths(offset.toLong())
            .format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))
    }

@Composable
fun BillingScreen(onBack: () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    var currentPlan by remember { mutableStateOf("pro") }
    var selectedPlan by remember { mutableStateOf("pro") }
    var showTeamDialog by remember { mutableStateOf(false) }
    var showFreeDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        GsScreenScaffold(title = "Subscription", onBack = onBack) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
            ) {
                CurrentPlanCard(currentPlan = currentPlan)
                CreditsCard(onPurchase = { showMessage("Checkout coming to the Play Store build") })

                GsSectionHeader(title = "Plans")
                PlanCard(
                    name = "Free",
                    price = "£0 forever",
                    features = listOf("40 messages a day", "1 model", "Basic tools"),
                    selected = selectedPlan == "free",
                    tag = if (currentPlan == "free") "Current" else null,
                    onClick = { selectedPlan = "free" }
                )
                PlanCard(
                    name = "Pro",
                    price = "£16/month",
                    features = listOf(
                        "Unlimited chats",
                        "All 8 models",
                        "Vision + voice",
                        "2,000 message quota"
                    ),
                    selected = selectedPlan == "pro",
                    tag = if (currentPlan == "pro") "Current" else null,
                    onClick = { selectedPlan = "pro" }
                )
                PlanCard(
                    name = "Team",
                    price = "£39/user · monthly",
                    features = listOf("Everything in Pro", "Shared workspaces", "Admin controls"),
                    selected = selectedPlan == "team",
                    tag = if (currentPlan == "team") "Current" else null,
                    onClick = { selectedPlan = "team" }
                )
                Spacer(Modifier.height(GsMotion.spaceS))
                Button(onClick = { showTeamDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Switch to Team")
                }
                Spacer(Modifier.height(GsMotion.spaceS))
                FilledTonalButton(
                    onClick = { showFreeDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Downgrade to Free")
                }

                PaymentMethodsCard(
                    onAddPayment = { showMessage("Payments land in the Play Store build") }
                )
                InvoicesCard(onDownload = { month, meta ->
                    // Real save: a readable invoice lands in the Library as a
                    // document — no more save-in-name-only.
                    scope.launch {
                        runCatching {
                            ServiceLocator.chat.saveToLibrary(
                                content = invoiceContent(month, meta),
                                kind = "document",
                                title = "Invoice $month"
                            )
                        }
                            .onSuccess { showMessage("Invoice saved to Library") }
                            .onFailure { showMessage("Couldn't save right now") }
                    }
                })
                ManageCard(
                    onRestore = { showMessage("Purchases restored") },
                    onCancel = { showCancelDialog = true }
                )
                Spacer(Modifier.height(GsMotion.spaceL))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showTeamDialog) {
        AlertDialog(
            onDismissRequest = { showTeamDialog = false },
            title = { Text("Change plan?") },
            text = {
                Text("Your Pro features end on the renewal date. Team adds shared workspaces and admin controls at £39 per user.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showTeamDialog = false
                    currentPlan = "team"
                    selectedPlan = "team"
                    showMessage("Plan change scheduled")
                }) { Text("Switch to Team") }
            },
            dismissButton = {
                TextButton(onClick = { showTeamDialog = false }) { Text("Keep Pro") }
            }
        )
    }

    if (showFreeDialog) {
        AlertDialog(
            onDismissRequest = { showFreeDialog = false },
            title = { Text("Change plan?") },
            text = {
                Text("Your Pro features end on the renewal date. Free keeps 40 messages a day, 1 model and basic tools.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFreeDialog = false
                        currentPlan = "free"
                        selectedPlan = "free"
                        showMessage("Plan change scheduled")
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Downgrade to Free") }
            },
            dismissButton = {
                TextButton(onClick = { showFreeDialog = false }) { Text("Keep Pro") }
            }
        )
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel subscription?") },
            text = {
                Text("Pro stays active until $renewalDateText. After that your account moves to Free — your chats and files are kept.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        currentPlan = "free"
                        selectedPlan = "free"
                        showMessage("Subscription cancelled — Pro ends $renewalDateText")
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Cancel subscription") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("Keep Pro") }
            }
        )
    }
}

@Composable
private fun CurrentPlanCard(currentPlan: String) {
    val planName = when (currentPlan) {
        "team" -> "Team"
        "free" -> "Free"
        else -> "Pro"
    }
    val planMeta = when (currentPlan) {
        "team" -> "£39/user · renews $renewalDateText"
        "free" -> "£0 · no renewal date"
        else -> "£16/month · renews $renewalDateText"
    }
    GsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = planName,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(GsMotion.spaceXS))
                Text(
                    text = planMeta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            GsChip(text = "Active", selected = currentPlan != "free")
        }
        Spacer(Modifier.height(GsMotion.spaceM))
        Text(
            text = "Usage this cycle",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(GsMotion.spaceS))
        UsageBar(label = "Messages", value = "1,284 / 2,000", fraction = 0.64f)
        Spacer(Modifier.height(GsMotion.spaceS))
        UsageBar(label = "Voice", value = "45m / 120m", fraction = 0.38f)
        Spacer(Modifier.height(GsMotion.spaceS))
        UsageBar(label = "Images", value = "32 / 100", fraction = 0.32f)
    }
}

@Composable
private fun UsageBar(label: String, value: String, fraction: Float) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun CreditsCard(onPurchase: () -> Unit) {
    GsCard {
        Text(
            text = "Credits",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(GsMotion.spaceXS))
        Text(
            text = "240 credits",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(GsMotion.spaceM))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            listOf("+100 · £2", "+500 · £8", "+2,000 · £25").forEach { pack ->
                GsChip(text = pack, selected = false, onClick = onPurchase)
            }
        }
    }
}

/** Selectable plan card — 2dp primary border when selected (custom Surface, mirrors GsCard). */
@Composable
private fun PlanCard(
    name: String,
    price: String,
    features: List<String>,
    selected: Boolean,
    tag: String?,
    onClick: () -> Unit
) {
    val planInteraction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .kineticPress(planInteraction),
        shape = RoundedCornerShape(GsMotion.radiusCard),
        color = MaterialTheme.colorScheme.surface,
        interactionSource = planInteraction,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(GsMotion.spaceM),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                if (tag != null) {
                    GsChip(text = tag, selected = true)
                }
            }
            Text(
                text = price,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            features.forEach { feature ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = feature,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentMethodsCard(onAddPayment: () -> Unit) {
    GsCard {
        Text(
            text = "Payment methods",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(GsMotion.spaceS))
        GsListItem(
            title = "Visa •• 4242",
            subtitle = "Expiry 09/27",
            leading = { IconBadge(icon = Icons.Outlined.CreditCard) }
        )
        Spacer(Modifier.height(GsMotion.spaceXS))
        GsListItem(
            title = "Apple Pay",
            subtitle = "Default for renewals",
            leading = { IconBadge(icon = Icons.Outlined.AccountBalanceWallet) }
        )
        Spacer(Modifier.height(GsMotion.spaceS))
        // "Dashed-feel" add row: 1dp outline surface (Compose has no built-in dashed border).
        Surface(
            onClick = onAddPayment,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Add payment method",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InvoicesCard(onDownload: (String, String) -> Unit) {
    GsCard {
        Text(
            text = "Invoices",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(GsMotion.spaceS))
        recentInvoiceMonths.map { it to "£16.00 · Paid" }.forEach { (month, meta) ->
            GsListItem(
                title = month,
                subtitle = meta,
                leading = { IconBadge(icon = Icons.Outlined.ReceiptLong) },
                trailing = {
                    IconButton(onClick = { onDownload(month, meta) }) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = "Download $month invoice",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
            Spacer(Modifier.height(GsMotion.spaceXS))
        }
    }
}

@Composable
private fun ManageCard(onRestore: () -> Unit, onCancel: () -> Unit) {
    GsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRestore)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Restore purchases",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onCancel)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Cancel subscription",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** A real, readable invoice: the document that lands in the Library. */
private fun invoiceContent(month: String, meta: String): String =
    "GS AI — Invoice $month\n\nPlan: Pro · $meta\nBilled monthly. Chats, files and exports stay yours."

@Composable
private fun IconBadge(icon: ImageVector) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(34.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
