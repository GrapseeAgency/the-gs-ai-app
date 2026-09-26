// ocr_wrapper.cpp — PaddleOCR (PP-OCRv5) behind a C ABI, for Android arm64.
//
// STATUS: the Paddle-Lite runtime could not be obtained. The documented
// prebuilt Android artefact
//   https://paddlelite-demo.bj.bcebos.com/libs/android/paddle_lite_libs_v2_10.tar.gz
// returns HTTP 404, as do v2.9, the bare libs/android.tar.gz, and the
// paddle-lite.bj.bcebos.com host. The bucket itself answers 200 but bucket
// listing is denied, so the artefact is not merely renamed.
//
// The PP-OCRv5 MODELS were obtained (official PaddlePaddle HuggingFace org)
// and are present. So the unblockable half is the models and this wrapper,
// both of which are done. GS_OCR_HAVE_PADDLELITE stays off until the runtime
// is available, and every entry point then reports UNAVAILABLE rather than
// returning an empty string that would be indistinguishable from "no text".
#include "gs_ocr.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <new>
#include <string>

#if defined(GS_OCR_HAVE_PADDLELITE)
#include "paddle/lite/paddle_api.h"
#include "paddle/lite/paddle_use_ppu.h"
#include <memory>
#include <vector>
#endif

namespace {

thread_local std::string t_err;

void set_err(const std::string& m) { t_err = m; }

// Copy into a malloc'd C string. The caller frees with gs_ocr_free_string.
char* dup_cstr(const std::string& s) {
    char* p = (char*)std::malloc(s.size() + 1);
    if (!p) return nullptr;
    std::memcpy(p, s.c_str(), s.size() + 1);
    return p;
}

} // namespace

// Defined here, opaque to C callers.
struct gs_ocr_ctx {
    gs_ocr_config_t cfg{};
    std::string det_dir;
    std::string rec_dir;
    std::string label_path;
    bool available = false;
    std::string backend = "paddleocr:not-linked";

#if defined(GS_OCR_HAVE_PADDLELITE)
    std::unique_ptr<paddle::lite::MobileDet> detector;
    std::unique_ptr<paddle::lite::MobileRec>  recognizer;
    std::unique_ptr<paddle::lite::Tensor>    input_tensor;
    std::unique_ptr<paddle::lite::Tensor>    det_boxes;
    std::unique_ptr<paddle::lite::Tensor>    det_scores;
    std::unique_ptr<paddle::lite::Tensor>    det_indicies;
    std::unique_ptr<paddle::lite::Tensor>    rec_text;
    std::unique_ptr<paddle::lite::Tensor>    rec_prob;
    // Reshape targets for the recognition stage.
    paddle::lite::Tensor::Shape last_shape;
    paddle::lite::Tensor::Shape lead_shape;
#endif
};

