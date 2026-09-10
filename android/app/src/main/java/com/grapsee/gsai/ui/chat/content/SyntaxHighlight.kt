package com.grapsee.gsai.ui.chat.content

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.grapsee.gsai.ui.theme.GsColors

/**
 * Lightweight syntax colouring — comments, strings, numbers, keywords.
 * Purely cosmetic: an unknown token stays plain, nothing can break the layout.
 * Colours are the theme-resolved code tokens (GsColors.code*) read at the
 * call site and passed in, so this stays a pure function and the remember-cache
 * at the call site re-colours when the theme changes.
 *
 * Step 5 upgrade over the Step-4 tokenizer: triple-quoted strings, block
 * comments, SQL/bash `--` comments, per-language keyword families and a
 * case-insensitive SQL pass. Still ONE regex pass per render — cached by the
 * caller — and unknown languages degrade to the generic cross-language set.
 */

/** Languages whose line comments start with '#'. */
private val hashCommentLanguages = setOf(
    "python", "py", "bash", "sh", "shell", "zsh", "ruby", "rb", "yaml", "yml", "toml", "r", "perl", "makefile", "dockerfile"
)

/** Languages whose line comments start with '--'. */
private val dashCommentLanguages = setOf(
    "sql", "psql", "sqlite", "mysql", "postgresql", "postgres", "lua", "haskell", "hs"
)

