import SwiftUI
import Combine
import os.log

/**
 * Owns one conversation: history load (store first, then network), optimistic
 * send, SSE streaming with live accumulation, stop, retry/regenerate. Every
 * turn is persisted to `ConversationStore` so threads survive relaunches, and
 * failed requests keep actual received text and expose retry rather than
 * substituting unrelated canned answers. All observable state mutations happen
 * on the main actor; streaming deltas enter lock-guarded accumulation buffers.
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

    func reset() {
        lock.lock()
        text = ""
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

enum ChatGenerationStatus: Equatable {
    case generating
    case checking
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
        case toolActivity(title: String, detail: String)
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

/// FLASH-MODE BUG 1: the `mode` event payload — the first event of every
/// turn, carrying the backend's resolved effective mode.
private struct ModeEventPayload: Decodable {
    var state: String?
    var effectiveMode: String?
    var requested: String?
    var effort: String?
}

/// REASONING TRACE wire frame: `{"delta":"..."}`.
private struct ReasoningEventPayload: Decodable {
    var delta: String?
}

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

    // Swift does NOT synthesize CodingKeys when decoding is fully custom —
    // it must be declared. `engineOutcomes` is deliberately absent: it is
    // decoded by hand under the shared `engines` wire key.
    private enum CodingKeys: String, CodingKey {
        case type, intent, depth, label, round, query, engines
        case found, sourcesVerified, sourcesFailed, verified
        case syndicatedGroups, reason, queries, sources, retrieved, usedCitations
    }

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

    private enum CodingKeys: String, CodingKey {
        case type, ordinal, chars, windowChars, publishedDate
        case reason, status, source
    }

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

    private enum CodingKeys: String, CodingKey {
        case type, depth, roundsPlanned, round, found, read, failed
        case syndicatedGroups, queries, sources, retrieved, usedCitations
        case reason, message, by
    }

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
        /// The SERVER id of this turn. `id` above is a client UUID (SwiftUI
        /// identity, stable across streaming deltas); the verdict endpoint
        /// needs the row the database actually knows. A streaming assistant
        /// turn has no server id until `done` arrives, which is exactly when
        /// it becomes eligible for feedback — so nil here means "not yet a
        /// real row", and the verdict UI stays hidden rather than guessing.
        var serverId: String? = nil
        /// "up" | "down" | nil — the reader's verdict on this turn. Local
        /// truth: it is applied on tap, before the PATCH resolves.
        var feedback: String? = nil
        /// The actual offline row identity, retained without pretending that a
        /// local UUID is a server row eligible for the feedback API.
        var durableID: String? = nil
        var reactPreviewIdentity: String {
            if let serverId, !serverId.isEmpty { return serverId }
            if let durableID, !durableID.isEmpty { return durableID }
            return id.uuidString
        }
    }

    // MARK: - Published state

    @Published var messages: [ChatMessage] = []
    @Published var draft: String = ""
    @Published var isStreaming = false
    @Published var isLoadingHistory = false
    @Published var errorMessage: String?
    @Published var conversationID: String?

    /// FLASH MODE (Phase 3) — the composer's thinking mode, persisted per
    /// conversation in UserDefaults (gs.mode.<conversationId>, gs.mode.new
    /// fallback for brand-new chats — same semantics as the web client).
    /// Default 'flash'.
    @Published var chatMode: String
    static func loadChatMode(_ conversationID: String?) -> String {
        let d = UserDefaults.standard
        let valid = Set(["flash", "thinking", "auto"])
        if let id = conversationID, let saved = d.string(forKey: "gs.mode.\(id)"), valid.contains(saved) {
            return saved
        }
        if let fresh = d.string(forKey: "gs.mode.new"), valid.contains(fresh) { return fresh }
        return "flash"
    }
    func setChatMode(_ mode: String) {
        guard ["flash", "thinking", "auto"].contains(mode) else { return }
        chatMode = mode
        UserDefaults.standard.set(mode, forKey: "gs.mode.\(conversationID ?? "new")")
    }
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
    /// Real synthesis lifecycle from the backend or native inference hand-off.
    @Published private(set) var generationStatus: ChatGenerationStatus?
    /// FLASH-MODE BUG 1: the wire's resolved effective mode for THIS turn
    /// ("flash"|"thinking"; nil = no mode event — pre-v0.70 backend). The
    /// word "Thinking…" renders ONLY while this is "thinking".
    @Published private(set) var effectiveMode: String?
    /// The backend's real effort for this turn ("low"|"medium"|"high"; nil =
    /// provider default or flash). The Effort row renders this, never a guess.
    @Published private(set) var effectiveEffort: String?
    // REASONING TRACE (chat-reasoning panel): the model's thinking stream for
    // the LIVE turn. Stream-only by contract (never persisted): reset at send,
    // the measured start/end dates freeze when the first answer delta lands.
    @Published private(set) var reasoningText: String?
    @Published private(set) var reasoningStart: Date?
    @Published private(set) var reasoningEnd: Date?

    // MARK: - Private state

    private var streamTask: Task<Void, Never>?
    private var lastSentText: String?

    /// Growing live text of the current stream. Deltas append off-main; the
    /// flush task publishes to the transcript at display cadence, so a
    /// per-token published storm (whole-screen re-evaluation per chunk) is
    /// gone — one mutation per flush, committed to the store once at finalize.
    private var streamBuffer = StreamAccumulator()
    private var flushTask: Task<Void, Never>?
    // TASK 2 — tap-to-first-visible-char stopwatch state (send() start →
    // first non-empty flush publish). Logged as GS-PERF, one line per stream.
    private var sendTappedAt: Date?
    private var firstCharLogged = false

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

    private let apiClient: APIClient
    private let store: ConversationStore

    // MARK: - Init

    init(conversationID: String?, apiClient: APIClient = .shared, store: ConversationStore? = nil) {
        self.apiClient = apiClient
        self.store = store ?? .shared
        self.conversationID = conversationID
        self.chatMode = ChatViewModel.loadChatMode(conversationID)
        if let conversationID {
            loadHistory(conversationID: conversationID)
        }
    }

    deinit {
        streamTask?.cancel()
        flushTask?.cancel()
    }

    // MARK: - Intents

    /// The turn's hidden context: the plan checklist plus the reader's tone and
    /// detail. Empty means nil — an empty system message is not context.
    static func turnContext(for conversationID: String) -> String? {
        let parts = [
            NexlyPlanProgressStore.shared.context(for: conversationID),
            ResponseStyle.directive,
        ].compactMap { $0 }
        let joined = parts.joined(separator: "\n\n")
        return joined.isEmpty ? nil : joined
    }

    /// PHASE 5: `attachments` (every draft `ready` — the caller gates on
    /// AttachmentStore) travel as their uploaded server ids; the optimistic
    /// user bubble renders the chips immediately. Text may be empty ONLY
    /// when attachments are present (server rule, mirrored here).
    func send(attachments: [Attachment] = []) {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty || !attachments.isEmpty, !isStreaming else { return }
        draft = ""
        // REASONING TRACE: the panel lives with one turn only.
        reasoningText = nil
        reasoningStart = nil
        reasoningEnd = nil
        // TASK 2 — tap-to-first-visible-character stopwatch: the send tap and
        // the first published non-empty content bound the number the operator
        // asked for. Logged once per stream as GS-PERF.
        sendTappedAt = Date()
        firstCharLogged = false
        // Attachments-only turns are not regenerable: the ids are claimed by
        // the sent message server-side, so retry/regenerate stays text-only.
        lastSentText = text.isEmpty ? nil : text
        // COMMIT 1(b). The device check runs BEFORE the network, and only while
        // the user has turned it on. With "Prefer on-device AI" off -- the
        // default -- every condition below is false and beginStreaming takes
        // exactly the path it took before this existed.
        //
        // Attachments are excluded: a local model cannot read the files that
        // were just uploaded, so answering from it would produce a confident
        // reply about content it never saw. That is worse than a slow one.
        if SettingsStore.shared.preferLocal, attachments.isEmpty, GsNativeLoader.isAvailable {
            beginLocalStreaming(text: text)
            return
        }
        beginStreaming(text: text, appendUserMessage: true, attachments: attachments.isEmpty ? nil : attachments, mode: chatMode)
    }

    /// Local-first turns publish the same live bubble as provider turns, but
    /// use llama.cpp's real decoded-token callback on a detached worker. This
    /// keeps inference off the main actor and makes the prefill/decode period
    /// visible instead of freezing the screen before the first completed string.
    private func beginLocalStreaming(text: String) {
        errorMessage = nil
        traceSteps = []
        liveSources = []
        clarifyPrompt = nil
        searchPhase = .idle
        generationStatus = .generating
        effectiveMode = nil
        effectiveEffort = nil
        streamSink = SearchEventSink()

        if conversationID == nil {
            let title = SettingsStore.shared.autoTitle && !text.isEmpty
                ? String(text.prefix(40)) : "New chat"
            conversationID = store.createLocalConversation(title: title).id
        }
        let stamp = ConversationStore.now()
        let user = ChatMessage(role: "user", content: text, createdAt: stamp)
        messages.append(user)
        if let conversationID {
            let identity = user.id.uuidString
            store.append(StoredMessage(
                id: identity,
                conversationId: conversationID,
                role: "user",
                content: text,
                createdAt: stamp))
            messages[messages.count - 1].durableID = identity
        }
        messages.append(ChatMessage(
            role: "assistant",
            content: "",
            isStreaming: true,
            createdAt: stamp))

        streamBuffer = StreamAccumulator()
        let accumulator = streamBuffer
        isStreaming = true
        startFlushLoop()
        guard let responseID = messages.last?.id else { return }
        let prompt = text
        streamTask = Task {
            let generation = Task.detached(priority: .userInitiated) {
                try GsNative.chatStream(prompt) { delta in
                    guard !Task.isCancelled else { return false }
                    accumulator.append(delta)
                    return true
                }
            }
            do {
                let reply = try await withTaskCancellationHandler {
                    try await generation.value
                } onCancel: {
                    generation.cancel()
                }
                guard self.isStreaming, self.messages.last?.id == responseID else { return }
                let visible = accumulator.snapshot()
                if reply.isEmpty, !visible.isEmpty {
                    // Keep the exact text already published if a native build
                    // returns an empty terminal payload after real callbacks.
                } else if reply.hasPrefix(visible), reply.count > visible.count {
                    accumulator.append(String(reply.dropFirst(visible.count)))
                } else if visible.isEmpty, !reply.isEmpty {
                    // Defensive compatibility for an older native framework
                    // whose stream symbol is present but callback is a no-op.
                    accumulator.append(reply)
                } else if reply != visible {
                    os_log("GS-PERF local native stream differed from terminal text", log: .default, type: .error)
                }
                self.stopFlushLoop()
                while self.isStreaming, self.messages.last?.id == responseID,
                      let index = self.messages.indices.last,
                      self.messages[index].isStreaming,
                      self.messages[index].content != accumulator.snapshot() {
                    try Task.checkCancellation()
                    self.flushStreamingText()
                    try await Task.sleep(nanoseconds: 33_000_000)
                }
                if self.isStreaming, self.messages.last?.id == responseID { self.finalizeLocal() }
            } catch {
                guard self.isStreaming, self.messages.last?.id == responseID else { return }
                if Self.isCancellation(error) {
                    self.finalizeLocal()
                } else {
                    let hasPartial = !accumulator.isEmpty
                    self.finalizeLocal()
                    self.errorMessage = hasPartial
                        ? "On-device generation stopped early. Your partial answer is saved."
                        : "The on-device model could not answer. Try again or switch back to online mode."
                }
            }
        }
    }

    /// PHASE 8.1 clarify quick-choice: the tapped label travels as a normal
    /// user message through the exact pipeline a typed send uses — the
    /// composer draft is untouched (the chip's text never lands in the field).
    func sendClarifyChoice(_ label: String) {
        let text = label.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !isStreaming else { return }
        lastSentText = text
        beginStreaming(text: text, appendUserMessage: true, mode: chatMode)
    }

    /// Resends the last user text after a failure (also backs regenerate).
    func retry() {
        guard !isStreaming, let text = lastSentText else { return }
        if let last = messages.last, last.role != "user" {
            messages.removeLast() // drop the failed/partial assistant row
        }
        beginStreaming(text: text, appendUserMessage: false, mode: chatMode)
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
            self.store.truncateMessages(fromUserContent: original, in: conversation)
        }
        lastSentText = text
        beginStreaming(text: text, appendUserMessage: true)
    }

    /// Cancels the in-flight stream; the task throws CancellationError and
    /// the partial assistant text is kept on screen.
    func stop() {
        streamTask?.cancel()
        streamTask = nil
        // Close synchronously: callers can send a new turn immediately, and
        // every already-received byte survives even if its reveal was pending.
        // The cancelled task's response-ID guard prevents late finalization.
        if isStreaming { finalizeLocal() }
    }

    /// Benchmark branch-new-chat: everything up to and including the tapped
    /// turn becomes the opening history of a fresh local conversation, and
    /// this surface re-bases onto the branch. The original thread is untouched.
    func branch(at index: Int) {
        guard !isStreaming, messages.indices.contains(index) else { return }
        let turns = Array(messages[...index]).filter { !$0.content.isEmpty }
        guard !turns.isEmpty else { return }
        let sourceTitle = conversationID.flatMap {
            self.store.conversation(withID: $0)?.title
        } ?? "New chat"
        let conversation = self.store.createLocalConversation(
            title: String("Branch: \(sourceTitle)".prefix(40)))
        let fallbackStamp = ConversationStore.now()
        for turn in turns {
            self.store.append(StoredMessage(
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
            "Reply with the translation only, no notes, no quotes.\n\n\(trimmed)"
        var scratchID = ""
        defer {
            if !scratchID.isEmpty {
                Task {
                    try? await self.apiClient.deleteConversation(id: scratchID)
                }
            }
        }
        var accumulated = ""
        do {
            let scratch = try await self.apiClient.createConversation(title: "Translation")
            scratchID = scratch.id
            try await self.apiClient.stream(
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

    private func beginStreaming(text: String, appendUserMessage: Bool, attachments: [Attachment]? = nil, mode: String? = nil) {
        errorMessage = nil
        // PHASE 8.1: every turn starts with a clean honest trace — no state
        // leaks from the previous turn, nothing exists until an event says so.
        traceSteps = []
        liveSources = []
        clarifyPrompt = nil
        searchPhase = .idle
        generationStatus = nil
        effectiveMode = nil
        effectiveEffort = nil
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
        guard let responseID = messages.last?.id else { return }
        streamTask = Task {
            do {
                let conversationID = try await self.ensureConversation(for: text)
                guard self.isStreaming, self.messages.last?.id == responseID else { return }
                // A failed first create leaves the optimistic user turn in
                // memory. Persist it once creation actually succeeds, including
                // when that happens on Retry (appendUserMessage == false).
                if let index = self.messages.lastIndex(where: { $0.role == "user" }),
                   self.messages[index].durableID == nil {
                    let user = self.messages[index]
                    self.store.append(StoredMessage(
                        id: user.id.uuidString,
                        conversationId: conversationID,
                        role: "user",
                        content: user.content,
                        createdAt: user.createdAt,
                        attachments: user.attachments))
                    self.messages[index].durableID = user.id.uuidString
                }
                var completedMessage: Message?
                try await self.apiClient.stream(
                    message: text,
                    conversationID: conversationID,
                    attachmentIDs: attachments?.map { $0.id },
                    mode: mode,
                    // Two kinds of non-visible context ride together: the plan
                    // checklist (real progress) and the response style (tone and
                    // depth the reader chose). The style used to be stored and
                    // never read — this is the reader that makes it mean
                    // something.
                    turnContext: Self.turnContext(for: conversationID),
                    onDelta: { [buffer = self.streamBuffer] delta in
                        buffer.append(delta) // lock-guarded — no main hop per token
                    },
                    onDone: { message in
                        // Consume the canonical result before finalizing the
                        // stream, not in a separately scheduled actor task.
                        completedMessage = message
                    },
                    onReset: { [buffer = self.streamBuffer] in
                        // The server discarded a provisional draft after a
                        // real guard failure. Clearing the shared accumulator
                        // makes the next display-cadence flush replace it.
                        buffer.reset()
                    },
                    onEvent: { [sink = self.streamSink] event in
                        // The server titles a chat on its first turn and says so
                        // as it happens. Applying it here is what makes the new
                        // headline live: the drawer row, the Chats card and the
                        // icon (chosen from the headline's topic) are correct
                        // before the user sees them, not after some later refetch.
                        if event.event == "conversation" {
                            Self.applyConversationEvent(event.data)
                        }
                        sink.append(event) // lock-guarded — drained in the flush loop
                    }
                )
                guard self.isStreaming, self.messages.last?.id == responseID else { return }
                if self.streamBuffer.isEmpty, let content = completedMessage?.content, !content.isEmpty {
                    self.streamBuffer.append(content)
                }
                // Reveal received text at display cadence even when an upstream
                // proxy batches several deltas. Never invent tokens or work steps.
                self.stopFlushLoop()
                self.drainSearchEvents()
                while self.isStreaming, self.messages.last?.id == responseID,
                      let index = self.messages.indices.last,
                      self.messages[index].isStreaming,
                      self.messages[index].content != self.streamBuffer.snapshot() {
                    try Task.checkCancellation()
                    self.flushStreamingText()
                    try await Task.sleep(nanoseconds: 33_000_000)
                }
                if self.isStreaming, self.messages.last?.id == responseID { self.finalize(with: completedMessage) }
            } catch {
                // A stopped task may resume after a new send. Its completion or
                // cancellation must never close the newer bubble or its painter.
                guard self.isStreaming, self.messages.last?.id == responseID else { return }
                if Self.isCancellation(error) {
                    self.finalizeLocal()
                } else {
                    // A failed request is not a generated answer. Preserve only
                    // actual received text and expose the existing retry UI.
                    let hasPartialAnswer = !self.streamBuffer.isEmpty
                    self.finalizeLocal()
                    self.errorMessage = hasPartialAnswer
                        ? "The response was interrupted. Your partial answer is saved. Retry to request a new response."
                        : "I couldn't get a response. Please try again."
                }
            }
        }
    }

    /// A nil conversationID means "first message of a brand-new chat":
    /// create it server-side; offline, fabricate a local row so the thread,
    /// recents and pins keep working until the backend is reachable again.
    /// Task 86-e: the first-message auto-title honours Settings → "Auto-title
    /// chats", when it is off, the thread keeps the default "New chat" title
    /// (the same default the store materialises for non-user turns) until it
    /// is renamed.
    private func ensureConversation(for text: String) async throws -> String {
        if let conversationID { return conversationID }
        // Attachments-only first messages (empty text) keep the default title.
        let title = SettingsStore.shared.autoTitle && !text.isEmpty ? String(text.prefix(40)) : "New chat"
        // Do not invent a remote ID after a create failure. The optimistic
        // user turn stays visible and Retry must retry creation, not repeatedly
        // POST to a local UUID the backend has never seen. Existing stored
        // histories are not deleted or reassigned by this failure path.
        let conversation = try await self.apiClient.createConversation(title: title)
        conversationID = conversation.id
        return conversation.id
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

    private func flushStreamingText(drainAll: Bool = false) {
        guard let index = messages.indices.last, messages[index].isStreaming else { return }
        let text = streamBuffer.snapshot()
        guard messages[index].content != text else { return }
        if !firstCharLogged, !text.isEmpty {
            firstCharLogged = true
            // The first answer paint freezes the measured thinking span.
            if reasoningStart != nil, reasoningEnd == nil { reasoningEnd = Date() }
            if let tappedAt = sendTappedAt {
                let ms = Int(Date().timeIntervalSince(tappedAt) * 1000)
                // OSLog has no `.app` member (raw: iOS run 38470835743, "type
                // 'OSLog' has no member 'app'"). The GS-PERF prefix keeps the
                // line greppable from the process' default log handle.
                os_log("GS-PERF tapToFirstVisibleCharMs=%d chars=%d", log: .default, type: .info, ms, text.count)
            }
        }
        let visible = messages[index].content
        let remaining = max(0, text.count - visible.count)
        // Normal provider tokens paint immediately; bursts catch up adaptively.
        // String prefixes are grapheme-safe (including custom-icon tokens' text).
        if !drainAll, text.hasPrefix(visible), remaining > 32 {
            messages[index].content = String(text.prefix(visible.count + max(32, remaining / 12)))
        } else {
            messages[index].content = text
        }
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
        case "mode":
            // FLASH-MODE BUG 1: the first event of every turn — the backend's
            // resolved effective mode. Tolerant parse; anything malformed or
            // unknown is ignored and the neutral default stays.
            if let obj = try? JSONDecoder().decode(ModeEventPayload.self, from: Data(data.utf8)),
               obj.effectiveMode == "flash" || obj.effectiveMode == "thinking" {
                effectiveMode = obj.effectiveMode
                effectiveEffort = obj.effort
            }
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
        case "ledger":
            applyLedgerEvent(data)
        case "reasoning":
            // REASONING TRACE — {"delta":"..."} from thinking-enabled stream
            // turns. Tolerant: a malformed frame is dropped, never breaks the
            // turn. The start date is the first reasoning delta; the end date
            // freezes at the first ANSWER delta (set in the delta path).
            // `delta` is optional on the wire model, so unwrap it before the
            // non-optional use — a frame without a delta is dropped, not fatal.
            if let payload = try? JSONDecoder().decode(ReasoningEventPayload.self, from: Data(data.utf8)),
               let delta = payload.delta, !delta.isEmpty {
                if reasoningStart == nil { reasoningStart = Date() }
                reasoningText = (reasoningText ?? "") + delta
            }
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
        case "generating":
            generationStatus = .generating
        case "checking":
            generationStatus = .checking
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
                publishedDate: payload.publishedDate,
                status: payload.status ?? "retrieved"))
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

    /// Public research work facts, never inferred skills or hidden thought steps.
    private func applyLedgerEvent(_ data: String) {
        guard let payload = try? JSONSerialization.jsonObject(with: Data(data.utf8)) as? [String: Any],
              let type = payload["type"] as? String else { return }
        let query = String((payload["query"] as? String ?? "").prefix(240))
        let title: String
        let detail: String
        switch type {
        case "ledger_started":
            title = "Research started"
            detail = (payload["branches"] as? Int).map { "\($0) branches planned" } ?? ""
            promotePhase(.searching)
        case "branch_started":
            guard !query.isEmpty else { return }
            title = "Researching"; detail = query
            promotePhase(.searching)
        case "branch_completed":
            title = "Research branch completed"; detail = query
        case "branch_failed":
            title = "Research branch failed"; detail = query
        case "subagents_launched":
            guard let count = payload["count"] as? Int, count > 0 else { return }
            title = "Research workers launched"; detail = "\(count) workers"
        case "gaps_open":
            title = "Research gaps found"
            detail = (payload["gaps"] as? [String]).map { "\($0.count) gaps" } ?? ""
        case "contradictions":
            guard let count = payload["count"] as? Int, count > 0 else { return }
            title = "Conflicting evidence found"; detail = "\(count) conflicts"
        case "ledger_final":
            title = "Research evidence assembled"
            detail = (payload["claims"] as? Int).map { "\($0) claims" } ?? ""
        case "stopped_by_user":
            title = "Research stopped"; detail = "Stopped by user"
        default: return
        }
        appendUniqueStep(.toolActivity(title: title, detail: detail))
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

    /// A `conversation` event carries `{"type":"title","id":"…","title":"…"}` as a
    /// JSON string in the payload. Unknown subtypes are ignored, the same
    /// forward-compatibility contract every other event name follows.
    nonisolated static func applyConversationEvent(_ payload: String?) {
        guard let payload,
              let data = payload.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              object["type"] as? String == "title",
              let id = object["id"] as? String,
              let title = object["title"] as? String,
              !title.isEmpty
        else { return }
        Task { @MainActor in
            ConversationStore.shared.rename(id: id, to: title)
        }
    }

    // MARK: - Verdict (Helpful / Not helpful)

    /// Records the reader's verdict on one assistant turn.
    ///
    /// Local first: the thumb must answer the finger, not the network. The
    /// PATCH follows, and a failure rolls the thumb back with an honest error —
    /// a vote the server never received is not a vote, and leaving the thumb lit
    /// would make the UI lie about what the model will see next turn.
    func setFeedback(for id: UUID, value: String?) {
        guard let index = messages.firstIndex(where: { $0.id == id }) else { return }
        let previous = messages[index].feedback
        guard previous != value else { return }
        messages[index].feedback = value
        guard let serverID = messages[index].serverId, !serverID.isEmpty else { return }
        let client = apiClient
        Task { @MainActor [weak self] in
            do {
                try await client.setFeedback(messageID: serverID, value: value)
            } catch {
                guard let self else { return }
                if let i = self.messages.firstIndex(where: { $0.id == id }) {
                    self.messages[i].feedback = previous
                }
                self.errorMessage = "Couldn't save your feedback — try again."
            }
        }
    }

    private func finalize(with message: Message?) {
        defer {
            isStreaming = false
            streamTask = nil
            searchPhase = .idle // done → the orb honestly goes idle
            generationStatus = nil
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
        // The row now exists in the database, so the verdict endpoint finally
        // has something to point at. Before `done` there is no id and the
        // thumb stays hidden — a thumb that 404s is worse than no thumb.
        messages[index].serverId = message.id
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
            generationStatus = nil
        }
        stopFlushLoop()
        drainSearchEvents() // stop/cancel keeps the trace that really happened
        flushStreamingText(drainAll: true) // stop/error retains ALL received text
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
        guard let conversationID, !content.isEmpty,
              let index = messages.indices.last, messages[index].role == "assistant" else { return }
        // Persist the SAME identity used by the live answer. A new unrelated
        // UUID here made actual React edits disappear after history reload.
        let identity = messages[index].reactPreviewIdentity
        messages[index].durableID = identity
        self.store.append(StoredMessage(
            id: identity,
            conversationId: conversationID,
            role: "assistant",
            content: content,
            createdAt: ConversationStore.now()))
    }

    // Real on-device generation remains opt-in in send(). Remote failures
    // must never be replaced by a keyword-selected pool of canned answers.

    // MARK: - History

    private func loadHistory(conversationID: String) {
        // Store first — threads must survive relaunches even fully offline.
        // Deep-perf pass #2b: the open is a newest-window read, so a
        // 2,000-turn thread opens with the same O(page) cost as a 20-turn one.
        let stored = self.store.recentMessages(for: conversationID, limit: historyPageSize)
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
        let client = apiClient
        Task { [weak self] in
            do {
                let history = try await client.messages(conversationID: conversationID)
                guard let self else { return }
                self.messages = history.map {
                    ChatMessage(
                        role: $0.role,
                        content: $0.content,
                        createdAt: $0.createdAt,
                        attachments: $0.attachments,
                        sources: $0.sources,
                        clarifyOptions: $0.clarifyOptions,
                        serverId: $0.id,
                        feedback: $0.feedback)
                }
                // The remote seed carries the full thread — nothing older locally.
                self.oldestStamp = self.messages.first?.createdAt
                self.hasOlder = false
                self.isLoadingHistory = false
            } catch {
                guard let self else { return }
                self.isLoadingHistory = false
                // A cold cache stays empty after a failed history load. Do not
                // fabricate previous answers or erase other stored histories.
                if Self.isCancellation(error) { return }
                self.messages = []
            }
        }
    }

    private func applyWindow(_ page: [StoredMessage]) {
        messages = page.map {
            ChatMessage(role: $0.role, content: $0.content, createdAt: $0.createdAt, attachments: $0.attachments, durableID: $0.id)
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
        let page = self.store.olderMessages(for: conversationID, before: oldest, limit: historyPageSize)
        guard !page.isEmpty else {
            hasOlder = false
            return
        }
        messages.insert(contentsOf: page.map {
            ChatMessage(role: $0.role, content: $0.content, createdAt: $0.createdAt, attachments: $0.attachments, durableID: $0.id)
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
                ChatMessage(role: "user", content: "Send the revised deck plan for Q3 pricing, one page, new tiers first."),
                ChatMessage(role: "assistant", content: "Here's the one-page skeleton:\n\n1. Lead with the three tiers (Starter · Studio · Scale), value before price.\n2. Frame the founder discount as early-adopter lock-in, not a markdown.\n3. Close with the migration path for existing accounts.\n\nWant me to tighten the discount wording?")
            ]
        case "demo-2":
            return [
                ChatMessage(role: "user", content: "Five days in Kyoto, temples before 9am, food after dark, sketch an itinerary."),
                ChatMessage(role: "assistant", content: "Day 1. Higashiyama at dawn: Kiyomizu-dera before the crowds, then the Philosopher's Path.\nDay 2. Arashiyama bamboo grove at 7am, river lunch, Nishiki market at dusk.\nDay 3. Fushimi Inari at sunrise, then a kaiseki splurge.\n\nWant train times added?")
            ]
        case "demo-3":
            return [
                ChatMessage(role: "user", content: "Why does my coroutine inside launch block the calling thread here?"),
                ChatMessage(role: "assistant", content: "It usually doesn't, unless something on that dispatcher is blocking. Two usual suspects:\n\n1. runBlocking on a dispatcher the launched job also uses.\n2. A Mutex/withContext cycle back to the same single-threaded dispatcher.\n\nPaste the snippet around the launch and I'll point at the line.")
            ]
        case "demo-4":
            return [
                ChatMessage(role: "user", content: "Three tone pillars for the brand, sharper than 'friendly but expert'."),
                ChatMessage(role: "assistant", content: "Try these:\n\n· Plainspoken, short sentences, zero jargon debt.\n· Warmly precise, numbers and names, delivered like a good host.\n· Quietly confident, no exclamation marks doing the work.\n\nShall I rewrite the landing hero in this voice?")
            ]
        default:
            return []
        }
    }
}
