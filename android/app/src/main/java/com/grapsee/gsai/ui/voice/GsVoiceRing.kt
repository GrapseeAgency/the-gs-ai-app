package com.grapsee.gsai.ui.voice

import android.animation.ValueAnimator
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.theme.LocalGsIsDark
import kotlin.math.*

/**
 * Native port of Libraries.dev VoiceRing(.mono), MIT, Jakub Antalik 2026.
 * Upstream 14ae9d2dad18fcee251f4b06333b492a2a41d2ed:
 * packages/voice-ring/src/{voice.ts,engine/auraMono.ts,engine/tube.ts,engine/lean.ts}
 * and ports/ios/VoiceRingKit/Sources/VoiceRingKit/AuraMono.swift.
 *
 * Uses the upstream level-driven follower, two-loop saddle/oval geometry,
 * neutral tube painter with depth-sorted overlapping runs, and lean field.
 * No synthetic microphone level, no captured animation, no WebView.
 * No emotion classifier or edge-contact lens; mono preset has no lens.
 */
internal enum class RingPhase { Idle, Listening, Speaking, Processing }

@Composable
internal fun GsVoiceRing(
    phase: RingPhase,
    level: () -> Float,
    direction: Float? = null,
    modifier: Modifier = Modifier,
    diameter: Int = 224,
    label: String = "Voice ring"
) {
    val dark = LocalGsIsDark.current
    val moving = !SettingsStore.reduceMotion && !SettingsStore.reduceAnimations &&
        ValueAnimator.areAnimatorsEnabled()
    val follower = remember { VoiceRingFollower() }
    val painter = remember { MonoRingPainter() }
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(moving) {
        if (moving) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { clock.longValue = it - start }
        }
    }
    Canvas(modifier.size(diameter.dp).semantics { contentDescription = label }) {
        val width = minOf(size.width, size.height)
        if (width <= 0f) return@Canvas
        val raw = if (phase == RingPhase.Listening || phase == RingPhase.Speaking) level() else 0f
        val input = follower.step(
            clock.longValue / 1e9,
            raw.toDouble().coerceIn(0.0, 1.0),
            phase == RingPhase.Processing,
            if (phase == RingPhase.Listening) direction?.toDouble() ?: 0.0 else 0.0,
            !moving
        )
        val canvas = drawContext.canvas.nativeCanvas
        val save = canvas.save()
        canvas.translate((size.width-width)/2, (size.height-width)/2)
        canvas.scale(density,density)
        painter.draw(canvas, width / density, input, dark)
        canvas.restoreToCount(save)
    }
}

internal data class RingInput(
    val t: Double, val drive: Double, val soft: Double, val mids: Double,
    val processing: Double, val spin: Double, val pan: Double
)

/** Upstream voice.ts plain-level path and mono preset clock. */
internal class VoiceRingFollower {
    private var last = -1.0
    private var clock = 0.0
    private var t = 0.0
    private var drive = 0.0
    private var level = 0.0
    private var mid = 0.0
    private var heard = 0.0
    private var soft = 0.0
    private var softMid = 0.0
    private var proc = 0.0
    private var spin = 0.0
    private var pan = 0.0
    private var panVel = 0.0
    private fun follow(prev:Double,target:Double,dt:Double,attack:Double,release:Double):Double =
        prev+(target-prev)*(1-exp(-dt/max(.001,if(target>prev)attack else release)))
    private fun shape(raw:Double,threshold:Double):Double =
        if(raw<=threshold)0.0 else ((1-exp(-3*(raw-threshold)/max(.001,1-threshold)))/(1-exp(-3.0))).coerceIn(0.0,1.0)
    fun step(now:Double,raw:Double,processing:Boolean,direction:Double,still:Boolean=false):RingInput {
        val dt=if(last<0 || now<last)1.0/60 else (now-last).coerceIn(.001,.05)
        last=now
        val r=raw*.85
        val rm=r*(.72+.28*sin(clock*2.3))
        proc=follow(proc,if(processing)1.0 else 0.0,dt,.35,.5)
        val pace=.4+.6*min(1.0,max(heard*2.5,proc))
        if(!still){clock+=dt;t+=dt*1.2*pace*(1+.5*proc)}
        level=follow(level,shape(r,.04),dt,.06,.32)
        heard=follow(heard,level,dt,.35,.9)
        mid=follow(mid,shape(rm,.024),dt,.06,.32*1.15)
        val lift=.1*(if(still).5 else .5+.5*sin(2*PI*clock/6))*(1-proc)
        soft=follow(soft,level+(1-level)*lift,dt,.35,.9)
        softMid=follow(softMid,mid+(1-mid)*lift*.6,dt,.35,.9)
        if(!still){
            drive+=dt*1.2*(pace+4*level+1.3*proc)
            val home=ceil(spin/(2*PI)-1e-6)*2*PI
            spin+=dt*(2.1*1.2*proc+min(.3,(home-spin)*.6)*(1-proc))
        }
        val goal=if(heard>.06&&!processing)direction.coerceIn(-1.0,1.0) else 0.0
        val w=if(abs(goal)>abs(pan))2.5 else 1.6
        if(!still){panVel+=(w*w*(goal-pan)-2*w*panVel)*dt;pan+=panVel*dt}
        return RingInput(t,drive,soft,softMid,proc,spin,pan)
    }
}

