import SwiftUI

/**
 * AERUO KINETIC drawer — a CHAT HISTORY PANEL, not a route directory.
 * The sidebar is primarily about starting and navigating conversations:
 *
 *   1. ACCOUNT — one compact identity row; opening it reveals the account
 *      controls (Profile · Settings · Notifications · Billing) in a sheet
 *      instead of spending four sidebar rows on them.
 *   2. NEW CHAT + SEARCH — the two conversation actions, always first.
 *   3. RECENT — the visual heart: live conversations, tap to open,
 *      long-press for pin / rename / archive / delete. Genuine empty state —
 *      nothing is invented. "All chats" is the quiet overflow into the full
 *      history hub.
 *   4. MORE — one row. Explore, Create, Projects, Library, Assistants, Voice,
 *      Models and Compare models live behind it, one step away — present,
 *      but never competing with conversation at row parity.
 *
 * Machinery (tracked dismissal, VoiceOver containment, rename alert,
 * edge-swipe entry) is unchanged.
 */
struct AeroDrawer: View {

    /// Opening-drag offset owned by the host (edge-swipe rides the finger);
    /// the drawer adds its own close-drag tracking on top of it.
    @Binding var entryOffset: CGFloat
    var onRoute: (AeroRoute) -> Void
    var onClose: () -> Void

    /// Live selected-state input: the route currently on screen.
    var activeRoute: AeroRoute? = nil

    @ObservedObject private var store = ConversationStore.shared
    /// The drawer header shows the REAL stored identity — it re-renders the
    /// moment the account changes (sign-in, name edit).
    @ObservedObject private var account = AccountStore.shared

    @State private var renameTarget: StoredConversation?
    @State private var renameDraft = ""
    @State private var accountSheet = false
    @State private var moreSheet = false

    /// Close-drag tracking (Task 85-e I9): while the finger is down the panel
    /// follows it 1:1 leftward; the state flip only happens on release, and
    /// only when the drag (or its projected fling) earns the dismissal.
    @State private var closeOffset: CGFloat = 0

    /// Panel geometry: bounded width, leading-anchored — the panel must never
    /// span the full screen; the scrim beyond it stays tappable and
    /// VoiceOver-reachable.
    private var panelWidth: CGFloat {
        min(340, UIScreen.main.bounds.width * 0.85)
    }

    // Theme-following navigation palette: the drawer belongs to the active
    // appearance — monochrome black/white in both.
    private let panel = Aero.navSurface

    var body: some View {
        ZStack(alignment: .leading) {
            scrimLayer
            panelLayer
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
        .sheet(isPresented: $accountSheet) {
            accountSheetView
                .presentationDetents([.medium])
        }
        .sheet(isPresented: $moreSheet) {
            moreSheetView
                .presentationDetents([.medium, .large])
        }
    }

    // MARK: Body building blocks (type-check split — the previous single
    // expression exceeded the Swift type-checker's budget; same view tree,
    // decomposed into named sub-expressions)

    private var scrimLayer: some View {
        Aero.scrim
            .ignoresSafeArea()
            .onTapGesture { onClose() }
            .accessibilityLabel("Close menu")
            .accessibilityAddTraits(.isButton)
            .transition(.opacity)
    }

    private var panelLayer: some View {
        VStack(alignment: .leading, spacing: 0) {
            ScrollView(showsIndicators: false) {
                panelList
            }
        }
        .padding(.top, Aero.Spacing.xl)
        .frame(width: panelWidth, alignment: .top)
        .frame(maxHeight: .infinity, alignment: .top)
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

    private var panelList: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            header
            newChatRow
            recentGroup
            divider
            moreRow
        }
        .padding(.horizontal, Aero.Spacing.m)
        .padding(.bottom, Aero.Spacing.xl)
    }

