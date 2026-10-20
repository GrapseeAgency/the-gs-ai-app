package com.grapsee.gsai.ui.chat.content

// ---------------------------------------------------------------------------
// Catalogue v3 kinds — entry views. Each shows the real payload today and is
// upgraded to the full interactive world (rAF sims, projected 3D, canvas
// editing, SSE collab) in the following commits. No fake data, ever: every
// row on these cards comes from the model's payload.
// ---------------------------------------------------------------------------

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
private fun SpecRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textTertiary)
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = GsTheme.colors.textPrimary)
    }
}

@Composable
internal fun SimWidgetView(w: SimWidget) {
    WidgetCardShell(w.title.ifEmpty { "Simulation — ${w.sim}" }, "sim") {
        SpecRow("world", w.sim)
        SpecRow("gravity", "${"%.1f".format(w.gravity)} m/s²")
        SpecRow("speed", "${"%.2f".format(w.speed)}×")
        SpecRow("damping", "${"%.2f".format(w.damping)}")
        SpecRow("count", w.count.toString())
        Text(
            if (w.interactive) "touch world — rendering live in an upcoming build of this client" else "ambient world",
            style = MaterialTheme.typography.labelSmall,
            color = GsTheme.colors.textTertiary,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
internal fun Stage3DWidgetView(w: Stage3DWidget) {
    WidgetCardShell(w.title.ifEmpty { "3D stage — ${w.view}" }, "stage3d") {
        SpecRow("view", w.view)
        SpecRow("nodes", w.nodes.size.toString())
        SpecRow("bonds", w.bonds.size.toString())
        if (w.z.isNotBlank()) SpecRow("z(x, y)", w.z)
        SpecRow("span", "${"%.1f".format(w.span)}")
        Text(
            "orbit stage — projected rendering in an upcoming build of this client",
            style = MaterialTheme.typography.labelSmall,
            color = GsTheme.colors.textTertiary,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
internal fun CollabWidgetView(w: CollabWidgetData) {
    WidgetCardShell(w.title.ifEmpty { "Shared — ${w.surface}" }, "collab") {
        SpecRow("surface", w.surface)
        SpecRow("room", w.room)
        if (w.items.isNotEmpty()) SpecRow("items", w.items.size.toString())
        Text(
            "live shared state — the SSE room connects in an upcoming build of this client",
            style = MaterialTheme.typography.labelSmall,
            color = GsTheme.colors.textTertiary,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
