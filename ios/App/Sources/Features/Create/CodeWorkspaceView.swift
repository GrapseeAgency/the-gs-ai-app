import SwiftUI
import UIKit

// MARK: - Stagger entrance (private per-file helper)

/// Fades + lifts a section into place, delayed by its index —
/// the Aeruo Kinetic section entrance.
private struct StaggerIn<Content: View>: View {
    let index: Int
    @ViewBuilder var content: () -> Content

    @State private var appeared = false

    var body: some View {
        content()
            .opacity(appeared ? 1 : 0)
            .offset(y: appeared ? 0 : 16)
            .onAppear {
                withAnimation(Aero.spring.delay(Aero.stagger(index))) {
                    appeared = true
                }
            }
    }
}

// MARK: - Code — editor, console and diff on an obsidian canvas

/// Full-screen code workspace (presented via .fullScreenCover). Owns its
/// chrome: close control, serif title, language chip. Files, build output and
/// the diff are local sample state.
struct CodeWorkspaceView: View {

    @Environment(\.dismiss) private var dismiss

    /// Obsidian code canvas — built through the frozen Aero.dynamic helper
    /// using Aero.surface's exact dark value (11151C). The frozen palette has
    /// no guaranteed-dark token, so this canvas stays dark in both
    /// appearances, like a terminal.
    private let inkSurface = Aero.dynamic(
        light: UIColor(red: 0.067, green: 0.082, blue: 0.110, alpha: 1),
        dark: UIColor(red: 0.067, green: 0.082, blue: 0.110, alpha: 1))
    private let inkText = Color.white.opacity(0.92)
    private let inkMuted = Color.white.opacity(0.45)
    /// Soft sky for string literals — an Aurora family token used flat.
    private let inkString = Aero.aurora[1]

    private let keywords: Set<String> = [
        "fun", "val", "var", "return", "if", "else", "class", "data",
        "import", "package", "when", "object", "for", "in"
    ]

    // MARK: Sample files

    private struct CodeFile: Identifiable {
        /// Identity from the file name — a per-instance UUID would re-identify
        /// every chip and the whole editor on every state change.
        var id: String { name }
        let name: String
        let lines: [String]
    }

    private let mainLines: [String] = [
        "// Entry point — engine smoke test",
        "package app.aeruo.main",
        "",
        "import app.aeruo.engine.Engine",
        "",
        "fun main() {",
        "    val engine = Engine(name = \"aurora\")",
        "    val summary = engine.summarise(\"EV battery supply chain\")",
        "    if (summary.isNotBlank()) {",
        "        println(summary)",
        "    }",
        "}"
    ]

    private let engineLines: [String] = [
        "// Summariser with a pluggable backend",
        "package app.aeruo.engine",
        "",
        "class Engine(val name: String) {",
        "",
        "    fun summarise(topic: String): String {",
        "        // TODO swap in the streaming client",
        "        val sources = 5",
        "        return \"Read $sources sources on $topic\"",
        "    }",
        "}"
    ]

    private let typesLines: [String] = [
        "// Core value types shared across modules",
        "package app.aeruo.types",
        "",
        "data class Source(",
        "    val title: String,",
        "    val domain: String,",
        "    val year: Int",
        ")",
        "",
        "data class Synthesis(",
        "    val answer: String,",
        "    val citations: List<Int>",
        ")"
    ]

    private let gradleLines: [String] = [
        "// App module — pinned versions",
        "plugins {",
        "    alias(libs.plugins.android.application)",
        "    alias(libs.plugins.kotlin.android)",
        "}",
        "",
        "dependencies {",
        "    implementation(libs.compose.runtime)",
        "    implementation(libs.ktor.client.cio)",
        "}"
    ]

    private var files: [CodeFile] {
        [
            CodeFile(name: "Main.kt", lines: mainLines),
            CodeFile(name: "Engine.kt", lines: engineLines),
            CodeFile(name: "Types.kt", lines: typesLines),
            CodeFile(name: "build.gradle.kts", lines: gradleLines)
        ]
    }

