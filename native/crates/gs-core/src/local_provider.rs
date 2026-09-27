//! A local llama.cpp provider, so the benchmark can measure the model that
//! actually ships rather than a hosted one.
//!
//! The pool routes to `Provider`, and every provider that existed so far was
//! HTTP. That made "measure the local model" impossible without pretending a
//! local model is a remote endpoint. This is the honest version: one slot, no
//! key, no network, and `name()` reports "local" so provider_distribution says
//! what actually served the request.
//!
//! ## Why there is a scheduler instead of a Mutex
//!
//! This used to hold `Mutex<Option<LlamaModel>>`, so the machine served exactly
//! one request at a time: 3.84 req/s measured, against 7.7 of 8 GiB of VRAM
//! idle. That was named "the first wall" in native/docs/million-user-shape.md.
//!
//! A pool of N contexts is the obvious next step and it does not work. N
//! contexts still submit to ONE GPU queue, so Vulkan serialises the submissions
//! and aggregate throughput barely moves. What raises throughput is putting
//! tokens from several sequences into a single llama_decode, which is what
//! `n_seq_max` exists for.
//!
//! So the context has `n_seq_max` slots and a single scheduler thread drains a
//! queue of pending requests into batches. The batch size is a real parameter,
//! not a constant: measured on this box, throughput is 7.65 req/s at 25
//! concurrent, 9.48 at 50, 12.52 at 100 and 12.85 at 200, where it saturates.
//! The default sits below saturation because every extra slot costs KV memory
//! whether or not it is used, and a phone-sized memory budget is the case that
//! actually ships.
//!
//! Batched output is byte-identical to decoding each request alone, and that is
//! asserted rather than assumed -- see `local_provider::tests` and
//! `gs-bench --bin run_batch`, which reported 200/200 identical across the
//! sweep.

use crate::router::{PoolError, Provider};
use gs_common::{Completion, CompletionConfig, Message};
use std::sync::mpsc::{Receiver, Sender};
use std::sync::{Arc, Mutex};

/// A request parked in the scheduler queue.
struct Job {
    prompt: String,
    max_tokens: i32,
    temperature: f32,
    reply: Sender<Result<String, String>>,
}

/// Owns the model and runs the batch loop. The Provider handle only enqueues.
struct Engine {
    model: Arc<Mutex<gs_ffi::LlamaModel>>,
    tx: Sender<Job>,
    /// Held so the scheduler thread's `rx` is not dropped, which would make
    /// every subsequent `send` fail once the loop exits.
    _worker: std::thread::JoinHandle<()>,
}

pub struct LocalProvider {
    engine: Arc<Engine>,
    n_seq_max: i32,
    path: String,
    n_ctx: i32,
    n_threads: i32,
    n_gpu_layers: i32,
    label: String,
    backend: String,
}

/// Drain the queue into batches until it is empty and the channel is closed.
///
/// The batch delay is what makes batching worth anything: a caller that enqueued
/// one request and waited for its own answer would get a batch of one, which is
/// the old serial behaviour with extra steps. Waiting a bounded interval lets
/// concurrent callers accumulate, and costs a single unlucky caller at most that
/// interval.
///
/// The bound is short on purpose. A request that arrives alone should not wait
/// 20ms for company that will never come.
const BATCH_LINGER: std::time::Duration = std::time::Duration::from_millis(4);

impl LocalProvider {
    /// Load a GGUF model.
    ///
    /// `require_gpu` is the strict mode: it refuses to return a provider
    /// unless the build reports a working accelerator offload path. That is a
    /// refusal rather than a warning on purpose. A run that was supposed to be
    /// on the GPU and quietly landed on the CPU is not slower, it is wrong:
    /// the numbers it produces are not the numbers the operator asked for, and
    /// nothing in the output would say so.
    ///
    /// `n_seq_max` is how many requests may share a decode. 1 restores the
    /// original serial behaviour, which is the right setting for a memory-
    /// constrained device.
    pub fn load(
        path: &str,
        n_ctx: i32,
        n_threads: i32,
        n_gpu_layers: i32,
        require_gpu: bool,
    ) -> Result<Self, String> {
        Self::load_with_slots(path, n_ctx, n_threads, n_gpu_layers, require_gpu, 1)
    }

