import XCTest
@testable import GSApp

/// PHASE 5 attachment layer — pure-logic suite (docs/ATTACHMENTS.md §3/§5).
/// No device features, no permissions, no network: allowlist + kind mapping,
/// display-name sanitization, limits, the honest phase machine, send gating,
/// retry-step classification, the manual multipart body format, the typed
/// failure coding, the draft envelope (with legacy plain-string compat) and
/// the tolerant wire decoders.
final class AttachmentLogicTests: XCTestCase {

    // MARK: - Rules (mirror of src/lib/attachments.ts)

    func testAllowlistMatchesBackend() {
        let expected: Set<String> = [
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/heic",
            "image/heif", "application/pdf", "text/plain", "text/markdown",
            "text/csv",
        ]
        XCTAssertEqual(AttachmentRules.allowedMIMETypes, expected)
        // Deliberately absent: no audio/video — receiving them would fake a
        // capability (spec §0).
        XCTAssertFalse(AttachmentRules.allowedMIMETypes.contains("audio/mpeg"))
        XCTAssertFalse(AttachmentRules.allowedMIMETypes.contains("video/mp4"))
    }

    func testLimitsMatchBackend() {
        XCTAssertEqual(AttachmentRules.maxBytes, 10 * 1024 * 1024)
        XCTAssertEqual(AttachmentRules.maxPerMessage, 6)
    }

    func testKindForMime() {
        XCTAssertEqual(AttachmentRules.kindForMime("image/jpeg"), .image)
        XCTAssertEqual(AttachmentRules.kindForMime("image/heic"), .image)
        XCTAssertEqual(AttachmentRules.kindForMime("image/webp"), .image)
        XCTAssertEqual(AttachmentRules.kindForMime("application/pdf"), .pdf)
        XCTAssertEqual(AttachmentRules.kindForMime("text/plain"), .document)
        XCTAssertEqual(AttachmentRules.kindForMime("text/markdown"), .document)
        XCTAssertEqual(AttachmentRules.kindForMime("text/csv"), .document)
        XCTAssertNil(AttachmentRules.kindForMime("video/mp4"))
        XCTAssertNil(AttachmentRules.kindForMime("audio/mpeg"))
        XCTAssertNil(AttachmentRules.kindForMime("application/zip"))
        XCTAssertNil(AttachmentRules.kindForMime("application/octet-stream"))
    }

    func testMimeForFileName() {
        XCTAssertEqual(AttachmentRules.mimeForFileName("photo.JPG"), "image/jpeg")
        XCTAssertEqual(AttachmentRules.mimeForFileName("scan.pdf"), "application/pdf")
        XCTAssertEqual(AttachmentRules.mimeForFileName("notes.md"), "text/markdown")
        XCTAssertEqual(AttachmentRules.mimeForFileName("table.csv"), "text/csv")
        XCTAssertEqual(AttachmentRules.mimeForFileName("readme.txt"), "text/plain")
        XCTAssertNil(AttachmentRules.mimeForFileName("archive.zip"))
        XCTAssertNil(AttachmentRules.mimeForFileName("noextension"))
    }

    func testSanitizeDisplayNameStripsPathsAndControlChars() {
        XCTAssertEqual(AttachmentRules.sanitizeDisplayName("a/b/c.txt"), "c.txt")
        XCTAssertEqual(AttachmentRules.sanitizeDisplayName("..\\evil.png"), "evil.png")
        XCTAssertEqual(AttachmentRules.sanitizeDisplayName("na\u{01}me\u{7F}.txt"), "name.txt")
        XCTAssertEqual(AttachmentRules.sanitizeDisplayName("  spaced name.pdf  "), "spaced name.pdf")
        XCTAssertEqual(AttachmentRules.sanitizeDisplayName(""), "attachment")
        XCTAssertEqual(AttachmentRules.sanitizeDisplayName("///"), "attachment")
    }

    func testSanitizeDisplayNameCapsLengthAt120() {
        let long = String(repeating: "a", count: 300)
        XCTAssertEqual(AttachmentRules.sanitizeDisplayName(long).count, 120)
        XCTAssertEqual(AttachmentRules.sanitizeDisplayName(long), String(repeating: "a", count: 120))
    }

