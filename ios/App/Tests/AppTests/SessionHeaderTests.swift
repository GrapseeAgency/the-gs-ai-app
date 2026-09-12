import XCTest
@testable import GSApp

/// TRANSPORT REGRESSION (upload "Network error" audit): the platform edge
/// rejects every invocation that lacks `x-session-id` (session affinity).
/// These tests pin the header contract on the shared client.
final class SessionHeaderTests: XCTestCase {

    func testSessionIDIsStablePerInstall() {
        XCTAssertFalse(APIClient.sessionID.isEmpty)
        // Two reads resolve to the same persisted value — affinity survives
        // process restarts because the value comes from UserDefaults.
        XCTAssertEqual(UserDefaults.standard.string(forKey: "gs.client.session-id"), APIClient.sessionID)
    }

    func testBuildRequestCarriesSessionHeader() throws {
        let request = try APIClient.shared.buildRequest(path: "/api/v1/models")
        XCTAssertEqual(
            request.value(forHTTPHeaderField: APIClient.sessionHeaderName),
            APIClient.sessionID
        )
    }

    func testBaseURLIsRealHTTPSEdge() {
        XCTAssertTrue(APIClient.shared.baseURL.absoluteString.hasPrefix("https://"))
        XCTAssertTrue(APIClient.shared.baseURL.absoluteString.contains("fcapp.run"))
    }
}
