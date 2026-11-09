import XCTest
import Foundation
@testable import GSApp

/// Exercise the real URLSession -> APIClient -> ChatViewModel -> SQLite path.
/// The fixture changes the HTTP responses, not response parsing/finalization.
private final class ChatFixtureURLProtocol: URLProtocol {
    typealias Handler = (URLRequest) -> (Int, Data)
    private static let lock = NSLock()
    private static var handlers: [String: Handler] = [:]

    static func install(host: String, handler: @escaping Handler) {
        lock.lock(); defer { lock.unlock() }
        handlers[host] = handler
    }

    static func remove(host: String) {
        lock.lock(); defer { lock.unlock() }
        handlers.removeValue(forKey: host)
    }

    override class func canInit(with request: URLRequest) -> Bool {
        lock.lock(); defer { lock.unlock() }
        return handlers[request.url?.host ?? ""] != nil
    }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.lock.lock()
        let handler = Self.handlers[request.url?.host ?? ""]
        Self.lock.unlock()
        guard let handler, let url = request.url else {
            client?.urlProtocol(self, didFailWithError: URLError(.unsupportedURL))
            return
        }
        let (status, data) = handler(request)
        let response = HTTPURLResponse(url: url, statusCode: status, httpVersion: "HTTP/1.1",
                                       headerFields: ["Content-Type": "text/event-stream; charset=utf-8"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: data)
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}

@MainActor
private final class ActualChatFixture {
    let session: URLSession
    let store: ConversationStore
    let vm: ChatViewModel
    private let host: String
    private let previousLocalPreference: Bool

    init(createReply: ChatFixtureURLProtocol.Handler? = nil, reply: @escaping ChatFixtureURLProtocol.Handler) throws {
        let fixtureHost = UUID().uuidString.lowercased() + ".gs-fixture.invalid"
        host = fixtureHost
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [ChatFixtureURLProtocol.self]
        let fixtureSession = URLSession(configuration: configuration)
        session = fixtureSession
        let client = APIClient(session: fixtureSession)
        client.baseURL = try XCTUnwrap(URL(string: "https://" + fixtureHost))
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("gs-response-" + UUID().uuidString)
        // OS-managed fixture files: never unlink a live WAL database.
        let sql = SQLiteChatStore(databaseURL: directory.appendingPathComponent("history.sqlite"))
        XCTAssertTrue(sql.isAvailable)
        let fixtureStore = ConversationStore(sql: sql)
        store = fixtureStore
        vm = ChatViewModel(conversationID: nil, apiClient: client, store: fixtureStore)
        previousLocalPreference = SettingsStore.shared.preferLocal
        SettingsStore.shared.preferLocal = false
        let id = "actual-fixture-" + UUID().uuidString
        let conversation = try JSONSerialization.data(withJSONObject: ["id": id, "title": "Actual request"])
        ChatFixtureURLProtocol.install(host: host) { request in
            if request.url?.path == "/api/v1/conversations" {
                return createReply?(request) ?? (201, conversation)
            }
            return reply(request)
        }
    }

    func finish(file: StaticString = #filePath, line: UInt = #line) async throws {
        let deadline = Date().addingTimeInterval(10)
        while vm.isStreaming && Date() < deadline {
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTAssertFalse(vm.isStreaming, "Actual transport did not finish", file: file, line: line)
    }

    func close() {
        if vm.isStreaming { vm.stop() }
        session.invalidateAndCancel()
        ChatFixtureURLProtocol.remove(host: host)
        SettingsStore.shared.preferLocal = previousLocalPreference
    }
}

private final class ActualChatRequestLog {
    private let lock = NSLock()
    private var recorded: [String] = []
    func record(_ request: URLRequest) -> Int {
        lock.lock(); defer { lock.unlock() }
        recorded.append(request.url?.path ?? "")
        return recorded.count
    }
    var paths: [String] {
        lock.lock(); defer { lock.unlock() }
        return recorded
    }
}

final class ChatResponseHonestyTests: XCTestCase {
    private func event(_ name: String, _ data: String) throws -> Data {
        let json = try JSONSerialization.data(withJSONObject: ["event": name, "data": data])
        return Data(("data: " + String(decoding: json, as: UTF8.self) + "\n\n").utf8)
    }

    @MainActor
    func testUnusualPromptFailureNeverBecomesKeywordSelectedCannedAnswer() async throws {
        let prompt = "Write a Bengali evacuation checklist for a Feni household with a wheelchair user; do not give marketing advice."
        let fixture = try ActualChatFixture { _ in (503, Data("{\"message\":\"private upstream detail\"}".utf8)) }
        defer { fixture.close() }
        fixture.vm.draft = prompt
        fixture.vm.send()
        try await fixture.finish()
        XCTAssertEqual(fixture.vm.messages.map(\.content), [prompt])
        XCTAssertEqual(fixture.vm.messages.map(\.role), ["user"])
        XCTAssertNotNil(fixture.vm.errorMessage)
        XCTAssertFalse(fixture.vm.errorMessage?.contains("private upstream detail") ?? true)
        let id = try XCTUnwrap(fixture.vm.conversationID)
        XCTAssertEqual(fixture.store.recentMessages(for: id, limit: 20).filter { $0.role == "assistant" }.count, 0)
    }

