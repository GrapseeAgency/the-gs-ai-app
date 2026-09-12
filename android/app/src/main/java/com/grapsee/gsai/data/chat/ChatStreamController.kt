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
 * Threading: [Dispatchers.Main.immediate] — the stream text publishes into
 * Compose-observable state read by the UI, so deltas must land on Main, and
 * `immediate` avoids a needless frame hop when already there (e.g. the Stop
 * button's cancel on the UI thread).
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
     */
    data class StreamState(
        val conversationId: String?,
        val assistantMessageId: String,
        val streamText: String,
        val phase: Phase,
        val error: String? = null
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
     * Only one stream at a time: if another is somehow still active it is
     * cancelled and finalized exactly like a navigation-away.
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
        _state.value = StreamState(
            conversationId = conversationId,
            assistantMessageId = token,
            streamText = "",
            phase = Phase.Streaming
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
     * phase lets the observing screen commit the full buffered text on screen.
     */
    fun cancelAndFinalize() {
        job?.cancel()
        job = null
    }

    /**
     * Ownership-guarded publish: a stream may only mutate the state it started.
     * This is what makes the one-stream-per-app eviction race-free — the evicted
     * job's terminal publish (posted after the new stream's initial publish)
     * must never clobber the newer state.
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
