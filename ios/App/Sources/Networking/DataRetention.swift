import Foundation

/// The data-retention contract behind Data Export & Account Deletion.
///
/// The reference draws a retention period and an auto-delete switch; both live
/// in `SettingsStore`, and this enum is their only reader. A retention control
/// with no executing path would be the dead-switch failure the honesty contract
/// forbids.
///
/// WHAT IT DELETES: a conversation whose last turn (`updatedAt`) is older than
/// the chosen period, together with its messages. Saved Library items are not
/// conversations and stay. Pinned and archived chats are not exempt — the
/// period measures the last turn, and the page says so.
@MainActor
enum DataRetention {

    /// ISO cutoff for `months` months before `now`, in the storage format.
    static func cutoffIso(months: Int, now: Date = Date()) -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0) ?? .current
        let cutoff = calendar.date(
            byAdding: .month,
            value: -max(1, months),
            to: now) ?? now
        return ConversationStore.isoString(cutoff)
    }

    /// Delete conversations untouched for longer than the stored period.
    /// Returns how many conversations left the device; zero when the switch is
    /// off or nothing is old enough.
    @discardableResult
    static func purgeExpired(now: Date = Date()) -> Int {
        let settings = SettingsStore.shared
        guard settings.autoDeleteInactive else { return 0 }
        let cutoff = cutoffIso(months: settings.dataRetentionMonths, now: now)
        return ConversationStore.shared.purgeConversations(updatedBefore: cutoff)
    }

    /// Clear Chat History: conversations, messages and staged attachments.
    /// Library saves and identity stay.
    static func clearChatHistory() {
        ConversationStore.shared.clearHistory()
    }

    /// Delete Account: the whole local corpus, including Library saves and
    /// staged files. The view owns identity/session teardown, the only layer
    /// that can leave the screen.
    static func deleteAllContent() {
        ConversationStore.shared.wipeAllContent()
    }
}
