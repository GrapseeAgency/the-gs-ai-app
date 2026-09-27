//! Diffusion image generation, GPU-only.
//!
//! Separate from `procedural` because the two have nothing in common but the
//! output: one is arithmetic, the other is a 4 GB model on the GPU. Keeping
//! them apart is what lets a chart render in a millisecond and a photograph
//! take half a minute without either one pretending to be the other.

pub use gs_ffi::sd_bridge::{diffuse, DiffuseRequest, Diffused};

/// Generate an image, refusing any run that fell back to the host CPU.
///
/// The refusal is the point: a CPU diffusion run takes tens of minutes instead
/// of tens of seconds, and reporting it as a success is how an operator ends up
/// wondering why the machine is pinned.
pub fn run(req: &DiffuseRequest) -> Result<Diffused, String> {
    diffuse(req)
}
