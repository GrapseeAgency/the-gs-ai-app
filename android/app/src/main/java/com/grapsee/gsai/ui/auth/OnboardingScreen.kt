package com.grapsee.gsai.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.tween
import com.grapsee.gsai.data.AccountStore
import com.grapsee.gsai.data.OnboardingProfileStore
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.components.RefAccentRow
import com.grapsee.gsai.ui.components.RefArt
import com.grapsee.gsai.ui.components.RefArtwork
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefChip
import com.grapsee.gsai.ui.components.RefChoiceRow
import com.grapsee.gsai.ui.components.RefDivider
import com.grapsee.gsai.ui.components.RefFootnote
import com.grapsee.gsai.ui.components.RefIconButton
import com.grapsee.gsai.ui.components.RefPrimaryButton
import com.grapsee.gsai.ui.components.RefQuietButton
import com.grapsee.gsai.ui.components.RefTextField
import com.grapsee.gsai.ui.components.RefToggleRow
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsAccents
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.GsTheme

/**
 * The setup wizard — six real questions, every answer kept.
 *
 * This is the reference language end to end: a bare back chevron, one
 * segmented progress line, a hand-drawn scene, then groups in cream glass.
 * The old version asked for interests, capabilities, a response style and a
 * name and then discarded all of them (they lived in `remember`). Now:
 *
 *   interests / capabilities / style / personality -> OnboardingProfileStore,
 *     read by Home suggestions, the chat empty state and the local responder;
 *   name -> AccountStore (the same source the drawer and Profile read);
 *   accent -> SettingsStore.updateAccentId, applied live so the next tap is
 *     already in the chosen colour.
 */
private enum class SetupStep { Interests, Voice, Tools, Quiet, Personal, Ready }

private val interestOptions = listOf(
    "Writing", "Coding", "Research", "Business", "Design", "Education",
    "Science", "Language", "Productivity", "Marketing", "Data", "Travel"
)

private val responseStyles = listOf(
    Triple("Concise", "Short answers, no preamble", GsIcons.Zap),
    Triple("Standard", "A balanced answer with context", GsIcons.MessageCircle),
    Triple("Detailed", "Thorough, with reasoning and examples", GsIcons.BookOpen)
)

private val personalityOptions = listOf(
    Triple("Professional", "Direct, neutral, work-first", GsIcons.Briefcase),
    Triple("Friendly", "Warm, plain language, a little humour", GsIcons.Heart),
    Triple("Playful", "Light, curious, more personality", GsIcons.Wand)
)

