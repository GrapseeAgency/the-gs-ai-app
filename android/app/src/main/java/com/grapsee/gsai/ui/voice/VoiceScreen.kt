package com.grapsee.gsai.ui.voice

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.theme.Aeruo
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.rememberAuroraBrush

/** Call-end red — the single non-aurora accent allowed in voice mode. */
private val EndCallRed = Color(0xFFE5484D)

private val voiceOptions = listOf("Aurora", "Ember", "Slate")

/**
 * Full-screen dark voice mode. This screen forces the obsidian palette with
 * explicit Aeruo text colors, independent of the app theme.
 */
@Composable
fun VoiceScreen(onBack: () -> Unit) {
    var muted by remember { mutableStateOf(false) }
    var speakerOn by remember { mutableStateOf(true) }
    var voice by remember { mutableStateOf("Aurora") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Aeruo.Obsidian)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(GsMotion.spaceL)
        ) {
            // Close — top start
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close voice mode",
                        tint = Aeruo.TextDark
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = if (muted) "Paused" else "Listening…",
                style = MaterialTheme.typography.displaySmall,
                color = Aeruo.TextDark,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(GsMotion.spaceL))

            // Waveform — 24 aurora bars, staggered per index
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(24) { index ->
                    WaveformBar(index = index)
                }
            }

            Spacer(modifier = Modifier.height(GsMotion.spaceL))

            // Transcript — dark card variant
            Surface(
                color = Aeruo.RaisedDark,
                shape = RoundedCornerShape(GsMotion.radiusCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(GsMotion.spaceM),
                    verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                ) {
                    Text(
                        text = "You · “Where did we land on the launch copy?”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Aeruo.TextMutedDark
                    )
                    Text(
                        text = "GS Aurora · “Locked this morning — three options are waiting in the doc.”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Aeruo.TextDark
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VoiceCircle(
                    icon = if (muted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                    container = if (muted) Aeruo.ContainerHighDark else Aeruo.ContainerDark,
                    tint = if (muted) Aeruo.TextMutedDark else Aeruo.TextDark,
                    contentDescription = if (muted) "Unmute" else "Mute"
                ) {
                    muted = !muted
                }
                VoiceCircle(
                    icon = Icons.Outlined.CallEnd,
                    container = EndCallRed.copy(alpha = 0.18f),
                    tint = EndCallRed,
                    contentDescription = "End voice session"
                ) {
                    onBack()
                }
                VoiceCircle(
                    icon = if (speakerOn) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff,
                    container = Aeruo.ContainerDark,
                    tint = Aeruo.TextDark,
                    contentDescription = if (speakerOn) "Speaker on" else "Speaker off"
                ) {
                    speakerOn = !speakerOn
                }
            }

            Spacer(modifier = Modifier.height(GsMotion.spaceM))

            Text(
                text = "Voice: GS $voice · English (UK)",
                style = MaterialTheme.typography.labelMedium,
                color = Aeruo.TextMutedDark,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(GsMotion.spaceS))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS, Alignment.CenterHorizontally)
            ) {
                voiceOptions.forEach { option ->
                    GsChip(
                        text = option,
                        selected = option == voice,
                        onClick = { voice = option }
                    )
                }
            }

            Spacer(modifier = Modifier.height(GsMotion.spaceS))
        }
    }
}

/** One animated waveform bar — height oscillates 8–56dp with a per-index phase delay. */
@Composable
private fun WaveformBar(index: Int) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(index * 45)
        ),
        label = "wavePhase"
    )
    Box(
        modifier = Modifier
            .width(4.dp)
            .height((8 + 48 * phase).dp)
            .background(rememberAuroraBrush(), RoundedCornerShape(2.dp))
    )
}

@Composable
private fun VoiceCircle(
    icon: ImageVector,
    container: Color,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        modifier = Modifier.size(64.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
