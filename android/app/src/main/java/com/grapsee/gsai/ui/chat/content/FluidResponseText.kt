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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import com.grapsee.gsai.data.SettingsStore

internal val LocalFluidResponseText = staticCompositionLocalOf { false }

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
    inlineContent: Map<String, InlineTextContent>,
    modifier: Modifier = Modifier
) {
    val enabled = LocalFluidResponseText.current &&
        !SettingsStore.reduceMotion && !SettingsStore.reduceAnimations &&
        !SettingsStore.highContrast && ValueAnimator.areAnimatorsEnabled()
    if (!enabled) {
        Text(text = text, style = style, color = color, inlineContent = inlineContent, modifier = modifier)
        return
    }

    val motion = remember { FluidTextMotion(text.text) }
    val bands = remember { mutableStateOf(emptyList<FluidTextBand>()) }
    val clock = remember { mutableLongStateOf(0L) }
    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }
    SideEffect {
        val now = System.nanoTime()
        bands.value = motion.update(text.text, now, true)
        clock.longValue = now
    }
    LaunchedEffect(text.text) {
        while (bands.value.any { it.progress(clock.longValue) < 1f }) {
            withFrameNanos { clock.longValue = System.nanoTime() }
        }
    }

    Text(
        text = text,
        style = style,
        color = color,
        inlineContent = inlineContent,
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
            val layerPaint = Paint()
            val bounds = Rect(0f, 0f, size.width, size.height)
            onDrawWithContent {
                val now = clock.longValue
                if (paths.none { it.first.progress(now) < 1f } || size.width <= 0f || size.height <= 0f) {
                    drawContent()
                } else {
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
