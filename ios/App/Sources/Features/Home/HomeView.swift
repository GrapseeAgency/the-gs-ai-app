import SwiftUI

/**
 * GS home — the conversation-first launch surface (Phase 2 hierarchy).
 *
 * Mental model: "Open app → talk to AI → get answer → continue conversation."
 * One primary element — the REAL inline composer pinned at the bottom —
 * supported by a compact identity moment and quiet real content:
 *   1. Compact hero   — small orb + time-of-day greeting (real first name
 *                       via AccountStore; neutral fallback, never invented)
 *                       + one quiet subline.
 *   2. Continue       — up to 3 REAL recent conversations; the section
 *                       disappears entirely when there is nothing to show.
 *   3. Starters       — up to 3 quiet chips that fill the inline composer
 *                       and raise the keyboard (they never navigate away).
 *   4. Pinned composer— a real field ("Ask GS anything…") that grows to
 *                       ~4 lines, send (enabled only with text → opens a NEW
 *                       chat and auto-sends via .chatAutoSend), mic (tap =
 *                       Voice screen; hold = the existing dictation mechanic,
 *                       transcript lands in THIS field), disclaimer line.
 *
 * Honesty contract: every control performs a real action or real navigation.
 * No model pill (model choice lives quietly in chat), no tool cards, no
 * fabricated content, no competing invitation systems beyond Continue +
 * starters. The orb survives only as a small, reduce-motion-gated identity
 * mark — the existing breathing loop, demoted in size.
 */
struct HomeView: View {

    var onOpenDrawer: (() -> Void)? = nil
    /// Programmatic route bridge — the composer's send hands the typed text
    /// to a brand-new chat through `.chatAutoSend`, and the mic's quick tap
    /// opens full voice mode.
    var onRoute: ((AeroRoute) -> Void)? = nil

    // Live content sources — the greeting re-renders when identity changes,
    // the recents re-render when conversations change.
    @ObservedObject private var account = AccountStore.shared
    @ObservedObject private var store = ConversationStore.shared

    @State private var breathe: CGFloat = 1.0

    // The inline composer — the one primary element of the screen.
    @State private var composerText = ""
    @FocusState private var composerFocused: Bool

    // Voice press-and-hold on the composer mic (mechanics unchanged from the
    // Step-3 orb; the transcript now lands in the inline composer field).
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

    // MARK: Starters (quiet chips — the label IS the payload)

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

