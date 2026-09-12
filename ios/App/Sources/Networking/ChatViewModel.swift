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

    // MARK: - Private state

    private var streamTask: Task<Void, Never>?
    private var lastSentText: String?

    /// Growing live text of the current stream. Deltas append off-main; the
    /// flush task publishes to the transcript at display cadence, so a
    /// per-token published storm (whole-screen re-evaluation per chunk) is
    /// gone — one mutation per flush, committed to the store once at finalize.
    private var streamBuffer = StreamAccumulator()
    private var flushTask: Task<Void, Never>?

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

    private func finalize(with message: Message?) {
        defer {
            isStreaming = false
            streamTask = nil
        }
        stopFlushLoop()
        guard let message, !message.content.isEmpty else {
            finalizeLocal()
            return
        }
        guard let index = messages.indices.last, messages[index].isStreaming else { return }
        messages[index].content = message.content
        messages[index].isStreaming = false
        persistAssistant(content: message.content)
    }

    /// Keeps whatever streamed so far (GS Lite reply included) and closes the
    /// bubble; the same text lands in the store so history is identical.
    private func finalizeLocal() {
        defer {
            isStreaming = false
            streamTask = nil
        }
        stopFlushLoop()
        flushStreamingText() // land the last ≤33ms of deltas before closing
        guard let index = messages.indices.last, messages[index].isStreaming else { return }
        if messages[index].content.isEmpty {
            messages.remove(at: index)
        } else {
            messages[index].isStreaming = false
            persistAssistant(content: messages[index].content)
        }
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
                    ChatMessage(role: $0.role, content: $0.content, createdAt: $0.createdAt, attachments: $0.attachments)
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
