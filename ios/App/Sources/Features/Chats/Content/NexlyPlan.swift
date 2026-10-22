import Foundation
import SwiftUI

// ---------------------------------------------------------------------------
// The wire shape
// ---------------------------------------------------------------------------

/// One timed row of the Nexly timetable. The icon is a Tabler kebab-case id
/// resolved against the shared 5,247-glyph registry; it is never an emoji.
struct NexlyPlanRow: Equatable, Identifiable {
    let time: String
    let text: String
    let icon: String
    let tone: NexlyGlassTone

    var id: String { "\(time)|\(text)|\(icon)" }
}

struct NexlyPlanData: Equatable {
    let title: String
    let rows: [NexlyPlanRow]
    let closing: String?
    let closingIcon: String?
    let timestamp: String?

    static let placeholder = NexlyPlanData(
        title: "Building your plan…",
        rows: [],
        closing: nil,
        closingIcon: nil,
        timestamp: nil
    )
}

/// Parse a ```plan payload exactly the way src/lib/nexly/plan.ts does.
/// Rows without a time or text are dropped; icons are canonicalised; the
/// table is capped at fourteen rows. A malformed fence therefore degrades to
/// a code block instead of an empty glass card.
func parseNexlyPlan(_ raw: String) -> NexlyPlanData? {
    guard let data = raw.data(using: .utf8),
          let object = try? JSONSerialization.jsonObject(with: data),
          let root = object as? [String: Any] else { return nil }
    return parseNexlyPlan(root)
}

func parseNexlyPlan(_ root: [String: Any]) -> NexlyPlanData? {
    let source = nexlyPlanRoot(root)
    guard let rawRows = (source["rows"] as? [Any]) ?? (source["items"] as? [Any]) else { return nil }
    var rows: [NexlyPlanRow] = []
    rows.reserveCapacity(min(rawRows.count, 14))
    for rawRow in rawRows.prefix(14) {
        guard let row = rawRow as? [String: Any] else { continue }
        let time = cleanNexlyText(
            nexlyFirstString(row, "time", "start", "from", "date", "slot"),
            max: 40
        )
        let text = cleanNexlyText(
            nexlyFirstString(row, "text", "label", "title", "activity", "detail", "task"),
            max: 180
        )
        guard !time.isEmpty, !text.isEmpty else { continue }
        let icon = NexlyIcons.resolve(row["icon"] as? String)
        let tone = NexlyGlassTone(rawValue: (row["tone"] as? String ?? "").lowercased())
            ?? NexlyIcons.tone(for: icon)
        rows.append(NexlyPlanRow(time: time, text: text, icon: icon, tone: tone))
    }
    guard !rows.isEmpty else { return nil }

    let closing = cleanNexlyText(nexlyFirstString(source, "closing", "footer"), max: 220)
    let timestamp = cleanNexlyText(source["timestamp"] as? String, max: 24)
    var closingIcon: String?
    if let rawClosingIcon = source["closingIcon"] as? String, !rawClosingIcon.isEmpty {
        closingIcon = NexlyIcons.resolve(rawClosingIcon)
    }
    return NexlyPlanData(
        title: cleanNexlyText(
            nexlyFirstString(source, "title", "heading"),
            max: 160,
            fallback: "Here's your plan:"
        ),
        rows: rows,
        closing: closing.isEmpty ? nil : closing,
        closingIcon: closingIcon,
        timestamp: timestamp.isEmpty ? nil : timestamp
    )
}

/// Accept the wrappers models reach for, not only a bare root object.
private func nexlyPlanRoot(_ root: [String: Any]) -> [String: Any] {
    for key in ["plan", "schedule", "timetable"] {
        if let inner = root[key] as? [String: Any] { return inner }
    }
    return root
}

private func nexlyFirstString(_ object: [String: Any], _ keys: String...) -> String? {
    for key in keys {
        if let value = object[key] as? String { return value }
    }
    return nil
}

