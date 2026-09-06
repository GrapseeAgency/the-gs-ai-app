import SwiftUI

/**
 * AERUO KINETIC navigation shell — 5 bottom tabs (Home · Chats · Explore ·
 * Create · Library). Everything else is pushed: Projects, Assistants, Models,
 * Search, Profile, Settings, Voice, Notifications. Nav bar stays clean.
 */

enum AeroRoute: Hashable {
    case chat(String?)                 // nil = new chat
    case assistant(String)
    case assistantCreate
    case project(String)
    case models
    case modelCompare
    case search
    case settings
    case notifications
    case voice
    case chatArchive
    case chatFolders
    case chatShared
    case chatSearch
}

private enum AeroTab: String, CaseIterable {
    case home, chats, explore, create, library

    var title: String {
        switch self {
        case .home: return "Home"
        case .chats: return "Chats"
        case .explore: return "Explore"
        case .create: return "Create"
        case .library: return "Library"
        }
    }

    var icon: String {
        switch self {
        case .home: return "house"
        case .chats: return "bubble.left"
        case .explore: return "safari"
        case .create: return "plus.circle"
        case .library: return "books.vertical"
        }
    }
}

struct RootTabView: View {
    @State private var selection: AeroTab = .home

    var body: some View {
        TabView(selection: $selection) {
            ForEach(AeroTab.allCases, id: \.rawValue) { tab in
                NavigationStack {
                    root(for: tab)
                        .aeroDestinations()
                }
                .tabItem { Label(tab.title, systemImage: tab.icon) }
                .tag(tab)
            }
        }
        .tint(Aero.accent)
    }

    @ViewBuilder
    private func root(for tab: AeroTab) -> some View {
        switch tab {
        case .home: HomeView()
        case .chats: ChatsListView()
        case .explore: ExploreView()
        case .create: CreateView()
        case .library: LibraryView()
        }
    }
}

/// Install once per NavigationStack: resolves every pushed route.
struct AeroDestinations: ViewModifier {
    func body(content: Content) -> some View {
        content.navigationDestination(for: AeroRoute.self) { route in
            switch route {
            case .chat(let id): ChatDetailView(conversationID: id)
            case .assistant(let id): AssistantDetailView(assistantID: id)
            case .assistantCreate: AssistantCreateView()
            case .project(let id): ProjectDetailView(projectID: id)
            case .models: ModelCentreView()
            case .modelCompare: ModelCompareView()
            case .search: SearchView()
            case .settings: SettingsView()
            case .notifications: NotificationsView()
            case .voice: VoiceView()
            case .chatArchive: ArchivedChatsView()
            case .chatFolders: FoldersView()
            case .chatShared: SharedChatsView()
            case .chatSearch: ChatSearchView()
            }
        }
    }
}

extension View {
    func aeroDestinations() -> some View { modifier(AeroDestinations()) }
}