    func testHumanBytesIsDeterministic() {
        XCTAssertEqual(AttachmentRules.humanBytes(0), "0 B")
        XCTAssertEqual(AttachmentRules.humanBytes(33), "33 B")
        XCTAssertEqual(AttachmentRules.humanBytes(1024), "1 KB")
        XCTAssertEqual(AttachmentRules.humanBytes(1536), "1.5 KB")
        XCTAssertEqual(AttachmentRules.humanBytes(1024 * 1024), "1 MB")
        XCTAssertEqual(AttachmentRules.humanBytes(10 * 1024 * 1024), "10 MB")
    }

    // MARK: - Pre-flight validation (spec §3)

    func testValidateFailsFastAndHonestly() {
        XCTAssertEqual(
            AttachmentTransitions.validate(byteSize: AttachmentRules.maxBytes + 1, mimeType: "image/png"),
            .tooLarge)
        XCTAssertEqual(
            AttachmentTransitions.validate(byteSize: 100, mimeType: "video/mp4"),
            .unsupported)
        XCTAssertEqual(
            AttachmentTransitions.validate(byteSize: 0, mimeType: "text/plain"),
            .readFailed)
        XCTAssertNil(AttachmentTransitions.validate(byteSize: 100, mimeType: "text/plain"))
        XCTAssertNil(AttachmentTransitions.validate(byteSize: AttachmentRules.maxBytes, mimeType: "image/gif"))
    }

    // MARK: - Phase machine (spec §5 — selected → preparing → uploading → ready)

    private func makeDraft(phase: AttachmentPhase) -> AttachmentDraft {
        AttachmentDraft(
            id: "client-1",
            kind: .image,
            source: .gallery,
            displayName: "tiny.png",
            mimeType: "image/png",
            phase: phase,
            byteSize: 33,
            createdAt: "2026-09-12T03:03:20.500Z")
    }

    func testHappyPathTransitions() {
        var draft = makeDraft(phase: .selected)
        XCTAssertEqual(draft.phase, .selected)

        draft = AttachmentTransitions.beginningPrepare(draft)
        XCTAssertEqual(draft.phase, .preparing)

        draft = AttachmentTransitions.stagedForUpload(draft, path: "/tmp/staged/tiny.png", byteSize: 33)
        XCTAssertEqual(draft.phase, .uploading)
        XCTAssertEqual(draft.localPath, "/tmp/staged/tiny.png")
        XCTAssertEqual(draft.byteSize, 33)

        draft = AttachmentTransitions.uploaded(draft, remote: Attachment(
            id: "srv-1", kind: "image", displayName: "tiny.png", mimeType: "image/png",
            byteSize: 33, createdAt: "2026-09-12T03:03:21.000Z", url: "/api/v1/files/srv-1"))
        XCTAssertEqual(draft.phase, .ready)
        XCTAssertEqual(draft.serverID, "srv-1")
        XCTAssertEqual(draft.remoteURL, "/api/v1/files/srv-1")
        XCTAssertEqual(draft.error, nil)
    }

    func testFailureTransitionCarriesStructuredReason() {
        let draft = AttachmentTransitions.failed(makeDraft(phase: .uploading), with: .server(429))
        XCTAssertEqual(draft.phase, .failed)
        XCTAssertEqual(draft.error, .server(429))
        XCTAssertEqual(draft.error?.label, "Too many uploads — try again in a moment")
    }

    func testRetryStepReRunsOnlyTheFailedStep() {
        // Failed at upload with a staged copy → retry upload only.
        var draft = AttachmentTransitions.stagedForUpload(makeDraft(phase: .uploading), path: "/tmp/x.png", byteSize: 33)
        draft = AttachmentTransitions.failed(draft, with: .network)
        XCTAssertEqual(AttachmentTransitions.retryStep(for: draft), .upload)

        // Failed before anything was staged → retry prepare.
        let prepare = AttachmentTransitions.failed(makeDraft(phase: .selected), with: .readFailed)
        XCTAssertEqual(AttachmentTransitions.retryStep(for: prepare), .prepare)

        // Not failed → no retry.
        XCTAssertEqual(AttachmentTransitions.retryStep(for: makeDraft(phase: .ready)), .none)
        XCTAssertEqual(AttachmentTransitions.retryStep(for: makeDraft(phase: .uploading)), .none)
    }

