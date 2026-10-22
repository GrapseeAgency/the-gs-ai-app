package com.grapsee.gsai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.theme.GsColors
import com.grapsee.gsai.ui.theme.GsTheme

/*
 * THE GLASS — one material, seven semantic roles.
 *
 * The reference is a photograph of light through blossom, seen through frosted
 * glass, and the material carries four optical cues at once. Only the first was
 * ever implemented here, which is why the surfaces read as tinted plastic
 * rather than glass:
 *
 *  1. a translucent fill (the old `glassSurface` + `.copy(alpha = …)`);
 *  2. **in-scattered light** — glass adds airlight back, raising the black
 *     floor and lowering local contrast. [GsGlassRole.veilingAlpha];
 *  3. **a lit rim, strongest at the top** — measured off the sheet, not
 *     eyeballed. Its Mind Guide card's hairline reads +17.2 L above its own
 *     glass along the whole top edge (253 at the left corner, 247 at the right,
 *     against a 233.8 interior) and +4.6 L along the bottom. So the axis is
 *     vertical with a slight lean, and both ends are BRIGHTER than the fill.
 *     The previous brush ran top-left to bottom-right through a warm mid-stop
 *     that is 11 L DARKER than the fill, which is why a wide card's top edge —
 *     the one edge the eye reads as the light — measured +0.35 L of rim on the
 *     render, i.e. no rim at all;
 *  4. **interior luminance fall** — the fill is lighter at the top of a panel
 *     than at the bottom.
 *
 * Cues 2–4 cost nothing to draw and work on every supported API level, because
 * none of them needs `RenderEffect`. Blur is the *least* load-bearing of the
 * four — roughly a fifth of the glass read at UI scale — which is why the
 * material survives the API 26 floor intact.
 *
 * Roles, not parameters. A screen asks for [GsGlassRole.Elevated]; it never
 * picks an alpha. The seven different ad-hoc alphas this replaces were why the
 * same "card" looked subtly different in ten places.
 */

/** The material roles of the shared contract; rationale in the note above. */
enum class GsGlassRole(
    /** How opaque the fill is over the bloom. Thicker glass scatters more. */
    val fillAlpha: Float,
    /** Airlight added back — the in-scatter term. Raises the black floor. */
    val veilingAlpha: Float,
    /** Rim brightness at the lit end, relative to the highlight token. */
    val rimStrength: Float,
    /** Shadow elevation in dp; this is depth, not darkness. */
    val elevation: Dp,
) {
    /** Content on the canvas — the everyday card, chip and row. */
    Card(fillAlpha = 1f, veilingAlpha = 0.05f, rimStrength = 0.55f, elevation = 10.dp),

    /**
     * The sheet's own glass, over the bloom. iOS calls this `.cardLight`; the
     * two are the same measured material and must stay that way (mandate b10).
     *
     * Two cards on the sheet pin it down, because a fill is only observable
     * against what it covers. Over the flat cream page the sheet's Suggestions
     * card drops 242.05 to 239.06; over the bloom its Mind Guide card lifts
     * 229.25 to 232.05. Two equations, two unknowns: a fill of 235.2 — the
     * token's own value — laid down at **45% coverage**. Solving only the first
     * card, which is all the old gate could see, is what let an 80% fill look
     * correct: over flat cream 45% and 80% land 2 L apart.
     *
     * `fillAlpha` is the multiplier on `glassSurface`'s own 0xCC alpha, so
     * 0.80 × 0.565 = the measured 0.45.
     */
    CardLight(fillAlpha = 0.565f, veilingAlpha = 0.05f, rimStrength = 1f, elevation = 10.dp),

    /**
     * The same 45% glass with a DIM edge — the sheet's rows on flat cream. This
     * is not a fudge to make a second probe pass: the sheet's own Suggestions
     * hairline stands +2.6 L off its glass where its Mind Guide stands +19.9 L.
     * An edge glow is light escaping through a frosted rim, so a card with bright
     * bloom behind it carries more of it than a card on flat cream, and the
     * renderer cannot be told that — the role says it.
     */
    CardDim(fillAlpha = 0.565f, veilingAlpha = 0.05f, rimStrength = 0.20f, elevation = 10.dp),

    /** Popovers and cards lifted above other content. */
    Elevated(fillAlpha = 1f, veilingAlpha = 0.08f, rimStrength = 0.75f, elevation = 16.dp),

    /** Floating tab bars, tool clusters, the send affordance. Thinnest glass,
     *  brightest rim — it has to look like it hovers. */
    FloatingControl(fillAlpha = 0.86f, veilingAlpha = 0.06f, rimStrength = 1f, elevation = 20.dp),

    /** Bottom sheets and dialogs. The thickest glass, so the strongest veiling. */
    Modal(fillAlpha = 1f, veilingAlpha = 0.12f, rimStrength = 0.8f, elevation = 28.dp),

    /** Wells and inset fields — glass seen from the shadowed side. */
    Sunken(fillAlpha = 0.62f, veilingAlpha = 0f, rimStrength = 0.3f, elevation = 0.dp),
}

