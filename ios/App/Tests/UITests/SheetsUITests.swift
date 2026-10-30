import XCTest

/// The six owner sheets, opened from the real Settings hub on a simulator.
///
/// The render tests prove what each page looks like; the copy gate proves the
/// strings exist in source. Neither proves the page is REACHABLE, or that the
/// view that opens is the one under test. This drives the real navigation:
/// drawer → Settings → each row → Back, plus the upgrade pill for the plan and
/// the drawer's Notifications row for the inbox.
///
/// No launch seed is used for the inbox assertion: the filter control exists
/// whether the store is empty or not, so this does not depend on another test's
/// leftover rows.
final class SheetsUITests: XCTestCase {

    override func setUp() {
        super.setUp()
        continueAfterFailure = true
    }

    func testEveryOwnerSheetOpensFromSettings() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-gs_test_session_active"]
        app.launch()

        guard openSettings(app) else {
            XCTFail("Home never offered Settings; screen: \(app.debugDescription)")
            return
        }

        // In the hub's own order. `tapRow` only scrolls DOWN, which is correct
        // for a list read top-to-bottom — tapping App Settings rows out of
        // order would leave the next row above the viewport and the helper
        // would walk away from it.
        let sheets: [(row: String, marker: String)] = [
            ("Privacy Settings", "Your privacy matters"),
            ("AI Intelligence", "Model Selection & Reasoning Effort"),
            ("Notifications & Alerts", "Notification Types"),
            ("Account & Security", "Sign-in Methods"),
            ("Connected Apps", "Your Connected Apps"),
            ("Help & Support", "Browse by Category"),
        ]
        for sheet in sheets {
            guard tapRow(app, prefix: sheet.row) else {
                XCTFail("\(sheet.row) row was not reachable in Settings")
                continue
            }
            XCTAssertTrue(
                app.staticTexts[sheet.marker].waitForExistence(timeout: 15),
                "\(sheet.row) opened but never showed \"\(sheet.marker)\"")
            // Chat History & Search is reached from the privacy sheet, not a
            // hub row. Proof lives inside the privacy visit so the run keeps
            // its two-launch shape.
            if sheet.row == "Privacy Settings" {
                if tapRow(app, prefix: "Chat History") {
                    XCTAssertTrue(
                        app.staticTexts["Chat History & Search"].waitForExistence(timeout: 15),
                        "Chat History opened but never showed its own page")
                    XCTAssertTrue(
                        app.staticTexts["Recent Chats"].waitForExistence(timeout: 10),
                        "Chat History opened without its recent-chats section")
                    back(app)
                } else {
                    XCTFail("the privacy sheet never offered Chat History")
                }
            }
            goBack(app)
        }

        // The plan page is reached by the hub's Upgrade pill, not a row.
        for _ in 0..<3 { app.swipeDown() }
        let upgrade = app.buttons["Upgrade"].firstMatch
        if upgrade.waitForExistence(timeout: 10) {
            upgrade.tap()
            XCTAssertTrue(
                app.staticTexts["Plan Details"].waitForExistence(timeout: 15),
                "Upgrade opened but never showed Plan Details")
            goBack(app)
        } else {
            XCTFail("Settings never offered its Upgrade pill")
        }

