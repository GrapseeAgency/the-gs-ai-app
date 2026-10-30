import SwiftUI

/// Voice & Speech — the iOS twin of Android's `VoiceSettingsScreen`, which was
/// one of the settings pages registered as a route with nothing reaching it.
///
/// Every row is backed by something this build does: voice mode is the real
/// session in `VoiceView`, the engine label is the system synthesizer this app
/// actually uses, and the haptic toggle is the same `settings.haptics` the
/// rest of the app reads. The footnote says where the audio goes (nowhere).
struct VoiceSpeechView: View {

    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var router: Router
    @ObservedObject private var settings = SettingsStore.shared

    var body: some View {
        GsSettingsPage(
            title: "Voice & Speech",
            subtitle: "Listen, speak, and keep your hands free.",
            onBack: { dismiss() }
        ) {
            GsGroupCard {
                VStack(spacing: 6) {
                    GsArtwork(art: .voice, size: 104, label: "Read aloud")
                    Text("Read aloud")
                        .font(.custom(Aero.productName(.semibold), size: 15, relativeTo: .headline))
                        .foregroundStyle(Aero.text)
                    Text("Answers can be spoken with your device's speech engine. Voice mode listens when you tap the mic.")
                        .font(.custom(Aero.productName(.regular), size: 12.5, relativeTo: .caption))
                        .foregroundStyle(Aero.textTertiary)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 18)
                .padding(.vertical, 18)
            }

            GsGroupHeader(title: "Voice", caption: nil)
            GsGroupCard {
                GsSettingsRow(
                    title: "Open voice mode",
                    detail: "Speak a question instead of typing it",
                    action: { router.path.append(.voice) }
                ) {
                    GsWellTile(glyph: "microphone", tone: .peach, size: 28)
                } trailing: {
                    GsSheetChevron()
                }
                // A value, not a control: the synthesizer is the system
                // default voice, and naming an engine we do not let the reader
                // pick would be the dead-control failure this repo removes.
                GsSettingsRow(
                    title: "Speech engine",
                    detail: "Device default",
                    action: nil
                ) {
                    GsWellTile(glyph: "wave-sine", tone: .lilac, size: 28)
                } trailing: {
                    EmptyView()
                }
                GsSettingsRow(
                    title: "Haptic confirmation",
                    detail: "A tick when dictation starts and stops",
                    showDivider: false,
                    action: nil
                ) {
                    GsWellTile(glyph: "device-mobile", tone: .mint, size: 28)
                } trailing: {
                    Toggle("", isOn: Binding(
                        get: { settings.haptics },
                        set: { settings.haptics = $0; GSHaptics.select() }))
                        .labelsHidden()
                        .tint(Aero.accent)
                        .accessibilityLabel("Haptic confirmation")
                }
            }

            footnote("Speech is synthesized locally on this device. Audio is not uploaded to GS.")
        }
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
