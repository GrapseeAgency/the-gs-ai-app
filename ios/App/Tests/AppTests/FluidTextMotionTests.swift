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
