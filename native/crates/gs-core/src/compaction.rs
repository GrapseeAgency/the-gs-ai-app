//! Named compaction systems from dossier 6.2g.
//!
//! Three different theories of what is safe to drop from a long conversation:
//!
//! - SSPM (Structural Semantic Patch Memory): keep decisions, identifiers and
//!   constraints. Drop prose. The claim is that meaning lives in structure, not
//!   in sentences.
//! - AttnCompress: keep the tokens the model would attend to. The claim is that
//!   attention is the right importance signal.
//! - FocusAgent: given a task, keep only the lines of an observation relevant to
//!   it. The claim is that relevance is task-relative, not absolute.
//!
//! DAG trim (Phase 5) is the fourth; it is not reimplemented here.
//!
//! All three share one hard requirement, checked by the demo rather than
//! asserted: every identifier that appeared in the original must survive. A
//! compaction that drops "INV-4471" is not a compaction, it is data loss with a
//! token-count benefit attached.

use std::collections::{BTreeMap, BTreeSet};

/// An identifier worth protecting: numbers, versions, paths, hashes, IDs.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Identifier {
    pub text: String,
    pub kind: &'static str,
}

/// Extract the tokens that must never be dropped.
///
/// Deliberately conservative and syntactic. Anything matching a number, a
/// version, a path, a URL, a hash, an all-caps token, a UUID or a key=value pair
/// is an identifier, because the cost of keeping a non-identifier is a few
/// tokens and the cost of dropping an identifier is a wrong answer.
pub fn extract_identifiers(text: &str) -> Vec<Identifier> {
    let mut out = Vec::new();
    let mut seen = BTreeSet::new();
    let bytes: Vec<char> = text.chars().collect();
    let mut i = 0usize;
    while i < bytes.len() {
        let c = bytes[i];
        let start = i;

        // path or url
        if c == '/' && i + 1 < bytes.len() && (bytes[i + 1] == '/' || bytes[i + 1].is_ascii_alphabetic()) {
            while i < bytes.len()
                && !bytes[i].is_whitespace()
                && bytes[i] != ')'
                && bytes[i] != ','
                && bytes[i] != '"'
                && bytes[i] != ' '
            {
                i += 1;
            }
            push(&mut out, &mut seen, &bytes[start..i], "path");
            continue;
        }
        // key=value
        if c.is_ascii_alphabetic() || c == '_' {
            let mut j = i;
            while j < bytes.len() && (bytes[j].is_ascii_alphanumeric() || bytes[j] == '_' || bytes[j] == '-' || bytes[j] == '.') {
                j += 1;
            }
            if j < bytes.len() && bytes[j] == '=' {
                while j < bytes.len() && !bytes[j].is_whitespace() && bytes[j] != ',' && bytes[j] != ')' {
                    j += 1;
                }
                push(&mut out, &mut seen, &bytes[start..j], "assignment");
                i = j;
                continue;
            }
            // ALLCAPS token of 2+ chars, possibly with a hyphen
            if j - i >= 2 && bytes[start..j].iter().all(|c| !c.is_lowercase()) {
                let mut k = j;
                while k < bytes.len() && (bytes[k] == '-' || bytes[k] == '_' || bytes[k].is_ascii_alphanumeric()) {
                    k += 1;
                }
                push(&mut out, &mut seen, &bytes[start..k], "constant");
                i = k;
                continue;
            }
            i = j;
            continue;
        }
        // number, possibly with a version or date shape
        if c.is_ascii_digit() {
            while i < bytes.len() && (bytes[i].is_ascii_digit() || bytes[i] == '.' || bytes[i] == '-' || bytes[i] == ':' || bytes[i] == '/') {
                i += 1;
            }
            while i > start && (bytes[i - 1] == '.' || bytes[i - 1] == '-' || bytes[i - 1] == ':' || bytes[i - 1] == '/') {
                i -= 1;
            }
            push(&mut out, &mut seen, &bytes[start..i], "number");
            continue;
        }
        i += 1;
    }
    out
}

