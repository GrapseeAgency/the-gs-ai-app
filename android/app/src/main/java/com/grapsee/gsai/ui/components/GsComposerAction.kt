package com.grapsee.gsai.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.chat.content.NexlyGlyph
import com.grapsee.gsai.ui.theme.GsTheme

/**
 * THE composer's trailing control — one button, two verbs, one continuous
 * morph between them.
 *
 * Empty field  → a quiet glass circle holding the mic: tap it and you speak.
 * Typed text   → the same circle, now the accent send disc, holding the up
 *                arrow: tap it and the turn goes.
 *
 * The old surface carried BOTH a mic and a separate send button side by side,
 * which meant two chrome elements for one slot and a dead control in the way
 * of the live one. Here the glyphs trade places: the mic rolls out (‑90°,
 * scales to 0.6, fades) while the arrow rolls in (+90° → 0°, 0.55 → 1, fades
 * up) on a single [GsMotion.snappy] spring, and the container crossfades from
 * rim-lit glass to the accent gradient underneath them. Because both glyphs
 * ride the SAME 0..1 progress value the exchange can never desynchronise, and
 * because the container is one size the pill never reflows while typing.
 *
 * Everything here is a real control: [onVoice] is null when no voice route is
 * wired (the circle then stays a send button, disabled while empty), and the
 * reduce-motion setting collapses the morph to an instant swap.
 */
@Composable
fun GsComposerAction(
    /** True when there is something to send (text or a ready attachment). */
    hasContent: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    /** Null = no voice destination on this surface. */
    onVoice: (() -> Unit)? = null,
) {
    val colors = GsTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = SettingsStore.reduceAnimations || SettingsStore.reduceMotion

    // The morph target. With a voice destination the control is a mic until
    // there is something to send; without one it is always a send button.
    val showSend = onVoice == null || hasContent
    val morph by animateFloatAsState(
        targetValue = if (showSend) 1f else 0f,
        animationSpec = if (reduced) snap() else spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "composerMorph",
    )
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (reduced) {
            snap<Float>()
        } else if (pressed) {
            tween<Float>(120)
        } else {
            spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "composerPress",
    )

    // The arrow is live only when there is something to send; the mic is live
    // whenever a voice route exists.
    val clickable = if (showSend) hasContent else true
    val label = if (showSend) "Send" else "Voice"

    Box(
        modifier = modifier
            .size(38.dp)
            .graphicsLayer {
                val scale = 1f - 0.05f * press
                scaleX = scale
                scaleY = scale
                translationY = press * 1.dp.toPx()
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = clickable,
                role = Role.Button,
                onClickLabel = label,
            ) { if (showSend) onSend() else onVoice?.invoke() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        // --- the send disc: accent gradient, lit rim -------------------------
        Box(
            modifier = Modifier
                .matchParentSize()
                .alpha(morph)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFFA78BFF),
                            Color(0xFF7C5CFF),
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape)
        )
        // --- the voice disc: floating-control glass --------------------------
        if (onVoice != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .alpha(1f - morph)
                    .gsGlass(GsGlassRole.FloatingControl, CircleShape)
            )
        }
        // --- the mic, leaving ------------------------------------------------
        if (onVoice != null) {
            NexlyGlyph(
                name = "microphone",
                size = 17.dp,
                color = colors.textSecondary,
                modifier = Modifier.graphicsLayer {
                    alpha = 1f - morph
                    val s = 1f - 0.40f * morph
                    scaleX = s
                    scaleY = s
                    rotationZ = -90f * morph
                    translationY = 5.dp.toPx() * morph
                },
            )
        }
        // --- the arrow, arriving --------------------------------------------
        NexlyGlyph(
            name = "arrow-up",
            size = 17.dp,
            color = if (hasContent || onVoice == null) Color.White
            else Color.White.copy(alpha = 0.88f),
            modifier = Modifier.graphicsLayer {
                alpha = morph
                val s = 0.55f + 0.45f * morph
                scaleX = s
                scaleY = s
                rotationZ = 90f * (1f - morph)
                translationY = -5.dp.toPx() * (1f - morph)
            },
        )
    }
}
