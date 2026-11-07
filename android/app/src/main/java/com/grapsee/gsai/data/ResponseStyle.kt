package com.grapsee.gsai.data

/**
 * HOW NEXLY ANSWERS — the reader's tone and detail level, made real.
 *
 * The setup wizard has been writing `personality` and `responseStyle` since it
 * shipped, and nothing has ever read them. The store's own comment said so: "a
 * contract comment nobody can execute is not a contract". Profile printed both
 * values with no chevron and no editor, so the app showed a preference it could
 * not change and would not honour.
 *
 * This object closes the loop at the only place it can be closed — the turn's
 * non-visible system context, which already carries the plan checklist. The
 * directive is one sentence per axis, phrased as an instruction to the model
 * rather than as a label, because "Friendly" on its own means nothing to a
 * model and "warm, plain-spoken, no flattery" does.
 *
 * It is a PREFERENCE, not a persona: the facts, the sources and the structure
 * of an answer are untouched. If the reader asks for a table, they get a table
 * at every detail level.
 */
object ResponseStyle {

    val tones = listOf("Professional", "Friendly", "Playful")
    val details = listOf("Concise", "Standard", "Detailed")

    val toneBlurbs = mapOf(
        "Professional" to "Plain, neutral, no slang. The register of a good colleague.",
        "Friendly" to "Warm and direct. Contractions, no flattery, no filler.",
        "Playful" to "Light where it costs nothing. Never jokey about a serious answer.",
    )

    val detailBlurbs = mapOf(
        "Concise" to "The answer, then stop. No preamble and no recap.",
        "Standard" to "The answer, the reasoning that matters, and the next step.",
        "Detailed" to "Show the working, the alternatives and the trade-offs.",
    )

    private const val NEXLY_UI_CONTRACT =
        "Nexly UI rendering contract (mandatory): " +
        "1) ZERO EMOJIS: Never output raw Unicode emojis anywhere in prose, headings, lists, tables, or widgets. " +
        "Use `:icon-name:` tokens (e.g. `:sparkles:`, `:rocket:`, `:bulb:`, `:bolt:`, `:check:`, `:alert-triangle:`, " +
        "`:chart-bar:`, `:calculator:`, `:code:`, `:flame:`, `:shield-check:`, `:calendar:`, `:clock:`, `:coin:`, " +
        "`:compass:`, `:book:`, `:cpu:`, `:world:`, `:target:`, `:heart:`, `:star:`) at the start of section headings and callouts. " +
        "2) SPONTANEOUS INTERACTIVE WIDGETS: Whenever a topic involves numbers, budgets, loans, pricing, comparisons, " +
        "schedules, processes, study concepts, or choices — even when the user did not explicitly ask for a widget — " +
        "delight the user by appending a logical interactive fenced widget block (` ```calc `, ` ```chart `, ` ```stat `, " +
        "` ```compare `, ` ```timeline `, ` ```quiz `, ` ```flashcards `, ` ```checklist `, ` ```steps `, ` ```poll `, " +
        "` ```sim `, ` ```stage3d `) with real domain values and mathematical formulas. " +
        "Choose one genuinely useful interaction, not an unrelated decoration. If required values are missing, ask for them or label editable assumptions explicitly. " +
        "For calc widgets, define EVERY expression variable in fields. Numeric fields accept direct entry; min/max are optional and must have a meaningful source. " +
        "format percent takes ratios at every magnitude (1.2 means 120%); for percentage-point results use unit % with format number. Never guess from magnitude."

    /** Rendering contract plus one sentence per preference axis. */
    fun directive(): String? {
        val tone = OnboardingProfileStore.personality
        val detail = OnboardingProfileStore.responseStyle
        if (tone == "Friendly" && detail == "Standard") return NEXLY_UI_CONTRACT
        val toneLine = when (tone) {
            "Professional" -> "Use a professional register: plain, neutral, no slang or exclamation marks."
            "Playful" -> "Use a light, playful register where it costs nothing — never about a serious answer."
            else -> "Use a warm, direct register: contractions, no flattery, no filler."
        }
        val detailLine = when (detail) {
            "Concise" -> "Keep it short: the answer first, then stop. No preamble and no recap."
            "Detailed" -> "Be thorough: show the working, name the alternatives and the trade-offs."
            else -> "Standard depth: the answer, the reasoning that matters, one next step."
        }
        return "$NEXLY_UI_CONTRACT\nReader preference (does not override facts or structure): $toneLine $detailLine"
    }

    /** The one-line summary Profile shows under its row. */
    fun summary(): String = "${OnboardingProfileStore.personality} · ${OnboardingProfileStore.responseStyle}"

    fun setTone(value: String) = apply(
        OnboardingProfileStore.interests,
        OnboardingProfileStore.capabilities,
        OnboardingProfileStore.responseStyle,
        value,
    )

    fun setDetail(value: String) = apply(
        OnboardingProfileStore.interests,
        OnboardingProfileStore.capabilities,
        value,
        OnboardingProfileStore.personality,
    )

    private fun apply(
        interests: Collection<String>,
        capabilities: Collection<String>,
        detail: String,
        tone: String,
    ) = OnboardingProfileStore.save(interests, capabilities, detail, tone)
}
