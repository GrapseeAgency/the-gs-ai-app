package com.grapsee.gsai.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.BuildConfig
import com.grapsee.gsai.CrashReporter
import com.grapsee.gsai.data.ModelPrefs
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.data.model.ModelCatalog
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.gsHaptic
import androidx.activity.compose.BackHandler
import java.time.OffsetDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val themeOptions = listOf("Light", "Dark", "System")
private val effortOptions = listOf("Low", "Medium", "High")
private val aiLanguageOptions = listOf("EN", "中文", "हिन्दी", "العربية")

@Composable
fun SettingsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit = {}) {
    var expandedId by remember { mutableStateOf<String?>(null) }
    // Privacy flows (UI-local)
    var showClearData by remember { mutableStateOf(false) }
    var showDeleteAccount by remember { mutableStateOf(false) }

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

    GsScreenScaffold(title = "Settings", onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
        ) {
            ExpandCard(
                id = "appearance",
                icon = Icons.Outlined.Palette,
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
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Aurora teal",
                            style = MaterialTheme.typography.labelSmall,
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

            ExpandCard(
                id = "chat",
                icon = Icons.Outlined.ChatBubbleOutline,
                title = "Chat",
                expandedId = expandedId,
                onToggle = { expandedId = if (expandedId == "chat") null else "chat" }
            ) {
                ValueRow(title = "Default model", value = "GS Balanced")
                SwitchRow(
                    title = "Enter to send",
                    checked = SettingsStore.enterToSend,
                    onCheckedChange = { SettingsStore.updateEnterToSend(it) }
                )
                SwitchRow(
                    title = "Auto-title chats",
                    subtitle = "Name conversations from their first message",
                    checked = SettingsStore.autoTitleChats,
                    onCheckedChange = { SettingsStore.updateAutoTitleChats(it) }
                )
                SwitchRow(
                    title = "Send on double-tap",
                    checked = SettingsStore.sendDoubleTap,
                    onCheckedChange = { SettingsStore.updateSendDoubleTap(it) }
                )
                // Real model state: shows the default the chat send path reads,
                // and hands the reader to the Model Centre to change it.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { onNavigate(GsRoutes.MODELS) })
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Default model",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = ModelCatalog.byId(ModelPrefs.defaultId(context))?.displayName
                            ?: ModelCatalog.default.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ExpandCard(
                id = "ai",
                icon = Icons.Outlined.AutoAwesome,
                title = "AI",
                expandedId = expandedId,
                onToggle = { expandedId = if (expandedId == "ai") null else "ai" }
            ) {
                SwitchRow(
                    title = "Memory",
                    subtitle = "Remember details across chats",
                    checked = SettingsStore.memory,
                    onCheckedChange = { SettingsStore.updateMemory(it) }
                )
                SwitchRow(
                    title = "Personalisation",
                    checked = SettingsStore.personalisation,
                    onCheckedChange = { SettingsStore.updatePersonalisation(it) }
                )
                Text(
                    text = "Reasoning effort",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ChipRow(options = effortOptions, selected = SettingsStore.reasoningEffort) { SettingsStore.updateReasoningEffort(it) }
            }

            ExpandCard(
                id = "privacy",
                icon = Icons.Outlined.PrivacyTip,
                title = "Privacy",
                expandedId = expandedId,
                onToggle = { expandedId = if (expandedId == "privacy") null else "privacy" }
            ) {
                ActionRow(
                    title = "Export data",
                    subtitle = "Download a copy of your data",
                    leading = Icons.Outlined.Download,
                    onClick = { exportData.launch("gs-ai-export.json") }
                )
                SwitchRow(
                    title = "Training preferences",
                    subtitle = "Improve models with your chats",
                    checked = SettingsStore.trainingOptIn,
                    onCheckedChange = { SettingsStore.updateTrainingOptIn(it) }
                )
                ActionRow(
                    title = "Clear local data",
                    subtitle = "Remove cached chats and files on this device",
                    leading = Icons.Outlined.DeleteOutline,
                    onClick = { showClearData = true }
                )
                if (CrashReporter.lastReport(context) != null) {
                    ActionRow(
                        title = "Send crash report",
                        subtitle = "The last launch captured an error — share its details",
                        leading = Icons.Outlined.BugReport,
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

            ExpandCard(
                id = "security",
                icon = Icons.Outlined.Shield,
                title = "Security",
                expandedId = expandedId,
                onToggle = { expandedId = if (expandedId == "security") null else "security" }
            ) {
                SwitchRow(
                    title = "App passcode",
                    checked = SettingsStore.appPasscode,
                    onCheckedChange = { SettingsStore.updateAppPasscode(it) }
                )
                SwitchRow(
                    title = "Biometric unlock",
                    checked = SettingsStore.biometricUnlock,
                    onCheckedChange = { SettingsStore.updateBiometricUnlock(it) }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Two-factor authentication",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    GsChip(text = "On", selected = true)
                }
                ValueRow(title = "Trusted devices", value = "2")
            }

            ExpandCard(
                id = "notifications",
                icon = Icons.Outlined.Notifications,
                title = "Notifications",
                expandedId = expandedId,
                onToggle = { expandedId = if (expandedId == "notifications") null else "notifications" }
            ) {
                SwitchRow(
                    title = "Push notifications",
                    checked = SettingsStore.pushNotifications,
                    onCheckedChange = { SettingsStore.updatePushNotifications(it) }
                )
                SwitchRow(
                    title = "Sounds",
                    checked = SettingsStore.sounds,
                    onCheckedChange = { SettingsStore.updateSounds(it) }
                )
                SwitchRow(
                    title = "AI task alerts",
                    checked = SettingsStore.taskAlerts,
                    onCheckedChange = { SettingsStore.updateTaskAlerts(it) }
                )
                SwitchRow(
                    title = "Email digest",
                    checked = SettingsStore.emailDigest,
                    onCheckedChange = { SettingsStore.updateEmailDigest(it) }
                )
            }

            ExpandCard(
                id = "language",
                icon = Icons.Outlined.Language,
                title = "Language",
                expandedId = expandedId,
                onToggle = { expandedId = if (expandedId == "language") null else "language" }
            ) {
                ValueRow(title = "App language", value = "English (UK)")
                Text(
                    text = "AI language",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ChipRow(options = aiLanguageOptions, selected = SettingsStore.aiLanguage) { SettingsStore.updateAiLanguage(it) }
                ValueRow(title = "Voice language", value = "English (UK)")
            }

            ExpandCard(
                id = "accessibility",
                icon = Icons.Outlined.Accessibility,
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
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(SettingsStore.fontScale * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
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
                        text = "Aa — How your chats will read",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = (15 * SettingsStore.fontScale).sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                SwitchRow(
                    title = "High contrast",
                    checked = SettingsStore.highContrast,
                    onCheckedChange = { SettingsStore.updateHighContrast(it) }
                )
                SwitchRow(
                    title = "Reduce motion",
                    checked = SettingsStore.reduceMotion,
                    onCheckedChange = { SettingsStore.updateReduceMotion(it) }
                )
                SwitchRow(
                    title = "Screen reader hints",
                    checked = SettingsStore.screenReaderHints,
                    onCheckedChange = { SettingsStore.updateScreenReaderHints(it) }
                )
                SwitchRow(
                    title = "Haptics",
                    checked = SettingsStore.haptics,
                    onCheckedChange = { SettingsStore.updateHaptics(it) }
                )
            }

            ExpandCard(
                id = "about",
                icon = Icons.Outlined.Info,
                title = "About",
                expandedId = expandedId,
                onToggle = { expandedId = if (expandedId == "about") null else "about" }
            ) {
                ValueRow(title = "Version", value = BuildConfig.VERSION_NAME)
                ValueRow(title = "Build", value = BuildConfig.VERSION_CODE.toString())
                ValueRow(title = "Updates", value = "Automatic via GS LiveUpdate")
            }

            Spacer(modifier = Modifier.height(GsMotion.spaceL))
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
}

/** Expandable category card: header row always visible, content revealed on tap. */
@Composable
private fun ExpandCard(
    id: String,
    icon: ImageVector,
    title: String,
    expandedId: String?,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val expanded = expandedId == id
    GsCard(onClick = onToggle) {
        Column(modifier = Modifier.animateContentSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) {
                        Icons.Outlined.KeyboardArrowUp
                    } else {
                        Icons.Outlined.KeyboardArrowDown
                    },
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(GsMotion.spaceM))
                Column(
                    verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    val view = LocalView.current
    // The WHOLE row is the switch (toggleable role exposes the state to
    // TalkBack and gives the row the system switch touch treatment); the inner
    // Switch is passive — pure visual. Toggling gets the light virtual-key tick.
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
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
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
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
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (leading != null) {
            Icon(
                imageVector = leading,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
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
