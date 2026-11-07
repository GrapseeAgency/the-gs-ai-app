package com.grapsee.gsai.ui.chat.content

// ---------------------------------------------------------------------------
// V2 Interactive & Layout Widgets (tabs, accordion, steps, menu, chips,
// rating, poll, diff, tree, flashcards, checklist) + Client-Side Lenient JSON
// Pre-Normalizer for Android Compose parity across all 36 WIDGET_KINDS.
// ---------------------------------------------------------------------------

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.ui.theme.GsTheme
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

// --- models -----------------------------------------------------------------

@Serializable
data class TabPane(
    val label: String = "",
    val body: String = "",
    val items: List<String> = emptyList()
)

@Serializable
data class TabsWidget(
    val title: String = "",
    val tabs: List<TabPane> = emptyList()
)

@Serializable
data class AccordionSection(
    val label: String = "",
    val body: String = "",
    val expanded: Boolean = false
)

@Serializable
data class AccordionWidget(
    val title: String = "",
    val sections: List<AccordionSection> = emptyList()
)

@Serializable
data class StepEntry(
    val title: String = "",
    val body: String = "",
    /** done | current | todo */
    val state: String = "todo"
)

@Serializable
data class StepsWidget(
    val title: String = "",
    val steps: List<StepEntry> = emptyList()
)

@Serializable
data class MenuOption(
    val label: String = "",
    val detail: String = "",
    val prompt: String = ""
)

@Serializable
data class MenuWidget(
    val title: String = "",
    val options: List<MenuOption> = emptyList()
)

@Serializable
data class ChipOption(
    val label: String = "",
    val prompt: String = ""
)

@Serializable
data class ChipsWidget(
    val title: String = "",
    val multi: Boolean = false,
    val options: List<ChipOption> = emptyList()
)

@Serializable
data class RatingWidget(
    val title: String = "",
    val prompt: String = "",
    /** star | heart | thumb | nps */
    val style: String = "star",
    val max: Int = 5,
    val labels: List<String> = emptyList()
)

@Serializable
data class PollOption(
    val label: String = "",
    val votes: Int = 0
)

@Serializable
data class PollWidget(
    val title: String = "",
    val question: String = "",
    val options: List<PollOption> = emptyList()
)

@Serializable
data class DiffWidget(
    val title: String = "",
    val language: String = "",
    val before: String = "",
    val after: String = "",
    val unified: String = ""
)

@Serializable
data class HierarchyNode(
    val label: String = "",
    val detail: String = "",
    val children: List<HierarchyNode> = emptyList()
)

@Serializable
data class TreeWidget(
    val title: String = "",
    val roots: List<HierarchyNode> = emptyList()
)

@Serializable
data class FlashcardItem(
    val front: String = "",
    val back: String = ""
)

@Serializable
data class FlashcardsWidget(
    val title: String = "",
    val cards: List<FlashcardItem> = emptyList()
)

@Serializable
data class ChecklistItem(
    val label: String = "",
    val detail: String = "",
    val done: Boolean = false
)

@Serializable
data class ChecklistWidget(
    val title: String = "",
    val items: List<ChecklistItem> = emptyList()
)

// --- Views ------------------------------------------------------------------

