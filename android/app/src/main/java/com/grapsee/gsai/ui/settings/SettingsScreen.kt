package com.grapsee.gsai.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.gsContentWidth
import com.grapsee.gsai.BuildConfig
import com.grapsee.gsai.CrashReporter
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.data.notifications.ReplyNotifier
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.kineticPress
import com.grapsee.gsai.ui.theme.gsHaptic
import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import java.time.OffsetDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.grapsee.gsai.data.local.ModelCatalog
import com.grapsee.gsai.data.local.ModelStore

private val themeOptions = listOf("Light", "Dark", "System")

/**
 * DESIGN 3 IDENTITY settings. The nine bordered cards are gone: two flat
 * grouped containers (16dp radius, surfaceContainer fill, no border, no
 * shadow) under micro-labels — PREFERENCES (Appearance, AI, Language,
 * Accessibility) and SYSTEM (Privacy, Notifications, About).
 * Rows: min 56dp, 16dp inner padding, the 40dp/12dp row glyph container,
 * 16sp/500 titles over 14sp supporting lines, hairline separators indented
 * to the row text, trailing chevron. In-place expansion is unchanged
 * (expandedId + BackHandler), animated via animateContentSize unless the
 * user asked the system to stand still.
 *
 * The 0.77.7 honesty sweep: the Chat and Security groups are gone — every
 * switch in them persisted but was read by nothing. A group that expands to
 * nothing teaches the user the settings are decoration, so it left too.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
    /**
     * Commit 1(b): the user tapped "Prefer on-device AI" with no model
     * installed. Defaulted so GsNavHost's existing call site is untouched.
     * Shows the consent dialog over this screen rather than navigating
     * anywhere, because there is no settings route for the downloader.
     */
    onLocalAiRequested: () -> Unit = {},
) {
    var expandedId by remember { mutableStateOf<String?>(null) }
    // Privacy flows (UI-local)
    var showClearData by remember { mutableStateOf(false) }
    var showDeleteAccount by remember { mutableStateOf(false) }
    var showDeleteModel by remember { mutableStateOf(false) }

    // Reply notifications: the switch is the CONSENT surface for the runtime
    // POST_NOTIFICATIONS prompt (API 33+). Turning the switch ON asks; a
    // refused prompt leaves the switch OFF — never a toggle that lies about
    // what the system will actually deliver.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        SettingsStore.updatePushNotifications(granted)
    }

    // An expanded section is an in-screen overlay state: BACK folds it first
    // and only pops the screen once every section is closed.
    BackHandler(enabled = expandedId != null) { expandedId = null }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportData = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch { exportAllData(context, uri) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .fillMaxWidth()
            .gsContentWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFFFFB36D).copy(alpha = .18f), MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = GsMotion.spaceM)
    ) {
        // Back row — same affordance the shared scaffold draws, kept so the
        // large title below can own its own ramp.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = GsMotion.spaceM),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    GsIcons.ChevronLeft,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GsMotion.spaceM)
                .padding(bottom = GsMotion.spaceL)
        ) {
            // Large title — the ramp's top step: 34sp bold, zero tracking,
            // onBackground, straight on the background.
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 34.sp,
                    lineHeight = 41.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            GroupMicroLabel("Preferences")
            GroupContainer {
                ExpandRow(
                    id = "appearance",
                    icon = GsIcons.Palette,
                    title = "Appearance",
                    expandedId = expandedId,
                    onToggle = { expandedId = if (expandedId == "appearance") null else "appearance" }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        ) {}
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Accent",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                // The truth since the monochrome pass: there is
                                // no hue to name.
                                text = "Monochrome",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    letterSpacing = 0.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    ChipRow(options = themeOptions, selected = SettingsStore.themeMode) { SettingsStore.updateThemeMode(it) }
                    SwitchRow(
                        title = "Reduce animations",
                        subtitle = "Skip decorative motion for a faster feel",
                        checked = SettingsStore.reduceAnimations,
                        onCheckedChange = { SettingsStore.updateReduceAnimations(it) }
                    )
                }
                GroupSeparator()
                ExpandRow(
                    id = "ai",
                    icon = GsIcons.Cpu,
                    title = "AI",
                    expandedId = expandedId,
                    onToggle = { expandedId = if (expandedId == "ai") null else "ai" }
                ) {
                    // Commit 1(b) of the mobile wiring. One row in the existing AI
                    // section, using the existing SwitchRow: no new screen, no new
                    // card, no new token. Default OFF, and the subtitle says what it
                    // will and will not do rather than implying local is always
                    // better.
                    SwitchRow(
                        title = "Prefer on-device AI",
                        subtitle = if (ModelStore.installedPath == null) {
                            "Needs a model first, tap to download"
                        } else {
                            "Answer on this device before using the network"
                        },
                        checked = SettingsStore.preferLocal,
                        onCheckedChange = { onCheckedChange ->
                            if (!ModelStore.installedPath.isNullOrEmpty()) {
                                SettingsStore.updatePreferLocal(onCheckedChange)
                            } else {
                                // Turning it on with no model would silently do
                                // nothing, which is worse than not offering it.
                                SettingsStore.updatePreferLocal(false)
                                onLocalAiRequested()
                            }
                        }
                    )
                    // Installed-model management. The download dialog's final
                    // screen promises "You can delete it any time in Settings" —
                    // this row is that promise made true: before it existed,
                    // ModelStore.removeInstalled() had no UI caller and the
                    // 1.1 GB could never be freed from inside the app.
                    val installedPath = ModelStore.installedPath
                    if (installedPath != null) {
                        ActionRow(
                            title = "Delete on-device model",
                            subtitle = runCatching {
                                "${installedModelLabel()} · " +
                                    "${fmtFileSize(java.io.File(installedPath).length())} on this device"
                            }.getOrElse { "Free the space used by the on-device model" },
                            leading = GsIcons.Trash2,
                            onClick = { showDeleteModel = true }
                        )
                    }
                    // "Reasoning effort" (Low/Medium/High) deleted: it was a
                    // parameter dial wired to nothing — no send path, model or
                    // prompt ever read it. Same for the Memory and
                    // Personalisation rows that sat above it. SettingsStore no
                    // longer carries their keys at all.
                }
                GroupSeparator()
                ExpandRow(
                    id = "language",
                    icon = GsIcons.Languages,
                    title = "Language",
                    expandedId = expandedId,
                    onToggle = { expandedId = if (expandedId == "language") null else "language" }
                ) {
                    ValueRow(title = "App language", value = "English (UK)")
                    // Voice language shows the language the on-device recogniser
                    // ACTUALLY uses — it follows the device locale, so the row
                    // reports the truth instead of a hardcoded claim. (The old
                    // "AI language" chip row is gone: nothing on the wire read
                    // that choice — the translate path follows the device
                    // language too, not a setting.)
                    ValueRow(
                        title = "Voice language",
                        value = java.util.Locale.getDefault().displayLanguage
                            .ifBlank { "English" }
                    )
                }
                GroupSeparator()
                ExpandRow(
                    id = "accessibility",
                    icon = GsIcons.Accessibility,
                    title = "Accessibility",
                    expandedId = expandedId,
                    onToggle = { expandedId = if (expandedId == "accessibility") null else "accessibility" }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Font scale",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${(SettingsStore.fontScale * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    letterSpacing = 0.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Slider(
                            value = SettingsStore.fontScale,
                            onValueChange = { SettingsStore.stageFontScale(it) },
                            onValueChangeFinished = {
                                SettingsStore.updateFontScale(SettingsStore.fontScale)
                            },
                            valueRange = 0.8f..1.4f,
                            steps = 5
                        )
                        Text(
                            text = "Aa. How your chats will read",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = (15 * SettingsStore.fontScale).sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    SwitchRow(
                        title = "High contrast",
                        subtitle = "Stronger text and border contrast",
                        checked = SettingsStore.highContrast,
                        onCheckedChange = { SettingsStore.updateHighContrast(it) }
                    )
                    SwitchRow(
                        title = "Reduce motion",
                        subtitle = "Press scale, aurora and pulsing become static",
                        checked = SettingsStore.reduceMotion,
                        onCheckedChange = { SettingsStore.updateReduceMotion(it) }
                    )
                    SwitchRow(
                        title = "Screen reader hints",
                        subtitle = "Extra spoken labels on core controls",
                        checked = SettingsStore.screenReaderHints,
                        onCheckedChange = { SettingsStore.updateScreenReaderHints(it) }
                    )
                    SwitchRow(
                        title = "Haptics",
                        subtitle = "Vibration feedback for taps and alerts",
                        checked = SettingsStore.haptics,
                        onCheckedChange = { SettingsStore.updateHaptics(it) }
                    )
                }
            }

            GroupMicroLabel("System")
            GroupContainer {
                ExpandRow(
                    id = "privacy",
                    icon = GsIcons.Shield,
                    title = "Privacy",
                    expandedId = expandedId,
                    onToggle = { expandedId = if (expandedId == "privacy") null else "privacy" }
                ) {
                    ActionRow(
                        title = "Export data",
                        subtitle = "Download a copy of your data",
                        leading = GsIcons.Download,
                        onClick = { exportData.launch("gs-ai-export.json") }
                    )
                    ActionRow(
                        title = "Clear local data",
                        subtitle = "Remove cached chats and files on this device",
                        leading = GsIcons.Trash2,
                        onClick = { showClearData = true }
                    )
                    if (CrashReporter.lastReport(context) != null) {
                        ActionRow(
                            title = "Send crash report",
                            subtitle = "The last launch captured an error, share its details",
                            leading = GsIcons.CircleAlert,
                            onClick = {
                                val report = CrashReporter.lastReport(context) ?: return@ActionRow
                                runCatching {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "GS AI App crash report")
                                        putExtra(Intent.EXTRA_TEXT, report.take(80_000))
                                    }
                                    context.startActivity(Intent.createChooser(send, "Send crash report"))
                                    CrashReporter.clear(context)
                                }
                            }
                        )
                    }
                    TextButton(
                        onClick = { showDeleteAccount = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete account")
                    }
                }
                GroupSeparator()
                ExpandRow(
                    id = "notifications",
                    icon = GsIcons.Bell,
                    title = "Notifications",
                    expandedId = expandedId,
                    onToggle = { expandedId = if (expandedId == "notifications") null else "notifications" }
                ) {
                    SwitchRow(
                        title = "Reply notifications",
                        subtitle = "One notice when a reply finishes while GS is in the background",
                        checked = SettingsStore.pushNotifications,
                        onCheckedChange = { want ->
                            if (!want) {
                                SettingsStore.updatePushNotifications(false)
                            } else if (ReplyNotifier.hasPermission(context)) {
                                SettingsStore.updatePushNotifications(true)
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                }
                GroupSeparator()
                ExpandRow(
                    id = "about",
                    icon = GsIcons.Info,
                    title = "About",
                    expandedId = expandedId,
                    onToggle = { expandedId = if (expandedId == "about") null else "about" }
                ) {
                    ValueRow(title = "Version", value = BuildConfig.VERSION_NAME)
                    ValueRow(title = "Build", value = BuildConfig.VERSION_CODE.toString())
                    // TRUE since 0.77.8, and precise about what "automatic"
                    // means: the manifest check runs on every foreground
                    // (MainActivity ON_START → LiveUpdater.syncFrom), and the
                    // install is one tap on the Chats hub pill — Android never
                    // allows a silent APK swap, so the row does not claim one.
                    ValueRow(
                        title = "Updates",
                        value = "Automatic checks · install on tap"
                    )
                }
            }
        }
    }

    if (showClearData) {
        val view = LocalView.current
        AlertDialog(
            onDismissRequest = { showClearData = false },
            title = { Text("Clear local data?") },
            text = {
                Text("Cached chats, files and preferences on this device will be removed. Anything synced to your account stays safe.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Destructive confirmations get the heavy long-press
                        // haptic the system uses for delete moments.
                        view.gsHaptic(HapticFeedbackConstants.LONG_PRESS)
                        showClearData = false
                        scope.launch {
                            runCatching {
                                val db = ServiceLocator.db
                                db.messageDao().deleteAll()
                                db.savedItemDao().clear()
                                db.conversationDao().clear()
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearData = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteAccount) {
        val view = LocalView.current
        AlertDialog(
            onDismissRequest = { showDeleteAccount = false },
            title = { Text("Delete account?") },
            text = {
                Text("This permanently deletes your account, assistants, chats and files. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        view.gsHaptic(HapticFeedbackConstants.LONG_PRESS)
                        showDeleteAccount = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccount = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteModel) {
        val view = LocalView.current
        AlertDialog(
            onDismissRequest = { showDeleteModel = false },
            title = { Text("Delete on-device model?") },
            text = {
                Text(
                    "The model file is removed from this device and answers go back to " +
                        "the network. You can download it again at any time. " +
                        "\"Prefer on-device AI\" turns off with it."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        view.gsHaptic(HapticFeedbackConstants.LONG_PRESS)
                        runCatching { ModelStore.removeInstalled() }
                        // A preference for an engine that no longer exists is a
                        // lie the switch would tell on its next read.
                        SettingsStore.updatePreferLocal(false)
                        showDeleteModel = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteModel = false }) { Text("Keep") }
            }
        )
    }
}

/** Same shape as the consent dialog's formatter, kept local to this file. */
private fun fmtFileSize(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> String.format("%.1f GB", bytes / 1e9)
    bytes >= 1_000_000L -> String.format("%.0f MB", bytes / 1e6)
    bytes >= 1_000L -> String.format("%.0f KB", bytes / 1e3)
    else -> "$bytes B"
}

/**
 * The installed model's display name, resolved across the whole catalog
 * (tier entries AND the quant sweep — a sweep file is not reachable through
 * modelFor(tier), and falling back to the raw id would show the user
 * "qwen2.5-0.5b-instruct-q4_0").
 */
private fun installedModelLabel(): String {
    val id = ModelStore.installedModelId ?: return "On-device model"
    val catalog = listOf(ModelCatalog.MODEL_1_5B, ModelCatalog.MODEL_0_5B) +
        ModelCatalog.QUANT_SWEEP
    return catalog.firstOrNull { it.id == id }?.displayName ?: id
}

/**
 * One expandable row inside a flat grouped container: min 56dp, 16dp inner
 * padding, the 40dp/12dp row glyph container (surfaceContainerHighest,
 * 24dp ink-tinted stroke icon), a 16sp/500 title, and the trailing chevron
 * that swaps with the state. Expansion animates via animateContentSize
 * unless the user asked the system to stand still, in which case it snaps
 * like a disclose.
 */
@Composable
private fun ExpandRow(
    id: String,
    icon: ImageVector,
    title: String,
    expandedId: String?,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val expanded = expandedId == id
    val rowInteraction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Size change animates — unless the user asked the system to
            // stand still, in which case expansion snaps like a disclose.
            .let {
                if (SettingsStore.reduceAnimations || SettingsStore.reduceMotion) it
                else it.animateContentSize()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .kineticPress(rowInteraction)
                .clickable(
                    interactionSource = rowInteraction,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClickLabel = if (expanded) "Collapse" else "Expand",
                    onClick = onToggle
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) GsIcons.ChevronUp else GsIcons.ChevronDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS),
                content = content
            )
        }
    }
}

/**
 * One toggle row: the WHOLE row is the switch (toggleable role exposes the
 * state to TalkBack and gives the row the system switch touch treatment);
 * the inner Switch is passive — pure visual. Toggling gets the light
 * virtual-key tick. 16sp/500 title over a 14sp supporting line, min 56dp.
 */
@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = {
                    view.gsHaptic(HapticFeedbackConstants.VIRTUAL_KEY)
                    onCheckedChange(it)
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        letterSpacing = 0.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(GsMotion.spaceM))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ValueRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                letterSpacing = 0.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String?,
    leading: ImageVector?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(vertical = GsMotion.spaceXS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (leading != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = leading,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        letterSpacing = 0.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChipRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
    ) {
        options.forEach { option ->
            GsChip(
                text = option,
                selected = option == selected,
                onClick = { onSelect(option) }
            )
        }
    }
}

/** Group micro-label per the type ramp: 11sp medium, uppercase, 1.2sp
 *  tracking, onSurfaceVariant, 16dp top / 8dp bottom. TalkBack heading. */
@Composable
private fun GroupMicroLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.2.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = 16.dp, bottom = 8.dp)
            .semantics { heading() }
    )
}

