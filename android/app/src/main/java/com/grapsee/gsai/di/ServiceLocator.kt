package com.grapsee.gsai.di

import android.content.Context
import com.grapsee.gsai.data.SessionStore
import com.grapsee.gsai.data.attachment.AttachmentStore
import com.grapsee.gsai.data.chat.ChatStreamController
import com.grapsee.gsai.data.local.AppDatabase
import com.grapsee.gsai.data.remote.ApiClient
import com.grapsee.gsai.data.remote.GsApiJson
import com.grapsee.gsai.data.repository.ChatRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import java.util.UUID

/** Header REQUIRED by the platform edge (session affinity) — missing = 400. */
internal const val GS_SESSION_HEADER = "x-session-id"

/**
 * One HttpClient factory for the whole app. CIO engine keeps the stack
 * pure-Kotlin (JVM/Android friendly, easy to unit-test); 120s request timeout
 * lets SSE streams run long without the client cutting them off.
 *
 * TRANSPORT FIX: every request carries `x-session-id` — the platform edge
 * rejects ALL invocations without it ("header 'x-session-id' is required for
 * header field session affinity"), which surfaced to users as a bare
 * "Network error" on uploads. [engine] is overridable so tests capture the
 * emitted request with MockEngine.
 */
internal fun gsHttpClient(sessionId: String, engine: HttpClientEngine? = null): HttpClient =
    if (engine != null) HttpClient(engine) { configure(sessionId) }
    else HttpClient(CIO) { configure(sessionId) }

private fun io.ktor.client.HttpClientConfig<*>.configure(sessionId: String) {
    defaultRequest {
        headers.append(GS_SESSION_HEADER, sessionId)
    }
    install(ContentNegotiation) {
        json(GsApiJson)
    }
    install(HttpRequestRetry) {
        // Only 5xx get a second chance — connect failures (no backend
        // reachable) fall through instantly so GS Lite takes over.
        retryOnServerErrors(1)
        exponentialDelay()
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 120_000
        connectTimeoutMillis = 3_000
    }
}

/**
 * Manual dependency container (deliberately not Hilt — the chat data layer stays
 * trivially reachable from screens and preview-safe via runCatching). Initialised
 * once from GSApplication.onCreate.
 */
object ServiceLocator {

    lateinit var db: AppDatabase
    lateinit var api: ApiClient
    lateinit var chat: ChatRepository

    /**
     * PHASE 5: app-scoped attachment store — staging, real multipart upload,
     * retry and draft persistence for the composer. One instance per process,
     * so in-flight uploads and the live draft list survive rotation.
     */
    lateinit var attachments: AttachmentStore

    /**
     * App-scoped chat-stream owner (Task 86-d): the streaming Job lives here,
     * NOT in ChatScreen's composition — rotation mid-stream keeps the answer
     * growing; navigation away cancels + finalizes through the same instance.
     */
    lateinit var chatStream: ChatStreamController

    /**
     * Per-install affinity id. Seeded immediately (random) so a premature
     * `http` access still works; init() replaces it with the persisted value
     * BEFORE the lazy client is first touched (Application.onCreate ordering).
     */
    @Volatile
    private var clientId: String = UUID.randomUUID().toString()

    val http: HttpClient by lazy { gsHttpClient(clientId) }

    fun init(context: Context) {
        if (::db.isInitialized) return
        clientId = SessionStore.stableClientId(context)
        db = AppDatabase.build(context)
        api = ApiClient(http)
        chat = ChatRepository(api, db)
        chatStream = ChatStreamController(chat)
        attachments = AttachmentStore(http, context.applicationContext)
    }
}
