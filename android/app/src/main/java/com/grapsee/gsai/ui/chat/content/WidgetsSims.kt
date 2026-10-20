package com.grapsee.gsai.ui.chat.content

// ---------------------------------------------------------------------------
// Sim kind — eight real-time worlds on one Compose Canvas. Logical space is
// 320x220 for every world (scaled at draw), physics is plain Float math in a
// plain class (no allocation in the frame loop), and the frame loop is a
// LaunchedEffect + withFrameNanos pair that bumps one Int state — the draw
// phase reads the world directly. Same contract as the web renderer: knobs
// clamp at parse, tap/drag interacts, Pause/Reset always work.
// ---------------------------------------------------------------------------

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.theme.GsTheme
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val SIM_W = 320f
private const val SIM_H = 220f

/** One world: mutable physics state + draw. Never allocates inside step(). */
private abstract class SimWorld(protected val spec: SimWidget) {
    protected val rnd = Random(spec.seed.toLong())
    var frames = 0
        private set

    open fun step(dt: Float) {
        frames++
    }

    open fun tap(p: Offset, size: Size) {}
    open fun drag(p: Offset, size: Size) {}
    abstract fun draw(scope: DrawScope)

    protected fun accent(): Color = WidgetPalette.legible(WidgetPalette.at(0), WidgetPalette.onDarkCanvas())
    protected fun jade(): Color = WidgetPalette.legible(WidgetPalette.at(1), WidgetPalette.onDarkCanvas())
    protected fun amber(): Color = WidgetPalette.legible(WidgetPalette.at(2), WidgetPalette.onDarkCanvas())
    protected fun rose(): Color = WidgetPalette.legible(WidgetPalette.at(9), WidgetPalette.onDarkCanvas())
    protected fun ink(): Color = GsTheme.colors.textTertiary
}

private fun scaleOf(size: Size): Pair<Float, Float> = size.width / SIM_W to size.height / SIM_H

// --- particles ---------------------------------------------------------------

private class ParticleWorld(spec: SimWidget) : SimWorld(spec) {
    private val n = spec.count.coerceIn(10, 120)
    private val x = FloatArray(n)
    private val y = FloatArray(n)
    private val vx = FloatArray(n)
    private val vy = FloatArray(n)
    private val hue = IntArray(n) { it % 12 }
    private val g = spec.gravity

    init {
        for (i in 0 until n) respawn(i, true)
    }

    private fun respawn(i: Int, spread: Boolean) {
        x[i] = SIM_W * (0.2f + rnd.nextFloat() * 0.6f)
        y[i] = if (spread) SIM_H * rnd.nextFloat() else SIM_H * 0.8f
        vx[i] = (rnd.nextFloat() - 0.5f) * 60f
        vy[i] = -(20f + rnd.nextFloat() * 90f)
    }

    override fun step(dt: Float) {
        super.step(dt)
        for (i in 0 until n) {
            vy[i] += g * dt
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            if (y[i] > SIM_H - 6f) {
                y[i] = SIM_H - 6f
                vy[i] = -abs(vy[i]) * spec.damping
                vx[i] *= 0.96f
            }
            if (x[i] < 4f || x[i] > SIM_W - 4f) {
                vx[i] = -vx[i]
                x[i] = x[i].coerceIn(4f, SIM_W - 4f)
            }
            if (frames % 240 == 0 && vy[i] > -1f && y[i] > SIM_H - 8f) respawn(i, false)
        }
    }

    override fun tap(p: Offset, size: Size) {
        val (sx, sy) = scaleOf(size)
        val tx = p.x / sx
        val ty = p.y / sy
        for (i in 0 until n) {
            val dx = x[i] - tx
            val dy = y[i] - ty
            val d = max(1f, sqrt(dx * dx + dy * dy))
            if (d < 80f) {
                vx[i] += dx / d * 120f
                vy[i] += dy / d * 120f
            }
        }
    }

    override fun draw(scope: DrawScope) {
        val (sx, sy) = scaleOf(scope.size)
        for (i in 0 until n) {
            scope.drawCircle(WidgetPalette.legible(WidgetPalette.at(hue[i]), WidgetPalette.onDarkCanvas()), radius = 3.4f * sx, center = Offset(x[i] * sx, y[i] * sy))
        }
    }
}

