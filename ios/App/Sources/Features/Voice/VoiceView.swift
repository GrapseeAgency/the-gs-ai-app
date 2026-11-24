import SwiftUI
import UIKit
import Speech
import AVFoundation
import VoiceRingKit

/// Full-screen voice session — kinetic aurora waveform, live transcript,
/// call controls. Now real: the on-device SpeechRecognizer (via VoiceDictation)
/// drives the transcript, SpeechPlayer reads a finished result back, and the
/// result hands off to a brand-new chat through `.chatPrefill` (PHASE 5 §10:
/// the draft is seeded WITHOUT auto-send — Android parity). Every status
/// line maps to what the engine is actually doing — no decorative listening.
struct VoiceView: View {

    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var router: Router

    @StateObject private var dictation = VoiceDictation()
    @StateObject private var speech = SpeechPlayer()

    var body: some View {
        ZStack {
            Aero.background.ignoresSafeArea()
            VStack(spacing: Aero.Spacing.xl) {
                topBar
                Spacer()
                status
                Spacer()
                transcript
                controls
                engineCaption
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.l)
        }
        .toolbar(.hidden, for: .navigationBar)
        .onAppear {
            // Permission already granted on a previous visit: open the mic
            // straight away. First visit waits for one explicit tap so the
            // system prompt never surprises.
            if SFSpeechRecognizer.authorizationStatus() == .authorized,
               AVAudioSession.sharedInstance().recordPermission == .granted {
                dictation.startSession()
            }
        }
        .onDisappear {
            // The mic can never outlive the screen: leaving voice mode closes
            // the capture and deactivates the audio session.
            dictation.suspendForBackground()
            speech.stop()
        }
    }

    // MARK: Top bar

