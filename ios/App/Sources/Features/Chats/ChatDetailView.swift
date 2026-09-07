import SwiftUI
import UIKit

/// One conversation — streaming transcript, stop button, starter chips for
/// empty chats, per-message copy/regenerate/share. Backed by ChatViewModel,
/// which owns conversation creation and the SSE stream lifecycle.
struct ChatDetailView: View {

    @StateObject private var vm: ChatViewModel

    // Voice press-and-hold handoff — seeds the composer once on arrival.
    private let prefill: String?

    // Attach + local toasts (added 8-d; streaming/VM logic untouched)
    @State private var showingAttachments = false
    @State private var toast: String?

    // Reading protection — true while the reader has scrolled away from the
    // live edge; streaming deltas never yank the transcript back down.
    @State private var userIsReading = false
    @State private var editingIndex: Int?
    @State private var editDraft = ""

    // Find-in-chat — query, hit list, active hit (mirrors the Android bar).
    @State private var searchActive = false
    @State private var searchQuery = ""
    @State private var searchIndex = 0

    // Read-aloud — on-device speech, silent when the device has no voice.
    @StateObject private var speech = SpeechPlayer()

    init(conversationID: String?, prefill: String? = nil) {
        _vm = StateObject(wrappedValue: ChatViewModel(conversationID: conversationID))
        self.prefill = prefill
    }

