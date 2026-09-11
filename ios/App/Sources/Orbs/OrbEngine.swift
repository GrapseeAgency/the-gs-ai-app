// Native Swift transcription of the thinking-orbs geometry engine
// (Jakubantalik/thinking-orbs@0.3.1, MIT — vendored spec in docs/orbs/).
//
// Geometry is pure math over (size, t, opts): no rendering surface, no theme,
// and — by the Phase 4 performance contract — no allocation in the per-frame
// path. One OrbFrameBuffer is created per mounted orb and reused across
// frames; every dot position, radius and ink value is recomputed into it.
// `dark` affects only ink at paint time (ThinkingOrbView), never geometry.
//
// Fidelity is enforced by OrbGoldenTests: frozen-timestamp dot lists from the
// upstream parity harness (docs/orbs/orbs-golden-subset.json) must match to
// 1e-4 — libm ulp headroom, far below a device pixel. The Kotlin engine in
// this repo passes the identical vectors; the two must never drift.

import Foundation

// MARK: - Math

enum OrbMath {
    static func lerp(_ a: Double, _ b: Double, _ f: Double) -> Double { a + (b - a) * f }
    static func frac(_ x: Double) -> Double { x - floor(x) }

    /// Deterministic hash in [0, 1) — verbatim from the reference.
    static func hashD(_ a: Double, _ b: Double) -> Double {
        let h = sin(a * 12.9898 + b * 78.233) * 43758.5453
        return h - floor(h)
    }

    /// Value noise on a 2D lattice — smooth, deterministic, cheap.
    static func vnoise(_ x: Double, _ y: Double) -> Double {
        let xi = floor(x)
        let yi = floor(y)
        var fx = x - xi
        var fy = y - yi
        fx = fx * fx * (3 - 2 * fx)
        fy = fy * fy * (3 - 2 * fy)
        let ka = hashD(xi, yi)
        let kb = hashD(xi + 1, yi)
        let kc = hashD(xi, yi + 1)
        let kd = hashD(xi + 1, yi + 1)
        return ka + (kb - ka) * fx + (kc - ka) * fy + (ka - kb - kc + kd) * fx * fy
    }

    /// Stable directions on a unit sphere (Fibonacci lattice) — writes x,y,z into out.
    static func fibDir(_ i: Int, _ n: Int, _ out: inout [Double]) {
        let goldenAngle = Double.pi * (3 - sqrt(5.0))
        let y = 1 - (2 * (Double(i) + 0.5)) / Double(n)
        let rad = sqrt(1 - y * y)
        let a = Double(i) * goldenAngle
        out[0] = rad * cos(a)
        out[1] = y
        out[2] = rad * sin(a)
    }

    /// Shortest signed angular distance, wrapped to (−π, π].
    static func angleDelta(_ a: Double, _ b: Double) -> Double {
        atan2(sin(a - b), cos(a - b))
    }

    /// Dot radii were tuned for a 300-unit frame; sub-linear scaling keeps small spinners legible.
    static func radiusScale(_ size: Double, _ pw: Double) -> Double {
        pow(size / 300.0, pw)
    }
}

// MARK: - Projection

/// Shared spin + tilt + orthographic projection (upstream makeProj): rotates a
/// world point by yaw then tilt, projects orthographically. x/y scale; z stays
/// in world units — that is what every depth term expects. One instance lives
/// on the frame buffer and is re-set per frame; never allocated per frame.
final class Proj {
    var sy = 0.0
    var cyw = 0.0
    var st = 0.0
    var ct = 0.0
    var cx = 0.0
    var cy = 0.0
    var scale = 1.0

    func set(yaw: Double, tilt: Double, cx: Double, cy: Double, scale: Double) {
        sy = sin(yaw)
        cyw = cos(yaw)
        st = sin(tilt)
        ct = cos(tilt)
        self.cx = cx
        self.cy = cy
        self.scale = scale
    }

    func apply(_ x: Double, _ y: Double, _ z: Double, _ out: inout [Double]) {
        let x1 = x * cyw + z * sy
        let z1 = -x * sy + z * cyw
        let y1 = y * ct - z1 * st
        let z2 = y * st + z1 * ct
        out[0] = cx + x1 * scale
        out[1] = cy - y1 * scale
        out[2] = z2
    }
}

// MARK: - Frame buffer

/// Preallocated draw list for one frame: dots z-sorted far→near into
/// drawOrder, lines drawn before dots (only the `connecting` web emits
/// lines). The per-frame path fills these arrays in place — zero allocation.
final class OrbFrameBuffer {
    let maxDots: Int
    let maxLines: Int

    private(set) var dotCount = 0
    private(set) var lineCount = 0

    let px: [Double]
    let py: [Double]
    let pz: [Double]
    let pr: [Double]
    let pWhite: [Double]
    let pAlpha: [Double]

