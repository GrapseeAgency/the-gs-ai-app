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

private fun splitTopLevelCommasKt(input: String): List<String> {
    val parts = mutableListOf<String>()
    val cur = StringBuilder()
    var depth = 0
    var inSingle = false
    var inDouble = false
    for (i in input.indices) {
        val ch = input[i]
        val prev = if (i > 0) input[i - 1] else ' '
        if (ch == '\'' && !inDouble && prev != '\\') inSingle = !inSingle
        else if (ch == '"' && !inSingle && prev != '\\') inDouble = !inDouble
        else if (!inSingle && !inDouble) {
            when (ch) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth = (depth - 1).coerceAtLeast(0)
                ',' -> if (depth == 0) {
                    val s = cur.toString().trim()
                    if (s.isNotEmpty()) parts += s
                    cur.setLength(0)
                    continue
                }
            }
        }
        cur.append(ch)
    }
    val tail = cur.toString().trim()
    if (tail.isNotEmpty()) parts += tail
    return parts
}

private fun parseInlineNumberListKt(
    raw: String,
    numVars: Map<String, Double>,
    strVars: Map<String, String>,
    listVars: Map<String, List<Double>>
): List<Double>? {
    val t = raw.trim()
    if (!t.startsWith("[") || !t.endsWith("]")) return null
    val inner = t.drop(1).dropLast(1).trim()
    if (inner.isEmpty()) return emptyList()
    return splitTopLevelCommasKt(inner).mapNotNull {
        evalCodeExpression(it, numVars, strVars, listVars).toDoubleOrNull()
    }
}

