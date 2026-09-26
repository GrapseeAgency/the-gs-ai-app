---
name: stable-diffusion-cpp-build
description: |-
  Build, configure and run `stable-diffusion.cpp` — the ggml-based C/C++ inference library for SD, SDXL,
  SD3/3.5, FLUX, Qwen-Image, Wan, LTX and other image/video diffusion models. TRIGGER — read BEFORE
  writing a build or run command — whenever the task involves: stable-diffusion.cpp, sd-cli, sd.cpp,
  SDXL, FLUX, Qwen-Image, ControlNet, LoRA, IP-Adapter, ADetailer, ESRGAN, TAESD, Latent Consistency
  Model, VAE tiling, GGUF quantization of diffusion weights, SD_VULKAN / SD_OPENCL / SD_METAL /
  SD_CUDA CMake options, or on-device image generation. Covers backend selection, weight formats,
  sampling methods, and the CPU-only build path for machines with no GPU.
---

# stable-diffusion.cpp build

## Purpose

`stable-diffusion.cpp` runs diffusion models in pure C/C++ on the same `ggml`
foundation as llama.cpp — same author ecosystem, same CMake idiom, same
`-DSD_<BACKEND>=ON` option shape. If you know llama.cpp you already know most of
this.

> **The project is under active development and API/CLI options change frequently.**
> (Verbatim warning from the upstream README.) Pin a commit if you need
> reproducibility; do not assume a flag from memory.

### Clone it with submodules — `git clone --depth 1` alone will NOT configure

This is the first thing that bites, and it is build-verified. `stable-diffusion.cpp`
carries **`ggml` as a git submodule**. A plain `git clone --depth 1` leaves `ggml/`
**empty**, and cmake dies with a message that does not obviously point at the cause:

```
CMake Error at cmake/ggml.cmake:19 (message):
  Set SD_GGML_SOURCE_DIR to the source tree matching the selected ggml
  library (ggml-impl.h is required).
```

That reads like a cmake variable problem. It is actually a missing submodule.

```bash
git clone --depth 1 https://github.com/leejet/stable-diffusion.cpp
cd stable-diffusion.cpp
git submodule update --init --depth 1 ggml    # <-- the fix; ggml is the only one required
cmake -B build -DCMAKE_BUILD_TYPE=Release -DGGML_NATIVE=ON
```

There are four submodules (`ggml`, `examples/server/frontend`, `thirdparty/libwebp`,
`thirdparty/libwebm`) but **only `ggml` is required to configure and build `sd-cli`**.
Init just that one and skip the web UI and codec trees.

For a full recursive clone in one step:

```bash
git clone --depth 1 --recurse-submodules https://github.com/leejet/stable-diffusion.cpp
```

## Quick Reference

### Backends — the `-D` options

| Option | Backend | Notes |
|---|---|---|
| *(none)* | **CPU** | Default. AVX / AVX2 / AVX512 auto-detected on x86 |
| `-DSD_CUDA=ON` | NVIDIA | needs CUDA toolkit |
| `-DSD_VULKAN=ON` | Vulkan | broadest GPU reach; **flags the most reliably across vendors** |
| `-DSD_METAL=ON` | Metal | macOS / iOS |
| `-DSD_OPENCL=ON` | OpenCL | legacy path; prefer Vulkan on modern hardware |
| `-DSD_SYCL=ON` | SYCL | Intel GPUs |
| `-DGGML_OPENBLAS=ON` | CPU BLAS | useful on a CPU-only box with a good BLAS |

**Choosing one:** CPU is fine for SD 1.x. Vulkan is the pragmatic GPU default when
you do not know the vendor. Metal on Apple. **If the build is on a machine with no
GPU, enable nothing and accept CPU speed** — a CPU-only run is a valid result, and
reporting it as such is better than enabling a backend that will not initialise.

### Weight formats accepted

`.ckpt` / `.pth` / `.pt` (PyTorch) · `.safetensors` · `.gguf`

Convert mode can convert weights to `.gguf` or `.safetensors`. Quantization and GGUF
details: `docs/quantization_and_gguf.md`.

### Samplers available

`Euler A` · `Euler` · `Heun` · `DPM2` · `DPM++ 2M` · `DPM++ 2M v2` · `DPM++ 2S a`
· `ER-SDE` · `LCM`

