import SwiftUI
import Combine

/**
 * Owns one conversation: history load (store first, then network), optimistic
 * send, SSE streaming with live accumulation, stop, retry/regenerate. Every
 * turn is persisted to `ConversationStore` so threads survive relaunches, and
 * a dead backend never dead-ends the thread — the assistant bubble closes
 * with a graceful saved-offline notice (never an error screen). All state
 * mutations happen on the main actor; the streaming connection itself runs on
 * URLSession's background plumbing and hops back via `Task { @MainActor in … }`.
 */
@MainActor
final class ChatViewModel: ObservableObject {

    // MARK: - Types

    struct ChatMessage: Identifiable {
        let id = UUID()
        let role: String
        var content: String
        var isStreaming: Bool = false
        var createdAt: String = ""
    }

    // MARK: - Published state

    @Published var messages: [ChatMessage] = []
    @Published var draft: String = ""
    @Published var isStreaming = false
    @Published var isLoadingHistory = false
    @Published var errorMessage: String?
    @Published var conversationID: String?

    // MARK: - Private state

    private var streamTask: Task<Void, Never>?
    private var lastSentText: String?
    private var streamingAccumulator = ""

    // MARK: - Init

    init(conversationID: String?) {
        self.conversationID = conversationID
        if let conversationID {
            loadHistory(conversationID: conversationID)
        }
    }

    deinit {
        streamTask?.cancel()
    }

    // MARK: - Intents

    func send() {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !isStreaming else { return }
        draft = ""
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
                createdAt: turn.createdAt.isEmpty ? fallbackStamp : turn.createdAt))
        }
        conversationID = conversation.id
        messages = turns
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

    private func beginStreaming(text: String, appendUserMessage: Bool) {
        errorMessage = nil
        if appendUserMessage {
            messages.append(ChatMessage(
                role: "user",
                content: text,
                createdAt: ConversationStore.now()))
        }
        messages.append(ChatMessage(
            role: "assistant",
            content: "",
            isStreaming: true,
            createdAt: ConversationStore.now()))
        streamingAccumulator = ""
        isStreaming = true

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
                        createdAt: ConversationStore.now()))
                }
                try await APIClient.shared.stream(
                    message: text,
                    conversationID: conversationID,
                    modelId: modelID,
                    onDelta: { delta in
                        Task { @MainActor in
                            self.streamingAccumulator += delta
                            self.updateLastStreaming(with: self.streamingAccumulator)
                        }
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
                } else {
                    // Graceful landing instead of an error banner: same text on
                    // screen and on disk, Regenerate picks it up when online.
                    let notice = (self.streamingAccumulator.isEmpty ? "" : "\n\n") +
                        "I couldn't reach the GS servers just now — your message is saved " +
                        "in this chat and will sync once you're back online.\n\nTap Regenerate to try again."
                    self.streamingAccumulator += notice
                    self.updateLastStreaming(with: self.streamingAccumulator)
                    self.finalizeLocal()
                }
            }
        }
    }

    /// A nil conversationID means "first message of a brand-new chat":
    /// create it server-side; offline, fabricate a local row so the thread,
    /// recents and pins keep working until the backend is reachable again.
    private func ensureConversation(for text: String) async throws -> String {
        if let conversationID { return conversationID }
        do {
            let conversation = try await APIClient.shared.createConversation(title: String(text.prefix(40)))
            conversationID = conversation.id
            return conversation.id
        } catch {
            let local = ConversationStore.shared.createLocalConversation(title: String(text.prefix(40)))
            conversationID = local.id
            return local.id
        }
    }

    private func updateLastStreaming(with content: String) {
        guard let index = messages.indices.last, messages[index].isStreaming else { return }
        messages[index].content = content
    }

    private func finalize(with message: Message?) {
        defer {
            isStreaming = false
            streamTask = nil
        }
        guard let message, !message.content.isEmpty else {
            finalizeLocal()
            return
        }
        guard let index = messages.indices.last, messages[index].isStreaming else { return }
        messages[index].content = message.content
        messages[index].isStreaming = false
        persistAssistant(content: message.content)
    }

    /// Keeps whatever streamed so far (offline notice included) and closes the
    /// bubble; the same text lands in the store so history is identical.
    private func finalizeLocal() {
        defer {
            isStreaming = false
            streamTask = nil
        }
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

    // MARK: - History

    private func loadHistory(conversationID: String) {
        // Store first — threads must survive relaunches even fully offline.
        let stored = ConversationStore.shared.messages(for: conversationID)
        if !stored.isEmpty {
            messages = stored.map { ChatMessage(role: $0.role, content: $0.content, createdAt: $0.createdAt) }
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
                self.messages = history.map { ChatMessage(role: $0.role, content: $0.content, createdAt: $0.createdAt) }
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

    // MARK: - Error helpers

    private static func isCancellation(_ error: Error) -> Bool {
        error is CancellationError || (error as? URLError)?.code == .cancelled
    }

    private static func friendly(_ error: Error) -> String {
        if let urlError = error as? URLError {
            switch urlError.code {
            case .notConnectedToInternet, .networkConnectionLost, .dataNotAllowed:
                return "You're offline — check your connection and try again."
            case .timedOut:
                return "The server took too long to respond."
            case .cannotFindHost, .cannotConnectToHost, .dnsLookupFailed:
                return "Can't reach the GS server right now."
            default:
                return urlError.localizedDescription
            }
        }
        if let apiError = error as? APIError, let description = apiError.errorDescription {
            return description
        }
        if error is DecodingError {
            return "The server sent something the app couldn't read."
        }
        return error.localizedDescription
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
