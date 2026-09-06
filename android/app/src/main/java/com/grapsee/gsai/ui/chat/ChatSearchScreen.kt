package com.grapsee.gsai.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsInputBar
import com.grapsee.gsai.ui.components.GsListItem
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.components.GsSectionHeader
import com.grapsee.gsai.ui.theme.GsMotion

private data class SearchResult(val title: String, val conversation: String, val moment: String)

private val searchResults = listOf(
    SearchResult("streaming with Server-Sent Events", "Backend contract notes", "yesterday"),
    SearchResult("Room migration strategy", "Android sync engine", "3 days ago"),
    SearchResult("spring pricing comparison", "Launch plan review", "last week")
)

private val searchFilters = listOf("This week", "Model", "Has files")

/** Chat-scoped search — query bar, quick filters, seeded result rows. */
@Composable
fun ChatSearchScreen(onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf<String?>(null) }

    GsScreenScaffold(title = "Search chats", onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
        ) {
            GsInputBar(
                value = query,
                onValueChange = { query = it },
                onSend = {},
                placeholder = "Search all conversations…"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                searchFilters.forEach { filter ->
                    GsChip(
                        text = filter,
                        selected = activeFilter == filter,
                        onClick = { activeFilter = if (activeFilter == filter) null else filter }
                    )
                }
            }
            GsSectionHeader("Results")
            searchResults.forEach { result ->
                GsListItem(
                    title = result.title,
                    subtitle = "In ${result.conversation} · ${result.moment}",
                    leading = {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    onClick = {}
                )
            }
            Text(
                text = "Search runs across every conversation on this device.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
