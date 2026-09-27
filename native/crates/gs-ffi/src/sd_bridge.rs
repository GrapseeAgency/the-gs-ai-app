//! Stable-diffusion wrapper bindings, plus the procedural path.
//!
//! The procedural renderer is the one that actually works today and it needs no
//! weights, no GPU and no diffusion. That is deliberate: diagrams, charts and
//! UI mockups should never require a 2.3 GB model.

use std::ffi::{c_char, c_int, CString};

pub const GS_OK: c_int = 0;

extern "C" {
    fn sd_render_svg(spec_json: *const c_char, output_path: *const c_char) -> c_int;
}

/// Render an SVG diagram from a flat JSON spec. No model required.
pub fn render_svg(spec_json: &str, output_path: &str) -> Result<(), i32> {
    let spec = CString::new(spec_json).map_err(|_| -1)?;
    let out = CString::new(output_path).map_err(|_| -1)?;
    let rc = std::panic::catch_unwind(|| unsafe { sd_render_svg(spec.as_ptr(), out.as_ptr()) })
        .map_err(|_| -7)?;
    if rc == GS_OK {
        Ok(())
    } else {
        Err(rc)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn the_procedural_path_renders_without_any_model() {
        // The claim worth testing: diagrams need no weights at all.
        let spec = r#"{"title":"Architecture","width":"800","height":"400",
                        "layer1":"orchestration","layer2":"inference"}"#;
        let out = std::env::temp_dir().join("gs-test-diagram.svg");
        let p = out.to_string_lossy().into_owned();
        match render_svg(spec, &p) {
            Ok(()) => {
                let mut f = std::fs::File::open(&out).expect("svg written");
                let mut s = String::new();
                use std::io::Read;
                f.read_to_string(&mut s).unwrap();
                assert!(s.starts_with("<svg"), "must be an svg root");
                assert!(s.contains("Architecture"), "title must appear");
                assert!(s.contains("orchestration"), "field must be rendered");
                assert!(s.trim_end().ends_with("</svg>"), "must be closed");
                let _ = std::fs::remove_file(&out);
            }
            Err(e) => panic!("procedural render failed: {e}"),
        }
    }

    #[test]
    fn an_unwritable_path_is_an_error_not_a_panic() {
        let r = render_svg(r#"{"title":"x"}"#, "/nonexistent-dir-xyz/out.svg");
        assert!(r.is_err());
    }
}

// ---------------------------------------------------------------------------
// Diffusion path
// ---------------------------------------------------------------------------

/// Where stable-diffusion.cpp lives and what to generate.
///
/// GPU-only by construction. `backend` is passed straight through to
/// stable-diffusion.cpp's `--backend` flag, and `require_gpu` makes the call
/// fail if that flag is not honoured, so a run cannot quietly fall back to the
/// host: on a machine without a working Vulkan device that is a failure the
/// operator should see, not a 30-minute CPU grind that looks like success.
#[derive(Debug, Clone)]
pub struct DiffuseRequest {
    pub prompt: String,
    pub negative: String,
    pub steps: u32,
    pub width: u32,
    pub height: u32,
    pub seed: i64,
    pub threads: u32,
    pub backend: String,
    pub require_gpu: bool,
    pub timeout_secs: u64,
}

impl Default for DiffuseRequest {
    fn default() -> Self {
        Self {
            prompt: String::new(),
            negative: String::new(),
            steps: 20,
            width: 512,
            height: 512,
            seed: -1,
            threads: 4,
            // Pinned to the GPU. This is not a default to be overridden
            // casually: it is the only backend that was verified to work.
            backend: "vulkan0".into(),
            require_gpu: true,
            timeout_secs: 900,
        }
    }
}

/// A generated image and where it landed.
#[derive(Debug, Clone)]
pub struct Diffused {
    pub path: std::path::PathBuf,
    pub bytes: u64,
    pub wall_ms: u64,
    pub log: String,
}

fn sd_cli_path() -> Result<std::path::PathBuf, String> {
    if let Ok(p) = std::env::var("GS_SD_CLI") {
        return Ok(p.into());
    }
    for cand in [
        "/mnt/new_volume/sd/stable-diffusion.cpp/build/bin/sd-cli",
        "sd/stable-diffusion.cpp/build/bin/sd-cli",
    ] {
        if std::path::Path::new(cand).exists() {
            return Ok(cand.into());
        }
    }
    Err(format!(
        "stable-diffusion.cpp binary not found. Set GS_SD_CLI=/path/to/sd-cli \
         (looked in /mnt/new_volume/sd/stable-diffusion.cpp/build/bin/sd-cli)"
    ))
}

fn sd_model_path() -> Result<std::path::PathBuf, String> {
    let p = std::env::var("GS_SD_MODEL").unwrap_or_else(|_| {
        "/mnt/new_volume/models/sd/sd-v1-5.safetensors".to_string()
    });
    let pb = std::path::PathBuf::from(&p);
    if !pb.exists() {
        return Err(format!("no diffusion model at {p}"));
    }
    Ok(pb)
}

/// Generate one image with stable-diffusion.cpp.
pub fn diffuse(req: &DiffuseRequest) -> Result<Diffused, String> {
    if req.prompt.trim().is_empty() {
        return Err("prompt must not be empty".into());
    }
    if req.width == 0 || req.height == 0 || req.steps == 0 {
        return Err("width, height and steps must all be > 0".into());
    }
    let bin = sd_cli_path()?;
    let model = sd_model_path()?;

    // Timestamp in the name, as specified, so successive generations never
    // overwrite each other.
    let stamp = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis())
        .unwrap_or(0);
    let outdir = std::env::var("GS_IMAGE_DIR")
        .map(std::path::PathBuf::from)
        .unwrap_or_else(|_| std::env::temp_dir());
    std::fs::create_dir_all(&outdir)
        .map_err(|e| format!("cannot create {}: {e}", outdir.display()))?;
    let out = outdir.join(format!("gs_image_{stamp}.png"));

    let mut cmd = std::process::Command::new(&bin);
    if !req.backend.is_empty() {
        cmd.arg("--backend").arg(&req.backend);
    }
    cmd.arg("-m").arg(&model)
        .arg("-p").arg(&req.prompt.trim())
        .arg("--steps").arg(req.steps.to_string())
        .arg("-W").arg(req.width.to_string())
        .arg("-H").arg(req.height.to_string())
        .arg("-o").arg(&out)
        .arg("-t").arg(req.threads.to_string());
    // No --sampler flag: this build has no such option and passing one makes
    // the binary print its help and exit 1. The scheduler is selected through
    // --extra-sample-args when it needs to be; the default is Euler A, which is
    // what the verified run used.
    if !req.negative.trim().is_empty() {
        // -n is the documented short form of --negative-prompt.
        cmd.arg("-n").arg(req.negative.trim());
    }
    if req.seed >= 0 {
        cmd.arg("-s").arg(req.seed.to_string());
    }

    let started = std::time::Instant::now();
    // Output goes to a log file, not a pipe. A piped child that fills its
    // 64 KB buffer blocks forever while the parent waits for exit, and a
    // diffusion run prints well over that; reading the file afterwards cannot
    // deadlock.
    let log_path = outdir.join(format!("gs_image_{stamp}.log"));
    let log_file = std::fs::File::create(&log_path)
        .map_err(|e| format!("cannot create {}: {e}", log_path.display()))?;
    let err_file = log_file
        .try_clone()
        .map_err(|e| format!("cannot duplicate the log handle: {e}"))?;
    cmd.stdout(std::process::Stdio::from(log_file))
        .stderr(std::process::Stdio::from(err_file));

    let child = cmd
        .spawn()
        .map_err(|e| format!("cannot spawn {}: {e}", bin.display()))?;
    let status = wait_with_timeout(child, req.timeout_secs)
        .map_err(|e| format!("stable-diffusion.cpp timed out after {}s: {e}", req.timeout_secs))?;
    let wall_ms = started.elapsed().as_millis() as u64;
    let log = std::fs::read_to_string(&log_path).unwrap_or_default();

    if !status.success() {
        return Err(format!("stable-diffusion.cpp exited with {status}\n{}", tail(&log, 20)));
    }
    if !out.exists() {
        return Err(format!("stable-diffusion.cpp reported success but {} is absent", out.display()));
    }

    // GPU enforcement: the binary reports where the weights actually live.
    // "RAM 0.00MB" is the line that proves the run was not on the host.
    if req.require_gpu {
        let mem_line = log
            .lines()
            .filter(|l| l.contains("total params memory size"))
            .last()
            .unwrap_or("")
            .to_string();
        // "RAM" must be matched as a standalone token. Splitting on the
        // substring "RAM" matches inside "VRAM", so a line reading
        //   VRAM 2784.45MB, RAM 0.00MB
        // was parsed as 2784.45 MB of host memory and a correct GPU run was
        // rejected. rfind plus a guard against a preceding 'V' is the fix.
        let on_host = host_megabytes(&mem_line).map(|mb| mb > 1.0).unwrap_or(false);
        if on_host {
            return Err(format!(
                "diffusion ran on the CPU despite --backend {}; refusing to report it as a GPU run.\n{}",
                req.backend,
                &mem_line
            ));
        }
        if mem_line.is_empty() {
            return Err(format!(
                "stable-diffusion.cpp did not report where the weights were placed, so the \
                 run cannot be verified as GPU-only.\n{}",
                tail(&log, 20)
            ));
        }
        if !log.contains("vulkan") && !log.contains("Vulkan") && !log.contains("CUDA") {
            return Err(format!(
                "no accelerator device appeared in the stable-diffusion.cpp output; \
                 it may have run entirely on the CPU. Re-run with a valid --backend.\n{}",
                tail(&log, 20)
            ));
        }
    }

    let bytes = std::fs::metadata(&out).map(|m| m.len()).unwrap_or(0);
    Ok(Diffused { path: out, bytes, wall_ms, log })
}

