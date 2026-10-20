package com.grapsee.gsai.ui.components

import androidx.compose.foundation.drawWithCache
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
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
 * THE GLASS — one material, five semantic roles.
 *
 * The reference is a photograph of light through blossom, seen through frosted
 * glass, and the material carries four optical cues at once. Only the first was
 * ever implemented here, which is why the surfaces read as tinted plastic
 * rather than glass:
 *
 *  1. a translucent fill (the old `glassSurface` + `.copy(alpha = …)`);
 *  2. **in-scattered light** — glass adds airlight back, raising the black
 *     floor and lowering local contrast. [GsGlassRole.veilingAlpha];
 *  3. **a directional rim** — the light is above and slightly left in every
 *     source screen, so the top-left edge is bright and the bottom-right edge
 *     falls away. One uniform hairline erases the strongest depth cue in the
 *     artwork, and this is the fix for it;
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

/** The five material roles of the shared contract; rationale in the note above. */
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
            // 3. Directional rim — top-left catches the light, bottom-right falls.
            val rimBrush = Brush.linearGradient(
                0f to rim.copy(alpha = rim.alpha * role.rimStrength),
                0.42f to colors.glassBorder,
                1f to colors.glassRimShadow,
                start = Offset(size.width * 0.06f, 0f),
                end = Offset(size.width * 0.94f, size.height),
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
        size = Offset(size.width, size.height * 0.24f),
    )
}

/** Convert a shape outline to a path, cached by size rather than by frame. */
private fun Outline.toPath(): Path = when (this) {
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
    is Outline.Generic -> Path().apply { addPath(path) }
    is Outline.Rectangular -> Path().apply { addRect(rect) }
}

/** Corner radius helper for callers that draw glass geometry by hand. */
fun DrawScope.glassCornerRadius(radius: Dp): CornerRadius =
    CornerRadius(radius.toPx(), radius.toPx())

/** Shared glass corner scale, so sheets and cards stop inventing their own. */
object GsGlassRadius {
    val chip: Dp = 14.dp
    val field: Dp = 18.dp
    val card: Dp = 22.dp
    val sheet: Dp = 30.dp
    val control: Dp = 999.dp
}