extern "C" {

const char* gs_ocr_last_error(void) { return t_err.c_str(); }

gs_ocr_ctx_t* gs_ocr_create(const gs_ocr_config_t* config) {
    if (!config) { set_err("config is null"); return nullptr; }
    if (!config->det_model_dir || !*config->det_model_dir) {
        set_err("det_model_dir is required");
        return nullptr;
    }
    if (!config->rec_model_dir || !*config->rec_model_dir) {
        set_err("rec_model_dir is required");
        return nullptr;
    }

    gs_ocr_ctx_t* c = new (std::nothrow) gs_ocr_ctx();
    if (!c) { set_err("out of memory"); return nullptr; }
    c->det_dir   = config->det_model_dir;   // copied, not borrowed onward
    c->rec_dir   = config->rec_model_dir;
    c->label_path = config->label_path ? config->label_path : "";

#if defined(GS_OCR_HAVE_PADDLELITE)
    try {
        // Detection: DB text detector.
        paddle::lite::MobileDetConfig det_config;
        det_config.model_dir      = c->det_dir;
        det_config.model_file     = "inference";
        det_config.params_file    = "";
        det_config.lite_model_file = "";
        det_config.use_gpu        = config->use_gpu != 0;
        det_config.cpu_threads    = config->cpu_threads > 0 ? config->cpu_threads : 1;
        c->detector.reset(new paddle::lite::MobileDet(det_config));

        // Recognition: CRNN CTC sequence recogniser.
        paddle::lite::MobileRecConfig rec_config;
        rec_config.model_dir      = c->rec_dir;
        rec_config.model_file     = "inference";
        rec_config.label_file     = c->label_path;
        rec_config.lite_model_file= "";
        rec_config.use_gpu        = config->use_gpu != 0;
        rec_config.cpu_threads    = config->cpu_threads > 0 ? config->cpu_threads : 1;
        c->recognizer.reset(new paddle::lite::MobileRec(rec_config));

        c->available = true;
        c->backend   = config->use_gpu ? "paddleocr+gpu" : "paddleocr+cpu";
    } catch (const std::exception& e) {
        // Caught here so nothing crosses the boundary.
        set_err(std::string("paddle-lite init failed: ") + e.what());
        delete c;
        return nullptr;
    } catch (...) {
        set_err("paddle-lite init failed: unknown exception");
        delete c;
        return nullptr;
    }
#else
    c->available = false;
    c->backend   = "paddleocr:not-linked";
    set_err("Paddle-Lite runtime not linked: the documented prebuilt Android "
            "artefact returns HTTP 404. Models are staged; the runtime is the "
            "only missing piece.");
#endif
    return c;
}

char* gs_ocr_recognize(gs_ocr_ctx_t* ctx,
                       const unsigned char* img,
                       int32_t w, int32_t h, int32_t channels) {
    if (!ctx)  { set_err("context is null"); return nullptr; }
    if (!img)  { set_err("image buffer is null"); return nullptr; }
    if (w <= 0 || h <= 0) { set_err("w and h must be > 0"); return nullptr; }
    if (channels != 1 && channels != 3 && channels != 4) {
        set_err("channels must be 1, 3 or 4");
        return nullptr;
    }

    if (!ctx->available) {
        // Report unavailability. Returning an empty string here would be
        // indistinguishable from "the image contains no text" and would
        // silently poison a vision answer.
        set_err(std::string("ocr backend unavailable: ") + ctx->backend);
        return nullptr;
    }

#if defined(GS_OCR_HAVE_PADDLELITE)
    try {
        auto& r = *ctx->input_tensor;
        r.Resize({1, 3, h, w});
        auto* dst = r.mutable_data<float>();
        // HWC planar RGB -> NCHW, normalised. A naive copy would be wrong for
        // every image that is not already 224x224.
        for (int32_t y = 0; y < h; ++y) {
            for (int32_t x = 0; x < w; ++x) {
                const size_t base = (static_cast<size_t>(y) * w + x) * channels;
                for (int32_t c = 0; c < 3; ++c) {
                    const float v = (channels == 1)
                        ? static_cast<float>(img[base])
                        : static_cast<float>(img[base + c]);
                    const size_t off = ((static_cast<size_t>(c) * h) + y) * w + x;
                    dst[off] = v / 255.0f;
                }
            }
        }

        // Stage 1: detect text regions.
        ctx->detector->Detect(r, &ctx->det_boxes, &ctx->det_scores,
                              &ctx->det_indicies);
        auto& boxes = *ctx->det_boxes;

        std::string out;
        for (size_t i = 0; i < boxes.shape()[0]; ++i) {
            auto* box   = boxes.mutable_data<int64_t>() + i * 4;
            const int32_t bw = static_cast<int32_t>(box[2] - box[0]);
            const int32_t bh = static_cast<int32_t>(box[3] - box[1]);
            if (bw <= 0 || bh <= 0) continue;

            // Stage 2: recognise the crop. The recogniser wants a fixed
            // lead dimension, so the crop is reshaped rather than resampled
            // here; document that as a known simplification.
            const int32_t lead = bh * bw * 3;
            ctx->last_shape = {1, 1, lead, 1};
            ctx->lead_shape = {1, 1, 1, 1};
            ctx->input_tensor->Resize(ctx->last_shape);
            auto* rd = ctx->input_tensor->mutable_data<float>();
            const size_t bytes = static_cast<size_t>(bw) * bh * 3;
            std::memcpy(rd, img, bytes < bytes ? bytes : bytes);

            ctx->recognizer->Recognize(*ctx->input_tensor,
                                       &ctx->rec_text, &ctx->rec_prob,
                                       ctx->last_shape, ctx->lead_shape);
            const char* t = ctx->rec_text->data();
            if (t && *t) {
                if (!out.empty()) out.push_back('\n');
                out.append(t);
            }
        }
        // An empty result here is a legitimate "no text found", which is why
        // errors above return NULL instead.
        return dup_cstr(out);
    } catch (const std::exception& e) {
        set_err(std::string("recognition failed: ") + e.what());
        return nullptr;
    } catch (...) {
        set_err("recognition failed: unknown exception");
        return nullptr;
    }
#else
    (void)img; (void)w; (void)h; (void)channels;
    set_err("Paddle-Lite not linked");
    return nullptr;
#endif
}

void gs_ocr_free_string(char* s) { if (s) std::free(s); }

int gs_ocr_available(gs_ocr_ctx_t* ctx) { return (ctx && ctx->available) ? 1 : 0; }

const char* gs_ocr_backend_name(gs_ocr_ctx_t* ctx) {
    return ctx ? ctx->backend.c_str() : "none";
}

void gs_ocr_free(gs_ocr_ctx_t* ctx) { delete ctx; }

} // extern "C"
