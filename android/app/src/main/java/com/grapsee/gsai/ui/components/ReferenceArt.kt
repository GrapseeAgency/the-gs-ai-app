package com.grapsee.gsai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.theme.GsTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * GS ARTWORK — a hand-built illustration set, not a generated image set.
 *
 * Every scene is drawn from primitives so it shares one light source (upper
 * left), one material (peach glass with a white edge), and one motif (the
 * five-petal bloom). That is what makes a set read as a single illustrator's
 * work rather than ten unrelated stock images: the same petal appears in the
 * empty conversation, the offline cloud, the success ring and the memory
 * stack, at different scales.
 *
 * The set is deliberately small and honest. These are the states the product
 * actually has; nothing here is decoration for a screen that does not exist.
 */
enum class RefArt {
    /** The companion: petals + lit sphere. Used on onboarding and About. */
    Bloom,

    /** Empty conversation: two speech petals, one speaking, one listening. */
    Conversation,

    /** No search results: a lens with a blossom caught inside. */
    Search,

    /** Offline: a soft cloud with a sleeping crescent. */
    Offline,

    /** Error: a petal with a repaired crack and a warm core. */
    Error,

    /** Library / saved: a small stack of glass cards. */
    Library,

    /** Voice: a five-bar waveform held in a ring. */
    Voice,

    /** Success: a bloom whose centre is a check. */
    Success,

    /** Privacy: a shield grown from a petal. */
    Privacy,

    /** Empty archive: a box with a folded petal. */
    Archive,

    /** Notifications: a glass bell with a petal clapper and two quiet rings. */
    Notifications,

    /** Video: a stack of silk film cards, a glass lens, and rising bubbles. */
    Video,

    /** Mind Guide: a clarity lens with a bloom embryo caught inside. */
    Clarity,

    /** Today's reflection: a lotus resting on still water, mirrored below. */
    Reflection,

    /** Pearlescent light: overlapping glass spheres, the reference's hero art. */
    Pearl,

}

@Composable
fun RefArtwork(
    art: RefArt,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    contentDescription: String = "GS illustration",
) {
    val colors = GsTheme.colors
    val accent = colors.accent
    val strong = colors.accentStrong
    val pale = colors.aurora.firstOrNull() ?: colors.accent
    val ink = colors.textPrimary
    Canvas(
        modifier = modifier
            .size(size)
            .semantics {
                role = Role.Image
                this.contentDescription = contentDescription
            }
    ) {
        when (art) {
            RefArt.Bloom -> drawBloomScene(accent, strong, pale, colors.onAccent)
            RefArt.Conversation -> drawConversationScene(accent, strong, pale, colors.glassSurface, colors.glassBorder)
            RefArt.Search -> drawSearchScene(accent, strong, pale)
            RefArt.Offline -> drawOfflineScene(accent, strong, pale)
            RefArt.Error -> drawErrorScene(colors.error, accent, pale)
            RefArt.Library -> drawLibraryScene(accent, strong, pale)
            RefArt.Voice -> drawVoiceScene(accent, strong, pale)
            RefArt.Success -> drawSuccessScene(accent, strong, pale)
            RefArt.Privacy -> drawPrivacyScene(accent, strong, pale)
            RefArt.Archive -> drawArchiveScene(accent, strong, pale, ink)
            RefArt.Notifications -> drawNotificationsScene(accent, strong, pale, ink)
            RefArt.Video -> drawVideoScene(accent, strong, pale)
            RefArt.Clarity -> drawClarityScene(accent, strong, pale, ink)
            RefArt.Reflection -> drawReflectionScene(accent, strong, pale)
            RefArt.Pearl -> drawPearlScene()
            RefArt.Lotus -> drawLotusScene(accent, strong, pale)
        }
    }
}

// ---------------------------------------------------------------------------
// Scenes
// ---------------------------------------------------------------------------

private fun DrawScope.drawBloomScene(accent: Color, strong: Color, pale: Color, onAccent: Color) {
    val c = Offset(size.width / 2f, size.height * 0.52f)
    val r = size.minDimension * 0.24f
    // soft ground shadow
    drawOval(
        brush = Brush.radialGradient(
            listOf(Color(0x22000000), Color.Transparent),
            center = Offset(c.x, c.y + r * 2.4f),
            radius = r * 1.8f
        ),
        topLeft = Offset(c.x - r * 1.5f, c.y + r * 1.9f),
        size = Size(r * 3f, r * 0.9f)
    )
    // nine petals, alternating length
    repeat(9) { i ->
        val angle = i * 40f - 90f
        val len = if (i % 2 == 0) r * 1.85f else r * 1.48f
        drawPetal(
            center = c,
            angle = angle,
            length = len,
            width = r * 0.55f,
            top = pale.copy(alpha = 0.85f),
            bottom = accent.copy(alpha = 0.55f)
        )
    }
    // glass sphere
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.9f), pale, accent, strong),
            center = Offset(c.x - r * 0.35f, c.y - r * 0.45f),
            radius = r * 1.55f
        ),
        radius = r,
        center = c
    )
    drawCircle(Color.White.copy(alpha = 0.5f), radius = r * 0.92f, center = c, style = Stroke(width = 1.2.dp.toPx()))
    // specular
    drawOval(
        color = Color.White.copy(alpha = 0.65f),
        topLeft = Offset(c.x - r * 0.58f, c.y - r * 0.66f),
        size = Size(r * 0.62f, r * 0.34f)
    )
    drawCircle(onAccent.copy(alpha = 0.35f), radius = r * 0.10f, center = Offset(c.x + r * 0.3f, c.y + r * 0.34f))
    // orbit
    drawArc(
        color = accent.copy(alpha = 0.45f),
        startAngle = 200f,
        sweepAngle = 250f,
        useCenter = false,
        topLeft = Offset(c.x - r * 1.55f, c.y - r * 1.55f),
        size = Size(r * 3.1f, r * 3.1f),
        style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
    )
    drawCircle(strong, radius = r * 0.09f, center = Offset(c.x + r * 1.42f, c.y - r * 0.42f))
    repeat(3) { i ->
        val a = Math.toRadians((i * 120 + 25).toDouble())
        drawCircle(
            Color.White.copy(alpha = 0.75f),
            radius = r * 0.045f,
            center = Offset(c.x + (cos(a) * r * 1.85f).toFloat(), c.y + (sin(a) * r * 1.55f).toFloat())
        )
    }
}

