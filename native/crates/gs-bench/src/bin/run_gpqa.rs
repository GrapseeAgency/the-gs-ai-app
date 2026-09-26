//! GPQA Diamond driver. One scaffold lever per invocation.
use gs_bench::{delta_table, load_questions, Runner, Scaffold, Scorecard, SampleResult};
use gs_common::CompletionConfig;
use gs_core::http_provider::pool_from_env;
use std::collections::BTreeMap;
use std::sync::Arc;

fn main() {
    let mut args: Vec<String> = std::env::args().skip(1).collect();
    let mut scaffold = Scaffold::Baseline;
    let mut limit: Option<usize> = None;
    let mut pace: u64 = 2000;
    let mut maxtok: u32 = 1024;
    let mut outdir = "data/benchmarks/results".to_string();
    let mut i = 0;
    while i < args.len() {
        match args[i].as_str() {
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
            "--pace-ms" => { pace = args.get(i+1).and_then(|v| v.parse().ok()).unwrap_or(2000); i += 2; }
            "--max-tokens" => { maxtok = args.get(i+1).and_then(|v| v.parse().ok()).unwrap_or(1024); i += 2; }
            "--limit" => { limit = args.get(i + 1).and_then(|v| v.parse().ok()); i += 2; }
            "--out" => { outdir = args.get(i + 1).cloned().unwrap_or(outdir); i += 2; }
            _ => { i += 1; }
        }
    }
    args.clear();

    let rt = tokio::runtime::Runtime::new().expect("tokio runtime");
    rt.block_on(async move {
        // Resolve the dataset relative to the crate, not the cwd, so the runner
        // works from native/ or from the repo root.
        let path = ["data/benchmarks/gpqa/diamond.jsonl",
                    "native/data/benchmarks/gpqa/diamond.jsonl"]
            .into_iter()
            .find(|p| std::path::Path::new(p).exists())
            .unwrap_or("data/benchmarks/gpqa/diamond.jsonl");
        let mut qs = match load_questions(path) {
            Ok(v) => v,
            Err(e) => { eprintln!("BLOCKED: {e}"); std::process::exit(2); }
        };
        if let Some(n) = limit { qs.truncate(n); }
        eprintln!("staged {} questions, scaffold={}, pace={}ms, max_tokens={}", qs.len(), scaffold.as_str(), pace, maxtok);

        let pool = Arc::new(pool_from_env());
        if pool.slots_len() == 0 {
            eprintln!("BLOCKED: provider pool is empty. Set GS_PROVIDERS + GS_KEYS_<NAME>.");
            std::process::exit(3);
        }
        // Model ids must come from the provider, not from memory.
        let model = std::env::var("GS_BENCH_MODEL").unwrap_or_else(|_| "allam-2-7b".into());
        let cfg = CompletionConfig { model: model.clone(), max_tokens: maxtok,
                                    temperature: 0.0, top_p: 1.0 };
        let runner = Runner { pool, config: cfg, scaffold, pace_ms: pace, park_wait_ms: 15_000 };
        let (results, card) = runner.run(&qs).await;

        std::fs::create_dir_all(&outdir).ok();
        let stem = format!("{}/gpqa-{}", outdir, scaffold.as_str());
        let per: Vec<SampleResult> = results.clone();
        let pj = format!("{stem}.per-sample.json");
        std::fs::write(&pj, serde_json::to_string_pretty(&per).unwrap_or_default()).ok();

        let mut card2: Scorecard = card;
        card2.per_sample_path = pj.clone();
        let cj = format!("{stem}.scorecard.json");
        std::fs::write(&cj, serde_json::to_string_pretty(&card2).unwrap_or_default()).ok();

        println!("{{\"scaffold\":\"{}\",\"score\":{:.4},\"ci_low\":{:.4},\"ci_high\":{:.4},\
                   \"n_samples\":{},\"n_correct\":{},\"n_errored\":{},\"unparseable\":{},\
                   \"p50_ms\":{},\"p95_ms\":{},\"per_sample\":\"{}\"}}",
            card2.scaffold, card2.score, card2.ci_low, card2.ci_high,
            card2.n_samples, card2.n_correct, card2.n_errored, card2.unparseable,
            card2.latency_p50_ms, card2.latency_p95_ms, card2.per_sample_path);
        let _ = BTreeMap::<String, u32>::new();
        let _ = delta_table;
    });
}
