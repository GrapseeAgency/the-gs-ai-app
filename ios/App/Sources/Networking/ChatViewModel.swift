import SwiftUI
import Combine

/**
 * Owns one conversation: history load (store first, then network), optimistic
 * send, SSE streaming with live accumulation, stop, retry/regenerate. Every
 * turn is persisted to `ConversationStore` so threads survive relaunches, and
 * a dead backend never dead-ends the thread — GS Lite, the on-device responder,
 * streams a local reply instead (never an error screen). All state
 * mutations happen on the main actor; the streaming connection itself runs on
 * URLSession's background plumbing and hops back via `Task { @MainActor in … }`.
 */
/**
 * Lock-guarded delta sink for one stream. SSE chunks arrive on URLSession's
 * background plumbing and append here with no main-actor hop and no
 * allocation per token — the UI reads it at a fixed ~30Hz cadence instead of
 * per chunk (deep-perf pass 80-b). `@unchecked Sendable`: every access is
 * serialized by the lock.
 */
final class StreamAccumulator: @unchecked Sendable {

    private let lock = NSLock()
    private var text = ""

    func append(_ delta: String) {
        lock.lock()
        text += delta
        lock.unlock()
    }

    func snapshot() -> String {
        lock.lock()
        defer { lock.unlock() }
        return text
    }

    var isEmpty: Bool { snapshot().isEmpty }
}

// MARK: - Search/research trace state (PHASE 8.1 + 8.2 v2 — docs/search-event-protocol.md)

/**
 * The REAL search phase of the live turn, driven ONLY by wire events
 * (`status`, `search`, `source`). Nothing may fabricate a phase:
 * `.searching` exists only after a genuine search-start signal,
 * `.working` only while page retrieval is actually in flight,
 * `.composing` on synthesis start. `.idle` = no search signal this turn.
 */
enum ChatSearchPhase: Equatable {
    case idle
    case searching
    case working
    case composing
    case failed
}

/**
 * One engine's outcome from the v2 `search` payload subtype `engines`
 * (per-engine truth). Tolerant decode: any missing field degrades to its
 * default instead of failing the surrounding payload decode.
 */
struct SearchEngineOutcome: Decodable, Equatable {
    var id: String
    /// `ok` | `failed` | … — anything non-`ok` renders as a failure glyph.
    var status: String
    var count: Int?
    var error: String?

    init(id: String = "", status: String = "", count: Int? = nil, error: String? = nil) {
        self.id = id
        self.status = status
        self.count = count
        self.error = error
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(String.self, forKey: .id) ?? ""
        status = try c.decodeIfPresent(String.self, forKey: .status) ?? ""
        count = try c.decodeIfPresent(Int.self, forKey: .count)
        error = try c.decodeIfPresent(String.self, forKey: .error)
    }

    private enum CodingKeys: String, CodingKey {
        case id, status, count, error
    }

    var ok: Bool { status.lowercased() == "ok" }
}

/**
 * One search-trace step, built only from a received `search`/`source`/
 * `research` event (plus the synthesis start from `status`). The protocol's
 * honest-state rule is binding: no timers, no fabricated steps — if the
 * backend never sent it, it never appears.
 */
struct SearchTraceStep: Identifiable, Equatable {

    enum Kind: Equatable {
        case started(label: String?)
        case query(round: Int, text: String, engines: [String])
        /// v2 per-engine truth (`search` payload subtype `engines`).
        case engines(round: Int, outcomes: [SearchEngineOutcome])
        case results(round: Int, found: Int, query: String, engines: [String])
        case discovered(ordinal: Int, title: String, domain: String)
        case reading(ordinal: Int)
        case sourceCompleted(ordinal: Int, chars: Int?)
        /// v2 (`source` payload subtype `evidence`) — extraction really
        /// happened: `windowChars` is the evidence window actually mined.
        case evidence(ordinal: Int, chars: Int?, windowChars: Int?)
        case sourceFailed(ordinal: Int, reason: String)
        case sourceSkipped(ordinal: Int, reason: String)
        /// v2 round summary gains the syndicated-group count.
        case roundSummary(round: Int, verified: Int, failed: Int, syndicated: Int?)
        // v2 `research` event family — research-level truth (§28).
        case researchStarted(depth: String?, roundsPlanned: Int?)
        case researchRound(round: Int, found: Int, read: Int, failed: Int, syndicated: Int?)
        case researchFailed(reason: String)
        case researchCancelled(by: String?)
        case searchFailed(reason: String)
        /// v2: carries the bound citations (`usedCitations`) when the wire
        /// sent them.
        case completedSummary(queries: Int, sources: Int, retrieved: Int, citations: [Int]?)
        case composing
    }

    let id = UUID()
    let kind: Kind

    /// The one-line summary the trace collapses to after `done`, computed
    /// ONLY from the steps that actually arrived (nil = no search ran).
    static func collapsedSummary(_ steps: [SearchTraceStep]) -> String? {
        guard !steps.isEmpty else { return nil }
        for step in steps.reversed() {
            if case .completedSummary(let queries, let sources, let retrieved, _) = step.kind {
                return "Searched \(plural(queries, "query")) \u{00B7} \(plural(sources, "source")) \u{00B7} \(plural(retrieved, "page")) read"
            }
            if case .searchFailed(let reason) = step.kind {
                let clean = reason.replacingOccurrences(of: "_", with: " ")
                return clean.isEmpty ? "Search failed" : "Search failed \u{2014} \(clean)"
            }
            if case .researchFailed(let reason) = step.kind {
                let clean = reason.replacingOccurrences(of: "_", with: " ")
                return clean.isEmpty ? "Research failed" : "Research failed \u{2014} \(clean)"
            }
        }
        let queries = steps.reduce(0) { count, step in
            if case .query = step.kind { return count + 1 }
            return count
        }
        let read = steps.reduce(0) { count, step in
            if case .sourceCompleted = step.kind { return count + 1 }
            return count
        }
        if queries > 0 {
            var line = "Searched \(plural(queries, "query"))"
            if read > 0 { line += " \u{00B7} \(plural(read, "source")) read" }
            return line
        }
        return "Searched"
    }

    private static func plural(_ n: Int, _ word: String) -> String {
        n == 1 ? "1 \(word)" : "\(n) \(word)s"
    }
}