private fun DrawScope.drawConversationScene(
    accent: Color,
    strong: Color,
    pale: Color,
    glass: Color,
    hairline: Color,
) {
    val w = size.width
    val h = size.height
    // back bubble — the listening side, glass
    val back = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = w * 0.10f,
                top = h * 0.24f,
                right = w * 0.66f,
                bottom = h * 0.62f,
                radiusX = w * 0.12f,
                radiusY = w * 0.12f
            )
        )
        moveTo(w * 0.22f, h * 0.60f)
        lineTo(w * 0.15f, h * 0.71f)
        lineTo(w * 0.34f, h * 0.62f)
        close()
    }
    drawPath(back, glass.copy(alpha = 0.75f))
    drawPath(back, hairline, style = Stroke(width = 1.dp.toPx()))
    repeat(3) { i ->
        drawRoundRect(
            color = accent.copy(alpha = 0.28f),
            topLeft = Offset(w * 0.18f, h * (0.33f + i * 0.08f)),
            size = Size(w * (0.34f - i * 0.07f), h * 0.035f),
            cornerRadius = CornerRadius(h * 0.02f)
        )
    }
    // front bubble — the speaking side, filled
    val front = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = w * 0.36f,
                top = h * 0.42f,
                right = w * 0.92f,
                bottom = h * 0.78f,
                radiusX = w * 0.12f,
                radiusY = w * 0.12f
            )
        )
        moveTo(w * 0.78f, h * 0.77f)
        lineTo(w * 0.86f, h * 0.86f)
        lineTo(w * 0.68f, h * 0.78f)
        close()
    }
    drawPath(
        front,
        Brush.linearGradient(
            listOf(accent, strong),
            start = Offset(w * 0.36f, h * 0.42f),
            end = Offset(w * 0.92f, h * 0.78f)
        )
    )
    repeat(2) { i ->
        drawRoundRect(
            color = Color.White.copy(alpha = 0.72f),
            topLeft = Offset(w * 0.45f, h * (0.52f + i * 0.09f)),
            size = Size(w * (0.34f - i * 0.10f), h * 0.035f),
            cornerRadius = CornerRadius(h * 0.02f)
        )
    }
    // a blossom caught between them
    drawPetalBurst(
        center = Offset(w * 0.70f, h * 0.30f),
        radius = w * 0.085f,
        light = Color.White.copy(alpha = 0.92f),
        deep = pale,
        petals = 5,
        tilt = 12f
    )
    drawCircle(Color.White.copy(alpha = 0.7f), radius = w * 0.012f, center = Offset(w * 0.70f, h * 0.30f))
}

private fun DrawScope.drawSearchScene(accent: Color, strong: Color, pale: Color) {
    val w = size.width
    val h = size.height
    val lens = Offset(w * 0.46f, h * 0.44f)
    val r = w * 0.26f
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.85f), pale.copy(alpha = 0.55f), accent.copy(alpha = 0.12f)),
            center = Offset(lens.x - r * 0.3f, lens.y - r * 0.35f),
            radius = r * 1.4f
        ),
        radius = r,
        center = lens
    )
    drawCircle(Color.White.copy(alpha = 0.6f), radius = r, center = lens, style = Stroke(width = 2.dp.toPx()))
    drawCircle(accent.copy(alpha = 0.5f), radius = r * 0.94f, center = lens, style = Stroke(width = 1.dp.toPx()))
    // handle
    drawLine(
        color = strong,
        start = Offset(lens.x + r * 0.72f, lens.y + r * 0.72f),
        end = Offset(lens.x + r * 1.42f, lens.y + r * 1.40f),
        strokeWidth = w * 0.055f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color.White.copy(alpha = 0.4f),
        start = Offset(lens.x + r * 0.78f, lens.y + r * 0.62f),
        end = Offset(lens.x + r * 1.25f, lens.y + r * 1.18f),
        strokeWidth = w * 0.016f,
        cap = StrokeCap.Round
    )
    drawPetalBurst(
        center = Offset(lens.x - r * 0.12f, lens.y - r * 0.05f),
        radius = r * 0.52f,
        light = Color.White.copy(alpha = 0.98f),
        deep = accent,
        petals = 5,
        tilt = 22f
    )
    drawCircle(strong, radius = r * 0.10f, center = Offset(lens.x - r * 0.12f, lens.y - r * 0.05f))
    drawSparkle(Offset(w * 0.78f, h * 0.20f), w * 0.035f, Color.White.copy(alpha = 0.9f))
    drawSparkle(Offset(w * 0.20f, h * 0.78f), w * 0.022f, accent.copy(alpha = 0.55f))
}

private fun DrawScope.drawOfflineScene(accent: Color, strong: Color, pale: Color) {
    val w = size.width
    val h = size.height
    // sleeping crescent behind the cloud
    drawCircle(
        brush = Brush.radialGradient(listOf(pale.copy(alpha = 0.9f), pale.copy(alpha = 0f))),
        radius = w * 0.22f,
        center = Offset(w * 0.70f, h * 0.30f)
    )
    drawCrescent(Offset(w * 0.72f, h * 0.29f), w * 0.115f, Color.White.copy(alpha = 0.85f))
    // cloud: four lobes + base
    val baseY = h * 0.64f
    val body = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = w * 0.16f,
                top = h * 0.48f,
                right = w * 0.84f,
                bottom = baseY,
                radiusX = w * 0.10f,
                radiusY = w * 0.10f
            )
        )
    }
    drawPath(body, pale.copy(alpha = 0.9f))
    drawCircle(pale.copy(alpha = 0.92f), radius = w * 0.14f, center = Offset(w * 0.34f, h * 0.47f))
    drawCircle(pale.copy(alpha = 0.94f), radius = w * 0.18f, center = Offset(w * 0.52f, h * 0.40f))
    drawCircle(pale.copy(alpha = 0.90f), radius = w * 0.12f, center = Offset(w * 0.68f, h * 0.49f))
    // highlight
    drawCircle(Color.White.copy(alpha = 0.35f), radius = w * 0.10f, center = Offset(w * 0.48f, h * 0.37f))
    // little z z z
    val zColor = strong.copy(alpha = 0.65f)
    drawTextZ(Offset(w * 0.80f, h * 0.16f), w * 0.045f, zColor)
    drawTextZ(Offset(w * 0.87f, h * 0.10f), w * 0.032f, zColor.copy(alpha = 0.5f))
    // a single dropped petal
    drawPetal(
        center = Offset(w * 0.30f, h * 0.80f),
        angle = 38f,
        length = w * 0.10f,
        width = w * 0.04f,
        top = Color.White.copy(alpha = 0.9f),
        bottom = accent.copy(alpha = 0.6f)
    )
}

