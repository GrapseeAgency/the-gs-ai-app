package com.grapsee.gsai.ui.chat.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.ui.theme.GsTheme
import kotlinx.coroutines.delay
import com.grapsee.gsai.ui.icons.GsIcons

private data class CodeRunResult(
    val output: String,
    val error: String? = null
)

private fun formatNumValue(d: Double): String =
    if (d % 1.0 == 0.0) d.toLong().toString()
    else String.format(java.util.Locale.US, "%.4f", d).trimEnd('0').trimEnd('.')

private fun executeCodePreview(language: String?, code: String): CodeRunResult {
    val lang = (language ?: "code").lowercase().trim()
    val trimmed = code.trim()
    if (trimmed.isEmpty()) return CodeRunResult("(empty snippet)")

    if (lang == "json") {
        return try {
            val tok = org.json.JSONTokener(trimmed).nextValue()
            val formatted = when (tok) {
                is org.json.JSONObject -> tok.toString(2)
                is org.json.JSONArray -> tok.toString(2)
                else -> tok.toString()
            }
            CodeRunResult(formatted)
        } catch (t: Throwable) {
            CodeRunResult(output = "", error = "JSONSyntaxError: ${t.message ?: "Invalid JSON"}")
        }
    }

    if (lang in setOf("py", "python", "python3")) {
        // This is an arithmetic preview, NOT a Python runtime. Never skip a
        // statement or supply missing variables and then report script success.
        val vars = mutableMapOf<String, Double>()
        val output = mutableListOf<String>()
        fun numeric(expr: String): Double {
            require(expr.matches(Regex("""[a-zA-Z0-9_+*()./\s-]+""")) && "**" !in expr && "//" !in expr) {
                "Only numbers, variables, +, -, *, / and parentheses are supported"
            }
            Regex("""[a-zA-Z_][a-zA-Z0-9_]*""").findAll(expr).forEach {
                require(vars.containsKey(it.value)) { "Unknown variable: ${it.value}" }
            }
            require(!Regex("""[a-zA-Z_]\w*\s*\(""").containsMatchIn(expr)) { "Function calls are not supported" }
            return (CalcExpr.eval(expr, vars) ?: error("Invalid expression")).also {
                require(it.isFinite()) { "Result is not finite (check division by zero)" }
            }
        }
        var lineNumber = 0
        return try {
            require(trimmed.length <= 32000 && trimmed.lines().size <= 200) { "Preview limit exceeded" }
            for (raw in trimmed.lines()) {
                lineNumber++
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val assign = Regex("""^([a-zA-Z_]\w*)\s*=\s*([^=]+)$""").matchEntire(line)
                if (assign != null) { vars[assign.groupValues[1]] = numeric(assign.groupValues[2]); continue }
                val print = Regex("""^print\((.*)\)$""").matchEntire(line)
                if (print != null) {
                    val arg = print.groupValues[1].trim()
                    output += when {
                        arg.isEmpty() -> ""
                        arg.matches(Regex(""""[^"\\]*"|'[^'\\]*'""")) -> arg.drop(1).dropLast(1)
                        else -> formatNumValue(numeric(arg))
                    }
                    continue
                }
                error("Unsupported statement; imports, loops and libraries require a Python runtime")
            }
            CodeRunResult("Arithmetic preview (not a Python runtime)\n" + if (output.isEmpty()) "(no print output)" else output.joinToString("\n"))
        } catch (t: IllegalArgumentException) {
            CodeRunResult("", "Preview stopped at line $lineNumber: ${t.message}")
        } catch (t: IllegalStateException) {
            CodeRunResult("", "Preview stopped at line $lineNumber: ${t.message}")
        }
    }
    if (lang in setOf("sql", "sqlite", "postgres", "postgresql", "mysql")) {
        return CodeRunResult("", "Not executed: no database is connected. Copy this SQL into your database client.")
    }
    return CodeRunResult("", "Not executed: no ${lang.uppercase()} runtime is available here.")
}

/**
 * Fenced code block — upgraded to match Screenshots 1 & 2 (04. Code Blocks &
 * 05. Code Execution): deep indigo code card (#221F3B), language badge,
 * line-number gutter (1..N), expand/collapse & edit controls, bottom execution
 * bar ([▷ Run in Browser] / [▷ Run Code]), and terminal-style output drawer
 * with ● Success / ● Error status and ↻ Rerun control.
 */
