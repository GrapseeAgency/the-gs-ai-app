//! gs-core — the Rust orchestration layer.
//!
//! Owns the agent loop, provider routing, memory tiers, tool/skill loading,
//! verification guards and token-budget enforcement. No inference code lives
//! here: C++ owns that, behind the C ABI in gs-ffi.

pub mod agent;
pub mod http_provider;
pub mod image;
pub mod local_provider;
pub mod scratch;
pub mod budget;
pub mod memory;
pub mod router;
pub mod skills;
pub mod tools;
pub mod verification;
pub mod vision;

pub use agent::{AgentLoop, AgentOutcome, IntentClassifier, Plan, Step};
pub use budget::TokenBudget;
pub use memory::{MemoryStore, Compaction, WorkingMemory};
pub use http_provider::{HttpProvider, pool_from_env};
pub use router::{ProviderPool, PoolError, Provider};
pub use vision::{gather as gather_vision_evidence, VisionEvidence};
pub use image::diffuse as diffuse;
pub use image::Spec as ImageSpec;
pub use local_provider::LocalProvider;
pub use skills::{SkillRegistry, Skill};
pub use tools::{ToolRegistry, ToolResult};
pub use verification::{Verification, Verdict};
