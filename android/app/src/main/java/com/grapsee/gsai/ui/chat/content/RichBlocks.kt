package com.grapsee.gsai.ui.chat.content

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.GsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import android.graphics.BitmapFactory

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

private class StreamBlockCache {
    var content: String = ""
    var blocks: List<Block> = emptyList()

    fun update(newContent: String, streaming: Boolean): List<Block> {
        val next = if (streaming) {
            parseStreamingBlocks(content, blocks, newContent)
        } else {
            parseBlocks(newContent)
        }
        content = newContent
        blocks = next
        return next
    }
}

@Composable
fun BlocksContent(content: String, isStreaming: Boolean, onCopyCode: (String) -> Unit) {
    val cache = remember { StreamBlockCache() }
    val blocks = remember(content, isStreaming) { cache.update(content, isStreaming) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEachIndexed { index, block ->
            key(block) {
                BlockView(
                    block = block,
                    isLast = index == blocks.lastIndex,
                    isStreaming = isStreaming,
                    onCopyCode = onCopyCode
                )
            }
        }
    }
}

@Composable
private fun BlockView(block: Block, isLast: Boolean, isStreaming: Boolean, onCopyCode: (String) -> Unit) {
    when (block) {
        is Block.Paragraph -> ParagraphBlock(block, showCaret = isStreaming && isLast, selectable = !isStreaming)
        is Block.Heading -> HeadingBlock(block, isFirst = false)
        is Block.BulletList -> BulletListBlock(block, selectable = !isStreaming)
        is Block.OrderedList -> OrderedListBlock(block, selectable = !isStreaming)
        is Block.BlockQuote -> BlockQuoteView(block, selectable = !isStreaming)
        is Block.CodeBlock -> CodeBlockCard(
            language = block.language,
            code = block.code,
            open = block.open || isStreaming,
            onCopyCode = onCopyCode
        )
        is Block.TableBlock -> TableBlockView(block, selectable = !isStreaming)
        is Block.Divider -> DividerBlock()
        is Block.MathBlock -> MathBlockView(block)
        is Block.MermaidBlock -> MermaidBlockView(block, streaming = isStreaming, onCopyCode = onCopyCode)
        is Block.ImageBlock -> ImageBlockView(block)
        is Block.CollapsibleBlock -> CollapsibleBlockView(block)
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
    val fontSize: androidx.compose.ui.unit.TextUnit
)

@Composable
private fun rememberSpanTheme(defaultFontSize: androidx.compose.ui.unit.TextUnit): SpanTheme =
    SpanTheme(
        codeBg = MaterialTheme.colorScheme.surfaceContainerHighest,
        linkColor = MaterialTheme.colorScheme.primary,
        textColor = MaterialTheme.colorScheme.onSurface,
        caretColor = MaterialTheme.colorScheme.primary,
        fontSize = defaultFontSize
    )

private fun appendSpans(builder: AnnotatedString.Builder, spans: List<InlineSpan>, theme: SpanTheme) {
    for (span in spans) {
        when (span) {
            is InlineSpan.TextSpan -> builder.append(span.text)
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
                    TextLinkStyles(style = SpanStyle(color = theme.linkColor, textDecoration = TextDecoration.Underline))
                )
            ) { builder.append(span.text) }
            is InlineSpan.MathSpan -> {
                val rendered = renderMath(span.latex, theme.fontSize, theme.textColor)
                if (rendered != null) builder.append(rendered) else builder.append(span.latex)
            }
        }
    }
}

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

@Composable
private fun ParagraphBlock(block: Block.Paragraph, showCaret: Boolean, selectable: Boolean) {
    if (block.spans.isEmpty()) return
    val theme = rememberSpanTheme(MaterialTheme.typography.bodyMedium.fontSize)
    val styled = remember(block.spans, theme, showCaret) {
        spansToAnnotatedString(block.spans, theme, showCaret)
    }
    val text = @Composable {
        Text(
            text = styled,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    if (selectable) SelectionContainer { text() } else text()
}

@Composable
private fun HeadingBlock(block: Block.Heading, isFirst: Boolean) {
    val theme = rememberSpanTheme(MaterialTheme.typography.bodyMedium.fontSize)
    val styled = remember(block.spans, theme) { spansToAnnotatedString(block.spans, theme) }
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
            Text(
                text = styled,
                style = style,
                color = if (block.level == 6) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
        }
    }
}

@Composable
private fun BulletListBlock(block: Block.BulletList, selectable: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        block.items.forEach { item ->
            ListItemRow(marker = "•", markerColor = MaterialTheme.colorScheme.primary, item = item, selectable = selectable)
        }
    }
}

@Composable
private fun OrderedListBlock(block: Block.OrderedList, selectable: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        block.items.forEachIndexed { index, item ->
            val number = item.number ?: (block.start + index)
            ListItemRow(marker = "$number.", markerColor = MaterialTheme.colorScheme.onSurfaceVariant, item = item, selectable = selectable)
        }
    }
}

