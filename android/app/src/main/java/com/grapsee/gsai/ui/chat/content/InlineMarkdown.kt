package com.grapsee.gsai.ui.chat.content

/**
 * Inline markdown → [InlineSpan] tree. One precompiled alternation regex,
 * COMPLETE matches only: an unclosed marker stays literal text, so a growing
 * stream never flickers or reinterprets half-finished emphasis.
 *
 * Alternative order = precedence (leftmost position wins within the engine,
 * and alternatives are tried in order at each position):
 *   1. fenced inline code  `…`
 *   2. math \(…\) \[…\] $$…$$ and math-ish $…$
 *   3. images ![alt](url) (inline images degrade to their link form)
 *   4. links [text](url) — http(s) only
 *   5. bare autolink URLs (trailing sentence punctuation excluded)
 *   6. bold/italic/strike (recursive inner parse)
 *   7. backslash escapes — LAST so "\*" still wins at the backslash position
 *      but never robs the math delimiters above of \( \) \[ \]
 *   8. PHASE 8.1 citation markers [N] + defensive fullwidth variants — AFTER
 *      everything else, so a complete markdown link still wins at the same
 *      '[' position and only a BARE [N] becomes a [InlineSpan.CitationSpan].
 *      The parser is source-blind (just the number); the renderer decides
 *      chip vs. literal text.
 */
private val inlineRegex = Regex(
    """`([^`\n]+)`""" +
        "|\\\\\\(([\\s\\S]+?)\\\\\\)" +
        """|\\\[([\s\S]+?)\\\]""" +
        """|\$\$(?!\s)([\s\S]+?)\$\$""" +
        """|\$(?=[^$\n]*[\\^_])([^$\n]+?)\$""" +
        """|!\[([^\]]*)\]\(([^)\s]+)\)""" +
        """|\[([^\]\n]+)\]\((https?://[^)\s]+)\)""" +
        """|(https?://[^\s<>\[\]{}"'()]+[^\s<>\[\]{}"'().,;:!?])""" +
        """|\*\*([\s\S]+?)\*\*""" +
        """|__([\s\S]+?)__""" +
        """|(?<!\*)\*([^*\n]+?)\*(?!\*)""" +
        """|(?<![\w])_(?!_)([^_\n]+?)_(?![\w])""" +
        """|~~([\s\S]+?)~~""" +
        """|\\([*_#`\[\]()~\\-])""" +
        // PHASE 8.1 citation markers — appended LAST so every group index above
        // stays stable and the established precedence is preserved: a complete
        // markdown link [text](url) still wins at the '[' position, escaped
        // brackets still win at the '\' position, and a bare [N] only becomes a
        // citation when nothing earlier claimed it. 1–2 digits keeps bracketed
        // years like [2024] out. Fullwidth variants are the defensive fallback
        // for a server that skipped bracket normalization (it normalizes; the
        // client survives if that ever regresses).
        """|\[(\d{1,2})\]""" +
        "|【(\\d{1,2})】" +
        "|〚(\\d{1,2})〛" +
        "|［(\\d{1,2})］"
)

// Group indices of the combined pattern.
private const val G_CODE = 1
private const val G_MATH_PAREN = 2
private const val G_MATH_BRACKET = 3
private const val G_MATH_DISPLAY = 4
private const val G_MATH_DOLLAR = 5
private const val G_IMG_ALT = 6
private const val G_IMG_URL = 7
private const val G_LINK_TEXT = 8
private const val G_LINK_URL = 9
private const val G_BARE_URL = 10
private const val G_BOLD_STAR = 11
private const val G_BOLD_UNDER = 12
private const val G_ITALIC_STAR = 13
private const val G_ITALIC_UNDER = 14
private const val G_STRIKE = 15
private const val G_ESCAPE = 16
// PHASE 8.1 citation markers (appended last — existing indices untouched).
private const val G_CITE = 17
private const val G_CITE_FW_SQUARE = 18  // 【N】
private const val G_CITE_FW_TORTOISE = 19 // 〚N〛
private const val G_CITE_FW_FULLWIDTH = 20 // ［N］

/** Schemes we will never turn into links — model output is not trusted. */
private val blockedSchemes = setOf("javascript:", "data:", "file:", "vbscript:", "blob:")

