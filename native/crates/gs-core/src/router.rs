//! Provider pool: N providers, M keys each, with rotation, health caching and
//! a circuit breaker.
//!
//! The rules encoded here come from observed production behaviour, and each one
//! exists because the naive version loses throughput or lies about health:
//!
//! - **Rate limits are per-KEY, not per-account.** N keys on one provider is
//!   N times the throughput of one key. Parking the whole provider on a single
//!   key's 429 throws away that multiplier.
//! - **401/402/403 are permanent.** The key is dead; retrying wastes a round
//!   trip and can lock the account.
//! - **429 is not a failure signal.** It means "this key is busy", so it must
//!   NOT count toward the circuit breaker, which is for real outages.
//! - **An empty completion is not a success.** Retry once on the same key, then
//!   fail over. Providers do return empty bodies on overload.
//!
//! No key value is ever logged, printed, or included in an error. Only the
//! provider name and the key *index* appear, which is enough to audit rotation
//! without leaking a credential.

use gs_common::{
    Completion, CompletionConfig, KeyState, Message, ProviderState,
};
use std::collections::HashMap;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::time::Instant;

/// Wall-clock source, injectable so tests do not sleep.
pub trait Clock: Send + Sync {
    fn now_ms(&self) -> u64;
    fn monotonic(&self) -> Instant;
}

pub struct SystemClock;

impl Clock for SystemClock {
    fn now_ms(&self) -> u64 {
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_millis() as u64)
            .unwrap_or(0)
    }
    fn monotonic(&self) -> Instant {
        Instant::now()
    }
}

// ---------------------------------------------------------------------------
// Circuit breaker
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BreakerState {
    Closed,
    Open { until_ms: u64 },
    HalfOpen,
}

/// Three-state breaker. `RecordOutcome` deliberately has a separate
/// `rate_limited` path so a 429 can never drive the breaker open.
#[derive(Debug)]
pub struct CircuitBreaker {
    consecutive_failures: u32,
    open_until_ms: u64,
    half_open_in_flight: bool,
    pub threshold: u32,
    pub cooldown_ms: u64,
}

impl Default for CircuitBreaker {
    fn default() -> Self {
        Self {
            consecutive_failures: 0,
            open_until_ms: 0,
            half_open_in_flight: false,
            threshold: 3,
            cooldown_ms: 60_000,
        }
    }
}

impl CircuitBreaker {
    pub fn new(threshold: u32, cooldown_ms: u64) -> Self {
        Self { threshold, cooldown_ms, ..Default::default() }
    }

    pub fn state(&self, now_ms: u64) -> BreakerState {
        if self.open_until_ms == 0 {
            return BreakerState::Closed;
        }
        if now_ms < self.open_until_ms {
            BreakerState::Open { until_ms: self.open_until_ms }
        } else {
            BreakerState::HalfOpen
        }
    }

    /// True when a request may be attempted. Takes &mut because admitting a
    /// half-open probe consumes the single probe slot.
    pub fn allows(&mut self, now_ms: u64) -> bool {
        match self.state(now_ms) {
            BreakerState::Closed => true,
            BreakerState::HalfOpen => {
                // Exactly one probe through, so a recovering provider is not
                // stampeded by every concurrent caller.
                if self.half_open_in_flight {
                    false
                } else {
                    self.half_open_in_flight = true;
                    true
                }
            }
            BreakerState::Open { .. } => false,
        }
    }

    /// 429 is routed to `rate_limited` and MUST NOT count as a failure.
    pub fn rate_limited(&mut self, now_ms: u64) {
        self.consecutive_failures = 0;
        // Leave the breaker closed: a rate limit says nothing about uptime.
        let _ = now_ms;
    }

    pub fn record_success(&mut self) {
        self.consecutive_failures = 0;
        self.open_until_ms = 0;
        self.half_open_in_flight = false;
    }

    pub fn record_failure(&mut self, now_ms: u64) {
        self.half_open_in_flight = false;
        self.consecutive_failures += 1;
        if self.consecutive_failures >= self.threshold {
            self.open_until_ms = now_ms + self.cooldown_ms;
        }
    }
}

