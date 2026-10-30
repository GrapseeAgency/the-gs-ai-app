package com.grapsee.gsai.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.LocalGsIsDark

/**
 * REFERENCE SHEET KIT — the primitives the six owner sheets share and the
 * settings kit did not have: a 34×20 switch (Material's is 52×32, half again
 * the mock's), the pastel hero wash, compact tag pills, artwork bleed, the
 * 3-up tiles with their chevron/plus corner, usage bars and the two small
 * header actions.
 *
 * Everything here is measured from the six PNGs at 1.644 px/pt (a 646px phone
 * panel = 393pt): hero radius 20, tag pill 8.5pt on a 6% wash, tiles 16
 * radius with a 34pt well, bars 4pt tall, switches 31×18 with a 16pt thumb.
 */

/** The hero wash: lavender → blush → peach, the order every sheet uses. */
private val heroWash = listOf(
    Color(0xFFE7DBFB),
    Color(0xFFF9DCE8),
    Color(0xFFFDE8D8),
)

/** The closing/upgrade card wash: cooler, quieter, still the same three hues. */
private val quietWash = listOf(
    Color(0xFFEFE6FC),
    Color(0xFFFBE9F1),
)

/**
 * The mock's switch. The Material 3 switch is 52×32 — a third again as tall
 * and half again as wide as the reference's 31×18, which is what made every
 * toggle row on these sheets read a size too loud.
 *
 * [enabled] false keeps the shape (the row still owns its slot) but removes
 * the touch target and the colour, which is how a channel this build does not
 * have is drawn without pretending it works.
 */
@Composable
fun RefSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = GsTheme.colors
    val trackWidth = 34.dp
    val thumb = 16.dp
    val travel by animateDpAsState(
        targetValue = if (checked) trackWidth - thumb - 4.dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 900f),
        label = "ref-switch-thumb",
    )
    val onWash = Brush.horizontalGradient(
        listOf(colors.accentSoft, colors.accentStrong)
    )
    Box(
        modifier = modifier
            .width(trackWidth)
            .height(20.dp)
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (checked) {
                    Modifier.background(onWash)
                } else {
                    Modifier.background(
                        if (LocalGsIsDark.current) {
                            colors.border.copy(alpha = 0.55f)
                        } else {
                            Color.White.copy(alpha = 0.62f)
                        }
                    )
                }
            )
            .border(
                1.dp,
                if (checked) {
                    Color.White.copy(alpha = 0.42f)
                } else {
                    colors.border.copy(alpha = 0.55f)
                },
                RoundedCornerShape(999.dp),
            )
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .alphaFor(enabled),
    ) {
        Box(
            Modifier
                .offset(x = travel, y = 2.dp)
                .size(thumb)
                .shadow(2.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.16f))
                .background(Color.White, CircleShape),
        )
    }
}

private fun Modifier.alphaFor(enabled: Boolean): Modifier =
    if (enabled) this else this.alpha(0.55f)

/** A compact status tag ("Update", "Connected", "Primary"): 8.5pt on a wash. */
@Composable
fun RefTagPill(
    text: String,
    tone: Color = GsTheme.colors.success,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tone.copy(alpha = 0.13f))
            .padding(horizontal = 6.dp, vertical = 1.5.dp),
    ) {
        Text(
            text = text,
            fontSize = 7.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = GsFonts.Product,
            color = tone,
            maxLines = 1,
        )
    }
}

/**
 * The sheet's hero: a pastel wash card with an optional composition bleeding
 * past its right edge, and its content laid out in the mock's order.
 */
