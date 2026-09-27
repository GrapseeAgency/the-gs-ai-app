//! Item 6.2f demo: right-sized routing, with the chosen model actually run.
//!
//! Six messages, one per tier and one that must fall back. Prints the decision
//! and its reason, then generates with the model that was chosen and reports the
//! real bytes and latency, so the table is what ran rather than what was planned.

use gs_core::routing::{Registry, Tier};
use gs_ffi::LlamaModel;
use std::process::exit;

/// (intent, user message). Each maps to a different tier by policy.
const CASES: &[(&str, &str)] = &[
    ("classification", "Classify this as urgent or routine: 'the payment page is down'"),
    ("extraction", "Extract the invoice number from: INVOICE INV-9920 DUE 2026-04-02"),
    ("summarization", "Summarise in one sentence: the pipeline resolves a digest, verifies a signature, and records an audit entry."),
    ("chat", "In one sentence, what is a hash function?"),
    ("code", "Write a Python function that returns the median of a list of numbers."),
    ("reasoning", "A tank fills at 3 L/min and drains at 1 L/min. Starting from 20 L, how long until it holds 50 L?"),
];

fn main() {
    // Two models, deliberately different sizes, so "right-sized" has something
    // to choose between. The small one is the 135M draft already on disk.
    let small = std::env::var("GS_SMALL_MODEL")
        .unwrap_or_else(|_| "/mnt/new_volume/models/llm/smollm2-135m-instruct-q4_k_m.gguf".into());
    let mid = std::env::var("GS_MID_MODEL")
        .unwrap_or_else(|_| "/mnt/new_volume/models/llm/qwen2.5-0.5b-instruct-q4_k_m.gguf".into());

    let (reg, missing) = Registry::discover(&[
        ("smollm2-135m", &small, Tier::Small, "classification, extraction, short factual"),
        ("qwen2.5-0.5b", &mid, Tier::Mid, "chat, summarisation, light reasoning"),
        // No Large model is registered on this machine, which is deliberate:
        // it exercises the provider-fallback path for real.
    ]);

    println!("=== REGISTRY ===");
    for m in &reg.models {
        println!("  {:<14} {:>12} bytes  tier={:<6} {}", m.name, m.bytes, m.tier.as_str(), m.strength);
    }
    println!("  total registered: {} bytes", reg.total_bytes());
    if !missing.is_empty() {
        println!("  not on disk (skipped, not faked): {}", missing.join(", "));
    }
    println!("  no Large model registered -> Large intents must fall back to a provider");
    println!();

    println!("=== ROUTING DECISIONS AND WHAT ACTUALLY RAN ===");
    println!("{:<14} {:<11} {:>12} {:>8}  {}", "intent", "model", "bytes", "ms", "reason");
    println!("{}", "-".repeat(120));

    let mut loaded: Vec<(String, LlamaModel)> = Vec::new();
    for (intent, msg) in CASES {
        let d = reg.route(intent);
        if d.fell_back_to_provider {
            println!("{:<14} {:<11} {:>12} {:>8}  {}", intent, d.model, "-", "-", d.reason);
            continue;
        }
        // Load the chosen model if it is not already resident.
        let path = reg.models.iter().find(|m| m.name == d.model).map(|m| m.path.clone());
        let path = match path {
            Some(p) => p,
            None => {
                eprintln!("BLOCKED: registry returned {} but it is not in the registry", d.model);
                exit(1);
            }
        };
        if !loaded.iter().any(|(n, _)| *n == d.model) {
            match LlamaModel::load(&path.to_string_lossy(), 2048, 4, -1) {
                Ok(m) => loaded.push((d.model.clone(), m)),
                Err(e) => {
                    eprintln!("BLOCKED: loading {} failed with code {e}", d.model);
                    exit(1);
                }
            }
        }
        let idx = loaded.iter().position(|(n, _)| *n == d.model).unwrap();
        let prompt = format!("### User\n{msg}\n\n### Assistant\n");
        let t0 = std::time::Instant::now();
        let out = match loaded[idx].1.generate(&prompt, 48, 0.0) {
            Ok(o) => o,
            Err(e) => {
                eprintln!("BLOCKED: generate with {} failed with code {e}", d.model);
                exit(1);
            }
        };
        let ms = t0.elapsed().as_millis();
        println!("{:<14} {:<11} {:>12} {:>8}  {}", intent, d.model, d.bytes, ms, d.reason);
        println!("{:<14} reply: {}", "", out.trim().replace('\n', " ").chars().take(96).collect::<String>());
    }
    println!();

    // The interesting measurement: did the tier choice change the answer quality
    // on a task the small model should not be trusted with?
    println!("=== DOES THE TIER CHOICE MATTER? same prompt, both models ===");
    let probe = "A tank fills at 3 L/min and drains at 1 L/min. Starting from 20 L, how long until it holds 50 L?";
    let prompt = format!("### User\n{probe}\n\n### Assistant\n");
    let small_name = reg.models.iter().find(|m| m.tier == Tier::Small).map(|m| m.name.clone());
    let mid_name = reg.models.iter().find(|m| m.tier == Tier::Mid).map(|m| m.name.clone());
    for (label, want) in [("small", small_name), ("mid", mid_name)] {
        let name = match want {
            Some(n) => n,
            None => continue,
        };
        let path = reg.models.iter().find(|m| m.name == name).unwrap().path.clone();
        let mut m = match LlamaModel::load(&path.to_string_lossy(), 2048, 4, -1) {
            Ok(m) => m,
            Err(_) => continue,
        };
        let t0 = std::time::Instant::now();
        let out = m.generate(&prompt, 48, 0.0).unwrap_or_default();
        let ms = t0.elapsed().as_millis();
        // 50-20 = 30 L at a net 2 L/min = 15 minutes.
        let says_15 = out.contains("15");
        println!("  {:<6} {:<14} {:>4} ms  says_15_minutes={:<5}  {}",
                 label, name, ms, says_15, out.trim().replace('\n', " ").chars().take(70).collect::<String>());
    }
    println!("  ground truth: 50-20 = 30 L, net 2 L/min -> 15 minutes");
    println!();
    println!("dossier claim: cost reduction with no quality loss. See the table above:");
    println!("routing is tiered by policy, and the provider fallback is reported rather than");
    println!("silently downgrading a reasoning task to a 135M model.");
}
