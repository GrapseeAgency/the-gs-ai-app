package com.grapsee.gsai.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * The Subscription & Usage meters claim to count the current LOCAL calendar
 * month, while the message writer stores UTC ISO stamps. That boundary has been
 * wrong once before (a naive local stamp compared against UTC rows drops or
 * adds hours), so the only way the screen's "this month" can be trusted is if
 * the emitted stamp is the UTC instant of local midnight on the 1st.
 */
class UsageMeterTest {

    @Test
    fun monthStartIsLocalMidnightExpressedInUtc() {
        val original = TimeZone.getDefault()
        try {
            // Dhaka is UTC+6, so local 1 Oct 00:00 is 30 Sep 18:00Z.
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dhaka"))
            val october15 = Calendar.getInstance().apply {
                set(2026, Calendar.OCTOBER, 15, 12, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            assertEquals(
                "a UTC+6 month boundary must not be compared as a local stamp",
                "2026-09-30T18:00:00.000Z",
                UsageMeter.monthStartIso(october15),
            )
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun monthStartInUtcIsItsOwnInstant() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val october15 = Calendar.getInstance().apply {
                set(2026, Calendar.OCTOBER, 15, 12, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            assertEquals(
                "2026-10-01T00:00:00.000Z",
                UsageMeter.monthStartIso(october15),
            )
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun anUncountedMeterStaysUnknownRatherThanZero() {
        // -1 renders as "—"; zero would read as "you used none of your
        // allowance", which is a different and false claim.
        assertEquals(-1, UsageMeter.Unknown.messages)
        assertEquals(-1, UsageMeter.Unknown.uploads)
    }
}
