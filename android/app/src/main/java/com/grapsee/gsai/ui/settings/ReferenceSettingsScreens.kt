package com.grapsee.gsai.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.BuildConfig
import com.grapsee.gsai.data.AccountStore
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.data.local.ModelCatalog
import com.grapsee.gsai.data.local.ModelStore
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
import com.grapsee.gsai.ui.components.LOTUS_BLEED_ALPHA
import com.grapsee.gsai.ui.components.RefNavRow
import com.grapsee.gsai.ui.components.RefRowDivider
import com.grapsee.gsai.ui.components.RefInlinePill
import com.grapsee.gsai.ui.components.RefTileStrip
import com.grapsee.gsai.ui.components.RefStripTile
import com.grapsee.gsai.ui.components.RefInfoCard

// ---------------------------------------------------------------------------
// Theme customisation
// ---------------------------------------------------------------------------

@Composable
fun ThemeCustomisationScreen(onBack: () -> Unit) {
    val colors = GsTheme.colors
    RefSettingsPage(
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

        RefSection(label = "Background style", role = GsGlassRole.CardLight) {
            Column(Modifier.padding(14.dp)) {
                RefSegmented(
                    listOf("Blurred", "Gradient", "Solid"),
                    SettingsStore.backgroundStyle,
                    { SettingsStore.updateBackgroundStyle(it) }
                )
            }
        }

        RefSection(label = "Accent", role = GsGlassRole.CardLight) {
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
// Notifications — superseded by NotificationsAlertsScreen.kt, which draws the
// notifications-alerts reference sheet and wires every switch to GsNotifier.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Privacy
// ---------------------------------------------------------------------------

@Composable
fun PrivacySettingsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit = {}) {
    val context = LocalContext.current
    val colors = GsTheme.colors

    // docs/reference/pending/privacy-data-controls.png — the mock's chrome is
    // the settings scaffold (22dp inset, well-style back), not RefPage, and its
    // four groups are this page's own, not the hub's Privacy & Data card.
    RefSettingsPage(
        title = "Privacy & Data Controls",
        subtitle = "Your data, your control",
        onBack = onBack,
    ) {
        RefCard(role = GsGlassRole.CardLight) {
            // The mock's blossom bleed, using the same shipped lotus photograph
            // and recipe as the Settings hub — no new raster is minted.
            Box(Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(com.grapsee.gsai.R.drawable.gs_home_lotus_new),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(LOTUS_BLEED_ALPHA),
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to colors.glassSurface,
                                0.45f to colors.glassSurface.copy(alpha = 0.55f),
                                1f to Color.Transparent,
                            )
                        ),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RefWellTile(GsIcons.Shield, GsWellTone.Peach, 56.dp)
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
        }

        // ---- Data & Permissions ------------------------------------------
        RefGroupHeader("Data & Permissions", "Manage what Nexly can access")
        RefCard(role = GsGlassRole.CardLight) {
            RefNavRow(
                title = "App Permissions",
                // The manifest grants only these; naming camera or location
                // would be copy the build cannot back.
                subtitle = "Microphone, notifications, Do-Not-Disturb and biometric unlock",
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
                // Reachable now: the screen states the same truth in full.
                onClick = { onNavigate(GsRoutes.SETTINGS_INTEGRATIONS) },
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
                // The owner page can now choose a period and auto-delete; the
                // hub row points at it instead of stating a policy that no
                // longer matches what the app does.
                subtitle = "Choose how long we keep your data",
                onClick = { onNavigate(GsRoutes.SETTINGS_DATA) },
                leading = { RefWellTile(GsIcons.Clock, GsWellTone.Rose, 28.dp) },
            )
        }

        // ---- Export & Deletion -------------------------------------------
        RefGroupHeader("Export & Deletion", "Download or delete your data")
        RefCard(role = GsGlassRole.CardLight) {
            RefNavRow(
                title = "Export Your Data",
                subtitle = "Download a copy of your information",
                onClick = { onNavigate(GsRoutes.SETTINGS_DATA) },
                leading = { RefWellTile(GsIcons.Download, GsWellTone.Blue, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "Delete Your Account",
                // The detail page owns the destructive flow (its consent gate
                // and its format choice); this row is the way there.
                subtitle = "Permanently remove everything this app stores on this device",
                onClick = { onNavigate(GsRoutes.SETTINGS_DATA) },
                leading = { RefWellTile(GsIcons.Trash2, GsWellTone.Rose, 28.dp) },
            )
        }

        RefInfoCard(
            title = "We respect your privacy",
            text = "Your data is private to this app, never sold, and only used to improve your experience.",
            icon = GsIcons.Lock,
        )
        Spacer(Modifier.height(10.dp))
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

    RefSettingsPage(
        title = "Chat & Conversations",
        subtitle = "How new chats behave.",
        onBack = onBack
    ) {
        RefSection(label = "Response mode", role = GsGlassRole.CardLight) {
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

        RefSection(label = "History", role = GsGlassRole.CardLight) {
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

        RefSection(label = "Data", role = GsGlassRole.CardLight) {
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
    val local = SettingsStore.preferLocal
    var confirmReset by remember { mutableStateOf(false) }

    RefSettingsPage(
        title = "Model Selection & Reasoning Effort",
        subtitle = "Choose the best model and how deeply Nexly thinks",
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
                tone = GsWellTone.Peach,
                selected = SettingsStore.preferredModel == MODEL_PRO,
                badge = "Recommended",
                onClick = { SettingsStore.updatePreferredModel(MODEL_PRO) },
            )
            RefRowDivider()
            ModelChoiceRow(
                title = "Nexly Advanced",
                subtitle = "Deep reasoning, complex problem solving and analysis. Ideal for research, coding and detailed tasks.",
                icon = GsIcons.Sparkle,
                tone = GsWellTone.Lilac,
                selected = SettingsStore.preferredModel == MODEL_ADVANCED,
                onClick = { SettingsStore.updatePreferredModel(MODEL_ADVANCED) },
            )
            RefRowDivider()
            ModelChoiceRow(
                title = "Nexly Code",
                subtitle = "Specialized for programming and technical tasks. Great for developers and code-related work.",
                icon = GsIcons.Code,
                tone = GsWellTone.Mint,
                selected = SettingsStore.preferredModel == MODEL_CODE,
                onClick = { SettingsStore.updatePreferredModel(MODEL_CODE) },
            )
            RefRowDivider()
            ModelChoiceRow(
                title = "Nexly Creative",
                subtitle = "Focused on imagination, visuals and creative content. Best for design, writing and artistic tasks.",
                icon = GsIcons.Palette,
                tone = GsWellTone.Rose,
                selected = SettingsStore.preferredModel == MODEL_CREATIVE,
                onClick = { SettingsStore.updatePreferredModel(MODEL_CREATIVE) },
            )
        }

        // The radios persist a preference, but the backend ignores a
        // client-chosen model (conversations/[id]/route.ts:20). One line keeps
        // the tick from implying routing it cannot control.
        RefFootnote("One model is currently served; this preference is kept for when routing supports per-model choice.")

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
                    .height(IntrinsicSize.Min)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                REASONING_EFFORTS.forEachIndexed { index, effort ->
                    if (index > 0) {
                        // The mock separates the four tiles with hairlines, not
                        // just gaps.
                        Box(
                            Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(colors.glassBorder)
                        )
                    }
                    ReasoningTile(
                        title = effort.label,
                        description = effort.description,
                        timing = effort.timing,
                        icon = effort.icon,
                        tone = effort.tone,
                        isSelected = SettingsStore.reasoningEffort == effort.id,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { SettingsStore.updateReasoningEffort(effort.id) },
                    )
                }
            }
        }

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
            ResetToDefaultRow { confirmReset = true }
        }

        // The mock's closing card, in its own position after Model Preferences.
        RefCard(role = GsGlassRole.CardLight) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RefArtwork(RefArt.Success, size = 46.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Smarter choices. Better conversations.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GsFonts.Product,
                        color = colors.textPrimary,
                    )
                    Text(
                        "The right model + the right thinking = the best Nexly experience.",
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textTertiary,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))

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
                // Informational: no destination, so no chevron and no press.
                onClick = null
            )
        }

        RefFootnote("On-device generation is slower and uses battery. It is the honest choice when the network is gone, not a silent replacement for the cloud model.")
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset to Nexly's recommended settings?") },
            text = { Text("The model returns to Nexly Pro, reasoning effort to Standard, and \"Remember my choices\" turns back on.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    SettingsStore.updatePreferredModel(MODEL_PRO)
                    SettingsStore.updateRememberChoices(true)
                    SettingsStore.updateReasoningEffort("standard")
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
        )
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
    val tone: GsWellTone,
)

private val REASONING_EFFORTS = listOf(
    ReasoningEffort("minimal", "Minimal", "Quick answers for simple questions.", "~ 1–2 seconds", GsIcons.Zap, GsWellTone.Peach),
    ReasoningEffort("light", "Light", "Balanced speed and depth.", "~ 2–5 seconds", GsIcons.Leaf, GsWellTone.Mint),
    ReasoningEffort("standard", "Standard", "Best for most tasks and questions.", "~ 5–10 seconds", GsIcons.Sparkles, GsWellTone.Peach),
    ReasoningEffort("high", "High", "Deeper analysis and detailed reasoning.", "~ 10–30 seconds", GsIcons.Brain, GsWellTone.Lilac),
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
    tone: GsWellTone,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = GsTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) colors.accentSoft else colors.glassSurface.copy(alpha = 0.45f),
                RoundedCornerShape(16.dp),
            )
            // The mock outlines the chosen tile as well as tinting it; the
            // fill alone vanished against the card on the first capture.
            .then(
                if (isSelected) {
                    Modifier.border(1.5.dp, colors.accent.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            )
            .clickable(role = Role.RadioButton, onClickLabel = title, onClick = onClick)
            // The radio state is announced, not only painted; a screen reader
            // must be able to tell which of the four is chosen.
            .semantics { selected = isSelected }
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RefWellTile(icon, tone, 26.dp)
        Spacer(Modifier.height(4.dp))
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
            fontSize = 8.sp,
            lineHeight = 10.5.sp,
            fontFamily = GsFonts.Product,
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
            // The mock's tiles carry a 2-line description; the smaller size
            // and 2dp side padding are what keep the whole copy inside them.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Equal-height tiles put every timing line and radio on one baseline,
        // the way the mock draws them; without the weighted spacer the High
        // tile (four description lines) pushed its radio below the other three.
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(2.dp))
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
                .border(
                    1.5.dp,
                    if (isSelected) colors.accent else colors.textTertiary.copy(alpha = 0.30f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Box(Modifier.size(7.dp).background(colors.accent, CircleShape))
            }
        }
    }
}

