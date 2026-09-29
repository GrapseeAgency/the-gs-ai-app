// gs_mobile.cpp — portable mobile surface.
//
// Compiles with a C++17 compiler and libm. No ONNX Runtime, no llama.cpp, no
// Tesseract unless GS_MOBILE_HAVE_LLAMA is defined, which only the desktop
// build does.
//
// The design point: every backend-dependent call reports GS_ERR_UNAVAILABLE with
// a reason. It does not return empty text, does not return a zero vector, and
// does not succeed quietly. A mobile app that asks for OCR on a build without an
// OCR engine gets told so, and can fall through to the provider pool. A build
// that returned "" would be indistinguishable from a photo of a blank page.

#include "gs_mobile.h"

#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#ifdef GS_MOBILE_HAVE_LLAMA
#include "llama_wrapper.h"
#endif

namespace {
// Writes through the shared channel so gs_last_error() actually reports it.
void set_err(const std::string& m) { gs_set_error(m.c_str()); }

const char* kNoBackend =
    "no generation backend is compiled into this build; "
    "on mobile the default gs-ffi build is portable-only and chat requires a "
    "llama.cpp-enabled build";
}  // namespace

struct gs_mobile_ctx {
    int32_t n_ctx = 2048;
    int32_t n_threads = 4;
    std::string model_path;
    bool has_model = false;
#ifdef GS_MOBILE_HAVE_LLAMA
    gs_llama_context_t* llama = nullptr;
#endif
};

extern "C" {

gs_mobile_ctx_t* gs_mobile_create(const char* model_path, int32_t n_ctx, int32_t n_threads) {
    try {
        auto* ctx = new gs_mobile_ctx();
        if (n_ctx > 0) ctx->n_ctx = n_ctx;
        if (n_threads > 0) ctx->n_threads = n_threads;
        if (model_path && *model_path) {
            ctx->model_path = model_path;
            // Presence is checked, not opened. A model that ships in app storage
            // is verified by the host, and failing here would make "not
            // downloaded yet" look like a library fault.
            ctx->has_model = true;
        }
#ifdef GS_MOBILE_HAVE_LLAMA
        if (ctx->has_model) {
            llama_config_t cfg{};
            cfg.model_path = ctx->model_path.c_str();
            cfg.n_ctx = ctx->n_ctx;
            cfg.n_threads = ctx->n_threads;
            cfg.n_gpu_layers = 0;  // mobile: no GPU offload path here
            cfg.use_mmap = 1;
            cfg.cache_type_k = 1;  // F16
            cfg.cache_type_v = 1;
            ctx->llama = gs_llama_create(&cfg);
            if (!ctx->llama) {
                set_err(std::string("llama load failed: ") + gs_last_error());
                delete ctx;
                return nullptr;
            }
        }
#endif
        return ctx;
    } catch (const std::exception& e) {
        set_err(std::string("gs_mobile_create: ") + e.what());
        return nullptr;
    }
}

void gs_mobile_free(gs_mobile_ctx_t* ctx) {
    if (!ctx) return;
#ifdef GS_MOBILE_HAVE_LLAMA
    if (ctx->llama) gs_llama_free(ctx->llama);
#endif
    delete ctx;
}

int32_t gs_mobile_backend_available(gs_mobile_ctx_t* ctx) {
    (void)ctx;
#ifdef GS_MOBILE_HAVE_LLAMA
    return ctx && ctx->llama && gs_llama_available(ctx->llama) ? 1 : 0;
#else
    return 0;
#endif
}

const char* gs_mobile_backend_name(gs_mobile_ctx_t* ctx) {
    (void)ctx;
#ifdef GS_MOBILE_HAVE_LLAMA
    return ctx && ctx->llama ? "llama.cpp" : "none";
#else
    return "none";
#endif
}

char* gs_mobile_chat(gs_mobile_ctx_t* ctx, const char* prompt, int32_t max_tokens, float temperature) {
    if (!ctx || !prompt) {
        set_err("ctx or prompt is null");
        return nullptr;
    }
    if (max_tokens <= 0) max_tokens = 256;
#ifdef GS_MOBILE_HAVE_LLAMA
    if (ctx->llama) {
        // TEMPLATED, NOT RAW. The raw prompt produced a prompt FRAGMENT rather
        // than an answer, from a passing test on the emulator:
        //
        //   chat("hello") -> ", i have a question about the following code:"
        //
        // which is a base model completing a document, because nothing told it a
        // user was speaking. The template comes from the GGUF itself, so this is
        // not a Qwen-specific assumption.
        //
        // The system prompt is terse on purpose. This is a phone assistant and
        // the tokens spent on a persona are tokens not spent answering.
        static const char* kSystem = "You are a helpful assistant on a phone. "
                                     "Answer briefly and directly.";
        llama_result_t r = gs_llama_chat(ctx->llama, kSystem, prompt,
                                         max_tokens, temperature);
        if (r.status != GS_OK) {
            set_err(std::string("generate failed: ") + gs_last_error());
            return nullptr;
        }
        return r.text;  // owned by caller; gs_llama_free_result_text releases
    }
#endif
    set_err(kNoBackend);
    return nullptr;
}

char* gs_mobile_ocr(gs_mobile_ctx_t* ctx, const char* image_path) {
    (void)ctx;
    if (!image_path) {
        set_err("image_path is null");
        return nullptr;
    }
    // No OCR engine in the portable build. Returning "" here would be read as
    // "the image contains no text", which is a different and wrong claim.
    set_err("no OCR engine is compiled into this mobile build; "
            "run OCR through the provider path or use a gs-ocr-enabled build");
    return nullptr;
}

int32_t gs_mobile_embed_image(gs_mobile_ctx_t* ctx, const uint8_t* rgb, int32_t w, int32_t h,
                              float* out, int32_t cap) {
    (void)ctx;
    (void)rgb;
    (void)w;
    (void)h;
    (void)out;
    (void)cap;
    set_err("no image embedder is compiled into this mobile build");
    return GS_ERR_UNAVAILABLE;
}

int32_t gs_mobile_embed_text(gs_mobile_ctx_t* ctx, const char* text, float* out, int32_t cap) {
    (void)ctx; (void)text; (void)out; (void)cap;
    set_err("no text embedder is compiled into this mobile build");
    return GS_ERR_UNAVAILABLE;
}

int32_t gs_mobile_embed_dim(gs_mobile_ctx_t* ctx) {
    (void)ctx;
    return GS_ERR_UNAVAILABLE;
}

gs_device_class_t gs_mobile_device_class(void) { return gs_device_class(); }

int32_t gs_mobile_should_use_local(uint64_t max_params) { return gs_device_allows(max_params); }

const char* gs_mobile_build_info(void) {
    // Assembled in pieces because the #ifdef arms are separate string literals.
    //
    // STATIC, and it has to be: this returned info.c_str() from a function-local
    // std::string, which dangles the moment the function returns. The symptom was
    // build_info() == "" on every call, with no crash, because the freed
    // buffer happened to read as an empty string.
    static std::string info = std::string("gs-ffi ") + GS_MOBILE_VERSION;
#ifdef GS_MOBILE_HAVE_LLAMA
    info += " +llama";
#else
    info += " portable";
#endif
#ifdef GS_CLIP_HAVE_ONNXRUNTIME
    info += " +clip";
#endif
#ifdef GS_OCR_HAVE_TESSERACT
    info += " +ocr";
#endif
    return info.c_str();
}

}  // extern "C"
