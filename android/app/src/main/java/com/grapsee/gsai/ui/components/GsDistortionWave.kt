package com.grapsee.gsai.ui.components

import android.animation.ValueAnimator
import android.graphics.RenderEffect as PlatformRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader as PlatformShader
import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.SettingsStore
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The source-aware travel directions used by upstream DistortionWavesKit. */
enum class GsDistortionWaveType { Vertical, Horizontal, Center }

/** Flowing colors on the front and edge rim, plus its two chromatic fringes. */
data class GsDistortionWavePalette(
    val colors: List<Color>,
    val ahead: Color,
    val behind: Color,
) {
    companion object {
        val Colorful = GsDistortionWavePalette(
            colors = listOf(
                Color(0xFFFFC915), Color(0xFF7EC4FF), Color(0xFFB428E6),
                Color(0xFFEB64A0), Color(0xFFFFB07A), Color(0xFF9AA0FF), Color(0xFF7FD9EE),
            ),
            ahead = Color(0xFFFF7AB6), behind = Color(0xFF5ABEFF),
        )
        val Green = GsDistortionWavePalette(
            colors = listOf(
                Color(0xFF35BA00), Color(0xFF8CEB5A), Color(0xFF00C46E),
                Color(0xFFBEFA82), Color(0xFF28A03C), Color(0xFF6EE1A0), Color(0xFF5AD228),
            ),
            ahead = Color(0xFFC4FF78), behind = Color(0xFF009646),
        )
        val Ocean = GsDistortionWavePalette(
            colors = listOf(
                Color(0xFF2878FF), Color(0xFF5AC8FF), Color(0xFF1450C8),
                Color(0xFF78E6F0), Color(0xFF3C96FF), Color(0xFF96B4FF), Color(0xFF00B4DC),
            ),
            ahead = Color(0xFF96E6FF), behind = Color(0xFF1E46DC),
        )
        val Sunset = GsDistortionWavePalette(
            colors = listOf(
                Color(0xFFFF8C3C), Color(0xFFFF5A78), Color(0xFFFFC850),
                Color(0xFFF0465A), Color(0xFFFFAA78), Color(0xFFDC5AC8), Color(0xFFFF7850),
            ),
            ahead = Color(0xFFFFDC78), behind = Color(0xFFC83CA0),
        )
        val Mono = GsDistortionWavePalette(
            colors = listOf(
                Color(0xFFF0F0F5), Color(0xFFC8CDD7), Color.White, Color(0xFFAAAFBE),
                Color(0xFFE1E4EB), Color(0xFFBEC3CD), Color(0xFFF5F5FA),
            ),
            ahead = Color.White, behind = Color(0xFFA0A5B4),
        )
    }
}

/**
 * Compose-side style for the upstream distortion-wave model. Distances are
 * declared in dp (the iOS kit uses points); the transition converts them once
 * to the current display density. These are fixed visual tuning values, not
 * user-configurable settings.
 */
data class GsDistortionWaveStyle(
    val type: GsDistortionWaveType = GsDistortionWaveType.Vertical,
    val palette: GsDistortionWavePalette = GsDistortionWavePalette.Colorful,
    val anticipationSeconds: Float = 0.5f,
    val travelSeconds: Float = 2.28f,
    val launchGapDp: Float = 0f,
    val lingerSeconds: Float = -0.35f,
    val rimFadeSeconds: Float = 3.03f,
    val returnSpeed: Float = 1.45f,
    val pullDp: Float = 24f,
    val inhaleDp: Float = 56f,
    val gatherGlow: Float = 0.33f,
    val gatherDots: Float = 1f,
    val amplitudeDp: Float = 34f,
    val lensWidthDp: Float = 150f,
    val aberration: Float = 0.78f,
    val swellDp: Float = 22f,
    val blurDp: Float = 18f,
    val blurAheadDp: Float = 90f,
    val blurBehindDp: Float = 240f,
    val leadDp: Float = 130f,
    val tailDp: Float = 300f,
    val strength: Float = 0.92f,
    val rimWidthDp: Float = 14f,
    val rimUnevenness: Float = 1f,
    val rimStrength: Float = 0.82f,
    val rimSoftnessDp: Float = 343f,
    val cornerRadiusDp: Float = 62f,
    val rimPulse: Float = 0.6f,
    val hairlineWidthDp: Float = 1.5f,
    val hairlineOpacity: Float = 0.9f,
    val hairlineWhite: Float = 0.95f,
    val hairlineLingerSeconds: Float = 0.47f,
    val hairlineFadeSeconds: Float = 3f,
    val orientation: Float = 0.5f,
    val particleCount: Int = 64,
    val particleSizeDp: Float = 1.8f,
    val particleReachDp: Float = 150f,
    val particleFlightDp: Float = 30f,
    val particleGapDp: Float = 24f,
    val particleLifeSeconds: Float = 2.19f,
    val particleSpread: Float = 1f,
) {
    val durationSeconds: Float
        get() = anticipationSeconds + maxOf(
            travelSeconds * 0.6f,
            lingerSeconds + rimFadeSeconds,
            lingerSeconds + hairlineLingerSeconds + hairlineFadeSeconds,
            particleLifeSeconds,
        )
    val durationMillis: Int get() = (durationSeconds * 1_000f).toInt().coerceAtLeast(1)

    companion object {
        val Vertical = GsDistortionWaveStyle()
        val Horizontal = GsDistortionWaveStyle(type = GsDistortionWaveType.Horizontal)
        val Center = GsDistortionWaveStyle(
            type = GsDistortionWaveType.Center,
            palette = GsDistortionWavePalette.Green,
            anticipationSeconds = 0.25f,
            travelSeconds = 3.43f,
            rimFadeSeconds = 2.51f,
            hairlineLingerSeconds = 0.96f,
            strength = 0.6f,
            rimStrength = 0.6f,
            aberration = 0f,
            gatherGlow = 0f,
            gatherDots = 0f,
            particleLifeSeconds = 3.93f,
            particleSpread = 0.64f,
            cornerRadiusDp = 48f,
            pullDp = 14.4f,
            inhaleDp = 33.6f,
            amplitudeDp = 20.4f,
            lensWidthDp = 90f,
            swellDp = 13.2f,
            blurDp = 10.8f,
            blurAheadDp = 54f,
            blurBehindDp = 144f,
            leadDp = 78f,
            tailDp = 180f,
            rimSoftnessDp = 205.8f,
            particleCount = 89,
            particleSizeDp = 1.8f,
            particleReachDp = 90f,
            particleFlightDp = 18f,
            particleGapDp = 11f,
        )
    }
}

