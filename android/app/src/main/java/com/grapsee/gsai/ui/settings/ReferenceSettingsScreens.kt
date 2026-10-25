package com.grapsee.gsai.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.BuildConfig
import com.grapsee.gsai.data.AccountStore
import com.grapsee.gsai.data.DataExport
import com.grapsee.gsai.data.SessionStore
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.data.local.ModelCatalog
import com.grapsee.gsai.data.local.ModelStore
import com.grapsee.gsai.data.notifications.ReplyNotifier
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.GsLinks
import com.grapsee.gsai.ui.components.RefAccentRow
import com.grapsee.gsai.ui.components.RefArt
import com.grapsee.gsai.ui.components.RefArtwork
import com.grapsee.gsai.ui.components.RefAvatar
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefDivider
import com.grapsee.gsai.ui.components.RefFootnote
import com.grapsee.gsai.ui.components.RefChoiceTile
import com.grapsee.gsai.ui.components.RefIconTile
import com.grapsee.gsai.ui.components.RefOptionCard
import com.grapsee.gsai.ui.components.RefPage
import com.grapsee.gsai.ui.components.RefPrimaryButton
import com.grapsee.gsai.ui.components.RefQuietButton
import com.grapsee.gsai.ui.components.RefRow
import com.grapsee.gsai.ui.components.RefSection
import com.grapsee.gsai.ui.components.RefSegmented
import com.grapsee.gsai.ui.components.RefStatTile
import com.grapsee.gsai.ui.components.RefStatusPill
import com.grapsee.gsai.ui.components.RefToggleRow
import com.grapsee.gsai.ui.components.RefValueRow
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Image
import androidx.compose.runtime.LaunchedEffect
import com.grapsee.gsai.data.ServiceLocator
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefNavRow
import com.grapsee.gsai.ui.components.RefRowDivider
import com.grapsee.gsai.ui.components.RefInlinePill
import com.grapsee.gsai.ui.components.RefTileStrip
import com.grapsee.gsai.ui.components.RefStripTile
import com.grapsee.gsai.ui.components.RefInfoCard

// ---------------------------------------------------------------------------
// Account
// ---------------------------------------------------------------------------

