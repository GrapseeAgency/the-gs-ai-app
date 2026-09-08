import SwiftUI

/**
 * AERUO KINETIC entry.
 * First launch walks Auth → Onboarding before the command centre; later
 * launches go straight Home. Session state persists in UserDefaults —
 * swap for the real token/session store when the auth backend lands.
 */
@main
struct GSApp: App {
    // Home-screen quick actions (New chat / New image / Ask GS) ride in
    // through the scene delegate this adaptor advertises.
    @UIApplicationDelegateAdaptor(QuickActionAppDelegate.self) private var appDelegate
    @AppStorage("gs.session.active") private var sessionActive = false
    @AppStorage("gs.onboarded") private var onboarded = false

    // Appearance + accessibility contract (Task 85-e): the Settings theme
    // choice tints the system chrome (sheets, alerts, keyboard) and the
    // font-scale slider drives real Dynamic Type app-wide.
    @ObservedObject private var settings = SettingsStore.shared

    var body: some Scene {
        WindowGroup {
            Group {
                if sessionActive {
                    if onboarded {
                        RootView()
                    } else {
                        OnboardingView {
                            onboarded = true
                        }
                    }
                } else {
                    AuthFlowView {
                        sessionActive = true
                    }
                }
            }
            .preferredColorScheme(Self.colorScheme(for: settings.theme))
            .gsDynamicTypeSize(Self.dynamicTypeSize(for: settings.fontScale))
        }
    }

    // MARK: Appearance mapping

    /// "Dark"/"Light" pin the mode so system chrome matches; "System" (and
    /// anything unknown) leaves the OS decision untouched.
    private static func colorScheme(for theme: String) -> ColorScheme? {
        switch theme {
        case "Dark": return .dark
        case "Light": return .light
        default: return nil
        }
    }

    /// fontScale → DynamicTypeSize steps: 0.8→.small, 0.9→.medium, 1.0→
    /// untouched (the system default, .large), 1.1→.xLarge, 1.2→.xxLarge,
    /// 1.3+→.xxxLarge. Intermediate slider values clamp to the nearest step.
    private static func dynamicTypeSize(for scale: Double) -> DynamicTypeSize? {
        switch scale {
        case ..<0.85: return .small
        case ..<0.95: return .medium
        case ..<1.05: return nil      // default — leave Dynamic Type untouched
        case ..<1.15: return .xLarge
        case ..<1.25: return .xxLarge
        default: return .xxxLarge
        }
    }
}

/// Conditional Dynamic Type override — `dynamicTypeSize(_:)` takes a
/// non-optional size, so the default (1.0 / nil) applies no modifier at all
/// and the system Dynamic Type setting rules untouched.
private struct GSDynamicTypeSizeModifier: ViewModifier {
    let size: DynamicTypeSize?

    func body(content: Content) -> some View {
        if let size {
            content.dynamicTypeSize(size)
        } else {
            content
        }
    }
}

private extension View {
    func gsDynamicTypeSize(_ size: DynamicTypeSize?) -> some View {
        modifier(GSDynamicTypeSizeModifier(size: size))
    }
}
