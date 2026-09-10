import SwiftUI

/// Native latex-lite rendering (iOS twin of MathText.kt) — no WebView, no
/// remote service, no dependencies. Supports \frac, \sqrt, ^{} _{} (and
/// single-token forms), the Greek alphabet and a wide operator set mapped to
/// Unicode. Superscript/subscript ride per-piece baseline offsets in a
/// concatenated Text; Latin letters render italic.
///
/// Graceful degradation is the contract: any UNKNOWN \command makes
/// renderMathText return nil and the caller shows the ORIGINAL expression
/// verbatim — a wrong-looking formula is worse than an honest literal one.

private struct MPiece {
    let text: String
    var script: Int = 0 // 1 superscript, -1 subscript, 0 normal
    var italic: Bool = false
}

private let greekMap: [String: String] = [
    "alpha": "α", "beta": "β", "gamma": "γ", "delta": "δ", "epsilon": "ε",
    "varepsilon": "ε", "zeta": "ζ", "eta": "η", "theta": "θ", "vartheta": "ϑ",
    "iota": "ι", "kappa": "κ", "lambda": "λ", "mu": "μ", "nu": "ν", "xi": "ξ",
    "omicron": "ο", "pi": "π", "varpi": "ϖ", "rho": "ρ", "varrho": "ϱ",
    "sigma": "σ", "varsigma": "ς", "tau": "τ", "upsilon": "υ", "phi": "φ",
    "varphi": "ϕ", "chi": "χ", "psi": "ψ", "omega": "ω",
    "Gamma": "Γ", "Delta": "Δ", "Theta": "Θ", "Lambda": "Λ", "Xi": "Ξ",
    "Pi": "Π", "Sigma": "Σ", "Upsilon": "Υ", "Phi": "Φ", "Psi": "Ψ", "Omega": "Ω"
]

private let operatorMap: [String: String] = [
    "times": "×", "cdot": "·", "div": "÷", "pm": "±", "mp": "∓",
    "leq": "≤", "geq": "≥", "neq": "≠", "ne": "≠", "approx": "≈",
    "equiv": "≡", "sim": "∼", "simeq": "≃", "propto": "∝", "infty": "∞",
    "sum": "∑", "prod": "∏", "int": "∫", "iint": "∬", "oint": "∮",
    "partial": "∂", "nabla": "∇", "in": "∈", "notin": "∉", "subset": "⊂",
    "subseteq": "⊆", "supset": "⊃", "supseteq": "⊇", "cup": "∪", "cap": "∩",
    "forall": "∀", "exists": "∃", "nexists": "∤", "emptyset": "∅",
    "varnothing": "∅", "rightarrow": "→", "to": "→", "leftarrow": "←",
    "gets": "←", "leftrightarrow": "↔", "Rightarrow": "⇒", "Leftarrow": "⇐",
    "Leftrightarrow": "⇔", "mapsto": "↦", "uparrow": "↑", "downarrow": "↓",
    "circ": "∘", "bullet": "•", "star": "⋆", "ast": "∗", "angle": "∠",
    "degree": "°", "perp": "⊥", "parallel": "∥", "land": "∧", "lor": "∨",
    "neg": "¬", "hbar": "ℏ", "ell": "ℓ", "aleph": "ℵ", "wp": "℘",
    "Re": "ℜ", "Im": "ℑ", "prime": "′", "langle": "⟨", "rangle": "⟩",
    "lceil": "⌈", "rceil": "⌉", "lfloor": "⌊", "rfloor": "⌋",
    "cdots": "⋯", "ldots": "…", "vdots": "⋮", "ddots": "⋱", "over": "/"
]

/// Pre-normalization: drop spacing/limits commands and delimiter wrappers.
private func normalizeLatex(_ src: String) -> String {
    src.replacingOccurrences(of: "\\left", with: "")
        .replacingOccurrences(of: "\\right", with: "")
        .replacingOccurrences(of: "\\displaystyle", with: "")
        .replacingOccurrences(of: "\\limits", with: "")
        .replacingOccurrences(of: "\\,", with: " ")
        .replacingOccurrences(of: "\\;", with: " ")
        .replacingOccurrences(of: "\\:", with: " ")
        .replacingOccurrences(of: "\\!", with: "")
}

private struct MathParser {
    let s: String
    var i: String.Index
    var out: [MPiece] = []

    init(_ s: String) {
        self.s = s
        self.i = s.startIndex
    }

    var done: Bool { i >= s.endIndex }

