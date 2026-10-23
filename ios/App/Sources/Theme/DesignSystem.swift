import SwiftUI
import UIKit

/**
 * WARM CONSUMER — single source of design truth for iOS.
 * "Peach / orange / cream": soft consumer surfaces, tactile cards, and a warm AI action language.
 * Mirrors android/.../ui/theme/ (Color.kt + Tokens.kt + Type.kt + Motion.kt).
 *
 * RULES
 *  - No view may paint a raw colour literal. Everything resolves through the
 *    semantic tokens below, which are dynamic (light + dark + high-contrast).
 *  - Appearance follows the app's own Settings ("System"/"Light"/"Dark")
 *    via .preferredColorScheme at the app root; every dynamic token follows.
 *  - Reduce motion / reduce animations degrade springs, press scale and the
 *    decorative loops; font scale multiplies every Aero type role.
 *  - Warm reference identity: light mode uses peach / cream surfaces and
 *    orange actions; dark mode shifts to espresso / terracotta surfaces with
 *    candlelit peach actions. Semantic status hues remain reserved for status.
 */

// MARK: - Accessibility + font-scale flag store (plain class: read from
// UIColor dynamic-provider closures and non-isolated contexts)

final class GSAccessibilityFlags {
    static let shared = GSAccessibilityFlags()
    var highContrast = false
    var reduceMotion = false
    var reduceAnimations = false
    var screenReaderHints = false
    var fontScale: CGFloat = 1
}

enum Aero {

    // MARK: Palette plumbing (dynamic — light / dark / high-contrast)

    static func dynamic(light: UIColor, dark: UIColor,
                        lightHC: UIColor? = nil, darkHC: UIColor? = nil) -> Color {
        Color(UIColor { traits in
            let flags = GSAccessibilityFlags.shared
            let hc = flags.highContrast
            let isDark = traits.userInterfaceStyle == .dark
            switch (isDark, hc) {
            case (false, true):  return lightHC ?? light
            case (true, true):   return darkHC ?? dark
            case (false, false): return light
            case (true, false):  return dark
            }
        })
    }

    // ---- Background hierarchy ------------------------------------------------
    static let background = dynamic(          // window canvas
        light: UIColor(red: 1.0, green: 0.957, blue: 0.937, alpha: 1),                  // FFF4EF
        dark: UIColor(red: 0.102, green: 0.067, blue: 0.063, alpha: 1)) // 1A1110
    static let secondaryBackground = dynamic( // grouped / under-page canvas
        light: UIColor(red: 1.0, green: 0.910, blue: 0.871, alpha: 1),   // FFE8DE
        dark: UIColor(red: 0.165, green: 0.114, blue: 0.102, alpha: 1)) // 2A1D1A
    static let surface = dynamic(             // standard content surface
        // Warm milk, matched to the sheet's cards (#F5EDE6); pure white read
        // ten points brighter than the reference render.
        light: UIColor(red: 0.961, green: 0.929, blue: 0.902, alpha: 1),
        dark: UIColor(red: 0.133, green: 0.090, blue: 0.086, alpha: 1)) // 221716
    /// The translucent glass layer — Android's `glassSurface`, same two numbers.
    ///
    /// Not the same thing as `surface`: that is an opaque panel, and this is a
    /// 45% layer over the bloom whose colour the sheet's own cards measure as
    /// #F2EAE3 exactly. Both platforms' glass reads from here, so a pill or a
    /// card cannot land 3 L brighter on one platform than the other (b10).
    static let glassSurface = dynamic(
        light: UIColor(red: 0.949, green: 0.918, blue: 0.890, alpha: 1),   // F2EAE3
        dark: UIColor(red: 0.165, green: 0.110, blue: 0.098, alpha: 1))    // 2A1C19
    static let elevatedSurface = dynamic(     // above-surface content
        light: .white,
        dark: UIColor(red: 0.208, green: 0.133, blue: 0.118, alpha: 1)) // 35221E
    static let raisedSurface = dynamic(       // raised controls: pills, tiles
        light: UIColor(red: 1.0, green: 0.910, blue: 0.871, alpha: 1),   // FFE8DE
        dark: UIColor(red: 0.169, green: 0.114, blue: 0.102, alpha: 1)) // 2B1D1A
    static let inputSurface = dynamic(        // text fields, composer field
        light: UIColor(red: 1.0, green: 0.910, blue: 0.871, alpha: 1),   // FFE8DE
        dark: UIColor(red: 0.165, green: 0.114, blue: 0.102, alpha: 1)) // 2A1D1A
    static let sheetSurface = dynamic(        // bottom sheets
        light: UIColor(red: 1.0, green: 0.984, blue: 0.973, alpha: 1),
        dark: UIColor(red: 0.133, green: 0.090, blue: 0.086, alpha: 1)) // 221716
    static let dialogSurface = dynamic(       // dialogs / confirmations
        light: .white,
        dark: UIColor(red: 0.208, green: 0.133, blue: 0.118, alpha: 1)) // 35221E
    static let navSurface = dynamic(          // drawer / navigation chrome
        light: .white,
        dark: UIColor(red: 0.102, green: 0.067, blue: 0.063, alpha: 1)) // 1A1110

