package com.grapsee.gsai.data.attachment

import com.grapsee.gsai.data.remote.AttachmentDto
import kotlinx.serialization.Serializable

/**
 * PHASE 5 input & multimodal foundation — the PURE attachment layer
 * (docs/ATTACHMENTS.md §1/§3/§5). Everything in this file is plain Kotlin:
 * no Android imports, fully JVM unit-testable. Android I/O lives in
 * [AttachmentStore]; the wire shape lives in data/remote/AttachmentDto.
 *
 * Honest-capability rule (§0): kinds are exactly image | pdf | document.
 * audio/video/screenshot stay reserved and are never offered anywhere.
 * There is NO processing phase — the server has none, the UI must not fake one.
 */

@Serializable
enum class AttachmentKind(val wire: String) {
    Image("image"),
    Pdf("pdf"),
    Document("document");

    companion object {
        fun fromWire(value: String?): AttachmentKind? =
            entries.firstOrNull { it.wire == value }
    }
}

@Serializable
enum class AttachmentSource(val wire: String) {
    Gallery("gallery"),
    Camera("camera"),
    Files("files")
}

/**
 * Honest state machine phases (§5): selected → preparing → uploading → ready,
 * any preparing/uploading step may fail (retryable). NO processing state.
 */
@Serializable
enum class AttachmentPhase {
    /** Bytes known, staging queued — the picker just returned. */
    Selected,
    /** Copying/validating on a background dispatcher. */
    Preparing,
    /** Real HTTP multipart in flight (no fabricated percent exists). */
    Uploading,
    /** Server record + remoteURL exist. */
    Ready,
    /** Validation, staging, network or HTTP error — Retry + Remove available. */
    Failed
}

/**
 * Failure taxonomy surfaced honestly (§5). `RateLimited` is the backend's real
 * /uploads behaviour (30/60s per client); `Server` carries the HTTP code in
 * [AttachmentDraft.serverCode]. The server remains the authority — client
 * pre-checks only fail fast with the same vocabulary.
 */
@Serializable
enum class AttachmentFailure {
    TooLarge,
    Unsupported,
    ReadFailed,
    Network,
    RateLimited,
    Server,
    ConversationMissing
}

/** Thumbnail pipeline state (§1): none | ready | failed. */
@Serializable
enum class ThumbState { None, Ready, Failed }

/**
 * The composer's working attachment draft — logical model of §1. `id` is the
 * client uuid for the whole draft lifetime; once uploaded the server record
 * arrives in remoteId/remoteUrl and THAT id is what a message claims.
 */
@Serializable
data class AttachmentDraft(
    val id: String,
    val kind: AttachmentKind = AttachmentKind.Document,
    val source: AttachmentSource = AttachmentSource.Files,
    /** Sanitized display name (§3 rule 4) — never a path. */
    val displayName: String = "",
    /** One of the shared allowlist MIME types. */
    val mimeType: String = "",
    /** App-private staged copy: filesDir/attachments/<uuid>/<displayName>. */
    val localPath: String? = null,
    /** Bytes of the STAGED COPY — the truth, not picker metadata. */
    val byteSize: Long = 0,
    val phase: AttachmentPhase = AttachmentPhase.Selected,
    val failure: AttachmentFailure? = null,
    /** HTTP status when failure == Server (surfaced verbatim, never invented). */
    val serverCode: Int? = null,
    /** Server attachment id once uploaded — the id a message claims. */
    val remoteId: String? = null,
    /** Server-relative "/api/v1/files/<id>" once uploaded. */
    val remoteUrl: String? = null,
    /** Server createdAt once uploaded. */
    val createdAt: String = "",
    val thumbState: ThumbState = ThumbState.None
) {
    /** True once a server record exists (removal becomes purely a draft op). */
    val isUploaded: Boolean get() = remoteId != null
}

/**
 * Shared client pre-flight rules (§3) — an exact mirror of the backend
 * (src/lib/attachments.ts). The SERVER is the authority; these exist to fail
 * fast with friendly, honest errors and to keep the pickers honest.
 */
object AttachmentRules {

    /** Receive allowlist — identical to the backend. Audio/video absent on purpose. */
    val ALLOWED_MIME_TYPES: Set<String> = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/gif",
        "image/heic",
        "image/heif",
        "application/pdf",
        "text/plain",
        "text/markdown",
        "text/csv"
    )

    /** Hard byte cap per attachment (checked before upload; server re-checks). */
    const val MAX_BYTES: Long = 10L * 1024 * 1024

    /** Hard cap per message. */
    const val MAX_PER_MESSAGE: Int = 6

    /** Cap of the sanitized display name, mirroring the backend exactly. */
    const val MAX_DISPLAY_NAME_LENGTH: Int = 120

    fun isAllowed(mimeType: String): Boolean = mimeType.lowercase() in ALLOWED_MIME_TYPES

    /** image kinds map to Image, application/pdf to Pdf, text kinds to Document, else null. */
    fun kindForMime(mimeType: String): AttachmentKind? {
        val mime = mimeType.lowercase()
        return when {
            mime.startsWith("image/") -> AttachmentKind.Image
            mime == "application/pdf" -> AttachmentKind.Pdf
            mime.startsWith("text/") -> AttachmentKind.Document
            else -> null
        }
    }

    /** §3 rule 1: the mime must be allowlisted AND map to a shipped kind. */
    fun mimeFailure(mimeType: String): AttachmentFailure? =
        if (isAllowed(mimeType) && kindForMime(mimeType) != null) null else AttachmentFailure.Unsupported

    /** §3 rule 2: oversized content fails BEFORE any upload. */
    fun sizeFailure(byteSize: Long): AttachmentFailure? =
        if (byteSize > MAX_BYTES) AttachmentFailure.TooLarge else null

    /**
     * Strip path separators/control chars, cap length, honest fallback —
     * a char-level mirror of the backend sanitizeDisplayName (keeps every
     * printable char, trims, "attachment" when nothing survives).
     */
    fun sanitizeDisplayName(raw: String): String {
        val base = raw.split('/', '\\').lastOrNull() ?: ""
        val cleaned = base.filter { it.code >= 32 && it.code != 0x7F }.trim()
        val safe = cleaned.ifEmpty { "attachment" }
        return if (safe.length > MAX_DISPLAY_NAME_LENGTH) safe.take(MAX_DISPLAY_NAME_LENGTH) else safe
    }

    /** Remaining composer slots for the honest picker gating (never silently drops). */
    fun remainingSlots(activeCount: Int): Int = (MAX_PER_MESSAGE - activeCount).coerceAtLeast(0)

    /** Human byte size for chips ("33 B", "1.5 MB") — display-only, honest rounding. */
    fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return trim(kb) + " KB"
        val mb = kb / 1024.0
        return trim(mb) + " MB"
    }

    private fun trim(value: Double): String {
        val rounded = (value * 10).toLong() / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }
}

