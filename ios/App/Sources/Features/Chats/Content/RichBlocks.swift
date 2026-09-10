import SwiftUI

/// STEP 5 renderer (iOS twin of RichBlocks.kt) — the consumer half of the
/// message → blocks seam. iOS parses on FINALIZE only (the streaming bubble
/// stays plain text by design — deep-perf decision preserved), so block views
/// here never re-parse at stream cadence. Document-style: prose sits directly
/// on the canvas; chrome stays only on content that earns it (code, tables,
/// diagrams, citations, tool cards, images, collapsibles).

// MARK: - Entry

struct BlocksView: View {
    let blocks: [Block]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(blocks.enumerated()), id: \.offset) { index, block in
                BlockView(block: block, isLast: index == blocks.count - 1)
            }
        }
    }
}

@ViewBuilder
private func BlockView(block: Block, isLast: Bool) -> some View {
    switch block {
    case .paragraph(let spans, _):
        ParagraphView(spans: spans, showCaret: false)
    case .heading(let level, let spans, _):
        HeadingView(level: level, spans: spans, isFirst: false)
    case .bulletList(let items, _):
        BulletListView(items: items)
    case .orderedList(let start, let items, _):
        OrderedListView(start: start, items: items)
    case .blockQuote(let inner, _):
        BlockQuoteView(blocks: inner)
    case .codeBlock(let language, let code, let open, _):
        CodeBlockCardView(language: language, code: code, open: open)
    case .table(let headers, let aligns, let rows, _):
        TableBlockView(headers: headers, aligns: aligns, rows: rows)
    case .divider:
        DividerBlockView()
    case .mathBlock(let latex, _):
        MathBlockView(latex: latex)
    case .mermaid(let source, _):
        MermaidBlockView(source: source, streaming: false)
    case .image(let url, let alt, _):
        ImageBlockView(url: url, alt: alt)
    case .collapsible(let summary, let inner, _):
        CollapsibleBlockView(summary: summary, blocks: inner)
    case .citations(let citations, _):
        CitationsBlockView(citations: citations)
    case .toolResult(let entry, _):
        ToolResultBlockView(entry: entry)
    }
}

// MARK: - Inline spans → AttributedString

private struct SpanTheme {
    let monoBackground: Color
    let linkColor: Color
    let textColor: Color
    let baseSize: CGFloat
}

private func attributed(from spans: [InlineSpan], theme: SpanTheme) -> AttributedString {
    var result = AttributedString()
    append(spans: spans, into: &result, theme: theme)
    return result
}

private func append(spans: [InlineSpan], into result: inout AttributedString, theme: SpanTheme) {
    for span in spans {
        switch span {
        case .text(let text):
            result.append(AttributedString(text))
        case .bold(let children):
            var inner = attributed(from: children, theme: theme)
            inner.inlinePresentationIntent = .stronglyEmphasized
            result.append(inner)
        case .italic(let children):
            var inner = attributed(from: children, theme: theme)
            inner.inlinePresentationIntent = .emphasized
            result.append(inner)
        case .strike(let children):
            var inner = attributed(from: children, theme: theme)
            inner.inlinePresentationIntent = .strikethrough
            result.append(inner)
        case .code(let code):
            var run = AttributedString(code)
            run.font = .system(size: theme.baseSize * 0.88, weight: .regular, design: .monospaced)
            run.backgroundColor = theme.monoBackground
            result.append(run)
        case .link(let text, let url):
            if let url = URL(string: url) {
                var run = AttributedString(text)
                run.link = url
                run.underlineStyle = .single
                run.foregroundColor = theme.linkColor
                result.append(run)
            } else {
                result.append(AttributedString(text))
            }
        case .math(let latex):
            if let rendered = renderMathAttributedString(latex, baseSize: theme.baseSize, color: theme.textColor) {
                result.append(rendered)
            } else {
                result.append(AttributedString(latex))
            }
        }
    }
}