    let lx1: [Double]
    let ly1: [Double]
    let lx2: [Double]
    let ly2: [Double]
    let lWhite: [Double]
    let lAlpha: [Double]
    let lWidth: [Double]

    /// Indices into the dot arrays, far→near, after finalize(rMin:).
    let drawOrder: [Int]

    let proj = Proj()

    // Per-frame scratch shared by the mode functions (allocation-free math).
    var out3: [Double] = Array(repeating: 0, count: 3)
    var solveAmount: [Double] = Array(repeating: 0, count: 40)
    var morphPts: [Double] = Array(repeating: 0, count: 2 * 160)
    var morphSegLen: [Double] = Array(repeating: 0, count: 160)
    var webNodes: [Double] = Array(repeating: 0, count: 3 * 128)

    private let orderScratch: [Int]

    init(maxDots: Int = 1024, maxLines: Int = 1024) {
        self.maxDots = maxDots
        self.maxLines = maxLines
        px = Array(repeating: 0, count: maxDots)
        py = Array(repeating: 0, count: maxDots)
        pz = Array(repeating: 0, count: maxDots)
        pr = Array(repeating: 0, count: maxDots)
        pWhite = Array(repeating: 0, count: maxDots)
        pAlpha = Array(repeating: 0, count: maxDots)
        lx1 = Array(repeating: 0, count: maxLines)
        ly1 = Array(repeating: 0, count: maxLines)
        lx2 = Array(repeating: 0, count: maxLines)
        ly2 = Array(repeating: 0, count: maxLines)
        lWhite = Array(repeating: 0, count: maxLines)
        lAlpha = Array(repeating: 0, count: maxLines)
        lWidth = Array(repeating: 0, count: maxLines)
        drawOrder = Array(repeating: 0, count: maxDots)
        orderScratch = Array(repeating: 0, count: maxDots)
    }

    func addDot(_ x: Double, _ y: Double, _ z: Double, r: Double, white: Double, alpha: Double = 1) {
        guard dotCount < maxDots else { return }
        px[dotCount] = x
        py[dotCount] = y
        pz[dotCount] = z
        pr[dotCount] = r
        pWhite[dotCount] = white
        pAlpha[dotCount] = alpha
        dotCount += 1
    }

    func addLine(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, white: Double, alpha: Double, width: Double) {
        guard lineCount < maxLines else { return }
        lx1[lineCount] = x1
        ly1[lineCount] = y1
        lx2[lineCount] = x2
        ly2[lineCount] = y2
        lWhite[lineCount] = white
        lAlpha[lineCount] = alpha
        lWidth[lineCount] = width
        lineCount += 1
    }

    func reset() {
        dotCount = 0
        lineCount = 0
    }

    /// Upstream finalizeFrame: cull alpha < 0.02, clamp radii to the mode
    /// floor, z-sort far→near into draw order. The merge sort below is stable
    /// (ties keep insertion order) — exactly the reference's stable
    /// `sort((a, b) => a.z - b.z)`, which matters for flat modes like `morph`
    /// where every dot shares z = 0.
    func finalize(rMin: Double) {
        var w = 0
        for i in 0..<dotCount {
            if pAlpha[i] < 0.02 { continue }
            if w != i {
                px[w] = px[i]
                py[w] = py[i]
                pz[w] = pz[i]
                pr[w] = pr[i]
                pWhite[w] = pWhite[i]
                pAlpha[w] = pAlpha[i]
            }
            if pr[w] < rMin { pr[w] = rMin }
            w += 1
        }
        dotCount = w
        // lines are filtered too (upstream: lines.filter((l) => (l.a ?? 1) >= 0.02))
        var lw = 0
        for i in 0..<lineCount {
            if lAlpha[i] < 0.02 { continue }
            if lw != i {
                lx1[lw] = lx1[i]
                ly1[lw] = ly1[i]
                lx2[lw] = lx2[i]
                ly2[lw] = ly2[i]
                lWhite[lw] = lWhite[i]
                lAlpha[lw] = lAlpha[i]
                lWidth[lw] = lWidth[i]
            }
            lw += 1
        }
        lineCount = lw
        sortIndicesByZ(dotCount)
    }

    /// Stable bottom-up merge sort of draw indices by ascending z (ties: insertion order).
    private func sortIndicesByZ(_ n: Int) {
        for i in 0..<n { drawOrder[i] = i }
        guard n >= 2 else { return }
        var width = 1
        while width < n {
            var lo = 0
            while lo < n {
                let mid = min(lo + width, n)
                let hi = min(lo + 2 * width, n)
                var i = lo
                var j = mid
                var k = lo
                while i < mid && j < hi {
                    let zi = pz[drawOrder[i]]
                    let zj = pz[drawOrder[j]]
                    if zi <= zj {
                        orderScratch[k] = drawOrder[i]
                        i += 1
                    } else {
                        orderScratch[k] = drawOrder[j]
                        j += 1
                    }
                    k += 1
                }
                while i < mid {
                    orderScratch[k] = drawOrder[i]
                    i += 1
                    k += 1
                }
                while j < hi {
                    orderScratch[k] = drawOrder[j]
                    j += 1
                    k += 1
                }
                lo += 2 * width
            }
            for idx in 0..<n { drawOrder[idx] = orderScratch[idx] }
            width *= 2
        }
    }
}

