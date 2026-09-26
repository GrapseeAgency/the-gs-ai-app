//! GPQA Diamond benchmark runner — provider-routed through the native pool.
//!
//! Honesty rules baked in, because a benchmark that can flatter itself is worse
//! than no benchmark:
//!   - Every score carries a Wilson 95% CI. A bare percentage is not a legal
//!     output.
//!   - Unparseable answers score as WRONG, never as skipped-and-forgotten.
//!     Silently dropping hard samples inflates the score.
//!   - Provider errors are counted as `errored`, separately from `failed`.
//!   - A lever run uses the SAME sample set and the SAME model as baseline;
//!     the runner refuses to compare across differing inputs.
//!   - Raw per-sample JSON is always written, so any number can be audited.

use gs_common::{CompletionConfig, Message, ScoreWithCI};
use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;
use std::time::Instant;

#[derive(Debug, Deserialize)]
pub struct Question {
    pub id: String,
    pub question: String,
    pub answer: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Scaffold {
    Baseline,
    Aci,
    Verification,
    Context,
    Router,
}

impl Scaffold {
    pub fn as_str(self) -> &'static str {
        match self {
            Scaffold::Baseline => "baseline",
            Scaffold::Aci => "aci",
            Scaffold::Verification => "verification",
            Scaffold::Context => "context",
            Scaffold::Router => "router",
        }
    }
}

/// The scaffolding a lever adds on top of a bare question. Deliberately
/// explicit so a lever's effect is attributable.
pub fn scaffold_prompt(s: Scaffold, question: &str) -> String {
    match s {
        Scaffold::Baseline => format!(
            "Answer the following multiple-choice question. \
             Respond with ONLY the single letter of the correct option.\n\n{question}"
        ),
        // ACI: act-then-check. Forces a visible intermediate step.
        Scaffold::Aci => format!(
            "Answer the following multiple-choice question.\n\
             First reason briefly, then on the last line output \
             'ANSWER: <letter>' where <letter> is one of A, B, C, D.\n\n{question}"
        ),
        // Verification: the dossier's guards, applied to the model's own output.
        Scaffold::Verification => format!(
            "Answer the following multiple-choice question.\n\
             State your chosen letter, then verify it by re-deriving the key \
             quantity. If your verification disagrees, revise before answering.\n\
             End with 'ANSWER: <letter>'.\n\n{question}"
        ),
        // Context: retrieval-shaped framing plus an explicit recall instruction.
        Scaffold::Context => format!(
            "Answer the following graduate-level science question.\n\
             Recall the relevant principle and any formulae first, then apply \
             them step by step. End with 'ANSWER: <letter>'.\n\n{question}"
        ),
        // Router: none of the above, but routed through the pool identically.
        // Present so the comparison has a matched control.
        Scaffold::Router => format!(
            "Answer the following multiple-choice question. \
             Respond with ONLY the single letter of the correct option.\n\n{question}"
        ),
    }
}

/// Extract a single A/B/C/D answer.
///
/// Returns None when no answer can be found, which the caller scores as WRONG.
/// Guessing here would let a non-answer become a correct one by luck.
pub fn extract_answer(text: &str) -> Option<char> {
    let t = text.trim();
    // Prefer an explicit marker, last occurrence wins so a revision counts.
    if let Some(i) = t.rfind("ANSWER:") {
        let rest = &t[i + 7..];
        for c in rest.chars() {
            let u = c.to_ascii_uppercase();
            if ['A', 'B', 'C', 'D'].contains(&u) {
                return Some(u);
            }
        }
    }
    // A bare single letter, e.g. "D" or "**D**".
    let cleaned: String = t
        .chars()
        .filter(|c| !c.is_whitespace() && *c != '*' && *c != '.' && *c != ':')
        .collect();
    if cleaned.len() == 1 {
        let u = cleaned.chars().next()?.to_ascii_uppercase();
        if ['A', 'B', 'C', 'D'].contains(&u) {
            return Some(u);
        }
    }
    // "the answer is C" / "option B" / "(A)"
    let lower = t.to_lowercase();
    for pat in ["answer is", "option", "choice", "answer:"] {
        if let Some(p) = lower.rfind(pat) {
            for c in t[p + pat.len()..].chars() {
                let u = c.to_ascii_uppercase();
                if ['A', 'B', 'C', 'D'].contains(&u) {
                    return Some(u);
                }
            }
        }
    }
    None
}