Defaults: for SD 1.x use `Euler A`. For SDXL use `DPM++ 2M` + a higher step count.
For LCM models use the `LCM` sampler and few steps, or output is noise.

### Feature flags worth knowing exist

ControlNet (SD 1.5) · LoRA (A1111-compatible) · IP-Adapter (SD 1.5 / SDXL) ·
ADetailer · PhotoMaker · TAESD fast decode · ESRGAN upscale · VAE tiling (lower
memory) · negative prompt · Flash Attention · LCM / LCM-LoRA.

## Working Example

### CPU-only build — the realistic path for a no-GPU machine

```bash
git clone https://github.com/leejet/stable-diffusion.cpp
cd stable-diffusion.cpp

cmake -B build \
  -DCMAKE_BUILD_TYPE=Release \
  -DGGML_NATIVE=ON \
  -DGGML_OPENBLAS=ON
cmake --build build --config Release -j"$(nproc)"
```

`GGML_NATIVE=ON` lets it use the host's exact ISA (AVX2/AVX512). On a 4-core
machine expect **minutes per 512×512 image at 20 steps** for SD 1.x — that is
normal, not a hang. Reduce with `-W 384 -H 384 --steps 12` when you only need a
smoke test.

### GPU build

```bash
cmake -B build -DCMAKE_BUILD_TYPE=Release -DSD_VULKAN=ON
```

For CUDA instead, `-DSD_CUDA=ON` with the CUDA toolkit on `PATH`. For Metal on
macOS, `-DSD_METAL=ON`.

### Get weights, then generate in one command

```bash
# v1.5 from the upstream README's own example
curl -L -O https://huggingface.co/stable-diffusion-v1-5/stable-diffusion-v1-5/resolve/main/v1-5-pruned-emaonly.safetensors

./bin/sd-cli -m ./v1-5-pruned-emaonly.safetensors -p "a lovely cat"
```

### The options you will actually use

```bash
./bin/sd-cli \
  -m ../models/v1-5-pruned-emaonly.safetensors \
  -p "a lovely cat" \
  -n "blurry, low quality, watermark" \        # negative prompt
  -W 512 -H 512 \                                # width / height
  --steps 20 \
  -s "Euler A" \
  --cfg-scale 7.0 \
  -o out.png
```

Full CLI reference: `examples/cli/README.md`.

### Memory reduction on a small box

```bash
# VAE tiling decodes in strips — the single biggest RAM/VRAM saving
./bin/sd-cli -m model.safetensors -p "..." -o out.png --vae-tiling

# Flash Attention
./bin/sd-cli -m model.safetensors -p "..." -o out.png --flash-attn
```

On a 15 GB machine, VAE tiling is usually the difference between running and
`std::bad_alloc`. Reach for it before reducing steps — it costs a little speed and
saves a lot of memory, whereas cutting steps hurts image quality directly.

### Reproducibility

The README defines two RNG modes explicitly:

- `--rng cuda` — **default**; consistent with the `stable-diffusion-webui` GPU RNG
- `--rng cpu` — consistent with the `comfyui` RNG

Pick one and state which. Cross-platform reproducibility is a supported property of
this project, but only if you pin the RNG mode along with the sampler, steps, seed
and model. Same prompt with no seed and no RNG mode is not a reproducible run.

## Common Pitfalls

1. **`git clone --depth 1` without `--recurse-submodules`.** Leaves `ggml/` empty and
   cmake fails with a `ggml-impl.h is required` error that looks like a cmake
   variable problem. Fix: `git submodule update --init --depth 1 ggml`.
2. **Enabling a backend the machine does not have.** `SD_CUDA=ON` on a box with no
   CUDA toolkit fails at configure time; worse, it can configure and fail at runtime.
   A CPU-only build that works beats a GPU build that does not.
3. **Using the wrong sampler for the model family.** LCM models with `Euler A` at
   20 steps produce noise. LCM needs the `LCM` sampler and few steps.
4. **Assuming `--steps` defaults are tuned for the model.** SDXL at Euler A / 20
   steps is undercooked; it wants DPM++ 2M and more.
5. **Running out of memory and blaming the model.** Enable `--vae-tiling` first. Then
   reduce resolution. Then steps. Memory fixes are cheaper than quality fixes.
6. **Reporting a CPU-only run without saying so.** Diffusion timings vary by orders
   of magnitude across backends. An unlabelled number is meaningless, and a CPU
   number presented as a GPU number is worse than no number. Label the backend in
   the artifact.
