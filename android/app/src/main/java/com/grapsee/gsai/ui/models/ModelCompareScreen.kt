package com.grapsee.gsai.ui.models

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.model.ModelCatalog
import com.grapsee.gsai.data.model.ModelInfo
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.theme.GsMotion

@Composable
fun ModelCompareScreen(onBack: () -> Unit) {
    var selected by remember { mutableStateOf(listOf("gs-swift", "gs-balanced", "gs-deep")) }
    val models = selected.mapNotNull { ModelCatalog.byId(it) }

    GsScreenScaffold(title = "Compare", onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
        ) {
            Text(
                text = "Pick three models to compare side by side. Choosing a new one replaces the oldest slot.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
            ) {
                ModelCatalog.all.forEach { model ->
                    GsChip(
                        text = model.displayName.removePrefix("GS "),
                        selected = model.id in selected,
                        onClick = {
                            if (model.id !in selected) {
                                selected = selected.drop(1) + model.id
                            }
                        }
                    )
                }
            }

            GsCard {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    // Header — three model columns
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = GsMotion.spaceS),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.width(76.dp))
                        models.forEach { model ->
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = model.displayName.removePrefix("GS "),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    CompareRow(label = "Context", models = models) { model ->
                        Text(
                            text = "${model.contextK}K",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    CompareRow(label = "Speed", models = models) { model ->
                        SpeedDots(model.speedTier)
                    }
                    CompareRow(label = "Tools", models = models) { model ->
                        CapCell("tools" in model.capabilities)
                    }
                    CompareRow(label = "Reasoning", models = models) { model ->
                        CapCell("reasoning" in model.capabilities)
                    }
                    CompareRow(label = "Vision", models = models) { model ->
                        CapCell("vision" in model.capabilities)
                    }
                    CompareRow(label = "Voice", models = models) { model ->
                        CapCell("voice" in model.capabilities)
                    }
                }
            }

            Spacer(modifier = Modifier.height(GsMotion.spaceL))
        }
    }
}

@Composable
private fun CompareRow(
    label: String,
    models: List<ModelInfo>,
    cell: @Composable (ModelInfo) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp)
        )
        models.forEach { model ->
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                cell(model)
            }
        }
    }
}

@Composable
private fun CapCell(supported: Boolean) {
    if (supported) {
        Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = "Supported",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    } else {
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = "Not supported",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 1–3 filled dots by speed tier: fast=3, balanced=2, deep=1. */
@Composable
private fun SpeedDots(tier: String) {
    val filled = when (tier) {
        "fast" -> 3
        "balanced" -> 2
        else -> 1
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(3) { index ->
            Surface(
                shape = CircleShape,
                color = if (index < filled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier.size(6.dp)
            ) {}
        }
    }
}
