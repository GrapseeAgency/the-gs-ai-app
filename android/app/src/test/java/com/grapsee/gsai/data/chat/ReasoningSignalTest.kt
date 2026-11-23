package com.grapsee.gsai.data.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only the existence of a private reasoning delta may reach UI state. */
class ReasoningSignalTest {
    @Test
    fun `reasoning payload exposes presence only and tolerates malformed frames`() {
        assertTrue(hasReasoningDelta("""{"delta":"private reasoning text"}"""))
        assertFalse(hasReasoningDelta("""{"delta":""}"""))
        assertFalse(hasReasoningDelta("""{"delta":null}"""))
        assertFalse(hasReasoningDelta("""{"delta":123}"""))
        assertFalse(hasReasoningDelta("not json"))
        assertFalse(hasReasoningDelta(null))
    }
}
