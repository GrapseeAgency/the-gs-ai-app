package com.grapsee.gsai.data

import android.content.Context
import android.net.Uri
import com.grapsee.gsai.BuildConfig
import com.grapsee.gsai.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

/**
 * One privacy export, used by both the old settings sheet and the reference
 * Privacy page. The corpus is assembled and written on IO; a failure leaves
 * the destination file untouched rather than half-written.
 */
object DataExport {
    suspend fun write(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val db = ServiceLocator.db
            val payload = JSONObject().apply {
                put("exportedAt", OffsetDateTime.now().toString())
                put("appVersion", BuildConfig.VERSION_NAME)
                put("conversations", JSONArray().apply {
                    db.conversationDao().all().forEach { c ->
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
                    db.messageDao().all().forEach { m ->
                        put(JSONObject().apply {
                            put("id", m.id)
                            put("conversationId", m.conversationId)
                            put("role", m.role)
                            put("content", m.content)
                            put("createdAt", m.createdAt)
                        })
                    }
                })
                put("library", JSONArray().apply {
                    db.savedItemDao().observeAll().first().forEach { item ->
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
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(payload.toString(2).toByteArray(Charsets.UTF_8))
            } != null
        }.getOrDefault(false)
    }
}
