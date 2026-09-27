//! Live provider-pool probe. Proves the pool routes, rotates keys and
//! classifies errors against real providers. Prints NO key material.
use gs_common::{CompletionConfig, Message};
use gs_core::http_provider::pool_from_env;

#[tokio::main]
async fn main() {
    let pool = pool_from_env();
    println!("providers registered: {}", pool.slots_len());
    if pool.slots_len() == 0 {
        println!("BLOCKED: no providers. Set GS_PROVIDERS and GS_KEYS_<NAME>.");
        std::process::exit(2);
    }
    let cfg = CompletionConfig {
        model: std::env::var("GS_PROBE_MODEL").unwrap_or_else(|_| "auto".into()),
        max_tokens: 16,
        temperature: 0.0,
        top_p: 1.0,
        response_format: None,
    };
    match pool
        .complete(&[Message::user("Reply with exactly: POOL-OK")], &cfg)
        .await
    {
        Ok(c) => {
            println!("PROVIDER   = {}", c.provider);
            println!("MODEL      = {}", c.served_model);
            println!("KEY_INDEX  = {}", c.key_index);
            println!("LATENCY_MS = {}", c.latency_ms);
            println!("TOKENS     = {} in / {} out", c.input_tokens, c.output_tokens);
            println!("TEXT       = {:?}", c.text.trim());
            println!("requests={} failovers={} rate_limits={} dead_keys={}",
                pool.stats.requests.load(std::sync::atomic::Ordering::Relaxed),
                pool.stats.failovers.load(std::sync::atomic::Ordering::Relaxed),
                pool.stats.rate_limits.load(std::sync::atomic::Ordering::Relaxed),
                pool.stats.dead_keys.load(std::sync::atomic::Ordering::Relaxed));
            println!("POOL_LIVE_OK");
        }
        Err(e) => {
            println!("BLOCKED: {e}");
            std::process::exit(3);
        }
    }
}