// MARK: - Modes

/// `working` — particles on tilted orbits; ghost paths trace where they run.
func frameOrbits(_ size: Double, _ t: Double, _ o: OrbitsOpts, _ buf: OrbFrameBuffer) {
    let cx = size / 2
    let cy = size / 2
    let R = (size / 2) * 0.82
    buf.proj.set(yaw: t * 0.12, tilt: 0.3, cx: cx, cy: cy, scale: 1)
    let rs = OrbMath.radiusScale(size, o.rsPow)
    for orb in 0..<o.orbitN {
        let h1 = OrbMath.hashD(Double(orb), 1.7)
        let h2 = OrbMath.hashD(Double(orb), 5.2)
        let h3 = OrbMath.hashD(Double(orb), 8.9)
        let ro = R * (0.45 + 0.52 * h1)
        let th = h1 * 2 * Double.pi
        let phi = acos(2 * h2 - 1)
        // orbit plane basis (u, v ⟂ normal n)
        let nx = sin(phi) * cos(th)
        let ny = cos(phi)
        let nz = sin(phi) * sin(th)
        var ux = -ny
        var uy = nx
        let ul = max(1e-6, sqrt(ux * ux + uy * uy))
        ux /= ul
        uy /= ul
        let vx = -nz * uy
        let vy = nz * ux
        let vz = nx * uy - ny * ux
        let speed = (0.25 + 0.55 * h3) * (h3 > 0.5 ? 1 : -1)
        // ghost path
        for k in 0..<o.ghostN {
            let a = (Double(k) / Double(o.ghostN)) * 2 * Double.pi
            buf.proj.apply(
                (ux * cos(a) + vx * sin(a)) * ro,
                (uy * cos(a) + vy * sin(a)) * ro,
                (vz * sin(a)) * ro,
                &buf.out3
            )
            let depth = (buf.out3[2] / ro + 1) / 2
            buf.addDot(
                buf.out3[0], buf.out3[1], buf.out3[2],
                r: o.ghostR * rs,
                white: 0.72,
                alpha: o.ghostA * (0.4 + 0.6 * depth)
            )
        }
        // the particles doing the work
        for pi in 0..<o.particles {
            let a = t * speed + (Double(pi) / Double(o.particles)) * 2 * Double.pi + h2 * 6
            buf.proj.apply(
                (ux * cos(a) + vx * sin(a)) * ro,
                (uy * cos(a) + vy * sin(a)) * ro,
                (vz * sin(a)) * ro,
                &buf.out3
            )
            let depth = (buf.out3[2] / ro + 1) / 2
            buf.addDot(
                buf.out3[0], buf.out3[1], buf.out3[2],
                r: (o.partR + o.partRDepth * depth) * rs,
                white: 0.3 - 0.22 * depth
            )
        }
    }
    buf.finalize(rMin: o.rMin)
}

/// `searching` — a lat/long dotted globe; a scan meridian sweeps as a size
/// ripple, and un-scanned dots dim so the meridian reads clearly.
func frameGlobe(_ size: Double, _ t: Double, _ o: GlobeOpts, _ buf: OrbFrameBuffer) {
    let spin = 0.5
    let cx = size / 2
    let cy = size / 2
    let radius = (size / 2) * 0.82
    let tilt = 0.4 + 0.06 * sin(t * 0.35)
    buf.proj.set(yaw: t * spin, tilt: tilt, cx: cx, cy: cy, scale: radius)
    // scan sweeps relative to the spin; scanMul scales that relative rate
    let scan = t * (spin + (1.7 - spin) * o.scanMul)
    let rs = OrbMath.radiusScale(size, o.rsPow)
    for li in 0...o.latRings {
        let lat = -Double.pi / 2 + (Double(li) / Double(o.latRings)) * Double.pi
        let cosLat = cos(lat)
        let sinLat = sin(lat)
        let lonCount = max(1, Int((abs(cosLat) * Double(o.lonDensity)).rounded()))
        for lj in 0..<lonCount {
            let lon = (Double(lj) / Double(lonCount)) * 2 * Double.pi
            buf.proj.apply(cosLat * cos(lon), sinLat, cosLat * sin(lon), &buf.out3)
            let depth = (buf.out3[2] + 1) / 2
            // the scan: a moving meridian read as a size ripple, not a shine
            let d = OrbMath.angleDelta(lon + t * spin, scan)
            let boost = exp(-(d * d) / 0.18) * max(0, buf.out3[2])
            buf.addDot(
                buf.out3[0], buf.out3[1], buf.out3[2],
                r: (o.rBase + o.rDepth * depth + o.rBoost * boost) * rs,
                white: o.inkFar - o.inkSpan * depth,
                alpha: o.dimBase + (1 - o.dimBase) * min(1, boost)
            )
        }
    }
    buf.finalize(rMin: o.rMin)
}

