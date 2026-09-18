import XCTest
@testable import GSApp

/// PHASE 6/7 — the honest chat-stream → orb mapping. The orb may only show
/// states the pipeline really has:
///  - an image request waiting for its first token is a REAL phase (the
///    vision model genuinely receives and analyses the image);
///  - a document request waiting for its first token is a REAL phase (the
///    server genuinely extracts the PDF/TXT/MD/CSV text before answering);
/// so `.working` is mapped — never searching/solving/connecting/weaving/
/// shaping, which still have no real application state.
final class ChatVisionMappingTests: XCTestCase {

    // MARK: - Attachment requests (PHASE 6 images, PHASE 7 documents)

    func testImageRequestBeforeFirstTokenMapsToWorking() {
        XCTAssertEqual(
            orbStateForChatStreaming(isStreaming: true, liveContentEmpty: true, requestHasAttachments: true),
            .working
        )
        XCTAssertEqual(orbStateForChatStreaming(isStreaming: true, liveContentEmpty: true, requestHasAttachments: true)?.label, "Working…")
    }

    func testImageRequestTransitionsToComposingOnceTokensFlow() {
        XCTAssertEqual(
            orbStateForChatStreaming(isStreaming: true, liveContentEmpty: false, requestHasAttachments: true),
            .composing
        )
    }

    func testDocumentRequestBeforeFirstTokenMapsToWorking() {
        // PHASE 7 — server-side document extraction is real pre-first-token
        // work; the orb must show it, not "Thinking…".
        XCTAssertEqual(
            orbStateForChatStreaming(isStreaming: true, liveContentEmpty: true, requestHasAttachments: true),
            .working
        )
    }

    // MARK: - Text-only requests (Phase 4 behaviour preserved)

    func testTextRequestBeforeFirstTokenMapsToBreathing() {
        XCTAssertEqual(
            orbStateForChatStreaming(isStreaming: true, liveContentEmpty: true, requestHasAttachments: false),
            .breathing
        )
    }

    func testTextRequestWithTokensMapsToComposing() {
        XCTAssertEqual(
            orbStateForChatStreaming(isStreaming: true, liveContentEmpty: false, requestHasAttachments: false),
            .composing
        )
    }

    // MARK: - Terminal / idle never map (settled turn = no orb)

    func testNotStreamingMapsToNilRegardlessOfAttachments() {
        XCTAssertNil(orbStateForChatStreaming(isStreaming: false, liveContentEmpty: true, requestHasAttachments: true))
        XCTAssertNil(orbStateForChatStreaming(isStreaming: false, liveContentEmpty: false, requestHasAttachments: true))
        XCTAssertNil(orbStateForChatStreaming(isStreaming: false, liveContentEmpty: true, requestHasAttachments: false))
    }

    // MARK: - The fabricated states stay unmapped

    func testMappingNeverReturnsUnmappedCatalogueStates() {
        let fabricated: Set<OrbState> = [.searching, .solving, .connecting, .weaving, .shaping]
        let results: [OrbState?] = [
            orbStateForChatStreaming(isStreaming: true, liveContentEmpty: true, requestHasAttachments: true),
            orbStateForChatStreaming(isStreaming: true, liveContentEmpty: false, requestHasAttachments: true),
            orbStateForChatStreaming(isStreaming: true, liveContentEmpty: true, requestHasAttachments: false),
            orbStateForChatStreaming(isStreaming: true, liveContentEmpty: false, requestHasAttachments: false),
        ]
        for state in results.compactMap({ $0 }) {
            XCTAssertFalse(fabricated.contains(state), "fabricated orb state mapped: \(state)")
        }
    }
}
