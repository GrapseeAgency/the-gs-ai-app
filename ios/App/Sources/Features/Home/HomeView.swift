import SwiftUI

/**
 * AERUO KINETIC home — the content-driven workbench (UI rebuild Step 3).
 * The canvas answers three questions with REAL content only:
 *   1. What can GS do?        — capability tiles, each routed to a real screen
 *   2. What was I doing?      — live Continue/Recent rows from ConversationStore
 *   3. What can GS help with? — starter prompts that prefill the composer
 *
 * Honesty contract: every control performs a real action or real
 * navigation. No invented identity (the greeting falls back to a plain
 * time-of-day line via `AccountStore`), no fabricated model label (the
 * pill resolves the persisted pick against the catalog), no tagline
 * rotation loop, no demo recents (those stay drawer-only), no attach
 * affordance (attachments live in the real composer), and no fabricated
 * category labels (Trending / Popular / For you are gone).
 */
struct HomeView: View {

    var onOpenDrawer: (() -> Void)? = nil
    /// Programmatic route bridge — the composer orb's voice hold hands its
    /// transcript to a brand-new chat through `.chatPrefill`, and the orb's
    /// quick tap opens full voice mode.
    var onRoute: ((AeroRoute) -> Void)? = nil

    // Live content sources — the greeting re-renders when identity changes,
    // the recents re-render when conversations change.
    @ObservedObject private var account = AccountStore.shared
    @ObservedObject private var store = ConversationStore.shared

    @State private var breathe: CGFloat = 1.0

    // Model pill resolution, re-read from UserDefaults each time Home
    // surfaces (returning from the Model Centre refreshes it via onAppear).
    @State private var pillModel: ModelInfo?
    @State private var pillMode: String?

    // Research has no registered AeroRoute — it stays a fullScreenCover
    // workspace (the only one Home still needs; the Vision / Writing / Code /
    // Image tiles died with the chip wall).
    @State private var showResearch = false

    // Voice press-and-hold on the composer orb
    @StateObject private var dictation = VoiceDictation()
    @State private var holdTimer: Task<Void, Never>?
    @State private var holdTriggered = false
    @State private var haloPulse: CGFloat = 1.0
    @State private var orbTouched = false

    // Background lifecycle: a hold that outlives the scene closes its mic
    // session instead of leaving a dead capture behind a suspended UI.
    @Environment(\.scenePhase) private var scenePhase

    // Home recents long-press — same action set as the drawer rows.
    @State private var renameTarget: StoredConversation?
    @State private var renameDraft = ""

    // MARK: Static tile lists (let-constants — no per-render rebuilds, no
    // rotation loops; every symbol is a verified, rendering SF Symbol).

    private struct StarterPrompt: Identifiable {
        let symbol: String
        let label: String
        var id: String { label }
    }

    private let starters: [StarterPrompt] = [
        StarterPrompt(symbol: "doc.text", label: "Summarise a PDF into a brief"),
        StarterPrompt(symbol: "pencil.line", label: "Draft a launch email"),
        StarterPrompt(symbol: "lightbulb", label: "Explain a concept step by step")
    ]

    private enum TileTarget {
        case route(AeroRoute)
        case research
    }

    private struct HomeTile: Identifiable {
        let symbol: String
        let label: String
        let target: TileTarget
        var id: String { label }
    }

    private let toolTiles: [HomeTile] = [
        HomeTile(symbol: "doc.text.magnifyingglass", label: "Research", target: .research),
        HomeTile(symbol: "sparkles", label: "Create", target: .route(.createTab)),
        HomeTile(symbol: "magnifyingglass", label: "Search", target: .route(.search))
    ]

    private let workspaceTiles: [HomeTile] = [
        HomeTile(symbol: "folder", label: "Projects", target: .route(.projects)),
        HomeTile(symbol: "cpu", label: "Assistants", target: .route(.assistants))
    ]

