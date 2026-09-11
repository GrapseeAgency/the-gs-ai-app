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

    // Read-aloud — on-device speech, silent when the device has no voice.
    @StateObject private var speech = SpeechPlayer()

    // Task 85-e: platform wiring.
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @FocusState private var composerFocused: Bool
    @ObservedObject private var conversations = ConversationStore.shared
    // Voice entry (Step 4): the same shared Router every pushed surface reads
    // (ChatsListView, VoiceView, AssistantDetailView, …) — the composer mic
    // appends .voice onto the stack this screen was pushed onto.
    @EnvironmentObject private var router: Router
    // Offline state (Step 4): one shared NWPathMonitor for the whole process.
    @ObservedObject private var network = NetworkMonitor.shared

    // Model control (Phase 3): the header chip is a SECONDARY utility — tier
    // word only (Fast / Everyday / Best), no product/model name, NO colour
    // dot. Tapping it opens the tiered "Choose a model" sheet (same shelves
    // as the Model Centre). Re-read on appear (return from Model Centre) and
    // after a sheet pick.
    @State private var showingModelPicker = false
    @State private var pillModel: ModelInfo?

    // PHASE 3 workspace model: the app-root instance of this screen carries
    // the shell chrome — the drawer menu replaces the back button, and "+"
    // resets to a fresh workspace via the router. Pushed instances (opened
    // from the drawer's recents) keep the standard back affordance.
    var isWorkspaceRoot: Bool = false
    var onOpenDrawer: (() -> Void)? = nil
    var onNewChat: (() -> Void)? = nil

    init(conversationID: String?, prefill: String? = nil, autoSendPrefill: Bool = false) {
        _vm = StateObject(wrappedValue: ChatViewModel(conversationID: conversationID))
        self.prefill = prefill
        self.autoSendPrefill = autoSendPrefill
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

            if network.isOffline {
                offlinePill
            }

            transcript

            Divider().overlay(Aero.outline)
            composerZone
        }
        .background(Aero.background.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .navigationTitle(conversationTitle)
        .navigationBarBackButtonHidden(isWorkspaceRoot)
        .toolbar {
            // Workspace root: the drawer menu lives where back would be —
            // this screen IS the app, there is nothing to go back to.
            ToolbarItemGroup(placement: .navigationBarLeading) {
                if isWorkspaceRoot, let openDrawer = onOpenDrawer {
                    Button(action: openDrawer) {
                        Image(systemName: "line.3.horizontal")
                    }
                    .accessibilityLabel("Open menu")
                }
            }
            // Phase 3: model chip BEFORE search — a bordered neutral pill with
            // the consumer tier WORD only. No aurora dot, no model name, no
            // metadata — a quiet secondary utility the ordinary user can
            // ignore forever. "+" resets to a fresh workspace (root only).
            ToolbarItemGroup(placement: .navigationBarTrailing) {
                Button {
                    showingModelPicker = true
                } label: {
                    Text(modelPillText)
                        .font(Aero.label())
                        .foregroundStyle(Aero.text)
                        .lineLimit(1)
                        .padding(.horizontal, Aero.Spacing.control)
                        .padding(.vertical, 7)
                        .background(Capsule().stroke(Aero.outline, lineWidth: 1))
                }
                .buttonStyle(KineticPressStyle())
                .accessibilityLabel("Model \(modelPillText). Change model")

                if let newChat = onNewChat {
                    Button {
                        newChat()
                    } label: {
                        Image(systemName: "square.and.pencil")
                    }
                    .accessibilityLabel("New chat")
                }

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
            refreshModelPill()
            if let prefill, vm.draft.isEmpty, !vm.isStreaming {
                vm.draft = prefill
            } else if let id = vm.conversationID, vm.draft.isEmpty, !vm.isStreaming {
                vm.draft = UserDefaults.standard.string(forKey: "draft_\(id)") ?? ""
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
            AttachmentSheetView { option in
                showToast(attachmentMessage(for: option))
            }
        }
        .sheet(isPresented: $showingModelPicker) {
            modelPickerSheet
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
                withAnimation(Aero.gentle) {
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
            MessageBubble(
                message: message,
                isSpeaking: speech.speakingMessageID == message.id.uuidString,
                editEnabled: !vm.isStreaming && editingIndex == nil,
                highlight: message.id == activeMatch,
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
            .id(message.id)
        }
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

    // MARK: Composer (Step 4 unified zone)

    /// One composer zone replaces the old three-row stack (attachRow above
    /// the bar + a separate streamingBar above the divider): a generating
    /// line and the attach toast live here, then a single input row —
    /// [+] [expanding field with the send/stop slot] [mic]. During a stream
    /// the send slot becomes Stop (real vm.stop()) and the field stays live:
    /// the draft remains editable, vm.send() itself gates on !isStreaming,
    /// so nothing sends and nothing stops by accident.
    private var composerZone: some View {
        VStack(spacing: Aero.Spacing.s) {
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
            if vm.isStreaming {
                HStack(spacing: Aero.Spacing.s) {
                    AuroraIndicator()
                    Text("Generating…")
                        .font(Aero.label())
                        .foregroundStyle(Aero.textMuted)
                    Spacer()
                }
                .accessibilityElement(children: .combine)
            }
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

                AeroInputBar(
                    text: $vm.draft,
                    action: {
                        userIsReading = false
                        sendAndClearDraft()
                    },
                    focus: $composerFocused,
                    busy: vm.isStreaming,
                    onStop: { vm.stop() })

                Button {
                    router.path.append(.voice)
                } label: {
                    Image(systemName: "mic")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Aero.text)
                        .frame(width: 30, height: 30)
                        .background(Circle().fill(Aero.container))
                        .overlay(Circle().stroke(Aero.outline, lineWidth: 1))
                }
                .buttonStyle(KineticPressStyle())
                .accessibilityLabel("Voice input")
            }
        }
        .padding(.horizontal, Aero.Spacing.m)
        .padding(.vertical, Aero.Spacing.s)
        .frame(maxWidth: columnMaxWidth)
        .background(Aero.surface.ignoresSafeArea(edges: .bottom))
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
            Text("Offline — replies come from the on-device responder until you reconnect.")
                .font(Aero.label())
                .foregroundStyle(Aero.textMuted)
                .multilineTextAlignment(.leading)
        }
        .foregroundStyle(Aero.textMuted)
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: Aero.Radius.sm).fill(Aero.elevatedSurface))
        .padding(.horizontal, Aero.Spacing.m)
        .padding(.top, Aero.Spacing.s)
        .accessibilityElement(children: .combine)
    }

    // MARK: Model control (Phase 2)

    /// The chip always shows the consumer tier WORD for the REAL persisted
    /// pick — Fast / Everyday / Best (never a model name, never metadata).
    /// Model education stays opt-in via the sheet's "About models".
    private var modelPillText: String {
        let model = pillModel ?? ModelInfo.catalog.first { $0.isDefault }
        return Self.consumerTierWord(model?.speedTier)
    }

    /// speedTier → consumer word. "Deep" reads as "Best" (matches the
    /// picker's tier shelves); everything balanced is "Everyday".
    static func consumerTierWord(_ speedTier: String?) -> String {
        switch speedTier {
        case "Fast": return "Fast"
        case "Deep": return "Best"
        case .some: return "Everyday"
        case nil: return "Auto"
        }
    }

    private var activeModelID: String {
        pillModel?.id ?? ModelInfo.catalog.first { $0.isDefault }?.id ?? "gs-balanced"
    }

    private func refreshModelPill() {
        let storedID = UserDefaults.standard.string(forKey: "gs.models.defaultId") ?? "gs-balanced"
        pillModel = ModelInfo.catalog.first { $0.id == storedID }
    }

    /// Sheet pick: writes the SAME key Model Centre writes and refreshes the
    /// chip immediately. ChatViewModel resolves the id per send
    /// (resolvePreferredModelID runs inside beginStreaming), so the new model
    /// governs from the next message — never a mid-stream switch.
    private func selectModel(_ model: ModelInfo) {
        UserDefaults.standard.set(model.id, forKey: "gs.models.defaultId")
        refreshModelPill()
        showingModelPicker = false
    }

    /// The consumer model sheet: the 3 tiers EXACTLY as the Model Centre
    /// shelves them (ModelInfo.consumerTiers — shared, never duplicated),
    /// name + tagline + checkmark on the active pick, and a quiet
    /// "About models" footer into the Model Centre. No raw 8-name overflow,
    /// no context/speed figures outside the Model Centre's Advanced details.
    private var modelPickerSheet: some View {
        AeroSheetShell(title: "Choose a model") {
            ScrollView {
                VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                    Text("Applies from your next message — a reply already streaming keeps the model it started with.")
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                    ForEach(ModelInfo.consumerTiers) { tier in
                        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                            Text(tier.title)
                                .font(Aero.label())
                                .foregroundStyle(Aero.textMuted)
                            ForEach(tier.models) { model in
                                TierModelRow(
                                    model: model,
                                    isActive: model.id == activeModelID,
                                    action: { selectModel(model) })
                            }
                        }
                    }
                    Button {
                        showingModelPicker = false
                        router.path.append(.models)
                    } label: {
                        Label("About models", systemImage: "info.circle")
                            .font(Aero.label())
                            .foregroundStyle(Aero.textMuted)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, Aero.Spacing.s)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(KineticPressStyle())
                    .accessibilityHint("Opens the Model Centre")
                }
            }
        }
        .presentationDetents([.medium, .large])
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

    // MARK: Workspace empty state (Phase 3)

    /// The starters — immediate composer suggestions, mirroring the Android
    /// workspace exactly. No navigation, no giant cards: a tap seeds the
    /// composer and raises the keyboard; the reader stays in control of send.
    private let starters = [
        "Draft a crisp product update email for our beta testers",
        "Explain Kotlin coroutines like I'm a senior Java developer",
        "Plan a three-day Tokyo itinerary focused on design studios"
    ]

    /// Time-of-day greeting — the empty state's whole identity. No orb, no
    /// hero, no aurora: a quiet line of text.
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
            Text(workspaceGreeting)
                .font(Aero.headline())
                .foregroundStyle(Aero.text)
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

