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
        .onDisappear {
            speech.stop()
        }
        .onAppear {
            if let prefill, vm.draft.isEmpty, !vm.isStreaming {
                vm.draft = prefill
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

                    ForEach(vm.messages) { message in
                        MessageBubble(
                            message: message,
                            isSpeaking: speech.speakingMessageID == message.id,
                            onRegenerate: {
                                userIsReading = false
                                vm.regenerate()
                            },
                            onReadAloud: { speech.toggle(messageID: message.id, text: message.content) },
                            onTranslate: { showToast("Translation arrives with the language pack build") },
                            onSave: { showToast("Saved to Library") }
                        )
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
            vm.send()
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

    // MARK: Empty state

    private let starters = ["Draft a launch plan", "Explain quantum computing", "Plan a Kyoto itinerary"]

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
                        vm.send()
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

// MARK: - Bubble

private struct MessageBubble: View {

    let message: ChatViewModel.ChatMessage
    var isSpeaking: Bool = false
    var onRegenerate: () -> Void = {}
    var onReadAloud: () -> Void = {}
    var onTranslate: () -> Void = {}
    var onSave: () -> Void = {}

    var body: some View {
        if message.role == "user" {
            userBubble
        } else {
            assistantBubble
        }
    }

    private var userBubble: some View {
        HStack(alignment: .bottom, spacing: 0) {
            Spacer(minLength: 56)
            Text(message.content)
                .font(Aero.body())
                .foregroundStyle(Aero.text)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(RoundedRectangle(cornerRadius: 18).fill(Aero.accent.opacity(0.14)))
                .frame(maxWidth: 280, alignment: .trailing)
        }
    }

    private var assistantBubble: some View {
        HStack(alignment: .top, spacing: 0) {
            VStack(alignment: .leading, spacing: 8) {
                ForEach(parsedSegments) { segment in
                    if segment.isCode {
                        codeBlock(segment)
                    } else {
                        Text(segment.text)
                            .font(Aero.body())
                            .foregroundStyle(Aero.text)
                            .textSelection(.enabled)
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
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Aero.outline, lineWidth: 1))
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
