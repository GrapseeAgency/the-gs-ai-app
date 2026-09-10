package com.grapsee.gsai.ui.chat.content

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.theme.GsTheme
import kotlinx.coroutines.delay

/**
 * Fenced code block — the Step-4 card upgraded: language label, copied-state
 * feedback, both-axis internal scroll with a height cap (large code scrolls
 * inside its card instead of growing the transcript item forever), the Step-1
 * code palette via [highlightCode], and plain monospace while the fence is
 * still open mid-stream (one highlight pass on finalize, never per flush).
 */
@Composable
fun CodeBlockCard(
    language: String?,
    code: String,
    open: Boolean,
    onCopyCode: (String) -> Unit
) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    val colors = GsTheme.colors
    val highlighted = remember(code, language, colors, open) {
        when {
            code.isBlank() -> AnnotatedString("…")
            // Mid-stream per-flush regex colouring buys nothing — plain
            // monospace while growing, one highlight pass when it lands.
            open -> AnnotatedString(code)
            else -> highlightCode(code, language, colors)
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = if (language != null) "Code block, $language" else "Code block"
            }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language ?: "code",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onCopyCode(code); copied = true }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (copied) Icons.Filled.Check else Icons.Outlined.ContentCopy,
                        contentDescription = if (copied) "Copied" else "Copy code",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
            ) {
                Text(
                    text = highlighted,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                )
            }
        }
    }
}