private data class PixelStyle(
    val pull: Float,
    val inhale: Float,
    val launchGap: Float,
    val amplitude: Float,
    val lensWidth: Float,
    val swell: Float,
    val blur: Float,
    val blurAhead: Float,
    val blurBehind: Float,
    val lead: Float,
    val tail: Float,
    val rimWidth: Float,
    val rimSoftness: Float,
    val cornerRadius: Float,
    val hairlineWidth: Float,
    val particleSize: Float,
    val particleReach: Float,
    val particleFlight: Float,
    val particleGap: Float,
)

private fun Density.pixels(style: GsDistortionWaveStyle) = PixelStyle(
    pull = style.pullDp.dp.toPx(),
    inhale = style.inhaleDp.dp.toPx(),
    launchGap = style.launchGapDp.dp.toPx(),
    amplitude = style.amplitudeDp.dp.toPx(),
    lensWidth = style.lensWidthDp.dp.toPx(),
    swell = style.swellDp.dp.toPx(),
    blur = style.blurDp.dp.toPx(),
    blurAhead = style.blurAheadDp.dp.toPx(),
    blurBehind = style.blurBehindDp.dp.toPx(),
    lead = style.leadDp.dp.toPx(),
    tail = style.tailDp.dp.toPx(),
    rimWidth = style.rimWidthDp.dp.toPx(),
    rimSoftness = style.rimSoftnessDp.dp.toPx(),
    cornerRadius = style.cornerRadiusDp.dp.toPx(),
    hairlineWidth = style.hairlineWidthDp.dp.toPx(),
    particleSize = style.particleSizeDp.dp.toPx(),
    particleReach = style.particleReachDp.dp.toPx(),
    particleFlight = style.particleFlightDp.dp.toPx(),
    particleGap = style.particleGapDp.dp.toPx(),
)

private data class WaveFrame(
    val origin: Offset,
    val tilt: Float,
    val spine: Float,
    val radius: Float,
    val moved: Float,
    val squash: Float,
    val time: Float,
    val energy: Float,
    val swell: Float,
    val gravity: Float,
    val glow: Float,
    val clearRadius: Float,
    val hairClearRadius: Float,
    val rim: Float,
    val sparkle: Float,
    val flying: Float,
    val gathered: Float,
    val reach: Float,
    val launched: Boolean,
    val hasCleared: Boolean,
    val ring: Boolean,
    val sideways: Boolean,
    val hold: Float,
)

