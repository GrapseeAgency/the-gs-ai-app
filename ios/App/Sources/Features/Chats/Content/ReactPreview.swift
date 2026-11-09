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
            NotificationCenter.default.addObserver(self, selector: #selector(keyboardChanged(_:)), name: UIResponder.keyboardDidShowNotification, object: nil)
        }
        deinit { NotificationCenter.default.removeObserver(self) }
        func restoreKeyboardInset() {
            if let scroll = adjustedScroll, let bottom = originalBottomInset {
                var inset = scroll.contentInset; inset.bottom = bottom; scroll.contentInset = inset
            }
            adjustedScroll = nil; originalBottomInset = nil
        }
        @objc private func keyboardChanged(_ note: Notification) {
            guard let web = webView, let window = web.window,
                  let keyboardValue = note.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue else { return }
            let keyboard = window.convert(keyboardValue.cgRectValue, from: nil)
            guard keyboard.minY < window.bounds.maxY else { restoreKeyboardInset(); return }
            // Establish actual DOM input focus, not a guessed UIKit responder
            // inside WebKit's private remote-input hierarchy. Read no values,
            // inject no events, and expose no generated-code native bridge.
            web.evaluateJavaScript("(function(){const e=document.activeElement;return !!(document.hasFocus()&&e&&e!==document.body&&(e.matches('input,textarea,select')||e.isContentEditable));})()") { [weak self, weak web] result, _ in
                #if DEBUG
                print("GS_REACT_FOCUS_CHECK: focused=\(result as? Bool == true)")
                #endif
                guard let self, let web, let window = web.window, result as? Bool == true else { return }
                var ancestor = web.superview
                var parent: UIScrollView?
                while let current = ancestor {
                    if let scroll = current as? UIScrollView { parent = scroll; break }
                    ancestor = current.superview
                }
                let scroll = parent ?? web.scrollView
                scroll.superview?.layoutIfNeeded()
                let visible = scroll.convert(scroll.bounds, to: window)
                let overlap = visible.intersection(keyboard)
                if self.adjustedScroll !== scroll { self.restoreKeyboardInset() }
                if !overlap.isNull && overlap.height > 0 {
                    if self.originalBottomInset == nil { self.originalBottomInset = scroll.contentInset.bottom }
                    self.adjustedScroll = scroll
                    var inset = scroll.contentInset
                    inset.bottom = max(self.originalBottomInset ?? 0, overlap.height)
                    if inset != scroll.contentInset { scroll.contentInset = inset }
                } else { self.restoreKeyboardInset() }
                // Actual failure pixels showed the WHOLE inline WK canvas below
                // the keyboard. Bring that native canvas into the measured
                // parent viewport, rather than subtracting visualViewport offsets
                // a second time from WebKit's already-panned DOM coordinates.
                let canvas = web.convert(web.bounds, to: window)
                let top = max(visible.minY, window.safeAreaInsets.top) + 8
                if canvas.maxY > keyboard.minY || canvas.minY < top {
                    let minimum = -scroll.adjustedContentInset.top
                    let maximum = max(minimum, scroll.contentSize.height - scroll.bounds.height + scroll.adjustedContentInset.bottom)
                    let y = min(maximum, max(minimum, scroll.contentOffset.y + canvas.minY - top))
                    if abs(y - scroll.contentOffset.y) > 0.5 {
                        scroll.setContentOffset(CGPoint(x: scroll.contentOffset.x, y: y), animated: false)
                    }
                }
                #if DEBUG
                print("GS_REACT_KEYBOARD_LAYOUT: parent=\(type(of: scroll)); viewport=\(visible); canvas=\(canvas); keyboard=\(keyboard); offset=\(scroll.contentOffset); size=\(scroll.contentSize)")
                #endif
            }
        }
        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            // Run can be tapped while the new 420pt canvas is below the chat
            // viewport. Offscreen WebKit content is throttled and its controls
            // are not reliably exposed to accessibility or native hit testing.
            // Reveal the actual canvas in its containing conversation scroller;
            // do not pan the DOM or scroll the separate source-code editor.
            guard webView.window != nil else { return }
            var ancestor = webView.superview
            while let current = ancestor {
                if let scroll = current as? UIScrollView {
                    scroll.superview?.layoutIfNeeded()
                    let canvas = webView.convert(webView.bounds, to: scroll)
                    scroll.scrollRectToVisible(canvas.insetBy(dx: 0, dy: -8), animated: false)
                    break
                }
                ancestor = current.superview
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
