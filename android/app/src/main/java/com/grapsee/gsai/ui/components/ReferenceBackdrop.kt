package com.grapsee.gsai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.LocalGsIsDark
import kotlin.math.cos
import kotlin.math.sin

/**
 * THE BLOOM — the app's one ambient artwork.
 *
 * The reference product is not "a white app with orange buttons"; it is a
 * photograph of light through blossom, seen through frosted glass. That
 * atmosphere cannot live in a token, so it lives here: a single static
 * canvas painted behind the whole navigation graph.
 *
 * Three deliberate layers, in painterly order:
 *   1. a vertical wash (warm paper at the top, blush at the bottom);
 *   2. out-of-focus light — large radial gradients plus ringed bokeh;
 *   3. in-focus blossom branches in the two lower corners and the upper
 *      right, drawn petal by petal with a lit edge and a warm core.
 *
 * Everything is deterministic and non-interactive. There is no animation:
 * the screen repaints constantly while a conversation streams, and a
 * breathing background would tax every one of those frames. The orb carries
 * the motion instead.
 *
 * Settings → Appearance → Background style chooses how much of this is
 * painted (Blurred = all, Gradient = wash + light, Solid = wash only).
 */
/** One background pearl: contact glow, iridescent body, rim, specular. */
private fun DrawScope.ambientPearl(
    center: Offset,
    radius: Float,
    tint: Color,
    dark: Boolean,
) {
    val bodyAlpha = if (dark) 0.14f else 0.56f
    val rimAlpha = if (dark) 0.16f else 0.62f
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                Color.White.copy(alpha = bodyAlpha),
                tint.copy(alpha = bodyAlpha * 0.55f),
                Color.Transparent
            ),
            center = Offset(center.x - radius * 0.3f, center.y - radius * 0.35f),
            radius = radius * 1.35f
        ),
        radius = radius,
        center = center
    )
    // Warm core: the sheet's orbs are lit from inside, not just rimmed.
    drawCircle(
        brush = Brush.radialGradient(
            listOf(tint.copy(alpha = bodyAlpha * 0.85f), Color.Transparent),
            center = Offset(center.x - radius * 0.10f, center.y - radius * 0.05f),
            radius = radius * 0.62f
        ),
        radius = radius * 0.62f,
        center = Offset(center.x - radius * 0.10f, center.y - radius * 0.05f)
    )
    drawArc(
        color = Color.White.copy(alpha = rimAlpha),
        startAngle = 140f,
        sweepAngle = 160f,
        useCenter = false,
        topLeft = Offset(center.x - radius * 0.96f, center.y - radius * 0.96f),
        size = Size(radius * 1.92f, radius * 1.92f),
        style = Stroke(width = radius * 0.035f, cap = StrokeCap.Round)
    )
}