fn push(out: &mut Vec<Identifier>, seen: &mut BTreeSet<String>, chars: &[char], kind: &'static str) {
    let text: String = chars.iter().collect();
    if text.is_empty() {
        return;
    }
    if seen.insert(text.clone()) {
        out.push(Identifier { text, kind });
    }
}

// ---------------------------------------------------------------------------
// SSPM: structural semantic patch memory
// ---------------------------------------------------------------------------

/// One retained patch: a statement plus the identifiers it carries.
#[derive(Debug, Clone)]
pub struct Patch {
    pub text: String,
    pub identifiers: Vec<String>,
}

/// Extract a semantic patch per turn: the sentences carrying identifiers or a
/// decision verb, verbatim. Prose with neither is dropped.
pub fn sspm_compact(turns: &[String]) -> (Vec<Patch>, BTreeSet<String>) {
    let mut patches = Vec::new();
    let mut kept_ids = BTreeSet::new();
    for turn in turns {
        for sentence in split_sentences(turn) {
            let ids = extract_identifiers(&sentence);
            let decision = has_decision_marker(&sentence);
            if ids.is_empty() && !decision {
                continue;
            }
            for id in &ids {
                kept_ids.insert(id.text.clone());
            }
            patches.push(Patch {
                text: sentence,
                identifiers: ids.into_iter().map(|i| i.text).collect(),
            });
        }
    }
    (patches, kept_ids)
}

fn split_sentences(text: &str) -> Vec<String> {
    // A sentence ends at a period followed by whitespace or end of string.
    // Splitting on every '.' tore paths and versions apart: "/etc/gs/secrets.yaml"
    // became "/etc/gs/secrets" and "yaml", and the identifier was destroyed by
    // the compactor that was supposed to be protecting it.
    let chars: Vec<char> = text.chars().collect();
    let mut out = Vec::new();
    let mut cur = String::new();
    for (i, &c) in chars.iter().enumerate() {
        cur.push(c);
        let boundary = c == '\n'
            || c == ';'
            || (c == '.' && (i + 1 >= chars.len() || chars[i + 1].is_whitespace()));
        if boundary {
            let t = cur.trim().to_string();
            if !t.is_empty() { out.push(t); }
            cur.clear();
        }
    }
    let t = cur.trim().to_string();
    if !t.is_empty() { out.push(t); }
    out
}

fn has_decision_marker(s: &str) -> bool {
    const MARKERS: &[&str] = &[
        "must", "should", "will", "shall", "decided", "decision", "agreed", "require",
        "constraint", "instead", "never", "always", "only", "cannot", "must not",
    ];
    let l = s.to_lowercase();
    MARKERS.iter().any(|m| l.contains(m))
}

// ---------------------------------------------------------------------------
// AttnCompress: attention-guided token selection
// ---------------------------------------------------------------------------