    // MARK: - Send gating (spec §5: every attachment ready, or removed)

    func testCanSendGate() {
        XCTAssertTrue(AttachmentTransitions.canSend([])) // text-only send: no attachment blocks
        XCTAssertTrue(AttachmentTransitions.canSend([makeDraft(phase: .ready), makeDraft(phase: .ready)]))
        XCTAssertFalse(AttachmentTransitions.canSend([makeDraft(phase: .ready), makeDraft(phase: .uploading)]))
        XCTAssertFalse(AttachmentTransitions.canSend([makeDraft(phase: .failed)]))
        XCTAssertFalse(AttachmentTransitions.canSend([makeDraft(phase: .preparing)]))
    }

    func testSendBlockReasonIsHonest() {
        XCTAssertNil(AttachmentTransitions.sendBlockReason([]))
        XCTAssertNil(AttachmentTransitions.sendBlockReason([makeDraft(phase: .ready)]))

        let uploading = AttachmentTransitions.sendBlockReason([makeDraft(phase: .uploading)])
        XCTAssertEqual(uploading, "Attachments are still uploading")

        let failed = AttachmentTransitions.sendBlockReason([makeDraft(phase: .ready), makeDraft(phase: .failed)])
        XCTAssertEqual(failed, "An attachment failed — retry or remove it to send")
    }

    // MARK: - Multipart body (manual build, no external deps)

    func testMultipartBodyFormatIsExact() {
        let bytes = Data([0x89, 0x50, 0x4E, 0x47])
        let body = MultipartFormData.body(
            boundary: "BOUNDARY",
            field: "file",
            filename: "tiny.png",
            mimeType: "image/png",
            data: bytes,
            extraFields: ["displayName": "tiny.png"])

        let expected =
            "--BOUNDARY\r\n" +
            "Content-Disposition: form-data; name=\"displayName\"\r\n\r\n" +
            "tiny.png\r\n" +
            "--BOUNDARY\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"tiny.png\"\r\n" +
            "Content-Type: image/png\r\n\r\n"
        var expectedData = Data(expected.utf8)
        expectedData.append(bytes)
        expectedData.append(Data("\r\n--BOUNDARY--\r\n".utf8))

        XCTAssertEqual(body, expectedData)
    }

    func testMultipartBodyEscapesAndTerminates() {
        let body = MultipartFormData.body(
            boundary: "b",
            field: "file",
            filename: "we\"ird\\name.png",
            mimeType: "image/png",
            data: Data([0x01]))

        let text = String(decoding: body, as: UTF8.self)
        XCTAssertTrue(text.hasPrefix("--b\r\n"))
        XCTAssertTrue(text.contains("filename=\"we\\\"ird\\\\name.png\""))
        XCTAssertTrue(text.hasSuffix("\r\n--b--\r\n"))
        // The file part carries its Content-Type line.
        XCTAssertTrue(text.contains("Content-Type: image/png\r\n"))
    }

    func testMultipartExtraFieldsAreSortedForDeterminism() {
        let a = MultipartFormData.body(boundary: "b", field: "file", filename: "f", mimeType: "text/plain", data: Data(), extraFields: ["zeta": "1", "alpha": "2"])
        let b = MultipartFormData.body(boundary: "b", field: "file", filename: "f", mimeType: "text/plain", data: Data(), extraFields: ["alpha": "2", "zeta": "1"])
        XCTAssertEqual(a, b)
        let text = String(decoding: a, as: UTF8.self)
        let alphaRange = text.range(of: "name=\"alpha\"")
        let zetaRange = text.range(of: "name=\"zeta\"")
        XCTAssertNotNil(alphaRange)
        XCTAssertNotNil(zetaRange)
        XCTAssertTrue(alphaRange!.lowerBound < zetaRange!.lowerBound)
    }

    // MARK: - Failure taxonomy coding (draft persistence)

    func testAttachmentFailureCodableRoundTrip() throws {
        let cases: [AttachmentFailure] = [.tooLarge, .unsupported, .readFailed, .network, .server(413), .server(500)]
        for failure in cases {
            let data = try JSONEncoder().encode([failure])
            let decoded = try JSONDecoder().decode([AttachmentFailure].self, from: data)
            XCTAssertEqual(decoded, [failure])
        }
        // The wire codes stay snake_case-honest for debuggability.
        let raw = String(decoding: try JSONEncoder().encode(AttachmentFailure.tooLarge), as: UTF8.self)
        XCTAssertTrue(raw.contains("too_large"))
    }

