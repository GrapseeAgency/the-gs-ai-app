package com.grapsee.gsai.ui.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.notifications.NoticeInbox
import com.grapsee.gsai.ui.components.GsConfirmDialog
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefNavRow
import com.grapsee.gsai.ui.components.RefRowDivider
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefTagPill
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme

/**
 * ONE NOTICE, ON ITS OWN PAGE.
 *
 * The inbox used to open a `material3.AlertDialog` — a centred white rectangle
 * holding a title and a paragraph. That is the wrong object for a notice: a
 * notice has a kind, a time, a full body, and usually somewhere it wants to
 * take you. A dialog can hold none of that, and it also made the inbox the one
 * place in the app where tapping a row did not push a page.
 *
 * This page is the notice's home:
 *  - who sent it (kind well + label) and when, at the top;
 *  - the body at reading size and reading line height, in a card of its own;
 *  - the destination the notice is about, as a real row, so "your model
 *    finished downloading" can take you to the model screen;
 *  - two honest actions: mark unread again, or delete this notice from the
 *    device.
 *
 * Opening the page is still what marks the notice read — the same rule the
 * inbox had, because it is the truth: you have now seen it.
 */
@Composable
fun NotificationDetailScreen(
    noticeId: Long,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
    now: Long = System.currentTimeMillis(),
) {
    val context = LocalContext.current
    val colors = GsTheme.colors

    // `revision` is read so a mark-unread/delete recomposes this page.
    @Suppress("UNUSED_EXPRESSION")
    NoticeInbox.revision

    val notice = remember(NoticeInbox.revision, noticeId) {
        NoticeInbox.read(context).firstOrNull { it.id == noticeId }
    }
    var confirmingDelete by remember { mutableStateOf(false) }

    LaunchedEffect(noticeId) {
        if (notice != null && !notice.read) NoticeInbox.markRead(context, noticeId)
    }

    if (notice == null) {
        // A notice can disappear under the page (deleted here, or trimmed by
        // the ring buffer). Saying so beats an empty screen with a title.
        RefSettingsPage(
            title = "Notification",
            subtitle = "This notice is no longer on this device",
            onBack = onBack,
        ) {
            RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(18.dp)) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RefWellTile(GsIcons.Bell, GsWellTone.Blue, 40.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "It was deleted, or the inbox rotated it out.",
                        fontSize = 12.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textSecondary,
                    )
                    TextButton(onClick = onBack) {
                        Text("Back to notifications", fontFamily = GsFonts.Product, fontSize = 12.sp)
                    }
                }
            }
        }
        return
    }

    val style = noticeStyleFor(notice.kindId)
    val destination = noticeDestinationFor(notice.kindId)

    RefSettingsPage(
        title = "Notification",
        subtitle = relativeNoticeTime(notice.createdAtMillis, now) + " • " + style.label,
        onBack = onBack,
    ) {
        // ---- The notice itself -------------------------------------------
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RefWellTile(style.icon, style.tone, 38.dp)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = notice.title,
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GsFonts.Product,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RefTagPill(style.label, style.tone.ink)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            relativeNoticeTime(notice.createdAtMillis, now),
                            fontSize = 10.5.sp,
                            fontFamily = GsFonts.Product,
                            color = colors.textTertiary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            RefRowDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                text = notice.body,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                fontFamily = GsFonts.Product,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Sent " + absoluteNoticeTime(notice.createdAtMillis),
                fontSize = 10.5.sp,
                fontFamily = GsFonts.Product,
                color = colors.textTertiary,
            )
        }

        // ---- Where this notice points ------------------------------------
        RefGroupHeader("Related", "The screen this notice is about")
        RefCard(role = GsGlassRole.CardLight) {
            RefNavRow(
                title = destination.label,
                subtitle = destination.detail,
                onClick = { onNavigate(destination.route) },
                leading = { RefWellTile(destination.icon, GsWellTone.Blue, 28.dp) },
            )
        }

        // ---- What you can do about it ------------------------------------
        RefGroupHeader("This notice", "Kept on this device only")
        RefCard(role = GsGlassRole.CardLight) {
            RefNavRow(
                title = "Mark as unread",
                subtitle = "Puts the dot back in the inbox",
                onClick = {
                    NoticeInbox.markUnread(context, notice.id)
                    onBack()
                },
                leading = { RefWellTile(GsIcons.Bell, GsWellTone.Peach, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "Delete this notification",
                subtitle = "Removes it from the inbox on this device",
                onClick = { confirmingDelete = true },
                leading = { RefWellTile(GsIcons.Trash, GsWellTone.Rose, 28.dp) },
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Nexly only writes a notice here after the system has actually posted it, so this list cannot contain a message you were never sent.",
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
            fontFamily = GsFonts.Product,
            color = colors.textTertiary,
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .testTag("notice-detail-footnote"),
        )
        Spacer(Modifier.height(16.dp))
    }

    if (confirmingDelete) {
        GsConfirmDialog(
            title = "Delete this notification?",
            message = "It disappears from the inbox on this device. Nexly cannot send it again — the notice was a one-time message.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                NoticeInbox.delete(context, notice.id)
                onBack()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

private data class NoticeDestination(
    val label: String,
    val detail: String,
    val route: String,
    val icon: ImageVector,
)

/** Where a notice of this kind wants to take the reader. */
private fun noticeDestinationFor(kindId: String): NoticeDestination = when (kindId) {
    "messages" -> NoticeDestination(
        "Open your chats",
        "The conversation this message was about",
        GsRoutes.CHATS,
        GsIcons.MessageCircle,
    )
    "account-activity" -> NoticeDestination(
        "Account & Security",
        "Sessions, biometrics and the address on this install",
        GsRoutes.SETTINGS_ACCOUNT,
        GsIcons.UserRound,
    )
    "product-updates", "ai-updates" -> NoticeDestination(
        "About Nexly",
        "Version, build and what changed",
        GsRoutes.SETTINGS_ABOUT,
        GsIcons.Sparkles,
    )
    "tips" -> NoticeDestination(
        "Help & Support",
        "Guides for what this build can do",
        GsRoutes.SETTINGS_HELP,
        GsIcons.Lightbulb,
    )
    else -> NoticeDestination(
        "Notification settings",
        "Choose what Nexly is allowed to tell you",
        GsRoutes.SETTINGS_NOTIFICATIONS,
        GsIcons.Bell,
    )
}

private fun absoluteNoticeTime(millis: Long): String =
    java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(millis))