    var body: some View {
        VStack(spacing: 0) {
            if let error = vm.errorMessage {
                ErrorStateView(message: error, retry: {
                    vm.errorMessage = nil
                    vm.retry()
                })
                .padding(.horizontal, Aero.Spacing.m)
                .padding(.top, Aero.Spacing.s)
            }

            transcript

            if vm.isStreaming {
                streamingBar
            }

            Divider().overlay(Aero.outline)
            attachRow
            inputBar
        }
        .background(Aero.background.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    searchActive.toggle()
                    if !searchActive {
                        searchQuery = ""
                        searchIndex = 0
                    }
                } label: {
                    Image(systemName: "magnifyingglass")
                }
            }
        }
        .onDisappear {
            speech.stop()
            // Park the unsent draft with the conversation — it comes back
            // when the thread reopens. Blank text just clears the slot.
            if let id = vm.conversationID {
                if vm.draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    UserDefaults.standard.removeObject(forKey: "draft_\(id)")
                } else {
                    UserDefaults.standard.set(vm.draft, forKey: "draft_\(id)")
                }
            }
        }
        .onAppear {
            if let prefill, vm.draft.isEmpty, !vm.isStreaming {
                vm.draft = prefill
            } else if let id = vm.conversationID, vm.draft.isEmpty, !vm.isStreaming {
                vm.draft = UserDefaults.standard.string(forKey: "draft_\(id)") ?? ""
            }
        }
        .sheet(isPresented: $showingAttachments) {
            AttachmentSheetView { option in
                showToast(attachmentMessage(for: option))
            }
        }
    }

    // MARK: Transcript

    private var transcript: some View {
        ScrollViewReader { proxy in
            VStack(spacing: 0) {
                if searchActive {
                    searchBar(proxy)
                        .transition(.opacity)
                }
                ScrollView {
                LazyVStack(spacing: 12) {
                    if vm.isLoadingHistory {
                        LoadingView(label: "Catching up")
                            .padding(.top, Aero.Spacing.l)
                    }

                    if vm.messages.isEmpty && !vm.isStreaming && !vm.isLoadingHistory {
                        emptyState
                            .padding(.top, Aero.Spacing.l)
                    }

                    ForEach(Array(vm.messages.enumerated()), id: \.element.id) { index, message in
                        if let stamp = dayLabel(message.createdAt),
                           index == 0 || dayKey(vm.messages[index - 1].createdAt) != dayKey(message.createdAt) {
                            DaySeparator(label: stamp)
                        }
                        if editingIndex == index {
                            editEditor
                        } else {
                            MessageBubble(
                                message: message,
                                isSpeaking: speech.speakingMessageID == message.id,
                                editEnabled: !vm.isStreaming && editingIndex == nil,
                                highlight: message.id == activeMatchID,
                                onRegenerate: {
                                    userIsReading = false
                                    vm.regenerate()
                                },
                                onReadAloud: { speech.toggle(messageID: message.id, text: message.content) },
                                onTranslate: { showToast("Translation arrives with the language pack build") },
                                onSave: { showToast("Saved to Library") },
                                onEditStart: {
                                    editDraft = message.content
                                    editingIndex = index
                                }
                            )
                        }
                        .id(message.id)
                    }
                }
                .padding(.horizontal, Aero.Spacing.m)
                .padding(.vertical, Aero.Spacing.m)
            }
            .simultaneousGesture(
                DragGesture(minimumDistance: 3).onChanged { _ in
                    userIsReading = true
                }
            )
            .scrollDismissesKeyboard(.immediately)
            .onChange(of: vm.messages.last?.content) { _ in
                guard !userIsReading else { return }
                withAnimation(Aero.gentle) {
                    if let last = vm.messages.last {
                        proxy.scrollTo(last.id, anchor: .bottom)
                    }
                }
            }
            .overlay(alignment: .bottomTrailing) {
                if userIsReading && !vm.messages.isEmpty {
                    Button {
                        userIsReading = false
                        withAnimation(Aero.gentle) {
                            if let last = vm.messages.last {
                                proxy.scrollTo(last.id, anchor: .bottom)
                            }
                        }
                    } label: {
                        Image(systemName: "arrow.down")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Aero.text)
                            .frame(width: 36, height: 36)
                            .background(Circle().fill(Aero.surface))
                            .overlay(Circle().stroke(Aero.outline, lineWidth: 1))
                            .shadow(color: Color.black.opacity(0.18), radius: 8, y: 2)
                    }
                    .buttonStyle(KineticPressStyle())
                    .padding(.trailing, Aero.Spacing.m)
                    .padding(.bottom, Aero.Spacing.m)
                    .transition(.opacity)
                }
            }
            }
        }
    }

    // MARK: Streaming controls

    private var streamingBar: some View {
        HStack(spacing: Aero.Spacing.s) {
            AuroraIndicator()
            Spacer()
            Button {
                vm.stop()
            } label: {
                Label("Stop generating", systemImage: "stop.fill")
                    .font(Aero.label())
                    .foregroundStyle(Aero.text)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .overlay(Capsule().stroke(Aero.accent, lineWidth: 1))
            }
            .buttonStyle(KineticPressStyle())
        }
        .padding(.horizontal, Aero.Spacing.m)
        .padding(.vertical, Aero.Spacing.s)
    }

    // MARK: Input

    private var inputBar: some View {
        AeroInputBar(text: $vm.draft, action: {
            userIsReading = false
            sendAndClearDraft()
        })
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.vertical, Aero.Spacing.s)
            .background(Aero.surface.ignoresSafeArea(edges: .bottom))
    }

    // MARK: Attach row (sits above the input bar — outside the frozen AeroInputBar)

    private var attachRow: some View {
        HStack(spacing: Aero.Spacing.s) {
            Button {
                showingAttachments = true
            } label: {
                Image(systemName: "plus")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Aero.text)
                    .frame(width: 30, height: 30)
                    .background(Circle().fill(Aero.container))
                    .overlay(Circle().stroke(Aero.outline, lineWidth: 1))
            }
            .buttonStyle(KineticPressStyle())
            if let message = toast {
                Image(systemName: "checkmark.circle")
                    .font(.system(size: 12))
                    .foregroundStyle(Aero.accent)
                Text(message)
                    .font(Aero.label())
                    .foregroundStyle(Aero.textMuted)
                    .lineLimit(1)
            }
            Spacer()
        }
        .padding(.horizontal, Aero.Spacing.m)
        .padding(.top, Aero.Spacing.s)
    }

    private func attachmentMessage(for option: String) -> String {
        switch option {
        case "Camera": return "Camera capture arrives with device builds"
        case "Gallery": return "Gallery import arrives with device builds"
        case "Files": return "File import arrives with the files build"
        case "Document": return "Document upload arrives with the files build"
        case "Code": return "Code attachments arrive with the repo build"
        case "Prompt template": return "Prompt templates arrive with the library build"
        default: return "Attachment support lands with the next build"
        }
    }

    private func showToast(_ message: String) {
        toast = message
        Task {
            try? await Task.sleep(nanoseconds: 1_800_000_000)
            if toast == message {
                toast = nil
            }
        }
    }

    /// Sends and, when the composer actually empties, drops the parked draft.
    private func sendAndClearDraft() {
        let pendingID = vm.conversationID
        vm.send()
        if vm.draft.isEmpty, let pendingID {
            UserDefaults.standard.removeObject(forKey: "draft_\(pendingID)")
        }
    }

    // MARK: Find in chat

    /// Indices of turns whose text contains the query, case-insensitive.
    private var searchMatches: [Int] {
        let query = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        guard searchActive, !query.isEmpty else { return [] }
        return vm.messages.indices.filter {
            vm.messages[$0].content.localizedCaseInsensitiveContains(query)
        }
    }

    /// The message id under the active hit — bubbles tint when they match.
    private var activeMatchID: String? {
        let matches = searchMatches
        guard !matches.isEmpty else { return nil }
        let wrapped = ((searchIndex % matches.count) + matches.count) % matches.count
        return vm.messages[matches[wrapped]].id
    }

    private var matchCountLabel: String {
        let matches = searchMatches
        let query = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        if matches.isEmpty { return query.isEmpty ? "" : "No results" }
        let wrapped = ((searchIndex % matches.count) + matches.count) % matches.count
        return "\(wrapped + 1)/\(matches.count)"
    }

    private func stepSearch(_ direction: Int, proxy: ScrollViewProxy) {
        let matches = searchMatches
        guard !matches.isEmpty else { return }
        searchIndex = ((searchIndex + direction) % matches.count + matches.count) % matches.count
        withAnimation(Aero.gentle) {
            proxy.scrollTo(vm.messages[matches[searchIndex]].id, anchor: .center)
        }
    }

    private func searchBar(_ proxy: ScrollViewProxy) -> some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 13))
                .foregroundStyle(Aero.textMuted)
            TextField("Search in chat", text: $searchQuery)
                .font(Aero.body())
                .foregroundStyle(Aero.text)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
                .submitLabel(.search)
                .onSubmit { stepSearch(1, proxy: proxy) }
                .onChange(of: searchQuery) { _ in
                    let matches = searchMatches
                    guard !matches.isEmpty else { return }
                    searchIndex = 0
                    withAnimation(Aero.gentle) {
                        proxy.scrollTo(vm.messages[matches[0]].id, anchor: .center)
                    }
                }
            if !matchCountLabel.isEmpty {
                Text(matchCountLabel)
                    .font(Aero.label())
                    .foregroundStyle(Aero.textMuted)
            }
            Button {
                stepSearch(-1, proxy: proxy)
            } label: {
                Image(systemName: "chevron.up")
            }
            .buttonStyle(KineticPressStyle())
            .disabled(searchMatches.isEmpty)
            Button {
                stepSearch(1, proxy: proxy)
            } label: {
                Image(systemName: "chevron.down")
            }
            .buttonStyle(KineticPressStyle())
            .disabled(searchMatches.isEmpty)
            Button {
                searchActive = false
                searchQuery = ""
                searchIndex = 0
            } label: {
                Image(systemName: "xmark")
            }
            .buttonStyle(KineticPressStyle())
        }
        .padding(.horizontal, Aero.Spacing.m)
        .padding(.vertical, Aero.Spacing.s)
        .background(Aero.surface)
    }

    // MARK: Empty state

    private let starters = ["Draft a launch plan", "Explain quantum computing", "Plan a Kyoto itinerary"]

    /// Inline edit surface for a sent user turn (benchmark pencil flow):
    /// Cancel returns the bubble untouched; Save & resend truncates the tail
    /// and streams a fresh reply.
    private var editEditor: some View {
        VStack(alignment: .trailing, spacing: 8) {
            TextField("Edit message", text: $editDraft, axis: .vertical)
                .font(Aero.body())
                .foregroundStyle(Aero.text)
                .padding(12)
                .background(RoundedRectangle(cornerRadius: 14).fill(Aero.container))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Aero.outline, lineWidth: 1))
            HStack(spacing: 12) {
                Button("Cancel") {
                    editingIndex = nil
                    editDraft = ""
                }
                .foregroundStyle(Aero.textMuted)
                Button("Save & resend") {
                    let target = editingIndex
                    let text = editDraft
                    editingIndex = nil
                    editDraft = ""
                    if let target {
                        userIsReading = false
                        vm.editAndResend(at: target, newText: text)
                    }
                }
                .disabled(editDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                .foregroundStyle(Aero.accent)
            }
            .font(.system(size: 14, weight: .medium))
            .buttonStyle(KineticPressStyle())
        }
    }

    private var emptyState: some View {
        VStack(spacing: Aero.Spacing.l) {
            EmptyStateView(
                icon: "sparkles",
                title: "New conversation",
                message: "Ask anything — GS is listening."
            )
            VStack(spacing: Aero.Spacing.s) {
                ForEach(starters, id: \.self) { starter in
                    AeroChip(text: starter) {
                        vm.draft = starter
                        sendAndClearDraft()
                    }
                }
            }
        }
    }
}