// MARK: - Paragraph & heading

private struct ParagraphView: View {
    let spans: [InlineSpan]
    var showCaret: Bool = false

    var body: some View {
        if !spans.isEmpty {
            let theme = SpanTheme(
                monoBackground: Aero.container,
                linkColor: Aero.accentDeep,
                textColor: Aero.text,
                baseSize: 15
            )
            Text(attributed(from: spans, theme: theme))
                .font(Aero.body())
                .foregroundStyle(Aero.text)
                .textSelection(.enabled)
        }
    }
}

private struct HeadingView: View {
    let level: Int
    let spans: [InlineSpan]
    var isFirst: Bool = false

    var body: some View {
        let theme = SpanTheme(
            monoBackground: Aero.container,
            linkColor: Aero.accentDeep,
            textColor: Aero.text,
            baseSize: 15
        )
        VStack(alignment: .leading, spacing: 0) {
            if !isFirst && level <= 3 {
                Spacer().frame(height: 8)
            }
            Text(attributed(from: spans, theme: theme))
                .font(font)
                .foregroundStyle(level == 6 ? Aero.textSecondary : Aero.text)
                .textSelection(.enabled)
                .accessibilityAddTraits(.isHeader)
        }
    }

    private var font: Font {
        switch level {
        case 1: return Aero.responsive(22, .bold, relativeTo: .title2)
        case 2: return Aero.responsive(19, .bold, relativeTo: .title3)
        case 3: return Aero.responsive(17, .bold, relativeTo: .headline)
        case 4: return Aero.responsive(16, .bold, relativeTo: .body)
        case 5: return Aero.responsive(15, .bold, relativeTo: .body)
        default: return Aero.responsive(15, .semibold, relativeTo: .body)
        }
    }
}

// MARK: - Lists

private struct BulletListView: View {
    let items: [ListItem]

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                ListItemRowView(marker: "\u{2022}", markerColor: Aero.accent, item: item)
            }
        }
    }
}

private struct OrderedListView: View {
    let start: Int
    let items: [ListItem]

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                let number = item.number ?? (start + index)
                ListItemRowView(marker: "\(number).", markerColor: Aero.textTertiary, item: item)
            }
        }
    }
}

private struct ListItemRowView: View {
    let marker: String
    let markerColor: Color
    let item: ListItem

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(marker)
                    .font(Aero.body())
                    .foregroundStyle(markerColor)
                ParagraphView(spans: item.spans)
            }
            if !item.children.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(Array(item.children.enumerated()), id: \.offset) { _, child in
                        NestedChildView(block: child)
                    }
                }
                .padding(.leading, 16)
            }
        }
    }
}

@ViewBuilder
private func NestedChildView(block: Block) -> some View {
    switch block {
    case .paragraph(let spans, _):
        ParagraphView(spans: spans)
    case .bulletList(let items, _):
        BulletListView(items: items)
    case .orderedList(let start, let items, _):
        OrderedListView(start: start, items: items)
    case .blockQuote(let inner, _):
        BlockQuoteView(blocks: inner)
    default:
        BlockView(block: block, isLast: false)
    }
}

// MARK: - Blockquote

private struct BlockQuoteView: View {
    let blocks: [Block]

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            RoundedRectangle(cornerRadius: 2)
                .fill(Aero.accent.opacity(0.55))
                .frame(width: 3)
            VStack(alignment: .leading, spacing: 6) {
                ForEach(Array(blocks.enumerated()), id: \.offset) { _, inner in
                    NestedChildView(block: inner)
                }
            }
        }
    }
}

// MARK: - Code block

struct CodeBlockCardView: View {
    let language: String?
    let code: String
    var open: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(language ?? "code")
                    .font(Aero.label())
                    .foregroundStyle(Aero.textMuted)
                Spacer()
                CodeCopyButton(text: code)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .accessibilityElement(children: .combine)
            .accessibilityLabel("Code block\(language.map { ", " + $0 } ?? "")")

