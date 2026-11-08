import XCTest

final class PlatformRuntimeUITests: XCTestCase {
    func testContextualEditOpensActualCodeAndExportOpensSystemDocumentPicker() {
        let app = XCUIApplication()
        app.launchArguments = ["-gs_test_session_active", "-gs_test_platform_runtime"]
        app.launch()
        XCTAssertTrue(app.buttons["Open generated code"].waitForExistence(timeout: 20))
        app.buttons["Open generated code"].tap()
        XCTAssertTrue(app.buttons["Edit Code"].waitForExistence(timeout: 10))
        app.buttons["Edit Code"].tap()
        let editor = app.textViews.firstMatch
        XCTAssertTrue(editor.waitForExistence(timeout: 10), "Contextual Edit must open the existing editor, not request another model answer")
        XCTAssertTrue((editor.value as? String ?? "").contains("console.log"))
        XCTAssertFalse((editor.value as? String ?? "").contains("😀"))
        app.buttons["Done"].tap()
        app.buttons["Download Project"].tap()
        XCTAssertTrue(app.buttons["Cancel"].firstMatch.waitForExistence(timeout: 15), "Export must present the real system document picker")
        XCTAssertGreaterThan(app.navigationBars.count, 0)
        let capture = XCTAttachment(screenshot: app.screenshot())
        capture.name = "genui-contextual-real-source-export-picker"; capture.lifetime = .keepAlways; add(capture)
        app.buttons["Cancel"].firstMatch.tap()
    }

