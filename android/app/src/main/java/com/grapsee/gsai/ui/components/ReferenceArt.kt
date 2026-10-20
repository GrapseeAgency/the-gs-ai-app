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
// Shared marks
// ---------------------------------------------------------------------------

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
