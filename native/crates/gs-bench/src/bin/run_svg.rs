//! Procedural SVG from a JSON spec on stdin.
//!
//! Usage: echo '{"type":"chart",...}' | run_svg > out.svg
//!
//! stdout is the SVG document and nothing else, so it can be piped straight
//! into a file, a browser or a rasteriser.

use gs_core::image::Spec;
use std::io::Read;
use std::process::exit;

fn main() {
    let mut input = String::new();
    if std::io::stdin().read_to_string(&mut input).is_err() {
        eprintln!("BLOCKED: could not read the spec from stdin");
        exit(1);
    }
    let spec = match Spec::parse(input.trim()) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("BLOCKED: {e}");
            exit(2);
        }
    };
    let svg = spec.render();
    print!("{svg}");
    eprintln!(
        "rendered {} kind={} elements -> {} bytes",
        if spec.kind == "chart" { format!("chart/{}", spec.chart) } else { spec.kind.clone() },
        spec.series.len() + spec.nodes.len() + spec.shapes.len(),
        svg.len()
    );
}
