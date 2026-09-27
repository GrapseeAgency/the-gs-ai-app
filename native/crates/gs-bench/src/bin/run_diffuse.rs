//! Diffusion through the agent loop.
//!
//! Usage: run_diffuse "<request>"
//!
//! Classifies the request, strips the instruction wrapper, and asks
//! stable-diffusion.cpp for an image. GPU-only: a run that lands on the host
//! CPU is reported as a failure rather than a success.

use gs_core::agent::{diffusion_prompt, IntentClassifier};
use gs_ffi::sd_bridge::{diffuse, DiffuseRequest};
use std::process::exit;

fn main() {
    let request = match std::env::args().nth(1) {
        Some(r) => r,
        None => {
            eprintln!("usage: run_diffuse \"<request>\"");
            exit(2);
        }
    };

    let intent = IntentClassifier::classify(&request);
    println!("request : {request}");
    println!("intent  : {intent:?}");
    if intent != gs_common::Intent::ImageGenerate {
        eprintln!("BLOCKED: the request did not classify as ImageGenerate");
        exit(3);
    }

    let prompt = diffusion_prompt(&request);
    println!("prompt  : {prompt}");

    let req = DiffuseRequest {
        prompt,
        steps: 20,
        width: 512,
        height: 512,
        ..Default::default()
    };
    println!("backend : {} (require_gpu={})", req.backend, req.require_gpu);

    let t0 = std::time::Instant::now();
    let d = match diffuse(&req) {
        Ok(d) => d,
        Err(e) => {
            eprintln!("BLOCKED: {e}");
            exit(4);
        }
    };
    let wall_ms = t0.elapsed().as_millis() as u64;

    // Pull the line that proves where the weights lived.
    let mem = d
        .log
        .lines()
        .filter(|l| l.contains("total params memory size"))
        .last()
        .unwrap_or("(not reported)")
        .trim();

    println!("path    : {}", d.path.display());
    println!("bytes   : {}", d.bytes);
    println!("wall    : {wall_ms} ms");
    println!("weights : {mem}");
    let ok = d.path.exists() && d.bytes > 0;
    println!("VERDICT: {}", if ok { "image written" } else { "NO IMAGE WRITTEN" });
    if !ok {
        exit(5);
    }
}
