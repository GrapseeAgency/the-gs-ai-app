package com.grapsee.gsai.data.model

/**
 * Static model catalogue for the Model Centre — the "brain picker".
 * speedTier is one of: fast | balanced | deep.
 * Replaced by the remote model registry later; UI reads this object only.
 */
data class ModelInfo(
    val id: String,
    val displayName: String,
    val tagline: String,
    val capabilities: List<String>,
    val contextK: Int,
    val speedTier: String,
    val modes: List<String>,
    val isDefault: Boolean = false
)

object ModelCatalog {
    val all = listOf(
        ModelInfo(
            id = "gs-swift",
            displayName = "GS Swift",
            tagline = "Instant answers",
            capabilities = listOf("text"),
            contextK = 32,
            speedTier = "fast",
            modes = listOf("Fast")
        ),
        ModelInfo(
            id = "gs-balanced",
            displayName = "GS Balanced",
            tagline = "Everyday intelligence",
            capabilities = listOf("text", "tools"),
            contextK = 128,
            speedTier = "balanced",
            modes = listOf("Fast", "Balanced"),
            isDefault = true
        ),
        ModelInfo(
            id = "gs-deep",
            displayName = "GS Deep",
            tagline = "Extended reasoning",
            capabilities = listOf("text", "tools", "reasoning"),
            contextK = 200,
            speedTier = "deep",
            modes = listOf("Deep reasoning")
        ),
        ModelInfo(
            id = "gs-research",
            displayName = "GS Research",
            tagline = "Multi-source synthesis",
            capabilities = listOf("text", "tools", "reasoning"),
            contextK = 200,
            speedTier = "deep",
            modes = listOf("Research")
        ),
        ModelInfo(
            id = "gs-coder",
            displayName = "GS Coder",
            tagline = "Code generation & review",
            capabilities = listOf("text", "tools"),
            contextK = 128,
            speedTier = "balanced",
            modes = listOf("Coding")
        ),
        ModelInfo(
            id = "gs-creative",
            displayName = "GS Creative",
            tagline = "Writing & ideation",
            capabilities = listOf("text"),
            contextK = 128,
            speedTier = "balanced",
            modes = listOf("Creative")
        ),
        ModelInfo(
            id = "gs-vision",
            displayName = "GS Vision",
            tagline = "Images, charts, OCR",
            capabilities = listOf("text", "vision"),
            contextK = 128,
            speedTier = "balanced",
            modes = listOf("Vision")
        ),
        ModelInfo(
            id = "gs-voice",
            displayName = "GS Voice",
            tagline = "Real-time voice",
            capabilities = listOf("text", "voice"),
            contextK = 64,
            speedTier = "fast",
            modes = listOf("Voice")
        )
    )

    fun byId(id: String): ModelInfo? = all.firstOrNull { it.id == id }

    val default: ModelInfo
        get() = all.first { it.isDefault }
}
