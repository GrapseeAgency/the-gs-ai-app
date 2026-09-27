//! gs-ffi — the Rust side of the C ABI boundary.
//!
//! Dossier rule 4: **no Rust panic may cross the FFI boundary.** Every
//! `extern "C"` function here wraps its body in `catch_unwind` and converts a
//! panic into an error code. A panic unwinding into C++ is undefined behaviour
//! and, in practice, an abort that takes the whole process with it.

use std::ffi::{c_char, c_int, c_void, CStr};

/// Mirrors `gs_status_t` in cpp/include/gs_abi.h.
pub const GS_OK: c_int = 0;
pub const GS_ERR_INVALID_ARG: c_int = -1;
pub const GS_ERR_NO_MEMORY: c_int = -2;
pub const GS_ERR_IO: c_int = -3;
pub const GS_ERR_UNAVAILABLE: c_int = -4;
pub const GS_ERR_GENERATION: c_int = -5;
pub const GS_ERR_INTERNAL: c_int = -7;

#[repr(C)]
pub struct LlamaConfig {
    pub model_path: *const c_char,
    pub n_ctx: c_int,
    pub n_threads: c_int,
    pub n_gpu_layers: c_int,
    pub use_mmap: c_int,
    /// ggml_type for the K cache. 1 = F16 (default), 2 = Q4_0, 8 = Q8_0.
    pub cache_type_k: c_int,
    /// ggml_type for the V cache. 1 = F16 (default), 2 = Q4_0, 8 = Q8_0.
    pub cache_type_v: c_int,
    /// Concurrent sequences the context keeps apart. 0 or 1 disables batching,
    /// which is the single-request behaviour this bridge had before batching
    /// existed.
    pub n_seq_max: c_int,
}

/// KV cache element type. Mirrors `gs_kv_type_t` in the C header.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum KvType {
    F16 = 1,
    Q8_0 = 8,
    Q5_1 = 7,
    Q5_0 = 6,
    Q4_1 = 3,
    /// The aggressive option: 4-bit, roughly a quarter of F16 per element.
    Q4_0 = 2,
    Iq4Nl = 20,
}

impl KvType {
    pub fn as_str(self) -> &'static str {
        match self {
            KvType::F16 => "f16",
            KvType::Q8_0 => "q8_0",
            KvType::Q5_1 => "q5_1",
            KvType::Q5_0 => "q5_0",
            KvType::Q4_1 => "q4_1",
            KvType::Q4_0 => "q4_0",
            KvType::Iq4Nl => "iq4_nl",
        }
    }
    pub fn from_name(s: &str) -> Option<Self> {
        match s.to_ascii_lowercase().as_str() {
            "f16" => Some(KvType::F16),
            "q8_0" => Some(KvType::Q8_0),
            "q5_1" => Some(KvType::Q5_1),
            "q5_0" => Some(KvType::Q5_0),
            "q4_1" => Some(KvType::Q4_1),
            "q4_0" => Some(KvType::Q4_0),
            "iq4_nl" => Some(KvType::Iq4Nl),
            _ => None,
        }
    }
}

#[repr(C)]
pub struct LlamaResult {
    pub text: *mut c_char,
    pub n_tokens: c_int,
    pub status: c_int,
}

/// An owned snapshot of a context's KV state.
pub struct StateBlob {
    buf: Vec<u8>,
}

impl StateBlob {
    pub fn len(&self) -> usize {
        self.buf.len()
    }
    pub fn is_empty(&self) -> bool {
        self.buf.is_empty()
    }
}

impl std::fmt::Debug for StateBlob {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "StateBlob({} bytes)", self.buf.len())
    }
}

