import XCTest

/// Blocker 4 — a real UI test, the iOS counterpart of Android's
/// `ChatUiReplyTest`.
///
/// Drives the app through its actual first-chat route: Home → NewChatView's
/// `new-chat-input` → `new-chat-send` → the `.chatAutoSend` destination. That
/// route enters the production `ChatDetailView` / `ChatViewModel.send` path,
/// and the test asserts that the rendered answer came from the loaded local
/// model rather than the canned fallback.
///
/// WIRING STATUS: WIRED. This file belongs ONLY to the `GsChatUITests` bundle
/// (bundle.ui-testing, ios/project.yml). XCUITest drives the app from OUTSIDE
/// its process, so it cannot and must not `@testable import GSApp`. The scheme
/// supplies `GS_UI_TEST_MODE=1` and `GS_TEST_MODEL`; ios-native.yml stages the
/// verified 0.5B GGUF at that path. The app launch arguments initialize that
/// exact model and use the process-scoped, DEBUG-only
/// `-gs_test_prefer_local` override, without changing the saved user setting.
final class GsChatUiTests: XCTestCase {

    private static let newChatInputIdentifier = "new-chat-input"
    private static let chatComposerPlaceholder = "Ask anything..."

    /// NewChatView uses a vertical SwiftUI TextField whose UIKit automation
    /// classification can vary by simulator. Query by its stable identifier,
    /// independent of whether XCTest reports it as a text field or text view.
    private func newChatInputElement(_ app: XCUIApplication, timeout: TimeInterval) -> XCUIElement {
        let input = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier == %@", Self.newChatInputIdentifier))
            .firstMatch
        let deadline = Date().addingTimeInterval(timeout)
        repeat {
            if input.exists { return input }
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        } while Date() < deadline
        return input
    }

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

    /// Transcript chrome that is a plain `Text` and therefore shows up among
    /// the reply candidates. Run 40179201146 — the first run green through
    /// this test — printed `reply bubble = 9:27 AM`: the clock stamp under a
    /// turn (`GSFormatters.clock`, format "h:mm a") is the BOTTOM-most
    /// staticText, so the content assertions ran against a timestamp and
    /// proved nothing about the reply. These three patterns are the
    /// transcript's own furniture — clock stamps, day separators ("Today",
    /// "Yesterday", "d MMM yyyy", "yyyy-MM-dd") — and none of them can be the
    /// model's answer to "hello".
    private static func isTranscriptChrome(_ label: String) -> Bool {
        let t = label.trimmingCharacters(in: .whitespacesAndNewlines)
        if t == "Today" || t == "Yesterday" { return true }
        for pattern in ["^\\d{1,2}:\\d{2}\\s?(AM|PM)?$",
                        "^\\d{1,2} [A-Za-z]{3} \\d{4}$",
                        "^\\d{4}-\\d{2}-\\d{2}$"] {
            if t.range(of: pattern, options: .regularExpression) != nil { return true }
        }
        return false
    }

