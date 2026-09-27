//! Image creation through the agent loop, on the geometry path.
//!
//! Usage: run_image "<request>"
//!
//! Classifies the request, renders the artefact, writes it to disk and prints
//! the path. No model call and no weights are involved: a chart is geometry.

use gs_core::agent::IntentClassifier;
use gs_core::image;
use gs_common::Intent;
use std::process::exit;

fn main() {
    let request = match std::env::args().nth(1) {
        Some(r) => r,
        None => {
            eprintln!("usage: run_image \"<request>\"");
            exit(2);
        }
    };

    println!("request : {request}");
    let intent = IntentClassifier::classify(&request);
    println!("intent  : {intent:?}");
    if intent != Intent::ImageCreate {
        eprintln!("BLOCKED: the request did not classify as ImageCreate");
        exit(3);
    }

    let t0 = std::time::Instant::now();
    let made = match image::create(&request) {
        Ok(c) => c,
        Err(e) => {
            eprintln!("BLOCKED: {e}");
            exit(4);
        }
    };
    println!("kind    : {}", made.kind);
    println!("elapsed : {} ms", t0.elapsed().as_millis());
    println!("bytes   : {}", made.bytes);
    println!("path    : {}", made.path.display());

    match image::create::rasterize(&made.path) {
        Ok(p) => println!("raster  : {}", p.display()),
        Err(e) => eprintln!("raster  : unavailable ({e})"),
    }
    println!("VERDICT: artefact written");
}
