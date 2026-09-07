package com.grapsee.gsai.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.grapsee.gsai.data.liveupdate.LiveUpdateState
import com.grapsee.gsai.data.liveupdate.LiveUpdater
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.auroraBackground
import com.grapsee.gsai.ui.theme.Aeruo
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.kineticPress
import com.grapsee.gsai.ui.theme.rememberAuroraBrush
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * AERUO KINETIC home canvas — benchmark pattern (ChatGPT · Claude · Kimi):
 * obsidian full-bleed, top bar (menu · model pill · new chat), centred brand
 * orb + time-aware serif greeting + upgrade pill, quick-action chips and one
 * hero input bar pinned to the bottom. Navigation lives in the drawer.
 */
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    onOpenDrawer: () -> Unit = {}
) {
    val context = LocalContext.current
    // GS LiveUpdate: quiet GitHub manifest check on every Home appearance (10-min throttle).
    LaunchedEffect(Unit) { LiveUpdater.syncFrom(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Aeruo.Obsidian)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = GsMotion.spaceM)
        ) {
            TopBar(
                onOpenDrawer = onOpenDrawer,
                onNavigate = onNavigate
            )

            Spacer(Modifier.weight(1.1f))
            HeroBlock(onNavigate = onNavigate)
            Spacer(Modifier.weight(1f))

            SuggestionRows(onNavigate = onNavigate)
            Spacer(Modifier.height(GsMotion.spaceM))
            TrendingRow(onNavigate = onNavigate)
            Spacer(Modifier.height(GsMotion.spaceM))
            QuickChips(onNavigate = onNavigate)
            Spacer(Modifier.height(GsMotion.spaceM))
            // GS LiveUpdate — appears only when a newer build exists on GitHub.
            LiveUpdatePill()
            Spacer(Modifier.height(GsMotion.spaceS))
            HeroInput(onNavigate = onNavigate)
            Spacer(Modifier.height(GsMotion.spaceS))
            Text(
                "GS can make mistakes — double-check important info.",
                style = MaterialTheme.typography.labelMedium,
                color = Aeruo.TextMutedDark,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = GsMotion.spaceS),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun TopBar(
    onOpenDrawer: () -> Unit,
    onNavigate: (String) -> Unit
) {
    Row(
        modifier = Modifier
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

        // Model pill — "Instant High" pattern
        Surface(
            shape = RoundedCornerShape(GsMotion.radiusChip),
            color = Aeruo.RaisedDark,
            modifier = Modifier.kineticPress()
        ) {
            Row(
                modifier = Modifier.clickable { onNavigate(GsRoutes.MODELS) }.padding(
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
                    "GS Balanced · High",
                    style = MaterialTheme.typography.labelLarge,
                    color = Aeruo.TextDark
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
    Surface(
        shape = CircleShape,
        color = Aeruo.RaisedDark,
        modifier = Modifier
            .size(44.dp)
            .kineticPress()
    ) {
        Box(
            modifier = Modifier.clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = Aeruo.TextDark,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun HeroBlock(onNavigate: (String) -> Unit) {
    val transition = rememberInfiniteTransition(label = "orb")
    val breathe by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbBreathe"
    )

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
                    .background(Aeruo.Obsidian.copy(alpha = 0.35f))
            )
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = "GS",
                tint = Aeruo.TextDark,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.height(GsMotion.spaceL))

        Text(
            greeting(),
            style = MaterialTheme.typography.displayLarge,
            color = Aeruo.TextDark,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(GsMotion.spaceS))

        // Claude-style rotating tagline — one quiet line that slowly cycles
        RotatingTagline(modifier = Modifier)

        Spacer(Modifier.height(GsMotion.spaceM))

        // Upgrade pill — subtle, under the greeting (Kimi pattern)
        Surface(
            shape = RoundedCornerShape(GsMotion.radiusChip),
            color = Aeruo.RaisedDark,
            modifier = Modifier.kineticPress()
        ) {
            Row(
                modifier = Modifier.clickable { onNavigate(GsRoutes.BILLING) }.padding(
                    horizontal = GsMotion.spaceM,
                    vertical = 10.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = Aeruo.Accent,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    "Upgrade plan",
                    style = MaterialTheme.typography.labelLarge,
                    color = Aeruo.TextDark
                )
            }
        }
    }
}

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour in 23..24 || hour in 0..4 -> "Up late, Admin?"
        hour in 5..11 -> "Good morning, Admin"
        hour in 12..17 -> "Good afternoon, Admin"
        else -> "Good evening, Admin"
    }
}

/** Time-aware tagline set; the hero crossfades one line every few seconds. */
private fun taglines(): List<String> {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val first = if (hour >= 23 || hour < 5) "Working while the world sleeps?" else "What should we make today?"
    return listOf(
        first,
        "Ask, build, refine — all in one thread.",
        "Your move. GS is listening."
    )
}

@Composable
private fun RotatingTagline(modifier: Modifier = Modifier) {
    val lines = remember { taglines() }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(TAGLINE_ROTATE_MS)
            index = (index + 1) % lines.size
        }
    }
    AnimatedContent(
        targetState = index,
        transitionSpec = {
            fadeIn(tween(TAGLINE_FADE_MS)) togetherWith fadeOut(tween(TAGLINE_FADE_MS))
        },
        label = "tagline",
        modifier = modifier
    ) { current ->
        Text(
            lines[current],
            style = MaterialTheme.typography.bodyMedium,
            color = Aeruo.TextMutedDark
        )
    }
}

