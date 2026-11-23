package com.grapsee.gsai.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.AssistantAvatarStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * A real Jetpack Compose Canvas character, not an icon or a WebView.
 * State follows the actual assistant stream. This is a native interpretation
 * of the clover reference, NOT the JavaScript bot-avatars rendering engine.
 */
@Composable
fun GsBotAvatar(
    style: AssistantAvatarStyle,
    size: Dp = 32.dp,
    isWorking: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val motionEnabled = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) != 0f
    }
    val transition = rememberInfiniteTransition(label = "nativeBotAvatar")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = if (isWorking) 1_500 else 5_600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "nativeBotMotion",
    )
    val t = if (motionEnabled) progress else 0f
    val jump = if (!motionEnabled) 0f else if (isWorking) {
        max(0f, sin((t * PI).toFloat()))
    } else if (t > 0.84f) {
        max(0f, sin(((t - 0.84f) / 0.16f * PI).toFloat()))
    } else 0f
    val glanceX = if (motionEnabled) sin((t * PI * 2).toFloat()) * 3.2f else 0f
    val glanceY = if (motionEnabled) sin((t * PI * 4).toFloat()) * 1.5f else 0f
    val blink = motionEnabled && (t in 0.46f..0.475f || t in 0.78f..0.79f)
    val body = remember(style) { avatarBodyPath(style) }
    val ink = Color(0xFF12251C)
    val palette = when (style) {
        AssistantAvatarStyle.CLOVER -> listOf(Color(0xFF9AFFB9), Color(0xFF38DF85), Color(0xFF079B58))
        AssistantAvatarStyle.FLOWER -> listOf(Color(0xFFFFB1D6), Color(0xFFFF65AB), Color(0xFFC61A69))
        AssistantAvatarStyle.TRIANGLE -> listOf(Color(0xFFFFB1FA), Color(0xFFD968F1), Color(0xFF8E32C4))
        AssistantAvatarStyle.SQUARE -> listOf(Color(0xFFA9F2FF), Color(0xFF48BCF5), Color(0xFF176EB4))
    }

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                translationY = -jump * this.size.height * 0.10f
                rotationZ = sin((t * PI * 2).toFloat()) * (if (isWorking) 9f else 3f)
                scaleX = 1f + jump * 0.06f
                scaleY = 1f - jump * 0.035f
            }
            .semantics {
                contentDescription = style.label + if (isWorking) " bot, working" else " bot, idle"
            },
    ) {
        val unit = this.size.minDimension / 100f
        withTransform({ scale(unit, unit, pivot = Offset.Zero) }) {
            // Layered shading gives the body depth; no circular badge/backplate.
            drawPath(body, color = palette[2].copy(alpha = 0.28f))
            drawPath(
                path = body,
                brush = Brush.linearGradient(
                    0f to palette[0], 0.38f to palette[1], 1f to palette[2],
                    start = Offset(21f, 17f), end = Offset(83f, 87f),
                ),
            )
            clipPath(body) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.52f), Color.Transparent),
                        center = Offset(32f, 23f), radius = 43f,
                    ),
                    radius = 43f, center = Offset(32f, 23f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(palette[2].copy(alpha = 0.24f), Color.Transparent),
                        center = Offset(76f, 79f), radius = 45f,
                    ),
                    radius = 45f, center = Offset(76f, 79f),
                )
            }
            drawPath(body, color = palette[0].copy(alpha = 0.32f), style = Stroke(width = 1.2f))

            val eyeHeight = if (blink) 1.3f else 8.4f
            for (x in listOf(41f, 59f)) {
                val cx = x + glanceX
                val cy = 48f + glanceY
                drawOval(
                    color = ink,
                    topLeft = Offset(cx - 3.8f, cy - eyeHeight / 2f),
                    size = Size(7.6f, eyeHeight),
                )
                if (!blink) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.92f),
                        radius = 1.7f,
                        center = Offset(cx - 1.15f, cy - 2f),
                    )
                }
            }
            val smile = Path().apply {
                moveTo(44.5f + glanceX * 0.35f, 61f + glanceY * 0.35f)
                quadraticBezierTo(
                    50f + glanceX * 0.35f,
                    if (isWorking) 69f else 65f,
                    55.5f + glanceX * 0.35f,
                    61f + glanceY * 0.35f,
                )
            }
            drawPath(smile, color = ink.copy(alpha = 0.84f), style = Stroke(width = 2f, cap = StrokeCap.Round))
        }
    }
}

private fun avatarBodyPath(style: AssistantAvatarStyle): Path = Path().apply {
    when (style) {
        AssistantAvatarStyle.CLOVER -> {
            moveTo(51f, 16f)
            cubicTo(38f, 4f, 18f, 12f, 18f, 31f)
            cubicTo(3f, 33f, 5f, 58f, 21f, 65f)
            cubicTo(16f, 84f, 37f, 96f, 52f, 83f)
            cubicTo(69f, 96f, 90f, 80f, 80f, 65f)
            cubicTo(96f, 57f, 91f, 36f, 77f, 32f)
            cubicTo(80f, 14f, 60f, 5f, 51f, 16f)
        }
        AssistantAvatarStyle.FLOWER -> {
            for (step in 0..96) {
                val a = step / 96.0 * PI * 2
                val r = 32.0 + 8.0 * cos(a * 6)
                val x = 50f + (r * cos(a)).toFloat()
                val y = 50f + (r * sin(a)).toFloat()
                if (step == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        AssistantAvatarStyle.TRIANGLE -> {
            moveTo(47f, 15f)
            quadraticBezierTo(51f, 10f, 55f, 17f)
            lineTo(90f, 74f)
            quadraticBezierTo(96f, 86f, 82f, 86f)
            lineTo(19f, 86f)
            quadraticBezierTo(5f, 86f, 12f, 72f)
        }
        AssistantAvatarStyle.SQUARE -> {
            moveTo(32f, 17f)
            lineTo(68f, 17f)
            quadraticBezierTo(83f, 17f, 83f, 32f)
            lineTo(83f, 68f)
            quadraticBezierTo(83f, 83f, 68f, 83f)
            lineTo(32f, 83f)
            quadraticBezierTo(17f, 83f, 17f, 68f)
            lineTo(17f, 32f)
            quadraticBezierTo(17f, 17f, 32f, 17f)
        }
    }
    close()
}
