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

    /// The old version of this test was `XCTAssertEqual(Aero.Radius.card, 20)`
    /// against a token declared as 20. It could not fail, and while it was
    /// passing every Home card was hardcoding a corner the token did not
    /// describe — a green assertion that certifies a number against itself is
    /// worse than no assertion, because it is quoted in reviews as proof.
    ///
    /// What is asserted instead is the RELATIONSHIP, which can genuinely break:
    /// the sheet's card is tighter than the app's standard card, the scale is
    /// monotonic, and the pill is not a corner anyone can round by accident.
    /// The call-site rule ("no Home glass surface may pass a literal corner,
    /// and both platforms must name the same number") lives in
    /// .github/scripts/check_home_type_parity.py, because a test in one
    /// language cannot see the other platform's source.
    func testRadiusScaleIsOrderedAndHomeCardsAreTighter() {
        XCTAssertLessThan(Aero.Radius.sm, Aero.Radius.md)
        // The sheet's card is the medium control radius, on both platforms
        // (GsRadius.homeCard = 16.dp = GsRadius.md) — named separately because
        // it is measured from the render, not inherited from the scale.
        XCTAssertEqual(Aero.Radius.homeCard, Aero.Radius.md)
        XCTAssertLessThan(Aero.Radius.homeCard, Aero.Radius.card)
        XCTAssertLessThan(Aero.Radius.card, Aero.Radius.sheet)
        XCTAssertLessThan(Aero.Radius.sheet, Aero.Radius.input)
        XCTAssertEqual(Aero.Radius.chip, 999)
    }

    /// Poppins is the product's one typeface on both platforms. Asserted here so
    /// the claim is checked against the running binary rather than against
    /// project.yml, which can say UIAppFonts while the bundle ships nothing.
    func testPoppinsIsRegistered() {
        XCTAssertTrue(Aero.productLoaded,
                      "Poppins did not resolve, so every Aero.responsive call is "
                      + "silently falling back to SF Pro")
        XCTAssertEqual(Aero.productName(.semibold), "Poppins-SemiBold")
        XCTAssertEqual(Aero.productName(.medium), "Poppins-Medium")
        XCTAssertEqual(Aero.productName(.regular), "Poppins-Regular")
        XCTAssertEqual(Aero.productName(.bold), "Poppins-Bold")
    }
}
