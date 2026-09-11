package com.grapsee.gsai.ui.orbs

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Native Kotlin transcription of the thinking-orbs geometry engine
 * (Jakubantalik/thinking-orbs@0.3.1, MIT — vendored spec in docs/orbs/).
 *
 * Geometry is pure math over (size, t, opts): no rendering surface, no theme,
 * and — by the Phase 4 performance contract — no allocation in the per-frame
 * path. One [OrbFrameBuffer] is created per mounted orb and reused across
 * frames; every dot position, radius and ink value is recomputed into it.
 * `dark` affects only ink at paint time (see ThinkingOrb.kt), never geometry.
 *
 * Fidelity is enforced by OrbGoldenTest: frozen-timestamp dot lists from the
 * upstream parity harness (docs/orbs/orbs-golden-subset.json) must match to
 * 1e-4 — libm ulp headroom, far below a device pixel.
 */
internal object OrbMath {
    fun lerp(a: Double, b: Double, f: Double): Double = a + (b - a) * f
    fun frac(x: Double): Double = x - floor(x)

    /** Deterministic hash in [0, 1) — verbatim from the reference. */
    fun hashD(a: Double, b: Double): Double {
        val h = sin(a * 12.9898 + b * 78.233) * 43758.5453
        return h - floor(h)
    }

    /** Value noise on a 2D lattice — smooth, deterministic, cheap. */
    fun vnoise(x: Double, y: Double): Double {
        val xi = floor(x)
        val yi = floor(y)
        var fx = x - xi
        var fy = y - yi
        fx = fx * fx * (3 - 2 * fx)
        fy = fy * fy * (3 - 2 * fy)
        val ka = hashD(xi, yi)
        val kb = hashD(xi + 1, yi)
        val kc = hashD(xi, yi + 1)
        val kd = hashD(xi + 1, yi + 1)
        return ka + (kb - ka) * fx + (kc - ka) * fy + (ka - kb - kc + kd) * fx * fy
    }

    /** Stable directions on a unit sphere (Fibonacci lattice) — writes x,y,z into [out]. */
    fun fibDir(i: Int, n: Int, out: DoubleArray) {
        val golden = PI * (3 - sqrt(5.0))
        val y = 1 - (2 * (i + 0.5)) / n
        val rad = sqrt(1 - y * y)
        val a = i * golden
        out[0] = rad * cos(a)
        out[1] = y
        out[2] = rad * sin(a)
    }

    /** Shortest signed angular distance, wrapped to (−π, π]. */
    fun angleDelta(a: Double, b: Double): Double = atan2(sin(a - b), cos(a - b))

    /** Dot radii were tuned for a 300-unit frame; sub-linear scaling keeps small spinners legible. */
    fun radiusScale(size: Double, pow: Double): Double = (size / 300.0).pow(pow)
}

/**
 * Shared spin + tilt + orthographic projection (upstream makeProj): rotates a
 * world point by yaw then tilt, projects orthographically. x/y scale; z stays
 * in world units — that is what every depth term below expects. One instance
 * lives on the frame buffer and is re-set per frame; never allocated per frame.
 */
internal class Proj {
    var sy = 0.0
    var cyw = 0.0
    var st = 0.0
    var ct = 0.0
    var cx = 0.0
    var cy = 0.0
    var scale = 1.0

    fun set(yaw: Double, tilt: Double, cx: Double, cy: Double, scale: Double) {
        sy = sin(yaw)
        cyw = cos(yaw)
        st = sin(tilt)
        ct = cos(tilt)
        this.cx = cx
        this.cy = cy
        this.scale = scale
    }

    fun apply(x: Double, y: Double, z: Double, out: DoubleArray) {
        val x1 = x * cyw + z * sy
        val z1 = -x * sy + z * cyw
        val y1 = y * ct - z1 * st
        val z2 = y * st + z1 * ct
        out[0] = cx + x1 * scale
        out[1] = cy - y1 * scale
        out[2] = z2
    }
}

/**
 * Preallocated draw list for one frame: dots z-sorted far→near into
 * [drawOrder], lines drawn before dots (only the `connecting` web emits
 * lines). The per-frame path fills these arrays in place — zero allocation.
 */
internal class OrbFrameBuffer(maxDots: Int = 1024, maxLines: Int = 1024) {
    var dotCount = 0
        private set
    var lineCount = 0
        private set

    val px = DoubleArray(maxDots)
    val py = DoubleArray(maxDots)
    val pz = DoubleArray(maxDots)
    val pr = DoubleArray(maxDots)
    val pWhite = DoubleArray(maxDots)
    val pAlpha = DoubleArray(maxDots)

