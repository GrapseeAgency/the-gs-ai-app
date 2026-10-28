package com.grapsee.gsai.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsAccentFamily
import com.grapsee.gsai.ui.theme.GsAccents
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.kineticPress

/**
 * REFERENCE UI KIT — the product's shared shell vocabulary.
 *
 * These are not "nice cards". They encode four decisions the reference makes
 * everywhere, and that a screen is not allowed to re-decide:
 *
 *   1. A page is a 20dp title + 12sp supporting line on the bloom, with one
 *      bare back chevron. No app bar, no elevation, no box around the title.
 *   2. Content sits in 22dp cream-glass cards with a 1dp warm hairline. A
 *      card is a group of related rows, never a single row.
 *   3. A row is 62dp: a 40dp pale icon tile, a 14sp title over an 11.5sp
 *      supporting line, and one trailing affordance (chevron, value, switch).
 *   4. Actions are either an orange fill (the page's single primary act) or a
 *      quiet glass row. There is no third button style.
 *
 * Every screen added in the reference pass composes from this file. That is
 * the architecture: screens own state and copy; the kit owns geometry, colour
 * and press behaviour. A new screen cannot accidentally invent a 16dp card, a
 * grey icon tile or a purple switch.
 */

// ---------------------------------------------------------------------------
// Page shell
// ---------------------------------------------------------------------------

/**
 * The reference page: back chevron, large title, supporting line, optional
 * trailing action, then a scrollable content column.
 */
@Composable
fun RefPage(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    largeTitle: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = GsTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                leading != null -> leading()
                onBack != null -> RefIconButton(GsIcons.ChevronLeft, "Back", onBack)
                else -> Spacer(Modifier.width(8.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (leading != null || onBack != null) 4.dp else 6.dp)
            ) {
                Text(
                    text = title,
                    style = if (largeTitle) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                        maxLines = 2
                    )
                }
            }
            actions()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            content = content
        )
    }
}

@Composable
fun RefIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = GsTheme.colors.accentStrong,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

// ---------------------------------------------------------------------------
// Glass surfaces
// ---------------------------------------------------------------------------

/**
 * The one card material: cream glass over the bloom, with the full four-cue
 * response (see GlassMaterial.kt). Callers pick a semantic [role]; they never
 * pick an alpha. Defaults to [GsGlassRole.Card] so every existing call site is
 * unchanged and simply gains the rim and the in-scatter it was missing.
 */
@Composable
fun RefCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    role: GsGlassRole = GsGlassRole.Card,
    padding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .gsGlass(role, shape)
            .padding(padding),
        content = content
    )
}

/** A titled card: the small section label lives above the glass. */
@Composable
fun RefSection(
    label: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (!label.isNullOrBlank()) {
            RefSectionLabel(label)
        }
        RefCard(content = content)
    }
}

@Composable
fun RefSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.1.sp
        ),
        color = GsTheme.colors.textSecondary,
        modifier = modifier
            .padding(start = 4.dp, top = 18.dp, bottom = 8.dp)
            .semantics { heading() }
    )
}

@Composable
fun RefDivider(startIndent: Dp = 68.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startIndent, end = 16.dp)
            .height(1.dp)
            .background(GsTheme.colors.divider)
    )
}

// ---------------------------------------------------------------------------
// Rows
// ---------------------------------------------------------------------------

