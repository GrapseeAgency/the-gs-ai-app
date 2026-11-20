package com.grapsee.gsai.ui.components

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas as PlatformCanvas
import android.graphics.Paint
import android.graphics.Path as PlatformPath
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.LocalGsIsDark
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private val GsMetalCapsule = RoundedCornerShape(percent = 50)

/**
 * Android Compose adaptation of Libraries.dev metal-fx v2's silver material.
 * The upstream repository has SwiftUI MetalFxKit and a React Native Skia port,
 * but no native Compose implementation. API 33+ runs its Paper liquidMetal
 * fragment shader as AGSL; older Android versions evaluate the same field,
 * stripe, contour, tint and chromatic-channel equations into a small bitmap.
 *
 * Adapted sources and required notices are in THIRD_PARTY_NOTICES.md.
 */
@Suppress("NewApi")
private class GsLiquidMetalRenderer {
    private var runtimeShader: Shader? = createRuntimeShader()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bounds = RectF()
    private var fallbackBitmap: Bitmap? = null
    private var fallbackPixels = IntArray(FALLBACK_WIDTH * FALLBACK_HEIGHT)
    private var lastFallbackTime = Float.NaN
    private var lastFallbackDarkTheme: Boolean? = null
    private var lastFallbackDensity = Float.NaN
    private var lastFallbackMapping: SheetMapping? = null

