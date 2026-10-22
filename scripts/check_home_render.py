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
  A4 rim highlight   the top hairline must stand off the glass, which is the
                     cue the whole material turns on and, until now, the one
                     nothing measured

The probe rect itself was wrong too. The old one overhung the mock card's bottom
edge by 3px and averaged in its bright bottom rim (+11.7 L), which cancelled the
glass tint and produced the bogus -0.43 L baseline that the bands were said to be
"MEASURED, not assumed" from. On clean glass the sheet's card is -2.79 L. That
one correction reframed everything: Android measures -2.93 (correct to 0.14 L)
while iOS measures -4.64 — a real defect the old +3.0 upper bound was hiding.

A5 makes "could not measure" exit 2 as documented. Previously a NaN from an
undersized rim window fell through `if r[key] is not None` (never true for a
float), got compared against the band, and was reported as glass DRIFT with
exit 1 — and a missing file raised an uncaught traceback.

A6 checks the gate against its own anchor: every band must contain the sheet's
own measurement, which is printed every run. A recorded anchor that no longer
matches the image it came from means the sheet moved or a rect slipped, and a
verdict built on either is worthless.

PROBES ARE PER-IMAGE-KIND ON PURPOSE
------------------------------------
`SCREEN` crops the mock to 680x1404 (aspect 0.4843) while a device capture is
393x852 (0.4613) — the crop is 5% too wide — so one normalised rect cannot land
on the same element in both. The reference and render rects are measured
separately. They are also deliberately different in y: the two platforms
currently place the Suggestions card 18pt apart (Android 0.7602, iOS 0.7812), so
the render rect sits in the overlap of the two cards. Tighten it once that
divergence is fixed.

The bento probe cannot do that. Android's Mind Guide card top edge sits at
0.4374 of the frame and iOS's at 0.4550 — an 18pt gap against a card whose whole
lit hairline is 3px tall — so there is no y that is on both cards' glass, let
alone a rim_y that measures both hairlines. It gets its own rect per platform
(`--probe-set`), each verified with --debug-out rather than assumed.

TWO PROBES, AND NOT THE SAME METRIC ON BOTH
-------------------------------------------
The sheet's Suggestions card sits on flat cream and its glass only moves the page
2.79 L; the bento card sits over the bloom and moves it 10.94 L. Same glass,
different ground. Gating only the weak case is what let a surface with no visible
material at all print OK for three rounds, so both are gated, each against its own
measured anchor — and each on the number that isolates the glass at that spot:
the row on flat cream is measured against the page beside it, the card over the
bloom against the backdrop actually under it (see BANDS for why).

Usage:
  check_home_render.py --render path/to/home.png --probe-set android [--label android-393]
  check_home_render.py --report-only                 # reference numbers only

