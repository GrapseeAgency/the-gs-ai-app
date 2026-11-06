package com.grapsee.gsai.ui.chat.content

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.gsGlass
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.GsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import android.graphics.BitmapFactory
import com.grapsee.gsai.ui.icons.GsIcons

/**
 * STEP 5 renderer — the consumer half of the message → blocks seam.
 * [BlocksContent] owns the per-bubble stream parse cache (the incremental
 * successor of the Step-4 segment cache: frozen blocks keep their instances
 * across ~30 Hz flushes, so Compose skips every completed block and only the
 * live tail recomposes), then renders each block document-style.
 */

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

/**
 * perf-smooth-3 (emulator 08/09/10/11/12 -- long-scroll, fling, rich renders):
 * every transcript item that left the viewport disposed its StreamBlockCache,
 * so re-entering it during a fling re-ran the FULL markdown parse on the main
 * thread -- per item, per viewport re-entry, exactly when frames matter most.
 *
 * The parse result of a finished (non-streaming) message is a pure function of
 * its content string, so it is memoized process-wide. A fling past 50 messages
 * now pays the parse once per distinct content, then O(1) lookups forever.
 * Streaming content bypasses this (it mutates in place and the per-composition
 * cache already handles the incremental path).
 */
private object BlockMemo {
    private const val MAX_CACHED_CHARS = 24_000
    private val lru = android.util.LruCache<String, List<Block>>(96)

    fun parse(content: String): List<Block> {
        if (content.isEmpty() || content.length > MAX_CACHED_CHARS) return parseBlocks(content)
        lru.get(content)?.let { return it }
        val blocks = parseBlocks(content)
        lru.put(content, blocks)
        return blocks
    }
}

private class StreamBlockCache {
    var content: String = ""
    var blocks: List<Block> = emptyList()

    fun update(newContent: String, streaming: Boolean): List<Block> {
        val next = if (streaming) {
            parseStreamingBlocks(content, blocks, newContent)
        } else {
            BlockMemo.parse(newContent)
        }
        content = newContent
        blocks = next
        return next
    }
}

@Composable
fun BlocksContent(
    content: String,
    isStreaming: Boolean,
    onCopyCode: (String) -> Unit,
    /**
     * PHASE 8.1: ordinal → source URL for THIS turn, built from the turn's
     * REAL persisted sources (live event cards before done). A [N] citation
     * marker in the text becomes a tappable chip when N resolves here; a
     * marker without a matching source renders as the plain literal text it
     * always was (honest fallback, no dead taps).
     */
    citationUrls: Map<Int, String> = emptyMap(),
    /**
     * 0.78.0: "send this prompt" wiring for interactive widgets (action
     * cards, follow-up chips, form results). Null renders those as inert
     * cards instead of dead taps.
     */
    onSendPrompt: ((String) -> Unit)? = null,
    /**
     * 0.81.3 widget recovery. The turn's user prompt and the previous
     * assistant turn. When the model answers a "show me a chart" request with
     * prose (a text-only model literally cannot know it is talking to an app
     * that renders), [WidgetRecovery] rebuilds the visual from numbers the
     * answer already stated. Empty strings disable recovery.
     */
    userPrompt: String = "",
    priorContext: String = "",
    /**
     * Nexly glass transcript: consecutive prose blocks become one glass card,
     * a plan block becomes its own sibling card, and [statusCard] is appended
     * inside the first prose card (the reference's Thinking/Reasoning/Effort
     * panel below the preamble). Other call sites keep the document surface.
     */
    glassAssistant: Boolean = false,
    statusCard: (@Composable () -> Unit)? = null,
    /**
     * Plan turns put the status card after the whole preamble (the reference).
     * A non-plan answer puts it after the first paragraph, so a long answer
     * cannot push the card under the composer.
     */
    statusAtEnd: Boolean = true,
    planTimestamp: String? = null,
    /** Conversation that owns the plan, for durable checklist progress. */
    planConversationId: String? = null
) {
    val cache = remember { StreamBlockCache() }
    // Recovery must be decided BEFORE parsing, because a successful recovery
    // also removes the model's false "I can't render" sentence from the prose.
    val recovered = remember(content, isStreaming, userPrompt, priorContext) {
        if (isStreaming || userPrompt.isBlank() || WidgetRecovery.hasWidgetFence(content)) null
        else WidgetRecovery.recover(content, userPrompt, priorContext)
    }
    val shown = remember(content, recovered) {
        if (recovered == null) content else WidgetRecovery.stripFalseRefusal(content)
    }
    val parsed = remember(shown, isStreaming) { cache.update(shown, isStreaming) }
    val blocks = remember(parsed, recovered) {
        if (recovered == null) parsed else parsed + recovered
    }
    // THE ANSWER TEXT IS SELECTABLE — but ONLY the prose. SelectionContainer
    // installs its own gesture that competes with (and can win over) a child's
    // per-datum pointer input, so wrapping the WHOLE column meant tapping a
    // chart/bar/slice started selecting the entire message instead of reading
    // the datum. That is the "tapping an interactive renders the whole chat"
    // defect. Prose blocks keep selection; every interactive block (widgets,
    // charts, code cards, mermaid, collapsibles, citation chips, tool results)
    // renders OUTSIDE the container and owns its own taps.
    if (glassAssistant) {
        NexlyAssistantBlocks(
            blocks = blocks,
            isStreaming = isStreaming,
            onCopyCode = onCopyCode,
            citationUrls = citationUrls,
            onSendPrompt = onSendPrompt,
            statusCard = statusCard,
            statusAtEnd = statusAtEnd,
            planTimestamp = planTimestamp,
            planConversationId = planConversationId
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isStreaming && blocks.isEmpty()) {
                StreamingCaret()
            }
            StructuredBlockSequenceView(
                blocks = blocks,
                isStreaming = isStreaming,
                onCopyCode = onCopyCode,
                citationUrls = citationUrls,
                onSendPrompt = onSendPrompt,
                statusCard = statusCard,
                statusAtEnd = statusAtEnd,
                onStatusPlaced = {}
            )
        }
    }
}

private sealed interface NexlyGroup {
    data class Prose(val blocks: List<Block>) : NexlyGroup
    data class Plan(val block: Block.PlanBlock) : NexlyGroup
}

/**
 * Compose the assistant turn the way the reference does: one glass card for
 * the preamble (with the live Thinking panel nested after it), then one glass
 * card for the timetable. A plan card is never nested inside the prose card.
 */
@Composable
private fun NexlyAssistantBlocks(
    blocks: List<Block>,
    isStreaming: Boolean,
    onCopyCode: (String) -> Unit,
    citationUrls: Map<Int, String>,
    onSendPrompt: ((String) -> Unit)?,
    statusCard: (@Composable () -> Unit)?,
    statusAtEnd: Boolean,
    planTimestamp: String?,
    planConversationId: String?
) {
    val groups = remember(blocks, statusCard != null) {
        val out = mutableListOf<NexlyGroup>()
        val prose = mutableListOf<Block>()
        fun flush() {
            if (prose.isNotEmpty()) {
                out += NexlyGroup.Prose(prose.toList())
                prose.clear()
            }
        }
        for (block in blocks) {
            if (block is Block.PlanBlock) {
                flush()
                out += NexlyGroup.Plan(block)
            } else {
                prose += block
            }
        }
        flush()
        if (statusCard != null && out.firstOrNull() !is NexlyGroup.Prose) {
            out.add(0, NexlyGroup.Prose(emptyList()))
        }
        out
    }
    var statusPlaced = false
    // The sheet keeps the assistant cards narrower than the phone: about 69%
    // of the device width, so the pearl backdrop stays visible at the right.
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    // The card used to be hard-capped at 286dp on EVERY phone. On a 393dp
    // device that left a 28dp dead column at the right, and on a 411dp one 46dp:
    // the answer stopped short of the question above it, and the action row
    // underneath ran wider than the card it belonged to. A card is as wide as
    // the reading column; only a tablet needs a cap.
    val cardMax = if (wide) 520.dp else 2000.dp
    val planMax = if (wide) 540.dp else 2000.dp
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        groups.forEach { group ->
            // ONE identity mark per turn, not two. This row used to draw its own
            // 28dp orb AND the transcript row above drew a 38dp avatar, so every
            // answer began with two sparkles at different sizes and different
            // offsets — the clearest "assembled by two people" tell on the page.
            // The transcript owns identity (it knows the turn grouping); the
            // blocks own content.
            Box(modifier = Modifier.fillMaxWidth()) {
                    when (group) {
                        is NexlyGroup.Plan -> NexlyPlanCard(
                            plan = group.block.plan,
                            open = group.block.open,
                            fallbackTimestamp = planTimestamp,
                            modifier = Modifier.widthIn(max = planMax),
                            planConversationId = planConversationId
                        )
                        is NexlyGroup.Prose -> CompositionLocalProvider(
                            LocalNexlyProseStyle provides TextStyle(
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Normal,
                                color = NexlyInk.prose
                            ),
                            LocalNexlyCodeWrap provides true
                        ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = cardMax)
                                .fillMaxWidth()
                                .nexlyGlassCard(20.dp)
                                .padding(horizontal = 13.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (group.blocks.isEmpty() && isStreaming) {
                                if (statusCard == null) StreamingCaret()
                            }
                            StructuredBlockSequenceView(
                                blocks = group.blocks,
                                isStreaming = isStreaming,
                                onCopyCode = onCopyCode,
                                citationUrls = citationUrls,
                                onSendPrompt = onSendPrompt,
                                statusCard = if (!statusPlaced) statusCard else null,
                                statusAtEnd = statusAtEnd,
                                onStatusPlaced = { statusPlaced = true }
                            )
                            if (!statusPlaced && statusCard != null && group.blocks.isEmpty()) {
                                statusPlaced = true
                                statusCard()
                            }
                        }
                        }
                    }
                }
        }
        if (isStreaming && blocks.isEmpty() && groups.isEmpty()) {
            StreamingCaret()
        }
    }
}

