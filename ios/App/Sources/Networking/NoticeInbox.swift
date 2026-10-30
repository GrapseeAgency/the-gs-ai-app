import Combine
import Foundation

/// One row in the Notifications inbox — the twin of Android's
/// `NoticeInbox.Entry`, same field names and the same id shape (epoch millis).
struct GsNoticeEntry: Codable, Identifiable, Equatable {
    var id: Double
    var kindId: String
    var title: String
    var body: String
    var createdAtMillis: Double
    var read: Bool

    var createdAt: Date { Date(timeIntervalSince1970: createdAtMillis / 1000) }
}

/// The durable record of what Nexly actually told this user — the iOS twin of
/// Android's `data/notifications/NoticeInbox.kt`.
///
/// The Notifications screen draws its chips, counts, Today/Earlier split,
/// unread dots and "Mark all as read" from this store only. There is no seeded
/// content: a fresh install is genuinely empty and says "You're all caught up",
/// which is the truth, not a designed state.
///
/// `GsNotifier.record` is the writer: a sign-in is the first real producer, and
/// it reaches this file only when the master switch and the Account Activity
/// switch allow it. A future banner dispatcher posts first and records after,
/// exactly as `GsNotifier.post` does on Android, so the inbox and the banner
/// can never disagree.
@MainActor
final class GsNoticeInbox: ObservableObject {

    static let shared = GsNoticeInbox()

    static let maxEntries = 200

    @Published private(set) var entries: [GsNoticeEntry]

    private let defaults = UserDefaults.standard
    private let key = "gs.notice.inbox.entries"

    private init() {
        if let data = defaults.data(forKey: key),
           let decoded = try? JSONDecoder().decode([GsNoticeEntry].self, from: data) {
            entries = decoded
        } else {
            entries = []
        }
    }

    /// Called by a dispatcher only once the notice has actually been shown.
    func record(kindId: String, title: String, body: String, now: Date = Date()) {
        let entry = GsNoticeEntry(
            id: now.timeIntervalSince1970 * 1000,
            kindId: kindId,
            title: title,
            body: String(body.prefix(400)),
            createdAtMillis: now.timeIntervalSince1970 * 1000,
            read: false)
        entries = Array(([entry] + entries).prefix(Self.maxEntries))
        persist()
    }

    func markRead(id: Double) {
        guard let index = entries.firstIndex(where: { $0.id == id }), !entries[index].read else { return }
        entries[index].read = true
        persist()
    }

    func markAllRead() {
        guard entries.contains(where: { !$0.read }) else { return }
        entries = entries.map { entry in
            var copy = entry
            copy.read = true
            return copy
        }
        persist()
    }

    #if DEBUG
    /// Test-only: clears the persisted ring so a render harness can seed its
    /// own rows without leaking them into the next launch of the app.
    func removeAllForTesting() {
        entries = []
        defaults.removeObject(forKey: key)
    }
    #endif

    /// Real per-chip counts, computed from the stored rows.
    /// Billing is always zero: no payment processor exists to produce one.
    var counts: [String: Int] {
        [
            "all": entries.count,
            "messages": entries.filter { $0.kindId == "messages" }.count,
            "updates": entries.filter { $0.kindId == "product-updates" || $0.kindId == "ai-updates" }.count,
            "account": entries.filter { $0.kindId == "account-activity" }.count,
            "billing": 0,
        ]
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(entries) else { return }
        defaults.set(data, forKey: key)
    }
}
