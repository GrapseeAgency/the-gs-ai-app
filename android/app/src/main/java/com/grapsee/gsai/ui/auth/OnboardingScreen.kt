package com.grapsee.gsai.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.rememberAuroraBrush
import com.grapsee.gsai.ui.theme.GsEase
import com.grapsee.gsai.ui.theme.GsRadius
import com.grapsee.gsai.ui.icons.GsIcons

/** Six-step onboarding wizard — all selections are screen-local state. */
private enum class OnboardingStep { Interests, AiPreferences, Capabilities, Notifications, Personalisation, Ready }

private val interestOptions = listOf(
    "Writing", "Coding", "Research", "Business", "Design", "Education",
    "Science", "Language", "Productivity", "Entertainment", "Marketing", "Data"
)

// Accent choices come from the semantic layer's aurora family (GsTheme.colors.aurora)
private val accentNames = listOf("Aurora", "Sky", "Violet")

/** Kinetic horizontal slide shared by all step changes. */
private fun onboardingTransition(forward: Boolean): ContentTransform {
    val slideSpec = tween<IntOffset>(durationMillis = 220)
    return (
        slideInHorizontally(animationSpec = slideSpec) { full ->
            if (forward) full / 4 else -full / 4
        } + fadeIn(animationSpec = GsMotion.standard())
        ) togetherWith (
        slideOutHorizontally(animationSpec = slideSpec) { full ->
            if (forward) -full / 4 else full / 4
        } + fadeOut(animationSpec = GsMotion.standard())
        )
}

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    var stepIndex by remember { mutableIntStateOf(0) }
    val interests = remember { mutableStateListOf<String>() }
    var responseStyle by remember { mutableStateOf("Standard") }
    var personality by remember { mutableStateOf("Friendly") }
    val capabilities = remember { mutableStateListOf<String>() }
    var taskAlerts by remember { mutableStateOf(true) }
    var fileAlerts by remember { mutableStateOf(true) }
    var assistantUpdates by remember { mutableStateOf(false) }
    var productNews by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var accentIndex by remember { mutableIntStateOf(0) }
    // uiverse sour-dragon-56: the ONE onboarding tooltip. Armed once per visit,
    // retired by any press anywhere (observed without consuming, below).
    var showHint by remember { mutableStateOf(true) }

    val currentStep = OnboardingStep.entries[stepIndex]
    val canContinue = when (currentStep) {
        OnboardingStep.Interests -> interests.isNotEmpty()
        OnboardingStep.AiPreferences -> true
        OnboardingStep.Capabilities -> capabilities.isNotEmpty()
        OnboardingStep.Notifications -> true
        OnboardingStep.Personalisation -> true
        OnboardingStep.Ready -> true
    }

    // perf-smooth-2 (cause 5): the Ready progress sweep is animated INSIDE
    // ReadyStep now. The old screen-level animateFloatAsState re-composed the
    // whole onboarding screen every frame of the 1200ms sweep; the read now
    // lives in the only composable that draws it.

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(Unit) {
                // The tooltip's dismissal surface: every press on the screen
                // (buttons, chips, switches, empty canvas) retires it. Events
                // are observed, never consumed, so real controls still work.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    showHint = false
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GsMotion.spaceL, vertical = GsMotion.spaceM)
        ) {
            LinearProgressIndicator(
                progress = { (stepIndex + 1) / 6f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
            Spacer(Modifier.height(GsMotion.spaceS))
            Text(
                text = "Step ${stepIndex + 1} of 6",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedContent(
            targetState = stepIndex,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                onboardingTransition(forward = targetState > initialState)
            },
            label = "onboardingStep"
        ) { index ->
            when (OnboardingStep.entries[index]) {
                OnboardingStep.Interests -> InterestsStep(interests = interests, showHint = showHint)
                OnboardingStep.AiPreferences -> AiPreferencesStep(
                    responseStyle = responseStyle,
                    onStyleChange = { responseStyle = it },
                    personality = personality,
                    onPersonalityChange = { personality = it }
                )
                OnboardingStep.Capabilities -> CapabilitiesStep(capabilities = capabilities)
                OnboardingStep.Notifications -> NotificationsStep(
                    taskAlerts = taskAlerts,
                    onTaskAlertsChange = { taskAlerts = it },
                    fileAlerts = fileAlerts,
                    onFileAlertsChange = { fileAlerts = it },
                    assistantUpdates = assistantUpdates,
                    onAssistantUpdatesChange = { assistantUpdates = it },
                    productNews = productNews,
                    onProductNewsChange = { productNews = it }
                )
                OnboardingStep.Personalisation -> PersonalisationStep(
                    name = displayName,
                    onNameChange = { displayName = it },
                    accentIndex = accentIndex,
                    onAccentChange = { accentIndex = it },
                    onContinue = {
                        if (canContinue) {
                            if (stepIndex < OnboardingStep.entries.lastIndex) stepIndex++ else onFinished()
                        }
                    }
                )
                OnboardingStep.Ready -> ReadyStep(name = displayName)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GsMotion.spaceL, vertical = GsMotion.spaceM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            IconButton(
                onClick = { if (stepIndex > 0) stepIndex-- },
                enabled = stepIndex in 1..OnboardingStep.entries.lastIndex - 1
            ) {
                Icon(
                    imageVector = GsIcons.ChevronLeft,
                    contentDescription = "Back"
                )
            }
            GsArrowPillButton(
                label = if (currentStep == OnboardingStep.Ready) "Enter GS AI" else "Continue",
                enabled = canContinue,
                onClick = {
                    if (stepIndex < OnboardingStep.entries.lastIndex) stepIndex++ else onFinished()
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * uiverse clever-insect-61, materialised: the onboarding CONTINUE pill. Solid
 * primary fill (no gradient), label + trailing ArrowRight. Press slides the
 * arrow 4dp right and settles the pill 1dp down over 120ms ease-out; release
 * returns on the app's standard spring. Reduced motion holds the resting
 * frame: no shift, no sink.
 */
@Composable
private fun GsArrowPillButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = SettingsStore.reduceAnimations || SettingsStore.reduceMotion
    val settleSpec: AnimationSpec<Float> = when {
        reduced -> tween(1)
        pressed -> tween(durationMillis = 120, easing = EaseOut)
        else -> GsMotion.standard()
    }
    val arrowShift by animateFloatAsState(
        targetValue = if (pressed && !reduced) 4f else 0f,
        animationSpec = settleSpec,
        label = "continueArrowShift"
    )
    val pillSink by animateFloatAsState(
        targetValue = if (pressed && !reduced) 1f else 0f,
        animationSpec = settleSpec,
        label = "continuePillSink"
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(percent = 50),
        color = if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        },
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        },
        modifier = modifier
            .height(48.dp)
            .graphicsLayer { translationY = pillSink.dp.toPx() }
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = GsIcons.ArrowRight,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { translationX = arrowShift.dp.toPx() }
            )
        }
    }
}

/** Scrollable step body used by every wizard page. */
@Composable
private fun StepColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = GsMotion.spaceL)
    ) {
        content()
        Spacer(Modifier.height(GsMotion.spaceL))
    }
}

