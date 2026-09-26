//! Tool (plugin) registry with manifest loading and crash isolation
//! (dossier Phase 9).
//!
//! Isolation model: each plugin call is wrapped in `catch_unwind`. A plugin
//! that panics yields a structured error and the system continues. This is
//! in-process isolation — process-level isolation is a later step — but it is
//! the difference between one bad plugin ending a turn and ending the server.

use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::sync::{Arc, Mutex};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginManifest {
    pub name: String,
    pub description: String,
    #[serde(default)]
    pub capabilities: Vec<String>,
    pub token_budget: u32,
    pub entrypoint: String,
    pub timeout_ms: u64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ToolResult {
    pub plugin: String,
    pub ok: bool,
    pub output: String,
    pub tokens: u32,
    /// Set when the plugin panicked. The turn continues.
    pub isolated_panic: bool,
}

pub trait Tool: Send + Sync {
    fn name(&self) -> &str;
    fn run(&self, input: &str) -> Result<String, String>;
}

pub struct ToolRegistry {
    tools: HashMap<String, Arc<dyn Tool>>,
    manifests: HashMap<String, PluginManifest>,
    /// Per-plugin panic counts, so a repeatedly crashing plugin can be
    /// quarantined rather than retried forever.
    panics: Mutex<HashMap<String, u32>>,
    pub panic_limit: u32,
}

impl Default for ToolRegistry {
    fn default() -> Self {
        Self::new()
    }
}

impl ToolRegistry {
    pub fn new() -> Self {
        Self {
            tools: HashMap::new(),
            manifests: HashMap::new(),
            panics: Mutex::new(HashMap::new()),
            panic_limit: 3,
        }
    }

    pub fn register_manifest(&mut self, m: PluginManifest) {
        self.manifests.insert(m.name.clone(), m);
    }

    pub fn register(&mut self, tool: Arc<dyn Tool>) {
        self.tools.insert(tool.name().to_string(), tool);
    }

    /// Look up a loaded manifest. Used by the server to report plugin
    /// capabilities on /health.
    pub fn manifest(&self, name: &str) -> Option<&PluginManifest> {
        self.manifests.get(name)
    }

    pub fn has(&self, name: &str) -> bool {
        self.tools.contains_key(name)
    }

    pub fn names(&self) -> Vec<String> {
        let mut v: Vec<String> = self.tools.keys().cloned().collect();
        v.sort();
        v
    }

    pub fn panic_count(&self, name: &str) -> u32 {
        self.panics.lock().unwrap().get(name).copied().unwrap_or(0)
    }

    /// True once a plugin has panicked too many times to be worth calling.
    pub fn is_quarantined(&self, name: &str) -> bool {
        self.panic_count(name) >= self.panic_limit
    }

    /// Call a plugin with panic isolation. A panic becomes a structured
    /// ToolResult, never a propagated unwind that would kill the caller.
    pub fn call(&self, name: &str, input: &str) -> ToolResult {
        let Some(tool) = self.tools.get(name) else {
            return ToolResult {
                plugin: name.to_string(),
                ok: false,
                output: format!("plugin '{name}' is not registered"),
                tokens: 0,
                isolated_panic: false,
            };
        };
        if self.is_quarantined(name) {
            return ToolResult {
                plugin: name.to_string(),
                ok: false,
                output: format!("plugin '{name}' quarantined after repeated panics"),
                tokens: 0,
                isolated_panic: false,
            };
        }

        let tool = tool.clone();
        let input_owned = input.to_string();
        let outcome = catch_unwind(AssertUnwindSafe(move || tool.run(&input_owned)));

        match outcome {
            Ok(Ok(out)) => ToolResult {
                plugin: name.to_string(),
                ok: true,
                tokens: crate::budget::estimate_tokens(&out),
                output: out,
                isolated_panic: false,
            },
            Ok(Err(e)) => ToolResult {
                plugin: name.to_string(),
                ok: false,
                output: e,
                tokens: 0,
                isolated_panic: false,
            },
            Err(_) => {
                *self.panics.lock().unwrap().entry(name.to_string()).or_insert(0) += 1;
                ToolResult {
                    plugin: name.to_string(),
                    ok: false,
                    output: format!("plugin '{name}' panicked and was isolated"),
                    tokens: 0,
                    isolated_panic: true,
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Built-in tools
// ---------------------------------------------------------------------------

/// Exact integer arithmetic. Never calls a model, so it cannot be wrong in
/// the way a model is — which is the whole reason it exists as a tool.
pub struct Calc;

impl Tool for Calc {
    fn name(&self) -> &str {
        "calc"
    }
    fn run(&self, input: &str) -> Result<String, String> {
        let expr = input.trim();
        if expr.is_empty() {
            return Err("empty expression".into());
        }
        // Only digits, spaces and + - * / ( ). Anything else is refused rather
        // than partially evaluated.
        if !expr
            .chars()
            .all(|c| c.is_ascii_digit() || c.is_whitespace() || "+-*/()".contains(c))
        {
            return Err("expression contains unsupported characters".into());
        }
        // Reject unbalanced parentheses before doing anything.
        let mut depth = 0i32;
        for c in expr.chars() {
            if c == '(' {
                depth += 1;
            }
            if c == ')' {
                depth -= 1;
                if depth < 0 {
                    return Err("unbalanced parentheses".into());
                }
            }
        }
        if depth != 0 {
            return Err("unbalanced parentheses".into());
        }
        // Only single binary operations are evaluated; anything more complex
        // is declined rather than approximated.
        let toks: Vec<&str> = expr.split_whitespace().collect();
        if toks.len() != 3 {
            return Err("only 'a op b' is supported".into());
        }
        let a: f64 = toks[0].parse().map_err(|_| "bad left operand")?;
        let b: f64 = toks[2].parse().map_err(|_| "bad right operand")?;
        let v = match toks[1] {
            "+" => a + b,
            "-" => a - b,
            "*" => a * b,
            "/" if b != 0.0 => a / b,
            "/" => return Err("division by zero".into()),
            _ => return Err(format!("unsupported operator '{}'", toks[1])),
        };
        // Print as an integer when it is one, so "2 + 2" is "4" not "4.0".
        if (v.fract()).abs() < f64::EPSILON {
            Ok(format!("{}", v as i64))
        } else {
            Ok(format!("{v}"))
        }
    }
}

pub struct WebSearch {
    pub results: Vec<String>,
}

impl Tool for WebSearch {
    fn name(&self) -> &str {
        "web_search"
    }
    fn run(&self, input: &str) -> Result<String, String> {
        if self.results.is_empty() {
            return Err("no search backend configured".into());
        }
        Ok(format!("results for {:?}: {}", input, self.results.join(" | ")))
    }
}

pub struct Ocr;

impl Tool for Ocr {
    fn name(&self) -> &str {
        "ocr"
    }
    fn run(&self, _input: &str) -> Result<String, String> {
        // Honest: the OCR engine is not linked. Returning empty text here
        // would be indistinguishable from "the image had no text".
        Err("ocr backend not linked: Paddle-Lite requires an NDK cross-compile".into())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[allow(dead_code)]
    fn manifest(name: &str) -> PluginManifest {
        PluginManifest {
            name: name.into(),
            description: "d".into(),
            capabilities: vec!["search".into()],
            token_budget: 2000,
            entrypoint: format!("native://plugins/{name}"),
            timeout_ms: 10_000,
        }
    }

    #[test]
    fn manifest_loads_from_the_dossier_json_shape() {
        let j = json!({
            "name": "web_search",
            "description": "Search the web for current information",
            "capabilities": ["search"],
            "token_budget": 2000,
            "entrypoint": "native://plugins/web_search",
            "timeout_ms": 10000
        });
        let m: PluginManifest = serde_json::from_value(j).expect("manifest parses");
        assert_eq!(m.name, "web_search");
        assert_eq!(m.token_budget, 2000);
        assert_eq!(m.timeout_ms, 10_000);
    }

    #[test]
    fn an_unregistered_plugin_fails_cleanly() {
        let r = ToolRegistry::new().call("nope", "x");
        assert!(!r.ok);
        assert!(r.output.contains("not registered"));
    }

    #[test]
    fn a_panicking_plugin_is_isolated_not_propagated() {
        struct Boom;
        impl Tool for Boom {
            fn name(&self) -> &str {
                "boom"
            }
            fn run(&self, _i: &str) -> Result<String, String> {
                panic!("plugin exploded");
            }
        }
        let mut reg = ToolRegistry::new();
        reg.register(Arc::new(Boom));
        // The whole point: this must return, not unwind into the caller.
        let r = reg.call("boom", "x");
        assert!(!r.ok);
        assert!(r.isolated_panic);
        assert_eq!(reg.panic_count("boom"), 1);
    }

    #[test]
    fn a_repeatedly_panicking_plugin_is_quarantined() {
        struct Boom;
        impl Tool for Boom {
            fn name(&self) -> &str {
                "boom"
            }
            fn run(&self, _i: &str) -> Result<String, String> {
                panic!("again");
            }
        }
        let mut reg = ToolRegistry::new();
        reg.register(Arc::new(Boom));
        for _ in 0..3 {
            reg.call("boom", "x");
        }
        assert!(reg.is_quarantined("boom"));
        let r = reg.call("boom", "x");
        assert!(!r.isolated_panic, "quarantined plugins are not re-run");
        assert!(r.output.contains("quarantined"));
    }

    #[test]
    fn calc_is_exact_and_refuses_anything_else() {
        let c = Calc;
        assert_eq!(c.run("2 + 2").unwrap(), "4");
        assert_eq!(c.run("10 / 4").unwrap(), "2.5");
        assert_eq!(c.run("7 * 6").unwrap(), "42");
        // Refusals, not guesses.
        assert!(c.run("drop table").is_err());
        assert!(c.run("2 +").is_err());
        assert!(c.run("(1 + 2)").is_err());
        assert!(c.run("1 / 0").is_err());
        assert!(c.run("").is_err());
    }

    #[test]
    fn ocr_reports_blocked_rather_than_returning_empty_text() {
        let mut reg = ToolRegistry::new();
        reg.register(Arc::new(Ocr));
        let out = reg.call("ocr", "/tmp/x.png");
        assert!(!out.ok);
        // Must say why, not look like "no text found".
        assert!(out.output.contains("not linked"), "got: {}", out.output);
    }

    #[test]
    fn registry_lists_tools_deterministically() {
        let mut reg = ToolRegistry::new();
        reg.register(Arc::new(Calc));
        reg.register(Arc::new(Ocr));
        assert_eq!(reg.names(), vec!["calc", "ocr"]);
    }
}
