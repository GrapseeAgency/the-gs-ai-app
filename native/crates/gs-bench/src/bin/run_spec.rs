//! Item 2.5 - speculative decoding, measured, with a losslessness check.
//!
//! Same five prompts, same main model, same token budget, greedy sampler.
//! Reports tokens/sec with and without a draft model, the acceptance rate, and
//! whether the two outputs are byte-identical -- which is the property that
//! makes speculative decoding worth having at all.

use gs_ffi::LlamaModel;
use std::process::exit;

const PROMPTS: &[&str] = &[
    "Name three primary colours.",
    "What is the capital of Japan? Answer in one word.",
    "Write a Python function that reverses a string.",
    "Why is the sky blue? One sentence.",
    "Summarise the water cycle in one sentence.",
];

fn tok_s(model: &mut LlamaModel, prompt: &str, max_tokens: i32) -> (f64, String, i32) {
    let t0 = std::time::Instant::now();
    let out = model.generate(prompt, max_tokens, 0.0).unwrap_or_default();
    let ms = t0.elapsed().as_millis().max(1) as f64;
    let n = model.last_n_tokens().max(1) as f64;
    (n / (ms / 1000.0), out, model.last_n_tokens())
}

fn main() {
    let main_path = std::env::var("GS_LOCAL_MODEL")
        .unwrap_or_else(|_| "/mnt/new_volume/models/llm/qwen2.5-0.5b-instruct-q4_k_m.gguf".into());
    let draft_path = std::env::var("GS_DRAFT_MODEL").unwrap_or_else(|_| {
        "/mnt/new_volume/models/llm/smollm2-135m-instruct-q4_k_m.gguf".into()
    });
    for (k, v) in [("main", &main_path), ("draft", &draft_path)] {
        if !std::path::Path::new(v).exists() {
            eprintln!("BLOCKED: no {k} model at {v}");
            exit(1);
        }
    }
    let n_draft: i32 = std::env::var("GS_N_DRAFT").ok().and_then(|v| v.parse().ok()).unwrap_or(4);
    let max_tokens: i32 = std::env::var("GS_MAX_TOKENS").ok().and_then(|v| v.parse().ok()).unwrap_or(48);

    let mb = std::fs::metadata(&main_path).map(|m| m.len()).unwrap_or(0);
    let db = std::fs::metadata(&draft_path).map(|m| m.len()).unwrap_or(0);
    println!("main model  : {main_path}");
    println!("draft model : {draft_path}");
    println!("main bytes  : {mb}");
    println!("draft bytes : {db}  ({:.2}x smaller)", mb as f64 / db.max(1) as f64);
    println!("n_draft     : {n_draft}");
    println!("max_tokens  : {max_tokens}   sampler: greedy (temp 0.0)");
    println!();

    let mut main = LlamaModel::load(&main_path, 1024, 4, -1).unwrap_or_else(|e| { eprintln!("BLOCKED: main load {e}"); exit(1) });
    let mut draft = LlamaModel::load(&draft_path, 1024, 4, -1).unwrap_or_else(|e| { eprintln!("BLOCKED: draft load {e}"); exit(1) });
    println!("main backend : {}", main.backend_name());
    println!("draft backend: {}", draft.backend_name());
    println!();

    let mut sum_base = 0.0;
    let mut sum_spec = 0.0;
    let mut sum_drafted = 0i64;
    let mut sum_accepted = 0i64;
    let mut identical = 0usize;

    for (i, p) in PROMPTS.iter().enumerate() {
        let prompt = format!("### User\n{p}\n\n### Assistant\n");
        let (t_base, out_base, n_base) = tok_s(&mut main, &prompt, max_tokens);
        let t0 = std::time::Instant::now();
        let (out_spec, st) = match main.generate_speculative(&mut draft, &prompt, max_tokens, 0.0, n_draft) {
            Ok(v) => v,
            Err(e) => { eprintln!("BLOCKED: speculative failed with code {e}"); exit(1) }
        };
        let ms = t0.elapsed().as_millis().max(1) as f64;
        let t_spec = st.generated.max(1) as f64 / (ms / 1000.0);
        let same = out_base == out_spec;
        if same { identical += 1; }

        sum_base += t_base; sum_spec += t_spec;
        sum_drafted += st.drafted as i64; sum_accepted += st.accepted as i64;
        println!("prompt {i}: {}", &p[..p.len().min(44)]);
        println!("  baseline    : {:7.2} tok/s  ({} tokens)", t_base, n_base);
        println!("  speculative : {:7.2} tok/s  ({} tokens, drafted {}, accepted {}, rate {:.3}, resyncs {})",
                 t_spec, st.generated, st.drafted, st.accepted, st.accept_rate, st.resyncs);
        println!("  speedup     : {:7.3}x     identical_output={}", t_spec / t_base, same);
        println!("  text        : {}", out_spec.trim().replace('\n'," ").chars().take(70).collect::<String>());
        println!();
    }

    let n = PROMPTS.len() as f64;
    println!("=== TOTALS over {} prompts ===", PROMPTS.len());
    println!("  mean baseline tok/s    : {:.2}", sum_base / n);
    println!("  mean speculative tok/s : {:.2}", sum_spec / n);
    println!("  SPEEDUP RATIO          : {:.3}x", (sum_spec / n) / (sum_base / n));
    println!("  drafted / accepted     : {sum_drafted} / {sum_accepted}  overall accept_rate {:.4}",
             if sum_drafted > 0 { sum_accepted as f64 / sum_drafted as f64 } else { 0.0 });
    println!("  identical outputs      : {identical}/{}", PROMPTS.len());
    println!();
    println!("dossier claim: 2-3x. measured: {:.3}x", (sum_spec / n) / (sum_base / n));
}
