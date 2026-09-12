package com.grapsee.gsai.data.attachment

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.grapsee.gsai.data.remote.AttachmentUploader
import com.grapsee.gsai.data.remote.GsApiJson
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * PHASE 5 — app-scoped attachment store (docs/ATTACHMENTS.md §4/§5).
 *
 * Owns the composer's active attachment drafts end-to-end with REAL work only:
 *  - staging: picked content is COPIED into `filesDir/attachments/<uuid>/<name>`
 *    on Dispatchers.IO (picker URIs expire — originals are never re-read);
 *  - validation BEFORE staging (fail-fast too_large/unsupported, §3);
 *  - upload: real HTTP multipart through [AttachmentUploader] the moment the
 *    staged copy exists — no timers, no simulated progress;
 *  - retry re-runs ONLY the failed step; removal deletes the staged copy when
 *    the attachment was never uploaded and is purely a draft operation if it was;
 *  - draft persistence extends the chat draft prefs ("gs_chat_drafts") with
 *    JSON {text, attachments:[…]} per conversation; restore verifies the staged
 *    files still exist and honestly drops entries whose files are gone.
 *
 * Constructed once from [com.grapsee.gsai.di.ServiceLocator] — one instance per
 * process, so in-flight uploads and the live draft list survive rotation.
 */
