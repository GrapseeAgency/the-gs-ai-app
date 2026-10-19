package com.grapsee.gsai.ui.chat.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.theme.GsTheme
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth

/**
 * Application-style widget: one kind, many layouts.
 *
 * Every layout is native Compose with real state — wizards step, kanban cards
 * move, the calendar switches months, the sheet recomputes sums, the
 * configurator prices selections. Nothing here is model-authored HTML.
 */

@Serializable
data class AppAction(val label: String = "", val prompt: String = "", val kind: String = "prompt")

@Serializable
data class AppItem(
    val title: String = "",
    val subtitle: String = "",
    val meta: String = "",
    val badge: String = "",
    val prompt: String = "",
    val date: String = "",
    val price: Double = 0.0
)

@Serializable
data class AppColumn(val title: String = "", val items: List<AppItem> = emptyList())

@Serializable
data class AppOption(val label: String = "", val detail: String = "", val price: Double = 0.0)

@Serializable
data class AppGroup(val title: String = "", val options: List<AppOption> = emptyList())

@Serializable
data class AppStep(val title: String = "", val body: String = "", val fields: List<FormField> = emptyList())

@Serializable
data class AppWidget(
    val type: String = "wizard",
    val title: String = "",
    val steps: List<AppStep> = emptyList(),
    val columns: List<AppColumn> = emptyList(),
    val items: List<AppItem> = emptyList(),
    val groups: List<AppGroup> = emptyList(),
    val rows: List<List<String>> = emptyList(),
    val actions: List<AppAction> = emptyList(),
    val submitLabel: String = "",
    val month: String = "",
    val slots: List<String> = emptyList(),
    val height: Int = 0
)

internal val APP_TYPES = setOf(
    "wizard", "onboarding", "kanban", "calendar", "sheet", "tasks", "dashboard",
    "config", "configurator", "booking", "schedule", "search", "filter",
    "analytics", "report", "files", "connected", "decision", "scenario"
)

@Composable
internal fun AppWidgetView(w: AppWidget, onSendPrompt: ((String) -> Unit)? = null) {
    val type = w.type.lowercase()
    WidgetCardShell(w.title.ifEmpty { "App" }, type) {
        Column(Modifier.padding(top = 8.dp)) {
            when (type) {
                "wizard", "onboarding", "decision" -> WizardLayout(w, onSendPrompt)
                "kanban" -> KanbanLayout(w, onSendPrompt)
                "calendar" -> CalendarLayout(w, onSendPrompt)
                "sheet" -> SheetLayout(w)
                "tasks" -> TasksLayout(w, onSendPrompt)
                "dashboard", "analytics", "report", "scenario" -> DashboardLayout(w, onSendPrompt)
                "config", "configurator" -> ConfiguratorLayout(w, onSendPrompt)
                "booking", "schedule" -> BookingLayout(w, onSendPrompt)
                "search" -> SearchLayout(w, onSendPrompt)
                "filter" -> FilterLayout(w, onSendPrompt)
                "files", "connected" -> ListLayout(w, onSendPrompt)
                else -> ListLayout(w, onSendPrompt)
            }
        }
    }
}

// --- wizard ------------------------------------------------------------------

