package com.grapsee.gsai.data.chat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Durable, per-conversation progress for the Nexly plan checklist.
 *
 * This is the bridge the first version was missing: the rings were local
 * decoration, so the model never learned what the user completed. The store:
 *  - survives recomposition, scrolling and app relaunch (SharedPreferences),
 *  - is keyed by the plan fingerprint so an old checklist cannot restore onto
 *    a different plan,
 *  - produces the compact, non-visible context block the chat request sends
 *    on the NEXT turn.
 *
 * It is deliberately client-owned: checking a task is user progress, not a
 * preference rating. The model is told only what was actually marked.
 */
object NexlyPlanProgressStore {
    private const val PREFS = "nexly_plan_progress_v1"

    fun fingerprint(title: String, rows: List<Pair<String, String>>): String {
        val raw = buildString {
            append(title)
            rows.forEach { (time, text) ->
                append('\u001F').append(time).append('\u001E').append(text)
            }
        }
        return Integer.toHexString(raw.hashCode()) + "-" + rows.size
    }

    fun load(context: Context?, conversationId: String?, fingerprint: String): Set<String> {
        if (context == null || conversationId.isNullOrBlank()) return emptySet()
        val raw = prefs(context).getString(conversationId, null) ?: return emptySet()
        return runCatching {
            val root = JSONObject(raw)
            if (root.optString("fingerprint") != fingerprint) return emptySet()
            val done = root.optJSONArray("done") ?: return emptySet()
            buildSet {
                for (i in 0 until done.length()) {
                    done.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.getOrDefault(emptySet())
    }

    fun save(
        context: Context?,
        conversationId: String?,
        fingerprint: String,
        title: String,
        rows: List<Pair<String, String>>,
        done: Set<String>,
        preference: String? = null
    ) {
        if (context == null || conversationId.isNullOrBlank()) return
        val items = JSONArray()
        rows.forEach { (time, text) ->
            items.put(
                JSONObject()
                    .put("key", key(time, text))
                    .put("label", if (time.isBlank()) text else "$time — $text")
            )
        }
        val doneArray = JSONArray()
        done.forEach { doneArray.put(it) }
        val root = JSONObject()
            .put("fingerprint", fingerprint)
            .put("title", title)
            .put("items", items)
            .put("done", doneArray)
            .put("preference", preference ?: JSONObject.NULL)
            .put("updatedAt", System.currentTimeMillis())
        prefs(context).edit().putString(conversationId, root.toString()).apply()
    }

    /** "up" / "down" for the plan, or null when the user has not rated it. */
    fun loadPreference(
        context: Context?,
        conversationId: String?,
        fingerprint: String
    ): String? {
        if (context == null || conversationId.isNullOrBlank()) return null
        val raw = prefs(context).getString(conversationId, null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            if (root.optString("fingerprint") != fingerprint) return null
            root.optString("preference").takeIf { it == "up" || it == "down" }
        }.getOrNull()
    }

    /**
     * The exact text sent as `turnContext` on the next message. Empty when the
     * user has not completed anything — there is nothing for the model to
     * adapt to yet.
     */
    fun contextFor(context: Context?, conversationId: String?): String? {
        if (context == null || conversationId.isNullOrBlank()) return null
        val raw = prefs(context).getString(conversationId, null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            val done = root.optJSONArray("done") ?: JSONArray()
            val preference = root.optString("preference")
            if (done.length() == 0 && preference != "up" && preference != "down") return null
            val doneKeys = buildSet {
                for (i in 0 until done.length()) done.optString(i).let(::add)
            }
            val items = root.optJSONArray("items") ?: return null
            val completed = mutableListOf<String>()
            val pending = mutableListOf<String>()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val label = item.optString("label")
                val key = item.optString("key")
                if (label.isBlank()) continue
                if (key in doneKeys) completed += label else pending += label
            }
            val total = completed.size + pending.size
            if (total == 0) return null
            buildString {
                append("Plan: ").append(root.optString("title").ifBlank { "today's plan" })
                append("\n- Completed (").append(completed.size).append('/').append(total).append("): ")
                append(completed.joinToString("; ").ifBlank { "none" })
                append("\n- Pending (").append(pending.size).append("): ")
                append(pending.joinToString("; ").ifBlank { "none" })
                if (preference == "up" || preference == "down") {
                    append("\n- User feedback: the plan was marked ")
                    append(if (preference == "up") "helpful" else "not helpful")
                    append('.')
                }
            }
        }.getOrNull()
    }

    private fun key(time: String, text: String) = time + "|" + text

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
