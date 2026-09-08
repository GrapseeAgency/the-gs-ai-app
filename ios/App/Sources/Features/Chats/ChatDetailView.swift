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

    // Translate — the tapped turn streams its translation in a bottom sheet.
    @State private var translationCard: TranslationCard?
    @State private var translationText = ""
    @State private var translating = false

    // Reading protection — the reader is away from the live edge; streaming
    // deltas never yank the transcript back down. Position-truthful (design
    // parity with Android's derivedStateOf isAtBottom): a 1pt sentinel after
    // the last turn IS the position signal — it appears when the live edge
    // re-enters the viewport (re-engaging follow and hiding the jump button,
    // like ChatGPT/Claude/Kimi) and its absence, confirmed after a short
    // grace window so streaming flicker can't false-trigger, latches the
    // button on. drags schedule a fast check so a hairline pan at the live
    // edge never summons the button.
    @State private var userIsReading = false
    @State private var atBottom = true
    @State private var disengageWork: DispatchWorkItem?
    private let liveEdgeID = "gs-live-edge"
    @State private var editingIndex: Int?
    @State private var editDraft = ""

    // Scroll-up pagination (deep-perf pass #2b) — the anchor row captured the
    // instant the older-page sentinel appears; restored to the viewport top
    // right after the prepend, instantly, like Android's index-shift restore.
    @State private var olderAnchor: ChatViewModel.ChatMessage.ID?

    // Find-in-chat — query, hit list, active hit (mirrors the Android bar).
    @State private var searchActive = false
    @State private var searchQuery = ""
    @State private var searchIndex = 0

    // Read-aloud — on-device speech, silent when the device has no voice.
    @StateObject private var speech = SpeechPlayer()

    // Task 85-e: platform wiring.
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @FocusState private var composerFocused: Bool
    @ObservedObject private var conversations = ConversationStore.shared

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
        .navigationTitle(conversationTitle)
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
                .accessibilityLabel("Search in chat")
            }
        }
        .onDisappear {
            speech.stop()
            persistDraft()
        }
        .onChange(of: scenePhase) { phase in
            // Process death while composing loses the onDisappear save — the
            // same draft path also flushes when the scene leaves the foreground.
            if phase == .background {
                persistDraft()
            }
        }
        .onChange(of: vm.isStreaming) { streaming in
            // Falling edge only — one announcement per stream (Task 85-e).
            guard !streaming else { return }
            UIAccessibility.post(
                notification: .announcement,
                argument: vm.errorMessage == nil ? "Reply ready" : "Reply failed")
        }
        .onAppear {
            GSHaptics.prepare()
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
        .sheet(item: $translationCard) { card in
            TranslationSheet(
                source: card.source,
                translated: translationText,
                busy: translating,
                onCopy: { _ in showToast("Copied") }
            )
        }
    }

    /// Real Translate: streams the tapped turn into the sheet, targeted at the
    /// device language. Offline the sheet closes quietly with a soft note —
    /// no error surfaces, the thread stays untouched.
    private func beginTranslation(_ text: String) {
        guard !translating else { return }
        translationCard = TranslationCard(source: text)
        translationText = ""
        translating = true
        let language = Locale.current.localizedString(
            forLanguageCode: Locale.current.languageCode ?? "en") ?? "English"
        Task {
            let result = await vm.translate(text: text, targetLanguage: language) { delta in
                translationText += delta
            }
            translating = false
            if result.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                translationCard = nil
                showToast("Translation needs a connection")
            }
        }
    }

    // MARK: Transcript

    /// Latches "reader is away" only if the live edge is still gone when the
    /// check fires — a hairline pan at the bottom (sentinel never left) or the
    /// follow scroll re-materializing the sentinel cancels out before this
    /// runs, so the jump button obeys position like Android's, not gestures.
    private func scheduleDisengage(after seconds: Double) {
        guard !userIsReading else { return }
        disengageWork?.cancel()
        let work = DispatchWorkItem {
            if !atBottom { userIsReading = true }
        }
        disengageWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + seconds, execute: work)
    }

    private var transcript: some View {
        ScrollViewReader { proxy in
            VStack(spacing: 0) {
                if searchActive {
                    searchBar(proxy)
                        .transition(.opacity)
                }
                ScrollView {
                LazyVStack(spacing: 12) {
                    // One search-match resolution per body pass — read per-row
                    // it was O(visible rows × messages) contains() scans on
                    // every keystroke while find-in-chat is open.
                    let activeMatch = activeMatchID
                    // Scroll-up pagination sentinel: a 1pt row above the
                    // loaded window. Materializing near the top pulls one
                    // older page from the local store (armed by a real drag,
                    // so the bottom-landing on open never fires it). It
                    // re-arms by disappearing — each prepend pushes it a full
                    // page above the viewport.
                    if vm.hasOlder {
                        Color.clear
                            .frame(height: 1)
                            .onAppear {
                                olderAnchor = vm.messages.first?.id
                                vm.loadOlder()
                            }
                    }

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
                                highlight: message.id == activeMatch,
                                onRegenerate: {
                                    userIsReading = false
                                    vm.regenerate()
                                },
                                onReadAloud: { speech.toggle(messageID: message.id, text: message.content) },
                                onTranslate: { beginTranslation(message.content) },
                                onSave: {
                                    ConversationStore.shared.saveToLibrary(content: message.content)
                                    showToast("Saved to Library")
                                },
                                onEditStart: {
                                    editDraft = message.content
                                    editingIndex = index
                                },
                                onBranch: { vm.branch(at: index) }
                            )
                        }
                        .id(message.id)
                    }

                    // Live-edge sentinel: 1pt after the newest turn. Materialized
                    // ⇔ the reader is at the bottom — the iOS16-honest position
                    // signal (no scroll-offset API). Pinned by the follow scroll
                    // itself, so streaming never flickers it.
                    Color.clear
                        .frame(height: 1)
                        .id(liveEdgeID)
                        .onAppear {
                            atBottom = true
                            disengageWork?.cancel()
                            disengageWork = nil
                            userIsReading = false
                        }
                        .onDisappear {
                            atBottom = false
                            scheduleDisengage(after: 0.6)
                        }
                }
                .padding(.horizontal, Aero.Spacing.m)
                .padding(.vertical, Aero.Spacing.m)
                .frame(maxWidth: columnMaxWidth)
            }
            .simultaneousGesture(
                DragGesture(minimumDistance: 3).onChanged { _ in
                    vm.armOlderPages()
                    scheduleDisengage(after: 0.25)
                }
            )
            .scrollDismissesKeyboard(.interactively)
            .onChange(of: vm.messages.count) { _ in
                // Prepend restore: the captured anchor row (first visible when
                // the page was pulled) returns to the viewport top — instant,
                // because an animated glide would read as motion the user
                // never made. Appends leave olderAnchor nil and no-op.
                guard let anchor = olderAnchor,
                      vm.messages.contains(where: { $0.id == anchor }) else { return }
                olderAnchor = nil
                proxy.scrollTo(anchor, anchor: .top)
            }
            .onChange(of: vm.messages.last?.content) { _ in
                guard !userIsReading else { return }
                withAnimation(Aero.gentle) {
                    // Pin the sentinel, not the bubble: the 1pt row stays
                    // materialized at the viewport bottom, so atBottom holds
                    // steady and the jump button never flickers mid-stream.
                    proxy.scrollTo(liveEdgeID, anchor: .bottom)
                }
            }
            .onDisappear { disengageWork?.cancel() }
            .overlay(alignment: .bottomTrailing) {
                if userIsReading && !vm.messages.isEmpty {
                    Button {
                        userIsReading = false
                        withAnimation(Aero.gentle) {
                            proxy.scrollTo(liveEdgeID, anchor: .bottom)
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
        }, focus: $composerFocused)
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.vertical, Aero.Spacing.s)
            .frame(maxWidth: columnMaxWidth)
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
            .accessibilityLabel("Add attachment")
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
        .frame(maxWidth: columnMaxWidth)
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

    /// Park the unsent draft with the conversation — it comes back when the
    /// thread reopens. Blank text just clears the slot. Shared by onDisappear
    /// and the scenePhase background flush so both write identically.
    private func persistDraft() {
        if let id = vm.conversationID {
            if vm.draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                UserDefaults.standard.removeObject(forKey: "draft_\(id)")
            } else {
                UserDefaults.standard.set(vm.draft, forKey: "draft_\(id)")
            }
        }
    }

    /// Live nav-bar title — follows the store row as it appears/renames
    /// (auto-titled threads update while the reply streams).
    private var conversationTitle: String {
        gsConversationTitle(vm.conversationID.flatMap { conversations.conversation(withID: $0)?.title })
    }

    /// iPad / regular width: the transcript column and the composer row cap
    /// at one readable measure, centred. Compact (iPhone) is untouched.
    private var columnMaxWidth: CGFloat? {
        horizontalSizeClass == .regular ? 640 : nil
    }

    /// Sends and, when the composer actually empties, drops the parked draft.
    private func sendAndClearDraft() {
        // Committed send — the Taptic tick the system keyboard plays on keys.
        GSHaptics.tap()
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
                .gsKeyboardDoneBar()
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
            .accessibilityLabel("Previous match")
            Button {
                stepSearch(1, proxy: proxy)
            } label: {
                Image(systemName: "chevron.down")
            }
            .buttonStyle(KineticPressStyle())
            .disabled(searchMatches.isEmpty)
            .accessibilityLabel("Next match")
            Button {
                searchActive = false
                searchQuery = ""
                searchIndex = 0
            } label: {
                Image(systemName: "xmark")
            }
            .buttonStyle(KineticPressStyle())
            .accessibilityLabel("Close search")
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
            .font(Aero.responsive(14, .medium, relativeTo: .subheadline))
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
                        // Seed the composer and raise the keyboard — the reader
                        // edits, then sends through the gated send path.
                        vm.draft = starter
                        composerFocused = true
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

/// Inline pass: `code` | **bold** | *italic* | https:// links — unclosed
/// markers stay literal mid-stream, and plain gaps gain tappable URLs
/// (AttributedString `.link` is auto-tappable inside SwiftUI Text, iOS 15+).
private let inlineMdRegex = try! NSRegularExpression(
    pattern: "`([^`\\n]+)`|\\*\\*([^*\\n]+?)\\*\\*|(?<!\\*)\\*([^*\\n]+?)\\*(?!\\*)")

/// Bare http(s) URLs in prose — conservative host/body characters; trailing
/// sentence punctuation is trimmed by the renderer so links don't 404.
private let inlineURLRegex = try! NSRegularExpression(
    pattern: "https?://[^\\s<>()\"']+")

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
            appendPlainWithLinks(
                ns.substring(with: NSRange(location: cursor, length: match.range.location - cursor)),
                into: &result)
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
        appendPlainWithLinks(ns.substring(from: cursor), into: &result)
    }
    return result
}

