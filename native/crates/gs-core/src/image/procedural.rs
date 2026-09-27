//! Procedural SVG generation — no weights, no GPU, no diffusion.
//!
//! Charts, diagrams, logos and UI mockups are geometry. Rendering them should
//! never require a 2.3 GB model, so this is pure Rust over a small JSON spec
//! and it is the path that always works.
//!
//! Spec shape:
//! ```json
//! {"type":"chart","chart":"bar","title":"Sales",
//!  "elements":[{"label":"A","value":10},{"label":"B","value":25}]}
//! ```
//! Supported: chart/bar, chart/line, chart/pie, diagram (nodes + edges),
//! logo (shapes + text), ui (labelled rectangles).

use serde_json::Value;

/// Default palette. Deliberately high-contrast on the dark background.
const PALETTE: &[&str] = &[
    "#4c8dff", "#3fb950", "#d29922", "#f85149", "#a371f7", "#39c5cf", "#db61a2",
];

#[derive(Debug, Clone)]
pub struct Spec {
    pub kind: String,
    pub chart: String,
    pub title: String,
    pub width: f64,
    pub height: f64,
    /// (label, value) pairs — the data series for charts.
    pub series: Vec<(String, f64)>,
    /// Node labels for diagrams.
    pub nodes: Vec<String>,
    /// (from, to) index pairs for diagrams.
    pub edges: Vec<(usize, usize)>,
    /// (shape kind, label) for logos and UI.
    pub shapes: Vec<(String, String)>,
}

impl Default for Spec {
    fn default() -> Self {
        Self {
            kind: "chart".into(),
            chart: "bar".into(),
            title: "Untitled".into(),
            width: 800.0,
            height: 450.0,
            series: Vec::new(),
            nodes: Vec::new(),
            edges: Vec::new(),
            shapes: Vec::new(),
        }
    }
}

fn num(v: &Value, key: &str, dflt: f64) -> f64 {
    v.get(key).and_then(|x| x.as_f64()).unwrap_or(dflt)
}

fn text(v: &Value, key: &str, dflt: &str) -> String {
    v.get(key).and_then(|x| x.as_str()).unwrap_or(dflt).to_string()
}

impl Spec {
    /// Parse a spec. Unknown fields are ignored rather than fatal: a spec is
    /// user input and a typo should not blank the canvas.
    pub fn parse(json: &str) -> Result<Self, String> {
        let v: Value = serde_json::from_str(json).map_err(|e| format!("invalid JSON: {e}"))?;
        let mut s = Spec {
            kind: text(&v, "type", "chart"),
            chart: text(&v, "chart", "bar"),
            title: text(&v, "title", "Untitled"),
            width: num(&v, "width", 800.0),
            height: num(&v, "height", 450.0),
            ..Default::default()
        };
        if s.kind.is_empty() {
            s.kind = "chart".into();
        }
        let empty = Vec::new();
        for el in v.get("elements").and_then(|e| e.as_array()).unwrap_or(&empty) {
            let label = text(el, "label", "");
            if let Some(from) = el.get("from").and_then(|x| x.as_u64()) {
                let to = el.get("to").and_then(|x| x.as_u64()).unwrap_or(0);
                s.edges.push((from as usize, to as usize));
                continue;
            }
            if let Some(kind) = el.get("kind").and_then(|x| x.as_str()) {
                s.shapes.push((kind.to_string(), label));
                continue;
            }
            if let Some(value) = el.get("value").and_then(|x| x.as_f64()) {
                s.series.push((label, value));
                continue;
            }
            if !label.is_empty() {
                s.nodes.push(label);
            }
        }
        if s.series.is_empty() && s.nodes.is_empty() && s.shapes.is_empty() {
            return Err("spec has no elements".into());
        }
        Ok(s)
    }

    /// Render to an SVG document.
    pub fn render(&self) -> String {
        match self.kind.as_str() {
            "diagram" => self.render_diagram(),
            "logo" => self.render_logo(),
            "ui" => self.render_ui(),
            _ => match self.chart.as_str() {
                "line" => self.render_line(),
                "pie" => self.render_pie(),
                _ => self.render_bar(),
            },
        }
    }