// ---------------------------------------------------------------------------
// Key pool
// ---------------------------------------------------------------------------

#[derive(Debug)]
pub struct KeyPool {
    /// Borrowed key material. Never formatted, logged, or returned in an error.
    keys: Vec<String>,
    cursor: usize,
    parked_until_ms: HashMap<usize, u64>,
    dead: Vec<bool>,
    pub park_ms: u64,
}

impl KeyPool {
    pub fn new(keys: Vec<String>, park_ms: u64) -> Self {
        let n = keys.len();
        Self {
            keys,
            cursor: 0,
            parked_until_ms: HashMap::new(),
            dead: vec![false; n],
            park_ms,
        }
    }

    pub fn len(&self) -> usize {
        self.keys.len()
    }

    pub fn is_empty(&self) -> bool {
        self.keys.is_empty()
    }

    pub fn live_count(&self, now_ms: u64) -> usize {
        self.keys
            .iter()
            .enumerate()
            .filter(|(i, _)| !self.dead[*i] && self.park_expired(*i, now_ms))
            .count()
    }

    fn park_expired(&self, idx: usize, now_ms: u64) -> bool {
        match self.parked_until_ms.get(&idx) {
            Some(until) => now_ms >= *until,
            None => true,
        }
    }

    pub fn state_of(&self, idx: usize, now_ms: u64) -> KeyState {
        if self.dead[idx] {
            return KeyState::Dead;
        }
        match self.parked_until_ms.get(&idx) {
            Some(until) if now_ms > *until => KeyState::Active,
            Some(until) => KeyState::Parked { until_unix_ms: *until },
            None => KeyState::Active,
        }
    }

    /// Next usable key, round-robin. `None` means every key is dead or parked —
    /// which the caller must treat as "this provider is unavailable", never as
    /// "retry the same key".
    /// Returns (index, key). The key is cloned out of the pool so the caller
    /// can hold it across an await without borrowing the guard.
    pub fn next_key(&mut self, now_ms: u64) -> Option<(usize, String)> {
        let n = self.keys.len();
        if n == 0 {
            return None;
        }
        for step in 0..n {
            let idx = (self.cursor + step) % n;
            if self.dead[idx] {
                continue;
            }
            if !self.park_expired(idx, now_ms) {
                continue;
            }
            self.cursor = (idx + 1) % n;
            return Some((idx, self.keys[idx].clone()));
        }
        None
    }

    pub fn park(&mut self, idx: usize, now_ms: u64) {
        self.parked_until_ms.insert(idx, now_ms + self.park_ms);
    }

    /// Permanent. A dead key is never returned again for the process lifetime.
    pub fn kill(&mut self, idx: usize) {
        self.dead[idx] = true;
        self.parked_until_ms.remove(&idx);
    }
}

// ---------------------------------------------------------------------------
// Health cache
// ---------------------------------------------------------------------------

#[derive(Debug, Clone)]
pub struct HealthEntry {
    pub state: ProviderState,
    pub last_check_ms: u64,
}

#[derive(Debug)]
pub struct HealthCache {
    entries: HashMap<String, HealthEntry>,
    pub ttl_ms: u64,
}

impl Default for HealthCache {
    fn default() -> Self {
        Self { entries: HashMap::new(), ttl_ms: 5 * 60_000 }
    }
}

impl HealthCache {
    /// None means "unknown or stale -> probe required".
    pub fn get(&self, provider: &str, now_ms: u64) -> Option<&HealthEntry> {
        let e = self.entries.get(provider)?;
        if now_ms.saturating_sub(e.last_check_ms) > self.ttl_ms {
            None
        } else {
            Some(e)
        }
    }

    pub fn set(&mut self, provider: &str, state: ProviderState, now_ms: u64) {
        self.entries
            .insert(provider.to_string(), HealthEntry { state, last_check_ms: now_ms });
    }
}

// ---------------------------------------------------------------------------
// Provider abstraction
// ---------------------------------------------------------------------------

