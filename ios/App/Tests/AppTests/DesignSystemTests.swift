import XCTest
@testable import GSApp

/// Smoke tests for the "Premium intelligent editorial" design tokens.
///
/// Build-repair note: this suite predates the first-ever successful Xcode
/// compile of the app target. It referenced `GSTheme.Spacing` / `GSTheme.Radius`,
/// a namespace that never existed (the legacy `GSTheme` bridge only carries
/// four accent/font symbols) — the tokens live on `Aero` — and it asserted a
/// card radius of 12 that the Step-1 token refresh superseded with 16
/// (DesignSystem.swift `Aero.Radius.card` is the source of truth).
/// The assertions now target the real tokens and current values.
final class DesignSystemTests: XCTestCase {

    func testSpacingTokensArePositive() {
        XCTAssertGreaterThan(Aero.Spacing.xs, 0)
        XCTAssertGreaterThan(Aero.Spacing.m, 0)
        XCTAssertGreaterThan(Aero.Spacing.xl, 0)
    }

    func testSpacingScaleIsOrdered() {
        XCTAssertLessThan(Aero.Spacing.xs, Aero.Spacing.s)
        XCTAssertLessThan(Aero.Spacing.s, Aero.Spacing.m)
        XCTAssertLessThan(Aero.Spacing.m, Aero.Spacing.l)
        XCTAssertLessThan(Aero.Spacing.l, Aero.Spacing.xl)
    }

    func testCardRadiusToken() {
        XCTAssertEqual(Aero.Radius.card, 16)
    }
}
