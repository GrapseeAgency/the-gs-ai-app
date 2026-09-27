//! Image generation, two independent paths.
//!
//! `procedural` is arithmetic: charts, diagrams, logos, UI. Always available,
//! no weights, no GPU. `diffuse` is stable-diffusion.cpp: a 4 GB model, GPU
//! only, tens of seconds. They are separate modules because a chart must never
//! wait on a model, and a photograph must never be approximated with geometry.

pub mod create;
pub mod diffuse;
pub mod procedural;

pub use create::{create, spec_from_request, Created};
pub use procedural::Spec;
