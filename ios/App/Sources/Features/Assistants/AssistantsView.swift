import SwiftUI

// MARK: - Sample data (inline, replaced by the real store later)

/// Inline sample assistant powering the marketplace + workspace tabs.
struct AssistantSample: Identifiable, Hashable, Codable {
    let id: String
    let name: String
    let category: String
    let desc: String
    let uses: Int
    let rating: Double
    var isFav: Bool
    var published: Bool
    // User-created assistants carry the builder's extras; samples omit them.
    var instructions: String? = nil
    var starters: [String] = []
    var capabilities: [String] = []

    /// "12.4k" style usage label.
    var usesText: String {
        uses >= 1000 ? String(format: "%.1fk", Double(uses) / 1000) : "\(uses)"
    }

    var ratingText: String { String(format: "%.1f", rating) }

    static let catalog: [AssistantSample] = [
        AssistantSample(id: "asst-1", name: "Writing Coach", category: "Writing", desc: "Tightens drafts while keeping your voice, coaching structure line by line.", uses: 12400, rating: 4.8, isFav: true, published: true),
        AssistantSample(id: "asst-2", name: "Code Reviewer", category: "Coding", desc: "Reviews diffs for bugs, style and security, then suggests concrete patches.", uses: 9800, rating: 4.9, isFav: false, published: true),
        AssistantSample(id: "asst-3", name: "Research Analyst", category: "Research", desc: "Digests sources into briefs with balanced findings and a citation for every claim.", uses: 7600, rating: 4.7, isFav: false, published: false),
        AssistantSample(id: "asst-4", name: "Language Tutor", category: "Language", desc: "Conversational drills with gentle corrections and a weekly practice plan.", uses: 6100, rating: 4.6, isFav: false, published: false),
        AssistantSample(id: "asst-5", name: "Meeting Summariser", category: "Productivity", desc: "Turns raw transcripts into decisions, owners and crisp next steps.", uses: 5200, rating: 4.7, isFav: false, published: false),
        AssistantSample(id: "asst-6", name: "Brainstorm Partner", category: "Creativity", desc: "Pushes past the obvious with wilder angles, provocations and quick concept sketches.", uses: 4400, rating: 4.5, isFav: true, published: false),
        AssistantSample(id: "asst-7", name: "Data Cruncher", category: "Data", desc: "Explains spreadsheets, runs quick statistics and drafts charts that read clearly.", uses: 3900, rating: 4.6, isFav: false, published: false),
        AssistantSample(id: "asst-8", name: "Brand Strategist", category: "Business", desc: "Positions products, sharpens messaging and stress-tests campaign ideas before launch.", uses: 2800, rating: 4.4, isFav: false, published: false)
    ]
}

// MARK: - Assistants hub

struct AssistantsView: View {

    @ObservedObject private var store = AssistantsStore.shared

    private enum Segment: String, CaseIterable, Identifiable {
        case marketplace = "Marketplace"
        case mine = "My assistants"
        case favourites = "Favourites"
        case published = "Published"

        var id: String { rawValue }
    }

    @State private var selection: Segment = .marketplace