extern "C" {
    #[allow(dead_code)]
    fn gs_llama_validate_config(config: *const LlamaConfig) -> c_int;
    fn gs_llama_create(config: *const LlamaConfig) -> *mut c_void;
    fn gs_llama_free_text(s: *mut c_char);

    fn gs_llama_batch_generate(
        ctx: *mut c_void,
        prompts: *const *const c_char,
        n: c_int,
        max_tokens: c_int,
        temperature: f32,
        out_texts: *mut *mut c_char,
        out_lens: *mut c_int,
    ) -> c_int;

    fn gs_llama_generate(
        ctx: *mut c_void,
        prompt: *const c_char,
        max_tokens: c_int,
        temperature: f32,
    ) -> LlamaResult;
    fn gs_llama_free_result_text(result: *mut LlamaResult);
    fn gs_llama_available(ctx: *mut c_void) -> c_int;
    fn gs_llama_backend_name(ctx: *mut c_void) -> *const c_char;
    fn gs_llama_n_layer(ctx: *mut c_void) -> c_int;
    fn gs_llama_token_count(ctx: *mut c_void, text: *const c_char) -> c_int;
    fn gs_llama_n_ctx(ctx: *mut c_void) -> c_int;
    fn gs_llama_prefill(ctx: *mut c_void, prompt: *const c_char, clear_cache: c_int) -> c_int;
    fn gs_llama_state_size(ctx: *mut c_void) -> i64;
    fn gs_llama_state_save(ctx: *mut c_void, dest: *mut u8, cap: i64) -> i64;
    fn gs_llama_state_restore(ctx: *mut c_void, src: *const u8, len: i64) -> i64;
    fn gs_llama_state_free(buf: *mut u8);
    fn gs_llama_generate_opts(
        ctx: *mut c_void,
        prompt: *const c_char,
        max_tokens: c_int,
        temperature: f32,
        clear_cache: c_int,
        start_pos: c_int,
    ) -> LlamaResult;
    fn gs_llama_gpu_offload_supported(ctx: *mut c_void) -> c_int;
    fn gs_llama_free(ctx: *mut c_void);
}

/// Serialises every call into libllama.
///
/// `llama_backend_init` / `llama_backend_free` are process-global and are NOT
/// reentrant. Two threads each creating a context race on backend setup, and
/// the failure mode is a SIGSEGV inside llama.cpp with no diagnostic. This is
/// not only a test-harness concern: the Axum server is multi-threaded, so this
/// lock is load-bearing in production. A single global backend is also simply
/// what llama.cpp is designed around - contexts share it.
static LLAMA_LOCK: std::sync::Mutex<()> = std::sync::Mutex::new(());

/// Safe handle over the C ABI. Dropping it calls the C free function, so a
/// context cannot leak by being forgotten mid-turn.
pub struct LlamaModel {
    raw: *mut c_void,
    /// Kept alive for the handle's lifetime: the C side borrows nothing, but
    /// holding it here documents the ownership and avoids a surprise free.
    _path: String,
    /// Output tokens from the most recent generate(). Reported by the C side,
    /// which counts pieces; the text length is not a token count.
    last_n: i32,
}

// The handle is only touched through &mut self on the C side, which is safe
// from Rust's perspective as long as we serialise access. The AgentLoop is
// single-threaded per request, so no lock is needed here.
unsafe impl Send for LlamaModel {}

impl LlamaModel {
    /// Load a model. Returns Err with a status code on any failure — never a
    /// half-built handle.
    pub fn load(path: &str, n_ctx: i32, n_threads: i32, n_gpu_layers: i32) -> Result<Self, i32> {
        Self::load_with(path, n_ctx, n_threads, n_gpu_layers, KvType::F16, KvType::F16, 1)
    }

    /// Load with an explicit KV cache element type. F16 unless asked otherwise.
    pub fn load_with_kv(
        path: &str,
        n_ctx: i32,
        n_threads: i32,
        n_gpu_layers: i32,
        kv_k: KvType,
        kv_v: KvType,
    ) -> Result<Self, i32> {
        Self::load_with(path, n_ctx, n_threads, n_gpu_layers, kv_k, kv_v, 1)
    }

