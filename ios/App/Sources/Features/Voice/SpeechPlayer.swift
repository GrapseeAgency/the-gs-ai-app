import AVFoundation

/// Read-aloud for assistant messages — on-device AVSpeechSynthesizer, zero
/// error surface: devices without a voice simply stay silent, never alert.
///
/// Audio session contract (Task 85-e): playback under `.playback` +
/// `.spokenAudio` with `.duckOthers`, so the silent switch never mutes
/// read-aloud and background audio ducks instead of stopping. The session is
/// deactivated (notifying others) once every outstanding utterance finishes —
/// counted in the delegate, since each utterance delivers exactly one
/// didFinish/didCancel. Interruptions (calls, Siri, alarms) stop speaking.
final class SpeechPlayer: NSObject, ObservableObject, AVSpeechSynthesizerDelegate {

    @Published var speakingMessageID: String?

    private let synthesizer = AVSpeechSynthesizer()
    /// Utterances handed to the synthesizer that have not yet delivered their
    /// terminal delegate callback. Mutated on the main queue only.
    private var outstandingUtterances = 0
    private var interruptionObserver: NSObjectProtocol?

    override init() {
        super.init()
        synthesizer.delegate = self
        interruptionObserver = NotificationCenter.default.addObserver(
            forName: AVAudioSession.interruptionNotification,
            object: AVAudioSession.sharedInstance(),
            queue: .main
        ) { [weak self] notification in
            // Only .began stops speaking — .ended leaves the user in control.
            let typeRaw = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt
            if typeRaw == AVAudioSession.InterruptionType.began.rawValue {
                self?.stop()
            }
        }
    }

    deinit {
        if let token = interruptionObserver {
            NotificationCenter.default.removeObserver(token)
        }
    }

    /// First tap speaks, second tap on the same bubble stops playback.
    func toggle(messageID: String, text: String) {
        if speakingMessageID == messageID {
            stop()
            return
        }
        stop()
        activateSession()
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: nil) // system default voice
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate
        outstandingUtterances += 1
        synthesizer.speak(utterance)
        speakingMessageID = messageID
    }

    func stop() {
        synthesizer.stopSpeaking(at: .immediate)
        speakingMessageID = nil
    }

    // MARK: Audio session

    /// Read-aloud is deliberate speech: .playback ignores the silent switch,
    /// .spokenAudio + .duckOthers pauses/ducks background audio politely.
    private func activateSession() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .spokenAudio, options: [.duckOthers])
        try? session.setActive(true)
    }

    /// Hand the audio focus back once the last utterance ends — the system
    /// notification lets paused apps (music, podcasts) resume.
    private func deactivateSession() {
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    // MARK: AVSpeechSynthesizerDelegate (callbacks arrive off-main)

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        utteranceEnded()
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        utteranceEnded()
    }

    /// One terminal callback per utterance — balance the outstanding count on
    /// the main queue and drop the session when nothing is speaking.
    private func utteranceEnded() {
        DispatchQueue.main.async {
            self.outstandingUtterances = max(0, self.outstandingUtterances - 1)
            if self.outstandingUtterances == 0 && !self.synthesizer.isSpeaking {
                self.deactivateSession()
            }
        }
    }
}
