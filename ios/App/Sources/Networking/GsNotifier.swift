import Foundation

/// The iOS twin of Android's `GsNotifier.record`: the in-app half of a notice.
///
/// iOS has no notification dispatcher in this build, so nothing here posts a
/// banner. What it does is make the Inbox a real log of events this build can
/// back: a sign-in is an account event, and the Account Activity switch really
/// gates it. A future dispatcher posts first and records after, exactly as
/// Android's `GsNotifier.post` does.
@MainActor
enum GsNotifier {

    enum Kind: String {
        case messages
        case aiUpdates = "ai-updates"
        case tips
        case accountActivity = "account-activity"
        case productUpdates = "product-updates"
    }

    /// Honours the master switch and the per-kind switch. Returns false when
    /// the notice was not allowed, so a caller can tell "recorded" from
    /// "dropped" instead of assuming the write happened.
    @discardableResult
    static func record(kind: Kind, title: String, body: String) -> Bool {
        let text = body.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty, !text.isEmpty else { return false }
        let settings = SettingsStore.shared
        guard settings.pushNotifications else { return false }
        guard settings.noticeEnabled(kind.rawValue) else { return false }
        GsNoticeInbox.shared.record(kindId: kind.rawValue, title: title, body: text)
        return true
    }
}
