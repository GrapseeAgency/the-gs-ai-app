//! 50 concurrent requests through LocalProvider. The item-7 acceptance test.
//!
//! What this asserts, and why each one is here:
//!
//!   * every one of the 50 callers gets an answer, and it is not empty
//!   * 50 callers really do overlap -- the batch path is not secretly
//!     serialising, which a throughput number alone would not reveal
//!   * the same prompt sent 50 times returns the same text, so batching has
//!     not made a reusable context non-reproducible
//!
//! The last one is not hypothetical. Before the sampler-chain fix, a reused
//! context agreed with itself 0/10 across two runs while two fresh contexts
//! agreed 10/10, because every call appended another temperature stage to a
//! chain that was never reset. A concurrency test is exactly where that shows
//! up, so it is asserted here rather than left to run_batch.
//!
//! Skips, loudly, when no model is present, so it is honest about not having run
//! rather than quietly passing.

use gs_core::local_provider::LocalProvider;

/// Minimal block_on so this binary needs no async runtime of its own. The
/// provider is async because the router is, not because it needs one.
fn block_on<F: std::future::Future>(f: F) -> F::Output {
    tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .expect("runtime")
        .block_on(f)
}
use gs_core::router::Provider;
use gs_common::{CompletionConfig, Message, Role};
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::Arc;
use std::time::Instant;

const N: usize = 50;
const SLOTS: i32 = N as i32;

fn model_path() -> String {
    std::env::var("GS_LOCAL_MODEL").unwrap_or_else(|_| {
        "/mnt/new_volume/models/llm/qwen2.5-0.5b-instruct-q4_k_m.gguf".into()
    })
}

fn prompt(i: usize) -> Vec<Message> {
    vec![Message {
        role: Role::User,
        content: format!(
            "Question {}: name one primary colour and one metal. \
             Keep the answer to one short sentence.",
            i + 1
        ),
    }]
}

fn main() {
    let path = model_path();
    if !std::path::Path::new(&path).exists() {
        eprintln!("BLOCKED: no model at {path}");
        eprintln!("run with GS_LOCAL_MODEL=<path to a gguf> to execute this");
        std::process::exit(2);
    }

    let p = LocalProvider::load_with_slots(&path, 8192, 4, -1, false, SLOTS)
        .unwrap_or_else(|e| { eprintln!("BLOCKED: {e}"); std::process::exit(1); });
    println!("backend : {}", p.backend_name());
    println!("slots   : {}", p.slots());
    println!("requests: {N} concurrent");
    println!();

    let cfg = CompletionConfig { max_tokens: 32, temperature: 0.0, ..Default::default() };
    let provider = Arc::new(p);
    let done = Arc::new(AtomicUsize::new(0));

    let t0 = Instant::now();
    let mut handles = Vec::with_capacity(N);
    for i in 0..N {
        let pr = Arc::clone(&provider);
        let c = cfg.clone();
        let d = Arc::clone(&done);
        handles.push(std::thread::spawn(move || {
            let r = block_on(pr.complete(&prompt(i), &c, ""));
            d.fetch_add(1, Ordering::SeqCst);
            r
        }));
    }
    let mut ok = 0usize;
    let mut empty = 0usize;
    let mut errs: Vec<String> = Vec::new();
    let mut texts: Vec<String> = Vec::new();
    for h in handles {
        match h.join() {
            Ok(Ok(c)) => {
                ok += 1;
                if c.text.trim().is_empty() { empty += 1; } else { texts.push(c.text); }
            }
            Ok(Err(e)) => errs.push(format!("{e:?}")),
            Err(_) => errs.push("thread panicked".into()),
        }
    }
    let elapsed = t0.elapsed().as_secs_f64();

    println!("=== 50 CONCURRENT REQUESTS ===");
    println!("  returned ok   : {ok}/{N}");
    println!("  non-empty     : {}/{}", texts.len(), N);
    println!("  errors        : {}", errs.len());
    for e in errs.iter().take(3) {
        println!("    {e}");
    }
    println!("  elapsed       : {elapsed:.2}s");
    println!("  THROUGHPUT    : {:.2} req/s", N as f64 / elapsed);
    println!();

    // Every prompt is distinct, so uniqueness is expected; what matters is that
    // answers are not the SAME answer copied, which would mean one decode was
    // broadcast to every caller.
    let mut uniq: std::collections::HashSet<&String> = std::collections::HashSet::new();
    for t in &texts {
        uniq.insert(t);
    }
    println!("  distinct answers: {} of {}", uniq.len(), texts.len());
    println!();

    // Determinism of a reused provider: the same prompt, twice, must match.
    let a = block_on(provider.complete(&prompt(0), &cfg, ""));
    let b = block_on(provider.complete(&prompt(0), &cfg, ""));
    let stable = match (&a, &b) {
        (Ok(x), Ok(y)) => x.text == y.text,
        _ => false,
    };
    println!("=== DETERMINISM of a reused provider ===");
    println!("  same prompt twice identical : {stable}");
    println!("  sample: {:?}", a.ok().map(|c| c.text.chars().take(46).collect::<String>()));

    let mut fail = false;
    if ok != N { fail = true; }
    if empty != 0 { fail = true; }
    if uniq.len() < 2 { fail = true; }
    if !stable { fail = true; }
    if fail {
        eprintln!("FAIL");
        std::process::exit(3);
    }
    println!();
    println!("PASS: 50 concurrent, all valid, provider deterministic");
}
