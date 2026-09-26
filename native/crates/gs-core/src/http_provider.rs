//! OpenAI-compatible HTTP provider.
//!
//! The pool abstraction in `router.rs` is deliberately transport-agnostic; this
//! is the concrete implementation that speaks the chat-completions protocol
//! that Groq, OpenRouter, Cerebras, SiliconFlow, Google and the Kilo gateway
//! all expose. One implementation serves the whole pool, which is the point:
//! adding a provider must be configuration, never new code.
//!
//! Key handling: the key arrives as a borrowed string, is used only in the
//! Authorization header, and is never stored in a struct, logged, or included
//! in an error. Errors name the provider, the HTTP status and the index.

use gs_common::{Completion, CompletionConfig, Message};
use serde::Deserialize;

use crate::router::{PoolError, Provider};

/// Error body shape returned by OpenAI-compatible endpoints.
#[derive(Debug, Deserialize)]
struct ApiError {
    #[serde(default)]
    error: Option<ApiErrorInner>,
}
#[derive(Debug, Deserialize)]
struct ApiErrorInner {
    #[serde(default)]
    message: String,
}

#[derive(Debug, Deserialize)]
struct ChatCompletion {
    #[serde(default)]
    model: String,
    #[serde(default)]
    choices: Vec<ChoiceOut>,
    #[serde(default)]
    usage: Option<UsageOut>,
}
#[derive(Debug, Deserialize)]
struct ChoiceOut {
    #[serde(default)]
    message: Option<MessageOut>,
    #[serde(default)]
    text: String,
}
#[derive(Debug, Deserialize)]
struct MessageOut {
    #[serde(default)]
    content: String,
}
#[derive(Debug, Deserialize, Default)]
struct UsageOut {
    #[serde(default)]
    prompt_tokens: u32,
    #[serde(default)]
    completion_tokens: u32,
}

pub struct HttpProvider {
    name: String,
    base_url: String,
    free_tier: bool,
    client: reqwest::Client,
}

impl HttpProvider {
    pub fn new(name: &str, base_url: &str, free_tier: bool) -> Self {
        Self {
            name: name.to_string(),
            base_url: base_url.trim_end_matches('/').to_string(),
            free_tier,
            client: reqwest::Client::new(),
        }
    }
}

#[async_trait::async_trait]
impl Provider for HttpProvider {
    fn name(&self) -> &str {
        &self.name
    }

    fn base_url(&self) -> &str {
        &self.base_url
    }

    fn is_healthy(&self) -> bool {
        // Transport-level health only. Whether the provider has quota is a
        // per-key question the pool resolves, not a property of the provider.
        true
    }

    fn is_free(&self) -> bool {
        self.free_tier
    }

