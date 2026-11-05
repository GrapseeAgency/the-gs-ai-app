import SwiftUI

/// The inbox's one description of a notice kind.
///
/// It lived as two private functions inside `NotificationsView`, which is why
/// the notice detail page had no way to ask "what kind is this?" — the answer
/// was unreachable from anywhere else. Android has the same split
/// (`noticeStyleFor` / `relativeNoticeTime`), so both platforms read one source.
struct GsNoticeStyle {
    let glyph: String
    let tone: GsWellTone
    let label: String

    static func forKind(_ kindId: String) -> GsNoticeStyle {
        switch kindId {
        case "messages": return .init(glyph: "message-circle", tone: .rose, label: "Messages")
        case "ai-updates": return .init(glyph: "sparkles", tone: .lilac, label: "Update")
        case "product-updates": return .init(glyph: "settings", tone: .blue, label: "Updates")
        case "account-activity": return .init(glyph: "user", tone: .peach, label: "Account")
        case "tips": return .init(glyph: "bulb", tone: .peach, label: "Tips")
        default: return .init(glyph: "bell", tone: .blue, label: "Update")
        }
    }

    /// Real elapsed time from the stored stamp — never a canned "2h ago".
    static func relative(_ date: Date, now: Date) -> String {
        let seconds = max(0, now.timeIntervalSince(date))
        let minutes = Int(seconds / 60)
        if minutes < 1 { return "now" }
        if minutes < 60 { return "\(minutes)m ago" }
        let hours = minutes / 60
        if hours < 24 { return "\(hours)h ago" }
        return "\(hours / 24)d ago"
    }
}