@Composable
fun ReferenceBackdrop(
    modifier: Modifier = Modifier,
    /**
     * The Home sheet is a photograph of pearls and silk, with no blossom in
     * frame. Home repaints this backdrop without the branches so the hero
     * matches the sheet; every other screen keeps the bloom.
     */
    showBlossom: Boolean = true,
    /** Lens bokeh. Home's sheet shows soft orbs only, and skipping seven
     *  gradient discs keeps the double-drawn hero cheap on software GPUs. */
    bokeh: Boolean = true,
) {
    val colors = GsTheme.colors
    val dark = LocalGsIsDark.current
    val style = SettingsStore.backgroundStyle

    // Sampled from the reference photography: a cream paper that warms to
    // beige, not a peach wash.
    val top = if (dark) Color(0xFF241715) else Color(0xFFFBF6F1)
    val middle = if (dark) Color(0xFF1D1211) else Color(0xFFF6EDE7)
    val bottom = if (dark) Color(0xFF1A1110) else Color(0xFFF2E7DF)
    val petalLight = if (dark) colors.accent.copy(alpha = 0.20f) else Color(0xFFFFC9B6)
    val petalDeep = if (dark) colors.accentStrong.copy(alpha = 0.14f) else Color(0xFFF7A98F)
    val petalCore = if (dark) colors.accent.copy(alpha = 0.45f) else Color(0xFFFFD9A8)
    val stem = if (dark) Color(0xFF6A4A40).copy(alpha = 0.35f) else Color(0xFFD9A996).copy(alpha = 0.55f)
    val glowA = if (dark) colors.accent.copy(alpha = 0.10f) else Color(0xFFFFB98F).copy(alpha = 0.22f)
    val glowB = if (dark) Color(0xFF8C6FE0).copy(alpha = 0.08f) else Color(0xFFFFC6D8).copy(alpha = 0.20f)
    val glowC = if (dark) Color(0xFFFFC98C).copy(alpha = 0.06f) else Color(0xFFFFE0B8).copy(alpha = 0.24f)

    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(0f to top, 0.52f to middle, 1f to bottom))

        if (style == "Solid") return@Canvas

        // 2. Out-of-focus light. These are the "camera" blooms: huge, soft,
        //    and placed so the brightest corner sits behind the status bar.
        drawCircle(
            brush = Brush.radialGradient(
                listOf(glowA, glowA.copy(alpha = 0f)),
                center = Offset(size.width * 0.94f, size.height * 0.02f),
                radius = size.width * 0.95f
            ),
            radius = size.width * 0.95f,
            center = Offset(size.width * 0.94f, size.height * 0.02f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(glowB, glowB.copy(alpha = 0f)),
                center = Offset(size.width * 0.02f, size.height * 0.58f),
                radius = size.width * 0.75f
            ),
            radius = size.width * 0.75f,
            center = Offset(size.width * 0.02f, size.height * 0.58f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(glowC, glowC.copy(alpha = 0f)),
                center = Offset(size.width * 0.70f, size.height * 1.02f),
                radius = size.width * 0.85f
            ),
            radius = size.width * 0.85f,
            center = Offset(size.width * 0.70f, size.height * 1.02f)
        )

        // Bokeh: ringed discs read as lens blur, flat discs read as dirty
        // paper. The ring is what sells it.
        val bokehDiscs = listOf(
            floatArrayOf(0.86f, 0.20f, 0.075f, 0.20f),
            floatArrayOf(0.12f, 0.34f, 0.048f, 0.16f),
            floatArrayOf(0.72f, 0.66f, 0.030f, 0.13f),
            floatArrayOf(0.24f, 0.72f, 0.062f, 0.11f),
            floatArrayOf(0.50f, 0.12f, 0.038f, 0.14f),
            floatArrayOf(0.93f, 0.55f, 0.052f, 0.10f),
            floatArrayOf(0.06f, 0.90f, 0.070f, 0.13f),
        )
        if (bokeh) bokehDiscs.forEach { b ->
            val c = Offset(size.width * b[0], size.height * b[1])
            val r = size.width * b[2]
            val a = b[3]
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = a), Color.White.copy(alpha = a * 0.25f), Color.White.copy(alpha = 0f)),
                    center = c,
                    radius = r
                ),
                radius = r,
                center = c
            )
            // No ring: the sheet's bokeh is pure soft light, and a stroke
            // reads as a drawn circle rather than defocus.
        }

        // 2b. Pearlescent orbs. The reference's backdrop is not only bloom:
        //     three out-of-focus glass spheres hang behind the content, lit
        //     from the upper left. They sit between the bokeh and the blossom
        //     so the branches still read on top.
        ambientPearl(
            center = Offset(size.width * 1.02f, size.height * 0.16f),
            radius = size.width * 0.34f,
            tint = Color(0xFFFFC7B4),
            dark = dark
        )
        // The sheet's hero pearl sits behind the greeting's right shoulder.
        ambientPearl(
            center = Offset(size.width * 0.94f, size.height * 0.30f),
            radius = size.width * 0.27f,
            tint = Color(0xFFFFBE9E),
            dark = dark
        )
        ambientPearl(
            center = Offset(size.width * -0.06f, size.height * 0.60f),
            radius = size.width * 0.30f,
            tint = Color(0xFFD9C6FF),
            dark = dark
        )
        ambientPearl(
            center = Offset(size.width * 0.74f, size.height * 0.99f),
            radius = size.width * 0.36f,
            tint = Color(0xFFFFC2D6),
            dark = dark
        )

        if (style == "Gradient" || !showBlossom) return@Canvas

        // 3. In-focus blossom. Two branches, mirrored: the lower-left one
        //    enters from off-screen, the upper-right one hangs in from the
        //    corner. Petals are translucent so the wash shows through them.
        val branch = Path().apply {
            moveTo(-size.width * 0.04f, size.height * 1.02f)
            cubicTo(
                size.width * 0.08f, size.height * 0.93f,
                size.width * 0.20f, size.height * 0.94f,
                size.width * 0.34f, size.height * 0.83f
            )
        }
        drawPath(
            branch,
            color = stem,
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        )
        // twigs
        drawLine(
            color = stem,
            start = Offset(size.width * 0.13f, size.height * 0.925f),
            end = Offset(size.width * 0.10f, size.height * 0.845f),
            strokeWidth = 1.4.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = stem,
            start = Offset(size.width * 0.22f, size.height * 0.915f),
            end = Offset(size.width * 0.25f, size.height * 0.835f),
            strokeWidth = 1.4.dp.toPx(),
            cap = StrokeCap.Round
        )

        drawBlossom(Offset(size.width * 0.055f, size.height * 0.945f), size.width * 0.085f, petalLight, petalDeep, petalCore, 18f)
        drawBlossom(Offset(size.width * 0.155f, size.height * 0.885f), size.width * 0.056f, petalLight, petalDeep, petalCore, -34f)
        drawBlossom(Offset(size.width * 0.255f, size.height * 0.905f), size.width * 0.040f, petalLight, petalDeep, petalCore, 62f)
        drawBlossom(Offset(size.width * 0.315f, size.height * 0.836f), size.width * 0.028f, petalLight, petalDeep, petalCore, -8f)
        drawBud(Offset(size.width * 0.095f, size.height * 0.845f), size.width * 0.016f, petalDeep)
        drawBud(Offset(size.width * 0.235f, size.height * 0.842f), size.width * 0.013f, petalDeep)

        val upper = Path().apply {
            moveTo(size.width * 1.04f, size.height * -0.02f)
            cubicTo(
                size.width * 0.94f, size.height * 0.06f,
                size.width * 0.90f, size.height * 0.10f,
                size.width * 0.80f, size.height * 0.15f
            )
        }
        drawPath(
            upper,
            color = stem,
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        )
        drawBlossom(Offset(size.width * 0.955f, size.height * 0.055f), size.width * 0.048f, petalLight, petalDeep, petalCore, 40f)
        drawBlossom(Offset(size.width * 0.875f, size.height * 0.105f), size.width * 0.032f, petalLight, petalDeep, petalCore, -20f)
        drawBlossom(Offset(size.width * 0.802f, size.height * 0.150f), size.width * 0.020f, petalLight, petalDeep, petalCore, 74f)

        // A handful of four-point sparkles. Four is enough; more reads as
        // glitter, which is a different product.
        val sparkles = listOf(
            floatArrayOf(0.42f, 0.22f, 0.016f),
            floatArrayOf(0.66f, 0.42f, 0.010f),
            floatArrayOf(0.18f, 0.52f, 0.008f),
            floatArrayOf(0.88f, 0.78f, 0.012f),
        )
        sparkles.forEach { s ->
            drawSparkle(
                center = Offset(size.width * s[0], size.height * s[1]),
                radius = size.width * s[2],
                color = Color.White.copy(alpha = if (dark) 0.32f else 0.68f)
            )
        }
    }
}

