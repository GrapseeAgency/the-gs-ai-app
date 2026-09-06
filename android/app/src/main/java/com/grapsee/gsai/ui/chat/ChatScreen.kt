package com.grapsee.gsai.ui.chat

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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

/** Words tinted in code blocks — a deliberately small cross-language set. */
private val codeKeywords = setOf(
    "val", "var", "fun", "func", "function", "def", "class", "struct", "enum", "interface",
    "object", "trait", "impl", "type", "if", "else", "elif", "for", "while", "switch", "case",
    "match", "when", "break", "continue", "return", "yield", "import", "from", "package",
    "public", "private", "protected", "static", "final", "const", "new", "this", "self",
    "super", "null", "nil", "none", "true", "false", "try", "catch", "finally", "throw",
    "throws", "await", "async", "let", "in", "is", "as", "of", "do", "end", "override",
    "open", "suspend", "data", "where", "with", "lambda", "and", "or", "not"
)

/** Languages whose line comments start with '#' rather than '//'. */
private val hashCommentLanguages = setOf("python", "py", "bash", "sh", "shell", "ruby", "rb", "yaml", "yml", "toml")

private val codeTokenRegex = Regex(
    "(//[^\\n]*|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|\\b\\d+(?:\\.\\d+)?\\b|[A-Za-z_][A-Za-z0-9_]*)"
)

/**
 * Lightweight syntax colouring — comments, strings, numbers, keywords.
 * Purely cosmetic: an unknown token stays plain, nothing can break the layout.
 */
