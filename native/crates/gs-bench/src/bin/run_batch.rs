//! Batched decode, measured. The 50-concurrent-request test for item 7.
//!
//! Four questions, in order, because each can invalidate the next:
//!
//!   1. IS IT SELF-CONSISTENT? Running the same batch twice must give the same
//!      answer twice, and so must the serial path. Without this, a difference
//!      between the two paths cannot be attributed.
//!   2. HOW DOES IT DIVERGE? At which token does a batched output stop matching
//!      the serial one. A difference at token 0 is a different bug from a
//!      difference at token 30.
//!   3. IS IT VALID? 50 concurrent requests all return plausible, non-empty,
//!      non-degenerate output.
//!   4. HOW FAST? requests/second against the serialised baseline, reproduced
//!      here rather than quoted so both numbers come from the same minute.
//!
//! An earlier version of this reported "lossless" as identical strings. It was
//! not: 4/50 matched. The first token already differed. That turned out to be
//! backend float non-determinism, which is a materially different claim from a
//! scheduling bug, so it is now measured and named rather than asserted.

use gs_ffi::LlamaModel;
use std::time::Instant;

/// Concurrency sweep, because "how many sequences share a decode" is a real
/// parameter and picking one number without measuring the others is how a
/// throughput target gets missed for a fixable reason.
static BATCH_N: std::sync::OnceLock<usize> = std::sync::OnceLock::new();
fn batch_size() -> usize {
    *BATCH_N.get_or_init(|| {
        std::env::var("GS_BATCH_N").ok().and_then(|v| v.parse().ok()).unwrap_or(50)
    })
}
const MAX_TOKENS: i32 = 32;

fn prompt(i: usize) -> String {
    // Distinct prompts, not one repeated 50 times. Identical prompts share a
    // prefix, which would let a prefix cache flatter the batch.
    format!(
        "### User\nQuestion {}: name one primary colour and one metal. \
         Keep the answer to one short sentence.\n\n### Assistant\n",
        i + 1
    )
}

/// First index at which two strings differ, or None if one is a prefix of the
/// other. Character-level, which is what the caller can actually observe.
fn diverge(a: &str, b: &str) -> Option<usize> {
    let ab = a.as_bytes();
    let bb = b.as_bytes();
    let n = ab.len().min(bb.len());
    for i in 0..n {
        if ab[i] != bb[i] {
            return Some(i);
        }
    }
    if ab.len() == bb.len() { None } else { Some(n) }
}