    var body: some View {
        ZStack {
            Aero.background.ignoresSafeArea()

            VStack(spacing: 0) {
                topBar
                    .padding(.horizontal, Aero.Spacing.m)
                ScrollView(showsIndicators: false) {
                    VStack(spacing: Aero.Spacing.xl) {
                        hero
                        continuation
                        startersSection
                    }
                    .padding(.horizontal, Aero.Spacing.m)
                    .gsContentWidth()
                    .padding(.top, Aero.Spacing.s)
                    .padding(.bottom, Aero.Spacing.l)
                }
                Divider().overlay(Aero.outline)
                pinnedComposer
                    .padding(.horizontal, Aero.Spacing.m)
                    .padding(.top, Aero.Spacing.s)
                    .gsContentWidth()
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
            // Voice press-and-hold: a finished hold fills the INLINE composer
            // field with its transcript and raises the keyboard (blank
            // transcripts quietly do nothing).
            dictation.onFinish = { text in
                guard !text.isEmpty else { return }
                composerText = text
                composerFocused = true
            }
        }
        .onDisappear {
            // Deep-perf pass 80-b: the hero breathe is a repeatForever
            // animation — let it settle when Home leaves the hierarchy so it
            // never keeps ticking behind a pushed screen. onAppear restarts it.
            breathe = 1.0
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

    // MARK: Top bar — menu · brand mark · new chat

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

            // Simple brand mark — no model name, no mode suffix. Model
            // choice lives quietly in the chat header, not on the front door.
            Text("GS")
                .font(Aero.headline())
                .foregroundColor(Aero.text)
                .accessibilityAddTraits(.isHeader)

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

    // MARK: Compact hero — small orb · greeting (identity, never invention)

    /// The orb is demoted to a small identity mark: the existing aurora
    /// treatment and breathing loop at ~36pt, reduce-motion gated.
    private var hero: some View {
        VStack(spacing: Aero.Spacing.s) {
            ZStack {
                Circle()
                    .fill(LinearGradient(
                        colors: Aero.aurora,
                        startPoint: .topLeading, endPoint: .bottomTrailing))
                    .frame(width: 36, height: 36)
                    .scaleEffect(breathe)
                Circle()
                    .fill(Aero.background.opacity(0.35))
                    .frame(width: 26, height: 26)
                Image(systemName: "sparkles")
                    .font(.system(size: 12))
                    .foregroundColor(Aero.text)
            }
            .padding(.bottom, Aero.Spacing.xs)

            Text(greeting)
                .font(Aero.headline())
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
        .padding(.top, Aero.Spacing.l)
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

    // MARK: Continuation — "What was I doing?"

    /// Live data only, straight from ConversationStore with the SAME
    /// filtering/ordering the drawer recents use (activeConversations:
    /// archived hidden, pins float, newest first) — up to 3 rows total, and
    /// NOTHING at all when the store is empty (no demo fallback anywhere).
    @ViewBuilder
    private var continuation: some View {
        if !visibleConversations.isEmpty {
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                sectionLabel("Continue")
                ForEach(visibleConversations) { conversation in
                    conversationRow(conversation)
                }
            }
        }
    }

    private var visibleConversations: [StoredConversation] {
        Array(store.activeConversations.prefix(3))
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

    // MARK: Starters — quiet chips that seed the inline composer

    /// Tapping a starter NEVER navigates away: the text lands in the pinned
    /// composer field and the keyboard rises — the reader stays on Home and
    /// sends when ready.
    private var startersSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            sectionLabel("Try asking")
            ForEach(starters) { starter in
                Button {
                    GSHaptics.select()
                    composerText = starter.label
                    composerFocused = true
                } label: {
                    HStack(spacing: Aero.Spacing.s) {
                        Image(systemName: starter.symbol)
                            .font(.system(size: 13))
                            .foregroundColor(Aero.textSecondary)
                        Text(starter.label)
                            .font(Aero.body())
                            .foregroundColor(Aero.text)
                            .lineLimit(1)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, Aero.Spacing.m)
                    .padding(.vertical, 10)
                    .background(Capsule().fill(Aero.raisedSurface))
                    .contentShape(Rectangle())
                }
                .buttonStyle(KineticPressStyle())
                .accessibilityLabel("\(starter.label). Fills the composer below")
            }
        }
    }

    // MARK: Pinned inline composer — the primary element

    private var pinnedComposer: some View {
        VStack(spacing: Aero.Spacing.xs) {
            HStack(alignment: .bottom, spacing: Aero.Spacing.s) {
                if dictation.isListening {
                    // Live transcript while the hold is active — the field
                    // fills for real, so the reader watches their words land.
                    Text(dictation.transcript.isEmpty ? "Listening…" : dictation.transcript)
                        .font(Aero.body())
                        .foregroundColor(dictation.transcript.isEmpty ? Aero.textPlaceholder : Aero.text)
                        .multilineTextAlignment(.leading)
                        .lineLimit(4)
                        .frame(maxWidth: .infinity, minHeight: 24, alignment: .leading)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)
                        .background(Capsule().fill(Aero.inputSurface))
                        .overlay(Capsule().stroke(Aero.outline, lineWidth: 1))
                        .accessibilityLabel("Dictating")
                } else {
                    TextField("Ask GS anything…", text: $composerText, axis: .vertical)
                        .font(Aero.body())
                        .foregroundColor(Aero.text)
                        .lineLimit(1...4)
                        .focused($composerFocused)
                        .submitLabel(.send)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)
                        .background(Capsule().fill(Aero.inputSurface))
                        .overlay(Capsule().stroke(Aero.outline, lineWidth: 1))
                        .onSubmit {
                            // Mirror the app's Enter-to-send setting — the
                            // same gate the chat composer applies.
                            if SettingsStore.shared.enterToSend {
                                sendComposer()
                            }
                        }
                        .onChange(of: composerText) { newValue in
                            // Multi-line fields land Return as a trailing
                            // newline on some iOS builds — same coalescing
                            // trick the chat composer uses.
                            guard SettingsStore.shared.enterToSend, newValue.hasSuffix("\n") else { return }
                            composerText = String(newValue.dropLast())
                            sendComposer()
                        }
                }

                sendButton

                voiceHoldOrb
            }
            Text("GS can make mistakes — double-check important info.")
                .font(Aero.caption())
                .foregroundColor(Aero.textSecondary)
                .frame(maxWidth: .infinity)
        }
        .padding(.bottom, Aero.Spacing.s)
    }

    /// Send is enabled only when the field holds real text; a send opens a
    /// NEW chat and the typed text is sent automatically on arrival
    /// (.chatAutoSend — the conversation starts, not a prefill).
    private var sendButton: some View {
        Button {
            sendComposer()
        } label: {
            Image(systemName: "arrow.up.circle.fill")
                .font(.system(size: 24))
                .foregroundStyle(
                    composerText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        ? AnyShapeStyle(Aero.textDisabled)
                        : AnyShapeStyle(Aero.accent))
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(KineticPressStyle())
        .disabled(composerText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        .accessibilityLabel("Send")
    }

    private func sendComposer() {
        let text = composerText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        GSHaptics.tap()   // committed send — the same tick the chat plays
        composerText = ""
        composerFocused = false
        onRoute?(.chatAutoSend(text))
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text.uppercased())
            .font(Aero.label())
            .foregroundColor(Aero.textSecondary)
            .padding(.horizontal, 4)
            .accessibilityAddTraits(.isHeader)
    }

    // MARK: Voice press-and-hold — the composer affordance (verbatim)

    /// Aurora orb: hold past the threshold to dictate (the transcript fills
    /// the inline composer field), quick tap still opens full voice mode.
    /// Every failure path dissolves quietly — nothing is ever shown as an
    /// error.
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
