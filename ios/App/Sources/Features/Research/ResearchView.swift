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

// MARK: - Research — multi-source answers with citations

/// Full-screen research workspace (presented via .fullScreenCover). Owns its
/// chrome: close control, serif title, mode chip. Answers and sources are
/// computed from local static sample data.
struct ResearchView: View {

    @Environment(\.dismiss) private var dismiss

    // MARK: Sample data

    private struct Source: Identifiable {
        let id = UUID()
        let title: String
        let domain: String
        let year: Int
        let relevance: String

        var domainLetter: String { String(domain.prefix(1)).uppercased() }
    }

    private struct RecentResearch: Identifiable {
        let id = UUID()
        let query: String
        let meta: String
    }

    private let sources: [Source] = [
        .init(title: "Global EV Outlook — battery demand outlook", domain: "iea.org", year: 2025, relevance: "98%"),
        .init(title: "Battery price survey — record lows in 2024", domain: "about.bnef.com", year: 2024, relevance: "96%"),
        .init(title: "Cell capacity tracker — regional build-out", domain: "benchmarkminerals.com", year: 2025, relevance: "94%"),
        .init(title: "LFP cathode chemistry — a review", domain: "nature.com", year: 2024, relevance: "91%"),
        .init(title: "Monthly shipment note — top cell makers", domain: "sneresearch.com", year: 2025, relevance: "89%")
    ]

    private let suggestions = [
        "EV battery supply chain 2025",
        "State of small models",
        "Creator economy economics",
        "GLP-1 market outlook"
    ]

    private let recents: [RecentResearch] = [
        .init(query: "EV battery supply chain 2025", meta: "12 sources · 2h ago"),
        .init(query: "State of small models", meta: "9 sources · yesterday"),
        .init(query: "GLP-1 market outlook", meta: "14 sources · 3 days ago")
    ]

    private let suggestionColumns = [
        GridItem(.adaptive(minimum: 150), spacing: Aero.Spacing.xs)
    ]

    // MARK: State

    @State private var query = ""
    @State private var isRunning = false
    @State private var hasResults = false
    @State private var bookmarked: Set<UUID> = []
    @State private var toast: String?

    // MARK: Body

