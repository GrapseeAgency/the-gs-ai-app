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

// MARK: - Search sources + clarify (PHASE 8.1 + 8.2 v2 — docs/search-event-protocol.md)

/// One persisted search source on an assistant message (the protocol's
/// MessageJson `sources[]` addition). `ordinal` IS the citation number the
/// answer's `[N]` markers refer to. Tolerant decode follows the house
/// pattern: a missing/malformed field degrades to a default instead of
/// failing the whole message decode — older servers carry no sources at all.
struct MessageSource: Codable, Equatable, Hashable {
    let id: String
    let ordinal: Int
    var title: String
    var url: String
    var domain: String
    var snippet: String
    var publishedDate: String?
    var query: String?
    var retrievedAt: String?
    /// discovered | retrieved | snippet_only | failed | skipped | used
    var status: String?
    /// true = cited in the final answer (server-computed; never guessed)
    var used: Bool?
    var rank: Int?
    /// PHASE 8.2 (v2 protocol): news | general | reference | academic | book |
    /// primary. Absent on older servers decodes to nil.
    var sourceType: String?
    /// PHASE 8.2 (v2 protocol): primary | academic | reputable | reference |
    /// discovery. Absent on older servers decodes to nil.
    var authority: String?
    /// PHASE 8.2 (v2 protocol): when this source syndicates another's
    /// coverage, the ordinal of the representative source it duplicates
    /// (null = original). Absent on older servers decodes to nil.
    var syndicatedOf: Int?

    init(
        id: String = "",
        ordinal: Int,
        title: String = "",
        url: String = "",
        domain: String = "",
        snippet: String = "",
        publishedDate: String? = nil,
        query: String? = nil,
        retrievedAt: String? = nil,
        status: String? = nil,
        used: Bool? = nil,
        rank: Int? = nil,
        sourceType: String? = nil,
        authority: String? = nil,
        syndicatedOf: Int? = nil
    ) {
        self.id = id
        self.ordinal = ordinal
        self.title = title
        self.url = url
        self.domain = domain
        self.snippet = snippet
        self.publishedDate = publishedDate
        self.query = query
        self.retrievedAt = retrievedAt
        self.status = status
        self.used = used
        self.rank = rank
        self.sourceType = sourceType
        self.authority = authority
        self.syndicatedOf = syndicatedOf
    }

    private enum CodingKeys: String, CodingKey {
        case id, ordinal, title, url, domain, snippet
        case publishedDate, query, retrievedAt, status, used, rank
        case sourceType, authority, syndicatedOf
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decodeIfPresent(String.self, forKey: .id) ?? ""
        ordinal = try container.decodeIfPresent(Int.self, forKey: .ordinal) ?? 0
        title = try container.decodeIfPresent(String.self, forKey: .title) ?? ""
        url = try container.decodeIfPresent(String.self, forKey: .url) ?? ""
        domain = try container.decodeIfPresent(String.self, forKey: .domain) ?? ""
        snippet = try container.decodeIfPresent(String.self, forKey: .snippet) ?? ""
        publishedDate = try container.decodeIfPresent(String.self, forKey: .publishedDate)
        query = try container.decodeIfPresent(String.self, forKey: .query)
        retrievedAt = try container.decodeIfPresent(String.self, forKey: .retrievedAt)
        status = try container.decodeIfPresent(String.self, forKey: .status)
        used = try container.decodeIfPresent(Bool.self, forKey: .used)
        rank = try container.decodeIfPresent(Int.self, forKey: .rank)
        sourceType = try container.decodeIfPresent(String.self, forKey: .sourceType)
        authority = try container.decodeIfPresent(String.self, forKey: .authority)
        syndicatedOf = try container.decodeIfPresent(Int.self, forKey: .syndicatedOf)
    }
}

/// One quick-choice of a clarify turn (`{"id","label"}`).
struct ClarifyOption: Codable, Identifiable, Equatable, Hashable {
    let id: String
    let label: String

    init(id: String, label: String) {
        self.id = id
        self.label = label
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decodeIfPresent(String.self, forKey: .id)
            ?? (try container.decodeIfPresent(String.self, forKey: .label) ?? "")
        label = try container.decodeIfPresent(String.self, forKey: .label)
            ?? (try container.decodeIfPresent(String.self, forKey: .id) ?? "")
    }
}

