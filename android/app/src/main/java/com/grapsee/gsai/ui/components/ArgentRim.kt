package com.grapsee.gsai.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.theme.LocalGsIsDark
import kotlin.math.*

/** Regular cards keep the component limit. Only explicitly marked transient
 * panels may extend it, including tablet and landscape sheet dimensions.
 * The upper bound is a renderer guard, not a phone-size layout assumption. */
internal fun argentComponentSize(width: Float, height: Float, overlay: Boolean = false): Boolean =
    width.isFinite() && height.isFinite() && width >= 16f && height >= 16f &&
        if (overlay) width <= 2048f && height <= 2048f else width <= 720f && height <= 320f

/**
 * Original ArgentUI native shader, rim finish only. Draw a 0.75dp inset stroke
 * with a fixed reflection phase and neutral directional envelope. The centre
 * never receives a shader or a fill. No idle animation or timer per component.
 * Android 33+ uses AGSL; older devices sample the same equations once per
 * size/theme (static, deliberately no expensive per-frame CPU rasterization).
 */
@Composable
internal fun Modifier.argentComponentRim(shape: Shape, enabled: Boolean = true, fallback: Color? = null, overlay: Boolean = false): Modifier {
    if (!enabled) return this
    if (SettingsStore.highContrast) return if (fallback != null) this.border(1.dp, fallback, shape) else this
    val dark = LocalGsIsDark.current
    return drawWithCache {
        if (!argentComponentSize(size.width / density, size.height / density, overlay)) {
            val outline = shape.createOutline(size, layoutDirection, this)
            val legacyPath = when (outline) {
                is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                is Outline.Generic -> outline.path
            }
            val width = 1.dp.toPx()
            onDrawWithContent {
                drawContent()
                fallback?.let { drawPath(legacyPath, color = it, style = Stroke(width)) }
            }
        } else {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = when(outline) {
                is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                is Outline.Generic -> outline.path
            }.asAndroidPath()
            val renderer = ArgentRimPainter(size.width, size.height, dark)
            val stroke = 1.5.dp.toPx() // half clipped inside -> 0.75dp
            onDrawWithContent {
                drawContent()
                renderer.draw(drawContext.canvas.nativeCanvas, path, stroke, 3.4f)
            }
        }
    }
}


// Explicit component border replacement, preserving the original accessibility
// stroke instead of stacking bright white and metal rims.
@Composable
internal fun Modifier.argentComponentBorder(shape: Shape, fallback: Color): Modifier =
    argentComponentRim(shape, fallback = fallback)

@Suppress("NewApi")
private class ArgentRimPainter(private val w:Float,private val h:Float,private val dark:Boolean) {
    private var gpu: Shader? = if(Build.VERSION.SDK_INT>=33)
        runCatching { RuntimeShader(ARGENT_RIM_SHADER) }.getOrNull() else null
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        style=Paint.Style.STROKE
        strokeJoin=Paint.Join.ROUND
    }
    private val light=if(dark) floatArrayOf(.86f,.86f,.86f) else floatArrayOf(.60f,.60f,.60f)
    private val shadow=if(dark) floatArrayOf(.58f,.58f,.58f) else floatArrayOf(.35f,.35f,.35f)
    private val dispersion=0f
    private val opacityRange=if(dark) floatArrayOf(.07f,.38f) else floatArrayOf(.045f,.24f)
    private val fallback by lazy {
        val tw=96;val th=64
        val pixels=IntArray(tw*th){i->
            val col=ArgentRimSample.color((i%tw+.5)/tw*w,(i/tw+.5)/th*h,w.toDouble(),h.toDouble(),3.4,light,shadow,dispersion.toDouble())
            android.graphics.Color.argb(ArgentRimSample.alpha((i%tw+.5)/tw,(i/tw+.5)/th,opacityRange),col[0],col[1],col[2])
        }
        val bmp=Bitmap.createBitmap(pixels,tw,th,Bitmap.Config.ARGB_8888)
        BitmapShader(bmp,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply {setScale(w/tw,h/th)})
        }
    }
    fun draw(canvas:android.graphics.Canvas,path:android.graphics.Path,stroke:Float,time:Float) {
        val save=canvas.save()
        canvas.clipPath(path)
        paint.strokeWidth=stroke
        val shader=if(Build.VERSION.SDK_INT>=33 && canvas.isHardwareAccelerated) gpu as? RuntimeShader else null
        if(shader!=null) {
            try {
                shader.setFloatUniform("u_res",w,h)
                shader.setFloatUniform("u_time",time)
                shader.setFloatUniform("u_light",light)
                shader.setFloatUniform("u_dark",shadow)
                shader.setFloatUniform("u_rep",1.2f)
                shader.setFloatUniform("u_alpha",opacityRange)
                shader.setFloatUniform("u_angle",40f*PI.toFloat()/180f)
                shader.setFloatUniform("u_soft",.48f)
                shader.setFloatUniform("u_disp",dispersion)
                shader.setFloatUniform("u_dist",.05f)
                shader.setFloatUniform("u_scale",1.6f)
                paint.shader=shader
                paint.alpha=255
                canvas.drawPath(path,paint)
                canvas.restoreToCount(save)
                return
            } catch(_:RuntimeException) {gpu=null}
        }
        paint.shader=fallback
        paint.alpha=255
        canvas.drawPath(path,paint)
        canvas.restoreToCount(save)
    }
}

