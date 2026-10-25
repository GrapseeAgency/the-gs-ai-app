import XCTest

/// Blocker 4 — a real UI test, the iOS counterpart of Android's
/// `ChatUiReplyTest`.
///
/// Drives the app the way a user does: finds the chat composer's text field
/// (placeholder "Ask me anything..." — NexlyComposerBar,
/// ios/App/Sources/Features/Chats/NexlyChatShell.swift), types "hello", taps
/// the Send button (accessibilityLabel "Send"), and asserts a real, non-blank
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

    /// The composer's placeholder, verbatim from NexlyComposerBar
    /// (ios/App/Sources/Features/Chats/NexlyChatShell.swift):
    ///
    ///     TextField("Ask me anything...", text: $text, axis: .vertical)
    ///
    /// THE STRING THIS SUITE USED TO LOOK FOR NO LONGER EXISTS. Until
    /// 5f78d9f2 the queries asked for "Ask anything…" — AeroInputBar's
    /// DEFAULT placeholder, which no conversation screen passes since the
    /// reference redesign moved the composer into NexlyComposerBar. So every
    /// probe at every element type was pointed at a string the app does not
    /// render, and runs 40116678867 through 40167513280 all failed with
    /// "composer field never appeared" while the composer was on screen the
    /// whole time. (Run 40167513280's tap failure even names the element in
    /// its "Possibly caused by" hint — a SwiftUI.VerticalTextView, i.e. this
    /// exact TextField — under a query for the wrong identifier.)
    ///
    /// NOTE the three ASCII dots, not "…": ExploreView.swift carries a
    /// DIFFERENT field whose placeholder is "Ask me anything…" with a real
    /// ellipsis. The conversation composer is the ASCII-dots spelling, and a
    /// test that mixed them up would quietly match the wrong screen's field.
    private static let composerPlaceholder = "Ask me anything..."

    /// The composer, whichever control the current simulator image exposes.
    ///
    /// The field is a `SwiftUI.VerticalTextView` on newer images and answers
    /// as a `UITextField` on older ones, and on some images the two
    /// classifications disagree with each other (run 40167513280:
    /// "Automation type mismatch: computed TextField from legacy attributes
    /// vs TextView from modern attribute") so NEITHER typed query resolves.
    /// The third probe matches any element type by identifier or label, which
    /// cannot be defeated by the classification — it asks for the string,
    /// not the control.
    private func composerElement(_ app: XCUIApplication, timeout: TimeInterval) -> XCUIElement {
        let field = app.textFields[Self.composerPlaceholder].firstMatch
        let view = app.textViews[Self.composerPlaceholder].firstMatch
        let anyTyped = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier == %@ OR label == %@",
                                  Self.composerPlaceholder, Self.composerPlaceholder))
            .firstMatch
        let deadline = Date().addingTimeInterval(timeout)
        repeat {
            if field.exists { return field }
            if view.exists { return view }
            if anyTyped.exists { return anyTyped }
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        } while Date() < deadline
        return field
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

        // The launch arguments are the ONLY app state a UI test can set: the
        // test process is outside the app's sandbox and cannot touch its
        // UserDefaults or stores. GSApp reads both arguments in init.
        let app = XCUIApplication()
        app.launchArguments += ["-gs_test_session_active",
                                "-gs_test_local_model", model]
        app.launch()

        // The app opens on Home since the reference redesign (the composer
        // lives inside a conversation), so reach a chat first. Without this the
        // suite looked for the composer on the dashboard and failed at 15 s —
        // which is what kept the whole iOS gate red.
        let composer = composerElement(app, timeout: 6)
        if !composer.exists {
            let newChat = app.buttons["New Chat"].firstMatch
            if newChat.waitForExistence(timeout: 5) {
                newChat.tap()
            } else {
                let generate = app.buttons["Generate"].firstMatch
                XCTAssertTrue(generate.waitForExistence(timeout: 5),
                              "Home exposed neither New Chat nor Generate, so no chat can be opened")
                generate.tap()
            }
        }

        // 1. tap the chat input, 2. type "hello".
        let field = composerElement(app, timeout: 20)
        if !field.exists {
            // THE ACCESSIBILITY TREE IS THE EVIDENCE. A "never appeared"
            // failure without the tree is a guess about what was on screen;
            // with it, the next reader can see which element type and
            // identifier the composer really exposed. Printed (the workflow
            // uploads the test log) and attached (it rides the result bundle).
            let tree = app.debugDescription
            print("GsChatUiTests: composer '\(Self.composerPlaceholder)' absent; tree:\n\(tree)")
            let attachment = XCTAttachment(string: tree)
            attachment.name = "composer-missing-tree"
            attachment.lifetime = .keepAlways
            add(attachment)
        }
        XCTAssertTrue(field.exists, "composer field never appeared")
        // THE TAP CAN BE DROPPED WHILE THE APP IS STILL LOADING THE MODEL.
        // GSApp.init initialises the GGUF synchronously, so the composer is in
        // the tree long before the main thread is free. On run 39342295960
        // field.tap() was accepted at t=37.6s and typeText() then failed with
        // "Neither element nor any descendant has keyboard focus": the tap
        // landed while the field could not become first responder. Tap, prove
        // focus by requiring the keyboard, retry once, then type.
        let keyboard = app.keyboards.firstMatch
        field.tap()
        if !keyboard.waitForExistence(timeout: 10) {
            field.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }
        XCTAssertTrue(keyboard.waitForExistence(timeout: 15),
                      "composer never took keyboard focus, so the send path cannot be typed")
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
                                  Self.composerPlaceholder, "hello", "Search"))
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
