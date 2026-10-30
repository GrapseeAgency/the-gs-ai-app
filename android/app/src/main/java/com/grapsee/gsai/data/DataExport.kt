package com.grapsee.gsai.data

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.grapsee.gsai.BuildConfig
import com.grapsee.gsai.data.local.ConversationEntity
import com.grapsee.gsai.data.local.MessageEntity
import com.grapsee.gsai.data.local.SavedItemEntity
import com.grapsee.gsai.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * One privacy export, used by Data Export & Account Deletion.
 *
 * The reference offers three formats (JSON, PDF, CSV) and two scopes
 * (conversations, the personal archive), plus the complete archive. This object
 * owns every combination so the screen has no format-specific logic and a
 * failure leaves the destination file untouched rather than half-written.
 *
 * The corpus is assembled and rendered on IO. Attachment records are carried as
 * their stored JSON: the bytes of a staged attachment are not part of a chat
 * export, so the screen's copy says "attachment details" rather than promising
 * files it does not ship.
 */
object DataExport {

    enum class Format(
        val id: String,
        val label: String,
        val caption: String,
        val mime: String,
        val extension: String,
    ) {
        JSON("json", "JSON", "Structured data", "application/json", "json"),
        PDF("pdf", "PDF", "Readable document", "application/pdf", "pdf"),
        CSV("csv", "CSV", "Spreadsheet format", "text/csv", "csv"),
    }

    enum class Scope(val id: String, val fileStem: String) {
        CONVERSATIONS("conversations", "gs-ai-conversations"),
        PERSONAL_ARCHIVE("archive", "gs-ai-personal-archive"),
        COMPLETE("complete", "gs-ai-complete-archive"),
    }

    /** Default name for the system document picker. */
    fun fileName(format: Format, scope: Scope, now: Instant = Instant.now()): String {
        val stamp = FILE_STAMP.format(now)
        return "${scope.fileStem}-$stamp.${format.extension}"
    }