@Composable
private fun WizardLayout(w: AppWidget, onSendPrompt: ((String) -> Unit)?) {
    val c = GsTheme.colors
    val steps = w.steps.take(10).filter { it.title.isNotBlank() || it.body.isNotBlank() || it.fields.isNotEmpty() }
    if (steps.isEmpty()) {
        Text("No steps", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        return
    }
    var index by remember { mutableStateOf(0) }
    val answers = remember { mutableStateMapOf<String, String>() }
    val step = steps[index.coerceIn(0, steps.lastIndex)]

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        steps.indices.forEach { i ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(3.dp))
                    .background(if (i <= index) c.accent else c.surface)
                    .padding(vertical = 2.dp)
            ) {}
        }
    }
    Text(
        "${index + 1}/${steps.size}",
        style = MaterialTheme.typography.labelSmall,
        color = c.textTertiary,
        modifier = Modifier.padding(top = 6.dp)
    )
    Text(
        step.title,
        style = MaterialTheme.typography.titleSmall,
        color = c.textPrimary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 2.dp)
    )
    if (step.body.isNotBlank()) {
        Text(
            step.body,
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    step.fields.take(6).forEachIndexed { fi, f ->
        val key = "$index:$fi"
        when (f.type) {
            "select", "radio", "segmented" -> {
                Text(
                    f.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    f.options.take(8).forEach { opt ->
                        val selected = answers[key] == opt
                        Box(
                            Modifier.clip(RoundedCornerShape(16.dp))
                                .background(if (selected) c.accent else c.surface)
                                .clickable { answers[key] = opt }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(opt, style = MaterialTheme.typography.labelSmall, color = if (selected) c.onAccent else c.textSecondary)
                        }
                    }
                }
            }
            "slider" -> {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(f.label, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                    Text(answers[key] ?: f.value.toInt().toString(), style = MaterialTheme.typography.labelSmall, color = c.textPrimary)
                }
                Slider(
                    value = (answers[key]?.toFloatOrNull() ?: f.value).coerceIn(f.min, f.max),
                    onValueChange = { answers[key] = it.toInt().toString() },
                    valueRange = f.min..f.max,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            else -> OutlinedTextField(
                value = answers[key] ?: "",
                onValueChange = { answers[key] = it },
                label = { Text(f.label) },
                singleLine = f.type != "textarea",
                minLines = if (f.type == "textarea") 3 else 1,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }

    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            "Back",
            style = MaterialTheme.typography.labelMedium,
            color = if (index == 0) c.textTertiary else c.textSecondary,
            modifier = Modifier.clickable { if (index > 0) index-- }.padding(vertical = 6.dp)
        )
        val last = index == steps.lastIndex
        Text(
            when {
                last && w.submitLabel.isNotBlank() -> w.submitLabel
                last -> "Done"
                else -> "Next"
            },
            style = MaterialTheme.typography.labelMedium,
            color = c.accent,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable {
                if (last) {
                    val summary = steps.mapIndexed { si, s ->
                        val vals = s.fields.indices.mapNotNull { fi ->
                            answers["$si:$fi"]?.takeIf { it.isNotBlank() }?.let { "${s.fields[fi].label}=$it" }
                        }
                        if (vals.isEmpty()) s.title else "${s.title}: ${vals.joinToString(", ")}"
                    }.filter { it.isNotBlank() }.joinToString("; ")
                    onSendPrompt?.invoke("${w.title.ifEmpty { "Wizard" }} — $summary")
                } else index++
            }.padding(vertical = 6.dp)
        )
    }
}

// --- kanban ------------------------------------------------------------------

@Composable
private fun KanbanLayout(w: AppWidget, onSendPrompt: ((String) -> Unit)?) {
    val c = GsTheme.colors
    val columns = w.columns.take(6).filter { it.title.isNotBlank() }
    val moved = remember { mutableStateMapOf<String, Int>() }
    if (columns.isEmpty()) {
        Text("No columns", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        return
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        columns.forEachIndexed { ci, col ->
            Column(
                Modifier.width(168.dp).clip(RoundedCornerShape(10.dp)).background(c.surface).padding(8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(col.title, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, fontWeight = FontWeight.SemiBold)
                    Text("${col.items.size}", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                }
                col.items.take(12).forEach { item ->
                    val at = moved[item.title] ?: ci
                    if (at == ci) {
                        Column(
                            Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(8.dp))
                                .background(c.elevatedSurface)
                                .clickable {
                                    moved[item.title] = (ci + 1).coerceAtMost(columns.lastIndex)
                                }
                                .padding(8.dp)
                        ) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (item.subtitle.isNotBlank()) {
                                Text(item.subtitle, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, maxLines = 1)
                            }
                            if (item.badge.isNotBlank()) {
                                Text(item.badge, style = MaterialTheme.typography.labelSmall, color = c.accent)
                            }
                        }
                    }
                }
            }
        }
    }
    Text(
        "Tap a card to move it forward",
        style = MaterialTheme.typography.labelSmall,
        color = c.textTertiary,
        modifier = Modifier.padding(top = 6.dp)
    )
    if (onSendPrompt != null) {
        Text(
            "Summarise the board",
            style = MaterialTheme.typography.labelMedium,
            color = c.accent,
            modifier = Modifier.padding(top = 6.dp).clickable {
                val state = columns.mapIndexed { ci, col ->
                    val here = col.items.filter { (moved[it.title] ?: ci) == ci }.joinToString(", ") { it.title }
                    "${col.title}: ${here.ifBlank { "—" }}"
                }.joinToString("; ")
                onSendPrompt("Board state — $state")
            }
        )
    }
}

// --- calendar ----------------------------------------------------------------

@Composable
private fun CalendarLayout(w: AppWidget, onSendPrompt: ((String) -> Unit)?) {
    val c = GsTheme.colors
    val start = runCatching {
        if (w.month.isNotBlank()) YearMonth.parse(w.month) else YearMonth.now()
    }.getOrElse { YearMonth.now() }
    var month by remember { mutableStateOf(start) }
    var selected by remember { mutableStateOf<String?>(null) }
    val events = w.items.filter { it.date.isNotBlank() }.groupBy { it.date }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("‹", style = MaterialTheme.typography.titleMedium, color = c.textSecondary,
            modifier = Modifier.clickable { month = month.minusMonths(1) }.padding(horizontal = 8.dp))
        Text("${month.month} ${month.year}", style = MaterialTheme.typography.labelLarge, color = c.textPrimary)
        Text("›", style = MaterialTheme.typography.titleMedium, color = c.textSecondary,
            modifier = Modifier.clickable { month = month.plusMonths(1) }.padding(horizontal = 8.dp))
    }
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    var day = 1
    for (row in 0..5) {
        if (day > days) break
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (col in 0..6) {
                val cell = row * 7 + col
                if (cell < offset || day > days) {
                    Box(Modifier.weight(1f)) {}
                } else {
                    val d = day
                    val iso = month.atDay(d).toString()
                    val has = events[iso].orEmpty()
                    val isSelected = selected == iso
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) c.accent else Color.Transparent)
                            .clickable { selected = if (isSelected) null else iso }
                            .padding(vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "$d",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) c.onAccent else c.textPrimary
                        )
                        if (has.isNotEmpty()) {
                            Box(
                                Modifier.padding(top = 2.dp).clip(RoundedCornerShape(2.dp))
                                    .background(if (isSelected) c.onAccent else c.accent)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text("${has.size}", style = MaterialTheme.typography.labelSmall, color = if (isSelected) c.accent else c.onAccent)
                            }
                        }
                    }
                    day++
                }
            }
        }
    }
    val shown = selected?.let { events[it].orEmpty() }.orEmpty()
    if (shown.isNotEmpty()) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            shown.take(6).forEach { e ->
                Row(Modifier.fillMaxWidth().clickable {
                    onSendPrompt?.invoke(e.prompt.ifBlank { e.title })
                }, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(e.title, style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
                    if (e.meta.isNotBlank()) {
                        Text(e.meta, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                    }
                }
            }
        }
    }
}