    val lx1 = DoubleArray(maxLines)
    val ly1 = DoubleArray(maxLines)
    val lx2 = DoubleArray(maxLines)
    val ly2 = DoubleArray(maxLines)
    val lWhite = DoubleArray(maxLines)
    val lAlpha = DoubleArray(maxLines)
    val lWidth = DoubleArray(maxLines)

    /** Indices into the dot arrays, far→near, after [finalize]. */
    val drawOrder = IntArray(maxDots)

    val proj = Proj()

    // Per-frame scratch shared by the mode functions (allocation-free math).
    val out3 = DoubleArray(3)
    val out2 = DoubleArray(2)
    val solveAmount = DoubleArray(40)
    val morphPts = DoubleArray(2 * 160)
    val morphSegLen = DoubleArray(160)
    val webNodes = DoubleArray(3 * 128)

    // Merge-sort scratch.
    private val orderScratch = IntArray(maxDots)

    fun addDot(x: Double, y: Double, z: Double, r: Double, white: Double, alpha: Double = 1.0) {
        if (dotCount >= px.size) return
        px[dotCount] = x
        py[dotCount] = y
        pz[dotCount] = z
        pr[dotCount] = r
        pWhite[dotCount] = white
        pAlpha[dotCount] = alpha
        dotCount++
    }

    fun addLine(x1: Double, y1: Double, x2: Double, y2: Double, white: Double, alpha: Double, width: Double) {
        if (lineCount >= lx1.size) return
        lx1[lineCount] = x1
        ly1[lineCount] = y1
        lx2[lineCount] = x2
        ly2[lineCount] = y2
        lWhite[lineCount] = white
        lAlpha[lineCount] = alpha
        lWidth[lineCount] = width
        lineCount++
    }

    fun reset() {
        dotCount = 0
        lineCount = 0
    }

    /**
     * Upstream finalizeFrame: cull alpha < 0.02, clamp radii to the mode
     * floor, z-sort far→near into draw order. The merge sort below is stable
     * (ties keep insertion order) — exactly the reference's stable
     * `sort((a, b) => a.z - b.z)`, which matters for flat modes like `morph`
     * where every dot shares z = 0.
     */
    fun finalize(rMin: Double) {
        var w = 0
        for (i in 0 until dotCount) {
            if (pAlpha[i] < 0.02) continue
            if (w != i) {
                px[w] = px[i]
                py[w] = py[i]
                pz[w] = pz[i]
                pr[w] = pr[i]
                pWhite[w] = pWhite[i]
                pAlpha[w] = pAlpha[i]
            }
            if (pr[w] < rMin) pr[w] = rMin
            w++
        }
        dotCount = w
        // lines are filtered too (upstream: lines.filter((l) => (l.a ?? 1) >= 0.02))
        var lw = 0
        for (i in 0 until lineCount) {
            if (lAlpha[i] < 0.02) continue
            if (lw != i) {
                lx1[lw] = lx1[i]
                ly1[lw] = ly1[i]
                lx2[lw] = lx2[i]
                ly2[lw] = ly2[i]
                lWhite[lw] = lWhite[i]
                lAlpha[lw] = lAlpha[i]
                lWidth[lw] = lWidth[i]
            }
            lw++
        }
        lineCount = lw
        sortIndicesByZ(dotCount)
    }

    /** Stable bottom-up merge sort of draw indices by ascending z (ties: insertion order). */
    private fun sortIndicesByZ(n: Int) {
        for (i in 0 until n) drawOrder[i] = i
        if (n < 2) return
        var width = 1
        while (width < n) {
            var lo = 0
            while (lo < n) {
                val mid = minOf(lo + width, n)
                val hi = minOf(lo + 2 * width, n)
                var i = lo
                var j = mid
                var k = lo
                while (i < mid && j < hi) {
                    val zi = pz[drawOrder[i]]
                    val zj = pz[drawOrder[j]]
                    if (zi <= zj) {
                        orderScratch[k] = drawOrder[i]
                        i++
                    } else {
                        orderScratch[k] = drawOrder[j]
                        j++
                    }
                    k++
                }
                while (i < mid) {
                    orderScratch[k] = drawOrder[i]
                    i++
                    k++
                }
                while (j < hi) {
                    orderScratch[k] = drawOrder[j]
                    j++
                    k++
                }
                lo += 2 * width
            }
            System.arraycopy(orderScratch, 0, drawOrder, 0, n)
            width *= 2
        }
    }
}

// --- modes -------------------------------------------------------------------

