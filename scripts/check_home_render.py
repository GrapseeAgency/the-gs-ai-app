#!/usr/bin/env python3
"""Measure Home's glass material at known probes, and fail on drift.

WHY NOT AUTO-DETECTION
----------------------
Two earlier versions of this script tried to FIND the cards — first by
thresholding luminance ("glass darkens the page"), then by finding closed
gradient contours. Both failed, and the self-test proved it: thresholding
merged the whole frame because the page's own gradient (p50 = 234) spans a
wider range than the card-to-page delta (page 243.8 vs interior 236.7, ~7 L),
and contour detection returned text blobs. An instrument that reports different
numbers for the same image at different scales cannot gate anything.

So this measures a DECLARED probe set instead. Each probe is a normalised rect,
chosen once and then verified by eye via `--debug-out`, which draws every probe
onto a copy of the image so "did it land on clean glass" is checkable rather
than assumed. Reference and render get separate probe sets because they are
different layouts at different aspect ratios — pretending otherwise is what
made the earlier versions lie.

WHAT IS COMPARED
----------------
Both metrics are LOCAL, so they survive the mock's non-rectified scale:
  interior_minus_page : card interior vs the page immediately outside it
  rim_minus_interior  : the card's brightest top-edge band vs its interior

Exit: 0 within tolerance, 1 drift, 2 could not measure.
"""

import argparse
import json
import sys

import numpy as np
from PIL import Image, ImageDraw

SCREEN = (172, 56, 852, 1460)          # the phone screen inside the mock

# Each probe: a card interior rect (normalised x, y, w, h), plus a strip of bare
# page immediately beside it, plus the band along the card's top edge that
# should carry the rim highlight.
#
# The Suggestions card is the one surface that sits at the same normalised
# position in the mock and in a device render (~0.78 down the page) and is wide
# enough to give a large text-free area, so it is the only probe asserted here.
# Every other card was tried and the --debug-out overlay showed the boxes landing
# on glyphs or on the header; a probe that cannot be verified by eye is not a
# measurement.
PROBES = {
    "reference": [
        {
            "name": "suggestions",
            "interior": [0.66, 0.792, 0.26, 0.042],
            "outside":  [0.004, 0.792, 0.022, 0.042],
            "top_edge": [0.30, 0.7765, 0.40, 0.011],
        },
    ],
    "render": [
        {
            "name": "suggestions",
            "interior": [0.66, 0.792, 0.26, 0.042],
            "outside":  [0.004, 0.792, 0.022, 0.042],
            "top_edge": [0.30, 0.7765, 0.40, 0.011],
        },
    ],
}

