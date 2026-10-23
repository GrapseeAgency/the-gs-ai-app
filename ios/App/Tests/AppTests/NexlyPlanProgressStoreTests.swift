import XCTest
@testable import GSApp

final class NexlyPlanProgressStoreTests: XCTestCase {
    private let plan = NexlyPlanData(
        title: "Tomorrow",
        rows: [
            NexlyPlanRow(time: "6:30 – 7:00 AM", text: "Wake up + light stretch", icon: "sunrise", tone: .amber),
            NexlyPlanRow(time: "7:00 – 7:30 AM", text: "Breakfast & coffee", icon: "coffee", tone: .amber),
            NexlyPlanRow(time: "9:00 – 5:00 PM", text: "Work shift", icon: "device-laptop", tone: .blue),
        ],
        closing: nil,
        closingIcon: nil,
        timestamp: nil
    )

    func testSavesRestoresAndBuildsContext() {
        let conversationID = "test-\(UUID().uuidString)"
        let store = NexlyPlanProgressStore.shared
        let fingerprint = store.fingerprint(for: plan)
        let done: Set<String> = [
            "6:30 – 7:00 AM|Wake up + light stretch",
            "9:00 – 5:00 PM|Work shift",
        ]
        defer {
            UserDefaults.standard.removeObject(forKey: "nexly.planProgress.\(conversationID)")
        }

        store.save(conversationID: conversationID, plan: plan, done: done)

        XCTAssertEqual(store.load(conversationID: conversationID, fingerprint: fingerprint), done)
        XCTAssertEqual(store.load(conversationID: conversationID, fingerprint: "different"), [])
        let context = store.context(for: conversationID)
        XCTAssertNotNil(context)
        XCTAssertTrue(context?.contains("Completed (2/3)") == true)
        XCTAssertTrue(context?.contains("Wake up + light stretch") == true)
        XCTAssertTrue(context?.contains("Pending (1)") == true)
        XCTAssertTrue(context?.contains("Breakfast & coffee") == true)
        XCTAssertNil(store.context(for: "no-such-conversation"))
    }

    func testEmptyChecklistProducesNoContext() {
        let conversationID = "test-\(UUID().uuidString)"
        let store = NexlyPlanProgressStore.shared
        defer {
            UserDefaults.standard.removeObject(forKey: "nexly.planProgress.\(conversationID)")
        }
        store.save(conversationID: conversationID, plan: plan, done: [])
        XCTAssertNil(store.context(for: conversationID))
    }

    func testPreferenceAloneProducesContext() {
        let conversationID = "test-\(UUID().uuidString)"
        let store = NexlyPlanProgressStore.shared
        defer {
            UserDefaults.standard.removeObject(forKey: "nexly.planProgress.\(conversationID)")
        }
        store.save(conversationID: conversationID, plan: plan, done: [], preference: "up")
        XCTAssertEqual(
            store.loadPreference(
                conversationID: conversationID,
                fingerprint: store.fingerprint(for: plan)
            ),
            "up"
        )
        XCTAssertTrue(store.context(for: conversationID)?.contains("marked helpful") == true)
    }
}