/** `working` — particles on tilted orbits; ghost paths trace where they run. */
internal fun frameOrbits(size: Double, t: Double, o: OrbitsOpts, buf: OrbFrameBuffer) {
    val cx = size / 2
    val cy = size / 2
    val R = (size / 2) * 0.82
    buf.proj.set(t * 0.12, 0.3, cx, cy, 1.0)
    val rs = OrbMath.radiusScale(size, o.rsPow)
    val orbitN = o.orbitN
    val ghostN = o.ghostN
    val particles = o.particles
    for (orb in 0 until orbitN) {
        val h1 = OrbMath.hashD(orb.toDouble(), 1.7)
        val h2 = OrbMath.hashD(orb.toDouble(), 5.2)
        val h3 = OrbMath.hashD(orb.toDouble(), 8.9)
        val ro = R * (0.45 + 0.52 * h1)
        val th = h1 * 2 * PI
        val phi = acos(2 * h2 - 1)
        // orbit plane basis (u, v ⟂ normal n)
        val nx = sin(phi) * cos(th)
        val ny = cos(phi)
        val nz = sin(phi) * sin(th)
        var ux = -ny
        var uy = nx
        val ul = max(1e-6, sqrt(ux * ux + uy * uy))
        ux /= ul
        uy /= ul
        val vx = -nz * uy
        val vy = nz * ux
        val vz = nx * uy - ny * ux
        val speed = (0.25 + 0.55 * h3) * (if (h3 > 0.5) 1.0 else -1.0)
        // ghost path
        for (k in 0 until ghostN) {
            val a = (k.toDouble() / ghostN) * 2 * PI
            buf.proj.apply(
                (ux * cos(a) + vx * sin(a)) * ro,
                (uy * cos(a) + vy * sin(a)) * ro,
                (vz * sin(a)) * ro,
                buf.out3
            )
            val depth = (buf.out3[2] / ro + 1) / 2
            buf.addDot(
                buf.out3[0], buf.out3[1], buf.out3[2],
                r = o.ghostR * rs,
                white = 0.72,
                alpha = o.ghostA * (0.4 + 0.6 * depth)
            )
        }
        // the particles doing the work
        for (pi in 0 until particles) {
            val a = t * speed + (pi.toDouble() / particles) * 2 * PI + h2 * 6
            buf.proj.apply(
                (ux * cos(a) + vx * sin(a)) * ro,
                (uy * cos(a) + vy * sin(a)) * ro,
                (vz * sin(a)) * ro,
                buf.out3
            )
            val depth = (buf.out3[2] / ro + 1) / 2
            buf.addDot(
                buf.out3[0], buf.out3[1], buf.out3[2],
                r = (o.partR + o.partRDepth * depth) * rs,
                white = 0.3 - 0.22 * depth
            )
        }
    }
    buf.finalize(o.rMin)
}

/**
 * `searching` — a lat/long dotted globe; a scan meridian sweeps as a size
 * ripple, and un-scanned dots dim so the meridian reads clearly.
 */
internal fun frameGlobe(size: Double, t: Double, o: GlobeOpts, buf: OrbFrameBuffer) {
    val spin = 0.5
    val cx = size / 2
    val cy = size / 2
    val radius = (size / 2) * 0.82
    val tilt = 0.4 + 0.06 * sin(t * 0.35)
    buf.proj.set(t * spin, tilt, cx, cy, radius)
    // scan sweeps relative to the spin; scanMul scales that relative rate
    val scan = t * (spin + (1.7 - spin) * o.scanMul)
    val rs = OrbMath.radiusScale(size, o.rsPow)
    val latRings = o.latRings
    val lonDensity = o.lonDensity
    for (li in 0..latRings) {
        val lat = -PI / 2 + (li.toDouble() / latRings) * PI
        val cosLat = cos(lat)
        val sinLat = sin(lat)
        val lonCount = max(1, (abs(cosLat) * lonDensity).roundToInt())
        for (lj in 0 until lonCount) {
            val lon = (lj.toDouble() / lonCount) * 2 * PI
            buf.proj.apply(cosLat * cos(lon), sinLat, cosLat * sin(lon), buf.out3)
            val depth = (buf.out3[2] + 1) / 2
            // the scan: a moving meridian read as a size ripple, not a shine
            val d = OrbMath.angleDelta(lon + t * spin, scan)
            val boost = exp(-(d * d) / 0.18) * max(0.0, buf.out3[2])
            buf.addDot(
                buf.out3[0], buf.out3[1], buf.out3[2],
                r = (o.rBase + o.rDepth * depth + o.rBoost * boost) * rs,
                white = o.inkFar - o.inkSpan * depth,
                alpha = o.dimBase + (1 - o.dimBase) * min(1.0, boost)
            )
        }
    }
    buf.finalize(o.rMin)
}

// --- the shared rubik solver heartbeat --------------------------------------

internal class RubikMove(val axis: Int, val lo: Double, val hi: Double, val ang: Double)

private val rubikMoveCache = HashMap<Int, Array<RubikMove>>()

