package com.grapsee.gsai.ui.chat.content

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.json.JSONArray
import org.json.JSONObject

/** Same truthful local filesystem and real bounded JS Worker on all clients.
 * Custom commands are help metadata, never fabricated execution results. */
@Composable
internal fun TerminalWidgetView(w: TerminalWidget) {
    val context = LocalContext.current
    val document = remember(context, w) {
        runCatching {
            val terminal = context.assets.open("terminal-runtime.json").bufferedReader().use { JSONObject(it.readText()) }
            val worker = context.assets.open("javascript-worker-sandbox.json").bufferedReader().use { it.readText() }
            require(JSONObject(worker).getString("template").isNotBlank())
            val commands = JSONArray()
            w.commands.forEach { commands.put(JSONObject().put("name", it.name).put("description", it.description)) }
            val payload = JSONObject().put("welcome", w.welcome).put("commands", commands)
            val template = terminal.getString("template")
            val slots = mapOf(
                terminal.getString("payloadPlaceholder") to JSONObject.quote(payload.toString()).replace("<", "\\u003c"),
                terminal.getString("workerPlaceholder") to JSONObject.quote(worker).replace("<", "\\u003c")
            )
            require(slots.keys.all { template.split(it).size == 2 })
            // Atomic callbacks preserve dollars and token-looking payload text.
            template.replace(Regex(slots.keys.joinToString("|") { Regex.escape(it) })) { slots.getValue(it.value) }
        }.getOrNull()
    }
    if (document == null) Text("Terminal unavailable: runtime contract could not be loaded. Nothing was executed.")
    else HarnessWebView(doc = document, fileBase = false)
}