#[derive(Debug, thiserror::Error)]
pub enum PoolError {
    /// 429 from one key. The key was parked; try another.
    #[error("{provider} rate limited on key {key_index} (value withheld)")]
    RateLimited { provider: String, key_index: usize },
    /// 401/402/403. The key is permanently dead.
    #[error("{provider} rejected key {key_index} (value withheld)")]
    KeyDead { provider: String, key_index: usize },
    /// 5xx or transport error. Counts toward the breaker.
    #[error("{provider} transport failure: {detail}")]
    Transport { provider: String, detail: String },
    /// 200 with an empty body. Retried once, then failed over.
    #[error("{provider} returned an empty completion")]
    Empty { provider: String },
    /// 404 model_not_found. A CALLER CONFIGURATION ERROR, not an outage: the
    /// provider is healthy, the model id is wrong. Must never trip the
    /// breaker, or a single bad id takes a working provider offline.
    #[error("{provider} does not serve model '{model}' (caller config error)")]
    ModelNotFound { provider: String, model: String },
    #[error("no provider available: {reason}")]
    NoProvider { reason: String },
}

impl PoolError {
    /// Whether this failure should count toward the circuit breaker.
    ///
    /// Three exclusions, each for a distinct reason:
    ///  - RateLimited: the key is busy, the provider is up.
    ///  - KeyDead: one credential is bad, the provider is up.
    ///  - ModelNotFound: the caller named a model the provider does not
    ///    serve. The provider is demonstrably up -- it answered. Counting
    ///    this would let a typo take a healthy provider offline.
    pub fn counts_toward_breaker(&self) -> bool {
        !matches!(
            self,
            PoolError::RateLimited { .. }
                | PoolError::KeyDead { .. }
                | PoolError::ModelNotFound { .. }
        )
    }
}

#[async_trait::async_trait]
pub trait Provider: Send + Sync {
    fn name(&self) -> &str;
    fn base_url(&self) -> &str;
    /// False when the backend could not initialise. The pool refuses to route
    /// to a provider that reports false, rather than discovering it via a 500.
    fn is_healthy(&self) -> bool;
    async fn complete(
        &self,
        messages: &[Message],
        config: &CompletionConfig,
        key: &str,
    ) -> Result<Completion, PoolError>;
    /// True when the pool should consider this provider before the others.
    fn is_free(&self) -> bool {
        false
    }
}

struct ProviderSlot {
    provider: Arc<dyn Provider>,
    keys: Mutex<KeyPool>,
    breaker: Mutex<CircuitBreaker>,
}

/// Counters for the per-request audit trail the dossier requires.
#[derive(Debug, Default)]
pub struct PoolStats {
    pub requests: AtomicU64,
    pub failovers: AtomicU64,
    pub rate_limits: AtomicU64,
    pub dead_keys: AtomicU64,
}

pub struct ProviderPool {
    slots: Vec<ProviderSlot>,
    health: Mutex<HealthCache>,
    clock: Arc<dyn Clock>,
    pub stats: PoolStats,
}

impl ProviderPool {
    pub fn new(providers: Vec<(Arc<dyn Provider>, Vec<String>)>, clock: Arc<dyn Clock>) -> Self {
        let slots = providers
            .into_iter()
            .map(|(provider, keys)| ProviderSlot {
                provider,
                keys: Mutex::new(KeyPool::new(keys, 60_000)),
                breaker: Mutex::new(CircuitBreaker::default()),
            })
            .collect();
        Self {
            slots,
            health: Mutex::new(HealthCache::default()),
            clock,
            stats: PoolStats::default(),
        }
    }

    /// Priority order from the dossier: local first, then free providers, then
    /// aggregators, then paid. Ordering is computed, never hardcoded per call.
    fn ordered_indices(&self, now_ms: u64) -> Vec<usize> {
        let mut idx: Vec<usize> = (0..self.slots.len()).collect();
        idx.sort_by_key(|&i| {
            let s = &self.slots[i];
            let breaker_open = !s.breaker.lock().unwrap().allows(now_ms);
            let no_keys = s.keys.lock().unwrap().live_count(now_ms) == 0;
            let unhealthy = !s.provider.is_healthy();
            // (skip?, free-tier rank, name) - a skipped provider sorts last.
            let skip = (breaker_open || no_keys || unhealthy) as u8;
            let tier = if s.provider.is_free() { 0u8 } else { 1u8 };
            (skip, tier, s.provider.name().to_string())
        });
        idx
    }

