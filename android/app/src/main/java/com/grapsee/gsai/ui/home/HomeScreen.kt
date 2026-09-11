package com.grapsee.gsai.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.grapsee.gsai.data.AccountStore
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.data.liveupdate.LiveUpdateState
import com.grapsee.gsai.data.liveupdate.LiveUpdater
import com.grapsee.gsai.data.local.ConversationEntity
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.ConversationActionsSheet
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsInputBar
import com.grapsee.gsai.ui.components.GsSectionHeader
import com.grapsee.gsai.ui.components.gsContentWidth
import com.grapsee.gsai.ui.components.gsConversationTitle
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsHaptics
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.GsRadius
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.gsHaptic
import com.grapsee.gsai.ui.theme.kineticPress
import com.grapsee.gsai.ui.theme.rememberAuroraBrush
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.OffsetDateTime
import java.util.Calendar

/**
 * PHASE 2 — Home is a CONVERSATION-FIRST launch surface. The hierarchy is:
 *
 *  PRIMARY    the inline composer — a real input, pinned at the bottom.
 *             Tap → type → send: sending composes a NEW chat and hands the
 *             text straight to the streaming path (GsRoutes.chat(autoSend)),
 *             so the front door IS the conversation, not a gateway to it.
 *  SECONDARY  a little supporting context — the Continue section (real
 *             conversations only) and three quiet starter chips that fill
 *             the composer rather than navigate away.
 *  CONTEXTUAL voice — tap the mic for full voice mode, hold it to dictate
 *             into the composer field.
 *  OPTIONAL   everything else lives in the drawer (drawer navigation owns
 *             tools/discovery; the old 5-card Tools grid is gone).
 *
 * Removed in this phase (per the consumer-first mandate): the model pill
 * ("GS Balanced · Balanced") from the top bar — a quiet brand mark replaces
 * it; the 84 dp hero orb and its halo — identity shrinks to a 44 dp mark;
 * the Tools/workbench card grid; the composer ENTRY bar that pretended to be
 * an input. Kept: real identity greeting, real Continue/Recents, honest
 * starters, the LiveUpdate pill (appears only when an update exists), the
 * disclaimer line.
 */
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    onOpenDrawer: () -> Unit = {}
) {
    val context = LocalContext.current
    // GS LiveUpdate: quiet GitHub manifest check on every Home appearance (10-min throttle).
    LaunchedEffect(Unit) { LiveUpdater.syncFrom(context) }
    // Platform feedback surface — permission denials etc. answer with a
    // snackbar (with a recovery action), never a system Toast.
    val snackbarHostState = remember { SnackbarHostState() }

    // Identity — the name the user actually typed at auth.
    val displayName = AccountStore.displayName(context)

    // Continuation source — the SAME Room flow the drawer's Recent list uses
    // (archived hidden, pins float, newest first). No fabricated samples:
    // an empty history renders neither Continue nor starters filler.
    val recents by remember {
        runCatching { ServiceLocator.chat.activeConversations() }.getOrElse { flowOf(emptyList()) }
    }.collectAsState(initial = emptyList())

    // The one composer draft. Starters pour their text here; sending hands it
    // to a fresh conversation with autoSend so the answer starts immediately.
    var draft by remember { mutableStateOf("") }

    // Long-press actions — the exact sheet the drawer and Chats use.
    var actionTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val mutate: (suspend (ConversationEntity) -> Unit) -> Unit = { action ->
        val target = actionTarget
        actionTarget = null
        if (target != null) {
            scope.launch { runCatching { action(target) } }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GsTheme.colors.appBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // TalkBack visits: top bar → composer → scroll content
                // (hero → continue → starters). The composer is the primary
                // action and is PINNED at the visual bottom; traversal indices
                // keep it early in the swipe order instead of dead last.
                .semantics { isTraversalGroup = true }
                .padding(horizontal = GsMotion.spaceM)
        ) {
            TopBar(
                onOpenDrawer = onOpenDrawer,
                onNavigate = onNavigate,
                modifier = Modifier.semantics { traversalIndex = 0f }
            )

            // Everything between the pinned top bar and the pinned composer
            // scrolls: at large system font scales a fixed hero would clip.
            // gsContentWidth(): phones unaffected, tablets/landscape get the
            // 640dp reading column.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .gsContentWidth()
                    .verticalScroll(rememberScrollState())
                    .semantics { traversalIndex = 2f },
                verticalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
            ) {
                Spacer(Modifier.height(GsMotion.spaceM))
                HeroBlock(displayName = displayName)

                // WHAT WAS I DOING? — the most recent conversation first,
                // then up to two more (three total; the drawer owns history).
                val continueConversation = recents.firstOrNull()
                if (continueConversation != null) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                    ) {
                        GsSectionHeader(
                            title = "Continue",
                            actionLabel = "All chats",
                            onAction = { onNavigate(GsRoutes.CHATS) }
                        )
                        HomeConversationRow(
                            conversation = continueConversation,
                            onOpen = { onNavigate(GsRoutes.chat(continueConversation.id)) },
                            onActions = {
                                GsHaptics.longPress(haptics)
                                actionTarget = continueConversation
                            }
                        )
                        recents.drop(1).take(2).forEach { conversation ->
                            HomeConversationRow(
                                conversation = conversation,
                                onOpen = { onNavigate(GsRoutes.chat(conversation.id)) },
                                onActions = {
                                    GsHaptics.longPress(haptics)
                                    actionTarget = conversation
                                }
                            )
                        }
                    }
                }

                StartersSection(onPick = { starter -> draft = starter })
                // GS LiveUpdate — appears only when a newer build exists on GitHub.
                LiveUpdatePill()
            }

            // Pinned inline composer + disclaimer — one traversal group so
            // TalkBack reaches the primary action right after the top bar.
            Column(
                modifier = Modifier.semantics { traversalIndex = 1f }
            ) {
                Spacer(Modifier.height(GsMotion.spaceS))
                HomeComposer(
                    draft = draft,
                    onDraftChange = { draft = it },
                    onSend = { text ->
                        draft = ""
                        onNavigate(GsRoutes.chat(conversationId = null, prompt = text, autoSend = true))
                    },
                    onOpenVoice = { onNavigate(GsRoutes.VOICE) },
                    snackbarHostState = snackbarHostState
                )
                Spacer(Modifier.height(GsMotion.spaceS))
                Text(
                    "GS can make mistakes — double-check important info.",
                    style = MaterialTheme.typography.labelMedium,
                    color = GsTheme.colors.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = GsMotion.spaceS),
                    textAlign = TextAlign.Center
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (actionTarget != null) {
        ConversationActionsSheet(
            title = gsConversationTitle(actionTarget?.title),
            pinned = actionTarget?.pinned == true,
            onDismiss = { actionTarget = null },
            onTogglePin = { mutate { c -> ServiceLocator.chat.setPinned(c.id, !c.pinned) } },
            onArchive = { mutate { c -> ServiceLocator.chat.setArchived(c.id, true) } },
            onDelete = { mutate { c -> ServiceLocator.chat.delete(c.id) } },
            onRename = { name ->
                val target = actionTarget
                actionTarget = null
                if (target != null) {
                    scope.launch { runCatching { ServiceLocator.chat.rename(target.id, name) } }
                }
            }
        )
    }
}

