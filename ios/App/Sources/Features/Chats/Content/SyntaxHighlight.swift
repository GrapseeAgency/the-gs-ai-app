import SwiftUI
import UIKit

/// Lightweight syntax colouring (iOS twin of SyntaxHighlight.kt) — comments,
/// strings, numbers, keywords. Purely cosmetic: an unknown token stays plain,
/// nothing can break the layout. Tokens are dynamic UIColors (light + dark +
/// high-contrast) resolved per trait collection at render.
///
/// Step 5 upgrade over the Step-4 tokenizer: triple-quoted strings, block
/// comments, SQL/bash `--` comments, per-language keyword families, and a
/// generic cross-language fallback for unknown fences. Still ONE regex pass
/// per render (cached by the caller); unknown languages degrade gracefully.

/// Languages whose line comments start with '#'.
private let hashCommentLanguages: Set<String> = [
    "python", "py", "bash", "sh", "shell", "zsh", "ruby", "rb", "yaml", "yml", "toml", "r", "perl", "makefile", "dockerfile"
]

/// Languages whose line comments start with '--'.
private let dashCommentLanguages: Set<String> = [
    "sql", "psql", "sqlite", "mysql", "postgresql", "postgres", "lua", "haskell", "hs"
]

private let codeTokenRegex = try! NSRegularExpression(pattern:
    "(\"\"\"[\\s\\S]*?\"\"\"" +
        "|/\\*[\\s\\S]*?\\*/" +
        "|//[^\\n]*" +
        "|#[^\\n]*" +
        "|--[^\\n]*" +
        "|\"(?:\\\\.|[^\"\\\\\\n])*\"" +
        "|'(?:\\\\.|[^'\\\\\\n])*'" +
        "|\\b\\d+(?:\\.\\d+)?\\b" +
        "|[A-Za-z_][A-Za-z0-9_]*)"
)

private let kotlinJavaKeywords: Set<String> = [
    "val", "var", "fun", "class", "object", "interface", "enum", "sealed", "data", "companion",
    "init", "constructor", "override", "open", "abstract", "final", "internal", "public",
    "private", "protected", "if", "else", "when", "for", "while", "do", "break", "continue",
    "return", "import", "package", "as", "is", "in", "try", "catch", "finally", "throw",
    "throws", "suspend", "async", "await", "lateinit", "by", "lazy", "const", "static",
    "extends", "implements", "new", "this", "super", "null", "true", "false", "void",
    "int", "long", "double", "float", "boolean", "char", "byte", "short"
]

private let swiftKeywords: Set<String> = [
    "func", "var", "let", "class", "struct", "enum", "protocol", "extension", "actor",
    "init", "deinit", "self", "Self", "nil", "true", "false", "if", "else", "guard",
    "defer", "for", "while", "repeat", "switch", "case", "default", "break", "continue",
    "return", "import", "static", "public", "private", "internal", "open", "fileprivate",
    "final", "override", "throws", "rethrows", "try", "catch", "async", "await", "some",
    "any", "inout", "mutating", "lazy", "weak", "unowned", "where", "in", "is", "as",
    "typealias", "associatedtype", "willSet", "didSet", "get", "set"
]

private let pythonKeywords: Set<String> = [
    "def", "class", "import", "from", "as", "if", "elif", "else", "for", "while", "in",
    "is", "not", "and", "or", "None", "True", "False", "try", "except", "finally", "raise",
    "with", "lambda", "yield", "global", "nonlocal", "pass", "break", "continue", "return",
    "async", "await", "del", "assert", "self"
]

private let jsKeywords: Set<String> = [
    "const", "let", "var", "function", "class", "extends", "implements", "interface",
    "type", "enum", "import", "export", "from", "default", "async", "await", "return",
    "if", "else", "for", "while", "switch", "case", "break", "continue", "new", "this",
    "super", "typeof", "instanceof", "in", "of", "try", "catch", "finally", "throw",
    "null", "undefined", "true", "false", "static", "get", "set", "public", "private",
    "protected", "readonly", "declare", "namespace", "abstract", "as", "satisfies"
]

private let goKeywords: Set<String> = [
    "func", "package", "import", "var", "const", "type", "struct", "interface", "map",
    "chan", "go", "defer", "if", "else", "for", "range", "switch", "case", "default",
    "return", "break", "continue", "select", "fallthrough", "nil", "true", "false",
    "make", "new", "panic", "recover"
]

private let rustKeywords: Set<String> = [
    "fn", "let", "mut", "const", "static", "struct", "enum", "trait", "impl", "for",
    "while", "loop", "if", "else", "match", "return", "break", "continue", "use", "mod",
    "pub", "crate", "self", "Self", "super", "as", "in", "ref", "move", "where", "async",
    "await", "dyn", "unsafe", "true", "false", "Some", "None", "Ok", "Err"
]

private let cKeywords: Set<String> = [
    "int", "char", "float", "double", "void", "long", "short", "signed", "unsigned",
    "bool", "struct", "union", "enum", "class", "template", "typename", "namespace",
    "using", "public", "private", "protected", "virtual", "override", "const", "static",
    "inline", "new", "delete", "this", "if", "else", "for", "while", "do", "switch",
    "case", "default", "break", "continue", "return", "sizeof", "typedef", "auto",
    "nullptr", "NULL", "true", "false", "include", "define", "pragma", "ifndef", "endif"
]

