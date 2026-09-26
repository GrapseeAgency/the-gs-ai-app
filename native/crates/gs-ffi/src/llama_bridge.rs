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
}

#[repr(C)]
pub struct LlamaResult {
    pub text: *mut c_char,
    pub n_tokens: c_int,
    pub status: c_int,
}

extern "C" {
    #[allow(dead_code)]
    fn gs_llama_validate_config(config: *const LlamaConfig) -> c_int;
    fn gs_llama_create(config: *const LlamaConfig) -> *mut c_void;
    fn gs_llama_generate(
        ctx: *mut c_void,
        prompt: *const c_char,
        max_tokens: c_int,
        temperature: f32,
    ) -> LlamaResult;
    fn gs_llama_free_result_text(result: *mut LlamaResult);
    fn gs_llama_available(ctx: *mut c_void) -> c_int;
    fn gs_llama_backend_name(ctx: *mut c_void) -> *const c_char;
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
}

// The handle is only touched through &mut self on the C side, which is safe
// from Rust's perspective as long as we serialise access. The AgentLoop is
// single-threaded per request, so no lock is needed here.
unsafe impl Send for LlamaModel {}

impl LlamaModel {
    /// Load a model. Returns Err with a status code on any failure — never a
    /// half-built handle.
    pub fn load(path: &str, n_ctx: i32, n_threads: i32, n_gpu_layers: i32) -> Result<Self, i32> {
        let c_path = match std::ffi::CString::new(path) {
            Ok(c) => c,
            Err(_) => return Err(GS_ERR_INVALID_ARG), // interior NUL
        };
        let cfg = LlamaConfig {
            model_path: c_path.as_ptr(),
            n_ctx,
            n_threads,
            n_gpu_layers,
            use_mmap: 1,
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
        Ok(Self { raw: created, _path: path.to_string() })
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

    /// Generate. The returned String is owned by Rust; the C side's `text` is
    /// released here so the C allocation never leaks.
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