/**
 * Pure state-machine helpers for the composer pipeline (§5). Every transition
 * is guarded: a call from an invalid phase is a no-op returning the draft
 * unchanged — impossible states are never fabricated.
 */
object AttachmentStateMachine {

    /** selected|failed(prepare-step) → preparing. */
    fun beginPrepare(draft: AttachmentDraft): AttachmentDraft =
        if (draft.phase == AttachmentPhase.Selected || retryStep(draft) == AttachmentPhase.Preparing) {
            draft.copy(phase = AttachmentPhase.Preparing, failure = null, serverCode = null)
        } else {
            draft
        }

    /** preparing → uploading with the staged-copy truth (path/bytes/mime/name). */
    fun prepared(
        draft: AttachmentDraft,
        localPath: String,
        byteSize: Long,
        displayName: String,
        mimeType: String
    ): AttachmentDraft {
        if (draft.phase != AttachmentPhase.Preparing) return draft
        return draft.copy(
            phase = AttachmentPhase.Uploading,
            localPath = localPath,
            byteSize = byteSize,
            displayName = displayName,
            mimeType = mimeType,
            kind = AttachmentRules.kindForMime(mimeType) ?: draft.kind,
            failure = null,
            serverCode = null
        )
    }

    /** uploading → ready with the server record. */
    fun uploadSucceeded(
        draft: AttachmentDraft,
        remote: AttachmentDto
    ): AttachmentDraft {
        if (draft.phase != AttachmentPhase.Uploading) return draft
        return draft.copy(
            phase = AttachmentPhase.Ready,
            remoteId = remote.id,
            remoteUrl = remote.url,
            byteSize = remote.byteSize,
            createdAt = remote.createdAt,
            failure = null,
            serverCode = null,
            thumbState = if (draft.kind == AttachmentKind.Image) ThumbState.Ready else ThumbState.None
        )
    }

    /** preparing|uploading → failed with the honest reason. */
    fun failed(draft: AttachmentDraft, failure: AttachmentFailure, serverCode: Int? = null): AttachmentDraft =
        if (draft.phase == AttachmentPhase.Preparing || draft.phase == AttachmentPhase.Uploading) {
            draft.copy(phase = AttachmentPhase.Failed, failure = failure, serverCode = serverCode)
        } else {
            draft
        }

    /**
     * Which step a retry must re-run: prepare-step failures (size/type/read)
     * re-enter preparing; transport failures re-enter uploading. The staged
     * copy survives a failed upload, so upload retries never re-stage.
     */
    fun retryStep(draft: AttachmentDraft): AttachmentPhase = when (draft.failure) {
        AttachmentFailure.TooLarge,
        AttachmentFailure.Unsupported,
        AttachmentFailure.ReadFailed -> AttachmentPhase.Preparing
        null -> if (draft.phase == AttachmentPhase.Failed) AttachmentPhase.Preparing else draft.phase
        else -> AttachmentPhase.Uploading
    }

    /** failed → the failed step only (never re-picks, never skips validation). */
    fun retry(draft: AttachmentDraft): AttachmentDraft {
        if (draft.phase != AttachmentPhase.Failed) return draft
        val step = retryStep(draft)
        return draft.copy(
            phase = step,
            failure = null,
            serverCode = null,
            thumbState = ThumbState.None
        )
    }

    /** §5 send gate: every draft ready (removed drafts have already left the list). */
    fun canSend(drafts: List<AttachmentDraft>): Boolean = drafts.all { it.phase == AttachmentPhase.Ready }

    /** True when a draft's staged file may be deleted (never uploaded → local only). */
    fun shouldDeleteStagedFile(draft: AttachmentDraft): Boolean = !draft.isUploaded
}

/** Typed upload/transport error — carries the honest failure taxonomy value. */
class AttachmentException(
    val failure: AttachmentFailure,
    val code: Int? = null,
    message: String? = null
) : Exception(message ?: failure.name)

/**
 * Display adapter: wire record → chip draft (history/optimistic rendering).
 * No staged file is known for remote records — image chips fall back to the
 * monochrome kind icon (no network image pipeline exists by contract).
 */
fun AttachmentDto.asDisplayDraft(): AttachmentDraft = AttachmentDraft(
    id = id,
    kind = AttachmentKind.fromWire(kind) ?: AttachmentKind.Document,
    source = AttachmentSource.Files,
    displayName = displayName,
    mimeType = mimeType,
    localPath = null,
    byteSize = byteSize,
    phase = AttachmentPhase.Ready,
    remoteId = id,
    remoteUrl = url,
    createdAt = createdAt,
    thumbState = ThumbState.None
)
