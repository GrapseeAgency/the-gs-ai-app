package com.grapsee.gsai.ui.chat.content

import android.animation.ValueAnimator
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.ResolvedTextDirection
import com.grapsee.gsai.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

internal val LocalFluidResponseText = staticCompositionLocalOf { false }
internal val LocalFluidFreshMount = staticCompositionLocalOf { false }
internal val LocalFluidNewBlockFloor = staticCompositionLocalOf { Int.MAX_VALUE }
internal val LocalFluidFramePulse = staticCompositionLocalOf<FluidFramePulse?> { null }

/** One display-clock lease per mounted response, not one per paragraph.
 * Writes are observed in draw, never in the transcript's composition.
 * Empty/settled replies do not ask the display for continuous frames. */
internal class FluidFramePulse {
    private val clock = mutableLongStateOf(System.nanoTime())
    private val deadline = mutableLongStateOf(0L)
    val now: Long get() = clock.longValue

    fun arm(until: Long, receivedAt: Long) {
        if (deadline.longValue <= clock.longValue) clock.longValue = receivedAt
        if (until > deadline.longValue) deadline.longValue = until
    }

    suspend fun run() {
        while (coroutineContext.isActive) {
            snapshotFlow { deadline.longValue }.first { it > clock.longValue }
            do {
                withFrameNanos { clock.longValue = System.nanoTime() }
            } while (deadline.longValue > clock.longValue)
        }
    }
}

@Composable
internal fun rememberFluidFramePulse(): FluidFramePulse {
    val pulse = remember { FluidFramePulse() }
    LaunchedEffect(pulse) { pulse.run() }
    return pulse
}

private data class FluidRegion(
    val band: FluidTextBand,
    val path: Path,
    val rect: Rect,
    val first: Float,
    val last: Float,
    val rtl: Boolean
)

/**
 * One native shaped Text, including all inline content and accessibility text.
 * The moving ink front is a draw-phase mask over the actual native glyph layout.
 * No full-paragraph shader, per-frame RuntimeShader creation or text relayout.
 * A mounted live node is retained until its existing optical tail settles.
 */
@Composable
internal fun FluidResponseText(
    text: AnnotatedString,
    style: TextStyle,
    color: Color,
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
    modifier: Modifier = Modifier,
    softWrap: Boolean = true,
    animate: Boolean = true
) {
    val allowed = !SettingsStore.reduceMotion && !SettingsStore.reduceAnimations &&
        !SettingsStore.highContrast && ValueAnimator.areAnimatorsEnabled()
    val fresh = LocalFluidFreshMount.current
    val live = animate && LocalFluidResponseText.current && allowed
    // Keep the native Text call site stable. A branch swapping two Text
    // nodes changes layout/selection identity precisely at the reveal edge.
    val motion = remember { FluidTextMotion(if (live && fresh) "" else text.text) }
    val bands = remember { mutableStateOf(emptyList<FluidTextBand>()) }
    val wasLive = remember { booleanArrayOf(live) }
    val pulse = LocalFluidFramePulse.current ?: rememberFluidFramePulse()
    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }
    val active = allowed && (live || bands.value.isNotEmpty() || (fresh && wasLive[0]))
    SideEffect {
        val now = System.nanoTime()
        val next = motion.update(text.text, now, active, sourceAppend = fresh)
        bands.value = next
        next.maxOfOrNull { it.bornNanos + it.durationNanos }?.let { pulse.arm(it, now) }
        wasLive[0] = live || (allowed && wasLive[0] && next.isNotEmpty())
    }
    val deadline = bands.value.maxOfOrNull { it.bornNanos + it.durationNanos }
    // Cleanup is driven by the same clock, not a restarted animation or
    // a fixed sleep. The modifier detaches only after the final ink frame.
    LaunchedEffect(pulse, deadline) {
        if (deadline != null) {
            snapshotFlow { pulse.now }.first { it >= deadline }
            bands.value = bands.value.filter { it.progress(pulse.now) < 1f }
        }
    }

    Text(
        text = text,
        style = style,
        color = color,
        inlineContent = inlineContent,
        softWrap = softWrap,
        onTextLayout = { layout.value = it },
        modifier = modifier.then(if (active) Modifier.drawWithCache {
            val measured = layout.value
            val regions = if (measured != null && measured.layoutInput.text.text == text.text) {
                bands.value.flatMap { band ->
                    if (band.start < 0 || band.end > text.length || band.start >= band.end) emptyList()
                    else {
                        val firstLine = measured.getLineForOffset(band.start)
                        val lastLine = measured.getLineForOffset(band.end - 1)
                        (firstLine..lastLine).mapNotNull { line ->
                            val start = maxOf(band.start, measured.getLineStart(line))
                            val end = minOf(band.end, measured.getLineEnd(line))
                            if (start >= end) return@mapNotNull null
                            val path = measured.getPathForRange(start, end)
                            val rect = path.getBounds()
                            if (rect.width <= 0f || rect.height <= 0f) return@mapNotNull null
                            FluidRegion(band, path, rect, band.fractionAt(start), band.fractionAt(end),
                                measured.getBidiRunDirection(start) == ResolvedTextDirection.Rtl)
                        }
                    }
                }
            } else emptyList()
            val layerPaint = Paint()
            val nativeClip = android.graphics.Rect()
            onDrawWithContent {
                val now = pulse.now
                val active = regions.filter { it.band.progress(now) < 1f }
                if (active.isEmpty()) {
                    drawContent()
                    return@onDrawWithContent
                }
                // Isolate the visible part of the arriving ink only. The
                // settled paragraph is drawn directly outside this layer.
                // On long answers the offscreen text never allocates a texture.
                val hasClip = drawContext.canvas.nativeCanvas.getClipBounds(nativeClip)
                val l = maxOf(0f, active.minOf { it.rect.left } - 1f, if (hasClip) nativeClip.left.toFloat() else 0f)
                val t = maxOf(0f, active.minOf { it.rect.top } - 1f, if (hasClip) nativeClip.top.toFloat() else 0f)
                val r = minOf(size.width, active.maxOf { it.rect.right } + 1f, if (hasClip) nativeClip.right.toFloat() else size.width)
                val b = minOf(size.height, active.maxOf { it.rect.bottom } + 1f, if (hasClip) nativeClip.bottom.toFloat() else size.height)
                if (r <= l || b <= t) {
                    drawContent()
                    return@onDrawWithContent
                }
                clipRect(l, t, r, b, clipOp = ClipOp.Difference) {
                    this@onDrawWithContent.drawContent()
                }
                clipRect(l, t, r, b) {
                    drawContext.canvas.saveLayer(Rect(l, t, r, b), layerPaint)
                    try {
                        this@onDrawWithContent.drawContent()
                        for (region in active) {
                            val (band, path, rect, first, last, rtl) = region
                            if (rect.right <= l || rect.left >= r || rect.bottom <= t || rect.top >= b) continue
                            val stops = (0..6).map { j ->
                                val x = j / 6f
                                val phase = first + (last - first) * (if (rtl) 1f - x else x)
                                Color.Black.copy(alpha = (1f - band.opacity(now, phase)).coerceIn(0f, 1f))
                            }
                            drawPath(path, Brush.horizontalGradient(stops, rect.left, rect.right), blendMode = BlendMode.DstOut)
                        }
                    } finally {
                        drawContext.canvas.restore()
                    }
                }
            }
        } else Modifier)
    )
}
