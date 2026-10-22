#!/usr/bin/env python3
"""Measure Home's glass at a verified probe, and fail if it is not glass.

WHY THIS EXISTS
---------------
Every round of this page ended with "verified" and then a real render found new
defects, because verification was a human reading a screenshot. This measures
the scale-invariant, local relationships that define the glass.

WHY THE PREVIOUS VERSION WAS REPLACED
-------------------------------------
It gated two DIFFERENCES and nothing else, so any frame with no card under the
probe made both samples identical, both differences became 0.00, and 0.00 sat
inside both bands. It was proven to exit 0 on a flat page-colour frame, on the
real render with the Suggestions card painted out, and on pure black and pure
white frames — a blocking gate in two workflows that could not fail on the
entire class of defect it existed to catch. Three new checks close that:

  A1 presence floor  the bare page beside the card must actually be the cream
                     page (a difference metric is blind to a uniform buffer)
  A2 tint band       the card must be measurably DARKER than the page, so a
                     missing card (delta 0.00) can no longer pass
  A3 probe purity    the interior rect's row-median spread must be small, i.e.
                     the box must be sitting on clean glass rather than
                     straddling a boundary — the check the docstring previously
                     claimed --debug-out provided "rather than assumed"

A4 also corrects the reference probe itself. The old rect overhung the mock
card's bottom edge by 3px and averaged in its bright bottom rim (+11.7 L),
which cancelled the glass tint and produced the bogus -0.43 L baseline that the
bands were said to be "MEASURED, not assumed" from. On clean glass the
reference card is -2.79 L. That single correction reframes everything: Android
measures -2.93 (correct to 0.14 L) while iOS measures -7.28, i.e. iOS's glass is
4.5 L too dark — a real defect the old +3.0 upper bound was hiding.

A5 makes "could not measure" exit 2 as documented. Previously a NaN from an
undersized rim window fell through `if r[key] is not None` (never true for a
float), got compared against the band, and was reported as glass DRIFT with
exit 1 — and a missing file raised an uncaught traceback.

PROBES ARE PER-IMAGE-KIND ON PURPOSE
------------------------------------
`SCREEN` crops the mock to 680x1404 (aspect 0.4843) while a device capture is
393x852 (0.4613) — the crop is 5% too wide — so one normalised rect cannot land
on the same element in both. The reference and render rects are measured
separately. They are also deliberately different in y: the two platforms
currently place the Suggestions card 18pt apart (Android 0.7602, iOS 0.7812), so
the render rect sits in the overlap of the two cards. Tighten it once that
divergence is fixed.

Usage:
  check_home_render.py --render path/to/home.png [--label ios-393]
  check_home_render.py --report-only                 # reference numbers only

Exit: 0 within tolerance, 1 drift, 2 could not measure.
"""

import argparse
import json
import sys

import numpy as np
from PIL import Image, ImageDraw

SCREEN = (172, 56, 852, 1460)          # the phone screen inside the mock

# interior/outside are normalised rects (x, y, w, h); rim_y is the card's top
# edge, from which rim_excess searches a small window in both directions.
PROBES = {
    "reference": {
        "name": "suggestions",
        # A4: measured onto clean glass inside the mock card, which spans
        # rows 1068..1167 of the 1404-row crop (y 0.7607..0.8312).
        "interior": [0.66, 0.7621, 0.26, 0.0328],
        "outside":  [0.004, 0.7621, 0.022, 0.0328],
        "rim_y": 0.7607,
    },
    "render": {
        "name": "suggestions",
        # Inside both platforms' cards despite the 18pt divergence.
        "interior": [0.66, 0.7900, 0.26, 0.0220],
        "outside":  [0.004, 0.7900, 0.022, 0.0220],
        "rim_y": 0.7795,
    },
}

# A1/A2/A3 thresholds, each derived from measured data with the margin stated.
PAGE_LUMA_BAND = (235.0, 250.0)   # real 242.20 / 242.49 | black 0.00, white 255.00
SPREAD_MAX = 5.0                  # real 0.21 / 2.36 | boundary-straddling 7.4..41.8

# A2 is anchored to the reference's own measured tint, NOT to a tolerance that
# happens to admit the current builds. The sheet's Suggestions card sits at
# -2.79 L below its page. Android measures -2.93 (correct to 0.14 L); iOS
# measures -7.07, i.e. 4.3 L too dark. The previous band's +3.0 upper bound
# admitted a zero delta, which is why "no glass at all" and "correct glass"
# both printed OK.
REFERENCE_TINT = -2.79
TINT_TOLERANCE = 1.70
TINT_BAND = (REFERENCE_TINT - TINT_TOLERANCE - 1.0, REFERENCE_TINT + TINT_TOLERANCE)
RIM_BAND = (-1.5, 8.0)


def load_luma(path, crop=None):
    arr = np.asarray(Image.open(path).convert("RGB")).astype(float)
    if crop:
        x0, y0, x1, y1 = crop
        arr = arr[y0:y1, x0:x1]
    return 0.2126 * arr[..., 0] + 0.7152 * arr[..., 1] + 0.0722 * arr[..., 2]


def rect_of(l, r):
    H, W = l.shape
    x, y, w, h = r
    x0, y0 = int(x * W), int(y * H)
    x1, y1 = max(x0 + 1, int((x + w) * W)), max(y0 + 1, int((y + h) * H))
    return l[y0:y1, x0:x1]