/// The clarify payload (`{"question","options":[…]}`) — arrives inside the
/// `clarify` SSE event and travels on the persisted Message as the
/// `clarifyOptions` JSON string, so reloads re-render the chips.
struct ClarifyPrompt: Equatable, Hashable {
    let question: String
    let options: [ClarifyOption]

    init(question: String, options: [ClarifyOption]) {
        self.question = question
        self.options = options
    }

    /// Tolerant read of the double-encoded payload: nil when the string is
    /// not a clarify object or the question is empty.
    init?(jsonString: String) {
        guard let data = jsonString.data(using: .utf8) else { return nil }
        struct Payload: Decodable {
            var question: String?
            var options: [ClarifyOption]?
        }
        guard let payload = try? JSONDecoder().decode(Payload.self, from: data),
              let question = payload.question, !question.isEmpty else { return nil }
        self.question = question
        self.options = payload.options ?? []
    }

    /// The persisted `clarifyOptions` string (same JSON shape the events and
    /// the server carry, so a backfilled prompt round-trips unchanged).
    var jsonString: String {
        struct Payload: Encodable {
            var question: String
            var options: [ClarifyOption]
        }
        let payload = Payload(question: question, options: options)
        return (try? JSONEncoder().encode(payload)).flatMap { String(data: $0, encoding: .utf8) } ?? ""
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
    /// PHASE 8.1: the search sources persisted with the assistant turn
    /// (cited ordinals only, server-side). Absent on the wire (older
    /// servers, non-search turns) decodes to nil.
    var sources: [MessageSource]?
    /// PHASE 8.1: on clarify turns the persisted message carries the clarify
    /// payload as a JSON string (same shape the `clarify` event sends), so a
    /// reloaded thread re-renders the quick-choice chips.
    var clarifyOptions: String?

    init(
        id: String,
        conversationId: String,
        role: String,
        content: String,
        createdAt: String,
        attachments: [Attachment]? = nil,
        sources: [MessageSource]? = nil,
        clarifyOptions: String? = nil
    ) {
        self.id = id
        self.conversationId = conversationId
        self.role = role
        self.content = content
        self.createdAt = createdAt
        self.attachments = attachments
        self.sources = sources
        self.clarifyOptions = clarifyOptions
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decodeIfPresent(String.self, forKey: .id) ?? ""
        conversationId = try container.decodeIfPresent(String.self, forKey: .conversationId) ?? ""
        role = try container.decodeIfPresent(String.self, forKey: .role) ?? "assistant"
        content = try container.decodeIfPresent(String.self, forKey: .content) ?? ""
        createdAt = try container.decodeIfPresent(String.self, forKey: .createdAt) ?? ""
        attachments = try container.decodeIfPresent([Attachment].self, forKey: .attachments)
        sources = try container.decodeIfPresent([MessageSource].self, forKey: .sources)
        clarifyOptions = try container.decodeIfPresent(String.self, forKey: .clarifyOptions)
    }
}

// MARK: - Request bodies

/// `#/components/schemas/SendMessageInput`
struct SendMessageRequest: Codable, Equatable {
    var content: String
    var stream: Bool
    /// PHASE 5: uploaded attachment ids (max 6). Synthesized encoding uses
    /// encodeIfPresent, so nil keeps text-only sends byte-compatible with the
    /// pre-attachments wire shape. `content` may be empty only when this
    /// array is non-empty (server rule).
    var attachments: [String]?

    init(content: String, stream: Bool = false, attachments: [String]? = nil) {
        self.content = content
        self.stream = stream
        self.attachments = attachments
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        content = try container.decodeIfPresent(String.self, forKey: .content) ?? ""
        stream = try container.decodeIfPresent(Bool.self, forKey: .stream) ?? false
        attachments = try container.decodeIfPresent([String].self, forKey: .attachments)
    }
}

/// `#/components/schemas/CreateConversationInput`
struct CreateConversationRequest: Codable, Equatable {
    var title: String?

    init(title: String?) {
        self.title = title
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        title = try container.decodeIfPresent(String.self, forKey: .title)
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

/// `#/components/schemas/Error` — used to surface `{"code","message"}` bodies.
struct APIErrorEnvelope: Codable, Equatable {
    var code: String?
    var message: String
}
