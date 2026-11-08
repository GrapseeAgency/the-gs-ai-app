import Foundation

/// Presentation only. Never pass escaped display text to Copy or execution.
enum CodeDisplayIOS {
    static func escape(_ source: String) -> String {
        source.unicodeScalars.map { scalar in
            let cp = scalar.value
            let forbidden = cp > 0x7f && (scalar.properties.isEmoji || (0x1f000...0x1faff).contains(cp) || cp == 0x200d || cp == 0x20e3 || cp == 0xfe0e || cp == 0xfe0f || (0xe0020...0xe007f).contains(cp))
            guard forbidden else { return String(scalar) }
            return String(scalar).utf16.map { String(format: "\\u%04x", $0) }.joined()
        }.joined()
    }
    static func requiresEncoding(_ source: String) -> Bool { escape(source) != source }
    static func encodeEditor(_ source: String) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: source, options: [.fragmentsAllowed]),
              let encoded = String(data: data, encoding: .utf8) else { return "\"\"" }
        return escape(encoded)
    }
    static func decodeEditor(_ draft: String) throws -> String {
        let value = try JSONSerialization.jsonObject(with: Data(draft.utf8), options: [.fragmentsAllowed])
        guard let source = value as? String else {
            throw NSError(domain: "CodeDisplayIOS", code: 1, userInfo: [NSLocalizedDescriptionKey: "Enter one JSON-encoded source string, not an object or array."])
        }
        return source
    }
}
