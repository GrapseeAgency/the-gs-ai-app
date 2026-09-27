//! gs-ffi — Rust side of the GS AI C ABI boundary.
//!
//! Everything crossing into C++ goes through here, and every entry point is
//! panic-guarded (dossier rule 4).

pub mod clip_bridge;
pub mod llama_bridge;
pub mod ocr_bridge;
pub mod sd_bridge;

pub use clip_bridge::{ClipError, ClipModel, IMAGE_SIDE};
pub use llama_bridge::{KvType, LlamaModel, LlamaConfig, SpecStats, StateBlob};
pub use ocr_bridge::{extract_text, OcrError, OcrModel};
pub use sd_bridge::render_svg;