// MARK: - Reply content segmentation (text + fenced code blocks)

/// One renderable chunk of an assistant reply: plain text or a fenced code block.
private struct ContentSegment: Identifiable {
    let id: Int
    let text: String
    let isCode: Bool
    let language: String?
}

/// Split on ``` fences; an unterminated trailing fence (mid-stream) still renders as code.
private func parseContentSegments(_ content: String) -> [ContentSegment] {
    var segments: [ContentSegment] = []
    var id = 0
    let ns = content as NSString
    guard let regex = try? NSRegularExpression(pattern: "```(\\w*)\\n?([\\s\\S]*?)```") else {
        return [ContentSegment(id: 0, text: content, isCode: false, language: nil)]
    }
    var cursor = 0
    for match in regex.matches(in: content, range: NSRange(location: 0, length: ns.length)) {
        if match.range.location > cursor {
            segments.append(ContentSegment(
                id: id,
                text: ns.substring(with: NSRange(location: cursor, length: match.range.location - cursor)),
                isCode: false,
                language: nil
            ))
            id += 1
        }
        let language = match.range(at: 1).length > 0 ? ns.substring(with: match.range(at: 1)) : nil
        let bodyRange = match.range(at: 2)
        let body = bodyRange.length > 0
            ? ns.substring(with: bodyRange).trimmingCharacters(in: CharacterSet(charactersIn: "\n"))
            : ""
        segments.append(ContentSegment(id: id, text: body, isCode: true, language: language))
        id += 1
        cursor = match.range.location + match.range.length
    }
    if cursor < ns.length {
        let tail = ns.substring(from: cursor)
        if let openRange = tail.range(of: "```") {
            let before = String(tail[..<openRange.lowerBound])
            if !before.isEmpty {
                segments.append(ContentSegment(id: id, text: before, isCode: false, language: nil))
                id += 1
            }
            let rest = String(tail[openRange.upperBound...])
            var language: String? = nil
            var body = rest
            if let newline = rest.firstIndex(of: "\n") {
                let candidate = String(rest[..<newline]).trimmingCharacters(in: .whitespaces)
                language = candidate.isEmpty ? nil : candidate
                body = String(rest[rest.index(after: newline)...])
            }
            segments.append(ContentSegment(id: id, text: body, isCode: true, language: language))
            id += 1
        } else if !tail.isEmpty {
            segments.append(ContentSegment(id: id, text: tail, isCode: false, language: nil))
            id += 1
        }
    }
    if segments.isEmpty {
        segments.append(ContentSegment(id: 0, text: content, isCode: false, language: nil))
    }
    return segments
}