    fun draw(
        canvas: PlatformCanvas,
        width: Float,
        height: Float,
        radius: Float,
        time: Float,
        density: Float,
        darkTheme: Boolean,
    ) {
        if (width <= 0f || height <= 0f) return
        bounds.set(0f, 0f, width, height)
        val material = silverMaterial(darkTheme)
        val mapping = sheetMapping(width, height, density)
        val shader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runtimeShader as? RuntimeShader
        } else null
        if (shader != null) {
            try {
                shader.setFloatUniform("uvOrigin", mapping.originX, mapping.originY)
                shader.setFloatUniform(
                    "uvScale",
                    mapping.scaleX / max(width, 1f),
                    mapping.scaleY / max(height, 1f),
                )
                shader.setFloatUniform("time", time)
                shader.setFloatUniform("colorBack", 0f, 0f, 0f, 0f)
                shader.setFloatUniform("colorTint", material.tintR, material.tintG, material.tintB, material.tintA)
                shader.setFloatUniform("repetition", material.repetition)
                shader.setFloatUniform("softness", material.softness)
                shader.setFloatUniform("shiftRed", material.shiftRed)
                shader.setFloatUniform("shiftBlue", material.shiftBlue)
                shader.setFloatUniform("distortion", material.distortion)
                shader.setFloatUniform("contour", material.contour)
                shader.setFloatUniform("angle", material.angle)
                shader.setFloatUniform("opacityMul", material.opacity)
                shader.setFloatUniform("ditherScale", density)
                paint.shader = shader
                canvas.drawRoundRect(bounds, radius, radius, paint)
                paint.shader = null
                return
            } catch (_: RuntimeException) {
                // Keep the control visible if a particular vendor AGSL compiler
                // rejects the shader; the CPU path uses the same upstream field.
                runtimeShader = null
                paint.shader = null
            }
        }
        drawCpuMaterial(canvas, width, height, radius, time, density, darkTheme, mapping)
    }

    private fun drawCpuMaterial(
        canvas: PlatformCanvas,
        width: Float,
        height: Float,
        radius: Float,
        time: Float,
        density: Float,
        darkTheme: Boolean,
        mapping: SheetMapping,
    ) {
        val bitmap = fallbackBitmap ?: Bitmap.createBitmap(
            FALLBACK_WIDTH,
            FALLBACK_HEIGHT,
            Bitmap.Config.ARGB_8888,
        ).also { fallbackBitmap = it }
        // A 20 Hz CPU material clock is enough for the sub-33 fallback and
        // avoids burning a full frame on a tiny decorative surface.
        val sampledTime = floor(time * CPU_FALLBACK_FPS) / CPU_FALLBACK_FPS
        if (sampledTime != lastFallbackTime || darkTheme != lastFallbackDarkTheme ||
            density != lastFallbackDensity || mapping != lastFallbackMapping) {
            fillCpuPixels(sampledTime, density, darkTheme, mapping)
            bitmap.setPixels(fallbackPixels, 0, FALLBACK_WIDTH, 0, 0, FALLBACK_WIDTH, FALLBACK_HEIGHT)
            lastFallbackTime = sampledTime
            lastFallbackDarkTheme = darkTheme
            lastFallbackDensity = density
            lastFallbackMapping = mapping
        }

        val clip = PlatformPath().apply {
            addRoundRect(bounds, radius, radius, PlatformPath.Direction.CW)
        }
        val save = canvas.save()
        canvas.clipPath(clip)
        paint.shader = null
        canvas.drawBitmap(bitmap, null, bounds, paint)
        canvas.restoreToCount(save)
    }

    private fun fillCpuPixels(time: Float, density: Float, darkTheme: Boolean, mapping: SheetMapping) {
        val material = silverMaterial(darkTheme)
        val texel = 1f / 192f
        var index = 0
        for (y in 0 until FALLBACK_HEIGHT) {
            val py = (y + 0.5f) / FALLBACK_HEIGHT
            for (x in 0 until FALLBACK_WIDTH) {
                val px = (x + 0.5f) / FALLBACK_WIDTH
                val uvX = mapping.originX + px * mapping.scaleX
                val uvY = mapping.originY + py * mapping.scaleY
                val value = sampleMaterial(uvX, uvY, time, texel, material, density)
                val alpha = value.a.coerceIn(0f, 1f)
                fun straight(channel: Float): Int =
                    if (alpha <= 0.0001f) 0 else (channel / alpha * 255f).toInt().coerceIn(0, 255)
                fallbackPixels[index++] = android.graphics.Color.argb(
                    (alpha * 255f).toInt().coerceIn(0, 255),
                    straight(value.r),
                    straight(value.g),
                    straight(value.b),
                )
            }
        }
    }

    private fun createRuntimeShader(): Shader? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return runCatching { RuntimeShader(LIQUID_METAL_AGSL) }.getOrNull()
    }

    private data class Material(
        val tintR: Float,
        val tintG: Float,
        val tintB: Float,
        val tintA: Float,
        val repetition: Float = 1.5f,
        val softness: Float = 0.05f,
        val shiftRed: Float = 0.3f,
        val shiftBlue: Float = 0.3f,
        val distortion: Float = 0.1f,
        val contour: Float = 0.4f,
        val angle: Float = 90f,
        val opacity: Float,
    )

    private data class SheetMapping(
        val originX: Float,
        val originY: Float,
        val scaleX: Float,
        val scaleY: Float,
    )

    private fun silverMaterial(darkTheme: Boolean) = if (darkTheme) {
        Material(1f, 1f, 1f, 0.4f, opacity = 0.88f)
    } else {
        Material(1f, 1f, 1f, 0.25f, opacity = 1f)
    }

    private fun sheetMapping(width: Float, height: Float, density: Float): SheetMapping {
        // Compose's native canvas is measured in physical pixels; the upstream
        // canonical sheet is 140×40 dp (SwiftUI points). Convert its crop window
        // to pixels before calculating the normalized UV span.
        val canonicalScale = 1.6f * density.coerceAtLeast(0.1f)
        val fx = min(1f, width / (140f * canonicalScale))
        val fy = min(1f, height / (40f * canonicalScale))
        return SheetMapping(
            originX = 0.5f - 0.5f * fx,
            originY = 0.5f - 0.5f * fy,
            scaleX = fx,
            scaleY = fy,
        )
    }

    private data class Field(
        val direction: Float,
        val bump: Float,
        val edge: Float,
        val diagBl: Float,
        val diagTl: Float,
    )

    private fun sampleMaterial(
        u: Float,
        v: Float,
        time: Float,
        texel: Float,
        material: Material,
        density: Float,
    ): CpuColor {
        val t = 0.3f * (time + 2.8f)
        val cw = material.repetition * 2f
        val noise = snoise(u - t, v - t)
        val f = field(u, v, t, noise, cw, material)
        val fx = field(u + texel, v, t, noise, cw, material)
        val fy = field(u, v + texel, t, noise, cw, material)
        val fw = abs(fx.direction - f.direction) + abs(fy.direction - f.direction)

        val uvcX = u.coerceIn(0f, 1f)
        val uvcY = v.coerceIn(0f, 1f)
        val maskX = min(uvcX, 1f - uvcX)
        val maskY = min(uvcY, 1f - uvcY)
        val rawEdge = (1f - powf(ss(0f, 0.5f, maskX), 0.25f) * powf(ss(0f, 0.5f, maskY), 0.25f)).coerceIn(0f, 1f)
        var opacity = 1f - ss(0.896f, 0.9f, rawEdge)

        val color2Blue = 0.1f + 0.1f * ss(0.7f, 1.3f, f.diagTl)
        val thin1 = 0.12f / cw * (1f - 0.4f * f.bump)
        val thin2 = 0.07f / cw * (1f + 0.4f * f.bump)
        val w0 = cw * thin1
        var w1 = cw * thin2
        val w2 = 1f - thin1 - thin2

        var dR = (1f - f.bump).coerceIn(0f, 1f)
        dR += 0.03f * f.bump * noise
        dR += 5f * (ss(-0.1f, 0.2f, v) * (1f - ss(0.1f, 0.5f, v))) *
            (ss(0.4f, 0.6f, f.bump) * (1f - ss(0.4f, 1f, f.bump)))
        dR -= f.diagBl
        var dB = (1f - f.bump).coerceIn(0f, 1f) * 1.3f
        dB += (ss(0f, 0.4f, v) * (1f - ss(0.1f, 0.8f, v))) *
            (ss(0.4f, 0.6f, f.bump) * (1f - ss(0.4f, 0.8f, f.bump)))
        dB -= 0.2f * f.edge
        dR *= material.shiftRed / 20f
        dB *= material.shiftBlue / 20f

        val blur = material.softness / 15f
        w1 -= 0.02f * ss(0f, 1f, f.edge + f.bump)
        val red = colorChanges(0.98f, 0.1f, fract(f.direction + dR), w0, w1, w2, blur + fw, f.bump, material.tintR, material.tintA)
        val green = colorChanges(0.98f, 0.1f, fract(f.direction), w0, w1, w2, blur + fw, f.bump, material.tintG, material.tintA)
        val blue = colorChanges(1f, color2Blue, fract(f.direction - dB), w0, w1, w2, blur + fw, f.bump, material.tintB, material.tintA)
        val bgA = 0f
        val bgR = 0f
        val bgG = 0f
        val bgB = 0f
        var r = red * opacity
        var g = green * opacity
        var b = blue * opacity
        r += bgR * bgA * (1f - opacity)
        g += bgG * bgA * (1f - opacity)
        b += bgB * bgA * (1f - opacity)
        opacity += bgA * (1f - opacity)
        val dither = (fract(sin((0.014f * 12.9898f * density * u + 0.014f * 78.233f * density * v)) * 43758.5453f) - 0.5f) / 256f
        return CpuColor(
            (r + dither).coerceIn(0f, 1f) * material.opacity,
            (g + dither).coerceIn(0f, 1f) * material.opacity,
            (b + dither).coerceIn(0f, 1f) * material.opacity,
            opacity * material.opacity,
        )
    }

    private fun field(u: Float, v: Float, t: Float, noise: Float, cw: Float, material: Material): Field {
        val uvcX = u.coerceIn(0f, 1f)
        val uvcY = v.coerceIn(0f, 1f)
        val maskX = min(uvcX, 1f - uvcX)
        val maskY = min(uvcY, 1f - uvcY)
        val maskPowX = powf(ss(0f, 0.5f, maskX), 0.25f)
        val maskPowY = powf(ss(0f, 0.5f, maskY), 0.25f)
        var edge = (1f - maskPowX * maskPowY).coerceIn(0f, 1f)
        edge = mix(ss(0.896f, 0.9f, edge), edge, ss(0f, 0.4f, material.contour)) * 1.2f

        val angle = (-material.angle + 70f) * PI.toFloat() / 180f
        val rX = u - 0.5f
        val rY = v - 0.5f
        val rotatedX = rX * cos(angle) - rY * sin(angle) + 0.5f
        val rotatedY = rX * sin(angle) + rY * cos(angle) + 0.5f
        val diagBl = rotatedX - rotatedY
        val diagTl = rotatedX + rotatedY
        var gx = u - 0.5f
        var gy = v - 0.5f
        val dist = sqrt(gx * gx + (gy + 0.2f * diagBl) * (gy + 0.2f * diagBl))
        val rotateBy = (0.25f - 0.2f * diagBl) * PI.toFloat()
        val oldGx = gx
        gx = cos(rotateBy) * oldGx - sin(rotateBy) * gy
        gy = sin(rotateBy) * oldGx + cos(rotateBy) * gy
        var direction = gx

        val uy = max(uvcY, 0f)
        var bump = 1f - powf(1.8f * dist, 1.2f)
        bump *= powf(uy, 0.3f)
        edge += (1f - edge) * material.distortion * noise
        direction += diagBl
        val sE = ss(0f, 1f, edge)
        direction -= 2f * noise * diagBl * (sE * (1f - sE))
        val c51 = ss(0.5f, 1f, material.contour)
        direction *= mix(1f, 1f - edge, c51)
        direction -= 1.7f * edge * c51
        direction += 0.2f * powf(material.contour, 4f) * (1f - sE)
        bump *= powf(uy, 0.1f).coerceIn(0.3f, 1f)
        direction *= 0.1f + (1.1f - edge) * bump
        direction *= 0.4f + 0.6f * (1f - ss(0.5f, 1f, edge))
        direction += 0.18f * (ss(0.1f, 0.2f, v) * (1f - ss(0.2f, 0.4f, v)))
        direction += 0.03f * (ss(0.1f, 0.2f, 1f - v) * (1f - ss(0.2f, 0.4f, 1f - v)))
        direction *= 0.5f + 0.5f * v * v
        direction *= cw
        direction -= t
        return Field(direction, bump, edge, diagBl, diagTl)
    }

    private fun colorChanges(
        c1: Float,
        c2: Float,
        p: Float,
        w0: Float,
        w1: Float,
        w2: Float,
        blurIn: Float,
        bump: Float,
        tint: Float,
        tintAlpha: Float,
    ): Float {
        val blur = max(blurIn, 1e-5f)
        var color = mix(c2, c1, ss(0f, 2f * blur, p))
        var border = w0
        color = mix(color, c2, ss(border, border + 2f * blur, p))
        border = w0 + 0.4f * (1f - bump) * w1
        color = mix(color, c1, ss(border, border + 2f * blur, p))
        border = w0 + 0.5f * (1f - bump) * w1
        color = mix(color, c2, ss(border, border + 2f * blur, p))
        border = w0 + w1
        color = mix(color, c1, ss(border, border + 2f * blur, p))
        val gradient = mix(c1, c2, ss(0f, 1f, (p - w0 - w1) / w2))
        color = mix(color, gradient, ss(border, border + 0.5f * blur, p))
        return mix(color, 1f - min(1f, (1f - color) / max(tint, 0.0001f)), tintAlpha)
    }

    private fun snoise(x: Float, y: Float): Float {
        val cX = 0.2113248654f
        val cY = 0.3660254038f
        val cZ = -0.5773502692f
        val cW = 0.0243902439f
        val skew = (x + y) * cY
        var ix = floor(x + skew)
        var iy = floor(y + skew)
        val unskew = (ix + iy) * cX
        val x0 = x - ix + unskew
        val y0 = y - iy + unskew
        val i1x = if (x0 > y0) 1f else 0f
        val i1y = if (x0 > y0) 0f else 1f
        val x1 = x0 + cX - i1x
        val y1 = y0 + cX - i1y
        val x2 = x0 + cZ
        val y2 = y0 + cZ
        ix = glslMod(ix, 289f)
        iy = glslMod(iy, 289f)

        fun permute(value: Float): Float = glslMod(((value * 34f) + 1f) * value, 289f)
        val p0 = permute(permute(iy) + ix)
        val p1 = permute(permute(iy + i1y) + ix + i1x)
        val p2 = permute(permute(iy + 1f) + ix + 1f)
        fun contribution(p: Float, px: Float, py: Float): Float {
            var m = max(0.5f - px * px - py * py, 0f)
            m *= m
            m *= m
            val xh = 2f * fract(p * cW) - 1f
            val h = abs(xh) - 0.5f
            val ox = floor(xh + 0.5f)
            val a0 = xh - ox
            m *= 1.792842914f - 0.8537347209f * (a0 * a0 + h * h)
            val g = a0 * px + h * py
            return m * g
        }
        return 130f * (
            contribution(p0, x0, y0) +
                contribution(p1, x1, y1) +
                contribution(p2, x2, y2)
            )
    }

    private fun ss(a: Float, b: Float, x: Float): Float {
        val end = max(b, a + 1e-6f)
        val t = ((x - a) / (end - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun fract(x: Float) = x - floor(x)
    private fun glslMod(x: Float, y: Float) = x - y * floor(x / y)
    private fun powf(x: Float, y: Float) = x.toDouble().pow(y.toDouble()).toFloat()

    private companion object {
        const val FALLBACK_WIDTH = 96
        const val FALLBACK_HEIGHT = 48
        const val CPU_FALLBACK_FPS = 20f

        // Ported from packages/metal-fx/ports/react-native/metal-fx-native/src/shader.ts
        // (LIQUID_METAL_SKSL); uniforms and the uv sheet mapping are bound above.
        // The upstream source's copyright/license attribution is retained in
        // THIRD_PARTY_NOTICES.md.
        const val LIQUID_METAL_AGSL = """
            uniform float2 uvOrigin;
            uniform float2 uvScale;
            uniform float time;
            uniform float4 colorBack;
            uniform float4 colorTint;
            uniform float repetition;
            uniform float softness;
            uniform float shiftRed;
            uniform float shiftBlue;
            uniform float distortion;
            uniform float contour;
            uniform float angle;
            uniform float opacityMul;
            uniform float ditherScale;

            const float PI = 3.14159265358979323846;
            const float TEXEL = 1.0 / 192.0;

            float3 permute3(float3 x) { return mod(((x * 34.0) + 1.0) * x, 289.0); }

            float snoise(float2 v) {
                const float4 C = float4(0.211324865405187, 0.366025403784439, -0.577350269189626, 0.024390243902439);
                float2 i = floor(v + dot(v, C.yy));
                float2 x0 = v - i + dot(i, C.xx);
                float2 i1 = (x0.x > x0.y) ? float2(1.0, 0.0) : float2(0.0, 1.0);
                float4 x12 = x0.xyxy + C.xxzz;
                x12.xy -= i1;
                i = mod(i, 289.0);
                float3 p = permute3(permute3(i.y + float3(0.0, i1.y, 1.0)) + i.x + float3(0.0, i1.x, 1.0));
                float3 m = max(0.5 - float3(dot(x0, x0), dot(x12.xy, x12.xy), dot(x12.zw, x12.zw)), 0.0);
                m = m * m;
                m = m * m;
                float3 x = 2.0 * fract(p * C.www) - 1.0;
                float3 h = abs(x) - 0.5;
                float3 ox = floor(x + 0.5);
                float3 a0 = x - ox;
                m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);
                float3 g;
                g.x = a0.x * x0.x + h.x * x0.y;
                g.yz = a0.yz * x12.xz + h.yz * x12.yw;
                return 130.0 * dot(m, g);
            }

            float ss(float e0, float e1, float x) { return smoothstep(e0, max(e1, e0 + 1e-6), x); }
            float2 rot2(float2 uv, float th) { return float2(cos(th) * uv.x - sin(th) * uv.y, sin(th) * uv.x + cos(th) * uv.y); }

            float colorChanges(float c1, float c2, float p, float3 w, float blur, float bump, float tint, float tintA) {
                blur = max(blur, 1e-5);
                float ch = mix(c2, c1, ss(0.0, 2.0 * blur, p));
                float border = w[0];
                ch = mix(ch, c2, ss(border, border + 2.0 * blur, p));
                border = w[0] + 0.4 * (1.0 - bump) * w[1];
                ch = mix(ch, c1, ss(border, border + 2.0 * blur, p));
                border = w[0] + 0.5 * (1.0 - bump) * w[1];
                ch = mix(ch, c2, ss(border, border + 2.0 * blur, p));
                border = w[0] + w[1];
                ch = mix(ch, c1, ss(border, border + 2.0 * blur, p));
                float gt = (p - w[0] - w[1]) / w[2];
                float gradient = mix(c1, c2, ss(0.0, 1.0, gt));
                ch = mix(ch, gradient, ss(border, border + 0.5 * blur, p));
                ch = mix(ch, 1.0 - min(1.0, (1.0 - ch) / max(tint, 0.0001)), tintA);
                return ch;
            }

            // direction, bump, edge, diagBL
            float4 fieldA(float2 uv, float t, float noise, float cw, float2 rot) {
                float2 uvc = clamp(uv, 0.0, 1.0);
                float2 mask = min(uvc, 1.0 - uvc);
                float maskX = pow(ss(0.0, 0.5, mask.x), 0.25);
                float maskY = pow(ss(0.0, 0.5, mask.y), 0.25);
                float edge = clamp(1.0 - maskX * maskY, 0.0, 1.0);
                const float fwE = 0.002;
                edge = mix(ss(0.9 - 2.0 * fwE, 0.9, edge), edge, ss(0.0, 0.4, contour));
                edge = 1.2 * edge;

                float2 r = uv - 0.5;
                float2 rotated = float2(r.x * rot.x - r.y * rot.y, r.x * rot.y + r.y * rot.x) + 0.5;
                float diagBL = rotated.x - rotated.y;
                float2 g = uv - 0.5;
                float dist = length(g + float2(0.0, 0.2 * diagBL));
                g = rot2(g, (0.25 - 0.2 * diagBL) * PI);
                float direction = g.x;
                float uy = max(uvc.y, 0.0);
                float bump = 1.0 - pow(1.8 * dist, 1.2);
                bump *= pow(uy, 0.3);
                edge += (1.0 - edge) * distortion * noise;
                direction += diagBL;
                float sE = ss(0.0, 1.0, edge);
                direction -= 2.0 * noise * diagBL * (sE * (1.0 - sE));
                float c51 = ss(0.5, 1.0, contour);
                direction *= mix(1.0, 1.0 - edge, c51);
                direction -= 1.7 * edge * c51;
                direction += 0.2 * pow(contour, 4.0) * (1.0 - sE);
                bump *= clamp(pow(uy, 0.1), 0.3, 1.0);
                direction *= (0.1 + (1.1 - edge) * bump);
                direction *= (0.4 + 0.6 * (1.0 - ss(0.5, 1.0, edge)));
                direction += 0.18 * (ss(0.1, 0.2, uv.y) * (1.0 - ss(0.2, 0.4, uv.y)));
                direction += 0.03 * (ss(0.1, 0.2, 1.0 - uv.y) * (1.0 - ss(0.2, 0.4, 1.0 - uv.y)));
                direction *= (0.5 + 0.5 * uv.y * uv.y);
                direction *= cw;
                direction -= t;
                return float4(direction, bump, edge, diagBL);
            }

            half4 main(float2 pos) {
                float2 uv = uvOrigin + pos * uvScale;
                float t = 0.3 * (time + 2.8);
                float cw = repetition * 2.0;
                float a = (-angle + 70.0) * PI / 180.0;
                float2 rot = float2(cos(a), sin(a));
                float noise = snoise(uv - t);
                float4 f = fieldA(uv, t, noise, cw, rot);
                float4 fx = fieldA(uv + float2(TEXEL, 0.0), t, noise, cw, rot);
                float4 fy = fieldA(uv + float2(0.0, TEXEL), t, noise, cw, rot);
                float fw = abs(fx.x - f.x) + abs(fy.x - f.x);
                float direction = f.x, bump = f.y, edge = f.z, diagBL = f.w;
                float2 r = uv - 0.5;
                float diagTL = (r.x * rot.x - r.y * rot.y + 0.5) + (r.x * rot.y + r.y * rot.x + 0.5);
                const float fwE = 0.002;
                float2 uvc = clamp(uv, 0.0, 1.0);
                float2 mask = min(uvc, 1.0 - uvc);
                float rawEdge = clamp(1.0 - pow(ss(0.0, 0.5, mask.x), 0.25) * pow(ss(0.0, 0.5, mask.y), 0.25), 0.0, 1.0);
                float opacity = 1.0 - ss(0.9 - 2.0 * fwE, 0.9, rawEdge);
                float3 color1 = float3(0.98, 0.98, 1.0);
                float3 color2 = float3(0.1, 0.1, 0.1 + 0.1 * ss(0.7, 1.3, diagTL));
                float thin1 = 0.12 / cw * (1.0 - 0.4 * bump);
                float thin2 = 0.07 / cw * (1.0 + 0.4 * bump);
                float3 w = float3(cw * thin1, cw * thin2, 1.0 - thin1 - thin2);
                float cd = clamp(1.0 - bump, 0.0, 1.0);
                float dR = cd + 0.03 * bump * noise;
                dR += 5.0 * (ss(-0.1, 0.2, uv.y) * (1.0 - ss(0.1, 0.5, uv.y))) * (ss(0.4, 0.6, bump) * (1.0 - ss(0.4, 1.0, bump)));
                dR -= diagBL;
                float dB = cd * 1.3;
                dB += (ss(0.0, 0.4, uv.y) * (1.0 - ss(0.1, 0.8, uv.y))) * (ss(0.4, 0.6, bump) * (1.0 - ss(0.4, 0.8, bump)));
                dB -= 0.2 * edge;
                dR *= shiftRed / 20.0;
                dB *= shiftBlue / 20.0;
                float blur = softness / 15.0;
                w[1] -= 0.02 * ss(0.0, 1.0, edge + bump);
                float cr = colorChanges(color1.r, color2.r, fract(direction + dR), w, blur + fw, bump, colorTint.r, colorTint.a);
                float cg = colorChanges(color1.g, color2.g, fract(direction), w, blur + fw, bump, colorTint.g, colorTint.a);
                float cb = colorChanges(color1.b, color2.b, fract(direction - dB), w, blur + fw, bump, colorTint.b, colorTint.a);
                float3 color = float3(cr, cg, cb) * opacity;
                float3 bg = colorBack.rgb * colorBack.a;
                color = color + bg * (1.0 - opacity);
                opacity = opacity + colorBack.a * (1.0 - opacity);
                color += 1.0 / 256.0 * (fract(sin(dot(0.014 * pos * ditherScale, float2(12.9898, 78.233))) * 43758.5453123) - 0.5);
                float k = opacityMul;
                return half4(half3(color * k), half(opacity * k));
            }
        """
    }
}

private data class CpuColor(val r: Float, val g: Float, val b: Float, val a: Float)

private fun Modifier.gsLiquidMetal(
    renderer: GsLiquidMetalRenderer,
    time: Float,
    density: Float,
    darkTheme: Boolean,
): Modifier = drawBehind {
    val radius = min(size.width, size.height) * 0.5f
    renderer.draw(
        canvas = drawContext.canvas.nativeCanvas,
        width = size.width,
        height = size.height,
        radius = radius,
        time = time,
        density = density,
        darkTheme = darkTheme,
    )
    // The white hairline and upper rim are the metallic badge's Figma layer;
    // the body material above is the upstream liquidMetal shader, not a Brush.
    drawRoundRect(
        color = Color.White.copy(alpha = 0.74f),
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(width = 0.8.dp.toPx()),
    )
    drawLine(
        color = Color.White.copy(alpha = 0.72f),
        start = Offset(radius * 0.58f, 1.dp.toPx()),
        end = Offset(size.width - radius * 0.58f, 1.dp.toPx()),
        strokeWidth = 0.7.dp.toPx(),
    )
}

@Composable
private fun rememberMetalTime(): Float {
    val elapsed by rememberInfiniteTransition(label = "gsLiquidMetalClock").animateFloat(
        initialValue = 0f,
        targetValue = 36f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 36_000, easing = LinearEasing),
        ),
        label = "gsLiquidMetalTime",
    )
    // Honor the shared motion gate: freeze at a deterministic sample when the
    // reader requested reduced motion rather than merely slowing the material.
    return if (SettingsStore.reduceAnimations || SettingsStore.reduceMotion || !ValueAnimator.areAnimatorsEnabled()) 0f else elapsed
}

