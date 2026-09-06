package com.grapsee.gsai.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.components.GsEmptyState
import com.grapsee.gsai.ui.components.GsListItem
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.components.GsSectionHeader
import com.grapsee.gsai.ui.theme.GsMotion

private data class ArchivedChat(val title: String, val subtitle: String)

private val archivedChats = listOf(
    ArchivedChat("Old marketing brainstorm", "Archived 3 weeks ago · 24 messages"),
    ArchivedChat("2024 tax research", "Archived last month · 12 messages"),
    ArchivedChat("Scraper debugging log", "Archived 2 months ago · 41 messages"),
    ArchivedChat("Onboarding flow drafts", "Archived last quarter · 8 messages")
)

/** Archive shelf — restore affordances per row; nothing else archived below the fold. */
@Composable
fun ArchivedChatsScreen(onBack: () -> Unit) {
    GsScreenScaffold(title = "Archived", onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            GsSectionHeader("Recently archived")
            archivedChats.forEach { chat ->
                GsListItem(
                    title = chat.title,
                    subtitle = chat.subtitle,
                    leading = {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.Archive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    trailing = {
                        IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Outlined.Unarchive,
                                contentDescription = "Unarchive",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
            }
            Spacer(Modifier.height(GsMotion.spaceS))
            GsEmptyState(
                icon = Icons.Outlined.Archive,
                title = "Nothing else archived",
                message = "Chats you archive will rest here until you bring them back."
            )
        }
    }
}
