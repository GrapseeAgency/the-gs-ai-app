package com.grapsee.gsai.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Paint
import android.graphics.Path
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.theme.LocalGsIsDark
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * Native circular send ring: the upstream metal-fx chromatic material and
 * circle geometry, with the tilt simulation from the official React Native
 * port's bend.ts (revision 14ae9d2dad18fcee251f4b06333b492a2a41d2ed).
 *
 * The host is Compose/Android Canvas, not React Native. The shader is its
 * AGSL port with the existing CPU material fallback below API 33. This
 * component intentionally does not implement neighbouring-surface
 * reflections, magnet mode or the upstream separate halo renderer.
 */
@Composable
internal fun GsMetalSendRing(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val dark = LocalGsIsDark.current
    val reduced = SettingsStore.reduceAnimations || SettingsStore.reduceMotion ||
        !ValueAnimator.areAnimatorsEnabled()
    val active = enabled && !reduced
    val tilt = remember { MetalGravityInput(context.applicationContext) }
    val bend = remember { MetalCircleBend() }
    val renderer = remember { GsLiquidMetalRenderer(chromatic = true) }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val outline = remember { Path() }
    val band = remember { Path() }
    val clock = remember { mutableLongStateOf(0L) }
    DisposableEffect(tilt, active, lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            if (active && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                tilt.start()
            } else tilt.stop()
        }
        lifecycle.addObserver(observer)
        if (active && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) tilt.start()
        onDispose {
            lifecycle.removeObserver(observer)
            tilt.stop()
        }
    }
    LaunchedEffect(active) {
        clock.longValue = 0L
        bend.reset()
        if (active) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { clock.longValue = it - start }
        }
    }
    Canvas(modifier) {
        val diameter = minOf(size.width, size.height)
        if (diameter <= 0f) return@Canvas
        val dp = diameter / density
        val time = clock.longValue / 1_000_000_000.0
        val field = if (active) bend.step(time, dp, tilt.x, tilt.y) else null
        outline.reset()
        band.reset()
        band.fillType = Path.FillType.EVEN_ODD
        // Upstream circle variant: 2pt band, full circle radius, the same
        // deformPoint gaussian field applied to both inner and outer outlines.
        fun append(path: Path, inset: Float) {
            val radius = (dp / 2f - inset).coerceAtLeast(0f)
            for (i in 0 until 56) {
                val theta = i * (2.0 * PI / 56)
                val x = dp / 2f + radius * cos(theta).toFloat()
                val y = dp / 2f + radius * sin(theta).toFloat()
                val p = field?.deform(x.toDouble(), y.toDouble())
                val px = (p?.first?.toFloat() ?: x) * density
                val py = (p?.second?.toFloat() ?: y) * density
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
        }
        append(outline, 0f)
        append(band, 0f)
        append(band, 2f)
        val canvas = drawContext.canvas.nativeCanvas
        val save = canvas.save()
        canvas.translate((size.width - diameter) / 2, (size.height - diameter) / 2)
        // Upstream theme surfaces and rim opacity.
        paint.style = Paint.Style.FILL
        paint.color = if (dark) 0xFF272727.toInt() else android.graphics.Color.WHITE
        canvas.drawPath(outline, paint)
        renderer.draw(canvas, diameter, diameter, diameter / 2f, time.toFloat(), density, dark, band)
        paint.color = if (dark) 0x1AFFFFFF else 0x0F000000
        canvas.drawPath(band, paint)
        canvas.restoreToCount(save)
    }
}