/** Metallic pill, backed by the manual Android port of MetalFx's silver preset. */
@Composable
fun GsMetallicBadge(label: String, modifier: Modifier = Modifier) {
    val renderer = remember { GsLiquidMetalRenderer() }
    val time = rememberMetalTime()
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val darkTheme = LocalGsIsDark.current
    Box(
        modifier = modifier
            .clip(GsMetalCapsule)
            .gsLiquidMetal(renderer, time, density, darkTheme)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.uppercase(),
            fontSize = 8.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            fontFamily = GsFonts.Product,
            color = Color(0xFF38424C),
            maxLines = 1,
        )
    }
}

/**
 * Visual half of the Auto switch; its parent row owns the actual toggle
 * semantics and state binding.
 */
@Composable
fun GsMetallicAutoControl(checked: Boolean, modifier: Modifier = Modifier) {
    val renderer = remember { GsLiquidMetalRenderer() }
    val checkboxRenderer = remember { GsLiquidMetalRenderer() }
    val time = rememberMetalTime()
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val darkTheme = LocalGsIsDark.current
    Row(
        modifier = modifier
            .clip(GsMetalCapsule)
            .gsLiquidMetal(renderer, time, density, darkTheme)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Auto",
            fontSize = 9.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            fontFamily = GsFonts.Product,
            color = Color(0xFF46515C),
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .gsLiquidMetal(checkboxRenderer, time + 1.6f, density, darkTheme),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    GsIcons.Check,
                    contentDescription = null,
                    tint = Color(0xFF48535E),
                    modifier = Modifier.size(9.dp),
                )
            }
        }
    }
}
