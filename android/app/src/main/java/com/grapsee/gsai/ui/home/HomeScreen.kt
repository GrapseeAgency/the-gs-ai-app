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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TravelExplore
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
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
import com.grapsee.gsai.data.model.ModelCatalog
import com.grapsee.gsai.data.ModelPrefs
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.ConversationActionsSheet
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
 * The Home workbench answers three questions with REAL data only (no fake
 * features, no fabricated labels):
 *
 *  1. WHAT CAN GS DO?   — three seeded starters (each opens the real chat
 *     composer with a prompt) and a compact Tools/Workspaces group of five
 *     real destinations. No "Trending"/"Popular" fiction, no chip wall.
 *  2. WHAT WAS I DOING? — the Continue section, driven by the exact same
 *     Room flow the drawer's RECENT list uses (archived hidden, pins float,
 *     newest first). At most three conversations — continuity taste here,
 *     full history lives in Chats. No conversations → no section at all.
 *  3. WHAT CAN GS HELP WITH? — the composer entry: the dominant action,
 *     opening the real chat composer; press-and-hold the orb to dictate;
 *     mic opens full voice mode.
 *
 * Identity is real too: the greeting uses the name the user actually typed
 * at auth (AccountStore) and degrades to a neutral line when nothing is
 * stored. The old rotating tagline, hardcoded "Admin" greeting, hardcoded
 * model pill, fake attach affordance and "Upgrade plan" upsell are gone —
 * Billing stays reachable from the drawer's Account group, and attaching
 * files belongs to the real composer (Chat, step 4 scope).
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
    // snackbar (with a recovery action), never a system Toast: the snackbar
    // lives inside the app's own layout, respects the theme and can act.
    val snackbarHostState = remember { SnackbarHostState() }

    // Identity — the name the user actually typed at auth. Read per
    // composition: returning from Auth/Onboarding re-runs this body, so the
    // greeting reflects the stored identity with no observers.
    val displayName = AccountStore.displayName(context)

    // Continuation source — the SAME flow the drawer's RECENT list uses
    // (Room: archived hidden, pins float, newest first). No separate query
    // and no fabricated samples: an empty inbox renders neither Continue
    // nor Recents, and the starters take the space instead.
    val recents by remember {
        runCatching { ServiceLocator.chat.activeConversations() }.getOrElse { flowOf(emptyList()) }
    }.collectAsState(initial = emptyList())

    // Long-press actions — the exact sheet the drawer and Chats use, fed by
    // the same ServiceLocator mutations.
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
                // TalkBack visits: top bar → composer entry → scroll content
                // (orb → greeting → continue → starters → tools). The
                // composer is the primary action and is PINNED at the visual
                // bottom; traversal indices keep it early in the swipe order
                // instead of dead last. (The exact spec order orb → greeting
                // → composer is unachievable with the pinned-composer
                // skeleton, because the greeting lives inside the scrollable
                // middle — this is the closest compliant order.)
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
            // gsContentWidth() (STEP 2 foundation): phones unaffected, tablets/
            // landscape get the 640dp reading column.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .gsContentWidth()
                    .verticalScroll(rememberScrollState())
                    .semantics { traversalIndex = 2f },
                verticalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
            ) {
                Spacer(Modifier.height(GsMotion.spaceL))
                HeroBlock(displayName = displayName)

                // WHAT WAS I DOING? — the most recent conversation first,
                // then up to two more (three total, the drawer owns history).
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

                StartersSection(onNavigate = onNavigate)
                ToolsSection(onNavigate = onNavigate)
                // GS LiveUpdate — appears only when a newer build exists on GitHub.
                LiveUpdatePill()
            }

            // Pinned composer entry + disclaimer — one traversal group so
            // TalkBack reaches the primary action right after the top bar.
            Column(
                modifier = Modifier.semantics { traversalIndex = 1f }
            ) {
                Spacer(Modifier.height(GsMotion.spaceS))
                HeroInput(onNavigate = onNavigate, snackbarHostState = snackbarHostState)
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
    val context = LocalContext.current
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

        // Model pill — the REAL default model and mode (ModelPrefs), never a
        // hardcoded label. The mode suffix renders only when the stored mode
        // is one this model actually supports; otherwise the name alone.
        val model = ModelCatalog.byId(ModelPrefs.defaultId(context)) ?: ModelCatalog.default
        val mode = ModelPrefs.mode(context)
        val pillLabel = if (mode in model.modes) "${model.displayName} · $mode" else model.displayName
        val modelPillInteraction = remember { MutableInteractionSource() }
        Surface(
            shape = RoundedCornerShape(GsMotion.radiusChip),
            color = GsTheme.colors.raisedSurface,
            modifier = Modifier.kineticPress(modelPillInteraction)
        ) {
            Row(
                modifier = Modifier
                    .clickable(
                        interactionSource = modelPillInteraction,
                        indication = LocalIndication.current,
                        role = Role.Button,
                        // Announces with the real model name (the label text)
                        // and a factual action, not a hardcoded one.
                        onClickLabel = "Change model"
                    ) { onNavigate(GsRoutes.MODELS) }
                    .padding(
                        horizontal = GsMotion.spaceM,
                        vertical = 10.dp
                    ),
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
                    pillLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = GsTheme.colors.textPrimary
                )
            }
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

@Composable
private fun HeroBlock(displayName: String) {
    // Reduce-motion gate (reactive — the settings are snapshot state): when
    // on, the infinite breathe transition is NEVER created and the orb holds
    // its resting frame. No faster loop — no loop at all.
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
        // Brand orb — the one sanctioned aurora mark on the canvas (breathe reads in draw phase)
        Box(
            modifier = Modifier
                .size(84.dp)
                .graphicsLayer { scaleX = breathe; scaleY = breathe }
                .clip(CircleShape)
                .background(rememberAuroraBrush(CircleShape)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(CircleShape)
                    .background(GsTheme.colors.appBackground.copy(alpha = 0.35f))
            )
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = "GS",
                tint = GsTheme.colors.textPrimary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.height(GsMotion.spaceL))

        Text(
            greetingFor(displayName),
            style = MaterialTheme.typography.displayLarge,
            color = GsTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            // TalkBack: the greeting is the page heading.
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(GsMotion.spaceS))

        // One quiet static line — the old rotating tagline loop is gone
        // (infinite recomposition for zero information).
        Text(
            "What would you like to work on?",
            style = MaterialTheme.typography.bodyMedium,
            color = GsTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )

        // The old "Upgrade plan" pill is deliberately absent from Home:
        // Billing stays reachable from the drawer's Account group.
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
 * One real conversation row: title + relative time + (when the stored model
 * id is known to the catalogue) the model name. Tap opens the conversation;
 * long-press opens the same pin/rename/archive/delete sheet as the drawer.
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)
            ) {
                Text(
                    relativeTime(conversation.updatedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = GsTheme.colors.textSecondary
                )
                // Model indicator only from a real catalogue hit — an unknown
                // or absent model id is omitted, never guessed.
                modelNameFor(conversation.modelId)?.let { model ->
                    Text(
                        "· $model",
                        style = MaterialTheme.typography.labelMedium,
                        color = GsTheme.colors.textTertiary
                    )
                }
            }
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
 * WHAT CAN GS HELP WITH? — three honest seeds into the REAL chat composer.
 * Each one opens chat(null, prompt): no fake capability, just a head start.
 */
@Composable
private fun StartersSection(onNavigate: (String) -> Unit) {
    val starters = remember {
        listOf(
            Starter(Icons.Outlined.Description, "Summarise a PDF into a brief"),
            Starter(Icons.Outlined.EditNote, "Draft a launch email"),
            Starter(Icons.Outlined.School, "Explain a concept step by step")
        )
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
    ) {
        SectionLabel("Start something")
        starters.forEach { starter ->
            StarterRow(icon = starter.icon, label = starter.label) {
                onNavigate(GsRoutes.chat(null, starter.label))
            }
        }
    }
}

private data class Starter(val icon: ImageVector, val label: String)

@Composable
private fun StarterRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val rowInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GsRadius.mdShape())
            .kineticPress(rowInteraction)
            .clickable(
                interactionSource = rowInteraction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = "Start a chat"
            ) { onClick() }
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
    ) {
        Surface(
            shape = CircleShape,
            color = GsTheme.colors.raisedSurface,
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = GsTheme.colors.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = GsTheme.colors.textPrimary
        )
    }
}

