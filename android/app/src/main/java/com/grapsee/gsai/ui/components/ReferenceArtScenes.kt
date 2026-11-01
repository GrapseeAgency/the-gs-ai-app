package com.grapsee.gsai.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.cos
import kotlin.math.sin

/**
 * The three conversation pages' artworks, drawn from primitives so they read
 * the same on both platforms at any scale — the Dreamy Pastel mocks' own
 * glass-and-petal language, not crops of it:
 *
 *  - [drawLotusOrbScene] — the pre-chat hero: a lotus inside a glass dome,
 *    a violet crystal rising from its heart.
 *  - [drawArchivedLotusScene] — the archived totals card's close-up lotus.
 *  - [drawSharedOrbScene] — the shared page's empty state: a share glyph
 *    inside a glass bubble resting on glass petals, one orbit ring.
 *
 * Shared vocabulary: [petal] (a pointed oval around a pivot), [star4] (the
 * four-point sparkle), and the glass washes — radial white-to-tint fills with
 * bright rim arcs, never flat colour.
 */

private val WashWhite = Color(0xFFFFFFFF)
private val PetalPeach = Color(0xFFFFDCC8)
private val PetalBlush = Color(0xFFFFC9DC)
private val PetalRose = Color(0xFFF7AFC8)
private val PetalLavender = Color(0xFFE3D4FB)
private val PetalLilac = Color(0xFFCDB4F2)
private val CrystalLight = Color(0xFFC9ADF6)
private val CrystalDeep = Color(0xFF8F6BE0)
private val Violet = Color(0xFF8C72DC)
private val TintPink = Color(0x33FFD9EC)
private val TintLavender = Color(0x2EDECDF7)

/** A pointed oval petal: two cubic curves meeting at a soft tip, drawn
 *  upward from the origin, then rotated. [widthFraction] widens the belly. */
private fun petalPath(length: Float, widthFraction: Float = 0.42f): Path {
    val w = length * widthFraction
    return Path().apply {
        moveTo(0f, 0f)
        cubicTo(-w, -length * 0.28f, -w * 0.72f, -length * 0.82f, 0f, -length)
        cubicTo(w * 0.72f, -length * 0.82f, w, -length * 0.28f, 0f, 0f)
        close()
    }
}

private fun DrawScope.drawPetal(
    center: Offset,
    angleDegrees: Float,
    length: Float,
    widthFraction: Float,
    tip: Color,
    base: Color,
    alpha: Float,
    edge: Color = WashWhite,
) {
    rotate(degrees = angleDegrees, pivot = center) {
        translate(center.x, center.y) {
            val path = petalPath(length, widthFraction)
            drawPath(
                path,
                Brush.verticalGradient(
                    listOf(base.copy(alpha = alpha), tip.copy(alpha = alpha)),
                    startY = 0f,
                    endY = -length,
                ),
            )
            drawPath(path, edge.copy(alpha = alpha * 0.85f), style = Stroke(1.1f))
        }
    }
}

private fun DrawScope.drawStar4(
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float = 1f,
) {
    val thin = radius * 0.16f
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        cubicTo(center.x + thin, center.y - thin, center.x + thin, center.y - thin, center.x + radius, center.y)
        cubicTo(center.x + thin, center.y + thin, center.x + thin, center.y + thin, center.x, center.y + radius)
        cubicTo(center.x - thin, center.y + thin, center.x - thin, center.y + thin, center.x - radius, center.y)
        cubicTo(center.x - thin, center.y - thin, center.x - thin, center.y - thin, center.x, center.y - radius)
        close()
    }
    drawPath(path, color.copy(alpha = alpha))
    drawCircle(WashWhite.copy(alpha = alpha * 0.9f), radius * 0.12f, center)
}

private fun Stroke(width: Float) =
    androidx.compose.ui.graphics.drawscope.Stroke(width = width)

