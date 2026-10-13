package com.grapsee.gsai.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.components.GsSectionHeader
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.icons.GsIcons

private data class ChatFolder(val name: String, val count: Int, val icon: ImageVector)

private val chatFolders = listOf(
    ChatFolder("Work", 12, GsIcons.Briefcase),
    ChatFolder("Learning", 7, GsIcons.GraduationCap),
    ChatFolder("Client drafts", 3, GsIcons.FileText)
)

/** Folders — two-column grid of named buckets plus a creation tile. */
@Composable
fun ConversationFoldersScreen(onBack: () -> Unit) {
    GsScreenScaffold(title = "Folders", onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
        ) {
            GsSectionHeader("Organise your chats")
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(chatFolders, key = { it.name }) { folder ->
                    GsCard(onClick = {}) {
                        Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)) {
                            Icon(
                                folder.icon,
                                contentDescription = folder.name,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                folder.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "${folder.count} chats",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                item(key = "new-folder") {
                    GsCard(onClick = {}) {
                        Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)) {
                            Icon(
                                GsIcons.FolderPlus,
                                contentDescription = "New folder",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                "New folder",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Group related chats",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
