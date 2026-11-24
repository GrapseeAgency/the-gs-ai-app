package com.grapsee.gsai.ui.components

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.theme.LocalGsIsDark
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Original Libraries.dev rotate-layer SkSL (md/colorful), drawn as a native
 * Android overlay. No WebView, sweep-gradient imitation, or captured frames.
 * The overlay has no pointer handlers; the underlying Generate button owns taps.
 */
@Composable
internal fun GsGenerateBorderBeam(modifier: Modifier = Modifier) {
    val dark = LocalGsIsDark.current
    val active = !SettingsStore.reduceAnimations && !SettingsStore.reduceMotion &&
        ValueAnimator.areAnimatorsEnabled()
    val clock = remember { mutableLongStateOf(0L) }
    val renderer = remember { GenerateBeamRenderer() }
    LaunchedEffect(active) {
        clock.longValue = 0L
        if (active) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { clock.longValue = it - start }
        }
    }
    Canvas(modifier) {
        if (active) renderer.draw(
            drawContext.canvas.nativeCanvas, size.width, size.height, density,
            dark, clock.longValue / 1e9f
        )
    }
}

private class GenerateBeamRenderer {
    private var gpu = if (Build.VERSION.SDK_INT >= 33) {
        runCatching { GenerateBeamGpu() }.getOrNull()
    } else null
    private val cpu by lazy { GenerateBeamCpu() }

    fun draw(canvas: NativeCanvas, w: Float, h: Float, density: Float, dark: Boolean, time: Float) {
        if (w <= 1f || h <= 1f) return
        if (Build.VERSION.SDK_INT >= 33 && canvas.isHardwareAccelerated) {
            gpu?.let {
                try {
                    it.draw(canvas, w, h, density, dark, time)
                    return
                } catch (_: RuntimeException) {
                    gpu = null
                }
            }
        }
        cpu.draw(canvas, w, h, density, dark, time)
    }
}

/** CSS filter math from the upstream colorMatrix.ts, including ping-pong hue. */
private fun beamMatrix(hue: Float, saturation: Float): FloatArray {
    val radians = hue * PI.toFloat() / 180f
    val c = cos(radians)
    val s = sin(radians)
    val m = floatArrayOf(
        .213f+c*.787f-s*.213f, .715f-c*.715f-s*.715f, .072f-c*.072f+s*.928f,
        .213f-c*.213f+s*.143f, .715f+c*.285f+s*.140f, .072f-c*.072f-s*.283f,
        .213f-c*.213f-s*.787f, .715f-c*.715f+s*.715f, .072f+c*.928f+s*.072f
    )
    val sat = saturation
    val sm = floatArrayOf(
        .213f+.787f*sat, .715f-.715f*sat, .072f-.072f*sat,
        .213f-.213f*sat, .715f+.285f*sat, .072f-.072f*sat,
        .213f-.213f*sat, .715f-.715f*sat, .072f+.928f*sat
    )
    return FloatArray(9) { i ->
        val r = i / 3
        val col = i % 3
        1.3f * (sm[r*3]*m[col] + sm[r*3+1]*m[3+col] + sm[r*3+2]*m[6+col])
    }
}
private fun beamHue(time: Float): Float {
    val p = (time / 12f) % 2f
    return -30f + 60f * (if (p <= 1f) p else 2f - p)
}

@Suppress("NewApi")
private class GenerateBeamGpu {
    private val source = GenerateBeamSource
    private val shaders = Array(3) { RuntimeShader(source.shader) }
    private val paints = Array(3) { i -> Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = shaders[i] } }
    private val bloom = RenderNode("original-generate-beam-bloom")
    private var blurDensity = -1f

    fun draw(canvas: NativeCanvas, width: Float, height: Float, density: Float, dark: Boolean, time: Float) {
        val w = width / density
        val h = height / density
        val saturation = if (dark) 1.2f else 1.5f
        val matrix = beamMatrix(beamHue(time), saturation)
        for (kind in 0..2) {
            val shader = shaders[kind]
            val inner = kind == 1
            val isBloom = kind == 2
            shader.setFloatUniform("uSize", w, h)
            shader.setFloatUniform("uAngle", (time / source.duration) % 1f)
            shader.setFloatUniform("uRadius", minOf(w, h) / 2f)
            shader.setFloatUniform("uBorderWidth", 1f)
            shader.setFloatUniform("uKind", kind.toFloat())
            shader.setFloatUniform("uEdgeMaskPx", if (inner) 28f else 0f)
            shader.setFloatUniform("uBlobs", (if (isBloom) source.emptyBlobs else if (inner) source.inner else source.stroke))
            shader.setFloatUniform("uBlobCount", if (isBloom) 0f else source.blobCount)
            val bg = if (inner) source.emptyBg else if (isBloom) {
                if (dark) source.darkBloom else source.lightBloom
            } else if (dark) source.darkStroke else source.lightStroke
            shader.setFloatUniform("uBg", bg)
            shader.setFloatUniform("uBgCount", if (inner) 0f else if (isBloom) source.bloomCount else source.strokeCount)
            shader.setFloatUniform("uBgIsBlack", if (dark) 0f else 1f)
            shader.setFloatUniform("uMask", (if (isBloom) source.emptyMask else source.mask))
            shader.setFloatUniform("uMaskCount", if (isBloom) 0f else source.maskCount)
            shader.setFloatUniform("uCM", (if (isBloom) beamMatrix(0f, saturation) else matrix))
            val opacity = if (dark) floatArrayOf(.26f,.42f,.24f) else floatArrayOf(.12f,.26f,.34f)
            shader.setFloatUniform("uOpacity", opacity[kind] * source.strength)
            if (inner && dark) shader.setFloatUniform("uShadowColor", 1f,1f,1f,.27f)
            else shader.setFloatUniform("uShadowColor", 0f,0f,0f,if (inner) .14f else 0f)
            shader.setFloatUniform("uShadowBlur", if (inner) 9f else 0f)
        }
        val save = canvas.save()
        canvas.scale(density, density)
        canvas.drawRect(0f, 0f, w, h, paints[1])
        canvas.drawRect(0f, 0f, w, h, paints[0])
        canvas.restoreToCount(save)
        // RN's third layer is the original bloom ring blurred at sigma 8dp.
        bloom.setPosition(0, 0, width.roundToInt(), height.roundToInt())
        if (blurDensity != density) {
            bloom.setRenderEffect(RenderEffect.createBlurEffect(8f*density, 8f*density, Shader.TileMode.DECAL))
            blurDensity = density
        }
        val recording = bloom.beginRecording()
        recording.scale(density, density)
        recording.drawRect(0f, 0f, w, h, paints[2])
        bloom.endRecording()
        canvas.drawRenderNode(bloom)
    }
}