/// The "RAM <n>MB" figure from a memory-placement report.
///
/// Deliberately hand-parsed. The substring "RAM" also occurs inside "VRAM", and
/// a naive split reads the VRAM figure as host memory -- which is how a run
/// that was entirely on the GPU gets rejected as a CPU run.
fn host_megabytes(line: &str) -> Option<f64> {
    let mut from = 0usize;
    while let Some(at) = line[from..].find("RAM") {
        let abs = from + at;
        let before = line[..abs].chars().next_back();
        let rest = &line[abs + 3..];
        if before != Some('V') {
            let digits: String = rest
                .trim_start()
                .chars()
                .take_while(|c| c.is_ascii_digit() || *c == '.')
                .collect();
            if let Ok(v) = digits.parse::<f64>() {
                return Some(v);
            }
        }
        from = abs + 3;
    }
    None
}

/// Wait for a child, killing it if it overruns `secs`. Returns the exit status.
fn wait_with_timeout(
    mut child: std::process::Child,
    secs: u64,
) -> Result<std::process::ExitStatus, String> {
    let deadline = std::time::Instant::now() + std::time::Duration::from_secs(secs);
    loop {
        match child.try_wait() {
            Ok(Some(st)) => return Ok(st),
            Ok(None) => {}
            Err(e) => return Err(e.to_string()),
        }
        if std::time::Instant::now() > deadline {
            let _ = child.kill();
            let _ = child.wait();
            return Err(format!("no exit status after {secs}s"));
        }
        std::thread::sleep(std::time::Duration::from_millis(100));
    }
}

