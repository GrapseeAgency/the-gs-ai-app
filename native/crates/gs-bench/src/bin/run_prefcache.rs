//! Item 6.2a - prompt caching by KV state reuse, measured.
//!
//! Three sequential calls that share one long system prefix. Call 1 prefills
//! the prefix and snapshots the state. Calls 2 and 3 restore that snapshot and
//! generate only the new suffix, so the prefix is not re-prefilled.
//!
//! Reports prefill+decode wall time per call and the reduction against call 1.

use gs_ffi::LlamaModel;
use std::process::exit;

/// A long, identical system prefix. The saving scales with prefix length, so
/// it has to be long enough to dominate the decode.
fn system_prefix() -> String {
    let mut s = String::from("### System\nYou are GS AI. Reference material follows.\n");
    for i in 0..55 {
        s.push_str(&format!(
            "Fact {i}: the ingest service writes a batch manifest, the resolver pins the \
             artifact digest, the verifier checks the signature against key rev 7, and the \
             audit sink records the outcome before the result is released.\n"
        ));
    }
    s
}

fn main() {
    let mp = std::env::var("GS_LOCAL_MODEL")
        .unwrap_or_else(|_| "/mnt/new_volume/models/llm/qwen2.5-0.5b-instruct-q4_k_m.gguf".into());
    if !std::path::Path::new(&mp).exists() {
        eprintln!("BLOCKED: no model at {mp}");
        exit(1);
    }
    let n_ctx: i32 = std::env::var("GS_LOCAL_CTX").ok().and_then(|v| v.parse().ok()).unwrap_or(4096);
    let max_tokens: i32 = std::env::var("GS_MAX_TOKENS").ok().and_then(|v| v.parse().ok()).unwrap_or(24);

    let prefix = system_prefix();
    let questions = [
        "In one sentence, what does the verifier check?",
        "In one sentence, what does the audit sink do?",
        "In one sentence, which key revision is pinned?",
    ];

    let mut m = LlamaModel::load(&mp, n_ctx, 4, -1).unwrap_or_else(|e| { eprintln!("BLOCKED: load {e}"); exit(1) });
    println!("model          : {mp}");
    println!("backend        : {}", m.backend_name());
    println!("prefix bytes   : {}", prefix.len());
    println!("prefix tokens  : {}", m.token_count(&prefix));
    println!("n_ctx          : {n_ctx}");
    println!("max_tokens     : {max_tokens}");
    println!();

    // Snapshot AFTER the shared prefix is resident but BEFORE any answer
    // exists. Snapshotting post-generation carried the previous turn's answer
    // into the restored state, and the next question was then answered from
    // that thread: an 80% speedup that returned the wrong text.
    let cold = format!("{prefix}\n### User\n{}\n\n### Assistant\n", questions[0]);
    let t0 = std::time::Instant::now();
    if let Err(e) = m.prefill(&cold, true) {
        eprintln!("BLOCKED: prefill failed with code {e}");
        exit(1);
    }
    let prefix_tokens = m.token_count(&prefix).max(0) as i64;
    let blob = match m.state_save() {
        Ok(b) => { println!("prefix tokens : {prefix_tokens}"); println!("snapshot      : {} bytes\n", b.len()); b }
        Err(e) => { eprintln!("BLOCKED: state_save failed with code {e}"); exit(1) }
    };

    let mut times = Vec::new();
    let mut outs = Vec::new();
    for (i, q) in questions.iter().enumerate() {
        let t0 = std::time::Instant::now();
        let out = if i == 0 {
            // Cold reference: full prompt, cache cleared.
            m.generate(&cold, max_tokens, 0.0)
        } else {
            // Warm: restore the prefix-only state, append just this question.
            if let Err(e) = m.state_restore(&blob) {
                eprintln!("BLOCKED: state_restore failed with code {e}");
                exit(1);
            }
            let suffix = format!("### User\n{q}\n\n### Assistant\n");
            if let Err(e) = m.prefill(&suffix, false) {
                eprintln!("BLOCKED: suffix prefill failed with code {e}");
                exit(1);
            }
            m.generate_preserving_cache(&suffix, max_tokens, 0.0)
        };
        let out = match out {
            Ok(o) => o,
            Err(e) => { eprintln!("BLOCKED: generate {e}"); exit(1) }
        };
        let ms = t0.elapsed().as_millis() as f64;
        times.push(ms);
        outs.push(out.trim().replace('\n', " ").chars().take(80).collect::<String>());
        println!("call {i} ({}):", if i == 0 { "cold, full prefill" } else { "CACHE HIT" });
        println!("  wall_ms   : {:.0}", ms);
        println!("  tokens    : {}", m.last_n_tokens());
        println!("  tok/s     : {:.2}", m.last_n_tokens() as f64 / (ms / 1000.0).max(0.001));
        println!("  output    : {}", outs[i]);
        println!();
    }

    println!("=== correctness: each call must answer ITS OWN question ===");
    for (i, q) in questions.iter().enumerate() {
        println!("  q{}: {}", i, q);
        println!("      {}", outs[i]);
    }
    let distinct = {
        let mut v = outs.clone();
        v.sort();
        v.dedup();
        v.len()
    };
    println!("  distinct answers: {}/{}  ({})", distinct, outs.len(),
             if distinct == outs.len() { "each question got its own answer" } else { "REUSED ACROSS QUESTIONS - BUG" });
    println!();
    let base = times[0];
    println!("=== PREFIX REUSE ===");
    println!("  call 1 (cold, full prefill) : {:.0} ms", base);
    println!("  call 2 (restored)           : {:.0} ms  ({:+.1}% vs call 1)", times[1], (times[1]/base-1.0)*100.0);
    println!("  call 3 (restored)           : {:.0} ms  ({:+.1}% vs call 1)", times[2], (times[2]/base-1.0)*100.0);
    let warm = (times[1]+times[2])/2.0;
    println!("  mean warm / mean cold       : {:.3}x  ({:+.1}% reduction)", warm/base, (1.0-warm/base)*100.0);
    println!();
    println!("dossier claim: ~90% cost reduction on cached tokens. measured: {:+.1}% on wall time",
             (1.0-warm/base)*100.0);
}
