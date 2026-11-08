package com.grapsee.gsai.ui.chat.content

// ---------------------------------------------------------------------------
// Collab kind — LIVE shared state over /api/v1/collab (SSE + POST), the same
// rooms the web widget joins. Presence = 10s heartbeat, 30s TTL server-side.
// Tier-2 honesty is printed on the card: public SQLite-backed state,
// last-write-wins lists, idle eviction and no privacy/durability guarantee. Every mutation goes through the server (POST) and every
// view converges on the server's authoritative revision (SSE snapshot/rev).
// ---------------------------------------------------------------------------

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.BuildConfig
import com.grapsee.gsai.ui.theme.GsTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import kotlin.math.roundToInt

private data class CollabPeer(val id: String, val name: String, val color: String)

private val collabJson = Json { ignoreUnknownKeys = true }

@Composable
internal fun CollabWidgetView(w: CollabWidgetData) {
    val scope = rememberCoroutineScope()
    val clientId = remember(w) { "a" + UUID.randomUUID().toString().replace("-", "").take(7) }
    val myName = remember(w) { "Guest-${clientId.take(3)}" }
    val myColor = "#4C6FFF"
    val sharedState = remember(w) {
        mutableStateMapOf<String, String>().apply {
            if (w.surface == "counter") put("n", w.start.toString())
            if (w.surface == "todo") put("todos", JsonArray(w.items.map { item ->
                buildJsonObject { put("text", item); put("done", false) }
            }).toString())
        }
    }
    val presence = remember(w) { mutableStateListOf<CollabPeer>() }
    var status by remember(w) { mutableStateOf("connecting") }
    var revision by remember(w) { mutableStateOf(-1L) }
    var pending by remember(w) { mutableStateOf(false) }
    var publishError by remember(w) { mutableStateOf<String?>(null) }
    val myVotes = remember(w) { mutableStateListOf<Int>() }
    val initial = remember(w) { buildJsonObject {
        if (w.surface == "counter") put("n", w.start)
        if (w.surface == "todo") put("todos", JsonArray(w.items.map { item ->
            buildJsonObject { put("text", item); put("done", false) }
        }))
    } }
    var draft by remember(w) { mutableStateOf("") }
    var boxSize by remember(w) { mutableStateOf(IntSize.Zero) }

    val root = remember(w) { BuildConfig.BASE_URL.trimEnd('/') + "/api/v1/collab" }
    val client = remember(w) {
        HttpClient(OkHttp) {
            engine { config { readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS) } }
            expectSuccess = false
        }
    }
    DisposableEffect(w) {
        onDispose { client.close() }
    }

    fun acceptSnapshot(obj: JsonObject) {
        val nextState = obj["state"] as? JsonObject ?: error("Missing shared state")
        val nextRevision = (obj["rev"] as? JsonPrimitive)?.content?.toLongOrNull() ?: error("Missing shared revision")
        if (nextRevision < revision) return
        revision = nextRevision
        sharedState.clear()
        nextState.forEach { (key, value) ->
            sharedState[key] = if (value is JsonPrimitive) value.content else value.toString()
        }
        (obj["presence"] as? JsonArray)?.let { peers ->
            presence.clear()
            peers.forEach { element ->
                val peer = element as? JsonObject ?: return@forEach
                presence.add(CollabPeer(
                    (peer["id"] as? JsonPrimitive)?.content ?: "",
                    (peer["name"] as? JsonPrimitive)?.content ?: "guest",
                    (peer["color"] as? JsonPrimitive)?.content ?: "#4C6FFF"
                ))
            }
        }
    }

    fun publish(body: JsonObject, onSaved: () -> Unit = {}) {
        if (pending || status != "live") return
        pending = true
        scope.launch {
            try {
                val response = withTimeout(15_000L) { client.post(root) {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("room", w.room)
                        put("initial", initial)
                        body.forEach { (key, value) -> put(key, value) }
                    }.toString())
                } }
                require(response.status.value in 200..299) { "Shared server rejected the write" }
                acceptSnapshot(collabJson.parseToJsonElement(response.bodyAsText()).jsonObject)
                publishError = null
                onSaved()
            } catch (_: TimeoutCancellationException) {
                publishError = "Not confirmed: shared write timed out. Your input is retained; the server may still have received it."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                publishError = "Not confirmed: the shared server did not acknowledge this change. Your input is retained."
            } finally {
                pending = false
            }
        }
    }

    fun delta(key: String, by: Int, onSaved: () -> Unit = {}) = publish(buildJsonObject {
        put("delta", buildJsonObject { put(key, by) })
    }, onSaved)

    LaunchedEffect(w, clientId, root) {
        while (isActive) {
            try {
                client.post(root) {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("room", w.room)
                        put("initial", initial)
                        put("heartbeat", buildJsonObject {
                            put("id", clientId)
                            put("name", myName)
                            put("color", myColor)
                        })
                    }.toString())
                }
            } catch (_: Throwable) {
            }
            delay(10_000L)
        }
    }

    LaunchedEffect(w, clientId, root) {
        try {
            client.prepareGet(root) {
                url {
                    parameters.append("room", w.room)
                    parameters.append("client", clientId)
                    parameters.append("name", myName)
                    parameters.append("color", myColor)
                }
            }.execute { response ->
                    require(response.status.value in 200..299 && response.headers["Content-Type"]?.startsWith("text/event-stream") == true) { "Shared stream unavailable" }
                    val channel = response.bodyAsChannel()
                    var event = ""
                    var data = ""
                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line(65536) ?: break
                        when {
                            line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                            line.startsWith("data:") -> data = line.removePrefix("data:").trim()
                            line.isEmpty() && data.isNotEmpty() -> {
                                if (event == "snapshot" || event == "rev") {
                                    try {
                                        val obj = collabJson.parseToJsonElement(data).jsonObject
                                        acceptSnapshot(obj)
                                        status = "live"
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Throwable) {
                                        status = "offline"
                                    }
                                }
                                event = ""
                                data = ""
                            }
                        }
                    }
                }
            if (status != "offline") status = "offline"
        } catch (_: Throwable) {
            status = "offline"
        }
    }

    fun num(key: String): Int = sharedState[key]?.toDoubleOrNull()?.toInt() ?: 0
    fun colorOf(hex: String): Color = parseHexColor(hex) ?: seriesInk(0)
    val dotColor = { s: String -> if (s == "live") seriesInk(1) else if (s == "connecting") seriesInk(2) else seriesInk(9) }

    WidgetCardShell(w.title.ifEmpty { "Shared — ${w.surface}" }, "collab") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            presence.take(5).forEach { p ->
                Box(Modifier.padding(end = 4.dp).size(10.dp).background(colorOf(p.color), CircleShape))
            }
            Text(
                if (presence.isNotEmpty()) "${presence.size} live" else "you",
                style = MaterialTheme.typography.labelSmall,
                color = GsTheme.colors.textTertiary,
                modifier = Modifier.weight(1f)
            )
            Box(Modifier.padding(end = 4.dp).size(7.dp).background(dotColor(status), CircleShape))
            Text(status, style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textSecondary)
        }
        when (w.surface) {
            "vote" -> {
                val items = w.items
                val votes = items.indices.map { num("v$it") }
                val total = maxOf(1, votes.sum())
                items.forEachIndexed { i, label ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable(enabled = status == "live" && !pending && !myVotes.contains(i)) { delta("v$i", 1) { myVotes.add(i) } }
                    ) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(label, style = MaterialTheme.typography.bodySmall, color = GsTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                            Text(votes[i].toString(), style = MaterialTheme.typography.bodySmall, color = seriesInk(0), fontWeight = FontWeight.SemiBold)
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(GsTheme.colors.divider.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth((votes[i].toFloat() / total).coerceIn(0f, 1f))
                                    .height(6.dp)
                                    .background(seriesInk(i % 12).copy(alpha = 0.55f), RoundedCornerShape(3.dp))
                            )
                        }
                    }
                }
                Text("tap an option to vote — one vote per viewer per option", style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary, modifier = Modifier.padding(top = 6.dp))
            }
            "todo" -> {
                val todos = try {
                    (collabJson.parseToJsonElement(sharedState["todos"] ?: "[]") as? JsonArray)?.toList() ?: emptyList()
                } catch (_: Throwable) { emptyList() }
                fun replaceTodos(next: List<kotlinx.serialization.json.JsonElement>, onSaved: () -> Unit = {}) = publish(buildJsonObject {
                    put("set", buildJsonObject { put("todos", JsonArray(next)) })
                }, onSaved)
                todos.forEachIndexed { index, element ->
                    val item = element as? JsonObject ?: return@forEachIndexed
                    val text = (item["text"] as? JsonPrimitive)?.content ?: return@forEachIndexed
                    val done = (item["done"] as? JsonPrimitive)?.content == "true"
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = done, enabled = status == "live" && !pending, onCheckedChange = { nextDone ->
                            replaceTodos(todos.mapIndexed { i, current -> if (i == index) buildJsonObject { put("text", text); put("done", nextDone) } else current })
                        })
                        Text(text, style = MaterialTheme.typography.bodySmall, color = GsTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                        TextButton(enabled = status == "live" && !pending, onClick = {
                            replaceTodos(todos.filterIndexed { i, _ -> i != index })
                        }) { Text("Remove") }
                    }
                }
                if (todos.isEmpty()) Text("No shared items in the current snapshot.", style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary)
                if (todos.size >= 12) Text("Shared prototype capacity: 12 items. Remove an item before adding another.", style = MaterialTheme.typography.labelSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = draft, onValueChange = { draft = it }, label = { Text("add a shared item") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(enabled = status == "live" && !pending && draft.isNotBlank() && todos.size < 12, onClick = {
                        val original = draft
                        replaceTodos(todos + buildJsonObject { put("text", original.trim()); put("done", false) }) {
                            if (draft == original) draft = ""
                        }
                    }) { Text(if (pending) "Saving…" else "Add") }
                }
            }
            "pointer" -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .onSizeChanged { boxSize = it }
                        .background(GsTheme.colors.appBackground, RoundedCornerShape(10.dp))
                        .pointerInput(w, clientId) {
                            var lastSent = 0L
                            detectDragGestures { change, _ ->
                                change.consume()
                                val now = System.currentTimeMillis()
                                if (status != "live" || pending || now - lastSent < 90 || boxSize.width == 0) return@detectDragGestures
                                lastSent = now
                                val nx = ((change.position.x / size.width) * 1000).roundToInt() / 1000.0
                                val ny = ((change.position.y / size.height) * 1000).roundToInt() / 1000.0
                                publish(buildJsonObject {
                                    put("set", buildJsonObject {
                                        put("px.$clientId", JsonPrimitive(nx))
                                        put("py.$clientId", JsonPrimitive(ny))
                                    })
                                    put("heartbeat", buildJsonObject {
                                        put("id", clientId)
                                        put("name", myName)
                                        put("color", myColor)
                                    })
                                })
                            }
                        }
                ) {
                    presence.filter { it.id != clientId }.forEach { p ->
                        val pxS = sharedState["px.${p.id}"]?.toFloatOrNull()
                        val pyS = sharedState["py.${p.id}"]?.toFloatOrNull()
                        if (pxS != null && pyS != null && boxSize.width > 0) {
                            Column(
                                Modifier
                                    .offset {
                                        IntOffset(
                                            (pxS * boxSize.width).roundToInt(),
                                            (pyS * boxSize.height).roundToInt()
                                        )
                                    }
                            ) {
                                Text(
                                    p.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.background(colorOf(p.color), RoundedCornerShape(999.dp)).padding(horizontal = 6.dp)
                                )
                            }
                        }
                    }
                    Text(
                        "move here — everyone sees your cursor · cursors expire 30s after movement stops",
                        style = MaterialTheme.typography.labelSmall,
                        color = GsTheme.colors.textTertiary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            else -> {
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        (sharedState["n"]?.toDoubleOrNull() ?: w.start).let { value ->
                            if (value % 1.0 == 0.0 && value >= Long.MIN_VALUE.toDouble() && value < Long.MAX_VALUE.toDouble()) value.toLong().toString() else value.toString()
                        },
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = seriesInk(0)
                    )
                    Text(
                        if (status == "live" && sharedState.containsKey("n")) w.label.ifBlank { "Confirmed shared state" } else "Configured start; awaiting a confirmed shared value.",
                        style = MaterialTheme.typography.labelSmall,
                        color = GsTheme.colors.textTertiary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(-1, 1, 5).forEach { d ->
                        TextButton(enabled = status == "live" && !pending, onClick = { delta("n", d) }) { Text(if (d > 0) "+$d" else "$d") }
                    }
                }
            }
        }
        publishError?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textSecondary) }
        Text(
            "room ${w.room} · shared prototype: public SQLite-backed room, last-write-wins; state can survive restarts and idle rooms may be evicted",
            style = MaterialTheme.typography.labelSmall,
            color = GsTheme.colors.textTertiary,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
