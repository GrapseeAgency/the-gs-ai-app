import XCTest

/**
 * The menu chrome harness: does a `Label`'s icon reach the leading slot of a
 * REAL menu, at a menu's optical size?
 *
 * The 29 `Label(_:systemImage:)` sites left in the app are not page layout —
 * a menu, a context menu and a swipe action hand their rows to UIKit's menu
 * chrome, which decides whether an icon is drawn at all and at what size. The
 * only way to know is to open the menu and look at the pixels. So this test
 * opens every one of them on seeded data, screenshots the presented menu, and
 * measures the ink immediately beside the title:
 *
 *   - ink exists at all            → the icon was not dropped;
 *   - ink's height is 11–22 pt     → it is an icon, not a speck or a banner;
 *   - ink sits LEFT of the title   → it is the leading slot, not decoration.
 *
 * The thresholds are deliberately loose around SF's 17 pt because the point
 * is to catch a category error, not to pin a font metric. The measurement is
 * logged per item and written to `/tmp/gs-menu-chrome/report.json`, and every
 * presented menu is saved as a PNG, so a green run still leaves the picture
 * behind it.
 *
 * It runs on the SF baseline first (2026-10-25): a harness that cannot see the
 * system's own glyphs is not a harness. The family's rows are then held to the
 * same numbers.
 */
final class MenuChromeTests: XCTestCase {

    /// Screenshots and measurements land here; the simulator shares the host's
    /// /tmp, which is the channel the render harnesses already use.
    private static let outputDir = URL(fileURLWithPath: "/tmp/gs-menu-chrome", isDirectory: true)

    private var failures: [String] = []
    private var measurements: [[String: Any]] = []

    // MARK: - Entry