    fn header(&self) -> String {
        format!(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"{w}\" height=\"{h}\" \
             viewBox=\"0 0 {w} {h}\" role=\"img\" aria-label=\"{title}\">\n\
             <rect width=\"100%\" height=\"100%\" fill=\"#0d1117\"/>\n\
             <style>text{{font-family:ui-sans-serif,system-ui,-apple-system,sans-serif;\
             fill:#e6edf3}}</style>\n\
             <text x=\"32\" y=\"44\" font-size=\"22\" font-weight=\"600\">{title}</text>\n",
            w = self.width,
            h = self.height,
            title = escape(&self.title),
        )
    }

    fn render_bar(&self) -> String {
        let mut out = self.header();
        if self.series.is_empty() {
            out.push_str("  <text x=\"32\" y=\"90\" font-size=\"16\" fill=\"#8b949e\">no data</text>\n</svg>\n");
            return out;
        }
        let max = self.series.iter().map(|(_, v)| *v).fold(0.0f64, f64::max);
        let max = if max <= 0.0 { 1.0 } else { max };
        let top = 80.0;
        let plot_h = self.height - top - 70.0;
        let n = self.series.len() as f64;
        let gap = 18.0;
        let bar_w = ((self.width - 64.0) - gap * (n - 1.0)) / n;

        // Baseline.
        out.push_str(&format!(
            "  <line x1=\"32\" y1=\"{}\" x2=\"{}\" y2=\"{}\" stroke=\"#30363d\"/>\n",
            top + plot_h,
            self.width - 32.0,
            top + plot_h
        ));

        for (i, (label, value)) in self.series.iter().enumerate() {
            let h = (value / max) * plot_h;
            let x = 32.0 + i as f64 * (bar_w + gap);
            let y = top + plot_h - h;
            let colour = PALETTE[i % PALETTE.len()];
            out.push_str(&format!(
                "  <rect x=\"{x:.1}\" y=\"{y:.1}\" width=\"{bw:.1}\" height=\"{h:.1}\" \
                 rx=\"4\" fill=\"{c}\"/>\n",
                x = x, y = y, bw = bar_w, h = h.max(1.0), c = colour
            ));
            out.push_str(&format!(
                "  <text x=\"{:.1}\" y=\"{:.1}\" font-size=\"12\" text-anchor=\"middle\" \
                 fill=\"#8b949e\">{}</text>\n",
                x + bar_w / 2.0,
                top + plot_h + 18.0,
                escape(label)
            ));
            out.push_str(&format!(
                "  <text x=\"{:.1}\" y=\"{:.1}\" font-size=\"12\" text-anchor=\"middle\">{}</text>\n",
                x + bar_w / 2.0,
                y - 6.0,
                fmt_value(*value)
            ));
        }
        out.push_str("</svg>\n");
        out
    }

    fn render_line(&self) -> String {
        let mut out = self.header();
        if self.series.is_empty() {
            out.push_str("  <text x=\"32\" y=\"90\" font-size=\"16\" fill=\"#8b949e\">no data</text>\n</svg>\n");
            return out;
        }
        let top = 80.0;
        let plot_h = self.height - top - 70.0;
        let plot_w = self.width - 64.0;
        let max = self.series.iter().map(|(_, v)| *v).fold(0.0f64, f64::max).max(1.0);
        let n = self.series.len().max(2) as f64;
        let step = if self.series.len() > 1 { plot_w / (n - 1.0) } else { 0.0 };

        let mut pts = String::new();
        for (i, (_, v)) in self.series.iter().enumerate() {
            let x = 32.0 + i as f64 * step;
            let y = top + plot_h - (v / max) * plot_h;
            pts.push_str(&format!("{x:.1},{y:.1} "));
        }
        out.push_str(&format!(
            "  <polyline points=\"{}\" fill=\"none\" stroke=\"{}\" stroke-width=\"2.5\" \
             stroke-linejoin=\"round\"/>\n",
            pts.trim(),
            PALETTE[0]
        ));
        for (i, (label, v)) in self.series.iter().enumerate() {
            let x = 32.0 + i as f64 * step;
            let y = top + plot_h - (v / max) * plot_h;
            out.push_str(&format!("  <circle cx=\"{x:.1}\" cy=\"{y:.1}\" r=\"3.5\" fill=\"{}\"/>\n", PALETTE[0]));
            out.push_str(&format!(
                "  <text x=\"{x:.1}\" y=\"{:.1}\" font-size=\"12\" text-anchor=\"middle\" \
                 fill=\"#8b949e\">{}</text>\n",
                top + plot_h + 20.0,
                escape(label)
            ));
        }
        out.push_str("</svg>\n");
        out
    }

