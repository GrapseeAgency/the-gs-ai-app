import XCTest
@testable import GSApp

final class FluidTextMotionTests: XCTestCase {
    func testInitialAndRewrittenTextAreSettled() {
        var motion = FluidTextMotion(initial: "already visible")
        motion.update("already visible", now: 0)
        XCTAssertTrue(motion.bands.isEmpty)
        motion.update("rewritten", now: 0.01)
        XCTAssertTrue(motion.bands.isEmpty)
        motion.update("rewritten!", now: 0.02, enabled: false)
        XCTAssertTrue(motion.bands.isEmpty)
    }

    func testAppendIsImmediateAndSettles() throws {
        var motion = FluidTextMotion(initial: "old ")
        motion.update("old new words", now: 1)
        let band = try XCTUnwrap(motion.bands.first)
        XCTAssertEqual(band.start, 4)
        XCTAssertEqual(band.end, 13)
        XCTAssertEqual(band.opacity(at: 1), 0.18, accuracy: 0.001)
        XCTAssertGreaterThan(band.opacity(at: 1.08), band.opacity(at: 1))
        XCTAssertEqual(band.opacity(at: 1.35), 1, accuracy: 0.001)
        motion.update("old new words", now: 1.35)
        XCTAssertTrue(motion.bands.isEmpty)
    }

    func testAllRealPacketPiecesDevelopImmediatelyWithContinuousPhase() {
        var motion = FluidTextMotion(initial: "")
        motion.update(String(repeating: "abc ", count: 200), now: 0.001)
        let bands = motion.bands
        XCTAssertGreaterThan(bands.count, 1)
        XCTAssertTrue(bands.allSatisfy { $0.born == 0.001 })
        XCTAssertTrue(bands.allSatisfy { $0.opacity(at: 0.017, position: 1) > 0.18 })
        for (a, b) in zip(bands, bands.dropFirst()) {
            XCTAssertEqual(a.phaseTo, b.phaseFrom, accuracy: 0.00001)
            XCTAssertEqual(a.opacity(at: 0.1, position: 1), b.opacity(at: 0.1), accuracy: 0.00001)
        }
        for pos in 0...100 {
            var previous = 0.0
            for tick in 0...1000 {
                let p = fluidLocalProgress(Double(tick) / 1000, position: Double(pos) / 100, travel: 0.48)
                XCTAssertGreaterThanOrEqual(p + 0.000001, previous)
                if (1...999).contains(tick) { XCTAssertTrue(p > 0 && p < 1) }
                previous = p
            }
        }
    }

    func testReusableGradientPlacementMatchesNativeInkCurve() {
        for (from, to) in [(0.0, 1.0), (1.0, 0.0), (0.42, 0.43), (0.2, 0.2)] {
            for tick in 0...100 {
                let progress = Double(tick) / 100
                let projection = fluidInkProjection(progress: progress, phaseFrom: from, phaseTo: to, travel: 0.48)
                for step in 0...100 {
                    let x = Double(step) / 100
                    let actual = projection.solid ?? fluidInkOpacity((x - projection.origin) / projection.scale)
                    let expected = fluidInkOpacity(fluidLocalProgress(progress, position: from + (to - from) * x, travel: 0.48))
                    XCTAssertEqual(actual, expected, accuracy: 0.0002)
                }
            }
        }
    }

    func testContinuousInkProjectionMatchesCurveInBothDirections() throws {
        for (first, last) in [(0.0, 1.0), (1.0, 0.0), (0.3, 0.42)] {
            for tick in 0...100 {
                let progress = Double(tick) / 100
                let stops = fluidInkStops(progress: progress, phaseFrom: first, phaseTo: last, travel: 0.48)
                XCTAssertEqual(stops.first?.position, 0)
                XCTAssertEqual(stops.last?.position, 1)
                for (a, b) in zip(stops, stops.dropFirst()) {
                    XCTAssertLessThan(a.position, b.position)
                }
                for x in 0...100 {
                    let f = Double(x) / 100
                    let (a, b) = try XCTUnwrap(zip(stops, stops.dropFirst()).first { $0.1.position >= f })
                    let alpha = a.opacity + (b.opacity - a.opacity) * (f - a.position) / (b.position - a.position)
                    let phase = first + (last - first) * f
                    XCTAssertEqual(alpha, fluidInkOpacity(fluidLocalProgress(progress, position: phase, travel: 0.48)), accuracy: 0.008)
                }
            }
        }
    }