    func testContextualReportShowsActualDataAndSharePresentsSystemActivitySheet() {
        let app = XCUIApplication()
        app.launchArguments = ["-gs_test_session_active", "-gs_test_platform_runtime"]
        app.launch()
        XCTAssertTrue(app.buttons["Open generated table"].waitForExistence(timeout: 20))
        app.buttons["Open generated table"].tap()
        app.buttons["Use ordinary Markdown"].tap()
        XCTAssertTrue(app.buttons["View Full Report"].waitForExistence(timeout: 10))
        app.buttons["View Full Report"].tap()
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "Item 31")).firstMatch.waitForExistence(timeout: 10), "Report must include every supplied row, not just the visible page")
        app.buttons["Close details"].tap()
        app.buttons["Share"].tap()
        XCTAssertTrue(app.buttons["Copy"].firstMatch.waitForExistence(timeout: 15), "Share must present the real activity sheet, not copy silently")
        let capture = XCTAttachment(screenshot: app.screenshot())
        capture.name = "genui-contextual-real-system-share-sheet"; capture.lifetime = .keepAlways; add(capture)
    }

    func testGeneratedTerminalUsesRealFilesWorkerAndUnsupportedRefusal() {
        let app = XCUIApplication()
        app.launchArguments = ["-gs_test_session_active", "-gs_test_platform_runtime"]
        app.launch()
        XCTAssertTrue(app.buttons["Open generated terminal"].waitForExistence(timeout: 20))
        app.buttons["Open generated terminal"].tap()
        let input = app.webViews.textFields["Terminal command"].firstMatch
        XCTAssertTrue(input.waitForExistence(timeout: 15))
        func command(_ value: String) {
            input.tap()
            input.typeText(value)
            app.webViews.buttons["Run"].firstMatch.tap()
            XCTAssertEqual(input.value as? String, "", "Real form submission must consume the command")
        }
        func output(_ value: String) -> XCUIElement {
            app.webViews.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", value)).firstMatch
        }
        command("mkdir project"); command("cd project"); command("touch actual.txt"); command("ls")
        XCTAssertTrue(app.webViews.staticTexts["actual.txt"].waitForExistence(timeout: 10))
        command("rm actual.txt"); command("ls")
        XCTAssertTrue(app.webViews.staticTexts["(empty)"].waitForExistence(timeout: 10))
        command("js [1,2,3].map(n=>n*2)")
        XCTAssertTrue(output("[2,4,6]").waitForExistence(timeout: 15), "A genuine Worker must compute array output")
        command("js while(true) {}")
        XCTAssertTrue(output("exceeded 2 seconds").waitForExistence(timeout: 15), "Actual infinite execution must stop")
        command("python print(7)")
        XCTAssertTrue(output("python: not executed").waitForExistence(timeout: 10))
        XCTAssertFalse(app.webViews.buttons["Run again"].exists, "Unsupported execution must remove stale Worker output")
        command("deploy")
        XCTAssertTrue(output("deploy: not executed").waitForExistence(timeout: 10), "Model-provided output must never become fake execution")
        XCTAssertFalse(output("😀").exists)
        app.webViews.buttons["Reset session"].tap()
        command("ls")
        XCTAssertTrue(app.webViews.staticTexts["README.md"].waitForExistence(timeout: 10))
        XCTAssertFalse(output("deploy: not executed").exists, "Reset must clear the prior actual session")
        let evidence = XCTAttachment(screenshot: app.screenshot())
        evidence.name = "genui-terminal-real-files-worker-and-refusal"
        evidence.lifetime = .keepAlways
        add(evidence)
    }

    func testGeneratedCodeCopyEditRunAndInvalidDraftReset() {
        let app = XCUIApplication()
        app.launchArguments = ["-gs_test_session_active", "-gs_test_platform_runtime"]
        app.launch()
        XCTAssertTrue(app.buttons["Open generated code"].waitForExistence(timeout: 20))
        app.buttons["Open generated code"].tap()
        XCTAssertTrue(app.buttons["Copy code"].waitForExistence(timeout: 10))
        app.buttons["Copy code"].tap()
        app.buttons["Inspect copied source"].tap()
        XCTAssertTrue(app.staticTexts["Original source copied"].waitForExistence(timeout: 10), "Copy must not substitute display escapes")
        app.buttons["Run Code"].tap()
        let output = app.webViews.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "16 2")).firstMatch
        XCTAssertTrue(output.waitForExistence(timeout: 15), "Native Run must execute actual Unicode-key computation")
        app.buttons["Edit"].tap()
        let editor = app.textViews.firstMatch
        XCTAssertTrue(editor.waitForExistence(timeout: 10))
        XCTAssertFalse((editor.value as? String ?? "").contains("😀"), "Editor must use reversible escapes")
        editor.tap()
        editor.typeText("\n\"unterminated")
        app.buttons["Run Code"].tap()
        XCTAssertTrue(app.staticTexts["Not executed: correct the JSON-encoded source draft first."].waitForExistence(timeout: 10))
        XCTAssertEqual(app.webViews.count, 0, "Invalid Run must not leave a stale successful execution visible")
        app.buttons["Reset"].tap()
        XCTAssertTrue(app.buttons["Edit"].waitForExistence(timeout: 10))
        XCTAssertFalse(editor.exists)
        app.buttons["Run Code"].tap()
        XCTAssertTrue(output.waitForExistence(timeout: 15), "Reset must restore the original executable source")
        let evidence = XCTAttachment(screenshot: app.screenshot())
        evidence.name = "genui-code-actual-copy-edit-run-reset"
        evidence.lifetime = .keepAlways
        add(evidence)
    }

    func testGeneratedChartRetainsAllPointsSelectsSeriesAndRejectsSignedPie() {
        let app = XCUIApplication()
        app.launchArguments = ["-gs_test_session_active", "-gs_test_platform_runtime"]
        app.launch()
        let open = app.buttons["Open generated chart"]
        XCTAssertTrue(open.waitForExistence(timeout: 20))
        open.tap()
        XCTAssertTrue(app.buttons["All (31 pts) ▾"].waitForExistence(timeout: 10), "Chart truncated its actual 31 observations")
        app.buttons["Pie"].tap()
        XCTAssertTrue(app.staticTexts["Pie shares require nonnegative values and a positive total. Choose Line or Bar for this dataset."].waitForExistence(timeout: 10))
        app.buttons["Bar"].tap()
        XCTAssertTrue(app.buttons["All (31 pts) ▾"].exists)
        app.buttons["Series: Primary"].tap()
        let second = app.buttons["Secondary"]
        XCTAssertTrue(second.waitForExistence(timeout: 10))
        second.tap()
        XCTAssertTrue(app.buttons["All (3 pts) ▾"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["Series: Secondary"].exists)
        app.buttons["All (3 pts) ▾"].tap()
        XCTAssertTrue(app.buttons["Last 3 pts ▾"].waitForExistence(timeout: 10))
        app.buttons["Last 3 pts ▾"].tap()
        XCTAssertTrue(app.buttons["Last 2 pts ▾"].waitForExistence(timeout: 10))
        let evidence = XCTAttachment(screenshot: app.screenshot())
        evidence.name = "genui-chart-real-series-selection-and-range"
        evidence.lifetime = .keepAlways
        add(evidence)
    }

    func testGeneratedTableAllDisplaysEveryActualRow() {
        let app = XCUIApplication()
        app.launchArguments = ["-gs_test_session_active", "-gs_test_platform_runtime"]
        app.launch()
        let open = app.buttons["Open generated table"]
        XCTAssertTrue(open.waitForExistence(timeout: 20))
        open.tap()
        func show(_ label: String) -> XCUIElement {
            let button = app.buttons[label]
            for _ in 0..<18 {
                if button.exists && button.isHittable { return button }
                app.swipeUp()
            }
            XCTFail("Real table never exposed \(label)")
            return button
        }
        show("Show: 5 ▾").tap()
        show("Show: 10 ▾").tap()
        XCTAssertTrue(show("Show: All ▾").exists)
        XCTAssertTrue(app.staticTexts["Item 31"].exists)
        XCTAssertTrue(app.staticTexts["1–31 of 31"].exists)
        let evidence = XCTAttachment(screenshot: app.screenshot())
        evidence.name = "genui-table-all-31-real-rows"
        evidence.lifetime = .keepAlways
        add(evidence)
        app.buttons["Use ordinary Markdown"].tap()
        show("Show: 5 ▾").tap()
        show("Show: 10 ▾").tap()
        XCTAssertTrue(show("Show: All ▾").exists)
        XCTAssertTrue(app.staticTexts["Item 31"].exists)
        XCTAssertTrue(app.staticTexts["1–31 of 31"].exists)
        // Inspect actual native text, not just a string-normalizer result.
        XCTAssertEqual(app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "😀")).count, 0)
        let markdownEvidence = XCTAttachment(screenshot: app.screenshot())
        markdownEvidence.name = "genui-markdown-table-all-31-real-rows"
        markdownEvidence.lifetime = .keepAlways
        add(markdownEvidence)
    }

    func testGeneratedCalculatorUsesActualTypedNumbersAndNoEmoji() {
        let app = XCUIApplication()
        app.launchArguments = ["-gs_test_session_active", "-gs_test_platform_runtime"]
        app.launch()
        let open = app.buttons["Open generated calculator"]
        XCTAssertTrue(open.waitForExistence(timeout: 20))
        open.tap()
        let field = app.textFields["calc-input-amount"]
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        XCTAssertEqual(app.sliders.count, 0, "Unbounded model field invented a slider range")
        XCTAssertEqual(app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "©")).count, 0)
        XCTAssertEqual(app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "😀")).count, 0)
        // A centre tap can place the caret BEFORE right-aligned text. Move
        // it to the trailing edge explicitly before issuing backspaces.
        field.coordinate(withNormalizedOffset: CGVector(dx: 0.98, dy: 0.5)).tap()
        let initial = field.value as? String ?? ""
        field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: initial.count) + "12.3456")
        XCTAssertEqual(field.value as? String, "12.3456", "Typed input was rounded or reformatted")
        XCTAssertTrue(app.staticTexts["24.69"].waitForExistence(timeout: 10))
        field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 7))
        XCTAssertTrue(app.staticTexts["Enter valid inputs"].waitForExistence(timeout: 10))
        app.buttons["Reset"].tap()
        XCTAssertTrue(app.staticTexts["200"].waitForExistence(timeout: 10))
        let evidence = XCTAttachment(screenshot: app.screenshot())
        evidence.name = "genui-calculator-typed-reset-no-emoji"
        evidence.lifetime = .keepAlways
        add(evidence)
    }

    func testBackgroundNoticeIsDeliveredAndDisabledKindsAreNot() {
        let app = XCUIApplication()
        app.launchArguments = ["-gs_test_session_active", "-gs_test_platform_runtime"]
        app.launch()
        defer {
            app.activate()
            app.buttons["Restore runtime settings"].tap()
        }
        let permission = addUIInterruptionMonitor(withDescription: "Notification permission") { alert in
            let allow = alert.buttons["Allow"]
            guard allow.exists else { return false }
            allow.tap()
            return true
        }
        defer { removeUIInterruptionMonitor(permission) }
        let status = app.staticTexts["platform-runtime-status"]
        XCTAssertTrue(status.waitForExistence(timeout: 20))

        for (button, mode, result) in [
            ("Arm allowed notice", "allowed", "os-delivered"),
            ("Arm master-disabled notice", "master", "os-blocked"),
            ("Arm messages-disabled notice", "messages", "os-blocked")
        ] {
            app.buttons[button].tap()
            // On a fresh install the OS alert belongs to SpringBoard. Handle
            // it explicitly rather than making permission denial a test skip.
            let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
            let allow = springboard.alerts.buttons["Allow"]
            if allow.waitForExistence(timeout: 2) { allow.tap() }
            let armed = XCTNSPredicateExpectation(
                predicate: NSPredicate(format: "label == %@", "armed-\(mode)"), object: status)
            XCTAssertEqual(XCTWaiter.wait(for: [armed], timeout: 15), .completed,
                "Could not arm actual notifier: \(status.label)")
            XCUIDevice.shared.press(.home)
            XCTAssertTrue(app.wait(for: .runningBackground, timeout: 10))
            // Leave the app backgrounded beyond the probe's one-second delay.
            // This is a finite background task, not a scheduled OS wake.
            let window = expectation(description: "background continuation window")
            DispatchQueue.main.asyncAfter(deadline: .now() + 3) { window.fulfill() }
            wait(for: [window], timeout: 5)
            app.activate()
            app.buttons["Inspect OS delivery"].tap()
            let observed = XCTNSPredicateExpectation(
                predicate: NSPredicate(format: "label == %@", result), object: status)
            XCTAssertEqual(XCTWaiter.wait(for: [observed], timeout: 15), .completed,
                "Expected \(result), got \(status.label)")
            let evidence = XCTAttachment(screenshot: app.screenshot())
            evidence.name = "platform-notice-\(mode)"
            evidence.lifetime = .keepAlways
            add(evidence)
        }
    }

    func testLongPressPinSurvivesBackgroundAndRelaunch() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-gs_test_session_active", "-gs_test_seed_chats"]
        app.launch()
        func openChats() {
            let chats = app.buttons["Chats"].firstMatch
            XCTAssertTrue(chats.waitForExistence(timeout: 20))
            chats.tap()
            XCTAssertTrue(app.buttons["Archived"].waitForExistence(timeout: 15))
        }
        func openRowMenu(_ stage: String) throws {
            let row = app.buttons.matching(
                NSPredicate(format: "label CONTAINS %@", "Explain quantum physics")).firstMatch
            func pressIfVisible() -> Bool {
                guard row.exists && row.isHittable else { return false }
                row.press(forDuration: 1.2)
                return true
            }
            if row.waitForExistence(timeout: 5), pressIfVisible() { return }
            // This is a virtualized List, not an eagerly rendered fixture.
            // Pinning also moves a row to the top without promising to reset
            // its scroll offset. Locate it with real gestures in either
            // direction; never reseed/reset state after pinning.
            for _ in 0..<8 {
                app.swipeDown()
                if pressIfVisible() { return }
            }
            for _ in 0..<24 {
                app.swipeUp()
                if pressIfVisible() { return }
            }
            XCTFail("Persisted chat was not reachable in the real list: \(stage)")
            throw NSError(domain: "PlatformRuntimeUITests.missingPersistedRow", code: 1,
                userInfo: [NSLocalizedDescriptionKey: stage])
        }
        openChats()
        try openRowMenu("initial fixture")
        // Existing menu tests may have touched this idempotent fixture.
        if app.buttons["Unpin"].waitForExistence(timeout: 2) {
            app.buttons["Unpin"].tap()
            try openRowMenu("after normalizing an existing pin")
        }
        let pin = app.buttons["Pin to top"]
        XCTAssertTrue(pin.waitForExistence(timeout: 10))
        pin.tap()
        XCUIDevice.shared.press(.home)
        XCTAssertTrue(app.wait(for: .runningBackground, timeout: 10))
        app.activate()
        try openRowMenu("after background continuation")
        XCTAssertTrue(app.buttons["Unpin"].waitForExistence(timeout: 10), "Pin did not survive background")
        // Relaunch WITHOUT reseeding: verify persisted data, not a second fixture.
        app.terminate()
        app.launchArguments = ["-gs_test_session_active"]
        app.launch()
        openChats()
        try openRowMenu("after process relaunch without reseeding")
        let unpin = app.buttons["Unpin"]
        XCTAssertTrue(unpin.waitForExistence(timeout: 10), "Pin did not survive process relaunch")
        let evidence = XCTAttachment(screenshot: app.screenshot())
        evidence.name = "platform-longpress-persisted-pin"
        evidence.lifetime = .keepAlways
        add(evidence)
        unpin.tap()
    }
}
