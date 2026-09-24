package com.grapsee.gsai.data.remote

import com.grapsee.gsai.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.serialization.json.Json

/**
 * FORENSIC AUDIT [5] — failure class (c) BACKEND_ERROR: the server answered with
 * an error status. Carries the status code and the SERVER'S SANITIZED error
 * message (the backend's userFacingTurnError text) so the repository can label
 * the failure with the real reason instead of a fake "unreachable" claim.
 */
class GsBackendException(
    val status: Int,
    /** Sanitized server error text, or null when the body carried none. */
    val serverMessage: String?
) : Exception(serverMessage ?: "GS backend error (HTTP $status)")

/**
 * Thin typed client for the GS AI backend contract (shared-contracts/openapi.yaml v0.1.0).
 *
 * JSON endpoints read the raw response text and decode with [GsApiJson] — String is in
 * Ktor's default ignored-types set, so the installed ContentNegotiation plugin never
 * intercepts these reads. The SSE stream is consumed straight from the byte channel
 * (ByteReadChannel is likewise ignored) so `data:` lines arrive line-by-line, untouched.
 */
class ApiClient(
    private val client: HttpClient,
    baseUrl: String = BuildConfig.BASE_URL
) {
    private val root = baseUrl.trimEnd('/') + "/api/v1"

    suspend fun conversations(limit: Int = 30): List<ConversationDto> {
        val response = client.get("$root/conversations") { parameter("limit", limit) }
        return decodeList(
            response.bodyAsText(),
            fromEnvelope = { GsApiJson.decodeFromString<ConversationListDto>(it).items },
            fromArray = { GsApiJson.decodeFromString<List<ConversationDto>>(it) }
        )
    }

    suspend fun createConversation(title: String?): ConversationDto {
        val response = client.post("$root/conversations") {
            contentType(ContentType.Application.Json)
            setBody(CreateConversationRequest(title = title))
        }
        return GsApiJson.decodeFromString<ConversationDto>(response.bodyAsText())
    }

    /** Null when the conversation does not exist (404) or the body cannot be parsed. */
    suspend fun conversation(id: String): ConversationDto? {
        val response = client.get("$root/conversations/$id")
        if (!response.status.isSuccess()) return null
        return runCatching {
            GsApiJson.decodeFromString<ConversationDto>(response.bodyAsText())
        }.getOrNull()
    }

    suspend fun deleteConversation(id: String): Boolean =
        client.delete("$root/conversations/$id").status.isSuccess()

    /**
     * Pin / archive / rename. The server is the echo, not the gate: callers
     * already applied the change locally and only sync here (best-effort).
     */
    suspend fun updateConversation(id: String, patch: UpdateConversationRequest): Boolean =
        client.patch("$root/conversations/$id") {
            contentType(ContentType.Application.Json)
            setBody(patch)
        }.status.isSuccess()

    suspend fun messages(id: String): List<MessageDto> {
        val response = client.get("$root/conversations/$id/messages")
        return decodeList(
            response.bodyAsText(),
            fromEnvelope = { GsApiJson.decodeFromString<MessageListDto>(it).items },
            fromArray = { GsApiJson.decodeFromString<List<MessageDto>>(it) }
        )
    }

    /**
     * POST a user message with stream=true and walk the SSE body.
     * Wire format: `data: {"event":"delta","data":"…"}` for each chunk,
     * `data: {"event":"done","data":"<json Message>"}` as the terminal event.
     * An `error` event (or a non-2xx status) throws; the caller decides how to surface it.
     *
     * PHASE 5: [attachments] carries the server attachment ids (uploaded via
     * /api/v1/uploads first). explicitNulls=false keeps null OFF the wire, so a
     * plain-text send serializes byte-identically to the pre-attachments contract.
     *
     * PHASE 8.1 (docs/search-event-protocol.md v1, additive): the search chain
     * events are forwarded raw and the client stays silent on unknown event
     * names — the exact tolerance the protocol promises old clients:
     *  - [onStatus] — payload is the plain status string
     *    (searching | working | composing | search_failed),
     *  - [onSearchEvent] / [onSourceEvent] / [onClarifyEvent] — the payload is
     *    a JSON-encoded object STRING (double-encoded, like `done`); parsing is
     *    the stream layer's job, this walker never interprets it.
     *
     * PHASE 8.2 (docs/search-event-protocol.md v2, additive): the new
     * `research` event name is routed the same way to [onResearchEvent]. The
     * v2 `search`/`source` payload subtypes (`engines` / `read` / `evidence`)
     * need no walker change — they arrive on the existing event names and are
     * the stream layer's parsing job. All new callbacks default to no-op so
     * every existing call site keeps its exact meaning.
     */
    suspend fun sendMessageStream(
        conversationId: String,
        content: String,
        attachments: List<String>? = null,
        /** FLASH MODE (Phase 3): 'flash' | 'thinking' | 'auto'; null omits the field. */
        mode: String? = null,
        onDelta: (String) -> Unit,
        onDone: (MessageDto?) -> Unit,
        onStatus: (String) -> Unit = {},
        onSearchEvent: (String) -> Unit = {},
        onSourceEvent: (String) -> Unit = {},
        onClarifyEvent: (String) -> Unit = {},
        onResearchEvent: (String) -> Unit = {}
    ) {
        val response = client.post("$root/conversations/$conversationId/messages") {
            contentType(ContentType.Application.Json)
            setBody(
                SendMessageRequest(
                    content = content,
                    stream = true,
                    attachments = attachments?.ifEmpty { null },
                    mode = mode
                )
            )
        }
        // FORENSIC AUDIT [5]: a non-2xx here is BACKEND_ERROR, not "offline".
        // Surface the server's sanitized error message instead of reading an
        // error JSON body as if it were an SSE stream.
        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrDefault("")
            val serverMessage = runCatching {
                GsApiJson.decodeFromString<ErrorResponseDto>(body).error
            }.getOrNull()?.takeIf { it.isNotBlank() }
            throw GsBackendException(status = response.status.value, serverMessage = serverMessage)
        }
        val channel = response.bodyAsChannel()
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            val trimmed = line.trim()
            if (!trimmed.startsWith("data:")) continue
            val payload = trimmed.removePrefix("data:").trim()
            if (payload.isEmpty()) continue
            val event = runCatching { GsApiJson.decodeFromString<SseEvent>(payload) }.getOrNull() ?: continue
            when (event.event) {
                "delta" -> onDelta(event.data.orEmpty())
                "status" -> onStatus(event.data.orEmpty())
                // PHASE 8.1: search-chain payloads are double-encoded JSON strings —
                // forward untouched; an empty payload is ignored, never forwarded.
                "search" -> if (!event.data.isNullOrBlank()) onSearchEvent(event.data)
                "source" -> if (!event.data.isNullOrBlank()) onSourceEvent(event.data)
                "clarify" -> if (!event.data.isNullOrBlank()) onClarifyEvent(event.data)
                // PHASE 8.2: research-level events (§research) — same double-encoded
                // payload shape, same empty-payload tolerance.
                "research" -> if (!event.data.isNullOrBlank()) onResearchEvent(event.data)
                "done" -> {
                    val message = event.data?.let { data ->
                        runCatching { GsApiJson.decodeFromString<MessageDto>(data) }.getOrNull()
                    }
                    onDone(message)
                }
                "error" -> throw IllegalStateException(event.data ?: "Stream error from backend")
            }
        }
    }

    /** Sanitized backend error payload ({"error": "..."}). */
    @kotlinx.serialization.Serializable
    private data class ErrorResponseDto(val error: String? = null)

    /** Contract returns `{ items: [...] }`; tolerate a bare `[...]` from minimal backends. */
    private inline fun <T> decodeList(
        body: String,
        fromEnvelope: (String) -> List<T>,
        fromArray: (String) -> List<T>
    ): List<T> =
        runCatching { fromEnvelope(body) }.getOrElse { fromArray(body) }
}
