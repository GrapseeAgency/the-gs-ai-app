import SwiftUI

/// Settings — 9 sections: Appearance, Chat, AI, Privacy, Security,
/// Notifications, Language, Accessibility, About.
struct SettingsView: View {

    private static let dangerRed = Color(red: 0.9, green: 0.28, blue: 0.28)

    // Appearance
    @State private var theme = "System"
    @State private var reduceAnimations = false

    // Chat
    @State private var enterToSend = true
    @State private var autoTitle = true

    // AI
    @State private var memory = true
    @State private var personalisation = true
    @State private var reasoning = "Medium"

    // Privacy
    @State private var helpImprove = false
    @State private var showClearData = false
    @State private var showDeleteAccount = false

    // Security
    @State private var appPasscode = false
    @State private var biometricUnlock = true

    // Notifications
    @State private var pushNotifications = true
    @State private var taskCompleted = true
    @State private var assistantUpdates = true
    @State private var productNews = false

    // Language
    @State private var aiLanguage = "EN"

    // Accessibility
    @State private var fontScale: Double = 1.0
    @State private var highContrast = false
    @State private var reduceMotion = false
    @State private var haptics = true

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.m) {
                header
                appearanceSection
                chatSection
                aiSection
                privacySection
                securitySection
                notificationsSection
                languageSection
                accessibilitySection
                aboutSection
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
        .confirmationDialog(
            "Clear local data?",
            isPresented: $showClearData,
            titleVisibility: .visible
        ) {
            Button("Clear local data", role: .destructive) {}
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Cached chats, files and drafts on this device will be removed. Your account is untouched.")
        }
        .confirmationDialog(
            "Delete account?",
            isPresented: $showDeleteAccount,
            titleVisibility: .visible
        ) {
            Button("Delete account", role: .destructive) {}
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This permanently removes your account and all synced data. This cannot be undone.")
        }
    }

    // MARK: Header

    private var header: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.xs) {
            Text("Settings")
                .font(Aero.headline())
                .foregroundStyle(Aero.text)
            Text("Tune GS to taste.")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
        }
    }

    // MARK: Appearance

    private var appearanceSection: some View {
        section("Appearance") {
            HStack(spacing: Aero.Spacing.s) {
                ForEach(["Light", "Dark", "System"], id: \.self) { option in
                    AeroChip(text: option, selected: theme == option) {
                        theme = option
                    }
                }
                Spacer()
            }
            HStack(spacing: Aero.Spacing.s) {
                Circle()
                    .fill(Aero.accent)
                    .frame(width: 18, height: 18)
                Text("Accent — Aurora teal")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
                Spacer()
            }
            toggleRow("Reduce animations", isOn: $reduceAnimations)
        }
    }

    // MARK: Chat

    private var chatSection: some View {
        section("Chat") {
            NavigationLink(value: AeroRoute.models) {
                AeroListRow(
                    title: "Default model",
                    subtitle: "GS Balanced",
                    leading: {
                        Image(systemName: "sparkles")
                            .font(.system(size: 14))
                            .foregroundStyle(Aero.accent)
                            .frame(width: 34, height: 34)
                            .background(Circle().fill(Aero.container))
                    },
                    trailing: {
                        Image(systemName: "chevron.right")
                            .font(.system(size: 12))
                            .foregroundStyle(Aero.textMuted)
                    }
                )
            }
            .buttonStyle(KineticPressStyle())
            toggleRow("Enter to send", isOn: $enterToSend)
            toggleRow("Auto-title chats", isOn: $autoTitle)
        }
    }

    // MARK: AI

    private var aiSection: some View {
        section("AI") {
            toggleRow("Memory", isOn: $memory)
            toggleRow("Personalisation", isOn: $personalisation)
            HStack(spacing: Aero.Spacing.s) {
                ForEach(["Low", "Medium", "High"], id: \.self) { level in
                    AeroChip(text: level, selected: reasoning == level) {
                        reasoning = level
                    }
                }
                Spacer()
            }
        }
    }

    // MARK: Privacy

    private var privacySection: some View {
        section("Privacy") {
            AeroListRow(
                title: "Export data",
                leading: { leadingIcon("arrow.down.circle") },
                trailing: {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12))
                        .foregroundStyle(Aero.textMuted)
                },
                action: {}
            )
            toggleRow("Help improve GS", isOn: $helpImprove)
            Button {
                showClearData = true
            } label: {
                HStack(spacing: Aero.Spacing.s) {
                    Image(systemName: "arrow.counterclockwise")
                        .font(.system(size: 14))
                    Text("Clear local data")
                        .font(Aero.body())
                    Spacer()
                }
                .foregroundStyle(Aero.text)
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(RoundedRectangle(cornerRadius: 12).fill(Aero.container))
            }
            .buttonStyle(KineticPressStyle())
            Button {
                showDeleteAccount = true
            } label: {
                Text("Delete account")
                    .font(Aero.body())
                    .foregroundStyle(Self.dangerRed)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(KineticPressStyle())
        }
    }

    // MARK: Security

    private var securitySection: some View {
        section("Security") {
            toggleRow("App passcode", isOn: $appPasscode)
            toggleRow("Biometric unlock", isOn: $biometricUnlock)
            AeroListRow(
                title: "Two-factor",
                subtitle: "Authenticator app",
                leading: { leadingIcon("lock.shield") },
                trailing: { AeroChip(text: "On", selected: true) }
            )
            AeroListRow(
                title: "Trusted devices",
                leading: { leadingIcon("desktopcomputer") },
                trailing: {
                    Text("2")
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                }
            )
        }
    }

    // MARK: Notifications

    private var notificationsSection: some View {
        section("Notifications") {
            toggleRow("Push notifications", isOn: $pushNotifications)
            toggleRow("Task completed", isOn: $taskCompleted)
            toggleRow("Assistant updates", isOn: $assistantUpdates)
            toggleRow("Product news", isOn: $productNews)
        }
    }

    // MARK: Language

    private var languageSection: some View {
        section("Language") {
            AeroListRow(
                title: "App language",
                subtitle: "English (UK)",
                leading: { leadingIcon("globe") },
                trailing: {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12))
                        .foregroundStyle(Aero.textMuted)
                }
            )
            VStack(alignment: .leading, spacing: Aero.Spacing.xs) {
                Text("AI language")
                    .font(Aero.body())
                    .foregroundStyle(Aero.text)
                HStack(spacing: Aero.Spacing.s) {
                    ForEach(["EN", "中文", "हिन्दी", "العربية"], id: \.self) { option in
                        AeroChip(text: option, selected: aiLanguage == option) {
                            aiLanguage = option
                        }
                    }
                }
            }
            AeroListRow(
                title: "Voice language",
                subtitle: "English (UK)",
                leading: { leadingIcon("waveform") },
                trailing: {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12))
                        .foregroundStyle(Aero.textMuted)
                }
            )
        }
    }

    // MARK: Accessibility

    private var accessibilitySection: some View {
        section("Accessibility") {
            VStack(alignment: .leading, spacing: Aero.Spacing.xs) {
                HStack {
                    Text("Font scale")
                        .font(Aero.body())
                        .foregroundStyle(Aero.text)
                    Spacer()
                    Text("\(Int((fontScale * 100).rounded()))%")
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                }
                Slider(value: $fontScale, in: 0.8...1.4)
                    .tint(Aero.accent)
                Text("The quick brown fox jumps over the lazy dog.")
                    .font(Aero.body())
                    .foregroundStyle(Aero.text)
                    .scaleEffect(fontScale)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, Aero.Spacing.s)
                    .animation(Aero.gentle, value: fontScale)
            }
            toggleRow("High contrast", isOn: $highContrast)
            toggleRow("Reduce motion", isOn: $reduceMotion)
            toggleRow("Haptics", isOn: $haptics)
        }
    }

    // MARK: About

    private var aboutSection: some View {
        section("About") {
            valueRow("Version", value: appVersion)
            valueRow("Build", value: buildNumber)
            valueRow("Updates", value: "Automatic via GS LiveUpdate")
        }
    }

    /// Read live from the bundle so the row always names the installed build.
    private var appVersion: String {
        (Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String) ?? "1.0"
    }

    private var buildNumber: String {
        (Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String) ?? "1"
    }

    private func valueRow(_ title: String, value: String) -> some View {
        HStack {
            Text(title)
                .font(Aero.body())
                .foregroundStyle(Aero.text)
            Spacer()
            Text(value)
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
        }
    }

    // MARK: Helpers

    private func section<Content: View>(
        _ title: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s + 4) {
                SectionHeader(title: title)
                content()
            }
        }
    }

    private func toggleRow(_ title: String, isOn: Binding<Bool>) -> some View {
        Toggle(title, isOn: isOn)
            .font(Aero.body())
            .foregroundStyle(Aero.text)
            .tint(Aero.accent)
    }

    private func leadingIcon(_ name: String) -> some View {
        Image(systemName: name)
            .font(.system(size: 14))
            .foregroundStyle(Aero.accent)
            .frame(width: 34, height: 34)
            .background(Circle().fill(Aero.container))
    }
}