/// Languages whose line comments start with '#' rather than '//'.
private let hashCommentLanguages: Set<String> = ["python", "py", "bash", "sh", "shell", "ruby", "rb", "yaml", "yml", "toml"]

/// Words tinted in code blocks — a deliberately small cross-language set.
private let codeKeywords: Set<String> = [
    "val", "var", "fun", "func", "function", "def", "class", "struct", "enum", "interface",
    "object", "trait", "impl", "type", "if", "else", "elif", "for", "while", "switch", "case",
    "match", "when", "break", "continue", "return", "yield", "import", "from", "package",
    "public", "private", "protected", "static", "final", "const", "new", "this", "self",
    "super", "null", "nil", "none", "true", "false", "try", "catch", "finally", "throw",
    "throws", "await", "async", "let", "in", "is", "as", "of", "do", "end", "override",
    "open", "suspend", "data", "where", "with", "lambda", "and", "or", "not"
]

/// Lightweight syntax colouring — comments, strings, numbers, keywords.
/// Purely cosmetic: an unknown token stays plain, nothing can break the layout.
private func highlightedCode(_ code: String, language: String?) -> Text {
    let dark = UITraitCollection.current.userInterfaceStyle == .dark
    let kw = dark ? UIColor(red: 0.78, green: 0.57, blue: 0.92, alpha: 1) : UIColor(red: 0.42, green: 0.25, blue: 0.88, alpha: 1)
    let st = dark ? UIColor(red: 0.76, green: 0.91, blue: 0.55, alpha: 1) : UIColor(red: 0.18, green: 0.49, blue: 0.20, alpha: 1)
    let cm = dark ? UIColor(red: 0.49, green: 0.55, blue: 0.60, alpha: 1) : UIColor(red: 0.42, green: 0.49, blue: 0.55, alpha: 1)
    let nm = dark ? UIColor(red: 0.97, green: 0.55, blue: 0.42, alpha: 1) : UIColor(red: 0.85, green: 0.26, blue: 0.08, alpha: 1)
    let hashComments = hashCommentLanguages.contains((language ?? "").lowercased())

    let result = NSMutableAttributedString(string: "")
    let ns = code as NSString
    let pattern = "(//[^\\n]*|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|\\b\\d+(?:\\.\\d+)?\\b|[A-Za-z_][A-Za-z0-9_]*)"
    guard let regex = try? NSRegularExpression(pattern: pattern) else { return Text(code) }
    var cursor = 0
    for match in regex.matches(in: code, range: NSRange(location: 0, length: ns.length)) {
        if match.range.location > cursor {
            result.append(NSAttributedString(string: ns.substring(with: NSRange(location: cursor, length: match.range.location - cursor))))
        }
        let token = ns.substring(with: match.range)
        let color: UIColor? = {
            if token.hasPrefix("//") || (hashComments && token.hasPrefix("#")) { return cm }
            if token.hasPrefix("\"") || token.hasPrefix("'") { return st }
            if let first = token.first, first.isNumber { return nm }
            if codeKeywords.contains(token) { return kw }
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

// MARK: - Markdown-lite prose (headings, lists, bold/italic/inline code)

private enum ProseKind { case plain, h1, h2, h3, bullet, numbered }

private struct ProseLine {
    let kind: ProseKind
    let marker: String
    let text: String
}

private let numberedLineRegex = try! NSRegularExpression(pattern: "^(\\d{1,2})[.)]\\s+(.+)$")

/// Inline pass: `code` | **bold** | *italic* — unclosed markers stay literal mid-stream.
private let inlineMdRegex = try! NSRegularExpression(
    pattern: "`([^`\\n]+)`|\\*\\*([^*\\n]+?)\\*\\*|(?<!\\*)\\*([^*\\n]+?)\\*(?!\\*)")

/// Classify one markdown-lite line; blank lines drop (spacing handles rhythm).
private func classifyProseLine(_ raw: String) -> ProseLine? {
    let line = raw.replacingOccurrences(of: "\\s+$", with: "", options: .regularExpression)
    if line.isEmpty { return nil }
    let ns = line as NSString
    if line.hasPrefix("### ") { return ProseLine(kind: .h3, marker: "", text: String(line.dropFirst(4))) }
    if line.hasPrefix("## ") { return ProseLine(kind: .h2, marker: "", text: String(line.dropFirst(3))) }
    if line.hasPrefix("# ") { return ProseLine(kind: .h1, marker: "", text: String(line.dropFirst(2))) }
    if line.hasPrefix("- ") || line.hasPrefix("* ") {
        return ProseLine(kind: .bullet, marker: "\u{2022}", text: String(line.dropFirst(2)))
    }
    let numbered = numberedLineRegex.firstMatch(
        in: line, range: NSRange(location: 0, length: ns.length))
    if let m = numbered, m.range.length == ns.length {
        let marker = ns.substring(with: m.range(at: 1))
        return ProseLine(kind: .numbered, marker: marker + ".", text: ns.substring(with: m.range(at: 2)))
    }
    return ProseLine(kind: .plain, marker: "", text: line)
}

/// Inline markdown → AttributedString; unmatched markers render literally, so streaming never flickers.
private func renderInline(_ text: String, monoBackground: Color) -> AttributedString {
    var result = AttributedString()
    let ns = text as NSString
    var cursor = 0
    for match in inlineMdRegex.matches(in: text, range: NSRange(location: 0, length: ns.length)) {
        if match.range.location > cursor {
            result.append(AttributedString(
                ns.substring(with: NSRange(location: cursor, length: match.range.location - cursor))))
        }
        if match.range(at: 1).length > 0 {
            var run = AttributedString(ns.substring(with: match.range(at: 1)))
            run.font = .system(size: 12, weight: .regular, design: .monospaced)
            run.backgroundColor = monoBackground
            result.append(run)
        } else if match.range(at: 2).length > 0 {
            var run = AttributedString(ns.substring(with: match.range(at: 2)))
            run.inlinePresentationIntent = .stronglyEmphasized
            result.append(run)
        } else if match.range(at: 3).length > 0 {
            var run = AttributedString(ns.substring(with: match.range(at: 3)))
            run.inlinePresentationIntent = .emphasized
            result.append(run)
        }
        cursor = match.range.location + match.range.length
    }
    if cursor < ns.length {
        result.append(AttributedString(ns.substring(from: cursor)))
    }
    return result
}

/// Heading hierarchy inside bubbles; everything else rides the body voice.
private func proseFont(_ kind: ProseKind) -> Font {
    switch kind {
    case .h1: return .system(size: 16, weight: .bold)
    case .h2: return .system(size: 15, weight: .bold)
    case .h3: return .system(size: 14, weight: .bold)
    case .plain, .bullet, .numbered: return Aero.body()
    }
}

// MARK: - Bubble

private struct MessageBubble: View {

    let message: ChatViewModel.ChatMessage
    var isSpeaking: Bool = false
    var editEnabled: Bool = false
    var highlight: Bool = false
    var onRegenerate: () -> Void = {}
    var onReadAloud: () -> Void = {}
    var onTranslate: () -> Void = {}
    var onSave: () -> Void = {}
    var onEditStart: () -> Void = {}

    var body: some View {
        if message.role == "user" {
            userBubble
        } else {
            assistantBubble
        }
    }

    private var userBubble: some View {
        VStack(alignment: .trailing, spacing: 4) {
            HStack(alignment: .bottom, spacing: 0) {
                Spacer(minLength: 56)
                Text(message.content)
                    .font(Aero.body())
                    .foregroundStyle(Aero.text)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(RoundedRectangle(cornerRadius: 18).fill(Aero.accent.opacity(highlight ? 0.32 : 0.14)))
                    .frame(maxWidth: 280, alignment: .trailing)
            }
            HStack(spacing: 10) {
                Button {
                    UIPasteboard.general.string = message.content
                } label: {
                    Image(systemName: "doc.on.doc")
                }
                if editEnabled {
                    Button(action: onEditStart) {
                        Image(systemName: "pencil")
                    }
                }
            }
            .font(.system(size: 13))
            .foregroundStyle(Aero.textMuted)
            .buttonStyle(KineticPressStyle())
        }
    }

    private var assistantBubble: some View {
        HStack(alignment: .top, spacing: 0) {
            VStack(alignment: .leading, spacing: 8) {
                ForEach(parsedSegments) { segment in
                    if segment.isCode {
                        codeBlock(segment)
                    } else {
                        proseBlock(segment)
                    }
                }
                if message.isStreaming {
                    AuroraIndicator()
                } else if !message.content.isEmpty {
                    actionRow
                }
            }
            .padding(14)
            .background(RoundedRectangle(cornerRadius: 18).fill(Aero.surface))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(highlight ? Aero.accent : Aero.outline, lineWidth: highlight ? 2 : 1))
            .frame(maxWidth: 320, alignment: .leading)
            .contextMenu { bubbleMenu }
            Spacer(minLength: 40)
        }
    }

    private var parsedSegments: [ContentSegment] {
        if message.content.isEmpty {
            return [ContentSegment(id: 0, text: bubbleText, isCode: false, language: nil)]
        }
        return parseContentSegments(message.content)
    }

    /// One prose segment rendered as markdown-lite: headings, bullets, numbered
    /// lists, inline styling — mirror of the Android ProseBlock.
    private func proseBlock(_ segment: ContentSegment) -> some View {
        let lines = segment.text
            .components(separatedBy: "\n")
            .compactMap(classifyProseLine)
        return VStack(alignment: .leading, spacing: 4) {
            if lines.isEmpty {
                Text(" ")
                    .font(Aero.body())
            } else {
                ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        if !line.marker.isEmpty {
                            Text(line.marker)
                                .font(Aero.body())
                                .foregroundStyle(Aero.accent)
                        }
                        Text(renderInline(line.text, monoBackground: Aero.container))
                            .font(proseFont(line.kind))
                            .foregroundStyle(Aero.text)
                            .textSelection(.enabled)
                    }
                }
            }
        }
    }

    /// Fenced code block styled like the benchmark apps: language label, copy,
    /// monospaced body. An unterminated trailing fence (mid-stream) renders live.
    private func codeBlock(_ segment: ContentSegment) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(segment.language ?? "code")
                    .font(Aero.label())
                    .foregroundStyle(Aero.textMuted)
                Spacer()
                Button {
                    UIPasteboard.general.string = segment.text
                } label: {
                    Image(systemName: "doc.on.doc")
                }
                .buttonStyle(KineticPressStyle())
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            if segment.text.isEmpty {
                Text("…")
                    .font(.system(size: 12, weight: .regular, design: .monospaced))
                    .foregroundStyle(Aero.text)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12)
                    .padding(.bottom, 10)
            } else {
                highlightedCode(segment.text, language: segment.language)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12)
                    .padding(.bottom, 10)
            }
        }
        .background(RoundedRectangle(cornerRadius: 12).fill(Aero.container))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Aero.outline, lineWidth: 1))
    }

    private var bubbleText: String {
        (message.isStreaming && message.content.isEmpty) ? "…" : message.content
    }

    private var actionRow: some View {
        HStack(spacing: 18) {
            Button {
                UIPasteboard.general.string = message.content
            } label: {
                Image(systemName: "doc.on.doc")
            }
            Button(action: onRegenerate) {
                Image(systemName: "arrow.clockwise")
            }
            ShareLink(item: message.content) {
                Image(systemName: "square.and.arrow.up")
            }
            Button(action: onReadAloud) {
                Image(systemName: isSpeaking ? "stop.fill" : "speaker.wave.2")
                    .foregroundStyle(isSpeaking ? Aero.accent : Aero.textMuted)
            }
            Button(action: onTranslate) {
                Image(systemName: "translate")
            }
            Button(action: onSave) {
                Image(systemName: "bookmark")
            }
        }
        .font(.system(size: 13))
        .foregroundStyle(Aero.textMuted)
        .buttonStyle(KineticPressStyle())
    }

    private var bubbleMenu: some View {
        Group {
            Button {
                UIPasteboard.general.string = message.content
            } label: {
                Label("Copy", systemImage: "doc.on.doc")
            }
            Button(action: onRegenerate) {
                Label("Regenerate", systemImage: "arrow.clockwise")
            }
            ShareLink(item: message.content) {
                Label("Share", systemImage: "square.and.arrow.up")
            }
        }
    }
}

