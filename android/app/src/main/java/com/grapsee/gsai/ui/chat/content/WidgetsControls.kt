package com.grapsee.gsai.ui.chat.content

// ---------------------------------------------------------------------------
// Control widgets (0.79.0) — the interactive-control gaps:
//   actions: buttons / copy-to-clipboard / open-link / clickable surfaces
//   panel:   expandable panels / popovers / badges
// Plus the `segmented` form field type in InteractiveWidgets.kt.
// ---------------------------------------------------------------------------

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.theme.GsTheme
import kotlinx.serialization.Serializable

// --- models -----------------------------------------------------------------

@Serializable
data class ActionButton(
    val label: String = "",
    /** prompt | copy | open */
    val kind: String = "prompt",
    val value: String = "",
    /** filled | tonal | text */
    val style: String = "filled"
)

@Serializable
data class ActionsWidget(val title: String = "", val buttons: List<ActionButton> = emptyList())

@Serializable
data class PanelBadge(val label: String = "", val tone: String = "accent")

@Serializable
data class PanelWidget(
    val title: String = "",
    val summary: String = "",
    val body: String = "",
    val expanded: Boolean = false,
    val badges: List<PanelBadge> = emptyList(),
    /** Non-blank shows a "More info" button that reveals this text in a popover. */
    val popover: String = ""
)

// --- actions ----------------------------------------------------------------

@Composable
fun ActionsWidgetView(w: ActionsWidget, onSendPrompt: ((String) -> Unit)? = null) {
    val clipboard = LocalClipboardManager.current
    val uri = LocalUriHandler.current
    val act: (ActionButton) -> Unit = { b ->
        when (b.kind.lowercase()) {
            "copy" -> clipboard.setText(AnnotatedString(b.value.ifBlank { b.label }))
            "open" -> if (b.value.isNotBlank()) runCatching { uri.openUri(b.value) }
            else -> onSendPrompt?.invoke(b.value.ifBlank { b.label })
        }
    }
    WidgetCardShell(w.title, "actions") {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            w.buttons.take(8).forEach { b ->
                when (b.style.lowercase()) {
                    "text" -> TextButton(onClick = { act(b) }) { Text(b.label) }
                    "tonal" -> FilledTonalButton(onClick = { act(b) }) { Text(b.label) }
                    else -> Button(onClick = { act(b) }) { Text(b.label) }
                }
            }
        }
    }
}

// --- panel / popover / badges ----------------------------------------------

@Composable
fun PanelWidgetView(w: PanelWidget) {
    val c = GsTheme.colors
    var expanded by remember { mutableStateOf(w.expanded) }
    var popover by remember { mutableStateOf(false) }
    WidgetCardShell(w.title, "panel") {
        Column(Modifier.padding(top = 4.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    w.summary.ifBlank { if (expanded) "Hide details" else "Show details" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textPrimary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(if (expanded) "▾" else "▸", color = c.textTertiary)
            }

            if (w.badges.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    w.badges.take(8).forEach { b ->
                        val tone = when (b.tone.lowercase()) {
                            "success" -> c.success
                            "warning" -> c.warning
                            "danger" -> c.error
                            else -> c.accent
                        }
                        Box(
                            Modifier.clip(RoundedCornerShape(99.dp)).background(tone.copy(alpha = 0.16f))
                                .padding(horizontal = 9.dp, vertical = 3.dp)
                        ) {
                            Text(b.label, style = MaterialTheme.typography.labelSmall, color = tone)
                        }
                    }
                }
            }

            if (w.popover.isNotBlank()) {
                TextButton(onClick = { popover = !popover }) {
                    Text(if (popover) "Hide note" else "More info", style = MaterialTheme.typography.labelMedium)
                }
                if (popover) {
                    Box(
                        Modifier.fillMaxWidth().padding(top = 2.dp).clip(RoundedCornerShape(10.dp))
                            .background(c.elevatedSurface).padding(10.dp)
                    ) {
                        Text(w.popover, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
            }

            if (expanded && w.body.isNotBlank()) {
                Box(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp))
                        .background(c.surface).padding(10.dp)
                ) {
                    Text(w.body, style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
                }
            }
        }
    }
}
