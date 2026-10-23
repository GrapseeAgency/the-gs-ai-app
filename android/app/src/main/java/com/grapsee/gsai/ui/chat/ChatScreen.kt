package com.grapsee.gsai.ui.chat

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.grapsee.gsai.data.attachment.AttachmentDraft
import com.grapsee.gsai.data.attachment.AttachmentPhase
import com.grapsee.gsai.data.attachment.AttachmentRules
import com.grapsee.gsai.data.attachment.AttachmentSource
import com.grapsee.gsai.data.attachment.AttachmentStateMachine
import com.grapsee.gsai.data.attachment.asDisplayDraft
import com.grapsee.gsai.data.remote.AttachmentDto
import com.grapsee.gsai.ui.attachment.AttachmentChipRow
import java.io.File
import android.view.HapticFeedbackConstants
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.chat.ChatStreamController
import com.grapsee.gsai.data.chat.ClarifyOption
import com.grapsee.gsai.data.chat.ClarifyPrompt
import com.grapsee.gsai.data.chat.EngineRoundRow
import com.grapsee.gsai.data.chat.ResearchPhaseNote
import com.grapsee.gsai.data.chat.ResearchLedgerView
import com.grapsee.gsai.data.chat.SearchTraceItem
import com.grapsee.gsai.data.chat.SourceCard
import com.grapsee.gsai.data.chat.decodeClarifyPrompt
import com.grapsee.gsai.data.chat.decodeSourceCards
import com.grapsee.gsai.data.repository.ChatRepository
import com.grapsee.gsai.di.ServiceLocator
import androidx.activity.compose.BackHandler
import com.grapsee.gsai.ui.components.GsOfflineBanner
import com.grapsee.gsai.ui.components.AnimatedGsIllustration
import com.grapsee.gsai.ui.components.RefArt
import com.grapsee.gsai.ui.components.RefArtwork
import com.grapsee.gsai.ui.components.RefIconTile
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.data.OnboardingProfileStore
import com.grapsee.gsai.data.notifications.ReplyNotifier
import com.grapsee.gsai.data.tts.TtsFocus
import com.grapsee.gsai.ui.components.GsInputBar
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.gsContentWidth
import com.grapsee.gsai.ui.components.gsGlass
import com.grapsee.gsai.ui.components.rememberDeviceOffline
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.GsRadius
import com.grapsee.gsai.ui.theme.GsHaptics
import com.grapsee.gsai.ui.theme.GsEase
import com.grapsee.gsai.ui.theme.messageAppear
import com.grapsee.gsai.ui.icons.GsIcons
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import com.grapsee.gsai.ui.theme.kineticPress
import com.grapsee.gsai.ui.chat.content.BlocksContent
import com.grapsee.gsai.ui.chat.content.LocalVoiceLauncher
import com.grapsee.gsai.ui.chat.content.NexlyGlassGlyphTile
import com.grapsee.gsai.ui.chat.content.NexlyGlyph
import com.grapsee.gsai.ui.chat.content.NexlyInk
import com.grapsee.gsai.ui.chat.content.NexlyUserAvatar
import com.grapsee.gsai.ui.chat.content.containsNexlyPlan
import com.grapsee.gsai.ui.chat.content.nexlyPearlBackdrop
import com.grapsee.gsai.ui.orbs.OrbSize
import com.grapsee.gsai.ui.orbs.OrbState
import com.grapsee.gsai.ui.orbs.ThinkingOrb
import com.grapsee.gsai.ui.orbs.orbStateForChatStream
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.gsHaptic
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.launch

/** One renderable chat turn. role is "user" or "assistant".
 *  [failed] marks a turn whose stream ended in a controller error (Done+error):
 *  the partial content — if any streamed — stays committed verbatim, and the
 *  surface renders an honest inline system-status notice under the turn instead
 *  of pretending the failure is assistant prose. Failed turns are transient
 *  presentation state: they are never persisted to Room, so a reload shows the
 *  committed partial as an ordinary turn.
 *
 *  perf-4-a (technique D): @Immutable so LazyColumn rows carrying this model
 *  stay skippable — every field is a val of a stable or immutable type; the
 *  DTO list element types carry @Immutable themselves. */
@Immutable
internal data class ChatUiMessage(
    val id: String,
    val role: String,
    val content: String,
    val isStreaming: Boolean = false,
    val createdAt: String = "",
    val failed: Boolean = false,
    /** PHASE 5: real attachment records on the turn (user turns only today). */
    val attachments: List<AttachmentDto> = emptyList(),
    /**
     * PHASE 8.1: the turn's REAL web-search results.
     *  - [sources] survive reload (Room `sources` column → decoded cards).
     *  - [clarify] survives reload (Room `clarifyOptions` column → parsed).
     *  - [traceItems] / [traceSummary] are IN-MEMORY ONLY: the trace detail
     *    exists exactly as long as the process does. After a reload the
     *    collapsed summary is re-derived from the source count — the one thing
     *    actually persisted. Nothing here is ever fabricated for a reload.
     */
    val sources: List<SourceCard> = emptyList(),
    val clarify: ClarifyPrompt? = null,
    val traceItems: List<SearchTraceItem> = emptyList(),
    val traceSummary: String? = null,
    /** PHASE 8.2: research-level trace detail — same IN-MEMORY ONLY discipline:
     *  engine rows, research notes and used citations exist exactly as long as
     *  the received events do; a reload re-derives nothing (honest empty). */
    val engineRows: List<EngineRoundRow> = emptyList(),
    val researchNotes: List<ResearchPhaseNote> = emptyList(),
    val usedCitations: List<Int> = emptyList(),
    /** PHASE 5: the deep-research branch table (`ledger` events) — same
     *  IN-MEMORY ONLY discipline: null for quick/linear turns, gone after a
     *  reload (the source cards + summary stay — never a fabricated table). */
    val ledgerView: ResearchLedgerView? = null
)

/**
 * PHASE 8.1: the collapsed one-line trace summary, derived ONLY from what the
 * wire actually delivered — the terminal `search completed` counts when they
 * exist, the failure line when the search failed, the query count as the
 * honest fallback, and null for a turn that never searched. Used at commit
 * time; after a reload the collapsed line re-derives from the persisted
 * source count (see [SearchTraceCard] wiring below).
 */
private fun traceSummaryOf(state: ChatStreamController.StreamState): String? {
    if (state.traceItems.any { it.kind == SearchTraceItem.Kind.SearchFailed }) {
        return "Search failed"
    }
    state.searchSummary?.let { summary ->
        val parts = mutableListOf("Searched ${summary.queries} ${if (summary.queries == 1) "query" else "queries"}")
        if (summary.sources > 0) parts += "${summary.sources} sources"
        if (summary.retrieved > 0) parts += "${summary.retrieved} read"
        // PHASE 8.2: the collapsed line also carries the research-level truth.
        appendResearchSummaryParts(state, countedRead = summary.retrieved, parts)
        return parts.joinToString(" · ")
    }
    val queryCount = state.traceItems.count { it.kind == SearchTraceItem.Kind.Query }
    if (queryCount > 0) {
        val parts = mutableListOf("Searched $queryCount ${if (queryCount == 1) "query" else "queries"}")
        appendResearchSummaryParts(state, countedRead = 0, parts)
        return parts.joinToString(" · ")
    }
    return null
}

/**
 * PHASE 8.2: appends the research-level summary parts — engine count,
 * read/failed counts and the syndication note — ONLY from received events.
 * [countedRead] suppresses a duplicate "read" when the completed counts
 * already carried exactly that number.
 */