/// One plain-text gap of the inline pass, with bare URLs turned into tappable
/// links (accent + underline + `.link` — SwiftUI Text opens them in Safari).
private func appendPlainWithLinks(_ raw: String, into result: inout AttributedString) {
    let ns = raw as NSString
    var cursor = 0
    for match in inlineURLRegex.matches(in: raw, range: NSRange(location: 0, length: ns.length)) {
        if match.range.location > cursor {
            result.append(AttributedString(
                ns.substring(with: NSRange(location: cursor, length: match.range.location - cursor))))
        }
        let token = ns.substring(with: match.range)
        var urlText = token
        while let last = urlText.last, ".;:,!?".contains(last) {
            urlText.removeLast()
        }
        if let url = URL(string: urlText) {
            var run = AttributedString(urlText)
            run.link = url
            run.underlineStyle = .single
            run.foregroundColor = Aero.accentDeep
            result.append(run)
            let tail = String(token.dropFirst(urlText.count))
            if !tail.isEmpty {
                result.append(AttributedString(tail))
            }
        } else {
            result.append(AttributedString(token))
        }
        cursor = match.range.location + match.range.length
    }
    if cursor < ns.length {
        result.append(AttributedString(ns.substring(from: cursor)))
    }
}

/// Heading hierarchy inside bubbles; everything else rides the body voice.
/// Dynamic Type (Task 85-e): headings scale with their semantic role —
/// h1→.title2, h2→.title3, h3→.headline — at the same point size by default.
private func proseFont(_ kind: ProseKind) -> Font {
    switch kind {
    case .h1: return Aero.responsive(16, .bold, relativeTo: .title2)
    case .h2: return Aero.responsive(15, .bold, relativeTo: .title3)
    case .h3: return Aero.responsive(14, .bold, relativeTo: .headline)
    case .plain, .bullet, .numbered: return Aero.body()
    }
}