// --- cloth -------------------------------------------------------------------

private class ClothWorld(spec: SimWidget) : SimWorld(spec) {
    private val cols = 18
    private val rows = 12
    private val spacingX = (SIM_W * 0.7f) / (cols - 1)
    private val spacingY = spacingX * 0.8f
    private val x = FloatArray(cols * rows)
    private val y = FloatArray(cols * rows)
    private val px = FloatArray(cols * rows)
    private val py = FloatArray(cols * rows)
    private val alive = Array(cols * rows) { true }
    private var dragIdx = -1

    init {
        for (r in 0 until rows) for (c in 0 until cols) {
            val i = r * cols + c
            x[i] = SIM_W * 0.15f + c * spacingX
            y[i] = 22f + r * spacingY
            px[i] = x[i]
            py[i] = y[i]
        }
    }

    private fun constrain(i: Int, j: Int, rest: Float) {
        if (!alive[i] || !alive[j]) return
        val dx = x[j] - x[i]
        val dy = y[j] - y[i]
        val d = max(0.01f, sqrt(dx * dx + dy * dy))
        val diff = (d - rest) / d * 0.5f
        if (d > rest * 3.4f) {
            alive[i] = false
            alive[j] = false
            return
        }
        if (i % cols == 0 && y[i] <= 22.5f) {
            x[j] -= dx * diff * 2f
            y[j] -= dy * diff * 2f
        } else if (j % cols == 0 && y[j] <= 22.5f) {
            x[i] += dx * diff * 2f
            y[i] += dy * diff * 2f
        } else {
            x[i] += dx * diff
            y[i] += dy * diff
            x[j] -= dx * diff
            y[j] -= dy * diff
        }
    }

    override fun step(dt: Float) {
        super.step(dt)
        val g = spec.gravity
        for (i in 0 until cols * rows) {
            if (!alive[i]) continue
            val vx = (x[i] - px[i]) * spec.damping
            val vy = (y[i] - py[i]) * spec.damping
            px[i] = x[i]
            py[i] = y[i]
            x[i] += vx
            y[i] += vy + g * dt * dt * 60f
        }
        repeat(3) {
            for (r in 0 until rows) for (c in 0 until cols) {
                val i = r * cols + c
                if (c < cols - 1) constrain(i, i + 1, spacingX)
                if (r < rows - 1) constrain(i, i + cols, spacingY)
            }
            for (c in 0 until cols) {
                val i = c
                x[i] = SIM_W * 0.15f + c * spacingX
                y[i] = 22f
            }
        }
        if (dragIdx >= 0 && dragIdx < x.size) {
            x[dragIdx] = dragX
            y[dragIdx] = dragY
        }
    }

    private var dragX = 0f
    private var dragY = 0f

    override fun drag(p: Offset, size: Size) {
        val (sx, sy) = scaleOf(size)
        val tx = p.x / sx
        val ty = p.y / sy
        var best = -1
        var bestD = Float.MAX_VALUE
        for (i in 0 until cols * rows) {
            if (!alive[i]) continue
            val d = (x[i] - tx) * (x[i] - tx) + (y[i] - ty) * (y[i] - ty)
            if (d < bestD) { bestD = d; best = i }
        }
        if (best >= 0) {
            dragIdx = best
            dragX = tx
            dragY = ty
        }
    }

    override fun draw(scope: DrawScope) {
        val (sx, sy) = scaleOf(scope.size)
        val ink = jade()
        for (r in 0 until rows) for (c in 0 until cols) {
            val i = r * cols + c
            if (!alive[i]) continue
            if (c < cols - 1 && alive[i + 1]) scope.drawLine(ink, Offset(x[i] * sx, y[i] * sy), Offset(x[i + 1] * sx, y[i + 1] * sy), strokeWidth = 1.4f * sx)
            if (r < rows - 1 && alive[i + cols]) scope.drawLine(ink, Offset(x[i] * sx, y[i] * sy), Offset(x[i + cols] * sx, y[i + cols] * sy), strokeWidth = 1.4f * sx)
        }
        scope.drawLine(ink(), Offset(SIM_W * 0.12f * sx, 22f * sy), Offset(SIM_W * 0.88f * sx, 22f * sy), strokeWidth = 2f)
    }
}

