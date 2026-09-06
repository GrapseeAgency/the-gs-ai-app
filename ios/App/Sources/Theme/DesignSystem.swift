import SwiftUI

// MARK: - Color tokens (raw brand constants)

extension Color {
    /// Editorial paper — warm off-white page background (light mode).
    static let gsPaper = Color(red: 0.980, green: 0.976, blue: 0.965)

    /// Primary ink — near-black with a cool blue cast (light mode).
    static let gsInk = Color(red: 0.102, green: 0.114, blue: 0.137)

    /// Muted ink — secondary copy (light mode).
    static let gsInkMuted = Color(red: 0.412, green: 0.431, blue: 0.478)

    /// Copper — the single brand accent.
    static let gsAccent = Color(red: 0.706, green: 0.388, blue: 0.173)

    /// Deep navy-black app surface (dark mode — deep, not flat grey).
    static let gsDarkSurface = Color(red: 0.043, green: 0.055, blue: 0.075)

    /// Elevated surface for cards/sheets on dark.
    static let gsDarkElevated = Color(red: 0.102, green: 0.122, blue: 0.157)

    /// Warm off-white text (dark mode).
    static let gsDarkText = Color(red: 0.949, green: 0.941, blue: 0.922)
}

// MARK: - Design system

/// "Premium intelligent editorial" design tokens.
///
/// Layered surfaces, subtle tonal differences, strong dark mode,
/// editorial serif for display headlines, system sans everywhere else.
enum GSTheme {

    // MARK: Spacing

    enum Spacing {
        static let xs: CGFloat = 4
        static let s: CGFloat = 8
        static let m: CGFloat = 16
        static let l: CGFloat = 24
        static let xl: CGFloat = 32
    }

    // MARK: Corner radius

    enum Radius {
        /// Standard card / input bar radius (moderate, editorial).
        static let card: CGFloat = 12
    }

    // MARK: Elevation

    enum Shadow {
        static let color = Color.black.opacity(0.08)
        static let radius: CGFloat = 6
        static let x: CGFloat = 0
        static let y: CGFloat = 2
    }

    // MARK: Typography

    /// Editorial serif for big display headlines / branding moments.
    static func displayTitle() -> Font {
        .system(size: 34, weight: .semibold, design: .serif)
    }

    /// Section headings, card titles.
    static func headline() -> Font {
        .system(size: 20, weight: .semibold)
    }

    /// Default reading size.
    static func body() -> Font {
        .system(size: 16)
    }

    /// Supporting copy, labels, timestamps.
    static func caption() -> Font {
        .system(size: 13)
    }

    // MARK: Light / dark token sets

    /// Semantic tokens resolved per appearance.
    struct Palette {
        let background: Color
        let surface: Color
        let elevatedSurface: Color
        let text: Color
        let textMuted: Color
        let accent: Color

        static let light = Palette(
            background: .gsPaper,
            surface: .white,
            elevatedSurface: .white,
            text: .gsInk,
            textMuted: .gsInkMuted,
            accent: .gsAccent
        )

        static let dark = Palette(
            background: .gsDarkSurface,
            surface: .gsDarkElevated,
            elevatedSurface: .gsDarkElevated,
            text: .gsDarkText,
            textMuted: Color.white.opacity(0.55),
            accent: .gsAccent
        )
    }

    /// Resolves the correct token set for a given appearance.
    static func palette(dark: Bool) -> Palette {
        dark ? .dark : .light
    }
}

// MARK: - View helpers

extension View {
    /// Standard soft elevation used across editorial cards.
    func gsCardShadow() -> some View {
        shadow(
            color: GSTheme.Shadow.color,
            radius: GSTheme.Shadow.radius,
            x: GSTheme.Shadow.x,
            y: GSTheme.Shadow.y
        )
    }
}
