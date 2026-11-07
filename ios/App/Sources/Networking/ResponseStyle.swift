import Foundation

/// HOW NEXLY ANSWERS — iOS twin of `data/ResponseStyle.kt`.
///
/// The setup wizard has been writing `personality` and `responseStyle` since it
/// shipped, and nothing has ever read them. Profile printed both values with no
/// chevron and no editor: a preference the app could show, could not change and
/// would not honour.
///
/// This closes the loop at the only place it can be closed — the turn's
/// non-visible system context, which already carries the plan checklist. The
/// directive is phrased as an instruction to the model, not as a label, because
/// "Friendly" means nothing to a model and "warm, plain-spoken, no flattery"
/// does.
///
/// It is a PREFERENCE, not a persona: facts, sources and structure are
/// untouched. Ask for a table at any detail level and you get a table.
enum ResponseStyle {

    static let tones = ["Professional", "Friendly", "Playful"]
    static let details = ["Concise", "Standard", "Detailed"]

    static func toneBlurb(_ tone: String) -> String {
        switch tone {
        case "Professional": return "Plain, neutral, no slang. The register of a good colleague."
        case "Playful": return "Light where it costs nothing. Never jokey about a serious answer."
        default: return "Warm and direct. Contractions, no flattery, no filler."
        }
    }

    static func detailBlurb(_ detail: String) -> String {
        switch detail {
        case "Concise": return "The answer, then stop. No preamble and no recap."
        case "Detailed": return "Show the working, the alternatives and the trade-offs."
        default: return "The answer, the reasoning that matters, and the next step."
        }
    }

    private static let nexlyUIContract =
        "Nexly UI rendering contract (mandatory): " +
        "1) ZERO EMOJIS: Never output raw Unicode emojis anywhere in prose, headings, lists, tables, or widgets. " +
        "Use `:icon-name:` tokens (e.g. `:sparkles:`, `:rocket:`, `:bulb:`, `:bolt:`, `:check:`, `:alert-triangle:`, " +
        "`:chart-bar:`, `:calculator:`, `:code:`, `:flame:`, `:shield-check:`, `:calendar:`, `:clock:`, `:coin:`, " +
        "`:compass:`, `:book:`, `:cpu:`, `:world:`, `:target:`, `:heart:`, `:star:`) at the start of section headings and callouts. " +
        "2) SPONTANEOUS INTERACTIVE WIDGETS: Whenever a topic involves numbers, budgets, loans, pricing, comparisons, " +
        "schedules, processes, study concepts, or choices — even when the user did not explicitly ask for a widget — " +
        "delight the user by appending a logical interactive fenced widget block (` ```calc `, ` ```chart `, ` ```stat `, " +
        "` ```compare `, ` ```timeline `, ` ```quiz `, ` ```flashcards `, ` ```checklist `, ` ```steps `, ` ```poll `, " +
        "` ```sim `, ` ```stage3d `) with real domain values and mathematical formulas."

    /// Rendering contract plus one sentence per preference axis.
    ///
    /// Reads the two UserDefaults keys directly rather than through
    /// `OnboardingProfileStore` (a @MainActor ObservableObject): this directive
    /// travels with the turn's send path, which builds context off the main
    /// actor, and UserDefaults is the thread-safe source of the same values
    /// the store caches.
    static var directive: String? {
        let defaults = UserDefaults.standard
        let tone = defaults.string(forKey: "gs.profile.personality") ?? "Friendly"
        let detail = defaults.string(forKey: "gs.profile.responseStyle") ?? "Standard"
        if tone == "Friendly" && detail == "Standard" { return nexlyUIContract }
        let toneLine: String
        switch tone {
        case "Professional":
            toneLine = "Use a professional register: plain, neutral, no slang or exclamation marks."
        case "Playful":
            toneLine = "Use a light, playful register where it costs nothing — never about a serious answer."
        default:
            toneLine = "Use a warm, direct register: contractions, no flattery, no filler."
        }
        let detailLine: String
        switch detail {
        case "Concise":
            detailLine = "Keep it short: the answer first, then stop. No preamble and no recap."
        case "Detailed":
            detailLine = "Be thorough: show the working, name the alternatives and the trade-offs."
        default:
            detailLine = "Standard depth: the answer, the reasoning that matters, one next step."
        }
        return "\(nexlyUIContract)\nReader preference (does not override facts or structure): \(toneLine) \(detailLine)"
    }

    /// The one-line summary Profile shows under its row.
    @MainActor
    static var summary: String {
        "\(OnboardingProfileStore.shared.personality) · \(OnboardingProfileStore.shared.responseStyle)"
    }

    @MainActor
    static func setTone(_ value: String) {
        let store = OnboardingProfileStore.shared
        store.save(interests: store.interests, capabilities: store.capabilities,
                   responseStyle: store.responseStyle, personality: value)
    }

    @MainActor
    static func setDetail(_ value: String) {
        let store = OnboardingProfileStore.shared
        store.save(interests: store.interests, capabilities: store.capabilities,
                   responseStyle: value, personality: store.personality)
    }
}
