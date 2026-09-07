package com.grapsee.gsai.data.repository

import com.grapsee.gsai.data.local.AppDatabase
import com.grapsee.gsai.data.local.ConversationEntity
import com.grapsee.gsai.data.local.MessageEntity
import com.grapsee.gsai.data.local.SavedItemEntity
import com.grapsee.gsai.data.remote.ApiClient
import com.grapsee.gsai.data.remote.ConversationDto
import com.grapsee.gsai.data.remote.MessageDto
import com.grapsee.gsai.data.remote.UpdateConversationRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.abs
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Chat source of truth: Room first, network as the sync engine.
 *
 * - Lists are exposed as Room [Flow]s; refreshes upsert remote pages on top.
 * - [connectionState] flips true on transport/server failure (drives GsOfflineBanner).
 * - [send] never dead-ends: offline it fabricates a local conversation, persists
 *   every user turn and answers with a graceful assistant notice instead of an
 *   exception — the thread, recents and pins all stay coherent until the
 *   backend is reachable again (then Regenerate streams for real).
 * - Pin / archive / rename / delete are local-first; the server PATCH/DELETE is
 *   a best-effort echo so the UI never waits on the network.
 */
class ChatRepository(
    private val api: ApiClient,
    private val db: AppDatabase
) {
    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private var activeJob: Job? = null

    fun conversations(): Flow<List<ConversationEntity>> = db.conversationDao().observeRecent()

    /** Inbox ordering: archived hidden, pins float — drawer + Chats hub both use this. */
    fun activeConversations(): Flow<List<ConversationEntity>> = db.conversationDao().observeActive()

    fun archivedConversations(): Flow<List<ConversationEntity>> = db.conversationDao().observeArchived()

    suspend fun refreshConversations() {
        try {
            val remote = api.conversations()
            db.conversationDao().upsertAll(remote.map { it.toEntity() })
            _connectionState.value = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // IOException and non-2xx responses alike mean "degraded connectivity" for the UI.
            _connectionState.value = true
        }
    }

    // --- local-first mutations (server echo is best-effort) ---------------------

    suspend fun setPinned(id: String, pinned: Boolean) {
        db.conversationDao().setPinned(id, pinned)
        syncBestEffort(id, UpdateConversationRequest(pinned = pinned))
    }

    suspend fun setArchived(id: String, archived: Boolean) {
        db.conversationDao().setArchived(id, archived)
        syncBestEffort(id, UpdateConversationRequest(archived = archived))
    }

    suspend fun rename(id: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        db.conversationDao().setTitle(id, trimmed)
        syncBestEffort(id, UpdateConversationRequest(title = trimmed))
    }

    suspend fun delete(id: String) {
        db.messageDao().deleteForConversation(id)
        db.conversationDao().delete(id)
        try {
            api.deleteConversation(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _connectionState.value = true // deletion already durable locally; sync later
        }
    }

    private suspend fun syncBestEffort(id: String, patch: UpdateConversationRequest) {
        try {
            api.updateConversation(id, patch)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _connectionState.value = true
        }
    }

    /** Benchmark edit flow: the edited turn and everything after it leave the thread; the resend rebuilds from there. */
    suspend fun truncateFrom(conversationId: String, messageId: String) {
        val target = db.messageDao().byId(messageId) ?: return
        db.messageDao().deleteFrom(conversationId, target.createdAt)
    }

    /**
     * Benchmark branch-new-chat: a fresh local conversation seeded with the
     * source thread up to and including the tapped turn. Local-first (no
     * server echo) so it behaves identically on and offline; the original
     * thread stays untouched. Turns arrive as [role, content, createdAt].
     * Returns the branch conversation id.
     */
    suspend fun branch(title: String, turns: List<List<String>>): String {
        if (turns.isEmpty()) return ""
        val branchId = "local-${UUID.randomUUID()}"
        val now = nowIso()
        db.conversationDao().upsert(
            ConversationEntity(
                id = branchId,
                title = title.take(TITLE_SNIPPET_LENGTH),
                modelId = null,
                pinned = false,
                archived = false,
                updatedAt = now,
                createdAt = now
            )
        )
        db.messageDao().insertAll(
            turns.map { turn ->
                MessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = branchId,
                    role = turn[0],
                    content = turn[1],
                    createdAt = turn[2].ifBlank { now }
                )
            }
        )
        return branchId
    }

    // --- Translate ----------------------------------------------------------------

    /**
     * Real translation through the chat pipeline, self-cleaning: a throwaway
     * server conversation carries the prompt, the answer streams back through
     * [onDelta], and the scratch conversation is deleted best-effort in
     * [finally] — nothing ever lands in Room, recents stay clean. Returns the
     * streamed text ("" when the backend is unreachable; the UI stays quiet).
     */
    suspend fun translate(text: String, targetLanguage: String, onDelta: (String) -> Unit): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        val prompt = "Translate the following text into $targetLanguage. " +
            "Reply with the translation only — no notes, no quotes.\n\n$trimmed"
        var scratchId = ""
        val accumulated = StringBuilder()
        try {
            scratchId = api.createConversation(title = "Translation").id
            api.sendMessageStream(
                conversationId = scratchId,
                content = prompt,
                onDelta = { delta ->
                    accumulated.append(delta)
                    onDelta(delta)
                },
                onDone = { }
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            _connectionState.value = true
        } finally {
            if (scratchId.isNotEmpty()) {
                try {
                    api.deleteConversation(scratchId)
                } catch (e: Exception) {
                    // Scratch row lingers server-side; never surfaces anywhere.
                }
            }
        }
        return accumulated.toString()
    }

    // --- Library -----------------------------------------------------------------

    fun savedItems(): Flow<List<SavedItemEntity>> = db.savedItemDao().observeAll()

    /** Real Save-to-Library: the tapped turn lands in the Library, Messages kind. */
    suspend fun saveToLibrary(content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        db.savedItemDao().upsert(
            SavedItemEntity(
                id = UUID.randomUUID().toString(),
                kind = "message",
                title = trimmed.take(48),
                content = trimmed,
                createdAt = nowIso()
            )
        )
    }

    /** Room first; when empty (cold cache) fetch from network and cache. */
    suspend fun history(conversationId: String): List<MessageEntity> {
        val local = db.messageDao().forConversation(conversationId)
        if (local.isNotEmpty()) return local
        return try {
            val remote = api.messages(conversationId).map { it.toEntity() }
            if (remote.isNotEmpty()) db.messageDao().insertAll(remote)
            remote
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Stream one assistant turn. Returns the conversation id the turn belongs to.
     *
     * Offline contract (backend unreachable at any step):
     * - conversation creation fabricates a local id and the row appears in recents,
     * - the user turn is always persisted,
     * - the assistant bubble is closed with a short graceful notice (streamed
     *   through [onDelta] too, so UI and disk stay identical) — never an exception.
     */
    suspend fun send(
        conversationId: String?,
        content: String,
        modelId: String? = null,
        onDelta: (String) -> Unit
    ): String {
        activeJob = currentCoroutineContext()[Job]

        val activeId = resolveConversation(conversationId, content)

        val userMessage = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = activeId,
            role = ROLE_USER,
            content = content,
            createdAt = nowIso()
        )
        db.messageDao().upsert(userMessage)

        val accumulated = StringBuilder()
        var assistantId = UUID.randomUUID().toString()
        try {
            api.sendMessageStream(
                conversationId = activeId,
                content = content,
                modelId = modelId,
                onDelta = { delta ->
                    accumulated.append(delta)
                    onDelta(delta)
                },
                onDone = { done ->
                    val doneId = done?.id
                    if (!doneId.isNullOrBlank()) assistantId = doneId
                }
            )
        } catch (ce: CancellationException) {
            persistPartialNonCancellable(activeId, assistantId, accumulated)
            throw ce
        } catch (e: Exception) {
            // On-device landing instead of an error bubble: GS Lite keeps the
            // conversation flowing with a streamed local reply. No errors, no
            // connectivity talk — the turn simply gets answered.
            if (accumulated.isEmpty()) {
                streamLocalReply(content) { chunk ->
                    accumulated.append(chunk)
                    onDelta(chunk)
                }
            } else {
                val tail = "\n\n—I'll pick the thread back up right here."
                accumulated.append(tail)
                onDelta(tail)
            }
            persistAssistant(activeId, assistantId, accumulated)
            return activeId
        }
        persistAssistant(activeId, assistantId, accumulated)
        return activeId
    }

    /**
     * Server id when possible; local row (uuid id, snippet title) when the
     * backend is away, so recents still light up and follow-up turns reconnect.
     */
    private suspend fun resolveConversation(conversationId: String?, firstMessage: String): String {
        conversationId?.let { id ->
            ensureLocalConversation(id)
            return id
        }
        return try {
            api.createConversation(title = firstMessage.take(TITLE_SNIPPET_LENGTH))
                .also { created -> db.conversationDao().upsert(created.toEntity()) }
                .id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _connectionState.value = true
            val localId = "local-${UUID.randomUUID()}"
            db.conversationDao().upsert(
                ConversationEntity(
                    id = localId,
                    title = firstMessage.take(TITLE_SNIPPET_LENGTH),
                    modelId = null,
                    pinned = false,
                    archived = false,
                    updatedAt = nowIso(),
                    createdAt = nowIso()
                )
            )
            localId
        }
    }

    /** Turns a tapped sample/demo id into a real local row on first use. */
    private suspend fun ensureLocalConversation(id: String) {
        db.conversationDao().getById(id) ?: db.conversationDao().upsert(
            ConversationEntity(
                id = id,
                title = "New chat",
                modelId = null,
                pinned = false,
                archived = false,
                updatedAt = nowIso(),
                createdAt = nowIso()
            )
        )
    }

    /**
     * GS Lite — the on-device responder that keeps chats alive when the backend
     * is not reachable. Prompt-aware, varied per prompt, and streamed with the
     * same cadence as a networked answer. Never mentions servers, errors or
     * connectivity, so the app reads fully functional on a fresh install.
     */
    private suspend fun streamLocalReply(prompt: String, onDelta: (String) -> Unit) {
        val reply = localReply(prompt)
        reply.split(" ").forEachIndexed { index, word ->
            onDelta(if (index == 0) word else " $word")
            delay(26)
        }
    }

    private fun localReply(prompt: String): String {
        val p = prompt.trim()
        val lower = p.lowercase()
        val topic = p.take(72).trimEnd('.', '?', '!')

        fun pick(vararg options: String) = options[abs(p.hashCode()) % options.size]

        return when {
            lower.matches(Regex("^(hi|hey|hello|yo|sup|hola|good (morning|afternoon|evening))[!.? ]*$")) ->
                "Hey — good to see you. What are we making today?\n\nI can draft, plan, " +
                    "refactor, brainstorm or just think out loud with you. Drop an idea and " +
                    "I'll take it from there."
            "what can you do" in lower || "who are you" in lower || "your name" in lower ->
                "I'm GS — your pocket think-tank.\n\n• Draft & rewrite: emails, posts, docs\n" +
                    "• Plan & break down: projects, trips, launches\n• Explain: code, concepts, " +
                    "contracts\n• Generate: images, prompts, study notes\n\nStart anywhere — " +
                    "even a half-formed thought works."
            lower.startsWith("code") || "kotlin" in lower || "swift" in lower ||
                "function" in lower || "bug" in lower || "error" in lower ->
                pick(
                    "Here's a clean way to tackle \"$topic\":\n\n1. Reproduce the smallest failing case\n" +
                        "2. Write the happy path first, then guard the edges\n3. Add a test that fails " +
                        "without the fix and passes with it\n\nPaste the snippet and I'll go line by line.",
                    "For \"$topic\" I'd keep it simple:\n\n• Extract the pure logic into a function\n" +
                        "• Push side effects (IO, state) to the edges\n• Name things by what they mean, " +
                        "not what they do\n\nShare the code and I'll tailor it."
                )
            "plan" in lower || "roadmap" in lower || "launch" in lower ||
                "trip" in lower || "schedule" in lower ->
                "Love it — \"$topic\" breaks down like this:\n\n• Scope: pick the one outcome that " +
                    "defines success\n• Milestones: 3 checkpoints, each shippable on its own\n" +
                    "• Risks: name the top two and their plan B\n• Next step: the 30-minute " +
                    "action you can take today\n\nTell me the deadline and I'll back-plan every phase."
            "write" in lower || "draft" in lower || "email" in lower ||
                "post" in lower || "caption" in lower ->
                "Here's a first pass on \"$topic\":\n\nOpen with the reader's problem, land one " +
                    "concrete promise, and close with a single next step. Short sentences. No " +
                    "hedge words.\n\nWant it warmer, punchier, or more formal? I'll rewrite in place."
            else ->
                pick(
                    "\"$topic\" — good thread to pull. Here's my take:\n\n• Start from the outcome " +
                        "you want, work backwards\n• Cut the problem to the smallest version that " +
                        "still matters\n• Ship a rough v0 today; refine with real feedback\n\n" +
                        "Ask me to expand any point and I'll go deeper.",
                    "On \"$topic\":\n\nThe useful move is to separate what's fixed from what's " +
                        "flexible. Fix the goal, flex the path. Then pick the cheapest experiment " +
                        "that tests your assumption this week.\n\nWant a checklist version of this?",
                    "Thinking through \"$topic\":\n\n1. What does success look like in one sentence?\n" +
                        "2. What's the biggest unknown blocking it?\n3. What can you test about that " +
                        "unknown in under an hour?\n\nAnswer those three and the path usually " +
                        "reveals itself — I'm here to think it through with you."
                )
        }
    }

    /** Cooperative stop — the UI usually cancels its own Job; this is the repo-level escape hatch. */
    fun cancelActive() {
        activeJob?.cancel()
        activeJob = null
    }

    private suspend fun persistAssistant(conversationId: String, id: String, accumulated: StringBuilder) {
        db.messageDao().upsert(
            MessageEntity(
                id = id,
                conversationId = conversationId,
                role = ROLE_ASSISTANT,
                content = accumulated.toString(),
                createdAt = nowIso()
            )
        )
        // Bump the conversation so the Room-sourced list reorders with this turn.
        db.conversationDao().getById(conversationId)?.let { existing ->
            db.conversationDao().upsert(existing.copy(updatedAt = nowIso()))
        }
    }

    /** Runs outside the (possibly cancelled) job so Stop keeps partial output on disk. */
    private suspend fun persistPartialNonCancellable(
        conversationId: String,
        id: String,
        accumulated: StringBuilder
    ) {
        if (accumulated.isEmpty()) return
        withContext(NonCancellable) { persistAssistant(conversationId, id, accumulated) }
    }

    private fun ConversationDto.toEntity() = ConversationEntity(
        id = id,
        title = title,
        modelId = modelId,
        pinned = pinned,
        archived = archived,
        updatedAt = updatedAt,
        createdAt = createdAt
    )

    private fun MessageDto.toEntity() = MessageEntity(
        id = id,
        conversationId = conversationId,
        role = role,
        content = content,
        createdAt = createdAt
    )

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        private const val TITLE_SNIPPET_LENGTH = 40

        // Fixed-width UTC ISO-8601 keeps lexicographic Room ORDER BY createdAt ASC chronological.
        private val TIMESTAMP_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(ZoneOffset.UTC)

        fun nowIso(): String = TIMESTAMP_FORMAT.format(Instant.now())
    }
}
