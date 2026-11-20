package com.grapsee.gsai.data

/**
 * The four bot identities offered in Chat settings. The stable IDs are shared
 * with iOS so a settings export or migration can preserve the same choice.
 */
enum class AssistantAvatarStyle(val id: String, val label: String) {
    CLOVER("clover", "Clover"),
    FLOWER("flower", "Flower"),
    TRIANGLE("triangle", "Triangle"),
    SQUARE("square", "Square");

    companion object {
        fun fromId(id: String?): AssistantAvatarStyle =
            entries.firstOrNull { it.id == id } ?: CLOVER
    }
}