// --- softbody ----------------------------------------------------------------

private class SoftBodyWorld(spec: SimWidget) : SimWorld(spec) {
    private val n = 18
    private val x = FloatArray(n)
    private val y = FloatArray(n)
    private val vx = FloatArray(n)
    private val vy = FloatArray(n)
    private val cx = SIM_W / 2f
    private val cy = SIM_H * 0.55f
    private val radius = 52f

    init {
        for (i in 0 until n) {
            val a = (i.toFloat() / n) * 2f * Math.PI.toFloat()
            x[i] = cx + radius * cos(a)
            y[i] = cy + radius * sin(a)
        }
    }

    override fun step(dt: Float) {
        super.step(dt)
        val g = spec.gravity * 0.4f
        for (i in 0 until n) {
            // ring spring to the next vertex
            val j = (i + 1) % n
            var dx = x[j] - x[i]
            var dy = y[j] - y[i]
            var d = max(0.01f, sqrt(dx * dx + dy * dy))
            val rest = 2f * radius * sin(Math.PI.toFloat() / n)
            val f = (d - rest) * 3f
            vx[i] += dx / d * f
            vy[i] += dy / d * f
            // radial pressure back to the centroid
            dx = x[i] - cx
            dy = y[i] - cy
            d = max(0.01f, sqrt(dx * dx + dy * dy))
            val rf = (d - radius) * 1.6f
            vx[i] -= dx / d * rf
            vy[i] -= dy / d * rf
            vy[i] += g * dt
            vx[i] *= spec.damping
            vy[i] *= spec.damping
        }
        for (i in 0 until n) {
            x[i] += vx[i] * dt * 60f * 0.16f
            y[i] += vy[i] * dt * 60f * 0.16f
        }
    }

    override fun tap(p: Offset, size: Size) {
        val (sx, sy) = scaleOf(size)
        val tx = p.x / sx
        val ty = p.y / sy
        for (i in 0 until n) {
            val dx = x[i] - tx
            val dy = y[i] - ty
            val d = max(1f, sqrt(dx * dx + dy * dy))
            if (d < 70f) {
                vx[i] += dx / d * 60f
                vy[i] += dy / d * 60f
            }
        }
    }

    override fun draw(scope: DrawScope) {
        val (sx, sy) = scaleOf(scope.size)
        val path = Path()
        for (i in 0 until n) {
            if (i == 0) path.moveTo(x[i] * sx, y[i] * sy) else path.lineTo(x[i] * sx, y[i] * sy)
        }
        path.close()
        scope.drawPath(path, accent().copy(alpha = 0.30f))
        scope.drawPath(path, accent(), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
    }
}

// --- fluid -------------------------------------------------------------------

private class FluidWorld(spec: SimWidget) : SimWorld(spec) {
    private val n = spec.count.coerceIn(20, 100)
    private val x = FloatArray(n)
    private val y = FloatArray(n)
    private val vx = FloatArray(n)
    private val vy = FloatArray(n)

    init {
        for (i in 0 until n) {
            x[i] = SIM_W * (0.3f + rnd.nextFloat() * 0.4f)
            y[i] = SIM_H * (0.15f + rnd.nextFloat() * 0.5f)
        }
    }

    override fun step(dt: Float) {
        super.step(dt)
        val g = spec.gravity
        for (i in 0 until n) {
            vy[i] += g * dt
            // neighbour separation — the cheap half of SPH, enough for sloshing
            for (j in i + 1 until n) {
                val dx = x[j] - x[i]
                val dy = y[j] - y[i]
                val d2 = dx * dx + dy * dy
                if (d2 < 100f && d2 > 0.01f) {
                    val d = sqrt(d2)
                    val push = (10f - d) * 0.5f / d
                    vx[i] -= dx * push
                    vy[i] -= dy * push
                    vx[j] += dx * push
                    vy[j] += dy * push
                }
            }
            vx[i] *= spec.damping
            vy[i] *= spec.damping
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            if (y[i] > SIM_H - 6f) { y[i] = SIM_H - 6f; vy[i] = -abs(vy[i]) * 0.5f }
            if (y[i] < 4f) { y[i] = 4f; vy[i] = abs(vy[i]) * 0.5f }
            if (x[i] < 4f) { x[i] = 4f; vx[i] = abs(vx[i]) * 0.6f }
            if (x[i] > SIM_W - 4f) { x[i] = SIM_W - 4f; vx[i] = -abs(vx[i]) * 0.6f }
        }
    }