private enum class SectionListMode { DEFAULT, CHECKMARK, TODO, SOURCES }

private val LocalSectionListMode = androidx.compose.runtime.compositionLocalOf { SectionListMode.DEFAULT }

private fun classifyCalloutHeaderTitle(raw: String): Pair<CalloutTone, String>? {
    val clean = raw.trim().trimEnd(':').trim()
    val lower = clean.lowercase()
    return when {
        lower in setOf("important", "warning", "caution", "attention", "important notice") ->
            CalloutTone.WARNING to "⚠️"
        lower in setOf("quick summary", "summary", "tl;dr", "tldr", "executive summary") ->
            CalloutTone.SUMMARY to "⚡"
        lower in setOf("key insight", "key insights", "quick fact", "quick facts", "pro tip", "tip", "note", "key takeaway", "key takeaways") ->
            CalloutTone.FACT to "💡"
        else -> null
    }
}

private fun classifySectionListMode(headingText: String): SectionListMode {
    val lower = headingText.trim().lowercase()
    return when {
        "next action" in lower || "action item" in lower || "todo" in lower || "to-do" in lower || "checklist" in lower || lower == "tasks" || "your tasks" in lower ->
            SectionListMode.TODO
        "key source" in lower || lower == "sources" || "trusted source" in lower ->
            SectionListMode.SOURCES
        "priorit" in lower || "feature" in lower || "highlight" in lower || "benefit" in lower || "why " in lower ->
            SectionListMode.CHECKMARK
        else -> SectionListMode.DEFAULT
    }
}

@Composable
private fun StructuredBlockSequenceView(
    blocks: List<Block>,
    isStreaming: Boolean,
    onCopyCode: (String) -> Unit,
    citationUrls: Map<Int, String>,
    onSendPrompt: ((String) -> Unit)?,
    statusCard: (@Composable () -> Unit)?,
    statusAtEnd: Boolean,
    onStatusPlaced: () -> Unit
) {
    // 1. Detect if this response is a multi-section dashboard (Screenshot 1)
    val dashboardTabs = remember(blocks) {
        val tabs = mutableListOf("Dashboard")
        val hasTable = blocks.any { it is Block.TableBlock || (it is Block.WidgetBlock && it.kind == "table") }
        val hasTodo = blocks.any {
            (it is Block.BulletList && it.items.any { item -> item.checked != null }) ||
                (it is Block.Heading && classifySectionListMode(plainTextOfSpans(it.spans)) == SectionListMode.TODO) ||
                (it is Block.WidgetBlock && it.kind == "checklist")
        }
        val hasCalc = blocks.any {
            (it is Block.WidgetBlock && it.kind == "calc") ||
                (it is Block.Heading && "calculator" in plainTextOfSpans(it.spans).lowercase())
        }
        val hasCode = blocks.any { it is Block.CodeBlock }
        if (hasTable) tabs += "Table"
        if (hasTodo) tabs += "Todo"
        if (hasCalc) tabs += "Calculator"
        else if (hasCode && tabs.size >= 3) tabs += "Code"
        if (tabs.size >= 3) tabs else emptyList()
    }
    var activeDashboardTab by remember(blocks.size) { mutableStateOf("Dashboard") }

    if (dashboardTabs.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFEDE6FF))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dashboardTabs.forEach { tab ->
                val selected = activeDashboardTab == tab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selected) Color.White.copy(alpha = 0.92f) else Color.Transparent)
                        .clickable { activeDashboardTab = tab }
                        .padding(horizontal = 11.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = tab,
                        fontSize = 11.5.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) Color(0xFF4F35E8) else Color(0xFF564E7A)
                    )
                }
            }
        }
    }

    val statusHostIndex = if (statusAtEnd) blocks.lastIndex else 0
    var currentSectionMode = SectionListMode.DEFAULT
    var i = 0
    while (i < blocks.size) {
        val block = blocks[i]
        val nextBlock = blocks.getOrNull(i + 1)

        // Filter by dashboard tab when user selects Table / Todo / Calculator / Code
        val skipForTab = when (activeDashboardTab) {
            "Table" -> block !is Block.TableBlock && !(block is Block.WidgetBlock && block.kind == "table") &&
                !(block is Block.Heading && ("table" in plainTextOfSpans(block.spans).lowercase() || "product" in plainTextOfSpans(block.spans).lowercase() || "language" in plainTextOfSpans(block.spans).lowercase()))
            "Todo" -> !(block is Block.BulletList) && !(block is Block.WidgetBlock && block.kind == "checklist") &&
                !(block is Block.Heading && classifySectionListMode(plainTextOfSpans(block.spans)) == SectionListMode.TODO)
            "Calculator" -> !(block is Block.WidgetBlock && block.kind == "calc") &&
                !(block is Block.Heading && "calculator" in plainTextOfSpans(block.spans).lowercase())
            "Code" -> block !is Block.CodeBlock
            else -> false
        }

        // Coalesce Heading("Quick Calculator" | "Calculator") + Paragraph/BulletList into interactive NexlyQuickCalculatorCard (Screenshot 1)
        if (!skipForTab && block is Block.Heading) {
            val hTitle = plainTextOfSpans(block.spans).trim()
            if ("calculator" in hTitle.lowercase() && (nextBlock is Block.Paragraph || nextBlock is Block.BulletList)) {
                val bodyText = when (nextBlock) {
                    is Block.Paragraph -> plainTextOfSpans(nextBlock.spans)
                    is Block.BulletList -> nextBlock.items.joinToString(" ") { plainTextOfSpans(it.spans) }
                    else -> ""
                }
                if (bodyText.isNotBlank()) {
                    val nums = Regex("\\d+(?:\\.\\d+)?").findAll(bodyText).mapNotNull { it.value.toDoubleOrNull() }.toList()
                    val amt = nums.firstOrNull { it >= 50.0 } ?: 1200.0
                    val disc = nums.firstOrNull { it > 0.0 && it < 50.0 } ?: 10.0
                    key("calc:${block.sourceStart}") {
                        NexlyQuickCalculatorCard(title = hTitle, initialAmount = amt, initialDiscount = disc)
                    }
                    if (statusCard != null && (i == statusHostIndex || i + 1 == statusHostIndex)) {
                        onStatusPlaced()
                        Spacer(Modifier.height(2.dp))
                        statusCard()
                    }
                    i += 2
                    continue
                }
            }
        }

        // Coalesce Heading("Quick summary" | "Important" | "Key Insights" | "Quick Fact") + Paragraph/BulletList into one NexlyCalloutCard!
        val calloutHeadingMatch = when (block) {
            is Block.Heading -> {
                val hText = plainTextOfSpans(block.spans).trim()
                classifyCalloutHeaderTitle(hText)?.let { Triple(hText.trimEnd(':'), it.first, it.second) }
            }
            is Block.Paragraph -> {
                if (block.spans.size == 1 && block.spans.first() is InlineSpan.BoldSpan) {
                    val bText = plainTextOfSpans(block.spans).trim()
                    classifyCalloutHeaderTitle(bText)?.let { Triple(bText.trimEnd(':'), it.first, it.second) }
                } else null
            }
            else -> null
        }

        if (!skipForTab && calloutHeadingMatch != null && (nextBlock is Block.Paragraph || nextBlock is Block.BulletList)) {
            val (titleText, tone, icon) = calloutHeadingMatch
            val bodySpans = if (nextBlock is Block.Paragraph) nextBlock.spans else emptyList()
            key("callout:${block.sourceStart}") {
                NexlyCalloutCard(
                    callout = CalloutParsed(
                        tone = tone,
                        icon = icon,
                        title = titleText,
                        bodySpans = bodySpans
                    ),
                    citationUrls = citationUrls,
                    extraBlocks = if (nextBlock is Block.BulletList) {
                        {
                            BulletListBlock(
                                block = nextBlock,
                                selectable = !isStreaming,
                                citationUrls = citationUrls
                            )
                        }
                    } else null
                )
            }
            if (statusCard != null && (i == statusHostIndex || i + 1 == statusHostIndex)) {
                onStatusPlaced()
                Spacer(Modifier.height(2.dp))
                statusCard()
            }
            i += 2
            continue
        }

        if (block is Block.Heading) {
            currentSectionMode = classifySectionListMode(plainTextOfSpans(block.spans))
        } else if (block !is Block.BulletList && block !is Block.OrderedList) {
            currentSectionMode = SectionListMode.DEFAULT
        }

        if (!skipForTab) {
            val modeForBlock = currentSectionMode
            key(streamBlockKey(block, i)) {
                CompositionLocalProvider(LocalSectionListMode provides modeForBlock) {
                    val blockContent: @Composable () -> Unit = {
                        BlockView(
                            block = block,
                            isLast = i == blocks.lastIndex,
                            isStreaming = isStreaming,
                            onCopyCode = onCopyCode,
                            citationUrls = citationUrls,
                            onSendPrompt = onSendPrompt
                        )
                    }
                    if (block.isProseBlock() && modeForBlock != SectionListMode.TODO && modeForBlock != SectionListMode.SOURCES) {
                        SelectionContainer { blockContent() }
                    } else {
                        blockContent()
                    }
                }
            }
        }
        if (statusCard != null && i == statusHostIndex) {
            onStatusPlaced()
            Spacer(Modifier.height(2.dp))
            statusCard()
        }
        i += 1
    }

    // Bottom Contextual Action Pills (Screenshots 1–6)
    if (!isStreaming && blocks.size >= 2) {
        val endsWithActionWidget = blocks.lastOrNull().let {
            it is Block.WidgetBlock && it.kind in setOf("actions", "followups", "chips", "menu")
        }
        if (!endsWithActionWidget) {
            NexlyContextualActionPillsRow(
                blocks = blocks,
                onCopyCode = onCopyCode,
                onSendPrompt = onSendPrompt
            )
        }
    }
}

