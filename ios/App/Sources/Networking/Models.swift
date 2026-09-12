import Foundation

// MARK: - Conversation (`#/components/schemas/Conversation`, openapi v0.1.0)

struct Conversation: Codable, Identifiable, Equatable, Hashable {
    let id: String
    var title: String
    var modelId: String?
    var pinned: Bool?
    var archived: Bool?
    var createdAt: String
    var updatedAt: String

    init(
        id: String,
        title: String,
        modelId: String? = nil,
        pinned: Bool? = nil,
        archived: Bool? = nil,
        createdAt: String,
        updatedAt: String
    ) {
        self.id = id
        self.title = title
        self.modelId = modelId
        self.pinned = pinned
        self.archived = archived
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }

    /// Tolerant decode: missing/`null` optional-able fields fall back to
    /// sensible defaults instead of failing the whole page decode.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decodeIfPresent(String.self, forKey: .id) ?? ""
        title = try container.decodeIfPresent(String.self, forKey: .title) ?? "Untitled"
        modelId = try container.decodeIfPresent(String.self, forKey: .modelId)
        pinned = try container.decodeIfPresent(Bool.self, forKey: .pinned)
        archived = try container.decodeIfPresent(Bool.self, forKey: .archived)
        createdAt = try container.decodeIfPresent(String.self, forKey: .createdAt) ?? ""
        updatedAt = try container.decodeIfPresent(String.self, forKey: .updatedAt) ?? ""
    }
}

// MARK: - Attachment (PHASE 5 — docs/ATTACHMENTS.md §1 wire shape)

/// Server-side attachment record, exactly the JSON the uploads endpoint
/// returns: {id, kind, displayName, mimeType, byteSize, createdAt, url}.
/// `url` is the RELATIVE "/api/v1/files/<id>" — resolve against the configured
/// base URL, never render it as an absolute remote address. Tolerant decoding
/// follows the house pattern: a malformed field degrades to a default instead
/// of failing the whole message decode.
struct Attachment: Codable, Identifiable, Equatable, Hashable {
    let id: String
    var kind: String        // "image" | "pdf" | "document"
    var displayName: String
    var mimeType: String
    var byteSize: Int
    var createdAt: String
    var url: String

    init(
        id: String,
        kind: String,
        displayName: String,
        mimeType: String,
        byteSize: Int,
        createdAt: String,
        url: String
    ) {
        self.id = id
        self.kind = kind
        self.displayName = displayName
        self.mimeType = mimeType
        self.byteSize = byteSize
        self.createdAt = createdAt
        self.url = url
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decodeIfPresent(String.self, forKey: .id) ?? ""
        kind = try container.decodeIfPresent(String.self, forKey: .kind) ?? "document"
        displayName = try container.decodeIfPresent(String.self, forKey: .displayName) ?? "attachment"
        mimeType = try container.decodeIfPresent(String.self, forKey: .mimeType) ?? "application/octet-stream"
        byteSize = try container.decodeIfPresent(Int.self, forKey: .byteSize) ?? 0
        createdAt = try container.decodeIfPresent(String.self, forKey: .createdAt) ?? ""
        url = try container.decodeIfPresent(String.self, forKey: .url) ?? ""
    }
}

// MARK: - Message (`#/components/schemas/Message`)

struct Message: Codable, Identifiable, Equatable, Hashable {
    let id: String
    var conversationId: String
    var role: String        // "user" | "assistant" | "system" | "tool"
    var content: String
    var createdAt: String
    /// PHASE 5: up to 6 attachments on user turns; assistant messages carry
    /// none today. Absent on the wire (older servers) decodes to nil.
    var attachments: [Attachment]?

    init(
        id: String,
        conversationId: String,
        role: String,
        content: String,
        createdAt: String,
        attachments: [Attachment]? = nil
    ) {
        self.id = id
        self.conversationId = conversationId
        self.role = role
        self.content = content
        self.createdAt = createdAt
        self.attachments = attachments
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decodeIfPresent(String.self, forKey: .id) ?? ""
        conversationId = try container.decodeIfPresent(String.self, forKey: .conversationId) ?? ""
        role = try container.decodeIfPresent(String.self, forKey: .role) ?? "assistant"
        content = try container.decodeIfPresent(String.self, forKey: .content) ?? ""
        createdAt = try container.decodeIfPresent(String.self, forKey: .createdAt) ?? ""
        attachments = try container.decodeIfPresent([Attachment].self, forKey: .attachments)
    }
}