@Composable
fun RefHeroCard(
    modifier: Modifier = Modifier,
    bleed: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(heroWash))
            .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(20.dp)),
    ) {
        // The mock's sheen: a white bloom in the upper-left third.
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(size.width * 0.22f, size.height * 0.30f),
                    radius = size.width * 0.55f,
                ),
                radius = size.width * 0.55f,
                center = Offset(size.width * 0.22f, size.height * 0.30f),
            )
        }
        if (bleed != null) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                bleed()
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** The quieter gradient the "Upgrade" and "Connect New App" cards use. */
@Composable
fun RefQuietWashCard(
    modifier: Modifier = Modifier,
    bleed: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(quietWash))
            .border(1.dp, Color.White.copy(alpha = 0.50f), RoundedCornerShape(20.dp)),
    ) {
        if (bleed != null) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                bleed()
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** Artwork that bleeds off the card's right edge at a fixed optical size. */
@Composable
fun RefBleedArt(
    art: RefArt,
    size: Dp,
    modifier: Modifier = Modifier,
    alpha: Float = 0.85f,
) {
    // Compose rejects negative padding; an offset bleeds the art past the
    // card's right edge by the same optical amount.
    Box(modifier.offset(x = (size.value * 0.22f).dp)) {
        RefArtwork(
            art = art,
            size = size,
            modifier = Modifier.alpha(alpha),
            contentDescription = "Illustration",
        )
    }
}

/**
 * One 3-up tile: well, optional corner chevron/plus, title, caption. The
 * mock's category grid and integration strip are the same tile at two sizes.
 */
@Composable
fun RefSheetTile(
    title: String,
    caption: String,
    icon: ImageVector? = null,
    glyph: String? = null,
    brandGlyph: String? = null,
    brandTint: Color? = null,
    tone: GsWellTone = GsWellTone.Lilac,
    modifier: Modifier = Modifier,
    wellSize: Dp = 34.dp,
    corner: TileCorner = TileCorner.Chevron,
    centered: Boolean = false,
    minHeight: Dp = 0.dp,
    /**
     * Push the corner affordance to the card's bottom edge instead of letting
     * it ride under the caption. Only meaningful inside a bounded row (see
     * `IntrinsicSize.Min`); without that the weight has nothing to divide.
     */
    pinCorner: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = GsTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(shape)
            .background(
                if (LocalGsIsDark.current) {
                    colors.glassSurface.copy(alpha = 0.55f)
                } else {
                    Color.White.copy(alpha = 0.42f)
                }
            )
            .border(1.dp, Color.White.copy(alpha = 0.62f), shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClickLabel = title, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (centered) Arrangement.Center else Arrangement.SpaceBetween,
        ) {
            if (icon != null) {
                RefWellTile(icon = icon, tone = tone, size = wellSize)
            } else if (brandGlyph != null) {
                RefBrandWell(glyph = brandGlyph, size = wellSize, tint = brandTint)
            } else if (glyph != null) {
                RefGlyphWell(glyph = glyph, tone = tone, size = wellSize)
            }
            if (corner != TileCorner.None) {
                when (corner) {
                    TileCorner.Chevron -> Icon(
                        GsIcons.ChevronRight,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(13.dp),
                    )
                    TileCorner.Plus, TileCorner.None, TileCorner.BottomChevron -> Unit
                }
            }
        }
        if (centered) Spacer(Modifier.height(6.dp)) else Spacer(Modifier.height(9.dp))
        Text(
            title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = GsFonts.Product,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        Spacer(Modifier.height(1.dp))
        Text(
            caption,
            fontSize = 8.sp,
            lineHeight = 10.sp,
            fontFamily = GsFonts.Product,
            color = colors.textTertiary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        if (corner == TileCorner.Plus) {
            if (pinCorner) {
                Spacer(Modifier.weight(1f))
            } else {
                Spacer(Modifier.height(5.dp))
            }
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(colors.border.copy(alpha = 0.30f))
                    .align(if (centered) Alignment.CenterHorizontally else Alignment.Start),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    GsIcons.Plus,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
        if (corner == TileCorner.BottomChevron) {
            Spacer(Modifier.height(5.dp))
            Icon(
                GsIcons.ChevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier
                    .size(12.dp)
                    .align(if (centered) Alignment.CenterHorizontally else Alignment.Start),
            )
        }
    }
}

enum class TileCorner { Chevron, Plus, BottomChevron, None }

/**
 * The six sheets' row: 10pt title, 8pt detail at 10.5 line height, one hairline
 * of air between them, and 11pt above/below. That is the mock's measured
 * pitch: title bands 92px apart on the two-line cards (56.0pt), 74px on the
 * one-line cards (45.0pt). `RefNavRow` (42.5 + 9.7) is the settings hub's,
 * which is why these sheets no longer compose it.
 */
@Composable
fun RefSheetRow(
    title: String,
    subtitle: String? = null,
    /**
     * A third line in the text column — the provider sheets draw their status
     * pill under the subtitle, not in the trailing slot. Defaulted so no
     * existing row changes height.
     */
    extra: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit = { DefaultChevron() },
) {
    val colors = GsTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClickLabel = title, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(11.dp))
        } else {
            Spacer(Modifier.width(2.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = GsFonts.Product,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(1.dp))
                Text(
                    subtitle,
                    fontSize = 8.sp,
                    lineHeight = 10.5.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (extra != null) {
                Spacer(Modifier.height(4.dp))
                extra()
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

/** The same row with the sheet's own 34×20 switch in the trailing slot. */
@Composable
fun RefSheetToggle(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = GsTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(11.dp))
        } else {
            Spacer(Modifier.width(2.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = GsFonts.Product,
                color = if (enabled) colors.textPrimary else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(1.dp))
                Text(
                    subtitle,
                    fontSize = 8.sp,
                    lineHeight = 10.5.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        RefSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/**
 * A well carrying a Tabler glyph instead of a vector: the brand marks on
 * Connected Apps and the mascot glyphs on Help come from the shared icon font,
 * so both platforms draw the same artwork for the same name.
 */
@Composable
fun RefGlyphWell(
    glyph: String,
    tone: GsWellTone,
    size: Dp,
    modifier: Modifier = Modifier,
    circle: Boolean = true,
) {
    val shape = if (circle) CircleShape else RoundedCornerShape(size * 0.28f)
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 4.dp,
                shape = shape,
                ambientColor = tone.ink.copy(alpha = 0.10f),
                spotColor = tone.ink.copy(alpha = 0.16f),
            )
            .background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.72f), tone.wash)),
                shape,
            )
            .border(1.dp, Color.White.copy(alpha = 0.85f), shape),
        contentAlignment = Alignment.Center,
    ) {
        com.grapsee.gsai.ui.chat.content.NexlyGlyph(
            name = glyph,
            size = size * 0.50f,
            color = tone.ink,
        )
    }
}

/** A brand mark on its own paper tile — the provider rows' 34pt well. */
@Composable
fun RefBrandWell(
    glyph: String,
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.30f))
            .background(Color.White.copy(alpha = if (LocalGsIsDark.current) 0.14f else 0.86f))
            .border(1.dp, Color.White.copy(alpha = 0.65f), RoundedCornerShape(size * 0.30f)),
        contentAlignment = Alignment.Center,
    ) {
        com.grapsee.gsai.ui.chat.content.NexlyGlyph(
            name = glyph,
            size = size * 0.56f,
            color = tint ?: GsTheme.colors.textPrimary,
        )
    }
}