private fun appendResearchSummaryParts(
    state: ChatStreamController.StreamState,
    countedRead: Int,
    parts: MutableList<String>
) {
    val engines = state.engineRows.sumOf { it.engines.size }
    if (engines > 0) parts += "$engines ${if (engines == 1) "engine" else "engines"}"
    val rounds = state.researchPhaseNotes.filterIsInstance<ResearchPhaseNote.RoundSummary>()
    val read = rounds.sumOf { it.read }
    if (read > countedRead) parts += "$read read"
    val failed = rounds.sumOf { it.failed }
    if (failed > 0) parts += "$failed failed"
    val syndicated = rounds.sumOf { it.syndicatedGroups ?: 0 }
    if (syndicated > 0) {
        parts += "$syndicated syndicated ${if (syndicated == 1) "group" else "groups"}"
    }
}

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
    // PHASE 3 workspace model: this screen IS the app root. When true (start
    // destination, nothing beneath it in the stack) the header shows the drawer
    // menu instead of a back arrow and a "+" resets to a fresh workspace.
    atWorkspaceRoot: Boolean = false,
    // Drawer reveal for the workspace root header.
    onOpenDrawer: (() -> Unit)? = null,
    // Reset to a fresh workspace (nav-owned: pops to a single chat/new).
    onNewChat: (() -> Unit)? = null,
    // Voice press-and-hold hands its transcript to the composer through this.
    prefillPrompt: String? = null,
    // Typed-elsewhere prompts (Voice handoff / launcher shortcut): dispatched
    // immediately on arrival. One-shot guarded so process death / re-entry
    // never re-sends.
    autoSendInitialPrompt: Boolean = false,
    // Optional voice entry point — the main agent wires this to GsRoutes.VOICE in GsNavHost.
    onNavigateVoice: (() -> Unit)? = null,
    // Reference "Chat Details" page (info & stats). Null hides the row on
    // surfaces that are embedded without a navigation host.
    onOpenChatDetails: ((String) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    // Critical surface state survives rotation/process death (Bundle saveable):
    // most importantly the ADOPTED conversation id — after a rotation it used to
    // reset to the nav arg (null for "new"), making the onDispose draft save a
    // silent no-op and orphaning the thread. rememberSaveable's autosaver handles
    // nullable String (null is simply not stored and restores the nav arg).
    var activeConversationId by rememberSaveable { mutableStateOf(conversationId) }
    val messages = remember { mutableStateListOf<ChatUiMessage>() }
    // The composer text is surface state like the conversation id and the
    // title, so it saves the same way: rotation (or a process death without
    // ON_STOP) must not eat a half-typed next question. ON_STOP + dispose
    // snapshots additionally park it per conversation, so a backgrounded
    // thread comes back to exactly the draft the reader left.
    var draft by rememberSaveable { mutableStateOf("") }
    // The message parked by the queue affordance while the current turn runs.
    // Remembered-saveable for the same reason as the draft: it is a REAL
    // pending message, and losing it to a rotation would be silent.
    var queued by rememberSaveable { mutableStateOf("") }
    // The parked message mid-flight to the turn that just finished (null =
    // nothing in flight). Kept next to its source so the collector below can
    // write it; the flush effect lives after dispatch, which owns the send.
    var queuedFlush by rememberSaveable { mutableStateOf<String?>(null) }
    // Fresh workspace reads "GS" — the product's quiet identity mark, not a
    // page name. The first exchange re-titles the thread from the prompt (and
    // existing conversations load their stored title below).
    var conversationTitle by rememberSaveable { mutableStateOf("GS") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingDraft by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchActiveIndex by remember { mutableStateOf(0) }
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    var attachSheetOpen by rememberSaveable { mutableStateOf(false) }
    // Conversation options (reference sheet 4/5): Rename / Archive / Delete for
    // the ACTIVE thread. Only meaningful once the conversation exists.
    var chatMenuOpen by rememberSaveable { mutableStateOf(false) }
    // FLASH MODE (Phase 3) — the composer's thinking mode, persisted per
    // conversation in local prefs (SettingsStore), applied to the next send.
    // Default 'flash'; the chip above the composer shows it only when it is
    // not the clean default.
    var chatMode by rememberSaveable { mutableStateOf("flash") }
    LaunchedEffect(activeConversationId) {
        chatMode = SettingsStore.chatMode(activeConversationId)
    }
    // PHASE 5: app-scoped attachment store — the composer's drafts, real
    // staging and real multipart uploads. Process-scoped, so in-flight uploads
    // and the live draft list survive rotation.
    val attachStore = remember { ServiceLocator.attachments }
    val attachDrafts by attachStore.drafts.collectAsStateWithLifecycle()
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
    val streamStateRaw = chatStream.state.collectAsStateWithLifecycle()
    val isStreaming by remember {
        derivedStateOf { streamStateRaw.value?.phase == ChatStreamController.Phase.Streaming }
    }
    // Phase-4 activity orb state — derived from the REAL stream state only.
    // It changes twice per stream (no tokens yet ↔ tokens flowing), so the
    // ~30 Hz text flushes recompute this but never recompose anything below.
    // PHASE 6/7: the request's real hasAttachments flag drives the honest
    // pre-first-token mapping (vision analysis / server-side document
    // extraction are real work → WORKING before the first token).
    val liveOrbState by remember {
        derivedStateOf {
            orbStateForChatStream(
                streamStateRaw.value?.phase,
                streamStateRaw.value?.streamText,
                streamStateRaw.value?.hasAttachments == true,
                // PHASE 8.1: the wire's honest search phase (searching/working)
                // drives SEARCHING/WORKING before the first token. Null for a
                // plain turn — the mapping is unchanged without a search.
                streamStateRaw.value?.searchPhase
            )
        }
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
    // PHASE 8.1 + TASK 1: source cards and citation chips open the REAL URL
    // the backend persisted — now through the IN-APP browser (Custom Tabs).
    // The external browser stays the long-press path (SourceCardView), and a
    // quiet honest note when nothing can open it at all.
    val openSource: (String) -> Unit = { url ->
        com.grapsee.gsai.ui.components.GsLinks.openInApp(context, url)
    }
    // PHASE 5 real pickers — system Photo Picker, TakePicture through the
    // existing FileProvider, SAF OpenMultipleDocuments. No storage or camera
    // permission is ever requested: the system surfaces own their permissions,
    // we consume only their results. Picker grants are transient — the store
    // copies bytes into app-private staging immediately (§4).
    val remainingSlots = AttachmentRules.remainingSlots(attachDrafts.size)
    var cameraCaptureFile by remember { mutableStateOf<File?>(null) }
    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = AttachmentRules.MAX_PER_MESSAGE)
    ) { uris ->
        if (uris.size > remainingSlots) {
            showSnack("Up to ${AttachmentRules.MAX_PER_MESSAGE} attachments per message")
        }
        uris.take(remainingSlots).forEach { attachStore.stageFromUri(it, AttachmentSource.Gallery) }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val file = cameraCaptureFile
        cameraCaptureFile = null
        if (captured && file != null) attachStore.stageFromCameraFile(file)
    }
    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.size > remainingSlots) {
            showSnack("Up to ${AttachmentRules.MAX_PER_MESSAGE} attachments per message")
        }
        uris.take(remainingSlots).forEach { attachStore.stageFromUri(it, AttachmentSource.Files) }
    }
    fun launchCamera() {
        if (remainingSlots == 0) {
            showSnack("Up to ${AttachmentRules.MAX_PER_MESSAGE} attachments per message")
            return
        }
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "attach-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        cameraCaptureFile = file
        try {
            cameraLauncher.launch(uri)
        } catch (e: ActivityNotFoundException) {
            // No camera app on the device — say so, never fake a capture.
            cameraCaptureFile = null
            showSnack("No camera app available")
        }
    }
    // The store binds to the active conversation: a switch drops or rehydrates
    // that conversation's persisted ready drafts; a rotation re-bind no-ops
    // (same id), so live uploads are never disturbed.
    LaunchedEffect(activeConversationId) { attachStore.bind(activeConversationId) }
    // Background leave also snapshots the composer — the dispose-time save
    // below never runs on process death.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) attachStore.persistSnapshot(activeConversationId, draft)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
        // PHASE 5: the store owns the draft entry {text, attachments} — the
        // leave-time snapshot covers both halves in one atomic write.
        onDispose { attachStore.persistSnapshot(activeConversationId, draft) }
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
        messages.addAll(
            recent.map {
                ChatUiMessage(
                    it.id, it.role, it.content,
                    createdAt = it.createdAt,
                    attachments = ChatRepository.decodeAttachments(it.attachments),
                    // PHASE 8.1: persisted sources/clarifyOptions reload the
                    // turn's citation chips, source cards and quick-choices.
                    sources = decodeSourceCards(it.sources),
                    clarify = decodeClarifyPrompt(it.clarifyOptions)
                )
            }
        )
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
        if (draft.isBlank()) draft = attachStore.loadPersistedText(id) ?: ""
    }

    // A brand-new chat has no id to load history by, but its composer draft is
    // just as real: without this arm, anything typed before the first send
    // vanished on rotate / background / process death — the unsent-message
    // amnesia no native AI app has. The store parks the unsaved draft under
    // its own stable slot (AttachmentStore.UNSAVED_KEY), cleared on send.
    LaunchedEffect(conversationId) {
        if (conversationId == null && draft.isBlank()) {
            draft = attachStore.loadPersistedText(null) ?: ""
        }
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
        val rebuilt = recent.map {
            ChatUiMessage(
                it.id, it.role, it.content,
                createdAt = it.createdAt,
                attachments = ChatRepository.decodeAttachments(it.attachments),
                sources = decodeSourceCards(it.sources),
                clarify = decodeClarifyPrompt(it.clarifyOptions)
            )
        }.toMutableList()
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
                        messages.addAll(
                            0,
                            older.map {
                                ChatUiMessage(
                                    it.id, it.role, it.content,
                                    createdAt = it.createdAt,
                                    attachments = ChatRepository.decodeAttachments(it.attachments),
                                    sources = decodeSourceCards(it.sources),
                                    clarify = decodeClarifyPrompt(it.clarifyOptions)
                                )
                            }
                        )
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
            messages[index] = messages[index].copy(
                content = state.streamText,
                isStreaming = false,
                // PHASE 8.1: the turn's real search artifacts commit with the
                // text — live source cards / clarify prompt move onto the turn,
                // the trace keeps its detail in memory and collapses to a
                // one-line summary (protocol: "after done, the trace collapses;
                // source cards stay"). Whatever the wire actually delivered is
                // what the committed turn shows — nothing more.
                sources = state.sources,
                clarify = state.clarify,
                traceItems = state.traceItems,
                traceSummary = traceSummaryOf(state),
                // PHASE 8.2: the research detail commits with the turn (memory
                // only — a reload honestly shows just the summary + sources).
                engineRows = state.engineRows,
                researchNotes = state.researchPhaseNotes,
                usedCitations = state.usedCitations,
                // PHASE 5: the branch table commits with the turn too — the
                // live ledger snapshot, in-memory only, same honesty rule.
                ledgerView = state.ledgerView
            )
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
                state.phase == ChatStreamController.Phase.Done || state.phase == ChatStreamController.Phase.Cancelled -> {
                    if (state.phase == ChatStreamController.Phase.Done && state.error == null) {
                        // A parked message goes on its own the moment the reply
                        // lands clean. Attachments ride along only while the
                        // composer still holds the staged ones — if the user
                        // changed them after parking, the honest move is to
                        // hand the text back instead of sending it with the
                        // wrong files. The SEND itself is deferred one step:
                        // the controller's terminal publish runs BEFORE its
                        // finally clears the job, so isStreaming still reads
                        // true inside this collector and dispatch would drop
                        // the flush on the floor.
                        val pending = queued
                        if (pending.isNotBlank()) {
                            val attachStill = attachDrafts.toList()
                            queued = ""
                            if (attachStill.isEmpty()) queuedFlush = pending else draft = pending
                        }
                        // The reply is finished. If the user is in ANOTHER app
                        // right now, say so — the stream is app-scoped and has
                        // been running while they were away. The notifier
                        // re-checks foreground/permission/setting itself; this
                        // call is the signal, nothing more.
                        if (SettingsStore.pushNotifications && state.streamText.isNotBlank()) {
                            ReplyNotifier.postReplyReady(
                                context,
                                state.conversationId,
                                state.streamText
                            )
                        }
                    }
                    commitStreamResult(state)
                }
            }
        }
    }

    fun dispatch(text: String, echoUser: Boolean) {
        val prompt = text.trim()
        // PHASE 5: attachments-only sends are real — the text may be empty when
        // the composer holds ready attachments. Anything not yet ready blocks
        // the send honestly; nothing silently drops.
        val readyDrafts = attachDrafts.toList()
        if ((prompt.isEmpty() && readyDrafts.isEmpty()) || chatStream.isStreaming) return
        if (!AttachmentStateMachine.canSend(readyDrafts)) {
            showSnack("Attachments aren't ready yet")
            return
        }
        // A committed send gets the platform's virtual-key tick — the touch
        // confirmation native keyboards and dial pads use, gated by Settings.
        view.gsHaptic(HapticFeedbackConstants.VIRTUAL_KEY)
        draft = ""
        attachStore.clearPersistedDraft(activeConversationId)
        val userAttachments = readyDrafts.map { d ->
            AttachmentDto(
                id = d.remoteId ?: d.id,
                kind = d.kind.wire,
                displayName = d.displayName,
                mimeType = d.mimeType,
                byteSize = d.byteSize,
                createdAt = d.createdAt,
                url = d.remoteUrl ?: ""
            )
        }
        if (echoUser) {
            messages.add(
                ChatUiMessage(
                    UUID.randomUUID().toString(), "user", prompt,
                    createdAt = ChatRepository.nowIso(),
                    attachments = userAttachments
                )
            )
        }
        if (activeConversationId == null) {
            conversationTitle = prompt.ifEmpty { readyDrafts.firstOrNull()?.displayName ?: "GS" }.take(40)
        }
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
            assistantMessageId = assistantId,
            attachments = readyDrafts,
            mode = chatMode
        )
        // The send consumed the drafts: ready staged copies persist on disk
        // (they were uploaded), never-uploaded leftovers are cleaned up (§4).
        attachStore.clear()
    }

    // The parked message waiting for the controller to go idle (null = nothing
    // parked). Deferred by one step on purpose — see the collector's Done
    // branch for why the send cannot happen inside the collector itself.
    LaunchedEffect(queuedFlush) {
        val pending = queuedFlush ?: return@LaunchedEffect
        // Bounded wait for the controller to go idle (its finally runs right
        // after the terminal publish, so this is typically one tick). If it
        // somehow never idles, the text goes back to the composer — a queue
        // must never swallow a message the user wrote.
        var waited = 0
        while (chatStream.isStreaming && waited < 100) {
            kotlinx.coroutines.delay(10)
            waited++
        }
        queuedFlush = null
        if (chatStream.isStreaming) draft = pending else dispatch(pending, true)
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

    // Workspace header: menu (at root) / back (pushed) and the quiet title.
    val menuAction = onOpenDrawer
    val newChatAction = onNewChat
    // design-3-b: the chat top bar is owned here instead of the shared
    // scaffold, so this surface photographs differently: 20sp weight-600 sans
    // title onBackground (no serif tier), 24dp icons in 40dp rounded-square
    // (12dp radius) hit targets, and a 1dp outlineVariant divider that exists
    // only while the transcript is scrolled. Same inset + content-width
    // contract as GsScreenScaffold (status/nav bar padding, gsContentWidth).
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .gsContentWidth()
            .nexlyPearlBackdrop()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        NexlyChatHeader(
            sparkleLabel = "New chat",
            onSparkle = {
                if (newChatAction != null) newChatAction()
                else {
                    searchOpen = !searchOpen
                    if (!searchOpen) {
                        searchQuery = ""
                        searchActiveIndex = 0
                    }
                }
            },
            onSparkleLong = {
                searchOpen = !searchOpen
                if (!searchOpen) {
                    searchQuery = ""
                    searchActiveIndex = 0
                }
            },
            menuIcon = if (atWorkspaceRoot && menuAction != null) GsIcons.Menu else GsIcons.ChevronLeft,
            menuLabel = if (atWorkspaceRoot && menuAction != null) "Open menu" else "Back",
            onMenu = {
                if (atWorkspaceRoot && menuAction != null) menuAction()
                else if (activeConversationId != null) chatMenuOpen = true
                else onBack()
            },
            onMenuLong = if (activeConversationId != null) {
                { chatMenuOpen = true }
            } else null
        )
            if (chatMenuOpen && activeConversationId != null) {
                val activeId = activeConversationId
                ConversationOptionsSheet(
                    title = conversationTitle,
                    onDismiss = { chatMenuOpen = false },
                    onRename = { newTitle ->
                        chatMenuOpen = false
                        conversationTitle = newTitle
                        if (activeId != null) {
                            scope.launch {
                                runCatching { ServiceLocator.chat.rename(activeId, newTitle) }
                            }
                        }
                    },
                    onShare = {
                        chatMenuOpen = false
                        if (activeId != null) {
                            scope.launch {
                                val transcript = runCatching {
                                    ServiceLocator.chat.history(activeId).joinToString("\n\n") { m ->
                                        val who = if (m.role == "user") "You" else "Nexly"
                                        "$who: ${m.content}"
                                    }
                                }.getOrDefault("")
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, conversationTitle)
                                    putExtra(Intent.EXTRA_TEXT, transcript.ifBlank { conversationTitle })
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(send, "Share chat"))
                                }
                            }
                        }
                    },
                    onDetails = if (onOpenChatDetails != null && activeId != null) {
                        {
                            chatMenuOpen = false
                            onOpenChatDetails(activeId)
                        }
                    } else {
                        null
                    },
                    onExport = {
                        chatMenuOpen = false
                        if (activeId != null) {
                            scope.launch {
                                val transcript = runCatching {
                                    ServiceLocator.chat.history(activeId).joinToString("\n\n") { m ->
                                        val who = if (m.role == "user") "You" else "Nexly"
                                        "$who: ${m.content}"
                                    }
                                }.getOrDefault("")
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Nexly — $conversationTitle")
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "# $conversationTitle\n\n" + transcript.ifBlank { "No messages yet." }
                                    )
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(send, "Export chat"))
                                }
                            }
                        }
                    },
                    onClear = {
                        chatMenuOpen = false
                        if (activeId != null) {
                            scope.launch {
                                val first = runCatching {
                                    ServiceLocator.chat.history(activeId).firstOrNull()
                                }.getOrNull()
                                if (first != null) {
                                    runCatching { ServiceLocator.chat.truncateFrom(activeId, first.id) }
                                }
                            }
                        }
                    },
                    onDelete = {
                        chatMenuOpen = false
                        if (activeId != null) {
                            scope.launch {
                                runCatching { ServiceLocator.chat.delete(activeId) }
                            }
                        }
                        onBack()
                    }
                )
            }
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .imePadding()
                .padding(horizontal = GsMotion.spaceM),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            // Honest connectivity in the primary surface — same banner as the
            // Chats hub (navigation-bar inset now comes from the scaffold).
            // Already the compact treatment (one quiet line, 16dp icon), so it
            // stays exactly as the shared component renders it.
            GsOfflineBanner(visible = offline)

            AnimatedVisibility(visible = searchOpen, enter = fadeIn(), exit = fadeOut()) {
                // Opening find-in-chat must land the caret IN the field — the
                // keyboard appearing on open is what a native caller expects,
                // not a second tap on the row itself.
                val searchFocus = remember { FocusRequester() }
                LaunchedEffect(searchOpen) {
                    if (searchOpen) runCatching { searchFocus.requestFocus() }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocus),
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
                            GsIcons.ChevronUp,
                            contentDescription = "Previous match",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { stepSearch(1) }, enabled = matchIndices.isNotEmpty()) {
                        Icon(
                            GsIcons.ChevronDown,
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
                            GsIcons.X,
                            contentDescription = "Close search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (messages.isEmpty()) {
                    // PHASE 3 empty state, uiverse pass — part of the chat
                    // surface, not a separate landing composition: a quiet
                    // greeting, one 3D-press New chat primary, one capability
                    // showcase card, and space. The block steps aside the
                    // moment the reader types; the first real turn replaces
                    // the whole block with the transcript. Same screen
                    // throughout — no navigation hop.
                    WorkspaceEmptyState(
                        draftText = { draft },
                        onNewChat = newChatAction,
                        onSuggestion = { prompt -> dispatch(prompt, echoUser = true) }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        // NO imeNestedScroll().
                        //
                        // It was here to let a downward drag "hand the gesture
                        // to the IME". What it actually did: the composer keeps
                        // focus after a turn, and the IME nested-scroll
                        // connection treats an UPWARD scroll as "bring the
                        // keyboard back" — so reading back through the
                        // transcript popped the keyboard up on every drag. The
                        // hide-on-drag below already gives the reading-first
                        // behaviour, with none of that.
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
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
                                Box(Modifier.messageAppear(message.id)) {
                                    FailedTurnNotice(
                                        hasPartial = false,
                                        onRegenerate = { regenerate(message.id) }
                                    )
                                }
                            } else if (message.role == "user") {
                                Box(Modifier.messageAppear(message.id)) {
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
                                }
                            } else if (
                                // NOTHING TO READ YET — draw nothing.
                                //
                                // Before the first token an assistant turn has
                                // no text, no trace and no sources, so all it
                                // painted was the avatar, the "GS" label and a
                                // bare streaming caret. On a real screen that
                                // reads as a SECOND, empty input bar parked at
                                // the top of the transcript: a monogram, a name
                                // and a cursor. It carries no information the
                                // orb in the composer does not already carry,
                                // so the row waits for its first token.
                                message.isStreaming &&
                                    message.content.isBlank() &&
                                    message.traceItems.isEmpty() &&
                                    message.sources.isEmpty() &&
                                    liveOrbState == OrbState.BREATHING
                            ) {
                                // Intentionally empty: the composer's orb is
                                // the status for this window.
                            } else {
                                // 0.81.3: the prompt that asked for this answer,
                                // plus the turn before it. Widget recovery needs
                                // both: "show me a bar chart" often carries no
                                // data of its own, and the numbers live in the
                                // previous answer.
                                val turnUserPrompt = remember(message.id, index) {
                                    messages.subList(0, index)
                                        .lastOrNull { it.role == "user" }
                                        ?.content.orEmpty()
                                }
                                val turnPriorAssistant = remember(message.id, index) {
                                    messages.subList(0, index)
                                        .lastOrNull { it.role == "assistant" }
                                        ?.content.orEmpty()
                                }
                                Box(Modifier.messageAppear(message.id)) {
                                    AssistantMessage(
                                        message = message,
                                        conversationId = activeConversationId,
                                        isSpeaking = speakingMessageId == message.id,
                                        highlight = index == activeMatchIndex,
                                        showIdentity = false,
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
                                        onTranslate = { translateMessage(message.content) },
                                        // PHASE 8.1: real search artifacts — trace
                                        // card, source cards, citation chips, clarify
                                        // quick-choices. A picked chip is sent as an
                                        // ordinary user message through dispatch.
                                        onOpenSource = openSource,
                                        onClarifyOption = { option -> dispatch(option.label, echoUser = true) },
                                        onSendPrompt = { text -> dispatch(text, echoUser = true) },
                                        onNavigateVoice = onNavigateVoice,
                                        userPrompt = turnUserPrompt,
                                        priorContext = turnPriorAssistant
                                    )
                                }
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
            // where send always is), and the static thinking dots sit directly
            // on top of the composer row. The REAL stream phase drives it (Phase 4):
            // "Thinking…" before the first token, "Composing…" while tokens
            // flow — never a fabricated state, never a full-width bar
            // replacing the composer, never a distraction from the answer.
            // FLASH-MODE BUG 1: the word "Thinking…" renders ONLY when the
            // wire's `mode` event resolved this turn to effectiveMode=
            // "thinking" — a Flash turn shows the bare dots (neutral, no
            // label). Search/Working states keep their honest labels in every
            // mode: those are real pipeline phases, not mode statements.
            // NO STATUS LINE HERE ANY MORE.
            //
            // There used to be a centred "Thinking…" / "Composing…" /
            // "Searching…" row sitting directly above the composer, next to a
            // three-dot indicator, while the send slot showed a generic stop
            // square. Three signals for one fact, and the two words collided
            // with the "Thinking ▾" mode chip INSIDE the composer — same word,
            // two meanings, one above the other.
            //
            // The orb in the send slot is the status now: it is the real
            // phase, animated, in the place the thumb already is. TalkBack
            // still gets the phase from the orb's own contentDescription.
            ComposerRow(
                draftText = { draft },
                onDraftChange = { draft = it },
                onSend = { text -> dispatch(text, echoUser = true) },
                onAttach = { attachSheetOpen = true },
                drafts = attachDrafts,
                onRemoveAttachment = { attachStore.remove(it) },
                onRetryAttachment = { attachStore.retry(it) },
                // One-capsule reference: mode and voice live inside the
                // sparkle's attach/mode sheet, not as extra chrome beside the
                // field. The field and send slot stay exactly where the thumb
                // expects them.
                isStreaming = isStreaming,
                // The honest phase, already derived from the real stream state
                // at the screen scope — the orb in the send slot renders it.
                orbState = liveOrbState,
                onStop = {
                    // Cancelling finalizes in the controller: the cancelled
                    // repository job persists the partial (NonCancellable)
                    // and the Cancelled phase commits the full buffered text
                    // through the observer — same contract as before.
                    chatStream.cancelAndFinalize()
                },
                // A next message drafted while this turn runs can be parked
                // and it goes by itself the moment the reply lands. Survives
                // rotation (rememberSaveable); a stop hands it back to the
                // composer rather than sending it against the user's intent.
                queuedText = { queued },
                onQueue = {
                    queued = draft
                    draft = ""
                },
                onUnqueue = {
                    val pending = queued
                    queued = ""
                    draft = when {
                        pending.isBlank() -> draft
                        draft.isBlank() -> pending
                        // Never clobber newer typing: the parked text comes
                        // back first, the new text follows below it.
                        else -> "$pending\n\n$draft"
                    }
                }
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
            // FLASH MODE (Phase 3) — the Mode section lives in this sheet.
            selectedMode = chatMode,
            onModeSelected = { next ->
                chatMode = next
                SettingsStore.setChatMode(activeConversationId, next)
            },
            onGallery = {
                attachSheetOpen = false
                if (remainingSlots == 0) {
                    showSnack("Up to ${AttachmentRules.MAX_PER_MESSAGE} attachments per message")
                } else {
                    galleryPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            },
            onCamera = {
                attachSheetOpen = false
                launchCamera()
            },
            onFiles = {
                attachSheetOpen = false
                if (remainingSlots == 0) {
                    showSnack("Up to ${AttachmentRules.MAX_PER_MESSAGE} attachments per message")
                } else {
                    documentPicker.launch(arrayOf("image/*", "application/pdf", "text/*"))
                }
            },
            onVoice = {
                attachSheetOpen = false
                val navigateVoice = onNavigateVoice
                if (navigateVoice != null) navigateVoice()
                else showSnack("Voice note arrives with the device permissions build")
            },
            onUnavailable = { label ->
                attachSheetOpen = false
                showSnack("$label isn't available yet, nothing fake behind it")
            }
        )
    }
}

// --- top bar chrome -----------------------------------------------------------

/**
 * design-3-b top-bar hit target: a 24dp icon in a 40dp rounded-square (12dp
 * radius) press target, tinted onBackground. The visible press state comes
 * from the existing kineticPress pattern (reduce-motion gated internally);
 * the label doubles as the content description for TalkBack.
 */
/**
 * The reference brand header, shared by the chat screen and the render
 * harness: orb, “Nexly / AI Companion”, and two quiet glass controls.
 */
@Composable
internal fun NexlyChatHeader(
    sparkleLabel: String,
    onSparkle: () -> Unit,
    onSparkleLong: (() -> Unit)?,
    menuIcon: ImageVector,
    menuLabel: String,
    onMenu: () -> Unit,
    onMenuLong: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        GsTheme.colors.surface.copy(alpha = 0.56f),
                        GsTheme.colors.surface.copy(alpha = 0.30f)
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GsMotion.spaceM, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(com.grapsee.gsai.R.drawable.gs_companion_orb),
                contentDescription = "Nexly",
                modifier = Modifier
                    .size(38.dp)
                    .shadow(3.dp, CircleShape)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Nexly",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.5.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = NexlyInk.header,
                    maxLines = 1
                )
                Text(
                    text = "AI Companion",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        lineHeight = 12.sp
                    ),
                    color = NexlyInk.headerMeta,
                    maxLines = 1
                )
            }
            ChatTopIconButton(
                icon = GsIcons.Sparkles,
                label = sparkleLabel,
                onClick = onSparkle,
                onLongClick = onSparkleLong
            )
            Spacer(Modifier.width(6.dp))
            ChatTopIconButton(
                icon = menuIcon,
                label = menuLabel,
                onClick = onMenu,
                onLongClick = onMenuLong
            )
        }
        // The sheet closes the header with a hairline instead of letting it
        // dissolve into the backdrop.
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.75.dp)
                .background(Color.White.copy(alpha = 0.42f))
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatTopIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(36.dp)
            .kineticPress()
            .gsGlass(GsGlassRole.Sunken, CircleShape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onLongClickLabel = onLongClick?.let { "$label (more)" },
                onLongClick = onLongClick?.let {
                    {
                        if (GsHaptics.enabled()) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        it()
                    }
                },
                onClick = onClick
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = GsTheme.colors.textPrimary,
            modifier = Modifier.size(19.dp)
        )
    }
}

