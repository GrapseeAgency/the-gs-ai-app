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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.BuildConfig
import com.grapsee.gsai.data.AccountStore
import com.grapsee.gsai.data.DataExport
import com.grapsee.gsai.data.SessionStore
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.data.local.ModelCatalog
import com.grapsee.gsai.data.local.ModelStore
import com.grapsee.gsai.data.notifications.ReplyNotifier
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.GsGlassRole
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
fun PrivacySettingsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit = {}) {
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

    // docs/reference/pending/privacy-data-controls.png — the mock's chrome is
    // the settings scaffold (22dp inset, well-style back), not RefPage, and its
    // four groups are this page's own, not the hub's Privacy & Data card.
    RefSettingsPage(
        title = "Privacy & Data Controls",
        subtitle = "Your data, your control",
        onBack = onBack,
    ) {
        RefCard(role = GsGlassRole.CardLight) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RefArtwork(RefArt.Privacy, size = 62.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Your privacy matters",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GsFonts.Product,
                        color = colors.textPrimary,
                    )
                    Text(
                        "Nexly is designed with your privacy in mind. You're always in control of your data, conversations, and personal information.",
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textTertiary,
                    )
                }
            }
        }

        // ---- Data & Permissions ------------------------------------------
        RefGroupHeader("Data & Permissions", "Manage what Nexly can access")
        RefCard(role = GsGlassRole.CardLight) {
            RefNavRow(
                title = "App Permissions",
                // The manifest grants only these two; naming camera or location
                // would be copy the build cannot back.
                subtitle = "Microphone and notifications — nothing else is granted",
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            )
                        )
                    }
                },
                leading = { RefWellTile(GsIcons.Camera, GsWellTone.Lilac, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "Personalization & Ads",
                // No ad profile and no ad SDK: a chevron here would promise a
                // destination that does not exist.
                subtitle = "No ad profile is built from your activity",
                onClick = null,
                // No chevron: a static row must not promise a page.
                trailing = {},
                leading = { RefWellTile(GsIcons.UserRound, GsWellTone.Mint, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "Third-Party Integrations",
                subtitle = "No services are connected",
                onClick = null,
                // No chevron: a static row must not promise a page.
                trailing = {},
                leading = { RefWellTile(GsIcons.Paperclip, GsWellTone.Blue, 28.dp) },
            )
        }

        // ---- Conversation & Activity -------------------------------------
        RefGroupHeader("Conversation & Activity", "Control your chat data")
        RefCard(role = GsGlassRole.CardLight) {
            RefNavRow(
                title = "Chat History",
                subtitle = "View, search, and manage your conversations",
                onClick = { onNavigate(GsRoutes.CHAT_SEARCH) },
                leading = { RefWellTile(GsIcons.MessageCircle, GsWellTone.Peach, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "Search History",
                subtitle = "Searches are not recorded",
                onClick = null,
                // No chevron: a static row must not promise a page.
                trailing = {},
                leading = { RefWellTile(GsIcons.Search, GsWellTone.Lilac, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "Data Retention",
                subtitle = "Kept on this device until you delete it",
                onClick = null,
                // No chevron: a static row must not promise a page.
                trailing = {},
                leading = { RefWellTile(GsIcons.Clock, GsWellTone.Rose, 28.dp) },
            )
        }

        // ---- Export & Deletion -------------------------------------------
        RefGroupHeader("Export & Deletion", "Download or delete your data")
        RefCard(role = GsGlassRole.CardLight) {
            RefNavRow(
                title = "Export Your Data",
                subtitle = "Download a copy of your information",
                onClick = { export.launch("gs-ai-export.json") },
                leading = { RefWellTile(GsIcons.Download, GsWellTone.Blue, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "Delete Your Account",
                // There is no server account to delete; the honest verb is what
                // this actually erases.
                subtitle = "Permanently remove everything this app stores on this device",
                onClick = { confirmClear = true },
                leading = { RefWellTile(GsIcons.Trash2, GsWellTone.Rose, 28.dp) },
            )
        }

        RefInfoCard(
            text = "Your data is private to this app, never sold, and only used to improve your experience.",
            icon = GsIcons.Lock,
        )
        Spacer(Modifier.height(10.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete your data on this device?") },
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
    var selectedModel by remember { mutableStateOf(MODEL_PRO) }

    RefPage(
        title = "Model Selection & Reasoning Effort",
        subtitle = "Choose the best model and how deeply Nexly thinks.",
        onBack = onBack
    ) {
        // ---- AI Model -----------------------------------------------------
        // docs/reference/pending/model-selection.png. The four rows, their copy
        // and the Recommended chip come from that mock verbatim.
        RefGroupHeader("AI Model", "Different models for different needs")
        RefCard(role = GsGlassRole.CardLight) {
            ModelChoiceRow(
                title = "Nexly Pro",
                subtitle = "Best balance of speed, intelligence and creativity. Great for most everyday tasks.",
                icon = GsIcons.Sparkles,
                selected = selectedModel == MODEL_PRO,
                badge = "Recommended",
                onClick = { selectedModel = MODEL_PRO },
            )
            RefRowDivider()
            ModelChoiceRow(
                title = "Nexly Advanced",
                subtitle = "Deep reasoning, complex problem solving and analysis. Ideal for research, coding and detailed tasks.",
                icon = GsIcons.Sparkle,
                selected = selectedModel == MODEL_ADVANCED,
                onClick = { selectedModel = MODEL_ADVANCED },
            )
            RefRowDivider()
            ModelChoiceRow(
                title = "Nexly Code",
                subtitle = "Specialized for programming and technical tasks. Great for developers and code-related work.",
                icon = GsIcons.Code,
                selected = selectedModel == MODEL_CODE,
                onClick = { selectedModel = MODEL_CODE },
            )
            RefRowDivider()
            ModelChoiceRow(
                title = "Nexly Creative",
                subtitle = "Focused on imagination, visuals and creative content. Best for design, writing and artistic tasks.",
                icon = GsIcons.Palette,
                selected = selectedModel == MODEL_CREATIVE,
                onClick = { selectedModel = MODEL_CREATIVE },
            )
        }

        // The radios persist a preference, but the backend ignores a
        // client-chosen model (conversations/[id]/route.ts:20). Saying so here is
        // cheaper than letting the tick imply routing it cannot control.
        RefInfoCard(
            text = "One model is currently served. This preference is kept for when routing supports per-model choice.",
            icon = GsIcons.Info,
        )
        Spacer(Modifier.height(10.dp))

        // ---- Reasoning Effort ---------------------------------------------
        // The mock draws FOUR ACROSS with a radio under each. Order, copy and
        // timings are its own; the selection is SettingsStore.reasoningEffort,
        // never chatMode (Minimal and Light share the `flash` wire mode, so
        // deriving from chatMode would light two tiles at once).
        RefGroupHeader("Reasoning Effort", "Control how much Nexly thinks")
        RefCard(role = GsGlassRole.CardLight) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                REASONING_EFFORTS.forEach { effort ->
                    ReasoningTile(
                        title = effort.label,
                        description = effort.description,
                        timing = effort.timing,
                        icon = effort.icon,
                        selected = SettingsStore.reasoningEffort == effort.id,
                        modifier = Modifier.weight(1f),
                        onClick = { SettingsStore.updateReasoningEffort(effort.id) },
                    )
                }
            }
        }

        RefInfoCard(
            text = "Higher reasoning effort helps Nexly provide more thoughtful, detailed and accurate responses — especially for complex questions.",
            icon = GsIcons.Sparkles,
        )
        Spacer(Modifier.height(10.dp))

        // ---- Model Preferences -------------------------------------------
        RefGroupHeader("Model Preferences", "Customize your experience")
        RefCard(role = GsGlassRole.CardLight) {
            // The mock draws this switch OFF. It is off and DISABLED because the
            // backend always chooses the model — a tappable switch that changes
            // nothing is the failure this repo keeps recording.
            RefToggleRow(
                title = "Auto-select model",
                subtitle = "Nexly always chooses the best model for each task.",
                icon = GsIcons.Settings,
                checked = false,
                onCheckedChange = {},
                enabled = false,
            )
            RefRowDivider()
            RefToggleRow(
                title = "Remember my choices",
                subtitle = "Keep using your preferred model and reasoning effort.",
                icon = GsIcons.SlidersHorizontal,
                checked = SettingsStore.rememberChoices,
                onCheckedChange = { SettingsStore.updateRememberChoices(it) },
            )
            RefRowDivider()
            ResetToDefaultRow()
        }

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

private const val MODEL_PRO = "pro"
private const val MODEL_ADVANCED = "advanced"
private const val MODEL_CODE = "code"
private const val MODEL_CREATIVE = "creative"

/**
 * The mock's four effort tiles, in its order, with its copy and timings.
 *
 * `id` is what [SettingsStore.reasoningEffort] stores; [SettingsStore.wireModeForEffort]
 * turns it into the only three modes the backend accepts.
 */
private data class ReasoningEffort(
    val id: String,
    val label: String,
    val description: String,
    val timing: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val REASONING_EFFORTS = listOf(
    ReasoningEffort("minimal", "Minimal", "Quick answers for simple questions.", "~ 1–2 seconds", GsIcons.Zap),
    ReasoningEffort("light", "Light", "Balanced speed and depth.", "~ 2–5 seconds", GsIcons.Zap),
    ReasoningEffort("standard", "Standard", "Best for most tasks and questions.", "~ 5–10 seconds", GsIcons.Sparkles),
    ReasoningEffort("high", "High", "Deeper analysis and detailed reasoning.", "~ 10–30 seconds", GsIcons.Brain),
)

/**
 * One four-across effort tile: well, title, two-line description, timing, radio.
 *
 * Built here rather than from RefChoiceTile because that composable has no timing
 * line and marks selection with a top-right tick where the mock puts a radio at
 * the bottom.
 */
@Composable
private fun ReasoningTile(
    title: String,
    description: String,
    timing: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = GsTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) colors.accentSoft else colors.glassSurface.copy(alpha = 0.45f),
                RoundedCornerShape(16.dp),
            )
            .clickable(role = Role.RadioButton, onClickLabel = title, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RefWellTile(icon, GsWellTone.Peach, 26.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = GsFonts.Product,
            color = colors.textPrimary,
            maxLines = 1,
        )
        Text(
            description,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontFamily = GsFonts.Product,
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            timing,
            fontSize = 9.sp,
            fontFamily = GsFonts.Product,
            color = colors.textTertiary,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .size(16.dp)
                .background(
                    if (selected) colors.accent else colors.textTertiary.copy(alpha = 0.30f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(6.dp).background(colors.surface, CircleShape))
            }
        }
    }
}

/** "Reset to default" — a real reset, not a navigation to another page. */
@Composable
private fun ResetToDefaultRow() {
    RefRow(
        title = "Reset to default",
        subtitle = "Go back to Nexly's recommended settings.",
        icon = GsIcons.RotateCcw,
        onClickLabel = "Reset to default",
        onClick = {
            SettingsStore.updatePreferredModel(MODEL_PRO)
            SettingsStore.updateRememberChoices(true)
            SettingsStore.updateReasoningEffort("standard")
        },
    )
}

/**
 * One row of the mock's AI Model list: well, title (+ optional chip) and its two
 * detail lines, with the selection ring on the right.
 *
 * The copy and the Recommended chip are the mock's own
 * (docs/reference/pending/model-selection.png).
 */
@Composable
private fun ModelChoiceRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    badge: String? = null,
) {
    val colors = GsTheme.colors
    // Built here rather than with RefRow for three reasons the mock insists on:
    // the Recommended chip sits beside the title (RefRow only takes a String
    // title), the unselected ring must be visible (glassBorder vanishes against
    // the card), and the two detail lines must WRAP rather than elide — RefRow's
    // subtitle is maxLines = 2, which cut the mock's own words to "….".
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = title, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RefWellTile(icon, GsWellTone.Peach, 28.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = GsFonts.Product,
                    color = colors.textPrimary,
                    maxLines = 1,
                )
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        badge,
                        fontSize = 10.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.accent,
                        modifier = Modifier
                            .background(colors.accentSoft, RoundedCornerShape(999.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                subtitle,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                fontFamily = GsFonts.Product,
                color = colors.textTertiary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(20.dp)
                .background(
                    if (selected) colors.accent else colors.textTertiary.copy(alpha = 0.30f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(colors.surface, CircleShape)
                )
            }
        }
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
        RefCard(role = GsGlassRole.CardLight) {
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
        RefCard(role = GsGlassRole.CardLight) {
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
        RefCard(role = GsGlassRole.CardLight) {
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
        RefCard(role = GsGlassRole.CardLight) {
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
