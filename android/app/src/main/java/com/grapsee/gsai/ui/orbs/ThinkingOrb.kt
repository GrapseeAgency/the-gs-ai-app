package com.grapsee.gsai.ui.orbs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.theme.LocalGsIsDark
import kotlin.math.roundToInt

/**
 * The shared orb clock. Every mounted orb derives its time from the SAME
 * process epoch, so same-speed instances stay in phase — the native
 * equivalent of the reference's shared performance.now clock. One CAS on
 * first use; nothing per frame.
 */
internal object OrbClock {
    private val epochNanos = java.util.concurrent.atomic.AtomicLong(0L)

    fun elapsedSeconds(now: Long): Double {
        val start = epochNanos.get()
        if (start != 0L) return (now - start) / 1e9
        return if (epochNanos.compareAndSet(0L, now)) 0.0 else (now - epochNanos.get()) / 1e9
    }
}

/**
 * The native AI activity orb (Phase 4): a restrained, monochrome visual
 * language for what the assistant is currently doing. The state comes from
 * the honest mapping layer (OrbStateMapping.kt) — the orb shows what the app
 * is REALLY doing, never what it wants to look like it is doing.
 *
 * Design contract (thinking-orbs@0.3.1, vendored spec in docs/orbs/):
 *  - Monochrome only: ink mirrors the theme (light dots on dark canvas, dark
 *    dots on light); all identity comes from shape + motion + density.
 *  - Two purpose-tuned sizes (INLINE 20 / STANDARD 64) — separate designs,
 *    not a scale factor.
 *  - Reduced motion renders ONE static representative frame (t = 0.6) — the
 *    state survives through shape; the orb is never simply hidden.
 *  - The frame clock is shared (all orbs in phase) and stops entirely when
 *    paused, backgrounded, or off-composition (Lazy rows dispose it).
 *
 * Performance contract: t is read ONLY inside the draw phase, so frame
 * updates invalidate drawing — never recomposition. Geometry fills a
 * preallocated buffer; the per-frame path allocates nothing.
 *
 * @param state          the (honestly mapped) activity state
 * @param size           INLINE = message-status scale, STANDARD = avatar scale
 * @param speed          multiplier on the preset's baked speed (1 = tuned)
 * @param paused         freeze on the current frame
 * @param dark           ink direction; defaults to the app-resolved theme
 * @param reduceMotion   static-frame gate; defaults to the app's motion settings
 * @param contentDescription overrides the per-state accessibility label
 */
@Composable
fun ThinkingOrb(
    state: OrbState,
    size: OrbSize = OrbSize.STANDARD,
    speed: Float = 1f,
    paused: Boolean = false,
    modifier: Modifier = Modifier,
    dark: Boolean = LocalGsIsDark.current,
    reduceMotion: Boolean = SettingsStore.reduceMotion || SettingsStore.reduceAnimations,
    contentDescription: String? = null
) {
    val resolved = remember(state, size) { OrbSpec.resolve(state, size) }
    val buffer = remember(state, size) { OrbFrameBuffer() }
    val tState = remember { mutableDoubleStateOf(OrbSpec.STATIC_T) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var inForeground by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            inForeground = event == Lifecycle.Event.ON_RESUME
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resolved, speed, paused, reduceMotion, inForeground) {
        when {
            reduceMotion -> tState.doubleValue = OrbSpec.STATIC_T
            // Paused / backgrounded: freeze on the current frame (the last
            // drawn t stays); the effect restarts in phase from the shared
            // epoch when unpaused or foregrounded.
            paused || !inForeground -> Unit
            else -> {
                while (true) {
                    withFrameNanos { now ->
                        tState.doubleValue =
                            OrbClock.elapsedSeconds(now) * resolved.speed * speed
                    }
                }
            }
        }
    }
    Canvas(
        modifier
            .size(size.px.dp)
            .semantics {
                role = Role.Image
                this.contentDescription = contentDescription ?: state.label
            }
    ) {
        OrbEngine.render(size.px.toDouble(), tState.doubleValue, resolved, buffer)
        // Upstream paint contract: lines first (only `connecting` emits them),
        // then dots far→near; ink mirrors the theme; plain source-over fills.
        for (li in 0 until buffer.lineCount) {
            val w = buffer.lWhite[li].coerceIn(0.0, 1.0)
            val gray = (if (dark) 1 - w else w)
            val g = (gray * 255.0).roundToInt().coerceIn(0, 255)
            drawLine(
                color = Color(
                    red = g / 255f,
                    green = g / 255f,
                    blue = g / 255f,
                    alpha = buffer.lAlpha[li].coerceIn(0.0, 1.0).toFloat()
                ),
                start = Offset(
                    (buffer.lx1[li] * density).toFloat(),
                    (buffer.ly1[li] * density).toFloat()
                ),
                end = Offset(
                    (buffer.lx2[li] * density).toFloat(),
                    (buffer.ly2[li] * density).toFloat()
                ),
                strokeWidth = (buffer.lWidth[li] * density).toFloat()
            )
        }
        for (di in 0 until buffer.dotCount) {
            val i = buffer.drawOrder[di]
            val w = buffer.pWhite[i].coerceIn(0.0, 1.0)
            val gray = (if (dark) 1 - w else w)
            val g = (gray * 255.0).roundToInt().coerceIn(0, 255)
            drawCircle(
                color = Color(
                    red = g / 255f,
                    green = g / 255f,
                    blue = g / 255f,
                    alpha = buffer.pAlpha[i].coerceIn(0.0, 1.0).toFloat()
                ),
                radius = (buffer.pr[i] * density).toFloat(),
                center = Offset(
                    (buffer.px[i] * density).toFloat(),
                    (buffer.py[i] * density).toFloat()
                )
            )
        }
    }
}