// MARK: - Translate

/// Sheet identity for `.sheet(item:)` — carries the tapped turn's source text.
private struct TranslationCard: Identifiable {
    let id = UUID()
    let source: String
}

/// Real Translate surface: the original turn above for reference, the
/// streaming translation below — Copy hands the result to the clipboard with
/// the benchmark checkmark, Close waits for the stream to settle.
private struct TranslationSheet: View {
    let source: String
    let translated: String
    let busy: Bool
    let onCopy: (String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var copied = false

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Label("Translation", systemImage: "translate")
                    .font(Aero.responsive(17, .semibold, relativeTo: .body))
                    .foregroundStyle(Aero.text)
                Spacer()
                Button("Close") { dismiss() }
                    .font(Aero.responsive(15, relativeTo: .subheadline))
                    .foregroundStyle(Aero.textMuted)
                    .disabled(busy)
            }
            Text(source)
                .font(Aero.responsive(13, relativeTo: .footnote))
                .foregroundStyle(Aero.textMuted)
                .lineLimit(4)
            Divider().overlay(Aero.outline)
            if busy && translated.isEmpty {
                Text("Translating…")
                    .font(Aero.responsive(15, relativeTo: .subheadline))
                    .foregroundStyle(Aero.textMuted)
            } else {
                ScrollView {
                    Text(translated)
                        .font(Aero.responsive(15, relativeTo: .subheadline))
                        .foregroundStyle(Aero.text)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .textSelection(.enabled)
                }
                Button {
                    UIPasteboard.general.string = translated
                    onCopy(translated)
                    withAnimation(.easeOut(duration: 0.15)) { copied = true }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) {
                        withAnimation(.easeIn(duration: 0.2)) { copied = false }
                    }
                } label: {
                    Label(
                        copied ? "Copied" : "Copy translation",
                        systemImage: copied ? "checkmark" : "doc.on.doc"
                    )
                    .font(Aero.responsive(14, relativeTo: .subheadline))
                    .foregroundStyle(copied ? Aero.accent : Aero.textMuted)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(Aero.Spacing.l)
        .presentationDetents([.medium, .large])
    }
}

