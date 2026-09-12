package com.grapsee.gsai.data.attachment

import com.grapsee.gsai.data.remote.AttachmentDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PHASE 5 — the honest state machine (docs/ATTACHMENTS.md §5). Every
 * transition is guarded: invalid calls are no-ops, impossible states are
 * never fabricated, and there is deliberately NO processing state.
 */
class AttachmentStateMachineTest {

    private val remote = AttachmentDto(
        id = "srv-1",
        kind = "image",
        displayName = "pic.png",
        mimeType = "image/png",
        byteSize = 1234,
        createdAt = "2026-09-12T00:00:00.000Z",
        url = "/api/v1/files/srv-1"
    )

    private fun preparedDraft(): AttachmentDraft =
        AttachmentStateMachine.prepared(
            AttachmentStateMachine.beginPrepare(AttachmentDraft(id = "d1")),
            localPath = "/data/staged/pic.png",
            byteSize = 1234,
            displayName = "pic.png",
            mimeType = "image/png"
        )

    @Test
    fun `happy path selected preparing uploading ready`() {
        var draft = AttachmentDraft(id = "d1")
        assertEquals(AttachmentPhase.Selected, draft.phase)

        draft = AttachmentStateMachine.beginPrepare(draft)
        assertEquals(AttachmentPhase.Preparing, draft.phase)

        draft = AttachmentStateMachine.prepared(draft, "/p", 10, "a.png", "image/png")
        assertEquals(AttachmentPhase.Uploading, draft.phase)
        assertEquals(AttachmentKind.Image, draft.kind)

        draft = AttachmentStateMachine.uploadSucceeded(draft, remote)
        assertEquals(AttachmentPhase.Ready, draft.phase)
        assertEquals("srv-1", draft.remoteId)
        assertTrue(draft.isUploaded)
        // Images have a staged file to thumb from; the state flips only here.
        assertEquals(ThumbState.Ready, draft.thumbState)
    }

    @Test
    fun `no processing phase exists anywhere`() {
        // The enum itself is the contract: preparing, uploading, ready, failed —
        // nothing server-side is claimed because the server does none.
        assertEquals(
            listOf("Selected", "Preparing", "Uploading", "Ready", "Failed"),
            AttachmentPhase.entries.map { it.name }
        )
    }

    @Test
    fun `invalid transitions are no-ops`() {
        val selected = AttachmentDraft(id = "d1")
        // prepared() from selected must not jump the queue.
        assertEquals(selected, AttachmentStateMachine.prepared(selected, "/p", 10, "a", "text/plain"))
        // uploadSucceeded() from selected must not fabricate a server record.
        assertEquals(selected, AttachmentStateMachine.uploadSucceeded(selected, remote))
        // failed() only applies to in-flight phases.
        assertEquals(selected, AttachmentStateMachine.failed(selected, AttachmentFailure.Network))
    }

    @Test
    fun `prepare-step failure retries into preparing not uploading`() {
        val failing = AttachmentStateMachine.beginPrepare(preparedDraft()) // → preparing again
        val failed = AttachmentStateMachine.failed(failing, AttachmentFailure.TooLarge)
        assertEquals(AttachmentPhase.Failed, failed.phase)
        assertEquals(AttachmentPhase.Preparing, AttachmentStateMachine.retryStep(failed))
        val retried = AttachmentStateMachine.retry(failed)
        assertEquals(AttachmentPhase.Preparing, retried.phase)
        assertNull(retried.failure)
    }

    @Test
    fun `transport failure retries straight into uploading keeping the staged copy`() {
        val failed = AttachmentStateMachine.failed(
            preparedDraft(),
            AttachmentFailure.Server,
            serverCode = 500
        )
        assertEquals(AttachmentPhase.Uploading, AttachmentStateMachine.retryStep(failed))
        val retried = AttachmentStateMachine.retry(failed)
        assertEquals(AttachmentPhase.Uploading, retried.phase)
        // The staged copy survived — retry never re-stages, never re-picks.
        assertEquals("/data/staged/pic.png", retried.localPath)
        assertEquals(1234L, retried.byteSize)
    }

    @Test
    fun `send gate requires every draft ready`() {
        assertTrue(AttachmentStateMachine.canSend(emptyList()))
        assertTrue(AttachmentStateMachine.canSend(listOf(preparedDraft().copy(phase = AttachmentPhase.Ready))))
        assertFalse(AttachmentStateMachine.canSend(listOf(preparedDraft()))) // still uploading
        assertFalse(
            AttachmentStateMachine.canSend(
                listOf(
                    preparedDraft().copy(phase = AttachmentPhase.Ready),
                    AttachmentStateMachine.failed(preparedDraft(), AttachmentFailure.Network)
                )
            )
        )
    }

    @Test
    fun `staged file deletion only for never-uploaded drafts`() {
        // A READY draft always carries its server id (uploadSucceeded sets it) —
        // its staged copy persists; anything never uploaded is local-only.
        assertFalse(
            AttachmentStateMachine.shouldDeleteStagedFile(
                preparedDraft().copy(phase = AttachmentPhase.Ready, remoteId = "srv-1")
            )
        )
        assertTrue(AttachmentStateMachine.shouldDeleteStagedFile(preparedDraft()))
        assertTrue(AttachmentStateMachine.shouldDeleteStagedFile(AttachmentDraft(id = "x")))
    }

    @Test
    fun `display adapter keeps the wire record intact`() {
        val chip = remote.asDisplayDraft()
        assertEquals(AttachmentPhase.Ready, chip.phase)
        assertEquals(AttachmentKind.Image, chip.kind)
        assertEquals("srv-1", chip.remoteId)
        assertEquals(1234L, chip.byteSize)
    }
}
