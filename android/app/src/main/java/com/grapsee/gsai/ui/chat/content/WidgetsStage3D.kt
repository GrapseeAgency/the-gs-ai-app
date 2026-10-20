package com.grapsee.gsai.ui.chat.content

// ---------------------------------------------------------------------------
// Stage3D kind — a REAL projected 3D renderer, no new dependencies: rotate
// around yaw/pitch, perspective-project, depth-sort (painter's algorithm),
// draw bonds as depth-tinted lines and nodes as scaled circles. surface3d
// builds its grid from the payload's z expression THROUGH CalcExpr — the same
// whitelist the validator gates with. If the payload has no nodes and no z,
// the card says so instead of inventing a scene.
// ---------------------------------------------------------------------------

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.theme.GsTheme
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private data class StagePoint(val x: Float, val y: Float, val z: Float, val r: Float, val hueIdx: Int, val label: String)

private fun buildStage(w: Stage3DWidget): List<StagePoint> {
    val pts = mutableListOf<StagePoint>()
    when (w.view) {
        "surface3d" -> {
            // z = f(x, y) heightfield over [-span, span]; 12x12 — the SAME
            // expression the validator already proved evaluates.
            val span = w.span.coerceIn(0.5f, 10f)
            if (w.z.isNotBlank()) {
                val n = 12
                var minZ = Float.MAX_VALUE
                var maxZ = -Float.MAX_VALUE
                val grid = Array(n) { gy -> FloatArray(n) { gx ->
                    val x = -span + 2f * span * gx / (n - 1)
                    val y = -span + 2f * span * gy / (n - 1)
                    (CalcExpr.eval(w.z, mapOf("x" to x.toDouble(), "y" to y.toDouble()))?.toFloat() ?: 0f).also { v ->
                        if (v < minZ) minZ = v
                        if (v > maxZ) maxZ = v
                    }
                } }
                val zRange = max(1e-4f, maxZ - minZ)
                for (gy in 0 until n) for (gx in 0 until n) {
                    val hz = (grid[gy][gx] - minZ) / zRange
                    pts.add(StagePoint(
                        x = -span + 2f * span * gx / (n - 1),
                        y = hz * span * 0.7f - span * 0.35f,
                        z = -span + 2f * span * gy / (n - 1),
                        r = 0.06f,
                        hueIdx = (hz * 11).toInt().coerceIn(0, 11),
                        label = ""
                    ))
                }
            }
        }
        else -> {
            w.nodes.take(60).forEachIndexed { i, n ->
                pts.add(StagePoint(n.x, n.y, n.z, if (n.r > 0f) n.r else 0.28f, abs(n.group.hashCode()) % 12, n.label))
            }
        }
    }
    return pts
}

private fun bondIndices(w: Stage3DWidget, size: Int): List<Pair<Int, Int>> =
    w.bonds.filter { it.size >= 2 && it[0] in 0 until size && it[1] in 0 until size }.map { it[0] to it[1] }

@Composable
internal fun Stage3DWidgetView(w: Stage3DWidget) {
    val stage = remember(w) { buildStage(w) }
    val bonds = remember(w) { bondIndices(w, stage.size) }
    var yaw by remember(w) { mutableFloatStateOf(0.6f) }
    var pitch by remember(w) { mutableFloatStateOf(0.35f) }
    var frame by remember(w) { mutableIntStateOf(0) }
    var spinOn by remember(w) { mutableStateOf(w.spin) }

    LaunchedEffect(w, spinOn) {
        var last = 0L
        while (spinOn) {
            withFrameNanos { now ->
                if (last != 0L) yaw += ((now - last) / 1_000_000_000f) * 0.5f
                last = now
            }
            frame++
        }
    }

    WidgetCardShell(w.title.ifEmpty { "3D stage — ${w.view}" }, "stage3d") {
        if (stage.isEmpty()) {
            Text(
                "this ${w.view} payload has no nodes and no z field — nothing to render, and this client does not invent scenes",
                style = MaterialTheme.typography.bodySmall,
                color = GsTheme.colors.textSecondary,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            return@WidgetCardShell
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(230.dp)
                .pointerInput(w) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        yaw += dragAmount.x * 0.01f
                        pitch = (pitch + dragAmount.y * 0.008f).coerceIn(-1.2f, 1.2f)
                        spinOn = false
                    }
                }
        ) {
            val cw = size.width
            val ch = size.height
            val cy = cos(yaw)
            val sy = sin(yaw)
            val cp = cos(pitch)
            val sp = sin(pitch)
            val d = 3.2f
            val scale = min(cw, ch) / 2.4f
            val projected = stage.map { p ->
                // rotate yaw (Y axis) then pitch (X axis), then perspective
                val x1 = p.x * cy - p.z * sy
                val z1 = p.x * sy + p.z * cy
                val y2 = p.y * cp - z1 * sp
                val z2 = p.y * sp + z1 * cp
                val persp = 1f / max(0.4f, d + z2)
                Triple(
                    Offset(cw / 2f + x1 * persp * scale, ch / 2f - y2 * persp * scale),
                    z2,
                    (p.r * 26f * persp * scale / 60f).coerceIn(2f, 26f)
                )
            }
            val order = projected.withIndex().sortedByDescending { it.value.second }
            // bonds first, depth-tinted
            bonds.forEach { (a, b) ->
                if (a < projected.size && b < projected.size) {
                    val pa = projected[a]
                    val pb = projected[b]
                    val depthAlpha = (0.75f - ((pa.second + pb.second) / 2f) * 0.12f).coerceIn(0.25f, 0.8f)
                    drawLine(seriesColor(0).copy(alpha = depthAlpha), pa.first, pb.first, strokeWidth = 2f)
                }
            }
            order.forEach { (_, triple) ->
                val (pt, z, radius) = triple
                val depth = ((z + 2f) / 4f).coerceIn(0f, 1f)
                val color = WidgetPalette.legible(WidgetPalette.at(stage[projected.indexOf(triple)].hueIdx), WidgetPalette.onDarkCanvas())
                drawCircle(color.copy(alpha = 0.35f + 0.55f * depth), radius = radius, center = pt)
                drawCircle(Color.White.copy(alpha = 0.25f * depth), radius = radius * 0.4f, center = Offset(pt.x - radius * 0.25f, pt.y - radius * 0.25f))
            }
        }
        Row(Modifier.padding(top = 4.dp)) {
            Text(
                if (w.view == "vr" || w.view == "ar") {
                    "magic-window ${w.view} — drag orbits · true device-parallax needs the inline WebXR upgrade (labelled Tier 2)"
                } else {
                    "drag to orbit · ${stage.size} points · ${bonds.size} bonds"
                },
                style = MaterialTheme.typography.labelSmall,
                color = GsTheme.colors.textTertiary
            )
        }
        if (w.z.isNotBlank()) {
            Text(
                "z = ${w.z}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                color = GsTheme.colors.textSecondary
            )
        }
    }
}
