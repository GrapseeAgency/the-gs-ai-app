package com.grapsee.gsai.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WHY THIS TEST EXISTS
 *
 * 2026-10-05, on a real arm64 phone: "hi" was sent, nothing came back for
 * ~20 seconds, and the assistant bubble rendered
 *
 *   "— The connection dropped mid-turn while GS was working.
 *     Reopen this chat in a moment to load the finished reply. —"
 *
 * Three separate lies in one sentence, and the code proves each one:
 *
 *   1. `accumulated` was EMPTY. Not one token arrived, so GS was not
 *      "working" -- there was nothing to work on.
 *   2. This branch runs only AFTER `recoverLatestAssistant()` returned null.
 *      Recovery had ALREADY proven no finished reply was saved. Telling the
 *      user to reopen the chat and load it pointed them at something
 *      guaranteed not to exist.
 *   3. The text is appended to `accumulated` and then persisted as the
 *      assistant's message, so the conversation permanently holds an answer
 *      the model never gave.
 *
 * Nothing caught this because the string was an inline literal deep inside a
 * ~700-line `send()` with no JVM-testable seam. So the assertions below are
 * written as NEGATIVE constraints -- "the text must not promise X" -- rather
 * than pinning the exact wording, because the wording is the part that drifts
 * and the promises are the part that are load-bearing.
 *
 * These run on the JVM (`testDebugUnitTest`), with no device and no network,
 * unlike the arm64 path where this was originally found.
 */
class SendFailureNoticeTest {

    /** The bug, verbatim, as it shipped in v0.71.0 and v0.71.1. */
    private val shippedLie = "Reopen this chat in a moment to load the finished reply."

    @Test
    fun `no notice promises a reply that recovery already ruled out`() {
        for (text in listOf(
            SendFailureNotice.midStreamPartial(),
            SendFailureNotice.midStreamNothingArrived(),
        )) {
            assertFalse(
                "a failure notice must not tell the user to reopen the chat for a " +
                    "finished reply. This branch is reached only after " +
                    "recoverLatestAssistant() returned null, so no finished reply " +
                    "exists to load -- now or ever. Got: $text",
                text.contains(shippedLie),
            )
            assertFalse(
                "a failure notice must not promise a future answer at all. " +
                    "The turn is over and nothing was saved. Got: $text",
                text.contains("in a moment to load"),
            )
        }
    }

    @Test
    fun `the nothing-arrived notice does not claim GS was working`() {
        val text = SendFailureNotice.midStreamNothingArrived()
        assertFalse(
            "\`accumulated\` is empty in this branch, so no token ever arrived and " +
                "there was no work in progress to report on. Got: $text",
            text.contains("while GS was working"),
        )
    }

    @Test
    fun `the nothing-arrived notice is not mistaken for a half-written answer`() {
        // "connection dropped mid-turn" is the classification signal
        // DeviceVerificationTest asserts for the PARTIAL case, where the cut is
        // real and text did arrive. Reusing it for a zero-token failure would
        // present a request that produced nothing as a half-finished answer --
        // exactly the wrong reading of the user's own screenshot.
        assertFalse(
            "a turn that produced no tokens must not be described as a mid-turn " +
                "cut; that phrase belongs to the partial-text branch. Got: " +
                SendFailureNotice.midStreamNothingArrived(),
            SendFailureNotice.midStreamNothingArrived()
                .contains("connection dropped mid-turn"),
        )
        assertTrue(
            "the partial branch keeps the classification phrase so the on-device " +
                "mid-stream test still discriminates a cut from a refusal",
            SendFailureNotice.midStreamPartial()
                .contains("connection dropped mid-turn"),
        )
    }

    @Test
    fun `both notices offer an action the user can actually take`() {
        for (text in listOf(
            SendFailureNotice.midStreamPartial(),
            SendFailureNotice.midStreamNothingArrived(),
        )) {
            assertTrue(
                "a failure with no way forward reads as a dead end; the user needs " +
                    "a next step. Got: $text",
                text.contains("regenerate", ignoreCase = true) ||
                    text.contains("try again", ignoreCase = true),
            )
        }
    }

    @Test
    fun `both notices read as a failure, not as a reply`() {
        for (text in listOf(
            SendFailureNotice.midStreamPartial(),
            SendFailureNotice.midStreamNothingArrived(),
        )) {
            assertTrue(
                "a failure notice should be delimited like the other failure " +
                    "markers in this layer so it cannot read as prose. Got: $text",
                text.startsWith("— ") && text.endsWith(" —"),
            )
        }
    }
}