private fun smooth(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / max(b - a, 1e-6f)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun cubicBezier(x1: Float, y1: Float, x2: Float, y2: Float, x: Float): Float {
    if (x <= 0f) return 0f
    if (x >= 1f) return 1f
    fun component(t: Float, a: Float, b: Float): Float {
        val inverse = 1f - t
        return 3f * inverse * inverse * t * a + 3f * inverse * t * t * b + t * t * t
    }
    var low = 0f
    var high = 1f
    repeat(24) {
        val middle = (low + high) * 0.5f
        if (component(middle, x1, x2) < x) low = middle else high = middle
    }
    return component((low + high) * 0.5f, y1, y2)
}

private val collapseEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

private fun resolvedSource(source: Rect, size: Size): Rect {
    if (source.width > 0f && source.height > 0f) return source
    val side = min(32f, min(size.width, size.height).coerceAtLeast(1f))
    val center = Offset(size.width / 2f, size.height / 2f)
    return Rect(center.x - side / 2f, center.y - side / 2f, center.x + side / 2f, center.y + side / 2f)
}

/** Direct Kotlin translation of upstream WaveFront's progress model. */
private fun waveFront(
    progress: Float,
    sourceInput: Rect,
    size: Size,
    focus: Offset?,
    style: GsDistortionWaveStyle,
    px: PixelStyle,
): WaveFrame {
    val source = resolvedSource(sourceInput, size)
    val time = progress.coerceIn(0f, 1f) * style.durationSeconds
    val sinceLaunch = time - style.anticipationSeconds
    val gathered = smooth(0f, style.anticipationSeconds.coerceAtLeast(0.001f), time)
    val x = (sinceLaunch / style.travelSeconds.coerceAtLeast(0.001f)).coerceIn(0f, 1f)
    val moved = cubicBezier(0.22f, 1f, 0.36f, 1f, x)

    val ring = style.type == GsDistortionWaveType.Center
    val sideways = style.type == GsDistortionWaveType.Horizontal
    val fullSquash = if (ring) 1f else 1.4f
    val midX = source.center.x
    val midY = source.center.y
    val startRadius = min(source.width, source.height) / 2f
    val length = if (sideways) source.height else source.width
    val startSpine = if (ring) 0f else max(0f, length / 2f - startRadius)
    val turned = if (sideways) (PI / 2.0).toFloat() else 0f
    val axisX = cos(turned)
    val axisY = sin(turned)

    val lean = if (ring || focus == null) 0f else {
        val offset = if (sideways) focus.y - midY else focus.x - midX
        (offset / max(length / 2f, 1f)).coerceIn(-1f, 1f)
    }
    val fullShift = lean * length / 2f * style.orientation
    val fullLean = lean * style.orientation * 0.45f
    val fullTilt = turned + fullLean
    val opening = smooth(0f, 0.45f, moved)
    val cosTilt = cos(fullTilt)
    val sinTilt = sin(fullTilt)
    var reach = 0f
    val corners = arrayOf(
        Offset.Zero,
        Offset(size.width, 0f),
        Offset(0f, size.height),
        Offset(size.width, size.height),
    )
    corners.forEach { corner ->
        val dx = corner.x - midX - fullShift * axisX
        val dy = corner.y - midY - fullShift * axisY
        val qx = cosTilt * dx + sinTilt * dy
        val qy = -sinTilt * dx + cosTilt * dy
        reach = max(reach, sqrt((qx * fullSquash) * (qx * fullSquash) + qy * qy))
    }
    val crossed = reach + (px.swell + 60f) * fullSquash
    val settling = 1f - smooth(0.2f, 0.55f, x)
    val launchRadius = startRadius + max(px.launchGap, 0f)
    val radius = if (sinceLaunch < 0f) {
        launchRadius + px.inhale * (1f - gathered)
    } else {
        launchRadius + (crossed + 100f - launchRadius) * moved
    }
    val gatheredGlow = min(0.5f * max(style.gatherGlow, 0f), 1f)
    val clearStart = startRadius - px.rimSoftness
    val clearEnd = reach + (px.rimSoftness + px.swell) * fullSquash
    val facing = smooth(0f, 0.1f, moved)
    val origin = Offset(
        midX + fullShift * facing * axisX,
        midY + fullShift * facing * axisY,
    )
    val tilt = turned + fullLean * facing
    val spine = startSpine * (1f - opening)
    val squash = 1f + (fullSquash - 1f) * smooth(0f, 0.5f, moved)
    val energy = smooth(0f, 0.05f, x) * settling
    val glow = if (sinceLaunch < 0f) {
        gatheredGlow * gathered
    } else {
        (gatheredGlow + (1f - gatheredGlow) * smooth(0f, 0.07f, x)) * settling
    }
    val clearRadius = clearStart + (clearEnd - clearStart) * cubicBezier(
        0.42f, 0f, 0.58f, 1f,
        ((sinceLaunch - style.lingerSeconds) / style.rimFadeSeconds.coerceAtLeast(0.01f)).coerceIn(0f, 1f),
    )
    val hairClearRadius = clearStart + (clearEnd - clearStart) * cubicBezier(
        0.42f, 0f, 0.58f, 1f,
        ((sinceLaunch - style.lingerSeconds - style.hairlineLingerSeconds) /
            style.hairlineFadeSeconds.coerceAtLeast(0.01f)).coerceIn(0f, 1f),
    )
    // Keep the animated visibility separate from style.rimStrength. The style
    // multiplier is applied once by both the edge band and hairline renderers.
    val rim = smooth(0f, 0.1f, x) *
        (1f - smooth(0.9f, 1f, time / style.durationSeconds.coerceAtLeast(0.001f)))
    val sparkle = smooth(0f, style.anticipationSeconds.coerceAtLeast(0.001f) * 0.35f, time)
    val flying = max(sinceLaunch, 0f)
    val launched = sinceLaunch > 0f

    return WaveFrame(
        origin = origin,
        tilt = tilt,
        spine = spine,
        radius = radius,
        moved = moved,
        squash = squash,
        time = time,
        energy = energy,
        swell = px.swell * smooth(0f, 0.3f, moved),
        gravity = px.pull * gathered * (1f - smooth(0f, 0.22f, sinceLaunch)),
        glow = glow,
        clearRadius = clearRadius,
        hairClearRadius = hairClearRadius,
        rim = rim,
        sparkle = sparkle,
        flying = flying,
        gathered = gathered,
        reach = reach,
        launched = launched,
        hasCleared = radius > crossed,
        ring = ring,
        sideways = sideways,
        hold = if (ring) startRadius else 0f,
    )
}

private fun wavePath(
    frame: WaveFrame,
    radiusOffset: Float = 0f,
): Path {
    val path = Path()
    val radius = (frame.radius + radiusOffset).coerceAtLeast(0.5f)
    if (frame.ring) {
        path.addOval(Rect(
            frame.origin.x - radius,
            frame.origin.y - radius,
            frame.origin.x + radius,
            frame.origin.y + radius,
        ))
        return path
    }

    val squash = frame.squash.coerceAtLeast(0.001f)
    val rx = radius / squash
    val spine = frame.spine / squash
    val k = 0.55228475f
    fun map(x: Float, y: Float): Offset {
        val c = cos(frame.tilt)
        val s = sin(frame.tilt)
        return Offset(frame.origin.x + c * x - s * y, frame.origin.y + s * x + c * y)
    }
    fun move(x: Float, y: Float) = map(x, y).let { path.moveTo(it.x, it.y) }
    fun line(x: Float, y: Float) = map(x, y).let { path.lineTo(it.x, it.y) }
    fun cubic(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        val a = map(x1, y1); val b = map(x2, y2); val c = map(x3, y3)
        path.cubicTo(a.x, a.y, b.x, b.y, c.x, c.y)
    }

    move(-spine, -radius)
    line(spine, -radius)
    cubic(spine + rx * k, -radius, spine + rx, -radius * k, spine + rx, 0f)
    cubic(spine + rx, radius * k, spine + rx * k, radius, spine, radius)
    line(-spine, radius)
    cubic(-spine - rx * k, radius, -spine - rx, radius * k, -spine - rx, 0f)
    cubic(-spine - rx, -radius * k, -spine - rx * k, -radius, -spine, -radius)
    path.close()
    return path
}

private data class WaveParticle(
    val index: Int,
    val column: Float,
    val heading: Float,
    val pace: Float,
    val drift: Float,
    val sway: Float,
    val swayRate: Float,
    val twinkleRate: Float,
    val phase: Float,
    val life: Float,
    val size: Float,
    val brightness: Float,
    val hop: Float,
    val hopOffset: Float,
)

private fun particles(style: GsDistortionWaveStyle, px: PixelStyle): List<WaveParticle> =
    (0 until style.particleCount.coerceIn(0, 128)).map { index ->
        val i = (index + 1).toFloat()
        fun scatter(step: Float) = (i * step) % 1f
        val small = scatter(0.5497004779f)
        WaveParticle(
            index = index + 1,
            column = (scatter(0.7548776662f) * 2f - 1f) * 0.92f,
            heading = scatter(0.7548776662f) * 2f * PI.toFloat(),
            pace = 0.35f + 0.72f * sqrt(scatter(0.8191725134f)),
            drift = 15f + 50f * scatter(0.6710436067f),
            sway = 3f + 11f * scatter(0.4142135624f),
            swayRate = 2.5f + 3.5f * scatter(0.7320508076f),
            twinkleRate = 8f + 12f * scatter(0.2360679775f),
            phase = scatter(0.6180339887f) * 2f * PI.toFloat(),
            life = style.particleLifeSeconds * (0.4f + 0.6f * scatter(0.6457513111f)),
            size = px.particleSize * (0.55f + 0.45f * small * small),
            brightness = 0.3f + 0.7f * scatter(0.6055512755f),
            hop = 0.16f + 0.14f * scatter(0.1231056256f),
            hopOffset = scatter(0.4721359550f),
        )
    }

private fun particleHash(index: Int, count: Float, salt: Float): Float {
    val value = sin(index * 12.9898f + count * 78.233f + salt * 37.719f) * 43758.5453f
    return value - floor(value)
}

private fun particlePoint(
    p: WaveParticle,
    frame: WaveFrame,
    sourceInput: Rect,
    size: Size,
    style: GsDistortionWaveStyle,
    px: PixelStyle,
): Offset {
    val source = resolvedSource(sourceInput, size)
    val midX = source.center.x
    val midY = source.center.y
    val spread = style.particleSpread.coerceIn(0f, 1f)
    if (!frame.launched) {
        val hops = frame.time / p.hop + p.hopOffset
        val count = floor(hops)
        val along = hops - count
        if (frame.ring) {
            val angle = particleHash(p.index, count, 1f) * 2f * PI.toFloat()
            val nearest = min(source.width, source.height) / 2f + px.particleGap + px.particleFlight
            val away = nearest + max(px.particleReach - nearest, 0f) * particleHash(p.index, count, 2f)
            val distance = away - px.particleFlight * along * along
            return Offset(midX + cos(angle) * distance, midY + sin(angle) * distance)
        }
        val half = (if (frame.sideways) source.height else source.width) / 2f
        val face = (if (frame.sideways) source.width else source.height) / 2f
        val x = (particleHash(p.index, count, 1f) * 2f - 1f) * (half + 24f)
        val nearest = px.particleGap + px.particleFlight
        val away = nearest + max(px.particleReach - nearest, 0f) * particleHash(p.index, count, 2f)
        val corner = min(source.width, source.height) / 2f
        val far = face + away
        val landing = x.coerceIn(-half + corner, half - corner)
        val startX = if (frame.sideways) midX + far else midX + x
        val startY = if (frame.sideways) midY + x else midY - far
        val dx = (if (frame.sideways) midX + face else midX + landing) - startX
        val dy = (if (frame.sideways) midY + landing else midY - face) - startY
        val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val travel = px.particleFlight * along * along
        return Offset(startX + dx / length * travel, startY + dy / length * travel)
    }

    val swaying = p.sway * sin(p.swayRate * frame.flying + p.phase) * min(frame.flying / 0.25f, 1f)
    if (frame.ring) {
        val edge = min(source.width, source.height) / 2f
        val distance = edge + ((frame.radius - edge) * p.pace + p.drift * frame.flying) * spread
        val dx = cos(p.heading); val dy = sin(p.heading)
        return Offset(frame.origin.x + dx * distance - dy * swaying, frame.origin.y + dy * distance + dx * swaying)
    }
    val start = min(source.width, source.height) / 2f
    val widening = 1f + 0.22f * frame.moved * p.pace * spread
    val x = p.column * (if (frame.sideways) source.height else source.width) / 2f * widening + swaying
    val height = (start + (frame.radius - start) * p.pace * spread) *
        (1f - 0.3f * p.column * p.column) + p.drift * frame.flying * spread
    val c = cos(frame.tilt); val s = sin(frame.tilt)
    return Offset(frame.origin.x + c * x + s * height, frame.origin.y + s * x - c * height)
}

private fun particleOpacity(p: WaveParticle, frame: WaveFrame, style: GsDistortionWaveStyle): Float {
    val twinkle = 0.5f + 0.5f * sin(frame.time * p.twinkleRate + p.phase)
    val glint = p.brightness * (0.7f + 0.3f * twinkle)
    if (!frame.launched) {
        val hops = frame.time / p.hop + p.hopOffset
        val along = hops - floor(hops)
        val hopping = smooth(0f, 0.25f, along) * (1f - smooth(0.55f, 1f, along))
        val ending = 1f - smooth(style.anticipationSeconds * 0.75f, style.anticipationSeconds, frame.time)
        return frame.sparkle * hopping * ending * glint * style.gatherDots
    }
    return (1f - smooth(p.life * 0.55f, p.life, frame.flying)) * glint
}

private data class RimLump(val center: Float, val length: Float, val depth: Float)

private fun rimHash(index: Float, a: Float, b: Float): Float {
    val value = sin(index * a + b) * 43758.5453f
    return value - floor(value)
}

/** The seven stable, drifting and breathing edge lobes used by upstream WaveRim. */
private fun rimLumps(time: Float, size: Size, radius: Float, pulse: Float): Pair<Float, List<RimLump>> {
    val ex = max(size.width / 2f - radius, 0f)
    val ey = max(size.height / 2f - radius, 0f)
    val perimeter = 4f * (ex + ey + 0.5f * PI.toFloat() * radius)
    val count = 7
    val lumps = (0 until count).map { index ->
        val i = index.toFloat()
        val a = rimHash(i, 12.9898f, 1.3f)
        val b = rimHash(i, 78.233f, 4.1f)
        val c = rimHash(i, 39.425f, 2.7f)
        val center = perimeter * (i + 0.35f * a) / count +
            time * (-22f + 44f * b) * (1f + 2f * pulse)
        val length = perimeter / count * (0.2f + 0.25f * c)
        val breath = (0.25f + 0.3f * pulse) *
            sin(time * (1.1f + 1.2f * a) * (1f + pulse) + i * 2.4f)
        RimLump(center, length, (0.2f + 2.6f * b) * (1f + breath))
    }
    return perimeter to lumps
}

private fun rimThickness(s: Float, perimeter: Float, lumps: List<RimLump>): Float {
    if (perimeter <= 0f) return 1f
    var thickness = 0.08f
    lumps.forEach { lump ->
        val ds = s - lump.center
        val wrapped = ds - perimeter * kotlin.math.round(ds / perimeter)
        val normalized = wrapped / lump.length.coerceAtLeast(1f)
        thickness += lump.depth * exp(-normalized * normalized)
    }
    return thickness / 1.0498f
}

/** Point clockwise along a rounded-rectangle perimeter, beginning at its top-left tangent. */
private fun pointOnRoundedRect(s: Float, size: Size, radius: Float, perimeter: Float): Offset {
    val width = size.width
    val height = size.height
    val r = radius.coerceIn(0f, min(width, height) / 2f)
    if (r <= 0.1f) {
        var d = ((s % perimeter) + perimeter) % perimeter
        if (d <= width) return Offset(d, 0f)
        d -= width
        if (d <= height) return Offset(width, d)
        d -= height
        if (d <= width) return Offset(width - d, height)
        d -= width
        return Offset(0f, (height - d).coerceIn(0f, height))
    }

    val top = max(width - 2f * r, 0f)
    val side = max(height - 2f * r, 0f)
    val quarter = 0.5f * PI.toFloat() * r
    var d = ((s % perimeter) + perimeter) % perimeter
    if (d <= top) return Offset(r + d, 0f)
    d -= top
    if (d <= quarter) {
        val angle = -0.5f * PI.toFloat() + d / r
        return Offset(width - r + r * cos(angle), r + r * sin(angle))
    }
    d -= quarter
    if (d <= side) return Offset(width, r + d)
    d -= side
    if (d <= quarter) {
        val angle = d / r
        return Offset(width - r + r * cos(angle), height - r + r * sin(angle))
    }
    d -= quarter
    if (d <= top) return Offset(width - r - d, height)
    d -= top
    if (d <= quarter) {
        val angle = 0.5f * PI.toFloat() + d / r
        return Offset(r + r * cos(angle), height - r + r * sin(angle))
    }
    d -= quarter
    if (d <= side) return Offset(0f, height - r - d)
    d -= side
    val angle = PI.toFloat() + d / r
    return Offset(r + r * cos(angle), r + r * sin(angle))
}

private fun waveColorAt(colors: List<Color>, position: Float): Color {
    if (colors.isEmpty()) return Color.White
    if (colors.size == 1) return colors.first()
    val wrapped = position - floor(position)
    val scaled = wrapped * colors.size
    val index = floor(scaled).toInt().coerceIn(0, colors.lastIndex)
    val next = (index + 1) % colors.size
    val raw = scaled - floor(scaled)
    val t = raw * raw * (3f - 2f * raw)
    val a = colors[index]
    val b = colors[next]
    return Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha + (b.alpha - a.alpha) * t,
    )
}