    // MARK: - Draft envelope (draft_<id> JSON; legacy plain-string compat)

    func testDraftEnvelopeRoundTrip() throws {
        var draft = makeDraft(phase: .ready)
        draft.serverID = "srv-9"
        draft.remoteURL = "/api/v1/files/srv-9"
        draft.localPath = "/tmp/staged/tiny.png"

        let raw = DraftEnvelope.encode(text: "hello there", drafts: [draft])
        XCTAssertNotNil(raw)

        let envelope = DraftEnvelope.decode(raw!)
        XCTAssertNotNil(envelope)
        XCTAssertEqual(envelope?.text, "hello there")
        XCTAssertEqual(envelope?.attachments, [draft])
    }

    func testLegacyPlainStringDraftIsNotEnvelopeJSON() {
        XCTAssertNil(DraftEnvelope.decode("just a prompt, typed by hand"))
        XCTAssertNil(DraftEnvelope.decode("{not json"))
    }

    // MARK: - Wire models (tolerant decode, spec §1 exact shape)

    func testAttachmentWireDecode() throws {
        let json = """
        {"id":"cmtxsvxdf0003rp9gr8v93oa5","kind":"image","displayName":"tiny.png",
         "mimeType":"image/png","byteSize":33,
         "createdAt":"2026-09-12T03:03:20.500Z","url":"/api/v1/files/cmtxsvxdf0003rp9gr8v93oa5"}
        """
        let attachment = try JSONDecoder().decode(Attachment.self, from: Data(json.utf8))
        XCTAssertEqual(attachment.id, "cmtxsvxdf0003rp9gr8v93oa5")
        XCTAssertEqual(attachment.kind, "image")
        XCTAssertEqual(attachment.displayName, "tiny.png")
        XCTAssertEqual(attachment.mimeType, "image/png")
        XCTAssertEqual(attachment.byteSize, 33)
        XCTAssertEqual(attachment.url, "/api/v1/files/cmtxsvxdf0003rp9gr8v93oa5")
    }

    func testMessageAttachmentsDecodeTolerantly() throws {
        let withAttachments = """
        {"id":"m1","conversationId":"c1","role":"user","content":"see attached",
         "createdAt":"2026-09-12T03:03:20.500Z",
         "attachments":[{"id":"a1","kind":"pdf","displayName":"doc.pdf","mimeType":"application/pdf",
                         "byteSize":10,"createdAt":"2026-09-12T03:03:20.000Z","url":"/api/v1/files/a1"}]}
        """
        let message = try JSONDecoder().decode(Message.self, from: Data(withAttachments.utf8))
        XCTAssertEqual(message.attachments?.count, 1)
        XCTAssertEqual(message.attachments?.first?.kind, "pdf")

        // Older servers / assistant turns: no key → nil, never a decode failure.
        let without = """
        {"id":"m2","conversationId":"c1","role":"assistant","content":"hi",
         "createdAt":"2026-09-12T03:03:20.500Z"}
        """
        let plain = try JSONDecoder().decode(Message.self, from: Data(without.utf8))
        XCTAssertNil(plain.attachments)
    }

    func testSendMessageRequestOmitsNilAttachmentsOnTheWire() throws {
        // Text-only send must stay byte-compatible with the pre-attachments
        // contract: no "attachments" key travels.
        let textOnly = try JSONEncoder().encode(SendMessageRequest(content: "hi", stream: true))
        let textObject = try JSONSerialization.jsonObject(with: textOnly) as! [String: Any]
        XCTAssertNil(textObject["attachments"])
        XCTAssertEqual(textObject["content"] as? String, "hi")
        XCTAssertEqual(textObject["stream"] as? Bool, true)

        // With attachments the ids travel as strings.
        let withIDs = try JSONEncoder().encode(SendMessageRequest(
            content: "", stream: true, attachments: ["a1", "a2"]))
        let withObject = try JSONSerialization.jsonObject(with: withIDs) as! [String: Any]
        XCTAssertEqual(withObject["attachments"] as? [String], ["a1", "a2"])
    }
}