/// Remove pictographs the prompt bans and collapse whitespace. Known emoji are
/// converted to `:icon:` tokens first so a model slip still renders as glass.
func cleanNexlyText(_ value: String?, max: Int, fallback: String = "") -> String {
    guard let value, !value.isEmpty else { return fallback }
    let normalised = NexlyIcons.emojiToIconTokens(value)
        .replacingOccurrences(of: "\\*\\*(.+?)\\*\\*", with: "$1", options: .regularExpression)
        .replacingOccurrences(of: "__(.+?)__", with: "$1", options: .regularExpression)
        .replacingOccurrences(of: "~~(.+?)~~", with: "$1", options: .regularExpression)
        .replacingOccurrences(of: "`([^`]+)`", with: "$1", options: .regularExpression)
    let collapsed = normalised
        .components(separatedBy: .whitespacesAndNewlines)
        .filter { !$0.isEmpty }
        .joined(separator: " ")
    return String(collapsed.prefix(max))
}

// ---------------------------------------------------------------------------
// Icon registry
// ---------------------------------------------------------------------------

/// The Nexly assistant card runs one step smaller than the app's default body
/// copy, matching the sheet's airy preamble. False keeps every other chat
/// surface on its normal typography.
private struct NexlyProseCompactKey: EnvironmentKey {
    static let defaultValue = false
}

extension EnvironmentValues {
    var nexlyProseCompact: Bool {
        get { self[NexlyProseCompactKey.self] }
        set { self[NexlyProseCompactKey.self] = newValue }
    }
}

/// The sheet's cards are thinner than the app's standard card glass: a clear
/// white veil over the pearl field, a hairline rim and barely any shadow.
private struct NexlyCardGlass: ViewModifier {
    @Environment(\.colorScheme) private var scheme
    let corner: CGFloat

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: corner, style: .continuous)
        content
            .background(
                shape
                    .fill(.thinMaterial)
                    .overlay(
                        shape.fill(
                            Color.white.opacity(scheme == .dark ? 0.10 : 0.18)
                        )
                    )
            )
            .overlay(
                shape.strokeBorder(
                    LinearGradient(
                        colors: [
                            Color.white.opacity(scheme == .dark ? 0.30 : 0.88),
                            Color.white.opacity(scheme == .dark ? 0.10 : 0.32),
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    lineWidth: 0.8
                )
            )
            .shadow(
                color: Color(red: 0.45, green: 0.28, blue: 0.20)
                    .opacity(scheme == .dark ? 0.10 : 0.07),
                radius: 5,
                y: 2
            )
    }
}

extension View {
    func nexlyGlassCard(corner: CGFloat = 20) -> some View {
        modifier(NexlyCardGlass(corner: corner))
    }
}

enum NexlyGlassTone: String, CaseIterable {
    case amber, lavender, blue, green, pink, peach, mint, lilac

    var top: Color {
        switch self {
        case .amber: return Color(red: 1.000, green: 0.969, blue: 0.906)
        case .lavender: return Color(red: 0.969, green: 0.945, blue: 1.000)
        case .blue: return Color(red: 0.941, green: 0.976, blue: 1.000)
        case .green: return Color(red: 0.941, green: 0.984, blue: 0.953)
        case .pink: return Color(red: 1.000, green: 0.953, blue: 0.969)
        case .peach: return Color(red: 1.000, green: 0.965, blue: 0.929)
        case .mint: return Color(red: 0.941, green: 0.984, blue: 0.969)
        case .lilac: return Color(red: 0.969, green: 0.945, blue: 1.000)
        }
    }

    var bottom: Color {
        switch self {
        case .amber: return Color(red: 0.984, green: 0.898, blue: 0.761)
        case .lavender: return Color(red: 0.910, green: 0.863, blue: 0.984)
        case .blue: return Color(red: 0.863, green: 0.922, blue: 0.984)
        case .green: return Color(red: 0.863, green: 0.953, blue: 0.894)
        case .pink: return Color(red: 0.984, green: 0.882, blue: 0.914)
        case .peach: return Color(red: 0.984, green: 0.894, blue: 0.824)
        case .mint: return Color(red: 0.863, green: 0.953, blue: 0.925)
        case .lilac: return Color(red: 0.914, green: 0.875, blue: 0.980)
        }
    }