    fn render_pie(&self) -> String {
        let mut out = self.header();
        let total: f64 = self.series.iter().map(|(_, v)| v.max(0.0)).sum();
        if self.series.is_empty() || total <= 0.0 {
            out.push_str("  <text x=\"32\" y=\"90\" font-size=\"16\" fill=\"#8b949e\">no data</text>\n</svg>\n");
            return out;
        }
        let cx = self.width / 2.0;
        let cy = (self.height + 40.0) / 2.0;
        let r = (self.height / 2.0 - 70.0).min(self.width / 2.0 - 140.0).max(40.0);
        // Start at 12 o'clock, sweep clockwise.
        let mut angle = -std::f64::consts::FRAC_PI_2;
        for (i, (label, value)) in self.series.iter().enumerate() {
            let frac = (value.max(0.0) / total).clamp(0.0, 1.0);
            let sweep = frac * std::f64::consts::TAU;
            let a0 = angle;
            let a1 = angle + sweep;
            let large = if sweep > std::f64::consts::PI { 1 } else { 0 };
            let (x0, y0) = (cx + r * a0.cos(), cy + r * a0.sin());
            let (x1, y1) = (cx + r * a1.cos(), cy + r * a1.sin());
            out.push_str(&format!(
                "  <path d=\"M {cx:.1} {cy:.1} L {x0:.1} {y0:.1} A {r:.1} {r:.1} 0 {large} 1 \
                 {x1:.1} {y1:.1} Z\" fill=\"{c}\"/>\n",
                cx = cx, cy = cy, x0 = x0, y0 = y0, x1 = x1, y1 = y1, r = r, large = large,
                c = PALETTE[i % PALETTE.len()]
            ));
            angle = a1;
            // Legend entry to the right.
            let ly = 120.0 + i as f64 * 24.0;
            out.push_str(&format!(
                "  <rect x=\"{:.1}\" y=\"{:.1}\" width=\"12\" height=\"12\" rx=\"2\" fill=\"{}\"/>\n",
                self.width - 150.0, ly - 10.0, PALETTE[i % PALETTE.len()]
            ));
            out.push_str(&format!(
                "  <text x=\"{:.1}\" y=\"{ly:.1}\" font-size=\"12\">{} ({:.1}%)</text>\n",
                self.width - 132.0, escape(label), frac * 100.0
            ));
        }
        out.push_str("</svg>\n");
        out
    }

    fn render_diagram(&self) -> String {
        let mut out = self.header();
        let nodes = if self.nodes.is_empty() {
            self.series.iter().map(|(l, _)| l.clone()).collect::<Vec<_>>()
        } else {
            self.nodes.clone()
        };
        if nodes.is_empty() {
            out.push_str("  <text x=\"32\" y=\"90\" font-size=\"16\" fill=\"#8b949e\">no nodes</text>\n</svg>\n");
            return out;
        }
        // Vertical flow: boxes stacked, arrows between consecutive levels.
        let top = 80.0;
        let box_h = 52.0;
        let gap = 34.0;
        let box_w = (self.width - 160.0).min(420.0);
        let cx = self.width / 2.0;
        for (i, label) in nodes.iter().enumerate() {
            let y = top + i as f64 * (box_h + gap);
            out.push_str(&format!(
                "  <rect x=\"{:.1}\" y=\"{y:.1}\" width=\"{bw:.1}\" height=\"{bh:.1}\" rx=\"8\" \
                 fill=\"#161b22\" stroke=\"{c}\" stroke-width=\"1.5\"/>\n",
                cx - box_w / 2.0, y = y, bw = box_w, bh = box_h, c = PALETTE[i % PALETTE.len()]
            ));
            out.push_str(&format!(
                "  <text x=\"{cx:.1}\" y=\"{:.1}\" font-size=\"15\" text-anchor=\"middle\">{}</text>\n",
                y + box_h / 2.0 + 5.0,
                escape(label)
            ));
            if i + 1 < nodes.len() {
                let ay = y + box_h;
                let by = y + box_h + gap;
                out.push_str(&format!(
                    "  <line x1=\"{cx:.1}\" y1=\"{ay:.1}\" x2=\"{cx:.1}\" y2=\"{by:.1}\" \
                     stroke=\"#8b949e\" stroke-width=\"1.5\" marker-end=\"url(#arrow)\"/>\n",
                    cx = cx, ay = ay, by = by
                ));
            }
        }
        // Explicit edges, if the spec gave any.
        for (a, b) in &self.edges {
            if *a < nodes.len() && *b < nodes.len() {
                let ya = top + *a as f64 * (box_h + gap) + box_h;
                let yb = top + *b as f64 * (box_h + gap);
                out.push_str(&format!(
                    "  <line x1=\"{}\" y1=\"{ya:.1}\" x2=\"{}\" y2=\"{yb:.1}\" stroke=\"#3fb950\" \
                     stroke-dasharray=\"4 3\"/>\n",
                    cx + box_w / 2.0, cx + box_w / 2.0, ya = ya, yb = yb
                ));
            }
        }
        out.push_str("  <defs><marker id=\"arrow\" viewBox=\"0 0 10 10\" refX=\"9\" refY=\"5\" \
                      markerWidth=\"6\" markerHeight=\"6\" orient=\"auto-start-reverse\">\
                      <path d=\"M 0 0 L 10 5 L 0 10 z\" fill=\"#8b949e\"/></marker></defs>\n");
        out.push_str("</svg>\n");
        out
    }

