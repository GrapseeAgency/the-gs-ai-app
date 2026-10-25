#!/usr/bin/env python3
"""check_settings_render.py — the Settings trio's pixel gate.

WHY THIS EXISTS
---------------
The three settings screens (hub, Profile & Preferences, Memory Management) were
CI-gated on EXISTENCE ONLY: the workflows asserted the six PNGs were present and
>20 KB. That is the structural reason a page can "look done" in review while its
cards went flat, the lit rim never painted, or the peach CTA came back as glass.

WHY THE GATED NUMBER IS A RESIDUAL, NOT A LIFT
----------------------------------------------
The plan asked for "card interior lift +1…+6 L over page". On these screens that
number is not measurable, and the reason is the ground, not the card:

  * the page beside the card is a photograph whose LEFT EDGE is 10-14 L
    brighter than its centre, so the gutter is not the ground under the card.
    The same card read -5.5 L against its left gutter and +4.3 L against its
    right one on the same capture;
  * the ground ITSELF moves between captures: the backdrop is scaled to cover
    the screen, so the same point of the page sits over a different part of the
    artwork at 393x852 and at 360x780 (ground 228.7 vs 235.8 L for one window).

So `lift` is PRINTED as a diagnostic and not gated, and what is gated is the
HOME GATE'S MATERIAL LAW, applied to the ground actually under the card:

    interior = A·F + (1 − A)·ground,   A = 0.45, F = 235.2

Those constants were solved off the home sheet with two cards on two grounds
(check_home_render.py documents the solve); the settings cards carry the same
role, so the law applies unchanged here, and the residual —
`interior − law(ground)` — is position-independent: the card may sit anywhere
and the law still says what its pixels must be.

The ground is not a guess either: the backdrop is a SHIPPED FILE
(`gs_home_backdrop.png`, 590x1278, identical bytes on both platforms) drawn
edge to edge under the whole page, cropped the way the renderers crop it. Against
the captures the model reproduces the visible page to +/-0.01 L in both gutters,
an error smaller than one 8-bit step.

`interior` is the MEDIAN of the card's rect, not its mean: the rect necessarily
holds labels, wells and hairlines, and a mean measures their share instead of the
glass. The median is the glass — text is a dark minority and a hairline is one
row — and it is what made the probe stable across both widths (mean: 8 L of
drift; median: under 1.5 L).

WHAT EACH PROBE IS GATED ON
---------------------------
  material  the residual above, +/-2.5 L. Catches the card that lost its glass
            (interior -> ground, residual +2.7 L on the brightest ground here),
            the card that went opaque (255 -> +16 L), and a card built from the
            wrong role (the thick `Card` role reads +1.5 to +4 L).
  rim       twice on the same window: `excess` over the card's own median (the
            mock's lit top edge is +10.2 / +10.7 / +6.4 L over its glass; floor
            6) and `level`, the absolute luma of that row (the mock's three rims
            read 249.4 / 248.1 / 243.9; ours 250.6-253.2). The level is
            ground-independent — the same law the Home gate's A9 uses — so a rim
            cannot be faked by moving the card somewhere darker.
  cta       the 90th percentile of R-B inside the peach pill's window: the
            mock's #FEB58D reads ~113, a pill repainted as another glass capsule
            <= 20. A percentile, not a mean, because the white label's share of
            the window changes with the label.

Probe coordinates are in POINTS, not ratios: both harnesses capture at two
widths (393x852, 360x780), and a fixed-dp layout moves in points between them
while every ratio moves. The point size comes from `--pt WxH` or, failing that,
from the render's `-393x852` suffix; neither present is exit 2.

Usage:
  check_settings_render.py --render nexly-settings-393x852.png --screen hub
  check_settings_render.py --render ... --screen memory --self-test
Exit: 0 within bands, 1 drift, 2 could not measure.
"""

import argparse
import json
import os
import re
import sys
import tempfile

import numpy as np
from PIL import Image

