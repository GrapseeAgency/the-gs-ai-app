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

        let sheets: [(row: String, marker: String)] = [
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
        notifications.tap()
        XCTAssertTrue(
            app.buttons["Filter notifications"].waitForExistence(timeout: 15),
            "the inbox opened without its filter control")
        XCTAssertTrue(
            app.staticTexts["Your updates, alerts and messages"].waitForExistence(timeout: 10),
            "the inbox opened without its subtitle")
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
        let back = app.buttons["Back"].firstMatch
        if back.waitForExistence(timeout: 8) {
            back.tap()
        } else {
            XCTFail("the sheet offered no Back control")
        }
        _ = app.staticTexts["Settings"].waitForExistence(timeout: 12)
    }
}
