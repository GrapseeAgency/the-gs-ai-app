package com.grapsee.gsai.ui.chat.content

import android.animation.ValueAnimator
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import com.grapsee.gsai.data.SettingsStore

internal val LocalFluidResponseText = staticCompositionLocalOf { false }
internal val LocalFluidFreshMount = staticCompositionLocalOf { false }

/**
 * Native Compose text with an append-only, draw-phase feather. No second text
 * layout, invisible buffer, substring replacement, per-character composables
 * or animated paragraph size. The same Text owns links, inline glyphs and layout.
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
    val enabled = animate && LocalFluidResponseText.current &&
        !SettingsStore.reduceMotion && !SettingsStore.reduceAnimations &&
        !SettingsStore.highContrast && ValueAnimator.areAnimatorsEnabled()
    if (!enabled) {
        Text(text = text, style = style, color = color, inlineContent = inlineContent, modifier = modifier, softWrap = softWrap)
        return
    }

    val fresh = LocalFluidFreshMount.current
    val motion = remember { FluidTextMotion(if (fresh) "" else text.text) }
    val opticalLayer = rememberGraphicsLayer()
    val optical = remember { if (Build.VERSION.SDK_INT >= 33) runCatching { FluidOpticalEffect() }.getOrNull() else null }
    val bands = remember { mutableStateOf(emptyList<FluidTextBand>()) }
    val clock = remember { mutableLongStateOf(0L) }
    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }
    SideEffect {
        val now = System.nanoTime()
        bands.value = motion.update(text.text, now, true)
        clock.longValue = now
    }
    LaunchedEffect(text.text) {
        // The first frame also covers the initial effect/SideEffect ordering.
        do {
            withFrameNanos { clock.longValue = System.nanoTime() }
        } while (bands.value.any { it.progress(clock.longValue) < 1f })
    }

    Text(
        text = text,
        style = style,
        color = color,
        inlineContent = inlineContent,
        softWrap = softWrap,
        onTextLayout = { layout.value = it },
        modifier = modifier.drawWithCache {
            val measured = layout.value
            // Layout and source must describe the same frame. A Markdown
            // rewrite or an unfinished layout is drawn normally, not masked.
            val paths = if (measured != null && measured.layoutInput.text.text == text.text) {
                bands.value.mapNotNull { band ->
                    if (band.start >= 0 && band.end <= text.length && band.start < band.end)
                        band to measured.getPathForRange(band.start, band.end)
                    else null
                }
            } else emptyList()
            // Split at actual line boundaries; never soften a whole paragraph.
            val opticalRects = if (measured != null && measured.layoutInput.text.text == text.text) {
                paths.flatMap { (band, _) ->
                    val first = measured.getLineForOffset(band.start)
                    val last = measured.getLineForOffset(band.end - 1)
                    (first..last).mapNotNull { line ->
                        val start = maxOf(band.start, measured.getLineStart(line))
                        val end = minOf(band.end, measured.getLineEnd(line))
                        if (start < end) band to measured.getPathForRange(start, end).getBounds() else null
                    }
                }.filter { it.second.width > 0f && it.second.height > 0f }
            } else emptyList()
            val layerPaint = Paint()
            val bounds = Rect(0f, 0f, size.width, size.height)
            onDrawWithContent {
                val now = clock.longValue
                if (paths.none { it.first.progress(now) < 1f } || size.width <= 0f || size.height <= 0f ||
                    size.width * size.height > 3_000_000f) {
                    // Never allocate a huge transient GPU layer for a pathological
                    // unbroken paragraph; immediate full-contrast text wins.
                    drawContent()
                } else {
                    // One GPU pass, isolated to incoming glyph regions. A failed
                    // shader or software canvas keeps the native opacity fallback.
                    if (optical != null && drawContext.canvas.nativeCanvas.isHardwareAccelerated && opticalRects.size in 1..16) {
                        val effect = optical.effect(opticalRects, now, density)
                        if (effect != null) {
                            opticalLayer.record { this@onDrawWithContent.drawContent() }
                            opticalLayer.renderEffect = effect
                            drawLayer(opticalLayer)
                            return@onDrawWithContent
                        }
                    }
                    // DstOut is confined to this text's own temporary layer.
                    // The glass, neighboring words and controls are untouched.
                    drawContext.canvas.saveLayer(bounds, layerPaint)
                    try {
                        drawContent()
                        for ((band, path) in paths) {
                            val removal = 1f - band.opacity(now)
                            if (removal > 0f) drawPath(path, Color.Black.copy(alpha = removal), blendMode = BlendMode.DstOut)
                        }
                    } finally {
                        drawContext.canvas.restore()
                    }
                }
            }
        }
    )
}

/** Local optical settling, operating on premultiplied text pixels only. */
@Suppress("NewApi")
private class FluidOpticalEffect {
    private val shader = RuntimeShader(
        """
        uniform shader source;
        uniform float4 regions[16];
        uniform float4 motion[16];
        half4 main(float2 p) {
            for (int i = 0; i < 16; ++i) {
                float4 r = regions[i];
                float4 m = motion[i];
                if (m.w > 0.0 && p.x >= r.x && p.x < r.z && p.y >= r.y && p.y < r.w) {
                    float2 lo = r.xy + float2(0.01);
                    float2 hi = max(lo, r.zw - float2(0.01));
                    float2 q = p - float2(0.0, m.z);
                    half4 ink = source.eval(clamp(q, lo, hi)) * 0.40;
                    ink += source.eval(clamp(q + float2(m.y, 0.0), lo, hi)) * 0.15;
                    ink += source.eval(clamp(q - float2(m.y, 0.0), lo, hi)) * 0.15;
                    ink += source.eval(clamp(q + float2(0.0, m.y), lo, hi)) * 0.15;
                    ink += source.eval(clamp(q - float2(0.0, m.y), lo, hi)) * 0.15;
                    return ink * half(m.x);
                }
            }
            return source.eval(p);
        }
        """.trimIndent()
    )
    private val regions = FloatArray(64)
    private val motion = FloatArray(64)

    fun effect(rects: List<Pair<FluidTextBand, Rect>>, now: Long, density: Float): androidx.compose.ui.graphics.RenderEffect? {
        regions.fill(0f); motion.fill(0f)
        for ((index, item) in rects.withIndex()) {
            val (band, rect) = item
            val remaining = 1f - band.progress(now)
            if (remaining <= 0f) continue
            val at = index * 4
            regions[at] = rect.left
            regions[at + 1] = rect.top
            regions[at + 2] = rect.right
            regions[at + 3] = rect.bottom
            motion[at] = band.opacity(now)
            motion[at + 1] = 1.8f * density * remaining
            motion[at + 2] = 0.9f * density * remaining * remaining
            motion[at + 3] = 1f
        }
        return runCatching {
            shader.setFloatUniform("regions", regions)
            shader.setFloatUniform("motion", motion)
            android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "source").asComposeRenderEffect()
        }.getOrNull()
    }
}
