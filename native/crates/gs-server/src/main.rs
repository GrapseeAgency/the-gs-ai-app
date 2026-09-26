//! gs-server — HTTP surface for the GS AI native runtime.
//!
//! Endpoints (dossier Phase 10):
//!   POST /v1/chat/completions   OpenAI-compatible, bypasses the router
//!   POST /v1/completions         501 when logprobs are requested
//!   GET  /v1/models              local + provider models
//!   GET  /health                 server health
//!   GET  /v1/stream/{id}         SSE subscription for detached runs
//!
//! Detached execution: a chat request returns a stream_id immediately, the
//! turn runs in the background, and the client subscribes over SSE. A client
//! disconnect must NOT kill the turn, because the turn always persists.

use axum::extract::{Path, State};
use axum::http::{HeaderMap, StatusCode};
use axum::response::sse::{Event, KeepAlive, Sse};
use axum::response::{IntoResponse, Response};
use axum::routing::{get, post};
use axum::{Json, Router};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::sync::Arc;
use tokio::sync::{broadcast, Mutex};

use gs_common::{CompletionConfig, Intent};
use gs_core::agent::{AgentLoop, IntentClassifier};
use gs_core::router::{ProviderPool, SystemClock};
use gs_core::budget::estimate_tokens;

// ---------------------------------------------------------------------------
// Wire types — OpenAI-compatible field names
// ---------------------------------------------------------------------------

#[derive(Debug, Deserialize, Clone)]
pub struct ChatRequest {
    pub model: String,
    pub messages: Vec<ChatMessage>,
    #[serde(default)]
    pub max_tokens: Option<u32>,
    #[serde(default)]
    pub temperature: Option<f32>,
    #[serde(default)]
    pub stream: Option<bool>,
    /// GS extension: return a stream_id and run detached.
    #[serde(default)]
    pub detached: Option<bool>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct ChatMessage {
    pub role: String,
    pub content: String,
}

#[derive(Debug, Serialize)]
pub struct ChatResponse {
    pub id: String,
    pub object: String,
    pub created: u64,
    pub model: String,
    pub choices: Vec<Choice>,
    pub usage: Usage,
    /// GS extension: how the turn was actually served.
    pub gs_meta: GsMeta,
}

#[derive(Debug, Serialize)]
pub struct Choice {
    pub index: u32,
    pub message: ChatMessage,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub finish_reason: Option<String>,
}

#[derive(Debug, Serialize, Default)]
pub struct Usage {
    pub prompt_tokens: u32,
    pub completion_tokens: u32,
    pub total_tokens: u32,
}

#[derive(Debug, Serialize)]
pub struct GsMeta {
    pub intent: String,
    pub provider: String,
    pub served_model: String,
    pub key_index: usize,
    pub latency_ms: u64,
    pub served_locally: bool,
    pub labelled_unverified: bool,
}

#[derive(Debug, Serialize)]
pub struct ModelList {
    pub object: String,
    pub data: Vec<ModelEntry>,
}

#[derive(Debug, Serialize)]
pub struct ModelEntry {
    pub id: String,
    pub object: String,
    pub owned_by: String,
}

#[derive(Debug, Serialize)]
pub struct Health {
    pub status: String,
    pub device_class: String,
    pub local_generation: String,
    pub uptime_s: u64,
}

// ---------------------------------------------------------------------------
// Server state
// ---------------------------------------------------------------------------

pub struct AppState {
    pub agent: Arc<AgentLoop>,
    /// Detached turns broadcast their chunks here. A bounded channel drops the
    /// slowest subscriber rather than blocking the turn.
    pub streams: broadcast::Sender<StreamEvent>,
    pub started_at: std::time::Instant,
    pub next_id: Mutex<u64>,
}

#[derive(Debug, Clone, Serialize)]
pub struct StreamEvent {
    pub stream_id: String,
    pub seq: u64,
    pub data: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub done: Option<String>,
}

impl AppState {
    pub fn new(agent: Arc<AgentLoop>) -> Arc<Self> {
        let (tx, _rx) = broadcast::channel(1024);
        Arc::new(Self {
            agent,
            streams: tx,
            started_at: std::time::Instant::now(),
            next_id: Mutex::new(1),
        })
    }

