import SwiftUI
import UIKit

/// One conversation — streaming transcript, stop button, starter chips for
/// empty chats, per-message copy/regenerate/share. Backed by ChatViewModel,
/// which owns conversation creation and the SSE stream lifecycle.
struct ChatDetailView: View {

    @StateObject private var vm: ChatViewModel

    // Voice press-and-hold handoff — seeds the composer once on arrival.
    private let prefill: String?
    // Phase 2 routing contract (.chatAutoSend): open a NEW chat and send the
    // prefilled prompt automatically on first appear. The once-guard lives
    // in @State so rotation / re-appear never re-sends.
    private let autoSendPrefill: Bool
    @State private var autoSendConsumed = false

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

    // Conversation options (reference sheet): Share / Rename / Chat Details /
    // Export / Clear / Delete, then Cancel — the same contract as Android.
    @State private var showOptions = false
    @State private var showRename = false
    @State private var renameDraft = ""
    @State private var showDeleteConfirm = false
    @State private var showClearConfirm = false
    @State private var showInfo = false

    // Read-aloud — on-device speech, silent when the device has no voice.
    @StateObject private var speech = SpeechPlayer()

    // Task 85-e: platform wiring.
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.dismiss) private var dismiss
    @FocusState private var composerFocused: Bool
    @ObservedObject private var conversations = ConversationStore.shared
    // Voice entry (Step 4): the same shared Router every pushed surface reads
    // (ChatsListView, VoiceView, …) — the composer mic
    // appends .voice onto the stack this screen was pushed onto.
    @EnvironmentObject private var router: Router
    // Offline state (Step 4): one shared NWPathMonitor for the whole process.
    @ObservedObject private var network = NetworkMonitor.shared
    // PHASE 5: the shared attachment store — composer chips, upload phases,
    // send gating and draft persistence all read/write through it.
    @ObservedObject private var attachments = AttachmentStore.shared

    // PHASE 3 workspace model: the app-root instance of this screen carries
    // the shell chrome — the drawer menu replaces the back button, and "+"
    // resets to a fresh workspace via the router. Pushed instances (opened
    // from the drawer's recents) keep the standard back affordance.
    var isWorkspaceRoot: Bool = false
    var onOpenDrawer: (() -> Void)? = nil
    var onNewChat: (() -> Void)? = nil

    init(conversationID: String?, prefill: String? = nil, autoSendPrefill: Bool = false,
         isWorkspaceRoot: Bool = false,
         onOpenDrawer: (() -> Void)? = nil,
         onNewChat: (() -> Void)? = nil) {
        _vm = StateObject(wrappedValue: ChatViewModel(conversationID: conversationID))
        self.prefill = prefill
        self.autoSendPrefill = autoSendPrefill
        self.isWorkspaceRoot = isWorkspaceRoot
        self.onOpenDrawer = onOpenDrawer
        self.onNewChat = onNewChat
    }

    var body: some View {
        ZStack {
            NexlyChatBackdrop()
            VStack(spacing: 0) {
                brandHeader

                if let error = vm.errorMessage {
                    ErrorStateView(message: error, retry: {
                        vm.errorMessage = nil
                        vm.retry()
                    })
                    .padding(.horizontal, Aero.Spacing.m)
                    .padding(.top, Aero.Spacing.s)
                }

                if network.isOffline {
                    offlinePill
                }

                transcript

                composerZone
            }
            // Pin the column to the proposed height so the transcript absorbs
            // the slack. Without this the VStack can keep its content height
            // and push the composer past the bottom edge in landscape.
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
        .toolbar(.hidden, for: .navigationBar)
        .alert("Rename chat", isPresented: $showRename) {
            TextField("Chat name", text: $renameDraft)
            Button("Save") {
                if let id = vm.conversationID {
                    conversations.rename(id: id, to: renameDraft)
                }
            }
            Button("Cancel", role: .cancel) {}
        }
        .confirmationDialog(
            "Delete this chat?",
            isPresented: $showDeleteConfirm,
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                if let id = vm.conversationID {
                    conversations.delete(id: id)
                }
                leaveConversation()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This conversation and its messages will be removed from this device.")
        }
        .confirmationDialog(
            "Clear all messages?",
            isPresented: $showClearConfirm,
            titleVisibility: .visible
        ) {
            Button("Clear messages", role: .destructive) {
                clearMessages()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("The chat stays in your history, but every message in it is removed.")
        }
        .sheet(isPresented: $showOptions) {
            conversationOptionsSheet
        }
        .sheet(isPresented: $showInfo) {
            chatInfoSheet
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
            } else {
                restoreDraft()
            }
            // Auto-send (Phase 2 routing contract): a .chatAutoSend route
            // arrives with the prompt already in the composer — send it once
            // on first appear. The @State once-guard makes every later
            // onAppear (rotation, re-appear from Voice, back-navigation)
            // a no-op, so the prompt can never re-send.
            if autoSendPrefill, !autoSendConsumed, !vm.isStreaming {
                autoSendConsumed = true
                if vm.draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, let prefill {
                    vm.draft = prefill
                }
                sendAndClearDraft()
            }
        }
        .sheet(isPresented: $showingAttachments) {
            AttachmentSheetView(
                onImageData: { data, mime, source in
                    if source == .camera {
                        attachments.addCameraCapture(data)
                    } else {
                        attachments.addImageData(data, mimeType: mime, source: source)
                    }
                },
                onFileURL: { url in
                    attachments.addFileURL(url)
                },
                onUnavailable: { option in
                    showToast(attachmentMessage(for: option))
                },
                remainingSlots: attachments.remainingSlots,
                // FLASH MODE (Phase 3) — the Mode section in the same sheet.
                selectedMode: vm.chatMode,
                onModeSelected: { vm.setChatMode($0) })
        }
        .sheet(item: $translationCard) { card in
            TranslationSheet(
                source: card.source,
                translated: translationText,
                busy: translating,
                onCopy: { _ in showToast("Copied") }
            )
        }
        // TASK 1c: one in-app browser host for the whole chat surface —
        // reply text links, citation chips and source cards all resolve here.
        .gsInAppBrowserHost()
    }

    /// The reference brand header: the companion orb, the product identity,
    /// and two quiet glass controls. The old navigation bar (inline title,
    /// back chevron, ellipsis) is gone — this screen owns its chrome.
    private var brandHeader: some View {
        NexlyBrandHeader(
            sparkleLabel: onNewChat != nil ? "New chat" : "Search in chat",
            menuGlyph: isWorkspaceRoot && onOpenDrawer != nil ? "menu-2" : "chevron-left",
            menuLabel: isWorkspaceRoot && onOpenDrawer != nil ? "Open menu" : "Back",
            onSparkle: {
                if let newChat = onNewChat {
                    newChat()
                } else {
                    toggleSearch()
                }
            },
            onMenu: {
                if isWorkspaceRoot, let openDrawer = onOpenDrawer {
                    openDrawer()
                } else {
                    dismiss()
                }
            },
            sparkleMenu: {
                if let newChat = onNewChat {
                    Button(action: newChat) {
                        Label("New chat", systemImage: "square.and.pencil")
                    }
                }
                Button {
                    toggleSearch()
                } label: {
                    Label("Search in chat", systemImage: "magnifyingglass")
                }
                Button {
                    router.path.append(.voice)
                } label: {
                    Label("Voice input", systemImage: "mic")
                }
            },
            menuMenu: {
                if vm.conversationID != nil {
                    Button {
                        showOptions = true
                    } label: {
                        Label("Chat options", systemImage: "ellipsis")
                    }
                }
                Button {
                    toggleSearch()
                } label: {
                    Label("Search in chat", systemImage: "magnifyingglass")
                }
                if let newChat = onNewChat {
                    Button(action: newChat) {
                        Label("New chat", systemImage: "square.and.pencil")
                    }
                }
            }
        )
    }

    private func toggleSearch() {
        withAnimation(.easeOut(duration: 0.18)) {
            searchActive.toggle()
        }
        if !searchActive {
            searchQuery = ""
            searchIndex = 0
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
                LazyVStack(spacing: 20) {
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
                        messageRow(index, message, activeMatch: activeMatch)
                    }

                    // Live-edge sentinel: 1pt after the newest turn. Materialized
                    // ⇔ the reader is at the bottom — the iOS16-honest position
                    // signal (no scroll-offset API). Pinned by the follow scroll
                    // itself, so streaming never flickers it.
                    liveEdgeSentinel
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
                withAnimation(Aero.chatCurve) {
                    // Pin the sentinel, not the bubble: the 1pt row stays
                    // materialized at the viewport bottom, so atBottom holds
                    // steady and the jump button never flickers mid-stream.
                    proxy.scrollTo(liveEdgeID, anchor: .bottom)
                }
            }
            .onDisappear { disengageWork?.cancel() }
            .overlay(alignment: .bottomTrailing) {
                jumpToLive(proxy)
            }
            }
        }
    }

    // MARK: Transcript building blocks (type-check split — the previous single
    // expression exceeded the Swift type-checker's budget; same view tree,
    // decomposed into named sub-expressions)

    /// One bubble (and its day separator when the day rolls over).
    /// PHASE 8.1: assistant rows additionally compose — the live search-trace
    /// card ABOVE the streaming bubble, the collapsed trace summary + source
    /// cards + clarify chips around the finalized one — all additive around
    /// the unchanged MessageBubble.
    @ViewBuilder
    private func messageRow(
        _ index: Int,
        _ message: ChatViewModel.ChatMessage,
        activeMatch: ChatViewModel.ChatMessage.ID?
    ) -> some View {
        if let stamp = dayLabel(message.createdAt),
           index == 0 || dayKey(vm.messages[index - 1].createdAt) != dayKey(message.createdAt) {
            DaySeparator(label: stamp)
        }
        if editingIndex == index {
            editEditor
        } else {
            VStack(alignment: .leading, spacing: 8) {
                if message.role == "assistant" {
                    if message.isStreaming, !vm.traceSteps.isEmpty {
                        // The compact trace card — ONLY steps that really
                        // arrived as events; after done it collapses to the
                        // one-line summary below (protocol rule).
                        SearchTraceView(steps: vm.traceSteps)
                    }
                    if !message.isStreaming, let summary = message.traceSummary {
                        SearchTraceSummaryView(text: summary)
                    }
                    // PHASE 8.2: the expandable research audit — live from
                    // the received events, after done from the turn's
                    // memory-only steps + persisted sources; a reloaded
                    // thread re-derives from the sources alone. Self-hides
                    // when the turn carries no research detail at all.
                    ResearchDetailsView(
                        steps: message.isStreaming ? vm.traceSteps : (message.traceSteps ?? []),
                        sources: message.isStreaming ? vm.liveSources : (message.sources ?? []))
                }
                MessageBubble(
                    message: message,
                    isSpeaking: speech.speakingMessageID == message.id.uuidString,
                    editEnabled: !vm.isStreaming && editingIndex == nil,
                    highlight: message.id == activeMatch,
                    orbState: orbStateForChatStreaming(
                        isStreaming: message.isStreaming,
                        liveContentEmpty: message.content.isEmpty,
                        requestHasAttachments: pendingTurnHasAttachments,
                        searchPhase: vm.searchPhase),
                    reasoning: message.isStreaming ? vm.reasoningText : nil,
                    reasoningStart: message.isStreaming ? vm.reasoningStart : nil,
                    reasoningEnd: message.isStreaming ? vm.reasoningEnd : nil,
                    effectiveMode: message.isStreaming ? vm.effectiveMode : nil,
                    effectiveEffort: message.isStreaming ? vm.effectiveEffort : nil,
                    onRegenerate: {
                        userIsReading = false
                        vm.regenerate()
                    },
                    onReadAloud: { speech.toggle(messageID: message.id.uuidString, text: message.content) },
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
                if message.role == "assistant" {
                    if message.isStreaming {
                        if !vm.liveSources.isEmpty {
                            SourceCardsView(sources: vm.liveSources)
                        }
                        if let prompt = vm.clarifyPrompt {
                            ClarifyChipsView(prompt: prompt, onPick: sendClarifyPick)
                        }
                    } else {
                        if let sources = message.sources, !sources.isEmpty {
                            SourceCardsView(sources: sources)
                        }
                        if let options = message.clarifyOptions,
                           let prompt = ClarifyPrompt(jsonString: options) {
                            ClarifyChipsView(prompt: prompt, onPick: sendClarifyPick)
                        }
                    }
                }
            }
            .id(message.id)
        }
    }

    /// PHASE 8.1: a tapped clarify chip sends its label as a normal user
    /// message — the same gated send path the composer uses.
    private func sendClarifyPick(_ label: String) {
        userIsReading = false
        vm.sendClarifyChoice(label)
    }

    /// The pending turn's attachment presence drives the honest orb mapping
    /// (vision/extraction wait = .working) — shared by both orb call sites.
    private var pendingTurnHasAttachments: Bool {
        !(vm.messages.last(where: { $0.role == "user" })?.attachments?.isEmpty ?? true)
    }

    /// 1pt row after the newest turn — materialized ⇔ the reader is at the
    /// bottom (the iOS16-honest position signal, no scroll-offset API).
    private var liveEdgeSentinel: some View {
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

    /// Jump-to-latest affordance while the reader is away from the live edge.
    @ViewBuilder
    private func jumpToLive(_ proxy: ScrollViewProxy) -> some View {
        if userIsReading && !vm.messages.isEmpty {
            Button {
                userIsReading = false
                withAnimation(Aero.chatCurve) {
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

    // MARK: Composer (Step 4 unified zone)

    /// The reference composer: ONE capsule, a lilac sparkle at the leading
    /// edge, the field, and the peach paper-plane send control at the trailing
    /// edge. Attach, mode, and voice all remain reachable from the sparkle
    /// (tap = attachments/modes sheet, long-press = voice/search/new chat), so
    /// no capability was traded for the single-pill composition.
    private var composerZone: some View {
        VStack(spacing: 6) {
            if let message = toast {
                HStack(spacing: Aero.Spacing.xs) {
                    Image(systemName: "checkmark.circle")
                        .font(.system(size: 12))
                        .foregroundStyle(Aero.accent)
                    Text(message)
                        .font(Aero.label())
                        .foregroundStyle(Aero.textMuted)
                        .lineLimit(1)
                    Spacer()
                }
                .accessibilityElement(children: .combine)
            }
            if !attachments.drafts.isEmpty {
                AttachmentChipRow(
                    drafts: attachments.drafts,
                    onRetry: { attachments.retry(id: $0) },
                    onRemove: { attachments.remove(id: $0) })
            }
            if let reason = attachments.sendBlockReason {
                HStack(spacing: Aero.Spacing.xs) {
                    Image(systemName: "info.circle")
                        .font(.system(size: 12))
                        .foregroundStyle(Aero.textMuted)
                    Text(reason)
                        .font(Aero.label())
                        .foregroundStyle(Aero.textMuted)
                        .lineLimit(1)
                    Spacer()
                }
                .accessibilityElement(children: .combine)
            }

            NexlyComposerBar(
                text: $vm.draft,
                isStreaming: vm.isStreaming,
                canSend: canSendFromComposer,
                focused: $composerFocused,
                onAttach: { showingAttachments = true },
                onVoice: { router.path.append(.voice) },
                onSearch: { toggleSearch() },
                onSend: {
                    userIsReading = false
                    sendAndClearDraft()
                },
                onStop: { vm.stop() },
                onSubmit: submitComposer
            )
        }
        .padding(.horizontal, Aero.Spacing.m)
        .padding(.top, 6)
        .padding(.bottom, 6)
        .frame(maxWidth: columnMaxWidth)
    }

    private var canSendFromComposer: Bool {
        !vm.draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            (attachments.hasDrafts && attachments.canSend)
    }

    /// Enter-to-send reads the same setting as the shared input bar and uses a
    /// short debounce so the onSubmit + trailing-newline paths can never
    /// double-fire one keypress.
    private func submitComposer() {
        guard SettingsStore.shared.enterToSend, canSendFromComposer, !vm.isStreaming else { return }
        userIsReading = false
        sendAndClearDraft()
    }

    /// Slim honest offline line (Step 4): shown only while the system reports
    /// no usable network. Copy reflects what actually happens — sends are NOT
    /// queued for later; the turn is answered by the on-device responder
    /// (ChatViewModel's GS Lite path) and persists in the local store, so the
    /// thread keeps working until connectivity returns.
    private var offlinePill: some View {
        HStack(spacing: Aero.Spacing.s) {
            Image(systemName: "wifi.slash")
                .font(.system(size: 12))
            Text("Offline, replies come from the on-device responder until you reconnect.")
                .font(Aero.label())
                .foregroundStyle(Aero.textMuted)
                .multilineTextAlignment(.leading)
        }
        .foregroundStyle(Aero.textMuted)
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        // Offline notice sits as a calm well on the canvas — the Sunken glass
        // role, no lift: an ember state should not look like it is floating up.
        .gsGlass(.sunken, corner: Aero.Radius.sm)
        .padding(.horizontal, Aero.Spacing.m)
        .padding(.top, Aero.Spacing.s)
        .accessibilityElement(children: .combine)
    }

    /// The two tiles without a backend consumer stay honestly unavailable —
    /// muted in the sheet, and the toast says exactly that (no fakes).
    private func attachmentMessage(for option: String) -> String {
        switch option {
        case "Code": return "Code attachments arrive with the repo build"
        case "Prompt template": return "Prompt templates arrive with the library build"
        default: return "Not available yet"
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

    // MARK: - Conversation options (reference sheet)

    /// The whole conversation as plain text — what Share and Export hand off.
    private var transcriptText: String {
        guard let id = vm.conversationID else { return conversationTitle }
        return conversations.messages(for: id).map { message in
            let who = message.role == "user" ? "You" : "Nexly"
            return "\(who): \(message.content)"
        }.joined(separator: "\n\n")
    }

    private var exportText: String {
        return "# \(conversationTitle)\n\n\(transcriptText)"
    }

    private func clearMessages() {
        guard let id = vm.conversationID,
              let first = conversations.messages(for: id).first else { return }
        conversations.truncateMessages(fromUserContent: first.content, in: id)
    }

    /// A sheet cannot present another sheet: dismiss first, then open.
    private func openAfterDismiss(_ action: @escaping () -> Void) {
        Task {
            try? await Task.sleep(nanoseconds: 280_000_000)
            action()
        }
    }

    private var conversationOptionsSheet: some View {
        VStack(spacing: 6) {
            Capsule()
                .fill(Aero.outlineStrong)
                .frame(width: 38, height: 4)
                .padding(.top, 8)
            Text(conversationTitle)
                .font(Aero.title())
                .foregroundStyle(Aero.text)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16)
                .padding(.top, 4)
            ScrollView {
                VStack(spacing: 4) {
                    ShareLink(item: transcriptText) {
                        optionLabel("square.and.arrow.up", "Share Chat", "Share this conversation")
                    }
                    Button {
                        showOptions = false
                        openAfterDismiss { showRename = true }
                    } label: {
                        optionLabel("pencil", "Rename Chat", "Give your chat a new name")
                    }
                    Button {
                        showOptions = false
                        openAfterDismiss { showInfo = true }
                    } label: {
                        optionLabel("info.circle", "Chat Details", "View info & stats")
                    }
                    ShareLink(item: exportText) {
                        optionLabel("doc.text", "Export Chat", "Send the transcript as text")
                    }
                    Button {
                        showOptions = false
                        openAfterDismiss { showClearConfirm = true }
                    } label: {
                        optionLabel("eraser", "Clear Chat", "Remove all messages")
                    }
                    Button {
                        showOptions = false
                        openAfterDismiss { showDeleteConfirm = true }
                    } label: {
                        optionLabel("trash", "Delete Chat", "Permanently delete this chat", destructive: true)
                    }
                    Button {
                        showOptions = false
                    } label: {
                        optionLabel("xmark", "Cancel", "")
                    }
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 12)
                .padding(.top, 8)
                .padding(.bottom, 24)
            }
        }
        .background(Aero.background.ignoresSafeArea())
        .presentationDetents([.medium, .large])
    }

    private func optionLabel(
        _ icon: String,
        _ title: String,
        _ subtitle: String,
        destructive: Bool = false
    ) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(destructive ? Aero.danger : Aero.textSecondary)
                .frame(width: 38, height: 38)
                .gsGlass(.sunken, corner: 12)
            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                    .font(Aero.label())
                    .foregroundStyle(destructive ? Aero.danger : Aero.text)
                if !subtitle.isEmpty {
                    Text(subtitle)
                        .font(Aero.caption())
                        .foregroundStyle(destructive ? Aero.danger.opacity(0.75) : Aero.textMuted)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
    }

    private var chatInfoSheet: some View {
        ChatInfoSheet(
            title: conversationTitle,
            messages: vm.conversationID.map { conversations.messages(for: $0) } ?? [],
            mode: vm.chatMode,
            local: SettingsStore.shared.preferLocal)
    }

    /// Leave the chat that was just archived or deleted. At the workspace root
    /// that means opening a fresh workspace; on a pushed chat it means popping
    /// the stack — never leaving a tombstone on screen.
    private func leaveConversation() {
        if let newChat = onNewChat {
            newChat()
        } else if !router.path.isEmpty {
            router.path.removeLast()
        }
    }

    /// Park the unsent draft with the conversation — it comes back when the
    /// thread reopens. PHASE 5: the draft is a JSON envelope {text,
    /// attachments} where ready-or-failed attachment drafts ride along (their
    /// staged copies are on disk); a legacy plain-string value restores as
    /// text-only. Blank everything clears the slot. Shared by onDisappear
    /// and the scenePhase background flush so both write identically.
    private func persistDraft() {
        guard let id = vm.conversationID else { return }
        let text = vm.draft
        let persistable = attachments.drafts.filter { $0.phase == .ready || $0.phase == .failed }
        if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && persistable.isEmpty {
            UserDefaults.standard.removeObject(forKey: "draft_\(id)")
            return
        }
        let raw = DraftEnvelope.encode(text: text, drafts: persistable) ?? text
        UserDefaults.standard.set(raw, forKey: "draft_\(id)")
    }

    /// Restore path: envelope JSON rehydrates text AND attachment drafts
    /// (AttachmentStore verifies each staged copy still exists — missing
    /// files are dropped, never faked). Legacy plain strings restore as
    /// text-only. No-ops once any live state exists, so re-appear from a
    /// pushed screen never clobbers the composer.
    private func restoreDraft() {
        guard let id = vm.conversationID,
              vm.draft.isEmpty,
              !attachments.hasDrafts,
              !vm.isStreaming else { return }
        guard let raw = UserDefaults.standard.string(forKey: "draft_\(id)") else { return }
        if let envelope = DraftEnvelope.decode(raw) {
            vm.draft = envelope.text
            AttachmentStore.shared.restoreDrafts(envelope.attachments)
        } else {
            vm.draft = raw
        }
    }

    /// Live nav-bar title — follows the store row as it appears/renames
    /// (auto-titled threads update while the reply streams). A FRESH root
    /// workspace reads "GS": the product's quiet identity mark, never a page
    /// name like "New chat".
    private var conversationTitle: String {
        guard let id = vm.conversationID else {
            return isWorkspaceRoot ? "GS" : "New chat"
        }
        return gsConversationTitle(conversations.conversation(withID: id)?.title)
    }

    /// iPad / regular width: the transcript column and the composer row cap
    /// at one readable measure, centred. Compact (iPhone) is untouched.
    private var columnMaxWidth: CGFloat? {
        horizontalSizeClass == .regular ? 640 : nil
    }

    /// Sends and, when the composer actually empties, drops the parked draft.
    /// PHASE 5: ready drafts travel as their uploaded server ids; failed or
    /// still-uploading drafts block with the visible reason. A successful
    /// attachments turn clears the composer chips (staged copies stay for
    /// transcript thumbnails).
    private func sendAndClearDraft() {
        if let reason = attachments.sendBlockReason {
            showToast(reason)
            return
        }
        // Committed send — the Taptic tick the system keyboard plays on keys.
        GSHaptics.tap()
        let pendingID = vm.conversationID
        let sentAttachments = attachments.drafts.compactMap { draft -> Attachment? in
            guard let serverID = draft.serverID else { return nil }
            return Attachment(
                id: serverID,
                kind: draft.kind.rawValue,
                displayName: draft.displayName,
                mimeType: draft.mimeType,
                byteSize: draft.byteSize,
                createdAt: ConversationStore.now(),
                url: draft.remoteURL ?? "/api/v1/files/\(serverID)")
        }
        vm.send(attachments: sentAttachments)
        if !sentAttachments.isEmpty, vm.draft.isEmpty {
            // The send went through (send() clears the draft only then).
            attachments.clearSent()
        }
        if vm.draft.isEmpty, let pendingID {
            if attachments.hasDrafts {
                persistDraft() // drafts remain parked with the conversation
            } else {
                UserDefaults.standard.removeObject(forKey: "draft_\(pendingID)")
            }
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
    /// Typed as ChatMessage.ID (UUID): bubbles compare their own message.id
    /// against this directly.
    private var activeMatchID: ChatViewModel.ChatMessage.ID? {
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
        withAnimation(Aero.chatCurve) {
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
                    withAnimation(Aero.chatCurve) {
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
        // FloatingControl glass — the find bar hovers over the transcript, so
        // thinnest glass + brightest rim, the iOS twin of the Android search
        // surfaces (role contract, not a flat fill).
        .gsGlass(.floatingControl, corner: Aero.Radius.md)
    }

    // MARK: Empty state

    /// Inline edit surface for a sent user turn (benchmark pencil flow):
    /// Cancel returns the bubble untouched; Save & resend truncates the tail
    /// and streams a fresh reply.
    private var editEditor: some View {
        VStack(alignment: .trailing, spacing: 8) {
            TextField("Edit message", text: $editDraft, axis: .vertical)
                .font(Aero.body())
                .foregroundStyle(Aero.text)
                .padding(12)
                // The inline edit field is a sunken well — glass seen from the
                // shadowed side (the Sunken role), matching the composer family.
                .gsGlass(.sunken, corner: 14)
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

    // MARK: Workspace empty state (Phase 3)

    /// The starters — immediate composer suggestions, mirroring the Android
    /// workspace exactly. No navigation, no giant cards: a tap seeds the
    /// composer and raises the keyboard; the reader stays in control of send.
    private let starters = [
        "Draft a crisp product update email for our beta testers",
        "Explain Kotlin coroutines like I'm a senior Java developer",
        "Plan a three-day Tokyo itinerary focused on design studios"
    ]

    /// Time-of-day greeting — the empty state's written identity (the Phase 4
    /// breathing orb above it is the living one; neither is a hero).
    private var workspaceGreeting: String {
        let hour = Calendar.current.component(.hour, from: Date())
        let base: String
        switch hour {
        case 23, 0...4: base = "Up late?"
        case 5...11: base = "Good morning"
        case 12...17: base = "Good afternoon"
        default: base = "Good evening"
        }
        let name = AccountStore.shared.firstName
        return name.isEmpty ? base : "\(base), \(name)"
    }

    /// The fresh workspace IS the chat surface: small greeting, a few
    /// composer suggestions, readable empty space — and the composer already
    /// waiting at the bottom of the very same screen. Suggestions vanish the
    /// moment the reader types; the first turn replaces the whole block with
    /// the transcript. No navigation hop anywhere.
    private var emptyState: some View {
        VStack(spacing: Aero.Spacing.l) {
            AnimatedGsIllustration(imageName: "GSCompanionOrb", size: 112, breathDuration: 3.2, rotationDegrees: 0.9, lift: -2, startDelay: 0.18)
                .accessibilityLabel("GS assistant companion")
            Text("How can GS make today lighter?")
                .font(Aero.headline())
                .foregroundStyle(Aero.text)
                .multilineTextAlignment(.center)
            Text("Ask, create, explore, and make something beautiful.")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
                .multilineTextAlignment(.center)
            if vm.draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                VStack(spacing: Aero.Spacing.s) {
                    ForEach(starters, id: \.self) { starter in
                        AeroChip(text: starter) {
                            // Seed the composer and raise the keyboard — the
                            // reader edits, then sends through the gated send
                            // path.
                            vm.draft = starter
                            composerFocused = true
                        }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity)
        .frame(minHeight: UIScreen.main.bounds.height * 0.5)
        .accessibilityElement(children: .contain)
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
                    withAnimation(Aero.chatCurve) { copied = true }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) {
                        withAnimation(Aero.chatCurve) { copied = false }
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

struct MessageBubble: View, Equatable {

    let message: ChatViewModel.ChatMessage
    var isSpeaking: Bool = false
    var editEnabled: Bool = false
    var highlight: Bool = false
    /// PHASE 8.1: the honest orb state for the LIVE row, computed by the
    /// caller from the real stream/search phase. nil = settled turn (the
    /// mapping helper already returned nil for non-streaming rows).
    var orbState: OrbState? = nil
    /// REASONING TRACE (chat-reasoning panel): the live turn's thinking
    /// stream + measured span, passed explicitly because this row is
    /// Equatable on visible inputs only (vm state is not observed here).
    var reasoning: String? = nil
    var reasoningStart: Date? = nil
    var reasoningEnd: Date? = nil
    /// The backend's real mode/effort for the live turn. Stream-only; the
    /// card remembers what it saw so a thinking turn does not lose its
    /// status the instant the answer finalizes.
    var effectiveMode: String? = nil
    var effectiveEffort: String? = nil
    var onRegenerate: () -> Void = {}
    var onReadAloud: () -> Void = {}
    var onTranslate: () -> Void = {}
    var onSave: () -> Void = {}
    var onEditStart: () -> Void = {}
    var onBranch: () -> Void = {}

    @State private var copied = false
    @State private var sawThinking = false
    @State private var lastEffort: String? = nil
    @State private var lastReasoningHint: String? = nil
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.horizontalSizeClass) private var sizeClass

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
        lhs.highlight == rhs.highlight &&
        lhs.orbState == rhs.orbState &&
        lhs.reasoning == rhs.reasoning &&
        lhs.reasoningStart == rhs.reasoningStart &&
        lhs.reasoningEnd == rhs.reasoningEnd &&
        lhs.effectiveMode == rhs.effectiveMode &&
        lhs.effectiveEffort == rhs.effectiveEffort
    }

    /// Benchmark copy feedback: the icon answers with a brief checkmark —
    /// the SwiftUI counterpart of Android's "Copied" snack.
    private func copyAndConfirm(_ text: String) {
        UIPasteboard.general.string = text
        GSHaptics.success()
        withAnimation(Aero.chatCurve) { copied = true }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) {
            withAnimation(Aero.chatCurve) { copied = false }
        }
    }

    var body: some View {
        Group {
            if message.role == "user" {
                userBubble
            } else {
                assistantBubble
            }
        }
        .onChange(of: effectiveMode) { value in
            if value == "thinking" { sawThinking = true }
        }
        .onChange(of: reasoning) { value in
            if let value = value, !value.isEmpty { sawThinking = true }
            if let hint = reasoningHint(value) { lastReasoningHint = hint }
        }
        .onChange(of: effectiveEffort) { value in
            if let value = value, !value.isEmpty { lastEffort = value }
        }
    }

    private var userBubble: some View {
        VStack(alignment: .trailing, spacing: 5) {
            // PHASE 5: attachment chips render ABOVE/with the text — the
            // message anatomy is otherwise unchanged (additive only).
            if let sentAttachments = message.attachments, !sentAttachments.isEmpty {
                MessageAttachmentChips(attachments: sentAttachments)
            }
            HStack(alignment: .top, spacing: 12) {
                Spacer(minLength: 44)
                VStack(alignment: .trailing, spacing: 5) {
                    Text(message.content)
                        .font(.system(size: 13))
                        .foregroundStyle(Aero.text)
                        .lineSpacing(2)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(
                            RoundedRectangle(cornerRadius: 18, style: .continuous)
                                .fill(
                                    LinearGradient(
                                        colors: userBubbleColors,
                                        startPoint: .top,
                                        endPoint: .bottom
                                    )
                                )
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 18, style: .continuous)
                                .strokeBorder(
                                    highlight ? Aero.accent : Color.white.opacity(0.58),
                                    lineWidth: highlight ? 2 : 0.5
                                )
                        )
                        .shadow(color: Color(red: 0.55, green: 0.32, blue: 0.24).opacity(0.08),
                                radius: 3, y: 1)
                        .frame(maxWidth: sizeClass == .regular ? 260 : 210,
                               alignment: .trailing)
                        .textSelection(.enabled)

                    // Reference delivery receipt: time, then the double tick.
                    HStack(spacing: 4) {
                        if let stamp = timeLabel(message.createdAt) {
                            Text(stamp)
                                .font(.system(size: 9.5, weight: .regular))
                                .foregroundStyle(Aero.textTertiary)
                        }
                        NexlyGlyph(
                            name: "checks",
                            size: 12,
                            color: Aero.textSecondary.opacity(0.78)
                        )
                    }
                    .padding(.trailing, 2)
                }
                NexlyUserAvatar(size: 30)
            }
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

    private var userBubbleColors: [Color] {
        if colorScheme == .dark {
            return [Aero.surface.opacity(0.62), Aero.accentSoft.opacity(0.34)]
        }
        return [
            Color(red: 1.0, green: 0.945, blue: 0.910).opacity(0.62),
            Color(red: 1.0, green: 0.875, blue: 0.808).opacity(0.50),
        ]
    }

    /// A plan turn always keeps the card (the timetable is the proof of the
    /// work). A thinking-mode turn keeps it for the session; a flash turn with
    /// no reasoning never pretends it thought.
    private var showThinkingCard: Bool {
        if hasPlan || sawThinking || effectiveMode == "thinking" { return true }
        return !(reasoning ?? "").isEmpty
    }

    private var thinkingPhase: NexlyThinkingPhase {
        guard message.isStreaming else { return .done }
        if !message.content.isEmpty { return .effort }
        if let reasoning, !reasoning.isEmpty { return .reasoning }
        return .thinking
    }

    private var thinkingEffort: String? { effectiveEffort ?? lastEffort }

    /// The first meaningful sentence of the real reasoning, capped for one row.
    /// `lastReasoningHint` keeps it after finalize, when the stream-only
    /// reasoning text is cleared.
    private var thinkingReasoningHint: String? {
        reasoningHint(reasoning) ?? lastReasoningHint
    }

    private func reasoningHint(_ text: String?) -> String? {
        guard let text, !text.isEmpty else { return nil }
        let collapsed = text
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespaces)
        if let end = collapsed.firstIndex(where: { $0 == "." || $0 == "!" || $0 == "?" }) {
            return String(collapsed[...end])
        }
        return String(collapsed.prefix(96))
    }

    private var assistantBubble: some View {
        VStack(alignment: .leading, spacing: 6) {
            NexlyAssistantBlocksView(
                blocks: message.isStreaming ? streamingBlocks : finalizedBlocks,
                isStreaming: message.isStreaming,
                showStatus: showThinkingCard,
                thinkingPhase: thinkingPhase,
                thinkingEffort: thinkingEffort,
                thinkingReasoningHint: thinkingReasoningHint,
                isPlanTurn: hasPlan,
                statusAtEnd: hasPlan,
                citations: citationLinks,
                planTimestamp: message.createdAt
            )
            .background {
                if highlight {
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .fill(Aero.accentSoft.opacity(0.7))
                }
            }
            .contextMenu { bubbleMenu }

            // The reference's assistant footer is the timestamp alone; copy,
            // read aloud, regenerate, translate, save and branch all live in
            // the long-press menu. A plan card carries its own timestamp.
            if !message.isStreaming && !hasPlan, let stamp = timeLabel(message.createdAt) {
                Text(stamp)
                    .font(.system(size: 10.5, weight: .regular))
                    .foregroundStyle(Aero.textMuted.opacity(0.85))
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .padding(.trailing, 2)
            }
        }
    }

    /// True once the turn carries (or is streaming) a `plan` fence. The
    /// Thinking/Reasoning/Effort panel survives finalize for these turns.
    private var hasPlan: Bool {
        if message.content.range(of: "```plan", options: .caseInsensitive) != nil { return true }
        return finalizedBlocks.contains { block in
            if case .plan(_, _, _) = block { return true }
            return false
        }
    }

    /// Finalized content → ordered blocks (STEP 5). Empty content renders the
    /// quiet ellipsis paragraph; everything else parses once and caches.
    private var finalizedBlocks: [Block] {
        if message.content.isEmpty {
            return [.paragraph(spans: [.text("…")], sourceStart: 0)]
        }
        return Self.cachedBlocks(for: message)
    }

    /// PHASE 8.1: ordinal → URL for the answer's `[N]` citation chips. Built
    /// only from the turn's real sources (done payload / persisted rows);
    /// markers with no matching source stay plain text. http(s) only —
    /// model output is never trusted as a URL (same rule as InlineMarkdown).
    private var citationLinks: [Int: String] {
        var links: [Int: String] = [:]
        for source in message.sources ?? [] where source.ordinal > 0 {
            let lower = source.url.lowercased()
            guard lower.hasPrefix("https://") || lower.hasPrefix("http://") else { continue }
            links[source.ordinal] = source.url
        }
        return links
    }

    // MARK: Per-message parse cache (deep-perf pass 80-b, STEP 5 upgrade)

    /// Block parse per finished message, keyed by turn id AND content — the
    /// content suffix keeps a regenerated turn from serving a stale parse.
    /// LazyVStack rows re-evaluate on every (re)materialisation — without the
    /// cache, scrolling back through a thread re-parsed per bubble per pass.
    /// NSCache auto-evicts under memory pressure; blocks are style-free data,
    /// so one entry serves both appearances.
    private static let blockCache = NSCache<NSString, BlockBox>()

    private final class BlockBox {
        let blocks: [Block]
        init(_ blocks: [Block]) { self.blocks = blocks }
    }

    private static func cachedBlocks(for message: ChatViewModel.ChatMessage) -> [Block] {
        let key = "\(message.id.uuidString):\(message.content)" as NSString
        if let box = blockCache.object(forKey: key) { return box.blocks }
        let blocks = parseBlocks(message.content)
        blockCache.setObject(BlockBox(blocks), forKey: key)
        return blocks
    }

    // MARK: Live streaming parse cache (TASK 1b)

    /// Incremental block parse for the LIVE bubble. One entry per streaming
    /// message: the previous input + its frozen-prefix parse feed
    /// parseStreamingBlocks so each ~30 Hz flush re-parses only the tail.
    /// Bounded: a handful of live bubbles exist at once; entries for settled
    /// messages simply age out of the ring.
    private static let streamParseCache = StreamParseCache()

    private var streamingBlocks: [Block] {
        Self.streamParseCache.blocks(id: message.id, content: message.content)
    }

    private final class StreamParseCache {
        private struct Entry {
            let input: String
            let blocks: [Block]
        }
        private var entries: [UUID: Entry] = [:]

        func blocks(id: UUID, content: String) -> [Block] {
            if let hit = entries[id], hit.input == content { return hit.blocks }
            let previous = entries[id]
            let parsed = parseStreamingBlocks(
                previousContent: previous?.input ?? "",
                previousBlocks: previous?.blocks ?? [],
                content: content
            )
            if entries.count > 8 { entries.removeAll(keepingCapacity: true) }
            entries[id] = Entry(input: content, blocks: parsed)
            return parsed
        }
    }

    private var bubbleMenu: some View {
        Group {
            Button {
                copyAndConfirm(message.content)
            } label: {
                Label("Copy", systemImage: "doc.on.doc")
            }
            Button(action: onReadAloud) {
                Label(isSpeaking ? "Stop reading" : "Read aloud",
                      systemImage: isSpeaking ? "stop.fill" : "speaker.wave.2")
            }
            Button(action: onRegenerate) {
                Label("Regenerate", systemImage: "arrow.clockwise")
            }
            Button(action: onTranslate) {
                Label("Translate", systemImage: "translate")
            }
            Button(action: onSave) {
                Label("Save", systemImage: "bookmark")
            }
            Button(action: onBranch) {
                Label("Branch new chat", systemImage: "arrow.triangle.branch")
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

// MARK: - Reasoning trace panel (chat-reasoning)

/// The 21st chat-reasoning intent, monochrome: a quiet collapsible card above
/// the streamed answer. Open while the model thinks, auto-collapsed once the
/// answer's first token paints, reopenable. The seconds label is the MEASURED
/// span from the first reasoning delta to the first answer delta, never a
/// timer. Stream-only: renders while this turn is live.
private struct ReasoningTracePanel: View {
    let text: String
    let start: Date?
    let end: Date?

    @State private var expanded = true
    // The answer started: collapse once. A manual re-open after that sticks —
    // the effect only fires on the nil → set transition.
    @State private var autoCollapsed = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button {
                expanded.toggle()
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(Aero.textMuted)
                        .rotationEffect(.degrees(expanded ? 90 : 0))
                    Text(label)
                        .font(Aero.label())
                        .foregroundStyle(Aero.textMuted)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(expanded ? "Collapse reasoning" : "Expand reasoning, \(label)")

            if expanded {
                ScrollView(.vertical, showsIndicators: false) {
                    Text(text)
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted.opacity(0.9))
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .frame(maxHeight: 240)
                .padding(.leading, 28)
                .padding(.trailing, 12)
                .padding(.bottom, 12)
            }
        }
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Aero.raisedSurface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Aero.outline, lineWidth: 1)
        )
        .onAppear {
            if end != nil, !autoCollapsed {
                withAnimation(Aero.chatCurve) { expanded = false }
                autoCollapsed = true
            }
        }
        .onChange(of: end) { newValue in
            if newValue != nil, !autoCollapsed {
                withAnimation(Aero.chatCurve) { expanded = false }
                autoCollapsed = true
            }
        }
    }

    private var label: String {
        guard let start, let end else { return "Thinking…" }
        let seconds = max(0, end.timeIntervalSince(start))
        return String(format: "Thought for %.1f seconds", seconds)
    }
}

// MARK: - Chat Info (reference sheet: "Chat Info")

/// Identity, measured stats, and where the chat lives. Every number is read
/// from the stored messages — the reference's page, without invented data.
private struct ChatInfoSheet: View {
    let title: String
    let messages: [StoredMessage]
    let mode: String
    let local: Bool

    private var span: String {
        guard messages.count > 1 else { return "—" }
        let formatter = ISO8601DateFormatter()
        let stamps = messages.compactMap { formatter.date(from: $0.createdAt) }
        guard let first = stamps.min(), let last = stamps.max() else { return "—" }
        let minutes = max(0, Int(last.timeIntervalSince(first) / 60))
        if minutes < 1 { return "<1m" }
        if minutes < 60 { return "\(minutes)m" }
        if minutes < 1440 { return "\(minutes / 60)h \(minutes % 60)m" }
        return "\(minutes / 1440)d"
    }

    private var modeLabel: String {
        switch mode {
        case "flash": return "Fast"
        case "thinking": return "Reasoning"
        default: return "Auto"
        }
    }

    private var modeDetail: String {
        switch mode {
        case "flash": return "Fast — immediate, concise answers"
        case "thinking": return "Reasoning — spends longer on hard problems"
        default: return "Auto — chooses per question"
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.m) {
                Text("Chat Info").font(Aero.headline()).foregroundStyle(Aero.text)
                Text("Everything this conversation knows about itself.")
                    .font(Aero.caption()).foregroundStyle(Aero.textMuted)

                AeroCard {
                    HStack(spacing: 12) {
                        GsArtwork(art: .bloom, size: 64, label: "Nexly")
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Nexly").font(Aero.label()).foregroundStyle(Aero.text)
                            Text("AI Companion").font(Aero.caption()).foregroundStyle(Aero.textMuted)
                            Text(title)
                                .font(Aero.caption())
                                .foregroundStyle(Aero.textMuted)
                                .lineLimit(2)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(16)
                }

                AeroCard {
                    HStack {
                        stat("\(messages.count)", "Messages")
                        Spacer()
                        stat(span, "Span")
                        Spacer()
                        stat(modeLabel, "Response style")
                    }
                    .padding(16)
                }

                AeroCard {
                    VStack(alignment: .leading, spacing: 14) {
                        row("pencil", "Chat name", title)
                        Divider().overlay(Aero.outline)
                        row("cpu", "Model", local ? "On-device engine" : "Nexly Cloud")
                        Divider().overlay(Aero.outline)
                        row("brain", "Chat mode", modeDetail)
                    }
                    .padding(16)
                }

                Text("Stats come from the messages stored on this device.")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
            }
            .padding(Aero.Spacing.m)
        }
        .background(Aero.background.ignoresSafeArea())
        .presentationDetents([.medium, .large])
    }

    private func stat(_ value: String, _ label: String) -> some View {
        VStack(spacing: 2) {
            Text(value).font(Aero.label()).foregroundStyle(Aero.text)
            Text(label).font(Aero.caption()).foregroundStyle(Aero.textMuted)
        }
    }

    private func row(_ icon: String, _ title: String, _ detail: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Aero.textSecondary)
                .frame(width: 34, height: 34)
                .gsGlass(.sunken, corner: 11)
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(Aero.label()).foregroundStyle(Aero.text)
                Text(detail).font(Aero.caption()).foregroundStyle(Aero.textMuted)
            }
            Spacer(minLength: 0)
        }
    }
}
