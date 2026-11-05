import SwiftUI

/// MEMORY MANAGEMENT — `docs/reference/settings/memory-management.png`.
///
/// The mock's four "Recent Memories" rows are seed data; the app renders the
/// REAL saved-library items (ConversationStore.savedLibraryItems) and an
/// honest empty state instead. "Memory is ON" reads `SettingsStore.memory`.
///
/// "View all" is back, and it has somewhere to go: the Saved Library page.
/// These four rows are the newest four of that same store, so each one opens
/// its own item page instead of being a dead label — and the caption reaches
/// the rest of them.
struct MemoryManagementView: View {
    @ObservedObject private var settings = SettingsStore.shared
    @ObservedObject private var store = ConversationStore.shared
    @EnvironmentObject private var router: Router
    @Environment(\.dismiss) private var dismiss

    @State private var showClearAll = false
    @State private var showItemsSheet = false
    @State private var showManageHint = false

    private var items: [LibraryItem] { store.savedLibraryItems() }

    var body: some View {
        GsSettingsPage(
            title: "Memory Management",
            subtitle: "Control what Nexly remembers about you",
            onBack: { dismiss() }
        ) {
            heroCard

            GsGroupHeader(title: "What Nexly remembers", caption: "Based on your past chats")
            GsGroupCard {
                GsTileStrip(tiles: [
                    GsStripTile(id: "personal", glyph: "user", tone: .lilac,
                                title: "Personal Info", caption: "Name, interests, preferences"),
                    GsStripTile(id: "likes", glyph: "heart", tone: .rose,
                                title: "Likes & Interests", caption: "Hobbies, topics, creative style"),
                    GsStripTile(id: "details", glyph: "clipboard-list", tone: .blue,
                                title: "Important Details", caption: "Goals, plans, reminders"),
                    GsStripTile(id: "prefs", glyph: "settings", tone: .mint,
                                title: "Preferences", caption: "Tone, style, settings"),
                ])
            }

            GsGroupHeader(
                title: "Recent Memories",
                caption: items.isEmpty ? nil : "View all",
                action: items.isEmpty ? nil : { router.path.append(.library) }
            )
            GsGroupCard {
                if items.isEmpty {
                    emptyRows
                } else {
                    let shown = Array(items.prefix(4))
                    ForEach(Array(shown.enumerated()), id: \.element.id) { index, item in
                        itemRow(item, isLast: index == shown.count - 1)
                    }
                }
            }

            GsGroupHeader(title: "Manage Memory")
            GsGroupCard {
                // Editing a memory means opening it: the library page shows
                // the whole text and deletes one row. It used to open the same
                // dialog as the row below it, so two rows named two different
                // verbs and did one identical thing.
                manageRow("Edit what Nexly remembers", "Open any memory to read or remove it",
                          glyph: "pencil", tone: .peach) { router.path.append(.library) }
                divider
                manageRow("Clear specific memories", "Delete individual memories",
                          glyph: "trash", tone: .rose) { showItemsSheet = true }
                divider
                manageRow("Clear all memories", "Start fresh with no saved information",
                          glyph: "trash", tone: .rose) { showClearAll = true }
            }

            Spacer().frame(height: 14)
            GsInfoCard(
                text: "Your privacy matters. You're always in control of what Nexly remembers.",
                glyph: "shield-check"
            )
            Spacer().frame(height: 10)
        }
        .gsDialog(isPresented: showClearAll) {
            GsDialog(
                title: "Clear all memories?",
                message: "Every saved memory leaves this device. Your conversations stay.",
                confirmLabel: "Clear all memories",
                onConfirm: {
                    for item in items {
                        store.deleteLibraryItem(id: item.id)
                    }
                    GSHaptics.warning()
                },
                onDismiss: { showClearAll = false },
                destructive: true,
                glyph: "trash",
                tone: .rose,
                footnote: "Your conversations stay exactly where they are."
            )
        }
        .gsDialog(isPresented: showItemsSheet) {
            MemoryItemsDialog(
                items: items,
                onDelete: { id in store.deleteLibraryItem(id: id) },
                onDismiss: { showItemsSheet = false })
        }
    }

    // MARK: Pieces

