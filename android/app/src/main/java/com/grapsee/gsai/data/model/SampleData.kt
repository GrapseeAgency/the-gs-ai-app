package com.grapsee.gsai.data.model

/**
 * Static UI sample data — the curated assistant catalogue shown in the
 * Assistants marketplace. Replaced by the repository layer (Room + Ktor)
 * when the first data feature slices land.
 *
 * PHASE 2: the fabricated notifications list (8 invented events incl. a
 * fake "Maya shared a chat") is deleted — NotificationsScreen renders the
 * real (currently empty) store with an honest empty state instead, and the
 * invented marketplace metrics on the samples ("12.4k uses" / 4.8★) are
 * neutralised: the fields survive only as JSON-schema placeholders that no
 * surface renders.
 */

data class AssistantSample(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val instructions: String,
    val starters: List<String>,
    // PHASE 2 honesty: never rendered anywhere (the ★/uses line was purged);
    // kept only because AssistantsStore persists them for schema stability.
    val uses: String,
    val rating: Double,
    val published: Boolean = false,
    // User-created assistants carry the Create form's capability picks;
    // samples omit it (default empty).
    val capabilities: List<String> = emptyList(),
    // Workspace organization — pin floats a card to the top of My assistants,
    // archive parks it in the Archived tab. Samples default both false.
    val pinned: Boolean = false,
    val archived: Boolean = false
)

object SampleData {

    val assistants = listOf(
        AssistantSample(
            id = "asst-1",
            name = "Writing Coach",
            category = "Writing",
            description = "A patient editor that sharpens clarity, tone and structure without flattening your voice. Built for essays, newsletters and long-form drafts.",
            instructions = "Act as a warm, precise writing coach. Give feedback in three passes — structure, clarity, then style. Always quote the sentence you are improving, explain why the change helps the reader, and keep the author's voice intact.",
            starters = listOf(
                "Tighten my opening paragraph",
                "Rewrite this email so it sounds warmer",
                "Suggest five headline options"
            ),
            uses = "",
            rating = 0.0,
            published = true
        ),
        AssistantSample(
            id = "asst-2",
            name = "Code Reviewer",
            category = "Engineering",
            description = "Reviews diffs like a senior engineer — correctness, edge cases, naming and tests — with concise, actionable comments.",
            instructions = "Review code like a pragmatic senior engineer. Prioritise correctness and edge cases, then readability and naming, then performance. Keep comments short and actionable, and always suggest a concrete fix or a unit test.",
            starters = listOf(
                "Review this Kotlin function",
                "What edge cases am I missing?",
                "Write unit tests for this class"
            ),
            uses = "",
            rating = 0.0,
            published = true
        ),
        AssistantSample(
            id = "asst-3",
            name = "Research Analyst",
            category = "Research",
            description = "Synthesises multi-source findings into structured briefs with clear assumptions, confidence levels and open questions.",
            instructions = "Behave as a research analyst. Structure every answer as: summary, key findings, sources and confidence, open questions. Flag uncertainty explicitly and never present a single source as consensus.",
            starters = listOf(
                "Brief me on the EU AI Act",
                "Compare these two vendors",
                "Draft a research plan for entering Japan"
            ),
            uses = "",
            rating = 0.0
        ),
        AssistantSample(
            id = "asst-4",
            name = "Language Tutor",
            category = "Education",
            description = "Conversation-first language practice with gentle corrections, level tracking and natural, everyday dialogue.",
            instructions = "Run a relaxed conversation in the learner's target language at their CEFR level. Correct mistakes inline in brackets, explain the rule only when asked, and end each session with three phrases to practise.",
            starters = listOf(
                "Practise Spanish at B1 level",
                "Correct my past-tense story",
                "Teach me ten kitchen words in Japanese"
            ),
            uses = "",
            rating = 0.0
        ),
        AssistantSample(
            id = "asst-5",
            name = "Meeting Summariser",
            category = "Productivity",
            description = "Turns messy transcripts into decisions, owners and next steps — nothing lost, no fluff.",
            instructions = "Convert transcripts into: decisions made, action items with owners and dates, open questions, and a two-line executive summary. Quote timestamps for anything contentious.",
            starters = listOf(
                "Summarise this transcript",
                "Extract action items and owners",
                "Draft the follow-up email"
            ),
            uses = "",
            rating = 0.0
        ),
        AssistantSample(
            id = "asst-6",
            name = "Brainstorm Partner",
            category = "Creativity",
            description = "A fast-thinking partner for divergent ideation: ten angles first, then pressure-tests the strongest three.",
            instructions = "Brainstorm in two phases: first generate ten distinct ideas without judging them, then pick the three with the most potential and stress-test risks, effort and originality. Keep energy high and jargon low.",
            starters = listOf(
                "Brainstorm names for a podcast",
                "Ten features for a habit tracker",
                "Poke holes in my idea"
            ),
            uses = "",
            rating = 0.0
        ),
        AssistantSample(
            id = "asst-7",
            name = "Data Cruncher",
            category = "Analytics",
            description = "Explains datasets in plain English — trends, outliers and the next analysis worth running.",
            instructions = "Interpret data like a friendly analyst: describe the shape of the data, call out trends and outliers with numbers, state limitations, then suggest the single next analysis that would be most informative.",
            starters = listOf(
                "Interpret this CSV summary",
                "Why did churn spike in March?",
                "Which chart fits this data?"
            ),
            uses = "",
            rating = 0.0
        ),
        AssistantSample(
            id = "asst-8",
            name = "Brand Strategist",
            category = "Business",
            description = "Positioning, naming and voice guidelines grounded in audience insight and competitor gaps.",
            instructions = "Think like a brand strategist. Anchor every recommendation in a specific audience insight, name the competitor contrast, and deliver outputs as a positioning statement, three proof points and a tone-of-voice rule.",
            starters = listOf(
                "Draft a positioning statement",
                "Audit our tone of voice",
                "Name this product line"
            ),
            uses = "",
            rating = 0.0
        )
    )
}
