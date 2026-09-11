import Foundation

/**
 * Account identity — the one real display-name + email source on iOS
 * (twin of Android's `data/AccountStore.kt`).
 *
 * CONTRACT
 *  - The name the user actually types at sign-up is persisted here and read
 *    by the Home greeting (first name, neutral fallback when absent).
 *  - Nothing is invented: no stored name means no name is displayed — the
 *    greeting degrades to a plain time-of-day line ("Good morning", never
 *    "Admin", never a guessed placeholder). Empty value → the key is
 *    REMOVED, so there is no way to store a blank identity.
 *  - Every value is whitespace-trimmed on the way in and on the way out.
 *  - Sign-in persists the email only; it must never clear a stored display
 *    name (the name survives a sign-in from any device shape).
 *
 * Backed by UserDefaults ("gs.account.displayName" / "gs.account.email"),
 * the same local-first shape as the rest of the app's stores; swap for the
 * real account profile when the backend session API lands.
 *
 * CHOICE — ObservableObject with @Published properties (not a static-func
 * enum): the workspace greeting must re-render the moment identity changes
 * while Home is on screen, and one `@ObservedObject` gives that reactivity
 * with zero extra machinery — the exact pattern ConversationStore.shared /
 * SettingsStore.shared already use across the app.
 */
@MainActor
final class AccountStore: ObservableObject {

    static let shared = AccountStore()

    /// The stored display name, or "" when the user never gave one.
    @Published private(set) var displayName: String
    /// The stored email, or "" when none was given.
    @Published private(set) var email: String

    private let defaults = UserDefaults.standard

    private init() {
        displayName = Self.read(Self.nameKey)
        email = Self.read(Self.emailKey)
    }

    /// The leading whitespace-separated word of the display name — the form
    /// greetings use. "" when no name is stored (callers show the neutral
    /// fallback); derived only, never invented.
    var firstName: String {
        displayName.split(separator: " ").first.map(String.init) ?? ""
    }

    /// Persist a (possibly empty → clears) display name; whitespace-trimmed.
    func setDisplayName(_ value: String) {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            defaults.removeObject(forKey: Self.nameKey)
        } else {
            defaults.set(trimmed, forKey: Self.nameKey)
        }
        displayName = trimmed
    }

    /// Persist a (possibly empty → clears) email; whitespace-trimmed.
    func setEmail(_ value: String) {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            defaults.removeObject(forKey: Self.emailKey)
        } else {
            defaults.set(trimmed, forKey: Self.emailKey)
        }
        email = trimmed
    }

    /// The stored value, or "" when the user never gave one — never a
    /// placeholder.
    private static func read(_ key: String) -> String {
        (UserDefaults.standard.string(forKey: key) ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static let nameKey = "gs.account.displayName"
    private static let emailKey = "gs.account.email"
}
