package com.grapsee.gsai.di

import android.content.Context
import com.grapsee.gsai.BuildConfig
import com.grapsee.gsai.data.SessionStore
import com.grapsee.gsai.data.attachment.AttachmentStore
import com.grapsee.gsai.data.chat.ChatStreamController
import com.grapsee.gsai.data.local.AppDatabase
import com.grapsee.gsai.data.remote.ApiClient
import com.grapsee.gsai.data.remote.GsApiJson
import com.grapsee.gsai.data.repository.ChatRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import java.util.UUID

/** Header REQUIRED by the platform edge (session affinity) — missing = 400. */
internal const val GS_SESSION_HEADER = "x-session-id"

/**
 * One HttpClient factory for the whole app. 120s request timeout lets SSE
 * streams run long without the client cutting them off.
 *
 * v0.71.6 TRANSPORT FIX — the engine is OkHttp. CIO was here for years
 * ("pure-Kotlin, JVM/Android friendly") and the app NEVER ONCE completed a
 * TLS handshake to the origin on a real phone: CIO builds its SSLEngine
 * anonymously (no peer host), and Android's NSC RootTrustManager refuses
 * that shape when the device's network-security config carries per-domain
 * entries — CertificateException "Domain specific configurations require
 * that hostname aware checkServerTrusted(X509Certificate[], String, String)
 * is used", captured verbatim by the v0.71.5 forensic diag line on the
 * operator's arm64 phone. Every send died DURING the handshake, before the
 * request was written — which is why the server logs showed zero phone
 * rows while the SAME phone's browser chatted fine. OkHttp performs TLS on
 * host-named sockets via the platform (Conscrypt) path — the same path the
 * working browser effectively uses.
 *
 * TRANSPORT FIX (v0.66): every request carries `x-session-id` — the
 * platform edge rejects ALL invocations without it ("header 'x-session-id'
 * is required for header field session affinity"), which surfaced to users
 * as a bare "Network error" on uploads. [engine] is overridable so tests
 * capture the emitted request with MockEngine.
 */
internal fun gsHttpClient(sessionId: String, engine: HttpClientEngine? = null): HttpClient =
    gsHttpClient({ sessionId }, engine)

/**
 * v0.71.5 AFFINITY-ROTATION — the provider form reads the session id AT
 * REQUEST TIME. The platform edge uses `x-session-id` for session affinity;
 * a value baked at client construction can never be rotated, and a STALE pin
 * routes every send of this install to whatever upstream the edge pinned it
 * to weeks ago — observed live 2026-10-13: the app got a 2xx and then
 * nothing, mid-turn cut with zero bytes, while the SAME phone's browser
 * (fresh session id per page load) chatted fine against the same origin.
 */
internal fun gsHttpClient(sessionIdProvider: () -> String, engine: HttpClientEngine? = null): HttpClient =
    if (engine != null) HttpClient(engine) { configure(sessionIdProvider) }
    else HttpClient(OkHttp) { configure(sessionIdProvider) }

private fun io.ktor.client.HttpClientConfig<*>.configure(sessionIdProvider: () -> String) {
    defaultRequest {
        headers.append(GS_SESSION_HEADER, sessionIdProvider())
        // FORENSIC AUDIT [3] — every request carries the app version so the
        // backend can log the client build against its own revision and warn
        // on stale-APK audits (X-GS-* handshake).
        headers.append("x-gs-app-version", BuildConfig.VERSION_NAME)
    }
    install(ContentNegotiation) {
        json(GsApiJson)
    }
    install(HttpRequestRetry) {
        // Only 5xx get a second chance — connect failures (no backend
        // reachable) fall through instantly so the offline reply takes over.
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
     * Per-launch affinity id. Seeded immediately (random) so a premature
     * `http` access still works; init() ROTATES it with a fresh persisted
     * value BEFORE the lazy client is first touched (Application.onCreate
     * ordering).
     */
    @Volatile
    private var clientId: String = UUID.randomUUID().toString()

    private lateinit var appContext: Context

    /** Live affinity id — read at request time by the shared client's header provider. */
    internal fun currentClientId(): String = clientId

    val http: HttpClient by lazy { gsHttpClient({ currentClientId() }) }

    fun init(context: Context) {
        if (::db.isInitialized) return
        appContext = context.applicationContext
        // v0.71.5 AFFINITY-ROTATION FIX: a FRESH session id EVERY launch.
        // The old stable-per-install id outlived the backend instance it was
        // pinned to — this install kept sending the same x-session-id for
        // weeks across platform resurrections, and the edge kept honoring
        // the dead pin. Per-launch ids keep affinity stable WITHIN the
        // session (all requests of one launch share one id) and guarantee a
        // live routing slot after any backend restart.
        clientId = SessionStore.rotateClientId(context)
        db = AppDatabase.build(context)
        api = ApiClient(http)
        chat = ChatRepository(
            api,
            db,
            onDeadSession = { rotateSessionId() },
            appContext = appContext
        )
        chatStream = ChatStreamController(chat)
        attachments = AttachmentStore(http, context.applicationContext)
    }

    /**
     * v0.71.5: mint + persist a fresh affinity id, callable mid-session when
     * a send proves the current pin is dead. The shared client picks the new
     * value up on the very next request (dynamic header) — no rebuild.
     */
    fun rotateSessionId(): String {
        val fresh = UUID.randomUUID().toString()
        clientId = fresh
        if (::appContext.isInitialized) SessionStore.persistClientId(appContext, fresh)
        return fresh
    }
}
