//! Item 6.2g demo: the three named compaction systems, measured.
//!
//! A ten-turn conversation with identifiers planted throughout, then each
//! system applied to it. For every system: tokens before, tokens after,
//! reduction percentage, and a checklist of every identifier that must have
//! survived, marked present or MISSING.

use gs_core::compaction::{approx_tokens, attn_compress, extract_identifiers, focus_compact, sspm_compact};
use std::collections::BTreeSet;
use std::process::exit;

fn conversation() -> Vec<String> {
    vec![
        "Hi there, how are you doing today? I hope the week is going well for you so far.".into(),
        "The weather has been quite pleasant this week and the garden is finally blooming nicely.".into(),
        "We discussed the deployment pipeline at length and talked about the various stages involved.".into(),
        "The invoice for March is INV-4471 and payment is due on 2026-03-01, so that needs attention.".into(),
        "We decided that retries must be bounded to 3 attempts with exponential backoff between them.".into(),
        "The staging environment runs on node-07 and the logs are written to /var/log/pipeline.log daily.".into(),
        "Honestly the conversation so far has been mostly pleasantries and general discussion of weather.".into(),
        "The API key for the service is stored at /etc/gs/secrets.yaml and is rotated every 90 days.".into(),
        "We should always verify the signature before releasing the artifact to the caller downstream.".into(),
        "The release tag is v2.14.0 and the build number is 4471, deployed on 2026-03-02 at 09:15.".into(),
    ]
}

/// Report for a LOSSLESS compactor: every identifier in the input must survive.
fn report(name: &str, claim: &str, before: &str, after: &str, must_survive: &BTreeSet<String>, surviving: &BTreeSet<String>) {
    let b = approx_tokens(before);
    let a = approx_tokens(after);
    let reduction = if b > 0.0 { (1.0 - a / b) * 100.0 } else { 0.0 };
    println!("=== {name} ===");
    println!("  before : {} chars, ~{:.0} tokens", before.chars().count(), b);
    println!("  after  : {} chars, ~{:.0} tokens", after.chars().count(), a);
    println!("  REDUCTION: {:.1}%", reduction);
    println!("  dossier claim: {claim}   measured: {reduction:.1}%");
    let missing: Vec<&String> = must_survive.difference(surviving).collect();
    println!("  identifier survival ({} must survive):", must_survive.len());
    for id in must_survive {
        println!("    [{}] {}", if surviving.contains(id) { "OK  " } else { "MISS" }, id);
    }
    println!(
        "  VERDICT: {}",
        if missing.is_empty() {
            format!("all {} identifiers survived", must_survive.len())
        } else {
            format!("DATA LOSS - {} identifier(s) dropped: {:?}", missing.len(), missing)
        }
    );
    println!();
}

fn main() {
    let turns = conversation();
    let original = turns.join("\n");
    let all_ids: BTreeSet<String> = turns
        .iter()
        .flat_map(|t| extract_identifiers(t))
        .map(|i| i.text)
        .collect();

    println!("conversation: {} turns, {} chars, ~{:.0} tokens",
             turns.len(), original.chars().count(), approx_tokens(&original));
    println!("identifiers planted: {}", all_ids.len());
    for id in &all_ids { println!("  {id}"); }
    println!();

    // --- SSPM ---
    let (patches, sspm_ids) = sspm_compact(&turns);
    let sspm_out: String = patches.iter().map(|p| p.text.clone()).collect::<Vec<_>>().join("\n");
    report("SSPM (structural semantic patch memory)", "48.7%", &original, &sspm_out, &all_ids, &sspm_ids);
    println!("  retained patches ({}):", patches.len());
    for p in &patches {
        println!("    - {}  [{}]", p.text.chars().take(76).collect::<String>(), p.identifiers.join(", "));
    }
    println!();

    // --- AttnCompress ---
    // keep_top is derived from the original token count so the target retention
    // ratio is explicit rather than a magic number.
    let target = (approx_tokens(&original) * 0.78) as usize; // aim at ~22% kept
    let (attn_lines, attn_ids, is_proxy) = attn_compress(&turns, target);
    let attn_out = attn_lines.join("\n");
    report("AttnCompress (attention-guided)", "21.6%", &original, &attn_out, &all_ids, &attn_ids);
    println!("  NOTE: {} -- this build has no per-layer attention map exposed, so the", is_proxy);
    println!("        importance signal is an IDF+recency stand-in, not measured attention.");
    println!("        The compression ratio and identifier survival ARE measured.");
    println!();

    // --- FocusAgent ---
    //
    // FocusAgent has a DIFFERENT contract from the other two. SSPM and
    // AttnCompress compact a conversation without losing anything, so every
    // identifier must survive. FocusAgent deliberately drops whatever is
    // irrelevant to the current task, so an identifier the task never asked
    // about is SUPPOSED to disappear. Holding it to the lossless contract
    // would be measuring it against the wrong specification.
    //
    // The correct check is that the facts the task asked for are all present.
    let task = "What is the invoice number, when is it due, and where are the logs?";
    let asked_for: BTreeSet<String> = ["INV-4471", "2026-03-01", "/var/log/pipeline.log"]
        .iter().map(|s| s.to_string()).collect();
    let obs = original.clone();
    let (focus_lines, focus_ids) = focus_compact(&obs, task);
    let focus_out = focus_lines.join("\n");
    report("FocusAgent (task-relative observation filter)", ">50%", &obs, &focus_out, &asked_for, &focus_ids);
    let legitimately_dropped: Vec<&String> = all_ids.difference(&focus_ids).collect();
    println!("  dropped as task-irrelevant (by design, NOT data loss): {} identifier(s)", legitimately_dropped.len());
    for id in &legitimately_dropped { println!("    - {id}"); }
    println!("  task: {task}");
    println!("  retained {} of {} lines:", focus_lines.len(), obs.lines().count());
    for l in &focus_lines {
        println!("    - {}", l.chars().take(88).collect::<String>());
    }
    println!();

    // A second task must select differently, which is the whole claim of
    // FocusAgent: relevance is task-relative, not a fixed subset.
    let task2 = "What is the release tag and build number?";
    let (focus2, _) = focus_compact(&obs, task2);
    println!("  same observation, different task: {task2:?}");
    println!("    retained {} lines (vs {} for the previous task)", focus2.len(), focus_lines.len());
    // Task-relative means the two tasks select DIFFERENT subsets, not that they
    // are disjoint. One task's answer may legitimately contain the other's.
    let same = focus_lines == focus2;
    println!("    identical selection: {same}  -> relevance is task-relative: {}", !same);
    if same {
        eprintln!("BLOCKED: FocusAgent selected the same lines for two different tasks");
        exit(2);
    }
}