            if code.isEmpty {
                Text("…")
                    .font(.system(size: 12, weight: .regular, design: .monospaced))
                    .foregroundStyle(Aero.text)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12)
                    .padding(.bottom, 10)
                    .textSelection(.enabled)
            } else {
                // Both axes scroll inside the card; large code never grows the
                // transcript item forever. Open (mid-stream) fences stay plain
                // monospace — one highlight pass on finalize.
                ScrollView([.horizontal, .vertical]) {
                    Group {
                        if open {
                            Text(code)
                                .font(.system(size: 12, weight: .regular, design: .monospaced))
                                .foregroundStyle(Aero.text)
                        } else {
                            highlightedCode(code, language: language)
                                .foregroundStyle(Aero.text)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12)
                    .padding(.bottom, 10)
                    .textSelection(.enabled)
                }
                .frame(maxHeight: 340)
            }
        }
        .background(RoundedRectangle(cornerRadius: 12).fill(Aero.container))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Aero.outline, lineWidth: 1))
    }
}

/// Code-header copy with its own checkmark confirmation (moved from
/// ChatDetailView — same behaviour, now shared by code and diagram cards).
struct CodeCopyButton: View {
    let text: String
    @State private var copied = false

    var body: some View {
        Button {
            UIPasteboard.general.string = text
            GSHaptics.success()
            withAnimation(.easeOut(duration: 0.15)) { copied = true }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) {
                withAnimation(.easeIn(duration: 0.2)) { copied = false }
            }
        } label: {
            Image(systemName: copied ? "checkmark" : "doc.on.doc")
                .foregroundStyle(copied ? Aero.accent : Aero.textMuted)
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityLabel(copied ? "Copied" : "Copy code")
    }
}

// MARK: - Table

private struct TableBlockView: View {
    let headers: [[InlineSpan]]
    let aligns: [TableAlign]
    let rows: [[[InlineSpan]]]

    /// Available width mirrors the transcript's reading column (16pt padding
    /// each side, content clamped to the Step-2 640pt column) — deterministic,
    /// no greedy GeometryReader inside the lazy transcript.
    private var availableWidth: CGFloat {
        min(UIScreen.main.bounds.width - 32, Aero.Layout.contentMaxWidth)
    }

    var body: some View {
        let columns = max(headers.count, aligns.count, 1)
        let minCell: CGFloat = 96
        let cellWidth = max(minCell, availableWidth / CGFloat(columns))
        let theme = SpanTheme(
            monoBackground: Aero.container,
            linkColor: Aero.accentDeep,
            textColor: Aero.text,
            baseSize: 13
        )
        Group {
            if CGFloat(columns) * cellWidth <= availableWidth {
                table(theme: theme, cellWidth: cellWidth, columns: columns, scrollable: false)
            } else {
                // Wide table: the TABLE region scrolls horizontally; the
                // conversation scroll is never hijacked.
                ScrollView(.horizontal, showsIndicators: false) {
                    table(theme: theme, cellWidth: cellWidth, columns: columns, scrollable: true)
                }
            }
        }
    }

