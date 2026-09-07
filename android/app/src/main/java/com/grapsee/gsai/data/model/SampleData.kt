package com.grapsee.gsai.data.model

/**
 * Static UI sample data — pure presentation seeds for Assistants and
 * Notifications screens. Replaced by the repository layer (Room + Ktor)
 * when the first data feature slices land.
 */

data class AssistantSample(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val instructions: String,
    val starters: List<String>,
    val uses: String,
    val rating: Double,
    val published: Boolean = false,
    // User-created assistants carry the Create form's capability picks;
    // samples omit it (default empty).
    val capabilities: List<String> = emptyList()
)

/** type is one of: task | file | assistant | share | project | system | security */
data class NotificationSample(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val time: String,
    val unread: Boolean
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
            uses = "12.4k",
            rating = 4.8,
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
            uses = "9.1k",
            rating = 4.7,
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
            uses = "7.8k",
            rating = 4.6
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
            uses = "11.2k",
            rating = 4.9
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
            uses = "8.6k",
            rating = 4.7
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
            uses = "6.3k",
            rating = 4.5
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
            uses = "5.4k",
            rating = 4.6
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
            uses = "4.9k",
            rating = 4.4
        )
    )

    val notifications = listOf(
        NotificationSample(
            id = "ntf-1",
            type = "task",
            title = "Voice memo transcribed",
            body = "interview-draft.m4a is ready — 48 min, 12 speakers detected.",
            time = "2m ago",
            unread = true
        ),
        NotificationSample(
            id = "ntf-2",
            type = "assistant",
            title = "Code Reviewer updated",
            body = "New capability added: repository-wide diff review.",
            time = "1h ago",
            unread = true
        ),
        NotificationSample(
            id = "ntf-3",
            type = "share",
            title = "Maya shared a chat",
            body = "\u201CQ3 pricing experiment\u201D — you now have view access.",
            time = "3h ago",
            unread = true
        ),
        NotificationSample(
            id = "ntf-4",
            type = "file",
            title = "Report summarised",
            body = "annual-review.pdf condensed into 9 key findings.",
            time = "Yesterday",
            unread = false
        ),
        NotificationSample(
            id = "ntf-5",
            type = "project",
            title = "Aurora launch",
            body = "Priya uploaded brand-guidelines-v2.fig to the project.",
            time = "Yesterday",
            unread = false
        ),
        NotificationSample(
            id = "ntf-6",
            type = "system",
            title = "Scheduled maintenance",
            body = "GS AI will be briefly unavailable on Sunday, 02:00–02:30 UTC.",
            time = "2d ago",
            unread = false
        ),
        NotificationSample(
            id = "ntf-7",
            type = "security",
            title = "New sign-in detected",
            body = "Pixel 9 Pro · London, UK. Was this you?",
            time = "3d ago",
            unread = false
        ),
        NotificationSample(
            id = "ntf-8",
            type = "task",
            title = "Batch export finished",
            body = "42 conversations exported to JSON in your downloads.",
            time = "4d ago",
            unread = false
        )
    )
}