    var body: some View {
        ZStack {
            Aero.background.ignoresSafeArea()

            VStack(spacing: 0) {
                topBar
                    .padding(.horizontal, Aero.Spacing.m)
                ScrollView(showsIndicators: false) {
                    VStack(spacing: Aero.Spacing.xl) {
                        hero
                        composerEntry
                        continuation
                        startersSection
                        capabilitySection
                        disclaimer
                    }
                    .padding(.horizontal, Aero.Spacing.m)
                    .gsContentWidth()
                    .padding(.top, Aero.Spacing.s)
                    .padding(.bottom, Aero.Spacing.l)
                }
            }
        }
        .toolbar(.hidden, for: .navigationBar)
        .onAppear {
            // Reduce animations / Reduce motion: the orb holds its resting
            // frame — the breathe loop is skipped entirely. The voice
            // handoff wiring below is intentionally OUTSIDE this gate: it
            // is behaviour, not decoration, and must exist under reduce
            // motion too.
            if !SettingsStore.shared.animationReduced {
                withAnimation(.easeInOut(duration: 2.4).repeatForever(autoreverses: true)) {
                    breathe = 1.06
                }
            }
            // Voice press-and-hold: a finished hold hands its transcript to
            // a fresh chat composer (blank transcripts quietly do nothing).
            dictation.onFinish = { text in
                guard !text.isEmpty else { return }
                onRoute?(.chatPrefill(text))
            }
            // Real model pill: resolve the persisted pick whenever Home
            // surfaces (first show, and returning from the Model Centre).
            refreshModelPill()
        }
        .onDisappear {
            // Deep-perf pass 80-b: the hero breathe is a repeatForever
            // animation — let it settle when Home leaves the hierarchy so it
            // never keeps ticking behind a pushed screen. onAppear restarts it.
            breathe = 1.0
        }
        .fullScreenCover(isPresented: $showResearch) {
            ResearchView()
        }
        .onChange(of: scenePhase) { phase in
            // Scene went to the background mid-hold: the mic session closes
            // and the halo dissolves — the same ON_STOP stop Android does.
            if phase == .background {
                dictation.suspendForBackground()
            }
        }
        .alert("Rename chat", isPresented: Binding(
            get: { renameTarget != nil },
            set: { if !$0 { renameTarget = nil } }
        )) {
            TextField("Chat name", text: $renameDraft)
            Button("Rename") {
                let trimmed = renameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
                if let target = renameTarget, !trimmed.isEmpty {
                    store.rename(id: target.id, to: trimmed)
                    sync(target.id, title: trimmed)
                    GSHaptics.success()
                }
                renameTarget = nil
            }
            Button("Cancel", role: .cancel) { renameTarget = nil }
        } message: {
            Text("Give this conversation a name you'll recognise.")
        }
    }

    // MARK: Top bar — menu · real model pill · new chat

    private var topBar: some View {
        HStack(spacing: Aero.Spacing.s) {
            Button {
                onOpenDrawer?()
            } label: {
                circleIcon("line.3.horizontal")
            }
            .buttonStyle(KineticPressStyle())
            .accessibilityLabel("Open menu")

            Spacer()

            NavigationLink(value: AeroRoute.models) {
                HStack(spacing: 6) {
                    Circle()
                        .fill(LinearGradient(
                            colors: Aero.aurora,
                            startPoint: .topLeading, endPoint: .bottomTrailing))
                        .frame(width: 8, height: 8)
                    Text(modelPillText)
                        .font(Aero.label())
                        .foregroundColor(Aero.text)
                }
                .padding(.horizontal, Aero.Spacing.m)
                .padding(.vertical, 10)
                .background(Capsule().fill(Aero.raisedSurface))
            }
            .buttonStyle(KineticPressStyle())

            Spacer()

            NavigationLink(value: AeroRoute.chat(nil)) {
                circleIcon("plus")
            }
            .buttonStyle(KineticPressStyle())
            .accessibilityLabel("New chat")
        }
        .padding(.vertical, Aero.Spacing.s)
    }