@Composable
private fun NexlyContextualActionPillsRow(
    blocks: List<Block>,
    onCopyCode: (String) -> Unit,
    onSendPrompt: ((String) -> Unit)?
) {
    val actions = remember(blocks) {
        val hasChart = blocks.any { it is Block.WidgetBlock && it.kind == "chart" }
        val hasTable = blocks.any { it is Block.TableBlock || (it is Block.WidgetBlock && it.kind == "table") }
        val hasCode = blocks.any { it is Block.CodeBlock }
        val hasSources = blocks.any {
            it is Block.CitationsBlock ||
                (it is Block.Heading && classifySectionListMode(plainTextOfSpans(it.spans)) == SectionListMode.SOURCES)
        }
        val hasPlanOrSteps = blocks.any {
            it is Block.OrderedList ||
                (it is Block.BulletList && it.items.any { item -> item.checked != null }) ||
                (it is Block.Heading && ("plan" in plainTextOfSpans(it.spans).lowercase() || "step" in plainTextOfSpans(it.spans).lowercase()))
        }
        when {
            hasChart && hasTable && hasCode -> listOf(
                "💬" to "Ask follow-up",
                "📊" to "Show me a detailed report",
                "✦" to "Create a presentation"
            )
            hasChart -> listOf(
                "⤓" to "Download Report",
                "⤢" to "View Details"
            )
            hasTable -> listOf(
                "📊" to "View Full Report",
                "🔗" to "Share"
            )
            hasSources -> listOf(
                "↗" to "View All Sources",
                "📋" to "Copy Citations",
                "📖" to "Learn More"
            )
            hasCode -> listOf(
                "</>" to "Edit Code",
                "⤓" to "Download Project",
                "💬" to "Ask Follow Up"
            )
            hasPlanOrSteps -> listOf(
                "📅" to "Make a timeline",
                "⤢" to "Expand plan",
                "🔗" to "Share"
            )
            else -> emptyList()
        }
    }
    if (actions.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        actions.forEach { (icon, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.82f))
                    .border(1.dp, Color(0xFFE1DAFA), RoundedCornerShape(999.dp))
                    .clickable {
                        if (label.contains("Copy", ignoreCase = true) || label.contains("Share", ignoreCase = true)) {
                            onCopyCode(
                                extractCopyableBlockSummary(
                                    blocks = blocks,
                                    citationsOnly = label.contains("Citation", ignoreCase = true)
                                )
                            )
                        } else {
                            onSendPrompt?.invoke(label)
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = icon,
                    fontSize = 11.5.sp,
                    color = Color(0xFF5B3DF5),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 5.dp)
                )
                Text(
                    text = label,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF28204E)
                )
            }
        }
    }
}

private fun extractCopyableBlockSummary(blocks: List<Block>, citationsOnly: Boolean): String {
    val lines = mutableListOf<String>()
    for (block in blocks) {
        when (block) {
            is Block.CitationsBlock -> {
                block.citations.forEach { c ->
                    lines += "[${c.number}] ${c.title} — ${c.url.ifBlank { c.domain }}"
                }
            }
            is Block.Heading -> if (!citationsOnly) lines += plainTextOfSpans(block.spans)
            is Block.Paragraph -> if (!citationsOnly) lines += plainTextOfSpans(block.spans)
            is Block.BulletList -> {
                block.items.forEach { item ->
                    val t = plainTextOfSpans(item.spans)
                    if (!citationsOnly || "http" in t || "www." in t) {
                        lines += "• $t"
                    }
                }
            }
            is Block.OrderedList -> {
                block.items.forEachIndexed { idx, item ->
                    val n = item.number ?: (block.start + idx)
                    val t = plainTextOfSpans(item.spans)
                    if (!citationsOnly || "http" in t || "www." in t) {
                        lines += "$n. $t"
                    }
                }
            }
            is Block.CodeBlock -> if (!citationsOnly) lines += block.code
            else -> {}
        }
    }
    return lines.joinToString("\n").trim().ifEmpty { "Copied from Nexly" }
}

/**
 * Whether a block is prose the reader may select. Interactive blocks must be
 * FALSE: selection and a control's own gesture both want the same pointer, and
 * a chart that cannot be tapped is worse than text that cannot be selected.
 */
private fun Block.isProseBlock(): Boolean = when (this) {
    is Block.Paragraph,
    is Block.Heading,
    is Block.BulletList,
    is Block.OrderedList,
    is Block.BlockQuote,
    is Block.TableBlock,
    is Block.MathBlock -> true
    else -> false
}

/**
 * O(1) sibling identity for one top-level block, stable across ~30 Hz stream
 * flushes. Content blocks own a unique, strictly increasing [Block.sourceStart]
 * — a real byte offset into the message (lists included, never a fake 0) — so
 * the same block keeps the same key while it grows in place and Compose REUSES
 * its node instead of replacing it. Metadata seams carry no source position;
 * they are keyed by kind + index (defensive: the streaming path never emits
 * them).
 *
 * This replaces `key(block)` — a data-class key whose hashCode/equals walk the
 * ENTIRE span tree of EVERY block on EVERY flush, an O(document) tax that no
 * amount of parser incrementality could hide, and which also replaced every
 * node at finalize (fresh instances → unequal keys → full subtree churn).
 */
private fun streamBlockKey(block: Block, index: Int): Any = when (block) {
    is Block.ToolResultBlock -> "tool:$index"
    is Block.CitationsBlock -> "citations"
    else -> block.sourceStart
}

@Composable
private fun BlockView(
    block: Block,
    isLast: Boolean,
    isStreaming: Boolean,
    onCopyCode: (String) -> Unit,
    citationUrls: Map<Int, String>,
    onSendPrompt: ((String) -> Unit)? = null
) {
    when (block) {
        is Block.Paragraph -> ParagraphBlock(block, showCaret = isStreaming && isLast, selectable = !isStreaming, citationUrls = citationUrls)
        is Block.Heading -> HeadingBlock(block, isFirst = false, citationUrls = citationUrls)
        is Block.BulletList -> BulletListBlock(block, selectable = !isStreaming, citationUrls = citationUrls)
        is Block.OrderedList -> OrderedListBlock(block, selectable = !isStreaming, citationUrls = citationUrls)
        is Block.BlockQuote -> BlockQuoteView(block, selectable = !isStreaming, citationUrls = citationUrls)
        is Block.CodeBlock -> {
            // A plain fence may secretly be a picture: ASCII bars, a data
            // grid, a dated log, percentages. When it parses as one of those
            // it renders as the native widget directly — no tabs, no
            // placeholder, nothing for the user to configure.
            val visual = remember(block.code) {
                if (block.open || isStreaming) null
                else {
                    val lang = block.language?.lowercase()
                    if (lang.isNullOrEmpty() || lang in setOf("text", "txt", "plain", "ascii", "art")) visualFromAscii(block.code)
                    else null
                }
            }
            if (visual != null) {
                WidgetView(visual, onSendPrompt)
            } else {
                CodeBlockCard(
                    language = block.language,
                    code = block.code,
                    open = block.open || isStreaming,
                    onCopyCode = onCopyCode
                )
            }
        }
        is Block.TableBlock -> TableBlockView(block, selectable = !isStreaming, citationUrls = citationUrls)
        is Block.Divider -> DividerBlock()
        is Block.MathBlock -> MathBlockView(block)
        is Block.MermaidBlock -> MermaidBlockView(block, streaming = isStreaming, onCopyCode = onCopyCode)
        is Block.HtmlBlock -> HtmlBlockView(block, streaming = isStreaming)
        is Block.WidgetBlock -> WidgetBlockView(block, streaming = isStreaming, onSendPrompt = onSendPrompt)
        is Block.PlanBlock -> NexlyPlanCard(block.plan, open = block.open)
        is Block.ImageBlock -> ImageBlockView(block)
        is Block.CollapsibleBlock -> CollapsibleBlockView(block, citationUrls)
        is Block.CitationsBlock -> CitationsBlockView(block)
        is Block.ToolResultBlock -> ToolResultBlockView(block)
    }
}

// ---------------------------------------------------------------------------
// Inline spans → AnnotatedString
// ---------------------------------------------------------------------------

