import BotAvatarsKit
import Foundation

/// The four assistant identities exposed to users in Chat settings.
/// Raw values deliberately match BotAvatarsKit's stable type names and Android's stored IDs.
enum AssistantAvatarStyle: String, CaseIterable, Identifiable, Equatable {
    case clover
    case flower
    case triangle
    case square

    static let defaultValue: AssistantAvatarStyle = .clover

    static func fromStoredValue(_ value: String?) -> AssistantAvatarStyle {
        guard let value, let style = AssistantAvatarStyle(rawValue: value) else {
            return defaultValue
        }
        return style
    }

    var id: String { rawValue }
    var title: String { rawValue.capitalized }
    var botAvatarType: BotAvatarType { BotAvatarType(rawValue: rawValue) ?? .clover }
}
