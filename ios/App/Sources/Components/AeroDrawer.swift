import SwiftUI

/**
 * AERUO KINETIC drawer — the primary navigation, benchmark pattern
 * (ChatGPT / Claude / Kimi): a bounded panel sliding over the canvas,
 * account header, one-tap new chat, LIVE recents from `ConversationStore`
 * (pin / archive / delete on long-press), and the section map.
 *
 * Task 90-b rebuild: the panel is width-bounded and leading-anchored so the
 * scrim stays tappable beyond it, carries a single sanctioned soft shadow,
 * and follows the canonical hierarchy — account header, dominant primary
 * "New chat" action, RECENT, primary navigation, Tools, Account — with live
 * selected-state routing and VoiceOver containment/escape.
 */
struct AeroDrawer: View {

    /// Opening-drag offset owned by the host (edge-swipe rides the finger);
    /// the drawer adds its own close-drag tracking on top of it.
    @Binding var entryOffset: CGFloat
    var onRoute: (AeroRoute) -> Void
    var onClose: () -> Void

    /// Live selected-state inputs (Task 90-b): the route currently on screen
    /// and whether the shell sits at the Home root. Defaults keep call sites
    /// compiling; RootView passes the real values.
    var activeRoute: AeroRoute? = nil
    var atHomeRoot: Bool = true

    /// Dedicated Home action (Task 90-b). Home is a shell state, not an
    /// AeroRoute — the host closes the drawer and empties the stack itself,
    /// so no unregistered route can ever be appended.
    var onHome: (() -> Void)? = nil

    @ObservedObject private var store = ConversationStore.shared

    @State private var renameTarget: StoredConversation?
    @State private var renameDraft = ""

    /// Close-drag tracking (Task 85-e I9): while the finger is down the panel
    /// follows it 1:1 leftward; the state flip only happens on release, and
    /// only when the drag (or its projected fling) earns the dismissal.
    @State private var closeOffset: CGFloat = 0

    /// Panel geometry (Task 90-b): bounded width, leading-anchored — the
    /// panel must never span the full screen; the scrim beyond it stays
    /// tappable and VoiceOver-reachable.
    private var panelWidth: CGFloat {
        min(340, UIScreen.main.bounds.width * 0.85)
    }

    // Theme-following navigation palette (foundation Step 1: the drawer belongs
    // to the active appearance — no more forced-obsidian panel in light mode).
    private let panel = Aero.navSurface

