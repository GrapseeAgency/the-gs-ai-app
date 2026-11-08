import Foundation

/// Insert app presentation into HEAD without demoting a full document's CSP
/// to BODY. Inline source/script bytes remain data and are never rewritten.
enum HTMLDocumentIOS {
    static func withHead(_ source: String, head: String) -> String {
        let options: String.CompareOptions = [.regularExpression, .caseInsensitive]
        let full = source.range(of: #"^\s*(?:<!--[\s\S]*?-->\s*)*(?:<!doctype\s+html\b|<html\b|<head\b)"#, options: options) != nil
        func insert(_ pattern: String, make: (String) -> String) -> String? {
            guard let range = source.range(of: pattern, options: options) else { return nil }
            return String(source[..<range.lowerBound]) + make(String(source[range])) + String(source[range.upperBound...])
        }
        if full {
            if let doc = insert(#"<head(?:\s[^>]*)?>"#, make: { $0 + head }) { return doc }
            if let doc = insert(#"<html(?:\s[^>]*)?>"#, make: { $0 + "<head>" + head + "</head>" }) { return doc }
            if let doc = insert(#"<!doctype[^>]*>"#, make: { $0 + "<head>" + head + "</head>" }) { return doc }
        }
        return "<!doctype html><html><head>" + head + "</head><body>" + source + "</body></html>"
    }
}