    @MainActor
    func testCanonicalDoneReplacesDraftAndKeepsRealServerIdentityBeforeFinalization() async throws {
        let canonical = "Actual answer: a source-defined interactive Bengali evacuation checklist."
        let message = try JSONSerialization.data(withJSONObject: ["id": "actual-server-answer", "conversationId": "fixture", "role": "assistant", "content": canonical, "sources": [["id": "real-source", "ordinal": 1, "title": "Source", "url": "https://example.org/actual", "domain": "example.org", "snippet": "Actual evidence"]]])
        var stream = try event("delta", "Unfinished draft that must not survive completion")
        stream.append(try event("done", String(decoding: message, as: UTF8.self)))
        let response = stream
        let fixture = try ActualChatFixture { _ in (200, response) }
        defer { fixture.close() }
        fixture.vm.draft = "Make a tailored evacuation checklist, not a generic project plan."
        fixture.vm.send()
        try await fixture.finish()
        let answer = try XCTUnwrap(fixture.vm.messages.last)
        XCTAssertEqual(answer.content, canonical)
        XCTAssertEqual(answer.serverId, "actual-server-answer")
        XCTAssertEqual(answer.sources?.first?.id, "real-source")
        XCTAssertEqual(answer.sources?.first?.url, "https://example.org/actual")
        XCTAssertEqual(answer.reactPreviewIdentity, "actual-server-answer")
        XCTAssertNil(fixture.vm.errorMessage)
        let id = try XCTUnwrap(fixture.vm.conversationID)
        let stored = fixture.store.recentMessages(for: id, limit: 20).filter { $0.role == "assistant" }
        XCTAssertEqual(stored.count, 1)
        XCTAssertEqual(stored.first?.id, "actual-server-answer")
        XCTAssertEqual(stored.first?.content, canonical)
    }

    @MainActor
    func testInterruptedReplyPreservesOnlyActualTextAndDoesNotPromiseAutomaticContinuation() async throws {
        let partial = "Actual received instructions, incomplete."
        let response = try event("delta", partial) // TCP EOF without a done event.
        let fixture = try ActualChatFixture { _ in (200, response) }
        defer { fixture.close() }
        fixture.vm.draft = "Give a careful step-by-step repair plan for this unusual device."
        fixture.vm.send()
        try await fixture.finish()
        XCTAssertEqual(fixture.vm.messages.last?.content, partial)
        XCTAssertNil(fixture.vm.messages.last?.serverId)
        XCTAssertTrue(fixture.vm.errorMessage?.contains("interrupted") ?? false)
        XCTAssertFalse(fixture.vm.messages.last?.content.contains("pick the thread") ?? true)
    }

    @MainActor
    func testFailedFirstCreationRetriesCreationAndPersistsOriginalUserOnceWithoutFakeIDs() async throws {
        let log = ActualChatRequestLog()
        let prompt = "Design a custom monsoon evacuation tool for a wheelchair user in Feni."
        let message = try JSONSerialization.data(withJSONObject: ["id": "retry-answer", "conversationId": "created-after-retry", "role": "assistant", "content": "Actual successful retry answer"])
        let response = try event("done", String(decoding: message, as: UTF8.self))
        let fixture = try ActualChatFixture(createReply: { request in
            if log.record(request) == 1 { return (503, Data()) }
            return (201, Data("{\"id\":\"created-after-retry\",\"title\":\"Actual retry\"}".utf8))
        }, reply: { request in
            _ = log.record(request)
            return (200, response)
        })
        defer { fixture.close() }
        let existing = fixture.store.createLocalConversation(title: "Unrelated offline history")
        fixture.store.append(StoredMessage(id: "keep-this-offline-row", conversationId: existing.id, role: "assistant", content: "Actual offline source", createdAt: ConversationStore.now()))
        fixture.vm.draft = prompt
        fixture.vm.send()
        try await fixture.finish()
        XCTAssertNil(fixture.vm.conversationID, "Failed creation must not become a fake backend ID")
        XCTAssertEqual(fixture.vm.messages.map(\.content), [prompt])
        XCTAssertNotNil(fixture.vm.errorMessage)
        XCTAssertEqual(log.paths, ["/api/v1/conversations"])

        fixture.vm.retry()
        try await fixture.finish()
        XCTAssertNil(fixture.vm.errorMessage)
        XCTAssertEqual(fixture.vm.conversationID, "created-after-retry")
        XCTAssertEqual(fixture.vm.messages.filter { $0.role == "user" }.map(\.content), [prompt])
        XCTAssertEqual(fixture.vm.messages.last?.serverId, "retry-answer")
        XCTAssertEqual(log.paths, ["/api/v1/conversations", "/api/v1/conversations", "/api/v1/conversations/created-after-retry/messages"])
        let saved = fixture.store.recentMessages(for: "created-after-retry", limit: 20)
        XCTAssertEqual(saved.filter { $0.role == "user" }.map(\.content), [prompt])
        XCTAssertEqual(saved.filter { $0.role == "assistant" }.count, 1)
        XCTAssertEqual(fixture.store.recentMessages(for: existing.id, limit: 20).first?.content, "Actual offline source")
    }

    @MainActor
    func testEmptyCompletionIsAnErrorNotASuccessfulBlankAnswer() async throws {
        let response = try event("done", "")
        let fixture = try ActualChatFixture { _ in (200, response) }
        defer { fixture.close() }
        fixture.vm.draft = "Generate a custom interactive lesson, not a stock reply."
        fixture.vm.send()
        try await fixture.finish()
        XCTAssertEqual(fixture.vm.messages.count, 1)
        XCTAssertEqual(fixture.vm.messages.first?.role, "user")
        XCTAssertNotNil(fixture.vm.errorMessage)
    }
}