fun parseInline(text: String): List<InlineSpan> {
    if (text.isEmpty()) return emptyList()
    val spans = mutableListOf<InlineSpan>()
    var index = 0
    while (index < text.length) {
        val match = inlineRegex.find(text, startIndex = index)
        if (match == null) {
            spans += InlineSpan.TextSpan(text.substring(index))
            break
        }
        if (match.range.first > index) spans += InlineSpan.TextSpan(text.substring(index, match.range.first))
        val g = match.groupValues
        when {
            g[G_CODE].isNotEmpty() -> spans += InlineSpan.CodeSpan(g[G_CODE])

            g[G_MATH_PAREN].isNotEmpty() -> spans += InlineSpan.MathSpan(g[G_MATH_PAREN])
            g[G_MATH_BRACKET].isNotEmpty() -> spans += InlineSpan.MathSpan(g[G_MATH_BRACKET])
            g[G_MATH_DISPLAY].isNotEmpty() -> spans += InlineSpan.MathSpan(g[G_MATH_DISPLAY])
            g[G_MATH_DOLLAR].isNotEmpty() -> spans += InlineSpan.MathSpan(g[G_MATH_DOLLAR])

            g[G_IMG_URL].isNotEmpty() -> spans += safeLink(
                text = g[G_IMG_ALT].ifBlank { g[G_IMG_URL] },
                url = g[G_IMG_URL]
            )

            g[G_LINK_URL].isNotEmpty() -> spans += safeLink(g[G_LINK_TEXT], g[G_LINK_URL])

            g[G_BARE_URL].isNotEmpty() -> spans += safeLink(g[G_BARE_URL], g[G_BARE_URL])

            g[G_BOLD_STAR].isNotEmpty() -> spans += InlineSpan.BoldSpan(parseInline(g[G_BOLD_STAR]))
            g[G_BOLD_UNDER].isNotEmpty() -> spans += InlineSpan.BoldSpan(parseInline(g[G_BOLD_UNDER]))
            g[G_ITALIC_STAR].isNotEmpty() -> spans += InlineSpan.ItalicSpan(parseInline(g[G_ITALIC_STAR]))
            g[G_ITALIC_UNDER].isNotEmpty() -> spans += InlineSpan.ItalicSpan(parseInline(g[G_ITALIC_UNDER]))

            g[G_STRIKE].isNotEmpty() -> spans += InlineSpan.StrikeSpan(parseInline(g[G_STRIKE]))

            g[G_ESCAPE].isNotEmpty() -> spans += InlineSpan.TextSpan(g[G_ESCAPE])

            // PHASE 8.1: a bare complete [N] (or fullwidth twin) — the number
            // alone travels; chip vs. literal text is a renderer decision.
            g[G_CITE].isNotEmpty() -> citationSpan(spans, g[G_CITE], match.value)
            g[G_CITE_FW_SQUARE].isNotEmpty() -> citationSpan(spans, g[G_CITE_FW_SQUARE], match.value)
            g[G_CITE_FW_TORTOISE].isNotEmpty() -> citationSpan(spans, g[G_CITE_FW_TORTOISE], match.value)
            g[G_CITE_FW_FULLWIDTH].isNotEmpty() -> citationSpan(spans, g[G_CITE_FW_FULLWIDTH], match.value)
        }
        index = match.range.last + 1
    }
    return spans
}

private fun citationSpan(spans: MutableList<InlineSpan>, number: String, fullMatch: String) {
    val value = number.toIntOrNull()
    if (value != null && value > 0) {
        spans += InlineSpan.CitationSpan(value)
    } else {
        // Defensive: a malformed marker (e.g. "[0]") stays the exact literal text.
        spans += InlineSpan.TextSpan(fullMatch)
    }
}

private fun safeLink(text: String, url: String): InlineSpan {
    val lower = url.lowercase()
    val schemeOk = lower.startsWith("https://") || lower.startsWith("http://") ||
        lower.startsWith("mailto:")
    if (!schemeOk || blockedSchemes.any { lower.startsWith(it) }) {
        return InlineSpan.TextSpan(text)
    }
    return InlineSpan.LinkSpan(text = text, url = url)
}
