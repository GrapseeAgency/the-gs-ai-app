package com.grapsee.gsai.ui.create

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.theme.Aeruo
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.rememberAuroraBrush
import kotlinx.coroutines.delay

/**
 * AERUO KINETIC — CODE WORKSPACE.
 * File chips switch samples, Editor/Output/Diff tabs switch views. The editor
 * is a dark RaisedDark card with simple span-rule syntax colouring (keywords
 * accent, strings secondary, comments muted) and a muted line-number gutter.
 * Run compiles fake-fast (800ms) with a pulsing aurora dot — the sanctioned
 * aurora moment — then reveals the console. Sample data only.
 */

private val codeFiles = listOf("Main.kt", "Engine.kt", "Types.kt", "build.gradle.kts")

private val codeTabs = listOf("Editor", "Output", "Diff")

private val codeKeywords = setOf("fun", "val", "return", "if")

private val codeSamples: Map<String, List<String>> = mapOf(
    "Main.kt" to listOf(
        "// Entry point — greets the engine.",
        "fun main() {",
        "    val engine = Engine(\"Aeruo\")",
        "    if (engine.ready) {",
        "        println(engine.greet())",
        "    }",
        "}"
    ),
    "Engine.kt" to listOf(
        "// Minimal kinematic core.",
        "class Engine(val name: String) {",
        "    val ready: Boolean = name.isNotEmpty()",
        "    fun greet(): String {",
        "        return \"Hello, \$name!\"",
        "    }",
        "}"
    ),
    "Types.kt" to listOf(
        "// Shared value types for the motion loop.",
        "data class Frame(val t: Long, val x: Float)",
        "data class Span(val from: Long, val to: Long)",
        "typealias Handler = (Frame) -> Unit"
    ),
    "build.gradle.kts" to listOf(
        "// App module build logic.",
        "plugins {",
        "    id(\"com.android.application\")",
        "}",
        "dependencies {",
        "    implementation(\"androidx.compose.ui:ui\")",
        "}"
    )
)

private data class DiffLine(val marker: String, val text: String)

private val diffLines = listOf(
    DiffLine("+", "val stiffness = 380f"),
    DiffLine(" ", "val damping = 0.8f"),
    DiffLine("-", "fun integrate(frame: Frame): Frame = frame"),
    DiffLine("+", "fun integrate(frame: Frame): Frame = frame.copy(t = frame.t + 16L)"),
    DiffLine(" ", "// springs use the standard damping ratio"),
    DiffLine(" ", "if (frame.t > deadline) cancel()")
)

@Composable
fun CodeWorkspaceScreen(onBack: () -> Unit) {
    var selectedFile by remember { mutableStateOf("Main.kt") }
    var selectedTab by remember { mutableStateOf("Editor") }
    // 0 idle · 1 running · 2 done
    var runPhase by remember { mutableStateOf(0) }

    LaunchedEffect(runPhase) {
        if (runPhase == 1) {
            delay(800)
            runPhase = 2
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        GsScreenScaffold(
            title = "Code",
            onBack = onBack,
            actions = {
                GsChip(text = "0 problems", selected = true, onClick = {})
                GsChip(text = "Kotlin", selected = false, onClick = {})
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
            ) {
                Spacer(Modifier.height(GsMotion.spaceS))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                ) {
                    codeFiles.forEach { file ->
                        GsChip(text = file, selected = selectedFile == file) { selectedFile = file }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                    ) {
                        codeTabs.forEach { tab ->
                            GsChip(text = tab, selected = selectedTab == tab) { selectedTab = tab }
                        }
                    }
                    Button(
                        onClick = {
                            runPhase = 1
                            selectedTab = "Output"
                        },
                        enabled = runPhase != 1
                    ) {
                        Text(if (runPhase == 1) "Running…" else "Run")
                    }
                }
                when (selectedTab) {
                    "Output" -> OutputCard(runPhase = runPhase)
                    "Diff" -> DiffCard()
                    else -> EditorCard(fileName = selectedFile)
                }
                Spacer(Modifier.height(GsMotion.spaceL))
            }
        }
    }
}

