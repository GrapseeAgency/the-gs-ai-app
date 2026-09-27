//! OCR over a single image file.
//!
//! Usage: run_ocr <image>
//!
//! Prints the extracted text verbatim, plus a one-line verdict on whether any
//! alphanumeric identifier was recovered. An image with no text is reported as
//! empty text; a failure is reported as an error, never as empty output.

use gs_ffi::{OcrError, OcrModel};
use std::path::PathBuf;
use std::process::exit;

fn main() {
    // Fail closed at start-up if the vision stack is not compiled in.
    gs_ffi::require_onnx_runtime("run_ocr.rs");
    let path = match std::env::args().nth(1) {
        Some(p) => PathBuf::from(p),
        None => {
            eprintln!("usage: run_ocr <image>");
            exit(2);
        }
    };

    println!("image   : {}", path.display());
    let model = match OcrModel::new() {
        Ok(m) => m,
        Err(OcrError::Unavailable(m)) => {
            eprintln!("BLOCKED: {m}");
            eprintln!("hint: install tesseract + leptonica so pkg-config finds them");
            exit(3);
        }
        Err(e) => {
            eprintln!("BLOCKED: {e}");
            exit(3);
        }
    };

    let t0 = std::time::Instant::now();
    let text = match model.recognize(&path) {
        Ok(t) => t,
        Err(e) => {
            eprintln!("BLOCKED: {e}");
            exit(1);
        }
    };
    let ms = t0.elapsed().as_millis();

    let trimmed = text.trim();
    println!("elapsed : {ms} ms");
    println!("chars   : {}", trimmed.chars().count());
    println!("--- extracted text ---");
    println!("{trimmed}");
    println!("--- end ---");

    // Verdict: did we recover anything that looks like an identifier?
    let has_alnum = trimmed.chars().any(|c| c.is_alphanumeric());
    if trimmed.is_empty() {
        println!("VERDICT: no text recovered");
        exit(4);
    }
    if has_alnum {
        println!("VERDICT: text recovered ({} bytes)", trimmed.len());
    } else {
        println!("VERDICT: only non-alphanumeric characters recovered");
        exit(5);
    }
}
