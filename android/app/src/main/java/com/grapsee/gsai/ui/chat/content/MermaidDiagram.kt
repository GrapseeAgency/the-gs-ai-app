package com.grapsee.gsai.ui.chat.content

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.min

/**
 * STEP 5 — native Mermaid rendering. NO WebView, NO remote page, NO new
 * dependencies: diagrams parse and lay out locally, then draw on a Compose
 * Canvas. Supported families: flowchart (TD/LR/BT/RL) and sequenceDiagram —
 * everything else (class/state/er/journey/gantt/mindmap, subgraphs, group
 * frames) degrades HONESTLY to "Diagram unavailable" with a source viewer.
 * Diagrams never render mid-stream (a fenced mermaid block shows its plain
 * source while streaming, exactly like a code block).
 */

// ---------------------------------------------------------------------------
// Model + parser
// ---------------------------------------------------------------------------

enum class MermaidShape { RECT, ROUND, DIAMOND, STADIUM, CIRCLE }

data class FlowNode(val id: String, val label: String, val shape: MermaidShape)
data class FlowEdge(val from: String, val to: String, val label: String?, val style: EdgeStyle)
enum class EdgeStyle { SOLID, DASHED, THICK, OPEN }

data class SeqParticipant(val id: String, val label: String)
data class SeqMessage(val from: String, val to: String, val text: String, val style: SeqStyle)
enum class SeqStyle { SOLID, DASHED, CROSS, ASYNC }
data class SeqNote(val ids: List<String>, val text: String)

sealed interface MermaidChart {
    data class Flowchart(
        val vertical: Boolean,
        val reversed: Boolean,
        val nodes: List<FlowNode>,
        val edges: List<FlowEdge>
    ) : MermaidChart

    data class Sequence(
        val participants: List<SeqParticipant>,
        val messages: List<SeqMessage>,
        val notes: List<SeqNote>
    ) : MermaidChart

    data class Unsupported(val reason: String) : MermaidChart
}

fun parseMermaid(source: String): MermaidChart {
    val lines = source.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
    if (lines.isEmpty()) return MermaidChart.Unsupported("The diagram source is empty.")
    val head = lines.first().trim()
    val rest = lines.drop(1)
    return when {
        head.startsWith("flowchart") || head.startsWith("graph") -> {
            val dir = head.substringAfter("flowchart").substringAfter("graph").trim()
                .split(Regex("\\s+")).firstOrNull()?.uppercase() ?: "TD"
            if (dir !in setOf("TD", "TB", "LR", "BT", "RL")) {
                MermaidChart.Unsupported("Unknown flowchart direction \"$dir\".")
            } else {
                parseFlowchart(
                    vertical = dir == "TD" || dir == "TB" || dir == "BT",
                    reversed = dir == "BT" || dir == "RL",
                    lines = rest
                )
            }
        }
        head == "sequenceDiagram" || head == "sequence" -> parseSequence(rest)
        head.startsWith("classDiagram") -> MermaidChart.Unsupported("Class diagrams aren't supported yet.")
        head.startsWith("stateDiagram") -> MermaidChart.Unsupported("State diagrams aren't supported yet.")
        head.startsWith("erDiagram") -> MermaidChart.Unsupported("Entity-relationship diagrams aren't supported yet.")
        head.startsWith("journey") -> MermaidChart.Unsupported("User journeys aren't supported yet.")
        head.startsWith("gantt") -> MermaidChart.Unsupported("Gantt charts aren't supported yet.")
        head.startsWith("mindmap") -> MermaidChart.Unsupported("Mindmaps aren't supported yet.")
        else -> MermaidChart.Unsupported("This diagram type isn't supported yet.")
    }
}

private val edgeOpRegex = Regex("(-\\.->|-->|==>|---|--)(\\|([^|]*)\\|)?")

