package com.grapsee.gsai.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.AssistantAvatarStyle
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.LocalGsIsDark

/**
 * Native Compose assistant avatar. The selected style is a persisted setting,
 * shared by every assistant reply; the mark breathes and tilts while a reply
 * streams instead of being replaced with a generic loading glyph.
 */
@Composable
fun GsBotAvatar(
    style: AssistantAvatarStyle,
    size: Dp = 32.dp,
    isWorking: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val dark = LocalGsIsDark.current
    val tint = style.tint
    val pulse = if (isWorking) {
        val transition = rememberInfiniteTransition(label = "botAvatarWorking")
        val animatedPulse by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1_000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "botAvatarPulse",
        )
        animatedPulse
    } else {
        0f
    }

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 3.dp,
                shape = CircleShape,
                ambientColor = tint.copy(alpha = 0.18f),
                spotColor = tint.copy(alpha = 0.18f),
            )
            .clip(CircleShape)
            .background(
                if (dark) Color.White.copy(alpha = 0.10f) else tint.copy(alpha = 0.12f),
                CircleShape,
            )
            .border(
                width = 1.dp,
                color = if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.95f),
                shape = CircleShape,
            )
            .semantics {
                contentDescription = if (isWorking) "${style.label} bot is replying" else "${style.label} bot avatar"
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = style.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(size * 0.56f)
                .graphicsLayer {
                    val breathe = if (isWorking) 0.94f + 0.12f * pulse else 1f
                    scaleX = breathe
                    scaleY = breathe
                    rotationZ = if (isWorking) (pulse - 0.5f) * 8f else 0f
                },
        )
    }
}

private val AssistantAvatarStyle.icon: ImageVector
    get() = when (this) {
        AssistantAvatarStyle.CLOVER -> GsIcons.Clover
        AssistantAvatarStyle.FLOWER -> GsIcons.Flower
        AssistantAvatarStyle.TRIANGLE -> GsIcons.Triangle
        AssistantAvatarStyle.SQUARE -> GsIcons.Square
    }

private val AssistantAvatarStyle.tint: Color
    get() = when (this) {
        AssistantAvatarStyle.CLOVER -> Color(0xFF8E7BFF)
        AssistantAvatarStyle.FLOWER -> Color(0xFFFF7AB8)
        AssistantAvatarStyle.TRIANGLE -> Color(0xFFDC48FF)
        AssistantAvatarStyle.SQUARE -> Color(0xFF35B8FF)
    }
