package com.grapsee.gsai.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.SettingsStore
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Compose-native counterpart to the iOS DistortionWave transition. The new
 * surface expands from the tapped plan control under a softly rippling edge;
 * reversing [isRevealed] closes the same wave back to its source.
 *
 * [source] and [focus] are in the same root-local coordinate space as this
 * composable. No purchase state is inferred here: callers choose which real
 * destination to reveal and only a verified commerce callback should ever
 * reveal a purchase-success/receipt screen.
 */
@Composable
fun GsDistortionWave(
    isRevealed: Boolean,
    source: Rect,
    focus: Offset? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
    revealed: @Composable () -> Unit,
) {
    val transition = updateTransition(targetState = isRevealed, label = "gsDistortionWave")
    val reduceMotion = SettingsStore.reduceAnimations || SettingsStore.reduceMotion
    val progress by transition.animateFloat(
        transitionSpec = {
            if (reduceMotion) snap() else tween(durationMillis = 520, easing = FastOutSlowInEasing)
        },
        label = "gsDistortionWaveProgress",
    ) { open -> if (open) 1f else 0f }

    Box(modifier = modifier) {
        content()
        if (isRevealed || transition.isRunning) {
            val origin = focus ?: source.takeUnless { it.width <= 0f || it.height <= 0f }?.center
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent()
                        }
                    }
                    .drawWithContent {
                        val drawScope = this
                        val waveOrigin = origin ?: Offset(size.width / 2f, size.height / 2f)
                        val path = radialWavePath(size, waveOrigin, progress, 18.dp.toPx())
                        clipPath(path) {
                            drawScope.drawContent()
                        }
                    },
            ) {
                revealed()
            }
        }
    }
}

private fun radialWavePath(size: Size, origin: Offset, progress: Float, maxAmplitude: Float): Path {
    val t = progress.coerceIn(0f, 1f)
    val center = Offset(
        x = origin.x.coerceIn(0f, size.width),
        y = origin.y.coerceIn(0f, size.height),
    )
    val farX = max(center.x, size.width - center.x)
    val farY = max(center.y, size.height - center.y)
    val radius = sqrt(farX * farX + farY * farY) * t
    val amplitude = min(size.minDimension * 0.018f, maxAmplitude) * sin(PI * t).toFloat()
    val path = Path()
    val segments = 144

    for (index in 0..segments) {
        val angle = (index.toFloat() / segments) * (2f * PI.toFloat())
        val ripple = sin(angle * 5f - t * 8f) * amplitude
        val r = (radius + ripple).coerceAtLeast(0f)
        val point = Offset(
            x = center.x + cos(angle) * r,
            y = center.y + sin(angle) * r,
        )
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    return path
}