@Composable
private fun TopBar(
    onOpenDrawer: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = GsMotion.spaceS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Menu — opens the drawer
        CircleButton(
            icon = Icons.Outlined.Menu,
            contentDescription = "Open menu",
            onClick = onOpenDrawer
        )

        Spacer(Modifier.weight(1f))

        // Quiet brand mark. The model name/pill is GONE from the hero — an
        // ordinary user should never meet "GS Balanced · Balanced" on screen
        // one (model choice lives in chat + Model Centre, quietly).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(rememberAuroraBrush(CircleShape))
            )
            Text(
                "GS",
                style = MaterialTheme.typography.titleMedium,
                color = GsTheme.colors.textPrimary
            )
        }

        Spacer(Modifier.weight(1f))

        CircleButton(
            icon = Icons.Outlined.Add,
            contentDescription = "New chat",
            onClick = { onNavigate(GsRoutes.chat(null)) }
        )
    }
}

@Composable
private fun CircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = CircleShape,
        color = GsTheme.colors.raisedSurface,
        modifier = Modifier
            .size(44.dp)
            .kineticPress(interaction)
    ) {
        Box(
            modifier = Modifier.clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = GsTheme.colors.textPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Compact identity moment: a 44 dp orb (was an 84 dp hero) + the greeting
 * built from the user's REAL first name. It supports the composer below; it
 * no longer competes with it. Reduce-motion holds the resting frame.
 */
@Composable
private fun HeroBlock(displayName: String) {
    val reduced = SettingsStore.reduceAnimations || SettingsStore.reduceMotion
    val breathe: Float = if (reduced) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "orb")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400),
                repeatMode = RepeatMode.Reverse
            ),
            label = "orbBreathe"
        ).value
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer { scaleX = breathe; scaleY = breathe }
                .clip(CircleShape)
                .background(rememberAuroraBrush(CircleShape)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = "GS",
                tint = GsTheme.colors.textPrimary,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(Modifier.height(GsMotion.spaceM))

        Text(
            greetingFor(displayName),
            style = MaterialTheme.typography.headlineMedium,
            color = GsTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            // TalkBack: the greeting is the page heading.
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(GsMotion.spaceXS))

        // One quiet static line — no rotating tagline loop (infinite
        // recomposition for zero information).
        Text(
            "What would you like to work on?",
            style = MaterialTheme.typography.bodyMedium,
            color = GsTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Time-of-day greeting built from the user's REAL first name (AccountStore).
 * Nothing stored → a neutral line: no name, no invented title.
 */
private fun greetingFor(displayName: String): String {
    val firstName = displayName.trim()
        .split(Regex("\\s+"))
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour in 23..24 || hour in 0..4 -> if (firstName.isEmpty()) "Up late?" else "Up late, $firstName?"
        hour in 5..11 -> salutation("Good morning", firstName)
        hour in 12..17 -> salutation("Good afternoon", firstName)
        else -> salutation("Good evening", firstName)
    }
}

private fun salutation(base: String, firstName: String): String =
    if (firstName.isEmpty()) base else "$base, $firstName"

/**
 * One real conversation row: title + relative time. Tap opens the
 * conversation; long-press opens the same pin/rename/archive/delete sheet as
 * the drawer. (The per-row model-name subline is gone — model names are not
 * ordinary-user furniture.)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeConversationRow(
    conversation: ConversationEntity,
    onOpen: () -> Unit,
    onActions: () -> Unit
) {
    val rowInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GsRadius.mdShape())
            .background(GsTheme.colors.raisedSurface)
            .kineticPress(rowInteraction)
            .combinedClickable(
                interactionSource = rowInteraction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onOpen,
                onLongClickLabel = "More options",
                onLongClick = onActions
            )
            .padding(horizontal = GsMotion.spaceM, vertical = GsMotion.spaceS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                gsConversationTitle(conversation.title),
                style = MaterialTheme.typography.bodyMedium,
                color = GsTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                relativeTime(conversation.updatedAt),
                style = MaterialTheme.typography.labelMedium,
                color = GsTheme.colors.textSecondary
            )
        }
        if (conversation.pinned) {
            Icon(
                Icons.Filled.Star,
                contentDescription = "Pinned",
                tint = GsTheme.colors.accent,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * SECONDARY invitation — three honest seeds. A starter fills the ONE composer
 * above (no navigation, no second input system): the user reviews the text and
 * presses send. Tap → review → send. Quiet text chips — no icon theatre.
 */
@Composable
private fun StartersSection(onPick: (String) -> Unit) {
    val starters = remember {
        listOf(
            "Summarise a PDF into a brief",
            "Draft a launch email",
            "Explain a concept step by step"
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
    ) {
        starters.forEach { label ->
            GsChip(
                text = label,
                selected = false,
                onClick = { onPick(label) }
            )
        }
    }
}

/**
 * The PRIMARY action — a REAL inline composer. The old bar was an entry that
 * routed to chat before the user could type; this field accepts the text right
 * here and sending opens the conversation with the prompt already dispatched
 * (GsRoutes.chat(autoSend = true)). Anatomy mirrors the chat composer: the
 * shared [GsInputBar] (send lives in its trailing slot), one mic to the right
 * — quick tap opens full voice mode, press-and-hold dictates straight into the
 * field. Every dictation failure path dissolves quietly; denial is spoken via
 * the snackbar with a recovery action.
 */
@Composable
private fun HomeComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onOpenVoice: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val view = LocalView.current
    val keyboard = LocalSoftwareKeyboardController.current

    // --- Voice press-and-hold (dictate into the field) ------------------------
    var listening by remember { mutableStateOf(false) }
    val recognizerRef = remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun quietReset() {
        listening = false
    }

    fun startRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            // Device has no speech service — hand the user to full voice mode.
            quietReset()
            onOpenVoice()
            return
        }
        runCatching {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizerRef.value = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    // No match / timeout / busy — dissolve back to idle quietly.
                    quietReset()
                }

                override fun onResults(results: Bundle?) {
                    listening = false
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!text.isNullOrBlank()) {
                        onDraftChange((draft + " " + text).trim())
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    // Partials stay spoken-only; the committed result fills the
                    // field once — no half-words flickering in the input.
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
            }
            recognizer.startListening(intent)
        }.onFailure { quietReset() }
    }

    fun beginVoiceHold() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        when {
            granted -> startRecognizer()
            // The system dialog covers the app; the grant callback picks up.
            else -> listening = true
        }
    }

    val holdScope = rememberCoroutineScope()

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startRecognizer()
        } else {
            // Denial is spoken, not silent: a snackbar says where the fix lives
            // and hands the user straight to the app settings.
            view.gsHaptic(android.view.HapticFeedbackConstants.CLOCK_TICK)
            holdScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Microphone is off — allow it in Settings to talk",
                    actionLabel = "Open Settings",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    runCatching {
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
            quietReset()
        }
    }

    // Background lifecycle: the dictation mic is a foreground-only session.
    // ON_STOP tears the recognizer down immediately; the dispose twin
    // guarantees a created recognizer never outlives this canvas.
    fun cancelDictation() {
        runCatching {
            recognizerRef.value?.stopListening()
            recognizerRef.value?.destroy()
        }
        recognizerRef.value = null
        quietReset()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) cancelDictation()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cancelDictation()
        }
    }

    val keyboardController = keyboard
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)
    ) {
        GsInputBar(
            value = draft,
            onValueChange = onDraftChange,
            onSend = { text ->
                keyboardController?.hide()
                onSend(text)
            },
            placeholder = "Ask GS anything…",
            modifier = Modifier.weight(1f),
            imeAction = ImeAction.Send,
            minLines = 1,
            maxLines = 4
        )

        // Mic — tap: full voice mode; hold: dictate into the field.
        var micHeld by remember { mutableStateOf(false) }
        val micScale by animateFloatAsState(
            targetValue = if (micHeld) 0.93f else 1f,
            animationSpec = GsMotion.standard(),
            label = "micHold"
        )
        Box(
            modifier = Modifier
                .size(56.dp)
                .scale(micScale)
                // TalkBack parity: the mic is a button — its double-tap opens
                // voice mode exactly like a sighted tap; the hold behaviour
                // stays a gesture the finger performs.
                .semantics {
                    role = Role.Button
                    onClick(label = "Open voice mode") {
                        onOpenVoice()
                        true
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            micHeld = true
                            var isHold = false
                            val timer = holdScope.launch {
                                delay(VOICE_HOLD_TRIGGER_MS)
                                isHold = true
                                view.gsHaptic(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                beginVoiceHold()
                            }
                            val released = tryAwaitRelease()
                            micHeld = false
                            timer.cancel()
                            when {
                                isHold -> recognizerRef.value?.stopListening()
                                released -> onOpenVoice()
                                // else — gesture cancelled: quiet no-op
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (listening) VoicePulseHalo()
            Icon(
                Icons.Outlined.Mic,
                contentDescription = "Voice input",
                tint = if (listening) GsTheme.colors.accent else GsTheme.colors.textSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** Breathing aurora halo around the mic while dictation is live. */
@Composable
private fun VoicePulseHalo() {
    // Reduce-motion gate: the resting frame is held — a static halo, no
    // infinite transition created at all (not a faster one).
    if (SettingsStore.reduceAnimations || SettingsStore.reduceMotion) {
        HaloFrame(haloScale = 1f, haloAlpha = 0.5f)
        return
    }
    val transition = rememberInfiniteTransition(label = "voiceHalo")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voiceHaloPulse"
    )
    val fade by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voiceHaloFade"
    )
    HaloFrame(haloScale = pulse, haloAlpha = fade)
}

@Composable
private fun HaloFrame(haloScale: Float, haloAlpha: Float) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .graphicsLayer { scaleX = haloScale; scaleY = haloScale; alpha = haloAlpha }
            .clip(CircleShape)
            .background(rememberAuroraBrush(CircleShape))
    )
}

private const val VOICE_HOLD_TRIGGER_MS = 280L

/**
 * GS LiveUpdate pill — benchmark-quiet surface (raised + aurora dot). Only
 * rendered when a newer build is published: "v0.2.0 ready" → tap →
 * "Downloading update · 42%" → "Update ready · tap to install" → system
 * installer. A dropped stream resumes from the exact byte it broke at, and if
 * a download truly cannot finish the pill says so and stays tappable.
 */
@Composable
private fun LiveUpdatePill() {
    val state by LiveUpdater.state.collectAsState()
    when (val current = state) {
        is LiveUpdateState.Available -> UpdatePill(
            text = "GS LiveUpdate · v${current.versionName} ready",
            onClick = { LiveUpdater.beginInstallFlow() }
        )
        is LiveUpdateState.Downloading -> UpdatePill(
            text = "Downloading update · ${current.percent}%",
            onClick = {}
        )
        LiveUpdateState.Ready -> UpdatePill(
            text = "Update ready · tap to install",
            onClick = { LiveUpdater.beginInstallFlow() }
        )
        is LiveUpdateState.Failed -> UpdatePill(
            text = "Update didn't finish · tap to retry",
            onClick = { LiveUpdater.beginInstallFlow() }
        )
        LiveUpdateState.Idle -> Unit
    }
}

@Composable
private fun UpdatePill(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(GsMotion.radiusChip),
        color = GsTheme.colors.raisedSurface,
        modifier = Modifier.kineticPress(interaction)
    ) {
        Row(
            modifier = Modifier
                .clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    onClick = onClick
                )
                .padding(horizontal = GsMotion.spaceM, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(rememberAuroraBrush(CircleShape))
            )
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = GsTheme.colors.textPrimary
            )
        }
    }
}

/**
 * Same relative-time language as the Chats inbox (ChatsScreen.relativeTime):
 * minutes → hours → days → date, empty string when the timestamp is not
 * parseable (a broken stamp never renders as garbage).
 */
private fun relativeTime(iso: String): String = runCatching {
    val timestamp = OffsetDateTime.parse(iso)
    val elapsed = Duration.between(timestamp, OffsetDateTime.now())
    when {
        elapsed.toMinutes() < 1 -> "just now"
        elapsed.toHours() < 1 -> "${elapsed.toMinutes()}m ago"
        elapsed.toHours() < 24 -> "${elapsed.toHours()}h ago"
        elapsed.toDays() < 7 -> "${elapsed.toDays()}d ago"
        else -> timestamp.toLocalDate().toString()
    }
}.getOrElse { "" }
