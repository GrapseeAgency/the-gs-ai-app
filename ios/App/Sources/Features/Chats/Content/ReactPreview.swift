import SwiftUI
import WebKit
import CryptoKit

private struct ReactPreviewScopeKey: EnvironmentKey { static let defaultValue: String? = nil }
extension EnvironmentValues {
    var reactPreviewScope: String? {
        get { self[ReactPreviewScopeKey.self] }
        set { self[ReactPreviewScopeKey.self] = newValue }
    }
}

enum ReactPreviewRuntimeIOS {
    private static let template: String? = {
        guard let url = Bundle.main.url(forResource: "react-preview-runtime", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let contract = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        return contract["template"] as? String
    }()
    static func document(source: String) -> String {
        guard let template, let policy = DOMDisplayPolicyIOS.script,
              let data = try? JSONSerialization.data(withJSONObject: source, options: [.fragmentsAllowed]),
              let literal = String(data: data, encoding: .utf8) else {
            return "<p>Not executed: the offline React runtime or display policy is unavailable.</p>"
        }
        return template.replacingOccurrences(of: "<head>", with: "<head><script>" + policy.replacingOccurrences(of: "</script", with: "<\\/script") + "</script>")
            .replacingOccurrences(of: "__GS_REACT_STORAGE_BOOTSTRAP__", with: "")
            .replacingOccurrences(of: "__GS_REACT_SOURCE_LITERAL__", with: literal.replacingOccurrences(of: "<", with: "\\u003c"))
    }
    static func origin(scope: String) -> URL {
        let hash = SHA256.hash(data: Data(scope.utf8)).map { String(format: "%02x", $0) }.joined()
        return URL(string: "https://\(hash.prefix(48)).gs-react.invalid/")!
    }
}

/// Dedicated web origin per response/snippet. Genuine WebKit localStorage is
/// persistent, with no cookie/DOM/native-bridge access to the application.
struct ReactPreviewIOS: UIViewRepresentable {
    let source: String
    let scope: String
    func makeCoordinator() -> Coordinator { Coordinator() }
    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .default()
        let view = WKWebView(frame: .zero, configuration: configuration)
        view.navigationDelegate = context.coordinator
        view.isOpaque = false
        view.backgroundColor = .clear
        view.accessibilityIdentifier = "react.preview"
        return view
    }
    func updateUIView(_ view: WKWebView, context: Context) {
        let document = ReactPreviewRuntimeIOS.document(source: source)
        let origin = ReactPreviewRuntimeIOS.origin(scope: scope)
        guard context.coordinator.lastSource != source || context.coordinator.origin != origin else { return }
        context.coordinator.lastSource = source
        context.coordinator.origin = origin
        context.coordinator.initialNavigation = true
        view.loadHTMLString(document, baseURL: origin)
    }
    final class Coordinator: NSObject, WKNavigationDelegate {
        var lastSource: String?
        var origin: URL?
        var initialNavigation = true
        func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            let initial = initialNavigation && action.navigationType == .other && action.targetFrame?.isMainFrame == true
            initialNavigation = false
            let expected = action.request.url == origin || action.request.url?.scheme == "about"
            decisionHandler(initial && expected ? .allow : .cancel)
        }
    }
}