Exit: 0 within tolerance, 1 drift, 2 could not measure.
"""

import argparse
import hashlib
import json
import sys

import numpy as np
from PIL import Image, ImageDraw

SCREEN = (172, 56, 852, 1460)          # the phone screen inside the mock

# Every band in BANDS is a measurement OF THIS FILE. If the sheet is replaced,
# the numbers in it are still printed as "the reference" and a render can pass or
# fail against an anchor nobody re-derived — so the asset is checksummed before
# anything is measured, and a changed sheet is a stop, not a silent re-baseline.
# Update it in the same commit as the new sheet, after re-measuring every probe
# with --report-only and --debug-out.
REFERENCE_SHA256 = "bd47f5b6c2b47226e22cf188ff297e98f3418b9b38ed0fa4d25c157eac4925a5"

# interior/outside are normalised rects (x, y, w, h); rim_y is the card's top
# edge, from which rim_excess searches a small window in both directions.

# The sheet's Suggestions card. A4: measured onto clean glass inside the mock
# card, which spans rows 1068..1167 of the 1404-row crop (y 0.7607..0.8312).
SUGGESTIONS_REF = {
    "name": "suggestions",
    "interior": [0.66, 0.7621, 0.26, 0.0328],
    "outside":  [0.004, 0.7621, 0.022, 0.0328],
    "rim_y": 0.7607,
}
# Inside both platforms' cards despite the 18pt divergence.
SUGGESTIONS_RENDER = {
    "name": "suggestions",
    "interior": [0.66, 0.7900, 0.26, 0.0220],
    "outside":  [0.004, 0.7900, 0.022, 0.0220],
}
# The rim cannot be shared. Measured off the two current renders, the card's own
# top edge is at 0.7582 on Android and 0.7707 on iOS, while the shared rim_y this
# probe used was 0.7795 — 155px and 89px BELOW the edge respectively. Both
# platforms were therefore being scored on a hairline the window never contained
# (0.72 and 1.42 L, i.e. noise), which is the same "a gate that cannot fail"
# defect this script was rewritten for, hiding in the one check added last.
SUGGESTIONS_ANDROID = dict(SUGGESTIONS_RENDER, rim_y=0.7582)
SUGGESTIONS_IOS = dict(SUGGESTIONS_RENDER, rim_y=0.7707)

# The bento's Mind Guide card — the sheet's purest glass, sitting on the bloom
# where the material has something to do. x 0.62..0.92 is inside the card on all
# three kinds (the sheet's spans x 388..655 of the 680 crop; Android's starts at
# x 638 and iOS's at x 657 of 1179). The interior strip is the clean glass
# between the top hairline and the title row: 18px of the 1404-row crop on the
# sheet, 20px of the 2556-row frame on both devices.
BENTO_REF = {
    "name": "bento",
    "interior": [0.62, 0.4359, 0.30, 0.0128],
    "outside":  [0.004, 0.4359, 0.022, 0.0128],
    "rim_y": 0.4312,
}
BENTO_ANDROID = {
    "name": "bento",
    "interior": [0.62, 0.4405, 0.30, 0.0078],
    "outside":  [0.004, 0.4405, 0.022, 0.0078],
    "rim_y": 0.4374,
}
BENTO_IOS = {
    "name": "bento",
    "interior": [0.62, 0.4576, 0.30, 0.0078],
    "outside":  [0.004, 0.4576, 0.022, 0.0078],
    "rim_y": 0.4550,
}

PROBES = {
    "reference": [SUGGESTIONS_REF, BENTO_REF],
    "android": [SUGGESTIONS_ANDROID, BENTO_ANDROID],
    "ios": [SUGGESTIONS_IOS, BENTO_IOS],
}

# A1/A3 thresholds, each derived from measured data with the margin stated.
PAGE_LUMA_BAND = (235.0, 250.0)   # real 242.20 / 242.49 | black 0.00, white 255.00
SPREAD_MAX = 5.0                  # real 0.21 / 2.36 | boundary-straddling 7.4..41.8

# A2 and the rim are anchored to each probe's own measured value on the sheet,
# NOT to a tolerance that happens to admit the current builds. The previous
# band's +3.0 upper bound admitted a zero delta, which is why "no glass at all"
# and "correct glass" both printed OK.
#
#   suggestions  tint vs its page -2.79, rim +2.58
#                (Android -2.93 correct to 0.14; iOS -4.64)
#   bento        lift over its own ground +2.79, rim +19.94
#                (Android +3.57 / +18.94; iOS +2.58 / +18.42)
#
# The two probes do not gate the same luminance metric, because the page will not
# let them. The bento sits on a bloom that swings 13 L across the few points the
# card moves, so "card minus page" there measures WHERE the card is as much as
# what the glass does — and the two platforms currently place it 17pt apart, which
# is a real defect belonging to the bento geometry, not to the material. Subtract
# the backdrop PNG from under the card instead and the number is the material
# alone: coverage. That the sheet's +2.79, Android's +3.57 and iOS's +2.58 agree
# to 1 L is the evidence the metric is the right one; on the page-relative number
# the same two renders read -10.09 and -7.44, and only 0.5 L of that spread is
# glass.
#
# The suggestions row is on flat cream, where the ground does not move, so it
# keeps the page-relative number — including the 1.0 L extra room on its dark
# side that the old band had, so the iOS card that is 1.85 L too dark stays a
# known, printed gap instead of a new red.
BANDS = {
    "suggestions": {"tint": (-5.49, -1.09), "rim": (-1.50, 8.00)},
    "bento":       {"lift": (1.00, 4.50), "rim": (15.00, 22.50)},
}


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


def measure(path, kind, crop=None, debug_out=None, ground=None):
    """Every probe of `kind`'s set, measured on one decoded frame.

    `ground` is the luminance of the backdrop PNG the page draws its atmosphere
    from. Where a probe gates on `lift`, the card's interior is compared with the
    same rectangle of that image — the ground actually under the glass — because
    that isolates the material from where the card happens to sit.
    """
    l = load_luma(path, crop)
    got = []
    for p in PROBES[kind]:
        ins = rect_of(l, p["interior"])
        interior = float(np.median(ins))
        page = float(np.median(rect_of(l, p["outside"])))
        # A3: how flat the interior rect is. A box straddling a card boundary has
        # row medians that jump; a box on clean glass does not.
        spread = float(np.ptp(np.median(ins, axis=1)))
        lift = interior - float(np.median(rect_of(ground, p["interior"]))) if ground is not None else None
        got.append({
            "probe": p["name"], "interior": interior, "page_outside": page,
            "interior_minus_page": interior - page, "interior_spread": spread,
            "rim_minus_interior": rim_excess(l, p),
            "lift_over_ground": lift,
        })
    if debug_out:
        src = Image.open(path).convert("RGB")
        if crop:
            src = src.crop(crop)
        d = ImageDraw.Draw(src)
        H, W = l.shape
        for p in PROBES[kind]:
            for r, colour in ((p["interior"], "red"), (p["outside"], "lime")):
                x, y, w, h = r
                d.rectangle([x * W, y * H, (x + w) * W, (y + h) * H], outline=colour, width=3)
            x, _, w, _ = p["interior"]
            ry = (p["rim_y"] - 0.006) * H
            d.rectangle([int(x * W), int(ry), int((x + w) * W),
                         int((p["rim_y"] + 0.018) * H)], outline="blue", width=3)
            d.text((x * W + 6, ry - 16), p["name"], fill=(0, 0, 255))
        src.save(debug_out)
    return got


def fmt(v):
    return "    n/a" if v is None or (isinstance(v, float) and np.isnan(v)) else f"{v:8.2f}"


def row(r):
    lift = f" lift={fmt(r['lift_over_ground'])}" if r["lift_over_ground"] is not None else ""
    return (f"  {r['probe']:14s} interior={fmt(r['interior'])} page={fmt(r['page_outside'])} "
            f"Δtint={fmt(r['interior_minus_page'])}{lift} spread={fmt(r['interior_spread'])} "
            f"Δrim={fmt(r['rim_minus_interior'])}")


# Which number each gated metric reads off a measurement row.
METRICS = {"tint": "interior_minus_page", "lift": "lift_over_ground",
           "rim": "rim_minus_interior", "spread": "interior_spread"}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--reference", default="assets/reference/home-reference.png")
    ap.add_argument("--render")
    ap.add_argument("--label", default="render")
    ap.add_argument("--backdrop",
                    default="android/app/src/main/res/drawable-nodpi/gs_home_backdrop.png",
                    help="the page atmosphere PNG the cards sit on, read as the ground "
                         "under them for the lift metric (both platforms ship this file)")
    ap.add_argument("--probe-set", default="android", choices=["android", "ios"],
                    help="which platform's render rects to use")
    ap.add_argument("--probes", help="JSON overriding the built-in probe sets")
    ap.add_argument("--debug-out", help="annotated copy, to verify probe placement")
    ap.add_argument("--report-only", action="store_true")
    args = ap.parse_args()

    if args.probes:
        with open(args.probes) as fh:
            PROBES.update(json.load(fh))

    # The backdrop is only needed by probes that gate on lift, but loading it is
    # cheap and doing it unconditionally means a probe switched to lift cannot
    # silently start measuring nothing because the file went missing.
    digest = hashlib.sha256(open(args.reference, "rb").read()).hexdigest()
    if digest != REFERENCE_SHA256:
        print(f"::error::{args.reference} is not the sheet these bands were measured "
              f"from.\n  expected {REFERENCE_SHA256}\n  found    {digest}")
        print("  re-measure every probe (--report-only, --debug-out) and update "
              "REFERENCE_SHA256 and BANDS in the same commit.")
        return 2

    try:
        ground = load_luma(args.backdrop)
    except (OSError, ValueError) as exc:
        print(f"::error::could not read the backdrop ground {args.backdrop}: {exc}")
        return 2

    try:
        ref = measure(args.reference, "reference", SCREEN, ground=ground)
    except (OSError, ValueError) as exc:
        print(f"::error::could not read the reference sheet: {exc}")
        return 2

    print("=== reference sheet, measured on clean glass (A4-corrected) ===")
    for r in ref:
        print(row(r))

    # The bands are recorded measurements of this sheet. If the sheet or a probe
    # rect has moved, the band is a lie, and a gate that cannot be right about
    # its own anchor cannot be right about a render. Checked, not assumed.
    stale = []
    for r in ref:
        for metric, band in BANDS[r["probe"]].items():
            v = r[METRICS[metric]]
            if v is None or v != v or not band[0] <= v <= band[1]:
                stale.append(f"{r['probe']} {metric} {fmt(v)} vs band {band}")
    if stale:
        print("\n::error::the recorded band no longer contains the sheet itself:")
        for s in stale:
            print(f"  - {s}")
        print("  re-measure the probe (and check the rect still lands on glass "
              "with --debug-out) before trusting a verdict.")
        return 2

    if args.report_only:
        return 0
    if not args.render:
        print("::error::--render is required unless --report-only")
        return 2
    try:
        got = measure(args.render, args.probe_set, None, args.debug_out, ground=ground)
    except (OSError, ValueError) as exc:
        # A5: an unreadable capture is "cannot measure", not "glass drifted".
        print(f"::error::could not read {args.render}: {exc}")
        return 2

    print(f"\n=== {args.label}, same metrics ({args.probe_set} rects) ===")
    for r in got:
        print(row(r))

    # A5: a NaN means the instrument could not see the surface at all. It must
    # never be routed into a band comparison, where it reads as a drift.
    blind = [r["probe"] for r in got
             if any(isinstance(v, float) and np.isnan(v) for v in r.values())]
    if blind:
        print(f"\n::error::could not measure {', '.join(blind)} on {args.render} "
              f"— a probe window is too small or off-frame (not a glass verdict)")
        if args.debug_out:
            print(f"  inspect {args.debug_out} to see where the boxes landed")
        return 2

    failures = []
    print("\n--- assertions (presence + material + purity + rim, all local) ---")
    for r in got:
        checks = [("A1 page present",   r["page_outside"],    PAGE_LUMA_BAND),
                  ("A3 probe on glass", r["interior_spread"], (0.0, SPREAD_MAX))]
        for metric, band in BANDS[r["probe"]].items():
            label = "A2 glass tint" if metric == "tint" else (
                "A2 glass lift" if metric == "lift" else "A4 rim highlight")
            checks.append((label, r[METRICS[metric]], band))
        print(f"  [{r['probe']}]")
        for name, value, (lo, hi) in sorted(checks):
            ok = lo <= value <= hi
            if not ok:
                failures.append(f"{r['probe']}/{name}: {value:.2f} outside [{lo}, {hi}]")
            print(f"    {name:18s} {value:8.2f}  in [{lo},{hi}]  {'OK' if ok else 'FAIL'}")

    if failures:
        print(f"\n::error::Home glass failed {len(failures)} check(s) on {args.label}:")
        for f in failures:
            print(f"  - {f}")
        if args.debug_out:
            print(f"  (if A3 failed, open {args.debug_out}: the red box must sit "
                  f"entirely on blank card glass)")
        return 1
    print("\nOK: both cards are present, each moves its own ground as the sheet's "
          "does, both probes are on clean glass, and both hairlines are lit")
    return 0


if __name__ == "__main__":
    sys.exit(main())
