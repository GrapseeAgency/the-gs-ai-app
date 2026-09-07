import SwiftUI
import UIKit

/**
 * Home-screen quick actions — the iOS counterpart of Android's static
 * shortcuts (New chat / New image / Ask GS). Static UIApplicationShortcutItems
 * live in Info.plist; the scene delegate forwards the tapped type into a bus,
 * and RootView consumes it into the matching AeroRoute.
 */
enum QuickAction: String {
    case newChat = "com.grapsee.gsai.quick.newChat"
    case newImage = "com.grapsee.gsai.quick.newImage"
    case askGS = "com.grapsee.gsai.quick.ask"

    var route: AeroRoute {
        switch self {
        case .newChat: return .chat(nil)
        case .newImage: return .imageStudio
        case .askGS: return .voice
        }
    }
}

/// Bridges UIKit scene callbacks into the SwiftUI world. RootView observes
/// `pendingRoute` and consumes it exactly once.
final class QuickActionBus: ObservableObject {
    static let shared = QuickActionBus()
    @Published private(set) var pendingRoute: AeroRoute?

    private init() {}

    /// Dropped silently when the session gate is still at Auth — a quick
    /// action never navigates while signed out (mirrors the Android drop).
    func enqueue(_ type: String) {
        guard UserDefaults.standard.bool(forKey: "gs.session.active"),
              UserDefaults.standard.bool(forKey: "gs.onboarded"),
              let action = QuickAction(rawValue: type) else { return }
        pendingRoute = action.route
    }

    func consume() {
        pendingRoute = nil
    }
}

/// Advertises a custom scene delegate so quick actions reach the bus
/// (without an app delegate, SwiftUI's default lifecycle skips them).
final class QuickActionAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        configuration.delegateClass = QuickActionSceneDelegate.self
        return configuration
    }
}

/// Delivers quick actions for warm taps and cold launches alike — for a cold
/// launch UIKit re-dispatches the connection-options item here when
/// scene(_:willConnectTo:options:) leaves it untouched.
final class QuickActionSceneDelegate: NSObject, UIWindowSceneDelegate {
    func windowScene(
        _ windowScene: UIWindowScene,
        performActionFor shortcutItem: UIApplicationShortcutItem,
        completionHandler: @escaping (Bool) -> Void
    ) {
        QuickActionBus.shared.enqueue(shortcutItem.type)
        completionHandler(true)
    }
}
