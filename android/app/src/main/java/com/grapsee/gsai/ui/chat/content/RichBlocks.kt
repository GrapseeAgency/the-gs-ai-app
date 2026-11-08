package com.grapsee.gsai.ui.chat.content

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
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
    val context = LocalContext.current
    NexlyIcons.ensureLoaded(context)
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
    val clean = NexlyIcons.cleanPlainText(raw).trimEnd(':').trim()
    val lower = clean.lowercase()
    return when {
        lower in setOf("important", "warning", "caution", "attention", "important notice") ->
            CalloutTone.WARNING to "alert-triangle"
        lower in setOf("quick summary", "summary", "tl;dr", "tldr", "executive summary") ->
            CalloutTone.SUMMARY to "bolt"
        lower in setOf("key insight", "key insights", "quick fact", "quick facts", "pro tip", "tip", "note", "key takeaway", "key takeaways") ->
            CalloutTone.FACT to "bulb"
        else -> null
    }
}

private fun isCalculatorHeadingTitle(raw: String): Boolean {
    val lower = NexlyIcons.cleanPlainText(raw).lowercase()
    return "calculator" in lower || "estimator" in lower || "simulator" in lower || "pricing model" in lower
}

internal fun extractQuickCalcParams(text: String): Triple<Double, Double, Int>? {
    val low = text.lowercase()
    // A generic percentage tool cannot infer a medical model, profit margin,
    // investment cash flows or a 50/30/20 allocation from nearby numbers.
    if (listOf("calorie", "protein", "macro", "tdee", "bmi", "margin", "roi", "budget", "salary", "income").any { it in low }) return null
    val amounts = Regex("""[$€£¥]\s*(\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?)(?:\s*([kKmMbB])(?![A-Za-z]))?(?![\dA-Za-z.,])""").findAll(text).toList()
    val rates = Regex("""(?<![\d.,−-])(\d+(?:\.\d+)?)\s*(?:%|percent\b)""", RegexOption.IGNORE_CASE).findAll(text).toList()
    if (amounts.size != 1 || rates.size != 1) return null
    if (listOf("discount", "tax", "tip", "markup", "vat", "loan", "mortgage", "emi", "compound", "apy", "apr").none { it in low }) return null
    val needsTerm = listOf("loan", "mortgage", "emi", "amortiz", "compound", "invest", "apy", "apr").any { it in low }
    if (needsTerm && !Regex("""\b\d{1,2}\s*(?:-\s*)?(?:years?|yrs?\b)""", RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
    val currMatch = Regex("""[$€£¥]\s*(\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?)(?:\s*([kKmMbB])(?![A-Za-z]))?(?![\dA-Za-z.,])""").find(text)
    val currAmount = currMatch?.let { m ->
        val base = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return@let null
        val mult = when (m.groupValues[2].lowercase()) {
            "k" -> 1_000.0
            "m" -> 1_000_000.0
            "b" -> 1_000_000_000.0
            else -> 1.0
        }
        base * mult
    }
    val pctMatch = Regex("""(?<![\d.,−-])(\d+(?:\.\d+)?)\s*(?:%|percent\b)""", RegexOption.IGNORE_CASE).find(text)
    val pctVal = pctMatch?.groupValues?.get(1)?.toDoubleOrNull()

    val yrMatch = Regex("""(\d{1,2})\s*(?:-\s*)?(?:years?|yrs?\b)""", RegexOption.IGNORE_CASE).find(text)
    val suppliedYears = yrMatch?.groupValues?.get(1)?.toIntOrNull()
    if (needsTerm && (suppliedYears == null || suppliedYears !in 1..40)) return null
    val yearsVal = suppliedYears ?: 5

    val amt = currAmount?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
    val rate = pctVal?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
    return Triple(amt, rate, yearsVal)
}

private fun classifySectionListMode(headingText: String): SectionListMode {
    val lower = headingText.trim().lowercase()
    return when {
        "next action" in lower || "action item" in lower || "todo" in lower || "to-do" in lower ||
            "checklist" in lower || lower == "tasks" || "your tasks" in lower ||
            "what to do" in lower || "next step" in lower || "first step" in lower ||
            "how to fix" in lower || "routine" in lower ->
            SectionListMode.TODO
        "key source" in lower || lower == "sources" || "trusted source" in lower ->
            SectionListMode.SOURCES
        "priorit" in lower || "feature" in lower || "highlight" in lower || "benefit" in lower ||
            "why " in lower || "bottom line" in lower || "takeaway" in lower ->
            SectionListMode.CHECKMARK
        else -> SectionListMode.DEFAULT
    }
}

private fun promoteStandaloneBoldHeadings(rawBlocks: List<Block>): List<Block> =
    rawBlocks.map { b ->
        if (b is Block.Paragraph && b.spans.size == 1 && b.spans.first() is InlineSpan.BoldSpan) {
            val bold = b.spans.first() as InlineSpan.BoldSpan
            val text = NexlyIcons.cleanPlainText(plainTextOfSpans(bold.children)).trim()
            if (text.length in 3..72 && !text.endsWith(".") && classifyCalloutHeaderTitle(text) == null) {
                Block.Heading(level = 3, spans = bold.children, sourceStart = b.sourceStart)
            } else b
        } else b
    }

internal fun sourceCardForHeading(heading: Block, following: Block?): Pair<Block.CitationsBlock, String>? {
    if (heading !is Block.Heading || following == null) return null
    val title = NexlyIcons.cleanPlainText(plainTextOfSpans(heading.spans)).trim()
    if (classifySectionListMode(title) != SectionListMode.SOURCES) return null
    if (following is Block.CitationsBlock) return following to title
    val items = when (following) {
        is Block.OrderedList -> following.items
        is Block.BulletList -> following.items
        else -> return null
    }
    val start = (following as? Block.OrderedList)?.start ?: 1
    val sources = items.mapIndexedNotNull { index, item -> extractSourceItemFromItem(item.number ?: (start + index), item) }
    // A mixed list must not lose its unlinked source text.
    if (sources.isEmpty() || sources.size != items.size) return null
    return Block.CitationsBlock(sources.map { CitationEntry(number = it.number, title = it.title, domain = it.domain, snippet = it.snippet, url = it.url) }, following.sourceStart) to title
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
    val codeActions = remember(blocks) { ResponseCodeActions() }
    CompositionLocalProvider(LocalResponseCodeActions provides codeActions) {
        StructuredBlockSequenceContent(blocks, isStreaming, onCopyCode, citationUrls, onSendPrompt, statusCard, statusAtEnd, onStatusPlaced)
    }
}

@Composable
private fun StructuredBlockSequenceContent(
    blocks: List<Block>,
    isStreaming: Boolean,
    onCopyCode: (String) -> Unit,
    citationUrls: Map<Int, String>,
    onSendPrompt: ((String) -> Unit)?,
    statusCard: (@Composable () -> Unit)?,
    statusAtEnd: Boolean,
    onStatusPlaced: () -> Unit
) {
    val normalizedBlocks = remember(blocks) { promoteStandaloneBoldHeadings(blocks) }
    // 1. Detect if this response is a multi-section dashboard (Screenshot 1)
    val dashboardTabs = remember(normalizedBlocks) {
        val tabs = mutableListOf("Dashboard")
        val hasTable = normalizedBlocks.any { it is Block.TableBlock || (it is Block.WidgetBlock && it.kind == "table") }
        val hasTodo = normalizedBlocks.any {
            (it is Block.BulletList && it.items.any { item -> item.checked != null }) ||
                (it is Block.Heading && classifySectionListMode(plainTextOfSpans(it.spans)) == SectionListMode.TODO) ||
                (it is Block.WidgetBlock && it.kind == "checklist")
        }
        val hasCalc = normalizedBlocks.any {
            (it is Block.WidgetBlock && it.kind == "calc") ||
                (it is Block.Heading && isCalculatorHeadingTitle(plainTextOfSpans(it.spans)))
        }
        val hasCode = normalizedBlocks.any { it is Block.CodeBlock }
        if (hasTable) tabs += "Table"
        if (hasTodo) tabs += "Todo"
        if (hasCalc) tabs += "Calculator"
        else if (hasCode && tabs.size >= 3) tabs += "Code"
        if (tabs.size >= 3) tabs else emptyList()
    }
    var activeDashboardTab by remember(normalizedBlocks.size) { mutableStateOf("Dashboard") }

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

    val statusHostIndex = if (statusAtEnd) normalizedBlocks.lastIndex else 0
    var currentSectionMode = SectionListMode.DEFAULT
    var renderedExplicitCalc = false
    var i = 0
    while (i < normalizedBlocks.size) {
        val block = normalizedBlocks[i]
        val nextBlock = normalizedBlocks.getOrNull(i + 1)

        // Filter by dashboard tab when user selects Table / Todo / Calculator / Code
        val skipForTab = when (activeDashboardTab) {
            "Table" -> block !is Block.TableBlock && !(block is Block.WidgetBlock && block.kind == "table") &&
                !(block is Block.Heading && ("table" in plainTextOfSpans(block.spans).lowercase() || "product" in plainTextOfSpans(block.spans).lowercase() || "language" in plainTextOfSpans(block.spans).lowercase()))
            "Todo" -> !(block is Block.BulletList) && !(block is Block.WidgetBlock && block.kind == "checklist") &&
                !(block is Block.Heading && classifySectionListMode(plainTextOfSpans(block.spans)) == SectionListMode.TODO)
            "Calculator" -> !(block is Block.WidgetBlock && block.kind == "calc") &&
                !(block is Block.Heading && isCalculatorHeadingTitle(plainTextOfSpans(block.spans)))
            "Code" -> block !is Block.CodeBlock
            else -> false
        }

        // Markdown source lists are still list blocks at this seam, not only
        // dedicated citations blocks. Coalesce the actual parser output too.
        val sourceCard = if (!skipForTab) sourceCardForHeading(block, nextBlock) else null
        if (sourceCard != null) {
            currentSectionMode = SectionListMode.SOURCES
            key("sources:${block.sourceStart}") { CitationsBlockView(sourceCard.first, sourceCard.second) }
            if (statusCard != null && (i == statusHostIndex || i + 1 == statusHostIndex)) {
                onStatusPlaced(); Spacer(Modifier.height(2.dp)); statusCard()
            }
            i += 2
            continue
        }

        // Coalesce Heading("Quick Calculator" | "Calculator") + Paragraph/BulletList into interactive NexlyQuickCalculatorCard (Screenshot 1)
        if (!skipForTab && block is Block.Heading) {
            val hTitle = NexlyIcons.cleanPlainText(plainTextOfSpans(block.spans))
            if (isCalculatorHeadingTitle(hTitle) && (nextBlock is Block.Paragraph || nextBlock is Block.BulletList)) {
                val bodyText = when (nextBlock) {
                    is Block.Paragraph -> plainTextOfSpans(nextBlock.spans)
                    is Block.BulletList -> nextBlock.items.joinToString(" ") { plainTextOfSpans(it.spans) }
                    else -> ""
                }
                val params = extractQuickCalcParams("$hTitle $bodyText")
                if (params != null) {
                    val (amt, disc, yrs) = params
                    renderedExplicitCalc = true
                    key("calc:${block.sourceStart}") {
                        NexlyQuickCalculatorCard(
                            title = hTitle,
                            initialAmount = amt,
                            initialDiscount = disc,
                            initialYears = yrs,
                            contextHint = bodyText
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

    // Automatic interactive calculator when prose contains financial, loan, budget, macro, or rate signals
    var renderedSpontaneousCalc = false
    if (!isStreaming && !renderedExplicitCalc && normalizedBlocks.none { it is Block.WidgetBlock && it.kind == "calc" }) {
        val fullProse = remember(normalizedBlocks) {
            normalizedBlocks.mapNotNull { b ->
                when (b) {
                    is Block.Paragraph -> plainTextOfSpans(b.spans)
                    is Block.BulletList -> b.items.joinToString(" ") { plainTextOfSpans(it.spans) }
                    is Block.OrderedList -> b.items.joinToString(" ") { plainTextOfSpans(it.spans) }
                    else -> null
                }
            }.joinToString(" ")
        }
        val hasCalcSignal = remember(fullProse) {
            val low = fullProse.lowercase()
            val hasCurrency = fullProse.contains('$') || fullProse.contains('€') || fullProse.contains('£')
            val hasPct = fullProse.contains('%') || "percent" in low
            val hasNumber = Regex("\\b\\d{2,}\\b").containsMatchIn(fullProse)
            val isFinOrMath = ((hasCurrency || hasNumber) && hasPct && ("discount" in low || "tax" in low || "tip" in low || "interest" in low || "margin" in low || "roi" in low || "price" in low || "cost" in low || "save" in low || "loan" in low || "mortgage" in low || "compound" in low || "invest" in low || "budget" in low || "salary" in low)) ||
                (hasNumber && ("mortgage" in low || "amortiz" in low || "compound interest" in low || ("loan" in low && ("apr" in low || "monthly" in low)) || ("budget" in low && "50/30/20" in low) || ("calorie" in low && "protein" in low) || "tdee" in low || ("macro" in low && "protein" in low)))
            isFinOrMath
        }
        val params = remember(fullProse) { extractQuickCalcParams(fullProse) }
        if (hasCalcSignal && params != null) {
            renderedSpontaneousCalc = true
            val (amt, disc, yrs) = params
            NexlyQuickCalculatorCard(
                title = "Interactive Simulator",
                initialAmount = amt,
                initialDiscount = disc,
                initialYears = yrs,
                contextHint = fullProse
            )
        }
    }

    // Spontaneous Interactive Quantitative Explorer when prose lists 3+ numeric items without a chart or table
    var renderedQuantitativeExplorer = false
    if (!isStreaming && normalizedBlocks.none { it is Block.TableBlock || (it is Block.WidgetBlock && it.kind in setOf("chart", "table", "stat", "progress", "gauge")) }) {
        val extractedMetrics = remember(normalizedBlocks) { extractSpontaneousMetricsFromBlocks(normalizedBlocks) }
        if (extractedMetrics.size >= 3) {
            renderedQuantitativeExplorer = true
            NexlyQuantitativeExplorerCard(items = extractedMetrics)
        }
    }

    // Spontaneous Interactive Concept Deck (Flashcards + Self-Test Quiz) when structured prose explains 3+ concepts
    if (!isStreaming && !renderedExplicitCalc && !renderedSpontaneousCalc && !renderedQuantitativeExplorer &&
        normalizedBlocks.none { it is Block.TableBlock || it is Block.WidgetBlock || it is Block.HtmlBlock }
    ) {
        val extractedConcepts = remember(normalizedBlocks) { extractSpontaneousConceptsFromBlocks(normalizedBlocks) }
        if (extractedConcepts.size >= 3) {
            NexlyInteractiveConceptDeckCard(concepts = extractedConcepts)
        }
    }

    // Bottom Contextual Action Pills (Screenshots 1–6)
    if (!isStreaming && normalizedBlocks.isNotEmpty()) {
        val endsWithActionWidget = normalizedBlocks.lastOrNull().let {
            it is Block.WidgetBlock && it.kind in setOf("actions", "followups", "chips", "menu")
        }
        if (!endsWithActionWidget) {
            NexlyContextualActionPillsRow(
                blocks = normalizedBlocks,
                onCopyCode = onCopyCode,
                onSendPrompt = onSendPrompt,
                citationUrls = citationUrls,
                onShowCode = { activeDashboardTab = "Dashboard" }
            )
        }
    }
}

@Composable
private fun NexlyContextualActionPillsRow(
    blocks: List<Block>,
    onCopyCode: (String) -> Unit,
    onSendPrompt: ((String) -> Unit)?,
    citationUrls: Map<Int, String>,
    onShowCode: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val codeActions = LocalResponseCodeActions.current
    var pendingSave by remember { mutableStateOf<ByteArray?>(null) }
    var detailView by remember(blocks) { mutableStateOf<String?>(null) }
    fun notify(message: String) { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val bytes = pendingSave
        pendingSave = null
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null && bytes != null) scope.launch {
            val failure = withContext(Dispatchers.IO) {
                runCatching {
                    val output = context.contentResolver.openOutputStream(uri) ?: error("The selected destination cannot be opened.")
                    output.use { it.write(bytes) }
                }.exceptionOrNull()
            }
            notify(if (failure == null) "Export saved" else "Could not save export: ${failure.message}")
        }
    }
    val sourceText = remember(blocks, citationUrls) {
        extractCopyableBlockSummary(blocks, false) + if (citationUrls.isEmpty()) "" else
            "\n\nSources\n" + citationUrls.toSortedMap().entries.joinToString("\n") { "[${it.key}] ${it.value}" }
    }
    val citationText = remember(blocks, citationUrls) {
        listOf(extractCopyableBlockSummary(blocks, true), citationUrls.toSortedMap().entries.joinToString("\n") { "[${it.key}] ${it.value}" })
            .filter { it.isNotBlank() }.joinToString("\n")
    }
    val sourceUrls = remember(blocks, citationUrls) {
        (citationUrls.values + blocks.filterIsInstance<Block.CitationsBlock>().flatMap { block -> block.citations.map { it.url } })
            .distinct().filter { raw ->
                val uri = android.net.Uri.parse(raw)
                uri.scheme?.lowercase() in setOf("https", "http") && !uri.host.isNullOrBlank()
            }
    }
    fun saveFile(name: String, mime: String, bytes: ByteArray) {
        pendingSave = bytes
        runCatching {
            saveLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = mime; putExtra(Intent.EXTRA_TITLE, name)
            })
        }.onFailure { pendingSave = null; notify("No file picker is available: ${it.message}") }
    }
    if (detailView != null) AlertDialog(
        onDismissRequest = { detailView = null },
        title = { Text(if (detailView == "sources") "All sources" else "Response report — original source and data") },
        text = {
            SelectionContainer {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    Text(CodeDisplay.escape(if (detailView == "sources") citationText.ifBlank { "No source URLs were supplied." } else sourceText))
                    if (detailView == "sources") sourceUrls.forEach { url ->
                        TextButton(onClick = { com.grapsee.gsai.ui.components.GsLinks.openInApp(context, url) }) {
                            NexlyGlassGlyphTile(name = "external-link", size = 20.dp, glyphSize = 12.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(CodeDisplay.escape(url))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { detailView = null }) { Text("Close details") } }
    )
    val actions = remember(blocks, citationUrls) {
        val hasChart = blocks.any { it is Block.WidgetBlock && it.kind == "chart" }
        val hasTable = blocks.any { it is Block.TableBlock || (it is Block.WidgetBlock && it.kind == "table") }
        val hasCode = blocks.any { it is Block.CodeBlock }
        val hasSources = citationUrls.isNotEmpty() || blocks.any {
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
                "message-circle" to "Ask follow-up",
                "chart-bar" to "Show me a detailed report",
                "sparkles" to "Create a presentation"
            )
            hasChart -> listOf(
                "download" to "Download Report",
                "arrows-maximize" to "View Details"
            )
            hasTable -> listOf(
                "chart-bar" to "View Full Report",
                "share" to "Share"
            )
            hasSources -> listOf(
                "external-link" to "View All Sources",
                "clipboard" to "Copy Citations",
                "book-2" to "Learn More"
            )
            hasCode -> listOf(
                "code" to "Edit Code",
                "download" to "Download Project",
                "message-circle" to "Ask Follow Up"
            )
            hasPlanOrSteps -> listOf(
                "calendar" to "Make a timeline",
                "arrows-maximize" to "Expand plan",
                "share" to "Share"
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
                        runCatching {
                            when (label) {
                                "Share" -> context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, sourceText)
                                }, "Share response"))
                                "Copy Citations" -> if (citationText.isBlank()) notify("No citation URLs are available to copy.") else onCopyCode(citationText)
                                "Download Report" -> saveFile("gs-ai-response.md", "text/markdown", sourceText.toByteArray(Charsets.UTF_8))
                                "Download Project" -> {
                                    val sources = blocks.filterIsInstance<Block.CodeBlock>().filterNot { it.open }.map {
                                        codeActions?.source(it.sourceStart) ?: (it.language to it.code)
                                    }
                                    saveFile("gs-ai-source-snippets.zip", "application/zip", responseSourceArchive(sources))
                                    notify("Source snippets only, not a complete runnable project. See README.txt.")
                                }
                                "Edit Code" -> {
                                    onShowCode()
                                    codeActions?.editFirst() ?: notify("No editable code block is available.")
                                }
                                "View All Sources" -> detailView = "sources"
                                "View Details", "View Full Report" -> detailView = "report"
                                else -> if (onSendPrompt != null) onSendPrompt(label) else notify("Follow-up requests are unavailable in this view.")
                            }
                        }.onFailure { notify("The action failed: ${it.message}") }
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                NexlyGlassGlyphTile(
                    name = icon,
                    size = 20.dp,
                    glyphSize = 12.dp
                )
                Spacer(Modifier.width(6.dp))
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

internal fun extractCopyableBlockSummary(blocks: List<Block>, citationsOnly: Boolean): String {
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
            is Block.WidgetBlock -> if (!citationsOnly) lines += "${block.kind} data\n${block.json}"
            is Block.HtmlBlock -> if (!citationsOnly) lines += block.code
            is Block.TableBlock -> if (!citationsOnly) {
                lines += block.headers.joinToString("\t") { plainTextOfSpans(it) }
                lines += block.rows.map { row -> row.joinToString("\t") { plainTextOfSpans(it) } }
            }
            is Block.BlockQuote -> if (!citationsOnly) lines += extractCopyableBlockSummary(block.blocks, false)
            is Block.MathBlock -> if (!citationsOnly) lines += block.latex
            is Block.MermaidBlock -> if (!citationsOnly) lines += block.source
            else -> {}
        }
    }
    return lines.joinToString("\n").trim()
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
            val visual = remember(block.code, block.open, isStreaming, block.language) {
                if (block.open || isStreaming) null
                else {
                    val lang = block.language?.lowercase()
                    if (lang.isNullOrEmpty() || lang in setOf("text", "txt", "plain", "ascii", "art")) visualFromAscii(block.code)
                    else null
                }
            }
            val codeActions = LocalResponseCodeActions.current
            var editPromotedSource by remember(block.sourceStart, block.code) { mutableStateOf(false) }
            DisposableEffect(codeActions, block.sourceStart, block.code, visual) {
                if (visual != null) codeActions?.register(block.sourceStart, ResponseCodeActions.Entry(block.language, { block.code }, {
                    codeActions?.editOnRegistration(block.sourceStart)
                    editPromotedSource = true
                }))
                onDispose { if (visual != null) codeActions?.remove(block.sourceStart) }
            }
            if (visual != null && !editPromotedSource) {
                WidgetView(visual, onSendPrompt)
            } else {
                CodeBlockCard(
                    language = block.language,
                    code = block.code,
                    open = block.open || isStreaming,
                    sourceStart = block.sourceStart,
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

private const val NEXLY_INLINE_ICON_TAG = "nexly_inline_icon"

private fun appendNexlyText(
    builder: AnnotatedString.Builder,
    text: String,
    theme: SpanTheme
) {
    for (part in NexlyIcons.inlineParts(text)) {
        if (part.isIcon) {
            val iconName = NexlyIcons.resolve(part.value)
            val inlineId = "nexly_icon:$iconName"
            builder.pushStringAnnotation(tag = NEXLY_INLINE_ICON_TAG, annotation = iconName)
            builder.appendInlineContent(id = inlineId, alternateText = " ")
            builder.pop()
        } else {
            builder.append(part.value)
        }
    }
}

@Composable
private fun rememberNexlyInlineContent(styled: AnnotatedString): Map<String, InlineTextContent> {
    return remember(styled) {
        val annotations = styled.getStringAnnotations(
            tag = NEXLY_INLINE_ICON_TAG,
            start = 0,
            end = styled.length
        )
        if (annotations.isEmpty()) {
            emptyMap()
        } else {
            annotations.map { it.item }.distinct().associate { iconName ->
                "nexly_icon:$iconName" to InlineTextContent(
                    Placeholder(
                        width = 20.sp,
                        height = 18.sp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        NexlyGlassGlyphTile(
                            name = iconName,
                            toneName = NexlyIcons.toneFor(iconName),
                            size = 17.dp,
                            glyphSize = 11.dp
                        )
                    }
                }
            }
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
            ) { builder.append(CodeDisplay.escape(span.code)) }
            is InlineSpan.LinkSpan -> builder.withLink(
                LinkAnnotation.Url(
                    span.url,
                    TextLinkStyles(style = SpanStyle(color = theme.linkColor, textDecoration = TextDecoration.Underline)),
                    // TASK 1: tap opens the IN-APP browser. External open is the
                    // long-press path (GsLinks), never a plain tap.
                    linkInteractionListener = { _ -> theme.openLink(span.url) }
                )
            ) { builder.append(NexlyIcons.cleanPlainText(span.text).ifEmpty { "Link" }) }
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
        val rawTitle = NexlyIcons.cleanPlainText(plainTextOfSpans(first.children)).trimEnd(':').trim()
        val lower = rawTitle.lowercase()
        val toneAndIcon: Pair<CalloutTone, String>? = when {
            lower in setOf("important", "warning", "caution", "attention") ->
                CalloutTone.WARNING to "alert-triangle"
            lower in setOf("quick summary", "summary", "tl;dr", "tldr", "executive summary") ->
                CalloutTone.SUMMARY to "bolt"
            lower in setOf("key insight", "key insights", "quick fact", "pro tip", "tip", "note", "takeaway") ->
                CalloutTone.FACT to "bulb"
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
                icon = if (isWarn) "alert-triangle" else "bulb",
                title = if (kind == "IMPORTANT") "Important" else if (isWarn) "Warning" else "Key Insight",
                bodySpans = rest
            )
        }
    }
    return null
}

private fun sectionBadgeForHeadingText(raw: String): String? {
    val (leadingIcon, clean) = NexlyIcons.extractLeadingIcon(raw)
    if (leadingIcon != null) return leadingIcon
    if (clean.isEmpty()) return null
    val lower = clean.lowercase()
    return when {
        "priorit" in lower || "goal" in lower || "objective" in lower -> "target"
        "step-by-step" in lower || "steps" in lower || "plan" in lower || "roadmap" in lower || "what to do" in lower -> "list-check"
        "next action" in lower || "action item" in lower || "checklist" in lower || "todo" in lower || "next step" in lower -> "checkbox"
        "key source" in lower || "sources" in lower -> "link"
        "reference" in lower || "citation" in lower || "further reading" in lower -> "book-2"
        "feature" in lower || "highlight" in lower || "benefit" in lower -> "sparkles"
        "calculator" in lower || "estimator" in lower || "pricing" in lower -> "calculator"
        lower.startsWith("top ") || "chart" in lower || "metric" in lower || "overview" in lower -> "chart-bar"
        "fix" in lower || "solution" in lower || "repair" in lower || "troubleshoot" in lower -> "tool"
        "why " in lower || "how " in lower || "explain" in lower || "reason" in lower -> "bulb"
        "bottom line" in lower || "conclusion" in lower || "verdict" in lower || "takeaway" in lower -> "circle-check"
        "difference" in lower || "compare" in lower || "optional" in lower || "alternative" in lower -> "arrows-diff"
        "code" in lower || "sql" in lower || "query" in lower || "script" in lower -> "code"
        else -> "sparkles"
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
        NexlyGlassGlyphTile(
            name = callout.icon,
            toneName = if (isWarn) "amber" else "lavender",
            size = 28.dp,
            glyphSize = 16.dp
        )
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = NexlyIcons.cleanPlainText(callout.title),
                style = bodyStyle.copy(fontWeight = FontWeight.Bold),
                color = titleColor
            )
            if (bodyText != null && bodyText.isNotEmpty()) {
                val inlineContent = rememberNexlyInlineContent(bodyText)
                Text(
                    text = bodyText,
                    style = bodyStyle,
                    color = bodyColor,
                    inlineContent = inlineContent
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
    val leadingIcon = remember(block.spans) {
        NexlyIcons.extractLeadingIcon(plainTextOfSpans(block.spans)).first
    }
    val displaySpans = remember(block.spans, leadingIcon) {
        if (leadingIcon != null) stripLeadingIconFromSpans(block.spans) else block.spans
    }
    val styled = remember(displaySpans, theme, showCaret) {
        spansToAnnotatedString(displaySpans, theme, showCaret)
    }
    val inlineContent = rememberNexlyInlineContent(styled)
    val text = @Composable {
        if (leadingIcon != null) {
            Row(verticalAlignment = Alignment.Top) {
                NexlyGlassGlyphTile(
                    name = leadingIcon,
                    toneName = NexlyIcons.toneFor(leadingIcon),
                    size = 21.dp,
                    glyphSize = 12.dp,
                    modifier = Modifier.padding(top = 1.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = styled,
                    style = bodyStyle,
                    color = ink,
                    inlineContent = inlineContent
                )
            }
        } else {
            Text(
                text = styled,
                style = bodyStyle,
                color = ink,
                inlineContent = inlineContent
            )
        }
    }
    if (selectable) SelectionContainer { text() } else text()
}

private fun stripLeadingIconFromSpans(spans: List<InlineSpan>): List<InlineSpan> {
    val first = spans.firstOrNull() ?: return spans
    return when (first) {
        is InlineSpan.TextSpan -> {
            val (_, rest) = NexlyIcons.extractLeadingIcon(first.text)
            if (rest == first.text) spans
            else if (rest.isEmpty()) spans.drop(1)
            else listOf(InlineSpan.TextSpan(rest)) + spans.drop(1)
        }
        is InlineSpan.BoldSpan -> {
            val inner = stripLeadingIconFromSpans(first.children)
            if (inner.isEmpty()) spans.drop(1)
            else listOf(InlineSpan.BoldSpan(inner)) + spans.drop(1)
        }
        is InlineSpan.ItalicSpan -> {
            val inner = stripLeadingIconFromSpans(first.children)
            if (inner.isEmpty()) spans.drop(1)
            else listOf(InlineSpan.ItalicSpan(inner)) + spans.drop(1)
        }
        else -> spans
    }
}

@Composable
private fun HeadingBlock(block: Block.Heading, isFirst: Boolean, citationUrls: Map<Int, String>) {
    val theme = rememberSpanTheme(MaterialTheme.typography.bodyMedium.fontSize, citationUrls)
    val rawHeadingText = remember(block.spans) { plainTextOfSpans(block.spans) }
    val badge = remember(rawHeadingText, block.level) {
        if (block.level in 2..3) sectionBadgeForHeadingText(rawHeadingText)
        else NexlyIcons.extractLeadingIcon(rawHeadingText).first
    }
    val cleanSpans = remember(block.spans) {
        stripLeadingIconFromSpans(block.spans)
    }
    val styled = remember(cleanSpans, theme) { spansToAnnotatedString(cleanSpans, theme) }
    val inlineContent = rememberNexlyInlineContent(styled)
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
                    NexlyGlassGlyphTile(
                        name = badge,
                        size = if (block.level <= 2) 26.dp else 22.dp,
                        glyphSize = if (block.level <= 2) 15.dp else 13.dp
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    text = styled,
                    style = style,
                    color = if (block.level == 6) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    inlineContent = inlineContent,
                    modifier = Modifier.semantics { heading() }
                )
            }
        }
    }
}

internal fun sourceLoanMonthlyPayment(principal: Double, apr: Double, years: Int): Double? {
    if (!principal.isFinite() || !apr.isFinite() || principal < 0 || apr < 0 || years <= 0) return null
    val months = years.toDouble() * 12.0
    val rate = apr / 1200.0
    // log1p/expm1 retain real interest at small rates; a tolerance-based
    // zero-rate substitution silently erased interest on large principals.
    val payment = if (rate == 0.0) principal / months
        else principal * (rate / -kotlin.math.expm1(-months * kotlin.math.ln1p(rate)))
    return payment.takeIf { it.isFinite() }
}

@Composable
private fun NexlyQuickCalculatorCard(
    title: String,
    initialAmount: Double,
    initialDiscount: Double,
    initialYears: Int = 5,
    contextHint: String = ""
) {
    val lowerHint = remember(title, contextHint) { "$title $contextHint".lowercase() }
    // Modes: 0 = Discount (-%), 1 = Tax/Tip (+%), 2 = Compound (Yr), 3 = Loan/EMI, 4 = Budget 50/30/20, 5 = Macros/TDEE
    val initialMode = remember(lowerHint) {
        when {
            "loan" in lowerHint || "mortgage" in lowerHint || "emi" in lowerHint || "amortiz" in lowerHint || "apr" in lowerHint -> 3
            "compound" in lowerHint || "roi" in lowerHint || "invest" in lowerHint || "portfolio" in lowerHint || "apy" in lowerHint || "wealth" in lowerHint -> 2
            "budget" in lowerHint || "50/30/20" in lowerHint || "salary" in lowerHint || "income" in lowerHint -> 4
            "calorie" in lowerHint || "protein" in lowerHint || "macro" in lowerHint || "tdee" in lowerHint || "bmi" in lowerHint -> 5
            "tax" in lowerHint || "markup" in lowerHint || "tip" in lowerHint || "vat" in lowerHint -> 1
            else -> 0
        }
    }
    val currency = remember(contextHint) {
        when {
            "€" in contextHint -> "€"
            "£" in contextHint -> "£"
            "¥" in contextHint -> "¥"
            else -> "$"
        }
    }
    var mode by remember(initialMode) { mutableStateOf(initialMode) }
    var years by remember(initialYears, initialMode) {
        mutableStateOf(initialYears.coerceIn(1, 40))
    }
    var amountText by remember(initialAmount, initialMode) {
        val seedAmt = if (initialMode == 5 && initialAmount > 300.0) 75.0 else initialAmount
        mutableStateOf(
            if (seedAmt % 1.0 == 0.0) seedAmt.toLong().toString()
            else String.format(java.util.Locale.US, "%.2f", seedAmt)
        )
    }
    var rateText by remember(initialDiscount, initialMode) {
        val seedRate = if (initialMode == 5 && initialDiscount > 5.0) 2.0 else initialDiscount
        mutableStateOf(
            if (seedRate % 1.0 == 0.0) seedRate.toLong().toString()
            else String.format(java.util.Locale.US, "%.1f", seedRate)
        )
    }
    val amount = amountText.replace(",", "").toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: Double.NaN
    val ratePct = rateText.replace("%", "").toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: Double.NaN

    val monthlyLoanPayment = remember(amount, ratePct, years) {
        sourceLoanMonthlyPayment(amount, ratePct, years) ?: Double.NaN
    }
    val totalLoanPaid = monthlyLoanPayment * years * 12
    val totalLoanInterest = (totalLoanPaid - amount).coerceAtLeast(0.0)

    val total = when (mode) {
        1 -> amount * (1.0 + ratePct / 100.0)
        2 -> amount * Math.pow(1.0 + ratePct / 100.0, years.toDouble())
        3 -> monthlyLoanPayment
        4 -> amount * (ratePct / 100.0)
        5 -> amount * 33.0
        else -> amount * (1.0 - ratePct / 100.0)
    }
    val deltaVal = kotlin.math.abs(total - amount)
    val modeLabels = listOf("Discount (-%)", "Tax / Tip (+%)", "Compound (Yr)", "Loan / EMI", "Budget Plan", "Macros / TDEE")
    val baseLabel = when (mode) {
        3 -> "Principal ($currency)"
        4 -> "Monthly Income ($currency)"
        5 -> "Weight (kg)"
        else -> "Amount ($currency)"
    }
    val rateLabel = when (mode) {
        1 -> "Rate (+%)"
        2 -> "Annual APY (%)"
        3 -> "Annual APR (%)"
        4 -> "Savings Rate (%)"
        5 -> "Protein (g/kg)"
        else -> "Discount (-%)"
    }
    val resultLabel = when (mode) {
        2 -> "${years}-Yr Value"
        3 -> "Monthly EMI"
        4 -> "Saved / Mo"
        5 -> "Daily Target"
        else -> "Result"
    }
    val formattedResult = if (!total.isFinite()) "Undefined" else when (mode) {
        3 -> String.format(java.util.Locale.US, "%s%.2f/mo", currency, total)
        5 -> String.format(java.util.Locale.US, "%.0f kcal", total)
        else -> String.format(java.util.Locale.US, "%s%.2f", currency, total)
    }

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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                NexlyGlassGlyphTile(
                    name = when (mode) {
                        3 -> "building-bank"
                        2 -> "trending-up"
                        4 -> "coin"
                        5 -> "barbell"
                        else -> "calculator"
                    },
                    toneName = when (mode) {
                        2 -> "green"
                        4 -> "amber"
                        5 -> "peach"
                        else -> "blue"
                    },
                    size = 26.dp,
                    glyphSize = 15.dp
                )
                Text(
                    text = NexlyIcons.cleanPlainText(title).ifBlank { "Quick Calculator" },
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF211845)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFEDE5FF))

                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = modeLabels[mode],
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5B3DF5)
                    )
                }
                Text(
                    text = "Reset",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF6C4CF6),
                    modifier = Modifier.clickable {
                        mode = initialMode
                        years = initialYears.coerceIn(1, 40)
                        amountText = if (initialAmount % 1.0 == 0.0) initialAmount.toLong().toString()
                        else String.format(java.util.Locale.US, "%.2f", initialAmount)
                        rateText = if (initialDiscount % 1.0 == 0.0) initialDiscount.toLong().toString()
                        else String.format(java.util.Locale.US, "%.1f", initialDiscount)
                    }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(baseLabel, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.92f))
                        .border(1.dp, Color(0xFFE0D8FA), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    BasicTextField(
                        value = amountText,
                        onValueChange = { next ->
                            if (next.length <= 12 && next.all { it.isDigit() || it == '.' }) {
                                amountText = next
                            }
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF211845)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(rateLabel, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.92f))
                        .border(1.dp, Color(0xFFE0D8FA), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    BasicTextField(
                        value = rateText,
                        onValueChange = { next ->
                            if (next.length <= 6 && next.all { it.isDigit() || it == '.' }) {
                                rateText = next
                            }
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF211845)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(resultLabel, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEDE5FF))
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formattedResult,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5B3DF5),
                        maxLines = 1
                    )
                }
            }
        }
        if (mode == 2 || mode == 3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (mode == 3) "Loan Term" else "Compounding Horizon", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (if (mode == 3) listOf(5, 10, 15, 20, 30) else listOf(1, 3, 5, 10, 20)).forEach { yr ->
                        val active = years == yr
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) Color(0xFF6C4CF6) else Color(0xFFF3EEFF))
                                .clickable { years = yr }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${yr}Y",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) Color.White else Color(0xFF4F35E8)
                            )
                        }
                    }
                }
            }
        }
        Slider(
            value = (ratePct.takeIf { it.isFinite() } ?: 0.0).toFloat().coerceIn(0f, if (mode == 5) 4f else 100f),
            enabled = ratePct.isFinite(),
            onValueChange = { v ->
                rateText = if (mode == 5 || mode == 3) String.format(java.util.Locale.US, "%.1f", v)
                else String.format(java.util.Locale.US, "%.0f", v)
            },
            valueRange = if (mode == 5) 0.8f..3.5f else if (mode == 3) 1f..20f else 0f..100f,
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val presets = when (mode) {
                    3 -> listOf(4, 5, 6, 7, 8, 10)
                    5 -> listOf(1, 2, 3)
                    else -> listOf(5, 10, 15, 20, 25, 50)
                }
                presets.forEach { preset ->
                    val active = kotlin.math.abs(ratePct - preset) < 0.5
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) Color(0xFF6C4CF6) else Color(0xFFF3EEFF))
                            .clickable { rateText = preset.toString() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (mode == 5) "${preset}g/kg" else "$preset%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (active) Color.White else Color(0xFF4F35E8)
                        )
                    }
                }
            }
            Text(
                text = if (!total.isFinite()) "Enter valid amount and rate." else when (mode) {
                    1 -> "+${String.format(java.util.Locale.US, "%s%.2f", currency, deltaVal)}"
                    2 -> "Gain +${String.format(java.util.Locale.US, "%s%.2f", currency, deltaVal)}"
                    3 -> "Interest ${String.format(java.util.Locale.US, "%s%.2f · Total %s%.2f", currency, totalLoanInterest, currency, totalLoanPaid)}"
                    4 -> "Needs ${String.format(java.util.Locale.US, "%s%.0f · Annual +%s%.0f", currency, amount * 0.5, currency, total * 12)}"
                    5 -> "Protein ${String.format(java.util.Locale.US, "%.0fg · Water %.1fL", amount * ratePct.coerceAtLeast(1.2), amount * 0.035)}"
                    else -> "Saved ${String.format(java.util.Locale.US, "%s%.2f", currency, deltaVal)}"
                },
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF5B3DF5)
            )
        }
    }
}

