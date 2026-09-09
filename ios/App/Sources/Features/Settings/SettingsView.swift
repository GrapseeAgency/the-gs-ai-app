import SwiftUI

/// Settings — 9 sections: Appearance, Chat, AI, Privacy, Security,
/// Notifications, Language, Accessibility, About.
struct SettingsView: View {

    /// Every switch, chip and slider reads and writes the remembered store.
    @ObservedObject private var settings = SettingsStore.shared

    // Privacy flows (UI-local)
    @State private var showClearData = false
    @State private var showDeleteAccount = false
    // Export flow (UI-local)
    @State private var exportURL: URL?
    @State private var showExport = false

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
            Button("Clear local data", role: .destructive) {
                ConversationStore.shared.wipeAllContent()
                GSHaptics.warning()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Cached chats, files and drafts on this device will be removed. Your account is untouched.")
        }
        .confirmationDialog(
            "Delete account?",
            isPresented: $showDeleteAccount,
            titleVisibility: .visible
        ) {
            Button("Delete account", role: .destructive) {
                GSHaptics.warning()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This permanently removes your account and all synced data. This cannot be undone.")
        }
        .sheet(isPresented: $showExport) {
            if let url = exportURL {
                ActivitySheet(items: [url])
            }
        }
    }

    // MARK: Export

    /// Privacy pass: the whole on-device corpus — conversations, the full
    /// message store, library saves — as one JSON document the reader shares
    /// anywhere via the system sheet. Encoding hiccups never open the sheet.
    /// Deep-perf pass 80-b: assembly (full SQL read + corpus encode + file
    /// write) runs OFF the main thread — with a long history that was a
    /// multi-hundred-ms UI freeze on tap. The sheet still opens from main.
    private func buildExportFile() {
        let store = ConversationStore.shared
        let conversations = store.conversations
        let library = store.savedLibraryItems()
        let exportedAt = ConversationStore.now()
        Task {
            let url: URL? = await Task.detached(priority: .userInitiated) { () -> URL? in
                let messages = store.exportMessages()
                let payload = ExportPayload(
                    exportedAt: exportedAt,
                    conversations: conversations,
                    messages: messages,
                    library: library)
                guard let data = try? JSONEncoder().encode(payload) else { return nil }
                let url = FileManager.default.temporaryDirectory
                    .appendingPathComponent("gs-ai-export.json")
                do { try data.write(to: url, options: .atomic) } catch { return nil }
                return url
            }.value
            guard let url else { return }
            exportURL = url
            GSHaptics.success()
            showExport = true
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
                    AeroChip(text: option, selected: settings.theme == option) {
                        GSHaptics.select()
                        settings.theme = option
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
            toggleRow("Reduce animations", isOn: $settings.reduceAnimations)
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
            toggleRow("Enter to send", isOn: $settings.enterToSend)
            toggleRow("Auto-title chats", isOn: $settings.autoTitle)
        }
    }

    // MARK: AI

    private var aiSection: some View {
        section("AI") {
            toggleRow("Memory", isOn: $settings.memory)
            toggleRow("Personalisation", isOn: $settings.personalisation)
            HStack(spacing: Aero.Spacing.s) {
                ForEach(["Low", "Medium", "High"], id: \.self) { level in
                    AeroChip(text: level, selected: settings.reasoning == level) {
                        GSHaptics.select()
                        settings.reasoning = level
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
                subtitle: "A JSON copy of your chats, messages and library",
                leading: { leadingIcon("square.and.arrow.up") },
                trailing: {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12))
                        .foregroundStyle(Aero.textMuted)
                },
                action: { buildExportFile() }
            )
            toggleRow("Help improve GS", isOn: $settings.helpImprove)
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
                    .foregroundStyle(Aero.danger)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(KineticPressStyle())
        }
    }

    // MARK: Security

    private var securitySection: some View {
        section("Security") {
            toggleRow("App passcode", isOn: $settings.appPasscode)
            toggleRow("Biometric unlock", isOn: $settings.biometricUnlock)
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
            toggleRow("Push notifications", isOn: $settings.pushNotifications)
            toggleRow("Task completed", isOn: $settings.taskCompleted)
            toggleRow("Assistant updates", isOn: $settings.assistantUpdates)
            toggleRow("Product news", isOn: $settings.productNews)
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
                        AeroChip(text: option, selected: settings.aiLanguage == option) {
                            GSHaptics.select()
                            settings.aiLanguage = option
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
                    Text("\(Int((settings.fontScale * 100).rounded()))%")
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                }
                Slider(value: $settings.fontScale, in: 0.8...1.4)
                    .tint(Aero.accent)
                Text("The quick brown fox jumps over the lazy dog.")
                    .font(Aero.body())
                    .foregroundStyle(Aero.text)
                    .scaleEffect(settings.fontScale)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, Aero.Spacing.s)
                    .animation(Aero.gentle, value: settings.fontScale)
            }
            toggleRow("High contrast", isOn: $settings.highContrast)
            toggleRow("Reduce motion", isOn: $settings.reduceMotion)
            toggleRow("Screen reader hints", isOn: $settings.screenReaderHints)
            toggleRow("Haptics", isOn: $settings.haptics)
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
            // Inline state flip — the selection tick (Task 85-e I10).
            .onChange(of: isOn.wrappedValue) { _ in GSHaptics.select() }
    }

    private func leadingIcon(_ name: String) -> some View {
        Image(systemName: name)
            .font(.system(size: 14))
            .foregroundStyle(Aero.accent)
            .frame(width: 34, height: 34)
            .background(Circle().fill(Aero.container))
    }
}


/// The export document — the same contract the Android build writes.
private struct ExportPayload: Encodable {
    let exportedAt: String
    let conversations: [StoredConversation]
    let messages: [StoredMessage]
    let library: [LibraryItem]
}

/// The system share sheet, so "Export data" hands the JSON to Files, Mail,
/// AirDrop — wherever the reader wants it.
private struct ActivitySheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