private fun DrawScope.drawErrorScene(error: Color, accent: Color, pale: Color) {
    val w = size.width
    val h = size.height
    val c = Offset(w * 0.50f, h * 0.50f)
    val r = w * 0.30f
    // halo
    drawCircle(
        brush = Brush.radialGradient(listOf(error.copy(alpha = 0.22f), Color.Transparent)),
        radius = r * 1.8f,
        center = c
    )
    // petal shield
    val shape = Path().apply {
        moveTo(c.x, c.y - r)
        cubicTo(c.x + r * 1.05f, c.y - r * 0.55f, c.x + r * 0.65f, c.y + r * 0.65f, c.x, c.y + r)
        cubicTo(c.x - r * 0.65f, c.y + r * 0.65f, c.x - r * 1.05f, c.y - r * 0.55f, c.x, c.y - r)
        close()
    }
    drawPath(shape, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.9f), pale.copy(alpha = 0.65f)), start = Offset(c.x, c.y - r), end = Offset(c.x, c.y + r)))
    drawPath(shape, error.copy(alpha = 0.55f), style = Stroke(width = 1.6.dp.toPx()))
    // crack, repaired in gold
    val crack = Path().apply {
        moveTo(c.x - r * 0.30f, c.y - r * 0.55f)
        lineTo(c.x - r * 0.10f, c.y - r * 0.18f)
        lineTo(c.x + r * 0.16f, c.y - r * 0.30f)
        lineTo(c.x + r * 0.02f, c.y + r * 0.06f)
        lineTo(c.x + r * 0.28f, c.y + r * 0.18f)
    }
    drawPath(crack, accent.copy(alpha = 0.75f), style = Stroke(width = w * 0.018f, cap = StrokeCap.Round))
    drawCircle(accent, radius = r * 0.18f, center = c)
    drawCircle(Color.White.copy(alpha = 0.8f), radius = r * 0.07f, center = Offset(c.x - r * 0.05f, c.y - r * 0.05f))
    drawSparkle(Offset(w * 0.78f, h * 0.26f), w * 0.03f, accent.copy(alpha = 0.55f))
}

private fun DrawScope.drawLibraryScene(accent: Color, strong: Color, pale: Color) {
    val w = size.width
    val h = size.height
    val cardW = w * 0.56f
    val cardH = h * 0.36f
    repeat(3) { i ->
        val dx = (i - 1) * w * 0.045f
        val dy = (1 - i) * h * 0.055f
        rotate(degrees = (i - 1) * 6f, pivot = Offset(w * 0.5f, h * 0.55f)) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.52f + i * 0.10f),
                topLeft = Offset(w * 0.22f + dx, h * 0.38f + dy),
                size = Size(cardW, cardH),
                cornerRadius = CornerRadius(w * 0.06f)
            )
            drawRoundRect(
                color = pale.copy(alpha = 0.5f),
                topLeft = Offset(w * 0.22f + dx, h * 0.38f + dy),
                size = Size(cardW, cardH),
                cornerRadius = CornerRadius(w * 0.06f),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
    // top card detail
    drawRoundRect(
        color = accent.copy(alpha = 0.55f),
        topLeft = Offset(w * 0.30f, h * 0.40f),
        size = Size(w * 0.16f, h * 0.035f),
        cornerRadius = CornerRadius(h * 0.02f)
    )
    repeat(2) { i ->
        drawRoundRect(
            color = strong.copy(alpha = 0.28f),
            topLeft = Offset(w * 0.30f, h * (0.47f + i * 0.06f)),
            size = Size(w * (0.32f - i * 0.08f), h * 0.022f),
            cornerRadius = CornerRadius(h * 0.012f)
        )
    }
    drawPetalBurst(
        center = Offset(w * 0.66f, h * 0.66f),
        radius = w * 0.09f,
        light = Color.White.copy(alpha = 0.95f),
        deep = accent,
        petals = 5,
        tilt = 8f
    )
    drawCircle(strong, radius = w * 0.018f, center = Offset(w * 0.66f, h * 0.66f))
}

private fun DrawScope.drawVoiceScene(accent: Color, strong: Color, pale: Color) {
    val w = size.width
    val h = size.height
    val c = Offset(w * 0.50f, h * 0.52f)
    val r = w * 0.30f
    drawCircle(
        brush = Brush.radialGradient(listOf(pale.copy(alpha = 0.75f), Color.Transparent)),
        radius = r * 1.6f,
        center = c
    )
    drawCircle(Color.White.copy(alpha = 0.55f), radius = r, center = c, style = Stroke(width = 1.4.dp.toPx()))
    drawArc(
        color = accent.copy(alpha = 0.55f),
        startAngle = -40f,
        sweepAngle = 250f,
        useCenter = false,
        topLeft = Offset(c.x - r * 1.22f, c.y - r * 1.22f),
        size = Size(r * 2.44f, r * 2.44f),
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
    )
    val heights = listOf(0.32f, 0.62f, 0.92f, 0.54f, 0.38f)
    heights.forEachIndexed { i, factor ->
        val x = c.x + (i - 2) * r * 0.26f
        val barH = r * factor
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.95f), accent, strong),
                startY = c.y - barH,
                endY = c.y + barH
            ),
            topLeft = Offset(x - r * 0.06f, c.y - barH / 2f),
            size = Size(r * 0.12f, barH),
            cornerRadius = CornerRadius(r * 0.06f)
        )
    }
    drawCircle(strong, radius = w * 0.02f, center = Offset(c.x + r * 1.18f, c.y - r * 0.62f))
    drawSparkle(Offset(w * 0.80f, h * 0.24f), w * 0.025f, Color.White.copy(alpha = 0.85f))
}