private data class SpanTheme(
    val codeBg: Color,
    val linkColor: Color,
    val textColor: Color,
    val caretColor: Color,
    val fontSize: androidx.compose.ui.unit.TextUnit,
    /** PHASE 8.1: citation ordinal → source URL (empty map = all markers literal). */
    val citations: Map<Int, String> = emptyMap(),
    /**
     * TASK 1: tap routing for every link annotation — in-app browser via
     * GsLinks (external only on the explicit long-press paths).
     */
    val openLink: (String) -> Unit = {}
)

@Composable
private fun rememberSpanTheme(
    defaultFontSize: androidx.compose.ui.unit.TextUnit,
    citationUrls: Map<Int, String> = emptyMap(),
    textColor: Color? = null
): SpanTheme {
    val context = androidx.compose.ui.platform.LocalContext.current
    remember(context) { NexlyIcons.ensureLoaded(context.applicationContext) }
    return SpanTheme(
        codeBg = MaterialTheme.colorScheme.surfaceContainerHighest,
        linkColor = MaterialTheme.colorScheme.primary,
        textColor = textColor ?: MaterialTheme.colorScheme.onSurface,
        caretColor = MaterialTheme.colorScheme.primary,
        fontSize = defaultFontSize,
        citations = citationUrls,
        openLink = { url -> com.grapsee.gsai.ui.components.GsLinks.openInApp(context, url) }
    )
}

private fun appendNexlyText(
    builder: AnnotatedString.Builder,
    text: String,
    theme: SpanTheme
) {
    for (part in NexlyIcons.inlineParts(text)) {
        if (part.isIcon) {
            builder.withStyle(
                SpanStyle(
                    fontFamily = NexlyIcons.family,
                    fontSize = theme.fontSize * 1.14f,
                    color = theme.textColor
                )
            ) { append(NexlyIcons.glyph(part.value)) }
        } else {
            builder.append(part.value)
        }
    }
}

private fun appendSpans(builder: AnnotatedString.Builder, spans: List<InlineSpan>, theme: SpanTheme) {
    for (span in spans) {
        when (span) {
            is InlineSpan.TextSpan -> appendNexlyText(builder, span.text, theme)
            is InlineSpan.BoldSpan -> builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendSpans(builder, span.children, theme)
            }
            is InlineSpan.ItalicSpan -> builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendSpans(builder, span.children, theme)
            }
            is InlineSpan.StrikeSpan -> builder.withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                appendSpans(builder, span.children, theme)
            }
            is InlineSpan.CodeSpan -> builder.withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = theme.codeBg, fontSize = theme.fontSize * 0.88f)
            ) { builder.append(span.code) }
            is InlineSpan.LinkSpan -> builder.withLink(
                LinkAnnotation.Url(
                    span.url,
                    TextLinkStyles(style = SpanStyle(color = theme.linkColor, textDecoration = TextDecoration.Underline)),
                    // TASK 1: tap opens the IN-APP browser. External open is the
                    // long-press path (GsLinks), never a plain tap.
                    linkInteractionListener = { _ -> theme.openLink(span.url) }
                )
            ) { builder.append(span.text) }
            is InlineSpan.CitationSpan -> {
                // PHASE 8.1: [N] resolves against the turn's real sources → a
                // small superscript-style tappable chip that opens the source
                // URL (LinkAnnotation.Url routes through the platform handler,
                // same as ordinary links). No matching source → literal "[N]"
                // text, byte-identical to the pre-8.1 rendering: a citation
                // that cannot be verified is never dressed up as one.
                val url = theme.citations[span.number]
                if (url == null) {
                    builder.append("[${span.number}]")
                } else {
                    builder.withLink(
                        LinkAnnotation.Url(
                            url,
                            TextLinkStyles(style = citationChipStyle(theme)),
                            // TASK 1: inline citation chips route through the
                            // in-app browser too — same tap contract as links.
                            linkInteractionListener = { _ -> theme.openLink(url) }
                        )
                    ) {
                        withStyle(citationChipStyle(theme)) { builder.append(span.number.toString()) }
                    }
                }
            }
            is InlineSpan.MathSpan -> {
                val rendered = renderMath(span.latex, theme.fontSize, theme.textColor)
                if (rendered != null) builder.append(rendered) else builder.append(span.latex)
            }
        }
    }
}

/** Small superscript-style chip look for a RESOLVED citation marker. */
private fun citationChipStyle(theme: SpanTheme): SpanStyle = SpanStyle(
    color = theme.linkColor,
    background = theme.linkColor.copy(alpha = 0.10f),
    fontSize = theme.fontSize * 0.78f,
    baselineShift = BaselineShift(0.18f),
    fontWeight = FontWeight.SemiBold
)

private fun spansToAnnotatedString(spans: List<InlineSpan>, theme: SpanTheme, showCaret: Boolean = false): AnnotatedString =
    buildAnnotatedString {
        appendSpans(this, spans, theme)
        // The kinetic caret rides the LAST LINE of the live paragraph —
        // appending the styled glyph tracks the wrapped text automatically.
        if (showCaret) {
            withStyle(SpanStyle(color = theme.caretColor)) { append("▍") }
        }
    }

// ---------------------------------------------------------------------------
// Streaming caret (moved verbatim from the Step-4 ChatScreen prose renderer)
// ---------------------------------------------------------------------------

@Composable
private fun StreamingCaret() {
    if (SettingsStore.reduceMotion) {
        Text(
            text = "▍",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 2.dp)
        )
        return
    }
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "caret")
    val alpha by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(durationMillis = GsMotion.CARET_PULSE_MS),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "caretAlpha"
    )
    Text(
        text = "▍",
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 2.dp)
            .graphicsLayer { this.alpha = alpha }
    )
}

// ---------------------------------------------------------------------------
// Prose blocks
// ---------------------------------------------------------------------------

private enum class CalloutTone { SUMMARY, WARNING, FACT }

private data class CalloutParsed(
    val tone: CalloutTone,
    val icon: String,
    val title: String,
    val bodySpans: List<InlineSpan>
)

private fun plainTextOfSpans(spans: List<InlineSpan>): String = buildString {
    spans.forEach { span ->
        when (span) {
            is InlineSpan.TextSpan -> append(span.text)
            is InlineSpan.CodeSpan -> append(span.code)
            is InlineSpan.MathSpan -> append(span.latex)
            is InlineSpan.CitationSpan -> append("[${span.number}]")
            is InlineSpan.BoldSpan -> append(plainTextOfSpans(span.children))
            is InlineSpan.ItalicSpan -> append(plainTextOfSpans(span.children))
            is InlineSpan.StrikeSpan -> append(plainTextOfSpans(span.children))
            is InlineSpan.LinkSpan -> append(span.text)
        }
    }
}

private fun detectCalloutFromSpans(spans: List<InlineSpan>): CalloutParsed? {
    if (spans.isEmpty()) return null
    val first = spans.first()
    if (first is InlineSpan.BoldSpan) {
        val rawTitle = plainTextOfSpans(first.children).trim().trimEnd(':').trim()
        val lower = rawTitle.lowercase()
        val toneAndIcon: Pair<CalloutTone, String>? = when {
            lower in setOf("important", "warning", "caution", "attention") ->
                CalloutTone.WARNING to "⚠️"
            lower in setOf("quick summary", "summary", "tl;dr", "tldr", "executive summary") ->
                CalloutTone.SUMMARY to "⚡"
            lower in setOf("key insight", "key insights", "quick fact", "pro tip", "tip", "note", "takeaway") ->
                CalloutTone.FACT to "💡"
            else -> null
        }
        if (toneAndIcon != null) {
            val rest = spans.drop(1).toMutableList()
            if (rest.isNotEmpty()) {
                val head = rest.first()
                if (head is InlineSpan.TextSpan) {
                    val cleaned = head.text.replace(Regex("^\\s*[:\\-—–]\\s*"), "")
                    if (cleaned.isEmpty()) rest.removeAt(0) else rest[0] = InlineSpan.TextSpan(cleaned)
                }
            }
            if (rest.isNotEmpty()) {
                return CalloutParsed(
                    tone = toneAndIcon.first,
                    icon = toneAndIcon.second,
                    title = rawTitle,
                    bodySpans = rest
                )
            }
        }
    }
    if (first is InlineSpan.TextSpan) {
        val m = Regex("^\\s*\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]\\s*(.*)$", RegexOption.IGNORE_CASE)
            .find(first.text)
        if (m != null) {
            val kind = m.groupValues[1].uppercase()
            val tail = m.groupValues[2]
            val rest = spans.drop(1).toMutableList()
            if (tail.isNotBlank()) rest.add(0, InlineSpan.TextSpan(tail))
            val isWarn = kind in setOf("IMPORTANT", "WARNING", "CAUTION")
            return CalloutParsed(
                tone = if (isWarn) CalloutTone.WARNING else CalloutTone.FACT,
                icon = if (isWarn) "⚠️" else "💡",
                title = if (kind == "IMPORTANT") "Important" else if (isWarn) "Warning" else "Key Insight",
                bodySpans = rest
            )
        }
    }
    return null
}