// --- bubbles ----------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun UserMessage(
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
            // The reference user turn: a pale translucent peach bubble on the
            // right, a glass avatar beside it, and the delivered time + double
            // ticks underneath. Copy and Edit live in the long-press menu so
            // the resting surface has no action chrome.
            val dark = isSystemInDarkTheme()
            val colors = GsTheme.colors
            var userMenu by remember { mutableStateOf(false) }
            // The sheet's user turn is a narrow column (about half the device),
            // not a full-width slab: the pearl field stays visible beside it.
            val maxBubble = minOf(
                210.dp,
                (LocalConfiguration.current.screenWidthDp * 0.54f).dp
            )
            val bubbleTop = if (dark) colors.surface.copy(alpha = 0.54f)
            else Color(0xFFFFE9DE).copy(alpha = 0.82f)
            val bubbleBottom = if (dark) colors.accentSoft.copy(alpha = 0.28f)
            else Color(0xFFFFD6C4).copy(alpha = 0.68f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    if (message.attachments.isNotEmpty()) {
                        AttachmentChipRow(
                            drafts = message.attachments.map { it.asDisplayDraft() },
                            editable = false,
                            horizontalArrangement = Arrangement.End
                        )
                    }
                    if (message.content.isNotEmpty()) {
                        Box {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = maxBubble)
                                    .shadow(2.dp, RoundedCornerShape(18.dp))
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(
                                        Brush.verticalGradient(listOf(bubbleTop, bubbleBottom))
                                    )
                                    .border(
                                        width = if (highlight) 2.dp else 0.5.dp,
                                        color = if (highlight) colors.accentStrong
                                        else Color.White.copy(alpha = 0.58f),
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = {
                                            if (GsHaptics.enabled()) {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            userMenu = true
                                        }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = message.content,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    ),
                                    color = NexlyInk.prose
                                )
                            }
                            DropdownMenu(
                                expanded = userMenu,
                                onDismissRequest = { userMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Copy") },
                                    onClick = {
                                        userMenu = false
                                        onCopy(message.content)
                                    }
                                )
                                if (editEnabled) {
                                    DropdownMenuItem(
                                        text = { Text("Edit") },
                                        onClick = {
                                            userMenu = false
                                            onEditStart()
                                        }
                                    )
                                }
                            }
                        }
                    }
                    val stamp = remember(message.createdAt) { timeLabel(message.createdAt) }
                    if (stamp.isNotEmpty() || !message.failed) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 5.dp, end = 2.dp)
                        ) {
                            if (stamp.isNotEmpty()) {
                                Text(
                                    text = stamp,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                    color = NexlyInk.meta
                                )
                            }
                            if (!message.failed) {
                                NexlyGlyph(
                                    name = "checks",
                                    size = 12.dp,
                                    color = NexlyInk.meta
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                NexlyUserAvatar(size = 30.dp)
            }
        }
    }
}

