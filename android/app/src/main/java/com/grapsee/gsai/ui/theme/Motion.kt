package com.grapsee.gsai.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * AERUO KINETIC — motion tokens. Everything moves with intent.
 * Springs over eases; press scales; stagger entrances; aurora only when AI is alive.
 */
object GsMotion {
    // Springs (mirror iOS .spring(response:0.35, dampingFraction:0.8))
    fun standard() = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)
    fun snappy() = spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)
    fun gentle() = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow)

    const val STAGGER_MS = 30
    const val PRESS_SCALE = 0.97f
    const val CARET_PULSE_MS = 800

    // Radii
    val radiusCard = 16.dp
    val radiusChip = 999.dp
    val radiusSheet = 24.dp
    val radiusInput = 26.dp

    // Spacing
    val spaceXS = 4.dp
    val spaceS = 8.dp
    val spaceM = 16.dp
    val spaceL = 24.dp
    val spaceXL = 32.dp
}

/**
 * Kinetic press: spring scale-down on press. Use on every tappable surface
 * whose click handler lives elsewhere.
 */
fun Modifier.kineticPress(
    pressedScale: Float = GsMotion.PRESS_SCALE
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = GsMotion.standard(),
        label = "kineticPress"
    )
    this.scale(scale)
}

/**
 * Kinetic press driven by the component's OWN interaction source. Pass the
 * same [interaction] into the clickable Surface/onClick — the press scale then
 * tracks real touches instead of listening to a source no one emits to.
 * Without this wiring the animation never fires and taps feel dead.
 */
fun Modifier.kineticPress(
    interaction: MutableInteractionSource,
    pressedScale: Float = GsMotion.PRESS_SCALE
): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = GsMotion.standard(),
        label = "kineticPress"
    )
    this.scale(scale)
}

/**
 * Aurora brush. STATIC by default — a sliding gradient behind every surface used to
 * recompose each caller every frame (the #1 reported jank source). Pass animated = true
 * only for a true AI-active moment, and prefer Modifier.auroraBackground which animates
 * in the draw phase without recomposing anything.
 */
@Composable
fun rememberAuroraBrush(
    shape: Shape = RoundedCornerShape(GsMotion.radiusCard),
    animated: Boolean = false
): Brush {
    if (!animated) {
        return remember {
            Brush.linearGradient(
                colors = Aeruo.Aurora,
                start = Offset(-100f, 0f),
                end = Offset(500f, 600f)
            )
        }
    }
    val transition = rememberInfiniteTransition(label = "aurora")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auroraShift"
    )
    val colors = Aeruo.Aurora
    val start = Offset(shift * 400f - 100f, 0f)
    val end = Offset(start.x + 600f, 600f)
    return Brush.linearGradient(colors = colors, start = start, end = end)
}

/**
 * Draw-phase aurora background — the gradient slides while the AI is alive, but the
 * animated state is read inside drawBehind, so nothing ever recomposes; only this node
 * re-renders. Use for the streaming/generating/listening life-signs.
 */
fun Modifier.auroraBackground(shape: Shape): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "aurora")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auroraShift"
    )
    val colors = Aeruo.Aurora
    this
        .clip(shape)
        .drawBehind {
            val start = Offset(shift * 400f - 100f, 0f)
            val brush = Brush.linearGradient(colors, start = start, end = Offset(start.x + 600f, 600f))
            drawRect(brush)
        }
}

/** Staggered entrance delay for list items. */
fun staggerDelay(index: Int): Int = index * GsMotion.STAGGER_MS

/** Placeholder pulser for skeletons. */
@Composable
fun skeletonAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "skeletonAlpha"
    )
    return alpha
}

/** Shimmer background for skeleton blocks. */
@Composable
fun Modifier.skeletonSurface(shape: Shape, color: Color): Modifier = composed {
    this.background(color.copy(alpha = skeletonAlpha()), shape)
}

/** Soft elevation shadow (editorial, subtle). */
fun Modifier.softElevation(elevation: Dp = 2.dp): Modifier = this
