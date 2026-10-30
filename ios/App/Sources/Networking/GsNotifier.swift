import Foundation

/// The iOS twin of Android's `GsNotifier`: one gate for the Inbox row and one
/// for the banner.
///
/// `post` is the full path — switches, Quiet Hours, foreground and OS
/// authorization, then the Inbox row only if the system accepted the banner.
/// `record` is the in-app half, used when the reader is already looking at the
/// app (a sign-in, a finished onboarding): it writes the log without
/// interrupting.
///
/// The dispatcher arrived last; before it, `pushNotifications` persisted a
/// preference nothing delivered, which is exactly the dead toggle the honesty
/// contract forbids. Both entry points now read it.
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

    /// The full delivery path. Identical switches to `record`, plus the three
    /// gates that only a banner needs: Quiet Hours, Nexly's own DND preference
    /// and the foreground / OS-authorization pair inside the dispatcher.
    ///
    /// Records only after the system accepted the notification, so the Inbox
    /// can never claim a banner the device did not show. Returns whether the
    /// notice landed anywhere.
    @discardableResult
    static func post(kind: Kind, title: String, body: String) async -> Bool {
        let text = body.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty, !text.isEmpty else { return false }
        let settings = SettingsStore.shared
        guard settings.pushNotifications else { return false }
        guard settings.noticeEnabled(kind.rawValue) else { return false }
        // Quiet Hours and Nexly's DND are interruption gates, not log gates:
        // they stop the banner, and the caller falls back to `record`.
        guard !settings.noticeDnd else { return false }
        guard !inQuietHours() else { return false }
        let landed = await GsBannerDispatcher.shared.post(title: title, body: text)
        guard landed else { return false }
        GsNoticeInbox.shared.record(kindId: kind.rawValue, title: title, body: text)
        return true
    }

    /// The mock's Quiet Hours window, stored as minutes-of-day and wrapping
    /// midnight — the same arithmetic Android's `GsNotifier.inQuietHours` uses,
    /// so 22:00→07:00 holds every hour from ten until seven and nothing
    /// between.
    static func inQuietHours(_ now: Date = Date()) -> Bool {
        let settings = SettingsStore.shared
        guard settings.quietHoursEnabled else { return false }
        return inQuietHours(
            now,
            startMinute: settings.quietStartMinute,
            endMinute: settings.quietEndMinute)
    }

    /// Pure form, so the boundary cases are testable without mutating the
    /// store's clock-dependent preferences.
    static func inQuietHours(_ now: Date, startMinute: Int, endMinute: Int) -> Bool {
        let parts = Calendar.current.dateComponents([.hour, .minute], from: now)
        let minute = (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
        return startMinute <= endMinute
            ? (minute >= startMinute && minute < endMinute)
            : (minute >= startMinute || minute < endMinute)
    }
}
