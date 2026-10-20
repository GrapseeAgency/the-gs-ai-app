package com.grapsee.gsai.ui.chat.content

// ---------------------------------------------------------------------------
// Collab kind — LIVE shared state over /api/v1/collab (SSE + POST), the same
// rooms the web widget joins. Presence = 10s heartbeat, 30s TTL server-side.
// Tier-2 honesty is printed on the card: per-server memory, last-write-wins,
// resets on restart. Every mutation goes through the server (POST) and every
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
    val sharedState = remember(w) { mutableStateMapOf<String, String>() }
    val presence = remember(w) { mutableStateListOf<CollabPeer>() }
    var status by remember(w) { mutableStateOf("connecting") }
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

    fun publish(body: JsonObject) {
        scope.launch {
            try {
                client.post(root) {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("room", w.room)
                        body.forEach { (k, v) -> put(k, v) }
                    }.toString())
                }.bodyAsText()
            } catch (_: Throwable) {
            }
        }
    }

    fun delta(key: String, by: Int) = publish(buildJsonObject {
        put("delta", buildJsonObject { put(key, by) })
    })

    LaunchedEffect(w, clientId, root) {
        while (isActive) {
            try {
                client.post(root) {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("room", w.room)
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
            client.prepareGet("$root?room=${w.room}&client=$clientId&name=$myName&color=$myColor")
                .execute { response ->
                    val channel = response.bodyAsChannel()
                    var event = ""
                    var data = ""
                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line(16384) ?: break
                        when {
                            line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                            line.startsWith("data:") -> data = line.removePrefix("data:").trim()
                            line.isEmpty() && data.isNotEmpty() -> {
                                if (event == "snapshot" || event == "rev") {
                                    try {
                                        val obj = collabJson.parseToJsonElement(data).jsonObject
                                        status = "live"
                                        sharedState.clear()
                                        (obj["state"] as? JsonObject)?.forEach { (k, v) ->
                                            val content = (v as? JsonPrimitive)?.content
                                            if (content != null) sharedState[k] = content
                                        }
                                        presence.clear()
                                        (obj["presence"] as? JsonArray)?.forEach { el ->
                                            val p = el as? JsonObject ?: return@forEach
                                            presence.add(
                                                CollabPeer(
                                                    (p["id"] as? JsonPrimitive)?.content ?: "",
                                                    (p["name"] as? JsonPrimitive)?.content ?: "guest",
                                                    (p["color"] as? JsonPrimitive)?.content ?: "#4C6FFF"
                                                )
                                            )
                                        }
                                    } catch (_: Throwable) {
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
                val items = w.items.ifEmpty { listOf("Yes", "No") }
                val votes = items.indices.map { num("v$it") }
                val total = maxOf(1, votes.sum())
                items.forEachIndexed { i, label ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { delta("v$i", 1) }
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
                val todos = (0 until 12).mapNotNull { i -> sharedState["t$i"]?.takeIf { it.isNotBlank() } }
                todos.forEachIndexed { i, item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(item, style = MaterialTheme.typography.bodySmall, color = GsTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                        Text(
                            "×",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GsTheme.colors.textTertiary,
                            modifier = Modifier
                                .clickable {
                                    publish(buildJsonObject {
                                        put("set", buildJsonObject {
                                            (0 until 12).forEach { k -> sharedState["t$k"]?.let { put("t$k", JsonPrimitive(it)) } }
                                            put("t$i", JsonPrimitive(""))
                                        })
                                    })
                                }
                                .padding(horizontal = 8.dp)
                        )
                    }
                }
                if (todos.isEmpty()) {
                    Text("empty — add something, it appears for everyone", style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text("add a shared item") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        if (draft.isNotBlank()) {
                            val idx = (0 until 12).firstOrNull { sharedState["t$it"].isNullOrBlank() } ?: return@TextButton
                            val key = draft.trim()
                            draft = ""
                            publish(buildJsonObject {
                                put("set", buildJsonObject { put("t$idx", JsonPrimitive(key)) })
                            })
                        }
                    }) { Text("Add") }
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
                                if (now - lastSent < 90 || boxSize.width == 0) return@detectDragGestures
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
                        num("n").toString(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = seriesInk(0)
                    )
                    Text(
                        w.label.ifBlank { "this number is the same for everyone, right now" },
                        style = MaterialTheme.typography.labelSmall,
                        color = GsTheme.colors.textTertiary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(-1, 1, 5).forEach { d ->
                        TextButton(onClick = { delta("n", d) }) { Text(if (d > 0) "+$d" else "$d") }
                    }
                }
            }
        }
        Text(
            "room ${w.room} · shared prototype: per-server memory, last-write-wins, resets on restart",
            style = MaterialTheme.typography.labelSmall,
            color = GsTheme.colors.textTertiary,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
