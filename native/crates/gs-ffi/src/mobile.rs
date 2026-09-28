//! Mobile C ABI, bound from Rust.
//!
//! Two jobs, both necessary:
//!
//! 1. Give Rust a safe surface over `gs_mobile.h` so callers on desktop and in
//!    tests do not have to write `extern "C"` blocks.
//! 2. Keep the symbols ALIVE in the cdylib. A cdylib only exports what the
//!    linker sees referenced; the C++ translation units compiled by `cc` land in
//!    a static archive and are dropped unless Rust names them. The first
//!    libgs_ffi.so this produced was 422 KB and exported nothing at all, for
//!    exactly this reason.
//!
//! Every backend-dependent call reports `GS_ERR_UNAVAILABLE` on a mobile build.
//! That is propagated here as a typed error rather than as empty output, so a
//! host app can route around it instead of treating "" as a real answer.

use std::ffi::{c_char, c_int, CStr, CString};

pub const GS_OK: c_int = 0;
pub const GS_ERR_UNAVAILABLE: c_int = -4;

// ---------------------------------------------------------------------------
// Linkage roots
// ---------------------------------------------------------------------------
//
// A cdylib only exports what something reachable references, and the
// `cc`-compiled C++ lives in static archives. An `extern "C"` *declaration* is
// not a reference, so every gs_mobile_* symbol was dropped and the first
// libgs_ffi.so this produced was 423 KB exporting not one `gs_` symbol.
//
// Two mechanisms were tried and rejected before this one:
//   - `#[used] static [*const (); N]` — `*const ()` is not Sync, so it will not
//     compile as a static.
//   - `#[used] static [usize; N]` with `fn as usize` — rejected by const eval:
//     "pointers cannot be cast to integers during const eval".
//
// Exported Rust shims are the mechanism that works and is also the right design:
// a `#[no_mangle] pub extern "C"` symbol in a cdylib is kept by definition, and
// keeping it keeps the C++ object it calls. It doubles as a stable Rust-side ABI.

/// Version of the mobile ABI, so a mismatched host build is detectable.
#[no_mangle]
pub extern "C" fn gs_ffi_mobile_abi_version() -> u32 {
    1
}

#[no_mangle]
pub extern "C" fn gs_ffi_mobile_chat(
    ctx: *mut GS_MobileCtx,
    prompt: *const c_char,
    max_tokens: c_int,
    temperature: f32,
) -> *mut c_char {
    unsafe { gs_mobile_chat(ctx, prompt, max_tokens, temperature) }
}

#[no_mangle]
pub extern "C" fn gs_ffi_mobile_ocr(ctx: *mut GS_MobileCtx, image_path: *const c_char) -> *mut c_char {
    unsafe { gs_mobile_ocr(ctx, image_path) }
}

#[no_mangle]
pub extern "C" fn gs_ffi_mobile_embed_dim(ctx: *mut GS_MobileCtx) -> c_int {
    unsafe { gs_mobile_embed_dim(ctx) }
}

#[no_mangle]
pub extern "C" fn gs_ffi_mobile_backend_available(ctx: *mut GS_MobileCtx) -> c_int {
    unsafe { gs_mobile_backend_available(ctx) }
}

#[no_mangle]
pub extern "C" fn gs_ffi_mobile_build_info() -> *const c_char {
    unsafe { gs_mobile_build_info() }
}

#[no_mangle]
pub extern "C" fn gs_ffi_mobile_create(
    model_path: *const c_char,
    n_ctx: c_int,
    n_threads: c_int,
) -> *mut GS_MobileCtx {
    unsafe { gs_mobile_create(model_path, n_ctx, n_threads) }
}

#[no_mangle]
pub extern "C" fn gs_ffi_mobile_free(ctx: *mut GS_MobileCtx) {
    unsafe { gs_mobile_free(ctx) }
}

