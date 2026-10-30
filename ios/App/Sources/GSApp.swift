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
    @State private var showReferenceWelcome = true
    // Appearance + accessibility contract (Task 85-e): the Settings theme
    // choice tints the system chrome (sheets, alerts, keyboard) and the
    // font-scale slider drives real Dynamic Type app-wide.
    @ObservedObject private var settings = SettingsStore.shared

    init() {
        // TEST HOOKS (GsChatUITests, ITEM 5 of the current brief) — launch
        // arguments, read HERE and nowhere else, and read only when present,
        // so a normal launch takes none of these branches. They exist because
        // an XCUITest runs OUTSIDE the app process and can set app state no
        // other way: no UserDefaults, no stores, no direct initialisation.
        //
        //   -gs_test_session_active
        //       Skips AuthFlowView and OnboardingView (both flags true), so
        //       the launch lands on RootView — the conversation workspace IS
        //       the app's root, which is the surface the UI test drives.
        //
        //   -gs_test_local_model <path>
        //       Initialises the native engine with the model at <path>
        //       BEFORE the first turn. Without this the app's local path is
        //       never armed (nothing in production calls
        //       GsNativeLoader.initialize yet — the offline reply would be
        //       the canned localReply, which GsChatUiTests treats as a
        //       FAILURE). The path is the verified 0.5B GGUF the ios-native
        //       workflow stages on the runner host, visible inside the
        //       simulator.
        //
        //   -gs_test_seed_chats
        //       Three fixed conversations with one user and one assistant
        //       turn each, so MenuChromeTests can open the REAL menus — the
        //       chats row's context menu and swipes, both bubble menus, the
        //       two header long-press menus and the drawer's recent-row
        //       menu — on a store an XCUITest cannot write itself. Debug
        //       only: a Release build ships no seed.
        let args = ProcessInfo.processInfo.arguments
        if args.contains("-gs_test_session_active") {
            sessionActive = true
            onboarded = true
        }
        #if DEBUG
        if args.contains("-gs_test_seed_chats") {
            MenuChromeSeed.install()
        }
        #endif
        if let i = args.firstIndex(of: "-gs_test_local_model"),
           args.indices.contains(i + 1) {
            let modelPath = args[i + 1]
            if FileManager.default.fileExists(atPath: modelPath) {
                GsNativeLoader.initialize(modelPath: modelPath)
            } else {
                NSLog("GSApp: -gs_test_local_model path missing: %@", modelPath)
            }
        }
    }

    var body: some Scene {
        WindowGroup {
            Group {
                if sessionActive {
                    if onboarded {
                        // The real biometric gate: when Account & Security →
                        // Biometric Login is on, the shell does not render
                        // until LAContext answers. Off (the default) is a
                        // pass-through.
                        GsBiometricGate { RootView() }
                    } else {
                        if showReferenceWelcome {
                            ReferenceWelcomeView(
                                onExplore: { withAnimation(Aero.spring) { showReferenceWelcome = false } },
                                onSkip: { onboarded = true }
                            )
                        } else {
                            OnboardingView {
                                onboarded = true
                                // The real producer behind the "Tips &
                                // Suggestions" switch: onboarding just
                                // captured the preferences this notice
                                // describes. Skipping the setup records
                                // nothing.
                                Task { @MainActor in
                                    GsNotifier.record(
                                        kind: .tips,
                                        title: "Personalization applied",
                                        body: "Your preferences have been updated. We'll now tailor responses to your style and interests.")
                                }
                            }
                        }
                    }
                } else {
                    AuthFlowView {
                        sessionActive = true
                        // The real producer behind the "Account Activity"
                        // switch: a sign-in is the account event that actually
                        // happens. It goes to the Inbox, not the shade — the
                        // user is mid-sign-in, and this build posts no banner.
                        // The explicit main-actor hop keeps this closure legal
                        // even though `onComplete` is a plain () -> Void.
                        Task { @MainActor in
                            GsNotifier.record(
                                kind: .accountActivity,
                                title: "Signed in to Nexly",
                                body: "This device is now signed in to your Grapsee account.")
                        }
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
