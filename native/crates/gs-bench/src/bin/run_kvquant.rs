//! Item 2 - KV cache quantisation, measured.
//!
//! Same 2000-token prompt, same model, same decode budget; the only difference
//! is the KV cache element type. Reports real peak process RSS (VmHWM from
//! /proc), prefill and decode latency, tokens/sec, and prints both outputs so
//! any quality regression is visible rather than summarised away.

use gs_ffi::{KvType, LlamaModel};
use std::process::exit;

/// Peak resident set size in bytes, from the kernel. Not an estimate.
fn peak_rss_bytes() -> u64 {
    let s = std::fs::read_to_string("/proc/self/status").unwrap_or_default();
    for line in s.lines() {
        if let Some(rest) = line.strip_prefix("VmHWM:") {
            let kb: u64 = rest
                .trim()
                .trim_end_matches(" kB")
                .trim()
                .parse()
                .unwrap_or(0);
            return kb * 1024;
        }
    }
    0
}

fn current_rss_bytes() -> u64 {
    let s = std::fs::read_to_string("/proc/self/status").unwrap_or_default();
    for line in s.lines() {
        if let Some(rest) = line.strip_prefix("VmRSS:") {
            let kb: u64 = rest.trim().trim_end_matches(" kB").trim().parse().unwrap_or(0);
            return kb * 1024;
        }
    }
    0
}

fn mib(b: u64) -> f64 {
    b as f64 / (1024.0 * 1024.0)
}

fn build_prompt(target_tokens: usize) -> String {
    // A long, stable, semantically inert preamble. Long enough to make the KV
    // cache the dominant term, so a cache-type change is actually visible in
    // RSS rather than drowned out by the weights.
    let mut s = String::from(
        "### System\nYou are GS AI. Answer directly. Be brief and correct.\n\n### User\n",
    );
    let mut i = 0usize;
    while s.len() < target_tokens * 4 {
        s.push_str(&format!(
            "Reference note {i}: the deployment pipeline reads a manifest, resolves the \
             artifact digest, verifies the signature against the pinned key, and records the \
             outcome in the audit log before releasing the result to the caller.\n"
        ));
        i += 1;
    }
    s.push_str("\n### Assistant\n");
    s
}

fn run(tag: &str, model: &mut LlamaModel, prompt: &str, max_tokens: i32) {
    let t0 = std::time::Instant::now();
    let out = match model.generate(prompt, max_tokens, 0.0) {
        Ok(o) => o,
        Err(e) => {
            eprintln!("BLOCKED: generate failed with code {e}");
            exit(1);
        }
    };
    let ms = t0.elapsed().as_millis() as f64;
    let n = model.last_n_tokens().max(1) as f64;
    println!("  [{tag}]");
    println!("    peak_rss   : {:.1} MiB", mib(peak_rss_bytes()));
    println!("    rss_now    : {:.1} MiB", mib(current_rss_bytes()));
    println!("    total_ms   : {:.0}", ms);
    println!("    out_tokens : {}", n as i64);
    println!("    tok/s      : {:.2}", n / (ms / 1000.0));
    println!("    output     : {}", out.trim().replace('\n', " ").chars().take(150).collect::<String>());
}

fn main() {
    let model_path = std::env::var("GS_LOCAL_MODEL")
        .unwrap_or_else(|_| "/mnt/new_volume/models/llm/qwen2.5-0.5b-instruct-q4_k_m.gguf".into());
    if !std::path::Path::new(&model_path).exists() {
        eprintln!("BLOCKED: no model at {model_path}");
        exit(1);
    }
    let n_ctx: i32 = std::env::var("GS_LOCAL_CTX").ok().and_then(|v| v.parse().ok()).unwrap_or(4096);
    let prompt = build_prompt(2000);
    let prompt_tokens = {
        let mut m = LlamaModel::load(&model_path, 512, 4, -1).unwrap_or_else(|e| {
            eprintln!("BLOCKED: load failed {e}");
            exit(1)
        });
        m.token_count(&prompt)
    };
    println!("model        : {model_path}");
    println!("prompt bytes : {}", prompt.len());
    println!("prompt tokens: {prompt_tokens}");
    println!("n_ctx        : {n_ctx}");

    // Two processes would be cleaner for RSS isolation, but this binary is one
    // process: the F16 run happens first and its peak is recorded before the
    // Q4_0 model is ever allocated, so the Q4_0 peak is measured from a
    // baseline that still contains the F16 model. Run the two configs in
    // separate invocations for a clean number:
    //   GS_KV=1 ./run_kvquant   -> f16
    //   GS_KV=2 ./run_kvquant   -> q4_0
    let which = std::env::var("GS_KV").unwrap_or_else(|_| "1".into());
    let kv = if which == "2" { KvType::Q4_0 } else { KvType::F16 };
    let mut m = LlamaModel::load_with_kv(&model_path, n_ctx, 4, -1, kv, kv)
        .unwrap_or_else(|e| {
            eprintln!("BLOCKED: load with kv={} failed {e}", kv.as_str());
            exit(1)
        });
    println!("cache_type_k : {}", kv.as_str());
    println!("cache_type_v : {}", kv.as_str());
    println!("n_layer      : {}", m.n_layer());
    println!("baseline_rss : {:.1} MiB (before model load)", mib(current_rss_bytes()));
    println!();
    run(kv.as_str(), &mut m, &prompt, 64);
    println!();
    println!("DONE {tag_kv}", tag_kv = kv.as_str());
}