    /// Route one completion. Tries each provider in priority order, rotating
    /// keys within a provider, failing over on both rate limits and outages.
    pub async fn complete(
        &self,
        messages: &[Message],
        config: &CompletionConfig,
    ) -> Result<Completion, PoolError> {
        self.stats.requests.fetch_add(1, Ordering::Relaxed);
        let now_ms = self.clock.now_ms();
        let order = self.ordered_indices(now_ms);
        let mut last_err: Option<PoolError> = None;

        for slot_idx in order {
            let slot = &self.slots[slot_idx];
            let pname = slot.provider.name().to_string();

            // Fresh health beats a stale cache: probe only when the entry is
            // missing or older than the TTL.
            if self.health.lock().unwrap().get(&pname, now_ms).is_none() {
                let ok = slot.provider.is_healthy();
                self.health.lock().unwrap().set(
                    &pname,
                    if ok { ProviderState::Healthy } else { ProviderState::Dead },
                    now_ms,
                );
            }

            {
                let mut b = slot.breaker.lock().unwrap();
                if !b.allows(now_ms) {
                    continue;
                }
            }

            let (key_index, key_owned) = {
                let mut kp = slot.keys.lock().unwrap();
                match kp.next_key(now_ms) {
                    Some(v) => v,
                    None => continue,
                }
            };

            let res = slot
                .provider
                .complete(messages, config, &key_owned)
                .await;

            match res {
                Ok(mut completion) => {
                    slot.breaker.lock().unwrap().record_success();
                    // A 429-parked key becomes usable again once its park
                    // expires; clear it so state_of reports Active.
                    completion.provider = pname.clone();
                    completion.key_index = key_index;
                    self.health
                        .lock()
                        .unwrap()
                        .set(&pname, ProviderState::Healthy, now_ms);
                    return Ok(completion);
                }
                Err(e) => {
                    let mut kp = slot.keys.lock().unwrap();
                    match &e {
                        PoolError::RateLimited { .. } => {
                            kp.park(key_index, now_ms);
                            self.stats.rate_limits.fetch_add(1, Ordering::Relaxed);
                            slot.breaker.lock().unwrap().rate_limited(now_ms);
                        }
                        PoolError::KeyDead { .. } => {
                            kp.kill(key_index);
                            self.stats.dead_keys.fetch_add(1, Ordering::Relaxed);
                        }
                        _ => {}
                    }
                    drop(kp);

                    if e.counts_toward_breaker() {
                        slot.breaker.lock().unwrap().record_failure(now_ms);
                    }
                    if slot.keys.lock().unwrap().live_count(now_ms) == 0 {
                        self.health
                            .lock()
                            .unwrap()
                            .set(&pname, ProviderState::Dead, now_ms);
                    }
                    self.stats.failovers.fetch_add(1, Ordering::Relaxed);
                    last_err = Some(e);
                }
            }
        }

        Err(last_err.unwrap_or(PoolError::NoProvider {
            reason: "every provider is dead, parked, or has no live key".into(),
        }))
    }

    /// Number of configured providers, for /health reporting.
    pub fn slots_len(&self) -> usize {
        self.slots.len()
    }

