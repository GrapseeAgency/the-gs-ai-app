package com.grapsee.gsai.data

/**
 * The four models the Model Selection reference draws, and the rule that makes
 * its two controls work together.
 *
 * HONESTY BOUNDARY: the server decides which model actually answers a message
 * (`src/app/api/v1/conversations/[id]/route.ts` ignores a client model). What
 * the app genuinely controls is the wire mode — `flash | thinking | auto` — and
 * that is what this file resolves. The model name is the app's stated
 * preference; the mode is what the next reply does.
 *
 * The reference draws Auto Select ON and Medium highlighted at the same time.
 * That is exactly the contract below: with Auto Select ON, the model is picked
 * per message and the effort follows THAT model's default; with it OFF, the
 * effort control is the authority. Both controls therefore have a reader, and
 * neither silently overwrites the other.
 */
enum class GsModel(
    val id: String,
    val title: String,
    /** Effort this model runs at when Auto Select chooses it. */
    val defaultEffort: String,
    /** Capability bucket the reference's All/Text/Vision/Code tabs filter on. */
    val capabilities: Set<String>,
) {
    PRO(
        id = "pro",
        title = "Nexly Pro",
        defaultEffort = "standard",
        capabilities = setOf("text", "vision", "code"),
    ),
    LITE(
        id = "lite",
        title = "Nexly Lite",
        defaultEffort = "low",
        capabilities = setOf("text"),
    ),
    VISION(
        id = "vision",
        title = "Nexly Vision",
        defaultEffort = "standard",
        capabilities = setOf("vision"),
    ),
    CODE(
        id = "code",
        title = "Nexly Code",
        defaultEffort = "high",
        capabilities = setOf("code"),
    );

    companion object {
        /** Stored ids are stable; an unknown id falls back to Pro instead of
         *  leaving the page with no selection. */
        fun byId(id: String?): GsModel = entries.firstOrNull { it.id == id } ?: PRO
    }
}

object ModelRouter {

    /** Signals that a message wants the code model. Deliberately narrow: a
     *  fenced block, a stack trace, or a handful of programming words. A false
     *  positive only means "answer with the deeper model". */
    private val CODE_HINTS = listOf(
        "```", "stack trace", "traceback", "compile", "kotlin", "swift",
        "python", "javascript", "typescript", "gradle", "sql", "regex",
        "function(", "nullpointer", "exception",
    )

    /**
     * Which model Auto Select would use for this message.
     *
     * Order matters: an image outranks everything (only Vision can read it), a
     * code signal outranks length, and a long message is treated as complex
     * enough for Pro. Otherwise the stored preference wins, so the reader's
     * choice is still felt on ordinary traffic.
     */
    fun pick(text: String, hasImage: Boolean, preferred: GsModel): GsModel {
        if (hasImage) return GsModel.VISION
        val lower = text.lowercase()
        if (CODE_HINTS.any { lower.contains(it) }) return GsModel.CODE
        if (text.length >= 220) return GsModel.PRO
        return preferred
    }

    /** The three-mode wire value for an effort tier. `minimal` and `low` are
     *  both `flash` because the wire cannot tell them apart; the tile still
     *  remembers which one was tapped. */
    fun modeFor(effort: String): String = when (effort) {
        "minimal", "low", "light" -> "flash"
        "high" -> "thinking"
        else -> "auto"
    }

    /**
     * The mode the next send should use.
     *
     * Auto Select ON  -> the picked model's default effort decides (the model
     *                    was chosen FOR this message, so its budget follows it).
     * Auto Select OFF -> the effort the reader set in the control decides.
     */
    fun modeForMessage(
        text: String,
        hasImage: Boolean,
        preferred: GsModel,
        effort: String,
        autoSelect: Boolean,
    ): String {
        if (!autoSelect) return modeFor(effort)
        val model = pick(text, hasImage, preferred)
        return modeFor(model.defaultEffort)
    }
}