@Composable
fun AccountSettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val colors = GsTheme.colors
    var name by remember { mutableStateOf(AccountStore.displayName(context)) }
    var email by remember { mutableStateOf(AccountStore.email(context)) }
    var editing by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    RefPage(
        title = "Account",
        subtitle = "Your profile and sign-in.",
        onBack = onBack
    ) {
        RefCard(padding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RefAvatar(initials = initialsOf(name), size = 84.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    name.ifBlank { "GS account" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary
                )
                Text(
                    email.ifBlank { "Signed in on this device" },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )
                Spacer(Modifier.height(8.dp))
                RefStatusPill("Early access")
            }
        }

        RefSection(label = "Profile information") {
            RefRow(
                title = "Full name",
                subtitle = name.ifBlank { "Not set" },
                icon = GsIcons.UserRound,
                onClick = { editing = true }
            )
            RefDivider()
            RefRow(
                title = "Email address",
                subtitle = email.ifBlank { "Not set" },
                icon = GsIcons.Mail,
                onClick = { editing = true }
            )
            RefDivider()
            RefValueRow(
                title = "Username",
                value = "@" + (name.trim().lowercase().replace(" ", ".").ifBlank { "guest" }),
                icon = GsIcons.UserRound
            )
            RefDivider()
            RefRow(
                title = "Plan",
                subtitle = "Free early access",
                icon = GsIcons.CreditCard,
                onClick = { }
            )
        }

        RefSection(label = "Account actions") {
            RefRow(
                title = "Edit profile",
                subtitle = "Change the name and email stored on this device",
                icon = GsIcons.PenLine,
                onClick = { editing = true }
            )
            RefDivider()
            RefRow(
                title = "Sign out",
                subtitle = "Return to the welcome screen",
                icon = GsIcons.ArrowRight,
                onClick = { confirmLogout = true }
            )
        }

        RefFootnote("Your name and email are stored locally and are never sent to a third party. Signing out clears the session flag, not your chats.")
        Spacer(Modifier.height(10.dp))
    }

    if (editing) {
        var draftName by remember { mutableStateOf(name) }
        var draftEmail by remember { mutableStateOf(email) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Edit profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        label = { Text("Full name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = draftEmail,
                        onValueChange = { draftEmail = it },
                        label = { Text("Email address") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    AccountStore.setDisplayName(context, draftName)
                    AccountStore.setEmail(context, draftEmail)
                    name = draftName.trim()
                    email = draftEmail.trim()
                    editing = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text("Cancel") }
            }
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Sign out?") },
            text = { Text("You will return to the welcome screen. Your local conversations stay on this device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    onLogout()
                }) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Cancel") }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Theme customisation
// ---------------------------------------------------------------------------

@Composable
fun ThemeCustomisationScreen(onBack: () -> Unit) {
    val colors = GsTheme.colors
    RefPage(
        title = "Theme Customisation",
        subtitle = "Choose a surface and accent that feels like yours.",
        onBack = onBack
    ) {
        // A miniature of the app: bloom, glass card, pill button. It is drawn
        // from the same tokens the real screens use, so it cannot drift.
        Box(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            colors.aurora.first().copy(alpha = 0.55f),
                            colors.accentSoft,
                            colors.appBackground
                        )
                    )
                )
        ) {
            RefArtwork(
                art = RefArt.Bloom,
                size = 150.dp,
                modifier = Modifier.align(Alignment.Center)
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text("GS AI", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Text(
                    "Smarter conversations. A brighter you.",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )
            }
        }

        RefSection(label = "Background style") {
            Column(Modifier.padding(14.dp)) {
                RefSegmented(
                    listOf("Blurred", "Gradient", "Solid"),
                    SettingsStore.backgroundStyle,
                    { SettingsStore.updateBackgroundStyle(it) }
                )
            }
        }

        RefSection(label = "Accent") {
            Column(Modifier.padding(16.dp)) {
                RefAccentRow(
                    selectedId = SettingsStore.accentId,
                    onSelect = { SettingsStore.updateAccentId(it) }
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "The accent travels through buttons, charts, links and the selected state. It is applied instantly.",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        RefPrimaryButton("Done", onClick = onBack, icon = GsIcons.Check)
        Spacer(Modifier.height(10.dp))
        RefFootnote("Every surface is tuned for readable contrast in both light and dark appearances.")
    }
}

// ---------------------------------------------------------------------------
// Notifications
// ---------------------------------------------------------------------------

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = GsTheme.colors
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> SettingsStore.updatePushNotifications(granted) }

    RefPage(
        title = "Notifications",
        subtitle = "Choose what reaches you and how it feels.",
        onBack = onBack
    ) {
        RefCard(
            padding = androidx.compose.foundation.layout.PaddingValues(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RefIconTile(GsIcons.Bell, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Quiet by default", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Text(
                        "GS only speaks up when a reply finishes while the app is in the background.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary
                    )
                }
            }
        }

        RefSection(label = "Messages") {
            RefToggleRow(
                title = "Reply notifications",
                subtitle = "One notice when a long answer completes",
                icon = GsIcons.MessageCircle,
                checked = SettingsStore.pushNotifications,
                onCheckedChange = { want ->
                    if (!want) {
                        SettingsStore.updatePushNotifications(false)
                    } else if (ReplyNotifier.hasPermission(context)) {
                        SettingsStore.updatePushNotifications(true)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        SettingsStore.updatePushNotifications(true)
                    }
                }
            )
            RefDivider()
            RefValueRow(
                title = "System permission",
                value = if (ReplyNotifier.hasPermission(context)) "Allowed" else "Not granted",
                icon = GsIcons.Lock
            )
        }

        RefSection(label = "Feedback") {
            RefToggleRow(
                title = "Haptic feedback",
                subtitle = "A soft tick on presses and confirmations",
                icon = GsIcons.Smartphone,
                checked = SettingsStore.haptics,
                onCheckedChange = { SettingsStore.updateHaptics(it) }
            )
            RefDivider()
            RefToggleRow(
                title = "Respect reduced motion",
                subtitle = "Hold still when the system asks for less motion",
                icon = GsIcons.Accessibility,
                checked = SettingsStore.reduceMotion,
                onCheckedChange = { SettingsStore.updateReduceMotion(it) }
            )
        }

        RefFootnote("Notification permission is owned by Android. The switch reflects what the system will actually deliver, never a promise it cannot keep.")
    }
}

// ---------------------------------------------------------------------------
// Privacy
// ---------------------------------------------------------------------------

@Composable
fun PrivacySettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = GsTheme.colors
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            val ok = DataExport.write(context, uri)
            Toast.makeText(
                context,
                if (ok) "Export written" else "Could not write the export",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    RefPage(
        title = "Privacy & Security",
        subtitle = "Your data, your device, your call.",
        onBack = onBack
    ) {
        RefCard(padding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RefArtwork(RefArt.Privacy, size = 82.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Local first", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Text(
                        "Conversations, drafts and files live in this app's private storage. Export or erase them at any time.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary
                    )
                }
            }
        }

        RefSection(label = "What we collect") {
            RefValueRow(title = "Account information", value = "Local", icon = GsIcons.UserRound)
            RefDivider()
            RefValueRow(title = "Conversations", value = "On device", icon = GsIcons.MessageCircle)
            RefDivider()
            RefValueRow(title = "Attachments", value = "App storage", icon = GsIcons.Paperclip)
            RefDivider()
            RefValueRow(title = "Analytics", value = "None", icon = GsIcons.Gauge)
        }

        RefSection(label = "Your controls") {
            RefRow(
                title = "Export my data",
                subtitle = "Write every conversation and saved item to a JSON file",
                icon = GsIcons.FileText,
                onClick = { export.launch("gs-ai-export.json") }
            )
            RefDivider()
            RefRow(
                title = "Clear local data",
                subtitle = "Remove chats, messages and library saves from this device",
                icon = GsIcons.Trash2,
                onClick = { confirmClear = true }
            )
            RefDivider()
            RefRow(
                title = "On-device AI",
                subtitle = if (SettingsStore.preferLocal) "Preferred when a model is installed" else "Cloud provider by default",
                icon = GsIcons.Cpu,
                onClick = { }
            )
        }

        RefFootnote("Clearing local data cannot be undone. Anything already delivered to the cloud provider is governed by that provider's retention policy.")
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear local data?") },
            text = { Text("Every conversation, message and library save on this device will be removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val db = ServiceLocator.db
                                db.messageDao().deleteAll()
                                db.savedItemDao().clear()
                                db.conversationDao().clear()
                            }
                        }
                        Toast.makeText(context, "Local data cleared", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Clear everything") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Chat & conversations
// ---------------------------------------------------------------------------

@Composable
fun ChatSettingsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val context = LocalContext.current
    val colors = GsTheme.colors
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    val mode = SettingsStore.chatMode(null)

    RefPage(
        title = "Chat & Conversations",
        subtitle = "How new chats behave.",
        onBack = onBack
    ) {
        RefSection(label = "Response mode") {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RefSegmented(
                    options = listOf("Flash", "Thinking", "Auto"),
                    selected = mode.replaceFirstChar { it.uppercase() },
                    onSelect = { SettingsStore.setChatMode(null, it.lowercase()) }
                )
                Text(
                    text = when (mode) {
                        "thinking" -> "Thinking spends longer on reasoning before it answers."
                        "auto" -> "Auto decides per question: quick for simple turns, deeper for hard ones."
                        else -> "Flash answers immediately — the right default for conversation."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )
            }
        }

        RefSection(label = "History") {
            RefRow(
                title = "Search chats",
                subtitle = "Find a message across every conversation",
                icon = GsIcons.Search,
                onClick = { onNavigate(GsRoutes.CHAT_SEARCH) }
            )
            RefDivider()
            RefRow(
                title = "Archived chats",
                subtitle = "Conversations you set aside",
                icon = GsIcons.Archive,
                onClick = { onNavigate(GsRoutes.CHAT_ARCHIVE) }
            )
            RefDivider()
            RefRow(
                title = "Clear conversation history",
                subtitle = "Delete every message on this device",
                icon = GsIcons.Trash2,
                onClick = { confirmClear = true }
            )
        }

        RefSection(label = "Data") {
            RefRow(
                title = "Export conversations",
                subtitle = "Download a JSON copy",
                icon = GsIcons.FileText,
                onClick = { onNavigate(GsRoutes.SETTINGS_PRIVACY) }
            )
        }

        RefFootnote("Response mode is a per-conversation property. Changing it here sets the default for chats you start from now on.")
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear conversation history?") },
            text = { Text("Every message and conversation on this device will be removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val db = ServiceLocator.db
                                db.messageDao().deleteAll()
                                db.conversationDao().clear()
                            }
                        }
                        Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Clear history") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// AI intelligence
// ---------------------------------------------------------------------------

@Composable
fun AiIntelligenceScreen(
    onBack: () -> Unit,
    onLocalAiRequested: () -> Unit,
    onTryComponents: () -> Unit,
    onOpenModelDetails: (() -> Unit)? = null,
) {
    val colors = GsTheme.colors
    val installed = ModelStore.installedPath != null
    val mode = SettingsStore.chatMode(null)
    val local = SettingsStore.preferLocal

    RefPage(
        title = "AI Intelligence",
        subtitle = "Configure how Nexly thinks, responds and helps you.",
        onBack = onBack
    ) {
        RefCard(padding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RefArtwork(RefArt.Bloom, size = 92.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("GS Intelligence", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Text(
                        "One assistant, two engines. The cloud model handles depth; the on-device model keeps working offline.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary
                    )
                }
            }
        }

        RefSection(label = "Model source") {
            Row(
                Modifier.padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RefChoiceTile(
                    title = "Nexly Cloud",
                    subtitle = "Full-quality answers through the GS backend",
                    selected = !local,
                    onClick = { SettingsStore.updatePreferLocal(false) },
                    icon = GsIcons.Globe,
                    modifier = Modifier.weight(1f)
                )
                RefChoiceTile(
                    title = "On-device",
                    subtitle = if (installed) {
                        installedModelLabel()
                    } else {
                        "No model installed — tap to download"
                    },
                    selected = local && installed,
                    onClick = {
                        if (installed) SettingsStore.updatePreferLocal(true) else onLocalAiRequested()
                    },
                    icon = GsIcons.Cpu,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        RefSection(label = "Reasoning effort") {
            Row(
                Modifier.padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RefChoiceTile(
                    title = "Fast",
                    subtitle = "Immediate, concise replies",
                    selected = mode == "flash",
                    onClick = { SettingsStore.setChatMode(null, "flash") },
                    icon = GsIcons.Zap,
                    modifier = Modifier.weight(1f)
                )
                RefChoiceTile(
                    title = "Thinking",
                    subtitle = "Spends longer on hard problems",
                    selected = mode == "thinking",
                    onClick = { SettingsStore.setChatMode(null, "thinking") },
                    icon = GsIcons.Brain,
                    modifier = Modifier.weight(1f)
                )
                RefChoiceTile(
                    title = "Auto",
                    subtitle = "Chooses per question",
                    selected = mode == "auto",
                    onClick = { SettingsStore.setChatMode(null, "auto") },
                    icon = GsIcons.Split,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "This is the same switch the chat header exposes — changing it here changes the next reply.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 12.dp)
            )
        }

        if (onOpenModelDetails != null) {
            RefSection(label = "On this device") {
                RefRow(
                    title = "View model details",
                    subtitle = "Size, capability and what installing it changes",
                    icon = GsIcons.Cpu,
                    onClick = onOpenModelDetails,
                    onClickLabel = "View model details"
                )
            }
        }

        RefSection(label = "Rendering") {
            RefRow(
                title = "Native component library",
                subtitle = "Charts, forms, maps, editors, 3D and more — 200+ interactive surfaces",
                icon = GsIcons.LayoutGrid,
                onClick = onTryComponents
            )
            RefDivider()
            RefRow(
                title = "Local model contract",
                subtitle = "On-device answers receive the same widget vocabulary as cloud answers",
                icon = GsIcons.Code,
                onClick = { }
            )
        }

        RefFootnote("On-device generation is slower and uses battery. It is the honest choice when the network is gone, not a silent replacement for the cloud model.")
    }
}

// ---------------------------------------------------------------------------
// Memory
// ---------------------------------------------------------------------------

@Composable
fun MemorySettingsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val colors = GsTheme.colors
    val scope = rememberCoroutineScope()
    val dao = ServiceLocator.db.savedItemDao()
    var items by remember { mutableStateOf<List<com.grapsee.gsai.data.local.SavedItemEntity>>(emptyList()) }
    var showClearAll by remember { mutableStateOf(false) }
    var showItems by remember { mutableStateOf(false) }
    var memoryOn by remember { mutableStateOf(SettingsStore.memory) }

    LaunchedEffect(Unit) {
        dao.observeAll().collect { items = it }
    }

    RefSettingsPage(
        title = "Memory Management",
        subtitle = "Control what Nexly remembers about you",
        onBack = onBack,
    ) {
        // ---- Nexly remembers -------------------------------------------
        RefCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(com.grapsee.gsai.R.drawable.gs_home_lotus_new),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                    Box(
                        Modifier
                            .size(46.dp)
                            .background(Color.White.copy(alpha = 0.30f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            GsIcons.Brain,
                            contentDescription = null,
                            tint = Color(0xFF5A50D6),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Nexly remembers",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GsFonts.Product,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "so your conversations feel more personal, helpful, and relevant.",
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textTertiary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFFC3DCCB).copy(alpha = 0.60f))
                                .clickable(role = Role.Button, onClickLabel = "Toggle memory") {
                                    memoryOn = !memoryOn
                                    SettingsStore.updateMemory(memoryOn)
                                }
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                GsIcons.Info,
                                contentDescription = null,
                                tint = Color(0xFF177A50),
                                modifier = Modifier.size(10.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                if (memoryOn) "Memory is ON" else "Memory is OFF",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = GsFonts.Product,
                                color = Color(0xFF177A50),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        RefInlinePill(label = "Manage", icon = GsIcons.ChevronRight) { showItems = true }
                    }
                }
            }
        }

        // ---- What Nexly remembers --------------------------------------
        RefGroupHeader("What Nexly remembers", "Based on your past chats")
        RefCard {
            RefTileStrip(
                listOf(
                    RefStripTile(GsIcons.UserRound, GsWellTone.Lilac, "Personal Info", "Name, interests, preferences"),
                    RefStripTile(GsIcons.Heart, GsWellTone.Rose, "Likes & Interests", "Hobbies, topics, creative style"),
                    RefStripTile(GsIcons.ClipboardList, GsWellTone.Blue, "Important Details", "Goals, plans, reminders"),
                    RefStripTile(GsIcons.Settings, GsWellTone.Mint, "Preferences", "Tone, style, settings"),
                )
            )
        }

        // ---- Recent Memories -------------------------------------------
        RefGroupHeader("Recent Memories")
        RefCard {
            if (items.isEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RefWellTile(GsIcons.Sparkles, GsWellTone.Peach, 28.dp)
                    Spacer(Modifier.width(11.dp))
                    Text(
                        "Nothing saved yet — memories appear here as you save answers and gems from chats.",
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textTertiary,
                    )
                }
            } else {
                val shown = items.take(4)
                shown.forEachIndexed { index, item ->
                    RefNavRow(
                        title = item.title,
                        subtitle = "\u201C" + item.content.take(48) + "\u2026\u201D",
                        leading = { RefWellTile(kindGlyph(item.kind), kindTone(item.kind), 28.dp) },
                        trailing = {
                            Text(
                                memoryDate(item.createdAt),
                                fontSize = 10.5.sp,
                                fontFamily = GsFonts.Product,
                                color = colors.textTertiary,
                            )
                        },
                    )
                    if (index < shown.lastIndex) RefRowDivider()
                }
            }
        }

        // ---- Manage Memory ---------------------------------------------
        RefGroupHeader("Manage Memory")
        RefCard {
            RefNavRow(
                title = "Edit what Nexly remembers",
                subtitle = "Remove or update specific memories",
                onClick = { showItems = true },
                leading = { RefWellTile(GsIcons.PenLine, GsWellTone.Peach, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "Clear specific memories",
                subtitle = "Delete individual memories",
                onClick = { showItems = true },
                leading = { RefWellTile(GsIcons.Trash2, GsWellTone.Rose, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "Clear all memories",
                subtitle = "Start fresh with no saved information",
                onClick = { showClearAll = true },
                leading = { RefWellTile(GsIcons.Trash2, GsWellTone.Rose, 28.dp) },
            )
        }

        Spacer(Modifier.height(14.dp))
        RefInfoCard(
            text = "Your privacy matters. You're always in control of what Nexly remembers.",
            icon = GsIcons.Shield,
        )
        Spacer(Modifier.height(10.dp))
    }

    if (showClearAll) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearAll = false },
            title = { Text("Clear all memories?") },
            text = { Text("Every saved memory leaves this device. Your conversations stay.") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        scope.launch { dao.clear() }
                        showClearAll = false
                    },
                ) { Text("Clear all") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showClearAll = false }) { Text("Cancel") }
            },
        )
    }

    if (showItems) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showItems = false },
            title = { Text("What Nexly remembers") },
            text = {
                Column {
                    if (items.isEmpty()) {
                        Text("Nothing saved yet.")
                    } else {
                        items.take(8).forEach { item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    item.title,
                                    fontSize = 12.sp,
                                    fontFamily = GsFonts.Product,
                                    color = colors.textPrimary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(
                                    GsIcons.Trash2,
                                    contentDescription = "Delete memory",
                                    tint = colors.textTertiary,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable(role = Role.Button, onClickLabel = "Delete memory") {
                                            scope.launch { dao.delete(item.id) }
                                        },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showItems = false }) { Text("Done") }
            },
        )
    }
}