// --- spreadsheet -------------------------------------------------------------

@Composable
private fun SheetLayout(w: AppWidget) {
    val c = GsTheme.colors
    val rows = w.rows.take(30).filter { it.isNotEmpty() }
    if (rows.isEmpty()) {
        Text("No rows", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        return
    }
    val edits = remember { mutableStateMapOf<Pair<Int, Int>, String>() }
    val header = rows.first()
    val body = rows.drop(1)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column {
            Row {
                header.take(6).forEach { h ->
                    Box(Modifier.width(96.dp).background(c.surface).padding(6.dp)) {
                        Text(h, style = MaterialTheme.typography.labelSmall, color = c.textSecondary, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
            body.forEachIndexed { ri, row ->
                Row {
                    row.take(6).forEachIndexed { ci, cell ->
                        val key = ri to ci
                        OutlinedTextField(
                            value = edits[key] ?: cell,
                            onValueChange = { edits[key] = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(96.dp).padding(1.dp)
                        )
                    }
                }
            }
        }
    }
    val numericCols = header.indices.filter { ci ->
        body.any { it.getOrNull(ci)?.toDoubleOrNull() != null }
    }
    if (numericCols.isNotEmpty()) {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            numericCols.forEach { ci ->
                var sum = 0.0
                body.forEachIndexed { ri, r ->
                    val raw = edits[ri to ci] ?: r.getOrNull(ci).orEmpty()
                    sum += raw.toDoubleOrNull() ?: 0.0
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Σ ${header.getOrNull(ci).orEmpty()}", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                    Text(formatCalc(sum, "number", ""), style = MaterialTheme.typography.labelSmall, color = c.textPrimary)
                }
            }
        }
    }
    Text("Tap any cell to edit", style = MaterialTheme.typography.labelSmall, color = c.textTertiary, modifier = Modifier.padding(top = 6.dp))
}

// --- tasks -------------------------------------------------------------------

@Composable
private fun TasksLayout(w: AppWidget, onSendPrompt: ((String) -> Unit)?) {
    val c = GsTheme.colors
    val items = w.items.take(20).filter { it.title.isNotBlank() }
    val done = remember { mutableStateMapOf<String, Boolean>() }
    if (items.isEmpty()) {
        Text("No tasks", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        return
    }
    items.forEach { item ->
        val checked = done[item.title] == true
        Row(
            Modifier.fillMaxWidth().clickable { done[item.title] = !checked }.padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = null)
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (checked) c.textTertiary else c.textPrimary
                )
                if (item.subtitle.isNotBlank()) {
                    Text(item.subtitle, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, maxLines = 1)
                }
            }
            if (item.badge.isNotBlank()) {
                Text(item.badge, style = MaterialTheme.typography.labelSmall, color = c.accent)
            }
        }
    }
    val open = items.count { done[it.title] != true }
    Text("$open of ${items.size} open", style = MaterialTheme.typography.labelSmall, color = c.textTertiary, modifier = Modifier.padding(top = 6.dp))
    if (onSendPrompt != null) {
        Text("Ask about progress", style = MaterialTheme.typography.labelMedium, color = c.accent,
            modifier = Modifier.padding(top = 6.dp).clickable {
                val state = items.joinToString(", ") { "${it.title}=${if (done[it.title] == true) "done" else "open"}" }
                onSendPrompt("Task progress — $state")
            })
    }
}

// --- dashboard ---------------------------------------------------------------

@Composable
private fun DashboardLayout(w: AppWidget, onSendPrompt: ((String) -> Unit)?) {
    val c = GsTheme.colors
    val items = w.items.take(8).filter { it.title.isNotBlank() }
    if (items.isEmpty()) {
        Text("No metrics", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        return
    }
    items.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { item ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(c.surface).padding(10.dp)
                ) {
                    Text(item.title, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, maxLines = 1)
                    Text(
                        item.meta.ifBlank { "—" },
                        style = MaterialTheme.typography.titleMedium,
                        color = c.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (item.badge.isNotBlank()) {
                        val up = item.badge.trim().startsWith("+") || item.badge.contains("↑")
                        Text(
                            item.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (up) c.success else c.error
                        )
                    }
                }
            }
            if (pair.size == 1) Box(Modifier.weight(1f)) {}
        }
    }
    if (onSendPrompt != null) {
        Text("Analyse this", style = MaterialTheme.typography.labelMedium, color = c.accent,
            modifier = Modifier.padding(top = 8.dp).clickable {
                onSendPrompt(w.title.ifEmpty { "Dashboard" } + " — " + items.joinToString(", ") { "${it.title}: ${it.meta}" })
            })
    }
}

// --- configurator ------------------------------------------------------------

@Composable
private fun ConfiguratorLayout(w: AppWidget, onSendPrompt: ((String) -> Unit)?) {
    val c = GsTheme.colors
    val groups = w.groups.take(6).filter { it.options.isNotEmpty() }
    if (groups.isEmpty()) {
        Text("No options", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        return
    }
    val picked = remember { mutableStateMapOf<Int, Int>() }
    var total = 0.0
    groups.forEachIndexed { gi, g ->
        Text(g.title, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
        g.options.take(5).forEachIndexed { oi, opt ->
            val selected = (picked[gi] ?: 0) == oi
            if (selected) total += opt.price
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (selected) c.accent.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { picked[gi] = oi }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(opt.label, style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
                    if (opt.detail.isNotBlank()) {
                        Text(opt.detail, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, maxLines = 2)
                    }
                }
                if (opt.price != 0.0) {
                    Text(formatCalc(opt.price, "money", ""), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Total", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
        Text(formatCalc(total, "money", ""), style = MaterialTheme.typography.titleSmall, color = c.accent, fontWeight = FontWeight.SemiBold)
    }
    if (onSendPrompt != null) {
        Text(
            w.submitLabel.ifBlank { "Confirm" },
            style = MaterialTheme.typography.labelMedium,
            color = c.accent,
            modifier = Modifier.padding(top = 8.dp).clickable {
                val picks = groups.mapIndexed { gi, g -> "${g.title}=${g.options.getOrNull(picked[gi] ?: 0)?.label.orEmpty()}" }
                onSendPrompt("${w.title.ifEmpty { "Configuration" }}: ${picks.joinToString(", ")}")
            }
        )
    }
}

// --- booking -----------------------------------------------------------------

@Composable
private fun BookingLayout(w: AppWidget, onSendPrompt: ((String) -> Unit)?) {
    val c = GsTheme.colors
    val slots = w.slots.take(16).filter { it.isNotBlank() }
    val days = w.items.take(14).filter { it.title.isNotBlank() }
    var day by remember { mutableStateOf(days.firstOrNull()?.title.orEmpty()) }
    var slot by remember { mutableStateOf("") }
    if (days.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach { d ->
                val selected = day == d.title
                Column(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (selected) c.accent else c.surface)
                        .clickable { day = d.title }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(d.title, style = MaterialTheme.typography.labelMedium, color = if (selected) c.onAccent else c.textPrimary)
                    if (d.subtitle.isNotBlank()) {
                        Text(d.subtitle, style = MaterialTheme.typography.labelSmall, color = if (selected) c.onAccent else c.textTertiary)
                    }
                }
            }
        }
    }
    if (slots.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            slots.forEach { s ->
                val selected = slot == s
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (selected) c.accent else c.surface)
                        .clickable { slot = s }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(s, style = MaterialTheme.typography.labelSmall, color = if (selected) c.onAccent else c.textSecondary)
                }
            }
        }
    }
    val ready = day.isNotBlank() && (slots.isEmpty() || slot.isNotBlank())
    Text(
        w.submitLabel.ifBlank { "Confirm booking" },
        style = MaterialTheme.typography.labelMedium,
        color = if (ready) c.accent else c.textTertiary,
        modifier = Modifier.padding(top = 10.dp).clickable {
            if (ready) onSendPrompt?.invoke("Book ${w.title.ifEmpty { "appointment" }}: $day${if (slot.isBlank()) "" else " at $slot"}")
        }
    )
}

// --- search ------------------------------------------------------------------

@Composable
private fun SearchLayout(w: AppWidget, onSendPrompt: ((String) -> Unit)?) {
    val c = GsTheme.colors
    var query by remember { mutableStateOf("") }
    val items = w.items.take(40).filter { it.title.isNotBlank() }
    val hits = if (query.isBlank()) items else items.filter {
        it.title.contains(query, true) || it.subtitle.contains(query, true) || it.meta.contains(query, true)
    }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("Search") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Text("${hits.size} result${if (hits.size == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = c.textTertiary, modifier = Modifier.padding(top = 6.dp))
    hits.take(12).forEach { item ->
        Column(
            Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).background(c.surface)
                .clickable { onSendPrompt?.invoke(item.prompt.ifBlank { item.title }) }
                .padding(8.dp)
        ) {
            Text(item.title, style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
            if (item.subtitle.isNotBlank()) {
                Text(item.subtitle, style = MaterialTheme.typography.labelSmall, color = c.textSecondary, maxLines = 2)
            }
            if (item.meta.isNotBlank()) {
                Text(item.meta, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, maxLines = 1)
            }
        }
    }
}

// --- filter ------------------------------------------------------------------

@Composable
private fun FilterLayout(w: AppWidget, onSendPrompt: ((String) -> Unit)?) {
    val c = GsTheme.colors
    val facets = w.columns.take(5).filter { it.items.isNotEmpty() }
    val picked = remember { mutableStateMapOf<String, Boolean>() }
    val active = picked.filterValues { it }.keys
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        facets.forEach { facet ->
            facet.items.take(6).forEach { opt ->
                val on = picked[opt.title] == true
                Box(
                    Modifier.clip(RoundedCornerShape(16.dp))
                        .background(if (on) c.accent else c.surface)
                        .clickable { picked[opt.title] = !on }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(opt.title, style = MaterialTheme.typography.labelSmall, color = if (on) c.onAccent else c.textSecondary)
                }
            }
        }
    }
    val items = w.items.take(40).filter { it.title.isNotBlank() }
    val hits = if (active.isEmpty()) items else items.filter { it.badge in active || it.meta in active }
    Text("${hits.size} shown", style = MaterialTheme.typography.labelSmall, color = c.textTertiary, modifier = Modifier.padding(top = 6.dp))
    hits.take(12).forEach { item ->
        Row(
            Modifier.fillMaxWidth().padding(top = 5.dp).clickable { onSendPrompt?.invoke(item.prompt.ifBlank { item.title }) },
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(item.title, style = MaterialTheme.typography.bodySmall, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
            if (item.badge.isNotBlank()) {
                Text(item.badge, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
            }
        }
    }
}

// --- generic list ------------------------------------------------------------

@Composable
private fun ListLayout(w: AppWidget, onSendPrompt: ((String) -> Unit)?) {
    val c = GsTheme.colors
    val items = w.items.take(20).filter { it.title.isNotBlank() }
    if (items.isEmpty()) {
        Text("Nothing to show", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        return
    }
    items.forEach { item ->
        Column(
            Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).background(c.surface)
                .clickable { onSendPrompt?.invoke(item.prompt.ifBlank { item.title }) }
                .padding(8.dp)
        ) {
            Text(item.title, style = MaterialTheme.typography.bodySmall, color = c.textPrimary, maxLines = 1)
            if (item.subtitle.isNotBlank()) {
                Text(item.subtitle, style = MaterialTheme.typography.labelSmall, color = c.textSecondary, maxLines = 2)
            }
            if (item.meta.isNotBlank()) {
                Text(item.meta, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, maxLines = 1)
            }
        }
    }
}