/** Native sensor source, with the upstream four-second adapting baseline. */
private class MetalGravityInput(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var started = false
    private var last = 0L
    private var bx = 0.0
    private var by = 0.0
    var x = 0.0
        private set
    var y = 0.0
        private set

    fun start() {
        if (started || sensor == null) return
        started = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (started) manager.unregisterListener(this)
        started = false
        last = 0L
        x = 0.0
        y = 0.0
    }

    override fun onSensorChanged(event: SensorEvent) {
        val sx = event.values[0] / SensorManager.GRAVITY_EARTH.toDouble()
        val sy = -event.values[1] / SensorManager.GRAVITY_EARTH.toDouble()
        if (last == 0L) {
            bx = sx
            by = sy
        } else {
            val dt = ((event.timestamp - last) / 1e9).coerceIn(0.001, 0.1)
            val k = 1 - exp(-dt / 4)
            bx += (sx - bx) * k
            by += (sy - by) * k
        }
        last = event.timestamp
        x = sx - bx
        y = sy - by
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/** Source: metal-fx-native/src/bend.ts, BEND_DEFAULTS and deformPoint. */
internal data class MetalCircleField(
    val cx: Double,
    val cy: Double,
    val dirX: Double,
    val dirY: Double,
    val radK: Double,
) {
    fun deform(x: Double, y: Double): Pair<Double, Double> {
        val dx = x - cx
        val dy = y - cy
        val q = dx * dx + dy * dy
        val gb = exp(-q / (2 * 13.0 * 13))
        val gl = exp(-q / (2 * 10.0 * 10))
        return Pair(x + dirX * gb + dx * radK * gl, y + dirY * gb + dy * radK * gl)
    }
}

/** Direct translation of upstream bend.ts for a circle in dp. */
internal class MetalCircleBend {
    private var cx = 0.0
    private var cy = 0.0
    private var ax = 0.0
    private var ay = 0.0
    private var vax = 0.0
    private var vay = 0.0
    private var la = 0.0
    private var vla = 0.0
    private var stx = 0.0
    private var sty = 0.0
    private var stl = 0.0
    private var env = 0.0
    private var active = false
    private var last = -1.0

    fun reset() {
        cx = 0.0; cy = 0.0; ax = 0.0; ay = 0.0; vax = 0.0; vay = 0.0
        la = 0.0; vla = 0.0; stx = 0.0; sty = 0.0; stl = 0.0
        env = 0.0; active = false; last = -1.0
    }

    fun step(now: Double, diameter: Float, tiltX: Double, tiltY: Double): MetalCircleField? {
        val dt = if (last >= 0) (now - last).coerceIn(0.001, 0.032) else 1.0 / 60
        last = now
        val mag = hypot(tiltX, tiltY)
        val t = ((mag - 0.025) / (0.45 - 0.025)).coerceIn(0.0, 1.0)
        val m = t * t * (3 - 2 * t)
        var tx = 0.0
        var ty = 0.0
        var tl = 0.0
        if (m > 0.0005 && diameter > 0) {
            val ux = tiltX / mag
            val uy = tiltY / mag
            val radius = diameter / 2.0
            val contactX = radius + radius * ux
            val contactY = radius + radius * uy
            if (!active) { cx = contactX; cy = contactY; active = true }
            val fa = 1 - (1 - 0.32).pow(dt * 60)
            cx += (contactX - cx) * fa
            cy += (contactY - cy) * fa
            tx = ux * m * 9
            ty = uy * m * 9
            tl = 7.5 * m
        } else active = false
        val sa = 1 - exp(-dt * 1000 / 140)
        stx += (tx - stx) * sa
        sty += (ty - sty) * sa
        vax += (-260 * (ax - stx) - 13 * vax) * dt
        vay += (-260 * (ay - sty) - 13 * vay) * dt
        ax += vax * dt
        ay += vay * dt
        stl += (tl - stl) * sa
        vla += (-53 * (la - stl) - 9 * vla) * dt
        la += vla * dt
        val target = if (active) 1.0 else 0.0
        val tauMs = (if (active) 200.0 else 350.0) / 3
        env += (target - env) * (1 - exp(-dt * 1000 / tauMs))
        if (env < 0.002 && !active) env = 0.0
        if (!active && env == 0.0 && hypot(ax, ay) < 0.05 &&
            hypot(vax, vay) < 1 && kotlin.math.abs(la) < 0.05) {
            ax = 0.0; ay = 0.0; vax = 0.0; vay = 0.0
            la = 0.0; vla = 0.0; stx = 0.0; sty = 0.0; stl = 0.0
            return null
        }
        val k = 0.74 * env
        return MetalCircleField(cx, cy, ax * k, ay * k, la / 10 * k)
    }
}
