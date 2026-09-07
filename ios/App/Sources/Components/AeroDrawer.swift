import SwiftUI

/**
 * AERUO KINETIC drawer — the primary navigation, benchmark pattern
 * (ChatGPT / Claude / Kimi): obsidian panel sliding over the canvas,
 * account header, one-tap new chat, LIVE recents from `ConversationStore`
 * (pin / archive / delete on long-press), and the section map.
 */
struct AeroDrawer: View {

    var onRoute: (AeroRoute) -> Void
    var onClose: () -> Void

    @ObservedObject private var store = ConversationStore.shared

    @State private var renameTarget: StoredConversation?
    @State private var renameDraft = ""

    // Forced-obsidian palette (fixed benchmark-dark in both appearances)
    private let panel = Aero.dynamic(
        light: UIColor(red: 0.039, green: 0.051, blue: 0.071, alpha: 1),
        dark: UIColor(red: 0.039, green: 0.051, blue: 0.071, alpha: 1))
    private let raised = Aero.dynamic(
        light: UIColor(red: 0.094, green: 0.118, blue: 0.157, alpha: 1),
        dark: UIColor(red: 0.094, green: 0.118, blue: 0.157, alpha: 1))
    private let ink = Aero.dynamic(
        light: UIColor(red: 0.929, green: 0.937, blue: 0.949, alpha: 1),
        dark: UIColor(red: 0.929, green: 0.937, blue: 0.949, alpha: 1))
    private let muted = Aero.dynamic(
        light: UIColor(red: 0.545, green: 0.576, blue: 0.631, alpha: 1),
        dark: UIColor(red: 0.545, green: 0.576, blue: 0.631, alpha: 1))

    var body: some View {
        ZStack(alignment: .leading) {
            Color.black.opacity(0.5)
                .ignoresSafeArea()
                .onTapGesture { onClose() }
                .gesture(DragGesture(minimumDistance: 25).onEnded { value in
                    if value.translation.width < -50 { onClose() }
                })
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
                            sectionLabel("Explore")
                            row(title: "Chats", icon: "bubble.left") { onRoute(.chats) }
                            row(title: "Explore", icon: "safari") { onRoute(.explore) }
                            row(title: "Create", icon: "sparkles") { onRoute(.createTab) }
                            row(title: "Library", icon: "books.vertical") { onRoute(.library) }
                            row(title: "Projects", icon: "folder") { onRoute(.projects) }
                            row(title: "Assistants", icon: "smarttoy") { onRoute(.assistants) }
                            row(title: "Models", icon: "speed") { onRoute(.models) }
                            row(title: "Search", icon: "magnifyingglass") { onRoute(.search) }
                        }
                        divider
                        Group {
                            sectionLabel("Account")
                            row(title: "Upgrade plan", icon: "sparkles") { onRoute(.billing) }
                            row(title: "Notifications", icon: "bell") { onRoute(.notifications) }
                            row(title: "Profile", icon: "person") { onRoute(.profile) }
                            row(title: "Settings", icon: "gearshape") { onRoute(.settings) }
                        }
                    }
                    .padding(.horizontal, Aero.Spacing.m)
                    .padding(.bottom, Aero.Spacing.xl)
                }
            }
            .padding(.top, Aero.Spacing.xl)
            .frame(maxHeight: .infinity, alignment: .top)
            .background(panel)
            .gesture(DragGesture(minimumDistance: 25).onEnded { value in
                // Horizontal left-swipe anywhere on the panel dismisses.
                if value.translation.width < -60 { onClose() }
            })
            .transition(.move(edge: .leading).combined(with: .opacity))
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
                }
                renameTarget = nil
            }
            Button("Cancel", role: .cancel) { renameTarget = nil }
        } message: {
            Text("Give this conversation a name you'll recognise.")
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
                let target = !conversation.pinned
                store.setPinned(id: conversation.id, target)
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
                store.setArchived(id: conversation.id, true)
                sync(conversation.id, archived: true)
            } label: {
                Label("Archive", systemImage: "archivebox")
            }
            Button(role: .destructive) {
                store.delete(id: conversation.id)
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
                    .foregroundColor(ink)
                Text("graphesee@gmail.com")
                    .font(Aero.caption())
                    .foregroundColor(muted)
            }
            Spacer()
            AeroChip(text: "Pro", selected: true) { onRoute(.billing) }
        }
        .padding(.bottom, Aero.Spacing.m)
    }

    private var newChat: some View {
        Button {
            onRoute(.chat(nil))
        } label: {
            HStack(spacing: Aero.Spacing.s) {
                Image(systemName: "square.and.pencil")
                    .font(.system(size: 14))
                    .foregroundColor(Aero.accent)
                Text("New chat")
                    .font(Aero.body())
                    .foregroundColor(ink)
                Spacer()
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.vertical, 13)
            .background(RoundedRectangle(cornerRadius: 14).fill(raised))
        }
        .buttonStyle(KineticPressStyle())
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
            .foregroundColor(muted)
            .padding(.horizontal, 4)
            .padding(.bottom, Aero.Spacing.s)
    }

    private func row(title: String, icon: String?, isPinned: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: Aero.Spacing.s) {
                if let icon {
                    Image(systemName: icon)
                        .font(.system(size: 13))
                        .foregroundColor(muted)
                        .frame(width: 18)
                }
                Text(title)
                    .font(Aero.body())
                    .foregroundColor(ink)
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
            .contentShape(Rectangle())
        }
        .buttonStyle(KineticPressStyle())
    }
}
