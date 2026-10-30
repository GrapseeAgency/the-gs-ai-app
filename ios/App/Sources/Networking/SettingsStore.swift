import SwiftUI
import UIKit
import Combine

/// The settings contract — every switch, chip and slider on the Settings
/// screen is a real, remembered preference. State lives in @Published
/// properties (observable from any view) and writes through to UserDefaults
/// the moment it changes, so nothing forgets between launches. init()
/// hydrates from disk once, before any UI reads the store. Preference
/// persistence is local-first: switches whose subsystems arrive later (push,
/// passcode, memory) already remember their declared value — the same
/// client-held preference shape the benchmark apps use until their server
/// half is reachable.
@MainActor
final class SettingsStore: ObservableObject {

    static let shared = SettingsStore()

    private let defaults = UserDefaults.standard

    // Appearance
    @Published var theme: String { didSet { defaults.set(theme, forKey: K.theme) } }
    @Published var reduceAnimations: Bool { didSet { defaults.set(reduceAnimations, forKey: K.reduceAnimations); propagateFlags() } }
    // Chat
    @Published var enterToSend: Bool { didSet { defaults.set(enterToSend, forKey: K.enterToSend) } }
    @Published var autoTitle: Bool { didSet { defaults.set(autoTitle, forKey: K.autoTitle) } }
    // AI
    @Published var memory: Bool { didSet { defaults.set(memory, forKey: K.memory) } }
    @Published var personalisation: Bool { didSet { defaults.set(personalisation, forKey: K.personalisation) } }

    /// Commit 1(b): answer from the on-device engine before the network.
    ///
    /// Default OFF, deliberately, and for the same reason as on Android: local is
    /// not uniformly faster, it burns battery, and a default that silently moves
    /// a user's chat data from a server to the device is a decision the user has
    /// to make. With it off, `ChatViewModel` takes exactly the path it took
    /// before this existed.
    @Published var preferLocal: Bool { didSet { defaults.set(preferLocal, forKey: K.preferLocal) } }
    @Published var reasoning: String { didSet { defaults.set(reasoning, forKey: K.reasoning) } }

    // Model Selection & Reasoning Effort — docs/reference/pending/
    // model-selection.png. Keys and defaults mirror Android's SettingsStore
    // (`settings.preferredModel`, `.reasoningEffort`, `.rememberChoices`), so
    // a cross-platform export carries one vocabulary.
    @Published var preferredModel: String { didSet { defaults.set(preferredModel, forKey: K.preferredModel) } }
    @Published var reasoningEffort: String { didSet { defaults.set(reasoningEffort, forKey: K.reasoningEffort) } }
    @Published var rememberChoices: Bool { didSet { defaults.set(rememberChoices, forKey: K.rememberChoices) } }
    // Privacy
    @Published var helpImprove: Bool { didSet { defaults.set(helpImprove, forKey: K.helpImprove) } }
    // Security
    @Published var appPasscode: Bool { didSet { defaults.set(appPasscode, forKey: K.appPasscode) } }
    @Published var biometricUnlock: Bool { didSet { defaults.set(biometricUnlock, forKey: K.biometricUnlock) } }
    /// Epoch millis of the last successful biometric check; 0 when none.
    @Published var lastSecurityCheckMillis: Double { didSet { defaults.set(lastSecurityCheckMillis, forKey: K.lastSecurityCheck) } }
    // Notifications
    @Published var pushNotifications: Bool { didSet { defaults.set(pushNotifications, forKey: K.pushNotifications) } }
    @Published var taskCompleted: Bool { didSet { defaults.set(taskCompleted, forKey: K.taskCompleted) } }
    @Published var assistantUpdates: Bool { didSet { defaults.set(assistantUpdates, forKey: K.assistantUpdates) } }
    @Published var productNews: Bool { didSet { defaults.set(productNews, forKey: K.productNews) } }
    // Notification kinds + quiet hours — the Notifications & Alerts sheet.
    // Persisted now, read by the notice path when it lands; same precedent as
    // the push switch above. Keys mirror Android's so a cross-platform export
    // carries one vocabulary.
    @Published var noticeMessages: Bool { didSet { defaults.set(noticeMessages, forKey: K.noticeMessages) } }
    @Published var noticeAiUpdates: Bool { didSet { defaults.set(noticeAiUpdates, forKey: K.noticeAiUpdates) } }
    @Published var noticeTips: Bool { didSet { defaults.set(noticeTips, forKey: K.noticeTips) } }
    @Published var noticeAccountActivity: Bool { didSet { defaults.set(noticeAccountActivity, forKey: K.noticeAccountActivity) } }
    @Published var noticeProductUpdates: Bool { didSet { defaults.set(noticeProductUpdates, forKey: K.noticeProductUpdates) } }
    @Published var noticeInApp: Bool { didSet { defaults.set(noticeInApp, forKey: K.noticeInApp) } }
    @Published var noticeDnd: Bool { didSet { defaults.set(noticeDnd, forKey: K.noticeDnd) } }
    @Published var quietHoursEnabled: Bool { didSet { defaults.set(quietHoursEnabled, forKey: K.quietHoursEnabled) } }
    @Published var quietStartMinute: Int { didSet { defaults.set(quietStartMinute, forKey: K.quietStartMinute) } }
    @Published var quietEndMinute: Int { didSet { defaults.set(quietEndMinute, forKey: K.quietEndMinute) } }