    @ViewBuilder
    private func table(theme: SpanTheme, cellWidth: CGFloat, columns: Int, scrollable: Bool) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 0) {
                ForEach(0..<columns, id: \.self) { c in
                    cell(
                        attributed(from: c < headers.count ? headers[c] : [], theme: theme),
                        width: cellWidth,
                        align: c < aligns.count ? aligns[c] : .left,
                        emphasize: true
                    )
                }
            }
            Rectangle().fill(Aero.outline.opacity(0.5)).frame(height: 1)
            ForEach(Array(rows.enumerated()), id: \.offset) { r, row in
                HStack(spacing: 0) {
                    ForEach(0..<columns, id: \.self) { c in
                        cell(
                            attributed(from: c < row.count ? row[c] : [], theme: theme),
                            width: cellWidth,
                            align: c < aligns.count ? aligns[c] : .left,
                            emphasize: false
                        )
                    }
                }
                if r != rows.count - 1 {
                    Rectangle().fill(Aero.outline.opacity(0.25)).frame(height: 1)
                }
            }
        }
        .frame(width: scrollable ? cellWidth * CGFloat(columns) : nil, alignment: .leading)
        .textSelection(.enabled)
    }

    private func cell(_ text: AttributedString, width: CGFloat, align: TableAlign, emphasize: Bool) -> some View {
        Text(text)
            .font(emphasize ? Aero.label() : Aero.caption())
            .foregroundStyle(emphasize ? Aero.text : Aero.textSecondary)
            .frame(width: width, alignment: alignment(for: align))
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .multilineTextAlignment(alignment(for: align))
    }

    private func alignment(for align: TableAlign) -> TextAlignment {
        switch align {
        case .center: return .center
        case .right: return .trailing
        case .left: return .leading
        }
    }
}

// MARK: - Divider & math

private struct DividerBlockView: View {
    var body: some View {
        Rectangle()
            .fill(Aero.outline.opacity(0.6))
            .frame(height: 1)
            .padding(.vertical, 4)
    }
}

private struct MathBlockView: View {
    let latex: String

    var body: some View {
        Group {
            if let rendered = renderMathText(latex, baseSize: 16, color: Aero.text) {
                rendered
            } else {
                Text(latex)
            }
        }
        .font(Aero.responsive(16, relativeTo: .body))
        .foregroundStyle(Aero.text)
        .frame(maxWidth: .infinity, alignment: .center)
        .textSelection(.enabled)
    }
}

// MARK: - Image

private enum ImageLoadState {
    case loading
    case ready(UIImage)
    case failed
}

private struct ImageBlockView: View {
    let url: String
    let alt: String
    @State private var state: ImageLoadState = .loading
    @State private var viewerOpen = false

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            content
            metaRow
        }
    }

    @ViewBuilder
    private var content: some View {
        switch state {
        case .loading:
            RoundedRectangle(cornerRadius: 12)
                .fill(Aero.raisedSurface)
                .frame(height: 120)
                .overlay(ProgressView().scaleEffect(0.8))
        case .ready(let image):
            Image(uiImage: image)
                .resizable()
                .scaledToFit()
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .onTapGesture { viewerOpen = true }
                .accessibilityLabel(alt.isEmpty ? "Image" : alt)
        case .failed:
            // Honest failure: compact row, never a fake success.
            HStack(spacing: 8) {
                Image(systemName: "photo.badge.exclamationmark")
                    .foregroundStyle(Aero.textTertiary)
                VStack(alignment: .leading, spacing: 2) {
                    Text(alt.isEmpty ? "Image" : alt)
                        .font(Aero.bodyMedium())
                        .foregroundStyle(Aero.text)
                        .lineLimit(1)
                    Text("Couldn't load this image")
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textTertiary)
                }
                Spacer()
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(RoundedRectangle(cornerRadius: 12).fill(Aero.raisedSurface))
        }
    }

    private var metaRow: some View {
        HStack(spacing: 12) {
            Button("Open") {
                if let link = URL(string: url), ["https", "http"].contains(link.scheme?.lowercased() ?? "") {
                    UIApplication.shared.open(link)
                }
            }
            .font(Aero.responsive(13, relativeTo: .footnote))
            .foregroundStyle(Aero.textMuted)
            Button("Copy link") {
                UIPasteboard.general.string = url
            }
            .font(Aero.responsive(13, relativeTo: .footnote))
            .foregroundStyle(Aero.textMuted)
        }
        .buttonStyle(KineticPressStyle())
    }

    private func load() async {
        guard url.lowercased().hasPrefix("https://") else {
            state = .failed
            return
        }
        state = .loading
        var request = URLRequest(url: URL(string: url) ?? URL(string: "https://invalid.local")!)
        request.timeoutInterval = 8
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            if let http = response as? HTTPURLResponse, http.statusCode >= 400 {
                state = .failed
                return
            }
            guard data.count < 8 * 1024 * 1024, let image = UIImage(data: data) else {
                state = .failed
                return
            }
            state = .ready(image)
        } catch {
            state = .failed
        }
    }
}

