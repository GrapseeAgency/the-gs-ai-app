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

    var body: some Scene {
        WindowGroup {
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
    }
}