private data class SpontaneousMetricItem(
    val label: String,
    val value: Double,
    val unit: String,
    val icon: String,
    val tone: String
)

private fun extractSpontaneousMetricsFromBlocks(blocks: List<Block>): List<SpontaneousMetricItem> {
    val items = mutableListOf<SpontaneousMetricItem>()
    val itemRegex = Regex("""^([A-Za-z][A-Za-z0-9\s/+.#&-]{1,28})\s*(?:—|–|-|:)\s*([$€£¥])?\s*(\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?)\s*([kKmMbB%])?\s*$""")
    blocks.forEach { b ->
        val rawItems = when (b) {
            is Block.BulletList -> if (b.items.all { it.checked == null }) b.items else emptyList()
            is Block.OrderedList -> b.items
            else -> emptyList()
        }
        rawItems.forEach { li ->
            val plain = NexlyIcons.cleanPlainText(plainTextOfSpans(li.spans))
            val m = itemRegex.find(plain)
            if (m != null) {
                val label = m.groupValues[1].trim()
                val curr = m.groupValues[2]
                val num = m.groupValues[3].replace(",", "").toDoubleOrNull()
                val suf = m.groupValues[4]
                if (num != null && num > 0.0 && num !in 1990.0..2035.0) {
                    val mult = when (suf.lowercase()) {
                        "k" -> 1_000.0
                        "m" -> 1_000_000.0
                        "b" -> 1_000_000_000.0
                        else -> 1.0
                    }
                    val unit = if (curr.isNotEmpty()) curr else if (suf == "%") "%" else ""
                    val (icon, tone) = NexlyIcons.semanticIconFor(label, "chart-bar")
                    items += SpontaneousMetricItem(label, num * mult, unit, icon, tone)
                }
            }
        }
    }
    // Mixed units and percentages are not additive parts of a whole.
    if (items.map { it.unit }.distinct().size != 1 || items.any { it.unit !in setOf("$", "€", "£", "¥") }) return emptyList()
    return items.distinctBy { it.label.lowercase() }.take(8)
}