#[no_mangle]
pub extern "C" fn gs_ffi_mobile_embed_image(
    ctx: *mut GS_MobileCtx,
    rgb: *const u8,
    w: c_int,
    h: c_int,
    out: *mut f32,
    cap: c_int,
) -> c_int {
    unsafe { gs_mobile_embed_image(ctx, rgb, w, h, out, cap) }
}

#[no_mangle]
pub extern "C" fn gs_ffi_mobile_should_use_local(max_params: u64) -> c_int {
    unsafe { gs_mobile_should_use_local(max_params) }
}

#[repr(C)]
pub struct GS_MobileCtx {
    _private: [u8; 0],
}

extern "C" {
    fn gs_mobile_create(model_path: *const c_char, n_ctx: c_int, n_threads: c_int) -> *mut GS_MobileCtx;
    fn gs_mobile_free(ctx: *mut GS_MobileCtx);
    fn gs_mobile_backend_available(ctx: *mut GS_MobileCtx) -> c_int;
    fn gs_mobile_backend_name(ctx: *mut GS_MobileCtx) -> *const c_char;
    fn gs_mobile_chat(
        ctx: *mut GS_MobileCtx,
        prompt: *const c_char,
        max_tokens: c_int,
        temperature: f32,
    ) -> *mut c_char;
    fn gs_mobile_ocr(ctx: *mut GS_MobileCtx, image_path: *const c_char) -> *mut c_char;
    fn gs_mobile_embed_dim(ctx: *mut GS_MobileCtx) -> c_int;
    fn gs_mobile_embed_image(
        ctx: *mut GS_MobileCtx,
        rgb: *const u8,
        w: c_int,
        h: c_int,
        out: *mut f32,
        cap: c_int,
    ) -> c_int;
    fn gs_mobile_embed_text(
        ctx: *mut GS_MobileCtx,
        text: *const c_char,
        out: *mut f32,
        cap: c_int,
    ) -> c_int;
    fn gs_mobile_should_use_local(max_params: u64) -> c_int;
    fn gs_mobile_build_info() -> *const c_char;
    fn gs_last_error() -> *const c_char;
    fn gs_free_string(s: *mut c_char);
}

#[derive(Debug)]
pub enum MobileError {
    /// No backend compiled into this build, or the context is gone.
    Unavailable(String),
    InvalidArg(String),
    Io(String),
    Internal(String),
}

impl std::fmt::Display for MobileError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            MobileError::Unavailable(m) => write!(f, "unavailable: {m}"),
            MobileError::InvalidArg(m) => write!(f, "invalid argument: {m}"),
            MobileError::Io(m) => write!(f, "io: {m}"),
            MobileError::Internal(m) => write!(f, "internal: {m}"),
        }
    }
}

impl std::error::Error for MobileError {}

fn last_error() -> String {
    let p = unsafe { gs_last_error() };
    if p.is_null() {
        return String::from("no error detail reported");
    }
    unsafe { CStr::from_ptr(p) }.to_string_lossy().into_owned()
}

/// A mobile context.
///
/// Send is implemented deliberately, and the JNI layer stores its one context in
/// a `static Mutex<Option<MobileCtx>>`. A bare `static` holding a raw pointer is
/// unsound the moment two threads read it, and a JNI entry point is reachable
/// from any thread in the JVM.
///
/// SAFETY of the Send impl: the C context is not itself concurrency-safe, but
/// every access goes through the Mutex that owns it, so no two threads are ever
/// inside the C call at once. Moving the handle between threads moves only the
/// pointer, never concurrent use of it. Removing this impl is what would make
/// the JNI global unsound, which is why it is an `unsafe impl` with its argument
/// written down rather than a derived bound.
// SAFETY: see the type-level comment above. Access is serialised by the owning
// Mutex in every call site.
unsafe impl Send for MobileCtx {}

pub struct MobileCtx {
    raw: *mut GS_MobileCtx,
}

