---
name: model-downloads
description: |-
  Download GGUF and Stable Diffusion model weights from Hugging Face reproducibly, with verified
  URLs, sizes and hashes. TRIGGER — read BEFORE writing a fetch step or a model-download script —
  whenever the task involves: downloading a model, GGUF, `.gguf` file, quantization, Q4_K_M, Q8_0,
  `huggingface-cli` / `hf download`, Hugging Face repos, local LLM weights, Stable Diffusion
  safetensors, model size on disk, or setting up a local/on-device inference fixture for testing.
  Covers direct `curl`, the HF CLI, resumable downloads, disk-space budgeting, and verifying a
  download is not truncated or corrupt.
---

# Model downloads (Hugging Face)

## Purpose

Getting weights onto disk is the first step of every local-inference test and the
most common place for a silent failure: a truncated file, the wrong quantization, or
a repo that has moved. This skill records the exact URLs, the verified byte sizes,
and the checks that distinguish "downloaded" from "downloaded correctly".

## Quick Reference — verified model URLs

All three verified reachable (HTTP 200) with the exact `Content-Length` below.
**`Content-Length` is the expected on-disk size — compare against it.** The Qwen
row below was additionally **downloaded, byte-counted and sha256'd** on a real
machine, so treat that triple as the reference fixture.

**Direct URLs:**

- `https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf`
- `https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf`

| Model | Quant | Exact size | sha256 (verified) |
|---|---|---|---|
| Qwen2.5-0.5B-Instruct | Q4_K_M | 491,400,032 (~469 MiB) | `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db` |
| Llama-3.2-1B-Instruct | Q4_K_M | 807,694,464 (~770 MiB) | not measured — size header-verified only |

**Repository pages:**

- Qwen2.5-0.5B-Instruct-GGUF — https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF
- bartowski/Llama-3.2-1B-Instruct-GGUF — https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF
- unsloth/Llama-3.2-1B-Instruct-GGUF (alternative) — https://huggingface.co/unsloth/Llama-3.2-1B-Instruct-GGUF

**Stable Diffusion:**

- `https://huggingface.co/CompVis/stable-diffusion-v-1-4-original`
- `https://huggingface.co/runwayml/stable-diffusion-v1-5`
- Canonical v1.5 file (from the stable-diffusion.cpp README):
  `https://huggingface.co/stable-diffusion-v1-5/stable-diffusion-v1-5/resolve/main/v1-5-pruned-emaonly.safetensors`

**Why these two GGUFs are a good default pair:** both are sub-1 GB, both are
instruct-tuned, and Q4_K_M is the accuracy/size sweet spot for CPU inference. Qwen
at ~469 MiB is the smallest thing that still follows an instruction format, which
makes it the fastest fixture for testing a decode path. Do not use an 0.5B model as
evidence of *quality* — only as evidence that the *plumbing* works.

## Working Example

### 1. `curl` — simplest, fully inspectable

```bash
mkdir -p models && cd models

# -L follow redirects (required: HF redirects to a CDN)
# -C - resume a partial download instead of restarting
curl -L -C - -o qwen2.5-0.5b-instruct-q4_k_m.gguf \
  "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"
```

`-C -` is the important flag. It resumes from the existing byte offset, so an
interrupted download is cheap to continue rather than restarted. Without it, a
`curl -o` on retry truncates the file and leaves a corrupt model that loads *and
produces garbage* — a genuinely nasty failure mode.

### 2. `hf` CLI — preferred, handles auth and metadata

> **`huggingface-cli` no longer exists.** Verified against `huggingface-hub 2.0.0`:
> the `cli` extra was removed (`warning: The package 'huggingface-hub==2.0.0' does not
> have an extra named 'cli'`) and only the `hf` entrypoint is installed.
> **`--local-dir-use-symlinks` was removed too.** Any command using either will fail.
> If you find a tutorial or an old script using `huggingface-cli download`, it is
> pre-2.0 and needs updating.