// MARK: the shared rubik solver heartbeat

struct RubikTurn {
    let axis: Int
    let lo: Double
    let hi: Double
    let ang: Double
}

enum RubikTurns {
    private static var cache: [Int: [RubikTurn]] = [:]

    static func turns(_ count: Int) -> [RubikTurn] {
        if let hit = cache[count] { return hit }
        var built: [RubikTurn] = []
        built.reserveCapacity(count)
        for i in 0..<count {
            let axis = min(2, Int(OrbMath.hashD(Double(i), 2.3) * 3))
            let lo = -1.0 + 0.5 * Double(min(3, Int(OrbMath.hashD(Double(i), 5.9) * 4)))
            let dir = OrbMath.hashD(Double(i), 7.7) < 0.5 ? 1.0 : -1.0
            built.append(RubikTurn(axis: axis, lo: lo, hi: lo + 0.5, ang: dir * Double.pi / 2))
        }
        cache[count] = built
        return built
    }
}

/// Rapid eased quarter-turns scramble, then replay in reverse so everything clicks back solved.
private func solveCycle(_ time: Double, _ count: Int, _ slotDur: Double, _ rest: Double, _ amount: inout [Double]) -> Int {
    let cyc = 2 * Double(count) * slotDur + rest
    let tc = time.truncatingRemainder(dividingBy: cyc)
    var active = -1
    if tc < 2 * Double(count) * slotDur {
        let slot = Int(floor(tc / slotDur))
        let p = (tc - Double(slot) * slotDur) / slotDur
        let cl = min(1, p / 0.7)
        let ep = 1 - pow(1 - cl, 3) // machine ease-out
        if slot < count {
            for i in 0..<slot { amount[i] = 1 }
            amount[slot] = ep
            active = slot
        } else {
            let u = 2 * count - 1 - slot
            for i in 0..<u { amount[i] = 1 }
            amount[u] = 1 - ep
            active = u
        }
    }
    return active
}

private func applyRubikTurns(
    _ x0: Double, _ y0: Double, _ z0: Double,
    _ turns: [RubikTurn],
    _ amount: [Double],
    _ active: Int,
    _ out: inout [Double]
) -> Bool {
    var x = x0
    var y = y0
    var z = z0
    var inActive = false
    for i in turns.indices {
        if amount[i] <= 0 { continue }
        let mv = turns[i]
        let coord: Double
        switch mv.axis {
        case 0: coord = x
        case 1: coord = y
        default: coord = z
        }
        if coord < mv.lo || coord >= mv.hi { continue }
        if i == active { inActive = true }
        let a = mv.ang * amount[i]
        let ca = cos(a)
        let sa = sin(a)
        switch mv.axis {
        case 0:
            let y2 = y * ca - z * sa
            z = y * sa + z * ca
            y = y2
        case 1:
            let x2 = x * ca + z * sa
            z = -x * sa + z * ca
            x = x2
        default:
            let x2 = x * ca - y * sa
            y = x * sa + y * ca
            x = x2
        }
    }
    out[0] = x
    out[1] = y
    out[2] = z
    return inActive
}

/// `solving` — sphere bands twist in quarter turns: scramble → solve → rest.
func frameRubik(_ size: Double, _ t: Double, _ o: RubikOpts, _ buf: OrbFrameBuffer) {
    let cx = size / 2
    let cy = size / 2
    let R = (size / 2) * 0.82
    buf.proj.set(yaw: t * 0.55, tilt: 0.35 + 0.1 * sin(t * 0.9), cx: cx, cy: cy, scale: R)
    let rs = OrbMath.radiusScale(size, o.rsPow)
    let turns = RubikTurns.turns(o.moveCount)
    for i in 0..<buf.solveAmount.count { buf.solveAmount[i] = 0 }
    let active = solveCycle(t, o.moveCount, 0.42, 1.2, &buf.solveAmount)
    for li in 0...o.latRings {
        let lat = -Double.pi / 2 + (Double(li) / Double(o.latRings)) * Double.pi
        let cosLat = cos(lat)
        let sinLat = sin(lat)
        let lonCount = max(1, Int((abs(cosLat) * Double(o.lonDensity)).rounded()))
        for lj in 0..<lonCount {
            let lon = (Double(lj) / Double(lonCount)) * 2 * Double.pi
            let inActive = applyRubikTurns(
                cosLat * cos(lon), sinLat, cosLat * sin(lon),
                turns, buf.solveAmount, active, &buf.out3
            )
            buf.proj.apply(buf.out3[0], buf.out3[1], buf.out3[2], &buf.out3)
            let depth = (buf.out3[2] + 1) / 2
            // the band being turned inks a touch darker — the "hand"
            buf.addDot(
                buf.out3[0], buf.out3[1], buf.out3[2],
                r: (o.rBase + o.rDepth * depth + (inActive ? o.rActive : 0)) * rs,
                white: o.inkFar - o.inkSpan * depth - (inActive ? 0.14 : 0)
            )
        }
    }
    buf.finalize(rMin: o.rMin)
}

