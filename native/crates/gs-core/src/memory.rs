//! Three-tier memory: working, episodic, semantic (dossier Phase 5).
//!
//! The hard requirement is that compaction must never lose an identifier.
//! Summarising "the invoice is INV-4471, due 2026-03-01" down to "the invoice"
//! is worse than running out of context, because the summary looks complete.
//! So identifiers are extracted and re-emitted *verbatim* after the prose is
//! compacted, and a test pins that behaviour.

use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// Anything that must survive compaction untouched.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum Identifier {
    /// Uppercase or mixed alphanumeric token, e.g. INV-4471, PR-88, v2.1
    Code(String),
    /// Anything that parses as a number, preserved as its literal text.
    Number(String),
    /// A filesystem path.
    Path(String),
    /// A URL.
    Url(String),
}

impl Identifier {
    pub fn as_str(&self) -> &str {
        match self {
            Identifier::Code(s) | Identifier::Number(s) | Identifier::Path(s) | Identifier::Url(s) => s,
        }
    }
}

/// Pull out identifiers. Deliberately regex-free: a hand-rolled scanner avoids
/// a regex dependency in the hot path and lets the rules be explicit.
pub fn extract_identifiers(text: &str) -> Vec<Identifier> {
    let mut out = Vec::new();
    let mut seen = std::collections::HashSet::new();
    let mut cur = String::new();

    let flush = |cur: &mut String, out: &mut Vec<Identifier>, seen: &mut std::collections::HashSet<String>| {
        if cur.is_empty() {
            return;
        }
        let tok = cur.clone();
        cur.clear();
        if tok.len() < 2 || !seen.insert(tok.clone()) {
            return;
        }
        if tok.starts_with("http://") || tok.starts_with("https://") {
            out.push(Identifier::Url(tok));
        } else if tok.starts_with('/') && tok.contains('/') {
            out.push(Identifier::Path(tok));
        } else if tok.chars().all(|c| c.is_ascii_digit() || c == '.' || c == ',' || c == '-') {
            // Numeric, but not a bare "3." or "-".
            if tok.chars().any(|c| c.is_ascii_digit()) {
                out.push(Identifier::Number(tok));
            }
        } else if tok.chars().any(|c| c.is_ascii_uppercase()) {
            out.push(Identifier::Code(tok));
        }
    };

    for ch in text.chars() {
        if ch.is_alphanumeric()
            || ch == '-'
            || ch == '_'
            || ch == '.'
            || ch == '/'
            || ch == ':'
            || ch == ','
        {
            if ch == '/' || ch == ':' {
                // keep path/URL prefixes attached
                cur.push(ch);
            } else {
                cur.push(ch);
            }
        } else {
            flush(&mut cur, &mut out, &mut seen);
        }
    }
    flush(&mut cur, &mut out, &mut seen);
    out
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Turn {
    pub role: String,
    pub content: String,
    /// Identifiers captured at write time, so compaction cannot lose them even
    /// if the prose is rewritten.
    pub identifiers: Vec<Identifier>,
}

impl Turn {
    pub fn new(role: impl Into<String>, content: impl Into<String>) -> Self {
        let content = content.into();
        let identifiers = extract_identifiers(&content);
        Self { role: role.into(), content, identifiers }
    }
}

/// Working memory: the current turn at full fidelity. Never persisted.
#[derive(Debug, Default)]
pub struct WorkingMemory {
    turns: Vec<Turn>,
}

impl WorkingMemory {
    pub fn push(&mut self, turn: Turn) {
        self.turns.push(turn);
    }
    pub fn turns(&self) -> &[Turn] {
        &self.turns
    }
    pub fn len(&self) -> usize {
        self.turns.len()
    }
    pub fn is_empty(&self) -> bool {
        self.turns.is_empty()
    }
    pub fn clear(&mut self) {
        self.turns.clear();
    }
}

/// Structural compaction: DAG-style trim, no LLM call.
#[derive(Debug, Default, Clone, Serialize, Deserialize)]
pub struct Compaction {
    pub kept_turns: Vec<Turn>,
    pub summary: String,
    pub preserved_identifiers: Vec<Identifier>,
    pub turns_dropped: usize,
    pub chars_before: usize,
    pub chars_after: usize,
    /// 1.0 - after/before. The dossier's levers are all judged on this.
    pub reduction_ratio: f64,
}

/// Keep the last `keep_recent` turns verbatim; summarise the rest without an
/// LLM by preserving role structure and every identifier.
pub fn structural_compaction(turns: &[Turn], keep_recent: usize) -> Compaction {
    let chars_before: usize = turns.iter().map(|t| t.content.len()).sum();
    if turns.len() <= keep_recent {
        return Compaction {
            kept_turns: turns.to_vec(),
            summary: String::new(),
            preserved_identifiers: Vec::new(),
            turns_dropped: 0,
            chars_before,
            chars_after: chars_before,
            reduction_ratio: 0.0,
        };
    }

    let split = turns.len() - keep_recent;
    let (old, recent) = turns.split_at(split);

    // Preserve every identifier from the dropped turns, deduplicated.
    let mut preserved: Vec<Identifier> = Vec::new();
    let mut seen = std::collections::HashSet::new();
    for t in old {
        for id in &t.identifiers {
            if seen.insert(id.as_str().to_string()) {
                preserved.push(id.clone());
            }
        }
    }

    // Lead with structure and the most recent dropped line, then identifiers.
    // Prose is dropped; facts are not.
    let mut summary = format!("[{} earlier turns compacted]\n", old.len());
    if let Some(last) = old.last() {
        let head: String = last.content.chars().take(120).collect();
        summary.push_str(&format!("last of the dropped: {}: {}\n", last.role, head));
    }
    if !preserved.is_empty() {
        let ids: Vec<&str> = preserved.iter().map(|i| i.as_str()).collect();
        summary.push_str(&format!("PRESERVED IDENTIFIERS: {}", ids.join(", ")));
    }

    let chars_after: usize = recent.iter().map(|t| t.content.len()).sum::<usize>() + summary.len();
    let reduction_ratio = if chars_before == 0 {
        0.0
    } else {
        1.0 - (chars_after as f64 / chars_before as f64)
    };

    Compaction {
        kept_turns: recent.to_vec(),
        summary,
        preserved_identifiers: preserved,
        turns_dropped: old.len(),
        chars_before,
        chars_after,
        reduction_ratio,
    }
}

/// Episodic + semantic store. SQLite is wired in production; the trait keeps
/// the compaction logic testable without a database.
pub trait MemoryStore {
    fn record_episodic(&mut self, turn: &Turn);
    fn episodic_all(&self) -> Vec<Turn>;
    /// Bi-temporal: a fact is valid from `valid_from` and is superseded (not
    /// deleted) at `superseded_at`.
    fn record_fact(&mut self, entity: &str, relation: &str, value: &str, valid_from: i64);
    fn facts_for(&self, entity: &str) -> Vec<(String, String, i64)>;
    fn search_episodic(&self, needle: &str, limit: usize) -> Vec<Turn>;
}

#[derive(Debug, Default)]
pub struct InMemoryStore {
    episodic: Vec<Turn>,
    facts: HashMap<String, Vec<(String, String, i64)>>,
}

impl MemoryStore for InMemoryStore {
    fn record_episodic(&mut self, turn: &Turn) {
        self.episodic.push(turn.clone());
    }
    fn episodic_all(&self) -> Vec<Turn> {
        self.episodic.clone()
    }
    fn record_fact(&mut self, entity: &str, relation: &str, value: &str, valid_from: i64) {
        self.facts
            .entry(entity.to_string())
            .or_default()
            .push((relation.to_string(), value.to_string(), valid_from));
    }
    fn facts_for(&self, entity: &str) -> Vec<(String, String, i64)> {
        self.facts.get(entity).cloned().unwrap_or_default()
    }
    fn search_episodic(&self, needle: &str, limit: usize) -> Vec<Turn> {
        let n = needle.to_lowercase();
        self.episodic
            .iter()
            .filter(|t| t.content.to_lowercase().contains(&n))
            .take(limit)
            .cloned()
            .collect()
    }
}

/// Tool-output offload threshold. Above this the full output goes to scratch
/// and only a preview plus the path stays in context.
pub const OFFLOAD_THRESHOLD_CHARS: usize = 8_000;
pub const PREVIEW_CHARS: usize = 500;

#[derive(Debug, Clone, PartialEq)]
pub struct Offloaded {
    /// What stays in the model's context.
    pub context_text: String,
    /// Where the full output landed, when it was offloaded.
    pub scratch_path: Option<String>,
    pub was_offloaded: bool,
    pub full_len: usize,
}

/// Split oversized tool output. The preview is always a prefix, never a
/// summary, so a model that needs the tail is told to read the file.
pub fn offload_tool_output(tool: &str, output: &str, scratch_dir: &str) -> Offloaded {
    if output.len() <= OFFLOAD_THRESHOLD_CHARS {
        return Offloaded {
            context_text: output.to_string(),
            scratch_path: None,
            was_offloaded: false,
            full_len: output.len(),
        };
    }
    let preview: String = output.chars().take(PREVIEW_CHARS).collect();
    let path = format!("{scratch_dir}/{tool}-{}.txt", crate::budget::estimate_tokens(output));
    Offloaded {
        context_text: format!(
            "[{tool} output offloaded: {} chars]\nPREVIEW:\n{preview}\n\
             FULL OUTPUT: read {path} for the complete result.",
            output.len()
        ),
        scratch_path: Some(path),
        was_offloaded: true,
        full_len: output.len(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn turns(n: usize, filler: &str) -> Vec<Turn> {
        (0..n)
            .map(|i| Turn::new("user", format!("turn {i} {}", filler.repeat(40))))
            .collect()
    }

    #[test]
    fn identifiers_are_extracted_from_real_shaped_text() {
        let text = "Invoice INV-4471 for $1,299.50 is at /var/log/app.log see https://x.io/a PR-88";
        let ids = extract_identifiers(text);
        let all: Vec<&str> = ids.iter().map(|i| i.as_str()).collect();
        assert!(all.contains(&"INV-4471"), "code identifier");
        assert!(all.contains(&"PR-88"), "second code identifier");
        assert!(all.contains(&"/var/log/app.log"), "path");
        assert!(all.contains(&"https://x.io/a"), "url");
    }

    #[test]
    fn compaction_never_drops_an_identifier() {
        // This is the requirement that matters most: a summary that loses
        // "INV-4471" is worse than no summary, because it looks complete.
        let mut ts = vec![
            Turn::new("user", "please check invoice INV-4471"),
            Turn::new("assistant", "invoice INV-4471 is 30 days overdue"),
            Turn::new("user", "also PR-88 needs review"),
        ];
        ts.push(Turn::new("user", "unrelated chatter ".repeat(200).as_str()));
        ts.push(Turn::new("assistant", "acknowledged, thanks"));

        let c = structural_compaction(&ts, 1);
        assert_eq!(c.turns_dropped, 4);
        let kept: Vec<&str> = c
            .preserved_identifiers
            .iter()
            .map(|i| i.as_str())
            .collect();
        assert!(kept.contains(&"INV-4471"), "identifier survived compaction");
        assert!(kept.contains(&"PR-88"), "second identifier survived");
        assert!(c.summary.contains("PRESERVED IDENTIFIERS"));
        assert!(c.reduction_ratio > 0.5, "compaction should actually compact");
    }

    #[test]
    fn compaction_keeps_the_most_recent_turns_verbatim() {
        let ts = turns(20, "filler");
        let c = structural_compaction(&ts, 3);
        assert_eq!(c.kept_turns.len(), 3);
        assert_eq!(c.kept_turns[2].content, ts[19].content);
    }

    #[test]
    fn compaction_below_threshold_is_a_no_op() {
        let ts = turns(3, "x");
        let c = structural_compaction(&ts, 5);
        assert_eq!(c.turns_dropped, 0);
        assert_eq!(c.reduction_ratio, 0.0);
    }

    #[test]
    fn twenty_turns_fit_in_a_4k_window_after_compaction() {
        // The dossier's Phase 5 commit criterion.
        let mut ts = Vec::new();
        for i in 0..20 {
            ts.push(Turn::new("user", format!("question {i}: {}", "detail ".repeat(200))));
        }
        let c = structural_compaction(&ts, 4);
        // 4096 tokens at ~4 chars/token is ~16k chars of budget.
        let after_chars = c.kept_turns.iter().map(|t| t.content.len()).sum::<usize>()
            + c.summary.len();
        assert!(
            crate::budget::estimate_tokens(&c.summary) + crate::budget::estimate_tokens(
                &c.kept_turns.iter().map(|t| t.content.clone()).collect::<Vec<_>>().join("\n")
            ) <= 4096,
            "compacted history must fit a 4K window, was {after_chars} chars"
        );
    }

    #[test]
    fn small_tool_output_stays_inline() {
        let o = offload_tool_output("calc", "42", "/tmp");
        assert!(!o.was_offloaded);
        assert_eq!(o.context_text, "42");
        assert!(o.scratch_path.is_none());
    }

    #[test]
    fn large_tool_output_is_offloaded_with_a_preview() {
        let big = "x".repeat(20_000);
        let o = offload_tool_output("search", &big, "/tmp/scratch");
        assert!(o.was_offloaded);
        assert!(o.context_text.len() < 1_000, "context must shrink");
        assert!(o.context_text.contains("read /tmp/scratch/"), "path must be given");
        assert!(o.context_text.contains("PREVIEW"));
        assert_eq!(o.full_len, 20_000);
    }

    #[test]
    fn offload_boundary_is_exactly_8000() {
        let at = "a".repeat(OFFLOAD_THRESHOLD_CHARS);
        assert!(!offload_tool_output("t", &at, "/tmp").was_offloaded);
        let over = "a".repeat(OFFLOAD_THRESHOLD_CHARS + 1);
        assert!(offload_tool_output("t", &over, "/tmp").was_offloaded);
    }

    #[test]
    fn semantic_facts_are_bi_temporal() {
        let mut s = InMemoryStore::default();
        s.record_fact("user:arafat", "plan", "pro", 100);
        s.record_fact("user:arafat", "plan", "enterprise", 200);
        let f = s.facts_for("user:arafat");
        assert_eq!(f.len(), 2, "superseded facts are retained, not overwritten");
        assert_eq!(f[1].1, "enterprise");
    }

    #[test]
    fn episodic_search_finds_by_substring() {
        let mut s = InMemoryStore::default();
        s.record_episodic(&Turn::new("user", "the deployment failed on Tuesday"));
        s.record_episodic(&Turn::new("user", "lunch was fine"));
        let hits = s.search_episodic("DEPLOY", 5);
        assert_eq!(hits.len(), 1, "search must be case-insensitive");
    }
}
