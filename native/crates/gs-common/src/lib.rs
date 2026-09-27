//! gs-common — shared types for the GS AI native runtime.
//!
//! Rule 10: this crate must pass `cargo check` and `cargo clippy`.

use serde::{Deserialize, Serialize};

/// Intent classes. The classifier is deterministic — no LLM call — because
/// routing an obvious "what time is it" through a model to discover it is a
/// time query is exactly the token waste the dossier forbids.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum Intent {
    Chat,
    Search,
    Research,
    Code,
    Vision,
    /// Procedural image generation. Geometry only: no weights, no GPU.
    ImageCreate,
    /// Diffusion image generation. Runs stable-diffusion.cpp on the GPU.
    ImageGenerate,
    Time,
}

impl Intent {
    /// Token budget per turn, from the dossier's table.
    pub fn token_budget(self) -> u32 {
        match self {
            Intent::Chat => 512,
            Intent::Time => 128,
            Intent::Vision => 4096,
            Intent::Search => 4096,
            Intent::Code => 8192,
            Intent::Research => 16_384,
            // Procedural generation is pure geometry: no tokens are spent.
            Intent::ImageCreate => 256,
            // Diffusion is a local compute job, not a model conversation.
            Intent::ImageGenerate => 256,
        }
    }

    pub fn as_str(self) -> &'static str {
        match self {
            Intent::Chat => "chat",
            Intent::Search => "search",
            Intent::Research => "research",
            Intent::Code => "code",
            Intent::Vision => "vision",
            Intent::ImageCreate => "image_create",
            Intent::ImageGenerate => "image_generate",
            Intent::Time => "time",
        }
    }
}

/// Device capability class. Mirrors `gs_device_class_t` in the C ABI.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum DeviceClass {
    Unknown,
    Low,
    Mid,
    High,
}

impl DeviceClass {
    /// Largest model (in billions of parameters) this class may load locally.
    pub fn max_local_params_b(self) -> Option<u64> {
        match self {
            DeviceClass::Low => None, // local inference disabled entirely
            DeviceClass::Mid => Some(1),
            DeviceClass::High => Some(3),
            DeviceClass::Unknown => Some(1),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Message {
    pub role: Role,
    pub content: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Role {
    System,
    User,
    Assistant,
    Tool,
}

impl Message {
    pub fn system(content: impl Into<String>) -> Self {
        Self { role: Role::System, content: content.into() }
    }
    pub fn user(content: impl Into<String>) -> Self {
        Self { role: Role::User, content: content.into() }
    }
    pub fn assistant(content: impl Into<String>) -> Self {
        Self { role: Role::Assistant, content: content.into() }
    }
    pub fn tool(content: impl Into<String>) -> Self {
        Self { role: Role::Tool, content: content.into() }
    }
}

#[derive(Debug, Clone)]
pub struct CompletionConfig {
    pub model: String,
    pub max_tokens: u32,
    pub temperature: f32,
    pub top_p: f32,
    /// Optional OpenAI `response_format` body. A json_schema with an enum is
    /// how a benchmark forces a parseable answer INSTEAD OF parsing prose.
    /// Verified supported on Groq and OpenRouter.
    pub response_format: Option<serde_json::Value>,
}

impl Default for CompletionConfig {
    fn default() -> Self {
        Self { model: String::new(), max_tokens: 512, temperature: 0.2, top_p: 0.95, response_format: None }
    }
}

#[derive(Debug, Clone)]
pub struct Completion {
    pub text: String,
    /// Model actually served the request, which may differ from requested.
    pub served_model: String,
    pub provider: String,
    /// Index into that provider's key pool. Reported for the key-rotation
    /// audit trail; never the key value itself.
    pub key_index: usize,
    pub latency_ms: u64,
    pub input_tokens: u32,
    pub output_tokens: u32,
    pub was_retry: bool,
}

/// Health of one (provider, key) pair.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum KeyState {
    /// Available now.
    Active,
    /// Rate limited; usable again after `until`.
    Parked { until_unix_ms: u64 },
    /// Permanently dead (401/402/403). Never reused.
    Dead,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ProviderState {
    Healthy,
    RateLimited { until_unix_ms: u64 },
    Dead,
    Unknown,
}

/// Result of scoring a sample, always with a confidence interval.
/// Rule 6: every score carries a CI. A bare percentage is not a legal output.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ScoreWithCI {
    pub score: f64,
    /// Wilson score interval at 95%.
    pub ci_low: f64,
    pub ci_high: f64,
    pub n_samples: u32,
    pub n_failed: u32,
    pub n_errored: u32,
    pub n_skipped: u32,
}

impl ScoreWithCI {
    /// Wilson score interval. Chosen over normal-approximation because it
    /// stays inside [0,1] and remains sane at small n, which is exactly the
    /// regime a 198-question benchmark sits in.
    pub fn wilson(successes: u32, n: u32) -> Self {
        if n == 0 {
            return Self {
                score: 0.0,
                ci_low: 0.0,
                ci_high: 0.0,
                n_samples: 0,
                n_failed: 0,
                n_errored: 0,
                n_skipped: 0,
            };
        }
        let z = 1.959_963_984_540_054_f64; // 95% two-sided
        let nf = n as f64;
        let p = successes as f64 / nf;
        let z2 = z * z;
        let denom = 1.0 + z2 / nf;
        let centre = p + z2 / (2.0 * nf);
        let margin = z * ((p * (1.0 - p) / nf) + z2 / (4.0 * nf * nf)).sqrt();
        Self {
            score: p,
            ci_low: ((centre - margin) / denom).max(0.0),
            ci_high: ((centre + margin) / denom).min(1.0),
            n_samples: n,
            n_failed: 0,
            n_errored: 0,
            n_skipped: 0,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn wilson_interval_brackets_the_point_estimate() {
        let s = ScoreWithCI::wilson(50, 100);
        assert!(s.ci_low < s.score && s.score < s.ci_high);
        // A 198-question benchmark: the CI must be wide enough to be honest.
        let g = ScoreWithCI::wilson(100, 198);
        assert!(g.ci_high - g.ci_low > 0.05, "CI too narrow to be informative");
    }

    #[test]
    fn wilson_stays_inside_unit_interval_at_extremes() {
        for (k, n) in [(0u32, 10u32), (10, 10), (0, 1), (1, 1)] {
            let s = ScoreWithCI::wilson(k, n);
            assert!((0.0..=1.0).contains(&s.ci_low), "ci_low out of range for {k}/{n}");
            assert!((0.0..=1.0).contains(&s.ci_high), "ci_high out of range for {k}/{n}");
        }
    }

    #[test]
    fn low_device_class_forbids_all_local_inference() {
        assert_eq!(DeviceClass::Low.max_local_params_b(), None);
        assert_eq!(DeviceClass::Mid.max_local_params_b(), Some(1));
        assert_eq!(DeviceClass::High.max_local_params_b(), Some(3));
    }

    #[test]
    fn token_budgets_are_ordered_by_task_cost() {
        assert!(Intent::Time.token_budget() < Intent::Chat.token_budget());
        assert!(Intent::Chat.token_budget() < Intent::Vision.token_budget());
        assert!(Intent::Vision.token_budget() < Intent::Code.token_budget());
        assert!(Intent::Code.token_budget() < Intent::Research.token_budget());
    }
}