/// `listening` — a two-tempi waveform rolls through the sphere's rings.
func frameWave(_ size: Double, _ t: Double, _ o: WaveOpts, _ buf: OrbFrameBuffer) {
    let cx = size / 2
    let cy = size / 2
    // 0.76 base × 1.15 — the undulation pulls the sphere inward, so wave reads
    // ~15% smaller than the other lattice modes; scaled up to match them
    let R = (size / 2) * 0.874
    buf.proj.set(yaw: t * 0.18, tilt: 0.38, cx: cx, cy: cy, scale: 1)
    let rs = OrbMath.radiusScale(size, o.rsPow)
    for ri in 0...o.rings {
        let lat = -Double.pi / 2 + (Double(ri) / Double(o.rings)) * Double.pi
        let cosLat = cos(lat)
        let sinLat = sin(lat)
        // two waves, different tempi — organic, never quite repeating
        let w = 0.62 * sin(t * 2.1 - Double(ri) * 0.52) + 0.38 * sin(t * 1.27 + Double(ri) * 0.83)
        let rr = R * (0.88 + 0.105 * w)
        let lonCount = max(1, Int((abs(cosLat) * Double(o.lonDensity)).rounded()))
        for lj in 0..<lonCount {
            let lon = (Double(lj) / Double(lonCount)) * 2 * Double.pi
            buf.proj.apply(cosLat * cos(lon) * rr, sinLat * rr, cosLat * sin(lon) * rr, &buf.out3)
            let depth = (buf.out3[2] / R + 1) / 2
            let crest = max(0, w)
            buf.addDot(
                buf.out3[0], buf.out3[1], buf.out3[2],
                r: (o.rBase + o.rDepth * depth) * (1 + 0.4 * crest) * rs,
                white: 0.66 - 0.56 * depth - 0.1 * crest
            )
        }
    }
    buf.finalize(rMin: o.rMin)
}

/// `connecting` — a constellation wires itself: noise-drifting nodes, proximity edges, signal packets.
func frameWeb(_ size: Double, _ t: Double, _ o: WebOpts, _ buf: OrbFrameBuffer) {
    let cx = size / 2
    let cy = size / 2
    let R = (size / 2) * 0.8
    // the projector carries the radius as its scale, so node vectors stay
    // unit-length and distances below are in unit-sphere space
    buf.proj.set(yaw: t * 0.12, tilt: 0.32, cx: cx, cy: cy, scale: R)
    let rs = OrbMath.radiusScale(size, o.rsPow)
    let nodeN = o.nodeN
    // nodes: fib lattice + slow noise wander, renormalised to the surface
    for i in 0..<nodeN {
        OrbMath.fibDir(i, nodeN, &buf.out3)
        let dx = buf.out3[0]
        let dy = buf.out3[1]
        let dz = buf.out3[2]
        let x = dx + 0.3 * (OrbMath.vnoise(Double(i) * 0.31 + 9, t * 0.24) - 0.5) * 2
        let y = dy + 0.3 * (OrbMath.vnoise(Double(i) * 0.53 + 27, t * 0.21) - 0.5) * 2
        let z = dz + 0.3 * (OrbMath.vnoise(Double(i) * 0.77 + 55, t * 0.27) - 0.5) * 2
        let l = sqrt(x * x + y * y + z * z)
        buf.webNodes[3 * i] = x / l
        buf.webNodes[3 * i + 1] = y / l
        buf.webNodes[3 * i + 2] = z / l
    }
    // edges between close neighbours, alpha by proximity + depth
    for i in 0..<nodeN {
        for j in (i + 1)..<nodeN {
            let dx = buf.webNodes[3 * i] - buf.webNodes[3 * j]
            let dy = buf.webNodes[3 * i + 1] - buf.webNodes[3 * j + 1]
            let dz = buf.webNodes[3 * i + 2] - buf.webNodes[3 * j + 2]
            let dist = sqrt(dx * dx + dy * dy + dz * dz)
            if dist >= o.thr { continue }
            buf.proj.apply(buf.webNodes[3 * i], buf.webNodes[3 * i + 1], buf.webNodes[3 * i + 2], &buf.out3)
            let x1 = buf.out3[0]
            let y1 = buf.out3[1]
            let z1 = buf.out3[2]
            buf.proj.apply(buf.webNodes[3 * j], buf.webNodes[3 * j + 1], buf.webNodes[3 * j + 2], &buf.out3)
            let depth = ((z1 + buf.out3[2]) / 2 + 1) / 2
            buf.addLine(
                x1, y1, buf.out3[0], buf.out3[1],
                white: 0.42,
                alpha: (1 - dist / o.thr) * (0.3 + 0.55 * depth),
                width: max(0.6, o.lineW * rs)
            )
        }
    }
    for i in 0..<nodeN {
        buf.proj.apply(buf.webNodes[3 * i], buf.webNodes[3 * i + 1], buf.webNodes[3 * i + 2], &buf.out3)
        let depth = (buf.out3[2] + 1) / 2
        let pulse = 1 + 0.25 * sin(t * 1.4 + Double(i) * 2.7)
        buf.addDot(
            buf.out3[0], buf.out3[1], buf.out3[2],
            r: (o.nodeR + o.nodeRDepth * depth) * pulse * rs,
            white: 0.55 - 0.45 * depth
        )
    }
    // signals: bright packets running between randomly re-picked node pairs
    for s in 0..<o.signals {
        let seg = floor(t * 0.55 + Double(s) * 7.31)
        let aI = Int(OrbMath.hashD(seg, Double(s) * 3.1 + 1.7) * Double(nodeN))
        let bI = Int(OrbMath.hashD(seg, Double(s) * 5.7 + 4.2) * Double(nodeN))
        if aI == bI { continue }
        let f = OrbMath.frac(t * 0.55 + Double(s) * 7.31)
        let x = OrbMath.lerp(buf.webNodes[3 * aI], buf.webNodes[3 * bI], f)
        let y = OrbMath.lerp(buf.webNodes[3 * aI + 1], buf.webNodes[3 * bI + 1], f)
        let z = OrbMath.lerp(buf.webNodes[3 * aI + 2], buf.webNodes[3 * bI + 2], f)
        let l = max(1e-6, sqrt(x * x + y * y + z * z))
        buf.proj.apply(x / l, y / l, z / l, &buf.out3)
        let depth = (buf.out3[2] + 1) / 2
        buf.addDot(
            buf.out3[0], buf.out3[1], buf.out3[2],
            r: (o.nodeR * 1.5 + o.nodeRDepth * depth) * rs,
            white: 0.05,
            alpha: 0.5 + 0.5 * depth
        )
    }
    buf.finalize(rMin: o.rMin)
}

