package com.grapsee.gsai.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.data.local.AppDatabase
import com.grapsee.gsai.data.remote.ApiClient
import com.grapsee.gsai.data.remote.GsApiJson
import com.grapsee.gsai.data.remote.MessageDto
import com.grapsee.gsai.data.remote.SseEvent
import com.grapsee.gsai.di.gsHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.net.ConnectException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Actual Ktor transport -> ApiClient -> ChatRepository -> isolated Room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SendResponseHonestyTest {
    private lateinit var db: AppDatabase
    private var previousLocalPreference = false

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SettingsStore.init(context)
        previousLocalPreference = SettingsStore.preferLocal
        SettingsStore.updatePreferLocal(false)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
        SettingsStore.updatePreferLocal(previousLocalPreference)
    }

    @Test
    fun unreachableUnusualPromptsProduceOneRetryNoticeAndNoKeywordSelectedAnswer() = runBlocking {
        val prompts = listOf(
            "Write a Bengali evacuation checklist for a Feni household with a wheelchair user; no marketing advice.",
            "Build an unusual custom classroom simulation; do not give generic startup planning tips."
        )
        val paths = mutableListOf<String>()
        val engine = MockEngine { request ->
            paths += request.url.encodedPath
            throw ConnectException("fixture connection refused")
        }
        val client = gsHttpClient("actual-honesty-fixture", engine)
        try {
            val repository = ChatRepository(ApiClient(client, "https://actual-fixture.invalid"), db)
            prompts.forEachIndexed { index, prompt ->
                val chunks = mutableListOf<String>()
                val id = "actual-offline-request-$index"
                assertEquals(id, repository.send(conversationId = id, content = prompt, onDelta = chunks::add))
                assertEquals("A failed connection must not append a canned second answer", 1, chunks.size)
                val notice = chunks.single()
                assertTrue(notice.startsWith("(Offline, cannot reach GS.)"))
                assertTrue(notice.contains("regenerate", ignoreCase = true))
                val saved = db.messageDao().forConversation(id)
                assertEquals(listOf(prompt), saved.filter { it.role == "user" }.map { it.content })
                assertEquals(listOf(notice), saved.filter { it.role == "assistant" }.map { it.content })
            }
            assertEquals(prompts.size, paths.size)
            assertTrue(paths.all { it.endsWith("/messages") })
        } finally {
            client.close()
        }
    }

    @Test
    fun successfulCanonicalAnswerRetainsActualServerContentAndIdentity() = runBlocking {
        val actual = MessageDto(id = "actual-server-answer", conversationId = "actual-remote-chat",
            role = "assistant", content = "Actual custom answer, not the streamed draft.", createdAt = ChatRepository.nowIso())
        fun event(name: String, data: String): String = "data: " + GsApiJson.encodeToString(SseEvent(name, data)) + "\n\n"
        val stream = event("delta", "Unfinished draft") + event("done", GsApiJson.encodeToString(actual))
        val engine = MockEngine {
            respond(stream, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
        }
        val client = gsHttpClient("actual-success-fixture", engine)
        try {
            val repository = ChatRepository(ApiClient(client, "https://actual-fixture.invalid"), db)
            var received: MessageDto? = null
            repository.send(conversationId = actual.conversationId, content = "Generate a tailored interactive lesson.",
                onDone = { received = it }, onDelta = {})
            assertEquals(actual, received)
            val saved = db.messageDao().forConversation(actual.conversationId).filter { it.role == "assistant" }
            assertEquals(1, saved.size)
            assertEquals(actual.id, saved.single().id)
            assertEquals(actual.content, saved.single().content)
        } finally {
            client.close()
        }
    }
}