        // The inbox is a drawer destination, not a Settings row.
        app.terminate()
        app.launchArguments = ["-gs_test_session_active"]
        app.launch()
        let menu = app.buttons["Open menu"].firstMatch
        guard menu.waitForExistence(timeout: 30) else {
            XCTFail("Home never offered its menu after relaunch")
            return
        }
        menu.tap()
        // Notifications is in the account sheet, not the four destination
        // rows; the footer avatar opens it.
        let account = app.buttons["Account options"].firstMatch
        guard account.waitForExistence(timeout: 12) else {
            XCTFail("the drawer footer never offered Account options")
            return
        }
        account.tap()
        let notifications = app.buttons["Notifications"].firstMatch
        guard notifications.waitForExistence(timeout: 12) else {
            XCTFail("the account sheet never offered Notifications")
            return
        }
        // The sheet slides in; a tap that lands mid-transition hits the
        // scrim and only dismisses the sheet — the drawer stays open and no
        // route fires (run 41023143540's snapshot showed exactly that). The
        // row reports hittable as soon as it is in the tree, before the
        // presentation settles, so wait for the transition to finish too.
        let hittable = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "isHittable == true"),
            object: notifications)
        XCTAssertEqual(XCTWaiter().wait(for: [hittable], timeout: 8), .completed)
        Thread.sleep(forTimeInterval: 0.8)
        // Keep a frame of the menu as it stands right before the tap: the
        // failure mode this test is chasing is a tap landing outside the row,
        // and only the pixels say where the row actually is.
        let beforeTap = XCTAttachment(screenshot: app.screenshot())
        beforeTap.name = "account-menu-before-tap"
        beforeTap.lifetime = .keepAlways
        add(beforeTap)
        notifications.tap()
        XCTAssertTrue(
            app.buttons["Filter notifications"].waitForExistence(timeout: 15),
            "the inbox opened without its filter control; screen: \(app.debugDescription)")
        XCTAssertTrue(
            app.staticTexts["Your updates, alerts and messages"].waitForExistence(timeout: 10),
            "the inbox opened without its subtitle; screen: \(app.debugDescription)")
    }

    /// Travel Agent was built but nothing navigated to it on either platform.
    /// Explore's catalogue now routes the trip planner there; this drives that
    /// path through the real search field (the tile is a search result, not a
    /// card on the landing page).
    func testTravelAgentOpensFromExploreSearch() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-gs_test_session_active"]
        app.launch()

        let menu = app.buttons["Open menu"].firstMatch
        guard menu.waitForExistence(timeout: 30) else {
            XCTFail("Home never offered its menu")
            return
        }
        menu.tap()
        // Inside the drawer, not Home's Explore quick action that sits under
        // it: both are labelled "Explore" and a plain firstMatch can resolve
        // the covered one.
        let drawer = app.otherElements["Navigation menu"].firstMatch
        guard drawer.waitForExistence(timeout: 12) else {
            XCTFail("the navigation menu never appeared")
            return
        }
        let explore = drawer.buttons["Explore"].firstMatch
        guard explore.waitForExistence(timeout: 12) else {
            XCTFail("the drawer never offered Explore")
            return
        }
        explore.tap()

        // Explore's own chrome replaces the system bar now; its section
        // headers open the full Discover page, where the trip planner is a
        // visible Popular row (no keyboard to push it off-screen).
        let seeAll = app.buttons["See All"].firstMatch
        guard seeAll.waitForExistence(timeout: 15) else {
            XCTFail("Explore never offered its See All control; screen: \(app.debugDescription)")
            return
        }
        seeAll.tap()
        XCTAssertTrue(
            app.staticTexts["Featured Tools"].waitForExistence(timeout: 15),
            "the See All control did not open Discover; screen: \(app.debugDescription)")

        let trip = app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "Plan a trip")).firstMatch
        // Popular sits below Featured Tools; walk down to it, and back up if a
        // fast swipe overshoots.
        for _ in 0..<6 where !(trip.exists && trip.isHittable) {
            app.swipeUp()
        }
        for _ in 0..<4 where !(trip.exists && trip.isHittable) {
            app.swipeDown()
        }
        guard trip.waitForExistence(timeout: 10), trip.isHittable else {
            XCTFail("Discover never offered a hittable Plan a trip; screen: \(app.debugDescription)")
            return
        }
        trip.tap()
        XCTAssertTrue(
            app.staticTexts["Welcome to Travel Agent"].waitForExistence(timeout: 15),
            "the trip planner opened something other than Travel Agent; screen: \(app.debugDescription)")
    }

    // MARK: helpers

    private func openSettings(_ app: XCUIApplication) -> Bool {
        let menu = app.buttons["Open menu"].firstMatch
        guard menu.waitForExistence(timeout: 30) else { return false }
        menu.tap()
        let settings = app.buttons["Settings"].firstMatch
        guard settings.waitForExistence(timeout: 12) else { return false }
        settings.tap()
        return app.staticTexts["Settings"].waitForExistence(timeout: 20)
    }

    /// Row labels carry the row's title and detail; match the title prefix and
    /// scroll it into reach before tapping.
    private func tapRow(_ app: XCUIApplication, prefix: String) -> Bool {
        let row = app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", prefix)).firstMatch
        for _ in 0..<5 {
            if row.exists && row.isHittable {
                row.tap()
                return true
            }
            app.swipeUp()
        }
        if row.exists {
            row.tap()
            return true
        }
        return false
    }

    private func goBack(_ app: XCUIApplication) {
        back(app)
        _ = app.staticTexts["Settings"].waitForExistence(timeout: 12)
    }

    /// One level up, without assuming which page is underneath.
    private func back(_ app: XCUIApplication) {
        let back = app.buttons["Back"].firstMatch
        if back.waitForExistence(timeout: 8) {
            back.tap()
        } else {
            XCTFail("the sheet offered no Back control")
        }
    }
}