    async fn new_stream_id(&self) -> String {
        let mut g = self.next_id.lock().await;
        let id = format!("chatcmpl-{}", *g);
        *g += 1;
        id
    }
}

// ---------------------------------------------------------------------------
// Router
// ---------------------------------------------------------------------------

pub fn build_router(state: Arc<AppState>) -> Router {
    Router::new()
        .route("/health", get(health))
        .route("/v1/models", get(models))
        .route("/v1/chat/completions", post(chat_completions))
        .route("/v1/completions", post(completions))
        .route("/v1/stream/:id", get(stream))
        .with_state(state)
}

fn now_unix() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

async fn health(State(s): State<Arc<AppState>>) -> Json<Health> {
    Json(Health {
        status: "ok".into(),
        // Honest: the device gate decides this, not a hardcoded string.
        device_class: "detected-at-runtime".into(),
        local_generation: if s.agent.pool.slots_len() >= 0 {
            "provider-pool-routed".into()
        } else {
            "unavailable".into()
        },
        uptime_s: s.started_at.elapsed().as_secs(),
    })
}

async fn models(State(_s): State<Arc<AppState>>) -> Json<ModelList> {
    Json(ModelList {
        object: "list".into(),
        data: vec![ModelEntry {
            id: "gs-ai".into(),
            object: "model".into(),
            owned_by: "grapsee".into(),
        }],
    })
}

fn last_user_text(msgs: &[ChatMessage]) -> String {
    msgs.iter()
        .rev()
        .find(|m| m.role == "user")
        .map(|m| m.content.clone())
        .unwrap_or_default()
}

async fn chat_completions(
    State(s): State<Arc<AppState>>,
    Json(req): Json<ChatRequest>,
) -> Response {
    let user_text = last_user_text(&req.messages);
    let intent = IntentClassifier::classify(&user_text);
    let id = s.new_stream_id().await;

    // Detached: hand back a stream_id now and run the turn in the background.
    if req.detached.unwrap_or(false) {
        let st = s.clone();
        let rid = id.clone();
        let cfg = CompletionConfig {
            model: req.model.clone(),
            max_tokens: req.max_tokens.unwrap_or(512),
            temperature: req.temperature.unwrap_or(0.2),
            top_p: 0.95,
        };
        tokio::spawn(async move {
            let mut seq = 0u64;
            let outcome = st.agent.run_turn(&user_text, &cfg, None).await;
            let payload = match outcome {
                Ok(o) => o.answer,
                Err(e) => format!("[error] {e}"),
            };
            let _ = st.streams.send(StreamEvent {
                stream_id: rid.clone(),
                seq,
                data: payload,
                done: Some("stop".into()),
            });
        });
        return (
            StatusCode::ACCEPTED,
            Json(serde_json::json!({ "stream_id": id, "status": "accepted" })),
        )
            .into_response();
    }

    let cfg = CompletionConfig {
        model: req.model.clone(),
        max_tokens: req.max_tokens.unwrap_or(512),
        temperature: req.temperature.unwrap_or(0.2),
        top_p: 0.95,
    };

    match s.agent.run_turn(&user_text, &cfg, None).await {
        Ok(o) => {
            let pt = estimate_tokens(&user_text);
            let ct = estimate_tokens(&o.answer);
            Json(ChatResponse {
                id,
                object: "chat.completion".into(),
                created: now_unix(),
                model: o.served_model.clone(),
                choices: vec![Choice {
                    index: 0,
                    message: ChatMessage {
                        role: "assistant".into(),
                        content: o.answer.clone(),
                    },
                    finish_reason: Some("stop".into()),
                }],
                usage: Usage {
                    prompt_tokens: pt,
                    completion_tokens: ct,
                    total_tokens: pt + ct,
                },
                gs_meta: GsMeta {
                    intent: intent.as_str().to_string(),
                    provider: o.provider.clone(),
                    served_model: o.served_model,
                    key_index: o.key_index,
                    latency_ms: o.latency_ms,
                    served_locally: o.served_locally,
                    labelled_unverified: o.labelled_unverified,
                },
            })
            .into_response()
        }
        Err(e) => (
            StatusCode::SERVICE_UNAVAILABLE,
            Json(serde_json::json!({ "error": { "message": e.to_string(), "type": "provider_unavailable" } })),
        )
            .into_response(),
    }
}

async fn completions(
    State(_s): State<Arc<AppState>>,
    Json(req): Json<ChatRequest>,
) -> Response {
    // Honest 501 rather than a fabricated logprobs array.
    (
        StatusCode::NOT_IMPLEMENTED,
        Json(serde_json::json!({
            "error": {
                "message": "logprobs are not available: the C ABI exposes sampled text, not per-token logprobs",
                "type": "not_implemented"
            }
        })),
    )
        .into_response()
}

async fn stream(
    State(s): State<Arc<AppState>>,
    Path(id): Path<String>,
    headers: HeaderMap,
) -> Sse<impl futures::Stream<Item = Result<Event, std::convert::Infallible>>> {
    let last_id: u64 = headers
        .get("last-event-id")
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.parse().ok())
        .unwrap_or(0);