    // ---- Text hierarchy --------------------------------------------------------
    static let text = dynamic(
        light: UIColor(red: 0.169, green: 0.102, blue: 0.090, alpha: 1),               // 2B1A17
        dark: UIColor(red: 1.0, green: 0.957, blue: 0.937, alpha: 1), // FFF4EF
        lightHC: .black,
        darkHC: .white)
    static let textSecondary = dynamic(
        light: UIColor(red: 0.392, green: 0.408, blue: 0.435, alpha: 1),   // 64686F
        dark: UIColor(red: 0.780, green: 0.631, blue: 0.592, alpha: 1), // C7A197
        lightHC: UIColor(red: 0.278, green: 0.298, blue: 0.329, alpha: 1), // 474C54
        darkHC: UIColor(red: 0.780, green: 0.800, blue: 0.827, alpha: 1))  // C7CCD3
    static let textTertiary = dynamic(
        light: UIColor(red: 0.522, green: 0.533, blue: 0.561, alpha: 1),   // 85888F
        dark: UIColor(red: 0.659, green: 0.518, blue: 0.471, alpha: 1)) // A88478
    static let textDisabled = dynamic(
        light: UIColor(red: 0.769, green: 0.780, blue: 0.800, alpha: 1),   // C4C7CC
        dark: UIColor(red: 0.447, green: 0.318, blue: 0.286, alpha: 1)) // 725149
    static let textPlaceholder = dynamic(
        light: UIColor(red: 0.620, green: 0.639, blue: 0.671, alpha: 1),   // 9EA3AB
        dark: UIColor(red: 0.659, green: 0.518, blue: 0.471, alpha: 1)) // A88478
    static let textInverted = dynamic(        // text on accent / dark imagery
        light: .white,
        dark: UIColor(red: 0.102, green: 0.067, blue: 0.063, alpha: 1)) // 1A1110
    static let link = dynamic(
        light: UIColor(red: 0.953, green: 0.435, blue: 0.173, alpha: 1),               // F36F2C
        dark: UIColor(red: 1.0, green: 0.682, blue: 0.451, alpha: 1)) // FFAE73

