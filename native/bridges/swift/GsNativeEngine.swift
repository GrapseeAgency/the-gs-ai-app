/*
 * GS AI — Swift bridge over the GS C++ native engine (Phase F, step 6).
 *
 * Swift consumes the C ABI directly (no JNI layer needed): the header
 * native/include/gs_engine/c_api.h is exposed to Swift via a module map —
 * the FFI rule (no C++ types across the boundary) makes the C header the
 * entire surface. This file adds ergonomics only.
 *
 * WIRING (documented in native/README.md): ship c_api.h + libgs_engine.a in
 * an XCFramework target; import via:
 *   #import "gs_engine/c_api.h"   (bridging header)
 * or a modulemap.modulemap with `header "gs_engine/c_api.h"`.
 * Not wired into the iOS app target until a backend plugin ships for device —
 * same reasoning as the Kotlin bridge (no engine .so, no compile).
 */

import Foundation

public enum GsStatus: Int32, Error {
    case ok = 0
    case invalidArg = 1
    case backendUnavailable = 2
    case modelNotLoaded = 3
    case generationFailed = 4
    case cancelled = 5
    case outOfMemory = 6
    case internalError = 7

    public var isBlocked: Bool {
        self == .backendUnavailable || self == .modelNotLoaded
    }
}

public struct GsGenerationStats: Sendable {
    /// Backend-reported first-token latency in ms. -1 = NOT MEASURED (never 0).
    public var ttftMs: Int32
    public var totalMs: Int32
    /// Backend-reported generation rate. -1 = NOT MEASURED.
    public var tokensPerSec: Float
    public var promptTokens: Int32
    public var outputTokens: Int32
}

public struct GsAgentResult: Sendable {
    public var finalText: String
    public var stepsUsed: Int32
    public var toolCalls: Int32
    public var toolArgErrors: Int32
    public var offloads: Int32
    public var stats: GsGenerationStats
}

public struct GsTool {
    public var name: String
    public var argsSchema: String
    /// Called only after the C++ engine's poka-yoke validation passed.
    public var execute: @Sendable (String) -> String

    public init(name: String, argsSchema: String, execute: @escaping @Sendable (String) -> String) {
        self.name = name
        self.argsSchema = argsSchema
        self.execute = execute
    }
}

public enum GsNativeEngine {
    /// Planner/executor split (LEVER 4): the C++ registry may select a
    /// different backend per role.
    public static func runAgent(
        service: OpaquePointer,
        userMessage: String,
        systemPrompt: String?,
        maxSteps: Int32,
        tools: [GsTool]
    ) throws -> GsAgentResult {
        // The bridge adapts Swift closures onto gs_tool_t executor pointers
        // with a retain/release trampoline; the full adaptation ships with the
        // XCFramework wiring (it is pure glue, zero engine logic).
        fatalError("wired with the XCFramework target — see native/README.md")
    }
}
