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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
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
import com.grapsee.gsai.ui.theme.GsTheme
import kotlin.math.PI
import kotlin.math.sin

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
 * The native AI activity orb (Phase 4): a restrained, warm visual
 * language for what the assistant is currently doing. The state comes from
 * the honest mapping layer (OrbStateMapping.kt) — the orb shows what the app
 * is REALLY doing, never what it wants to look like it is doing.
 *
 * Design contract (thinking-orbs@0.3.1, vendored spec in docs/orbs/):
 *  - Warm AI-active color: the shared accent carries identity while shape,
 *    motion, and density communicate the honest activity state.
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
 * @param renderScale    draws the preset's geometry at `size.px * renderScale`
 *                       physical dp instead of `size.px`. The whole drawing —
 *                       dots, radii, stroke — scales together, so a 20dp
 *                       preset can fill a 36dp control without being cropped
 *                       and without inventing a third hand-tuned preset.
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
    contentDescription: String? = null,
    renderScale: Float = 1f,
    // LUMINOUS layer (OrbPhase.kt). [luminance] overrides the per-state default
    // presentation; null borrows [luminousProfileFor] so geometry-only callers
    // still glow. [amplitude] is a live 0..1 signal (the microphone level while
    // recording) that lifts the core halo; negative means "not reactive".
    luminance: LuminousProfile? = null,
    amplitude: Float = -1f
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
    // The preset's geometry is authored in its own dp space (INLINE = 20dp,
    // STANDARD = 64dp). [renderScale] draws that SAME drawing at a different
    // physical size: the canvas grows and translate+scale centres the authored
    // space inside it, so the dots scale with the control instead of the
    // control cropping them. No third preset, no re-tuning, no clipped edge.
    val presetPx = size.px
    val scaleFactor = renderScale.coerceAtLeast(0.01f)
    // Read the accent during composition: the Canvas lambda is DrawScope and
    // cannot touch GsTheme.
    val colors = GsTheme.colors
    val aiActive = colors.aiActive
    // Resolve the glow hues here, not in the draw cache — a role maps to real
    // token colours so an accent or appearance flip relights the orb with it.
    val profile = luminance ?: luminousProfileFor(state)
    val glowColors = listOf(
        when (profile.role) {
            OrbLuminousRole.CALM -> aiActive
            OrbLuminousRole.ACTIVE -> colors.accentStrong
            OrbLuminousRole.SUCCESS -> colors.success
            OrbLuminousRole.ALERT -> colors.error
            OrbLuminousRole.MUTED -> colors.textSecondary
        },
        Color.Transparent,
    )
    // The iridescent tail the reference is made of — the aurora spectrum as a
    // faint outer ring, only for the alive/calm roles, never for alarm or mute.
    val iridescent = profile.role == OrbLuminousRole.CALM || profile.role == OrbLuminousRole.ACTIVE
    Canvas(
        modifier
            .size((presetPx * scaleFactor).dp)
            .semantics {
                role = Role.Image
                this.contentDescription = contentDescription ?: state.label
            }
    ) {
        // LUMINOUS PASS — drawn first, so it sits *behind* the dots and adds
        // light without touching the locked geometry. A radial bloom in the
        // phase hue, breathing on the shared clock; a live amplitude lifts the
        // core. Screen blend makes it glow over the atmosphere, not tint it.
        val glowCenter = Offset(size.width / 2f, size.height / 2f)
        val glowRadius = size.minDimension / 2f
        val breathe = if (profile.pulse) {
            (0.5 + 0.5 * sin(tState.doubleValue * profile.pulseSpeed * PI)).toFloat()
        } else {
            1f
        }
        val boost = if (amplitude >= 0f) amplitude.coerceIn(0f, 1f) else 0f
        if (iridescent) {
            drawCircle(
                brush = Brush.radialGradient(colors.aurora + Color.Transparent, center = glowCenter, radius = glowRadius, tileMode = TileMode.Clamp),
                radius = glowRadius,
                center = glowCenter,
                alpha = (profile.glowAlpha * 0.5f * breathe).coerceIn(0f, 1f),
                blendMode = BlendMode.Screen,
            )
        }
        drawCircle(
            brush = Brush.radialGradient(glowColors, center = glowCenter, radius = glowRadius, tileMode = TileMode.Clamp),
            radius = glowRadius,
            center = glowCenter,
            alpha = (profile.glowAlpha * breathe + boost * 0.22f).coerceIn(0f, 1f),
            blendMode = BlendMode.Screen,
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(aiActive, Color.Transparent), center = glowCenter, radius = glowRadius * 0.46f, tileMode = TileMode.Clamp),
            radius = glowRadius * 0.46f,
            center = glowCenter,
            alpha = (profile.coreAlpha * (0.6f + 0.4f * breathe) + boost * 0.5f).coerceIn(0f, 1f),
            blendMode = BlendMode.Screen,
        )
        val contentPx = presetPx * density
        // NEGATIVE when scaling a preset DOWN (authored space is larger than
        // the canvas). Clamping this at 0 would leave the drawing anchored to
        // the top-left and the scale pivot outside the canvas — the orb would
        // be cropped and off-centre. The offset is exactly what centres the
        // authored space, in both directions.
        val pad = (this.size.width - contentPx) / 2f
        translate(pad, pad) {
            scale(scaleFactor, scaleFactor, pivot = Offset(contentPx / 2f, contentPx / 2f)) {
                OrbEngine.render(presetPx.toDouble(), tState.doubleValue, resolved, buffer)
                // Upstream paint contract: lines first (only `connecting` emits
                // them), then dots far→near; warm accent; plain
                // source-over fills.
                for (li in 0 until buffer.lineCount) {
                    val w = buffer.lWhite[li].coerceIn(0.0, 1.0)
                    val gray = (if (dark) 1 - w else w)
                    drawLine(
                        color = aiActive.copy(alpha = buffer.lAlpha[li].coerceIn(0.0, 1.0).toFloat() * (0.35f + gray.toFloat() * 0.65f)),
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
                    // perf-smooth-5: CULL WHAT CANNOT BE SEEN BEFORE BUILDING IT.
                    //
                    // The engine emits the full point cloud every frame,
                    // including the dots the depth/size curve has already faded
                    // to nothing. A dot at alpha <= 0.004 or a sub-pixel radius
                    // contributes no pixels but still costs a drawCircle
                    // command, three divisions, a round and an Offset
                    // construction — per dot, per frame, at up to 120Hz. The
                    // globe and rubik modes emit several hundred dots, most of
                    // them on the far hemisphere. Skipping them is free: the
                    // result is pixel-identical because they drew nothing.
                    val alpha = buffer.pAlpha[i]
                    if (alpha <= 0.004) continue
                    val radius = (buffer.pr[i] * density).toFloat()
                    if (radius < 0.05f) continue
                    val w = buffer.pWhite[i].coerceIn(0.0, 1.0)
                    val gray = (if (dark) 1 - w else w)
                    drawCircle(
                        color = aiActive.copy(alpha = alpha.coerceIn(0.0, 1.0).toFloat() * (0.35f + gray.toFloat() * 0.65f)),
                        radius = radius,
                        center = Offset(
                            (buffer.px[i] * density).toFloat(),
                            (buffer.py[i] * density).toFloat()
                        )
                    )
                }
            }
        }
    }
}