/// Last `n` lines, for an error message that is readable rather than a wall.
fn tail(s: &str, n: usize) -> String {
    let lines: Vec<&str> = s.lines().collect();
    lines[lines.len().saturating_sub(n)..].join("\n")
}

#[cfg(test)]
mod diffusion_tests {
    use super::*;

    #[test]
    fn an_empty_prompt_is_refused_before_anything_is_spawned() {
        let mut r = DiffuseRequest::default();
        r.prompt = "   ".into();
        assert!(diffuse(&r).unwrap_err().contains("prompt must not be empty"));
    }

    #[test]
    fn zero_dimensions_are_refused() {
        let mut r = DiffuseRequest::default();
        r.prompt = "a cat".into();
        r.steps = 0;
        assert!(diffuse(&r).is_err());
    }

    #[test]
    fn a_missing_model_names_the_path_it_looked_for() {
        std::env::set_var("GS_SD_MODEL", "/nonexistent/model.safetensors");
        let mut r = DiffuseRequest::default();
        r.prompt = "a cat".into();
        let e = diffuse(&r).unwrap_err();
        assert!(e.contains("/nonexistent/model.safetensors"), "{e}");
        std::env::remove_var("GS_SD_MODEL");
    }

    #[test]
    fn the_gpu_report_is_parsed_without_confusing_vram_for_ram() {
        // Regression: "RAM" occurs inside "VRAM". Reading the wrong one
        // rejected a correct GPU-only run as a CPU run.
        let line = "[INFO] total params memory size = 2784.45MB (VRAM 2784.45MB, RAM 0.00MB): text_encoders 469.44MB(VRAM)";
        assert_eq!(host_megabytes(line), Some(0.0), "a 0.00MB host figure must not read as 2784.45");

        let cpu = "[INFO] total params memory size = 2784.45MB (VRAM 0.00MB, RAM 2784.45MB)";
        assert_eq!(host_megabytes(cpu), Some(2784.45));

        assert_eq!(host_megabytes("no numbers here"), None);
    }

    #[test]
    fn the_request_defaults_to_the_gpu() {
        let r = DiffuseRequest::default();
        assert_eq!(r.backend, "vulkan0");
        assert!(r.require_gpu, "a silent CPU fallback must be impossible");
    }
}
