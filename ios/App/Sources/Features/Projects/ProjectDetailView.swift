import SwiftUI

// MARK: - Stagger entrance (private per-file helper)

/// Fades + lifts a section into place, delayed by its index —
/// the Aeruo Kinetic section entrance.
private struct StaggerIn<Content: View>: View {
    let index: Int
    @ViewBuilder var content: () -> Content

    @State private var appeared = false

    var body: some View {
        content()
            .opacity(appeared ? 1 : 0)
            .offset(y: appeared ? 0 : 16)
            .onAppear {
                withAnimation(Aero.spring.delay(Aero.stagger(index))) {
                    appeared = true
                }
            }
    }
}

// MARK: - Project detail — real state on every tab

/// The hero answers from the stored project: the name, blurb and custom
/// instructions the reader actually wrote. Tabs answer from real state —
/// Chats lists the conversations genuinely linked to the project (linked
/// and unlinked right here, rows push `.chat(id)`), Activity replays the
/// store's real event log, and Files/Members are honest gates for
/// subsystems that don't exist on-device yet. The gearshape opens edit +
/// delete — no dead controls, no sample rows.
struct ProjectDetailView: View {

    private enum DetailTab: String, CaseIterable, Identifiable {
        case chats = "Chats"
        case files = "Files"
        case activity = "Activity"
        case members = "Members"

        var id: String { rawValue }
    }

    let projectID: String

    @ObservedObject private var store = ProjectStore.shared
    @ObservedObject private var conversations = ConversationStore.shared
    @State private var tab: DetailTab = .chats
    @State private var showEdit = false
    @State private var showDeleteConfirm = false
    @State private var showLinker = false
    @State private var editName = ""
    @State private var editBlurb = ""
    @State private var editInstructions = ""
    @State private var selectedChatIDs: Set<String> = []

    private var project: ProjectStore.Project? {
        store.byId(projectID)
    }

