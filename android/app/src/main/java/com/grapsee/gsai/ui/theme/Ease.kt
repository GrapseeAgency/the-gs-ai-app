package com.grapsee.gsai.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.composed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.SettingsStore

/**
 * DESIGN PHASE 2 motion spec — the operator's values, applied app-wide.
 * ONE curve family. No bounce. No spring for entrances, exits, or state
 * changes (the legacy [GsMotion] springs remain only where a physical drag
 * gesture earns one).
 *
 *  Entrance      200ms ease-out            lists, cards, screens appearing
 *  Exit          150ms ease-in             dismissals, removals
 *  State change  180ms ease-in-out         selection, toggle, color/alpha
 *  Sheet/drawer  250ms cubic-bezier(.2,0,0,1)  ModalBottomSheet, nav drawer
 *  Message send  slide up 8dp + fade, 150ms    [Modifier.messageAppear]
 *  Streaming     120ms fade per chunk      streaming tail (see content pkg)
 *
 * Every curve flattens to a near-instant tween when the OS reduce-motion or
 * the in-app reduce-animations setting is on — the static fallback required
 * by the accessibility rule. The gate is read at spec-creation time, and
 * specs are created inside composition, so flipping the setting re-composes.
 */
public object GsEase {
    private const val REDUCED_MS = 1

    public const val ENTRANCE_MS: Int = 200
    public const val EXIT_MS: Int = 150
    public const val STATE_MS: Int = 180
    public const val SHEET_MS: Int = 250
    public const val MESSAGE_SEND_MS: Int = 150
    public const val STREAM_CHUNK_MS: Int = 120

    private fun ms(design: Int): Int = if (SettingsStore.reduceAnimations || SettingsStore.reduceMotion) REDUCED_MS else design

    /** Entrance: 200ms ease-out. */
    public fun <T> entrance(): TweenSpec<T> = tween(ms(ENTRANCE_MS), easing = EaseOut)

    /** Exit: 150ms ease-in. */
    public fun <T> exit(): TweenSpec<T> = tween(ms(EXIT_MS), easing = EaseIn)

    /** State change: 180ms ease-in-out. */
    public fun <T> state(): TweenSpec<T> = tween(ms(STATE_MS), easing = EaseInOut)

    /** Sheet / drawer: 250ms cubic-bezier(0.2, 0, 0, 1) — the MD3 emphasized curve. */
    public fun <T> sheet(): TweenSpec<T> = tween(ms(SHEET_MS), easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
}

/**
 * Message-send motion: the message slides up 8dp and fades in over 150ms
 * (ease-out). Static (no offset, full alpha) under reduce-motion. Apply once
 * to a message row's root modifier. [delayMs] staggers entrances (drawer
 * suggestions, list items) via [GsMotion.staggerDelay].
 */
public fun Modifier.messageAppear(delayMs: Int = 0): Modifier = composed {
    if (SettingsStore.reduceAnimations || SettingsStore.reduceMotion) {
        this
    } else {
        var appeared by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (delayMs > 0) kotlinx.coroutines.delay(delayMs.toLong())
            appeared = true
        }
        val progress by animateFloatAsState(
            targetValue = if (appeared) 1f else 0f,
            animationSpec = tween(GsEase.MESSAGE_SEND_MS, easing = EaseOut),
            label = "messageAppear",
        )
        this.graphicsLayer {
            alpha = progress
            // The GraphicsLayerScope is a Density: 8dp → px in place.
            translationY = (1f - progress) * 8.dp.toPx()
        }
    }
}