    override fun drag(p: Offset, size: Size) {
        val (sx, sy) = scaleOf(size)
        val tx = p.x / sx
        val ty = p.y / sy
        for (i in 0 until n) {
            val dx = x[i] - tx
            val dy = y[i] - ty
            val d = max(1f, sqrt(dx * dx + dy * dy))
            if (d < 60f) {
                vx[i] += dx / d * 40f
                vy[i] += dy / d * 40f
            }
        }
    }

    override fun draw(scope: DrawScope) {
        val (sx, sy) = scaleOf(scope.size)
        for (i in 0 until n) scope.drawCircle(jade().copy(alpha = 0.85f), radius = 4.2f * sx, center = Offset(x[i] * sx, y[i] * sy))
    }
}

// --- smoke -------------------------------------------------------------------

private class SmokeWorld(spec: SimWidget) : SimWorld(spec) {
    private val n = spec.count.coerceIn(20, 110)
    private val x = FloatArray(n)
    private val y = FloatArray(n)
    private val vy = FloatArray(n)
    private val phase = FloatArray(n)
    private val life = FloatArray(n)

    init {
        for (i in 0 until n) respawn(i, true)
    }

    private fun respawn(i: Int, spread: Boolean) {
        x[i] = SIM_W / 2f + (rnd.nextFloat() - 0.5f) * 30f
        y[i] = if (spread) SIM_H * rnd.nextFloat() else SIM_H - 10f
        vy[i] = -(26f + rnd.nextFloat() * 30f)
        phase[i] = rnd.nextFloat() * 6.28f
        life[i] = if (spread) rnd.nextFloat() else 1f
    }

    override fun step(dt: Float) {
        super.step(dt)
        val rise = max(0.2f, -spec.gravity * 0.08f)  // negative gravity knob = faster smoke
        for (i in 0 until n) {
            y[i] += vy[i] * dt * max(0.5f, rise)
            x[i] += sin(frames * 0.05f + phase[i]) * 12f * dt
            life[i] -= dt * 0.22f
            if (life[i] <= 0f || y[i] < 8f) respawn(i, false)
        }
    }

    override fun tap(p: Offset, size: Size) {
        val (sx, sy) = scaleOf(size)
        val tx = p.x / sx
        for (i in 0 until n) if (rnd.nextFloat() < 0.2f) {
            x[i] = tx + (rnd.nextFloat() - 0.5f) * 24f
            y[i] = SIM_H - 10f
            life[i] = 1f
        }
    }

    override fun draw(scope: DrawScope) {
        val (sx, sy) = scaleOf(scope.size)
        for (i in 0 until n) {
            val r = (4f + (1f - life[i]) * 14f) * sx
            scope.drawCircle(ink().copy(alpha = 0.34f * life[i]), radius = r, center = Offset(x[i] * sx, y[i] * sy))
        }
    }
}

// --- rigid -------------------------------------------------------------------

private class RigidWorld(spec: SimWidget) : SimWorld(spec) {
    private val n = spec.count.coerceIn(4, 24)
    private val w = FloatArray(n) { 16f + rnd.nextFloat() * 16f }
    private val h = FloatArray(n) { 10f + rnd.nextFloat() * 10f }
    private val x = FloatArray(n)
    private val y = FloatArray(n)
    private val vy = FloatArray(n)
    private val vx = FloatArray(n)

    init {
        for (i in 0 until n) {
            x[i] = SIM_W / 2f + (rnd.nextFloat() - 0.5f) * 60f
            y[i] = SIM_H - 12f - i * 22f
        }
    }

