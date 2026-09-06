package com.grapsee.gsai.ui.create

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.components.GsSectionHeader
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.rememberAuroraBrush
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * AERUO KINETIC — IMAGE STUDIO.
 * Prompt + style/aspect chips feed a 1.6s generation run: the sanctioned
 * aurora progress bar ("Dreaming up 4 variations…"), then a 2x2 grid of
 * variation tiles in stepped primary tints (no gradients) with per-tile
 * download. Sample results only — the diffusion model lands later.
 */

private val styleOptions = listOf("Editorial", "Cinematic", "Minimal", "Bold", "Watercolour")

private val aspectOptions = listOf("1:1", "3:2", "16:9", "9:16")

private val resultAlphas = listOf(0.06f, 0.10f, 0.14f, 0.18f)

@Composable
fun ImageStudioScreen(onBack: () -> Unit) {
    var prompt by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("Editorial") }
    var aspect by remember { mutableStateOf("1:1") }
    var isGenerating by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var hasResults by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showSnack: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    LaunchedEffect(isGenerating) {
        if (isGenerating) {
            progress = 0f
            while (progress < 1f) {
                delay(16)
                progress = (progress + 0.01f).coerceAtMost(1f)
            }
            isGenerating = false
            hasResults = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        GsScreenScaffold(title = "Image studio", onBack = onBack) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(GsMotion.spaceL)
            ) {
                Spacer(Modifier.height(GsMotion.spaceS))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    placeholder = {
                        Text(
                            "A calm editorial still life…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )

                Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                    GsSectionHeader(title = "Style")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                    ) {
                        styleOptions.forEach { option ->
                            GsChip(text = option, selected = style == option) { style = option }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                    GsSectionHeader(title = "Aspect")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                    ) {
                        aspectOptions.forEach { option ->
                            GsChip(text = option, selected = aspect == option) { aspect = option }
                        }
                    }
                }

                Button(
                    onClick = {
                        isGenerating = true
                        hasResults = false
                    },
                    enabled = prompt.isNotBlank() && !isGenerating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Generate")
                }

                if (isGenerating) {
                    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                        AuroraProgressBar(progress = progress)
                        Text(
                            text = "Dreaming up 4 variations…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (hasResults) {
                    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                        GsSectionHeader(title = "Variations", actionLabel = "4", onAction = {})
                        resultAlphas.chunked(2).forEach { rowAlphas ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                            ) {
                                rowAlphas.forEach { alpha ->
                                    ResultTile(
                                        alpha = alpha,
                                        onDownload = { showSnack("Saved to Library") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        FilledTonalButton(
                            onClick = {
                                isGenerating = true
                                hasResults = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Regenerate")
                        }
                    }
                }
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

/** Determinate progress with the sanctioned aurora fill — the model is dreaming. */
@Composable
private fun AuroraProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(rememberAuroraBrush())
        )
    }
}

@Composable
private fun ResultTile(
    alpha: Float,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(GsMotion.radiusCard))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
    ) {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.Center)
                .size(28.dp)
        )
        IconButton(
            onClick = onDownload,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = "Save to Library",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
