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

// MARK: - Message (`#/components/schemas/Message`)

struct Message: Codable, Identifiable, Equatable, Hashable {
    let id: String
    var conversationId: String
    var role: String        // "user" | "assistant" | "system" | "tool"
    var content: String
    var createdAt: String

    init(id: String, conversationId: String, role: String, content: String, createdAt: String) {
        self.id = id
        self.conversationId = conversationId
        self.role = role
        self.content = content
        self.createdAt = createdAt
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decodeIfPresent(String.self, forKey: .id) ?? ""
        conversationId = try container.decodeIfPresent(String.self, forKey: .conversationId) ?? ""
        role = try container.decodeIfPresent(String.self, forKey: .role) ?? "assistant"
        content = try container.decodeIfPresent(String.self, forKey: .content) ?? ""
        createdAt = try container.decodeIfPresent(String.self, forKey: .createdAt) ?? ""
    }
}

// MARK: - Request bodies

/// `#/components/schemas/SendMessageInput`
struct SendMessageRequest: Codable, Equatable {
    var content: String
    var stream: Bool
    var modelId: String?

    init(content: String, stream: Bool = false, modelId: String? = nil) {
        self.content = content
        self.stream = stream
        self.modelId = modelId
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        content = try container.decodeIfPresent(String.self, forKey: .content) ?? ""
        stream = try container.decodeIfPresent(Bool.self, forKey: .stream) ?? false
        modelId = try container.decodeIfPresent(String.self, forKey: .modelId)
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
