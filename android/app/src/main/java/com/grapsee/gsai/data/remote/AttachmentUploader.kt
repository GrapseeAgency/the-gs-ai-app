package com.grapsee.gsai.data.remote

import com.grapsee.gsai.BuildConfig
import com.grapsee.gsai.data.attachment.AttachmentException
import com.grapsee.gsai.data.attachment.AttachmentFailure
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import java.io.File

/**
 * PHASE 5 — real multipart transport for POST /api/v1/uploads
 * (docs/ATTACHMENTS.md §2). The HTTP round-trip IS the upload lifecycle: no
 * simulated progress, no fabricated states — the caller surfaces
 * selected→preparing→uploading→ready/failed from real work only.
 *
 * Maps the backend's honest error codes onto the shared failure taxonomy:
 * 413 → TooLarge, 415 → Unsupported (type not allowed / spoofed sniff),
 * 429 → RateLimited, 404 (unknown pre-bind conversation) → ConversationMissing,
 * other non-2xx → Server(code); connect/IO failures → Network.
 */
class AttachmentUploader(
    private val client: HttpClient,
    baseUrl: String = BuildConfig.BASE_URL
) {
    private val root = baseUrl.trimEnd('/') + "/api/v1"

    /**
     * Upload one staged file. Returns the server attachment record (201).
     * Throws [AttachmentException] with the honest failure reason on any error.
     */
    suspend fun upload(
        file: File,
        mimeType: String,
        displayName: String,
        conversationId: String? = null
    ): AttachmentDto {
        return try {
            // MultiPartFormDataContent ships in ktor-client-core 2.3.12 — no new
            // dependency. The staged copy is ≤ AttachmentRules.MAX_BYTES, so a
            // one-shot byte read is bounded; the file part carries the real MIME
            // and a quote-free filename (the server reads displayName from the
            // dedicated form field, which is the single source of truth).
            val response = client.post("$root/uploads") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                "file",
                                file.readBytes(),
                                Headers.build {
                                    append(HttpHeaders.ContentType, mimeType)
                                    append(
                                        HttpHeaders.ContentDisposition,
                                        "filename=\"${displayName.replace("\"", "")}\""
                                    )
                                }
                            )
                            append("displayName", displayName)
                            if (!conversationId.isNullOrBlank()) {
                                append("conversationId", conversationId)
                            }
                        }
                    )
                )
            }
            when (val status = response.status.value) {
                413 -> throw AttachmentException(AttachmentFailure.TooLarge, status)
                415 -> throw AttachmentException(AttachmentFailure.Unsupported, status)
                429 -> throw AttachmentException(AttachmentFailure.RateLimited, status)
                404 -> throw AttachmentException(AttachmentFailure.ConversationMissing, status)
                in 200..299 -> Unit
                else -> throw AttachmentException(AttachmentFailure.Server, status)
            }
            val body = response.bodyAsText()
            GsApiJson.decodeFromString<UploadResponseDto>(body).attachment
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: AttachmentException) {
            throw e
        } catch (e: Exception) {
            // Connect/timeout/decoding failures — honest Network, never a guess.
            throw AttachmentException(AttachmentFailure.Network, null, e.message)
        }
    }
}