/**
 * Lock-guarded SSE sink for the search/research-trace events of one stream —
 * the state twin of `StreamAccumulator`. `status`/`search`/`source`/
 * `clarify`/`research` frames append here off-main with no main-actor hop;
 * the ~30Hz flush loop drains it in arrival order on the main actor (and
 * finalize drains the tail before closing), so trace order on screen is
 * exactly wire order. `@unchecked Sendable`: every access is serialized by
 * the lock.
 */
final class SearchEventSink: @unchecked Sendable {

    private let lock = NSLock()
    private var events: [SseEvent] = []

    func append(_ event: SseEvent) {
        lock.lock()
        events.append(event)
        lock.unlock()
    }

    func drain() -> [SseEvent] {
        lock.lock()
        defer { lock.unlock() }
        let out = events
        events = []
        return out
    }
}

// MARK: - Wire payload decodes (PHASE 8.1 + 8.2 v2)

/// Tolerant decode of the double-encoded `search` event payload — every
/// field beyond the discriminator is optional, so an unknown/renamed field
/// degrades instead of failing the whole event.
private struct SearchEventPayload: Decodable {
    var type: String = ""
    var intent: String?
    var depth: String?
    var label: String?
    var round: Int?
    var query: String?
    var engines: [String]?
    /// v2 `engines` subtype: per-engine outcome objects. The same wire key
    /// carries name arrays on query/results — the decode is polymorphic.
    var engineOutcomes: [SearchEngineOutcome]?
    var found: Int?
    var sourcesVerified: Int?
    var sourcesFailed: Int?
    var verified: Bool?
    var syndicatedGroups: Int?
    var reason: String?
    var queries: Int?
    var sources: Int?
    var retrieved: Int?
    var usedCitations: [Int]?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        type = try c.decodeIfPresent(String.self, forKey: .type) ?? ""
        intent = try c.decodeIfPresent(String.self, forKey: .intent)
        depth = try c.decodeIfPresent(String.self, forKey: .depth)
        label = try c.decodeIfPresent(String.self, forKey: .label)
        round = try c.decodeIfPresent(Int.self, forKey: .round)
        query = try c.decodeIfPresent(String.self, forKey: .query)
        // `engines` is polymorphic on the wire: per-engine objects on the
        // engines subtype, plain id strings on query/results. Try the object
        // shape first, fall back to the names shape; anything else degrades
        // to nil (tolerant — never fails the whole event).
        if let outcomes = try? c.decodeIfPresent([SearchEngineOutcome].self, forKey: .engines),
           !outcomes.isEmpty {
            engineOutcomes = outcomes
        } else {
            engines = try? c.decodeIfPresent([String].self, forKey: .engines)
        }
        found = try c.decodeIfPresent(Int.self, forKey: .found)
        sourcesVerified = try c.decodeIfPresent(Int.self, forKey: .sourcesVerified)
        sourcesFailed = try c.decodeIfPresent(Int.self, forKey: .sourcesFailed)
        verified = try c.decodeIfPresent(Bool.self, forKey: .verified)
        syndicatedGroups = try c.decodeIfPresent(Int.self, forKey: .syndicatedGroups)
        reason = try c.decodeIfPresent(String.self, forKey: .reason)
        queries = try c.decodeIfPresent(Int.self, forKey: .queries)
        sources = try c.decodeIfPresent(Int.self, forKey: .sources)
        retrieved = try c.decodeIfPresent(Int.self, forKey: .retrieved)
        usedCitations = try c.decodeIfPresent([Int].self, forKey: .usedCitations)
    }
}

/// Tolerant decode of the double-encoded `source` event payload. The v2
/// subtypes `read` (canonical twin of v1 `completed`) and `evidence` ride
/// the same fields.
private struct SourceEventPayload: Decodable {
    var type: String = ""
    var ordinal: Int?
    var chars: Int?
    var windowChars: Int?
    var publishedDate: String?
    var reason: String?
    var status: String?
    var source: MessageSource?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        type = try c.decodeIfPresent(String.self, forKey: .type) ?? ""
        ordinal = try c.decodeIfPresent(Int.self, forKey: .ordinal)
        chars = try c.decodeIfPresent(Int.self, forKey: .chars)
        windowChars = try c.decodeIfPresent(Int.self, forKey: .windowChars)
        publishedDate = try c.decodeIfPresent(String.self, forKey: .publishedDate)
        reason = try c.decodeIfPresent(String.self, forKey: .reason)
        status = try c.decodeIfPresent(String.self, forKey: .status)
        source = try c.decodeIfPresent(MessageSource.self, forKey: .source)
    }
}

/// Tolerant decode of the double-encoded `research` event payload (PHASE 8.2
/// v2 — research-level truth). Unknown fields are ignored by construction;
/// an unknown `type` falls through `applyResearchEvent`'s default arm.
private struct ResearchEventPayload: Decodable {
    var type: String = ""
    var depth: String?
    var roundsPlanned: Int?
    var round: Int?
    var found: Int?
    var read: Int?
    var failed: Int?
    var syndicatedGroups: Int?
    var queries: Int?
    var sources: Int?
    var retrieved: Int?
    var usedCitations: [Int]?
    var reason: String?
    var message: String?
    var by: String?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        type = try c.decodeIfPresent(String.self, forKey: .type) ?? ""
        depth = try c.decodeIfPresent(String.self, forKey: .depth)
        roundsPlanned = try c.decodeIfPresent(Int.self, forKey: .roundsPlanned)
        round = try c.decodeIfPresent(Int.self, forKey: .round)
        found = try c.decodeIfPresent(Int.self, forKey: .found)
        read = try c.decodeIfPresent(Int.self, forKey: .read)
        failed = try c.decodeIfPresent(Int.self, forKey: .failed)
        syndicatedGroups = try c.decodeIfPresent(Int.self, forKey: .syndicatedGroups)
        queries = try c.decodeIfPresent(Int.self, forKey: .queries)
        sources = try c.decodeIfPresent(Int.self, forKey: .sources)
        retrieved = try c.decodeIfPresent(Int.self, forKey: .retrieved)
        usedCitations = try c.decodeIfPresent([Int].self, forKey: .usedCitations)
        reason = try c.decodeIfPresent(String.self, forKey: .reason)
        message = try c.decodeIfPresent(String.self, forKey: .message)
        by = try c.decodeIfPresent(String.self, forKey: .by)
    }
}