@Composable
private fun StepHeading(title: String, caption: String) {
    Spacer(Modifier.height(GsMotion.spaceS))
    Text(
        text = title,
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(Modifier.height(GsMotion.spaceS))
    Text(
        text = caption,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(GsMotion.spaceM))
}

@Composable
private fun InterestsStep(interests: SnapshotStateList<String>, showHint: Boolean) {
    StepColumn {
        StepHeading(
            title = "What are you into?",
            caption = "Pick at least one. GS tunes itself to your world. You can change these anytime."
        )
        if (showHint) {
            // uiverse sour-dragon-56: one tooltip, above the first control.
            HintBubble(text = "Tap the topics you like. GS uses them to tune your answers.")
            Spacer(Modifier.height(4.dp))
        }
        interestOptions.chunked(3).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
            ) {
                row.forEach { topic ->
                    GsChip(
                        text = topic,
                        selected = topic in interests,
                        onClick = {
                            if (topic in interests) interests.remove(topic) else interests.add(topic)
                        }
                    )
                }
            }
            Spacer(Modifier.height(GsMotion.spaceS))
        }
    }
}

/**
 * uiverse sour-dragon-56, materialised: a small dark bubble (inverseSurface on
 * inverseOnSurface, 12dp radius, 4dp tail) pointing at the first onboarding
 * control. Shown once; any press anywhere on the screen dismisses it.
 */