fn main() {
    let model_path = std::env::var("GS_LOCAL_MODEL").unwrap_or_else(|_| {
        "/mnt/new_volume/models/llm/qwen2.5-0.5b-instruct-q4_k_m.gguf".into()
    });
    if !std::path::Path::new(&model_path).exists() {
        eprintln!("BLOCKED: no model at {model_path}");
        std::process::exit(1);
    }

    println!("model      : {model_path}");
    let n_prompts = batch_size();
    println!("prompts    : {n_prompts}, distinct");
    println!("max_tokens : {MAX_TOKENS}, greedy");
    println!();

    let prompts: Vec<String> = (0..n_prompts).map(prompt).collect();

    // ---- 1. SERIAL BASELINE: the pre-batching behaviour ------------------
    // One context, n_seq_max=1, one request at a time. Exactly what the Mutex
    // in LocalProvider produced.
    let mut serial = LlamaModel::load(&model_path, 2048, 4, -1).unwrap_or_else(|e| {
        eprintln!("BLOCKED: load failed with code {e}");
        std::process::exit(1);
    });
    println!("backend    : {}", serial.backend_name());
    println!();

    println!("=== 1. SERIAL BASELINE (n_seq_max=1, one at a time) ===");
    let t0 = Instant::now();
    let mut s1: Vec<String> = Vec::with_capacity(n_prompts);
    for p in &prompts {
        s1.push(serial.generate(p, MAX_TOKENS, 0.0).unwrap_or_else(|e| {
            eprintln!("BLOCKED: serial generate code {e}");
            std::process::exit(1);
        }));
    }
    let serial_elapsed = t0.elapsed().as_secs_f64();
    let serial_rate = n_prompts as f64 / serial_elapsed;
    println!("  {n_prompts} requests in {serial_elapsed:.2}s");
    println!("  THROUGHPUT  : {serial_rate:.2} req/s");
    println!();

    let mut batched = LlamaModel::load_with(
        &model_path, 4096, 4, -1, gs_ffi::KvType::F16, gs_ffi::KvType::F16, n_prompts as i32,
    )
    .unwrap_or_else(|e| {
        eprintln!("BLOCKED: batched load failed with code {e}");
        std::process::exit(1);
    });

    println!("=== 2. SELF-CONSISTENCY (same input twice, same path) ===");
    let b1 = batched.batch_generate(&prompts, MAX_TOKENS, 0.0).unwrap_or_else(|e| {
        eprintln!("BLOCKED: batch_generate code {e}");
        std::process::exit(1);
    });
    let b2 = batched.batch_generate(&prompts, MAX_TOKENS, 0.0).unwrap_or_else(|e| {
        eprintln!("BLOCKED: batch_generate code {e}");
        std::process::exit(1);
    });
    let batch_self_same = b1.iter().zip(&b2).filter(|(x, y)| x == y).count();

    // Serial, again, on the same context. If serial is stable and batched is
    // stable but they disagree, the difference is the batch, not a race.
    let mut s2: Vec<String> = Vec::new();
    for p in &prompts {
        s2.push(serial.generate(p, MAX_TOKENS, 0.0).unwrap_or_default());
    }
    let serial_self_same = s1.iter().zip(&s2).filter(|(x, y)| x == y).count();
    println!("  serial  run1 == run2 : {serial_self_same}/{n_prompts}");
    println!("  batched run1 == run2 : {batch_self_same}/{n_prompts}");
    println!();

    // ---- 3. DIVERGEENCE -------------------------------------------------
    println!("=== 3. BATCHED vs SERIAL: where do they differ ===");
    let mut identical = 0usize;
    let mut at_zero = 0usize;
    let mut divergences: Vec<usize> = Vec::new();
    for i in 0..n_prompts {
        let a = b1.get(i).map(String::as_str).unwrap_or("");
        let b = s1.get(i).map(String::as_str).unwrap_or("");
        if a == b {
            identical += 1;
        } else {
            let d = diverge(a, b).unwrap_or(0);
            if d == 0 { at_zero += 1; }
            divergences.push(d);
        }
    }
    println!("  identical            : {identical}/{n_prompts}");
    println!("  differ at char 0     : {at_zero}/{n_prompts}");
    if !divergences.is_empty() {
        divergences.sort_unstable();
        let med = divergences[divergences.len() / 2];
        println!(
            "  first divergence char: min {} / median {} / max {}",
            divergences[0], med, divergences[divergences.len() - 1]
        );
    }
    println!();
    println!("  serial [0] : {:?}", &s1[0][..s1[0].len().min(58)]);
    println!("  batched[0] : {:?}", &b1[0][..b1[0].len().min(58)]);
    println!();

    // ---- 4. VALIDITY ----------------------------------------------------
    println!("=== 4. VALIDITY of 50 concurrent outputs ===");
    let mut empty = 0usize;
    let mut degenerate = 0usize;
    for s in &b1 {
        let t = s.trim();
        if t.is_empty() { empty += 1; }
        if t.len() > 8 && t.chars().all(|c| Some(c) == t.chars().next()) {
            degenerate += 1;
        }
    }
    println!("  non-empty     : {}/{n_prompts}", n_prompts - empty);
    println!("  non-degenerate: {}/{n_prompts}", n_prompts - degenerate);
    println!();

    // ---- 5. THROUGHPUT ---------------------------------------------------
    println!("=== 5. THROUGHPUT (fresh batch, 50 concurrent) ===");
    let t0 = Instant::now();
    let _ = batched.batch_generate(&prompts, MAX_TOKENS, 0.0).unwrap_or_default();
    let batch_elapsed = t0.elapsed().as_secs_f64();
    let batch_rate = n_prompts as f64 / batch_elapsed;
    println!("  {n_prompts} requests in {batch_elapsed:.2}s");
    println!("  THROUGHPUT  : {batch_rate:.2} req/s");
    println!();

    println!("=== RESULT ===");
    println!("  serial baseline        : {serial_rate:.2} req/s");
    println!("  batched                : {batch_rate:.2} req/s");
    println!("  SPEEDUP                : {:.2}x", batch_rate / serial_rate);
    println!("  target >= 15 req/s     : {}", if batch_rate >= 15.0 { "PASS" } else { "MISS" });
    println!("  serial self-consistent : {serial_self_same}/{n_prompts}");
    println!("  batched self-consistent: {batch_self_same}/{n_prompts}");
    println!("  batched == serial      : {identical}/{n_prompts}");

    if empty != 0 || degenerate != 0 || batch_self_same != n_prompts {
        eprintln!("VALIDITY OR DETERMINISM FAILURE");
        std::process::exit(3);
    }
}