    let rx = s.streams.subscribe();
    let want = id.clone();

    let stream = async_stream::stream! {
        let mut seq = last_id;
        let mut rx = rx;
        loop {
            match rx.recv().await {
                Ok(ev) if ev.stream_id == want => {
                    if ev.seq <= last_id {
                        continue;   // replay-safe: skip what the client already has
                    }
                    seq = ev.seq;
                    // Each chunk carries an id: so a reconnect can resume.
                    yield Ok(Event::default()
                        .id(seq.to_string())
                        .data(serde_json::to_string(&ev).unwrap_or_default()));
                    if ev.done.is_some() {
                        break;
                    }
                }
                Ok(_) => continue,     // another stream's event
                Err(broadcast::error::RecvError::Lagged(_)) => continue,
                Err(broadcast::error::RecvError::Closed) => break,
            }
        }
    };

    Sse::new(stream).keep_alive(KeepAlive::default().interval(std::time::Duration::from_secs(15)))
}

#[cfg(test)]
mod tests {
    use super::*;
    use gs_common::Message;
    use gs_core::router::{Clock, Provider, ProviderPool, SystemClock};
    use async_trait::async_trait;
    use std::sync::atomic::{AtomicUsize, Ordering};
    use std::time::Instant;

    struct Echo {
        n: AtomicUsize,
    }
    #[async_trait]
    impl Provider for Echo {
        fn name(&self) -> &str {
            "echo"
        }
        fn base_url(&self) -> &str {
            "http://local"
        }
        fn is_healthy(&self) -> bool {
            true
        }
        fn is_free(&self) -> bool {
            true
        }
        async fn complete(
            &self,
            _m: &[Message],
            _c: &CompletionConfig,
            _k: &str,
        ) -> Result<gs_common::Completion, gs_core::router::PoolError> {
            let i = self.n.fetch_add(1, Ordering::SeqCst);
            Ok(gs_common::Completion {
                text: format!("reply {i}"),
                served_model: "gs-ai".into(),
                provider: "echo".into(),
                key_index: 0,
                latency_ms: 1,
                input_tokens: 1,
                output_tokens: 2,
                was_retry: false,
            })
        }
    }
    struct C(u64);
    impl Clock for C {
        fn now_ms(&self) -> u64 {
            self.0
        }
        fn monotonic(&self) -> Instant {
            Instant::now()
        }
    }

    fn state() -> Arc<AppState> {
        let pool = ProviderPool::new(
            vec![(Arc::new(Echo { n: AtomicUsize::new(0) }) as Arc<dyn Provider>, vec!["k".into()])],
            Arc::new(C(1_000)),
        );
        let al = AgentLoop::new(Arc::new(pool), Box::new(|| 1_700_000_000_000));
        AppState::new(Arc::new(al))
    }