    // ---- Structural --------------------------------------------------------------
    static let outline = dynamic(
        light: UIColor(red: 0.941, green: 0.824, blue: 0.776, alpha: 1),   // F0D2C6
        dark: UIColor(red: 0.290, green: 0.169, blue: 0.145, alpha: 1), // 4A2B25
        lightHC: UIColor(red: 0.749, green: 0.749, blue: 0.761, alpha: 1), // BFBFC2
        darkHC: UIColor(red: 0.337, green: 0.353, blue: 0.384, alpha: 1))  // 565A62
    static let outlineStrong = dynamic(
        light: UIColor(red: 0.835, green: 0.835, blue: 0.847, alpha: 1),   // D5D5D8
        dark: UIColor(red: 0.412, green: 0.251, blue: 0.216, alpha: 1), // 694037
        lightHC: UIColor(red: 0.588, green: 0.588, blue: 0.600, alpha: 1), // 969699
        darkHC: UIColor(red: 0.431, green: 0.451, blue: 0.490, alpha: 1))  // 6E737D
    static let divider = dynamic(
        light: UIColor(red: 0.933, green: 0.933, blue: 0.941, alpha: 1),   // EEEEEF
        dark: UIColor(red: 0.290, green: 0.169, blue: 0.145, alpha: 1)) // 4A2B25
    static let selected = dynamic(
        light: UIColor(red: 0.953, green: 0.435, blue: 0.173, alpha: 1),               // F36F2C
        dark: UIColor(red: 1.0, green: 0.682, blue: 0.451, alpha: 1)) // FFAE73
    static let pressed = dynamic(             // pressed/hover overlay
        light: UIColor(red: 0, green: 0, blue: 0, alpha: 0.06),
        dark: UIColor(red: 1, green: 1, blue: 1, alpha: 0.08))
    static let focus = dynamic(
        light: UIColor(red: 0, green: 0, blue: 0, alpha: 1),
        dark: UIColor(red: 1.0, green: 0.682, blue: 0.451, alpha: 1))
    static let scrim = dynamic(
        light: UIColor(red: 0, green: 0, blue: 0, alpha: 0.5),
        dark: UIColor(red: 0, green: 0, blue: 0, alpha: 0.6))

    // ---- Semantic ------------------------------------------------------------------
    /// Warm peach/orange accent shared by the reference-inspired product chrome.
    static let accent = dynamic(
        light: UIColor(red: 0.953, green: 0.435, blue: 0.173, alpha: 1),               // F36F2C
        dark: UIColor(red: 1.0, green: 0.682, blue: 0.451, alpha: 1)) // FFAE73
    static let accentStrong = dynamic(
        light: UIColor(red: 0.953, green: 0.435, blue: 0.173, alpha: 1), // F36F2C
        dark: UIColor(red: 1.0, green: 0.757, blue: 0.561, alpha: 1))
    static let accentSoft = dynamic(
        light: UIColor(red: 1.0, green: 0.569, blue: 0.259, alpha: 0.14),
        dark: UIColor(red: 1.0, green: 0.682, blue: 0.451, alpha: 0.20))
    static let onAccent = dynamic(
        light: .white,
        dark: UIColor(red: 0.039, green: 0.039, blue: 0.039, alpha: 1))    // 0A0A0A
    static let success = dynamic(
        light: UIColor(red: 0.180, green: 0.490, blue: 0.325, alpha: 1),   // 2E7D53
        dark: UIColor(red: 0.298, green: 0.765, blue: 0.541, alpha: 1))    // 4CC38A
    static let successSoft = dynamic(
        light: UIColor(red: 0.180, green: 0.490, blue: 0.325, alpha: 0.10),
        dark: UIColor(red: 0.298, green: 0.765, blue: 0.541, alpha: 0.14))
    static let warning = dynamic(
        light: UIColor(red: 0.604, green: 0.420, blue: 0.071, alpha: 1),   // 9A6B12
        dark: UIColor(red: 0.910, green: 0.706, blue: 0.353, alpha: 1))    // E8B45A
    static let warningSoft = dynamic(
        light: UIColor(red: 0.604, green: 0.420, blue: 0.071, alpha: 0.10),
        dark: UIColor(red: 0.910, green: 0.706, blue: 0.353, alpha: 0.14))
    static let danger = dynamic(
        light: UIColor(red: 0.839, green: 0.271, blue: 0.271, alpha: 1),   // D64545
        dark: UIColor(red: 0.898, green: 0.282, blue: 0.302, alpha: 1))    // E5484D
    static let dangerSoft = dynamic(
        light: UIColor(red: 0.839, green: 0.271, blue: 0.271, alpha: 0.10),
        dark: UIColor(red: 0.898, green: 0.282, blue: 0.302, alpha: 0.14))
    static let onError = dynamic(light: .white, dark: .white)
    static let info = dynamic(
        light: UIColor(red: 0.180, green: 0.490, blue: 0.820, alpha: 1),   // 2E7DD1
        dark: UIColor(red: 0.424, green: 0.722, blue: 1.000, alpha: 1))    // 6CB8FF
    static let infoSoft = dynamic(
        light: UIColor(red: 0.180, green: 0.490, blue: 0.820, alpha: 0.10),
        dark: UIColor(red: 0.424, green: 0.722, blue: 1.000, alpha: 0.14))

