import SwiftUI

/// SAVED LIBRARY — the page behind every save.
///
/// Until this view existed, `ConversationStore.saveToLibrary` wrote rows that
/// nothing in the app could read back: the only place any of it surfaced was
/// the four-row "Recent Memories" preview in Memory Management, which showed
/// the newest four and offered no way to reach the rest. Saving was a one-way
/// door on this platform too.
///
/// So this page is the destination those rows always implied:
///
///  - **Facets are built from the data.** A "Drafts" chip over an empty page
///    is the dead end this repo keeps deleting, so a kind with no items has
///    no chip, and an empty library has no chips at all.
///  - **Day headings, not one flat list.** "Today / Yesterday / 3 Nov 2026"
///    is the smallest structure that keeps the newest save at the top.
///  - **Every row opens its own page.** The list truncates at 72 characters;
///    the item page is where the whole answer lives, with copy and delete.
///  - **The empty state is a page, not a shrug.**
///
/// Items are read from the store on appear and after a delete, because the
/// library lives in UserDefaults rather than in a published property — the
/// same reason Memory Management re-reads on appear.
struct SavedLibraryView: View {
    @ObservedObject private var store = ConversationStore.shared
    @EnvironmentObject private var router: Router
    @Environment(\.dismiss) private var dismiss

    @State private var items: [LibraryItem] = []
    @State private var facet: String?

    private var facets: [LibraryFacet] { SavedLibraryModel.facets(items) }

    /// A chip whose last row was deleted must not leave the list filtered to
    /// nothing.
    private var activeFacet: String? {
        guard let facet else { return nil }
        return facets.contains { $0.kind == facet } ? facet : nil
    }

    private var days: [LibraryDay] {
        SavedLibraryModel.groupByDay(SavedLibraryModel.filter(items, kind: activeFacet))
    }

    var body: some View {
        GsSettingsPage(
            title: "Saved Library",
            subtitle: "Everything you kept from a chat, on this device",
            onBack: { dismiss() }
        ) {
            if items.isEmpty {
                emptyCard
            } else {
                hero
                if facets.count > 1 { facetRow }
                ForEach(days) { day in
                    GsGroupHeader(title: day.label, caption: "\(day.items.count) saved")
                    GsGroupCard {
                        ForEach(Array(day.items.enumerated()), id: \.element.id) { index, item in
                            row(item, isLast: index == day.items.count - 1, dayLabel: day.label)
                        }
                    }
                }
            }

            Spacer().frame(height: 14)
            GsInfoCard(
                text: "Saved items stay on this device. Export or remove them from "
                    + "Settings → Export / Delete Data.",
                glyph: "bookmark"
            )
            Spacer().frame(height: 10)
        }
        .onAppear { items = store.savedLibraryItems() }
    }

    // MARK: Pieces

    /// The library's own artwork card: the lotus the Memory page leads with,
    /// and the one honest number on the page — how much is actually here.
    private var hero: some View {
        GsGroupCard {
            HStack(spacing: 11) {
                ZStack {
                    Image("GSHomeLotusNew")
                        .resizable()
                        .scaledToFill()
                        .frame(width: 64, height: 64)
                        .clipped()
                    Circle()
                        .fill(.white.opacity(0.34))
                        .frame(width: 36, height: 36)
                    NexlyGlyph(name: "bookmark", size: 18,
                               color: Color(red: 0.353, green: 0.314, blue: 0.839))
                }
                .frame(width: 64, height: 64)
                .clipShape(RoundedRectangle(cornerRadius: 16))

                VStack(alignment: .leading, spacing: 2) {
                    Text(items.count == 1 ? "1 thing kept" : "\(items.count) things kept")
                        .font(.custom(Aero.productName(.semibold), size: 16, relativeTo: .subheadline))
                        .foregroundStyle(Aero.text)
                    Text(SavedLibraryModel.summaryLine(items))
                        .font(.custom(Aero.productName(.regular), size: 12, relativeTo: .caption))
                        .foregroundStyle(Aero.textTertiary)
                        .lineLimit(2)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 12)
        }
    }

    /// "All 12 · Messages 9 · Images 2 · Drafts 1" — a control, not a
    /// destination, so capsules rather than a third glass panel.
    private var facetRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                facetChip(label: "All", count: items.count, selected: activeFacet == nil) {
                    facet = nil
                }
                ForEach(facets) { f in
                    facetChip(label: f.label, count: f.count, selected: activeFacet == f.kind) {
                        facet = f.kind
                    }
                }
            }
            .padding(.vertical, 8)
        }
    }

    private func facetChip(label: String, count: Int, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Text(label)
                    .font(.custom(Aero.productName(selected ? .semibold : .medium), size: 12,
                                  relativeTo: .caption))
                    .foregroundStyle(selected ? ExplorePalette.peachDeep : Aero.textSecondary)
                Text("\(count)")
                    .font(.custom(Aero.productName(.regular), size: 11, relativeTo: .caption2))
                    .foregroundStyle(selected ? ExplorePalette.peachDeep : Aero.textTertiary)
            }
            .padding(.horizontal, 11)
            .padding(.vertical, 6)
            .background(
                Capsule().fill(selected
                               ? ExplorePalette.peach.opacity(0.18)
                               : Aero.glassSurface.opacity(0.55))
            )
            .overlay(
                Capsule().strokeBorder(
                    selected ? ExplorePalette.peach.opacity(0.45) : .white.opacity(0.45),
                    lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(label), \(count)")
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private func row(_ item: LibraryItem, isLast: Bool, dayLabel: String) -> some View {
        GsSettingsRow(
            title: item.title.isEmpty ? SavedLibraryModel.kindLabel(item.kind) : item.title,
            detail: SavedLibraryModel.snippet(item.content),
            showDivider: !isLast,
            action: { router.path.append(.libraryItem(item.id)) }
        ) {
            GsWellTile(glyph: SavedLibraryModel.glyph(item.kind),
                       tone: SavedLibraryModel.tone(item.kind), size: 28)
        } trailing: {
            // A relative heading already says when; repeating "4 Nov" under
            // "Yesterday" is the date saying itself twice.
            Text(dayLabel == "Today" || dayLabel == "Yesterday"
                 ? ""
                 : SavedLibraryModel.shortDate(item.createdAt))
                .font(.custom(Aero.productName(.regular), size: 11, relativeTo: .caption2))
                .foregroundStyle(Aero.textTertiary)
        }
    }

    /// Nothing saved yet — the page explains the verb instead of apologising.
    private var emptyCard: some View {
        GsGroupCard {
            VStack(spacing: 0) {
                GsWellTile(glyph: "bookmark", tone: .peach, size: 46)
                Spacer().frame(height: 10)
                Text("Nothing saved yet")
                    .font(.custom(Aero.productName(.semibold), size: 16, relativeTo: .subheadline))
                    .foregroundStyle(Aero.text)
                Spacer().frame(height: 4)
                Text("Long-press any answer in a chat and choose Save. Images from the "
                     + "studios and drafts you keep land here too — with the whole text, "
                     + "not a preview.")
                    .font(.custom(Aero.productName(.regular), size: 12, relativeTo: .caption))
                    .lineSpacing(2)
                    .foregroundStyle(Aero.textTertiary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer().frame(height: 12)
                GsInlinePill(label: "Start a chat", trailingGlyph: "arrow-right") {
                    router.resetWorkspace()
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 18)
            .padding(.vertical, 22)
        }
    }
}
