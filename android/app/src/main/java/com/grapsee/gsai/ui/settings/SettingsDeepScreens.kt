package com.grapsee.gsai.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.data.tts.TtsFocus
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsLinks
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefArt
import com.grapsee.gsai.ui.components.RefArtwork
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefFootnote
import com.grapsee.gsai.ui.components.RefPrimaryButton
import com.grapsee.gsai.ui.components.RefRow
import com.grapsee.gsai.ui.components.RefRowDivider
import com.grapsee.gsai.ui.components.RefSection
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefStatusPill
import com.grapsee.gsai.ui.components.RefValueRow
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------
// Speech engine — the page behind a row that used to be a dead value
// ---------------------------------------------------------------------------

/** The three steps the delivery controls offer, and what each means. */
private val RATE_STEPS = listOf("Slower" to 0.85f, "Natural" to 1.0f, "Faster" to 1.15f)
private val PITCH_STEPS = listOf("Lower" to 0.90f, "Natural" to 1.0f, "Higher" to 1.10f)

private fun labelFor(steps: List<Pair<String, Float>>, value: Float): String =
    steps.minByOrNull { kotlin.math.abs(it.second - value) }?.first ?: steps[1].first

/**
 * SPEECH ENGINE — Settings → Voice & Speech → Speech engine.
 *
 * The row that opened this page used to be `RefValueRow("Speech engine",
 * "Device default")`: a label with no destination, which reads as a control and
 * is not one. Two things were missing behind it — the reader could not see
 * WHICH engine was speaking, and could not change how it speaks.
 *
 * Everything here is wired to the real synthesizer:
 *
 *  - the engine name is `TextToSpeech.defaultEngine` resolved against the
 *    installed engine list, so it names the app that will actually speak;
 *  - speed and pitch are written to [SettingsStore] and applied inside
 *    [TtsFocus.speak], which is the single path every read-aloud in the app
 *    takes — so the change is audible in chat, in voice mode and here;
 *  - "Test voice" speaks a real utterance through the same wrapper, with the
 *    same audio-focus discipline, so it cannot blast over the reader's music;
 *  - a device with no synthesizer gets an honest state, not a silent button.
 */
@Composable
fun SpeechEngineScreen(
    onBack: () -> Unit,
    /** Injectable so the JVM suite can prove the row is wired without a device. */
    onOpenSystemSpeech: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val colors = GsTheme.colors

    var ready by remember { mutableStateOf(false) }
    /** True once the engine has had a moment to answer; only then may the page
     *  call a failure a failure. A first-frame `ready == false` is just "not
     *  yet", and showing the error state for it would flash on every open. */
    var settled by remember { mutableStateOf(false) }
    var engineLabel by remember { mutableStateOf("Device default") }
    var voiceLabel by remember { mutableStateOf<String?>(null) }
    var speaking by remember { mutableStateOf(false) }

    // `runCatching` because a few OEM builds throw from the TextToSpeech
    // constructor when no engine is installed. A settings page must open and
    // say so, not crash — and the JVM render probe has no synthesizer at all.
    val speaker = remember {
        runCatching { TtsFocus(context, onReady = { ready = it }) }.getOrNull()
    }
    DisposableEffect(Unit) {
        onDispose { speaker?.shutdown() }
    }
    LaunchedEffect(Unit) {
        delay(1_200)
        settled = true
    }
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        val engine = speaker?.engine ?: return@LaunchedEffect
        runCatching {
            val pkg = engine.defaultEngine
            val installed = engine.engines.orEmpty()
            engineLabel = installed.firstOrNull { it.name == pkg }?.label
                ?.takeIf { it.isNotBlank() }
                ?: pkg?.takeIf { it.isNotBlank() }
                ?: "Device default"
            voiceLabel = engine.voice?.locale?.displayName
        }
    }

    val openSystem: () -> Unit = {
        val run = onOpenSystemSpeech ?: {
            val opened = runCatching {
                context.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
            }.isSuccess
            if (!opened) {
                runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
            }
            Unit
        }
        run()
    }

    RefSettingsPage(
        title = "Speech engine",
        subtitle = "How answers sound when you ask Nexly to read them.",
        onBack = onBack
    ) {
        // ---- What will speak --------------------------------------------
        RefCard(role = GsGlassRole.CardLight) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RefWellTile(GsIcons.AudioLines, GsWellTone.Lilac, 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        "Read aloud",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GsFonts.Product,
                        color = colors.textPrimary
                    )
                    Text(
                        if (settled && !ready) {
                            "No speech engine answered on this device."
                        } else if (ready) {
                            "$engineLabel${voiceLabel?.let { " · $it" } ?: ""}"
                        } else {
                            "Looking for the device's speech engine…"
                        },
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textTertiary,
                        maxLines = 2
                    )
                }
                when {
                    ready -> RefStatusPill("Ready", colors.success)
                    settled -> RefStatusPill("Unavailable", colors.error)
                    else -> RefStatusPill("Checking…", colors.textTertiary)
                }
            }
        }

        // ---- The one failure this page can have --------------------------
        if (settled && !ready) {
            Spacer(Modifier.height(10.dp))
            RefCard(role = GsGlassRole.CardLight) {
                Column(Modifier.padding(16.dp)) {
                    RefArtwork(RefArt.Error, size = 96.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "This device has no text-to-speech engine",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary
                    )
                    Text(
                        "Read-aloud needs one installed. Android's own engine is a free " +
                            "download in Settings → Accessibility → Text-to-speech output.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    RefPrimaryButton(label = "Open system speech settings", onClick = openSystem)
                }
            }
        }

        // ---- Engine ------------------------------------------------------
        RefSection(label = "Engine", role = GsGlassRole.CardLight) {
            RefValueRow("Engine", if (ready) engineLabel else "Device default", GsIcons.AudioLines)
            RefRowDivider()
            RefValueRow("Voice", voiceLabel ?: "System default", GsIcons.Globe)
            RefRowDivider()
            RefRow(
                title = "System speech settings",
                subtitle = "Install engines, download voices, set the system language",
                icon = GsIcons.Sliders,
                onClick = openSystem
            )
        }

        // ---- Delivery ----------------------------------------------------
        RefSection(label = "Delivery", role = GsGlassRole.CardLight) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(
                    "Speed",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = GsFonts.Product,
                    color = colors.textPrimary
                )
                Spacer(Modifier.height(8.dp))
                com.grapsee.gsai.ui.components.RefSegmented(
                    options = RATE_STEPS.map { it.first },
                    selected = labelFor(RATE_STEPS, SettingsStore.ttsRate),
                    onSelect = { label ->
                        RATE_STEPS.firstOrNull { it.first == label }?.let {
                            SettingsStore.updateTtsRate(it.second)
                        }
                    }
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Pitch",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = GsFonts.Product,
                    color = colors.textPrimary
                )
                Spacer(Modifier.height(8.dp))
                com.grapsee.gsai.ui.components.RefSegmented(
                    options = PITCH_STEPS.map { it.first },
                    selected = labelFor(PITCH_STEPS, SettingsStore.ttsPitch),
                    onSelect = { label ->
                        PITCH_STEPS.firstOrNull { it.first == label }?.let {
                            SettingsStore.updateTtsPitch(it.second)
                        }
                    }
                )
                Spacer(Modifier.height(14.dp))
                RefPrimaryButton(
                    label = if (speaking) "Speaking…" else "Test voice",
                    onClick = {
                        if (!ready) {
                            openSystem()
                        } else {
                            speaking = true
                            speaker?.speak(
                                "Hi. This is how I will read your answers — " +
                                    "${labelFor(RATE_STEPS, SettingsStore.ttsRate).lowercase()} " +
                                    "and ${labelFor(PITCH_STEPS, SettingsStore.ttsPitch).lowercase()}.",
                                "gs-voice-test"
                            )
                        }
                    }
                )
            }
        }

        RefFootnote(
            "Speech is synthesized on this device by the engine above. Audio is never " +
                "uploaded, and these two controls change every answer you ask Nexly to read."
        )
        Spacer(Modifier.height(10.dp))
    }
}

