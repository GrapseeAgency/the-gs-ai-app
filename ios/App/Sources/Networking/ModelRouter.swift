import Foundation

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
