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

/// EXPLORE tab root, ChatGPT-store style: horizontal discovery rows —
/// Top picks, Trending now, Popular prompts, Featured AI tools and — when
/// the user has created assistants — Made by you. One catalogue feeds the
/// assistant rows, shared with the Assistants hub (`AssistantSample.catalog`
/// plus published user creations), so names, ratings and usage never drift
/// between surfaces. Category chips are built from the live catalogue —
/// every chip leads somewhere real. Search + chip filter the rows together;
/// serif prompt cards carry the editorial voice. Every card pushes an
/// `AeroRoute` through NavigationLink(value:).
struct ExploreView: View {

    // MARK: Row-local sample content

    private struct PromptCard: Identifiable {
        /// Identity from content — a UUID minted per struct recreation would
        /// re-identify every card on each keystroke and kill row continuity.
        var id: String { text }
        let text: String
        let caption: String
        let category: String
    }

    private struct ToolCard: Identifiable {
        var id: String { name }
        let name: String
        let detail: String
        let icon: String
        let category: String
        let starter: String
    }

    @ObservedObject private var store = AssistantsStore.shared
    @State private var selectedCategory = "All"
    @State private var query = ""
    @FocusState private var searchFocused: Bool
    @State private var pendingDelete: AssistantSample?

    private let prompts: [PromptCard] = [
        .init(text: "Write a launch announcement in our brand voice", caption: "Writing · 8.2k uses", category: "Writing"),
        .init(text: "Explain this quarter's numbers to a new hire", caption: "Business · 6.4k uses", category: "Business"),
        .init(text: "Design a 7-day learning plan for Swift", caption: "Education · 5.1k uses", category: "Education")
    ]

    private let tools: [ToolCard] = [
        .init(name: "Web Researcher", detail: "Live sources, citations and clean reports.", icon: "magnifyingglass", category: "Research",
              starter: "Research this topic with live sources and citations: "),
        .init(name: "Diagrammer", detail: "Turn messy ideas into clean diagrams.", icon: "rectangle.3.group", category: "Design",
              starter: "Turn these ideas into a clean diagram: ")
    ]

    // MARK: Catalogue — one source, shared with the Assistants hub

    /// Curated samples plus the user's published creations (archive
    /// respects the owner's intent). Identical merge to the Marketplace tab.
    private var catalogue: [AssistantSample] {
        AssistantSample.catalog + store.userAssistants.filter { $0.published && !$0.archived }
    }

    /// Chips built from the live catalogue — every chip leads somewhere real.
    private var categories: [String] {
        ["All"] + Set(catalogue.map(\.category)).sorted()
    }

    // MARK: Filtering

    private func matches(_ category: String) -> Bool {
        selectedCategory == "All" || category == selectedCategory
    }

    /// Combined query gate — empty term passes everything, else any-field contains.
    private func containsTerm(_ fields: [String]) -> Bool {
        term.isEmpty || fields.contains { $0.localizedCaseInsensitiveContains(term) }
    }

    private var term: String {
        query.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var filtered: [AssistantSample] {
        catalogue.filter { matches($0.category) && containsTerm([$0.name, $0.category, $0.desc]) }
    }
    private var topPicks: [AssistantSample] {
        Array(filtered.prefix(4))
    }
    private var trending: [AssistantSample] {
        filtered.sorted { $0.uses > $1.uses }
    }
    private var mine: [AssistantSample] {
        store.userAssistants
            .filter { !$0.archived && containsTerm([$0.name, $0.category]) }
    }
    private var filteredPrompts: [PromptCard] {
        prompts.filter { matches($0.category) && containsTerm([$0.text, $0.caption]) }
    }
    private var filteredTools: [ToolCard] {
        tools.filter { matches($0.category) && containsTerm([$0.name, $0.detail]) }
    }

    private var nothingToShow: Bool {
        filtered.isEmpty && filteredPrompts.isEmpty && filteredTools.isEmpty
    }

    private var nothingMessage: String {
        term.isEmpty
            ? "No assistants, prompts or tools in this category — try another one."
            : "Nothing matched “\(term)”. Try different words or another category."
    }

    // MARK: Body

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                StaggerIn(index: 0) { header }
                StaggerIn(index: 1) { searchRow }
                StaggerIn(index: 2) { categoryChips }
                if nothingToShow {
                    EmptyStateView(
                        icon: "sparkles",
                        title: "Nothing here yet",
                        message: nothingMessage
                    )
                } else {
                    if !topPicks.isEmpty { StaggerIn(index: 3) { picksRow } }
                    if !trending.isEmpty { StaggerIn(index: 4) { trendingRow } }
                    if !filteredPrompts.isEmpty { StaggerIn(index: 5) { promptsRow } }
                    if !filteredTools.isEmpty { StaggerIn(index: 6) { toolsRow } }
                    if !mine.isEmpty { StaggerIn(index: 7) { mineRow } }
                }
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
        .navigationTitle("Explore")
        .navigationBarTitleDisplayMode(.inline)
        .scrollDismissesKeyboard(.interactively)
        .confirmationDialog(
            "Delete “\(pendingDelete?.name ?? "")” from My assistants? Chats you started with it stay in your history.",
            isPresented: Binding(
                get: { pendingDelete != nil },
                set: { if !$0 { pendingDelete = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                if let id = pendingDelete?.id {
                    AssistantsStore.shared.remove(id)
                    GSHaptics.warning()
                }
                pendingDelete = nil
            }
            Button("Keep", role: .cancel) { pendingDelete = nil }
        }
        .onAppear { GSHaptics.prepare() }
>>>>>>> 4418691 (c5dbb0b6-b487-4fa4-9552-e757cb13f3a7)
    }

    // MARK: Header

    private var header: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.xs) {
            // The screen title lives in the inline nav bar now (Task 85-e I7)
            // — the duplicate in-content header text was dropped so the name
            // doesn't render twice. The tagline stays.
            Text("The AI app store — assistants, prompts and tools.")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
        }
    }

