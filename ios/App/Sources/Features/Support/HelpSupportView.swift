import SwiftUI
import UIKit

/// One help topic. This is app CONTENT, not invented telemetry: each answer
/// describes something this build actually does, and a topic whose feature does
/// not exist is not listed.
enum GsHelpCategory: String, CaseIterable, Hashable, Identifiable {
    var id: String { rawValue }
    case general, troubleshooting, account, privacy, features, support

    var label: String {
        switch self {
        case .general: return "FAQs"
        case .troubleshooting: return "Troubleshooting"
        case .account: return "Account & Billing"
        case .privacy: return "Privacy & Security"
        case .features: return "Feature Guides"
        case .support: return "Technical Support"
        }
    }

    var caption: String {
        switch self {
        case .general: return "Find answers to common questions."
        case .troubleshooting: return "Fix issues and get things working."
        case .account: return "Manage your account, plans and payments."
        case .privacy: return "Learn about your data and security."
        case .features: return "Get to know Nexly's features and tools."
        case .support: return "Contact our team for personalized help."
        }
    }

    var glyph: String {
        switch self {
        case .general: return "help-circle"
        case .troubleshooting: return "tool"
        case .account: return "receipt"
        case .privacy: return "shield-check"
        case .features: return "settings"
        case .support: return "message-circle"
        }
    }

    var tone: GsWellTone {
        switch self {
        case .general: return .lilac
        case .troubleshooting: return .blue
        case .account: return .peach
        case .privacy: return .mint
        case .features: return .lilac
        case .support: return .rose
        }
    }
}

/// Help & Support — docs/reference/pending/help-support.png.
///
/// The sheet's search, hero, 3×2 category grid, popular topics, the
/// "Still need help?" block and the heart footer are reproduced in order and
/// at the sheet's own scale. What is real:
///  - the search field filters the shipped topic list (it is not decorative),
///    and the sliders button opens the same list grouped by category;
///  - every category tile opens a real article list, and every article opens
///    with two working actions (ask Nexly, email support);
///  - "Ask Nexly" opens a new chat;
///  - Contact Support and Report a Problem compose a real mail intent carrying
///    the device facts a support engineer would need;
///  - Live Chat opens Nexly, the only chat that answers in this build.
///
/// The mock's "$19.99" and its FAQ copy about passwords were dropped: the price
/// is $12.99 (owner-confirmed) and this build has no password to reset. A help
/// article that describes a flow the app does not have is the same lie as a
/// fake number.
struct HelpSupportView: View {

    @EnvironmentObject private var router: Router
    @Environment(\.dismiss) private var dismiss

    @State private var query = ""
    @State private var library: GsHelpCategory?
    @State private var showAllTopics = false

    private var topics: [GsHelpTopic] { GsHelpCatalog.topics }

    private var visible: [GsHelpTopic] {
        guard !query.isEmpty else { return topics }
        return topics.filter {
            $0.question.localizedCaseInsensitiveContains(query)
                || $0.answer.localizedCaseInsensitiveContains(query)
        }
    }

    var body: some View {
        GsSheetPage(
            title: "Help & Support",
            subtitle: "We're here to help. Find answers, get assistance, or reach out to our team.",
            onBack: { dismiss() }
        ) {
            searchRow
            Spacer().frame(height: 12)
            hero

            GsSheetGroupHeader(
                "Browse by Category",
                caption: "View all",
                onCaption: { library = .general })
            categoryRow([.general, .troubleshooting, .account])
            Spacer().frame(height: 8)
            categoryRow([.privacy, .features, .support])

            GsSheetGroupHeader(
                "Popular Help Topics",
                caption: "View all",
                glyph: "flame",
                onCaption: { showAllTopics = true })
            popularCard

            Spacer().frame(height: 18)
            supportCard

            Spacer().frame(height: 12)
            heartFooter
        }
        .sheet(item: $library) { category in
            GsHelpLibrarySheet(
                title: category == .general ? "Browse by Category" : category.label,
                topics: topics,
                categories: GsHelpCategory.allCases,
                startAt: category == .general ? nil : category,
                onAskNexly: {
                    library = nil
                    router.path.append(.chat(nil))
                },
                onOpenArticle: { slug in
                    library = nil
                    router.path.append(.helpArticle(slug))
                })
                .presentationDetents([.large])
        }
        .sheet(isPresented: $showAllTopics) {
            GsHelpLibrarySheet(
                title: "Popular Help Topics",
                topics: topics,
                categories: GsHelpCategory.allCases,
                startAt: nil,
                onAskNexly: {
                    showAllTopics = false
                    router.path.append(.chat(nil))
                },
                onOpenArticle: { slug in
                    showAllTopics = false
                    router.path.append(.helpArticle(slug))
                })
                .presentationDetents([.large])
        }
        // A topic used to open a `.medium` sheet: a paragraph in a box. The
        // article is a pushed page now (see HelpArticleView).
    }

