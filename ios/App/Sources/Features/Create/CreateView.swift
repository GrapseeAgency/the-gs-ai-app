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

/// CREATE tab root. Ten tool cards in a 2-column grid; each opens its own
/// workspace later — for now every generator hands off to chat, except the
/// assistant builder which routes to `.assistantCreate`.
struct CreateView: View {

    // MARK: Sample data

    private struct CreateTool: Identifiable {
        let id = UUID()
        let label: String
        let icon: String
        let detail: String
        let route: AeroRoute
    }

    private let tools: [CreateTool] = [
        .init(label: "AI image", icon: "wand.and.stars", detail: "Generate art from a prompt", route: .chat(nil)),
        .init(label: "Image edit", icon: "wand.and.rays", detail: "Retouch, extend, restyle", route: .chat(nil)),
        .init(label: "Document", icon: "doc.text", detail: "Reports, briefs and memos", route: .chat(nil)),
        .init(label: "Presentation", icon: "rectangle.on.rectangle", detail: "Decks from an outline", route: .chat(nil)),
        .init(label: "Spreadsheet", icon: "tablecells", detail: "Tables with formulas", route: .chat(nil)),
        .init(label: "Writing", icon: "pencil.line", detail: "Drafts in your voice", route: .chat(nil)),
        .init(label: "Code", icon: "chevron.left.forwardslash.chevron.right", detail: "Snippets, reviews and fixes", route: .chat(nil)),
        .init(label: "Diagram", icon: "rectangle.3.group", detail: "Flows, maps and schemas", route: .chat(nil)),
        .init(label: "Prompt builder", icon: "lightbulb", detail: "Sharper prompts, faster", route: .chat(nil)),
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
                        EmptyStateView(
                            icon: "hourglass",
                            title: "Nothing yet",
                            message: "Generated images, docs and decks will live here."
                        )
                    }
                }
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
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
                NavigationLink(value: tool.route) {
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
                .buttonStyle(KineticPressStyle())
            }
        }
    }
}
