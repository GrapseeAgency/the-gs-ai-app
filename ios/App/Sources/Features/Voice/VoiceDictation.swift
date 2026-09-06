import Foundation
import AVFoundation
import Speech

/**
 * Press-and-hold dictation for the hero orb — live partial transcripts feed
 * the hero line while the finger is down, and the best available text hands
 * off to the chat composer on release. Every failure path (permission denied,
 * no recognizer, engine hiccup) dissolves quietly back to idle: nothing is
 * ever surfaced as an error.
 */
final class VoiceDictation: ObservableObject {

    /// Halo/UI state — true from the moment the hold triggers until handoff.
    @Published private(set) var isListening = false
    /// Live partial transcription while listening.
    @Published private(set) var transcript = ""

    /// Fires once per hold with the best available text ("" = nothing said —
    /// the call site quietly ignores that).
    var onFinish: ((String) -> Void)?

    private let audioEngine = AVAudioEngine()
    private var recognizer: SFSpeechRecognizer?
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private var bestText = ""
    private var holdActive = false
    private var starting = false
    private var handedOff = false

    /// Finger went down past the hold threshold.
    func begin() {
        holdActive = true
        handedOff = false
        bestText = ""
        transcript = ""
        isListening = true
        requestPermissionsAndStart()
    }

    /// Finger lifted — stop capturing and hand the text over shortly after
    /// (a short grace period lets the final result land for the last words).
    func end() {
        holdActive = false
        request?.endAudio()
        audioEngine.stop()
        audioEngine.inputNode.removeTap(onBus: 0)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) { [weak self] in
            self?.finishHandoff()
        }
    }

    // MARK: Internals

    private func requestPermissionsAndStart() {
        guard holdActive else {
            isListening = false
            return
        }

        // One prompt at a time; each resolution re-evaluates both states.
        if SFSpeechRecognizer.authorizationStatus() == .notDetermined {
            SFSpeechRecognizer.requestAuthorization { [weak self] _ in
                DispatchQueue.main.async { self?.requestPermissionsAndStart() }
            }
            return
        }
        if AVAudioSession.sharedInstance().recordPermission == .undetermined {
            AVAudioSession.sharedInstance().requestRecordPermission { [weak self] _ in
                DispatchQueue.main.async { self?.requestPermissionsAndStart() }
            }
            return
        }

        guard SFSpeechRecognizer.authorizationStatus() == .authorized,
              AVAudioSession.sharedInstance().recordPermission == .granted else {
            // Denied — quiet dissolve, no dialogs, no error copy.
            isListening = false
            return
        }
        startEngine()
    }

    private func startEngine() {
        guard holdActive, task == nil, !starting else { return }
        starting = true

        let speechRecognizer = SFSpeechRecognizer()
        guard speechRecognizer?.isAvailable == true else {
            starting = false
            isListening = false
            return
        }
        recognizer = speechRecognizer

        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement, options: .duckOthers)
            try session.setActive(true, options: .notifyOthersOnDeactivation)

            let bufferRequest = SFSpeechAudioBufferRecognitionRequest()
            bufferRequest.shouldReportPartialResults = true
            request = bufferRequest

            let input = audioEngine.inputNode
            let format = input.outputFormat(forBus: 0)
            input.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
                self?.request?.append(buffer)
            }
            audioEngine.prepare()
            try audioEngine.start()

            task = speechRecognizer.recognitionTask(with: bufferRequest) { [weak self] result, error in
                DispatchQueue.main.async {
                    guard let self else { return }
                    if let result {
                        let text = result.bestTranscription.formattedString
                        if !text.isEmpty {
                            self.bestText = text
                            self.transcript = text
                        }
                        if result.isFinal { self.finishHandoff() }
                    } else if error != nil {
                        // Hiccup mid-hold — keep whatever partials we already
                        // have and drop back to idle quietly.
                        isListening = false
                    }
                }
            }
            starting = false
        } catch {
            teardownCapture()
            isListening = false
        }
    }

    private func finishHandoff() {
        guard !handedOff else { return }
        handedOff = true
        let text = bestText
        teardownCapture()
        onFinish?(text)
    }

    private func teardownCapture() {
        task?.finish()
        task = nil
        request = nil
        audioEngine.stop()
        audioEngine.inputNode.removeTap(onBus: 0)
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        isListening = false
        transcript = ""
    }
}