private fun highlightCode(code: String, language: String?, dark: Boolean): AnnotatedString {
    val kw = if (dark) androidx.compose.ui.graphics.Color(0xFFC792EA) else androidx.compose.ui.graphics.Color(0xFF6C3FE0)
    val str = if (dark) androidx.compose.ui.graphics.Color(0xFFC3E88D) else androidx.compose.ui.graphics.Color(0xFF2E7D32)
    val com = if (dark) androidx.compose.ui.graphics.Color(0xFF7E8C99) else androidx.compose.ui.graphics.Color(0xFF6B7C8C)
    val num = if (dark) androidx.compose.ui.graphics.Color(0xFFF78C6C) else androidx.compose.ui.graphics.Color(0xFFD84315)
    val hashComments = hashCommentLanguages.contains(language?.lowercase() ?: "")
    return buildAnnotatedString {
        var index = 0
        for (match in codeTokenRegex.findAll(code)) {
            append(code.substring(index, match.range.first))
            val token = match.value
            val color = when {
                token.startsWith("//") || (hashComments && token.startsWith("#")) -> com
                token.startsWith("\"") || token.startsWith("'") -> str
                token.first().isDigit() -> num
                codeKeywords.contains(token) -> kw
                else -> null
            }
            if (color != null) withStyle(SpanStyle(color = color)) { append(token) } else append(token)
            index = match.range.last + 1
        }
        append(code.substring(index))
    }
}

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
fun ChatScreen(
    conversationId: String?,
    onBack: () -> Unit,
    // Voice press-and-hold hands its transcript to the composer through this.
    prefillPrompt: String? = null,
    // Optional voice entry point — the main agent wires this to GsRoutes.VOICE in GsNavHost.
    onNavigateVoice: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var activeConversationId by remember { mutableStateOf(conversationId) }
    val messages = remember { mutableStateListOf<ChatUiMessage>() }
    var draft by remember { mutableStateOf("") }
    var streamingJob by remember { mutableStateOf<Job?>(null) }
    var conversationTitle by remember { mutableStateOf("New chat") }
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    var attachSheetOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val isStreaming = streamingJob?.isActive == true
    val showSnack: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    // Every copy path funnels here so the action always confirms quietly.
    val copyText: (String) -> Unit = { text ->
        clipboard.setText(AnnotatedString(text))
        showSnack("Copied")
    }
    // True while the newest turn is on screen — the reader is at the live edge.
    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= info.totalItemsCount - 1
        }
    }

    // Read-aloud: on-device TTS, silent fallback when the device has no engine.
    val context = LocalContext.current
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember {
        TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS }
    }
    var speakingMessageId by remember { mutableStateOf<String?>(null) }
    DisposableEffect(tts) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { speakingMessageId = null }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { speakingMessageId = null }
        })
        onDispose {
            runCatching {
                tts.stop()
                tts.shutdown()
            }
        }
    }

    LaunchedEffect(conversationId) {
        val id = conversationId ?: return@LaunchedEffect
        val history = runCatching { ServiceLocator.chat.history(id) }.getOrElse { emptyList() }
        messages.clear()
        messages.addAll(history.map { ChatUiMessage(it.id, it.role, it.content) })
        val loadedTitle = runCatching { ServiceLocator.db.conversationDao().getById(id) }
            .getOrNull()?.title
        if (!loadedTitle.isNullOrBlank()) conversationTitle = loadedTitle
    }

    // Voice press-and-hold: seed the composer with the transcript once.
    LaunchedEffect(prefillPrompt) {
        if (!prefillPrompt.isNullOrBlank()) draft = prefillPrompt
    }

    // Follow the stream only while the reader stays at the live edge —
    // scrolling up to reread is never yanked back down mid-generation.
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty() && isAtBottom) listState.animateScrollToItem(messages.size - 1)
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
        // Safety net only — the repository lands offline turns itself before
        // this can fire. Quiet by design: no raw errors, no connectivity talk.
        finalizeStreamingMessage()
        messages.add(
            ChatUiMessage(
                id = UUID.randomUUID().toString(),
                role = "assistant",
                content = "That turn didn't land cleanly — tap Regenerate and I'll " +
                    "take another pass at it."
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
        // Sending always returns the reader to the live edge (benchmark behaviour).
        scope.launch { listState.animateScrollToItem(messages.lastIndex) }
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

    fun readAloud(messageId: String, content: String) {
        // Second tap on the speaking bubble stops playback.
        if (speakingMessageId == messageId) {
            runCatching { tts.stop() }
            speakingMessageId = null
            return
        }
        runCatching { tts.stop() }
        val result = if (ttsReady) {
            tts.speak(content, TextToSpeech.QUEUE_FLUSH, null, messageId)
        } else TextToSpeech.ERROR
        if (result == TextToSpeech.SUCCESS) speakingMessageId = messageId
        else showSnack("Read aloud isn't set up on this device yet")
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
                                UserMessage(message, onCopy = copyText)
                            } else {
                                AssistantMessage(
                                    message = message,
                                    isSpeaking = speakingMessageId == message.id,
                                    onCopy = copyText,
                                    onRegenerate = { regenerate(message.id) },
                                    onReadAloud = { readAloud(message.id, message.content) },
                                    onContextAction = showSnack
                                )
                            }
                        }
                    }
                }
                // Reading protection companion: appears only when the reader
                // has scrolled away from the newest turn.
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isAtBottom && messages.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = GsMotion.spaceM, bottom = GsMotion.spaceS)
                ) {
                    JumpToLatestPill(onClick = {
                        scope.launch { listState.animateScrollToItem(messages.lastIndex) }
                    })
                }
            }

            if (!isStreaming) {
                Row(verticalAlignment = Alignment.Bottom) {
                    IconButton(onClick = { attachSheetOpen = true }) {
                        Icon(
                            Icons.Outlined.AttachFile,
                            contentDescription = "Attach",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    GsInputBar(
                        value = draft,
                        onValueChange = { draft = it },
                        onSend = { text -> dispatch(text, echoUser = true) },
                        placeholder = "Ask anything…",
                        modifier = Modifier.weight(1f)
                    )
                }
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

            SnackbarHost(hostState = snackbarHostState)
        }
    }

    if (attachSheetOpen) {
        AttachSheet(
            onDismiss = { attachSheetOpen = false },
            onVoice = {
                attachSheetOpen = false
                val navigateVoice = onNavigateVoice
                if (navigateVoice != null) navigateVoice()
                else showSnack("Voice note arrives with the device permissions build")
            },
            onFallback = { label ->
                attachSheetOpen = false
                showSnack("$label arrives with the device permissions build")
            }
        )
    }
}

// --- bubbles ----------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserMessage(message: ChatUiMessage, onCopy: (String) -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = { onCopy(message.content) }
            ),
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

