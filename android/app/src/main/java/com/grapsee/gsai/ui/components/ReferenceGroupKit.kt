package com.grapsee.gsai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import com.grapsee.gsai.data.SettingsStore
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.LocalGsIsDark

/**
 * REFERENCE GROUP KIT — the Settings pages' shared primitives, measured from
 * the committed mocks (docs/reference/settings/settings-geometry.json):
 *
 *   card inset 21.8pt (0.0554 W) · wells 28pt pastel circles · rows 48dp-ish
 *   pitch with 50pt-indented hairlines · section title 17 + right caption 12
 *   · compact peach pills.
 *
 * Additive on purpose: RefRow / RefIconTile / RefSectionLabel / RefDivider /
 * RefPrimaryButton stay EXACTLY as they are, because ~15 screens compose them
 * and no mock asks those screens to move. The settings screens compose this
 * kit instead (the plan's "one kit per platform" rule).
 */

/** The mocks' pastel wells: a pale wash and the ink drawn on it. */
enum class GsWellTone(val wash: Color, val ink: Color) {
    Peach(Color(0xFFFDE8DA), Color(0xFFB85C12)),
    Rose(Color(0xFFFBE0E6), Color(0xFFC43366)),
    Lilac(Color(0xFFEDE9FE), Color(0xFF5A50D6)),
    Blue(Color(0xFFDDEBFC), Color(0xFF1663C4)),
    Mint(Color(0xFFDDF3E6), Color(0xFF177A50)),
}

/** Section title with the mock's right-aligned whisper caption. */
@Composable
fun RefGroupHeader(
    title: String,
    caption: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = GsTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = GsFonts.Product,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.weight(1f))
        if (caption != null) {
            Text(
                text = caption,
                fontSize = 12.sp,
                fontFamily = GsFonts.Product,
                color = colors.textTertiary,
                maxLines = 1,
            )
        }
    }
}

/**
 * The 28pt pale circle beside a settings row. One tone per concept, taken
 * from the mocks: peach for identity/features, lilac for appearance/mail,
 * blue for privacy/data, mint for language/green states, rose for destructive.
 */
@Composable
fun RefWellTile(
    icon: ImageVector,
    tone: GsWellTone,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 5.dp,
                shape = CircleShape,
                ambientColor = tone.ink.copy(alpha = 0.12f),
                spotColor = tone.ink.copy(alpha = 0.20f),
            )
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.55f),
                        tone.wash,
                    )
                ),
                CircleShape,
            )
            .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tone.ink,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

/**
 * The mock's navigation row: leading well slot, title + optional detail, and
 * a trailing slot (chevron, value, toggle). [onClick] null renders a static row.
 */
@Composable
fun RefNavRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit = { DefaultChevron() },
) {
    val colors = GsTheme.colors
    val base = Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp)
        .then(
            if (onClick != null) {
                Modifier.clickable(role = Role.Button, onClickLabel = title, onClick = onClick)
            } else {
                Modifier
            }
        )
        .padding(horizontal = 12.dp, vertical = 7.dp)
    Row(
        modifier = modifier.then(base),
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
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = GsFonts.Product,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    fontSize = 11.5.sp,
                    lineHeight = 14.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

/** A right-aligned value on a static row (e.g. "Not set", "Balanced"). */
@Composable
fun RefRowValue(text: String) {
    Text(
        text = text,
        fontSize = 11.5.sp,
        fontFamily = GsFonts.Product,
        color = GsTheme.colors.textTertiary,
        maxLines = 1,
    )
}

@Composable
fun DefaultChevron() {
    val colors = GsTheme.colors
    Icon(
        com.grapsee.gsai.ui.icons.GsIcons.ChevronRight,
        contentDescription = null,
        tint = colors.textTertiary,
        modifier = Modifier.size(16.dp),
    )
}

/** The mock's hairline between rows inside one card. */
@Composable
fun RefRowDivider(startIndent: Dp = 50.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startIndent, end = 12.dp)
            .height(1.dp)
            .background(GsTheme.colors.divider),
    )
}

/**
 * The compact pill the mock uses inline ("Upgrade →"): 32pt tall, peach
 * gradient, white label — not the full-width 52dp RefPrimaryButton.
 */
@Composable
fun RefInlinePill(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val colors = GsTheme.colors
    Row(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color(0x2EEDBEA4),
                spotColor = Color(0x59EB9E72),
            )
            .background(
                Brush.horizontalGradient(listOf(colors.accentSoft, colors.accentStrong)),
                RoundedCornerShape(16.dp),
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.85f),
                    1f to Color.White.copy(alpha = 0.30f),
                ),
                RoundedCornerShape(16.dp),
            )
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = GsFonts.Product,
            color = Color.White,
        )
        if (icon != null) {
            Spacer(Modifier.width(6.dp))
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
        }
    }
}

/** One cell of the memory screen's four-up capability strip. */
data class RefStripTile(
    val icon: ImageVector,
    val tone: GsWellTone,
    val title: String,
    val caption: String,
)

/**
 * The mock's four-up strip: equal columns, hairline dividers between them,
 * each column a well + title + caption.
 */
@Composable
fun RefTileStrip(tiles: List<RefStripTile>, modifier: Modifier = Modifier) {
    val colors = GsTheme.colors
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        tiles.forEachIndexed { index, tile ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                RefWellTile(icon = tile.icon, tone = tile.tone, size = 34.dp)
                Spacer(Modifier.height(7.dp))
                Text(
                    tile.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GsFonts.Product,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    tile.caption,
                    fontSize = 9.5.sp,
                    lineHeight = 12.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (index < tiles.lastIndex) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(64.dp)
                        .padding(vertical = 14.dp)
                        .background(colors.divider),
                )
            }
        }
    }
}

/** The quiet info card that closes Profile & Preferences and Memory. */
@Composable
fun RefInfoCard(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val colors = GsTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.glassSurface.copy(alpha = if (LocalGsIsDark.current) 0.50f else 0.55f))
            .border(1.dp, colors.glassBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.accentStrong, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text,
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
            fontFamily = GsFonts.Product,
            color = colors.textTertiary,
        )
    }
}

/**
 * The settings scaffold: pearl backdrop (light) or the ambient bloom (dark),
 * the mock's back well + title block, and a scrolling content column inset
 * 22dp — the measured card inset (settings-geometry.json, 0.0554 W).
 *
 * Deliberately NOT RefPage: RefPage's back affordance is a bare chevron and
 * its inset is 16dp; both differ from the mocks, and RefPage is composed by
 * ten other screens that must not move.
 */
@Composable
fun RefSettingsPage(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = GsTheme.colors
    val isDark = LocalGsIsDark.current
    Box(Modifier.fillMaxSize()) {
        if (!isDark && SettingsStore.backgroundStyle != "Solid") {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(com.grapsee.gsai.R.drawable.gs_home_backdrop),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .gsGlass(GsGlassRole.FloatingControl, CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        com.grapsee.gsai.ui.icons.GsIcons.ChevronLeft,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GsFonts.Product,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            fontFamily = GsFonts.Product,
                            color = colors.textTertiary,
                            maxLines = 1,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 26.dp),
                content = content,
            )
        }
    }
}
