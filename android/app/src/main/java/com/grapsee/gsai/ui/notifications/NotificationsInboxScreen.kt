package com.grapsee.gsai.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.notifications.NoticeInbox
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefRowDivider
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme

/**
 * Notifications Inbox — docs/reference/pending/notifications-inbox.png.
 *
 * The sheet's chips, per-tab counts, Today/Earlier split, unread dots and
 * "Mark all as read" are all real: they read [NoticeInbox], which only ever
 * receives a row after `GsNotifier` has successfully posted that exact notice.
 * There is no seeded content, so a fresh install is genuinely empty and says
 * "You're all caught up" — which is the truth, not a designed state.
 *
 * The Billing chip is always empty because nothing in this build produces a
 * billing notice: there is no payment processor to take a payment from.
 */
@Composable
fun NotificationsInboxScreen(
    onBack: () -> Unit,
    // Accepted so the existing route call site keeps its shape; the inbox has
    // no sub-destinations of its own yet.
    onNavigate: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val colors = GsTheme.colors

    // `revision` is read so a write from the dispatcher recomposes the list.
    @Suppress("UNUSED_EXPRESSION")
    NoticeInbox.revision

    var tab by remember { mutableStateOf("all") }
    val entries = remember(NoticeInbox.revision) { NoticeInbox.read(context) }
    val counts = remember(NoticeInbox.revision) { NoticeInbox.counts(context) }

    val filtered = entries.filter { e ->
        when (tab) {
            "messages" -> e.kindId == "messages"
            "updates" -> e.kindId == "product-updates" || e.kindId == "ai-updates"
            "account" -> e.kindId == "account-activity"
            "billing" -> false
            "tips" -> e.kindId == "tips"
            else -> true
        }
    }

    val now = System.currentTimeMillis()
    val dayStart = remember(now) { startOfToday() }
    val today = filtered.filter { it.createdAtMillis >= dayStart }
    val earlier = filtered.filter { it.createdAtMillis < dayStart }

    RefSettingsPage(
        title = "Notifications",
        subtitle = "Your updates, alerts and messages",
        onBack = onBack,
    ) {
        // ---- Tabs with real counts ----------------------------------------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TabChip("All", counts["all"] ?: 0, "all", tab) { tab = it }
            TabChip("Account", counts["account"] ?: 0, "account", tab) { tab = it }
            TabChip("Billing", counts["billing"] ?: 0, "billing", tab) { tab = it }
            TabChip("Messages", counts["messages"] ?: 0, "messages", tab) { tab = it }
            TabChip("Updates", counts["updates"] ?: 0, "updates", tab) { tab = it }
        }

        if (filtered.isEmpty()) {
            Spacer(Modifier.height(18.dp))
            RefCard(role = GsGlassRole.CardLight, padding = androidx.compose.foundation.layout.PaddingValues(22.dp)) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RefWellTile(GsIcons.Bell, GsWellTone.Lilac, 44.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "You're all caught up!",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GsFonts.Product,
                        color = colors.textPrimary,
                    )
                    Text(
                        if (tab == "billing") {
                            "Payments aren't processed in this build, so no billing notices exist yet."
                        } else {
                            "Nexly has not sent you a notice yet."
                        },
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textTertiary,
                    )
                }
            }
        } else {
            if (today.isNotEmpty()) {
                RefGroupHeader(
                    title = "Today",
                    trailing = {
                        Text(
                            "Mark all as read",
                            fontSize = 11.5.sp,
                            fontFamily = GsFonts.Product,
                            color = colors.textSecondary,
                            modifier = Modifier
                                .clickable { NoticeInbox.markAllRead(context) }
                                .padding(4.dp),
                        )
                    },
                )
                NoticeList(today, context)
            }
            if (earlier.isNotEmpty()) {
                RefGroupHeader(title = "Earlier")
                NoticeList(earlier, context)
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun NoticeList(entries: List<NoticeInbox.Entry>, context: android.content.Context) {
    RefCard(role = GsGlassRole.CardLight) {
        entries.forEachIndexed { index, e ->
            if (index > 0) RefRowDivider()
            NoticeRow(e) { NoticeInbox.markRead(context, e.id) }
        }
    }
}

@Composable
private fun NoticeRow(entry: NoticeInbox.Entry, onClick: () -> Unit) {
    val colors = GsTheme.colors
    val (icon, tone) = styleFor(entry.kindId)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RefWellTile(icon, tone, 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = GsFonts.Product,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.width(6.dp))
                // An unread dot, not a badge: it disappears the moment the row
                // is opened, because the store really does flip the flag.
                if (!entry.read) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(tone.ink),
                    )
                }
            }
            Text(
                entry.body,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontFamily = GsFonts.Product,
                color = colors.textTertiary,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            relativeTime(entry.createdAtMillis),
            fontSize = 10.5.sp,
            fontFamily = GsFonts.Product,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun TabChip(
    label: String,
    count: Int,
    id: String,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val colors = GsTheme.colors
    val active = selected == id
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) colors.glassSurface else colors.glassSurface.copy(alpha = 0.45f))
            .clickable { onSelect(id) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontFamily = GsFonts.Product,
            color = if (active) colors.textPrimary else colors.textSecondary,
        )
        Text(
            count.toString(),
            fontSize = 10.sp,
            fontFamily = GsFonts.Product,
            color = colors.textTertiary,
        )
    }
}

private fun styleFor(kindId: String): Pair<ImageVector, GsWellTone> = when (kindId) {
    "messages" -> GsIcons.MessageCircle to GsWellTone.Rose
    "ai-updates" -> GsIcons.Sparkles to GsWellTone.Lilac
    "product-updates" -> GsIcons.Star to GsWellTone.Lilac
    "account-activity" -> GsIcons.UserRound to GsWellTone.Peach
    "tips" -> GsIcons.Lightbulb to GsWellTone.Peach
    else -> GsIcons.Bell to GsWellTone.Blue
}

private fun startOfToday(): Long {
    val cal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

/** Real elapsed time from the stored stamp — never a canned "2h ago". */
private fun relativeTime(then: Long): String {
    val delta = (System.currentTimeMillis() - then).coerceAtLeast(0L)
    val minutes = delta / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}
