package com.grapsee.gsai.ui.components

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.theme.LocalGsIsDark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlin.math.*

/** Component-only guard; full-size panels and modal surfaces retain plain glass. */
internal fun argentComponentSize(width: Float, height: Float): Boolean =
    width >= 16f && height >= 16f && width <= 720f && height <= 320f

/** One shared, slow clock. No WebGL context, animator or coroutine per button. */
private object ArgentClock {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val time = flow {
        val start = SystemClock.uptimeMillis()
        while (true) {
            emit((SystemClock.uptimeMillis() - start) / 1000f * .12f + 3.4f)
            delay(50)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(0), 3.4f)
}

/**
 * Original ArgentUI native shader, rim finish only. Draw a 0.75dp inset stroke
 * over the existing glass rim. The centre never receives a shader or a fill.
 * Android 33+ uses AGSL; older devices sample the same equations once per
 * size/theme (static, deliberately no expensive per-frame CPU rasterization).
 */
@Composable
internal fun Modifier.argentComponentRim(shape: Shape, enabled: Boolean = true): Modifier {
    if (!enabled || SettingsStore.highContrast) return this
    val dark = LocalGsIsDark.current
    val animate = Build.VERSION.SDK_INT >= 33 &&
        !SettingsStore.reduceMotion && !SettingsStore.reduceAnimations &&
        ValueAnimator.areAnimatorsEnabled()
    // Snapshot state is read in draw only, not during composition/cache build.
    val time = if (animate) ArgentClock.time.collectAsStateWithLifecycle() else null
    return drawWithCache {
        if (!argentComponentSize(size.width / density, size.height / density)) {
            onDrawWithContent { drawContent() }
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
                renderer.draw(drawContext.canvas.nativeCanvas, path, stroke, time?.value ?: 3.4f)
            }
        }
    }
}

@Suppress("NewApi")
private class ArgentRimPainter(private val w:Float,private val h:Float,private val dark:Boolean) {
    private var gpu: Shader? = if(Build.VERSION.SDK_INT>=33)
        runCatching { RuntimeShader(ARGENT_RIM_SHADER) }.getOrNull() else null
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        style=Paint.Style.STROKE
        strokeJoin=Paint.Join.ROUND
    }
    private val light=if(dark) floatArrayOf(182/255f,190/255f,201/255f) else floatArrayOf(248/255f,250/255f,252/255f)
    private val shadow=if(dark) floatArrayOf(20/255f,22/255f,26/255f) else floatArrayOf(35/255f,38/255f,43/255f)
    private val dispersion=if(dark).0125f else .015f
    private val fallback by lazy {
        val tw=96;val th=64
        val pixels=IntArray(tw*th){i->
            val col=ArgentRimSample.color((i%tw+.5)/tw*w,(i/tw+.5)/th*h,w.toDouble(),h.toDouble(),3.4,light,shadow,dispersion.toDouble())
            android.graphics.Color.rgb(col[0],col[1],col[2])
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
                shader.setFloatUniform("u_light",*light)
                shader.setFloatUniform("u_dark",*shadow)
                shader.setFloatUniform("u_rep",4.5f)
                shader.setFloatUniform("u_angle",40f*PI.toFloat()/180f)
                shader.setFloatUniform("u_soft",.3f)
                shader.setFloatUniform("u_disp",dispersion)
                shader.setFloatUniform("u_dist",.24f)
                shader.setFloatUniform("u_scale",.85f)
                paint.shader=shader
                paint.alpha=if(dark)82 else 61
                canvas.drawPath(path,paint)
                canvas.restoreToCount(save)
                return
            } catch(_:RuntimeException) {gpu=null}
        }
        paint.shader=fallback
        paint.alpha=if(dark)82 else 61
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
    fun color(px:Double,py:Double,w:Double,h:Double,t:Double,light:FloatArray,dark:FloatArray,disp:Double):IntArray {
        val x=(px/w-.5)*w/h/.85
        val y=(.5-py/h)/.85
        val d=hypot(x,y)
        val n=noise(x*.9,y*.9-t*.28)+.35*noise(x*1.8+t*.1,y*1.8-t*.45)
        val angle=40*PI/180
        val coord=(-sin(angle)*x+cos(angle)*y)*(1-.45*d*d)*4.5+n*.24*2.2-t*.22
        val rim=.94+.14*smooth(.25,.85,d)
        return IntArray(3){i->
            val stripe=smooth(.2,.8,abs(fract(coord+(1-i)*disp)*2-1))
            ((dark[i]+(light[i]-dark[i])*stripe)*rim*255).roundToInt().coerceIn(0,255)
        }
    }
}
