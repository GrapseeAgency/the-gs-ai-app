import SwiftUI

/**
 * AERUO KINETIC drawer — the primary navigation, benchmark pattern
 * (ChatGPT / Claude / Kimi): a bounded panel sliding over the canvas,
 * account header, one-tap new chat, LIVE recents from `ConversationStore`
 * (pin / archive / delete on long-press), and the section map.
 *
 * Phase 2 reclassification — conversation-first, honest, calm:
 *   1. Account header (REAL stored identity via AccountStore — no fake
 *      plan chip; the row opens Profile)
 *   2. "New chat" — the single loud primary action
 *   3. Recent — REAL conversations only (no samples when empty), with
 *      All chats + Archived always visible
 *   4. PRIMARY (no label): Home · Chats · Explore · Create · Library
 *   5. SECONDARY ("More"): Projects · Assistants · Voice · Search
 *   6. ACCOUNT ("Account"): Profile · Notifications · Billing · Settings
 *   7. ADVANCED (collapsed by default): Models · Compare models
 * Folders / Shared are gone (fabricated surfaces); machinery (tracked
 * dismissal, VoiceOver containment, rename) is unchanged.
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
    /// The drawer header shows the REAL stored identity — it re-renders the
    /// moment the account changes (sign-in, name edit).
    @ObservedObject private var account = AccountStore.shared

    @State private var renameTarget: StoredConversation?
    @State private var renameDraft = ""
    /// ADVANCED group — collapsed by default (Phase 2): model machinery is
    /// opt-in, never front-door navigation.
    @State private var advancedExpanded = false

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
                            ForEach(recentRows) { conversation in
                                recentRow(conversation)
                            }
                            // Always visible — real destinations even when
                            // the recent list above is empty. No samples.
                            row(title: "All chats", icon: "tray", isSelected: activeRoute == .chats) { onRoute(.chats) }
                            row(title: "Archived", icon: "archivebox", isSelected: activeRoute == .chatArchive) { onRoute(.chatArchive) }
                        }
                        divider
                        // PRIMARY — no label. The five surfaces a reader
                        // actually lives in.
                        Group {
                            row(title: "Home", icon: "house", isSelected: atHomeRoot) { onHome?() }
                            row(title: "Chats", icon: "bubble.left", isSelected: activeRoute == .chats) { onRoute(.chats) }
                            row(title: "Explore", icon: "safari", isSelected: activeRoute == .explore) { onRoute(.explore) }
                            row(title: "Create", icon: "sparkles", isSelected: activeRoute == .createTab) { onRoute(.createTab) }
                            row(title: "Library", icon: "books.vertical", isSelected: activeRoute == .library) { onRoute(.library) }
                        }
                        divider
                        Group {
                            sectionLabel("More")
                            row(title: "Projects", icon: "folder", isSelected: activeRoute == .projects) { onRoute(.projects) }
                            row(title: "Assistants", icon: "cpu", isSelected: activeRoute == .assistants) { onRoute(.assistants) }
                            row(title: "Voice", icon: "waveform", isSelected: activeRoute == .voice) { onRoute(.voice) }
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
                        divider
                        advancedGroup
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

    // MARK: Advanced group (collapsed by default)

    /// One expandable "Advanced" row; model machinery lives inside it.
    private var advancedGroup: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            Button {
                GSHaptics.select()
                withAnimation(Aero.snappy) { advancedExpanded.toggle() }
            } label: {
                HStack(spacing: Aero.Spacing.s) {
                    Image(systemName: "gearshape.2")
                        .font(.system(size: 13))
                        .foregroundColor(Aero.textSecondary)
                        .frame(width: 18)
                    Text("Advanced")
                        .font(Aero.body())
                        .foregroundColor(Aero.text)
                        .lineLimit(1)
                    Spacer()
                    Image(systemName: "chevron.down")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(Aero.textSecondary)
                        .rotationEffect(.degrees(advancedExpanded ? 180 : 0))
                }
                .padding(.horizontal, Aero.Spacing.m)
                .padding(.vertical, 11)
                .contentShape(Rectangle())
            }
            .buttonStyle(KineticPressStyle())
            .accessibilityLabel("Advanced")
            .accessibilityAddTraits(advancedExpanded ? [.isSelected] : [])

            if advancedExpanded {
                VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                    row(title: "Models", icon: "speedometer", isSelected: activeRoute == .models) { onRoute(.models) }
                    row(title: "Compare models", icon: "rectangle.on.rectangle", isSelected: activeRoute == .modelCompare) { onRoute(.modelCompare) }
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
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

    // MARK: Header

    /// Account header — the REAL stored identity (AccountStore; neutral
    /// fallback when the reader never gave a name: initials yield to a
    /// person glyph, the name line reads "Account", a missing email line is
    /// simply absent). Nothing is invented. The whole row opens Profile —
    /// the fabricated "Pro" plan chip is gone.
    private var header: some View {
        HStack(spacing: Aero.Spacing.s) {
            ZStack {
                Circle().fill(Aero.accent.opacity(0.16)).frame(width: 44, height: 44)
                if account.displayName.isEmpty {
                    Image(systemName: "person.fill")
                        .font(.system(size: 15))
                        .foregroundColor(Aero.accent)
                } else {
                    Text(accountInitials)
                        .font(Aero.label())
                        .foregroundColor(Aero.accent)
                }
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(account.displayName.isEmpty ? "Account" : account.displayName)
                    .font(Aero.body())
                    .foregroundColor(Aero.text)
                    .lineLimit(1)
                if !account.email.isEmpty {
                    Text(account.email)
                        .font(Aero.caption())
                        .foregroundColor(Aero.textSecondary)
                        .lineLimit(1)
                }
            }
            Spacer()
        }
        .contentShape(Rectangle())
        .onTapGesture {
            GSHaptics.tap()   // committed open — the drawer routes
            onRoute(.profile)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Account. Open profile")
        .padding(.bottom, Aero.Spacing.m)
    }

    /// Up to two leading initials of the stored display name — derived only,
    /// never invented (empty name never reaches here).
    private var accountInitials: String {
        account.displayName
            .split(separator: " ")
            .prefix(2)
            .map { String($0.prefix(1)).uppercased() }
            .joined()
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
