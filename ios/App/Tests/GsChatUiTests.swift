import XCTest
@testable import GSApp

/// Blocker 4 — a real UI test, the iOS counterpart of Android's
/// `ChatUiReplyTest`.
///
/// Drives the app the way a user does: finds the chat composer's text field
/// (AeroInputBar, placeholder "Ask anything…"), types "hello", taps the
/// Send button (accessibilityLabel "Send"), and asserts a real, non-blank
/// assistant bubble appears in the UI within a bounded time — and that the
/// text is NOT the canned greeting `ChatViewModel.localReply` fabricates
/// when the engine is absent.
///
/// WIRING STATUS: XCTSkip-guarded unless every precondition is provisioned:
///   1. `GS_UI_TEST_MODE=1` in the test process environment — without it the
///      test skips even in a UI-testing bundle, so it can never fire a lonely
///      XCUIApplication() launch from inside the unit-test host.
///   2. A 0.5B GGUF at `$GS_TEST_MODEL` or in Application Support.
///   3. Running under an XCUITest (ui-testing) target, not the AppTests unit
///      bundle — see native/docs/ISSUE-LOG.md; App/project.yml currently has
///      AppTests (bundle.unit-test) only, so making this EXECUTE requires a
///      one-target addition (`bundle.ui-testing`) plus scheme wiring.
final class GsChatUiTests: XCTestCase {

    /// Same provisioned-model rule as GsNativeTests.deviceModel().
    private func deviceModel() -> String? {
        if let env = ProcessInfo.processInfo.environment["GS_TEST_MODEL"],
           FileManager.default.fileExists(atPath: env) {
            return env
        }
        let support = try? FileManager.default.url(
            for: .applicationSupportDirectory, in: .userDomainMask,
            appropriateFor: nil, create: false)
        guard let dir = support else { return nil }
        let candidate = dir.appendingPathComponent("qwen2.5-0.5b-instruct-q4_k_m.gguf")
        return FileManager.default.fileExists(atPath: candidate.path) ? candidate.path : nil
    }

    func testSendHelloRendersARealAssistantReply() throws {
        guard ProcessInfo.processInfo.environment["GS_UI_TEST_MODE"] == "1" else {
            throw XCTSkip("set GS_UI_TEST_MODE=1 and run from a UI-testing target")
        }
        guard deviceModel() != nil else {
            throw XCTSkip("no 0.5B GGUF provisioned — never a fabricated pass")
        }

        let app = XCUIApplication()
        app.launch()

        // 1. tap the chat input, 2. type "hello".
        let field = app.textFields["Ask anything…"].firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 15),
                      "composer field never appeared")
        field.tap()
        field.typeText("hello")

        // 3. send it.
        let send = app.buttons["Send"].firstMatch
        XCTAssertTrue(send.waitForExistence(timeout: 5),
                      "Send button never appeared after typing")
        send.tap()

        // 4. a real reply bubble with non-blank assistant text within N s.
        //    The canned fallback's "hello" greeting is known; the real local
        //    model's is free-form, so we discriminate on the rendered text.
        let cannedPrefix = "Hey — good to see you."
        let deadline = Date().addingTimeInterval(180)
        var bubbleText: String?
        repeat {
            let matches = app.staticTexts
                .matching(NSPredicate(format: "label.length > 0 AND label != %@ AND label != %@ AND NOT (label BEGINSWITH %@)",
                                      "Ask anything…", "hello", "Search"))
                .allElementsBoundByIndex
                .map(\.label)
                .filter { $0 != cannedPrefix && !$0.isEmpty }
            bubbleText = matches.first
            if bubbleText != nil { break }
            Thread.sleep(forTimeInterval: 0.5)
        } while Date() < deadline

        XCTAssertNotNil(bubbleText,
                        "no assistant reply rendered within 180s of sending 'hello'")
        let reply = bubbleText ?? ""
        print("GsChatUiTests: reply bubble = \(reply.prefix(120))")
        XCTAssertFalse(reply.hasPrefix(cannedPrefix),
                       "rendered reply starts with the canned fallback prefix, " +
                       "i.e. it came from localReply(), NOT the local model")
        XCTAssertNotEqual(reply.trimmingCharacters(in: .whitespacesAndNewlines), "hello",
                          "reply is the sent prompt echoed back, not an answer")
    }
}
