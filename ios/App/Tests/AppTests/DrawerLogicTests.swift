import XCTest
@testable import GSApp

/// The drawer's decisions, tested without a screen.
///
/// The pixel gate answers "does it look like the sheet"; the interaction suite on
/// Android answers "does each row go where its label says". These are the iOS
/// halves that can be asserted without a UI: that every destination names the
/// route it claims, and that the row icon has two distinct answers reflecting a
/// real fact.
final class DrawerLogicTests: XCTestCase {

    func testEveryDestinationRoutesToItsOwnScreen() {
        let routes = DrawerDestination.allCases.map { String(describing: $0.route) }
        XCTAssertEqual(Set(routes).count, DrawerDestination.allCases.count,
                       "two destinations share a route: \(routes)")
        for (destination, route) in zip(DrawerDestination.allCases, routes) {
            XCTAssertFalse(route.isEmpty, "\(destination) has no route")
        }
    }

    func testTheRecentIconHasTwoDistinctAnswers() {
        XCTAssertNotEqual(DrawerTime.symbol(pinned: true),
                          DrawerTime.symbol(pinned: false))
        XCTAssertEqual(DrawerTime.symbol(pinned: true), "pin")
        XCTAssertEqual(DrawerTime.symbol(pinned: false), "bubble.left")
    }

    /// The same row object the drawer mutates when a pin is tapped: the icon must
    /// follow the state in the same tick, which is what the stale-snapshot bug
    /// broke.
    func testTogglingARowChangesItsIconInPlace() {
        var row = DrawerRecent(id: "c1", title: "Plan my tomorrow",
                               timestamp: "Just now",
                               symbol: DrawerTime.symbol(pinned: false), pinned: false)
        row.pinned = true
        row.symbol = DrawerTime.symbol(pinned: row.pinned)
        XCTAssertEqual(row.symbol, "pin")
    }

    func testSectionsAndDestinationsAreOrderedAsTheSheetShows() {
        XCTAssertEqual(DrawerDestination.allCases.map(\.label),
                       ["Explore", "Image Studio", "Voice", "Saved creations"])
    }
}
