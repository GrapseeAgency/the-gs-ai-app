import SwiftUI
import UIKit

/// ONE SAVED ITEM — the page the list's 72-character rows point at.
///
/// The list can only show a fragment, and a fragment is useless for the thing
/// people save: a whole answer, a whole draft. So the item page is the text at
/// full length and selectable, with the two things you can do to a saved thing
/// — copy it, or let it go.
///
/// Delete is the only destructive action in the library and it asks first. It
/// removes the row and returns to the list, which re-reads the store on
/// appear, so the day group shrinks without a refresh.
struct LibraryItemView: View {
    let itemID: String

    @ObservedObject private var store = ConversationStore.shared
    @Environment(\.dismiss) private var dismiss

    @State private var item: LibraryItem?
    @State private var loaded = false
    @State private var confirmDelete = false
    @State private var copied = false

    private var savedOn: String { item.map { SavedLibraryModel.shortDate($0.createdAt) } ?? "" }

    var body: some View {
        GsSettingsPage(
            title: item.map { SavedLibraryModel.kindLabel($0.kind) } ?? "Saved item",
            subtitle: subtitle,
            onBack: { dismiss() }
        ) {
            if loaded {
                if let item {
                    contentCard(item)
                    GsGroupHeader(title: "Actions")
                    actionsCard(item)
                    Spacer().frame(height: 14)
                    GsInfoCard(
                        text: "Deleting here removes the saved copy only. The conversation "
                            + "it came from is untouched.",
                        glyph: "bookmark"
                    )
                    Spacer().frame(height: 10)
                } else {
                    missingCard
                }
            }
        }
        .onAppear {
            item = store.savedLibraryItems().first { $0.id == itemID }
            loaded = true
        }
        .gsDialog(isPresented: confirmDelete) {
            GsDialog(
                title: "Delete this saved item?",
                message: item.map { "“\(SavedLibraryModel.snippet($0.content, max: 60))” leaves "
                    + "this device. It cannot be undone." } ?? "",
                confirmLabel: "Delete",
                onConfirm: {
                    if let item { store.deleteLibraryItem(id: item.id) }
                    confirmDelete = false
                    GSHaptics.warning()
                    dismiss()
                },
                onDismiss: { confirmDelete = false },
                destructive: true,
                glyph: "trash",
                tone: .rose,
                footnote: "The conversation it came from stays exactly where it is."
            )
        }
    }

    private var subtitle: String? {
        guard item != nil else { return nil }
        return savedOn.isEmpty ? "Kept on this device" : "Saved \(savedOn) · on this device"
    }

    private func contentCard(_ item: LibraryItem) -> some View {
        GsGroupCard {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    kindBadge(item.kind)
                    Spacer(minLength: 0)
                    // The header already carries "Saved 5 Nov"; the card
                    // repeats the date only when it is missing up there.
                    if savedOn.isEmpty {
                        Text("Date unreadable")
                            .font(.custom(Aero.productName(.regular), size: 11, relativeTo: .caption2))
                            .foregroundStyle(Aero.textTertiary)
                    }
                }
                Spacer().frame(height: 9)
                Text(item.title.isEmpty ? SavedLibraryModel.kindLabel(item.kind) : item.title)
                    .font(.custom(Aero.productName(.semibold), size: 17, relativeTo: .headline))
                    .foregroundStyle(Aero.text)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer().frame(height: 10)
                Rectangle()
                    .fill(Aero.divider.opacity(0.8))
                    .frame(height: 1.0 / 3.0)
                Spacer().frame(height: 11)
                // Selectable: the reason to open this page is to take the text
                // somewhere else, and a long-press that copies beats a button
                // for the common case.
                Text(item.content)
                    .font(.custom(Aero.productName(.regular), size: 13.5, relativeTo: .callout))
                    .lineSpacing(4)
                    .foregroundStyle(Aero.textSecondary)
                    .textSelection(.enabled)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 13)
        }
    }

    private func actionsCard(_ item: LibraryItem) -> some View {
        GsGroupCard {
            GsSettingsRow(
                title: copied ? "Copied" : "Copy text",
                detail: copied ? "It's on your clipboard" : "Take the whole thing with you",
                showDivider: true,
                action: {
                    UIPasteboard.general.string = item.content
                    copied = true
                    GSHaptics.success()
                }
            ) {
                GsWellTile(glyph: copied ? "check" : "copy",
                           tone: copied ? .mint : .blue, size: 28)
            } trailing: {
                NexlyGlyph(name: "chevron-right", size: 13, color: Aero.textTertiary)
            }

            Button {
                confirmDelete = true
            } label: {
                HStack(spacing: 11) {
                    GsWellTile(glyph: "trash", tone: .rose, size: 28)
                    VStack(alignment: .leading, spacing: 1) {
                        Text("Delete from library")
                            .font(.custom(Aero.productName(.medium), size: 15, relativeTo: .subheadline))
                            .foregroundStyle(Aero.danger)
                            .lineLimit(1)
                        Text("Remove it from this device for good")
                            .font(.custom(Aero.productName(.regular), size: 12.5, relativeTo: .caption))
                            .foregroundStyle(Aero.textTertiary)
                            .lineLimit(2)
                    }
                    Spacer(minLength: 8)
                    NexlyGlyph(name: "chevron-right", size: 13, color: Aero.textTertiary)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 4.25)
                .frame(minHeight: 42.5)
                .contentShape(Rectangle())
            }
            .buttonStyle(KineticPressStyle())
            .accessibilityLabel("Delete from library, remove it from this device for good")
        }
    }

    private func kindBadge(_ kind: String) -> some View {
        HStack(spacing: 4) {
            NexlyGlyph(name: SavedLibraryModel.glyph(kind), size: 11, color: ExplorePalette.peachDeep)
            Text(SavedLibraryModel.kindLabel(kind))
                .font(.custom(Aero.productName(.medium), size: 11, relativeTo: .caption2))
                .foregroundStyle(ExplorePalette.peachDeep)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 3)
        .background(Capsule().fill(ExplorePalette.peach.opacity(0.18)))
    }

    /// The row was deleted on another screen, or the link is stale. Say so —
    /// a blank page under a header is the abnormal state, not a rare one.
    private var missingCard: some View {
        GsGroupCard {
            VStack(spacing: 0) {
                GsWellTile(glyph: "bookmark-off", tone: .rose, size: 46)
                Spacer().frame(height: 10)
                Text("This item is gone")
                    .font(.custom(Aero.productName(.semibold), size: 16, relativeTo: .subheadline))
                    .foregroundStyle(Aero.text)
                Spacer().frame(height: 4)
                Text("It was deleted from the library, or it was saved on another device. "
                     + "Saved items live on the device that kept them.")
                    .font(.custom(Aero.productName(.regular), size: 12, relativeTo: .caption))
                    .lineSpacing(2)
                    .foregroundStyle(Aero.textTertiary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer().frame(height: 12)
                GsInlinePill(label: "Back to library", trailingGlyph: "arrow-left") {
                    dismiss()
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 18)
            .padding(.vertical, 22)
        }
    }
}
