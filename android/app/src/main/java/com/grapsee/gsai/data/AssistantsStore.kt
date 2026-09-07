package com.grapsee.gsai.data

import android.content.Context
import com.grapsee.gsai.data.model.AssistantSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local store for user-created assistants — the Create form's persistence
 * layer (create / edit / delete / pin / archive, all on-device) plus the
 * favourites set shared by samples and user assistants. Backed by
 * SharedPreferences JSON, the same local-first contract as chats: no backend
 * required, store hiccups resolve to an empty list, never an error. Sample
 * assistants stay curated (not editable, not deletable) — user data lives
 * alongside them.
 */
object AssistantsStore {
    private const val PREFS = "gs_assistants"
    private const val KEY = "gs.assistants.user"
    private const val FAV_KEY = "gs.assistants.favs"

    /** Favourites before the first real toggle — today's curated defaults. */
    private val defaultFavourites = setOf("asst-2", "asst-4")

    private var prefs: android.content.SharedPreferences? = null

    private val _assistants = MutableStateFlow<List<AssistantSample>>(emptyList())
    val assistants: StateFlow<List<AssistantSample>> = _assistants.asStateFlow()

    private val _favourites = MutableStateFlow<Set<String>>(emptySet())
    val favourites: StateFlow<Set<String>> = _favourites.asStateFlow()

    /** Call once from GSApplication before any UI reads the store. */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _assistants.value = runCatching { read() }.getOrElse { emptyList() }
        _favourites.value = runCatching {
            prefs?.getStringSet(FAV_KEY, null) ?: defaultFavourites
        }.getOrElse { emptySet() }
    }

    fun find(id: String): AssistantSample? =
        _assistants.value.firstOrNull { it.id == id }

    /**
     * Insert or update by id, newest last; list order is stable across
     * launches. The Create form doesn't know pin/archive state — an edit
     * carries the existing flags over instead of resetting them.
     */
    fun upsert(assistant: AssistantSample) {
        val carried = _assistants.value.firstOrNull { it.id == assistant.id }
            ?.let { assistant.copy(pinned = it.pinned, archived = it.archived) }
            ?: assistant
        writeRecord(carried)
    }

    fun delete(id: String) {
        val next = _assistants.value.filterNot { it.id == id }
        _assistants.value = next
        write(next)
    }

    /** Favourite any assistant (sample or user-created); survives relaunches. */
    fun toggleFavourite(id: String) {
        val next = _favourites.value.toMutableSet().apply { if (!add(id)) remove(id) }
        _favourites.value = next
        prefs?.edit()?.putStringSet(FAV_KEY, next)?.apply()
    }

    /** Pinned assistants sort to the top of My assistants (user-owned only). */
    fun togglePin(id: String) {
        val current = _assistants.value.firstOrNull { it.id == id } ?: return
        writeRecord(current.copy(pinned = !current.pinned))
    }

    /** Archived assistants leave the default tabs and rest in Archived. */
    fun setArchived(id: String, archived: Boolean) {
        val current = _assistants.value.firstOrNull { it.id == id } ?: return
        if (current.archived == archived) return
        writeRecord(current.copy(archived = archived))
    }

    private fun writeRecord(record: AssistantSample) {
        val next = _assistants.value.filterNot { it.id == record.id } + record
        _assistants.value = next
        write(next)
    }

    private fun read(): List<AssistantSample> {
        val raw = prefs?.getString(KEY, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrElse { return emptyList() }
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            AssistantSample(
                id = o.optString("id"),
                name = o.optString("name"),
                category = o.optString("category"),
                description = o.optString("description"),
                instructions = o.optString("instructions"),
                starters = stringList(o, "starters").filter { it.isNotBlank() },
                uses = o.optString("uses", "1"),
                rating = o.optDouble("rating", 5.0),
                published = o.optBoolean("published", false),
                capabilities = stringList(o, "capabilities").filter { it.isNotBlank() },
                pinned = o.optBoolean("pinned", false),
                archived = o.optBoolean("archived", false)
            )
        }.filter { it.id.isNotBlank() && it.name.isNotBlank() }
    }

    private fun stringList(o: JSONObject, key: String): List<String> {
        val arr = o.optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }
    }

    private fun write(list: List<AssistantSample>) {
        val array = JSONArray()
        list.forEach { a ->
            val o = JSONObject()
            o.put("id", a.id)
            o.put("name", a.name)
            o.put("category", a.category)
            o.put("description", a.description)
            o.put("instructions", a.instructions)
            o.put("starters", JSONArray(a.starters))
            o.put("uses", a.uses)
            o.put("rating", a.rating)
            o.put("published", a.published)
            o.put("capabilities", JSONArray(a.capabilities))
            o.put("pinned", a.pinned)
            o.put("archived", a.archived)
            array.put(o)
        }
        prefs?.edit()?.putString(KEY, array.toString())?.apply()
    }
}