private fun parseFlowchart(vertical: Boolean, reversed: Boolean, lines: List<String>): MermaidChart {
    val nodeMap = LinkedHashMap<String, FlowNode>()
    val edges = mutableListOf<FlowEdge>()

    fun registerNode(raw: String): String? {
        val parsed = parseNodeToken(raw.trim()) ?: return null
        val (id, shape, label) = parsed
        val existing = nodeMap[id]
        when {
            existing == null -> nodeMap[id] = FlowNode(id, label ?: id, shape)
            label != null -> nodeMap[id] = existing.copy(label = label, shape = shape)
        }
        return id
    }

    for (line in lines) {
        val first = line.trim().lowercase()
        if (first.startsWith("subgraph")) {
            return MermaidChart.Unsupported("Subgraphs aren't supported yet.")
        }
        if (first == "end") continue
        // Segment-based scan: node labels may contain spaces, so the line is
        // split by EDGE OPERATORS (never by whitespace) — "Start([App start])
        // --> Check{Signed in?}" stays intact as two node segments.
        val matches = edgeOpRegex.findAll(line).toList()
        if (matches.isEmpty()) continue // a bare node-declaration line
        val segments = mutableListOf<String>()
        var cursor = 0
        for (m in matches) {
            segments += line.substring(cursor, m.range.first).trim()
            cursor = m.range.last + 1
        }
        segments += line.substring(cursor).trim()

        var fromId: String? = null
        var idx = 0
        while (idx < matches.size) {
            val op = matches[idx]
            val opText = op.groupValues[1]
            val pipeLabel = op.groupValues[3].takeIf { it.isNotBlank() }
            if (fromId == null) {
                val firstSeg = segments.getOrNull(idx).orEmpty()
                if (firstSeg.isEmpty()) return MermaidChart.Unsupported("An edge has no source node.")
                fromId = registerNode(firstSeg) ?: return MermaidChart.Unsupported("Couldn't read a node definition.")
            }
            if (opText == "--" || opText == "---") {
                val nextOp = matches.getOrNull(idx + 1)
                val segAfter = segments.getOrNull(idx + 1).orEmpty()
                if (nextOp != null && nextOp.groupValues[1] == "-->" && segAfter.isNotEmpty()) {
                    // "A -- text --> B" label form
                    val target = segments.getOrNull(idx + 2).orEmpty()
                    if (target.isEmpty()) return MermaidChart.Unsupported("An edge has no target node.")
                    val toId = registerNode(target) ?: return MermaidChart.Unsupported("Couldn't read a node definition.")
                    edges += FlowEdge(fromId!!, toId, segAfter.ifBlank { null }, EdgeStyle.SOLID)
                    fromId = toId
                    idx += 2
                } else {
                    if (opText == "--") return MermaidChart.Unsupported("Malformed edge.")
                    if (segAfter.isEmpty()) return MermaidChart.Unsupported("An edge has no target node.")
                    val toId = registerNode(segAfter) ?: return MermaidChart.Unsupported("Couldn't read a node definition.")
                    edges += FlowEdge(fromId!!, toId, null, EdgeStyle.OPEN)
                    fromId = toId
                    idx += 1
                }
                continue
            }
            val style = when (opText) {
                "-.->" -> EdgeStyle.DASHED
                "==>" -> EdgeStyle.THICK
                "---" -> EdgeStyle.OPEN
                else -> EdgeStyle.SOLID
            }
            val target = segments.getOrNull(idx + 1).orEmpty()
            if (target.isEmpty()) return MermaidChart.Unsupported("An edge has no target node.")
            val toId = registerNode(target) ?: return MermaidChart.Unsupported("Couldn't read a node definition.")
            edges += FlowEdge(fromId!!, toId, pipeLabel, style)
            fromId = toId
            idx += 1
        }
    }
    if (nodeMap.isEmpty()) return MermaidChart.Unsupported("No nodes found in the diagram source.")
    return MermaidChart.Flowchart(vertical, reversed, nodeMap.values.toList(), edges)
}

