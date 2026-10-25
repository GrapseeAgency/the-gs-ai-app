import Foundation

/**
 * The answers the setup wizard actually uses — the iOS twin of Android's
 * `data/OnboardingProfileStore.kt`.
 *
 * Until now the wizard held interests and capabilities in screen-local
 * `@State` and dropped them on finish, which is indistinguishable from never
 * asking. Android persists them and lets Home choose its suggestion cards
 * from what the reader actually pinned; this store gives iOS the same
 * behaviour so the two platforms put the same question in front of the same
 * person.
 *
 * CONTRACT
 *  - Hydrates once at init from UserDefaults, the same local-first shape as
 *    AccountStore / SettingsStore / ConversationStore.
 *  - What is READ today, honestly: [capabilities] is read by Home and the chat
 *    empty state (HomeDispatch.selectedSuggestions(for:)), and as of the
 *    Settings reference pass [interests], [responseStyle] and [personality]
 *    are read by Profile & Preferences (Response Style and Personalization
 *    Preferences groups). The previous wording here claimed "only the fields
 *    Home actually consumes are stored"; that was not true, and a false
 *    contract comment is worse than an admitted gap because it is quoted as
 *    proof.
 *  - Sets are written as a sorted CSV so a re-save of identical answers never
 *    churns the stored string.
 */
@MainActor
final class OnboardingProfileStore: ObservableObject {

    static let shared = OnboardingProfileStore()

    /// Topic labels picked in step 2 ("Writing", "Travel", …).
    @Published private(set) var interests: Set<String>
    /// Capability titles picked in step 3 ("Draft and write", …).
    @Published private(set) var capabilities: Set<String>
    /// Concise | Standard | Detailed
    @Published private(set) var responseStyle: String
    /// Professional | Friendly | Playful
    @Published private(set) var personality: String

    private let defaults = UserDefaults.standard

    private init() {
        interests = Self.readSet(Self.interestsKey)
        capabilities = Self.readSet(Self.capabilitiesKey)
        responseStyle = defaults.string(forKey: Self.responseStyleKey) ?? "Standard"
        personality = defaults.string(forKey: Self.personalityKey) ?? "Friendly"
    }

    /// Persist the wizard's answers. Empty collections remove their keys so
    /// there is no stored blank identity to reason about.
    func save(interests: Set<String>, capabilities: Set<String>,
              responseStyle: String, personality: String) {
        self.interests = interests
        self.capabilities = capabilities
        self.responseStyle = responseStyle
        self.personality = personality
        write(interests, forKey: Self.interestsKey)
        write(capabilities, forKey: Self.capabilitiesKey)
        defaults.set(responseStyle, forKey: Self.responseStyleKey)
        defaults.set(personality, forKey: Self.personalityKey)
    }

    func clear() {
        interests = []
        capabilities = []
        responseStyle = "Standard"
        personality = "Friendly"
        [Self.interestsKey, Self.capabilitiesKey,
         Self.responseStyleKey, Self.personalityKey].forEach { defaults.removeObject(forKey: $0) }
    }

    /// A short, honest description for Profile; nil when setup was skipped.
    var summary: String? {
        if interests.isEmpty && capabilities.isEmpty { return nil }
        let topics = interests.prefix(3).joined(separator: ", ")
        switch (topics.isEmpty, capabilities.isEmpty) {
        case (false, false): return "\(topics) · \(capabilities.count) tools pinned"
        case (false, true):  return topics
        default:             return "\(capabilities.count) tools pinned"
        }
    }

    /// True when the wizard has actually been completed with pinned skills.
    var hasCapabilities: Bool { !capabilities.isEmpty }

    // MARK: Plumbing

    private func write(_ set: Set<String>, forKey key: String) {
        if set.isEmpty { defaults.removeObject(forKey: key); return }
        defaults.set(set.sorted().joined(separator: ","), forKey: key)
    }

    private static func readSet(_ key: String) -> Set<String> {
        (UserDefaults.standard.string(forKey: key) ?? "")
            .split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
            .reduce(into: Set<String>()) { $0.insert($1) }
    }

    private static let interestsKey = "gs.profile.interests"
    private static let capabilitiesKey = "gs.profile.capabilities"
    private static let responseStyleKey = "gs.profile.responseStyle"
    private static let personalityKey = "gs.profile.personality"
}