private fun sectionBadgeForHeadingText(raw: String): String? {
    val clean = raw.trim()
    if (clean.isEmpty()) return null
    val firstCode = clean.codePointAt(0)
    if (firstCode > 0x2190) return null
    val lower = clean.lowercase()
    return when {
        "priorit" in lower || "goal" in lower || "objective" in lower -> "🎯"
        "step-by-step" in lower || "steps" in lower || "plan" in lower || "roadmap" in lower -> "☰"
        "next action" in lower || "action item" in lower || "checklist" in lower || "todo" in lower -> "☑️"
        "key source" in lower || "sources" in lower -> "🔗"
        "reference" in lower || "citation" in lower || "further reading" in lower -> "📖"
        "feature" in lower || "highlight" in lower || "benefit" in lower -> "✦"
        lower.startsWith("top ") || "chart" in lower || "metric" in lower || "overview" in lower -> "📊"
        else -> null
    }
}

@Composable
private fun NexlyCalloutCard(
    callout: CalloutParsed,
    citationUrls: Map<Int, String>,
    extraBlocks: (@Composable () -> Unit)? = null
) {
    val isWarn = callout.tone == CalloutTone.WARNING
    val bgColor = if (isWarn) Color(0xFFFFF4E6) else Color(0xFFF2ECFF)
    val borderColor = if (isWarn) Color(0xFFF6C88F) else Color(0xFFD9CCFF)
    val iconBg = if (isWarn) Color(0xFFFDE3C2) else Color(0xFFE4D8FF)
    val titleColor = if (isWarn) Color(0xFF6B3206) else Color(0xFF211845)
    val bodyColor = if (isWarn) Color(0xFF5A3310) else Color(0xFF39325C)

    val bodyStyle = LocalNexlyProseStyle.current ?: MaterialTheme.typography.bodyMedium
    val theme = rememberSpanTheme(bodyStyle.fontSize, citationUrls, bodyColor)
    val bodyText = remember(callout.bodySpans, theme) {
        if (callout.bodySpans.isEmpty()) null else spansToAnnotatedString(callout.bodySpans, theme)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Text(text = callout.icon, fontSize = 14.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = callout.title,
                style = bodyStyle.copy(fontWeight = FontWeight.Bold),
                color = titleColor
            )
            if (bodyText != null && bodyText.isNotEmpty()) {
                Text(
                    text = bodyText,
                    style = bodyStyle,
                    color = bodyColor
                )
            }
            extraBlocks?.invoke()
        }
    }
}

@Composable
private fun ParagraphBlock(
    block: Block.Paragraph,
    showCaret: Boolean,
    selectable: Boolean,
    citationUrls: Map<Int, String>
) {
    if (block.spans.isEmpty()) return
    val callout = remember(block.spans) {
        if (showCaret) null else detectCalloutFromSpans(block.spans)
    }
    if (callout != null) {
        NexlyCalloutCard(callout = callout, citationUrls = citationUrls)
        return
    }
    val compact = LocalNexlyProseStyle.current
    val bodyStyle = compact ?: MaterialTheme.typography.bodyMedium
    val ink = compact?.color ?: MaterialTheme.colorScheme.onSurface
    val theme = rememberSpanTheme(bodyStyle.fontSize, citationUrls, ink)
    val styled = remember(block.spans, theme, showCaret) {
        spansToAnnotatedString(block.spans, theme, showCaret)
    }
    val text = @Composable {
        Text(
            text = styled,
            style = bodyStyle,
            color = ink
        )
    }
    if (selectable) SelectionContainer { text() } else text()
}

@Composable
private fun HeadingBlock(block: Block.Heading, isFirst: Boolean, citationUrls: Map<Int, String>) {
    val theme = rememberSpanTheme(MaterialTheme.typography.bodyMedium.fontSize, citationUrls)
    val styled = remember(block.spans, theme) { spansToAnnotatedString(block.spans, theme) }
    val badge = remember(block.spans, block.level) {
        if (block.level in 2..3) sectionBadgeForHeadingText(plainTextOfSpans(block.spans)) else null
    }
    val style = when (block.level) {
        1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        3 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        4 -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
        5 -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
        else -> MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Column {
        if (!isFirst && block.level <= 3) Spacer(Modifier.height(8.dp))
        SelectionContainer {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (badge != null) {
                    Text(
                        text = badge,
                        fontSize = if (block.level == 2) 15.sp else 14.sp,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                Text(
                    text = styled,
                    style = style,
                    color = if (block.level == 6) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
            }
        }
    }
}

@Composable
private fun NexlyQuickCalculatorCard(
    title: String,
    initialAmount: Double,
    initialDiscount: Double
) {
    val discounts = remember { listOf(0.0, 5.0, 10.0, 15.0, 20.0, 25.0, 30.0, 50.0) }
    var amount by remember(initialAmount) { mutableStateOf(initialAmount) }
    var discountIdx by remember(initialDiscount) {
        mutableStateOf(discounts.indexOf(initialDiscount).takeIf { it >= 0 } ?: 2)
    }
    val discountPct = discounts[discountIdx % discounts.size]
    val total = amount * (1.0 - discountPct / 100.0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.78f))
            .border(1.dp, Color(0xFFE2DAFA), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🧮", fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp))
                Text(
                    text = title.ifBlank { "Quick Calculator" },
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF211845)
                )
            }
            Text(
                text = "Clear",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF6C4CF6),
                modifier = Modifier.clickable {
                    amount = initialAmount
                    discountIdx = 2
                }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Amount", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.9f))
                        .border(1.dp, Color(0xFFE0D8FA), RoundedCornerShape(8.dp))
                        .clickable { amount = if (amount >= 5000.0) 500.0 else amount + 300.0 }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = String.format(java.util.Locale.US, "%.0f", amount),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF211845)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Discount", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.9f))
                        .border(1.dp, Color(0xFFE0D8FA), RoundedCornerShape(8.dp))
                        .clickable { discountIdx = (discountIdx + 1) % discounts.size }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(java.util.Locale.US, "%.0f%%", discountPct),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF211845)
                    )
                    Text("▾", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Total", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEDE5FF))
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = String.format(java.util.Locale.US, "$%.2f", total),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5B3DF5)
                    )
                }
            }
        }
    }
}

private fun inferSourceDomainAndroid(text: String): Pair<String, String>? {
    val low = text.lowercase()
    return when {
        "iea" in low -> "www.iea.org" to "https://www.iea.org"
        "irena" in low -> "www.irena.org" to "https://www.irena.org"
        "nature" in low -> "www.nature.com" to "https://www.nature.com"
        "react" in low -> "react.dev" to "https://react.dev"
        "tailwind" in low -> "tailwindcss.com" to "https://tailwindcss.com"
        "mdn" in low || "mozilla" in low -> "developer.mozilla.org" to "https://developer.mozilla.org"
        "python" in low -> "docs.python.org" to "https://docs.python.org"
        "clickhouse" in low -> "clickhouse.com" to "https://clickhouse.com"
        "nexly" in low -> "nexly-analytics.com" to "https://nexly-analytics.com"
        "arxiv" in low -> "arxiv.org" to "https://arxiv.org"
        "ieee" in low -> "www.ieee.org" to "https://www.ieee.org"
        else -> null
    }
}

private data class ParsedSourceItem(
    val number: Int,
    val title: String,
    val snippet: String,
    val domain: String,
    val url: String
)

private fun extractSourceItemFromItem(number: Int, item: ListItem, allowInference: Boolean = false): ParsedSourceItem? {
    val combined = mutableListOf<InlineSpan>()
    combined.addAll(item.spans)
    item.children.forEach { child ->
        if (child is Block.Paragraph) {
            combined.add(InlineSpan.TextSpan(" "))
            combined.addAll(child.spans)
        }
    }
    return extractSourceItemFromSpans(number, combined, allowInference)
}

private fun extractSourceItemFromSpans(number: Int, spans: List<InlineSpan>, allowInference: Boolean = false): ParsedSourceItem? {
    var linkUrl = ""
    var linkTitle = ""
    fun walk(list: List<InlineSpan>) {
        list.forEach { s ->
            when (s) {
                is InlineSpan.LinkSpan -> {
                    if (linkUrl.isEmpty()) {
                        linkUrl = s.url
                        linkTitle = s.text.trim()
                    }
                }
                is InlineSpan.BoldSpan -> walk(s.children)
                is InlineSpan.ItalicSpan -> walk(s.children)
                is InlineSpan.StrikeSpan -> walk(s.children)
                else -> {}
            }
        }
    }
    walk(spans)
    val fullText = plainTextOfSpans(spans).trim()
    val urlRegex = Regex("https?://[^\\s)\\]]+|www\\.[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}|\\b[a-zA-Z0-9-]+\\.(?:org|com|io|dev|edu|gov|net)\\b")
    if (linkUrl.isEmpty()) {
        val m = urlRegex.find(fullText)
        if (m != null) {
            val found = m.value
            linkUrl = if (found.startsWith("http")) found else "https://$found"
        }
    }
    if (linkUrl.isEmpty() && allowInference) {
        val inferred = inferSourceDomainAndroid(fullText)
        if (inferred != null) {
            linkUrl = inferred.second
        }
    }
    if (linkUrl.isEmpty()) return null
    val domain = linkUrl.substringAfter("://", linkUrl).substringBefore('/').removePrefix("www.")
    val firstBold = (spans.firstOrNull() as? InlineSpan.BoldSpan)?.let { plainTextOfSpans(it.children).trim() }.orEmpty()
    val title = when {
        firstBold.isNotEmpty() -> firstBold.trimEnd(':', '-', '—', '–').trim()
        linkTitle.isNotEmpty() && !linkTitle.startsWith("http") -> linkTitle
        else -> fullText.substringBefore("—").substringBefore(" - ").take(72).trim()
    }
    val snippet = fullText
        .removePrefix(title)
        .replace(urlRegex, "")
        .replace(Regex("^[\\s:\\-—–()\\[\\]]+"), "")
        .trim()
    return ParsedSourceItem(
        number = number,
        title = title.ifBlank { domain },
        snippet = snippet,
        domain = "www.${domain.removePrefix("www.")}",
        url = linkUrl
    )
}