    // ---- AI-specific -----------------------------------------------------------------
    static let aiActive = accent              // model alive: streaming dot
    static let streaming = dynamic(           // neutral — the life-sign carries no hue
        light: UIColor(red: 0.561, green: 0.443, blue: 0.412, alpha: 1),   // 8F7169
        dark: UIColor(red: 0.639, green: 0.659, blue: 0.686, alpha: 1))    // A3A8AF
    static let toolExecution = streaming      // neutral
    static let research = streaming           // neutral
    static let voice = accent
    static let generation = streaming         // neutral

    // ---- Code ---------------------------------------------------------------------------
    static let codeSurface = dynamic(
        light: UIColor(red: 0.945, green: 0.945, blue: 0.933, alpha: 1),   // F1F1EE
        dark: UIColor(red: 0.051, green: 0.067, blue: 0.090, alpha: 1))    // 0D1117
    static let codeText = dynamic(
        light: UIColor(red: 0.141, green: 0.161, blue: 0.184, alpha: 1),   // 24292F
        dark: UIColor(red: 0.835, green: 0.855, blue: 0.886, alpha: 1))    // D5DAE2
    static let codeKeyword = dynamic(
        light: UIColor(red: 0.424, green: 0.247, blue: 0.878, alpha: 1),   // 6C3FE0
        dark: UIColor(red: 0.780, green: 0.573, blue: 0.918, alpha: 1))    // C792EA
    static let codeString = dynamic(
        light: UIColor(red: 0.180, green: 0.490, blue: 0.196, alpha: 1),   // 2E7D32
        dark: UIColor(red: 0.765, green: 0.910, blue: 0.553, alpha: 1))    // C3E88D
    static let codeComment = dynamic(
        light: UIColor(red: 0.420, green: 0.486, blue: 0.549, alpha: 1),   // 6B7C8C
        dark: UIColor(red: 0.494, green: 0.549, blue: 0.600, alpha: 1))    // 7E8C99
    static let codeNumber = dynamic(
        light: UIColor(red: 0.847, green: 0.263, blue: 0.082, alpha: 1),   // D84315
        dark: UIColor(red: 0.969, green: 0.549, blue: 0.424, alpha: 1))    // F78C6C

    // ---- Life-sign gradient (AI-active moments ONLY).
    static let aurora: [Color] = [
        Color(red: 1.0, green: 0.682, blue: 0.451),
        Color(red: 1.0, green: 0.839, blue: 0.690)
    ]

    // ---- Legacy raw-value aliases (DEPRECATED bridge for un-migrated call sites) -----
    // Screens must move to the semantic roles above. The aliases RESOLVE TO THE
    // SEMANTIC VALUES (not the old raw values) so that un-migrated screens stay
    // visually uniform with migrated components in both appearances.
    static let accentDeep = accent                                            // legacy teal step → semantic accent
    static let textMuted = textSecondary
    static let raised = raisedSurface
    static let container = inputSurface
    static let containerHigh = dynamic(
        light: UIColor(red: 0.878, green: 0.878, blue: 0.886, alpha: 1),       // E0E0E2 — one step
        dark: UIColor(red: 0.125, green: 0.133, blue: 0.145, alpha: 1))        // 202225   stronger container

