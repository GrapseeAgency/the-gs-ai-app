package com.grapsee.gsai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.components.RefAccentRow
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefFootnote
import com.grapsee.gsai.ui.components.RefPage
import com.grapsee.gsai.ui.components.RefRow
import com.grapsee.gsai.ui.components.RefSection
import com.grapsee.gsai.ui.components.RefSegmented
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsTheme

/**
 * APPEARANCE — the page the reference gives the most air to, because it is
 * the one the user revisits. Three real axes: light/dark, accent family, and
 * type size. Every control writes through SettingsStore, so the preview at
 * the top of the page *is* the app, not a mock.
 */
@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val colors = GsTheme.colors
    val theme = SettingsStore.themeMode
    val accent = SettingsStore.accentId
    val scale = SettingsStore.fontScale

    RefPage(
        title = "Appearance",
        subtitle = "Theme, display and style.",
        onBack = onBack
    ) {
        // Live sample: the type is read from the real theme, so changing the
        // slider below visibly rescales this sentence.
        RefCard(padding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.accentSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Aa", style = MaterialTheme.typography.titleMedium, color = colors.accentStrong)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "A calmer way to think.",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary
                    )
                    Text(
                        "This is the real type scale, updating live.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            }
        }

        RefSection(label = "Theme") {
            Row(
                Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThemeTile(
                    label = "Light",
                    icon = GsIcons.Lightbulb,
                    selected = theme == "Light",
                    onClick = { SettingsStore.updateThemeMode("Light") },
                    modifier = Modifier.weight(1f)
                )
                ThemeTile(
                    label = "Dark",
                    icon = GsIcons.Eye,
                    selected = theme == "Dark",
                    onClick = { SettingsStore.updateThemeMode("Dark") },
                    modifier = Modifier.weight(1f)
                )
                ThemeTile(
                    label = "System",
                    icon = GsIcons.Smartphone,
                    selected = theme == "System",
                    onClick = { SettingsStore.updateThemeMode("System") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        RefSectionLabelText("Accent colour")
        RefCard(padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            RefAccentRow(
                selectedId = accent,
                onSelect = { SettingsStore.updateAccentId(it) }
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Accent is used for actions, focus, links and the selected state across the app.",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary
            )
        }

        RefSectionLabelText("Text size")
        RefCard(padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Aa", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                Slider(
                    value = scale,
                    onValueChange = { SettingsStore.stageFontScale(it) },
                    onValueChangeFinished = { SettingsStore.updateFontScale(scale) },
                    valueRange = 0.85f..1.30f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.accent,
                        activeTrackColor = colors.accent,
                        inactiveTrackColor = colors.accentSoft
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                )
                Text("Aa", style = MaterialTheme.typography.titleMedium, color = colors.textSecondary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Small", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                Text(
                    when {
                        scale < 0.93f -> "Small"
                        scale < 1.06f -> "Default"
                        scale < 1.20f -> "Large"
                        else -> "Largest"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accentStrong
                )
                Text("Largest", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
            }
        }

        RefSection(label = "Background") {
            Column(Modifier.padding(14.dp)) {
                RefSegmented(
                    options = listOf("Blurred", "Gradient", "Solid"),
                    selected = SettingsStore.backgroundStyle,
                    onSelect = { SettingsStore.updateBackgroundStyle(it) }
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Blurred paints the full blossom artwork, Gradient keeps the light only, Solid uses flat paper.",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )
            }
            RefDividerLine()
            RefRow(
                title = "Theme customisation",
                subtitle = "Preview the bloom and tune the surface",
                icon = GsIcons.Palette,
                onClick = { onNavigate(GsRoutes.SETTINGS_THEME) }
            )
        }

        RefFootnote("Type is set in Poppins. The scale is applied to the entire app, not just this page.")
    }
}

@Composable
internal fun ThemeTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GsTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) colors.accentSoft else colors.glassSurface.copy(alpha = 0.5f))
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) colors.accent.copy(alpha = 0.55f) else colors.glassBorder,
                RoundedCornerShape(18.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClickLabel = label,
                onClick = onClick
            )
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) colors.accentStrong else colors.textSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = if (selected) colors.accentStrong else colors.textSecondary
        )
    }
}

@Composable
internal fun RefSectionLabelText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = GsTheme.colors.textSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp)
    )
}

@Composable
internal fun RefDividerLine() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 68.dp, end = 16.dp)
            .height(1.dp)
            .background(GsTheme.colors.divider)
    )
}
