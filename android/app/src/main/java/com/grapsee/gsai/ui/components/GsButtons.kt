package com.grapsee.gsai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.GsRadius
import com.grapsee.gsai.ui.theme.GsSpacing
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.kineticPress

/**
 * AERUO KINETIC — the button hierarchy.
 *
 * One dominant action (Primary) → supporting actions (Secondary) → contextual
 * actions (Tertiary). Destructive is reserved for irreversible operations.
 * Pills are NOT used here: buttons are structured controls with GsRadius.md.
 */

enum class GsButtonVariant { Primary, Secondary, Tertiary, Destructive }

@Composable
fun GsButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: GsButtonVariant = GsButtonVariant.Primary,
    enabled: Boolean = true,
    compact: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val colors = GsTheme.colors
    val (container, content, borderStroke) = when (variant) {
        GsButtonVariant.Primary ->
            Triple(colors.accent, colors.onAccent, null)
        GsButtonVariant.Secondary ->
            Triple(colors.raisedSurface, colors.textPrimary, BorderStroke(1.dp, colors.border))
        GsButtonVariant.Tertiary ->
            Triple(colors.appBackground.copy(alpha = 0f), colors.accent, null)
        GsButtonVariant.Destructive ->
            Triple(colors.error, colors.onError, null)
    }
    val press = if (GsMotion.reduced || !enabled) Modifier else Modifier.kineticPress()
    Surface(
        modifier = modifier
            .then(press)
            .alpha(if (enabled) 1f else 0.45f)
            .semantics { contentDescription = label },
        shape = GsRadius.mdShape(),
        color = container,
        contentColor = content,
        border = borderStroke,
        onClick = onClick,
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) GsSpacing.control else GsSpacing.m,
                vertical = if (compact) GsSpacing.xs + 2.dp else GsSpacing.s + 4.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 16.dp else 18.dp),
                )
                Spacer(Modifier.width(GsSpacing.s))
            }
            Text(
                text = label,
                style = GsTheme.textStyles.button,
            )
        }
    }
}

/** Circular 44dp icon button — secondary by default (supports the touch-target rule). */
@Composable
fun GsIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: GsButtonVariant = GsButtonVariant.Secondary,
    enabled: Boolean = true,
    iconTint: androidx.compose.ui.graphics.Color? = null,
) {
    val colors = GsTheme.colors
    val (container, content, borderStroke) = when (variant) {
        GsButtonVariant.Primary ->
            Triple(colors.accent, colors.onAccent, null)
        GsButtonVariant.Secondary ->
            Triple(colors.raisedSurface, colors.textPrimary, BorderStroke(1.dp, colors.border))
        GsButtonVariant.Tertiary ->
            Triple(colors.appBackground.copy(alpha = 0f), colors.textSecondary, null)
        GsButtonVariant.Destructive ->
            Triple(colors.error, colors.onError, null)
    }
    val tint = iconTint ?: content
    Surface(
        modifier = modifier
            .size(44.dp)
            .then(if (GsMotion.reduced || !enabled) Modifier else Modifier.kineticPress())
            .alpha(if (enabled) 1f else 0.45f),
        shape = CircleShape,
        color = container,
        border = borderStroke,
        onClick = onClick,
        enabled = enabled,
    ) {
        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}
