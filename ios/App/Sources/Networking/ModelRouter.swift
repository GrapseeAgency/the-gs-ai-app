import Foundation

/// The same four preference IDs and defaults as Android's GsModel.
enum GsModel: String, CaseIterable, Identifiable {
    case pro, lite, vision, code
    var id: String { rawValue }
    var title: String {
        switch self {
        case .pro: return "Nexly Pro"
        case .lite: return "Nexly Lite"
        case .vision: return "Nexly Vision"
        case .code: return "Nexly Code"
        }
    }
    var defaultEffort: String {
        switch self {
        case .lite: return "low"
        case .code: return "high"
        case .pro, .vision: return "standard"
        }
    }
    var capabilities: Set<String> {
        switch self {
        case .pro: return ["text", "vision", "code"]
        case .lite: return ["text"]
        case .vision: return ["vision"]
        case .code: return ["code"]
        }
    }
    static func byId(_ id: String?) -> GsModel {
        id.flatMap(GsModel.init(rawValue:)) ?? .pro
    }
}

/// Maps the reader's reasoning preference to the server-supported mode.
/// Capability and provider routing stay on the GS server, which has the full
/// message and attachment context. Auto therefore sends `auto`; it does not
/// make a client-side keyword guess or claim to force a provider model.
enum ModelRouter {

    static func mode(for effort: String) -> String {
        switch effort {
        case "minimal", "low", "light": return "flash"
        case "high": return "thinking"
        default: return "auto"
        }
    }

    static func modeForMessage(effort: String, autoSelect: Bool) -> String {
        autoSelect ? "auto" : mode(for: effort)
    }
}
