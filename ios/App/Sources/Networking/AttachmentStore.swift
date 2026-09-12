import Foundation
import UniformTypeIdentifiers

// MARK: - PHASE 5 attachment layer (docs/ATTACHMENTS.md)
//
// Reception is real: picking, staging, validating and uploading user content
// are genuine operations against POST /api/v1/uploads. Analysis does not
// exist — nothing here inspects content beyond the MIME/size rules the
// server also enforces. No invented states: the phase machine carries NO
// "processing" step because the server has none (spec §0/§5).
//
// Threading discipline: the store is @MainActor (all published mutations land
// on main); staging copies and multipart uploads run detached on the
// background plumbing and hop back with one await. Everything below the
// store that is pure (rules, transitions, multipart body assembly) is
// testable without a device, network or filesystem.

// MARK: - Vocabulary

/// Shipped media kinds (spec §0): image | pdf | document. audio/video/
/// screenshot are reserved in the model and MUST NOT be offered until a real
/// consumer exists — deliberately no cases for them.
enum AttachmentKind: String, Codable, CaseIterable, Equatable {
    case image
    case pdf
    case document
}

/// Where the bytes came from (spec §1 `source`).
enum AttachmentSource: String, Codable, Equatable {
    case gallery
    case camera
    case files
}

/// Honest phase machine (spec §5):
/// selected → preparing → uploading → ready, with failed reachable from
/// preparing and uploading. Retry re-runs ONLY the failed step.
enum AttachmentPhase: String, Codable, Equatable {
    case selected      // bytes known, staging queued — the chip appears
    case preparing     // copying/validating on background
    case uploading     // real HTTP multipart in flight (NO fabricated percent)
    case ready         // server record + remoteURL exist
    case failed        // validation, staging, network or HTTP error
}

/// Structured failure taxonomy (spec §5) — surfaced honestly, never smoothed
/// into a generic "something went wrong".
enum AttachmentFailure: Equatable {
    case tooLarge
    case unsupported
    case readFailed
    case network
    case server(Int)

    /// Human copy that states the real reason, nothing more.
    var label: String {
        switch self {
        case .tooLarge: return "File is larger than the 10 MB limit"
        case .unsupported: return "This file type isn't accepted yet"
        case .readFailed: return "Couldn't read a copy of the file"
        case .network: return "Upload failed — check your connection"
        case .server(429): return "Too many uploads — try again in a moment"
        case .server(let code): return "Upload failed (server error \(code))"
        }
    }
}

extension AttachmentFailure: Codable {
    private enum CodingKeys: String, CodingKey {
        case code
        case status
    }

    private enum WireCode: String, Codable {
        case tooLarge = "too_large"
        case unsupported
        case readFailed = "read_failed"
        case network
        case server
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        switch try container.decode(WireCode.self, forKey: .code) {
        case .tooLarge: self = .tooLarge
        case .unsupported: self = .unsupported
        case .readFailed: self = .readFailed
        case .network: self = .network
        case .server: self = .server(try container.decodeIfPresent(Int.self, forKey: .status) ?? 0)
        }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case .tooLarge: try container.encode(WireCode.tooLarge, forKey: .code)
        case .unsupported: try container.encode(WireCode.unsupported, forKey: .code)
        case .readFailed: try container.encode(WireCode.readFailed, forKey: .code)
        case .network: try container.encode(WireCode.network, forKey: .code)
        case .server(let status):
            try container.encode(WireCode.server, forKey: .code)
            try container.encode(status, forKey: .status)
        }
    }
}

// MARK: - Client pre-flight rules (spec §3 — mirror of src/lib/attachments.ts)

struct AttachmentRules {

    /// Receive allowlist — identical to the backend set. Audio/video are
    /// deliberately absent: receiving them would fake a capability.
    static let allowedMIMETypes: Set<String> = [
        "image/jpeg", "image/png", "image/webp", "image/gif", "image/heic",
        "image/heif", "application/pdf", "text/plain", "text/markdown",
        "text/csv",
    ]

    /// Hard byte cap per attachment — the server re-checks; this is the
    /// early, friendly pre-flight only.
    static let maxBytes = 10 * 1024 * 1024

    /// Hard cap per message.
    static let maxPerMessage = 6