/** "Reset to default" — a real reset behind the repo's confirm-dialog pattern. */
@Composable
private fun ResetToDefaultRow(onClick: () -> Unit) {
    RefRow(
        title = "Reset to default",
        subtitle = "Go back to Nexly's recommended settings.",
        icon = GsIcons.RotateCcw,
        onClickLabel = "Reset to default",
        onClick = onClick,
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
    tone: GsWellTone,
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RefWellTile(icon, tone, 28.dp)
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
        // The mock marks the chosen MODEL with a check in a soft well; the
        // four-across effort tiles keep radios. Both are drawn, so the two
        // selection idioms are not interchangeable.
        Box(
            Modifier
                .size(24.dp)
                .background(
                    if (selected) colors.accentSoft else Color.Transparent,
                    CircleShape,
                )
                .then(
                    if (selected) {
                        Modifier
                    } else {
                        Modifier.border(1.5.dp, colors.textTertiary.copy(alpha = 0.30f), CircleShape)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    GsIcons.Check,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(14.dp),
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
    RefSettingsPage(
        title = "Voice & Speech",
        subtitle = "Listen, speak, and keep your hands free.",
        onBack = onBack
    ) {
        RefCard(role = GsGlassRole.CardLight, padding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
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

        RefSection(label = "Voice", role = GsGlassRole.CardLight) {
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

        RefFootnote("Speech is synthesized locally on this device. Audio is not uploaded to GS.")
    }
}

// ---------------------------------------------------------------------------
// Accessibility
// ---------------------------------------------------------------------------

@Composable
fun AccessibilitySettingsScreen(onBack: () -> Unit) {
    val colors = GsTheme.colors
    val scale = SettingsStore.fontScale
    RefSettingsPage(
        title = "Accessibility",
        subtitle = "Make GS comfortable for the way you read and move.",
        onBack = onBack
    ) {
        RefSection(label = "Visual", role = GsGlassRole.CardLight) {
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

        RefSection(label = "Motion", role = GsGlassRole.CardLight) {
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
// Language
// ---------------------------------------------------------------------------

/**
 * Language is a real page, not a relabelled Appearance. The app ships one
 * interface language, answers follow the language the reader writes in (the
 * server prompt already says so), and the voice is the device's synthesizer —
 * so the rows state those facts, and the one thing the reader can change here
 * opens the system's own speech settings.
 */
@Composable
fun LanguageSettingsScreen(
    onBack: () -> Unit,
    /** Injectable so the JVM suite can prove the row is wired without a device. */
    onOpenSpeechSettings: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val deviceLanguage = java.util.Locale.getDefault().toLanguageTag()

    RefSettingsPage(
        title = "Language",
        subtitle = "What GS speaks, and what it listens for.",
        onBack = onBack
    ) {
        RefSection(label = "Interface", role = GsGlassRole.CardLight) {
            RefValueRow("App language", "English (UK)", GsIcons.Globe)
        }
        RefFootnote(
            "The interface ships in English (UK). More languages arrive only " +
                "once the copy is translated, not before."
        )

        RefSection(label = "Replies", role = GsGlassRole.CardLight) {
            RefValueRow("Answer language", "Match my message", GsIcons.MessageCircle)
        }
        RefFootnote(
            "GS answers in the language you write in, so a mixed conversation " +
                "stays readable."
        )

        RefSection(label = "Voice", role = GsGlassRole.CardLight) {
            RefValueRow("Device language", deviceLanguage, GsIcons.AudioLines)
            RefDivider()
            RefRow(
                title = "Speech settings",
                subtitle = "Open the system text-to-speech settings",
                icon = GsIcons.Sliders,
                onClick = {
                    val open = onOpenSpeechSettings ?: {
                        runCatching {
                            context.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                        }.onFailure {
                            runCatching {
                                context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                            }
                        }
                        Unit
                    }
                    open()
                }
            )
        }
        RefFootnote(
            "The voice is the device's own synthesizer. Its language and accent " +
                "are set in the system, not in GS."
        )
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

    RefSettingsPage(
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

        RefSection(label = "Product", role = GsGlassRole.CardLight) {
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

        RefSection(label = "Legal", role = GsGlassRole.CardLight) {
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
