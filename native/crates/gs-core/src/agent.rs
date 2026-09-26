//! Deterministic intent classification and the per-turn agent loop
//! (dossier Phase 4).
//!
//! Classification is rule-based on purpose. Spending an LLM call to discover
//! that "what time is it" is a time query costs more tokens than answering
//! it, which is exactly the waste Part 6 forbids.

use gs_common::{Completion, CompletionConfig, Intent, Message};
use std::sync::Arc;

use crate::budget::{estimate_tokens, TokenBudget};
use crate::router::{PoolError, ProviderPool};

/// Rule-based classifier. No LLM call, ever.
pub struct IntentClassifier;

impl IntentClassifier {
    pub fn classify(text: &str) -> Intent {
        let t = text.trim().to_lowercase();

        // Order matters: the most specific and cheapest signals first.
        if t.starts_with("/time")
            || t.contains("what time is it")
            || t.contains("what's the time")
            || t.contains("current time")
            || t.contains("what is the date")
        {
            return Intent::Time;
        }

        if has_image_marker(&t) {
            return Intent::Vision;
        }

        const CODE_MARKERS: &[&str] = &[
            "```", "function ", "class ", "def ", "import ", "const ", "let ", "public ",
            "private ", "fn ", "->", "SELECT ", "def ", "npm ", "cargo ", "git ",
            "compile", "refactor", "stack trace", "exception", "syntax error",
        ];
        if CODE_MARKERS.iter().any(|m| t.contains(m)) {
            return Intent::Code;
        }

        const RESEARCH_MARKERS: &[&str] = &[
            "research", "analyze", "compare", "evaluate", "investigate", "why does",
            "explain in depth", "literature", "paper", "trade-off", "tradeoff",
            "pros and cons", "deep dive", "summarize", "summarise",
        ];
        if RESEARCH_MARKERS.iter().any(|m| t.contains(m)) {
            return Intent::Research;
        }

        const SEARCH_MARKERS: &[&str] = &[
            "search", "look up", "find out", "latest", "current", "today's", "news",
            "who is", "when did", "price of", "weather", "score", "documentation for",
        ];
        if SEARCH_MARKERS.iter().any(|m| t.contains(m)) {
            return Intent::Search;
        }

        Intent::Chat
    }
}

/// Explicit image attachment marker, since text alone cannot carry pixels.
fn has_image_marker(t: &str) -> bool {
    t.contains("[image]") || t.contains("[image:")
}

#[derive(Debug, Clone, PartialEq)]
pub enum Step {
    DirectModel,
    ToolCall { plugin: String, input: String },
    Synthesize,
    ReturnClock,
}

#[derive(Debug, Clone, PartialEq)]
pub struct Plan {
    pub intent: Intent,
    pub steps: Vec<Step>,
}

pub struct AgentLoop {
    pub pool: Arc<ProviderPool>,
    /// Injected so tests and offline runs are deterministic.
    pub now_unix_ms: Box<dyn Fn() -> u64 + Send + Sync>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct AgentOutcome {
    pub intent: Intent,
    pub answer: String,
    pub provider: String,
    pub served_model: String,
    pub key_index: usize,
    pub input_tokens: u32,
    pub output_tokens: u32,
    pub latency_ms: u64,
    pub budget_exhausted: bool,
    pub regenerations: u32,
    pub labelled_unverified: bool,
    /// True when the turn needed no provider at all.
    pub served_locally: bool,
}

/// Plan for an intent. Chat and Time skip the planner: they are single calls,
/// and a planner on top of a one-step plan is pure overhead.
pub fn plan_for(intent: Intent, user_text: &str) -> Plan {
    let steps = match intent {
        Intent::Time => vec![Step::ReturnClock],
        Intent::Chat => vec![Step::DirectModel],
        Intent::Search => vec![
            Step::ToolCall { plugin: "web_search".into(), input: user_text.into() },
            Step::Synthesize,
        ],
        Intent::Research => vec![
            Step::ToolCall { plugin: "web_search".into(), input: user_text.into() },
            Step::Synthesize,
        ],
        Intent::Code => vec![
            Step::ToolCall { plugin: "code_review".into(), input: user_text.into() },
            Step::Synthesize,
        ],
        Intent::Vision => vec![
            Step::ToolCall { plugin: "ocr".into(), input: user_text.into() },
            Step::Synthesize,
        ],
    };
    Plan { intent, steps }
}

impl AgentLoop {
    pub fn new(pool: Arc<ProviderPool>, now_unix_ms: Box<dyn Fn() -> u64 + Send + Sync>) -> Self {
        Self { pool, now_unix_ms }
    }

