import SwiftUI

/// ONE TOOL, EXPLAINED BEFORE IT IS STARTED — iOS twin of `ToolDetailScreen.kt`.
///
/// The four entry points into a tool — Explore's Popular Tools, Discover's
/// Featured Tools, a collection's tool list, and the search results — all used
/// to push a paragraph into the composer and open a blank conversation. The
/// card promised a capability and the app answered with a wall of prompt text
/// the reader had no chance to read in context.
///
/// This page is the missing middle: what the tool is for, the three moves it
/// makes, and three openings. Every opening starts the same real conversation
/// the card used to start, so the page adds a step without adding a dead end.
/// "Pairs well with" leads to another tool's page — the sub-page of this
/// sub-page.
///
/// The trip planner keeps its own screen (Travel Agent); its primary action
/// here opens that screen rather than a generic chat, because a purpose-built
/// surface should not be demoted to a seeded prompt.
struct ToolDetailView: View {

    let toolID: String

    @EnvironmentObject private var router: Router
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var scheme

    private var tool: ExploreTool? { ToolCatalog.tool(toolID) }
    private var detail: ToolDetail? { ToolCatalog.detail(toolID) }

    var body: some View {
        Group {
            if let tool, let detail {
                page(tool, detail)
            } else {
                missing
            }
        }
    }

    private func page(_ tool: ExploreTool, _ detail: ToolDetail) -> some View {
        let pairs = ToolCatalog.pairs(toolID)
        let isTripPlanner = tool.id == "trip-planner"
        // On the dark canvas the tool's ink is a deep colour on near-black, so
        // the glyph and the step numbers disappear. The tone's own wash is the
        // light member of the pair, which is what the dark surface needs.
        let toneInk = scheme == .dark ? tool.bg : tool.fg
        return GsSheetPage(
            title: tool.title,
            subtitle: tool.category.label,
            onBack: { dismiss() }
        ) {
            // ---- The tool's own card -------------------------------------
            ZStack(alignment: .topLeading) {
                LinearGradient(colors: [tool.bg.opacity(scheme == .dark ? 0.18 : 0.30), .clear],
                               startPoint: .topLeading, endPoint: .bottomTrailing)
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 12) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 17)
                                .fill(scheme == .dark
                                      ? Color.white.opacity(0.10)
                                      : (tool.filledTile ? tool.bg : Color.white.opacity(0.76)))
                            RoundedRectangle(cornerRadius: 17)
                                .strokeBorder(Color.white.opacity(scheme == .dark ? 0.14 : 0.88), lineWidth: 1)
                            NexlyGlyph(name: tool.glyph, size: 23, color: toneInk)
                        }
                        .frame(width: 52, height: 52)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(tool.category.label.uppercased())
                                .font(.custom(Aero.productName(.medium), size: 9.5, relativeTo: .caption2))
                                .tracking(0.8)
                                .foregroundStyle(toneInk)
                            Text(tool.title)
                                .font(.custom(Aero.productName(.semibold), size: 17, relativeTo: .headline))
                                .foregroundStyle(Aero.text)
                            Text(tool.subtitle)
                                .font(.custom(Aero.productName(.regular), size: 11, relativeTo: .caption))
                                .foregroundStyle(Aero.textSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        Spacer(minLength: 0)
                    }
                    Text(detail.blurb)
                        .font(.custom(Aero.productName(.regular), size: 12, relativeTo: .footnote))
                        .lineSpacing(4)
                        .foregroundStyle(Aero.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(16)
            }
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .gsGlass(.cardLight, corner: 20)

