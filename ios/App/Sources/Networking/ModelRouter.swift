import Foundation

/// The four models the Model Selection reference draws, and the rule that makes
/// its two controls work together — the twin of Android's `GsModel`.
///
/// HONESTY BOUNDARY: the server decides which model actually answers a message
/// (`src/app/api/v1/conversations/[id]/route.ts` ignores a client model). What
/// the app genuinely controls is the wire mode — `flash | thinking | auto`.
/// The model name is the app's stated preference; the mode is what the next
/// reply does.
enum GsModel: String, CaseIterable, Identifiable {
    case pro
    case lite
    case vision
    case code

    var id: String { rawValue }

    var title: String {
        switch self {
        case .pro: return "Nexly Pro"
        case .lite: return "Nexly Lite"
        case .vision: return "Nexly Vision"
        case .code: return "Nexly Code"
        }
    }

    /// Effort this model runs at when Auto Select chooses it.
    var defaultEffort: String {
        switch self {
        case .pro: return "standard"
        case .lite: return "low"
        case .vision: return "standard"
        case .code: return "high"
        }
    }

    /// Capability bucket the reference's All/Text/Vision/Code tabs filter on.
    var capabilities: Set<String> {
        switch self {
        case .pro: return ["text", "vision", "code"]
        case .lite: return ["text"]
        case .vision: return ["vision"]
        case .code: return ["code"]
        }
    }

    /// Stored ids are stable; an unknown id falls back to Pro instead of
    /// leaving the page with no selection.
    static func byId(_ id: String?) -> GsModel {
        GsModel(rawValue: id ?? "") ?? .pro
    }
}

enum ModelRouter {

    /// Signals that a message wants the code model. Deliberately narrow: a
    /// fenced block, a stack trace, or a handful of programming words. A false
    /// positive only means "answer with the deeper model".
    private static let codeHints = [
        "```", "stack trace", "traceback", "compile", "kotlin", "swift",
        "python", "javascript", "typescript", "gradle", "sql", "regex",
        "function(", "nullpointer", "exception",
    ]

    /// Which model Auto Select would use. An image outranks everything, a code
    /// signal outranks length, a long message is complex enough for Pro, and
    /// otherwise the stored preference wins.
    static func pick(text: String, hasImage: Bool, preferred: GsModel) -> GsModel {
        if hasImage { return .vision }
        let lower = text.lowercased()
        if codeHints.contains(where: { lower.contains($0) }) { return .code }
        if text.count >= 220 { return .pro }
        return preferred
    }

    /// The three-mode wire value for an effort tier. `minimal` and `low` are
    /// both `flash` because the wire cannot tell them apart; the tile still
    /// remembers which one was tapped.
    static func mode(for effort: String) -> String {
        switch effort {
        case "minimal", "low", "light": return "flash"
        case "high": return "thinking"
        default: return "auto"
        }
    }

    /// Auto Select ON takes the picked model's default effort; OFF takes the
    /// effort the reader set. Both states are read by the send path.
    static func modeForMessage(
        text: String,
        hasImage: Bool,
        preferred: GsModel,
        effort: String,
        autoSelect: Bool
    ) -> String {
        guard autoSelect else { return mode(for: effort) }
        let model = pick(text: text, hasImage: hasImage, preferred: preferred)
        return mode(for: model.defaultEffort)
    }
}
