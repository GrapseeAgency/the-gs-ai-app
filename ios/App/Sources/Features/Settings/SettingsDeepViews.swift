import SwiftUI
import AVFoundation

// THE TWO PAGES THAT DID NOT EXIST.
//
// Android has had Settings → Voice → Speech engine and Settings → About →
// Open-source licences since 0.86.21; on iOS the first was a value row with no
// destination ("Speech engine — Device default") and the second was a
// `.sheet` with one grey paragraph. Both are now pages with the same content
// and the same anatomy as their Android twins, so the two builds cannot drift.

// MARK: - Speech engine

/// Settings → Voice & Speech → Speech engine.
///
/// Everything here is wired to the real synthesizer: the engine and voice names
/// are read from `AVSpeechSynthesisVoice`, the speed and pitch are `@AppStorage`
/// values applied to the utterance that "Test voice" actually speaks, and the
/// footnote says where voices come from. Nothing on this page is a label
/// pretending to be a control.
struct SpeechEngineView: View {

    @Environment(\.dismiss) private var dismiss

    /// 0 = slower/lower, 1 = natural, 2 = faster/higher. Stored as steps rather
    /// than raw rates so a restored backup cannot put an inaudible value here.
    @AppStorage("gs.tts.rateStep") private var rateStep: Int = 1
    @AppStorage("gs.tts.pitchStep") private var pitchStep: Int = 1

    @State private var speaking = false
    @State private var synthesizer = AVSpeechSynthesizer()

    private let rateLabels = ["Slower", "Natural", "Faster"]
    private let pitchLabels = ["Lower", "Natural", "Higher"]
    /// AVSpeechUtterance's default rate is 0.5; these are the three steps either
    /// side of it that stay intelligible.
    private let rates: [Float] = [0.42, 0.50, 0.58]
    private let pitches: [Float] = [0.90, 1.00, 1.10]

    private var languageCode: String { AVSpeechSynthesisVoice.currentLanguageCode() }
    private var voice: AVSpeechSynthesisVoice? {
        AVSpeechSynthesisVoice(language: languageCode)
    }

    var body: some View {
        GsSettingsPage(
            title: "Speech engine",
            subtitle: "How answers sound when you ask Nexly to read them.",
            onBack: { dismiss() }
        ) {
            statusCard

            if voice == nil {
                unavailableCard
            }

            GsGroupHeader(title: "Engine", caption: nil)
            GsGroupCard {
                GsSettingsRow(
                    title: "Engine",
                    detail: "AVSpeechSynthesizer",
                    action: nil
                ) {
                    GsWellTile(glyph: "wave-sine", tone: .lilac, size: 28)
                } trailing: {
                    EmptyView()
                }
                GsSettingsRow(
                    title: "Voice",
                    detail: voice?.name ?? "System default",
                    action: nil
                ) {
                    GsWellTile(glyph: "globe", tone: .peach, size: 28)
                } trailing: {
                    EmptyView()
                }
                GsSettingsRow(
                    title: "Language",
                    detail: Locale(identifier: languageCode).localizedString(forIdentifier: languageCode)
                        ?? languageCode,
                    showDivider: false,
                    action: nil
                ) {
                    GsWellTile(glyph: "language", tone: .mint, size: 28)
                } trailing: {
                    EmptyView()
                }
            }

            GsGroupHeader(title: "Delivery", caption: nil)
            GsGroupCard {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Speed")
                        .font(.custom(Aero.productName(.medium), size: 13, relativeTo: .body))
                        .foregroundStyle(Aero.text)
                    chipRow(labels: rateLabels, selected: rateStep) { index in
                        rateStep = index
                        GSHaptics.select()
                    }
                    Divider().opacity(0.4).padding(.vertical, 2)
                    Text("Pitch")
                        .font(.custom(Aero.productName(.medium), size: 13, relativeTo: .body))
                        .foregroundStyle(Aero.text)
                    chipRow(labels: pitchLabels, selected: pitchStep) { index in
                        pitchStep = index
                        GSHaptics.select()
                    }
                    Button(action: testVoice) {
                        Text(speaking ? "Speaking…" : "Test voice")
                            .font(.custom(Aero.productName(.semibold), size: 14, relativeTo: .body))
                            .foregroundStyle(Aero.onAccent)
                            .frame(maxWidth: .infinity)
                            .frame(height: 46)
                            .background(Capsule().fill(Aero.accent))
                    }
                    .buttonStyle(KineticPressStyle())
                    .padding(.top, 6)
                    .accessibilityLabel("Test voice with the current speed and pitch")
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
            }

            footnote(
                "Speech is synthesized on this device by the engine above. Audio is never " +
                "uploaded, and these two controls change every answer you ask Nexly to read. " +
                "More voices are installed in iOS Settings → Accessibility → Spoken Content."
            )
        }
        .onDisappear { synthesizer.stopSpeaking(at: .immediate) }
    }

