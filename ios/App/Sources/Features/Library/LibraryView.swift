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
    @FocusState private var searchFocused: Bool
    @State private var pendingDelete: LibraryItem?

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

    /// Card-row insets (Task 86-e) — same shape as ChatsListView's.
    private var rowInsets: EdgeInsets {
        EdgeInsets(top: 4, leading: Aero.Spacing.m, bottom: 4, trailing: Aero.Spacing.m)
    }

    var body: some View {
        List {
            StaggerIn(index: 0) { header }
                .gsListRowChrome(EdgeInsets(
                    top: Aero.Spacing.s, leading: Aero.Spacing.m, bottom: 0, trailing: Aero.Spacing.m))
            StaggerIn(index: 1) { searchRow }
                .gsListRowChrome(EdgeInsets(
                    top: Aero.Spacing.l, leading: Aero.Spacing.m, bottom: 0, trailing: Aero.Spacing.m))
            StaggerIn(index: 2) { filterChips }
                .gsListRowChrome(EdgeInsets(
                    top: Aero.Spacing.l, leading: Aero.Spacing.m, bottom: 0, trailing: Aero.Spacing.m))
            StaggerIn(index: 3) { collectionsSection }
                .gsListRowChrome(EdgeInsets(
                    top: Aero.Spacing.l, leading: Aero.Spacing.m, bottom: 0, trailing: Aero.Spacing.m))

            // Saved items (filtered) — the section lead stays a row above the
            // item rows so the rows themselves can swipe (Task 86-e). It keeps
            // its original stagger slot (was block 4 of the LazyVStack).
            StaggerIn(index: 4) {
                SectionHeader(title: "Saved items")
            }
            .gsListRowChrome(EdgeInsets(
                top: Aero.Spacing.l, leading: Aero.Spacing.m, bottom: Aero.Spacing.m - 4, trailing: Aero.Spacing.m))

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
                    .gsListRowChrome(rowInsets)
                } else {
                    EmptyStateView(
                        icon: "magnifyingglass",
                        title: "No matches for \"\(term)\"",
                        message: "Try different words — or save something new from a chat or studio."
                    )
                    .gsListRowChrome(rowInsets)
                }
            } else {
                ForEach(realRows) { real in
                    realRow(real)
                }
                ForEach(filteredItems) { item in
                    itemRow(item)
                }
            }

            // Tail spacer reproduces the old LazyVStack bottom padding.
            Color.clear
                .frame(height: Aero.Spacing.xl - 4)
                .gsListRowChrome(EdgeInsets())
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .environment(\.defaultMinListRowHeight, 1)
        .background(Aero.background.ignoresSafeArea())
        .scrollDismissesKeyboard(.interactively)
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
                .accessibilityAddTraits(.isHeader)
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
                .focused($searchFocused)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.search)
                .onSubmit { searchFocused = false }
            if !query.isEmpty {
                Button {
                    query = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 14))
                        .foregroundStyle(Aero.textMuted)
                }
                .buttonStyle(KineticPressStyle())
                .accessibilityLabel("Clear search")
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
                            GSHaptics.select()
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
        .gsListRowChrome(rowInsets)
    }

    /// A real save: taps into the reader sheet where the full item can be
    /// copied or removed. List row (Task 86-e): the default row style gives
    /// the native tap highlight (the KineticPressStyle scale is gone on this
    /// surface only) and the swipes carry copy / delete through the same
    /// mutations the sheet uses.
    private func realRow(_ item: LibraryItem) -> some View {
        Button {
            viewingItem = item
        } label: {
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
                }
            )
        }
        .swipeActions(edge: .leading, allowsFullSwipe: true) {
            Button {
                copySave(item)
            } label: {
                Label("Copy", systemImage: "doc.on.doc")
            }
            .tint(Aero.accent)
        }
        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
            Button(role: .destructive) {
                deleteSave(item)
            } label: {
                Label("Delete", systemImage: "trash")
            }
        }
        .gsListRowChrome(rowInsets)
    }

    /// The reader sheet's copy mutation — pasteboard + the shared success
    /// tick (the sheet keeps its in-place Copied checkmark animation).
    private func copySave(_ item: LibraryItem) {
        UIPasteboard.general.string = item.content
        GSHaptics.success()
    }

    /// The reader sheet's delete mutation (deleteLibraryItem + success tick +
    /// list refresh) without the dismiss — the sheet keeps its confirming
    /// dialog; the swipe is the native instant path.
    private func deleteSave(_ item: LibraryItem) {
        ConversationStore.shared.deleteLibraryItem(id: item.id)
        GSHaptics.success()
        savedMessages = ConversationStore.shared.savedLibraryItems()
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
    @State private var showDeleteConfirm = false

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top) {
                Label(item.title, systemImage: "bookmark")
                    .font(Aero.responsive(16, .semibold, relativeTo: .callout))
                    .foregroundStyle(Aero.text)
                    .lineLimit(2)
                Spacer()
                Button("Close") { dismiss() }
                    .font(Aero.responsive(15, relativeTo: .subheadline))
                    .foregroundStyle(Aero.textMuted)
            }
            Text("Saved message")
                .font(Aero.responsive(12, relativeTo: .caption))
                .foregroundStyle(Aero.textMuted)
            ScrollView {
                Text(item.content)
                    .font(Aero.responsive(15, relativeTo: .subheadline))
                    .foregroundStyle(Aero.text)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .textSelection(.enabled)
            }
            HStack(spacing: 20) {
                Button {
                    let text = item.content
                    GSHaptics.tap()   // committed — a fresh chat opens seeded
                    dismiss()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                        onContinue?(text)
                    }
                } label: {
                    Label("Continue in chat", systemImage: "plus.bubble")
                        .font(Aero.responsive(14, relativeTo: .subheadline))
                        .foregroundStyle(Aero.textMuted)
                }
                Button {
                    UIPasteboard.general.string = item.content
                    // Copy completed — the shared success tick (Task 85-e I8).
                    GSHaptics.success()
                    withAnimation(Aero.motion(Aero.snappy)) { copied = true }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) {
                        withAnimation(Aero.motion(Aero.snappy)) { copied = false }
                    }
                } label: {
                    Label(
                        copied ? "Copied" : "Copy",
                        systemImage: copied ? "checkmark" : "doc.on.doc"
                    )
                    .font(Aero.responsive(14, relativeTo: .subheadline))
                    .foregroundStyle(copied ? Aero.accent : Aero.textMuted)
                }
                Button {
                    // Destructive — confirms through the dialog like the
                    // Shared-chats revoke (Task 85-e I8); the instant delete
                    // never asked.
                    showDeleteConfirm = true
                } label: {
                    Label("Delete", systemImage: "trash")
                        .font(Aero.responsive(14, relativeTo: .subheadline))
                        .foregroundStyle(.red)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(Aero.Spacing.l)
        .presentationDetents([.medium, .large])
        .confirmationDialog(
            "Delete this saved item?",
            isPresented: $showDeleteConfirm,
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                ConversationStore.shared.deleteLibraryItem(id: item.id)
                GSHaptics.success()
                onDeleted()
                dismiss()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("“\(item.title)” leaves your library for good. Chats are untouched.")
        }
    }
}