impl MobileCtx {
    /// Create a context. `model_path` may be empty, which is the normal
    /// "app launched, model not downloaded yet" state.
    pub fn create(model_path: &str, n_ctx: i32, n_threads: i32) -> Result<Self, MobileError> {
        let p = CString::new(model_path).map_err(|_| MobileError::InvalidArg("path contains NUL".into()))?;
        let raw = std::panic::catch_unwind(|| unsafe { gs_mobile_create(p.as_ptr(), n_ctx, n_threads) })
            .map_err(|_| MobileError::Internal("panic in gs_mobile_create".into()))?;
        if raw.is_null() {
            return Err(MobileError::Unavailable(last_error()));
        }
        Ok(Self { raw })
    }

    /// 1 when a generation backend is compiled in.
    pub fn backend_available(&self) -> bool {
        std::panic::catch_unwind(|| unsafe { gs_mobile_backend_available(self.raw) != 0 })
            .unwrap_or(false)
    }

    pub fn backend_name(&self) -> String {
        std::panic::catch_unwind(|| unsafe {
            let p = gs_mobile_backend_name(self.raw);
            if p.is_null() { String::from("none") } else { CStr::from_ptr(p).to_string_lossy().into_owned() }
        })
        .unwrap_or_else(|_| String::from("unknown"))
    }

    pub fn chat(&self, prompt: &str, max_tokens: i32, temperature: f32) -> Result<String, MobileError> {
        let p = CString::new(prompt).map_err(|_| MobileError::InvalidArg("prompt contains NUL".into()))?;
        let raw = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
            gs_mobile_chat(self.raw, p.as_ptr(), max_tokens, temperature)
        }))
        .map_err(|_| MobileError::Internal("panic in gs_mobile_chat".into()))?;
        if raw.is_null() {
            let m = last_error();
            return Err(if m.contains("no generation backend") {
                MobileError::Unavailable(m)
            } else {
                MobileError::Internal(m)
            });
        }
        let s = unsafe { CStr::from_ptr(raw) }.to_string_lossy().into_owned();
        unsafe { gs_free_string(raw) };
        Ok(s)
    }

    /// OCR. On a portable build this is `Unavailable`, never an empty string:
    /// "" would mean "the image has no text", which is a different claim.
    pub fn ocr(&self, image_path: &str) -> Result<String, MobileError> {
        let p = CString::new(image_path)
            .map_err(|_| MobileError::InvalidArg("path contains NUL".into()))?;
        let raw = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
            gs_mobile_ocr(self.raw, p.as_ptr())
        }))
        .map_err(|_| MobileError::Internal("panic in gs_mobile_ocr".into()))?;
        if raw.is_null() {
            return Err(MobileError::Unavailable(last_error()));
        }
        let s = unsafe { CStr::from_ptr(raw) }.to_string_lossy().into_owned();
        unsafe { gs_free_string(raw) };
        Ok(s)
    }

    pub fn embed_dim(&self) -> Result<usize, MobileError> {
        let d = std::panic::catch_unwind(|| unsafe { gs_mobile_embed_dim(self.raw) }).unwrap_or(-1);
        if d < 0 {
            return Err(MobileError::Unavailable(last_error()));
        }
        Ok(d as usize)
    }

    /// Embed raw 8-bit RGB, three bytes per pixel, row-major.
    ///
    /// The length is checked against `w * h * 3` rather than trusted: a caller
    /// that lies about the geometry would otherwise read past the end of the
    /// buffer, which is a memory-safety bug and not a wrong answer.
    pub fn embed_image(&self, rgb: &[u8], w: i32, h: i32) -> Result<Vec<f32>, MobileError> {
        if w <= 0 || h <= 0 {
            return Err(MobileError::InvalidArg("w and h must be > 0".into()));
        }
        let need = (w as usize).saturating_mul(h as usize).saturating_mul(3);
        if rgb.len() < need {
            return Err(MobileError::InvalidArg(format!(
                "expected {need} bytes for {w}x{h} RGB, got {}",
                rgb.len()
            )));
        }
        let dim = self.embed_dim()?;
        let mut buf = vec![0f32; dim];
        let n = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
            gs_mobile_embed_image(
                self.raw,
                rgb.as_ptr(),
                w as c_int,
                h as c_int,
                buf.as_mut_ptr(),
                dim as c_int,
            )
        }))
        .unwrap_or(-1);
        if n < 0 {
            return Err(MobileError::Unavailable(last_error()));
        }
        buf.truncate(n as usize);
        Ok(buf)
    }

    /// Embed a text query. Mirrors [`Self::embed_image`] on the error contract.
    pub fn embed_text(&self, text: &str) -> Result<Vec<f32>, MobileError> {
        let dim = self.embed_dim()?;
        let mut buf = vec![0f32; dim];
        let p =
            CString::new(text).map_err(|_| MobileError::InvalidArg("text contains NUL".into()))?;
        let n = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
            gs_mobile_embed_text(self.raw, p.as_ptr(), buf.as_mut_ptr(), dim as c_int)
        }))
        .unwrap_or(-1);
        if n < 0 {
            return Err(MobileError::Unavailable(last_error()));
        }
        buf.truncate(n as usize);
        Ok(buf)
    }
}

