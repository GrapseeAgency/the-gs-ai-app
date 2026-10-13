import SwiftUI
import SafariServices

/**
 * TASK 1c — in-app browser host for reply content.
 *
 * Contract: a TAP on any reply link (inline text links, citation chips,
 * source cards) opens the IN-APP browser (SFSafariViewController: the user's
 * real session, zero app chrome to maintain, and its action menu already
 * carries "Open in Safari" as the escape hatch). The external browser is the
 * EXPLICIT path only. No reply link ever leaves the app by accident.
 *
 * One host, one presenter: `.gsInAppBrowserHost()` on a screen installs the
 * sheet and the `\.gsOpenInApp` environment action, and re-routes the
 * `\.openURL` environment so AttributedString links inside Text (the renderer
 * uses `run.link = url`) resolve through the host without per-view wiring.
 * Screens without the host keep the system behaviour (the presenter is nil
 * and callers fall back).
 */
final class InAppBrowserPresenter: ObservableObject {

    struct Presenting: Identifiable {
        let id = UUID()
        let url: URL
    }

    @Published var presenting: Presenting?

    func open(_ url: URL) {
        DispatchQueue.main.async { [weak self] in
            self?.presenting = Presenting(url: url)
        }
    }
}

private struct InAppBrowserKey: EnvironmentKey {
    static let defaultValue: InAppBrowserPresenter? = nil
}

extension EnvironmentValues {
    var gsOpenInApp: InAppBrowserPresenter? {
        get { self[InAppBrowserKey.self] }
        set { self[InAppBrowserKey.self] = newValue }
    }
}

private struct IdentifiableURLBox: Identifiable {
    let id = UUID()
    let url: URL
}

private struct SafariView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        SFSafariViewController(url: url)
    }

    func updateUIViewController(_ controller: SFSafariViewController, context: Context) {}
}

private struct InAppBrowserHostModifier: ViewModifier {
    @StateObject private var presenter = InAppBrowserPresenter()

    func body(content: Content) -> some View {
        content
            .environment(\.gsOpenInApp, presenter)
            .environment(\.openURL, OpenURLAction { url in
                // Single presentation path for every link: AttributedString
                // links inside Text resolve through here.
                let scheme = url.scheme?.lowercased()
                if scheme == "http" || scheme == "https" {
                    presenter.open(url)
                    return .handled
                }
                return .systemAction
            })
            .sheet(item: $presenter.presenting) { box in
                SafariView(url: box.url)
                    .ignoresSafeArea()
            }
    }
}

extension View {
    /// Install the in-app browser host: sheet + `\.gsOpenInApp` + `\.openURL`
    /// re-route. One per screen, at the screen root.
    func gsInAppBrowserHost() -> some View {
        modifier(InAppBrowserHostModifier())
    }
}

/// Convenience for plain Button call sites (source cards): opens through the
/// host when one is installed, else the system path — never a dead tap.
func gsOpenURL(_ url: URL, inApp: InAppBrowserPresenter?, externalFallback: Bool = true) {
    if let inApp {
        inApp.open(url)
        return
    }
    if externalFallback {
        UIApplication.shared.open(url)
    }
}
