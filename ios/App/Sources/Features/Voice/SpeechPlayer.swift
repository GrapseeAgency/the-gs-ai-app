import AVFoundation

/// Read-aloud for assistant messages — on-device AVSpeechSynthesizer, zero
/// error surface: devices without a voice simply stay silent, never alert.
final class SpeechPlayer: NSObject, ObservableObject, AVSpeechSynthesizerDelegate {

    @Published var speakingMessageID: String?

    private let synthesizer = AVSpeechSynthesizer()

    override init() {
        super.init()
        synthesizer.delegate = self
    }

    /// First tap speaks, second tap on the same bubble stops playback.
    func toggle(messageID: String, text: String) {
        if speakingMessageID == messageID {
            stop()
            return
        }
        stop()
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: nil) // system default voice
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate
        synthesizer.speak(utterance)
        speakingMessageID = messageID
    }

    func stop() {
        synthesizer.stopSpeaking(at: .immediate)
        speakingMessageID = nil
    }

    // MARK: AVSpeechSynthesizerDelegate (callbacks arrive off-main)

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        DispatchQueue.main.async { self.speakingMessageID = nil }
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        DispatchQueue.main.async { self.speakingMessageID = nil }
    }
}
