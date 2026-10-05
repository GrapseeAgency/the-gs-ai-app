import XCTest

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
/// WIRING STATUS: WIRED. This file lives in ios/App/Tests/UITests/ and belongs
/// ONLY to the `GsChatUITests` bundle (bundle.ui-testing, ios/project.yml) —
/// XCUITest drives the app from OUTSIDE its process, so it cannot and must not
/// `@testable import GSApp`; that import compiled only while this file sat in
/// the unit-test host and made the test SKIP instead of run. The remaining
/// guards are the honest preconditions, not wiring gaps:
///   1. `GS_UI_TEST_MODE=1` — baked into the GSApp scheme's test action
///      (ios/project.yml environmentVariables).
///   2. A 0.5B GGUF at `$GS_TEST_MODEL` — ios-native.yml copies the verified
///      download to /tmp/gs-ui-model.gguf, the path the scheme bakes in.
///      /tmp on the runner host is visible inside the simulator.
///   3. The app is driven into a testable state with launch arguments that
///      GSApp reads: `-gs_test_session_active` (past Auth + Onboarding) and
///      `-gs_test_local_model <path>` (engine initialised with the model),
///      so the reply comes from the local engine rather than the canned
///      fallback. A reply that IS the canned prefix still FAILS this test —
///      the launch arguments are provisioning, not a result.
final class GsChatUiTests: XCTestCase {

    /// The model path, if one has been provisioned. `nil` means the model-backed
    /// tests skip.
    ///
    /// THE FILENAME IS NOT HARDCODED. It used to be:
    ///
    ///     dir.appendingPathComponent("qwen2.5-0.5b-instruct-q4_k_m.gguf")
    ///
    /// while `.github/workflows/ios-native.yml` provisions, into this very
    /// directory:
    ///
    ///     GGUF="$WORK/qwen2.5-0.5b-instruct-q4_0.gguf"
    ///     cp "$GGUF" "$SUPPORT/qwen2.5-0.5b-instruct-q4_0.gguf"
    ///
    /// So the model WAS provisioned and `deviceModel()` still returned `nil`, and
    /// every test gated on it skipped while reporting the cause as **"no model
    /// provisioned"** -- a statement that reads as a precondition which was not
    /// met, and is indistinguishable from one.
    ///
    /// A skip is the one result a reader cannot tell from success, so a stale
    /// literal here is worse than a wrong assertion: it removes the evidence rather
    /// than contradicting it.
    ///
    /// Same defect already fixed in three Android test files, where the filename
    /// comes from `ModelCatalog.MODEL_0_5B.id`. iOS has no catalogue, so the
    /// equivalent is to ask for the FAMILY and take what was provisioned --
    /// deterministically, sorted, so provisioning two quants cannot make it flaky.
    /// The quantisation is a provisioning decision and the pipeline makes it in
    /// one place; a test has no business restating it.
    private func deviceModel() -> String? {
        if let env = ProcessInfo.processInfo.environment["GS_TEST_MODEL"],
           FileManager.default.fileExists(atPath: env) {
            return env
        }
        let support = try? FileManager.default.url(
            for: .applicationSupportDirectory, in: .userDomainMask,
            appropriateFor: nil, create: false)
        guard let dir = support else { return nil }
        let family = "qwen2.5-0.5b-instruct-"
        let present = (try? FileManager.default.contentsOfDirectory(atPath: dir.path)) ?? []
        let matches = present
            .filter { $0.hasPrefix(family) && $0.hasSuffix(".gguf") }
            .sorted()
        guard let name = matches.first else { return nil }
        return dir.appendingPathComponent(name).path
    }

    func testSendHelloRendersARealAssistantReply() throws {
        guard ProcessInfo.processInfo.environment["GS_UI_TEST_MODE"] == "1" else {
            throw XCTSkip("set GS_UI_TEST_MODE=1 in the scheme's test action")
        }
        guard let model = deviceModel() else {
            throw XCTSkip("no 0.5B GGUF provisioned — never a fabricated pass")
        }

        // The launch arguments are the ONLY app state a UI test can set: the
        // test process is outside the app's sandbox and cannot touch its
        // UserDefaults or stores. GSApp reads both arguments in init.
        let app = XCUIApplication()
        app.launchArguments += ["-gs_test_session_active",
                                "-gs_test_local_model", model]
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
        //    NO `label.length`: XCUITest rejects that key path outright --
        //    raw, run 37284554031:
        //        Invalid key path label.length specified in predicate format
        //        string (XCTElementQueryInvalidPredicate)
        //    -- at this exact line, after the composer and Send both worked.
        //    `label != ''` says the same thing in valid syntax.
        let cannedPrefix = "Hey — good to see you."
        let deadline = Date().addingTimeInterval(180)
        var bubbleText: String?
        repeat {
            let matches = app.staticTexts
                .matching(NSPredicate(format: "label != '' AND label != %@ AND label != %@ AND NOT (label BEGINSWITH %@)",
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
