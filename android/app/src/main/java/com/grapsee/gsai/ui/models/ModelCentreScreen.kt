package com.grapsee.gsai.ui.models

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.ModelPrefs
import com.grapsee.gsai.data.model.ModelCatalog
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsListItem
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.components.GsSectionHeader
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.gsHaptic
import com.grapsee.gsai.ui.theme.rememberAuroraBrush

private val reasoningModes = listOf(
    "Fast", "Balanced", "Deep reasoning", "Research",
    "Coding", "Creative", "Vision", "Voice"
)

@Composable
fun ModelCentreScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    // Preference-backed: the pick survives relaunches and the chat send path
    // reads the same id (ChatRepository gates it against the remote registry).
    var defaultId by remember { mutableStateOf(ModelPrefs.defaultId(context)) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var selectedMode by remember { mutableStateOf(ModelPrefs.mode(context)) }

    val current = ModelCatalog.byId(defaultId) ?: ModelCatalog.default

    GsScreenScaffold(
        title = "Models",
        actions = {
            TextButton(onClick = { onNavigate(GsRoutes.MODEL_COMPARE) }) {
                Text("Compare")
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
        ) {
            // Current default
            GsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                        ) {
                            AuroraIndicator()
                            Text(
                                text = "Current default",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(GsMotion.spaceXS))
                        Text(
                            text = current.displayName,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = current.tagline,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    GsChip(text = "Default", selected = true)
                }
            }

            // Reasoning mode
            Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                GsSectionHeader(title = "Reasoning mode")
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                ) {
                    reasoningModes.forEach { mode ->
                        GsChip(
                            text = mode,
                            selected = mode == selectedMode,
                            onClick = {
                                view.gsHaptic(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                                selectedMode = mode
                                ModelPrefs.setMode(context, mode)
                            }
                        )
                    }
                }
            }

            // Catalogue
            Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                GsSectionHeader(title = "All models")
                ModelCatalog.all.forEach { model ->
                    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)) {
                        GsListItem(
                            title = model.displayName,
                            subtitle = "${model.tagline} · ${model.contextK}K context",
                            leading = { ModelInitials(model.displayName) },
                            trailing = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    SpeedDots(model.speedTier)
                                    if (model.id == defaultId) {
                                        Icon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = "Default model",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                expandedId = if (expandedId == model.id) null else model.id
                            }
                        )
                        if (expandedId == model.id) {
                            GsCard {
                                Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                                    Text(
                                        text = "Capabilities",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                                    ) {
                                        model.capabilities.forEach { capability ->
                                            GsChip(text = capability, selected = false)
                                        }
                                    }
                                    Text(
                                        text = "Modes",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                                    ) {
                                        model.modes.forEach { mode ->
                                            GsChip(text = mode, selected = false)
                                        }
                                    }
                                    TextButton(onClick = {
                                        defaultId = model.id
                                        ModelPrefs.setDefaultId(context, model.id)
                                    }) {
                                        Text(
                                            text = "Set as default",
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(GsMotion.spaceL))
        }
    }
}

/** Small pulsing aurora dot — the "AI is alive" marker on the default card. */
@Composable
private fun AuroraIndicator() {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(rememberAuroraBrush(), CircleShape)
    )
}

@Composable
private fun ModelInitials(displayName: String) {
    val initials = displayName
        .split(" ")
        .drop(1) // skip the "GS" brand prefix
        .mapNotNull { it.firstOrNull() }
        .joinToString("")
        .uppercase()
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
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
