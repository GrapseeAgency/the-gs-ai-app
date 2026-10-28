package com.grapsee.gsai.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.data.notifications.GsNotifier
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefInfoCard
import com.grapsee.gsai.ui.components.RefNavRow
import com.grapsee.gsai.ui.components.RefRowDivider
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefToggleRow
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme

/**
 * Notifications & Alerts — docs/reference/pending/notifications-alerts.png.
 *
 * Every group, row, well tone, caption and the closing card are the mock's, in
 * the mock's order. What the mock cannot tell you is which of its switches the
 * app actually obeys, so the rule here is: a switch is drawn live only where
 * flipping it changes what the device receives, and the state it shows is read
 * from the system or from a preference `GsNotifier` consults on the way to
 * every `notify()` — never a remembered value nothing reads.
 *
 * Two rows are honest exceptions rather than live controls:
 *  - Email Notifications, drawn disabled and off because this build sends no
 *    mail at all;
 *  - the AI Updates / Tips & Suggestions / Account Activity switches, which
 *    gate a real channel and a real `allows()` check but have no producer yet,
 *    so there is nothing for them to suppress or release today.
 */
@Composable
fun NotificationsAlertsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // The OS-owned states are mirrored into snapshot state. `NotificationManager`
    // is not observable, so reading it inline produced a Switch that could never
    // recompose: tapping DND on succeeded on the device while the row stayed
    // off, and every later tap then computed `want == true` again — making the
    // off path unreachable from this screen.
    //
    // They are re-read in the launcher callbacks and after each successful
    // write, which covers every transition this screen can cause. A change made
    // elsewhere (the shade, system settings) is picked up on the next visit,
    // because `remember` re-seeds when the route is re-entered.
    var dndOn by remember { mutableStateOf(GsNotifier.dndActive(context)) }
    var systemOn by remember { mutableStateOf(GsNotifier.systemEnabled(context)) }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        SettingsStore.updatePushNotifications(granted)
        systemOn = GsNotifier.systemEnabled(context)
    }

    val systemNoticeSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        systemOn = GsNotifier.systemEnabled(context)
    }

    val dndAccessRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Coming back from the policy-access screen, try the toggle the user
        // actually asked for rather than leaving the row where it started.
        dndOn = GsNotifier.dndActive(context)
    }

    var editQuietHours by remember { mutableStateOf(false) }

    /**
     * Turning the master switch on has to end in a state where a notice can
     * actually land, so it asks Android for the runtime permission rather than
     * storing a promise. Denied means off.
     */
    fun setMaster(on: Boolean) {
        when {
            !on -> SettingsStore.updatePushNotifications(false)
            GsNotifier.hasPermission(context) -> SettingsStore.updatePushNotifications(true)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> SettingsStore.updatePushNotifications(true)
        }
    }

    RefSettingsPage(
        title = "Notifications & Alerts",
        subtitle = "Stay updated with what matters",
        onBack = onBack,
    ) {
        // ---- Master ------------------------------------------------------
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(14.dp)) {
            RefToggleRow(
                title = "Notifications",
                subtitle = "Control what you want to be notified about.",
                checked = SettingsStore.pushNotifications,
                onCheckedChange = { setMaster(it) },
                leading = { RefWellTile(GsIcons.Bell, GsWellTone.Peach, 40.dp) },
            )
        }

        // ---- Notification Types ------------------------------------------
        RefGroupHeader("Notification Types", "Choose what to receive")
        RefCard(role = GsGlassRole.CardLight) {
            TypeToggle(
                kindId = "messages",
                icon = GsIcons.MessageCircle,
                tone = GsWellTone.Rose,
                title = "New Messages",
                subtitle = "Get notified when you receive a new message or response from Nexly.",
            )
            RefRowDivider()
            TypeToggle(
                kindId = "ai-updates",
                icon = GsIcons.Sparkles,
                tone = GsWellTone.Lilac,
                title = "AI Updates",
                subtitle = "Be the first to know about new features, model updates and improvements.",
            )
            RefRowDivider()
            TypeToggle(
                kindId = "tips",
                icon = GsIcons.Lightbulb,
                tone = GsWellTone.Peach,
                title = "Tips & Suggestions",
                subtitle = "Receive helpful tips, shortcuts and personalized suggestions to get more from Nexly.",
            )
            RefRowDivider()
            TypeToggle(
                kindId = "account-activity",
                icon = GsIcons.ShieldCheck,
                tone = GsWellTone.Mint,
                title = "Account Activity",
                subtitle = "Get alerts about sign-ins, security changes and important account events.",
            )
            RefRowDivider()
            TypeToggle(
                kindId = "product-updates",
                icon = GsIcons.Star,
                tone = GsWellTone.Lilac,
                title = "Product Updates",
                subtitle = "Stay informed about new tools, features and exclusive releases.",
            )
        }

        // ---- Notification Channels ---------------------------------------
        RefGroupHeader("Notification Channels", "How you want to be notified")
        RefCard(role = GsGlassRole.CardLight) {
            RefToggleRow(
                title = "Push Notifications",
                subtitle = "Receive notifications on your device.",
                checked = SettingsStore.pushNotifications && systemOn,
                onCheckedChange = { want ->
                    if (!want) {
                        SettingsStore.updatePushNotifications(false)
                    } else if (systemOn) {
                        setMaster(true)
                    } else {
                        // The OS-level block lives in system settings, not in
                        // this app, so the switch sends the user there instead
                        // of pretending it cleared the block itself.
                        systemNoticeSettings.launch(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    }
                },
                leading = { RefWellTile(GsIcons.Bell, GsWellTone.Peach, 40.dp) },
            )
            RefRowDivider()
            // The one row on this screen with no delivery path behind it: Nexly
            // sends no email from this build. It is drawn disabled and off — the
            // truth — rather than as a live-looking switch, which is the exact
            // dead toggle the SettingsStore HONESTY CONTRACT exists to catch.
            RefToggleRow(
                title = "Email Notifications",
                subtitle = "Get updates via email.",
                checked = false,
                onCheckedChange = {},
                enabled = false,
                leading = { RefWellTile(GsIcons.Mail, GsWellTone.Lilac, 40.dp) },
            )
            RefRowDivider()
            RefToggleRow(
                title = "In-App Notifications",
                subtitle = "See notifications within the app.",
                checked = SettingsStore.noticeInApp,
                onCheckedChange = { SettingsStore.updateNoticeInApp(it) },
                leading = { RefWellTile(GsIcons.MessageSquare, GsWellTone.Mint, 40.dp) },
            )
        }

        // ---- Quiet Hours -------------------------------------------------
        RefGroupHeader("Quiet Hours", "Pause notifications when you need rest")
        RefCard(role = GsGlassRole.CardLight) {
            RefToggleRow(
                title = "Do Not Disturb",
                subtitle = "Silence all notifications during selected hours.",
                // Reads the device's real interruption filter, mirrored into
                // snapshot state so the row can actually follow the switch.
                checked = dndOn,
                onCheckedChange = { want ->
                    if (GsNotifier.setDnd(context, want)) {
                        dndOn = GsNotifier.dndActive(context)
                    } else {
                        // Without policy access the filter is not ours to set.
                        // Ask for it and re-read on the way back rather than
                        // flipping a switch that would lie about the outcome.
                        runCatching { dndAccessRequest.launch(GsNotifier.dndAccessIntent()) }
                    }
                },
                leading = { RefWellTile(GsIcons.MoonStar, GsWellTone.Lilac, 40.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "Quiet Hours",
                subtitle = formatWindow(SettingsStore.quietStartMinute, SettingsStore.quietEndMinute),
                onClick = { editQuietHours = true },
                leading = { RefWellTile(GsIcons.Clock, GsWellTone.Rose, 40.dp) },
            )
        }

        // ---- Closing card ------------------------------------------------
        Spacer(Modifier.height(14.dp))
        RefInfoCard(
            text = "You're in control. Notifications help you stay connected, without getting in the way.",
            trailing = {
                Image(
                    painter = painterResource(com.grapsee.gsai.R.drawable.gs_home_lotus_new),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape),
                )
            },
        )
        Spacer(Modifier.height(10.dp))
    }

    if (editQuietHours) {
        QuietHoursDialog(
            onDismiss = { editQuietHours = false },
            onApply = { isEnabled, startMinute, endMinute ->
                SettingsStore.updateQuietWindow(startMinute, endMinute)
                SettingsStore.updateQuietHoursEnabled(isEnabled)
                editQuietHours = false
            },
        )
    }
}