            // ---- Three moves ---------------------------------------------
            GsSheetGroupHeader("How it works", caption: "Three moves, in order")
            VStack(alignment: .leading, spacing: 11) {
                ForEach(Array(detail.steps.enumerated()), id: \.offset) { index, step in
                    HStack(alignment: .top, spacing: 10) {
                        ZStack {
                            Circle().fill(tool.bg.opacity(scheme == .dark ? 0.16 : 0.55))
                            Text("\(index + 1)")
                                .font(.custom(Aero.productName(.semibold), size: 10, relativeTo: .caption2))
                                .foregroundStyle(toneInk)
                        }
                        .frame(width: 20, height: 20)
                        Text(step)
                            .font(.custom(Aero.productName(.regular), size: 11.5, relativeTo: .footnote))
                            .lineSpacing(3)
                            .foregroundStyle(Aero.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                        Spacer(minLength: 0)
                    }
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .gsSheetCard(corner: 20)

            // ---- Openings -------------------------------------------------
            GsSheetGroupHeader("Try it with", caption: "Tap one to start — nothing sends until you do")
            VStack(spacing: 0) {
                ForEach(Array(detail.examples.enumerated()), id: \.offset) { index, example in
                    if index > 0 {
                        Rectangle().fill(Aero.divider).frame(height: 0.7).padding(.leading, 14)
                    }
                    Button {
                        router.path.append(.chatPrefill(example))
                    } label: {
                        HStack(spacing: 8) {
                            Text(example)
                                .font(.custom(Aero.productName(.regular), size: 12, relativeTo: .footnote))
                                .foregroundStyle(Aero.text)
                                .multilineTextAlignment(.leading)
                                .lineLimit(2)
                                .fixedSize(horizontal: false, vertical: true)
                            Spacer(minLength: 0)
                            NexlyGlyph(name: "arrow-right", size: 12, color: Aero.textTertiary)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(example)
                }
                Rectangle().fill(Aero.divider).frame(height: 0.7).padding(.leading, 14)
                Button {
                    router.path.append(.chatPrefill(tool.prompt))
                } label: {
                    HStack(spacing: 8) {
                        Text("Use my own words")
                            .font(.custom(Aero.productName(.medium), size: 12, relativeTo: .footnote))
                            .foregroundStyle(Aero.accentStrong)
                        Spacer(minLength: 0)
                        NexlyGlyph(name: "arrow-right", size: 12, color: Aero.accentStrong)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Use my own words")
            }
            .gsSheetCard(corner: 20)

            // ---- Pairs ----------------------------------------------------
            if !pairs.isEmpty {
                GsSheetGroupHeader("Pairs well with", caption: "The next tool in the same job")
                VStack(spacing: 0) {
                    ForEach(Array(pairs.enumerated()), id: \.element.id) { index, pair in
                        if index > 0 {
                            Rectangle().fill(Aero.divider).frame(height: 0.7).padding(.leading, 56)
                        }
                        Button {
                            router.path.append(.tool(pair.id))
                        } label: {
                            HStack(spacing: 11) {
                                GsWellTile(glyph: pair.glyph, tone: tone(for: pair), size: 28)
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(pair.title)
                                        .font(.custom(Aero.productName(.medium), size: 12, relativeTo: .footnote))
                                        .foregroundStyle(Aero.text)
                                    Text(pair.subtitle)
                                        .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2))
                                        .foregroundStyle(Aero.textTertiary)
                                        .lineLimit(2)
                                        .multilineTextAlignment(.leading)
                                }
                                Spacer(minLength: 6)
                                NexlyGlyph(name: "arrow-right", size: 12, color: Aero.textTertiary)
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 11)
                            .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("\(pair.title): \(pair.subtitle)")
                    }
                }
                .gsSheetCard(corner: 20)
            }

            Spacer().frame(height: 14)
            Text(isTripPlanner
                 ? "The trip planner has its own screen: dates, pace and a day-by-day board you can edit."
                 : "A tool is a way of starting the same assistant. Nothing is installed and nothing is sent until you press send.")
                .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2))
                .foregroundStyle(Aero.textTertiary)
                .padding(.horizontal, 6)

            Spacer().frame(height: 10)
            Button {
                if isTripPlanner {
                    router.path.append(.travelAgent)
                } else {
                    router.path.append(.chatPrefill(tool.prompt))
                }
            } label: {
                Text(isTripPlanner ? "Open Travel Agent" : "Start a conversation")
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

    private var missing: some View {
        GsSheetPage(
            title: "Tool",
            subtitle: "That tool is not in this build",
            onBack: { dismiss() }
        ) {
            VStack(spacing: 10) {
                Text("Tools change between versions.")
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

    /// The tone well for a paired tool. The pairing list is a set of ids, not
    /// colours, so the well has to pick a family from the tool's own tone.
    private func tone(for tool: ExploreTool) -> GsWellTone {
        if tool.fg == ExplorePalette.pinkFg || tool.fg == ExplorePalette.vermilionFg {
            return .rose
        }
        if tool.fg == ExplorePalette.greenFg { return .mint }
        if tool.fg == ExplorePalette.blueFg { return .blue }
        return .lilac
    }
}
