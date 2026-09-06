package com.grapsee.gsai.data.repository

import com.grapsee.gsai.data.local.AppDatabase
import com.grapsee.gsai.data.local.ConversationEntity
import com.grapsee.gsai.data.local.MessageEntity
import com.grapsee.gsai.data.remote.ApiClient
import com.grapsee.gsai.data.remote.ConversationDto
import com.grapsee.gsai.data.remote.MessageDto
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
 * - [send] creates the conversation on first message, persists the local user turn,
 *   streams the assistant reply through [onDelta] and persists the final message.
 *   Stop-generation is the caller's job: cancel the Job this send is running in —
 *   partial output is still persisted (NonCancellable) so history stays coherent.
 */
class ChatRepository(
    private val api: ApiClient,
    private val db: AppDatabase
) {
    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private var activeJob: Job? = null

    fun conversations(): Flow<List<ConversationEntity>> = db.conversationDao().observeRecent()

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
     * Stream one assistant turn. Returns the conversation id the turn belongs to —
     * creating the conversation server-side (titled from the message) when needed.
     */
    suspend fun send(
        conversationId: String?,
        content: String,
        modelId: String? = null,
        onDelta: (String) -> Unit
    ): String {
        activeJob = currentCoroutineContext()[Job]

        val activeId = conversationId
            ?: api.createConversation(title = content.take(TITLE_SNIPPET_LENGTH)).also { created ->
                db.conversationDao().upsert(created.toEntity())
            }.id

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
            persistPartial(activeId, assistantId, accumulated)
            throw e
        }
        persistAssistant(activeId, assistantId, accumulated)
        return activeId
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

    private suspend fun persistPartial(conversationId: String, id: String, accumulated: StringBuilder) {
        if (accumulated.isEmpty()) return
        persistAssistant(conversationId, id, accumulated)
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