private fun DrawScope.drawSuccessScene(accent: Color, strong: Color, pale: Color) {
    val w = size.width
    val h = size.height
    val c = Offset(w * 0.50f, h * 0.50f)
    val r = w * 0.30f
    repeat(8) { i ->
        drawPetal(
            center = c,
            angle = i * 45f - 90f,
            length = r * (if (i % 2 == 0) 1.55f else 1.28f),
            width = r * 0.38f,
            top = pale.copy(alpha = 0.45f),
            bottom = accent.copy(alpha = 0.22f)
        )
    }
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.9f), pale.copy(alpha = 0.65f)),
            center = Offset(c.x - r * 0.25f, c.y - r * 0.3f),
            radius = r * 1.4f
        ),
        radius = r,
        center = c
    )
    drawCircle(accent.copy(alpha = 0.55f), radius = r, center = c, style = Stroke(width = 1.5.dp.toPx()))
    val check = Path().apply {
        moveTo(c.x - r * 0.42f, c.y + r * 0.02f)
        lineTo(c.x - r * 0.08f, c.y + r * 0.34f)
        lineTo(c.x + r * 0.46f, c.y - r * 0.34f)
    }
    drawPath(check, strong, style = Stroke(width = w * 0.045f, cap = StrokeCap.Round))
    drawSparkle(Offset(w * 0.20f, h * 0.22f), w * 0.03f, accent.copy(alpha = 0.45f))
}

private fun DrawScope.drawPrivacyScene(accent: Color, strong: Color, pale: Color) {
    val w = size.width
    val h = size.height
    val c = Offset(w * 0.50f, h * 0.48f)
    val r = w * 0.28f
    drawCircle(
        brush = Brush.radialGradient(listOf(pale.copy(alpha = 0.55f), Color.Transparent)),
        radius = r * 1.7f,
        center = c
    )
    val shield = Path().apply {
        moveTo(c.x, c.y - r * 1.05f)
        cubicTo(c.x + r * 1.0f, c.y - r * 0.72f, c.x + r * 0.92f, c.y + r * 0.30f, c.x, c.y + r * 1.05f)
        cubicTo(c.x - r * 0.92f, c.y + r * 0.30f, c.x - r * 1.0f, c.y - r * 0.72f, c.x, c.y - r * 1.05f)
        close()
    }
    drawPath(
        shield,
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.88f), pale.copy(alpha = 0.55f)),
            start = Offset(c.x, c.y - r),
            end = Offset(c.x, c.y + r)
        )
    )
    drawPath(shield, accent.copy(alpha = 0.5f), style = Stroke(width = 1.6.dp.toPx()))
    drawPetalBurst(
        center = Offset(c.x, c.y - r * 0.05f),
        radius = r * 0.44f,
        light = Color.White.copy(alpha = 0.95f),
        deep = accent,
        petals = 5,
        tilt = 16f
    )
    drawCircle(strong, radius = r * 0.42f, center = Offset(c.x, c.y - r * 0.05f))
    drawCircle(Color.White.copy(alpha = 0.9f), radius = r * 0.16f, center = Offset(c.x, c.y - r * 0.05f))
    drawRoundRect(
        color = strong,
        topLeft = Offset(c.x - r * 0.10f, c.y + r * 0.02f),
        size = Size(r * 0.20f, r * 0.34f),
        cornerRadius = CornerRadius(r * 0.08f)
    )
}

private fun DrawScope.drawArchiveScene(accent: Color, strong: Color, pale: Color, ink: Color) {
    val w = size.width
    val h = size.height
    // lid
    drawRoundRect(
        brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.9f), pale.copy(alpha = 0.7f))),
        topLeft = Offset(w * 0.18f, h * 0.34f),
        size = Size(w * 0.64f, h * 0.16f),
        cornerRadius = CornerRadius(w * 0.05f)
    )
    // box
    drawRoundRect(
        brush = Brush.linearGradient(listOf(pale.copy(alpha = 0.75f), accent.copy(alpha = 0.55f))),
        topLeft = Offset(w * 0.22f, h * 0.48f),
        size = Size(w * 0.56f, h * 0.30f),
        cornerRadius = CornerRadius(w * 0.04f)
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.55f),
        topLeft = Offset(w * 0.22f, h * 0.48f),
        size = Size(w * 0.56f, h * 0.30f),
        cornerRadius = CornerRadius(w * 0.04f),
        style = Stroke(width = 1.dp.toPx())
    )
    // card peeking out
    rotate(degrees = -7f, pivot = Offset(w * 0.50f, h * 0.42f)) {
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(w * 0.30f, h * 0.28f),
            size = Size(w * 0.40f, h * 0.18f),
            cornerRadius = CornerRadius(w * 0.035f)
        )
        drawRoundRect(
            color = accent.copy(alpha = 0.45f),
            topLeft = Offset(w * 0.35f, h * 0.33f),
            size = Size(w * 0.20f, h * 0.018f),
            cornerRadius = CornerRadius(h * 0.009f)
        )
        drawRoundRect(
            color = strong.copy(alpha = 0.25f),
            topLeft = Offset(w * 0.35f, h * 0.37f),
            size = Size(w * 0.28f, h * 0.014f),
            cornerRadius = CornerRadius(h * 0.007f)
        )
    }
    drawCircle(strong, radius = w * 0.022f, center = Offset(w * 0.50f, h * 0.60f))
    drawPetal(
        center = Offset(w * 0.74f, h * 0.74f),
        angle = 52f,
        length = w * 0.09f,
        width = w * 0.035f,
        top = Color.White.copy(alpha = 0.85f),
        bottom = accent.copy(alpha = 0.5f)
    )
}

