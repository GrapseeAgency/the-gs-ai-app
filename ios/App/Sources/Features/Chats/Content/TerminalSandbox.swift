import Foundation

/// Source-safe loader for the shared local virtual terminal. No fake OS output.
enum TerminalSandboxIOS {
    static func document(payload: [String: Any]) -> String {
        let unavailable = "<p>Terminal unavailable: runtime contract could not be loaded. Nothing was executed.</p>"
        guard let terminalURL = Bundle.main.url(forResource: "terminal-runtime", withExtension: "json"),
              let workerURL = Bundle.main.url(forResource: "javascript-worker-sandbox", withExtension: "json") else { return unavailable }
        do {
            let data = try Data(contentsOf: terminalURL)
            guard let terminal = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let template = terminal["template"] as? String,
                  let payloadToken = terminal["payloadPlaceholder"] as? String,
                  let workerToken = terminal["workerPlaceholder"] as? String,
                  payloadToken != workerToken else { return unavailable }
            let worker = try String(contentsOf: workerURL, encoding: .utf8)
            let payloadData = try JSONSerialization.data(withJSONObject: payload)
            guard let payloadJSON = String(data: payloadData, encoding: .utf8) else { return unavailable }
            func literal(_ json: String) throws -> String {
                let bytes = try JSONSerialization.data(withJSONObject: json, options: [.fragmentsAllowed])
                return String(decoding: bytes, as: UTF8.self).replacingOccurrences(of: "<", with: "\\u003c")
            }
            let slots = [payloadToken: try literal(payloadJSON), workerToken: try literal(worker)]
            guard slots.keys.allSatisfy({ template.components(separatedBy: $0).count == 2 }) else { return unavailable }
            let pattern = slots.keys.map { NSRegularExpression.escapedPattern(for: $0) }.joined(separator: "|")
            let regex = try NSRegularExpression(pattern: pattern)
            let original = template as NSString
            let result = NSMutableString(string: template)
            // Match only the original template, never token-looking user data.
            for match in regex.matches(in: template, range: NSRange(location: 0, length: original.length)).reversed() {
                if let value = slots[original.substring(with: match.range)] {
                    result.replaceCharacters(in: match.range, with: value)
                }
            }
            return result as String
        } catch { return unavailable }
    }
}