/** A 40dp pale tile carrying the row's line icon. */
@Composable
fun RefIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = GsTheme.colors.accentStrong,
    size: Dp = 40.dp,
) {
    val colors = GsTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(13.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.72f), colors.accentSoft)
                )
            )
            .border(1.dp, colors.glassBorder, RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/**
 * The standard navigation/action row. [onClick] null renders a static row
 * (values, descriptions) with the same metrics.
 */
@Composable
fun RefRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String = "Open $title",
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = GsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .then(
                if (onClick != null) {
                    Modifier
                        .kineticPress()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClickLabel = onClickLabel,
                            onClick = onClick
                        )
                } else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) RefIconTile(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        } else if (onClick != null) {
            Icon(
                GsIcons.ChevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * The reference chip: a quiet glass pill when idle, an apricot fill with a
 * check when chosen. Chips are multi-select topics, never navigation.
 */
@Composable
fun RefChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // The Chat History mock fits six chips on one 393pt row; the reference
    // default is too wide for that, and other screens must not move.
    compact: Boolean = false,
    // The mock's selected chip is a soft peach wash with accent text, not the
    // full-strength pill used elsewhere. Kept opt-in for the same reason.
    softSelected: Boolean = false,
    trailingChevron: Boolean = false,
) {
    val colors = GsTheme.colors
    val lit = selected && softSelected
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                when {
                    lit -> colors.accentSoft
                    selected -> colors.accent
                    else -> colors.glassSurface.copy(alpha = 0.62f)
                }
            )
            .border(
                1.dp,
                when {
                    lit -> colors.accent.copy(alpha = 0.35f)
                    selected -> colors.accent
                    else -> colors.glassBorder
                },
                RoundedCornerShape(999.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Checkbox,
                onClickLabel = text,
                onClick = onClick
            )
            .padding(horizontal = if (compact) 9.dp else 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (selected && !softSelected) {
            Icon(
                GsIcons.Check,
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = when {
                lit -> colors.accent
                selected -> colors.onAccent
                else -> colors.textPrimary
            },
            maxLines = 1
        )
        if (trailingChevron) {
            Icon(
                GsIcons.ChevronDown,
                contentDescription = null,
                tint = if (selected) colors.accent else colors.textTertiary,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

/**
 * A selectable row with the reference's 40dp tile and a check on the right.
 * Used for capabilities, styles and any list where more than one answer is
 * possible but only one may be active.
 */
@Composable
fun RefChoiceRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GsTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .background(
                if (selected) colors.accentSoft.copy(alpha = 0.6f) else Color.Transparent
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Checkbox,
                onClickLabel = title,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RefIconTile(icon, tint = if (selected) colors.accentStrong else colors.textSecondary)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.textPrimary
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                    maxLines = 2
                )
            }
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) colors.accent else Color.Transparent)
                .border(1.5.dp, if (selected) colors.accent else colors.borderStrong, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    GsIcons.Check,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/** The one empty state: hand-drawn art, a 2-line promise, an optional action. */
@Composable
fun RefEmptyState(
    art: RefArt,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 26.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RefArtwork(art = art, size = 132.dp)
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = GsTheme.colors.textPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = GsTheme.colors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            RefPrimaryButton(
                label = actionLabel,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(0.72f)
            )
        }
    }
}

@Composable
fun RefValueRow(title: String, value: String, icon: ImageVector? = null) {
    RefRow(title = title, icon = icon) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = GsTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RefToggleRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val colors = GsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) RefIconTile(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.textPrimary
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                    maxLines = 2
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accent,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = colors.border,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Controls
// ---------------------------------------------------------------------------

/** Reference segmented control: a glass track with one lifted white pill. */
@Composable
fun RefSegmented(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GsTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(colors.accentSoft)
            .border(1.dp, colors.glassBorder, RoundedCornerShape(999.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            val active = option == selected
            val bg by animateColorAsState(
                targetValue = if (active) colors.elevatedSurface else Color.Transparent,
                label = "seg-bg"
            )
            val fg by animateColorAsState(
                targetValue = if (active) colors.accentStrong else colors.textSecondary,
                label = "seg-fg"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(bg)
                    .then(
                        if (active) Modifier.border(1.dp, colors.accent.copy(alpha = 0.26f), RoundedCornerShape(999.dp))
                        else Modifier
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                        onClickLabel = option,
                        onClick = { onSelect(option) }
                    )
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = fg,
                    maxLines = 1
                )
            }
        }
    }
}

/** The single primary action: apricot gradient, white label, soft glow. */
@Composable
fun RefPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = GsTheme.colors
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(
                if (enabled) Modifier.shadow(14.dp, shape, ambientColor = colors.accent.copy(alpha = 0.35f), spotColor = colors.accent.copy(alpha = 0.30f))
                else Modifier
            )
            .clip(shape)
            .background(
                if (enabled) Brush.horizontalGradient(listOf(colors.accentStrong, colors.accent))
                else Brush.horizontalGradient(listOf(colors.raisedSurface, colors.raisedSurface))
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) colors.onAccent else colors.textTertiary
        )
    }
}

@Composable
fun RefQuietButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GsTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .gsGlass(GsGlassRole.FloatingControl, RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
    }
}

/**
 * A selectable option card (model tier, response style, reasoning effort).
 * The selected state is a warm border plus a check — never a fill that
 * swallows the text.
 */
@Composable
fun RefOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    badge: String? = null,
    detail: String? = null,
) {
    val colors = GsTheme.colors
    val shape = RoundedCornerShape(20.dp)
    val border by animateColorAsState(
        if (selected) colors.accent.copy(alpha = 0.55f) else colors.glassBorder,
        label = "option-border"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) colors.accentSoft.copy(alpha = 0.65f) else colors.glassSurface.copy(alpha = 0.62f)
            )
            .border(if (selected) 1.5.dp else 1.dp, border, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClickLabel = title,
                onClick = onClick
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) RefIconTile(icon, tint = if (selected) colors.accentStrong else colors.textSecondary)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary
                )
                if (!badge.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.accentSoft)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(badge, style = MaterialTheme.typography.labelSmall, color = colors.accentStrong)
                    }
                }
            }
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            }
            if (!detail.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(detail, style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
            }
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) colors.accent else Color.Transparent)
                .border(1.5.dp, if (selected) colors.accent else colors.borderStrong, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(GsIcons.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(13.dp))
            }
        }
    }
}

/**
 * A reference "choice tile": icon, title, one supporting line, check when
 * selected. The reference uses this 3-up for reasoning effort and 2-up for
 * model source — a row of tall tiles reads faster than a stack of rows when
 * the options are few and mutually exclusive.
 */
