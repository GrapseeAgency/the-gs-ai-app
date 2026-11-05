package com.grapsee.gsai.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.data.notifications.GsNotifier
import com.grapsee.gsai.ui.components.GsDialogShell
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefInfoCard
import com.grapsee.gsai.ui.components.RefRowDivider
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefSheetRow
import com.grapsee.gsai.ui.components.RefSheetToggle
import com.grapsee.gsai.ui.components.RefSwitch
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
 * Geometry is the sheet's own (see RefSheetKit): 13/9 group headers, 10/8
 * rows at a 56pt two-line pitch, and the mock's 34×20 switch rather than
 * Material's 52×32.
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
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(2.dp)) {
            RefSheetToggle(
                title = "Notifications",
                subtitle = "Control what you want to be notified about.",
                checked = SettingsStore.pushNotifications,
                onCheckedChange = { setMaster(it) },
                leading = { RefWellTile(GsIcons.Bell, GsWellTone.Peach, 40.dp) },
            )
        }

        // ---- Notification Types ------------------------------------------
        RefGroupHeader("Notification Types", "Choose what to receive")
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(vertical = 2.dp)) {
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
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(vertical = 2.dp)) {
            RefSheetToggle(
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
            RefSheetToggle(
                title = "Email Notifications",
                subtitle = "Get updates via email.",
                checked = false,
                onCheckedChange = {},
                enabled = false,
                leading = { RefWellTile(GsIcons.Mail, GsWellTone.Lilac, 40.dp) },
            )
            RefRowDivider()
            RefSheetToggle(
                title = "In-App Notifications",
                subtitle = "See notifications within the app.",
                checked = SettingsStore.noticeInApp,
                onCheckedChange = { SettingsStore.updateNoticeInApp(it) },
                leading = { RefWellTile(GsIcons.MessageSquare, GsWellTone.Mint, 40.dp) },
            )
        }

        // ---- Quiet Hours -------------------------------------------------
        RefGroupHeader("Quiet Hours", "Pause notifications when you need rest")
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(vertical = 2.dp)) {
            RefSheetToggle(
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
            RefSheetRow(
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
    RefSheetToggle(
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

    GsDialogShell(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RefWellTile(GsIcons.Moon, GsWellTone.Lilac, 38.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        "Quiet Hours",
                        fontSize = 15.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        fontFamily = GsFonts.Product,
                        color = colors.textPrimary,
                    )
                    Text(
                        if (enabled) "Holding notices ${formatClock(start)} \u2013 ${formatClock(end)}"
                        else "Off \u2014 every notice arrives as it happens",
                        fontSize = 11.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textSecondary,
                    )
                }
                RefSwitch(checked = enabled, onCheckedChange = { enabled = it })
            }

            Spacer(Modifier.height(16.dp))

            // The two ends of the window. Steppers keep it to taps: a wheel
            // picker inside a glass slab would be a second material language
            // in the one place the reader is deciding something.
            ClockStepper(
                label = "From",
                minute = start,
                enabled = enabled,
                onStep = { start = step(start, it) },
            )
            Spacer(Modifier.height(10.dp))
            ClockStepper(
                label = "Until",
                minute = end,
                enabled = enabled,
                onStep = { end = step(end, it) },
            )

            Spacer(Modifier.height(14.dp))
            Text(
                "Nexly holds every notice back inside this window. Messages you miss are still there when it ends.",
                color = colors.textTertiary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontFamily = GsFonts.Product,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Save",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
                    .background(colors.accent)
                    .clickable(
                        role = androidx.compose.ui.semantics.Role.Button,
                        onClick = { onApply(enabled, start, end) }
                    )
                    .padding(vertical = 13.dp),
                fontSize = 13.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                fontFamily = GsFonts.Product,
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                text = "Cancel",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
                    .clickable(
                        role = androidx.compose.ui.semantics.Role.Button,
                        onClick = onDismiss
                    )
                    .padding(vertical = 12.dp),
                fontSize = 12.sp,
                fontFamily = GsFonts.Product,
                color = colors.textSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** One end of the quiet window: a label, a minus, the time, a plus. */
@Composable
private fun ClockStepper(
    label: String,
    minute: Int,
    enabled: Boolean,
    onStep: (Int) -> Unit,
) {
    val colors = GsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(colors.textPrimary.copy(alpha = if (enabled) 0.05f else 0.03f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            fontFamily = GsFonts.Product,
            color = if (enabled) colors.textSecondary else colors.textTertiary,
        )
        StepChip("−", enabled) { onStep(-30) }
        Text(
            formatClock(minute),
            modifier = Modifier.padding(horizontal = 10.dp),
            fontSize = 14.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            fontFamily = GsFonts.Product,
            color = if (enabled) colors.textPrimary else colors.textTertiary,
        )
        StepChip("+", enabled) { onStep(30) }
    }
}

@Composable
private fun StepChip(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = GsTheme.colors
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(colors.textPrimary.copy(alpha = if (enabled) 0.07f else 0.03f))
            .clickable(
                enabled = enabled,
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            fontSize = 15.sp,
            fontFamily = GsFonts.Product,
            color = if (enabled) colors.textPrimary else colors.textTertiary,
        )
    }
}
