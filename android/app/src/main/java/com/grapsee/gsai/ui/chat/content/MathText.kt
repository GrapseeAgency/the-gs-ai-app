package com.grapsee.gsai.ui.chat.content

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.ui.theme.GsColors

/**
 * Native latex-lite rendering — no WebView, no remote service, no dependencies.
 *
 * Supports the notation AI assistants emit most: \frac, \sqrt, ^{} _{} (and
 * single-token forms), the Greek alphabet and a wide operator set, mapped to
 * Unicode. Superscript/subscript ride SpanStyle baseline shifts; Latin letters
 * render italic; digits and operators stay upright.
 *
 * Graceful degradation is the contract: any UNKNOWN \command makes
 * [renderMath] return null and the caller shows the ORIGINAL expression
 * verbatim — a wrong-looking formula is worse than an honest literal one.
 */

private data class MPiece(val text: String, val script: Int = 0, val italic: Boolean = false)

private val greekMap = mapOf(
    "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε",
    "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η", "theta" to "θ", "vartheta" to "ϑ",
    "iota" to "ι", "kappa" to "κ", "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
    "omicron" to "ο", "pi" to "π", "varpi" to "ϖ", "rho" to "ρ", "varrho" to "ϱ",
    "sigma" to "σ", "varsigma" to "ς", "tau" to "τ", "upsilon" to "υ", "phi" to "φ",
    "varphi" to "ϕ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
    "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ",
    "Pi" to "Π", "Sigma" to "Σ", "Upsilon" to "Υ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω"
)

private val operatorMap = mapOf(
    "times" to "×", "cdot" to "·", "div" to "÷", "pm" to "±", "mp" to "∓",
    "leq" to "≤", "geq" to "≥", "neq" to "≠", "ne" to "≠", "approx" to "≈",
    "equiv" to "≡", "sim" to "∼", "simeq" to "≃", "propto" to "∝", "infty" to "∞",
    "sum" to "∑", "prod" to "∏", "int" to "∫", "iint" to "∬", "oint" to "∮",
    "partial" to "∂", "nabla" to "∇", "in" to "∈", "notin" to "∉", "subset" to "⊂",
    "subseteq" to "⊆", "supset" to "⊃", "supseteq" to "⊇", "cup" to "∪", "cap" to "∩",
    "forall" to "∀", "exists" to "∃", "nexists" to "∤", "emptyset" to "∅",
    "varnothing" to "∅", "rightarrow" to "→", "to" to "→", "leftarrow" to "←",
    "gets" to "←", "leftrightarrow" to "↔", "Rightarrow" to "⇒", "Leftarrow" to "⇐",
    "Leftrightarrow" to "⇔", "mapsto" to "↦", "uparrow" to "↑", "downarrow" to "↓",
    "circ" to "∘", "bullet" to "•", "star" to "⋆", "ast" to "∗", "angle" to "∠",
    "degree" to "°", "perp" to "⊥", "parallel" to "∥", "land" to "∧", "lor" to "∨",
    "neg" to "¬", "hbar" to "ℏ", "ell" to "ℓ", "aleph" to "ℵ", "wp" to "℘",
    "Re" to "ℜ", "Im" to "ℑ", "prime" to "′", "langle" to "⟨", "rangle" to "⟩",
    "lceil" to "⌈", "rceil" to "⌉", "lfloor" to "⌊", "rfloor" to "⌋",
    "cdots" to "⋯", "ldots" to "…", "vdots" to "⋮", "ddots" to "⋱", "over" to "/"
)

/** Pre-normalization: drop spacing/limits commands and delimiters wrappers. */
private fun normalizeLatex(src: String): String = src
    .replace("\\left", "")
    .replace("\\right", "")
    .replace("\\displaystyle", "")
    .replace("\\limits", "")
    .replace("\\,", " ")
    .replace("\\;", " ")
    .replace("\\:", " ")
    .replace("\\!", "")

private class MathParser(private val s: String) {
    var i = 0
    val out = mutableListOf<MPiece>()

