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

    /** One sentence per axis, or null when both are at their defaults. */
    fun directive(): String? {
        val tone = OnboardingProfileStore.personality
        val detail = OnboardingProfileStore.responseStyle
        if (tone == "Friendly" && detail == "Standard") return null
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
        return "Reader preference (does not override facts or structure): $toneLine $detailLine"
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