    override fun step(dt: Float) {
        super.step(dt)
        val g = spec.gravity
        for (i in 0 until n) {
            vy[i] += g * dt
            y[i] += vy[i] * dt
            x[i] += vx[i] * dt
            // floor
            if (y[i] > SIM_H - h[i] / 2f) {
                y[i] = SIM_H - h[i] / 2f
                vy[i] = -abs(vy[i]) * spec.damping * 0.4f
                vx[i] *= 0.9f
            }
            // stack: land on the nearest box below with x overlap
            for (j in 0 until n) {
                if (i == j) continue
                val overlapX = abs(x[i] - x[j]) < (w[i] + w[j]) / 2f * 0.9f
                val below = y[j] > y[i]
                val gap = (y[j] - h[j] / 2f) - (y[i] + h[i] / 2f)
                if (overlapX && below && gap < 1f && vy[i] >= 0f) {
                    y[i] = y[j] - h[j] / 2f - h[i] / 2f
                    vy[i] = -abs(vy[i]) * spec.damping * 0.3f
                    if (abs(vx[i]) < 2f) vx[i] += (rnd.nextFloat() - 0.5f) * 14f
                }
            }
            if (x[i] < w[i] / 2f || x[i] > SIM_W - w[i] / 2f) vx[i] = -vx[i] * 0.6f
        }
    }

    override fun tap(p: Offset, size: Size) {
        val (sx, sy) = scaleOf(size)
        val tx = p.x / sx
        for (i in 0 until n) vx[i] += (x[i] - tx).coerceIn(-1f, 1f) * 40f
    }

    override fun draw(scope: DrawScope) {
        val (sx, sy) = scaleOf(scope.size)
        for (i in 0 until n) {
            scope.drawRect(
                amber().copy(alpha = 0.9f),
                topLeft = Offset((x[i] - w[i] / 2f) * sx, (y[i] - h[i] / 2f) * sy),
                size = Size(w[i] * sx, h[i] * sy)
            )
        }
    }
}

// --- terrain -----------------------------------------------------------------

private class TerrainWorld(spec: SimWidget) : SimWorld(spec) {
    private val cols = 33
    private val heights = FloatArray(cols) { i -> SIM_H * 0.55f + sin(i * 0.6f) * 10f }

    override fun drag(p: Offset, size: Size) {
        val (sx, sy) = scaleOf(size)
        val gx = (p.x / sx / SIM_W * (cols - 1)).toInt().coerceIn(1, cols - 2)
        for (k in gx - 2..gx + 2) {
            if (k in 0 until cols) {
                val falloff = exp(-0.5f * ((k - gx) / 1.4f).let { it * it })
                heights[k] = (heights[k] - 9f * falloff).coerceAtLeast(SIM_H * 0.15f)
            }
        }
    }

    override fun draw(scope: DrawScope) {
        val (sx, sy) = scaleOf(scope.size)
        val path = Path().apply {
            moveTo(0f, SIM_H * sy)
            for (i in 0 until cols) lineTo(i * (SIM_W / (cols - 1)) * sx, heights[i] * sy)
            lineTo(SIM_W * sx, SIM_H * sy)
            close()
        }
        scope.drawPath(path, jade().copy(alpha = 0.5f))
        scope.drawPath(path, jade(), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
    }
}

// --- game --------------------------------------------------------------------

private class GameWorld(spec: SimWidget) : SimWorld(spec) {
    var score = 0
        private set
    var lives = 3
        private set
    var over = false
        private set
    private var px = SIM_W / 2f
    private val py = SIM_H - 18f
    private val fx = FloatArray(8) { -100f }
    private val fy = FloatArray(8) { -100f }
    private val isCoin = BooleanArray(8) { true }
    private val fall = FloatArray(8) { 60f + rnd.nextFloat() * 40f }
    private var spawnClock = 0

    override fun step(dt: Float) {
        super.step(dt)
        if (over) return
        spawnClock++
        if (spawnClock % 26 == 0) {
            val slot = (rnd.nextFloat() * 8).toInt().coerceIn(0, 7)
            fx[slot] = 20f + rnd.nextFloat() * (SIM_W - 40f)
            fy[slot] = -8f
            isCoin[slot] = rnd.nextFloat() < 0.6f
            fall[slot] = 60f + rnd.nextFloat() * 60f
        }
        for (i in 0 until 8) {
            if (fy[i] < -50f) continue
            fy[i] += fall[i] * dt
            val dx = abs(fx[i] - px)
            val dy = abs(fy[i] - py)
            if (dx < 14f && dy < 12f) {
                if (isCoin[i]) score += 10 else lives -= 1
                fx[i] = -100f
                if (lives <= 0) over = true
            }
            if (fy[i] > SIM_H + 10f) fx[i] = -100f
        }
    }

