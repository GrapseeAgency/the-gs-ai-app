package com.grapsee.gsai.di

import android.content.Context
import com.grapsee.gsai.data.chat.ChatStreamController
import com.grapsee.gsai.data.local.AppDatabase
import com.grapsee.gsai.data.remote.ApiClient
import com.grapsee.gsai.data.remote.GsApiJson
import com.grapsee.gsai.data.repository.ChatRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

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
     * App-scoped chat-stream owner (Task 86-d): the streaming Job lives here,
     * NOT in ChatScreen's composition — rotation mid-stream keeps the answer
     * growing; navigation away cancels + finalizes through the same instance.
     */
    lateinit var chatStream: ChatStreamController

    /**
     * One HttpClient for the whole app. CIO engine keeps the stack pure-Kotlin
     * (JVM/Android friendly, easy to unit-test); 120s request timeout lets SSE
     * streams run long without the client cutting them off.
     */
    val http: HttpClient by lazy {
        HttpClient(CIO) {
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
    }

    fun init(context: Context) {
        if (::db.isInitialized) return
        db = AppDatabase.build(context)
        api = ApiClient(http)
        chat = ChatRepository(api, db)
        chatStream = ChatStreamController(chat)
    }
}