@MainActor
final class ChatViewModel: ObservableObject {

    // MARK: - Types

    struct ChatMessage: Identifiable, Equatable {
        let id = UUID()
        let role: String
        var content: String
        var isStreaming: Bool = false
        var createdAt: String = ""
        /// PHASE 5: server attachment records on user turns (max 6, docs/
        /// ATTACHMENTS.md §7) — rendered as read-only chips above the text.
        /// Assistant turns carry none today; nil = none (tolerant decode).
        var attachments: [Attachment]? = nil
        /// PHASE 8.1: search sources riding the turn — live ones from
        /// `source` events while streaming, the done payload's persisted
        /// rows after finalize, remote history rows on reload. nil = none.
        var sources: [MessageSource]? = nil
        /// PHASE 8.1: on clarify turns the persisted clarify payload (JSON
        /// string, protocol shape) so the quick-choice chips re-render.
        var clarifyOptions: String? = nil
        /// PHASE 8.1: the one-line search-trace summary the card collapses
        /// to after the turn closes ("Searched 2 queries · 5 sources · 4
        /// read"). Computed only from steps that really arrived.
        var traceSummary: String? = nil
        /// PHASE 8.2: the turn's full trace steps — MEMORY ONLY (attached at
        /// finalize, never persisted to the local store). The research-
        /// details audit reads them until the conversation leaves memory;
        /// a reloaded thread re-derives the audit from persisted `sources[]`
        /// per the protocol's research-details rule. nil after reload.
        var traceSteps: [SearchTraceStep]? = nil
    }

    // MARK: - Published state

    @Published var messages: [ChatMessage] = []
    @Published var draft: String = ""
    @Published var isStreaming = false
    @Published var isLoadingHistory = false
    @Published var errorMessage: String?
    @Published var conversationID: String?
    /// Deep-perf pass #2b: the transcript is a newest-window — true while the
    /// store still holds turns above the loaded page. Scrolling up prepends
    /// pages locally (never the network); the view restores the scroll anchor.
    @Published private(set) var hasOlder = false

    // PHASE 8.1 — search trace, fed ONLY by real SSE events (honest states).

    /// Every trace step that actually arrived this turn, in wire order.
    @Published private(set) var traceSteps: [SearchTraceStep] = []
    /// Live source cards built from `source` events (upserted by ordinal);
    /// after finalize the turn renders the done payload's persisted rows.
    @Published private(set) var liveSources: [MessageSource] = []
    /// The clarify prompt while the clarify turn streams; on finalize it
    /// moves onto the message (clarifyOptions) and clears here.
    @Published private(set) var clarifyPrompt: ClarifyPrompt?
    /// The honest orb phase of the live turn (searching/working/…).
    @Published private(set) var searchPhase: ChatSearchPhase = .idle

    // MARK: - Private state

    private var streamTask: Task<Void, Never>?
    private var lastSentText: String?

    /// Growing live text of the current stream. Deltas append off-main; the
    /// flush task publishes to the transcript at display cadence, so a
    /// per-token published storm (whole-screen re-evaluation per chunk) is
    /// gone — one mutation per flush, committed to the store once at finalize.
    private var streamBuffer = StreamAccumulator()
    private var flushTask: Task<Void, Never>?

    /// PHASE 8.1: search-trace events of the current stream queue here
    /// off-main and drain in wire order inside the flush loop / finalize.
    private var streamSink = SearchEventSink()

    /// Page size of the newest-window history load (Android's HISTORY_PAGE).
    private let historyPageSize = 60
    /// `createdAt` of the oldest loaded turn — the scroll-up page boundary.
    private var oldestStamp: String?
    /// Pagination arms only on a real user drag, so the programmatic
    /// bottom-landing on open can never trigger a prepend (Android parity).
    private var olderArmed = false

    // MARK: - Init

    init(conversationID: String?) {
        self.conversationID = conversationID
        if let conversationID {
            loadHistory(conversationID: conversationID)
        }
    }

    deinit {
        streamTask?.cancel()
        flushTask?.cancel()
    }

    // MARK: - Intents

    /// PHASE 5: `attachments` (every draft `ready` — the caller gates on
    /// AttachmentStore) travel as their uploaded server ids; the optimistic
    /// user bubble renders the chips immediately. Text may be empty ONLY
    /// when attachments are present (server rule, mirrored here).
    func send(attachments: [Attachment] = []) {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty || !attachments.isEmpty, !isStreaming else { return }
        draft = ""
        // Attachments-only turns are not regenerable: the ids are claimed by
        // the sent message server-side, so retry/regenerate stays text-only.
        lastSentText = text.isEmpty ? nil : text
        beginStreaming(text: text, appendUserMessage: true, attachments: attachments.isEmpty ? nil : attachments)
    }

    /// PHASE 8.1 clarify quick-choice: the tapped label travels as a normal
    /// user message through the exact pipeline a typed send uses — the
    /// composer draft is untouched (the chip's text never lands in the field).
    func sendClarifyChoice(_ label: String) {
        let text = label.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !isStreaming else { return }
        lastSentText = text
        beginStreaming(text: text, appendUserMessage: true)
    }

    /// Resends the last user text after a failure (also backs regenerate).
    func retry() {
        guard !isStreaming, let text = lastSentText else { return }
        if let last = messages.last, last.role != "user" {
            messages.removeLast() // drop the failed/partial assistant row
        }
        beginStreaming(text: text, appendUserMessage: false)
    }

    func regenerate() {
        retry()
    }

    /// Benchmark edit flow: replace a sent user turn — the thread tail is
    /// dropped (in memory and in the store) and the edited text streams a
    /// fresh reply through the normal pipeline.
    func editAndResend(at index: Int, newText: String) {
        let text = newText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !isStreaming else { return }
        guard messages.indices.contains(index), messages[index].role == "user" else { return }
        let original = messages[index].content
        let conversation = conversationID
        messages.removeSubrange(index...)
        if let conversation, !conversation.hasPrefix("demo-") {
            ConversationStore.shared.truncateMessages(fromUserContent: original, in: conversation)
        }
        lastSentText = text
        beginStreaming(text: text, appendUserMessage: true)
    }

