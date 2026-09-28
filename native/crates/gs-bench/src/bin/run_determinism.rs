//! Regression test for the sampler-chain defect. Not a benchmark.
//!
//! THE DEFECT
//!
//! `gs_llama_generate_opts` used to append a temperature stage to a chain that
//! was built once in `gs_llama_create` and never reset:
//!
//!     llama_sampler_chain_add(ctx->sampler, llama_sampler_init_temp(temperature));
//!
//! Call N ran with N stacked temp stages behind a `dist(1234)` stage whose RNG
//! had advanced with request history. Three consequences, all shipping bugs:
//!
//!   1. completions were not reproducible — the same question gave different
//!      answers depending on how many requests preceded it
//!   2. one sampler leaked per call
//!   3. `temperature=0` did not mean greedy, which is what every caller passing
//!      0.0 assumed and what a losslessness check requires
//!
//! WHY THIS SHAPE
//!
//! A two-call comparison is not enough. The original investigation needed three
//! probes to localise it, and the decisive measurement was:
//!
//!     two INDEPENDENT contexts, same prompts : 10/10
//!     one REUSED context, run1 vs run2       :  0/10
//!
//! So the test asserts the case that actually failed — sequential reuse of ONE
//! context, 50 times — and separately asserts that a fresh context agrees, so a
//! future regression can be told apart from a different one. It also counts
//! against temperature > 0, because a non-zero temperature legitimately varies:
//! if the engine reports variance where greedy is requested, the check is broken
//! rather than the engine.
//!
//! 50 sequential requests, identical input, temperature 0.0, must be
//! byte-identical.

use gs_ffi::LlamaModel;
use std::time::Instant;

const N: usize = 50;

fn prompt() -> String {
    // A fixed prompt. Deliberately one that produces real tokens rather than an
    // early end-of-generation, so the whole decode loop is exercised 50 times.
    "### User\nExplain in two sentences why the sky appears blue.\n\n### Assistant\n".to_string()
}

fn run(m: &mut LlamaModel, temp: f32) -> Vec<String> {
    (0..N).map(|_| m.generate(&prompt(), 48, temp).unwrap_or_default()).collect()
}

fn main() {
    let path = std::env::var("GS_LOCAL_MODEL").unwrap_or_else(|_| {
        "/mnt/new_volume/models/llm/qwen2.5-0.5b-instruct-q4_k_m.gguf".into()
    });
    if !std::path::Path::new(&path).exists() {
        eprintln!("BLOCKED: no model at {path}");
        std::process::exit(2);
    }

    let mut m = LlamaModel::load(&path, 2048, 4, -1)
        .unwrap_or_else(|e| { eprintln!("BLOCKED: load failed with code {e}"); std::process::exit(1) });
    println!("model   : {path}");
    println!("backend : {}", m.backend_name());
    println!("requests: {N} sequential, temperature 0.0, one reused context");
    println!();

    let t0 = Instant::now();
    let out = run(&mut m, 0.0);
    let elapsed = t0.elapsed().as_secs_f64();

    // --- 1. The regression itself ----------------------------------------
    let same = out.iter().filter(|s| **s == out[0]).count();
    println!("=== 1. SEQUENTIAL REUSE, temperature = 0.0 ===");
    println!("  identical to request 1 : {same}/{N}");
    println!("  elapsed                : {elapsed:.2}s");
    let empty = out.iter().filter(|s| s.trim().is_empty()).count();
    println!("  empty                  : {empty}/{N}");
    if let Some(i) = (0..N).find(|i| out[*i] != out[0]) {
        println!("  first divergence at request {}", i + 1);
        println!("    #1: {:?}", &out[0][..out[0].len().min(70)]);
        println!("    #{}: {:?}", i + 1, &out[i][..out[i].len().min(70)]);
    }
    println!();

    // --- 2. A fresh context must agree -----------------------------------
    // This is the control that made the original diagnosis possible. If reuse
    // diverges but a fresh context does not, the fault is per-context state.
    let mut fresh = LlamaModel::load(&path, 2048, 4, -1)
        .unwrap_or_else(|e| { eprintln!("BLOCKED: second load failed {e}"); std::process::exit(1) });
    let out2 = run(&mut fresh, 0.0);
    let fresh_same = out2.iter().filter(|s| **s == out2[0]).count();
    println!("=== 2. FRESH CONTEXT, temperature = 0.0 (control) ===");
    println!("  identical to request 1 : {fresh_same}/{N}");
    println!("  agrees with the reused context : {}", out2[0] == out[0]);
    println!();

    // --- 3. Sanity: the temperature stage is actually live ---------------
    //
    // The obvious control — "temperature 0.9 should vary between calls" — is
    // WRONG, and this run is why. The non-greedy chain ends in
    // llama_sampler_init_dist(1234): a FIXED seed, and the chain is rebuilt per
    // call, so 0.9 is reproducible across calls by design. That is the point of
    // the fix, not a bug. Asserting variance there would have failed a correct
    // engine and taught the next reader the wrong thing.
    //
    // The real vacuity risk is the opposite one: a temperature stage that is
    // silently ignored, which would make temperature=0 pass for the wrong
    // reason. So the control is that 0.9 and 0.0 must DISAGREE. If they agreed,
    // greedy is not actually being selected and this whole file is a no-op.
    let mut hot = LlamaModel::load(&path, 2048, 4, -1)
        .unwrap_or_else(|e| { eprintln!("BLOCKED: third load failed {e}"); std::process::exit(1) });
    let hot_out = run(&mut hot, 0.9);
    let hot_stable = hot_out.iter().filter(|s| **s == hot_out[0]).count();
    let differs = hot_out[0] != out[0];
    println!("=== 3. CONTROL: the temperature stage must be live ===");
    println!("  temperature 0.9 self-consistent : {hot_stable}/{N}");
    println!("    (expected {N}/{N}: the dist stage is a fixed seed, so 0.9 is");
    println!("     reproducible across calls by design)");
    println!("  temperature 0.9 differs from 0.0 : {differs}");
    println!("    (must be true, or greedy is not being selected and this");
    println!("     whole test is vacuous)");
    if differs {
        println!("    0.0: {:?}", &out[0][..out[0].len().min(70)]);
        println!("    0.9: {:?}", &hot_out[0][..hot_out[0].len().min(70)]);
    }
    println!();

    println!("=== RESULT ===");
    let mut fail = Vec::new();
    if same != N { fail.push(format!("reused context: {same}/{N} identical")); }
    if fresh_same != N { fail.push(format!("fresh context: {fresh_same}/{N} identical")); }
    if out2[0] != out[0] { fail.push("fresh and reused contexts disagree".into()); }
    if empty != 0 { fail.push(format!("{empty} empty outputs")); }
    if !differs { fail.push("temperature 0.9 and 0.0 agree: the temperature stage is ignored, so the temperature=0 result is vacuous".into()); }
    if hot_stable != N { fail.push(format!("temperature 0.9 was not self-consistent: {hot_stable}/{N}; the fixed seed is not being applied per call").into()); }

    if fail.is_empty() {
        println!("  PASS: {N}/{N} byte-identical at temperature 0.0, and the");
        println!("        temperature 0.9 control confirms the check is not vacuous.");
        return;
    }
    for f in &fail {
        println!("  FAIL: {f}");
    }
    std::process::exit(3);
}
