package com.grapsee.gsai.ui.chat.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.theme.GsTheme
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import java.util.Locale

/**
 * Calculator widget — a real, stateful calculator.
 *
 * The model supplies *numbers and formulas*, never markup: fields become
 * sliders/segmented controls and each output is an expression evaluated on
 * every change. One kind covers unit/currency conversion, loans, mortgages,
 * tax, pricing, budgets, investments, probability, statistics, scientific and
 * date maths.
 */

@Serializable
data class CalcChoice(val label: String = "", val value: Double = 0.0)

@Serializable
data class CalcField(
    val key: String = "",
    val label: String = "",
    val value: Double = 0.0,
    val unit: String = "",
    val min: Double = 0.0,
    val max: Double = 100.0,
    val step: Double = 1.0,
    val choices: List<CalcChoice> = emptyList()
)

@Serializable
data class CalcOutput(
    val label: String = "",
    val expr: String = "",
    val unit: String = "",
    val format: String = "number",
    val primary: Boolean = false
)

@Serializable
data class CalcPreset(val label: String = "", val values: Map<String, Double> = emptyMap())

@Serializable
data class CalcWidget(
    val type: String = "loan",
    val title: String = "",
    val fields: List<CalcField> = emptyList(),
    val outputs: List<CalcOutput> = emptyList(),
    val note: String = "",
    val presets: List<CalcPreset> = emptyList()
)

internal val CALC_TYPES = setOf(
    "unit", "currency", "finance", "loan", "mortgage", "pricing", "tax",
    "budget", "investment", "probability", "statistics", "scientific", "datetime"
)

/** Recursive-descent expression evaluator: numbers, variables, + - * / % ^, functions. */
internal object CalcExpr {

    fun eval(expr: String, vars: Map<String, Double>): Double? {
        val parser = Parser(expr, vars)
        return try {
            val v = parser.expression()
            if (!v.isFinite()) null else v
        } catch (_: Throwable) {
            null
        }
    }

    private class Parser(private val src: String, private val vars: Map<String, Double>) {
        private var i = 0

        fun expression(): Double {
            var v = term()
            while (true) {
                skip()
                when {
                    take('+') -> v += term()
                    take('-') -> v -= term()
                    else -> return v
                }
            }
        }

        private fun term(): Double {
            var v = unary()
            while (true) {
                skip()
                when {
                    take('*') -> v *= unary()
                    take('/') -> {
                        val d = unary()
                        v = if (abs(d) < 1e-12) Double.NaN else v / d
                    }
                    take('%') -> {
                        val d = unary()
                        v = if (abs(d) < 1e-12) Double.NaN else v % d
                    }
                    else -> return v
                }
            }
        }

        private fun unary(): Double {
            skip()
            if (take('-')) return -unary()
            if (take('+')) return unary()
            return power()
        }

        private fun power(): Double {
            val base = primary()
            skip()
            if (take('^')) return base.pow(unary())
            return base
        }

        private fun primary(): Double {
            skip()
            if (take('(')) {
                val v = expression()
                expect(')')
                return v
            }
            if (i < src.length && (src[i].isDigit() || src[i] == '.')) return number()
            val name = ident()
            if (name.isEmpty()) return Double.NaN
            skip()
            if (take('(')) {
                val args = ArrayList<Double>()
                skip()
                if (!take(')')) {
                    do {
                        args += expression()
                        skip()
                    } while (take(','))
                    expect(')')
                }
                return call(name.lowercase(), args)
            }
            return when (name.lowercase()) {
                "pi" -> Math.PI
                "e" -> Math.E
                else -> vars[name] ?: vars[name.lowercase()] ?: Double.NaN
            }
        }

        private fun call(fn: String, a: List<Double>): Double = when (fn) {
            "sqrt" -> if (a.isEmpty()) Double.NaN else sqrt(max(0.0, a[0]))
            "abs" -> if (a.isEmpty()) Double.NaN else abs(a[0])
            "ln" -> if (a.isEmpty() || a[0] <= 0) Double.NaN else ln(a[0])
            "log", "log10" -> if (a.isEmpty() || a[0] <= 0) Double.NaN else log10(a[0])
            "exp" -> if (a.isEmpty()) Double.NaN else exp(a[0])
            "sin" -> if (a.isEmpty()) Double.NaN else sin(a[0])
            "cos" -> if (a.isEmpty()) Double.NaN else cos(a[0])
            "tan" -> if (a.isEmpty()) Double.NaN else tan(a[0])
            "floor" -> if (a.isEmpty()) Double.NaN else floor(a[0])
            "ceil" -> if (a.isEmpty()) Double.NaN else ceil(a[0])
            "round" -> if (a.isEmpty()) Double.NaN else round(a[0])
            "min" -> a.minOrNull() ?: Double.NaN
            "max" -> a.maxOrNull() ?: Double.NaN
            "pow" -> if (a.size < 2) Double.NaN else a[0].pow(a[1])
            "clamp" -> if (a.size < 3) Double.NaN else a[0].coerceIn(min(a[1], a[2]), max(a[1], a[2]))
            else -> Double.NaN
        }

