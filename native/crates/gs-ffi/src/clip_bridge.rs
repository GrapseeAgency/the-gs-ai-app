//! CLIP embeddings, safe wrapper over the C ABI.
//!
//! The C side owns ONNX Runtime and the CLIP BPE tokenizer; nothing here
//! touches ONNX types. Every entry point is wrapped in `catch_unwind` so a
//! panic in this layer can never cross the FFI boundary, and a NULL context or
//! an UNAVAILABLE build is reported as a typed error rather than a crash.

use std::ffi::{c_char, c_int, c_longlong, CStr, CString};
use std::path::Path;

pub const GS_CLIP_OK: c_int = 0;

extern "C" {
    fn gs_clip_last_error() -> *const c_char;
    fn gs_clip_create(vision_path: *const c_char, text_path: *const c_char) -> *mut std::ffi::c_void;
    fn gs_clip_embed_image(
        ctx: *mut std::ffi::c_void,
        rgb: *const u8,
        w: c_int,
        h: c_int,
        out: *mut f32,
    ) -> c_int;
    fn gs_clip_embed_text(ctx: *mut std::ffi::c_void, text: *const c_char, out: *mut f32) -> c_int;
    fn gs_clip_embed_tokens(
        ctx: *mut std::ffi::c_void,
        ids: *const c_longlong,
        n: c_int,
        out: *mut f32,
    ) -> c_int;
    fn gs_clip_embed_dim(ctx: *mut std::ffi::c_void) -> c_int;
    fn gs_clip_free(ctx: *mut std::ffi::c_void);
}

/// MobileCLIP2-S0 input geometry.
pub const IMAGE_SIDE: usize = 256;

/// Whether this binary was compiled with ONNX Runtime linked in.
///
/// This is the run-time half of a fail-closed build. The build script now
/// panics when ONNX is missing, so a broken build should be impossible; this
/// check exists because that guarantee is only as good as the flag that
/// produced the binary, and a stale artifact from before the hardening, or a
/// build from a different toolchain, would otherwise fail in exactly the same
/// quiet way it used to: correct source, no vision, green tests.
pub const COMPILED_WITH_ONNX: bool = cfg!(gs_onnxruntime);

/// Abort with a loud, actionable message if the vision stack is not present.
///
/// Intended to be the first thing a vision-using binary does. A missing
/// capability is reported as an error at startup, not discovered later as an
/// empty result that looks like "the image had no text".
pub fn require_onnx_runtime(binary: &str) {
    if !COMPILED_WITH_ONNX {
        eprintln!(
            "Error: CLIP was not compiled in. Build was invoked without \
             GS_ONNXRUNTIME_ROOT.\n\
             See .cargo/config.toml.\n\
             Binary: {binary}"
        );
        std::process::exit(3);
    }
}

#[derive(Debug)]
pub enum ClipError {
    /// Built without ONNX Runtime on this machine.
    Unavailable(String),
    Load(String),
    Shape(String),
    Tokenizer(String),
    Onnx(String),
}

impl std::fmt::Display for ClipError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ClipError::Unavailable(m) => write!(f, "CLIP unavailable: {m}"),
            ClipError::Load(m) => write!(f, "CLIP load failed: {m}"),
            ClipError::Shape(m) => write!(f, "CLIP shape error: {m}"),
            ClipError::Tokenizer(m) => write!(f, "CLIP tokenizer error: {m}"),
            ClipError::Onnx(m) => write!(f, "CLIP inference failed: {m}"),
        }
    }
}

impl std::error::Error for ClipError {}

/// Copy the wrapper's last-error string. Safe: the pointer is thread-local and
/// only read, never freed here.
fn last_error() -> String {
    let p = unsafe { gs_clip_last_error() };
    if p.is_null() {
        return String::from("no error detail reported");
    }
    unsafe { CStr::from_ptr(p) }.to_string_lossy().into_owned()
}

fn map_rc(rc: c_int) -> ClipError {
    match rc {
        -1 => ClipError::Shape("bad argument".into()),
        -2 => ClipError::Load(last_error()),
        -3 => ClipError::Onnx(last_error()),
        -4 => ClipError::Shape(last_error()),
        -5 => ClipError::Tokenizer(last_error()),
        -6 => ClipError::Unavailable(last_error()),
        other => ClipError::Onnx(format!("unexpected status {other}")),
    }
}

/// A loaded CLIP pair: vision tower + text tower.
pub struct ClipModel {
    ctx: *mut std::ffi::c_void,
    dim: usize,
}

// The context owns its ONNX sessions; moving it between threads is fine because
// Ort::Session is thread-safe, but we keep the type !Send-free and simply
// document that concurrent use is safe.
unsafe impl Send for ClipModel {}

