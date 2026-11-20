import XCTest
@testable import GSApp

final class AssistantAvatarStyleTests: XCTestCase {
    func testFourStableBotAvatarChoices() {
        XCTAssertEqual(
            AssistantAvatarStyle.allCases.map(\.rawValue),
            ["clover", "flower", "triangle", "square"]
        )
        XCTAssertEqual(AssistantAvatarStyle.allCases.count, 4)
    }
}
