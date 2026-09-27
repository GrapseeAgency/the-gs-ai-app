// gs_ocr_tess — OCR C ABI backed by Tesseract (host/desktop path).
//
// Tesseract ships on this host and its headers are C++-only, so this is a .cpp
// like the rest of the wrapper surface. Without GS_OCR_HAVE_TESSERACT defined
// the file still compiles and every entry point reports UNAVAILABLE, which is
// the honest state on a machine without the library.

#include "gs_ocr_tess.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>

#ifdef GS_OCR_HAVE_TESSERACT
#include <leptonica/allheaders.h>
#include <tesseract/baseapi.h>
#endif

namespace {
thread_local std::string g_err;

void set_err(const std::string &m) { g_err = m; }
}  // namespace

struct gs_ocr_ctx {
#ifdef GS_OCR_HAVE_TESSERACT
  tesseract::TessBaseAPI *api = nullptr;
#endif
};

extern "C" {

const char *gs_ocr_last_error(void) { return g_err.c_str(); }

gs_ocr_ctx_t *gs_ocr_create(void) {
#ifdef GS_OCR_HAVE_TESSERACT
  try {
    auto *ctx = new gs_ocr_ctx();
    ctx->api = new tesseract::TessBaseAPI();
    // Init returns 0 on success.
    if (ctx->api->Init(nullptr, "eng", tesseract::OEM_DEFAULT) != 0) {
      set_err(std::string("TessBaseAPI::Init failed for language 'eng' using ") +
              tesseract::TessBaseAPI::Version() +
              " (is the eng traineddata installed?)");
      delete ctx->api;
      delete ctx;
      return nullptr;
    }
    ctx->api->SetPageSegMode(tesseract::PSM_AUTO);
    return ctx;
  } catch (const std::exception &e) {
    set_err(std::string("create failed: ") + e.what());
    return nullptr;
  }
#else
  set_err("built without Tesseract (GS_OCR_HAVE_TESSERACT undefined)");
  return nullptr;
#endif
}

char *gs_ocr_recognize(gs_ocr_ctx_t *ctx, const char *image_path) {
#ifdef GS_OCR_HAVE_TESSERACT
  if (!ctx || !ctx->api || !image_path) {
    set_err("null argument");
    return nullptr;
  }
  try {
    Pix *pix = pixRead(image_path);
    if (!pix) {
      set_err("could not read image: " + std::string(image_path) +
              " (leptonica pixRead returned NULL; check the path and format)");
      return nullptr;
    }
    ctx->api->Clear();
    ctx->api->SetImage(pix);  // returns void; the pix must outlive the read
    char *utf8 = ctx->api->GetUTF8Text();
    pixDestroy(&pix);
    if (!utf8) {
      // A genuinely blank page yields no buffer. That is "no text", not a
      // failure, so report an empty string and leave the error clear.
      set_err("");
      return strdup("");
    }
    char *out = strdup(utf8);
    delete[] utf8;  // GetUTF8Text returns a new[] buffer
    if (!out) set_err("out of memory copying the result");
    return out;
  } catch (const std::exception &e) {
    set_err(std::string("recognize failed: ") + e.what());
    return nullptr;
  }
#else
  (void)ctx; (void)image_path;
  set_err("built without Tesseract (GS_OCR_HAVE_TESSERACT undefined)");
  return nullptr;
#endif
}

void gs_ocr_free_string(char *s) { free(s); }

void gs_ocr_free(gs_ocr_ctx_t *ctx) {
  if (!ctx) return;
#ifdef GS_OCR_HAVE_TESSERACT
  if (ctx->api) {
    ctx->api->End();
    delete ctx->api;
  }
#endif
  delete ctx;
}

}  // extern "C"