    /// The per-kind gate the notice path reads — the twin of Android's
    /// `SettingsStore.noticeEnabled`. An id with no row must not default to on.
    func noticeEnabled(_ kindId: String) -> Bool {
        switch kindId {
        case "messages": return noticeMessages
        case "ai-updates": return noticeAiUpdates
        case "tips": return noticeTips
        case "account-activity": return noticeAccountActivity
        case "product-updates": return noticeProductUpdates
        default: return false
        }
    }

    // MARK: - Model selection

    /// Effort tile -> wire mode. The wire accepts exactly
    /// `flash | thinking | auto`, so Minimal and Light are the same mode today:
    /// the tile remembers which one was tapped, the server cannot yet tell them
    /// apart. Widening this is a backend change, not a UI one.
    static func wireMode(forEffort effort: String) -> String {
        switch effort {
        case "minimal", "light": return "flash"
        case "high": return "thinking"
        default: return "auto"
        }
    }

    /// Writes the tile AND the new-chat wire mode, so the chip that is lit and
    /// what the next reply does never disagree. `gs.mode.new` is the key
    /// ChatViewModel falls back to for a brand-new conversation.
    ///
    /// "Remember my choices" governs the NEW-chat default only — the same
    /// contract Android's store implements — so with it off the default stays
    /// flash while the tile still remembers what was tapped.
    func applyReasoningEffort(_ effort: String) {
        reasoningEffort = effort
        let wire = rememberChoices ? Self.wireMode(forEffort: effort) : "flash"
        defaults.set(wire, forKey: "gs.mode.new")
    }

    /// Back to the mock's recommended pair: Pro, Standard effort, remembering.
    func resetModelPreferences() {
        preferredModel = "pro"
        rememberChoices = true
        applyReasoningEffort("standard")
    }

    // Accessibility
    @Published var fontScale: Double { didSet { defaults.set(fontScale, forKey: K.fontScale); propagateFlags() } }
    @Published var highContrast: Bool { didSet { defaults.set(highContrast, forKey: K.highContrast); propagateFlags() } }
    @Published var reduceMotion: Bool { didSet { defaults.set(reduceMotion, forKey: K.reduceMotion); propagateFlags() } }
    @Published var screenReaderHints: Bool { didSet { defaults.set(screenReaderHints, forKey: K.screenReaderHints); propagateFlags() } }
    @Published var haptics: Bool { didSet { defaults.set(haptics, forKey: K.haptics) } }

    /// Pushes the accessibility values into the plain flag store that
    /// dynamic colour providers and Aero fonts/motion read (they cannot
    /// observe ObservableObjects from non-isolated closures).
    private func propagateFlags() {
        let flags = GSAccessibilityFlags.shared
        flags.highContrast = highContrast
        flags.reduceMotion = reduceMotion
        flags.reduceAnimations = reduceAnimations
        flags.screenReaderHints = screenReaderHints
        flags.fontScale = CGFloat(fontScale)
    }