private fun flowingColors(colors: List<Color>, time: Float): List<Color> {
    if (colors.isEmpty()) return listOf(Color.White, Color.White)
    val count = colors.size
    val shift = time * 0.13f
    return (0..count).map { index ->
        waveColorAt(colors, index.toFloat() / count + shift)
    }
}

private fun mixColor(from: Color, to: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * t,
        green = from.green + (to.green - from.green) * t,
        blue = from.blue + (to.blue - from.blue) * t,
        alpha = from.alpha + (to.alpha - from.alpha) * t,
    )
}

private data class WaveFieldSample(val distance: Float, val gradient: Float)

/** Signed distance and gradient from the same curved front used by waveLens. */
private fun waveField(point: Offset, frame: WaveFrame): WaveFieldSample {
    val rawX = point.x - frame.origin.x
    val rawY = point.y - frame.origin.y
    val cs = cos(frame.tilt)
    val sn = sin(frame.tilt)
    val qx = cs * rawX + sn * rawY
    val qy = -sn * rawX + cs * rawY
    val mx = qx * frame.squash
    val vx = max(abs(mx) - frame.spine, 0f)
    val length = sqrt(vx * vx + qy * qy).coerceAtLeast(1e-3f)
    val gradX = vx * (if (qx < 0f) -1f else 1f) * frame.squash / length
    val gradY = qy / length
    val gradient = sqrt(gradX * gradX + gradY * gradY).coerceAtLeast(1e-3f)
    val bend = if (frame.ring) {
        val angle = atan2(qy, qx)
        0.62f * sin(2f * angle + frame.time * 1.1f) +
            0.38f * sin(3f * angle - frame.time * 1.5f + 1.7f)
    } else {
        val along = mx * 0.0058f
        0.62f * sin(along + frame.time * 1.1f) +
            0.38f * sin(along * 2.1f - frame.time * 1.5f + 1.7f)
    }
    return WaveFieldSample(
        distance = (length - frame.radius) / gradient + frame.swell * bend,
        gradient = gradient,
    )
}

