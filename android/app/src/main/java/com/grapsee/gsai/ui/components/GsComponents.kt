package com.grapsee.gsai.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.theme.auroraBackground
import com.grapsee.gsai.ui.theme.Aeruo
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.LocalHighContrast
import com.grapsee.gsai.ui.theme.LocalScreenReaderHints
import com.grapsee.gsai.ui.theme.kineticPress
import com.grapsee.gsai.ui.theme.skeletonSurface
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction

/**
 * AERUO KINETIC shared components — FROZEN API. All screens compose these.
 *
 * Accessibility wiring (Task 86-d), resolved here so NO call site changes:
 *  - LocalScreenReaderHints: when the Settings toggle is on, actionable
 *    components label their click action for TalkBack — Compose 1.7 has NO
 *    `hint` semantics property (verified against the resolved ui-android
 *    artifact), so the real, no-visual channel for activation context is the
 *    labeled OnClick action: TalkBack announces "double-tap to <label>". The
 *    label is set on a non-mergeable DESCENDANT of the clickable node, where
 *    the built-in action-key merge policy (parent action ?: child action,
 *    parent label ?: child label) keeps the real click and takes our label.
 *    Only set where a click affordance exists — a hint must never lie.
 *  - LocalHighContrast: when on, hairline borders resolve to the strongest
 *    ink token and muted/secondary text resolves to full-alpha onSurface.
 *    Off = byte-identical to the previous colors.
 */

/** High-contrast resolver: [strong] when the Settings toggle is on, [base]
 *  otherwise (off = today's colors exactly). Component-layer helper only. */
@Composable
private fun hcColor(base: Color, strong: Color): Color =
    if (LocalHighContrast.current) strong else base

@Composable
fun GsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            // TalkBack: section headers navigate as headings.
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        if (actionLabel != null) {
            if (onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, color = MaterialTheme.colorScheme.primary)
                }
            } else {
                // A count/metadata label, not a button: rendered as plain text
                // (same type ramp and tint) so it never fakes pressability.
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GsCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    // One interaction source shared by the click and the press-scale —
    // touching the card visibly responds (the platform touch-feedback contract).
    val interaction = remember { MutableInteractionSource() }
    // Screen reader hints: only on an actually-actionable card (a click label
    // on a non-clickable card would lie about an affordance).
    val hintModifier: Modifier = if (LocalScreenReaderHints.current) {
        onClick?.let { click ->
            Modifier.semantics { onClick("activate") { click(); true } }
        } ?: Modifier
    } else {
        Modifier
    }
    val base = modifier
        .fillMaxWidth()
        .let { if (onClick != null || onLongClick != null) it.kineticPress(interaction) else it }
    if (onLongClick != null) {
        // Long-pressable card: one gesture pipeline handles tap AND hold
        // (no parallel pointerInput racing the click), and the hold is
        // exposed to assistive tech through onLongClickLabel — a gesture
        // invisible to TalkBack is a feature that does not exist.
        Surface(
            modifier = base.combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick ?: {},
                onLongClickLabel = "More options",
                onLongClick = onLongClick
            ),
            shape = RoundedCornerShape(GsMotion.radiusCard),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, hcColor(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.onSurface))
        ) {
            // The label rides the content node (a non-mergeable descendant) so the
            // action-key merge policy applies it over the Surface's unlabeled click.
            Column(modifier = Modifier.padding(GsMotion.spaceM).then(hintModifier), content = content)
        }
    } else {
        Surface(
            modifier = base,
            shape = RoundedCornerShape(GsMotion.radiusCard),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, hcColor(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.onSurface)),
            onClick = onClick ?: {},
            enabled = onClick != null,
            interactionSource = interaction
        ) {
            // The label rides the content node (a non-mergeable descendant) so the
            // action-key merge policy applies it over the Surface's unlabeled click.
            Column(modifier = Modifier.padding(GsMotion.spaceM).then(hintModifier), content = content)
        }
    }
}

@Composable
fun GsChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    // null = a pure status/label chip: no click semantics, no ripple, no press
    // machinery — only real controls carry state layers.
    onClick: (() -> Unit)? = null
) {
    val chipClick = onClick
    if (chipClick != null) {
        val interaction = remember { MutableInteractionSource() }
        // Chips carry selected-state semantics, so the announced click stays the
        // factual "activate" — some chips toggle selection, some just act ("Try
        // again"), and a hint must never claim the wrong affordance.
        val chipHintModifier: Modifier = if (LocalScreenReaderHints.current) {
            Modifier.semantics { onClick("activate") { chipClick(); true } }
        } else {
            Modifier
        }
        Surface(
            modifier = modifier
                .kineticPress(interaction)
                .then(chipHintModifier),
            shape = RoundedCornerShape(GsMotion.radiusChip),
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceContainer,
            onClick = chipClick,
            interactionSource = interaction
        ) {
            ChipLabel(text, selected, chipHintModifier)
        }
    } else {
        // null = a pure status/label chip: no click semantics, no ripple, no
        // press machinery — only real controls carry state layers.
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(GsMotion.radiusChip),
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceContainer
        ) {
            ChipLabel(text, selected)
        }
    }
}

@Composable
private fun ChipLabel(
    text: String,
    selected: Boolean,
    hintModifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface,
        // Chips are toggles/filters: expose the selected state to TalkBack
        // (declared here so it merges into the clickable Surface node).
        modifier = Modifier
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .then(hintModifier)
            .semantics {
                this.selected = selected
                role = Role.Button
            }
    )
}

