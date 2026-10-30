import LocalAuthentication
import SwiftUI
import UIKit

/// Account & Security — docs/reference/pending/account-security.png.
///
/// The sheet's groups, rows, wells and pills are reproduced in its order and
/// at its own scale. What changes is the data behind them, because the mock
/// draws a product this build does not have: the owner's instruction was
/// explicit — "those are unreal not real… people will only have to use the
/// Grapsee to login the nexly… nothing else".
///
/// So Google and Apple keep their slots but say "Not connected", with no
/// chevron (nowhere to go); "Add another sign-in method" keeps its plus well
/// and states that Grapsee Mail is the only credential. There is no password
/// store and no account server, so Change Password and Two-Factor state the
/// truth rather than offering a flow that ends nowhere.
///
/// What IS real, and therefore live:
///  - the identity the user typed (AccountStore);
///  - the plan pill: PlanStore's plan name — the one plan this build has;
///  - the session: a persisted install id + the hardware it runs on;
///  - Biometric Login: a real LAContext evaluation that the root gate reads;
///  - both "..." surfaces (session facts, security facts) are real sheets.
struct AccountSecurityView: View {

    @ObservedObject private var account = AccountStore.shared
    @ObservedObject private var plan = PlanStore.shared
    @ObservedObject private var settings = SettingsStore.shared

    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var router: Router

    @State private var showSessionFacts = false
    @State private var showSecurityFacts = false
    @State private var biometricError: String?

    private var deviceLabel: String {
        "\(UIDevice.current.model) • iOS \(UIDevice.current.systemVersion)"
    }

    private var sessionId: String { Self.stableSessionId() }

    private var biometricAvailable: Bool { GsBiometricGate<EmptyView>.canUseBiometrics() }

