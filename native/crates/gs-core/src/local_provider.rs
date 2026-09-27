//! A local llama.cpp provider, so the benchmark can measure the model that
//! actually ships rather than a hosted one.
//!
//! The pool routes to `Provider`, and every provider that existed so far was
//! HTTP. That made "measure the local model" impossible without pretending a
//! local model is a remote endpoint. This is the honest version: one slot, no
//! key, no network, and `name()` reports "local" so provider_distribution says
//! what actually served the request.

use crate::router::{PoolError, Provider};
use gs_common::{Completion, CompletionConfig, Message};
use std::sync::Mutex;

/// Serialises access to the single context. llama.cpp contexts are not
/// concurrent-safe, and the pool is `Send + Sync`, so this is a hard lock
/// rather than a pool of contexts: one model, one context, honest latency.
pub struct LocalProvider {
    model: Mutex<Option<gs_ffi::LlamaModel>>,
    path: String,
    n_ctx: i32,
    n_threads: i32,
    n_gpu_layers: i32,
    label: String,
    backend: String,
}

impl LocalProvider {
    /// Load a GGUF model.
    ///
    /// `require_gpu` is the strict mode: it refuses to return a provider
    /// unless the build reports a working accelerator offload path. That is a
    /// refusal rather than a warning on purpose. A run that was supposed to be
    /// on the GPU and quietly landed on the CPU is not slower, it is wrong:
    /// the numbers it produces are not the numbers the operator asked for, and
    /// nothing in the output would say so.
    pub fn load(
        path: &str,
        n_ctx: i32,
        n_threads: i32,
        n_gpu_layers: i32,
        require_gpu: bool,
    ) -> Result<Self, String> {
        if !std::path::Path::new(path).exists() {
            return Err(format!("no model file at {path}"));
        }
        let m = gs_ffi::LlamaModel::load(path, n_ctx, n_threads, n_gpu_layers)
            .map_err(|code| format!("LlamaModel::load({path}) failed with code {code}"))?;
        if require_gpu {
            if n_gpu_layers == 0 {
                return Err(
                    "GPU-only requested but n_gpu_layers is 0. Pass a negative value \
                     to offload every layer."
                        .into(),
                );
            }
            if !m.gpu_offload_supported() {
                return Err(format!(
                    "GPU-only requested but this llama.cpp build reports no working \
                     accelerator offload path (backend: {}). Refusing to run on the CPU, \
                     because a silent fallback would produce CPU numbers labelled as GPU.",
                    m.backend_name()
                ));
            }
        }
        let backend = m.backend_name();
        Ok(Self {
            model: Mutex::new(Some(m)),
            backend,
            path: path.to_string(),
            n_ctx,
            n_threads,
            n_gpu_layers,
            label: format!("local:{}", std::path::Path::new(path).file_name().map(|s| s.to_string_lossy().into_owned()).unwrap_or_else(|| path.into())),
        })
    }

    pub fn model_path(&self) -> &str {
        &self.path
    }

    pub fn context(&self) -> i32 {
        self.n_ctx
    }

    pub fn threads(&self) -> i32 {
        self.n_threads
    }

    pub fn gpu_layers(&self) -> i32 {
        self.n_gpu_layers
    }

    /// The backend llama.cpp actually selected, e.g. "Vulkan0" or "CPU".
    pub fn backend_name(&self) -> String {
        self.model
            .lock()
            .ok()
            .and_then(|g| g.as_ref().map(|m| m.backend_name()))
            .unwrap_or_else(|| self.backend.clone())
    }

    /// Render a chat transcript the way the local model expects it.
    ///
    /// Deliberately a plain instruction + transcript rather than a template
    /// the model was not trained on: a wrong template would make the benchmark
    /// measure the template.
    fn render(messages: &[Message], config: &CompletionConfig) -> String {
        let mut s = String::new();
        for m in messages {
            match m.role {
                gs_common::Role::System => {
                    s.push_str("### System\n");
                    s.push_str(&m.content);
                    s.push_str("\n\n");
                }
                gs_common::Role::Assistant => {
                    s.push_str("### Assistant\n");
                    s.push_str(&m.content);
                    s.push_str("\n\n");
                }
                _ => {
                    s.push_str("### User\n");
                    s.push_str(&m.content);
                    s.push_str("\n\n");
                }
            }
        }
        s.push_str("### Assistant\n");
        // An explicit stop keeps the model from starting a new turn it will
        // never be asked to complete.
        if config.max_tokens == 0 {
            s.push_str("\n(stop)");
        }
        s
    }
}

#[async_trait::async_trait]
impl Provider for LocalProvider {
    fn name(&self) -> &str {
        "local"
    }

    fn base_url(&self) -> &str {
        "file://local"
    }

    fn is_healthy(&self) -> bool {
        self.model.lock().map(|g| g.is_some()).unwrap_or(false)
    }

    fn is_free(&self) -> bool {
        // No quota, no rate limit, no money. It is the only provider for which
        // that is literally true.
        true
    }

    async fn complete(
        &self,
        messages: &[Message],
        config: &CompletionConfig,
        _key: &str,
    ) -> Result<Completion, PoolError> {
        let prompt = Self::render(messages, config);
        let mut guard = self
            .model
            .lock()
            .map_err(|_| PoolError::NoProvider { reason: "local model mutex poisoned".into() })?;

        let model = guard.as_mut().ok_or_else(|| PoolError::NoProvider {
            reason: "local model was not loaded".into(),
        })?;

        let max_tokens = (config.max_tokens as i32).clamp(1, self.n_ctx / 2);
        // Wall clock, not a provider-reported number. llama.cpp exposes no
        // per-call timing through this ABI, and reporting 0 would put a
        // fabricated p50/p95 into the benchmark table.
        let started = std::time::Instant::now();
        let text = model
            .generate(&prompt, max_tokens, config.temperature as f32)
            .map_err(|code| PoolError::Transport {
                provider: "local".into(),
                detail: format!("llama.cpp generate failed with code {code}"),
            })?;
        let latency_ms = started.elapsed().as_millis() as u64;

        // Token counts are not observable through this ABI. They stay 0 rather
        // than being estimated, because a wrong count that looks measured is
        // worse than a visible gap.
        Ok(Completion {
            text,
            provider: "local".into(),
            served_model: format!("{} [{}]", self.label, self.backend),
            key_index: 0,
            input_tokens: 0,
            output_tokens: 0,
            latency_ms,
            was_retry: false,
        })
    }
}

impl std::fmt::Debug for LocalProvider {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "LocalProvider({})", self.label)
    }
}