/**
 * How far the unlit end of the rim falls below the lit end. The sheet's Mind
 * Guide hairline is +17.2 L over its glass at the top and +4.6 L at the bottom,
 * and 4.6 / 17.2 is this number.
 */
private const val RIM_FALL = 0.27f

/**
 * The reference glass as a [Modifier]. Shadow, clip, then the four cues in
 * painterly order.
 *
 * [colors] is read in composition, not inside the draw cache: `GsTheme.colors`
 * is `@ReadOnlyComposable` and cannot be touched from a `DrawScope`. Capturing
 * it here means an appearance or accent flip recomposes the call site and
 * rebuilds the cache, which is the behaviour we want.
 */
@Composable
fun Modifier.gsGlass(
    role: GsGlassRole = GsGlassRole.Card,
    shape: Shape = RoundedCornerShape(22.dp),
): Modifier {
    val colors = GsTheme.colors
    val shadowMod = if (role.elevation > 0.dp) {
        this.shadow(
            elevation = role.elevation,
            shape = shape,
            ambientColor = colors.glassShadowAmbient,
            spotColor = colors.glassShadowSpot,
        )
    } else {
        this
    }

    return shadowMod
        .clip(shape)
        .drawWithCache {
            val fill = colors.glassSurface
            val rim = colors.glassHighlight

            // 4. Interior luminance fall — lit at the top, deeper at the bottom.
            val body = Brush.verticalGradient(
                0f to fill,
                1f to fill.copy(alpha = fill.alpha * 0.82f),
            )
            // 2. In-scatter as a broad soft wash rather than a flat tint, so the
            //    lift concentrates where the light enters and fades downward.
            val veil = if (role.veilingAlpha > 0f) {
                Brush.verticalGradient(
                    0f to rim.copy(alpha = role.veilingAlpha),
                    0.62f to rim.copy(alpha = role.veilingAlpha * 0.25f),
                    1f to rim.copy(alpha = 0f),
                )
            } else {
                null
            }
            // 3. The lit rim — vertical with a slight lean, because that is what
            //    the sheet measures: +17.2 L along its whole top edge, +4.6 L
            //    along the bottom, so BOTH ends are brighter than the fill. The
            //    lean is a fraction of the HEIGHT rather than the width so a wide
            //    flat card keeps a lit top edge across all of it, not only at its
            //    left corner; the old brush ran corner to corner through
            //    glassBorder, which is 11 L DARKER than the fill and so painted a
            //    shadow line along the one edge that carries the material.
            val lean = size.height * 0.075f
            val lit = rim.alpha * role.rimStrength
            val rimBrush = Brush.linearGradient(
                0f to rim.copy(alpha = lit),
                1f to rim.copy(alpha = lit * RIM_FALL),
                start = Offset(size.width * 0.5f - lean, 0f),
                end = Offset(size.width * 0.5f + lean, size.height),
            )
            // Stroked as a path rather than drawOutline so the brush overload is
            // not required, and built once per size change — never per frame.
            val rimPath = shape.createOutline(size, layoutDirection, this).toPath()
            // The surface is already clipped, so a path stroke loses its outer
            // half to the clip. Double the width to land on a true 1dp hairline.
            val rimWidth = 2.dp.toPx()
            val fillAlpha = role.fillAlpha

            onDrawBehind {
                drawRect(body, alpha = fillAlpha)
                veil?.let { drawRect(it) }
                drawPath(rimPath, brush = rimBrush, style = Stroke(width = rimWidth))
            }
        }
}

/** Warm shadow alone, for surfaces that draw their own fill. */
@Composable
fun Modifier.gsGlassShadow(
    role: GsGlassRole,
    shape: Shape,
): Modifier {
    val colors = GsTheme.colors
    return if (role.elevation <= 0.dp) this else this.shadow(
        elevation = role.elevation,
        shape = shape,
        ambientColor = colors.glassShadowAmbient,
        spotColor = colors.glassShadowSpot,
    )
}

/**
 * The inner top-edge light, as a separate pass for surfaces that need the glass
 * to look thick — sheets, dialogs, the composer. Fades over the top quarter.
 */
fun DrawScope.gsGlassEdgeGlow(colors: GsColors, strength: Float = 1f) {
    val glow = colors.glassHighlight
    drawRect(
        brush = Brush.verticalGradient(
            0f to glow.copy(alpha = glow.alpha * 0.5f * strength),
            1f to glow.copy(alpha = 0f),
        ),
        topLeft = Offset.Zero,
        size = Size(size.width, size.height * 0.24f),
    )
}