@Composable
private fun HintBubble(text: String) {
    val bubbleColor = MaterialTheme.colorScheme.inverseSurface
    Column {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = bubbleColor,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        Canvas(
            modifier = Modifier
                .padding(start = 24.dp)
                .size(width = 8.dp, height = 4.dp)
        ) {
            val tail = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(tail, color = bubbleColor)
        }
    }
}

@Composable
private fun AiPreferencesStep(
    responseStyle: String,
    onStyleChange: (String) -> Unit,
    personality: String,
    onPersonalityChange: (String) -> Unit
) {
    StepColumn {
        StepHeading(
            title = "Tune your AI",
            caption = "Set the defaults, every chat can override them."
        )
        PreferenceChipCard(
            title = "Response style",
            options = listOf("Concise", "Standard", "Detailed"),
            selected = responseStyle,
            onSelect = onStyleChange
        )
        Spacer(Modifier.height(GsMotion.spaceS))
        PreferenceChipCard(
            title = "Personality",
            options = listOf("Professional", "Friendly", "Playful"),
            selected = personality,
            onSelect = onPersonalityChange
        )
        // PHASE 2: the "Reasoning effort" cards are removed — reasoning-mode
        // vocabulary is not ordinary-user configuration (and never persisted).
    }
}

@Composable
private fun PreferenceChipCard(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    GsCard {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(GsMotion.spaceS))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            options.forEach { option ->
                GsChip(
                    text = option,
                    selected = option == selected,
                    onClick = { onSelect(option) }
                )
            }
        }
    }
}

@Composable
private fun CapabilitiesStep(capabilities: SnapshotStateList<String>) {
    StepColumn {
        StepHeading(
            title = "What should GS do first?",
            caption = "Choose the tools to pin to your command centre. Pick at least one."
        )
        val capabilityItems = listOf(
            Triple(GsIcons.NotebookPen, "Draft and write", "Docs, emails and posts in your voice"),
            Triple(GsIcons.Telescope, "Research the web", "Sourced answers with links"),
            Triple(GsIcons.Image, "Analyse images", "Charts, screenshots and photos"),
            Triple(GsIcons.Code, "Write and fix code", "Reviews, refactors and tests"),
            Triple(GsIcons.FileText, "Summarise documents", "PDFs, reports and long threads"),
            Triple(GsIcons.Lightbulb, "Brainstorm ideas", "Naming, plans and fresh angles")
        )
        capabilityItems.forEach { (icon, title, caption) ->
            CapabilityCard(
                icon = icon,
                title = title,
                caption = caption,
                selected = title in capabilities,
                onToggle = {
                    if (title in capabilities) capabilities.remove(title) else capabilities.add(title)
                }
            )
            Spacer(Modifier.height(GsMotion.spaceS))
        }
    }
}

