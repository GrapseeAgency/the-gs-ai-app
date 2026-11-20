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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.LocalGsIsDark

/**
 * Native Compose counterpart to the iOS Clover bot avatar. The static mark is
 * the same clover-shaped identity on every assistant turn; while a reply is
 * streaming it gently breathes and tilts instead of replacing the bot with a
 * generic loading glyph.
 */
@Composable
fun GsCloverAvatar(
    size: Dp = 32.dp,
    isWorking: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val dark = LocalGsIsDark.current
    val pulse = if (isWorking) {
        val transition = rememberInfiniteTransition(label = "cloverWorking")
        val animatedPulse by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1_000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "cloverPulse",
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
                ambientColor = Color(0x2A7C5CFF),
                spotColor = Color(0x2A7C5CFF),
            )
            .clip(CircleShape)
            .background(
                if (dark) Color.White.copy(alpha = 0.10f) else Color(0xFFF1EBFF),
                CircleShape,
            )
            .border(
                width = 1.dp,
                color = if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.95f),
                shape = CircleShape,
            )
            .semantics {
                contentDescription = if (isWorking) "Nexly is replying" else "Nexly"
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = GsIcons.Clover,
            contentDescription = null,
            tint = Color(0xFF8E7BFF),
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