private fun DrawScope.drawNotificationsScene(accent: Color, strong: Color, pale: Color, ink: Color) {
    val w = size.width
    val h = size.height
    val c = Offset(w * 0.50f, h * 0.52f)
    val r = w * 0.26f
    drawCircle(
        brush = Brush.radialGradient(listOf(pale.copy(alpha = 0.55f), Color.Transparent)),
        radius = r * 1.9f,
        center = c
    )
    // two quiet rings, one on each side
    drawArc(
        color = accent.copy(alpha = 0.42f),
        startAngle = 150f,
        sweepAngle = 60f,
        useCenter = false,
        topLeft = Offset(c.x - r * 1.9f, c.y - r * 0.9f),
        size = Size(r * 1.5f, r * 1.8f),
        style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
    )
    drawArc(
        color = accent.copy(alpha = 0.42f),
        startAngle = -30f,
        sweepAngle = 60f,
        useCenter = false,
        topLeft = Offset(c.x + r * 0.4f, c.y - r * 0.9f),
        size = Size(r * 1.5f, r * 1.8f),
        style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
    )
    // handle
    drawCircle(
        color = strong.copy(alpha = 0.75f),
        radius = r * 0.16f,
        center = Offset(c.x, c.y - r * 0.98f),
        style = Stroke(width = r * 0.12f)
    )
    // bell body
    val bell = Path().apply {
        moveTo(c.x - r * 0.80f, c.y + r * 0.46f)
        cubicTo(
            c.x - r * 0.88f, c.y - r * 0.30f,
            c.x - r * 0.52f, c.y - r * 0.82f,
            c.x, c.y - r * 0.86f
        )
        cubicTo(
            c.x + r * 0.52f, c.y - r * 0.82f,
            c.x + r * 0.88f, c.y - r * 0.30f,
            c.x + r * 0.80f, c.y + r * 0.46f
        )
        close()
    }
    drawPath(
        bell,
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.92f), pale.copy(alpha = 0.72f), accent.copy(alpha = 0.55f)),
            startY = c.y - r,
            endY = c.y + r * 0.5f
        )
    )
    drawPath(bell, accent.copy(alpha = 0.55f), style = Stroke(width = 1.5.dp.toPx()))
    // lip
    drawRoundRect(
        brush = Brush.horizontalGradient(listOf(accent, strong, accent)),
        topLeft = Offset(c.x - r * 0.98f, c.y + r * 0.40f),
        size = Size(r * 1.96f, r * 0.22f),
        cornerRadius = CornerRadius(r * 0.11f)
    )
    // clapper
    drawCircle(strong, radius = r * 0.20f, center = Offset(c.x, c.y + r * 0.80f))
    drawCircle(Color.White.copy(alpha = 0.85f), radius = r * 0.075f, center = Offset(c.x - r * 0.05f, c.y + r * 0.76f))
    // highlight
    drawOval(
        color = Color.White.copy(alpha = 0.55f),
        topLeft = Offset(c.x - r * 0.46f, c.y - r * 0.58f),
        size = Size(r * 0.30f, r * 0.52f)
    )
    drawPetal(
        center = Offset(w * 0.22f, h * 0.76f),
        angle = -48f,
        length = w * 0.10f,
        width = w * 0.038f,
        top = Color.White.copy(alpha = 0.88f),
        bottom = accent.copy(alpha = 0.5f)
    )
}

// ---------------------------------------------------------------------------
// Bento scenes — the three product tools, drawn with the same light
// ---------------------------------------------------------------------------

/**
 * Pearl: overlapping glass spheres lit from the upper left, with an iridescent
 * wash and a silk thread. This is the reference's hero material — the light
 * through a pearl, not a photograph — and it is drawn from the same primitives
 * as the rest of the set so it never reads as a sticker.
 */
private fun DrawScope.drawPearlScene() {
    val w = size.width
    val h = size.height

    drawPearl(
        center = Offset(w * 0.54f, h * 0.44f),
        radius = w * 0.30f,
        warm = Color(0xFFFFD3BC),
        cool = Color(0xFFC9B6FF),
        blush = Color(0xFFFFB3C7)
    )
    drawPearl(
        center = Offset(w * 0.80f, h * 0.68f),
        radius = w * 0.17f,
        warm = Color(0xFFFFE6D6),
        cool = Color(0xFFB9C8FF),
        blush = Color(0xFFFFC4D8)
    )
    drawPearl(
        center = Offset(w * 0.27f, h * 0.71f),
        radius = w * 0.12f,
        warm = Color(0xFFFFF0E5),
        cool = Color(0xFFD6C4FF),
        blush = Color(0xFFFFCDB9)
    )
    // Two more lobes and a haze keep the cluster reading as one mass of light
    // rather than three separate balls.
    drawPearl(
        center = Offset(w * 0.63f, h * 0.80f),
        radius = w * 0.13f,
        warm = Color(0xFFFFE1D4),
        cool = Color(0xFFE4CBFF),
        blush = Color(0xFFFFB9CE)
    )
    drawPearl(
        center = Offset(w * 0.38f, h * 0.30f),
        radius = w * 0.10f,
        warm = Color(0xFFFFF4EC),
        cool = Color(0xFFD9CEFF),
        blush = Color(0xFFFFD3DE)
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                Color(0xFFFFD9EC).copy(alpha = 0.32f),
                Color(0xFFCFC2FF).copy(alpha = 0.18f),
                Color.Transparent
            ),
            center = Offset(w * 0.58f, h * 0.52f),
            radius = w * 0.48f
        ),
        radius = w * 0.48f,
        center = Offset(w * 0.58f, h * 0.52f)
    )
    // A single silk thread crossing the large pearl, as in the reference.
    val silk = Path().apply {
        moveTo(w * 0.10f, h * 0.30f)
        cubicTo(w * 0.42f, h * 0.12f, w * 0.58f, h * 0.78f, w * 0.94f, h * 0.56f)
    }
    drawPath(
        silk,
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.55f), Color(0xFFFFD9E4).copy(alpha = 0.35f)),
            start = Offset(w * 0.1f, h * 0.3f),
            end = Offset(w * 0.94f, h * 0.56f)
        ),
        style = Stroke(width = w * 0.018f, cap = StrokeCap.Round)
    )
}

