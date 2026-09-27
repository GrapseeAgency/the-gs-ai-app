//! Generic benchmark driver. One scaffold lever per invocation.
//!
//! Usage:
//!   run_bench --benchmark mmlu_dev --scaffold baseline [options]
//!
//! Renamed from run_gpqa: the driver was never GPQA-specific, only the default
//! dataset was. Naming a binary after one dataset is how a driver ends up
//! quietly measuring the wrong one.
//!
//! Local model (no provider, no network):
//!   GS_BENCH_MODEL=local \
//!   GS_LOCAL_MODEL=/path/to/model.gguf \
//!   run_bench --benchmark mmlu_dev --scaffold baseline
//!
//! Every arm must use the same benchmark, the same sample set and the same
//! model. The sample-set hash is recorded in the scorecard so a delta between
//! two arms can be refused if they were not measured on the same questions.

use gs_bench::{delta_table, load_questions, hardest, Runner, Scaffold, Scorecard, SampleResult};
use gs_common::CompletionConfig;
use gs_core::http_provider::pool_from_env;
use gs_core::local_provider::LocalProvider;
use std::collections::BTreeMap;
use std::sync::Arc;

/// Known benchmarks and their dataset paths, relative to native/ or the repo
/// root. Registering the datasets here rather than passing a path means a typo
/// cannot silently point an arm at a different question set.
const BENCHMARKS: &[(&str, &str, &str)] = &[
    ("mmlu_dev", "data/benchmarks/mmlu_dev/dev100.jsonl", "MMLU dev split, 100 items sampled across all 57 subjects"),
    ("gpqa_diamond", "data/benchmarks/gpqa/diamond.jsonl", "GPQA Diamond, 198 items"),
];

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let mut benchmark = "mmlu_dev".to_string();
    let mut scaffold = Scaffold::Baseline;
    let mut limit: Option<usize> = None;
    let mut hardest_flag = false;
    let mut pace: u64 = 0;
    let mut maxtok: u32 = 512;
    let mut constrained = true;
    let mut outdir = "data/benchmarks/results".to_string();
    let mut local = false;

    let mut i = 0;
    while i < args.len() {
        match args[i].as_str() {
            "--benchmark" => { benchmark = args.get(i + 1).cloned().unwrap_or(benchmark); i += 2; }
            "--scaffold" => {
                scaffold = match args.get(i + 1).map(|s| s.as_str()) {
                    Some("baseline") => Scaffold::Baseline,
                    Some("aci") => Scaffold::Aci,
                    Some("verification") => Scaffold::Verification,
                    Some("context") => Scaffold::Context,
                    Some("router") => Scaffold::Router,
                    other => { eprintln!("unknown scaffold {other:?}"); std::process::exit(2); }
                };
                i += 2;
            }
            "--pace-ms" => { pace = args.get(i + 1).and_then(|v| v.parse().ok()).unwrap_or(0); i += 2; }
            "--unconstrained" => { constrained = false; i += 1; }
            "--max-tokens" => { maxtok = args.get(i + 1).and_then(|v| v.parse().ok()).unwrap_or(512); i += 2; }
            "--hardest" => { hardest_flag = true; i += 1; }
            "--limit" => { limit = args.get(i + 1).and_then(|v| v.parse().ok()); i += 2; }
            "--out" => { outdir = args.get(i + 1).cloned().unwrap_or(outdir); i += 2; }
            "--local" => { local = true; i += 1; }
            _ => { i += 1; }
        }
    }

    let entry = match BENCHMARKS.iter().find(|(n, _, _)| *n == benchmark) {
        Some(e) => e,
        None => {
            eprintln!("BLOCKED: unknown benchmark {benchmark:?}. Known:");
            for (n, p, d) in BENCHMARKS {
                eprintln!("  {:16} {:44} {}", n, p, d);
            }
            std::process::exit(2);
        }
    };
    // Resolve relative to the crate or the repo root so the driver works from
    // either directory.
    let prefixed = format!("native/{}", entry.1);
    let path_owned: &str = if std::path::Path::new(entry.1).exists() {
        entry.1
    } else if std::path::Path::new(&prefixed).exists() {
        &prefixed
    } else {
        entry.1
    };
    let path = path_owned;

    let mut qs = match load_questions(path) {
        Ok(v) => v,
        Err(e) => { eprintln!("BLOCKED: {e}"); std::process::exit(2); }
    };
    let full_set = qs.len();
    if let Some(n) = limit {
        if hardest_flag {
            qs = hardest(&qs, n);
        } else {
            qs.truncate(n);
        }
    }

    // Model id and backend come from the environment, never from memory: the
    // scorecard has to name what actually served the answers.
    let model_id = std::env::var("GS_BENCH_MODEL").unwrap_or_else(|_| "local".into());
    local = local || model_id.starts_with("local");

    eprintln!(
        "benchmark={benchmark} scaffold={} model={model_id} samples={}/{} pace={}ms max_tokens={} constrained={} local={}",
        scaffold.as_str(), qs.len(), full_set, pace, maxtok, constrained, local
    );

    let rt = tokio::runtime::Runtime::new().expect("tokio runtime");
    rt.block_on(async move {
        let pool = if local {
            let path = std::env::var("GS_LOCAL_MODEL").unwrap_or_default();
            if path.is_empty() {
                eprintln!("BLOCKED: --local needs GS_LOCAL_MODEL=/path/to/model.gguf");
                std::process::exit(3);
            }
            let n_ctx: i32 = std::env::var("GS_LOCAL_CTX").ok().and_then(|v| v.parse().ok()).unwrap_or(2048);
            let threads: i32 = std::env::var("GS_LOCAL_THREADS").ok().and_then(|v| v.parse().ok()).unwrap_or(4);
            // Default is EVERY layer on the GPU. -1 is llama.cpp's "all layers"
            // sentinel; it must not default to 0, because 0 means CPU-only and
            // a benchmark that believes it is on the GPU while running on the
            // host produces numbers that are not comparable to anything.
            let layers: i32 = std::env::var("GS_LOCAL_GPU_LAYERS")
                .ok().and_then(|v| v.parse().ok()).unwrap_or(-1);
            let p = match LocalProvider::load(&path, n_ctx, threads, layers, true) {
                Ok(p) => p,
                Err(e) => { eprintln!("BLOCKED: {e}"); std::process::exit(3); }
            };
            eprintln!(
                "local model: {} (ctx={} threads={} gpu_layers={} backend={})",
                p.model_path(), p.context(), p.threads(), p.gpu_layers(), p.backend_name()
            );
            Arc::new(gs_core::router::ProviderPool::new(
                vec![(Arc::new(p) as Arc<dyn gs_core::router::Provider>, vec!["local".to_string()])],
                Arc::new(gs_core::router::SystemClock),
            ))
        } else {
            let p = Arc::new(pool_from_env());
            if p.slots_len() == 0 {
                eprintln!("BLOCKED: provider pool is empty. Set GS_PROVIDERS + GS_KEYS_<NAME>, or pass --local.");
                std::process::exit(3);
            }
            p
        };

        let mut cfg = CompletionConfig {
            model: model_id.clone(),
            max_tokens: maxtok,
            temperature: 0.0,
            top_p: 1.0,
            response_format: None,
        };
        // Constrained decoding: the model cannot emit anything but a letter.
        // Not applied locally — a 0.5B model cannot be steered by a response
        // schema through the llama.cpp text path, and pretending otherwise
        // would compare arms that are not comparable.
        if constrained && !local {
            cfg.response_format = Some(gs_bench::letter_schema());
        }

        let runner = Runner { pool, config: cfg, scaffold, pace_ms: pace, park_wait_ms: 15_000 };
        let (results, card) = runner.run(&qs).await;

        std::fs::create_dir_all(&outdir).ok();
        let stem = format!("{outdir}/{benchmark}-{}", scaffold.as_str());
        let per: Vec<SampleResult> = results.clone();
        let pj = format!("{stem}.per-sample.json");
        std::fs::write(&pj, serde_json::to_string_pretty(&per).unwrap_or_default()).ok();

        let mut card2: Scorecard = card;
        card2.per_sample_path = pj.clone();
        let cj = format!("{stem}.scorecard.json");
        std::fs::write(&cj, serde_json::to_string_pretty(&card2).unwrap_or_default()).ok();

        println!("{{\"benchmark\":\"{benchmark}\",\"scaffold\":\"{}\",\"model\":\"{model_id}\",\
                   \"local\":{local},\"score\":{:.4},\"ci95\":[{:.4},{:.4}],\
                   \"parse_rate\":{:.4},\"accuracy\":{:.4},\
                   \"accuracy_floor_adjusted\":{:.4},\"ci_floor\":[{:.4},{:.4}],\
                   \"n_samples\":{},\"n_parsed\":{},\"n_correct\":{},\"n_failed\":{},\
                   \"n_errored\":{},\"n_skipped\":{},\"unparseable\":{},\
                   \"provider_distribution\":{:?},\"model_distribution\":{:?},\
                   \"p50_ms\":{},\"p95_ms\":{},\"sample_set_sha\":\"{}\",\
                   \"scorecard\":\"{cj}\",\"per_sample\":\"{pj}\"}}",
            card2.scaffold, card2.score, card2.ci_low, card2.ci_high,
            card2.parse_rate, card2.accuracy,
            card2.accuracy_floor_adjusted, card2.ci_floor_low, card2.ci_floor_high,
            card2.n_samples, card2.n_parsed, card2.n_correct, card2.n_failed,
            card2.n_errored, card2.n_skipped, card2.unparseable,
            card2.provider_distribution, card2.model_distribution,
            card2.latency_p50_ms, card2.latency_p95_ms,
            card2.sample_set_sha);
        let _ = BTreeMap::<String, u32>::new();
        let _ = delta_table;
    });
}
