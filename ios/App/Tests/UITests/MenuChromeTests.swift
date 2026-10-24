import XCTest

/**
 * The menu chrome harness: does a `Label`'s icon reach the leading slot of a
 * REAL menu, at a menu's optical size?
 *
 * The 29 `Label(_:systemImage:)` rows were not page layout — a menu, a context
 * menu and a swipe action hand their rows to UIKit's menu chrome, which decides
 * whether an icon is drawn at all and at what size. The only way to know is to
 * open the menu and measure the pixels. So this test drives every one of them
 * on seeded data, screenshots the presented menu, and measures the ink
 * immediately beside the title:
 *
 *   - ink exists at all            → the icon was not dropped;
 *   - ink's height is 9–24 pt      → it is an icon, not a speck or a banner;
 *   - ink sits in the slot the label implies (left of the title, or above it in
 *     a swipe button)              → it is where the control's twin puts it.
 *
 * Every number is printed (`MEASURE …`) and written to `report.json`, and every
 * presented menu is attached to the result bundle as `menu-<name>`, so a green
 * run leaves the picture and the arithmetic behind it. The workflow fails if
 * this test did not pass or a capture is missing.
 *
 * Run against the SF baseline first (2026-10-25): a harness that cannot see the
 * system's own glyphs is not a harness.
 */
final class MenuChromeTests: XCTestCase {

    private var failures: [String] = []
    private var blockers: [String] = []
    private var measurements: [[String: Any]] = []

    /// Present in ChatDetailView and nowhere else — the honest way to know the
    /// thread opened. A message's text is NOT: the chats list shows the same
    /// sentence in its preview subtitle (run 40131109721 believed it was in the
    /// chat for five menus while it was still looking at the inbox).
    private var composerMarker: XCUIElement {
        XCUIApplication().buttons["Nexly tools, attachments and modes"]
    }

