import XCTest
@testable import GSApp

/// PHASE 6 — the honest chat-stream → orb mapping, including the real
/// image-analysis wait. The orb may only show states the pipeline really
/// has: an image request waiting for its first token is a REAL phase now
/// (the vision model genuinely receives and analyses the image), so
/// `.working` is mapped — never searching/solving/connecting/weaving/shaping,
/// which still have no real application state.
final class ChatVisionMappingTests: XCTestCase {

    // MARK: - Image requests (PHASE 6)

    func testImageRequestBeforeFirstTokenMapsToWorking() {
        XCTAssertEqual(
            orbStateForChatStreaming(isStreaming: true, liveContentEmpty: true, requestHasImages: true),
            .working
        )
        XCTAssertEqual(orbStateForChatStreaming(isStreaming: true, liveContentEmpty: true, requestHasImages: true)?.label, "Working…")
    }

    func testImageRequestTransitionsToComposingOnceTokensFlow() {
        XCTAssertEqual(
            orbStateForChatStreaming(isStreaming: true, liveContentEmpty: false, requestHasImages: true),
            .composing
        )
    }

    // MARK: - Text-only requests (Phase 4 behaviour preserved)

    func testTextRequestBeforeFirstTokenMapsToBreathing() {
        XCTAssertEqual(
            orbStateForChatStreaming(isStreaming: true, liveContentEmpty: true, requestHasImages: false),
            .breathing
        )
    }

    func testTextRequestWithTokensMapsToComposing() {
        XCTAssertEqual(
            orbStateForChatStreaming(isStreaming: true, liveContentEmpty: false, requestHasImages: false),
            .composing
        )
    }

    // MARK: - Terminal / idle never map (settled turn = no orb)

    func testNotStreamingMapsToNilRegardlessOfImages() {
        XCTAssertNil(orbStateForChatStreaming(isStreaming: false, liveContentEmpty: true, requestHasImages: true))
        XCTAssertNil(orbStateForChatStreaming(isStreaming: false, liveContentEmpty: false, requestHasImages: true))
        XCTAssertNil(orbStateForChatStreaming(isStreaming: false, liveContentEmpty: true, requestHasImages: false))
    }

    // MARK: - The fabricated states stay unmapped

    func testMappingNeverReturnsUnmappedCatalogueStates() {
        let fabricated: Set<OrbState> = [.searching, .solving, .connecting, .weaving, .shaping]
        let results: [OrbState?] = [
            orbStateForChatStreaming(isStreaming: true, liveContentEmpty: true, requestHasImages: true),
            orbStateForChatStreaming(isStreaming: true, liveContentEmpty: false, requestHasImages: true),
            orbStateForChatStreaming(isStreaming: true, liveContentEmpty: true, requestHasImages: false),
            orbStateForChatStreaming(isStreaming: true, liveContentEmpty: false, requestHasImages: false),
        ]
        for state in results.compactMap({ $0 }) {
            XCTAssertFalse(fabricated.contains(state), "fabricated orb state mapped: \(state)")
        }
    }
}
