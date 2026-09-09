package com.grapsee.gsai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.theme.GsRadius
import com.grapsee.gsai.ui.theme.GsSpacing
import com.grapsee.gsai.ui.theme.GsTheme

/**
 * AERUO KINETIC — the one shared sheet/dialog language.
 *
 * Every bottom sheet, confirmation and destructive dialog in the app presents
 * through these primitives: same surface hierarchy, radius, spacing, type and
 * button treatment. Feature screens must not invent their own sheet styling.
 */

/** The shared bottom-sheet shell (handle bar + optional title + content). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GsSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = GsTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = GsRadius.sheet, topEnd = GsRadius.sheet),
        containerColor = colors.sheetSurface,
        contentColor = colors.textPrimary,
        tonalElevation = 0.dp,
        dragHandle = {
            Surface(
                color = colors.borderStrong,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .padding(top = GsSpacing.s)
                    .width(36.dp)
                    .height(4.dp),
            ) {}
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GsSpacing.l)
                .padding(bottom = GsSpacing.l),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(bottom = GsSpacing.m),
                )
            }
            content()
        }
    }
}

/**
 * The shared confirmation dialog. `destructive = true` renders the confirm
 * action in the error role — the only sanctioned treatment for irreversible
 * operations.
 */
@Composable
fun GsConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    dismissLabel: String = "Cancel",
) {
    val colors = GsTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(GsRadius.sheet),
        containerColor = colors.dialogSurface,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        tonalElevation = 0.dp,
        title = { Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall) },
        text = { Text(message, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(
                    confirmLabel,
                    color = if (destructive) colors.error else colors.accent,
                    style = GsTheme.textStyles.button,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, color = colors.textSecondary, style = GsTheme.textStyles.button)
            }
        },
    )
}

/** Compact dropdown-menu surface token wrapper (context menus). */
@Composable
fun GsMenuSurface(content: @Composable () -> Unit) {
    Surface(
        shape = GsRadius.mdShape(),
        color = GsTheme.colors.elevatedSurface,
        tonalElevation = 3.dp,
        content = content,
    )
}

