package com.grapsee.gsai.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.data.local.ModelCatalog
import com.grapsee.gsai.data.local.ModelStore
import com.grapsee.gsai.ui.components.RefArt
import com.grapsee.gsai.ui.components.RefArtwork
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefDivider
import com.grapsee.gsai.ui.components.RefFootnote
import com.grapsee.gsai.ui.components.RefPage
import com.grapsee.gsai.ui.components.RefPrimaryButton
import com.grapsee.gsai.ui.components.RefRow
import com.grapsee.gsai.ui.components.RefSection
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsTheme

/**
 * MODEL DETAILS (reference sheet: "Model Details") — what the on-device
 * engine actually is on THIS phone. Every number comes from ModelCatalog,
 * which in turn records the publisher's measured bytes and digests; nothing
 * here is a marketing figure.
 */
@Composable
fun ModelDetailsScreen(
    onBack: () -> Unit,
    onDownloadRequested: () -> Unit,
) {
    val colors = GsTheme.colors
    val context = LocalContext.current
    val tier = remember { ModelCatalog.classify(context) }
    val model = remember(tier) { ModelCatalog.modelFor(tier) }
    val installed = ModelStore.installedPath != null
    val local = SettingsStore.preferLocal

    RefPage(
        title = "Model Details",
        subtitle = "What runs on this phone, and what it can do.",
        onBack = onBack
    ) {
        if (model == null) {
            RefCard(padding = PaddingValues(18.dp)) {
                Text(
                    "This phone is below the on-device threshold",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary
                )
                Text(
                    "A local model needs a 64-bit device with at least 2 GB of RAM. "
                        + "The cloud engine works here exactly as it does everywhere else.",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )
            }
        } else {
            // Identity: the same bloom the rest of the app uses, never a badge.
            RefCard(padding = PaddingValues(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RefArtwork(RefArt.Bloom, size = 92.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            model.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary
                        )
                        Text(
                            if (installed) "Installed — runs without a network" else "Not installed yet",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary
                        )
                        Text(
                            "The same weights used by the desktop benchmarks, byte for byte.",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary
                        )
                    }
                }
            }

            // Numbers, measured.
            RefCard(padding = PaddingValues(vertical = 18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ModelStat(model.sizeLabel(), "Download")
                    ModelStat(ramLabel(model.minRamBytes), "Min RAM")
                    ModelStat(if (tier == ModelCatalog.Tier.HIGH) "1.5B" else "0.5B", "Parameters")
                }
            }

            RefSection(label = "Key capabilities") {
                CapabilityRow(GsIcons.WifiOff, "Works with the network off")
                RefDivider()
                CapabilityRow(GsIcons.Lock, "Prompts never leave this phone")
                RefDivider()
                CapabilityRow(GsIcons.AudioLines, "Streams tokens through the same chat surface")
                RefDivider()
                CapabilityRow(GsIcons.LayoutGrid, "Renders the same widget vocabulary as cloud answers")
            }

            RefSection(label = "This chat") {
                RefRow(
                    title = "Engine",
                    subtitle = if (local) "On-device is selected" else "Cloud is selected",
                    icon = GsIcons.Cpu
                )
                RefDivider()
                RefRow(
                    title = "Model file",
                    subtitle = model.id,
                    icon = GsIcons.FileText
                )
            }

            Spacer(Modifier.height(18.dp))
            RefPrimaryButton(
                if (installed) "Use this model" else "Download model",
                onClick = {
                    if (installed) {
                        SettingsStore.updatePreferLocal(true)
                        onBack()
                    } else {
                        onDownloadRequested()
                    }
                },
                icon = if (installed) GsIcons.Check else GsIcons.Download
            )
        }

        RefFootnote(
            "On-device generation is slower and uses battery. It is the honest choice when the "
                + "network is gone, not a silent replacement for the cloud engine."
        )
    }
}

@Composable
private fun CapabilityRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        androidx.compose.material3.Icon(
            icon,
            contentDescription = null,
            tint = GsTheme.colors.accentStrong,
            modifier = Modifier.size(16.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = GsTheme.colors.textPrimary
        )
    }
}

@Composable
private fun ModelStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp
            ),
            color = GsTheme.colors.textPrimary
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = GsTheme.colors.textSecondary
        )
    }
}

private fun ramLabel(bytes: Long): String {
    val gb = bytes / 1_000_000_000.0
    return if (gb >= 1) String.format("%.0f GB", gb) else String.format("%.0f MB", bytes / 1_000_000.0)
}