private fun kindGlyph(kind: String): androidx.compose.ui.graphics.vector.ImageVector = when (kind) {
    "image" -> GsIcons.Palette
    "draft" -> GsIcons.FileText
    else -> GsIcons.MessageCircle
}

private fun kindTone(kind: String): GsWellTone = when (kind) {
    "image" -> GsWellTone.Rose
    "draft" -> GsWellTone.Blue
    else -> GsWellTone.Lilac
}

private fun memoryDate(iso: String): String = runCatching {
    val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXXXX", java.util.Locale.US)
    val date = parser.parse(iso) ?: return ""
    java.text.SimpleDateFormat("MMM d", java.util.Locale.US).format(date)
}.getOrDefault("")

@Composable
fun VoiceSettingsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val colors = GsTheme.colors
    RefPage(
        title = "Voice & Speech",
        subtitle = "Listen, speak, and keep your hands free.",
        onBack = onBack
    ) {
        RefCard(padding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                RefArtwork(RefArt.Voice, size = 110.dp)
                Spacer(Modifier.height(6.dp))
                Text("Read aloud", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Text(
                    "Answers can be spoken with your device's speech engine. Voice mode listens when you tap the mic.",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }

        RefSection(label = "Voice") {
            RefRow(
                title = "Open voice mode",
                subtitle = "Speak a question instead of typing it",
                icon = GsIcons.Mic,
                onClick = { onNavigate(GsRoutes.VOICE) }
            )
            RefDivider()
            RefValueRow(
                title = "Speech engine",
                value = "Device default",
                icon = GsIcons.AudioLines
            )
            RefDivider()
            RefToggleRow(
                title = "Haptic confirmation",
                subtitle = "A tick when dictation starts and stops",
                icon = GsIcons.Smartphone,
                checked = SettingsStore.haptics,
                onCheckedChange = { SettingsStore.updateHaptics(it) }
            )
        }

        RefFootnote("Speech is synthesized locally by Android. Audio is not uploaded to GS.")
    }
}

// ---------------------------------------------------------------------------
// Accessibility
// ---------------------------------------------------------------------------

@Composable
fun AccessibilitySettingsScreen(onBack: () -> Unit) {
    val colors = GsTheme.colors
    val scale = SettingsStore.fontScale
    RefPage(
        title = "Accessibility",
        subtitle = "Make GS comfortable for the way you read and move.",
        onBack = onBack
    ) {
        RefSection(label = "Visual") {
            RefToggleRow(
                title = "High contrast",
                subtitle = "Stronger ink, borders and focus rings",
                icon = GsIcons.Eye,
                checked = SettingsStore.highContrast,
                onCheckedChange = { SettingsStore.updateHighContrast(it) }
            )
            RefDivider()
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Text size", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    Text(
                        "${(scale * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.accentStrong
                    )
                }
                Slider(
                    value = scale,
                    onValueChange = { SettingsStore.stageFontScale(it) },
                    onValueChangeFinished = { SettingsStore.updateFontScale(scale) },
                    valueRange = 0.85f..1.30f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.accent,
                        activeTrackColor = colors.accent,
                        inactiveTrackColor = colors.accentSoft
                    )
                )
            }
            RefDivider()
            RefToggleRow(
                title = "Screen reader hints",
                subtitle = "Extra spoken context on controls",
                icon = GsIcons.Accessibility,
                checked = SettingsStore.screenReaderHints,
                onCheckedChange = { SettingsStore.updateScreenReaderHints(it) }
            )
        }

        RefSection(label = "Motion") {
            RefToggleRow(
                title = "Reduce motion",
                subtitle = "Hold decorative movement still",
                icon = GsIcons.Accessibility,
                checked = SettingsStore.reduceMotion,
                onCheckedChange = { SettingsStore.updateReduceMotion(it) }
            )
            RefDivider()
            RefToggleRow(
                title = "Simplify animations",
                subtitle = "Shorten transitions across the app",
                icon = GsIcons.Zap,
                checked = SettingsStore.reduceAnimations,
                onCheckedChange = { SettingsStore.updateReduceAnimations(it) }
            )
            RefDivider()
            RefToggleRow(
                title = "Haptics",
                subtitle = "Use touch feedback on presses",
                icon = GsIcons.Smartphone,
                checked = SettingsStore.haptics,
                onCheckedChange = { SettingsStore.updateHaptics(it) }
            )
        }

        RefFootnote("Every option here changes the running app immediately. If the system asks for reduced motion, GS honours it even when the switch is off.")
    }
}