@Composable
private fun NexlyQuantitativeExplorerCard(items: List<SpontaneousMetricItem>) {
    var scale by remember { mutableStateOf(1.0) }
    var sortDesc by remember { mutableStateOf(true) }
    var selectedIdx by remember { mutableStateOf(0) }
    val sorted = remember(items, sortDesc) {
        if (sortDesc) items.sortedByDescending { it.value } else items
    }
    val maxVal = remember(sorted, scale) {
        (sorted.maxOfOrNull { it.value * scale } ?: 1.0).coerceAtLeast(0.0001)
    }
    val totalVal = remember(sorted, scale) {
        sorted.sumOf { it.value * scale }.coerceAtLeast(0.0001)
    }
    val activeItem = sorted.getOrElse(selectedIdx.coerceIn(0, sorted.lastIndex)) { sorted.first() }
    val unit = activeItem.unit

    fun fmtVal(v: Double): String {
        val prefix = if (unit in listOf("$", "€", "£", "¥")) unit else ""
        val suffix = if (unit == "%") "%" else ""
        return when {
            v >= 1_000_000 -> String.format(java.util.Locale.US, "%s%.1fM%s", prefix, v / 1_000_000.0, suffix)
            v >= 1_000 -> String.format(java.util.Locale.US, "%s%.1fK%s", prefix, v / 1_000.0, suffix)
            v % 1.0 == 0.0 -> String.format(java.util.Locale.US, "%s%.0f%s", prefix, v, suffix)
            else -> String.format(java.util.Locale.US, "%s%.1f%s", prefix, v, suffix)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.80f))
            .border(1.dp, Color(0xFFE2DAFA), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                NexlyGlassGlyphTile(
                    name = "chart-bar",
                    toneName = "lavender",
                    size = 25.dp,
                    glyphSize = 14.dp
                )
                Text(
                    text = "Interactive Data Explorer",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF211845)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(0.5 to "0.5×", 1.0 to "1×", 1.5 to "1.5×", 2.0 to "2×").forEach { (mult, lbl) ->
                    val isSel = scale == mult
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) Color(0xFF6C4CF6) else Color(0xFFF3EEFF))
                            .clickable { scale = mult }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = lbl,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) Color.White else Color(0xFF4F35E8)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFEDE5FF))
                        .clickable { sortDesc = !sortDesc }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (sortDesc) "Rank ↓" else "Original",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5B3DF5)
                    )
                }
            }
        }
        sorted.forEachIndexed { idx, item ->
            val scaledVal = item.value * scale
            val sharePct = (scaledVal / totalVal) * 100.0
            val isSel = idx == selectedIdx.coerceIn(0, sorted.lastIndex)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSel) Color(0xFFF3EEFF) else Color.Transparent)
                    .clickable { selectedIdx = idx }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NexlyGlassGlyphTile(
                            name = item.icon,
                            toneName = item.tone,
                            size = 19.dp,
                            glyphSize = 11.dp
                        )
                        Text(
                            text = item.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.SemiBold,
                            color = Color(0xFF211845)
                        )
                    }
                    Text(
                        text = "${fmtVal(scaledVal)} (${String.format(java.util.Locale.US, "%.0f%%", sharePct)})",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSel) Color(0xFF5B3DF5) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFEAE3FC))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((scaledVal / maxVal).toFloat().coerceIn(0.05f, 1f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isSel) Color(0xFF5B3DF5) else Color(0xFF8C72F8))
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFFF6F2FF))
                .padding(horizontal = 9.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Selected: ${activeItem.label} (${fmtVal(activeItem.value * scale)})",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF211845)
            )
            Text(
                text = "Total: ${fmtVal(totalVal)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5B3DF5)
            )
        }
    }
}

