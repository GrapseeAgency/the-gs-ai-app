import LocalAuthentication
import SwiftUI

/// The app lock behind Account & Security → Biometric Login.
///
/// This is what makes that switch a control instead of a memory. Before it,
/// the preference was written by Settings and read by nobody: a row claiming a
/// lock that could never be asked for. It is the iOS twin of Android's
/// `BiometricLockGate.kt`.
///
/// Behaviour: when `SettingsStore.biometricUnlock` is on, the shell does not
/// render until `LAContext` answers a real `.deviceOwnerAuthenticationWithBiometrics`
/// evaluation. Turning the switch off removes the gate on the next launch.
///
/// A lock this device cannot satisfy must not be a prison: if biometrics were
/// removed after the switch was armed, the shell opens for this launch WITHOUT
/// writing the downgrade, so a user who re-secures their phone gets the lock
/// back. Nothing here claims to unlock; it only reports what the device said.
struct GsBiometricGate<Content: View>: View {

    @ObservedObject private var settings = SettingsStore.shared
    @ViewBuilder var content: () -> Content

    /// Starts false and is resolved by the real evaluation; when the lock is
    /// off the body shows content regardless, so no store read is needed here.
    @State private var unlocked = false
    @State private var attempt = 0
    @State private var errorText: String?

    /// A check that just succeeded (turning the switch on, for instance) is
    /// the same proof this gate would ask for; prompting again immediately
    /// would be a second dismissal of the user's own authentication.
    private var recentlyVerified: Bool {
        guard settings.lastSecurityCheckMillis > 0 else { return false }
        let age = Date().timeIntervalSince1970 * 1000 - settings.lastSecurityCheckMillis
        return age >= 0 && age < 60_000
    }

    var body: some View {
        Group {
            if unlocked || !settings.biometricUnlock || recentlyVerified {
                content()
            } else {
                lockScreen
            }
        }
        .onChange(of: settings.biometricUnlock) { enabled in
            if !enabled { unlocked = true }
        }
        .task(id: attempt) {
            guard settings.biometricUnlock, !unlocked, !recentlyVerified else { return }
            await evaluate()
        }
    }

    // MARK: Lock screen

    private var lockScreen: some View {
        ZStack {
            GsSettingsBackdrop().allowsHitTesting(false)
            VStack(spacing: 10) {
                NexlyGlyph(name: "fingerprint", size: 42, color: ExplorePalette.peachDeep)
                    .frame(width: 66, height: 66)
                    .background(Circle().fill(.white.opacity(0.55)))
                Text("Nexly is locked")
                    .font(.custom(Aero.productName(.semibold), size: 16, relativeTo: .headline))
                    .foregroundStyle(Aero.text)
                Text("Confirm your fingerprint or face to continue.")
                    .font(.custom(Aero.productName(.regular), size: 12, relativeTo: .caption))
                    .foregroundStyle(Aero.textTertiary)
                    .multilineTextAlignment(.center)
                if let errorText {
                    Text(errorText)
                        .font(.custom(Aero.productName(.regular), size: 10, relativeTo: .caption2))
                        .foregroundStyle(Aero.danger)
                        .multilineTextAlignment(.center)
                }
                Spacer().frame(height: 4)
                Button {
                    attempt += 1
                } label: {
                    Text("Try again")
                        .font(.custom(Aero.productName(.medium), size: 12.5, relativeTo: .caption))
                        .foregroundStyle(ExplorePalette.peachDeep)
                        .padding(8)
                }
                .buttonStyle(.plain)
                // The only escape a locked shell can offer without lying: turn
                // the lock off rather than pretend to bypass it.
                Button {
                    settings.biometricUnlock = false
                    unlocked = true
                } label: {
                    Text("Turn off app lock")
                        .font(.custom(Aero.productName(.regular), size: 11, relativeTo: .caption2))
                        .foregroundStyle(Aero.textTertiary)
                        .padding(8)
                }
                .buttonStyle(.plain)
            }
            .padding(32)
        }
    }

    // MARK: Real evaluation

    /// True when the device can actually answer a biometric prompt.
    static func canUseBiometrics() -> Bool {
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
            return false
        }
        return true
    }

    private func evaluate() async {
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
            // No sensor / nothing enrolled: open for this launch, without
            // persisting the downgrade.
            unlocked = true
            return
        }
        do {
            let ok = try await context.evaluatePolicy(
                .deviceOwnerAuthenticationWithBiometrics,
                localizedReason: "Unlock Nexly")
            await MainActor.run {
                if ok {
                    settings.lastSecurityCheckMillis = Date().timeIntervalSince1970 * 1000
                    unlocked = true
                }
            }
        } catch {
            await MainActor.run {
                let code = (error as NSError).code
                let userSaidNo = code == LAError.userCancel.rawValue
                    || code == LAError.appCancel.rawValue
                    || code == LAError.systemCancel.rawValue
                    || code == LAError.userFallback.rawValue
                if !userSaidNo && attempt < 3 {
                    attempt += 1
                } else if !userSaidNo {
                    errorText = "Biometric check failed. Try again when you're ready."
                }
            }
        }
    }
}