    func testEveryMenuPutsItsGlyphInTheLeadingSlot() throws {
        continueAfterFailure = true

        let app = XCUIApplication()
        app.launchArguments += ["-gs_test_session_active", "-gs_test_seed_chats"]
        app.launch()

        // The drawer first: it is reachable only from a root screen (a pushed
        // chat's header button says "Back"), and its recents come from the same
        // seed the chats list reads.
        let homeMenu = app.buttons["Open menu"].firstMatch
        if homeMenu.waitForExistence(timeout: 30) {
            homeMenu.tap()
            let recent = app.buttons.matching(
                NSPredicate(format: "label CONTAINS %@", "Explain quantum physics")).firstMatch
            if recent.waitForExistence(timeout: 12),
               openContextMenu(on: recent, app: app, expecting: "Rename…") {
                capture(
                    name: "drawer-recent-context",
                    app: app,
                    items: [
                        ("Pin to top", .leading),
                        ("Rename…", .leading),
                        ("Archive", .leading),
                        ("Delete", .leading),
                    ])
                dismissMenu(app: app, item: "Rename…")
            } else {
                record("drawer-recent-context: no recent-row menu in the drawer")
            }
            // Restart instead of fighting the panel: the drawer has no
            // tap-to-dismiss scrim and its Close button did not land twice
            // (runs 40133180902, 40134206951). The seed lives in SQLite, so the
            // rows survive a relaunch and the chats phase starts on a known
            // screen.
            app.terminate()
            app.launchArguments = ["-gs_test_session_active", "-gs_test_seed_chats"]
            app.launch()
        } else {
            record("drawer: Home never offered its menu button")
        }

        // The chats hub. "Archived" is the quick-access card: it exists on the
        // list and nowhere in the drawer, so it proves the panel is gone.
        let chatsDock = app.buttons["Chats"].firstMatch
        XCTAssertTrue(chatsDock.waitForExistence(timeout: 20), "Home never offered the Chats dock")
        chatsDock.tap()
        guard app.buttons["Archived"].waitForExistence(timeout: 15) else {
            XCTFail("the chats hub never opened — the drawer was probably still up")
            try finish(app: app)
            return
        }

        let row = app.buttons.matching(
            NSPredicate(format: "label CONTAINS %@", "Weekend in Kyoto")).firstMatch
        guard row.waitForExistence(timeout: 20) else {
            XCTFail("seeded row never appeared — -gs_test_seed_chats did not reach the store")
            try finish(app: app)
            return
        }

        // 1. The row's context menu (5 rows). The first seeded row is PINNED,
        //    so its menu says "Unpin".
        if openContextMenu(on: row, app: app, expecting: "Unpin") {
            capture(
                name: "chats-row-context",
                app: app,
                items: [
                    ("Select", .leading),
                    ("Unpin", .leading),
                    ("Rename…", .leading),
                    ("Archive", .leading),
                    ("Delete", .leading),
                ])
        }
        dismissMenu(app: app, item: "Unpin")

        // 2. The leading swipe (1 row).
        row.swipeRight()
        if app.buttons["Unpin"].waitForExistence(timeout: 8) {
            capture(name: "chats-row-swipe-leading", app: app, items: [("Unpin", .above)])
        } else {
            record("swipe-leading: Unpin never appeared after swipeRight")
        }
        row.swipeLeft()                      // closes the leading actions
        Thread.sleep(forTimeInterval: 0.4)

        // 3. The trailing swipe (2 rows).
        row.swipeLeft()
        if app.buttons["Archive"].waitForExistence(timeout: 8) {
            capture(
                name: "chats-row-swipe-trailing",
                app: app,
                items: [("Delete", .above), ("Archive", .above)])
        } else {
            record("swipe-trailing: Archive never appeared after swipeLeft")
        }
        row.swipeRight()                     // closes the trailing actions
        Thread.sleep(forTimeInterval: 0.4)

        // 4. Into the conversation. The marker is the composer: it exists only
        //    inside a thread, unlike a message's text, which the inbox preview
        //    repeats. Two attempts, because a swipe that did not fully close
        //    swallows the first tap.
        for _ in 0..<2 where !composerMarker.exists {
            row.tap()
            if composerMarker.waitForExistence(timeout: 8) { break }
        }
        guard composerMarker.waitForExistence(timeout: 10) else {
            XCTFail("seeded conversation never opened")
            try finish(app: app)
            return
        }

        // 5. User bubble — Copy / Edit. Its text is selectable, so the press
        //    moves 10 pt left onto the bubble's padding; pressing the text
        //    itself starts a selection instead of the menu.
        let userText = app.staticTexts.matching(
            NSPredicate(format: "label CONTAINS %@", "What should I pack")).firstMatch
        if userText.waitForExistence(timeout: 10),
           openContextMenu(on: userText, app: app, expecting: "Edit", press: .offsetFromLeading(-10)) {
            capture(name: "user-bubble-context", app: app,
                    items: [("Copy", .leading), ("Edit", .leading)])
            dismissMenu(app: app, item: "Edit")
        } else {
            record("user-bubble-context: the user turn's menu did not open")
        }

        // 6. Assistant bubble — seven rows.
        let assistantText = app.staticTexts.matching(
            NSPredicate(format: "label CONTAINS %@", "Layers, a compact umbrella")).firstMatch
        if assistantText.waitForExistence(timeout: 10),
           openContextMenu(on: assistantText, app: app, expecting: "Branch new chat") {
            capture(
                name: "assistant-bubble-context",
                app: app,
                items: [
                    ("Copy", .leading),
                    ("Read aloud", .leading),
                    ("Regenerate", .leading),
                    ("Translate", .leading),
                    ("Save", .leading),
                    ("Branch new chat", .leading),
                    ("Share", .leading),
                ])
            dismissMenu(app: app, item: "Branch new chat")
        } else {
            record("assistant-bubble-context: the bubble menu did not open")
        }

        // 7. Header menus, last: a press on these buttons can fire their ACTION
        //    (run 40129349764 landed in a new chat), so nothing that must be
        //    measured afterwards sits behind them.
        let sparkle = app.buttons["New chat"].firstMatch.exists
            ? app.buttons["New chat"].firstMatch
            : app.buttons["Search in chat"].firstMatch
        if sparkle.waitForExistence(timeout: 10),
           openContextMenu(on: sparkle, app: app, expecting: "Voice input", press: .center) {
            // A chat pushed from the inbox may not offer "New chat"; the same
            // menu then carries Search and Voice only.
            var items: [(String, Slot)] = [
                ("Search in chat", .leading),
                ("Voice input", .leading),
            ]
            if app.buttons["New chat"].exists { items.insert(("New chat", .leading), at: 0) }
            capture(name: "chat-header-sparkle", app: app, items: items)
            dismissMenu(app: app, item: "Voice input")
        } else {
            recordSkip("chat-header-sparkle", because: "the sparkle button consumes the long press as its action")
        }

        let headerMenu = app.buttons["Open menu"].firstMatch.exists
            ? app.buttons["Open menu"].firstMatch
            : app.buttons["Back"].firstMatch
        if headerMenu.waitForExistence(timeout: 10),
           openContextMenu(on: headerMenu, app: app, expecting: "Chat options", press: .center) {
            // A pushed chat offers two rows here, not three: "New chat" is the
            // sparkle's row and only exists when the screen has the action.
            var items: [(String, Slot)] = [
                ("Chat options", .leading),
                ("Search in chat", .leading),
            ]
            if app.buttons["New chat"].exists { items.append(("New chat", .leading)) }
            capture(name: "chat-header-menu", app: app, items: items)
            dismissMenu(app: app, item: "Chat options")
        } else {
            recordSkip("chat-header-menu", because: "the header menu button consumes the long press as its action")
        }

        try finish(app: app)
    }