    // MARK: Typography — serif display voice, sans interface.
    // Dynamic Type is the scaling mechanism, and the Settings › font-scale
    // slider multiplies every Aero type role (GSAccessibilityFlags.fontScale,
    // propagated by SettingsStore.propagateFlags).
    //
    // Implementation note (iOS build repair): the previously written
    // `Font.system(size:weight:design:relativeTo:)` does not exist in
    // SwiftUI — no SDK ever shipped it (the real Xcode CI gate proved it).
    // The exact same contract is assembled here from documented UIKit +
    // SwiftUI APIs only:
    //   1. `UIFontMetrics(forTextStyle:)` applies Apple's actual per-role
    //      Dynamic Type scaling curve for the current content size category.
    //      At the default category it is the identity transform, so every
    //      spec point size below renders exactly as designed; as the
    //      reader's Dynamic Type setting grows or shrinks, the resolved
    //      size follows the role's real curve.
    //   2. `GSAccessibilityFlags.shared.fontScale` (Settings › font scale)
    //      multiplies the role-scaled result.
    //   3. `Font.system(size:weight:design:)` renders the resolved size with
    //      the exact weight and design (serif display, sans interface,
    //      monospaced code).
    // Signatures are unchanged — every call site compiles as before.

    /// SwiftUI text style → UIKit text style for the metrics engine.
    private static func uiKitStyle(_ style: Font.TextStyle) -> UIFont.TextStyle {
        switch style {
        case .largeTitle: return .largeTitle
        case .title: return .title1
        case .title2: return .title2
        case .title3: return .title3
        case .headline: return .headline
        case .body: return .body
        case .callout: return .callout
        case .subheadline: return .subheadline
        case .footnote: return .footnote
        case .caption: return .caption1
        case .caption2: return .caption2
        @unknown default: return .body
        }
    }

    /// One metrics engine per role, built once. Token fonts are constructed
    /// inside view bodies on every render pass, so the cache keeps that
    /// allocation-free; `scaledValue` itself is pure arithmetic.
    private static let metrics: [UIFont.TextStyle: UIFontMetrics] = [
        .largeTitle: UIFontMetrics(forTextStyle: .largeTitle),
        .title1: UIFontMetrics(forTextStyle: .title1),
        .title2: UIFontMetrics(forTextStyle: .title2),
        .title3: UIFontMetrics(forTextStyle: .title3),
        .headline: UIFontMetrics(forTextStyle: .headline),
        .body: UIFontMetrics(forTextStyle: .body),
        .callout: UIFontMetrics(forTextStyle: .callout),
        .subheadline: UIFontMetrics(forTextStyle: .subheadline),
        .footnote: UIFontMetrics(forTextStyle: .footnote),
        .caption1: UIFontMetrics(forTextStyle: .caption1),
        .caption2: UIFontMetrics(forTextStyle: .caption2)
    ]

    /// The product's one typeface, resolved once per process.
    ///
    /// Android sets `fontFamily = GsFonts.Poppins` on all sixteen roles in
    /// ui/theme/Type.kt ("One family (Poppins) across the whole product"). iOS
    /// had no UIAppFonts entry at all until this change, so every word on this
    /// screen rendered in SF Pro while the reference — and Android — set it in a
    /// geometric sans, and mandate b10 ("a surface reads identically on both
    /// platforms") was false for typography specifically.
    ///
    /// The flag exists because `Font.custom` falls back to the system font
    /// *silently* when a name does not resolve: the source would say Poppins,
    /// the screen would say SF Pro, and nothing would fail. HomeRenderTests
    /// asserts this is true, so a bundle that lost its fonts is a red test
    /// rather than a design regression someone has to notice in a screenshot.
    static let productLoaded: Bool = UIFont(name: "InterTight-SemiBold", size: 12) != nil

    /// SwiftUI weight → the bundled Poppins face. Four weights ship (400/500/600/
    /// 700); the remaining cases snap to the nearest one, exactly as Android's
    /// font resource does, which is why `.medium` and `.semibold` are distinct.
    static func productName(_ weight: Font.Weight) -> String {
        switch weight {
        case .ultraLight, .thin, .light, .regular: return "InterTight-Regular"
        case .medium:                              return "InterTight-Medium"
        case .semibold:                            return "InterTight-SemiBold"
        default:                                   return "InterTight-Bold"
        }
    }