    /// MIME → kind, mirroring `kindForMime` on the server.
    static func kindForMime(_ mime: String) -> AttachmentKind? {
        if mime.hasPrefix("image/") { return .image }
        if mime == "application/pdf" { return .pdf }
        if mime.hasPrefix("text/") { return .document }
        return nil
    }

    /// File extension → allowlisted MIME. nil = not in the allowlist.
    static func mimeForFileName(_ raw: String) -> String? {
        let ext = (raw.split(separator: ".").last.map(String.init) ?? "").lowercased()
        switch ext {
        case "jpg", "jpeg": return "image/jpeg"
        case "png": return "image/png"
        case "webp": return "image/webp"
        case "gif": return "image/gif"
        case "heic": return "image/heic"
        case "heif": return "image/heif"
        case "pdf": return "application/pdf"
        case "txt", "text": return "text/plain"
        case "md", "markdown": return "text/markdown"
        case "csv": return "text/csv"
        default: return nil
        }
    }

    /// Strip path separators and control characters, keep a readable bounded
    /// filename — the exact contract of the server's `sanitizeDisplayName`
    /// (never trust picker names as paths, spec §9).
    static func sanitizeDisplayName(_ raw: String) -> String {
        let base = raw
            .split(whereSeparator: { $0 == "/" || $0 == "\\" })
            .last
            .map(String.init) ?? ""
        let cleaned = base
            .filter { character in
                character.unicodeScalars.allSatisfy { $0.value >= 32 && $0.value != 0x7F }
            }
            .trimmingCharacters(in: .whitespaces)
        let safe = cleaned.isEmpty ? "attachment" : cleaned
        return safe.count > 120 ? String(safe.prefix(120)) : safe
    }

    /// Deterministic human byte size (no locale drift — the tests pin it).
    static func humanBytes(_ bytes: Int) -> String {
        let kb = 1024.0, mb = kb * 1024.0, gb = mb * 1024.0
        let value = Double(max(0, bytes))
        func trimmed(_ value: Double) -> String {
            let rounded = (value * 10).rounded() / 10
            return rounded == rounded.rounded() ? String(Int(rounded)) : String(format: "%.1f", rounded)
        }
        if value >= gb { return "\(trimmed(value / gb)) GB" }
        if value >= mb { return "\(trimmed(value / mb)) MB" }
        if value >= kb { return "\(trimmed(value / kb)) KB" }
        return "\(Int(value)) B"
    }

    /// Preferred extension for a synthesized display name.
    static func preferredExtension(forMime mime: String) -> String {
        switch mime {
        case "image/jpeg": return "jpg"
        case "image/png": return "png"
        case "image/webp": return "webp"
        case "image/gif": return "gif"
        case "image/heic": return "heic"
        case "image/heif": return "heif"
        case "application/pdf": return "pdf"
        case "text/plain": return "txt"
        case "text/markdown": return "md"
        case "text/csv": return "csv"
        default: return "bin"
        }
    }
}

// MARK: - Draft (spec §1 logical model, client side)

/// One attachment on its way into a message. `id` is the CLIENT uuid until
/// the upload returns the server record; afterwards `serverID` + `remoteURL`
/// carry the real identity. Persistable (draft envelope JSON).
struct AttachmentDraft: Identifiable, Codable, Equatable {
    let id: String
    var kind: AttachmentKind
    var source: AttachmentSource
    var displayName: String
    var mimeType: String
    var phase: AttachmentPhase
    var error: AttachmentFailure?
    /// App-private staged copy path (string form for Codable portability).
    var localPath: String?
    /// Relative "/api/v1/files/<id>" once uploaded.
    var remoteURL: String?
    var serverID: String?
    /// The staged copy's size is the truth — never picker metadata.
    var byteSize: Int
    var createdAt: String

    var localURL: URL? { localPath.map { URL(fileURLWithPath: $0) } }

    init(
        id: String,
        kind: AttachmentKind,
        source: AttachmentSource,
        displayName: String,
        mimeType: String,
        phase: AttachmentPhase = .selected,
        error: AttachmentFailure? = nil,
        localPath: String? = nil,
        remoteURL: String? = nil,
        serverID: String? = nil,
        byteSize: Int = 0,
        createdAt: String = ""
    ) {
        self.id = id
        self.kind = kind
        self.source = source
        self.displayName = displayName
        self.mimeType = mimeType
        self.phase = phase
        self.error = error
        self.localPath = localPath
        self.remoteURL = remoteURL
        self.serverID = serverID
        self.byteSize = byteSize
        self.createdAt = createdAt
    }
}

