//! Procedural image generation.
//!
//! Two paths live under `image`. `procedural` and `create` are geometry-only
//! and always available; `diffusion` is the heavyweight path. They are separate
//! on purpose so a chart never depends on a 2.3 GB model.

pub mod create;
pub mod procedural;

pub use create::{create, spec_from_request, Created};
pub use procedural::Spec;