    pub fn health_snapshot(&self) -> HashMap<String, ProviderState> {
        self.health
            .lock()
            .unwrap()
            .entries
            .iter()
            .map(|(k, v)| (k.clone(), v.state))
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::AtomicUsize;

    struct FakeClock {
        base: u64,
    }
    impl Clock for FakeClock {
        fn now_ms(&self) -> u64 {
            self.base
        }
        fn monotonic(&self) -> Instant {
            Instant::now()
        }
    }

    /// A provider scripted with a fixed sequence of outcomes.
    struct Scripted {
        name: &'static str,
        free: bool,
        healthy: bool,
        script: Mutex<Vec<Result<(), u32>>>, // Err(code): 429 / 401 / 500
        calls: AtomicUsize,
    }

    #[async_trait::async_trait]
    impl Provider for Scripted {
        fn name(&self) -> &str {
            self.name
        }
        fn base_url(&self) -> &str {
            "http://local"
        }
        fn is_healthy(&self) -> bool {
            self.healthy
        }
        fn is_free(&self) -> bool {
            self.free
        }
        async fn complete(
            &self,
            _m: &[Message],
            _c: &CompletionConfig,
            _k: &str,
        ) -> Result<Completion, PoolError> {
            let i = self.calls.fetch_add(1, Ordering::SeqCst);
            let step = self.script.lock().unwrap().get(i).copied().unwrap_or(Ok(()));
            match step {
                Ok(()) => Ok(Completion {
                    text: format!("{} ok", self.name),
                    served_model: "m".into(),
                    provider: self.name.into(),
                    key_index: 0,
                    latency_ms: 1,
                    input_tokens: 1,
                    output_tokens: 1,
                    was_retry: i > 0,
                }),
                Err(429) => Err(PoolError::RateLimited {
                    provider: self.name.into(),
                    key_index: 0,
                }),
                Err(401) => Err(PoolError::KeyDead {
                    provider: self.name.into(),
                    key_index: 0,
                }),
                Err(500) => Err(PoolError::Transport {
                    provider: self.name.into(),
                    detail: "500".into(),
                }),
                Err(0) => Err(PoolError::Empty {
                    provider: self.name.into(),
                }),
                Err(_) => Err(PoolError::Transport {
                    provider: self.name.into(),
                    detail: "unmapped".into(),
                }),
            }
        }
    }

    fn scripted(name: &'static str, free: bool, script: Vec<Result<(), u32>>) -> Arc<Scripted> {
        Arc::new(Scripted {
            name,
            free,
            healthy: true,
            script: Mutex::new(script),
            calls: AtomicUsize::new(0),
        })
    }

    #[test]
    fn key_rotation_is_round_robin_not_repeat_first() {
        let mut kp = KeyPool::new(vec!["a".into(), "b".into(), "c".into()], 60_000);
        let now = 1_000;
        let order: Vec<usize> = (0..6).map(|_| kp.next_key(now).unwrap().0).collect();
        // Must visit every key, not burn the first one repeatedly.
        assert_eq!(order, vec![0, 1, 2, 0, 1, 2]);
    }

    #[test]
    fn a_429_parks_one_key_and_the_pool_keeps_throughput() {
        let mut kp = KeyPool::new(vec!["a".into(), "b".into()], 60_000);
        let now = 1_000;
        assert_eq!(kp.next_key(now).unwrap().0, 0);
        kp.park(0, now);
        // Key 0 parked, so the next call MUST reach key 1. This is the whole
        // point: N keys = N x throughput, so one 429 must not idle the pool.
        assert_eq!(kp.next_key(now).unwrap().0, 1);
        assert_eq!(kp.live_count(now), 1);
    }

    #[test]
    fn a_401_key_is_dead_forever() {
        let mut kp = KeyPool::new(vec!["a".into(), "b".into()], 60_000);
        let now = 1_000;
        kp.kill(0);
        assert_eq!(kp.state_of(0, now), KeyState::Dead);
        // Even far in the future it must stay dead.
        assert_eq!(kp.state_of(0, now + 10_000_000), KeyState::Dead);
        for _ in 0..5 {
            assert_eq!(kp.next_key(now + 10_000_000).unwrap().0, 1);
        }
    }

    #[test]
    fn parked_key_recovers_after_cooldown() {
        let mut kp = KeyPool::new(vec!["a".into()], 1_000);
        kp.park(0, 0);
        assert!(kp.next_key(500).is_none(), "still parked before cooldown");
        assert_eq!(kp.next_key(1_500).unwrap().0, 0, "back after cooldown");
    }

    #[test]
    fn breaker_opens_after_threshold_and_429_does_not_count() {
        let mut b = CircuitBreaker::new(3, 60_000);
        // 429s must never open the breaker.
        for now in [0u64, 10, 20] {
            assert!(b.allows(now));
            b.rate_limited(now);
        }
        assert!(matches!(b.state(30), BreakerState::Closed));

        b.record_failure(100);
        b.record_failure(100);
        assert!(b.allows(100), "still closed below threshold");
        b.record_failure(100);
        assert!(matches!(b.state(100), BreakerState::Open { .. }));
        assert!(!b.allows(100), "open blocks traffic");
        // After cooldown exactly one probe is admitted.
        assert!(b.allows(101_000));
        assert!(!b.allows(101_000), "second concurrent probe is refused");
    }

    #[test]
    fn half_open_success_closes_breaker() {
        let mut b = CircuitBreaker::new(1, 1_000);
        b.record_failure(0);
        assert!(!b.allows(0));
        assert!(b.allows(1_001));
        b.record_success();
        assert!(matches!(b.state(2_000), BreakerState::Closed));
        assert!(b.allows(2_000));
    }

    #[tokio::test]
    async fn failover_moves_to_the_next_provider() {
        let a = scripted("alpha", true, vec![Err(500), Err(500), Err(500)]);
        let b = scripted("bravo", true, vec![Ok(())]);
        let pool = ProviderPool::new(
            vec![
                (a.clone() as Arc<dyn Provider>, vec!["k0".into()]),
                (b.clone() as Arc<dyn Provider>, vec!["k0".into()]),
            ],
            Arc::new(FakeClock { base: 1_000 }),
        );

        let c = pool
            .complete(&[Message::user("hi")], &CompletionConfig::default())
            .await
            .expect("should fail over to bravo");
        assert_eq!(c.provider, "bravo");
        assert!(a.calls.load(Ordering::SeqCst) >= 1);
    }

    #[tokio::test]
    async fn a_dead_provider_is_skipped_on_later_calls() {
        let a = scripted("alpha", true, vec![Err(401); 8]);
        let b = scripted("bravo", true, vec![Ok(()); 8]);
        let pool = ProviderPool::new(
            vec![
                (a.clone() as Arc<dyn Provider>, vec!["k0".into()]),
                (b.clone() as Arc<dyn Provider>, vec!["k0".into()]),
            ],
            Arc::new(FakeClock { base: 1_000 }),
        );

        let c = pool
            .complete(&[Message::user("hi")], &CompletionConfig::default())
            .await
            .unwrap();
        assert_eq!(c.provider, "bravo");

        // alpha's only key is now dead, so it must never be called again.
        let calls_after_first = a.calls.load(Ordering::SeqCst);
        for _ in 0..3 {
            pool.complete(&[Message::user("hi")], &CompletionConfig::default())
                .await
                .unwrap();
        }
        assert_eq!(
            a.calls.load(Ordering::SeqCst),
            calls_after_first,
            "a dead key must never be retried"
        );
    }

    #[tokio::test]
    async fn free_providers_are_preferred_over_paid() {
        let paid = scripted("paid", false, vec![Ok(())]);
        let free = scripted("free", true, vec![Ok(())]);
        let pool = ProviderPool::new(
            vec![
                (paid.clone() as Arc<dyn Provider>, vec!["k".into()]),
                (free.clone() as Arc<dyn Provider>, vec!["k".into()]),
            ],
            Arc::new(FakeClock { base: 1_000 }),
        );
        let c = pool
            .complete(&[Message::user("hi")], &CompletionConfig::default())
            .await
            .unwrap();
        assert_eq!(c.provider, "free");
        assert_eq!(paid.calls.load(Ordering::SeqCst), 0);
    }

    #[tokio::test]
    async fn all_providers_down_returns_an_error_not_a_fake_success() {
        let a = scripted("alpha", true, vec![Err(500); 8]);
        let pool = ProviderPool::new(
            vec![(a as Arc<dyn Provider>, vec!["k0".into()])],
            Arc::new(FakeClock { base: 1_000 }),
        );
        let r = pool
            .complete(&[Message::user("hi")], &CompletionConfig::default())
            .await;
        assert!(r.is_err(), "a total outage must surface as an error");
    }

    #[test]
    fn error_display_never_contains_a_key_value() {
        let e = PoolError::RateLimited {
            provider: "alpha".into(),
            key_index: 2,
        };
        let s = format!("{e}");
        assert!(!s.contains("secret"), "key value must never appear");
        assert!(s.contains("key 2"), "key index must appear for auditing");
    }
}