/**
 * WHAT CAN GS DO? — capability discovery, honestly labelled: no "Trending",
 * no "Popular", no fabricated rankings. Five real destinations total — three
 * tools and two workspaces — each a compact raised-surface card.
 */
@Composable
private fun ToolsSection(onNavigate: (String) -> Unit) {
    // Static content, remembered once — recomposition-stable.
    val tools = remember {
        listOf(
            ToolEntry("Research", Icons.Outlined.TravelExplore, GsRoutes.RESEARCH),
            ToolEntry("Create", Icons.Outlined.AutoAwesome, GsRoutes.CREATE),
            ToolEntry("Search", Icons.Outlined.Search, GsRoutes.SEARCH)
        )
    }
    val workspaces = remember {
        listOf(
            ToolEntry("Projects", Icons.Outlined.Folder, GsRoutes.PROJECTS),
            ToolEntry("Assistants", Icons.Outlined.SmartToy, GsRoutes.ASSISTANTS)
        )
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
    ) {
        SectionLabel("Tools")
        Row(
            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            tools.forEach { entry ->
                ToolCard(entry = entry, modifier = Modifier.weight(1f), onNavigate = onNavigate)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            workspaces.forEach { entry ->
                ToolCard(entry = entry, modifier = Modifier.weight(1f), onNavigate = onNavigate)
            }
        }
    }
}

private data class ToolEntry(val label: String, val icon: ImageVector, val route: String)

@Composable
private fun ToolCard(
    entry: ToolEntry,
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit
) {
    val cardInteraction = remember { MutableInteractionSource() }
    Surface(
        shape = GsRadius.mdShape(),
        color = GsTheme.colors.raisedSurface,
        modifier = modifier.kineticPress(cardInteraction)
    ) {
        Row(
            modifier = Modifier
                .clickable(
                    interactionSource = cardInteraction,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClickLabel = "Open ${entry.label}"
                ) { onNavigate(entry.route) }
                .padding(horizontal = GsMotion.spaceS, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            Icon(
                entry.icon,
                contentDescription = null,
                tint = GsTheme.colors.accent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                entry.label,
                style = MaterialTheme.typography.labelLarge,
                color = GsTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Quiet uppercase section label — the same language as the drawer's groups. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = GsTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

/**
 * GS LiveUpdate pill — benchmark-quiet surface (raised + aurora dot, same
 * language as the model pill). Only rendered when a newer build is published:
 * "v0.2.0 ready" → tap → "Downloading update · 42%" → "Update ready · tap to
 * install" → system installer. A dropped stream resumes from the exact byte it
 * broke at, and if a download truly cannot finish the pill says so and stays
 * tappable — it never dissolves into nothing.
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
 * The composer ENTRY — the primary action of Home, visually dominant. A
 * raised bar that reads as the place to start: tapping it opens the real
 * chat composer (GsRoutes.chat(null)). This is an entry, not a composer:
 * there is no attach affordance here (attaching files belongs to the real
 * composer in Chat — step 4 scope) and the field never pretends to accept
 * typed text. The mic opens full voice mode; press-and-hold the orb to
 * dictate — live partials fill the bar, release hands the transcript to the
 * chat composer. A quick tap still opens full voice mode. Every failure path
 * dissolves quietly — nothing surfaces as an error.
 */
@Composable
private fun HeroInput(
    onNavigate: (String) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val view = LocalView.current

    // --- Voice press-and-hold -------------------------------------------------
    var listening by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("") }
    val recognizerRef = remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun quietReset() {
        listening = false
        transcript = ""
    }

    fun startRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            // Device has no speech service — hand the user to full voice mode.
            quietReset()
            onNavigate(GsRoutes.VOICE)
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
                    transcript = ""
                    if (!text.isNullOrBlank()) onNavigate(GsRoutes.chat(null, text))
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    transcript = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
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
            // The system dialog covers the app; if the user is still holding
            // when they return, the grant callback picks the hold right up.
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
            // and hands the user straight to the app settings (the system can
            // also keep asking — the launcher still re-fires on the next hold).
            // Then the orb quietly resets.
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

    // Background lifecycle (req 17): the dictation mic is a foreground-only
    // session. ON_STOP (home, recents, screen off) tears the recognizer down
    // immediately instead of leaving an open mic behind a stopped UI; the
    // dispose twin guarantees a created recognizer never outlives this canvas
    // — SpeechRecognizer must be destroyed explicitly, and leaking one per
    // hold eventually starves the system speech-service binding.
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

    val entryInteraction = remember { MutableInteractionSource() }
    Surface(
        shape = GsRadius.inputShape(),
        color = GsTheme.colors.raisedSurface,
        modifier = Modifier
            .fillMaxWidth()
            .kineticPress(entryInteraction)
    ) {
        Row(
            modifier = Modifier
                .clickable(
                    interactionSource = entryInteraction,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClickLabel = "Start a new chat"
                ) {
                    // Mid-dictation the entry does not navigate — releasing
                    // the orb decides what happens to the transcript.
                    if (!listening) onNavigate(GsRoutes.chat(null))
                }
                .padding(
                    horizontal = GsMotion.spaceS,
                    vertical = GsMotion.spaceS
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            Text(
                when {
                    listening && transcript.isBlank() -> "Listening…"
                    listening -> transcript
                    else -> "Ask GS anything…"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (listening) GsTheme.colors.textPrimary else GsTheme.colors.textPlaceholder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = GsMotion.spaceS)
            )

            // Mic — full voice mode
            val micInteraction = remember { MutableInteractionSource() }
            Surface(
                shape = CircleShape,
                color = androidx.compose.ui.graphics.Color.Transparent,
                modifier = Modifier
                    .size(42.dp)
                    .kineticPress(micInteraction)
            ) {
                Box(
                    modifier = Modifier.clickable(
                        interactionSource = micInteraction,
                        indication = LocalIndication.current
                    ) { onNavigate(GsRoutes.VOICE) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Mic,
                        contentDescription = "Voice input",
                        tint = GsTheme.colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Aurora orb — press-and-hold to dictate (the hero affordance)
            var orbHeld by remember { mutableStateOf(false) }
            val orbScale by animateFloatAsState(
                targetValue = if (orbHeld) 0.93f else 1f,
                animationSpec = GsMotion.standard(),
                label = "orbHold"
            )
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .scale(orbScale)
                    // TalkBack parity: the orb is a button — its double-tap
                    // opens voice mode exactly like a sighted tap; the hold
                    // behaviour stays a gesture the finger performs.
                    .semantics {
                        role = Role.Button
                        onClick(label = "Open voice mode") {
                            onNavigate(GsRoutes.VOICE)
                            true
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                orbHeld = true
                                var isHold = false
                                val timer = holdScope.launch {
                                    delay(VOICE_HOLD_TRIGGER_MS)
                                    isHold = true
                                    // Hold threshold crossed — the system's
                                    // quiet tick announces "dictation armed"
                                    // before the mic actually opens.
                                    view.gsHaptic(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                    beginVoiceHold()
                                }
                                val released = tryAwaitRelease()
                                orbHeld = false
                                timer.cancel()
                                when {
                                    isHold -> recognizerRef.value?.stopListening()
                                    released -> onNavigate(GsRoutes.VOICE)
                                    // else — gesture cancelled: quiet no-op
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (listening) VoicePulseHalo()
                Surface(
                    shape = CircleShape,
                    color = GsTheme.colors.accentSoft,
                    modifier = Modifier
                        .size(44.dp)
                        .kineticPress()
                ) {
                    Box(
                        modifier = Modifier
                            .background(rememberAuroraBrush(CircleShape), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.GraphicEq,
                            contentDescription = "Hold to talk",
                            tint = GsTheme.colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Breathing aurora halo around the hero orb while dictation is live. */
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

/** modelId → real catalogue display name; null/unknown ids yield null. */
private fun modelNameFor(modelId: String?): String? =
    modelId?.let { ModelCatalog.byId(it)?.displayName }

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
