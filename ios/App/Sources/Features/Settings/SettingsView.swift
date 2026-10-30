import SwiftUI
import AVFoundation

/// Settings — the reference hub, plus the nine detail pages its rows reach.
///
/// The hub is `docs/reference/settings/settings-hub.png`: identity card,
/// upgrade card, three captioned groups over one glass card each, Log Out.
/// The nine original sections did not disappear — they moved behind
/// `SettingsSectionID` detail pages (Appearance also carries Accessibility,
/// Privacy carries Security, Chat carries AI) so no surface lost its entry
/// point while the hub matches the mock exactly.
struct SettingsView: View {

    /// Every switch, chip and slider reads and writes the remembered store.
    @ObservedObject private var settings = SettingsStore.shared
    /// The name/email the reader actually gave (never invented).
    @ObservedObject private var account = AccountStore.shared
    @ObservedObject private var plan = PlanStore.shared

    /// Which detail page to render. Nil renders the hub.
    var detail: SettingsSectionID? = nil

    /// The router hand-off (reference hub): the top rows are DESTINATIONS, not
    /// inline toggles, exactly like the sheets. Nil keeps the inline sections
    /// working when the view is embedded without a router.
    var onRoute: ((AeroRoute) -> Void)? = nil

    @Environment(\.dismiss) private var dismiss
    /// The same session flag the auth gate reads (GSApp.swift).
    @AppStorage("gs.session.active") private var sessionActive = true

    // Sign-out is UI-local; the privacy flows live on PrivacyDataControlsView.
    @State private var showSignOut = false
    @State private var showLicences = false

