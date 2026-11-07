package com.grapsee.gsai.ui.models

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.ModelPrefs
import com.grapsee.gsai.data.model.ModelCatalog
import com.grapsee.gsai.data.model.ModelInfo
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsListItem
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.components.GsSectionHeader
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.gsHaptic
import com.grapsee.gsai.ui.theme.rememberAuroraBrush

/**
 * PHASE 2 — the Model Centre speaks USER language, not architecture language.
 *
 * The catalogue is grouped into three plain tiers a normal person can act on:
 * Fast / Everyday / Best for difficult questions. Picking a tier member is a
 * single tap (it writes the same ModelPrefs key the chat send path reads).
 * Everything technical — context windows, speed dots, capability chips,
 * reasoning modes — lives ONLY inside a per-model "Advanced details"
 * disclosure, collapsed by default. "Compare models" (the spec-sheet view) is
 * demoted to the Advanced section at the bottom, off the consumer path.
 */
@Composable
fun ModelCentreScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    // Preference-backed: the pick survives relaunches and the chat send path
    // reads the same id (ChatRepository gates it against the remote registry).
    var defaultId by remember { mutableStateOf(ModelPrefs.defaultId(context)) }
    var advancedId by remember { mutableStateOf<String?>(null) }

    val current = ModelCatalog.byId(defaultId) ?: ModelCatalog.default

    val tiers = remember {
        listOf(
            Triple(
                "Fast",
                "Quick answers when speed matters",
                ModelCatalog.all.filter { it.speedTier == "fast" }
            ),
            Triple(
                "Everyday",
                "Smart help for daily questions",
                ModelCatalog.all.filter { it.speedTier == "balanced" }
            ),
            Triple(
                "Best for difficult questions",
                "Takes its time, thinks deeper",
                ModelCatalog.all.filter { it.speedTier == "deep" }
            )
        )
    }

    GsScreenScaffold(title = "Models") {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
        ) {
            // Current default — quiet, plain language only.
            GsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(rememberAuroraBrush(), CircleShape)
                            )
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
                }
            }

            // The three consumer tiers. A tap selects — that is the whole
            // consumer surface. No "128K context" subtitles, no speed dots.
            tiers.forEach { (tierTitle, tierSubtitle, models) ->
                Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                    GsSectionHeader(title = tierTitle)
                    Text(
                        text = tierSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    models.forEach { model ->
                        Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)) {
                            GsListItem(
                                title = model.displayName,
                                subtitle = model.tagline,
                                leading = { ModelInitials(model.displayName) },
                                trailing = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (model.id == defaultId) {
                                            Icon(
                                                imageVector = Icons.Outlined.Check,
                                                contentDescription = "Default model",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Icon(
                                            imageVector = if (advancedId == model.id) {
                                                Icons.Outlined.ExpandLess
                                            } else {
                                                Icons.Outlined.ExpandMore
                                            },
                                            contentDescription = if (advancedId == model.id) {
                                                "Hide advanced details"
                                            } else {
                                                "Advanced details"
                                            },
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clickable {
                                                    advancedId =
                                                        if (advancedId == model.id) null else model.id
                                                }
                                                .padding(6.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    view.gsHaptic(HapticFeedbackConstants.VIRTUAL_KEY)
                                    defaultId = model.id
                                    ModelPrefs.setDefaultId(context, model.id)
                                }
                            )
                            // Technical material lives ONLY behind this
                            // disclosure — collapsed for every normal user.
                            if (advancedId == model.id) {
                                ModelAdvancedDetails(model)
                            }
                        }
                    }
                }
            }

            // Advanced — the deliberate secondary path. Compare (the spec
            // table) is no longer part of normal consumer navigation.
            Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                GsSectionHeader(title = "Advanced")
                GsListItem(
                    title = "Compare models",
                    subtitle = "Side-by-side technical view",
                    onClick = { onNavigate(GsRoutes.MODEL_COMPARE) }
                )
            }

            Spacer(modifier = Modifier.height(GsMotion.spaceL))
        }
    }
}

/** The full technical row — reachable only via the "Advanced details" toggle. */
@Composable
private fun ModelAdvancedDetails(model: ModelInfo) {
    GsCard {
        Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
            DetailLine("Context window", "${model.contextK}K tokens")
            DetailLine("Speed", speedLabel(model.speedTier))
            Text(
                text = "Capabilities",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScrollSafe(),
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
                modifier = Modifier.fillMaxWidth().horizontalScrollSafe(),
                horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
            ) {
                model.modes.forEach { mode ->
                    GsChip(text = mode, selected = false)
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

private fun speedLabel(tier: String): String = when (tier) {
    "fast" -> "Fastest"
    "balanced" -> "Balanced"
    else -> "Deepest"
}

/** Small horizontal scroller for chip rows — bounded, one line. */
@Composable
private fun Modifier.horizontalScrollSafe(): Modifier =
    this.horizontalScroll(rememberScrollState())

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
