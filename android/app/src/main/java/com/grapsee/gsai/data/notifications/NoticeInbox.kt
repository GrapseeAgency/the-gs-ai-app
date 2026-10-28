package com.grapsee.gsai.data.notifications

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Notifications Inbox — the durable record of what Nexly actually told
 * this user.
 *
 * The reference sheet draws tabs with counts, read/unread dots, a Today /
 * Earlier split and "Mark all as read". Every one of those is a query over
 * this store, so the inbox cannot show a notice the app never sent, and cannot
 * claim a count it cannot back.
 *
 * Written by [GsNotifier.post] only AFTER a notice actually lands, which also
 * means the inbox and the shade agree: no entry for a post the OS refused.
 *
 * Storage is a bounded JSON ring in SharedPreferences rather than a Room
 * table. Room would need a schema bump and a migration, and this build has no
 * local compiler to catch a migration mistake; the ring is a smaller risk for
 * the same honesty guarantee. [MAX_ENTRIES] keeps it from growing forever.
 */
object NoticeInbox {

    private const val PREFS = "gs_notice_inbox"
    private const val KEY_ENTRIES = "gs.notice.inbox.entries"
    private const val MAX_ENTRIES = 200

    /** One row in the inbox. `kindId` matches [GsNotifier.Kind.kindId]. */
    data class Entry(
        val id: Long,
        val kindId: String,
        val title: String,
        val body: String,
        val createdAtMillis: Long,
        val read: Boolean,
    )

    /** Bumped on every mutation so the screen recomposes. */
    var revision by mutableStateOf(0); private set

    fun read(context: Context): List<Entry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Entry(
                            id = o.optLong("id"),
                            kindId = o.optString("kindId"),
                            title = o.optString("title"),
                            body = o.optString("body"),
                            createdAtMillis = o.optLong("createdAt"),
                            read = o.optBoolean("read"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /** Called by the dispatcher only once the notice has actually posted. */
    fun record(context: Context, kind: GsNotifier.Kind, title: String, body: String) {
        val existing = read(context)
        val entry = Entry(
            id = System.currentTimeMillis(),
            kindId = kind.kindId,
            title = title,
            body = body.take(400),
            createdAtMillis = System.currentTimeMillis(),
            read = false,
        )
        write(context, (listOf(entry) + existing).take(MAX_ENTRIES))
    }

    fun markAllRead(context: Context) {
        val updated = read(context).map { it.copy(read = true) }
        write(context, updated)
    }

    fun markRead(context: Context, id: Long) {
        write(context, read(context).map { if (it.id == id) it.copy(read = true) else it })
    }

    private fun write(context: Context, entries: List<Entry>) {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("kindId", e.kindId)
                    put("title", e.title)
                    put("body", e.body)
                    put("createdAt", e.createdAtMillis)
                    put("read", e.read)
                }
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ENTRIES, arr.toString()).apply()
        revision++
    }

    /** Real per-tab counts, computed from the stored rows. */
    fun counts(context: Context): Map<String, Int> {
        val entries = read(context)
        return mapOf(
            "all" to entries.size,
            "messages" to entries.count { it.kindId == "messages" },
            "updates" to entries.count { it.kindId == "product-updates" || it.kindId == "ai-updates" },
            "account" to entries.count { it.kindId == "account-activity" },
            // Nothing in this build produces a billing notice: there is no
            // payment processor, so the tab is honestly always empty.
            "billing" to 0,
            "tips" to entries.count { it.kindId == "tips" },
        )
    }
}
