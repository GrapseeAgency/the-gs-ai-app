package com.grapsee.gsai.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.theme.GsTheme

enum class NativeIllustrationVariant { Companion, Research, Vision, Code, Documents, Ideas }

/**
 * Small code-drawn companion illustration for utility states. It deliberately
 * uses geometry, gradients, and motion rather than a stock glyph or bitmap.
 */
@Composable
fun NativeGsIllustration(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    variant: NativeIllustrationVariant = NativeIllustrationVariant.Companion,
    contentDescription: String = "GS companion illustration"
) {
    val reduced = SettingsStore.reduceMotion || SettingsStore.reduceAnimations
    val transition = rememberInfiniteTransition(label = "native-gs-illustration")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "native-gs-illustration-phase"
    )
    val t = if (reduced) 0.5f else phase
    val colors = GsTheme.colors

    Canvas(
        modifier = modifier
            .size(size)
            .semantics {
                role = Role.Image
                this.contentDescription = contentDescription
            }
    ) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = this.size.minDimension * 0.29f
        val orbit = this.size.minDimension * (0.27f + t * 0.035f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.accentStrong.copy(alpha = 0.92f), colors.accent.copy(alpha = 0.42f), colors.accentSoft.copy(alpha = 0.08f)),
                center = center,
                radius = radius * 1.9f
            ),
            radius = radius * 1.9f,
            center = center
        )
        rotate(degrees = -8f + t * 16f, pivot = center) {
            drawArc(
                color = colors.accent.copy(alpha = 0.52f),
                startAngle = 210f,
                sweepAngle = 220f,
                useCenter = false,
                topLeft = Offset(center.x - orbit, center.y - orbit),
                size = androidx.compose.ui.geometry.Size(orbit * 2f, orbit * 2f),
                style = Stroke(width = this.size.minDimension * 0.018f, cap = StrokeCap.Round)
            )
            drawCircle(
                color = colors.accentStrong,
                radius = this.size.minDimension * 0.045f,
                center = Offset(center.x + orbit * 0.92f, center.y - orbit * 0.16f)
            )
            if (variant != NativeIllustrationVariant.Companion) {
                val satelliteAngle = when (variant) {
                    NativeIllustrationVariant.Research -> 38f
                    NativeIllustrationVariant.Vision -> 112f
                    NativeIllustrationVariant.Code -> 176f
                    NativeIllustrationVariant.Documents -> 224f
                    NativeIllustrationVariant.Ideas -> 292f
                    NativeIllustrationVariant.Companion -> 0f
                }
                val radians = Math.toRadians(satelliteAngle.toDouble())
                drawCircle(
                    color = colors.accent.copy(alpha = 0.78f),
                    radius = this.size.minDimension * 0.028f,
                    center = Offset(
                        center.x + (orbit * 0.78f * kotlin.math.cos(radians)).toFloat(),
                        center.y + (orbit * 0.78f * kotlin.math.sin(radians)).toFloat()
                    )
                )
            }
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.accentStrong, colors.accent, colors.accent.copy(alpha = 0.72f)),
                center = Offset(center.x - radius * 0.28f, center.y - radius * 0.32f),
                radius = radius * 1.42f
            ),
            radius = radius,
            center = center
        )
        drawCircle(
            color = colors.onAccent.copy(alpha = 0.38f),
            radius = radius * 0.16f,
            center = Offset(center.x - radius * 0.28f, center.y - radius * 0.30f)
        )
    }
}
