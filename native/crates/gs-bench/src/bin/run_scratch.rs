//! Item 6.2b demo: a 50,000-character tool result through the agent.
//!
//! Prints the exact context before and after, the exact replacement text, the
//! token delta, and then has the LOCAL MODEL read a line back out of the
//! scratch file that exists only in the offloaded body.

use gs_core::agent::IntentClassifier;
use gs_core::scratch::{offload, read_scratch_file, PREVIEW_CHARS};
use gs_ffi::LlamaModel;
use std::process::exit;

/// ~4 characters per token for English prose. Used only to state the size of
/// the saving, and labelled as an estimate wherever it appears.
fn approx_tokens(chars: usize) -> f64 {
    chars as f64 / 4.0
}

fn main() {
    let model_path = std::env::var("GS_LOCAL_MODEL")
        .unwrap_or_else(|_| "/mnt/new_volume/models/llm/qwen2.5-0.5b-instruct-q4_k_m.gguf".into());
    if !std::path::Path::new(&model_path).exists() {
        eprintln!("BLOCKED: no model at {model_path}");
        exit(1);
    }

    // A realistic log dump. The secret line is at the very end, so finding it
    // requires reading the file rather than the preview.
    let secret = "DEPLOY_TOKEN=sk-live-9f3a2b7c4d8e1f60";
    let mut body = String::new();
    let mut i = 0usize;
    while body.len() < 50_000 {
        body.push_str(&format!(
            "2026-09-28T00:{:02}:{:02}Z INFO  worker={} shard={} handled=ok latency_ms={}\n",
            i % 60, (i * 7) % 60, i % 32, (i * 13) % 16, 10 + (i % 90)
        ));
        i += 1;
    }
    body.push_str(secret);
    body.push('\n');

    let before_chars = body.chars().count();
    println!("=== INPUT ===");
    println!("tool result label : server_log");
    println!("exact size        : {before_chars} chars");
    println!("approx tokens     : {:.0} (at 4 chars/token)", approx_tokens(before_chars));
    println!();

    let o = offload("server_log", &body);
    let after_chars = o.context_text.chars().count();
    let after_tokens = approx_tokens(after_chars);

    println!("=== AFTER OFFLOAD ===");
    println!("offloaded         : {}", o.offloaded);
    println!("context chars     : {after_chars}  (was {before_chars})");
    println!("approx tokens     : {after_tokens:.0}  (was {:.0})", approx_tokens(before_chars));
    println!(
        "token saving      : {:.0} - {:.0} = {:.0} tokens ({:.1}%)",
        approx_tokens(before_chars),
        after_tokens,
        approx_tokens(before_chars) - after_tokens,
        (1.0 - after_chars as f64 / before_chars as f64) * 100.0
    );
    println!();
    println!("=== EXACT REPLACEMENT TEXT PUT IN CONTEXT ===");
    println!("---8<---");
    println!("{}", o.context_text);
    println!("---8<---");
    println!();

    let path = match &o.path {
        Some(p) => p.clone(),
        None => {
            eprintln!("BLOCKED: nothing was offloaded");
            exit(1);
        }
    };
    let on_disk = std::fs::read_to_string(&path).unwrap_or_default();
    println!("=== VERIFY THE BODY IS INTACT ON DISK ===");
    println!("path              : {}", path.display());
    println!("file bytes        : {}", on_disk.len());
    println!("byte-identical    : {}", on_disk == body);
    println!("secret in preview : {}  (must be false)", o.context_text.contains(secret));
    println!("secret on disk    : {}   (must be true)", on_disk.contains(secret));
    println!();

    // The model is given ONLY the replacement text, exactly as the agent would.
    // If it can answer from the preview alone the answer is wrong; the point is
    // that it must ask for the file.
    println!("=== MODEL, GIVEN ONLY THE OFFLOADED CONTEXT ===");
    let mut m = LlamaModel::load(&model_path, 4096, 4, -1)
        .unwrap_or_else(|e| { eprintln!("BLOCKED: load {e}"); exit(1) });
    let prompt = format!(
        "### System\nYou are GS AI. Answer directly. If a fact is not in the context, \
         say you must read the file to get it.\n\n### User\n\
         Here is a server log. What is the DEPLOY_TOKEN value at the end of it?\n\n{}\n\n### Assistant\n",
        o.context_text
    );
    let reply = m.generate(&prompt, 96, 0.2).unwrap_or_else(|e| { eprintln!("BLOCKED: generate {e}"); exit(1) });
    println!("preview chars in context : {PREVIEW_CHARS}");
    println!("model reply              : {}", reply.trim().replace('\n', " ").chars().take(160).collect::<String>());
    println!("model saw the secret     : {}  (must be false)", reply.contains("sk-live-9f3a2b7c4d8e1f60"));
    println!();

    // Now the read-back path, which is what the model is told to use.
    println!("=== READ-BACK PATH (read_scratch_file) ===");
    let (read_back, truncated) = match read_scratch_file(&path, 60_000) {
        Ok(v) => v,
        Err(e) => { eprintln!("BLOCKED: read_scratch_file: {e}"); exit(1) }
    };
    println!("truncated                : {truncated}");
    println!("chars read               : {}", read_back.chars().count());
    println!("secret recovered         : {}", read_back.contains(secret));
    let line = read_back
        .lines()
        .find(|l| l.contains("DEPLOY_TOKEN"))
        .unwrap_or("(not found)");
    println!("line located             : {line}");
    println!();

    let found = read_back.contains(secret) && on_disk == body;
    println!("VERDICT: {}", if found { "offload saved context and the body is fully recoverable" } else { "FAILED" });
    if !found { exit(2) }

    // Show that the intent classifier is unaffected by all of this.
    println!();
    println!("(agent classifier sanity: {:?})", IntentClassifier::classify("read the log"));
}