/** A glass ring arc: bright at the top, fading at the bottom. */
private fun DrawScope.glassRing(
    center: Offset,
    radius: Float,
    width: Float,
    tint: Color = WashWhite,
    topAlpha: Float = 0.95f,
    bottomAlpha: Float = 0.30f,
) {
    drawArc(
        Brush.verticalGradient(
            listOf(tint.copy(alpha = topAlpha), tint.copy(alpha = bottomAlpha)),
            startY = center.y - radius,
            endY = center.y + radius,
        ),
        startAngle = -90f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = width),
    )
}

/** The pre-chat hero: dome, inner sphere, two petal rings, crystal, stars. */
fun DrawScope.drawLotusOrbScene() {
    val w = size.width
    val h = size.height
    val domeCenter = Offset(w * 0.5f, h * 0.60f)
    val domeR = w * 0.46f

    // Page-glow behind the dome.
    drawCircle(
        Brush.radialGradient(
            listOf(TintPink, TintLavender, Color.Transparent),
            center = domeCenter,
            radius = domeR * 1.55f,
        ),
        radius = domeR * 1.55f,
        center = domeCenter,
    )

    // Dome wash: brightest top-center, transparent at the lower edge.
    drawCircle(
        Brush.radialGradient(
            listOf(
                WashWhite.copy(alpha = 0.55f),
                Color(0xFFFDE7F3).copy(alpha = 0.30f),
                Color(0xFFE9DFFB).copy(alpha = 0.14f),
                Color.Transparent,
            ),
            center = Offset(domeCenter.x, domeCenter.y - domeR * 0.25f),
            radius = domeR * 1.15f,
        ),
        radius = domeR,
        center = domeCenter,
    )
    // Dome rims — the double concentric arc the mock draws, with a prismatic
    // pink pass and a cool pass slightly offset.
    glassRing(domeCenter, domeR * 0.985f, w * 0.012f, topAlpha = 0.95f, bottomAlpha = 0.22f)
    glassRing(domeCenter, domeR * 0.885f, w * 0.008f, tint = Color(0xFFF6CCE4), topAlpha = 0.75f, bottomAlpha = 0.18f)
    glassRing(domeCenter, domeR * 0.80f, w * 0.005f, tint = Color(0xFFC9E4F8), topAlpha = 0.45f, bottomAlpha = 0.10f)

    // Inner sphere carrying the flower.
    val innerCenter = Offset(w * 0.5f, h * 0.66f)
    val innerR = w * 0.27f
    drawCircle(
        Brush.radialGradient(
            listOf(
                WashWhite.copy(alpha = 0.50f),
                Color(0xFFFFE4F1).copy(alpha = 0.26f),
                Color(0xFFE4D6FA).copy(alpha = 0.16f),
                Color.Transparent,
            ),
            center = Offset(innerCenter.x - innerR * 0.2f, innerCenter.y - innerR * 0.35f),
            radius = innerR * 1.35f,
        ),
        radius = innerR,
        center = innerCenter,
    )
    glassRing(innerCenter, innerR, w * 0.010f, topAlpha = 0.9f, bottomAlpha = 0.35f)
    drawArc(
        Brush.horizontalGradient(listOf(Color.Transparent, WashWhite.copy(alpha = 0.85f))),
        startAngle = 150f, sweepAngle = 70f, useCenter = false,
        topLeft = Offset(innerCenter.x - innerR, innerCenter.y - innerR),
        size = Size(innerR * 2f, innerR * 2f),
        style = Stroke(width = w * 0.016f),
    )

    // The lotus: a full bloom — an outer ring whose side petals droop to
    // 4-5 and 7-8 o'clock like the mock's, a mid ring, and the violet crystal
    // rising from BEHIND the inner petals, then the white-gold bud.
    val heart = Offset(w * 0.5f, h * 0.74f)
    val outerLen = w * 0.30f
    val outerAngles = listOf(-150f, -120f, -90f, -60f, -30f, 0f, 30f, 60f, 90f, 120f, 150f)
    outerAngles.forEachIndexed { index, angle ->
        val tip = if (index % 2 == 0) PetalRose else PetalPeach
        drawPetal(heart, angle, outerLen, 0.40f, tip, WashWhite, alpha = 0.82f)
    }
    val midAngles = listOf(-120f, -80f, -40f, 0f, 40f, 80f, 120f)
    midAngles.forEach { angle ->
        drawPetal(heart, angle, outerLen * 0.76f, 0.36f, PetalBlush, WashWhite, alpha = 0.9f)
    }

    // The violet crystal rises between the mid and inner rings, so the
    // flower's own petals fold over its base the way the mock draws it.
    val crystalBase = Offset(w * 0.5f, h * 0.60f)
    val tipTop = Offset(w * 0.5f, h * 0.26f)
    val halfW = w * 0.10f
    val leftFace = Path().apply {
        moveTo(crystalBase.x - halfW, crystalBase.y)
        cubicTo(crystalBase.x - halfW * 0.8f, crystalBase.y - h * 0.14f, crystalBase.x - halfW * 0.35f, crystalBase.y - h * 0.24f, tipTop.x, tipTop.y)
        lineTo(crystalBase.x, crystalBase.y + h * 0.015f)
        close()
    }
    val rightFace = Path().apply {
        moveTo(tipTop.x, tipTop.y)
        cubicTo(crystalBase.x + halfW * 0.45f, crystalBase.y - h * 0.22f, crystalBase.x + halfW * 0.85f, crystalBase.y - h * 0.12f, crystalBase.x + halfW, crystalBase.y)
        lineTo(crystalBase.x, crystalBase.y + h * 0.015f)
        close()
    }
    drawPath(leftFace, Brush.verticalGradient(listOf(CrystalLight.copy(alpha = 0.96f), CrystalDeep.copy(alpha = 0.90f)), startY = tipTop.y, endY = crystalBase.y))
    drawPath(rightFace, Brush.verticalGradient(listOf(Violet.copy(alpha = 0.94f), Color(0xFF6E4BC9).copy(alpha = 0.92f)), startY = tipTop.y, endY = crystalBase.y))
    drawPath(
        leftFace,
        Brush.verticalGradient(listOf(WashWhite.copy(alpha = 0.9f), WashWhite.copy(alpha = 0.15f)), startY = tipTop.y, endY = crystalBase.y),
        style = Stroke(w * 0.006f),
    )

    val innerLen = w * 0.17f
    listOf(-25f, 0f, 25f).forEach { angle ->
        drawPetal(heart, angle, innerLen, 0.42f, PetalRose, WashWhite, alpha = 0.95f)
    }
    // Center bud.
    listOf(-10f, 0f, 10f).forEach { angle ->
        drawPetal(heart, angle, w * 0.085f, 0.5f, Color(0xFFFFE9A8), WashWhite, alpha = 0.98f)
    }
    // Sparkles — the mock scatters them over the dome.
    drawStar4(Offset(w * 0.16f, h * 0.30f), w * 0.035f, WashWhite, 0.95f)
    drawStar4(Offset(w * 0.845f, h * 0.33f), w * 0.028f, Color(0xFFE7D9FF), 0.85f)
    drawStar4(Offset(w * 0.80f, h * 0.72f), w * 0.020f, WashWhite, 0.75f)
    drawStar4(Offset(w * 0.135f, h * 0.70f), w * 0.018f, Color(0xFFFFD3E5), 0.70f)
    drawStar4(Offset(w * 0.70f, h * 0.16f), w * 0.016f, WashWhite, 0.8f)
}