/// `weaving` — three strands plait pole-to-pole around the sphere; radial breathing trades their places.
func frameBraid(_ size: Double, _ t: Double, _ o: BraidOpts, _ buf: OrbFrameBuffer) {
    let cx = size / 2
    let cy = size / 2
    let R = (size / 2) * 0.76
    buf.proj.set(yaw: t * 0.4, tilt: 0.3, cx: cx, cy: cy, scale: 1)
    let rs = OrbMath.radiusScale(size, o.rsPow)
    for i in 0..<o.ghostN {
        OrbMath.fibDir(i, o.ghostN, &buf.out3)
        buf.proj.apply(buf.out3[0] * R, buf.out3[1] * R, buf.out3[2] * R, &buf.out3)
        let depth = (buf.out3[2] / R + 1) / 2
        buf.addDot(buf.out3[0], buf.out3[1], buf.out3[2], r: 0.8 * rs, white: 0.78, alpha: 0.1 + 0.22 * depth)
    }
    for s in 0..<3 {
        let phase = (Double(s) / 3) * 2 * Double.pi
        for i in 0..<o.strandN {
            // u walks pole to pole; the frac() drift slides the whole strand along
            let u = (OrbMath.frac(Double(i) / Double(o.strandN) + t * 0.045) * 2 - 1) * 0.96
            let surf = sqrt(max(0, 1 - u * u))
            let endFade = min(1, (1 - abs(u)) / 0.1)
            let a = u * Double.pi * o.turns + phase
            // radial breathing: strands trade places — the over/under of a plait
            let weave = 1 + 0.075 * sin(u * Double.pi * o.turns * 2 + phase * 2 + t * 0.8)
            let rr = surf * R * weave
            buf.proj.apply(cos(a) * rr, u * R * weave, sin(a) * rr, &buf.out3)
            let depth = (buf.out3[2] / R + 1) / 2
            buf.addDot(
                buf.out3[0], buf.out3[1], buf.out3[2],
                r: (o.rBase + o.rDepth * depth) * rs,
                white: 0.55 - 0.45 * depth,
                alpha: endFade * (0.45 + 0.55 * depth)
            )
        }
    }
    buf.finalize(rMin: o.rMin)
}