private fun DrawScope.drawVariableRim(
    frame: WaveFrame,
    style: GsDistortionWaveStyle,
    px: PixelStyle,
) {
    val size = this.size
    val corner = px.cornerRadius.coerceIn(0f, min(size.width, size.height) / 2f)
    val pulse = style.rimPulse.coerceIn(0f, 1f)
    val (perimeter, lumps) = rimLumps(frame.time, size, corner, pulse)
    if (perimeter <= 0f || frame.rim <= 0f) return

    val colorStops = style.palette.colors
    val unevenness = style.rimUnevenness.coerceIn(0f, 2f)
    val segments = 128
    for (i in 0 until segments) {
        val s0 = perimeter * i / segments
        val s1 = perimeter * (i + 1) / segments
        val middle = (s0 + s1) * 0.5f
        val start = pointOnRoundedRect(s0, size, corner, perimeter)
        val end = pointOnRoundedRect(s1, size, corner, perimeter)
        val field = waveField(Offset((start.x + end.x) * 0.5f, (start.y + end.y) * 0.5f), frame)
        val reached = 1f - smooth(-px.lead * 0.7f, px.lead * 0.5f, field.distance)
        val clearDistance = field.distance + (frame.radius - frame.clearRadius) / field.gradient
        val hairClearDistance = field.distance + (frame.radius - frame.hairClearRadius) / field.gradient
        val kept = smooth(-px.rimSoftness, px.rimSoftness, clearDistance)
        val hairKept = smooth(-px.rimSoftness, px.rimSoftness, hairClearDistance)
        val thickness = (1f + (rimThickness(middle, perimeter, lumps) - 1f) * unevenness)
            .coerceIn(0.04f, 4f)
        val shimmer = 0.5f + 0.5f * sin(frame.time * 3.4f - middle / perimeter * 4f * PI.toFloat())
        val breathe = 1f + pulse * (0.7f + 0.3f * shimmer - 1f)
        val color = waveColorAt(
            colorStops,
            middle / perimeter + frame.time * (0.12f + 0.25f * pulse),
        )
        val baseAlpha = (frame.rim * style.rimStrength * reached * kept * breathe).coerceIn(0f, 1f)
        if (baseAlpha > 0.001f) {
            drawLine(
                color = color,
                start = start,
                end = end,
                strokeWidth = (px.rimWidth * thickness).coerceAtMost(72f),
                cap = StrokeCap.Round,
                alpha = baseAlpha,
            )
        }
        val hairAlpha = (
            frame.rim * style.rimStrength * style.hairlineOpacity * reached * hairKept
                * (0.5f + 0.5f * smooth(0.1f, 1.5f, thickness))
                * (1f + pulse * (0.55f + 0.45f * shimmer - 1f))
            ).coerceIn(0f, 1f)
        if (hairAlpha > 0.001f && px.hairlineWidth > 0f) {
            val white = style.hairlineWhite.coerceIn(0f, 1f) * (0.5f + 0.5f * shimmer)
            drawLine(
                color = mixColor(color, Color.White, white),
                start = start,
                end = end,
                strokeWidth = px.hairlineWidth.coerceAtLeast(0.5f),
                cap = StrokeCap.Round,
                alpha = hairAlpha,
            )
        }
    }
}