// MARK: - Bubble

private struct MessageBubble: View, Equatable {

    let message: ChatViewModel.ChatMessage
    var isSpeaking: Bool = false
    var editEnabled: Bool = false
    var highlight: Bool = false
    var onRegenerate: () -> Void = {}
    var onReadAloud: () -> Void = {}
    var onTranslate: () -> Void = {}
    var onSave: () -> Void = {}
    var onEditStart: () -> Void = {}
    var onBranch: () -> Void = {}

    @State private var copied = false

    /**
     * Deep-perf pass 80-b: equality covers the body's visible inputs only —
     * the action closures are rebuilt on every parent pass but are always
     * semantically identical. With it, SwiftUI skips this row's body whenever
     * nothing visible changed (composer keystrokes, streaming flushes that
     * only move the live bubble), so a 60-turn screen no longer re-runs
     * markdown/code parsing for every visible bubble on every body pass.
     */
    static func == (lhs: MessageBubble, rhs: MessageBubble) -> Bool {
        lhs.message == rhs.message &&
        lhs.isSpeaking == rhs.isSpeaking &&
        lhs.editEnabled == rhs.editEnabled &&
        lhs.highlight == rhs.highlight
    }

    /// Benchmark copy feedback: the icon answers with a brief checkmark —
    /// the SwiftUI counterpart of Android's "Copied" snack.
    private func copyAndConfirm(_ text: String) {
        UIPasteboard.general.string = text
        GSHaptics.success()
        withAnimation(.easeOut(duration: 0.15)) { copied = true }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) {
            withAnimation(.easeIn(duration: 0.2)) { copied = false }
        }
    }

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
                    .textSelection(.enabled)
            }
            HStack(spacing: 10) {
                if let stamp = timeLabel(message.createdAt) {
                    Text(stamp)
                        .font(Aero.responsive(11, relativeTo: .caption2))
                        .foregroundStyle(Aero.textMuted)
                }
                Button {
                    copyAndConfirm(message.content)
                } label: {
                    Image(systemName: copied ? "checkmark" : "doc.on.doc")
                        .foregroundStyle(copied ? Aero.accent : Aero.textMuted)
                }
                .accessibilityLabel(copied ? "Copied" : "Copy message")
                if editEnabled {
                    Button(action: onEditStart) {
                        Image(systemName: "pencil")
                    }
                    .accessibilityLabel("Edit message")
                }
            }
            .font(Aero.responsive(13, relativeTo: .footnote))
            .foregroundStyle(Aero.textMuted)
            .buttonStyle(KineticPressStyle())
        }
        .contextMenu {
            // Same idiom as the assistant bubble's long-press menu.
            Button {
                copyAndConfirm(message.content)
            } label: {
                Label("Copy", systemImage: "doc.on.doc")
            }
            if editEnabled {
                Button(action: onEditStart) {
                    Label("Edit", systemImage: "pencil")
                }
            }
        }
    }

    private var assistantBubble: some View {
        HStack(alignment: .top, spacing: 8) {
            assistantAvatar
            VStack(alignment: .leading, spacing: 8) {
                if message.isStreaming {
                    // Deep-perf pass 80-b: the live bubble is one plain Text +
                    // the aurora indicator. Growing text re-renders at the
                    // ~30Hz flush cadence — no fence/markdown/syntax regex
                    // over the whole message per flush. The styled render
                    // below happens once on finalize. Selection/links land
                    // with it: text selection over a 30 Hz-growing Text
                    // fights the reader's drag (Task 85-e I5 contract).
                    Text(message.content.isEmpty ? "…" : message.content)
                        .font(Aero.body())
                        .foregroundStyle(Aero.text)
                    AuroraIndicator()
                } else {
                    ForEach(parsedSegments) { segment in
                        if segment.isCode {
                            codeBlock(segment)
                        } else {
                            proseBlock(segment)
                        }
                    }
                    if !message.content.isEmpty {
                        actionRow
                    }
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

    /// Small assistant badge — mirrors the Android thread mark.
    private var assistantAvatar: some View {
        Image(systemName: "sparkles")
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(Aero.accent)
            .frame(width: 26, height: 26)
            .background(Circle().fill(Aero.accent.opacity(0.12)))
            .overlay(Circle().stroke(Aero.outline, lineWidth: 1))
    }

    private var parsedSegments: [ContentSegment] {
        if message.content.isEmpty {
            return [ContentSegment(id: 0, text: "…", isCode: false, language: nil)]
        }
        return Self.cachedSegments(for: message)
    }

    // MARK: Per-message parse cache (deep-perf pass 80-b)

    /// Segment parse per finished message, keyed by turn id. LazyVStack rows
    /// re-evaluate on every (re)materialisation — without the cache, scrolling
    /// back through a thread re-ran the fence regex per bubble per pass.
    /// NSCache auto-evicts under memory pressure; segments are style-free
    /// data, so one entry serves both appearances.
    private static let segmentCache = NSCache<NSString, SegmentBox>()

    private final class SegmentBox {
        let segments: [ContentSegment]
        init(_ segments: [ContentSegment]) { self.segments = segments }
    }

    private static func cachedSegments(for message: ChatViewModel.ChatMessage) -> [ContentSegment] {
        let key = message.id.uuidString as NSString
        if let box = segmentCache.object(forKey: key) { return box.segments }
        let segments = parseContentSegments(message.content)
        segmentCache.setObject(SegmentBox(segments), forKey: key)
        return segments
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
                CodeCopyButton(text: segment.text)
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
                    .textSelection(.enabled)
            } else {
                highlightedCode(segment.text, language: segment.language)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12)
                    .padding(.bottom, 10)
                    .textSelection(.enabled)
            }
        }
        .background(RoundedRectangle(cornerRadius: 12).fill(Aero.container))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Aero.outline, lineWidth: 1))
    }

    private var actionRow: some View {
        HStack(spacing: 18) {
            if let stamp = timeLabel(message.createdAt) {
                Text(stamp)
                    .font(Aero.responsive(11, relativeTo: .caption2))
                    .foregroundStyle(Aero.textMuted)
            }
            Button {
                copyAndConfirm(message.content)
            } label: {
                Image(systemName: copied ? "checkmark" : "doc.on.doc")
                    .foregroundStyle(copied ? Aero.accent : Aero.textMuted)
            }
            .accessibilityLabel(copied ? "Copied" : "Copy reply")
            Button(action: onRegenerate) {
                Image(systemName: "arrow.clockwise")
            }
            .accessibilityLabel("Regenerate reply")
            ShareLink(item: message.content) {
                Image(systemName: "square.and.arrow.up")
            }
            .accessibilityLabel("Share reply")
            Button(action: onReadAloud) {
                Image(systemName: isSpeaking ? "stop.fill" : "speaker.wave.2")
                    .foregroundStyle(isSpeaking ? Aero.accent : Aero.textMuted)
            }
            .accessibilityLabel(isSpeaking ? "Stop reading aloud" : "Read reply aloud")
            Button(action: onTranslate) {
                Image(systemName: "translate")
            }
            .accessibilityLabel("Translate reply")
            Button(action: onSave) {
                Image(systemName: "bookmark")
            }
            .accessibilityLabel("Save reply")
        }
        .font(Aero.responsive(13, relativeTo: .footnote))
        .foregroundStyle(Aero.textMuted)
        .buttonStyle(KineticPressStyle())
    }

    private var bubbleMenu: some View {
        Group {
            Button {
                copyAndConfirm(message.content)
            } label: {
                Label("Copy", systemImage: "doc.on.doc")
            }
            Button(action: onRegenerate) {
                Label("Regenerate", systemImage: "arrow.clockwise")
            }
            ShareLink(item: message.content) {
                Label("Share", systemImage: "square.and.arrow.up")
            }
            Button(action: onBranch) {
                Label("Branch new chat", systemImage: "arrow.triangle.branch")
            }
        }
    }
}

