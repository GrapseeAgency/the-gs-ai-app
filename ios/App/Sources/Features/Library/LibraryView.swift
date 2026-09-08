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

// MARK: - Library — personal knowledge space

/// LIBRARY tab root. Live search sits above the index; filter chips
/// (All/Messages/Documents/Images/Files/Prompts) drive the saved-item list;
/// collections scroll horizontally. Static samples.
struct LibraryView: View {

    // MARK: Sample data

    private enum Kind: String, CaseIterable {
        case message, document, image, file, prompt
    }

    private enum Filter: String, CaseIterable, Identifiable {
        case all = "All"
        case messages = "Messages"
        case documents = "Documents"
        case images = "Images"
        case files = "Files"
        case prompts = "Prompts"

        var id: String { rawValue }

        func matches(_ kind: Kind) -> Bool {
            switch self {
            case .all: return true
            case .messages: return kind == .message
            case .documents: return kind == .document
            case .images: return kind == .image
            case .files: return kind == .file
            case .prompts: return kind == .prompt
            }
        }
    }

    private struct SavedItem: Identifiable {
        let id = UUID()
        let title: String
        let detail: String
        let kind: Kind
    }

    private struct SavedCollection: Identifiable {
        let id = UUID()
        let name: String
        let count: String
    }

    @State private var filter: Filter = .all
    @State private var query = ""

    // Real saves from the chat surface — loaded on appear, rendered above seeds.
    @State private var savedMessages: [LibraryItem] = []

    // Item management: tap a real save to read it in full, copy or remove it.
    @State private var viewingItem: LibraryItem?

    @EnvironmentObject private var router: Router

    private let collections: [SavedCollection] = [
        .init(name: "Brand kit", count: "12 items"),
        .init(name: "Client work", count: "8 items"),
        .init(name: "Learning", count: "15 items")
    ]

    private let items: [SavedItem] = [
        .init(title: "Q3 report.pdf", detail: "PDF · 2.4 MB · 12 pages", kind: .document),
        .init(title: "hero-banner-v2.png", detail: "PNG · 1600 × 900", kind: .image),
        .init(title: "contracts.zip", detail: "ZIP · 8 files · 18 MB", kind: .file),
        .init(title: "Cold email sequence", detail: "Prompt · 5 steps", kind: .prompt)
    ]

    private var filteredItems: [SavedItem] {
        items.filter { filter.matches($0.kind) && containsTerm([$0.title, $0.detail]) }
    }

    // MARK: Search gate — same contract as Explore

    /// Empty term passes everything; otherwise any field contains it.
    private func containsTerm(_ fields: [String]) -> Bool {
        term.isEmpty || fields.contains { $0.localizedCaseInsensitiveContains(term) }
    }