    private func circleIcon(_ symbol: String) -> some View {
        ZStack {
            Circle().fill(Aero.raisedSurface).frame(width: 44, height: 44)
            Image(systemName: symbol)
                .font(.system(size: 16, weight: .medium))
                .foregroundColor(Aero.text)
        }
    }

    /// The pill always shows the REAL persisted pick: the catalog name for
    /// UserDefaults "gs.models.defaultId", plus a " · mode" suffix ONLY when
    /// the stored "gs.models.mode" is one that model actually offers
    /// (gs-balanced supports ["Fast", "Balanced"] — the fabricated
    /// "GS Balanced · High" is gone). An unknown id falls back to the
    /// catalog's flagged default — the same "server default" semantics the
    /// chat send path applies — and the suffix disappears rather than
    /// inventing a tier.
    private var modelPillText: String {
        let model = pillModel ?? ModelInfo.catalog.first { $0.isDefault }
        guard let model else { return "" }
        if let mode = pillMode, model.modes.contains(mode) {
            return "\(model.name) · \(mode)"
        }
        return model.name
    }

    private func refreshModelPill() {
        let storedID = UserDefaults.standard.string(forKey: "gs.models.defaultId") ?? "gs-balanced"
        pillModel = ModelInfo.catalog.first { $0.id == storedID }
        // The Model Centre treats an unset mode as "Balanced" — mirror it so
        // the pill agrees with what the Model Centre displays.
        pillMode = UserDefaults.standard.string(forKey: "gs.models.mode") ?? "Balanced"
    }

    // MARK: Hero — orb · greeting (identity, never invention)

    /// The old "Upgrade plan" pill was deleted with the Step-3 rebuild —
    /// billing stays reachable through the drawer's Account header Pro chip
    /// (routes to .billing).
    private var hero: some View {
        VStack(spacing: Aero.Spacing.s) {
            ZStack {
                Circle()
                    .fill(LinearGradient(
                        colors: Aero.aurora,
                        startPoint: .topLeading, endPoint: .bottomTrailing))
                    .frame(width: 84, height: 84)
                    .scaleEffect(breathe)
                Circle()
                    .fill(Aero.background.opacity(0.35))
                    .frame(width: 66, height: 66)
                Image(systemName: "sparkles")
                    .font(.system(size: 20))
                    .foregroundColor(Aero.text)
            }
            .padding(.bottom, Aero.Spacing.m)

            Text(greeting)
                .font(Aero.displayTitle())
                .foregroundColor(Aero.text)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)

            // One static quiet line — no rotation loop behind it.
            Text("What would you like to work on?")
                .font(Aero.body())
                .foregroundColor(Aero.textSecondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
    }

    /// Time-of-day + the user's actual first name (AccountStore). The
    /// neutral fallback is the bare line — never "Admin", never an invented
    /// name.
    private var greeting: String {
        let hour = Calendar.current.component(.hour, from: Date())
        let name = account.firstName
        switch hour {
        case 23, 0...4:
            return name.isEmpty ? "Up late?" : "Up late, \(name)?"
        case 5...11:
            return name.isEmpty ? "Good morning" : "Good morning, \(name)"
        case 12...17:
            return name.isEmpty ? "Good afternoon" : "Good afternoon, \(name)"
        default:
            return name.isEmpty ? "Good evening" : "Good evening, \(name)"
        }
    }

    // MARK: Composer entry — the one dominant action

    /// Honest composer entry: raised surface, real placeholder, tap opens
    /// the real composer (.chat(nil)). The old fake attach (+) affordance is
    /// gone — attachments live in the real composer. Mic tap opens full
    /// voice mode; the orb carries the verbatim press-and-hold machinery.
    private var composerEntry: some View {
        HStack(spacing: Aero.Spacing.s) {
            NavigationLink(value: AeroRoute.chat(nil)) {
                Text(dictation.isListening
                    ? (dictation.transcript.isEmpty ? "Listening…" : dictation.transcript)
                    : "Ask GS anything…")
                    .font(Aero.body())
                    .foregroundColor(dictation.isListening ? Aero.text : Aero.textPlaceholder)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, Aero.Spacing.s)
                    .contentShape(Rectangle())
            }
            .buttonStyle(KineticPressStyle())
            .disabled(dictation.isListening)

            NavigationLink(value: AeroRoute.voice) {
                Image(systemName: "mic")
                    .font(.system(size: 16))
                    .foregroundColor(Aero.textSecondary)
                    .frame(width: 42, height: 42)
                    .contentShape(Rectangle())
            }
            .buttonStyle(KineticPressStyle())
            .accessibilityLabel("Voice input")

            voiceHoldOrb
        }
        .padding(Aero.Spacing.s)
        .background(RoundedRectangle(cornerRadius: Aero.Radius.input).fill(Aero.raisedSurface))
    }