    /// New chat + Search — the two conversation actions, always first.
    private var newChatRow: some View {
        HStack(spacing: Aero.Spacing.s) {
            Button {
                GSHaptics.tap()
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

            Button {
                GSHaptics.tap()
                onRoute(.chatSearch)
            } label: {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundColor(Aero.text)
                    .frame(width: 48, height: 46)
                    .background(RoundedRectangle(cornerRadius: Aero.Radius.md).fill(Aero.raisedSurface))
            }
            .buttonStyle(KineticPressStyle())
            .accessibilityLabel("Search chats")
        }
        .padding(.bottom, Aero.Spacing.m)
    }

    /// The heart of the sidebar — live conversations only. When the store is
    /// empty the panel says so honestly; nothing is invented.
    private var recentGroup: some View {
        Group {
            sectionLabel("Recent")
            if recentRows.isEmpty {
                Text("No conversations yet")
                    .font(Aero.bodyMedium())
                    .foregroundColor(Aero.textSecondary)
                    .padding(.horizontal, Aero.Spacing.m)
                    .padding(.bottom, Aero.Spacing.s)
            } else {
                ForEach(recentRows) { conversation in
                    recentRow(conversation)
                }
            }
            // Always visible — the real overflow into the full history hub.
            row(title: "All chats", icon: "tray", isSelected: activeRoute == .chats) { onRoute(.chats) }
        }
    }

    /// Every other product surface, one quiet row away.
    private var moreRow: some View {
        row(title: "More", icon: "ellipsis.circle") { moreSheet = true }
    }

    // MARK: Account / More reveal sheets

    private var accountSheetView: some View {
        AeroSheetShell(title: account.displayName.isEmpty ? "Account" : account.displayName) {
            VStack(spacing: Aero.Spacing.s) {
                sheetRow(title: "Profile", icon: "person", isSelected: activeRoute == .profile) { onRoute(.profile) }
                sheetRow(title: "Settings", icon: "gearshape", isSelected: activeRoute == .settings) { onRoute(.settings) }
                sheetRow(title: "Notifications", icon: "bell", isSelected: activeRoute == .notifications) { onRoute(.notifications) }
                sheetRow(title: "Billing", icon: "creditcard", isSelected: activeRoute == .billing) { onRoute(.billing) }
            }
        }
    }

    private var moreSheetView: some View {
        AeroSheetShell(title: "More") {
            ScrollView {
                VStack(spacing: Aero.Spacing.s) {
                    sheetRow(title: "Explore", icon: "safari", isSelected: activeRoute == .explore) { onRoute(.explore) }
                    sheetRow(title: "Create", icon: "sparkles", isSelected: activeRoute == .createTab) { onRoute(.createTab) }
                    sheetRow(title: "Projects", icon: "folder", isSelected: activeRoute == .projects) { onRoute(.projects) }
                    sheetRow(title: "Library", icon: "books.vertical", isSelected: activeRoute == .library) { onRoute(.library) }
                    sheetRow(title: "Assistants", icon: "cpu", isSelected: activeRoute == .assistants) { onRoute(.assistants) }
                    sheetRow(title: "Voice", icon: "waveform", isSelected: activeRoute == .voice) { onRoute(.voice) }
                    sheetRow(title: "Models", icon: "speedometer", isSelected: activeRoute == .models) { onRoute(.models) }
                    sheetRow(title: "Compare models", icon: "rectangle.on.rectangle", isSelected: activeRoute == .modelCompare) { onRoute(.modelCompare) }
                }
            }
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
        Array(store.activeConversations.prefix(12))
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
    /// fallback when the reader never gave a name). The whole row reveals the
    /// account sheet (Profile / Settings / Notifications / Billing) instead
    /// of spending sidebar rows on account chrome.
    private var header: some View {
        HStack(spacing: Aero.Spacing.s) {
            ZStack {
                Circle().fill(Aero.accentSoft).frame(width: 44, height: 44)
                if account.displayName.isEmpty {
                    Image(systemName: "person.fill")
                        .font(.system(size: 15))
                        .foregroundColor(Aero.text)
                } else {
                    Text(accountInitials)
                        .font(Aero.label())
                        .foregroundColor(Aero.text)
                }
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(account.displayName.isEmpty ? "GS account" : account.displayName)
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
            Image(systemName: "chevron.up")
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(Aero.textSecondary)
        }
        .contentShape(Rectangle())
        .onTapGesture {
            GSHaptics.tap()   // committed open — the account sheet reveals
            accountSheet = true
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Account. Show account options")
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
                        .foregroundColor(isSelected ? Aero.text : Aero.textSecondary)
                        .frame(width: 18)
                }
                Text(title)
                    .font(Aero.body())
                    .foregroundColor(Aero.text)
                    .lineLimit(1)
                Spacer()
                if isPinned {
                    Image(systemName: "star.fill")
                        .font(.system(size: 10))
                        .foregroundColor(Aero.textSecondary)
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

    /// One row inside the account / More reveal sheets. The route handler in
    /// RootView closes the drawer itself (which unmounts the sheet with it) —
    /// so the row only routes.
    private func sheetRow(title: String, icon: String, isSelected: Bool = false, action: @escaping () -> Void) -> some View {
        row(title: title, icon: icon, isSelected: isSelected, action: action)
    }
}