private fun rubikMoves(count: Int): Array<RubikMove> =
    rubikMoveCache.getOrPut(count) {
        Array(count) { i ->
            val axis = min(2, floor(OrbMath.hashD(i.toDouble(), 2.3) * 3).toInt())
            val lo = -1.0 + 0.5 * min(3, floor(OrbMath.hashD(i.toDouble(), 5.9) * 4).toInt())
            val dir = if (OrbMath.hashD(i.toDouble(), 7.7) < 0.5) 1.0 else -1.0
            RubikMove(axis, lo, lo + 0.5, dir * PI / 2)
        }
    }

/** Rapid eased quarter-turns scramble, then replay in reverse so everything clicks back solved. */
private fun solveCycle(time: Double, count: Int, slotDur: Double, rest: Double, amount: DoubleArray): Int {
    val cyc = 2 * count * slotDur + rest
    val tc = time % cyc
    var active = -1
    if (tc < 2 * count * slotDur) {
        val slot = floor(tc / slotDur).toInt()
        val p = (tc - slot * slotDur) / slotDur
        val cl = min(1.0, p / 0.7)
        val ep = 1 - (1 - cl).pow(3) // machine ease-out
        if (slot < count) {
            for (i in 0 until slot) amount[i] = 1.0
            amount[slot] = ep
            active = slot
        } else {
            val u = 2 * count - 1 - slot
            for (i in 0 until u) amount[i] = 1.0
            amount[u] = 1 - ep
            active = u
        }
    }
    return active
}

private fun applyRubikMoves(
    x0: Double, y0: Double, z0: Double,
    moves: Array<RubikMove>,
    amount: DoubleArray,
    active: Int,
    out: DoubleArray
): Boolean {
    var x = x0
    var y = y0
    var z = z0
    var inActive = false
    for (i in moves.indices) {
        if (amount[i] <= 0.0) continue
        val mv = moves[i]
        val coord = when (mv.axis) {
            0 -> x
            1 -> y
            else -> z
        }
        if (coord < mv.lo || coord >= mv.hi) continue
        if (i == active) inActive = true
        val a = mv.ang * amount[i]
        val ca = cos(a)
        val sa = sin(a)
        when (mv.axis) {
            0 -> {
                val y2 = y * ca - z * sa
                z = y * sa + z * ca
                y = y2
            }
            1 -> {
                val x2 = x * ca + z * sa
                z = -x * sa + z * ca
                x = x2
            }
            else -> {
                val x2 = x * ca - y * sa
                y = x * sa + y * ca
                x = x2
            }
        }
    }
    out[0] = x
    out[1] = y
    out[2] = z
    return inActive
}

/** `solving` — sphere bands twist in quarter turns: scramble → solve → rest. */
internal fun frameRubik(size: Double, t: Double, o: RubikOpts, buf: OrbFrameBuffer) {
    val cx = size / 2
    val cy = size / 2
    val R = (size / 2) * 0.82
    buf.proj.set(t * 0.55, 0.35 + 0.1 * sin(t * 0.9), cx, cy, R)
    val rs = OrbMath.radiusScale(size, o.rsPow)
    val moves = rubikMoves(o.moveCount)
    java.util.Arrays.fill(buf.solveAmount, 0.0)
    val active = solveCycle(t, o.moveCount, 0.42, 1.2, buf.solveAmount)
    val latRings = o.latRings
    val lonDensity = o.lonDensity
    for (li in 0..latRings) {
        val lat = -PI / 2 + (li.toDouble() / latRings) * PI
        val cosLat = cos(lat)
        val sinLat = sin(lat)
        val lonCount = max(1, (abs(cosLat) * lonDensity).roundToInt())
        for (lj in 0 until lonCount) {
            val lon = (lj.toDouble() / lonCount) * 2 * PI
            val inActive = applyRubikMoves(
                cosLat * cos(lon), sinLat, cosLat * sin(lon),
                moves, buf.solveAmount, active, buf.out3
            )
            buf.proj.apply(buf.out3[0], buf.out3[1], buf.out3[2], buf.out3)
            val depth = (buf.out3[2] + 1) / 2
            // the band being turned inks a touch darker — the "hand"
            buf.addDot(
                buf.out3[0], buf.out3[1], buf.out3[2],
                r = (o.rBase + o.rDepth * depth + (if (inActive) o.rActive else 0.0)) * rs,
                white = o.inkFar - o.inkSpan * depth - (if (inActive) 0.14 else 0.0)
            )
        }
    }
    buf.finalize(o.rMin)
}

