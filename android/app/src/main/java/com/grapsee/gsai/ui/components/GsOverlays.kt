package com.grapsee.gsai.ui.components

import androidx.compose.material3.SnackbarHostState

import androidx.compose.material3.SnackbarHost

import androidx.compose.material3.Snackbar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
    GsModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
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



/** Native popup positioning, keyboard dismissal, focus and scroll behavior
 * remain Material-owned; the surface itself is our shared glass. */
@Composable
fun GsDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.gsOverlayGlass(shape),
        shape = shape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        content = content,
    )
}

/** Keep native drag, detents, system insets and dismissal. Only replace the
 * opaque Material paint, not the sheet's window or interaction machinery. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GsModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    shape: Shape = RoundedCornerShape(topStart = GsRadius.sheet, topEnd = GsRadius.sheet),
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.gsOverlayGlass(shape),
        sheetState = sheetState,
        shape = shape,
        containerColor = Color.Transparent,
        contentColor = GsTheme.colors.textPrimary,
        tonalElevation = 0.dp,
        dragHandle = dragHandle,
        content = content,
    )
}

/** Retain the native snackbar lifecycle, accessibility announcement, action
 * and dismissal; substitute only the transient surface's paint. */
@Composable
fun GsSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    val colors = GsTheme.colors
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            modifier = Modifier.gsOverlayGlass(shape),
            shape = shape,
            containerColor = Color.Transparent,
            contentColor = colors.textPrimary,
            actionColor = colors.accentStrong,
            dismissActionContentColor = colors.textSecondary,
        )
    }
}