/// A proxy for attention weight, because a real attention map is not available
/// through this ABI without instrumenting every layer.
///
/// The proxy is: tokens that are rare in the conversation and appear late are
/// what a model attends to, because they carry the information the earlier turns
/// do not. This is a STAND-IN and the demo says so; it is not a measurement of
/// attention. What is measured is the compression ratio and the identifier
/// survival rate of the result.
pub fn attn_compress(turns: &[String], keep_top: usize) -> (Vec<String>, BTreeSet<String>, bool) {
    // Score every token by inverse document frequency across turns, with a
    // recency bonus. High score = likely attended.
    let mut df: BTreeMap<String, usize> = BTreeMap::new();
    let mut total = 0usize;
    for t in turns {
        let mut seen_here = BTreeSet::new();
        for w in tokenize(t) {
            if seen_here.insert(w.clone()) {
                *df.entry(w).or_insert(0usize) += 1;
            }
            total += 1;
        }
    }
    #[derive(Debug)]
    struct Scored {
        turn: usize,
        token: String,
        score: f64,
    }
    let mut scored = Vec::new();
    for (ti, t) in turns.iter().enumerate() {
        for w in tokenize(t) {
            let d = *df.get(&w).unwrap_or(&1).max(&1);
            let idf = ((total as f64) / d as f64).ln();
            let recency = 1.0 + (ti as f64) * 0.15;
            scored.push(Scored { turn: ti, token: w, score: idf * recency });
        }
    }
    scored.sort_by(|a, b| b.score.partial_cmp(&a.score).unwrap_or(std::cmp::Ordering::Equal));

    // Identifiers are force-kept regardless of score. An attention proxy that
    // drops "INV-4471" is useless for the only property that matters.
    let all_ids: BTreeSet<String> = turns
        .iter()
        .flat_map(|t| extract_identifiers(t))
        .map(|i| i.text)
        .collect();
    let id_tokens: BTreeSet<String> = all_ids
        .iter()
        .flat_map(|s| tokenize(s))
        .collect();

    let mut kept_by_turn: BTreeMap<usize, Vec<String>> = BTreeMap::new();
    let mut used = 0usize;
    for s in &scored {
        if used >= keep_top {
            break;
        }
        if id_tokens.contains(&s.token) {
            continue; // added unconditionally below
        }
        kept_by_turn.entry(s.turn).or_default().push(s.token.clone());
        used += 1;
    }
    // Re-add each identifier to the turn it actually came from. Matching on the
    // identifier text rather than its lowercased tokens: tokenize() lowercases,
    // so looking for "inv" inside "INV-4471" fails and the identifier vanishes.
    for id in &all_ids {
        if let Some((ti, _)) = turns.iter().enumerate().find(|(_, t)| t.contains(id.as_str())) {
            let e = kept_by_turn.entry(ti).or_default();
            for t in tokenize(id) {
                if !e.contains(&t) {
                    e.push(t);
                }
            }
        }
    }

    let mut out = Vec::new();
    let mut surviving = BTreeSet::new();
    for (ti, toks) in &kept_by_turn {
        let line = toks.join(" ");
        // Compare tokenized forms on both sides. "INV-4471".split_whitespace()
        // yields ["INV-4471"] while the kept tokens are ["inv", "4471"], so a
        // whitespace split reports the identifier lost when it survived.
        let tokset: BTreeSet<String> = toks.iter().cloned().collect();
        for id in &all_ids {
            let want = tokenize(id);
            if !want.is_empty() && want.iter().all(|w| tokset.contains(w)) {
                surviving.insert(id.clone());
            }
        }
        let _ = ti;
        out.push(line);
    }
    (out, surviving, true)
}

// ---------------------------------------------------------------------------
// FocusAgent: task-relative observation filtering
// ---------------------------------------------------------------------------

/// Keep only the lines of `observation` that share a content word with `task`.
/// Numbers and identifiers on a kept line survive with it.
pub fn focus_compact(observation: &str, task: &str) -> (Vec<String>, BTreeSet<String>) {
    let stop: BTreeSet<&str> = [
        "the", "a", "an", "is", "are", "was", "were", "to", "of", "and", "or", "in", "on", "for",
        "with", "it", "this", "that", "at", "by", "from", "as", "be", "has", "have", "do", "does",
    ]
    .iter()
    .copied()
    .collect();
    let task_terms: BTreeSet<String> = tokenize(task)
        .into_iter()
        .filter(|w| w.len() > 2 && !stop.contains(w.as_str()))
        .collect();

    let all_ids: BTreeSet<String> = extract_identifiers(observation).into_iter().map(|i| i.text).collect();
    let mut kept = Vec::new();
    let mut surviving = BTreeSet::new();
    for line in observation.lines() {
        if line.trim().is_empty() {
            continue;
        }
        let terms: BTreeSet<String> = tokenize(line)
            .into_iter()
            .filter(|w| w.len() > 2 && !stop.contains(w.as_str()))
            .collect();
        let overlap = terms.intersection(&task_terms).count();
        // A line is kept if it shares a term with the task, or if it carries an
        // identifier the task mentions.
        let line_ids = extract_identifiers(line);
        let mentions = line_ids.iter().any(|i| task.contains(&i.text));
        if overlap > 0 || mentions {
            kept.push(line.to_string());
            for i in &line_ids {
                if observation.contains(&i.text) {
                    surviving.insert(i.text.clone());
                }
            }
        }
    }
    let _ = all_ids;
    (kept, surviving)
}

