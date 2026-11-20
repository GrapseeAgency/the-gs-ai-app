package com.grapsee.gsai.data

/**
 * The Android-facing mapping from Model Selection preferences to the
 * server-supported thinking mode.
 *
 * Model capability routing stays on the GS server, where it has the complete
 * message and attachment context. Auto sends the server's `auto` mode; when
 * Auto is off, the reader's effort choice selects the wire mode. A client-side
 * keyword/length guess must not pretend to choose the provider model because
 * the API deliberately ignores client model ids.
 */
object ModelRouter {

    /** The three-mode wire value for an effort tier. */
    fun modeFor(effort: String): String = when (effort) {
        "minimal", "low", "light" -> "flash"
        "high" -> "thinking"
        else -> "auto"
    }

    /**
     * Auto ON leaves capability/model routing to the server for this actual
     * message. Auto OFF sends the explicitly selected effort mode.
     */
    fun modeForMessage(
        effort: String,
        autoSelect: Boolean,
    ): String = if (autoSelect) "auto" else modeFor(effort)
}