/** Convert a shape outline to a path, cached by size rather than by frame. */
private fun Outline.toPath(): Path = when (this) {
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
    is Outline.Generic -> Path().apply { addPath(path) }
    is Outline.Rectangle -> Path().apply { addRect(bounds) }
}

/** Corner radius helper for callers that draw glass geometry by hand. */
fun DrawScope.glassCornerRadius(radius: Dp): CornerRadius =
    CornerRadius(radius.toPx(), radius.toPx())

/* ------------------------------------------------------------------------- *
 * BLUR TIERS (mandate b6 / b9).
 *
 * The base material above is deliberately analytically blurred — it fakes the
 * in-scatter and the rim so it costs nothing and works on API 26. That is the
 * *material*. This section is the separate *frost*: a real backdrop blur that
 * only the OS can give, layered *behind* translucent glass so the bloom
 * diffuses through the panel.
 *
 * A single [Modifier] cannot sample the pixels already composited behind it,
 * so a real frosted panel is built the honest way: draw the backdrop a second
 * time, blurred, clipped to the panel's shape, and let the glass fill sit over
 * it. [FrostedBloom] owns that redraw, so [Modifier.blur] blurs content the
 * layer actually holds — no undefined backdrop read.
 *
 * Tier split (see Platform-Specific Glass Implementation Strategy):
 *  - API 31+  — [Modifier.blur] lowers to `RenderEffect.createBlurEffect`, a
 *    true GPU blur of the bloom. This is b6: the real thing.
 *  - API 26-30 — no blur pass exists; the bloom is already soft, so drawing it
 *    un-blurred behind the same fill reproduces the baked frosted look. This is
 *    b9: the graceful fallback, same call site.
 * ------------------------------------------------------------------------- */

/** Whether this device can run a real RenderEffect blur (API 31, `Build.VERSION_CODES.S`). */
internal object GsBlurTier {
    val realBlur: Boolean = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    /** The bloom redraw radius: wide enough to read as ground glass, tight enough to keep colour. */
    val radius: Dp = 24.dp
}

/**
 * The bloom, frosted — the backing layer a translucent glass panel reveals.
 * Real GPU blur on API 31+; the same soft bloom without the pass on API 26.
 * Clip to the panel shape at the call site so the frost stays inside the glass.
 */
@Composable
fun FrostedBloom(
    modifier: Modifier = Modifier,
    radius: Dp = GsBlurTier.radius,
) {
    if (GsBlurTier.realBlur) {
        ReferenceBackdrop(modifier.blur(radius))
    } else {
        // API 26 tier (b9). There is no RenderEffect pass, so bake the frost the
        // honest way: the same bloom under a thin airlight veil — the veiling
        // light a GPU blur would add back on API 31+. Two stacked layers, one
        // call, and no undefined bitmap readback. Same soft result, no shader.
        val colors = GsTheme.colors
        Box(modifier = modifier) {
            ReferenceBackdrop(Modifier.matchParentSize())
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to colors.glassHighlight.copy(alpha = 0.16f),
                            1f to colors.glassSurface.copy(alpha = 0.10f),
                        )
                    )
            )
        }
    }
}

/**
 * Frosted backdrop clipped behind a glass shape: the two-line recipe every
 * full-bleed glass surface (sheets, the specimen modal, hero cards) uses to get
 * the OS blur where it exists and the baked look where it does not, from one
 * call. The [content] is drawn on top of the frosted bloom.
 */
@Composable
fun GlassFrostedPanel(
    shape: Shape,
    role: GsGlassRole = GsGlassRole.Modal,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val realBlur = GsBlurTier.realBlur
    val radius = GsBlurTier.radius
    Box(modifier = modifier) {
        // Bottom: the bloom redrawn for this panel, frosted. Blur then clip so
        // the diffused edge is trimmed back to the glass shape. It fills
        // whatever size the glass content above gives the box.
        val frost = Modifier.matchParentSize().clip(shape)
        if (realBlur) {
            ReferenceBackdrop(frost.blur(radius))
        } else {
            // b9 baked frost: bloom, then the airlight veil, then the glass fill.
            ReferenceBackdrop(frost)
            val colors = GsTheme.colors
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            0f to colors.glassHighlight.copy(alpha = 0.16f),
                            1f to colors.glassSurface.copy(alpha = 0.10f),
                        )
                    )
            )
        }
        // Top: the real material — a translucent fill over the frost, the veil,
        // the directional rim and the warm shadow — then the panel content.
        Box(modifier = Modifier.gsGlass(role, shape)) {
            content()
        }
    }
}

/** Shared glass corner scale, so sheets and cards stop inventing their own. */
object GsGlassRadius {
    val chip: Dp = 14.dp
    val field: Dp = 18.dp
    val card: Dp = 22.dp
    val sheet: Dp = 30.dp
    val control: Dp = 999.dp
}