/** One pearl: soft contact shadow, iridescent body, rim and specular. */
private fun DrawScope.drawPearl(
    center: Offset,
    radius: Float,
    warm: Color,
    cool: Color,
    blush: Color,
) {
    drawOval(
        brush = Brush.radialGradient(
            listOf(Color(0x1C6B3B2E), Color.Transparent),
            center = Offset(center.x, center.y + radius * 1.05f),
            radius = radius * 1.1f
        ),
        topLeft = Offset(center.x - radius * 0.95f, center.y + radius * 0.82f),
        size = Size(radius * 1.9f, radius * 0.5f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                Color.White.copy(alpha = 0.92f),
                warm,
                blush,
                cool,
                cool.copy(alpha = 0.55f)
            ),
            center = Offset(center.x - radius * 0.34f, center.y - radius * 0.40f),
            radius = radius * 1.55f
        ),
        radius = radius,
        center = center
    )
    // Rim: strongest where the light leaves the sphere.
    drawArc(
        color = Color.White.copy(alpha = 0.55f),
        startAngle = 150f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = Offset(center.x - radius * 0.94f, center.y - radius * 0.94f),
        size = Size(radius * 1.88f, radius * 1.88f),
        style = Stroke(width = radius * 0.045f, cap = StrokeCap.Round)
    )
    drawOval(
        color = Color.White.copy(alpha = 0.60f),
        topLeft = Offset(center.x - radius * 0.46f, center.y - radius * 0.62f),
        size = Size(radius * 0.46f, radius * 0.26f)
    )
}

/**
 * Video: silk ribbons thread a stack of translucent film cards. The aperture
 * is a real lens with a specular, and the bubbles carry the bloom motif, so
 * the card reads as the same world as the rest of the set rather than as a
 * generic "media" glyph.
 */
private fun DrawScope.drawVideoScene(accent: Color, strong: Color, pale: Color) {
    val w = size.width
    val h = size.height
    val cx = w * 0.52f
    val cy = h * 0.46f

    // One light source: a warm pool behind the stack, upper left.
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.60f), Color.Transparent),
            center = Offset(w * 0.26f, h * 0.18f),
            radius = w * 0.55f
        ),
        radius = w * 0.55f,
        center = Offset(w * 0.26f, h * 0.18f)
    )

    // Silk that passes behind the stack — depth without a drop shadow.
    val backRibbon = Path().apply {
        moveTo(-w * 0.04f, h * 0.78f)
        cubicTo(w * 0.24f, h * 0.44f, w * 0.46f, h * 0.98f, w * 1.04f, h * 0.52f)
    }
    drawPath(
        backRibbon,
        Brush.linearGradient(
            listOf(pale.copy(alpha = 0.55f), accent.copy(alpha = 0.22f)),
            start = Offset(0f, h * 0.74f),
            end = Offset(w, h * 0.54f)
        ),
        style = Stroke(width = w * 0.085f, cap = StrokeCap.Round)
    )

    // Back film card, tilted away from the light.
    rotate(degrees = -9f, pivot = Offset(cx, cy)) {
        val left = cx - w * 0.27f
        val top = cy - h * 0.24f
        val cw = w * 0.54f
        val ch = h * 0.47f
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.78f), pale.copy(alpha = 0.50f), accent.copy(alpha = 0.32f)),
                start = Offset(left, top),
                end = Offset(left + cw, top + ch)
            ),
            topLeft = Offset(left, top),
            size = Size(cw, ch),
            cornerRadius = CornerRadius(w * 0.055f)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.50f),
            topLeft = Offset(left, top),
            size = Size(cw, ch),
            cornerRadius = CornerRadius(w * 0.055f),
            style = Stroke(width = 1.2.dp.toPx())
        )
    }

    // Front film card, catching the specular.
    rotate(degrees = 4f, pivot = Offset(cx, cy + h * 0.05f)) {
        val left = cx - w * 0.29f
        val top = cy - h * 0.17f
        val cw = w * 0.58f
        val ch = h * 0.43f
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.94f), pale.copy(alpha = 0.60f), accent.copy(alpha = 0.38f)),
                start = Offset(left, top),
                end = Offset(left + cw, top + ch)
            ),
            topLeft = Offset(left, top),
            size = Size(cw, ch),
            cornerRadius = CornerRadius(w * 0.06f)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.72f),
            topLeft = Offset(left, top),
            size = Size(cw, ch),
            cornerRadius = CornerRadius(w * 0.06f),
            style = Stroke(width = 1.4.dp.toPx())
        )
        // The aperture: a real lens, not a flat play badge.
        val lx = left + cw * 0.53f
        val ly = top + ch * 0.47f
        val lr = w * 0.115f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.95f), pale.copy(alpha = 0.70f), accent.copy(alpha = 0.42f)),
                center = Offset(lx - lr * 0.32f, ly - lr * 0.42f),
                radius = lr * 1.5f
            ),
            radius = lr,
            center = Offset(lx, ly)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.75f),
            radius = lr,
            center = Offset(lx, ly),
            style = Stroke(width = 1.2.dp.toPx())
        )
        val tri = Path().apply {
            moveTo(lx - lr * 0.24f, ly - lr * 0.38f)
            lineTo(lx + lr * 0.46f, ly)
            lineTo(lx - lr * 0.24f, ly + lr * 0.38f)
            close()
        }
        drawPath(tri, strong.copy(alpha = 0.88f))
        // Perforations along the film edge.
        repeat(4) { i ->
            drawRoundRect(
                color = Color.White.copy(alpha = 0.48f),
                topLeft = Offset(left + w * 0.020f, top + ch * 0.12f + i * ch * 0.20f),
                size = Size(w * 0.018f, ch * 0.10f),
                cornerRadius = CornerRadius(w * 0.006f)
            )
        }
    }

    // Bubbles rising through the light.
    drawBubble(Offset(w * 0.84f, h * 0.26f), w * 0.075f, pale)
    drawBubble(Offset(w * 0.90f, h * 0.50f), w * 0.045f, accent)
    drawBubble(Offset(w * 0.14f, h * 0.24f), w * 0.034f, pale)
    // Motif: a petal caught inside the largest bubble.
    drawPetal(
        center = Offset(w * 0.84f, h * 0.26f),
        angle = -22f,
        length = w * 0.052f,
        width = w * 0.019f,
        top = Color.White.copy(alpha = 0.92f),
        bottom = strong.copy(alpha = 0.42f)
    )
    drawSparkle(Offset(w * 0.18f, h * 0.80f), w * 0.028f, Color.White.copy(alpha = 0.70f))
}