    private var heroCard: some View {
        GsGroupCard {
            HStack(alignment: .top, spacing: 11) {
                ZStack {
                    Image("GSHomeLotusNew")
                        .resizable()
                        .scaledToFill()
                        .frame(width: 84, height: 84)
                        .clipped()
                    Circle()
                        .fill(.white.opacity(0.30))
                        .frame(width: 46, height: 46)
                    NexlyGlyph(name: "brain", size: 22, color: Color(red: 0.353, green: 0.314, blue: 0.839))
                }
                .frame(width: 84, height: 84)
                .clipShape(RoundedRectangle(cornerRadius: 18))
                .overlay(
                    RoundedRectangle(cornerRadius: 18)
                        .strokeBorder(.white.opacity(0.65), lineWidth: 1)
                )

                VStack(alignment: .leading, spacing: 4) {
                    Text("Nexly remembers")
                        .font(Aero.bodyMedium().weight(.semibold))
                        .foregroundStyle(Aero.text)
                    Text("so your conversations feel more personal, helpful, and relevant.")
                        .font(.custom(Aero.productName(.regular), size: 11.5, relativeTo: .caption))
                        .foregroundStyle(Aero.textTertiary)
                        .fixedSize(horizontal: false, vertical: true)
                    HStack(spacing: 8) {
                        Button {
                            settings.memory.toggle()
                            GSHaptics.select()
                        } label: {
                            HStack(spacing: 5) {
                                NexlyGlyph(name: "info-circle", size: 10,
                                           color: Color(red: 0.090, green: 0.478, blue: 0.314))
                                Text(settings.memory ? "Memory is ON" : "Memory is OFF")
                                    .font(.custom(Aero.productName(.medium), size: 10.5, relativeTo: .caption2))
                                    .foregroundStyle(Color(red: 0.090, green: 0.478, blue: 0.314))
                            }
                            .padding(.horizontal, 9)
                            .padding(.vertical, 5)
                            .background(Capsule().fill(Color(red: 0.765, green: 0.863, blue: 0.796).opacity(0.60)))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(settings.memory ? "Memory is on. Turn off." : "Memory is off. Turn on.")

                        GsInlinePill(label: "Manage", trailingGlyph: "chevron-right") {
                            showItemsSheet = true
                        }
                    }
                    .padding(.top, 2)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 12)
        }
    }

    private var emptyRows: some View {
        HStack(spacing: 11) {
            GsWellTile(glyph: "sparkles", tone: .peach, size: 28)
            Text("Nothing saved yet — memories appear here as you save answers and gems from chats.")
                .font(.custom(Aero.productName(.regular), size: 11.5, relativeTo: .caption))
                .foregroundStyle(Aero.textTertiary)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 12)
    }

    private func itemRow(_ item: LibraryItem, isLast: Bool) -> some View {
        // A memory you can see but cannot open is the dead end this page used
        // to end in.
        Button {
            router.path.append(.libraryItem(item.id))
        } label: {
            HStack(spacing: 11) {
                GsWellTile(glyph: kindGlyph(item.kind), tone: kindTone(item.kind), size: 28)
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.title)
                        .font(Aero.bodyMedium().weight(.medium))
                        .foregroundStyle(Aero.text)
                        .lineLimit(1)
                    Text("“\(String(item.content.prefix(48)))…”")
                        .font(.custom(Aero.productName(.regular), size: 11, relativeTo: .caption2))
                        .foregroundStyle(Aero.textTertiary)
                        .lineLimit(1)
                }
                Spacer(minLength: 8)
                Text(dateLabel(item.createdAt))
                    .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2))
                    .foregroundStyle(Aero.textTertiary)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .frame(minHeight: 52)
            .contentShape(Rectangle())
            .overlay(alignment: .bottom) {
                if !isLast {
                    Rectangle()
                        .fill(Aero.divider.opacity(0.7))
                        .frame(height: 1.0 / 3.0)
                        .padding(.leading, 50)
                        .padding(.trailing, 12)
                }
            }
        }
        .buttonStyle(KineticPressStyle())
        .accessibilityLabel("\(item.title), open saved item")
    }

    private func manageRow(
        _ title: String,
        _ detail: String,
        glyph: String,
        tone: GsWellTone,
        action: @escaping () -> Void
    ) -> some View {
        GsSettingsRow(title: title, detail: detail, action: action) {
            GsWellTile(glyph: glyph, tone: tone, size: 28)
        } trailing: {
            NexlyGlyph(name: "chevron-right", size: 13, color: Aero.textTertiary)
        }
    }

    private var divider: some View {
        Rectangle()
            .fill(Aero.divider.opacity(0.7))
            .frame(height: 1.0 / 3.0)
            .padding(.leading, 50)
            .padding(.trailing, 12)
    }

    private func kindGlyph(_ kind: String) -> String {
        switch kind {
        case "image": return "palette"
        case "draft": return "file-text"
        default: return "message-circle"
        }
    }

    private func kindTone(_ kind: String) -> GsWellTone {
        switch kind {
        case "image": return .rose
        case "draft": return .blue
        default: return .lilac
        }
    }

    private func dateLabel(_ iso: String) -> String {
        let isoFormatter = ISO8601DateFormatter()
        isoFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        guard let date = isoFormatter.date(from: iso) else { return "" }
        let out = DateFormatter()
        out.dateFormat = "MMM d"
        return out.string(from: date)
    }
}

