package com.grapsee.gsai.data.remote

import com.grapsee.gsai.data.remote.GsApiJson
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CLOCK WIRE CONTRACT (GenUI).
 *
 * The backend anchors every relative date ("last 6 months", "next Friday")
 * in `body.timezone`, falling back to the `x-client-timezone` header. This
 * locks the Android half of that handshake:
 *
 *  1. a real zone string travels on the wire (no more silent UTC fallback),
 *  2. an explicitly null zone stays OFF the wire (old servers still parse,
 *     plain-text sends stay byte-identical to the pre-clock contract).
 */
class SendMessageRequestTest {

    @Test
    fun `timezone travels on the wire`() {
        val body = GsApiJson.encodeToString(
            SendMessageRequest(content = "hi", timezone = "Asia/Dhaka")
        )
        assertTrue(body, body.contains("\"timezone\":\"Asia/Dhaka\""))
    }

    @Test
    fun `null timezone is omitted entirely`() {
        val body = GsApiJson.encodeToString(
            SendMessageRequest(content = "hi")
        )
        assertFalse(body, body.contains("timezone"))
        // The rest of the contract is unchanged: no stray nulls anywhere.
        assertFalse(body, body.contains("null"))
        assertTrue(body, body.contains("\"content\":\"hi\""))
        assertTrue(body, body.contains("\"stream\":true"))
    }
}
