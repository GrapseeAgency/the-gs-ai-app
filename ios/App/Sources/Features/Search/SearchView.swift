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

// MARK: - Search — one index over everything

/// Pushed via `.search`. Empty query shows recent searches, filter chips and
/// the empty state; a query filters the static samples by title and groups
/// results by kind with per-group counts.
struct SearchView: View {

    // MARK: Sample data

    private struct SampleResult: Identifiable {
        enum Kind: String, CaseIterable {
            case conversations = "Conversations"
            case messages = "Messages"
            case files = "Files"
            case assistants = "Assistants"
            case projects = "Projects"

            var icon: String {
                switch self {
                case .conversations: return "bubble.left"
                case .messages: return "text.quote"
                case .files: return "doc.text"
                case .assistants: return "smarttoy"
                case .projects: return "folder"
                }
            }
        }

        let id = UUID()
        let kind: Kind
        let title: String
        let detail: String
        let route: AeroRoute
    }

    @State private var query = ""
    @State private var activeFilters: Set<String> = []

    private let recentSearches = ["pricing", "kyoto", "swift concurrency", "q3 report"]
    private let filterOptions = ["Date", "Model", "Type", "Project", "Assistant"]

    private let samples: [SampleResult] = [
        .init(kind: .conversations, title: "Q3 pricing strategy", detail: "12 messages · 2h ago", route: .chat("demo-1")),
        .init(kind: .conversations, title: "Kyoto trip plan", detail: "8 messages · yesterday", route: .chat("demo-2")),
        .init(kind: .messages, title: "Saved: pricing strategy idea", detail: "Saved message · Library", route: .chat("demo-1")),
        .init(kind: .files, title: "Q3 report.pdf", detail: "PDF · 12 pages", route: .chat("demo-1")),
        .init(kind: .assistants, title: "Research Scout", detail: "Assistant · by GS Studio", route: .assistant("asst-1")),
        .init(kind: .projects, title: "Brand Refresh 2025", detail: "Project · 8 chats · 14 files", route: .project("project-brand"))
    ]

    private var trimmedQuery: String {
        query.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var matches: [SampleResult] {
        guard !trimmedQuery.isEmpty else { return [] }
        return samples.filter { $0.title.localizedCaseInsensitiveContains(trimmedQuery) }
    }

    // MARK: Body

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                StaggerIn(index: 0) { inputBar }
                if trimmedQuery.isEmpty {
                    StaggerIn(index: 1) { recentSection }
                    StaggerIn(index: 2) { filterSection }
                    StaggerIn(index: 3) {
                        EmptyStateView(
                            icon: "magnifyingglass",
                            title: "Search everything",
                            message: "Conversations, messages, files, assistants, projects and prompts — one index."
                        )
                    }
                } else if matches.isEmpty {
                    EmptyStateView(
                        icon: "tray",
                        title: "No results",
                        message: "Nothing matches “\(trimmedQuery)” yet — try another term or clear filters."
                    )
                } else {
                    resultsSection
                }
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
        .navigationTitle("Search everything")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.visible, for: .navigationBar)
    }

    // MARK: Input

    private var inputBar: some View {
        AeroInputBar(
            text: $query,
            placeholder: "Search conversations, files, assistants…",
            action: {}
        )
    }

    // MARK: Recent searches

    private var recentSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Recent searches")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Aero.Spacing.s) {
                    ForEach(recentSearches, id: \.self) { term in
                        AeroChip(text: term, action: { query = term })
                    }
                }
                .padding(.vertical, 2)
            }
        }
    }

    // MARK: Filter chips (toggle set)

    private var filterSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Filters")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Aero.Spacing.s) {
                    ForEach(filterOptions, id: \.self) { option in
                        AeroChip(
                            text: option,
                            selected: activeFilters.contains(option),
                            action: {
                                withAnimation(Aero.snappy) {
                                    if activeFilters.contains(option) {
                                        activeFilters.remove(option)
                                    } else {
                                        activeFilters.insert(option)
                                    }
                                }
                            }
                        )
                    }
                }
                .padding(.vertical, 2)
            }
        }
    }

    // MARK: Grouped results

    private var resultsSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.l) {
            ForEach(SampleResult.Kind.allCases, id: \.self) { kind in
                let items = matches.filter { $0.kind == kind }
                if !items.isEmpty {
                    VStack(alignment: .leading, spacing: Aero.Spacing.m) {
                        SectionHeader(
                            title: kind.rawValue,
                            actionTitle: "\(items.count) found",
                            action: {}
                        )
                        VStack(spacing: Aero.Spacing.s) {
                            ForEach(items) { result in
                                NavigationLink(value: result.route) {
                                    AeroListRow(
                                        title: result.title,
                                        subtitle: result.detail,
                                        leading: {
                                            Image(systemName: kind.icon)
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
                        }
                    }
                }
            }
        }
    }
}
