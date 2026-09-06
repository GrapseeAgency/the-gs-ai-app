package com.grapsee.gsai.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Single JSON configuration for the whole chat pipeline (client + SSE parsing).
 * - ignoreUnknownKeys: contract evolves server-side without breaking old clients.
 * - encodeDefaults: `stream: true` must always travel on SendMessageRequest
 *   (server default is false — omitting it would silently disable streaming).
 * - explicitNulls: null fields (title?, modelId?) are omitted instead of sent
 *   as explicit nulls, matching the optional-property semantics of openapi.yaml.
 */
val GsApiJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

@Serializable
data class ConversationDto(
    val id: String,
    val title: String,
    val modelId: String? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class MessageDto(
    val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val createdAt: String
)

@Serializable
data class CreateConversationRequest(
    val title: String? = null,
    val modelId: String? = null
)

@Serializable
data class SendMessageRequest(
    val content: String,
    val stream: Boolean = true,
    val modelId: String? = null
)

/**
 * PATCH /conversations/{id} body. Nullable fields are omitted (explicitNulls=false),
 * so only the flags the user actually touched travel on the wire.
 */
@Serializable
data class UpdateConversationRequest(
    val title: String? = null,
    val pinned: Boolean? = null,
    val archived: Boolean? = null
)

@Serializable
data class ModelDto(
    val id: String,
    val displayName: String,
    val capabilities: List<String> = emptyList(),
    val contextWindow: Int? = null,
    val speedTier: String? = null
)

/** One server-sent event line: `data: {"event":"delta","data":"…"}`. */
@Serializable
data class SseEvent(
    val event: String,
    val data: String? = null
)

/**
 * List envelopes from openapi.yaml (ConversationList / MessageList / ModelList).
 * Kept internal to this package; ApiClient tolerates bare arrays as a fallback.
 */
@Serializable
internal data class ConversationListDto(
    val items: List<ConversationDto> = emptyList()
)

@Serializable
internal data class MessageListDto(
    val items: List<MessageDto> = emptyList()
)

@Serializable
internal data class ModelListDto(
    val models: List<ModelDto> = emptyList()
)