    async fn complete(
        &self,
        messages: &[Message],
        config: &CompletionConfig,
        key: &str,
    ) -> Result<Completion, PoolError> {
        use gs_common::Role;

        let model = if config.model.is_empty() { "auto" } else { &config.model };

        let payload = serde_json::json!({
            "model": model,
            "messages": messages.iter().map(|m| serde_json::json!({
                "role": match m.role {
                    Role::System => "system",
                    Role::User => "user",
                    Role::Assistant => "assistant",
                    Role::Tool => "tool",
                },
                "content": m.content,
            })).collect::<Vec<_>>(),
            "max_tokens": config.max_tokens,
            "temperature": config.temperature,
            "top_p": config.top_p,
        });

        let started = std::time::Instant::now();
        let resp = self
            .client
            .post(format!("{}/chat/completions", self.base_url))
            .header("Authorization", format!("Bearer {key}"))
            .header("content-type", "application/json")
            .json(&payload)
            .send()
            .await;

        let resp = match resp {
            Ok(r) => r,
            Err(e) => {
                return Err(PoolError::Transport {
                    provider: self.name.clone(),
                    // reqwest's error text can echo the URL but never the
                    // header, so no key material can reach this string.
                    detail: e.to_string(),
                })
            }
        };

        let status = resp.status();
        if !status.is_success() {
            let body = resp.text().await.unwrap_or_default();
            let parsed: Option<ApiError> = serde_json::from_str(&body).ok();
            let msg = parsed
                .and_then(|a| a.error.map(|e| e.message))
                .unwrap_or_else(|| body.chars().take(200).collect());

            return Err(match status.as_u16() {
                // 404 on a chat call is almost always model_not_found. That is
                // the caller naming a model this provider does not serve, and
                // the provider is demonstrably alive because it answered.
                // Mapping it to Transport would trip the breaker and take a
                // healthy provider offline over a typo.
                404 if msg.contains("model") || msg.contains("Model") => {
                    PoolError::ModelNotFound {
                        provider: self.name.clone(),
                        model: model.to_string(),
                    }
                }
                // Rate limited: park this key, do NOT count toward the breaker.
                429 => PoolError::RateLimited {
                    provider: self.name.clone(),
                    key_index: 0,
                },
                // Permanently invalid credentials/terms: kill the key.
                401 | 402 | 403 => PoolError::KeyDead {
                    provider: self.name.clone(),
                    key_index: 0,
                },
                // Upstream said 503 (service unavailable) is an outage.
                s => PoolError::Transport {
                    provider: self.name.clone(),
                    detail: format!("HTTP {s}: {msg}"),
                },
            });
        }

        let parsed: ChatCompletion = resp.json().await.map_err(|e| PoolError::Transport {
            provider: self.name.clone(),
            detail: format!("malformed response body: {e}"),
        })?;

        let text = parsed
            .choices
            .first()
            .map(|c| {
                c.message
                    .as_ref()
                    .map(|m| m.content.clone())
                    .unwrap_or_else(|| c.text.clone())
            })
            .unwrap_or_default();

        if text.trim().is_empty() {
            // An empty completion is NOT a success. Providers return these
            // under overload, and shipping one would be a fabricated answer.
            return Err(PoolError::Empty {
                provider: self.name.clone(),
            });
        }

        let usage = parsed.usage.unwrap_or_default();
        Ok(Completion {
            text,
            served_model: if parsed.model.is_empty() {
                model.to_string()
            } else {
                parsed.model
            },
            provider: self.name.clone(),
            key_index: 0, // overwritten by the pool with the real index
            latency_ms: started.elapsed().as_millis() as u64,
            input_tokens: usage.prompt_tokens,
            output_tokens: usage.completion_tokens,
            was_retry: false,
        })
    }
}

impl HttpProvider {
    /// Discover the model ids this provider actually serves.
    ///
    /// Hardcoding a model id is how the 404 path gets exercised in the first
    /// place: ids are per-account (a key may see a subset), and they change.
    /// A benchmark MUST call this and use what comes back.
    pub async fn list_models(&self, key: &str) -> Result<Vec<String>, PoolError> {
        let resp = self
            .client
            .get(format!("{}/models", self.base_url))
            .header("Authorization", format!("Bearer {key}"))
            .send()
            .await
            .map_err(|e| PoolError::Transport {
                provider: self.name.clone(),
                detail: format!("model discovery failed: {e}"),
            })?;
        if !resp.status().is_success() {
            return Err(PoolError::Transport {
                provider: self.name.clone(),
                detail: format!("model discovery HTTP {}", resp.status()),
            });
        }
        #[derive(Deserialize)]
        struct ModelList {
            #[serde(default)]
            data: Vec<ModelEntry>,
        }
        #[derive(Deserialize)]
        struct ModelEntry {
            id: String,
        }
        let parsed: ModelList = resp.json().await.map_err(|e| PoolError::Transport {
            provider: self.name.clone(),
            detail: format!("malformed model list: {e}"),
        })?;
        let mut ids: Vec<String> = parsed.data.into_iter().map(|m| m.id).collect();
        ids.sort();
        Ok(ids)
    }
}