    /** Parses until end (or the matching `}` when [stopBrace]); false = unsupported. */
    fun parse(stopBrace: Boolean): Boolean {
        while (i < s.length) {
            val c = s[i]
            when {
                c == '}' -> {
                    if (stopBrace) return true
                    i++
                }
                c == '{' -> {
                    i++
                    if (!parse(true)) return false
                }
                c == '^' || c == '_' -> {
                    i++
                    val script = if (c == '^') 1 else -1
                    val arg = mutableListOf<MPiece>()
                    if (!readArg(arg)) return false
                    out += arg.map { MPiece(it.text, script, it.italic) }
                }
                c == '\\' -> {
                    if (!parseCommand()) return false
                }
                c.isLetter() -> {
                    out += MPiece(c.toString(), italic = true)
                    i++
                }
                c.isWhitespace() -> { out += MPiece(" "); i++ }
                else -> { out += MPiece(c.toString()); i++ }
            }
        }
        return true
    }

    /** Reads a {group} or single-token argument into [sink]. */
    private fun readArg(sink: MutableList<MPiece>): Boolean {
        if (i >= s.length) return false
        if (s[i] == '{') {
            i++
            val saved = out.size
            if (!parse(true)) return false
            sink += out.drop(saved)
            return true
        }
        if (s[i] == '\\') {
            val saved = out.size
            if (!parseCommand()) return false
            sink += out.drop(saved)
            return true
        }
        sink += MPiece(s[i].toString(), italic = s[i].isLetter())
        i++
        return true
    }

    private fun parseCommand(): Boolean {
        if (i + 1 >= s.length) return false
        val start = i + 1
        var end = start
        while (end < s.length && s[end].isLetter()) end++
        val name = s.substring(start, end)
        i = end
        when (name) {
            "frac" -> {
                val a = mutableListOf<MPiece>()
                val b = mutableListOf<MPiece>()
                if (!readArg(a) || !readArg(b)) return false
                out += MPiece("(", italic = false)
                out += a
                out += MPiece(")/(", italic = false)
                out += b
                out += MPiece(")", italic = false)
            }
            "sqrt" -> {
                // Optional [n] root index → superscript before the radical.
                if (i < s.length && s[i] == '[') {
                    var close = s.indexOf(']', i)
                    if (close < 0) return false
                    out += MPiece(s.substring(i + 1, close), script = 1)
                    i = close + 1
                }
                val a = mutableListOf<MPiece>()
                if (!readArg(a)) return false
                out += MPiece("√(", italic = false)
                out += a
                out += MPiece(")", italic = false)
            }
            "text", "mathrm", "operatorname", "mathbf" -> {
                if (i >= s.length || s[i] != '{') return false
                var close = s.indexOf('}', i)
                if (close < 0) return false
                out += MPiece(s.substring(i + 1, close))
                i = close + 1
            }
            else -> {
                greekMap[name]?.let { out += MPiece(it); return true }
                operatorMap[name]?.let { out += MPiece(it); return true }
                return false // unknown command → caller degrades to literal source
            }
        }
        return true
    }
}

/**
 * Renders latex-lite to styled spans, or null when any part is unsupported —
 * the caller then renders the ORIGINAL expression string verbatim.
 */
fun renderMath(latex: String, baseFontSize: TextUnit, color: Color): AnnotatedString? {
    val normalized = normalizeLatex(latex)
    if (normalized.isBlank()) return null
    val parser = MathParser(normalized)
    if (!parser.parse(stopBrace = false)) return null
    if (parser.out.isEmpty()) return null
    return buildAnnotatedString {
        for (piece in parser.out) {
            if (piece.text.isEmpty()) continue
            val style = SpanStyle(
                color = color,
                fontSize = if (piece.script == 0) baseFontSize else (baseFontSize.value * 0.8f).sp,
                baselineShift = when (piece.script) {
                    1 -> BaselineShift(0.35f)
                    -1 -> BaselineShift(-0.25f)
                    else -> BaselineShift.None
                },
                fontStyle = if (piece.italic) FontStyle.Italic else FontStyle.Normal
            )
            withStyle(style) { append(piece.text) }
        }
    }
}
