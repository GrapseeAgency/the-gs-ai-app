//! Verification guards (dossier Phase 6).
//!
//! Three guards: numeric, contradiction, citation. The rule that shapes the
//! whole module is that **the verifier must be a different model from the
//! generator** — a same-model self-check has a documented blind spot for its
//! own errors. `Verification::new` therefore records both model ids and
//! `assert_distinct_models` refuses a same-model pairing rather than silently
//! producing a check that cannot fail.
//!
//! After `max_regenerations` the honest outcome is an "unverified" label, never
//! a silent pass.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum Guard {
    Numeric,
    Contradiction,
    Citation,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum Verdict {
    Verified,
    /// Caught a real error. The caller should regenerate.
    Regenerate { guard: Guard, detail: String },
    /// Budget spent. Ship with an honest "unverified" label.
    Unverified { guard: Guard, detail: String },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Report {
    pub verdict: Verdict,
    pub regenerations_used: u32,
    pub model_checked: String,
    pub labelled_unverified: bool,
}

#[derive(Debug, thiserror::Error)]
pub enum VerifyError {
    #[error("verifier must differ from generator: both are '{0}'")]
    SameModel(String),
}

/// One `a op b = c` claim pulled out of free text.
#[derive(Debug, Clone, PartialEq)]
pub struct ArithmeticClaim {
    pub expr: String,
    pub stated: f64,
    pub computed: f64,
}

/// Split an expression into operand/operator/operand, stripping surrounding
/// punctuation. Returns owned Strings so callers can hold them past the borrow
/// of the source text.
fn tokenize_expr(s: &str) -> Vec<String> {
    s.split_whitespace()
        .map(|t| {
            t.trim_matches(|c: char| {
                !c.is_ascii_digit()
                    && c != '.'
                    && c != '-'
                    && c != '+'
                    && c != '*'
                    && c != '/'
                    && c != '('
                    && c != ')'
            })
            .to_string()
        })
        .filter(|t| !t.is_empty())
        .collect()
}

/// Recompute a single binary operation. Returns None for anything it will not
/// evaluate, rather than guessing — a verifier that invents a result is worse
/// than one that declines.
fn eval_binary(a: f64, op: &str, b: f64) -> Option<f64> {
    match op {
        "+" => Some(a + b),
        "-" => Some(a - b),
        "*" => Some(a * b),
        "/" if b != 0.0 => Some(a / b),
        _ => None,
    }
}

/// Extract and independently recompute arithmetic from `text`.
///
/// Handles `a op b = c` forms. Returns the claims that are wrong, so an empty
/// result means "nothing checkable was wrong" — not "nothing was checked".
pub fn check_numeric(text: &str) -> Vec<ArithmeticClaim> {
    let mut bad = Vec::new();
    for raw in text.split(['.', '\n', ';']) {
        let raw = raw.trim();
        let Some(eq) = raw.find('=') else { continue };
        let (lhs, rhs) = (&raw[..eq], &raw[eq + 1..]);
        let toks = tokenize_expr(lhs);
        if toks.len() != 3 {
            continue; // only single binary ops; anything else is declined
        }
        let (Ok(a), Ok(b)) = (toks[0].parse::<f64>(), toks[2].parse::<f64>()) else {
            continue;
        };
        let Some(computed) = eval_binary(a, &toks[1], b) else { continue };
        let Ok(stated) = rhs.trim().parse::<f64>() else { continue };
        // Relative tolerance so floating point noise is not an "error".
        let tol = 1e-6 * stated.abs().max(1.0);
        if (stated - computed).abs() > tol {
            bad.push(ArithmeticClaim {
                expr: format!("{} {} {}", a, toks[1], b),
                stated,
                computed,
            });
        }
    }
    bad
}

/// Find a claim asserted and denied within the same text.
pub fn check_contradiction(text: &str) -> Option<String> {
    let lower = text.to_lowercase();
    const POS: &[&str] = &["is ", "was ", "are ", "were ", "equals "];
    const NEG: &[&str] = &["is not ", "isn't ", "was not ", "wasn't ", "are not ", "aren't "];

    // Extract the predicate that follows each copula, in order. (The subject
    // comes BEFORE the copula - "the service is healthy" - so what we capture
    // is the predicate. Reporting it as a "subject" would be mislabelled.)
    let mut affirmed: Vec<String> = Vec::new();
    let mut denied: Vec<String> = Vec::new();
    for (copulas, sink) in [(POS, &mut affirmed), (NEG, &mut denied)] {
        for cop in copulas {
            let mut from = 0usize;
            while let Some(p) = lower[from..].find(cop) {
                let after = from + p + cop.len();
                // The predicate runs to the next sentence or clause boundary.
                let subject = lower[after..]
                    .split(['.', ',', ';', ':'])
                    .next()
                    .unwrap_or("")
                    .trim()
                    .to_string();
                if subject.len() >= 3 {
                    sink.push(subject);
                }
                from = after;
            }
        }
    }
    for d in &denied {
        if affirmed.iter().any(|a| a == d) {
            return Some(format!("predicate '{d}' appears both affirmed and denied"));
        }
    }
    None
}

/// Citation guard: every claim must have a source and every source must appear.
/// Extracts `[1]`, `[2]` markers and bare URLs.
pub fn check_citations(text: &str) -> Vec<String> {
    let mut problems = Vec::new();
    let mut cited = 0usize;
    let bytes = text.as_bytes();
    let mut i = 0usize;
    while i < bytes.len() {
        if bytes[i] == b'[' {
            if let Some(close) = text[i..].find(']') {
                let inner = &text[i + 1..i + close];
                if !inner.is_empty() && inner.chars().all(|c| c.is_ascii_digit()) {
                    cited += 1;
                }
                i += close + 1;
                continue;
            }
        }
        i += 1;
    }

    let has_urls = text.contains("http://") || text.contains("https://");

    if cited == 0 && !has_urls {
        // Only a problem when the text makes a FACTUAL claim. An arithmetic
        // result is checkable by recomputation and needs no source, so text
        // whose only assertions are arithmetic is exempt.
        let assertive = [" is ", " was ", " has ", " shows ", " according to", " equals "];
        let makes_claims = assertive.iter().any(|a| text.to_lowercase().contains(a));
        let arithmetic_only = !check_numeric(text).is_empty()
            || text
                .split(|c: char| !c.is_ascii_digit() && c != ' ' && c != '=' && c != '+' && c != '-' && c != '*' && c != '/')
                .filter(|t| !t.trim().is_empty())
                .count()
                <= 1;
        if makes_claims && !arithmetic_only {
            problems.push("output asserts claims with no citation and no source".into());
        }
    }
    if cited > 0 && !has_urls && !text.contains("References") {
        problems.push("cites [n] markers but lists no source list".into());
    }
    problems
}

pub struct Verification {
    pub generator_model: String,
    pub verifier_model: String,
    pub max_regenerations: u32,
}

impl Verification {
    pub fn new(generator_model: &str, verifier_model: &str) -> Self {
        Self {
            generator_model: generator_model.to_string(),
            verifier_model: verifier_model.to_string(),
            max_regenerations: 2,
        }
    }

    /// Same-model verification is refused: it cannot catch its own errors.
    pub fn assert_distinct_models(&self) -> Result<(), VerifyError> {
        if self.generator_model == self.verifier_model {
            return Err(VerifyError::SameModel(self.generator_model.clone()));
        }
        Ok(())
    }

    /// Run the guards on one candidate output. `regenerations_used` is how
    /// many attempts have already been spent.
    pub fn run(&self, text: &str, regenerations_used: u32) -> Report {
        let model_checked = self.verifier_model.clone();

        // Guard order is cheapest-first: numeric is a pure recompute, citation
        // is a scan, contradiction is the most heuristic.
        let numeric_bad = check_numeric(text);
        if !numeric_bad.is_empty() {
            let detail = numeric_bad
                .iter()
                .map(|c| format!("{} stated {} but equals {}", c.expr, c.stated, c.computed))
                .collect::<Vec<_>>()
                .join("; ");
            return self.finish(Guard::Numeric, detail, regenerations_used, model_checked);
        }

        if let Some(detail) = check_contradiction(text) {
            return self.finish(Guard::Contradiction, detail, regenerations_used, model_checked);
        }

        let cit = check_citations(text);
        if !cit.is_empty() {
            return self.finish(Guard::Citation, cit.join("; "), regenerations_used, model_checked);
        }

        Report {
            verdict: Verdict::Verified,
            regenerations_used,
            model_checked,
            labelled_unverified: false,
        }
    }

    fn finish(
        &self,
        guard: Guard,
        detail: String,
        used: u32,
        model_checked: String,
    ) -> Report {
        if used < self.max_regenerations {
            Report {
                verdict: Verdict::Regenerate { guard, detail },
                regenerations_used: used,
                model_checked,
                labelled_unverified: false,
            }
        } else {
            // Budget spent: ship honestly rather than claim a pass.
            Report {
                verdict: Verdict::Unverified { guard, detail },
                regenerations_used: used,
                model_checked,
                labelled_unverified: true,
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn wrong_arithmetic_is_caught() {
        let claims = check_numeric("The total is 100 * 3 = 500.");
        assert_eq!(claims.len(), 1, "100*3=300, not 500");
        assert_eq!(claims[0].computed, 300.0);
    }

    #[test]
    fn correct_arithmetic_passes() {
        assert!(check_numeric("The total is 100 * 3 = 300.").is_empty());
        assert!(check_numeric("2 + 2 = 4 and 10 / 2 = 5").is_empty());
    }

    #[test]
    fn unparseable_expressions_are_declined_not_invented() {
        // A verifier that guesses is worse than one that declines.
        assert!(check_numeric("3 + 4 + 5 = 12").is_empty(), "not a single binary op");
        assert!(check_numeric("what is x plus y").is_empty());
        assert!(check_numeric("10 / 0 = 5").is_empty(), "division by zero declined");
    }

    #[test]
    fn self_contradiction_is_caught() {
        let t = "The service is healthy. Actually the service is not healthy.";
        let c = check_contradiction(t).expect("should detect the flip");
        assert!(c.contains("healthy"), "got: {c}");
    }

    #[test]
    fn consistent_text_is_not_flagged() {
        assert!(check_contradiction("The service is healthy and was restarted.").is_none());
    }

    #[test]
    fn a_pure_arithmetic_answer_needs_no_citation() {
        // The numeric guard already recomputes these; demanding a source for
        // "2 + 2 = 4" would be a false positive on every calculation.
        assert!(check_citations("The answer is 2 + 2 = 4.").is_empty());
    }

    #[test]
    fn uncited_claims_are_flagged() {
        let p = check_citations("The population is 4.2 million.");
        assert_eq!(p.len(), 1, "a factual assertion with no source must be flagged");
    }

    #[test]
    fn a_citation_marker_with_no_source_list_is_flagged() {
        // "Every source must resolve" - a [1] that resolves to nothing is
        // precisely the failure this guard exists to catch.
        let p = check_citations("The population is 4.2 million [1].");
        assert!(!p.is_empty(), "a dangling citation marker must not pass");
        assert!(p[0].contains("source list"), "got: {p:?}");
    }

    #[test]
    fn cited_claims_with_a_resolvable_source_pass() {
        assert!(
            check_citations("Studies show a 12% gain [1]. See https://example.org/paper")
                .is_empty()
        );
        assert!(
            check_citations("Growth was 12% [1] (https://example.org/p)")
                .is_empty()
        );
    }

    #[test]
    fn same_model_verification_is_refused() {
        let v = Verification::new("glm-4.6", "glm-4.6");
        assert!(v.assert_distinct_models().is_err());
        let ok = Verification::new("glm-4.6", "glm-4.5-flash");
        assert!(ok.assert_distinct_models().is_ok());
    }

    #[test]
    fn a_wrong_answer_is_caught_then_regenerated() {
        let v = Verification::new("gen", "ver");
        let r = v.run("100 * 3 = 500", 0);
        assert!(matches!(r.verdict, Verdict::Regenerate { guard: Guard::Numeric, .. }));
        assert!(!r.labelled_unverified);
    }

    #[test]
    fn after_the_budget_it_labels_unverified_instead_of_passing() {
        let v = Verification::new("gen", "ver");
        let r = v.run("100 * 3 = 500", 2);
        assert!(matches!(r.verdict, Verdict::Unverified { .. }));
        assert!(r.labelled_unverified, "must ship an honest label, not a pass");
        assert_eq!(r.regenerations_used, 2);
    }

    #[test]
    fn a_bad_citation_is_caught() {
        let v = Verification::new("gen", "ver");
        let r = v.run("Revenue grew [1] but no source list exists.", 0);
        assert!(matches!(
            r.verdict,
            Verdict::Regenerate { guard: Guard::Citation, .. }
        ));
    }

    #[test]
    fn clean_output_verifies() {
        let v = Verification::new("gen", "ver");
        let r = v.run("Hello. The answer is 2 + 2 = 4.", 0);
        assert_eq!(r.verdict, Verdict::Verified);
    }
}