/** The archived totals card's close-up lotus: layered petals opening to the
 *  upper right, lavender outside and peach within. */
fun DrawScope.drawArchivedLotusScene() {
    val w = size.width
    val h = size.height
    val heart = Offset(w * 0.78f, h * 0.94f)

    drawCircle(
        Brush.radialGradient(
            listOf(TintPink, Color.Transparent),
            center = Offset(w * 0.55f, h * 0.45f),
            radius = w * 0.75f,
        ),
        radius = w * 0.75f,
        center = Offset(w * 0.55f, h * 0.45f),
    )

    // Back layer: broad lavender petals leaning left.
    listOf(196f, 172f, 150f, 128f).forEachIndexed { index, angle ->
        drawPetal(
            heart, angle, w * (0.52f - index * 0.03f), 0.46f,
            PetalLavender, WashWhite, alpha = 0.88f,
        )
    }
    // Mid layer: lilac petals filling the left flank.
    listOf(160f, 138f).forEach { angle ->
        drawPetal(heart, angle, w * 0.42f, 0.42f, PetalLilac, WashWhite, alpha = 0.9f)
    }
    // Front layer: the peach-pink heart of the flower.
    listOf(120f, 100f, 82f, 64f).forEachIndexed { index, angle ->
        drawPetal(
            heart, angle, w * (0.40f - index * 0.02f), 0.38f,
            if (index % 2 == 0) PetalPeach else PetalBlush, WashWhite, alpha = 0.95f,
        )
    }
    listOf(92f, 76f).forEach { angle ->
        drawPetal(heart, angle, w * 0.24f, 0.4f, PetalRose, WashWhite, alpha = 0.95f)
    }
    drawStar4(Offset(w * 0.22f, h * 0.20f), w * 0.035f, WashWhite, 0.9f)
}