        private fun number(): Double {
            val start = i
            while (i < src.length && (src[i].isDigit() || src[i] == '.')) i++
            return src.substring(start, i).toDoubleOrNull() ?: Double.NaN
        }

        private fun ident(): String {
            val start = i
            while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_')) i++
            return src.substring(start, i)
        }

        private fun skip() {
            while (i < src.length && src[i].isWhitespace()) i++
        }

        private fun take(ch: Char): Boolean {
            skip()
            if (i < src.length && src[i] == ch) {
                i++
                return true
            }
            return false
        }

        private fun expect(ch: Char) {
            if (!take(ch)) throw IllegalStateException("expected $ch")
        }
    }
}

internal fun formatCalc(value: Double, format: String, unit: String): String {
    if (value.isNaN() || !value.isFinite()) return "—"
    val body = when (format) {
        "money" -> String.format(Locale.US, "%,.2f", value)
        "percent" -> String.format(Locale.US, "%.2f%%", value * 100)
        "integer" -> String.format(Locale.US, "%,.0f", value)
        "compact" -> when {
            abs(value) >= 1_000_000_000 -> String.format(Locale.US, "%.2fB", value / 1_000_000_000)
            abs(value) >= 1_000_000 -> String.format(Locale.US, "%.2fM", value / 1_000_000)
            abs(value) >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000)
            else -> String.format(Locale.US, "%.2f", value)
        }
        else -> if (abs(value - round(value)) < 1e-9) String.format(Locale.US, "%,.0f", value)
        else String.format(Locale.US, "%,.2f", value)
    }
    return if (unit.isBlank()) body else "$body $unit"
}

@Composable
internal fun CalcWidgetView(w: CalcWidget, onSendPrompt: ((String) -> Unit)? = null) {
    val c = GsTheme.colors
    val fields = w.fields.take(10).filter { it.key.isNotBlank() }
    val values = remember { mutableStateMapOf<String, Double>().apply { fields.forEach { put(it.key, it.value) } } }

    WidgetCardShell(w.title.ifEmpty { "Calculator" }, w.type) {
        Column(Modifier.padding(top = 6.dp)) {
            if (w.presets.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    w.presets.take(4).forEach { preset ->
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(c.surface)
                                .clickable {
                                    preset.values.forEach { (k, v) -> if (k in values) values[k] = v }
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(preset.label, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                        }
                    }
                }
            }

            fields.forEach { f ->
                val current = values[f.key] ?: f.value
                if (f.choices.isNotEmpty()) {
                    Text(
                        f.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(c.surface).padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        f.choices.take(5).forEach { choice ->
                            val selected = abs(current - choice.value) < 1e-9
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) c.accent else Color.Transparent)
                                    .clickable { values[f.key] = choice.value }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    choice.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) c.onAccent else c.textSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(f.label, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                        Text(
                            formatCalc(current, if (f.step >= 1) "integer" else "number", f.unit),
                            style = MaterialTheme.typography.labelMedium,
                            color = c.textPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Slider(
                        value = current.coerceIn(f.min, max(f.min + 0.0001, f.max)).toFloat(),
                        onValueChange = { values[f.key] = it.toDouble() },
                        valueRange = f.min.toFloat()..max(f.min + 0.0001, f.max).toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            val outputs = w.outputs.take(8).map { out ->
                out to CalcExpr.eval(out.expr, values)
            }
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp))
                    .background(c.surface).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                outputs.forEach { (out, value) ->
                    val shown = formatCalc(value ?: Double.NaN, out.format, out.unit)
                    if (out.primary) {
                        Text(out.label, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                        Text(
                            shown,
                            style = MaterialTheme.typography.headlineSmall,
                            color = c.accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(out.label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                            Text(
                                shown,
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            if (w.note.isNotBlank()) {
                Text(
                    w.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (onSendPrompt != null) {
                Text(
                    "Ask about this",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.accent,
                    modifier = Modifier.padding(top = 8.dp).clickable {
                        val summary = values.entries.joinToString(", ") { "${it.key}=${formatCalc(it.value, "number", "")}" }
                        onSendPrompt("${w.title.ifEmpty { w.type }}: $summary")
                    }
                )
            }
        }
    }
}