GEOMETRY = "docs/reference/settings/settings-geometry.json"
# The same artwork on both platforms; the iOS copy is the fallback so the gate
# runs from either checkout layout.
BACKDROP = "android/app/src/main/res/drawable-nodpi/gs_home_backdrop.png"
BACKDROP_IOS = "ios/App/Assets.xcassets/GSHomeBackdrop.imageset/gs-home-backdrop.png"

# The measured glass (check_home_render.py's solve, reused because the settings
# cards carry that role, not a second material).
COVERAGE = 0.45
FILL_LUMA = 235.2

MATERIAL_BAND = (-2.5, 2.5)       # residual; the home gate's +/-2.0 + 0.5 spread
# How far the composite moves between "glass" and "no glass": A x |F - ground|,
# which is exactly how far a card painted flat onto its ground would land from
# the law. Below this the two are within a band of each other and the probe
# prints n/a rather than a verdict — the same call check_home_render.py makes
# when its ground meets the fill. The rim carries those captures.
MATERIAL_MIN_SENSITIVITY = 2.5
RIM_LEVEL_BAND = (242.0, 255.0)   # mock 249.4 / 248.1 / 243.9
RIM_EXCESS_CAP = 40.0
CTA_BAND = (70.0, 160.0)          # mock #FEB58D reads ~113
CTA_PERCENTILE = 90

# Probes in points on a 393-wide phone. `rim_y` is where the card's top edge is
# EXPECTED and `rim_search` the +/- window that finds it: the edge is the
# brightest row on the page by 8-12 L, so a wide window cannot mistake a divider
# or a neighbour's edge for it, and the interior is then measured FROM THE FOUND
# EDGE rather than from a hard-coded y — which is what lets the same table hold
# both platforms, whose safe-area handling puts the same card a few points apart.
SCREENS = {
    "hub": {
        "about": "the Personalization group card",
        "rim_y": 339.0,
        "rim_search": 24.0,
        "rim_span_x": (70.0, 330.0),
        "card_dx": (26.0, 368.0),
        "card_dy": (91.0, 181.0),
        "cta_dx": (250.0, 355.0),
        "cta_dy": (-124.0, -54.0),
    },
    "profile": {
        "about": "the Profile group card",
        "rim_y": 250.0,
        "rim_search": 24.0,
        "rim_span_x": (70.0, 330.0),
        "card_dx": (26.0, 368.0),
        "card_dy": (180.0, 216.0),
        "cta_dx": None,
        "cta_dy": None,
    },
    "memory": {
        "about": "the Manage Memory group card",
        "rim_y": 501.0,
        "rim_search": 24.0,
        "rim_span_x": (70.0, 330.0),
        "card_dx": (26.0, 368.0),
        "card_dy": (5.0, 199.0),
        "cta_dx": (215.0, 320.0),
        "cta_dy": (-343.0, -305.0),
    },
}


def fail(msg):
    print(f"ERROR: {msg}")
    sys.exit(2)


def load_rgb(path):
    try:
        return np.asarray(Image.open(path).convert("RGB")).astype(float)
    except Exception as exc:  # unreadable is a measurement failure, not a drift
        fail(f"cannot open {path}: {exc}")


def load_backdrop(explicit):
    for path in [explicit] if explicit else [BACKDROP, BACKDROP_IOS]:
        if path and os.path.exists(path):
            return np.asarray(Image.open(path).convert("RGB")).astype(float), path
    fail(f"no backdrop artwork found (looked for {explicit or BACKDROP})")