/** `id[label]`, `id(label)`, `id{label}`, `id([label])`, `id((label))`, bare `id`. */
private fun parseNodeToken(token: String): Triple<String, MermaidShape, String?>? {
    val openerIndex = token.indexOfFirst { it == '[' || it == '(' || it == '{' }
    if (openerIndex <= 0) {
        return if (token.matches(Regex("\\w+"))) Triple(token, MermaidShape.RECT, null) else null
    }
    val id = token.substring(0, openerIndex)
    if (!id.matches(Regex("\\w+"))) return null
    val rest = token.substring(openerIndex)
    return when {
        rest.startsWith("([") && rest.endsWith("])") && rest.length >= 4 ->
            Triple(id, MermaidShape.STADIUM, rest.substring(2, rest.length - 2))
        rest.startsWith("((") && rest.endsWith("))") && rest.length >= 4 ->
            Triple(id, MermaidShape.CIRCLE, rest.substring(2, rest.length - 2))
        rest.startsWith("[") && rest.endsWith("]") && rest.length >= 2 ->
            Triple(id, MermaidShape.RECT, rest.substring(1, rest.length - 1))
        rest.startsWith("(") && rest.endsWith(")") && rest.length >= 2 ->
            Triple(id, MermaidShape.ROUND, rest.substring(1, rest.length - 1))
        rest.startsWith("{") && rest.endsWith("}") && rest.length >= 2 ->
            Triple(id, MermaidShape.DIAMOND, rest.substring(1, rest.length - 1))
        else -> null
    }
}

private fun parseSequence(lines: List<String>): MermaidChart {
    val participants = LinkedHashMap<String, SeqParticipant>()
    val messages = mutableListOf<SeqMessage>()
    val notes = mutableListOf<SeqNote>()

    fun participant(id: String): String {
        if (!participants.containsKey(id)) participants[id] = SeqParticipant(id, id)
        return id
    }

    for (line in lines) {
        val trimmed = line.trim()
        when {
            trimmed.startsWith("participant ") -> {
                val body = trimmed.removePrefix("participant ").trim()
                val asIndex = body.indexOf(" as ")
                if (asIndex > 0) {
                    val id = body.substring(0, asIndex).trim()
                    participants[id] = SeqParticipant(id, body.substring(asIndex + 4).trim())
                } else {
                    participant(body)
                }
            }
            trimmed.startsWith("Note ") -> {
                val body = trimmed.removePrefix("Note ").trim()
                val colon = body.indexOf(':')
                if (colon < 0) return MermaidChart.Unsupported("Couldn't read a note in the diagram source.")
                val text = body.substring(colon + 1).trim()
                val target = body.substring(0, colon).trim()
                val ids = target.removePrefix("over").removePrefix("right of").removePrefix("left of")
                    .split(',').map { it.trim() }.filter { it.isNotEmpty() }
                if (ids.isEmpty()) return MermaidChart.Unsupported("Couldn't read a note in the diagram source.")
                notes += SeqNote(ids, text)
            }
            trimmed == "autonumber" || trimmed.startsWith("activate ") || trimmed.startsWith("deactivate ") -> continue
            trimmed.startsWith("loop") || trimmed.startsWith("alt") || trimmed.startsWith("opt") ||
                trimmed.startsWith("par") || trimmed.startsWith("critical") -> {
                return MermaidChart.Unsupported("Group frames aren't supported yet.")
            }
            else -> {
                // Longest-arrow-first alternation: -->> before ->> so dashed
                // messages never parse as solid.
                val arrow = Regex("(-->>|->>|-x|-\\)|->)")
                val match = arrow.find(trimmed) ?: return MermaidChart.Unsupported("Couldn't read a line of the diagram source.")
                val colon = trimmed.indexOf(':', match.range.last)
                if (colon < 0) return MermaidChart.Unsupported("Couldn't read a message in the diagram source.")
                val text = trimmed.substring(colon + 1).trim()
                val head = trimmed.substring(0, colon)
                val sides = head.split(arrow).filter { it.isNotBlank() }
                if (sides.size != 2) return MermaidChart.Unsupported("Couldn't read a message in the diagram source.")
                val style = when (match.value) {
                    "-->>" -> SeqStyle.DASHED
                    "-x" -> SeqStyle.CROSS
                    "-)" -> SeqStyle.ASYNC
                    else -> SeqStyle.SOLID
                }
                messages += SeqMessage(participant(sides[0].trim()), participant(sides[1].trim()), text, style)
            }
        }
    }
    if (participants.isEmpty()) return MermaidChart.Unsupported("No participants found in the diagram source.")
    return MermaidChart.Sequence(participants.values.toList(), messages, notes)
}

