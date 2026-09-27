//! Vision turn, end to end, on the local model.
//!
//! Usage: run_vision "<user prompt>" [model.gguf]
//!
//! Classifies the prompt, runs OCR + CLIP over any image path it mentions,
//! feeds the evidence to the local llama model, and prints the answer. This is
//! the live check that the vision path is wired into the agent loop rather than
//! merely existing as a library.

use gs_core::agent::{find_image_path, IntentClassifier};
use gs_core::vision;
use gs_ffi::LlamaModel;
use std::path::PathBuf;
use std::process::exit;

fn main() {
    let mut args = std::env::args().skip(1);
    let prompt = match args.next() {
        Some(p) => p,
        None => {
            eprintln!("usage: run_vision \"<prompt>\" [model.gguf]");
            exit(2);
        }
    };
    let model_path = args
        .next()
        .map(PathBuf::from)
        .unwrap_or_else(|| {
            PathBuf::from("/mnt/new_volume/models/llm/qwen2.5-0.5b-instruct-q4_k_m.gguf")
        });

    println!("prompt : {prompt}");
    let intent = IntentClassifier::classify(&prompt);
    println!("intent : {intent:?}");
    let image = find_image_path(&prompt);
    println!("image  : {}", image.clone().unwrap_or_else(|| "(none found)".into()));

    if intent != gs_common::Intent::Vision {
        eprintln!("BLOCKED: the prompt did not classify as Vision");
        exit(3);
    }
    let path = match image {
        Some(p) => PathBuf::from(p),
        None => {
            eprintln!("BLOCKED: no image path in the prompt");
            exit(3);
        }
    };

    // --- vision evidence -------------------------------------------------
    let t0 = std::time::Instant::now();
    let evidence = vision::gather(&path, &prompt);
    println!("vision : {} ms", t0.elapsed().as_millis());
    println!("--- evidence ---");
    println!("[OCR TEXT]\n{}", if evidence.ocr_text.is_empty() { "(no text found)" } else { &evidence.ocr_text });
    match &evidence.clip {
        Some(c) => println!("[CLIP TOP MATCH]\n{} score={:.4}", c.query, c.score),
        None => println!("[CLIP TOP MATCH]\n(unavailable)"),
    }
    for n in &evidence.notes {
        println!("[NOTE] {n}");
    }

    if evidence.is_empty() {
        eprintln!("BLOCKED: neither OCR nor CLIP produced anything for {}", path.display());
        exit(4);
    }

    // --- local model over the evidence -----------------------------------
    let mut model = match LlamaModel::load(&model_path.to_string_lossy(), 2048, 4, 0) {
        Ok(m) => m,
        Err(e) => {
            eprintln!("BLOCKED: local model failed to load (code {e}) from {}", model_path.display());
            exit(5);
        }
    };
    println!("model  : {} ({})", model_path.display(), model.backend_name());

    let context = evidence.context(&prompt);
    let full = format!(
        "You are GS AI, an assistant that reads images.\n\n{context}\n\n\
         Answer the user's question using the OCR text above. Quote identifiers exactly."
    );
    let t1 = std::time::Instant::now();
    let reply = match model.generate(&full, 160, 0.2) {
        Ok(r) => r,
        Err(e) => {
            eprintln!("BLOCKED: local generation failed (code {e})");
            exit(5);
        }
    };
    println!("gen    : {} ms", t1.elapsed().as_millis());

    let answer = vision::compose_answer(&evidence, &reply);
    println!("--- model reply ---");
    println!("{}", reply.trim());
    println!("--- agent answer ---");
    println!("{}", answer.trim());
    println!("--- end ---");

    // The acceptance criterion: the invoice id must survive into the answer.
    let ok = evidence.ocr_text.contains("INV-4471") && answer.contains("INV-4471");
    println!("VERDICT: {}", if ok { "INV-4471 present in the answer" } else { "INV-4471 MISSING" });
    if !ok {
        exit(6);
    }
}