@Composable
private fun EditorCard(fileName: String) {
    val accent = MaterialTheme.colorScheme.primary
    val stringColor = MaterialTheme.colorScheme.secondary
    val lines = codeSamples[fileName] ?: emptyList()
    Surface(
        color = Aeruo.RaisedDark,
        shape = RoundedCornerShape(GsMotion.radiusCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(GsMotion.spaceM)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                lines.forEachIndexed { index, _ ->
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = Aeruo.TextMutedDark,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                lines.forEach { line ->
                    Text(
                        text = buildHighlightedLine(
                            line = line,
                            accent = accent,
                            stringColor = stringColor,
                            commentColor = Aeruo.TextMutedDark,
                            plainColor = Aeruo.TextDark
                        ),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        ),
                        color = Aeruo.TextDark
                    )
                }
            }
        }
    }
}

@Composable
private fun OutputCard(runPhase: Int) {
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp)
    Surface(
        color = Aeruo.RaisedDark,
        shape = RoundedCornerShape(GsMotion.radiusCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(GsMotion.spaceM),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (runPhase) {
                1 -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(rememberAuroraBrush(), CircleShape)
                        )
                        Text(text = "Compiling…", style = mono, color = Aeruo.TextMutedDark)
                    }
                }
                2 -> {
                    Text(text = "> Compiling Main.kt", style = mono, color = Aeruo.TextDark)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = Aeruo.Accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(text = "✓ Build succeeded in 1.2s", style = mono, color = Aeruo.TextDark)
                    }
                    Text(text = "Hello, Aeruo!", style = mono, color = Aeruo.TextDark)
                }
                else -> {
                    Text(
                        text = "Press Run to compile and execute Main.kt.",
                        style = mono,
                        color = Aeruo.TextMutedDark
                    )
                }
            }
        }
    }
}

@Composable
private fun DiffCard() {
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)
    Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
        Text(
            text = "engine.patch",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            color = Aeruo.RaisedDark,
            shape = RoundedCornerShape(GsMotion.radiusCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(GsMotion.spaceM),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                diffLines.forEach { line ->
                    Text(
                        text = line.marker + " " + line.text,
                        style = mono,
                        color = when (line.marker) {
                            "+" -> Color(0xFF2DD4A8) // aurora accent start — sanctioned
                            "-" -> Color(0xFFE5484D) // destructive red — deletion only
                            else -> Aeruo.TextMutedDark
                        }
                    )
                }
            }
        }
    }
}

/** Simple span rules only — no regex engine: keywords accent, strings secondary, comments muted. */
private fun buildHighlightedLine(
    line: String,
    accent: Color,
    stringColor: Color,
    commentColor: Color,
    plainColor: Color
): AnnotatedString = buildAnnotatedString {
    val commentIndex = line.indexOf("//")
    val codePart = if (commentIndex >= 0) line.substring(0, commentIndex) else line
    var index = 0
    while (index < codePart.length) {
        val quote = codePart.indexOf('"', index)
        if (quote < 0) {
            appendWords(codePart.substring(index), accent, plainColor)
            index = codePart.length
        } else {
            if (quote > index) appendWords(codePart.substring(index, quote), accent, plainColor)
            val end = codePart.indexOf('"', quote + 1)
            if (end < 0) {
                withStyle(SpanStyle(color = stringColor)) { append(codePart.substring(quote)) }
                index = codePart.length
            } else {
                withStyle(SpanStyle(color = stringColor)) { append(codePart.substring(quote, end + 1)) }
                index = end + 1
            }
        }
    }
    if (commentIndex >= 0) {
        withStyle(SpanStyle(color = commentColor)) { append(line.substring(commentIndex)) }
    }
}

private fun AnnotatedString.Builder.appendWords(text: String, accent: Color, plainColor: Color) {
    var start = 0
    for (position in text.indices) {
        val character = text[position]
        if (!character.isLetterOrDigit() && character != '_') {
            if (position > start) appendWord(text.substring(start, position), accent, plainColor)
            append(character.toString())
            start = position + 1
        }
    }
    if (start < text.length) appendWord(text.substring(start), accent, plainColor)
}

private fun AnnotatedString.Builder.appendWord(word: String, accent: Color, plainColor: Color) {
    if (word in codeKeywords) {
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(word) }
    } else {
        append(word)
    }
}
