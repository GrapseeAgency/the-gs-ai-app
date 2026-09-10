import SwiftUI

/**
 * AERUO KINETIC — single source of design truth for iOS.
 * "Kinetic aurora over obsidian": disciplined editorial surfaces,
 * energy reserved for motion + one aurora accent family.
 * Mirrors android/.../ui/theme/ (Color.kt + Tokens.kt + Type.kt + Motion.kt).
 *
 * RULES
 *  - No view may paint a raw colour literal. Everything resolves through the
 *    semantic tokens below, which are dynamic (light + dark + high-contrast).
 *  - Appearance follows the app's own Settings ("System"/"Light"/"Dark")
 *    via .preferredColorScheme at the app root; every dynamic token follows.
 *  - Reduce motion / reduce animations degrade springs, press scale and the
 *    decorative loops; font scale multiplies every Aero type role.
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
        light: UIColor(red: 0.969, green: 0.969, blue: 0.961, alpha: 1),   // F7F7F5
        dark: UIColor(red: 0.039, green: 0.051, blue: 0.071, alpha: 1))    // 0A0D12
    static let secondaryBackground = dynamic( // grouped / under-page canvas
        light: UIColor(red: 0.941, green: 0.941, blue: 0.929, alpha: 1),   // F0F0ED
        dark: UIColor(red: 0.063, green: 0.078, blue: 0.102, alpha: 1))    // 10141A
    static let surface = dynamic(             // standard content surface
        light: .white,
        dark: UIColor(red: 0.067, green: 0.082, blue: 0.110, alpha: 1))    // 11151C
    static let elevatedSurface = dynamic(     // above-surface content
        light: .white,
        dark: UIColor(red: 0.106, green: 0.133, blue: 0.176, alpha: 1))    // 1B2230
    static let raisedSurface = dynamic(       // raised controls: pills, tiles
        light: UIColor(red: 0.941, green: 0.941, blue: 0.929, alpha: 1),   // F0F0ED
        dark: UIColor(red: 0.094, green: 0.118, blue: 0.157, alpha: 1))    // 181E28
    static let inputSurface = dynamic(        // text fields, composer field
        light: UIColor(red: 0.941, green: 0.941, blue: 0.929, alpha: 1),   // F0F0ED
        dark: UIColor(red: 0.078, green: 0.098, blue: 0.137, alpha: 1))    // 141923
    static let sheetSurface = dynamic(        // bottom sheets
        light: .white,
        dark: UIColor(red: 0.067, green: 0.082, blue: 0.110, alpha: 1))    // 11151C
    static let dialogSurface = dynamic(       // dialogs / confirmations
        light: .white,
        dark: UIColor(red: 0.094, green: 0.118, blue: 0.157, alpha: 1))    // 181E28
    static let navSurface = dynamic(          // drawer / navigation chrome
        light: .white,
        dark: UIColor(red: 0.039, green: 0.051, blue: 0.071, alpha: 1))    // 0A0D12

    // ---- Text hierarchy --------------------------------------------------------
    static let text = dynamic(
        light: UIColor(red: 0.078, green: 0.086, blue: 0.102, alpha: 1),   // 14161A
        dark: UIColor(red: 0.929, green: 0.937, blue: 0.949, alpha: 1),    // EDEFF2
        lightHC: .black,
        darkHC: .white)
    static let textSecondary = dynamic(
        light: UIColor(red: 0.357, green: 0.392, blue: 0.447, alpha: 1),   // 5B6472
        dark: UIColor(red: 0.545, green: 0.576, blue: 0.631, alpha: 1),    // 8B93A1
        lightHC: UIColor(red: 0.239, green: 0.267, blue: 0.314, alpha: 1), // 3D4450
        darkHC: UIColor(red: 0.706, green: 0.737, blue: 0.788, alpha: 1))  // B4BCC9
    static let textTertiary = dynamic(
        light: UIColor(red: 0.541, green: 0.569, blue: 0.616, alpha: 1),   // 8A919D
        dark: UIColor(red: 0.431, green: 0.467, blue: 0.529, alpha: 1))    // 6E7787
    static let textDisabled = dynamic(
        light: UIColor(red: 0.753, green: 0.769, blue: 0.796, alpha: 1),   // C0C4CB
        dark: UIColor(red: 0.290, green: 0.322, blue: 0.380, alpha: 1))    // 4A5261
    static let textPlaceholder = dynamic(
        light: UIColor(red: 0.604, green: 0.631, blue: 0.675, alpha: 1),   // 9AA1AC
        dark: UIColor(red: 0.431, green: 0.467, blue: 0.529, alpha: 1))    // 6E7787
    static let textInverted = dynamic(        // text on accent / dark imagery
        light: .white,
        dark: UIColor(red: 0.024, green: 0.137, blue: 0.110, alpha: 1))    // 06231C
    static let link = dynamic(
        light: UIColor(red: 0.039, green: 0.431, blue: 0.337, alpha: 1),   // 0A6E56
        dark: UIColor(red: 0.176, green: 0.831, blue: 0.659, alpha: 1))    // 2DD4A8

    // ---- Structural --------------------------------------------------------------
    static let outline = dynamic(
        light: UIColor(red: 0.898, green: 0.898, blue: 0.882, alpha: 1),   // E5E5E1
        dark: UIColor(red: 0.137, green: 0.169, blue: 0.216, alpha: 1),    // 232B37
        lightHC: UIColor(red: 0.725, green: 0.725, blue: 0.698, alpha: 1), // B9B9B2
        darkHC: UIColor(red: 0.224, green: 0.267, blue: 0.353, alpha: 1))  // 39445A
    static let outlineStrong = dynamic(
        light: UIColor(red: 0.824, green: 0.824, blue: 0.800, alpha: 1),   // D2D2CC
        dark: UIColor(red: 0.180, green: 0.220, blue: 0.275, alpha: 1),    // 2E3846
        lightHC: UIColor(red: 0.561, green: 0.561, blue: 0.529, alpha: 1), // 8F8F87
        darkHC: UIColor(red: 0.306, green: 0.361, blue: 0.471, alpha: 1))  // 4E5C78
    static let divider = dynamic(
        light: UIColor(red: 0.910, green: 0.910, blue: 0.894, alpha: 1),   // E8E8E4
        dark: UIColor(red: 0.114, green: 0.141, blue: 0.188, alpha: 1))    // 1D2430
    static let selected = dynamic(
        light: UIColor(red: 0.059, green: 0.639, blue: 0.494, alpha: 1),   // 0FA37E
        dark: UIColor(red: 0.176, green: 0.831, blue: 0.659, alpha: 1))    // 2DD4A8
    static let pressed = dynamic(             // pressed/hover overlay
        light: UIColor(red: 0.078, green: 0.086, blue: 0.102, alpha: 0.06),
        dark: UIColor(red: 0.929, green: 0.937, blue: 0.949, alpha: 0.08))
    static let focus = dynamic(
        light: UIColor(red: 0.059, green: 0.639, blue: 0.494, alpha: 1),
        dark: UIColor(red: 0.176, green: 0.831, blue: 0.659, alpha: 1))
    static let scrim = dynamic(
        light: UIColor(red: 0, green: 0, blue: 0, alpha: 0.5),
        dark: UIColor(red: 0, green: 0, blue: 0, alpha: 0.6))

    // ---- Semantic ------------------------------------------------------------------
    /// The single accent family — aurora teal. `accent` is theme-resolved:
    /// on paper it deepens for readability, on obsidian it glows.
    static let accent = dynamic(
        light: UIColor(red: 0.059, green: 0.639, blue: 0.494, alpha: 1),   // 0FA37E
        dark: UIColor(red: 0.176, green: 0.831, blue: 0.659, alpha: 1))    // 2DD4A8
    static let accentStrong = dynamic(
        light: UIColor(red: 0.039, green: 0.431, blue: 0.337, alpha: 1),   // 0A6E56
        dark: UIColor(red: 0.357, green: 0.886, blue: 0.757, alpha: 1))    // 5BE2C1
    static let accentSoft = dynamic(
        light: UIColor(red: 0.176, green: 0.831, blue: 0.659, alpha: 0.10),
        dark: UIColor(red: 0.176, green: 0.831, blue: 0.659, alpha: 0.14))
    static let onAccent = dynamic(
        light: .white,
        dark: UIColor(red: 0.024, green: 0.137, blue: 0.110, alpha: 1))    // 06231C
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
    static let aiActive = accent              // model alive: orb, streaming dot
    static let streaming = dynamic(
        light: UIColor(red: 0.118, green: 0.498, blue: 0.796, alpha: 1),   // 1E7FCB
        dark: UIColor(red: 0.298, green: 0.765, blue: 1.000, alpha: 1))    // 4CC3FF
    static let toolExecution = dynamic(
        light: UIColor(red: 0.431, green: 0.357, blue: 0.784, alpha: 1),   // 6E5BC8
        dark: UIColor(red: 0.616, green: 0.482, blue: 1.000, alpha: 1))    // 9D7BFF
    static let research = dynamic(
        light: UIColor(red: 0.604, green: 0.420, blue: 0.071, alpha: 1),   // 9A6B12
        dark: UIColor(red: 0.898, green: 0.694, blue: 0.361, alpha: 1))    // E5B15C
    static let voice = accent
    static let generation = toolExecution

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

    // ---- Aurora (raw gradient stops — AI-active moments ONLY) ------------------------
    static let aurora: [Color] = [
        Color(red: 0.176, green: 0.831, blue: 0.659),   // 2DD4A8
        Color(red: 0.298, green: 0.765, blue: 1.000),   // 4CC3FF
        Color(red: 0.616, green: 0.482, blue: 1.000)    // 9D7BFF
    ]

    // ---- Legacy raw-value aliases (DEPRECATED bridge for un-migrated call sites) -----
    // Screens must move to the semantic roles above. The aliases RESOLVE TO THE
    // SEMANTIC VALUES (not the old raw values) so that un-migrated screens stay
    // visually uniform with migrated components in both appearances.
    static let accentDeep = Color(red: 0.059, green: 0.639, blue: 0.494)       // 0FA37E raw family step
    static let textMuted = textSecondary
    static let raised = raisedSurface
    static let container = inputSurface
    static let containerHigh = dynamic(
        light: UIColor(red: 0.886, green: 0.886, blue: 0.867, alpha: 1),       // E2E2DD — one step
        dark: UIColor(red: 0.137, green: 0.169, blue: 0.227, alpha: 1))        // 232B3A   stronger container

    // MARK: Typography — serif display voice, sans interface.
    // Dynamic Type is the scaling mechanism: fonts use relativeTo text styles
    // and the Settings › font-scale slider maps to DynamicTypeSize at the app
    // root (GSApp.gsDynamicTypeSize). `responsive(...)` is the sanctioned way
    // for screens to get role-matched scaling at custom point sizes.

    static func displayTitle() -> Font { .system(size: 34, weight: .semibold, design: .serif, relativeTo: .largeTitle) }
    static func display() -> Font { .system(size: 28, weight: .semibold, design: .serif, relativeTo: .title) }
    static func headline() -> Font { .system(size: 22, weight: .semibold, design: .serif, relativeTo: .title2) }
    static func title() -> Font { .system(size: 17, weight: .semibold, relativeTo: .headline) }
    static func body() -> Font { .system(size: 15, weight: .regular, relativeTo: .body) }
    static func bodyMedium() -> Font { .system(size: 14, weight: .regular, relativeTo: .body) }
    static func caption() -> Font { .system(size: 13, weight: .regular, relativeTo: .footnote) }
    static func label() -> Font { .system(size: 12, weight: .medium, relativeTo: .caption) }
    static func metadata() -> Font { .system(size: 11, weight: .medium, relativeTo: .caption2) }
    static func button() -> Font { .system(size: 14, weight: .medium, relativeTo: .callout) }
    static func code() -> Font { .system(size: 13, weight: .regular, design: .monospaced, relativeTo: .footnote) }
    static func codeBlock() -> Font { .system(size: 14, weight: .regular, design: .monospaced, relativeTo: .body) }

    /// Dynamic-Type-responsive system font at a custom point size — identical
    /// appearance at the default type category, scaling with the reader's
    /// Dynamic Type setting (and the Settings font-scale override) exactly
    /// like the token fonts above. Map the point size to the closest semantic
    /// style (11→.caption2, 12→.caption, 13→.footnote, 14/15→.subheadline,
    /// 16→.callout, 17→.body, 22→.title2) so scaling behaviour matches the role.
    static func responsive(
        _ size: CGFloat,
        _ weight: Font.Weight = .regular,
        relativeTo style: Font.TextStyle = .body,
        design: Font.Design = .default
    ) -> Font {
        .system(size: size, weight: weight, design: design, relativeTo: style)
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
    static var pressScale: CGFloat { motionReduced ? 1.0 : 0.97 }
    static var staggerStep: Double { motionReduced ? 0 : 0.03 }

    static func stagger(_ index: Int) -> Double { Double(index) * staggerStep }

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
        static let md: CGFloat = 14     // medium controls: list rows, buttons
        static let card: CGFloat = 16   // cards, tiles
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