    /// Parses until end (or the matching `}` when stopBrace); false = unsupported.
    mutating func parse(stopBrace: Bool) -> Bool {
        while i < s.endIndex {
            let c = s[i]
            if c == "}" {
                if stopBrace { return true }
                i = s.index(after: i)
            } else if c == "{" {
                i = s.index(after: i)
                if !parse(stopBrace: true) { return false }
            } else if c == "^" || c == "_" {
                i = s.index(after: i)
                let script = c == "^" ? 1 : -1
                var arg: [MPiece] = []
                if !readArg(into: &arg) { return false }
                out.append(contentsOf: arg.map { piece in
                    MPiece(text: piece.text, script: script, italic: piece.italic)
                })
            } else if c == "\\" {
                if !parseCommand() { return false }
            } else if c.isLetter {
                out.append(MPiece(text: String(c), italic: true))
                i = s.index(after: i)
            } else if c.isWhitespace {
                out.append(MPiece(text: " "))
                i = s.index(after: i)
            } else {
                out.append(MPiece(text: String(c)))
                i = s.index(after: i)
            }
        }
        return true
    }

    /// Reads a {group} or single-token argument into sink.
    private mutating func readArg(into sink: inout [MPiece]) -> Bool {
        if i >= s.endIndex { return false }
        if s[i] == "{" {
            i = s.index(after: i)
            let saved = out.count
            if !parse(stopBrace: true) { return false }
            sink.append(contentsOf: out.suffix(from: saved))
            return true
        }
        if s[i] == "\\" {
            let saved = out.count
            if !parseCommand() { return false }
            sink.append(contentsOf: out.suffix(from: saved))
            return true
        }
        sink.append(MPiece(text: String(s[i]), italic: s[i].isLetter))
        i = s.index(after: i)
        return true
    }

    private mutating func parseCommand() -> Bool {
        let after = s.index(after: i)
        if after >= s.endIndex { return false }
        var end = after
        while end < s.endIndex, s[end].isLetter { end = s.index(after: end) }
        let name = String(s[after..<end])
        i = end
        switch name {
        case "frac":
            var a: [MPiece] = []
            var b: [MPiece] = []
            if !readArg(into: &a) || !readArg(into: &b) { return false }
            out.append(MPiece(text: "("))
            out.append(contentsOf: a)
            out.append(MPiece(text: ")/("))
            out.append(contentsOf: b)
            out.append(MPiece(text: ")"))
        case "sqrt":
            // Optional [n] root index → superscript before the radical.
            if i < s.endIndex, s[i] == "[" {
                guard let close = s[i...].firstIndex(of: "]") else { return false }
                out.append(MPiece(text: String(s[s.index(after: i)..<close]), script: 1))
                i = s.index(after: close)
            }
            var a: [MPiece] = []
            if !readArg(into: &a) { return false }
            out.append(MPiece(text: "√("))
            out.append(contentsOf: a)
            out.append(MPiece(text: ")"))
        case "text", "mathrm", "operatorname", "mathbf":
            guard i < s.endIndex, s[i] == "{", let close = s[i...].firstIndex(of: "}") else { return false }
            out.append(MPiece(text: String(s[s.index(after: i)..<close])))
            i = s.index(after: close)
        default:
            if let symbol = greekMap[name] {
                out.append(MPiece(text: symbol))
                return true
            }
            if let symbol = operatorMap[name] {
                out.append(MPiece(text: symbol))
                return true
            }
            return false // unknown command → caller degrades to literal source
        }
        return true
    }
}

/// Renders latex-lite to a styled Text, or nil when any part is unsupported —
/// the caller then renders the ORIGINAL expression string verbatim.
func renderMathText(_ latex: String, baseSize: CGFloat, color: Color) -> Text? {
    guard var attributed = renderMathAttributedString(latex, baseSize: baseSize, color: color) else { return nil }
    attributed.font = .system(size: baseSize)
    return Text(attributed)
}

/// AttributedString form — used for INLINE math inside a paragraph (a Text
/// cannot be embedded in an AttributedString). Baseline shifts ride the
/// SwiftUI `baselineOffset` attribute; sub/superscript runs carry smaller fonts.
func renderMathAttributedString(_ latex: String, baseSize: CGFloat, color: Color) -> AttributedString? {
    let normalized = normalizeLatex(latex)
    if normalized.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return nil }
    var parser = MathParser(normalized)
    if !parser.parse(stopBrace: false) { return nil }
    if parser.out.isEmpty { return nil }

    var result = AttributedString()
    for piece in parser.out {
        if piece.text.isEmpty { continue }
        var run = AttributedString(piece.text)
        run.font = .system(size: piece.script == 0 ? baseSize : baseSize * 0.8)
        run.foregroundColor = color
        if piece.italic {
            run.inlinePresentationIntent = .emphasized
        }
        switch piece.script {
        case 1: run.swiftUI.baselineOffset = baseSize * 0.33
        case -1: run.swiftUI.baselineOffset = -baseSize * 0.22
        default: break
        }
        result.append(run)
    }
    return result
}
