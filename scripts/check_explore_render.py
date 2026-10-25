#!/usr/bin/env python3
"""check_explore_render.py — the Explore page's pixel gate.

WHY THIS EXISTS
---------------
The Explore renders were CI-gated on EXISTENCE ONLY: the workflow asserted the
six PNGs were present and >20 KB. That is the structural reason a screen can
"look done" in review while the dock never painted, the pill went glass, or a
rim lost its alpha. This gate measures the two numbers that carry the dock's
material — the capsule's hairline and the pill's peach saturation — from
shared-contracts/explore-geometry.json, whose rects and bands were derived OFF
THE CI RENDERS at c48aa78f (runs 40177639269, 40177187833), not guessed off
the mock. The mock is not rectified (aspect 0.441 vs the device 0.4613), so
it anchors presence only.

WHAT EACH PROBE IS GATED ON
---------------------------
  dock        the brightest row inside a +/-24 px window around rim_y, across
              x 0.12..0.30 — the capsule's 90%-white rim is ground-independent
              (the same law the Home gate's A9 uses). The floor 249.5 was set
              by the doctoring self-test: the Featured card's glow reads 247
              inside the window, so the page level alone is not a safe floor.
  dock-pill   R-B saturation and luma of a window right of the pill's label.
              Plain glass over this page reads 16-18; the peach gradient reads
              79-109. A pill repainted as another glass capsule fails.

Usage:
  check_explore_render.py --render path/to/nexly-explore-393x852.png [--label android]
  check_explore_render.py --self-test          # doctoring cases, needs --render
Exit: 0 within bands, 1 drift, 2 could not measure.
"""

import argparse
import json
import sys

import numpy as np
from PIL import Image

GEOMETRY = "shared-contracts/explore-geometry.json"


def load_rgb(path):
    try:
        im = Image.open(path).convert("RGB")
    except Exception as exc:  # unreadable file is a measurement failure, not a drift
        print(f"ERROR: cannot open {path}: {exc}")
        sys.exit(2)
    return np.asarray(im).astype(int)


def hairline(a, probe):
    """Brightest row-mean within rim_search px of rim_y, over rim_span_x."""
    h, w, _ = a.shape
    gray = a.mean(axis=2)
    x0, x1 = [int(f * w) for f in probe["rim_span_x"]]
    yc = int(probe["rim_y"] * h)
    rad = int(probe["rim_search"])
    lo, hi = max(0, yc - rad), min(h, yc + rad)
    rows = [(gray[y, x0:x1].mean(), y) for y in range(lo, hi)]
    best, by = max(rows)
    # nearest-edge diagnostic: where the rim ACTUALLY is if the window missed
    return best, by, (lo, hi)


def pill_fill(a, probe):
    h, w, _ = a.shape
    x, y, bw, bh = probe["interior"]
    ix0, ix1 = int(x * w), int((x + bw) * w)
    iy0, iy1 = int(y * h), int((y + bh) * h)
    win = a[iy0:iy1, ix0:ix1]
    luma = win.mean(axis=2).mean()
    sat = (win[:, :, 0] - win[:, :, 2]).mean()
    return luma, sat


def measure(path, geo, platform=None):
    a = load_rgb(path)
    rows = []
    probes = geo["probes"]

    def bands_for(probe):
        # Per-platform bands where the two captures legitimately differ (the
        # drawer/home gates do the same); shared "bands" otherwise.
        return probe.get(f"bands_{platform}", probe["bands"]) if platform else probe["bands"]

    hp = probes["dock"]
    hl, hy, (lo, hi) = hairline(a, hp)
    band = bands_for(hp)["hairline"]
    ok = band[0] <= hl <= band[1]
    rows.append(("dock", "hairline", hl, band, ok,
                 f"edge row {hy} (window {lo}..{hi})"))

    pp = probes["dock-pill"]
    luma, sat = pill_fill(a, pp)
    pb = bands_for(pp)
    ok_l = pb["luma"][0] <= luma <= pb["luma"][1]
    ok_s = pb["saturation"][0] <= sat <= pb["saturation"][1]
    rows.append(("dock-pill", "luma", luma, pb["luma"], ok_l, ""))
    rows.append(("dock-pill", "saturation", sat, pb["saturation"], ok_s, ""))

    return rows


