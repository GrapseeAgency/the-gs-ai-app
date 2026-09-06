package com.grapsee.gsai.ui.assistants

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.theme.GsMotion

private val assistantCategories = listOf(
    "Writing", "Engineering", "Research", "Education",
    "Productivity", "Creativity", "Analytics", "Business"
)

private val capabilityOptions = listOf("Web search", "Code", "Vision", "Files", "Memory")

@Composable
fun AssistantCreateScreen(onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    val starters = remember { mutableStateListOf("", "", "") }
    var category by remember { mutableStateOf(assistantCategories.first()) }
    val selectedCapabilities = remember { mutableStateListOf("Web search") }
    var published by remember { mutableStateOf(false) }
    var showCreated by remember { mutableStateOf(false) }

    GsScreenScaffold(title = "Create assistant", onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
        ) {
            // Identity
            GsCard {
                Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                    Text(
                        text = "Identity",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Assistant name") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Description") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            // Behaviour
            GsCard {
                Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                    Text(
                        text = "Behaviour",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = instructions,
                        onValueChange = { instructions = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("How should it behave?") },
                        minLines = 4,
                        maxLines = 8,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                    ) {
                        assistantCategories.forEach { option ->
                            GsChip(
                                text = option,
                                selected = option == category,
                                onClick = { category = option }
                            )
                        }
                    }
                }
            }

            // Conversation starters
            GsCard {
                Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                    Text(
                        text = "Conversation starters",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    starters.forEachIndexed { index, starter ->
                        OutlinedTextField(
                            value = starter,
                            onValueChange = { starters[index] = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Starter ${index + 1}") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }
            }

            // Capabilities (multi-select toggles)
            GsCard {
                Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                    Text(
                        text = "Capabilities",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    capabilityOptions.chunked(2).forEach { rowOptions ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowOptions.forEach { option ->
                                GsChip(
                                    text = option,
                                    selected = option in selectedCapabilities,
                                    onClick = {
                                        if (option in selectedCapabilities) {
                                            selectedCapabilities.remove(option)
                                        } else {
                                            selectedCapabilities.add(option)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Visibility
            GsCard {
                Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                    Text(
                        text = "Visibility",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                        GsChip(text = "Private", selected = !published, onClick = { published = false })
                        GsChip(text = "Published", selected = published, onClick = { published = true })
                    }
                    Text(
                        text = if (published) {
                            "Visible in the marketplace for everyone."
                        } else {
                            "Only you can start chats with this assistant."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Primary CTA — enabled only with a name
            Button(
                onClick = { showCreated = true },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(GsMotion.radiusInput)
            ) {
                Text("Create assistant", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(GsMotion.spaceM))
        }
    }

    if (showCreated) {
        AlertDialog(
            onDismissRequest = { showCreated = false },
            title = { Text("Assistant created") },
            text = {
                Text("“${name.trim()}” is ready. You can refine its behaviour, starters and capabilities any time.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showCreated = false
                    onBack()
                }) { Text("Done") }
            }
        )
    }
}