    var body: some View {
        ZStack(alignment: .leading) {
            Aero.scrim
                .ignoresSafeArea()
                .onTapGesture { onClose() }
                .accessibilityLabel("Close menu")
                .accessibilityAddTraits(.isButton)
                .transition(.opacity)

            VStack(alignment: .leading, spacing: 0) {
                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                        header
                        newChat
                        Group {
                            sectionLabel("Recent")
                            if recentRows.isEmpty {
                                ForEach(demoRecents, id: \.id) { sample in
                                    row(title: sample.title, icon: nil) { onRoute(.chat(sample.id)) }
                                }
                            } else {
                                ForEach(recentRows) { conversation in
                                    recentRow(conversation)
                                }
                                row(title: "All chats", icon: "tray") { onRoute(.chats) }
                                row(title: "Archived", icon: "archivebox") { onRoute(.chatArchive) }
                            }
                        }
                        divider
                        Group {
                            row(title: "Home", icon: "house", isSelected: atHomeRoot) { onHome?() }
                            row(title: "Chats", icon: "bubble.left", isSelected: activeRoute == .chats) { onRoute(.chats) }
                            row(title: "Explore", icon: "safari", isSelected: activeRoute == .explore) { onRoute(.explore) }
                            row(title: "Create", icon: "sparkles", isSelected: activeRoute == .createTab) { onRoute(.createTab) }
                            row(title: "Library", icon: "books.vertical", isSelected: activeRoute == .library) { onRoute(.library) }
                        }
                        divider
                        Group {
                            sectionLabel("Tools")
                            row(title: "Projects", icon: "folder", isSelected: activeRoute == .projects) { onRoute(.projects) }
                            row(title: "Assistants", icon: "cpu", isSelected: activeRoute == .assistants) { onRoute(.assistants) }
                            row(title: "Models", icon: "speedometer", isSelected: activeRoute == .models) { onRoute(.models) }
                            row(title: "Search", icon: "magnifyingglass", isSelected: activeRoute == .search) { onRoute(.search) }
                        }
                        divider
                        Group {
                            sectionLabel("Account")
                            row(title: "Profile", icon: "person", isSelected: activeRoute == .profile) { onRoute(.profile) }
                            row(title: "Notifications", icon: "bell", isSelected: activeRoute == .notifications) { onRoute(.notifications) }
                            row(title: "Billing", icon: "creditcard", isSelected: activeRoute == .billing) { onRoute(.billing) }
                            row(title: "Settings", icon: "gearshape", isSelected: activeRoute == .settings) { onRoute(.settings) }
                        }
                    }
                    .padding(.horizontal, Aero.Spacing.m)
                    .padding(.bottom, Aero.Spacing.xl)
                }
            }
            .padding(.top, Aero.Spacing.xl)
            .frame(width: panelWidth, maxHeight: .infinity, alignment: .top)
            .background(panel)
            .shadow(color: Color.black.opacity(0.18), radius: 24, x: 8)
            .offset(x: entryOffset + closeOffset)
            .gesture(closeDragGesture)
            .transition(.move(edge: .leading).combined(with: .opacity))
            .onAppear {
                // A stale tracked offset must never survive a fresh presentation.
                closeOffset = 0
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Navigation menu")
        .accessibilityAction(.escape) { onClose() }
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

    // MARK: Finger-tracked dismissal (Task 85-e I9)

    /// Leftward tracking is clamped at one screen; a rightward pull against
    /// the resting edge rubber-bands (damped to 12%).
    private static func trackedOffset(_ raw: CGFloat) -> CGFloat {
        if raw < 0 {
            return max(raw, -UIScreen.main.bounds.width)
        }
        return raw * 0.12
    }

    /// Drag → panel offset → release decision, like a UIKit drawer:
    /// committed drags and leftward flings dismiss, everything else springs
    /// back. `predictedEndTranslation` is SwiftUI's built-in velocity signal
    /// (DragGesture.Value exposes no raw velocity).
    private var closeDragGesture: some Gesture {
        DragGesture(minimumDistance: 20)
            .onChanged { value in
                closeOffset = Self.trackedOffset(value.translation.width)
            }
            .onEnded { value in
                let raw = value.translation.width
                let projected = value.predictedEndTranslation.width
                if raw < -110 || projected < -240 {
                    onClose()
                } else {
                    withAnimation(Aero.motion(Aero.spring)) { closeOffset = 0 }
                }
            }
    }

    // MARK: Live recents

    private var recentRows: [StoredConversation] {
        Array(store.activeConversations.prefix(5))
    }

    /// One live recent: tap to open, long-press for the benchmark action set.
    private func recentRow(_ conversation: StoredConversation) -> some View {
        row(title: gsConversationTitle(conversation.title), icon: nil, isPinned: conversation.pinned) {
            onRoute(.chat(conversation.id))
        }
        .contextMenu {
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
    }

    private func sync(_ id: String, pinned: Bool? = nil, archived: Bool? = nil, title: String? = nil) {
        guard !id.hasPrefix("demo-"), !id.hasPrefix("local-") else { return }
        Task { try? await APIClient.shared.updateConversation(id: id, pinned: pinned, archived: archived, title: title) }
    }

    private func syncDelete(_ id: String) {
        guard !id.hasPrefix("demo-"), !id.hasPrefix("local-") else { return }
        Task { try? await APIClient.shared.deleteConversation(id: id) }
    }

    private struct DemoRecent: Identifiable {
        let id: String
        let title: String
    }

    private let demoRecents: [DemoRecent] = [
        DemoRecent(id: "demo-1", title: "Q3 pricing strategy"),
        DemoRecent(id: "demo-2", title: "Kyoto trip plan"),
        DemoRecent(id: "demo-3", title: "Kotlin coroutines notes"),
        DemoRecent(id: "demo-4", title: "Brand voice guidelines"),
        DemoRecent(id: "demo-5", title: "Research: AI market")
    ]

    // MARK: Header

    /// Account header — tappable as a whole (account action); the Pro chip
    /// stays an independent button so it still wins taps over the row gesture
    /// and keeps its own billing route.
    private var header: some View {
        HStack(spacing: Aero.Spacing.s) {
            ZStack {
                Circle().fill(Aero.accent.opacity(0.16)).frame(width: 44, height: 44)
                Text("GA")
                    .font(Aero.label())
                    .foregroundColor(Aero.accent)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text("Grapsee Admin")
                    .font(Aero.body())
                    .foregroundColor(Aero.text)
                Text("graphesee@gmail.com")
                    .font(Aero.caption())
                    .foregroundColor(Aero.textSecondary)
            }
            Spacer()
            AeroChip(text: "Pro", selected: true) { onRoute(.billing) }
        }
        .contentShape(Rectangle())
        .onTapGesture {
            GSHaptics.tap()   // committed open — the drawer routes
            onRoute(.profile)
        }
        .padding(.bottom, Aero.Spacing.m)
    }

    /// Dominant primary action (Task 90-b): full-width accent-filled button.
    private var newChat: some View {
        Button {
            onRoute(.chat(nil))
        } label: {
            HStack(spacing: Aero.Spacing.s) {
                Image(systemName: "square.and.pencil")
                    .font(.system(size: 14))
                    .foregroundColor(Aero.onAccent)
                Text("New chat")
                    .font(Aero.body())
                    .foregroundColor(Aero.onAccent)
                Spacer()
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.vertical, 13)
            .background(RoundedRectangle(cornerRadius: Aero.Radius.md).fill(Aero.accent))
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityLabel("New chat")
        .padding(.bottom, Aero.Spacing.m)
    }

    private var divider: some View {
        Rectangle()
            .fill(Aero.outline)
            .frame(height: 1)
            .padding(.vertical, Aero.Spacing.m)
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text.uppercased())
            .font(Aero.label())
            .foregroundColor(Aero.textSecondary)
            .padding(.horizontal, 4)
            .padding(.bottom, Aero.Spacing.s)
    }

    private func row(title: String, icon: String?, isPinned: Bool = false, isSelected: Bool = false, action: @escaping () -> Void) -> some View {
        Button {
            GSHaptics.tap()   // committed open — the drawer routes
            action()
        } label: {
            HStack(spacing: Aero.Spacing.s) {
                if let icon {
                    Image(systemName: icon)
                        .font(.system(size: 13))
                        .foregroundColor(isSelected ? Aero.accent : Aero.textSecondary)
                        .frame(width: 18)
                }
                Text(title)
                    .font(Aero.body())
                    .foregroundColor(isSelected ? Aero.accent : Aero.text)
                    .lineLimit(1)
                Spacer()
                if isPinned {
                    Image(systemName: "star.fill")
                        .font(.system(size: 10))
                        .foregroundColor(Aero.accent)
                }
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.vertical, 11)
            .background {
                if isSelected {
                    RoundedRectangle(cornerRadius: Aero.Radius.md).fill(Aero.accentSoft)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
    }
}