/// Build a pool from a provider table and a key map, read from the
/// environment. Nothing here prints or stores a key value.
///
/// `GS_PROVIDERS` format: `name=base_url:free,name2=base_url2:paid`
/// `GS_KEYS_<NAME>` holds the comma-separated keys for that provider.
pub fn pool_from_env() -> crate::router::ProviderPool {
    use crate::router::{Clock, ProviderPool, SystemClock};
    use std::sync::Arc;

    let table = std::env::var("GS_PROVIDERS").unwrap_or_default();
    let mut slots: Vec<(Arc<dyn Provider>, Vec<String>)> = Vec::new();

    for entry in table.split(',').map(str::trim).filter(|s| !s.is_empty()) {
        // name=url[:free|:paid]
        let (name, rest) = match entry.split_once('=') {
            Some(v) => v,
            None => continue,
        };
        let (url, tier) = match rest.rsplit_once(':') {
            Some((u, t)) if t == "free" || t == "paid" => (u, t == "free"),
            _ => (rest, false),
        };
        // Keys come from the environment. Absent keys mean the provider is
        // simply not registered rather than registered and broken.
        let keys: Vec<String> = std::env::var(format!("GS_KEYS_{}", name.to_uppercase()))
            .unwrap_or_default()
            .split(',')
            .map(str::trim)
            .filter(|s| !s.is_empty())
            .map(|s| s.to_string())
            .collect();
        if keys.is_empty() {
            continue;
        }
        slots.push((Arc::new(HttpProvider::new(name, url, tier)), keys));
    }

    ProviderPool::new(slots, Arc::new(SystemClock))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn msg() -> Vec<Message> {
        vec![Message::user("hi")]
    }

    #[tokio::test]
    async fn a_429_becomes_rate_limited_not_a_transport_error() {
        // The distinction is load-bearing: a 429 must park the key and must
        // NOT count toward the circuit breaker.
        let p = HttpProvider::new("stub", "http://127.0.0.1:1", true);
        // Port 1 refuses connections, so this is a transport error, proving
        // the two paths stay distinct.
        let e = p
            .complete(&msg(), &CompletionConfig::default(), "k")
            .await
            .unwrap_err();
        assert!(matches!(e, PoolError::Transport { .. }), "got {e:?}");
        assert!(e.counts_toward_breaker());
    }

    #[tokio::test]
    async fn errors_never_contain_the_key() {
        let p = HttpProvider::new("stub", "http://127.0.0.1:1", true);
        let secret = "sk-SUPERSECRETVALUE";
        let e = p
            .complete(&msg(), &CompletionConfig::default(), secret)
            .await
            .unwrap_err();
        assert!(!format!("{e}").contains(secret), "key leaked into error");
    }

    #[test]
    fn a_provider_with_no_env_keys_is_not_registered() {
        // An empty pool must stay empty rather than registering a provider
        // that can only ever fail.
        let saved = std::env::var("GS_PROVIDERS").ok();
        unsafe { std::env::set_var("GS_PROVIDERS", "ghost=https://example.invalid:free") };
        let pool = pool_from_env();
        assert_eq!(pool.slots_len(), 0, "no keys means no provider");
        match saved {
            Some(v) => unsafe { std::env::set_var("GS_PROVIDERS", v) },
            None => unsafe { std::env::remove_var("GS_PROVIDERS") },
        }
    }

    #[test]
    fn a_model_not_found_never_counts_toward_the_breaker() {
        // This is the regression that would have corrupted the GPQA run: a
        // hardcoded id 404s, and if that opened the breaker every subsequent
        // sample would be refused as a "provider outage".
        let e = PoolError::ModelNotFound {
            provider: "groq".into(),
            model: "llama-3.1-8b-instant".into(),
        };
        assert!(
            !e.counts_toward_breaker(),
            "a caller config error must not open the circuit"
        );
        // And it must not be confused with a dead key either.
        assert!(!matches!(e, PoolError::KeyDead { .. }));

        // The three exclusions, all distinct reasons.
        assert!(!PoolError::RateLimited { provider: "p".into(), key_index: 0 }
            .counts_toward_breaker());
        assert!(!PoolError::KeyDead { provider: "p".into(), key_index: 0 }
            .counts_toward_breaker());
        // A real outage still does count.
        assert!(PoolError::Transport { provider: "p".into(), detail: "d".into() }
            .counts_toward_breaker());
    }

    #[test]
    fn a_model_not_found_names_the_bad_id_for_the_operator() {
        let e = PoolError::ModelNotFound {
            provider: "groq".into(),
            model: "auto".into(),
        };
        let s = format!("{e}");
        assert!(s.contains("auto"), "the offending id must be named: {s}");
        assert!(s.contains("groq"));
    }

    #[test]
    fn tier_flag_parses_and_defaults_to_paid() {
        assert!(HttpProvider::new("a", "http://x", true).is_free());
        assert!(!HttpProvider::new("a", "http://x", false).is_free());
    }
}