@Composable
fun GsListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    // Screen reader hints: rows with a click open their detail surface —
    // factual for every actionable GsListItem in this app (rows navigate;
    // toggles use dedicated switch rows instead).
    val hintModifier: Modifier = if (LocalScreenReaderHints.current) {
        onClick?.let { click ->
            Modifier.semantics { onClick("open details") { click(); true } }
        } ?: Modifier
    } else {
        Modifier
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.kineticPress(interaction) else it },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick ?: {},
        enabled = onClick != null,
        interactionSource = interaction
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .then(hintModifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = hcColor(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSurface),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            trailing?.invoke(this)
        }
    }
}

@Composable
fun GsInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Ask anything…",
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Default,
    // Swap the paper-plane for a field-appropriate affordance (a Search icon
    // on query fields) without changing the shared bar's look anywhere else.
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = when (imeAction) {
            ImeAction.Send -> KeyboardActions(onSend = {
                if (value.isNotBlank()) onSend(value.trim())
            })
            // IME parity: a Search action commits the query — results are
            // already live, so commit means fire onSend and dismiss the
            // keyboard exactly like the platform search fields.
            ImeAction.Search -> KeyboardActions(onSearch = {
                keyboard?.hide()
                if (value.isNotBlank()) onSend(value.trim())
            })
            else -> KeyboardActions.Default
        },
        placeholder = { Text(placeholder, color = hcColor(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSurface)) },
        shape = RoundedCornerShape(GsMotion.radiusInput),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = hcColor(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.onSurface),
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        trailingIcon = trailingIcon ?: {
            IconButton(
                onClick = { if (value.isNotBlank()) onSend(value.trim()) },
                enabled = enabled && value.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Send,
                    contentDescription = "Send",
                    tint = if (value.isNotBlank()) MaterialTheme.colorScheme.primary
                    else hcColor(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSurface)
                )
            }
        },
        maxLines = 4
    )
}

@Composable
fun GsEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(GsMotion.spaceXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(30.dp))
            }
        }
        Text(title, style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground)
        Text(message, style = MaterialTheme.typography.bodySmall,
            color = hcColor(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.padding(horizontal = GsMotion.spaceL))
    }
}

/** Aurora-pulsed loading state — the AI is alive. */
@Composable
fun GsLoadingState(label: String = "Thinking", modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(GsMotion.spaceL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
    ) {
        Surface(
            shape = RoundedCornerShape(GsMotion.radiusCard),
            modifier = Modifier.fillMaxWidth().height(10.dp)
        ) {
            Box(Modifier.auroraBackground(RoundedCornerShape(GsMotion.radiusCard)))
        }
        Text(label, style = MaterialTheme.typography.labelLarge,
            color = hcColor(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSurface))
    }
}

@Composable
fun GsErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(GsMotion.spaceL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium,
            color = hcColor(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSurface))
        GsChip(text = "Try again", selected = false, onClick = onRetry)
    }
}

@Composable
fun GsOfflineBanner(visible: Boolean, modifier: Modifier = Modifier) {
    // Presence animates in from the layout — the network state change reads as
    // the system reacting, not a card teleporting in mid-content.
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(180)) +
            expandVertically(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(150)) +
            shrinkVertically(animationSpec = tween(150)),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.CloudOff, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp))
                Text("You're offline — changes will sync when you reconnect.",
                    style = MaterialTheme.typography.labelMedium,
                    color = hcColor(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSurface))
            }
        }
    }
}

@Composable
fun GsQuickActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val hintModifier: Modifier = if (LocalScreenReaderHints.current) {
        Modifier.semantics { onClick("activate $label") { onClick(); true } }
    } else {
        Modifier
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.size(56.dp).kineticPress(interaction),
            onClick = onClick,
            interactionSource = interaction
        ) {
            // The label rides this inner node (a non-mergeable descendant of
            // the clickable Surface) so the action-key merge policy applies it
            // over the Surface's unlabeled click.
            Box(contentAlignment = Alignment.Center, modifier = Modifier.then(hintModifier)) {
                Icon(icon, contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = hcColor(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSurface),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Skeleton block for loading lists. */
@Composable
fun GsSkeleton(height: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .skeletonSurface(RoundedCornerShape(12.dp), MaterialTheme.colorScheme.surfaceContainer)
    )
}

/** Standard screen scaffold: top bar with optional back + actions.
 *
 * The scaffold owns the system-bar insets: statusBarsPadding keeps the title
 * clear of the status bar and navigationBarsPadding keeps content clear of the
 * gesture bar on every screen that composes it (callers must NOT add their
 * own — the padding would double). IME insets stay a per-screen concern:
 * screens with text input layer imePadding() on their scroll/content region,
 * which composes cleanly on top of the consumed navigation-bar inset. */
@Composable
fun GsScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = GsMotion.spaceM)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = GsMotion.spaceM),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground)
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            actions()
        }
        Spacer(Modifier.height(GsMotion.spaceS))
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = GsMotion.spaceM),
            content = content
        )
    }
}

// --- edge states -------------------------------------------------------------

/**
 * Blank or whitespace-only titles (legacy rows, interrupted syncs, stray
 * server data) can never render as an empty line: every conversation title
 * surface funnels through this fallback.
 */
fun gsConversationTitle(raw: String?): String =
    raw?.trim()?.takeIf { it.isNotEmpty() } ?: "Untitled chat"

// --- connectivity --------------------------------------------------------------

/**
 * The offline banner tracks the PHONE's connectivity only. A quiet backend is
 * handled invisibly (GS Lite local replies + Room persistence), so an unreachable
 * server never presents itself as an error state to the user. Shared by the
 * Chats hub and the chat surface — anywhere a dead connection must be honest.
 */
@Composable
fun rememberDeviceOffline(): State<Boolean> {
    val context = LocalContext.current
    val offline = remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        fun refresh() {
            val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            offline.value = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true
        }
        refresh()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                offline.value = false
            }

            override fun onLost(network: Network) {
                refresh()
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                offline.value = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }
    return offline
}