// ---------------------------------------------------------------------------
// Help & support
// ---------------------------------------------------------------------------

@Composable
fun HelpSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = GsTheme.colors
    val faqs = listOf(
        "How do I make GS draw a chart?" to
            "Ask for the chart in plain language. GS answers with a native, interactive chart — tap any bar, point or slice to inspect its value.",
        "Can GS work offline?" to
            "Yes, if you have installed an on-device model. Open Settings → AI Intelligence and choose On-device.",
        "Where are my conversations stored?" to
            "In this app's private storage on your phone. Export or erase them from Settings → Privacy & Security.",
        "Why does a diagram show as source?" to
            "A diagram that cannot be parsed is shown honestly as source rather than as a broken picture. Copy it or ask GS to re-render it."
    )
    var open by remember { mutableStateOf<Int?>(0) }

    RefPage(
        title = "Help & Support",
        subtitle = "Answers first, a human second.",
        onBack = onBack
    ) {
        RefCard(padding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RefArtwork(RefArt.Conversation, size = 84.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("How can we help?", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Text(
                        "Start with the short answers below. If they miss, write to us — a person reads it.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary
                    )
                }
            }
        }

        RefSection(label = "Common questions") {
            faqs.forEachIndexed { index, (question, answer) ->
                val isOpen = open == index
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = { open = if (isOpen) null else index }
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .animateContentSize()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            question,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            if (isOpen) GsIcons.ChevronUp else GsIcons.ChevronDown,
                            contentDescription = null,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (isOpen) {
                        Spacer(Modifier.height(6.dp))
                        Text(answer, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                    }
                }
                if (index != faqs.lastIndex) RefDivider(startIndent = 14.dp)
            }
        }

        Spacer(Modifier.height(20.dp))
        RefPrimaryButton(
            label = "Contact support",
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:contact@grapsee.agency"))
                            .putExtra(Intent.EXTRA_SUBJECT, "GS AI support")
                    )
                }
            },
            icon = GsIcons.Mail
        )
        Spacer(Modifier.height(10.dp))
        RefFootnote("Support replies come from a real inbox at contact@grapsee.agency.")
    }
}