    fn render_logo(&self) -> String {
        let mut out = self.header();
        let cx = self.width / 2.0;
        let cy = (self.height + 30.0) / 2.0;
        let r = (self.height / 2.0 - 60.0).min(90.0).max(30.0);
        let shapes = if self.shapes.is_empty() {
            vec![("circle".to_string(), self.title.clone())]
        } else {
            self.shapes.clone()
        };
        for (i, (kind, label)) in shapes.iter().enumerate() {
            let colour = PALETTE[i % PALETTE.len()];
            let a = i as f64 * 0.9;
            let ox = cx + a.cos() * (r * 1.6) - 30.0;
            let oy = cy + a.sin() * (r * 0.7) - 30.0;
            match kind.as_str() {
                "rect" => out.push_str(&format!(
                    "  <rect x=\"{ox:.1}\" y=\"{oy:.1}\" width=\"60\" height=\"60\" rx=\"10\" \
                     fill=\"{c}\"/>\n", ox = ox, oy = oy, c = colour
                )),
                "polygon" => out.push_str(&format!(
                    "  <polygon points=\"{x0:.1},{y0:.1} {x1:.1},{y1:.1} {x2:.1},{y2:.1}\" \
                     fill=\"{c}\"/>\n",
                    x0 = ox, y0 = oy, x1 = ox + 60.0, y1 = oy + 10.0, x2 = ox + 30.0, y2 = oy + 60.0,
                    c = colour
                )),
                _ => out.push_str(&format!(
                    "  <circle cx=\"{cx2:.1}\" cy=\"{r2:.1}\" r=\"{r2}\" fill=\"{c}\"/>\n",
                    cx2 = cx, r2 = r, c = colour
                )),
            }
            if !label.is_empty() {
                out.push_str(&format!(
                    "  <text x=\"{ox:.1}\" y=\"{:.1}\" font-size=\"12\" text-anchor=\"middle\" \
                     fill=\"#8b949e\">{}</text>\n",
                    oy + 76.0,
                    escape(label)
                ));
            }
        }
        out.push_str(&format!(
            "  <text x=\"{cx:.1}\" y=\"{}\" font-size=\"20\" text-anchor=\"middle\">{}</text>\n",
            self.height - 24.0,
            escape(&self.title)
        ));
        out.push_str("</svg>\n");
        out
    }

    fn render_ui(&self) -> String {
        let mut out = self.header();
        let top = 80.0;
        let box_h = 56.0;
        let gap = 16.0;
        let items = if self.shapes.is_empty() {
            self.nodes.clone()
        } else {
            self.shapes.iter().map(|(_, l)| l.clone()).collect::<Vec<_>>()
        };
        let series = self.series.clone();
        for (i, label) in items.iter().enumerate() {
            let y = top + i as f64 * (box_h + gap);
            out.push_str(&format!(
                "  <rect x=\"32\" y=\"{y:.1}\" width=\"{w:.1}\" height=\"{bh:.1}\" rx=\"8\" \
                 fill=\"#161b22\" stroke=\"#30363d\"/>\n",
                y = y, w = self.width - 64.0, bh = box_h
            ));
            out.push_str(&format!(
                "  <text x=\"52\" y=\"{:.1}\" font-size=\"15\">{}</text>\n",
                y + 24.0, escape(label)
            ));
            out.push_str(&format!(
                "  <rect x=\"52\" y=\"{:.1}\" width=\"{bw:.1}\" height=\"8\" rx=\"4\" \
                 fill=\"#21262d\"/>\n",
                y + 36.0,
                bw = if let Some((_, v)) = series.get(i) {
                    let max = series.iter().map(|(_, v)| *v).fold(0.0f64, f64::max).max(1.0);
                    (v / max) * (self.width - 200.0)
                } else {
                    0.0
                }
            ));
        }
        out.push_str("</svg>\n");
        out
    }
}