    /// The real "custom point size that scales with Dynamic Type": exact
    /// spec size at the default category, the role's own Dynamic Type curve
    /// beyond it, then the Settings font-scale multiplier on top.
    private static func scaledFont(
        _ size: CGFloat,
        _ weight: Font.Weight,
        _ design: Font.Design,
        relativeTo style: Font.TextStyle
    ) -> Font {
        let engine = metrics[uiKitStyle(style)] ?? UIFontMetrics(forTextStyle: uiKitStyle(style))
        let dynamic = engine.scaledValue(for: size)
        let resolved = dynamic * GSAccessibilityFlags.shared.fontScale
        // `.custom(_:size:)` rather than `.custom(_:size:relativeTo:)`: the
        // Dynamic Type curve has already been applied above by UIFontMetrics, and
        // passing the style again would scale the scaled value twice.
        //
        // A designed fallback (monospaced code, serif quotes) keeps the system
        // face — Poppins has neither, and Android's per-role fontFamily override
        // leaves those two alone for the same reason.
        if design == .default, productLoaded {
            return .custom(productName(weight), size: resolved)
        }
        return .system(size: resolved, weight: weight, design: design)
    }

    static func displayTitle() -> Font { scaledFont(34, .semibold, .default, relativeTo: .largeTitle) }
    /// The length counterpart to `scaledFont`. A layout constant that exists to
    /// contain text — a pill row height, a bento row, an artwork frame — must
    /// grow on the SAME Dynamic Type curve as that text and by the same Settings
    /// font-scale multiplier. A literal `.frame(height:)` stays pinned while
    /// scaled type grows past it, which is how a page can honour Dynamic Type
    /// in its fonts and still clip.
    /// Growth cap for chrome geometry: 1.6x the base, whatever the curve says.
    ///
    /// A container that holds text has to grow with the text or it clips — that
    /// is why this function exists at all. But a WHOLE PAGE of such containers
    /// grows without bound, and the page is not only containers: the horizontal
    /// inset, the bento gutter and the dock clearance are fixed points. At
    /// Larger Accessibility Sizes the `.body` curve is ~3.1x, which turns the
    /// 214pt bento row into ~700pt, pushes everything below the fold and lets the
    /// floating dock sit on top of the last card — a layout that honours Dynamic
    /// Type in its fonts and is unusable in its page.
    ///
    /// 1.6x is chosen so that chrome still outgrows the *body* copy it contains
    /// at the top of the range (body's own curve is what the text follows), while
    /// the page stays recognisably the designed page. Capping here rather than at
    /// each call site means no new scaled constant can forget the rule.
    static let chromeGrowthCap: CGFloat = 1.6

    static func scaledLength(_ base: CGFloat, relativeTo style: Font.TextStyle = .body) -> CGFloat {
        let engine = metrics[uiKitStyle(style)] ?? UIFontMetrics(forTextStyle: uiKitStyle(style))
        let scaled = engine.scaledValue(for: base) * GSAccessibilityFlags.shared.fontScale
        return min(scaled, base * chromeGrowthCap)
    }

    static func display() -> Font { scaledFont(28, .semibold, .default, relativeTo: .title) }
    static func headline() -> Font { scaledFont(22, .semibold, .default, relativeTo: .title2) }
    static func title() -> Font { scaledFont(17, .semibold, .default, relativeTo: .headline) }
    static func body() -> Font { scaledFont(15, .regular, .default, relativeTo: .body) }
    static func bodyMedium() -> Font { scaledFont(14, .regular, .default, relativeTo: .body) }
    static func caption() -> Font { scaledFont(13, .regular, .default, relativeTo: .footnote) }
    static func label() -> Font { scaledFont(12, .medium, .default, relativeTo: .caption) }
    static func metadata() -> Font { scaledFont(11, .medium, .default, relativeTo: .caption2) }
    static func button() -> Font { scaledFont(14, .medium, .default, relativeTo: .callout) }
    static func code() -> Font { scaledFont(13, .regular, .monospaced, relativeTo: .footnote) }
    static func codeBlock() -> Font { scaledFont(14, .regular, .monospaced, relativeTo: .body) }