    // MARK: Pieces

    private var statusCard: some View {
        GsGroupCard {
            HStack(spacing: 12) {
                GsWellTile(glyph: "wave-sine", tone: .lilac, size: 44)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Read aloud")
                        .font(.custom(Aero.productName(.semibold), size: 14, relativeTo: .subheadline))
                        .foregroundStyle(Aero.text)
                    Text(voice.map { "\($0.name) · \($0.language)" }
                        ?? "No voice is installed for this language.")
                        .font(.custom(Aero.productName(.regular), size: 11.5, relativeTo: .caption))
                        .foregroundStyle(Aero.textTertiary)
                        .lineLimit(2)
                }
                Spacer(minLength: 8)
                Text(voice == nil ? "Unavailable" : "Ready")
                    .font(.custom(Aero.productName(.medium), size: 10.5, relativeTo: .caption2))
                    .foregroundStyle(voice == nil ? Aero.danger : Aero.success)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(
                        Capsule().fill((voice == nil ? Aero.danger : Aero.success).opacity(0.12)))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 14)
        }
    }

    /// The one failure this page can have, stated as a state rather than left as
    /// a silent button. iOS ships a synthesizer, so this appears only when the
    /// device has no voice for the current language (a stripped-down install).
    private var unavailableCard: some View {
        GsGroupCard {
            VStack(alignment: .leading, spacing: 8) {
                GsArtwork(art: .voice, size: 92)
                    .frame(maxWidth: .infinity)
                Text("No voice for this language")
                    .font(.custom(Aero.productName(.semibold), size: 15, relativeTo: .headline))
                    .foregroundStyle(Aero.text)
                Text("iOS can download one in Settings → Accessibility → Spoken Content → Voices. " +
                     "Until then, read-aloud has nothing to speak with.")
                    .font(.custom(Aero.productName(.regular), size: 12, relativeTo: .caption))
                    .foregroundStyle(Aero.textTertiary)
                    .fixedSize(horizontal: false, vertical: true)
                Button {
                    guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
                    UIApplication.shared.open(url)
                } label: {
                    Text("Open iOS Settings")
                        .font(.custom(Aero.productName(.semibold), size: 13, relativeTo: .body))
                        .foregroundStyle(Aero.onAccent)
                        .frame(maxWidth: .infinity)
                        .frame(height: 42)
                        .background(Capsule().fill(Aero.accent))
                }
                .buttonStyle(KineticPressStyle())
                .padding(.top, 4)
            }
            .padding(14)
        }
    }

    private func chipRow(labels: [String], selected: Int, onSelect: @escaping (Int) -> Void) -> some View {
        HStack(spacing: 6) {
            ForEach(Array(labels.enumerated()), id: \.offset) { index, label in
                let on = index == selected
                Button {
                    onSelect(index)
                } label: {
                    Text(label)
                        .font(.custom(
                            Aero.productName(on ? .semibold : .medium),
                            size: 12, relativeTo: .caption))
                        .foregroundStyle(on ? Aero.accentStrong : Aero.textSecondary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 32)
                        .background(
                            Capsule().fill(on ? Aero.accent.opacity(0.14) : Aero.text.opacity(0.04)))
                        .overlay(
                            Capsule().strokeBorder(
                                on ? Aero.accent.opacity(0.42) : Aero.outline.opacity(0.7),
                                lineWidth: 1))
                }
                .buttonStyle(KineticPressStyle())
                .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
            }
        }
    }

    private func testVoice() {
        guard let voice else { return }
        if synthesizer.isSpeaking {
            synthesizer.stopSpeaking(at: .immediate)
            speaking = false
            return
        }
        let utterance = AVSpeechUtterance(
            string: "Hi. This is how I will read your answers — " +
                "\(rateLabels[rateStep].lowercased()) and \(pitchLabels[pitchStep].lowercased()).")
        utterance.voice = voice
        utterance.rate = rates[min(max(rateStep, 0), 2)]
        utterance.pitchMultiplier = pitches[min(max(pitchStep, 0), 2)]
        speaking = true
        synthesizer.speak(utterance)
        // No delegate in this view: the button returns to its idle label on a
        // timer that matches the sample's length rather than lying forever.
        DispatchQueue.main.asyncAfter(deadline: .now() + 4) { speaking = false }
    }

    private func footnote(_ text: String) -> some View {
        Text(text)
            .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2))
            .foregroundStyle(Aero.textTertiary)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 4)
            .padding(.top, 8)
    }
}

