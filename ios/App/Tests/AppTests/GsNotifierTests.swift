import XCTest
@testable import GSApp

/// The iOS notice path: an in-app record gated by the master + per-kind
/// switches, the release-notice token, and the quiet-hours arithmetic the
/// banner dispatcher shares with Android.
///
/// The dispatcher itself (UNUserNotificationCenter) is not exercised here — a
/// unit-test host cannot show a banner, and pretending otherwise would make
/// this suite certify nothing. What is pinned is every gate that decides
/// whether a banner is even attempted, plus the rule that the Inbox row is
/// written only after a real delivery or a real in-app event.
@MainActor
final class GsNotifierTests: XCTestCase {

    private let settings = SettingsStore.shared
    private let inbox = GsNoticeInbox.shared

    /// Called at the top of each test rather than from `setUp()`: XCTest's
    /// setUp is nonisolated and this class is @MainActor, so keeping the store
    /// writes inside the isolated test methods avoids an actor-isolation
    /// override the Swift 5 mode only warns about.
    private func reset() {
        settings.pushNotifications = true
        settings.noticeAccountActivity = true
        settings.noticeTips = true
        inbox.removeAllForTesting()
    }

    func testRecordWritesTheInboxRowWhenBothSwitchesAllowIt() {
        reset()
        let wrote = GsNotifier.record(
            kind: .accountActivity,
            title: "Signed in to Nexly",
            body: "This device is now signed in to your Grapsee account.")

        XCTAssertTrue(wrote)
        XCTAssertEqual(inbox.entries.count, 1)
        XCTAssertEqual(inbox.entries.first?.kindId, "account-activity")
        XCTAssertFalse(inbox.entries.first?.read ?? true)
        inbox.removeAllForTesting()
    }

    func testTheMasterSwitchStopsTheRecord() {
        reset()
        settings.pushNotifications = false

        let wrote = GsNotifier.record(
            kind: .accountActivity,
            title: "Signed in to Nexly",
            body: "This device is now signed in to your Grapsee account.")

        XCTAssertFalse(wrote)
        XCTAssertTrue(inbox.entries.isEmpty)
        // Restore the preference: this store outlives the test process and a
        // later render must not photograph the Alerts sheet with its master
        // switch off.
        settings.pushNotifications = true
        inbox.removeAllForTesting()
    }

    func testTheKindSwitchStopsOnlyThatKind() {
        reset()
        settings.noticeAccountActivity = false

        XCTAssertFalse(
            GsNotifier.record(
                kind: .accountActivity,
                title: "Signed in to Nexly",
                body: "This device is now signed in to your Grapsee account."))
        XCTAssertTrue(inbox.entries.isEmpty)

        settings.noticeTips = true
        XCTAssertTrue(
            GsNotifier.record(
                kind: .tips,
                title: "Personalization applied",
                body: "Your preferences now tailor Nexly's replies."))
        XCTAssertEqual(inbox.entries.count, 1)
        settings.noticeAccountActivity = true
        inbox.removeAllForTesting()
    }

    func testAnEmptyTitleOrBodyNeverBecomesARow() {
        reset()
        XCTAssertFalse(GsNotifier.record(kind: .accountActivity, title: "", body: "text"))
        XCTAssertFalse(GsNotifier.record(kind: .accountActivity, title: "title", body: "   "))
        XCTAssertTrue(inbox.entries.isEmpty)
    }

    /// The mock's window is 22:00 → 07:00 and wraps midnight. Both halves and
    /// both boundaries have to hold, or quiet hours either never closes or
    /// never opens.
    func testQuietHoursWrapAcrossMidnight() {
        func at(_ hour: Int, _ minute: Int = 0) -> Date {
            var parts = Calendar.current.dateComponents([.year, .month, .day], from: Date())
            parts.hour = hour
            parts.minute = minute
            return Calendar.current.date(from: parts) ?? Date()
        }
        // 22:00 → 07:00
        XCTAssertFalse(GsNotifier.inQuietHours(at(21, 59), startMinute: 22 * 60, endMinute: 7 * 60))
        XCTAssertTrue(GsNotifier.inQuietHours(at(22, 0), startMinute: 22 * 60, endMinute: 7 * 60))
        XCTAssertTrue(GsNotifier.inQuietHours(at(0, 30), startMinute: 22 * 60, endMinute: 7 * 60))
        XCTAssertTrue(GsNotifier.inQuietHours(at(6, 59), startMinute: 22 * 60, endMinute: 7 * 60))
        XCTAssertFalse(GsNotifier.inQuietHours(at(7, 0), startMinute: 22 * 60, endMinute: 7 * 60))
        // A same-day window is the plain interval, not the wrapped one.
        XCTAssertTrue(GsNotifier.inQuietHours(at(13, 0), startMinute: 12 * 60, endMinute: 14 * 60))
        XCTAssertFalse(GsNotifier.inQuietHours(at(14, 0), startMinute: 12 * 60, endMinute: 14 * 60))
    }

    /// The version notice is once per build, and the token is spent only when a
    /// row was really written: with the master switch off, the next launch must
    /// still announce it.
    func testReleaseNoticeSpendsItsTokenOnlyOnceRecorded() async {
        reset()
        let defaults = UserDefaults.standard
        defaults.removeObject(forKey: GsReleaseNotice.versionKey)

        GsReleaseNotice.announceIfNeeded(version: "9.9.9-test")
        await Task.yield()
        for _ in 0..<20 where inbox.entries.isEmpty {
            await Task.yield()
        }
        XCTAssertEqual(inbox.entries.count, 1)
        XCTAssertEqual(inbox.entries.first?.kindId, "product-updates")
        XCTAssertEqual(defaults.string(forKey: GsReleaseNotice.versionKey), "9.9.9-test")

        // Same version again: no second row.
        GsReleaseNotice.announceIfNeeded(version: "9.9.9-test")
        await Task.yield()
        XCTAssertEqual(inbox.entries.count, 1)

        // Master switch off: a new version writes nothing and keeps its token.
        settings.pushNotifications = false
        defaults.removeObject(forKey: GsReleaseNotice.versionKey)
        GsReleaseNotice.announceIfNeeded(version: "9.9.10-test")
        await Task.yield()
        XCTAssertTrue(inbox.entries.isEmpty)
        XCTAssertNil(defaults.string(forKey: GsReleaseNotice.versionKey))

        settings.pushNotifications = true
        inbox.removeAllForTesting()
        defaults.removeObject(forKey: GsReleaseNotice.versionKey)
    }
}
