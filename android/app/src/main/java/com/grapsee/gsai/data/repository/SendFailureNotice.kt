package com.grapsee.gsai.data.repository

/**
 * The words the app shows when a send FAILS.
 *
 * WHY THIS IS ITS OWN FILE AND NOT INLINE IN `ChatRepository.send()`:
 * both of these strings were inline literals in a ~700-line function with no
 * JVM-testable seam, so nothing could assert them and they quietly became
 * false. A real arm64 phone reported the empty case on 2026-10-05 after ~20s
 * of waiting on "hi".
 *
 * Both branches are reached only AFTER `recoverLatestAssistant()` returned
 * null. That fact is the whole constraint, and it is why these live where a
 * test can see them:
 *
 *   - Recovery found NOTHING saved. So no sentence here may tell the user to
 *     reopen the chat to load a finished reply. The old text did exactly
 *     that, in both branches: it promised an answer the line above had
 *     already proven does not exist.
 *   - `midStreamPartial` knows text arrived before the cut, so describing the
 *     turn as cut is true. `midStreamNothingArrived` knows NOTHING arrived, so
 *     it must not claim GS "was working" -- no token ever came back.
 *
 * `midStreamNothingArrived` deliberately does NOT contain the phrase
 * "connection dropped mid-turn". That phrase is the classification signal
 * DeviceVerificationTest asserts against for the PARTIAL case, where
 * `accumulated` is non-empty and the cut is real; reusing it here would let a
 * zero-token failure read as a half-finished answer.
 */
internal object SendFailureNotice {

    /**
     * Partial text arrived, then the stream died. The user has the real text
     * already, so the notice explains the gap instead of apologising for it.
     */
    fun midStreamPartial(): String =
        "— The connection dropped mid-turn. " +
            "Everything above is all that arrived; nothing further " +
            "was saved. Tap regenerate to try again. —"

    /**
     * Nothing arrived at all. Not an aborted answer -- a request that never
     * produced one. Says so, and offers the action that works.
     */
    fun midStreamNothingArrived(): String =
        "— No reply came back. The request to GS " +
            "failed before any part of the answer arrived, and " +
            "nothing was saved, so reopening this chat will not " +
            "help. Tap regenerate, or try again in a moment. —"
}