/// The honest "edit what Nexly remembers" surface: the real saved items, each
/// deletable — no fabricated memory list.
private struct MemoryItemsDialog: View {
    let items: [LibraryItem]
    let onDelete: (String) -> Void
    var onDismiss: () -> Void

    var body: some View {
        GsDialogFrame(onDismiss: onDismiss) {
            VStack(alignment: .leading, spacing: 0) {
                Text("What Nexly remembers")
                    .font(.custom(Aero.productName(.semibold), size: 15, relativeTo: .headline))
                    .foregroundStyle(Aero.text)
                Text(items.isEmpty
                     ? "Nothing saved yet."
                     : "\(items.count) \(items.count == 1 ? "memory" : "memories") on this device")
                    .font(.custom(Aero.productName(.regular), size: 11, relativeTo: .caption2))
                    .foregroundStyle(Aero.textSecondary)
                Spacer().frame(height: 12)

                if items.isEmpty {
                    Text("Save something from a conversation and it will be listed here.")
                        .font(.custom(Aero.productName(.regular), size: 11.5, relativeTo: .caption))
                        .foregroundStyle(Aero.textTertiary)
                        .fixedSize(horizontal: false, vertical: true)
                } else {
                    ScrollView(showsIndicators: false) {
                        VStack(spacing: 2) {
                            ForEach(items.prefix(40)) { item in
                                HStack(spacing: 10) {
                                    GsWellTile(glyph: Self.glyph(item.kind), tone: Self.tone(item.kind), size: 28)
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(item.title)
                                            .font(.custom(Aero.productName(.medium), size: 12, relativeTo: .footnote))
                                            .foregroundStyle(Aero.text)
                                            .lineLimit(1)
                                        Text(item.content.replacingOccurrences(of: "\n", with: " ")
                                                .trimmingCharacters(in: .whitespacesAndNewlines))
                                            .font(.custom(Aero.productName(.regular), size: 10.5, relativeTo: .caption2))
                                            .foregroundStyle(Aero.textTertiary)
                                            .lineLimit(2)
                                    }
                                    Spacer(minLength: 6)
                                    Button {
                                        onDelete(item.id)
                                        GSHaptics.warning()
                                    } label: {
                                        NexlyGlyph(name: "trash", size: 14, color: Aero.textTertiary)
                                            .frame(width: 26, height: 26)
                                    }
                                    .buttonStyle(.plain)
                                    .accessibilityLabel("Delete memory")
                                }
                                .padding(.vertical, 6)
                            }
                        }
                    }
                    .frame(maxHeight: 300)
                }

                Spacer().frame(height: 14)
                Button(action: onDismiss) {
                    Text("Done")
                        .font(.custom(Aero.productName(.medium), size: 12.5, relativeTo: .footnote))
                        .foregroundStyle(Aero.text)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 13)
                        .background(Capsule().fill(Aero.text.opacity(0.06)))
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 22)
        }
    }

    /// Same three kinds the library rows use; anything else is a note.
    private static func glyph(_ kind: String) -> String {
        switch kind {
        case "image": return "palette"
        case "draft": return "file-text"
        default: return "message-circle"
        }
    }

    private static func tone(_ kind: String) -> GsWellTone {
        switch kind {
        case "image": return .rose
        case "draft": return .blue
        default: return .lilac
        }
    }
}
