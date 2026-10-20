package com.grapsee.gsai.ui.chat.content

// ---------------------------------------------------------------------------
// Editor kind — the in-chat application editors. The sheet has a REAL formula
// engine (A1 refs, SUM/AVG/MIN/MAX/COUNT ranges, cycle guard — the Kotlin twin
// of the web renderer's engine), the pivot actually groups, the site builder
// reorders live, the css editor emits the declaration block it renders, the
// video timeline runs a real playhead. Audio is visual-only here and SAYS SO
// (WebAudio playback stays web-only) — honesty over faking a play button.
// ---------------------------------------------------------------------------

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.theme.GsTheme
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// --- sheet engine ------------------------------------------------------------

/** Evaluates one cell: numbers, refs, ranges, arithmetic. Null = error/cycle. */
internal fun evalSheetCell(raw: String, get: (String) -> String, depth: Int = 0): Double? {
    val v = raw.trim()
    if (v.isEmpty()) return null
    if (!v.startsWith("=")) return v.replace(",", "").toDoubleOrNull()
    if (depth > 8) return null
    var expr = v.drop(1)
    // range functions first: SUM(A1:B3) etc.
    val rangeFn = Regex("(SUM|AVG|AVERAGE|MIN|MAX|COUNT)\\(([A-L])(\\d{1,2}):([A-L])(\\d{1,2})\\)", RegexOption.IGNORE_CASE)
    expr = rangeFn.replace(expr) { m ->
        val c1 = m.groupValues[2].uppercase()[0] - 'A'
        val c2 = m.groupValues[4].uppercase()[0] - 'A'
        val r1 = m.groupValues[3].toIntOrNull() ?: 1
        val r2 = m.groupValues[5].toIntOrNull() ?: 1
        val vals = mutableListOf<Double>()
        for (c in min(c1, c2)..max(c1, c2)) for (r in min(r1, r2)..max(r1, r2)) {
            evalSheetCell(get("${'A' + c}$r"), get, depth + 1)?.let { vals.add(it) }
        }
        when (m.groupValues[1].uppercase()) {
            "SUM" -> vals.sum()
            "AVG", "AVERAGE" -> if (vals.isEmpty()) 0.0 else vals.sum() / vals.size
            "MIN" -> vals.minOrNull() ?: 0.0
            "MAX" -> vals.maxOrNull() ?: 0.0
            else -> vals.size.toDouble()
        }.toString()
    }
    // single-cell refs
    val ref = Regex("\\b([A-L])(\\d{1,2})\\b", RegexOption.IGNORE_CASE)
    expr = ref.replace(expr) { m ->
        (evalSheetCell(get("${m.groupValues[1].uppercase()}${m.groupValues[2]}"), get, depth + 1) ?: 0.0).toString()
    }
    return CalcExpr.eval(expr, emptyMap())
}

private fun colName(i: Int): String = ('A' + i).toString()

@Composable
internal fun EditorWidgetView(w: EditorWidgetData) {
    when (w.editor) {
        "pivot" -> PivotEditorView(w)
        "nodes" -> NodesEditorView(w)
        "site" -> SiteEditorView(w)
        "css" -> CssEditorView(w)
        "video" -> VideoEditorView(w)
        "audio" -> AudioEditorView(w)
        else -> SheetEditorView(w)
    }
}

