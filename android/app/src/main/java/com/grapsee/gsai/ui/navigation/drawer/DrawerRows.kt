package com.grapsee.gsai.ui.navigation.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.RefAvatar
import com.grapsee.gsai.ui.components.gsGlass
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsTheme

/**
 * The drawer's drawing primitives. No data, no navigation decisions — every row
 * takes its content and its action as parameters, which is what keeps
 * `GsDrawer.kt` a ~200-line assembly instead of a 500-line list with branches.
 *
 * One measured layout runs through all of them: a leading disc of 44 dp, a title
 * column, and a chevron whose ink ends 24 dp from the panel's right edge. The
 * recent rows put a relative time under the title, which is why their pitch is
 * 56 dp and the destination rows' is 46.
 */

@Composable
fun DrawerSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = DrawerMetrics.sectionLabel,
            letterSpacing = (DrawerMetrics.sectionLabel.value *
                    DrawerMetrics.sectionTracking).sp,
            fontWeight = FontWeight.Medium,
        ),
        color = DrawerMetrics.labelInk,
        maxLines = 1,
        modifier = modifier.padding(
            start = DrawerMetrics.contentInset,
            top = 10.dp,
            bottom = 8.dp,
        ),
    )
}

/** The magnifier + placeholder pill. A button that opens the real search screen. */
@Composable
fun DrawerSearchPill(onOpenSearch: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DrawerMetrics.contentInset)
            .heightIn(min = DrawerMetrics.searchHeight)
            .testTag("drawer-search")
            .gsGlass(GsGlassRole.Sunken, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = "Search chats",
                onClick = onOpenSearch,
            )
            .padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            GsIcons.Search,
            contentDescription = null,
            tint = DrawerMetrics.labelInk,
            modifier = Modifier.size(DrawerMetrics.searchGlyph),
        )
        Text(
            text = "Search chats",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = DrawerMetrics.searchText),
            color = DrawerMetrics.labelInk,
            maxLines = 1,
        )
    }
}

/** "New chat" — the sheet's one warm row. */
@Composable
fun DrawerHeroRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DrawerMetrics.contentInset)
            .heightIn(min = DrawerMetrics.heroHeight)
            .testTag("drawer-hero")
            .clip(RoundedCornerShape(DrawerMetrics.heroRadius))
            .background(
                // The measured gradient: warm apricot at the left stop, the
                // panel's cream at the right. Flat would pass every geometry
                // check and lose the one thing this row is for.
                Brush.horizontalGradient(listOf(DrawerMetrics.heroLeft, DrawerMetrics.heroRight))
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            )
            .padding(start = 8.dp, end = DrawerMetrics.chevronInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DrawerMetrics.discToTitle),
    ) {
        Box(
            modifier = Modifier
                .size(DrawerMetrics.heroOrb)
                .background(
                    Brush.verticalGradient(
                        listOf(DrawerMetrics.heroOrbTop, DrawerMetrics.heroOrbBottom)
                    ),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                GsIcons.Plus,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = DrawerMetrics.heroLabel,
                fontWeight = FontWeight.SemiBold,
            ),
            color = GsTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        DrawerChevron()
    }
}

/** A destination: disc, 13 pt label, chevron. */
@Composable
fun DrawerDestinationRow(
    destination: DrawerDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DrawerMetrics.contentInset)
            .heightIn(min = DrawerMetrics.destPitch)
            .testTag("drawer-dest-${destination.label}")
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) DrawerMetrics.selectedFill else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = destination.label,
                onClick = onClick,
            )
            .semantics {
                this.selected = selected
                if (selected) stateDescription = "Current page"
            }
            .padding(end = DrawerMetrics.chevronInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DrawerMetrics.discToTitle),
    ) {
        DrawerDisc(destination.icon)
        Text(
            text = destination.label,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = DrawerMetrics.rowTitle),
            color = GsTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        DrawerChevron()
    }
}

/**
 * A recent chat: title over its relative time, one disc, one chevron. The
 * timestamp is a real Text, not part of the title's string — the gate counts the
 * ink bands under the four titles, and a drawer that dropped them would still
 * look plausible in a screenshot.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawerRecentRow(
    recent: DrawerRecent,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DrawerMetrics.contentInset)
            .heightIn(min = DrawerMetrics.recentPitch)
            .testTag("drawer-recent-${recent.id}")
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) DrawerMetrics.selectedFill else Color.Transparent)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = recent.title,
                onClick = onClick,
                onLongClickLabel = "Conversation actions",
                onLongClick = onLongPress,
            )
            .padding(end = DrawerMetrics.chevronInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DrawerMetrics.discToTitle),
    ) {
        DrawerDisc(recent.icon)
        Column(Modifier.weight(1f)) {
            Text(
                text = recent.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = DrawerMetrics.chatTitle,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = GsTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = recent.timestamp,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = DrawerMetrics.timestamp),
                color = DrawerMetrics.timestampInk,
                maxLines = 1,
            )
        }
        DrawerChevron()
    }
}

@Composable
fun DrawerFooter(
    identity: DrawerIdentity,
    onAccount: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DrawerMetrics.contentInset, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Account options",
                    onClick = onAccount,
                )
                .semantics { contentDescription = "Account options" }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RefAvatar(initials = identity.initials, size = DrawerMetrics.avatar)
            Column {
                Text(
                    text = identity.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = DrawerMetrics.footerName,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = GsTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = identity.workspace,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = DrawerMetrics.footerSub),
                    color = DrawerMetrics.timestampInk,
                    maxLines = 1,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(DrawerMetrics.gear)
                .testTag("drawer-settings")
                .gsGlass(GsGlassRole.FloatingControl, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Settings",
                    onClick = onSettings,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                GsIcons.Settings,
                contentDescription = "Settings",
                tint = GsTheme.colors.textSecondary,
                modifier = Modifier.size(DrawerMetrics.gearGlyph),
            )
        }
    }
}

@Composable
private fun DrawerDisc(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(DrawerMetrics.disc)
            .gsGlass(GsGlassRole.FloatingControl, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = GsTheme.colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun DrawerChevron() {
    Icon(
        GsIcons.ChevronRight,
        contentDescription = null,
        tint = DrawerMetrics.chevronInk,
        modifier = Modifier.size(DrawerMetrics.chevron),
    )
}
