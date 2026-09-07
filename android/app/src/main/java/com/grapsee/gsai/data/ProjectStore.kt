package com.grapsee.gsai.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime
import java.util.UUID

/**
 * The projects contract — every project card, hero and detail tab answers
 * from user-created, on-device data. A project bundles what a reader
 * actually has: chats they linked, instructions they wrote, a timestamp
 * that moves when they touch it, and an activity log of real actions.
 * Nothing seeds, nothing pretends: files and teammates are honest gates
 * until their subsystems land. State lives in a snapshot list (observable
 * from any composable) and writes through to SharedPreferences the moment
 * it changes, the same local-first shape as AssistantsStore and
 * SettingsStore. init() hydrates once from GSApplication, before any UI
 * reads the store.
 */
object ProjectStore {
    private const val PREFS = "gs_projects"
    private const val KEY = "gs.projects.v1"
    private const val MAX_EVENTS = 30

    /** One real action on a project — created, renamed, chats linked. */
    data class ProjectEvent(val text: String, val at: String)

    data class Project(
        val id: String,
        val name: String,
        val blurb: String,
        val instructions: String,
        val createdAt: String,
        val updatedAt: String,
        val chatIds: List<String>,
        val events: List<ProjectEvent>
    )

    var projects by mutableStateOf(emptyList<Project>()); private set

    private var prefs: SharedPreferences? = null

    /** Call once from GSApplication before any UI reads the store. */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs?.getString(KEY, null)
        projects = raw?.let(::decode) ?: emptyList()
    }

    fun byId(id: String): Project? = projects.firstOrNull { it.id == id }

    fun create(name: String, blurb: String, instructions: String): Project {
        val now = OffsetDateTime.now().toString()
        val project = Project(
            id = "proj-" + UUID.randomUUID().toString(),
            name = name.trim(),
            blurb = blurb.trim(),
            instructions = instructions.trim(),
            createdAt = now,
            updatedAt = now,
            chatIds = emptyList(),
            events = listOf(ProjectEvent("Project created", now))
        )
        projects = listOf(project) + projects
        persist()
        return project
    }

    /** Edit flow: rename, re-describe, rewrite instructions — logs one event. */
    fun update(id: String, name: String, blurb: String, instructions: String) {
        val current = byId(id) ?: return
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        val now = OffsetDateTime.now().toString()
        val renamed = trimmedName != current.name
        val updated = current.copy(
            name = trimmedName,
            blurb = blurb.trim(),
            instructions = instructions.trim(),
            updatedAt = now,
            events = current.events +
                ProjectEvent(if (renamed) "Renamed to \"$trimmedName\"" else "Project details edited", now)
        )
        replace(updated)
    }

    fun delete(id: String) {
        projects = projects.filterNot { it.id == id }
        persist()
    }

    /** Link a batch of chats picked in the detail sheet — one event per batch. */
    fun linkChats(id: String, chatIds: List<String>, chatTitles: Map<String, String>) {
        val current = byId(id) ?: return
        val fresh = chatIds.filterNot { it in current.chatIds }
        if (fresh.isEmpty()) return
        val now = OffsetDateTime.now().toString()
        val label = chatTitles[fresh.first()] ?: "a chat"
        val event = if (fresh.size == 1) "Linked chat \"$label\""
        else "Linked ${fresh.size} chats"
        replace(
            current.copy(
                chatIds = current.chatIds + fresh,
                updatedAt = now,
                events = current.events + ProjectEvent(event, now)
            )
        )
    }

    fun unlinkChat(id: String, chatId: String) {
        val current = byId(id) ?: return
        if (chatId !in current.chatIds) return
        val now = OffsetDateTime.now().toString()
        replace(
            current.copy(
                chatIds = current.chatIds - chatId,
                updatedAt = now,
                events = current.events + ProjectEvent("Removed a chat", now)
            )
        )
    }

    private fun replace(updated: Project) {
        projects = projects.map { if (it.id == updated.id) updated else it }
        persist()
    }

    private fun persist() {
        val array = JSONArray()
        projects.forEach { project ->
            val obj = JSONObject()
            obj.put("id", project.id)
            obj.put("name", project.name)
            obj.put("blurb", project.blurb)
            obj.put("instructions", project.instructions)
            obj.put("createdAt", project.createdAt)
            obj.put("updatedAt", project.updatedAt)
            obj.put("chatIds", JSONArray(project.chatIds))
            val events = JSONArray()
            project.events.forEach { event ->
                events.put(JSONObject().put("text", event.text).put("at", event.at))
            }
            obj.put("events", events)
            array.put(obj)
        }
        prefs?.edit()?.putString(KEY, array.toString())?.apply()
    }

    /** Tolerant decode — a hiccup in one row never erases the others. */
    private fun decode(raw: String): List<Project> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val id = obj.optString("id")
            if (id.isEmpty()) return@mapNotNull null
            val chatIds = mutableListOf<String>()
            val idArray = obj.optJSONArray("chatIds")
            if (idArray != null) {
                (0 until idArray.length()).forEach { chatIds.add(idArray.optString(it)) }
            }
            val events = mutableListOf<ProjectEvent>()
            val eventArray = obj.optJSONArray("events")
            if (eventArray != null) {
                (0 until eventArray.length()).forEach { inner ->
                    val eventObj = eventArray.optJSONObject(inner) ?: return@forEach
                    events.add(
                        ProjectEvent(
                            text = eventObj.optString("text"),
                            at = eventObj.optString("at")
                        )
                    )
                }
            }
            Project(
                id = id,
                name = obj.optString("name"),
                blurb = obj.optString("blurb"),
                instructions = obj.optString("instructions"),
                createdAt = obj.optString("createdAt"),
                updatedAt = obj.optString("updatedAt"),
                chatIds = chatIds,
                events = events.takeLast(MAX_EVENTS)
            )
        }
    }.getOrDefault(emptyList())
}