    /// Cancels the in-flight stream; the task throws CancellationError and
    /// the partial assistant text is kept on screen.
    func stop() {
        streamTask?.cancel()
        streamTask = nil
    }

    /// Benchmark branch-new-chat: everything up to and including the tapped
    /// turn becomes the opening history of a fresh local conversation, and
    /// this surface re-bases onto the branch. The original thread is untouched.
    func branch(at index: Int) {
        guard !isStreaming, messages.indices.contains(index) else { return }
        let turns = Array(messages[...index]).filter { !$0.content.isEmpty }
        guard !turns.isEmpty else { return }
        let sourceTitle = conversationID.flatMap {
            ConversationStore.shared.conversation(withID: $0)?.title
        } ?? "New chat"
        let conversation = ConversationStore.shared.createLocalConversation(
            title: String("Branch: \(sourceTitle)".prefix(40)))
        let fallbackStamp = ConversationStore.now()
        for turn in turns {
            ConversationStore.shared.append(StoredMessage(
                id: UUID().uuidString,
                conversationId: conversation.id,
                role: turn.role,
                content: turn.content,
                createdAt: turn.createdAt.isEmpty ? fallbackStamp : turn.createdAt,
                attachments: turn.attachments))
        }
        conversationID = conversation.id
        messages = turns
        // The branch conversation's store holds exactly these turns — no older
        // pages exist, so the window state resets with the re-base.
        oldestStamp = turns.first?.createdAt
        hasOlder = false
    }

