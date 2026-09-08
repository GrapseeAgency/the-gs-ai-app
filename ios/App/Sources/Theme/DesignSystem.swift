import SwiftUI
import UIKit

/**
 * AERUO KINETIC — single source of design truth for iOS.
 * "Kinetic aurora over obsidian": disciplined editorial surfaces,
 * energy reserved for motion + one aurora accent family.
 * Mirrors android/.../ui/theme/Color.kt + Type.kt + Motion.kt exactly.
 */
enum Aero {
    // MARK: Palette (dynamic — responds to system appearance)
    static func dynamic(light: UIColor, dark: UIColor) -> Color {
        Color(UIColor { $0.userInterfaceStyle == .dark ? dark : light })
    }

    static let background = dynamic(
        light: UIColor(red: 0.969, green: 0.969, blue: 0.961, alpha: 1),   // F7F7F5
        dark: UIColor(red: 0.039, green: 0.051, blue: 0.071, alpha: 1))    // 0A0D12
    static let surface = dynamic(
        light: .white,
        dark: UIColor(red: 0.067, green: 0.082, blue: 0.110, alpha: 1))    // 11151C
    static let raised = dynamic(
        light: .white,
        dark: UIColor(red: 0.094, green: 0.118, blue: 0.157, alpha: 1))    // 181E28
    static let text = dynamic(
        light: UIColor(red: 0.078, green: 0.086, blue: 0.102, alpha: 1),   // 14161A
        dark: UIColor(red: 0.929, green: 0.937, blue: 0.949, alpha: 1))    // EDEFF2
    static let textMuted = dynamic(
        light: UIColor(red: 0.420, green: 0.443, blue: 0.478, alpha: 1),   // 6B7280
        dark: UIColor(red: 0.545, green: 0.576, blue: 0.631, alpha: 1))    // 8B93A1
    static let outline = dynamic(
        light: UIColor(red: 0.898, green: 0.898, blue: 0.882, alpha: 1),   // E5E5E1
        dark: UIColor(red: 0.137, green: 0.169, blue: 0.216, alpha: 1))    // 232B37
    static let container = dynamic(
        light: UIColor(red: 0.941, green: 0.941, blue: 0.929, alpha: 1),   // F0F0ED
        dark: UIColor(red: 0.078, green: 0.098, blue: 0.137, alpha: 1))    // 141923
    static let containerHigh = dynamic(
        light: UIColor(red: 0.886, green: 0.886, blue: 0.867, alpha: 1),   // E2E2DD
        dark: UIColor(red: 0.137, green: 0.169, blue: 0.227, alpha: 1))    // 232B3A

    /// The single accent family — aurora teal
    static let accent = Color(red: 0.176, green: 0.831, blue: 0.659)        // 2DD4A8
    static let accentDeep = Color(red: 0.059, green: 0.639, blue: 0.494)    // 0FA37E

    /// Kinetic aurora gradient — ONLY for AI-active moments
    /// (streaming caret, generation progress, voice waveform, primary CTA)
    static let aurora: [Color] = [
        Color(red: 0.176, green: 0.831, blue: 0.659),   // 2DD4A8
        Color(red: 0.298, green: 0.765, blue: 1.000),   // 4CC3FF
        Color(red: 0.616, green: 0.482, blue: 1.000)    // 9D7BFF
    ]

    // MARK: Typography — serif display voice, sans interface
    static func displayTitle() -> Font { .system(size: 34, weight: .semibold, design: .serif, relativeTo: .largeTitle) }
    static func display() -> Font { .system(size: 28, weight: .semibold, design: .serif, relativeTo: .title) }
    static func headline() -> Font { .system(size: 22, weight: .semibold, design: .serif, relativeTo: .title2) }
    static func title() -> Font { .system(size: 17, weight: .semibold, design: .default, relativeTo: .headline) }
    static func body() -> Font { .system(size: 15, weight: .regular, design: .default, relativeTo: .body) }
    static func caption() -> Font { .system(size: 13, weight: .regular, design: .default, relativeTo: .caption) }
    static func label() -> Font { .system(size: 12, weight: .medium, design: .default, relativeTo: .caption2) }

    /// Dynamic-Type-responsive system font at a custom point size — identical
    /// appearance at the default type category, scaling with the reader's
    /// Dynamic Type setting (and the Settings font-scale override) exactly
    /// like the seven token fonts above. The audit's ~60 raw `.system(size:)`
    /// TEXT runs convert to this; icon-sized runs stay deliberately fixed.
    /// Map the point size to the closest semantic style (11→.caption2,
    /// 12→.caption, 13→.footnote, 14/15→.subheadline, 16→.callout,
    /// 17→.body, 22→.title2) so scaling behaviour matches the role.
    static func responsive(
        _ size: CGFloat,
        _ weight: Font.Weight = .regular,
        relativeTo style: Font.TextStyle = .body,
        design: Font.Design = .default
    ) -> Font {
        .system(size: size, weight: weight, design: design, relativeTo: style)
    }

    // MARK: Motion — springs over eases, stagger entrances, press scales
    static let spring = Animation.spring(response: 0.35, dampingFraction: 0.8)
    static let snappy = Animation.spring(response: 0.28, dampingFraction: 0.75)
    static let gentle = Animation.spring(response: 0.5, dampingFraction: 0.9)
    static let pressScale: CGFloat = 0.97
    static let staggerStep: Double = 0.03

    static func stagger(_ index: Int) -> Double { Double(index) * staggerStep }

    // MARK: Metrics
    enum Spacing {
        static let xs: CGFloat = 4
        static let s: CGFloat = 8
        static let m: CGFloat = 16
        static let l: CGFloat = 24
        static let xl: CGFloat = 32
    }
    enum Radius {
        static let card: CGFloat = 16
        static let chip: CGFloat = 999
        static let sheet: CGFloat = 24
        static let input: CGFloat = 26
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
        let reduced = SettingsStore.shared.animationReduced
        let pressedScale: CGFloat = reduced ? 1.0 : Aero.pressScale
        return configuration.label
            .scaleEffect(configuration.isPressed ? pressedScale : 1)
            .animation(reduced ? nil : Aero.spring, value: configuration.isPressed)
    }
}

// MARK: - Keyboard dismiss bar (keyboard toolbar with Done)

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
}

// MARK: - Card shadow (soft elevation)

struct AeroCardShadow: ViewModifier {
    func body(content: Content) -> some View {
        content.shadow(color: Color.black.opacity(0.06), radius: 12, x: 0, y: 4)
    }
}

extension View {
    func aeroCardShadow() -> some View { modifier(AeroCardShadow()) }
}

// MARK: - Legacy token bridge (kept for earlier scaffold references)

enum GSTheme {
    static let accent = Aero.accent
    static func displayTitle() -> Font { Aero.displayTitle() }
    static func headline() -> Font { Aero.headline() }
    static func body() -> Font { Aero.body() }
    static func caption() -> Font { Aero.caption() }
}