// ---------------------------------------------------------------------------
// Open-source licences — a page instead of a dialog
// ---------------------------------------------------------------------------

private data class Licence(val title: String, val licence: String, val note: String)

private val LICENCE_GROUPS: List<Pair<String, List<Licence>>> = listOf(
    "Typeface" to listOf(
        Licence(
            "Poppins",
            "SIL Open Font License 1.1",
            "Copyright 2020 The Poppins Project Authors. The interface typeface."
        )
    ),
    "Runtime & UI" to listOf(
        Licence("Kotlin", "Apache License 2.0", "The language this app is written in."),
        Licence("Jetpack Compose", "Apache License 2.0", "Every surface you can see and touch."),
        Licence("AndroidX & Material", "Apache License 2.0", "Navigation, lifecycle, Room and the component layer."),
        Licence("OkHttp", "Apache License 2.0", "Network transport."),
        Licence("Coil", "Apache License 2.0", "Image loading."),
        Licence("kotlinx.coroutines & serialization", "Apache License 2.0", "Concurrency and JSON.")
    ),
    "On-device inference" to listOf(
        Licence(
            "llama.cpp",
            "MIT License",
            "The CPU inference runtime behind the on-device model. Its own licence and " +
                "the model weights' licences travel with the download."
        )
    )
)

/**
 * OPEN-SOURCE LICENCES — Settings → About → Open-source licences.
 *
 * This was an `AlertDialog` with one grey paragraph: legally adequate, and the
 * one surface in About that was not built like the rest of the app. It is now a
 * page, grouped by what the reader is actually looking at (the typeface, the UI
 * stack, the thing that runs the model), and it ends with a real way to read the
 * full texts instead of claiming they exist somewhere.
 */
@Composable
fun LicencesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = GsTheme.colors

    RefSettingsPage(
        title = "Open-source licences",
        subtitle = "What this build is made of, and under what terms.",
        onBack = onBack
    ) {
        Text(
            "Nexly is built on open-source work. Each entry below names the project, " +
                "the licence it ships under and what it does here.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        LICENCE_GROUPS.forEach { (group, entries) ->
            RefSection(label = group, role = GsGlassRole.CardLight) {
                entries.forEachIndexed { index, entry ->
                    if (index > 0) RefRowDivider()
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                entry.title,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = GsFonts.Product,
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                entry.licence,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = GsFonts.Product,
                                color = colors.accentStrong
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            entry.note,
                            fontSize = 11.5.sp,
                            lineHeight = 15.5.sp,
                            fontFamily = GsFonts.Product,
                            color = colors.textTertiary
                        )
                    }
                }
            }
        }

        RefSection(label = "Full texts", role = GsGlassRole.CardLight) {
            RefRow(
                title = "Read the licence texts",
                subtitle = "github.com/GrapseeAgency/the-gs-ai-app · LICENSES",
                icon = GsIcons.BookOpen,
                onClick = {
                    GsLinks.openInApp(
                        context,
                        "https://github.com/GrapseeAgency/the-gs-ai-app/tree/main/LICENSES"
                    )
                }
            )
        }

        RefFootnote(
            "Model weights carry their own licences, shown before any download. " +
                "Nothing here is modified from its published form."
        )
        Spacer(Modifier.height(10.dp))
    }
}
