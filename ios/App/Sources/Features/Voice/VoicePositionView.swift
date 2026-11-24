import AVFoundation
import SwiftUI
import UIKit
import VoicePositionKit
import VoiceRingKit

/// Local voice-position sensing. The speech-to-text and read-aloud experience
/// remains available from the Talk button; this surface only reads the
/// microphone's stereo image and never transcribes or uploads audio.
@MainActor
struct VoicePositionView: View {

    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var router: Router
    @ObservedObject private var playback = SpeechPlaybackState.shared

    @State private var meter = VoiceMeter()
    @State private var position = VoicePosition()
    @State private var isStarting = false
    @State private var microphoneError: String?
    @State private var calibratingSide: DirectionSide?
    @State private var calibrationMessage: String?
    @State private var calibrationTask: Task<Void, Never>?
    @State private var confirmReset = false

    private let calibrationKey = "gs.voicePosition.calibrations.v1"

    var body: some View {
        ZStack {
            Aero.background.ignoresSafeArea()
            ScrollView {
                VStack(spacing: Aero.Spacing.m) {
                    topBar
                    titleBlock
                    positionCard
                    readingsCard
                    calibrationCard
                    privacyNote
                }
                .padding(.horizontal, Aero.Spacing.m)
                .padding(.top, Aero.Spacing.s)
                .padding(.bottom, Aero.Spacing.xl)
            }
        }
        .toolbar(.hidden, for: .navigationBar)
        .task {
            restoreCalibration()
            position.readsDistance = !playback.isSpeaking
            // Preserve the existing voice screen's permission convention: a
            // returning user with microphone access starts immediately, while
            // a first visit waits for an explicit tap before requesting it.
            if AVAudioSession.sharedInstance().recordPermission == .granted {
                await startListening()
            }
        }
        .onChange(of: position.calibrations) { calibrations in
            persist(calibrations)
        }
        .onChange(of: playback.isSpeaking) { isSpeaking in
            position.readsDistance = !isSpeaking
        }
        .onDisappear {
            calibrationTask?.cancel()
            position.stop()
        }
        .confirmationDialog(
            "Reset voice calibration?",
            isPresented: $confirmReset,
            titleVisibility: .visible
        ) {
            Button("Reset calibration", role: .destructive) {
                position.resetCalibration()
                calibrationMessage = "Calibration cleared for every phone pose."
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This removes the saved left, ahead and right reference points on this device.")
        }
    }

    private var topBar: some View {
        HStack(spacing: Aero.Spacing.s) {
            Button { dismiss() } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Aero.text)
                    .frame(width: 36, height: 36)
                    .background(Circle().fill(Aero.raised))
                    .overlay(Circle().stroke(Aero.outline, lineWidth: 1))
            }
            .buttonStyle(KineticPressStyle())
            Spacer()
            Text("Voice Position")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
            Spacer()
            Button {
                calibrationTask?.cancel()
                position.stop()
                router.path.append(.voice)
            } label: {
                Label("Talk", systemImage: "waveform")
                    .font(Aero.label())
                    .foregroundStyle(Aero.text)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Capsule().fill(Aero.container))
                    .overlay(Capsule().stroke(Aero.outline, lineWidth: 1))
            }
            .buttonStyle(KineticPressStyle())
            .accessibilityLabel("Open speech mode")
        }
    }

    private var titleBlock: some View {
        VStack(spacing: Aero.Spacing.xs) {
            Text("Where is the voice?")
                .font(Aero.display())
                .foregroundStyle(Aero.text)
                .multilineTextAlignment(.center)
            Text("Calibrate left, ahead and right for this phone and how it is held.")
                .font(Aero.body())
                .foregroundStyle(Aero.textMuted)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.top, Aero.Spacing.s)
    }

    private var ringStyle: VoiceStyle {
        var style = VoiceStyle()
        style.paused = SettingsStore.shared.animationReduced || SettingsStore.shared.reduceMotion
        return style
    }

    private var positionCard: some View {
        AeroCard {
            VStack(spacing: Aero.Spacing.s) {
                VoiceRing(
                    .mono,
                    state: meter.isRunning ? .listening : .idle,
                    source: .meter(meter),
                    style: ringStyle,
                    direction: position.reading == nil ? nil : position.pan,
                    size: 224
                )
                .frame(maxWidth: .infinity)
                .accessibilityLabel("Voice position orb")

                Text(directionTitle)
                    .font(Aero.display())
                    .foregroundStyle(Aero.text)
                    .multilineTextAlignment(.center)
                    .accessibilityAddTraits(.updatesFrequently)

                Text(statusDescription)
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)

                VoicePositionPanTrack(pan: position.reading == nil ? nil : position.pan)
                    .frame(height: 28)
                    .padding(.horizontal, 4)

                Button(action: toggleListening) {
                    Label(
                        meter.isRunning ? "Stop sensing" : (isStarting ? "Opening microphone…" : "Start sensing"),
                        systemImage: meter.isRunning ? "pause.fill" : "mic.fill"
                    )
                    .font(Aero.label())
                    .foregroundStyle(Aero.onAccent)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 13)
                    .background(Capsule().fill(Aero.accent))
                }
                .buttonStyle(KineticPressStyle())
                .disabled(isStarting)

                if let microphoneError {
                    Text(microphoneError)
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                        .multilineTextAlignment(.center)
                    if AVAudioSession.sharedInstance().recordPermission == .denied {
                        Button("Open Settings") {
                            if let url = URL(string: UIApplication.openSettingsURLString) {
                                UIApplication.shared.open(url)
                            }
                        }
                        .font(Aero.label())
                        .foregroundStyle(Aero.accent)
                    }
                }
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.vertical, Aero.Spacing.m)
        }
    }

    private var readingsCard: some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                HStack {
                    readingTile(
                        title: "Pose",
                        value: position.pose == .desk ? "On desk" : "In hand",
                        symbol: position.pose == .desk ? "rectangle" : "iphone"
                    )
                    readingTile(
                        title: "Microphone",
                        value: meter.isRunning ? (meter.isStereo ? "Stereo" : "Mono / unavailable") : "Not active",
                        symbol: meter.isStereo ? "mic.and.signal.meter" : "mic"
                    )
                }

                VStack(alignment: .leading, spacing: 7) {
                    HStack {
                        Text("Loudness")
                        Spacer()
                        Text(meter.isRunning ? "\(Int(position.level * 100))%" : "—")
                            .monospacedDigit()
                    }
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
                    GeometryReader { geometry in
                        ZStack(alignment: .leading) {
                            Capsule().fill(Aero.outline)
                            Capsule()
                                .fill(LinearGradient(colors: Aero.aurora, startPoint: .leading, endPoint: .trailing))
                                .frame(width: geometry.size.width * CGFloat(min(max(position.level, 0), 1)))
                        }
                    }
                    .frame(height: 8)
                    .accessibilityLabel("Microphone loudness")
                    .accessibilityValue("\(Int(position.level * 100)) percent")
                }
                .padding(.top, 2)

                Divider().overlay(Aero.outline)

                HStack(alignment: .top, spacing: Aero.Spacing.s) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Distance")
                            .font(Aero.caption())
                            .foregroundStyle(Aero.textMuted)
                        Text(distanceTitle)
                            .font(Aero.label())
                            .foregroundStyle(Aero.text)
                        Text(distanceDetail)
                            .font(Aero.caption())
                            .foregroundStyle(Aero.textMuted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 8)
                    if let probabilities = position.probabilities, position.reading != nil {
                        HStack(alignment: .bottom, spacing: 8) {
                            probabilityBar("L", value: probabilities.x)
                            probabilityBar("A", value: probabilities.y)
                            probabilityBar("R", value: probabilities.z)
                        }
                        .frame(height: 48)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel("Direction model confidence")
                    }
                }

                if meter.isStereo, let input = meter.stereoInput {
                    Text(input)
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                        .lineLimit(2)
                }
            }
            .padding(Aero.Spacing.m)
        }
    }

    private var calibrationCard: some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                HStack(alignment: .firstTextBaseline) {
                    Text("Calibrate this phone")
                        .font(Aero.label())
                        .foregroundStyle(Aero.text)
                    Spacer()
                    Button("Reset") { confirmReset = true }
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                        .disabled(calibratingSide != nil || position.calibrations.values.allSatisfy { $0.points.isEmpty })
                }

                Text("Keep the phone in its current pose. Stand at each marked side and speak naturally for a few seconds.")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
                    .fixedSize(horizontal: false, vertical: true)

                HStack(spacing: Aero.Spacing.xs) {
                    calibrationButton(.left)
                    calibrationButton(.ahead)
                    calibrationButton(.right)
                }

                if let calibratingSide {
                    Text("Speak from \(calibratingSide.rawValue.capitalized)…")
                        .font(Aero.caption())
                        .foregroundStyle(Aero.text)
                }
                if let calibrationMessage {
                    Text(calibrationMessage)
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Text("Saved separately for the hand and desk poses.")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
            }
            .padding(Aero.Spacing.m)
        }
    }

    private var privacyNote: some View {
        Text("Audio is analysed on this device, not transcribed or uploaded. Direction needs stereo input and calibration or the optional model. The optional model was trained on iPhone 15 Pro, so calibrate other phones. Distance is shown only when a model supports the current pose; loudness alone is never used as distance.")
            .font(Aero.caption())
            .foregroundStyle(Aero.textMuted)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 4)
    }

    private var directionTitle: String {
        guard meter.isRunning else { return "Ready to listen" }
        guard meter.isStereo else { return "Direction unavailable" }
        guard position.reading != nil, let side = position.side else {
            if !position.isModelLoaded && !position.calibration.isReady {
                return "Calibrate left and right"
            }
            return "Listening for a voice"
        }
        switch side {
        case .left: return "Left"
        case .ahead: return "Ahead"
        case .right: return "Right"
        }
    }

    private var statusDescription: String {
        if isStarting { return "Opening the microphone…" }
        guard meter.isRunning else { return "Microphone is off until you start sensing." }
        guard meter.isStereo else {
            return "This audio route is mono. Try the phone's built-in microphones; Bluetooth and some devices do not expose stereo input."
        }
        if position.reading != nil {
            return "Live stereo reading · pan \(String(format: "%+.2f", position.pan ?? 0))"
        }
        if !position.isModelLoaded && !position.calibration.isReady {
            return "No model is bundled. Calibrate the left and right sides to enable direction."
        }
        return "Waiting for a voiced frame; no direction is shown during silence."
    }

    private var distanceTitle: String {
        guard meter.isStereo else { return "Unavailable" }
        guard position.hasDistanceModel else { return "Unavailable" }
        guard position.readsDistance else { return "Paused" }
        guard position.reading != nil else { return "Waiting for voice" }
        guard let distance = position.distance else { return "Waiting for voice" }
        switch distance {
        case .small: return "Near"
        case .large: return "Far"
        }
    }

    private var distanceDetail: String {
        guard meter.isStereo else { return "Stereo input required" }
        guard position.hasDistanceModel else {
            return position.isModelLoaded
                ? "No distance model for the current pose"
                : "VoicePosition.mfxl is not installed"
        }
        guard position.readsDistance else { return "Distance updates paused while GS reads aloud" }
        guard position.reading != nil else { return "No distance reading during silence" }
        guard let belief = position.distanceBelief else { return "No distance reading yet" }
        return "Model belief \(Int(belief * 100))% · categorical only"
    }

    private func readingTile(title: String, value: String, symbol: String) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Label(title, systemImage: symbol)
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
            Text(value)
                .font(Aero.label())
                .foregroundStyle(Aero.text)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 14).fill(Aero.container))
    }

    private func probabilityBar(_ label: String, value: Double) -> some View {
        VStack(spacing: 3) {
            Capsule()
                .fill(Aero.accent)
                .frame(width: 8, height: 24 * CGFloat(min(max(value, 0), 1)))
            Text(label)
                .font(.system(size: 9, weight: .medium))
                .foregroundStyle(Aero.textMuted)
        }
        .frame(width: 18, height: 46, alignment: .bottom)
        .accessibilityLabel("\(label): \(Int(value * 100)) percent")
    }

    private func calibrationButton(_ side: DirectionSide) -> some View {
        let ready = position.calibration.points[side] != nil
        return Button {
            beginCalibration(side)
        } label: {
            VStack(spacing: 4) {
                Image(systemName: ready ? "checkmark.circle.fill" : "dot.circle")
                    .font(.system(size: 14, weight: .medium))
                Text(side.rawValue.capitalized)
                    .font(Aero.caption())
            }
            .foregroundStyle(ready ? Aero.accent : Aero.text)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(RoundedRectangle(cornerRadius: 12).fill(Aero.container))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Aero.outline, lineWidth: 1))
        }
        .buttonStyle(KineticPressStyle())
        .disabled(!meter.isRunning || !meter.isStereo || calibratingSide != nil)
        .accessibilityLabel("Calibrate \(side.rawValue)")
    }

    private func toggleListening() {
        if meter.isRunning {
            calibrationTask?.cancel()
            calibrationTask = nil
            calibratingSide = nil
            position.stop()
        } else {
            Task { await startListening() }
        }
    }

    private func startListening() async {
        guard !meter.isRunning, !isStarting else { return }
        isStarting = true
        microphoneError = nil
        do {
            try await position.start(meter)
            isStarting = false
            if !meter.isStereo {
                microphoneError = "Stereo input is unavailable on this route; direction and distance will not be reported."
            }
        } catch let error as VoiceMeter.MeterError {
            isStarting = false
            switch error {
            case .permissionDenied:
                microphoneError = "Microphone access is off. Allow it in Settings to use Voice Position."
            case .noInput:
                microphoneError = "No microphone input is available on this device."
            }
        } catch {
            isStarting = false
            microphoneError = "Could not start the microphone: \(error.localizedDescription)"
        }
    }

    private func beginCalibration(_ side: DirectionSide) {
        guard meter.isRunning else {
            calibrationMessage = "Start sensing before calibrating."
            return
        }
        guard meter.isStereo else {
            calibrationMessage = "Calibration needs a stereo microphone input."
            return
        }
        guard calibratingSide == nil else { return }
        calibratingSide = side
        calibrationMessage = nil
        calibrationTask = Task {
            let cues = await position.calibrate(side, voiceSeconds: 3, timeout: 12)
            guard !Task.isCancelled else { return }
            calibratingSide = nil
            calibrationTask = nil
            guard cues != nil else {
                calibrationMessage = "Not enough voiced stereo audio was heard. Try again and speak clearly."
                return
            }
            switch position.calibration.issue {
            case .sidesAlike:
                calibrationMessage = "Left and right sound too alike. Move farther apart and recalibrate those sides."
            case .aheadOutside:
                calibrationMessage = "Ahead should fall between left and right. Recalibrate while keeping the phone still."
            case nil:
                calibrationMessage = "\(side.rawValue.capitalized) saved for the \(position.pose == .desk ? "desk" : "hand") pose."
            }
        }
    }

    private func restoreCalibration() {
        guard let data = UserDefaults.standard.data(forKey: calibrationKey),
              let saved = try? JSONDecoder().decode([DevicePose: DirectionCalibration].self, from: data) else {
            return
        }
        position.calibrations = saved
    }

    private func persist(_ calibrations: [DevicePose: DirectionCalibration]) {
        guard let data = try? JSONEncoder().encode(calibrations) else { return }
        UserDefaults.standard.set(data, forKey: calibrationKey)
    }
}

private struct VoicePositionPanTrack: View {
    let pan: Double?

    var body: some View {
        VStack(spacing: 2) {
            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    Capsule().fill(Aero.outline).frame(height: 2)
                    ForEach(0..<3, id: \.self) { index in
                        Circle()
                            .fill(Aero.textMuted.opacity(0.55))
                            .frame(width: 4, height: 4)
                            .offset(x: geometry.size.width * CGFloat(index) / 2 - 2)
                    }
                    if let pan {
                        Circle()
                            .fill(Aero.accent)
                            .frame(width: 12, height: 12)
                            .shadow(color: Aero.accent.opacity(0.4), radius: 6)
                            .offset(x: (geometry.size.width - 12) * CGFloat(min(max((pan + 1) / 2, 0), 1)))
                    }
                }
                .frame(maxHeight: .infinity)
            }
            HStack {
                Text("LEFT")
                Spacer()
                Text("AHEAD")
                Spacer()
                Text("RIGHT")
            }
            .font(.system(size: 8, weight: .semibold, design: .rounded))
            .foregroundStyle(Aero.textMuted)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Voice pan")
        .accessibilityValue(pan.map { String(format: "%+.2f", $0) } ?? "Unavailable")
    }
}