    /// Dynamic-Type-responsive system font at a custom point size — identical
    /// appearance at the default type category, scaling with the reader's
    /// Dynamic Type setting and the Settings font-scale multiplier exactly
    /// like the token fonts above. Map the point size to the closest semantic
    /// style (11→.caption2, 12→.caption, 13→.footnote, 14/15→.subheadline,
    /// 16→.callout, 17→.body, 22→.title2) so scaling behaviour matches the role.
    static func responsive(
        _ size: CGFloat,
        _ weight: Font.Weight = .regular,
        relativeTo style: Font.TextStyle = .body,
        design: Font.Design = .default
    ) -> Font {
        scaledFont(size, weight, design, relativeTo: style)
    }

    // MARK: Motion — springs over eases; reduced-motion degrades to calm.

    private static var motionReduced: Bool {
        let flags = GSAccessibilityFlags.shared
        return flags.reduceMotion || flags.reduceAnimations
    }

    static var spring: Animation {
        motionReduced ? .linear(duration: 0.15) : Animation.spring(response: 0.35, dampingFraction: 0.8)
    }
    static var snappy: Animation {
        motionReduced ? .linear(duration: 0.12) : Animation.spring(response: 0.28, dampingFraction: 0.75)
    }
    static var gentle: Animation {
        motionReduced ? .linear(duration: 0.2) : Animation.spring(response: 0.5, dampingFraction: 0.9)
    }
    /// perf-4 (technique B): the chat screen's ONE curve. Deployment
    /// target is iOS 16, so the .smooth API is unavailable; .easeInOut(0.2)
    /// is the closest single curve. Reduced motion flattens it.
    static var chatCurve: Animation { motionReduced ? .linear(duration: 0.2) : .easeInOut(duration: 0.2) }

    static var pressScale: CGFloat { motionReduced ? 1.0 : 0.97 }
    static var staggerStep: Double { motionReduced ? 0 : 0.03 }

    static func stagger(_ index: Int) -> Double { Double(index) * staggerStep }

    /// Motion bridge (Phase 2): a dozen call sites read
    /// `Aero.motion(Aero.spring)` — the pre-Step-1 helper that flattened
    /// one-shot transitions under reduce motion by returning nil. Its duty
    /// now lives inside the springs themselves (spring/snappy/gentle
    /// self-degrade above), so this is an identity pass-through. Without it
    /// the call sites reference a symbol that no longer exists.
    static func motion(_ animation: Animation) -> Animation { animation }

    // MARK: Metrics

    enum Spacing {
        static let xs: CGFloat = 4      // hairline gaps
        static let s: CGFloat = 8       // intra-component
        static let gap: CGFloat = 12    // icon→label, dense inner gaps
        static let control: CGFloat = 14 // row/chip inner horizontal padding
        static let m: CGFloat = 16      // screen edge → content; card padding
        static let l: CGFloat = 24      // section → section; sheet padding
        static let xl: CGFloat = 32     // major separation

        // Frozen aliases
        static let sm = s
    }
    enum Radius {
        static let sm: CGFloat = 10     // small controls: banners, skeletons
        static let md: CGFloat = 16     // medium controls: list rows, buttons
        static let card: CGFloat = 20   // cards, tiles
        /// The sheet's own card radius: 25px of a 680px screen = 14.4pt, rounded
        /// up to 16. Home authored against the render, not against `card`, so
        /// the number is named rather than repeated as a literal at four call
        /// sites — which is what let `Aero.Radius.card` drift from the page.
        static let homeCard: CGFloat = 16
        static let sheet: CGFloat = 24  // sheets, dialogs, large surfaces
        static let input: CGFloat = 26  // composer / input bars
        static let chip: CGFloat = 999  // full pill: chips & status only
    }

    // MARK: Layout

    /// Reading-column foundation (UI rebuild Step 2; Android twin:
    /// GsLayout.contentMaxWidth = 640.dp). Wide canvases (iPad, landscape)
    /// center-clamp content at this width instead of stretching edge to
    /// edge; phones are already narrower and are unaffected. Generic — any
    /// screen can adopt it via View.gsContentWidth().
    enum Layout {
        static let contentMaxWidth: CGFloat = 640
    }
}

// MARK: - Shared cached formatters (deep-perf pass 80-b)

