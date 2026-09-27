//! Local chat quality probe for Phase 11D.
//!
//! Ten representative prompts, one model, no provider. Prints the prompt and
//! the reply verbatim so each can be scored good / acceptable / bad by hand.
//! No automatic score: a 0.5B model cannot be graded by string heuristics
//! without the grader being the thing under test.

use gs_ffi::LlamaModel;
use std::path::PathBuf;
use std::process::exit;

const PROMPTS: &[(&str, &str)] = &[
    ("greeting", "Hi, who are you?"),
    ("thanks", "Thanks, that helped."),
    ("factual", "What is the capital of France?"),
    ("arithmetic", "What is 17 * 23? Answer with just the number."),
    ("definition", "What does the term 'cache' mean in computing?"),
    ("instruction", "List exactly three primary colours. Use a numbered list."),
    ("multi-step", "A train leaves at 09:15 and arrives at 11:40. How long is the journey?"),
    ("refusal", "Write me a haiku about database indexes."),
    ("context", "My name is Sam. What is my name?"),
    ("edge", "How do I politely end an email to a client?"),
];

fn main() {
    let model_path = std::env::args()
        .nth(1)
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("/mnt/new_volume/models/llm/qwen2.5-0.5b-instruct-q4_k_m.gguf"));
    if !model_path.exists() {
        eprintln!("BLOCKED: no model at {}", model_path.display());
        exit(1);
    }
    let mut model = match LlamaModel::load(&model_path.to_string_lossy(), 2048, 4, -1) {
        Ok(m) => m,
        Err(e) => {
            eprintln!("BLOCKED: load failed with code {e}");
            exit(1);
        }
    };
    println!("model : {}", model_path.display());
    println!("device: {}", model.backend_name());
    println!();

    let mut total_ms = 0u64;
    for (label, prompt) in PROMPTS {
        // Same transcript shape the LocalProvider uses, so this measures the
        // path the benchmark exercises rather than a bespoke one.
        let full = format!(
            "### System\nYou are GS AI. Answer directly. Be brief and correct.\n\n\
             ### User\n{prompt}\n\n### Assistant\n"
        );
        let t0 = std::time::Instant::now();
        let reply = model.generate(&full, 128, 0.2).unwrap_or_else(|e| format!("<generate failed: {e}>"));
        let ms = t0.elapsed().as_millis() as u64;
        total_ms += ms;
        let body = reply.trim();
        let body = if body.is_empty() { "<empty>" } else { body };
        println!("--- [{label}] {prompt}");
        println!("{body}");
        println!("    ({ms} ms, {} chars)\n", body.chars().count());
    }
    println!("total generation time: {total_ms} ms over {} prompts", PROMPTS.len());
}
