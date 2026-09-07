package com.grapsee.gsai.ui.assistants

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.AssistantsStore
import com.grapsee.gsai.data.model.AssistantSample
import com.grapsee.gsai.data.model.SampleData
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsListItem
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsMotion

/**
 * Assistant profile. GsNavHost calls this with (assistantId, onBack); the
 * optional onStartChat callback keeps the "Start chat" button real without
 * forcing a nav-graph change.
 */
@Composable
fun AssistantDetailScreen(
    assistantId: String,
    onBack: () -> Unit,
    onStartChat: ((String) -> Unit)? = null,
    onEdit: ((String) -> Unit)? = null
) {
    // Reactive on the local store: returning from the editor refreshes in place.
    val userAssistants by AssistantsStore.assistants.collectAsState()
    val assistant = remember(assistantId, userAssistants) {
        AssistantsStore.find(assistantId)
            ?: SampleData.assistants.firstOrNull { it.id == assistantId }
            ?: SampleData.assistants.first()
    }
    val isUserAssistant = userAssistants.any { it.id == assistantId }
    var favourited by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    GsScreenScaffold(
        title = "Assistant",
        onBack = onBack,
        actions = {
            if (isUserAssistant) {
                IconButton(onClick = { onEdit?.invoke(assistantId) }) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            } else {
                IconButton(onClick = { /* share sheet lands with the sharing subsystem */ }) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            if (isUserAssistant) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete assistant",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            IconButton(onClick = { favourited = !favourited }) {
                if (favourited) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "Favourited",
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favourite",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceL)
        ) {
            // Hero
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.SmartToy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Text(
                    text = assistant.name,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                GsChip(text = assistant.category, selected = false, onClick = {})
                Text(
                    text = assistant.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "★ ${assistant.rating}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${assistant.uses} uses",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = assistant.category,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Conversation starters — tap one and the composer opens pre-filled
            Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                Text(
                    text = "Conversation starters",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                assistant.starters.forEach { starter ->
                    GsListItem(
                        title = starter,
                        leading = {
                            Icon(
                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailing = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = { onStartChat?.invoke(GsRoutes.chat(null, starter)) }
                    )
                }
            }

            // Capabilities
            Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                Text(
                    text = "Capabilities",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                ) {
                    capabilitiesFor(assistant).forEach { capability ->
                        GsChip(text = capability, selected = false, onClick = {})
                    }
                }
            }

            // Instructions
            GsCard {
                Text(
                    text = "Instructions",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(GsMotion.spaceS))
                Text(
                    text = assistant.instructions,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Primary CTA
            Button(
                onClick = { onStartChat?.invoke(GsRoutes.chat(null, assistant.starters.firstOrNull().orEmpty())) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(GsMotion.radiusInput)
            ) {
                Text("Start chat", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(GsMotion.spaceM))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete assistant") },
            text = {
                Text("“${assistant.name}” will be removed from My assistants. Chats you started with it stay in your history.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    AssistantsStore.delete(assistantId)
                    onBack()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Keep") }
            }
        )
    }
}

/** Derived capability set per assistant category (static until tools ship). */
private fun capabilitiesFor(assistant: AssistantSample): List<String> = buildList {
    add("Web search")
    add("Memory")
    if (assistant.category == "Engineering") add("Code")
    if (assistant.category in listOf("Research", "Productivity", "Analytics")) add("Files")
    if (assistant.category == "Education") add("Vision")
}