def doctor_self_test(path, geo):
    """The gate must be CAPABLE of failing: paint the pill peach out and the
    rim row flat, and both probes must go red on the doctored copy."""
    a = load_rgb(path)
    h, w, _ = a.shape
    doctored = a.copy()
    # 1. pill -> glass: flatten saturation in the pill window
    x, y, bw, bh = geo["probes"]["dock-pill"]["interior"]
    ix0, ix1 = int(x * w), int((x + bw) * w)
    iy0, iy1 = int(y * h), int((y + bh) * h)
    m = doctored[iy0:iy1, ix0:ix1]
    doctored[iy0:iy1, ix0:ix1] = np.repeat(m.mean(axis=2, keepdims=True), 3, axis=2)
    # 2. dock capsule -> no capsule: bury EVERY row the probe's search window
    #    can reach. Painting a narrower band than rim_search means a render
    #    whose rim legitimately drifted 9-24 px would still show a bright row
    #    in the doctored copy, and the self-test would scream "the gate
    #    cannot fail" at a render that is fine — the review caught the first
    #    version painting only +/- 8 while the probe searched +/- 24.
    hp = geo["probes"]["dock"]
    yc = int(hp["rim_y"] * h)
    x0, x1 = [int(f * w) for f in hp["rim_span_x"]]
    rad = int(hp.get("rim_search", 8))
    doctored[max(0, yc - rad):yc + rad, x0:x1] = 236

    rows_clean = measure(path, geo)
    fails_clean = [r for r in rows_clean if not r[4]]
    # measure the doctored array by writing it to a temp file
    import tempfile, os
    with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tf:
        Image.fromarray(doctored.astype(np.uint8)).save(tf.name)
        rows_doc = measure(tf.name, geo)
    os.unlink(tf.name)
    fails_doc = [r for r in rows_doc if not r[4]]

    print(f"clean capture: {len(fails_clean)} failing probe(s)")
    print(f"doctored     : {len(fails_doc)} failing probe(s): "
          + ", ".join(f"{p}/{m}" for p, m, *_ in fails_doc))
    if fails_clean:
        print("SELF-TEST ABORT: the real capture does not pass its own bands")
        return 2
    if not any(p == "dock-pill" for p, *_ in fails_doc) or not any(p == "dock" for p, *_ in fails_doc):
        print("SELF-TEST FAIL: doctoring did not redden both probes — the gate cannot fail")
        return 1
    print("SELF-TEST PASS: both probes are capable of failing, and the capture passes")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--render", required=True)
    ap.add_argument("--geometry", default=GEOMETRY)
    ap.add_argument("--label", default="explore")
    ap.add_argument("--platform", choices=["android", "ios"],
                    help="use the probe's per-platform band override where the "
                         "two captures legitimately differ")
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()

    with open(args.geometry) as fh:
        geo = json.load(fh)

    if args.self_test:
        return doctor_self_test(args.render, geo)

    rows = measure(args.render, geo, args.platform)
    print(f"\n=== {args.label}: {args.render} ===")
    print(f"{'probe':<12}{'metric':<12}{'measured':>10}   {'band':<18} verdict")
    bad = 0
    for probe, metric, value, band, ok, note in rows:
        mark = "OK" if ok else "DRIFT"
        bad += 0 if ok else 1
        extra = f"  ({note})" if note else ""
        print(f"{probe:<12}{metric:<12}{value:>10.2f}   "
              f"[{band[0]:.1f}, {band[1]:.1f}]{mark:>6}{extra}")
    if bad:
        print(f"\n{bad} probe(s) drifted — re-derive rects with the printed edge rows")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