/** The shared page's empty state: a glass bubble carrying the share glyph,
 *  resting on glass petals, circled by one orbit ring. */
fun DrawScope.drawSharedOrbScene() {
    val w = size.width
    val h = size.height
    val bubbleCenter = Offset(w * 0.46f, h * 0.44f)
    val bubbleR = w * 0.26f

    drawCircle(
        Brush.radialGradient(
            listOf(TintLavender, TintPink, Color.Transparent),
            center = bubbleCenter,
            radius = bubbleR * 2.1f,
        ),
        radius = bubbleR * 2.1f,
        center = bubbleCenter,
    )

    // Glass petals cupping the bubble from both sides — the mock's left
    // cluster tips toward 8-10 o'clock, its right toward 2-4 o'clock, and the
    // pivot hides behind the bubble's base.
    val fanPivot = Offset(w * 0.47f, h * 0.78f)
    listOf(-150f, -115f, -85f, 85f, 115f, 150f).forEachIndexed { index, angle ->
        drawPetal(
            fanPivot, angle, w * (0.34f - (index % 2) * 0.05f), 0.5f,
            if (index % 2 == 0) PetalLavender else PetalBlush,
            WashWhite, alpha = 0.85f,
        )
    }
    listOf(-60f, 0f, 60f).forEach { angle ->
        drawPetal(fanPivot, angle, w * 0.24f, 0.46f, PetalPeach, WashWhite, alpha = 0.9f)
    }

    // The bubble.
    drawCircle(
        Brush.radialGradient(
            listOf(
                WashWhite.copy(alpha = 0.62f),
                Color(0xFFF3E6FB).copy(alpha = 0.34f),
                Color(0xFFE8D9FA).copy(alpha = 0.20f),
                Color(0xFFFFE6F1).copy(alpha = 0.30f),
            ),
            center = Offset(bubbleCenter.x - bubbleR * 0.25f, bubbleCenter.y - bubbleR * 0.3f),
            radius = bubbleR * 1.5f,
        ),
        radius = bubbleR,
        center = bubbleCenter,
    )
    glassRing(bubbleCenter, bubbleR * 0.99f, w * 0.014f, topAlpha = 0.95f, bottomAlpha = 0.45f)
    // Specular crescents, top-left and lower-right, the way the mock lights it.
    drawArc(
        Brush.horizontalGradient(listOf(WashWhite.copy(alpha = 0.95f), Color.Transparent)),
        startAngle = 195f, sweepAngle = 60f, useCenter = false,
        topLeft = Offset(bubbleCenter.x - bubbleR, bubbleCenter.y - bubbleR),
        size = Size(bubbleR * 2f, bubbleR * 2f),
        style = Stroke(width = w * 0.020f),
    )
    drawArc(
        Brush.horizontalGradient(listOf(Color.Transparent, WashWhite.copy(alpha = 0.65f))),
        startAngle = 15f, sweepAngle = 40f, useCenter = false,
        topLeft = Offset(bubbleCenter.x - bubbleR, bubbleCenter.y - bubbleR),
        size = Size(bubbleR * 2f, bubbleR * 2f),
        style = Stroke(width = w * 0.014f),
    )
    // Inner refraction ring.
    glassRing(
        Offset(bubbleCenter.x - bubbleR * 0.08f, bubbleCenter.y - bubbleR * 0.06f),
        bubbleR * 0.82f, w * 0.006f, tint = Color(0xFFF0C6E2), topAlpha = 0.55f, bottomAlpha = 0.15f,
    )

    // The share glyph: two strokes joining three ring nodes, drawn on the
    // bubble the way the mock prints it.
    val glyph = Color(0xFF8F74E2)
    val nodeTop = Offset(bubbleCenter.x + bubbleR * 0.42f, bubbleCenter.y - bubbleR * 0.42f)
    val nodeLeft = Offset(bubbleCenter.x - bubbleR * 0.38f, bubbleCenter.y + bubbleR * 0.05f)
    val nodeBottom = Offset(bubbleCenter.x + bubbleR * 0.34f, bubbleCenter.y + bubbleR * 0.58f)
    val stroke = w * 0.016f
    drawLine(glyph, nodeLeft, nodeTop, strokeWidth = stroke, alpha = 0.95f)
    drawLine(glyph, nodeLeft, nodeBottom, strokeWidth = stroke, alpha = 0.95f)
    listOf(nodeTop, nodeLeft, nodeBottom).forEach { node ->
        drawCircle(WashWhite, bubbleR * 0.14f, node)
        drawCircle(glyph, bubbleR * 0.14f, node, style = Stroke(w * 0.013f))
    }

    // The orbit ring, elliptical, passing behind and in front.
    val orbit = Offset(w * 0.47f, h * 0.47f)
    drawArc(
        Brush.horizontalGradient(listOf(Color.Transparent, WashWhite.copy(alpha = 0.8f), Color.Transparent)),
        startAngle = -15f, sweepAngle = 160f, useCenter = false,
        topLeft = Offset(orbit.x - w * 0.40f, orbit.y - h * 0.155f),
        size = Size(w * 0.80f, h * 0.31f),
        style = Stroke(width = w * 0.005f),
    )
    drawArc(
        Brush.horizontalGradient(listOf(WashWhite.copy(alpha = 0.75f), Color.Transparent)),
        startAngle = 168f, sweepAngle = 150f, useCenter = false,
        topLeft = Offset(orbit.x - w * 0.40f, orbit.y - h * 0.155f),
        size = Size(w * 0.80f, h * 0.31f),
        style = Stroke(width = w * 0.005f),
    )

    // Sparkles: lilac at the left, gold at the top right, white at the right.
    drawStar4(Offset(w * 0.13f, h * 0.30f), w * 0.030f, Color(0xFFB79BEA), 0.95f)
    drawStar4(Offset(w * 0.72f, h * 0.10f), w * 0.026f, Color(0xFFF3D98E), 0.95f)
    drawStar4(Offset(w * 0.86f, h * 0.52f), w * 0.022f, WashWhite, 0.9f)
    drawStar4(Offset(w * 0.24f, h * 0.72f), w * 0.016f, WashWhite, 0.8f)
}