    var body: some View {
        Group {
            if let detail {
                SettingsDetailPage(section: detail, onBack: { dismiss() }) {
                    detailSections(detail)
                }
            } else {
                hub
            }
        }
        .confirmationDialog(
            "Log out of Nexly?",
            isPresented: $showSignOut,
            titleVisibility: .visible
        ) {
            Button("Log Out", role: .destructive) {
                GSHaptics.warning()
                sessionActive = false
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("You can sign back in any time. Your local chats stay on this device.")
        }
        .sheet(isPresented: $showLicences) { licencesSheet }
    }

    // MARK: Hub — the reference mock

    /// `settings-hub.png`: identity card with the blossom bleed, the Upgrade
    /// card, three captioned groups, the standalone Log Out card. Every row
    /// is a real destination; the four surfaces the mock does not name stay
    /// reachable inside their semantic detail pages (see `detailSections`).
    private var hub: some View {
        GsSettingsPage(
            title: "Settings",
            subtitle: "Customize your Nexly experience",
            onBack: { dismiss() }
        ) {
            // Identity card.
            GsGroupCard {
                Button {
                    onRoute?(.profile)
                } label: {
                    identityRow
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Profile and preferences")
            }

            Spacer().frame(height: 12)

            // Upgrade card.
            GsGroupCard {
                HStack(spacing: 11) {
                    GsWellTile(glyph: "sparkles", tone: .peach, size: 34)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Upgrade to Nexly Pro")
                            .font(Aero.bodyMedium().weight(.semibold))
                            .foregroundStyle(Aero.text)
                        // The mock's caption is 12.5pt (settings-geometry.json),
                        // the same size as a row's detail. `Aero.caption()` is 13pt,
                        // and at 13 the line box pushed this sentence onto a third
                        // line that lineLimit(2) then cut to "exclusive…" — the
                        // shipped render lost the last two words of the mock's own
                        // copy. fixedSize keeps the second line from being clipped.
                        Text("Get more power, faster responses, and exclusive features.")
                            .font(.custom(Aero.productName(.regular), size: 12.5, relativeTo: .caption))
                            .foregroundStyle(Aero.textTertiary)
                            .lineLimit(2)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 8)
                    GsInlinePill(label: "Upgrade", trailingGlyph: "arrow-right") {
                        onRoute?(.subscriptionUsage)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 11)
            }

            // Personalization.
            GsGroupHeader(title: "Personalization", caption: "Make Nexly feel like you")
            GsGroupCard {
                hubRow("Profile & Preferences", "Your name, interests and style", "user", .peach, .profile)
                hubDivider
                hubRow("Memory", "Let Nexly remember what matters", "brain", .peach, .memory)
                hubDivider
                hubRow("Appearance", "Theme and display", "palette", .peach, .settingsSection(.appearance))
                hubDivider
                hubRow("Language", "Choose your preferred language", "language", .peach, .settingsSection(.language), divider: false)
            }

            // Privacy & Data.
            GsGroupHeader(title: "Privacy & Data", caption: "Your data, your control")
            GsGroupCard {
                hubRow("Privacy Settings", "Manage your data and activity", "lock", .peach, .privacyDataControls)
                hubDivider
                hubRow("Data Controls", "Control what you share and store", "shield-check", .peach, .privacyDataControls)
                hubDivider
                hubRow("Export / Delete Data", "Download or remove your data", "database", .peach, .privacyDataControls, divider: false)
            }

            // App Settings.
            GsGroupHeader(title: "App Settings", caption: "Fine-tune your experience")
            GsGroupCard {
                // Android's hub carries this row for the same reason: the
                // model / reasoning page was reachable only from Chat's AI
                // section, so the reference screen had no direct entry. It
                // lives in the last group so every card the settings pixel
                // gate anchors on keeps its measured y.
                hubRow("AI Intelligence", "Model, reasoning effort and preferences", "sparkles", .peach, .modelSelection)
                hubDivider
                // Android's hub exposes these two as their own rows; on iOS
                // they were reachable only from inside other pages (Chat's AI
                // section, Appearance's accessibility block). Same last group,
                // same reason: no anchored card moves.
                hubRow("Chat & Conversations", "How new chats behave", "message-circle", .peach, .settingsSection(.chat))
                hubDivider
                hubRow("Accessibility", "Contrast, text size and motion", "eye", .peach, .settingsSection(.accessibility))
                hubDivider
                hubRow("Voice & Speech", "Listen, speak and keep your hands free", "microphone", .peach, .voiceSettings)
                hubDivider
                hubRow("Notifications & Alerts", "Choose what you receive", "bell", .peach, .notificationsAlerts)
                hubDivider
                hubRow("Account & Security", "Sign-in, app lock and sessions", "device-mobile", .peach, .accountSecurity)
                hubDivider
                hubRow("Connected Apps", "Integrations and permissions", "link", .peach, .connectedApps)
                hubDivider
                hubRow("Help & Support", "Answers, assistant and contact", "help-circle", .peach, .helpSupport)
                hubDivider
                hubRow("About Nexly", "Version, terms and support", "info-circle", .peach, .settingsSection(.about), divider: false)
            }

            // Log Out.
            Spacer().frame(height: 12)
            GsGroupCard {
                GsSettingsRow(
                    title: "Log Out",
                    detail: "Sign out from your account",
                    showDivider: false,
                    action: { showSignOut = true }
                ) {
                    GsWellTile(glyph: "logout", tone: .peach, size: 28)
                } trailing: {
                    chevron
                }
            }

            Spacer().frame(height: 14)
        }
    }

    private var identityRow: some View {
        HStack(alignment: .center, spacing: 11) {
            ZStack(alignment: .bottomTrailing) {
                Circle()
                    .fill(Aero.raisedSurface)
                    .frame(width: 52, height: 52)
                    .overlay(Circle().strokeBorder(.white.opacity(0.55), lineWidth: 2))
                Text(account.displayName.isEmpty ? "NX" : initialsFor(account.displayName))
                    .font(.custom(Aero.productName(.semibold), size: 17, relativeTo: .headline))
                    .foregroundStyle(Aero.text)
                Circle()
                    .fill(Aero.raisedSurface)
                    .frame(width: 18, height: 18)
                    .overlay(NexlyGlyph(name: "pencil", size: 10, color: Aero.textSecondary))
                    .overlay(Circle().strokeBorder(.white.opacity(0.85), lineWidth: 1))
                    .offset(x: 3, y: 3)
            }
            // The mock's identity type: 15 / 12.5 with a 10pt plan pill, which is
            // what makes the card 54-of-avatar tall rather than the column's own
            // 63. Android's hub card carries the same numbers.
            VStack(alignment: .leading, spacing: 1) {
                Text(account.displayName.isEmpty ? "Your account" : account.displayName)
                    .font(.custom(Aero.productName(.semibold), size: 15, relativeTo: .subheadline))
                    .foregroundStyle(Aero.text)
                    .lineLimit(1)
                Text(account.email.isEmpty ? "Add your Grapsee Mail address" : account.email)
                    .font(.custom(Aero.productName(.regular), size: 12.5, relativeTo: .caption))
                    .foregroundStyle(Aero.textTertiary)
                    .lineLimit(1)
                Text(plan.planName)
                    .font(.custom(Aero.productName(.medium), size: 10, relativeTo: .caption2))
                    .foregroundStyle(Color(red: 0.541, green: 0.290, blue: 0.122))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 2.5)
                    .background(Capsule().fill(Color(red: 0.961, green: 0.812, blue: 0.710).opacity(0.55)))
                    .padding(.top, 2)
            }
            Spacer(minLength: 6)
            chevron
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
        .contentShape(Rectangle())
        // The mock's blossom bleed: the shipped lotus photograph, faded into the
        // card's right edge and drawn at 45% so it reads as the reference's pale
        // impression rather than a flower pasted onto a settings page. The raw
        // photograph measures R-B 44 and an 18.9 L spread in that corner where
        // the mock measures 33.5 and 9.6; over the card's pearl fill 45% lands
        // both within ~2 L of the mock. Android draws the same number
        // (`LOTUS_BLEED_ALPHA`).
        // Behind the row and clipped to the card's corner, so no petal escapes:
        // `.gsGlass` fills a shape but does not clip the content above it.
        .background(alignment: .trailing) {
            GeometryReader { geo in
                Image("GSHomeLotusNew")
                    .resizable()
                    .scaledToFill()
                    .frame(width: 190, height: geo.size.height)
                    .clipped()
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .opacity(0.45)
                    .mask(
                        LinearGradient(
                            colors: [.clear, .white.opacity(0.55), .white],
                            startPoint: .leading, endPoint: .trailing)
                    )
            }
            .allowsHitTesting(false)
        }
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private func hubRow(
        _ title: String,
        _ detail: String,
        _ glyph: String,
        _ tone: GsWellTone,
        _ route: AeroRoute,
        divider: Bool = true
    ) -> some View {
        GsSettingsRow(
            title: title,
            detail: detail,
            showDivider: divider,
            action: { onRoute?(route) }
        ) {
            GsWellTile(glyph: glyph, tone: tone, size: 28)
        } trailing: {
            chevron
        }
    }

    private var hubDivider: some View {
        Rectangle()
            .fill(Aero.divider.opacity(0.7))
            .frame(height: 1.0 / 3.0)
            .padding(.leading, 50)
            .padding(.trailing, 12)
    }

    private var chevron: some View {
        NexlyGlyph(name: "chevron-right", size: 13, color: Aero.textTertiary)
    }

    private func initialsFor(_ name: String) -> String {
        let parts = name.split(separator: " ").map(String.init)
        if parts.isEmpty { return "A" }
        if parts.count == 1 { return String(parts[0].prefix(2)).uppercased() }
        return (String(parts[0].prefix(1)) + String(parts[parts.count - 1].prefix(1))).uppercased()
    }

    // MARK: Detail pages

    /// Keeps every pre-existing section reachable: the four surfaces the mock
    /// does not name ride with their semantic neighbours (Accessibility with
    /// Appearance, Security with Privacy, AI with Chat).
    @ViewBuilder
    private func detailSections(_ section: SettingsSectionID) -> some View {
        switch section {
        case .appearance:
            appearanceSection
            accessibilitySection
        case .chat:
            chatSection
            aiSection
        case .ai:
            aiSection
        case .language:
            languageSection
        case .notifications:
            notificationsSection
        case .security:
            securitySection
        case .accessibility:
            accessibilitySection
        case .about:
            aboutSection
        }
    }

    // MARK: Appearance

    /// Theme + motion only. The static accent swatch that used to sit here read
    /// as a control but changed nothing; iOS has no accent-family system, so
    /// the row and the hub's "accent color" promise are both gone.
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
            toggleRow("Reduce animations", isOn: $settings.reduceAnimations)
        }
    }

    // MARK: Chat

    private var chatSection: some View {
        section("Chat") {
            toggleRow("Enter to send", isOn: $settings.enterToSend)
            toggleRow("Auto-title chats", isOn: $settings.autoTitle)
        }
    }

    // MARK: AI

    /// The "Reasoning effort" Low/Medium/High chips are DELETED (Phase 2):
    /// they persisted a value that nothing in the pipeline reads. Memory and
    /// Personalisation stay — they are honest preferences.
    private var aiSection: some View {
        section("AI") {
            // The reference's AI Intelligence page: model source and reasoning
            // effort as tiles, both writing the settings the chat already reads.
            NavigationLink {
                AiIntelligenceView()
            } label: {
                AeroListRow(
                    title: "AI Intelligence",
                    subtitle: "Model source and reasoning effort",
                    leading: {
                        Image(systemName: "cpu")
                            .font(.system(size: 14))
                            .foregroundStyle(Aero.accent)
                            .frame(width: 34, height: 34)
                            .background(Circle().fill(Aero.container))
                    },
                    trailing: {
                        Image(systemName: "chevron.right")
                            .font(.system(size: 12))
                            .foregroundStyle(Aero.textMuted)
                    })
            }
            .buttonStyle(.plain)

            toggleRow("Memory", isOn: $settings.memory)
            toggleRow("Personalisation", isOn: $settings.personalisation)
            // Commit 1(b). One row in the existing AI section with the existing
            // toggleRow: no new screen, no new card, no new token. Default OFF.
            // The subtitle states the precondition rather than implying local is
            // always better, because with no model installed this switch would
            // do nothing at all.
            // Only offered when the engine is actually reachable. A switch that
            // silently does nothing is worse than no switch, and "is it
            // available" is not a question a user can answer from a title.
            if GsNativeLoader.isAvailable {
                toggleRow("Prefer on-device AI", isOn: $settings.preferLocal)
            }
        }
    }

    // MARK: Security

    /// Only the REAL controls remain (Phase 2): the fabricated "Two-factor:
    /// On" and "Trusted devices: 2" values are gone — nothing was measured
    /// behind them. The passcode toggles persist locally and stay.
    private var securitySection: some View {
        section("Security") {
            toggleRow("App passcode", isOn: $settings.appPasscode)
            toggleRow("Biometric unlock", isOn: $settings.biometricUnlock)
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

    /// Every row here states something true. The interface ships in one
    /// language, answers already follow the language the reader writes in
    /// (the server prompt says so), and the voice belongs to the system
    /// synthesizer — so the previous App/Voice chevrons had nowhere to go and
    /// the AI-language chips stored a preference nothing read. Both are gone.
    private var languageSection: some View {
        section("Language") {
            valueRow("App language", value: "English (UK)")
            valueRow("Answer language", value: "Match my message")
            valueRow("Device language", value: Self.speechLanguage)
            Text("The interface ships in English (UK). GS answers in the language you write in; the voice is the system synthesizer, changed in iOS Settings.")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// The synthesizer this build actually speaks through, e.g. "en-US".
    private static var speechLanguage: String {
        AVSpeechSynthesisVoice.currentLanguageCode()
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

    /// Version + build, then the same real destinations Android's About page
    /// carries. The earlier page stopped at the two version rows, which made
    /// Android the only platform with a way to reach the privacy policy.
    @ViewBuilder
    private var aboutSection: some View {
        section("About") {
            valueRow("Version", value: appVersion)
            valueRow("Build", value: buildNumber)
        }
        section("Links") {
            linkRow("Website", detail: "grapsee.agency", glyph: "globe",
                    url: "https://grapsee.agency")
            linkRow("Contact", detail: "contact@grapsee.agency", glyph: "envelope",
                    url: "mailto:contact@grapsee.agency")
            linkRow("Privacy policy", detail: "How the app handles your data", glyph: "lock",
                    url: "https://grapsee.agency/privacy")
            AeroListRow(
                title: "Open-source licences",
                subtitle: "Poppins, Swift, llama.cpp and more",
                leading: { leadingIcon("book") },
                trailing: {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12))
                        .foregroundStyle(Aero.textMuted)
                },
                action: { showLicences = true }
            )
        }
    }

    /// The same credit list Android shows, naming what this build actually uses.
    private var licencesSheet: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            Text("Open-source licences")
                .font(Aero.headline())
                .foregroundStyle(Aero.text)
            ScrollView {
                Text("""
                Poppins — Copyright 2020 The Poppins Project Authors, SIL Open Font License 1.1.

                Swift and SwiftUI — Apache License 2.0 with Runtime Library Exception.

                llama.cpp — MIT License.

                SQLite — public domain.

                Full texts ship in the repository's LICENSES directory.
                """)
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
                .fixedSize(horizontal: false, vertical: true)
            }
            Button("Close") { showLicences = false }
                .font(Aero.button())
                .foregroundStyle(Aero.text)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(Capsule().fill(Aero.container))
        }
        .padding(Aero.Spacing.l)
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
    }

    /// Read live from the bundle so the row always names the installed build.
    private var appVersion: String {
        (Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String) ?? "1.0"
    }

    private var buildNumber: String {
        (Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String) ?? "1"
    }

    private func linkRow(
        _ title: String,
        detail: String,
        glyph: String,
        url: String
    ) -> some View {
        AeroListRow(
            title: title,
            subtitle: detail,
            leading: { leadingIcon(glyph) },
            trailing: {
                Image(systemName: "arrow.up.right")
                    .font(.system(size: 11))
                    .foregroundStyle(Aero.textMuted)
            },
            action: {
                guard let link = URL(string: url) else { return }
                UIApplication.shared.open(link)
            }
        )
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
        @ViewBuilder content: @escaping () -> Content
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
struct ExportPayload: Encodable {
    let exportedAt: String
    let conversations: [StoredConversation]
    let messages: [StoredMessage]
    let library: [LibraryItem]
}

/// The system share sheet, so "Export data" hands the JSON to Files, Mail,
/// AirDrop — wherever the reader wants it.
struct ActivitySheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