```bash
# install (user-space, no sudo)
uv tool install huggingface_hub        # or: pipx install huggingface_hub
hf version                            # 2.0.0
```

```bash
# single file
hf download Qwen/Qwen2.5-0.5B-Instruct-GGUF \
  qwen2.5-0.5b-instruct-q4_k_m.gguf \
  --local-dir /tmp/models

# whole repo (filename argument is optional)
hf download Qwen/Qwen2.5-0.5B-Instruct-GGUF --local-dir ./models

# pin a revision for reproducibility
hf download Qwen/Qwen2.5-0.5B-Instruct-GGUF \
  qwen2.5-0.5b-instruct-q4_k_m.gguf --local-dir models --revision <sha>
```

- `--local-dir` still exists and still places a real file at that path — the symlink
  question is gone, so the "copies vs symlinks" problem from 1.x no longer applies.
- `--revision <branch-or-sha>` is the reproducibility lever. Always pin a **sha**, not
  a branch name, if the artefact must be re-obtainable later.
- Add `--token "$HF_TOKEN"` for gated repos. **Never hardcode a token in a script
  or a committed file** — read it from the environment.
- `hf` prints a hint that the `hf-cli` skill is not installed. It is noise, not an error.
- Pre-2.0 environments (a pinned `huggingface_hub<2`) still have `huggingface-cli`.
  Check with `hf version` before choosing a command.

### 3. Verify — do not skip this

```bash
# size must match Content-Length exactly
stat -c '%s %n' models/*.gguf

# magic bytes: GGUF files start with "GGUF"
head -c 4 models/qwen2.5-0.5b-instruct-q4_k_m.gguf   # expect: GGUF

# sha256 — compare against the LFS oid in the repo's file metadata
sha256sum models/qwen2.5-0.5b-instruct-q4_k_m.gguf
```

The LFS sha256 is published on the repo file page and is obtainable from the API:

```bash
curl -sL "https://huggingface.co/api/models/Qwen/Qwen2.5-0.5B-Instruct-GGUF" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print([f.get('lfs') for f in d['siblings'] if 'q4_k_m' in f['rfilename'].lower()])"
```

**Reading a GGUF header without loading the model** — cheap sanity check that the
metadata (architecture, context length, quant type) is what you expect:

```python
import struct, sys
p = sys.argv[1]
with open(p, "rb") as f:
    magic = f.read(4)
    if magic != b"GGUF":
        raise SystemExit(f"BLOCKED: not a GGUF file (magic={magic!r})")
    ver, n_tensors, n_kv = struct.unpack("<IQQ", f.read(20))
    print(f"GGUF v{ver}  tensors={n_tensors}  kv={n_kv}")
```

### 4. Disk-space budget

| Item | Size |
|---|---|
| Qwen2.5-0.5B Q4_K_M | ~469 MiB |
| Llama-3.2-1B Q4_K_M | ~770 MiB |
| llama.cpp build tree (`/tmp/llama.cpp`, examples ON) | ~1–2 GB |
| HF cache copy (1.x only) | doubles the above |

On `huggingface-hub` **2.x** `--local-dir` writes a real file and the symlink
question is gone, but the blob store may still be populated — check
`du -sh ~/.cache/huggingface` and clear it if space is tight. On **1.x** the
double-occupancy was the classic trap. A llama.cpp build plus two GGUFs is ~2.5 GB
before anything else; on a 15 GB box, downloading several models without a space
check fills the volume and takes the whole toolchain down with it.

## Common Pitfalls

1. **Not resuming with `-C -`.** An interrupted `curl -o` restart produces a
   truncated file. Worse, GGUF may still parse and generate fluent nonsense, so the
   corruption is not obvious. Always `-C -`, and always size-check.
2. **Omitting `-L`.** HF `resolve` URLs redirect to a CDN. Without `-L` you get an
   empty or HTML file and a "success" exit code.