private fun DrawScope.drawWaveArtwork(
    frame: WaveFrame,
    source: Rect,
    style: GsDistortionWaveStyle,
    px: PixelStyle,
    dots: List<WaveParticle>,
) {
    val size = this.size
    val particleColors = style.palette.colors.ifEmpty { listOf(style.palette.ahead, style.palette.behind) }
    if (!frame.launched && frame.glow > 0f) {
        val gatherPath = wavePath(frame)
        drawPath(
            path = gatherPath,
            brush = Brush.sweepGradient(flowingColors(style.palette.colors, frame.time), frame.origin),
            alpha = frame.glow.coerceIn(0f, 0.45f),
            style = Stroke(width = max(1f, px.hairlineWidth)),
        )
    }

    // The shader-equivalent field is sampled along the perimeter: edges light
    // as the front actually reaches them, and the later front wipes the rim and
    // hairline independently, just like upstream WaveRim/WaveBand.
    drawVariableRim(frame, style, px)

    if (frame.launched && frame.energy > 0.001f) {
        // WaveBand is a soft field rather than a solid outline. Recreate its
        // lead/tail envelope with a broad, low-opacity underglow and a tighter
        // palette stroke; the two fringes stay tied to the same front geometry.
        val glow = (frame.glow * style.strength).coerceIn(0f, 1f)
        val path = wavePath(frame)
        val contourBrush = Brush.sweepGradient(
            flowingColors(style.palette.colors, frame.time),
            center = frame.origin,
        )
        val bandWidth = (px.lead + px.tail).coerceAtMost(max(size.width, size.height) * 0.78f)
        drawPath(
            path = path,
            color = style.palette.behind,
            alpha = glow * 0.12f,
            style = Stroke(width = max(px.lensWidth * 0.72f, bandWidth), cap = StrokeCap.Round),
        )
        drawPath(
            path = path,
            brush = contourBrush,
            alpha = glow * 0.48f,
            style = Stroke(width = max(2f, min(px.lensWidth * 0.34f, bandWidth * 0.38f)), cap = StrokeCap.Round),
        )
        val fringeOffset = px.aberration * px.lensWidth * 0.12f
        drawPath(
            path = wavePath(frame, fringeOffset),
            color = style.palette.ahead,
            alpha = glow * 0.38f,
            style = Stroke(width = max(1f, px.hairlineWidth * 1.4f), cap = StrokeCap.Round),
        )
        drawPath(
            path = wavePath(frame, -fringeOffset),
            color = style.palette.behind,
            alpha = glow * 0.38f,
            style = Stroke(width = max(1f, px.hairlineWidth * 1.4f), cap = StrokeCap.Round),
        )
        drawPath(
            path = path,
            color = Color.White,
            alpha = glow * 0.32f,
            style = Stroke(width = max(1f, px.hairlineWidth * 0.75f), cap = StrokeCap.Round),
        )
    }

    dots.forEachIndexed { index, particle ->
        val alpha = particleOpacity(particle, frame, style).coerceIn(0f, 1f)
        if (alpha > 0.001f) {
            val point = particlePoint(particle, frame, source, size, style, px)
            drawCircle(
                color = particleColors[index % particleColors.size],
                radius = max(0.6f, particle.size * 0.5f),
                center = point,
                alpha = alpha,
            )
        }
    }
}

