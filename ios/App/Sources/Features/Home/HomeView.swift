import SwiftUI

// MARK: - Models (seed data)

/// A single entry in the home quick-action grid.
struct QuickAction: Identifiable {
    let id = UUID()
    let title: String
    let systemImage: String
}

/// A "continue where you left off" card.
struct RecentItem: Identifiable {
    let id = UUID()
    let title: String
    let subtitle: String
    let systemImage: String
}

// MARK: - Home (root tab container)

/// Seed of the HOME screen — the AI command centre from the product
/// blueprint. Static content only; no dynamic behaviour wired up yet.
struct HomeView: View {

    @State private var quickActions: [QuickAction] = [
        QuickAction(title: "New chat", systemImage: "ellipsis.bubble"),
        QuickAction(title: "Voice", systemImage: "mic"),
        QuickAction(title: "Image", systemImage: "photo"),
        QuickAction(title: "Files", systemImage: "doc"),
        QuickAction(title: "Write", systemImage: "pencil"),
        QuickAction(title: "Research", systemImage: "magnifyingglass"),
        QuickAction(title: "Code", systemImage: "chevron.left.forwardslash.chevron.right"),
        QuickAction(title: "Translate", systemImage: "globe")
    ]

    @State private var recents: [RecentItem] = [
        RecentItem(title: "Untitled conversation", subtitle: "Chat · yesterday", systemImage: "bubble.left"),
        RecentItem(title: "Draft: product brief", subtitle: "Write · 2 days ago", systemImage: "doc.text")
    ]

    var body: some View {
        TabView {
            CommandCentre()
                .tabItem { Label("Home", systemImage: "house") }

            PlaceholderTab(title: "Chats", systemImage: "bubble.left")
                .tabItem { Label("Chats", systemImage: "bubble.left") }

            PlaceholderTab(title: "Create", systemImage: "plus.circle")
                .tabItem { Label("Create", systemImage: "plus.circle") }

            PlaceholderTab(title: "Library", systemImage: "books.vertical")
                .tabItem { Label("Library", systemImage: "books.vertical") }
        }
    }
}

// MARK: - AI command centre (Home tab content)

private struct CommandCentre: View {

    private let palette = GSTheme.Palette.light

    private let columns = [
        GridItem(.flexible()),
        GridItem(.flexible()),
        GridItem(.flexible()),
        GridItem(.flexible())
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: GSTheme.Spacing.l) {
                header
                inputBar
                quickActionGrid
                continueSection
            }
            .padding()
        }
        .background(palette.background.ignoresSafeArea())
    }

    // MARK: Greeting

    private var header: some View {
        VStack(alignment: .leading, spacing: GSTheme.Spacing.s) {
            Text("Good day")
                .font(GSTheme.displayTitle())
                .foregroundStyle(palette.text)
            Text("Your AI command centre")
                .font(GSTheme.body())
                .foregroundStyle(palette.textMuted)
        }
    }

    // MARK: Universal input bar

    private var inputBar: some View {
        HStack(spacing: GSTheme.Spacing.s) {
            Image(systemName: "sparkles")
                .foregroundStyle(palette.accent)
            Text("Ask anything…")
                .font(GSTheme.body())
                .foregroundStyle(palette.textMuted)
            Spacer()
            Image(systemName: "mic")
                .foregroundStyle(palette.textMuted)
            Image(systemName: "camera")
                .foregroundStyle(palette.textMuted)
        }
        .padding(.horizontal, GSTheme.Spacing.m)
        .padding(.vertical, 14)
        .background(
            RoundedRectangle(cornerRadius: GSTheme.Radius.card)
                .fill(palette.surface)
        )
        .gsCardShadow()
    }

    // MARK: Quick actions

    private var quickActionGrid: some View {
        VStack(alignment: .leading, spacing: GSTheme.Spacing.m) {
            sectionHeader("Quick actions")
            LazyVGrid(columns: columns, spacing: 12) {
                ForEach(quickActions) { action in
                    QuickActionCard(action: action, palette: palette)
                }
            }
        }
    }

    // MARK: Continue where you left off

    private var continueSection: some View {
        VStack(alignment: .leading, spacing: GSTheme.Spacing.m) {
            sectionHeader("Continue where you left off")
            HStack(spacing: GSTheme.Spacing.m) {
                ForEach(recents) { item in
                    RecentCard(item: item, palette: palette)
                }
            }
        }
    }

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .font(GSTheme.headline())
            .foregroundStyle(palette.text)
    }
}

// MARK: - Cards

private struct QuickActionCard: View {
    let action: QuickAction
    let palette: GSTheme.Palette

    var body: some View {
        VStack(spacing: GSTheme.Spacing.s) {
            Image(systemName: action.systemImage)
                .font(.system(size: 20, weight: .medium))
                .foregroundStyle(palette.accent)
            Text(action.title)
                .font(GSTheme.caption())
                .foregroundStyle(palette.text)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, GSTheme.Spacing.m)
        .background(
            RoundedRectangle(cornerRadius: GSTheme.Radius.card)
                .fill(palette.surface)
        )
        .gsCardShadow()
    }
}

private struct RecentCard: View {
    let item: RecentItem
    let palette: GSTheme.Palette

    var body: some View {
        HStack(spacing: GSTheme.Spacing.s) {
            Image(systemName: item.systemImage)
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(palette.accent)
            VStack(alignment: .leading, spacing: 2) {
                Text(item.title)
                    .font(GSTheme.body())
                    .foregroundStyle(palette.text)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                Text(item.subtitle)
                    .font(GSTheme.caption())
                    .foregroundStyle(palette.textMuted)
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
        }
        .padding(GSTheme.Spacing.m)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: GSTheme.Radius.card)
                .fill(palette.elevatedSurface)
        )
        .gsCardShadow()
    }
}

// MARK: - Placeholder tabs

private struct PlaceholderTab: View {
    let title: String
    let systemImage: String
    private let palette = GSTheme.Palette.light

    var body: some View {
        VStack(spacing: GSTheme.Spacing.s) {
            Image(systemName: systemImage)
                .font(.system(size: 34))
                .foregroundStyle(palette.textMuted)
            Text(title)
                .font(GSTheme.headline())
                .foregroundStyle(palette.text)
            Text("Coming in the next build.")
                .font(GSTheme.caption())
                .foregroundStyle(palette.textMuted)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(palette.background.ignoresSafeArea())
    }
}
