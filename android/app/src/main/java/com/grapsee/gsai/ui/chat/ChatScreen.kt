package com.grapsee.gsai.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsEmptyState
import com.grapsee.gsai.ui.components.GsInputBar
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.rememberAuroraBrush
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One renderable chat turn. role is "user" or "assistant" (error bubbles included). */
private data class ChatUiMessage(
    val id: String,
    val role: String,
    val content: String,
    val isStreaming: Boolean = false
)

/**
 * Streaming chat surface. History loads from Room (via the repository), sends go
 * through [ServiceLocator.chat.send] which returns the owning conversation id —
 * so a brand-new chat adopts its server id after the first turn completes.
 * Stop-generation cancels the streaming Job; partial output stays on screen and disk.
 */
@Composable
fun ChatScreen(conversationId: String?, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var activeConversationId by remember { mutableStateOf(conversationId) }
    val messages = remember { mutableStateListOf<ChatUiMessage>() }
    var draft by remember { mutableStateOf("") }
    var streamingJob by remember { mutableStateOf<Job?>(null) }
    var conversationTitle by remember { mutableStateOf("New chat") }
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val isStreaming = streamingJob?.isActive == true

    LaunchedEffect(conversationId) {
        val id = conversationId ?: return@LaunchedEffect
        val history = runCatching { ServiceLocator.chat.history(id) }.getOrElse { emptyList() }
        messages.clear()
        messages.addAll(history.map { ChatUiMessage(it.id, it.role, it.content) })
        val loadedTitle = runCatching { ServiceLocator.db.conversationDao().getById(id) }
            .getOrNull()?.title
        if (!loadedTitle.isNullOrBlank()) conversationTitle = loadedTitle
    }

    // Keep the newest turn in view while messages arrive and grow.
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun finalizeStreamingMessage() {
        val index = messages.indexOfLast { it.isStreaming }
        if (index >= 0) {
            val message = messages[index]
            if (message.content.isBlank()) messages.removeAt(index)
            else messages[index] = message.copy(isStreaming = false)
        }
    }

    fun reportFailure(error: Throwable) {
        finalizeStreamingMessage()
        messages.add(
            ChatUiMessage(
                id = UUID.randomUUID().toString(),
                role = "assistant",
                content = "⚠️ ${error.message ?: "Something went wrong"} — check backend"
            )
        )
    }

    fun dispatch(text: String, echoUser: Boolean) {
        val prompt = text.trim()
        if (prompt.isEmpty() || streamingJob?.isActive == true) return
        draft = ""
        if (echoUser) {
            messages.add(ChatUiMessage(UUID.randomUUID().toString(), "user", prompt))
        }
        if (activeConversationId == null) conversationTitle = prompt.take(40)
        val assistantId = UUID.randomUUID().toString()
        messages.add(ChatUiMessage(assistantId, "assistant", "", isStreaming = true))
        streamingJob = scope.launch {
            try {
                val returnedId = ServiceLocator.chat.send(
                    conversationId = activeConversationId,
                    content = prompt,
                    onDelta = { delta ->
                        val index = messages.indexOfFirst { it.id == assistantId }
                        if (index >= 0) {
                            val current = messages[index]
                            messages[index] = current.copy(content = current.content + delta)
                        }
                    }
                )
                activeConversationId = returnedId
                finalizeStreamingMessage()
            } catch (ce: CancellationException) {
                finalizeStreamingMessage()
                throw ce
            } catch (e: Exception) {
                reportFailure(e)
            } finally {
                streamingJob = null
            }
        }
    }

    fun regenerate(assistantMessageId: String) {
        if (streamingJob?.isActive == true) return
        val index = messages.indexOfFirst { it.id == assistantMessageId }
        if (index < 0) return
        val userText = messages.subList(0, index).lastOrNull { it.role == "user" }?.content ?: return
        messages.removeAt(index)
        dispatch(userText, echoUser = false)
    }

    GsScreenScaffold(
        title = conversationTitle,
        onBack = onBack,
        actions = {
            GsChip(text = "Auto", selected = false, onClick = {})
            IconButton(onClick = {}) {
                Icon(Icons.Outlined.Tune, contentDescription = "Model settings",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            if (isStreaming) AuroraIndicator()

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (messages.isEmpty()) {
                    StarterPrompts(onPick = { dispatch(it, echoUser = true) })
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(messages, key = { _, message -> message.id }) { _, message ->
                            if (message.role == "user") {
                                UserMessage(message)
                            } else {
                                AssistantMessage(
                                    message = message,
                                    onCopy = { text -> clipboard.setText(AnnotatedString(text)) },
                                    onRegenerate = { regenerate(message.id) }
                                )
                            }
                        }
                    }
                }
            }

            if (!isStreaming) {
                GsInputBar(
                    value = draft,
                    onValueChange = { draft = it },
                    onSend = { text -> dispatch(text, echoUser = true) },
                    placeholder = "Ask anything…"
                )
            } else {
                Button(
                    onClick = {
                        streamingJob?.cancel()
                        streamingJob = null
                        finalizeStreamingMessage()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Stop generating")
                }
            }
        }
    }
}

// --- bubbles ----------------------------------------------------------------

@Composable
private fun UserMessage(message: ChatUiMessage) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun AssistantMessage(
    message: ChatUiMessage,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (message.isStreaming) StreamingCaret()
                }
                if (!message.isStreaming) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        BubbleAction(Icons.Outlined.ContentCopy, "Copy") { onCopy(message.content) }
                        BubbleAction(Icons.Outlined.Refresh, "Regenerate", onRegenerate)
                        BubbleAction(Icons.Outlined.VolumeUp, "Read aloud") {}
                        BubbleAction(Icons.Outlined.Share, "Share") {}
                    }
                }
            }
        }
    }
}

@Composable
private fun BubbleAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** Kinetic caret that pulses while the assistant is writing. */
@Composable
private fun StreamingCaret() {
    val transition = rememberInfiniteTransition(label = "caret")
    val alpha by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = GsMotion.CARET_PULSE_MS),
            repeatMode = RepeatMode.Reverse
        ),
        label = "caretAlpha"
    )
    Text(
        text = "▍",
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 2.dp).alpha(alpha)
    )
}

/** Aurora life-sign above the input while the model is generating. */
@Composable
private fun AuroraIndicator() {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(shape)
            .background(rememberAuroraBrush(shape))
    )
}

// --- empty-state starters ----------------------------------------------------

private val starterPrompts = listOf(
    "Draft a crisp product update email for our beta testers",
    "Explain Kotlin coroutines like I'm a senior Java developer",
    "Plan a three-day Tokyo itinerary focused on design studios"
)

@Composable
private fun StarterPrompts(onPick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = GsMotion.spaceM),
        verticalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
    ) {
        GsEmptyState(
            icon = Icons.Outlined.ChatBubbleOutline,
            title = "Start the conversation",
            message = "Pick a starter or type your own — replies stream in as they are written."
        )
        starterPrompts.forEach { prompt ->
            GsCard(onClick = { onPick(prompt) }) {
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
