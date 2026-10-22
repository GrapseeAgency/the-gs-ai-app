import SwiftUI

/**
 * Home's real destinations — the iOS twin of the prompt table in Android's
 * `HomeDashboardScreen.kt`.
 *
 * The reference's cards are four different doors, not four copies of "blank
 * chat". Every string here is byte-identical to the Android constant it
 * mirrors, so the same tap on either platform puts the same words in the
 * composer. Nothing decorative: each prompt is a real dispatch into a new
 * conversation.
 */
enum HomeDispatch {

    /// The "Create" pill and the Video card: an image/video-making request.
    static let createPrompt =
        "Help me create something visual. Ask what I want to make (image or short video), the mood, and the subject, then build it with me."

    /// The Mind Guide card.
    static let mindGuidePrompt =
        "Be my clarity companion. Ask me what's on my mind, then help me find the one next step."

    /// The Today's reflection card.
    static let reflectionPrompt =
        "Give me a short daily reflection prompt and sit with my answer without judgement."

    // MARK: Capability-driven suggestions

    struct Suggestion: Identifiable, Equatable {
        /// The onboarding capability this maps to; nil means "always offered".
        let capability: String
        let title: String
        let prompt: String
        var id: String { title }
    }

    /// Every suggestion maps to a capability the setup wizard actually offered,
    /// so a chosen tool changes what Home puts in front of the user.
    static let suggestions: [Suggestion] = [
        Suggestion(capability: "Draft and write",
                   title: "Draft something in my voice",
                   prompt: "Draft a short, warm note I can send to a colleague. Ask me for the details you need first."),
        Suggestion(capability: "Research the web",
                   title: "Research a question properly",
                   prompt: "I want to understand a topic properly. Ask me what it is, then give me a sourced briefing with the strongest counterpoints."),
        Suggestion(capability: "Analyse images",
                   title: "Read an image or screenshot",
                   prompt: "I'd like to analyse an image or screenshot. Tell me what you can read from it and ask me to attach the file."),
        Suggestion(capability: "Write and fix code",
                   title: "Fix or review some code",
                   prompt: "Help me fix or review some code. Ask for the snippet, the language, and the error you're seeing."),
        Suggestion(capability: "Summarise documents",
                   title: "Summarise a long document",
                   prompt: "Summarise a long document for me. Ask me to attach it, then give me the argument, the evidence, and what to do next."),
        // Android words this skill "Think it through"; the iOS wizard offers
        // "Brainstorm ideas". Either label selects the same prompt.
        Suggestion(capability: "Think it through",
                   title: "Think a decision through",
                   prompt: "Help me think a decision through. Ask me the one question that matters most, then lay out the options and the trade-offs."),
    ]

    /// Draft / Research / Think / Code — the set shown before setup is taken.
    static let defaultSuggestions: [Suggestion] = [suggestions[0], suggestions[1], suggestions[5], suggestions[3]]

    /// The iOS wizard's alias for the "Think it through" skill.
    private static let brainstormAlias = "Brainstorm ideas"

    /// The suggestions the wizard actually selected, falling back to the
    /// default set when nothing was pinned. Same rule as Android.
    static func selectedSuggestions(for capabilities: Set<String>) -> [Suggestion] {
        let chosen = suggestions.filter {
            capabilities.contains($0.capability)
                || ($0.capability == "Think it through" && capabilities.contains(brainstormAlias))
        }
        return chosen.isEmpty ? defaultSuggestions : chosen
    }
}
