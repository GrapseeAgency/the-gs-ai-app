import XCTest
@testable import GSApp

/// The iOS half of the notice path that can be real today: an in-app record
/// gated by the same master + per-kind switches the Notifications & Alerts
/// sheet writes.
///
/// No banner is posted — iOS has no dispatcher in this build — but a sign-in
/// is an account event the app can back, and these tests pin that it reaches
/// the Inbox only when both switches allow it. A future dispatcher posts first
/// and records after, exactly as Android's `GsNotifier.post` does.
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
}
