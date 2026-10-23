package com.grapsee.gsai.ui.explore

import androidx.compose.ui.graphics.Color

/**
 * Explore AI — the capability catalog.
 *
 * Every entry is a real door into chat: [prompt] seeds a live conversation
 * through `GsRoutes.chat(null, prompt)` the same way Home's quick pills do.
 * Nothing here is decorative — a card that cannot start a real session has
 * no place on this page (the "logically complete, not only by design" rule).
 *
 * Tones are sampled from the Explore reference (pixel-measured, not guessed):
 * pastel circle fills with saturated glyph strokes on top.
 */
object ExploreTones {
    // Pastel circle fills (reference-measured).
    val PinkBg = Color(0xFFFBD7E7)
    val LavenderBg = Color(0xFFDED8FD)
    val GreenBg = Color(0xFFCAEBD4)
    val ApricotBg = Color(0xFFFEDFC5)
    val BlueBg = Color(0xFFD6E6FC)

    // Saturated glyph strokes on those fills (reference-measured).
    val PinkFg = Color(0xFFC43366)
    val LavenderFg = Color(0xFF5A50D6)
    val GreenFg = Color(0xFF177A50)
    val ApricotFg = Color(0xFFB85C12)
    val BlueFg = Color(0xFF1663C4)

    // Accent family: the peach the chips/CTA/send read as (reference-measured).
    val Peach = Color(0xFFFEB47D)
    val PeachDeep = Color(0xFFFEC493)
    val PeachPale = Color(0xFFFEDFC5)
    val CtaTop = Color(0xFFFFE9DC)
    val CtaBottom = Color(0xFFFED3C0)

    // The Discover chip (pink-tinted glass, rose ink).
    val DiscoverBg = Color(0xFFFBE6F5)
    val DiscoverInk = Color(0xFFC0447E)

    // Ink family (reference-measured: slate-navy on pearl).
    val InkStrong = Color(0xFF181C30)
    val InkHeader = Color(0xFF2B2C37)
    val InkBody = Color(0xFF4A4B55)
    val InkMuted = Color(0xFF8D8D95)
}

/** One discoverable capability. */
data class ExploreTool(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: String,
    val tone: ExploreTone,
    val category: ExploreCategory,
    /** Seeds the conversation this card opens — never a dead end. */
    val prompt: String,
)

enum class ExploreCategory(val label: String) {
    All("All"),
    Productivity("Productivity"),
    Creativity("Creativity"),
    Learning("Learning"),
    Lifestyle("Lifestyle"),
}

data class ExploreTone(val bg: Color, val fg: Color)