impl ClipModel {
    /// Load from a vision ONNX model and a text ONNX model. The BPE sidecars
    /// (`clip_vocab.tsv`, `clip_merges.tsv`) are read from beside the text model.
    pub fn load(vision_path: &Path, text_path: &Path) -> Result<Self, ClipError> {
        let v = CString::new(vision_path.to_string_lossy().as_ref())
            .map_err(|_| ClipError::Load("vision path contains a NUL".into()))?;
        let t = CString::new(text_path.to_string_lossy().as_ref())
            .map_err(|_| ClipError::Load("text path contains a NUL".into()))?;

        let ctx = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
            gs_clip_create(v.as_ptr(), t.as_ptr())
        }))
        .map_err(|_| ClipError::Load("panic while creating the CLIP context".into()))?;

        if ctx.is_null() {
            // The wrapper reports UNAVAILABLE (-6) as a NULL create, so the
            // message in last_error() is the useful part.
            return Err(ClipError::Unavailable(last_error()));
        }
        let dim = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
            gs_clip_embed_dim(ctx)
        }))
        .unwrap_or(0);
        if dim <= 0 {
            unsafe { gs_clip_free(ctx) };
            return Err(ClipError::Load("embedding dimension is zero".into()));
        }
        Ok(Self { ctx, dim: dim as usize })
    }

    pub fn dim(&self) -> usize {
        self.dim
    }

    /// Embed a 256x256 RGB buffer. The caller is responsible for resizing and
    /// centre-cropping to `IMAGE_SIDE`; the wrapper applies CLIP normalisation.
    pub fn embed_image(&self, rgb: &[u8]) -> Result<Vec<f32>, ClipError> {
        let want = IMAGE_SIDE * IMAGE_SIDE * 3;
        if rgb.len() != want {
            return Err(ClipError::Shape(format!(
                "expected {want} bytes for a {IMAGE_SIDE}x{IMAGE_SIDE} RGB image, got {}",
                rgb.len()
            )));
        }
        let mut out = vec![0f32; self.dim];
        let rc = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
            gs_clip_embed_image(
                self.ctx,
                rgb.as_ptr(),
                IMAGE_SIDE as c_int,
                IMAGE_SIDE as c_int,
                out.as_mut_ptr(),
            )
        }))
        .map_err(|_| ClipError::Onnx("panic during image embedding".into()))?;
        if rc != GS_CLIP_OK {
            return Err(map_rc(rc));
        }
        Ok(out)
    }

    /// Embed a query string using the in-wrapper CLIP BPE tokenizer.
    pub fn embed_text(&self, text: &str) -> Result<Vec<f32>, ClipError> {
        let t = CString::new(text)
            .map_err(|_| ClipError::Tokenizer("query contains a NUL byte".into()))?;
        let mut out = vec![0f32; self.dim];
        let rc = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
            gs_clip_embed_text(self.ctx, t.as_ptr(), out.as_mut_ptr())
        }))
        .map_err(|_| ClipError::Onnx("panic during text embedding".into()))?;
        if rc != GS_CLIP_OK {
            return Err(map_rc(rc));
        }
        Ok(out)
    }

    /// Cosine similarity of two embeddings. Both are already L2-normalised by
    /// the wrapper, so this is a dot product.
    pub fn cosine(&self, a: &[f32], b: &[f32]) -> f32 {
        let n = a.len().min(b.len());
        let mut dot = 0f32;
        let mut na = 0f32;
        let mut nb = 0f32;
        for i in 0..n {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        let denom = (na.sqrt() * nb.sqrt()).max(1e-12);
        dot / denom
    }
}

impl Drop for ClipModel {
    fn drop(&mut self) {
        if !self.ctx.is_null() {
            unsafe { gs_clip_free(self.ctx) };
            self.ctx = std::ptr::null_mut();
        }
    }
}

impl std::fmt::Debug for ClipModel {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "ClipModel(dim={})", self.dim)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn cosine_is_one_for_identical_vectors() {
        // A pure-Rust model: the maths must hold even with no ONNX present.
        let m = ClipModel { ctx: std::ptr::null_mut(), dim: 3 };
        let v = vec![0.6, 0.8, 0.0];
        assert!((m.cosine(&v, &v) - 1.0).abs() < 1e-5);
    }

    #[test]
    fn cosine_is_zero_for_orthogonal_vectors() {
        let m = ClipModel { ctx: std::ptr::null_mut(), dim: 3 };
        let a = vec![1.0, 0.0, 0.0];
        let b = vec![0.0, 1.0, 0.0];
        assert!(m.cosine(&a, &b).abs() < 1e-6);
    }

    #[test]
    fn wrong_sized_image_is_rejected_before_ffi() {
        let m = ClipModel { ctx: std::ptr::null_mut(), dim: 512 };
        assert!(m.embed_image(&[0u8; 10]).is_err());
    }
}