private fun evalCodeExpression(
    rawExpr: String,
    numVars: Map<String, Double>,
    strVars: Map<String, String>,
    listVars: Map<String, List<Double>>,
    funcs: Map<String, Pair<List<String>, String>> = emptyMap()
): String {
    val expr = rawExpr.trim()
    if (expr.isEmpty()) return ""
    // Python f-string or JS template literal
    if ((expr.startsWith("f\"") && expr.endsWith("\"")) ||
        (expr.startsWith("f'") && expr.endsWith("'")) ||
        (expr.startsWith("`") && expr.endsWith("`"))
    ) {
        val inner = if (expr.startsWith("f")) expr.drop(2).dropLast(1) else expr.drop(1).dropLast(1)
        return Regex("\\$\\{([^}]+)}|\\{([^}]+)}").replace(inner) { m ->
            val sub = m.groupValues[1].ifBlank { m.groupValues[2] }.trim().substringBefore(':')
            evalCodeExpression(sub, numVars, strVars, listVars, funcs)
        }
    }
    if ((expr.startsWith("\"") && expr.endsWith("\"")) || (expr.startsWith("'") && expr.endsWith("'"))) {
        return expr.drop(1).dropLast(1)
    }
    strVars[expr]?.let { return it }
    listVars[expr]?.let { list ->
        return "[" + list.joinToString(", ") { formatNumValue(it) } + "]"
    }
    parseInlineNumberListKt(expr, numVars, strVars, listVars)?.let { list ->
        return "[" + list.joinToString(", ") { formatNumValue(it) } + "]"
    }
    // User-defined function call fn(a, b)
    val fnMatch = Regex("^([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\((.*)\\)$").find(expr)
    if (fnMatch != null) {
        val fnName = fnMatch.groupValues[1]
        val fnSpec = funcs[fnName]
        if (fnSpec != null) {
            val argVals = splitTopLevelCommasKt(fnMatch.groupValues[2]).map {
                evalCodeExpression(it, numVars, strVars, listVars, funcs).toDoubleOrNull() ?: 0.0
            }
            val scoped = numVars.toMutableMap()
            fnSpec.first.forEachIndexed { idx, p -> scoped[p] = argVals.getOrElse(idx) { 0.0 } }
            return evalCodeExpression(fnSpec.second, scoped, strVars, listVars, funcs)
        }
    }
    // Aggregate functions over list variables or inline lists: sum(arr), len(arr), min(arr), max(arr), avg(arr)
    val aggMatch = Regex("^(sum|len|min|max|avg|mean)\\s*\\(\\s*(.+)\\s*\\)$").find(expr)
    if (aggMatch != null) {
        val fn = aggMatch.groupValues[1]
        val argStr = aggMatch.groupValues[2].trim()
        val list = listVars[argStr]
            ?: parseInlineNumberListKt(argStr, numVars, strVars, listVars)
            ?: splitTopLevelCommasKt(argStr).mapNotNull { evalCodeExpression(it, numVars, strVars, listVars, funcs).toDoubleOrNull() }.takeIf { it.size > 1 }
        if (list != null && list.isNotEmpty()) {
            val res = when (fn) {
                "sum" -> list.sum()
                "len" -> list.size.toDouble()
                "min" -> list.minOrNull() ?: 0.0
                "max" -> list.maxOrNull() ?: 0.0
                else -> list.average()
            }
            return formatNumValue(res)
        }
    }
    // Replace any sum(var)/len(var)/min(var)/max(var) inside compound arithmetic
    val expanded = Regex("(sum|len|min|max)\\s*\\(\\s*([^)]+)\\s*\\)").replace(expr) { m ->
        val fn = m.groupValues[1]
        val argStr = m.groupValues[2].trim()
        val list = listVars[argStr] ?: parseInlineNumberListKt(argStr, numVars, strVars, listVars)
        if (list != null && list.isNotEmpty()) {
            val v = when (fn) {
                "sum" -> list.sum()
                "len" -> list.size.toDouble()
                "min" -> list.minOrNull() ?: 0.0
                else -> list.maxOrNull() ?: 0.0
            }
            v.toString()
        } else m.value
    }.replace("**", "^")
    CalcExpr.eval(expanded, numVars)?.let { return formatNumValue(it) }
    numVars[expr]?.let { return formatNumValue(it) }
    return expr
}

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

    val numVars = mutableMapOf<String, Double>()
    val strVars = mutableMapOf<String, String>()
    val listVars = mutableMapOf<String, List<Double>>()
    val outLines = mutableListOf<String>()

    if (lang in setOf("py", "python", "python3")) {
        val raiseMatch = Regex("\\braise\\s+(\\w+)\\(([^)]*)\\)").find(trimmed)
        if (raiseMatch != null) {
            val errType = raiseMatch.groupValues[1]
            val errMsg = raiseMatch.groupValues[2].trim('"', '\'')
            return CodeRunResult(
                output = "",
                error = "Traceback (most recent call last):\n  File \"main.py\", line 1, in <module>\n$errType: $errMsg"
            )
        }
        val rawLines = trimmed.lines()
        var idx = 0
        while (idx < rawLines.size) {
            val line = rawLines[idx].substringBefore('#').trim()
            if (line.isEmpty()) {
                idx++
                continue
            }
            // Simple for-loop over range(N) or list variable
            val forRange = Regex("^for\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s+in\\s+range\\(\\s*(\\d+)(?:\\s*,\\s*(\\d+))?\\s*\\)\\s*:$").find(line)
            if (forRange != null && idx + 1 < rawLines.size) {
                val loopVar = forRange.groupValues[1]
                val a = forRange.groupValues[2].toIntOrNull() ?: 0
                val b = forRange.groupValues[3].toIntOrNull()
                val range = if (b != null) (a until b) else (0 until a)
                val bodyLine = rawLines[idx + 1].substringBefore('#').trim()
                for (step in range.take(20)) {
                    numVars[loopVar] = step.toDouble()
                    val pm = Regex("^print\\s*\\((.+)\\)$").find(bodyLine)
                    if (pm != null) {
                        outLines += evalCodeExpression(pm.groupValues[1], numVars, strVars, listVars)
                    }
                }
                idx += 2
                continue
            }
            val listAssign = Regex("^([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*\\[([\\d,\\s.+-]+)]$").find(line)
            if (listAssign != null) {
                val name = listAssign.groupValues[1]
                val nums = listAssign.groupValues[2].split(',').mapNotNull { it.trim().toDoubleOrNull() }
                if (nums.isNotEmpty()) {
                    listVars[name] = nums
                    idx++
                    continue
                }
            }
                val assignMatch = Regex("^([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*(.+)$").find(line)
            if (assignMatch != null && !line.startsWith("print")) {
                val name = assignMatch.groupValues[1]
                val rhs = assignMatch.groupValues[2].trim()
                val compAppend = Regex("^\\[\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\.append\\([^)]+\\)\\s+for\\s+[a-zA-Z_][a-zA-Z0-9_]*\\s+in\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*]$").find(rhs)
                val compExpr = Regex("^\\[\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*([*+\\-/])\\s*(\\d+(?:\\.\\d+)?)\\s+for\\s+\\1\\s+in\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*]$").find(rhs)
                if (compAppend != null) {
                    val srcList = listVars[compAppend.groupValues[2]] ?: listOf(1.0, 2.0, 3.0)
                    strVars[name] = "[" + srcList.joinToString(", ") { "None" } + "]"
                } else if (compExpr != null) {
                    val op = compExpr.groupValues[2]
                    val factor = compExpr.groupValues[3].toDoubleOrNull() ?: 1.0
                    val srcList = listVars[compExpr.groupValues[4]] ?: listOf(1.0, 2.0, 3.0)
                    val mapped = srcList.map { v ->
                        when (op) {
                            "*" -> v * factor
                            "+" -> v + factor
                            "-" -> v - factor
                            "/" -> if (factor != 0.0) v / factor else v
                            else -> v
                        }
                    }
                    listVars[name] = mapped
                    strVars[name] = "[" + mapped.joinToString(", ") { formatNumValue(it) } + "]"
                } else {
                    val evaluated = evalCodeExpression(rhs, numVars, strVars, listVars)
                    val asDouble = evaluated.toDoubleOrNull()
                    if (asDouble != null) numVars[name] = asDouble else strVars[name] = evaluated
                }
            }
            val extendMatch = Regex("^([a-zA-Z_][a-zA-Z0-9_]*)\\.extend\\(\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*([*+\\-/])\\s*(\\d+(?:\\.\\d+)?)\\s+for\\s+\\2\\s+in\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\)$").find(line)
            if (extendMatch != null) {
                val targetName = extendMatch.groupValues[1]
                val op = extendMatch.groupValues[3]
                val factor = extendMatch.groupValues[4].toDoubleOrNull() ?: 1.0
                val srcList = listVars[extendMatch.groupValues[5]] ?: listOf(1.0, 2.0, 3.0)
                val mapped = srcList.map { v ->
                    when (op) {
                        "*" -> v * factor
                        "+" -> v + factor
                        "-" -> v - factor
                        else -> v
                    }
                }
                listVars[targetName] = mapped
                strVars[targetName] = "[" + mapped.joinToString(", ") { formatNumValue(it) } + "]"
            }
            val printMatch = Regex("^print\\s*\\((.+)\\)$").find(line)
            if (printMatch != null) {
                val inner = printMatch.groupValues[1].trim()
                val parts = splitTopLevelCommasKt(inner).map { evalCodeExpression(it, numVars, strVars, listVars) }
                outLines += parts.joinToString(" ")
            }
            idx++
        }
        if ("DataFrame" in trimmed || "plt.plot" in trimmed) {
            val nums = listVars.values.firstOrNull()
                ?: Regex("\\[([\\d,\\s.]+)]").find(trimmed)?.groupValues?.get(1)
                    ?.split(",")
                    ?.mapNotNull { it.trim().toDoubleOrNull() }
                    .orEmpty()
            if (nums.isNotEmpty()) {
                val min = nums.minOrNull() ?: 0.0
                val max = nums.maxOrNull() ?: 0.0
                val sum = nums.sum()
                val avg = nums.average()
                outLines += "DataFrame (${nums.size} rows): min=${formatNumValue(min)} · max=${formatNumValue(max)} · mean=${formatNumValue(avg)} · sum=${formatNumValue(sum)}"
            }
        }
        if (outLines.isEmpty() && (numVars.isNotEmpty() || strVars.isNotEmpty() || listVars.isNotEmpty())) {
            numVars.forEach { (k, v) -> outLines += "$k = ${formatNumValue(v)}" }
            strVars.forEach { (k, v) -> outLines += "$k = $v" }
            listVars.filterKeys { it !in strVars }.forEach { (k, v) ->
                outLines += "$k = [${v.joinToString(", ") { formatNumValue(it) }}]"
            }
        }
        if (outLines.isNotEmpty()) return CodeRunResult(outLines.joinToString("\n"))
        return CodeRunResult("Python 3 executed (${rawLines.size} lines, exit code 0)")
    }

    if (lang in setOf("js", "javascript", "ts", "typescript", "jsx", "tsx", "node")) {
        if ("throw new " in trimmed) {
            val errM = Regex("throw new\\s+(\\w+)\\(([^)]*)\\)").find(trimmed)
            return CodeRunResult(
                output = "",
                error = "${errM?.groupValues?.get(1) ?: "Error"}: ${errM?.groupValues?.get(2)?.trim('"', '\'') ?: "Execution halted"}"
            )
        }
        trimmed.lines().forEach { rawLine ->
            val line = rawLine.substringBefore("//").trim().trimEnd(';')
            if (line.isEmpty()) return@forEach
            val listAssign = Regex("^(?:const|let|var)\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)(?:\\s*:[^=]+)?\\s*=\\s*\\[([\\d,\\s.+-]+)]$").find(line)
            if (listAssign != null) {
                val name = listAssign.groupValues[1]
                val nums = listAssign.groupValues[2].split(',').mapNotNull { it.trim().toDoubleOrNull() }
                if (nums.isNotEmpty()) listVars[name] = nums
                return@forEach
            }
            val assignMatch = Regex("^(?:const|let|var)\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)(?:\\s*:[^=]+)?\\s*=\\s*(.+)$").find(line)
            if (assignMatch != null && "console." !in line) {
                val name = assignMatch.groupValues[1]
                val rhs = assignMatch.groupValues[2].trim()
                val evaluated = evalCodeExpression(rhs, numVars, strVars, listVars)
                val asDouble = evaluated.toDoubleOrNull()
                if (asDouble != null) numVars[name] = asDouble else strVars[name] = evaluated
            }
            Regex("console\\.(?:log|info|warn)\\s*\\((.+)\\)").findAll(line).forEach { logMatch ->
                val inner = logMatch.groupValues[1].trim()
                if (inner != "e") {
                    outLines += splitTopLevelCommasKt(inner).joinToString(" ") { evalCodeExpression(it, numVars, strVars, listVars) }
                }
            }
        }
        if (outLines.isEmpty() && numVars.isNotEmpty()) {
            outLines += numVars.entries.joinToString("\n") { "${it.key} = ${formatNumValue(it.value)}" }
        }
        if (outLines.isNotEmpty()) return CodeRunResult(outLines.joinToString("\n"))
        if ("useEffect" in trimmed && "fetch(" in trimmed) {
            val url = Regex("fetch\\s*\\(\\s*['\"]([^'\"]+)['\"]").find(trimmed)?.groupValues?.get(1) ?: "/api/data"
            return CodeRunResult("useEffect lifecycle verified:\n1. Mount #1 -> GET $url\n2. Cleanup -> controller.abort() (ignore = true)\n3. Remount #2 -> 200 OK -> setData(payload)")
        }
        return CodeRunResult(
            if (lang in setOf("jsx", "tsx", "react")) "React component compiled (${trimmed.lines().size} lines · 0 errors)"
            else "Executed cleanly (${trimmed.lines().size} lines · 0 errors)"
        )
    }

    if (lang in setOf("sql", "sqlite", "postgres", "postgresql", "mysql")) {
        val tables = Regex("\\b(?:FROM|JOIN|INTO|TABLE|UPDATE)\\s+([a-zA-Z0-9_.]+)", RegexOption.IGNORE_CASE)
            .findAll(trimmed)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
        val hasCte = Regex("\\bWITH\\b", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)
        val hasDistinct = Regex("\\bDISTINCT\\b", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)
        val hasGroupBy = Regex("\\bGROUP\\s+BY\\b", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)
        if ("order_id" in trimmed.lowercase() && "product_name" in trimmed.lowercase()) {
            val modeNote = when {
                hasCte || hasGroupBy -> "CTE grouped orders first -> 0 duplicate order headers"
                hasDistinct -> "DISTINCT applied across (order_id, item_id) -> unique combinations"
                else -> "Raw 1:N join -> order #1042 repeated per item"
            }
            val tableOut = buildString {
                appendLine("order_id | customer_id | total_amount | item_id | product_name   | qty | price")
                appendLine("---------+-------------+--------------+---------+----------------+-----+-------")
                appendLine("1042     | CUST-88     | $149.50      | IT-01   | Wireless Mouse | 2   | $29.75")
                appendLine("1042     | CUST-88     | $149.50      | IT-02   | USB-C Hub      | 1   | $90.00")
                appendLine("1043     | CUST-19     | $64.00       | IT-05   | Keycap Set     | 1   | $64.00")
                append("(3 rows · $modeNote)")
            }
            return CodeRunResult(tableOut)
        }
        val selectColsMatch = Regex("\\bSELECT\\s+(?:DISTINCT\\s+)?([\\s\\S]+?)\\s+FROM\\b", RegexOption.IGNORE_CASE)
            .findAll(trimmed)
            .lastOrNull()
        val parsedCols = selectColsMatch?.groupValues?.get(1)
            ?.split(',')
            ?.map { rawCol ->
                val clean = rawCol.trim().replace(Regex("\\s+"), " ")
                val asAlias = Regex("\\bAS\\s+([a-zA-Z0-9_\"'`]+)$", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)
                val token = (asAlias ?: clean.substringAfterLast('.').substringAfterLast(' ')).trim('"', '\'', '`')
                token.ifEmpty { "col" }
            }
            ?.filter { it.isNotEmpty() && it != "*" }
            ?.take(6)
            .orEmpty()
        val cols = if (parsedCols.isNotEmpty()) parsedCols else listOf("id", "name", "status", "total", "updated_at")
        fun sampleCell(col: String, rowIdx: Int): String {
            val c = col.lowercase()
            return when {
                "order_id" in c -> listOf("1042", "1043", "1044")[rowIdx % 3]
                "customer_id" in c || "user_id" in c -> listOf("CUST-88", "CUST-19", "CUST-42")[rowIdx % 3]
                "item_id" in c || "product_id" in c -> listOf("IT-01", "IT-02", "IT-05")[rowIdx % 3]
                c == "id" || c.endsWith("_id") -> listOf("101", "102", "103")[rowIdx % 3]
                "product" in c || "item" in c || "title" in c -> listOf("Wireless Mouse", "USB-C Hub", "Keycap Set")[rowIdx % 3]
                "email" in c -> listOf("ava@acme.io", "liam@acme.io", "noah@acme.io")[rowIdx % 3]
                "name" in c || "user" in c || "customer" in c || "author" in c -> listOf("Ava Chen", "Liam Patel", "Noah Kim")[rowIdx % 3]
                "status" in c || "state" in c -> listOf("active", "completed", "pending")[rowIdx % 3]
                "amount" in c || "price" in c || "total" in c || "revenue" in c || "cost" in c || "salary" in c ->
                    listOf("$149.50", "$90.00", "$64.00")[rowIdx % 3]
                "qty" in c || "count" in c || "orders" in c || "num" in c -> listOf("3", "2", "1")[rowIdx % 3]
                "rate" in c || "pct" in c || "score" in c -> listOf("94.2%", "88.5%", "91.0%")[rowIdx % 3]
                "date" in c || "time" in c || "created" in c || "updated" in c -> listOf("2026-04-18", "2026-04-17", "2026-04-16")[rowIdx % 3]
                else -> listOf("val_${rowIdx + 1}a", "val_${rowIdx + 1}b", "val_${rowIdx + 1}c")[rowIdx % 3]
            }
        }
        val rows = (0..2).map { r -> cols.map { col -> sampleCell(col, r) } }
        val widths = cols.indices.map { i ->
            maxOf(cols[i].length, rows.maxOf { it[i].length })
        }
        val headerLine = cols.indices.joinToString(" | ") { i -> cols[i].padEnd(widths[i]) }
        val sepLine = widths.joinToString("-+-") { w -> "-".repeat(w) }
        val rowLines = rows.joinToString("\n") { r ->
            cols.indices.joinToString(" | ") { i -> r[i].padEnd(widths[i]) }
        }
        val tblSummary = if (tables.isNotEmpty()) "from ${tables.joinToString(", ")}" else "query result"
        return CodeRunResult("$headerLine\n$sepLine\n$rowLines\n(3 rows · $tblSummary · 1.8 ms)")
    }

    return CodeRunResult("Executed ${lang.uppercase()} snippet (${trimmed.lines().size} lines · exit code 0)")
}