7. **Downloading fp32 weights when a quantized variant exists.** SD 1.x fp16
   safetensors is ~2 GB; the model you want for a local test is often far smaller.
   See `model-downloads` for verification discipline.
8. **Assuming CLI flags are stable.** The upstream warning is explicit. Re-read
   `./bin/sd-cli --help` rather than trusting a remembered flag — especially
   `--rng`, `--flash-attn` and the sampler names.
9. **Skipping the negative prompt.** `-n` materially changes output quality. Omitting
   it in a comparison makes results non-comparable to any published sample.
10. **Confusing `-DSD_VULKAN=ON` with the Vulkan SDK.** You need a Vulkan ICD
   (driver) present at runtime, not just the build flag.
11. **Building Debug for a speed test.** `CMAKE_BUILD_TYPE=Release` with `GGML_NATIVE=ON`.
    Debug ggml is dramatically slower and the number means nothing.
12. **Expecting llama.cpp's GGUF files to work here.** The quant formats are related
    but the files are not interchangeable. Use a diffusion GGUF for SD.cpp.

## Debugging

**Confirm the backend actually initialised** — the build flag and the runtime
backend are different facts:

```bash
./bin/sd-cli --help          # shows available flags for YOUR build
vulkaninfo --summary         # is a Vulkan ICD visible? (if -DSD_VULKAN=ON)
nvidia-smi                   # is there a CUDA device? (if -DSD_CUDA=ON)
```

If you asked for a GPU backend and got CPU behaviour, the backend silently fell
back. That is exactly the kind of unlabelled measurement that produces a fabricated
looking result — check before you report.

**Watch the log, not just the exit code.** `sd-cli` reports step progress and
per-stage timing. A generation that stalls is usually one of: weights still loading
from a network filesystem, `--flash-attn` unsupported on the chosen backend, or
swap pressure. Check `free -h` and `dmesg | tail` for OOM kills.

**Isolate the build from the model.** A three-step bisect:

1. `cmake` configure only — no build. Proves the toolchain and options.
2. build with no backend flags, `sd-cli --help` only. Proves it links.
3. first real generation at 256×256 / 4 steps. Proves the model loads and decodes.

Only after step 3 passes is a quality or performance question meaningful.

**OOM specifically:** drop resolution first (256×256), then add `--vae-tiling`, then
try fp16 weights instead of fp32, then reduce `--steps`. In that order the quality
loss is smallest.

**Compare against a known reference.** The upstream README's own example is
`./bin/sd-cli -m ../models/v1-5-pruned-emaonly.safetensors -p "a lovely cat"`. If
yours does not produce a plausible image for that exact input, the fault is in your
build — not in your prompt technique.

## Source Links

- stable-diffusion.cpp repository and README (primary — backends, weight formats,
  samplers, feature list, RNG modes, quick start, platforms, Android/Termux note):
  https://github.com/leejet/stable-diffusion.cpp
- Build guide: https://github.com/leejet/stable-diffusion.cpp/blob/master/docs/build.md
- CLI argument reference: https://github.com/leejet/stable-diffusion.cpp/blob/master/examples/cli/README.md
- Backend selection guide (runtime and parameter backend placement — read this
  before choosing which ops go on GPU):
  https://github.com/leejet/stable-diffusion.cpp/blob/master/docs/backend.md
- Performance guide: https://github.com/leejet/stable-diffusion.cpp/blob/master/docs/performance.md
- Troubleshooting: https://github.com/leejet/stable-diffusion.cpp/blob/master/docs/troubleshooting.md
- Quantization and GGUF: https://github.com/leejet/stable-diffusion.cpp/blob/master/docs/quantization_and_gguf.md
- LoRA: https://github.com/leejet/stable-diffusion.cpp/blob/master/docs/lora.md
- LCM / LCM-LoRA: https://github.com/leejet/stable-diffusion.cpp/blob/master/docs/lcm.md
- DeepWiki hardware backend options (every CMake flag, cross-checked):
  https://deepwiki.com/leejet/stable-diffusion.cpp
- Android/Termux discussion: https://github.com/leejet/stable-diffusion.cpp/discussions/205
- Prebuilt binaries: https://github.com/leejet/stable-diffusion.cpp/releases
- ggml (shared foundation with llama.cpp): https://github.com/ggml-org/ggml