    func testOpticalFrontMovesAcrossRealInkAndSettlesTogether() {
        let band = FluidTextBand(start: 0, end: 12, born: 0, duration: 0.2)
        for tick in 0...200 {
            let now = Double(tick) / 1000
            let first = band.opacity(at: now, position: 0)
            let last = band.opacity(at: now, position: 1)
            XCTAssertGreaterThanOrEqual(first + 0.000001, last)
            XCTAssertTrue((0.179...1.001).contains(first))
            XCTAssertTrue((0.179...1.001).contains(last))
            if tick > 0 { XCTAssertGreaterThanOrEqual(first + 0.0001, band.opacity(at: now - 0.001)) }
        }
        XCTAssertGreaterThan(band.opacity(at: 0.09, position: 0) - band.opacity(at: 0.09, position: 1), 0.2)
        XCTAssertEqual(band.opacity(at: 0.2, position: 1), 1, accuracy: 0.001)
    }

    func testMarkdownClosingPreservesExistingInkPhase() throws {
        var motion = FluidTextMotion(initial: "")
        motion.update("**hello", now: 0)
        let first = try XCTUnwrap(motion.bands.first)
        motion.update("hello world", now: 0.05, sourceAppend: true)
        let preserved = try XCTUnwrap(motion.bands.first)
        let tail = try XCTUnwrap(motion.bands.last)
        XCTAssertEqual(preserved.start, 0)
        XCTAssertEqual(preserved.end, 5)
        XCTAssertEqual(preserved.born, first.born)
        XCTAssertEqual(preserved.opacity(at: 0.05), first.opacity(at: 0.05, position: 2.0 / 7), accuracy: 0.001)
        XCTAssertEqual(tail.start, 5)
        XCTAssertEqual(tail.end, 11)
        XCTAssertEqual(tail.born, 0.05)
        motion.update("replacement", now: 0.055)
        XCTAssertTrue(motion.bands.isEmpty)
    }

    func testAmbiguousRenderedRewriteDoesNotReplay() {
        var motion = FluidTextMotion(initial: "**word")
        motion.update("word word", now: 0, sourceAppend: true)
        XCTAssertTrue(motion.bands.isEmpty)
        motion.update("word word!", now: 0.01)
        XCTAssertEqual(motion.bands.first?.start, 9)
    }

    func testConsecutivePacketsJoinWithoutAccumulatingVisualBacklog() throws {
        var motion = FluidTextMotion(initial: "")
        var text = ""
        var previousBirth = -Double.infinity
        for tick in 0..<60 {
            let now = Double(tick) * 0.005
            text += "abcdefghij"
            motion.update(text, now: now)
            let newest = try XCTUnwrap(motion.bands.last)
            XCTAssertEqual(newest.born, now)
            XCTAssertGreaterThanOrEqual(newest.born, previousBirth)
            XCTAssertEqual(newest.end, text.count)
            XCTAssertGreaterThanOrEqual(newest.opacity(at: now, position: 1), 0.179)
            previousBirth = newest.born
        }
        motion.update(text, now: 0.8)
        XCTAssertTrue(motion.bands.isEmpty)
    }

    func testCadenceDoesNotRestartOldBands() throws {
        var motion = FluidTextMotion(initial: "")
        motion.update("first word ", now: 0)
        let first = try XCTUnwrap(motion.bands.first)
        motion.update("first word next word ", now: 0.017)
        XCTAssertEqual(motion.bands.first, first)
        XCTAssertGreaterThanOrEqual(try XCTUnwrap(motion.bands.last).start, first.end)
        motion.update("first word next word final", now: 0.117)
        let latest = try XCTUnwrap(motion.bands.last)
        XCTAssertTrue((0.185...0.265).contains(latest.duration))
        motion.update("first word next word final", now: 0.6)
        XCTAssertTrue(motion.bands.isEmpty)
    }

    func testBurstAndUnicodeRangesNeverDropUnsettledInk() {
        var motion = FluidTextMotion(initial: "")
        var text = String(repeating: "👨‍👩‍👧‍👦", count: 100)
        motion.update(text, now: 0)
        XCTAssertEqual(motion.bands.first?.start, 0)
        XCTAssertEqual(motion.bands.last?.end, 100)
        XCTAssertLessThanOrEqual(motion.bands.count, 4)
        let initial = motion.bands.first
        for tick in 0..<30 {
            text += "x"
            motion.update(text, now: Double(tick) / 1000)
            XCTAssertEqual(motion.bands.first, initial)
            XCTAssertEqual(motion.bands.last?.end, text.count)
            for (a, b) in zip(motion.bands, motion.bands.dropFirst()) {
                XCTAssertEqual(a.end, b.start)
            }
        }
        motion.update(text, now: 0.6)
        XCTAssertTrue(motion.bands.isEmpty)
        motion.settle()
        XCTAssertTrue(motion.bands.isEmpty)
        var cluster = FluidTextMotion(initial: "")
        cluster.update("e", now: 0)
        cluster.update("e\u0301", now: 0.01)
        XCTAssertTrue(cluster.bands.isEmpty)
    }
}