    /// Real translation through the chat pipeline, self-cleaning: a throwaway
    /// server conversation carries the prompt and is deleted best-effort after
    /// the stream — nothing touches ConversationStore, recents stay clean.
    /// Returns the streamed text ("" when the backend is unreachable; the UI
    /// stays quiet, deltas still update the sheet live as they arrive).
    func translate(text: String, targetLanguage: String, onDelta: @escaping (String) -> Void) async -> String {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "" }
        let prompt = "Translate the following text into \(targetLanguage). " +
            "Reply with the translation only — no notes, no quotes.\n\n\(trimmed)"
        var scratchID = ""
        defer {
            if !scratchID.isEmpty {
                Task {
                    try? await APIClient.shared.deleteConversation(id: scratchID)
                }
            }
        }
        var accumulated = ""
        do {
            let scratch = try await APIClient.shared.createConversation(title: "Translation")
            scratchID = scratch.id
            try await APIClient.shared.stream(
                message: prompt,
                conversationID: scratch.id,
                onDelta: { delta in
                    Task { @MainActor in
                        accumulated += delta
                        onDelta(delta)
                    }
                },
                onDone: { _ in }
            )
            return accumulated
        } catch {
            return accumulated
        }
    }

    // MARK: - Streaming pipeline

    /// Remote model ids, fetched once per process; nil = unknown/unchecked.
    private static var cachedRemoteModelIDs: [String]?

    /**
     * The preferred model id (persisted by the Model Centre in UserDefaults)
     * only travels when the backend's registry advertises it — otherwise the
     * server default applies silently. Registry fetch failures just mean nil.
     */
    private func resolvePreferredModelID() async -> String? {
        guard let preferred = UserDefaults.standard.string(forKey: "gs.models.defaultId"),
              !preferred.isEmpty else { return nil }
        if let cached = Self.cachedRemoteModelIDs {
            return cached.contains(preferred) ? preferred : nil
        }
        guard let remote = try? await APIClient.shared.models() else { return nil }
        let ids = remote.map { $0.id }
        Self.cachedRemoteModelIDs = ids
        return ids.contains(preferred) ? preferred : nil
    }

    private func beginStreaming(text: String, appendUserMessage: Bool, attachments: [Attachment]? = nil) {
        errorMessage = nil
        // PHASE 8.1: every turn starts with a clean honest trace — no state
        // leaks from the previous turn, nothing exists until an event says so.
        traceSteps = []
        liveSources = []
        clarifyPrompt = nil
        searchPhase = .idle
        streamSink = SearchEventSink()
        if appendUserMessage {
            messages.append(ChatMessage(
                role: "user",
                content: text,
                createdAt: ConversationStore.now(),
                attachments: attachments))
        }
        messages.append(ChatMessage(
            role: "assistant",
            content: "",
            isStreaming: true,
            createdAt: ConversationStore.now()))
        streamBuffer = StreamAccumulator()
        isStreaming = true
        startFlushLoop()

        // Strong self capture is intentional and bounded: the task only lives
        // as long as this stream; `stop()` cancels it, which ends the cycle.
        streamTask = Task {
            do {
                let conversationID = try await self.ensureConversation(for: text)
                let modelID = await self.resolvePreferredModelID()
                if appendUserMessage {
                    ConversationStore.shared.append(StoredMessage(
                        id: UUID().uuidString,
                        conversationId: conversationID,
                        role: "user",
                        content: text,
                        createdAt: ConversationStore.now(),
                        attachments: attachments))
                }
                try await APIClient.shared.stream(
                    message: text,
                    conversationID: conversationID,
                    modelId: modelID,
                    attachmentIDs: attachments?.map { $0.id },
                    onDelta: { [buffer = self.streamBuffer] delta in
                        buffer.append(delta) // lock-guarded — no main hop per token
                    },
                    onDone: { message in
                        Task { @MainActor in
                            self.finalize(with: message)
                        }
                    },
                    onEvent: { [sink = self.streamSink] event in
                        sink.append(event) // lock-guarded — drained in the flush loop
                    }
                )
                // Stream closed without an explicit `done` event — wrap up.
                if self.isStreaming {
                    self.finalizeLocal()
                }
            } catch {
                if Self.isCancellation(error) {
                    self.finalizeLocal()
                } else if !self.streamBuffer.isEmpty {
                    // Partial stream that broke mid-flight: keep what arrived on
                    // screen and disk, close with the same tail Android appends.
                    let tail = "\n\n—I'll pick the thread back up right here."
                    self.streamBuffer.append(tail)
                    self.flushStreamingText()
                    self.finalizeLocal()
                } else {
                    // GS Lite — the on-device responder keeps the conversation
                    // flowing with a streamed local reply. No errors, no
                    // connectivity talk; the turn simply gets answered.
                    await self.streamLocalReply(text)
                    self.finalizeLocal()
                }
            }
        }
    }

    /// A nil conversationID means "first message of a brand-new chat":
    /// create it server-side; offline, fabricate a local row so the thread,
    /// recents and pins keep working until the backend is reachable again.
    /// Task 86-e: the first-message auto-title honours Settings → "Auto-title
    /// chats" — when it is off, the thread keeps the default "New chat" title
    /// (the same default the store materialises for non-user turns) until it
    /// is renamed.
    private func ensureConversation(for text: String) async throws -> String {
        if let conversationID { return conversationID }
        // Attachments-only first messages (empty text) keep the default title.
        let title = SettingsStore.shared.autoTitle && !text.isEmpty ? String(text.prefix(40)) : "New chat"
        do {
            let conversation = try await APIClient.shared.createConversation(title: title)
            conversationID = conversation.id
            return conversation.id
        } catch {
            let local = ConversationStore.shared.createLocalConversation(title: title)
            conversationID = local.id
            return local.id
        }
    }

    // MARK: - Coalesced streaming publish (deep-perf pass 80-b)

    /**
     * Publish loop: reads the accumulator every ~33ms and writes the growing
     * text into the live bubble ONLY when it changed. During a stream this is
     * the single published mutation — the transcript re-evaluates at display
     * cadence instead of once per SSE chunk, and untouched rows skip their
     * bodies via MessageBubble's equality.
     */
    private func startFlushLoop() {
        flushTask?.cancel()
        flushTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 33_000_000)
                guard let self, self.isStreaming else { break }
                self.drainSearchEvents()
                self.flushStreamingText()
            }
        }
    }

    private func stopFlushLoop() {
        flushTask?.cancel()
        flushTask = nil
    }

    private func flushStreamingText() {
        guard let index = messages.indices.last, messages[index].isStreaming else { return }
        let text = streamBuffer.snapshot()
        guard messages[index].content != text else { return }
        messages[index].content = text
    }

    // MARK: - Search event ingest (PHASE 8.1 + 8.2 v2)

    /**
     * Drains the queued search-trace events in wire order and folds them
     * into the honest published state. Runs inside the ~30Hz flush loop and
     * once more in every finalize path, so the trace never loses a tail
     * event between the last flush tick and the turn closing.
     */
    private func drainSearchEvents() {
        for event in streamSink.drain() {
            ingest(event)
        }
    }

    private func ingest(_ event: SseEvent) {
        let data = event.data ?? ""
        switch event.event {
        case "status":
            applyStatus(data)
        case "search":
            guard let payload = try? JSONDecoder().decode(SearchEventPayload.self, from: Data(data.utf8)) else { return }
            applySearchEvent(payload)
        case "source":
            guard let payload = try? JSONDecoder().decode(SourceEventPayload.self, from: Data(data.utf8)) else { return }
            applySourceEvent(payload)
        case "clarify":
            guard let prompt = ClarifyPrompt(jsonString: data) else { return }
            clarifyPrompt = prompt
        case "research":
            // PHASE 8.2 v2: research-level truth rides the same honest
            // pipeline — decode tolerantly, append only what really came.
            guard let payload = try? JSONDecoder().decode(ResearchEventPayload.self, from: Data(data.utf8)) else { return }
            applyResearchEvent(payload)
        default:
            break
        }
    }

    /// `status` payloads: `searching` | `working` | `composing` | `search_failed`.
    /// The trace's `composing` line also comes from here — it is a real
    /// synthesis signal, not a guess. Phases only move forward; a late
    /// status event never regresses the orb backwards.
    private func applyStatus(_ payload: String) {
        switch payload {
        case "searching":
            promotePhase(.searching)
        case "working":
            promotePhase(.working)
        case "composing":
            promotePhase(.composing)
            if !traceSteps.isEmpty, !traceSteps.contains(where: { $0.kind == .composing }) {
                traceSteps.append(SearchTraceStep(kind: .composing))
            }
        case "search_failed":
            searchPhase = .failed
        default:
            break
        }
    }

    private func applySearchEvent(_ payload: SearchEventPayload) {
        switch payload.type {
        case "started":
            traceSteps.append(SearchTraceStep(kind: .started(label: payload.label ?? payload.intent)))
            promotePhase(.searching)
        case "query":
            traceSteps.append(SearchTraceStep(kind: .query(
                round: payload.round ?? 0,
                text: payload.query ?? "",
                engines: payload.engines ?? [])))
            promotePhase(.searching)
        case "engines":
            // v2 per-engine truth — one honest row group per engine report.
            let outcomes = payload.engineOutcomes ?? []
            guard !outcomes.isEmpty else { return }
            appendUniqueStep(.engines(round: payload.round ?? 0, outcomes: outcomes))
        case "results":
            traceSteps.append(SearchTraceStep(kind: .results(
                round: payload.round ?? 0,
                found: payload.found ?? 0,
                query: payload.query ?? "",
                engines: payload.engines ?? [])))
        case "round":
            appendUniqueStep(.roundSummary(
                round: payload.round ?? 0,
                verified: payload.sourcesVerified ?? 0,
                failed: payload.sourcesFailed ?? 0,
                syndicated: payload.syndicatedGroups))
        case "failed":
            appendUniqueStep(.searchFailed(reason: payload.reason ?? ""))
            searchPhase = .failed
        case "completed":
            // Legacy summary — the v2 research:completed carries the same
            // shape; exact duplicates collapse to one honest line.
            appendUniqueStep(.completedSummary(
                queries: payload.queries ?? 0,
                sources: payload.sources ?? 0,
                retrieved: payload.retrieved ?? 0,
                citations: payload.usedCitations))
        default:
            break // unknown search sub-types stay invisible, never invented
        }
    }

    private func applySourceEvent(_ payload: SourceEventPayload) {
        switch payload.type {
        case "discovered":
            guard let source = payload.source else { return }
            upsertLiveSource(source)
            traceSteps.append(SearchTraceStep(kind: .discovered(
                ordinal: source.ordinal,
                title: source.title,
                domain: source.domain)))
        case "opening", "reading":
            let ordinal = payload.ordinal ?? 0
            upsertLiveSource(MessageSource(ordinal: ordinal, status: "reading"))
            traceSteps.append(SearchTraceStep(kind: .reading(ordinal: ordinal)))
            promotePhase(.working) // page retrieval is genuinely in flight
        case "completed", "read":
            // v2: `read` is the canonical name of v1 `completed` — BOTH are
            // emitted. The card upsert is idempotent by ordinal and the
            // trace keeps ONE honest line per real read (exact-duplicate
            // drop), per the protocol's idempotency rule.
            let ordinal = payload.ordinal ?? 0
            upsertLiveSource(MessageSource(
                ordinal: ordinal,
                status: payload.status ?? "retrieved",
                publishedDate: payload.publishedDate))
            appendUniqueStep(.sourceCompleted(ordinal: ordinal, chars: payload.chars))
        case "evidence":
            // v2 (evidence.extracted): extraction really happened. Evidence
            // processing is genuine retrieval work — the honest orb maps it.
            traceSteps.append(SearchTraceStep(kind: .evidence(
                ordinal: payload.ordinal ?? 0,
                chars: payload.chars,
                windowChars: payload.windowChars)))
            promotePhase(.working)
        case "failed":
            let ordinal = payload.ordinal ?? 0
            upsertLiveSource(MessageSource(ordinal: ordinal, status: payload.status ?? "failed"))
            traceSteps.append(SearchTraceStep(kind: .sourceFailed(
                ordinal: ordinal,
                reason: payload.reason ?? "")))
        case "skipped":
            let ordinal = payload.ordinal ?? 0
            upsertLiveSource(MessageSource(ordinal: ordinal, status: payload.status ?? "skipped"))
            traceSteps.append(SearchTraceStep(kind: .sourceSkipped(
                ordinal: ordinal,
                reason: payload.reason ?? "")))
        default:
            break
        }
    }

    /// PHASE 8.2 v2 — research-level truth rides the same trace, so the
    /// audit reads top-to-bottom exactly as the turn happened.
    private func applyResearchEvent(_ payload: ResearchEventPayload) {
        switch payload.type {
        case "started":
            appendUniqueStep(.researchStarted(
                depth: payload.depth,
                roundsPlanned: payload.roundsPlanned))
            promotePhase(.searching) // the retrieval legs start now
        case "round_completed":
            appendUniqueStep(.researchRound(
                round: payload.round ?? 0,
                found: payload.found ?? 0,
                read: payload.read ?? 0,
                failed: payload.failed ?? 0,
                syndicated: payload.syndicatedGroups))
        case "synthesis_started":
            promotePhase(.composing)
            if !traceSteps.isEmpty, !traceSteps.contains(where: { $0.kind == .composing }) {
                traceSteps.append(SearchTraceStep(kind: .composing))
            }
        case "completed":
            appendUniqueStep(.completedSummary(
                queries: payload.queries ?? 0,
                sources: payload.sources ?? 0,
                retrieved: payload.retrieved ?? 0,
                citations: payload.usedCitations))
        case "failed":
            appendUniqueStep(.researchFailed(reason: payload.reason ?? payload.message ?? ""))
            searchPhase = .failed
        case "cancelled":
            traceSteps.append(SearchTraceStep(kind: .researchCancelled(by: payload.by)))
        default:
            break // unknown research sub-types stay invisible, never invented
        }
    }

    /// Appends a trace step unless an identical step already arrived this
    /// turn — the v2 idempotency pairs (`source` `read`/`completed`, the
    /// legacy `search:completed` + v2 `research:completed`) and re-sent
    /// events collapse to ONE honest line. Compares structural Kind equality
    /// (the step's UUID identity is deliberately excluded).
    private func appendUniqueStep(_ kind: SearchTraceStep.Kind) {
        guard !traceSteps.contains(where: { $0.kind == kind }) else { return }
        traceSteps.append(SearchTraceStep(kind: kind))
    }

    /// One card per ordinal — later events on the same source refine it in
    /// place (discovered → reading → retrieved/failed), never duplicate it.
    /// An `opening`/`reading` that arrives before its `discovered` still
    /// gets an honest placeholder card (ordinal known, metadata unknown).
    private func upsertLiveSource(_ update: MessageSource) {
        if let index = liveSources.firstIndex(where: { $0.ordinal == update.ordinal }) {
            var existing = liveSources[index]
            existing.status = update.status ?? existing.status
            existing.publishedDate = update.publishedDate ?? existing.publishedDate
            if !update.title.isEmpty { existing.title = update.title }
            if !update.url.isEmpty { existing.url = update.url }
            if !update.domain.isEmpty { existing.domain = update.domain }
            if !update.snippet.isEmpty { existing.snippet = update.snippet }
            liveSources[index] = existing
        } else {
            liveSources.append(update)
        }
    }

    /// Monotonic phase promotion — `idle < searching < working < composing`.
    /// `.failed` is set directly (a failed search is a real, terminal phase
    /// for the search leg; the turn itself keeps flowing honestly).
    private func promotePhase(_ next: ChatSearchPhase) {
        let ranks: [ChatSearchPhase: Int] = [.idle: 0, .searching: 1, .working: 2, .composing: 3]
        if (ranks[next] ?? 0) > (ranks[searchPhase] ?? 0) {
            searchPhase = next
        }
    }

    private func finalize(with message: Message?) {
        defer {
            isStreaming = false
            streamTask = nil
            searchPhase = .idle // done → the orb honestly goes idle
        }
        stopFlushLoop()
        drainSearchEvents() // the tail events land before the trace closes
        guard let message, !message.content.isEmpty else {
            finalizeLocal()
            return
        }
        guard let index = messages.indices.last, messages[index].isStreaming else { return }
        messages[index].content = message.content
        messages[index].isStreaming = false
        // PHASE 8.1: the done payload's persisted sources are the truth for
        // citation chips and cards; the live event rows back them up only if
        // the server sent none (older backend). Clarify backfills the same way.
        if let persisted = message.sources, !persisted.isEmpty {
            messages[index].sources = persisted
        } else if !liveSources.isEmpty {
            messages[index].sources = liveSources
        }
        messages[index].clarifyOptions = message.clarifyOptions ?? clarifyPrompt?.jsonString
        messages[index].traceSummary = SearchTraceStep.collapsedSummary(traceSteps)
        // PHASE 8.2: the full step list rides the turn memory-only — the
        // research-details audit reads it until the conversation leaves
        // memory; reloads re-derive from the persisted sources instead.
        messages[index].traceSteps = traceSteps
        persistAssistant(content: message.content)
        traceSteps = []
        liveSources = []
        clarifyPrompt = nil
    }

    /// Keeps whatever streamed so far (GS Lite reply included) and closes the
    /// bubble; the same text lands in the store so history is identical.
    private func finalizeLocal() {
        defer {
            isStreaming = false
            streamTask = nil
            searchPhase = .idle
        }
        stopFlushLoop()
        drainSearchEvents() // stop/cancel keeps the trace that really happened
        flushStreamingText() // land the last ≤33ms of deltas before closing
        guard let index = messages.indices.last, messages[index].isStreaming else { return }
        if messages[index].content.isEmpty {
            messages.remove(at: index)
        } else {
            messages[index].isStreaming = false
            if messages[index].sources == nil, !liveSources.isEmpty {
                messages[index].sources = liveSources
            }
            if messages[index].clarifyOptions == nil {
                messages[index].clarifyOptions = clarifyPrompt?.jsonString
            }
            messages[index].traceSummary = SearchTraceStep.collapsedSummary(traceSteps)
            messages[index].traceSteps = traceSteps // memory-only, never stored
            persistAssistant(content: messages[index].content)
        }
        traceSteps = []
        liveSources = []
        clarifyPrompt = nil
    }

    private func persistAssistant(content: String) {
        guard let conversationID, !content.isEmpty else { return }
        ConversationStore.shared.append(StoredMessage(
            id: UUID().uuidString,
            conversationId: conversationID,
            role: "assistant",
            content: content,
            createdAt: ConversationStore.now()))
    }

    // MARK: - GS Lite (on-device responder)

    /**
     * Streams the on-device reply word by word with the same cadence as
     * Android, so an offline turn reads exactly like a networked one. Stop
     * keeps whatever streamed before it was pressed. PHASE 5 note: an
     * offline turn keeps its attachments on the LOCAL user message (they
     * were uploaded earlier — a ready draft restores across relaunches); the
     * responder answers from the text prompt exactly as before, unchanged.
     */
    private func streamLocalReply(_ prompt: String) async {
        let reply = Self.localReply(prompt)
        let words = reply.split(separator: " ", omittingEmptySubsequences: false)
        for (index, word) in words.enumerated() {
            streamBuffer.append((index == 0 ? "" : " ") + word)
            do {
                try await Task.sleep(nanoseconds: 26_000_000)
            } catch {
                break // Stop pressed mid-reply: keep what's on screen
            }
        }
    }

    /**
     * The reply pool: prompt-aware, varied per prompt via a Java-compatible
     * hash so both platforms pick the same variant and the choice is stable
     * across launches (Swift's hashValue is per-process randomized). Never
     * mentions servers, errors or connectivity — the app reads fully
     * functional on a fresh install.
     */
    private static func localReply(_ prompt: String) -> String {
        let p = prompt.trimmingCharacters(in: .whitespacesAndNewlines)
        let lower = p.lowercased()
        var topic = String(p.prefix(72))
        while let last = topic.last, ".?!".contains(last) { topic.removeLast() }

        func pick(_ options: String...) -> String {
            options[Int(javaHash(p).magnitude % UInt32(options.count))]
        }

        if lower.range(of: "^(hi|hey|hello|yo|sup|hola|good (morning|afternoon|evening))[!.? ]*$", options: .regularExpression) != nil {
            return "Hey — good to see you. What are we making today?\n\nI can draft, plan, " +
                "refactor, brainstorm or just think out loud with you. Drop an idea and " +
                "I'll take it from there."
        }
        if lower.contains("what can you do") || lower.contains("who are you") || lower.contains("your name") {
            return "I'm GS — your pocket think-tank.\n\n• Draft & rewrite: emails, posts, docs\n" +
                "• Plan & break down: projects, trips, launches\n• Explain: code, concepts, " +
                "contracts\n• Generate: images, prompts, study notes\n\nStart anywhere — " +
                "even a half-formed thought works."
        }
        if lower.hasPrefix("code") || lower.contains("kotlin") || lower.contains("swift") ||
            lower.contains("function") || lower.contains("bug") || lower.contains("error") {
            return pick(
                "Here's a clean way to tackle \"\(topic)\":\n\n1. Reproduce the smallest failing case\n" +
                    "2. Write the happy path first, then guard the edges\n3. Add a test that fails " +
                    "without the fix and passes with it\n\nPaste the snippet and I'll go line by line.",
                "For \"\(topic)\" I'd keep it simple:\n\n• Extract the pure logic into a function\n" +
                    "• Push side effects (IO, state) to the edges\n• Name things by what they mean, " +
                    "not what they do\n\nShare the code and I'll tailor it."
            )
        }
        if lower.contains("plan") || lower.contains("roadmap") || lower.contains("launch") ||
            lower.contains("trip") || lower.contains("schedule") {
            return "Love it — \"\(topic)\" breaks down like this:\n\n• Scope: pick the one outcome that " +
                "defines success\n• Milestones: 3 checkpoints, each shippable on its own\n" +
                "• Risks: name the top two and their plan B\n• Next step: the 30-minute " +
                "action you can take today\n\nTell me the deadline and I'll back-plan every phase."
        }
        if lower.contains("write") || lower.contains("draft") || lower.contains("email") ||
            lower.contains("post") || lower.contains("caption") {
            return "Here's a first pass on \"\(topic)\":\n\nOpen with the reader's problem, land one " +
                "concrete promise, and close with a single next step. Short sentences. No " +
                "hedge words.\n\nWant it warmer, punchier, or more formal? I'll rewrite in place."
        }
        return pick(
            "\"\(topic)\" — good thread to pull. Here's my take:\n\n• Start from the outcome " +
                "you want, work backwards\n• Cut the problem to the smallest version that " +
                "still matters\n• Ship a rough v0 today; refine with real feedback\n\n" +
                "Ask me to expand any point and I'll go deeper.",
            "On \"\(topic)\":\n\nThe useful move is to separate what's fixed from what's " +
                "flexible. Fix the goal, flex the path. Then pick the cheapest experiment " +
                "that tests your assumption this week.\n\nWant a checklist version of this?",
            "Thinking through \"\(topic)\":\n\n1. What does success look like in one sentence?\n" +
                "2. What's the biggest unknown blocking it?\n3. What can you test about that " +
                "unknown in under an hour?\n\nAnswer those three and the path usually " +
                "reveals itself — I'm here to think it through with you."
        )
    }

    /// Java String.hashCode over UTF-16 code units — deterministic across
    /// launches and identical to Android's pick, so the same prompt lands on
    /// the same variant everywhere.
    private static func javaHash(_ s: String) -> Int32 {
        var h: Int32 = 0
        for unit in s.utf16 {
            h = h &* 31 &+ Int32(bitPattern: UInt32(unit))
        }
        return h
    }

    // MARK: - History

    private func loadHistory(conversationID: String) {
        // Store first — threads must survive relaunches even fully offline.
        // Deep-perf pass #2b: the open is a newest-window read, so a
        // 2,000-turn thread opens with the same O(page) cost as a 20-turn one.
        let stored = ConversationStore.shared.recentMessages(for: conversationID, limit: historyPageSize)
        if !stored.isEmpty {
            applyWindow(stored)
            return
        }
        // Untouched static demo conversations (drawer seeds "demo-N" ids).
        if conversationID.hasPrefix("demo-") {
            messages = Self.sampleTranscript(for: conversationID)
            return
        }
        isLoadingHistory = true
        Task { [weak self] in
            do {
                let history = try await APIClient.shared.messages(conversationID: conversationID)
                guard let self else { return }
                self.messages = history.map {
                    ChatMessage(
                        role: $0.role,
                        content: $0.content,
                        createdAt: $0.createdAt,
                        attachments: $0.attachments,
                        sources: $0.sources,
                        clarifyOptions: $0.clarifyOptions)
                }
                // The remote seed carries the full thread — nothing older locally.
                self.oldestStamp = self.messages.first?.createdAt
                self.hasOlder = false
                self.isLoadingHistory = false
            } catch {
                guard let self else { return }
                self.isLoadingHistory = false
                // A cold cache + offline is survivable: start the thread empty;
                // the first send will fabricate the conversation row locally.
                if Self.isCancellation(error) { return }
                self.messages = []
            }
        }
    }

    private func applyWindow(_ page: [StoredMessage]) {
        messages = page.map {
            ChatMessage(role: $0.role, content: $0.content, createdAt: $0.createdAt, attachments: $0.attachments)
        }
        oldestStamp = page.first?.createdAt
        hasOlder = page.count == historyPageSize
    }

    // MARK: - Older pages (deep-perf pass #2b)

    /// Real user scroll-ups arm pagination — called from the transcript's drag
    /// gesture, the same site that flips the reading guard (Android parity).
    func armOlderPages() {
        olderArmed = true
    }

    /**
     * Prepends the next local page of history above the loaded window.
     * Synchronous by design: the (conversationId, createdAt) index makes a
     * 60-row read sub-millisecond, and the caller restores the scroll anchor
     * immediately after the insert. Existing row identity is untouched, so
     * SwiftUI keeps every rendered bubble in place.
     */
    func loadOlder() {
        guard olderArmed, hasOlder, let conversationID, let oldest = oldestStamp else { return }
        let page = ConversationStore.shared.olderMessages(for: conversationID, before: oldest, limit: historyPageSize)
        guard !page.isEmpty else {
            hasOlder = false
            return
        }
        messages.insert(contentsOf: page.map {
            ChatMessage(role: $0.role, content: $0.content, createdAt: $0.createdAt, attachments: $0.attachments)
        }, at: 0)
        oldestStamp = page.first?.createdAt
        if page.count < historyPageSize { hasOlder = false }
    }

    // MARK: - Error helpers

    private static func isCancellation(_ error: Error) -> Bool {
        error is CancellationError || (error as? URLError)?.code == .cancelled
    }

    // MARK: - Demo transcripts (static pass — see worklog, Task 6-e)

    private static func sampleTranscript(for conversationID: String) -> [ChatMessage] {
        switch conversationID {
        case "demo-1":
            return [
                ChatMessage(role: "user", content: "Send the revised deck plan for Q3 pricing — one page, new tiers first."),
                ChatMessage(role: "assistant", content: "Here's the one-page skeleton:\n\n1. Lead with the three tiers (Starter · Studio · Scale) — value before price.\n2. Frame the founder discount as early-adopter lock-in, not a markdown.\n3. Close with the migration path for existing accounts.\n\nWant me to tighten the discount wording?")
            ]
        case "demo-2":
            return [
                ChatMessage(role: "user", content: "Five days in Kyoto, temples before 9am, food after dark — sketch an itinerary."),
                ChatMessage(role: "assistant", content: "Day 1 — Higashiyama at dawn: Kiyomizu-dera before the crowds, then the Philosopher's Path.\nDay 2 — Arashiyama bamboo grove at 7am, river lunch, Nishiki market at dusk.\nDay 3 — Fushimi Inari at sunrise, then a kaiseki splurge.\n\nWant train times added?")
            ]
        case "demo-3":
            return [
                ChatMessage(role: "user", content: "Why does my coroutine inside launch block the calling thread here?"),
                ChatMessage(role: "assistant", content: "It usually doesn't — unless something on that dispatcher is blocking. Two usual suspects:\n\n1. runBlocking on a dispatcher the launched job also uses.\n2. A Mutex/withContext cycle back to the same single-threaded dispatcher.\n\nPaste the snippet around the launch and I'll point at the line.")
            ]
        case "demo-4":
            return [
                ChatMessage(role: "user", content: "Three tone pillars for the brand — sharper than 'friendly but expert'."),
                ChatMessage(role: "assistant", content: "Try these:\n\n· Plainspoken — short sentences, zero jargon debt.\n· Warmly precise — numbers and names, delivered like a good host.\n· Quietly confident — no exclamation marks doing the work.\n\nShall I rewrite the landing hero in this voice?")
            ]
        default:
            return []
        }
    }
}
