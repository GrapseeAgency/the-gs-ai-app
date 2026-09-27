//! CLIP retrieval over a single image.
//!
//! Usage: run_clip <image> [vision.onnx] [text.onnx]
//!
//! Embeds the image, embeds a fixed set of queries, prints the cosine
//! similarity of each, and reports the best match. This is the live check that
//! the whole CLIP path — preprocessing, ONNX inference, BPE tokenisation and
//! normalisation — produces a usable ranking rather than noise.

use gs_ffi::{ClipError, ClipModel, IMAGE_SIDE};
use std::path::PathBuf;
use std::process::exit;

const QUERIES: &[&str] = &[
    "a white ceramic coffee mug on a marble counter",
    "a coffee mug",
    "a photo of a laptop and notebook on a desk",
    "a lake and mountains seen from a forest",
    "a red sports car racing on a track",
];

/// Resize so the shortest edge is `side`, then centre-crop to `side`x`side`.
/// This is the preprocessing MobileCLIP2-S0 was trained with.
fn resize_center_crop(img: &image::RgbImage, side: u32) -> image::RgbImage {
    let (w, h) = (img.width(), img.height());
    if w == side && h == side {
        return img.clone();
    }
    let scale = (side as f32 / w.min(h) as f32).max(1e-6);
    let nw = ((w as f32 * scale).round() as u32).max(side);
    let nh = ((h as f32 * scale).round() as u32).max(side);
    let resized = image::imageops::resize(img, nw, nh, image::imageops::FilterType::Triangle);
    let x = (nw - side) / 2;
    let y = (nh - side) / 2;
    image::imageops::crop_imm(&resized, x, y, side, side).to_image()
}

fn main() {
    let mut args = std::env::args().skip(1);
    let path = match args.next() {
        Some(p) => PathBuf::from(p),
        None => {
            eprintln!("usage: run_clip <image> [vision.onnx] [text.onnx]");
            exit(2);
        }
    };
    let vision = args
        .next()
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("/mnt/new_volume/models/clip/vision_model.onnx"));
    let text = args
        .next()
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("/mnt/new_volume/models/clip/text_model_quantized.onnx"));

    let img = match image::open(&path) {
        Ok(i) => i.to_rgb8(),
        Err(e) => {
            eprintln!("cannot decode {}: {e}", path.display());
            exit(1);
        }
    };
    println!("image        : {} ({}x{})", path.display(), img.width(), img.height());
    println!("vision model : {}", vision.display());
    println!("text model   : {}", text.display());

    let model = match ClipModel::load(&vision, &text) {
        Ok(m) => m,
        Err(ClipError::Unavailable(m)) => {
            eprintln!("BLOCKED: {m}");
            eprintln!("hint: set GS_ONNXRUNTIME_ROOT=/mnt/new_volume/onnxruntime/onnxruntime-linux-x64-1.20.0 and rebuild");
            exit(3);
        }
        Err(e) => {
            eprintln!("BLOCKED: {e}");
            exit(3);
        }
    };
    println!("embedding dim: {}", model.dim());

    let square = resize_center_crop(&img, IMAGE_SIDE as u32);
    let t0 = std::time::Instant::now();
    let img_emb = match model.embed_image(square.as_raw()) {
        Ok(v) => v,
        Err(e) => {
            eprintln!("image embedding failed: {e}");
            exit(1);
        }
    };
    let img_ms = t0.elapsed().as_millis();
    let img_norm: f32 = img_emb.iter().map(|v| v * v).sum::<f32>().sqrt();
    println!("image embed  : {img_ms} ms");
    println!(
        "image vector : L2={img_norm:.4} first8=[{}]",
        img_emb[..8.min(img_emb.len())]
            .iter()
            .map(|v| format!("{v:+.4}"))
            .collect::<Vec<_>>()
            .join(" ")
    );

    let mut embs: Vec<(&str, Vec<f32>)> = Vec::new();
    for q in QUERIES {
        let t = std::time::Instant::now();
        let emb = match model.embed_text(q) {
            Ok(v) => v,
            Err(e) => {
                eprintln!("text embedding failed for {q:?}: {e}");
                exit(1);
            }
        };
        let ms = t.elapsed().as_millis();
        let score = model.cosine(&img_emb, &emb);
        let norm: f32 = emb.iter().map(|v| v * v).sum::<f32>().sqrt();
        println!("{q:<44}  {score:.4}   (L2={norm:.3}, {ms} ms)");
        embs.push((q, emb));
    }

    // Diagnostic: if the text tower were healthy, distinct queries would sit
    // well below cosine 1.0 against each other. Values clustered near 1 mean
    // the tower is returning a near-constant vector.
    println!("\ntext-text cosine (lower is healthier):");
    for i in 0..embs.len() {
        for j in (i + 1)..embs.len() {
            println!("  {:.4}  {} | {}", model.cosine(&embs[i].1, &embs[j].1), embs[i].0, embs[j].0);
        }
    }

    let mut best = (f32::MIN, "");
    for (q, e) in &embs {
        let s = model.cosine(&img_emb, e);
        if s > best.0 {
            best = (s, *q);
        }
    }
    println!("\nTOP MATCH: {} score={:.4}", best.1, best.0);
}
