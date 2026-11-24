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
        XCTAssertGreaterThanOrEqual(band.opacity(at: 1), 0.57)
        XCTAssertEqual(band.opacity(at: 1.2), 1, accuracy: 0.001)
        motion.update("old new words", now: 1.2)
        XCTAssertTrue(motion.bands.isEmpty)
    }

    func testBurstAndUnicodeRangesStayBounded() {
        var motion = FluidTextMotion(initial: "")
        var text = String(repeating: "👨‍👩‍👧‍👦", count: 100)
        motion.update(text, now: 0)
        XCTAssertEqual(motion.bands.first?.start, 52)
        XCTAssertEqual(motion.bands.last?.end, 100)
        XCTAssertLessThanOrEqual(motion.bands.count, 4)
        for tick in 0..<30 {
            text += "x"
            motion.update(text, now: Double(tick) / 1000)
            XCTAssertLessThanOrEqual(motion.bands.count, 8)
        }
        motion.settle()
        XCTAssertTrue(motion.bands.isEmpty)
    }
}