/**
 * One blossom: five petals around a warm core. The lit edge is drawn as a
 * separate hairline so the flower keeps its shape when the fill is as
 * transparent as it is here.
 */
private fun DrawScope.drawBlossom(
    center: Offset,
    radius: Float,
    light: Color,
    deep: Color,
    core: Color,
    tilt: Float,
) {
    if (radius < 1f) return
    repeat(5) { i ->
        val angle = tilt + i * 72f
        rotate(degrees = angle, pivot = center) {
            val path = Path().apply {
                moveTo(center.x, center.y - radius * 0.08f)
                cubicTo(
                    center.x - radius * 0.78f, center.y - radius * 0.34f,
                    center.x - radius * 0.52f, center.y - radius * 1.02f,
                    center.x, center.y - radius * 0.98f
                )
                cubicTo(
                    center.x + radius * 0.52f, center.y - radius * 1.02f,
                    center.x + radius * 0.78f, center.y - radius * 0.34f,
                    center.x, center.y - radius * 0.08f
                )
                close()
            }
            drawPath(
                path,
                brush = Brush.linearGradient(
                    colors = listOf(light, deep),
                    start = Offset(center.x, center.y - radius * 0.08f),
                    end = Offset(center.x, center.y - radius)
                )
            )
            drawPath(
                path,
                color = Color.White.copy(alpha = 0.26f),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
    drawCircle(
        brush = Brush.radialGradient(
            listOf(core, core.copy(alpha = 0.5f), Color.Transparent),
            center = center,
            radius = radius * 0.42f
        ),
        radius = radius * 0.40f,
        center = center
    )
    drawCircle(Color.White.copy(alpha = 0.35f), radius = radius * 0.075f, center = center)
}

private fun DrawScope.drawBud(center: Offset, radius: Float, color: Color) {
    if (radius < 0.6f) return
    rotate(degrees = 30f, pivot = center) {
        val path = Path().apply {
            moveTo(center.x, center.y + radius * 0.8f)
            cubicTo(
                center.x - radius, center.y + radius * 0.1f,
                center.x - radius * 0.7f, center.y - radius,
                center.x, center.y - radius
            )
            cubicTo(
                center.x + radius * 0.7f, center.y - radius,
                center.x + radius, center.y + radius * 0.1f,
                center.x, center.y + radius * 0.8f
            )
            close()
        }
        drawPath(path, color)
    }
}

private fun DrawScope.drawSparkle(center: Offset, radius: Float, color: Color) {
    if (radius < 0.5f) return
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        cubicTo(center.x + radius * 0.16f, center.y - radius * 0.20f, center.x + radius * 0.20f, center.y - radius * 0.16f, center.x + radius, center.y)
        cubicTo(center.x + radius * 0.20f, center.y + radius * 0.16f, center.x + radius * 0.16f, center.y + radius * 0.20f, center.x, center.y + radius)
        cubicTo(center.x - radius * 0.16f, center.y + radius * 0.20f, center.x - radius * 0.20f, center.y + radius * 0.16f, center.x - radius, center.y)
        cubicTo(center.x - radius * 0.20f, center.y - radius * 0.16f, center.x - radius * 0.16f, center.y - radius * 0.20f, center.x, center.y - radius)
        close()
    }
    drawPath(path, color)
}

/**
 * Small helper used by the decorative halo rings on hero cards: a circle
 * whose stroke fades around the circumference.
 */
fun DrawScope.drawFadingRing(center: Offset, radius: Float, color: Color, width: Float) {
    val steps = 72
    for (i in 0 until steps) {
        val a0 = i * (360f / steps)
        val a1 = a0 + (360f / steps) * 0.7f
        val alpha = 0.10f + 0.55f * ((sin(Math.toRadians(a0.toDouble())).toFloat() + 1f) / 2f)
        drawArc(
            color = color.copy(alpha = color.alpha * alpha),
            startAngle = a0,
            sweepAngle = a1 - a0,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
            style = Stroke(width = width, cap = StrokeCap.Round)
        )
    }
}

/** Exposed for the hero orb: a soft radial light behind the companion. */
fun DrawScope.drawHalo(center: Offset, radius: Float, color: Color) {
    drawCircle(
        brush = Brush.radialGradient(
            listOf(color, color.copy(alpha = color.alpha * 0.35f), Color.Transparent),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

/** Unused trigonometry kept local so the geometry above reads declaratively. */
private fun unit(angleDegrees: Float): Offset =
    Offset(cos(Math.toRadians(angleDegrees.toDouble())).toFloat(), sin(Math.toRadians(angleDegrees.toDouble())).toFloat())