    var body: some View {
        GsSheetPage(
            title: "Account & Security",
            subtitle: "Keep your account safe and in your control",
            onBack: { dismiss() }
        ) {
            profileCard

            GsSheetGroupHeader("Sign-in Methods", caption: "Manage how you sign in")
            GsGroupCard {
                GsSheetRow(
                    title: "Grapsee Mail",
                    detail: account.email.isEmpty ? "Not set" : account.email,
                    action: { router.path.append(.profile) }
                ) {
                    GsWellTile(glyph: "mail", tone: .lilac, size: 34)
                } trailing: {
                    HStack(spacing: 8) {
                        GsTagPill(text: "Primary", tone: GsWellTone.mint.ink)
                        GsSheetChevron()
                    }
                }
                divider
                GsSheetRow(
                    title: "Google",
                    detail: "Not connected",
                    action: nil
                ) {
                    GsBrandWell(glyph: "brand-google", size: 34)
                } trailing: {
                    EmptyView()
                }
                divider
                GsSheetRow(
                    title: "Apple",
                    detail: "Not connected",
                    action: nil
                ) {
                    GsBrandWell(glyph: "brand-apple", size: 34)
                } trailing: {
                    EmptyView()
                }
                divider
                GsSheetRow(
                    title: "Add another sign-in method",
                    detail: "Grapsee Mail is the only sign-in — use a different provider",
                    action: nil
                ) {
                    GsWellTile(glyph: "plus", tone: .blue, size: 34)
                } trailing: {
                    EmptyView()
                }
            }

            GsSheetGroupHeader("Password & Authentication", caption: "Manage your security")
            GsGroupCard {
                GsSheetRow(
                    title: "Change Password",
                    detail: "Grapsee Mail signs you in — no password is stored here",
                    action: nil
                ) {
                    GsWellTile(glyph: "key", tone: .rose, size: 34)
                } trailing: {
                    EmptyView()
                }
                divider
                // The mock's off switch stays in its slot, drawn disabled:
                // there is no account server to enrol a second factor with.
                GsSheetToggle(
                    title: "Two-Factor Authentication",
                    detail: "Not available — no account server to enrol with",
                    isOn: .constant(false),
                    enabled: false
                ) {
                    GsWellTile(glyph: "shield-check", tone: .lilac, size: 34)
                }
                divider
                GsSheetToggle(
                    title: "Biometric Login",
                    detail: biometricAvailable
                        ? "Use fingerprint or face ID"
                        : "No biometric hardware enrolled on this device",
                    isOn: Binding(
                        get: { settings.biometricUnlock },
                        set: { want in
                            if !want {
                                settings.biometricUnlock = false
                            } else {
                                enableBiometric()
                            }
                        }),
                    enabled: biometricAvailable
                ) {
                    GsWellTile(glyph: "fingerprint", tone: .rose, size: 34)
                }
                if let biometricError {
                    Text(biometricError)
                        .font(.custom(Aero.productName(.regular), size: 9, relativeTo: .caption2))
                        .foregroundStyle(Aero.danger)
                        .padding(.horizontal, 14)
                        .padding(.bottom, 8)
                }
            }

            GsSheetGroupHeader("Active Sessions", caption: "See where you're logged in")
            GsGroupCard {
                GsSheetRow(
                    title: "Current Session",
                    detail: "\(deviceLabel) • \(sessionId.prefix(8))",
                    action: { showSessionFacts = true }
                ) {
                    GsWellTile(glyph: "device-mobile", tone: .blue, size: 34)
                } trailing: {
                    HStack(spacing: 6) {
                        Circle().fill(GsWellTone.mint.ink).frame(width: 5, height: 5)
                        Text("This device")
                            .font(.custom(Aero.productName(.regular), size: 8, relativeTo: .caption2))
                            .foregroundStyle(Aero.textTertiary)
                        GsSheetChevron()
                    }
                }
                divider
                GsSheetRow(
                    title: "Other Sessions",
                    detail: "No other device is signed in",
                    action: nil
                ) {
                    GsWellTile(glyph: "devices", tone: .rose, size: 34)
                } trailing: {
                    Text("None")
                        .font(GsSheetType.rowValue())
                        .foregroundStyle(Aero.textTertiary)
                }
            }

            GsSheetGroupHeader("Manage Devices", caption: "Control your devices")
            GsGroupCard {
                GsSheetRow(
                    title: "All Devices",
                    detail: "This install is the only signed-in device",
                    action: nil
                ) {
                    GsWellTile(glyph: "devices", tone: .mint, size: 34)
                } trailing: {
                    EmptyView()
                }
            }

            Spacer().frame(height: 14)
            Button {
                showSecurityFacts = true
            } label: {
                HStack(spacing: 10) {
                    GsWellTile(glyph: "shield-check", tone: .lilac, size: 34)
                    VStack(alignment: .leading, spacing: 1) {
                        Text("Your account is protected")
                            .font(GsSheetType.rowTitle())
                            .foregroundStyle(Aero.text)
                        Text(securityLine)
                            .font(GsSheetType.rowDetail())
                            .foregroundStyle(Aero.textTertiary)
                            .lineLimit(2)
                    }
                    Spacer(minLength: 8)
                    GsSheetChevron()
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 11)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 20)
                        .fill(Aero.glassSurface.opacity(0.55))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .strokeBorder(.white.opacity(0.55), lineWidth: 1)
                )
            }
            .buttonStyle(KineticPressStyle())
        }
        .sheet(isPresented: $showSessionFacts) {
            GsFactSheet(
                title: "Current session",
                facts: [
                    ("Device", deviceLabel),
                    ("Session", sessionId),
                    ("Signed in with", account.email.isEmpty ? "Grapsee Mail" : account.email),
                ])
                .presentationDetents([.medium])
        }
        .sheet(isPresented: $showSecurityFacts) {
            GsFactSheet(
                title: "Your account is protected",
                facts: [
                    ("App lock", settings.biometricUnlock ? "Biometric Login on" : "Off"),
                    ("Last check", formattedLastCheck),
                    ("Plan", plan.planName),
                    ("Two-factor", "Not available in this build"),
                ])
                .presentationDetents([.medium])
        }
    }

    // MARK: Pieces

    private var profileCard: some View {
        Button {
            router.path.append(.profile)
        } label: {
            HStack(spacing: 12) {
                Image("GSHomeAvatar")
                    .resizable()
                    .scaledToFill()
                    .frame(width: 56, height: 56)
                    .clipShape(Circle())
                VStack(alignment: .leading, spacing: 1) {
                    HStack(spacing: 4) {
                        Text(account.displayName.isEmpty ? "Grapsee account" : account.displayName)
                            .font(.custom(Aero.productName(.semibold), size: 13, relativeTo: .subheadline))
                            .foregroundStyle(Aero.text)
                            .lineLimit(1)
                        NexlyGlyph(name: "rosette-discount-check", size: 12, color: GsWellTone.blue.ink)
                    }
                    Text(account.email.isEmpty ? "No Grapsee address on this device" : account.email)
                        .font(.custom(Aero.productName(.regular), size: 9, relativeTo: .caption2))
                        .foregroundStyle(Aero.textTertiary)
                        .lineLimit(1)
                    Spacer().frame(height: 3)
                    planPill
                }
                Spacer(minLength: 8)
                GsSheetChevron()
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .gsSheetCard(corner: 22)
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityLabel("Profile and preferences")
    }

    private var planPill: some View {
        HStack(spacing: 3) {
            NexlyGlyph(name: "crown", size: 9, color: GsWellTone.lilac.ink)
            Text(plan.planName)
                .font(.custom(Aero.productName(.medium), size: 8, relativeTo: .caption2))
                .foregroundStyle(GsWellTone.lilac.ink)
        }
        .padding(.horizontal, 7)
        .padding(.vertical, 2)
        .background(Capsule().fill(GsWellTone.lilac.wash.opacity(0.75)))
    }

    private var securityLine: String {
        if settings.lastSecurityCheckMillis > 0 {
            return "Last security check: \(formattedLastCheck)"
        }
        return "No security check yet — turn on Biometric Login to require one"
    }

    private var formattedLastCheck: String {
        guard settings.lastSecurityCheckMillis > 0 else { return "None yet" }
        let date = Date(timeIntervalSince1970: settings.lastSecurityCheckMillis / 1000)
        return GSFormatters.relative.localizedString(for: date, relativeTo: Date())
    }

    private var divider: some View {
        Rectangle()
            .fill(Aero.divider.opacity(0.85))
            .frame(height: 1.0 / 3.0)
            .padding(.leading, 50)
            .padding(.trailing, 12)
    }

    // MARK: Real biometric enable

    /// A lock is only real once the user has proved they can pass it. Enabling
    /// it blind would lock them out, so success is the only thing that writes
    /// the preference.
    private func enableBiometric() {
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
            biometricError = "This device cannot answer a biometric prompt."
            return
        }
        context.evaluatePolicy(
            .deviceOwnerAuthenticationWithBiometrics,
            localizedReason: "Turn on Biometric Login"
        ) { ok, failure in
            Task { @MainActor in
                if ok {
                    settings.biometricUnlock = true
                    settings.lastSecurityCheckMillis = Date().timeIntervalSince1970 * 1000
                    biometricError = nil
                } else if let failure {
                    let code = (failure as NSError).code
                    if code != LAError.userCancel.rawValue {
                        biometricError = "Biometric check failed — the lock stays off."
                    }
                }
            }
        }
    }

    /// One persisted install id — real, stable across launches, and not a
    /// device identifier.
    private static func stableSessionId() -> String {
        let key = "gs.session.clientId"
        if let existing = UserDefaults.standard.string(forKey: key) {
            return existing
        }
        let created = UUID().uuidString
        UserDefaults.standard.set(created, forKey: key)
        return created
    }
}

/// A small real fact sheet, shared by the session and security rows.
struct GsFactSheet: View {
    let title: String
    let facts: [(String, String)]
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                Text(title)
                    .font(.custom(Aero.productName(.semibold), size: 15, relativeTo: .headline))
                    .foregroundStyle(Aero.text)
                ForEach(Array(facts.enumerated()), id: \.offset) { _, fact in
                    HStack(alignment: .top) {
                        Text(fact.0)
                            .font(.custom(Aero.productName(.regular), size: 11, relativeTo: .caption))
                            .foregroundStyle(Aero.textSecondary)
                            .frame(width: 108, alignment: .leading)
                        Text(fact.1)
                            .font(.custom(Aero.productName(.regular), size: 11, relativeTo: .caption))
                            .foregroundStyle(Aero.text)
                            .fixedSize(horizontal: false, vertical: true)
                        Spacer(minLength: 0)
                    }
                }
                Button {
                    dismiss()
                } label: {
                    Text("Done")
                        .font(Aero.button())
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Capsule().fill(ExplorePalette.peachDeep))
                }
                .buttonStyle(KineticPressStyle())
                .padding(.top, 6)
            }
            .padding(20)
        }
    }
}