/**
 * A source-aware transition port of Libraries.dev DistortionWavesKit.
 *
 * `source` and `focus` are in the Compose root coordinate system. Vertical
 * waves launch upward from a wide source, horizontal waves travel right from a
 * tall source, and Center spreads a ring. The same progress model drives the
 * gravity pull, front, progressive lens, edge rim, chromatic fringes and stable
 * particle field. Android 13+ additionally warps both content layers with an
 * AGSL RuntimeShader; earlier supported releases retain the source-shaped
 * transition and all drawn wave layers without the GPU lens.
 *
 * This is a page/navigation effect only. Billing actions open existing details;
 * no tap is represented as a purchase or activates a plan.
 */
@Composable
fun GsDistortionWave(
    isRevealed: Boolean,
    source: Rect,
    focus: Offset? = null,
    modifier: Modifier = Modifier,
    style: GsDistortionWaveStyle = GsDistortionWaveStyle.Vertical,
    content: @Composable () -> Unit,
    revealed: @Composable () -> Unit,
) {
    val reduceMotion = SettingsStore.reduceAnimations || SettingsStore.reduceMotion || !ValueAnimator.areAnimatorsEnabled()
    val transition = updateTransition(targetState = isRevealed, label = "gsDistortionWave")
    val progress by transition.animateFloat(
        transitionSpec = {
            when {
                reduceMotion -> tween(durationMillis = 300, easing = LinearEasing)
                targetState -> tween(durationMillis = style.durationMillis, easing = LinearEasing)
                else -> tween(
                    durationMillis = (style.durationMillis / style.returnSpeed).toInt().coerceAtLeast(1),
                    easing = collapseEasing,
                )
            }
        },
        label = "gsDistortionWaveProgress",
    ) { open -> if (open) 1f else 0f }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val px = density.pixels(style)
    val shaderPair = remember { newLensShaderPair() }
    var stageSize by remember { mutableStateOf(Size.Zero) }
    var stageOrigin by remember { mutableStateOf(Offset.Zero) }
    val localSource = if (source.width > 0f && source.height > 0f) {
        Rect(
            source.left - stageOrigin.x,
            source.top - stageOrigin.y,
            source.right - stageOrigin.x,
            source.bottom - stageOrigin.y,
        )
    } else source
    val localFocus = focus?.let { Offset(it.x - stageOrigin.x, it.y - stageOrigin.y) }
    val frame = remember(progress, localSource, localFocus, stageSize, style, px) {
        waveFront(progress, localSource, stageSize, localFocus, style, px)
    }
    val dots = remember(style, px) { particles(style, px) }
    val visible = progress > 0f || transition.isRunning
    val useShader = !reduceMotion && shaderPair.available

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            stageOrigin = coordinates.positionInRoot()
            stageSize = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
        },
    ) {
        if (reduceMotion || !useShader) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = if (reduceMotion) 1f - progress else 1f },
            ) { content() }
            if (visible) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = if (reduceMotion) progress else 1f }
                        .drawWithContent {
                            if (reduceMotion) {
                                drawContent()
                            } else if (frame.launched) {
                                clipPath(wavePath(frame, 66f)) { drawContent() }
                            }
                        },
                ) { revealed() }
            }
        } else {
            // New content is placed first; outgoing content is above it. Each
            // AGSL pass returns transparency on the side that has crossed the
            // shared source-aware field, just like WaveStage in the SwiftUI kit.
            if (visible) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            renderEffect = updateLensShader(
                                shader = shaderPair.incoming,
                                effect = shaderPair.incomingEffect,
                                size = size,
                                frame = frame,
                                style = style,
                                px = px,
                                side = -1f,
                            )
                        },
                ) { revealed() }
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        renderEffect = if (visible) {
                            updateLensShader(
                                shader = shaderPair.outgoing,
                                effect = shaderPair.outgoingEffect,
                                size = size,
                                frame = frame,
                                style = style,
                                px = px,
                                side = 1f,
                            )
                        } else null
                    },
            ) { content() }
        }

        if (!reduceMotion && visible && stageSize.width > 0f && stageSize.height > 0f) {
            Canvas(Modifier.matchParentSize()) {
                drawWaveArtwork(frame, localSource, style, px, dots)
            }
        }
        if (transition.isRunning) {
            // Like the SwiftUI stage, neither side accepts input while the
            // source-aware cover is between screens.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false).consume()
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    },
            )
        }
    }
}

@Suppress("NewApi")
private data class LensShaderPair(
    val outgoing: PlatformShader?,
    val incoming: PlatformShader?,
    val outgoingEffect: ComposeRenderEffect?,
    val incomingEffect: ComposeRenderEffect?,
) {
    val available: Boolean get() = outgoing != null && incoming != null && outgoingEffect != null && incomingEffect != null
}

@Suppress("NewApi")
private fun newLensShaderPair(): LensShaderPair {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return LensShaderPair(null, null, null, null)
    val outgoing = runCatching { RuntimeShader(WAVE_LENS_AGSL) }.getOrNull()
    val incoming = runCatching { RuntimeShader(WAVE_LENS_AGSL) }.getOrNull()
    val outgoingEffect = outgoing?.let { shader ->
        runCatching { PlatformRenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect() }.getOrNull()
    }
    val incomingEffect = incoming?.let { shader ->
        runCatching { PlatformRenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect() }.getOrNull()
    }
    return LensShaderPair(outgoing, incoming, outgoingEffect, incomingEffect)
}

