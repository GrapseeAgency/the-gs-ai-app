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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.AutoAwesome
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
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.BuildConfig
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.theme.GsMotion

private val themeOptions = listOf("Light", "Dark", "System")
private val effortOptions = listOf("Low", "Medium", "High")
private val aiLanguageOptions = listOf("EN", "中文", "हिन्दी", "العربية")

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var expandedId by remember { mutableStateOf<String?>(null) }

    // Appearance
    var themeMode by remember { mutableStateOf("System") }
    var reduceAnimations by remember { mutableStateOf(false) }
    // Chat
    var enterToSend by remember { mutableStateOf(true) }
    var autoTitleChats by remember { mutableStateOf(true) }
    var sendDoubleTap by remember { mutableStateOf(false) }
    // AI
    var memory by remember { mutableStateOf(true) }
    var personalisation by remember { mutableStateOf(true) }
    var reasoningEffort by remember { mutableStateOf("Medium") }
    // Privacy
    var trainingOptIn by remember { mutableStateOf(false) }
    var showClearData by remember { mutableStateOf(false) }
    var showDeleteAccount by remember { mutableStateOf(false) }
    // Security
    var appPasscode by remember { mutableStateOf(false) }
    var biometricUnlock by remember { mutableStateOf(false) }
    // Notifications
    var pushNotifications by remember { mutableStateOf(true) }
    var sounds by remember { mutableStateOf(true) }
    var taskAlerts by remember { mutableStateOf(true) }
    var emailDigest by remember { mutableStateOf(false) }
    // Language
    var aiLanguage by remember { mutableStateOf("EN") }
    // Accessibility
    var fontScale by remember { mutableStateOf(1.0f) }
    var highContrast by remember { mutableStateOf(false) }
    var reduceMotion by remember { mutableStateOf(false) }
    var screenReaderHints by remember { mutableStateOf(false) }
    var haptics by remember { mutableStateOf(true) }

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
                ChipRow(options = themeOptions, selected = themeMode) { themeMode = it }
                SwitchRow(
                    title = "Reduce animations",
                    subtitle = "Skip decorative motion for a faster feel",
                    checked = reduceAnimations,
                    onCheckedChange = { reduceAnimations = it }
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
                    checked = enterToSend,
                    onCheckedChange = { enterToSend = it }
                )
                SwitchRow(
                    title = "Auto-title chats",
                    subtitle = "Name conversations from their first message",
                    checked = autoTitleChats,
                    onCheckedChange = { autoTitleChats = it }
                )
                SwitchRow(
                    title = "Send on double-tap",
                    checked = sendDoubleTap,
                    onCheckedChange = { sendDoubleTap = it }
                )
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
                    checked = memory,
                    onCheckedChange = { memory = it }
                )
                SwitchRow(
                    title = "Personalisation",
                    checked = personalisation,
                    onCheckedChange = { personalisation = it }
                )
                Text(
                    text = "Reasoning effort",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ChipRow(options = effortOptions, selected = reasoningEffort) { reasoningEffort = it }
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
                    onClick = { /* export flow lands with the data subsystem */ }
                )
                SwitchRow(
                    title = "Training preferences",
                    subtitle = "Improve models with your chats",
                    checked = trainingOptIn,
                    onCheckedChange = { trainingOptIn = it }
                )
                ActionRow(
                    title = "Clear local data",
                    subtitle = "Remove cached chats and files on this device",
                    leading = Icons.Outlined.DeleteOutline,
                    onClick = { showClearData = true }
                )
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
                    checked = appPasscode,
                    onCheckedChange = { appPasscode = it }
                )
                SwitchRow(
                    title = "Biometric unlock",
                    checked = biometricUnlock,
                    onCheckedChange = { biometricUnlock = it }
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
                    GsChip(text = "On", selected = true, onClick = {})
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
                    checked = pushNotifications,
                    onCheckedChange = { pushNotifications = it }
                )
                SwitchRow(
                    title = "Sounds",
                    checked = sounds,
                    onCheckedChange = { sounds = it }
                )
                SwitchRow(
                    title = "AI task alerts",
                    checked = taskAlerts,
                    onCheckedChange = { taskAlerts = it }
                )
                SwitchRow(
                    title = "Email digest",
                    checked = emailDigest,
                    onCheckedChange = { emailDigest = it }
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
                ChipRow(options = aiLanguageOptions, selected = aiLanguage) { aiLanguage = it }
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
                            text = "${(fontScale * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Slider(
                        value = fontScale,
                        onValueChange = { fontScale = it },
                        valueRange = 0.8f..1.4f,
                        steps = 5
                    )
                    Text(
                        text = "Aa — How your chats will read",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = (15 * fontScale).sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                SwitchRow(
                    title = "High contrast",
                    checked = highContrast,
                    onCheckedChange = { highContrast = it }
                )
                SwitchRow(
                    title = "Reduce motion",
                    checked = reduceMotion,
                    onCheckedChange = { reduceMotion = it }
                )
                SwitchRow(
                    title = "Screen reader hints",
                    checked = screenReaderHints,
                    onCheckedChange = { screenReaderHints = it }
                )
                SwitchRow(
                    title = "Haptics",
                    checked = haptics,
                    onCheckedChange = { haptics = it }
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
        AlertDialog(
            onDismissRequest = { showClearData = false },
            title = { Text("Clear local data?") },
            text = {
                Text("Cached chats, files and preferences on this device will be removed. Anything synced to your account stays safe.")
            },
            confirmButton = {
                TextButton(
                    onClick = { showClearData = false },
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
        AlertDialog(
            onDismissRequest = { showDeleteAccount = false },
            title = { Text("Delete account?") },
            text = {
                Text("This permanently deletes your account, assistants, chats and files. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteAccount = false },
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
    Row(
        modifier = Modifier.fillMaxWidth(),
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
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