// ---------------------------------------------------------------------------
// Layout (pure data — theme applied at draw time)
// ---------------------------------------------------------------------------

data class LaidNode(
    val id: String,
    val label: String,
    val shape: MermaidShape,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float
)

data class LaidEdge(val from: LaidNode, val to: LaidNode, val label: String?, val style: EdgeStyle)
data class DiagramLayout(
    val width: Float,
    val height: Float,
    val nodes: List<LaidNode>,
    val edges: List<LaidEdge>,
    val description: String
)

private const val NODE_FONT = 12f
private const val CHAR_W = 6.6f
private const val LINE_H = 16f
private const val NODE_GAP = 36f
private const val LAYER_GAP = 56f

private fun nodeSize(label: String, shape: MermaidShape): Pair<Float, Float> {
    val lines = wrapLabel(label, 16)
    val textW = (lines.maxOfOrNull { it.length } ?: 1) * CHAR_W
    var w = (textW + 26f).coerceIn(56f, 190f)
    var h = lines.size * LINE_H + 12f
    if (shape == MermaidShape.DIAMOND) { w += 18f; h += 12f }
    if (shape == MermaidShape.CIRCLE) { w = maxOf(w, h); h = w }
    return Pair(w, h)
}

private fun wrapLabel(label: String, maxChars: Int): List<String> {
    val words = label.split(Regex("\\s+"))
    val lines = mutableListOf<String>()
    var current = StringBuilder()
    for (word in words) {
        if (current.isNotEmpty() && current.length + word.length + 1 > maxChars) {
            lines += current.toString()
            current = StringBuilder()
        }
        if (current.isNotEmpty()) current.append(' ')
        current.append(word)
    }
    if (current.isNotEmpty()) lines += current.toString()
    return lines.ifEmpty { listOf("") }
}

fun layoutFlowchart(chart: MermaidChart.Flowchart): DiagramLayout {
    val nodeById = chart.nodes.associateBy { it.id }
    val incoming = chart.edges.groupBy { it.to }
    val memo = HashMap<String, Int>()
    val stack = HashSet<String>()

    fun rank(id: String): Int {
        memo[id]?.let { return it }
        if (id in stack) return 0 // cycle break
        stack += id
        val r = incoming[id]?.maxOfOrNull { rank(it.from) + 1 } ?: 0
        stack -= id
        memo[id] = r
        return r
    }
    chart.nodes.forEach { rank(it.id) }

    val ordered = chart.nodes.sortedBy { (memo[it.id] ?: 0) * if (chart.reversed) -1 else 1 }
    val sizes = ordered.associate { it.id to nodeSize(it.label, it.shape) }
    val layers = ordered.groupBy { memo[it.id] ?: 0 }.toSortedMap()

    val layerWidths = layers.mapValues { (_, ns) ->
        ns.sumOf { sizes[it.id]!!.first.toDouble() }.toFloat() + NODE_GAP * (ns.size - 1).coerceAtLeast(0)
    }
    val width = (layerWidths.values.maxOrNull() ?: 300f).coerceAtLeast(300f)
    var y = 20f
    val laid = mutableListOf<LaidNode>()
    for ((_, layerNodes) in layers) {
        val layerH = layerNodes.maxOf { sizes[it.id]!!.second }
        val totalW = layerNodes.sumOf { sizes[it.id]!!.first.toDouble() }.toFloat() + NODE_GAP * (layerNodes.size - 1).coerceAtLeast(0)
        var x = (width - totalW) / 2f
        for (node in layerNodes) {
            val (w, h) = sizes[node.id]!!
            laid += LaidNode(node.id, node.label, node.shape, x, y + (layerH - h) / 2f, w, h)
            x += w + NODE_GAP
        }
        y += layerH + LAYER_GAP
    }
    val height = y - LAYER_GAP + 20f
    val laidById = laid.associateBy { it.id }
    val laidEdges = chart.edges.mapNotNull { e ->
        val from = laidById[e.from] ?: return@mapNotNull null
        val to = laidById[e.to] ?: return@mapNotNull null
        LaidEdge(from, to, e.label, e.style)
    }
    val desc = "Flowchart diagram, ${laid.size} nodes, ${laidEdges.size} connections. Nodes: " +
        laid.joinToString(", ") { it.label.ifBlank { it.id } }
    return DiagramLayout(width, height, laid, laidEdges, desc.take(400))
}

