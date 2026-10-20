package com.grapsee.gsai.ui.chat.content

// ---------------------------------------------------------------------------
// Terminal kind — a sandboxed shell in the transcript. Toy filesystem, arrow
// history, custom commands from the payload, and `js <expr>` evaluated by the
// SAME CalcExpr whitelist the calc widgets use — after a blocked-identifier
// check (fetch/document/storage/window…). Nothing here can reach network,
// storage or the DOM: the sandbox is the arithmetic engine itself.
// ---------------------------------------------------------------------------

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.theme.GsTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TERMINAL_BLOCKED = Regex(
    "\\b(fetch|XMLHttpRequest|WebSocket|localStorage|sessionStorage|indexedDB|document|window|globalThis|import|require|eval|Function|postMessage|location|navigator|serviceWorker|Notification|geolocation)\\b"
)

@Composable
internal fun TerminalWidgetView(w: TerminalWidget) {
    val cwd = remember(w) { mutableStateOf("/home/guest") }
    val fs = remember(w) {
        mutableStateMapOf<String, Any>().apply {
            put("/home/guest", true)
            put("/home/guest/README.md", "# Guest shell\nThis filesystem lives in this client only.")
            put("/home/guest/notes.txt", "try: echo hi · ls · cat notes.txt · tree · js 6*7")
        }
    }
    data class Line(val kind: String, val text: String)
    val lines = remember(w) { mutableStateListOf(Line("sys", w.welcome.ifBlank { "guest shell — type help for commands" })) }
    val history = remember(w) { mutableStateListOf<String>() }
    var input by remember(w) { mutableStateOf("") }

    fun push(kind: String, text: String) {
        lines.add(Line(kind, text))
        while (lines.size > 120) lines.removeAt(0)
    }

    fun run(cmdline: String) {
        val cmd = cmdline.trim()
        push("in", "${cwd.value} $ ${cmd}")
        if (cmd.isEmpty()) return
        history.add(0, cmd)
        val parts = cmd.split(Regex("\\s+"))
        val name = parts.firstOrNull() ?: return
        val args = parts.drop(1)
        fun resolve(p: String?): String {
            val raw = p ?: return cwd.value
            return when {
                raw.startsWith("/") -> raw.trimEnd('/')
                raw == ".." -> cwd.value.substringBeforeLast('/', "/")
                raw == "~" -> "/home/guest"
                else -> "${cwd.value.trimEnd('/')}/$raw"
            }
        }
        when (name) {
            "help" -> {
                push("out", "built-ins: help clear echo date whoami pwd ls tree cd cat touch mkdir rm")
                push("out", "js <expr> — sandboxed arithmetic (no network, no storage, no DOM)")
                w.commands.forEach { push("out", "${it.name} — ${it.description}") }
            }
            "clear" -> lines.clear()
            "echo" -> push("out", args.joinToString(" "))
            "date" -> push("out", SimpleDateFormat("EEE MMM d HH:mm:ss z yyyy", Locale.US).format(Date()))
            "whoami" -> push("out", "guest")
            "pwd" -> push("out", cwd.value)
            "ls" -> {
                val dir = resolve(args.firstOrNull { !it.startsWith("-") })
                val entries = fs.keys.filter { it.startsWith("$dir/") && it.substringAfter("$dir/").isNotEmpty() }
                    .map { it.removePrefix("$dir/").substringBefore('/') }
                    .distinct()
                push("out", if (entries.isEmpty()) "(empty)" else entries.joinToString("  ") { e -> if (fs["$dir/$e"] == true) "$e/" else e })
            }
            "tree" -> push("out", fs.keys.sorted().joinToString("\n") { k -> if (fs[k] == true) "$k/" else k })
            "cd" -> {
                val target = resolve(args.firstOrNull())
                if (fs[target] == true) cwd.value = target else push("err", "cd: no such directory: $target")
            }
            "cat" -> {
                val f = fs[resolve(args.firstOrNull())]
                if (f is String) push("out", f) else push("err", "cat: not a file")
            }
            "touch" -> {
                val p = resolve(args.firstOrNull())
                if (!fs.containsKey(p)) fs[p] = ""
                push("out", p)
            }
            "mkdir" -> {
                val p = resolve(args.firstOrNull())
                if (!fs.containsKey(p)) fs[p] = true
                push("out", "$p/")
            }
            "rm" -> {
                val p = resolve(args.filter { !it.startsWith("-") }.firstOrNull())
                val removed = fs.keys.filter { it == p || it.startsWith("$p/") }
                if (removed.isEmpty()) push("err", "rm: not found") else removed.forEach { fs.remove(it) }
            }
            "js" -> {
                val src = args.joinToString(" ")
                if (src.isBlank()) {
                    push("err", "js: expression required, e.g. js 6*7")
                } else if (TERMINAL_BLOCKED.containsMatchIn(src)) {
                    push("err", "js: blocked identifier — this sandbox has no network, storage or DOM access")
                } else {
                    val out = CalcExpr.eval(src, emptyMap())
                    if (out == null) push("err", "js: parse error — numbers, + - * / % ^ and parens only")
                    else push("out", if (out % 1.0 == 0.0) out.toLong().toString() else out.toString())
                }
            }
            else -> {
                val custom = w.commands.firstOrNull { it.name == name }
                if (custom != null) push("out", "${custom.name}: ${custom.description}")
                else push("err", "$name: command not found — try help")
            }
        }
    }

    WidgetCardShell(w.title, "terminal") {
        Column(
            Modifier
                .fillMaxWidth()
                .height(210.dp)
                .verticalScroll(rememberScrollState())
                .background(Color(0xFF0B0E12), RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            lines.forEach { line ->
                Text(
                    line.text,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = when (line.kind) {
                        "in" -> GsTheme.colors.textTertiary
                        "err" -> WidgetPalette.legible(WidgetPalette.at(9), true)
                        "sys" -> seriesColor(1)
                        else -> GsTheme.colors.textPrimary
                    }
                )
            }
            Text(
                "${cwd.value} $",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = seriesColor(1)
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("command") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { run(input); input = "" }) { Text("Run") }
        }
        if (history.isNotEmpty()) {
            TextButton(onClick = { input = history.first() }) { Text("↺ ${history.first()}") }
        }
    }
}
