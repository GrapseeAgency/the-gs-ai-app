package com.grapsee.gsai.data.attachment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PHASE 5 — the client pre-flight rules are an exact mirror of the backend
 * (src/lib/attachments.ts; docs/ATTACHMENTS.md §3). These JVM tests pin the
 * mirror: allowlist membership, kind derivation, size gate, name sanitization
 * and the honest slot accounting.
 */
class AttachmentRulesTest {

    @Test
    fun `allowlist matches the backend exactly`() {
        val backend = setOf(
            "image/jpeg", "image/png", "image/webp", "image/gif",
            "image/heic", "image/heif",
            "application/pdf",
            "text/plain", "text/markdown", "text/csv"
        )
        assertEquals(backend, AttachmentRules.ALLOWED_MIME_TYPES)
    }

    @Test
    fun `audio and video are deliberately absent`() {
        assertNull(AttachmentRules.kindForMime("audio/mpeg"))
        assertNull(AttachmentRules.kindForMime("video/mp4"))
        assertFalse(AttachmentRules.isAllowed("audio/mpeg"))
        assertFalse(AttachmentRules.isAllowed("video/mp4"))
    }

    @Test
    fun `kind derivation mirrors the backend`() {
        assertEquals(AttachmentKind.Image, AttachmentRules.kindForMime("image/png"))
        assertEquals(AttachmentKind.Pdf, AttachmentRules.kindForMime("application/pdf"))
        assertEquals(AttachmentKind.Document, AttachmentRules.kindForMime("text/plain"))
        assertEquals(AttachmentKind.Document, AttachmentRules.kindForMime("text/csv"))
        assertNull(AttachmentRules.kindForMime("application/zip"))
    }

    @Test
    fun `mime failure is honest for unknown types`() {
        assertEquals(AttachmentFailure.Unsupported, AttachmentRules.mimeFailure("application/x-msdownload"))
        assertNull(AttachmentRules.mimeFailure("image/jpeg"))
    }

    @Test
    fun `size gate fails before any upload`() {
        assertEquals(AttachmentFailure.TooLarge, AttachmentRules.sizeFailure(AttachmentRules.MAX_BYTES + 1))
        assertNull(AttachmentRules.sizeFailure(AttachmentRules.MAX_BYTES))
        assertEquals(10L * 1024 * 1024, AttachmentRules.MAX_BYTES)
        assertEquals(6, AttachmentRules.MAX_PER_MESSAGE)
    }

    @Test
    fun `display name sanitization strips paths and control chars`() {
        assertEquals("report.pdf", AttachmentRules.sanitizeDisplayName("../../etc/report.pdf"))
        // Control chars are DROPPED (backend-parity filter), never substituted.
        val withNul = String(charArrayOf('a', 0.toChar(), 'b')) + ".png"
        assertEquals("ab.png", AttachmentRules.sanitizeDisplayName(withNul))
        assertEquals("attachment", AttachmentRules.sanitizeDisplayName("///"))
        assertEquals("attachment", AttachmentRules.sanitizeDisplayName(""))
        assertEquals(120, AttachmentRules.sanitizeDisplayName("x".repeat(500)).length)
    }

    @Test
    fun `slot accounting never goes negative`() {
        assertEquals(6, AttachmentRules.remainingSlots(0))
        assertEquals(1, AttachmentRules.remainingSlots(5))
        assertEquals(0, AttachmentRules.remainingSlots(6))
        assertEquals(0, AttachmentRules.remainingSlots(9))
    }

    @Test
    fun `human sizes stay honest`() {
        assertEquals("33 B", AttachmentRules.humanSize(33))
        assertEquals("1 KB", AttachmentRules.humanSize(1024))
        assertEquals("1.5 MB", AttachmentRules.humanSize(1_572_864))
    }
}