    private var term: String {
        query.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    // MARK: Body

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: Aero.Spacing.l) {
                StaggerIn(index: 0) { header }
                StaggerIn(index: 1) { searchRow }
                StaggerIn(index: 2) { filterChips }
                StaggerIn(index: 3) { collectionsSection }
                StaggerIn(index: 4) { itemsSection }
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
        .onAppear {
            savedMessages = ConversationStore.shared.savedLibraryItems()
        }
        .sheet(item: $viewingItem) { item in
            LibraryItemSheet(item: item) {
                savedMessages = ConversationStore.shared.savedLibraryItems()
            } onContinue: { text in
                // Bridge back to the chat surface: the saved turn seeds a
                // fresh composer (pushed once the sheet has animated out).
                router.path.append(.chatPrefill(text))
            }
        }
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                NavigationLink(value: AeroRoute.chat(nil)) {
                    Image(systemName: "plus.circle")
                        .font(.system(size: 17, weight: .medium))
                        .foregroundStyle(Aero.text)
                }
                .buttonStyle(KineticPressStyle())
                .accessibilityLabel("New from library")
            }
        }
    }

    // MARK: Header

    private var header: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.xs) {
            Text("Library")
                .font(Aero.displayTitle())
                .foregroundStyle(Aero.text)
            Text("Everything you save — messages, docs, files and prompts.")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
        }
    }

    // MARK: Search row — live over the whole library

    private var searchRow: some View {
        HStack(spacing: Aero.Spacing.s) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Aero.textMuted)
            TextField("Search your library…", text: $query)
                .font(Aero.body())
                .foregroundStyle(Aero.text)
                .autocorrectionDisabled()
            if !query.isEmpty {
                Button {
                    query = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 14))
                        .foregroundStyle(Aero.textMuted)
                }
                .buttonStyle(KineticPressStyle())
            }
        }
        .padding(.horizontal, Aero.Spacing.m)
        .padding(.vertical, 13)
        .background(Capsule().fill(Aero.container))
        .overlay(Capsule().stroke(Aero.outline, lineWidth: 1))
    }

    // MARK: Filter chips

    private var filterChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Aero.Spacing.s) {
                ForEach(Filter.allCases) { f in
                    AeroChip(
                        text: f.rawValue,
                        selected: filter == f,
                        action: {
                            withAnimation(Aero.gentle) { filter = f }
                        }
                    )
                }
            }
            .padding(.vertical, 2)
        }
    }

    // MARK: Collections

    private var collectionsSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Collections")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Aero.Spacing.m) {
                    ForEach(collections) { collection in
                        AeroCard {
                            VStack(alignment: .leading, spacing: 3) {
                                Image(systemName: "square.stack.3d.up.fill")
                                    .font(.system(size: 15, weight: .medium))
                                    .foregroundStyle(Aero.text)
                                Text(collection.name)
                                    .font(Aero.title())
                                    .foregroundStyle(Aero.text)
                                    .lineLimit(1)
                                Text(collection.count)
                                    .font(Aero.caption())
                                    .foregroundStyle(Aero.textMuted)
                            }
                        }
                        .frame(width: 150)
                    }
                }
                .padding(.vertical, 2)
            }
        }
    }

    // MARK: Saved items (filtered)

    private var itemsSection: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Saved items")
            let realRows = savedMessages.filter { item in
                (filter == .all || filter == filterForKind(item.kind)) &&
                    containsTerm([item.title, item.content])
            }
            if realRows.isEmpty && filteredItems.isEmpty {
                if term.isEmpty {
                    EmptyStateView(
                        icon: "tray",
                        title: "Nothing here yet",
                        message: "Items you save will appear in this filter."
                    )
                } else {
                    EmptyStateView(
                        icon: "magnifyingglass",
                        title: "No matches for \"\(term)\"",
                        message: "Try different words — or save something new from a chat or studio."
                    )
                }
            } else {
                VStack(spacing: Aero.Spacing.s) {
                    ForEach(realRows) { real in
                        realRow(real)
                    }
                    ForEach(filteredItems) { item in
                        itemRow(item)
                    }
                }
            }
        }
    }

    // MARK: Row builders

    private func kindIcon(_ kind: Kind) -> String {
        switch kind {
        case .message: return "bubble.left"
        case .document: return "doc.text"
        case .image: return "photo"
        case .file: return "folder"
        case .prompt: return "lightbulb"
        }
    }

    /// A saved item's kind string maps onto the chip that owns it.
    private func filterForKind(_ kind: String) -> Filter {
        switch kind {
        case "image": return .images
        case "document": return .documents
        case "file": return .files
        case "prompt": return .prompts
        default: return .messages
        }
    }

    private func itemRow(_ item: SavedItem) -> some View {
        AeroListRow(
            title: item.title,
            subtitle: item.detail,
            leading: {
                Image(systemName: kindIcon(item.kind))
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Aero.text)
                    .frame(width: 36, height: 36)
                    .background(Circle().fill(Aero.containerHigh))
            },
            trailing: {
                Image(systemName: "ellipsis")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Aero.textMuted)
            }
        )
    }

    /// A real save: taps into the reader sheet where the full item can be
    /// copied or removed — the sample rows above keep their plain look.
    private func realRow(_ item: LibraryItem) -> some View {
        AeroListRow(
            title: item.title,
            subtitle: item.kind == "image" ? "Image · saved from the studio"
                : item.kind == "document" ? "Document · saved from the studio"
                : "Message · saved from your chats",
            leading: {
                Image(systemName: item.kind == "image" ? "photo"
                    : item.kind == "document" ? "doc.text" : "bookmark")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Aero.text)
                    .frame(width: 36, height: 36)
                    .background(Circle().fill(Aero.containerHigh))
            },
            trailing: {
                Image(systemName: "ellipsis")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Aero.textMuted)
            },
            action: { viewingItem = item }
        )
    }
}

// MARK: - Library item reader

/// One saved turn in full, selectable for partial copies — Continue seeds a
/// fresh chat composer with it, Copy hands it to the clipboard with the
/// benchmark checkmark, Delete removes it for good and the list refreshes
/// itself (Room on Android, the JSON store here).
private struct LibraryItemSheet: View {
    let item: LibraryItem
    let onDeleted: () -> Void
    var onContinue: ((String) -> Void)? = nil

    @Environment(\.dismiss) private var dismiss
    @State private var copied = false

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top) {
                Label(item.title, systemImage: "bookmark")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Aero.text)
                    .lineLimit(2)
                Spacer()
                Button("Close") { dismiss() }
                    .font(.system(size: 15))
                    .foregroundStyle(Aero.textMuted)
            }
            Text("Saved message")
                .font(.system(size: 12))
                .foregroundStyle(Aero.textMuted)
            ScrollView {
                Text(item.content)
                    .font(.system(size: 15))
                    .foregroundStyle(Aero.text)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .textSelection(.enabled)
            }
            HStack(spacing: 20) {
                Button {
                    let text = item.content
                    dismiss()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                        onContinue?(text)
                    }
                } label: {
                    Label("Continue in chat", systemImage: "plus.bubble")
                        .font(.system(size: 14))
                        .foregroundStyle(Aero.textMuted)
                }
                Button {
                    UIPasteboard.general.string = item.content
                    withAnimation(.easeOut(duration: 0.15)) { copied = true }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) {
                        withAnimation(.easeIn(duration: 0.2)) { copied = false }
                    }
                } label: {
                    Label(
                        copied ? "Copied" : "Copy",
                        systemImage: copied ? "checkmark" : "doc.on.doc"
                    )
                    .font(.system(size: 14))
                    .foregroundStyle(copied ? Aero.accent : Aero.textMuted)
                }
                Button {
                    ConversationStore.shared.deleteLibraryItem(id: item.id)
                    onDeleted()
                    dismiss()
                } label: {
                    Label("Delete", systemImage: "trash")
                        .font(.system(size: 14))
                        .foregroundStyle(.red)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(Aero.Spacing.l)
        .presentationDetents([.medium, .large])
    }
}
