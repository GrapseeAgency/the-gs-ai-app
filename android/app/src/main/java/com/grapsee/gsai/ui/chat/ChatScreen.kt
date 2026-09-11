package com.grapsee.gsai.ui.chat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.view.HapticFeedbackConstants
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.ModelPrefs
import com.grapsee.gsai.data.chat.ChatStreamController
import com.grapsee.gsai.data.model.ModelCatalog
import com.grapsee.gsai.data.repository.ChatRepository
import com.grapsee.gsai.di.ServiceLocator
import androidx.activity.compose.BackHandler
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsEmptyState
import com.grapsee.gsai.ui.components.GsOfflineBanner
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.data.tts.TtsFocus
import com.grapsee.gsai.ui.components.GsInputBar
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.components.rememberDeviceOffline
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.GsRadius
import com.grapsee.gsai.ui.theme.GsHaptics
import com.grapsee.gsai.ui.chat.content.BlocksContent
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.gsHaptic
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import com.grapsee.gsai.ui.theme.auroraBackground
import java.util.UUID
import kotlinx.coroutines.launch

/** One renderable chat turn. role is "user" or "assistant".
 *  [failed] marks a turn whose stream ended in a controller error (Done+error):
 *  the partial content — if any streamed — stays committed verbatim, and the
 *  surface renders an honest inline system-status notice under the turn instead
 *  of pretending the failure is assistant prose. Failed turns are transient
 *  presentation state: they are never persisted to Room, so a reload shows the
 *  committed partial as an ordinary turn. */
private data class ChatUiMessage(
    val id: String,
    val role: String,
    val content: String,
    val isStreaming: Boolean = false,
    val createdAt: String = "",
    val failed: Boolean = false
)

/**
 * Per-conversation unsent drafts — leave a thread mid-thought, come back,
 * and the composer still holds your words. SharedPreferences storage, silent
 * on any hiccup: drafts are a courtesy, never a crash surface.
 */
private const val DRAFTS_PREFS = "gs_chat_drafts"

/** Scroll-up pagination page size — the newest window opened on every switch,
 *  so conversation-open cost is O(page) no matter how long the thread grew. */
private const val HISTORY_PAGE = 60

private fun draftKey(conversationId: String) = "draft_$conversationId"

private fun loadDraft(context: Context, conversationId: String?): String? =
    conversationId?.let { id ->
        runCatching {
            context.getSharedPreferences(DRAFTS_PREFS, Context.MODE_PRIVATE)
                .getString(draftKey(id), null)
        }.getOrNull()
    }

private fun saveDraft(context: Context, conversationId: String?, draft: String) {
    val id = conversationId ?: return
    runCatching {
        val prefs = context.getSharedPreferences(DRAFTS_PREFS, Context.MODE_PRIVATE)
        if (draft.isBlank()) prefs.edit().remove(draftKey(id)).apply()
        else prefs.edit().putString(draftKey(id), draft).apply()
    }
}

private fun clearDraft(context: Context, conversationId: String?) {
    val id = conversationId ?: return
    runCatching {
        context.getSharedPreferences(DRAFTS_PREFS, Context.MODE_PRIVATE)
            .edit().remove(draftKey(id)).apply()
    }
}

/** The hosting Activity (ContextWrapper-unwrapped) — [Activity.isChangingConfigurations]
 *  is the rotation-vs-navigation discriminator for the stream disposal contract. */
