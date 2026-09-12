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

    suspend fun models(): List<ModelDto> {
        val response = client.get("$root/models")
        return decodeList(
            response.bodyAsText(),
            fromEnvelope = { GsApiJson.decodeFromString<ModelListDto>(it).models },
            fromArray = { GsApiJson.decodeFromString<List<ModelDto>>(it) }
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
     */
    suspend fun sendMessageStream(
        conversationId: String,
        content: String,
        modelId: String? = null,
        attachments: List<String>? = null,
        onDelta: (String) -> Unit,
        onDone: (MessageDto?) -> Unit
    ) {
        val response = client.post("$root/conversations/$conversationId/messages") {
            contentType(ContentType.Application.Json)
            setBody(
                SendMessageRequest(
                    content = content,
                    stream = true,
                    modelId = modelId,
                    attachments = attachments?.ifEmpty { null }
                )
            )
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

    /** Contract returns `{ items: [...] }`; tolerate a bare `[...]` from minimal backends. */
    private inline fun <T> decodeList(
        body: String,
        fromEnvelope: (String) -> List<T>,
        fromArray: (String) -> List<T>
    ): List<T> =
        runCatching { fromEnvelope(body) }.getOrElse { fromArray(body) }
}
