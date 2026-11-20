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

    /**
     * Marks everything read — or, when [kinds] is given, only the notices the
     * reader is actually looking at.
     *
     * The inbox draws this action inside a filtered list, so a global sweep
     * silently cleared tabs the reader never opened: press "Mark all as read"
     * on Account and your three unread messages lose their dots too. The
     * button must do what its position says it does.
     */
    fun markAllRead(context: Context, kinds: Set<String>? = null) {
        val updated = read(context).map {
            if (kinds == null || it.kindId in kinds) it.copy(read = true) else it
        }
        write(context, updated)
    }

    fun markRead(context: Context, id: Long) {
        write(context, read(context).map { if (it.id == id) it.copy(read = true) else it })
    }

    /** The reader put the dot back: the notice is unread again, not new. */
    fun markUnread(context: Context, id: Long) {
        write(context, read(context).map { if (it.id == id) it.copy(read = false) else it })
    }

    /** Removes one notice from this device. The ring buffer may still trim. */
    fun delete(context: Context, id: Long) {
        write(context, read(context).filterNot { it.id == id })
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

    /**
     * Real per-tab counts, computed from the stored rows.
     *
     * One key per kind the app can actually send. There used to be a `billing`
     * key hard-wired to 0 for a chip that could never fill: this build has no
     * payment processor, so a Billing tab was a permanent empty room. Tips, a
     * real kind with its own switch in Settings, had no key and no chip — you
     * could receive one and never filter to it.
     */
    fun counts(context: Context): Map<String, Int> {
        val entries = read(context)
        return mapOf(
            "all" to entries.size,
            "messages" to entries.count { it.kindId == "messages" },
            "updates" to entries.count { it.kindId == "product-updates" || it.kindId == "ai-updates" },
            "account" to entries.count { it.kindId == "account-activity" },
            "tips" to entries.count { it.kindId == "tips" },
        )
    }
}