def rim_excess(l, probe):
    """How much brighter the card's top hairline is than everything around it.

    Per-column peak then the median across columns: the mock is slightly
    perspective-distorted, so its card edge is not horizontal at pixel
    resolution and averaging columns first smears a 1-2px highlight to nothing.
    """
    H, W = l.shape
    x, _, w, _ = probe["interior"]
    x0, x1 = int((x + 0.02) * W), int((x + w - 0.02) * W)
    top = probe["rim_y"]
    y0, y1 = max(0, int((top - 0.006) * H)), min(H, int((top + 0.018) * H))
    win = l[y0:y1, x0:x1]
    if win.shape[0] < 6:
        return float("nan")
    prof = win.mean(axis=1)
    k = max(2, prof.size // 5)
    surround = max(float(np.median(prof[:k])), float(np.median(prof[-k:])))
    return float(np.median(win.max(axis=0))) - surround


def measure(path, kind, crop=None, debug_out=None):
    l = load_luma(path, crop)
    p = PROBES[kind]
    ins = rect_of(l, p["interior"])
    interior = float(np.median(ins))
    page = float(np.median(rect_of(l, p["outside"])))
    # A3: how flat the interior rect is. A box straddling a card boundary has
    # row medians that jump; a box on clean glass does not.
    spread = float(np.ptp(np.median(ins, axis=1)))
    if debug_out:
        src = Image.open(path).convert("RGB")
        if crop:
            src = src.crop(crop)
        d = ImageDraw.Draw(src)
        H, W = l.shape
        for r, colour in ((p["interior"], "red"), (p["outside"], "lime")):
            x, y, w, h = r
            d.rectangle([x * W, y * H, (x + w) * W, (y + h) * H], outline=colour, width=3)
        x, _, w, _ = p["interior"]
        d.rectangle([int(x * W), int((p["rim_y"] - 0.006) * H),
                     int((x + w) * W), int((p["rim_y"] + 0.018) * H)],
                    outline="blue", width=3)
        src.save(debug_out)
    return {
        "probe": p["name"], "interior": interior, "page_outside": page,
        "interior_minus_page": interior - page, "interior_spread": spread,
        "rim_minus_interior": rim_excess(l, p),
    }


def fmt(v):
    return "    n/a" if v is None or (isinstance(v, float) and np.isnan(v)) else f"{v:8.2f}"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--reference", default="assets/reference/home-reference.png")
    ap.add_argument("--render")
    ap.add_argument("--label", default="render")
    ap.add_argument("--probes", help="JSON overriding the built-in probe sets")
    ap.add_argument("--debug-out", help="annotated copy, to verify probe placement")
    ap.add_argument("--report-only", action="store_true")
    args = ap.parse_args()

    if args.probes:
        with open(args.probes) as fh:
            PROBES.update(json.load(fh))

    try:
        ref = measure(args.reference, "reference", SCREEN)
    except (OSError, ValueError) as exc:
        print(f"::error::could not read the reference sheet: {exc}")
        return 2

    print("=== reference sheet, measured on clean glass (A4-corrected) ===")
    print(f"  {ref['probe']:14s} interior={fmt(ref['interior'])} page={fmt(ref['page_outside'])} "
          f"Δtint={fmt(ref['interior_minus_page'])} spread={fmt(ref['interior_spread'])} "
          f"Δrim={fmt(ref['rim_minus_interior'])}")

    if args.report_only:
        return 0
    if not args.render:
        print("::error::--render is required unless --report-only")
        return 2
    try:
        got = measure(args.render, "render", None, args.debug_out)
    except (OSError, ValueError) as exc:
        # A5: an unreadable capture is "cannot measure", not "glass drifted".
        print(f"::error::could not read {args.render}: {exc}")
        return 2

    print(f"\n=== {args.label}, same metrics ===")
    print(f"  {got['probe']:14s} interior={fmt(got['interior'])} page={fmt(got['page_outside'])} "
          f"Δtint={fmt(got['interior_minus_page'])} spread={fmt(got['interior_spread'])} "
          f"Δrim={fmt(got['rim_minus_interior'])}")

    # A5: a NaN means the instrument could not see the surface at all. It must
    # never be routed into a band comparison, where it reads as a drift.
    nan_keys = [k for k, v in got.items() if isinstance(v, float) and np.isnan(v)]
    if nan_keys:
        print(f"\n::error::could not measure {', '.join(nan_keys)} on {args.render} "
              f"— the probe window is too small or off-frame (not a glass verdict)")
        if args.debug_out:
            print(f"  inspect {args.debug_out} to see where the boxes landed")
        return 2

    checks = [
        ("A1 page present",     got["page_outside"],        PAGE_LUMA_BAND),
        ("A2 glass tint",       got["interior_minus_page"], TINT_BAND),
        ("A3 probe on glass",   got["interior_spread"],      (0.0, SPREAD_MAX)),
        ("   rim highlight",    got["rim_minus_interior"],   RIM_BAND),
    ]
    print("\n--- assertions (presence + tint + purity, all local) ---")
    print(f"  reference tint anchor {REFERENCE_TINT:+.2f} L, tolerance +/-{TINT_TOLERANCE:.2f} L")
    failures = []
    for name, value, (lo, hi) in checks:
        ok = lo <= value <= hi
        if not ok:
            failures.append(f"{name}: {value:.2f} outside [{lo}, {hi}]")
        print(f"  {name:20s} {value:8.2f}  in [{lo},{hi}]  {'OK' if ok else 'FAIL'}")

    if failures:
        print(f"\n::error::Home glass failed {len(failures)} check(s) on {args.label}:")
        for f in failures:
            print(f"  - {f}")
        if args.debug_out:
            print(f"  (if A3 failed, open {args.debug_out}: the red box must sit "
                  f"entirely on blank card glass)")
        return 1
    print("\nOK: a card is present, it darkens the page as the sheet's does, "
          "and the probe is on clean glass")
    return 0


if __name__ == "__main__":
    sys.exit(main())
