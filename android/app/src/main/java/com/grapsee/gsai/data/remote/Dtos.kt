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
    val createdAt: String,
    /** PHASE 5: attachment records on the message (max 6). Tolerant default keeps
     *  every pre-attachments payload — history pages, SSE done events — decoding
     *  unchanged (ignoreUnknownKeys + default for the absent key). */
    val attachments: List<AttachmentDto> = emptyList()
)

/**
 * PHASE 5 wire shape of one uploaded attachment — exact server JSON keys
 * (docs/ATTACHMENTS.md §1): {id, kind, displayName, mimeType, byteSize,
 * createdAt, url}. `url` is the server-relative "/api/v1/files/<id>";
 * resolve against the configured base URL, never render it as an absolute link.
 */
@Serializable
data class AttachmentDto(
    val id: String,
    val kind: String,
    val displayName: String,
    val mimeType: String,
    val byteSize: Long = 0,
    val createdAt: String = "",
    val url: String = ""
)

/** POST /api/v1/uploads response envelope: `{ "attachment": {…} }`. */
@Serializable
data class UploadResponseDto(
    val attachment: AttachmentDto
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
    val modelId: String? = null,
    /** PHASE 5: server attachment ids uploaded via /api/v1/uploads first.
     *  explicitNulls=false keeps null OFF the wire — plain-text sends stay
     *  byte-identical to the pre-attachments contract. */
    val attachments: List<String>? = null
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