private const val TAGLINE_ROTATE_MS = 5_200L
private const val TAGLINE_FADE_MS = 700

/**
 * GS LiveUpdate pill — benchmark-quiet surface (raised dark + aurora dot, same
 * language as the model pill). Only rendered when a newer build is published:
 * "v0.2.0 ready" → tap → "Downloading update · 42%" → "Update ready · tap to
 * install" → system installer. Every failure path dissolves back to invisible.
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
        LiveUpdateState.Idle -> Unit
    }
}

@Composable
private fun UpdatePill(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(GsMotion.radiusChip),
        color = Aeruo.RaisedDark,
        modifier = Modifier.kineticPress()
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
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
                color = Aeruo.TextDark
            )
        }
    }
}

/**
 * ChatGPT-style explore/trending strip — one horizontal row of compact cards
 * surfacing Explore-section content on the canvas. Benchmark pattern: Kimi's
 * trending prompts + ChatGPT's suggestion depth, in Aeruo Kinetic surfaces.
 */
@Composable
private fun TrendingRow(onNavigate: (String) -> Unit) {
    data class TrendCard(val category: String, val title: String, val icon: ImageVector, val route: String)

    val cards = remember {
        listOf(
            TrendCard("Trending", "Deep research agent", Icons.Outlined.TravelExplore, GsRoutes.RESEARCH),
            TrendCard("Popular", "Prompt builder", Icons.Outlined.AutoAwesome, GsRoutes.PROMPT_BUILDER),
            TrendCard("New", "Image studio", Icons.Outlined.Palette, GsRoutes.IMAGE_STUDIO),
            TrendCard("For you", "Code workspace", Icons.Outlined.Code, GsRoutes.CODE_WORKSPACE),
            TrendCard("Browse all", "All assistants", Icons.Outlined.ArrowForward, GsRoutes.EXPLORE)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
    ) {
        cards.forEach { card ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Aeruo.RaisedDark,
                modifier = Modifier
                    .width(176.dp)
                    .kineticPress()
            ) {
                Column(
                    modifier = Modifier
                        .clickable { onNavigate(card.route) }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            card.icon,
                            contentDescription = null,
                            tint = Aeruo.Accent,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            card.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = Aeruo.TextMutedDark
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        card.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Aeruo.TextDark,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionRows(onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
    ) {
        SuggestionRow(Icons.Outlined.Description, "Summarise a PDF into a brief") {
            onNavigate(GsRoutes.chat(null, "Summarise a PDF into a brief"))
        }
        SuggestionRow(Icons.Outlined.EditNote, "Draft a launch email") {
            onNavigate(GsRoutes.chat(null, "Draft a launch email"))
        }
    }
}

@Composable
private fun SuggestionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .kineticPress()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
    ) {
        Surface(
            shape = CircleShape,
            color = Aeruo.RaisedDark,
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Aeruo.TextDark,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = Aeruo.TextDark
        )
    }
}

@Composable
private fun QuickChips(onNavigate: (String) -> Unit) {
    data class Chip(val label: String, val icon: ImageVector, val route: String)

    val chips = remember {
        listOf(
            Chip("Projects", Icons.Outlined.Folder, GsRoutes.PROJECTS),
            Chip("Research", Icons.Outlined.TravelExplore, GsRoutes.RESEARCH),
            Chip("Vision", Icons.Outlined.Visibility, GsRoutes.VISION),
            Chip("Image", Icons.Outlined.Palette, GsRoutes.IMAGE_STUDIO),
            Chip("Writing", Icons.Outlined.EditNote, GsRoutes.WRITING_STUDIO),
            Chip("Code", Icons.Outlined.Code, GsRoutes.CODE_WORKSPACE),
            Chip("Voice", Icons.Outlined.Mic, GsRoutes.VOICE),
            Chip("Library", Icons.Outlined.Bookmarks, GsRoutes.LIBRARY),
            Chip("Models", Icons.Outlined.Speed, GsRoutes.MODELS)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
    ) {
        chips.forEach { chip ->
            Surface(
                shape = RoundedCornerShape(GsMotion.radiusChip),
                color = Aeruo.RaisedDark,
                modifier = Modifier.kineticPress()
            ) {
                Row(
                    modifier = Modifier
                        .clickable { onNavigate(chip.route) }
                        .padding(horizontal = GsMotion.spaceM, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        chip.icon,
                        contentDescription = null,
                        tint = Aeruo.TextMutedDark,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        chip.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = Aeruo.TextDark
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroInput(onNavigate: (String) -> Unit) {
    val context = LocalContext.current

    // --- Voice press-and-hold -------------------------------------------------
    // Hold the aurora orb to dictate: live partials fill the hero line, release
    // hands the transcript to the chat composer. A quick tap still opens full
    // voice mode. Every failure path dissolves quietly — nothing surfaces as
    // an error.
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

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecognizer() else quietReset()
    }
    val holdScope = rememberCoroutineScope()

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Aeruo.RaisedDark,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = GsMotion.spaceS,
                vertical = GsMotion.spaceS
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attach — opens a new chat with the attach sheet
            Surface(
                shape = CircleShape,
                color = Aeruo.AccentSoftDark,
                modifier = Modifier
                    .size(42.dp)
                    .kineticPress()
            ) {
                Box(
                    modifier = Modifier.clickable { onNavigate(GsRoutes.chat(null)) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "Attach and new chat",
                        tint = Aeruo.Accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(GsMotion.spaceS))

            Text(
                when {
                    listening && transcript.isBlank() -> "Listening…"
                    listening -> transcript
                    else -> "Ask anything"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (listening) Aeruo.TextDark else Aeruo.TextMutedDark,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onNavigate(GsRoutes.chat(null)) }
                    .padding(vertical = GsMotion.spaceS)
            )

            // Mic — full voice mode
            Surface(
                shape = CircleShape,
                color = androidx.compose.ui.graphics.Color.Transparent,
                modifier = Modifier
                    .size(42.dp)
                    .kineticPress()
            ) {
                Box(
                    modifier = Modifier.clickable { onNavigate(GsRoutes.VOICE) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Mic,
                        contentDescription = "Voice input",
                        tint = Aeruo.TextMutedDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Aurora orb — press-and-hold to dictate (the hero affordance)
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                var isHold = false
                                val timer = holdScope.launch {
                                    delay(VOICE_HOLD_TRIGGER_MS)
                                    isHold = true
                                    beginVoiceHold()
                                }
                                val released = tryAwaitRelease()
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
                    color = Aeruo.AccentSoftDark,
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
                            tint = Aeruo.TextDark,
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
    Box(
        modifier = Modifier
            .size(46.dp)
            .graphicsLayer { scaleX = pulse; scaleY = pulse; alpha = fade }
            .clip(CircleShape)
            .background(rememberAuroraBrush(CircleShape))
    )
}

private const val VOICE_HOLD_TRIGGER_MS = 280L
