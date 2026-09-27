// CLIP image/text embedding against ONNX Runtime.
//
// ONNX Runtime is linked dynamically: the wrapper compiles and every entry
// point reports UNAVAILABLE when GS_CLIP_HAVE_ONNXRUNTIME is not defined, so
// `cargo build` never depends on a system onnxruntime package.
//
// The CLIP BPE tokenizer lives here because the ABI takes plain text. The
// vocabulary and merge table are read from TSV sidecars next to the text model
// (clip_vocab.tsv, clip_merges.tsv) rather than parsing tokenizer.json, so
// this file needs no JSON parser.

#include "clip_wrapper.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <map>
#include <sstream>
#include <string>
#include <unordered_map>
#include <vector>

#ifdef GS_CLIP_HAVE_ONNXRUNTIME
#include <onnxruntime_cxx_api.h>
#endif

namespace {

constexpr int kImageSide = 256;      // MobileCLIP2-S0 input
constexpr int kContextLen = 77;      // CLIP context length
constexpr int kThreads = 4;

thread_local std::string g_last_error;

void set_error(const std::string &e) { g_last_error = e; }

// ---------------------------------------------------------------------------
// CLIP byte-level BPE
// ---------------------------------------------------------------------------

// GPT-2 / CLIP byte-to-unicode table: maps each of the 256 byte values to a
// printable codepoint so arbitrary bytes survive a text round trip.
const std::array<int, 256> &byte_encoder() {
  static const std::array<int, 256> table = [] {
    std::array<int, 256> t{};
    std::vector<int> bs;
    for (int i = '!'; i <= '~'; ++i) bs.push_back(i);
    for (int i = 0xA1; i <= 0xAC; ++i) bs.push_back(i);
    for (int i = 0xAE; i <= 0xFF; ++i) bs.push_back(i);
    std::vector<int> cs;
    cs.reserve(bs.size());
    for (int b : bs) cs.push_back(b);
    int n = 0;
    for (int b = 0; b < 256; ++b) {
      if (b < 33 || b > 126 || b == 161 || b == 172 || b == 174) cs.push_back(256 + n++);
    }
    for (size_t i = 0; i < bs.size(); ++i) t[static_cast<size_t>(bs[i])] = cs[i];
    return t;
  }();
  return table;
}

int byte_to_unicode(unsigned char b) { return byte_encoder()[b]; }

// UTF-8 encode a codepoint that may exceed 255 (the byte-level alphabet does).
void append_utf8(std::string &out, int cp) {
  if (cp < 0x80) {
    out += static_cast<char>(cp);
  } else if (cp < 0x800) {
    out += static_cast<char>(0xC0 | (cp >> 6));
    out += static_cast<char>(0x80 | (cp & 0x3F));
  } else {
    out += static_cast<char>(0xE0 | (cp >> 12));
    out += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
    out += static_cast<char>(0x80 | (cp & 0x3F));
  }
}

bool is_letter(unsigned char c) {
  return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c >= 0x80;
}
bool is_digit(unsigned char c) { return c >= '0' && c <= '9'; }
bool is_space(unsigned char c) { return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f' || c == '\v'; }

struct Tokenizer {
  std::unordered_map<std::string, int> vocab;
  std::unordered_map<std::string, int> ranks;  // "a b" -> merge rank
  int bos_id = -1;
  int eos_id = -1;

  bool load(const std::string &vocab_path, const std::string &merges_path) {
    std::ifstream vf(vocab_path);
    if (!vf) { set_error("cannot open vocab: " + vocab_path); return false; }
    std::string line;
    while (std::getline(vf, line)) {
      auto tab = line.find('\t');
      if (tab == std::string::npos) continue;
      int id = std::atoi(line.substr(0, tab).c_str());
      std::string tok = line.substr(tab + 1);
      std::string out;
      for (size_t i = 0; i < tok.size(); ++i) {
        if (tok.compare(i, 5, "<TAB>") == 0) { out += '\t'; i += 4; }
        else out += tok[i];
      }
      vocab[out] = id;
    }
    // The sentinels are what frame a CLIP sequence; resolve them by name.
    auto bos = vocab.find("<|startoftext|>");
    auto eos = vocab.find("<|endoftext|>");
    if (bos == vocab.end() || eos == vocab.end()) {
      set_error("tokenizer vocab is missing the <|startoftext|>/<|endoftext|> sentinels");
      return false;
    }
    bos_id = bos->second;
    eos_id = eos->second;
    std::ifstream mf(merges_path);
    if (!mf) { set_error("cannot open merges: " + merges_path); return false; }
    while (std::getline(mf, line)) {
      auto t1 = line.find('\t');
      if (t1 == std::string::npos) continue;
      auto t2 = line.find('\t', t1 + 1);
      if (t2 == std::string::npos) continue;
      // Key must match the separator bpe() looks up.
      std::string a = line.substr(t1 + 1, t2 - t1 - 1);
      std::string b = line.substr(t2 + 1);
      ranks[a + "\x01" + b] = std::atoi(line.substr(0, t1).c_str());
    }
    if (vocab.empty() || ranks.empty()) { set_error("tokenizer sidecars empty"); return false; }
    return true;
  }

  // Byte-level encode one pre-token: every byte becomes its unicode stand-in.
  static std::string encode_bytes(const std::string &s) {
    std::string out;
    for (unsigned char c : s) append_utf8(out, byte_to_unicode(c));
    return out;
  }

  // Split a UTF-8 string into characters, marking the last one word-final.
  // CLIP appends the end-of-word marker to the final CHARACTER, not to the
  // whole word: "coffee" becomes c,o,f,f,e,"e</w>". The marker is literal
  // text and must not be byte-encoded, otherwise "</w>" turns into the
  // unrelated ids 27,14,86,29.
  static std::vector<std::string> symbols_for(const std::string &word) {
    std::vector<std::string> syms;
    for (size_t i = 0; i < word.size();) {
      size_t len = 1;
      while (i + len < word.size() && len < 4 &&
             (static_cast<unsigned char>(word[i + len]) & 0xC0) == 0x80) ++len;
      syms.push_back(word.substr(i, len));
      i += len;
    }
    if (!syms.empty()) syms.back() += "</w>";
    return syms;
  }

  std::vector<std::string> bpe(std::vector<std::string> word) const {
    if (word.size() < 2) return word;
    while (true) {
      int best_rank = -1;
      size_t best_i = 0;
      for (size_t i = 0; i + 1 < word.size(); ++i) {
        auto it = ranks.find(word[i] + "\x01" + word[i + 1]);
        if (it != ranks.end() && (best_rank < 0 || it->second < best_rank)) {
          best_rank = it->second;
          best_i = i;
        }
      }
      if (best_rank < 0) break;
      word[best_i] = word[best_i] + word[best_i + 1];
      word.erase(word.begin() + static_cast<long>(best_i) + 1);
      if (word.size() == 1) break;
    }
    return word;
  }

  std::vector<int> encode(const std::string &text) const {
    // Lowercase, then split into letters / digits / punctuation runs, matching
    // CLIP's pattern for ASCII input.
    std::string lower;
    lower.reserve(text.size());
    for (unsigned char c : text) lower += static_cast<char>(std::tolower(c));

    // CLIP requires the sequence to be framed by <|startoftext|> and
    // <|endoftext|>. Verified against the reference HF tokenizer:
    //   "a coffee mug" -> [49406, 320, 2453, 9722, 49407]
    // Omitting the two sentinels produces embeddings that do not align with the
    // image tower at all, which shows up as uniformly low cosine scores.
    std::vector<int> out;
    if (bos_id >= 0) out.push_back(bos_id);
    // Every word is marked as word-final with "</w>" before byte encoding, so
    // "coffee" resolves to the vocab entry "coffee</w>" (2453) rather than the
    // mid-word fragment "coffee" (12898). Without the marker the tokenizer
    // silently produces the wrong ids.
    auto emit = [&](const std::string &raw) {
      std::vector<std::string> syms = symbols_for(raw);
      for (std::string &s : syms) s = encode_bytes(s);
      for (const std::string &piece : bpe(syms)) {
        auto it = vocab.find(piece);
        if (it != vocab.end()) out.push_back(it->second);
      }
    };

    size_t i = 0;
    while (i < lower.size()) {
      unsigned char c = static_cast<unsigned char>(lower[i]);
      if (is_space(c)) { ++i; continue; }
      if (is_letter(c)) {
        size_t j = i;
        while (j < lower.size() && is_letter(static_cast<unsigned char>(lower[j]))) ++j;
        emit(lower.substr(i, j - i));
        i = j;
      } else if (is_digit(c)) {
        emit(lower.substr(i, 1));  // digits are split individually
        ++i;
      } else {
        size_t j = i;
        while (j < lower.size() && !is_space(static_cast<unsigned char>(lower[j])) &&
               !is_letter(static_cast<unsigned char>(lower[j])) &&
               !is_digit(static_cast<unsigned char>(lower[j]))) ++j;
        emit(lower.substr(i, j - i));
        i = j;
      }
    }
    if (eos_id >= 0) out.push_back(eos_id);
    return out;
  }
};

}  // namespace

// ---------------------------------------------------------------------------
// Context
// ---------------------------------------------------------------------------

struct gs_clip_ctx {
#ifdef GS_CLIP_HAVE_ONNXRUNTIME
  Ort::Env env{ORT_LOGGING_LEVEL_ERROR, "gs_clip"};
  Ort::SessionOptions opts;
  Ort::Session *vision = nullptr;
  Ort::Session *text = nullptr;
#endif
  Tokenizer tok;
  int dim = 0;
  std::string dir;
};

#ifdef GS_CLIP_HAVE_ONNXRUNTIME
namespace {

// Determine the embedding width by running one probe and reading the concrete
// output size.
//
// The declared shapes cannot be used: these models declare a symbolic batch
// dim (-1), and ORT's C++ GetShape() tries to size a vector from it, which
// throws std::length_error ("cannot create std::vector larger than
// max_size()"). After a real Run the output shape is concrete, so its element
// count is authoritative.
int probe_output_elems(Ort::Session *sess, const std::vector<float> &zeros,
                       const std::vector<int64_t> &shape) {
  Ort::AllocatorWithDefaultOptions alloc;
  auto in_name = sess->GetInputNameAllocated(0, alloc);
  auto out_name = sess->GetOutputNameAllocated(0, alloc);
  auto mem = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
  auto tensor = Ort::Value::CreateTensor<float>(mem, const_cast<float *>(zeros.data()),
                                                zeros.size(), shape.data(), shape.size());
  const char *in_names[] = {in_name.get()};
  const char *out_names[] = {out_name.get()};
  auto outputs = sess->Run(Ort::RunOptions{nullptr}, in_names, &tensor, 1, out_names, 1);
  return static_cast<int>(outputs[0].GetTensorTypeAndShapeInfo().GetElementCount());
}

}  // namespace
#endif

extern "C" {

const char *gs_clip_last_error(void) { return g_last_error.c_str(); }

gs_clip_ctx_t *gs_clip_create(const char *vision_path, const char *text_path) {
#ifdef GS_CLIP_HAVE_ONNXRUNTIME
  try {
    if (!vision_path || !text_path) { set_error("null path"); return nullptr; }
    auto *ctx = new gs_clip_ctx();
    ctx->opts.SetIntraOpNumThreads(kThreads);
    ctx->opts.SetInterOpNumThreads(1);
    ctx->opts.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);
    try {
      ctx->vision = new Ort::Session(ctx->env, vision_path, ctx->opts);
      ctx->text = new Ort::Session(ctx->env, text_path, ctx->opts);
    } catch (const std::exception &e) {
      set_error(std::string("onnx load failed: ") + e.what());
      delete ctx->vision; delete ctx->text; delete ctx;
      return nullptr;
    }
    // Sidecars live beside the text model.
    std::string tp(text_path);
    size_t slash = tp.find_last_of('/');
    ctx->dir = (slash == std::string::npos) ? "." : tp.substr(0, slash);
    if (!ctx->tok.load(ctx->dir + "/clip_vocab.tsv", ctx->dir + "/clip_merges.tsv")) {
      delete ctx->vision; delete ctx->text; delete ctx;
      return nullptr;
    }
    // Embedding width is measured, never assumed: one zero-input probe.
    {
      const size_t n = 3u * kImageSide * kImageSide;
      std::vector<float> zeros(n, 0.0f);
      const std::vector<int64_t> shape = {1, 3, kImageSide, kImageSide};
      ctx->dim = probe_output_elems(ctx->vision, zeros, shape);
    }
    if (ctx->dim <= 0) {
      set_error("could not determine embedding dim from vision model");
      delete ctx->vision; delete ctx->text; delete ctx;
      return nullptr;
    }
    return ctx;
  } catch (const std::exception &e) {
    set_error(std::string("create failed: ") + e.what());
    return nullptr;
  }
#else
  (void)vision_path; (void)text_path;
  set_error("built without ONNX Runtime (GS_CLIP_HAVE_ONNXRUNTIME undefined)");
  return nullptr;
#endif
}

int gs_clip_embed_dim(gs_clip_ctx_t *ctx) { return ctx ? ctx->dim : 0; }

#ifdef GS_CLIP_HAVE_ONNXRUNTIME
namespace {

// Run a session over a single float tensor and copy the first embedding.
int run_embed(Ort::Session *sess, const float *input, int64_t count,
              const int64_t *shape, size_t ndim, float *out, int dim) {
  try {
    Ort::AllocatorWithDefaultOptions alloc;
    auto in_name = sess->GetInputNameAllocated(0, alloc);
    auto out_name = sess->GetOutputNameAllocated(0, alloc);
    std::array<int64_t, 4> dims{};
    for (size_t i = 0; i < ndim; ++i) dims[i] = shape[i];
    auto mem = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
    auto tensor = Ort::Value::CreateTensor<float>(mem, const_cast<float *>(input), count,
                                                  dims.data(), ndim);
    const char *in_names[] = {in_name.get()};
    const char *out_names[] = {out_name.get()};
    auto outputs = sess->Run(Ort::RunOptions{nullptr}, in_names, &tensor, 1, out_names, 1);
    const float *src = outputs[0].GetTensorData<float>();
    std::copy(src, src + dim, out);
    // L2 normalise so callers can use a plain dot product.
    double norm = 0.0;
    for (int i = 0; i < dim; ++i) norm += static_cast<double>(out[i]) * out[i];
    norm = std::sqrt(norm);
    if (norm > 1e-12) for (int i = 0; i < dim; ++i) out[i] = static_cast<float>(out[i] / norm);
    return GS_CLIP_OK;
  } catch (const std::exception &e) {
    set_error(std::string("inference failed: ") + e.what());
    return GS_CLIP_ERR_ONNX;
  }
}

}  // namespace
#endif

int gs_clip_embed_image(gs_clip_ctx_t *ctx, const unsigned char *rgb, int w, int h,
                        float *out_embedding) {
#ifdef GS_CLIP_HAVE_ONNXRUNTIME
  if (!ctx || !rgb || !out_embedding) return GS_CLIP_ERR_ARG;
  if (w != kImageSide || h != kImageSide) return GS_CLIP_ERR_SHAPE;
  // NCHW float scaled to [0,1].
  //
  // No mean/std normalisation. The model card's reference snippet applies
  // ImageNet CLIP mean/std, but that is wrong for this ONNX export: measured
  // over three known images, normalising collapses the image tower to a
  // near-constant vector (image-image cosine ~1.0) and every query scores
  // 0.01-0.07 with the ranking essentially random. Feeding plain [0,1] gives
  // image-image cosine 0.14-0.24 and puts the true caption first for 3/3
  // images at 0.28-0.30. The export has the normalisation folded in already.
  std::vector<float> tensor(3 * kImageSide * kImageSide);
  for (int y = 0; y < h; ++y) {
    for (int x = 0; x < w; ++x) {
      const unsigned char *px = rgb + (static_cast<size_t>(y) * w + x) * 3;
      for (int c = 0; c < 3; ++c) {
        tensor[c * kImageSide * kImageSide + y * kImageSide + x] =
            static_cast<float>(px[c]) / 255.0f;
      }
    }
  }
  const int64_t shape[4] = {1, 3, kImageSide, kImageSide};
  return run_embed(ctx->vision, tensor.data(), static_cast<int64_t>(tensor.size()), shape, 4,
                   out_embedding, ctx->dim);
#else
  (void)ctx; (void)rgb; (void)w; (void)h; (void)out_embedding;
  return GS_CLIP_ERR_UNAVAILABLE;
#endif
}

int gs_clip_embed_tokens(gs_clip_ctx_t *ctx, const long long *ids, int n, float *out_embedding) {
#ifdef GS_CLIP_HAVE_ONNXRUNTIME
  if (!ctx || !ids || !out_embedding) return GS_CLIP_ERR_ARG;
  if (n <= 0 || n > kContextLen) return GS_CLIP_ERR_SHAPE;
  std::vector<int64_t> padded(kContextLen, 0);
  for (int i = 0; i < n; ++i) padded[i] = static_cast<int64_t>(ids[i]);
  const int64_t shape[2] = {1, kContextLen};
  // The text model takes int64, so this path runs the session directly rather
  // than reusing the float-only image helper.
  try {
    Ort::AllocatorWithDefaultOptions alloc;
    auto in_name = ctx->text->GetInputNameAllocated(0, alloc);
    auto out_name = ctx->text->GetOutputNameAllocated(0, alloc);
    auto mem = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
    auto tensor = Ort::Value::CreateTensor<int64_t>(mem, padded.data(), padded.size(), shape, 2);
    const char *in_names[] = {in_name.get()};
    const char *out_names[] = {out_name.get()};
    auto outputs = ctx->text->Run(Ort::RunOptions{nullptr}, in_names, &tensor, 1, out_names, 1);
    const float *src = outputs[0].GetTensorData<float>();
    std::copy(src, src + ctx->dim, out_embedding);
    double norm = 0.0;
    for (int i = 0; i < ctx->dim; ++i) norm += static_cast<double>(out_embedding[i]) * out_embedding[i];
    norm = std::sqrt(norm);
    if (norm > 1e-12) for (int i = 0; i < ctx->dim; ++i) out_embedding[i] = static_cast<float>(out_embedding[i] / norm);
    return GS_CLIP_OK;
  } catch (const std::exception &e) {
    set_error(std::string("text inference failed: ") + e.what());
    return GS_CLIP_ERR_ONNX;
  }
#else
  (void)ctx; (void)ids; (void)n; (void)out_embedding;
  return GS_CLIP_ERR_UNAVAILABLE;
#endif
}

int gs_clip_embed_text(gs_clip_ctx_t *ctx, const char *text, float *out_embedding) {
#ifdef GS_CLIP_HAVE_ONNXRUNTIME
  if (!ctx || !text || !out_embedding) return GS_CLIP_ERR_ARG;
  std::vector<int> ids = ctx->tok.encode(text);
  if (ids.empty()) { set_error("tokenizer produced no tokens"); return GS_CLIP_ERR_TOKENIZER; }
  if (static_cast<int>(ids.size()) > kContextLen) ids.resize(kContextLen);
  if (std::getenv("GS_CLIP_DEBUG_TOKENS")) {
    std::string line = "tokens:";
    for (int id : ids) { line += ' '; line += std::to_string(id); }
    fprintf(stderr, "%s\n", line.c_str());
  }
  std::vector<long long> ll(ids.begin(), ids.end());
  return gs_clip_embed_tokens(ctx, ll.data(), static_cast<int>(ll.size()), out_embedding);
#else
  (void)ctx; (void)text; (void)out_embedding;
  return GS_CLIP_ERR_UNAVAILABLE;
#endif
}

void gs_clip_free(gs_clip_ctx_t *ctx) {
  if (!ctx) return;
#ifdef GS_CLIP_HAVE_ONNXRUNTIME
  delete ctx->vision;
  delete ctx->text;
#endif
  delete ctx;
}

}  // extern "C"