def point_size(path, explicit, image_w):
    """Points, from --pt or the harness's -WxH filename suffix."""
    if explicit:
        m = re.fullmatch(r"(\d+)x(\d+)", explicit.strip())
        if not m:
            fail(f"--pt wants WxH, got {explicit!r}")
        return int(m.group(1)), int(m.group(2))
    m = re.search(r"-(\d{3})x(\d{3,4})(?:-|\.|$)", os.path.basename(path))
    if not m:
        fail(f"{os.path.basename(path)} carries no -WxH suffix; pass --pt WxH")
    pt_w, pt_h = int(m.group(1)), int(m.group(2))
    scale = image_w / pt_w
    if abs(scale - round(scale)) > 0.02:
        fail(f"{pt_w}pt does not divide {image_w}px into a whole scale")
    return pt_w, pt_h


def px(box, scale):
    x0, x1, y0, y1 = box
    return (int(round(x0 * scale)), int(round(x1 * scale)),
            int(round(y0 * scale)), int(round(y1 * scale)))


def window(a, box, scale):
    x0, x1, y0, y1 = px(box, scale)
    h, w, _ = a.shape
    return a[max(0, y0):min(h, y1), max(0, x0):min(w, x1)]


def ground(bd, box, pt_w, pt_h):
    """The backdrop's own pixels under `box`, cropped like ContentScale.Crop /
    scaledToFill: scaled by the larger factor, centred, then sampled."""
    bh, bw, _ = bd.shape
    k = max(pt_w / bw, pt_h / bh)
    ox = (pt_w - bw * k) / 2.0
    oy = (pt_h - bh * k) / 2.0
    x0, x1, y0, y1 = box
    ix0 = int(round((x0 - ox) / k))
    ix1 = int(max(round((x1 - ox) / k), ix0 + 1))
    iy0 = int(round((y0 - oy) / k))
    iy1 = int(max(round((y1 - oy) / k), iy0 + 1))
    win = bd[max(0, iy0):min(bh, iy1), max(0, ix0):min(bw, ix1)]
    if win.size == 0:
        fail("the ground window falls outside the backdrop artwork")
    return win


def rim_row(a, probe, scale):
    """Brightest row mean inside the window — the card's lit top edge."""
    h, w, _ = a.shape
    gray = a.mean(axis=2)
    x0 = int(round(probe["rim_span_x"][0] * scale))
    x1 = int(round(probe["rim_span_x"][1] * scale))
    yc = probe["rim_y"] * scale
    rad = probe["rim_search"] * scale
    lo, hi = max(0, int(round(yc - rad))), min(h, int(round(yc + rad)))
    rows = [(float(gray[y, x0:x1].mean()), y) for y in range(lo, hi)]
    best, by = max(rows)
    return best, by, (lo, hi)


def card_box(probe, rim_pt):
    """The interior rect, hung off the edge that was actually found."""
    return (probe["card_dx"][0], probe["card_dx"][1],
            rim_pt + probe["card_dy"][0], rim_pt + probe["card_dy"][1])


def cta_box(probe, rim_pt):
    """The pill's search window, also hung off the found edge: a platform whose
    safe area shifts the page shifts the pill with it."""
    if not probe.get("cta_dx"):
        return None
    return (probe["cta_dx"][0], probe["cta_dx"][1],
            rim_pt + probe["cta_dy"][0], rim_pt + probe["cta_dy"][1])