private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Streaming chat surface. History loads from Room (via the repository); sends
 * dispatch through [ChatStreamController] (ServiceLocator.chatStream) — an
 * app-scoped owner that keeps the stream alive across rotation (the recomposed
 * screen re-attaches from the controller state) and cancels + finalizes when
 * the user navigates away. The screen observes the controller state via
 * collectAsState for the live text and the terminal commit; a brand-new chat
 * adopts its server id mid-stream through the controller.
 * Stop-generation cancels through the controller; partial output stays on screen and disk.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    conversationId: String?,
    onBack: () -> Unit,
    // Voice press-and-hold hands its transcript to the composer through this.
    prefillPrompt: String? = null,
    // PHASE 2 Home inline composer: when true, [prefillPrompt] is dispatched
    // immediately on arrival (tap → type → send with no second keypress).
    // One-shot guarded so process death / re-entry never re-sends.
    autoSendInitialPrompt: Boolean = false,
    // Optional voice entry point — the main agent wires this to GsRoutes.VOICE in GsNavHost.
    onNavigateVoice: (() -> Unit)? = null,
    // The model centre exists — the header chip's sheet links there for the
    // full catalogue ("About models"); the send path reads the live default
    // model state this screen already sends with.
    onNavigateModels: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    // Critical surface state survives rotation/process death (Bundle saveable):
    // most importantly the ADOPTED conversation id — after a rotation it used to
    // reset to the nav arg (null for "new"), making the onDispose draft save a
    // silent no-op and orphaning the thread. rememberSaveable's autosaver handles
    // nullable String (null is simply not stored and restores the nav arg).
    var activeConversationId by rememberSaveable { mutableStateOf(conversationId) }
    val messages = remember { mutableStateListOf<ChatUiMessage>() }
    var draft by remember { mutableStateOf("") }
    var conversationTitle by rememberSaveable { mutableStateOf("New chat") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingDraft by remember { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchActiveIndex by remember { mutableStateOf(0) }
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    var attachSheetOpen by rememberSaveable { mutableStateOf(false) }
    // Translate: the source turn rides in the sheet key; the answer streams in live.
    var translationSource by remember { mutableStateOf<String?>(null) }
    var translationText by remember { mutableStateOf("") }
    var translationBusy by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var hasOlder by remember { mutableStateOf(false) }
    var loadingOlder by remember { mutableStateOf(false) }
    // Armed only by a real upward drag — pagination never engages on open.
    var olderArmed by remember { mutableStateOf(false) }
    // App-scoped stream (ChatStreamController owned by ServiceLocator — NOT this
    // composition): rotation kills nothing, navigation away cancels + finalizes.
    // The raw State is read ONLY inside the streaming bubble and the streaming
    // effects; everything screen-wide derives from the phase (coarse — it flips
    // twice per stream), so ~30 Hz text flushes still recompose exactly one bubble.
    val chatStream = ServiceLocator.chatStream
    val streamStateRaw = chatStream.state.collectAsState()
    val isStreaming by remember {
        derivedStateOf { streamStateRaw.value?.phase == ChatStreamController.Phase.Streaming }
    }
    // This screen's live-stream token (saveable): a rotation re-attach only
    // adopts a stream dispatched by this screen's own lineage — a fresh nav
    // entry restores null and never mistakes another surface's stream for its own.
    var myStreamMessageId by rememberSaveable { mutableStateOf<String?>(null) }
    // Set when THIS composition dispatched: the dispatching screen manages its
    // own optimistic bubbles, so it must never reload the transcript mid-stream.
    var dispatchedHere by remember { mutableStateOf(false) }
    // Bumped to force a transcript rebuild (re-attach / adopted-id reconciliation).
    var transcriptLoadTick by remember { mutableStateOf(0) }
    // Which conversation id the transcript currently reflects (re-attach bookkeeping).
    var transcriptLoadedForId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    // System-grade touch confirmation: the same Taptic-lite tick the platform
    // uses for key presses, applied when a send actually commits.
    val view = LocalView.current
    val showSnack: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    // Every copy path funnels here so the action always confirms quietly —
    // with the platform's virtual-key tick, gated by the Haptics setting.
    val copyText: (String) -> Unit = { text ->
        clipboard.setText(AnnotatedString(text))
        view.gsHaptic(HapticFeedbackConstants.VIRTUAL_KEY)
        showSnack("Copied")
    }
    // The system share sheet — devices without a handler stay silent.
    val shareText: (String) -> Unit = { text ->
        runCatching {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(send, null))
        }.onFailure { showSnack("Sharing isn't set up on this device") }
    }
    // True while the newest turn is on screen — the reader is at the live edge.
    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= info.totalItemsCount - 1
        }
    }
    // The banner tracks the phone's actual connectivity (see GsComponents).
    val offline by rememberDeviceOffline()

    // Read-aloud: on-device TTS wrapped in the shared audio-focus discipline —
    // focus is requested (USAGE_MEDIA + CONTENT_TYPE_SPEECH, AUDIOFOCUS_GAIN)
    // before every speak, abandoned on stop/shutdown/utterance-done, and any
    // focus loss (call, navigation prompt, another player) stops playback and
    // resets the speaking state. Silent fallback when the device has no engine.
    // speakingMessageId is declared FIRST: the focus-loss callback below
    // captures it (a lambda cannot reference a local declared later).
    var speakingMessageId by remember { mutableStateOf<String?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember {
        TtsFocus(
            context,
            onReady = { ttsReady = it },
            onStoppedByFocusLoss = { speakingMessageId = null }
        )
    }
    DisposableEffect(tts) {
        tts.engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                tts.abandonFocus()
                speakingMessageId = null
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                tts.abandonFocus()
                speakingMessageId = null
            }
        })
        onDispose {
            tts.shutdown()
        }
    }

    // Draft hand-off: leaving the thread parks the unsent text, re-entering
    // restores it. Cleared the moment a send actually lands.
    DisposableEffect(activeConversationId) {
        onDispose { saveDraft(context, activeConversationId, draft) }
    }

    // Stream disposal contract (rotation vs navigation-away): the stream lives
    // in the app-scoped controller, so a configuration change must leave it
    // running — the recomposed screen re-attaches from the controller state.
    // Navigating away preserves the old product behaviour exactly: cancel, and
    // the repository's NonCancellable path persists the partial to disk.
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity) {
        onDispose {
            if (activity?.isChangingConfigurations != true) chatStream.cancelAndFinalize()
        }
    }

    // Scrolling the transcript puts reading first — the keyboard steps aside.
    // Real drags only: streaming follow-scrolls never steal focus. A real drag
    // also arms scroll-up pagination (user intent, never fired on open).
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                keyboard?.hide()
                olderArmed = true
            }
        }
    }

    LaunchedEffect(conversationId) {
        val id = conversationId ?: return@LaunchedEffect
        olderArmed = false
        val recent = runCatching { ServiceLocator.chat.historyRecent(id, HISTORY_PAGE) }.getOrElse { emptyList() }
        messages.clear()
        messages.addAll(recent.map { ChatUiMessage(it.id, it.role, it.content, createdAt = it.createdAt) })
        hasOlder = recent.size == HISTORY_PAGE
        transcriptLoadedForId = id
        // Rotation re-attach (Task 86-d): an EXISTING-chat rotation re-runs this
        // load while the app-scoped stream is still growing — Room cannot contain
        // the unpersisted answer, so the live streaming bubble is re-attached
        // from the controller state here. (Brand-new chats take the adopted-id
        // path through the stream-observer rebuild instead; whichever load
        // finishes last, exactly one live bubble ends up in the list.)
        chatStream.state.value?.let { live ->
            if (live.phase == ChatStreamController.Phase.Streaming &&
                live.assistantMessageId == myStreamMessageId &&
                live.conversationId == id &&
                messages.none { it.id == live.assistantMessageId && it.isStreaming }
            ) {
                messages.add(
                    ChatUiMessage(
                        live.assistantMessageId, "assistant", "",
                        isStreaming = true, createdAt = ChatRepository.nowIso()
                    )
                )
            }
        }
        // A restored editingId must match a real turn — a dangling id (turn gone
        // after a process death) would silently block every Edit affordance.
        if (editingId != null && messages.none { it.id == editingId }) editingId = null
        val loadedTitle = runCatching { ServiceLocator.db.conversationDao().getById(id) }
            .getOrNull()?.title
        if (!loadedTitle.isNullOrBlank()) conversationTitle = loadedTitle
        if (draft.isBlank()) draft = loadDraft(context, id) ?: ""
    }

    // Transcript rebuild (re-attach / adopted-id reconciliation): triggered by
    // the stream observer below when this composition's transcript does not yet
    // reflect the live stream. Rebuilt OFF-list and swapped once — no empty frame
    // between clear and refill — then the live streaming bubble re-attaches if
    // this screen's lineage owns the app-scoped stream (rotation mid-stream).
    LaunchedEffect(transcriptLoadTick) {
        if (transcriptLoadTick == 0) return@LaunchedEffect
        val id = activeConversationId
        olderArmed = false
        val recent = if (id == null) emptyList()
        else runCatching { ServiceLocator.chat.historyRecent(id, HISTORY_PAGE) }.getOrElse { emptyList() }
        val rebuilt = recent.map { ChatUiMessage(it.id, it.role, it.content, createdAt = it.createdAt) }.toMutableList()
        val live = chatStream.state.value
        if (live?.phase == ChatStreamController.Phase.Streaming &&
            live.assistantMessageId == myStreamMessageId &&
            rebuilt.none { it.id == live.assistantMessageId }
        ) {
            rebuilt += ChatUiMessage(
                live.assistantMessageId, "assistant", "",
                isStreaming = true, createdAt = ChatRepository.nowIso()
            )
        }
        messages.clear()
        messages.addAll(rebuilt)
        hasOlder = recent.size == HISTORY_PAGE
        transcriptLoadedForId = id
        if (editingId != null && messages.none { it.id == editingId }) editingId = null
        if (id != null) {
            val loadedTitle = runCatching { ServiceLocator.db.conversationDao().getById(id) }
                .getOrNull()?.title
            if (!loadedTitle.isNullOrBlank()) conversationTitle = loadedTitle
        }
    }

    // Scroll-up pagination: prepend one older page and restore the reader's
    // position by index shift (keyed items keep identity, offsets stay put).
    // Local-only read — paging up never waits on the network.
    fun loadOlder() {
        val id = activeConversationId ?: return
        if (loadingOlder || !hasOlder || messages.isEmpty()) return
        loadingOlder = true
        scope.launch {
            runCatching { ServiceLocator.chat.historyBefore(id, messages.first().createdAt, HISTORY_PAGE) }
                .onSuccess { older ->
                    if (older.isEmpty()) {
                        hasOlder = false
                    } else {
                        val anchor = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                        messages.addAll(0, older.map { ChatUiMessage(it.id, it.role, it.content, createdAt = it.createdAt) })
                        listState.scrollToItem(anchor.first + older.size, anchor.second)
                        hasOlder = older.size == HISTORY_PAGE
                    }
                }
            loadingOlder = false
        }
    }

    // Armed by an actual drag: approaching the top then pulls one older page.
    LaunchedEffect(listState, olderArmed) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                if (olderArmed && index <= 1) loadOlder()
            }
    }

    // Voice press-and-hold: seed the composer with the transcript once.
    LaunchedEffect(prefillPrompt) {
        if (!prefillPrompt.isNullOrBlank()) draft = prefillPrompt
    }

    // Follow the stream only while the reader stays at the live edge —
    // scrolling up to reread is never yanked back down mid-generation. One
    // long-lived snapshotFlow (reading the controller-backed live text length
    // and the transcript size) replaces per-chunk-keyed effects; scrollToItem
    // is instantaneous by design — an animated follow gets cancelled and
    // relaunched on every chunk anyway.
    LaunchedEffect(Unit) {
        snapshotFlow {
            Triple(
                streamStateRaw.value?.phase,
                streamStateRaw.value?.streamText?.length ?: 0,
                messages.size
            )
        }.collect { (phase, _, count) ->
            if (phase == ChatStreamController.Phase.Streaming && isAtBottom && count > 0) {
                listState.scrollToItem(count - 1)
            }
        }
    }

    /** Commits the streamed answer into the transcript exactly once. Driven by
     *  the controller's terminal phase (Done / Cancelled) — idempotent: only a
     *  still-streaming bubble carrying the controller's message id commits, so
     *  the Finalizing→Done conflation or a re-observed terminal state never
     *  double-commits. Fires ONE commit-time accessibility announcement per
     *  stream for TalkBack ("Reply complete"; "Reply failed" on the error path)
     *  — deliberately a commit-time announcement, never a liveRegion on the
     *  30 Hz updating text. [86-d: declared BEFORE the collector below —
     *  Kotlin resolves local declarations in order, so a lambda that runs later
     *  still cannot reference a local fun declared after it.] */
    fun commitStreamResult(
        state: ChatStreamController.StreamState,
        announcement: String = "Reply complete"
    ) {
        val index = messages.indexOfFirst { it.id == state.assistantMessageId && it.isStreaming }
        if (index < 0) return
        if (state.streamText.isBlank()) messages.removeAt(index)
        else {
            messages[index] = messages[index].copy(content = state.streamText, isStreaming = false)
            view.announceForAccessibility(announcement)
        }
    }

    /** Honest failure state: the partial content stays committed exactly as a
     *  cancelled stream would leave it, and the failed turn carries an inline
     *  system-status notice (FailedTurnNotice) with a real Regenerate action.
     *  NOTHING is ever written as assistant prose here — a failure is a system
     *  status, not a fabricated reply. A Done+error with a blank stream still
     *  removes the empty bubble (commitStreamResult); the notice then stands
     *  alone as its own lightweight row so Regenerate stays reachable. */
    fun reportStreamFailure(state: ChatStreamController.StreamState) {
        commitStreamResult(state, announcement = "Reply failed")
        if (state.streamText.isBlank()) {
            messages.add(
                ChatUiMessage(
                    id = UUID.randomUUID().toString(),
                    role = "assistant",
                    content = "",
                    failed = true,
                    createdAt = ChatRepository.nowIso()
                )
            )
        } else {
            val index = messages.indexOfFirst { it.id == state.assistantMessageId }
            if (index >= 0) messages[index] = messages[index].copy(failed = true)
        }
    }

    // App-scoped stream observation — the screen's single reaction point to the
    // controller: adopts the controller's conversation id (pending→adopted; the
    // controller's id wins when a restored saveable still holds the nav arg),
    // rebuilds the transcript for re-attach, and commits exactly once at the
    // terminal phase. All snapshot writes happen inside the collector on Main —
    // never during composition.
    LaunchedEffect(Unit) {
        chatStream.state.collect { state ->
            when {
                state == null -> Unit
                state.phase == ChatStreamController.Phase.Streaming &&
                        state.assistantMessageId == myStreamMessageId -> {
                    if (state.conversationId != activeConversationId) {
                        activeConversationId = state.conversationId
                    }
                    // The dispatching screen keeps its optimistic bubbles; a
                    // re-attached composition reloads when its transcript does
                    // not yet reflect the live stream — a conversation the
                    // transcript never loaded (fresh nav entry / adopted id) OR
                    // a missing live bubble (existing-chat rotation: the Room
                    // page the nav-arg effect loads cannot contain the answer
                    // while it is still unpersisted). The rebuild re-attaches
                    // the live bubble from the controller state in both cases.
                    if (!dispatchedHere &&
                        (state.conversationId != transcriptLoadedForId ||
                            messages.none { it.id == state.assistantMessageId && it.isStreaming })
                    ) {
                        transcriptLoadTick++
                    }
                }
                state.phase == ChatStreamController.Phase.Done && state.error != null ->
                    reportStreamFailure(state)
                state.phase == ChatStreamController.Phase.Done ||
                        state.phase == ChatStreamController.Phase.Cancelled ->
                    commitStreamResult(state)
            }
        }
    }

    fun dispatch(text: String, echoUser: Boolean) {
        val prompt = text.trim()
        if (prompt.isEmpty() || chatStream.isStreaming) return
        // A committed send gets the platform's virtual-key tick — the touch
        // confirmation native keyboards and dial pads use, gated by Settings.
        view.gsHaptic(HapticFeedbackConstants.VIRTUAL_KEY)
        draft = ""
        clearDraft(context, activeConversationId)
        if (echoUser) {
            messages.add(ChatUiMessage(UUID.randomUUID().toString(), "user", prompt, createdAt = ChatRepository.nowIso()))
        }
        if (activeConversationId == null) conversationTitle = prompt.take(40)
        val assistantId = UUID.randomUUID().toString()
        messages.add(ChatUiMessage(assistantId, "assistant", "", isStreaming = true, createdAt = ChatRepository.nowIso()))
        // Sending always returns the reader to the live edge (benchmark behaviour).
        scope.launch { listState.animateScrollToItem(messages.lastIndex) }
        dispatchedHere = true
        myStreamMessageId = assistantId
        // The stream lives in the app-scoped controller: this composition only
        // dispatches and observes. The user turn is persisted inside the
        // repository BEFORE the network stream opens, the resolved conversation
        // id is published the moment it is known, and the terminal phase drives
        // the commit through the observer above — rotation mid-stream keeps the
        // answer growing and the recomposed screen re-attaches to it.
        chatStream.start(
            conversationId = activeConversationId,
            prompt = prompt,
            modelId = ModelPrefs.defaultId(context),
            assistantMessageId = assistantId
        )
    }

    // PHASE 2 Home: the inline composer dispatches straight into a new chat —
    // the typed text arrives via the nav args and is sent exactly once. The
    // one-shot guard survives process death (rememberSaveable), so restoring
    // the entry never re-sends the prompt.
    val autoSendConsumed = rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(prefillPrompt, autoSendInitialPrompt) {
        if (autoSendInitialPrompt && !prefillPrompt.isNullOrBlank() && !autoSendConsumed.value) {
            autoSendConsumed.value = true
            dispatch(prefillPrompt, echoUser = true)
        }
    }

    /**
     * Benchmark edit flow: replace a sent user turn — the persisted thread tail
     * (the turn itself and everything after) is dropped, then the edited text
     * streams a fresh reply through the normal dispatch path.
     */
    fun editAndResend(messageId: String, newText: String) {
        val text = newText.trim()
        if (text.isEmpty() || chatStream.isStreaming) return
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 0 || messages[index].role != "user") return
        val conversation = activeConversationId
        if (conversation != null) {
            scope.launch { runCatching { ServiceLocator.chat.truncateFrom(conversation, messageId) } }
        }
        while (messages.size > index) messages.removeAt(messages.size - 1)
        editingId = null
        dispatch(text, echoUser = true)
    }

    fun regenerate(assistantMessageId: String) {
        if (chatStream.isStreaming) return
        val index = messages.indexOfFirst { it.id == assistantMessageId }
        if (index < 0) return
        val userText = messages.subList(0, index).lastOrNull { it.role == "user" }?.content ?: return
        messages.removeAt(index)
        dispatch(userText, echoUser = false)
    }

    /**
     * Benchmark branch-new-chat: the thread up to and including the tapped turn
     * becomes the opening history of a fresh local conversation, and this
     * surface re-bases onto the branch. The original thread stays untouched.
     */
    fun branchFrom(messageId: String) {
        if (chatStream.isStreaming) return
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 0) return
        val turns = messages.subList(0, index + 1)
            .filter { it.content.isNotBlank() }
            .map { listOf(it.role, it.content, it.createdAt) }
        if (turns.isEmpty()) return
        scope.launch {
            runCatching {
                val branchTitle = "Branch: $conversationTitle".take(40)
                val newId = ServiceLocator.chat.branch(branchTitle, turns)
                if (newId.isBlank()) return@runCatching
                activeConversationId = newId
                conversationTitle = branchTitle
                messages.clear()
                messages.addAll(
                    turns.map {
                        ChatUiMessage(UUID.randomUUID().toString(), it[0], it[1], createdAt = it[2])
                    }
                )
                showSnack("Branched to a new chat")
            }.onFailure { showSnack("Couldn't branch right now") }
        }
    }

    /** Real Save-to-Library: the turn lands in the Room saved_items table. */
    fun saveToLibrary(content: String) {
        if (content.isBlank()) return
        scope.launch {
            runCatching { ServiceLocator.chat.saveToLibrary(content) }
                .onSuccess { showSnack("Saved to Library") }
                .onFailure { showSnack("Couldn't save right now") }
        }
    }

    /**
     * Real Translate: the tapped turn streams its translation into a bottom
     * sheet, targeted at the device language. Offline the sheet closes quietly
     * with a soft note — no error surfaces, the thread stays untouched.
     */
    fun translateMessage(content: String) {
        if (translationBusy || content.isBlank()) return
        translationSource = content
        translationText = ""
        translationBusy = true
        val targetLanguage = java.util.Locale.getDefault().displayLanguage.ifBlank { "English" }
        scope.launch {
            // Same discipline as the main stream: one growing buffer — per-chunk
            // concatenation re-allocated the whole prefix on every delta.
            val translated = StringBuilder()
            val result = runCatching {
                ServiceLocator.chat.translate(content, targetLanguage) { delta ->
                    translated.append(delta)
                    translationText = translated.toString()
                }
            }.getOrDefault("")
            translationBusy = false
            if (result.isBlank()) {
                translationSource = null
                showSnack("Translation needs a connection")
            }
        }
    }

    fun readAloud(messageId: String, content: String) {
        // Second tap on the speaking bubble stops playback.
        if (speakingMessageId == messageId) {
            tts.stop()
            speakingMessageId = null
            return
        }
        tts.stop()
        val result = if (ttsReady) {
            tts.speak(content, messageId)
        } else TextToSpeech.ERROR
        if (result == TextToSpeech.SUCCESS) speakingMessageId = messageId
        else showSnack("Read aloud isn't set up on this device yet")
    }

    // Overlay discipline: BACK closes in-screen overlays first — search, then
    // message editing — and only pops the screen once neither is open. The
    // platform convention everywhere; predictive back still governs the pop
    // that remains.
    BackHandler(enabled = searchOpen) {
        searchOpen = false
        searchQuery = ""
        searchActiveIndex = 0
    }
    BackHandler(enabled = editingId != null) { editingId = null }

    // Find-in-chat: case-insensitive matches over the rendered turns, one
    // active hit at a time; prev/next steps the reader through every match.
    val matchIndices by remember {
        derivedStateOf {
            val query = searchQuery.trim()
            if (query.isEmpty()) emptyList()
            else messages.withIndex()
                .filter { it.value.content.contains(query, ignoreCase = true) }
                .map { it.index }
        }
    }
    val activeMatchIndex =
        if (matchIndices.isEmpty()) -1
        else matchIndices[searchActiveIndex.mod(matchIndices.size)]

    fun stepSearch(step: Int) {
        if (matchIndices.isEmpty()) return
        searchActiveIndex = (searchActiveIndex + step).mod(matchIndices.size)
        scope.launch { listState.animateScrollToItem(matchIndices[searchActiveIndex]) }
    }

    // Typing re-anchors on the first hit; closing the bar resets clean.
    LaunchedEffect(searchOpen, searchQuery) {
        if (!searchOpen || searchQuery.isBlank()) return@LaunchedEffect
        if (matchIndices.isNotEmpty()) {
            searchActiveIndex = 0
            listState.animateScrollToItem(matchIndices.first())
        }
    }

    GsScreenScaffold(
        title = conversationTitle,
        onBack = onBack,
        actions = {
            IconButton(onClick = {
                searchOpen = !searchOpen
                if (!searchOpen) {
                    searchQuery = ""
                    searchActiveIndex = 0
                }
            }) {
                Icon(Icons.Outlined.Search, contentDescription = "Search in chat",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            // PHASE 2 model indicator — small, quiet, NAME ONLY. No mode
            // suffix, no technical vocabulary: an ordinary user can ignore it
            // forever. Tapping opens a native bottom sheet with three plain
            // language tiers; the full catalogue stays behind "About models"
            // (never a route exit away from the conversation).
            val activeModel = ModelCatalog.byId(ModelPrefs.defaultId(context))
            var modelSheetOpen by remember { mutableStateOf(false) }
            GsChip(
                text = activeModel?.displayName ?: "Auto",
                selected = false,
                onClick = { modelSheetOpen = true }
            )
            if (modelSheetOpen) {
                ModelPickerSheet(
                    activeId = remember(modelSheetOpen) { ModelPrefs.defaultId(context) },
                    onDismiss = { modelSheetOpen = false },
                    onSelect = { id ->
                        ModelPrefs.setDefaultId(context, id)
                        modelSheetOpen = false
                    },
                    onAboutModels = {
                        modelSheetOpen = false
                        onNavigateModels?.invoke()
                    }
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            // Honest connectivity in the primary surface — same banner as the
            // Chats hub (navigation-bar inset now comes from the scaffold).
            // Already the compact treatment (one quiet line, 16dp icon), so it
            // stays exactly as the shared component renders it.
            GsOfflineBanner(visible = offline)

            AnimatedVisibility(visible = searchOpen, enter = fadeIn(), exit = fadeOut()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Search in chat") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { stepSearch(1) })
                    )
                    Text(
                        text = if (matchIndices.isEmpty()) "No results"
                        else "${searchActiveIndex.mod(matchIndices.size) + 1}/${matchIndices.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    IconButton(onClick = { stepSearch(-1) }, enabled = matchIndices.isNotEmpty()) {
                        Icon(
                            Icons.Outlined.KeyboardArrowUp,
                            contentDescription = "Previous match",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { stepSearch(1) }, enabled = matchIndices.isNotEmpty()) {
                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            contentDescription = "Next match",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        searchOpen = false
                        searchQuery = ""
                        searchActiveIndex = 0
                    }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Close search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (messages.isEmpty()) {
                    StarterPrompts(onPick = { dispatch(it, echoUser = true) })
                } else {
                    LazyColumn(
                        state = listState,
                        // imeNestedScroll: dragging the transcript hands the
                        // gesture to the IME — the keyboard tracks the finger
                        // downward exactly like the platform chat apps.
                        modifier = Modifier
                            .fillMaxSize()
                            .imeNestedScroll(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(
                            messages,
                            key = { _, message -> message.id },
                            // User, assistant and failed-status rows have
                            // structurally different layouts — declaring
                            // contentType lets Compose reuse the right slot
                            // when items scroll through the viewport.
                            contentType = { _, message ->
                                if (message.failed) "failed" else message.role
                            }
                        ) { index, message ->
                            // ISO stamps parse once per message and stay cached —
                            // row recompositions (search highlights, edit states,
                            // pagination prepends) never re-parse dates.
                            val prevCreatedAt = if (index > 0) messages[index - 1].createdAt else ""
                            val dayHeader = remember(message.createdAt, prevCreatedAt, index) {
                                val stamp = dayLabel(message.createdAt)
                                if (stamp != null &&
                                    (index == 0 || dayKey(prevCreatedAt) != dayKey(message.createdAt))
                                ) stamp else null
                            }
                            if (dayHeader != null) DaySeparator(dayHeader!!)
                            // Turn-group identity: the assistant identity row
                            // renders only when the previous VISIBLE turn is not
                            // an assistant turn — a day separator resets the
                            // group. Failed-status rows are system notices, not
                            // assistant turns, and never suppress the next
                            // group's identity.
                            val prevMessage = if (index > 0) messages[index - 1] else null
                            val assistantGroupStart = message.role == "assistant" && !message.failed &&
                                (prevMessage == null || prevMessage.failed ||
                                    prevMessage.role != "assistant" || dayHeader != null)
                            if (message.failed && message.content.isBlank()) {
                                // A failed stream with nothing committed: the
                                // empty bubble is already gone, so the honest
                                // system status stands alone, Regenerate attached.
                                FailedTurnNotice(
                                    hasPartial = false,
                                    onRegenerate = { regenerate(message.id) }
                                )
                            } else if (message.role == "user") {
                                UserMessage(
                                    message = message,
                                    isEditing = editingId == message.id,
                                    highlight = index == activeMatchIndex,
                                    // Draft text travels as a reader lambda: typing
                                    // while editing re-renders only the bubble being
                                    // edited, never every visible row.
                                    editingDraft = { editingDraft },
                                    editEnabled = !isStreaming && editingId == null,
                                    onEditingDraftChange = { editingDraft = it },
                                    onEditStart = { editingDraft = message.content; editingId = message.id },
                                    onEditCancel = { editingId = null },
                                    onEditSubmit = { editAndResend(message.id, editingDraft) },
                                    onCopy = copyText
                                )
                            } else {
                                AssistantMessage(
                                    message = message,
                                    isSpeaking = speakingMessageId == message.id,
                                    highlight = index == activeMatchIndex,
                                    showIdentity = assistantGroupStart,
                                    // The growing answer is read inside the bubble
                                    // (controller State), so mid-stream flushes
                                    // touch exactly one item instead of the whole
                                    // screen.
                                    live = if (message.isStreaming) streamStateRaw else null,
                                    onCopy = copyText,
                                    onShare = { shareText(message.content) },
                                    onRegenerate = { regenerate(message.id) },
                                    onReadAloud = { readAloud(message.id, message.content) },
                                    onBranch = { branchFrom(message.id) },
                                    onSaveToLibrary = { saveToLibrary(message.content) },
                                    onTranslate = { translateMessage(message.content) }
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

            // The composer NEVER disappears while streaming: the send slot
            // inside the input bar becomes the stop control (stop lives exactly
            // where send always is), and the aurora life-sign sits directly on
            // top of the composer row — the active state is obvious without a
            // full-width bar replacing the composer.
            if (isStreaming) AuroraIndicator()
            ComposerRow(
                draftText = { draft },
                onDraftChange = { draft = it },
                onSend = { text -> dispatch(text, echoUser = true) },
                onAttach = { attachSheetOpen = true },
                // Real voice entry right in the composer; hidden — never
                // disabled-dead — when no voice route was wired, and the attach
                // sheet keeps its own honest Voice option as the fallback path.
                onMic = onNavigateVoice,
                isStreaming = isStreaming,
                onStop = {
                    // Cancelling finalizes in the controller: the cancelled
                    // repository job persists the partial (NonCancellable)
                    // and the Cancelled phase commits the full buffered text
                    // through the observer — same contract as before.
                    chatStream.cancelAndFinalize()
                },
                enterToSend = SettingsStore.enterToSend
            )

            SnackbarHost(hostState = snackbarHostState)
        }
    }

    translationSource?.let { source ->
        TranslationSheet(
            source = source,
            translated = translationText,
            busy = translationBusy,
            onDismiss = {
                if (!translationBusy) translationSource = null
            },
            onCopy = copyText
        )
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
private fun UserMessage(
    message: ChatUiMessage,
    highlight: Boolean = false,
    isEditing: Boolean = false,
    editingDraft: () -> String = { "" },
    editEnabled: Boolean = true,
    onEditingDraftChange: (String) -> Unit = {},
    onEditStart: () -> Unit = {},
    onEditCancel: () -> Unit = {},
    onEditSubmit: () -> Unit = {},
    onCopy: (String) -> Unit = {}
) {
    val haptics = LocalHapticFeedback.current
    Column(modifier = Modifier.fillMaxWidth()) {
        if (isEditing) {
            // Tapping Edit must land the reader in the field: the requester
            // focuses the TextField after composition (which also opens the
            // keyboard) and the explicit bring-into-view scrolls the edited
            // row clear of the keyboard inside the transcript LazyColumn.
            val editFocus = remember { FocusRequester() }
            val editInView = remember { BringIntoViewRequester() }
            LaunchedEffect(isEditing) {
                if (isEditing) {
                    runCatching { editInView.bringIntoView() }
                    editFocus.requestFocus()
                }
            }
            Column {
                OutlinedTextField(
                    value = editingDraft(),
                    onValueChange = onEditingDraftChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(editInView)
                        .focusRequester(editFocus),
                    minLines = 2,
                    maxLines = 6,
                    label = { Text("Edit message") }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onEditCancel) { Text("Cancel") }
                    Button(
                        onClick = onEditSubmit,
                        enabled = editingDraft().isNotBlank()
                    ) { Text("Save & resend") }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    modifier = Modifier
                        // Long user turns wrap inside a readable column instead
                        // of spanning the full width edge to edge.
                        .widthIn(max = 340.dp)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                if (GsHaptics.enabled()) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCopy(message.content)
                            }
                        ),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary.copy(
                        alpha = if (highlight) 0.34f else 0.14f
                    )
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
            val stamp = remember(message.createdAt) { timeLabel(message.createdAt) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (stamp.isNotEmpty()) {
                    Text(
                        text = stamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                BubbleAction(Icons.Outlined.ContentCopy, "Copy") { onCopy(message.content) }
                if (editEnabled) {
                    BubbleAction(Icons.Outlined.Edit, "Edit", onEditStart)
                }
            }
        }
    }
}

/**
 * Composer row isolated into its own recompose scope: keystrokes re-render
 * this and only this — the transcript, scaffold and snackbar never react.
 *
 * STEP 4 hierarchy: [attach] [expanding input] [mic] [send/stop]. The send/
 * stop slot lives INSIDE GsInputBar's trailing slot — while a stream is live
 * that slot hands over to the accent-outline stop control, so the composer
 * never disappears behind a full-width bar and stop is exactly where send
 * always is. The mic is a real voice entry ([onMic]); it is hidden — not left
 * disabled-dead — when no voice route was wired, and the attach sheet keeps
 * its own Voice option as the honest fallback.
 */
@Composable
private fun ComposerRow(
    draftText: () -> String,
    onDraftChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onAttach: () -> Unit,
    onMic: (() -> Unit)?,
    isStreaming: Boolean,
    onStop: () -> Unit,
    enterToSend: Boolean
) {
    Row(verticalAlignment = Alignment.Bottom) {
        IconButton(onClick = onAttach) {
            Icon(
                Icons.Outlined.AttachFile,
                contentDescription = "Attach",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        GsInputBar(
            value = draftText(),
            onValueChange = onDraftChange,
            onSend = onSend,
            placeholder = "Ask anything…",
            modifier = Modifier.weight(1f),
            imeAction = if (enterToSend) ImeAction.Send else ImeAction.Default,
            // Native multiline composer: one line at rest, six at most — past
            // that the field scrolls internally instead of eating the
            // transcript. (The shared bar keeps its old 4-line default for the
            // search/library/vision/research call sites.)
            maxLines = 6,
            trailingIcon = if (isStreaming) {
                { StopGeneratingControl(onStop = onStop) }
            } else null
        )
        if (onMic != null) {
            IconButton(onClick = onMic) {
                Icon(
                    Icons.Outlined.Mic,
                    contentDescription = "Dictate with voice",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** Accent-outline stop control living in the composer's send slot — the
 *  streaming-active affordance, semantically "Stop generating". */
@Composable
private fun StopGeneratingControl(onStop: () -> Unit) {
    Surface(
        onClick = onStop,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .padding(end = 4.dp)
            .size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.Stop,
                contentDescription = "Stop generating",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
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

/**
 * One assistant turn — DOCUMENT style. The answer is not a card: content sits
 * directly on the app background at the scaffold's reading column (the shell
 * clamps the whole surface to GsLayout.contentMaxWidth, so tablets get the
 * reading column and phones full width minus padding). Identity is a compact
 * avatar+name row rendered once per turn GROUP ([showIdentity] — see the
 * grouping logic at the call site), not a badge on every turn. Actions are a
 * quiet icon row BELOW the content (Copy / Regenerate / Read aloud / Share;
 * Translate / Save-to-Library / Branch stay in the long-press menu — all
 * real). The search-hit treatment tints the content field, replacing the old
 * card-border highlight. Mid-stream the visible answer comes from the
 * app-scoped controller state (read below, so only this bubble recomposes on
 * flush); once finalized the committed content wins. The State carries a
 * NULLABLE snapshot — collectAsState on StateFlow<StreamState?> yields
 * State<StreamState?>. A failed turn keeps its partial content and renders
 * the honest FailedTurnNotice system status under the action row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssistantMessage(
    message: ChatUiMessage,
    isSpeaking: Boolean,
    highlight: Boolean = false,
    showIdentity: Boolean = true,
    live: State<ChatStreamController.StreamState?>? = null,
    onCopy: (String) -> Unit,
    onShare: () -> Unit = {},
    onRegenerate: () -> Unit,
    onReadAloud: () -> Unit = {},
    onBranch: () -> Unit = {},
    onSaveToLibrary: () -> Unit = {},
    onTranslate: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (showIdentity) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistantAvatar()
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "GS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            // Document-style content: no card container, no border — just the
            // answer on the app background. The content column carries the
            // long-press context menu and (when this turn is the active search
            // hit) a soft accent field instead of the old card border.
            Column(
                modifier = Modifier
                    .background(
                        color = if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        else androidx.compose.ui.graphics.Color.Transparent,
                        shape = GsRadius.smShape()
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            if (GsHaptics.enabled()) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuExpanded = true
                        }
                    )
            ) {
                BlocksContent(
                    content = live?.value?.streamText ?: message.content,
                    isStreaming = message.isStreaming,
                    onCopyCode = onCopy
                )
            }
            if (!message.isStreaming) {
                Spacer(Modifier.height(2.dp))
                // Quiet per-turn action row: timestamp folded in, compact icon
                // actions below the content — per-turn capabilities stay
                // per-turn, without card chrome.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val stamp = remember(message.createdAt) { timeLabel(message.createdAt) }
                    if (stamp.isNotEmpty()) {
                        Text(
                            text = stamp,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    BubbleAction(Icons.Outlined.ContentCopy, "Copy") { onCopy(message.content) }
                    BubbleAction(Icons.Outlined.Refresh, "Regenerate", onRegenerate)
                    BubbleAction(
                        if (isSpeaking) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                        if (isSpeaking) "Stop reading" else "Read aloud",
                        onReadAloud
                    )
                    BubbleAction(Icons.Outlined.Share, "Share", onShare)
                }
                if (message.failed) {
                    Spacer(Modifier.height(6.dp))
                    FailedTurnNotice(
                        hasPartial = message.content.isNotBlank(),
                        onRegenerate = onRegenerate
                    )
                }
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Translate") },
                leadingIcon = {
                    Icon(Icons.Outlined.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                onClick = {
                    menuExpanded = false
                    onTranslate()
                }
            )
            DropdownMenuItem(
                text = { Text("Save to Library") },
                leadingIcon = {
                    Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                onClick = {
                    menuExpanded = false
                    onSaveToLibrary()
                }
            )
            DropdownMenuItem(
                text = { Text("Branch new chat") },
                leadingIcon = {
                    Icon(Icons.Outlined.CallSplit, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                onClick = {
                    menuExpanded = false
                    onBranch()
                }
            )
        }
    }
}

/** Compact assistant identity mark — small avatar beside the "GS" name in the
 *  turn-group header row (decorative: the name text carries the meaning). */
@Composable
private fun AssistantAvatar() {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(13.dp)
        )
    }
}

/**
 * Honest failure state — a compact inline SYSTEM STATUS under the turn it
 * belongs to: real role framing (a status row with an error marker, never
 * assistant prose), one short factual explanation, and the working Regenerate
 * action wired to the same regenerate() the action row uses. The partial
 * content above it stays committed exactly as streamed.
 */
@Composable
private fun FailedTurnNotice(hasPartial: Boolean, onRegenerate: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Reply failed",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (hasPartial) {
                    "GS stopped before finishing — the part that arrived is kept above."
                } else {
                    "GS couldn't generate a reply for this turn."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onRegenerate) { Text("Regenerate") }
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

/** Aurora life-sign above the input while the model is generating (draw-phase animated). */
@Composable
private fun AuroraIndicator() {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .auroraBackground(shape)
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

/**
 * Real Translate surface: the original turn stays on top for reference while
 * the translation streams in beneath it — Copy hands the result to the
 * clipboard, Close only works once the stream settles.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TranslationSheet(
    source: String,
    translated: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GsMotion.spaceM)
                .padding(bottom = GsMotion.spaceL),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Translate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Translation",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                TextButton(onClick = onDismiss, enabled = !busy) { Text("Close") }
            }
            Text(
                text = source,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            if (translated.isNotEmpty()) {
                SelectionContainer {
                    Text(
                        text = translated,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                TextButton(
                    onClick = { onCopy(translated) },
                    enabled = !busy,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy translation")
                }
            } else {
                Text(
                    text = "Translating…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

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

// --- date separators ---------------------------------------------------------

/**
 * Centered day pill — the benchmark thread rhythm ("Today", "Yesterday", dates).
 * Anything without a parsable stamp (legacy rows) simply shows no header.
 * Deliberately the QUIETEST element on the surface: labelSmall, soft field —
 * it marks time, it never competes with the transcript.
 */
@Composable
private fun DaySeparator(label: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            // TalkBack: day separators navigate as headings, like section titles.
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 10.dp, vertical = 3.dp)
                .semantics { heading() }
        )
    }
}

// Immutable, thread-safe formatters cached at process level — creating a
// formatter per call re-parses the pattern and re-queries locale data on every
// bubble stamp, on every recomposition of every visible turn.
private val TIME_LABEL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_LABEL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun dayKey(iso: String): String? = runCatching {
    OffsetDateTime.parse(iso).toLocalDate().toString()
}.getOrNull()

/** Quiet per-turn clock in the action row — the benchmark timestamp treatment. */
private fun timeLabel(iso: String): String = runCatching {
    OffsetDateTime.parse(iso).format(TIME_LABEL_FORMAT)
}.getOrDefault("")

private fun dayLabel(iso: String): String? = runCatching {
    val date = OffsetDateTime.parse(iso).toLocalDate()
    val today = OffsetDateTime.now().toLocalDate()
    when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DAY_LABEL_FORMAT)
    }
}.getOrNull()

/**
 * PHASE 2 model picker — a native bottom sheet, three plain-language tiers.
 * No context windows, no speed dots, no reasoning vocabulary: that material
 * lives behind "Advanced details" in the Model Centre. Selecting writes the
 * same ModelPrefs key the send path reads on the very next message, so a
 * normal user can also just keep chatting and never open this at all.
 * "About models" is the only exit into the full catalogue — a quiet link,
 * never a route the conversation pushes the user through.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    activeId: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onAboutModels: () -> Unit
) {
    // Static tiering of the catalogue — remembered once, recomposition-stable.
    val tiers = remember {
        listOf(
            Triple(
                "Fast",
                "Quick answers when speed matters",
                ModelCatalog.all.filter { it.speedTier == "fast" }
            ),
            Triple(
                "Everyday",
                "Smart help for daily questions",
                ModelCatalog.all.filter { it.speedTier == "balanced" }
            ),
            Triple(
                "Best for difficult questions",
                "Takes its time, thinks deeper",
                ModelCatalog.all.filter { it.speedTier == "deep" }
            )
        )
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GsMotion.spaceL)
                .padding(bottom = GsMotion.spaceL),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            Text(
                "Choose a model",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "GS uses your choice from the next message — you can also just keep chatting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            tiers.forEach { (tierTitle, tierSubtitle, models) ->
                Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)) {
                    Text(
                        tierTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        tierSubtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    models.forEach { model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable(onClick = { onSelect(model.id) })
                                .padding(horizontal = GsMotion.spaceS, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    model.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    model.tagline,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (model.id == activeId) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = "Current model",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
            TextButton(onClick = onAboutModels) {
                Text("About models")
            }
        }
    }
}