@Composable
internal fun TabsWidgetView(w: TabsWidget) {
    val c = GsTheme.colors
    var selected by remember { mutableIntStateOf(0) }
    val safeIndex = selected.coerceIn(0, (w.tabs.size - 1).coerceAtLeast(0))
    val active = w.tabs.getOrNull(safeIndex)
    WidgetCardShell(w.title.ifEmpty { "Tabs" }, "tabs") {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                w.tabs.forEachIndexed { idx, tab ->
                    val isSelected = idx == safeIndex
                    val accent = seriesColor(0)
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (isSelected) accent.copy(alpha = 0.20f) else c.raisedSurface)
                            .border(
                                1.dp,
                                if (isSelected) accent else c.border.copy(alpha = 0.45f),
                                RoundedCornerShape(99.dp)
                            )
                            .clickable { selected = idx }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) accent else c.textSecondary,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
            if (active != null) {
                if (active.body.isNotBlank()) {
                    Text(active.body, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                }
                if (active.items.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        active.items.forEach { item ->
                            Text("• $item", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AccordionWidgetView(w: AccordionWidget) {
    val c = GsTheme.colors
    val openMap = remember(w.sections) {
        mutableStateMapOf<Int, Boolean>().apply {
            w.sections.forEachIndexed { idx, s -> put(idx, s.expanded || idx == 0) }
        }
    }
    WidgetCardShell(w.title.ifEmpty { "Sections" }, "accordion") {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            w.sections.forEachIndexed { idx, section ->
                val isOpen = openMap[idx] == true
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.raisedSurface)
                        .border(1.dp, c.border.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .clickable { openMap[idx] = !isOpen }
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            section.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = c.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            if (isOpen) "−" else "+",
                            style = MaterialTheme.typography.titleMedium,
                            color = seriesColor(0)
                        )
                    }
                    if (isOpen && section.body.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(section.body, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
internal fun StepsWidgetView(w: StepsWidget) {
    val c = GsTheme.colors
    val accent = seriesColor(0)
    val doneMap = remember(w.steps) {
        mutableStateMapOf<Int, Boolean>().apply {
            w.steps.forEachIndexed { idx, s -> put(idx, s.state == "done") }
        }
    }
    val doneCount = w.steps.indices.count { doneMap[it] == true }
    val total = w.steps.size.coerceAtLeast(1)
    val currentIdx = w.steps.indices.firstOrNull { doneMap[it] != true } ?: -1
    WidgetCardShell(NexlyIcons.cleanPlainText(w.title).ifEmpty { "Steps" }, "steps") {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$doneCount of ${w.steps.size} steps completed", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                Text("${((doneCount.toFloat() / total.toFloat()) * 100f).toInt()}%", style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { (doneCount.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(99.dp)),
                color = accent,
                trackColor = c.border.copy(alpha = 0.25f)
            )
            w.steps.forEachIndexed { idx, step ->
                val isDone = doneMap[idx] == true
                val isCurrent = idx == currentIdx
                val badgeColor = when {
                    isDone -> Color(0xFF0CA678)
                    isCurrent -> accent
                    else -> c.textTertiary
                }
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isCurrent) accent.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable { doneMap[idx] = !isDone }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                ) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(badgeColor.copy(alpha = 0.18f))
                            .border(1.dp, badgeColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDone) {
                            NexlyGlyph(name = "check", size = 12.sp, color = badgeColor)
                        } else {
                            Text(
                                "${idx + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            NexlyIcons.cleanPlainText(step.title),
                            style = MaterialTheme.typography.labelLarge.copy(
                                textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            color = if (isDone) c.textSecondary else c.textPrimary,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold
                        )
                        if (step.body.isNotBlank()) {
                            Text(NexlyIcons.cleanPlainText(step.body), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MenuWidgetView(w: MenuWidget, onSendPrompt: ((String) -> Unit)?) {
    val c = GsTheme.colors
    val accent = seriesColor(0)
    WidgetCardShell(w.title.ifEmpty { "Options" }, "menu") {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            w.options.forEach { opt ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.raisedSurface)
                        .border(1.dp, c.border.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .clickable { onSendPrompt?.invoke(opt.prompt.ifBlank { opt.label }) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(opt.label, style = MaterialTheme.typography.labelLarge, color = c.textPrimary)
                        if (opt.detail.isNotBlank()) {
                            Text(opt.detail, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                    }
                    Text("→", style = MaterialTheme.typography.labelLarge, color = accent)
                }
            }
        }
    }
}

@Composable
internal fun ChipsWidgetView(w: ChipsWidget, onSendPrompt: ((String) -> Unit)?) {
    val c = GsTheme.colors
    val accent = seriesColor(0)
    val selected = remember { mutableStateMapOf<Int, Boolean>() }
    WidgetCardShell(w.title.ifEmpty { "Filters" }, "chips") {
        Row(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            w.options.forEachIndexed { idx, chip ->
                val active = selected[idx] == true
                Box(
                    Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (active) accent.copy(alpha = 0.22f) else c.raisedSurface)
                        .border(1.dp, if (active) accent else c.border.copy(alpha = 0.5f), RoundedCornerShape(99.dp))
                        .clickable {
                            if (!w.multi) selected.clear()
                            selected[idx] = !active
                            onSendPrompt?.invoke(chip.prompt.ifBlank { chip.label })
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        chip.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) accent else c.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
internal fun RatingWidgetView(w: RatingWidget, onSendPrompt: ((String) -> Unit)?) {
    val c = GsTheme.colors
    val accent = seriesColor(0)
    var chosen by remember { mutableIntStateOf(-1) }
    val count = if (w.style == "nps") 11 else if (w.style == "thumb") 2 else w.max.coerceIn(2, 10)
    WidgetCardShell(w.title.ifEmpty { "Rating" }, "rating") {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in 0 until count) {
                    val value = if (w.style == "nps") i else i + 1
                    val isSelected = if (w.style == "nps" || w.style == "thumb") chosen == value else chosen >= value
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) accent.copy(alpha = 0.20f) else c.raisedSurface)
                            .border(1.dp, if (isSelected) accent else c.border.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .clickable {
                                chosen = value
                                val msg = if (w.prompt.isNotBlank()) "${w.prompt}: $value" else "Rated $value"
                                onSendPrompt?.invoke(msg)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (w.style) {
                            "heart" -> NexlyGlyph(
                                name = "heart-filled",
                                size = 18.dp,
                                color = if (isSelected) accent else c.textTertiary.copy(alpha = 0.45f)
                            )
                            "thumb" -> Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                NexlyGlyph(
                                    name = if (i == 0) "thumb-up" else "thumb-down",
                                    size = 15.dp,
                                    color = if (isSelected) accent else c.textPrimary
                                )
                                Text(
                                    text = if (i == 0) "Yes" else "No",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isSelected) accent else c.textPrimary
                                )
                            }
                            "nps" -> Text(
                                text = "$value",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSelected) accent else c.textPrimary
                            )
                            else -> NexlyGlyph(
                                name = if (isSelected) "star-filled" else "star",
                                size = 18.dp,
                                color = if (isSelected) accent else c.textTertiary.copy(alpha = 0.55f)
                            )
                        }
                    }
                }
            }
            if (w.labels.size >= 2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(w.labels.first(), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                    Text(w.labels.last(), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                }
            }
        }
    }
}

@Composable
internal fun PollWidgetView(w: PollWidget) {
    val c = GsTheme.colors
    val accent = seriesColor(0)
    var votedIdx by remember { mutableIntStateOf(-1) }
    val baseTotal = w.options.sumOf { it.votes.coerceAtLeast(0) }
    val total = (baseTotal + if (votedIdx >= 0) 1 else 0).coerceAtLeast(1)
    WidgetCardShell(w.title.ifEmpty { "Poll" }, "poll") {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (w.question.isNotBlank()) {
                Text(w.question, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, fontWeight = FontWeight.Medium)
            }
            w.options.forEachIndexed { idx, opt ->
                val votes = opt.votes.coerceAtLeast(0) + if (votedIdx == idx) 1 else 0
                val pct = ((votes.toFloat() / total.toFloat()) * 100f).toInt().coerceIn(0, 100)
                val isSelected = votedIdx == idx
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.raisedSurface)
                        .border(1.dp, if (isSelected) accent else c.border.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .clickable { votedIdx = idx }
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(opt.label, style = MaterialTheme.typography.labelMedium, color = c.textPrimary)
                        Text("$pct% ($votes)", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                    }
                    LinearProgressIndicator(
                        progress = { (votes.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(99.dp)),
                        color = accent,
                        trackColor = c.border.copy(alpha = 0.25f)
                    )
                }
            }
        }
    }
}

@Composable
internal fun DiffWidgetView(w: DiffWidget) {
    val c = GsTheme.colors
    val addBg = Color(0xFF0CA678).copy(alpha = 0.16f)
    val delBg = Color(0xFFE03131).copy(alpha = 0.16f)
    val lines = remember(w.unified, w.before, w.after) {
        if (w.unified.isNotBlank()) {
            w.unified.lines().take(200)
        } else {
            val beforeLines = w.before.lines().map { "- $it" }
            val afterLines = w.after.lines().map { "+ $it" }
            (beforeLines + afterLines).take(200)
        }
    }
    WidgetCardShell(w.title.ifEmpty { "Diff" }, "diff") {
        Column(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(c.raisedSurface)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            lines.forEach { line ->
                val bg = when {
                    line.startsWith("+") && !line.startsWith("+++") -> addBg
                    line.startsWith("-") && !line.startsWith("---") -> delBg
                    else -> Color.Transparent
                }
                val fg = when {
                    line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF0CA678)
                    line.startsWith("-") && !line.startsWith("---") -> Color(0xFFE03131)
                    else -> c.textPrimary
                }
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = fg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bg)
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
internal fun TreeWidgetView(w: TreeWidget) {
    WidgetCardShell(w.title.ifEmpty { "Hierarchy" }, "tree") {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            w.roots.forEach { node ->
                TreeNodeRow(node, depth = 0)
            }
        }
    }
}

@Composable
private fun TreeNodeRow(node: HierarchyNode, depth: Int) {
    val c = GsTheme.colors
    var expanded by remember { mutableStateOf(depth < 2) }
    val hasKids = node.children.isNotEmpty()
    Column(Modifier.padding(start = (depth * 14).dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = hasKids) { expanded = !expanded }
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (!hasKids) "• " else if (expanded) "▾ " else "▸ ",
                style = MaterialTheme.typography.labelMedium,
                color = seriesColor(0)
            )
            Text(
                node.label,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textPrimary,
                fontWeight = if (hasKids) FontWeight.SemiBold else FontWeight.Normal
            )
            if (node.detail.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(node.detail, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
            }
        }
        if (hasKids && expanded) {
            node.children.forEach { child ->
                TreeNodeRow(child, depth = depth + 1)
            }
        }
    }
}

@Composable
internal fun FlashcardsWidgetView(w: FlashcardsWidget) {
    val c = GsTheme.colors
    val accent = seriesColor(0)
    var index by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    val safeIndex = index.coerceIn(0, (w.cards.size - 1).coerceAtLeast(0))
    val card = w.cards.getOrNull(safeIndex)
    WidgetCardShell(w.title.ifEmpty { "Flashcards" }, "flashcards") {
        if (card != null) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.raisedSurface)
                        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .clickable { flipped = !flipped }
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (flipped) "BACK" else "FRONT",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (flipped) card.back else card.front,
                            style = MaterialTheme.typography.bodyLarge,
                            color = c.textPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        enabled = safeIndex > 0,
                        onClick = { index = (safeIndex - 1).coerceAtLeast(0); flipped = false }
                    ) { Text("Prev") }
                    Text(
                        "${safeIndex + 1} / ${w.cards.size} (tap card to flip)",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary
                    )
                    TextButton(
                        enabled = safeIndex < w.cards.size - 1,
                        onClick = { index = (safeIndex + 1).coerceAtMost(w.cards.size - 1); flipped = false }
                    ) { Text("Next") }
                }
            }
        }
    }
}

@Composable
internal fun ChecklistWidgetView(w: ChecklistWidget) {
    val c = GsTheme.colors
    val accent = seriesColor(0)
    var addedTasks by remember(w.items) { mutableStateOf(listOf<ChecklistItem>()) }
    var addingTask by remember { mutableStateOf(false) }
    var newTaskInput by remember { mutableStateOf("") }
    val allItems = remember(w.items, addedTasks) { w.items + addedTasks }
    val checked = remember(w.items) {
        mutableStateMapOf<Int, Boolean>().apply {
            w.items.forEachIndexed { idx, item -> put(idx, item.done) }
        }
    }
    val doneCount = allItems.indices.count { checked[it] == true }
    val total = allItems.size.coerceAtLeast(1)
    WidgetCardShell(NexlyIcons.cleanPlainText(w.title).ifEmpty { "Your Todo List" }, "checklist") {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$doneCount of ${allItems.size} completed", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                Text("${((doneCount.toFloat() / total.toFloat()) * 100f).toInt()}%", style = MaterialTheme.typography.labelSmall, color = accent)
            }
            LinearProgressIndicator(
                progress = { (doneCount.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(99.dp)),
                color = accent,
                trackColor = c.border.copy(alpha = 0.25f)
            )
            allItems.forEachIndexed { idx, item ->
                val isDone = checked[idx] == true
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { checked[idx] = !isDone }
                        .padding(vertical = 5.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isDone) Color(0xFF6C4CF6) else Color.White.copy(alpha = 0.85f))
                            .then(
                                if (!isDone) Modifier.border(1.5.dp, Color(0xFF9C88F8), RoundedCornerShape(5.dp))
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDone) {
                            NexlyGlyph(name = "check", size = 11.sp, color = Color.White)
                        }
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            NexlyIcons.cleanPlainText(item.label),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            color = if (isDone) c.textTertiary else c.textPrimary
                        )
                        if (item.detail.isNotBlank()) {
                            Text(NexlyIcons.cleanPlainText(item.detail), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                    }
                    if (idx >= w.items.size) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    val customIdx = idx - w.items.size
                                    addedTasks = addedTasks.filterIndexed { i, _ -> i != customIdx }
                                    checked.remove(idx)
                                }
                                .padding(4.dp)
                        ) {
                            NexlyGlyph(name = "x", size = 12.sp, color = c.textTertiary)
                        }
                    }
                }
            }
            if (addingTask) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.92f))
                            .border(1.dp, Color(0xFFD8CEFA), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        if (newTaskInput.isEmpty()) {
                            Text("Type a new task…", fontSize = 12.sp, color = Color(0xFF958BB8))
                        }
                        BasicTextField(
                            value = newTaskInput,
                            onValueChange = { newTaskInput = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 12.5.sp, color = Color(0xFF1F1638)),
                            cursorBrush = SolidColor(Color(0xFF6C4CF6)),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF6C4CF6))
                            .clickable {
                                val trimmed = newTaskInput.trim()
                                if (trimmed.isNotEmpty()) {
                                    val nextIdx = allItems.size
                                    addedTasks = addedTasks + ChecklistItem(label = trimmed, done = false)
                                    checked[nextIdx] = false
                                    newTaskInput = ""
                                    addingTask = false
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Add", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                newTaskInput = ""
                                addingTask = false
                            }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        NexlyGlyph(name = "x", size = 13.sp, color = c.textSecondary)
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.8f))
                        .border(1.dp, Color(0xFFE0D8FA), RoundedCornerShape(999.dp))
                        .clickable { addingTask = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    NexlyGlyph(name = "plus", size = 11.sp, color = Color(0xFF5B3DF5))
                    Text(
                        text = "Add Task",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF5B3DF5)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Client-side JSON pre-normalizer — brings the Kotlin decoder to full synonym
// and dialect parity with src/lib/widgets/validate.ts even on uncompiled or
// mid-stream payloads.
// ---------------------------------------------------------------------------

internal fun normalizeWidgetJson(kind: String, rawJson: String): String {
    val trimmed = rawJson.trim()
    if (trimmed.isEmpty()) return rawJson

    // 1. Pre-clean Python/JS literals (`True`/`False`/`None`) outside strings.
    val dialectCleaned = trimmed
        .replace(Regex("(?<=[:\\[\\s,])True(?=[,\\]\\}\\s]|$)"), "true")
        .replace(Regex("(?<=[:\\[\\s,])False(?=[,\\]\\}\\s]|$)"), "false")
        .replace(Regex("(?<=[:\\[\\s,])None(?=[,\\]\\}\\s]|$)"), "null")

    val element = try {
        widgetJson.parseToJsonElement(dialectCleaned)
    } catch (_: Throwable) {
        return dialectCleaned
    }

    // 2. Wrap top-level JSON arrays (`[{...}]`) into an object with canonical keys.
    val baseObj: JsonObject = when (element) {
        is JsonArray -> JsonObject(
            mapOf(
                "items" to element,
                "cards" to element,
                "rows" to element,
                "entries" to element,
                "questions" to element,
                "fields" to element,
                "prompts" to element,
                "buttons" to element,
                "tabs" to element,
                "sections" to element,
                "steps" to element,
                "options" to element,
                "roots" to element,
                "series" to element
            )
        )
        is JsonObject -> {
            // Unwrap single-key envelopes (`{"chart":{...}}`, `{"widget":{...}}`, `{"data":{...}}`).
            val envKeys = listOf("widget", "payload", "data", "content", "result", "spec", "config", kind)
            val innerKey = envKeys.firstOrNull { k -> element[k] is JsonObject }
            if (innerKey != null) {
                val inner = element[innerKey] as JsonObject
                val merged = element.toMutableMap()
                merged.remove(innerKey)
                for ((k, v) in inner) merged[k] = v
                JsonObject(merged)
            } else {
                element
            }
        }
        else -> return dialectCleaned
    }

    val m = baseObj.toMutableMap()

    when (kind) {
        "chart" -> {
            val rawType = (m["type"] as? JsonPrimitive)?.contentOrNull ?: (m["chartType"] as? JsonPrimitive)?.contentOrNull
            if (rawType != null) m["type"] = JsonPrimitive(canonicalChartType(rawType))
            if ("labels" !in m) {
                (m["categories"] as? JsonArray)?.let { m["labels"] = it }
            }
            if ("series" !in m) {
                val datasets = m["datasets"] as? JsonArray
                if (datasets != null) {
                    val converted = datasets.mapNotNull { ds ->
                        val o = ds as? JsonObject ?: return@mapNotNull null
                        val name = (o["name"] ?: o["label"] ?: o["title"]) as? JsonPrimitive ?: JsonPrimitive("Series")
                        val data = (o["data"] ?: o["values"] ?: o["points"]) as? JsonArray ?: return@mapNotNull null
                        JsonObject(mapOf("name" to name, "data" to data))
                    }
                    m["series"] = JsonArray(converted)
                } else {
                    // A contour's `points` are [x, y, z] field samples, not a flat
                    // value list — synthesizing a "Value" series from them returned
                    // a chart for payloads the contour branch must reject (the
                    // nine-point minimum never saw them). Structural contour
                    // payloads carry no series, and the alias must not mint one.
                    val typeNow = (m["type"] as? JsonPrimitive)?.contentOrNull
                    val flat = if (typeNow == "contour") null
                        else (m["data"] ?: m["values"] ?: m["points"] ?: m["slices"]) as? JsonArray
                    if (flat != null) {
                        m["series"] = JsonArray(listOf(JsonObject(mapOf("name" to JsonPrimitive("Value"), "data" to flat))))
                    }
                }
            }
            // Expand single-series pie/donut with multiple points into one series per slice.
            val chartType = (m["type"] as? JsonPrimitive)?.contentOrNull ?: "bar"
            if (chartType == "pie" || chartType == "donut") {
                val seriesArr = m["series"] as? JsonArray
                val labelsArr = m["labels"] as? JsonArray
                if (seriesArr != null && seriesArr.size == 1) {
                    val firstSeries = seriesArr[0] as? JsonObject
                    val dataArr = firstSeries?.get("data") as? JsonArray
                    if (dataArr != null && dataArr.size > 1) {
                        val expanded = dataArr.mapIndexedNotNull { idx, pt ->
                            val sliceName = (labelsArr?.getOrNull(idx) as? JsonPrimitive)?.contentOrNull
                                ?: (pt as? JsonObject)?.let { (it["name"] ?: it["label"]) as? JsonPrimitive }?.contentOrNull
                                ?: "Slice ${idx + 1}"
                            val valueElem: JsonElement = when (pt) {
                                is JsonPrimitive -> pt
                                is JsonArray -> pt.lastOrNull() ?: JsonPrimitive(0)
                                is JsonObject -> (pt["y"] ?: pt["value"] ?: pt["val"]) ?: JsonPrimitive(0)
                            }
                            JsonObject(
                                mapOf(
                                    "name" to JsonPrimitive(sliceName),
                                    "data" to JsonArray(listOf(JsonArray(listOf(JsonPrimitive(0), valueElem))))
                                )
                            )
                        }
                        if (expanded.isNotEmpty()) m["series"] = JsonArray(expanded)
                    }
                }
            }
        }
        "stat" -> {
            val rawCards = (m["cards"] ?: m["stats"] ?: m["metrics"] ?: m["kpis"] ?: m["items"]) as? JsonArray
            if (rawCards != null) {
                val normalized = rawCards.mapNotNull { item ->
                    val o = item as? JsonObject ?: return@mapNotNull null
                    val cm = o.toMutableMap()
                    if ("label" !in cm) (o["name"] ?: o["title"] ?: o["metric"] ?: o["key"])?.let { cm["label"] = it }
                    if ("delta" !in cm) (o["change"] ?: o["diff"] ?: o["trend"])?.let { cm["delta"] = it }
                    val deltaStr = (cm["delta"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                    if ("direction" !in cm && deltaStr.isNotEmpty()) {
                        cm["direction"] = JsonPrimitive(
                            when {
                                deltaStr.startsWith("+") || deltaStr.startsWith("▲") -> "up"
                                deltaStr.startsWith("-") || deltaStr.startsWith("▼") -> "down"
                                else -> "flat"
                            }
                        )
                    }
                    JsonObject(cm)
                }
                m["cards"] = JsonArray(normalized)
            }
        }
        "table" -> {
            if ("columns" !in m) {
                (m["headers"] ?: m["fields"] ?: m["cols"])?.let { m["columns"] = it }
            }
            val rawRows = (m["rows"] ?: m["data"] ?: m["items"] ?: m["records"]) as? JsonArray
            if (rawRows != null && rawRows.isNotEmpty() && rawRows.first() is JsonObject) {
                val derivedCols = mutableListOf<String>()
                val existingCols = (m["columns"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
                if (existingCols.isNotEmpty()) {
                    derivedCols.addAll(existingCols)
                } else {
                    for (r in rawRows) {
                        val ro = r as? JsonObject ?: continue
                        for (k in ro.keys) if (k !in derivedCols) derivedCols.add(k)
                    }
                    m["columns"] = JsonArray(derivedCols.map { JsonPrimitive(it) })
                }
                val stringRows = rawRows.mapNotNull { r ->
                    val ro = r as? JsonObject ?: return@mapNotNull null
                    JsonArray(derivedCols.map { col -> JsonPrimitive((ro[col] as? JsonPrimitive)?.contentOrNull ?: "") })
                }
                m["rows"] = JsonArray(stringRows)
            }
        }
        "compare" -> {
            if ("left" !in m && "pros" in m) {
                m["left"] = JsonObject(mapOf("title" to JsonPrimitive("Pros"), "items" to (m["pros"] ?: JsonArray(emptyList()))))
            }
            if ("right" !in m && "cons" in m) {
                m["right"] = JsonObject(mapOf("title" to JsonPrimitive("Cons"), "items" to (m["cons"] ?: JsonArray(emptyList()))))
            }
            if ("left" !in m && "a" in m) m["left"] = m["a"]!!
            if ("right" !in m && "b" in m) m["right"] = m["b"]!!
            for (sideKey in listOf("left", "right")) {
                val side = m[sideKey] as? JsonObject ?: continue
                if ("items" !in side) {
                    val pts = (side["points"] ?: side["bullets"] ?: side["features"] ?: side["list"]) as? JsonArray
                    if (pts != null) {
                        val sm = side.toMutableMap()
                        sm["items"] = pts
                        m[sideKey] = JsonObject(sm)
                    }
                }
            }
        }
        "timeline" -> {
            if ("entries" !in m) {
                (m["events"] ?: m["milestones"] ?: m["items"] ?: m["steps"] ?: m["history"])?.let { m["entries"] = it }
            }
        }
        "quiz" -> {
            val rawQ = (m["questions"] ?: m["items"] ?: m["cards"]) as? JsonArray
                ?: if ("question" in m || "q" in m) JsonArray(listOf(baseObj)) else null
            if (rawQ != null) {
                val normQ = rawQ.mapNotNull { qe ->
                    val qo = qe as? JsonObject ?: return@mapNotNull null
                    val qm = qo.toMutableMap()
                    if ("q" !in qm) (qo["question"] ?: qo["prompt"] ?: qo["title"])?.let { qm["q"] = it }
                    val opts = (qo["options"] ?: qo["choices"] ?: qo["answers"]) as? JsonArray
                    if (opts != null) qm["options"] = opts
                    val ansElem = qo["answer"] ?: qo["correct"] ?: qo["correctAnswer"]
                    val ansPrim = ansElem as? JsonPrimitive
                    if (ansPrim != null && ansPrim.intOrNull == null) {
                        val rawStr = ansPrim.contentOrNull?.trim().orEmpty()
                        val optStrings = opts?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
                        val exactIdx = optStrings.indexOfFirst { it.equals(rawStr, ignoreCase = true) }
                        val resolved = when {
                            exactIdx >= 0 -> exactIdx
                            rawStr.length == 1 && rawStr[0].uppercaseChar() in 'A'..'F' -> rawStr[0].uppercaseChar() - 'A'
                            else -> 0
                        }
                        qm["answer"] = JsonPrimitive(resolved)
                    }
                    JsonObject(qm)
                }
                m["questions"] = JsonArray(normQ)
            }
        }
        "accordion" -> {
            val rawSec = (m["sections"] ?: m["items"] ?: m["faqs"] ?: m["panels"] ?: m["questions"]) as? JsonArray
            if (rawSec != null) {
                val normSec = rawSec.mapNotNull { se ->
                    val so = se as? JsonObject ?: return@mapNotNull null
                    val sm = so.toMutableMap()
                    if ("label" !in sm) (so["title"] ?: so["heading"] ?: so["question"] ?: so["q"])?.let { sm["label"] = it }
                    if ("body" !in sm) (so["content"] ?: so["text"] ?: so["answer"] ?: so["a"] ?: so["detail"])?.let { sm["body"] = it }
                    JsonObject(sm)
                }
                m["sections"] = JsonArray(normSec)
            }
        }
        "checklist" -> {
            val rawItems = (m["items"] ?: m["tasks"] ?: m["todos"] ?: m["checklist"]) as? JsonArray
            if (rawItems != null) {
                val normItems = rawItems.mapNotNull { ie ->
                    when (ie) {
                        is JsonPrimitive -> ie.contentOrNull?.let {
                            JsonObject(mapOf("label" to JsonPrimitive(it), "done" to JsonPrimitive(false)))
                        }
                        is JsonObject -> {
                            val im = ie.toMutableMap()
                            if ("label" !in im) (ie["title"] ?: ie["text"] ?: ie["task"] ?: ie["name"])?.let { im["label"] = it }
                            if ("done" !in im) {
                                val done = (ie["checked"] ?: ie["completed"]) as? JsonPrimitive
                                if (done?.booleanOrNull != null) im["done"] = done
                            }
                            JsonObject(im)
                        }
                        else -> null
                    }
                }
                m["items"] = JsonArray(normItems)
            }
        }
        "steps" -> {
            val rawSteps = (m["steps"] ?: m["items"] ?: m["stages"] ?: m["phases"]) as? JsonArray
            if (rawSteps != null) {
                val normSteps = rawSteps.mapIndexedNotNull { idx, se ->
                    when (se) {
                        is JsonPrimitive -> se.contentOrNull?.let {
                            JsonObject(mapOf("title" to JsonPrimitive(it), "state" to JsonPrimitive(if (idx == 0) "current" else "todo")))
                        }
                        is JsonObject -> {
                            val sm = se.toMutableMap()
                            if ("title" !in sm) (se["label"] ?: se["name"] ?: se["step"])?.let { sm["title"] = it }
                            if ("body" !in sm) (se["detail"] ?: se["description"] ?: se["text"])?.let { sm["body"] = it }
                            JsonObject(sm)
                        }
                        else -> null
                    }
                }
                m["steps"] = JsonArray(normSteps)
            }
        }
        "flashcards" -> {
            val rawCards = (m["cards"] ?: m["items"] ?: m["deck"] ?: m["pairs"]) as? JsonArray
            if (rawCards != null) {
                val normCards = rawCards.mapNotNull { ce ->
                    val co = ce as? JsonObject ?: return@mapNotNull null
                    val cm = co.toMutableMap()
                    if ("front" !in cm) (co["question"] ?: co["q"] ?: co["term"] ?: co["prompt"])?.let { cm["front"] = it }
                    if ("back" !in cm) (co["answer"] ?: co["a"] ?: co["definition"] ?: co["meaning"])?.let { cm["back"] = it }
                    JsonObject(cm)
                }
                m["cards"] = JsonArray(normCards)
            }
        }
        "app" -> {
            fun normAppItem(el: JsonElement): JsonObject? = when (el) {
                is JsonPrimitive -> el.contentOrNull?.let { JsonObject(mapOf("title" to JsonPrimitive(it))) }
                is JsonObject -> {
                    val im = el.toMutableMap()
                    if ("title" !in im) (el["label"] ?: el["text"] ?: el["task"] ?: el["event"] ?: el["name"])?.let { im["title"] = it }
                    if ("subtitle" !in im) (el["description"] ?: el["detail"] ?: el["assignee"] ?: el["time"])?.let { im["subtitle"] = it }
                    if ("badge" !in im) (el["priority"] ?: el["tag"] ?: el["status"] ?: el["value"])?.let { im["badge"] = it }
                    if ("date" !in im) (el["day"] ?: el["when"])?.let { im["date"] = it }
                    JsonObject(im)
                }
                else -> null
            }
            val rawItems = (m["items"] ?: m["events"] ?: m["tasks"] ?: m["cards"] ?: m["files"] ?: m["metrics"] ?: m["kpis"]) as? JsonArray
            if (rawItems != null) {
                m["items"] = JsonArray(rawItems.mapNotNull(::normAppItem))
            }
            val rawColsEl = m["columns"] ?: m["lanes"] ?: m["boards"]
            val rawCols: List<JsonObject>? = when (rawColsEl) {
                is JsonObject -> rawColsEl.entries.map { (k, v) ->
                    JsonObject(mapOf("title" to JsonPrimitive(k), "items" to (v as? JsonArray ?: JsonArray(emptyList()))))
                }
                is JsonArray -> rawColsEl.mapNotNull { ce ->
                    when (ce) {
                        is JsonPrimitive -> ce.contentOrNull?.let { JsonObject(mapOf("title" to JsonPrimitive(it))) }
                        is JsonObject -> ce
                        else -> null
                    }
                }
                else -> null
            }
            if (rawCols != null) {
                m["columns"] = JsonArray(rawCols.map { co ->
                    val cm = co.toMutableMap()
                    if ("title" !in cm) (co["name"] ?: co["label"] ?: co["status"] ?: co["header"])?.let { cm["title"] = it }
                    val colItems = (co["items"] ?: co["cards"] ?: co["tasks"]) as? JsonArray
                    if (colItems != null) {
                        cm["items"] = JsonArray(colItems.mapNotNull(::normAppItem))
                    }
                    JsonObject(cm)
                })
            }
        }
        "editor" -> {
            if ("columns" !in m && m["headers"] is JsonArray) m["columns"] = m["headers"]!!
            if ("rows" !in m && m["data"] is JsonArray) m["rows"] = m["data"]!!
        }
        "calc" -> {
            val rawFields = (m["fields"] ?: m["inputs"] ?: m["sliders"] ?: m["variables"] ?: m["params"]) as? JsonArray
            if (rawFields != null) {
                m["fields"] = JsonArray(rawFields.mapIndexedNotNull { idx, el ->
                    val fo = el as? JsonObject ?: return@mapIndexedNotNull null
                    val fm = fo.toMutableMap()
                    val keyPrim = (fo["key"] ?: fo["name"] ?: fo["id"]) as? JsonPrimitive
                    val keyStr = keyPrim?.content?.takeIf { it.isNotBlank() } ?: "x${idx + 1}"
                    fm["key"] = JsonPrimitive(keyStr)
                    if ("label" !in fm) {
                        val lbl = (fo["title"] ?: fo["name"] ?: fo["key"]) as? JsonPrimitive
                        fm["label"] = JsonPrimitive(lbl?.content ?: keyStr)
                    }
                    if ("value" !in fm) {
                        val defVal = (fo["default"] ?: fo["initial"] ?: fo["min"]) as? JsonPrimitive
                        val num = defVal?.content?.toDoubleOrNull() ?: 0.0
                        fm["value"] = JsonPrimitive(num)
                    }
                    JsonObject(fm)
                })
            }
            val rawOutputs = (m["outputs"] ?: m["results"]) as? JsonArray
            if (rawOutputs != null && rawOutputs.isNotEmpty()) {
                m["outputs"] = JsonArray(rawOutputs.mapNotNull { el ->
                    val oo = el as? JsonObject ?: return@mapNotNull null
                    val om = oo.toMutableMap()
                    if ("expr" !in om) {
                        val ex = (oo["formula"] ?: oo["expression"] ?: oo["value"]) as? JsonPrimitive
                        if (ex != null) om["expr"] = JsonPrimitive(ex.content)
                    }
                    if ("label" !in om) {
                        val lbl = (oo["title"] ?: oo["name"]) as? JsonPrimitive
                        om["label"] = JsonPrimitive(lbl?.content ?: "Result")
                    }
                    JsonObject(om)
                })
            } else {
                val topFormula = ((m["formula"] ?: m["expression"] ?: m["expr"]) as? JsonPrimitive)?.content?.trim().orEmpty()
                if (topFormula.isNotEmpty()) {
                    val topUnit = ((m["unit"] ?: m["currency"]) as? JsonPrimitive)?.content.orEmpty()
                    val topLabel = ((m["outputLabel"] ?: m["resultLabel"]) as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: "Result"
                    m["outputs"] = JsonArray(
                        listOf(
                            JsonObject(
                                mapOf(
                                    "label" to JsonPrimitive(topLabel),
                                    "expr" to JsonPrimitive(topFormula),
                                    "unit" to JsonPrimitive(topUnit),
                                    "primary" to JsonPrimitive(true)
                                )
                            )
                        )
                    )
                }
            }
        }
    }

    val rawKeysPreserved = setOf("code", "html", "svg", "url", "src", "href", "unified", "before", "after", "formula", "expression", "expr", "z", "pattern", "regex", "query", "sql")
    val iconTokenKeys = if (kind in setOf("followups", "actions", "menu", "chips")) setOf("label", "title", "text", "name") else emptySet()

    fun sanitizeJsonElement(el: JsonElement, keyHint: String? = null): JsonElement = when (el) {
        is JsonPrimitive -> {
            if (!el.isString) el
            else {
                val rawStr = el.content
                when {
                    keyHint in rawKeysPreserved -> el
                    keyHint in iconTokenKeys -> JsonPrimitive(NexlyIcons.normalize(rawStr).trim())
                    else -> JsonPrimitive(NexlyIcons.cleanPlainText(rawStr))
                }
            }
        }
        is JsonArray -> JsonArray(el.map { sanitizeJsonElement(it, keyHint) })
        is JsonObject -> JsonObject(el.mapValues { (k, v) -> sanitizeJsonElement(v, k) })
    }

    return sanitizeJsonElement(JsonObject(m)).toString()
}