    var body: some View {
        ZStack {
            Aero.background.ignoresSafeArea()
            VStack(spacing: 0) {
                header
                    .padding(.horizontal, Aero.Spacing.m)
                    .padding(.top, Aero.Spacing.s)
                    .padding(.bottom, Aero.Spacing.m)
                ScrollView {
                    VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                        StaggerIn(index: 0) { inputSection }
                        if isRunning {
                            StaggerIn(index: 1) { statusRow }
                        } else if hasResults {
                            StaggerIn(index: 1) { queryBubble }
                            StaggerIn(index: 2) { synthesisCard }
                            StaggerIn(index: 3) { sourcesSection }
                            StaggerIn(index: 4) { exportSection }
                            StaggerIn(index: 5) { recentsSection }
                        } else {
                            StaggerIn(index: 1) { hero }
                            StaggerIn(index: 2) { suggestionChips }
                        }
                    }
                    .padding(.horizontal, Aero.Spacing.m)
                    .padding(.bottom, Aero.Spacing.xl)
                }
            }
        }
        .toolbar(.hidden, for: .navigationBar)
        .overlay(alignment: .bottom) { toastView }
    }

    // MARK: Header (own chrome — no router)

    private var header: some View {
        HStack(spacing: Aero.Spacing.s) {
            closeButton
            Text("Research")
                .font(Aero.headline())
                .foregroundStyle(Aero.text)
            Spacer()
            AeroChip(text: "Research mode", selected: true)
        }
    }

    private var closeButton: some View {
        Button {
            dismiss()
        } label: {
            Image(systemName: "xmark")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Aero.text)
                .frame(width: 36, height: 36)
                .background(Circle().fill(Aero.raised))
                .overlay(Circle().stroke(Aero.outline, lineWidth: 1))
        }
        .buttonStyle(KineticPressStyle())
    }

    // MARK: Input + running state

    private var inputSection: some View {
        AeroInputBar(
            text: $query,
            placeholder: "Ask anything, get sources…",
            action: { run() }
        )
    }

    private var statusRow: some View {
        AeroCard {
            HStack(spacing: Aero.Spacing.s) {
                AuroraIndicator()
                Text("Reading 5 sources · Cross-checking claims…")
                    .font(Aero.caption())
                    .foregroundStyle(Aero.textMuted)
            }
        }
    }

    private func run() {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !isRunning else { return }
        isRunning = true
        hasResults = false
        Task {
            try? await Task.sleep(nanoseconds: 1_200_000_000)
            isRunning = false
            withAnimation(Aero.spring) { hasResults = true }
        }
    }

    // MARK: Idle hero + suggestions

    private var hero: some View {
        EmptyStateView(
            icon: "sparkles",
            title: "Ask anything, get sources",
            message: "GS reads across the web, cross-checks claims and returns a cited brief."
        )
    }

    private var suggestionChips: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Suggested questions")
            LazyVGrid(columns: suggestionColumns, alignment: .leading, spacing: Aero.Spacing.xs) {
                ForEach(suggestions, id: \.self) { suggestion in
                    AeroChip(text: suggestion) {
                        query = suggestion
                        run()
                    }
                }
            }
        }
    }

    // MARK: Results

    private var queryBubble: some View {
        HStack(alignment: .bottom, spacing: 0) {
            Spacer(minLength: 56)
            Text(query)
                .font(Aero.body())
                .foregroundStyle(Aero.text)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(RoundedRectangle(cornerRadius: 18).fill(Aero.accent.opacity(0.14)))
                .frame(maxWidth: 280, alignment: .trailing)
        }
    }

    private var synthesisCard: some View {
        AeroCard {
            VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                Text("Synthesis")
                    .font(Aero.headline())
                    .foregroundStyle(Aero.text)
                paragraphOne
                paragraphTwo
                paragraphThree
            }
        }
    }

    /// Paragraphs with inline citation markers styled in the accent.
    private var paragraphOne: Text {
        Text("Battery demand tripled between 2022 and 2025 as LFP chemistries moved into mainstream vehicles")
            .foregroundColor(Aero.text)
            + Text(" [1]").foregroundColor(Aero.accent)
            + Text(", while pack prices fell to a record low of $96/kWh.")
                .foregroundColor(Aero.text)
            + Text(" [2]").foregroundColor(Aero.accent)
    }

    private var paragraphTwo: Text {
        Text("Supply stays concentrated: three cell makers control just over 60% of global capacity, and new plants cluster where grid power is cheapest")
            .foregroundColor(Aero.text)
            + Text(" [3]").foregroundColor(Aero.accent)
            + Text(" The shift favours integrated refiners over pure miners.")
                .foregroundColor(Aero.text)
            + Text(" [1]").foregroundColor(Aero.accent)
    }

    private var paragraphThree: Text {
        Text("For 2026 the open question is sodium-ion at scale — promising in pilots, unproven in fleet economics")
            .foregroundColor(Aero.text)
            + Text(" [4]").foregroundColor(Aero.accent)
            + Text(" Watch the two announced gigafactory conversions.")
                .foregroundColor(Aero.text)
            + Text(" [5]").foregroundColor(Aero.accent)
    }

    private var sourcesSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Sources", actionTitle: "5 read", action: {})
            VStack(spacing: Aero.Spacing.s) {
                ForEach(sources) { source in
                    AeroListRow(
                        title: source.title,
                        subtitle: "\(source.domain) · \(source.year)",
                        leading: {
                            Text(source.domainLetter)
                                .font(Aero.label())
                                .foregroundStyle(Aero.text)
                                .frame(width: 36, height: 36)
                                .background(Circle().fill(Aero.containerHigh))
                        },
                        trailing: {
                            HStack(spacing: 10) {
                                AeroChip(text: source.relevance)
                                bookmarkButton(source)
                            }
                        }
                    )
                }
            }
        }
    }

    private func bookmarkButton(_ source: Source) -> some View {
        Button {
            if bookmarked.contains(source.id) {
                bookmarked.remove(source.id)
            } else {
                bookmarked.insert(source.id)
            }
        } label: {
            Image(systemName: bookmarked.contains(source.id) ? "bookmark.fill" : "bookmark")
                .font(.system(size: 15))
                .foregroundStyle(bookmarked.contains(source.id) ? Aero.accent : Aero.textMuted)
        }
        .buttonStyle(KineticPressStyle())
    }

    // MARK: Export

    private var exportSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Export")
            HStack(spacing: Aero.Spacing.s) {
                AeroChip(text: "PDF") { showToast("Report export queued") }
                AeroChip(text: "Markdown") { showToast("Report export queued") }
                AeroChip(text: "Notion") { showToast("Report export queued") }
                Spacer()
            }
        }
    }

    // MARK: Recent research

    private var recentsSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Recent research")
            VStack(spacing: Aero.Spacing.s) {
                ForEach(recents) { recent in
                    AeroListRow(
                        title: recent.query,
                        subtitle: recent.meta,
                        leading: {
                            Image(systemName: "clock")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundStyle(Aero.text)
                                .frame(width: 36, height: 36)
                                .background(Circle().fill(Aero.containerHigh))
                        },
                        trailing: {
                            Image(systemName: "chevron.right")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(Aero.textMuted)
                        },
                        action: {
                            query = recent.query
                            run()
                        }
                    )
                }
            }
        }
    }

    // MARK: Toast

    private var toastView: some View {
        Group {
            if let message = toast {
                Text(message)
                    .font(Aero.label())
                    .foregroundStyle(Aero.text)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Capsule().fill(Aero.raised))
                    .overlay(Capsule().stroke(Aero.outline, lineWidth: 1))
                    .aeroCardShadow()
                    .padding(.bottom, Aero.Spacing.l)
            }
        }
    }

    private func showToast(_ message: String) {
        withAnimation(Aero.snappy) { toast = message }
        Task {
            try? await Task.sleep(nanoseconds: 1_800_000_000)
            if toast == message {
                withAnimation(Aero.snappy) { toast = nil }
            }
        }
    }
}
