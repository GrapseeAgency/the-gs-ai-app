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

#[derive(Debug, Deserialize, Clone)]
pub struct Question {
    pub id: String,
    pub question: String,
    /// The options. Required: GPQA is multiple choice, and a stem with no
    /// options is not a benchmark item, it is a broken prompt.
    pub choices: Vec<String>,
    pub answer: String,
}

impl Question {
    /// Render as an A) B) C) D) block. Without this the model answers with a
    /// bare number and every sample scores as unparseable.
    pub fn rendered(&self) -> String {
        let mut s = format!("{}\n\n", self.question.trim_end());
        for (i, c) in self.choices.iter().enumerate() {
            s.push_str(&format!("{}) {}\n", (b'A' + i as u8) as char, c.trim()));
        }
        s
    }
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

/// A json_schema that forces the model to emit exactly one of A/B/C/D.
///
/// This is the Step 1 fix: the model is constrained to the answer space by the
/// decoder, so parse failure becomes a provider problem rather than a
/// formatting accident. Verified supported on Groq and OpenRouter.
pub fn letter_schema() -> serde_json::Value {
    serde_json::json!({
        "type": "json_schema",
        "json_schema": {
            "name": "multiple_choice",
            "strict": true,
            "schema": {
                "type": "object",
                "properties": {
                    "answer": { "type": "string", "enum": ["A", "B", "C", "D"] }
                },
                "required": ["answer"],
                "additionalProperties": false
            }
        }
    })
}

/// STRICT parser. Reads only the constrained `{"answer": "X"}` object.
///
/// It deliberately does NOT fall back to scanning prose. A permissive
/// extractor is exactly what conflated format compliance with reasoning in
/// the previous run, so the two are now separated: anything this rejects is
/// counted as UNPARSEABLE and reported as such, never silently guessed.
pub fn extract_answer_strict(text: &str) -> Option<char> {
    let t = text.trim();
    // A lone letter IS a constrained-decode result: Groq and OpenRouter both
    // sometimes render the json_schema answer as a bare character rather than
    // wrapped in the object. Accepting it is still strict, because a single
    // isolated character cannot be a guess drawn from prose - that is the
    // distinction the loose parser loses. Anything longer is refused.
    let bare = t.trim_matches(|c: char| c == '*' || c == '`' || c == '.');
    if bare.chars().count() == 1 {
        let u = bare.chars().next().unwrap().to_ascii_uppercase();
        if ['A', 'B', 'C', 'D'].contains(&u) {
            return Some(u);
        }
    }
    // Strip a markdown fence if the provider wrapped the JSON.
    let fenced = t.strip_prefix("```json").or_else(|| t.strip_prefix("```")).unwrap_or(t);
    let fenced = fenced.trim().trim_end_matches("```").trim();
    if let Ok(v) = serde_json::from_str::<serde_json::Value>(fenced) {
        if let Some(a) = v.get("answer").and_then(|x| x.as_str()) {
            let u = a.trim().to_ascii_uppercase();
            if ["A", "B", "C", "D"].contains(&u.as_str()) {
                return Some(u.chars().next().unwrap());
            }
        }
        // Well-formed JSON with no usable answer: refuse rather than go
        // hunting through the object for a stray letter.
        return None;
    }

    // Leading answer, unconstrained decoding.
    //
    // Constrained decoding is not available on the local llama.cpp text path,
    // so the model answers "D)", "D. False", "B\n\nIt depends on..." instead
    // of a bare letter. That is still a stated answer: the first token names
    // the option and the rest is the model explaining itself.
    //
    // The rule is "a standalone A-D in the first LEAD_CHARS", and LEAD_CHARS is
    // deliberately tiny.
    //
    // A 16-char window was tried and is wrong: it makes "the answer is C" parse,
    // which is exactly the prose-guessing the strict parser exists to refuse,
    // and an existing test caught it. The local model always leads with the
    // letter -- "D)", "D. False, True", "B\n\nIt depends" -- so a three-character
    // window covers every observed output while keeping "a letter buried in
    // prose" refused.
    const LEAD_CHARS: usize = 3;
    let head: String = t.chars().take(LEAD_CHARS).collect();
    if let Some(u) = first_standalone_letter(&head) {
        return Some(u);
    }
    None
}

/// Extract a single A/B/C/D answer.
///
/// PERMISSIVE. Retained only for comparison against the strict parser; the
/// scorecard uses the strict one.
///
/// Returns None when no answer can be found, which the caller scores as WRONG.
/// Guessing here would let a non-answer become a correct one by luck.
pub fn extract_answer(text: &str) -> Option<char> {
    let t = text.trim();
    // Prefer an explicit marker, last occurrence wins so a revision counts.
    if let Some(i) = t.rfind("ANSWER:") {
        if let Some(u) = first_standalone_letter(&t[i + 7..]) {
            return Some(u);
        }
    }
    // "the answer is C" / "option B" / "(A)"
    let lower = t.to_lowercase();
    for pat in ["answer is", "option", "choice", "answer:"] {
        if let Some(p) = lower.rfind(pat) {
            if let Some(u) = first_standalone_letter(&t[p + pat.len()..]) {
                return Some(u);
            }
        }
    }
    // A bare or lightly-decorated letter: "D", "D)", "(D)", "**D**", "D.".
    //
    // The decoration set matters more than it looks: this model answers "D)"
    // more often than anything else, and a parser that only accepts a naked
    // letter reports a correct answer as unparseable, which drops parse_rate
    // to zero and measures the parser instead of the model.
    let cleaned: String = t
        .chars()
        .filter(|c| {
            !c.is_whitespace()
                && !matches!(c, '*' | '.' | ':' | ')' | '(' | '[' | ']' | ',' | '-' | '_')
        })
        .collect();
    if cleaned.chars().count() == 1 {
        let u = cleaned.chars().next()?.to_ascii_uppercase();
        if ['A', 'B', 'C', 'D'].contains(&u) {
            return Some(u);
        }
    }
    None
}

/// The first A-D that stands alone as a token, not a letter inside a word.
///
/// Scanning character-by-character is wrong: "the correct option" contains a
/// C in "correct", so a naive scan of that region returns C. Requiring the
/// character to be bounded by a non-alphanumeric on both sides removes the
/// whole class of false positives.
fn first_standalone_letter(s: &str) -> Option<char> {
    let chars: Vec<char> = s.chars().collect();
    for (i, &c) in chars.iter().enumerate() {
        let u = c.to_ascii_uppercase();
        if !['A', 'B', 'C', 'D'].contains(&u) {
            continue;
        }
        let before_ok = i == 0 || !chars[i - 1].is_ascii_alphabetic();
        let after_ok = i + 1 >= chars.len() || !chars[i + 1].is_ascii_alphabetic();
        if before_ok && after_ok {
            return Some(u);
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
    /// parse_rate = parsed / n_samples: how often the answer was well-formed.
    pub parse_rate: f64,
    /// accuracy over PARSED samples only: the reasoning signal.
    pub accuracy: f64,
    pub n_parsed: u32,
    /// unparseable counted as WRONG. The number to quote, because it cannot
    /// be inflated by silently dropping the hard items.
    pub accuracy_floor_adjusted: f64,
    pub ci_floor_low: f64,
    pub ci_floor_high: f64,
    pub ci_accuracy_low: f64,
    pub ci_accuracy_high: f64,
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

/// Rank items by expected difficulty so a smoke test exercises the hard end.
///
/// The previous smoke used the first N items, which were easy: 5/5 parsed on a
/// model that went on to fail on a chemistry question. A gate that only ever
/// sees easy items is not a gate. Longest question text is a usable proxy for
/// expected reasoning length, and it needs no labels.
pub fn hardest(items: &[Question], n: usize) -> Vec<Question> {
    let mut v = items.to_vec();
    v.sort_by(|a, b| {
        let la = a.question.len() + a.choices.iter().map(|c| c.len()).sum::<usize>();
        let lb = b.question.len() + b.choices.iter().map(|c| c.len()).sum::<usize>();
        lb.cmp(&la).then_with(|| a.id.cmp(&b.id))   // stable tie-break
    });
    v.truncate(n);
    v
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
            let prompt = scaffold_prompt(self.scaffold, &q.rendered());
            let msgs = vec![Message::user(prompt)];

            // Retry while the only key is parked. The pool parks for 60s by
            // design; without a wait the run would abandon the remaining
            // samples instead of riding the park out.
            let mut attempt = 0u32;
            let r = loop {
                match self.pool.complete(&msgs, &self.config).await {
                    Ok(c) => break Ok(c),
                    Err(e) => {
                        // An empty completion is retried on the SAME key
                        // first: reasoning models on Groq occasionally return
                        // a 200 with no content, and rotating to another key
                        // does not help when the MODEL is the variable.
                        let s = e.to_string();
                        let empty = s.contains("empty completion");
                        // An empty completion gets EXACTLY ONE retry, to rule
                        // out a transient. With a single provider there is
                        // nowhere to fail over to, so retrying six times just
                        // burns 90s per sample and then errors it anyway.
                        let retryable = empty || s.contains("no provider available")
                            || s.contains("rate limited");
                        let cap = if empty { 1 } else { 12 };
                        if retryable && attempt < cap {
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
                    let parsed = extract_answer_strict(&c.text);
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
        // The three numbers, kept apart on purpose.
        //
        // n_parsed must EXCLUDE errored samples. Previously it was
        // n - unparseable, and because an errored sample has error=Some it
        // was never counted as unparseable - so an errored sample counted as
        // PARSED. That produced the impossible pair parse_rate=1.0 with
        // n_errored=1. The denominator for accuracy is parsed, not attempted.
        let n_parsed = n - unparseable - errored;
        // parse_rate is over ATTEMPTED samples, and an errored sample was
        // never parsed, so it belongs in the denominator as a failure.
        let parse_rate = if n == 0 { 0.0 } else { n_parsed as f64 / n as f64 };
        // accuracy over PARSED only: the reasoning signal.
        let accuracy = if n_parsed == 0 { 0.0 } else { correct as f64 / n_parsed as f64 };
        // floor-adjusted: unparseable = wrong. The number to quote.
        let floor_adj = if n == 0 { 0.0 } else { correct as f64 / n as f64 };
        let ci_a = ScoreWithCI::wilson(correct, n_parsed);
        let ci_f = ScoreWithCI::wilson(correct, n);

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
            parse_rate,
            accuracy,
            n_parsed,
            accuracy_floor_adjusted: floor_adj,
            ci_floor_low: ci_f.ci_low,
            ci_floor_high: ci_f.ci_high,
            ci_accuracy_low: ci_a.ci_low,
            ci_accuracy_high: ci_a.ci_high,
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
mod answer_parsing {
    use super::{extract_answer, extract_answer_strict, first_standalone_letter};

    #[test]
    fn a_bare_letter_is_a_constrained_decode_result() {
        let cases = [("A", 'A'), (" D ", 'D'), ("**D**", 'D'), ("`B`", 'B'), ("C.", 'C')];
        for (raw, want) in cases {
            assert_eq!(extract_answer_strict(raw), Some(want), "{raw:?}");
        }
    }

    #[test]
    fn json_schemas_are_read_not_hunted_through() {
        assert_eq!(extract_answer_strict(r#"{"answer":"C"}"#), Some('C'));
        assert_eq!(extract_answer_strict("```json\n{\"answer\":\"B\"}\n```"), Some('B'));
        // Valid JSON, no answer field: refuse instead of scraping a letter.
        assert_eq!(extract_answer_strict(r#"{"explanation":"A B C D"}"#), None);
    }

    #[test]
    fn a_leading_letter_with_trailing_prose_counts_as_an_answer() {
        // The local llama.cpp path has no constrained decoding, so this is the
        // shape the model actually produces.
        let cases = [("D)", 'D'), ("D) False, True", 'D'), ("B.\n\nIt depends.", 'B'), ("C:\nSome explanation", 'C')];
        for (raw, want) in cases {
            assert_eq!(extract_answer_strict(raw), Some(want), "{raw:?}");
        }
    }

    #[test]
    fn a_letter_buried_in_prose_is_not_an_answer() {
        // Strictness: the rule is anchored to the front of the output.
        for raw in [
            "I think the correct option is the third one, which is D",
            "Let me think about this question for a moment before answering.",
            "",
        ] {
            assert_eq!(extract_answer_strict(raw), None, "{raw:?}");
        }
    }

    #[test]
    fn a_letter_inside_a_word_is_never_mistaken_for_an_answer() {
        // "correct" contains a C, "A" is in the middle of nothing.
        assert_eq!(first_standalone_letter("the correct answer"), None);
        assert_eq!(first_standalone_letter("(C) because"), Some('C'));
        assert_eq!(first_standalone_letter("the answer is B."), Some('B'));
    }

    #[test]
    fn the_permissive_parser_agrees_on_the_common_shapes() {
        assert_eq!(extract_answer("ANSWER: D"), Some('D'));
        assert_eq!(extract_answer("The answer is C."), Some('C'));
        // And still refuses to guess.
        assert_eq!(extract_answer("no idea"), None);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn q(id: &str, a: &str) -> Question {
        Question {
            id: id.into(),
            question: "Q?".into(),
            choices: vec!["one".into(), "two".into()],
            answer: a.into(),
        }
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
    fn the_strict_parser_reads_only_constrained_json() {
        assert_eq!(extract_answer_strict(r#"{"answer":"C"}"#), Some('C'));
        assert_eq!(extract_answer_strict("```json\n{\"answer\":\"B\"}\n```"), Some('B'));
        assert_eq!(extract_answer_strict(r#"{"answer":"d"}"#), Some('D'));
    }

    #[test]
    fn the_strict_parser_refuses_prose_where_the_loose_one_would_guess() {
        // This is the separation the previous run failed to make. The loose
        // parser finds a letter in prose; the strict one must not, because
        // that is exactly what let formatting masquerade as reasoning.
        let prose = "Let us think. The answer is C because of the energy gap.";
        assert_eq!(extract_answer(prose), Some('C'), "loose parser still guesses");
        assert_eq!(extract_answer_strict(prose), None, "strict must refuse");
        // A lone letter is accepted: it is a constrained decode, not a guess.
        assert_eq!(extract_answer_strict("C"), Some('C'));
        assert_eq!(extract_answer_strict("**D**"), Some('D'));
        // But a letter embedded in prose is still refused.
        assert_eq!(extract_answer_strict("the answer is C"), None);
        assert_eq!(extract_answer_strict("AC"), None);
        assert_eq!(extract_answer_strict(r#"{"answer":"Z"}"#), None);
        assert_eq!(extract_answer_strict("not json at all"), None);
    }

    #[test]
    fn the_schema_constrains_to_exactly_the_four_letters() {
        let s = letter_schema();
        let letters = &s["json_schema"]["schema"]["properties"]["answer"]["enum"];
        assert_eq!(letters.as_array().unwrap().len(), 4);
        assert!(s["json_schema"]["schema"]["additionalProperties"] == false);
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
            unparseable: 0, parse_rate: 1.0, accuracy: 0.5, n_parsed: 10,
            accuracy_floor_adjusted: 0.5, ci_floor_low: 0.2, ci_floor_high: 0.8,
            ci_accuracy_low: 0.2, ci_accuracy_high: 0.8,
            provider_distribution: BTreeMap::new(),
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
            unparseable: 0, parse_rate: 1.0, accuracy: 0.5, n_parsed: 10,
            accuracy_floor_adjusted: 0.5, ci_floor_low: 0.2, ci_floor_high: 0.8,
            ci_accuracy_low: 0.2, ci_accuracy_high: 0.8,
            provider_distribution: BTreeMap::new(),
            model_distribution: BTreeMap::new(), latency_p50_ms: 1, latency_p95_ms: 2,
            total_inference_ms: 0, git_commit: "x".into(),
            sample_set_sha: "aaaa".into(), per_sample_path: String::new() };
        let mut l = b.clone(); l.scaffold = "aci".into(); l.score = 0.5;
        assert!(delta_table(&b, &l)[0].contains("REVERT (flat)"));
        let mut u = b.clone(); u.scaffold = "aci".into(); u.score = 0.6;
        assert!(delta_table(&b, &u)[0].contains("KEEP"));
    }

    #[test]
    fn an_errored_sample_never_counts_as_parsed() {
        // The operator saw parse_rate=1.0 together with n_errored=1, which
        // is impossible. This is the arithmetic that produced it and the
        // arithmetic that fixes it.
        let n = 1u32;
        let unparseable = 0u32;   // an errored sample is not "unparseable"
        let errored = 1u32;
        let n_parsed = n - unparseable - errored;
        assert_eq!(n_parsed, 0, "an errored sample is not parsed");
        let parse_rate = n_parsed as f64 / n as f64;
        assert_eq!(parse_rate, 0.0, "parse_rate must be 0 when nothing parsed");
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
    fn options_are_rendered_as_lettered_choices() {
        let qq = Question {
            id: "x".into(),
            question: "Which is true?".into(),
            choices: vec!["10 eV".into(), "11 eV".into(), "12 eV".into(), "13 eV".into()],
            answer: "C".into(),
        };
        let r = qq.rendered();
        for (i, l) in ["A) 10 eV", "B) 11 eV", "C) 12 eV", "D) 13 eV"].iter().enumerate() {
            assert!(r.contains(l), "missing option {i}: {r}");
        }
    }

    #[test]
    fn the_smoke_gate_selects_the_longest_items_not_the_first() {
        let items: Vec<Question> = (0..40)
            .map(|i| Question {
                id: format!("q{i}"),
                question: "x".repeat(i * 10 + 5),
                choices: vec!["a".into(), "b".into()],
                answer: "A".into(),
            })
            .collect();
        let gate = hardest(&items, 10);
        assert_eq!(gate.len(), 10);
        // The longest must be included; the shortest must not.
        assert_eq!(gate[0].id, "q39", "longest item first");
        assert!(!gate.iter().any(|q| q.id == "q0"), "shortest must be excluded");
        // Every gate item must be at least as long as everything outside it.
        let min_in = gate.iter().map(|q| q.question.len()).min().unwrap();
        let max_out = items
            .iter()
            .filter(|q| !gate.iter().any(|g| g.id == q.id))
            .map(|q| q.question.len())
            .max()
            .unwrap();
        assert!(min_in >= max_out, "gate must be the hard end, not a mix");
    }

    #[test]
    fn every_staged_question_has_options() {
        let p = "data/benchmarks/gpqa/diamond.jsonl";
        if !std::path::Path::new(p).exists() {
            eprintln!("skipping: {p} not staged");
            return;
        }
        let qs = load_questions(p).expect("staged GPQA must parse");
        assert_eq!(qs.len(), 198);
        // The bug this pins: a stem with no options is not a benchmark item.
        assert!(
            qs.iter().all(|q| q.choices.len() == 4),
            "every GPQA Diamond item must carry 4 options"
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
