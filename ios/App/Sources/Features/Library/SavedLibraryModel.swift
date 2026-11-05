import Foundation

/// The saved library's pure logic — facets, labels, day headings, snippets.
///
/// Twin of Android's `LibraryModel`, and deliberately outside the views for
/// the same reason: the library's only real job is to not lose things, and
/// that is a question about the data, not about the pixels.
///
/// Kinds come from `ConversationStore.saveToLibrary`: chat turns land as
/// "message", the studios save images and drafts under their own kind. The
/// list is open — an unknown kind is labelled from its own name rather than
/// dropped, because a saved item the app refuses to show is a lost item.
enum SavedLibraryModel {

    /// The facet that means "no filter".
    static let all = "all"

    /// The order facets appear in: the kinds a chat actually produces first.
    private static let canonical = ["message", "image", "draft"]

    static func kindLabel(_ kind: String) -> String {
        switch kind.lowercased() {
        case "message": return "Message"
        case "image": return "Image"
        case "draft": return "Draft"
        case "": return "Saved item"
        default: return kind.prefix(1).uppercased() + kind.dropFirst()
        }
    }

    static func facetLabel(_ kind: String) -> String {
        switch kind.lowercased() {
        case "message": return "Messages"
        case "image": return "Images"
        case "draft": return "Drafts"
        case "": return "Items"
        default: return kind.prefix(1).uppercased() + kind.dropFirst() + "s"
        }
    }

    /// The chips this library should show, with their counts.
    ///
    /// Built from the items that exist, not from a fixed list: a "Drafts" chip
    /// that filters to an empty page is the dead end this app keeps deleting.
    /// An empty library therefore has no chips at all.
    static func facets(_ items: [LibraryItem]) -> [LibraryFacet] {
        var counts: [String: Int] = [:]
        for item in items { counts[item.kind.lowercased(), default: 0] += 1 }
        let head = canonical.filter { counts[$0] != nil }
        let tail = counts.keys.filter { !canonical.contains($0) }.sorted()
        return (head + tail).map { kind in
            LibraryFacet(kind: kind, label: facetLabel(kind), count: counts[kind] ?? 0)
        }
    }

    /// `kind` nil or `all` means everything; anything else is an exact match.
    static func filter(_ items: [LibraryItem], kind: String?) -> [LibraryItem] {
        guard let kind, !kind.isEmpty, kind != all else { return items }
        return items.filter { $0.kind.lowercased() == kind.lowercased() }
    }

    /// The day a save belongs to, or nil when the timestamp cannot be read.
    static func day(of iso: String) -> Date? {
        let withFraction = ISO8601DateFormatter()
        withFraction.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = withFraction.date(from: iso) { return date }
        let plain = ISO8601DateFormatter()
        plain.formatOptions = [.withInternetDateTime]
        return plain.date(from: iso)
    }

    /// "Today" / "Yesterday" / "5 Nov 2026" — the heading above a day's saves.
    static func dayLabel(_ date: Date, today: Date = Date(), calendar: Calendar = .current) -> String {
        if calendar.isDate(date, inSameDayAs: today) { return "Today" }
        if let yesterday = calendar.date(byAdding: .day, value: -1, to: today),
           calendar.isDate(date, inSameDayAs: yesterday) {
            return "Yesterday"
        }
        let out = DateFormatter()
        out.locale = Locale(identifier: "en_GB")
        out.dateFormat = "d MMM yyyy"
        return out.string(from: date)
    }

    /// "5 Nov" — the short stamp on a row whose heading is an absolute day.
    static func shortDate(_ iso: String) -> String {
        guard let date = day(of: iso) else { return "" }
        let out = DateFormatter()
        out.locale = Locale(identifier: "en_GB")
        out.dateFormat = "d MMM"
        return out.string(from: date)
    }

    /// One line of a saved answer. Whitespace collapses so a multi-paragraph
    /// answer cannot push the row past the reference's 42.5pt pitch.
    static func snippet(_ content: String, max: Int = 72) -> String {
        let flat = content
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if flat.count <= max { return flat }
        let cut = String(flat.prefix(max))
        if let space = cut.lastIndex(of: " "), cut.distance(from: cut.startIndex, to: space) > max / 2 {
            return String(cut[..<space]).trimmingCharacters(in: .whitespaces)
                .trimmingCharacters(in: CharacterSet(charactersIn: ",.;:")) + "…"
        }
        return cut + "…"
    }

    /// Items grouped under day headings, newest first. An unreadable timestamp
    /// falls under "Earlier" rather than vanishing.
    static func groupByDay(_ items: [LibraryItem], today: Date = Date()) -> [LibraryDay] {
        let sorted = items.sorted { $0.createdAt > $1.createdAt }
        var order: [String] = []
        var buckets: [String: [LibraryItem]] = [:]
        for item in sorted {
            let key = day(of: item.createdAt).map { dayLabel($0, today: today) } ?? "Earlier"
            if buckets[key] == nil { order.append(key) }
            buckets[key, default: []].append(item)
        }
        return order.map { LibraryDay(label: $0, items: buckets[$0] ?? []) }
    }

    /// "9 messages · 2 images · 1 draft" — the line under the hero's count.
    /// Singular at one, because "1 drafts" is the kind of copy a count is
    /// supposed to get right.
    static func summaryLine(_ items: [LibraryItem]) -> String {
        if items.isEmpty { return "Nothing saved yet" }
        return facets(items).map { facet in
            let name = facet.count == 1 ? kindLabel(facet.kind) : facet.label
            return "\(facet.count) \(name.lowercased())"
        }.joined(separator: " · ")
    }

    /// Kind → look. One mapping for the list, the item page and Memory's
    /// preview, so a saved image cannot be a rose palette tile on one screen
    /// and a lilac message tile on the next.
    static func glyph(_ kind: String) -> String {
        switch kind.lowercased() {
        case "image": return "palette"
        case "draft": return "file-text"
        default: return "message-circle"
        }
    }

    static func tone(_ kind: String) -> GsWellTone {
        switch kind.lowercased() {
        case "image": return .rose
        case "draft": return .blue
        default: return .lilac
        }
    }
}

struct LibraryFacet: Identifiable, Hashable {
    let kind: String
    let label: String
    let count: Int
    var id: String { kind }
}

struct LibraryDay: Identifiable {
    let label: String
    let items: [LibraryItem]
    var id: String { label }
}
