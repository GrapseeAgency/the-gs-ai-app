//! OCR, safe wrapper over the Tesseract-backed C ABI.
//!
//! Ownership is explicit: `recognize` copies the C string out and releases it
//! immediately, so no pointer from the C side outlives the call. Panics cannot
//! cross the boundary because every entry point is inside `catch_unwind`.

use std::ffi::{c_char, CStr, CString};
use std::path::Path;

extern "C" {
    fn gs_ocr_last_error() -> *const c_char;
    fn gs_ocr_create() -> *mut std::ffi::c_void;
    fn gs_ocr_recognize(ctx: *mut std::ffi::c_void, image_path: *const c_char) -> *mut c_char;
    fn gs_ocr_free_string(s: *mut c_char);
    fn gs_ocr_free(ctx: *mut std::ffi::c_void);
}

#[derive(Debug)]
pub enum OcrError {
    Unavailable(String),
    Init(String),
    Read(String),
    Recognize(String),
}

impl std::fmt::Display for OcrError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            OcrError::Unavailable(m) => write!(f, "OCR unavailable: {m}"),
            OcrError::Init(m) => write!(f, "OCR init failed: {m}"),
            OcrError::Read(m) => write!(f, "OCR could not read the image: {m}"),
            OcrError::Recognize(m) => write!(f, "OCR recognition failed: {m}"),
        }
    }
}

impl std::error::Error for OcrError {}

/// The thread-local error string, copied out and never freed here.
fn last_error() -> String {
    let p = unsafe { gs_ocr_last_error() };
    if p.is_null() {
        return String::from("no error detail reported");
    }
    unsafe { CStr::from_ptr(p) }.to_string_lossy().into_owned()
}

/// Extract the text from an image, if OCR is available on this build.
///
/// Returns `None` when OCR is unavailable or the image cannot be read, so
/// callers can degrade to CLIP-only rather than pretending an image was empty.
pub fn extract_text(path: &std::path::Path) -> Option<String> {
    let m = OcrModel::new().ok()?;
    m.recognize(path).ok()
}

/// An initialised Tesseract engine.
pub struct OcrModel {
    ctx: *mut std::ffi::c_void,
}

unsafe impl Send for OcrModel {}

impl OcrModel {
    pub fn new() -> Result<Self, OcrError> {
        let ctx = std::panic::catch_unwind(|| unsafe { gs_ocr_create() })
            .map_err(|_| OcrError::Init("panic while creating the OCR context".into()))?;
        if ctx.is_null() {
            return Err(OcrError::Unavailable(last_error()));
        }
        Ok(Self { ctx })
    }

    /// Recognise the text in an image file. An image with no text yields an
    /// empty string; that is distinct from an error.
    pub fn recognize(&self, path: &Path) -> Result<String, OcrError> {
        let p = CString::new(path.to_string_lossy().as_ref())
            .map_err(|_| OcrError::Read("path contains a NUL byte".into()))?;

        let raw = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
            gs_ocr_recognize(self.ctx, p.as_ptr())
        }))
        .map_err(|_| OcrError::Recognize("panic during recognition".into()))?;

        if raw.is_null() {
            let msg = last_error();
            return Err(if msg.contains("could not read image") {
                OcrError::Read(msg)
            } else {
                OcrError::Recognize(msg)
            });
        }
        // Copy, then hand the buffer straight back to the C side.
        let text = unsafe { CStr::from_ptr(raw) }.to_string_lossy().into_owned();
        unsafe { gs_ocr_free_string(raw) };
        Ok(text)
    }
}

impl Drop for OcrModel {
    fn drop(&mut self) {
        if !self.ctx.is_null() {
            unsafe { gs_ocr_free(self.ctx) };
            self.ctx = std::ptr::null_mut();
        }
    }
}

impl std::fmt::Debug for OcrModel {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("OcrModel")
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn display_is_informative() {
        // Error text is part of the contract: an operator must be able to tell
        // "no Tesseract" from "bad image".
        assert!(OcrError::Unavailable("x".into()).to_string().contains("unavailable"));
        assert!(OcrError::Read("x".into()).to_string().contains("read"));
    }
}