    // MARK: Body

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                if let project {
                    StaggerIn(index: 0) { hero(project) }
                    StaggerIn(index: 1) { tabBar }
                    StaggerIn(index: 2) { tabContent(project) }
                } else {
                    Text("This project no longer exists.")
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                        .padding(.top, Aero.Spacing.xl)
                }
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
        .toolbar(.visible, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    if let project {
                        editName = project.name
                        editBlurb = project.blurb
                        editInstructions = project.instructions
                        showEdit = true
                    }
                } label: {
                    Image(systemName: "gearshape")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundStyle(Aero.text)
                }
                .buttonStyle(KineticPressStyle())
                .accessibilityLabel("Project settings")
            }
        }
        .alert("Edit project", isPresented: $showEdit) {
            TextField("Name", text: $editName)
            TextField("What is it for? (optional)", text: $editBlurb)
            TextField("Custom instructions (optional)", text: $editInstructions)
            Button("Save") {
                ProjectStore.shared.update(
                    id: projectID,
                    name: editName,
                    blurb: editBlurb,
                    instructions: editInstructions
                )
            }
            Button("Delete project", role: .destructive) { showDeleteConfirm = true }
            Button("Cancel", role: .cancel) {}
        }
        .alert("Delete this project?", isPresented: $showDeleteConfirm) {
            Button("Delete", role: .destructive) {
                ProjectStore.shared.delete(id: projectID)
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("The project, its instructions and its activity log are removed from this device. Chats stay in your library.")
        }
        .sheet(isPresented: $showLinker) { linkerSheet }
    }

    // MARK: Hero

    private func hero(_ project: ProjectStore.Project) -> some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                Text(project.name)
                    .font(Aero.headline())   // serif — hero voice
                    .foregroundStyle(Aero.text)
                if !project.blurb.isEmpty {
                    Text(project.blurb)
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                if !project.instructions.isEmpty {
                    VStack(alignment: .leading, spacing: Aero.Spacing.xs) {
                        Text("INSTRUCTIONS")
                            .font(Aero.label())
                            .foregroundStyle(Aero.textMuted)
                        Text(project.instructions)
                            .font(Aero.caption())
                            .foregroundStyle(Aero.text)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(Aero.Spacing.s)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Aero.container))
                }
            }
        }
    }

    // MARK: Tabs

    private var tabBar: some View {
        HStack(spacing: Aero.Spacing.s) {
            ForEach(DetailTab.allCases) { t in
                AeroChip(
                    text: t.rawValue,
                    selected: tab == t,
                    action: {
                        withAnimation(Aero.snappy) { tab = t }
                    }
                )
            }
        }
    }

    @ViewBuilder
    private func tabContent(_ project: ProjectStore.Project) -> some View {
        switch tab {
        case .chats: chatsTab(project)
        case .files: filesTab
        case .activity: activityTab(project)
        case .members: membersTab
        }
    }

    // MARK: Chats tab — real links

    private func chatsTab(_ project: ProjectStore.Project) -> some View {
        let linked = project.chatIds.compactMap { id in
            conversations.conversations.first { $0.id == id }
        }
        return VStack(spacing: Aero.Spacing.s) {
            AeroChip(text: "Add chats", selected: false, action: {
                selectedChatIDs = Set(project.chatIds)
                showLinker = true
            })
            if linked.isEmpty {
                Text("No chats linked yet — add conversations to bundle them with these instructions.")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            ForEach(linked, id: \.id) { chat in
                NavigationLink(value: AeroRoute.chat(chat.id)) {
                    AeroListRow(
                        title: chat.title,
                        subtitle: "updated \(relative(chat.updatedAt))",
                        leading: {
                            Image(systemName: "bubble.left")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundStyle(Aero.text)
                                .frame(width: 36, height: 36)
                                .background(Circle().fill(Aero.containerHigh))
                        },
                        trailing: {
                            Image(systemName: "chevron.right")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(Aero.textMuted)
                        }
                    )
                }
                .buttonStyle(KineticPressStyle())
                .contextMenu {
                    Button(role: .destructive) {
                        ProjectStore.shared.unlinkChat(id: project.id, chatId: chat.id)
                    } label: {
                        Label("Remove from project", systemImage: "minus.circle")
                    }
                }
            }
        }
    }

    // MARK: Link chats sheet — picker over the real conversation book

    private var linkerSheet: some View {
        NavigationStack {
            List(conversations.conversations.filter { !$0.archived }, id: \.id) { chat in
                Button {
                    if selectedChatIDs.contains(chat.id) {
                        selectedChatIDs.remove(chat.id)
                    } else {
                        selectedChatIDs.insert(chat.id)
                    }
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(chat.title)
                                .font(Aero.body())
                                .foregroundStyle(Aero.text)
                            Text("updated \(relative(chat.updatedAt))")
                                .font(Aero.label())
                                .foregroundStyle(Aero.textMuted)
                        }
                        Spacer()
                        Image(systemName: selectedChatIDs.contains(chat.id) ? "checkmark.circle.fill" : "circle")
                            .foregroundStyle(selectedChatIDs.contains(chat.id) ? Aero.accent : Aero.textMuted)
                    }
                }
            }
            .navigationTitle("Link chats")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { showLinker = false }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Save") {
                        let titles = Dictionary(
                            uniqueKeysWithValues: conversations.conversations.map { ($0.id, $0.title) }
                        )
                        ProjectStore.shared.linkChats(
                            id: projectID,
                            chatIds: Array(selectedChatIDs),
                            chatTitles: titles
                        )
                        showLinker = false
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    // MARK: Files tab — honest gate

    private var filesTab: some View {
        Text("File attachments aren't supported yet — projects hold files once on-device storage lands.")
            .font(Aero.caption())
            .foregroundStyle(Aero.textMuted)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: Activity tab — the store's real event log

    private func activityTab(_ project: ProjectStore.Project) -> some View {
        VStack(spacing: Aero.Spacing.s) {
            if project.events.isEmpty {
                Text("Nothing yet — actions you take on this project show up here.")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            ForEach(Array(project.events.reversed().enumerated()), id: \.offset) { _, event in
                AeroListRow(
                    title: event.text,
                    subtitle: relative(event.at),
                    leading: {
                        Image(systemName: "clock.arrow.circlepath")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(Aero.text)
                            .frame(width: 36, height: 36)
                            .background(Circle().fill(Aero.containerHigh))
                    },
                    trailing: {
                        EmptyView()
                    }
                )
            }
        }
    }

    // MARK: Members tab — honest single-member gate

    private var membersTab: some View {
        VStack(spacing: Aero.Spacing.s) {
            AeroListRow(
                title: "You",
                subtitle: "Owner",
                leading: {
                    Image(systemName: "person.crop.circle")
                        .font(.system(size: 20))
                        .foregroundStyle(Aero.textMuted)
                        .frame(width: 36, height: 36)
                        .background(Circle().fill(Aero.containerHigh))
                },
                trailing: {
                    Text("Owner")
                        .font(Aero.label())
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(Capsule().fill(Aero.container))
                        .foregroundStyle(Aero.accentDeep)
                }
            )
            Text("Team members arrive with accounts and sharing.")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: Helpers

    private func relative(_ iso: String) -> String {
        guard let then = GSFormatters.date(from: iso) else { return "earlier" } // cached ISO formatter
        let interval = Date().timeIntervalSince(then)
        switch interval {
        case ..<60: return "just now"
        case ..<3600: return "\(Int(interval / 60))m ago"
        case ..<86400: return "\(Int(interval / 3600))h ago"
        case ..<(7 * 86400): return "\(Int(interval / 86400))d ago"
        default: return "earlier"
        }
    }
}
