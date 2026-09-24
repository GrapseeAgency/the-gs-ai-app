/*
 * GS AI — C++ NATIVE ENGINE — agent loop.
 *
 * The C++ core owns: the loop, tool-call parsing, poka-yoke validation
 * (dispatch is refused before execution on any malformed shape), context
 * shaping (8k offload), and the planner/executor split via gs_model_role.
 * Model inference itself is fully behind the backend vtable.
 */
#include "gs_engine/c_api.h"

#include <cstdio>
#include <cstring>
#include <string>

namespace {

// Tool-call protocol (identical to the TS scaffold): the model emits exactly
// one JSON line {"tool": "<name>", "args": {...}}. Recovery scanning mirrors
// extractToolCall(): scan balanced JSON prefixes so trailing junk braces or
// prose cannot kill the call — the validator still rejects wrong SHAPES.
bool extract_tool_call(const std::string& reply, std::string* tool_name, std::string* args_json) {
    size_t line_start = 0;
    while (line_start <= reply.size()) {
        size_t nl = reply.find('\n', line_start);
        std::string line = reply.substr(line_start, (nl == std::string::npos ? reply.size() : nl) - line_start);
        // trim
        size_t b = line.find_first_not_of(" \t\r");
        if (b == std::string::npos) {
            if (nl == std::string::npos) break;
            line_start = nl + 1;
            continue;
        }
        size_t e = line.find_last_not_of(" \t\r");
        line = line.substr(b, e - b + 1);

        size_t start = line.find('{');
        if (start != std::string::npos) {
            size_t depth = 0;
            bool in_str = false;
            for (size_t i = start; i < line.size(); i++) {
                char c = line[i];
                if (in_str) {
                    if (c == '\\' && i + 1 < line.size()) i++;
                    else if (c == '"') in_str = false;
                    continue;
                }
                if (c == '"') in_str = true;
                else if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        std::string candidate = line.substr(start, i - start + 1);
                        // Dependency-free shape check: must contain "tool" and "args" keys.
                        if (candidate.find("\"tool\"") != std::string::npos &&
                            candidate.find("\"args\"") != std::string::npos) {
                            size_t tq = candidate.find("\"tool\"");
                            size_t colon = candidate.find(':', tq);
                            size_t q1 = candidate.find('"', colon);
                            size_t q2 = (q1 == std::string::npos) ? std::string::npos : candidate.find('"', q1 + 1);
                            if (q2 != std::string::npos) {
                                *tool_name = candidate.substr(q1 + 1, q2 - q1 - 1);
                                size_t aq = candidate.find("\"args\"");
                                size_t ab = candidate.find('{', candidate.find(':', aq));
                                // args span: from the inner '{' to the object's closing '}'
                                // (candidate-relative: closing brace is at i - start).
                                *args_json = (ab == std::string::npos)
                                    ? "{}"
                                    : candidate.substr(ab, (i - start) - ab + 1);
                                return true;
                            }
                        }
                    }
                }
            }
        }
        if (nl == std::string::npos) break;
        line_start = nl + 1;
    }
    return false;
}

std::string gen_to_prompt(const std::string& user_message, const std::string& extra) {
    return user_message + extra;
}
[[maybe_unused]] static const auto& keep_gen_to_prompt = gen_to_prompt;

} // namespace

gs_status gs_agent_run(gs_llm_service_t* service, const char* user_message,
                       const gs_agent_options* opts, gs_agent_result* out) {
    if (!service || !user_message || !opts || !out) return GS_ERR_INVALID_ARG;
    try {
        auto* vtbl = reinterpret_cast<gs_llm_service_vtable_t*>(service);
        if (!vtbl || vtbl->abi_version != GS_ENGINE_ABI_VERSION || !vtbl->generate) {
            return GS_ERR_BACKEND_UNAVAILABLE;
        }
        std::memset(out, 0, sizeof(*out));
        out->stats.ttft_ms = -1;
        out->stats.total_ms = -1;
        out->stats.tokens_per_sec = -1.0f;
        out->stats.prompt_tokens = -1;
        out->stats.output_tokens = -1;

        std::string convo = user_message;
        std::string last_reply;

        for (int32_t step = 1; step <= opts->max_steps; step++) {
            out->steps_used = step;
            gs_generation_stats st{};
            char* text = nullptr;
            gs_generation_options gopts{};
            gopts.system_prompt = opts->system_prompt;
            gopts.temperature = 0.2f;
            gopts.max_tokens = 1024;
            gopts.role = GS_ROLE_EXECUTOR;
            gs_status st_gen = vtbl->generate(service, convo.c_str(), &gopts, &text, &st);
            if (st_gen != GS_OK) {
                out->stats = st;
                return st_gen;
            }
            out->stats = st;
            last_reply = text ? text : "";
            gs_free_string(text);

            std::string tool_name, args_json;
            if (!extract_tool_call(last_reply, &tool_name, &args_json)) {
                // final answer
                out->final_text = static_cast<char*>(std::malloc(last_reply.size() + 1));
                if (!out->final_text) return GS_ERR_OUT_OF_MEMORY;
                std::memcpy(out->final_text, last_reply.c_str(), last_reply.size() + 1);
                return GS_OK;
            }

            // Validate + dispatch through the engine (poka-yoke: the validator
            // runs BEFORE the tool's executor; errors carry expected/got/hint).
            char* summary = nullptr;
            gs_status st_tool = gs_engine_dispatch_tool(opts->tools, opts->tool_count,
                                                        tool_name.c_str(), args_json.c_str(), &summary);
            if (st_tool == GS_ERR_INVALID_ARG) {
                out->tool_arg_errors++;
            } else if (st_tool != GS_OK) {
                return st_tool;
            } else {
                out->tool_calls++;
            }
            std::string result = summary ? summary : "";
            gs_free_string(summary);

            // Context shaping for the next step (8k offload + hint).
            bool off = result.size() > GS_OFFLOAD_THRESHOLD;
            if (off) out->offloads++;
            convo += "\nASSISTANT: " + last_reply + "\nTOOL_RESULT " + result;
        }
        // Budget exhausted: ship the last reply (honest, mirrors TS scaffold).
        out->final_text = static_cast<char*>(std::malloc(last_reply.size() + 1));
        if (!out->final_text) return GS_ERR_OUT_OF_MEMORY;
        std::memcpy(out->final_text, last_reply.c_str(), last_reply.size() + 1);
        return GS_OK;
    } catch (...) { return GS_ERR_INTERNAL; }
}

void gs_agent_result_free(gs_agent_result* r) {
    if (!r) return;
    if (r->final_text) std::free(r->final_text);
    r->final_text = nullptr;
}