# MEASURED, not assumed. These come from the reference sheet at the verified
# probe and are scale-stable to +/-0.06 L under 0.72x and 1.4x resampling.
#
# They also correct a claim made earlier in this work. Wave 1 was scoped from
# "interior = page -8..-12 L, rim = +17 L", read off a single column at the
# video card's top-left corner. That +17 step was the pearl ART behind the card,
# not the glass. Measured where there is no art, the reference's glass is far
# more restrained than that: the Suggestions card interior is level with the
# page (-0.4 L) and its top edge carries essentially no highlight (+0.3 L). The
# cards read as separate because of their rounded silhouette, their soft shadow
# and the artwork inside them — not because of a bright rim or a strong tint.
# Had the gate not measured this, Wave 1 would have painted a rim highlight over
# both platforms to chase a number that was never there.
#
# `rim_minus_interior` is the rim's excess over the brighter of page and
# interior, so 0 means "no highlight at all" — which is what the reference has.
TARGETS = {
    "interior_minus_page": (-8.0, 3.0),
    "rim_minus_interior": (-1.5, 8.0),
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
    return l[y0:y1, x0:x1], (x0, y0, x1, y1)


def rim_excess(l, probe):
    """How much brighter the card's top hairline is than everything around it.

    A fixed band cannot find the rim: the overlay showed it landing just inside
    the card on a render and on the label text in the mock, because the two
    images put the boundary a pixel or two apart. So the rim is found where it
    actually is — the local maximum of a vertical profile taken across a
    text-free x-range, over a window straddling the declared top edge — and
    scored against the brighter of the bare page above and the interior below.
    A real highlight beats both; a page sample cannot, because the page is one
    of the two things being compared.
    """
    H, W = l.shape
    x, y, w, _ = probe["interior"]
    # A clean horizontal span inside the card, away from glyphs.
    x0, x1 = int((x + 0.02) * W), int((x + w - 0.02) * W)
    top = probe["top_edge"][1]
    y0 = max(0, int((top - 0.006) * H))
    y1 = min(H, int((top + 0.018) * H))
    win = l[y0:y1, x0:x1]
    if win.shape[0] < 6:
        return float("nan"), float("nan"), float("nan")
    prof = win.mean(axis=1)
    k = max(2, prof.size // 5)
    page_above = float(np.median(prof[:k]))
    interior_below = float(np.median(prof[-k:]))
    # Per-column peak, then the median across columns. Averaging columns first
    # smears the rim to nothing: the mock is very slightly perspective-distorted,
    # so its card edge is not horizontal at pixel resolution and a row-mean
    # spreads a 1-2px highlight over the whole window. Each column finds its own
    # edge; the median across columns is then a stable estimate of the rim.
    peak = float(np.median(win.max(axis=0)))
    return peak, peak - max(page_above, interior_below), max(page_above, interior_below)


def measure(path, kind, crop=None, debug_out=None):
    l = load_luma(path, crop)
    src = Image.open(path).convert("RGB")
    if crop:
        src = src.crop(crop)
    d = ImageDraw.Draw(src) if debug_out else None
    rows = []
    for p in PROBES[kind]:
        ins, _ = rect_of(l, p["interior"])
        out, _ = rect_of(l, p["outside"])
        interior = float(np.median(ins))
        page = float(np.median(out))
        peak, rim_ex, _ = rim_excess(l, p)
        rows.append({
            "probe": p["name"],
            "interior": interior,
            "page_outside": page,
            "rim": peak,
            "interior_minus_page": interior - page,
            "rim_minus_interior": rim_ex,
        })
        if d is not None:
            for r, colour in ((p["interior"], "red"), (p["outside"], "lime")):
                x, y, w, h = r
                d.rectangle([x * src.width, y * src.height,
                             (x + w) * src.width, (y + h) * src.height],
                            outline=colour, width=3)
            # Draw the rim window so its placement is checkable too.
            H, W = l.shape
            x, y, w, _ = p["interior"]
            top = p["top_edge"][1]
            d.rectangle([int(x * W), int((top - 0.006) * H), int((x + w) * W),
                         int((top + 0.018) * H)], outline="blue", width=3)
    if debug_out:
        src.save(debug_out)
    return rows


def fmt(v):
    return "    n/a" if v is None or (isinstance(v, float) and np.isnan(v)) else f"{v:8.2f}"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--reference", default="assets/reference/home-reference.png")
    ap.add_argument("--render")
    ap.add_argument("--label", default="render")
    ap.add_argument("--probes", help="JSON overriding the built-in probe sets")
    ap.add_argument("--debug-out", help="write an annotated copy to verify probe placement")
    ap.add_argument("--report-only", action="store_true")
    args = ap.parse_args()

    if args.probes:
        with open(args.probes) as fh:
            PROBES.update(json.load(fh))
    if args.debug_out and not args.render:
        print("::error::--debug-out needs --render")
        return 2

    ref = measure(args.reference, "reference", SCREEN, None)
    print("=== reference sheet, measured at declared probes ===")
    for r in ref:
        print(f"  {r['probe']:14s} interior={fmt(r['interior'])} page={fmt(r['page_outside'])} "
              f"rim={fmt(r['rim'])}  Δ(interior-page)={fmt(r['interior_minus_page'])} "
              f"Δ(rim-interior)={fmt(r['rim_minus_interior'])}")

    if args.report_only:
        return 0
    if not args.render:
        print("::error::--render is required")
        return 2

    got = measure(args.render, "render", None, args.debug_out)
    print(f"\n=== {args.label}, same metrics ===")
    for r in got:
        print(f"  {r['probe']:14s} interior={fmt(r['interior'])} page={fmt(r['page_outside'])} "
              f"rim={fmt(r['rim'])}  Δ(interior-page)={fmt(r['interior_minus_page'])} "
              f"Δ(rim-interior)={fmt(r['rim_minus_interior'])}")

    # NO VERTICAL-RHYTHM GATE, and no half-built one printed either.
    # The idea was to compare where the brand row, greeting and dock sit.
    # It cannot work between these two kinds of image: the reference sheet has
    # a drawn-in "9:41" status bar that our captures do not, so the first text
    # band measured 0.03 on the reference (the status bar) against 0.14/0.17 on
    # the renders (the brand) — the comparison was silently ranking different
    # elements, and its "greeting-brand gap 0.15 vs 0.00" was pure artefact.
    # On top of that, Robolectric reports a zero status bar and the simulator
    # window reports zero safe-area insets, so real 47pt and 34pt insets are
    # missing from both renders while present in the sheet. Absolute vertical
    # position is therefore not a comparable quantity here, and printing a
    # confident-looking number for it would be worse than printing nothing.
    failures = []
    print("\n--- assertions (local, scale-invariant, inset-independent) ---")
    # GEOMETRY IS NOT GATED HERE, and there is no flag that pretends otherwise.
    # Two attempts to auto-detect the cards (luminance thresholding, gradient
    # contours) both failed their own self-test — the first returned different
    # column splits for the same image at different scales — so the ratios are
    # printed for a human and not asserted. A gate with no measurement behind
    # it is worse than an honest gap.
    for key, (lo, hi) in TARGETS.items():
        vals = [r[key] for r in got if r[key] is not None]
        if not vals:
            print(f"  {key:22s} n/a"); continue
        worst = min(vals) if key == "interior_minus_page" else max(vals)
        bad = [v for v in vals if not lo <= v <= hi]
        if bad:
            failures.append(f"{key}: {['%.2f' % v for v in bad]} outside [{lo}, {hi}]")
        print(f"  {key:22s} values={['%.2f' % v for v in vals]} "
              f"in [{lo},{hi}]  {'OK' if not bad else 'DRIFT'}")

    if failures:
        print("\n::error::Home glass drifted from the reference:")
        for f in failures:
            print(f"  - {f}")
        if args.debug_out:
            print(f"  (check {args.debug_out}: every box must sit on clean glass, not glyphs)")
        return 1
    print("\nOK: within tolerance")
    return 0


if __name__ == "__main__":
    sys.exit(main())