    func testEveryMenuPutsItsGlyphInTheLeadingSlot() throws {
        continueAfterFailure = true
        try FileManager.default.createDirectory(
            at: Self.outputDir, withIntermediateDirectories: true)

        let app = XCUIApplication()
        app.launchArguments += ["-gs_test_session_active", "-gs_test_seed_chats"]
        app.launch()

        // The chats hub: the dock's own label, then the seeded rows.
        let chatsDock = app.buttons["Chats"].firstMatch
        XCTAssertTrue(chatsDock.waitForExistence(timeout: 30), "Home never offered the Chats dock")
        chatsDock.tap()

        let row = app.buttons.matching(
            NSPredicate(format: "label CONTAINS %@", "Weekend in Kyoto")).firstMatch
        guard row.waitForExistence(timeout: 20) else {
            // A missing row means the seed never landed, and every later
            // step would fail for that one reason. Say so, once.
            XCTFail("seeded row never appeared — -gs_test_seed_chats did not reach the store")
            try finish(app: app)
            return
        }

        // 1. The row's context menu (5 rows).
        openContextMenu(on: row, app: app, expecting: "Pin to top")
        capture(
            name: "chats-row-context",
            app: app,
            items: [
                ("Select", .leading),
                ("Unpin", .leading),       // seed row 1 is pinned
                ("Rename…", .leading),
                ("Archive", .leading),
                ("Delete", .leading),
            ])
        dismissMenu(app: app)

        // 2. The leading swipe (1 row).
        row.swipeRight()
        if app.buttons["Unpin"].waitForExistence(timeout: 8) {
            capture(name: "chats-row-swipe-leading", app: app, items: [("Unpin", .above)])
            closeSwipe(row: row, app: app)
        } else {
            record("swipe-leading: Unpin never appeared after swipeRight")
            closeSwipe(row: row, app: app)
        }

        // 3. The trailing swipe (2 rows).
        row.swipeLeft()
        if app.buttons["Archive"].waitForExistence(timeout: 8) {
            capture(
                name: "chats-row-swipe-trailing",
                app: app,
                items: [("Delete", .above), ("Archive", .above)])
            closeSwipe(row: row, app: app)
        } else {
            record("swipe-trailing: Archive never appeared after swipeLeft")
            closeSwipe(row: row, app: app)
        }

        // 4. Into the conversation: both bubble menus and the header menus.
        row.tap()
        let assistantText = app.staticTexts.matching(
            NSPredicate(format: "label CONTAINS %@", "Layers, a compact umbrella")).firstMatch
        guard assistantText.waitForExistence(timeout: 20) else {
            XCTFail("seeded conversation never opened")
            try finish(app: app)
            return
        }

        // 4a. Header sparkle — long-press opens its menu.
        let sparkle = app.buttons["New chat"].firstMatch.exists
            ? app.buttons["New chat"].firstMatch
            : app.buttons["Search in chat"].firstMatch
        if sparkle.waitForExistence(timeout: 10) {
            openContextMenu(on: sparkle, app: app, expecting: "Voice input")
            if app.buttons["Voice input"].exists {
                capture(
                    name: "chat-header-sparkle",
                    app: app,
                    items: [
                        ("New chat", .leading),
                        ("Search in chat", .leading),
                        ("Voice input", .leading),
                    ])
            } else {
                // A chat pushed from the inbox may not offer "New chat"; the
                // same menu then carries Search and Voice only.
                capture(
                    name: "chat-header-sparkle",
                    app: app,
                    items: [
                        ("Search in chat", .leading),
                        ("Voice input", .leading),
                    ])
            }
            dismissMenu(app: app)
        } else {
            record("chat-header-sparkle: the sparkle button was not found")
        }

        // 4b. Header menu button — long-press opens a second menu.
        let headerMenu = app.buttons["Open menu"].firstMatch
        if headerMenu.waitForExistence(timeout: 10) {
            openContextMenu(on: headerMenu, app: app, expecting: "Chat options")
            capture(
                name: "chat-header-menu",
                app: app,
                items: [
                    ("Chat options", .leading),
                    ("Search in chat", .leading),
                    ("New chat", .leading),
                ])
            dismissMenu(app: app)
        } else {
            record("chat-header-menu: the header menu button was not found")
        }

        // 5. User bubble — Copy / Edit.
        let userText = app.staticTexts.matching(
            NSPredicate(format: "label CONTAINS %@", "What should I pack")).firstMatch
        if userText.waitForExistence(timeout: 10) {
            openContextMenu(on: userText, app: app, expecting: "Edit")
            capture(name: "user-bubble-context", app: app,
                    items: [("Copy", .leading), ("Edit", .leading)])
            dismissMenu(app: app)
        } else {
            record("user-bubble-context: the user turn was not found")
        }

        // 6. Assistant bubble — seven rows.
        openContextMenu(on: assistantText, app: app, expecting: "Branch new chat")
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
        dismissMenu(app: app)

        // 7. The drawer's recent-row menu.
        if headerMenu.exists { headerMenu.tap() } else { record("drawer: no menu button") }
        let recent = app.buttons.matching(
            NSPredicate(format: "label CONTAINS %@", "Explain quantum physics")).firstMatch
        if recent.waitForExistence(timeout: 12) {
            openContextMenu(on: recent, app: app, expecting: "Rename…")
            capture(
                name: "drawer-recent-context",
                app: app,
                items: [
                    ("Pin to top", .leading),
                    ("Rename…", .leading),
                    ("Archive", .leading),
                    ("Delete", .leading),
                ])
            dismissMenu(app: app)
        } else {
            record("drawer-recent-context: no recent row in the drawer")
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

    private func openContextMenu(on element: XCUIElement, app: XCUIApplication, expecting label: String) {
        element.press(forDuration: 1.3)
        if !app.buttons[label].waitForExistence(timeout: 12) {
            // The menu may expose rows as cells or static texts on some OS
            // builds; only a total absence is a failure.
            Thread.sleep(forTimeInterval: 0.6)
            if app.staticTexts[label].exists || app.cells[label].exists { return }
            record("menu '\(label)' never appeared after long-press")
            attachTree(app)
        }
    }

    private func dismissMenu(app: XCUIApplication) {
        // Menus close on a tap outside their card; the top strip is outside
        // every menu this harness opens.
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.04)).tap()
        // Wait for the dismissal, so the next step's long-press lands on the
        // page and not on a vanishing overlay.
        for _ in 0..<20 where app.buttons["Rename…"].exists || app.buttons["Regenerate"].exists {
            Thread.sleep(forTimeInterval: 0.1)
        }
    }

