//! Stable-diffusion wrapper bindings, plus the procedural path.
//!
//! The procedural renderer is the one that actually works today and it needs no
//! weights, no GPU and no diffusion. That is deliberate: diagrams, charts and
//! UI mockups should never require a 2.3 GB model.

use std::ffi::{c_char, c_int, CString};

pub const GS_OK: c_int = 0;

extern "C" {
    fn sd_render_svg(spec_json: *const c_char, output_path: *const c_char) -> c_int;
}

/// Render an SVG diagram from a flat JSON spec. No model required.
pub fn render_svg(spec_json: &str, output_path: &str) -> Result<(), i32> {
    let spec = CString::new(spec_json).map_err(|_| -1)?;
    let out = CString::new(output_path).map_err(|_| -1)?;
    let rc = std::panic::catch_unwind(|| unsafe { sd_render_svg(spec.as_ptr(), out.as_ptr()) })
        .map_err(|_| -7)?;
    if rc == GS_OK {
        Ok(())
    } else {
        Err(rc)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn the_procedural_path_renders_without_any_model() {
        // The claim worth testing: diagrams need no weights at all.
        let spec = r#"{"title":"Architecture","width":"800","height":"400",
                        "layer1":"orchestration","layer2":"inference"}"#;
        let out = std::env::temp_dir().join("gs-test-diagram.svg");
        let p = out.to_string_lossy().into_owned();
        match render_svg(spec, &p) {
            Ok(()) => {
                let mut f = std::fs::File::open(&out).expect("svg written");
                let mut s = String::new();
                use std::io::Read;
                f.read_to_string(&mut s).unwrap();
                assert!(s.starts_with("<svg"), "must be an svg root");
                assert!(s.contains("Architecture"), "title must appear");
                assert!(s.contains("orchestration"), "field must be rendered");
                assert!(s.trim_end().ends_with("</svg>"), "must be closed");
                let _ = std::fs::remove_file(&out);
            }
            Err(e) => panic!("procedural render failed: {e}"),
        }
    }

    #[test]
    fn an_unwritable_path_is_an_error_not_a_panic() {
        let r = render_svg(r#"{"title":"x"}"#, "/nonexistent-dir-xyz/out.svg");
        assert!(r.is_err());
    }
}
