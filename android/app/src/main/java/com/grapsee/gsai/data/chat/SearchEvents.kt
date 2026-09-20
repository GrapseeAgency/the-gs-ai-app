package com.grapsee.gsai.data.chat

import androidx.compose.runtime.Immutable
import com.grapsee.gsai.data.remote.GsApiJson
import com.grapsee.gsai.data.remote.MessageSourceDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * PHASE 8.1 — search/research event models (docs/search-event-protocol.md v1, FROZEN).
 *
 * The backend emits `search`, `source` and `clarify` SSE events whose payloads
 * are JSON-encoded strings (double-encoded, exactly like the existing `done`
 * event). This file owns the CLIENT side of that contract:
 *
 *  - the UI models the stream controller publishes ([SearchTraceItem],
 *    [SourceCard], [ClarifyPrompt], [SearchSummary]),
 *  - tolerant payload parsers ([parseSearchEventPayload],
 *    [parseSourceEventPayload], [parseClarifyPayload]) — unknown event types and
 *    unknown/missing fields degrade to null/defaults, never a crash,
 *  - decode helpers for the persisted message JSON ([decodeSourceCards],
 *    [decodeClarifyPrompt]) so a reloaded thread re-renders source cards,
 *    citation chips and clarify quick-choices exactly as the live turn did.
 *
 * HONESTY CONTRACT (binding, from the protocol): every trace line and every
 * source card here originates from ONE received event or the persisted done
 * message. Nothing in this file synthesizes steps, timers or progress.
 */

// ---------------------------------------------------------------------------
// UI models (published inside ChatStreamController.StreamState)
// ---------------------------------------------------------------------------

/**
 * One line of the search trace. [kind] selects the honest glyph in the trace
 * card (✓ done, ↗ in flight, ✕ failed, – skipped); [text] is the human part,
 * composed at event-application time from the event's REAL fields.
 */
@Immutable
data class SearchTraceItem(
    val kind: Kind,
    val text: String,
    val round: Int = 0
) {
    enum class Kind {
        SearchStarted,
        Query,
        Results,
        Reading,
        Read,
        SourceFailed,
        SourceSkipped,
        RoundVerified,
        Composing,
        Completed,
        SearchFailed
    }
}

/**
 * One real web source. A live card is created by a `source discovered` event
 * and mutated by `completed`/`failed`/`skipped`; after `done` the list is
 * replaced 1:1 with the persisted sources (which carry the `used` flag).
 * [status] mirrors the protocol values: discovered | retrieved | snippet_only |
 * failed | skipped | used.
 */
@Immutable
data class SourceCard(
    val ordinal: Int,
    val title: String,
    val url: String,
    val domain: String,
    val snippet: String = "",
    val publishedDate: String? = null,
    val status: String = "discovered"
)

/** Clarify quick-choice: tapping it sends [label] as a normal user message. */
@Immutable
data class ClarifyOption(
    val id: String,
    val label: String
)

/** The assistant message of a clarify turn IS the clarification question. */
@Immutable
data class ClarifyPrompt(
    val question: String,
    val options: List<ClarifyOption>
)

/** Terminal `search completed` counts — the collapsed one-line summary source. */
@Immutable
data class SearchSummary(
    val queries: Int,
    val sources: Int,
    val retrieved: Int,
    val usedCitations: List<Int> = emptyList()
)

// ---------------------------------------------------------------------------
// Typed search/source events
// ---------------------------------------------------------------------------

/** `search` event variants (docs/search-event-protocol.md §search). */
sealed interface SearchEvent {
    data class Started(val intent: String?, val depth: String?, val label: String?) : SearchEvent
    data class Query(val round: Int, val query: String, val engines: List<String>) : SearchEvent
    data class Results(val round: Int, val query: String, val found: Int, val engines: List<String>) : SearchEvent
    data class Round(
        val round: Int,
        val sourcesVerified: Int,
        val sourcesFailed: Int,
        val verified: Boolean
    ) : SearchEvent

    data class Failed(val reason: String?) : SearchEvent
    data class Completed(
        val queries: Int,
        val sources: Int,
        val retrieved: Int,
        val usedCitations: List<Int>
    ) : SearchEvent
}

/** `source` event variants — one per REAL backend action on one source. */
sealed interface SourceEvent {
    data class Discovered(val source: SourceCard) : SourceEvent
    data class Opening(val ordinal: Int) : SourceEvent
    data class Reading(val ordinal: Int) : SourceEvent
    data class Completed(
        val ordinal: Int,
        val chars: Int?,
        val publishedDate: String?,
        val status: String?
    ) : SourceEvent

    data class Failed(val ordinal: Int, val reason: String?, val status: String?) : SourceEvent
    data class Skipped(val ordinal: Int, val reason: String?, val status: String?) : SourceEvent
}

// ---------------------------------------------------------------------------
// Tolerant payload parsers (SSE payloads + persisted message JSON)
// ---------------------------------------------------------------------------

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.intOrNull