    /// The sheet's chips are a whisper of tone with the saturated colour in
    /// the glyph. White ink on saturated chips reads as a heavy badge.
    var ink: Color {
        switch self {
        case .amber: return Color(red: 0.878, green: 0.604, blue: 0.208)
        case .lavender: return Color(red: 0.471, green: 0.388, blue: 0.831)
        case .blue: return Color(red: 0.302, green: 0.553, blue: 0.812)
        case .green: return Color(red: 0.302, green: 0.647, blue: 0.408)
        case .pink: return Color(red: 0.839, green: 0.404, blue: 0.541)
        case .peach: return Color(red: 0.835, green: 0.498, blue: 0.298)
        case .mint: return Color(red: 0.275, green: 0.647, blue: 0.541)
        case .lilac: return Color(red: 0.502, green: 0.396, blue: 0.847)
        }
    }

    var halo: Color {
        switch self {
        case .amber: return Color(red: 0.914, green: 0.604, blue: 0.306)
        case .lavender: return Color(red: 0.608, green: 0.490, blue: 0.902)
        case .blue: return Color(red: 0.459, green: 0.663, blue: 0.902)
        case .green: return Color(red: 0.427, green: 0.788, blue: 0.541)
        case .pink: return Color(red: 0.910, green: 0.518, blue: 0.635)
        case .peach: return Color(red: 0.910, green: 0.580, blue: 0.392)
        case .mint: return Color(red: 0.424, green: 0.784, blue: 0.678)
        case .lilac: return Color(red: 0.655, green: 0.525, blue: 0.918)
        }
    }
}

enum NexlyIcons {
    static let family = "tabler-icons"

    /// Model-facing aliases for the semantic names a planner naturally uses.
    /// Every value exists in the Tabler outline set.
    static let aliases: [String: String] = [
        "study": "book-2", "studying": "book-2", "class": "book-2", "classes": "book-2",
        "lecture": "book-2", "reading": "book-2", "book": "book-2",
        "homework": "file-text", "assignment": "file-text", "assignments": "file-text",
        "work": "device-laptop", "laptop": "device-laptop", "computer": "device-laptop",
        "coding": "code", "email": "mail", "meeting": "users",
        "food": "tools-kitchen-2", "lunch": "tools-kitchen-2", "dinner": "tools-kitchen-2",
        "breakfast": "sunrise", "utensils": "tools-kitchen-2", "fork-knife": "tools-kitchen-2",
        "cafe": "coffee", "coffee": "coffee",
        "workout": "run", "exercise": "run", "gym": "barbell", "run": "run",
        "running": "run", "walk": "walk", "yoga": "stretching", "health": "heart",
        "sleep": "moon", "rest": "moon", "winddown": "moon", "wind-down": "moon",
        "night": "moon", "game": "device-gamepad-2", "games": "device-gamepad-2",
        "gaming": "device-gamepad-2", "gamepad": "device-gamepad-2",
        "hobby": "palette", "hobbies": "palette", "music": "music",
        "think": "sparkles", "thinking": "sparkles", "reasoning": "brain",
        "effort": "settings", "gear": "settings", "cog": "settings",
        "muscle": "flame", "flex": "flame", "biceps": "flame", "strong": "flame",
        "motivate": "flame", "motivation": "flame", "sparkle": "sparkles",
        "sun": "sunrise", "morning": "sunrise", "star": "star",
        "check": "checks", "done": "checks", "warning": "alert-triangle",
        "calendar": "calendar", "clock": "clock", "time": "clock",
        "target": "target", "idea": "bulb", "lightbulb": "bulb",
        "person": "user", "user": "user", "home": "home", "school": "school",
        "travel": "plane", "plane": "plane", "phone": "device-mobile",
        "mobile": "device-mobile",
    ]

