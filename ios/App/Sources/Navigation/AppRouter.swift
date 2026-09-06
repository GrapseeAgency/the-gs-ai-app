import SwiftUI

/**
 * AERUO KINETIC navigation shell — benchmark AI-app architecture:
 * one Home canvas + an obsidian drawer as primary navigation
 * (ChatGPT / Claude / Kimi pattern). Everything else is pushed onto
 * the single NavigationStack.
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

    // Section roots — reached from the drawer (no tab bar anywhere)
    case chats
    case explore
    case createTab
    case library
    case projects
    case assistants
    case profile
    case billing
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

/// Root shell — Home canvas + drawer overlay over one NavigationStack.
struct RootView: View {
    @State private var path: [AeroRoute] = []
    @State private var showDrawer = false

    var body: some View {
        ZStack {
            NavigationStack(path: $path) {
                HomeView(
                    onOpenDrawer: { withAnimation(Aero.spring) { showDrawer = true } }
                )
                .aeroDestinations()
            }

            if showDrawer {
                AeroDrawer(
                    onRoute: { route in
                        withAnimation(Aero.spring) { showDrawer = false }
                        path.append(route)
                    },
                    onClose: { withAnimation(Aero.spring) { showDrawer = false } }
                )
                .transition(.opacity)
            }
        }
        .tint(Aero.accent)
    }
}

/// Legacy alias — kept so any external reference keeps compiling.
typealias RootTabView = RootView

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

            // Section roots (drawer)
            case .chats: ChatsListView()
            case .explore: ExploreView()
            case .createTab: CreateView()
            case .library: LibraryView()
            case .projects: ProjectsView()
            case .assistants: AssistantsView()
            case .profile: ProfileView()
            case .billing: BillingView()
            }
        }
    }
}

extension View {
    func aeroDestinations() -> some View { modifier(AeroDestinations()) }
}
