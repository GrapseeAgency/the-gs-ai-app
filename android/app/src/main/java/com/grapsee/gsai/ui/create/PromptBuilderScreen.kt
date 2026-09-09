package com.grapsee.gsai.ui.create

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.components.GsSectionHeader
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.GsTheme
import kotlinx.coroutines.launch

/**
 * AERUO KINETIC — PROMPT BUILDER.
 * Four labelled fields assemble into one monospace system prompt on an
 * input-surface card (GsTheme.colors.inputSurface; empty parts omitted);
 * multi-select refinements append as numbered suffix lines. Copy to clipboard
 * or hand the prompt straight to a fresh chat via onNavigate("chat/new") —
 * wired by the main agent.
 */

private val refinementOptions = listOf(
    "Be concise",
    "Add examples",
    "Cite sources",
    "Ask clarifying questions"
)

/** Joins filled parts; empty parts omitted; refinements appended as numbered lines. */
private fun assembledPrompt(
    role: String,
    context: String,
    goal: String,
    format: String,
    refinements: Set<String>
): String {
    val sentences = mutableListOf<String>()
    if (role.isNotBlank()) sentences.add("You are a ${role.trim()}.")
    if (context.isNotBlank()) sentences.add("Context: ${context.trim()}.")
    if (goal.isNotBlank()) sentences.add("Goal: ${goal.trim()}.")
    if (format.isNotBlank()) sentences.add("Format: ${format.trim()}.")
    val chosen = refinementOptions.filter { it in refinements }
    val lines = chosen.mapIndexed { index, refinement -> "${index + 1}. $refinement" }
    val parts = mutableListOf<String>()
    if (sentences.isNotEmpty()) parts.add(sentences.joinToString(" "))
    if (lines.isNotEmpty()) parts.add(lines.joinToString("\n"))
    return parts.joinToString("\n\n")
}

@Composable
fun PromptBuilderScreen(
    onBack: () -> Unit,
    // Wired by the main agent in GsNavHost (open = navController::navigate).
    // Default keeps call sites compile-safe if unwired.
    onNavigate: (String) -> Unit = {}
) {
    var role by remember { mutableStateOf("") }
    var context by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    var format by remember { mutableStateOf("") }
    var refinements by remember { mutableStateOf(setOf<String>()) }
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showSnack: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    val promptText = assembledPrompt(role, context, goal, format, refinements)

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        GsScreenScaffold(title = "Prompt builder", onBack = onBack) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(GsMotion.spaceL)
            ) {
                Spacer(Modifier.height(GsMotion.spaceS))
                PromptFieldCard(
                    label = "Role",
                    placeholder = "You are a…",
                    value = role,
                    onValueChange = { role = it }
                )
                PromptFieldCard(
                    label = "Context",
                    placeholder = "Working on…",
                    value = context,
                    onValueChange = { context = it }
                )
                PromptFieldCard(
                    label = "Goal",
                    placeholder = "Produce…",
                    value = goal,
                    onValueChange = { goal = it }
                )
                PromptFieldCard(
                    label = "Format",
                    placeholder = "Respond as…",
                    value = format,
                    onValueChange = { format = it }
                )

                Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                    GsSectionHeader(title = "Assembled preview")
                    Surface(
                        color = GsTheme.colors.inputSurface,
                        shape = RoundedCornerShape(GsMotion.radiusCard),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(GsMotion.spaceM),
                            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                        ) {
                            Text(
                                text = "System prompt",
                                style = MaterialTheme.typography.labelMedium,
                                color = GsTheme.colors.textPlaceholder
                            )
                            Text(
                                text = promptText.ifBlank { "Fill any field to assemble the prompt." },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                ),
                                color = if (promptText.isBlank()) GsTheme.colors.textPlaceholder else GsTheme.colors.textPrimary
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                    GsSectionHeader(title = "Refinements")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                    ) {
                        refinementOptions.forEach { option ->
                            GsChip(
                                text = option,
                                selected = option in refinements,
                                onClick = {
                                    refinements = if (option in refinements) {
                                        refinements - option
                                    } else {
                                        refinements + option
                                    }
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                ) {
                    FilledTonalButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(promptText))
                            showSnack("Prompt copied")
                        },
                        enabled = promptText.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Copy")
                    }
                    Button(
                        onClick = { onNavigate(GsRoutes.chat(null, promptText)) },
                        enabled = promptText.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Open in chat")
                    }
                }
                Spacer(Modifier.height(GsMotion.spaceL))
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(GsMotion.spaceM)
        )
    }
}

@Composable
private fun PromptFieldCard(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    GsCard {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(GsMotion.spaceXS))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        )
    }
}
