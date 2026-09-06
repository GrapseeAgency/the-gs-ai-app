import XCTest
@testable import GSApp

/// Smoke tests for the "Premium intelligent editorial" design tokens.
final class DesignSystemTests: XCTestCase {

    func testSpacingTokensArePositive() {
        XCTAssertGreaterThan(GSTheme.Spacing.xs, 0)
        XCTAssertGreaterThan(GSTheme.Spacing.m, 0)
        XCTAssertGreaterThan(GSTheme.Spacing.xl, 0)
    }

    func testSpacingScaleIsOrdered() {
        XCTAssertLessThan(GSTheme.Spacing.xs, GSTheme.Spacing.s)
        XCTAssertLessThan(GSTheme.Spacing.s, GSTheme.Spacing.m)
        XCTAssertLessThan(GSTheme.Spacing.m, GSTheme.Spacing.l)
        XCTAssertLessThan(GSTheme.Spacing.l, GSTheme.Spacing.xl)
    }

    func testCardRadiusToken() {
        XCTAssertEqual(GSTheme.Radius.card, 12)
    }
}
