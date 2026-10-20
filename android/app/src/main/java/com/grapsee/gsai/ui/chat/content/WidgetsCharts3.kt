package com.grapsee.gsai.ui.chat.content

// ---------------------------------------------------------------------------
// Catalogue v3 charts — parity with the web renderer (src/components/genui/
// charts/): sankey, network, force, wordcloud, sunburst, bullet, quadrant,
// violin, contour, parallel coordinates, dendrogram, depth (order book).
// Same ChartWidget model, same card shell, same degrade contract; labels live
// in Text rows below the Canvas (no text painting inside draw), matching the
// house style of WidgetsCharts2.kt.
// ---------------------------------------------------------------------------

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.ui.theme.GsTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val CHART_LAYOUT_W = 320f
private const val CHART_LAYOUT_H = 190f

private fun groupHue(group: String): Color {
    val idx = if (group.isEmpty()) 0 else abs(group.hashCode()) % 12
    return seriesColor(idx)
}

// --- sankey ------------------------------------------------------------------

@Composable
internal fun SankeyChart(w: ChartWidget) {
    val links = (w.links.ifEmpty { w.edges }).filter { it.source.isNotEmpty() && it.target.isNotEmpty() }
    if (links.isEmpty() || w.nodes.isEmpty()) return
    val indexOf = HashMap<String, Int>()
    w.nodes.forEachIndexed { i, n -> if (n.id.isNotEmpty()) indexOf[n.id] = i }
    val targeted = links.mapNotNull { l -> indexOf[l.target] }.toSet()
    val total = max(1f, links.sumOf { max(it.value, 0.1f).toDouble() }.toFloat())
    val usable = CHART_LAYOUT_H - 16f
    val accent = seriesColor(0)
    val grid = GsTheme.colors.divider
    Canvas(Modifier.fillMaxWidth().height(190.dp).padding(top = 8.dp)) {
        val cw = size.width
        val ch = size.height
        val scale = cw / CHART_LAYOUT_W
        val sy = ch / CHART_LAYOUT_H
        val sources = w.nodes.withIndex().filter { it.index !in targeted }.ifEmpty { w.nodes.withIndex().take(3) }
        val targets = w.nodes.withIndex().filter { it.index in targeted }.ifEmpty { w.nodes.withIndex().drop(3) }
        val srcH = usable / max(1, sources.size)
        val dstH = usable / max(1, targets.size)
        val srcBase = HashMap<Int, Float>()
        val dstBase = HashMap<Int, Float>()
        sources.forEach { (j, entry) -> srcBase[entry.index] = 8f + j * srcH }
        targets.forEach { (j, entry) -> dstBase[entry.index] = 8f + j * dstH }
        val srcOff = HashMap<Int, Float>()
        val dstOff = HashMap<Int, Float>()
        links.forEach { l ->
            val si = indexOf[l.source]
            val ti = indexOf[l.target]
            if (si == null || ti == null) return@forEach
            val h = ((max(l.value, 0.1f) / total) * usable).coerceIn(2f, srcH - 3f)
            val x0 = cw * 0.24f
            val x1 = cw * 0.76f
            val sTop = (srcBase[si] ?: 8f) + (srcOff[si] ?: 0f)
            val dTop = (dstBase[ti] ?: 8f) + (dstOff[ti] ?: 0f)
            val path = Path().apply {
                moveTo(x0, sTop)
                cubicTo((x0 + x1) / 2f, sTop, (x0 + x1) / 2f, dTop, x1, dTop)
                lineTo(x1, dTop + h)
                cubicTo((x0 + x1) / 2f, dTop + h, (x0 + x1) / 2f, sTop + h, x0, sTop + h)
                close()
            }
            drawPath(path, accent.copy(alpha = 0.30f))
            srcOff[si] = (srcOff[si] ?: 0f) + h + 2f
            dstOff[ti] = (dstOff[ti] ?: 0f) + h + 2f
        }
        sources.forEachIndexed { i, (_, n) ->
            val y = srcBase[n.index] ?: 8f
            drawRect(seriesColor(i % 12), topLeft = Offset(cw * 0.18f, y * sy), size = Size(9f * scale, min(srcH - 4f, usable - y).coerceAtLeast(4f)))
        }
        targets.forEachIndexed { i, (_, n) ->
            val y = dstBase[n.index] ?: 8f
            drawRect(seriesColor((i + 5) % 12), topLeft = Offset(cw * 0.78f, y * sy), size = Size(9f * scale, min(dstH - 4f, usable - y).coerceAtLeast(4f)))
        }
        drawLine(grid, Offset(cw * 0.5f, 4f), Offset(cw * 0.5f, ch - 4f), strokeWidth = 1f)
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            w.nodes.take(3).forEach { n ->
                Text(n.label.ifEmpty { n.id }, style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            w.nodes.drop(3).take(3).forEach { n ->
                Text(n.label.ifEmpty { n.id }, style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}

// --- network -----------------------------------------------------------------

@Composable
internal fun NetworkChart(w: ChartWidget) {
    val links = w.links + w.edges
    val idx = HashMap<String, Int>()
    w.nodes.forEachIndexed { i, n -> idx[n.id] = i }
    Canvas(Modifier.fillMaxWidth().height(200.dp).padding(top = 8.dp)) {
        val cw = size.width
        val ch = size.height
        val cx = cw / 2f
        val cy = ch / 2f
        val r = min(cx, cy) * 0.72f
        val n = w.nodes.size.coerceAtLeast(1)
        val pos = w.nodes.mapIndexed { i, node ->
            val a = (i.toFloat() / n) * 2f * PI.toFloat()
            Offset(cx + r * cos(a), cy + r * sin(a))
        }
        links.forEach { l ->
            val a = idx[l.source]
            val b = idx[l.target]
            if (a != null && b != null && a < pos.size && b < pos.size) {
                drawLine(GsTheme.colors.divider, pos[a], pos[b], strokeWidth = (l.value * 0.6f).coerceIn(0.8f, 3f))
            }
        }
        pos.forEachIndexed { i, p ->
            drawCircle(seriesColor(i % 12), radius = 7f, center = p)
            drawCircle(Color.White.copy(alpha = 0.6f), radius = 3f, center = p)
        }
    }
    val groups = w.nodes.map { it.group }.filter { it.isNotEmpty() }.distinct().take(6)
    if (groups.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            groups.forEach { g ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.padding(end = 4.dp).size(width = 10.dp, height = 8.dp).background(groupHue(g), RoundedCornerShape(4.dp)))
                    Text(g, style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary)
                }
            }
        }
    }
}

// --- force -------------------------------------------------------------------

private fun runForceLayout(n: Int, links: List<Pair<Int, Int>>): List<Offset> {
    if (n <= 0) return emptyList()
    val rnd = java.util.Random(7L)
    var pts = List(n) { i ->
        val a = (i.toFloat() / n) * 2f * PI.toFloat()
        Offset(CHART_LAYOUT_W / 2f + 60f * cos(a) * (0.6f + rnd.nextFloat() * 0.4f), CHART_LAYOUT_H / 2f + 60f * sin(a) * (0.6f + rnd.nextFloat() * 0.4f))
    }
    repeat(140) {
        val fx = FloatArray(n)
        val fy = FloatArray(n)
        for (i in 0 until n) for (j in i + 1 until n) {
            val dx = pts[i].x - pts[j].x
            val dy = pts[i].y - pts[j].y
            val d2 = max(40f, dx * dx + dy * dy)
            val f = 1600f / d2
            val d = sqrt(d2)
            fx[i] += dx / d * f
            fy[i] += dy / d * f
            fx[j] -= dx / d * f
            fy[j] -= dy / d * f
        }
        links.forEach { (a, b) ->
            if (a < n && b < n) {
                val dx = pts[b].x - pts[a].x
                val dy = pts[b].y - pts[a].y
                val d = max(1f, sqrt(dx * dx + dy * dy))
                val f = (d - 58f) * 0.03f
                fx[a] += dx / d * f
                fy[a] += dy / d * f
                fx[b] -= dx / d * f
                fy[b] -= dy / d * f
            }
        }
        pts = pts.mapIndexed { i, p ->
            var nx = p.x + fx[i].coerceIn(-4f, 4f)
            var ny = p.y + fy[i].coerceIn(-4f, 4f)
            nx += (CHART_LAYOUT_W / 2f - nx) * 0.02f
            ny += (CHART_LAYOUT_H / 2f - ny) * 0.02f
            Offset(nx.coerceIn(14f, CHART_LAYOUT_W - 14f), ny.coerceIn(14f, CHART_LAYOUT_H - 14f))
        }
    }
    return pts
}

@Composable
internal fun ForceChart(w: ChartWidget) {
    val links = (w.links + w.edges).mapNotNull { l ->
        val a = w.nodes.indexOfFirst { it.id == l.source }
        val b = w.nodes.indexOfFirst { it.id == l.target }
        if (a >= 0 && b >= 0) a to b else null
    }
    var pts by remember(w) { mutableStateOf(runForceLayout(w.nodes.size, links)) }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(210.dp)
            .padding(top = 8.dp)
            .pointerInput(w) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val scale = size.width / CHART_LAYOUT_W
                    val target = Offset(change.position.x / scale, change.position.y / scale)
                    val cur = pts
                    if (cur.isEmpty()) return@detectDragGestures
                    var best = 0
                    var bestD = Float.MAX_VALUE
                    cur.forEachIndexed { i, p ->
                        val dx = p.x - target.x
                        val dy = p.y - target.y
                        val d = dx * dx + dy * dy
                        if (d < bestD) { bestD = d; best = i }
                    }
                    pts = cur.toMutableList().also { it[best] = (cur[best] + Offset(dragAmount.x / scale, dragAmount.y / scale)) }
                }
            }
    ) {
        val scale = size.width / CHART_LAYOUT_W
        val sy = size.height / CHART_LAYOUT_H
        val pos = pts.map { Offset(it.x * scale, it.y * sy) }
        links.forEach { (a, b) ->
            if (a < pos.size && b < pos.size) drawLine(GsTheme.colors.divider, pos[a], pos[b], strokeWidth = 1.4f)
        }
        pos.forEachIndexed { i, p ->
            drawCircle(seriesColor(i % 12), radius = 8f * scale, center = p)
        }
    }
    Text(
        "drag nodes to re-relax the layout",
        style = MaterialTheme.typography.labelSmall,
        color = GsTheme.colors.textTertiary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

// --- wordcloud ---------------------------------------------------------------

@Composable
internal fun WordcloudChart(w: ChartWidget) {
    val items = w.items.sortedByDescending { max(it.weight, it.value) }.take(24)
    val maxW = max(1f, items.maxOfOrNull { max(it.weight, it.value) } ?: 1f)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { item ->
                    val weight = max(item.weight, item.value)
                    val t = item.text.ifEmpty { item.label }
                    if (t.isNotEmpty()) {
                        Text(
                            t,
                            fontSize = (11 + (weight / maxW) * 13).sp,
                            fontWeight = if (weight / maxW > 0.6f) FontWeight.SemiBold else FontWeight.Normal,
                            color = seriesColor((t.hashCode() % 12 + 12) % 12),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

// --- sunburst ----------------------------------------------------------------

private fun subtreeValue(node: TreeNodeData): Float {
    val childSum = node.children.sumOf { subtreeValue(it).toDouble() }.toFloat()
    return if (childSum > 0f) childSum else max(node.value, 1f)
}

@Composable
internal fun SunburstChart(w: ChartWidget) {
    val root = w.root ?: return
    val kids = root.children
    Canvas(Modifier.fillMaxWidth().height(200.dp).padding(top = 8.dp)) {
        val cw = size.width
        val ch = size.height
        val cx = cw / 2f
        val cy = ch / 2f
        val outer = min(cx, cy) - 4f
        val ringW = outer / 3f
        var start = -90f
        val total = max(1f, kids.sumOf { subtreeValue(it).toDouble() }.toFloat())
        kids.forEachIndexed { i, child ->
            val sweep = (subtreeValue(child) / total) * 360f
            drawArc(
                seriesColor(i % 12).copy(alpha = 0.9f),
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(cx - outer, cy - outer),
                size = Size(outer * 2, outer * 2),
                style = Stroke(ringW)
            )
            val inner = outer - ringW
            val sub = child.children
            val subTotal = max(1f, sub.sumOf { subtreeValue(it).toDouble() }.toFloat())
            var s2 = start
            sub.forEachIndexed { j, gc ->
                val sweep2 = (subtreeValue(gc) / subTotal) * sweep
                drawArc(
                    seriesColor((i * 3 + j + 1) % 12).copy(alpha = 0.6f),
                    startAngle = s2,
                    sweepAngle = sweep2,
                    useCenter = false,
                    topLeft = Offset(cx - inner, cy - inner),
                    size = Size(inner * 2, inner * 2),
                    style = Stroke(ringW)
                )
                s2 += sweep2
            }
            start += sweep
        }
        drawCircle(GsTheme.colors.divider, radius = 3f, center = Offset(cx, cy))
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        kids.take(5).forEachIndexed { i, c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = 4.dp).height(8.dp).fillMaxWidth(0.03f).background(seriesColor(i % 12), androidx.compose.foundation.shape.RoundedCornerShape(4.dp)))
                Text(c.label, style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// --- bullet ------------------------------------------------------------------

@Composable
internal fun BulletChart(w: ChartWidget) {
    val items = w.items.take(8)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { i, item ->
            val label = item.label.ifEmpty { item.text }
            val value = if (item.value.isNaN()) 0f else item.value
            val target = if (item.target.isNaN()) Float.NaN else item.target
            val track = max(max(item.max, max(value, if (target.isNaN()) 0f else target)) * 1.15f, 1f)
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Canvas(Modifier.fillMaxWidth().height(14.dp).padding(top = 2.dp)) {
                    val cw = size.width
                    val band = item.ranges.take(3)
                    band.forEachIndexed { bi, r ->
                        val lo = r.getOrNull(0) ?: 0f
                        val hi = r.getOrNull(1) ?: lo
                        drawRect(
                            seriesColor(i % 12).copy(alpha = 0.14f + bi * 0.10f),
                            topLeft = Offset(cw * (lo / track).coerceIn(0f, 1f), 0f),
                            size = Size(cw * ((hi - lo) / track).coerceIn(0f, 1f), size.height)
                        )
                    }
                    drawRect(
                        seriesColor(i % 12),
                        topLeft = Offset(0f, size.height * 0.3f),
                        size = Size(cw * (value / track).coerceIn(0f, 1f), size.height * 0.4f)
                    )
                    if (!target.isNaN()) {
                        val tx = cw * (target / track).coerceIn(0f, 1f)
                        drawRect(GsTheme.colors.textPrimary, topLeft = Offset(tx - 1.5f, 0f), size = Size(3f, size.height))
                    }
                }
            }
        }
    }
}

// --- quadrant ----------------------------------------------------------------

@Composable
internal fun QuadrantChart(w: ChartWidget) {
    val pts = w.series.flatMap { s -> s.data.map { p -> Triple(p.getOrElse(0) { 0f }, p.getOrElse(1) { 0f }, s.name) } }
    if (pts.isEmpty()) return
    val xs = pts.map { it.first }
    val ys = pts.map { it.second }
    val xLo = (xs.minOrNull() ?: 0f)
    val xHi = max(xLo + 1f, xs.maxOrNull() ?: 1f)
    val yLo = (ys.minOrNull() ?: 0f)
    val yHi = max(yLo + 1f, ys.maxOrNull() ?: 1f)
    val labels = w.axes.take(2)
    Canvas(Modifier.fillMaxWidth().height(200.dp).padding(top = 8.dp)) {
        val cw = size.width
        val ch = size.height
        val mx = cw / 2f
        val my = ch / 2f
        drawRect(GsTheme.colors.divider.copy(alpha = 0.12f), topLeft = Offset(0f, 0f), size = Size(mx, my))
        drawRect(GsTheme.colors.divider.copy(alpha = 0.12f), topLeft = Offset(mx, my), size = Size(mx, my))
        drawLine(GsTheme.colors.divider, Offset(mx, 0f), Offset(mx, ch), strokeWidth = 1f)
        drawLine(GsTheme.colors.divider, Offset(0f, my), Offset(cw, my), strokeWidth = 1f)
        pts.forEachIndexed { i, (x, y, name) ->
            val si = w.series.indexOfFirst { it.name == name }.coerceAtLeast(0)
            val px = mx + (((x - xLo) / (xHi - xLo)) * cw - mx).coerceIn(-mx + 8f, mx - 8f)
            val py = my - (((y - yLo) / (yHi - yLo)) * ch - my).coerceIn(-my + 8f, my - 8f)
            drawCircle(seriesColor(si % 12).copy(alpha = 0.85f), radius = 6f, center = Offset(px, py))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(labels.getOrNull(0) ?: "", style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary, modifier = Modifier.padding(start = 4.dp))
        Text(labels.getOrNull(1) ?: "", style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary, modifier = Modifier.padding(end = 4.dp))
    }
}

// --- violin ------------------------------------------------------------------

private fun violinPath(values: List<Float>, x0: Float, colW: Float, usableH: Float): Pair<Path, Path> {
    val n = values.size
    val mean = values.sum() / max(1, n)
    val variance = values.sumOf { v -> (v - mean).toDouble().let { it * it } } / max(1, n)
    val sd = max(1e-4f, sqrt(variance.toFloat()))
    val h = max(1e-4f, 1.06f * sd * max(1f, n.toFloat()).pow(0.2f))
    val lo = values.minOrNull() ?: 0f
    val hi = max(lo + 1e-4f, values.maxOrNull() ?: 1f)
    val pad = (hi - lo) * 0.25f + h
    val span = hi - lo + 2 * pad
    val grid = 40
    val left = Path()
    val right = Path()
    var first = true
    for (g in 0..grid) {
        val x = lo - pad + (g.toFloat() / grid) * span
        var density = 0f
        values.forEach { v ->
            val u = (x - v) / h
            density += exp(-0.5f * u * u)
        }
        density /= n * h * sqrt(2f * PI.toFloat())
        val y = usableH - ((x - (lo - pad)) / span).coerceIn(0f, 1f) * usableH
        val halfW = (density / 1.6f).coerceAtMost(0.48f) * colW
        if (first) {
            left.moveTo(x0 + colW / 2f - halfW, y)
            right.moveTo(x0 + colW / 2f + halfW, y)
            first = false
        } else {
            left.lineTo(x0 + colW / 2f - halfW, y)
            right.lineTo(x0 + colW / 2f + halfW, y)
        }
    }
    left.close()
    right.close()
    return left to right
}

@Composable
internal fun ViolinChart(w: ChartWidget) {
    val series = w.series.take(8).filter { it.data.isNotEmpty() }
    if (series.isEmpty()) return
    val colors = series.indices.map { seriesColor(it) }
    val usableH = CHART_LAYOUT_H - 24f
    Canvas(Modifier.fillMaxWidth().height(190.dp).padding(top = 8.dp)) {
        val cw = size.width
        val sy = size.height / CHART_LAYOUT_H
        val colW = cw / series.size
        series.forEachIndexed { i, s ->
            val values = s.data.map { it.getOrElse(1) { 0f } }
            val (leftP, rightP) = violinPath(values, i * colW, colW, usableH)
            drawPath(leftP, colors[i % colors.size].copy(alpha = 0.55f))
            drawPath(rightP, colors[i % colors.size].copy(alpha = 0.55f))
            val mean = values.sum() / values.size
            val lo = values.minOrNull() ?: 0f
            val hi = max(lo + 1e-4f, values.maxOrNull() ?: 1f)
            val my = usableH - ((mean - lo) / (hi - lo)).coerceIn(0f, 1f) * usableH
            drawLine(GsTheme.colors.textPrimary, Offset(i * colW + colW * 0.34f, my * sy), Offset(i * colW + colW * 0.66f, my * sy), strokeWidth = 1.6f)
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        series.forEachIndexed { i, s ->
            Text(s.name.ifEmpty { "S${i + 1}" }, style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// --- contour -----------------------------------------------------------------

@Composable
internal fun ContourChart(w: ChartWidget) {
    val points = w.points
    if (points.size < 9) return
    val K = 12
    val grid = Array(K) { FloatArray(K) }
    val counts = Array(K) { IntArray(K) }
    val xs = points.map { it.getOrElse(0) { 0f } }
    val ys = points.map { it.getOrElse(1) { 0f } }
    val xLo = xs.minOrNull() ?: 0f
    val xHi = max(xLo + 1e-4f, xs.maxOrNull() ?: 1f)
    val yLo = ys.minOrNull() ?: 0f
    val yHi = max(yLo + 1e-4f, ys.maxOrNull() ?: 1f)
    points.forEach { p ->
        val gx = (((p.getOrElse(0) { 0f } - xLo) / (xHi - xLo)) * (K - 1)).toInt().coerceIn(0, K - 1)
        val gy = (((p.getOrElse(1) { 0f } - yLo) / (yHi - yLo)) * (K - 1)).toInt().coerceIn(0, K - 1)
        grid[gy][gx] += p.getOrElse(2) { 0f }
        counts[gy][gx] += 1
    }
    for (gy in 0 until K) for (gx in 0 until K) if (counts[gy][gx] > 0) grid[gy][gx] /= counts[gy][gx]
    val zLo = grid.flatMap { it.toList() }.minOrNull() ?: 0f
    val zHi = max(zLo + 1e-4f, grid.flatMap { it.toList() }.maxOrNull() ?: 1f)
    val levels = (if (w.levels in 3..12) w.levels else 6)
    val accent = seriesColor(0)
    Canvas(Modifier.fillMaxWidth().height(200.dp).padding(top = 8.dp)) {
        val cw = size.width
        val ch = size.height
        val cellW = cw / (K - 1)
        val cellH = ch / (K - 1)
        fun pointAt(gx: Int, gy: Int, corner: Int, level: Float): Offset {
            val z = grid[gy.coerceIn(0, K - 1)][gx.coerceIn(0, K - 1)]
            val zNext = when (corner) {
                1 -> grid[gy.coerceIn(0, K - 1)][(gx + 1).coerceIn(0, K - 1)]
                2 -> grid[(gy + 1).coerceIn(0, K - 1)][(gx + 1).coerceIn(0, K - 1)]
                3 -> grid[(gy + 1).coerceIn(0, K - 1)][gx.coerceIn(0, K - 1)]
                else -> z
            }
            val t = ((level - z) / (zNext - z).coerceAtLeast(1e-6f)).coerceIn(0f, 1f)
            val px = when (corner) {
                1 -> gx + t
                2 -> gx + 1f
                else -> gx.toFloat()
            }
            val py = when (corner) {
                3 -> gy + t
                2 -> gy + 1f
                else -> gy.toFloat()
            }
            return Offset(px * cellW, py * cellH)
        }
        for (li in 0 until levels) {
            val level = zLo + (zHi - zLo) * ((li + 1f) / (levels + 1f))
            val alpha = 0.25f + 0.55f * ((li + 1f) / levels)
            for (gy in 0 until K - 1) for (gx in 0 until K - 1) {
                val c0 = grid[gy][gx] > level
                val c1 = grid[gy][gx + 1] > level
                val c2 = grid[gy + 1][gx + 1] > level
                val c3 = grid[gy + 1][gx] > level
                val idx = (if (c0) 1 else 0) + (if (c1) 2 else 0) + (if (c2) 4 else 0) + (if (c3) 8 else 0)
                if (idx == 0 || idx == 15) continue
                val e0 = pointAt(gx, gy, 0, level)
                val e1 = pointAt(gx, gy, 1, level)
                val e2 = pointAt(gx, gy, 2, level)
                val e3 = pointAt(gx, gy, 3, level)
                val segs = when (idx) {
                    1, 14 -> listOf(e3 to e0)
                    2, 13 -> listOf(e0 to e1)
                    3, 12 -> listOf(e3 to e1)
                    4, 11 -> listOf(e1 to e2)
                    6, 9 -> listOf(e0 to e2)
                    7, 8 -> listOf(e3 to e2)
                    else -> listOf(e3 to e0, e1 to e2) // 5, 10 saddle
                }
                segs.forEach { (a, b) ->
                    drawLine(accent.copy(alpha = alpha), a, b, strokeWidth = 1.4f)
                }
                if (idx == 5 || idx == 10) drawLine(accent.copy(alpha = alpha * 0.7f), e0, e1, strokeWidth = 1.2f)
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("z: ${"%.2f".format(zLo)} → ${"%.2f".format(zHi)}", style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary)
        Text("levels $levels", style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary)
    }
}

// --- parallel coordinates ----------------------------------------------------

@Composable
internal fun ParallelChart(w: ChartWidget) {
    val axes = w.axes.take(10)
    val series = w.series.filter { it.values.isNotEmpty() }.take(12)
    if (axes.size < 3 || series.isEmpty()) return
    val colors = series.indices.map { seriesColor(it) }
    val axisX = { i: Int, cw: Float -> 24f + (i.toFloat() / (axes.size - 1)) * (cw - 48f) }
    Canvas(Modifier.fillMaxWidth().height(190.dp).padding(top = 8.dp)) {
        val cw = size.width
        val ch = size.height
        val top = 10f
        val bottom = ch - 10f
        axes.forEachIndexed { i, _ ->
            val col = series.map { it.values.getOrElse(i) { 0f } }
            val lo = col.minOrNull() ?: 0f
            val hi = max(lo + 1e-4f, col.maxOrNull() ?: 1f)
            val x = axisX(i, cw)
            drawLine(GsTheme.colors.divider, Offset(x, top), Offset(x, bottom), strokeWidth = 1.2f)
        }
        series.forEachIndexed { si, s ->
            val path = Path()
            axes.forEachIndexed { i, _ ->
                val col = series.map { it.values.getOrElse(i) { 0f } }
                val lo = col.minOrNull() ?: 0f
                val hi = max(lo + 1e-4f, col.maxOrNull() ?: 1f)
                val t = ((s.values.getOrElse(i) { 0f } - lo) / (hi - lo)).coerceIn(0f, 1f)
                val px = axisX(i, cw)
                val py = top + (1f - t) * (bottom - top)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, colors[si % colors.size].copy(alpha = 0.85f), style = Stroke(2f))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        axes.take(6).forEach { axis ->
            Text(axis, style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
}

// --- dendrogram --------------------------------------------------------------

private data class DendroLayout(val x: Float, val y: Float, val label: String, val isLeaf: Boolean)

private fun countLeaves(node: TreeNodeData): Int =
    if (node.children.isEmpty()) 1 else node.children.sumOf { countLeaves(it) }

private fun leavesOf(node: TreeNodeData): List<TreeNodeData> =
    if (node.children.isEmpty()) listOf(node) else node.children.flatMap { leavesOf(it) }

private fun maxDistOf(node: TreeNodeData): Float =
    max(node.distance, node.children.maxOfOrNull { maxDistOf(it) } ?: 0f)

private fun buildDendroLayout(node: TreeNodeData, slot: FloatArray, maxDist: Float, usableH: Float, out: MutableMap<TreeNodeData, DendroLayout>) {
    if (node.children.isEmpty()) {
        val x = (slot[0] + 0.5f) * CHART_LAYOUT_W / max(1f, slot[1])
        slot[0] += 1f
        out[node] = DendroLayout(x, usableH, node.label, true)
        return
    }
    node.children.forEach { buildDendroLayout(it, slot, maxDist, usableH, out) }
    val kids = node.children.mapNotNull { out[it] }
    if (kids.isEmpty()) return
    val x = kids.map { it.x }.average().toFloat()
    val y = usableH - ((node.distance.coerceAtLeast(0f) / max(1e-4f, maxDist)).coerceIn(0f, 1f)) * (usableH - 14f) - 6f
    out[node] = DendroLayout(x, y, node.label, false)
}

@Composable
internal fun DendrogramChart(w: ChartWidget) {
    val root = w.root ?: return
    val leaves = countLeaves(root)
    if (leaves == 0) return
    val layout = remember(w) {
        val slot = floatArrayOf(0f, leaves.toFloat())
        val map = HashMap<TreeNodeData, DendroLayout>()
        buildDendroLayout(root, slot, maxDistOf(root), CHART_LAYOUT_H - 24f, map)
        map
    }
    Canvas(Modifier.fillMaxWidth().height(190.dp).padding(top = 8.dp)) {
        val scale = size.width / CHART_LAYOUT_W
        val sy = size.height / CHART_LAYOUT_H
        val baseY = size.height - 22f * sy
        val ink = seriesColor(0)
        val grid = GsTheme.colors.divider
        drawLine(grid, Offset(0f, baseY), Offset(size.width, baseY), strokeWidth = 1f)
        layout.values.filter { it.isLeaf }.forEach { l ->
            drawLine(grid, Offset(l.x * scale, l.y * sy), Offset(l.x * scale, baseY), strokeWidth = 1f)
        }
        fun drawNode(node: TreeNodeData) {
            val p = layout[node] ?: return
            node.children.forEach { c ->
                val k = layout[c] ?: return@forEach
                // elbow: child up to the parent's merge height, across, then the parent
                drawLine(ink, Offset(k.x * scale, k.y * sy), Offset(k.x * scale, p.y * sy), strokeWidth = 1.6f)
                drawLine(ink, Offset(k.x * scale, p.y * sy), Offset(p.x * scale, p.y * sy), strokeWidth = 1.6f)
                drawNode(c)
            }
        }
        drawNode(root)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        leavesOf(root).take(7).forEach { l ->
            Text(l.label, style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// --- depth (order book) ------------------------------------------------------

@Composable
internal fun DepthChart(w: ChartWidget) {
    val bids = w.bids.take(40).map { it[0] to it.getOrElse(1) { 0f } }
    val asks = w.asks.take(40).map { it[0] to it.getOrElse(1) { 0f } }
    if (bids.isEmpty() && asks.isEmpty()) return
    val maxSize = max(1f, (bids + asks).maxOfOrNull { it.second } ?: 1f)
    val prices = (bids + asks).map { it.first }
    val pLo = (prices.minOrNull() ?: 0f)
    val pHi = max(pLo + 1e-4f, prices.maxOrNull() ?: 1f)
    val bestBid = bids.maxOfOrNull { it.first }
    val bestAsk = asks.minOfOrNull { it.first }
    val spread = if (bestBid != null && bestAsk != null) bestAsk - bestBid else Float.NaN
    val jade = seriesColor(1)
    val crimson = seriesColor(9)
    Canvas(Modifier.fillMaxWidth().height(190.dp).padding(top = 8.dp)) {
        val cw = size.width
        val ch = size.height
        val midX = cw / 2f
        val rowH = (ch - 8f) / max(1, max(bids.size, asks.size)).coerceAtMost(16)
        bids.sortedByDescending { it.first }.take(16).forEachIndexed { i, (price, size) ->
            val wpx = (size / maxSize) * (cw * 0.44f)
            val y = ch / 2f - (i + 0.5f) * rowH
            if (y >= 0f) drawRect(jade.copy(alpha = 0.75f), topLeft = Offset(midX - wpx, y), size = Size(wpx, (rowH - 2f).coerceAtLeast(2f)))
        }
        asks.sortedBy { it.first }.take(16).forEachIndexed { i, (price, size) ->
            val wpx = (size / maxSize) * (cw * 0.44f)
            val y = ch / 2f + i * rowH
            if (y <= ch) drawRect(crimson.copy(alpha = 0.75f), topLeft = Offset(midX, y), size = Size(wpx, (rowH - 2f).coerceAtLeast(2f)))
        }
        drawLine(GsTheme.colors.divider, Offset(midX, 0f), Offset(midX, ch), strokeWidth = 1.2f)
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("bid ${bestBid?.toString() ?: "—"}", style = MaterialTheme.typography.labelSmall, color = jade)
        Text(if (spread.isNaN()) "" else "spread ${"%.2f".format(spread)}", style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary)
        Text("ask ${bestAsk?.toString() ?: "—"}", style = MaterialTheme.typography.labelSmall, color = crimson)
    }
}
