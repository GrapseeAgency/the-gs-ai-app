import SwiftUI
import UIKit
import Speech
import AVFoundation

/// Full-screen voice session — kinetic aurora waveform, live transcript,
/// call controls. Now real: the on-device SpeechRecognizer (via VoiceDictation)
/// drives the transcript, SpeechPlayer reads a finished result back, and the
/// result hands off to a brand-new chat through `.chatAutoSend`. Every status
/// line maps to what the engine is actually doing — no decorative listening.
struct VoiceView: View {

    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var router: Router

    @StateObject private var dictation = VoiceDictation()
    @StateObject private var speech = SpeechPlayer()

    private let barCount = 24

    /// Deterministic waveform targets (24…56pt) — stable between runs.
    private var heights: [CGFloat] {
        (0..<barCount).map { index in
            CGFloat(24 + abs(sin(Double(index) * 1.37)) * 32)
        }
    }

    var body: some View {
        ZStack {
            Aero.background.ignoresSafeArea()
            VStack(spacing: Aero.Spacing.xl) {
                topBar
                Spacer()
                status
                waveform
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
            Text("Voice mode")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
        }
    }

    // MARK: Status

    private var statusLine: String {
        switch dictation.sessionState {
        case .idle: return "Tap the mic and speak"
        case .listening: return "Listening…"
        case .processing: return dictation.transcript.isEmpty ? "Starting the mic…" : "Processing…"
        case .result: return "Got it"
        case .noSpeech: return "Didn't catch that — tap the mic and try again"
        case .denied: return "Microphone access is off — enable it in Settings to speak"
        case .unavailable: return "Speech recognition isn't available right now"
        case .error: return "That didn't come through — tap the mic and try again"
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
            // Phase 4: the activity orb mirrors the recognizer's REAL phase —
            // "Listening…" while the mic is live, "Working…" while it
            // processes. Idle, error and permission states show no orb: an
            // absent state is never faked.
            if let orbState = orbStateForVoiceSession(dictation.sessionState) {
                ThinkingOrbView(
                    state: orbState,
                    size: .standard,
                    contentDescription: orbState.label
                )
                .padding(.bottom, Aero.Spacing.xs)
            }
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

    // MARK: Waveform (aurora — the one AI-active moment)

    private var waveform: some View {
        HStack(spacing: 4) {
            ForEach(0..<barCount, id: \.self) { index in
                Capsule()
                    .fill(LinearGradient(colors: Aero.aurora, startPoint: .top, endPoint: .bottom))
                    .frame(width: 4, height: dictation.isListening ? heights[index] : 8)
                    .animation(
                        SettingsStore.shared.animationReduced
                            ? nil
                            : .easeInOut(duration: 0.5)
                                .repeatForever(autoreverses: true)
                                .delay(Double(index) * 0.04),
                        value: dictation.isListening
                    )
            }
        }
        .frame(height: 56)
        .accessibilityHidden(true)
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
                            // Phase 2: the transcript AUTO-SENDS as the first
                            // message of a new chat (.chatAutoSend) — voice is
                            // a conversation, not a draft delivery.
                            GSHaptics.success()
                            router.path.append(.chatAutoSend(dictation.lastResult))
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