data class SeqLayout(
    val width: Float,
    val height: Float,
    val columns: List<Pair<SeqParticipant, Float>>, // participant → center x
    val headerTop: Float,
    val headerHeight: Float,
    val rows: List<SeqRow>,
    val notes: List<LaidNote>,
    val description: String
)

sealed interface SeqRow {
    data class Message(val from: Float, val to: Float, val text: String, val style: SeqStyle, val y: Float) : SeqRow
}

data class LaidNote(val left: Float, val right: Float, val y: Float, val text: String)

fun layoutSequence(chart: MermaidChart.Sequence): SeqLayout {
    val idOrder = chart.participants
    val columns = idOrder.mapIndexed { index, p ->
        val w = (p.label.length * CHAR_W + 20f).coerceAtLeast(90f)
        Pair(p, w)
    }
    val totalW = columns.sumOf { it.second.toDouble() }.toFloat() + 24f
    val centers = HashMap<String, Float>()
    var x = 12f
    for ((p, w) in columns) {
        centers[p.id] = x + w / 2f
        x += w
    }
    val headerTop = 8f
    val headerHeight = 32f
    val rows = mutableListOf<SeqRow>()
    val laidNotes = mutableListOf<LaidNote>()
    var y = headerTop + headerHeight + 30f
    for (message in chart.messages) {
        val from = centers[message.from] ?: 0f
        val to = centers[message.to] ?: 0f
        rows += SeqRow.Message(from, to, message.text, message.style, y)
        y += if (message.from == message.to) 56f else 46f
    }
    for (note in chart.notes) {
        val xs = note.ids.mapNotNull { centers[it] }
        if (xs.isEmpty()) continue
        val left = (xs.min() - 45f).coerceAtLeast(4f)
        val right = (xs.max() + 45f).coerceAtMost(totalW - 4f)
        laidNotes += LaidNote(left, right, y, note.text)
        y += 40f
    }
    val height = y + 16f
    val desc = "Sequence diagram, ${idOrder.size} participants, ${chart.messages.size} messages. Participants: " +
        idOrder.joinToString(", ") { it.label }
    return SeqLayout(totalW, height, columns.map { it.first to (centers[it.first.id] ?: 0f) },
        headerTop, headerHeight, rows, laidNotes, desc.take(400))
}

// ---------------------------------------------------------------------------
// Renderer
// ---------------------------------------------------------------------------

@Composable
fun MermaidBlockView(block: Block.MermaidBlock, streaming: Boolean, onCopyCode: (String) -> Unit) {
    // Streaming contract: the fenced source renders as a plain open code block —
    // diagram layout never runs at stream cadence.
    if (streaming) {
        CodeBlockCard(language = "mermaid", code = block.source, open = true, onCopyCode = onCopyCode)
        return
    }
    val chart = remember(block.source) { parseMermaid(block.source) }
    when (chart) {
        is MermaidChart.Unsupported -> MermaidFallback(chart.reason, block.source, onCopyCode)
        is MermaidChart.Flowchart -> {
            val layout = remember(chart) { layoutFlowchart(chart) }
            MermaidCard(title = "Mermaid · Flowchart", source = block.source, onCopyCode = onCopyCode, description = layout.description) {
                FlowchartCanvas(layout)
            }
        }
        is MermaidChart.Sequence -> {
            val layout = remember(chart) { layoutSequence(chart) }
            MermaidCard(title = "Mermaid · Sequence", source = block.source, onCopyCode = onCopyCode, description = layout.description) {
                SequenceCanvas(layout)
            }
        }
    }
}