    private let gridColumns = [
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible())
    ]

    private var featured: AssistantSample { AssistantSample.catalog[0] }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                header
                segmentChips
                content
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                NavigationLink(value: AeroRoute.assistantCreate) {
                    Image(systemName: "plus.circle")
                        .font(.system(size: 18))
                        .foregroundStyle(Aero.text)
                }
            }
        }
    }

    // MARK: Header + segments

    private var header: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.xs) {
            Text("Assistants")
                .font(Aero.displayTitle())
                .foregroundStyle(Aero.text)
            Text("Hire a specialist or build your own.")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
        }
    }

    private var segmentChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Aero.Spacing.s) {
                ForEach(Segment.allCases) { segment in
                    AeroChip(text: segment.rawValue, selected: selection == segment) {
                        selection = segment
                    }
                }
            }
        }
    }

    // MARK: Segment content

    @ViewBuilder
    private var content: some View {
        switch selection {
        case .marketplace:
            marketplace
        case .mine:
            filteredList(
                store.userAssistants,
                emptyTitle: "No assistants yet",
                emptyMessage: "Create your first assistant and it will live here."
            )
        case .favourites:
            filteredList(
                AssistantSample.catalog.filter(\.isFav),
                emptyTitle: "Nothing starred yet",
                emptyMessage: "Tap the heart on any assistant to keep it close."
            )
        case .published:
            filteredList(
                AssistantSample.catalog.filter(\.published) + store.userAssistants.filter(\.published),
                emptyTitle: "Nothing published yet",
                emptyMessage: "Publish an assistant to share it on the marketplace."
            )
        }
    }

    // MARK: Marketplace

    private var marketplace: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            featuredCard
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                SectionHeader(title: "All assistants")
                LazyVGrid(columns: gridColumns, spacing: 12) {
                    ForEach(AssistantSample.catalog + store.userAssistants.filter(\.published)) { assistant in
                        NavigationLink(value: AeroRoute.assistant(assistant.id)) {
                            AssistantGridCard(assistant: assistant)
                        }
                        .buttonStyle(KineticPressStyle())
                    }
                }
            }
        }
    }

    /// Featured assistant — accent left bar marks the spotlight.
    private var featuredCard: some View {
        NavigationLink(value: AeroRoute.assistant(featured.id)) {
            AeroCard {
                HStack(alignment: .top, spacing: Aero.Spacing.m) {
                    Rectangle()
                        .fill(Aero.accent)
                        .frame(width: 4)
                    VStack(alignment: .leading, spacing: Aero.Spacing.xs) {
                        Text("FEATURED")
                            .font(Aero.label())
                            .foregroundStyle(Aero.accent)
                        Text(featured.name)
                            .font(Aero.headline())
                            .foregroundStyle(Aero.text)
                        Text(featured.desc)
                            .font(Aero.caption())
                            .foregroundStyle(Aero.textMuted)
                        Text("\(featured.usesText) uses · ★ \(featured.ratingText)")
                            .font(Aero.caption())
                            .foregroundStyle(Aero.textMuted)
                    }
                }
            }
        }
        .buttonStyle(KineticPressStyle())
    }

    // MARK: Filtered lists (My assistants / Favourites / Published)

    private func filteredList(
        _ assistants: [AssistantSample],
        emptyTitle: String,
        emptyMessage: String
    ) -> some View {
        Group {
            if assistants.isEmpty {
                EmptyStateView(icon: "tray", title: emptyTitle, message: emptyMessage)
            } else {
                VStack(spacing: Aero.Spacing.s) {
                    ForEach(assistants) { assistant in
                        NavigationLink(value: AeroRoute.assistant(assistant.id)) {
                            AeroListRow(
                                title: assistant.name,
                                subtitle: "\(assistant.category) · \(assistant.usesText) uses · ★ \(assistant.ratingText)",
                                leading: {
                                    Image(systemName: "smarttoy")
                                        .font(.system(size: 15))
                                        .foregroundStyle(Aero.accent)
                                        .frame(width: 36, height: 36)
                                        .background(Circle().fill(Aero.container))
                                },
                                trailing: {
                                    HStack(spacing: Aero.Spacing.xs) {
                                        if assistant.isFav {
                                            Image(systemName: "heart.fill")
                                                .font(.system(size: 12))
                                                .foregroundStyle(Aero.accent)
                                        }
                                        Image(systemName: "chevron.right")
                                            .font(.system(size: 12))
                                            .foregroundStyle(Aero.textMuted)
                                    }
                                }
                            )
                        }
                        .buttonStyle(KineticPressStyle())
                    }
                }
            }
        }
    }
}

// MARK: - Grid card

private struct AssistantGridCard: View {
    let assistant: AssistantSample

    var body: some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                Image(systemName: "smarttoy")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundStyle(Aero.accent)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(Aero.container))
                Text(assistant.name)
                    .font(Aero.title())
                    .foregroundStyle(Aero.text)
                    .lineLimit(1)
                Text(assistant.category)
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
                Text("\(assistant.usesText) · ★ \(assistant.ratingText)")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
            }
        }
    }
}