class AttachmentStore(
    private val http: HttpClient,
    private val context: Context
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val uploader = AttachmentUploader(http)

    private val _drafts = MutableStateFlow<List<AttachmentDraft>>(emptyList())
    val drafts: StateFlow<List<AttachmentDraft>> = _drafts.asStateFlow()

    private val prefs by lazy {
        context.getSharedPreferences(DRAFTS_PREFS, Context.MODE_PRIVATE)
    }

    /** Guards read-modify-write cycles on the shared draft prefs entry. */
    private val draftLock = Any()

    /**
     * Session-only staging sources. Picker URIs are transient permission grants —
     * they exist ONLY to feed the one staging copy and are NEVER persisted.
     */
    private sealed interface StagingSource {
        data class Picked(val uri: Uri) : StagingSource
        data class Captured(val file: File) : StagingSource
    }

    private val sources = HashMap<String, StagingSource>()

    /** Conversation the composer's drafts currently belong to (bind contract). */
    private var boundConversationId: String? = null

    // --- composer queries -------------------------------------------------------

    /** Honest slot accounting — pickers disable at zero, nothing silently drops. */
    fun remainingSlots(): Int = AttachmentRules.remainingSlots(_drafts.value.size)

    fun isSendBlocked(): Boolean = !AttachmentStateMachine.canSend(_drafts.value)

    // --- staging entry points ---------------------------------------------------

    /** Gallery / Files picker result: stage one picked document or visual media. */
    fun stageFromUri(uri: Uri, source: AttachmentSource) {
        if (AttachmentRules.remainingSlots(_drafts.value.size) == 0) return
        val id = UUID.randomUUID().toString()
        var accepted = false
        _drafts.update { list ->
            if (AttachmentRules.remainingSlots(list.size) == 0) list
            else {
                accepted = true
                list + AttachmentDraft(id = id, source = source, phase = AttachmentPhase.Selected)
            }
        }
        if (!accepted) return
        sources[id] = StagingSource.Picked(uri)
        scope.launch { prepareAndUpload(id) }
    }

    /** Camera capture result: stage the temp JPEG taken through FileProvider. */
    fun stageFromCameraFile(tempFile: File) {
        if (AttachmentRules.remainingSlots(_drafts.value.size) == 0) return
        val id = UUID.randomUUID().toString()
        var accepted = false
        _drafts.update { list ->
            if (AttachmentRules.remainingSlots(list.size) == 0) list
            else {
                accepted = true
                list + AttachmentDraft(
                    id = id,
                    kind = AttachmentKind.Image,
                    source = AttachmentSource.Camera,
                    displayName = AttachmentRules.sanitizeDisplayName(tempFile.name),
                    mimeType = "image/jpeg",
                    phase = AttachmentPhase.Selected
                )
            }
        }
        if (!accepted) return
        sources[id] = StagingSource.Captured(tempFile)
        scope.launch { prepareAndUpload(id) }
    }

    /** §5: retry re-runs ONLY the failed step — prepare-step or upload-step. */
    fun retry(id: String) {
        val draft = current(id) ?: return
        if (draft.phase != AttachmentPhase.Failed) return
        when (AttachmentStateMachine.retryStep(draft)) {
            AttachmentPhase.Preparing -> {
                // Re-staging needs the source still alive in this session; picker
                // grants die with the process, so a restored failed draft (which
                // is never restored at all — restore keeps ready only) or a
                // source-less retry stays honestly failed instead of pretending.
                if (sources[id] == null) return
                _drafts.update { list -> list.map { if (it.id == id) AttachmentStateMachine.retry(it) else it } }
                scope.launch { prepareAndUpload(id) }
            }
            AttachmentPhase.Uploading -> {
                _drafts.update { list -> list.map { if (it.id == id) AttachmentStateMachine.retry(it) else it } }
                scope.launch { uploadStaged(id) }
            }
            else -> Unit
        }
    }

    /**
     * Remove a draft. Staged copy is deleted when never uploaded; an uploaded
     * record is untouched server-side (purely a draft operation, §4).
     */
    fun remove(id: String) {
        val draft = current(id) ?: return
        _drafts.update { list -> list.filterNot { it.id == id } }
        sources.remove(id)
        if (AttachmentStateMachine.shouldDeleteStagedFile(draft)) {
            deleteStagedDir(draft)
        }
    }

    /**
     * Drop every draft (sent or conversation left). Staged copies of
     * never-uploaded drafts are cleaned up; uploaded ones persist on disk until
     * the conversation is deleted (§4).
     */
    fun clear() {
        _drafts.value.forEach { draft ->
            if (AttachmentStateMachine.shouldDeleteStagedFile(draft)) deleteStagedDir(draft)
        }
        _drafts.value = emptyList()
        sources.clear()
    }

    // --- conversation binding + draft persistence -------------------------------

    /**
     * Bind the composer to a conversation: drops drafts belonging to whatever
     * was bound before and rehydrates THIS conversation's persisted ready
     * drafts. A re-bind for the same id is a no-op, so rotation (which reruns
     * the screen effects but keeps this process-scoped store) never disturbs
     * the live list — including uploads in flight.
     */
    fun bind(conversationId: String?) {
        if (boundConversationId == conversationId) return
        clear()
        boundConversationId = conversationId
        _drafts.value = restorePersisted(conversationId)
    }

    /** Persisted text half of the draft entry (legacy plain-text tolerant). */
    fun loadPersistedText(conversationId: String?): String? {
        if (conversationId == null) return null
        val raw = runCatching { prefs.getString(draftKey(conversationId), null) }.getOrNull()
            ?: return null
        val parsed = decodeEntry(raw) ?: return raw // legacy plain text
        return parsed.text.ifEmpty { null }
    }

    /**
     * Write the full draft entry {text, attachments} for a conversation. Called
     * at dispose (leave-time snapshot) and whenever the live drafts change once
     * the screen has finished loading — never speculatively, so a half-loaded
     * screen can never clobber a saved draft.
     */
    fun persistSnapshot(conversationId: String?, text: String) {
        if (conversationId == null) return
        val attachments = _drafts.value
            .filter { it.phase == AttachmentPhase.Ready || it.phase == AttachmentPhase.Failed }
        synchronized(draftLock) {
            runCatching {
                val key = draftKey(conversationId)
                if (text.isBlank() && attachments.isEmpty()) {
                    prefs.edit().remove(key).apply()
                } else {
                    val json = GsApiJson.encodeToString(
                        PersistedDraftEntry.serializer(),
                        PersistedDraftEntry(text = text, attachments = attachments)
                    )
                    prefs.edit().putString(key, json).apply()
                }
            }
        }
    }

    /** Draft entry is gone the moment a send lands (text + attachments). */
    fun clearPersistedDraft(conversationId: String?) {
        if (conversationId == null) return
        synchronized(draftLock) {
            runCatching { prefs.edit().remove(draftKey(conversationId)).apply() }
        }
    }

    /**
     * §5 draft persistence: staged (ready-or-failed) attachments persist with
     * the draft; restore rehydrates READY entries only, and only when their
     * staged copy still exists — a missing file is dropped honestly, never
     * resurrected as a fake chip.
     */
    private fun restorePersisted(conversationId: String?): List<AttachmentDraft> {
        if (conversationId == null) return emptyList()
        val raw = runCatching { prefs.getString(draftKey(conversationId), null) }.getOrNull()
            ?: return emptyList()
        val entry = decodeEntry(raw) ?: return emptyList()
        return entry.attachments.filter { draft ->
            draft.phase == AttachmentPhase.Ready &&
                draft.remoteId != null &&
                draft.localPath != null &&
                File(draft.localPath).exists()
        }
    }

    @Serializable
    private data class PersistedDraftEntry(
        val text: String = "",
        val attachments: List<AttachmentDraft> = emptyList()
    )

    private fun decodeEntry(raw: String): PersistedDraftEntry? {
        if (!raw.startsWith("{")) return null
        return runCatching {
            GsApiJson.decodeFromString(PersistedDraftEntry.serializer(), raw)
        }.getOrNull()
    }

    private fun draftKey(conversationId: String) = "draft_$conversationId"

    // --- the real pipeline ------------------------------------------------------

    /** Prepare step (copy + validate) followed by the upload step. */
    private suspend fun prepareAndUpload(id: String) {
        val started = current(id) ?: return
        update(AttachmentStateMachine.beginPrepare(started))
        try {
            val resolved = when (val source = sources[id]) {
                is StagingSource.Picked -> resolvePicked(source.uri)
                is StagingSource.Captured -> resolveCaptured(source.file)
                null -> throw AttachmentException(AttachmentFailure.ReadFailed)
            }
            // Fail fast BEFORE any copy (§3): unsupported type, declared-oversize.
            AttachmentRules.mimeFailure(resolved.mimeType)?.let { throw AttachmentException(it) }
            AttachmentRules.sizeFailure(resolved.declaredSize)?.let { throw AttachmentException(it) }

            val staged = copyIntoStaging(id, resolved.displayName, resolved.openStream)
            // The camera capture temp (cacheDir/camera) is disposable the
            // moment the staged copy lands — the copy is the truth (§4).
            (sources[id] as? StagingSource.Captured)?.file?.delete()
            val byteSize = staged.length()
            if (byteSize <= 0L) {
                staged.delete()
                throw AttachmentException(AttachmentFailure.ReadFailed)
            }
            val oversize = AttachmentRules.sizeFailure(byteSize)
            if (oversize != null) {
                staged.delete()
                throw AttachmentException(oversize)
            }
            val preparing = current(id) ?: return
            update(
                AttachmentStateMachine.prepared(
                    draft = preparing,
                    localPath = staged.absolutePath,
                    byteSize = byteSize,
                    displayName = resolved.displayName,
                    mimeType = resolved.mimeType
                )
            )
            uploadStaged(id)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: AttachmentException) {
            failDraft(id, e.failure, e.code)
        } catch (e: Exception) {
            failDraft(id, AttachmentFailure.ReadFailed, null)
        }
    }

    /** Upload step only — the staged copy is the transport source of truth. */
    private suspend fun uploadStaged(id: String) {
        val draft = current(id) ?: return
        if (draft.phase != AttachmentPhase.Uploading) return
        val stagedFile = draft.localPath?.let(::File)
        if (stagedFile == null || !stagedFile.exists() || stagedFile.length() == 0L) {
            failDraft(id, AttachmentFailure.ReadFailed, null)
            return
        }
        try {
            val remote = uploader.upload(stagedFile, draft.mimeType, draft.displayName)
            val latest = current(id) ?: return
            update(AttachmentStateMachine.uploadSucceeded(latest, remote))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: AttachmentException) {
            failDraft(id, e.failure, e.code)
        } catch (e: Exception) {
            failDraft(id, AttachmentFailure.Network, null)
        }
    }

    private suspend fun failDraft(id: String, failure: AttachmentFailure, code: Int?) {
        val draft = current(id) ?: return
        val failed = AttachmentStateMachine.failed(draft, failure, code)
        update(failed)
    }

    private fun current(id: String): AttachmentDraft? =
        _drafts.value.firstOrNull { it.id == id }

    private fun update(draft: AttachmentDraft) {
        _drafts.update { list -> list.map { if (it.id == draft.id) draft else it } }
    }

    private class ResolvedContent(
        val displayName: String,
        val mimeType: String,
        /** Best pre-copy size knowledge (-1 when the provider says nothing). */
        val declaredSize: Long,
        val openStream: () -> InputStream?
    )

    private suspend fun resolvePicked(uri: Uri): ResolvedContent = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name: String? = null
        var size = -1L
        runCatching {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    if (!cursor.isNull(0)) name = cursor.getString(0)
                    if (!cursor.isNull(1)) size = cursor.getLong(1)
                }
            }
        }
        val displayName = AttachmentRules.sanitizeDisplayName(
            name ?: uri.lastPathSegment ?: "attachment"
        )
        val mime = resolver.getType(uri)
            ?: mimeFromExtension(displayName)
            ?: "application/octet-stream"
        ResolvedContent(
            displayName = displayName,
            mimeType = mime.lowercase(),
            declaredSize = size,
            openStream = { runCatching { resolver.openInputStream(uri) }.getOrNull() }
        )
    }

    private fun resolveCaptured(file: File): ResolvedContent = ResolvedContent(
        displayName = AttachmentRules.sanitizeDisplayName(file.name),
        mimeType = "image/jpeg",
        declaredSize = file.length(),
        openStream = { runCatching { file.inputStream() }.getOrNull() }
    )

    /**
     * The staged copy IS the truth: bytes land under
     * filesDir/attachments/<draftId>/<displayName> and byteSize is measured
     * from the copy afterwards — never from picker metadata (§4).
     */
    private suspend fun copyIntoStaging(
        draftId: String,
        displayName: String,
        openStream: () -> InputStream?
    ): File = withContext(Dispatchers.IO) {
        val dir = File(File(context.filesDir, STAGING_ROOT), draftId).apply { mkdirs() }
        val staged = File(dir, displayName)
        val input = openStream() ?: throw AttachmentException(AttachmentFailure.ReadFailed)
        input.use { source ->
            staged.outputStream().use { target ->
                source.copyTo(target)
            }
        }
        staged
    }

    private fun deleteStagedDir(draft: AttachmentDraft) {
        val path = draft.localPath ?: return
        runCatching {
            // The draft dir holds exactly this draft's staged copy.
            File(path).parentFile?.deleteRecursively()
        }
    }

    private fun mimeFromExtension(name: String): String? {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return null
        return runCatching { MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) }.getOrNull()
    }

    companion object {
        private const val STAGING_ROOT = "attachments"
        /** The chat drafts prefs file — attachments extend it, key space shared. */
        const val DRAFTS_PREFS = "gs_chat_drafts"
    }
}
