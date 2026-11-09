import XCTest
import CoreGraphics
import UIKit

final class PlatformRuntimeUITests: XCTestCase {
    private func printScreenshotPixels(_ screenshot: XCUIScreenshot, label: String) {
        let encoded = screenshot.pngRepresentation.base64EncodedString()
        let count = (encoded.count + 3499) / 3500
        for (index, start) in stride(from: 0, to: encoded.count, by: 3500).enumerated() {
            print("GS_UI_PNG_V1 \(label) \(index + 1)/\(count) " + String(encoded.dropFirst(start).prefix(3500)))
        }
    }

    /// iOS 26 Files exposes its ellipsis as an AX element named Cancel. Find
    /// the actual leading navigation glyph in the composited toolbar pixels,
    /// not that incorrect label or a guessed fraction of the phone screen.
    private func visibleFilesNavigationPoint(_ screenshot: XCUIScreenshot, application: CGRect, toolbar: CGRect) -> CGPoint? {
        guard let image = screenshot.image.cgImage, application.width > 0, application.height > 0 else { return nil }
        let width = image.width, height = image.height
        let sx = CGFloat(width) / application.width, sy = CGFloat(height) / application.height
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        let drawn = pixels.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(data: buffer.baseAddress, width: width, height: height,
                bitsPerComponent: 8, bytesPerRow: width * 4, space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGBitmapInfo.byteOrder32Big.rawValue | CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
            context.draw(image, in: CGRect(x: 0, y: 0, width: CGFloat(width), height: CGFloat(height)))
            return true
        }
        guard drawn else { return nil }
        let y0 = max(0, Int((toolbar.minY - application.minY) * sy))
        let y1 = min(height - 1, Int((toolbar.maxY - application.minY) * sy))
        guard y0 < y1 else { return nil }
        let x1 = width / 2
        func dark(_ x: Int, _ y: Int) -> Bool {
            let p = (y * width + x) * 4
            return pixels[p + 3] > 200 && max(pixels[p], max(pixels[p + 1], pixels[p + 2])) < 120
        }
        var seed: (Int, Int)?
        for x in 0..<x1 {
            if let y = (y0...y1).first(where: { dark(x, $0) }) { seed = (x, y); break }
        }
        guard let seed else { return nil }
        var pending = [seed], visited = Set<Int>()
        var minX = seed.0, maxX = seed.0, minY = seed.1, maxY = seed.1
        while let (x, y) = pending.popLast() {
            guard x >= 0, x < x1, y >= y0, y <= y1, visited.insert(y * width + x).inserted, dark(x, y) else { continue }
            minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y)
            for dx in -1...1 { for dy in -1...1 where dx != 0 || dy != 0 { pending.append((x + dx, y + dy)) } }
        }
        let glyphWidth = CGFloat(maxX - minX + 1) / sx, glyphHeight = CGFloat(maxY - minY + 1) / sy
        guard glyphWidth >= 2, glyphWidth <= 36, glyphHeight >= 6, glyphHeight <= 44 else { return nil }
        let point = CGPoint(x: application.minX + CGFloat(minX + maxX) / (2 * sx), y: application.minY + CGFloat(minY + maxY) / (2 * sy))
        print("GS_FILES_VISIBLE_GLYPH: bounds=(\(minX),\(minY),\(maxX),\(maxY)); touch=\(point)")
        return point
    }

    private func printPresentationDiagnostics(_ app: XCUIApplication, label: String) {
        let hierarchy = app.debugDescription.replacingOccurrences(of: "\n", with: " | ")
        for start in stride(from: 0, to: hierarchy.count, by: 1400) {
            print("GS_PRESENTATION_\(label)_\(start): " + String(hierarchy.dropFirst(start).prefix(1400)))
        }
        let screenshot = app.screenshot()
        printScreenshotPixels(screenshot, label: label)
        let capture = XCTAttachment(screenshot: screenshot)
        capture.name = "genui-actual-\(label.lowercased())-failure"; capture.lifetime = .keepAlways; add(capture)
    }

    func testGeneratedReactCodeRunsActualTaskControlsAndPersistsAcrossRelaunch() {
        let app = XCUIApplication()
        app.launchArguments = ["-gs_test_session_active", "-gs_test_react_preview"]
        app.launchEnvironment["GS_REACT_ACCEPTANCE_SCOPE"] = UUID().uuidString
        app.launch()
        func run() -> Bool {
            let button = app.buttons["Run in Browser"]
            let offered = button.waitForExistence(timeout: 20)
            XCTAssertTrue(offered)
            guard offered else { printPresentationDiagnostics(app, label: "REACT"); return false }
            button.tap()
            let mounted = app.webViews.staticTexts["React preview running"].waitForExistence(timeout: 20)
            XCTAssertTrue(mounted, "An actual React component must mount, not just a source card")
            if !mounted { printPresentationDiagnostics(app, label: "REACT") }
            return mounted
        }
        func complete(_ text: String) -> XCUIElement {
            app.webViews.descendants(matching: .any).matching(NSPredicate(format: "label == %@", text)).firstMatch
        }
        guard run() else { return }
        let input = app.webViews.textFields["New task"]
        XCTAssertTrue(input.waitForExistence(timeout: 10)); input.tap(); input.typeText("iOS React task")
        let addTask = app.webViews.buttons["Add task"]
        let reachable = NSPredicate { _, _ in addTask.exists && addTask.isHittable }
        let formVisible = XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: reachable, object: nil)], timeout: 5) == .completed
        if !formVisible { printPresentationDiagnostics(app, label: "REACT") }
        XCTAssertTrue(formVisible, "The actual inline form submit button must remain touchable above the real keyboard")
        guard formVisible else { return }
        addTask.tap()
        let added = complete("0/1 complete").waitForExistence(timeout: 10)
        if !added { printPresentationDiagnostics(app, label: "REACT") }
        XCTAssertTrue(added, "The real Add touch must submit the typed draft and change the actual React task count")
        guard added else { return }
        let toggle = complete("Complete iOS React task")
        XCTAssertTrue(toggle.waitForExistence(timeout: 5)); toggle.tap()
        XCTAssertTrue(complete("1/1 complete").waitForExistence(timeout: 5))
        let title = app.webViews.textFields["Task title"]
        title.tap(); title.typeText(" edited")
        let edited = title.value as? String ?? ""
        XCTAssertTrue(edited.contains("edited"), "Real keyboard input must update the React task, not just its DOM label")
        app.webViews.staticTexts["Response tasks"].tap()
        guard run() else { return }
        XCTAssertEqual(app.webViews.textFields["Task title"].value as? String, edited)
        XCTAssertTrue(complete("1/1 complete").exists)
        app.buttons["Use other response"].tap(); guard run() else { return }
        XCTAssertTrue(complete("0/0 complete").waitForExistence(timeout: 5))
        XCTAssertFalse(app.webViews.textFields["Task title"].exists)
        app.buttons["Use original response"].tap(); guard run() else { return }
        XCTAssertEqual(app.webViews.textFields["Task title"].value as? String, edited)
        app.terminate(); app.launch(); guard run() else { return }
        XCTAssertEqual(app.webViews.textFields["Task title"].value as? String, edited, "Real WebKit persistence must survive killing and relaunching the app")
        XCTAssertTrue(complete("1/1 complete").exists)
        app.webViews.buttons["Delete task"].tap()
        XCTAssertTrue(complete("0/0 complete").waitForExistence(timeout: 5))
        guard run() else { return }; XCTAssertFalse(app.webViews.textFields["Task title"].exists)
        let capture = XCTAttachment(screenshot: app.screenshot())
        capture.name = "genui-react-actual-task-crud-storage-isolation"; capture.lifetime = .keepAlways; add(capture)
    }

    func testConversationActionsTapOpensRealOptionsWithoutLeavingChat() {
        let app = XCUIApplication()
        app.launchArguments = ["-gs_test_session_active", "-gs_test_seed_chats"]
        app.launch()
        let chats = app.buttons["Chats"].firstMatch
        XCTAssertTrue(chats.waitForExistence(timeout: 20)); chats.tap()
        let row = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Weekend in Kyoto")).firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 20)); row.tap()
        let actions = app.buttons["Conversation actions"].firstMatch
        XCTAssertTrue(actions.waitForExistence(timeout: 15)); actions.tap()
        XCTAssertTrue(app.buttons["Rename"].waitForExistence(timeout: 5), "A normal tap must open actual actions, not navigate away or require a long press")
        XCTAssertTrue(app.buttons["Back"].exists, "Opening actions must keep the user in the conversation")
        let capture = XCTAttachment(screenshot: app.screenshot())
        capture.name = "genui-actual-conversation-actions-tap"; capture.lifetime = .keepAlways; add(capture)
    }

    func testVanishedDashboardTabCannotHideReplacementSource() {
        let app = XCUIApplication()
        app.launchArguments = ["-gs_test_session_active", "-gs_test_platform_runtime"]
        app.launch()
        XCTAssertTrue(app.buttons["Open generated dashboard"].waitForExistence(timeout: 20))
        app.buttons["Open generated dashboard"].tap()
        XCTAssertTrue(app.buttons["Table"].firstMatch.waitForExistence(timeout: 10))
        app.buttons["Table"].firstMatch.tap()
        XCTAssertFalse(app.staticTexts["Source footer"].exists)
        app.buttons["Replace dashboard source"].tap()
        XCTAssertTrue(app.staticTexts["Replacement stays visible"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Supplied replacement footer"].exists)
        let capture = XCTAttachment(screenshot: app.screenshot())
        capture.name = "genui-dashboard-actual-source-replacement"; capture.lifetime = .keepAlways; add(capture)
    }

    func testOrdinaryMarkdownTaskControlsPreserveRealDetailsAndCompletion() {
        let app = XCUIApplication()
        app.launchArguments = ["-gs_test_session_active", "-gs_test_platform_runtime"]
        app.launch()
        XCTAssertTrue(app.buttons["Open generated checklist"].waitForExistence(timeout: 20))
        app.buttons["Open generated checklist"].tap()
        app.buttons["Use ordinary Markdown tasks"].tap()
        XCTAssertTrue(app.staticTexts["1/2 completed"].waitForExistence(timeout: 10))
        func toggle(_ text: String) {
            let button = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", text)).firstMatch
            XCTAssertTrue(button.waitForExistence(timeout: 5)); button.tap()
        }
        func edit(_ text: String) {
            let input = app.textFields["prose.task.input"]
            XCTAssertTrue(input.waitForExistence(timeout: 5))
            let current = input.value as? String ?? ""
            input.tap(); input.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current.count) + text + "\n")
        }
        func addTask(_ text: String) {
            app.buttons["Add Task"].tap()
            XCTAssertFalse(app.buttons["Add"].isEnabled)
            edit(text)
        }
        toggle("Original task")
        XCTAssertTrue(app.staticTexts["2/2 completed"].exists)
        app.buttons["Edit task: Original task"].tap(); edit("Edited source")
        XCTAssertTrue(app.staticTexts["Supplied source detail"].exists)
        XCTAssertTrue(app.staticTexts["Supplied nested detail"].exists)
        XCTAssertFalse(app.buttons["Edit task: Supplied nested detail"].exists)
        XCTAssertTrue(app.staticTexts["2/2 completed"].exists)
        app.buttons["Delete task: Keep completed"].tap()
        XCTAssertTrue(app.staticTexts["1/1 completed"].exists)
        addTask("First added task"); toggle("First added task")
        addTask("Second added task")
        app.buttons["Delete task: First added task"].tap()
        XCTAssertTrue(app.staticTexts["1/2 completed"].exists, "Completion must remain with its actual task after another row is deleted")
        app.buttons["Edit task: Second added task"].tap(); edit("Second edited")
        XCTAssertTrue(app.staticTexts["1/2 completed"].exists)
        toggle("Second edited")
        XCTAssertTrue(app.staticTexts["2/2 completed"].exists)
        app.buttons["Replace ordinary tasks"].tap()
        XCTAssertTrue(app.staticTexts["0/1 completed"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["Edit task: Edited source"].exists)
        let capture = XCTAttachment(screenshot: app.screenshot())
        capture.name = "genui-ordinary-markdown-actual-task-controls"; capture.lifetime = .keepAlways; add(capture)
    }

    func testGeneratedTimerUsesActualSecondsPausesResetsAndNeverUsesPrices() {
        let app = XCUIApplication()
        app.launchArguments = ["-gs_test_session_active", "-gs_test_platform_runtime"]
        app.launch()
        XCTAssertTrue(app.buttons["Open generated timer"].waitForExistence(timeout: 20))
        app.buttons["Open generated timer"].tap()
        let remaining = app.staticTexts["timer.remaining"]
        let toggle = app.buttons["timer.toggle"]
        let duration = app.textFields["timer.duration"]
        XCTAssertTrue(remaining.waitForExistence(timeout: 10))
        XCTAssertEqual(remaining.label, "00:00"); XCTAssertFalse(toggle.isEnabled)
        duration.tap(); duration.typeText("2")
        XCTAssertTrue(toggle.isEnabled); toggle.tap()
        XCTAssertEqual(toggle.label, "Pause"); toggle.tap()
        let paused = remaining.label
        let changesWhilePaused = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label != %@", paused), object: remaining)
        changesWhilePaused.isInverted = true
        XCTAssertEqual(XCTWaiter.wait(for: [changesWhilePaused], timeout: 1.2), .completed)
        app.buttons["timer.reset"].tap()
        XCTAssertEqual(remaining.label, "00:02"); XCTAssertTrue(duration.isEnabled)
        toggle.tap()
        XCTAssertTrue(app.staticTexts["timer.completed"].waitForExistence(timeout: 5))
        XCTAssertEqual(remaining.label, "00:00"); XCTAssertFalse(toggle.isEnabled)
        app.buttons["Use next timer payload"].tap()
        XCTAssertEqual(remaining.label, "00:05"); XCTAssertTrue(toggle.isEnabled)
        XCTAssertFalse(app.staticTexts["timer.completed"].exists)
        XCTAssertTrue(app.staticTexts["Local countdown only. No background alarm or notification is scheduled."].exists)
        let capture = XCTAttachment(screenshot: app.screenshot())
        capture.name = "genui-timer-actual-seconds-pause-reset-source-replacement"; capture.lifetime = .keepAlways; add(capture)
    }

    func testGeneratedBookingSavesOnlyActualLocalSelectionWithoutReservation() {
        let app = XCUIApplication()
        app.launchArguments = ["-gs_test_session_active", "-gs_test_platform_runtime"]
        app.launch()
        XCTAssertTrue(app.buttons["Open generated booking"].waitForExistence(timeout: 20))
        app.buttons["Open generated booking"].tap()
        let save = app.buttons["Save slot locally"]
        XCTAssertTrue(save.waitForExistence(timeout: 10)); XCTAssertFalse(save.isEnabled)
        app.buttons["09:00"].tap()
        XCTAssertTrue(save.isEnabled); save.tap()
        let receipt = app.staticTexts["booking.local.receipt"]
        XCTAssertTrue(receipt.waitForExistence(timeout: 5))
        XCTAssertEqual(receipt.label, "Saved locally: 09:00. Preview only; no reservation has been made.")
        app.buttons["10:00"].tap()
        XCTAssertFalse(receipt.exists)
        save.tap()
        XCTAssertEqual(receipt.label, "Saved locally: 10:00. Preview only; no reservation has been made.")
        let capture = XCTAttachment(screenshot: app.screenshot())
        capture.name = "genui-booking-actual-local-selection"; capture.lifetime = .keepAlways; add(capture)
        app.buttons["Use next booking payload"].tap()
        XCTAssertTrue(app.buttons["11:00"].waitForExistence(timeout: 5))
        XCTAssertFalse(receipt.exists); XCTAssertFalse(save.isEnabled)
    }

    func testGeneratedChecklistEditsDeletesAddsAndKeepsActualTaskCompletion() {
        let app = XCUIApplication()
        app.launchArguments = ["-gs_test_session_active", "-gs_test_platform_runtime"]
        app.launch()
        XCTAssertTrue(app.buttons["Open generated checklist"].waitForExistence(timeout: 20))
        app.buttons["Open generated checklist"].tap()
        XCTAssertTrue(app.staticTexts["0 of 2 completed"].waitForExistence(timeout: 10))
        app.buttons["Keep source"].tap()
        XCTAssertEqual(app.buttons["Keep source"].value as? String, "Completed")
        func replaceTask(_ text: String) {
            let input = app.textFields["checklist.task.input"]
            XCTAssertTrue(input.waitForExistence(timeout: 5))
            let current = input.value as? String ?? ""
            input.tap()
            input.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current.count) + text)
        }
        app.buttons["Edit task: Original source"].tap()
        replaceTask("Renamed source")
        app.buttons["Save task"].tap()
        XCTAssertTrue(app.buttons["Renamed source, Only supplied detail"].exists)
        XCTAssertEqual(app.buttons["Keep source"].value as? String, "Completed")
        app.buttons["Delete task: Renamed source"].tap()
        XCTAssertTrue(app.staticTexts["1 of 1 completed"].exists)
        app.buttons["Add Task"].tap()
        XCTAssertFalse(app.buttons["Add"].isEnabled)
        let input = app.textFields["checklist.task.input"]
        input.tap(); input.typeText("Added task")
        app.buttons["Add"].tap()
        app.buttons["Added task"].tap()
        XCTAssertTrue(app.staticTexts["2 of 2 completed"].exists)
        app.buttons["Edit task: Added task"].tap()
        replaceTask("Added edited")
        app.buttons["Save task"].tap()
        XCTAssertEqual(app.buttons["Added edited"].value as? String, "Completed")
        app.buttons["Delete task: Keep source"].tap()
        XCTAssertTrue(app.staticTexts["1 of 1 completed"].exists)
        XCTAssertEqual(app.buttons["Added edited"].value as? String, "Completed")
        app.buttons["Use next checklist payload"].tap()
        let sourceReset = app.staticTexts["0 of 1 completed"].waitForExistence(timeout: 5)
        if !sourceReset { printPresentationDiagnostics(app, label: "CHECKLIST_RESET") }
        XCTAssertTrue(sourceReset, "The source replacement must reset actual task count and completion")
        XCTAssertFalse(app.buttons["Added edited"].exists)
        XCTAssertEqual(app.buttons["New supplied task"].value as? String, "Not completed")
        app.buttons["Use app task surface"].tap()
        XCTAssertTrue(app.staticTexts["1 of 2 completed"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.buttons["App first, Supplied app detail"].value as? String, "Completed")
        app.buttons["Edit task: App first"].tap()
        replaceTask("App first edited")
        app.buttons["Save task"].tap()
        XCTAssertEqual(app.buttons["App first edited, Supplied app detail"].value as? String, "Completed")
        app.buttons["Delete task: App second"].tap()
        XCTAssertTrue(app.staticTexts["1 of 1 completed"].exists)
        let capture = XCTAttachment(screenshot: app.screenshot())
        capture.name = "genui-checklist-actual-task-mutations"; capture.lifetime = .keepAlways; add(capture)
    }

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
        // Modern Files remote UI may expose its action as Other rather than
        // Button. Verify the real provider and actual cancellation callback,
        // not one OS version's accessibility class spelling.
        let pickerCancel = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Cancel")).firstMatch
        let shown = pickerCancel.waitForExistence(timeout: 15)
        if !shown { printPresentationDiagnostics(app, label: "EXPORT") }
        XCTAssertTrue(shown, "Export must present the real system document picker; notice=\(app.staticTexts["response.action.notice"].firstMatch.label); hierarchy=\(app.debugDescription.suffix(5000))")
        guard shown else { return }
        let provider = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@ OR label CONTAINS %@ OR label == %@", "On My iPhone", "iCloud Drive", "Browse")).firstMatch
        XCTAssertTrue(provider.waitForExistence(timeout: 10), "Export must expose the actual system Files provider, not an app-owned Cancel label")
        // Files is remote UI. On current iOS, resolving isHittable inside a
        // waiter can itself fail with "remote AX element ... interrupted by
        // waiter". Resolve once after composition, outside any polling waiter;
        // only the real Files delegate receipt can pass this test.
        // Screenshot resolves the visible, composited system presentation.
        // Query the touch frame AFTER it, not before: the remote Files AX
        // element can exist while the full-screen presentation is animating.
        let screenshot = app.screenshot()
        printScreenshotPixels(screenshot, label: "FILES")
        let capture = XCTAttachment(screenshot: screenshot)
        capture.name = "genui-contextual-real-source-export-picker"; capture.lifetime = .keepAlways; add(capture)
        let cancelFrame = pickerCancel.frame
        let applicationFrame = app.frame
        print("GS_FILES_CANCEL_FRAME: cancel=\(cancelFrame); application=\(applicationFrame)")
        guard !cancelFrame.isEmpty, !cancelFrame.isNull,
              applicationFrame.contains(CGPoint(x: cancelFrame.midX, y: cancelFrame.midY)) else {
            printPresentationDiagnostics(app, label: "EXPORT")
            XCTFail("The actual native Files Cancel control must occupy the visible viewport")
            return
        }
        let cancelled = app.staticTexts["response.action.notice"].firstMatch
        // The genuine screenshot shows a leading Back/Close control. Leave a
        // provider directory first if necessary, then activate native Close.
        // Each touch is measured from fresh real pixels. Only the delegate's
        // receipt below can acknowledge cancellation; no app state is forced.
        for _ in 0..<2 {
            guard let point = visibleFilesNavigationPoint(app.screenshot(), application: applicationFrame, toolbar: cancelFrame) else {
                printPresentationDiagnostics(app, label: "EXPORT")
                XCTFail("The visible native Files navigation glyph must be identifiable in actual pixels")
                return
            }
            app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: point.x - applicationFrame.minX, dy: point.y - applicationFrame.minY)).tap()
            let receipt = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
                cancelled.exists && cancelled.label == "Export cancelled."
            }, object: app)
            if XCTWaiter.wait(for: [receipt], timeout: 2) == .completed { break }
        }
        let acknowledged = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            cancelled.exists && cancelled.label == "Export cancelled."
        }, object: app)
        let didAcknowledge = XCTWaiter.wait(for: [acknowledged], timeout: 10) == .completed
        if !didAcknowledge { printPresentationDiagnostics(app, label: "EXPORT") }
        XCTAssertTrue(didAcknowledge, "The actual document-picker delegate must acknowledge cancellation; no simulated save is allowed")
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
        app.buttons["Prepare report share check"].tap()
        app.buttons["Share"].tap()
        let copyAction = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Copy")).firstMatch
        let shareShown = copyAction.waitForExistence(timeout: 15)
        if !shareShown { printPresentationDiagnostics(app, label: "SHARE") }
        XCTAssertTrue(shareShown, "Share must present the real activity sheet, not copy silently")
        let capture = XCTAttachment(screenshot: app.screenshot())
        capture.name = "genui-contextual-real-system-share-sheet"; capture.lifetime = .keepAlways; add(capture)
        guard shareShown else { return }
        XCTAssertTrue(copyAction.isHittable)
        copyAction.tap()
        XCTAssertTrue(app.buttons["Inspect shared report"].waitForExistence(timeout: 10))
        app.buttons["Inspect shared report"].tap()
        XCTAssertEqual(app.staticTexts["report.copy.inspection"].label, "Original complete report shared", "Real system Copy must replace the sentinel with all 31 actual source records")
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

    func testGeneratedCalculatorKeepsNinthTenthInputsSixthChoiceAndSeventhOutputUsable() {
        let app = XCUIApplication()
        app.launchArguments = ["-gs_test_session_active", "-gs_test_platform_runtime"]
        app.launch()
        let open = app.buttons["Open generated calculator"]
        XCTAssertTrue(open.waitForExistence(timeout: 20))
        open.tap()
        let expand = app.buttons["Use calculator with all source controls"]
        XCTAssertTrue(expand.waitForExistence(timeout: 10))
        expand.tap()
        let scroll = app.scrollViews.firstMatch
        func reveal(_ element: XCUIElement) {
            for _ in 0..<8 {
                if element.isHittable { break }
                scroll.swipeUp()
            }
            XCTAssertTrue(element.isHittable, "Actual supplied control is missing or inaccessible: \(element)")
        }
        let ninth = app.textFields["calc-input-x9"]
        reveal(ninth)
        XCTAssertEqual(ninth.value as? String, "9.0", "Source initial alias was lost")
        ninth.coordinate(withNormalizedOffset: CGVector(dx: 0.98, dy: 0.5)).tap()
        ninth.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 3) + "20\n")
        let keyboardDismissed = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: app.keyboards.firstMatch)
        XCTAssertEqual(XCTWaiter.wait(for: [keyboardDismissed], timeout: 5), .completed,
                       "Done must actually dismiss calculator editing")

        let tenth = app.textFields["calc-input-x10"]
        reveal(tenth)
        XCTAssertEqual(tenth.value as? String, "10.0")
        tenth.coordinate(withNormalizedOffset: CGVector(dx: 0.98, dy: 0.5)).tap()
        tenth.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 4) + "30\n")
        let sixthChoice = app.buttons["Source option 6"]
        reveal(sixthChoice)
        sixthChoice.tap()
        reveal(app.staticTexts["Last supplied result"])
        XCTAssertTrue(app.staticTexts["92.00"].waitForExistence(timeout: 10), "All real inputs and the sixth choice must affect the seventh output")
        XCTAssertEqual(app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "$" )).count, 0,
                       "An unspecified currency must not become dollars")
        let evidence = XCTAttachment(screenshot: app.screenshot())
        evidence.name = "genui-calculator-all-source-controls-no-invented-currency"
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
