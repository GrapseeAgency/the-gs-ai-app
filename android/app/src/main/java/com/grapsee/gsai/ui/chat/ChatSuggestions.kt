package com.grapsee.gsai.ui.chat

import androidx.compose.ui.graphics.vector.ImageVector
import com.grapsee.gsai.ui.icons.GsIcons

/**
 * The four doors into an empty chat.
 *
 * A suggestion that ignores what the reader told setup is a decoration. Each
 * card here carries the capabilities and interests it serves, and
 * [chatSuggestionsFor] picks the four that overlap the saved answers, falling
 * back to breadth — prose, numbers, a plan, research — when setup was skipped.
 *
 * The scoring is a pure function on purpose: it is the part that can be wrong,
 * so it is the part with a unit test.
 */
internal data class ChatSuggestion(
    val icon: ImageVector,
    val label: String,
    val prompt: String,
    val capabilities: Set<String> = emptySet(),
    val interests: Set<String> = emptySet(),
)

internal val chatSuggestionPool: List<ChatSuggestion> = listOf(
    ChatSuggestion(
        icon = GsIcons.PenLine,
        label = "Draft something",
        prompt = "Draft a warm, concise note to a client apologising for a two-day delay and offering a revised date.",
        capabilities = setOf("Draft and write"),
        interests = setOf("Writing", "Business", "Marketing")
    ),
    ChatSuggestion(
        icon = GsIcons.Gauge,
        label = "See your numbers",
        prompt = "Show a bar chart of monthly revenue for a small bakery from January to June, with a short read on the trend.",
        capabilities = setOf("Analyse images"),
        interests = setOf("Data", "Business", "Science")
    ),
    ChatSuggestion(
        icon = GsIcons.ClipboardList,
        label = "Plan the week",
        prompt = "Plan a focused five-day week for a solo founder launching a small product, with one outcome per day.",
        capabilities = setOf("Think it through"),
        interests = setOf("Productivity", "Business")
    ),
    ChatSuggestion(
        icon = GsIcons.Compass,
        label = "Go deeper",
        prompt = "Research the current trade-offs of solid-state batteries versus lithium-ion, and summarise them with a comparison table.",
        capabilities = setOf("Research the web"),
        interests = setOf("Research", "Science", "Education")
    ),
    ChatSuggestion(
        icon = GsIcons.Code,
        label = "Fix my code",
        prompt = "Review this Kotlin function for edge cases, then rewrite it with a test that fails without the fix: fun average(values: List<Int>) = values.sum() / values.size",
        capabilities = setOf("Write and fix code"),
        interests = setOf("Coding")
    ),
    ChatSuggestion(
        icon = GsIcons.FileText,
        label = "Summarise a document",
        prompt = "Summarise a long report into five decisions, three risks and one next step a busy reader can act on today.",
        capabilities = setOf("Summarise documents"),
        interests = setOf("Education", "Research", "Productivity")
    ),
    ChatSuggestion(
        icon = GsIcons.Palette,
        label = "Sketch a layout",
        prompt = "Sketch a calm one-page layout for a small studio's portfolio, with a clear reading order and one accent colour.",
        interests = setOf("Design")
    ),
    ChatSuggestion(
        icon = GsIcons.Languages,
        label = "Learn a phrase",
        prompt = "Teach me ten useful Portuguese phrases for a first visit to Lisbon, with pronunciation and when to use each one.",
        interests = setOf("Language", "Travel")
    ),
    ChatSuggestion(
        icon = GsIcons.GraduationCap,
        label = "Explain it simply",
        prompt = "Explain how a transformer model works to a curious fifteen-year-old, using one everyday analogy and no jargon.",
        interests = setOf("Education", "Science", "Coding")
    ),
    ChatSuggestion(
        icon = GsIcons.Brain,
        label = "Find the through-line",
        prompt = "Help me name the through-line in three projects I care about, then suggest one small bet that connects them.",
        capabilities = setOf("Think it through"),
        interests = setOf("Business", "Marketing", "Productivity")
    ),
    ChatSuggestion(
        icon = GsIcons.Presentation,
        label = "Pitch it",
        prompt = "Turn this idea into a five-slide pitch: a planning app for freelancers that keeps one outcome visible each day.",
        interests = setOf("Marketing", "Business", "Productivity")
    ),
    ChatSuggestion(
        icon = GsIcons.Camera,
        label = "Read an image",
        prompt = "I will paste a screenshot of a dashboard. Read the numbers, point out what changed, and tell me what to look at first.",
        capabilities = setOf("Analyse images")
    )
)

/** The no-setup fallback: one card per kind of answer the product is good at. */
internal val chatSuggestionDefaults: List<ChatSuggestion> = chatSuggestionPool.take(4)

/**
 * Ranked by overlap, then by pool order (breadth first). Always four cards so
 * the 2x2 empty-state grid keeps its shape.
 */
internal fun chatSuggestionsFor(
    capabilities: Set<String>,
    interests: Set<String>,
): List<ChatSuggestion> {
    if (capabilities.isEmpty() && interests.isEmpty()) return chatSuggestionDefaults
    val ranked = chatSuggestionPool
        .map { suggestion ->
            suggestion to (
                suggestion.capabilities.count { it in capabilities } * 3 +
                    suggestion.interests.count { it in interests } * 2
                )
        }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .map { it.first }
    return (ranked + chatSuggestionPool)
        .distinctBy { it.prompt }
        .take(4)
}
