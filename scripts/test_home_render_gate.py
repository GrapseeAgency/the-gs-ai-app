#!/usr/bin/env python3
"""Prove the Home pixel gate can fail, on every run, against doctored frames.

WHY THIS EXISTS
---------------
The gate's worst defect was never a wrong band: it was that the band could not see
the defect it existed for. It compared two differences, so a frame with no card on
the probe produced 0.00 for both and passed. That was found by a reviewer building
adversarial frames by hand, not by anything in the repository — and every later
round of "the gate is non-vacuous now" was the same claim made in a commit message
and never checked again.

This runs the claim. It takes the real render the gate just measured, doctors it in
six ways that a plausible regression would produce, and requires each one to fail
with the check that is supposed to catch it — plus the untouched frame to pass. If a
future edit to the gate (or to a band) makes any of these pass, this step goes red
and names which lie got through.

Nothing here renders: it edits the PNG the gate already read, so it costs a second.

Usage: test_home_render_gate.py --render <png> --reference <png> --backdrop <png>
       --probe-set <android|ios>
"""
import argparse
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
GATE = HERE / "check_home_render.py"


def luma(path, crop=None):
    im = Image.open(path).convert("RGB")
    if crop:
        im = im.crop(crop)
    a = np.asarray(im).astype(float)
    return a, 0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2]


def page_colour(L):
    """The page's own cream, sampled from the top-left corner the card never covers."""
    h, w = L.shape
    return float(np.median(L[int(0.02 * h):int(0.05 * h), int(0.002 * w):int(0.02 * w)]))


def probes_for(kind, reference, crop):
    """The gate's own probe rects, loaded by asking it rather than copying them."""
    import importlib.util
    spec = importlib.util.spec_from_file_location("gate_mod", GATE)
    mod = importlib.util.module_from_spec(spec)
    sys.modules["gate_mod"] = mod
    spec.loader.exec_module(mod)
    return mod.PROBES[kind], mod.SCREEN if crop else None


def doctor(src, out, kind, mode):
    a, L = luma(src)
    h, w = L.shape
    probes, _ = probes_for(kind, None, False)
    page = page_colour(L)
    img = Image.open(src).convert("RGB")
    arr = np.asarray(img).copy().astype(np.uint8)
    if mode == "flat":
        arr[:] = int(round(page))
    elif mode == "cards-painted":
        for p in probes:
            x, y, pw, ph = p["interior"]
            arr[int(y * h):int((y + ph) * h), int(x * w):int((x + pw) * w)] = int(round(page))
    elif mode == "rim-erased":
        for p in probes:
            x, _, pw, _ = p["interior"]
            top = p["rim_y"]
            y0, y1 = max(0, int((top - 0.008) * h)), min(h, int((top + 0.020) * h))
            # paint the hairline with the glass that sits under it
            x0, x1 = int((x + 0.02) * w), int((x + pw - 0.02) * w)
            fill = int(round(float(np.median(L[y1:y1 + 6, x0:x1]))))
            arr[y0:y1, x0:x1] = fill
    elif mode == "shifted":
        arr = np.roll(arr, 40 * (w // 393), axis=0)
    else:
        raise SystemExit(f"unknown mode {mode}")
    Image.fromarray(arr).save(out)


def moved_probes(src_kind, offset):
    """A probe file whose rects have slipped the way a layout shift would move them."""
    probes, _ = probes_for(src_kind, None, False)
    out = []
    for p in probes:
        q = json.loads(json.dumps(p))
        x, y, w, h = q["interior"]
        q["interior"] = [x, y + offset, w, h]
        x, y, w, h = q["outside"]
        q["outside"] = [x, y + offset, w, h]
        q["rim_y"] = q["rim_y"] + offset
        if "edge_y" in q:
            q["edge_y"] = q["edge_y"] + offset
        out.append(q)
    return out


def run(args, render=None, backdrop=None, probes=None):
    cmd = [sys.executable, str(GATE), "--reference", args.reference,
           "--render", render or args.render, "--probe-set", args.probe_set,
           "--label", "doctored", "--backdrop", backdrop or args.backdrop]
    if probes:
        cmd += ["--probes", probes]
    p = subprocess.run(cmd, capture_output=True, text=True)
    return p.returncode, p.stdout + p.stderr


CASES = [
    # (name, mode, expected exit or None for "non-zero", pattern that must appear)
    ("untouched frame passes", None, 0, r"OK: \d+ surfaces"),
    ("flat page", "flat", 2, r"A1 page present|no card edge"),
    ("cards painted out", "cards-painted", 1, r"A2 (glass tint|material law)"),
    ("hairline erased", "rim-erased", 1, r"A4 rim highlight|A9 hairline level"),
    ("frame shifted 40 px", "shifted", None, r"A8|A1|A3|no card edge|could not measure"),
]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--render", required=True)
    ap.add_argument("--reference", default="assets/reference/home-reference.png")
    ap.add_argument("--backdrop", default="android/app/src/main/res/drawable-nodpi/gs_home_backdrop.png")
    ap.add_argument("--probe-set", default="android", choices=["android", "ios"])
    args = ap.parse_args()

    failures = []
    with tempfile.TemporaryDirectory() as tmp:
        for name, mode, want, pattern in CASES:
            if mode is None:
                code, out = run(args)
            else:
                path = str(Path(tmp) / f"{mode}.png")
                doctor(args.render, path, args.probe_set, mode)
                code, out = run(args, render=path)
            ok = (code == want if want is not None else code != 0) and re.search(pattern, out)
            print(f"  {'ok  ' if ok else 'FAIL'}  {name:26s} exit {code} "
                  f"(want {'non-zero' if want is None else want})"
                  f"  matched {bool(re.search(pattern, out))}")
            if not ok:
                failures.append(f"{name}: exit {code} (want {want}); "
                                f"pattern {pattern!r} {'matched' if re.search(pattern, out) else 'missing'}")
                print("      " + "\n      ".join(out.strip().split("\n")[-6:]))

        # A probe that has slipped the way a layout shift moves it must be reported
        # as "cannot measure", not as a material verdict.
        for offset, label in ((0.03, "probes slipped 3% down"), (-0.03, "probes slipped 3% up")):
            path = str(Path(tmp) / f"probes{offset}.json")
            Path(path).write_text(json.dumps({args.probe_set: moved_probes(args.probe_set, offset)}))
            code, out = run(args, probes=path)
            ok = code != 0
            print(f"  {'ok  ' if ok else 'FAIL'}  {label:26s} exit {code} (want non-zero)")
            if not ok:
                failures.append(f"{label}: gate still passed (exit {code})")

        # A backdrop that is not the reference's atmosphere must be caught by A7.
        a, L = luma(args.backdrop)
        dim = str(Path(tmp) / "backdrop-dim.png")
        Image.fromarray((a * 0.97).astype(np.uint8)).save(dim)
        code, out = run(args, backdrop=dim)
        ok = code != 0 and ("A7" in out or "margin" in out)
        print(f"  {'ok  ' if ok else 'FAIL'}  {'backdrop dimmed 3%':26s} exit {code} (want non-zero, A7)")
        if not ok:
            failures.append(f"dimmed backdrop: gate passed (exit {code})")

    if failures:
        print(f"\n::error::the pixel gate failed to catch {len(failures)} doctored frame(s) — "
              f"it is certifying nothing:")
        for f in failures:
            print(f"  - {f}")
        return 1
    print("\nThe gate caught every doctored frame and passed the real one.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