// MARK: - Model sheet row (Phase 2)

/// One tier-sheet row — display name + tagline + checkmark on the active
/// pick. No capability/context line: technical detail lives in the Model
/// Centre's per-model "Advanced details" only.
private struct TierModelRow: View {
    let model: ModelInfo
    let isActive: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(model.name)
                        .font(Aero.title())
                        .foregroundStyle(Aero.text)
                    Text(model.tagline)
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                }
                Spacer()
                if isActive {
                    Image(systemName: "checkmark")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Aero.accent)
                }
            }
            .padding(12)
            .background(
                RoundedRectangle(cornerRadius: Aero.Radius.md)
                    .fill(isActive ? AnyShapeStyle(Aero.accentSoft) : AnyShapeStyle(Aero.raisedSurface)))
            .contentShape(Rectangle())
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityLabel("\(model.name). \(model.tagline)")
        .accessibilityAddTraits(isActive ? [.isSelected] : [])
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
                    BlocksView(blocks: finalizedBlocks)
                    if !message.content.isEmpty {
                        actionRow
                    }
                }
            }
            // Step 4 de-card: assistant prose sits straight on the canvas
            // background at the transcript's reading column (no 320pt cap, no
            // card padding/fill/stroke) — chrome stays only on content that
            // earns it (code blocks). The find-in-chat tint remains as a soft
            // accent wash instead of a permanent outline.
            .background {
                if highlight {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Aero.accentSoft)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Aero.accent, lineWidth: 1))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
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

    /// Finalized content → ordered blocks (STEP 5). Empty content renders the
    /// quiet ellipsis paragraph; everything else parses once and caches.
    private var finalizedBlocks: [Block] {
        if message.content.isEmpty {
            return [.paragraph(spans: [.text("…")], sourceStart: 0)]
        }
        return Self.cachedBlocks(for: message)
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

    /// Step 4 hierarchy: the visible row carries the three high-frequency
    /// actions (Copy · Read aloud/Stop · Regenerate); everything else lives in
    /// the long-press menu — Translate (real streaming sheet), Save to
    /// Library, Branch new chat, Share. No dead entries: every item fires a
    /// real closure verified against ChatDetailView's wiring. Edit is a
    /// USER-turn action and stays on the user bubble (its row + menu).
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
            Button(action: onReadAloud) {
                Image(systemName: isSpeaking ? "stop.fill" : "speaker.wave.2")
                    .foregroundStyle(isSpeaking ? Aero.accent : Aero.textMuted)
            }
            .accessibilityLabel(isSpeaking ? "Stop reading aloud" : "Read reply aloud")
            Button(action: onRegenerate) {
                Image(systemName: "arrow.clockwise")
            }
            .accessibilityLabel("Regenerate reply")
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