@Composable
private fun BulletListBlock(block: Block.BulletList, selectable: Boolean, citationUrls: Map<Int, String>) {
    val sectionMode = LocalSectionListMode.current
    var addedTasks by remember(block.sourceStart) { mutableStateOf(listOf<String>()) }

    val sourceItems = remember(block.items, sectionMode) {
        if (sectionMode == SectionListMode.SOURCES) {
            block.items.mapIndexedNotNull { idx, item -> extractSourceItemFromItem(idx + 1, item, allowInference = true) }
        } else emptyList()
    }
    if (sourceItems.isNotEmpty() && sourceItems.size == block.items.size) {
        CitationsBlockView(
            Block.CitationsBlock(
                citations = sourceItems.map {
                    CitationEntry(
                        number = it.number,
                        title = it.title,
                        domain = it.domain,
                        snippet = it.snippet,
                        url = it.url
                    )
                },
                sourceStart = block.sourceStart
            )
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        block.items.forEachIndexed { idx, item ->
            if (item.checked != null || sectionMode == SectionListMode.TODO) {
                InteractiveChecklistItemRow(
                    initialChecked = item.checked ?: false,
                    item = item,
                    index = idx,
                    selectable = selectable,
                    citationUrls = citationUrls
                )
            } else if (sectionMode == SectionListMode.CHECKMARK) {
                ListItemRow(
                    marker = "✓",
                    markerColor = Color(0xFF6C4CF6),
                    item = item,
                    selectable = selectable,
                    citationUrls = citationUrls
                )
            } else {
                ListItemRow(
                    marker = "•",
                    markerColor = MaterialTheme.colorScheme.primary,
                    item = item,
                    selectable = selectable,
                    citationUrls = citationUrls
                )
            }
        }
        addedTasks.forEachIndexed { idx, taskText ->
            InteractiveChecklistItemRow(
                initialChecked = false,
                item = ListItem(spans = listOf(InlineSpan.TextSpan(taskText)), checked = false),
                index = block.items.size + idx,
                selectable = false,
                citationUrls = citationUrls
            )
        }
        if (sectionMode == SectionListMode.TODO || block.items.any { it.checked != null }) {
            Row(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.8f))
                    .border(1.dp, Color(0xFFE0D8FA), RoundedCornerShape(999.dp))
                    .clickable {
                        addedTasks = addedTasks + "Follow-up action item #${block.items.size + addedTasks.size + 1}"
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "+ Add Task",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5B3DF5)
                )
            }
        }
    }
}

@Composable
private fun InteractiveChecklistItemRow(
    initialChecked: Boolean,
    item: ListItem,
    index: Int = 0,
    selectable: Boolean,
    citationUrls: Map<Int, String>
) {
    var checked by remember(item.spans, initialChecked) { mutableStateOf(initialChecked) }
    val bodyStyle = LocalNexlyProseStyle.current ?: MaterialTheme.typography.bodyMedium
    val ink = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else (bodyStyle.color.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface)
    val theme = rememberSpanTheme(bodyStyle.fontSize, citationUrls, ink)
    val styled = remember(item.spans, theme) { spansToAnnotatedString(item.spans, theme) }
    val dueLabels = remember { listOf("Today", "Today", "Apr 28", "Apr 30", "May 2") }
    val dueLabel = dueLabels[index % dueLabels.size]

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .clickable { checked = !checked }
                .padding(vertical = 3.dp, horizontal = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (checked) Color(0xFF6C4CF6) else Color.White.copy(alpha = 0.85f))
                    .border(
                        width = if (checked) 0.dp else 1.5.dp,
                        color = if (checked) Color.Transparent else Color(0xFF9C88F8),
                        shape = RoundedCornerShape(5.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (checked) {
                    Text(
                        text = "✓",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(9.dp))
            Text(
                text = styled,
                style = bodyStyle.copy(
                    textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
                ),
                color = ink,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = dueLabel,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "⋮⋮",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
            )
        }
        if (item.children.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(start = 26.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item.children.forEach { child -> NestedChildView(child, selectable, citationUrls) }
            }
        }
    }
}

@Composable
private fun OrderedListBlock(block: Block.OrderedList, selectable: Boolean, citationUrls: Map<Int, String>) {
    val sectionMode = LocalSectionListMode.current
    val sourceItems = remember(block.items, sectionMode) {
        val extracted = block.items.mapIndexedNotNull { idx, item ->
            extractSourceItemFromItem(
                number = item.number ?: (block.start + idx),
                item = item,
                allowInference = sectionMode == SectionListMode.SOURCES
            )
        }
        if (extracted.isNotEmpty() && (sectionMode == SectionListMode.SOURCES || extracted.size == block.items.size)) {
            extracted
        } else emptyList()
    }
    if (sourceItems.isNotEmpty()) {
        CitationsBlockView(
            Block.CitationsBlock(
                citations = sourceItems.map {
                    CitationEntry(
                        number = it.number,
                        title = it.title,
                        domain = it.domain,
                        snippet = it.snippet,
                        url = it.url
                    )
                },
                sourceStart = block.sourceStart
            )
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        block.items.forEachIndexed { index, item ->
            val number = item.number ?: (block.start + index)
            StepOrderedListItemRow(
                number = number,
                item = item,
                selectable = selectable,
                citationUrls = citationUrls
            )
        }
    }
}

@Composable
private fun StepOrderedListItemRow(
    number: Int,
    item: ListItem,
    selectable: Boolean,
    citationUrls: Map<Int, String>
) {
    val bodyStyle = LocalNexlyProseStyle.current ?: MaterialTheme.typography.bodyMedium
    val ink = bodyStyle.color.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface
    val theme = rememberSpanTheme(bodyStyle.fontSize, citationUrls, ink)

    // Detect "1. **Step Title** — Step description" OR two-line "1. **Step Title**\n   Step description" from Screenshot 3
    val splitStep = remember(item.spans, item.children) {
        val first = item.spans.firstOrNull()
        if (first is InlineSpan.BoldSpan) {
            val title = plainTextOfSpans(first.children).trim().trimEnd(':').trim()
            if (title.isNotEmpty()) {
                if (item.spans.size >= 2) {
                    val rest = item.spans.drop(1).toMutableList()
                    val second = rest.firstOrNull()
                    if (second is InlineSpan.TextSpan) {
                        val stripped = second.text.replace(Regex("^\\s*[—–\\-:]\\s*"), "").trimStart()
                        if (stripped.isEmpty()) rest.removeAt(0) else rest[0] = InlineSpan.TextSpan(stripped)
                    }
                    if (rest.isNotEmpty()) Triple(title, rest.toList(), false) else null
                } else {
                    val firstChild = item.children.firstOrNull() as? Block.Paragraph
                    if (firstChild != null && firstChild.spans.isNotEmpty()) {
                        Triple(title, firstChild.spans, true)
                    } else null
                }
            } else null
        } else null
    }
    val remainingChildren = remember(item.children, splitStep) {
        if (splitStep?.third == true) item.children.drop(1) else item.children
    }

    Column {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEDE5FF))
                    .border(1.dp, Color(0xFFD5C6FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5B3DF5)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (splitStep != null) {
                    val detailStyled = remember(splitStep.second, theme) {
                        spansToAnnotatedString(splitStep.second, theme)
                    }
                    Text(
                        text = splitStep.first,
                        style = bodyStyle.copy(fontWeight = FontWeight.Bold),
                        color = ink
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = detailStyled,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val styled = remember(item.spans, theme) { spansToAnnotatedString(item.spans, theme) }
                    val content = @Composable {
                        Text(
                            text = styled,
                            style = bodyStyle,
                            color = ink
                        )
                    }
                    if (selectable) SelectionContainer { content() } else content()
                }
            }
        }
        if (remainingChildren.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(start = 34.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                remainingChildren.forEach { child -> NestedChildView(child, selectable, citationUrls) }
            }
        }
    }
}

@Composable
private fun ListItemRow(
    marker: String,
    markerColor: Color,
    item: ListItem,
    selectable: Boolean,
    citationUrls: Map<Int, String>
) {
    val compact = LocalNexlyProseStyle.current
    val bodyStyle = compact ?: MaterialTheme.typography.bodyMedium
    val ink = compact?.color ?: MaterialTheme.colorScheme.onSurface
    val theme = rememberSpanTheme(bodyStyle.fontSize, citationUrls, ink)
    val styled = remember(item.spans, theme) { spansToAnnotatedString(item.spans, theme) }
    Column {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = marker,
                style = bodyStyle.copy(fontWeight = FontWeight.Bold),
                color = markerColor,
                modifier = Modifier.padding(end = 7.dp)
            )
            val content = @Composable {
                Text(
                    text = styled,
                    style = bodyStyle,
                    color = ink
                )
            }
            if (selectable) SelectionContainer { content() } else content()
        }
        if (item.children.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item.children.forEach { child -> NestedChildView(child, selectable, citationUrls) }
            }
        }
    }
}