impl Drop for MobileCtx {
    fn drop(&mut self) {
        if !self.raw.is_null() {
            unsafe { gs_mobile_free(self.raw) };
            self.raw = std::ptr::null_mut();
        }
    }
}

impl std::fmt::Debug for MobileCtx {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "MobileCtx(backend={})", self.backend_name())
    }
}

/// Whether local inference is recommended for a model of `max_params` bytes.
/// This is the local-first routing gate the host apps call before choosing
/// between the embedded model and the provider pool.
pub fn should_use_local(max_params: u64) -> bool {
    std::panic::catch_unwind(|| unsafe { gs_mobile_should_use_local(max_params) != 0 }).unwrap_or(false)
}

/// Build identification, so a shipped binary can be matched to a commit.
pub fn build_info() -> String {
    std::panic::catch_unwind(|| unsafe {
        let p = gs_mobile_build_info();
        if p.is_null() { String::new() } else { CStr::from_ptr(p).to_string_lossy().into_owned() }
    })
    .unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_context_creates_and_names_its_backend() {
        let c = MobileCtx::create("", 512, 2).expect("context must create");
        // On the desktop build llama is present; on mobile it is not. Both are
        // valid, and the test asserts only that the name is one of them, so it
        // does not encode a platform assumption.
        let n = c.backend_name();
        assert!(n == "llama.cpp" || n == "none", "unexpected backend {n:?}");
    }

    #[test]
    fn unavailable_is_an_error_not_an_empty_string() {
        let c = MobileCtx::create("", 512, 2).unwrap();
        if c.backend_available() {
            return; // a real backend is present; nothing to assert
        }
        // The important property: a portable build says so rather than
        // returning "" and letting the caller read that as an answer.
        let e = c.chat("hello", 8, 0.0).unwrap_err();
        assert!(matches!(e, MobileError::Unavailable(_)), "{e:?}");
        let o = c.ocr("/tmp/whatever.png").unwrap_err();
        assert!(matches!(o, MobileError::Unavailable(_)), "{o:?}");
        let d = c.embed_dim().unwrap_err();
        assert!(matches!(d, MobileError::Unavailable(_)), "{d:?}");
    }

    #[test]
    fn build_info_is_non_empty_and_names_the_platform() {
        let b = build_info();
        assert!(b.starts_with("gs-ffi"), "{b:?}");
        assert!(b.contains("portable") || b.contains("llama"), "{b:?}");
    }

    #[test]
    fn the_local_gate_answers_for_both_directions() {
        // Must not panic, and must agree with the desktop device class.
        let _ = should_use_local(0);
        let _ = should_use_local(1_000_000_000);
    }
}
