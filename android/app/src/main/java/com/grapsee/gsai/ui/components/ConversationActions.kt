package com.grapsee.gsai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.kineticPress

/**
 * Pin / rename / archive / delete — the full benchmark action set (ChatGPT's
 * drawer recents and chat rows). Shared by GsDrawer, ChatsScreen and the
 * Archive shelf so every surface behaves identically.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ConversationActionsSheet(
    title: String,
    pinned: Boolean,
    archiveLabel: String = "Archive",
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit
) {
    var renameOpen by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf(title) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GsMotion.spaceM)
                .padding(bottom = GsMotion.spaceL),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = GsMotion.spaceS)
            )
            SheetAction(
                icon = Icons.Outlined.PushPin,
                label = if (pinned) "Unpin" else "Pin to top",
                onClick = onTogglePin
            )
            SheetAction(
                icon = Icons.Outlined.DriveFileRenameOutline,
                label = "Rename",
                onClick = { renameDraft = title; renameOpen = true }
            )
            SheetAction(icon = Icons.Outlined.Archive, label = archiveLabel, onClick = onArchive)
            SheetAction(
                icon = Icons.Outlined.Delete,
                label = "Delete",
                tone = MaterialTheme.colorScheme.error,
                onClick = onDelete
            )
        }
    }

    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Rename chat") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                    label = { Text("Chat name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renameOpen = false
                        onRename(renameDraft.trim())
                    },
                    enabled = renameDraft.isNotBlank()
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SheetAction(
    icon: ImageVector,
    label: String,
    tone: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    val actionInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(androidx.compose.ui.graphics.Color.Transparent)
            .kineticPress(actionInteraction)
            .clickable(
                interactionSource = actionInteraction,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(horizontal = GsMotion.spaceM, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
    ) {
        Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tone)
    }
}
