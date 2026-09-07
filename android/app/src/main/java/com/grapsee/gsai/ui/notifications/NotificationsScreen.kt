package com.grapsee.gsai.ui.notifications

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.model.NotificationSample
import com.grapsee.gsai.data.model.SampleData
import com.grapsee.gsai.ui.components.GsEmptyState
import com.grapsee.gsai.ui.components.GsListItem
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.components.GsSectionHeader
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsMotion

@Composable
fun NotificationsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    // Local read state: ids dismissed from "unread" via "Mark all read" or a row tap.
    val readIds = remember { mutableStateListOf<String>() }
    val allRead = SampleData.notifications.all { !it.unread || it.id in readIds }

    fun open(item: NotificationSample) {
        if (item.unread && item.id !in readIds) readIds.add(item.id)
        onNavigate(routeFor(item.type))
    }

    GsScreenScaffold(
        title = "Notifications",
        onBack = onBack,
        actions = {
            TextButton(
                onClick = {
                    readIds.clear()
                    readIds.addAll(SampleData.notifications.map { it.id })
                },
                enabled = !allRead
            ) {
                Text("Mark all read")
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
        ) {
            val today = SampleData.notifications.filter { it.unread && it.id !in readIds }
            val earlier = SampleData.notifications.filter { !(it.unread && it.id !in readIds) }

            if (today.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                    GsSectionHeader(title = "Today")
                    today.forEach { item ->
                        NotificationRow(item = item, unread = true, onOpen = ::open)
                    }
                }
            }

            if (earlier.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                    GsSectionHeader(title = "Earlier")
                    earlier.forEach { item ->
                        NotificationRow(item = item, unread = false, onOpen = ::open)
                    }
                }
            }

            if (today.isEmpty() && earlier.isEmpty()) {
                GsEmptyState(
                    icon = Icons.Outlined.Notifications,
                    title = "All caught up",
                    message = "Task, file and security alerts will land here."
                )
            }

            Spacer(modifier = Modifier.height(GsMotion.spaceL))
        }
    }
}

@Composable
private fun NotificationRow(item: NotificationSample, unread: Boolean, onOpen: (NotificationSample) -> Unit) {
    GsListItem(
        title = item.title,
        subtitle = item.body,
        onClick = { onOpen(item) },
        modifier = if (item.type == "security") {
            Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
        } else {
            Modifier
        },
        leading = { TypeBadge(type = item.type, unread = unread) },
        trailing = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (unread) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(8.dp)
                    ) {}
                }
                Text(
                    text = item.time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

/** Type-based deep link: finished work opens a chat, activity opens its hub. */
private fun routeFor(type: String): String = when (type) {
    "assistant" -> GsRoutes.EXPLORE
    "share", "project" -> GsRoutes.PROJECTS
    "system", "security" -> GsRoutes.SETTINGS
    else -> GsRoutes.chat(null) // task, file — continue the work in a chat
}

@Composable
private fun TypeBadge(type: String, unread: Boolean) {
    val icon: ImageVector = when (type) {
        "task" -> Icons.Outlined.TaskAlt
        "file" -> Icons.Outlined.Description
        "assistant" -> Icons.Outlined.SmartToy
        "share" -> Icons.Outlined.Share
        "project" -> Icons.Outlined.Folder
        "security" -> Icons.Outlined.Security
        else -> Icons.Outlined.Info
    }
    Surface(
        shape = CircleShape,
        color = if (unread) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = type,
                tint = if (unread) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