/**
 * Older devices and software canvases: evaluate the same original layer
 * equations at one sample per dp and composite a small native bitmap.
 * The bloom alpha uses a separable Gaussian approximation (3 box passes).
 * Static geometry/blob colors are cached, and fallback runs at 20Hz.
 */
private class GenerateBeamCpu {
    private val src = GenerateBeamSource
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var bitmap: Bitmap? = null
    private var w = 0
    private var h = 0
    private var pixels = IntArray(0)
    private var geom = FloatArray(0)
    private var depth = FloatArray(0)
    private var turn = FloatArray(0)
    private var ring = FloatArray(0)
    private var edge = FloatArray(0)
    private var inner = FloatArray(0)
    private var stroke = FloatArray(0)
    private var bloom = FloatArray(0)
    private var scratch = FloatArray(0)
    private var lastFrame = -1
    private var lastDark = false

    private fun smooth(a: Float, b: Float, v: Float): Float {
        val t = ((v-a)/(b-a)).coerceIn(0f,1f)
        return t*t*(3f-2f*t)
    }
    private fun sdf(x: Float, y: Float, r: Float, inset: Float): Float {
        val qx = abs(x-w*.5f) - (w*.5f-inset) + r
        val qy = abs(y-h*.5f) - (h*.5f-inset) + r
        return hypot(maxOf(qx,0f),maxOf(qy,0f)) + minOf(maxOf(qx,qy),0f)-r
    }
    private fun blobPixel(data: FloatArray, x: Float, y: Float, out: FloatArray, i: Int) {
        var r=0f; var g=0f; var b=0f; var a=0f
        for (k in src.blobCount.toInt()-1 downTo 0) {
            val j=k*8
            val d=hypot((x-data[j+2]*w)/data[j],(y-data[j+3]*h)/data[j+1])
            val alpha=data[j+7]*(1f-d).coerceIn(0f,1f)
            r=data[j+4]*alpha+r*(1-alpha)
            g=data[j+5]*alpha+g*(1-alpha)
            b=data[j+6]*alpha+b*(1-alpha)
            a=alpha+a*(1-alpha)
        }
        out[i*4]=r; out[i*4+1]=g; out[i*4+2]=b; out[i*4+3]=a
    }
    private fun setup(width: Int, height: Int) {
        if (width==w && height==h) return
        w=width; h=height
        bitmap?.recycle()
        bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        val n=w*h
        pixels=IntArray(n); geom=FloatArray(n); depth=FloatArray(n); turn=FloatArray(n)
        ring=FloatArray(n); edge=FloatArray(n); inner=FloatArray(n*4); stroke=FloatArray(n*4)
        bloom=FloatArray(n); scratch=FloatArray(n)
        val radius=minOf(w,h)*.5f
        for (i in 0 until n) {
            val x=i%w+.5f; val y=i/w+.5f
            val sdf=sdf(x,y,radius,0f)
            geom[i]=1f-smooth(-1f,0f,sdf)
            depth[i]=-sdf
            ring[i]=geom[i]-(1f-smooth(-1f,0f,sdf(x,y,maxOf(radius-1f,0f),1f)))
            val theta=atan2(x-w*.5f,-(y-h*.5f))/(2f*PI.toFloat())+1f
            turn[i]=theta-floor(theta)
            edge[i]=(maxOf(0f,1f-minOf(y,h-y)/28f)+maxOf(0f,1f-minOf(x,w-x)/28f)).coerceIn(0f,1f)
            if (geom[i]>0f) {
                blobPixel(src.inner,x,y,inner,i)
                if (ring[i]>0f) blobPixel(src.stroke,x,y,stroke,i)
            }
        }
        lastFrame=-1
    }
    private fun sample(stops: FloatArray,count: Int,t: Float): Float {
        var result=stops[1]
        for (j in 2 until count step 2) {
            val p0=stops[j-2]; val p1=stops[j]
            if (t>p0) {
                val k=if(p1>p0)((t-p0)/(p1-p0)).coerceIn(0f,1f) else 1f
                result=stops[j-1]+(stops[j+1]-stops[j-1])*k
            }
        }
        return result
    }
    private fun layer(data: FloatArray,i: Int,top: Float,topColor: Float,opacity: Float,m: FloatArray): Int {
        val j=i*4
        val a=top+data[j+3]*(1f-top)
        if (a<=.0001f || opacity<=0f) return 0
        val r=(topColor*top+data[j]*(1f-top))/a
        val g=(topColor*top+data[j+1]*(1f-top))/a
        val b=(topColor*top+data[j+2]*(1f-top))/a
        fun ch(k:Int)=((m[k]*r+m[k+1]*g+m[k+2]*b).coerceIn(0f,1f)*255f).roundToInt()
        return android.graphics.Color.argb((a*opacity*255f).roundToInt().coerceIn(0,255),ch(0),ch(3),ch(6))
    }
    private fun over(top: Int,bottom: Int): Int {
        val a=android.graphics.Color.alpha(top)/255f
        val ba=android.graphics.Color.alpha(bottom)/255f
        val oa=a+ba*(1f-a)
        if (oa<=0f) return 0
        fun c(shift:Int)=((((top ushr shift) and 255)*a+((bottom ushr shift) and 255)*ba*(1f-a))/oa).roundToInt().coerceIn(0,255)
        return android.graphics.Color.argb((oa*255).roundToInt(),c(16),c(8),c(0))
    }
    private fun blur() {
        // Three width-15 box kernels approximate sigma 8dp; zero outside.
        repeat(3) {
            for(y in 0 until h) {
                var sum=0f
                for(x in 0..minOf(7,w-1)) sum+=bloom[y*w+x]
                for(x in 0 until w) {
                    scratch[y*w+x]=sum/15f
                    if(x-7>=0) sum-=bloom[y*w+x-7]
                    if(x+8<w) sum+=bloom[y*w+x+8]
                }
            }
            for(x in 0 until w) {
                var sum=0f
                for(y in 0..minOf(7,h-1)) sum+=scratch[y*w+x]
                for(y in 0 until h) {
                    bloom[y*w+x]=sum/15f
                    if(y-7>=0) sum-=scratch[(y-7)*w+x]
                    if(y+8<h) sum+=scratch[(y+8)*w+x]
                }
            }
        }
    }
    private fun update(dark:Boolean,time:Float) {
        val m=beamMatrix(beamHue(time),if(dark)1.2f else 1.5f)
        val angle=(time/src.duration)%1f
        val bg=if(dark)src.darkStroke else src.lightStroke
        val bs=if(dark)src.darkBloom else src.lightBloom
        val topColor=if(dark)1f else 0f
        for(i in pixels.indices) {
            val t=turn[i]-angle+1f
            val fraction=t-floor(t)
            val mask=sample(src.mask,src.maskCount.toInt(),fraction)
            val shadow=(1f-depth[i]/10f).coerceIn(0f,1f)
            val a=layer(inner,i,(if(dark).27f else .14f)*shadow*shadow,topColor,
                geom[i]*mask*edge[i]*(if(dark).42f else .26f)*src.strength,m)
            val b=layer(stroke,i,sample(bg,src.strokeCount.toInt(),fraction),topColor,
                ring[i]*mask*(if(dark).26f else .12f)*src.strength,m)
            pixels[i]=over(b,a)
            bloom[i]=ring[i]*sample(bs,src.bloomCount.toInt(),fraction)*(if(dark).24f else .34f)*src.strength
        }
        blur()
        val color=if(dark)255 else 0
        for(i in pixels.indices) pixels[i]=over(
            android.graphics.Color.argb((bloom[i]*255f).roundToInt().coerceIn(0,255),color,color,color),pixels[i])
        bitmap?.setPixels(pixels,0,w,0,0,w,h)
    }
    fun draw(canvas:NativeCanvas,width:Float,height:Float,density:Float,dark:Boolean,time:Float) {
        setup((width/density).roundToInt().coerceAtLeast(2),(height/density).roundToInt().coerceAtLeast(2))
        val frame=(time*20).toInt()
        if(frame!=lastFrame || dark!=lastDark) {
            update(dark,frame/20f); lastFrame=frame; lastDark=dark
        }
        bitmap?.let { canvas.drawBitmap(it,null,android.graphics.RectF(0f,0f,width,height),paint) }
    }
}