// MARK: - Pure state machine (spec §5) — unit-testable, no IO

enum AttachmentTransitions {

    enum RetryStep: Equatable {
        case prepare   // re-run staging (nothing usable staged yet)
        case upload    // staged copy exists — go straight back to upload
        case none      // not failed; retry is a no-op
    }

    /// Which failed step a retry re-runs. Pure on draft fields; the store
    /// additionally re-verifies the staged file at retry time.
    static func retryStep(for draft: AttachmentDraft) -> RetryStep {
        guard draft.phase == .failed else { return .none }
        if let path = draft.localPath, !path.isEmpty, draft.byteSize > 0 {
            return .upload
        }
        return .prepare
    }

    /// Send gate (spec §5): every attachment must be `ready` (or removed).
    /// An empty list does not block — text-only sends gate on text alone.
    static func canSend(_ drafts: [AttachmentDraft]) -> Bool {
        drafts.allSatisfy { $0.phase == .ready }
    }

    /// The visible reason send is blocked, or nil. Never fabricated — each
    /// branch states what is actually happening.
    static func sendBlockReason(_ drafts: [AttachmentDraft]) -> String? {
        guard !drafts.isEmpty else { return nil }
        if drafts.contains(where: { $0.phase == .failed }) {
            return "An attachment failed — retry or remove it to send"
        }
        if drafts.contains(where: { $0.phase != .ready }) {
            return "Attachments are still uploading"
        }
        return nil
    }

    /// Pre-flight validation before any upload (spec §3). The server stays
    /// the authority; this only fails fast and honestly.
    static func validate(byteSize: Int, mimeType: String) -> AttachmentFailure? {
        guard AttachmentRules.kindForMime(mimeType) != nil,
              AttachmentRules.allowedMIMETypes.contains(mimeType) else { return .unsupported }
        if byteSize <= 0 { return .readFailed }
        if byteSize > AttachmentRules.maxBytes { return .tooLarge }
        return nil
    }

    /// failed → preparing (retry re-runs staging).
    static func beginningPrepare(_ draft: AttachmentDraft) -> AttachmentDraft {
        var next = draft
        next.phase = .preparing
        next.error = nil
        return next
    }

    /// staged, validation passed → uploading.
    static func stagedForUpload(_ draft: AttachmentDraft, path: String, byteSize: Int) -> AttachmentDraft {
        var next = draft
        next.phase = .uploading
        next.error = nil
        next.localPath = path
        next.byteSize = byteSize
        return next
    }

    /// 201 received → ready with the server record.
    static func uploaded(_ draft: AttachmentDraft, remote: Attachment) -> AttachmentDraft {
        var next = draft
        next.phase = .ready
        next.error = nil
        next.serverID = remote.id
        next.remoteURL = remote.url
        next.byteSize = remote.byteSize
        next.displayName = remote.displayName.isEmpty ? next.displayName : remote.displayName
        return next
    }

    /// any failure → failed with the structured reason.
    static func failed(_ draft: AttachmentDraft, with error: AttachmentFailure) -> AttachmentDraft {
        var next = draft
        next.phase = .failed
        next.error = error
        return next
    }
}

// MARK: - Multipart body builder (pure, no external deps)

/// Manual multipart/form-data assembly — URLSession has no multipart API and
/// the project carries no external packages. Pure function so the exact wire
/// format is unit-testable.
enum MultipartFormData {

    /// Quoted-string escape per RFC 7578/2183: backslashes and quotes
    /// escaped, CR/LF neutralized (defensive — names are pre-sanitized).
    static func escaped(_ value: String) -> String {
        var out = ""
        out.reserveCapacity(value.count)
        for character in value {
            switch character {
            case "\"": out += "\\\""
            case "\\": out += "\\\\"
            case "\r", "\n": out.append(" ")
            default: out.append(character)
            }
        }
        return out
    }