/** `listening` — a two-tempi waveform rolls through the sphere's rings. */
internal fun frameWave(size: Double, t: Double, o: WaveOpts, buf: OrbFrameBuffer) {
    val cx = size / 2
    val cy = size / 2
    // 0.76 base × 1.15 — the undulation pulls the sphere inward, so wave reads
    // ~15% smaller than the other lattice modes; scaled up to match them
    val R = (size / 2) * 0.874
    buf.proj.set(t * 0.18, 0.38, cx, cy, 1.0)
    val rs = OrbMath.radiusScale(size, o.rsPow)
    val rings = o.rings
    val lonDensity = o.lonDensity
    for (ri in 0..rings) {
        val lat = -PI / 2 + (ri.toDouble() / rings) * PI
        val cosLat = cos(lat)
        val sinLat = sin(lat)
        // two waves, different tempi — organic, never quite repeating
        val w = 0.62 * sin(t * 2.1 - ri * 0.52) + 0.38 * sin(t * 1.27 + ri * 0.83)
        val rr = R * (0.88 + 0.105 * w)
        val lonCount = max(1, (abs(cosLat) * lonDensity).roundToInt())
        for (lj in 0 until lonCount) {
            val lon = (lj.toDouble() / lonCount) * 2 * PI
            buf.proj.apply(cosLat * cos(lon) * rr, sinLat * rr, cosLat * sin(lon) * rr, buf.out3)
            val depth = (buf.out3[2] / R + 1) / 2
            val crest = max(0.0, w)
            buf.addDot(
                buf.out3[0], buf.out3[1], buf.out3[2],
                r = (o.rBase + o.rDepth * depth) * (1 + 0.4 * crest) * rs,
                white = 0.66 - 0.56 * depth - 0.1 * crest
            )
        }
    }
    buf.finalize(o.rMin)
}

/** `connecting` — a constellation wires itself: noise-drifting nodes, proximity edges, signal packets. */
internal fun frameWeb(size: Double, t: Double, o: WebOpts, buf: OrbFrameBuffer) {
    val cx = size / 2
    val cy = size / 2
    val R = (size / 2) * 0.8
    // the projector carries the radius as its scale, so node vectors stay
    // unit-length and distances below are in unit-sphere space
    buf.proj.set(t * 0.12, 0.32, cx, cy, R)
    val rs = OrbMath.radiusScale(size, o.rsPow)
    val nodeN = o.nodeN
    val thr = o.thr
    // nodes: fib lattice + slow noise wander, renormalised to the surface
    for (i in 0 until nodeN) {
        OrbMath.fibDir(i, nodeN, buf.out3)
        val dx = buf.out3[0]
        val dy = buf.out3[1]
        val dz = buf.out3[2]
        val x = dx + 0.3 * (OrbMath.vnoise(i * 0.31 + 9, t * 0.24) - 0.5) * 2
        val y = dy + 0.3 * (OrbMath.vnoise(i * 0.53 + 27, t * 0.21) - 0.5) * 2
        val z = dz + 0.3 * (OrbMath.vnoise(i * 0.77 + 55, t * 0.27) - 0.5) * 2
        val l = sqrt(x * x + y * y + z * z)
        buf.webNodes[3 * i] = x / l
        buf.webNodes[3 * i + 1] = y / l
        buf.webNodes[3 * i + 2] = z / l
    }
    // edges between close neighbours, alpha by proximity + depth
    for (i in 0 until nodeN) {
        for (j in i + 1 until nodeN) {
            val dx = buf.webNodes[3 * i] - buf.webNodes[3 * j]
            val dy = buf.webNodes[3 * i + 1] - buf.webNodes[3 * j + 1]
            val dz = buf.webNodes[3 * i + 2] - buf.webNodes[3 * j + 2]
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            if (dist >= thr) continue
            buf.proj.apply(buf.webNodes[3 * i], buf.webNodes[3 * i + 1], buf.webNodes[3 * i + 2], buf.out3)
            val x1 = buf.out3[0]
            val y1 = buf.out3[1]
            val z1 = buf.out3[2]
            buf.proj.apply(buf.webNodes[3 * j], buf.webNodes[3 * j + 1], buf.webNodes[3 * j + 2], buf.out3)
            val depth = ((z1 + buf.out3[2]) / 2 + 1) / 2
            buf.addLine(
                x1, y1, buf.out3[0], buf.out3[1],
                white = 0.42,
                alpha = (1 - dist / thr) * (0.3 + 0.55 * depth),
                width = max(0.6, o.lineW * rs)
            )
        }
    }
    for (i in 0 until nodeN) {
        buf.proj.apply(buf.webNodes[3 * i], buf.webNodes[3 * i + 1], buf.webNodes[3 * i + 2], buf.out3)
        val depth = (buf.out3[2] + 1) / 2
        val pulse = 1 + 0.25 * sin(t * 1.4 + i * 2.7)
        buf.addDot(
            buf.out3[0], buf.out3[1], buf.out3[2],
            r = (o.nodeR + o.nodeRDepth * depth) * pulse * rs,
            white = 0.55 - 0.45 * depth
        )
    }
    // signals: bright packets running between randomly re-picked node pairs
    for (s in 0 until o.signals) {
        val seg = floor(t * 0.55 + s * 7.31)
        val aI = floor(OrbMath.hashD(seg, s * 3.1 + 1.7) * nodeN).toInt()
        val bI = floor(OrbMath.hashD(seg, s * 5.7 + 4.2) * nodeN).toInt()
        if (aI == bI) continue
        val f = OrbMath.frac(t * 0.55 + s * 7.31)
        val x = OrbMath.lerp(buf.webNodes[3 * aI], buf.webNodes[3 * bI], f)
        val y = OrbMath.lerp(buf.webNodes[3 * aI + 1], buf.webNodes[3 * bI + 1], f)
        val z = OrbMath.lerp(buf.webNodes[3 * aI + 2], buf.webNodes[3 * bI + 2], f)
        val l = max(1e-6, sqrt(x * x + y * y + z * z))
        buf.proj.apply(x / l, y / l, z / l, buf.out3)
        val depth = (buf.out3[2] + 1) / 2
        buf.addDot(
            buf.out3[0], buf.out3[1], buf.out3[2],
            r = (o.nodeR * 1.5 + o.nodeRDepth * depth) * rs,
            white = 0.05,
            alpha = 0.5 + 0.5 * depth
        )
    }
    buf.finalize(o.rMin)
}