    private init() {
        theme = defaults.string(forKey: K.theme) ?? "System"
        reduceAnimations = defaults.object(forKey: K.reduceAnimations) as? Bool ?? false
        enterToSend = defaults.object(forKey: K.enterToSend) as? Bool ?? true
        autoTitle = defaults.object(forKey: K.autoTitle) as? Bool ?? true
        memory = defaults.object(forKey: K.memory) as? Bool ?? true
        personalisation = defaults.object(forKey: K.personalisation) as? Bool ?? true
        // false, not true. An absent key must mean OFF.
        preferLocal = defaults.object(forKey: K.preferLocal) as? Bool ?? false
        reasoning = defaults.string(forKey: K.reasoning) ?? "Medium"
        preferredModel = defaults.string(forKey: K.preferredModel) ?? "pro"
        reasoningEffort = defaults.string(forKey: K.reasoningEffort) ?? "standard"
        // true, like Android: the tile the reader taps and the default the next
        // new chat picks are the same choice until they say otherwise.
        rememberChoices = defaults.object(forKey: K.rememberChoices) as? Bool ?? true
        helpImprove = defaults.object(forKey: K.helpImprove) as? Bool ?? false
        appPasscode = defaults.object(forKey: K.appPasscode) as? Bool ?? false
        // false, not true: a lock is opt-in. Android's store has always
        // defaulted off, and an app that arms a biometric prompt before the
        // user asked for one is a lock they never chose.
        biometricUnlock = defaults.object(forKey: K.biometricUnlock) as? Bool ?? false
        lastSecurityCheckMillis = defaults.double(forKey: K.lastSecurityCheck)
        pushNotifications = defaults.object(forKey: K.pushNotifications) as? Bool ?? true
        taskCompleted = defaults.object(forKey: K.taskCompleted) as? Bool ?? true
        assistantUpdates = defaults.object(forKey: K.assistantUpdates) as? Bool ?? true
        productNews = defaults.object(forKey: K.productNews) as? Bool ?? false
        noticeMessages = defaults.object(forKey: K.noticeMessages) as? Bool ?? true
        noticeAiUpdates = defaults.object(forKey: K.noticeAiUpdates) as? Bool ?? true
        noticeTips = defaults.object(forKey: K.noticeTips) as? Bool ?? true
        noticeAccountActivity = defaults.object(forKey: K.noticeAccountActivity) as? Bool ?? true
        noticeProductUpdates = defaults.object(forKey: K.noticeProductUpdates) as? Bool ?? true
        noticeInApp = defaults.object(forKey: K.noticeInApp) as? Bool ?? true
        noticeDnd = defaults.object(forKey: K.noticeDnd) as? Bool ?? false
        quietHoursEnabled = defaults.object(forKey: K.quietHoursEnabled) as? Bool ?? false
        quietStartMinute = defaults.object(forKey: K.quietStartMinute) as? Int ?? 22 * 60
        quietEndMinute = defaults.object(forKey: K.quietEndMinute) as? Int ?? 7 * 60
        fontScale = defaults.object(forKey: K.fontScale) as? Double ?? 1.0
        highContrast = defaults.object(forKey: K.highContrast) as? Bool ?? false
        reduceMotion = defaults.object(forKey: K.reduceMotion) as? Bool ?? false
        screenReaderHints = defaults.object(forKey: K.screenReaderHints) as? Bool ?? false
        haptics = defaults.object(forKey: K.haptics) as? Bool ?? true
        propagateFlags()
    }

    /// Shared motion gate (Task 85-e): the in-app Reduce animations / Reduce
    /// motion toggles OR the system Reduce Motion accessibility setting.
    /// KineticPressStyle and the aurora/skeleton loops read this and hold
    /// still (scale 1.0, no spring, static frames) when any of them is set.
    /// UIAccessibility.isReduceMotionEnabled is documented safe to read from
    /// any thread; the store itself is @MainActor and every reader here is
    /// main-thread UI code.
    var animationReduced: Bool {
        reduceAnimations || reduceMotion || UIAccessibility.isReduceMotionEnabled
    }

    private enum K {
        static let theme = "settings.theme"
        static let reduceAnimations = "settings.reduceAnimations"
        static let enterToSend = "settings.enterToSend"
        static let autoTitle = "settings.autoTitle"
        static let memory = "settings.memory"
        static let personalisation = "settings.personalisation"
        static let preferLocal = "settings.preferLocal"
        static let reasoning = "settings.reasoning"
        static let preferredModel = "settings.preferredModel"
        static let reasoningEffort = "settings.reasoningEffort"
        static let rememberChoices = "settings.rememberChoices"
        static let helpImprove = "settings.helpImprove"
        static let appPasscode = "settings.appPasscode"
        static let biometricUnlock = "settings.biometricUnlock"
        static let lastSecurityCheck = "settings.lastSecurityCheck"
        static let pushNotifications = "settings.pushNotifications"
        static let taskCompleted = "settings.taskCompleted"
        static let assistantUpdates = "settings.assistantUpdates"
        static let productNews = "settings.productNews"
        static let noticeMessages = "settings.notice.messages"
        static let noticeAiUpdates = "settings.notice.aiUpdates"
        static let noticeTips = "settings.notice.tips"
        static let noticeAccountActivity = "settings.notice.accountActivity"
        static let noticeProductUpdates = "settings.notice.productUpdates"
        static let noticeInApp = "settings.notice.inApp"
        static let noticeDnd = "settings.notice.dnd"
        static let quietHoursEnabled = "settings.quietHours.enabled"
        static let quietStartMinute = "settings.quietHours.start"
        static let quietEndMinute = "settings.quietHours.end"
        static let fontScale = "settings.fontScale"
        static let highContrast = "settings.highContrast"
        static let reduceMotion = "settings.reduceMotion"
        static let screenReaderHints = "settings.screenReaderHints"
        static let haptics = "settings.haptics"
    }
}