/// Code-header copy with its own checkmark confirmation.
private struct CodeCopyButton: View {
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

// MARK: Date separators

/// Centered day pill — the benchmark thread rhythm ("Today", "Yesterday", dates).
/// Rows without a parsable stamp (legacy data) simply show no header.
private struct DaySeparator: View {
    let label: String
    var body: some View {
        Text(label)
            .font(Aero.label())
            .foregroundStyle(Aero.textMuted)
            .accessibilityAddTraits(.isHeader)
            .padding(.horizontal, 12)
            .padding(.vertical, 4)
            .background(Capsule().fill(Aero.containerHigh))
            .frame(maxWidth: .infinity)
    }
}

private func parseISODate(_ iso: String) -> Date? {
    GSFormatters.date(from: iso) // cached formatters — was a fresh ISO8601DateFormatter per call
}

private func dayKey(_ iso: String) -> String? {
    guard let date = parseISODate(iso) else { return nil }
    return GSFormatters.dayStamp.string(from: date)
}

/// Quiet per-turn clock in the action row — the benchmark timestamp treatment.
private func timeLabel(_ iso: String) -> String? {
    guard let date = parseISODate(iso) else { return nil }
    return GSFormatters.clock.string(from: date)
}

private func dayLabel(_ iso: String) -> String? {
    guard let date = parseISODate(iso) else { return nil }
    let calendar = Calendar.current
    let today = calendar.startOfDay(for: Date())
    let day = calendar.startOfDay(for: date)
    if day == today { return "Today" }
    if day == calendar.date(byAdding: .day, value: -1, to: today) { return "Yesterday" }
    return GSFormatters.dayTitle.string(from: date)
}
