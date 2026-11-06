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
                // Was a value row with no destination — a label that reads as a
                // control. The engine's name, its voice, speed and pitch all
                // live one tap away now, and those controls are wired to the
                // synthesizer that actually speaks.
                GsSettingsRow(
                    title: "Speech engine",
                    detail: engineSummary,
                    action: { router.path.append(.speechEngine) }
                ) {
                    GsWellTile(glyph: "wave-sine", tone: .lilac, size: 28)
                } trailing: {
                    GsSheetChevron()
                }
                GsSettingsRow(
                    title: "Haptic confirmation",
                    detail: "A tick when dictation starts and stops",
                    showDivider: false,
                    action: nil
                ) {
                    GsWellTile(glyph: "device-mobile", tone: .mint, size: 28)
                } trailing: {
                    GsGlassSwitch(isOn: Binding(
                        get: { settings.haptics },
                        set: { settings.haptics = $0; GSHaptics.select() }))
                        .accessibilityLabel("Haptic confirmation")
                }
            }

            footnote("Speech is synthesized locally on this device. Audio is not uploaded to GS.")
        }
    }

    /// "Device default" at the middle step (the mock's value row), or the
    /// informative "Natural · device synthesizer" summary once the reader
    /// moves a control.
    private var engineSummary: String {
        // `object(forKey:)` rather than `integer(forKey:)`: the latter returns 0
        // for an unset key, which would make a fresh install report "Slower"
        // while the engine screen (whose @AppStorage default is 1) says
        // "Natural". An unset key means the middle step on both pages.
        let speed = UserDefaults.standard.object(forKey: "gs.tts.rateStep") as? Int ?? 1
        let pitch = UserDefaults.standard.object(forKey: "gs.tts.pitchStep") as? Int ?? 1
        if speed == 1 && pitch == 1 {
            return "Device default"
        }
        let speedLabel = ["Slower", "Natural", "Faster"][min(max(speed, 0), 2)]
        let pitchLabel = ["lower pitch", "natural pitch", "higher pitch"][min(max(pitch, 0), 2)]
        return "\(speedLabel) · \(pitchLabel) · device synthesizer"
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
