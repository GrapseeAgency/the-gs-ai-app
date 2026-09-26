//! Skill loading and progressive disclosure (dossier Phase 9).
//!
//! Three levels, and the arithmetic is the point:
//! - L1  name + description, in the system prompt at rest  (~100 tokens/skill)
//! - L2  full body, loaded only on invocation                  (~5K tokens)
//! - L3  referenced files, loaded on demand
//!
//! A skill is only worth having if the L1 listing is far cheaper than loading
//! everything. `listing_token_cost` is capped at 1% of the context window, and
//! the test pins that.

use serde::{Deserialize, Serialize};
use std::collections::HashMap;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SkillStep {
    pub plugin: String,
    /// `$name` placeholders are substituted from the invocation input.
    pub input: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Skill {
    pub name: String,
    pub description: String,
    pub steps: Vec<SkillStep>,
    pub token_budget: u32,
}

impl Skill {
    /// L1: what the model sees without invoking. This is the whole cost of
    /// having the skill exist.
    pub fn listing(&self) -> String {
        format!("{}: {}", self.name, self.description)
    }

    pub fn listing_token_cost(&self) -> u32 {
        crate::budget::estimate_tokens(&self.listing())
    }
}

#[derive(Debug, Default, Clone)]
pub struct SkillRegistry {
    skills: HashMap<String, Skill>,
    /// L2 body, loaded on invocation. Absent means L1 only.
    bodies: HashMap<String, String>,
}

impl SkillRegistry {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn register(&mut self, skill: Skill) {
        self.skills.insert(skill.name.clone(), skill);
    }

    pub fn set_body(&mut self, name: &str, body: impl Into<String>) {
        self.bodies.insert(name.to_string(), body.into());
    }

    pub fn get(&self, name: &str) -> Option<&Skill> {
        self.skills.get(name)
    }

    pub fn names(&self) -> Vec<String> {
        let mut v: Vec<String> = self.skills.keys().cloned().collect();
        v.sort();
        v
    }

    /// L2 + L3: the full disclosure, loaded at invocation time only.
    pub fn expand(&self, name: &str) -> Option<String> {
        let s = self.skills.get(name)?;
        let mut out = s.clone();
        if let Some(b) = self.bodies.get(name) {
            out.description = format!("{}\n\n{b}", s.description);
        }
        Some(format!(
            "SKILL {}\n{}\n\nSTEPS:\n{}",
            s.name,
            out.description,
            s.steps
                .iter()
                .enumerate()
                .map(|(i, st)| format!("  {}. {} <- {}", i + 1, st.plugin, st.input))
                .collect::<Vec<_>>()
                .join("\n")
        ))
    }

    /// Token cost of the always-resident listing.
    pub fn listing_token_cost(&self) -> u32 {
        self.skills.values().map(|s| s.listing_token_cost()).sum()
    }

    /// Fraction of the context window the L1 listing consumes. The dossier
    /// caps this at 1%.
    pub fn listing_fraction(&self, context_window: u32) -> f64 {
        if context_window == 0 {
            return 1.0;
        }
        self.listing_token_cost() as f64 / context_window as f64
    }

    /// The L1 listing, HARD-capped at 1% of the context window.
    ///
    /// A cap that is only documented is not a cap. When the full listing would
    /// exceed the budget, the cheapest skills are dropped and the omission is
    /// stated, so the model knows the list is partial rather than complete.
    pub fn capped_listing(&self, context_window: u32) -> String {
        let budget = ((context_window as f64) * 0.01) as u32;
        let notice = "[further skills omitted: listing budget is 1% of the context]";
        // Reserve the omission notice BEFORE spending, or the capped listing
        // can still land over 1% -- which is how this bug happened once.
        let notice_cost = crate::budget::estimate_tokens(notice);
        let mut kept: Vec<&Skill> = self.skills.values().collect();
        kept.sort_by_key(|a| a.listing_token_cost());
        let mut lines: Vec<String> = Vec::new();
        let mut cost = 0u32;
        let mut dropped = 0usize;
        for s in kept {
            let c = s.listing_token_cost();
            // Only emit the notice if we actually dropped something.
            let fits = cost + c + notice_cost <= budget;
            if fits {
                cost += c;
                lines.push(s.listing());
            } else {
                dropped += 1;
            }
        }
        if dropped > 0 {
            cost += notice_cost;
            lines.push(notice.to_string());
        }
        debug_assert!(cost <= budget, "capped listing must fit the 1% budget");
        lines.sort();
        lines.join("\n")
    }

    /// Substitute `$name` placeholders in every step.
    pub fn resolve(&self, name: &str, vars: &HashMap<String, String>) -> Vec<SkillStep> {
        let Some(s) = self.skills.get(name) else { return Vec::new() };
        s.steps
            .iter()
            .map(|st| SkillStep {
                plugin: st.plugin.clone(),
                input: substitute(&st.input, vars),
            })
            .collect()
    }
}

fn substitute(input: &str, vars: &HashMap<String, String>) -> String {
    let mut out = input.to_string();
    for (k, v) in vars {
        out = out.replace(&format!("${k}"), v);
    }
    out
}

/// Execute a skill's steps against the tool registry. A failing step stops the
/// sequence and is reported: a skill that half-ran and claimed success is worse
/// than one that failed loudly.
pub fn execute(
    reg: &crate::tools::ToolRegistry,
    steps: &[SkillStep],
) -> Vec<crate::tools::ToolResult> {
    let mut out = Vec::new();
    for st in steps {
        let r = reg.call(&st.plugin, &st.input);
        let failed = !r.ok;
        out.push(r);
        if failed {
            break;
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::tools::{PluginManifest, ToolRegistry};
    use std::sync::Arc;

    fn skill(name: &str) -> Skill {
        Skill {
            name: name.into(),
            description: "Find, read, and synthesize research papers".into(),
            steps: vec![
                SkillStep { plugin: "web_search".into(), input: "$query".into() },
                SkillStep { plugin: "summarize".into(), input: "$text".into() },
            ],
            token_budget: 16_384,
        }
    }

    #[test]
    fn skill_loads_from_the_dossier_json_shape() {
        let j = serde_json::json!({
            "name": "research_paper",
            "description": "Find, read, and synthesize research papers",
            "steps": [
                {"plugin": "web_search", "input": "$query"},
                {"plugin": "ocr", "input": "$documents"},
                {"plugin": "summarize", "input": "$text"}
            ],
            "token_budget": 16384
        });
        let s: Skill = serde_json::from_value(j).expect("skill parses");
        assert_eq!(s.steps.len(), 3);
        assert_eq!(s.token_budget, 16_384);
    }

    #[test]
    fn a_full_manifest_stays_under_one_percent_of_context() {
        // The dossier's cap. With 24 skills listed, L1 must still be cheap.
        let mut reg = SkillRegistry::new();
        for i in 0..24 {
            reg.register(Skill {
                name: format!("skill_{i}"),
                description: "Find, read, and synthesize research papers about a topic".into(),
                steps: vec![SkillStep {
                    plugin: "web_search".into(),
                    input: "$query".into(),
                }],
                token_budget: 4096,
            });
        }
        // The raw listing exceeds 1%; the CAPPED listing must not.
        assert!(
            reg.listing_fraction(8192) > 0.01,
            "precondition: 24 verbose skills should overflow a 1% budget"
        );
        let capped = reg.capped_listing(8192);
        let used = crate::budget::estimate_tokens(&capped);
        assert!(
            used as f64 / 8192.0 <= 0.01 + 0.001,
            "capped listing used {:.3}% of an 8K window",
            used as f64 / 81.92
        );
        assert!(capped.contains("omitted"), "truncation must be stated, not silent");
    }

    #[test]
    fn the_body_is_not_paid_for_until_invocation() {
        let mut reg = SkillRegistry::new();
        reg.register(skill("research_paper"));
        let before = reg.listing_token_cost();
        reg.set_body("research_paper", "A very long body. ".repeat(500));
        // Registering the body must not change the resident cost.
        assert_eq!(reg.listing_token_cost(), before, "L2 must stay unloaded at rest");
        let expanded = reg.expand("research_paper").expect("expands");
        assert!(expanded.contains("A very long body"));
    }

    #[test]
    fn a_skill_executes_its_steps_in_order() {
        let mut tools = ToolRegistry::new();
        tools.register_manifest(PluginManifest {
            name: "web_search".into(),
            description: "d".into(),
            capabilities: vec!["search".into()],
            token_budget: 2000,
            entrypoint: "native://plugins/web_search".into(),
            timeout_ms: 10_000,
        });
        tools.register(Arc::new(crate::tools::WebSearch {
            results: vec!["doc1".into()],
        }));
        let mut reg = SkillRegistry::new();
        reg.register(skill("research_paper"));

        let mut vars = HashMap::new();
        vars.insert("query".to_string(), "paged attention".to_string());
        let steps = reg.resolve("research_paper", &vars);
        let results = execute(&tools, &steps);
        // summarize is not registered, so the sequence stops there.
        assert_eq!(results.len(), 2);
        assert!(results[0].ok);
        assert!(!results[1].ok, "must stop at the first failure");
    }

    #[test]
    fn variables_are_substituted_into_every_step() {
        let mut reg = SkillRegistry::new();
        reg.register(skill("research_paper"));
        let mut vars = HashMap::new();
        vars.insert("query".to_string(), "XYZ".into());
        vars.insert("text".to_string(), "ABC".into());
        let steps = reg.resolve("research_paper", &vars);
        assert_eq!(steps[0].input, "XYZ");
        assert_eq!(steps[1].input, "ABC");
    }

    #[test]
    fn an_unknown_skill_expands_to_nothing() {
        assert!(SkillRegistry::new().expand("nope").is_none());
    }
}
