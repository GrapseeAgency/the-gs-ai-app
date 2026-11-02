package com.grapsee.gsai.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefNavRow
import com.grapsee.gsai.ui.components.RefRowDivider
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefSheetPillButton
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.icons.GsIcons

/**
 * The three pages the Privacy sheet used to promise but not reach.
 *
 * Each one exists because the row above it was a dead end: Search History said
 * "searches are not recorded" while nothing recorded them, Personalization &
 * Ads had no chevron because there was nothing to open, and App Permissions
 * only bounced to the OS app-info page. The reference art draws all three as
 * real destinations, so here they are — with the truth behind them, not the
 * mock's invented camera and location grants.
 */

// ---------------------------------------------------------------------------
// Search History
// ---------------------------------------------------------------------------

@Composable
fun SearchHistoryScreen(
    onBack: () -> Unit,
    onRunSearch: (String) -> Unit = {},
) {
    val entries = SettingsStore.searchHistory
    var confirmClear by remember { mutableStateOf(false) }

    RefSettingsPage(
        title = "Search History",
        subtitle = "Searches you have run in Chat History",
        onBack = onBack,
        headerTrailing = if (entries.isEmpty()) null else {
            {
                RefSheetPillButton(
                    label = if (confirmClear) "Tap again" else "Clear",
                    onClick = {
                        if (confirmClear) {
                            SettingsStore.clearSearchHistory()
                            confirmClear = false
                        } else {
                            confirmClear = true
                        }
                    },
                )
            }
        },
    ) {
        RefGroupHeader(
            "On this device",
            if (entries.isEmpty()) "Nothing recorded yet" else "${entries.size} ${if (entries.size == 1) "search" else "searches"}",
        )
        RefCard(role = GsGlassRole.CardLight) {
            if (entries.isEmpty()) {
                RefNavRow(
                    title = "No searches yet",
                    subtitle = "Queries you submit from Chat History & Search are listed here, and only here.",
                    onClick = null,
                    trailing = {},
                    leading = { RefWellTile(GsIcons.Search, GsWellTone.Lilac, 28.dp) },
                )
            } else {
                entries.forEachIndexed { index, entry ->
                    if (index > 0) RefRowDivider()
                    RefNavRow(
                        title = entry.query,
                        subtitle = relativeWhen(entry.atMillis),
                        onClick = { onRunSearch(entry.query) },
                        leading = { RefWellTile(GsIcons.Search, GsWellTone.Lilac, 28.dp) },
                        trailing = {
                            RefSheetPillButton(
                                label = "Remove",
                                onClick = { SettingsStore.removeSearch(entry.query) },
                            )
                        },
                    )
                }
            }
        }

        RefGroupHeader("What this is", "The log the Privacy row refers to")
        RefCard(role = GsGlassRole.CardLight) {
            RefNavRow(
                title = "Local only",
                subtitle = "The queries stay in this app's storage. They are never uploaded and never used to build an ad profile.",
                onClick = null,
                trailing = {},
                leading = { RefWellTile(GsIcons.Lock, GsWellTone.Mint, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "Clearing is immediate",
                subtitle = "Clear removes every entry above; the conversations themselves are untouched.",
                onClick = null,
                trailing = {},
                leading = { RefWellTile(GsIcons.Trash2, GsWellTone.Rose, 28.dp) },
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ---------------------------------------------------------------------------
// Personalization & Ads
// ---------------------------------------------------------------------------

@Composable
fun PersonalizationScreen(onBack: () -> Unit) {
    RefSettingsPage(
        title = "Personalization & Ads",
        subtitle = "What Nexly does and does not build from your activity",
        onBack = onBack,
    ) {
        RefGroupHeader("Personalization", "What shapes your answers")
        RefCard(role = GsGlassRole.CardLight) {
            RefNavRow(
                title = "Your conversations",
                subtitle = "The current chat and its recent history are sent to the model so it can answer. Nothing older is attached.",
                onClick = null,
                trailing = {},
                leading = { RefWellTile(GsIcons.MessageCircle, GsWellTone.Peach, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "Saved memory",
                subtitle = if (SettingsStore.memory) {
                    "On — memories you save are added to future conversations. Turn it off in Settings → Memory."
                } else {
                    "Off — no saved memory is added to your conversations."
                },
                onClick = null,
                trailing = {},
                leading = { RefWellTile(GsIcons.Sparkles, GsWellTone.Lilac, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "No ad profile",
                subtitle = "There is no advertising SDK in this build and no profile is assembled from what you type.",
                onClick = null,
                trailing = {},
                leading = { RefWellTile(GsIcons.Shield, GsWellTone.Mint, 28.dp) },
            )
        }

        RefGroupHeader("Advertising", "There is nothing to opt out of")
        RefCard(role = GsGlassRole.CardLight) {
            RefNavRow(
                title = "No third-party ads",
                subtitle = "Nexly does not show ads and does not share your activity with ad networks.",
                onClick = null,
                trailing = {},
                leading = { RefWellTile(GsIcons.Camera, GsWellTone.Blue, 28.dp) },
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ---------------------------------------------------------------------------
// App Permissions
// ---------------------------------------------------------------------------

private data class PermissionRow(
    val title: String,
    val detail: String,
    val granted: Boolean,
    val open: () -> Unit,
)

@Composable
fun AppPermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current

    // Permission state changes outside the app (the user flips it in system
    // settings and comes back), so the list is rebuilt on every resume rather
    // than cached at composition time.
    var refresh by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }

    val rows = remember(refresh) { permissionRows(context) }

    RefSettingsPage(
        title = "App Permissions",
        subtitle = "What this build can actually access",
        onBack = onBack,
    ) {
        RefGroupHeader("Granted on this device", "Tap a row to change it in system settings")
        RefCard(role = GsGlassRole.CardLight) {
            rows.forEachIndexed { index, row ->
                if (index > 0) RefRowDivider()
                RefNavRow(
                    title = row.title,
                    subtitle = "${if (row.granted) "Allowed" else "Not allowed"} — ${row.detail}",
                    onClick = row.open,
                    leading = {
                        RefWellTile(
                            when (index) {
                                0 -> GsIcons.Mic
                                1 -> GsIcons.BellRing
                                2 -> GsIcons.Clock3
                                else -> GsIcons.Fingerprint
                            },
                            when (index) {
                                0 -> GsWellTone.Peach
                                1 -> GsWellTone.Lilac
                                2 -> GsWellTone.Blue
                                else -> GsWellTone.Mint
                            },
                            28.dp,
                        )
                    },
                )
            }
        }

        RefGroupHeader("Not requested", "The reference art lists these; this build does not")
        RefCard(role = GsGlassRole.CardLight) {
            RefNavRow(
                title = "Camera and location",
                subtitle = "Neither is in the manifest, because no feature uses them. Asking for access the app cannot use is how a permission prompt stops meaning anything.",
                onClick = null,
                trailing = {},
                leading = { RefWellTile(GsIcons.Camera, GsWellTone.Rose, 28.dp) },
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun permissionRows(context: Context): List<PermissionRow> {
    val appSettings = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )
    val openAppSettings: () -> Unit = {
        runCatching { context.startActivity(appSettings) }
        Unit
    }

    val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    val notifications = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val dnd = runCatching {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
            .isNotificationPolicyAccessGranted
    }.getOrDefault(false)
    // The same predicate the app lock uses to decide whether the switch can
    // be armed at all — one definition, so the row and the lock cannot disagree.
    val biometric = com.grapsee.gsai.ui.components.canUseBiometrics(context)

    return listOf(
        PermissionRow(
            "Microphone",
            "used for voice input",
            mic,
            openAppSettings,
        ),
        PermissionRow(
            "Notifications",
            "messages, reminders and account notices",
            notifications,
            openAppSettings,
        ),
        PermissionRow(
            "Do Not Disturb access",
            "lets Quiet Hours hold notifications",
            dnd,
            {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
                Unit
            },
        ),
        PermissionRow(
            "Biometric unlock",
            "used only when the app lock is on",
            biometric,
            openAppSettings,
        ),
    )
}

private fun relativeWhen(atMillis: Long): String {
    if (atMillis <= 0L) return "Earlier"
    val delta = System.currentTimeMillis() - atMillis
    val minutes = delta / 60_000
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 60 * 24 -> "${minutes / 60} h ago"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)} d ago"
        else -> java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(atMillis))
    }
}
