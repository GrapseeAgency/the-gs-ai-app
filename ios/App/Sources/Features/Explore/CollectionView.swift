import SwiftUI

/// ONE COLLECTION, WITH ITS OWN TOOLS — iOS twin of `CollectionScreen.kt`.
///
/// Every Creative Collection card called `onOpenDiscover()`, so tapping "Work &
/// Productivity" opened the entire mixed catalogue. The card promised "here are
/// the tools for this" and the app answered "here is everything".
///
/// This page is what the card promised: the collection, what it is for, and
/// exactly its tools — each of which seeds a real conversation.
struct CollectionView: View {

    let collectionID: String

    @EnvironmentObject private var router: Router
    @Environment(\.dismiss) private var dismiss

    private var collection: ExploreCatalog.Collection? {
        ExploreCatalog.collections.first { $0.id == collectionID }
    }

    var body: some View {
        Group {
            if let collection {
                page(collection)
            } else {
                missing
            }
        }
    }

    private func page(_ collection: ExploreCatalog.Collection) -> some View {
        let tools = ExploreCatalog.tools.filter { $0.category == collection.category }
        let lead = tools.first
        return GsSheetPage(
            title: collection.title,
            subtitle: collection.subtitle,
            onBack: { dismiss() }
        ) {
            // ---- The collection's own card -----------------------------
            ZStack(alignment: .topLeading) {
                LinearGradient(
                    // A quarter-strength wash, not a colour block: the page is cream,
                    // and a saturated green slab beside it reads as a different app.
                    colors: [(lead?.bg ?? .white).opacity(0.26), .clear],
                    startPoint: .topLeading, endPoint: .bottomTrailing)
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 11) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 14)
                                .fill(Color.white.opacity(0.72))
                            RoundedRectangle(cornerRadius: 14)
                                .strokeBorder(Color.white.opacity(0.85), lineWidth: 1)
                            NexlyGlyph(name: "sparkles", size: 19, color: lead?.fg ?? Aero.accentStrong)
                        }
                        .frame(width: 42, height: 42)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(collection.title)
                                .font(.custom(Aero.productName(.semibold), size: 14, relativeTo: .headline))
                                .foregroundStyle(Aero.text)
                            Text("\(collection.category.label) • \(tools.count) tool\(tools.count == 1 ? "" : "s")")
                                .font(.custom(Aero.productName(.regular), size: 11, relativeTo: .caption))
                                .foregroundStyle(Aero.textTertiary)
                        }
                        Spacer(minLength: 0)
                    }
                    Text(Self.blurb(collection.id, fallback: collection.subtitle))
                        .font(.custom(Aero.productName(.regular), size: 12, relativeTo: .footnote))
                        .lineSpacing(4)
                        .foregroundStyle(Aero.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(16)
            }
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .gsGlass(.cardLight, corner: 20)

            // ---- Its tools ---------------------------------------------
            if tools.isEmpty {
                GsSheetGroupHeader("Tools", caption: "None in this build yet")
                Text("This collection is a promise the build cannot keep yet, so it holds no tools. Explore's other collections are complete.")
                    .font(.custom(Aero.productName(.regular), size: 11.5, relativeTo: .footnote))
                    .lineSpacing(4)
                    .foregroundStyle(Aero.textSecondary)
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .gsSheetCard(corner: 20)
            } else {
                GsSheetGroupHeader("Tools", caption: "Each one opens a real conversation")
                VStack(spacing: 8) {
                    ForEach(tools) { tool in
                        toolRow(tool)
                    }
                }
            }

            Spacer().frame(height: 14)
            Text("Each tool opens its own page first, with three openings to start from. Nothing is sent until you press send.")
                .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2))
                .foregroundStyle(Aero.textTertiary)
                .padding(.horizontal, 6)

            Spacer().frame(height: 10)
            Button {
                router.path.append(.discover)
            } label: {
                Text("Explore everything")
                    .font(.custom(Aero.productName(.semibold), size: 13, relativeTo: .callout))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(
                        Capsule().fill(
                            LinearGradient(colors: [Aero.accentStrong, Aero.accent],
                                           startPoint: .top, endPoint: .bottom)))
                    .overlay(Capsule().strokeBorder(Color.white.opacity(0.28), lineWidth: 1))
            }
            .buttonStyle(KineticPressStyle())
        }
    }

    private func toolRow(_ tool: ExploreTool) -> some View {
        Button {
            router.path.append(.tool(tool.id))
        } label: {
            HStack(spacing: 11) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(tool.filledTile ? tool.bg : Color.white.opacity(0.66))
                    RoundedRectangle(cornerRadius: 12)
                        .strokeBorder(Color.white.opacity(0.80), lineWidth: 1)
                    NexlyGlyph(name: tool.glyph, size: 16, color: tool.fg)
                }
                .frame(width: 36, height: 36)
                VStack(alignment: .leading, spacing: 1) {
                    Text(tool.title)
                        .font(.custom(Aero.productName(.medium), size: 12, relativeTo: .footnote))
                        .foregroundStyle(Aero.text)
                        .lineLimit(1)
                    Text(tool.subtitle)
                        .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2))
                        .foregroundStyle(Aero.textTertiary)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                }
                Spacer(minLength: 6)
                NexlyGlyph(name: "arrow-right", size: 12, color: Aero.textTertiary)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 11)
            .frame(maxWidth: .infinity)
            .gsGlass(.cardLight, corner: 20)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(tool.title): \(tool.subtitle)")
    }

    private var missing: some View {
        GsSheetPage(
            title: "Collection",
            subtitle: "That collection is not in this build",
            onBack: { dismiss() }
        ) {
            VStack(spacing: 10) {
                Text("Collections change between versions.")
                    .font(.custom(Aero.productName(.regular), size: 12, relativeTo: .footnote))
                    .foregroundStyle(Aero.textSecondary)
                Button("Explore everything") { router.path.append(.discover) }
                    .font(.custom(Aero.productName(.medium), size: 12, relativeTo: .footnote))
            }
            .frame(maxWidth: .infinity)
            .padding(18)
            .gsSheetCard(corner: 20)
        }
    }

    /// The collection's own sentence — the card carries a four-word strapline,
    /// but a page has to say what the place is FOR.
    private static func blurb(_ id: String, fallback: String) -> String {
        switch id {
        case "design":
            return "Work that is judged by the eye: copy, layout, naming, moodboards and the words around them. Every tool here starts a conversation with the visual problem already framed."
        case "work":
            return "The jobs that eat an afternoon: summarising a thread, turning notes into a plan, drafting the message you keep postponing, and checking your own writing."
        case "learning":
            return "Explaining, quizzing and practising — the three things a person actually does when they learn something, rather than the three things a course sells."
        default:
            return fallback
        }
    }
}