private data class SpontaneousConceptItem(
    val term: String,
    val definition: String,
    val icon: String,
    val tone: String
)

private fun extractSpontaneousConceptsFromBlocks(blocks: List<Block>): List<SpontaneousConceptItem> {
    val out = mutableListOf<SpontaneousConceptItem>()
    val sepRegex = Regex("""^([^:—–-]{3,42})\s*(?::|—|–|-)\s*(.{14,})$""")
    blocks.forEachIndexed { idx, b ->
        val rawItems = when (b) {
            is Block.BulletList -> if (b.items.all { it.checked == null }) b.items else emptyList()
            is Block.OrderedList -> b.items
            else -> emptyList()
        }
        rawItems.forEach { li ->
            val first = li.spans.firstOrNull()
            if (first is InlineSpan.BoldSpan) {
                val term = NexlyIcons.cleanPlainText(plainTextOfSpans(first.children)).trimEnd(':', '-', '—', '–').trim()
                val rest = NexlyIcons.cleanPlainText(plainTextOfSpans(li.spans.drop(1))).replace(Regex("""^[\s:—–-]+"""), "").trim()
                if (term.length in 3..44 && rest.length >= 14 && "http" !in rest.lowercase()) {
                    val (icon, tone) = NexlyIcons.semanticIconFor("$term $rest", "bulb")
                    out += SpontaneousConceptItem(term, rest.take(180), icon, tone)
                }
            } else {
                val plain = NexlyIcons.cleanPlainText(plainTextOfSpans(li.spans))
                val m = sepRegex.find(plain)
                if (m != null) {
                    val term = m.groupValues[1].trim()
                    val rest = m.groupValues[2].trim()
                    if (term.length in 3..44 && rest.length >= 14 && "http" !in rest.lowercase()) {
                        val (icon, tone) = NexlyIcons.semanticIconFor("$term $rest", "bulb")
                        out += SpontaneousConceptItem(term, rest.take(180), icon, tone)
                    }
                }
            }
        }
        if (b is Block.Heading && b.level in 2..3 && idx + 1 < blocks.size) {
            val hText = NexlyIcons.cleanPlainText(plainTextOfSpans(b.spans)).trimEnd(':').trim()
            val next = blocks[idx + 1]
            if (next is Block.Paragraph && classifyCalloutHeaderTitle(hText) == null && classifySectionListMode(hText) == SectionListMode.DEFAULT) {
                val pText = NexlyIcons.cleanPlainText(plainTextOfSpans(next.spans)).trim()
                if (hText.length in 3..44 && pText.length >= 20) {
                    val (icon, tone) = NexlyIcons.semanticIconFor("$hText $pText", "bulb")
                    out += SpontaneousConceptItem(hText, pText.take(180), icon, tone)
                }
            }
        }
    }
    return out.distinctBy { it.term.lowercase() }.take(8)
}

