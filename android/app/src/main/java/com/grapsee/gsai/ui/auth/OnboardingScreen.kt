package com.grapsee.gsai.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.theme.Aeruo
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.rememberAuroraBrush

/** Six-step onboarding wizard — all selections are screen-local state. */
private enum class OnboardingStep { Interests, AiPreferences, Capabilities, Notifications, Personalisation, Ready }

private val interestOptions = listOf(
    "Writing", "Coding", "Research", "Business", "Design", "Education",
    "Science", "Language", "Productivity", "Entertainment", "Marketing", "Data"
)

// Accent choices are token colors from Color.kt (Aurora accent family).
private val accentColors = listOf(Aeruo.Accent, Aeruo.Aurora[1], Aeruo.Aurora[2])
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
    var responseStyle by remember { mutableStateOf("Balanced") }
    var reasoningEffort by remember { mutableStateOf("Medium") }
    var personality by remember { mutableStateOf("Friendly") }
    val capabilities = remember { mutableStateListOf<String>() }
    var taskAlerts by remember { mutableStateOf(true) }
    var fileAlerts by remember { mutableStateOf(true) }
    var assistantUpdates by remember { mutableStateOf(false) }
    var productNews by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var accentIndex by remember { mutableIntStateOf(0) }

    val currentStep = OnboardingStep.entries[stepIndex]
    val canContinue = when (currentStep) {
        OnboardingStep.Interests -> interests.isNotEmpty()
        OnboardingStep.AiPreferences -> true
        OnboardingStep.Capabilities -> capabilities.isNotEmpty()
        OnboardingStep.Notifications -> true
        OnboardingStep.Personalisation -> true
        OnboardingStep.Ready -> true
    }

    // The single sanctioned aurora moment: the Ready setup bar sweeping 0 -> 1.
    val readyProgress by animateFloatAsState(
        targetValue = if (currentStep == OnboardingStep.Ready) 1f else 0f,
        animationSpec = tween(durationMillis = 1200),
        label = "readyProgress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
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
                OnboardingStep.Interests -> InterestsStep(interests = interests)
                OnboardingStep.AiPreferences -> AiPreferencesStep(
                    responseStyle = responseStyle,
                    onStyleChange = { responseStyle = it },
                    reasoningEffort = reasoningEffort,
                    onEffortChange = { reasoningEffort = it },
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
                    onAccentChange = { accentIndex = it }
                )
                OnboardingStep.Ready -> ReadyStep(name = displayName, progress = readyProgress)
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
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = "Back"
                )
            }
            Button(
                onClick = {
                    if (stepIndex < OnboardingStep.entries.lastIndex) stepIndex++ else onFinished()
                },
                enabled = canContinue,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (currentStep == OnboardingStep.Ready) "Enter GS AI" else "Continue"
                )
            }
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
private fun InterestsStep(interests: SnapshotStateList<String>) {
    StepColumn {
        StepHeading(
            title = "What are you into?",
            caption = "Pick at least one — GS tunes itself to your world. You can change these anytime."
        )
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

@Composable
private fun AiPreferencesStep(
    responseStyle: String,
    onStyleChange: (String) -> Unit,
    reasoningEffort: String,
    onEffortChange: (String) -> Unit,
    personality: String,
    onPersonalityChange: (String) -> Unit
) {
    StepColumn {
        StepHeading(
            title = "Tune your AI",
            caption = "Set the defaults — every chat can override them."
        )
        PreferenceChipCard(
            title = "Response style",
            options = listOf("Concise", "Balanced", "Detailed"),
            selected = responseStyle,
            onSelect = onStyleChange
        )
        Spacer(Modifier.height(GsMotion.spaceS))
        PreferenceChipCard(
            title = "Reasoning effort",
            options = listOf("Low", "Medium", "High"),
            selected = reasoningEffort,
            onSelect = onEffortChange
        )
        Spacer(Modifier.height(GsMotion.spaceS))
        PreferenceChipCard(
            title = "Personality",
            options = listOf("Professional", "Friendly", "Playful"),
            selected = personality,
            onSelect = onPersonalityChange
        )
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
            Triple(Icons.Outlined.EditNote, "Draft and write", "Docs, emails and posts in your voice"),
            Triple(Icons.Outlined.TravelExplore, "Research the web", "Sourced answers with links"),
            Triple(Icons.Outlined.Image, "Analyse images", "Charts, screenshots and photos"),
            Triple(Icons.Outlined.Code, "Write and fix code", "Reviews, refactors and tests"),
            Triple(Icons.Outlined.Summarize, "Summarise documents", "PDFs, reports and long threads"),
            Triple(Icons.Outlined.Lightbulb, "Brainstorm ideas", "Naming, plans and fresh angles")
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
    GsCard(onClick = onToggle) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
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
                            MaterialTheme.colorScheme.primary
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
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
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
            caption = "Quiet by default — only the pings you actually want."
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
    onAccentChange: (Int) -> Unit
) {
    StepColumn {
        StepHeading(
            title = "Make it yours",
            caption = "A name and an accent — that's all GS needs."
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
                singleLine = true
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
                                        imageVector = Icons.Outlined.Check,
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
private fun ReadyStep(name: String, progress: Float) {
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
                    .fillMaxWidth(progress.coerceIn(0.02f, 1f))
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