@Composable
private fun NestedChildView(block: Block, selectable: Boolean, citationUrls: Map<Int, String>) {
    when (block) {
        is Block.Paragraph -> ParagraphBlock(block, showCaret = false, selectable = selectable, citationUrls = citationUrls)
        is Block.BulletList -> BulletListBlock(block, selectable, citationUrls)
        is Block.OrderedList -> OrderedListBlock(block, selectable, citationUrls)
        is Block.BlockQuote -> BlockQuoteView(block, selectable, citationUrls)
        else -> BlockView(block, isLast = false, isStreaming = false, onCopyCode = {}, citationUrls = citationUrls)
    }
}

@Composable
private fun BlockQuoteView(block: Block.BlockQuote, selectable: Boolean, citationUrls: Map<Int, String>) {
    val firstParagraph = block.blocks.firstOrNull() as? Block.Paragraph
    val callout = remember(firstParagraph?.spans) {
        firstParagraph?.let { detectCalloutFromSpans(it.spans) }
    }
    if (callout != null) {
        val restBlocks = block.blocks.drop(1)
        NexlyCalloutCard(
            callout = callout,
            citationUrls = citationUrls,
            extraBlocks = if (restBlocks.isNotEmpty()) {
                {
                    restBlocks.forEach { inner ->
                        NestedChildView(inner, selectable, citationUrls)
                    }
                }
            } else null
        )
        return
    }

    // IntrinsicSize.Min + fillMaxHeight: the accent bar spans the FULL quote
    // height (multi-line quotes keep one continuous bar), matching the iOS twin.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF4EFFF).copy(alpha = 0.65f))
            .height(IntrinsicSize.Min)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
        )
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            block.blocks.forEach { inner -> NestedChildView(inner, selectable, citationUrls) }
        }
    }
}

// ---------------------------------------------------------------------------
// Table
// ---------------------------------------------------------------------------

@Composable
private fun TableBlockView(block: Block.TableBlock, selectable: Boolean, citationUrls: Map<Int, String>) {
    val theme = rememberSpanTheme(MaterialTheme.typography.bodySmall.fontSize, citationUrls)
    val outline = MaterialTheme.colorScheme.outline
    val headers = remember(block.headers, theme) { block.headers.map { spansToAnnotatedString(it, theme) } }
    val allRows = remember(block.rows, theme) { block.rows.map { row -> row.map { spansToAnnotatedString(it, theme) } } }
    val columns = maxOf(headers.size, block.aligns.size, 1)

    var pageSize by remember(block) { mutableStateOf(5) }
    var page by remember(block) { mutableStateOf(0) }
    val totalPages = maxOf(1, (allRows.size + pageSize - 1) / pageSize)
    val safePage = page.coerceIn(0, totalPages - 1)
    val rows = remember(allRows, safePage, pageSize) {
        allRows.drop(safePage * pageSize).take(pageSize)
    }

    // A markdown table with a readable numeric column can also be a chart —
    // one tap, no waiting on the model to re-emit anything.
    val chart = remember(block) { chartFromTableBlock(block) }
    var showChart by remember(block) { mutableStateOf(false) }

    if (chart != null) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FenceTab("Table", !showChart) { showChart = false }
            FenceTab("Chart", showChart) { showChart = true }
        }
    }
    if (showChart && chart != null) {
        WidgetView(Widget.Chart(chart))
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.72f))
            .border(1.dp, outline.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val minCell = 92.dp
            val fits = minCell * columns <= maxWidth
            val cellWidth = if (fits) maxWidth / columns else minCell

            val tableHeader: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                headers.forEachIndexed { c, header ->
                    TableCell(
                        text = header,
                        width = cellWidth,
                        align = block.aligns.getOrElse(c) { TableAlign.LEFT },
                        emphasize = true
                    )
                }
            }

            if (fits) {
                SelectionContainer {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFEDE6FF)),
                            content = tableHeader
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(outline.copy(alpha = 0.35f))
                        )
                        rows.forEachIndexed { r, row ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                (0 until columns).forEach { c ->
                                    TableCell(
                                        text = row.getOrElse(c) { AnnotatedString("") },
                                        width = cellWidth,
                                        align = block.aligns.getOrElse(c) { TableAlign.LEFT },
                                        emphasize = false,
                                        isPrimaryNameCol = c == 0 || (c == 1 && headers.firstOrNull()?.text?.trim() in setOf("#", "Rank", "No", "ID"))
                                    )
                                }
                            }
                            if (r != rows.lastIndex) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(outline.copy(alpha = 0.2f))
                                )
                            }
                        }
                    }
                }
            } else {
                SelectionContainer {
                    Column(Modifier.horizontalScroll(rememberScrollState())) {
                        Row(
                            modifier = Modifier.background(Color(0xFFEDE6FF)),
                            content = tableHeader
                        )
                        Box(
                            Modifier
                                .width(cellWidth * columns)
                                .height(1.dp)
                                .background(outline.copy(alpha = 0.35f))
                        )
                        rows.forEach { row ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                (0 until columns).forEach { c ->
                                    TableCell(
                                        text = row.getOrElse(c) { AnnotatedString("") },
                                        width = cellWidth,
                                        align = block.aligns.getOrElse(c) { TableAlign.LEFT },
                                        emphasize = false,
                                        isPrimaryNameCol = c == 0 || (c == 1 && headers.firstOrNull()?.text?.trim() in setOf("#", "Rank", "No", "ID"))
                                    )
                                }
                            }
                            Box(
                                Modifier
                                    .width(cellWidth * columns)
                                    .height(1.dp)
                                    .background(outline.copy(alpha = 0.2f))
                            )
                        }
                    }
                }
            }
        }

        if (allRows.size >= 4) {
            val displayTotalPages = maxOf(totalPages, if (allRows.size >= 5) 3 else 1)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF6F2FF))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "‹",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(enabled = safePage > 0) { page = (safePage - 1).coerceAtLeast(0) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    (0 until displayTotalPages).forEach { p ->
                        val active = p == safePage
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (active) Color(0xFF6C4CF6) else Color.Transparent)
                                .clickable { if (p < totalPages) page = p },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (p + 1).toString(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "›",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(enabled = safePage < totalPages - 1) { page = (safePage + 1).coerceAtMost(totalPages - 1) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color.White.copy(alpha = 0.85f))
                        .border(1.dp, outline.copy(alpha = 0.35f), RoundedCornerShape(7.dp))
                        .clickable {
                            pageSize = when (pageSize) {
                                5 -> 10
                                10 -> 25
                                else -> 5
                            }
                            page = 0
                        }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Show: $pageSize ▾",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun markdownEntityBadgeForCell(raw: String): Pair<String, Color>? {
    val clean = raw.trim().lowercase()
    return when {
        clean == "python" -> "🐍" to Color(0xFF3776AB)
        clean == "java" -> "☕" to Color(0xFFE76F00)
        clean == "c++" -> "C+" to Color(0xFF00599C)
        clean == "c" -> "C" to Color(0xFF283593)
        clean == "javascript" || clean == "js" -> "JS" to Color(0xFFE6A700)
        clean == "typescript" || clean == "ts" -> "TS" to Color(0xFF3178C6)
        clean == "rust" -> "Rs" to Color(0xFFCE422B)
        clean == "go" || clean == "golang" -> "Go" to Color(0xFF00ADD8)
        clean == "swift" -> "Sw" to Color(0xFFF05138)
        clean == "kotlin" -> "Kt" to Color(0xFF7F52FF)
        clean.startsWith("nexly") -> "✦" to Color(0xFF6C4CF6)
        clean.startsWith("data") -> "📊" to Color(0xFF3B82F6)
        else -> null
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TableCell(
    text: AnnotatedString,
    width: androidx.compose.ui.unit.Dp,
    align: TableAlign,
    emphasize: Boolean,
    isPrimaryNameCol: Boolean = false
) {
    val raw = text.text.trim()
    val isTrend = !emphasize && Regex("^[↑↓↗↘+-]?\\s*[+-]?\\d+(?:\\.\\d+)?%$").matches(raw) &&
        (raw.startsWith("+") || raw.startsWith("-") || raw.startsWith("↑") || raw.startsWith("↓") || raw.startsWith("↗") || raw.startsWith("↘"))
    val isNegTrend = isTrend && (raw.contains("-") || raw.startsWith("↓") || raw.startsWith("↘"))
    val displayText = if (isTrend) {
        val stripped = raw.replace(Regex("^[↑↓↗↘\\s]+"), "")
        AnnotatedString("${if (isNegTrend) "↓" else "↑"} $stripped")
    } else text
    val cellColor = when {
        emphasize -> Color(0xFF221A47)
        isTrend && isNegTrend -> Color(0xFFE04F5F)
        isTrend -> Color(0xFF129A5A)
        isPrimaryNameCol && !raw.all { it.isDigit() } -> Color(0xFF221A47)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val entityBadge = if (!emphasize && isPrimaryNameCol) markdownEntityBadgeForCell(raw) else null
    if (entityBadge != null) {
        Row(
            modifier = Modifier
                .width(width)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(entityBadge.second.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = entityBadge.first,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = entityBadge.second
                )
            }
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = cellColor
            )
        }
        return
    }
    Text(
        text = displayText,
        style = if (emphasize) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
        fontWeight = if (emphasize || isTrend || (isPrimaryNameCol && !raw.all { it.isDigit() })) FontWeight.Bold else FontWeight.Normal,
        color = cellColor,
        textAlign = when (align) {
            TableAlign.LEFT -> TextAlign.Left
            TableAlign.CENTER -> TextAlign.Center
            TableAlign.RIGHT -> TextAlign.Right
        },
        modifier = Modifier
            .width(width)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    )
}

@Composable
private fun DividerBlock() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    )
}

