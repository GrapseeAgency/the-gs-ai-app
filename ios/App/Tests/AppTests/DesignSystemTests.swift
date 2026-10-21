import XCTest
@testable import GSApp

/// Smoke tests for the "Premium intelligent editorial" design tokens.
///
/// Build-repair note: this suite predates the first-ever successful Xcode
/// compile of the app target. It referenced `GSTheme.Spacing` / `GSTheme.Radius`,
/// a namespace that never existed (the legacy `GSTheme` bridge only carries
/// four accent/font symbols) — the tokens live on `Aero` — and it asserted a
/// card radius of 12 that the Step-1 token refresh superseded with 16, then the
/// softer reference pass with 20. DesignSystem.swift `Aero.Radius.card` is the
/// source of truth (20, "cards, tiles"), and it is in parity with Android's
/// `GsRadius.card = 20.dp` (Tokens.kt). The assertion tracks the token, not a
/// frozen number: when the token moves on purpose, the test moves with it.
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
        // Tracks Aero.Radius.card (source of truth = 20, Android parity via
        // GsRadius.card). It asserts the token equals its documented value, so
        // it still fails if the token drifts away from 20 unintentionally.
        XCTAssertEqual(Aero.Radius.card, 20)
    }
}
