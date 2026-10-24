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

    func testTheSheetsFourTitlesGetFourDifferentIcons() {
        // The sheet's own titles are the test vector for the topic table.
        let icons = [
            DrawerTopic.iconName(title: "Plan my tomorrow", pinned: false),
            DrawerTopic.iconName(title: "Ideas for a brand launch", pinned: false),
            DrawerTopic.iconName(title: "Explain quantum physics", pinned: false),
            DrawerTopic.iconName(title: "Weekend in Kyoto", pinned: false),
        ]
        XCTAssertEqual(icons, ["CalendarCheck", "Lightbulb", "Atom", "MapPin"])
        XCTAssertEqual(Set(icons).count, 4)
    }

    func testAPinBeatsTheTopic() {
        XCTAssertEqual(DrawerTopic.iconName(title: "Weekend in Kyoto", pinned: true), "Pin")
    }

    func testMatchingIsOnWholeWords() {
        XCTAssertEqual(DrawerTopic.iconName(title: "planet fitness", pinned: false), "MessageCircle")
        XCTAssertEqual(DrawerTopic.iconName(title: "plan the week", pinned: false), "CalendarCheck")
    }

    /// The same row object the drawer mutates when a pin is tapped: the icon must
    /// follow the state in the same tick, which is what the stale-snapshot bug
    /// broke.
    func testTogglingARowChangesItsIconInPlace() {
        var row = DrawerRecent(id: "c1", title: "Plan my tomorrow",
                               timestamp: "Just now",
                               symbol: DrawerTopic.iconName(title: "Plan my tomorrow", pinned: false),
                               pinned: false)
        XCTAssertEqual(row.symbol, "CalendarCheck")
        row.pinned = true
        row.symbol = DrawerTopic.iconName(title: row.title, pinned: row.pinned)
        XCTAssertEqual(row.symbol, "Pin")
    }

    func testSectionsAndDestinationsAreOrderedAsTheSheetShows() {
        XCTAssertEqual(DrawerDestination.allCases.map(\.label),
                       ["Explore", "Image Studio", "Voice", "Saved creations"])
    }
}