@Composable
private fun CapabilityCard(
    icon: ImageVector,
    title: String,
    caption: String,
    selected: Boolean,
    onToggle: () -> Unit
) {
    // uiverse tiny-moose-42, materialised: a quiet multi-select card whose
    // check circle carries a 2dp accent ring and one static radial glow
    // (accent at 12% fading out) while selected; unselected is a quiet
    // monochrome outline. The check itself arrives with a scale-in state
    // change (180ms ease-in-out). No looping animation anywhere.
    val checkScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = GsEase.state(),
        label = "capabilityCheck"
    )
    GsCard(onClick = onToggle) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(GsRadius.md),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val ringColor by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                animationSpec = GsEase.state(),
                label = "capabilityCheckRing"
            )
            val glowColor = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .drawBehind {
                        if (selected) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        glowColor.copy(alpha = 0.12f),
                                        Color.Transparent
                                    ),
                                    center = center,
                                    radius = size.maxDimension
                                ),
                                radius = size.maxDimension
                            )
                        }
                    }
                    .border(2.dp, ringColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = GsIcons.Check,
                    contentDescription = if (selected) "Selected" else null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer {
                            scaleX = checkScale
                            scaleY = checkScale
                            alpha = checkScale
                        }
                )
            }
        }
    }
}

@Composable
private fun NotificationsStep(
    taskAlerts: Boolean,
    onTaskAlertsChange: (Boolean) -> Unit,
    fileAlerts: Boolean,
    onFileAlertsChange: (Boolean) -> Unit,
    assistantUpdates: Boolean,
    onAssistantUpdatesChange: (Boolean) -> Unit,
    productNews: Boolean,
    onProductNewsChange: (Boolean) -> Unit
) {
    StepColumn {
        StepHeading(
            title = "Stay in the loop",
            caption = "Quiet by default, only the pings you actually want."
        )
        GsCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SwitchRow(title = "Task completions", checked = taskAlerts, onCheckedChange = onTaskAlertsChange)
                SwitchRow(title = "File processed", checked = fileAlerts, onCheckedChange = onFileAlertsChange)
                SwitchRow(title = "Assistant updates", checked = assistantUpdates, onCheckedChange = onAssistantUpdatesChange)
                SwitchRow(title = "Product news", checked = productNews, onCheckedChange = onProductNewsChange)
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(GsMotion.spaceM))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PersonalisationStep(
    name: String,
    onNameChange: (String) -> Unit,
    accentIndex: Int,
    onAccentChange: (Int) -> Unit,
    onContinue: () -> Unit
) {
    StepColumn {
        StepHeading(
            title = "Make it yours",
            caption = "A name and an accent, that's all GS needs."
        )
        GsCard {
            Text(
                text = "Name",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(GsMotion.spaceS))
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("What should GS call you?") },
                placeholder = { Text("e.g. Alex") },
                singleLine = true,
                // Done on the keyboard action continues the flow — the action
                // key on a singleLine field otherwise does nothing at all.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onContinue() })
            )
        }
        Spacer(Modifier.height(GsMotion.spaceS))
        GsCard {
            Text(
                text = "Accent",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(GsMotion.spaceS))
            Row(horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceL)) {
                val accentColors = GsTheme.colors.aurora
                accentColors.forEachIndexed { index, color ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            onClick = { onAccentChange(index) },
                            shape = CircleShape,
                            color = color,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (index == accentIndex) {
                                    Icon(
                                        imageVector = GsIcons.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(GsMotion.spaceXS))
                        Text(
                            text = accentNames[index],
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** The Ready step: aurora progress sweeps to full while the serif headline lands. */
@Composable
private fun ReadyStep(name: String) {
    // perf-smooth-2 (cause 5): this is the single sanctioned aurora moment --
    // the Ready setup bar sweeping 0 -> 1. The 1200ms animation and its read
    // both live in this scope, so the sweep re-composes only this step.
    val readyProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 1200),
        label = "readyProgress"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = GsMotion.spaceL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(readyProgress.coerceIn(0.02f, 1f))
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(rememberAuroraBrush(shape = CircleShape))
            )
        }
        Spacer(Modifier.height(GsMotion.spaceXL))
        Text(
            text = if (name.isBlank()) "You're all set" else "You're all set, $name",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(GsMotion.spaceS))
        Text(
            text = "Your command centre is ready.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(GsMotion.spaceXS))
        Text(
            text = "Interests, models and tools are tuned to how you work.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