@Composable
fun RefChoiceTile(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = GsTheme.colors
    val shape = RoundedCornerShape(18.dp)
    val border by animateColorAsState(
        if (selected) colors.accent.copy(alpha = 0.60f) else colors.glassBorder,
        label = "choice-tile-border"
    )
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                // The token is ALREADY a low-alpha tint (0x1F). Overriding its
                // alpha here turned the selected tile into a solid orange slab;
                // the reference is a whisper of colour with an accent border.
                if (selected) colors.accentSoft else colors.glassSurface.copy(alpha = 0.55f)
            )
            .border(1.dp, border, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = title,
                onClick = onClick
            )
            .semantics { this.selected = selected }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) colors.accentStrong else colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            if (selected) {
                Icon(
                    GsIcons.Check,
                    contentDescription = null,
                    tint = colors.accentStrong,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------
// Identity and data decoration
// ---------------------------------------------------------------------------

/** Profile avatar: warm gradient disc with the user's real initials. */
@Composable
fun RefAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    onClick: (() -> Unit)? = null,
) {
    val colors = GsTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(colors.aurora.first(), colors.accent, colors.accentStrong)))
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                ) else Modifier
            )
            .border(2.dp, Color.White.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = (size.value * 0.32f).sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = Color.White
        )
    }
}

/** Accent swatch row used by Appearance and Theme customisation. */
@Composable
fun RefAccentRow(
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GsTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GsAccents.all.forEach { family: GsAccentFamily ->
            val selected = family.id == selectedId
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(family.base)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) colors.textPrimary else Color.White.copy(alpha = 0.7f),
                        shape = CircleShape
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.RadioButton,
                        onClickLabel = family.label,
                        onClick = { onSelect(family.id) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(GsIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/** A 92dp progress ring with the value in the middle. */
@Composable
fun RefRing(
    progress: Float,
    label: String,
    caption: String,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    val colors = GsTheme.colors
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.toPx() * 0.085f
            val inset = stroke / 2f
            drawArc(
                color = colors.accentSoft,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(colors.accentStrong, colors.accent, colors.aurora.first(), colors.accentStrong)),
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
        }
    }
}

/** Compact metric tile used by the usage and billing surfaces. */
@Composable
fun RefStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = GsTheme.colors
    Column(
        modifier = modifier
            .gsGlass(GsGlassRole.Card, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.accentStrong, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(8.dp))
        }
        Text(value, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
    }
}

/** A soft status pill: green for available, amber for limited, clay for off. */
@Composable
fun RefStatusPill(text: String, tone: Color = GsTheme.colors.success) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tone.copy(alpha = 0.13f))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = tone)
    }
}

/**
 * Reduced-motion gate reused by kit animations: when the user stands still,
 * values snap rather than easing.
 */
@Composable
fun refMotion(): Boolean = !(SettingsStore.reduceAnimations || SettingsStore.reduceMotion) && !GsMotion.reduced

/** A one-line footnote in the reference's quiet register. */
@Composable
fun RefFootnote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = GsTheme.colors.textTertiary,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 10.dp)
    )
}

/**
 * The reference text field.
 *
 * Labels sit *above* the field, never floating inside it: a floating label is
 * either a second line of content competing with the value, or — the bug this
 * replaces — an unreadable overlapping glyph. Every colour is bound to a
 * semantic role, so a value can never be painted in the container's own ink.
 */
@Composable
fun RefTextField(
    value: String,
    onValueChange: (String) -> Unit,
    // Nullable because a reference field may carry only a placeholder
    // (Chat History & Search's field does). An empty label used to still draw
    // its line plus 6dp of padding.
    label: String? = null,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supporting: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isPassword: Boolean = false,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    val colors = GsTheme.colors
    var revealed by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val border = if (error != null) colors.error else colors.glassBorder
    Column(modifier = modifier.fillMaxWidth()) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.textSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (focusRequester != null) Modifier.focusRequester(focusRequester)
                    else Modifier
                ),
            enabled = enabled,
            shape = shape,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
            placeholder = placeholder?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPlaceholder
                    )
                }
            },
            leadingIcon = leadingIcon?.let {
                {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            },
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { revealed = !revealed }) {
                        Icon(
                            imageVector = if (revealed) GsIcons.EyeOff else GsIcons.Eye,
                            contentDescription = if (revealed) "Hide password" else "Show password",
                            tint = colors.textTertiary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            } else null,
            visualTransformation = if (isPassword && !revealed) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                disabledTextColor = colors.textDisabled,
                cursorColor = colors.accentStrong,
                focusedBorderColor = colors.accent.copy(alpha = 0.75f),
                unfocusedBorderColor = border,
                disabledBorderColor = colors.divider,
                errorBorderColor = colors.error,
                focusedContainerColor = colors.inputSurface,
                unfocusedContainerColor = colors.inputSurface.copy(alpha = 0.72f),
                disabledContainerColor = colors.secondaryBackground,
                errorContainerColor = colors.inputSurface
            )
        )
        val hint = error ?: supporting
        if (!hint.isNullOrBlank()) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = if (error != null) colors.error else colors.textTertiary,
                modifier = Modifier.padding(start = 4.dp, top = 5.dp)
            )
        }
    }
}
