package com.grapsee.gsai.ui.chat.content

// ---------------------------------------------------------------------------
// Canvas kind — four pointer surfaces. Whiteboard draws real strokes; mindmap
// chips drag and branch; diagram connects boxes with arrows; polygons plots a
// zone over an OFFLINE graticule grid (no map tiles in this client — the
// widget says so instead of pretending). Logical space 0..1 × 0..1 matches
// the web renderer's payload contract.
// ---------------------------------------------------------------------------

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.grapsee.gsai.ui.theme.GsTheme
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size

private val PEN_HUES = listOf(0, 1, 2, 9, 4)

@Composable
internal fun CanvasWidgetView(w: CanvasWidgetData) {
    when (w.surface) {
        "mindmap" -> MindMapSurface(w)
        "diagram" -> DiagramSurface(w)
        "polygons" -> PolygonSurface(w)
        else -> WhiteboardSurface(w)
    }
}

// --- whiteboard --------------------------------------------------------------

@Composable
private fun WhiteboardSurface(w: CanvasWidgetData) {
    val strokes = remember(w) { mutableStateListOf<MutableList<Offset>>() }
    var pen by remember { mutableStateOf(0) }
    WidgetCardShell(w.title.ifEmpty { "Whiteboard" }, "canvas") {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .pointerInput(w) {
                    detectDragGestures(
                        onDragStart = { p ->
                            strokes.add(mutableStateListOf(p))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            strokes.lastOrNull()?.add(change.position)
                        }
                    )
                }
        ) {
            strokes.forEach { stroke ->
                if (stroke.size >= 2) {
                    val path = Path().apply {
                        moveTo(stroke.first().x, stroke.first().y)
                        stroke.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, seriesInk(PEN_HUES[pen]), style = Stroke(3f))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PEN_HUES.forEachIndexed { i, hue ->
                Box(
                    Modifier
                        .size(width = 22.dp, height = 22.dp)
                        .background(seriesInk(hue), RoundedCornerShape(11.dp))
                        .border(
                            width = if (pen == i) 2.dp else 0.dp,
                            color = GsTheme.colors.textPrimary,
                            shape = RoundedCornerShape(11.dp)
                        )
                        .clickable { pen = i }
                )
            }
            TextButton(onClick = { strokes.clear() }) { Text("Clear") }
            Text("${strokes.size} strokes", style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary)
        }
    }
}

// --- mindmap -----------------------------------------------------------------

private data class MapNodeState(var x: Float, var y: Float)

@Composable
private fun MindMapSurface(w: CanvasWidgetData) {
    val nodes = remember(w) {
        mutableStateListOf(
            *w.nodes.take(40).mapIndexed { i, n ->
                MapNodeState(
                    (n.x.takeIf { it > 0f } ?: (0.18f + (i % 4) * 0.22f)),
                    (n.y.takeIf { it > 0f } ?: (0.25f + (i / 4) * 0.25f))
                )
            }.ifEmpty { listOf(MapNodeState(0.5f, 0.3f)) }.toTypedArray()
        )
    }
    val labels = remember(w) { w.nodes.map { it.label }.ifEmpty { listOf("Idea") } }
    val edges = remember(w) {
        mutableStateListOf(
            *w.edges.filter { it.size >= 2 && it[0] < w.nodes.size.coerceAtLeast(1) && it[1] < w.nodes.size.coerceAtLeast(1) }.toTypedArray()
        )
    }
    var pxSize by remember { mutableStateOf(IntSize.Zero) }
    WidgetCardShell(w.title.ifEmpty { "Mind map" }, "canvas") {
        Box(
            Modifier
                .fillMaxWidth()
                .height(240.dp)
                .onSizeChanged { pxSize = it }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                edges.forEach { e ->
                    val a = nodes.getOrNull(e[0]) ?: return@forEach
                    val b = nodes.getOrNull(e[1]) ?: return@forEach
                    val ax = a.x * size.width
                    val ay = a.y * size.height
                    val bx = b.x * size.width
                    val by = b.y * size.height
                    val path = Path().apply {
                        moveTo(ax, ay)
                        cubicTo((ax + bx) / 2f, ay, (ax + bx) / 2f, by, bx, by)
                    }
                    drawPath(path, seriesInk(0).copy(alpha = 0.5f), style = Stroke(2f))
                }
            }
            nodes.forEachIndexed { i, node ->
                val label = labels.getOrElse(i) { "Idea ${i + 1}" }
                Row(
                    Modifier
                        .offset { IntOffset((node.x * pxSize.width).roundToInt(), (node.y * pxSize.height).roundToInt()) }
                        .padding(top = (-12).dp)
                        .pointerInput(i, pxSize) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                if (pxSize.width > 0) {
                                    node.x = (node.x + dragAmount.x / pxSize.width).coerceIn(0.04f, 0.96f)
                                    node.y = (node.y + dragAmount.y / pxSize.height).coerceIn(0.05f, 0.95f)
                                }
                            }
                        }
                ) {
                    Row(
                        Modifier
                            .background(seriesInk(i % 12).copy(alpha = 0.16f), RoundedCornerShape(999.dp))
                            .border(1.dp, seriesInk(i % 12), RoundedCornerShape(999.dp))
                            .clickable {
                                val id = nodes.size
                                nodes.add(MapNodeState((node.x + 0.18f).coerceAtMost(0.9f), (node.y + if (id % 2 == 0) 0.14f else -0.14f).coerceIn(0.08f, 0.9f)))
                                edges.add(arrayListOf(i, id))
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = GsTheme.colors.textPrimary)
                        Text(
                            " +",
                            style = MaterialTheme.typography.labelMedium,
                            color = seriesInk(i % 12),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                }
            }
        }
        Text(
            "drag chips to move · tap a chip to branch",
            style = MaterialTheme.typography.labelSmall,
            color = GsTheme.colors.textTertiary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// --- diagram -----------------------------------------------------------------

@Composable
private fun DiagramSurface(w: CanvasWidgetData) {
    val nodes = remember(w) {
        mutableStateListOf(
            *w.nodes.take(40).mapIndexed { i, n ->
                MapNodeState(
                    n.x.takeIf { it > 0f } ?: (0.15f + (i % 3) * 0.3f),
                    n.y.takeIf { it > 0f } ?: (0.3f + (i / 3) * 0.32f)
                )
            }.ifEmpty { listOf(MapNodeState(0.2f, 0.5f), MapNodeState(0.8f, 0.5f)) }.toTypedArray()
        )
    }
    val labels = remember(w) { w.nodes.map { it.label }.ifEmpty { listOf("Start", "End") } }
    val edges = remember(w) { mutableStateListOf(*w.edges.toTypedArray()) }
    var selected by remember { mutableStateOf(-1) }
    var pxSize by remember { mutableStateOf(IntSize.Zero) }
    WidgetCardShell(w.title.ifEmpty { "Diagram" }, "canvas") {
        Box(
            Modifier
                .fillMaxWidth()
                .height(240.dp)
                .onSizeChanged { pxSize = it }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                edges.forEach { e ->
                    val a = nodes.getOrNull(e[0]) ?: return@forEach
                    val b = nodes.getOrNull(e[1]) ?: return@forEach
                    val ax = a.x * size.width
                    val ay = a.y * size.height
                    val bx = b.x * size.width
                    val by = b.y * size.height
                    drawLine(seriesInk(0).copy(alpha = 0.8f), Offset(ax, ay), Offset(bx, by), strokeWidth = 2f)
                    // arrowhead
                    val ang = atan2(by - ay, bx - ax)
                    val head = 10f
                    val p1 = Offset(bx, by)
                    val p2 = Offset(bx - head * cos(ang - 0.5f), by - head * sin(ang - 0.5f))
                    val p3 = Offset(bx - head * cos(ang + 0.5f), by - head * sin(ang + 0.5f))
                    val tri = Path().apply {
                        moveTo(p1.x, p1.y)
                        lineTo(p2.x, p2.y)
                        lineTo(p3.x, p3.y)
                        close()
                    }
                    drawPath(tri, seriesInk(0).copy(alpha = 0.8f))
                }
            }
            nodes.forEachIndexed { i, node ->
                val label = labels.getOrElse(i) { "Step ${i + 1}" }
                val isDecision = label.contains("?")
                Box(
                    Modifier
                        .offset { IntOffset((node.x * pxSize.width).roundToInt(), (node.y * pxSize.height).roundToInt()) }
                        .pointerInput(i, pxSize) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                if (pxSize.width > 0) {
                                    node.x = (node.x + dragAmount.x / pxSize.width).coerceIn(0.08f, 0.92f)
                                    node.y = (node.y + dragAmount.y / pxSize.height).coerceIn(0.08f, 0.92f)
                                }
                            }
                        }
                ) {
                    Box(
                        Modifier
                            .background(
                                if (selected == i) seriesInk(0) else seriesInk(0).copy(alpha = 0.13f),
                                RoundedCornerShape(if (isDecision) 6.dp else 999.dp)
                            )
                            .border(
                                1.5.dp,
                                if (selected == i) seriesInk(0) else seriesInk(0).copy(alpha = 0.5f),
                                RoundedCornerShape(if (isDecision) 6.dp else 999.dp)
                            )
                            .clickable {
                                if (selected == -1) {
                                    selected = i
                                } else if (selected != i) {
                                    edges.add(arrayListOf(selected, i))
                                    selected = -1
                                } else {
                                    selected = -1
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected == i) GsTheme.colors.appBackground else GsTheme.colors.textPrimary
                        )
                    }
                }
            }
        }
        Text(
            if (selected >= 0) "tap a target node to connect…" else "drag to move · tap two nodes to connect · tap empty ends the chain",
            style = MaterialTheme.typography.labelSmall,
            color = GsTheme.colors.textTertiary,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (edges.isNotEmpty()) {
            TextButton(onClick = { edges.clear() }) { Text("Clear connections (${edges.size})") }
        }
    }
}

// --- polygons (offline graticule — honest: no tiles in this client) ----------

@Composable
private fun PolygonSurface(w: CanvasWidgetData) {
    val verts = remember(w) { mutableStateListOf<Offset>() }
    var area by remember { mutableStateOf(0f) }
    WidgetCardShell(w.title.ifEmpty { "Zone editor" }, "canvas") {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(230.dp)
                .pointerInput(w) {
                    detectTapGestures { p ->
                        verts.add(p)
                        area = if (verts.size >= 3) polygonAreaKm2(verts.toList(), Size(size.width.toFloat(), size.height.toFloat())) else 0f
                    }
                }
        ) {
            val grid = Color(0x33808080)
            // graticule: 6 x 4 cells
            for (i in 0..6) drawLine(grid, Offset(size.width * i / 6f, 0f), Offset(size.width * i / 6f, size.height), strokeWidth = 1f)
            for (j in 0..4) drawLine(grid, Offset(0f, size.height * j / 4f), Offset(size.width, size.height * j / 4f), strokeWidth = 1f)
            if (verts.size >= 2) {
                val path = Path().apply {
                    moveTo(verts[0].x, verts[0].y)
                    verts.drop(1).forEach { lineTo(it.x, it.y) }
                    if (verts.size >= 3) close()
                }
                drawPath(path, seriesInk(2).copy(alpha = 0.22f))
                drawPath(path, seriesInk(2), style = Stroke(2f))
            }
            verts.forEach { v -> drawCircle(seriesInk(2), radius = 4f, center = v) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (verts.size >= 3) "≈ ${"%.2f".format(area)} km²" else "${verts.size} vertices",
                style = MaterialTheme.typography.labelSmall,
                color = GsTheme.colors.textSecondary
            )
            TextButton(onClick = { verts.clear(); area = 0f }) { Text("Reset") }
        }
        Text(
            "offline graticule grid — this client renders zones without map tiles",
            style = MaterialTheme.typography.labelSmall,
            color = GsTheme.colors.textTertiary
        )
    }
}

/** Planar shoelace over the graticule: 6x4 cells at ~1.1 km per cell. */
private fun polygonAreaKm2(points: List<Offset>, size: androidx.compose.ui.geometry.Size): Float {
    if (points.size < 3 || size.width <= 0f || size.height <= 0f) return 0f
    var sum = 0f
    for (i in points.indices) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        sum += a.x * b.y - b.x * a.y
    }
    val pxArea = abs(sum) / 2f
    val cellW = size.width / 6f
    val cellH = size.height / 4f
    return pxArea * (1.1f / cellW) * (1.1f / cellH)
}
