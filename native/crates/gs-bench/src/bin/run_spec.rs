//! Single-model decode throughput, five prompts, greedy.
//!
//! This used to be the speculative-decoding A/B harness. Speculative decoding
//! was DELETED -- the commit that removed it carries the measurement that
//! justified it. What survives is the half that is still true: a reproducible
//! tokens/sec figure for the local model on this machine, which every other
//! throughput claim is measured against.

use gs_ffi::LlamaModel;
use std::process::exit;

const PROMPTS: &[&str] = &[
    "Name three primary colours.",
    "What is the capital of Japan? Answer in one word.",
    "Write a Python function that reverses a string.",
    "Why is the sky blue? One sentence.",
    "Summarise the water cycle in one sentence.",
];

fn main() {
    let model_path = std::env::var("GS_LOCAL_MODEL")
        .unwrap_or_else(|_| "/mnt/new_volume/models/llm/qwen2.5-0.5b-instruct-q4_k_m.gguf".into());
    if !std::path::Path::new(&model_path).exists() {
        eprintln!("BLOCKED: no model at {model_path}");
        exit(1);
    }
    let max_tokens: i32 = std::env::var("GS_MAX_TOKENS").ok().and_then(|v| v.parse().ok()).unwrap_or(48);

    let mut main = LlamaModel::load(&model_path, 1024, 4, -1)
        .unwrap_or_else(|e| { eprintln!("BLOCKED: main load failed with code {e}"); exit(1) });
    println!("model   : {model_path}");
    println!("backend : {}", main.backend_name());
    println!("sampler : greedy (temp 0.0), max_tokens={max_tokens}");
    println!();

    let mut total_tokens = 0i64;
    let mut total_ms = 0f64;

    for (i, p) in PROMPTS.iter().enumerate() {
        let prompt = format!("### User\n{p}\n\n### Assistant\n");
        let t0 = std::time::Instant::now();
        let out = main.generate(&prompt, max_tokens, 0.0)
            .unwrap_or_else(|e| { eprintln!("BLOCKED: generate failed with code {e}"); exit(1) });
        let ms = t0.elapsed().as_millis().max(1) as f64;
        let n = main.last_n_tokens().max(1) as i64;
        let tps = n as f64 / (ms / 1000.0);
        total_tokens += n;
        total_ms += ms;
        println!("prompt {i}: {}", &p[..p.len().min(44)]);
        println!("  {:7.2} tok/s  ({} tokens, {:.0} ms)", tps, n, ms);
        println!("  text: {}", out.trim().replace('\n', " ").chars().take(70).collect::<String>());
        println!();
    }

    println!("=== DECODE THROUGHPUT over {} prompts ===", PROMPTS.len());
    println!("  tokens generated : {total_tokens}");
    println!("  total_ms         : {total_ms:.0}");
    println!(
        "  AGGREGATE tok/s  : {:.2}",
        total_tokens as f64 / (total_ms / 1000.0).max(0.001)
    );
    println!("  backend          : {}", main.backend_name());
}
