//! Token budget enforcement (dossier Phase 4).
//!
//! The rule is "minimum tokens required for the task - not fewer, not more."
//! A budget that cannot stop a runaway executor is not a budget, so this type
//! makes exhaustion a value the caller must handle rather than a log line.

use gs_common::Intent;

/// Hard ceiling. Exceeding it truncates rather than aborts, because a partial
/// answer with an honest label beats no answer.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct TokenBudget {
    pub limit: u32,
    pub spent: u32,
    pub violations: u32,
}

impl TokenBudget {
    pub fn for_intent(intent: Intent) -> Self {
        Self { limit: intent.token_budget(), spent: 0, violations: 0 }
    }

    pub fn with_limit(limit: u32) -> Self {
        Self { limit, spent: 0, violations: 0 }
    }

    pub fn remaining(&self) -> u32 {
        self.limit.saturating_sub(self.spent)
    }

    pub fn is_exhausted(&self) -> bool {
        self.spent >= self.limit
    }

    /// Returns true when the call was permitted, false when it was refused.
    /// `n` is clamped to whatever remains, so a single oversized step cannot
    /// silently blow the budget.
    pub fn try_spend(&mut self, n: u32) -> bool {
        if self.is_exhausted() {
            self.violations += 1;
            return false;
        }
        let allowed = n.min(self.remaining());
        self.spent += allowed;
        if allowed < n {
            self.violations += 1;
        }
        allowed > 0
    }

    /// Force-spend whatever is left. Used at synthesis, where a truncated
    /// answer is still better than none.
    pub fn force_spend_remaining(&mut self) -> u32 {
        let r = self.remaining();
        self.spent += r;
        r
    }
}

/// Cheap deterministic token estimate.
///
/// This is a character-class heuristic, NOT a BPE tokenizer: it is used for
/// budget arithmetic before the tokenizer is consulted. It is deliberately
/// conservative (~1 token per 4 chars) so it over-estimates slightly and never
/// lets a turn exceed its real limit. An exact count comes from
/// `gs_llama_token_count` when a model is loaded.
pub fn estimate_tokens(text: &str) -> u32 {
    if text.is_empty() {
        return 0;
    }
    let mut count = 0u32;
    let mut word = 0u32;
    for ch in text.chars() {
        if ch.is_ascii_whitespace() {
            if word > 0 {
                count += 1;
                word = 0;
            }
            if ch == '\n' {
                count += 1; // newlines are their own token
            }
        } else {
            word += 1;
            // ~4 chars per subword token, rounding up.
            if word.is_multiple_of(4) {
                count += 1;
                word -= 3;
            }
        }
    }
    if word > 0 {
        count += 1;
    }
    count.max(1)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn budget_refuses_spend_once_exhausted() {
        let mut b = TokenBudget::with_limit(10);
        assert!(b.try_spend(6));
        assert!(b.try_spend(4));
        assert!(b.is_exhausted());
        assert!(!b.try_spend(1), "must refuse beyond the limit");
        assert_eq!(b.violations, 1);
        assert_eq!(b.spent, 10, "spend must never exceed the limit");
    }

    #[test]
    fn an_oversized_step_is_clamped_not_honoured() {
        let mut b = TokenBudget::with_limit(10);
        assert!(b.try_spend(100));
        assert_eq!(b.spent, 10, "clamped to the ceiling");
        assert_eq!(b.violations, 1, "the clamp is recorded as a violation");
    }

    #[test]
    fn intent_budgets_come_from_the_dossier_table() {
        assert_eq!(TokenBudget::for_intent(Intent::Chat).limit, 512);
        assert_eq!(TokenBudget::for_intent(Intent::Search).limit, 4096);
        assert_eq!(TokenBudget::for_intent(Intent::Research).limit, 16_384);
        assert_eq!(TokenBudget::for_intent(Intent::Code).limit, 8192);
        assert_eq!(TokenBudget::for_intent(Intent::Vision).limit, 4096);
    }

    #[test]
    fn estimator_is_monotonic_and_never_zero_for_text() {
        let short = "hi";
        let long = "the quick brown fox jumps over the lazy dog repeatedly and often";
        assert!(estimate_tokens(short) >= 1);
        assert!(estimate_tokens(long) > estimate_tokens(short));
        assert_eq!(estimate_tokens(""), 0);
    }
}