/** Scalar port of Argent engine.ts simplex noise and original reflection field. */
internal object ArgentRimSample {
    private fun mod289(x:Double)=x-floor(x/289.0)*289
    private fun permute(x:Double)=mod289((x*34+1)*x)
    private fun fract(x:Double)=x-floor(x)
    private fun smooth(a:Double,b:Double,x:Double):Double{
        val t=((x-a)/(b-a)).coerceIn(0.0,1.0);return t*t*(3-2*t)
    }
    private fun noise(vx:Double,vy:Double):Double{
        val c=.211324865405187;val cy=.366025403784439
        val ix=floor(vx+(vx+vy)*cy);val iy=floor(vy+(vx+vy)*cy)
        val x0=vx-ix+(ix+iy)*c;val y0=vy-iy+(ix+iy)*c
        val dx=if(x0>y0)1.0 else 0.0;val dy=1-dx
        val xs=doubleArrayOf(x0,x0+c-dx,x0-.577350269189626)
        val ys=doubleArrayOf(y0,y0+c-dy,y0-.577350269189626)
        val mx=mod289(ix);val my=mod289(iy)
        val p=doubleArrayOf(permute(permute(my)+mx),permute(permute(my+dy)+mx+dx),permute(permute(my+1)+mx+1))
        var result=0.0
        for(k in 0..2){
            val m=max(0.0,.5-xs[k]*xs[k]-ys[k]*ys[k]).pow(4)
            val x=2*fract(p[k]*.024390243902439)-1
            val h=abs(x)-.5;val a0=x-floor(x+.5)
            result+=m*(1.79284291400159-.85373472095314*(a0*a0+h*h))*(a0*xs[k]+h*ys[k])
        }
        return 130*result
    }
    fun alpha(x:Double,y:Double,range:FloatArray):Int {
        val directional=smooth(.10,.68,abs((x-.5)*.85+(y-.5)*.65))
        return ((range[0]+(range[1]-range[0])*directional)*255).roundToInt().coerceIn(0,255)
    }
    fun color(px:Double,py:Double,w:Double,h:Double,t:Double,light:FloatArray,dark:FloatArray,disp:Double):IntArray {
        val x=(px/w-.5)*min(w/h,2.5)/1.6
        val y=(.5-py/h)/1.6
        val d=hypot(x,y)
        val n=noise(x*.9,y*.9-t*.28)+.35*noise(x*1.8+t*.1,y*1.8-t*.45)
        val angle=40*PI/180
        val coord=(-sin(angle)*x+cos(angle)*y)*(1-.45*d*d)*1.2+n*.05*2.2-t*.22
        val rim=.94+.14*smooth(.25,.85,d)
        return IntArray(3){i->
            val stripe=smooth(.02,.98,abs(fract(coord+(1-i)*disp)*2-1))
            ((dark[i]+(light[i]-dark[i])*stripe)*rim*255).roundToInt().coerceIn(0,255)
        }
    }
}
