// Golden-vector parity (Phase 4 §17): the native Swift engine must reproduce
// the reference engine's exact dot lists at frozen timestamps. The vectors
// come from upstream's parity harness (thinking-orbs@0.3.1 — 9 states × 2
// sizes × 4 timestamps; dot count + the first-3/last-2 dots of the z-sorted
// draw order, 1e-4 tolerance = libm ulp headroom, far below a device pixel).
//
// The Kotlin engine in this repo passes the IDENTICAL vectors — this is the
// anti-drift contract between the two native ports and the upstream spec.

import XCTest
@testable import GSApp

final class OrbGoldenTests: XCTestCase {

    func testAllGoldenVectorsMatch() {
        for c in OrbGoldenData.cases {
            let resolved = OrbSpec.resolve(state: c.state, size: c.size)
            XCTAssertEqual(resolved.mode, c.mode, c.key + ": mode")
            let buf = OrbFrameBuffer()
            OrbEngine.render(size: Double(c.size.rawValue), t: c.t, resolved: resolved, buf: buf)
            XCTAssertEqual(buf.dotCount, c.dotCount, c.key + ": dotCount")
            XCTAssertEqual(buf.lineCount, c.lineCount, c.key + ": lineCount")
            // sample = first-3 + last-2 of the reference's z-sorted draw order
            let positions = [0, 1, 2, c.dotCount - 2, c.dotCount - 1]
            for (si, pos) in positions.enumerated() {
                let idx = buf.drawOrder[pos]
                for f in 0..<6 {
                    let expected = c.sample[si * 6 + f]
                    let actual: Double
                    switch f {
                    case 0: actual = buf.px[idx]
                    case 1: actual = buf.py[idx]
                    case 2: actual = buf.pz[idx]
                    case 3: actual = buf.pr[idx]
                    case 4: actual = buf.pWhite[idx]
                    default: actual = buf.pAlpha[idx]
                    }
                    XCTAssertEqual(
                        actual,
                        expected,
                        accuracy: 1e-4,
                        c.key + " dot#\(pos) field#\(f)"
                    )
                }
            }
        }
    }

    /// Reduced-motion contract: the static representative frame is exactly t = 0.6.
    func testStaticFrameConstantMatchesReference() {
        XCTAssertEqual(OrbSpec.staticT, 0.6, accuracy: 0.0)
    }

    /// The buffer must never grow unbounded: a long run reuses the same arrays.
    func testLongRunStaysAllocationStable() {
        let resolved = OrbSpec.resolve(state: .composing, size: .standard)
        let buf = OrbFrameBuffer()
        var t = 0.0
        for _ in 0..<600 {
            OrbEngine.render(size: 64, t: t, resolved: resolved, buf: buf)
            t += 1.0 / 60.0
        }
        XCTAssertLessThanOrEqual(buf.dotCount, buf.maxDots, "frame buffer overflow")
    }
}
