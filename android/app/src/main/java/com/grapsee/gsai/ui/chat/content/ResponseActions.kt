package com.grapsee.gsai.ui.chat.content

import androidx.compose.runtime.staticCompositionLocalOf
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Scoped to one response, never a singleton that can export another chat. */
internal class ResponseCodeActions {
    data class Entry(val language: String?, val source: () -> String, val edit: () -> Unit)
    private val entries = linkedMapOf<Any, Entry>()
    private var pendingEdit = false
    fun register(id: Any, entry: Entry) {
        entries[id] = entry
        if (pendingEdit) { pendingEdit = false; entry.edit() }
    }
    fun remove(id: Any) { entries.remove(id) }
    fun editFirst() {
        val first = entries.values.firstOrNull()
        if (first == null) pendingEdit = true else first.edit()
    }
    fun sources(): List<Pair<String?, String>> = entries.values.map { it.language to it.source() }
}
internal val LocalResponseCodeActions = staticCompositionLocalOf<ResponseCodeActions?> { null }

internal fun responseSourceExtension(language: String?): String = when (language?.lowercase()) {
    "javascript", "js", "node" -> "js"
    "typescript", "ts" -> "ts"
    "python", "python3", "py" -> "py"
    "kotlin" -> "kt"
    "rust" -> "rs"
    "bash", "shell", "sh" -> "sh"
    "htm", "html" -> "html"
    "swift", "java", "cpp", "c", "go", "css", "json", "sql", "svg", "jsx", "tsx" -> language.lowercase()
    else -> "txt"
}

internal fun responseSourceArchive(sources: List<Pair<String?, String>>): ByteArray {
    require(sources.isNotEmpty()) { "No source files are available to export." }
    val output = ByteArrayOutputStream()
    ZipOutputStream(output, Charsets.UTF_8).use { zip ->
        fun entry(name: String, source: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(source.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        entry("README.txt", "GS AI response source snippets\n\nThese are source snippets, not a complete runnable project.\nNo dependencies, build configuration, credentials or execution results have been invented.\nReview each file and supply its required runtime before executing it.\n")
        sources.forEachIndexed { index, (language, source) ->
            entry("snippet-${index + 1}.${responseSourceExtension(language)}", source)
        }
    }
    return output.toByteArray()
}
