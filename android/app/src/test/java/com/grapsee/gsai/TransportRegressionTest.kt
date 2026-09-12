package com.grapsee.gsai

import com.grapsee.gsai.di.GS_SESSION_HEADER
import com.grapsee.gsai.di.gsHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test

/**
 * TRANSPORT REGRESSION (upload "Network error" audit):
 * 1. EVERY request must carry `x-session-id` — the platform edge rejects all
 *    invocations without it (400 InvalidArgument) before the app ever sees a
 *    status. Captured with MockEngine against the real client factory.
 * 2. The RELEASE build's BASE_URL must be the real HTTPS origin — the shipped
 *    app pointed at the emulator-only 10.0.2.2 alias, unroutable on devices.
 *    Meaningful under `testReleaseUnitTest`; skipped under debug by design.
 */
class TransportRegressionTest {

    @Test
    fun `every request carries the edge-required session header`() = runBlocking {
        val captured = mutableListOf<String>()
        val engine = MockEngine { request ->
            captured.add(request.headers[GS_SESSION_HEADER] ?: "")
            respondOk("ok")
        }
        val client = gsHttpClient("test-edge-session", engine)
        val body = client.get("https://edge.example.test/api/v1/models").bodyAsText()
        assertEquals("ok", body)
        assertEquals(1, captured.size)
        assertEquals("test-edge-session", captured.first())
        client.close()
    }

    @Test
    fun `session header uses the injected value, not a per-request random`() = runBlocking {
        val captured = mutableListOf<String>()
        val engine = MockEngine { request ->
            captured.add(request.headers[GS_SESSION_HEADER] ?: "")
            respondOk("ok")
        }
        val client = gsHttpClient("stable-affinity-id", engine)
        client.get("https://edge.example.test/a")
        client.get("https://edge.example.test/b")
        assertEquals(listOf("stable-affinity-id", "stable-affinity-id"), captured)
        client.close()
    }

    @Test
    fun `release transport origin is the real HTTPS edge`() {
        assumeFalse(BuildConfig.DEBUG) // release assertion — run via testReleaseUnitTest
        assertTrue(
            "BASE_URL must be HTTPS (release): ${BuildConfig.BASE_URL}",
            BuildConfig.BASE_URL.startsWith("https://")
        )
        assertTrue(
            "BASE_URL must target the platform edge (release): ${BuildConfig.BASE_URL}",
            BuildConfig.BASE_URL.contains("fcapp.run")
        )
    }
}
