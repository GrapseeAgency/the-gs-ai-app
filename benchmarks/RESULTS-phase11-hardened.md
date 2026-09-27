# Phase 11 re-run on the hardened build

Produced after f24cc3d (checked-in ONNX + llama defaults, fail-closed build). The
run exported NO GS_ONNXRUNTIME_ROOT and NO GS_LLAMA_ROOT: both came from
native/.cargo/config.toml, which is the proof the config works.

## Result: identical to the committed run

Same sample set (`sample_set_sha=6cb6fd9d55a6b221`, matches the committed run),
same model, same flags, `n_errored=0` and `n_skipped=0` on every arm.

| Lever | Score (committed) | Score (hardened) | Delta | CI95 | parse | p50 |
|---|---|---|---|---|---|---|
| baseline | 0.2800 | 0.2800 | +0.0000 | [0.2014, 0.3749] | 0.94 | 208 ms |
| aci | 0.1600 | 0.1600 | +0.0000 | [0.1010, 0.2442] | 0.54 | 1780 ms |
| verification | 0.2800 | 0.2800 | +0.0000 | [0.2014, 0.3749] | 0.99 | 1753 ms |
| context | 0.1000 | 0.1000 | +0.0000 | [0.0552, 0.1744] | 0.27 | 1802 ms |
| router | 0.2800 | 0.2800 | +0.0000 | [0.2014, 0.3749] | 0.94 | 206 ms |

The committed Phase 11 numbers therefore stand. They were never produced with
vision silently off, and they are not revised.

## Why the ONNX flag could not have affected this measurement

`run_bench` imports no vision module. Its use list is gs_bench, gs_common,
gs_core::http_provider, gs_core::local_provider and std. `local_provider.rs`
contains no reference to clip, ocr or vision. CLIP and OCR are linked into the
gs-ffi rlib but are dead code on this path, so the compiled-in state of ONNX
cannot change a number this binary produces.

That is an argument, not a measurement, which is why the run was repeated rather
than accepted. It reproduced to four decimal places on all five arms.

## Parsed-only accuracy, hardened run

| Lever | parse_rate | accuracy (parsed) | p50 | chi2 vs baseline | p |
|---|---|---|---|---|---|
| baseline | 0.94 | 0.2979 | 208 ms | — | — |
| aci | 0.54 | 0.2963 | 1780 ms | 28.085 | 0.000 |
| verification | 0.99 | 0.2828 | 1753 ms | 42.830 | 0.000 |
| context | 0.27 | 0.3704 | 1802 ms | 19.216 | 0.000 |
| router | 0.94 | 0.2979 | 206 ms | 42.830 | 0.000 |

Conclusion unchanged: no arm differs significantly in reasoning quality (every
p > 0.47), and the 0.10 to 0.28 spread in the Score column is a parse-rate effect
from the 256-token budget, not a lever effect. The router control again
reproduced baseline exactly (p=1.000).