@Composable
private fun MermaidCard(
    title: String,
    source: String,
    onCopyCode: (String) -> Unit,
    description: String,
    content: @Composable () -> Unit
) {
    var viewerOpen by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1500)
            copied = false
        }
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = {
                        onCopyCode(source)
                        copied = true
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        if (copied) Icons.Filled.Check else Icons.Outlined.ContentCopy,
                        contentDescription = if (copied) "Copied" else "Copy diagram source",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            // Tap opens the viewer — the conversation's vertical scroll stays
            // dominant; no continuous gesture lives on the inline card.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewerOpen = true }
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.height(340.dp)) { content() }
            }
        }
    }
    if (viewerOpen) {
        MermaidViewerDialog(
            title = title,
            description = description,
            onDismiss = { viewerOpen = false }
        ) { content() }
    }
}

@Composable
private fun MermaidFallback(reason: String, source: String, onCopyCode: (String) -> Unit) {
    var showSource by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Diagram unavailable",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { showSource = !showSource }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
                    Text(if (showSource) "Hide source" else "View source", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = { onCopyCode(source) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
                    Text("Copy source", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (showSource) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        text = source,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Canvases
// ---------------------------------------------------------------------------

private class DiagramColors(
    val nodeFill: Int,
    val nodeStroke: Int,
    val text: Int,
    val edge: Int,
    val noteFill: Int
)

@Composable
private fun rememberDiagramColors(): DiagramColors = DiagramColors(
    nodeFill = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb(),
    nodeStroke = MaterialTheme.colorScheme.outline.toArgb(),
    text = MaterialTheme.colorScheme.onSurface.toArgb(),
    edge = MaterialTheme.colorScheme.onSurfaceVariant.toArgb(),
    noteFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f).toArgb()
)

@Composable
private fun FlowchartCanvas(layout: DiagramLayout) {
    val colors = rememberDiagramColors()
    val paint = remember {
        android.graphics.Paint().apply { isAntiAlias = true; textSize = NODE_FONT }
    }
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = layout.description }
    ) {
        val scale = min(min(1f, size.width / layout.width), size.height / layout.height)
        val left = (size.width - layout.width * scale) / 2f
        val top = (size.height - layout.height * scale) / 2f
        withTransform({
            translate(left, top)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            for (edge in layout.edges) drawEdgeElbow(edge, colors, paint, vertical = true)
            for (node in layout.nodes) drawNode(node, colors, paint)
        }
    }
}

@Composable
private fun SequenceCanvas(layout: SeqLayout) {
    val colors = rememberDiagramColors()
    val paint = remember {
        android.graphics.Paint().apply { isAntiAlias = true; textSize = NODE_FONT }
    }
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = layout.description }
    ) {
        val scale = min(min(1f, size.width / layout.width), size.height / layout.height)
        val left = (size.width - layout.width * scale) / 2f
        val top = (size.height - layout.height * scale) / 2f
        withTransform({
            translate(left, top)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            // Lifelines
            for ((_, cx) in layout.columns) {
                drawLine(
                    color = Color(colors.edge),
                    start = Offset(cx, layout.headerTop + layout.headerHeight + 4f),
                    end = Offset(cx, layout.height - 8f),
                    strokeWidth = 1.2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
            }
            // Header boxes
            for ((p, cx) in layout.columns) {
                val w = (p.label.length * CHAR_W + 20f).coerceAtLeast(90f)
                drawNodeBox(cx - w / 2f, layout.headerTop, w, layout.headerHeight, p.label, colors, paint, MermaidShape.RECT)
            }
            // Messages
            for (row in layout.rows) {
                when (row) {
                    is SeqRow.Message -> drawSeqMessage(row, colors, paint)
                }
            }
            // Notes
            for (note in layout.notes) {
                drawRoundRect(
                    color = Color(colors.noteFill),
                    topLeft = Offset(note.left, note.y),
                    size = androidx.compose.ui.geometry.Size(note.right - note.left, 28f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                )
                paint.color = colors.text
                drawIntoCanvasText(note.text, (note.left + note.right) / 2f, note.y + 19f, paint, center = true)
            }
        }
    }
}

private fun DrawScope.drawNodeBox(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    label: String,
    colors: DiagramColors,
    paint: android.graphics.Paint,
    shape: MermaidShape
) {
    when (shape) {
        MermaidShape.RECT, MermaidShape.ROUND, MermaidShape.STADIUM, MermaidShape.CIRCLE -> {
            val radius = when (shape) {
                MermaidShape.RECT -> 4f
                MermaidShape.STADIUM, MermaidShape.CIRCLE -> h / 2f
                else -> h / 2.4f
            }
            drawRoundRect(
                color = Color(colors.nodeFill),
                topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                style = Fill
            )
            drawRoundRect(
                color = Color(colors.nodeStroke),
                topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                style = Stroke(width = 1.4f)
            )
        }
        MermaidShape.DIAMOND -> {
            val path = Path().apply {
                moveTo(x + w / 2f, y)
                lineTo(x + w, y + h / 2f)
                lineTo(x + w / 2f, y + h)
                lineTo(x, y + h / 2f)
                close()
            }
            drawPath(path, color = Color(colors.nodeFill), style = Fill)
            drawPath(path, color = Color(colors.nodeStroke), style = Stroke(width = 1.4f))
        }
    }
    paint.color = colors.text
    val lines = wrapLabel(label, 16)
    val startY = y + h / 2f - (lines.size - 1) * LINE_H / 2f + LINE_H / 3f
    lines.forEachIndexed { index, line ->
        drawIntoCanvasText(line, x + w / 2f, startY + index * LINE_H, paint, center = true)
    }
}

private fun DrawScope.drawNode(node: LaidNode, colors: DiagramColors, paint: android.graphics.Paint) {
    drawNodeBox(node.x, node.y, node.w, node.h, node.label, colors, paint, node.shape)
}

private fun DrawScope.drawEdgeElbow(edge: LaidEdge, colors: DiagramColors, paint: android.graphics.Paint, vertical: Boolean) {
    val strokeColor = Color(colors.edge)
    val dashed = edge.style == EdgeStyle.DASHED
    val width = if (edge.style == EdgeStyle.THICK) 2.2f else 1.4f
    val pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
    if (vertical) {
        val sx = edge.from.x + edge.from.w / 2f
        val sy = edge.from.y + edge.from.h
        val tx = edge.to.x + edge.to.w / 2f
        val ty = edge.to.y
        val midY = (sy + ty) / 2f
        val path = Path().apply {
            moveTo(sx, sy)
            lineTo(sx, midY)
            lineTo(tx, midY)
            lineTo(tx, ty - 7f)
        }
        drawPath(path, color = strokeColor, style = Stroke(width = width, pathEffect = pathEffect))
        drawArrowHead(tx, ty, pointing = ArrowDir.DOWN, color = strokeColor)
        edge.label?.let {
            paint.color = colors.edge
            drawIntoCanvasText(it, (sx + tx) / 2f, midY - 5f, paint, center = true)
        }
    } else {
        val sx = edge.from.x + edge.from.w
        val sy = edge.from.y + edge.from.h / 2f
        val tx = edge.to.x
        val ty = edge.to.y + edge.to.h / 2f
        val midX = (sx + tx) / 2f
        val path = Path().apply {
            moveTo(sx, sy)
            lineTo(midX, sy)
            lineTo(midX, ty)
            lineTo(tx - 7f, ty)
        }
        drawPath(path, color = strokeColor, style = Stroke(width = width, pathEffect = pathEffect))
        drawArrowHead(tx, ty, pointing = ArrowDir.RIGHT, color = strokeColor)
        edge.label?.let {
            paint.color = colors.edge
            drawIntoCanvasText(it, midX, (sy + ty) / 2f - 5f, paint, center = true)
        }
    }
}

private enum class ArrowDir { DOWN, RIGHT }

private fun DrawScope.drawArrowHead(x: Float, y: Float, pointing: ArrowDir, color: Color) {
    val path = Path()
    when (pointing) {
        ArrowDir.DOWN -> {
            path.moveTo(x, y)
            path.lineTo(x - 4.5f, y - 8f)
            path.lineTo(x + 4.5f, y - 8f)
        }
        ArrowDir.RIGHT -> {
            path.moveTo(x, y)
            path.lineTo(x - 8f, y - 4.5f)
            path.lineTo(x - 8f, y + 4.5f)
        }
    }
    path.close()
    drawPath(path, color = color, style = Fill)
}

private fun DrawScope.drawSeqMessage(row: SeqRow.Message, colors: DiagramColors, paint: android.graphics.Paint) {
    val color = Color(colors.edge)
    val dashed = row.style == SeqStyle.DASHED
    val pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
    if (row.from == row.to) {
        // Self message: small rectangular loop on the right of the lifeline.
        val x = row.from
        val path = Path().apply {
            moveTo(x, row.y - 8f)
            lineTo(x + 26f, row.y - 8f)
            lineTo(x + 26f, row.y + 8f)
            lineTo(x, row.y + 8f)
        }
        drawPath(path, color = color, style = Stroke(width = 1.4f, pathEffect = pathEffect))
        drawArrowHead(x + 1f, row.y + 8f, ArrowDir.RIGHT, color)
        paint.color = colors.text
        drawIntoCanvasText(row.text, x + 34f, row.y + 3f, paint, center = false)
    } else {
        val left = min(row.from, row.to)
        val right = maxOf(row.from, row.to)
        drawLine(
            color = color,
            start = Offset(left, row.y),
            end = Offset(right - (if (row.style == SeqStyle.CROSS) 0f else 2f), row.y),
            strokeWidth = 1.4f,
            pathEffect = pathEffect
        )
        when (row.style) {
            SeqStyle.CROSS -> {
                drawLine(color, Offset(right - 9f, row.y - 9f), Offset(right + 1f, row.y + 1f), 1.6f)
                drawLine(color, Offset(right - 9f, row.y + 1f), Offset(right + 1f, row.y - 9f), 1.6f)
            }
            SeqStyle.ASYNC -> {
                drawLine(color, Offset(right - 8f, row.y - 4.5f), Offset(right, row.y), 1.4f)
                drawLine(color, Offset(right - 8f, row.y + 4.5f), Offset(right, row.y), 1.4f)
            }
            else -> drawArrowHead(if (row.to > row.from) right else left, row.y, ArrowDir.RIGHT, color)
        }
        paint.color = colors.text
        drawIntoCanvasText(row.text, (left + right) / 2f, row.y - 7f, paint, center = true)
    }
}

private fun DrawScope.drawIntoCanvasText(
    text: String,
    x: Float,
    y: Float,
    paint: android.graphics.Paint,
    center: Boolean
) {
    if (center) paint.textAlign = android.graphics.Paint.Align.CENTER
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
    if (center) paint.textAlign = android.graphics.Paint.Align.LEFT
}

// ---------------------------------------------------------------------------
// Full-screen viewer (zoom + pan, double-tap reset)
// ---------------------------------------------------------------------------

@Composable
private fun MermaidViewerDialog(title: String, description: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset += pan
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = 1f
                            offset = Offset.Zero
                        }
                    )
                }
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text("Close", color = MaterialTheme.colorScheme.onSurface)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .semantics { contentDescription = description }
            ) {
                content()
            }
        }
    }
}
