package com.grapsee.gsai.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.notifications.NoticeInbox
import com.grapsee.gsai.ui.chat.content.NexlyGlyph
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefHeaderAction
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefTagPill
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.LocalGsIsDark

/** One chip in the mock's five-up strip. */
private data class InboxTab(
    val id: String,
    val label: String,
    val glyph: String,
    val tone: GsWellTone,
)

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
 *
 * The chips are the mock's five equal cells with their icon above the label
 * and the count below; the selected cell carries the peach wash. The header's
 * sliders button opens the same five filters as a list, so it resolves to a
 * real action instead of a painted control.
 */
@Composable
fun NotificationsInboxScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
    /**
     * The reader's clock. Production passes the wall clock; the screenshot
     * harness passes a fixed 18:00 so the Today/Earlier split and the "2h ago"
     * labels cannot change with the hour the capture happens to run.
     */
    now: Long = System.currentTimeMillis(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val colors = GsTheme.colors

    // `revision` is read so a write from the dispatcher recomposes the list.
    @Suppress("UNUSED_EXPRESSION")
    NoticeInbox.revision

    var tab by remember { mutableStateOf("all") }
    var earlierExpanded by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var openNotice by remember { mutableStateOf<NoticeInbox.Entry?>(null) }

    // Newest first is the reader's contract, not the producer's: `record()`
    // prepends today, but a fixture, a restored backup or a future producer
    // may not, and the Today/Earlier split below assumes the order.
    val entries = remember(NoticeInbox.revision) {
        NoticeInbox.read(context).sortedByDescending { it.createdAtMillis }
    }
    val counts = remember(NoticeInbox.revision) { NoticeInbox.counts(context) }

    val tabs = listOf(
        InboxTab("all", "All", "bell", GsWellTone.Peach),
        InboxTab("account", "Account", "user", GsWellTone.Blue),
        InboxTab("billing", "Billing", "credit-card", GsWellTone.Mint),
        InboxTab("messages", "Messages", "message-circle", GsWellTone.Rose),
        InboxTab("updates", "Updates", "sparkles", GsWellTone.Lilac),
    )

    val filtered = entries.filter { e ->
        when (tab) {
            "messages" -> e.kindId == "messages"
            "updates" -> e.kindId == "product-updates" || e.kindId == "ai-updates"
            "account" -> e.kindId == "account-activity"
            "billing" -> false
            else -> true
        }
    }

    val dayStart = remember(now) { startOfToday(now) }
    val today = filtered.filter { it.createdAtMillis >= dayStart }
    val earlier = filtered.filter { it.createdAtMillis < dayStart }
    val unread = filtered.count { !it.read }

    RefSettingsPage(
        title = "Notifications",
        subtitle = "Your updates, alerts and messages",
        onBack = onBack,
        headerTrailing = {
            RefHeaderAction(
                icon = GsIcons.SlidersHorizontal,
                label = "Filter notifications",
                onClick = { showFilter = true },
            )
        },
    ) {
        // ---- Tabs with real counts ---------------------------------------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            tabs.forEach { item ->
                InboxChip(
                    tab = item,
                    count = counts[item.id] ?: 0,
                    selected = tab == item.id,
                    modifier = Modifier.weight(1f),
                    onClick = { tab = item.id },
                )
            }
        }

        if (filtered.isEmpty()) {
            Spacer(Modifier.height(18.dp))
            AllCaughtUpCard(
                body = when (tab) {
                    "billing" -> "Payments aren't processed in this build, so no billing notices exist yet."
                    else -> "Nexly has not sent you a notice yet."
                },
            )
        } else {
            if (today.isNotEmpty()) {
                RefGroupHeader(
                    title = "Today",
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .testTag("notice-mark-all")
                                .clickable(enabled = unread > 0) {
                                    NoticeInbox.markAllRead(context)
                                }
                                .padding(horizontal = 3.dp, vertical = 2.dp),
                        ) {
                            Icon(
                                GsIcons.CheckCircle2,
                                contentDescription = null,
                                tint = if (unread > 0) colors.textSecondary else colors.textTertiary.copy(alpha = 0.5f),
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Mark all as read",
                                fontSize = 9.sp,
                                fontFamily = GsFonts.Product,
                                color = if (unread > 0) colors.textSecondary else colors.textTertiary.copy(alpha = 0.5f),
                            )
                        }
                    },
                )
                NoticeList(today, now) { openNotice = it }
            }
            if (earlier.isNotEmpty()) {
                val shown = if (earlierExpanded) earlier else earlier.take(3)
                RefGroupHeader(
                    title = "Earlier",
                    caption = if (earlierExpanded) "Show less" else "See all",
                    onCaptionClick = { earlierExpanded = !earlierExpanded },
                )
                NoticeList(shown, now) { openNotice = it }
            }
            Spacer(Modifier.height(14.dp))
            AllCaughtUpCard(body = "No more notifications for now.")
        }
        Spacer(Modifier.height(14.dp))
    }

    openNotice?.let { notice ->
        NoticeDialog(
            notice = notice,
            now = now,
            onDismiss = { openNotice = null },
            onOpenSettings = {
                openNotice = null
                onNavigate(com.grapsee.gsai.ui.navigation.GsRoutes.SETTINGS_NOTIFICATIONS)
            },
        )
        // Opening a notice is what marks it read — the dot disappears for the
        // same reason the store flips the flag.
        androidx.compose.runtime.LaunchedEffect(notice.id) {
            if (!notice.read) NoticeInbox.markRead(context, notice.id)
        }
    }

    if (showFilter) {
        AlertDialog(
            onDismissRequest = { showFilter = false },
            title = {
                Text(
                    "Filter notifications",
                    fontFamily = GsFonts.Product,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            },
            text = {
                Column {
                    tabs.forEach { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    tab = item.id
                                    showFilter = false
                                }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            com.grapsee.gsai.ui.components.RefGlyphWell(
                                glyph = item.glyph,
                                tone = item.tone,
                                size = 30.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                item.label,
                                modifier = Modifier.weight(1f),
                                fontSize = 10.5.sp,
                                fontFamily = GsFonts.Product,
                                color = colors.textPrimary,
                            )
                            Text(
                                "${counts[item.id] ?: 0}",
                                fontSize = 9.sp,
                                fontFamily = GsFonts.Product,
                                color = colors.textTertiary,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFilter = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun InboxChip(
    tab: InboxTab,
    count: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = GsTheme.colors
    val isDark = LocalGsIsDark.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) {
                    GsWellTone.Peach.wash.copy(alpha = if (isDark) 0.30f else 0.55f)
                } else if (isDark) {
                    colors.glassSurface.copy(alpha = 0.45f)
                } else {
                    Color.White.copy(alpha = 0.38f)
                }
            )
            .border(
                1.dp,
                if (selected) GsWellTone.Peach.ink.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.55f),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NexlyGlyph(name = tab.glyph, size = 15.dp, color = tab.tone.ink)
        Spacer(Modifier.height(5.dp))
        Text(
            tab.label,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            fontFamily = GsFonts.Product,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(
                    if (selected) {
                        GsWellTone.Peach.ink.copy(alpha = 0.14f)
                    } else {
                        colors.border.copy(alpha = 0.28f)
                    }
                )
                .padding(horizontal = 5.dp, vertical = 1.dp),
        ) {
            Text(
                "$count",
                fontSize = 8.sp,
                fontFamily = GsFonts.Product,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun NoticeList(
    entries: List<NoticeInbox.Entry>,
    now: Long,
    onOpen: (NoticeInbox.Entry) -> Unit,
) {
    // The mock draws each notice as its own card with a 5pt gap, not as rows
    // inside one card — that is what makes an unread row read as a separate
    // object rather than a table line.
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        entries.forEach { entry ->
            RefCard(
                role = GsGlassRole.CardLight,
                shape = RoundedCornerShape(20.dp),
                padding = PaddingValues(0.dp),
            ) {
                NoticeRow(entry, now) { onOpen(entry) }
            }
        }
    }
}

@Composable
private fun NoticeRow(entry: NoticeInbox.Entry, now: Long, onClick: () -> Unit) {
    val colors = GsTheme.colors
    val style = styleFor(entry.kindId)
    Row(
        Modifier
            .fillMaxWidth()
            .testTag("notice-row-${entry.id}")
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RefWellTile(style.icon, style.tone, 34.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.title,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = GsFonts.Product,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(5.dp))
                RefTagPill(style.label, style.tone.ink)
            }
            Spacer(Modifier.height(1.dp))
            Text(
                entry.body,
                fontSize = 8.sp,
                lineHeight = 10.5.sp,
                fontFamily = GsFonts.Product,
                color = colors.textTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                relativeTime(entry.createdAtMillis, now),
                fontSize = 8.5.sp,
                fontFamily = GsFonts.Product,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(6.dp))
            if (!entry.read) {
                // An unread dot, not a badge: it disappears the moment the row
                // is opened, because the store really does flip the flag.
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(style.tone.ink),
                )
            } else {
                Icon(
                    GsIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

private data class NoticeStyle(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tone: GsWellTone,
    val label: String,
)

private fun styleFor(kindId: String): NoticeStyle = when (kindId) {
    "messages" -> NoticeStyle(GsIcons.MessageCircle, GsWellTone.Rose, "Messages")
    "ai-updates" -> NoticeStyle(GsIcons.Sparkles, GsWellTone.Lilac, "Update")
    "product-updates" -> NoticeStyle(GsIcons.Cog, GsWellTone.Blue, "Updates")
    "account-activity" -> NoticeStyle(GsIcons.UserRound, GsWellTone.Peach, "Account")
    "tips" -> NoticeStyle(GsIcons.Lightbulb, GsWellTone.Peach, "Tips")
    else -> NoticeStyle(GsIcons.Bell, GsWellTone.Blue, "Update")
}

/** The mock's closing card: dashes, "You're all caught up!", one whisper. */
@Composable
private fun AllCaughtUpCard(body: String) {
    val colors = GsTheme.colors
    RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(horizontal = 14.dp, vertical = 16.dp)) {
        // The mock flanks the title itself with two short rules; full-width
        // weight rules vanished into the cream glass and were not its shape.
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CaughtUpRule()
                Text(
                    "You're all caught up!",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = GsFonts.Product,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                CaughtUpRule()
            }
            Text(
                body,
                fontSize = 8.5.sp,
                fontFamily = GsFonts.Product,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun CaughtUpRule() {
    Box(
        Modifier
            .width(28.dp)
            .height(1.dp)
            .background(GsTheme.colors.textTertiary.copy(alpha = 0.35f)),
    )
}

@Composable
private fun NoticeDialog(
    notice: NoticeInbox.Entry,
    now: Long,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = GsTheme.colors
    val style = styleFor(notice.kindId)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    notice.title,
                    fontFamily = GsFonts.Product,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Text(
                    "${style.label} • ${relativeTime(notice.createdAtMillis, now)}",
                    fontFamily = GsFonts.Product,
                    fontSize = 9.5.sp,
                    color = colors.textTertiary,
                )
            }
        },
        text = {
            Text(
                notice.body,
                fontFamily = GsFonts.Product,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = colors.textSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = onOpenSettings) { Text("Notification settings") }
        },
    )
}

private fun startOfToday(now: Long): Long {
    val cal = java.util.Calendar.getInstance().apply {
        timeInMillis = now
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

/** Real elapsed time from the stored stamp — never a canned "2h ago". */
private fun relativeTime(then: Long, now: Long): String {
    val delta = (now - then).coerceAtLeast(0L)
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
