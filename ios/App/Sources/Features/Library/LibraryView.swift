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

/// LIBRARY tab root. Filter chips (All/Messages/Documents/Images/Files/Prompts)
/// drive the saved-item list; collections scroll horizontally. Static samples.
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

    private let collections: [SavedCollection] = [
        .init(name: "Brand kit", count: "12 items"),
        .init(name: "Client work", count: "8 items"),
        .init(name: "Learning", count: "15 items")
    ]

    private let items: [SavedItem] = [
        .init(title: "Saved: pricing strategy idea", detail: "Message · from Q3 pricing strategy", kind: .message),
        .init(title: "Q3 report.pdf", detail: "PDF · 2.4 MB · 12 pages", kind: .document),
        .init(title: "hero-banner-v2.png", detail: "PNG · 1600 × 900", kind: .image),
        .init(title: "contracts.zip", detail: "ZIP · 8 files · 18 MB", kind: .file),
        .init(title: "Cold email sequence", detail: "Prompt · 5 steps", kind: .prompt),
        .init(title: "Saved: onboarding copy rewrite", detail: "Message · from Launch comms", kind: .message)
    ]

    private var filteredItems: [SavedItem] {
        items.filter { filter.matches($0.kind) }
    }

    // MARK: Body

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                StaggerIn(index: 0) { header }
                StaggerIn(index: 1) { filterChips }
                StaggerIn(index: 2) { collectionsSection }
                StaggerIn(index: 3) { itemsSection }
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
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
            if filteredItems.isEmpty {
                EmptyStateView(
                    icon: "tray",
                    title: "Nothing here yet",
                    message: "Items you save will appear in this filter."
                )
            } else {
                VStack(spacing: Aero.Spacing.s) {
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
}
