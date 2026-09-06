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

// MARK: - Explore — the discovery layer

/// EXPLORE tab root. Category chips filter the three sample sections live;
/// every card pushes an `AeroRoute` through NavigationLink(value:).
struct ExploreView: View {

    // MARK: Sample data

    private struct TrendingAssistant: Identifiable {
        let id: String
        let name: String
        let byline: String
        let category: String
    }

    private struct PromptCard: Identifiable {
        let id = UUID()
        let text: String
        let caption: String
        let category: String
    }

    private struct ToolCard: Identifiable {
        let id = UUID()
        let name: String
        let detail: String
        let icon: String
        let category: String
    }

    @State private var selectedCategory = "All"

    private let categories = [
        "All", "Coding", "Education", "Business", "Writing", "Productivity",
        "Research", "Design", "Mathematics", "Language", "Science", "Entertainment"
    ]

    private let assistants: [TrendingAssistant] = [
        .init(id: "asst-1", name: "Research Scout", byline: "by GS Studio · 12.4k uses · ★ 4.8", category: "Research"),
        .init(id: "asst-2", name: "Copysmith", byline: "by Inkwell · 9.8k uses · ★ 4.7", category: "Writing"),
        .init(id: "asst-3", name: "Code Muse", byline: "by Nova Labs · 8.1k uses · ★ 4.6", category: "Coding"),
        .init(id: "asst-4", name: "Tutor Pro", byline: "by Bright Works · 7.5k uses · ★ 4.9", category: "Education")
    ]

    private let prompts: [PromptCard] = [
        .init(text: "Write a launch announcement in our brand voice", caption: "Writing · 8.2k uses", category: "Writing"),
        .init(text: "Explain this quarter's numbers to a new hire", caption: "Business · 6.4k uses", category: "Business"),
        .init(text: "Design a 7-day learning plan for Swift", caption: "Education · 5.1k uses", category: "Education")
    ]

    private let tools: [ToolCard] = [
        .init(name: "Web Researcher", detail: "Live sources, citations and clean reports.", icon: "magnifyingglass", category: "Research"),
        .init(name: "Diagrammer", detail: "Turn messy ideas into clean diagrams.", icon: "rectangle.3.group", category: "Design")
    ]

    // MARK: Filtering

    private func matches(_ category: String) -> Bool {
        selectedCategory == "All" || category == selectedCategory
    }

    private var filteredAssistants: [TrendingAssistant] { assistants.filter { matches($0.category) } }
    private var filteredPrompts: [PromptCard] { prompts.filter { matches($0.category) } }
    private var filteredTools: [ToolCard] { tools.filter { matches($0.category) } }

    // MARK: Body

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                StaggerIn(index: 0) { header }
                StaggerIn(index: 1) { searchRow }
                StaggerIn(index: 2) { categoryChips }
                if filteredAssistants.isEmpty && filteredPrompts.isEmpty && filteredTools.isEmpty {
                    EmptyStateView(
                        icon: "sparkles",
                        title: "Nothing here yet",
                        message: "No assistants, prompts or tools in this category — try another one."
                    )
                } else {
                    if !filteredAssistants.isEmpty { StaggerIn(index: 3) { trendingSection } }
                    if !filteredPrompts.isEmpty { StaggerIn(index: 4) { promptsSection } }
                    if !filteredTools.isEmpty { StaggerIn(index: 5) { toolsSection } }
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
            Text("Explore")
                .font(Aero.displayTitle())
                .foregroundStyle(Aero.text)
            Text("The AI app store — assistants, prompts and tools.")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
        }
    }

    // MARK: Static search row

    private var searchRow: some View {
        HStack(spacing: Aero.Spacing.s) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Aero.textMuted)
            Text("Search assistants, prompts, tools…")
                .font(Aero.body())
                .foregroundStyle(Aero.textMuted)
            Spacer()
        }
        .padding(.horizontal, Aero.Spacing.m)
        .padding(.vertical, 13)
        .background(Capsule().fill(Aero.container))
        .overlay(Capsule().stroke(Aero.outline, lineWidth: 1))
    }

    // MARK: Category chips

    private var categoryChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Aero.Spacing.s) {
                ForEach(categories, id: \.self) { category in
                    AeroChip(
                        text: category,
                        selected: selectedCategory == category,
                        action: {
                            withAnimation(Aero.snappy) { selectedCategory = category }
                        }
                    )
                }
            }
            .padding(.vertical, 2)
        }
    }

    // MARK: Trending assistants

    private var trendingSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Trending assistants")
            VStack(spacing: Aero.Spacing.s) {
                ForEach(filteredAssistants) { assistant in
                    NavigationLink(value: AeroRoute.assistant(assistant.id)) {
                        AeroCard {
                            HStack(spacing: Aero.Spacing.s) {
                                Image(systemName: "smarttoy")
                                    .font(.system(size: 15, weight: .medium))
                                    .foregroundStyle(Aero.text)
                                    .frame(width: 40, height: 40)
                                    .background(Circle().fill(Aero.container))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(assistant.name)
                                        .font(Aero.title())
                                        .foregroundStyle(Aero.text)
                                        .lineLimit(1)
                                    Text(assistant.byline)
                                        .font(Aero.caption())
                                        .foregroundStyle(Aero.textMuted)
                                        .lineLimit(1)
                                }
                                Spacer(minLength: 0)
                                Image(systemName: "star.fill")
                                    .font(.system(size: 13))
                                    .foregroundStyle(Aero.accent)
                            }
                        }
                    }
                    .buttonStyle(KineticPressStyle())
                }
            }
        }
    }

    // MARK: Popular prompts

    private var promptsSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Popular prompts")
            VStack(spacing: Aero.Spacing.s) {
                ForEach(filteredPrompts) { prompt in
                    NavigationLink(value: AeroRoute.chat(nil)) {
                        AeroCard {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("“\(prompt.text)”")
                                    .font(Aero.headline())   // serif — editorial prompt voice
                                    .foregroundStyle(Aero.text)
                                    .fixedSize(horizontal: false, vertical: true)
                                Text(prompt.caption)
                                    .font(Aero.caption())
                                    .foregroundStyle(Aero.textMuted)
                            }
                        }
                    }
                    .buttonStyle(KineticPressStyle())
                }
            }
        }
    }

    // MARK: Featured AI tools

    private var toolsSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Featured AI tools")
            VStack(spacing: Aero.Spacing.s) {
                ForEach(filteredTools) { tool in
                    NavigationLink(value: AeroRoute.chat(nil)) {
                        AeroCard {
                            HStack(spacing: Aero.Spacing.s) {
                                Image(systemName: tool.icon)
                                    .font(.system(size: 15, weight: .medium))
                                    .foregroundStyle(Aero.text)
                                    .frame(width: 40, height: 40)
                                    .background(Circle().fill(Aero.container))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(tool.name)
                                        .font(Aero.title())
                                        .foregroundStyle(Aero.text)
                                        .lineLimit(1)
                                    Text(tool.detail)
                                        .font(Aero.caption())
                                        .foregroundStyle(Aero.textMuted)
                                        .lineLimit(1)
                                }
                                Spacer(minLength: 0)
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(Aero.textMuted)
                            }
                        }
                    }
                    .buttonStyle(KineticPressStyle())
                }
            }
        }
    }
}
