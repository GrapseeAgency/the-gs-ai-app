package com.grapsee.gsai.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
 * A deliberately restrained idle motion: a 2.4s breath and a 4-degree drift.
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
) {
    val reduced = SettingsStore.reduceMotion || SettingsStore.reduceAnimations
    val transition = rememberInfiniteTransition(label = "gs-illustration-motion")
    val breath by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (reduced) 1f else 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gs-illustration-breath"
    )
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (reduced) 0f else 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gs-illustration-drift"
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
            }
    )
}