/** `weaving` — three strands plait pole-to-pole around the sphere; radial breathing trades their places. */
internal fun frameBraid(size: Double, t: Double, o: BraidOpts, buf: OrbFrameBuffer) {
    val cx = size / 2
    val cy = size / 2
    val R = (size / 2) * 0.76
    buf.proj.set(t * 0.4, 0.3, cx, cy, 1.0)
    val rs = OrbMath.radiusScale(size, o.rsPow)
    val ghostN = o.ghostN
    for (i in 0 until ghostN) {
        OrbMath.fibDir(i, ghostN, buf.out3)
        buf.proj.apply(buf.out3[0] * R, buf.out3[1] * R, buf.out3[2] * R, buf.out3)
        val depth = (buf.out3[2] / R + 1) / 2
        buf.addDot(buf.out3[0], buf.out3[1], buf.out3[2], r = 0.8 * rs, white = 0.78, alpha = 0.1 + 0.22 * depth)
    }
    val strandN = o.strandN
    val turns = o.turns
    for (s in 0 until 3) {
        val phase = (s.toDouble() / 3) * 2 * PI
        for (i in 0 until strandN) {
            // u walks pole to pole; the frac() drift slides the whole strand along
            val u = (OrbMath.frac(i.toDouble() / strandN + t * 0.045) * 2 - 1) * 0.96
            val surf = sqrt(max(0.0, 1 - u * u))
            val endFade = min(1.0, (1 - abs(u)) / 0.1)
            val a = u * PI * turns + phase
            // radial breathing: strands trade places — the over/under of a plait
            val weave = 1 + 0.075 * sin(u * PI * turns * 2 + phase * 2 + t * 0.8)
            val rr = surf * R * weave
            buf.proj.apply(cos(a) * rr, u * R * weave, sin(a) * rr, buf.out3)
            val depth = (buf.out3[2] / R + 1) / 2
            buf.addDot(
                buf.out3[0], buf.out3[1], buf.out3[2],
                r = (o.rBase + o.rDepth * depth) * rs,
                white = 0.55 - 0.45 * depth,
                alpha = endFade * (0.45 + 0.55 * depth)
            )
        }
    }
    buf.finalize(o.rMin)
}

/**
 * `composing` (ribbon) — an undulating sash of parallel strands rides a great
 * circle; the tuned preset freezes the 3D tumble, leaving the traveling
 * undulation on a fixed band. The same painter drives `breathing` (ring) via
 * the faceOn flag: a face-on circle whose RADIUS — not its out-of-plane
 * offset — undulates, so it reads as a ring slowly morphing.
 */