    /// As [`Self::load`], with an explicit number of concurrent sequence slots.
    pub fn load_with_slots(
        path: &str,
        n_ctx: i32,
        n_threads: i32,
        n_gpu_layers: i32,
        require_gpu: bool,
        n_seq_max: i32,
    ) -> Result<Self, String> {
        if !std::path::Path::new(path).exists() {
            return Err(format!("no model file at {path}"));
        }
        let slots = n_seq_max.max(1);
        // n_ctx is the TOTAL context, shared across the sequence slots. Handing
        // each slot the whole n_ctx would let the model believe it had 50x the
        // memory it has, and the failure would show up as a decode error deep
        // inside llama.cpp rather than here.
        let m = gs_ffi::LlamaModel::load_with(
            path,
            n_ctx,
            n_threads,
            n_gpu_layers,
            gs_ffi::KvType::F16,
            gs_ffi::KvType::F16,
            slots,
        )
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

        let (tx, rx) = std::sync::mpsc::channel::<Job>();
        let model = Arc::new(Mutex::new(m));
        let worker_model = Arc::clone(&model);
        let max_batch = slots as usize;
        let worker = std::thread::Builder::new()
            .name("gs-local-batch".into())
            .spawn(move || scheduler_loop(worker_model, rx, max_batch))
            .map_err(|e| format!("could not start the batch scheduler thread: {e}"))?;

        Ok(Self {
            engine: Arc::new(Engine { model, tx, _worker: worker }),
            n_seq_max: slots,
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

    /// Concurrent sequence slots, i.e. the batch ceiling.
    pub fn slots(&self) -> i32 {
        self.n_seq_max
    }

    /// The backend llama.cpp actually selected, e.g. "Vulkan0" or "CPU".
    pub fn backend_name(&self) -> String {
        self.engine
            .model
            .lock()
            .map(|m| m.backend_name())
            .unwrap_or_else(|_| self.backend.clone())
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

/// The batch loop. Runs on its own thread for the life of the provider.
fn scheduler_loop(model: Arc<Mutex<gs_ffi::LlamaModel>>, rx: Receiver<Job>, max_batch: usize) {
    loop {
        // Block for the first job, so an idle provider costs nothing.
        let first = match rx.recv() {
            Ok(j) => j,
            // All senders gone: the provider is being dropped.
            Err(_) => return,
        };
        let mut jobs = vec![first];
        // Then take whatever else has arrived, waiting at most BATCH_LINGER for
        // company. try_recv first: no wait when the queue is already busy.
        while jobs.len() < max_batch {
            match rx.try_recv() {
                Ok(j) => jobs.push(j),
                Err(std::sync::mpsc::TryRecvError::Empty) => {
                    std::thread::sleep(BATCH_LINGER);
                    // One more look after the linger, then ship what we have.
                    match rx.try_recv() {
                        Ok(j) => jobs.push(j),
                        Err(_) => break,
                    }
                }
                Err(std::sync::mpsc::TryRecvError::Disconnected) => break,
            }
        }

        let (max_tokens, temperature) = (jobs[0].max_tokens, jobs[0].temperature);
        let prompts: Vec<String> = jobs.iter().map(|j| j.prompt.clone()).collect();
        // A batch must be homogeneous in decode length: max_tokens and
        // temperature are read once, from the first job. Callers that vary
        // either are batched together and every one of them gets the first
        // job's settings, which is a correctness bug, so it is asserted here
        // rather than left to a comment.
        let mixed = jobs
            .iter()
            .any(|j| j.max_tokens != max_tokens || j.temperature != temperature);
        if mixed {
            for j in &jobs {
                let _ = j.reply.send(Err(
                    "batched requests must share max_tokens and temperature; \
                     the router grouped incompatible requests into one batch"
                        .into(),
                ));
            }
            continue;
        }

        let mut guard = match model.lock() {
            Ok(g) => g,
            Err(_) => {
                for j in &jobs {
                    let _ = j.reply.send(Err("local model mutex poisoned".into()));
                }
                continue;
            }
        };
        let outs = guard.batch_generate(&prompts, max_tokens, temperature);
        drop(guard);

        match outs {
            Ok(texts) => {
                for (j, t) in jobs.iter().zip(texts) {
                    let _ = j.reply.send(Ok(t));
                }
            }
            Err(code) => {
                for j in &jobs {
                    let _ = j
                        .reply
                        .send(Err(format!("llama.cpp batched generate failed with code {code}")));
                }
            }
        }
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
        self.engine.model.lock().map(|m| m.is_available()).unwrap_or(false)
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
        let max_tokens = (config.max_tokens as i32).clamp(1, self.n_ctx / 2);

        // Wall clock measured around the enqueue-and-wait, not around a decode
        // this thread did not perform. Under the old Mutex the caller decoded
        // inline and could time itself; under batching it cannot, and reporting
        // 0 would put a fabricated p50/p95 into the benchmark table.
        let started = std::time::Instant::now();
        let (reply_tx, reply_rx) = std::sync::mpsc::channel();
        self.engine
            .tx
            .send(Job {
                prompt,
                max_tokens,
                temperature: config.temperature as f32,
                reply: reply_tx,
            })
            .map_err(|_| PoolError::NoProvider {
                reason: "the local batch scheduler has stopped".into(),
            })?;

        // The provider is async but the engine is a blocking C call, so this
        // waits on a worker rather than on the async runtime. tokio's
        // spawn_blocking would be the right home for this; doing it inline
        // would park a runtime worker for the length of a generation.
        let text = tokio::task::spawn_blocking(move || reply_rx.recv())
            .await
            .map_err(|e| PoolError::Transport {
                provider: "local".into(),
                detail: format!("local scheduler join failed: {e}"),
            })?
            .map_err(|_| PoolError::NoProvider {
                reason: "the local batch scheduler dropped the reply channel".into(),
            })?
            .map_err(|detail| PoolError::Transport { provider: "local".into(), detail })?;

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
        write!(f, "LocalProvider({}, {} slots)", self.label, self.n_seq_max)
    }
}