/// `composing` (ribbon) — an undulating sash of parallel strands rides a great
/// circle; the tuned preset freezes the 3D tumble, leaving the traveling
/// undulation on a fixed band. The same painter drives `breathing` (ring) via
/// the faceOn flag: a face-on circle whose RADIUS — not its out-of-plane
/// offset — undulates, so it reads as a ring slowly morphing.
func frameRibbon(_ size: Double, _ t: Double, _ o: RibbonOpts, _ buf: OrbFrameBuffer) {
    let cx = size / 2
    let cy = size / 2
    let R = (size / 2) * 0.78
    // spin scales the 3D tumble; spin=0 freezes the band's orientation
    let spin = o.spin
    let camTilt = 0.3
    buf.proj.set(yaw: t * 0.1 * spin, tilt: camTilt, cx: cx, cy: cy, scale: 1)
    let rs = OrbMath.radiusScale(size, o.rsPow)
    for i in 0..<o.ghostN {
        OrbMath.fibDir(i, o.ghostN, &buf.out3)
        buf.proj.apply(buf.out3[0] * R, buf.out3[1] * R, buf.out3[2] * R, &buf.out3)
        let depth = (buf.out3[2] / R + 1) / 2
        buf.addDot(buf.out3[0], buf.out3[1], buf.out3[2], r: 0.8 * rs, white: 0.78, alpha: 0.1 + 0.22 * depth)
    }
    // The band plane, precessing (frozen when spin=0). Face-on sets
    // ta = -camTilt so the projection's vertical squash term is 1 and the
    // band reads as a true circle rather than a tilted ellipse.
    let ya = t * 0.24 * spin
    let ta = o.faceOn ? -camTilt : 0.55 + 0.3 * sin(t * 0.18) * spin
    let ux = cos(ya)
    let uy = 0.0
    let uz = sin(ya)
    let vx = -uz * sin(ta)
    let vy = cos(ta)
    let vz = ux * sin(ta)
    // plane normal n = u × v
    let nx = uy * vz - uz * vy
    let ny = uz * vx - ux * vz
    let nz = ux * vy - uy * vx
    // Radial lobes swell past R, so pull the base radius in by (most of) the
    // wobble amplitude — the silhouette stays inside the frame.
    let wobAmp = 0.23 * o.wobMul
    let baseR = o.faceOn ? R / (1 + 0.85 * wobAmp) : R
    let lanes = max(1, Int((Double(o.lanes) * o.bandMul).rounded()))
    for w in 0..<lanes {
        let laneOff = (Double(w) - Double(lanes - 1) / 2.0) * 0.075
        let edge = abs(Double(w) - Double(lanes - 1) / 2.0) / max(1.0, Double(lanes - 1) / 2.0)
        for k in 0..<o.segs {
            let a = (Double(k) / Double(o.segs)) * 2 * Double.pi
            // the undulation: two traveling waves along the band
            let wob = (0.16 * sin(a * 3 - t * 1.7 + Double(w) * 0.22) + 0.07 * sin(a * 5 + t * 1.1)) * o.wobMul
            // Face-on modulates the in-plane RADIUS (lobes swell outward);
            // ribbon keeps the original out-of-plane sash wobble.
            let radial = o.faceOn ? 1 + wob : 1.0
            let off = o.faceOn ? laneOff : laneOff + wob
            let x = ux * cos(a) + vx * sin(a) + nx * off
            let y = uy * cos(a) + vy * sin(a) + ny * off
            let z = uz * cos(a) + vz * sin(a) + nz * off
            let l = sqrt(x * x + y * y + z * z)
            let rr = baseR * radial
            buf.proj.apply((x / l) * rr, (y / l) * rr, (z / l) * rr, &buf.out3)
            let depth = (buf.out3[2] / R + 1) / 2
            buf.addDot(
                buf.out3[0], buf.out3[1], buf.out3[2],
                r: (o.rBase + o.rDepth * depth) * (1 - 0.25 * edge) * rs,
                white: 0.52 - 0.44 * depth + 0.18 * edge,
                alpha: 0.4 + 0.6 * depth
            )
        }
    }
    buf.finalize(rMin: o.rMin)
}

// MARK: morph (shaping)

private func smoothE(_ x: Double) -> Double { x * x * (3 - 2 * x) }

/// A closed polygon path parameterised by arc length, precomputed once.
final class PolyPath {
    let vx: [Double]
    let vy: [Double]
    let segLen: [Double]
    let total: Double

    init(verts: [[Double]]) {
        vx = verts.map { $0[0] }
        vy = verts.map { $0[1] }
        var lens: [Double] = []
        var sum = 0.0
        lens.reserveCapacity(verts.count)
        for i in 0..<verts.count {
            let j = (i + 1) % verts.count
            let dx = vx[j] - vx[i]
            let dy = vy[j] - vy[i]
            let l = sqrt(dx * dx + dy * dy)
            lens.append(l)
            sum += l
        }
        segLen = lens
        total = sum
    }

    func point(_ f: Double, _ out: inout [Double]) {
        let V = vx.count
        var target = f * total
        var si = 0
        while target > segLen[si] && si < V - 1 {
            target -= segLen[si]
            si += 1
        }
        let j = (si + 1) % V
        let ff = segLen[si] > 0 ? min(1, target / segLen[si]) : 0
        out[0] = vx[si] + (vx[j] - vx[si]) * ff
        out[1] = vy[si] + (vy[j] - vy[si]) * ff
    }
}