// MARK: - Collapsible (<details> — never auto-collapsed prose)

private struct CollapsibleBlockView: View {
    let summary: String
    let blocks: [Block]
    @State private var expanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Button {
                withAnimation(.easeOut(duration: 0.2)) { expanded.toggle() }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Aero.accent)
                        .rotationEffect(.degrees(expanded ? 90 : 0))
                    Text(summary)
                        .font(Aero.label())
                        .foregroundStyle(Aero.text)
                    Spacer()
                }
                .padding(.vertical, 6)
                .contentShape(Rectangle())
            }
            .buttonStyle(KineticPressStyle())
            .accessibilityValue(expanded ? "Expanded" : "Collapsed")
            .accessibilityHint("Toggle detail section")

            if expanded {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(Array(blocks.enumerated()), id: \.offset) { _, inner in
                        BlockView(block: inner, isLast: false)
                    }
                }
                .padding(.leading, 24)
            }
        }
    }
}

// MARK: - Structured seams — citations & tool results (backend provides; never faked)

private struct CitationsBlockView: View {
    let citations: [CitationEntry]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Sources")
                .font(Aero.label())
                .foregroundStyle(Aero.textMuted)
            ForEach(Array(citations.enumerated()), id: \.offset) { _, citation in
                HStack(alignment: .top, spacing: 10) {
                    Text("\(citation.number)")
                        .font(Aero.caption())
                        .foregroundStyle(Aero.accent)
                        .frame(width: 22, height: 22)
                        .background(Circle().fill(Aero.accent.opacity(0.12)))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(citation.title)
                            .font(Aero.bodyMedium())
                            .foregroundStyle(Aero.text)
                        Text(citation.domain)
                            .font(Aero.caption())
                            .foregroundStyle(Aero.textTertiary)
                        if !citation.snippet.isEmpty {
                            Text(citation.snippet)
                                .font(Aero.caption())
                                .foregroundStyle(Aero.textTertiary)
                                .lineLimit(2)
                        }
                    }
                    Spacer(minLength: 0)
                }
                .accessibilityElement(children: .combine)
                .accessibilityLabel("Source \(citation.number). \(citation.title), \(citation.domain)")
            }
        }
    }
}

private struct ToolResultBlockView: View {
    let entry: ToolResultEntry
    @State private var expanded = false

    private var statusSymbol: String {
        switch entry.status {
        case "completed", "success", "done": return "checkmark.circle"
        case "failed", "error": return "xmark.circle"
        default: return entry.kind == "search" || entry.kind == "research" ? "magnifyingglass" : "gearshape"
        }
    }

    private var statusColor: Color {
        switch entry.status {
        case "completed", "success", "done": return Aero.success
        case "failed", "error": return Aero.danger
        default: return Aero.toolExecution
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Button {
                guard !entry.detail.isEmpty else { return }
                withAnimation(.easeOut(duration: 0.2)) { expanded.toggle() }
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: statusSymbol)
                        .font(.system(size: 14))
                        .foregroundStyle(statusColor)
                    Text(entry.title)
                        .font(Aero.bodyMedium())
                        .foregroundStyle(Aero.text)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text("\(entry.kind) · \(entry.status)")
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textTertiary)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .contentShape(Rectangle())
            }
            .buttonStyle(KineticPressStyle())
            if expanded && !entry.detail.isEmpty {
                Text(entry.detail)
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textSecondary)
                    .padding(.horizontal, 12)
                    .padding(.bottom, 10)
            }
        }
        .background(RoundedRectangle(cornerRadius: 12).fill(Aero.raisedSurface))
    }
}
