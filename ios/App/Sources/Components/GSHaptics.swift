import UIKit

/// GS haptics — the real Taptic Engine vocabulary, wired through the Settings
/// "Haptics" toggle (which previously decorated a checkbox and nothing else).
///
/// The generators are process-wide singletons on purpose: UIKit drops feedback
/// when a generator is deallocated before the engine plays, so transient
/// generators feel intermittent. Prepared on first use; every call site is a
/// one-liner that reads as the interaction it confirms:
///
///     GSHaptics.tap()       // a committed send, a quick-tap route
///     GSHaptics.press()     // a hold threshold crossed, mic opening
///     GSHaptics.success()   // copy, pin, archive, hand-off done
///     GSHaptics.warning()   // destructive commit (delete)
///     GSHaptics.select()    // inline state flip (selection, segment)
enum GSHaptics {

    private static let tapGenerator = UIImpactFeedbackGenerator(style: .light)
    private static let pressGenerator = UIImpactFeedbackGenerator(style: .medium)
    private static let notifyGenerator = UINotificationFeedbackGenerator()
    private static let selectionGenerator = UISelectionFeedbackGenerator()

    /// Pre-arms the Taptic Engine so the first real event has zero latency.
    static func prepare() {
        tapGenerator.prepare()
        pressGenerator.prepare()
        notifyGenerator.prepare()
        selectionGenerator.prepare()
    }

    private static var enabled: Bool {
        SettingsStore.shared.haptics
    }

    /// Light impact — a send that committed, a quick tap that routed.
    static func tap() {
        guard enabled else { return }
        tapGenerator.impactOccurred()
    }

    /// Medium impact — a hold threshold crossed, a mic genuinely opening.
    static func press() {
        guard enabled else { return }
        pressGenerator.impactOccurred()
    }

    /// Success notification — copy/pin/archive/hand-off completed.
    static func success() {
        guard enabled else { return }
        notifyGenerator.notificationOccurred(.success)
    }

    /// Warning notification — a destructive action committed.
    static func warning() {
        guard enabled else { return }
        notifyGenerator.notificationOccurred(.warning)
    }

    /// Selection change — inline state flips inside a persistent control set.
    static func select() {
        guard enabled else { return }
        selectionGenerator.selectionChanged()
    }
}