/** The discoverable catalog. Order = prominence on the page. */
object ExploreCatalog {
    val tools = listOf(
        ExploreTool(
            id = "image-studio",
            title = "Image Studio",
            subtitle = "Create stunning images with AI",
            icon = "photo",
            tone = ExploreTone(ExploreTones.PinkBg, ExploreTones.PinkFg),
            category = ExploreCategory.Creativity,
            prompt = "Act as my image studio director. Ask me the subject, mood and style one question at a time, then write three detailed image prompts I can paste into any image generator.",
        ),
        ExploreTool(
            id = "video-generator",
            title = "Video Generator",
            subtitle = "Turn ideas into videos",
            icon = "player-play",
            tone = ExploreTone(ExploreTones.LavenderBg, ExploreTones.LavenderFg),
            category = ExploreCategory.Creativity,
            prompt = "Help me plan a short video. Ask what it is for and who watches it, then give me a shot-by-shot outline with narration lines and on-screen text for each scene.",
        ),
        ExploreTool(
            id = "voice-assistant",
            title = "Voice Assistant",
            subtitle = "Natural, human-like voices",
            icon = "microphone",
            tone = ExploreTone(ExploreTones.GreenBg, ExploreTones.GreenFg),
            category = ExploreCategory.Productivity,
            prompt = "Be my speaking coach. We'll practise a short introduction out loud: I'll type what I want to say, you tighten it, then give me feedback on clarity and tone.",
        ),
        ExploreTool(
            id = "code-assistant",
            title = "Code Assistant",
            subtitle = "Write, debug, and build faster",
            icon = "code",
            tone = ExploreTone(ExploreTones.ApricotBg, ExploreTones.ApricotFg),
            category = ExploreCategory.Productivity,
            prompt = "Be my pair programmer. Ask what I'm building and which language, then help me write and debug it step by step, explaining every fix.",
        ),
        ExploreTool(
            id = "writing-assistant",
            title = "Writing Assistant",
            subtitle = "Craft, improve and perfect your text",
            icon = "file-text",
            tone = ExploreTone(ExploreTones.BlueBg, ExploreTones.BlueFg),
            category = ExploreCategory.Productivity,
            prompt = "Be my editor. I'll paste text; you keep my voice, fix grammar and flow, and show me a short list of what you changed and why.",
        ),
        ExploreTool(
            id = "study-plan",
            title = "Create a study plan",
            subtitle = "Personalized learning plan for your goals",
            icon = "school",
            tone = ExploreTone(ExploreTones.LavenderBg, ExploreTones.LavenderFg),
            category = ExploreCategory.Learning,
            prompt = "Build me a personalized study plan. Ask my subject, deadline and weekly hours first, then give a day-by-day plan with one review session per week.",
        ),
        ExploreTool(
            id = "logo-maker",
            title = "Generate a logo",
            subtitle = "Turn your idea into a unique logo",
            icon = "brush",
            tone = ExploreTone(ExploreTones.ApricotBg, ExploreTones.ApricotFg),
            category = ExploreCategory.Creativity,
            prompt = "Help me design a logo. Ask about the name, feeling and where it will live, then propose three concepts described precisely enough to brief a designer or generator.",
        ),
        ExploreTool(
            id = "trip-planner",
            title = "Plan a trip",
            subtitle = "Get a custom travel itinerary",
            icon = "plane",
            tone = ExploreTone(ExploreTones.GreenBg, ExploreTones.GreenFg),
            category = ExploreCategory.Lifestyle,
            prompt = "Plan a trip with me. Ask the destination, dates and pace, then build a day-by-day itinerary with one highlight, one meal and one rest stop per day.",
        ),
        ExploreTool(
            id = "doc-summary",
            title = "Summarize a document",
            subtitle = "Save time with AI summaries",
            icon = "clipboard-text",
            tone = ExploreTone(ExploreTones.BlueBg, ExploreTones.BlueFg),
            category = ExploreCategory.Learning,
            prompt = "Summarize documents for me. I'll paste or attach one; give a five-bullet summary, the key numbers, and three questions I should ask about it.",
        ),
        ExploreTool(
            id = "meal-planner",
            title = "Weekly meal planner",
            subtitle = "Eat well without the guesswork",
            icon = "salad",
            tone = ExploreTone(ExploreTones.GreenBg, ExploreTones.GreenFg),
            category = ExploreCategory.Lifestyle,
            prompt = "Be my meal planner. Ask dietary needs and cooking time per day, then write a seven-day plan with one shared grocery list.",
        ),
    )

    /** Horizontal collections (reference: Design & Visuals / Work & Productivity / Learning & Growth). */
    data class Collection(
        val title: String,
        val subtitle: String,
        val category: ExploreCategory,
    )

    val collections = listOf(
        Collection("Design & Visuals", "For creators & dreamers", ExploreCategory.Creativity),
        Collection("Work & Productivity", "Work smarter, not harder", ExploreCategory.Productivity),
        Collection("Learning & Growth", "Build new skills", ExploreCategory.Learning),
    )

    /** The featured journey card (reference: Weekend in Kyoto). */
    val featured = ExploreTool(
        id = "kyoto-weekend",
        title = "Weekend in Kyoto",
        subtitle = "A creative journey through Japan's timeless beauty",
        icon = "sparkles",
        tone = ExploreTone(ExploreTones.ApricotBg, ExploreTones.ApricotFg),
        category = ExploreCategory.Lifestyle,
        prompt = "Plan a creative weekend in Kyoto. Ask my pace and interests, then give me one day per page: morning temple light, one craft workshop, one quiet meal, and one place locals actually go.",
    )

    fun byCategory(category: ExploreCategory): List<ExploreTool> =
        if (category == ExploreCategory.All) tools else tools.filter { it.category == category }

    fun search(query: String): List<ExploreTool> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return tools
        return tools.filter {
            it.title.lowercase().contains(q) ||
                it.subtitle.lowercase().contains(q) ||
                it.category.label.lowercase().contains(q)
        }
    }
}