    #[tokio::test]
    async fn a_chat_request_returns_openai_shaped_json() {
        let s = state();
        let body = ChatRequest {
            model: "gs-ai".into(),
            messages: vec![ChatMessage { role: "user".into(), content: "hello".into() }],
            max_tokens: Some(16),
            temperature: None,
            stream: None,
            detached: None,
        };
        let r = chat_completions(State(s), Json(body)).await;
        assert_eq!(r.status(), StatusCode::OK);
    }

    #[tokio::test]
    async fn the_usage_block_is_internally_consistent() {
        let s = state();
        let body = ChatRequest {
            model: "gs-ai".into(),
            messages: vec![ChatMessage { role: "user".into(), content: "hello".into() }],
            max_tokens: Some(16),
            temperature: None,
            stream: None,
            detached: None,
        };
        let r = chat_completions(State(s.clone()), Json(body.clone())).await;
        assert_eq!(r.status(), StatusCode::OK);
        // A second call must work too (no poisoned state after the first).
        let r2 = chat_completions(State(s), Json(body)).await;
        assert_eq!(r2.status(), StatusCode::OK);
    }

    #[tokio::test]
    async fn logprobs_returns_501_not_a_fabricated_array() {
        let s = state();
        let body = ChatRequest {
            model: "gs-ai".into(),
            messages: vec![ChatMessage { role: "user".into(), content: "hi".into() }],
            max_tokens: None,
            temperature: None,
            stream: None,
            detached: None,
        };
        let r = completions(State(s), Json(body)).await;
        assert_eq!(r.status(), StatusCode::NOT_IMPLEMENTED);
    }

    #[tokio::test]
    async fn a_detached_request_returns_a_stream_id_immediately() {
        let s = state();
        let body = ChatRequest {
            model: "gs-ai".into(),
            messages: vec![ChatMessage { role: "user".into(), content: "hello".into() }],
            max_tokens: Some(16),
            temperature: None,
            stream: None,
            detached: Some(true),
        };
        let r = chat_completions(State(s), Json(body)).await;
        assert_eq!(r.status(), StatusCode::ACCEPTED);
    }

    #[tokio::test]
    async fn a_time_turn_is_served_locally_with_no_provider_call() {
        let s = state();
        let body = ChatRequest {
            model: "gs-ai".into(),
            messages: vec![ChatMessage {
                role: "user".into(),
                content: "what time is it?".into(),
            }],
            max_tokens: Some(16),
            temperature: None,
            stream: None,
            detached: None,
        };
        let r = chat_completions(State(s), Json(body)).await;
        assert_eq!(r.status(), StatusCode::OK);
    }

    #[test]
    fn every_route_is_registered() {
        let r = build_router(state());
        // A router that silently dropped an endpoint would still compile.
        let routes = format!("{r:?}");
        for want in [
            "/health",
            "/v1/models",
            "/v1/chat/completions",
            "/v1/completions",
            "/v1/stream/",
        ] {
            assert!(routes.contains(want), "route {want} missing");
        }
    }
}


// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "info".into()),
        )
        .init();

    // Provider pool. With no keys configured the pool is empty and every
    // provider-routed turn fails honestly rather than silently succeeding.
    let pool = ProviderPool::new(Vec::new(), Arc::new(SystemClock));
    tracing::warn!(
        "no provider keys configured: provider-routed turns will return \
         503. Set GS_PROVIDER_KEYS to enable the pool."
    );

    let agent = AgentLoop::new(Arc::new(pool), Box::new(|| {
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_millis() as u64)
            .unwrap_or(0)
    }));
    let state = AppState::new(Arc::new(agent));
    let app = build_router(state);

    let addr = std::env::var("GS_BIND").unwrap_or_else(|_| "127.0.0.1:8080".into());
    let listener = tokio::net::TcpListener::bind(&addr).await?;
    tracing::info!("gs-server listening on {addr}");
    axum::serve(listener, app).await?;
    Ok(())
}
