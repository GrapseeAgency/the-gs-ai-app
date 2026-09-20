package com.grapsee.gsai.data.chat

import android.os.SystemClock
import com.grapsee.gsai.data.attachment.AttachmentDraft
import com.grapsee.gsai.data.repository.ChatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * App-scoped owner of THE chat stream (Task 86-d, closing the 85-d deferred item).
 *
 * Historically the streaming Job lived in ChatScreen's composition: rotating the
 * phone mid-stream cancelled the scope, the repository's NonCancellable finalize
 * persisted the partial, and the answer simply stopped growing. Now the stream
 * is owned HERE — one instance in [com.grapsee.gsai.di.ServiceLocator] — so:
 *  - a configuration change kills nothing; the recomposed screen re-attaches by
 *    observing [state] (the pending→adopted conversation id included),
 *  - navigating away is an explicit [cancelAndFinalize] from the screen, which
 *    preserves the old product behaviour (the repository's NonCancellable path
 *    persists the partial),
 *  - only one stream exists process-wide; starting a new one finalizes the old.
 *
 * The markdown pipeline is untouched: this class only carries the stream text
 * VALUE; parseStreamingSegments / StreamParseCache / SegmentedContent keep
 * operating on it wherever it comes from.
 *
 * PHASE 8.1 (docs/search-event-protocol.md v1, FROZEN): the search-chain SSE
 * events are consumed here and published as first-class [StreamState] fields —
 * [StreamState.searchPhase] (the honest orb driver), [StreamState.traceItems]
 * (one entry per REAL received event, no timers, no fabricated steps),
 * [StreamState.sources] (live source cards, replaced 1:1 by the persisted
 * sources on `done`) and [StreamState.clarify] (quick-choices for an ambiguous
 * request). Every event application below is a pure StreamState transform;
 * nothing here invents state the wire did not carry.
 *
 * Threading: [Dispatchers.Main.immediate] — the stream text publishes into
 * Compose-observable state read by the UI, so deltas must land on Main, and
 * `immediate` avoids a needless frame hop when already there (e.g. the Stop
 * button's cancel on the UI thread). All search events arrive on the same
 * dispatcher (they are read inside the same send coroutine), so the
 * [SearchStreamAccumulator] below needs no locking.
 */
class ChatStreamController(private val chat: ChatRepository) {

    enum class Phase {
        /** Consuming the repository stream; deltas coalesce into [StreamState.streamText] at ~30 Hz. */
        Streaming,
        /** Stream consumed, terminal commit pending — transient between the last two publishes. */
        Finalizing,
        /** Terminal success (or a landed failure — see [StreamState.error]). Persisted by the repository. */
        Done,
        /** Terminal cancel (Stop button, navigation-away, one-stream-per-app eviction). Partial persisted. */
        Cancelled
    }

    /**
     * The whole live-stream snapshot. Null when no stream ran this process.
     * [conversationId] starts as the screen's active id (null for a brand-new
     * chat), and is updated the moment the repository resolves the real id —
     * the pending→adopted transition happens mid-stream, not at the end.
     *
     * PHASE 6/7: the hasAttachments flag records whether THIS request
     * carried any attachments
     * — a real fact of the real request, consumed by the honest
     * orb mapping (vision analysis / document extraction → WORKING) and nothing else.
     *
     * PHASE 8.1 additions (all derived ONLY from received events):
     *  - [searchPhase] mirrors the wire statuses: "searching" | "working" |
     *    "composing" | "search_failed" — the honest orb mapping consumes it
     *    (searching → SEARCHING, working → WORKING); null for plain turns.
     *  - [traceItems] is one entry per real search-chain event, in arrival
     *    order; the trace card renders it verbatim and collapses to
     *    [searchSummary] (+ the failed flag) once the turn settles.
     *  - [sources] grows live from `source` events; on `done` it is replaced
     *    1:1 by the persisted sources of the message (the authoritative list).
     *  - [clarify] is the parsed clarify payload (live event or persisted
     *    clarifyOptions on the done message).
     */
    data class StreamState(
        val conversationId: String?,
        val assistantMessageId: String,
        val streamText: String,
        val phase: Phase,
        val error: String? = null,
        val hasAttachments: Boolean = false,
        /** PHASE 8.1: last known wire status of the search chain (null = no search this turn). */
        val searchPhase: String? = null,
        /** PHASE 8.1: real received search-chain steps, in arrival order. */
        val traceItems: List<SearchTraceItem> = emptyList(),
        /** PHASE 8.1: live source cards; final value = the persisted done sources. */
        val sources: List<SourceCard> = emptyList(),
        /** PHASE 8.1: clarify quick-choices for an ambiguous request. */
        val clarify: ClarifyPrompt? = null,
        /** PHASE 8.1: terminal `search completed` counts (null = never completed). */
        val searchSummary: SearchSummary? = null
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<StreamState?>(null)
    val state: StateFlow<StreamState?> = _state.asStateFlow()

    private var job: Job? = null

    /** True while a stream is in flight — the send/edit/regenerate in-flight gate. */
    val isStreaming: Boolean get() = job?.isActive == true

    /**
     * Start streaming one assistant turn. The caller (ChatScreen.dispatch) has
     * already echoed the user turn + added the streaming bubble; persistence of
     * the user turn happens inside [ChatRepository.send] BEFORE the network
     * stream opens, so a rotation mid-stream reloads a transcript containing it.
     *
     * PHASE 5 (additive only): [attachments] forwards the composer's ready
     * attachment drafts to the repository — the ids travel on the send request
     * and the records serialize into the persisted user turn. The streaming
     * engine itself is untouched.
     *
     * PHASE 8.1: the search-chain events are parsed (tolerantly, see
     * SearchEvents.kt) and folded into the StreamState by the
     * [SearchStreamAccumulator] — one accumulator per stream, owned by this
     * launch closure exactly like the delta buffer. Only one stream at a time:
     * if another is somehow still active it is cancelled and finalized exactly
     * like a navigation-away.
     */
    fun start(
        conversationId: String?,
        prompt: String,
        modelId: String?,
        assistantMessageId: String,
        attachments: List<AttachmentDraft> = emptyList()
    ) {
        if (isStreaming) cancelAndFinalize()
        val token = assistantMessageId
        val buffer = StringBuilder()
        var lastPaint = 0L
        val search = SearchStreamAccumulator()
        _state.value = StreamState(
            conversationId = conversationId,
            assistantMessageId = token,
            streamText = "",
            phase = Phase.Streaming,
            hasAttachments = attachments.isNotEmpty()
        )
        job = scope.launch {
            try {
                val returnedId = chat.send(
                    conversationId = conversationId,
                    content = prompt,
                    modelId = modelId,
                    attachments = attachments,
                    onConversationResolved = { id ->
                        publishIfMine(token) { it.copy(conversationId = id) }
                    },
                    // --- PHASE 8.1 search-chain events (protocol v1) -----------
                    onStatus = { status ->
                        publishIfMine(token) { search.applyStatus(it, status) }
                    },
                    onSearchEvent = { payload ->
                        parseSearchEventPayload(payload)?.let { event ->
                            publishIfMine(token) { search.applySearch(it, event) }
                        }
                    },
                    onSourceEvent = { payload ->
                        parseSourceEventPayload(payload)?.let { event ->
                            publishIfMine(token) { search.applySource(it, event) }
                        }
                    },
                    onClarify = { payload ->
                        parseClarifyPayload(payload)?.let { clarify ->
                            publishIfMine(token) { it.copy(clarify = clarify) }
                        }
                    },
                    onDelta = { delta ->
                        // One growing buffer for the whole streamed answer —
                        // per-chunk concatenation re-allocated the entire prefix
                        // on every delta (quadratic over a long stream). Deltas
                        // only coalesce into the paint state at ~30 Hz.
                        buffer.append(delta)
                        val now = SystemClock.uptimeMillis()
                        if (now - lastPaint >= STREAM_PAINT_MS) {
                            lastPaint = now
                            publishIfMine(token) { it.copy(streamText = buffer.toString()) }
                        }
                    },
                    // The done message's persisted sources[] are the AUTHORITATIVE
                    // card list (cited ordinals only, with the used flag) — they
                    // replace the live event-built list 1:1. clarifyOptions is the
                    // fallback when the live clarify event was missed.
                    onDone = { done ->
                        publishIfMine(token) { current ->
                            val cards = done?.sources?.map { it.toSourceCard() }
                            current.copy(
                                sources = if (cards.isNullOrEmpty()) current.sources else cards,
                                clarify = current.clarify
                                    ?: done?.clarifyOptions?.let { parseClarifyPayload(it) }
                            )
                        }
                    }
                )
                publishIfMine(token) { it.copy(streamText = buffer.toString(), phase = Phase.Finalizing) }
                publishIfMine(token) { it.copy(conversationId = returnedId, phase = Phase.Done) }
            } catch (ce: CancellationException) {
                // Stop-generation / navigation-away land here: the repository
                // already persisted the partial (NonCancellable); publish the
                // full buffered text so the screen commits what is on disk.
                publishIfMine(token) { it.copy(streamText = buffer.toString(), phase = Phase.Cancelled) }
                throw ce
            } catch (e: Exception) {
                // Safety net only — the repository lands offline turns itself.
                // The screen decides how an error state presents (quietly).
                publishIfMine(token) {
                    it.copy(
                        streamText = buffer.toString(),
                        phase = Phase.Done,
                        error = e.message ?: e.javaClass.simpleName
                    )
                }
            } finally {
                if (job === coroutineContext[Job]) job = null
            }
        }
    }

    /**
     * Cooperative stop that preserves the old finalize contract: the cancelled
     * repository job persists the partial on disk (NonCancellable), the Cancelled
     * phase commits the full buffered text on screen.
     */
    fun cancelAndFinalize() {
        job?.cancel()
        job = null
    }

    /**
     * Ownership-guarded publish: a stream may only mutate the state it started.
     * This is what makes the one-stream-per-app eviction race-free — the evicted
     * job's terminal publish (posted after the new stream's initial publish)
     * must never clobber the newer state. The transform runs ONLY when the
     * token matches, so the accumulator's in-flight bookkeeping below is never
     * mutated by a foreign stream's events.
     */
    private fun publishIfMine(token: String, transform: (StreamState) -> StreamState) {
        _state.update { current ->
            if (current?.assistantMessageId == token) transform(current) else current
        }
    }

    companion object {
        /** Streaming repaint coalescing: network deltas arrive far faster than the
         *  eye; repainting markdown at delta cadence re-parses and re-lays-out the
         *  growing bubble for zero visible benefit. ~30 Hz is visually identical
         *  to per-delta and caps the streaming cost per second. */
        const val STREAM_PAINT_MS = 33L
    }
}

/**
 * Per-stream search-event accumulator — the single place where wire events
 * become trace lines, source cards and the honest search phase. One instance
 * lives per stream (captured by the start() launch closure); it holds only the
 * in-flight retrieval bookkeeping the wire does not restate on every event.
 *
 * Honest-state decisions (binding, docs/search-event-protocol.md §honest-state):
 *  - a trace line exists BECAUSE an event arrived — no event, no line;
 *  - "working" is entered by a real `source opening/reading` event and left
 *    when the last in-flight retrieval settles (completed/failed) — never
 *    guessed from silence;
 *  - composing/failed trace lines are de-duplicated (the wire may repeat the
 *    status); query/results/round/per-source lines are distinct events and
 *    each earns its own line.
 */
private class SearchStreamAccumulator {

    /** Ordinals with a retrieval genuinely in flight (opening/reading seen). */
    private val inFlight = HashSet<Int>()

    fun applyStatus(state: ChatStreamController.StreamState, status: String): ChatStreamController.StreamState =
        when (status) {
            "searching" -> state.copy(searchPhase = "searching")
            "working" -> state.copy(searchPhase = "working")
            "composing" -> state.copy(
                searchPhase = "composing",
                traceItems = if (state.traceItems.any { it.kind == SearchTraceItem.Kind.Composing }) {
                    state.traceItems
                } else {
                    state.traceItems + SearchTraceItem(SearchTraceItem.Kind.Composing, "Composing the answer")
                }
            )
            "search_failed" -> state.copy(
                searchPhase = "search_failed",
                traceItems = if (state.traceItems.any { it.kind == SearchTraceItem.Kind.SearchFailed }) {
                    state.traceItems
                } else {
                    state.traceItems + SearchTraceItem(SearchTraceItem.Kind.SearchFailed, "Search failed")
                }
            )
            else -> state
        }

    // Block body: two branches guard with an early `return state` (an
    // expression-body `when` prohibits bare returns).
    fun applySearch(
        state: ChatStreamController.StreamState,
        event: SearchEvent
    ): ChatStreamController.StreamState {
        return when (event) {
        is SearchEvent.Started -> {
            // The real intent label (or raw intent) is the only honest opener;
            // without one there is nothing to show — no line is invented.
            val label = event.label ?: event.intent ?: return state
            state.copy(
                searchPhase = "searching",
                traceItems = state.traceItems + SearchTraceItem(SearchTraceItem.Kind.SearchStarted, label)
            )
        }
        is SearchEvent.Query -> state.copy(
            searchPhase = "searching",
            traceItems = state.traceItems + SearchTraceItem(
                SearchTraceItem.Kind.Query,
                "Search — ${event.query}",
                round = event.round
            )
        )
        is SearchEvent.Results ->
            if (event.found <= 0) state
            else state.copy(
                traceItems = state.traceItems + SearchTraceItem(
                    SearchTraceItem.Kind.Results,
                    "Found ${event.found} results",
                    round = event.round
                )
            )
        is SearchEvent.Round -> {
            if (event.sourcesVerified <= 0 && event.sourcesFailed <= 0) return state
            val text = buildString {
                append(event.sourcesVerified)
                append(" sources verified")
                if (event.sourcesFailed > 0) append(", ${event.sourcesFailed} failed")
            }
            state.copy(
                traceItems = state.traceItems + SearchTraceItem(
                    SearchTraceItem.Kind.RoundVerified,
                    text,
                    round = event.round
                )
            )
        }
        is SearchEvent.Failed -> state.copy(
            searchPhase = "search_failed",
            traceItems = if (state.traceItems.any { it.kind == SearchTraceItem.Kind.SearchFailed }) {
                state.traceItems
            } else {
                state.traceItems + SearchTraceItem(
                    SearchTraceItem.Kind.SearchFailed,
                    "Search failed" + (event.reason?.let { " — $it" } ?: "")
                )
            }
        )
        is SearchEvent.Completed -> state.copy(
            searchSummary = SearchSummary(
                queries = event.queries,
                sources = event.sources,
                retrieved = event.retrieved,
                usedCitations = event.usedCitations
            ),
            traceItems = state.traceItems + SearchTraceItem(
                SearchTraceItem.Kind.Completed,
                "${event.sources} sources · ${event.retrieved} read"
            )
        )
        }
    }

    // Block body (same reason as [applySearch]).
    fun applySource(
        state: ChatStreamController.StreamState,
        event: SourceEvent
    ): ChatStreamController.StreamState {
        return when (event) {
        is SourceEvent.Discovered -> state.copy(sources = upsertCard(state.sources, event.source))
        is SourceEvent.Opening -> {
            // Fetch about to start — bookkeeping only; the `reading` event (real
            // fetch started) earns the visible "Reading …" line.
            if (!inFlight.add(event.ordinal)) return state
            state.copy(searchPhase = "working")
        }
        is SourceEvent.Reading -> {
            val isNew = inFlight.add(event.ordinal)
            val domain = state.sources.firstOrNull { it.ordinal == event.ordinal }?.domain
                ?: "source ${event.ordinal}"
            val line = SearchTraceItem(SearchTraceItem.Kind.Reading, "Reading $domain")
            state.copy(
                searchPhase = "working",
                traceItems = if (isNew || state.traceItems.lastOrNull() != line) {
                    state.traceItems + line
                } else {
                    state.traceItems
                }
            )
        }
        is SourceEvent.Completed -> {
            inFlight.remove(event.ordinal)
            state.copy(
                searchPhase = if (inFlight.isEmpty()) "searching" else "working",
                sources = updateCard(state.sources, event.ordinal) { card ->
                    card.copy(
                        status = event.status ?: "retrieved",
                        publishedDate = event.publishedDate ?: card.publishedDate
                    )
                },
                traceItems = state.traceItems + readLine(state, event.ordinal, "Read")
            )
        }
        is SourceEvent.Failed -> {
            inFlight.remove(event.ordinal)
            state.copy(
                searchPhase = if (inFlight.isEmpty()) "searching" else "working",
                sources = updateCard(state.sources, event.ordinal) { card ->
                    card.copy(status = event.status ?: "failed")
                },
                traceItems = state.traceItems + SearchTraceItem(
                    SearchTraceItem.Kind.SourceFailed,
                    "Failed ${domainOf(state, event.ordinal)}"
                )
            )
        }
        is SourceEvent.Skipped -> state.copy(
            sources = updateCard(state.sources, event.ordinal) { card ->
                card.copy(status = event.status ?: "skipped")
            },
            traceItems = state.traceItems + SearchTraceItem(
                SearchTraceItem.Kind.SourceSkipped,
                "Skipped ${domainOf(state, event.ordinal)}" +
                    (event.reason?.let { " — $it" } ?: "")
            )
        )
        }
    }

    /** "Read <domain> — <title>" — the title comes from the discovered card (real wire data). */
    private fun readLine(
        state: ChatStreamController.StreamState,
        ordinal: Int,
        verb: String
    ): SearchTraceItem {
        val card = state.sources.firstOrNull { it.ordinal == ordinal }
        val title = card?.title?.takeIf { it.isNotBlank() && it != card.domain }
        val text = "Read ${card?.domain ?: "source $ordinal"}" + (title?.let { " — $it" } ?: "")
        return SearchTraceItem(SearchTraceItem.Kind.Read, text)
    }

    private fun domainOf(state: ChatStreamController.StreamState, ordinal: Int): String =
        state.sources.firstOrNull { it.ordinal == ordinal }?.domain ?: "source $ordinal"

    private fun upsertCard(sources: List<SourceCard>, card: SourceCard): List<SourceCard> {
        val index = sources.indexOfFirst { it.ordinal == card.ordinal }
        return if (index < 0) sources + card
        else sources.toMutableList().also { it[index] = sources[index].mergeFrom(card) }
    }

    private fun updateCard(
        sources: List<SourceCard>,
        ordinal: Int,
        transform: (SourceCard) -> SourceCard
    ): List<SourceCard> {
        val index = sources.indexOfFirst { it.ordinal == ordinal }
        if (index < 0) return sources
        return sources.toMutableList().also { it[index] = transform(sources[index]) }
    }

    /** A repeat `discovered` for a known ordinal never demotes a settled status. */
    private fun SourceCard.mergeFrom(next: SourceCard): SourceCard = copy(
        title = next.title.ifBlank { title },
        url = next.url.ifBlank { url },
        domain = next.domain.ifBlank { domain },
        snippet = next.snippet.ifBlank { snippet },
        publishedDate = next.publishedDate ?: publishedDate,
        status = if (status == "discovered") next.status else status
    )
}