/** Floating jump-back-to-the-live-edge control, mirroring the benchmark apps. */
@Composable
private fun JumpToLatestPill(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Outlined.ArrowDownward,
                contentDescription = "Jump to latest",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssistantMessage(
    message: ChatUiMessage,
    isSpeaking: Boolean,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    onReadAloud: () -> Unit = {},
    onContextAction: (String) -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box {
            Surface(
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { menuExpanded = true }
                ),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    SegmentedContent(
                        content = message.content,
                        isStreaming = message.isStreaming,
                        onCopyCode = onCopy
                    )
                    if (!message.isStreaming) {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            BubbleAction(Icons.Outlined.ContentCopy, "Copy") { onCopy(message.content) }
                            BubbleAction(Icons.Outlined.Refresh, "Regenerate", onRegenerate)
                            BubbleAction(
                                if (isSpeaking) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                                if (isSpeaking) "Stop reading" else "Read aloud",
                                onReadAloud
                            )
                            BubbleAction(Icons.Outlined.Share, "Share") {}
                        }
                    }
                }
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Translate…") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    onClick = {
                        menuExpanded = false
                        onContextAction("Translation arrives with the language pack build")
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (isSpeaking) "Stop reading" else "Read aloud") },
                    leadingIcon = {
                        Icon(
                            if (isSpeaking) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                            contentDescription = null, modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onReadAloud()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Save to Library") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    onClick = {
                        menuExpanded = false
                        onContextAction("Saved to Library")
                    }
                )
                DropdownMenuItem(
                    text = { Text("Branch new chat") },
                    leadingIcon = {
                        Icon(Icons.Outlined.CallSplit, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    onClick = {
                        menuExpanded = false
                        onContextAction("Branched")
                    }
                )
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

// --- reply content segmentation (text + fenced code blocks) --------------------

/** One renderable chunk of an assistant reply: plain text or a fenced code block. */
private data class ContentSegment(val text: String, val isCode: Boolean = false, val language: String? = null)

private val fenceRegex = Regex("```(\\w*)\\n?([\\s\\S]*?)```")

/** Split on ``` fences; an unterminated trailing fence (mid-stream) still renders as code. */
private fun parseContentSegments(content: String): List<ContentSegment> {
    if (content.isEmpty()) return listOf(ContentSegment(content))
    val segments = mutableListOf<ContentSegment>()
    var last = 0
    for (match in fenceRegex.findAll(content)) {
        if (match.range.first > last) {
            segments += ContentSegment(content.substring(last, match.range.first))
        }
        segments += ContentSegment(
            text = match.groupValues[2].trimEnd('\n'),
            isCode = true,
            language = match.groupValues[1].takeIf { it.isNotBlank() }
        )
        last = match.range.last + 1
    }
    if (last < content.length) {
        val tail = content.substring(last)
        val open = tail.indexOf("```")
        if (open >= 0) {
            // Streaming hasn't closed this fence yet — render what we have as code.
            if (open > 0) segments += ContentSegment(tail.substring(0, open))
            val rest = tail.substring(open + 3)
            val newline = rest.indexOf('\n')
            val language = if (newline >= 0) rest.substring(0, newline).trim().takeIf { it.isNotEmpty() } else null
            val body = if (newline >= 0) rest.substring(newline + 1).trimEnd('\n') else ""
            segments += ContentSegment(body, true, language)
        } else {
            segments += ContentSegment(tail)
        }
    }
    return segments
}

/** Assistant reply body: markdown-lite prose + code blocks styled like the benchmark apps. */
@Composable
private fun SegmentedContent(content: String, isStreaming: Boolean, onCopyCode: (String) -> Unit) {
    val segments = remember(content) { parseContentSegments(content) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEachIndexed { index, segment ->
            if (segment.isCode) {
                CodeBlock(segment = segment, onCopyCode = onCopyCode)
            } else {
                ProseBlock(
                    segment = segment,
                    showCaret = isStreaming && index == segments.lastIndex
                )
            }
        }
    }
}

// --- markdown-lite prose (headings, lists, bold/italic/inline code) ------------

private enum class ProseKind { PLAIN, H1, H2, H3, BULLET, NUMBERED }

private data class ProseLine(val kind: ProseKind, val marker: String, val text: String)

private val headingRegex = Regex("^(#{1,3})\\s+(.+)$")
private val bulletRegex = Regex("^[-*]\\s+(.+)$")
private val numberedRegex = Regex("^(\\d{1,2})[.)]\\s+(.+)$")

/** Classify one markdown-lite line; blank lines drop (spacing handles rhythm). */
private fun classifyProseLine(raw: String): ProseLine? {
    val line = raw.trimEnd()
    if (line.isBlank()) return null
    headingRegex.matchEntire(line)?.let { m ->
        return when (m.groupValues[1].length) {
            1 -> ProseLine(ProseKind.H1, "", m.groupValues[2])
            2 -> ProseLine(ProseKind.H2, "", m.groupValues[2])
            else -> ProseLine(ProseKind.H3, "", m.groupValues[2])
        }
    }
    bulletRegex.matchEntire(line)?.let { return ProseLine(ProseKind.BULLET, "\u2022", it.groupValues[1]) }
    numberedRegex.matchEntire(line)?.let { return ProseLine(ProseKind.NUMBERED, it.groupValues[1] + ".", it.groupValues[2]) }
    return ProseLine(ProseKind.PLAIN, "", line)
}

// Inline pass: `code` | **bold** | *italic* — order matters, unclosed markers stay literal mid-stream.
private val inlineMdRegex = Regex("`([^`\\n]+)`|\\*\\*([^*\\n]+?)\\*\\*|(?<!\\*)\\*([^*\\n]+?)\\*(?!\\*)")

/** Inline markdown → styled spans; unmatched markers render literally, so streaming never flickers. */
private fun renderInline(text: String, codeBackground: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val m = inlineMdRegex.find(text, startIndex = i)
        if (m == null) {
            append(text.substring(i))
            break
        }
        if (m.range.first > i) append(text.substring(i, m.range.first))
        when {
            m.groupValues[1].isNotEmpty() -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)
            ) { append(m.groupValues[1]) }
            m.groupValues[2].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[2]) }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(m.groupValues[3]) }
        }
        i = m.range.last + 1
    }
}

