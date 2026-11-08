import Foundation

/// Same document and runtime policy as Web/Android. Missing assets fail closed.
enum JavaScriptWorkerSandboxIOS {
    static func document(source: String) -> String {
        guard let url = Bundle.main.url(forResource: "javascript-worker-sandbox", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let contract = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let template = contract["template"] as? String,
              let token = contract["sourcePlaceholder"] as? String,
              template.components(separatedBy: token).count == 2,
              let literal = try? JSONSerialization.data(withJSONObject: source, options: [.fragmentsAllowed]),
              let encoded = String(data: literal, encoding: .utf8)
        else { return "<p>Not executed: the JavaScript worker contract is unavailable.</p>" }
        return template.replacingOccurrences(of: token, with: encoded.replacingOccurrences(of: "<", with: "\\u003c"))
    }
}