@Composable
private fun NexlyInteractiveConceptDeckCard(concepts: List<SpontaneousConceptItem>) {
    var quizMode by remember { mutableStateOf(false) }
    var cardIdx by remember { mutableStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    var masteredSet by remember { mutableStateOf(setOf<Int>()) }
    var quizIdx by remember { mutableStateOf(0) }
    var quizSelected by remember { mutableStateOf<Int?>(null) }
    var quizScore by remember { mutableStateOf(0) }
    var quizAnsweredCount by remember { mutableStateOf(0) }

    val safeCardIdx = cardIdx.coerceIn(0, concepts.lastIndex)
    val currentCard = concepts[safeCardIdx]
    val safeQuizIdx = quizIdx.coerceIn(0, concepts.lastIndex)
    val quizTarget = concepts[safeQuizIdx]
    val quizOptions = remember(concepts, safeQuizIdx) {
        val distractors = concepts.indices.filter { it != safeQuizIdx }.take(3)
        (listOf(safeQuizIdx) + distractors).sortedBy { (it * 7 + safeQuizIdx * 3) % 11 }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.82f))
            .border(1.dp, Color(0xFFE2DAFA), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                NexlyGlassGlyphTile(
                    name = if (quizMode) "school" else "cards",
                    toneName = "lavender",
                    size = 25.dp,
                    glyphSize = 14.dp
                )
                Text(
                    text = if (quizMode) "Interactive Self-Test" else "Interactive Concept Deck",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF211845)
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFEDE5FF))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf(false to "Flashcards", true to "Quick Quiz").forEach { (isQuiz, label) ->
                    val active = quizMode == isQuiz
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) Color(0xFF6C4CF6) else Color.Transparent)
                            .clickable { quizMode = isQuiz }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (active) Color.White else Color(0xFF4F35E8)
                        )
                    }
                }
            }
        }

        if (!quizMode) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (flipped) Color(0xFFEDE5FF) else Color(0xFFF6F2FF))
                    .border(1.dp, Color(0xFFD9CEFA), RoundedCornerShape(11.dp))
                    .clickable { flipped = !flipped }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NexlyGlassGlyphTile(
                            name = currentCard.icon,
                            toneName = currentCard.tone,
                            size = 20.dp,
                            glyphSize = 12.dp
                        )
                        Text(
                            text = if (flipped) "DEFINITION · TAP TO FLIP" else "CONCEPT ${safeCardIdx + 1}/${concepts.size} · TAP TO FLIP",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5B3DF5)
                        )
                    }
                    if (safeCardIdx in masteredSet) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            NexlyGlyph(name = "circle-check", size = 12.dp, color = Color(0xFF129A5A))
                            Text(
                                text = "Mastered",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF129A5A)
                            )
                        }
                    }
                }
                Text(
                    text = if (flipped) currentCard.definition else currentCard.term,
                    fontSize = if (flipped) 12.5.sp else 14.sp,
                    fontWeight = if (flipped) FontWeight.Medium else FontWeight.Bold,
                    color = Color(0xFF211845)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFFF3EEFF))
                            .clickable {
                                cardIdx = (safeCardIdx - 1 + concepts.size) % concepts.size
                                flipped = false
                            }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text("Prev", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4F35E8))
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFFF3EEFF))
                            .clickable {
                                cardIdx = (safeCardIdx + 1) % concepts.size
                                flipped = false
                            }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text("Next", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4F35E8))
                    }
                }
                val isMastered = safeCardIdx in masteredSet
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isMastered) Color(0xFFE6F9F0) else Color(0xFFEDE5FF))
                        .clickable {
                            masteredSet = if (isMastered) masteredSet - safeCardIdx else masteredSet + safeCardIdx
                        }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    NexlyGlyph(
                        name = if (isMastered) "circle-check" else "sparkles",
                        size = 12.dp,
                        color = if (isMastered) Color(0xFF129A5A) else Color(0xFF5B3DF5)
                    )
                    Text(
                        text = "${masteredSet.size}/${concepts.size} Mastered",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isMastered) Color(0xFF129A5A) else Color(0xFF5B3DF5)
                    )
                }
            }
        } else {
            Text(
                text = quizTarget.definition,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF211845),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF6F2FF))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                quizOptions.forEach { optConceptIdx ->
                    val optConcept = concepts[optConceptIdx]
                    val isChosen = quizSelected == optConceptIdx
                    val isCorrect = optConceptIdx == safeQuizIdx
                    val revealed = quizSelected != null
                    val bg = when {
                        revealed && isCorrect -> Color(0xFFE6F9F0)
                        revealed && isChosen -> Color(0xFFFDECEC)
                        else -> Color(0xFFF3EEFF)
                    }
                    val borderCol = when {
                        revealed && isCorrect -> Color(0xFF129A5A)
                        revealed && isChosen -> Color(0xFFE04F5F)
                        else -> Color(0xFFD9CEFA)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .border(1.dp, borderCol, RoundedCornerShape(8.dp))
                            .clickable(enabled = !revealed) {
                                quizSelected = optConceptIdx
                                quizAnsweredCount += 1
                                if (isCorrect) quizScore += 1
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            NexlyGlassGlyphTile(
                                name = optConcept.icon,
                                toneName = optConcept.tone,
                                size = 18.dp,
                                glyphSize = 10.dp
                            )
                            Text(
                                text = optConcept.term,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF211845)
                            )
                        }
                        if (revealed && (isCorrect || isChosen)) {
                            NexlyGlyph(
                                name = if (isCorrect) "circle-check" else "circle-x",
                                size = 14.dp,
                                color = if (isCorrect) Color(0xFF129A5A) else Color(0xFFE04F5F)
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Score: $quizScore / ${quizAnsweredCount.coerceAtLeast(1)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5B3DF5)
                )
                if (quizSelected != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFF6C4CF6))
                            .clickable {
                                quizSelected = null
                                quizIdx = (safeQuizIdx + 1) % concepts.size
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Next Question", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

private data class ParsedSourceItem(
    val number: Int,
    val title: String,
    val snippet: String,
    val domain: String,
    val url: String
)

private fun extractSourceItemFromItem(number: Int, item: ListItem): ParsedSourceItem? {
    val combined = mutableListOf<InlineSpan>()
    combined.addAll(item.spans)
    item.children.forEach { child ->
        if (child is Block.Paragraph) {
            combined.add(InlineSpan.TextSpan(" "))
            combined.addAll(child.spans)
        }
    }
    return extractSourceItemFromSpans(number, combined)
}

private fun extractSourceItemFromSpans(number: Int, spans: List<InlineSpan>): ParsedSourceItem? {
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
    var isAddingTask by remember(block.sourceStart) { mutableStateOf(false) }
    var draftTaskText by remember(block.sourceStart) { mutableStateOf("") }
    val isChecklist = sectionMode == SectionListMode.TODO || block.items.any { it.checked != null }
    var checkedMap by remember(block.sourceStart, block.items.size) {
        mutableStateOf(
            block.items.mapIndexed { idx, item -> idx to (item.checked ?: false) }.toMap()
        )
    }

    val sourceItems = remember(block.items, sectionMode) {
        if (sectionMode == SectionListMode.SOURCES) {
            block.items.mapIndexedNotNull { idx, item -> extractSourceItemFromItem(idx + 1, item) }
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

    val totalTaskCount = block.items.size + addedTasks.size
    val completedTaskCount = (0 until totalTaskCount).count { checkedMap[it] == true }
    val completionPct = if (totalTaskCount > 0) (completedTaskCount * 100) / totalTaskCount else 0

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (isChecklist && totalTaskCount >= 2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF3EEFF))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NexlyGlassGlyphTile(
                        name = if (completedTaskCount == totalTaskCount) "trophy" else "list-check",
                        toneName = if (completedTaskCount == totalTaskCount) "green" else "lavender",
                        size = 18.dp,
                        glyphSize = 11.dp
                    )
                    Text(
                        text = "$completedTaskCount of $totalTaskCount completed",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF211845)
                    )
                }
                Text(
                    text = "$completionPct%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (completedTaskCount == totalTaskCount) Color(0xFF129A5A) else Color(0xFF5B3DF5)
                )
            }
        }
        block.items.forEachIndexed { idx, item ->
            if (item.checked != null || sectionMode == SectionListMode.TODO) {
                InteractiveChecklistItemRow(
                    initialChecked = checkedMap[idx] ?: (item.checked ?: false),
                    item = item,
                    index = idx,
                    selectable = selectable,
                    citationUrls = citationUrls,
                    onCheckedChange = { next -> checkedMap = checkedMap + (idx to next) }
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
            val taskIdx = block.items.size + idx
            InteractiveChecklistItemRow(
                initialChecked = checkedMap[taskIdx] ?: false,
                item = ListItem(spans = listOf(InlineSpan.TextSpan(taskText)), checked = false),
                index = taskIdx,
                selectable = false,
                citationUrls = citationUrls,
                onCheckedChange = { next -> checkedMap = checkedMap + (taskIdx to next) },
                onRemove = {
                    addedTasks = addedTasks.filterIndexed { i, _ -> i != idx }
                }
            )
        }
        if (sectionMode == SectionListMode.TODO || block.items.any { it.checked != null }) {
            if (isAddingTask) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.92f))
                        .border(1.dp, Color(0xFFD6CBF9), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (draftTaskText.isEmpty()) {
                            Text(
                                text = "Type a new task…",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = draftTaskText,
                            onValueChange = { draftTaskText = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 12.5.sp,
                                color = Color(0xFF211845)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFF6C4CF6))
                            .clickable {
                                val clean = draftTaskText.trim()
                                if (clean.isNotEmpty()) {
                                    addedTasks = addedTasks + clean
                                    draftTaskText = ""
                                    isAddingTask = false
                                }
                            }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Add",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "Cancel",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable {
                            draftTaskText = ""
                            isAddingTask = false
                        }
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.8f))
                        .border(1.dp, Color(0xFFE0D8FA), RoundedCornerShape(999.dp))
                        .clickable { isAddingTask = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    NexlyGlyph(
                        name = "plus",
                        size = 12.dp,
                        color = Color(0xFF5B3DF5)
                    )
                    Text(
                        text = "Add Task",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5B3DF5)
                    )
                }
            }
        }
    }
}

