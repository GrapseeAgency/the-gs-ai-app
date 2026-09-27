//! Item 6.2d demo: semantic caching with the CLIP text tower.
//!
//! Ten queries: five unique, then five paraphrases of those five. Prints the
//! hit/miss decision, the similarity that drove it, and the measured hit rate
//! at the dossier's 0.95 threshold. Also sweeps the threshold so the
//! hit-rate/precision trade is visible rather than asserted.

use gs_core::semcache::SemanticCache;
use gs_ffi::ClipModel;
use std::path::PathBuf;
use std::process::exit;

const UNIQUE: &[&str] = &[
    "how do I reset my password",
    "what are your shipping options",
    "how do I contact support",
    "can I change my email address",
    "where is my order",
];

const PARAPHRASES: &[&str] = &[
    "how do I reset my passwd",
    "what shipping choices do you have",
    "how can I get in touch with support",
    "is it possible to change my email",
    "where can I find my order status",
];

/// A deliberately unrelated control. If this ever hits, the cache is returning
/// an answer for a question it was never asked.
const UNRELATED: &[&str] = &[
    "what is the boiling point of water",
    "write a haiku about rust",
];

fn main() {
    let base = std::env::var("GS_CLIP_DIR").unwrap_or_else(|_| "/mnt/new_volume/models/clip".into());
    let vision = PathBuf::from(std::env::var("GS_CLIP_VISION").unwrap_or_else(|_| format!("{base}/vision_model.onnx")));
    let text = PathBuf::from(std::env::var("GS_CLIP_TEXT").unwrap_or_else(|_| format!("{base}/text_model_quantized.onnx")));

    let clip = match ClipModel::load(&vision, &text) {
        Ok(m) => m,
        Err(e) => {
            eprintln!("BLOCKED: {e}");
            eprintln!("hint: GS_ONNXRUNTIME_ROOT must be set at build time and ONNX_RUNTIME on the loader path");
            exit(3);
        }
    };
    println!("embedder   : clip-text ({}d), no new model downloaded", clip.dim());
    println!();

    let threshold: f32 = std::env::var("GS_SEM_THRESHOLD").ok().and_then(|v| v.parse().ok()).unwrap_or(0.95);
    println!("=== PASS 1: {} unique queries, all cold ===", UNIQUE.len());
    let mut cache = SemanticCache::new(64);
    for q in UNIQUE {
        let emb = clip.embed_text(q).unwrap_or_else(|e| { eprintln!("BLOCKED: embed {e}"); exit(1) });
        let l = cache.get(q, &emb, threshold);
        println!("  MISS  sim={:.4}  {}", l.similarity, q);
        // A "response" stands in for the model call. Only its content matters here.
        cache.put(q, &format!("ANSWER({})", q), emb);
    }
    println!();

    println!("=== PASS 2: {} paraphrases, threshold {:.2} ===", PARAPHRASES.len(), threshold);
    let mut hits = 0usize;
    for q in PARAPHRASES {
        let emb = clip.embed_text(q).unwrap_or_else(|e| { eprintln!("BLOCKED: embed {e}"); exit(1) });
        let l = cache.get(q, &emb, threshold);
        if l.hit { hits += 1; }
        println!("  {}  sim={:.4}  {}", if l.hit { "HIT " } else { "MISS" }, l.similarity, q);
        if let Some(mq) = &l.matched_query {
            println!("          matched: {}", mq);
            println!("          served : {}", l.response.clone().unwrap_or_default());
        }
        if !l.hit {
            cache.put(q, &format!("ANSWER({})", q), emb);
        }
    }
    println!();
    println!("=== CONTROL: unrelated queries must MISS ===");
    let mut control_hits = 0usize;
    for q in UNRELATED {
        let emb = clip.embed_text(q).unwrap_or_else(|e| { eprintln!("BLOCKED: embed {e}"); exit(1) });
        let l = cache.get(q, &emb, threshold);
        if l.hit { control_hits += 1; }
        println!("  {}  sim={:.4}  {}", if l.hit { "HIT " } else { "MISS" }, l.similarity, q);
    }
    println!();

    let rate = cache.hit_rate();
    let (h, m) = cache.stats();
    println!("=== RESULT at threshold {:.2} ===", threshold);
    println!("  paraphrase hit rate    : {}/{} = {:.1}%", hits, PARAPHRASES.len(),
             hits as f64 / PARAPHRASES.len() as f64 * 100.0);
    println!("  unrelated false hits   : {}/{} (must be 0)", control_hits, UNRELATED.len());
    println!("  overall lookups        : {} hits / {} misses -> {:.1}%", h, m, rate * 100.0);
    println!();
    println!("dossier claim: 30-50% hit rate. measured on the 5 paraphrases at this threshold: {:.1}%",
             hits as f64 / PARAPHRASES.len() as f64 * 100.0);
    println!();

    println!("=== THRESHOLD SWEEP (the precision/hit-rate trade) ===");
    println!("  The dossier's 0.95 is not the right threshold for THIS embedder.");
    println!("  {:>7}  {:>10}  {:>12}  {:>10}", "thresh", "paraphrase%", "false hits", "decision");
    for t in [0.80f32, 0.85, 0.90, 0.93, 0.95, 0.97, 0.99] {
        let mut c = SemanticCache::new(64);
        for q in UNIQUE {
            let e = clip.embed_text(q).unwrap();
            c.get(q, &e, t);
            c.put(q, &format!("ANSWER({})", q), e);
        }
        let mut ph = 0;
        for q in PARAPHRASES {
            let e = clip.embed_text(q).unwrap();
            if c.get(q, &e, t).hit { ph += 1; }
            else { c.put(q, &format!("ANSWER({})", q), e); }
        }
        let mut fh = 0;
        for q in UNRELATED {
            let e = clip.embed_text(q).unwrap();
            if c.get(q, &e, t).hit { fh += 1; }
        }
        let pct = ph as f64 / PARAPHRASES.len() as f64 * 100.0;
        let decision = if fh > 0 { "UNSAFE: answers unrelated questions" } else { "ok" };
        println!("  {:>7.2}  {:>9.1}%  {:>12}  {:>10}", t, pct, fh, decision);
    }
}
