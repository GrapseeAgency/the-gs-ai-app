package com.grapsee.gsai.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grapsee.gsai.data.ModelRouter
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.data.local.ConversationPreviewRow
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefArt
import com.grapsee.gsai.ui.components.RefArtwork
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.LocalGsIsDark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.Calendar

/**
 * "Start a New Chat" — docs/reference/pending/home-new-chat.png.
 *
 * The screen a reader lands on before a conversation exists: the companion
 * header, the hero bloom, the composer, the six starting tools, the recent
 * chats and the way into Model Selection.
 *
 * Every element is wired:
 *  - the composer sends what is typed (and computes the wire mode through
 *    [ModelRouter], so Auto Select and the effort control are read here);
 *  - each tool chip seeds the composer with that tool's own prompt, the same
 *    rule Explore uses — only the trip planner has a screen of its own;
 *  - the recent rows are the real active conversations, newest first, with
 *    their real previews and times;
 *  - "Explore AI Models" opens [com.grapsee.gsai.ui.settings.ModelSelectionScreen];
 *  - the top-right control opens the drawer, because this page — like the
 *    reference — carries no dock, and an app with a drawer cannot ship without
 *    one way to reach it.
 */
@Composable
fun NewChatScreen(
    onStartChat: (prompt: String, autoSend: Boolean) -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenAllChats: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenExplore: () -> Unit,
    /** The reader's clock; the capture harness pins it so "2h ago" is stable. */
    now: Long = System.currentTimeMillis(),
) {
    val colors = GsTheme.colors
    val dark = LocalGsIsDark.current
    var draft by remember { mutableStateOf("") }
    val rows by rememberConversationRows().collectAsStateWithLifecycle(initialValue = emptyList())

    Box(Modifier.fillMaxSize()) {
        if (!dark && SettingsStore.backgroundStyle != "Solid") {
            Image(
                painter = androidx.compose.ui.res.painterResource(com.grapsee.gsai.R.drawable.gs_home_backdrop),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            CompanionHeader(onOpenMenu)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                RefArtwork(RefArt.Bloom, size = 112.dp, contentDescription = "GS companion")
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.Text(
                    "Start a New Chat",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GsFonts.Product,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.Text(
                    "Ask anything, get instant answers,\nor explore something new.",
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textTertiary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Spacer(Modifier.height(10.dp))
            Composer(
                draft = draft,
                onDraft = { draft = it },
                onSend = {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        startChat(text, autoSend = true, onStartChat)
                        draft = ""
                    }
                },
                onAttach = { onStartChat("", false) },
                onImage = { onStartChat("", false) },
                onCreate = onOpenExplore,
                onMore = onOpenExplore,
            )
            Spacer(Modifier.height(10.dp))
            ToolGrid(onOpenExplore) { prompt -> startChat(prompt, autoSend = false, onStartChat) }

            RecentHeader(onOpenAllChats)
            val recent = rows.take(4)
            if (recent.isEmpty()) {
                androidx.compose.material3.Text(
                    "No conversations yet — your first one starts above.",
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    recent.forEach { row ->
                        RecentRow(row, now) { onOpenConversation(row.conversation.id) }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            ExploreModelsCard(onOpenModels)
            Spacer(Modifier.height(22.dp))
        }
    }
}

/**
 * The send path's one decision: Auto Select ON means the model is chosen for
 * the message and its effort follows; OFF means the reader's effort wins.
 * Written before navigating so the next reply is the mode the reader saw.
 */
private fun startChat(prompt: String, autoSend: Boolean, onStartChat: (String, Boolean) -> Unit) {
    if (prompt.isNotBlank()) {
        val mode = ModelRouter.modeForMessage(
            text = prompt,
            hasImage = false,
            preferred = com.grapsee.gsai.data.GsModel.byId(SettingsStore.preferredModel),
            effort = SettingsStore.reasoningEffort,
            autoSelect = SettingsStore.autoSelectModel,
        )
        SettingsStore.setChatMode(null, mode)
    }
    onStartChat(prompt, autoSend)
}

@Composable
private fun rememberConversationRows(): Flow<List<ConversationPreviewRow>> =
    remember { recentConversationsForNewChat() }

private fun recentConversationsForNewChat(): Flow<List<ConversationPreviewRow>> =
    runCatching { ServiceLocator.db.conversationDao().observeActiveWithPreview() }
        .getOrElse { flowOf(emptyList()) }

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun CompanionHeader(onOpenMenu: () -> Unit) {
    val colors = GsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = androidx.compose.ui.res.painterResource(com.grapsee.gsai.R.drawable.gs_home_avatar),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            androidx.compose.material3.Text(
                "Nexly",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GsFonts.Product,
                color = colors.textPrimary,
            )
            androidx.compose.material3.Text(
                "Your AI companion.\nAlways.",
                fontSize = 9.5.sp,
                lineHeight = 12.sp,
                fontFamily = GsFonts.Product,
                color = colors.textSecondary,
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(36.dp)
                .gsGlassCircle()
                .testTag("new-chat-menu")
                .clickable(role = Role.Button, onClickLabel = "Open menu", onClick = onOpenMenu),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                GsIcons.UserRound,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun Modifier.gsGlassCircle(): Modifier = this
    .background(Color.White.copy(alpha = 0.50f), CircleShape)
    .border(1.dp, Color.White.copy(alpha = 0.75f), CircleShape)

// ---------------------------------------------------------------------------
// Composer
// ---------------------------------------------------------------------------

@Composable
private fun Composer(
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onImage: () -> Unit,
    onCreate: () -> Unit,
    onMore: () -> Unit,
) {
    val colors = GsTheme.colors
    RefCard(
        role = GsGlassRole.CardLight,
        shape = RoundedCornerShape(22.dp),
        padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box {
            if (draft.isEmpty()) {
                androidx.compose.material3.Text(
                    "Type your message...",
                    fontSize = 10.5.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textTertiary,
                )
            }
            BasicTextField(
                value = draft,
                onValueChange = onDraft,
                textStyle = TextStyle(
                    fontSize = 10.5.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textPrimary,
                ),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 20.dp)
                    .testTag("new-chat-input"),
                maxLines = 4,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ComposerButton(GsIcons.Paperclip, "Attach a file", onAttach)
            Spacer(Modifier.width(8.dp))
            ComposerButton(GsIcons.Image, "Attach an image", onImage)
            Spacer(Modifier.width(8.dp))
            ComposerButton(GsIcons.Sparkles, "Create", onCreate)
            Spacer(Modifier.width(8.dp))
            ComposerButton(GsIcons.MoreHorizontal, "More", onMore)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(38.dp)
                    .testTag("new-chat-send")
                    .background(
                        if (draft.isBlank()) colors.accent.copy(alpha = 0.35f) else colors.accent,
                        CircleShape,
                    )
                    .clickable(enabled = draft.isNotBlank(), role = Role.Button, onClickLabel = "Send", onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    GsIcons.ArrowRight,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ComposerButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val colors = GsTheme.colors
    Box(
        Modifier
            .size(32.dp)
            .gsGlassCircle()
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            icon,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(14.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Tool grid
// ---------------------------------------------------------------------------

private data class StartTool(
    val label: String,
    val prompt: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tone: GsWellTone,
)

private val START_TOOLS = listOf(
    StartTool("Web Search", "Search the web for ", GsIcons.Globe, GsWellTone.Lilac),
    StartTool("Code", "Write code that ", GsIcons.Code, GsWellTone.Blue),
    StartTool("Write", "Help me write ", GsIcons.Pencil, GsWellTone.Peach),
    StartTool("Analyze", "Analyze this for me: ", GsIcons.ChartBar, GsWellTone.Mint),
    StartTool("Image", "Create an image of ", GsIcons.Image, GsWellTone.Rose),
)

@Composable
private fun ToolGrid(onMore: () -> Unit, onTool: (String) -> Unit) {
    // Exactly the mock's three rows of two: five tools, then "More".
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        START_TOOLS.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                pair.forEach { tool ->
                    ToolChip(tool.label, tool.icon, tool.tone) { onTool(tool.prompt) }
                }
                if (pair.size == 1) {
                    ToolChip("More", GsIcons.Grid, GsWellTone.Lilac, onMore)
                }
            }
        }
    }
}

@Composable
private fun RowScope.ToolChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tone: GsWellTone,
    onClick: () -> Unit,
) {
    val colors = GsTheme.colors
    Row(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 40.dp)
            .background(Color.White.copy(alpha = 0.42f), RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.62f), RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RefWellTile(icon, tone, 24.dp)
        Spacer(Modifier.width(9.dp))
        androidx.compose.material3.Text(
            label,
            fontSize = 9.sp,
            fontFamily = GsFonts.Product,
            color = colors.textPrimary,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// Recent rows + model entry
// ---------------------------------------------------------------------------

/** The mock's section header sits tighter than the sheets' 18pt top. */
@Composable
private fun RecentHeader(onViewAll: () -> Unit) {
    val colors = GsTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Text(
            "Recent Chats",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = GsFonts.Product,
            color = colors.textPrimary,
        )
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(role = Role.Button, onClickLabel = "View all", onClick = onViewAll),
        ) {
            androidx.compose.material3.Text(
                "View all",
                fontSize = 9.sp,
                fontFamily = GsFonts.Product,
                color = colors.textTertiary,
            )
            Spacer(Modifier.width(4.dp))
            androidx.compose.material3.Icon(
                GsIcons.ArrowRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun RecentRow(row: ConversationPreviewRow, now: Long, onClick: () -> Unit) {
    val colors = GsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recent-row-${row.conversation.id}")
            .background(Color.White.copy(alpha = 0.46f), RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.62f), RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClickLabel = row.conversation.title, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RefWellTile(topicIcon(row), topicTone(row), 28.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            androidx.compose.material3.Text(
                row.conversation.title.ifBlank { "Untitled chat" },
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = GsFonts.Product,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!row.lastMessage.isNullOrBlank()) {
                Spacer(Modifier.height(1.dp))
                androidx.compose.material3.Text(
                    startPreview(row.lastMessage.orEmpty()),
                    fontSize = 7.5.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.Text(
            startTimeLabel(row.conversation.updatedAt, now),
            fontSize = 7.5.sp,
            fontFamily = GsFonts.Product,
            color = colors.textTertiary,
            modifier = Modifier.widthIn(min = 34.dp),
        )
        Spacer(Modifier.width(4.dp))
        androidx.compose.material3.Icon(
            GsIcons.ChevronRight,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(13.dp),
        )
    }
}

@Composable
private fun ExploreModelsCard(onClick: () -> Unit) {
    val colors = GsTheme.colors
    val dark = LocalGsIsDark.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("explore-models")
            .background(
                if (dark) colors.accentSoft.copy(alpha = 0.55f) else GsWellTone.Lilac.wash.copy(alpha = 0.55f),
                RoundedCornerShape(18.dp),
            )
            .border(1.dp, Color.White.copy(alpha = 0.62f), RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClickLabel = "Explore AI Models", onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RefWellTile(GsIcons.Sparkles, GsWellTone.Lilac, 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            androidx.compose.material3.Text(
                "Explore AI Models",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = GsFonts.Product,
                color = colors.textPrimary,
            )
            androidx.compose.material3.Text(
                "Choose the right model for your needs.",
                fontSize = 8.sp,
                fontFamily = GsFonts.Product,
                color = colors.textTertiary,
                maxLines = 1,
            )
        }
        androidx.compose.material3.Icon(
            GsIcons.ChevronRight,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(13.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Formatting
// ---------------------------------------------------------------------------

/**
 * The row's icon is the same shipped topic classifier the drawer and Chat
 * History use, so "Code Review Help" draws a code mark and a travel thread
 * draws a sprout — the variety the mock draws, from real titles.
 */
private fun topicIcon(row: ConversationPreviewRow) =
    com.grapsee.gsai.ui.navigation.drawer.drawerIcon(
        row.conversation.title,
        row.conversation.pinned,
    )

private fun topicTone(row: ConversationPreviewRow): GsWellTone =
    when (com.grapsee.gsai.ui.navigation.drawer.drawerTopic(row.conversation.title)) {
        com.grapsee.gsai.ui.navigation.drawer.DrawerTopic.Code -> GsWellTone.Blue
        com.grapsee.gsai.ui.navigation.drawer.DrawerTopic.Data -> GsWellTone.Mint
        com.grapsee.gsai.ui.navigation.drawer.DrawerTopic.Money -> GsWellTone.Mint
        com.grapsee.gsai.ui.navigation.drawer.DrawerTopic.Travel -> GsWellTone.Rose
        com.grapsee.gsai.ui.navigation.drawer.DrawerTopic.Learning,
        com.grapsee.gsai.ui.navigation.drawer.DrawerTopic.Science -> GsWellTone.Peach
        else -> GsWellTone.Lilac
    }

/** The stored stamp is UTC ISO; the label is elapsed time, never a canned one. */
internal fun startTimeLabel(iso: String, now: Long): String {
    val then = runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull() ?: return ""
    val delta = (now - then).coerceAtLeast(0L)
    val minutes = delta / 60_000
    val hours = delta / 3_600_000
    val days = delta / 86_400_000
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> {
            val cal = Calendar.getInstance().apply { timeInMillis = then }
            "%d/%d".format(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1)
        }
    }
}

internal fun startPreview(raw: String, limit: Int = 70): String =
    raw.replace(Regex("[#*_`>\\[\\]]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .let { if (it.length > limit) it.take(limit).trimEnd() + "..." else it }