    private let diffLines: [String] = [
        "+ fun summarise(topic: String): Summary {",
        "- fun summarise(topic: String): String {",
        "+   val sources = readSources(topic)",
        "-   val sources = 5",
        "    return Summary(answer, sources.count)",
        "  }"
    ]

    // MARK: State

    @State private var selectedFile = "Main.kt"
    @State private var selectedTab = "Editor"
    @State private var isBuilding = false
    @State private var buildDone = false

    private var currentFile: CodeFile {
        files.first { $0.name == selectedFile } ?? files[0]
    }

    // MARK: Body

    var body: some View {
        ZStack {
            Aero.background.ignoresSafeArea()
            VStack(spacing: 0) {
                header
                    .padding(.horizontal, Aero.Spacing.m)
                    .padding(.top, Aero.Spacing.s)
                    .padding(.bottom, Aero.Spacing.m)
                ScrollView {
                    VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                        StaggerIn(index: 0) { fileSection }
                        StaggerIn(index: 1) { tabSection }
                        StaggerIn(index: 2) { contentSection }
                    }
                    .padding(.horizontal, Aero.Spacing.m)
                    .padding(.bottom, Aero.Spacing.xl)
                }
            }
        }
        .toolbar(.hidden, for: .navigationBar)
        .onAppear { GSHaptics.prepare() }
    }

    // MARK: Header (own chrome — no router)

    private var header: some View {
        HStack(spacing: Aero.Spacing.s) {
            closeButton
            Text("Code")
                .font(Aero.headline())
                .foregroundStyle(Aero.text)
            Spacer()
            AeroChip(text: "Kotlin")
        }
    }

    private var closeButton: some View {
        Button {
            dismiss()
        } label: {
            Image(systemName: "xmark")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Aero.text)
                .frame(width: 36, height: 36)
                .background(Circle().fill(Aero.raised))
                .overlay(Circle().stroke(Aero.outline, lineWidth: 1))
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityLabel("Close")
    }

    // MARK: File chips

    private var fileSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Files")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Aero.Spacing.s) {
                    ForEach(files) { file in
                        AeroChip(text: file.name, selected: selectedFile == file.name) {
                            GSHaptics.select()
                            withAnimation(Aero.snappy) { selectedFile = file.name }
                        }
                    }
                }
                .padding(.vertical, 2)
            }
        }
    }

    // MARK: Tabs

    private var tabSection: some View {
        HStack(spacing: Aero.Spacing.s) {
            ForEach(["Editor", "Output", "Diff"], id: \.self) { tab in
                AeroChip(text: tab, selected: selectedTab == tab) {
                    GSHaptics.select()
                    withAnimation(Aero.snappy) { selectedTab = tab }
                }
            }
            Spacer()
        }
    }

    @ViewBuilder
    private var contentSection: some View {
        switch selectedTab {
        case "Output": outputView
        case "Diff": diffView
        default: editorView
        }
    }

    // MARK: Editor

    private var editorView: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            HStack {
                Text(currentFile.name)
                    .font(Aero.label())
                    .foregroundStyle(inkMuted)
                Spacer()
                Text("Kotlin")
                    .font(Aero.label())
                    .foregroundStyle(inkMuted)
            }
            VStack(alignment: .leading, spacing: 5) {
                ForEach(Array(currentFile.lines.enumerated()), id: \.offset) { index, line in
                    HStack(alignment: .firstTextBaseline, spacing: 12) {
                        Text("\(index + 1)")
                            .font(Aero.responsive(12, relativeTo: .caption, design: .monospaced))
                            .foregroundStyle(inkMuted)
                            .frame(width: 22, alignment: .trailing)
                        coloredLine(line)
                            .font(Aero.responsive(13, relativeTo: .footnote, design: .monospaced))
                    }
                }
            }
        }
        .padding(Aero.Spacing.m)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 16).fill(inkSurface))
    }

    /// Simple syntax colouring: keywords accent, strings soft sky, comments
    /// muted, everything else ink.
    private func coloredLine(_ line: String) -> Text {
        guard !line.isEmpty else { return Text(" ") }
        if line.trimmingCharacters(in: .whitespaces).hasPrefix("//") {
            return Text(line).foregroundColor(inkMuted)
        }
        var output = Text("")
        var word = ""
        var inString = false

        func flushWord() {
            guard !word.isEmpty else { return }
            let color: Color = keywords.contains(word) ? Aero.accent : inkText
            output = output + Text(word).foregroundColor(color)
            word = ""
        }

        for character in line {
            if character == "\"" {
                flushWord()
                inString.toggle()
                output = output + Text(String(character)).foregroundColor(inkString)
            } else if inString {
                output = output + Text(String(character)).foregroundColor(inkString)
            } else if character.isLetter || character.isNumber || character == "_" {
                word.append(character)
            } else {
                flushWord()
                output = output + Text(String(character)).foregroundColor(inkText)
            }
        }
        flushWord()
        return output
    }

    // MARK: Output (Run = 0.8s aurora build, then console lines)

    private var outputView: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            Button {
                build()
            } label: {
                HStack(spacing: Aero.Spacing.s) {
                    Image(systemName: "play.fill")
                    Text("Run")
                }
                .font(Aero.title())
                .frame(maxWidth: .infinity)
                .padding(14)
                .background(RoundedRectangle(cornerRadius: Aero.Radius.card).fill(Aero.accent))
                .foregroundStyle(Color.white)
            }
            .buttonStyle(KineticPressStyle())
            .disabled(isBuilding)
            .opacity(isBuilding ? 0.6 : 1)
            consoleCard
        }
    }

    private var consoleCard: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            if isBuilding {
                HStack(spacing: Aero.Spacing.s) {
                    AuroraIndicator()
                    Text("Building Main.kt…")
                        .font(Aero.responsive(13, relativeTo: .footnote, design: .monospaced))
                        .foregroundColor(inkMuted)
                }
            } else if buildDone {
                Text("> Compiling Main.kt")
                    .font(Aero.responsive(13, relativeTo: .footnote, design: .monospaced))
                    .foregroundColor(inkMuted)
                Text("✓ Build succeeded in 1.2s")
                    .font(Aero.responsive(13, relativeTo: .footnote, design: .monospaced))
                    .foregroundColor(Aero.accent)
                Text("Hello, Aeruo!")
                    .font(Aero.responsive(13, relativeTo: .footnote, design: .monospaced))
                    .foregroundColor(inkText)
            } else {
                Text("> Press Run to compile Main.kt")
                    .font(Aero.responsive(13, relativeTo: .footnote, design: .monospaced))
                    .foregroundColor(inkMuted)
            }
        }
        .padding(Aero.Spacing.m)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 16).fill(inkSurface))
    }

    private func build() {
        guard !isBuilding else { return }
        GSHaptics.tap()   // committed — the run moment begins
        isBuilding = true
        buildDone = false
        Task {
            try? await Task.sleep(nanoseconds: 800_000_000)
            isBuilding = false
            buildDone = true
        }
    }

    // MARK: Diff

    private var diffView: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            VStack(alignment: .leading, spacing: 5) {
                ForEach(diffLines, id: \.self) { line in
                    Text(line)
                        .font(Aero.responsive(13, relativeTo: .footnote, design: .monospaced))
                        .foregroundColor(diffColor(for: line))
                }
            }
            .padding(Aero.Spacing.m)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 16).fill(inkSurface))
            Text("engine.patch · 2 additions · 2 removals")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
        }
    }

    private func diffColor(for line: String) -> Color {
        if line.hasPrefix("+") { return Aero.accent }
        if line.hasPrefix("-") { return Color(red: 0.9, green: 0.28, blue: 0.28) }
        return inkMuted
    }
}