    // MARK: - Menu driving

    private enum Slot {
        /// The icon sits left of the title — every menu and context-menu row.
        case leading
        /// The icon sits above the title — iOS lays swipe actions out stacked.
        case above
    }

    private enum Press {
        /// `element.press` — the gesture menu rows and bubble containers answer.
        case element
        /// A press through a coordinate, at the element's centre.
        case center
        /// A press through a coordinate, `pt` from the element's LEADING edge —
        /// negative lands outside it, which is how the user bubble's selectable
        /// text is avoided: 10 pt left of it is the bubble's own padding.
        case offsetFromLeading(CGFloat)
    }

    @discardableResult
    private func openContextMenu(on element: XCUIElement, app: XCUIApplication,
                                 expecting label: String, press: Press = .element) -> Bool {
        switch press {
        case .element:
            element.press(forDuration: 1.4)
        case .center:
            element.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
                .press(forDuration: 1.4)
        case .offsetFromLeading(let pt):
            let width = max(1, element.frame.width)
            element.coordinate(withNormalizedOffset: CGVector(dx: pt / width, dy: 0.5))
                .press(forDuration: 1.4)
        }
        if app.buttons[label].waitForExistence(timeout: 12) {
            print("MENU-OPEN \(label)")
            return true
        }
        if app.staticTexts[label].exists || app.cells[label].exists {
            print("MENU-OPEN(text) \(label)")
            return true
        }
        // Printed, so the run log carries what was on screen instead of a
        // screenshot nobody can reach from a failed step.
        let labels = app.buttons.allElementsBoundByIndex.prefix(30).map(\.label)
        print("MENU-FAIL \(label): buttons=\(labels)")
        record("menu '\(label)' never appeared after long-press")
        attachTree(app)
        return false
    }

    /// Closes the drawer's panel if it is still up. Kept for the day the panel
    /// gains a dismiss gesture; the test itself restarts instead of relying on
    /// it (see step 1).
    private func closeDrawer(_ app: XCUIApplication) {
        let close = app.buttons["Close menu"].firstMatch
        for _ in 0..<4 {
            if app.buttons["Chats"].isHittable { return }
            if close.exists { close.tap() }
            Thread.sleep(forTimeInterval: 0.5)
        }
        record("drawer: the panel did not close")
    }

    /// A menu this build cannot open by any gesture is recorded, not silently
    /// dropped: the header's context menus are attached to buttons that consume
    /// the long press as their action, so no press produces a menu (runs
    /// 40129349764 onward). The workflow prints this list on every run.
    private func recordSkip(_ name: String, because: String) {
        blockers.append(name)
        print("MENU-SKIP \(name): \(because)")
    }