// MARK: Date separators

/// Centered day pill — the benchmark thread rhythm ("Today", "Yesterday", dates).
/// Rows without a parsable stamp (legacy data) simply show no header.
private struct DaySeparator: View {
    let label: String
    var body: some View {
        Text(label)
            .font(Aero.label())
            .foregroundStyle(Aero.textMuted)
            .padding(.horizontal, 12)
            .padding(.vertical, 4)
            .background(Capsule().fill(Aero.containerHigh))
            .frame(maxWidth: .infinity)
    }
}

private func parseISODate(_ iso: String) -> Date? {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    if let date = formatter.date(from: iso) { return date }
    formatter.formatOptions = [.withInternetDateTime]
    return formatter.date(from: iso)
}

private func dayKey(_ iso: String) -> String? {
    guard let date = parseISODate(iso) else { return nil }
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyy-MM-dd"
    return formatter.string(from: date)
}

private func dayLabel(_ iso: String) -> String? {
    guard let date = parseISODate(iso) else { return nil }
    let calendar = Calendar.current
    let today = calendar.startOfDay(for: Date())
    let day = calendar.startOfDay(for: date)
    if day == today { return "Today" }
    if day == calendar.date(byAdding: .day, value: -1, to: today) { return "Yesterday" }
    let formatter = DateFormatter()
    formatter.dateFormat = "d MMM yyyy"
    return formatter.string(from: date)
}
