import XCTest
import CoreGraphics
@testable import GSApp

final class ArgentOverlayTests: XCTestCase {
    func testOnlyExplicitTransientSurfacesExtendTheComponentLimit() {
        XCTAssertTrue(gsArgentComponentSize(CGSize(width: 36, height: 36)))
        XCTAssertFalse(gsArgentComponentSize(CGSize(width: 248, height: 540)))
        XCTAssertTrue(gsArgentComponentSize(CGSize(width: 248, height: 540), overlayPanel: true))
        XCTAssertTrue(gsArgentComponentSize(CGSize(width: 360, height: 780), overlayPanel: true))
        XCTAssertFalse(gsArgentComponentSize(CGSize(width: 780, height: 360), overlayPanel: true))
        XCTAssertFalse(gsArgentComponentSize(CGSize(width: 360, height: 1200), overlayPanel: true))
    }
}
