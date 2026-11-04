package com.grapsee.gsai.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.LocalGsIsDark
import kotlin.math.roundToInt

/*
 * THE TWO CONTROLS THAT WERE STILL STOCK.
 *
 * Everything around them is glass, so the two pieces of chrome a reader touches
 * most often were also the two that gave the illusion away: an M3 `Switch` with
 * a flat track and a white dot, and an M3 `Slider` with a hairline rail, a
 * circular thumb and tick marks. Both are correct Material and both are the
 * wrong material for this app — they read as a form from 2016 sitting inside a
 * card made of light.
 *
 * The rules the rest of the app already follows, applied here:
 *
 *  - a control is a piece of GLASS, not a rectangle of colour: it has a lit top
 *    edge, an interior fall, and a rim that is brighter than its fill;
 *  - STATE IS LIGHT, not position. Off is glass seen from the shadowed side
 *    (`Sunken`); on is the same glass lit from inside by the accent, and the
 *    light spills past the shape as a halo. The knob moves, but the glow is what
 *    tells you the answer at a glance;
 *  - the accent is the reader's own (it is a setting), so nothing here
 *    hard-codes a hue.
 */

/** A capsule, expressed once so every corner in this file agrees. */
private val PillShape = RoundedCornerShape(percent = 50)

/** The rung's corner: round enough to be glass, square enough to be a step. */
private val RungShape = RoundedCornerShape(9.dp)

// ---------------------------------------------------------------------------
// The switch
// ---------------------------------------------------------------------------

/**
 * The app's on/off control, in glass.
 *
 * Off: a sunken capsule — `glassHighlight` at low alpha over the card, a dark
 * hairline, and a frosted knob resting at the left. On: the capsule fills with
 * the accent (lit at the top, deeper at the bottom), gains a white rim, throws a
 * coloured halo onto the card behind it, and the knob travels right with a
 * spring and carries a lit core.
 *
 * [onCheckedChange] is nullable so a row can own the touch target (the M3
 * convention: pass `null` and let the parent `toggleable` drive), which is what
 * [RefToggleRow] does.
 */