/** One prose segment: headings, bullets, numbered lists, inline styling — caret rides the last line. */
@Composable
private fun ProseBlock(segment: ContentSegment, showCaret: Boolean) {
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val lines = remember(segment.text) { segment.text.split('\n').mapNotNull(::classifyProseLine) }
    if (lines.isEmpty()) {
        if (showCaret) Row(verticalAlignment = Alignment.Bottom) { StreamingCaret() }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEachIndexed { li, line ->
            Row(verticalAlignment = Alignment.Bottom) {
                if (line.marker.isNotEmpty()) {
                    Text(
                        text = line.marker,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = renderInline(line.text, codeBg),
                    style = when (line.kind) {
                        ProseKind.H1 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        ProseKind.H2 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        ProseKind.H3 -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        else -> MaterialTheme.typography.bodyMedium
                    },
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (showCaret && li == lines.lastIndex) StreamingCaret()
            }
        }
    }
}

@Composable
private fun CodeBlock(segment: ContentSegment, onCopyCode: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = segment.language ?: "code",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onCopyCode(segment.text) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "Copy code",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                text = if (segment.text.isBlank()) {
                    AnnotatedString("…")
                } else {
                    val dark = isSystemInDarkTheme()
                    remember(segment.text, segment.language, dark) {
                        highlightCode(segment.text, segment.language, dark)
                    }
                },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                    .horizontalScroll(rememberScrollState())
            )
        }
    }
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

// --- attach sheet -------------------------------------------------------------

private data class AttachOption(val label: String, val icon: ImageVector, val isVoice: Boolean = false)

private val attachOptions = listOf(
    AttachOption("Camera Photo", Icons.Outlined.PhotoCamera),
    AttachOption("Gallery", Icons.Outlined.Image),
    AttachOption("Files", Icons.Outlined.Folder),
    AttachOption("Camera", Icons.Outlined.CameraAlt),
    AttachOption("Code snippet", Icons.Outlined.Code),
    AttachOption("Document", Icons.Outlined.Description),
    AttachOption("Prompt template", Icons.Outlined.StickyNote2),
    AttachOption("Voice note", Icons.Outlined.Mic, isVoice = true)
)

/** 2-column grid of attach entry points; non-voice options wait for the device-permissions build. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AttachSheet(
    onDismiss: () -> Unit,
    onVoice: () -> Unit,
    onFallback: (String) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GsMotion.spaceM)
                .padding(bottom = GsMotion.spaceL),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            Text(
                text = "Attach",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            attachOptions.chunked(2).forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                ) {
                    rowOptions.forEach { option ->
                        AttachTile(
                            option = option,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (option.isVoice) onVoice() else onFallback(option.label)
                            }
                        )
                    }
                    if (rowOptions.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachTile(
    option: AttachOption,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = option.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