@Composable
private fun SheetEditorView(w: EditorWidgetData) {
    val cells = remember(w) {
        mutableStateMapOf<String, String>().apply {
            put("B1", "120"); put("B2", "180"); put("B3", "95")
            put("B4", "=SUM(B1:B3)"); put("B5", "=AVG(B1:B3)"); put("C1", "=B4*1.2")
            putAll(w.cells.take(200).toMap())
        }
    }
    var editing by remember { mutableStateOf<String?>(null) }
    val cols = 6
    val rows = 10
    WidgetCardShell(w.title.ifEmpty { "Spreadsheet" }, "editor") {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row {
                Box(Modifier.width(30.dp))
                repeat(cols) { c ->
                    Box(Modifier.width(76.dp)) {
                        Text(colName(c), style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary)
                    }
                }
            }
            repeat(rows) { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${r + 1}", style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary, modifier = Modifier.width(30.dp))
                    repeat(cols) { c ->
                        val ref = "${colName(c)}${r + 1}"
                        val raw = cells[ref] ?: ""
                        val value = if (raw.isEmpty()) "" else {
                            val evaluated = evalSheetCell(raw) { cells[it] ?: "" }
                            if (evaluated == null) raw else {
                                val rounded = (evaluated * 100).toLong() / 100.0
                                if (raw.startsWith("=")) rounded.toString() else raw
                            }
                        }
                        val isFormula = raw.startsWith("=")
                        Box(
                            Modifier
                                .width(76.dp)
                                .border(0.5.dp, GsTheme.colors.divider)
                                .clickable { editing = ref }
                                .padding(horizontal = 6.dp, vertical = 7.dp)
                        ) {
                            Text(
                                value,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isFormula) seriesColor(0) else GsTheme.colors.textPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
        if (editing != null) {
            OutlinedTextField(
                value = cells[editing ?: ""] ?: "",
                onValueChange = { editing?.let { e -> cells[e] = it } },
                label = { Text(editing ?: "") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            TextButton(onClick = { editing = null }) { Text("Done") }
        } else {
            Text(
                "tap a cell to edit · formulas start with = · =SUM(B1:B3), =AVG, =A1*B2",
                style = MaterialTheme.typography.labelSmall,
                color = GsTheme.colors.textTertiary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

// --- pivot -------------------------------------------------------------------

@Composable
private fun PivotEditorView(w: EditorWidgetData) {
    val rows = w.rows.ifEmpty { listOf(listOf("Region", "Product", "Revenue"), listOf("Dhaka", "Pro", "420"), listOf("Chattogram", "Basic", "180"), listOf("Dhaka", "Basic", "260")) }
    val header = rows.firstOrNull() ?: return
    val body = rows.drop(1)
    var dim by remember(w) { mutableIntStateOf(0) }
    var agg by remember(w) { mutableIntStateOf(0) } // 0 sum 1 avg 2 count
    val measureCol = header.size - 1
    val grouped = remember(dim, agg, w) {
        val map = LinkedHashMap<String, MutableList<Double>>()
        body.forEach { row ->
            val key = row.getOrNull(dim)?.takeIf { it.isNotBlank() } ?: "—"
            val num = row.getOrNull(measureCol)?.replace(Regex("[^0-9.\\-]"), "")?.toDoubleOrNull() ?: 0.0
            map.getOrPut(key) { mutableListOf() }.add(num)
        }
        map.entries.sortedByDescending { it.value.sum() }
    }
    WidgetCardShell(w.title.ifEmpty { "Pivot" }, "editor") {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { dim = (dim + 1) % max(1, header.size - 1) }) { Text("by ${header.getOrNull(dim) ?: "—"}") }
            TextButton(onClick = { agg = (agg + 1) % 3 }) { Text(if (agg == 0) "sum" else if (agg == 1) "avg" else "count") }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(header.getOrNull(dim) ?: "", style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary, modifier = Modifier.weight(1f))
            Text("value", style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary)
            Box(Modifier.width(28.dp))
        }
        grouped.forEach { (key, vals) ->
            val value = when (agg) {
                0 -> vals.sum()
                1 -> if (vals.isEmpty()) 0.0 else vals.sum() / vals.size
                else -> vals.size.toDouble()
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(key, style = MaterialTheme.typography.bodySmall, color = GsTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                Text(
                    if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(value),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = seriesColor(0)
                )
                Box(Modifier.width(28.dp))
            }
        }
    }
}

// --- nodes (visual programming) ----------------------------------------------

private data class V3NodeState(var x: Float, var y: Float, val label: String, val kind: String, var value: Double)

@Composable
private fun NodesEditorView(w: EditorWidgetData) {
    val nodes = remember(w) {
        mutableStateListOf(
            *w.nodes.take(12).mapIndexed { i, n ->
                V3NodeState(n.x, n.y, n.label, n.group.ifEmpty { if (i == 0) "input" else "add" }, 1.0)
            }.ifEmpty {
                listOf(
                    V3NodeState(0.12f, 0.3f, "A", "input", 6.0),
                    V3NodeState(0.12f, 0.7f, "B", "input", 9.0),
                    V3NodeState(0.5f, 0.5f, "A+B", "add", 0.0)
                )
            }.toTypedArray()
        )
    }
    val links = remember(w) { mutableStateListOf(*w.edges.map { listOf(it[0], it[1]) }.toTypedArray()) }
    var pxSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    fun valueOf(id: Int, depth: Int = 0): Double {
        val node = nodes.getOrNull(id) ?: return 0.0
        if (depth > 10) return 0.0
        return when (node.kind) {
            "input" -> node.value
            else -> {
                val ins = links.filter { it.getOrNull(1) == id }.mapNotNull { it.getOrNull(0) }.map { valueOf(it, depth + 1) }
                if (node.kind == "multiply") ins.fold(1.0) { a, b -> a * b } else ins.sum()
            }
        }
    }
    WidgetCardShell(w.title.ifEmpty { "Node graph" }, "editor") {
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .onSizeChanged { pxSize = it }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                links.forEach { l ->
                    val a = nodes.getOrNull(l.getOrNull(0) ?: -1) ?: return@forEach
                    val b = nodes.getOrNull(l.getOrNull(1) ?: -1) ?: return@forEach
                    val ax = a.x * size.width
                    val ay = a.y * size.height
                    val bx = b.x * size.width
                    val by = b.y * size.height
                    drawLine(seriesColor(0).copy(alpha = 0.7f), Offset(ax, ay), Offset(bx, by), strokeWidth = 2f)
                }
            }
            nodes.forEachIndexed { i, node ->
                Column(
                    Modifier
                        .offset { androidx.compose.ui.unit.IntOffset((node.x * pxSize.width).roundToInt() - 40, (node.y * pxSize.height).roundToInt() - 18) }
                        .background(GsTheme.colors.elevatedSurface, RoundedCornerShape(8.dp))
                        .border(1.5.dp, seriesColor(0).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable {
                            if (node.kind == "input") node.value += 1.0
                        }
                        .pointerInput(i, pxSize) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                if (pxSize.width > 0) {
                                    node.x = (node.x + dragAmount.x / pxSize.width).coerceIn(0.08f, 0.92f)
                                    node.y = (node.y + dragAmount.y / pxSize.height).coerceIn(0.1f, 0.9f)
                                }
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(node.label, style = MaterialTheme.typography.labelMedium, color = GsTheme.colors.textPrimary)
                    Text(
                        if (node.kind == "input") "tap +1 → ${node.value.toLong()}" else "= ${valueOf(i)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = seriesColor(0)
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = {
                val id = nodes.size
                nodes.add(V3NodeState(0.1f, 0.2f + (id % 4) * 0.15f, "In $id", "input", 1.0))
                if (id >= 2) links.add(listOf(id, 0))
            }) { Text("+ input") }
            TextButton(onClick = {
                val id = nodes.size
                nodes.add(V3NodeState(0.7f, 0.5f, "Sum", "add", 0.0))
                links.forEachIndexed { idx, l -> if (idx < 2) links.add(listOf(l[0], id)) }
            }) { Text("+ sum") }
        }
        Text(
            "drag nodes to move · tap an input to bump it · values flow live",
            style = MaterialTheme.typography.labelSmall,
            color = GsTheme.colors.textTertiary
        )
    }
}

// --- site builder ------------------------------------------------------------

@Composable
private fun SiteEditorView(w: EditorWidgetData) {
    val blocks = remember(w) {
        mutableStateListOf(
            *(w.blocks.take(20).ifEmpty {
                listOf(SiteBlock("hero", "Launch day"), SiteBlock("text", "Everything ships today."), SiteBlock("button", "Get started"))
            }).toTypedArray()
        )
    }
    WidgetCardShell(w.title.ifEmpty { "Site builder" }, "editor") {
        Column(Modifier.verticalScroll(rememberScrollState()).height(220.dp)) {
            blocks.forEachIndexed { i, block ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⠿", style = MaterialTheme.typography.labelMedium, color = GsTheme.colors.textTertiary, modifier = Modifier.width(20.dp))
                    Box(Modifier.weight(1f)) {
                        val text = block.text
                        when (block.type) {
                            "hero" -> Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = seriesColor(0))
                            "button" -> Text(
                                text,
                                style = MaterialTheme.typography.labelMedium,
                                color = GsTheme.colors.appBackground,
                                modifier = Modifier.background(seriesColor(0), RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                            "image" -> Box(Modifier.fillMaxWidth().height(28.dp).background(seriesColor(i % 12).copy(alpha = 0.14f), RoundedCornerShape(6.dp))) {
                                Text("🖼 $text", style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary, modifier = Modifier.padding(4.dp))
                            }
                            else -> Text(text, style = MaterialTheme.typography.bodySmall, color = GsTheme.colors.textSecondary)
                        }
                    }
                    TextButton(onClick = { if (i > 0) { blocks.add(i - 1, blocks.removeAt(i)) } }) { Text("↑") }
                    TextButton(onClick = { if (i < blocks.size - 1) { blocks.add(i + 1, blocks.removeAt(i)) } }) { Text("↓") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("hero", "text", "image", "button").forEach { kind ->
                TextButton(onClick = { blocks.add(SiteBlock(kind, "$kind block")) }) { Text("+$kind") }
            }
        }
    }
}

// --- css editor --------------------------------------------------------------

@Composable
private fun CssEditorView(w: EditorWidgetData) {
    val directions = listOf("row", "column")
    val justifications = listOf("flex-start", "center", "flex-end", "space-between")
    val alignments = listOf("stretch", "flex-start", "center", "flex-end")
    var direction by remember(w) { mutableStateOf(directions.firstOrNull { it == (w.layout["direction"] as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: "row") }
    var justify by remember(w) { mutableStateOf(justifications.firstOrNull { it == (w.layout["justify"] as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: "flex-start") }
    var align by remember(w) { mutableStateOf(alignments.firstOrNull { it == (w.layout["align"] as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: "center") }
    var gap by remember(w) { mutableStateOf(((w.layout["gap"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull() ?: 12.0)) }
    val hues = listOf(0, 1, 2, 9, 4)
    val css = "display: flex;\nflex-direction: $direction;\ngap: ${gap.toInt()}px;\njustify-content: $justify;\nalign-items: $align;"
    WidgetCardShell(w.title.ifEmpty { "CSS layout" }, "editor") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { direction = directions[(directions.indexOf(direction) + 1) % 2] }) { Text(direction) }
            TextButton(onClick = { justify = justifications[(justifications.indexOf(justify) + 1) % justifications.size] }) { Text(justify) }
            TextButton(onClick = { align = alignments[(alignments.indexOf(align) + 1) % alignments.size] }) { Text(align) }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(GsTheme.colors.appBackground, RoundedCornerShape(10.dp))
                .padding(8.dp),
            horizontalArrangement = when (justify) {
                "center" -> Arrangement.Center
                "flex-end" -> Arrangement.End
                "space-between" -> Arrangement.SpaceBetween
                else -> Arrangement.Start
            },
            verticalAlignment = when (align) {
                "center" -> Alignment.CenterVertically
                "flex-end" -> Alignment.Bottom
                "flex-start" -> Alignment.Top
                else -> Alignment.CenterVertically
            }
        ) {
            if (direction == "row") {
                Row(horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                    hues.take(4).forEach { Box(Modifier.size(width = 34.dp, height = 60.dp).background(seriesColor(it), RoundedCornerShape(6.dp))) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(gap.dp)) {
                    hues.take(4).forEach { Box(Modifier.size(width = 120.dp, height = 14.dp).background(seriesColor(it), RoundedCornerShape(6.dp))) }
                }
            }
        }
        Text(
            css,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = GsTheme.colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

// --- video timeline ----------------------------------------------------------

@Composable
private fun VideoEditorView(w: EditorWidgetData) {
    val tracks = remember(w) {
        w.tracks.take(6).ifEmpty {
            listOf(
                TrackData("Video A", listOf(ClipData(0f, 8f, "Intro"), ClipData(10f, 12f, "Demo"))),
                TrackData("Audio", listOf(ClipData(0f, 20f, "Voiceover")))
            )
        }
    }
    val duration = w.duration.coerceIn(1, 600).toFloat()
    var playhead by remember(w) { mutableStateOf(0f) }
    var playing by remember(w) { mutableStateOf(false) }
    LaunchedEffect(w, playing) {
        var last = 0L
        while (playing) {
            withFrameNanos { now ->
                if (last != 0L) playhead = (playhead + (now - last) / 1_000_000_000f) % duration
                last = now
            }
        }
    }
    WidgetCardShell(w.title.ifEmpty { "Clip timeline" }, "editor") {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .pointerInput(w, duration) {
                    detectTapGestures { p -> playhead = (p.x / size.width) * duration }
                }
        ) {
            val cw = size.width
            val trackH = size.height / max(1, tracks.size)
            tracks.forEachIndexed { ti, track ->
                val y = ti * trackH
                track.clips.forEach { clip ->
                    val x = (clip.start / duration) * cw
                    val clipW = (clip.dur / duration) * cw
                    drawRect(seriesColor(ti % 12).copy(alpha = 0.4f), topLeft = Offset(x, y + 3f), size = androidx.compose.ui.geometry.Size(clipW, trackH - 6f))
                }
            }
            val px = (playhead / duration) * cw
            drawLine(seriesColor(9), Offset(px, 0f), Offset(px, size.height), strokeWidth = 2f)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { playing = !playing }) { Text(if (playing) "Pause" else "Play") }
            Text("${"%.1f".format(playhead)}s / ${duration.toInt()}s", style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary)
        }
    }
}

// --- audio (visual-only on Android, honestly labelled) -----------------------

@Composable
private fun AudioEditorView(w: EditorWidgetData) {
    val notes = listOf("C4", "D4", "E4", "G4", "A4", "C5")
    val freqs = mapOf("C4" to 261.6, "D4" to 293.7, "E4" to 329.6, "G4" to 392.0, "A4" to 440.0, "C5" to 523.3)
    val seq = remember(w) { mutableStateListOf(*listOf("C4", "E4", "G4", "E4", "A4", "G4", "C5", "G4").toTypedArray()) }
    val buffer = remember(seq.toList()) {
        FloatArray(8000) { s ->
            val i = s / 1120
            if (i < seq.size) {
                val t = (s % 1120) / 8000.0
                val env = min(1.0, (s % 1120) / 600.0) * exp(-t * 5.0)
                (0.5 * env * sin(2 * Math.PI * (freqs[seq[i]] ?: 440.0) * t)).toFloat()
            } else 0f
        }
    }
    WidgetCardShell(w.title.ifEmpty { "Sequencer" }, "editor") {
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val mid = size.height / 2f
            val step = size.width / buffer.size
            val path = Path()
            var first = true
            for (x in 0 until size.width.toInt()) {
                val v = buffer[(x.toFloat() / size.width * (buffer.size - 1)).toInt()]
                val y = mid - v * (mid - 6f)
                if (first) { path.moveTo(x.toFloat(), y); first = false } else path.lineTo(x.toFloat(), y)
            }
            drawPath(path, seriesColor(0), style = Stroke(1.4f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            seq.forEachIndexed { i, note ->
                Text(
                    note,
                    style = MaterialTheme.typography.labelSmall,
                    color = seriesColor(0),
                    modifier = Modifier
                        .border(1.dp, seriesColor(0).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .clickable { seq[i] = notes[(notes.indexOf(note) + 1) % notes.size] }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Text(
            "tap notes to retune — waveform re-renders live (playback is web-only)",
            style = MaterialTheme.typography.labelSmall,
            color = GsTheme.colors.textTertiary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