/** One "Notification Types" row: the mock's well tile plus the switch that
 *  gates that kind inside `GsNotifier.allows`. */
@Composable
private fun TypeToggle(
    kindId: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tone: GsWellTone,
    title: String,
    subtitle: String,
) {
    RefToggleRow(
        title = title,
        subtitle = subtitle,
        checked = SettingsStore.noticeEnabled(kindId),
        onCheckedChange = { SettingsStore.updateNoticeEnabled(kindId, it) },
        leading = { RefWellTile(icon, tone, 40.dp) },
    )
}

/** The mock's "10:00 PM – 7:00 AM" line, built from the stored window. */
internal fun formatWindow(startMinute: Int, endMinute: Int): String =
    "${formatClock(startMinute)} – ${formatClock(endMinute)}"

private fun formatClock(minuteOfDay: Int): String {
    val hour = (minuteOfDay / 60) % 24
    val minute = minuteOfDay % 60
    val suffix = if (hour < 12) "AM" else "PM"
    val h12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return if (minute == 0) "$h12:00 $suffix" else String.format("%d:%02d %s", h12, minute, suffix)
}

/**
 * Picks the quiet window. Steppers rather than a Material time picker: the
 * build has no `TimePicker` usage to match against, and a wrong API level here
 * costs a CI cycle to discover.
 */