    /// Builds the full body: optional scalar fields first (sorted for
    /// determinism), then the file part, terminated by the closing boundary.
    /// Every line ends with CRLF per the multipart spec.
    static func body(
        boundary: String,
        field: String,
        filename: String,
        mimeType: String,
        data: Data,
        extraFields: [String: String] = [:]
    ) -> Data {
        var out = Data()
        func append(_ text: String) { out.append(Data(text.utf8)) }

        for (name, value) in extraFields.sorted(by: { $0.key < $1.key }) {
            append("--\(boundary)\r\n")
            append("Content-Disposition: form-data; name=\"\(escaped(name))\"\r\n\r\n")
            append("\(value)\r\n")
        }
        append("--\(boundary)\r\n")
        append("Content-Disposition: form-data; name=\"\(escaped(field))\"; filename=\"\(escaped(filename))\"\r\n")
        append("Content-Type: \(escaped(mimeType))\r\n\r\n")
        out.append(data)
        append("\r\n--\(boundary)--\r\n")
        return out
    }
}

// MARK: - Draft persistence envelope (spec §5)

/// JSON envelope persisted under the per-conversation draft key:
/// `{"text": "...", "attachments": [AttachmentDraft…]}`. Backward
/// compatibility: an old plain-string draft value decodes as text-only.
struct DraftEnvelope: Codable, Equatable {
    var text: String
    var attachments: [AttachmentDraft]

    static func encode(text: String, drafts: [AttachmentDraft]) -> String? {
        guard let data = try? JSONEncoder().encode(DraftEnvelope(text: text, attachments: drafts)) else {
            return nil
        }
        return String(decoding: data, as: UTF8.self)
    }

    /// Returns nil when `raw` is not envelope JSON (legacy plain-string
    /// draft) — the caller treats it as the text itself.
    static func decode(_ raw: String) -> DraftEnvelope? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.hasPrefix("{") else { return nil }
        return try? JSONDecoder().decode(DraftEnvelope.self, from: Data(trimmed.utf8))
    }
}

// MARK: - The store

@MainActor
final class AttachmentStore: ObservableObject {

    static let shared = AttachmentStore()

    /// Composer-visible drafts, in pick order.
    @Published private(set) var drafts: [AttachmentDraft] = []

    /// 201 response envelope: {"attachment": {…}}.
    private struct UploadEnvelope: Codable {
        var attachment: Attachment
    }

    /// Retry fuel for the CURRENT session only (never persisted): original
    /// picker bytes, or the security-scoped file URL for document picks.
    private enum SourcePayload {
        case data(Data)
        case fileURL(URL)
    }

    private var sources: [String: SourcePayload] = [:]

    /// remoteID → staged local path, so transcript thumbnails can use the
    /// staged copy after the draft left the composer (session-scoped; the
    /// honest fallback elsewhere is the monochrome kind icon).
    private var stagedByRemoteID: [String: String] = [:]

    // MARK: Derived state

    var remainingSlots: Int { max(0, AttachmentRules.maxPerMessage - drafts.count) }
    /// True when every draft is ready (empty list does not block text sends).
    var canSend: Bool { AttachmentTransitions.canSend(drafts) }
    var sendBlockReason: String? { AttachmentTransitions.sendBlockReason(drafts) }
    var hasDrafts: Bool { !drafts.isEmpty }

    /// Staged copy for an uploaded attachment (transcript thumbnails).
    func stagedLocalURL(forRemoteID remoteID: String) -> URL? {
        guard let path = stagedByRemoteID[remoteID] else { return nil }
        return URL(fileURLWithPath: path)
    }

    // MARK: Storage locations (spec §4)

    /// Application Support/Attachments/<uuid>/<displayName>
    nonisolated static func stagingDirectory(for id: String) -> URL {
        attachmentsRoot()
            .appendingPathComponent(id, isDirectory: true)
    }

    nonisolated static func attachmentsRoot() -> URL {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return support.appendingPathComponent("Attachments", isDirectory: true)
    }

    nonisolated private static func uploadsURL() -> URL {
        let base = APIClient.shared.baseURL
        return URL(string: "/api/v1/uploads", relativeTo: base) ?? base
    }

    // MARK: Adding (pick → selected → preparing → uploading → ready)