def measure(a, bd, probe, scale, pt_w, pt_h, bands, anchor=None):
    """The shared prober: every screen runs the same measurements.

    `anchor` freezes the edge the interior hangs off. The self-test passes the
    clean capture's anchor through, because a doctored capture has no rim left
    to find — re-locating it there would measure a different rect and quietly
    pass for the wrong reason.
    """
    rows = []

    # The anchor first: the card's lit top edge. Everything else hangs off it,
    # including the CTA window, so a frozen anchor keeps the whole probe set
    # pointing at the same place on a doctored copy.
    best, by, (lo, hi) = rim_row(a, probe, scale)
    base = (anchor if anchor is not None else by) / scale
    box = card_box(probe, base)

    card_win = window(a, box, scale)
    if card_win.size == 0:
        fail("a probe window falls outside the render")
    interior = float(np.median(card_win.mean(axis=2)))
    ground_l = float(ground(bd, box, pt_w, pt_h).mean(axis=2).mean())
    law = COVERAGE * FILL_LUMA + (1.0 - COVERAGE) * ground_l
    sensitivity = abs(FILL_LUMA - ground_l) * COVERAGE

    residual = interior - law
    mb = bands["material"]
    identifiable = sensitivity >= MATERIAL_MIN_SENSITIVITY
    rows.append(("card", "residual", residual, mb,
                 (mb[0] <= residual <= mb[1]) if identifiable else None,
                 f"interior {interior:.1f} L, ground {ground_l:.1f} L, "
                 f"sensitivity {sensitivity:.1f} L"))
    # Diagnostic, deliberately ungated: the mock's own +3.8…+5.0 L is measured
    # over the mock's ground, and this capture's ground is a different part of
    # the same photograph. See the header.
    rows.append(("card", "lift*", interior - ground_l, (float("-inf"), float("inf")),
                 True, "diagnostic only"))

    excess = best - interior
    rb = bands["rim"]
    rows.append(("card", "rim_excess", excess, rb, rb[0] <= excess <= rb[1],
                 f"edge row {by} = {by / scale:.1f}pt (window {lo}..{hi})"))
    rows.append(("card", "rim_level", best, RIM_LEVEL_BAND,
                 RIM_LEVEL_BAND[0] <= best <= RIM_LEVEL_BAND[1], "mock 243.9-249.4"))

    cta = cta_box(probe, base)
    if cta:
        cta_win = window(a, cta, scale)
        rb90 = float(np.percentile(cta_win[:, :, 0] - cta_win[:, :, 2],
                                   CTA_PERCENTILE))
        rows.append(("cta", f"R-B p{CTA_PERCENTILE}", rb90, CTA_BAND,
                     CTA_BAND[0] <= rb90 <= CTA_BAND[1], "#FEB58D reads ~113"))
    return rows


