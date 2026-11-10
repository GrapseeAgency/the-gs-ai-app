package com.grapsee.gsai.data.remote

import com.grapsee.gsai.di.gsHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

/** A deliberately unclosed response proves callbacks arrive BEFORE completion.
 * This deadlocks/timeouts with client.post(), which saves the whole SSE body.
 */
class LiveStreamTest {
    @Test
    fun `mode and first delta arrive while response is still open`() = runBlocking {
        val body = ByteChannel(autoFlush = true)
        val engine = MockEngine {
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
        }
        val client = gsHttpClient("live-stream-test", engine)
        val api = ApiClient(client, "https://test.invalid")
        val delta = CompletableDeferred<String>()
        val mode = CompletableDeferred<String>()
        var completions = 0
        val stream = async {
            api.sendMessageStream("chat", "hello", timezone = null,
                onDelta = { delta.complete(it) }, onMode = { mode.complete(it) },
                onDone = { completions++ })
        }
        try {
            body.writeStringUtf8("data: {\"event\":\"mode\",\"data\":\"thinking\"}\n\n")
            body.writeStringUtf8("data: {\"event\":\"delta\",\"data\":\"First words\"}\n\n")
            assertEquals("thinking", withTimeout(3000) { mode.await() })
            assertEquals("First words", withTimeout(3000) { delta.await() })
            assertFalse("Must not require done or EOF to paint text", stream.isCompleted)
            assertEquals(0, completions)
            body.writeStringUtf8("data: {\"event\":\"done\"}\n\n")
            withTimeout(3000) { stream.await() }
            assertEquals(1, completions)
        } finally {
            stream.cancelAndJoin()
            body.cancel(null)
            client.close()
        }
    }

    @Test
    fun `EOF without done is a cut and keeps the already delivered partial`() = runBlocking {
        val engine = MockEngine {
            respond("data: {\"event\":\"delta\",\"data\":\"Partial\"}\n\n",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
        }
        val client = gsHttpClient("cut-test", engine)
        val text = StringBuilder()
        try {
            ApiClient(client, "https://test.invalid").sendMessageStream("chat", "hello", timezone = null,
                onDelta = { text.append(it) }, onDone = { fail("Missing done must not complete") })
            fail("EOF must throw")
        } catch (cut: GsStreamCutException) {
            assertEquals(200, cut.status)
            assertEquals("Partial", text.toString())
        } finally { client.close() }
    }

    @Test
    fun `server error stays a backend failure not a connection cut`() = runBlocking {
        val engine = MockEngine {
            respond("data: {\"event\":\"error\",\"data\":\"Capacity unavailable\"}\n\n",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
        }
        val client = gsHttpClient("error-test", engine)
        try {
            ApiClient(client, "https://test.invalid").sendMessageStream("chat", "hello", timezone = null,
                onDelta = { fail("Unexpected answer") }, onDone = { fail("Error must not complete") })
            fail("Server error must throw")
        } catch (error: GsBackendException) {
            assertEquals(200, error.status)
        } finally { client.close() }
    }
}