    /// Gallery / camera bytes. The caller resolved the MIME (picker UTType or
    /// the camera's fixed image/jpeg); the store validates honestly.
    func addImageData(
        _ data: Data,
        mimeType: String,
        source: AttachmentSource,
        suggestedName: String? = nil
    ) {
        guard remainingSlots > 0 else { return } // picker flow disables; guard is defense
        let mime = mimeType.lowercased()
        guard let kind = AttachmentRules.kindForMime(mime),
              AttachmentRules.allowedMIMETypes.contains(mime) else { return }

        let name = AttachmentRules.sanitizeDisplayName(
            suggestedName ?? Self.defaultDisplayName(kind: kind, mimeType: mime))
        let draft = AttachmentDraft(
            id: UUID().uuidString,
            kind: kind,
            source: source,
            displayName: name,
            mimeType: mime,
            phase: .selected,
            byteSize: data.count,     // staged size replaces this after staging
            createdAt: ConversationStore.now())

        // Honest early gate: >10 MB fails BEFORE any staging/upload.
        if data.count > AttachmentRules.maxBytes {
            drafts.append(AttachmentTransitions.failed(draft, with: .tooLarge))
            sources[draft.id] = .data(data)
            return
        }

        sources[draft.id] = .data(data)
        drafts.append(draft)
        beginPrepare(id: draft.id)
    }

    /// Document picked through .fileImporter — a security-scoped URL. The
    /// store copies it into app-private staging immediately (picker grants
    /// expire; spec §4/§9).
    func addFileURL(_ url: URL) {
        guard remainingSlots > 0 else { return }
        let rawName = url.lastPathComponent
        let mime = Self.resolvedMIME(forFileAt: url)
            ?? AttachmentRules.mimeForFileName(rawName)
            ?? "application/octet-stream"

        let name = AttachmentRules.sanitizeDisplayName(rawName)
        let base = AttachmentDraft(
            id: UUID().uuidString,
            kind: AttachmentRules.kindForMime(mime) ?? .document,
            source: .files,
            displayName: name,
            mimeType: mime,
            phase: .selected,
            createdAt: ConversationStore.now())

        guard let kind = AttachmentRules.kindForMime(mime),
              AttachmentRules.allowedMIMETypes.contains(mime) else {
            // The importer allowed the type (UTType conformance) but it is not
            // in the allowlist — an honest failed chip, never a silent drop.
            drafts.append(AttachmentTransitions.failed(base, with: .unsupported))
            return
        }

        sources[base.id] = .fileURL(url)
        drafts.append(base)
        beginPrepare(id: base.id)
    }

    /// Camera capture: UIImagePickerController hands back the original
    /// image; the sheet compressed it to JPEG 0.9 — staged like gallery bytes.
    func addCameraCapture(_ data: Data) {
        addImageData(
            data,
            mimeType: "image/jpeg",
            source: .camera,
            suggestedName: "camera-\(Int(Date().timeIntervalSince1970)).jpg")
    }

    /// Display name when the picker has none (PhotosPicker items carry no
    /// filename): readable, timestamped, extension-matched to the MIME.
    nonisolated private static func defaultDisplayName(kind: AttachmentKind, mimeType: String) -> String {
        let stamp = Int(Date().timeIntervalSince1970)
        let ext = AttachmentRules.preferredExtension(forMime: mimeType)
        switch kind {
        case .image: return "photo-\(stamp).\(ext)"
        case .pdf: return "document-\(stamp).pdf"
        case .document: return "note-\(stamp).\(ext)"
        }
    }

    /// UTType-derived MIME from the file's resource metadata (iOS 14+).
    nonisolated private static func resolvedMIME(forFileAt url: URL) -> String? {
        let values = try? url.resourceValues(forKeys: [.contentTypeKey])
        let mime = values?.contentType?.preferredMIMEType?.lowercased()
        return mime
    }

    // MARK: Staging (selected → preparing → uploading)

    private func beginPrepare(id: String) {
        guard let index = drafts.firstIndex(where: { $0.id == id }) else { return }
        drafts[index] = AttachmentTransitions.beginningPrepare(drafts[index])

        let payload = sources[id]
        let directory = Self.stagingDirectory(for: id)
        let displayName = drafts[index].displayName

        Task { [weak self] in
            let outcome = await Task.detached(priority: .userInitiated) { () -> StageOutcome in
                AttachmentStore.stageSync(
                    payload: payload,
                    directory: directory,
                    displayName: displayName)
            }.value
            self?.finishPrepare(id: id, outcome: outcome)
        }
    }

