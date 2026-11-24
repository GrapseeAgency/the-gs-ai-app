package com.grapsee.gsai.ui.chat.content

import android.animation.ValueAnimator
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.graphics.asAndroidPath
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

// Frequently changing values use tracked locals; a token must not
// invalidate every non-consuming descendant of the response card.
internal val LocalFluidResponseText = compositionLocalOf { false }
internal val LocalFluidFreshMount = compositionLocalOf { false }
internal val LocalFluidNewBlockFloor = compositionLocalOf { Int.MAX_VALUE }
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
        wasLive[0] = allowed && (live || wasLive[0])
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
                            // Preserve native line geometry here; a mixed
                            // bidi line is subdivided below at actual run
                            // direction changes, never by visual guesses.
                            start to end
                        }.flatMap { (start, end) ->
                            val pieces = mutableListOf<FluidRegion>()
                            var at = start
                            while (at < end) {
                                val direction = measured.getBidiRunDirection(at)
                                var until = at + Character.charCount(text.text.codePointAt(at))
                                while (until < end && measured.getBidiRunDirection(until) == direction) {
                                    until += Character.charCount(text.text.codePointAt(until))
                                }
                                until = minOf(until, end)
                                val path = measured.getPathForRange(at, until)
                                val rect = path.getBounds()
                                if (rect.width > 0f && rect.height > 0f) {
                                    pieces += FluidRegion(band, path, rect, band.fractionAt(at), band.fractionAt(until),
                                        direction == ResolvedTextDirection.Rtl)
                                }
                                at = until
                            }
                            pieces
                        }
                    }
                }
            } else emptyList()
            val layerPaint = Paint()
            val nativeClip = android.graphics.Rect()
            // One immutable 33-stop curve per drawing cache. Only its local
            // affine transform changes during a frame; no per-range stop
            // arrays, sorting, Color objects or Brush allocation.
            val maskShader = android.graphics.LinearGradient(
                0f, 0f, 1f, 0f,
                IntArray(33) { index ->
                    val alpha = ((1f - fluidInkOpacity(index / 32f)) * 255f + 0.5f).toInt().coerceIn(0, 255)
                    android.graphics.Color.argb(alpha, 0, 0, 0)
                },
                null,
                android.graphics.Shader.TileMode.CLAMP
            )
            val maskMatrix = android.graphics.Matrix()
            val nativeMask = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
            }
            onDrawWithContent {
                // A settled node must not subscribe to other paragraphs'
                // display pulses. Check its cached geometry before reading
                // the shared frame state.
                if (regions.isEmpty()) {
                    drawContent()
                    return@onDrawWithContent
                }
                val now = pulse.now
                // Compute only the intersection of the animated ink and
                // native viewport. No intermediate active-list allocation,
                // and offscreen lines cannot enlarge the offscreen layer.
                val hasClip = drawContext.canvas.nativeCanvas.getClipBounds(nativeClip)
                val clipL = if (hasClip) maxOf(0f, nativeClip.left.toFloat()) else 0f
                val clipT = if (hasClip) maxOf(0f, nativeClip.top.toFloat()) else 0f
                val clipR = if (hasClip) minOf(size.width, nativeClip.right.toFloat()) else size.width
                val clipB = if (hasClip) minOf(size.height, nativeClip.bottom.toFloat()) else size.height
                var l = clipR
                var t = clipB
                var r = clipL
                var b = clipT
                for (region in regions) {
                    if (region.band.progress(now) >= 1f) continue
                    val rect = region.rect
                    if (rect.right <= clipL || rect.left >= clipR || rect.bottom <= clipT || rect.top >= clipB) continue
                    l = minOf(l, maxOf(clipL, rect.left - 1f))
                    t = minOf(t, maxOf(clipT, rect.top - 1f))
                    r = maxOf(r, minOf(clipR, rect.right + 1f))
                    b = maxOf(b, minOf(clipB, rect.bottom + 1f))
                }
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
                        for (region in regions) {
                            val (band, path, rect, first, last, rtl) = region
                            if (band.progress(now) >= 1f) continue
                            if (rect.right <= l || rect.left >= r || rect.bottom <= t || rect.top >= b) continue
                            val logicalFirst = if (rtl) last else first
                            val logicalLast = if (rtl) first else last
                            val phaseFirst = band.phaseFrom + (band.phaseTo - band.phaseFrom) * logicalFirst
                            val phaseLast = band.phaseFrom + (band.phaseTo - band.phaseFrom) * logicalLast
                            val projection = fluidInkProjection(band.progress(now), phaseFirst, phaseLast, band.travel)
                            val solid = projection.solid
                            if (solid != null) {
                                val removal = 1f - solid
                                if (removal > 0f) drawPath(path, Color.Black.copy(alpha = removal), blendMode = BlendMode.DstOut)
                            } else {
                                maskMatrix.setScale(rect.width * projection.scale, 1f)
                                maskMatrix.postTranslate(rect.left + rect.width * projection.origin, 0f)
                                maskShader.setLocalMatrix(maskMatrix)
                                nativeMask.shader = maskShader
                                drawContext.canvas.nativeCanvas.drawPath(path.asAndroidPath(), nativeMask)
                            }
                        }
                    } finally {
                        drawContext.canvas.restore()
                    }
                }
            }
        } else Modifier)
    )
}