    override fun drag(p: Offset, size: Size) {
        val (sx, _) = scaleOf(size)
        px = (p.x / sx).coerceIn(12f, SIM_W - 12f)
    }

    override fun draw(scope: DrawScope) {
        val (sx, sy) = scaleOf(scope.size)
        for (i in 0 until 8) {
            if (fx[i] < -50f) continue
            scope.drawCircle(if (isCoin[i]) amber() else rose(), radius = 6f * sx, center = Offset(fx[i] * sx, fy[i] * sy))
        }
        scope.drawCircle(accent(), radius = 9f * sx, center = Offset(px * sx, py * sy))
    }
}

// --- host --------------------------------------------------------------------

private fun createWorld(w: SimWidget): SimWorld = when (w.sim) {
    "cloth" -> ClothWorld(w)
    "softbody" -> SoftBodyWorld(w)
    "fluid" -> FluidWorld(w)
    "smoke" -> SmokeWorld(w)
    "rigid" -> RigidWorld(w)
    "terrain" -> TerrainWorld(w)
    "game" -> GameWorld(w)
    else -> ParticleWorld(w)
}

@Composable
internal fun SimWidgetView(w: SimWidget) {
    val ctx = LocalContext.current
    var world by remember(w) { mutableStateOf(createWorld(w)) }
    val prefs = remember(w) { ctx.getSharedPreferences("gs.game.best", android.content.Context.MODE_PRIVATE) }
    var best by remember(w) { mutableIntStateOf(prefs.getInt("best", 0)) }
    var frame by remember(w) { mutableIntStateOf(0) }
    var running by remember(w) { mutableStateOf(true) }
    var speed by remember(w) { mutableStateOf(w.speed) }

    LaunchedEffect(world, running) {
        var last = 0L
        while (running) {
            withFrameNanos { now ->
                if (last != 0L) world.step(((now - last) / 1_000_000_000f) * speed)
                last = now
            }
            frame++
        }
    }
    val game = world as? GameWorld
    if (game != null) {
        // persist the best score OUTSIDE composition — an effect keyed on score
        LaunchedEffect(game.score) {
            if (game.score > best) {
                best = game.score
                prefs.edit().putInt("best", game.score).apply()
            }
        }
    }

    WidgetCardShell(w.title.ifEmpty { "Simulation — ${w.sim}" }, "sim") {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(210.dp)
                .pointerInput(world) {
                    detectTapGestures { p ->
                        val g = world as? GameWorld
                        if (g != null && g.over) world = createWorld(w) else world.tap(p, size)
                    }
                }
                .pointerInput(world) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        world.drag(change.position, size)
                    }
                }
        ) {
            world.draw(this)
        }
        val caption = when (world) {
            is ClothWorld -> "drag the fabric — pull hard to tear · top row pinned"
            is SoftBodyWorld -> "tap to poke the pressure ring"
            is FluidWorld -> "drag to slosh the tank"
            is SmokeWorld -> "tap to stoke the source"
            is RigidWorld -> "tap to shove the stack"
            is TerrainWorld -> "drag to sculpt the terrain"
            is GameWorld -> {
                val g = world as GameWorld
                if (g.over) "over — score ${g.score} · tap the stage to restart · best $best"
                else "drag to dodge · coins +10 · rocks cost a life · best $best"
            }
            else -> "tap to burst the fountain"
        }
        Text(caption, style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { running = !running }) { Text(if (running) "Pause" else "Resume") }
            TextButton(onClick = { world = createWorld(w); frame = 0 }) { Text("Reset") }
            Text(
                "speed ${"%.1f".format(speed)}×",
                style = MaterialTheme.typography.labelSmall,
                color = GsTheme.colors.textTertiary,
                modifier = Modifier.weight(1f)
            )
        }
        Slider(value = speed, onValueChange = { speed = it }, valueRange = 0.1f..3f, modifier = Modifier.fillMaxWidth())
    }
}
