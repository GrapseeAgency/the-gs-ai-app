//! Right-sized model routing: the cheapest model that can plausibly do the job.
//!
//! The claim being tested is "1B for classification, 7B+ for reasoning". The
//! part worth measuring is not the routing table, it is whether the tier chosen
//! for each intent actually changed anything. A router that picks different
//! models while every pick returns the same quality is a router that costs
//! memory for nothing.
//!
//! Every decision is logged with the reason, because a routing decision you
//! cannot explain is indistinguishable from a coin flip that happened to work.

use std::path::PathBuf;

/// One entry in the local registry.
#[derive(Debug, Clone)]
pub struct ModelEntry {
    pub name: &'static str,
    pub path: PathBuf,
    /// File size in bytes. The proxy for capability that does not require
    /// running the model.
    pub bytes: u64,
    pub tier: Tier,
    pub strength: &'static str,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Tier {
    /// Classification, extraction, short factual chat.
    Small,
    /// Summarisation, search shaping, medium chat.
    Mid,
    /// Multi-step reasoning, code, research.
    Large,
}

impl Tier {
    pub fn as_str(self) -> &'static str {
        match self {
            Tier::Small => "small",
            Tier::Mid => "mid",
            Tier::Large => "large",
        }
    }
}

/// Why a particular model was chosen. Printed verbatim in the demo.
#[derive(Debug, Clone)]
pub struct Decision {
    pub intent: String,
    pub model: String,
    pub tier: Tier,
    pub bytes: u64,
    pub reason: String,
    pub fell_back_to_provider: bool,
}

/// The registry. Populated from disk so a missing model is a reported fact
/// rather than a crash at the first request.
#[derive(Debug, Default)]
pub struct Registry {
    pub models: Vec<ModelEntry>,
}

/// Which tier an intent needs. Kept as data so the policy is inspectable and a
/// test can assert it, rather than buried in a match inside a request path.
pub fn tier_for_intent(intent: &str) -> Tier {
    match intent {
        // Cheap, local, no reasoning required.
        "classification" | "extraction" | "vision" | "time" | "image_create" => Tier::Small,
        // Needs to hold a document but not reason across it.
        "summarization" | "search" | "chat" => Tier::Mid,
        // Multi-step. A small model is not merely worse here, it is wrong.
        "reasoning" | "code" | "research" => Tier::Large,
        // Unknown intent: assume it is harder than it looks.
        _ => Tier::Large,
    }
}

impl Registry {
    /// Build from a list of (name, path, tier, strength), skipping anything not
    /// on disk and reporting what was skipped.
    pub fn discover(candidates: &[(&'static str, &str, Tier, &'static str)]) -> (Self, Vec<String>) {
        let mut models = Vec::new();
        let mut missing = Vec::new();
        for (name, path, tier, strength) in candidates {
            let p = PathBuf::from(path);
            match std::fs::metadata(&p) {
                Ok(m) => models.push(ModelEntry {
                    name,
                    path: p,
                    bytes: m.len(),
                    tier: *tier,
                    strength,
                }),
                Err(_) => missing.push(format!("{name} ({path})")),
            }
        }
        (Self { models }, missing)
    }

    /// Pick the smallest registered model in `tier`.
    ///
    /// "Smallest in tier" rather than "smallest overall" is the whole point: a
    /// 135M draft would win a naive size sort and be useless for reasoning.
    pub fn route(&self, intent: &str) -> Decision {
        let want = tier_for_intent(intent);
        let candidate = self
            .models
            .iter()
            .filter(|m| m.tier == want)
            .min_by_key(|m| m.bytes);

        match candidate {
            Some(m) => {
                let reason = format!(
                    "intent `{}` needs {} capability; {} is the smallest registered {} model \
                     ({} bytes, {}) and no smaller model is rated for it",
                    intent,
                    want.as_str(),
                    m.name,
                    want.as_str(),
                    m.bytes,
                    m.strength
                );
                Decision {
                    intent: intent.to_string(),
                    model: m.name.to_string(),
                    tier: m.tier,
                    bytes: m.bytes,
                    reason,
                    fell_back_to_provider: false,
                }
            }
            None => {
                let have = self
                    .models
                    .iter()
                    .map(|m| format!("{} ({})", m.name, m.tier.as_str()))
                    .collect::<Vec<_>>()
                    .join(", ");
                Decision {
                    intent: intent.to_string(),
                    model: "provider".into(),
                    tier: want,
                    bytes: 0,
                    reason: format!(
                        "no {} model registered; routing to a provider rather than silently \
                         downgrading a {} task. Registered: [{}]",
                        want.as_str(),
                        want.as_str(),
                        if have.is_empty() { "none".into() } else { have }
                    ),
                    fell_back_to_provider: true,
                }
            }
        }
    }

    pub fn total_bytes(&self) -> u64 {
        self.models.iter().map(|m| m.bytes).sum()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn reg() -> Registry {
        Registry {
            models: vec![
                ModelEntry { name: "tiny", path: PathBuf::from("/x"), bytes: 100, tier: Tier::Small, strength: "extraction" },
                ModelEntry { name: "mid", path: PathBuf::from("/y"), bytes: 2_000, tier: Tier::Mid, strength: "summarisation" },
                ModelEntry { name: "big", path: PathBuf::from("/z"), bytes: 20_000, tier: Tier::Large, strength: "reasoning" },
            ],
        }
    }

    #[test]
    fn each_intent_lands_in_its_own_tier() {
        let r = reg();
        for (intent, want) in [
            ("classification", Tier::Small),
            ("extraction", Tier::Small),
            ("summarization", Tier::Mid),
            ("search", Tier::Mid),
            ("chat", Tier::Mid),
            ("reasoning", Tier::Large),
            ("code", Tier::Large),
            ("research", Tier::Large),
        ] {
            let d = r.route(intent);
            assert_eq!(d.tier, want, "{intent}");
            assert!(!d.fell_back_to_provider, "{intent} fell back");
        }
    }

    #[test]
    fn a_smaller_model_in_another_tier_does_not_win() {
        // The 100-byte "tiny" must never serve reasoning just because it is
        // smallest overall.
        let d = reg().route("reasoning");
        assert_eq!(d.model, "big");
        assert!(d.reason.contains("smallest registered large"));
    }

    #[test]
    fn a_missing_tier_routes_to_a_provider_and_says_why() {
        let r = Registry { models: vec![reg().models[0].clone()] };
        let d = r.route("research");
        assert!(d.fell_back_to_provider);
        assert_eq!(d.model, "provider");
        assert!(d.reason.contains("no large model registered"));
    }

    #[test]
    fn an_unknown_intent_is_treated_as_hard() {
        assert_eq!(tier_for_intent("something-new"), Tier::Large);
    }

    #[test]
    fn every_decision_carries_a_non_empty_reason() {
        let r = reg();
        for i in ["classification", "chat", "code"] {
            assert!(r.route(i).reason.len() > 20, "{i} has no usable reason");
        }
    }
}