    // MARK: Search

    private var searchRow: some View {
        HStack(spacing: 8) {
            HStack(spacing: 8) {
                NexlyGlyph(name: "search", size: 14, color: Aero.textSecondary)
                TextField(
                    "Search for help, topics, or keywords…",
                    text: $query)
                    .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2))
                    .foregroundStyle(Aero.text)
                    .submitLabel(.search)
            }
            .padding(.horizontal, 12)
            .frame(minHeight: 44)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(Aero.glassSurface.opacity(0.70))
            )
            GsHeaderAction(
                glyph: "adjustments",
                label: "Filter topics",
                action: { library = .general })
        }
    }

    // MARK: Hero

    private var hero: some View {
        GsHeroCard {
            HStack(spacing: 11) {
                GsWellTile(glyph: "robot", tone: .lilac, size: 40)
                VStack(alignment: .leading, spacing: 1) {
                    Text("Need quick help?")
                        .font(.custom(Aero.productName(.semibold), size: 11, relativeTo: .caption))
                        .foregroundStyle(Aero.text)
                    Text("Try our AI assistant for instant answers to common questions.")
                        .font(.custom(Aero.productName(.regular), size: 8.5, relativeTo: .caption2))
                        .foregroundStyle(Aero.textTertiary)
                        .lineLimit(2)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 6)
                GsSheetPillButton(label: "Ask Nexly") {
                    router.path.append(.chat(nil))
                }
            }
        }
    }

    // MARK: Categories

    private func categoryRow(_ categories: [GsHelpCategory]) -> some View {
        HStack(spacing: 8) {
            ForEach(categories, id: \.self) { category in
                GsSheetTile(
                    title: category.label,
                    caption: category.caption,
                    glyph: category.glyph,
                    tone: category.tone,
                    action: { library = category })
            }
        }
    }

    // MARK: Popular topics

    private var popularCard: some View {
        GsGroupCard {
            if visible.isEmpty {
                Text("No topic matches \u{201C}\(query)\u{201D}. Try a shorter word, or contact support below.")
                    .font(.custom(Aero.productName(.regular), size: 9, relativeTo: .caption2))
                    .foregroundStyle(Aero.textTertiary)
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                let shown = Array(visible.prefix(4))
                ForEach(Array(shown.enumerated()), id: \.element.id) { index, topic in
                    GsSheetRow(
                        title: topic.question,
                        detail: topic.summary,
                        action: { router.path.append(.helpArticle(topic.slug)) },
                        showDivider: index < shown.count - 1
                    ) {
                        GsWellTile(glyph: topic.glyph, tone: topic.tone, size: 34)
                    } trailing: {
                        GsSheetChevron()
                    }
                }
            }
        }
    }

    // MARK: Still need help

    private var supportCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                GsWellTile(glyph: "headset", tone: .lilac, size: 34)
                VStack(alignment: .leading, spacing: 0) {
                    Text("Still need help?")
                        .font(.custom(Aero.productName(.semibold), size: 11, relativeTo: .caption))
                        .foregroundStyle(Aero.text)
                    Text("Our support team is available 24/7 to assist you.")
                        .font(.custom(Aero.productName(.regular), size: 8.5, relativeTo: .caption2))
                        .foregroundStyle(Aero.textTertiary)
                }
                Spacer(minLength: 0)
            }
            HStack(spacing: 8) {
                GsSheetTile(
                    title: "Contact Support",
                    caption: "Get in touch with our team",
                    glyph: "mail",
                    tone: .peach,
                    corner: .bottomChevron,
                    centered: true,
                    action: { sendMail(subject: "Nexly support", body: "Hi team,") })
                GsSheetTile(
                    title: "Report a Problem",
                    caption: "Help us improve Nexly",
                    glyph: "bug",
                    tone: .rose,
                    corner: .bottomChevron,
                    centered: true,
                    action: {
                        sendMail(
                            subject: "Problem report",
                            body: "What happened:\n\nWhat I expected:\n\nSteps to reproduce:\n")
                    })
                GsSheetTile(
                    title: "Live Chat",
                    caption: "Chat with Nexly now",
                    glyph: "message-circle",
                    tone: .blue,
                    corner: .bottomChevron,
                    centered: true,
                    action: { router.path.append(.chat(nil)) })
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .gsSheetCard(corner: 22)
    }

    // MARK: Footer

    private var heartFooter: some View {
        HStack(spacing: 6) {
            NexlyGlyph(name: "heart", size: 12, color: GsWellTone.rose.ink)
            Text("Your satisfaction matters to us")
                .font(.custom(Aero.productName(.regular), size: 9, relativeTo: .caption2))
                .foregroundStyle(Aero.textTertiary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .gsSheetCard(corner: 22)
    }

    // MARK: Real mail

    private func collectMail(for topic: GsHelpTopic) {
        sendMail(
            subject: "Help: \(topic.question)",
            body: "Question: \(topic.question)\n\n\(topic.answer)\n")
    }

    private func sendMail(subject: String, body: String) {
        let plan = PlanStore.shared.isActive ? PlanStore.shared.planName : "Nexly Pro ($12.99/month)"
        let full = body
            + "\n\n—\nPlan: \(plan)"
            + "\nDevice: \(UIDevice.current.model)"
            + "\niOS: \(UIDevice.current.systemVersion)"
        var components = URLComponents()
        components.scheme = "mailto"
        components.path = "support@grapsee.com"
        components.queryItems = [
            URLQueryItem(name: "subject", value: subject),
            URLQueryItem(name: "body", value: full),
        ]
        guard let url = components.url else { return }
        UIApplication.shared.open(url)
    }
}

// MARK: - Article list

/// A real article list: categories → topics → answer, with working actions.
struct GsHelpLibrarySheet: View {

    let title: String
    let topics: [GsHelpTopic]
    let categories: [GsHelpCategory]
    let startAt: GsHelpCategory?
    let onAskNexly: () -> Void
    /// The sheet hands the article to the shell: one article page exists, and
    /// it is the pushed one (HelpArticleView), not a second body in here.
    var onOpenArticle: (String) -> Void = { _ in }

    @Environment(\.dismiss) private var dismiss
    @State private var path: [GsHelpRoute] = []

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(categories, id: \.self) { category in
                        Button {
                            path.append(.category(category))
                        } label: {
                            HStack(spacing: 10) {
                                GsWellTile(glyph: category.glyph, tone: category.tone, size: 32)
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(category.label)
                                        .font(.custom(Aero.productName(.medium), size: 12, relativeTo: .body))
                                        .foregroundStyle(Aero.text)
                                    Text(category.caption)
                                        .font(.custom(Aero.productName(.regular), size: 10, relativeTo: .caption2))
                                        .foregroundStyle(Aero.textTertiary)
                                }
                                Spacer()
                                NexlyGlyph(name: "chevron-right", size: 12, color: Aero.textTertiary)
                            }
                            .padding(.vertical, 8)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(20)
            }
            .background(Aero.background.ignoresSafeArea())
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
            .navigationDestination(for: GsHelpRoute.self) { route in
                switch route {
                case .category(let category):
                    let inCategory = topics.filter { $0.category == category }
                    ScrollView {
                        VStack(alignment: .leading, spacing: 8) {
                            if inCategory.isEmpty {
                                Text("No articles in this category yet. Ask Nexly, or email support from the Help page.")
                                    .font(.custom(Aero.productName(.regular), size: 12, relativeTo: .caption))
                                    .foregroundStyle(Aero.textSecondary)
                                    .padding(.vertical, 8)
                            } else {
                                ForEach(inCategory) { topic in
                                    Button {
                                        dismiss()
                                        onOpenArticle(topic.slug)
                                    } label: {
                                        HStack(spacing: 10) {
                                            GsWellTile(glyph: topic.glyph, tone: topic.tone, size: 30)
                                            VStack(alignment: .leading, spacing: 1) {
                                                Text(topic.question)
                                                    .font(.custom(Aero.productName(.medium), size: 11, relativeTo: .caption))
                                                    .foregroundStyle(Aero.text)
                                                    .multilineTextAlignment(.leading)
                                                Text(topic.answer)
                                                    .font(.custom(Aero.productName(.regular), size: 9, relativeTo: .caption2))
                                                    .foregroundStyle(Aero.textTertiary)
                                                    .lineLimit(2)
                                            }
                                            Spacer()
                                            NexlyGlyph(name: "chevron-right", size: 12, color: Aero.textTertiary)
                                        }
                                        .padding(.vertical, 8)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                        .padding(20)
                    }
                    .background(Aero.background.ignoresSafeArea())
                    .navigationTitle(category.label)
                    .navigationBarTitleDisplayMode(.inline)
                case .topic(let topic):
                    // Unreachable since rows hand the slug to the shell, but a
                    // destination must exist for every case.
                    Color.clear.onAppear { dismiss(); onOpenArticle(topic.slug) }
                }
            }
        }
        .onAppear {
            if let startAt, path.isEmpty {
                path = [.category(startAt)]
            }
        }
    }
}

enum GsHelpRoute: Hashable {
    case category(GsHelpCategory)
    case topic(GsHelpTopic)
}