fn tokenize(s: &str) -> Vec<String> {
    s.split(|c: char| !c.is_ascii_alphanumeric() && c != '.' && c != '-' && c != '_' && c != '/')
        .map(|w| w.trim_matches(|c: char| c == '.' || c == '-' || c == '/').to_lowercase())
        .filter(|w| !w.is_empty())
        .collect()
}

/// Rough token count, used only to state a size change.
pub fn approx_tokens(s: &str) -> f64 {
    s.chars().count() as f64 / 4.0
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn identifiers_are_found_and_deduplicated() {
        let ids = extract_identifiers("Invoice INV-4471 is due 2026-03-01, see /tmp/report.pdf and KEY=abc");
        let texts: Vec<&str> = ids.iter().map(|i| i.text.as_str()).collect();
        assert!(texts.contains(&"INV-4471"), "{texts:?}");
        assert!(texts.contains(&"2026-03-01"), "{texts:?}");
        assert!(texts.contains(&"/tmp/report.pdf"), "{texts:?}");
        assert!(texts.contains(&"KEY=abc"), "{texts:?}");
        let mut d = texts.clone();
        d.sort();
        d.dedup();
        assert_eq!(d.len(), texts.len(), "must be deduplicated");
    }

    #[test]
    fn sspm_drops_prose_and_keeps_every_identifier() {
        let turns = vec![
            "The weather is quite nice today and the birds are singing.".to_string(),
            "The invoice is INV-4471 and it is due 2026-03-01.".to_string(),
        ];
        let (patches, ids) = sspm_compact(&turns);
        assert_eq!(patches.len(), 1, "the prose-only sentence must be dropped");
        assert!(ids.contains("INV-4471"));
        assert!(ids.contains("2026-03-01"));
    }

    #[test]
    fn sspm_keeps_a_decision_with_no_identifier() {
        let turns = vec!["We decided that retries must be bounded.".to_string()];
        let (patches, _) = sspm_compact(&turns);
        assert_eq!(patches.len(), 1, "a decision marker alone must retain the line");
    }

    #[test]
    fn attn_compression_keeps_every_identifier() {
        let mut turns: Vec<String> = (0..10)
            .map(|i| format!("Turn {i}: we discussed the deployment and the weather at length while planning things."))
            .collect();
        turns.push("The invoice is INV-4471 due 2026-03-01 at /srv/a.log".to_string());
        let (out, surviving, _) = attn_compress(&turns, 20usize);
        let joined = out.join(" ");
        assert!(joined.contains("inv-4471"), "identifier dropped: {joined}");
        assert!(surviving.contains("INV-4471"), "{surviving:?}");
    }

    #[test]
    fn focus_keeps_the_relevant_line_and_drops_the_rest() {
        let obs = "worker=0 shard=3 handled=ok\nDEPLOY_TOKEN=sk-live-9f3a at 2026-09-28\ndisk usage 91% on node c\nnetwork latency 4ms";
        let (kept, ids) = focus_compact(obs, "what is the DEPLOY_TOKEN?");
        assert_eq!(kept.len(), 1, "only the token line is relevant: {kept:?}");
        assert!(kept[0].contains("DEPLOY_TOKEN"));
        assert!(ids.contains("DEPLOY_TOKEN=sk-live-9f3a"), "{ids:?}");
    }

    #[test]
    fn focus_keeps_nothing_when_nothing_is_relevant() {
        let obs = "disk usage 91%\nnetwork latency 4ms";
        let (kept, _) = focus_compact(obs, "what is the DEPLOY_TOKEN?");
        assert!(kept.is_empty(), "{kept:?}");
    }
}
