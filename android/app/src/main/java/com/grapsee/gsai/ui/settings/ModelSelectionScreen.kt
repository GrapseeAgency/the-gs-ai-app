package com.grapsee.gsai.ui.settings

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.GsModel
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.LocalGsIsDark

/**
 * Model Selection — docs/reference/pending/model-selection-v2.png.
 *
 * The reference's inventory, in its order: the four-tab capability bar, the
 * "Available Models" list with the Recommended card lit, the Reasoning Effort
 * control, the Auto Select row and the Continue action.
 *
 * WHAT EACH CONTROL DOES (see [com.grapsee.gsai.data.ModelRouter] for the rule):
 *  - a tab filters the list by the capability the model declares;
 *  - picking a card stores the model AND moves the effort chip to that model's
 *    default, so the tap has a visible consequence;
 *  - the effort control writes the wire mode the next reply uses;
 *  - Auto Select, when on, lets the send path choose the model per message and
 *    take that model's effort; when off, the effort control above is the
 *    authority. Both states are read by `ModelRouter.modeForMessage`.
 */
@Composable
fun ModelSelectionScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    /** Injectable for the JVM suite: the persisted ids are the real contract. */
    preferred: String = SettingsStore.preferredModel,
    effort: String = SettingsStore.reasoningEffort,
    autoSelect: Boolean = SettingsStore.autoSelectModel,
    onPreferred: (String) -> Unit = { SettingsStore.updatePreferredModel(it) },
    onEffort: (String) -> Unit = { SettingsStore.updateReasoningEffort(it) },
    onAutoSelect: (Boolean) -> Unit = { SettingsStore.updateAutoSelectModel(it) },
) {
    var tab = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("all") }
    val shown = GsModel.entries.filter { tab.value == "all" || tab.value in it.capabilities }
    val selected = GsModel.byId(preferred)

    RefSettingsPage(
        title = "Model Selection",
        subtitle = "Choose the best model for your needs.",
        onBack = onBack,
    ) {
        ModelTabs(selected = tab.value, onSelect = { tab.value = it })
        Spacer(Modifier.height(4.dp))

        RefGroupHeader(
            title = "Available Models",
            trailing = { RecommendedCaption() },
        )
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            shown.forEach { model ->
                ModelCard(
                    model = model,
                    isSelected = model == selected,
                    onClick = { onPreferred(model.id) },
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        ReasoningBlock(
            selectedEffort = effort,
            autoSelect = autoSelect,
            onEffort = onEffort,
            onAutoSelect = onAutoSelect,
        )

        Spacer(Modifier.height(18.dp))
        ContinueButton(onClick = onContinue)
        Spacer(Modifier.height(6.dp))
    }
}

// ---------------------------------------------------------------------------
// Tabs
// ---------------------------------------------------------------------------

private data class ModelTab(val id: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val MODEL_TABS = listOf(
    ModelTab("all", "All", GsIcons.Sparkles),
    ModelTab("text", "Text", GsIcons.MessageCircle),
    ModelTab("vision", "Vision", GsIcons.Eye),
    ModelTab("code", "Code", GsIcons.Code),
)

@Composable
private fun ModelTabs(selected: String, onSelect: (String) -> Unit) {
    val colors = GsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.42f), RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MODEL_TABS.forEach { item ->
            val active = item.id == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .testTag("model-tab-${item.id}")
                    .background(
                        if (active) colors.elevatedSurface.copy(alpha = 0.95f) else Color.Transparent,
                        RoundedCornerShape(15.dp),
                    )
                    .then(
                        if (active) {
                            Modifier.border(1.dp, colors.accent.copy(alpha = 0.18f), RoundedCornerShape(15.dp))
                        } else {
                            Modifier
                        }
                    )
                    .selectable(
                        selected = active,
                        role = Role.Tab,
                        onClick = { onSelect(item.id) },
                    )
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.material3.Icon(
                    item.icon,
                    contentDescription = null,
                    tint = if (active) colors.accentStrong else colors.textSecondary,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.height(3.dp))
                androidx.compose.material3.Text(
                    item.label,
                    fontSize = 8.5.sp,
                    fontFamily = GsFonts.Product,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) colors.textPrimary else colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun RecommendedCaption() {
    val colors = GsTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(
            GsIcons.Sparkles,
            contentDescription = null,
            tint = colors.accentStrong,
            modifier = Modifier.size(11.dp),
        )
        Spacer(Modifier.width(4.dp))
        androidx.compose.material3.Text(
            "Recommended",
            fontSize = 9.sp,
            fontFamily = GsFonts.Product,
            color = colors.textTertiary,
        )
    }
}

// ---------------------------------------------------------------------------
// Model card
// ---------------------------------------------------------------------------

private fun GsModel.tone(): GsWellTone = when (this) {
    GsModel.PRO -> GsWellTone.Lilac
    GsModel.LITE -> GsWellTone.Peach
    GsModel.VISION -> GsWellTone.Blue
    GsModel.CODE -> GsWellTone.Mint
}

private fun GsModel.glyph() = when (this) {
    GsModel.PRO -> GsIcons.Sparkles
    GsModel.LITE -> GsIcons.Leaf
    GsModel.VISION -> GsIcons.Eye
    GsModel.CODE -> GsIcons.Code
}

private fun GsModel.description(): String = when (this) {
    GsModel.PRO -> "Our most capable model. Great for complex tasks, creative work and deep analysis."
    GsModel.LITE -> "Fast, efficient and perfect for everyday questions and quick tasks."
    GsModel.VISION -> "Advanced image understanding and multimodal capabilities."
    GsModel.CODE -> "Specialized for programming, debugging and technical tasks."
}

private fun GsModel.tags(): List<String> = when (this) {
    GsModel.PRO -> listOf("Best for complex tasks", "Creative", "Balanced")
    GsModel.LITE -> listOf("Fast", "Efficient", "Everyday")
    GsModel.VISION -> listOf("Image", "Vision", "Multimodal")
    GsModel.CODE -> listOf("Coding", "Debugging", "Technical")
}

@Composable
private fun ModelCard(model: GsModel, isSelected: Boolean, onClick: () -> Unit) {
    val colors = GsTheme.colors
    val dark = LocalGsIsDark.current
    val tint = model.tone()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("model-card-${model.id}")
            .background(
                if (isSelected) {
                    if (dark) colors.accentSoft.copy(alpha = 0.55f) else tint.wash.copy(alpha = 0.55f)
                } else {
                    Color.White.copy(alpha = if (dark) 0.07f else 0.40f)
                },
                RoundedCornerShape(20.dp),
            )
            .then(
                if (isSelected) {
                    Modifier.border(1.2.dp, colors.accent.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                } else {
                    Modifier.border(1.dp, Color.White.copy(alpha = if (dark) 0.10f else 0.55f), RoundedCornerShape(20.dp))
                }
            )
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RefWellTile(model.glyph(), tint, 38.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Text(
                    model.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GsFonts.Product,
                    color = colors.textPrimary,
                )
                if (model == GsModel.PRO) {
                    Spacer(Modifier.width(7.dp))
                    RecommendedPill()
                }
            }
            Spacer(Modifier.height(2.dp))
            androidx.compose.material3.Text(
                model.description(),
                fontSize = 8.5.sp,
                lineHeight = 11.5.sp,
                fontFamily = GsFonts.Product,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                model.tags().take(3).forEach { ModelTag(it) }
            }
        }
        Spacer(Modifier.width(8.dp))
        ModelRadio(isSelected)
    }
}

@Composable
private fun RecommendedPill() {
    val colors = GsTheme.colors
    Box(
        Modifier
            .background(colors.accentSoft, RoundedCornerShape(999.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        androidx.compose.material3.Text(
            "Recommended",
            fontSize = 7.5.sp,
            fontFamily = GsFonts.Product,
            color = colors.accentStrong,
        )
    }
}

@Composable
private fun ModelTag(label: String) {
    val colors = GsTheme.colors
    Box(
        Modifier
            .background(Color.White.copy(alpha = 0.55f), RoundedCornerShape(7.dp))
            .border(1.dp, Color.White.copy(alpha = 0.60f), RoundedCornerShape(7.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        androidx.compose.material3.Text(
            label,
            fontSize = 7.5.sp,
            fontFamily = GsFonts.Product,
            color = colors.textSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun ModelRadio(isSelected: Boolean) {
    val colors = GsTheme.colors
    Box(
        Modifier
            .size(19.dp)
            .background(
                if (isSelected) colors.accent else Color.Transparent,
                CircleShape,
            )
            .border(
                1.4.dp,
                if (isSelected) Color.Transparent else colors.textTertiary.copy(alpha = 0.45f),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            androidx.compose.material3.Icon(
                GsIcons.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Reasoning effort + auto select
// ---------------------------------------------------------------------------

private data class EffortOption(val id: String, val label: String)

private val EFFORT_OPTIONS = listOf(
    EffortOption("minimal", "Minimal"),
    EffortOption("low", "Low"),
    EffortOption("standard", "Medium"),
    EffortOption("high", "High"),
)

@Composable
private fun ReasoningBlock(
    selectedEffort: String,
    autoSelect: Boolean,
    onEffort: (String) -> Unit,
    onAutoSelect: (Boolean) -> Unit,
) {
    val colors = GsTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(
            GsIcons.Sliders,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.Text(
            "Reasoning Effort",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = GsFonts.Product,
            color = colors.textPrimary,
        )
    }
    Spacer(Modifier.height(3.dp))
    androidx.compose.material3.Text(
        "Control how much time and thought the model spends on its response.",
        fontSize = 9.sp,
        lineHeight = 12.sp,
        fontFamily = GsFonts.Product,
        color = colors.textTertiary,
    )
    Spacer(Modifier.height(9.dp))
    EffortSegments(selectedEffort, onEffort)

    Spacer(Modifier.height(14.dp))
    RefCard(role = GsGlassRole.CardLight, shape = RoundedCornerShape(20.dp), padding = PaddingValues(0.dp)) {
        AutoSelectRow(
            checked = autoSelect,
            onCheckedChange = onAutoSelect,
        )
    }
}

@Composable
private fun EffortSegments(selected: String, onSelect: (String) -> Unit) {
    val colors = GsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
            .border(1.dp, Color.White.copy(alpha = 0.70f), RoundedCornerShape(999.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        EFFORT_OPTIONS.forEach { option ->
            val active = option.id == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .testTag("effort-${option.id}")
                    .background(
                        if (active) {
                            Brush.horizontalGradient(listOf(colors.accent.copy(alpha = 0.72f), colors.accent))
                        } else {
                            Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                        },
                        RoundedCornerShape(999.dp),
                    )
                    .selectable(
                        selected = active,
                        role = Role.Tab,
                        onClick = { onSelect(option.id) },
                    )
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Text(
                    option.label,
                    fontSize = 9.sp,
                    fontFamily = GsFonts.Product,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) Color.White else colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AutoSelectRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = GsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auto-select-row")
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RefWellTile(GsIcons.Sparkles, GsWellTone.Lilac, 34.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            androidx.compose.material3.Text(
                "Auto Select Model",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = GsFonts.Product,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(1.dp))
            androidx.compose.material3.Text(
                "Let Nexly choose the best model for your message.",
                fontSize = 8.sp,
                lineHeight = 10.5.sp,
                fontFamily = GsFonts.Product,
                color = colors.textTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = null,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accent,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = colors.border,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

/** The switch row is one switch target, not a row with a switch inside it. */
@Composable
private fun ContinueButton(onClick: () -> Unit) {
    val colors = GsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("model-continue")
            .background(
                Brush.horizontalGradient(
                    listOf(colors.accent.copy(alpha = 0.78f), colors.accent)
                ),
                RoundedCornerShape(999.dp),
            )
            .border(1.dp, Color.White.copy(alpha = 0.40f), RoundedCornerShape(999.dp))
            .clickable(role = Role.Button, onClickLabel = "Continue", onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(
            GsIcons.Sparkles,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(7.dp))
        androidx.compose.material3.Text(
            "Continue",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = GsFonts.Product,
            color = Color.White,
        )
        Spacer(Modifier.width(7.dp))
        androidx.compose.material3.Icon(
            GsIcons.ArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(13.dp),
        )
    }
}