    private var topBar: some View {
        HStack(spacing: Aero.Spacing.s) {
            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Aero.text)
                    .frame(width: 36, height: 36)
                    .background(Circle().fill(Aero.raised))
                    .overlay(Circle().stroke(Aero.outline, lineWidth: 1))
            }
            .buttonStyle(KineticPressStyle())
            Spacer()
            Text("Talk mode")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
            Spacer()
            Button {
                dictation.suspendForBackground()
                speech.stop()
                router.path.append(.voicePosition)
            } label: {
                Label("Position", systemImage: "dot.radiowaves.left.and.right")
                    .font(Aero.label())
                    .foregroundStyle(Aero.text)
                    .padding(.horizontal, 11)
                    .padding(.vertical, 8)
                    .background(Capsule().fill(Aero.container))
                    .overlay(Capsule().stroke(Aero.outline, lineWidth: 1))
            }
            .buttonStyle(KineticPressStyle())
            .accessibilityLabel("Open Voice Position")
        }
    }

    // MARK: Status

    private var statusLine: String {
        switch dictation.sessionState {
        case .idle: return "Tap the mic and speak"
        case .listening: return "Listening…"
        case .processing: return dictation.transcript.isEmpty ? "Starting the mic…" : "Processing…"
        case .result: return "Got it"
        case .noSpeech: return "Didn't catch that, tap the mic and try again"
        case .denied: return "Microphone access is off, enable it in Settings to speak"
        case .unavailable: return "Speech recognition isn't available right now"
        case .error: return "That didn't come through, tap the mic and try again"
        }
    }

    private var statusIsTrouble: Bool {
        switch dictation.sessionState {
        case .noSpeech, .denied, .unavailable, .error: return true
        default: return false
        }
    }

    private var status: some View {
        VStack(spacing: Aero.Spacing.xs) {
            VoiceRing(
                .mono,
                state: ringState,
                source: .level { dictation.isListening ? Double(dictation.level) : 0 },
                style: ringStyle,
                size: 224
            )
            .accessibilityLabel("Voice ring")
            Text(statusLine)
                .font(Aero.display())
                .foregroundStyle(statusIsTrouble ? Aero.textMuted : Aero.text)
                .multilineTextAlignment(.center)
            if dictation.sessionState == .denied {
                // Permission recovery: a labelled button that actually opens
                // the app's Settings page (Task 85-e I12).
                Button {
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        UIApplication.shared.open(url)
                    }
                } label: {
                    Text("Open Settings")
                        .font(Aero.label())
                        .foregroundStyle(Aero.text)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(Capsule().fill(Aero.container))
                        .overlay(Capsule().stroke(Aero.outline, lineWidth: 1))
                }
                .buttonStyle(KineticPressStyle())
            }
        }
    }

    private var ringState: VoiceState {
        if speech.speakingMessageID == "voice-result" { return .speaking }
        switch dictation.sessionState {
        case .listening: return .listening
        case .processing: return .processing
        default: return .idle
        }
    }

    private var ringStyle: VoiceStyle {
        var style = VoiceStyle()
        style.paused = SettingsStore.shared.animationReduced || SettingsStore.shared.reduceMotion
        return style
    }

    // MARK: Transcript

    private var transcriptText: String {
        switch dictation.sessionState {
        case .result: return dictation.lastResult
        default: return dictation.transcript
        }
    }

    private var transcript: some View {
        AeroCard {
            VStack(spacing: Aero.Spacing.s) {
                Text(transcriptText.isEmpty ? "Your words will appear here." : transcriptText)
                    .font(Aero.body())
                    .foregroundStyle(transcriptText.isEmpty ? Aero.textMuted : Aero.text)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if dictation.sessionState == .result {
                    HStack(spacing: Aero.Spacing.s) {
                        AeroChip(text: "Send to chat", selected: true) {
                            // The engine has finished with the mic by now;
                            // the chat opens on top, voice mode stays beneath.
                            // PHASE 5 handoff harmonization (§10): the
                            // transcript seeds the composer draft WITHOUT
                            // auto-send — Android parity. The reader stays
                            // in control of the send.
                            GSHaptics.success()
                            router.path.append(.chatPrefill(dictation.lastResult))
                        }
                        AeroChip(text: "Copy", selected: false) {
                            UIPasteboard.general.string = dictation.lastResult
                            GSHaptics.success()
                        }
                        AeroChip(text: "Try again", selected: false) {
                            GSHaptics.tap()
                            dictation.startSession()
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
    }

    // MARK: Controls

    private var controls: some View {
        HStack(spacing: 24) {
            controlButton(
                dictation.isListening ? "mic" : "mic.slash",
                dimmed: !dictation.isListening,
                accessibilityLabel: dictation.isListening ? "Stop listening" : "Start listening"
            ) {
                if dictation.isListening {
                    dictation.stopSession()
                } else {
                    dictation.startSession()
                }
            }
            controlButton(
                "phone.down.fill",
                tint: Aero.onError,
                background: Aero.danger,
                accessibilityLabel: "Close voice mode"
            ) {
                dismiss()
            }
            controlButton(
                "speaker.wave.2.fill",
                dimmed: speech.speakingMessageID != "voice-result",
                accessibilityLabel: speech.speakingMessageID == "voice-result" ? "Stop reading aloud" : "Read result aloud"
            ) {
                guard dictation.sessionState == .result, !dictation.lastResult.isEmpty else { return }
                speech.toggle(messageID: "voice-result", text: dictation.lastResult)
            }
        }
    }

    private func controlButton(
        _ icon: String,
        tint: Color = Aero.text,
        background: Color = Aero.raised,
        dimmed: Bool = false,
        accessibilityLabel: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 20, weight: .medium))
                .foregroundStyle(tint)
                .opacity(dimmed ? 0.55 : 1)
                .frame(width: 64, height: 64)
                .background(Circle().fill(background))
                .overlay(Circle().stroke(Aero.outline, lineWidth: 1))
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityLabel(accessibilityLabel)
    }

    // MARK: Engine caption (the honest replacement for the fake voice picker)

    private var engineCaption: some View {
        Text("Speech stays on this device · \(Locale.current.identifier)")
            .font(Aero.caption())
            .foregroundStyle(Aero.textMuted)
            .frame(maxWidth: .infinity)
    }
}