private data class CapabilityOption(val title: String, val caption: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val capabilityOptions = listOf(
    CapabilityOption("Draft and write", "Docs, emails and posts in your voice", GsIcons.NotebookPen),
    CapabilityOption("Research the web", "Sourced answers with links", GsIcons.Globe),
    CapabilityOption("Analyse images", "Charts, screenshots and photos", GsIcons.ImagePlus),
    CapabilityOption("Write and fix code", "Reviews, refactors and tests", GsIcons.Code),
    CapabilityOption("Summarise documents", "PDFs, reports and long threads", GsIcons.FileText),
    CapabilityOption("Think it through", "Naming, plans and fresh angles", GsIcons.Lightbulb)
)

private fun setupTransition(forward: Boolean): ContentTransform {
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
    val context = LocalContext.current
    var stepIndex by remember { mutableIntStateOf(0) }
    val currentStep = SetupStep.entries[stepIndex]

    val interests = remember {
        mutableStateListOf<String>().apply { addAll(OnboardingProfileStore.interests.sorted()) }
    }
    val capabilities = remember {
        mutableStateListOf<String>().apply { addAll(OnboardingProfileStore.capabilities.sorted()) }
    }
    var responseStyle by remember { mutableStateOf(OnboardingProfileStore.responseStyle) }
    var personality by remember { mutableStateOf(OnboardingProfileStore.personality) }
    var displayName by remember { mutableStateOf(AccountStore.displayName(context)) }
    var accentIndex by remember {
        mutableIntStateOf(GsAccents.all.indexOfFirst { it.id == SettingsStore.accentId }.coerceAtLeast(0))
    }

    val canContinue = when (currentStep) {
        SetupStep.Interests -> interests.isNotEmpty()
        SetupStep.Tools -> capabilities.isNotEmpty()
        else -> true
    }

    fun commitAndFinish() {
        AccountStore.setDisplayName(context, displayName)
        OnboardingProfileStore.save(
            interests = interests,
            capabilities = capabilities,
            responseStyle = responseStyle,
            personality = personality
        )
        onFinished()
    }

    fun next() {
        if (!canContinue) return
        if (stepIndex < SetupStep.entries.lastIndex) stepIndex++ else commitAndFinish()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Bare top bar: back, the step's position, and one honest escape.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (stepIndex > 0) {
                RefIconButton(
                    icon = GsIcons.ChevronLeft,
                    label = "Back",
                    onClick = { stepIndex-- }
                )
            } else {
                Spacer(Modifier.width(40.dp))
            }
            Text(
                text = "STEP ${stepIndex + 1} OF ${SetupStep.entries.size}",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 10.5.sp,
                    letterSpacing = 1.3.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = GsTheme.colors.textTertiary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Skip",
                style = MaterialTheme.typography.labelMedium,
                color = GsTheme.colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClickLabel = "Skip setup",
                        onClick = onFinished
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        // Segmented progress — six marks, the walked ones filled.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            repeat(SetupStep.entries.size) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (index <= stepIndex) GsTheme.colors.accent
                            else GsTheme.colors.border
                        )
                )
            }
        }

        AnimatedContent(
            targetState = stepIndex,
            modifier = Modifier.weight(1f),
            transitionSpec = { setupTransition(forward = targetState > initialState) },
            label = "setupStep"
        ) { index ->
            when (SetupStep.entries[index]) {
                SetupStep.Interests -> InterestsStep(interests)
                SetupStep.Voice -> VoiceStep(
                    responseStyle = responseStyle,
                    onStyle = { responseStyle = it },
                    personality = personality,
                    onPersonality = { personality = it }
                )
                SetupStep.Tools -> ToolsStep(capabilities)
                SetupStep.Quiet -> QuietStep()
                SetupStep.Personal -> PersonalStep(
                    name = displayName,
                    onName = { displayName = it },
                    accentIndex = accentIndex,
                    onAccent = { index ->
                        accentIndex = index
                        SettingsStore.updateAccentId(GsAccents.all[index].id)
                    },
                    onDone = { next() }
                )
                SetupStep.Ready -> ReadyStep(
                    name = displayName,
                    interests = interests,
                    style = responseStyle,
                    personality = personality,
                    tools = capabilities
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!canContinue) {
                Text(
                    text = if (currentStep == SetupStep.Interests) {
                        "Pick at least one starting point, or skip setup."
                    } else {
                        "Choose at least one tool, or skip setup."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = GsTheme.colors.textTertiary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (stepIndex > 0) {
                    RefQuietButton(
                        label = "Back",
                        onClick = { stepIndex-- },
                        modifier = Modifier.width(104.dp)
                    )
                }
                RefPrimaryButton(
                    label = if (currentStep == SetupStep.Ready) "Enter GS AI" else "Continue",
                    onClick = { next() },
                    enabled = canContinue,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StepBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        content()
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun StepHero(art: RefArt, size: Int = 132) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        RefArtwork(art = art, size = size.dp)
    }
}

@Composable
private fun StepHeading(eyebrow: String, title: String, caption: String) {
    Spacer(Modifier.height(10.dp))
    Text(
        text = eyebrow.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 10.5.sp,
            letterSpacing = 1.4.sp,
            fontWeight = FontWeight.SemiBold
        ),
        color = GsTheme.colors.accentStrong
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.displaySmall,
        color = GsTheme.colors.textPrimary
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = caption,
        style = MaterialTheme.typography.bodyMedium,
        color = GsTheme.colors.textSecondary
    )
    Spacer(Modifier.height(16.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterestsStep(interests: MutableList<String>) {
    StepBody {
        StepHero(RefArt.Bloom, 138)
        StepHeading(
            eyebrow = "A warmer start",
            title = "What should we make of today?",
            caption = "Pick the things you reach for. GS uses them to choose the first suggestions you see — you can change them any time."
        )
        RefCard {
            Text(
                text = "I'm mostly here for",
                style = MaterialTheme.typography.titleSmall,
                color = GsTheme.colors.textPrimary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp)
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                interestOptions.forEach { topic ->
                    RefChip(
                        text = topic,
                        selected = topic in interests,
                        onClick = {
                            if (topic in interests) interests.remove(topic) else interests.add(topic)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceStep(
    responseStyle: String,
    onStyle: (String) -> Unit,
    personality: String,
    onPersonality: (String) -> Unit,
) {
    StepBody {
        StepHero(RefArt.Conversation, 112)
        StepHeading(
            eyebrow = "Tune the voice",
            title = "How should GS sound?",
            caption = "The default for every new chat. A conversation can always ask for something different."
        )
        RefCard {
            Text(
                text = "Answer length",
                style = MaterialTheme.typography.titleSmall,
                color = GsTheme.colors.textPrimary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
            )
            responseStyles.forEachIndexed { index, (label, caption, icon) ->
                if (index > 0) RefDivider(startIndent = 66.dp)
                RefChoiceRow(
                    title = label,
                    subtitle = caption,
                    icon = icon,
                    selected = responseStyle == label,
                    onClick = { onStyle(label) }
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(12.dp))
        RefCard {
            Text(
                text = "Personality",
                style = MaterialTheme.typography.titleSmall,
                color = GsTheme.colors.textPrimary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
            )
            personalityOptions.forEachIndexed { index, (label, caption, icon) ->
                if (index > 0) RefDivider(startIndent = 66.dp)
                RefChoiceRow(
                    title = label,
                    subtitle = caption,
                    icon = icon,
                    selected = personality == label,
                    onClick = { onPersonality(label) }
                )
            }
        }
    }
}

@Composable
private fun ToolsStep(capabilities: MutableList<String>) {
    StepBody {
        StepHero(RefArt.Library, 104)
        StepHeading(
            eyebrow = "Your toolkit",
            title = "What should GS do first?",
            caption = "These become the quick actions on Home. Pick the ones you actually use."
        )
        RefCard {
            capabilityOptions.forEachIndexed { index, option ->
                if (index > 0) RefDivider(startIndent = 66.dp)
                RefChoiceRow(
                    title = option.title,
                    subtitle = option.caption,
                    icon = option.icon,
                    selected = option.title in capabilities,
                    onClick = {
                        if (option.title in capabilities) capabilities.remove(option.title)
                        else capabilities.add(option.title)
                    }
                )
            }
        }
    }
}

@Composable
private fun QuietStep() {
    StepBody {
        StepHero(RefArt.Privacy, 104)
        StepHeading(
            eyebrow = "Quiet by default",
            title = "Only the pings you want",
            caption = "No marketing, no streaks, no badges begging for attention. You can change both of these later in Settings."
        )
        RefCard {
            RefToggleRow(
                title = "Reply ready",
                subtitle = "One quiet notice when an answer finishes while GS is in the background",
                icon = GsIcons.Bell,
                checked = SettingsStore.pushNotifications,
                onCheckedChange = { SettingsStore.updatePushNotifications(it) }
            )
            RefDivider()
            RefToggleRow(
                title = "Haptic feedback",
                subtitle = "A soft tap when a message sends or an action commits",
                icon = GsIcons.Smartphone,
                checked = SettingsStore.haptics,
                onCheckedChange = { SettingsStore.updateHaptics(it) }
            )
        }
        RefFootnote("GS never sends product news to your lock screen.")
    }
}

@Composable
private fun PersonalStep(
    name: String,
    onName: (String) -> Unit,
    accentIndex: Int,
    onAccent: (Int) -> Unit,
    onDone: () -> Unit,
) {
    val family = GsAccents.all[accentIndex.coerceIn(0, GsAccents.all.lastIndex)]
    StepBody {
        StepHero(RefArt.Success, 96)
        StepHeading(
            eyebrow = "Make it yours",
            title = "A name and a colour",
            caption = "That's all GS needs. Your name only appears in greetings and your account row."
        )
        RefTextField(
            value = name,
            onValueChange = onName,
            label = "What should GS call you?",
            placeholder = "e.g. Alex",
            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            onDone = onDone,
            leadingIcon = GsIcons.UserRound
        )
        Spacer(Modifier.height(16.dp))
        RefCard {
            Text(
                text = "Accent",
                style = MaterialTheme.typography.titleSmall,
                color = GsTheme.colors.textPrimary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp)
            )
            Spacer(Modifier.height(12.dp))
            RefAccentRow(
                selectedId = family.id,
                onSelect = { id ->
                    val index = GsAccents.all.indexOfFirst { it.id == id }
                    if (index >= 0) onAccent(index)
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "${family.label} — used for buttons, switches and the bloom behind this page.",
                style = MaterialTheme.typography.labelMedium,
                color = GsTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ReadyStep(
    name: String,
    interests: List<String>,
    style: String,
    personality: String,
    tools: List<String>,
) {
    StepBody {
        StepHero(RefArt.Success, 148)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "You're all set",
            style = MaterialTheme.typography.displayMedium,
            color = GsTheme.colors.textPrimary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (name.isBlank()) {
                "Your companion is ready. Start with a thought, a file, or a blank page."
            } else {
                "Welcome, $name. Your companion is ready — start with a thought, a file, or a blank page."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = GsTheme.colors.textSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        RefCard(padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            Text(
                text = "This is how GS will start",
                style = MaterialTheme.typography.titleSmall,
                color = GsTheme.colors.textPrimary
            )
            Spacer(Modifier.height(10.dp))
            SummaryLine(
                label = "Interests",
                value = if (interests.isEmpty()) "None picked yet" else interests.take(4).joinToString(", ")
            )
            SummaryLine(label = "Answers", value = "$style · $personality")
            SummaryLine(
                label = "Tools",
                value = if (tools.isEmpty()) "None pinned yet" else tools.joinToString(", ")
            )
        }
        RefFootnote("Everything here can be changed later in Settings → Personalisation.")
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = GsTheme.colors.textTertiary,
            modifier = Modifier.width(78.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = GsTheme.colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}
