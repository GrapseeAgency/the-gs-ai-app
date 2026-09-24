/*
 * GS AI — Kotlin bridge over the GS C++ native engine (Phase F, step 5).
 *
 * Thin JNI layer: NO business logic lives here. The C++ core owns the agent
 * loop, tool dispatch, context management, verification hooks and routing;
 * this bridge only maps JVM types onto the C ABI (gs_engine/c_api.h).
 *
 * WIRING (next step, documented in native/README.md): add to
 * android/app/build.gradle:
 *   externalNativeBuild { cmake { path "../../native/CMakeLists.txt" } }
 * The CMakeLists links libgs_engine.a + the chosen backend plugin and builds
 * libgs_engine.so for the device ABIs. This file intentionally does NOT get
 * compiled by the app's release pipeline until that backend wiring lands —
 * a bridge with no engine .so would break assembleDebug for zero benefit.
 */
package dev.grapsee.gs.nativeengine

/** Status codes mirror gs_status in c_api.h — keep in lockstep. */
enum class GsStatus(val code: Int) {
    OK(0),
    INVALID_ARG(1),
    BACKEND_UNAVAILABLE(2),
    MODEL_NOT_LOADED(3),
    GENERATION_FAILED(4),
    CANCELLED(5),
    OUT_OF_MEMORY(6),
    INTERNAL(7);

    val isBlocked: Boolean
        get() = this == BACKEND_UNAVAILABLE || this == MODEL_NOT_LOADED
}

/** Mirrors gs_generation_stats. Negative values mean "backend cannot count" —
 *  never coerce to 0 (that would fabricate numbers; rule 5). */
data class GsGenerationStats(
    val ttftMs: Int,
    val totalMs: Int,
    val tokensPerSec: Float,
    val promptTokens: Int,
    val outputTokens: Int,
)

/** Mirrors gs_agent_result. */
data class GsAgentResult(
    val finalText: String,
    val stepsUsed: Int,
    val toolCalls: Int,
    val toolArgErrors: Int,
    val offloads: Int,
    val stats: GsGenerationStats,
)

/**
 * Tool bridge: the engine validates argument SHAPES before calling execute
 * (poka-yoke — see native/src/plugin_registry.cpp validate_tool_args), so
 * execute() only ever sees schema-conforming JSON.
 */
fun interface GsToolExecutor {
    fun execute(argsJson: String): String
}

class GsTool(val name: String, val argsSchema: String, val executor: GsToolExecutor)

object GsNativeEngine {
    init {
        // The .so is produced by the CMake wiring in native/README.md; until
        // that lands this bridge stays dormant on device (honest, not fatal).
        System.loadLibrary("gs_engine")
    }

    /** @param profile planner|executor — the C++ registry picks the backend per role. */
    external fun createService(profile: String): Long
    external fun loadModel(service: Long, modelPath: String): Int
    external fun runAgent(
        service: Long,
        userMessage: String,
        systemPrompt: String?,
        maxSteps: Int,
        toolNames: Array<String>,
        toolSchemas: Array<String>,
    ): GsAgentResult
    external fun freeService(service: Long)

    /**
     * Measurement contract (reported per run by the app):
     *  - TTFT: stats.ttftMs (backend-reported; -1 = NOT MEASURED, never 0)
     *  - tok/s: stats.tokensPerSec (-1 = NOT MEASURED)
     *  - battery per 100 inferences: device instrumentation (BlockBench-style);
     *    NOT measurable from this bridge — collected via the device audit run.
     *  - offline capability: airplane-mode audit on the device; the engine is
     *    fully on-device so it must hold, but the claim ships only with the
     *    audit artifact.
     */
}