internal fun frameRibbon(size: Double, t: Double, o: RibbonOpts, buf: OrbFrameBuffer) {
    val cx = size / 2
    val cy = size / 2
    val R = (size / 2) * 0.78
    // spin scales the 3D tumble; spin=0 freezes the band's orientation
    val spin = o.spin
    val camTilt = 0.3
    buf.proj.set(t * 0.1 * spin, camTilt, cx, cy, 1.0)
    val rs = OrbMath.radiusScale(size, o.rsPow)
    val ghostN = o.ghostN
    for (i in 0 until ghostN) {
        OrbMath.fibDir(i, ghostN, buf.out3)
        buf.proj.apply(buf.out3[0] * R, buf.out3[1] * R, buf.out3[2] * R, buf.out3)
        val depth = (buf.out3[2] / R + 1) / 2
        buf.addDot(buf.out3[0], buf.out3[1], buf.out3[2], r = 0.8 * rs, white = 0.78, alpha = 0.1 + 0.22 * depth)
    }
    // The band plane, precessing (frozen when spin=0). Face-on sets
    // ta = -camTilt so the projection's vertical squash term is 1 and the
    // band reads as a true circle rather than a tilted ellipse.
    val ya = t * 0.24 * spin
    val ta = if (o.faceOn) -camTilt else 0.55 + 0.3 * sin(t * 0.18) * spin
    val ux = cos(ya)
    val uy = 0.0
    val uz = sin(ya)
    val vx = -uz * sin(ta)
    val vy = cos(ta)
    val vz = ux * sin(ta)
    // plane normal n = u × v
    val nx = uy * vz - uz * vy
    val ny = uz * vx - ux * vz
    val nz = ux * vy - uy * vx
    // Radial lobes swell past R, so pull the base radius in by (most of) the
    // wobble amplitude — the silhouette stays inside the frame.
    val wobAmp = 0.23 * o.wobMul
    val baseR = if (o.faceOn) R / (1 + 0.85 * wobAmp) else R
    val lanes = max(1, (o.lanes * o.bandMul).roundToInt())
    val segs = o.segs
    for (w in 0 until lanes) {
        val laneOff = (w - (lanes - 1) / 2.0) * 0.075
        val edge = abs(w - (lanes - 1) / 2.0) / max(1.0, (lanes - 1) / 2.0)
        for (k in 0 until segs) {
            val a = (k.toDouble() / segs) * 2 * PI
            // the undulation: two traveling waves along the band
            val wob = (0.16 * sin(a * 3 - t * 1.7 + w * 0.22) + 0.07 * sin(a * 5 + t * 1.1)) * o.wobMul
            // Face-on modulates the in-plane RADIUS (lobes swell outward);
            // ribbon keeps the original out-of-plane sash wobble.
            val radial = if (o.faceOn) 1 + wob else 1.0
            val off = if (o.faceOn) laneOff else laneOff + wob
            val x = ux * cos(a) + vx * sin(a) + nx * off
            val y = uy * cos(a) + vy * sin(a) + ny * off
            val z = uz * cos(a) + vz * sin(a) + nz * off
            val l = sqrt(x * x + y * y + z * z)
            val rr = baseR * radial
            buf.proj.apply((x / l) * rr, (y / l) * rr, (z / l) * rr, buf.out3)
            val depth = (buf.out3[2] / R + 1) / 2
            buf.addDot(
                buf.out3[0], buf.out3[1], buf.out3[2],
                r = (o.rBase + o.rDepth * depth) * (1 - 0.25 * edge) * rs,
                white = 0.52 - 0.44 * depth + 0.18 * edge,
                alpha = 0.4 + 0.6 * depth
            )
        }
    }
    buf.finalize(o.rMin)
}

// --- morph (shaping) ---------------------------------------------------------

private fun smoothE(x: Double): Double = x * x * (3 - 2 * x)

/** A closed polygon path parameterised by arc length, precomputed once. */
private class PolyShape(verts: Array<DoubleArray>) {
    val vx = DoubleArray(verts.size)
    val vy = DoubleArray(verts.size)
    val segLen = DoubleArray(verts.size)
    val total: Double

    init {
        var sum = 0.0
        for (i in verts.indices) {
            vx[i] = verts[i][0]
            vy[i] = verts[i][1]
        }
        for (i in verts.indices) {
            val j = (i + 1) % verts.size
            val l = sqrt((vx[j] - vx[i]) * (vx[j] - vx[i]) + (vy[j] - vy[i]) * (vy[j] - vy[i]))
            segLen[i] = l
            sum += l
        }
        total = sum
    }

    fun point(f: Double, out: DoubleArray) {
        val V = vx.size
        var target = f * total
        var si = 0
        while (target > segLen[si] && si < V - 1) {
            target -= segLen[si]
            si++
        }
        val j = (si + 1) % V
        val ff = if (segLen[si] > 0) min(1.0, target / segLen[si]) else 0.0
        out[0] = vx[si] + (vx[j] - vx[si]) * ff
        out[1] = vy[si] + (vy[j] - vy[si]) * ff
    }
}

private val MORPH_TRIANGLE = PolyShape(
    arrayOf(
        doubleArrayOf(0.0, -0.26),
        doubleArrayOf(0.24, 0.16),
        doubleArrayOf(-0.24, 0.16)
    )
)

// 5-vertex walk so the path STARTS at top-centre like the other shapes
private val MORPH_SQUARE = PolyShape(
    arrayOf(
        doubleArrayOf(0.0, -0.2),
        doubleArrayOf(0.2, -0.2),
        doubleArrayOf(0.2, 0.2),
        doubleArrayOf(-0.2, 0.2),
        doubleArrayOf(-0.2, -0.2)
    )
)

