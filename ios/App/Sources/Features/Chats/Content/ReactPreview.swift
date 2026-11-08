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
        return URL(string: "https://gs-react-\(hash.prefix(48)).invalid/")!
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
        context.coordinator.webView = view
        view.scrollView.keyboardDismissMode = .interactive
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
    static func dismantleUIView(_ view: WKWebView, coordinator: Coordinator) {
        coordinator.restoreKeyboardInset()
    }
    final class Coordinator: NSObject, WKNavigationDelegate {
        weak var webView: WKWebView?
        private weak var adjustedScroll: UIScrollView?
        private var originalBottomInset: CGFloat?
        override init() {
            super.init()
            NotificationCenter.default.addObserver(self, selector: #selector(keyboardChanged(_:)), name: UIResponder.keyboardDidChangeFrameNotification, object: nil)
        }
        deinit { NotificationCenter.default.removeObserver(self) }
        func restoreKeyboardInset() {
            if let scroll = adjustedScroll, let bottom = originalBottomInset {
                var inset = scroll.contentInset; inset.bottom = bottom; scroll.contentInset = inset
            }
            adjustedScroll = nil; originalBottomInset = nil
        }
        private func ownsFirstResponder(_ view: UIView) -> Bool {
            view.isFirstResponder || view.subviews.contains { ownsFirstResponder($0) }
        }
        @objc private func keyboardChanged(_ note: Notification) {
            guard let web = webView, let window = web.window,
                  let keyboardScreen = note.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect else { return }
            let keyboard = window.convert(keyboardScreen, from: nil)
            guard keyboard.minY < window.bounds.maxY else { restoreKeyboardInset(); return }
            guard ownsFirstResponder(web) else { return }
            // Read the ACTUAL focused form bounds. No runtime bridge is exposed
            // to generated code, and no task value/state is changed. A nested
            // inline WK view cannot itself move its SwiftUI chat scroll parent
            // when the native keyboard covers the form's submit button.
            web.evaluateJavaScript("(function(){const a=document.activeElement;if(!a||a===document.body)return null;const r=(a.closest('form')||a).getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,height:r.height,viewport:innerWidth};})()") { [weak self, weak web] result, _ in
                guard let self, let web, let window = web.window, self.ownsFirstResponder(web),
                      let rect = result as? [String: Double],
                      let x = rect["x"], let y = rect["y"], let width = rect["width"], let height = rect["height"],
                      let viewport = rect["viewport"], viewport > 0,
                      [x, y, width, height, viewport].allSatisfy({ $0.isFinite }) else { return }
                var ancestor = web.superview
                var parent: UIScrollView?
                while let current = ancestor {
                    if let scroll = current as? UIScrollView { parent = scroll; break }
                    ancestor = current.superview
                }
                let scroll = parent ?? web.scrollView
                let visible = scroll.convert(scroll.bounds, to: window)
                let overlap = visible.intersection(keyboard)
                if self.adjustedScroll !== scroll { self.restoreKeyboardInset() }
                if !overlap.isNull && overlap.height > 0 {
                    if self.originalBottomInset == nil { self.originalBottomInset = scroll.contentInset.bottom }
                    self.adjustedScroll = scroll
                    var inset = scroll.contentInset
                    inset.bottom = max(self.originalBottomInset ?? 0, overlap.height)
                    scroll.contentInset = inset
                } else { self.restoreKeyboardInset() }
                let scale = web.bounds.width / CGFloat(viewport)
                let actualForm = CGRect(x: CGFloat(x) * scale, y: CGFloat(y) * scale,
                                        width: CGFloat(width) * scale, height: CGFloat(height) * scale)
                scroll.scrollRectToVisible(scroll.convert(actualForm, from: web).insetBy(dx: 0, dy: -8), animated: true)
            }
        }
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