/**
 * Mind Guide: a clarity lens. Three depths of ring, a bloom embryo held in the
 * glass, and one caustic streak — the visual argument that the tool is a lens
 * you look through, not a magic wand.
 */
private fun DrawScope.drawClarityScene(accent: Color, strong: Color, pale: Color, ink: Color) {
    val c = Offset(size.width * 0.50f, size.height * 0.48f)
    val r = size.minDimension * 0.30f
    val stroke = 1.3.dp.toPx()

    // Ground shadow — warm, never grey.
    drawOval(
        brush = Brush.radialGradient(
            listOf(ink.copy(alpha = 0.10f), Color.Transparent),
            center = Offset(c.x, c.y + r * 1.62f),
            radius = r * 1.20f
        ),
        topLeft = Offset(c.x - r * 1.05f, c.y + r * 1.34f),
        size = Size(r * 2.10f, r * 0.56f)
    )
    // Outer rings, lighter as they recede.
    drawCircle(pale.copy(alpha = 0.34f), radius = r * 1.36f, center = c, style = Stroke(width = stroke))
    drawCircle(accent.copy(alpha = 0.20f), radius = r * 1.14f, center = c, style = Stroke(width = stroke))
    // The lens body.
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.92f), pale.copy(alpha = 0.55f), accent.copy(alpha = 0.28f)),
            center = Offset(c.x - r * 0.34f, c.y - r * 0.42f),
            radius = r * 1.45f
        ),
        radius = r * 0.94f,
        center = c
    )
    // Caustic: a light band refracted through the glass.
    rotate(degrees = -32f, pivot = c) {
        drawOval(
            brush = Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                center = c,
                radius = r * 0.9f
            ),
            topLeft = Offset(c.x - r * 0.86f, c.y - r * 0.16f),
            size = Size(r * 1.72f, r * 0.32f)
        )
    }
    // Bloom embryo inside the lens.
    drawPetalBurst(
        center = c,
        radius = r * 0.50f,
        light = Color.White.copy(alpha = 0.86f),
        deep = accent.copy(alpha = 0.42f),
        petals = 5,
        tilt = -90f
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White, pale, strong),
            center = Offset(c.x - r * 0.06f, c.y - r * 0.08f),
            radius = r * 0.24f
        ),
        radius = r * 0.16f,
        center = c
    )
    // Rim + specular: the glass reads as glass.
    drawCircle(Color.White.copy(alpha = 0.55f), radius = r * 0.86f, center = c, style = Stroke(width = 1.2.dp.toPx()))
    drawOval(
        color = Color.White.copy(alpha = 0.60f),
        topLeft = Offset(c.x - r * 0.56f, c.y - r * 0.66f),
        size = Size(r * 0.52f, r * 0.24f)
    )
    // One satellite, mid-orbit.
    val orbit = Path().apply {
        moveTo(c.x + r * 0.72f, c.y - r * 0.92f)
        cubicTo(c.x + r * 1.30f, c.y - r * 0.70f, c.x + r * 1.34f, c.y + r * 0.30f, c.x + r * 1.02f, c.y + r * 0.86f)
    }
    drawPath(orbit, accent.copy(alpha = 0.40f), style = Stroke(width = stroke, cap = StrokeCap.Round))
    drawCircle(strong, radius = r * 0.085f, center = Offset(c.x + r * 1.30f, c.y - r * 0.44f))
    drawSparkle(Offset(size.width * 0.18f, size.height * 0.22f), r * 0.16f, Color.White.copy(alpha = 0.80f))
}

/**
 * Lotus: three rings of petals opening from one point, lit from above. No
 * water and no ripples — the hero sheet fills the reflection card with petals,
 * so this scene is the flower alone.
 */
private fun DrawScope.drawLotusScene(accent: Color, strong: Color, pale: Color) {
    val w = size.width
    val h = size.height
    val c = Offset(w * 0.50f, h * 0.78f)

    // Outer ring: seven wide petals, nearly white at the tip.
    repeat(7) { i ->
        drawPetal(
            center = c,
            angle = (i - 3) * 24f,
            length = h * 0.50f - kotlin.math.abs(i - 3) * h * 0.035f,
            width = w * 0.085f,
            top = Color.White.copy(alpha = 0.92f),
            bottom = pale.copy(alpha = 0.55f)
        )
    }
    // Middle ring: five petals, warmer.
    repeat(5) { i ->
        drawPetal(
            center = c,
            angle = (i - 2) * 21f,
            length = h * 0.40f - kotlin.math.abs(i - 2) * h * 0.030f,
            width = w * 0.070f,
            top = Color.White.copy(alpha = 0.96f),
            bottom = accent.copy(alpha = 0.60f)
        )
    }
    // Inner ring: three upright petals carrying the blush.
    repeat(3) { i ->
        drawPetal(
            center = c,
            angle = (i - 1) * 15f,
            length = h * 0.28f,
            width = w * 0.055f,
            top = Color.White,
            bottom = strong.copy(alpha = 0.72f)
        )
    }
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White, pale, strong.copy(alpha = 0.55f)),
            center = Offset(c.x - w * 0.012f, c.y - h * 0.030f),
            radius = w * 0.085f
        ),
        radius = w * 0.085f,
        center = Offset(c.x, c.y - h * 0.02f)
    )
}

/**
 * Today's reflection: a lotus on still water. The mirror image below the
 * waterline is drawn quieter and the ripples are wide, which is what makes it
 * read as water rather than as a second flower.
 */