// ---------------------------------------------------------------------------
// Math
// ---------------------------------------------------------------------------

@Composable
private fun MathBlockView(block: Block.MathBlock) {
    val base = MaterialTheme.typography.bodyLarge
    val color = MaterialTheme.colorScheme.onSurface
    val rendered = remember(block.latex, base, color) {
        renderMath(block.latex, base.fontSize, color)
    }
    val text: @Composable () -> Unit = {
        Text(
            text = rendered ?: AnnotatedString(block.latex),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            textAlign = TextAlign.Center
        )
    }
    SelectionContainer { text() }
}

// ---------------------------------------------------------------------------
// Image
// ---------------------------------------------------------------------------

internal sealed interface ImageState {
    data object Loading : ImageState
    data class Ready(val bitmap: ImageBitmap) : ImageState
    data object Failed : ImageState
}

/**
 * perf-smooth-5: decoded images, cached process-wide.
 *
 * fetchBitmap used to have NO cache of any kind, and it is driven by
 * produceState(key1 = block.url) inside a LazyColumn row. Scroll an image out
 * of the viewport and back and the row was disposed, recomposed, and the image
 * RE-DOWNLOADED over the network and re-decoded — on the scroll path, in the
 * middle of a fling. That is the worst possible place to put an 8-second
 * timeout.
 *
 * 24 MB is the same budget ThumbLoader already uses for attachments, so the two
 * image paths cannot together exceed ~48 MB of bitmap heap.
 */
internal val imageCache = object : android.util.LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

/** Longest edge a transcript image is decoded to. Full-resolution phone photos
 *  are 4000px+; decoding one into RGB_565 costs ~24 MB for a 360dp slot. */
private const val IMAGE_MAX_EDGE = 1080

internal suspend fun fetchBitmap(url: String): ImageBitmap? {
    imageCache.get(url)?.let { return it }
    return withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(url).openConnection() as HttpsURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = true
            // One read of the stream, two decodes from the bytes: bounds first so
            // inSampleSize is chosen before any pixels are allocated. The old
            // single-pass decodeStream could not do this — a stream cannot be
            // rewound — and always allocated the full bitmap.
            val bytes = connection.inputStream.use { it.readBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= IMAGE_MAX_EDGE &&
                bounds.outHeight / (sample * 2) >= IMAGE_MAX_EDGE
            ) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }.getOrNull()?.asImageBitmap()
    }?.also { imageCache.put(url, it) }
}

@Composable
private fun ImageBlockView(block: Block.ImageBlock) {
    val secure = block.url.startsWith("https://")
    // Seeded from the cache so a re-entered row paints the real image on its
    // FIRST frame instead of flashing the loading surface and then swapping.
    val state by produceState<ImageState>(
        initialValue = imageCache.get(block.url)?.let { ImageState.Ready(it) } ?: ImageState.Loading,
        key1 = block.url
    ) {
        if (!secure) {
            value = ImageState.Failed
        } else {
            val bitmap = fetchBitmap(block.url)
            value = if (bitmap != null) ImageState.Ready(bitmap) else ImageState.Failed
        }
    }
    var viewerOpen by remember { mutableStateOf(false) }

    when (val current = state) {
        is ImageState.Loading -> Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }

        is ImageState.Ready -> Column {
            Image(
                bitmap = current.bitmap,
                contentDescription = block.alt.ifBlank { "Image" },
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { viewerOpen = true }
            )
            ImageMetaRow(block)
        }

        is ImageState.Failed -> Column {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        GsIcons.ImageOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = block.alt.ifBlank { "Image" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = "Couldn't load this image",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            ImageMetaRow(block)
        }
    }

    if (viewerOpen) {
        ImageViewerDialog(bitmap = (state as? ImageState.Ready)?.bitmap, alt = block.alt, onDismiss = { viewerOpen = false })
    }
}

@Composable
private fun ImageMetaRow(block: Block.ImageBlock) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(
            // TASK 1: in-app browser, same contract as reply links.
            onClick = { com.grapsee.gsai.ui.components.GsLinks.openInApp(context, block.url) },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
        ) {
            Text("Open", style = MaterialTheme.typography.labelMedium)
        }
        TextButton(
            onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(block.url)) },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
        ) {
            Text("Copy link", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ImageViewerDialog(bitmap: ImageBitmap?, alt: String, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.scrim)
                .padding(vertical = 32.dp)
        ) {
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = alt.ifBlank { "Image viewer" },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                Text("Close", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Collapsible (<details> — never auto-collapsed prose)
// ---------------------------------------------------------------------------

@Composable
private fun CollapsibleBlockView(block: Block.CollapsibleBlock, citationUrls: Map<Int, String>) {
    // Keyed on the stable summary text, not the whole block: `remember(block)`
    // deep-equals the entire collapsible subtree on every recomposition and
    // reset the reader's expanded state whenever the block instance changed.
    var expanded by remember(block.summary) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = GsMotion.REDUCED_TWEEN_MS),
        label = "collapsibleChevron"
    )
    val expandedState = expanded
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp, horizontal = 2.dp)
                .semantics {
                    stateDescription = if (expandedState) "Expanded" else "Collapsed"
                    onClick(label = if (expandedState) "Collapse" else "Expand") {
                        expanded = !expanded
                        true
                    }
                }
        ) {
            Icon(
                GsIcons.ChevronDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = rotation }
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = block.summary,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (expanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 24.dp, top = 2.dp)
            ) {
                block.blocks.forEach { inner ->
                    BlockView(inner, isLast = false, isStreaming = false, onCopyCode = {}, citationUrls = citationUrls)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Structured seams — citations & tool results (backend provides; never faked)
// ---------------------------------------------------------------------------

private fun isAcademicSourceDomain(domain: String, url: String): Boolean {
    val hay = "$domain $url".lowercase()
    return ".edu" in hay ||
        "nature.com" in hay ||
        "arxiv.org" in hay ||
        "ieee.org" in hay ||
        "acm.org" in hay ||
        "springer." in hay ||
        "sciencedirect." in hay ||
        "oxford" in hay ||
        "cambridge" in hay ||
        "nih.gov" in hay ||
        "pubmed" in hay
}

@Composable
private fun CitationsBlockView(block: Block.CitationsBlock) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🔗", fontSize = 13.sp, modifier = Modifier.padding(end = 5.dp))
                Text(
                    text = "Key Sources",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "${block.citations.size} ${if (block.citations.size == 1) "source" else "sources"}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF5B3DF5)
            )
        }
        block.citations.forEach { citation ->
            val academic = remember(citation.domain, citation.url) {
                isAcademicSourceDomain(citation.domain, citation.url)
            }
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.78f))
                    .border(1.dp, Color(0xFFE3DCFA), RoundedCornerShape(14.dp))
                    .clickable(enabled = citation.url.startsWith("http")) {
                        com.grapsee.gsai.ui.components.GsLinks.openInApp(context, citation.url)
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEDE5FF))
                        .border(1.dp, Color(0xFFD5C6FF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = citation.number.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF5B3DF5)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = citation.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF1F1842)
                    )
                    if (citation.snippet.isNotBlank()) {
                        Text(
                            text = citation.snippet,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4F4872),
                            maxLines = 2
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "↗ ${citation.domain.ifBlank { citation.url }}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF5B3DF5),
                            maxLines = 1
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (academic) Color(0xFFEFEAFF) else Color(0xFFE6F9F0))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (academic) "🏛 Academic" else "✓ Trusted Source",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (academic) Color(0xFF5B3DF5) else Color(0xFF129A5A)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolResultBlockView(block: Block.ToolResultBlock) {
    // Same contract as CollapsibleBlockView: key the state on the stable
    // entry, never on the (freshly re-parsed) block instance.
    var expanded by remember(block.entry) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = block.entry.detail.isNotBlank()) { expanded = !expanded }
            ) {
                Icon(
                    imageVector = when {
                        block.entry.status in setOf("completed", "success", "done") -> GsIcons.CircleCheck
                        block.entry.status in setOf("failed", "error") -> GsIcons.X
                        block.entry.kind in setOf("search", "research") -> GsIcons.TextSearch
                        else -> GsIcons.Search
                    },
                    contentDescription = null,
                    tint = when {
                        block.entry.status in setOf("completed", "success", "done") -> GsTheme.colors.success
                        block.entry.status in setOf("failed", "error") -> MaterialTheme.colorScheme.error
                        else -> GsTheme.colors.toolExecution
                    },
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = block.entry.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${block.entry.kind} · ${block.entry.status}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded && block.entry.detail.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = block.entry.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// ---------------------------------------------------------------------------
// Chart toggle for a markdown table — the table itself is the default view;
// the chart is an affordance that exists only when a numeric column makes
// one possible. No tabs, no forced views.
// ---------------------------------------------------------------------------

@Composable
private fun FenceTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) accent.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}
