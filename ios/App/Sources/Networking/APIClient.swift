import Foundation

/// Errors surfaced by `APIClient`, each with a human-readable description.
enum APIError: LocalizedError {
    case invalidURL
    case http(status: Int, body: String)
    case server(String)
    case badResponse(String)

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "The request URL is invalid."
        case .http(let status, let body):
            let snippet = body.trimmingCharacters(in: .whitespacesAndNewlines)
            return snippet.isEmpty ? "Request failed (HTTP \(status))." : "Request failed (HTTP \(status)). \(snippet)"
        case .server(let message):
            return message
        case .badResponse(let detail):
            return "Unexpected response: \(detail)"
        }
    }
}

/// Minimal REST + SSE client for the GS AI platform API
/// (contract: shared-contracts/openapi.yaml). No external dependencies —
/// plain `URLSession`, including the async `bytes(for:)` streaming API
/// (iOS 15+, safe for the iOS 16.0 deployment target).
final class APIClient {

    static let shared = APIClient()

    var baseURL = URL(string: "http://localhost:3000")!

    private let session: URLSession = .shared
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    private init() {}

    // MARK: - Conversations

    /// GET /api/v1/conversations?limit=N — newest first.
    func conversations(limit: Int = 30) async throws -> [Conversation] {
        let request = try buildRequest(path: "/api/v1/conversations?limit=\(limit)")
        let data = try await validatedData(for: request)
        return try decode(ConversationListEnvelope.self, from: data).items
    }

    /// POST /api/v1/conversations — returns the created conversation (201).
    func createConversation(title: String?) async throws -> Conversation {
        let request = try buildRequest(
            path: "/api/v1/conversations",
            method: "POST",
            body: try encoded(CreateConversationRequest(title: title))
        )
        let data = try await validatedData(for: request)
        return try decode(Conversation.self, from: data)
    }

    /// GET /api/v1/conversations/{id}/messages — oldest first.
    func messages(conversationID: String) async throws -> [Message] {
        let request = try buildRequest(path: "/api/v1/conversations/\(Self.escaped(conversationID))/messages")
        let data = try await validatedData(for: request)
        return try decode(MessageListEnvelope.self, from: data).items
    }

    /// DELETE /api/v1/conversations/{id} — expects 2xx (usually 204).
    func deleteConversation(id: String) async throws {
        let request = try buildRequest(path: "/api/v1/conversations/\(Self.escaped(id))", method: "DELETE")
        _ = try await validatedData(for: request)
    }

    /// PATCH /api/v1/conversations/{id} — pin / archive / rename echo.
    /// The UI already applied the change locally; this is the sync step.
    @discardableResult
    func updateConversation(
        id: String,
        pinned: Bool? = nil,
        archived: Bool? = nil,
        title: String? = nil
    ) async throws -> Bool {
        var patch = UpdateConversationRequest()
        patch.pinned = pinned
        patch.archived = archived
        patch.title = title
        let request = try buildRequest(
            path: "/api/v1/conversations/\(Self.escaped(id))",
            method: "PATCH",
            body: try encoded(patch))
        let data = try await validatedData(for: request)
        return (try? decoder.decode(Conversation.self, from: data)) != nil || data.isEmpty
    }

    // MARK: - Model catalogue

    /// GET /api/v1/models
    func models() async throws -> [ModelEntry] {
        let request = try buildRequest(path: "/api/v1/models")
        let data = try await validatedData(for: request)
        return try decode(ModelListEnvelope.self, from: data).models
    }

    // MARK: - Streaming (SSE)

    /// POSTs a user message with `stream: true` and consumes the
    /// `text/event-stream` reply.
    ///
    /// Frame format (one JSON object per `data:` line):
    ///   `data: {"event":"delta","data":"…chunk…"}`      → `onDelta`
    ///   `data: {"event":"done","data":"{…Message json…}"}` → `onDone`, stream ends
    ///   `data: {"event":"error","data":"…"}`            → `onError`, then throws
    ///
    /// Cancellation: cancelling the surrounding `Task` throws
    /// `CancellationError` (or `URLError.cancelled`) and tears the
    /// connection down — this is what powers the "Stop generating" button.
    func stream(
        message content: String,
        conversationID: String,
        modelId: String? = nil,
        attachmentIDs: [String]? = nil,
        onDelta: @escaping (String) -> Void,
        onDone: @escaping (Message?) -> Void,
        onError: @escaping (Error) -> Void = { _ in }
    ) async throws {
        var request = try buildRequest(
            path: "/api/v1/conversations/\(Self.escaped(conversationID))/messages",
            method: "POST",
            body: try encoded(SendMessageRequest(
                content: content,
                stream: true,
                modelId: modelId,
                attachments: attachmentIDs))
        )
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")

        let (bytes, response) = try await session.bytes(for: request)

        // Non-2xx → the body is a JSON error envelope, not an event stream.
        if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            var buffer: [UInt8] = []
            for try await byte in bytes {
                buffer.append(byte)
                if buffer.count > 2048 { break }
            }
            let body = Data(buffer)
            if let envelope = try? decoder.decode(APIErrorEnvelope.self, from: body) {
                throw APIError.server(envelope.message)
            }
            throw APIError.http(status: http.statusCode, body: String(decoding: body, as: UTF8.self))
        }

        for try await line in bytes.lines {
            guard line.hasPrefix("data:") else { continue }
            let payload = line.dropFirst("data:".count).trimmingCharacters(in: .whitespaces)
            guard !payload.isEmpty,
                  let event = try? decoder.decode(SseEvent.self, from: Data(payload.utf8)) else { continue }

            switch event.event {
            case "delta":
                onDelta(event.data ?? "")
            case "done":
                let final = event.data.flatMap { try? decoder.decode(Message.self, from: Data($0.utf8)) }
                onDone(final)
                return
            case "error":
                let failure = APIError.server(event.data ?? "The assistant hit an unexpected error.")
                onError(failure)
                throw failure
            default:
                continue // unknown event types are ignored for forward-compat
            }
        }
    }

    // MARK: - Plumbing

    /// Builds a JSON request against `baseURL`. `path` starts with "/" and
    /// may include a query string, e.g. "/api/v1/conversations?limit=30".
    func buildRequest(path: String, method: String = "GET", body: Data? = nil) throws -> URLRequest {
        guard let url = URL(string: path, relativeTo: baseURL) else { throw APIError.invalidURL }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        return request
    }

    private func encoded<T: Encodable>(_ value: T) throws -> Data {
        do { return try encoder.encode(value) }
        catch { throw APIError.badResponse("could not encode \(T.self): \(error.localizedDescription)") }
    }

    private func validatedData(for request: URLRequest) async throws -> Data {
        let (data, response) = try await session.data(for: request)
        if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            if let envelope = try? decoder.decode(APIErrorEnvelope.self, from: data) {
                throw APIError.server(envelope.message)
            }
            throw APIError.http(status: http.statusCode, body: String(decoding: data, as: UTF8.self))
        }
        return data
    }

    private func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T {
        do { return try decoder.decode(type, from: data) }
        catch { throw APIError.badResponse("could not parse \(T.self): \(error.localizedDescription)") }
    }

    private static func escaped(_ raw: String) -> String {
        raw.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? raw
    }
}
