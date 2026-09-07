package com.grapsee.gsai.ui.create

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsListItem
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.components.GsSectionHeader
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.rememberAuroraBrush
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * AERUO KINETIC — WRITING STUDIO.
 * Doc type + tone shape a brief; "Draft outline" thinks for 700ms behind an
 * aurora dot, then lays out a 5-beat outline and a prefilled editable draft
 * with copy / share (plain-text ACTION_SEND) / save actions. Sample drafts
 * only — the writing model lands with the data layer.
 */

private val docTypes = listOf("Blog post", "Essay", "Email", "Script")

private val tones = listOf("Warm", "Sharp", "Neutral", "Playful")

private val outlinesByType = mapOf(
    "Blog post" to listOf(
        "Hook — open with the tension the reader feels",
        "Context — why now, in three sentences",
        "Argument — three moves, one section each",
        "Counterpoint — steel-man the sceptic, then answer",
        "Close — the single sentence worth quoting"
    ),
    "Essay" to listOf(
        "Thesis — state the claim precisely",
        "Grounds — evidence and examples, oldest to newest",
        "Warrant — connect the evidence to the claim",
        "Objection — the strongest counter-reading",
        "Synthesis — restate the thesis, widened"
    ),
    "Email" to listOf(
        "Subject — five words, decision-forward",
        "Opening line — the ask, stated plainly",
        "Context — two bullets of background",
        "Options — what needs deciding, by when",
        "Sign-off — one line, next step dated"
    ),
    "Script" to listOf(
        "Cold open — the moment before the problem",
        "Setup — introduce the world in thirty seconds",
        "Turn — the decision that changes everything",
        "Rise — escalating stakes across three beats",
        "Button — the final line, held for a beat"
    )
)

private val draftsByType = mapOf(
    "Blog post" to "The best writing tools disappear. You notice them only when they interrupt — a menu where a sentence should be, a suggestion that mistakes decoration for voice.\n\nWe built this editor backwards from that observation: no chrome, no modes, just a page that keeps up with the pace of thought. The result reads less like software and more like a well-set page.",
    "Essay" to "Every claim about attention begins with an unexamined metaphor. We say we spend it, pay it, capture it — as if attention were currency moving between pockets.\n\nThe metaphor is convenient and wrong. Attention is closer to a posture than a purse: it is a way of holding body and mind at once, and it cannot be stockpiled, only practised.",
    "Email" to "Hi Maya,\n\nQuick one: can you sign off on the Q3 creative budget by Friday? Two options attached — the lean plan holds at £48k, the ambitious one at £61k.\n\nEither works; I just need a name against one of them before the vendor holds expire.\n\nThanks,\nSam",
    "Script" to "INT. STUDIO - NIGHT\n\nA single desk lamp. MAYA reads the same line for the third time.\n\nMAYA\n(to no one)\nRun it again.\n\nThe engine hums. The cursor blinks, patient as a lighthouse."
)

@Composable
fun WritingStudioScreen(onBack: () -> Unit) {
    var docType by remember { mutableStateOf("Blog post") }
    var tone by remember { mutableStateOf("Warm") }
    var brief by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var outline by remember { mutableStateOf<List<String>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showSnack: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    LaunchedEffect(thinking) {
        if (thinking) {
            delay(700)
            outline = outlinesByType[docType] ?: emptyList()
            draft = draftsByType[docType] ?: ""
            thinking = false
        }
    }

    // Switching doc type after a draft exists re-derives outline + draft instantly.
    LaunchedEffect(docType) {
        if (!thinking && outline.isNotEmpty()) {
            outline = outlinesByType[docType] ?: emptyList()
            draft = draftsByType[docType] ?: ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        GsScreenScaffold(title = "Writing", onBack = onBack) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(GsMotion.spaceL)
            ) {
                Spacer(Modifier.height(GsMotion.spaceS))

                Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                    GsSectionHeader(title = "Document type")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                    ) {
                        docTypes.forEach { option ->
                            GsChip(text = option, selected = docType == option) { docType = option }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                    GsSectionHeader(title = "Tone")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                    ) {
                        tones.forEach { option ->
                            GsChip(text = option, selected = tone == option) { tone = option }
                        }
                    }
                }

                OutlinedTextField(
                    value = brief,
                    onValueChange = { brief = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    placeholder = {
                        Text(
                            "Describe what you need — audience, length, angle…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )

                Button(
                    onClick = {
                        thinking = true
                        outline = emptyList()
                        draft = ""
                    },
                    enabled = brief.isNotBlank() && !thinking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Draft outline")
                }

                if (thinking) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(rememberAuroraBrush(), CircleShape)
                        )
                        Text(
                            text = "Thinking…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (outline.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)) {
                        GsSectionHeader(title = "Outline")
                        outline.forEachIndexed { index, item ->
                            GsListItem(
                                title = item,
                                leading = { NumberBadge(index + 1) }
                            )
                        }
                    }
                }

                if (draft.isNotBlank()) {
                    GsCard {
                        Text(
                            text = "Draft",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(GsMotion.spaceS))
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        )
                        Spacer(Modifier.height(GsMotion.spaceS))
                        val words = draft.trim().split(" ").count { it.isNotEmpty() }
                        Text(
                            text = "$words words · ${tone.lowercase()} tone",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(GsMotion.spaceS))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(draft))
                                    showSnack("Copied to clipboard")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Copy")
                            }
                            FilledTonalButton(
                                onClick = {
                                    val send = Intent(Intent.ACTION_SEND)
                                    send.type = "text/plain"
                                    send.putExtra(Intent.EXTRA_TEXT, draft)
                                    context.startActivity(Intent.createChooser(send, "Share draft"))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Share")
                            }
                        }
                        Spacer(Modifier.height(GsMotion.spaceS))
                        FilledTonalButton(
                            onClick = {
                                // Real save: the finished draft lands in the Library
                                // under the Documents kind — filters catch it.
                                scope.launch {
                                    runCatching {
                                        ServiceLocator.chat.saveToLibrary(draft, kind = "document")
                                    }
                                        .onSuccess { showSnack("Saved to Library") }
                                        .onFailure { showSnack("Couldn't save right now") }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save to Library")
                        }
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
private fun NumberBadge(number: Int) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(26.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "$number",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
