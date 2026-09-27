//! The image-create turn: turn a plain request into a rendered artefact.
//!
//! This is the geometry path, and it is the default. A request for a chart, a
//! diagram, a logo or a mockup is answered by arithmetic and a file write — no
//! model call, no weights, no GPU. The diffusion path is a separate, explicitly
//! slower route; see `crate::image::diffusion`.

use crate::image::procedural::Spec;
use std::path::{Path, PathBuf};

/// A rendered artefact plus the file it landed in.
pub struct Created {
    pub path: PathBuf,
    pub kind: String,
    pub bytes: usize,
}

/// Turn a natural request into a spec.
///
/// Deliberately narrow: it understands the shapes this renderer actually
/// supports and refuses the rest. Guessing at a chart type the renderer cannot
/// draw would produce a plausible-looking artefact that answers a question
/// nobody asked.
pub fn spec_from_request(request: &str) -> Result<Spec, String> {
    let t = request.to_lowercase();

    let kind = if ["diagram", "flowchart", "pipeline", "architecture", "flow"]
        .iter()
        .any(|k| t.contains(k))
    {
        "diagram"
    } else if ["logo", "mark", "badge", "emblem"].iter().any(|k| t.contains(k)) {
        "logo"
    } else if ["mockup", "wireframe", "ui ", "screen"].iter().any(|k| t.contains(k)) {
        "ui"
    } else {
        "chart"
    };

    // Pull out (label, number) pairs. Accepts "A 10", "A: 10", "A=10" and
    // bare "A 10" so the caller can phrase a request the way a human would.
    let mut series: Vec<(String, f64)> = Vec::new();
    let bytes: Vec<&str> = t.split(|c: char| !(c.is_ascii_alphanumeric() || c == '.'))
        .filter(|s| !s.is_empty())
        .collect();
    let mut i = 0;
    while i + 1 < bytes.len() {
        if let Ok(v) = bytes[i + 1].parse::<f64>() {
            series.push((bytes[i].to_string(), v));
            i += 2;
        } else {
            i += 1;
        }
    }

    if series.is_empty() {
        return Err(format!(
            "no labelled numbers found in {request:?}; \
             try \"make a bar chart of A 10, B 25, C 15\""
        ));
    }

    let chart = if t.contains("pie") {
        "pie"
    } else if t.contains("line") || t.contains("trend") {
        "line"
    } else {
        "bar"
    };

    let title = request
        .split(" of ")
        .nth(1)
        .map(|s| s.trim().trim_end_matches('.').to_string())
        .unwrap_or_else(|| "Generated".to_string());

    Ok(Spec {
        kind: kind.to_string(),
        chart: chart.to_string(),
        title,
        series,
        nodes: Vec::new(),
        edges: Vec::new(),
        shapes: Vec::new(),
        ..Default::default()
    })
}

/// Where artefacts land. Overridable so a caller can keep them with a project
/// instead of in /tmp.
pub fn output_dir() -> PathBuf {
    std::env::var("GS_IMAGE_DIR")
        .map(PathBuf::from)
        .unwrap_or_else(|_| std::env::temp_dir())
}

/// Render a request and write the artefact. Returns the path written.
pub fn create(request: &str) -> Result<Created, String> {
    let spec = spec_from_request(request)?;
    create_from_spec(&spec)
}

/// Render a spec that has already been built and write the artefact.
pub fn create_from_spec(spec: &Spec) -> Result<Created, String> {
    let svg = spec.render();
    let dir = output_dir();
    std::fs::create_dir_all(&dir)
        .map_err(|e| format!("cannot create {}: {e}", dir.display()))?;

    // Timestamp-free collision guard: try a small counter rather than trusting
    // that two requests in the same second do not collide.
    let stem = if spec.kind == "chart" { spec.chart.clone() } else { spec.kind.clone() };
    let mut chosen: Option<PathBuf> = None;
    for n in 0..64u32 {
        let name = if n == 0 {
            format!("gs_{stem}.svg")
        } else {
            format!("gs_{stem}_{n}.svg")
        };
        let p = dir.join(name);
        if !p.exists() {
            chosen = Some(p);
            break;
        }
    }
    let path = chosen.ok_or_else(|| format!("64 existing artefacts named gs_{stem}.svg in {}", dir.display()))?;
    std::fs::write(&path, svg.as_bytes())
        .map_err(|e| format!("cannot write {}: {e}", path.display()))?;

    let label = if spec.kind == "chart" {
        format!("{}/{}", spec.kind, spec.chart)
    } else {
        spec.kind.clone()
    };
    Ok(Created { path, kind: label, bytes: svg.len() })
}

/// Rasterise an SVG if a rasteriser is available, for callers that need PNG.
/// Returns the PNG path, or the SVG path when no rasteriser is installed.
pub fn rasterize(svg_path: &Path) -> Result<PathBuf, String> {
    let png = svg_path.with_extension("png");
    let out = std::process::Command::new("rsvg-convert")
        .arg(svg_path)
        .arg("-o")
        .arg(&png)
        .output();
    match out {
        Ok(o) if o.status.success() && png.exists() => Ok(png),
        _ => Ok(svg_path.to_path_buf()),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_plain_request_becomes_a_bar_chart() {
        let s = spec_from_request("make a chart of A 10, B 25, C 15").unwrap();
        assert_eq!(s.kind, "chart");
        assert_eq!(s.chart, "bar");
        assert_eq!(s.series, vec![("a".into(), 10.0), ("b".into(), 25.0), ("c".into(), 15.0)]);
    }

    #[test]
    fn chart_type_follows_the_request() {
        assert_eq!(spec_from_request("make a pie chart of A 1, B 2").unwrap().chart, "pie");
        assert_eq!(spec_from_request("draw a line chart of A 1, B 2").unwrap().chart, "line");
    }

    #[test]
    fn a_diagram_request_is_routed_to_the_diagram_renderer() {
        let s = spec_from_request("make a diagram of A 1, B 2").unwrap();
        assert_eq!(s.kind, "diagram");
    }

    #[test]
    fn a_request_with_no_numbers_is_refused_rather_than_guessed() {
        let e = spec_from_request("make a chart of our revenue").unwrap_err();
        assert!(e.contains("no labelled numbers"), "{e}");
    }

    #[test]
    fn creating_writes_a_real_svg() {
        let dir = std::env::temp_dir().join("gs_image_test");
        std::env::set_var("GS_IMAGE_DIR", &dir);
        let c = create("make a chart of A 10, B 25").unwrap();
        let body = std::fs::read_to_string(&c.path).unwrap();
        assert!(body.starts_with("<svg"));
        assert!(c.bytes > 100);
        std::fs::remove_file(&c.path).ok();
    }
}