@Composable
private fun ListItemRow(marker: String, markerColor: Color, item: ListItem, selectable: Boolean) {
    val theme = rememberSpanTheme(MaterialTheme.typography.bodyMedium.fontSize)
    val styled = remember(item.spans, theme) { spansToAnnotatedString(item.spans, theme) }
    Column {
        Row {
            Text(
                text = marker,
                style = MaterialTheme.typography.bodyMedium,
                color = markerColor,
                modifier = Modifier.padding(end = 6.dp)
            )
            val content = @Composable {
                Text(
                    text = styled,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (selectable) SelectionContainer { content() } else content()
        }
        if (item.children.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item.children.forEach { child -> NestedChildView(child, selectable) }
            }
        }
    }
}

@Composable
private fun NestedChildView(block: Block, selectable: Boolean) {
    when (block) {
        is Block.Paragraph -> ParagraphBlock(block, showCaret = false, selectable = selectable)
        is Block.BulletList -> BulletListBlock(block, selectable)
        is Block.OrderedList -> OrderedListBlock(block, selectable)
        is Block.BlockQuote -> BlockQuoteView(block, selectable)
        else -> BlockView(block, isLast = false, isStreaming = false, onCopyCode = {})
    }
}

@Composable
private fun BlockQuoteView(block: Block.BlockQuote, selectable: Boolean) {
    Row {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        )
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            block.blocks.forEach { inner -> NestedChildView(inner, selectable) }
        }
    }
}

// ---------------------------------------------------------------------------
// Table
// ---------------------------------------------------------------------------

@Composable
private fun TableBlockView(block: Block.TableBlock, selectable: Boolean) {
    val theme = rememberSpanTheme(MaterialTheme.typography.bodySmall.fontSize)
    val outline = MaterialTheme.colorScheme.outline
    val headers = remember(block.headers, theme) { block.headers.map { spansToAnnotatedString(it, theme) } }
    val rows = remember(block.rows, theme) { block.rows.map { row -> row.map { spansToAnnotatedString(it, theme) } } }
    val columns = maxOf(headers.size, block.aligns.size, 1)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val minCell = 96.dp
        val fits = minCell * columns <= maxWidth
        val cellWidth = if (fits) maxWidth / columns else minCell

        val table: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
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
                    Row(modifier = Modifier.fillMaxWidth(), content = table)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(outline.copy(alpha = 0.5f))
                    )
                    rows.forEachIndexed { r, row ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            (0 until columns).forEach { c ->
                                TableCell(
                                    text = row.getOrElse(c) { AnnotatedString("") },
                                    width = cellWidth,
                                    align = block.aligns.getOrElse(c) { TableAlign.LEFT },
                                    emphasize = false
                                )
                            }
                        }
                        if (r != rows.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(outline.copy(alpha = 0.25f))
                            )
                        }
                    }
                }
            }
        } else {
            // Wide table: the TABLE region scrolls horizontally; the
            // conversation scroll is never hijacked (nested scroll is one-axis).
            SelectionContainer {
                Column(Modifier.horizontalScroll(rememberScrollState())) {
                    Row(content = table)
                    Box(
                        Modifier
                            .width(cellWidth * columns)
                            .height(1.dp)
                            .background(outline.copy(alpha = 0.5f))
                    )
                    rows.forEach { row ->
                        Row {
                            (0 until columns).forEach { c ->
                                TableCell(
                                    text = row.getOrElse(c) { AnnotatedString("") },
                                    width = cellWidth,
                                    align = block.aligns.getOrElse(c) { TableAlign.LEFT },
                                    emphasize = false
                                )
                            }
                        }
                        Box(
                            Modifier
                                .width(cellWidth * columns)
                                .height(1.dp)
                                .background(outline.copy(alpha = 0.25f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TableCell(
    text: AnnotatedString,
    width: androidx.compose.ui.unit.Dp,
    align: TableAlign,
    emphasize: Boolean
) {
    Text(
        text = text,
        style = if (emphasize) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
        fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
        color = if (emphasize) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = when (align) {
            TableAlign.LEFT -> TextAlign.Left
            TableAlign.CENTER -> TextAlign.Center
            TableAlign.RIGHT -> TextAlign.Right
        },
        modifier = Modifier
            .width(width)
            .padding(horizontal = 10.dp, vertical = 6.dp)
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

private sealed interface ImageState {
    data object Loading : ImageState
    data class Ready(val bitmap: ImageBitmap) : ImageState
    data object Failed : ImageState
}

private suspend fun fetchBitmap(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.instanceFollowRedirects = true
        connection.inputStream.use { stream ->
            val options = BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 }
            BitmapFactory.decodeStream(stream, null, options)
        }
    }.getOrNull()?.asImageBitmap()
}

@Composable
private fun ImageBlockView(block: Block.ImageBlock) {
    val secure = block.url.startsWith("https://")
    val state by produceState<ImageState>(initialValue = ImageState.Loading, key1 = block.url) {
        if (!secure) {
            value = ImageState.Failed
        } else {
            value = ImageState.Loading
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
                        Icons.Outlined.BrokenImage,
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
    val uriHandler = LocalUriHandler.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(
            onClick = { runCatching { uriHandler.openUri(block.url) } },
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
private fun CollapsibleBlockView(block: Block.CollapsibleBlock) {
    var expanded by remember(block) { mutableStateOf(false) }
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
                Icons.Outlined.ExpandMore,
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
                block.blocks.forEach { inner -> BlockView(inner, isLast = false, isStreaming = false, onCopyCode = {}) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Structured seams — citations & tool results (backend provides; never faked)
// ---------------------------------------------------------------------------

@Composable
private fun CitationsBlockView(block: Block.CitationsBlock) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Sources",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        block.citations.forEach { citation ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = citation.number.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = citation.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = citation.domain,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (citation.snippet.isNotBlank()) {
                        Text(
                            text = citation.snippet,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolResultBlockView(block: Block.ToolResultBlock) {
    var expanded by remember(block) { mutableStateOf(false) }
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
                        block.entry.status in setOf("completed", "success", "done") -> Icons.Outlined.CheckCircle
                        block.entry.status in setOf("failed", "error") -> Icons.Outlined.Cancel
                        block.entry.kind in setOf("search", "research") -> Icons.Outlined.ManageSearch
                        else -> Icons.Outlined.Search
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
