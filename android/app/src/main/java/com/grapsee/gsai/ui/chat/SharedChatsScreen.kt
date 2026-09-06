package com.grapsee.gsai.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsListItem
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.components.GsSectionHeader
import com.grapsee.gsai.ui.theme.GsMotion

private data class SharedChat(val title: String, val subtitle: String)

private val sharedChats = listOf(
    SharedChat("Launch plan review", "Shared 2 days ago · 6 views"),
    SharedChat("Kyoto itinerary", "Shared last week · 21 views"),
    SharedChat("API contract notes", "Shared 3 weeks ago · 4 views")
)

/** Shared chats — every row exposes copy-link and revoke affordances. */
@Composable
fun SharedChatsScreen(onBack: () -> Unit) {
    GsScreenScaffold(title = "Shared", onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            GsSectionHeader("Active links")
            sharedChats.forEach { chat ->
                GsListItem(
                    title = chat.title,
                    subtitle = chat.subtitle,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GsChip(text = "Anyone with link", selected = false, onClick = {})
                            IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Outlined.Link,
                                    contentDescription = "Copy link",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = "Revoke link",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                )
            }
            Text(
                text = "Revoke a link to make its chat private again.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
