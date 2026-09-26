//! gs-ffi — Rust side of the GS AI C ABI boundary.
//!
//! Everything crossing into C++ goes through here, and every entry point is
//! panic-guarded (dossier rule 4).

pub mod llama_bridge;
pub mod sd_bridge;

pub use llama_bridge::LlamaModel;
pub use sd_bridge::render_svg;