// MARK: - Request bodies

/// `#/components/schemas/SendMessageInput`
struct SendMessageRequest: Codable, Equatable {
    var content: String
    var stream: Bool
    var modelId: String?
    /// PHASE 5: uploaded attachment ids (max 6). Synthesized encoding uses
    /// encodeIfPresent, so nil keeps text-only sends byte-compatible with the
    /// pre-attachments wire shape. `content` may be empty only when this
    /// array is non-empty (server rule).
    var attachments: [String]?

    init(content: String, stream: Bool = false, modelId: String? = nil, attachments: [String]? = nil) {
        self.content = content
        self.stream = stream
        self.modelId = modelId
        self.attachments = attachments
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        content = try container.decodeIfPresent(String.self, forKey: .content) ?? ""
        stream = try container.decodeIfPresent(Bool.self, forKey: .stream) ?? false
        modelId = try container.decodeIfPresent(String.self, forKey: .modelId)
        attachments = try container.decodeIfPresent([String].self, forKey: .attachments)
    }
}

/// `#/components/schemas/CreateConversationInput`
struct CreateConversationRequest: Codable, Equatable {
    var title: String?
    var modelId: String?

    init(title: String?, modelId: String? = nil) {
        self.title = title
        self.modelId = modelId
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        title = try container.decodeIfPresent(String.self, forKey: .title)
        modelId = try container.decodeIfPresent(String.self, forKey: .modelId)
    }
}

/// PATCH /conversations/{id} body — synthesized encoding omits nil fields,
/// so only the flags the user actually touched travel on the wire.
struct UpdateConversationRequest: Codable, Equatable {
    var title: String?
    var pinned: Bool?
    var archived: Bool?

    init(title: String? = nil, pinned: Bool? = nil, archived: Bool? = nil) {
        self.title = title
        self.pinned = pinned
        self.archived = archived
    }
}

// MARK: - Model catalogue (`#/components/schemas/Model`)

struct ModelEntry: Codable, Identifiable, Equatable, Hashable {
    let id: String
    var displayName: String
    var capabilities: [String]?
    var contextWindow: Int?
    var speedTier: String?  // "fast" | "balanced" | "deep"

    init(
        id: String,
        displayName: String,
        capabilities: [String]? = nil,
        contextWindow: Int? = nil,
        speedTier: String? = nil
    ) {
        self.id = id
        self.displayName = displayName
        self.capabilities = capabilities
        self.contextWindow = contextWindow
        self.speedTier = speedTier
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decodeIfPresent(String.self, forKey: .id) ?? ""
        displayName = try container.decodeIfPresent(String.self, forKey: .displayName) ?? "Untitled model"
        capabilities = try container.decodeIfPresent([String].self, forKey: .capabilities)
        contextWindow = try container.decodeIfPresent(Int.self, forKey: .contextWindow)
        speedTier = try container.decodeIfPresent(String.self, forKey: .speedTier)
    }
}

// MARK: - SSE frame

/// One `data:` line of the event stream:
/// `data: {"event":"delta","data":"…chunk…"}` /
/// `data: {"event":"done","data":"{…message json…}"}` /
/// `data: {"event":"error","data":"…"}`.
struct SseEvent: Codable, Equatable {
    var event: String
    var data: String?

    init(event: String, data: String?) {
        self.event = event
        self.data = data
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        event = try container.decodeIfPresent(String.self, forKey: .event) ?? ""
        data = try container.decodeIfPresent(String.self, forKey: .data)
    }
}

// MARK: - List envelopes

struct ConversationListEnvelope: Codable, Equatable {
    var items: [Conversation]
    var nextCursor: String?
}

struct MessageListEnvelope: Codable, Equatable {
    var items: [Message]
}

struct ModelListEnvelope: Codable, Equatable {
    var models: [ModelEntry]
}

/// `#/components/schemas/Error` — used to surface `{"code","message"}` bodies.
struct APIErrorEnvelope: Codable, Equatable {
    var code: String?
    var message: String
}
