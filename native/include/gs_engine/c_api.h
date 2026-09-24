/*
 * GS AI — C++ NATIVE ENGINE (Phase F) — C API surface.
 *
 * RunAnywhere-pattern layering:
 *   KERNELS/GRAPH  live inside each backend plugin (Cactus: hand-written ARM
 *                  NEON; llama.cpp: Metal/Vulkan) — this header only exposes
 *                  the ENGINE layer: OpenAI-compatible generation + tool
 *                  calling + agent loop + context management + routing.
 *
 * FFI RULE (hard): no C++ RTTI and no C++ exceptions cross this boundary.
 * Every entry point is extern "C", catches everything internally, and
 * returns a gs_status code. Opaque handles only. Compatible with Swift,
 * JNI, and Dart FFI consumers without any C++ runtime coupling.
 */
#ifndef GS_ENGINE_C_API_H
#define GS_ENGINE_C_API_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define GS_ENGINE_ABI_VERSION 1u

typedef enum gs_status {
    GS_OK = 0,
    GS_ERR_INVALID_ARG = 1,
    GS_ERR_BACKEND_UNAVAILABLE = 2,
    GS_ERR_MODEL_NOT_LOADED = 3,
    GS_ERR_GENERATION_FAILED = 4,
    GS_ERR_CANCELLED = 5,
    GS_ERR_OUT_OF_MEMORY = 6,
    GS_ERR_INTERNAL = 7,
} gs_status;

/** Role for gs_agent_run planning/execution split (LEVER 4 port). */
typedef enum gs_model_role {
    GS_ROLE_EXECUTOR = 0,
    GS_ROLE_PLANNER = 1,
} gs_model_role;

typedef struct gs_generation_options {
    const char* system_prompt;  /* nullable */
    float temperature;          /* 0.0..2.0 */
    int32_t max_tokens;
    gs_model_role role;         /* planner|executor — registry picks per-role plugin */
} gs_generation_options;

typedef struct gs_generation_stats {
    int32_t ttft_ms;            /* first token latency (backend-reported) */
    int32_t total_ms;
    float tokens_per_sec;       /* generation rate (backend-reported) */
    int32_t prompt_tokens;      /* -1 when the backend cannot count */
    int32_t output_tokens;      /* -1 when the backend cannot count */
} gs_generation_stats;

/** Streaming callback. Returns nonzero to cancel. */
typedef int (*gs_stream_callback)(const char* token, void* user);

/* Service contract: the handle's FIRST MEMBER is the gs_llm_service_vtable_t
 * BY VALUE (not a pointer). Consumers recover it with
 * reinterpret_cast<gs_llm_service_vtable_t*>(handle). */
typedef struct gs_llm_service gs_llm_service_t;

/*
 * Backend vtable — each backend plugin ships exactly one of these.
 * The registry returns the highest-priority plugin serving the requested
 * primitive; backends can be swapped without touching app code.
 */
typedef struct gs_llm_service_vtable {
    uint32_t abi_version;       /* must equal GS_ENGINE_ABI_VERSION */
    const char* backend_id;     /* "cactus", "llamacpp", "mock", ... */
    gs_status (*load_model)(gs_llm_service_t* self, const char* model_path);
    gs_status (*generate)(gs_llm_service_t* self, const char* prompt,
                          const gs_generation_options* opts,
                          /* out, callee-allocated, freed by gs_free_string */
                          char** out_text, gs_generation_stats* out_stats);
    gs_status (*generate_stream)(gs_llm_service_t* self, const char* prompt,
                                 const gs_generation_options* opts,
                                 gs_stream_callback cb, void* user,
                                 gs_generation_stats* out_stats);
    void (*cancel)(gs_llm_service_t* self);
    void (*destroy)(gs_llm_service_t* self);
} gs_llm_service_vtable_t;

/* ---- plugin registry ---------------------------------------------------- */

typedef struct gs_plugin_entry {
    const char* backend_id;
    int32_t priority;           /* higher wins when several serve a primitive */
    /* "generate" | "generate_stream" | "load_model" — primitives a backend serves */
    const char* serves;         /* comma-separated primitive names */
    gs_llm_service_t* (*create)(void);
} gs_plugin_entry_t;

gs_status gs_registry_register(const gs_plugin_entry_t* entry);
int32_t   gs_registry_count(void);
/** Select the highest-priority registered plugin that serves `primitive`. */
gs_status gs_registry_select(const char* primitive, gs_plugin_entry_t* out);
void      gs_registry_clear(void);

/* ---- engine entry points ------------------------------------------------- */

/** Create a service instance from the best registered backend for `role`. */
gs_status       gs_engine_create_service(gs_model_role role, gs_llm_service_t** out);
const char*     gs_engine_last_backend(gs_llm_service_t* service);
void            gs_free_string(char* p);
void            gs_free_service(gs_llm_service_t* service);

/* ---- tool dispatch (ACI — LEVER 1 port) ---------------------------------- */

typedef struct gs_tool gs_tool_t;

/*
 * A tool receives an argument string ALREADY validated by the engine's
 * poka-yoke validator (typed enums, ranges, whitelists). The engine validates
 * BEFORE dispatch; the tool never sees malformed shapes. Returns a
 * callee-allocated summary string (offloaded if > GS_OFFLOAD_THRESHOLD).
 */
typedef gs_status (*gs_tool_execute)(gs_tool_t* self, const char* args_json,
                                     /* out, freed by gs_free_string */ char** out_summary);

struct gs_tool {
    const char* name;
    const char* args_schema;    /* JSON-schema-ish, surfaced verbatim to the model */
    gs_tool_execute execute;
    void* user;
};

/*
 * Validate + dispatch one tool call. Structured error strings (WHAT was
 * wrong, expected vs got) are returned in out_summary with GS_ERR_INVALID_ARG.
 */
gs_status gs_engine_dispatch_tool(const gs_tool_t* tools, int32_t tool_count,
                                  const char* tool_name, const char* args_json,
                                  char** out_summary);

/* ---- context management (LEVER 3 port) ----------------------------------- */

#define GS_OFFLOAD_THRESHOLD 8000
#define GS_OFFLOAD_PREVIEW   500

/*
 * Context pipeline: per-tool summarization + 8000-char offload with
 * agent-aware truncation hints. Returns a malloc'd string the caller frees
 * with gs_free_string. `has_subagents` selects the recovery hint shape.
 */
gs_status gs_engine_context_shape(const char* tool_output, int has_subagents,
                                  /* out, freed by gs_free_string */ char** out_shaped);

/* ---- agent loop ----------------------------------------------------------- */

typedef struct gs_agent_result {
    char* final_text;           /* gs_free_string */
    int32_t steps_used;
    int32_t tool_calls;
    int32_t tool_arg_errors;
    int32_t offloads;
    gs_generation_stats stats;
} gs_agent_result;

typedef struct gs_agent_options {
    int32_t max_steps;          /* ACI iteration budget (production: 6) */
    const gs_tool_t* tools;
    int32_t tool_count;
    const char* system_prompt;
} gs_agent_options;

/*
 * Runs the agent loop inside the C++ core: generate → parse tool call →
 * validate → dispatch → feed result → repeat, up to max_steps. `prompt_json`
 * is the user turn (plain text). The service handles plan/executor roles
 * internally via gs_generation_options.role.
 */
gs_status gs_agent_run(gs_llm_service_t* service, const char* user_message,
                       const gs_agent_options* opts, gs_agent_result* out);

void gs_agent_result_free(gs_agent_result* r);

#ifdef __cplusplus
} /* extern "C" */
#endif
#endif /* GS_ENGINE_C_API_H */