private val codeTokenRegex = Regex(
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

// --- per-family keyword sets ---------------------------------------------------

private val kotlinJavaKeywords = setOf(
    "val", "var", "fun", "class", "object", "interface", "enum", "sealed", "data", "companion",
    "init", "constructor", "override", "open", "abstract", "final", "internal", "public",
    "private", "protected", "if", "else", "when", "for", "while", "do", "break", "continue",
    "return", "import", "package", "as", "is", "in", "!in", "is", "try", "catch", "finally",
    "throw", "throws", "suspend", "async", "await", "lateinit", "by", "lazy", "const",
    "static", "extends", "implements", "new", "this", "super", "null", "true", "false",
    "void", "int", "long", "double", "float", "boolean", "char", "byte", "short"
)

private val swiftKeywords = setOf(
    "func", "var", "let", "class", "struct", "enum", "protocol", "extension", "actor",
    "init", "deinit", "self", "Self", "nil", "true", "false", "if", "else", "guard",
    "defer", "for", "while", "repeat", "switch", "case", "default", "break", "continue",
    "return", "import", "static", "public", "private", "internal", "open", "fileprivate",
    "final", "override", "throws", "rethrows", "try", "catch", "async", "await", "some",
    "any", "inout", "mutating", "lazy", "weak", "unowned", "where", "in", "is", "as",
    "typealias", "associatedtype", "willSet", "didSet", "get", "set"
)

private val pythonKeywords = setOf(
    "def", "class", "import", "from", "as", "if", "elif", "else", "for", "while", "in",
    "is", "not", "and", "or", "None", "True", "False", "try", "except", "finally", "raise",
    "with", "lambda", "yield", "global", "nonlocal", "pass", "break", "continue", "return",
    "async", "await", "del", "assert", "self"
)

private val jsKeywords = setOf(
    "const", "let", "var", "function", "class", "extends", "implements", "interface",
    "type", "enum", "import", "export", "from", "default", "async", "await", "return",
    "if", "else", "for", "while", "switch", "case", "break", "continue", "new", "this",
    "super", "typeof", "instanceof", "in", "of", "try", "catch", "finally", "throw",
    "null", "undefined", "true", "false", "static", "get", "set", "public", "private",
    "protected", "readonly", "declare", "namespace", "abstract", "as", "satisfies"
)

private val goKeywords = setOf(
    "func", "package", "import", "var", "const", "type", "struct", "interface", "map",
    "chan", "go", "defer", "if", "else", "for", "range", "switch", "case", "default",
    "return", "break", "continue", "select", "fallthrough", "nil", "true", "false",
    "make", "new", "panic", "recover"
)

private val rustKeywords = setOf(
    "fn", "let", "mut", "const", "static", "struct", "enum", "trait", "impl", "for",
    "while", "loop", "if", "else", "match", "return", "break", "continue", "use", "mod",
    "pub", "crate", "self", "Self", "super", "as", "in", "ref", "move", "where", "async",
    "await", "dyn", "unsafe", "true", "false", "Some", "None", "Ok", "Err"
)

private val cKeywords = setOf(
    "int", "char", "float", "double", "void", "long", "short", "signed", "unsigned",
    "bool", "struct", "union", "enum", "class", "template", "typename", "namespace",
    "using", "public", "private", "protected", "virtual", "override", "const", "static",
    "inline", "new", "delete", "this", "if", "else", "for", "while", "do", "switch",
    "case", "default", "break", "continue", "return", "sizeof", "typedef", "auto",
    "nullptr", "NULL", "true", "false", "include", "define", "pragma", "ifndef", "endif"
)

/** SQL is matched case-insensitively at lookup time. */
private val sqlKeywords = setOf(
    "select", "from", "where", "insert", "into", "values", "update", "set", "delete",
    "create", "table", "alter", "drop", "index", "view", "join", "left", "right", "inner",
    "outer", "full", "cross", "on", "group", "by", "order", "having", "limit", "offset",
    "as", "and", "or", "not", "null", "is", "in", "between", "like", "exists", "union",
    "all", "distinct", "case", "when", "then", "else", "end", "primary", "key", "foreign",
    "references", "default", "constraint", "begin", "commit", "rollback", "transaction",
    "count", "sum", "avg", "min", "max", "asc", "desc"
)

private val bashKeywords = setOf(
    "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac",
    "function", "return", "local", "export", "echo", "cd", "set", "unset", "source",
    "in", "exit", "read", "shift", "trap", "eval", "exec"
)

private val literalKeywords = setOf("true", "false", "null", "nil", "none", "None", "True", "False", "undefined")

private fun keywordSet(language: String?): Set<String>? {
    val lang = language?.lowercase() ?: return null
    return when {
        lang in setOf("kotlin", "kt", "java") -> kotlinJavaKeywords
        lang == "swift" -> swiftKeywords
        lang in setOf("python", "py") -> pythonKeywords
        lang in setOf("javascript", "js", "typescript", "ts", "jsx", "tsx", "node") -> jsKeywords
        lang in setOf("go", "golang") -> goKeywords
        lang == "rust" || lang == "rs" -> rustKeywords
        lang in setOf("c", "cpp", "c++", "h", "hpp", "objc", "objectivec") -> cKeywords
        lang in dashCommentLanguages && lang != "lua" && lang != "haskell" && lang != "hs" -> sqlKeywords
        lang in setOf("bash", "sh", "shell", "zsh") -> bashKeywords
        else -> null
    }
}

fun highlightCode(code: String, language: String?, colors: GsColors): AnnotatedString {
    val kw = colors.codeKeyword
    val str = colors.codeString
    val com = colors.codeComment
    val num = colors.codeNumber
    val lang = language?.lowercase()
    val hashComments = lang in hashCommentLanguages
    val dashComments = lang in dashCommentLanguages
    val languageKeywords = keywordSet(lang)
    val sqlFamily = lang != null && lang in dashCommentLanguages && lang !in setOf("lua", "haskell", "hs")

    return buildAnnotatedString {
        var index = 0
        for (match in codeTokenRegex.findAll(code)) {
            append(code.substring(index, match.range.first))
            val token = match.value
            val color = when {
                token.startsWith("\"\"\"") || token.startsWith("/*") ||
                    token.startsWith("//") || token.startsWith("\"") || token.startsWith("'") -> str
                token.startsWith("#") && hashComments -> com
                token.startsWith("--") && dashComments -> com
                token.first().isDigit() -> num
                languageKeywords != null && languageKeywords.contains(token) -> kw
                languageKeywords != null && sqlFamily && languageKeywords.contains(token.lowercase()) -> kw
                languageKeywords == null &&
                    (codeKeywordsGeneric.contains(token) || literalKeywords.contains(token)) -> kw
                else -> null
            }
            if (color != null) withStyle(SpanStyle(color = color)) { append(token) } else append(token)
            index = match.range.last + 1
        }
        append(code.substring(index))
    }
}

/** Generic cross-language fallback for unknown fences. */
private val codeKeywordsGeneric = setOf(
    "val", "var", "fun", "func", "function", "def", "class", "struct", "enum", "interface",
    "object", "trait", "impl", "type", "if", "else", "elif", "for", "while", "switch", "case",
    "match", "when", "break", "continue", "return", "yield", "import", "from", "package",
    "public", "private", "protected", "static", "final", "const", "new", "this", "self",
    "super", "try", "catch", "finally", "throw", "throws", "await", "async", "let", "in",
    "is", "as", "do", "end", "override", "open", "suspend", "data", "where", "with"
)