    // MARK: Continuation — "What was I doing?"

    /// Live data only, straight from ConversationStore with the SAME
    /// filtering/ordering the drawer recents use (activeConversations:
    /// archived hidden, pins float, newest first) — up to 3 rows total, and
    /// NOTHING at all when the store is empty (no demo fallback here; the
    /// demo recents stay drawer-only so starter prompts can breathe).
    @ViewBuilder
    private var continuation: some View {
        if let latest = visibleConversations.first {
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                sectionLabel("Continue")
                conversationRow(latest)
                allChatsLink
            }
            let older = Array(visibleConversations.dropFirst().prefix(2))
            if !older.isEmpty {
                VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                    sectionLabel("Recent")
                    ForEach(older) { conversation in
                        conversationRow(conversation)
                    }
                }
            }
        }
    }

    private var visibleConversations: [StoredConversation] {
        Array(store.activeConversations.prefix(3))
    }

    private var allChatsLink: some View {
        NavigationLink(value: AeroRoute.chats) {
            HStack(spacing: Aero.Spacing.xs) {
                Text("All chats")
                    .font(Aero.label())
                    .foregroundColor(Aero.textSecondary)
                Image(systemName: "chevron.right")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundColor(Aero.textSecondary)
            }
            .padding(.horizontal, Aero.Spacing.s)
            .padding(.vertical, 10)
            .contentShape(Rectangle())
        }
        .buttonStyle(KineticPressStyle())
    }

    /// One clear row: title + relative time + optional model indicator
    /// (catalog name for the stored modelId; unknown ids are omitted, never
    /// guessed). Tap opens the conversation; long-press offers the same
    /// action set as the drawer rows.
    private func conversationRow(_ conversation: StoredConversation) -> some View {
        NavigationLink(value: AeroRoute.chat(conversation.id)) {
            HStack(spacing: Aero.Spacing.s) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(gsConversationTitle(conversation.title))
                        .font(Aero.body())
                        .foregroundColor(Aero.text)
                        .lineLimit(1)
                    Text(rowMeta(conversation))
                        .font(Aero.caption())
                        .foregroundColor(Aero.textSecondary)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
                if conversation.pinned {
                    Image(systemName: "star.fill")
                        .font(.system(size: 10))
                        .foregroundColor(Aero.accent)
                }
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.vertical, 11)
            .background(RoundedRectangle(cornerRadius: Aero.Radius.md).fill(Aero.raisedSurface))
            .contentShape(Rectangle())
        }
        .buttonStyle(KineticPressStyle())
        .contextMenu {
            conversationMenu(conversation)
        }
    }

    /// "2h ago · GS Balanced" — empty segments are dropped, so an
    /// unparseable stamp or unknown model shrinks the line honestly.
    private func rowMeta(_ conversation: StoredConversation) -> String {
        var parts = [GSFormatters.relativeTime(from: conversation.updatedAt)]
        if let model = ModelInfo.catalog.first(where: { $0.id == conversation.modelId }) {
            parts.append(model.name)
        }
        return parts.filter { !$0.isEmpty }.joined(separator: " · ")
    }

    /// The drawer's four actions, applied against the same store — compact
    /// inline construction (the drawer's menu is private view code, not
    /// worth a shared extraction for two surfaces).
    @ViewBuilder
    private func conversationMenu(_ conversation: StoredConversation) -> some View {
        Button {
            GSHaptics.success()
            let target = !conversation.pinned
            store.setPinned(id: conversation.id, target)
            GSHaptics.success()
            sync(conversation.id, pinned: target)
        } label: {
            Label(conversation.pinned ? "Unpin" : "Pin to top", systemImage: "pin")
        }
        Button {
            renameTarget = conversation
            renameDraft = conversation.title
        } label: {
            Label("Rename…", systemImage: "pencil")
        }
        Button {
            GSHaptics.success()
            store.setArchived(id: conversation.id, true)
            GSHaptics.success()
            sync(conversation.id, archived: true)
        } label: {
            Label("Archive", systemImage: "archivebox")
        }
        Button(role: .destructive) {
            GSHaptics.warning()
            store.delete(id: conversation.id)
            GSHaptics.warning()
            syncDelete(conversation.id)
        } label: {
            Label("Delete", systemImage: "trash")
        }
    }

    // Local-first: the store already changed; demo/local ids stay local.
    private func sync(_ id: String, pinned: Bool? = nil, archived: Bool? = nil, title: String? = nil) {
        guard !id.hasPrefix("demo-"), !id.hasPrefix("local-") else { return }
        Task { try? await APIClient.shared.updateConversation(id: id, pinned: pinned, archived: archived, title: title) }
    }

    private func syncDelete(_ id: String) {
        guard !id.hasPrefix("demo-"), !id.hasPrefix("local-") else { return }
        Task { try? await APIClient.shared.deleteConversation(id: id) }
    }

    // MARK: Starter prompts — "What can GS help with?"

    private var startersSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            sectionLabel("Start a new thread")
            ForEach(starters) { starter in
                promptRow(starter)
            }
        }
    }

    /// Each prompt prefills the real composer — the label IS the payload.
    private func promptRow(_ starter: StarterPrompt) -> some View {
        NavigationLink(value: AeroRoute.chatPrefill(starter.label)) {
            HStack(spacing: Aero.Spacing.m) {
                ZStack {
                    Circle().fill(Aero.raisedSurface).frame(width: 38, height: 38)
                    Image(systemName: starter.symbol)
                        .font(.system(size: 14))
                        .foregroundColor(Aero.text)
                }
                Text(starter.label)
                    .font(Aero.body())
                    .foregroundColor(Aero.text)
                Spacer()
            }
            .padding(.horizontal, 6)
            .padding(.vertical, 6)
            .contentShape(Rectangle())
        }
        .buttonStyle(KineticPressStyle())
    }

    // MARK: Capability discovery — honest, route-backed, max 5 tiles

    /// One compact section split Tools / Workspaces. Every tile is a REAL
    /// destination: registered AeroRoute cases where they exist; Research
    /// keeps its (real) fullScreenCover because it has no registered route.
    /// No Trending / Popular / For you labels, and no LiveUpdate equivalent —
    /// that is an Android-only real feature and is not faked here.
    private var capabilitySection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.l) {
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                sectionLabel("Tools")
                ForEach(toolTiles) { tile in
                    tileRow(tile)
                }
            }
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                sectionLabel("Workspaces")
                ForEach(workspaceTiles) { tile in
                    tileRow(tile)
                }
            }
        }
    }

    private func tileRow(_ tile: HomeTile) -> some View {
        Group {
            switch tile.target {
            case .research:
                Button {
                    showResearch = true
                } label: {
                    tileLabel(tile)
                }
            case .route(let route):
                NavigationLink(value: route) {
                    tileLabel(tile)
                }
            }
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityLabel(tile.label)
    }

    private func tileLabel(_ tile: HomeTile) -> some View {
        HStack(spacing: Aero.Spacing.m) {
            ZStack {
                Circle().fill(Aero.raisedSurface).frame(width: 38, height: 38)
                Image(systemName: tile.symbol)
                    .font(.system(size: 14))
                    .foregroundColor(Aero.text)
            }
            Text(tile.label)
                .font(Aero.body())
                .foregroundColor(Aero.text)
            Spacer()
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 6)
        .contentShape(Rectangle())
    }

    // MARK: Footer

    private var disclaimer: some View {
        Text("GS can make mistakes — double-check important info.")
            .font(Aero.caption())
            .foregroundColor(Aero.textSecondary)
            .frame(maxWidth: .infinity)
            .padding(.bottom, Aero.Spacing.s)
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text.uppercased())
            .font(Aero.label())
            .foregroundColor(Aero.textSecondary)
            .padding(.horizontal, 4)
            .accessibilityAddTraits(.isHeader)
    }

    // MARK: Voice press-and-hold — the composer affordance (verbatim)

    /// Aurora orb: hold past the threshold to dictate (live transcript in
    /// the composer line), quick tap still opens full voice mode. Every
    /// failure path dissolves quietly — nothing is ever shown as an error.
    private var voiceHoldOrb: some View {
        ZStack {
            if dictation.isListening {
                Circle()
                    .fill(LinearGradient(
                        colors: Aero.aurora,
                        startPoint: .topLeading, endPoint: .bottomTrailing))
                    .frame(width: 46, height: 46)
                    .scaleEffect(haloPulse)
                    .opacity(0.45)
            }
            ZStack {
                Circle()
                    .fill(LinearGradient(
                        colors: Aero.aurora,
                        startPoint: .topLeading, endPoint: .bottomTrailing))
                    .frame(width: 44, height: 44)
                Image(systemName: "waveform")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(Aero.text)
            }
            .scaleEffect(orbTouched ? 0.94 : 1)
            .animation(Aero.motion(Aero.snappy), value: orbTouched)
        }
        .frame(width: 62, height: 62)
        .contentShape(Circle())
        .gesture(voiceHoldGesture)
        // VoiceOver parity (Task 85-e I4): the hold gesture is invisible to
        // assistive tech without an explicit element — one button with a
        // named action that drives the same dictation state the finger does.
        .accessibilityElement()
        .accessibilityLabel("Voice input — hold to dictate, tap for voice mode")
        .accessibilityAddTraits(.isButton)
        .accessibilityHint("Activates voice dictation")
        .accessibilityAction(named: dictation.isListening ? "Stop listening" : "Start listening") {
            if dictation.isListening {
                dictation.end()
            } else {
                GSHaptics.press()
                dictation.begin()
            }
        }
    }

    private var voiceHoldGesture: some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .onChanged { _ in
                if !orbTouched {
                    orbTouched = true   // pressed state lands with the finger
                }
                guard holdTimer == nil else { return }
                holdTriggered = false
                holdTimer = Task {
                    try? await Task.sleep(nanoseconds: 280_000_000)
                    guard !Task.isCancelled else { return }
                    await MainActor.run {
                        holdTriggered = true
                        // Hold threshold crossed — the mic is genuinely opening.
                        GSHaptics.press()
                        if !SettingsStore.shared.animationReduced {
                            withAnimation(.easeInOut(duration: 0.85).repeatForever(autoreverses: true)) {
                                haloPulse = 1.42
                            }
                        }
                        dictation.begin()
                    }
                }
            }
            .onEnded { _ in
                holdTimer?.cancel()
                holdTimer = nil
                orbTouched = false
                if holdTriggered {
                    dictation.end()
                } else {
                    GSHaptics.tap()   // quick tap — full voice mode
                    onRoute?(.voice)
                }
                holdTriggered = false
                haloPulse = 1.0
            }
    }
}