3. **Hardcoding a token.** Use `$HF_TOKEN` from the environment. Committing a token
   is a credential leak regardless of intent.
4. **Double disk usage** from `--local-dir` plus the HF cache. Choose one location.
5. **Assuming repo filename case.** `Qwen2.5-0.5B-Instruct-GGUF` uses
   `qwen2.5-0.5b-instruct-q4_k_m.gguf` — all lowercase — while bartowski's uses
   `Llama-3.2-1B-Instruct-Q4_K_M.gguf` — capitalised. Both are correct for their
   repos. Always list the repo files instead of guessing:
   ```bash
   hf download Qwen/Qwen2.5-0.5B-Instruct-GGUF --include "*.gguf" --dry-run
   ```
6. **Downloading safetensors when the runtime needs GGUF.** A ~4 GB fp16
   safetensors file will not load in llama.cpp. Check the runtime's expectation
   first; conversion is a separate step.
7. **Skipping the `GGUF` magic check.** It is four bytes and catches HTML error pages
   saved as `.gguf`.
8. **Treating a 0.5B model as a quality signal.** It is a plumbing fixture. Report it
   as a decode-path test, never as capability.
9. **Ignoring the model license.** Most weights carry a license with use conditions
   (Llama 3.2 has an explicit acceptable-use policy and a MAU threshold; SD 1.x
   weights have their own CreativeML terms). Check before shipping.
10. **Trusting a resume across a changed remote file.** If the repo's `main` was
    re-uploaded, `-C -` splices two different files. Delete and re-download when the
    remote sha256 changes.
11. **Downloading on a metered or shared connection without a size check first.**
    `curl -I` first, always.

## Debugging

**Establish the expected size before downloading anything** — one request, no
transfer:

```bash
curl -sIL "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf" \
  | grep -iE '^(HTTP/|content-length|x-linked-size)'
```

`x-linked-size` is the LFS object's real size; `content-length` on the final CDN
response should match it. A mismatch between the two means a redirect you did not
expect.

**Is it auth, or is it missing?** A 401/403 means you need a token; a 404 usually
means a wrong filename or a gated repo you have no access to. Distinguish them
before retrying:

```bash
curl -sI -o /dev/null -w '%{http_code}\n' "<url>"    # 200 / 401 / 403 / 404
```

**Did the disk fill mid-download?** curl's exit code is the tell, and the partial
file is the evidence:

```bash
echo "curl exit: $?"          # 28 = timeout, 23 = write error (often ENOSPC)
df -h .
du -sh ~/.cache/huggingface 2>/dev/null
```

**Provenance for a downloaded model.** If the file must be reproducible, record all
three: URL, byte size, sha256. Without the hash a later re-run cannot prove it used
the same weights — and a "same" benchmark on different weights is not a comparison.
Log these alongside any performance number the model produces.

## Source Links

- Qwen2.5-0.5B-Instruct-GGUF:
  https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF
- bartowski/Llama-3.2-1B-Instruct-GGUF:
  https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF
- unsloth/Llama-3.2-1B-Instruct-GGUF (alternative):
  https://huggingface.co/unsloth/Llama-3.2-1B-Instruct-GGUF
- CompVis/stable-diffusion-v-1-4-original:
  https://huggingface.co/CompVis/stable-diffusion-v-1-4-original
- runwayml/stable-diffusion-v1-5:
  https://huggingface.co/runwayml/stable-diffusion-v1-5
- `huggingface_hub` Python API (programmatic equivalent, with resume and
  `local_dir`): https://huggingface.co/docs/huggingface_hub/index
- `hf` / `huggingface-cli` command reference:
  https://huggingface.co/docs/huggingface_hub/guides/cli
- GGUF spec (so the header check above is grounded):
  https://github.com/ggml-org/ggml/blob/master/docs/gguf.md
- HF Hub file listing API (used for the sha256 lookup):
  https://huggingface.co/docs/huggingface_hub/package_reference/hf_api