private fun DrawScope.drawReflectionScene(accent: Color, strong: Color, pale: Color) {
    val w = size.width
    val h = size.height
    val waterY = h * 0.56f
    val c = Offset(w * 0.50f, waterY)

    // Water disc.
    drawOval(
        brush = Brush.verticalGradient(
            listOf(pale.copy(alpha = 0.50f), accent.copy(alpha = 0.26f), Color.Transparent),
            startY = waterY - h * 0.10f,
            endY = waterY + h * 0.34f
        ),
        topLeft = Offset(w * 0.06f, waterY - h * 0.10f),
        size = Size(w * 0.88f, h * 0.34f)
    )
    // Mirror image: the same petals, inverted and quieter.
    repeat(5) { i ->
        val spread = (i - 2) * 22f
        drawPetal(
            center = c,
            angle = 180f + spread,
            length = h * 0.15f,
            width = w * 0.040f,
            top = accent.copy(alpha = 0.22f),
            bottom = accent.copy(alpha = 0.06f)
        )
    }
    // Ripples: two widening rings on the surface.
    drawOval(
        color = Color.White.copy(alpha = 0.45f),
        topLeft = Offset(c.x - w * 0.30f, c.y - h * 0.045f),
        size = Size(w * 0.60f, h * 0.09f),
        style = Stroke(width = 1.2.dp.toPx())
    )
    drawOval(
        color = accent.copy(alpha = 0.22f),
        topLeft = Offset(c.x - w * 0.42f, c.y - h * 0.035f),
        size = Size(w * 0.84f, h * 0.07f),
        style = Stroke(width = 1.dp.toPx())
    )
    // Waterline, brightest where the flower meets the water.
    drawLine(
        brush = Brush.horizontalGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = 0.65f), Color.Transparent),
            startX = c.x - w * 0.44f,
            endX = c.x + w * 0.44f
        ),
        start = Offset(c.x - w * 0.44f, waterY),
        end = Offset(c.x + w * 0.44f, waterY),
        strokeWidth = 1.4.dp.toPx()
    )
    // The lotus: five petals fanned upward from the waterline.
    repeat(5) { i ->
        val spread = (i - 2) * 24f
        drawPetal(
            center = c,
            angle = spread,
            length = h * 0.20f - kotlin.math.abs(i - 2) * h * 0.022f,
            width = w * 0.048f,
            top = Color.White.copy(alpha = 0.88f),
            bottom = accent.copy(alpha = 0.50f)
        )
    }
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White, pale, strong),
            center = Offset(c.x - w * 0.015f, c.y - h * 0.020f),
            radius = w * 0.070f
        ),
        radius = w * 0.048f,
        center = Offset(c.x, c.y - h * 0.012f)
    )
    drawCircle(Color.White.copy(alpha = 0.70f), radius = w * 0.040f, center = Offset(c.x, c.y - h * 0.012f), style = Stroke(width = 1.dp.toPx()))
    // A single petal falling, and the light that catches it.
    drawPetal(
        center = Offset(w * 0.80f, h * 0.30f),
        angle = 128f,
        length = h * 0.075f,
        width = w * 0.022f,
        top = Color.White.copy(alpha = 0.90f),
        bottom = accent.copy(alpha = 0.40f)
    )
    drawSparkle(Offset(w * 0.20f, h * 0.28f), w * 0.032f, Color.White.copy(alpha = 0.80f))
}

// ---------------------------------------------------------------------------
// Shared marks
// ---------------------------------------------------------------------------

/** A glass bubble: body gradient, upper-left specular, and a rim that fades. */
private fun DrawScope.drawBubble(center: Offset, radius: Float, tint: Color) {
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.35f), tint.copy(alpha = 0.12f), Color.Transparent),
            center = Offset(center.x - radius * 0.30f, center.y - radius * 0.34f),
            radius = radius * 1.25f
        ),
        radius = radius,
        center = center
    )
    drawArc(
        color = Color.White.copy(alpha = 0.65f),
        startAngle = 150f,
        sweepAngle = 200f,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = maxOf(1f, radius * 0.10f), cap = StrokeCap.Round)
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.80f),
        radius = radius * 0.16f,
        center = Offset(center.x - radius * 0.34f, center.y - radius * 0.40f)
    )
}

private fun DrawScope.drawPetal(
    center: Offset,
    angle: Float,
    length: Float,
    width: Float,
    top: Color,
    bottom: Color,
) {
    rotate(degrees = angle, pivot = center) {
        val path = Path().apply {
            moveTo(center.x, center.y)
            cubicTo(
                center.x - width, center.y - length * 0.30f,
                center.x - width * 0.55f, center.y - length,
                center.x, center.y - length
            )
            cubicTo(
                center.x + width * 0.55f, center.y - length,
                center.x + width, center.y - length * 0.30f,
                center.x, center.y
            )
            close()
        }
        drawPath(
            path,
            Brush.linearGradient(
                listOf(top, bottom),
                start = Offset(center.x, center.y),
                end = Offset(center.x, center.y - length)
            )
        )
        drawPath(path, Color.White.copy(alpha = 0.22f), style = Stroke(width = 1.dp.toPx()))
    }
}

private fun DrawScope.drawPetalBurst(
    center: Offset,
    radius: Float,
    light: Color,
    deep: Color,
    petals: Int,
    tilt: Float,
) {
    repeat(petals) { i ->
        drawPetal(
            center = center,
            angle = tilt + i * (360f / petals),
            length = radius,
            width = radius * 0.36f,
            top = light,
            bottom = deep
        )
    }
}

private fun DrawScope.drawCrescent(center: Offset, radius: Float, color: Color) {
    // A thick round-capped arc is a crescent with none of the fill-rule
    // surprises of boolean path subtraction, and it works on every backdrop.
    drawArc(
        color = color,
        startAngle = 42f,
        sweepAngle = 276f,
        useCenter = false,
        topLeft = Offset(center.x - radius * 0.78f, center.y - radius * 0.78f),
        size = Size(radius * 1.56f, radius * 1.56f),
        style = Stroke(width = radius * 0.46f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawTextZ(origin: Offset, height: Float, color: Color) {
    val path = Path().apply {
        moveTo(origin.x, origin.y)
        lineTo(origin.x + height * 1.1f, origin.y)
        lineTo(origin.x, origin.y + height)
        lineTo(origin.x + height * 1.2f, origin.y + height)
    }
    drawPath(path, color, style = Stroke(width = height * 0.22f, cap = StrokeCap.Round))
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