    private func closeSwipe(row: XCUIElement, app: XCUIApplication) {
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.04)).tap()
        _ = row.waitForExistence(timeout: 2)
    }

    // MARK: - Capture + measure

    private func capture(name: String, app: XCUIApplication, items: [(String, Slot)]) {
        let shot = XCUIScreen.main.screenshot()
        write(shot.pngRepresentation, to: "menu-\(name).png")

        guard let image = shot.image.cgImage else {
            record("\(name): the screenshot had no bitmap")
            return
        }
        let scale = CGFloat(image.width) / shot.image.size.width

        for (label, slot) in items {
            guard let frame = titleFrame(label, app: app) else {
                record("\(name)/\(label): the row is not in the menu")
                continue
            }
            let band = probeBand(for: frame, slot: slot)
            guard let probe = probeInk(image: image, band: band, scale: scale) else {
                record("\(name)/\(label): the probe band was degenerate")
                continue
            }
            measurements.append([
                "menu": name,
                "item": label,
                "slot": slot == .leading ? "leading" : "above",
                "inkPixels": probe.count,
                "inkHeightPt": Double(round(probe.bbox.height / scale * 10) / 10),
                "inkWidthPt": Double(round(probe.bbox.width / scale * 10) / 10),
                "bandWidthPt": Double(round(band.width * 10) / 10),
            ])

            // 1. The icon is drawn at all. An 17 pt stroke glyph carries a few
            //    hundred pixels at 2x; a dropped icon carries none, and a
            //    hairline carries a handful.
            let minInk = Int(90 * (scale / 2) * (scale / 2))
            if probe.count < minInk {
                record("\(name)/\(label): only \(probe.count) ink px (need \(minInk)) — the icon is missing or hairline")
            }
            // 2. It is an icon's size, not a speck and not a paragraph.
            let height = probe.bbox.height / scale
            if height < 9 || height > 24 {
                record("\(name)/\(label): glyph height \(String(format: "%.1f", height)) pt is outside 9–24 pt")
            }
            // 3. It sits in the slot its label implies, not somewhere else.
            if slot == .leading, probe.bbox.maxX > frame.minX + 1 {
                record("\(name)/\(label): ink reaches x=\(Int(probe.bbox.maxX / scale)) but the title starts at \(Int(frame.minX)) — the icon is not in the leading slot")
            }
        }
    }

    /// The row's title box. Menu rows expose one or the other depending on the
    /// OS build; the fallback uses the button's own frame and the row's
    /// standard leading inset.
    private func titleFrame(_ label: String, app: XCUIApplication) -> CGRect? {
        let text = app.staticTexts[label].firstMatch
        if text.exists, !text.frame.isEmpty { return text.frame }
        let button = app.buttons[label].firstMatch
        if button.exists, !button.frame.isEmpty { return button.frame }
        let cell = app.cells[label].firstMatch
        if cell.exists, !cell.frame.isEmpty { return cell.frame }
        return nil
    }

    private func probeBand(for frame: CGRect, slot: Slot) -> CGRect {
        switch slot {
        case .leading:
            // Immediately left of the title: the leading icon slot. 34 pt
            // clears an SF glyph's 17 pt box plus the 8–12 pt gap menus use.
            return CGRect(x: frame.minX - 34, y: frame.midY - 12, width: 32, height: 24)
        case .above:
            // Stacked layout: the icon lives over the title, centred.
            return CGRect(x: frame.midX - 22, y: frame.minY - 28, width: 44, height: 26)
        }
    }

    /// Ink = pixels that differ from the band's own median by more than a
    /// fraction of the range. Scheme-agnostic: it works on white-on-black,
    /// black-on-white and white-on-tint alike.
    private func probeInk(image: CGImage, band: CGRect, scale: CGFloat) -> (count: Int, bbox: CGRect)? {
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
                if abs(v - background) > 40 {
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

    private func attachTree(_ app: XCUIApplication) {
        let attachment = XCTAttachment(string: app.debugDescription)
        attachment.name = "accessibility-tree"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func write(_ data: Data?, to name: String) {
        guard let data else { return }
        let url = Self.outputDir.appendingPathComponent(name)
        try? data.write(to: url)
    }

    private func finish(app: XCUIApplication) throws {
        let report: [String: Any] = [
            "generated": ISO8601DateFormatter().string(from: Date()),
            "measurements": measurements,
            "failures": failures,
        ]
        if let data = try? JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys]) {
            write(data, to: "report.json")
        }
        attachTree(app)
    }
}
