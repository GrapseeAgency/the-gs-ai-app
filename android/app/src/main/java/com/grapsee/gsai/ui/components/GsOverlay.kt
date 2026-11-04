package com.grapsee.gsai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.LocalGsIsDark
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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

// ---------------------------------------------------------------------------
// The anchored popover — the second kind of overlay
// ---------------------------------------------------------------------------

/**
 * A MENU THAT GROWS OUT OF ITS BUTTON.
 *
 * The panel above is right for a sheet of content. It is wrong for a menu, and
 * the reference the owner kept sending says so: Kimi's attach menu is a narrow
 * list that sits in the bottom-left corner, level with the "+" it came from,
 * and its options menu is a narrow list under the three dots in the header.
 * Neither is centred, neither spans the width, neither has a title or a drag
 * handle — a menu is an appendage of one control, and it should look like one.
 *
 * So this is:
 *
 *  - **narrow** — [width] is a real width, not a fraction. 248dp for the attach
 *    list, 196dp for the conversation menu;
 *  - **anchored** — [alignment] is the corner it belongs to, and it is inset
 *    [anchorInset] from that corner so it clears the control it hangs off;
 *  - **not modal-looking** — a 12% scrim, because a menu does not black out the
 *    page it is pointing at, it just says "tap elsewhere to close";
 *  - **arriving from the anchor** — it scales from 0.94 with its transform
 *    origin on that corner, so the growth starts at the button.
 */
@Composable
fun GsAnchoredPopover(
    alignment: Alignment,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 248.dp,
    /** Clearance from the side it is anchored to. */
    horizontalInset: Dp = 14.dp,
    /** Clearance from the top or bottom — the header, or the composer. */
    verticalInset: Dp = 14.dp,
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
            label = "popoverProgress"
        )
        // Transform origin on the anchor corner: the panel grows OUT of the
        // button, it does not fade in on top of the page.
        val originX = when (alignment) {
            Alignment.BottomStart, Alignment.TopStart, Alignment.CenterStart -> 0f
            Alignment.BottomEnd, Alignment.TopEnd, Alignment.CenterEnd -> 1f
            else -> 0.5f
        }
        val originY = when (alignment) {
            Alignment.TopStart, Alignment.TopCenter, Alignment.TopEnd -> 0f
            Alignment.BottomStart, Alignment.BottomCenter, Alignment.BottomEnd -> 1f
            else -> 0.5f
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.12f * progress))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Dismiss",
                    onClick = onDismiss
                )
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = alignment
        ) {
            val shape = RoundedCornerShape(22.dp)
            val colors = GsTheme.colors
            val dark = LocalGsIsDark.current
            Column(
                modifier = modifier
                    .padding(horizontal = horizontalInset, vertical = verticalInset)
                    .width(width)
                    .graphicsLayer {
                        alpha = progress
                        scaleX = 0.94f + 0.06f * progress
                        scaleY = 0.94f + 0.06f * progress
                        transformOrigin = TransformOrigin(originX, originY)
                    }
                    // A menu has to be legible over ANY content, so it is the one
                    // surface in the app that is nearly opaque: the dialog token
                    // under the glass, plus a real shadow. Pure glass was pretty
                    // and unreadable — the probe showed rows floating on pearl.
                    .shadow(
                        elevation = 22.dp,
                        shape = shape,
                        ambientColor = Color.Black.copy(alpha = 0.22f),
                        spotColor = Color.Black.copy(alpha = 0.28f),
                    )
                    .background(colors.dialogSurface.copy(alpha = if (dark) 0.92f else 0.88f), shape)
                    .gsGlass(GsGlassRole.Modal, shape)
                    // One hairline so the panel has an EDGE on a pearl page —
                    // the glass rim is white-on-white there and the shadow alone
                    // leaves the menu looking like a smudge.
                    .border(0.8.dp, colors.glassBorder.copy(alpha = if (dark) 0.55f else 0.95f), shape)
                    // Swallow taps on the panel itself so a tap on its padding
                    // does not fall through to the scrim.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(vertical = 6.dp),
                content = content
            )
        }
    }
}

/**
 * One line of a menu: a glyph and a label, nothing else.
 *
 * The reference rows are 54dp tall with a 38dp circular well on the left — the
 * well is what makes a list read as "things I can pick" rather than "settings",
 * so [well] is on by default and the conversation menu turns it off (its rows
 * are tighter and its glyphs are bare).
 */
@Composable
fun GsPopoverRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    enabled: Boolean = true,
    well: Boolean = true,
    /** A second line, only for the two-step confirmations. */
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = GsTheme.colors
    val dark = LocalGsIsDark.current
    val ink = when {
        destructive -> colors.error
        enabled -> colors.textPrimary
        else -> colors.textTertiary
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = if (subtitle == null) 0.dp else 6.dp)
            .heightIn(min = if (well) 54.dp else 46.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (well) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (destructive) colors.error.copy(alpha = 0.14f)
                        else colors.textPrimary.copy(alpha = if (dark) 0.10f else 0.055f)
                    )
                    .border(
                        0.7.dp,
                        if (destructive) colors.error.copy(alpha = 0.28f)
                        else colors.glassBorder.copy(alpha = if (dark) 0.45f else 0.75f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(19.dp))
            }
        } else {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 14.5.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = GsFonts.Product,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    fontFamily = GsFonts.Product,
                    color = GsTheme.colors.textTertiary,
                    maxLines = 2
                )
            }
        }
        trailing?.invoke()
    }
}

/** The hairline that separates a menu's groups without drawing a box. */
@Composable
fun GsPopoverRule(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .height(1.dp)
            .background(GsTheme.colors.glassBorder.copy(alpha = 0.6f))
    )
}