private fun morphShapePoint(k: Int, f: Double, out: DoubleArray) {
    when (k) {
        0 -> {
            // circle
            val a = -PI / 2 + f * 2 * PI
            out[0] = cos(a) * 0.24
            out[1] = sin(a) * 0.24
        }
        1 -> MORPH_TRIANGLE.point(f, out)
        else -> MORPH_SQUARE.point(f, out)
    }
}

/**
 * `shaping` — a dotted outline cycling circle → triangle → square → circle.
 * Every frame the engine blends the two neighbouring paths, then lays the
 * dots EVENLY along the blended outline — spacing stays uniform at every
 * instant of the morph. Plain circle fills only.
 */
internal fun frameMorph(size: Double, t: Double, o: MorphOpts, buf: OrbFrameBuffer) {
    val K = 3
    val HOLD = 1.4
    val MORPH = 0.9
    val SEG = HOLD + MORPH
    val tc = t % (SEG * K)
    val k = floor(tc / SEG).toInt()
    val local = tc - k * SEG
    val blend = if (local > HOLD) smoothE((local - HOLD) / MORPH) else 0.0
    val sprd = o.spread
    // blend the two shape PATHS at blend, then measure the blended outline
    val M = 160
    val pts = buf.morphPts
    val segLen = buf.morphSegLen
    for (i in 0 until M) {
        val f = i.toDouble() / M
        morphShapePoint(k, f, buf.out3)
        val ax = buf.out3[0]
        val ay = buf.out3[1]
        morphShapePoint((k + 1) % K, f, buf.out3)
        val bx = buf.out3[0]
        val by = buf.out3[1]
        pts[2 * i] = (ax + (bx - ax) * blend) * sprd
        pts[2 * i + 1] = (ay + (by - ay) * blend) * sprd
    }
    var total = 0.0
    for (i in 0 until M) {
        val j = (i + 1) % M
        val dx = pts[2 * j] - pts[2 * i]
        val dy = pts[2 * j + 1] - pts[2 * i + 1]
        val l = sqrt(dx * dx + dy * dy)
        segLen[i] = l
        total += l
    }
    // dot radius depends ONLY on rDot (the size knob); the count sets the gaps.
    // Formed shapes breathe a little (uniform pulse).
    val n = max(6, (34 * o.iconD).roundToInt())
    val re = o.rDot * 1.35 * sprd
    val pulse = 1 + 0.02 * sin(local * 3.1)
    val c2 = size / 2
    var seg = 0
    var acc = 0.0
    for (k2 in 0 until n) {
        val target = (k2.toDouble() / n) * total
        while (acc + segLen[seg] < target && seg < M - 1) {
            acc += segLen[seg]
            seg++
        }
        val j = (seg + 1) % M
        val ff = if (segLen[seg] > 0) min(1.0, (target - acc) / segLen[seg]) else 0.0
        val x = (pts[2 * seg] + (pts[2 * j] - pts[2 * seg]) * ff) * pulse
        val y = (pts[2 * seg + 1] + (pts[2 * j + 1] - pts[2 * seg + 1]) * ff) * pulse
        buf.addDot(
            c2 + x * size,
            c2 + y * size,
            0.0,
            r = max(0.35, re * size),
            white = 0.1
        )
    }
    buf.finalize(o.rMin)
}

// --- dispatch ----------------------------------------------------------------

/** Renders one frame of [resolved] at geometry time [t] into [buf]. */
internal object OrbEngine {
    fun render(size: Double, t: Double, resolved: ResolvedOrb, buf: OrbFrameBuffer) {
        buf.reset()
        when (resolved.mode) {
            OrbMode.ORBITS -> frameOrbits(size, t, resolved.opts as OrbitsOpts, buf)
            OrbMode.GLOBE -> frameGlobe(size, t, resolved.opts as GlobeOpts, buf)
            OrbMode.RUBIK -> frameRubik(size, t, resolved.opts as RubikOpts, buf)
            OrbMode.WAVE -> frameWave(size, t, resolved.opts as WaveOpts, buf)
            OrbMode.WEB -> frameWeb(size, t, resolved.opts as WebOpts, buf)
            OrbMode.BRAID -> frameBraid(size, t, resolved.opts as BraidOpts, buf)
            OrbMode.RIBBON -> frameRibbon(size, t, resolved.opts as RibbonOpts, buf)
            OrbMode.RING -> frameRibbon(size, t, resolved.opts as RibbonOpts, buf)
            OrbMode.MORPH -> frameMorph(size, t, resolved.opts as MorphOpts, buf)
        }
    }
}