@Composable
fun GsGlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = GsTheme.colors
    val dark = LocalGsIsDark.current

    val trackWidth = 48.dp
    val trackHeight = 28.dp
    val knobSize = 22.dp
    val inset = 3.dp

    val travel by animateDpAsState(
        targetValue = if (checked) trackWidth - knobSize - inset else inset,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 640f),
        label = "gs-switch-travel",
    )
    val coreAlpha by animateColorAsState(
        targetValue = colors.accent.copy(alpha = if (checked) 0.9f else 0f),
        label = "gs-switch-core",
    )

    // Off is glass in shadow. The old fill was `glassHighlight` — near-white —
    // which on the cream page made the track a white pill that vanished into the
    // card, leaving an empty outline and a knob with no ground under it. A well
    // is the card's own ink pressed down, so that is what this is: the text
    // token at low alpha, deeper in light where the page is bright.
    val offInk = colors.textPrimary.copy(alpha = if (dark) 0.15f else 0.085f)
    val offFill = Brush.verticalGradient(
        listOf(offInk.copy(alpha = offInk.alpha * 0.72f), offInk)
    )
    val onFill = Brush.verticalGradient(listOf(colors.accentStrong, colors.accent))

    Box(
        modifier = modifier
            .size(trackWidth, trackHeight)
            .then(
                if (checked) {
                    // The halo. A coloured shadow is the cheapest honest glow:
                    // it is light leaving the control, not a second rectangle.
                    Modifier.shadow(
                        elevation = 9.dp,
                        shape = PillShape,
                        ambientColor = colors.accent.copy(alpha = 0.55f),
                        spotColor = colors.accent.copy(alpha = 0.65f),
                    )
                } else {
                    Modifier
                }
            )
            .clip(PillShape)
            .background(if (checked) onFill else offFill)
            .border(
                width = 1.dp,
                color = if (checked) {
                    Color.White.copy(alpha = 0.44f)
                } else {
                    // The rim does the work when the fill cannot: a hairline of
                    // the page's own warm border, doubled in light so the well
                    // still has an edge against cream.
                    colors.border.copy(alpha = if (dark) 0.60f else 0.95f)
                },
                shape = PillShape,
            )
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = onCheckedChange,
                    )
                } else {
                    Modifier
                }
            )
            .alpha(if (enabled) 1f else 0.45f),
    ) {
        // The specular band along the top edge — the same light the cards get.
        Box(
            Modifier
                .matchParentSize()
                .clip(PillShape)
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = if (checked) 0.30f else 0.26f),
                        0.58f to Color.Transparent,
                    )
                )
        )
        // The knob: a frosted sphere with its own rim and a lit core.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = travel)
                .size(knobSize)
                .shadow(
                    elevation = 4.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.18f),
                    spotColor = Color.Black.copy(alpha = 0.26f),
                )
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            Color.White.copy(alpha = 0.82f),
                        ),
                        center = Offset.Unspecified,
                    ),
                    CircleShape,
                )
                .border(
                    width = 0.8.dp,
                    // Lit track → a white rim reads as the edge of the sphere.
                    // Sunken track → white on white reads as nothing, so the rim
                    // borrows the page's warm border instead.
                    color = if (checked) {
                        Color.White.copy(alpha = 0.90f)
                    } else {
                        colors.border.copy(alpha = if (dark) 0.45f else 0.70f)
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(coreAlpha, CircleShape)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// The text-size ladder
// ---------------------------------------------------------------------------

/**
 * Text size as a ladder of light rather than a rail with a dot on it.
 *
 * Ten glass rungs grow from left to right — the shape IS the sentence "small to
 * big", so no tick marks and no labels are needed to explain the axis. The rungs
 * the reader has already climbed are washed with `accentSoft`; the rung they are
 * standing on is lit with the accent and throws a halo; the ones ahead stay
 * clear glass. The current value is a glass chip in the header, so the number
 * moves with the light instead of sitting in a corner.
 *
 * Tap anywhere or drag across: both land on the nearest rung, and only the
 * release writes to storage ([onValueChangeFinished]) so a drag does not hammer
 * the prefs file.
 */
@Composable
fun GsTextSizeControl(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0.85f..1.30f,
    steps: Int = 8,
    title: String = "Text size",
    enabled: Boolean = true,
) {
    val colors = GsTheme.colors
    val dark = LocalGsIsDark.current
    val rungs = steps + 2
    val span = valueRange.endInclusive - valueRange.start
    val progress = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val activeIndex = (progress * (rungs - 1)).roundToInt().coerceIn(0, rungs - 1)

    fun valueAt(index: Int): Float =
        valueRange.start + span * (index.toFloat() / (rungs - 1))

    var trackWidth by remember { mutableFloatStateOf(1f) }

    Column(modifier = modifier.fillMaxWidth().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            // The readout is a glass chip, not a coloured label: it belongs to
            // the same material as the control it reports on.
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(colors.accent.copy(alpha = 0.12f))
                    .border(1.dp, colors.accent.copy(alpha = 0.34f), PillShape)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "${(value * 100).roundToInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GsFonts.Product,
                    color = colors.accentStrong,
                )
            }
        }

        // The well the ladder stands in — sunken glass, so the rungs sit BELOW
        // the card and the lit one reads as light coming up out of it.
        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .gsGlass(GsGlassRole.Sunken, RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .onSizeChanged { trackWidth = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(rungs, enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val picked = ((down.position.x / trackWidth) * rungs)
                            .toInt().coerceIn(0, rungs - 1)
                        onValueChange(valueAt(picked))
                        drag(down.id) { change ->
                            val index = ((change.position.x / trackWidth) * rungs)
                                .toInt().coerceIn(0, rungs - 1)
                            if (index != picked) onValueChange(valueAt(index))
                            change.consume()
                        }
                        onValueChangeFinished()
                    }
                }
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange, steps)
                    setProgress { target ->
                        val snapped = ((target - valueRange.start) / span * (rungs - 1))
                            .roundToInt().coerceIn(0, rungs - 1)
                        onValueChange(valueAt(snapped))
                        onValueChangeFinished()
                        true
                    }
                },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(46.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(rungs) { i ->
                    val isActive = i == activeIndex
                    val isBehind = i < activeIndex
                    // 14 → 46dp: at 34dp the rungs were wider than they were
                    // tall and read as a row of lozenges rather than a climb.
                    // The tile is a rounded rectangle, not a pill, for the same
                    // reason: a pill has no "step" in it.
                    val height = lerp(13.dp, 42.dp, i.toFloat() / (rungs - 1))
                    val wash by animateColorAsState(
                        targetValue = when {
                            isActive -> colors.accent
                            isBehind -> colors.accentSoft
                            // Ahead: clear glass, drawn with the page's ink so it
                            // is visible on cream as well as on charcoal.
                            // Warm, not grey: the page is cream, and a neutral
                            // grey tile beside it looks like a hole in the glass.
                            else -> colors.border.copy(alpha = if (dark) 0.30f else 0.45f)
                        },
                        label = "gs-rung-wash",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(height)
                            .then(
                                if (isActive) {
                                    Modifier.shadow(
                                        elevation = 8.dp,
                                        shape = RungShape,
                                        ambientColor = colors.accent.copy(alpha = 0.45f),
                                        spotColor = colors.accent.copy(alpha = 0.55f),
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clip(RungShape)
                            .background(
                                if (isActive) {
                                    Brush.verticalGradient(listOf(colors.accentStrong, colors.accent))
                                } else {
                                    Brush.verticalGradient(listOf(wash, wash.copy(alpha = wash.alpha * 0.72f)))
                                }
                            )
                            .border(
                                width = if (isActive) 1.dp else 0.5.dp,
                                color = if (isActive) {
                                    Color.White.copy(alpha = 0.36f)
                                } else {
                                    colors.glassBorder.copy(alpha = if (dark) 0.45f else 0.75f)
                                },
                                shape = RungShape,
                            )
                    )
                }
            }
        }

        // The axis, spelled with type rather than with ticks.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Aa",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = GsFonts.Product,
                color = colors.textTertiary,
            )
            Box(Modifier.weight(1f))
            Text(
                text = "Aa",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GsFonts.Product,
                color = colors.textTertiary,
            )
        }
    }
}