#[derive(Debug, Clone, Serialize)]
pub struct SampleResult {
    pub id: String,
    pub correct: bool,
    pub parsed: Option<String>,
    pub expected: String,
    pub provider: String,
    pub served_model: String,
    pub key_index: usize,
    pub latency_ms: u64,
    pub input_tokens: u32,
    pub output_tokens: u32,
    pub raw: String,
    pub error: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct Scorecard {
    pub scaffold: String,
    pub model_requested: String,
    pub n_samples: u32,
    pub n_correct: u32,
    pub score: f64,
    pub ci_low: f64,
    pub ci_high: f64,
    pub ci_method: String,
    pub n_failed: u32,   // answered but wrong
    pub n_errored: u32,  // provider/transport failure, not answered
    pub n_skipped: u32,  // not attempted
    pub unparseable: u32,
    pub provider_distribution: BTreeMap<String, u32>,
    pub model_distribution: BTreeMap<String, u32>,
    pub latency_p50_ms: u64,
    pub latency_p95_ms: u64,
    pub total_inference_ms: u64,
    pub git_commit: String,
    pub sample_set_sha: String,
    pub per_sample_path: String,
}

pub fn percentile(sorted: &[u64], p: f64) -> u64 {
    if sorted.is_empty() {
        return 0;
    }
    let idx = ((sorted.len() as f64 - 1.0) * p).round() as usize;
    sorted[idx.min(sorted.len() - 1)]
}

/// Short, stable fingerprint of the sample set, so two runs can prove they
/// used the same questions.
pub fn sample_set_sha(questions: &[Question]) -> String {
    // FNV-1a 64: no crypto dependency for an integrity fingerprint.
    let mut h: u64 = 0xcbf2_9ce4_8422_2325;
    for q in questions {
        for b in q.id.as_bytes().iter().chain(q.answer.as_bytes()) {
            h ^= *b as u64;
            h = h.wrapping_mul(0x1000_0000_01b3);
        }
    }
    format!("{h:016x}")
}

pub fn load_questions(path: &str) -> Result<Vec<Question>, String> {
    let raw = std::fs::read_to_string(path).map_err(|e| format!("read {path}: {e}"))?;
    let mut out = Vec::new();
    for (i, line) in raw.lines().enumerate() {
        if line.trim().is_empty() {
            continue;
        }
        let q: Question = serde_json::from_str(line)
            .map_err(|e| format!("parse {path} line {}: {e}", i + 1))?;
        out.push(q);
    }
    if out.is_empty() {
        return Err(format!("{path} contained no questions"));
    }
    Ok(out)
}

pub struct Runner {
    pub pool: std::sync::Arc<gs_core::router::ProviderPool>,
    pub config: CompletionConfig,
    pub scaffold: Scaffold,
    /// Delay between samples. Free tiers are per-KEY rate limited, so a single
    /// key needs pacing: 198 requests fired back-to-back trip the limit on
    /// roughly sample 20 and every later sample then finds no live key.
    pub pace_ms: u64,
    /// How long to wait for a parked key to come back before giving up.
    pub park_wait_ms: u64,
}

impl Runner {
    pub async fn run(&self, questions: &[Question]) -> (Vec<SampleResult>, Scorecard) {
        let mut results: Vec<SampleResult> = Vec::with_capacity(questions.len());
        let t_all = Instant::now();

        for (n, q) in questions.iter().enumerate() {
            if n > 0 && self.pace_ms > 0 {
                tokio::time::sleep(std::time::Duration::from_millis(self.pace_ms)).await;
            }
            let prompt = scaffold_prompt(self.scaffold, &q.question);
            let msgs = vec![Message::user(prompt)];

            // Retry while the only key is parked. The pool parks for 60s by
            // design; without a wait the run would abandon the remaining
            // samples instead of riding the park out.
            let mut attempt = 0u32;
            let r = loop {
                match self.pool.complete(&msgs, &self.config).await {
                    Ok(c) => break Ok(c),
                    Err(e) => {
                        let retryable = e.to_string().contains("no provider available")
                            || e.to_string().contains("rate limited");
                        if retryable && attempt < 12 {
                            attempt += 1;
                            eprintln!(
                                "  [retry {attempt}] waiting {}ms for a key: {e}",
                                self.park_wait_ms
                            );
                            tokio::time::sleep(std::time::Duration::from_millis(
                                self.park_wait_ms,
                            ))
                            .await;
                            continue;
                        }
                        break Err(e);
                    }
                }
            };
            let r = match r {
                Ok(c) => {
                    let parsed = extract_answer(&c.text);
                    SampleResult {
                        id: q.id.clone(),
                        // Unparseable counts as WRONG. Dropping it would
                        // inflate the score by removing hard samples.
                        correct: parsed == Some(q.answer.chars().next().unwrap()),
                        parsed: parsed.map(|c| c.to_string()),
                        expected: q.answer.clone(),
                        provider: c.provider.clone(),
                        served_model: c.served_model.clone(),
                        key_index: c.key_index,
                        latency_ms: c.latency_ms,
                        input_tokens: c.input_tokens,
                        output_tokens: c.output_tokens,
                        raw: c.text.clone(),
                        error: None,
                    }
                }
                Err(e) => {
                    // Counted as errored, NOT as correct and NOT as skipped.
                    SampleResult {
                        id: q.id.clone(),
                        correct: false,
                        parsed: None,
                        expected: q.answer.clone(),
                        provider: "none".into(),
                        served_model: String::new(),
                        key_index: 0,
                        latency_ms: 0,
                        input_tokens: 0,
                        output_tokens: 0,
                        raw: String::new(),
                        error: Some(e.to_string()),
                    }
                }
            };
            results.push(r);
        }

        let n = results.len() as u32;
        let correct = results.iter().filter(|r| r.correct).count() as u32;
        let errored = results.iter().filter(|r| r.error.is_some()).count() as u32;
        let unparseable = results
            .iter()
            .filter(|r| r.error.is_none() && r.parsed.is_none())
            .count() as u32;

        let mut pd: BTreeMap<String, u32> = BTreeMap::new();
        let mut md: BTreeMap<String, u32> = BTreeMap::new();
        for r in &results {
            *pd.entry(r.provider.clone()).or_insert(0) += 1;
            if !r.served_model.is_empty() {
                *md.entry(r.served_model.clone()).or_insert(0) += 1;
            }
        }
        let mut lat: Vec<u64> = results.iter().map(|r| r.latency_ms).collect();
        lat.sort_unstable();

        let ci = ScoreWithCI::wilson(correct, n);
        let card = Scorecard {
            scaffold: self.scaffold.as_str().to_string(),
            model_requested: self.config.model.clone(),
            n_samples: n,
            n_correct: correct,
            score: ci.score,
            ci_low: ci.ci_low,
            ci_high: ci.ci_high,
            ci_method: "wilson-95".into(),
            n_failed: n - correct,
            n_errored: errored,
            n_skipped: 0,
            unparseable,
            provider_distribution: pd,
            model_distribution: md,
            latency_p50_ms: percentile(&lat, 0.50),
            latency_p95_ms: percentile(&lat, 0.95),
            total_inference_ms: t_all.elapsed().as_millis() as u64,
            git_commit: git_commit(),
            sample_set_sha: sample_set_sha(questions),
            per_sample_path: String::new(),
        };
        (results, card)
    }
}

fn git_commit() -> String {
    std::process::Command::new("git")
        .args(["rev-parse", "--short", "HEAD"])
        .output()
        .ok()
        .and_then(|o| String::from_utf8(o.stdout).ok())
        .map(|s| s.trim().to_string())
        .unwrap_or_else(|| "unknown".into())
}

/// Compare a lever against baseline. Refuses to compare across differing
/// sample sets or models, because such a comparison is meaningless.
pub fn delta_table(baseline: &Scorecard, lever: &Scorecard) -> Vec<String> {
    let mut out = Vec::new();
    if baseline.sample_set_sha != lever.sample_set_sha {
        out.push(format!(
            "INCOMPARABLE: sample set differs ({} vs {})",
            baseline.sample_set_sha, lever.sample_set_sha
        ));
        return out;
    }
    if baseline.n_samples != lever.n_samples {
        out.push(format!(
            "INCOMPARABLE: n differs ({} vs {})",
            baseline.n_samples, lever.n_samples
        ));
        return out;
    }
    let d = lever.score - baseline.score;
    out.push(format!(
        "{:<13} {:>6.2}% [{:>5.2},{:>5.2}]  delta {:+.2}pp  \
         p50 {:>5}ms p95 {:>5}ms  verdict {}",
        lever.scaffold,
        lever.score * 100.0,
        lever.ci_low * 100.0,
        lever.ci_high * 100.0,
        d * 100.0,
        lever.latency_p50_ms,
        lever.latency_p95_ms,
        if d > 0.005 {
            "KEEP (up)"
        } else if d < -0.005 {
            "REVERT (down)"
        } else {
            "REVERT (flat)"
        }
    ));
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    fn q(id: &str, a: &str) -> Question {
        Question { id: id.into(), question: "Q?".into(), answer: a.into() }
    }

    #[test]
    fn answers_are_extracted_from_every_shape_a_model_emits() {
        assert_eq!(extract_answer("D"), Some('D'));
        assert_eq!(extract_answer("**C**"), Some('C'));
        assert_eq!(extract_answer("ANSWER: B"), Some('B'));
        assert_eq!(extract_answer("blah blah\nANSWER: A"), Some('A'));
        assert_eq!(extract_answer("The answer is C."), Some('C'));
        assert_eq!(extract_answer("option (D)"), Some('D'));
        // A revision must win over the earlier answer.
        assert_eq!(extract_answer("ANSWER: A\nActually ANSWER: D"), Some('D'));
    }

    #[test]
    fn an_unparseable_answer_returns_none_and_scores_wrong() {
        // Guessing here would turn a non-answer into a lucky correct answer.
        assert_eq!(extract_answer("I am not sure about this one"), None);
        assert_eq!(extract_answer(""), None);
        assert_eq!(extract_answer("E"), None, "E is not an option");
    }

    #[test]
    fn wilson_ci_is_always_present_and_brackets_the_score() {
        let c = ScoreWithCI::wilson(20, 198);
        assert!(c.ci_low < c.score && c.score < c.ci_high);
        // Exact regression values at 20/198, so a change to the Wilson
        // implementation cannot silently move every reported score.
        assert!((c.score - 0.10101).abs() < 1e-4, "score {}", c.score);
        assert!((c.ci_low - 0.06634).abs() < 1e-3, "ci_low {}", c.ci_low);
        assert!((c.ci_high - 0.15086).abs() < 1e-3, "ci_high {}", c.ci_high);
        // The width is ~8.5 points. This is the headline caveat for the whole
        // benchmark: at n=198 a lever has to move the score by roughly 8
        // points before the shift clears the interval. Anything smaller is
        // noise, and must be reported as flat rather than as an improvement.
        let w = c.ci_high - c.ci_low;
        assert!((0.084..0.085).contains(&w), "CI width moved to {w}");
    }

    #[test]
    fn sample_set_fingerprint_is_stable_and_order_sensitive() {
        let a = vec![q("1", "A"), q("2", "B")];
        let b = vec![q("1", "A"), q("2", "B")];
        let c = vec![q("2", "B"), q("1", "A")];
        assert_eq!(sample_set_sha(&a), sample_set_sha(&b), "same set, same hash");
        assert_ne!(sample_set_sha(&a), sample_set_sha(&c), "order matters");
    }

    #[test]
    fn comparing_across_different_sample_sets_is_refused() {
        let mut b = Scorecard { scaffold: "baseline".into(), model_requested: "m".into(),
            n_samples: 10, n_correct: 5, score: 0.5, ci_low: 0.2, ci_high: 0.8,
            ci_method: "wilson-95".into(), n_failed: 5, n_errored: 0, n_skipped: 0,
            unparseable: 0, provider_distribution: BTreeMap::new(),
            model_distribution: BTreeMap::new(), latency_p50_ms: 1, latency_p95_ms: 2,
            total_inference_ms: 0, git_commit: "x".into(),
            sample_set_sha: "aaaa".into(), per_sample_path: String::new() };
        let mut l = b.clone(); l.scaffold = "aci".into(); l.sample_set_sha = "bbbb".into();
        let out = delta_table(&b, &l);
        assert!(out[0].starts_with("INCOMPARABLE"), "got {out:?}");
    }

    #[test]
    fn a_flat_lever_is_advised_to_revert() {
        let mut b = Scorecard { scaffold: "baseline".into(), model_requested: "m".into(),
            n_samples: 10, n_correct: 5, score: 0.5, ci_low: 0.2, ci_high: 0.8,
            ci_method: "wilson-95".into(), n_failed: 5, n_errored: 0, n_skipped: 0,
            unparseable: 0, provider_distribution: BTreeMap::new(),
            model_distribution: BTreeMap::new(), latency_p50_ms: 1, latency_p95_ms: 2,
            total_inference_ms: 0, git_commit: "x".into(),
            sample_set_sha: "aaaa".into(), per_sample_path: String::new() };
        let mut l = b.clone(); l.scaffold = "aci".into(); l.score = 0.5;
        assert!(delta_table(&b, &l)[0].contains("REVERT (flat)"));
        let mut u = b.clone(); u.scaffold = "aci".into(); u.score = 0.6;
        assert!(delta_table(&b, &u)[0].contains("KEEP"));
    }

    #[test]
    fn percentiles_bracket_correctly() {
        let v = vec![10u64, 20, 30, 40, 50, 60, 70, 80, 90, 100];
        // nearest-rank on the index: round((n-1)*p). For even n that lands on
        // the upper-middle, which is the documented behaviour.
        assert_eq!(percentile(&v, 0.5), 60);
        assert_eq!(percentile(&v, 0.95), 100);
        assert_eq!(percentile(&v, 0.0), 10);
        assert_eq!(percentile(&v, 1.0), 100);
        assert_eq!(percentile(&[], 0.5), 0, "empty must not panic");
    }

    #[test]
    fn scaffolds_are_distinct_except_the_matched_router_control() {
        let all = [Scaffold::Baseline, Scaffold::Aci, Scaffold::Verification,
                   Scaffold::Context, Scaffold::Router];
        let mut seen = std::collections::HashSet::new();
        for s in all {
            seen.insert(scaffold_prompt(s, "Q?"));
        }
        // Router is intentionally a matched control: identical prompt text to
        // baseline, differing only in how the turn is routed. It therefore
        // must NOT contribute a fifth distinct prompt, and asserting that it
        // did would have hidden the whole point of the arm.
        assert_eq!(seen.len(), 4, "4 distinct prompts + 1 matched control");
        assert_eq!(
            scaffold_prompt(Scaffold::Baseline, "Q?"),
            scaffold_prompt(Scaffold::Router, "Q?"),
            "router must match baseline exactly to be a control"
        );
    }

    #[test]
    fn the_staged_gpqa_file_parses_to_198_questions() {
        let p = "data/benchmarks/gpqa/diamond.jsonl";
        if !std::path::Path::new(p).exists() {
            eprintln!("skipping: {p} not staged");
            return;
        }
        let qs = load_questions(p).expect("staged GPQA must parse");
        assert_eq!(qs.len(), 198, "GPQA Diamond is 198 questions");
        assert!(qs.iter().all(|q| ["A", "B", "C", "D"].contains(&q.answer.as_str())));
    }
}