/**
 * The reference composer — ONE glass capsule, nothing competing:
 *
 *   [sparkle/attach] [ field ] [queue?] (paper-plane send | stop orb)
 *
 * The pill carries the floating-control glass; the field is borderless inside
 * it, grows to six lines, then scrolls internally. The leading sparkle opens
 * the attach/mode sheet the old plus and mic used to reach, so no capability
 * was traded for the simpler chrome. Send is the peach paper-plane with the
 * layered press (uiverse dull-ladybug-18 intent: the icon lifts as the
 * container scales); the stop orb owns the slot while a turn runs.
 */
@Composable
internal fun ComposerRow(
    draftText: () -> String,
    onDraftChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onAttach: () -> Unit,
    isStreaming: Boolean,
    onStop: () -> Unit,
    // The message parked by the queue affordance while the current turn runs
    // ("" = nothing queued). It is a real pending message, owned by the screen.
    queuedText: () -> String,
    onQueue: () -> Unit,
    onUnqueue: () -> Unit,
    // The live activity state for the send slot while a turn runs. The slot
    // renders the orb instead of a stop glyph: the thing that is happening is
    // the control, and tapping it stops the turn.
    orbState: OrbState? = null,
    // PHASE 5: the live attachment drafts — chips row above the field,
    // recomposition stays scoped to the row (the transcript never reacts).
    drafts: List<AttachmentDraft> = emptyList(),
    onRemoveAttachment: (String) -> Unit = {},
    onRetryAttachment: (String) -> Unit = {},
    // FLASH-MODE BUG 2 — the mode chip sat INSIDE the pill, LEFT of the text
    // input; the one-capsule reference moved it into the sparkle's sheet.
    modeLabel: String? = null,
    onModeChipClick: () -> Unit = {}
) {
    Column {
        AttachmentChipRow(
            drafts = drafts,
            onRemove = onRemoveAttachment,
            onRetry = onRetryAttachment
        )
        // A queued message is a REAL pending message: parking it must never
        // look like the text vanished. One quiet line while the turn runs,
        // with an X that hands the draft straight back to the composer.
        if (queuedText().isNotBlank() && isStreaming) {
            QueuedDraftRow(text = queuedText(), onCancel = onUnqueue)
        }
        val canSend = draftText().isNotBlank() || drafts.isNotEmpty()
        // Queue-send is streaming-only and text-only: attachments still in
        // flight cannot ride a queue (their upload state is real), so with
        // staged drafts the queue affordance honestly stays away.
        val canQueue = isStreaming && draftText().isNotBlank() && drafts.isEmpty()
        // uiverse Cobp dangerous-dolphin-47, materialized WITHOUT its
        // gradient: the one-pill composer as a calm bordered container — a
        // clean 1dp hairline (outlineVariant) wrapped on a 28dp pill.
        // THE COMPOSER carries the four-cue floating-control glass: thinnest
        // fill, brightest directional rim, warm hover shadow — it must read as
        // glass lifted off the bloom, not a flat tinted pill with a hairline.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .gsGlass(GsGlassRole.FloatingControl, RoundedCornerShape(26.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // The reference has one leading sparkle. It opens the same
                // attach/mode sheet the removed plus and mic used to reach, so
                // no capability disappeared with the chrome.
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Nexly tools and attachments",
                            onClick = onAttach
                        )
                        .semantics { contentDescription = "Nexly tools and attachments" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        GsIcons.Sparkles,
                        contentDescription = null,
                        tint = Color(0xFF8E7BFF),
                        modifier = Modifier.size(17.dp)
                    )
                }
    // The field is LIVE during a turn. It used to be `enabled = !isStreaming`,
    // and the effect was exactly opposite to native: while the AI was
    // answering, the composer died — you could not write the next question or
    // task. Every native AI app keeps the composer editable while an answer
    // streams (a drafted next turn can queue; see the queue-send affordance).
    // The send slot still shows the stop orb while a turn runs, so the field
    // being live can never fire a second concurrent stream by accident.
    // Enter is a NEWLINE, never a send (there is a dedicated send button), so
    // multi-line messages type the way a phone expects. Sentence
    // auto-capitalisation is what every native composer does.
    BasicTextField(
        value = draftText(),
        onValueChange = onDraftChange,
        modifier = Modifier
            .weight(1f)
            .padding(vertical = 8.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = GsTheme.colors.textPrimary,
            fontSize = 14.sp
        ),
        cursorBrush = SolidColor(GsTheme.colors.accentStrong),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences
        ),
        maxLines = 6,
                    decorationBox = { inner ->
                        Box {
                            if (draftText().isEmpty()) {
                                Text(
                                    text = "Ask me anything...",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                                    color = GsTheme.colors.textPlaceholder
                                )
                            }
                            inner()
                        }
                    }
                )
                // While a turn runs the slot belongs to the stop orb — but a
                // drafted next message must still be able to GO. The queue
                // arrow sits left of the orb: it parks the draft and sends it
                // the moment the reply lands. Both actions stay reachable:
                // nothing is replaced, the field is simply never locked again.
                if (canQueue) {
                    GsQueueSendButton(onQueue = onQueue)
                }
                if (isStreaming) {
                    OrbStopControl(state = orbState ?: OrbState.BREATHING, onStop = onStop)
                } else {
                    GsSendButton(enabled = canSend) { onSend(draftText().trim()) }
                }
            }
        }
    }
}