private fun extractExplicitTaskTag(text: String): String? {
    val m = Regex("(?:\\(([^)]{2,18})\\)|\\[([^\\]]{2,18})])$").find(text.trim())
    return m?.groupValues?.get(1)?.ifEmpty { m.groupValues[2] }?.trim()
}

@Composable
private fun InteractiveChecklistItemRow(
    initialChecked: Boolean,
    item: ListItem,
    index: Int = 0,
    selectable: Boolean,
    citationUrls: Map<Int, String>,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    var checked by remember(item.spans, initialChecked) { mutableStateOf(initialChecked) }
    var statusIdx by remember(item.spans, index) { mutableStateOf(0) }
    val statusOptions = remember { listOf("Todo", "In Progress") }
    val bodyStyle = LocalNexlyProseStyle.current ?: MaterialTheme.typography.bodyMedium
    val ink = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else (bodyStyle.color.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface)
    val theme = rememberSpanTheme(bodyStyle.fontSize, citationUrls, ink)
    val styled = remember(item.spans, theme) { spansToAnnotatedString(item.spans, theme) }
    val explicitTag = remember(item.spans) { extractExplicitTaskTag(plainTextOfSpans(item.spans)) }
    val statusLabel = when {
        checked -> "Done"
        explicitTag != null -> explicitTag
        else -> statusOptions[statusIdx % statusOptions.size]
    }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .clickable {
                    val next = !checked
                    checked = next
                    onCheckedChange?.invoke(next)
                }
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
                    Icon(
                        imageVector = GsIcons.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            Spacer(Modifier.width(9.dp))
            val checklistInlineContent = rememberNexlyInlineContent(styled)
            Text(
                text = styled,
                style = bodyStyle.copy(
                    textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
                ),
                color = ink,
                inlineContent = checklistInlineContent,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        when {
                            checked -> Color(0xFFE6F9F0)
                            statusLabel.equals("High", true) || statusLabel.equals("Urgent", true) -> Color(0xFFFDECEC)
                            statusLabel == "In Progress" -> Color(0xFFFEF3C7)
                            else -> Color(0xFFF3EEFF)
                        }
                    )
                    .clickable(enabled = !checked && explicitTag == null) {
                        statusIdx = (statusIdx + 1) % statusOptions.size
                    }
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = statusLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        checked -> Color(0xFF129A5A)
                        statusLabel.equals("High", true) || statusLabel.equals("Urgent", true) -> Color(0xFFE04F5F)
                        statusLabel == "In Progress" -> Color(0xFFB45309)
                        else -> Color(0xFF5B3DF5)
                    }
                )
            }
            if (onRemove != null) {
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onRemove() }
                        .padding(3.dp)
                ) {
                    NexlyGlyph(
                        name = "x",
                        size = 11.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
                item = item
            )
        }
        if (extracted.isNotEmpty() && extracted.size == block.items.size) {
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

    if (sectionMode == SectionListMode.SOURCES) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            block.items.forEachIndexed { index, item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Text("${item.number ?: (block.start + index)}.", style = MaterialTheme.typography.labelMedium)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ParagraphBlock(Block.Paragraph(item.spans, block.sourceStart), showCaret = false, selectable = selectable, citationUrls = citationUrls)
                        item.children.forEach { NestedChildView(it, selectable, citationUrls) }
                    }
                }
            }
        }
        return
    }

    var completedSet by remember(block.sourceStart, block.items.size) {
        mutableStateOf(
            block.items.mapIndexedNotNull { idx, item -> if (item.checked == true) idx else null }.toSet()
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (completedSet.isNotEmpty() && block.items.size >= 2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF3EEFF))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${completedSet.size} of ${block.items.size} steps completed",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF5B3DF5)
                )
                Text(
                    text = "${(completedSet.size * 100) / block.items.size}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (completedSet.size == block.items.size) Color(0xFF129A5A) else Color(0xFF5B3DF5)
                )
            }
        }
        block.items.forEachIndexed { index, item ->
            val number = item.number ?: (block.start + index)
            StepOrderedListItemRow(
                number = number,
                item = item,
                selectable = selectable,
                citationUrls = citationUrls,
                isCompleted = index in completedSet,
                onToggleCompleted = {
                    completedSet = if (index in completedSet) completedSet - index else completedSet + index
                }
            )
        }
    }
}