    private enum StageOutcome {
        case staged(byteSize: Int, path: String)
        case failure(AttachmentFailure)
    }

    /// The real copy — always off-main. byteSize comes from the staged file
    /// (the truth), never from picker metadata.
    nonisolated private static func stageSync(
        payload: SourcePayload?,
        directory: URL,
        displayName: String
    ) -> StageOutcome {
        let fileManager = FileManager.default
        do {
            try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
            let target = directory.appendingPathComponent(displayName)
            if fileManager.fileExists(atPath: target.path) {
                try fileManager.removeItem(at: target)
            }
            let bytes: Data
            switch payload {
            case .data(let data):
                bytes = data
            case .fileURL(let source):
                let scoped = source.startAccessingSecurityScopedResource()
                defer { if scoped { source.stopAccessingSecurityScopedResource() } }
                do {
                    bytes = try Data(contentsOf: source)
                } catch {
                    return .failure(.readFailed)
                }
            case nil:
                return .failure(.readFailed)
            }
            guard !bytes.isEmpty else { return .failure(.readFailed) }
            try bytes.write(to: target, options: .atomic)
            let attrs = try? fileManager.attributesOfItem(atPath: target.path)
            let size = (attrs?[.size] as? NSNumber)?.intValue ?? bytes.count
            return .staged(byteSize: size, path: target.path)
        } catch {
            return .failure(.readFailed)
        }
    }

    private func finishPrepare(id: String, outcome: StageOutcome) {
        guard let index = drafts.firstIndex(where: { $0.id == id }) else { return }
        switch outcome {
        case .failure(let failure):
            drafts[index] = AttachmentTransitions.failed(drafts[index], with: failure)
        case .staged(let byteSize, let path):
            // Server is the authority — this pre-flight only fails fast.
            if let failure = AttachmentTransitions.validate(byteSize: byteSize, mimeType: drafts[index].mimeType) {
                drafts[index] = AttachmentTransitions.failed(
                    AttachmentTransitions.stagedForUpload(drafts[index], path: path, byteSize: byteSize),
                    with: failure)
                return
            }
            drafts[index] = AttachmentTransitions.stagedForUpload(
                drafts[index], path: path, byteSize: byteSize)
            beginUpload(id: id)
        }
    }

    // MARK: Upload (preparing → uploading → ready | failed)

    private func beginUpload(id: String) {
        guard let index = drafts.firstIndex(where: { $0.id == id }) else { return }
        let draft = drafts[index]
        guard let path = draft.localPath, !path.isEmpty else {
            drafts[index] = AttachmentTransitions.failed(draft, with: .readFailed)
            return
        }

        // Fresh UUID boundary per upload (RFC 7578).
        let boundary = "gs.boundary." + UUID().uuidString
        let url = Self.uploadsURL()
        let displayName = draft.displayName
        let mimeType = draft.mimeType

        Task { [weak self] in
            let outcome = await Task.detached(priority: .userInitiated) { () -> UploadOutcome in
                await AttachmentStore.uploadSync(
                    url: url,
                    boundary: boundary,
                    displayName: displayName,
                    mimeType: mimeType,
                    stagedPath: path)
            }.value
            self?.finishUpload(id: id, outcome: outcome)
        }
    }

    private enum UploadOutcome {
        case uploaded(Attachment)
        case failure(AttachmentFailure)
    }

    /// Real URLSession multipart POST to /api/v1/uploads. Status codes map
    /// onto the honest taxonomy: 413 too_large, 415 unsupported,
    /// 429/other → server(code), transport errors → network.
    nonisolated private static func uploadSync(
        url: URL,
        boundary: String,
        displayName: String,
        mimeType: String,
        stagedPath: String
    ) async -> UploadOutcome {
        guard let bytes = try? Data(contentsOf: URL(fileURLWithPath: stagedPath)),
              !bytes.isEmpty else {
            return .failure(.readFailed)
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 120
        request.setValue(APIClient.sessionID, forHTTPHeaderField: APIClient.sessionHeaderName)
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        request.httpBody = MultipartFormData.body(
            boundary: boundary,
            field: "file",
            filename: displayName,
            mimeType: mimeType,
            data: bytes,
            extraFields: ["displayName": displayName])

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse else { return .failure(.network) }
            switch http.statusCode {
            case 200..<300:
                guard let envelope = try? JSONDecoder().decode(UploadEnvelope.self, from: data),
                      !envelope.attachment.id.isEmpty else {
                    return .failure(.server(http.statusCode))
                }
                return .uploaded(envelope.attachment)
            case 413: return .failure(.tooLarge)
            case 415: return .failure(.unsupported)
            default: return .failure(.server(http.statusCode))
            }
        } catch {
            return .failure(.network)
        }
    }

