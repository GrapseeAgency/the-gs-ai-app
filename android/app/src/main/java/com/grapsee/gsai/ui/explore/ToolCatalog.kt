package com.grapsee.gsai.ui.explore

/**
 * WHAT A TOOL ACTUALLY IS.
 *
 * Every card in Explore used to jump straight into a conversation with a
 * pre-written prompt. That is a real action, but it answers a question the
 * reader has not asked yet: they tapped "Image Studio" to find out what it
 * does, and the app replied with a paragraph already in the composer.
 *
 * This catalogue is the missing middle. For each tool: what it is for, the
 * three moves it makes, and three openings short enough to read before
 * choosing one. The page around it is [ToolDetailScreen]; the examples start
 * the conversation, so the page never becomes a wall between the reader and
 * the thing they came for.
 *
 * The Discover mock's four cards are aliases, not separate tools: "Code
 * Helper" and "Code Assistant" are the same capability with different copy,
 * and giving them separate pages would drift the two mocks apart.
 */
data class ToolDetail(
    /** Two sentences on what the tool is for — not a feature list. */
    val blurb: String,
    /** The three moves it makes, in the order it makes them. */
    val steps: List<String>,
    /** Openings the reader can start from without writing their own. */
    val examples: List<String>,
    /** Tool ids that work well after this one. */
    val pairs: List<String>,
)

object ToolCatalog {

    /** The Discover cards point at the canonical tool behind them. */
    private val aliases = mapOf(
        "discover-image-studio" to "image-studio",
        "discover-voice" to "voice-assistant",
        "discover-writing" to "writing-assistant",
        "discover-code" to "code-assistant",
    )

    fun canonicalId(id: String): String = aliases[id] ?: id

    /** The card that was tapped, whichever catalogue it came from. */
    fun tool(id: String): ExploreTool? {
        val canonical = canonicalId(id)
        return ExploreCatalog.tools.firstOrNull { it.id == canonical }
            ?: ExploreCatalog.discoverFeatured.firstOrNull { it.id == id }
    }

    fun detail(id: String): ToolDetail? = details[canonicalId(id)]

    /** Related tools that exist in this build, in the order they were listed. */
    fun pairs(id: String): List<ExploreTool> =
        (detail(id)?.pairs ?: emptyList())
            .mapNotNull { tool(it) }
            .filter { it.id != canonicalId(id) }
            .distinctBy { it.id }