@Composable
private fun StepOrderedListItemRow(
    number: Int,
    item: ListItem,
    selectable: Boolean,
    citationUrls: Map<Int, String>,
    isCompleted: Boolean = false,
    onToggleCompleted: (() -> Unit)? = null
) {
    var localCompleted by remember(item.spans, number) { mutableStateOf(item.checked ?: false) }
    val completed = if (onToggleCompleted != null) isCompleted else localCompleted
    val bodyStyle = LocalNexlyProseStyle.current ?: MaterialTheme.typography.bodyMedium
    val ink = if (completed) MaterialTheme.colorScheme.onSurfaceVariant else (bodyStyle.color.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface)
    val theme = rememberSpanTheme(bodyStyle.fontSize, citationUrls, ink)

    // Detect "1. **Step Title** — Step description" OR two-line "1. **Step Title**\n   Step description" from Screenshot 3
    val splitStep = remember(item.spans, item.children) {
        val first = item.spans.firstOrNull()
        if (first is InlineSpan.BoldSpan) {
            val rawTitle = plainTextOfSpans(first.children).trim().trimEnd(':').trim()
            val (leadIcon, cleanedTitle) = NexlyIcons.extractLeadingIcon(rawTitle)
            val title = NexlyIcons.cleanPlainText(cleanedTitle.ifEmpty { rawTitle }).trim()
            if (title.isNotEmpty()) {
                if (item.spans.size >= 2) {
                    val rest = item.spans.drop(1).toMutableList()
                    val second = rest.firstOrNull()
                    if (second is InlineSpan.TextSpan) {
                        val stripped = second.text.replace(Regex("^\\s*[—–\\-:]\\s*"), "").trimStart()
                        if (stripped.isEmpty()) rest.removeAt(0) else rest[0] = InlineSpan.TextSpan(stripped)
                    }
                    if (rest.isNotEmpty()) Triple(leadIcon to title, rest.toList(), false) else null
                } else {
                    val firstChild = item.children.firstOrNull() as? Block.Paragraph
                    if (firstChild != null && firstChild.spans.isNotEmpty()) {
                        Triple(leadIcon to title, firstChild.spans, true)
                    } else null
                }
            } else null
        } else null
    }
    val remainingChildren = remember(item.children, splitStep) {
        if (splitStep?.third == true) item.children.drop(1) else item.children
    }

    Column {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    if (onToggleCompleted != null) onToggleCompleted()
                    else localCompleted = !localCompleted
                }
                .padding(vertical = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (completed) Color(0xFFDCFCE7) else Color(0xFFEDE5FF))
                    .border(1.dp, if (completed) Color(0xFF22C55E) else Color(0xFFD5C6FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (completed) {
                    NexlyGlyph(
                        name = "check",
                        size = 13.dp,
                        color = Color(0xFF15803D)
                    )
                } else {
                    Text(
                        text = number.toString(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5B3DF5)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (splitStep != null) {
                    val detailStyled = remember(splitStep.second, theme) {
                        spansToAnnotatedString(splitStep.second, theme)
                    }
                    val stepIcon = splitStep.first.first
                    val stepTitle = splitStep.first.second
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (stepIcon != null) {
                            NexlyGlassGlyphTile(
                                name = stepIcon,
                                size = 18.dp,
                                glyphSize = 11.dp
                            )
                        }
                        Text(
                            text = stepTitle,
                            style = bodyStyle.copy(
                                fontWeight = FontWeight.Bold,
                                textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            color = ink
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    val detailInlineContent = rememberNexlyInlineContent(detailStyled)
                    Text(
                        text = detailStyled,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        inlineContent = detailInlineContent
                    )
                } else {
                    val styled = remember(item.spans, theme) { spansToAnnotatedString(item.spans, theme) }
                    val stepInlineContent = rememberNexlyInlineContent(styled)
                    Text(
                        text = styled,
                        style = bodyStyle.copy(
                            textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = ink,
                        inlineContent = stepInlineContent
                    )
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
    val leadingIconAndSpans = remember(item.spans) {
        val first = item.spans.firstOrNull()
        when (first) {
            is InlineSpan.TextSpan -> {
                val (icon, stripped) = NexlyIcons.extractLeadingIcon(first.text)
                if (icon != null) {
                    icon to (listOf(InlineSpan.TextSpan(stripped)) + item.spans.drop(1))
                } else {
                    val (semIcon, _) = NexlyIcons.semanticIconFor(plainTextOfSpans(item.spans), "")
                    (semIcon.takeIf { it.isNotBlank() && it != "sparkles" }) to item.spans
                }
            }
            is InlineSpan.BoldSpan -> {
                val innerFirst = first.children.firstOrNull()
                if (innerFirst is InlineSpan.TextSpan) {
                    val (icon, stripped) = NexlyIcons.extractLeadingIcon(innerFirst.text)
                    if (icon != null) {
                        val newBold = InlineSpan.BoldSpan(listOf(InlineSpan.TextSpan(stripped)) + first.children.drop(1))
                        icon to (listOf(newBold) + item.spans.drop(1))
                    } else {
                        val (semIcon, _) = NexlyIcons.semanticIconFor(plainTextOfSpans(item.spans), "")
                        (semIcon.takeIf { it.isNotBlank() && it != "sparkles" }) to item.spans
                    }
                } else null to item.spans
            }
            else -> null to item.spans
        }
    }
    val leadingIcon = leadingIconAndSpans.first
    val styled = remember(leadingIconAndSpans.second, theme) {
        spansToAnnotatedString(leadingIconAndSpans.second, theme)
    }
    val bulletInlineContent = rememberNexlyInlineContent(styled)
    Column {
        Row(verticalAlignment = Alignment.Top) {
            if (leadingIcon != null) {
                NexlyGlassGlyphTile(
                    name = leadingIcon,
                    size = 20.dp,
                    glyphSize = 12.dp,
                    modifier = Modifier.padding(top = 1.dp)
                )
                Spacer(Modifier.width(8.dp))
            } else if (marker == "✓") {
                NexlyGlassGlyphTile(
                    name = "circle-check",
                    toneName = "lavender",
                    size = 20.dp,
                    glyphSize = 12.dp,
                    modifier = Modifier.padding(top = 1.dp)
                )
                Spacer(Modifier.width(8.dp))
            } else {
                Text(
                    text = marker,
                    style = bodyStyle.copy(fontWeight = FontWeight.Bold),
                    color = markerColor,
                    modifier = Modifier.padding(end = 7.dp)
                )
            }
            val content = @Composable {
                Text(
                    text = styled,
                    style = bodyStyle,
                    color = ink,
                    inlineContent = bulletInlineContent
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

internal fun parseSortableNumberAndroid(raw: String): Double? {
    val clean = raw.replace(Regex("[,$€£¥%↑↓↗↘+\\s]"), "").trim()
    if (clean.isEmpty()) return null
    val mult = when (clean.last().lowercaseChar()) {
        'k' -> 1_000.0
        'm' -> 1_000_000.0
        'b' -> 1_000_000_000.0
        't' -> 1_000_000_000_000.0
        else -> 1.0
    }
    val numPart = if (mult > 1.0) clean.dropLast(1).trim() else clean
    val base = numPart.toDoubleOrNull() ?: return null
    return base * mult
}

@Composable
private fun TableBlockView(block: Block.TableBlock, selectable: Boolean, citationUrls: Map<Int, String>) {
    val theme = rememberSpanTheme(MaterialTheme.typography.bodySmall.fontSize, citationUrls)
    val outline = MaterialTheme.colorScheme.outline
    val headers = remember(block.headers, theme) { block.headers.map { spansToAnnotatedString(it, theme) } }
    val allRows = remember(block.rows, theme) { block.rows.map { row -> row.map { spansToAnnotatedString(it, theme) } } }
    val columns = maxOf(headers.size, block.aligns.size, 1)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val bodyStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
    val headerStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    // Equal-width rank/data columns left only ~40dp for a language after its
    // glass badge, splitting JavaScript while clipping the actual trend. Size
    // from all supplied rows (not just this page), then scroll if necessary.
    val naturalWidths = remember(headers, allRows, density, bodyStyle, headerStyle) {
        (0 until columns).map { column ->
            val header = headers.getOrNull(column)?.text.orEmpty()
            val nameColumn = column == 0 || (column == 1 && headers.firstOrNull()?.text?.trim() in setOf("#", "Rank", "No", "ID"))
            val headerWidth = with(density) { textMeasurer.measure(header, headerStyle, softWrap = false).size.width.toDp() } + 24.dp
            val dataWidth = allRows.maxOfOrNull { row ->
                val raw = row.getOrNull(column)?.text.orEmpty().trim()
                val badgeSpace = if (nameColumn && markdownEntityBadgeForCell(raw) != null) 26.dp else 0.dp
                val trendSpace = if (raw.endsWith("%") && raw.firstOrNull() in listOf('+', '-', '↑', '↓', '↗', '↘')) 16.dp else 0.dp
                with(density) { textMeasurer.measure(raw, bodyStyle, softWrap = false).size.width.toDp() } + 20.dp + badgeSpace + trendSpace
            } ?: 0.dp
            maxOf(44.dp, headerWidth, dataWidth).coerceAtMost(280.dp)
        }
    }

    var sortCol by remember(block) { mutableStateOf<Int?>(null) }
    var sortAsc by remember(block) { mutableStateOf(true) }
    var pageSize by remember(block) { mutableStateOf(if (allRows.size in 4..5) 3 else 5) }
    var page by remember(block) { mutableStateOf(0) }

    val sortedRows = remember(allRows, sortCol, sortAsc) {
        val sc = sortCol ?: return@remember allRows
        allRows.sortedWith { a, b ->
            val av = a.getOrNull(sc)?.text?.trim().orEmpty()
            val bv = b.getOrNull(sc)?.text?.trim().orEmpty()
            val an = parseSortableNumberAndroid(av)
            val bn = parseSortableNumberAndroid(bv)
            val cmp = if (an != null && bn != null) an.compareTo(bn) else av.compareTo(bv, ignoreCase = true)
            if (sortAsc) cmp else -cmp
        }
    }

    val effectivePageSize = widgetTablePageSizeAndroid(pageSize, sortedRows.size)
    val totalPages = maxOf(1, (sortedRows.size + effectivePageSize - 1) / effectivePageSize)
    val safePage = page.coerceIn(0, totalPages - 1)
    val rows = remember(sortedRows, safePage, effectivePageSize) {
        sortedRows.drop(safePage * effectivePageSize).take(effectivePageSize)
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
            val naturalTotal = naturalWidths.fold(0.dp) { total, width -> total + width }
            val fits = naturalTotal <= maxWidth
            val extra = if (fits) (maxWidth - naturalTotal) / columns else 0.dp
            val widths = naturalWidths.map { it + extra }
            val tableWidth = widths.fold(0.dp) { total, width -> total + width }

            val tableHeader: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                headers.forEachIndexed { c, header ->
                    val sorted = sortCol == c
                    val cleanHeaderText = NexlyIcons.cleanPlainText(header.text).ifEmpty { "Column ${c + 1}" }
                    val hLabel = if (sorted) {
                        AnnotatedString("$cleanHeaderText ${if (sortAsc) "↑" else "↓"}")
                    } else AnnotatedString(cleanHeaderText)
                    Box(
                        modifier = Modifier.clickable {
                            if (sortCol == c) sortAsc = !sortAsc else {
                                sortCol = c
                                sortAsc = true
                            }
                            page = 0
                        }
                    ) {
                        Row {
                            TableCell(
                                text = hLabel,
                                width = widths[c],
                                align = block.aligns.getOrElse(c) { TableAlign.LEFT },
                                emphasize = true
                            )
                        }
                    }
                }
            }

            if (fits) {
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
                    SelectionContainer {
                        Column {
                            rows.forEachIndexed { r, row ->
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    (0 until columns).forEach { c ->
                                        TableCell(
                                            text = row.getOrElse(c) { AnnotatedString("") },
                                            width = widths[c],
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
                }
            } else {
                Column(Modifier.horizontalScroll(rememberScrollState())) {
                    Row(
                        modifier = Modifier.background(Color(0xFFEDE6FF)),
                        content = tableHeader
                    )
                    Box(
                        Modifier
                            .width(tableWidth)
                            .height(1.dp)
                            .background(outline.copy(alpha = 0.35f))
                    )
                    SelectionContainer {
                        Column {
                            rows.forEach { row ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    (0 until columns).forEach { c ->
                                        TableCell(
                                            text = row.getOrElse(c) { AnnotatedString("") },
                                            width = widths[c],
                                            align = block.aligns.getOrElse(c) { TableAlign.LEFT },
                                            emphasize = false,
                                            isPrimaryNameCol = c == 0 || (c == 1 && headers.firstOrNull()?.text?.trim() in setOf("#", "Rank", "No", "ID"))
                                        )
                                    }
                                }
                                Box(
                                    Modifier
                                        .width(tableWidth)
                                        .height(1.dp)
                                        .background(outline.copy(alpha = 0.2f))
                                )
                            }
                        }
                    }
                }
            }
        }

        if (sortedRows.size >= 4) {
            val startRow = safePage * effectivePageSize + 1
            val endRow = minOf(sortedRows.size, (safePage + 1) * effectivePageSize)
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
                    (0 until totalPages).forEach { p ->
                        val active = p == safePage
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (active) Color(0xFF6C4CF6) else Color.Transparent)
                                .clickable { page = p },
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
                    Text(
                        text = "$startRow–$endRow of ${sortedRows.size}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color.White.copy(alpha = 0.85f))
                        .border(1.dp, outline.copy(alpha = 0.35f), RoundedCornerShape(7.dp))
                        .clickable {
                            pageSize = when (pageSize) {
                                3 -> 5
                                5 -> 10
                                10 -> -1
                                else -> 3
                            }
                            page = 0
                        }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Show: ${if (pageSize == -1) "All" else pageSize.toString()} ▾",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun markdownEntityBadgeForCell(raw: String): Pair<String, String>? {
    val clean = NexlyIcons.cleanPlainText(raw)
    if (clean.isEmpty() || clean.length > 32 || clean.all { !it.isLetter() }) return null
    val lower = clean.lowercase()
    return when {
        lower.startsWith("python") -> "brand-python" to "blue"
        lower == "java" -> "coffee" to "amber"
        lower.startsWith("c++") || lower == "cpp" -> "brand-cpp" to "blue"
        lower == "c" || lower == "c#" -> "code" to "lavender"
        lower.startsWith("javascript") || lower == "js" -> "brand-javascript" to "amber"
        lower.startsWith("typescript") || lower == "ts" -> "brand-typescript" to "blue"
        lower.startsWith("rust") -> "brand-rust" to "peach"
        lower == "go" || lower == "golang" -> "brand-golang" to "mint"
        lower.startsWith("swift") -> "brand-apple" to "lavender"
        lower.startsWith("kotlin") || lower.startsWith("android") -> "brand-android" to "green"
        else -> NexlyIcons.semanticIconFor(raw, "sparkles")
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
            NexlyGlassGlyphTile(
                name = entityBadge.first,
                toneName = entityBadge.second,
                size = 20.dp,
                glyphSize = 11.dp
            )
            Text(
                text = AnnotatedString(NexlyIcons.cleanPlainText(raw)),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = cellColor
            )
        }
        return
    }
    val cellInlineContent = rememberNexlyInlineContent(displayText)
    Text(
        text = displayText,
        style = if (emphasize) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
        fontWeight = if (emphasize || isTrend || (isPrimaryNameCol && !raw.all { it.isDigit() })) FontWeight.Bold else FontWeight.Normal,
        color = cellColor,
        inlineContent = cellInlineContent,
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

internal fun citationSourceUrl(raw: String): java.net.URL? = runCatching {
    val url = java.net.URL(raw)
    url.toURI() // Reject malformed authority, whitespace and backslash ambiguities.
    require(url.protocol.lowercase() in setOf("http", "https") && url.host.isNotBlank() && url.userInfo == null && url.port <= 65535)
    if (!url.host.startsWith("[")) java.net.IDN.toASCII(url.host, java.net.IDN.USE_STD3_ASCII_RULES)
    url
}.getOrNull()

internal fun isAcademicSourceDomain(domain: String, raw: String): Boolean {
    val host = citationSourceUrl(raw)?.host?.lowercase() ?: return false
    val publishers = listOf("nature.com", "arxiv.org", "ieee.org", "acm.org", "springer.com", "springernature.com", "sciencedirect.com", "oup.com", "cambridge.org", "nih.gov")
    return host.endsWith(".edu") || host.endsWith(".ac.uk") || publishers.any { host == it || host.endsWith(".$it") }
}

@Composable
private fun CitationsBlockView(block: Block.CitationsBlock, title: String = "Key Sources") {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                NexlyGlassGlyphTile(
                    name = "link",
                    toneName = "lavender",
                    size = 24.dp,
                    glyphSize = 14.dp
                )
                Text(
                    text = title,
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
            val link = remember(citation.url) { citationSourceUrl(citation.url) }
            val domain = link?.host?.lowercase()?.removePrefix("www.") ?: "Invalid source URL"
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
                    .clickable(enabled = link != null) {
                        link?.let { com.grapsee.gsai.ui.components.GsLinks.openInApp(context, it.toExternalForm()) }
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
                        text = NexlyIcons.cleanPlainText(citation.title),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF1F1842)
                    )
                    if (citation.snippet.isNotBlank()) {
                        Text(
                            text = NexlyIcons.cleanPlainText(citation.snippet),
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                            NexlyGlyph(name = "external-link", size = 11.dp, color = Color(0xFF5B3DF5))
                            Text(text = domain, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFF5B3DF5), maxLines = 1)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFFEFEAFF))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            NexlyGlyph(
                                name = if (academic) "building-bank" else "external-link",
                                size = 11.dp,
                                color = Color(0xFF5B3DF5)
                            )
                            Text(
                                text = if (link == null) "Invalid link" else if (academic) "Academic domain" else "Source link",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF5B3DF5)
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
