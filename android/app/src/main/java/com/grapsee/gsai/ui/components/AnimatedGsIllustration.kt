package com.grapsee.gsai.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.grapsee.gsai.data.SettingsStore

/**
 * A deliberately restrained idle motion: a configurable breath, drift, and lift.
 * It gives the artwork life without turning a frequently visited screen into
 * a loading indicator. SettingsStore gates both animations and accessibility.
 */
@Composable
fun AnimatedGsIllustration(
    @DrawableRes resourceId: Int,
    contentDescription: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    breathDuration: Int = 2400,
    rotationDegrees: Float = 2.2f,
    liftPixels: Float = -3f,
    startDelayMillis: Int = 0,
) {
    val reduced = SettingsStore.reduceMotion || SettingsStore.reduceAnimations
    val transition = rememberInfiniteTransition(label = "gs-illustration-motion")
    val breath by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (reduced) 1f else 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(breathDuration, delayMillis = startDelayMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gs-illustration-breath"
    )
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (reduced) 0f else rotationDegrees,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, delayMillis = startDelayMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gs-illustration-drift"
    )
    val lift by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (reduced) 0f else liftPixels,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, delayMillis = startDelayMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gs-illustration-lift"
    )
    Image(
        painter = painterResource(resourceId),
        contentDescription = contentDescription,
        modifier = modifier
            .size(size)
            .then(if (shape != null) Modifier.clip(shape) else Modifier)
            .graphicsLayer {
                scaleX = breath
                scaleY = breath
                rotationZ = drift
                translationY = lift
            }
    )
}