    /**
     * Assemble [scope] in [format] and write it to [uri].
     *
     * Kept source-compatible with the old `write(context, uri)` call: JSON plus
     * conversations was the only export the Settings sheet could produce.
     */
    suspend fun write(
        context: Context,
        uri: Uri,
        format: Format = Format.JSON,
        scope: Scope = Scope.CONVERSATIONS,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val snapshot = snapshot(context, scope)
            val bytes = when (format) {
                Format.JSON -> snapshotJson(snapshot).toString(2).toByteArray(Charsets.UTF_8)
                Format.CSV -> snapshotCsv(snapshot).toByteArray(Charsets.UTF_8)
                Format.PDF -> snapshotPdf(snapshot)
            }
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
            } != null
        }.getOrDefault(false)
    }

    // -----------------------------------------------------------------------
    // Snapshot
    // -----------------------------------------------------------------------

    private data class Snapshot(
        val scope: Scope,
        val exportedAt: String,
        val appVersion: String,
        val conversations: List<ConversationEntity>,
        val messages: List<MessageEntity>,
        val library: List<SavedItemEntity>,
        val displayName: String,
        val email: String,
        val plan: JSONObject,
        val settings: JSONObject,
    )

    private suspend fun snapshot(context: Context, scope: Scope): Snapshot {
        val db = ServiceLocator.db
        val withChats = scope != Scope.PERSONAL_ARCHIVE
        return Snapshot(
            scope = scope,
            exportedAt = Instant.now().toString(),
            appVersion = BuildConfig.VERSION_NAME,
            conversations = if (withChats) db.conversationDao().all() else emptyList(),
            messages = if (withChats) db.messageDao().all() else emptyList(),
            library = if (withChats) db.savedItemDao().observeAll().first() else emptyList(),
            displayName = AccountStore.displayName(context),
            email = AccountStore.email(context),
            plan = JSONObject().apply {
                put("id", PlanStore.planId)
                put("activated", PlanStore.activated)
                put("sinceMillis", PlanStore.sinceMillis)
            },
            settings = settingsJson(),
        )
    }

    private fun settingsJson(): JSONObject = JSONObject().apply {
        put("themeMode", SettingsStore.themeMode)
        put("accentId", SettingsStore.accentId)
        put("backgroundStyle", SettingsStore.backgroundStyle)
        put("preferLocal", SettingsStore.preferLocal)
        put("pushNotifications", SettingsStore.pushNotifications)
        put("memory", SettingsStore.memory)
        put("biometricUnlock", SettingsStore.biometricUnlock)
        put("quietHoursEnabled", SettingsStore.quietHoursEnabled)
        put("quietStartMinute", SettingsStore.quietStartMinute)
        put("quietEndMinute", SettingsStore.quietEndMinute)
        put("fontScale", SettingsStore.fontScale.toDouble())
        put("highContrast", SettingsStore.highContrast)
        put("reduceMotion", SettingsStore.reduceMotion)
        put("screenReaderHints", SettingsStore.screenReaderHints)
        put("haptics", SettingsStore.haptics)
        put("preferredModel", SettingsStore.preferredModel)
        put("reasoningEffort", SettingsStore.reasoningEffort)
        put("rememberChoices", SettingsStore.rememberChoices)
        put("dataRetentionMonths", SettingsStore.dataRetentionMonths)
        put("autoDeleteInactive", SettingsStore.autoDeleteInactive)
    }

    private fun snapshotJson(s: Snapshot): JSONObject = JSONObject().apply {
        put("exportedAt", s.exportedAt)
        put("appVersion", s.appVersion)
        put("scope", s.scope.id)
        if (s.scope != Scope.PERSONAL_ARCHIVE) {
            put("conversations", JSONArray().apply {
                s.conversations.forEach { c ->
                    put(JSONObject().apply {
                        put("id", c.id)
                        put("title", c.title)
                        put("modelId", c.modelId ?: JSONObject.NULL)
                        put("pinned", c.pinned)
                        put("archived", c.archived)
                        put("createdAt", c.createdAt)
                        put("updatedAt", c.updatedAt)
                    })
                }
            })
            put("messages", JSONArray().apply {
                s.messages.forEach { m ->
                    put(JSONObject().apply {
                        put("id", m.id)
                        put("conversationId", m.conversationId)
                        put("role", m.role)
                        put("content", m.content)
                        put("createdAt", m.createdAt)
                        put("attachments", jsonArrayOrEmpty(m.attachments))
                        put("sources", jsonArrayOrEmpty(m.sources))
                    })
                }
            })
            put("library", JSONArray().apply {
                s.library.forEach { item ->
                    put(JSONObject().apply {
                        put("id", item.id)
                        put("kind", item.kind)
                        put("title", item.title)
                        put("content", item.content)
                        put("createdAt", item.createdAt)
                    })
                }
            })
        }
        put("profile", JSONObject().apply {
            put("displayName", s.displayName)
            put("email", s.email)
        })
        put("plan", s.plan)
        put("settings", s.settings)
    }

    private fun jsonArrayOrEmpty(raw: String): JSONArray =
        runCatching { JSONArray(raw) }.getOrElse { JSONArray() }

    // -----------------------------------------------------------------------
    // CSV
    // -----------------------------------------------------------------------

    private fun snapshotCsv(s: Snapshot): String {
        val out = StringBuilder()
        out.appendLine(
            "recordType,id,parentId,title,role,createdAt,content,attachments"
        )
        if (s.scope != Scope.PERSONAL_ARCHIVE) {
            s.conversations.forEach { c ->
                out.appendLine(
                    listOf(
                        "conversation", c.id, "", c.title, "", c.createdAt, "", ""
                    ).joinToString(",", transform = ::csvField)
                )
            }
            s.messages.forEach { m ->
                out.appendLine(
                    listOf(
                        "message", m.id, m.conversationId, "", m.role, m.createdAt,
                        m.content, m.attachments,
                    ).joinToString(",", transform = ::csvField)
                )
            }
            s.library.forEach { item ->
                out.appendLine(
                    listOf(
                        "library", item.id, "", item.title, item.kind, item.createdAt,
                        item.content, "",
                    ).joinToString(",", transform = ::csvField)
                )
            }
        }
        out.appendLine(
            listOf("profile", "", "", "displayName", "", "", s.displayName, "")
                .joinToString(",", transform = ::csvField)
        )
        out.appendLine(
            listOf("profile", "", "", "email", "", "", s.email, "")
                .joinToString(",", transform = ::csvField)
        )
        s.plan.keys().forEach { key ->
            out.appendLine(
                listOf("plan", "", "", key, "", "", s.plan.opt(key)?.toString().orEmpty(), "")
                    .joinToString(",", transform = ::csvField)
            )
        }
        s.settings.keys().forEach { key ->
            out.appendLine(
                listOf("setting", "", "", key, "", "", s.settings.opt(key)?.toString().orEmpty(), "")
                    .joinToString(",", transform = ::csvField)
            )
        }
        return out.toString()
    }

    private fun csvField(value: String): String =
        "\"" + value.replace("\"", "\"\"") + "\""

    // -----------------------------------------------------------------------
    // PDF
    // -----------------------------------------------------------------------

    private fun snapshotPdf(s: Snapshot): ByteArray {
        val doc = PdfDocument()
        val writer = PdfWriter(doc)
        writer.line("GS AI data export", 20f, bold = true, gapAfter = 4f)
        writer.line("${s.scope.id} · ${s.exportedAt} · v${s.appVersion}", 9f, gapAfter = 14f)

        if (s.scope != Scope.PERSONAL_ARCHIVE) {
            writer.line("Conversations", 14f, bold = true, gapAfter = 8f)
            if (s.conversations.isEmpty()) {
                writer.line("No conversations are stored on this device.", 10f, gapAfter = 10f)
            }
            val byConversation = s.messages.groupBy { it.conversationId }
            s.conversations.forEach { c ->
                val turns = byConversation[c.id].orEmpty()
                writer.line(c.title, 12f, bold = true, gapAfter = 2f)
                writer.line(
                    "id ${c.id} · updated ${c.updatedAt} · ${turns.size} messages",
                    8.5f,
                    gapAfter = 4f,
                )
                turns.forEach { m ->
                    writer.line("${m.role} · ${m.createdAt}", 9f, bold = true, gapAfter = 1f)
                    writer.line(m.content.ifBlank { "(empty turn)" }, 9.5f, gapAfter = 2f)
                    val names = attachmentNames(m.attachments)
                    if (names.isNotEmpty()) {
                        writer.line("Attachments: ${names.joinToString(", ")}", 8.5f, gapAfter = 3f)
                    }
                }
                writer.line("", 8f, gapAfter = 8f)
            }
            if (s.library.isNotEmpty()) {
                writer.line("Library", 12f, bold = true, gapAfter = 4f)
                s.library.forEach { item ->
                    writer.line("${item.title} (${item.kind}) · ${item.createdAt}", 9.5f, gapAfter = 2f)
                    writer.line(item.content, 9f, gapAfter = 3f)
                }
                writer.line("", 8f, gapAfter = 8f)
            }
        }

        writer.line("Account", 14f, bold = true, gapAfter = 6f)
        writer.line("Name: ${s.displayName.ifBlank { "Not set" }}", 10f, gapAfter = 2f)
        writer.line("Email: ${s.email.ifBlank { "Not set" }}", 10f, gapAfter = 8f)
        writer.line("Plan: ${s.plan.optString("id")} · activated ${s.plan.optBoolean("activated")}", 10f, gapAfter = 10f)
        writer.line("Settings", 14f, bold = true, gapAfter = 6f)
        s.settings.keys().forEach { key ->
            writer.line("$key: ${s.settings.opt(key)}", 9.5f, gapAfter = 1f)
        }
        return writer.finish()
    }

    private fun attachmentNames(raw: String): List<String> {
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { index ->
            val item = arr.optJSONObject(index) ?: return@mapNotNull null
            item.optString("displayName")
                .ifBlank { item.optString("name") }
                .ifBlank { null }
        }
    }

    /** A4 pages with a simple, page-aware wrapped-text writer. */
    private class PdfWriter(private val doc: PdfDocument) {
        private val pageWidth = 595
        private val pageHeight = 842
        private val margin = 40f
        private var pageNumber = 0
        private var page = newPage()
        private var canvas = page.canvas
        private var y = margin

        private fun newPage(): PdfDocument.Page {
            if (pageNumber > 0) doc.finishPage(page)
            pageNumber += 1
            return doc.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            )
        }

        fun line(
            text: String,
            size: Float,
            bold: Boolean = false,
            gapAfter: Float = 2f,
        ) {
            val paint = Paint().apply {
                isAntiAlias = true
                textSize = size
                color = android.graphics.Color.rgb(36, 28, 26)
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            val lineHeight = size * 1.45f
            val maxWidth = pageWidth - margin * 2
            text.split('\n').forEach { paragraph ->
                if (paragraph.isEmpty()) {
                    y += lineHeight * 0.6f
                    return@forEach
                }
                var rest = paragraph
                while (rest.isNotEmpty()) {
                    if (y + lineHeight > pageHeight - margin) {
                        page = newPage()
                        canvas = page.canvas
                        y = margin
                    }
                    val count = paint.breakText(rest, true, maxWidth, null).coerceAtLeast(1)
                    canvas.drawText(rest.substring(0, count), margin, y, paint)
                    y += lineHeight
                    rest = rest.substring(count)
                }
            }
            y += gapAfter
            if (y > pageHeight - margin) {
                page = newPage()
                canvas = page.canvas
                y = margin
            }
        }

        fun finish(): ByteArray {
            doc.finishPage(page)
            val out = ByteArrayOutputStream()
            doc.writeTo(out)
            doc.close()
            return out.toByteArray()
        }
    }

    private val FILE_STAMP: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)
}
