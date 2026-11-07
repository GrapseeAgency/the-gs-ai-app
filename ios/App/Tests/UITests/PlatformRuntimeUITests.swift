import XCTest

final class PlatformRuntimeUITests: XCTestCase {
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

    func testLongPressPinSurvivesBackgroundAndRelaunch() {
        let app = XCUIApplication()
        app.launchArguments = ["-gs_test_session_active", "-gs_test_seed_chats"]
        app.launch()
        func openChats() {
            let chats = app.buttons["Chats"].firstMatch
            XCTAssertTrue(chats.waitForExistence(timeout: 20))
            chats.tap()
            XCTAssertTrue(app.buttons["Archived"].waitForExistence(timeout: 15))
        }
        func openRowMenu() {
            let row = app.buttons.matching(
                NSPredicate(format: "label CONTAINS %@", "Explain quantum physics")).firstMatch
            XCTAssertTrue(row.waitForExistence(timeout: 15))
            row.press(forDuration: 1.2)
        }
        openChats()
        openRowMenu()
        // Existing menu tests may have touched this idempotent fixture.
        if app.buttons["Unpin"].waitForExistence(timeout: 2) {
            app.buttons["Unpin"].tap()
            openRowMenu()
        }
        let pin = app.buttons["Pin to top"]
        XCTAssertTrue(pin.waitForExistence(timeout: 10))
        pin.tap()
        XCUIDevice.shared.press(.home)
        XCTAssertTrue(app.wait(for: .runningBackground, timeout: 10))
        app.activate()
        openRowMenu()
        XCTAssertTrue(app.buttons["Unpin"].waitForExistence(timeout: 10), "Pin did not survive background")
        // Relaunch WITHOUT reseeding: verify persisted data, not a second fixture.
        app.terminate()
        app.launchArguments = ["-gs_test_session_active"]
        app.launch()
        openChats()
        openRowMenu()
        let unpin = app.buttons["Unpin"]
        XCTAssertTrue(unpin.waitForExistence(timeout: 10), "Pin did not survive process relaunch")
        let evidence = XCTAttachment(screenshot: app.screenshot())
        evidence.name = "platform-longpress-persisted-pin"
        evidence.lifetime = .keepAlways
        add(evidence)
        unpin.tap()
    }
}