    /// Load with KV cache element types and a sequence-slot count.
    ///
    /// `n_seq_max` is how many independent sequences [`Self::batch_generate`]
    /// can run in one decode. It costs one KV slot per sequence, so it is a
    /// memory decision as much as a throughput one: n_ctx is divided between the
    /// slots. 1 means no batching.
    pub fn load_with(
        path: &str,
        n_ctx: i32,
        n_threads: i32,
        n_gpu_layers: i32,
        kv_k: KvType,
        kv_v: KvType,
        n_seq_max: i32,
    ) -> Result<Self, i32> {
        let c_path = match std::ffi::CString::new(path) {
            Ok(c) => c,
            Err(_) => return Err(GS_ERR_INVALID_ARG), // interior NUL
        };
        let cfg = LlamaConfig {
            cache_type_k: kv_k as c_int,
            cache_type_v: kv_v as c_int,
            model_path: c_path.as_ptr(),
            n_ctx,
            n_threads,
            n_gpu_layers,
            use_mmap: 1,
            n_seq_max: n_seq_max.max(1),
        };

        // The whole load is inside catch_unwind: a panic in FFI must not
        // unwind into the caller.
        let _guard = LLAMA_LOCK.lock().unwrap_or_else(|e| e.into_inner());
        let created = std::panic::catch_unwind(|| unsafe { gs_llama_create(&cfg) })
            .map_err(|_| GS_ERR_INTERNAL)?;

        if created.is_null() {
            return Err(GS_ERR_UNAVAILABLE);
        }
        // Defence in depth: a non-null handle is NOT proof of a working
        // backend. When libllama is not linked the C factory still returns a
        // handle whose `available` flag is false, and accepting it would hand
        // the caller a model that can never generate anything.
        // Checked inline because load() already holds LLAMA_LOCK.
        let ok = std::panic::catch_unwind(|| unsafe { gs_llama_available(created) == 1 })
            .unwrap_or(false);
        if !ok {
            let _ = std::panic::catch_unwind(|| unsafe { gs_llama_free(created) });
            return Err(GS_ERR_UNAVAILABLE);
        }
        Ok(Self { raw: created, _path: path.to_string(), last_n: 0 })
    }

    pub fn is_available(&self) -> bool {
        let _guard = LLAMA_LOCK.lock().unwrap_or_else(|e| e.into_inner());
        std::panic::catch_unwind(|| unsafe { gs_llama_available(self.raw) == 1 })
            .unwrap_or(false)
    }

    pub fn backend_name(&self) -> String {
        std::panic::catch_unwind(|| unsafe {
            let p = gs_llama_backend_name(self.raw);
            if p.is_null() {
                String::new()
            } else {
                CStr::from_ptr(p).to_string_lossy().into_owned()
            }
        })
        .unwrap_or_else(|_| "unknown".into())
    }