@Composable
fun CodeBlockCard(
    language: String?,
    code: String,
    open: Boolean,
    onCopyCode: (String) -> Unit
) {
    var copied by remember { mutableStateOf(false) }
    var sandboxMode by remember(code, language) { mutableStateOf(false) }
    var editing by remember(code) { mutableStateOf(false) }
    var editorDraft by remember(code) { mutableStateOf("") }
    var editorEncoded by remember(code) { mutableStateOf(false) }
    var editorError by remember(code) { mutableStateOf<String?>(null) }
    var currentCode by remember(code) { mutableStateOf(code) }
    var expanded by remember(code) { mutableStateOf(false) }
    var runResult by remember(code) { mutableStateOf<CodeRunResult?>(null) }
    var runCount by remember(code) { mutableStateOf(0) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    if (sandboxMode && !open && currentCode.isNotBlank()) {
        Column {
            CodeSandboxView(code = currentCode, language = language ?: "javascript")
            Text(
                text = "Close full sandbox",
                style = MaterialTheme.typography.labelSmall,
                color = GsTheme.colors.codeComment,
                modifier = Modifier
                    .padding(top = 4.dp, start = 4.dp)
                    .clickable { sandboxMode = false }
            )
        }
        return
    }
    val colors = GsTheme.colors
    val wrapCode = LocalNexlyCodeWrap.current
    val allLines = remember(currentCode) { currentCode.lines() }
    val isCollapsible = allLines.size > 14
    val visibleCode = remember(currentCode, expanded, isCollapsible) {
        if (isCollapsible && !expanded) allLines.take(12).joinToString("\n") else currentCode
    }
    val visibleLineCount = remember(visibleCode) { visibleCode.lines().size.coerceAtLeast(1) }
    val lineNumbersText = remember(visibleLineCount) {
        (1..visibleLineCount).joinToString("\n")
    }

    val displayCode = remember(visibleCode) { CodeDisplay.escape(visibleCode) }
    val highlighted = remember(displayCode, language, colors, open) {
        when {
            visibleCode.isBlank() -> AnnotatedString("…")
            open -> AnnotatedString(displayCode)
            else -> highlightCode(displayCode, language, colors)
        }
    }

    val langLabel = (language ?: "code").trim().ifBlank { "code" }
    val lowerLang = langLabel.lowercase()
    val isBrowserLang = lowerLang in setOf("html", "htm", "svg", "js", "javascript")

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF221F3B),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF36315C), RoundedCornerShape(14.dp))
            .semantics {
                contentDescription = if (language != null) "Code block, $language" else "Code block"
            }
    ) {
        Column {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B1830))
                    .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NexlyGlyph(
                    name = "code",
                    size = 14.dp,
                    color = Color(0xFF9E86FF),
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = langLabel.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFE6E1FF),
                    modifier = Modifier.weight(1f)
                )
                if (!open && isCollapsible) {
                    Text(
                        text = if (expanded) "Collapse" else "Expand (${allLines.size})",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFFC5BCFF),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                if (!open && currentCode.isNotBlank()) {
                    Text(
                        text = if (editing) "Done" else "Edit",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFFC5BCFF),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable {
                                if (!editing) {
                                    editorEncoded = CodeDisplay.requiresEncoding(currentCode)
                                    editorDraft = if (editorEncoded) CodeDisplay.encodeEditor(currentCode) else currentCode
                                    editorError = null
                                }
                                if (editorError == null) editing = !editing
                            }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = { onCopyCode(currentCode); copied = true }, modifier = Modifier.size(26.dp)) {
                    Icon(
                        if (copied) GsIcons.Check else GsIcons.Copy,
                        contentDescription = if (copied) "Copied" else "Copy code",
                        tint = if (copied) Color(0xFF86EFAC) else Color(0xFFB5AFD9),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Line numbers + syntax-highlighted or editable code surface
            val vScroll = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .verticalScroll(vScroll)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = lineNumbersText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 19.sp
                        ),
                        color = Color(0xFF6E6996),
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .width(24.dp)
                            .padding(end = 8.dp)
                    )
                    if (editing) {
                        BasicTextField(
                            value = editorDraft,
                            onValueChange = { draft ->
                                if (!editorEncoded && CodeDisplay.requiresEncoding(draft)) {
                                    editorEncoded = true
                                    editorDraft = CodeDisplay.encodeEditor(draft)
                                    currentCode = draft
                                    editorError = null
                                } else {
                                    editorDraft = CodeDisplay.escape(draft)
                                    runCatching { if (editorEncoded) CodeDisplay.decodeEditor(draft) else draft }
                                        .onSuccess { currentCode = it; editorError = null }
                                        .onFailure { editorError = "Enter one valid JSON source string. Invalid drafts cannot run." }
                                }
                            },
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 19.sp,
                                color = Color(0xFFEEEBFF)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text(
                            text = highlighted,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 19.sp
                            ),
                            color = Color(0xFFEEEBFF),
                            softWrap = wrapCode,
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (wrapCode) Modifier
                                    else Modifier.horizontalScroll(rememberScrollState())
                                )
                        )
                    }
                }
            }

            if (CodeDisplay.requiresEncoding(currentCode)) {
                Text("Unicode symbols are shown as escapes; Copy and Run keep original source.", color = Color(0xFFC5BCFF), fontSize = 11.sp)
            }
            if (editing && editorEncoded) {
                Text("Unicode-safe editor: edit one JSON-encoded source string.", color = Color(0xFFC5BCFF), fontSize = 11.sp)
            }
            editorError?.let { Text(it, color = Color(0xFFFCA5A5), fontSize = 11.sp) }
            // Bottom Execution Action Bar (04 & 05: Screenshots 1 & 2)
            if (!open && currentCode.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1B1830))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF6C4CF6))
                            .clickable {
                                if (editing && editorError != null) {
                                    runResult = CodeRunResult("", "Not executed: correct the JSON-encoded source draft first.")
                                } else if (lowerLang in setOf("html", "htm", "svg", "js", "javascript")) {
                                    sandboxMode = true
                                } else {
                                    runCount += 1
                                    runResult = executeCodePreview(language, currentCode)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        NexlyGlyph(
                            name = "player-play",
                            size = 12.dp,
                            color = Color.White
                        )
                        Text(
                            text = if (isBrowserLang) "Run in Browser" else "Run Code",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val currentRun = runResult
                        if (currentRun != null) {
                            val hasError = currentRun.error != null
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        if (hasError) Color(0xFFEF4444).copy(alpha = 0.2f)
                                        else Color(0xFF22C55E).copy(alpha = 0.2f)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (hasError) "● Error" else "● Success",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hasError) Color(0xFFFCA5A5) else Color(0xFF86EFAC)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .clickable {
                                        if (editing && editorError != null) {
                                            runResult = CodeRunResult("", "Not executed: correct the JSON-encoded source draft first.")
                                        } else {
                                            runCount += 1
                                            runResult = executeCodePreview(language, currentCode)
                                        }
                                    }
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                NexlyGlyph(
                                    name = "refresh",
                                    size = 11.dp,
                                    color = Color(0xFFC5BCFF)
                                )
                                Text(
                                    text = "Rerun",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFC5BCFF)
                                )
                            }
                        }
                        Text(
                            text = "${allLines.size} lines",
                            fontSize = 11.sp,
                            color = Color(0xFF8E89B4)
                        )
                    }
                }
            }

            // Terminal-style Execution Output Drawer (05. Code Execution)
            val activeResult = runResult
            if (activeResult != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141124))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$ run $lowerLang (run #$runCount)",
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF8E89B4)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.clickable { runResult = null }
                        ) {
                            NexlyGlyph(
                                name = "x",
                                size = 11.dp,
                                color = Color(0xFF8E89B4)
                            )
                            Text(
                                text = "Clear",
                                fontSize = 10.5.sp,
                                color = Color(0xFF8E89B4)
                            )
                        }
                    }
                    if (activeResult.output.isNotBlank()) {
                        Text(
                            text = CodeDisplay.escape(activeResult.output),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = Color(0xFFD8F3DC)
                        )
                    }
                    if (activeResult.error != null) {
                        Text(
                            text = CodeDisplay.escape(activeResult.error),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = Color(0xFFFCA5A5),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFEF4444).copy(alpha = 0.14f))
                                .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        )
                    }
                }
            }

            // Visual Data Plot Preview (Screenshot 1: Revenue Trend mini bar chart from Python/JS code)
            val embeddedSeries = remember(currentCode) { extractCodeSeriesPreview(currentCode) }
            if (embeddedSeries != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF7F4FF))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = embeddedSeries.first,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF221A47)
                    )
                    val maxVal = (embeddedSeries.second.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(74.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        embeddedSeries.second.forEach { (label, value) ->
                            val frac = (value / maxVal).toFloat().coerceIn(0.15f, 1f)
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.68f)
                                        .height((48f * frac).dp)
                                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                        .background(Color(0xFF6C4CF6))
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    color = Color(0xFF5E5680)
                                )
                            }
                        }
                    }
                }
            }

        }
    }
}

private fun extractCodeSeriesPreview(code: String): Pair<String, List<Pair<String, Double>>>? {
    val labelMatch = Regex("['\"](?:Month|Label|Category|Quarter|Day|Year)s?['\"]\\s*:\\s*\\[([^\\]]+)]", RegexOption.IGNORE_CASE)
        .find(code)
    val valueMatch = Regex("['\"](Revenue|Sales|Value|Score|Users|Growth|Amount)s?['\"]\\s*:\\s*\\[([^\\]]+)]", RegexOption.IGNORE_CASE)
        .find(code)
    if (labelMatch == null || valueMatch == null) return null
    val seriesName = valueMatch.groupValues[1].replaceFirstChar { it.uppercase() } + " Trend"
    val labels = labelMatch.groupValues[1].split(',').map {
        it.trim().trim('\'', '"')
    }.filter { it.isNotEmpty() }
    val values = valueMatch.groupValues[2].split(',').mapNotNull {
        it.trim().toDoubleOrNull()
    }
    if (labels.size < 2 || values.size < 2) return null
    val pairs = labels.zip(values)
    return seriesName to pairs
}