    // MARK: Search row — live over the catalogue

    private var searchRow: some View {
        HStack(spacing: Aero.Spacing.s) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Aero.textMuted)
            TextField("Search assistants, prompts, tools…", text: $query)
                .font(Aero.body())
                .foregroundStyle(Aero.text)
                .focused($searchFocused)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.search)
                .onSubmit { searchFocused = false }
            if !query.isEmpty {
                Button {
                    query = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 14))
                        .foregroundStyle(Aero.textMuted)
                }
                .buttonStyle(KineticPressStyle())
                .accessibilityLabel("Clear search")
            }
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
                            GSHaptics.select()
                            withAnimation(Aero.snappy) { selectedCategory = category }
                        }
                    )
                }
            }
            .padding(.vertical, 2)
        }
    }

    // MARK: Rows — ChatGPT-store carousels

    /// Fixed-width horizontal carousel with edge-aligned padding that keeps
    /// the first/last card aligned with the content column.
    private func carousel<Card: View>(@ViewBuilder cards: () -> Card) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            LazyHStack(alignment: .top, spacing: Aero.Spacing.s) {
                cards()
            }
            .padding(.horizontal, Aero.Spacing.xs)
            .padding(.vertical, 2)
        }
    }

    private var picksRow: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            SectionHeader(title: "Top picks")
            carousel {
                ForEach(topPicks) { assistant in
                    NavigationLink(value: AeroRoute.assistant(assistant.id)) {
                        AeroCard {
                            VStack(alignment: .leading, spacing: 0) {
                                Image(systemName: "smarttoy")
                                    .font(.system(size: 15, weight: .medium))
                                    .foregroundStyle(Aero.text)
                                    .frame(width: 40, height: 40)
                                    .background(Circle().fill(Aero.container))
                                Spacer(minLength: 0)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(assistant.name)
                                        .font(Aero.title())
                                        .foregroundStyle(Aero.text)
                                        .lineLimit(1)
                                    Text("★ \(assistant.ratingText) · \(assistant.usesText) uses")
                                        .font(Aero.caption())
                                        .foregroundStyle(Aero.textMuted)
                                        .lineLimit(1)
                                    Text(assistant.category)
                                        .font(Aero.caption())
                                        .foregroundStyle(Aero.accent)
                                        .lineLimit(1)
                                }
                            }
                        }
                        .frame(width: 232, height: 148)
                    }
                    .buttonStyle(KineticPressStyle())
                }
            }
        }
    }

    private var trendingRow: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            SectionHeader(title: "Trending now")
            carousel {
                ForEach(Array(trending.enumerated()), id: \.element.id) { index, assistant in
                    NavigationLink(value: AeroRoute.assistant(assistant.id)) {
                        AeroCard {
                            VStack(alignment: .leading, spacing: 0) {
                                Text("\(index + 1)")
                                    .font(Aero.display())
                                    .foregroundStyle(Aero.accent)
                                Spacer(minLength: 0)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(assistant.name)
                                        .font(Aero.title())
                                        .foregroundStyle(Aero.text)
                                        .lineLimit(2)
                                    Text("\(assistant.usesText) uses")
                                        .font(Aero.caption())
                                        .foregroundStyle(Aero.textMuted)
                                        .lineLimit(1)
                                }
                            }
                        }
                        .frame(width: 148, height: 140)
                    }
                    .buttonStyle(KineticPressStyle())
                }
            }
        }
    }

    private var promptsRow: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            SectionHeader(title: "Popular prompts")
            carousel {
                ForEach(filteredPrompts) { prompt in
                    NavigationLink(value: AeroRoute.chatPrefill(prompt.text)) {
                        AeroCard {
                            VStack(alignment: .leading, spacing: 0) {
                                Text("“\(prompt.text)”")
                                    .font(Aero.headline())   // serif — editorial prompt voice
                                    .foregroundStyle(Aero.text)
                                    .fixedSize(horizontal: false, vertical: true)
                                    .multilineTextAlignment(.leading)
                                Spacer(minLength: 0)
                                Text(prompt.caption)
                                    .font(Aero.caption())
                                    .foregroundStyle(Aero.textMuted)
                            }
                        }
                        .frame(width: 248, height: 150)
                    }
                    .buttonStyle(KineticPressStyle())
                }
            }
        }
    }

    private var toolsRow: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            SectionHeader(title: "Featured AI tools")
            carousel {
                ForEach(filteredTools) { tool in
                    NavigationLink(value: AeroRoute.chatPrefill(tool.starter)) {
                        AeroCard {
                            VStack(alignment: .leading, spacing: 0) {
                                Image(systemName: tool.icon)
                                    .font(.system(size: 15, weight: .medium))
                                    .foregroundStyle(Aero.text)
                                    .frame(width: 40, height: 40)
                                    .background(Circle().fill(Aero.container))
                                Spacer(minLength: 0)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(tool.name)
                                        .font(Aero.title())
                                        .foregroundStyle(Aero.text)
                                        .lineLimit(1)
                                    Text(tool.detail)
                                        .font(Aero.caption())
                                        .foregroundStyle(Aero.textMuted)
                                        .fixedSize(horizontal: false, vertical: true)
                                        .multilineTextAlignment(.leading)
                                    Image(systemName: "chevron.right")
                                        .font(.system(size: 12, weight: .semibold))
                                        .foregroundStyle(Aero.textMuted)
                                }
                            }
                        }
                        .frame(width: 236, height: 172)
                    }
                    .buttonStyle(KineticPressStyle())
                }
            }
        }
    }

    private var mineRow: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.s) {
            SectionHeader(title: "Made by you")
            carousel {
                ForEach(mine) { assistant in
                    NavigationLink(value: AeroRoute.assistant(assistant.id)) {
                        AeroCard {
                            VStack(alignment: .leading, spacing: 0) {
                                Image(systemName: "person.crop.circle")
                                    .font(.system(size: 15, weight: .medium))
                                    .foregroundStyle(Aero.text)
                                    .frame(width: 40, height: 40)
                                    .background(Circle().fill(Aero.container))
                                Spacer(minLength: 0)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(assistant.name)
                                        .font(Aero.title())
                                        .foregroundStyle(Aero.text)
                                        .lineLimit(1)
                                    Text(byline(assistant))
                                        .font(Aero.caption())
                                        .foregroundStyle(Aero.textMuted)
                                        .lineLimit(1)
                                }
                            }
                        }
                        .frame(width: 184, height: 140)
                    }
                    .buttonStyle(KineticPressStyle())
                    .contextMenu { mineContextMenu(assistant) }
                }
            }
        }
    }

    /// Long-press actions on the reader's own creations — the same set the
    /// Assistants hub offers, so the two surfaces never drift.
    private func mineContextMenu(_ assistant: AssistantSample) -> some View {
        let fav = store.favourites.contains(assistant.id)
        return Group {
            Button {
                GSHaptics.select()
                store.toggleFavourite(assistant.id)
            } label: {
                Label(
                    fav ? "Remove from favourites" : "Favourite",
                    systemImage: fav ? "heart.slash" : "heart"
                )
            }
            Button {
                GSHaptics.success()
                store.togglePin(assistant.id)
            } label: {
                Label(assistant.pinned ? "Unpin" : "Pin to top", systemImage: "pin")
            }
            Button {
                GSHaptics.success()
                store.setArchived(assistant.id, true)
            } label: {
                Label("Archive", systemImage: "archivebox")
            }
            Divider()
            Button(role: .destructive) {
                pendingDelete = assistant
            } label: {
                Label("Delete…", systemImage: "trash")
            }
        }
    }

    /// Ownership lens: "You", plus the workspace flags that matter here.
    private func byline(_ assistant: AssistantSample) -> String {
        var flags: [String] = []
        if assistant.pinned { flags.append("Pinned") }
        if assistant.published { flags.append("Published") }
        return flags.isEmpty ? "You" : "You · \(flags.joined(separator: " · "))"
    }
}