    /// Prefill the cache without generating. Returns the number of positions
    /// now resident, or an error.
    pub fn prefill(&mut self, prompt: &str, clear_cache: bool) -> Result<i32, i32> {
        let c = std::ffi::CString::new(prompt).map_err(|_| GS_ERR_INVALID_ARG)?;
        std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
            gs_llama_prefill(self.raw, c.as_ptr(), if clear_cache { 1 } else { 0 })
        }))
        .map_err(|_| GS_ERR_INTERNAL)
    }

    /// Generate WITHOUT clearing the KV cache first.
    ///
    /// Only valid immediately after state_restore(), where the prefix is
    /// already resident. Calling it cold leaves a stale cache in place and the
    /// model will answer confidently from the wrong context, which is why the
    /// clearing variant is the default and this one is opt-in by name.
    pub fn generate_preserving_cache(
        &mut self,
        prompt: &str,
        max_tokens: i32,
        temperature: f32,
    ) -> Result<String, i32> {
        // -1: read the resident position from the cache rather than assuming.
        self.generate_from(self.raw, prompt, max_tokens, temperature, 0, -1)
    }

    fn generate_from(
        &mut self,
        _unused: *mut c_void,
        prompt: &str,
        max_tokens: i32,
        temperature: f32,
        clear_cache: i32,
        start_pos: i32,
    ) -> Result<String, i32> {
        let c = std::ffi::CString::new(prompt).map_err(|_| GS_ERR_INVALID_ARG)?;
        let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
            gs_llama_generate_opts(self.raw, c.as_ptr(), max_tokens, temperature, clear_cache, start_pos)
        }))
        .map_err(|_| GS_ERR_INTERNAL)?;
        if res.status != GS_OK {
            if !res.text.is_null() {
                let mut r = LlamaResult { text: res.text, n_tokens: 0, status: res.status };
                unsafe { gs_llama_free_result_text(&mut r) };
            }
            return Err(res.status);
        }
        if res.text.is_null() {
            return Err(GS_ERR_GENERATION);
        }
        self.last_n = res.n_tokens;
        let owned = unsafe { CStr::from_ptr(res.text) }.to_string_lossy().into_owned();
        let mut r = LlamaResult { text: res.text, n_tokens: res.n_tokens, status: res.status };
        unsafe { gs_llama_free_result_text(&mut r) };
        Ok(owned)
    }

    /// Serialise the whole KV state. Restoring it into a context that has
    /// already prefilled the same prefix skips that prefill entirely, which is
    /// where the prefix-reuse saving comes from.
    ///
    /// The buffer is owned here and freed on drop, so no C allocation outlives
    /// the Rust value that took it.
    pub fn state_save(&self) -> Result<StateBlob, i32> {
        let n = std::panic::catch_unwind(|| unsafe { gs_llama_state_size(self.raw) })
            .unwrap_or(0);
        if n <= 0 {
            return Err(GS_ERR_UNAVAILABLE);
        }
        let mut buf = vec![0u8; n as usize];
        let got = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
            gs_llama_state_save(self.raw, buf.as_mut_ptr(), n)
        }))
        .unwrap_or(0);
        if got <= 0 {
            return Err(GS_ERR_GENERATION);
        }
        buf.truncate(got as usize);
        Ok(StateBlob { buf })
    }

    /// Restore a previously saved state, discarding whatever is cached now.
    pub fn state_restore(&mut self, blob: &StateBlob) -> Result<usize, i32> {
        let got = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
            gs_llama_state_restore(self.raw, blob.buf.as_ptr(), blob.buf.len() as i64)
        }))
        .unwrap_or(0);
        if got <= 0 {
            return Err(GS_ERR_GENERATION);
        }
        Ok(got as usize)
    }

    /// Context size actually in force, used by the speculative loop.
    pub fn n_ctx(&self) -> i32 {
        std::panic::catch_unwind(|| unsafe { gs_llama_n_ctx(self.raw) }).unwrap_or(0)
    }

    /// Number of output tokens produced by the last generate() call.
    ///
    /// Reported by the C side, which counts decoded pieces. The text length is
    /// not a token count and using it as one would make tokens/sec a fiction.
    pub fn last_n_tokens(&self) -> i32 {
        self.last_n
    }

    /// Tokenise without generating. Used by demos to size a prompt.
    pub fn token_count(&self, text: &str) -> i32 {
        std::panic::catch_unwind(|| {
            let c = match std::ffi::CString::new(text) {
                Ok(c) => c,
                Err(_) => return -1,
            };
            unsafe { gs_llama_token_count(self.raw, c.as_ptr()) }
        })
        .unwrap_or(-1)
    }

    /// Layer count, for reporting cache footprint per layer.
    pub fn n_layer(&self) -> i32 {
        std::panic::catch_unwind(|| unsafe { gs_llama_n_layer(self.raw) }).unwrap_or(0)
    }

    /// Whether this build has a working accelerator offload path.
    ///
    /// Checked at load time by callers that require GPU execution. A build
    /// without it must be refused, not tolerated: silently running on the host
    /// while the operator believes the run is on the GPU is the failure mode
    /// this exists to prevent.
    pub fn gpu_offload_supported(&self) -> bool {
        std::panic::catch_unwind(|| unsafe { gs_llama_gpu_offload_supported(self.raw) != 0 })
            .unwrap_or(false)
    }

    /// Generate. The returned String is owned by Rust; the C side's `text` is
    /// released here so the C allocation never leaks.
    /// Decode `prompts` concurrently, one sequence each, in a single
    /// llama_decode per step.
    ///
    /// This is the primitive behind removing the mutex in LocalProvider. A pool
    /// of N contexts would not have helped: N contexts still submit to one GPU
    /// queue, so the submissions serialise. Batching several sequences into one
    /// decode is what actually raises aggregate throughput.
    ///
    /// The context must have been loaded with `n_seq_max >= prompts.len()`.
    /// The context is mutated, so this takes `&mut self` like `generate`.
    ///
    /// With a greedy sampler each output is identical to decoding that prompt
    /// alone; the batch changes how many tokens travel together, not which
    /// tokens are chosen.
    pub fn batch_generate(
        &mut self,
        prompts: &[String],
        max_tokens: i32,
        temperature: f32,
    ) -> Result<Vec<String>, i32> {
        if prompts.is_empty() {
            return Ok(Vec::new());
        }
        let c_prompts: Vec<std::ffi::CString> = prompts
            .iter()
            .map(|p| std::ffi::CString::new(p.as_str()).map_err(|_| GS_ERR_INVALID_ARG))
            .collect::<Result<_, _>>()?;
        let ptrs: Vec<*const c_char> = c_prompts.iter().map(|c| c.as_ptr()).collect();
        let n = ptrs.len() as c_int;
        let mut out_texts: Vec<*mut c_char> = vec![std::ptr::null_mut(); ptrs.len()];
        let mut out_lens: Vec<c_int> = vec![-1; ptrs.len()];

        let status = {
            let _guard = LLAMA_LOCK.lock().unwrap_or_else(|e| e.into_inner());
            // AssertUnwindSafe: the raw pointers handed to C are only read
            // there, and a panic must not be turned into a refusal to unwind.
            std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
                gs_llama_batch_generate(
                    self.raw,
                    ptrs.as_ptr(),
                    n,
                    max_tokens,
                    temperature,
                    out_texts.as_mut_ptr(),
                    out_lens.as_mut_ptr(),
                )
            }))
            .map_err(|_| GS_ERR_INTERNAL)?
        };

        // Reclaim every allocation the callee made before inspecting the status:
        // a partial failure still fills the slots it did finish, and leaking
        // them would turn one failed request into a slow leak per request.
        let mut out: Vec<String> = Vec::with_capacity(ptrs.len());
        for (i, p) in out_texts.iter().enumerate() {
            if p.is_null() {
                out.push(String::new());
                continue;
            }
            let owned = unsafe { CStr::from_ptr(*p) }.to_string_lossy().into_owned();
            unsafe { gs_llama_free_text(*p) };
            if out_lens[i] < 0 {
                out.push(String::new());
            } else {
                out.push(owned);
            }
        }

        if status != GS_OK {
            return Err(status);
        }
        Ok(out)
    }

    pub fn generate(&mut self, prompt: &str, max_tokens: i32, temperature: f32) -> Result<String, i32> {
        let c_prompt = std::ffi::CString::new(prompt).map_err(|_| GS_ERR_INVALID_ARG)?;

        let res = {
            let _guard = LLAMA_LOCK.lock().unwrap_or_else(|e| e.into_inner());
            std::panic::catch_unwind(|| unsafe {
                gs_llama_generate(self.raw, c_prompt.as_ptr(), max_tokens, temperature)
            })
            .map_err(|_| GS_ERR_INTERNAL)?
        };

        if res.status != GS_OK {
            // Free any partial allocation even on the error path.
            if !res.text.is_null() {
                let mut r = LlamaResult { text: res.text, n_tokens: 0, status: res.status };
                unsafe { gs_llama_free_result_text(&mut r) };
            }
            return Err(res.status);
        }
        if res.text.is_null() {
            return Err(GS_ERR_GENERATION);
        }
        self.last_n = res.n_tokens;
        let owned = unsafe { CStr::from_ptr(res.text) }.to_string_lossy().into_owned();
        let mut r = LlamaResult { text: res.text, n_tokens: res.n_tokens, status: res.status };
        unsafe { gs_llama_free_result_text(&mut r) };
        Ok(owned)
    }
}

