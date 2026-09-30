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
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json

/**
 * FORENSIC AUDIT [5] — failure class (c) BACKEND_ERROR: the server answered with
 * an error status. Carries the status code and the SERVER'S SANITIZED error
 * message (the backend's userFacingTurnError text) so the repository can label
 * the failure with the real reason instead of a fake "unreachable" claim.
 */
/**
 * The provider answered with 2xx and its body then DIED.
 *
 * Added for run 36779646507, which measured a user-visible lie:
 *
 *     Classify: mid-stream cut    -> — GS backend error (HTTP 0) —
 *     Classify: server saw 1 request(s)
 *
 * One request WAS served and one SSE delta WAS written by the server, so the
 * backend was reachable and had begun answering. The user was shown "GS backend
 * error (HTTP 0)" — a status code that does not exist, describing a failure that
 * did not happen, for a provider that was talking.
 *
 * WHY THE OLD CODE COULD NOT SEE IT. `classifySendFailure` decided between
 * MidStreamCut and BackendError on `receivedAnyEvent`, which flips when a delta
 * reaches the UI. On an abrupt close CIO discards the partial chunked buffer, so
 * the frame that arrived is never completed into a line, the callback never runs,
 * and the flag stays false. The information that distinguishes the two cases --
 * did the response headers arrive, and were they 2xx -- was true and available
 * here, and was simply not being passed on. A 750ms pause before the close (the
 * honest shape of a mid-stream cut) did not help, which is what established that
 * this is a wall and not a race.
 *
 * So the signal is taken at the one place that has it. A failure to READ THE NEXT
 * LINE, after a 2xx, is a cut by definition: the provider accepted the request and
 * the answer stopped arriving. Nothing else in this function can raise that.
 */
class GsStreamCutException(
    /** The 2xx status whose body was cut, so a caller can log what arrived. */
    val status: Int,
    cause: Throwable,
) : Exception("provider stream cut after HTTP $status", cause)

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
        /** FLASH-MODE BUG 1: the first event of every turn — raw JSON payload
         *  {"state":"streaming","effectiveMode":"flash"|"thinking",...}.
         *  Forwarded raw; the controller parses it tolerantly. */
        onMode: (String) -> Unit = {},
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
        // ==========================================================
        // A 2xx WHOSE BODY DIED IS A CUT. THE SCOPE IS EVERY TRANSPORT
        // OPERATION, NOT JUST THE ONE I GUESSED AT FIRST.
        // ==========================================================
        //
        // Run 36783468375 named the exception, which is what made this fixable:
        //
        //   Classify: mid-stream cut ->
        //     — EOFException: Chunked stream has ended unexpectedly: no chunk size —
        //   Classify: server saw 1 request(s)
        //
        // An EOFException is a plain java.io.IOException, so `catch (e: Exception)`
        // would have caught it -- if the read were where it was thrown. It was
        // not: the try I wrapped around `channel.readUTF8Line()` did not fire, and
        // the exception arrived at classifySendFailure unwrapped.
        //
        // I DO NOT KNOW WHICH CALL THREW, and the honest thing is to say so
        // rather than pick a fourth theory. What makes the fix correct is that it
        // no longer depends on knowing: EVERY operation that touches the transport
        // is inside the guard -- bodyAsChannel(), the isClosedForRead probe, and
        // the line read. Enumerating them one at a time is how two previous
        // attempts each looked correct and caught nothing.
        //
        // And this wider scope is the RIGHT definition anyway: a 2xx has arrived,
        // so the request succeeded; any failure from here on is the answer
        // stopping, whatever call noticed.
        val channel = try {
            response.bodyAsChannel()
        } catch (ce: CancellationException) {
            if (!coroutineContext.isActive) throw ce
            throw GsStreamCutException(status = response.status.value, cause = ce)
        } catch (e: Exception) {
            throw GsStreamCutException(status = response.status.value, cause = e)
        }

        try {
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
                    // FLASH-MODE BUG 1: first event of every turn carries the
                    // resolved effective mode. Ignored by pre-v0.70 parsers (no
                    // else branch here — unknown events are skipped silently).
                    "mode" -> if (!event.data.isNullOrBlank()) onMode(event.data)
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
                    // A SERVER-SENT ERROR IS NOT A TRANSPORT FAILURE, and it used to
                    // throw IllegalStateException, which the widened cut guard above
                    // would now have caught and called a mid-stream cut -- telling the
                    // user their connection dropped when the backend had answered with
                    // a refusal. So it throws the exception that means that, and it is
                    // in the guard's passthrough list.
                    //
                    // It also stops being an invented status. IllegalStateException
                    // matched none of classifySendFailure's shapes, so an SSE error
                    // rendered as "GS backend error (HTTP 0)" -- the same fabricated
                    // code as the cut, for the opposite reason. `status = 200` is
                    // literal: the response WAS a 200, and what arrived inside it was
                    // an error event carrying the server's own message.
                    "error" -> throw GsBackendException(
                        status = 200,
                        serverMessage = event.data ?: "Stream error from backend",
                    )
                }
            }
        } catch (ce: CancellationException) {
            // A cancelled TURN cancels this coroutine's Job, so the context is not
            // active and the rethrow is right. A peer that dropped the connection
            // leaves the Job running -- the user is still waiting -- so this is a
            // cut. There is no path where the user's own cancellation is
            // swallowed: `throw ce` still runs for it.
            //
            // Needed at BOTH scopes, because a Ktor channel closed by the peer
            // surfaces as a CancellationException whose Job is still active, and
            // treating that as a cancellation would send the turn down the
            // "user navigated away" path instead of the honest one.
            if (!coroutineContext.isActive) throw ce
            throw GsStreamCutException(status = response.status.value, cause = ce)
        } catch (e: Exception) {
            // Two exceptions already carry their own meaning and must keep it.
            //
            //   GsStreamCutException  already classified; re-wrapping would hide
            //                      the original cause one level deeper for no gain.
            //   GsBackendException    the server sent an SSE `error` EVENT. That is
            //                      a backend refusal, not a transport failure, and
            //                      calling it a cut would tell the user their
            //                      connection dropped when the server answered.
            if (e is GsStreamCutException || e is GsBackendException) throw e
            throw GsStreamCutException(status = response.status.value, cause = e)
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