/**
 * perf-4-a (technique B): the chat screen's SINGLE press-motion source.
 * Every press-driven animateFloatAsState on this screen (send button, New
 * chat key) routes through this one function — no per-element durations,
 * no duplicate mechanisms. Reduced motion snaps instantly.
 */
private fun chatPressSpec(reduced: Boolean, pressed: Boolean): AnimationSpec<Float> = when {
    reduced -> snap()
    pressed -> tween(120, easing = EaseOut)
    else -> GsMotion.snappy()
}

/**
 * perf-4-a (technique C): the ONLY composition scope that reads the live
 * stream state. Stream flushes (~30 Hz) recompose this text node alone —
 * never the message row, the LazyColumn, or the screen. Reading [live]
 * inside the row body would re-execute the whole row per flush; isolating
 * the read here keeps every settled row skippable. Semantics preserved
 * verbatim from the previous inline read: null stream state falls back to
 * the committed content; a blank stream text renders blank (the caret-only
 * pre-first-token frame is a feature, not a bug to fix here).
 */
@Composable
private fun LiveStreamText(
    live: State<ChatStreamController.StreamState?>?,
    fallback: String,
    isStreaming: Boolean,
    onCopyCode: (String) -> Unit,
    citationUrls: Map<Int, String>,
    onSendPrompt: ((String) -> Unit)? = null,
    onNavigateVoice: (() -> Unit)? = null,
    userPrompt: String = "",
    priorContext: String = "",
    glassAssistant: Boolean = false,
    statusCard: (@Composable () -> Unit)? = null,
    statusAtEnd: Boolean = true,
    planTimestamp: String? = null,
    planConversationId: String? = null
) {
    CompositionLocalProvider(LocalVoiceLauncher provides onNavigateVoice) {
        BlocksContent(
            content = live?.value?.streamText ?: fallback,
            isStreaming = isStreaming,
            onCopyCode = onCopyCode,
            citationUrls = citationUrls,
            onSendPrompt = onSendPrompt,
            userPrompt = userPrompt,
            priorContext = priorContext,
            glassAssistant = glassAssistant,
            statusCard = statusCard,
            statusAtEnd = statusAtEnd,
            planTimestamp = planTimestamp,
            planConversationId = planConversationId
        )
    }
}

/**
 * THE THINKING / REASONING / EFFORT CARD (reference): the same three stages,
 * with circular glass glyph tiles and the reference copy. It lives INSIDE the
 * first assistant card, after the preamble, and stays visible while the plan
 * streams — not just before the first token.
 */
enum class NexlyThinkingPhase { THINKING, REASONING, SEARCH, ANSWER, DONE }

/** First meaningful sentence of the live reasoning, capped for one line. */
private fun reasoningHint(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val clean = text.replace(Regex("\\s+"), " ").trim()
    if (clean.isEmpty()) return null
    val end = clean.indexOfFirst { it == '.' || it == '!' || it == '?' }
    val sentence = if (end >= 0) clean.substring(0, end + 1) else clean
    return sentence.take(96).trim()
}

@Composable
internal fun ReferenceThinkingCard(
    phase: NexlyThinkingPhase = NexlyThinkingPhase.DONE,
    effort: String? = null,
    isPlan: Boolean = true,
    reasoningHint: String? = null,
    searchSources: Int = 0,
) {
    val thinkingSubtitle = if (isPlan) {
        "Analyzing your goals and priorities..."
    } else {
        "Understanding your request..."
    }
    // Show a real reasoning sentence only when the provider streamed one.
    // Otherwise this row must not imply a trace the app never received.
    val reasoningSubtitle = reasoningHint?.takeIf { it.isNotBlank() }
        ?: if (isPlan) "Provider default reasoning" else "No reasoning trace exposed"
    val hasSearch = searchSources > 0
    val finalTitle = if (hasSearch) "Search" else "Answer"
    val finalSubtitle = if (hasSearch) {
        "$searchSources source${if (searchSources == 1) "" else "s"} found"
    } else if (isPlan) {
        "Composing your plan..."
    } else {
        "Composing the answer..."
    }
    val finalIcon = if (hasSearch) "world-search" else "pencil"
    val finalTone = if (hasSearch) "blue" else "peach"
    val finalActive = if (hasSearch) {
        phase == NexlyThinkingPhase.SEARCH
    } else {
        phase == NexlyThinkingPhase.ANSWER
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 460.dp)
            .gsGlass(GsGlassRole.FloatingControl, RoundedCornerShape(16.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        ThinkingRow(
            icon = "sparkles",
            tone = "peach",
            title = "Thinking",
            subtitle = thinkingSubtitle,
            active = phase == NexlyThinkingPhase.THINKING
        )
        ThinkingRow(
            icon = "brain",
            tone = "lavender",
            title = "Reasoning",
            subtitle = reasoningSubtitle,
            active = phase == NexlyThinkingPhase.REASONING
        )
        ThinkingRow(
            icon = finalIcon,
            tone = finalTone,
            title = finalTitle,
            subtitle = finalSubtitle,
            active = finalActive
        )
    }
}

@Composable
private fun ThinkingRow(
    icon: String,
    tone: String,
    title: String,
    subtitle: String,
    active: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        NexlyGlassGlyphTile(
            name = icon,
            toneName = tone,
            size = 24.dp,
            glyphSize = 12.dp,
            active = active
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.5.sp,
                    lineHeight = 15.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = NexlyInk.label
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.5.sp,
                    lineHeight = 12.sp
                ),
                color = NexlyInk.meta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * perf-smooth-1 (cause 4/5): the reasoning text flushes per token, so the
 * live?.value read lives here -- inside the smallest possible scope --
 * instead of in [AssistantMessage], where it used to re-compose the whole
 * assistant row ~30 times a second while a reasoning phase streamed. Emits
 * the panel plus its trailing spacer only while reasoning text is present.
 */
@Composable
private fun LiveReasoning(live: State<ChatStreamController.StreamState?>?) {
    val st = live?.value ?: return
    if (st.reasoning.isNullOrEmpty()) return
    ReasoningPanel(
        text = st.reasoning.orEmpty(),
        startMs = st.reasoningStartMs,
        endMs = st.reasoningEndMs
    )
    Spacer(Modifier.height(8.dp))
}

/**
 * The composer's send control, a 36dp ink circle with the up arrow. uiverse
 * vinodjangid07 dull-ladybug-18 press physics materialized monochrome: on
 * press the whole control settles to 0.96 scale and 1dp down over 120ms
 * ease-out; release springs back; reduce motion (settings or OS) applies both
 * states instantly with no animation. Empty field with no attachments:
 * neutral container, inert press, still a 44dp touch target via
 * minimumInteractiveComponentSize.
 */
@Composable
private fun GsSendButton(enabled: Boolean, onSend: () -> Unit) {
    val colors = GsTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = SettingsStore.reduceAnimations || SettingsStore.reduceMotion
    val pressSpec: AnimationSpec<Float> = chatPressSpec(reduced, pressed && enabled)
    val press by animateFloatAsState(
        targetValue = if (pressed && enabled) 1f else 0f,
        animationSpec = pressSpec,
        label = "sendPress"
    )
    Box(
        modifier = Modifier
            .padding(end = 3.dp, bottom = 1.dp)
            .size(36.dp)
            .graphicsLayer {
                val scale = 1f - 0.04f * press
                scaleX = scale
                scaleY = scale
                translationY = press * 1.dp.toPx()
            }
            .clip(CircleShape)
            .background(
                if (enabled) {
                    Brush.linearGradient(listOf(Color(0xFFFFC08D), Color(0xFFF0813F)))
                } else {
                    // The reference keeps the peach send control visible even
                    // with an empty field; the disabled state is the reduced
                    // alpha, not a gray disc.
                    Brush.linearGradient(
                        listOf(
                            Color(0xFFFFC08D).copy(alpha = 0.62f),
                            Color(0xFFF0813F).copy(alpha = 0.62f)
                        )
                    )
                }
            )
            .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "Send"
            ) { onSend() }
            .semantics { contentDescription = "Send" },
        contentAlignment = Alignment.Center
    ) {
        NexlyGlyph(
            name = "send-2",
            size = 18.dp,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.88f)
        )
    }
}

/**
 * The queue affordance: the send arrow with a small clock badge, shown while a
 * turn runs and a next message is drafted (left of the stop orb). The arrow is
 * the same shape as [GsSendButton] — same verb, later timing — and the clock
 * badge is what distinguishes "this goes when the reply finishes" from "this
 * goes now". Text-only by construction (the queue carries no attachments).
 */
@Composable
private fun GsQueueSendButton(onQueue: () -> Unit) {
    val colors = GsTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = SettingsStore.reduceAnimations || SettingsStore.reduceMotion
    val pressSpec: AnimationSpec<Float> = chatPressSpec(reduced, pressed)
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = pressSpec,
        label = "queuePress"
    )
    Box(
        modifier = Modifier
            .padding(end = 4.dp, bottom = 2.dp)
            .size(36.dp)
            .graphicsLayer {
                val scale = 1f - 0.04f * press
                scaleX = scale
                scaleY = scale
                translationY = press * 1.dp.toPx()
            }
            .clip(CircleShape)
            .background(colors.textPrimary)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "Send when the reply finishes"
            ) { onQueue() }
            .semantics { contentDescription = "Send when the reply finishes" },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            GsIcons.ArrowUp,
            contentDescription = null,
            tint = colors.appBackground,
            modifier = Modifier.size(20.dp)
        )
        // The "later" badge — the one pixel of difference from a live send.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(14.dp)
                .clip(CircleShape)
                .background(colors.appBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                GsIcons.Clock,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(10.dp)
            )
        }
    }
}