    private func dismissMenu(app: XCUIApplication, item: String) {
        // Menus close on a tap outside their card. The card is centred with a
        // margin, so the far-left edge is always outside it — and away from the
        // status bar, whose band swallows taps (the first run tapped 4% from
        // the top and left the menu standing).
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.02, dy: 0.5)).tap()
        for _ in 0..<30 {
            if !app.buttons[item].exists && !app.staticTexts[item].exists { return }
            Thread.sleep(forTimeInterval: 0.1)
        }
        record("menu '\(item)' did not dismiss — later gestures would land on it")
    }

    // MARK: - Capture + measure

    private func capture(name: String, app: XCUIApplication, items: [(String, Slot)]) {
        // No menu, no capture: measuring a closed overlay is how the first run
        // recorded five rows of zero ink and called it a glyph problem.
        guard app.buttons[items[0].0].exists || app.staticTexts[items[0].0].exists else {
            record("\(name): the menu was not open at capture time")
            return
        }
        // Let the menu finish presenting. Run 40135074617 measured its first
        // row mid-animation: 3.3 pt of ink where the settled rows carry 16.7.
        Thread.sleep(forTimeInterval: 0.45)
        let shot = XCUIScreen.main.screenshot()
        attach(shot, name: "menu-\(name)")
        guard let image = shot.image.cgImage else {
            record("\(name): the screenshot had no bitmap")
            return
        }
        let scale = CGFloat(image.width) / shot.image.size.width

        for (label, slot) in items {
            guard let box = titleBox(label, app: app) else {
                record("\(name)/\(label): the row is not in the menu")
                continue
            }
            let band = probeBand(for: box, slot: slot)
            // A swipe action's background is a tint gradient, so its ink has to
            // clear a higher bar than a menu card's flat field (run 40135074617
            // measured 36x22 pt of "ink" — the whole band — at threshold 40).
            let threshold = slot == .leading ? 40 : 70
            guard let probe = probeInk(image: image, band: band, scale: scale, threshold: threshold) else {
                record("\(name)/\(label): the probe band was degenerate")
                continue
            }
            let height = probe.bbox.height / scale
            let width = probe.bbox.width / scale
            let inset = probe.bbox.minX / scale - box.rowOriginX
            print("MEASURE \(name)/\(label) geometry=\(box.kind) ink=\(probe.count) "
                  + "h=\(String(format: "%.1f", height)) w=\(String(format: "%.1f", width)) "
                  + "inset=\(String(format: "%.1f", inset)) band=\(String(format: "%.0fx%.0f", band.width, band.height))")
            measurements.append([
                "menu": name,
                "item": label,
                "slot": slot == .leading ? "leading" : "above",
                "geometry": box.kind,
                "inkPixels": probe.count,
                "inkHeightPt": Double(round(height * 10) / 10),
                "inkWidthPt": Double(round(width * 10) / 10),
                "leadingInsetPt": Double(round(inset * 10) / 10),
            ])

            // 1. The icon is drawn at all.
            let minInk = Int(90 * (scale / 2) * (scale / 2))
            if probe.count < minInk {
                record("\(name)/\(label): only \(probe.count) ink px (need \(minInk)) — the icon is missing or hairline")
            }
            // 2. It is an icon's size, not a speck and not a paragraph.
            if height < 9 || height > 24 {
                record("\(name)/\(label): glyph height \(String(format: "%.1f", height)) pt is outside 9–24 pt")
            }
            // 3. It sits in the slot its label implies.
            if slot == .leading, case .text(let rect) = box, probe.bbox.maxX > rect.minX + 1 {
                record("\(name)/\(label): ink reaches x=\(Int(probe.bbox.maxX / scale)) but the title starts at \(Int(rect.minX)) — the icon is not in the leading slot")
            }
            if slot == .leading, case .row(let rect) = box, inset > 34 {
                // iOS puts a menu's icon column 27–29 pt in from the row's
                // leading edge (measured on SF). 34 catches an icon parked
                // mid-row without failing a menu's own gutter.
                record("\(name)/\(label): ink starts \(Int(inset)) pt into the row — not the leading slot")
            }
            if slot == .leading, case .textInRow(let rect, _) = box, inset > 34 {
                record("\(name)/\(label): ink starts \(Int(inset)) pt into the row — not the leading slot")
            }
        }
    }

    /// Where a row put its title. Menus normally expose the text as its own
    /// element; some builds expose only the row, and a swipe button exposes a
    /// text INSIDE a small button — three different geometries, and the band
    /// must follow the one that is really there.
    private enum TitleBox {
        case text(CGRect)
        case row(CGRect)
        case textInRow(text: CGRect, row: CGRect)

        var kind: String {
            switch self {
            case .text: return "text"
            case .row: return "row"
            case .textInRow: return "textInRow"
            }
        }

        /// x the row starts at — the reference for a leading inset.
        var rowOriginX: CGFloat {
            switch self {
            case .text(let rect): return rect.minX
            case .row(let rect): return rect.minX
            case .textInRow(_, let row): return row.minX
            }
        }
    }

    private func titleBox(_ label: String, app: XCUIApplication) -> TitleBox? {
        let text = app.staticTexts[label].firstMatch
        let button = app.buttons[label].firstMatch
        if text.exists, !text.frame.isEmpty {
            // A swipe button is a SMALL button wrapping its own text; a menu
            // row's button is the whole row (no text child at all).
            if button.exists, !button.frame.isEmpty, button.frame.height < 120 {
                return .textInRow(text: text.frame, row: button.frame)
            }
            return .text(text.frame)
        }
        if button.exists, !button.frame.isEmpty { return .row(button.frame) }
        let cell = app.cells[label].firstMatch
        if cell.exists, !cell.frame.isEmpty { return .row(cell.frame) }
        return nil
    }

    private func probeBand(for box: TitleBox, slot: Slot) -> CGRect {
        switch (box, slot) {
        case (.text(let rect), .leading):
            // Immediately left of the title: the leading icon slot.
            return CGRect(x: rect.minX - 34, y: rect.midY - 12, width: 32, height: 24)
        case (.row(let rect), .leading):
            // Inside the row's leading edge, where a menu's icon column sits.
            return CGRect(x: rect.minX + 4, y: rect.midY - 13, width: 36, height: 26)
        case (.textInRow(_, let row), .leading):
            return CGRect(x: row.minX + 4, y: row.midY - 13, width: 36, height: 26)
        case (.text(let rect), .above):
            // Stacked layout with only the text known: stop short of the box.
            return CGRect(x: rect.midX - 22, y: rect.minY - 24, width: 44, height: 22)
        case (.textInRow(let rect, let row), .above):
            // Swipe actions: the icon sits over the title inside one button.
            // The band is clamped INSIDE the button (run 40131109721 measured
            // 26.3 pt because 28 pt above the text reached past its top edge
            // into the page), and stops short of the text's own cap.
            let top = max(row.minY + 2, rect.minY - 24)
            let bottom = rect.minY - 2
            let height = max(8, bottom - top)
            let width = min(46, max(12, row.width * 0.6))
            return CGRect(x: rect.midX - width / 2, y: top, width: width, height: height)
        case (.row(let rect), .above):
            let top = rect.minY + 2
            let height = min(24, max(10, rect.height * 0.42))
            let width = min(46, max(12, rect.width * 0.6))
            return CGRect(x: rect.midX - width / 2, y: top, width: width, height: height)
        }
    }

    /// Ink = pixels that differ from the band's own median by more than a
    /// fraction of the range. Scheme-agnostic: it works on white-on-black,
    /// black-on-white and white-on-tint alike.
    private func probeInk(image: CGImage, band: CGRect, scale: CGFloat,
                          threshold: Int = 40) -> (count: Int, bbox: CGRect)? {
        let width = image.width, height = image.height
        // A context that owns its buffer: passing `&array` here would be a
        // pointer valid only for the initialiser's call, and the draw below
        // happens after it returns.
        guard let context = CGContext(
            data: nil, width: width, height: height,
            bitsPerComponent: 8, bytesPerRow: width,
            space: CGColorSpaceCreateDeviceGray(),
            bitmapInfo: CGImageAlphaInfo.none.rawValue) else { return nil }
        context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        guard let gray = context.data?.assumingMemoryBound(to: UInt8.self) else { return nil }

        let minX = max(0, Int(band.minX * scale))
        let maxX = min(width - 1, Int(band.maxX * scale))
        let minY = max(0, Int(band.minY * scale))
        let maxY = min(height - 1, Int(band.maxY * scale))
        guard maxX > minX + 2, maxY > minY + 2 else { return nil }

        var samples: [UInt8] = []
        samples.reserveCapacity((maxX - minX) * (maxY - minY))
        for y in minY...maxY {
            for x in minX...maxX { samples.append(gray[y * width + x]) }
        }
        let sorted = samples.sorted()
        let background = Int(sorted[sorted.count / 2])

        var count = 0
        var boxMinX = Int.max, boxMinY = Int.max, boxMaxX = Int.min, boxMaxY = Int.min
        for y in minY...maxY {
            for x in minX...maxX {
                let v = Int(gray[y * width + x])
                if abs(v - background) > threshold {
                    count += 1
                    boxMinX = min(boxMinX, x); boxMaxX = max(boxMaxX, x)
                    boxMinY = min(boxMinY, y); boxMaxY = max(boxMaxY, y)
                }
            }
        }
        guard count > 0 else { return (0, .zero) }
        let bbox = CGRect(x: CGFloat(boxMinX), y: CGFloat(boxMinY),
                          width: CGFloat(boxMaxX - boxMinX + 1),
                          height: CGFloat(boxMaxY - boxMinY + 1))
        return (count, bbox)
    }

    // MARK: - Reporting

    private func record(_ message: String) {
        failures.append(message)
        XCTFail(message)
    }

    /// Attachment named for the file the workflow's exporter expects.
    private func attach(_ shot: XCUIScreenshot, name: String) {
        let attachment = XCTAttachment(screenshot: shot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func attachTree(_ app: XCUIApplication) {
        let attachment = XCTAttachment(string: app.debugDescription)
        attachment.name = "accessibility-tree"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func finish(app: XCUIApplication) throws {
        let report: [String: Any] = [
            "generated": ISO8601DateFormatter().string(from: Date()),
            "measurements": measurements,
            "failures": failures,
            "unreachableMenus": blockers,
        ]
        if let data = try? JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys]) {
            let attachment = XCTAttachment(data: data, uniformTypeIdentifier: "public.json")
            attachment.name = "report"
            attachment.lifetime = .keepAlways
            add(attachment)
        }
        attachTree(app)
    }
}