    /// Known emoji → the closest semantic glass glyph. The prompt bans emoji;
    /// this is the renderer's safety net, not a suggestion to the model.
    static let emoji: [String: String] = [
        "🌅": "sunrise", "🌄": "sunrise", "☀️": "sunrise", "☀": "sunrise",
        "📚": "book-2", "📖": "book-2", "💻": "device-laptop", "🖥️": "device-laptop",
        "🍽️": "tools-kitchen-2", "🍴": "tools-kitchen-2", "🍔": "tools-kitchen-2",
        "📝": "file-text", "📄": "file-text", "🏃": "run", "🏃‍♂️": "run", "🏃‍♀️": "run",
        "🏋️": "barbell", "🎮": "device-gamepad-2", "🕹️": "device-gamepad-2",
        "🌙": "moon", "😴": "moon", "✨": "sparkles", "🧠": "brain",
        "⚙️": "settings", "⚙": "settings", "💪": "flame", "🔥": "flame",
        "❤️": "heart", "❤": "heart", "✅": "circle-check", "✔️": "circle-check",
        "⭐": "star", "🎯": "target", "☕": "coffee", "🎨": "palette",
        "🎵": "music", "🚶": "walk", "📅": "calendar", "⏰": "clock",
    ]

    private static let codepoints: [String: Int] = {
        guard let url = Bundle.main.url(forResource: "nexly-icons", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: String] else {
            return [:]
        }
        var map: [String: Int] = [:]
        map.reserveCapacity(object.count)
        for (name, hex) in object {
            if let value = Int(hex, radix: 16) { map[name] = value }
        }
        return map
    }()

    static func resolve(_ raw: String?) -> String {
        var name = (raw ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .replacingOccurrences(of: "^[:\\[]+|[:\\]]+$", with: "", options: .regularExpression)
            .replacingOccurrences(of: "^(icon|ti|tabler|gs)[-_:]", with: "", options: .regularExpression)
            .replacingOccurrences(of: "[_\\s]+", with: "-", options: .regularExpression)
            .replacingOccurrences(of: "-+", with: "-", options: .regularExpression)
        if name.isEmpty { return "sparkles" }
        if let alias = aliases[name] { name = alias }
        if codepoints.isEmpty { return name }
        return codepoints[name] == nil ? "sparkles" : name
    }

    static func glyph(_ raw: String?) -> String {
        let name = resolve(raw)
        let value = codepoints[name] ?? codepoints["sparkles"] ?? 0xF6D7
        guard let scalar = Unicode.Scalar(value) else { return "" }
        return String(Character(scalar))
    }

    /// The reference response's semantic palette; used when the model omits tone.
    private static let referenceTones: [String: NexlyGlassTone] = [
        "sunrise": .amber,
        "book-2": .lavender,
        "device-laptop": .blue,
        "tools-kitchen-2": .green,
        "file-text": .lavender,
        "run": .pink,
        "device-gamepad-2": .blue,
        "moon": .lilac,
        "flame": .peach,
    ]

    static func tone(for name: String) -> NexlyGlassTone {
        if let semantic = referenceTones[name] { return semantic }
        var hash = 0
        for scalar in name.unicodeScalars {
            hash = hash &* 31 &+ Int(scalar.value)
        }
        let count = NexlyGlassTone.allCases.count
        let index = ((hash % count) + count) % count
        return NexlyGlassTone.allCases[index]
    }

    /// Replace known emoji with `:icon:` tokens; drop any unknown pictograph.
    static func emojiToIconTokens(_ text: String) -> String {
        var out = text
        for (emoji, icon) in emoji.sorted(by: { $0.key.count > $1.key.count }) {
            out = out.replacingOccurrences(of: emoji, with: ":\(icon):")
        }
        var filtered = String.UnicodeScalarView()
        for scalar in out.unicodeScalars {
            let value = scalar.value
            let pictograph = (0x1F000...0x1FAFF).contains(value)
                || (0x2600...0x27BF).contains(value)
                || (0xFE00...0xFE0F).contains(value)
                || value == 0x200D
                || (0x1F1E6...0x1F1FF).contains(value)
            if !pictograph { filtered.append(scalar) }
        }
        return String(filtered)
    }

    enum InlinePart {
        case text(String)
        case icon(String)
    }

    private static let tokenRegex = try! NSRegularExpression(
        pattern: ":([a-z0-9][a-z0-9-]{1,42}):"
    )

    /// Split a string on the inline `:icon:` grammar. Text outside a token is
    /// preserved byte-for-byte; unknown token text stays literal.
    static func inlineParts(_ text: String) -> [InlinePart] {
        let normalised = emojiToIconTokens(text)
        let ns = normalised as NSString
        guard !normalised.isEmpty else { return [] }
        var parts: [InlinePart] = []
        var cursor = 0
        let matches = tokenRegex.matches(
            in: normalised,
            range: NSRange(location: 0, length: ns.length)
        )
        for match in matches {
            if match.range.location > cursor {
                parts.append(.text(ns.substring(with: NSRange(
                    location: cursor,
                    length: match.range.location - cursor
                ))))
            }
            let name = ns.substring(with: match.range(at: 1))
            if codepoints.isEmpty || codepoints[name] != nil || aliases[name] != nil {
                parts.append(.icon(name))
            } else {
                parts.append(.text(ns.substring(with: match.range)))
            }
            cursor = match.range.location + match.range.length
        }
        if cursor < ns.length {
            parts.append(.text(ns.substring(from: cursor)))
        }
        return parts
    }
}

// ---------------------------------------------------------------------------
// Glass primitives
// ---------------------------------------------------------------------------

/// The reference's circular pastel glass tile with one real outline glyph.
struct NexlyGlassGlyphTile: View {
    let name: String
    var tone: NexlyGlassTone? = nil
    var size: CGFloat = 28
    var glyphSize: CGFloat = 14
    var active: Bool = false