/**
 * The queued-message line above the composer: what is parked, that it goes
 * when the reply lands, and an X that hands the text straight back. A parked
 * message must never look like it vanished — this is the honest receipt.
 */
@Composable
private fun QueuedDraftRow(text: String, onCancel: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                GsIcons.Clock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Queued — sends when the reply finishes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                Icon(
                    GsIcons.X,
                    contentDescription = "Cancel queued message",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/** The streaming-active affordance, living in the composer's send slot.
 *
 *  It used to be a white disc with a stop square — a second, unrelated symbol
 *  next to the orb that was already animating above the composer. Now the
 *  slot renders the REAL activity orb for the turn's actual phase (breathing
 *  before the first token, ribbon while composing, globe while searching),
 *  and the orb itself is the stop target: the thing that is happening is the
 *  thing you press. Tap target stays 36dp; the orb keeps its tuned INLINE
 *  geometry (20dp) so its dot counts and speeds are the designed ones.
 */
@Composable
private fun OrbStopControl(state: OrbState, onStop: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 4.dp, bottom = 2.dp)
            .size(36.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = "Stop generating"
            ) { onStop() }
            .semantics { contentDescription = "Stop generating" },
        contentAlignment = Alignment.Center
    ) {
        ThinkingOrb(
            state = state,
            // STANDARD, not INLINE. Both presets were rasterised at this exact
            // size and compared: INLINE scaled UP to 34dp is fifteen fat blobs
            // that read as a broken spinner, while STANDARD scaled DOWN keeps
            // its 44-segment ribbon / 11-ring globe and reads as an orb. The
            // two presets are separate designs, and at 34dp the denser one is
            // the one that survives.
            size = OrbSize.STANDARD,
            renderScale = 0.53f,
            contentDescription = state.label
        )
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
                GsIcons.ChevronDown,
                contentDescription = "Jump to latest",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * One assistant turn — DOCUMENT style. The answer is not a card: content sits
 * directly on the app background at the reading column (bodyLarge measure,
 * 16sp / 24sp line height, hard-capped at 640dp; the shell clamps the whole
 * surface to GsLayout.contentMaxWidth and centers it). Identity is a compact
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
 *
 * PHASE 8.1 anatomy (docs/search-event-protocol.md): the turn's real search
 * artifacts ride along — the search TRACE card sits above the content
 * (expanded while streaming, collapsed to its one-line summary after done),
 * the SOURCE cards render under the content (live as sources complete, then
 * the persisted done list), [N] citation markers resolve into tappable chips
 * against those same sources, and a clarify turn's quick-choice chips render
 * under the content once the turn has settled (so every tap can actually
 * send). All of it is drawn from the controller state / the committed turn
 * data — nothing is synthesized here.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssistantMessage(
    message: ChatUiMessage,
    conversationId: String? = null,
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
    onTranslate: () -> Unit = {},
    // PHASE 8.1: open a real source URL (ACTION_VIEW) + send a clarify pick.
    onOpenSource: (String) -> Unit = {},
    onClarifyOption: (ClarifyOption) -> Unit = {},
    // 0.78.0: interactive widgets (cards/followups/form) send prompts back
    // through the same dispatch path as clarify chips.
    onSendPrompt: (String) -> Unit = {},
    // 0.80.0: the `voice` widget opens the real voice screen. Null (no route
    // wired) makes the card state that honestly instead of faking a session.
    onNavigateVoice: (() -> Unit)? = null,
    // 0.81.3: the turn's own user prompt + the assistant turn before it, used
    // by WidgetRecovery to rebuild a visual when the model answered a
    // "show me a chart" request with prose.
    userPrompt: String = "",
    priorContext: String = ""
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    // PHASE 8.1: the live stream wins while this bubble is the streaming one;
    // the committed turn data (memory, or Room after a reload) wins after.
    // perf-smooth-1 (cause 4/5): token flushes (~30 Hz) must NOT recompose
    // this row. The old code read live?.value here, subscribing the whole
    // AssistantMessage scope to every stream token even though only the text
    // node (LiveStreamText) and the reasoning panel consume per-token data.
    // Each field now reads through derivedStateOf keyed on list identity:
    // StreamState.copy() preserves field instances, so these recompose only
    // when that field actually changes (source events, trace rows) -- never
    // on streamText growth.
    val liveSources by remember(live) { derivedStateOf { live?.value?.sources } }
    val liveTraceItems by remember(live) { derivedStateOf { live?.value?.traceItems } }
    val liveEngineRows by remember(live) { derivedStateOf { live?.value?.engineRows } }
    val liveResearchNotes by remember(live) { derivedStateOf { live?.value?.researchPhaseNotes } }
    val liveUsedCitations by remember(live) { derivedStateOf { live?.value?.usedCitations } }
    val liveLedgerView by remember(live) { derivedStateOf { live?.value?.ledgerView } }
    // The Thinking / Reasoning / Effort card is driven by the REAL turn:
    // the backend's mode + effort, the reasoning stream, and whether the
    // answer has started. Boolean derivations keep the card out of the
    // per-token recomposition path.
    val liveMode by remember(live) { derivedStateOf { live?.value?.effectiveMode } }
    val liveEffort by remember(live) { derivedStateOf { live?.value?.effectiveEffort } }
    val liveReasoningActive by remember(live) {
        derivedStateOf { !live?.value?.reasoning.isNullOrEmpty() }
    }
    // The first sentence changes only once or twice while reasoning streams,
    // so the card does not join the per-token recomposition path.
    val liveReasoningHint by remember(live) {
        derivedStateOf { reasoningHint(live?.value?.reasoning) }
    }
    val liveAnswerStarted by remember(live) {
        derivedStateOf { !live?.value?.streamText.isNullOrEmpty() }
    }
    val livePlanStarted by remember(live) {
        derivedStateOf { live?.value?.streamText?.contains("```plan", ignoreCase = true) == true }
    }
    val turnSources = if (message.isStreaming) liveSources.orEmpty() else message.sources
    val turnClarify = if (message.isStreaming) null else message.clarify
    val turnTraceItems = liveTraceItems ?: message.traceItems
    // PHASE 8.2: the research-level detail rides the same live-vs-committed
    // seam as the trace items.
    val turnEngineRows = liveEngineRows ?: message.engineRows
    val turnResearchNotes = liveResearchNotes ?: message.researchNotes
    val turnUsedCitations = liveUsedCitations ?: message.usedCitations
    // PHASE 5: the branch table rides the same seam (memory-only; a reload
    // derives nothing — honest null).
    val turnLedgerView = if (message.isStreaming) liveLedgerView else message.ledgerView
    // Reload-time collapsed summary: the trace detail is memory-only, so a
    // reloaded turn honestly derives its one-liner from what persisted.
    val turnTraceSummary = message.traceSummary
        ?: if (message.sources.isNotEmpty()) "${message.sources.size} sources" else null
    // Citation chips resolve ONLY against the turn's real sources with a
    // plain http(s) URL — an [N] without a matching source stays literal text.
    val citationUrls = remember(turnSources) {
        turnSources
            .filter { it.url.startsWith("https://") || it.url.startsWith("http://") }
            .associate { it.ordinal to it.url }
    }
    // The reference keeps Thinking/Reasoning/Effort visible through the answer,
    // not just before the first token. A plan turn is the clearest case: the
    // timetable is the proof that planning happened, so the panel stays.
    // Thinking-mode turns keep the real card too; a flash turn with no
    // reasoning never pretends it thought.
    val hasPlan = remember(message.content) { containsNexlyPlan(message.content) }
    val planVisible = hasPlan || livePlanStarted
    var sawThinking by remember(message.id) { mutableStateOf(false) }
    var lastEffort by remember(message.id) { mutableStateOf<String?>(null) }
    var lastReasoningHint by remember(message.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(liveMode, liveReasoningActive) {
        if (liveMode == "thinking" || liveReasoningActive) sawThinking = true
    }
    LaunchedEffect(liveEffort) {
        if (!liveEffort.isNullOrBlank()) lastEffort = liveEffort
    }
    LaunchedEffect(liveReasoningHint) {
        if (!liveReasoningHint.isNullOrBlank()) lastReasoningHint = liveReasoningHint
    }
    val cardEffort = liveEffort ?: lastEffort
    val cardReasoningHint = liveReasoningHint ?: lastReasoningHint
    val thinkingPhase = when {
        !message.isStreaming -> NexlyThinkingPhase.DONE
        liveAnswerStarted -> NexlyThinkingPhase.ANSWER
        turnSources.isNotEmpty() -> NexlyThinkingPhase.SEARCH
        liveReasoningActive -> NexlyThinkingPhase.REASONING
        else -> NexlyThinkingPhase.THINKING
    }
    val showStatus = planVisible || sawThinking || liveMode == "thinking" || liveReasoningActive
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            if (showIdentity) {
                AssistantAvatar()
                Spacer(Modifier.width(9.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
            // PHASE 8.1: the search trace, ABOVE the bubble — expanded while
            // the stream runs, collapsed to the one-line summary after done
            // (protocol honest-state rule). Rendered only when a real search
            // happened; plain turns show nothing here.
            if (message.isStreaming) {
                if (turnTraceItems.isNotEmpty() || turnEngineRows.isNotEmpty() ||
                    turnResearchNotes.isNotEmpty() || turnLedgerView != null
                ) {
                    SearchTraceCard(
                        items = turnTraceItems,
                        summary = null,
                        expandable = true,
                        defaultExpanded = true,
                        engineRows = turnEngineRows,
                        researchNotes = turnResearchNotes,
                        usedCitations = turnUsedCitations,
                        ledgerView = turnLedgerView
                    )
                    Spacer(Modifier.height(8.dp))
                }
            } else {
                val hasTrace = turnTraceItems.isNotEmpty() || turnTraceSummary != null
                if (hasTrace) {
                    SearchTraceCard(
                        items = turnTraceItems,
                        summary = turnTraceSummary,
                        // PHASE 8.2: research detail keeps the card re-expandable
                        // after done, exactly while it is still in memory.
                        expandable = turnTraceItems.isNotEmpty() ||
                            turnEngineRows.isNotEmpty() ||
                            turnResearchNotes.isNotEmpty() ||
                            turnLedgerView != null,
                        defaultExpanded = false,
                        engineRows = turnEngineRows,
                        researchNotes = turnResearchNotes,
                        usedCitations = turnUsedCitations,
                        ledgerView = turnLedgerView
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            // THE NEXLY GLASS TRANSCRIPT. The Thinking panel now lives INSIDE
            // the first prose card, after the preamble, and stays there while
            // the plan streams. The raw reasoning panel is intentionally not
            // rendered here: the reference has one three-stage card, not a
            // second competing trace.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .background(
                        color = if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        else androidx.compose.ui.graphics.Color.Transparent,
                        shape = GsRadius.smShape()
                    )
                    .padding(vertical = 2.dp)
                    // Long-press only. There used to be `onClick = {}` here,
                    // which made every non-interactive pixel of an answer a
                    // button that did nothing — the exact "taps are swallowed"
                    // experience. A short tap now falls through to whatever is
                    // under it (a chart probe, a link, a widget control); only
                    // a deliberate long press opens the turn menu.
                    .pointerInput(message.id) {
                        detectTapGestures(
                            onLongPress = {
                                if (GsHaptics.enabled()) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                menuExpanded = true
                            }
                        )
                    }
            ) {
                // perf-4-a (technique C): the live stream read is isolated in
                // [LiveStreamText] so ~30 Hz text flushes recompose the text
                // node alone — never this row, the LazyColumn, or the screen.
                LiveStreamText(
                    live = live,
                    fallback = message.content,
                    isStreaming = message.isStreaming,
                    onCopyCode = onCopy,
                    // PHASE 8.1: citation markers [N] become tappable chips
                    // when N is one of THIS turn's real sources.
                    citationUrls = citationUrls,
                    onSendPrompt = onSendPrompt,
                    onNavigateVoice = onNavigateVoice,
                    userPrompt = userPrompt,
                    priorContext = priorContext,
                    glassAssistant = true,
                    planConversationId = conversationId,
                    statusCard = if (showStatus) {
                        {
                            ReferenceThinkingCard(
                                phase = thinkingPhase,
                                effort = cardEffort,
                                isPlan = planVisible,
                                reasoningHint = cardReasoningHint,
                                searchSources = turnSources.size
                            )
                        }
                    } else {
                        null
                    },
                    // Plan turns keep the reference placement (after the whole
                    // preamble); a non-plan answer puts the card after its first
                    // paragraph so a long answer cannot bury it.
                    statusAtEnd = planVisible,
                    planTimestamp = message.createdAt
                )
            }
            // PHASE 8.1: the turn's real sources — live while streaming (as
            // `source` events land), persisted done-list afterwards.
            if (turnSources.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SourceCardsRow(sources = turnSources, onOpenUrl = onOpenSource)
            }
            // PHASE 8.1: clarify quick-choices, only once the turn has settled
            // — a chip tap sends a normal message, which the in-flight gate
            // would refuse mid-stream. Honest affordance, honest timing.
            if (turnClarify != null && !message.isStreaming) {
                Spacer(Modifier.height(8.dp))
                ClarifyChips(prompt = turnClarify, onPick = onClarifyOption)
            }
            if (!message.isStreaming) {
                if (message.failed) {
                    Spacer(Modifier.height(6.dp))
                    FailedTurnNotice(
                        hasPartial = message.content.isNotBlank(),
                        onRegenerate = onRegenerate
                    )
                } else if (!hasPlan) {
                    // The reference shows only the timestamp under an assistant
                    // turn. Copy / regenerate / read / share remain reachable
                    // from the long-press menu, so no capability is lost.
                    val stamp = remember(message.createdAt) { timeLabel(message.createdAt) }
                    if (stamp.isNotEmpty()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = stamp,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.align(Alignment.End).padding(end = 2.dp)
                        )
                    }
                }
            }
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Copy") },
                leadingIcon = {
                    Icon(GsIcons.Copy, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                onClick = {
                    menuExpanded = false
                    onCopy(message.content)
                }
            )
            DropdownMenuItem(
                text = { Text(if (isSpeaking) "Stop reading" else "Read aloud") },
                leadingIcon = {
                    Icon(
                        if (isSpeaking) GsIcons.VolumeX else GsIcons.Volume2,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    menuExpanded = false
                    onReadAloud()
                }
            )
            DropdownMenuItem(
                text = { Text("Regenerate") },
                leadingIcon = {
                    Icon(GsIcons.RefreshCw, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                onClick = {
                    menuExpanded = false
                    onRegenerate()
                }
            )
            DropdownMenuItem(
                text = { Text("Share") },
                leadingIcon = {
                    Icon(GsIcons.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                onClick = {
                    menuExpanded = false
                    onShare()
                }
            )
            DropdownMenuItem(
                text = { Text("Translate") },
                leadingIcon = {
                    Icon(GsIcons.Languages, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                onClick = {
                    menuExpanded = false
                    onTranslate()
                }
            )
            DropdownMenuItem(
                text = { Text("Save to Library") },
                leadingIcon = {
                    Icon(GsIcons.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                onClick = {
                    menuExpanded = false
                    onSaveToLibrary()
                }
            )
            DropdownMenuItem(
                text = { Text("Branch new chat") },
                leadingIcon = {
                    Icon(GsIcons.Split, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                onClick = {
                    menuExpanded = false
                    onBranch()
                }
            )
        }
    }
}

/**
 * THE CONVERSATION OPTIONS SHEET (reference): Share / Rename / Chat Details /
 * Export / Clear / Delete, then a full-width Cancel. Rename, Clear and Delete
 * take a second tap so none is a one-tap accident. Every row calls a real
 * repository method or a real system intent; nothing here is inert.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationOptionsSheet(
    title: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onShare: () -> Unit,
    onDetails: (() -> Unit)?,
    onExport: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf(title) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var confirmingClear by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // A sheet is a SURFACE, not a tint. glassSurface is 80% opaque, and a
        // transcript of tables and code read straight through it — the sheet
        // looked broken rather than translucent. The reference's sheets are
        // solid cream; the glass language lives in the cards behind them.
        containerColor = GsTheme.colors.sheetSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GsMotion.spaceM)
                .padding(bottom = GsMotion.spaceL),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            Text(
                text = title.ifBlank { "This chat" },
                style = MaterialTheme.typography.titleMedium,
                color = GsTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            when {
                confirmingDelete -> {
                    SheetActionRow(
                        icon = GsIcons.Trash2,
                        title = "Delete this chat?",
                        subtitle = "This action cannot be undone. The conversation will be permanently removed from your history.",
                        destructive = true,
                        onClick = onDelete
                    )
                    SheetActionRow(GsIcons.X, "Cancel", onClick = { confirmingDelete = false })
                }
                confirmingClear -> {
                    SheetActionRow(
                        icon = GsIcons.Eraser,
                        title = "Clear all messages?",
                        subtitle = "The chat stays in your history, but every message in it is removed.",
                        destructive = true,
                        onClick = onClear
                    )
                    SheetActionRow(GsIcons.X, "Cancel", onClick = { confirmingClear = false })
                }
                renaming -> {
                    OutlinedTextField(
                        value = renameDraft,
                        onValueChange = { renameDraft = it },
                        label = { Text("Chat name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SheetActionRow(GsIcons.Check, "Save", onClick = {
                        val trimmed = renameDraft.trim()
                        if (trimmed.isNotEmpty()) onRename(trimmed)
                    })
                    SheetActionRow(GsIcons.X, "Cancel", onClick = { renaming = false })
                }
                else -> {
                    SheetActionRow(
                        icon = GsIcons.Share,
                        title = "Share Chat",
                        subtitle = "Share this conversation",
                        onClick = onShare
                    )
                    SheetActionRow(
                        icon = GsIcons.SquarePen,
                        title = "Rename Chat",
                        subtitle = "Give your chat a new name",
                        onClick = {
                            renameDraft = title
                            renaming = true
                        }
                    )
                    if (onDetails != null) {
                        SheetActionRow(
                            icon = GsIcons.Info,
                            title = "Chat Details",
                            subtitle = "View info & stats",
                            onClick = onDetails
                        )
                    }
                    SheetActionRow(
                        icon = GsIcons.FileText,
                        title = "Export Chat",
                        subtitle = "Send the transcript as text",
                        onClick = onExport
                    )
                    SheetActionRow(
                        icon = GsIcons.Eraser,
                        title = "Clear Chat",
                        subtitle = "Remove all messages",
                        onClick = { confirmingClear = true }
                    )
                    SheetActionRow(
                        icon = GsIcons.Trash2,
                        title = "Delete Chat",
                        subtitle = "Permanently delete this chat",
                        destructive = true,
                        onClick = { confirmingDelete = true }
                    )
                    SheetActionRow(
                        icon = GsIcons.X,
                        title = "Cancel",
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

/** One reference sheet row: glass icon tile, title, supporting sentence. */
@Composable
private fun SheetActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = GsTheme.colors
    val tint = if (destructive) colors.error else colors.textPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (subtitle == null) 52.dp else 64.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = title,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.glassSurface.copy(alpha = 0.85f))
                .border(1.dp, colors.glassBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (destructive) colors.error else colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = tint)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (destructive) colors.error.copy(alpha = 0.75f) else colors.textSecondary
                )
            }
        }
    }
}

/**
 * The assistant identity mark: the real companion orb, outside the glass card,
 * exactly as the reference frames it. No "GS" monogram and no second logo.
 */
@Composable
private fun AssistantAvatar() {
    Image(
        painter = painterResource(com.grapsee.gsai.R.drawable.gs_companion_orb),
        contentDescription = "Nexly",
        modifier = Modifier
            .size(38.dp)
            .shadow(5.dp, CircleShape)
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape)
    )
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
            GsIcons.CircleAlert,
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
                    "GS stopped before finishing, the part that arrived is kept above."
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
    // TASK 5: every interactive element gets a visible press state — the
    // icon-only buttons previously had only the default ripple, no scale.
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(28.dp)
            .kineticPress()
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * (design-3-b's three static dots lived here. They are gone: the composer's
 * send slot now renders the real activity orb for the turn's actual phase, so
 * a second abstract three-dot indicator was saying the same thing twice.)
 */

// --- workspace empty state ---------------------------------------------------

/**
 * design-3-b empty-state recomposition. The fresh workspace photographs as ONE
 * centered identity column, vertically centered in the available space with
 * 24dp side padding: the 72dp GS monogram circle leads (solid primary fill,
 * onPrimary "GS" at 28sp bold), then the 24sp weight-600 greeting "What can I
 * do for you?" (sans, never the serif display tier), an 8dp gap, the 3D-press
 * New chat primary (uiverse marcelodolza stupid-vampirebat-24, unchanged from
 * design-2-b), a 16dp gap, and the ONE capability showcase card (uiverse
 * Ashon-G big-chipmunk-8, unchanged). The former breathing orb identity mark
 * is gone from this surface. The block yields to the transcript on the first
 * turn; while the reader types, the actions step aside (existing draft-aware
 * behavior) and the monogram + greeting hold the floor.
 */
@Composable
private fun WorkspaceEmptyState(
    // perf-smooth-3 (emulator 15 -- composer typing): the draft arrived as a raw
    // String read at the SCREEN scope, so every keystroke on the empty chat
    // invalidated the entire ChatScreen body (transcript branch decision, top
    // bar, composer, sheet conditionals) instead of just the text that depends
    // on it. A deferred read moves the state read down into the one line that
    // uses it; the screen no longer subscribes to keystrokes.
    draftText: () -> String,
    onNewChat: (() -> Unit)?,
    onSuggestion: (String) -> Unit = {}
) {
    val colors = GsTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // The companion, hand-drawn from the same primitives as onboarding —
        // not a bitmap, and not a second visual language. The halo is what
        // makes it sit IN the bloom instead of on top of it.
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(196.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(colors.aurora.first().copy(alpha = 0.45f), Color.Transparent)
                        )
                    )
            )
            RefArtwork(RefArt.Bloom, size = 132.dp, contentDescription = "GS companion")
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "GS",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp
            ),
            color = colors.accentStrong
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "What should we make of today?",
            style = MaterialTheme.typography.displaySmall,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Ask for an answer, a plan, or something you can touch.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
        // perf-smooth-5: the draft read is confined to its own composable.
        EmptyStateActions(
            draftText = draftText,
            onSuggestion = onSuggestion
        )
    }
}

/**
 * The four doors into an empty chat are chosen from the setup answers by
 * [chatSuggestionsFor]; see ChatSuggestions.kt for the pool and the ranking.
 */
@Composable
private fun EmptyStateActions(draftText: () -> String, onSuggestion: (String) -> Unit) {
    if (!draftText().isBlank()) return
    val suggestions = remember(OnboardingProfileStore.capabilities, OnboardingProfileStore.interests) {
        chatSuggestionsFor(OnboardingProfileStore.capabilities, OnboardingProfileStore.interests)
    }
    Spacer(Modifier.height(24.dp))
    Column(
        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        suggestions.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                row.forEach { suggestion ->
                    SuggestionCard(
                        suggestion = suggestion,
                        onClick = { onSuggestion(suggestion.prompt) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: ChatSuggestion,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = GsTheme.colors
    Column(
        modifier = modifier
            .heightIn(min = 96.dp)
            .gsGlass(GsGlassRole.Card, RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = suggestion.label,
                onClick = onClick
            )
            .padding(14.dp)
    ) {
        RefIconTile(suggestion.icon, size = 34.dp)
        Spacer(Modifier.height(10.dp))
        Text(
            text = suggestion.label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.textPrimary
        )
    }
}

/**
 * uiverse marcelodolza stupid-vampirebat-24, materialized monochrome: the
 * New chat primary is a solid accent fill with a HARD bottom edge, a 4dp tall
 * darker layer beneath with the same 16dp radius, drawn as a second background
 * box (never an elevation shadow, no gradient, no glow). Pressing rides the
 * cap 2dp down while the edge compresses to 2dp: the classic 3D key press.
 * Press-in runs 120ms ease-out, release springs back, and reduce motion
 * (settings or OS) applies both states instantly. The accent fill is the
 * screen's one accent; everything around it stays ink-on-paper.
 */
@Composable
private fun NewChatButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = SettingsStore.reduceAnimations || SettingsStore.reduceMotion
    val pressSpec: AnimationSpec<Float> = chatPressSpec(reduced, pressed)
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = pressSpec,
        label = "newChat3DPress"
    )
    Box(
        modifier = Modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "New chat"
            ) { onClick() }
    ) {
        // The hard edge: accentStrong, one step stronger than the accent
        // fill, so it reads as the darker (light) / brighter (dark) step in
        // both appearances. Bottom corners match the cap radius.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(4.dp - (2.dp * press))
                .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                .background(MaterialTheme.colorScheme.secondary)
        )
        // The key cap: solid primary fill, onPrimary label, rides 2dp down.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .graphicsLayer { translationY = press * 2.dp.toPx() }
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "New chat",
                style = GsTheme.textStyles.button,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

/**
 * uiverse Ashon-G big-chipmunk-8, materialized as ONE capability showcase
 * card: a 40dp rounded-square icon container (12dp radius) with a 24dp
 * Lucide glyph, a bold title and one supporting line on surfaceContainer,
 * 16dp radius, capped at 360dp. Static on purpose: it showcases what the
 * composer below can do, it does not navigate.
 */
@Composable
private fun EmptyCapabilityCard() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    GsIcons.MessageCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text = "Your smarter companion",
                    style = MaterialTheme.typography.titleMedium
                        .copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Ask, create, explore, and make something beautiful.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// --- attach sheet -------------------------------------------------------------

/**
 * PHASE 5: every REAL capability has a real handler behind it; every tile
 * without a backend consumer is explicitly [available] = false and renders
 * muted with an honest caption — option §5B "clearly show unavailable".
 * The two pre-PHASE-5 duplicate tiles ("Camera Photo"/"Camera",
 * "Files"/"Document") collapsed into their single real entry points.
 */
private data class AttachOption(
    val label: String,
    val icon: ImageVector,
    val isVoice: Boolean = false,
    val available: Boolean = true
)

private val attachOptions = listOf(
    AttachOption("Gallery", GsIcons.Image),
    AttachOption("Camera", GsIcons.Camera),
    AttachOption("Files", GsIcons.Folder),
    AttachOption("Voice note", GsIcons.Mic, isVoice = true),
    AttachOption("Code snippet", GsIcons.Code, available = false),
    AttachOption("Prompt template", GsIcons.FileText, available = false)
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // A sheet is a SURFACE, not a tint. glassSurface is 80% opaque, and a
        // transcript of tables and code read straight through it — the sheet
        // looked broken rather than translucent. The reference's sheets are
        // solid cream; the glass language lives in the cards behind them.
        containerColor = GsTheme.colors.sheetSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
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
                        GsIcons.Languages,
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
                    Icon(GsIcons.Copy, contentDescription = null, modifier = Modifier.size(16.dp))
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

/**
 * DESIGN PHASE 2 attach + mode sheet. Attach tiles first (real pickers for
 * the real capabilities, honest unavailable tiles for the rest), then Mode —
 * a horizontal MD3-carousel selection row (the operator's Resource 3): one
 * item per mode, next item peeking, selected state is the ink container with
 * a scale-in check (uiverse tiny-moose-42's animated stroke-check intent,
 * monochrome), state change 180ms ease-in-out, touch targets 44dp minimum.
 * Group separation is spacing, not a divider.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AttachSheet(
    onDismiss: () -> Unit,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onFiles: () -> Unit,
    onVoice: () -> Unit,
    onUnavailable: (String) -> Unit,
    // FLASH MODE (Phase 3) — the Mode section at the bottom of the sheet.
    selectedMode: String = "flash",
    onModeSelected: (String) -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // A sheet is a SURFACE, not a tint. glassSurface is 80% opaque, and a
        // transcript of tables and code read straight through it — the sheet
        // looked broken rather than translucent. The reference's sheets are
        // solid cream; the glass language lives in the cards behind them.
        containerColor = GsTheme.colors.sheetSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GsMotion.spaceM)
                .padding(bottom = GsMotion.spaceL),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            // --- Attach (FIRST — ChatGPT/Kimi put attach on top of the
            //     sheet; behavioral modes sit at the bottom). ---
            Text(
                text = "Attach",
                style = MaterialTheme.typography.titleMedium,
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
                                when {
                                    option.isVoice -> onVoice()
                                    !option.available -> onUnavailable(option.label)
                                    option.label == "Gallery" -> onGallery()
                                    option.label == "Camera" -> onCamera()
                                    option.label == "Files" -> onFiles()
                                }
                            }
                        )
                    }
                    if (rowOptions.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            // Spacing — attach (intent) above, mode (infrastructure) below.
            Spacer(Modifier.height(GsMotion.spaceM))
            // --- Mode (LAST): the MD3 carousel selection row. ---------------
            Text(
                text = "Mode",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            val modes = listOf(
                Triple("flash", "Flash", "Instant replies"),
                Triple("thinking", "Thinking", "Deep reasoning"),
                Triple("auto", "Auto", "System decides")
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(modes, key = { it.first }) { (value, label, hint) ->
                    ModeCarouselItem(
                        label = label,
                        hint = hint,
                        selected = selectedMode == value,
                        onClick = { onModeSelected(value) }
                    )
                }
            }
        }
    }
}

/** One carousel item: 148dp wide (next item peeks), 88dp tall, radius card.
 *  Selected = the ink container, inverted text, scale-in check. The state
 *  change animates 180ms ease-in-out per the motion spec. */
@Composable
private fun ModeCarouselItem(
    label: String,
    hint: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = GsTheme.colors
    val container by animateColorAsState(
        targetValue = if (selected) colors.textPrimary else colors.raisedSurface,
        animationSpec = GsEase.state(),
        label = "modeContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.appBackground else colors.textPrimary,
        animationSpec = GsEase.state(),
        label = "modeContent"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = GsEase.state(),
        label = "modeCheck"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(GsRadius.card),
        color = container,
        modifier = Modifier
            .width(148.dp)
            .kineticPress()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor
                )
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer {
                            scaleX = checkScale
                            scaleY = checkScale
                            alpha = checkScale
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        GsIcons.Check,
                        contentDescription = if (selected) "Selected" else null,
                        tint = contentColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
        shape = RoundedCornerShape(GsRadius.card),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .alpha(if (option.available) 1f else 0.45f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // One container spec: rounded square, neutral fill, ink icon.
            Surface(
                shape = RoundedCornerShape(GsRadius.md),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!option.available) {
                    Text(
                        text = "Not available yet",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
private val TIME_LABEL_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.US)
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
 * REASONING TRACE panel (the 21st chat-reasoning intent, monochrome): a quiet
 * collapsible card above the streamed answer. Open while the model thinks,
 * auto-collapsed once the answer's first token lands, reopenable. The seconds
 * label is the MEASURED span from the first reasoning delta to the first
 * answer delta, never a timer. Stream-only: renders while this turn is live.
 */
@Composable
private fun ReasoningPanel(
    text: String,
    startMs: Long?,
    endMs: Long?
) {
    val colors = GsTheme.colors
    var expanded by remember { mutableStateOf(true) }
    // The answer started: collapse once. A manual re-open after that sticks —
    // the effect only fires on the endMs null → set transition.
    LaunchedEffect(endMs) {
        if (endMs != null) expanded = false
    }
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = GsEase.state(),
        label = "reasoningChevron"
    )
    Surface(
        shape = RoundedCornerShape(GsRadius.card),
        color = colors.raisedSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    GsIcons.ChevronRight,
                    contentDescription = if (expanded) "Collapse reasoning" else "Expand reasoning",
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer { rotationZ = chevron }
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (startMs != null && endMs != null) {
                        val seconds = ((endMs - startMs).coerceAtLeast(0L)) / 1000.0
                        String.format(java.util.Locale.US, "Thought for %.1f seconds", seconds)
                    } else {
                        "Thinking…"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )
            }
            if (expanded) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary.copy(alpha = 0.85f),
                    modifier = Modifier
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 32.dp, end = 12.dp, bottom = 12.dp)
                )
            }
        }
    }
}