fn fmt_value(v: f64) -> String {
    if (v.fract()).abs() < 1e-9 {
        format!("{}", v.round() as i64)
    } else {
        format!("{v:.2}")
    }
}

/// Escape the five characters that would break the document.
fn escape(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for c in s.chars() {
        match c {
            '&' => out.push_str("&amp;"),
            '<' => out.push_str("&lt;"),
            '>' => out.push_str("&gt;"),
            '"' => out.push_str("&quot;"),
            '\'' => out.push_str("&apos;"),
            _ => out.push(c),
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_bar_chart_renders_bars_for_every_element() {
        let svg = Spec::parse(
            r#"{"type":"chart","chart":"bar","title":"T",
                "elements":[{"label":"A","value":10},{"label":"B","value":25}]}"#,
        )
        .unwrap()
        .render();
        assert!(svg.starts_with("<svg"));
        assert!(svg.trim_end().ends_with("</svg>"));
        assert_eq!(svg.matches("<rect").count(), 3, "background plus two bars");
        assert!(svg.contains(">A<") && svg.contains(">B<"));
    }

    #[test]
    fn a_line_chart_draws_a_polyline() {
        let svg = Spec::parse(
            r#"{"type":"chart","chart":"line","elements":[
                 {"label":"A","value":1},{"label":"B","value":5},{"label":"C","value":3}]}"#,
        )
        .unwrap()
        .render();
        assert!(svg.contains("<polyline"));
        assert_eq!(svg.matches("<circle").count(), 3);
    }

    #[test]
    fn a_pie_chart_draws_one_path_per_slice() {
        let svg = Spec::parse(
            r#"{"type":"chart","chart":"pie","elements":[
                 {"label":"A","value":1},{"label":"B","value":3}]}"#,
        )
        .unwrap()
        .render();
        assert_eq!(svg.matches("<path d=\"M").count(), 2);
    }

    #[test]
    fn a_diagram_draws_boxes_and_arrows() {
        let svg = Spec::parse(
            r#"{"type":"diagram","title":"Flow","elements":[
                 {"label":"start"},{"label":"work"},{"label":"done"}]}"#,
        )
        .unwrap()
        .render();
        assert!(svg.contains(">start<") && svg.contains(">done<"));
        assert!(svg.contains("marker-end"), "boxes must be connected");
    }

    #[test]
    fn values_scale_with_the_data() {
        let spec = Spec::parse(
            r#"{"type":"chart","chart":"bar","elements":[
                 {"label":"A","value":1},{"label":"B","value":2}]}"#,
        )
        .unwrap();
        let svg = spec.render();
        // The taller bar must be at least double the shorter one.
        let mut heights: Vec<f64> = Vec::new();
        for part in svg.split("<rect").skip(2) {
            if let Some(h) = part.split("height=\"").nth(1).and_then(|s| s.split('"').next()) {
                heights.push(h.parse().unwrap_or(0.0));
            }
        }
        assert_eq!(heights.len(), 2, "expected two bars, got {heights:?}");
        assert!(heights[1] > heights[0] * 1.9, "heights {heights:?}");
    }

    #[test]
    fn markup_in_a_label_cannot_break_the_document() {
        let svg = Spec::parse(
            r#"{"type":"chart","chart":"bar","elements":[
                 {"label":"</text><script>alert(1)</script>","value":1}]}"#,
        )
        .unwrap()
        .render();
        assert!(!svg.contains("<script>"));
        assert!(svg.contains("&lt;script&gt;"));
    }

    #[test]
    fn an_empty_spec_is_rejected_rather_than_blank() {
        assert!(Spec::parse(r#"{"type":"chart","elements":[]}"#).is_err());
        assert!(Spec::parse("not json").is_err());
    }
}
