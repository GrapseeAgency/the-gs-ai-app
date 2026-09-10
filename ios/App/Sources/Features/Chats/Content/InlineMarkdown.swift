import Foundation

/// Inline markdown → [InlineSpan] tree (iOS twin of InlineMarkdown.kt).
/// One precompiled alternation regex, COMPLETE matches only: an unclosed
/// marker stays literal text, so a growing stream never flickers or
/// reinterprets half-finished emphasis.
///
/// Alternative order = precedence: code, math delimiters, images (degraded to
/// links), links (http(s) only), bare autolinks, bold/italic/strike, and
/// backslash escapes LAST so "\*" wins at the backslash position but never
/// robs \( \) \[ \] of math detection.
private let inlineRegex = try! NSRegularExpression(
    pattern: "`([^`\\n]+)`" +
        "|\\\\\\(([\\s\\S]+?)\\\\\\)" +
        "|\\\\\\[([\\s\\S]+?)\\\\\\]" +
        "|\\$\\$(?!\\s)([\\s\\S]+?)\\$\\$" +
        "|\\$(?=[^$\\n]*[\\\\^_])([^$\\n]+?)\\$" +
        "|!\\[([^\\]]*)\\]\\(([^)\\s]+)\\)" +
        "|\\[([^\\]\\n]+)\\]\\((https?://[^)\\s]+)\\)" +
        "|(https?://[^\\s<>\\[\\]{}\"'()]+[^\\s<>\\[\\]{}\"'().,;:!?])" +
        "|\\*\\*([\\s\\S]+?)\\*\\*" +
        "|__([\\s\\S]+?)__" +
        "|(?<!\\*)\\*([^*\\n]+?)\\*(?!\\*)" +
        "|(?<![\\w])_(?!_)([^_\\n]+?)_(?![\\w])" +
        "|~~([\\s\\S]+?)~~" +
        "|\\\\([*_#`\\[\\]()~\\\\-])"
)

// Group indices of the combined pattern.
private let G_CODE = 1
private let G_MATH_PAREN = 2
private let G_MATH_BRACKET = 3
private let G_MATH_DISPLAY = 4
private let G_MATH_DOLLAR = 5
private let G_IMG_ALT = 6
private let G_IMG_URL = 7
private let G_LINK_TEXT = 8
private let G_LINK_URL = 9
private let G_BARE_URL = 10
private let G_BOLD_STAR = 11
private let G_BOLD_UNDER = 12
private let G_ITALIC_STAR = 13
private let G_ITALIC_UNDER = 14
private let G_STRIKE = 15
private let G_ESCAPE = 16

/// Schemes we will never turn into links — model output is not trusted.
private let blockedSchemes: Set<String> = ["javascript:", "data:", "file:", "vbscript:", "blob:"]

func parseInline(_ text: String) -> [InlineSpan] {
    if text.isEmpty { return [] }
    var spans: [InlineSpan] = []
    let ns = text as NSString
    var index = 0
    while index < ns.length {
        let range = NSRange(location: index, length: ns.length - index)
        guard let match = inlineRegex.firstMatch(in: text, range: range) else {
            spans.append(.text(ns.substring(from: index)))
            break
        }
        if match.range.location > index {
            spans.append(.text(ns.substring(with: NSRange(location: index, length: match.range.location - index))))
        }
        func group(_ n: Int) -> String {
            let r = match.range(at: n)
            return r.location == NSNotFound ? "" : ns.substring(with: r)
        }
        if !group(G_CODE).isEmpty {
            spans.append(.code(group(G_CODE)))
        } else if !group(G_MATH_PAREN).isEmpty {
            spans.append(.math(latex: group(G_MATH_PAREN)))
        } else if !group(G_MATH_BRACKET).isEmpty {
            spans.append(.math(latex: group(G_MATH_BRACKET)))
        } else if !group(G_MATH_DISPLAY).isEmpty {
            spans.append(.math(latex: group(G_MATH_DISPLAY)))
        } else if !group(G_MATH_DOLLAR).isEmpty {
            spans.append(.math(latex: group(G_MATH_DOLLAR)))
        } else if !group(G_IMG_URL).isEmpty {
            spans.append(safeLink(text: group(G_IMG_ALT).isEmpty ? group(G_IMG_URL) : group(G_IMG_ALT), url: group(G_IMG_URL)))
        } else if !group(G_LINK_URL).isEmpty {
            spans.append(safeLink(text: group(G_LINK_TEXT), url: group(G_LINK_URL)))
        } else if !group(G_BARE_URL).isEmpty {
            spans.append(safeLink(text: group(G_BARE_URL), url: group(G_BARE_URL)))
        } else if !group(G_BOLD_STAR).isEmpty {
            spans.append(.bold(parseInline(group(G_BOLD_STAR))))
        } else if !group(G_BOLD_UNDER).isEmpty {
            spans.append(.bold(parseInline(group(G_BOLD_UNDER))))
        } else if !group(G_ITALIC_STAR).isEmpty {
            spans.append(.italic(parseInline(group(G_ITALIC_STAR))))
        } else if !group(G_ITALIC_UNDER).isEmpty {
            spans.append(.italic(parseInline(group(G_ITALIC_UNDER))))
        } else if !group(G_STRIKE).isEmpty {
            spans.append(.strike(parseInline(group(G_STRIKE))))
        } else if !group(G_ESCAPE).isEmpty {
            spans.append(.text(group(G_ESCAPE)))
        }
        index = match.range.location + match.range.length
    }
    return spans
}

private func safeLink(text: String, url: String) -> InlineSpan {
    let lower = url.lowercased()
    let schemeOk = lower.hasPrefix("https://") || lower.hasPrefix("http://") || lower.hasPrefix("mailto:")
    if !schemeOk || blockedSchemes.contains(where: { lower.hasPrefix($0) }) {
        return .text(text)
    }
    return .link(text: text, url: url)
}