// ---------------------------------------------------------------------------
// About
// ---------------------------------------------------------------------------

@Composable
fun AboutSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = GsTheme.colors
    var showLicenses by remember { mutableStateOf(false) }

    RefPage(
        title = "About GS AI",
        subtitle = "The product, the build and the credits.",
        onBack = onBack
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RefArtwork(RefArt.Bloom, size = 150.dp)
            Text("GS AI", style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
            Text(
                "Smarter conversations.\nA brighter you.",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            RefStatusPill("Version ${BuildConfig.VERSION_NAME}")
        }

        RefSection(label = "Product") {
            RefValueRow("Version", BuildConfig.VERSION_NAME, GsIcons.Info)
            RefDivider()
            RefValueRow("Build", BuildConfig.VERSION_CODE.toString(), GsIcons.Code)
            RefDivider()
            RefRow(
                title = "Website",
                subtitle = "grapsee.agency",
                icon = GsIcons.Globe,
                onClick = { GsLinks.openInApp(context, "https://grapsee.agency") }
            )
            RefDivider()
            RefRow(
                title = "Contact",
                subtitle = "contact@grapsee.agency",
                icon = GsIcons.Mail,
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:contact@grapsee.agency")))
                    }
                }
            )
        }

        RefSection(label = "Legal") {
            RefRow(
                title = "Open-source licences",
                subtitle = "Poppins, Kotlin, Compose, llama.cpp and more",
                icon = GsIcons.BookOpen,
                onClick = { showLicenses = true }
            )
            RefDivider()
            RefRow(
                title = "Privacy policy",
                subtitle = "How the app handles your data",
                icon = GsIcons.Lock,
                onClick = { GsLinks.openInApp(context, "https://grapsee.agency/privacy") }
            )
        }

        RefFootnote("GS AI is in early access. Features and copy change as the product learns.")
        Spacer(Modifier.height(16.dp))
        RefQuietButton("Back to settings", onBack)
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text("Open-source licences") },
            text = {
                Text(
                    "Poppins — Copyright 2020 The Poppins Project Authors, SIL Open Font License 1.1.\n\n" +
                        "Kotlin and Jetpack Compose — Apache License 2.0.\n\n" +
                        "llama.cpp — MIT License.\n\n" +
                        "OkHttp, Room, Coil and kotlinx — Apache License 2.0.\n\n" +
                        "Full texts ship in the repository's LICENSES directory."
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) { Text("Close") }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Small shared helpers
// ---------------------------------------------------------------------------

/** The installed model's human name, resolved across the whole catalogue. */
private fun installedModelLabel(): String {
    val id = ModelStore.installedModelId ?: return "On-device model"
    val catalog = listOf(ModelCatalog.MODEL_1_5B, ModelCatalog.MODEL_0_5B) + ModelCatalog.QUANT_SWEEP
    return catalog.firstOrNull { it.id == id }?.displayName ?: id
}