/**
 * The mock's white pill inside a hero: content-sized, 9.5pt, ~28pt tall.
 * `RefQuietButton` is the full-width 52dp settings button and cannot be used
 * here — its baked-in fillMaxWidth starves the hero's copy and stretches the
 * pill to its assigned width.
 */
@Composable
fun RefSheetPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GsTheme.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.78f))
            .border(1.dp, Color.White.copy(alpha = 0.90f), RoundedCornerShape(999.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = GsFonts.Product,
            color = colors.textPrimary,
            maxLines = 1,
        )
        Spacer(Modifier.width(3.dp))
        Icon(
            GsIcons.ChevronRight,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(10.dp),
        )
    }
}

/** The usage bar: 4pt, rounded, with a tone-coloured fill sized by fraction. */
@Composable
fun RefProgressBar(
    fraction: Float?,
    tone: Color,
    modifier: Modifier = Modifier,
) {
    val colors = GsTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(colors.border.copy(alpha = 0.30f)),
    ) {
        if (fraction != null) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(tone.copy(alpha = 0.72f), tone)
                        )
                    ),
            )
        }
    }
}

/** The header's round filter control (sliders). */
@Composable
fun RefHeaderAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GsTheme.colors
    Box(
        modifier = modifier
            .size(34.dp)
            .gsGlass(GsGlassRole.FloatingControl, CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(15.dp))
    }
}

/** The provider rows' "..." — a real action, so it stays a button. */
@Composable
fun RefDotsButton(onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    val colors = GsTheme.colors
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(colors.border.copy(alpha = 0.22f))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        com.grapsee.gsai.ui.chat.content.NexlyGlyph(
            name = "dots",
            size = 13.dp,
            color = colors.textSecondary,
        )
    }
}

/** One label/value row of the Plan Details card. */
@Composable
fun RefFactRow(
    label: String,
    value: String,
    labelWidth: Dp = 88.dp,
    modifier: Modifier = Modifier,
) {
    val colors = GsTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.width(labelWidth),
            fontSize = 9.5.sp,
            fontFamily = GsFonts.Product,
            color = colors.textSecondary,
        )
        Text(
            value,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = GsFonts.Product,
            color = colors.textPrimary,
        )
    }
}