impl Drop for LlamaModel {
    fn drop(&mut self) {
        if !self.raw.is_null() {
            let _guard = LLAMA_LOCK.lock().unwrap_or_else(|e| e.into_inner());
            let _ = std::panic::catch_unwind(|| unsafe { gs_llama_free(self.raw) });
            self.raw = std::ptr::null_mut();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_null_path_rejects_rather_than_loading() {
        // Interior NUL must be refused, not passed to C as a truncated path.
        let r = LlamaModel::load("bad\0path", 512, 4, 0);
        assert_eq!(r.err(), Some(GS_ERR_INVALID_ARG));
    }

    #[test]
    fn a_missing_model_is_an_error_not_a_handle() {
        let r = LlamaModel::load("/nonexistent/model.gguf", 512, 4, 0);
        assert!(r.is_err(), "must not fabricate a handle for a missing model");
    }

    #[test]
    fn a_real_model_loads_and_generates_from_rust() {
        // Skipped unless a model and a llama.cpp build tree are both present,
        // so the suite still runs on a bare checkout.
        let Ok(root) = std::env::var("GS_LLAMA_ROOT") else { return };
        let model_path = std::env::var("GS_TEST_MODEL").unwrap_or_else(|_| {
            "/tmp/models/qwen2.5-0.5b-instruct-q4_k_m.gguf".to_string()
        });
        if !std::path::Path::new(&model_path).exists() {
            eprintln!("skipping: no model at {model_path}");
            return;
        }
        assert!(std::path::Path::new(&root).join("include/llama.h").exists());

        let mut m = LlamaModel::load(&model_path, 512, 4, 0).expect("model loads");
        assert!(m.is_available());
        let out = m
            .generate("Say hello in one word.", 8, 0.2)
            .expect("generation succeeds from Rust");
        assert!(!out.trim().is_empty(), "must not return empty text");
    }
}
