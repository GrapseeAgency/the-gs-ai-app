package com.grapsee.gsai.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray

/**
 * The local record of conversations this reader shared out.
 *
 * There is no share backend in this build — no accounts, no recipients — so a
 * share is the moment this reader handed a conversation to the system share
 * sheet. The record keeps what is true: which conversation, when. The mock's
 * "With N people" and avatar chips describe recipients no install has, so they
 * are not drawn; the row says who shared and when, which is everything the
 * device actually knows.
 *
 * Both views of the Shared Conversations page (the mock's "Shared with Others"
 * and "Shared by You" tabs) read this one outbox: without recipients the two
 * lists coincide, and pretending otherwise would invent a difference the data
 * cannot carry.
 */
object SharedConversationsStore {

    data class Share(
        val id: String,
        val conversationId: String,
        val title: String,
        /** Epoch millis — rendered with the shared relative-time formatter. */
        val sharedAt: Long,
        /** Recipient count; 0 when the share sheet cannot know (no backend). */
        val people: Int = 0,
    )

    private const val PREFS = "gs_shared_conversations"
    private const val KEY = "gs.shared.entries"

    val entries = mutableStateListOf<Share>()

    fun init(context: Context) {
        if (entries.isNotEmpty()) return
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                entries.add(
                    Share(
                        id = o.getString("id"),
                        conversationId = o.getString("conversationId"),
                        title = o.getString("title"),
                        sharedAt = o.getLong("sharedAt"),
                        people = o.optInt("people", 0),
                    )
                )
            }
        }
        entries.sortByDescending { it.sharedAt }
    }

    /** Records one share and persists the outbox. Newest first. */
    fun record(
        context: Context,
        conversationId: String,
        title: String,
        at: Long = System.currentTimeMillis(),
        people: Int = 0,
    ) {
        entries.removeAll { it.conversationId == conversationId }
        entries.add(0, Share(id = "share-$at", conversationId = conversationId, title = title, sharedAt = at, people = people))
        persist(context)
    }

    fun remove(context: Context, id: String) {
        entries.removeAll { it.id == id }
        persist(context)
    }

    fun entryFor(conversationId: String): Share? = entries.firstOrNull { it.conversationId == conversationId }

    private fun persist(context: Context) {
        val arr = JSONArray()
        entries.forEach { s ->
            arr.put(
                org.json.JSONObject().apply {
                    put("id", s.id)
                    put("conversationId", s.conversationId)
                    put("title", s.title)
                    put("sharedAt", s.sharedAt)
                    put("people", s.people)
                }
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