/**
 * One inset grouped container per logical group: 16dp radius, surfaceContainer
 * fill, NO border, NO shadow. Rows inside carry their own 16dp inner padding;
 * hairline separators sit between them (GroupSeparator).
 */
@Composable
private fun GroupContainer(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        content()
    }
}

/**
 * Hairline between grouped rows: 1dp outlineVariant, indented 68dp from the
 * left so it starts at the row TEXT (16 inner + 40 glyph + 12 gap) and ends
 * 16dp from the right — not full-bleed.
 */
@Composable
private fun GroupSeparator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 68.dp, end = 16.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/** Privacy pass: the whole on-device corpus — conversations, messages, library
 *  saves — as one JSON document the user names and places via the system
 *  picker. Store hiccups leave the destination file unwritten, never partial.
 *  The build + write runs on Dispatchers.IO — the corpus-sized JSON assembly
 *  and the stream write never block the main thread. */
private suspend fun exportAllData(context: android.content.Context, uri: android.net.Uri) {
    withContext(Dispatchers.IO) {
        runCatching {
            val db = ServiceLocator.db
            val payload = JSONObject().apply {
            put("exportedAt", OffsetDateTime.now().toString())
            put("appVersion", BuildConfig.VERSION_NAME)
            put("conversations", JSONArray().apply {
                db.conversationDao().all().forEach { c ->
                    put(JSONObject().apply {
                        put("id", c.id)
                        put("title", c.title)
                        put("modelId", c.modelId ?: JSONObject.NULL)
                        put("pinned", c.pinned)
                        put("archived", c.archived)
                        put("createdAt", c.createdAt)
                        put("updatedAt", c.updatedAt)
                    })
                }
            })
            put("messages", JSONArray().apply {
                db.messageDao().all().forEach { m ->
                    put(JSONObject().apply {
                        put("id", m.id)
                        put("conversationId", m.conversationId)
                        put("role", m.role)
                        put("content", m.content)
                        put("createdAt", m.createdAt)
                    })
                }
            })
            put("library", JSONArray().apply {
                db.savedItemDao().observeAll().first().forEach { item ->
                    put(JSONObject().apply {
                        put("id", item.id)
                        put("kind", item.kind)
                        put("title", item.title)
                        put("content", item.content)
                        put("createdAt", item.createdAt)
                    })
                }
            })
        }
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(payload.toString(2).toByteArray(Charsets.UTF_8))
        }
        }
    }
}
