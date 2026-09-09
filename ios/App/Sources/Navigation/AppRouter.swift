import SwiftUI

/**
 * AERUO KINETIC navigation shell — benchmark AI-app architecture:
 * one Home canvas + an obsidian drawer as primary navigation
 * (ChatGPT / Claude / Kimi pattern). Everything else is pushed onto
 * the single NavigationStack.
 */

enum AeroRoute: Hashable {
    case chat(String?)                 // nil = new chat
    case chatPrefill(String)           // voice press-and-hold hands its transcript here
    case assistant(String)
    case assistantCreate
    case assistantEdit(String)
    case project(String)
    case models
    case modelCompare
    case search
    case settings
    case notifications
    case voice
    case imageStudio              // Image Studio — also the New image quick action
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

/// Programmatic navigation: any view — including one inside a sheet — can
/// append a route without a NavigationLink in reach. Classic ObservableObject
/// per the project's iOS baseline (the modern observation macro is banned).
final class Router: ObservableObject {
    @Published var path: [AeroRoute] = []
}

/// Root shell — Home canvas + drawer overlay over one NavigationStack.
struct RootView: View {
    @StateObject private var router = Router()
    @ObservedObject private var quickActions = QuickActionBus.shared
    @State private var showDrawer = false

    // Task 85-e I9 — finger-tracked opening. `drawerEntryOffset` is the
    // panel's live offset while a leading-edge swipe pulls the drawer out
    // (negative = still mostly off-screen); a plain open resets it to 0 so
    // the standard .move transition runs. `edgeDragActive` keeps tracking
    // after the mount flips `showDrawer` true mid-gesture.
    @State private var drawerEntryOffset: CGFloat = 0
    @State private var edgeDragActive = false

    /// Finger travel (pt) that maps to a fully revealed drawer; over-pull
    /// rubber-bands. Same panel width/palette as ever — only the gesture
    /// machinery is new.
    private static let revealSpan: CGFloat = 280

    var body: some View {
        ZStack {
            NavigationStack(path: $router.path) {
                HomeView(
                    onOpenDrawer: {
                        drawerEntryOffset = 0
                        withAnimation(Aero.motion(Aero.spring)) { showDrawer = true }
                    },
                    onRoute: { router.path.append($0) }
                )
                .aeroDestinations()
            }
            .environmentObject(router)

            if showDrawer {
                AeroDrawer(
                    entryOffset: $drawerEntryOffset,
                    onRoute: { route in
                        withAnimation(Aero.motion(Aero.spring)) { showDrawer = false }
                        router.path.append(route)
                    },
                    onClose: { withAnimation(Aero.motion(Aero.spring)) { showDrawer = false } }
                )
                .transition(.opacity)
            }
        }
        .simultaneousGesture(edgeOpenGesture)
        .onReceive(quickActions.$pendingRoute) { route in
            guard let route else { return }
            quickActions.consume()
            withAnimation(Aero.motion(Aero.spring)) { showDrawer = false }
            router.path.append(route)
        }
        .tint(Aero.accent)
    }

    // MARK: Edge-swipe-to-open (Task 85-e I9)

    /// Panel rides the finger: fully off-screen until the swipe begins,
    /// at rest after `revealSpan` of travel, rubber-banded past that.
    private static func revealOffset(for raw: CGFloat) -> CGFloat {
        let travelled = raw - revealSpan
        return travelled < 0 ? travelled : travelled * 0.12
    }

    /// A rightward drag starting in the leading ~24pt strip opens the drawer
    /// with live tracking; a short or slow release springs it back shut.
    /// simultaneousGesture keeps the underlying controls fully interactive —
    /// the start-location gate is what confines it to the edge zone, and the
    /// Home-root gate keeps the edge single-purpose: once a screen is pushed,
    /// the leading edge belongs to the system's interactive-pop back swipe,
    /// never to both at once.
    private var edgeOpenGesture: some Gesture {
        DragGesture(minimumDistance: 20)
            .onChanged { value in
                guard value.startLocation.x < 24, value.translation.width > 0 else { return }
                if !edgeDragActive {
                    // Leading-edge drawer reveal only from the Home root —
                    // pushed screens hand the edge to interactive pop.
                    guard router.path.isEmpty, !showDrawer else { return }
                    edgeDragActive = true
                    showDrawer = true   // mounts un-animated, straight at the finger
                }
                drawerEntryOffset = Self.revealOffset(for: value.translation.width)
            }
            .onEnded { value in
                guard edgeDragActive else { return }
                edgeDragActive = false
                let raw = value.translation.width
                let projected = value.predictedEndTranslation.width
                if raw > Self.revealSpan * 0.5 || projected > Self.revealSpan * 0.9 {
                    withAnimation(Aero.motion(Aero.spring)) { drawerEntryOffset = 0 }
                } else {
                    withAnimation(Aero.motion(Aero.spring)) { showDrawer = false }
                }
            }
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
            case .chatPrefill(let prompt): ChatDetailView(conversationID: nil, prefill: prompt)
            case .assistant(let id): AssistantDetailView(assistantID: id)
            case .assistantCreate: AssistantCreateView()
            case .assistantEdit(let id): AssistantCreateView(editID: id)
            case .project(let id): ProjectDetailView(projectID: id)
            case .models: ModelCentreView()
            case .modelCompare: ModelCompareView()
            case .search: SearchView()
            case .settings: SettingsView()
            case .notifications: NotificationsView()
            case .voice: VoiceView()
            case .imageStudio: ImageStudioView()
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