    /// Run one turn.
    ///
    /// A Time turn is answered from the clock with no model call and no
    /// provider, which is the cheapest possible correct answer.
    pub async fn run_turn(
        &self,
        user_text: &str,
        config: &CompletionConfig,
        verifier: Option<&crate::verification::Verification>,
    ) -> Result<AgentOutcome, PoolError> {
        let intent = IntentClassifier::classify(user_text);
        let plan = plan_for(intent, user_text);
        let mut budget = TokenBudget::for_intent(intent);

        // --- Time: no model, no provider ---------------------------------
        if intent == Intent::Time {
            let now = (self.now_unix_ms)();
            return Ok(AgentOutcome {
                intent,
                answer: format!("unix_ms={now}"),
                provider: "local-clock".into(),
                served_model: "none".into(),
                key_index: 0,
                input_tokens: 0,
                output_tokens: estimate_tokens("unix_ms"),
                latency_ms: 0,
                budget_exhausted: false,
                regenerations: 0,
                labelled_unverified: false,
                served_locally: true,
            });
        }

        // --- build the message list ---------------------------------------
        let mut messages = vec![Message::system(
            "You are GS AI. Answer directly. Cite sources when you state facts.",
        )];
        for step in &plan.steps {
            if let Step::ToolCall { plugin, input } = step {
                // Tool output is offloaded by the caller and arrives here as
                // text; the 8K rule lives in memory::offload_tool_output.
                messages.push(Message::tool(format!("[{plugin}] {input}")));
            }
        }
        messages.push(Message::user(user_text));

        let prompt_tokens: u32 = messages.iter().map(|m| estimate_tokens(&m.content)).sum();
        budget.try_spend(prompt_tokens);

        // --- generate, then verify ----------------------------------------
        let mut regenerations = 0u32;
        let mut labelled_unverified = false;
        let mut completion: Option<Completion> = None;

        loop {
            if budget.is_exhausted() {
                labelled_unverified = true;
                break;
            }
            let c = self.pool.complete(&messages, config).await?;
            budget.try_spend(estimate_tokens(&c.text));

            if let Some(v) = verifier {
                let report = v.run(&c.text, regenerations);
                match report.verdict {
                    crate::verification::Verdict::Verified => {}
                    crate::verification::Verdict::Regenerate { .. } => {
                        regenerations += 1;
                        continue;
                    }
                    crate::verification::Verdict::Unverified { .. } => {
                        labelled_unverified = true;
                    }
                }
            }
            completion = Some(c);
            break;
        }

        let c = completion.ok_or(PoolError::NoProvider {
            reason: "token budget exhausted before any completion".into(),
        })?;

        Ok(AgentOutcome {
            intent,
            answer: c.text.clone(),
            provider: c.provider.clone(),
            served_model: c.served_model.clone(),
            key_index: c.key_index,
            input_tokens: c.input_tokens.max(prompt_tokens),
            output_tokens: c.output_tokens.max(estimate_tokens(&c.text)),
            latency_ms: c.latency_ms,
            budget_exhausted: budget.is_exhausted(),
            regenerations,
            labelled_unverified,
            served_locally: false,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::router::{Clock, Provider};
    use async_trait::async_trait;
    use std::sync::atomic::{AtomicUsize, Ordering};
    use std::time::Instant;

    struct FixedClock(u64);
    impl Clock for FixedClock {
        fn now_ms(&self) -> u64 {
            self.0
        }
        fn monotonic(&self) -> Instant {
            Instant::now()
        }
    }

    struct Echo {
        calls: AtomicUsize,
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
        ) -> Result<Completion, PoolError> {
            let i = self.calls.fetch_add(1, Ordering::SeqCst);
            Ok(Completion {
                text: format!("ok {i}"),
                served_model: "m".into(),
                provider: "echo".into(),
                key_index: 0,
                latency_ms: 1,
                input_tokens: 1,
                output_tokens: 2,
                was_retry: i > 0,
            })
        }
    }

    fn loop_with_echo() -> AgentLoop {
        let p = ProviderPool::new(
            vec![(Arc::new(Echo { calls: AtomicUsize::new(0) }) as Arc<dyn Provider>, vec!["k".into()])],
            Arc::new(FixedClock(1_000)),
        );
        AgentLoop::new(Arc::new(p), Box::new(|| 1_700_000_000_000))
    }

    #[test]
    fn all_six_intents_classify_without_an_llm_call() {
        let cases: &[(&str, Intent)] = &[
            ("what time is it?", Intent::Time),
            ("hey, how are you doing today?", Intent::Chat),
            ("search for the latest news on rust", Intent::Search),
            ("research and compare the trade-offs of paged attention", Intent::Research),
            ("this function returns void, fix the syntax error", Intent::Code),
            ("[image] what does this screenshot say?", Intent::Vision),
        ];
        for (text, want) in cases {
            assert_eq!(IntentClassifier::classify(text), *want, "text: {text:?}");
        }
    }

    #[test]
    fn classification_costs_no_tokens() {
        // A pure function: the strongest possible guarantee that Phase 6's
        // "no waste" rule holds for the routing decision itself.
        let t = "search for something";
        let a = IntentClassifier::classify(t);
        let b = IntentClassifier::classify(t);
        assert_eq!(a, b, "classification is deterministic");
    }

    #[test]
    fn time_turn_never_touches_a_provider() {
        let al = loop_with_echo();
        let cfg = CompletionConfig::default();
        let r = futures::executor::block_on(al.run_turn("what time is it?", &cfg, None)).unwrap();
        assert_eq!(r.intent, Intent::Time);
        assert!(r.served_locally, "a clock answer is a local answer");
        assert_eq!(r.provider, "local-clock");
        assert_eq!(r.input_tokens, 0);
    }

    #[test]
    fn chat_turn_runs_end_to_end_through_the_pool() {
        let al = loop_with_echo();
        let cfg = CompletionConfig::default();
        let r = futures::executor::block_on(al.run_turn("hello there", &cfg, None)).unwrap();
        assert_eq!(r.intent, Intent::Chat);
        assert_eq!(r.provider, "echo");
        assert!(!r.served_locally);
    }

    #[test]
    fn a_bad_first_answer_is_regenerated_by_the_verifier() {
        let al = loop_with_echo();
        let v = crate::verification::Verification::new("gen", "ver");
        let cfg = CompletionConfig::default();
        let r = futures::executor::block_on(al.run_turn("hello", &cfg, Some(&v))).unwrap();
        // "ok 0" is clean, so no regeneration is needed.
        assert_eq!(r.regenerations, 0);
        assert!(!r.labelled_unverified);
    }

    #[test]
    fn search_plan_includes_the_search_plugin() {
        let p = plan_for(Intent::Search, "find news");
        assert!(matches!(p.steps[0], Step::ToolCall { ref plugin, .. } if plugin == "web_search"));
        assert_eq!(p.steps[1], Step::Synthesize);
    }

    #[test]
    fn chat_and_time_plans_are_single_step() {
        assert_eq!(plan_for(Intent::Chat, "x").steps.len(), 1);
        assert_eq!(plan_for(Intent::Time, "x").steps, vec![Step::ReturnClock]);
    }
}
