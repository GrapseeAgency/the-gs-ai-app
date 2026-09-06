import SwiftUI

/// Chat search — input + filter chips, live-filtered hits over the sample
/// set. Backend FTS5 wiring lands with the SQLite search phase (worklog,
/// Task 6-e).
struct ChatSearchView: View {

    private struct SearchHit: Identifiable {
        let id = UUID()
        let routeID: String
        let title: String
        let snippet: String
        let when: String
    }

    @State private var query = ""
    @State private var activeFilters: Set<String> = []

    private static let filterChips = ["This week", "Has files", "Model: GS Balanced"]

    private let samples: [SearchHit] = [
        SearchHit(routeID: "demo-1", title: "Q3 pricing strategy", snippet: "You: send the revised deck?", when: "2h"),
        SearchHit(routeID: "demo-2", title: "Kyoto trip planning", snippet: "GS: temples worth the early train…", when: "5h"),
        SearchHit(routeID: "demo-3", title: "Kotlin coroutines debug", snippet: "You: why does launch block here?", when: "1d"),
        SearchHit(routeID: "demo-4", title: "Brand voice workshop", snippet: "GS: three tone pillars emerged…", when: "3d")
    ]

    private var results: [SearchHit] {
        let term = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !term.isEmpty else { return samples }
        return samples.filter {
            $0.title.localizedCaseInsensitiveContains(term) ||
            $0.snippet.localizedCaseInsensitiveContains(term)
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.l) {
            Text("Search chats")
                .font(Aero.displayTitle())
                .foregroundStyle(Aero.text)

            AeroInputBar(text: $query, placeholder: "Search messages and chats")

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Aero.Spacing.s) {
                    ForEach(Self.filterChips, id: \.self) { chip in
                        AeroChip(text: chip, selected: activeFilters.contains(chip)) {
                            if activeFilters.contains(chip) {
                                activeFilters.remove(chip)
                            } else {
                                activeFilters.insert(chip)
                            }
                        }
                    }
                }
            }

            ScrollView {
                if results.isEmpty {
                    EmptyStateView(
                        icon: "magnifyingglass",
                        title: "No results",
                        message: "Nothing matched “\(query)”. Try different words or clear a filter."
                    )
                    .padding(.top, Aero.Spacing.xl)
                } else {
                    VStack(spacing: Aero.Spacing.s) {
                        ForEach(results) { hit in
                            NavigationLink(value: AeroRoute.chat(hit.routeID)) {
                                AeroListRow(
                                    title: hit.title,
                                    subtitle: "\(hit.when) · \(hit.snippet)",
                                    leading: {
                                        Image(systemName: "bubble.left")
                                            .font(.system(size: 14))
                                            .foregroundStyle(Aero.text)
                                            .frame(width: 36, height: 36)
                                            .background(Circle().fill(Aero.containerHigh))
                                    },
                                    trailing: {
                                        EmptyView()
                                    }
                                )
                            }
                            .buttonStyle(KineticPressStyle())
                        }
                    }
                }
            }
        }
        .padding(.horizontal, Aero.Spacing.m)
        .padding(.top, Aero.Spacing.s)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Aero.background.ignoresSafeArea())
    }
}
