package com.grapsee.gsai.ui.vision

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsInputBar
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.components.GsSectionHeader
import com.grapsee.gsai.ui.theme.GsMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * AERUO KINETIC — VISION, image understanding.
 * Pick a source (fake 600ms load), watch the 900ms analysis run, then read
 * the image through Summary / Objects / Text / Chart tabs. Follow-ups append
 * canned Q&A above the input. Compare mode shows two side-by-side slots.
 * Sample data only — real inference arrives with the vision model build.
 */

private val visionSources = listOf("Camera", "Gallery", "Screenshot")

private val visionTabs = listOf("Summary", "Objects", "Text", "Chart")

private data class DetectedObject(val label: String, val confidence: Int)

private val detectedObjects = listOf(
    DetectedObject("Ceramic mug", 98),
    DetectedObject("Laptop", 96),
    DetectedObject("Notebook", 91),
    DetectedObject("Plant", 87),
    DetectedObject("Window", 74)
)

private val ocrLines = listOf(
    "MEETING NOTES — Q3",
    "Owner: Maya",
    "Budget: £48k",
    "Review: Friday"
)

private val chartHeights = listOf(42, 68, 54, 88, 76, 110)

private val summaryParagraph =
    "The frame is a calm desk still life shot in natural light: a ceramic mug, an open notebook and a laptop arranged near a window. On the laptop screen, a chart tracks quarterly revenue rising steadily, with the steepest gains in the final quarter."

private val followUpAnswers = listOf(
    "Based on the image, the frame is dominated by a warm ceramic mug and an open notebook on pale oak.",
    "Based on the image, natural light enters from a window on the right, softening the laptop shadows.",
    "Based on the image, the handwritten page lists a Q3 budget of £48k with a Friday review."
)

@Composable
fun VisionScreen(onBack: () -> Unit) {
    var source by remember { mutableStateOf<String?>(null) }
    var loadingSource by remember { mutableStateOf(false) }
    var hasImage by remember { mutableStateOf(false) }
    var analysisProgress by remember { mutableStateOf(0f) }
    var analysisDone by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf("Summary") }
    var compareMode by remember { mutableStateOf(false) }
    var followUpDraft by remember { mutableStateOf("") }
    var qa by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showSnack: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun pickSource(label: String) {
        source = label
        hasImage = false
        analysisDone = false
        analysisProgress = 0f
        loadingSource = true
    }

    LaunchedEffect(loadingSource) {
        if (loadingSource) {
            delay(600)
            hasImage = true
            loadingSource = false
        }
    }

    LaunchedEffect(hasImage) {
        if (hasImage) {
            analysisProgress = 0f
            analysisDone = false
            while (analysisProgress < 1f) {
                delay(18)
                analysisProgress = (analysisProgress + 0.02f).coerceAtMost(1f)
            }
            analysisDone = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        GsScreenScaffold(
            title = "Vision",
            onBack = onBack,
            actions = {
                GsChip(
                    text = "Compare",
                    selected = compareMode,
                    onClick = { compareMode = !compareMode }
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(GsMotion.spaceL)
            ) {
                Spacer(Modifier.height(GsMotion.spaceS))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                ) {
                    visionSources.forEach { label ->
                        GsChip(text = label, selected = source == label) { pickSource(label) }
                    }
                }

                // Image preview — placeholder until a source loads the sample frame.
                GsCard {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(56.dp)
                        )
                        if (hasImage) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "IMG_2041.jpg",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    if (hasImage && source != null) {
                        Spacer(Modifier.height(GsMotion.spaceS))
                        Text(
                            text = "$source · IMG_2041.jpg · 3.2 MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (loadingSource) {
                    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "Loading sample frame…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (hasImage && !analysisDone) {
                    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                        LinearProgressIndicator(
                            progress = { analysisProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                        )
                        Text(
                            text = "Analysing image…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (compareMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(110.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Slot A · filled",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        DashedSlot(modifier = Modifier.weight(1f).height(110.dp))
                    }
                }

                if (analysisDone) {
                    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                        ) {
                            visionTabs.forEach { tab ->
                                GsChip(text = tab, selected = activeTab == tab) { activeTab = tab }
                            }
                        }
                        when (activeTab) {
                            "Summary" -> SummaryTab()
                            "Objects" -> ObjectsTab()
                            "Text" -> TextTab(
                                onCopy = {
                                    clipboard.setText(AnnotatedString(ocrLines.joinToString("\n")))
                                    showSnack("Copied")
                                }
                            )
                            else -> ChartTab()
                        }
                    }
                }

                if (qa.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                        GsSectionHeader(title = "Follow-ups")
                        qa.forEach { pair ->
                            GsCard {
                                Text(
                                    text = pair.first,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(GsMotion.spaceXS))
                                Text(
                                    text = pair.second,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                GsInputBar(
                    value = followUpDraft,
                    onValueChange = { followUpDraft = it },
                    onSend = { question ->
                        qa = qa + Pair(question, followUpAnswers[qa.size % followUpAnswers.size])
                        followUpDraft = ""
                    },
                    placeholder = "Ask about this image…"
                )
                Spacer(Modifier.height(GsMotion.spaceL))
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(GsMotion.spaceM)
        )
    }
}

@Composable
private fun SummaryTab() {
    GsCard {
        Text(
            text = summaryParagraph,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(GsMotion.spaceS))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            Icon(
                imageVector = Icons.Outlined.BarChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Chart interpreted: revenue up 23% QoQ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ObjectsTab() {
    GsCard {
        Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
            detectedObjects.forEach { detected ->
                Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = detected.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${detected.confidence}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LinearProgressIndicator(
                        progress = { detected.confidence / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun TextTab(onCopy: () -> Unit) {
    val mono = MaterialTheme.typography.bodyMedium.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp
    )
    GsCard {
        Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)) {
            ocrLines.forEach { line ->
                Text(
                    text = line,
                    style = mono,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(Modifier.height(GsMotion.spaceS))
        FilledTonalButton(onClick = onCopy) {
            Text("Copy")
        }
    }
}

@Composable
private fun ChartTab() {
    GsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            chartHeights.forEach { barHeight ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(barHeight.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        Spacer(Modifier.height(GsMotion.spaceS))
        Text(
            text = "Chart interpreted: revenue up 23% QoQ — the tallest bars close the quarter.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Empty compare slot drawn with a dashed outline. */
@Composable
private fun DashedSlot(modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                    ),
                    cornerRadius = CornerRadius(14.dp.toPx())
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Slot B — add another image",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