private data class MonoLoop(val x:DoubleArray,val y:DoubleArray,val shade:DoubleArray,val z:DoubleArray)
private data class MonoRun(val loop:MonoLoop,val from:Int,val to:Int,val depth:Double)
private fun smoothstep(a:Double,b:Double,v:Double):Double {
    val t=((v-a)/(b-a)).coerceIn(0.0,1.0)
    return t*t*(3-2*t)
}

/** Upstream frameAuraMono, using the original neutral default parameters. */
private class MonoRingPainter {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{
        style=Paint.Style.STROKE;strokeCap=Paint.Cap.BUTT;strokeJoin=Paint.Join.ROUND
    }
    private val path=Path()
    private val erase=PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    private fun build(size:Double,v:RingInput):List<MonoLoop> {
        val half=size/2
        val voice=v.soft
        val radius=half*.44*(1+.08*voice)*(1-.06*v.processing)
        val segs=max(48,(180*sqrt((size/200).coerceIn(.2,1.4))).roundToInt())
        val lean=.22+.75*voice
        val bend=.12+.88*(voice*1.85).coerceIn(0.0,1.0)
        val twist=1.69*bend
        val wobble=.31*bend
        val u=v.drive*2.1
        val calm=1-smoothstep(.1,.5,voice)
        val sway=.18*calm
        val swell=.05*calm
        val drift=.03*calm
        val t=v.t
        val cx=half+radius*drift*sin(.9*t+.6)
        val cy=half+radius*drift*sin(.7*t+2.1)
        return List(2){k->
            val ph=k*2.399
            val tip=lean*(1+.25*sin(.17*u+ph))+sway*sin(1.3*t+1.3*ph)
            val dir=k*PI+.23*u*(if(k%2==1)-1 else 1)+.4*sin(.11*u+ph)+v.spin
            val nx=sin(tip)*cos(dir);val ny=sin(tip)*sin(dir);val nz=cos(tip)
            val ul=hypot(nx,nz).coerceAtLeast(1e-9)
            val ux=nz/ul;val uz=-nx/ul
            val vx=ny*uz;val vy=nz*ux-nx*uz;val vz=-ny*ux
            val b2=.2*twist*sin(.29*u+1.3+ph);val p2=.37*u+ph
            val b3=.07*twist*sin(.23*u+.4+ph);val p3=-.31*u+2.1+ph
            val a2=.07*wobble*sin(.33*u+.4+ph);val q2=.41*u+ph
            val a3=.04*wobble*sin(.27*u+1+ph);val q3=-.35*u+.8+ph
            val a4=.04*1.85*v.mids;val q4=.5*u+ph
            val s2=swell*sin(1.1*t+.7+ph);val r2=.7*t+ph
            val s3=.6*swell*sin(1.5*t+1.9+ph);val r3=-.5*t+ph
            val rk=1-.035*k
            val xs=DoubleArray(segs);val ys=DoubleArray(segs);val zs=DoubleArray(segs)
            for(i in 0 until segs){
                val th=i.toDouble()/segs*2*PI
                val c=cos(th);val s=sin(th)
                val r=rk*(1+a2*cos(2*th+q2)+a3*cos(3*th+q3)+a4*cos(4*th+q4)+s2*cos(2*th+r2)+s3*cos(3*th+r3))
                val h=rk*(b2*sin(2*th+p2)+b3*sin(3*th+p3))
                xs[i]=(ux*c+vx*s)*r+nx*h;ys[i]=vy*s*r+ny*h;zs[i]=(uz*c+vz*s)*r+nz*h
            }
            val x=DoubleArray(segs);val y=DoubleArray(segs);val shade=DoubleArray(segs);val z=DoubleArray(segs)
            val lightNorm=sqrt(.45*.45+.55*.55+.7*.7)
            for(i in 0 until segs){
                val f=50/(50-zs[i])
                x[i]=cx+xs[i]*radius*f;y[i]=cy-ys[i]*radius*f
                val j=(i+1)%segs;val m=(i+segs-1)%segs
                val tx=xs[j]-xs[m];val ty=ys[j]-ys[m];val tz=zs[j]-zs[m]
                val tl=sqrt(tx*tx+ty*ty+tz*tz).coerceAtLeast(1e-9)
                val along=(-.45*tx+.55*ty+.7*tz)/(tl*lightNorm)
                val lit=sqrt(max(0.0,1-along*along))
                val near=((zs[i]+1)/2).coerceIn(0.0,1.0)
                shade[i]=(.12+.6*lit*(.55+.45*near)+.28*near+.08*voice).coerceIn(0.0,1.0)
                z[i]=near*2-1
            }
            MonoLoop(x,y,shade,z)
        }
    }
    fun draw(canvas:NativeCanvas,width:Float,input:RingInput,dark:Boolean){
        val size=width.toDouble()
        val loops=build(size,input)
        val pan=input.pan
        // Original mono lean preset, with native surface bounds limiting reach.
        if(abs(pan)>=.002){
            val c=size/2;val r=size*.36;val side=if(pan<0)-1.0 else 1.0
            fun shift(x:Double,y:Double):Double{
                val nx=(x-c)/r;val ny=(y-c)/r
                val near=((nx*side+1)/2).coerceIn(0.0,1.0)
                val flank=max(0.0,nx*side)
                return pan*r*(.55+.36*near*near+.28*flank*flank*exp(-ny*ny*1.5))
            }
            var scale=1.0
            val wall=if(side>0)size-2 else 2.0
            for(loop in loops)for(i in loop.x.indices){
                val dx=shift(loop.x[i],loop.y[i])*side
                if(dx>1e-9)scale=min(scale,max(0.0,((wall-loop.x[i])*side)/dx))
            }
            for(loop in loops)for(i in loop.x.indices){
                val near=(((loop.x[i]-c)/r*side+1)/2).coerceIn(0.0,1.0)
                loop.x[i]+=shift(loop.x[i],loop.y[i])*scale
                loop.y[i]=c+(loop.y[i]-c)*(1-.15*.28*abs(pan)*scale*near)
            }
        }
        val runs=ArrayList<MonoRun>()
        for(loop in loops){
            val n=loop.x.size
            for(i0 in 0 until n step 6){
                val i1=min(n,i0+6)
                var z=0.0
                for(i in i0..i1)z+=loop.z[i%n]
                runs.add(MonoRun(loop,i0-1,i1,z/(i1-i0+1)))
            }
        }
        runs.sortBy{it.depth}
        val w=max(.5,1.7*(size/200).pow(.6)).toFloat()
        // Upstream neutral tube: contrast=0 and depthWidth=0, constant grey.
        val light=.5
        val gray=(255*(if(dark).16+.84*light else .1+.6*(1-light))).roundToInt()
        val color=android.graphics.Color.rgb(gray,gray,gray)
        val layer=canvas.saveLayer(0f,0f,width,width,null)
        for(run in runs){
            path.reset()
            val n=run.loop.x.size
            for(i in run.from..run.to){
                val k=(i%n+n)%n
                val x=run.loop.x[k].toFloat();val y=run.loop.y[k].toFloat()
                if(i==run.from)path.moveTo(x,y)else path.lineTo(x,y)
            }
            // Port tube.ts destination-out gap then source-over body and highlight.
            paint.color=android.graphics.Color.BLACK;paint.strokeWidth=w*1.35f
            paint.xfermode=erase;canvas.drawPath(path,paint)
            paint.xfermode=null;paint.color=color;paint.strokeWidth=w
            canvas.drawPath(path,paint)
            val save=canvas.save()
            canvas.translate(-.17f*w,-.19f*w)
            paint.color=android.graphics.Color.argb(((if(dark).6 else .45)*light*light*255).roundToInt(),255,255,255)
            paint.strokeWidth=w*.32f;canvas.drawPath(path,paint)
            canvas.restoreToCount(save)
        }
        canvas.restoreToCount(layer)
    }
}
