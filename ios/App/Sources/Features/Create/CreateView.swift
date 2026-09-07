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

// MARK: - Create — every generator, one place

/// CREATE tab root. Ten tool cards in a 2-column grid. Five tools open their
/// own full-screen workspaces (AI image / Image edit / Writing / Code /
/// Prompt builder); the rest hand off to chat, and the assistant builder
/// routes to `.assistantCreate`.
struct CreateView: View {

    /// Full-screen workspaces opened straight from the tool tiles
    /// (Image / Code / Writing / Prompt builder) instead of routing to chat.
    /// Named CreateToolKind because the sample-data struct above owns CreateTool.
    private enum CreateToolKind: String, Identifiable {
        case image, code, writing, prompt

        var id: String { rawValue }
    }

    @State private var activeTool: CreateToolKind?

    // Real creations: everything the studios saved to the Library, newest first.
    @State private var creations: [LibraryItem] = []

    // MARK: Sample data

    private struct CreateTool: Identifiable {
        let id = UUID()
        let label: String
        let icon: String
        let detail: String
        let route: AeroRoute
        var workspace: CreateToolKind? = nil
    }

    private let tools: [CreateTool] = [
        .init(label: "AI image", icon: "wand.and.stars", detail: "Generate art from a prompt", route: .chat(nil), workspace: .image),
        .init(label: "Image edit", icon: "wand.and.rays", detail: "Retouch, extend, restyle", route: .chat(nil), workspace: .image),
        .init(label: "Document", icon: "doc.text", detail: "Reports, briefs and memos", route: .chatPrefill("Draft a document — brief, memo or report — about: ")),
        .init(label: "Presentation", icon: "rectangle.on.rectangle", detail: "Decks from an outline", route: .chatPrefill("Draft a slide deck outline about: ")),
        .init(label: "Spreadsheet", icon: "tablecells", detail: "Tables with formulas", route: .chatPrefill("Build a spreadsheet with formulas for: ")),
        .init(label: "Writing", icon: "pencil.line", detail: "Drafts in your voice", route: .chat(nil), workspace: .writing),
        .init(label: "Code", icon: "chevron.left.forwardslash.chevron.right", detail: "Snippets, reviews and fixes", route: .chat(nil), workspace: .code),
        .init(label: "Diagram", icon: "rectangle.3.group", detail: "Flows, maps and schemas", route: .chatPrefill("Describe the system or flow to diagram: ")),
        .init(label: "Prompt builder", icon: "lightbulb", detail: "Sharper prompts, faster", route: .chat(nil), workspace: .prompt),
        .init(label: "Assistant builder", icon: "smarttoy", detail: "Build your own persona", route: .assistantCreate)
    ]

    // MARK: Body

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                StaggerIn(index: 0) { header }
                StaggerIn(index: 1) { toolGrid }
                StaggerIn(index: 2) {
                    VStack(alignment: .leading, spacing: Aero.Spacing.m) {
                        SectionHeader(title: "Recent creations")
                        if creations.isEmpty {
                            EmptyStateView(
                                icon: "hourglass",
                                title: "Nothing yet",
                                message: "Generated images, docs and decks will live here."
                            )
                        } else {
                            VStack(spacing: Aero.Spacing.s) {
                                ForEach(Array(creations.prefix(6))) { item in
                                    creationRow(item)
                                }
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
        .onAppear {
            creations = ConversationStore.shared.savedLibraryItems()
        }
        .toolbar(.hidden, for: .navigationBar)
        .fullScreenCover(item: $activeTool) { tool in
            switch tool {
            case .image: ImageStudioView()
            case .code: CodeWorkspaceView()
            case .writing: WritingStudioView()
            case .prompt: PromptBuilderView()
            }
        }
    }

    // MARK: Header

    private var header: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.xs) {
            Text("Make anything")
                .font(Aero.displayTitle())
                .foregroundStyle(Aero.text)
            Text("Pick a tool — every generator gets its own workspace.")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
        }
    }

    // MARK: Tool grid

    private var toolGrid: some View {
        LazyVGrid(
            columns: [
                GridItem(.flexible(), spacing: 12),
                GridItem(.flexible(), spacing: 12)
            ],
            spacing: 12
        ) {
            ForEach(tools) { tool in
                if let workspace = tool.workspace {
                    Button {
                        activeTool = workspace
                    } label: {
                        toolCard(tool)
                    }
                    .buttonStyle(KineticPressStyle())
                } else {
                    NavigationLink(value: tool.route) {
                        toolCard(tool)
                    }
                    .buttonStyle(KineticPressStyle())
                }
            }
        }
    }

    /// A real creation: taps back into chat with the work seeded, so the
    /// studio output can be refined — same continue semantics as the Library.
    private func creationRow(_ item: LibraryItem) -> some View {
        NavigationLink(value: AeroRoute.chatPrefill(item.content)) {
            AeroListRow(
                title: item.title,
                subtitle: "\(kindLabel(item.kind)) · Library",
                leading: {
                    Image(systemName: kindSymbol(item.kind))
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
    }

    private func kindLabel(_ kind: String) -> String {
        switch kind {
        case "image": return "Image"
        case "document": return "Document"
        default: return "Creation"
        }
    }

    private func kindSymbol(_ kind: String) -> String {
        switch kind {
        case "image": return "photo"
        case "document": return "doc.text"
        default: return "wand.and.stars"
        }
    }

    /// The card content is shared by both the NavigationLink (chat-routed
    /// tools) and the Button (full-screen workspace tools) — identical layout
    /// and styling to the original grid.
    private func toolCard(_ tool: CreateTool) -> some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                Image(systemName: tool.icon)
                    .font(.system(size: 17, weight: .medium))
                    .foregroundStyle(Aero.text)
                    .frame(width: 44, height: 44)
                    .background(RoundedRectangle(cornerRadius: 14).fill(Aero.container))
                Text(tool.label)
                    .font(Aero.title())
                    .foregroundStyle(Aero.text)
                    .lineLimit(1)
                Text(tool.detail)
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}