private fun JsonObject.bool(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.strings(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

private fun JsonObject.ints(key: String): List<Int> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull } ?: emptyList()

/**
 * Parse a `search` event payload. Returns null for anything unrecognized —
 * an unknown `type` or malformed JSON simply produces no trace line (the
 * protocol is additive; forward compatibility is silence, never a crash).
 */
fun parseSearchEventPayload(payload: String): SearchEvent? = runCatching {
    val obj = GsApiJson.parseToJsonElement(payload).jsonObject
    when (obj.string("type")) {
        "started" -> SearchEvent.Started(
            intent = obj.string("intent"),
            depth = obj.string("depth"),
            label = obj.string("label")
        )
        "query" -> SearchEvent.Query(
            round = obj.int("round") ?: 0,
            query = obj.string("query") ?: return@runCatching null,
            engines = obj.strings("engines")
        )
        "results" -> SearchEvent.Results(
            round = obj.int("round") ?: 0,
            query = obj.string("query").orEmpty(),
            found = obj.int("found") ?: 0,
            engines = obj.strings("engines")
        )
        "round" -> SearchEvent.Round(
            round = obj.int("round") ?: 0,
            sourcesVerified = obj.int("sourcesVerified") ?: 0,
            sourcesFailed = obj.int("sourcesFailed") ?: 0,
            verified = obj.bool("verified") ?: true
        )
        "failed" -> SearchEvent.Failed(reason = obj.string("reason"))
        "completed" -> SearchEvent.Completed(
            queries = obj.int("queries") ?: 0,
            sources = obj.int("sources") ?: 0,
            retrieved = obj.int("retrieved") ?: 0,
            usedCitations = obj.ints("usedCitations")
        )
        else -> null
    }
}.getOrNull()

/**
 * Parse a `source` event payload. The `discovered` variant reuses
 * [MessageSourceDto]'s tolerant wire decoding (identical JSON shape), so both
 * the live event and the persisted message agree on one mapping.
 */
fun parseSourceEventPayload(payload: String): SourceEvent? = runCatching {
    val obj = GsApiJson.parseToJsonElement(payload).jsonObject
    when (obj.string("type")) {
        "discovered" -> {
            val element = obj["source"] as? JsonObject ?: return@runCatching null
            val dto = GsApiJson.decodeFromJsonElement(MessageSourceDto.serializer(), element)
            SourceEvent.Discovered(dto.toSourceCard())
        }
        "opening" -> SourceEvent.Opening(ordinal = obj.int("ordinal") ?: return@runCatching null)
        "reading" -> SourceEvent.Reading(ordinal = obj.int("ordinal") ?: return@runCatching null)
        "completed" -> SourceEvent.Completed(
            ordinal = obj.int("ordinal") ?: return@runCatching null,
            chars = obj.int("chars"),
            publishedDate = obj.string("publishedDate"),
            status = obj.string("status")
        )
        "failed" -> SourceEvent.Failed(
            ordinal = obj.int("ordinal") ?: return@runCatching null,
            reason = obj.string("reason"),
            status = obj.string("status")
        )
        "skipped" -> SourceEvent.Skipped(
            ordinal = obj.int("ordinal") ?: return@runCatching null,
            reason = obj.string("reason"),
            status = obj.string("status")
        )
        else -> null
    }
}.getOrNull()

/**
 * Parse the clarify payload — the same JSON travels twice: live on the
 * `clarify` event and persisted on the message as [com.grapsee.gsai.data.remote.MessageDto.clarifyOptions].
 * A prompt without a question or without options is not a usable prompt → null.
 */
fun parseClarifyPayload(payload: String?): ClarifyPrompt? {
    if (payload.isNullOrBlank()) return null
    return runCatching {
        val obj = GsApiJson.parseToJsonElement(payload).jsonObject
        val question = obj.string("question") ?: return@runCatching null
        val options = (obj["options"] as? JsonArray)?.mapNotNull { element ->
            val option = element as? JsonObject ?: return@mapNotNull null
            val id = option.string("id") ?: return@mapNotNull null
            ClarifyOption(id = id, label = option.string("label") ?: id)
        } ?: emptyList()
        if (options.isEmpty()) null else ClarifyPrompt(question = question, options = options)
    }.getOrNull()
}

/** Wire source record → UI card. `used` wins over `status` (cited in the answer). */
fun MessageSourceDto.toSourceCard(): SourceCard = SourceCard(
    ordinal = ordinal,
    title = title.ifBlank { domain },
    url = url,
    domain = domain,
    snippet = snippet,
    publishedDate = publishedDate,
    status = when {
        used == true -> "used"
        !status.isNullOrBlank() -> status
        else -> "discovered"
    }
)

/** Persisted message `sources` JSON column → cards (tolerant: corrupt → empty). */
fun decodeSourceCards(json: String?): List<SourceCard> {
    if (json.isNullOrBlank() || json == "[]") return emptyList()
    return runCatching {
        GsApiJson.decodeFromString<List<MessageSourceDto>>(json).map { it.toSourceCard() }
    }.getOrDefault(emptyList())
}

/** Persisted message `clarifyOptions` JSON column → prompt (tolerant: corrupt → null). */
fun decodeClarifyPrompt(json: String?): ClarifyPrompt? = parseClarifyPayload(json)
