//! The vision turn: OCR and CLIP over an attached image, wired into the agent
//! loop.
//!
//! Order matters and is deliberate: OCR first (it is exact), then CLIP over the
//! same image with the user's question as the query (it is approximate). The
//! two are complementary — OCR gives identifiers verbatim, CLIP gives the gist
//! when the image has no text at all.
//!
//! Nothing here is optional. If OCR returns text, that text is carried into the
//! answer verbatim rather than being summarised away, because an invoice
//! number that survives paraphrase is a number that has been corrupted.

use gs_ffi::{extract_text, ClipModel};
use std::path::Path;

/// A candidate caption drawn from the question, scored by CLIP.
#[derive(Debug)]
pub struct ClipMatch {
    pub query: String,
    pub score: f32,
}

/// Default model locations, overridable by env so the repo carries no weights.
fn clip_paths() -> (std::path::PathBuf, std::path::PathBuf) {
    let base = std::env::var("GS_CLIP_DIR")
        .unwrap_or_else(|_| "/mnt/new_volume/models/clip".to_string());
    let vision = std::env::var("GS_CLIP_VISION")
        .unwrap_or_else(|_| format!("{base}/vision_model.onnx"));
    let text = std::env::var("GS_CLIP_TEXT")
        .unwrap_or_else(|_| format!("{base}/text_model_quantized.onnx"));
    (vision.into(), text.into())
}

/// Resize (shortest edge) + centre-crop to the model's input side, then to RGB.
fn to_square_rgb(img: &image::DynamicImage, side: u32) -> image::RgbImage {
    let rgb = img.to_rgb8();
    let (w, h) = (rgb.width(), rgb.height());
    if w == side && h == side {
        return rgb;
    }
    let scale = (side as f32 / w.min(h) as f32).max(1e-6);
    let nw = ((w as f32 * scale).round() as u32).max(side);
    let nh = ((h as f32 * scale).round() as u32).max(side);
    let resized = image::imageops::resize(&rgb, nw, nh, image::imageops::FilterType::Triangle);
    image::imageops::crop_imm(&resized, (nw - side) / 2, (nh - side) / 2, side, side).to_image()
}

/// The evidence gathered from one image, before any model sees it.
#[derive(Debug, Default)]
pub struct VisionEvidence {
    pub ocr_text: String,
    pub clip: Option<ClipMatch>,
    pub notes: Vec<String>,
}

impl VisionEvidence {
    /// The context block handed to the model, in the documented shape.
    pub fn context(&self, question: &str) -> String {
        let clip_line = match &self.clip {
            Some(c) => format!("{} score={:.4}", c.query, c.score),
            None => "unavailable".to_string(),
        };
        format!(
            "[OCR TEXT]\n{}\n\n[CLIP TOP MATCH]\n{}\n\n[USER QUESTION]\n{}",
            if self.ocr_text.is_empty() { "(no text found)" } else { &self.ocr_text },
            clip_line,
            question
        )
    }

    pub fn is_empty(&self) -> bool {
        self.ocr_text.is_empty() && self.clip.is_none()
    }
}

/// Run OCR then CLIP over one image.
pub fn gather(image_path: &Path, question: &str) -> VisionEvidence {
    let mut ev = VisionEvidence::default();

    match extract_text(image_path) {
        Some(t) => {
            let t = t.trim().to_string();
            if t.is_empty() {
                ev.notes.push("OCR ran but found no text".into());
            } else {
                ev.ocr_text = t;
            }
        }
        None => ev.notes.push("OCR unavailable or the image could not be read".into()),
    }

    let img = match image::open(image_path) {
        Ok(i) => i,
        Err(e) => {
            ev.notes.push(format!("image decode failed: {e}"));
            return ev;
        }
    };

    let (vision, text) = clip_paths();
    match ClipModel::load(&vision, &text) {
        Ok(model) => {
            let side = gs_ffi::IMAGE_SIDE as u32;
            let square = to_square_rgb(&img, side);
            match model.embed_image(square.as_raw()) {
                Ok(img_emb) => {
                    // The user's own question is the query, plus a couple of
                    // neutral fallbacks so there is always a "top match".
                    let mut queries: Vec<String> = Vec::new();
                    let q = question.trim();
                    if !q.is_empty() {
                        queries.push(q.to_string());
                        queries.push(format!("a photo of {q}"));
                    }
                    queries.push("a photo of a document".to_string());
                    queries.push("a photo of an object".to_string());

                    let mut best: Option<ClipMatch> = None;
                    for cand in queries {
                        if let Ok(emb) = model.embed_text(&cand) {
                            let score = model.cosine(&img_emb, &emb);
                            if best.as_ref().map_or(true, |b| score > b.score) {
                                best = Some(ClipMatch { query: cand, score });
                            }
                        }
                    }
                    if best.is_none() {
                        ev.notes.push("CLIP text tower returned no usable embedding".into());
                    }
                    ev.clip = best;
                }
                Err(e) => ev.notes.push(format!("CLIP image embedding failed: {e}")),
            }
        }
        Err(e) => ev.notes.push(format!("CLIP unavailable: {e}")),
    }

    ev
}

/// The answer for a vision turn.
///
/// The model's reading is returned, but the OCR text is always appended so the
/// exact identifiers survive regardless of how the model phrased it.
pub fn compose_answer(evidence: &VisionEvidence, model_reply: &str) -> String {
    if evidence.ocr_text.is_empty() {
        return model_reply.to_string();
    }
    format!("{model_reply}\n\n[OCR TEXT READ FROM THE IMAGE]\n{}", evidence.ocr_text)
}