/// Matched case-insensitively at lookup time.
private let sqlKeywords: Set<String> = [
    "select", "from", "where", "insert", "into", "values", "update", "set", "delete",
    "create", "table", "alter", "drop", "index", "view", "join", "left", "right", "inner",
    "outer", "full", "cross", "on", "group", "by", "order", "having", "limit", "offset",
    "as", "and", "or", "not", "null", "is", "in", "between", "like", "exists", "union",
    "all", "distinct", "case", "when", "then", "else", "end", "primary", "key", "foreign",
    "references", "default", "constraint", "begin", "commit", "rollback", "transaction",
    "count", "sum", "avg", "min", "max", "asc", "desc"
]

private let bashKeywords: Set<String> = [
    "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac",
    "function", "return", "local", "export", "echo", "cd", "set", "unset", "source",
    "in", "exit", "read", "shift", "trap", "eval", "exec"
]

private let literalKeywords: Set<String> = [
    "true", "false", "null", "nil", "none", "None", "True", "False", "undefined"
]

/// Generic cross-language fallback for unknown fences.
private let codeKeywordsGeneric: Set<String> = [
    "val", "var", "fun", "func", "function", "def", "class", "struct", "enum", "interface",
    "object", "trait", "impl", "type", "if", "else", "elif", "for", "while", "switch", "case",
    "match", "when", "break", "continue", "return", "yield", "import", "from", "package",
    "public", "private", "protected", "static", "final", "const", "new", "this", "self",
    "super", "try", "catch", "finally", "throw", "throws", "await", "async", "let", "in",
    "is", "as", "do", "end", "override", "open", "suspend", "data", "where", "with"
]

private func keywordSet(for language: String?) -> (Set<String>, caseInsensitive: Bool)? {
    let lang = (language ?? "").lowercased()
    if lang.isEmpty { return nil }
    if ["kotlin", "kt", "java"].contains(lang) { return (kotlinJavaKeywords, false) }
    if lang == "swift" { return (swiftKeywords, false) }
    if ["python", "py"].contains(lang) { return (pythonKeywords, false) }
    if ["javascript", "js", "typescript", "ts", "jsx", "tsx", "node"].contains(lang) { return (jsKeywords, false) }
    if ["go", "golang"].contains(lang) { return (goKeywords, false) }
    if lang == "rust" || lang == "rs" { return (rustKeywords, false) }
    if ["c", "cpp", "c++", "h", "hpp", "objc", "objectivec"].contains(lang) { return (cKeywords, false) }
    if dashCommentLanguages.contains(lang), lang != "lua", lang != "haskell", lang != "hs" { return (sqlKeywords, true) }
    if ["bash", "sh", "shell", "zsh"].contains(lang) { return (bashKeywords, false) }
    return nil
}

/// Returns the highlighted code as styled SwiftUI `Text`; colours come ONLY
/// from the Aero code tokens.
func highlightedCode(_ code: String, language: String?) -> Text {
    let kw = UIColor(Aero.codeKeyword)
    let st = UIColor(Aero.codeString)
    let cm = UIColor(Aero.codeComment)
    let nm = UIColor(Aero.codeNumber)
    let lang = (language ?? "").lowercased()
    let hashComments = hashCommentLanguages.contains(lang)
    let dashComments = dashCommentLanguages.contains(lang)
    let keywords = keywordSet(for: language)

    let result = NSMutableAttributedString(string: "")
    let ns = code as NSString
    var cursor = 0
    for match in codeTokenRegex.matches(in: code, range: NSRange(location: 0, length: ns.length)) {
        if match.range.location > cursor {
            result.append(NSAttributedString(string: ns.substring(with: NSRange(location: cursor, length: match.range.location - cursor))))
        }
        let token = ns.substring(with: match.range)
        let color: UIColor? = {
            if token.hasPrefix("\"\"\"") || token.hasPrefix("/*") ||
                token.hasPrefix("//") || token.hasPrefix("\"") || token.hasPrefix("'") { return st }
            if token.hasPrefix("#") && hashComments { return cm }
            if token.hasPrefix("--") && dashComments { return cm }
            if let first = token.first, first.isNumber { return nm }
            if let keywords = keywords {
                if keywords.0.contains(token) { return kw }
                if keywords.caseInsensitive && keywords.0.contains(token.lowercased()) { return kw }
                return nil
            }
            if codeKeywordsGeneric.contains(token) || literalKeywords.contains(token) { return kw }
            return nil
        }()
        if let color = color {
            result.append(NSAttributedString(string: token, attributes: [.foregroundColor: color]))
        } else {
            result.append(NSAttributedString(string: token))
        }
        cursor = match.range.location + match.range.length
    }
    if cursor < ns.length {
        result.append(NSAttributedString(string: ns.substring(from: cursor)))
    }
    var attributed = AttributedString(result)
    attributed.font = .system(size: 12, weight: .regular, design: .monospaced)
    return Text(attributed)
}