@Composable
private fun QuietHoursDialog(
    onDismiss: () -> Unit,
    onApply: (enabled: Boolean, startMinute: Int, endMinute: Int) -> Unit,
) {
    // The enable switch lives in here rather than on the screen: the reference
    // draws this row as a plain window editor with a chevron. An earlier
    // revision only ever wrote `true`, so quiet hours could be turned on and
    // never back off.
    var enabled by remember { mutableStateOf(SettingsStore.quietHoursEnabled) }
    var start by remember { mutableStateOf(SettingsStore.quietStartMinute) }
    var end by remember { mutableStateOf(SettingsStore.quietEndMinute) }
    val colors = GsTheme.colors

    fun step(minute: Int, delta: Int): Int {
        val next = minute + delta
        return ((next % 1440) + 1440) % 1440
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quiet Hours") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Quiet hours", color = colors.textSecondary, fontSize = 13.sp)
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = colors.accent,
                            checkedBorderColor = Color.Transparent,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = colors.border,
                            uncheckedBorderColor = Color.Transparent,
                        ),
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("From", color = colors.textSecondary, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { start = step(start, -30) }) { Text("–") }
                        Text(formatClock(start), color = colors.textPrimary, fontSize = 14.sp)
                        TextButton(onClick = { start = step(start, 30) }) { Text("+") }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Until", color = colors.textSecondary, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { end = step(end, -30) }) { Text("–") }
                        Text(formatClock(end), color = colors.textPrimary, fontSize = 14.sp)
                        TextButton(onClick = { end = step(end, 30) }) { Text("+") }
                    }
                }
                Text(
                    "Nexly holds every notice back inside this window. Messages you miss are still there when it ends.",
                    color = colors.textTertiary,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    fontFamily = GsFonts.Product,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(enabled, start, end) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