private let morphTriangle = PolyPath(verts: [[0.0, -0.26], [0.24, 0.16], [-0.24, 0.16]])

// 5-vertex walk so the path STARTS at top-centre like the other shapes
private let morphSquare = PolyPath(verts: [[0, -0.2], [0.2, -0.2], [0.2, 0.2], [-0.2, 0.2], [-0.2, -0.2]])

private func morphShapePoint(_ k: Int, _ f: Double, _ out: inout [Double]) {
    switch k {
    case 0:
        // circle
        let a = -Double.pi / 2 + f * 2 * Double.pi
        out[0] = cos(a) * 0.24
        out[1] = sin(a) * 0.24
    case 1: morphTriangle.point(f, &out)
    default: morphSquare.point(f, &out)
    }
}

/// `shaping` — a dotted outline cycling circle → triangle → square → circle.
/// Every frame the engine blends the two neighbouring paths, then lays the
/// dots EVENLY along the blended outline — spacing stays uniform at every
/// instant of the morph. Plain circle fills only.
func frameMorph(_ size: Double, _ t: Double, _ o: MorphOpts, _ buf: OrbFrameBuffer) {
    let K = 3
    let HOLD = 1.4
    let MORPH = 0.9
    let SEG = HOLD + MORPH
    let tc = t.truncatingRemainder(dividingBy: SEG * Double(K))
    let k = Int(floor(tc / SEG))
    let local = tc - Double(k) * SEG
    let blend = local > HOLD ? smoothE((local - HOLD) / MORPH) : 0
    let sprd = o.spread
    // blend the two shape PATHS at blend, then measure the blended outline
    let M = 160
    for i in 0..<M {
        let f = Double(i) / Double(M)
        morphShapePoint(k, f, &buf.out3)
        let ax = buf.out3[0]
        let ay = buf.out3[1]
        morphShapePoint((k + 1) % K, f, &buf.out3)
        let bx = buf.out3[0]
        let by = buf.out3[1]
        buf.morphPts[2 * i] = (ax + (bx - ax) * blend) * sprd
        buf.morphPts[2 * i + 1] = (ay + (by - ay) * blend) * sprd
    }
    var total = 0.0
    for i in 0..<M {
        let j = (i + 1) % M
        let dx = buf.morphPts[2 * j] - buf.morphPts[2 * i]
        let dy = buf.morphPts[2 * j + 1] - buf.morphPts[2 * i + 1]
        let l = sqrt(dx * dx + dy * dy)
        buf.morphSegLen[i] = l
        total += l
    }
    // dot radius depends ONLY on rDot (the size knob); the count sets the gaps.
    // Formed shapes breathe a little (uniform pulse).
    let n = max(6, Int((34 * o.iconD).rounded()))
    let re = o.rDot * 1.35 * sprd
    let pulse = 1 + 0.02 * sin(local * 3.1)
    let c2 = size / 2
    var seg = 0
    var acc = 0.0
    for k2 in 0..<n {
        let target = (Double(k2) / Double(n)) * total
        while acc + buf.morphSegLen[seg] < target && seg < M - 1 {
            acc += buf.morphSegLen[seg]
            seg += 1
        }
        let j = (seg + 1) % M
        let ff = buf.morphSegLen[seg] > 0 ? min(1, (target - acc) / buf.morphSegLen[seg]) : 0
        let x = (buf.morphPts[2 * seg] + (buf.morphPts[2 * j] - buf.morphPts[2 * seg]) * ff) * pulse
        let y = (buf.morphPts[2 * seg + 1] + (buf.morphPts[2 * j + 1] - buf.morphPts[2 * seg + 1]) * ff) * pulse
        buf.addDot(
            c2 + x * size,
            c2 + y * size,
            0,
            r: max(0.35, re * size),
            white: 0.1
        )
    }
    buf.finalize(rMin: o.rMin)
}

// MARK: - Dispatch

/// Renders one frame of `resolved` at geometry time `t` into `buf`.
enum OrbEngine {
    static func render(size: Double, t: Double, resolved: ResolvedOrb, buf: OrbFrameBuffer) {
        buf.reset()
        switch resolved.opts {
        case .orbits(let o): frameOrbits(size, t, o, buf)
        case .globe(let o): frameGlobe(size, t, o, buf)
        case .rubik(let o): frameRubik(size, t, o, buf)
        case .wave(let o): frameWave(size, t, o, buf)
        case .web(let o): frameWeb(size, t, o, buf)
        case .braid(let o): frameBraid(size, t, o, buf)
        case .ribbon(let o): frameRibbon(size, t, o, buf)
        case .ring(let o): frameRibbon(size, t, o, buf)
        case .morph(let o): frameMorph(size, t, o, buf)
        }
    }
}