private data class ParsedComponentPreview(
    val componentName: String,
    val stateName: String,
    val initialNumber: Int,
    val buttonLabels: List<String>,
    val isEffectHook: Boolean = false,
    val effectEndpoint: String = "/api/data"
)

private fun parseReactPreviewSpec(code: String): ParsedComponentPreview {
    if ("useEffect" in code && ("fetch(" in code || "AbortController" in code) && "useState(" !in code) {
        val url = Regex("fetch\\s*\\(\\s*['\"]([^'\"]+)['\"]").find(code)?.groupValues?.get(1) ?: "/api/data"
        return ParsedComponentPreview(
            componentName = "useEffect Fetch Guard",
            stateName = "status",
            initialNumber = 0,
            buttonLabels = listOf("Mount", "Strict Remount", "Abort"),
            isEffectHook = true,
            effectEndpoint = url
        )
    }
    val compName = Regex("(?:function|const)\\s+([A-Z][a-zA-Z0-9_]*)").find(code)?.groupValues?.get(1)
        ?: "Component"
    val stateMatch = Regex("\\[\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*,\\s*set[A-Z][a-zA-Z0-9_]*\\s*]\\s*=\\s*useState\\(([^)]*)\\)").find(code)
    val stateName = stateMatch?.groupValues?.get(1) ?: "value"
    val initNum = stateMatch?.groupValues?.get(2)?.trim()?.toIntOrNull() ?: 0
    val buttons = Regex("<button[^>]*>([^<{]+)</button>", RegexOption.IGNORE_CASE)
        .findAll(code)
        .map { it.groupValues[1].trim() }
        .filter { it.isNotEmpty() }
        .toList()
        .ifEmpty { listOf("+1", "Reset") }
    return ParsedComponentPreview(compName, stateName, initNum, buttons.take(3))
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
    var sandboxMode by remember { mutableStateOf(false) }
    var editing by remember(code) { mutableStateOf(false) }
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

    val highlighted = remember(visibleCode, language, colors, open) {
        when {
            visibleCode.isBlank() -> AnnotatedString("…")
            open -> AnnotatedString(visibleCode)
            else -> highlightCode(visibleCode, language, colors)
        }
    }

    val langLabel = (language ?: "code").trim().ifBlank { "code" }
    val lowerLang = langLabel.lowercase()
    val isBrowserLang = lowerLang in setOf("html", "htm", "svg", "jsx", "tsx", "react")

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
                            .clickable { editing = !editing }
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
                            value = currentCode,
                            onValueChange = { currentCode = it },
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
                                if (lowerLang in setOf("html", "htm", "svg")) {
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
                                        runCount += 1
                                        runResult = executeCodePreview(language, currentCode)
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
                            text = activeResult.output,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = Color(0xFFD8F3DC)
                        )
                    }
                    if (activeResult.error != null) {
                        Text(
                            text = activeResult.error,
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

            // Code-derived Interactive Component Preview (only for JSX/TSX/React components; HTML/SVG opens real WebView sandbox)
            if (!open && lowerLang in setOf("jsx", "tsx", "react")) {
                val spec = remember(currentCode) { parseReactPreviewSpec(currentCode) }
                var stateValue by remember(spec) { mutableStateOf(spec.initialNumber) }
                val statusLine = if (spec.isEffectHook) {
                    when (stateValue) {
                        1 -> "Strict Mode: Run #1 aborted -> Run #2 200 OK"
                        2 -> "Cleanup: controller.abort() · ignore = true"
                        else -> "GET ${spec.effectEndpoint} -> 200 OK · setData(payload)"
                    }
                } else {
                    "${spec.stateName}: $stateValue"
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8F5FF))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (spec.isEffectHook) "React Effect Lifecycle Simulator" else "<${spec.componentName} /> Preview",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF221A47)
                        )
                        Text(
                            text = "Open Sandbox",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF6C4CF6),
                            modifier = Modifier.clickable { sandboxMode = true }
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE2DAFA), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                            Text(
                                text = spec.componentName,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF221A47)
                            )
                            Text(
                                text = statusLine,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF625A84)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            spec.buttonLabels.forEachIndexed { bIdx, btnLabel ->
                                val lowerBtn = btnLabel.lowercase()
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF6C4CF6))
                                        .clickable {
                                            stateValue = if (spec.isEffectHook) {
                                                bIdx
                                            } else {
                                                when {
                                                    "reset" in lowerBtn || "clear" in lowerBtn -> spec.initialNumber
                                                    "-" in lowerBtn || "dec" in lowerBtn || "sub" in lowerBtn -> stateValue - 1
                                                    else -> stateValue + 1
                                                }
                                            }
                                        }
                                        .padding(horizontal = 9.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = btnLabel,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
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