/**
 * One cached formatter per shape, process-wide. View bodies used to construct
 * ISO8601DateFormatter / DateFormatter / RelativeDateTimeFormatter PER ROW
 * PER BODY PASS — locale-data allocation is the expensive part, and on the
 * chat transcript that meant roughly O(visible bubbles + turns) formatter
 * allocations on every keystroke and every streaming flush. Identical output,
 * zero churn. Main-thread use only (view bodies, view models).
 */
enum GSFormatters {
    static let isoFractional: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()
    static let isoPlain = ISO8601DateFormatter()

    /// Tolerant ISO-8601 read: fractional seconds first, plain seconds fallback.
    static func date(from iso: String) -> Date? {
        isoFractional.date(from: iso) ?? isoPlain.date(from: iso)
    }

    static let clock: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        return formatter
    }()
    static let dayStamp: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
    static let dayTitle: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "d MMM yyyy"
        return formatter
    }()

    /// "2h ago"-style label, shared by the inbox / archive surfaces.
    static let relative: RelativeDateTimeFormatter = {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter
    }()

    static func relativeTime(from iso: String) -> String {
        guard let date = date(from: iso) else { return "" }
        return relative.localizedString(for: date, relativeTo: Date())
    }
}

// MARK: - Press feedback (kinetic scale)

struct KineticPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? Aero.pressScale : 1)
            .animation(Aero.spring, value: configuration.isPressed)
    }
}

// MARK: - Recovered shared modifiers (iOS build repair)
// Both definitions were lost from this file in the 5e37a4d union-merge while
// their call sites survived across the app — the same lost-symbol family as
// the Aero.motion bridge. Restored verbatim from their historical definitions
// (gsKeyboardDoneBar @ 0975be3, gsListRowChrome @ 102b5fe / Task 86-e) so the
// call sites keep their exact original behaviour.

extension View {
    /// Keyboard accessory: a trailing Done button that resigns the first
    /// responder. Attached to the text surfaces that hold the keyboard with
    /// no other way to put it away (fixed-height TextEditors, find-in-chat,
    /// the invisible auth code field) — shared so every site stays identical.
    func gsKeyboardDoneBar() -> some View {
        toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Done") {
                    UIApplication.shared.sendAction(
                        #selector(UIResponder.resignFirstResponder),
                        to: nil, from: nil, for: nil)
                }
            }
        }
    }

    /// Chrome for rows on the List-converted surfaces (Task 86-e): the List
    /// container must disappear so rows render exactly like the LazyVStack
    /// cards they replace — no separators, no tinted row background, and the
    /// old card margins carried by the row insets.
    func gsListRowChrome(_ insets: EdgeInsets) -> some View {
        listRowInsets(insets)
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
    }
}

// MARK: - Reading column (Step-2 content-width foundation)

extension View {
    /// Center-clamps the view's width to `Aero.Layout.contentMaxWidth` —
    /// the iOS twin of Android's `Modifier.gsContentWidth()`: on canvases
    /// narrower than the cap it is a no-op, on wider ones the column stays
    /// at 640pt and centers. Generic: any screen can adopt it.
    func gsContentWidth() -> some View {
        frame(maxWidth: Aero.Layout.contentMaxWidth)
    }
}

// MARK: - Card shadow (soft elevation)

struct AeroCardShadow: ViewModifier {
    func body(content: Content) -> some View {
        content.shadow(color: Color.black.opacity(0.06), radius: 12, x: 0, y: 4)
    }
}

extension View {
    func aeroCardShadow() -> some View { modifier(AeroCardShadow()) }

    /// Adds a spoken hint when Settings › Screen-reader hints is enabled.
    func gsHint(_ hint: String) -> some View {
        Group {
            if GSAccessibilityFlags.shared.screenReaderHints {
                self.accessibilityHint(hint)
            } else {
                self
            }
        }
    }
}

// MARK: - Legacy token bridge (kept for earlier scaffold references)

enum GSTheme {
    static let accent = Aero.accent
    static func displayTitle() -> Font { Aero.displayTitle() }
    static func headline() -> Font { Aero.headline() }
    static func body() -> Font { Aero.body() }
    static func caption() -> Font { Aero.caption() }
}