    private val details: Map<String, ToolDetail> = mapOf(
        "image-studio" to ToolDetail(
            blurb = "A working session with a studio director. It asks for the subject, the " +
                "mood and the light before it writes anything, then hands back three prompts " +
                "precise enough to paste into an image generator — and says which one it would " +
                "pick, and why.",
            steps = listOf(
                "Three questions first: subject, mood, and the light you want.",
                "Three prompts at different levels of abstraction, not three synonyms.",
                "Ask for a rewrite of the one you like — same idea, different era or lens.",
            ),
            examples = listOf(
                "A quiet photo of a bookshop in the rain, warm window light",
                "Product shot of a ceramic mug on linen, soft morning sun",
                "An illustrated poster for a night market, two colours only",
            ),
            pairs = listOf("logo-maker", "video-generator"),
        ),
        "video-generator" to ToolDetail(
            blurb = "Plans a short video before a single frame exists: who watches it, what they " +
                "should feel, and what happens in each shot. What comes back is a shot list with " +
                "narration and on-screen text — not a render, and it never pretends to be one.",
            steps = listOf(
                "Say what the video is for and who is watching.",
                "Get a shot-by-shot outline with rough timings.",
                "Take the list to any editor, or ask for a tighter cut.",
            ),
            examples = listOf(
                "A 30-second launch teaser for a note-taking app",
                "A one-minute explainer for a community bike-repair workshop",
                "A six-shot product demo for a coffee subscription",
            ),
            pairs = listOf("image-studio", "writing-assistant"),
        ),
        "voice-assistant" to ToolDetail(
            blurb = "A speaking coach that works in text. You write what you want to say, it " +
                "tightens the line without flattening it, then tells you where you would run out " +
                "of breath or lose the room.",
            steps = listOf(
                "Write the introduction or the pitch the way you would say it.",
                "Get a tightened version that keeps your words.",
                "Ask for delivery notes: pace, pauses, which word carries the sentence.",
            ),
            examples = listOf(
                "Tighten my 40-second conference introduction",
                "Make this toast sound spoken, not written",
                "Give me three ways to open a podcast episode",
            ),
            pairs = listOf("writing-assistant", "study-plan"),
        ),
        "code-assistant" to ToolDetail(
            blurb = "A pair programmer that explains before it edits. It asks what you are " +
                "building and in which language, then works in small steps you can follow — " +
                "rather than dumping a rewrite you have to reverse-engineer.",
            steps = listOf(
                "Describe the bug or the feature, and the stack.",
                "Work through one change at a time.",
                "Ask why. Every fix arrives with its reason attached.",
            ),
            examples = listOf(
                "Why does my Compose list lose scroll position on rotation?",
                "Add retry with backoff to this Kotlin HTTP call",
                "Explain what this Swift closure is capturing",
            ),
            pairs = listOf("doc-summary", "writing-assistant"),
        ),
        "writing-assistant" to ToolDetail(
            blurb = "An editor that keeps your voice. It fixes grammar and flow, then lists what " +
                "it changed and why — so the next draft needs less of it. Nothing is rewritten " +
                "into a house style you did not ask for.",
            steps = listOf(
                "Paste the text you want edited.",
                "Say how heavy the edit should be: light touch or full restructure.",
                "Read the change list, not only the result.",
            ),
            examples = listOf(
                "Tighten this email without making it cold",
                "Make this paragraph plainer for a general reader",
                "Cut 20% from this product description",
            ),
            pairs = listOf("doc-summary", "voice-assistant"),
        ),
        "study-plan" to ToolDetail(
            blurb = "A plan built around your real week, not an ideal one. It asks for the " +
                "subject, the deadline and the hours you can actually give, then schedules " +
                "review sessions you will keep rather than a syllabus you will abandon.",
            steps = listOf(
                "Give the subject, the deadline and the weekly hours.",
                "Get a day-by-day plan with one review session per week.",
                "Ask it to re-plan when a week goes wrong — that is expected, not failure.",
            ),
            examples = listOf(
                "Six weeks of Spanish, five hours a week",
                "Pass a statistics exam in three weeks",
                "Learn enough Rust to read a codebase in a month",
            ),
            pairs = listOf("doc-summary", "code-assistant"),
        ),
        "logo-maker" to ToolDetail(
            blurb = "Turns a name and a feeling into three concepts you can brief someone with. " +
                "Each one is described precisely enough — shape, weight, colour, what to avoid — " +
                "that a designer or a generator gets it right the first time.",
            steps = listOf(
                "Say the name, the feeling, and where the mark will live.",
                "Get three concepts, each with shape, weight and colour.",
                "Ask for a tighter version of the one you like.",
            ),
            examples = listOf(
                "A calm logo for a sleep app called Hush",
                "A bold mark for a youth football club",
                "Something hand-drawn for a bakery named after my grandmother",
            ),
            pairs = listOf("image-studio", "writing-assistant"),
        ),
        "trip-planner" to ToolDetail(
            blurb = "An itinerary that assumes you will be tired. Each day gets one highlight, " +
                "one meal worth the walk and one rest stop — and the plan says what to drop when " +
                "the day runs late, which is the part most itineraries leave out.",
            steps = listOf(
                "Say where, when, and how fast you want to move.",
                "Get a day-by-day plan with one anchor per day.",
                "Ask for the rainy-day version before you need it.",
            ),
            examples = listOf(
                "Four days in Lisbon in November, slow pace",
                "A weekend in Kyoto with one temple a day",
                "Ten days in Vietnam with two teenagers",
            ),
            pairs = listOf("doc-summary", "study-plan"),
        ),
        "doc-summary" to ToolDetail(
            blurb = "Reads a document the way a careful colleague would: five bullets, the " +
                "numbers that matter, and the three questions you should ask before signing " +
                "anything. It quotes the document rather than paraphrasing away the risk.",
            steps = listOf(
                "Paste the text or attach the file.",
                "Get a five-bullet summary and the key figures.",
                "Ask for the questions the document avoids.",
            ),
            examples = listOf(
                "Summarise this tenancy agreement",
                "Pull the numbers out of this quarterly report",
                "What is this contract asking me to agree to?",
            ),
            pairs = listOf("writing-assistant", "study-plan"),
        ),
    )
}
