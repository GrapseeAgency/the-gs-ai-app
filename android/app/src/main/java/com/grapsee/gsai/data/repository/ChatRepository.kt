package com.grapsee.gsai.data.repository

import com.grapsee.gsai.data.local.AppDatabase
import com.grapsee.gsai.data.local.ConversationEntity
import com.grapsee.gsai.data.local.MessageEntity
import com.grapsee.gsai.data.remote.ApiClient
import com.grapsee.gsai.data.remote.ConversationDto
import com.grapsee.gsai.data.remote.MessageDto
import com.grapsee.gsai.data.remote.UpdateConversationRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
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
            // Graceful landing instead of an error bubble: same text to UI and disk.
            val notice = failureNotice(accumulated.isEmpty())
            accumulated.append(notice)
            onDelta(notice)
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

    private fun failureNotice(offlineLikely: Boolean): String = if (offlineLikely) {
        "I couldn't reach the GS servers just now — your message is saved in this " +
            "chat and will sync once you're back online.\n\nTap Regenerate to try again."
    } else {
        "\n\nThe connection dropped mid-answer — tap Regenerate to pick up where we left off."
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
