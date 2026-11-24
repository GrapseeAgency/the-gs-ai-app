package com.grapsee.gsai.ui.notifications

import com.grapsee.gsai.ui.components.argentComponentBorder

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
import com.grapsee.gsai.ui.components.GsChoice
import com.grapsee.gsai.ui.components.GsChoiceDialog
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefHeaderAction
import com.grapsee.gsai.ui.components.RefNavRow
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefTagPill
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.navigation.GsRoutes
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
 * receives a row after `GsNotifier` has successfully posted — or recorded —
 * that exact notice. There is no seeded content, so a fresh install is
 * genuinely empty and says "You're all caught up" — which is the truth, not a
 * designed state.
 *
 * The five chips are the five things the app can actually send: All, Account,
 * Tips, Messages, Updates. A Billing chip used to hold the third slot and
 * could never fill (no payment processor exists here), while Tips — a real
 * kind with a real switch in Notification settings — had no chip and no
 * filter. A tab that is permanently zero is a decoration; a kind you can
 * receive but cannot filter to is a hole.
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
        // Tips replaced Billing. `GsNotifier.Kind.TIPS` is a real kind with a
        // real switch in Notification settings and a real producer, and it had
        // no chip at all — so a tips notice counted towards All and could
        // never be filtered to. Billing was the opposite: a chip, a count, and
        // no producer that could ever raise it, because this build has no
        // payment processor. One of those two belongs on the strip.
        InboxTab("tips", "Tips", "lightbulb", GsWellTone.Peach),
        InboxTab("messages", "Messages", "message-circle", GsWellTone.Rose),
        InboxTab("updates", "Updates", "sparkles", GsWellTone.Lilac),
    )

    /** The kinds a tab covers; null = every kind (the All tab). */
    fun kindsFor(id: String): Set<String>? = when (id) {
        "messages" -> setOf("messages")
        "updates" -> setOf("product-updates", "ai-updates")
        "account" -> setOf("account-activity")
        "tips" -> setOf("tips")
        else -> null
    }

    val tabKinds = kindsFor(tab)
    val filtered = entries.filter { tabKinds == null || it.kindId in tabKinds }
    val unreadInView = filtered.count { !it.read }
    val unreadAnywhere = entries.count { !it.read }
    val tabLabel = tabs.firstOrNull { it.id == tab }?.label ?: "All"

    val dayStart = remember(now) { startOfToday(now) }
    val today = filtered.filter { it.createdAtMillis >= dayStart }
    val earlier = filtered.filter { it.createdAtMillis < dayStart }

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
            // An empty TAB is not an empty INBOX. The old copy said "Nexly has
            // not sent you a notice yet" while nine message notices sat one
            // chip to the right — the page contradicted its own counts. And a
            // Billing tab said "payments aren't processed here", which is a
            // sentence about a product that has no billing screen at all.
            if (entries.isEmpty()) {
                AllCaughtUpCard(
                    title = "You're all caught up!",
                    body = "Nothing has been sent yet. A notice only lands here after this device actually showed one — a finished reply, a sign-in, a new on-device model.",
                )
            } else {
                AllCaughtUpCard(
                    title = "Nothing in $tabLabel yet",
                    body = if (entries.size == 1) {
                        "You have 1 notice in another kind."
                    } else {
                        "You have ${entries.size} notices in other kinds."
                    },
                    actionLabel = "Show all notifications",
                    onAction = { tab = "all" },
                )
            }
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
                                .clickable(enabled = unreadInView > 0) {
                                    // Scoped to this tab: the control sits in a
                                    // filtered list, so a global sweep cleared
                                    // tabs the reader never opened.
                                    NoticeInbox.markAllRead(context, tabKinds)
                                }
                                .padding(horizontal = 3.dp, vertical = 2.dp),
                        ) {
                            Icon(
                                GsIcons.CheckCircle2,
                                contentDescription = null,
                                tint = if (unreadInView > 0) colors.textSecondary else colors.textTertiary.copy(alpha = 0.5f),
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Mark all as read",
                                fontSize = 10.5.sp,
                                fontFamily = GsFonts.Product,
                                color = if (unreadInView > 0) colors.textSecondary else colors.textTertiary.copy(alpha = 0.5f),
                            )
                        }
                    },
                )
                NoticeList(today, now) { notice -> onNavigate(GsRoutes.notification(notice.id)) }
            }
            if (earlier.isNotEmpty()) {
                val shown = if (earlierExpanded) earlier else earlier.take(3)
                RefGroupHeader(
                    title = "Earlier",
                    caption = if (earlierExpanded) "Show less" else "See all",
                    onCaptionClick = { earlierExpanded = !earlierExpanded },
                )
                NoticeList(shown, now) { notice -> onNavigate(GsRoutes.notification(notice.id)) }
            }
            Spacer(Modifier.height(14.dp))
            // "You're all caught up!" used to sit under a list of unread rows,
            // which made the card a decoration rather than a statement. It now
            // only appears when it is true for what is on screen, and names the
            // unread mail it is not counting when there is some.
            when {
                unreadInView > 0 -> Unit
                unreadAnywhere > 0 -> AllCaughtUpCard(
                    title = "You're all caught up here",
                    body = if (unreadAnywhere == 1) {
                        "1 unread notice is waiting in another kind."
                    } else {
                        "$unreadAnywhere unread notices are waiting in other kinds."
                    },
                    actionLabel = "Show all notifications",
                    onAction = { tab = "all" },
                )
                else -> AllCaughtUpCard(
                    title = "You're all caught up!",
                    body = "No more notifications for now.",
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        // The inbox could not reach its own settings: the only route to them
        // was Settings → Notifications, three taps away from the page whose
        // contents they govern.
        RefGroupHeader("Settings", "Where these come from")
        RefCard(role = GsGlassRole.CardLight) {
            RefNavRow(
                title = "Notification settings",
                subtitle = "Choose which kinds Nexly may send, and when",
                onClick = { onNavigate(GsRoutes.SETTINGS_NOTIFICATIONS) },
                leading = { RefWellTile(GsIcons.Bell, GsWellTone.Blue, 28.dp) },
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showFilter) {
        GsChoiceDialog(
            title = "Filter notifications",
            subtitle = "Every kind Nexly can send, counted from what it actually sent",
            options = tabs.map { item ->
                GsChoice(
                    id = item.id,
                    label = item.label,
                    detail = "${counts[item.id] ?: 0} notices",
                    glyph = item.glyph,
                    tone = item.tone,
                    selected = tab == item.id,
                )
            },
            onDismiss = { showFilter = false },
            onSelect = { choice ->
                tab = choice.id
                showFilter = false
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
            .then(if (selected) Modifier.border(1.dp, GsWellTone.Peach.ink.copy(alpha = 0.45f), shape) else Modifier.argentComponentBorder(shape, Color.White.copy(alpha = 0.55f)))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NexlyGlyph(name = tab.glyph, size = 15.dp, color = tab.tone.ink)
        Spacer(Modifier.height(5.dp))
        Text(
            tab.label,
            fontSize = 10.5.sp,
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
                fontSize = 10.5.sp,
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
    val style = noticeStyleFor(entry.kindId)
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
                    fontSize = 11.sp,
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
                fontSize = 10.5.sp,
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
                relativeNoticeTime(entry.createdAtMillis, now),
                fontSize = 10.5.sp,
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

internal data class NoticeStyle(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tone: GsWellTone,
    val label: String,
)

internal fun noticeStyleFor(kindId: String): NoticeStyle = when (kindId) {
    "messages" -> NoticeStyle(GsIcons.MessageCircle, GsWellTone.Rose, "Messages")
    "ai-updates" -> NoticeStyle(GsIcons.Sparkles, GsWellTone.Lilac, "Update")
    "product-updates" -> NoticeStyle(GsIcons.Cog, GsWellTone.Blue, "Updates")
    "account-activity" -> NoticeStyle(GsIcons.UserRound, GsWellTone.Peach, "Account")
    "tips" -> NoticeStyle(GsIcons.Lightbulb, GsWellTone.Peach, "Tips")
    else -> NoticeStyle(GsIcons.Bell, GsWellTone.Blue, "Update")
}

/** The mock's closing card: dashes, a title, one whisper — and, when the card
 *  is explaining an empty tab, the one action that leaves it. */
@Composable
private fun AllCaughtUpCard(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
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
                    title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = GsFonts.Product,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                CaughtUpRule()
            }
            Spacer(Modifier.height(3.dp))
            Text(
                body,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                fontFamily = GsFonts.Product,
                color = colors.textTertiary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(9.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(GsWellTone.Peach.wash.copy(alpha = 0.55f))
                        .clickable(onClick = onAction)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        actionLabel,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = GsFonts.Product,
                        color = GsWellTone.Peach.ink,
                    )
                }
            }
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
internal fun relativeNoticeTime(then: Long, now: Long): String {
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
