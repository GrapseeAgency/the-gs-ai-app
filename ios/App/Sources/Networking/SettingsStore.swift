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
    @Published var reduceAnimations: Bool { didSet { defaults.set(reduceAnimations, forKey: K.reduceAnimations) } }
    // Chat
    @Published var enterToSend: Bool { didSet { defaults.set(enterToSend, forKey: K.enterToSend) } }
    @Published var autoTitle: Bool { didSet { defaults.set(autoTitle, forKey: K.autoTitle) } }
    // AI
    @Published var memory: Bool { didSet { defaults.set(memory, forKey: K.memory) } }
    @Published var personalisation: Bool { didSet { defaults.set(personalisation, forKey: K.personalisation) } }
    @Published var reasoning: String { didSet { defaults.set(reasoning, forKey: K.reasoning) } }
    // Privacy
    @Published var helpImprove: Bool { didSet { defaults.set(helpImprove, forKey: K.helpImprove) } }
    // Security
    @Published var appPasscode: Bool { didSet { defaults.set(appPasscode, forKey: K.appPasscode) } }
    @Published var biometricUnlock: Bool { didSet { defaults.set(biometricUnlock, forKey: K.biometricUnlock) } }
    // Notifications
    @Published var pushNotifications: Bool { didSet { defaults.set(pushNotifications, forKey: K.pushNotifications) } }
    @Published var taskCompleted: Bool { didSet { defaults.set(taskCompleted, forKey: K.taskCompleted) } }
    @Published var assistantUpdates: Bool { didSet { defaults.set(assistantUpdates, forKey: K.assistantUpdates) } }
    @Published var productNews: Bool { didSet { defaults.set(productNews, forKey: K.productNews) } }
    // Language
    @Published var aiLanguage: String { didSet { defaults.set(aiLanguage, forKey: K.aiLanguage) } }
    // Accessibility
    @Published var fontScale: Double { didSet { defaults.set(fontScale, forKey: K.fontScale) } }
    @Published var highContrast: Bool { didSet { defaults.set(highContrast, forKey: K.highContrast) } }
    @Published var reduceMotion: Bool { didSet { defaults.set(reduceMotion, forKey: K.reduceMotion) } }
    @Published var haptics: Bool { didSet { defaults.set(haptics, forKey: K.haptics) } }

    private init() {
        theme = defaults.string(forKey: K.theme) ?? "System"
        reduceAnimations = defaults.object(forKey: K.reduceAnimations) as? Bool ?? false
        enterToSend = defaults.object(forKey: K.enterToSend) as? Bool ?? true
        autoTitle = defaults.object(forKey: K.autoTitle) as? Bool ?? true
        memory = defaults.object(forKey: K.memory) as? Bool ?? true
        personalisation = defaults.object(forKey: K.personalisation) as? Bool ?? true
        reasoning = defaults.string(forKey: K.reasoning) ?? "Medium"
        helpImprove = defaults.object(forKey: K.helpImprove) as? Bool ?? false
        appPasscode = defaults.object(forKey: K.appPasscode) as? Bool ?? false
        biometricUnlock = defaults.object(forKey: K.biometricUnlock) as? Bool ?? true
        pushNotifications = defaults.object(forKey: K.pushNotifications) as? Bool ?? true
        taskCompleted = defaults.object(forKey: K.taskCompleted) as? Bool ?? true
        assistantUpdates = defaults.object(forKey: K.assistantUpdates) as? Bool ?? true
        productNews = defaults.object(forKey: K.productNews) as? Bool ?? false
        aiLanguage = defaults.string(forKey: K.aiLanguage) ?? "EN"
        fontScale = defaults.object(forKey: K.fontScale) as? Double ?? 1.0
        highContrast = defaults.object(forKey: K.highContrast) as? Bool ?? false
        reduceMotion = defaults.object(forKey: K.reduceMotion) as? Bool ?? false
        haptics = defaults.object(forKey: K.haptics) as? Bool ?? true
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
        static let reasoning = "settings.reasoning"
        static let helpImprove = "settings.helpImprove"
        static let appPasscode = "settings.appPasscode"
        static let biometricUnlock = "settings.biometricUnlock"
        static let pushNotifications = "settings.pushNotifications"
        static let taskCompleted = "settings.taskCompleted"
        static let assistantUpdates = "settings.assistantUpdates"
        static let productNews = "settings.productNews"
        static let aiLanguage = "settings.aiLanguage"
        static let fontScale = "settings.fontScale"
        static let highContrast = "settings.highContrast"
        static let reduceMotion = "settings.reduceMotion"
        static let haptics = "settings.haptics"
    }
}