def doctor_self_test(a, bd, probe, scale, pt_w, pt_h, bands, path):
    """The gate must be CAPABLE of failing: paint the card flat onto its own
    ground, bury the rim, desaturate the pill — every probe must go red."""
    doctored = a.copy()
    h, w, _ = doctored.shape

    # 1. glass -> ground: the card with no fill at all.
    best, by, _ = rim_row(a, probe, scale)
    box = card_box(probe, by / scale)
    cx0, cx1, cy0, cy1 = px(box, scale)
    doctored[cy0:cy1, cx0:cx1] = ground(bd, box, pt_w, pt_h).mean(axis=(0, 1))

    # 2. rim -> flat: bury EVERY row the search can reach, not a narrower band,
    #    or a render whose edge drifted would keep a bright row and the
    #    self-test would cry wolf at a capture that is fine.
    sx0, sx1 = [int(round(v * scale)) for v in probe["rim_span_x"]]
    yc = probe["rim_y"] * scale
    rad = probe["rim_search"] * scale
    fill = float(np.median(window(a, box, scale).mean(axis=2)))
    doctored[max(0, int(yc - rad)):min(h, int(yc + rad)), sx0:sx1] = fill

    # 3. cta -> grey: a peach pill repainted as another glass capsule.
    cta = cta_box(probe, by / scale)
    if cta:
        tx0, tx1, ty0, ty1 = px(cta, scale)
        m = doctored[ty0:ty1, tx0:tx1]
        doctored[ty0:ty1, tx0:tx1] = np.repeat(
            m.mean(axis=2, keepdims=True), 3, axis=2)

    clean_rows = measure(a, bd, probe, scale, pt_w, pt_h, bands)
    clean = [r for r in clean_rows if r[4] is False]
    with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tf:
        Image.fromarray(doctored.astype(np.uint8)).save(tf.name)
        doc = [r for r in measure(load_rgb(tf.name), bd, probe, scale,
                                  pt_w, pt_h, bands, anchor=by) if r[4] is False]
    os.unlink(tf.name)

    print(f"clean capture: {len(clean)} failing probe(s)"
          + ("" if not clean else ": " + ", ".join(f"{p}/{m}" for p, m, *_ in clean)))
    print(f"doctored     : {len(doc)} failing probe(s): "
          + ", ".join(f"{p}/{m}" for p, m, *_ in doc))
    if clean:
        print("SELF-TEST ABORT: the real capture does not pass its own bands")
        return 2

    fired = {(p, m) for p, m, *_ in doc}
    # The rim is ground-independent, so it must always be able to fail. The
    # residual is only demanded where the ground lets it see a difference;
    # where it cannot, the run says so above instead of pretending.
    wanted = {("card", "rim_excess"), ("card", "rim_level")}
    identifiable = {("card", "residual")} if any(
        m == "residual" and ok is not None
        for _, m, _, _, ok, _ in clean_rows) else set()
    wanted |= identifiable
    if probe.get("cta_dx"):
        wanted.add(("cta", f"R-B p{CTA_PERCENTILE}"))
    missing = wanted - fired
    if missing:
        print("SELF-TEST FAIL: doctoring left "
              + ", ".join(f"{p}/{m}" for p, m in sorted(missing))
              + " green — the gate cannot fail")
        return 1
    skipped = "" if identifiable else " (residual not identifiable on this ground)"
    print(f"SELF-TEST PASS: {os.path.basename(path)} passes, and every gated "
          f"probe ({', '.join(m for _, m in sorted(wanted))}) can fail{skipped}")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--render", required=True)
    ap.add_argument("--screen", required=True, choices=sorted(SCREENS))
    ap.add_argument("--geometry", default=GEOMETRY,
                    help="the committed contract; its rim floor is read back "
                         "when present, so the mock stays the source")
    ap.add_argument("--backdrop", help=f"page artwork (default {BACKDROP})")
    ap.add_argument("--pt", help="WxH render size in points; defaults to the "
                                 "filename suffix")
    ap.add_argument("--label", default="")
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()

    bands = {"material": MATERIAL_BAND, "rim": (6.0, RIM_EXCESS_CAP)}
    if os.path.exists(args.geometry):
        with open(args.geometry) as fh:
            geo = json.load(fh)
        floor = geo.get("card", {}).get("rim_top_excess_L_min")
        if floor:
            bands["rim"] = (float(floor), RIM_EXCESS_CAP)
    else:
        print(f"note: {args.geometry} not found; using the built-in bands")

    probe = SCREENS[args.screen]
    a = load_rgb(args.render)
    bd, bd_path = load_backdrop(args.backdrop)
    pt_w, pt_h = point_size(args.render, args.pt, a.shape[1])
    scale = a.shape[1] / pt_w

    if args.self_test:
        return doctor_self_test(a, bd, probe, scale, pt_w, pt_h, bands,
                                args.render)

    rows = measure(a, bd, probe, scale, pt_w, pt_h, bands)
    label = args.label or args.screen
    print(f"\n=== {label}: {os.path.basename(args.render)} "
          f"({pt_w}x{pt_h}pt @ {scale:.2f}x, {probe['about']}) ===")
    print(f"{'probe':<6}{'metric':<12}{'measured':>10}   {'band':<18} verdict")
    bad = 0
    for name, metric, value, band, ok, note in rows:
        mark = "n/a " if ok is None else ("OK" if ok else "DRIFT")
        bad += 1 if ok is False else 0
        shown = ("      n/a" if band[0] == float("-inf")
                 else f"[{band[0]:.1f}, {band[1]:.1f}]")
        extra = f"  ({note})" if note else ""
        print(f"{name:<6}{metric:<12}{value:>10.2f}   {shown:<18}{mark:>8}{extra}")
    if bad:
        print(f"\n{bad} probe(s) drifted — the mock's numbers are the contract; "
              f"ground read from {os.path.basename(bd_path)}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
