// ocr_wrapper.cpp — GS AI C ABI for OCR (PaddleOCR via Paddle-Lite).
//
// Status: not linked. The recognition engine needs Paddle-Lite built for the
// target, which is an NDK cross-compile (BLOCKED on this host — no NDK).
// Every entry point reports honestly rather than returning empty text, because
// an empty OCR result is indistinguishable from "the image had no text" and
// would silently poison a vision answer.
#include "ocr_wrapper.h"

#include <cstdlib>
#include <cstring>
#include <new>
#include <string>

namespace {
thread_local std::string o_err;
void set_err(const std::string& m) { o_err = m; }
} // namespace

struct ocr_context {
    ocr_config_t cfg{};
    bool available = false;
    std::string backend_name = "none";
};

extern "C" {

int32_t ocr_validate_config(const ocr_config_t* config) {
    if (!config) { set_err("config is null"); return GS_ERR_INVALID_ARG; }
    if (!config->det_model || !*config->det_model) {
        set_err("det_model is required");
        return GS_ERR_INVALID_ARG;
    }
    if (!config->rec_model || !*config->rec_model) {
        set_err("rec_model is required");
        return GS_ERR_INVALID_ARG;
    }
    return GS_OK;
}

ocr_context_t* ocr_create(const ocr_config_t* config) {
    if (ocr_validate_config(config) != GS_OK) return nullptr;
    ocr_context_t* c = new (std::nothrow) ocr_context();
    if (!c) { set_err("out of memory"); return nullptr; }
    c->available    = false;
    c->backend_name = "paddleocr:not-compiled";
    set_err("Paddle-Lite not linked: OCR BLOCKED, requires NDK cross-compile");
    return c;
}

char* ocr_recognize(ocr_context_t* ctx, const char* image_path) {
    if (!ctx || !image_path || !*image_path) {
        set_err("context and image_path are required");
        return nullptr;
    }
    if (!ctx->available) {
        set_err(std::string("ocr backend unavailable: ") + ctx->backend_name);
        return nullptr;
    }
    set_err("ocr_recognize: not implemented");
    return nullptr;
}

void ocr_free_string(char* s) { if (s) std::free(s); }

int32_t ocr_available(ocr_context_t* ctx) { return ctx && ctx->available ? 1 : 0; }

const char* ocr_backend_name(ocr_context_t* ctx) {
    return ctx ? ctx->backend_name.c_str() : "none";
}

void ocr_free(ocr_context_t* ctx) { delete ctx; }

} // extern "C"
