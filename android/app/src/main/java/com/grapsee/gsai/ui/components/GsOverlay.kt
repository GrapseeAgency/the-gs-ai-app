package com.grapsee.gsai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.GsTheme

/**
 * THE OVERLAY — a floating glass panel, not a full-screen sheet.
 *
 * What shipped before was `ModalBottomSheet` at full height: a cream rectangle
 * that began at the status bar and ran to the navigation bar, with Material
 * rows inside it. On a screen whose entire identity is light through frosted
 * glass, that reads as a different application laid on top of this one — the
 * exact "amader app er sathe oi overlay gular kono mil nei" complaint.
 *
 * An overlay is a small object that belongs to the thing it came from. So this
 * is:
 *
 *  - **sized to its content**, capped at [maxHeightFraction] of the window,
 *    never stretched to fill it;
 *  - **inset** — 14dp off each side and the bottom, so the page stays visible
 *    around it and the reader keeps their place;
 *  - **the same glass** as every card on the page ([GsGlassRole.Modal], the
 *    thickest fill, for the lift a surface over content needs), with the
 *    material's own lit rim;
 *  - **anchored low but not flush** — it rises 26dp on the snappy spring and
 *    fades in, so it arrives rather than appearing.
 *
 * The scrim is 26% at most: enough to separate, not enough to black out a page
 * the reader is still reading.
 */
@Composable
fun GsFloatingOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Share of the window height the panel may use before it scrolls. */
    maxHeightFraction: Float = 0.78f,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val progress by animateFloatAsState(
            targetValue = if (shown) 1f else 0f,
            animationSpec = GsMotion.snappy(),
            label = "overlayProgress"
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.26f * progress))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Dismiss",
                    onClick = onDismiss
                )
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.BottomCenter
        ) {
            val cap = maxHeight * maxHeightFraction
            GsOverlayPanel(
                modifier = modifier
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 14.dp)
                    .heightIn(max = cap)
                    .graphicsLayer {
                        alpha = progress
                        translationY = (1f - progress) * 44.dp.toPx()
                    },
                content = content
            )
        }
    }
}

/**
 * The panel itself, without the dialog and the scrim — so it can be rendered
 * on its own (the render probe does exactly that) and so both overlays are
 * guaranteed to be the same object.
 */
@Composable
fun GsOverlayPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .gsGlass(GsGlassRole.Modal, RoundedCornerShape(26.dp))
            // Swallow taps on the panel itself, so a tap that lands on padding
            // does not fall through to the scrim and dismiss.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 10.dp)
                .align(Alignment.CenterHorizontally)
                .background(
                    GsTheme.colors.textTertiary.copy(alpha = 0.35f),
                    RoundedCornerShape(99.dp)
                )
                .padding(horizontal = 16.dp, vertical = 1.5.dp)
        ) {}
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content
        )
    }
}

/**
 * A section caption inside an overlay — the quiet line that separates one
 * group of rows from the next without drawing a rule.
 */
@Composable
fun OverlaySectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
        color = GsTheme.colors.textTertiary,
        modifier = modifier.padding(start = 4.dp, top = 10.dp, bottom = 4.dp)
    )
}