@Suppress("NewApi")
private fun updateLensShader(
    shader: PlatformShader?,
    effect: ComposeRenderEffect?,
    size: Size,
    frame: WaveFrame,
    style: GsDistortionWaveStyle,
    px: PixelStyle,
    side: Float,
): ComposeRenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || shader == null || effect == null) return null
    val runtimeShader = shader as? RuntimeShader ?: return null
    return try {
        runtimeShader.setFloatUniform("uF0", frame.origin.x, frame.origin.y, frame.spine, frame.radius)
        runtimeShader.setFloatUniform("uF1", frame.squash, frame.time, frame.energy, frame.swell)
        runtimeShader.setFloatUniform("uF2", size.width, size.height, frame.gravity, frame.glow)
        runtimeShader.setFloatUniform("uF3", frame.clearRadius, frame.rim, frame.tilt, if (frame.ring) 1f else 0f)
        runtimeShader.setFloatUniform("uF4", frame.hold, if (frame.launched) 1f else 0f, frame.hairClearRadius, 0f)
        runtimeShader.setFloatUniform("uP", px.amplitude, px.lensWidth, style.aberration, side)
        runtimeShader.setFloatUniform("uBlur", px.blur)
        runtimeShader.setFloatUniform("uBlurRange", px.blurAhead, px.blurBehind)
        effect
    } catch (_: RuntimeException) {
        null
    }
}

/**
 * AGSL translation of DistortionWaveShaders.metal's fieldAt + waveLens. The
 * front's distance, source gravity, swell, chromatic split and cover zone all
 * share the upstream WaveFront state instead of an unrelated radial ripple.
 */
private const val WAVE_LENS_AGSL = """
    uniform shader content;
    uniform float4 uF0; // origin xy, spine, radius
    uniform float4 uF1; // squash, time, energy, swell
    uniform float4 uF2; // size xy, gravity, glow
    uniform float4 uF3; // clear radius, rim, tilt, ring
    uniform float4 uF4; // hold, launched, hair clear radius, unused
    uniform float4 uP;  // amplitude, lens width, aberration, side
    uniform float uBlur;
    uniform float2 uBlurRange; // ahead and behind radii

    struct Field { float d; float2 n; float2 q; float2 raw; float g; };

    float heldAt(float2 p) {
        float hold = uF4.x;
        if (hold <= 0.0) return 1.0;
        return smoothstep(hold * 0.9, hold * 1.8, length(p - uF0.xy));
    }

    Field fieldAt(float2 p) {
        float2 raw = p - uF0.xy;
        float cs = cos(uF3.z);
        float sn = sin(uF3.z);
        float2 q = float2(cs * raw.x + sn * raw.y, -sn * raw.x + cs * raw.y);
        float mx = q.x * uF1.x;
        float2 v = float2(max(abs(mx) - uF0.z, 0.0), q.y);
        float l = max(length(v), 1e-3);
        float2 grad = float2(v.x * sign(q.x) * uF1.x, v.y) / l;
        float g = max(length(grad), 1e-3);
        float bend;
        if (uF3.w > 0.5) {
            float a = atan(q.y, q.x);
            bend = 0.62 * sin(2.0 * a + uF1.y * 1.1) + 0.38 * sin(3.0 * a - uF1.y * 1.5 + 1.7);
        } else {
            float s = mx * 0.0058;
            bend = 0.62 * sin(s + uF1.y * 1.1) + 0.38 * sin(s * 2.1 - uF1.y * 1.5 + 1.7);
        }
        float2 n = grad / g;
        Field f;
        f.d = (l - uF0.w) / g + uF1.w * bend;
        f.n = float2(cs * n.x - sn * n.y, sn * n.x + cs * n.y);
        f.q = q;
        f.raw = raw;
        f.g = g;
        return f;
    }

    half4 main(float2 p) {
        Field w = fieldAt(p);
        float energy = uF1.z;
        float gravity = uF2.z;
        float2 bounds = uF2.xy - 0.5;
        float cover = 1.0;
        if (uP.w > 0.0) {
            if (uF4.y > 0.5) cover = smoothstep(-60.0, 60.0, w.d);
            if (cover <= 0.0) return half4(0.0);
        } else {
            if (uF4.y <= 0.5 || w.d > 66.0) return half4(0.0);
        }

        float u = w.d / (uP.y * (w.d > 0.0 ? 0.85 : 1.5));
        float lift = exp(-u * u);
        float r = max(length(w.raw), 1.0);
        float held = heldAt(p);
        float2 swollen = w.n * energy * uP.x * lift * held;
        float2 inward = w.raw / r;
        float2 s = p - swollen + inward * gravity * exp(-r / 900.0) * held;
        float2 split = (swollen - inward * gravity * exp(-r / 240.0) * held) * uP.z;
        float2 samplePoint = clamp(s, float2(0.5), bounds);
        half4 sharp = content.eval(samplePoint);

        float blurU = w.d / max(w.d > 0.0 ? uBlurRange.x : uBlurRange.y, 1.0);
        float blurAmount = energy * uBlur * exp(-blurU * blurU) * held;
        half4 base = sharp;
        if (blurAmount > 0.25) {
            float2 blurOffset = w.n * blurAmount * 0.45;
            half4 a = content.eval(clamp(samplePoint + blurOffset, float2(0.5), bounds));
            half4 b = content.eval(clamp(samplePoint - blurOffset, float2(0.5), bounds));
            half4 c = content.eval(clamp(samplePoint + blurOffset * 2.0, float2(0.5), bounds));
            half4 d = content.eval(clamp(samplePoint - blurOffset * 2.0, float2(0.5), bounds));
            base = (sharp + a + b + c + d) * 0.2;
        }
        if (dot(split, split) < 0.0625) return base * half(cover);

        half4 output = half4(0.0);
        for (int i = 0; i < 5; i++) {
            float along = float(i) * 0.5 - 1.0;
            half3 weight = half3(0.0, 0.0, 0.0);
            if (i == 0) weight = half3(0.571, 0.0, 0.0);
            else if (i == 1) weight = half3(0.343, 0.206, 0.0);
            else if (i == 2) weight = half3(0.086, 0.588, 0.086);
            else if (i == 3) weight = half3(0.0, 0.206, 0.343);
            else weight = half3(0.0, 0.0, 0.571);
            half4 c = content.eval(clamp(samplePoint + split * along, float2(0.5), bounds));
            output += half4(c.rgb * weight, c.a / 5.0);
        }
        return output * half(cover);
    }
"""