    func testSendHelloRendersARealAssistantReply() throws {
        guard ProcessInfo.processInfo.environment["GS_UI_TEST_MODE"] == "1" else {
            throw XCTSkip("set GS_UI_TEST_MODE=1 in the scheme's test action")
        }
        guard let model = deviceModel() else {
            throw XCTSkip("no 0.5B GGUF provisioned — never a fabricated pass")
        }

        // The app starts on Home. New Chat opens the real pre-chat composer;
        // sending there routes through .chatAutoSend into the conversation's
        // normal ChatViewModel.send implementation. The DEBUG-only override
        // selects the loaded engine for this process without persisting a
        // preference change across other UI tests.
        let app = XCUIApplication()
        app.launchArguments += ["-gs_test_session_active",
                                "-gs_test_local_model", model,
                                "-gs_test_prefer_local"]
        app.launch()

        let newChat = app.buttons["New Chat"].firstMatch
        if newChat.waitForExistence(timeout: 30) {
            newChat.tap()
        } else {
            let generate = app.buttons["Generate"].firstMatch
            guard generate.waitForExistence(timeout: 10) else {
                let tree = app.debugDescription
                print("GsChatUiTests: Home exposed neither New Chat nor Generate; tree:\n\(tree)")
                let attachment = XCTAttachment(string: tree)
                attachment.name = "new-chat-entry-missing-tree"
                attachment.lifetime = .keepAlways
                add(attachment)
                XCTFail("Home exposed neither New Chat nor Generate, so no chat can be opened")
                return
            }
            generate.tap()
        }

        let field = newChatInputElement(app, timeout: 20)
        if !field.exists {
            // Preserve the live accessibility tree with the failure; the
            // pre-chat field has a different identity from ChatDetailView's
            // later `Ask anything...` composer.
            let tree = app.debugDescription
            print("GsChatUiTests: `\(Self.newChatInputIdentifier)` absent; tree:\n\(tree)")
            let attachment = XCTAttachment(string: tree)
            attachment.name = "new-chat-input-missing-tree"
            attachment.lifetime = .keepAlways
            add(attachment)
        }
        guard field.exists else {
            XCTFail("NewChatView's `new-chat-input` field never appeared")
            return
        }

        let keyboard = app.keyboards.firstMatch
        field.tap()
        if !keyboard.waitForExistence(timeout: 10) {
            field.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }
        guard keyboard.waitForExistence(timeout: 15) else {
            XCTFail("NewChatView's input never took keyboard focus, so the send path cannot be typed")
            return
        }
        field.typeText("hello")

        let send = app.buttons["new-chat-send"].firstMatch
        guard send.waitForExistence(timeout: 5) else {
            XCTFail("NewChatView's Send button never appeared after typing")
            return
        }
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
        //
        //    THE POST-SEND FREEZE, MEASURED IN TWO RUNS OF THE SAME COMMIT.
        //    Sending hands the main thread to the engine: prompt evaluation
        //    plus the stream's per-token relayout keep the app unable to
        //    answer XCUITest's snapshot requests for ~95–105 s —
        //        run 40181545717 (green): probe hung 70→161 s, 3rd internal
        //            retry landed just past the freeze;
        //        run 40181550214 (red):   probe hung 53→146 s, retries ran
        //            out 12 s early and the query FAILED the test
        //                "Failed to get matching snapshots: Timed out while
        //                 evaluating UI query"
        //    XCUITest's own retry ladder is three attempts ~31 s apart (~93 s
        //    total) — shorter than the freeze. ANY query issued inside the
        //    window — Stop probe, transcript read, allElementsBoundByIndex —
        //    is a coin flip on whether the ladder outlasts the freeze, and a
        //    lost flip is an uncatchable test failure. So the test issues NO
        //    query at all for 120 s after Send (the measured window plus
        //    margin), and only then begins probing whether the composer's
        //    Stop control (`NexlyComposerBar` shows it exactly while
        //    `isStreaming`) has gone.
        let cannedPrefix = "Hey — good to see you."
        let stopGenerating = app.buttons["Stop generating"].firstMatch
        let replyQuery = app.staticTexts
            .matching(NSPredicate(format: "label != '' AND label != %@ AND label != %@ AND NOT (label BEGINSWITH %@)",
                                  Self.chatComposerPlaceholder, "hello", "Search"))
        // 360 s, not the old 180/300: 120 s of named quiet wait plus a
        // generation the runner itself measures at ~135 s worst case
        // (GS-PERF tapToFirstVisibleCharMs=41704), still inside the
        // scheme's 600 s per-test allowance.
        let deadline = Date().addingTimeInterval(360)
        Thread.sleep(forTimeInterval: 120)
        var bubbleText: String?
        while Date() < deadline {
            // waitForExistence, NOT a bare `.exists`: the waiter retries
            // inside XCUITest and returns false on timeout, where a direct
            // snapshot resolution that runs slow is recorded as a test
            // failure — the exact error this loop exists to avoid.
            if stopGenerating.waitForExistence(timeout: 2) {
                Thread.sleep(forTimeInterval: 5)
                continue
            }
            // Settle, then CONFIRM: a probe that timed out mid-stream reads
            // as "absent", and querying the transcript on that false
            // negative re-enters the freeze the quiet wait just avoided.
            Thread.sleep(forTimeInterval: 2)
            if stopGenerating.waitForExistence(timeout: 2) { continue }
            // The LAST match, not the first: accessibility order follows the
            // screen top-to-bottom, and the reply is the newest bubble at the
            // bottom — the first match is a header or chrome text (eight
            // staticTexts matched this predicate in run 40176354400, and the
            // conversation had exactly one reply). Transcript chrome (clock
            // stamps, day separators) is filtered out by isTranscriptChrome
            // so the chosen text is the assistant's own words, not the
            // furniture around them.
            if let text = replyQuery.allElementsBoundByIndex
                .map(\.label)
                .filter({ !$0.isEmpty && $0 != cannedPrefix && !Self.isTranscriptChrome($0) })
                .last {
                bubbleText = text
                break
            }
        }

        if bubbleText == nil {
            // The labels the query DID see, so a red run names what was on
            // screen instead of making the reader guess which chrome text
            // beat the reply.
            let seen = replyQuery.allElementsBoundByIndex.map(\.label)
            print("GsChatUiTests: no reply candidate; transcript labels = \(seen)")
        }
        XCTAssertNotNil(bubbleText,
                        "no assistant reply rendered within 360s of sending 'hello'")
        let reply = bubbleText ?? ""
        print("GsChatUiTests: reply bubble = \(reply.prefix(120))")
        XCTAssertFalse(reply.hasPrefix(cannedPrefix),
                       "rendered reply starts with the canned fallback prefix, " +
                       "i.e. it came from localReply(), NOT the local model")
        XCTAssertNotEqual(reply.trimmingCharacters(in: .whitespacesAndNewlines), "hello",
                          "reply is the sent prompt echoed back, not an answer")
    }
}
