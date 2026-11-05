import SwiftUI
import UIKit

/// ONE HELP ARTICLE, READ PROPERLY — iOS twin of `HelpArticleScreen.kt`.
///
/// A topic used to open a `.medium` sheet: one paragraph, clipped, with the two
/// ways of reaching a person demoted to plain text links. This page gives the
/// answer room: the question, the short answer, the same answer as numbered
/// steps, the related articles (its own sub-pages), and two real ways out.
struct HelpArticleView: View {

    let topicID: String

    @EnvironmentObject private var router: Router
    @Environment(\.dismiss) private var dismiss

    private var topic: GsHelpTopic? { GsHelpCatalog.bySlug(topicID) }

    var body: some View {
        Group {
            if let topic {
                page(topic)
            } else {
                missing
            }
        }
    }

    private func page(_ topic: GsHelpTopic) -> some View {
        GsSheetPage(
            title: "Help",
            subtitle: topic.category.label,
            onBack: { dismiss() }
        ) {
            // ---- The question ------------------------------------------
            HStack(alignment: .top, spacing: 11) {
                GsWellTile(glyph: topic.glyph, tone: topic.tone, size: 38)
                VStack(alignment: .leading, spacing: 6) {
                    Text(topic.question)
                        .font(.custom(Aero.productName(.semibold), size: 15, relativeTo: .headline))
                        .foregroundStyle(Aero.text)
                        .fixedSize(horizontal: false, vertical: true)
                    GsTagPill(text: topic.category.label, tone: topic.tone.ink)
                    Text(topic.summary)
                        .font(.custom(Aero.productName(.regular), size: 12, relativeTo: .footnote))
                        .foregroundStyle(Aero.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            .padding(16)
            .gsSheetCard(corner: 20)

            // ---- The short answer --------------------------------------
            GsSheetGroupHeader("The short answer")
            Text(topic.answer)
                .font(.custom(Aero.productName(.regular), size: 12.5, relativeTo: .footnote))
                .lineSpacing(5)
                .foregroundStyle(Aero.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .padding(15)
                .frame(maxWidth: .infinity, alignment: .leading)
                .gsSheetCard(corner: 20)

            // ---- Steps --------------------------------------------------
            if !topic.steps.isEmpty {
                GsSheetGroupHeader("Step by step", caption: "In the order you do them")
                VStack(spacing: 0) {
                    ForEach(Array(topic.steps.enumerated()), id: \.offset) { index, step in
                        HStack(alignment: .top, spacing: 10) {
                            ZStack {
                                Circle().fill(topic.tone.wash)
                                Circle().strokeBorder(topic.tone.ink.opacity(0.28), lineWidth: 0.8)
                                Text("\(index + 1)")
                                    .font(.custom(Aero.productName(.semibold), size: 10.5, relativeTo: .caption2))
                                    .foregroundStyle(topic.tone.ink)
                            }
                            .frame(width: 20, height: 20)
                            Text(step)
                                .font(.custom(Aero.productName(.regular), size: 12, relativeTo: .footnote))
                                .lineSpacing(4)
                                .foregroundStyle(Aero.textSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                            Spacer(minLength: 0)
                        }
                        .padding(.horizontal, 13)
                        .padding(.vertical, 9)
                        if index != topic.steps.count - 1 {
                            Divider().overlay(Aero.outline.opacity(0.4)).padding(.leading, 13)
                        }
                    }
                }
                .gsSheetCard(corner: 20)
            }

            // ---- Read next (the article's own sub-pages) -----------------
            let related = GsHelpCatalog.related(to: topic)
            if !related.isEmpty {
                GsSheetGroupHeader("Read next", caption: "Other answers that touch this one")
                VStack(spacing: 0) {
                    ForEach(Array(related.enumerated()), id: \.element.slug) { index, next in
                        GsSheetRow(
                            title: next.question,
                            detail: next.summary,
                            action: { router.path.append(.helpArticle(next.slug)) }
                        ) {
                            GsWellTile(glyph: next.glyph, tone: next.tone, size: 28)
                        } trailing: {
                            NexlyGlyph(name: "chevron-right", size: 12, color: Aero.textTertiary)
                        }
                        if index != related.count - 1 {
                            Divider().overlay(Aero.outline.opacity(0.4)).padding(.leading, 12)
                        }
                    }
                }
                .gsSheetCard(corner: 20)
            }

            // ---- Still need a person --------------------------------------
            GsSheetGroupHeader("Still need help?", caption: "A human answers this address")
            HStack(spacing: 8) {
                GsSheetTile(
                    title: "Email support",
                    caption: "About this article",
                    glyph: "mail",
                    tone: .peach,
                    corner: .bottomChevron,
                    centered: true,
                    action: { Self.mailSupport(subject: "Help: \(topic.question)", body: "I read \"\(topic.question)\" and still need help.\n\nWhat I tried:\n") })
                GsSheetTile(
                    title: "Ask Nexly",
                    caption: "Opens a new chat",
                    glyph: "message-circle",
                    tone: .blue,
                    corner: .bottomChevron,
                    centered: true,
                    action: { router.path.append(.chatPrefill(topic.question)) })
            }

            Spacer().frame(height: 12)
            HStack(spacing: 5) {
                NexlyGlyph(name: "heart", size: 10, color: Aero.textTertiary)
                Text("Written for this build, not a generic FAQ.")
                    .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2))
                    .foregroundStyle(Aero.textTertiary)
            }
            .frame(maxWidth: .infinity)
        }
    }

    private var missing: some View {
        GsSheetPage(
            title: "Help",
            subtitle: "That article is no longer in this build",
            onBack: { dismiss() }
        ) {
            VStack(spacing: 10) {
                GsWellTile(glyph: "book-open", tone: .peach, size: 40)
                Text("Help topics change between versions.")
                    .font(.custom(Aero.productName(.regular), size: 12, relativeTo: .footnote))
                    .foregroundStyle(Aero.textSecondary)
                Button("Open Help & Support") { dismiss() }
                    .font(.custom(Aero.productName(.medium), size: 12, relativeTo: .footnote))
            }
            .frame(maxWidth: .infinity)
            .padding(18)
            .gsSheetCard(corner: 20)
        }
    }

    /// Same message the Help page composes: the plan as recorded, the device and
    /// the OS — the three facts a support engineer would otherwise ask for.
    static func mailSupport(subject: String, body: String) {
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