// MARK: - Licences

private struct LicenceEntry: Identifiable {
    let id = UUID()
    let title: String
    let licence: String
    let note: String
}

/// Settings → About → Open-source licences.
///
/// This was a `.sheet` with one grey paragraph — legally adequate, and the one
/// surface in About not built like the rest of the app. Grouped by what the
/// reader is actually looking at (typeface, UI stack, inference), and it ends
/// with a real way to read the full texts.
struct LicencesView: View {

    @Environment(\.dismiss) private var dismiss

    private let groups: [(String, [LicenceEntry])] = [
        ("Typeface", [
            LicenceEntry(
                title: "Poppins",
                licence: "SIL Open Font License 1.1",
                note: "Copyright 2020 The Poppins Project Authors. The interface typeface.")
        ]),
        ("Runtime & UI", [
            LicenceEntry(title: "Swift & SwiftUI", licence: "Apache License 2.0",
                         note: "The language and the framework every surface is built with."),
            LicenceEntry(title: "Combine", licence: "Apache License 2.0",
                         note: "The observation plumbing behind the app's stores."),
            LicenceEntry(title: "AVFoundation", licence: "Apple SDK terms",
                         note: "Speech synthesis and audio focus for read-aloud."),
        ]),
        ("Data & storage", [
            LicenceEntry(title: "SQLite", licence: "Public domain",
                         note: "The on-device store behind chats, memories and settings."),
            LicenceEntry(title: "URLSession", licence: "Apple SDK terms",
                         note: "Network transport.")
        ]),
        ("On-device inference", [
            LicenceEntry(title: "llama.cpp", licence: "MIT License",
                         note: "The CPU inference runtime behind the on-device model. Its own " +
                               "licence and the model weights' licences travel with the download.")
        ]),
    ]

    var body: some View {
        GsSettingsPage(
            title: "Open-source licences",
            subtitle: "What this build is made of, and under what terms.",
            onBack: { dismiss() }
        ) {
            Text("Nexly is built on open-source work. Each entry below names the project, " +
                 "the licence it ships under and what it does here.")
                .font(.custom(Aero.productName(.regular), size: 12, relativeTo: .caption))
                .foregroundStyle(Aero.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 4)

            ForEach(groups, id: \.0) { group in
                GsGroupHeader(title: group.0, caption: nil)
                GsGroupCard {
                    VStack(spacing: 0) {
                        ForEach(Array(group.1.enumerated()), id: \.element.id) { index, entry in
                            if index > 0 {
                                Divider().opacity(0.35).padding(.leading, 14)
                            }
                            VStack(alignment: .leading, spacing: 3) {
                                HStack(alignment: .firstTextBaseline) {
                                    Text(entry.title)
                                        .font(.custom(Aero.productName(.semibold), size: 13.5,
                                                      relativeTo: .subheadline))
                                        .foregroundStyle(Aero.text)
                                    Spacer(minLength: 8)
                                    Text(entry.licence)
                                        .font(.custom(Aero.productName(.medium), size: 10.5,
                                                      relativeTo: .caption2))
                                        .foregroundStyle(Aero.accentStrong)
                                        .multilineTextAlignment(.trailing)
                                }
                                Text(entry.note)
                                    .font(.custom(Aero.productName(.regular), size: 11.5,
                                                  relativeTo: .caption))
                                    .foregroundStyle(Aero.textTertiary)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 12)
                        }
                    }
                }
            }

            GsGroupHeader(title: "Full texts", caption: nil)
            GsGroupCard {
                GsSettingsRow(
                    title: "Read the licence texts",
                    detail: "github.com/GrapseeAgency/the-gs-ai-app · LICENSES",
                    showDivider: false,
                    action: {
                        guard let url = URL(string:
                            "https://github.com/GrapseeAgency/the-gs-ai-app/tree/main/LICENSES")
                        else { return }
                        UIApplication.shared.open(url)
                    }
                ) {
                    GsWellTile(glyph: "book", tone: .peach, size: 28)
                } trailing: {
                    GsSheetChevron()
                }
            }

            Text("Model weights carry their own licences, shown before any download. " +
                 "Nothing here is modified from its published form.")
                .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2))
                .foregroundStyle(Aero.textTertiary)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 4)
                .padding(.top, 8)
        }
    }
}
