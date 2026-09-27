//! Tool output offload: keep the context small when a tool returns a wall of text.
//!
//! The rule is simple and the reason is arithmetic. A tool that returns 50,000
//! characters costs roughly 12,500 tokens on every subsequent turn, forever,
//! even though the model almost never needs all of it. Writing the body to a
//! scratch file and leaving a preview plus a path in the context turns that
//! fixed cost into a fixed ~150 tokens, and the model can ask for the rest.
//!
//! What is deliberately NOT done: silently truncating. A truncated tool result
//! that still looks complete is the worst outcome available, because the model
//! cannot tell it was cut. The replacement text says how much was moved and
//! where it went.

use std::path::{Path, PathBuf};

/// Above this, the body goes to disk. 8000 chars is ~2000 tokens, which is
/// already more than most tool results need in context.
pub const OFFLOAD_THRESHOLD_CHARS: usize = 8_000;

/// How much of the body stays in context.
pub const PREVIEW_CHARS: usize = 500;

/// Where offloaded bodies land.
pub fn scratch_dir() -> PathBuf {
    std::env::var("GS_SCRATCH_DIR")
        .map(PathBuf::from)
        .unwrap_or_else(|_| std::env::temp_dir().join("gs_scratch"))
}

/// The result of deciding whether to offload.
#[derive(Debug, Clone)]
pub struct Offloaded {
    /// What goes into the context. Identical to the input when nothing moved.
    pub context_text: String,
    /// Where the full body went, if it moved.
    pub path: Option<PathBuf>,
    pub original_chars: usize,
    pub offloaded: bool,
}

/// Monotonic, collision-free within a process; combined with the pid it is
/// unique on the machine without pulling in a uuid dependency.
fn unique_id() -> String {
    let nanos = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_nanos())
        .unwrap_or(0);
    format!("{}-{}", std::process::id(), nanos)
}

/// Offload `body` to a scratch file when it exceeds the threshold.
///
/// Returns the text that should replace it in the context, plus where the full
/// body is, so the caller can report both.
pub fn offload(label: &str, body: &str) -> Offloaded {
    let original_chars = body.chars().count();
    if original_chars <= OFFLOAD_THRESHOLD_CHARS {
        return Offloaded {
            context_text: body.to_string(),
            path: None,
            original_chars,
            offloaded: false,
        };
    }

    let dir = scratch_dir();
    if let Err(e) = std::fs::create_dir_all(&dir) {
        // Cannot offload, so keep the body. Dropping it would lose data, and
        // reporting a smaller context that silently lost content is worse than
        // an expensive one.
        return Offloaded {
            context_text: format!(
                "{body}\n[OFFLOAD FAILED: cannot create {}: {e}. Full text retained above.]",
                dir.display()
            ),
            path: None,
            original_chars,
            offloaded: false,
        };
    }

    let safe: String = label
        .chars()
        .map(|c| if c.is_ascii_alphanumeric() || c == '_' || c == '-' { c } else { '_' })
        .collect();
    let path = dir.join(format!("{}-{}.txt", if safe.is_empty() { "tool".into() } else { safe }, unique_id()));
    if let Err(e) = std::fs::write(&path, body.as_bytes()) {
        return Offloaded {
            context_text: format!(
                "{body}\n[OFFLOAD FAILED: cannot write {}: {e}. Full text retained above.]",
                path.display()
            ),
            path: None,
            original_chars,
            offloaded: false,
        };
    }

    let preview: String = body.chars().take(PREVIEW_CHARS).collect();
    let chars_saved = original_chars - PREVIEW_CHARS;
    let pct = if original_chars > 0 {
        (chars_saved as f64 / original_chars as f64) * 100.0
    } else {
        0.0
    };

    Offloaded {
        context_text: format!(
            "[LARGE OUTPUT - {original_chars} chars from `{label}`]\n\
             {preview}\n\
             [... {chars_saved} more chars moved out of context, {pct:.1}% saved]\n\
             [Full output at: {}]\n\
             Read with: read_scratch_file {}",
            path.display(),
            path.display()
        ),
        path: Some(path),
        original_chars,
        offloaded: true,
    }
}

/// Read a scratch file back. Bounded: a 50 MB log must not become 12.5M tokens
/// of context in one call, so the caller gets a cap and is told it applied.
pub fn read_scratch_file(path: &Path, max_chars: usize) -> Result<(String, bool), String> {
    let body = std::fs::read_to_string(path).map_err(|e| format!("cannot read {}: {e}", path.display()))?;
    let total = body.chars().count();
    if total <= max_chars {
        return Ok((body, false));
    }
    Ok((body.chars().take(max_chars).collect(), true))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_small_result_is_left_alone() {
        let o = offload("search", "short answer");
        assert!(!o.offloaded);
        assert_eq!(o.context_text, "short answer");
        assert!(o.path.is_none());
    }

    #[test]
    fn a_large_result_is_moved_and_the_preview_is_exact() {
        let body: String = std::iter::repeat("abcdefghij").take(6000).collect(); // 60000 chars
        let o = offload("log_dump", &body);
        assert!(o.offloaded, "60000 chars must offload");
        assert_eq!(o.original_chars, 60000);
        let p = o.path.expect("a path");
        // The full body really is on disk, byte for byte.
        assert_eq!(std::fs::read_to_string(&p).unwrap(), body);
        // The preview is the first 500 chars, not a lossy summary.
        let expected = body.chars().take(PREVIEW_CHARS).collect::<String>();
        assert!(o.context_text.contains(&expected));
        assert!(o.context_text.contains(&p.display().to_string()));
    }

    #[test]
    fn the_replacement_states_the_size_and_the_path() {
        let body: String = std::iter::repeat("x").take(20_000).collect();
        let o = offload("tool", &body);
        assert!(o.context_text.contains("[LARGE OUTPUT"));
        assert!(o.context_text.contains("20000 chars"));
        assert!(o.context_text.contains("read_scratch_file"));
        // It must not merely truncate: the reader has to be able to tell.
        assert!(!o.context_text.contains(&body));
    }

    #[test]
    fn reading_back_is_bounded_and_says_so() {
        let body: String = std::iter::repeat("y").take(5_000).collect();
        let p = std::env::temp_dir().join(format!("gs_scratch_test_{}.txt", std::process::id()));
        std::fs::write(&p, &body).unwrap();
        let (full, truncated) = read_scratch_file(&p, 100_000).unwrap();
        assert!(!truncated);
        assert_eq!(full.chars().count(), 5_000);
        let (part, truncated) = read_scratch_file(&p, 1_000).unwrap();
        assert!(truncated, "a bounded read must report that it truncated");
        assert_eq!(part.chars().count(), 1_000);
        std::fs::remove_file(&p).ok();
    }

    #[test]
    fn a_missing_file_is_an_error_not_an_empty_string() {
        assert!(read_scratch_file(Path::new("/nonexistent/gs_scratch/x.txt"), 100).is_err());
    }
}