    private func finishUpload(id: String, outcome: UploadOutcome) {
        guard let index = drafts.firstIndex(where: { $0.id == id }) else { return }
        switch outcome {
        case .uploaded(let remote):
            let ready = AttachmentTransitions.uploaded(drafts[index], remote: remote)
            drafts[index] = ready
            if let serverID = ready.serverID, let path = ready.localPath {
                stagedByRemoteID[serverID] = path
            }
            sources[id] = nil // uploaded — no further prepare retries
        case .failure(let failure):
            drafts[index] = AttachmentTransitions.failed(drafts[index], with: failure)
        }
    }

    // MARK: Retry / remove / clear

    /// Re-runs ONLY the failed step (prepare OR upload) — never re-picks.
    func retry(id: String) {
        guard let index = drafts.firstIndex(where: { $0.id == id }) else { return }
        guard drafts[index].phase == .failed else { return }
        let draft = drafts[index]
        switch AttachmentTransitions.retryStep(for: draft) {
        case .upload where draft.localPath.map({ FileManager.default.fileExists(atPath: $0) }) == true:
            // The staged copy survived — the retry goes straight back to the
            // failed upload step.
            var next = draft
            next.phase = .uploading
            next.error = nil
            drafts[index] = next
            beginUpload(id: id)
        case .upload, .prepare:
            // Nothing usable staged (or the staged copy vanished): the retry
            // is an honest re-run of staging from the session-held source.
            drafts[index] = AttachmentTransitions.beginningPrepare(draft)
            beginPrepare(id: id)
        case .none:
            break
        }
    }

    /// Chip ×. A never-uploaded draft loses its staged copy (draft-private);
    /// an uploaded one keeps it (the transcript thumbnail uses it) — removal
    /// is purely a draft operation then (spec §4).
    func remove(id: String) {
        guard let index = drafts.firstIndex(where: { $0.id == id }) else { return }
        let draft = drafts[index]
        if draft.remoteURL == nil, let path = draft.localPath {
            let directory = URL(fileURLWithPath: path).deletingLastPathComponent()
            try? FileManager.default.removeItem(at: directory)
        }
        sources[id] = nil
        drafts.remove(at: index)
    }

    /// Successful send: drafts leave the composer, staged copies STAY (they
    /// back the transcript thumbnails; staged files persist until the
    /// conversation is deleted — spec §4).
    func clearSent() {
        drafts = []
        sources = [:]
    }

    // MARK: Draft restore (relaunch)

    /// Rehydrates persisted drafts. Only ready/failed states survive a
    /// relaunch (mid-flight states are not truth); each must still have its
    /// staged copy on disk — missing files are dropped, not faked.
    func restoreDrafts(_ restored: [AttachmentDraft]) {
        guard drafts.isEmpty, !restored.isEmpty else { return }
        var live: [AttachmentDraft] = []
        for draft in restored {
            guard draft.phase == .ready || draft.phase == .failed else { continue }
            guard let path = draft.localPath,
                  !path.isEmpty,
                  FileManager.default.fileExists(atPath: path) else { continue }
            if let serverID = draft.serverID {
                stagedByRemoteID[serverID] = path
            }
            live.append(draft)
        }
        guard !live.isEmpty else { return }
        // A restored set can exceed the per-message cap only through old or
        // foreign data — truncate honestly to the cap, keeping the first
        // picks in pick order.
        if live.count > AttachmentRules.maxPerMessage {
            live = Array(live.prefix(AttachmentRules.maxPerMessage))
        }
        drafts = live
    }

    // MARK: Privacy

    /// Settings → Clear local data: every staged copy leaves the device.
    func purgeAllStagedFiles() {
        drafts = []
        sources = [:]
        stagedByRemoteID = [:]
        try? FileManager.default.removeItem(at: Self.attachmentsRoot())
    }
}