    var body: some View {
        let palette = tone ?? NexlyIcons.tone(for: name)
        ZStack {
            Circle()
                .fill(
                    LinearGradient(
                        colors: [palette.top, palette.bottom],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            Circle()
                .fill(
                    RadialGradient(
                        colors: [Color.white.opacity(0.60), Color.white.opacity(0)],
                        center: .topLeading,
                        startRadius: 0,
                        endRadius: size * 0.72
                    )
                )
            Circle()
                .strokeBorder(Color.white.opacity(0.92), lineWidth: 1)
            Circle()
                .fill(Color.white.opacity(0.38))
                .frame(width: size * 0.22, height: size * 0.16)
                .offset(x: -size * 0.18, y: -size * 0.25)
            NexlyGlyph(name: name, size: glyphSize, color: palette.ink)
        }
        .overlay(
            Circle()
                .strokeBorder(palette.halo.opacity(active ? 0.75 : 0), lineWidth: 1.5)
                .scaleEffect(1.16)
        )
        .frame(width: size, height: size)
        .shadow(color: palette.halo.opacity(0.16), radius: 2, x: 0, y: 1)
        .accessibilityHidden(true)
    }
}

struct NexlyGlyph: View {
    let name: String
    var size: CGFloat = 16
    var color: Color = Aero.text

    var body: some View {
        Text(NexlyIcons.glyph(name))
            .font(.custom(NexlyIcons.family, size: size))
            .foregroundColor(color)
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}

/// Pale circular glass avatar beside the user's turn.
struct NexlyUserAvatar: View {
    var size: CGFloat = 32

    var body: some View {
        ZStack {
            Circle()
                .fill(
                    LinearGradient(
                        colors: [Color.white.opacity(0.62), Aero.accentSoft],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            Circle().strokeBorder(Color.white.opacity(0.78), lineWidth: 1)
            NexlyGlyph(name: "user", size: size * 0.46, color: Color.white.opacity(0.94))
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

/// Inline `:icon:` runs rendered as glyphs inside a line of text.
struct NexlyInlineText: View {
    let text: String
    var fontSize: CGFloat = 14.5
    var glyphSize: CGFloat = 17
    var color: Color = Aero.text
    var weight: Font.Weight = .medium

    var body: some View {
        let parts = NexlyIcons.inlineParts(text)
        var attributed = AttributedString()
        for part in parts {
            switch part {
            case .text(let run):
                attributed.append(AttributedString(run))
            case .icon(let name):
                var glyph = AttributedString(NexlyIcons.glyph(name))
                glyph.font = .custom(NexlyIcons.family, size: glyphSize)
                glyph.foregroundColor = color
                attributed.append(glyph)
            }
        }
        return Text(attributed)
            .font(.system(size: fontSize, weight: weight))
            .foregroundColor(color)
    }
}

// ---------------------------------------------------------------------------
// The three-stage status card
// ---------------------------------------------------------------------------

enum NexlyThinkingPhase {
    case thinking, reasoning, effort, done
}

/// Thinking / Reasoning / Effort. It is a sibling of the preamble INSIDE the
/// assistant card and stays mounted while the plan streams — not a
/// pre-first-token placeholder. The copy is the reference copy for a plan
/// turn; every other turn uses honest, task-neutral copy and the backend's
/// real effort. The active row gets a thin halo ring.
struct NexlyThinkingCard: View {
    var phase: NexlyThinkingPhase = .done
    var effort: String? = nil
    var isPlan: Bool = true
    var reasoningHint: String? = nil

    private var thinkingSubtitle: String {
        isPlan ? "Analyzing your goals and priorities..." : "Understanding your request..."
    }

    private var reasoningSubtitle: String {
        if isPlan { return "Planning the best structure and balance..." }
        if let reasoningHint, !reasoningHint.isEmpty { return reasoningHint }
        return "Working through the best approach..."
    }

    private var effortSubtitle: String {
        if isPlan { return "Creating a detailed, realistic schedule..." }
        if let effort, !effort.isEmpty {
            let label = effort.prefix(1).uppercased() + String(effort.dropFirst())
            return "\(label) effort · composing the answer..."
        }
        return "Composing a detailed answer..."
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            row(icon: "sparkles", tone: .peach,
                title: "Thinking", subtitle: thinkingSubtitle,
                active: phase == .thinking)
            row(icon: "brain", tone: .lavender,
                title: "Reasoning", subtitle: reasoningSubtitle,
                active: phase == .reasoning)
            row(icon: "settings", tone: .mint,
                title: "Effort", subtitle: effortSubtitle,
                active: phase == .effort)
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 2)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Aero.elevatedSurface.opacity(phase == .done ? 0.24 : 0.32))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(Color.white.opacity(0.58), lineWidth: 1)
        )
    }

    private func row(
        icon: String,
        tone: NexlyGlassTone,
        title: String,
        subtitle: String,
        active: Bool
    ) -> some View {
        HStack(spacing: 9) {
            NexlyGlassGlyphTile(name: icon, tone: tone, size: 28, glyphSize: 14, active: active)
            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(Aero.text)
                Text(subtitle)
                    .font(.system(size: 9))
                    .foregroundColor(Aero.textMuted)
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 0)
    }
}

// ---------------------------------------------------------------------------
// The timetable
// ---------------------------------------------------------------------------

/// One glass card, icon-led rows, time stacked over activity, a closing line
/// and the timestamp at the trailing edge. This is the surface a plan fence
/// used to lose to a raw code block on iOS.
struct NexlyPlanCard: View {
    let plan: NexlyPlanData
    var open: Bool = false
    var fallbackTimestamp: String? = nil

    private var stamp: String? {
        if let timestamp = plan.timestamp, !timestamp.isEmpty { return timestamp }
        guard let iso = fallbackTimestamp else { return nil }
        return NexlyClock.label(iso)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            NexlyInlineText(
                text: plan.title,
                fontSize: 12,
                glyphSize: 14,
                color: Aero.text,
                // The sheet's title is only a hair larger than the rows; at the
                // narrower card width a bold title wrapped where the reference
                // stays on one line.
                weight: .medium
            )
            if plan.rows.isEmpty {
                skeleton
            } else {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(plan.rows) { row in
                        HStack(alignment: .center, spacing: 10) {
                            NexlyGlassGlyphTile(
                                name: row.icon,
                                tone: row.tone,
                                size: 30,
                                glyphSize: 15
                            )
                            VStack(alignment: .leading, spacing: 2) {
                                Text(row.time)
                                    .font(.system(size: 9, weight: .regular))
                                    .foregroundColor(Aero.textTertiary)
                                    .lineLimit(1)
                                NexlyInlineText(
                                    text: row.text,
                                    fontSize: 11.5,
                                    glyphSize: 13.5,
                                    color: Aero.text,
                                    weight: .regular
                                )
                            }
                            Spacer(minLength: 0)
                        }
                    }
                }
            }
            if plan.closing != nil || stamp != nil {
                HStack(alignment: .center, spacing: 4) {
                    if let closing = plan.closing {
                        NexlyInlineText(
                            text: closing,
                            fontSize: 12,
                            glyphSize: 13.5,
                            color: Aero.text,
                            weight: .regular
                        )
                        if let closingIcon = plan.closingIcon,
                           !closing.contains(where: { $0 == ":" }) {
                            NexlyGlyph(
                                name: closingIcon,
                                size: 14,
                                color: NexlyGlassTone.peach.ink
                            )
                        }
                    }
                    Spacer(minLength: 0)
                    if let stamp {
                        Text(stamp)
                            .font(.system(size: 9, weight: .regular))
                            .foregroundColor(Aero.textTertiary)
                    }
                }
            }
        }
        .padding(.horizontal, 11)
        .padding(.vertical, 6)
        .frame(maxWidth: .infinity, alignment: .leading)
        .nexlyGlassCard(corner: 20)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(plan.title)
    }

    private var skeleton: some View {
        VStack(alignment: .leading, spacing: 11) {
            ForEach(0..<3, id: \.self) { index in
                HStack(spacing: 11) {
                    Circle()
                        .fill(Aero.surface.opacity(0.55))
                        .frame(width: 30, height: 30)
                    VStack(alignment: .leading, spacing: 5) {
                        Capsule()
                            .fill(Aero.textMuted.opacity(0.16))
                            .frame(width: 76, height: 7)
                        Capsule()
                            .fill(Aero.textMuted.opacity(0.10))
                            .frame(width: index == 1 ? 188 : 148, height: 8)
                    }
                    Spacer(minLength: 0)
                }
            }
        }
    }
}

enum NexlyClock {
    private static let formatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "h:mm a"
        return formatter
    }()

    static func label(_ iso: String) -> String? {
        guard let date = GSFormatters.date(from: iso) else { return nil }
        return formatter.string(from: date)
    }
}

// ---------------------------------------------------------------------------
// Pearlescent stage
// ---------------------------------------------------------------------------

/// The reference backdrop: warm pearl wash, four translucent bubbles and the
/// app's soft lotus in the lower-left. The wash and bubbles are drawn; the
/// lotus reuses the existing transparent asset so chat does not fork a second
/// illustration.
struct NexlyChatBackdrop: View {
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            ZStack {
                LinearGradient(
                    colors: scheme == .dark
                        ? [Color(red: 0.10, green: 0.07, blue: 0.065),
                           Color(red: 0.15, green: 0.10, blue: 0.09),
                           Color(red: 0.11, green: 0.075, blue: 0.07)]
                        : [Color(red: 1.000, green: 0.985, blue: 0.965),
                           Color(red: 0.992, green: 0.949, blue: 0.918),
                           Color(red: 1.000, green: 0.973, blue: 0.953)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                // Daylight entering from the upper-left, as in the sheet.
                RadialGradient(
                    colors: [Color.white.opacity(scheme == .dark ? 0.05 : 0.52),
                             Color.clear],
                    center: UnitPoint(x: 0.12, y: 0.02),
                    startRadius: 0,
                    endRadius: w * 0.9
                )
                bubble(size: w * 0.92, x: w * 0.96, y: h * 0.10,
                       opacity: scheme == .dark ? 0.26 : 0.95)
                bubble(size: w * 0.80, x: w * -0.03, y: h * 0.33,
                       opacity: scheme == .dark ? 0.22 : 0.82)
                bubble(size: w * 0.60, x: w * 0.99, y: h * 0.80,
                       opacity: scheme == .dark ? 0.18 : 0.72)
                bubble(size: w * 0.20, x: w * 0.78, y: h * 0.22,
                       opacity: scheme == .dark ? 0.14 : 0.55)
                Image("GSHomeLotusNew")
                    .resizable()
                    .scaledToFit()
                    .frame(width: w * 1.18)
                    .opacity(scheme == .dark ? 0.16 : 0.72)
                    .offset(x: -w * 0.34, y: h * 0.40)
                    .allowsHitTesting(false)
            }
            .frame(width: w, height: h)
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    /// A glass sphere, not a flat pastel disc: the body stays almost
    /// transparent, the colour lives in a thin refraction band, and a crisp
    /// white rim plus a specular crescent carry the read. The first render
    /// proved that a bright centre at high opacity looks like a sticker.
    private func bubble(size: CGFloat, x: CGFloat, y: CGFloat, opacity: Double) -> some View {
        Circle()
            .fill(
                RadialGradient(
                    colors: [
                        Color.white.opacity(0.16),
                        Color(red: 1.0, green: 0.84, blue: 0.76).opacity(0.34),
                        Color(red: 0.90, green: 0.85, blue: 1.0).opacity(0.24),
                        Color(red: 0.78, green: 0.91, blue: 1.0).opacity(0.12),
                        Color.clear,
                    ],
                    center: UnitPoint(x: 0.40, y: 0.40),
                    startRadius: 0,
                    endRadius: size * 0.52
                )
            )
            .overlay(
                Circle().strokeBorder(Color.white.opacity(0.10), lineWidth: 7)
            )
            .overlay(
                Circle().strokeBorder(
                    LinearGradient(
                        colors: [
                            Color.white.opacity(0.90),
                            Color(red: 1.0, green: 0.78, blue: 0.84).opacity(0.36),
                            Color(red: 0.77, green: 0.87, blue: 1.0).opacity(0.24),
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    lineWidth: 1.2
                )
            )
            .overlay(
                Circle()
                    .strokeBorder(Color.white.opacity(0.20), lineWidth: 0.8)
                    .padding(size * 0.055)
            )
            .overlay(
                Circle()
                    .trim(from: 0.60, to: 0.86)
                    .stroke(Color.white.opacity(0.85),
                            style: StrokeStyle(lineWidth: size * 0.028, lineCap: .round))
                    .padding(size * 0.10)
                    .rotationEffect(.degrees(8))
            )
            .overlay(
                Circle()
                    .fill(Color.white.opacity(0.80))
                    .frame(width: size * 0.22, height: size * 0.13)
                    .blur(radius: 2)
                    .offset(x: -size * 0.12, y: -size * 0.30)
            )
            .overlay(
                Circle()
                    .fill(Color.white.opacity(0.88))
                    .frame(width: size * 0.055, height: size * 0.055)
                    .offset(x: -size * 0.26, y: -size * 0.34)
            )
            .overlay(
                Circle().fill(
                    RadialGradient(
                        colors: [
                            Color.clear,
                            Color(red: 1.0, green: 0.80, blue: 0.88).opacity(0.20),
                        ],
                        center: .center,
                        startRadius: size * 0.24,
                        endRadius: size * 0.50
                    )
                )
            )
            .frame(width: size, height: size)
            .shadow(color: Color(red: 0.95, green: 0.68, blue: 0.74).opacity(0.20),
                    radius: 12, y: 4)
            .position(x: x, y: y)
            .opacity(opacity)
    }
}
