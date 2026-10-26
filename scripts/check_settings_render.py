#!/usr/bin/env python3
"""check_settings_render.py — the Settings trio's pixel gate.

WHY THIS EXISTS
---------------
The three settings screens (hub, Profile & Preferences, Memory Management) were
CI-gated on EXISTENCE ONLY: the workflows asserted the six PNGs were present and
>20 KB. That is the structural reason a page can "look done" in review while its
cards went flat, the lit rim never painted, the peach CTA came back as glass, or
the whole page simply ran a quarter longer than the mock it is built from.

WHAT EACH PROBE IS GATED ON
---------------------------
  material  the HOME GATE'S MATERIAL LAW, applied to the ground actually under
            the card:  interior = A·F + (1 − A)·ground,  A = 0.45, F = 235.2.
            `interior` is the MEDIAN of the card's rect — the rect necessarily
            holds labels, wells and hairlines, and a mean measures their share
            instead of the glass. Residual band ±2.5 L.

            Why not the plan's "lift over the page beside the card, +1…+6 L":
            that page is a photograph whose left edge is 10-14 L brighter than
            its centre, so the gutter is not the ground under the card — one
            card read -5.5 L against its left gutter and +4.3 L against its
            right one. And the ground moves between capture widths because the
            backdrop is scaled to COVER the screen (one window sits over 228.7 L
            at 393x852 and 235.8 L at 360x780). `lift` is printed as a
            diagnostic (`lift*`) and the residual is gated, because the residual
            is position-independent: the card may sit anywhere and the law still
            says what its pixels must be.

            The ground is not a guess either: the backdrop is a SHIPPED FILE
            (`gs_home_backdrop.png`, 590x1278, identical bytes on both
            platforms) drawn edge to edge under the page, cropped the way the
            renderers crop it. Against the captures that model reproduces the
            visible page to +/-0.01 L in both gutters — smaller than one 8-bit
            step. Its one limit is stated rather than implied: where the ground
            meets the fill (`A·|F − ground|` below `MATERIAL_MIN_SENSITIVITY`)
            glass and no-glass are within a band of each other, and the probe
            prints n/a instead of a verdict. The rim carries those captures.

  rim       twice on the same row: `excess` over the card's own median (the
            mock's lit top edge is +10.2 / +10.7 / +6.4 L over its glass) and
            `level`, the absolute luma of that row (the mock's three rims read
            249.4 / 248.1 / 243.9) — ground-independent, the same law the Home
            gate's A9 uses, so a rim cannot be faked by moving the card.

  pitch     the mock's ROW RHYTHM: rows of type are located by ink band,
            clustered into rows, and the median band-to-band distance must be
            42.0-45.0 pt (the mock's own 42.3-43.5). This is the number that
            decides whether the page reads like the reference at a glance, and
            until now nothing could see it: the settings pitch drifted 54.7 →
            46.7 → 44.0 → 48.5 across four commits and every gate stayed green,
            because a card's height is invisible to a colour probe.

  cta       the 90th percentile of R-B inside the peach pill's window: the
            mock's #FEB58D reads ~113, a pill repainted as another glass capsule
            <= 20. A percentile, not a mean, because the white label's share of
            the window changes with the label.

THE ANCHOR, AND WHY IT REFUSES TO MEASURE
-----------------------------------------
Every window hangs off the card's lit top edge as it is FOUND in that capture
(`rim_y` is only where to look), which is what lets one table hold both
platforms — their safe areas put the same card a few points apart. But a rim
search alone can land on any bright row: on the 2026-10-26 worktree it found a
hairline at 325.7 pt, and the probes then measured a rect containing no card at
all and printed OK. An edge is only accepted when it is bright (`level` ≥ 245)
AND rows of type sit below it at a believable rhythm. Otherwise the run exits 2
with the candidate list — a wrong rect is a measurement failure, never a pass.

Modes: default (gate) · `--self-test` (doctoring; every gated probe must be able
to go red) · `--derive` (print the candidate SCREENS table from a real render,
so re-calibration is a command and not an investigation) · `--mock` (advisory
ink comparison against a reference mock at a verified scale).

Usage:
  check_settings_render.py --render nexly-settings-393x852.png --screen hub
  check_settings_render.py --render ... --screen memory --self-test
  check_settings_render.py --render ... --screen hub --derive
  check_settings_render.py --render ... --screen hub --derive \
      --mock docs/reference/settings/settings-hub.png
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

# Row rhythm. The mock's own title bands sit 42.3-43.5 pt apart (measured: 43.53
# on settings-hub.png); 45.0 is the tolerance, not a licence — the pass that
# shipped 48.5 pt would have been caught here.
PITCH_BAND = (42.0, 45.0)
# The instrument lands verdict-neutral: until the layout is converged, the rhythm
# is printed and named but not counted. Flip to True in the commit that ships
# the layout change AND its re-derived table — never before either.
PITCH_BLOCKS = False
PITCH_MIN_ROWS = 3                # fewer and the pitch is not measurable
BAND_MIN_PT = 3.5                 # shorter ink is a hairline, not a line of type
ROW_CLUSTER_GAP_PT = 15.0         # title->subtitle is INSIDE a row; rows are 43.5
ANCHOR_RIM_MIN_LEVEL = 245.0      # a lit edge, not any bright row
# Type is 25 L under its own window's median on pearl glass. A fixed cap cannot
# work: the card fill itself sits at 236-245 L depending on the photograph under
# it, and the 235 L cap the first version used merged a whole card into one band.
INK_MARGIN_L = 25.0
INK_MIN_COLS = 3                  # 1-2 inked pixels is a hairline end or a speck
# --derive accepts a candidate whose pitch is within this of the contract. It is
# deliberately WIDER than PITCH_BAND: derive has to be able to find the card on a
# page that is off-rhythm — that is the situation it exists for. Refusing to name
# a card until it is already correct would make the tool useless exactly when it
# is needed.
DERIVE_PITCH_TOL = (-10.0, 12.0)

# Probes in points on a 393-wide phone. `rim_y` is where the card's top edge is
# EXPECTED, per platform — iOS's safe area puts the same card a few points from
# Android's, and `null` means that side has never been measured (the run says so
# and points at --derive rather than guessing). `rim_search` is the +/- window
# that finds it: the edge is the brightest row on the page by 8-12 L, so this
# window cannot mistake a divider or a neighbour's edge for it, and every other
# window is measured FROM THE FOUND EDGE rather than from a hard-coded y. Do not
# widen it to absorb a moved layout; re-derive the table instead — the two Android
# widths agree within 0.7 pt, which is what makes 24 honest.
# `pitch_dx` is the type column (right of the well, left of the chevron);
# `card_dx/card_dy` is the material window and `cta_dx/cta_dy` the pill.
# Re-derive all of it with --derive after any layout change, in the same commit.
SCREENS = {
    # The hub's second group card. `card_dy` sits low in it on purpose: there the
    # backdrop is ~10 L brighter than the glass token, which is where the
    # residual can SEE the fill.
    "hub": {
        "about": "the Personalization group card",
        "rim_y": {"android": 339.0, "ios": None},
        "rim_search": 24.0,
        "rim_span_x": (70.0, 330.0),
        "pitch_dx": (62.0, 345.0),
        "pitch_dy": (4.0, 200.0),
        "card_dx": (26.0, 368.0),
        "card_dy": (91.0, 181.0),
        "cta_dx": (250.0, 355.0),
        "cta_dy": (-124.0, -54.0),   # the Upgrade pill, one card up
    },
    # The Profile card's last row, same reason.
    "profile": {
        "about": "the Profile group card",
        "rim_y": {"android": 250.0, "ios": None},
        "rim_search": 24.0,
        "rim_span_x": (70.0, 330.0),
        "pitch_dx": (62.0, 345.0),
        "pitch_dy": (4.0, 200.0),
        "card_dx": (26.0, 368.0),
        "card_dy": (180.0, 216.0),
        "cta_dx": None,              # this mock paints no peach pill
        "cta_dy": None,
    },
    # Memory's Manage Memory card. Its ground meets the fill, so the residual is
    # not identifiable here and the rim, the pill and the rhythm carry it.
    "memory": {
        "about": "the Manage Memory group card",
        "rim_y": {"android": 501.0, "ios": None},
        "rim_search": 24.0,
        "rim_span_x": (70.0, 330.0),
        "pitch_dx": (62.0, 345.0),
        "pitch_dy": (4.0, 160.0),
        "card_dx": (26.0, 368.0),
        "card_dy": (5.0, 199.0),
        "cta_dx": (215.0, 320.0),
        "cta_dy": (-343.0, -305.0),  # the "Manage →" pill, in the hero card
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


# --------------------------------------------------------------------------
# type, rows, rhythm


def ink_bands(gray, box, scale, minh_pt=BAND_MIN_PT):
    """Lines of type inside `box` (pt), as (top, bottom, height, extent, run) pt.

    The threshold is the WINDOW's own median minus INK_MARGIN_L, not a fixed level:
    the cards are pearl and their fill sits anywhere from 236 to 245 L depending on
    the photograph under them, so a cap that works on one card merges the whole next
    card into a single band (the first version's 235 L cap did exactly that).

    Two widths are reported because they answer different questions. `extent` is
    first-to-last inked column — the width of a string, which is the scale-robust
    way to compare one label across two images. `run` is the longest CONTIGUOUS
    inked stretch, and it is what survives a line that carries a title on the left
    AND a right-aligned caption: that band measured 311 pt of extent for roughly 90
    pt of letters, and calling it 311 pt wide would compare a gap against a word.
    """
    x0, x1, y0, y1 = px(box, scale)
    seg = gray[y0:y1, x0:x1]
    if seg.size == 0:
        return []
    thr = float(np.percentile(seg, 50)) - INK_MARGIN_L
    dark = seg < thr
    per_row = dark.sum(axis=1)
    bands = []
    start = None
    for i, v in enumerate(per_row):
        if v > INK_MIN_COLS and start is None:
            start = i
        elif v <= INK_MIN_COLS and start is not None:
            if (i - start) / scale >= minh_pt:
                cols = np.where(dark[start:i].any(axis=0))[0]
                extent = (cols.max() - cols.min() + 1) / scale if cols.size else 0.0
                run = 0.0
                if cols.size:
                    best = cur = 1
                    for k in range(1, len(cols)):
                        cur = cur + 1 if cols[k] - cols[k - 1] == 1 else 1
                        best = max(best, cur)
                    run = best / scale
                bands.append(((y0 + start) / scale, (y0 + i) / scale,
                              (i - start) / scale, extent, run))
            start = None
    return bands


def row_tops(bands, gap_pt=ROW_CLUSTER_GAP_PT):
    """Cluster ink bands into ROWS: a title and its subtitle are one row, and rows
    are 43.5 pt apart. Returns (top, bottom, widest extent, widest run) per row."""
    rows = []
    for top, bottom, _h, extent, run in sorted(bands):
        if rows and top - rows[-1][1] < gap_pt:
            rows[-1] = (rows[-1][0], max(rows[-1][1], bottom),
                        max(rows[-1][2], extent), max(rows[-1][3], run))
        else:
            rows.append((top, bottom, extent, run))
    return rows


def rows_of(gray, probe, edge_pt, scale):
    """The rows of type inside the anchored card."""
    dx0, dx1 = probe["pitch_dx"]
    p0, p1 = probe["pitch_dy"]
    return row_tops(ink_bands(gray, (dx0, dx1, edge_pt + p0, edge_pt + p1), scale))


def page_rims(gray, span_x, scale, delta=3.0, win_pt=40.0):
    """Bright rows across the card width — the census the anchor chooses from."""
    x0, x1 = [int(round(v * scale)) for v in span_x]
    prof = gray[:, x0:x1].mean(axis=1)
    n = len(prof)
    half = int(round(win_pt * scale))
    raw = []
    for i in range(6, n - 6):
        if prof[i] != prof[i - 5:i + 6].max():
            continue
        lo, hi = max(0, i - half), min(n, i + half)
        above, below = prof[lo:max(lo + 1, i - 8)], prof[min(n, i + 8):hi]
        pool = np.concatenate([above, below])
        if pool.size and prof[i] - float(np.median(pool)) > delta:
            raw.append((i / scale, float(prof[i])))
    peaks = []
    for pt, v in raw:
        if peaks and pt - peaks[-1][0] < 8.0:
            if v > peaks[-1][1]:
                peaks[-1] = (pt, v)
            continue
        peaks.append((pt, v))
    return peaks


def expected_rim(probe, platform):
    """The table's expected edge for this platform; None means never measured."""
    value = probe["rim_y"]
    return value.get(platform) if isinstance(value, dict) else value


def anchor_edge(gray, probe, scale, platform="android", edge_pt=None, span=None):
    """The card's lit top edge — or (None, why) rather than a measurement of
    something that is not the card.

    Returns ((edge_pt, level, rows), None) on success. A rim search alone can land
    on any bright row: on the 2026-10-26 worktree it found a hairline at 325.7 pt
    and the probes then measured a rect holding no card at all and printed OK. So an
    edge is only accepted when it is bright AND carries rows of type below it.
    `span` overrides the expected window (--derive scans the whole page); `edge_pt`
    freezes the anchor, which the self-test needs because a doctored capture has no
    rim left to find.
    """
    expect = edge_pt if edge_pt is not None else expected_rim(probe, platform)
    if expect is None:
        return None, (f"no {platform} rim_y is recorded for {probe['about']}; "
                      f"run --derive on a {platform} render and commit its value")
    half = probe["rim_search"] if span is None else span
    cands = [(pt, v) for pt, v in page_rims(gray, probe["rim_span_x"], scale)
             if expect - half <= pt <= expect + half]
    if edge_pt is not None:
        rows = rows_of(gray, probe, edge_pt, scale)
        if len(rows) >= PITCH_MIN_ROWS:
            # The frozen anchor has no rim left to find (the self-test buried it),
            # so read the level at that exact row instead of searching for it —
            # searching is what would let the doctored copy hide a probe.
            x0, x1 = [int(round(v * scale)) for v in probe["rim_span_x"]]
            row = max(0, min(gray.shape[0] - 1, int(round(edge_pt * scale))))
            level = float(gray[row, x0:x1].mean())
            return (edge_pt, level, rows), None
        return None, (f"the frozen anchor {edge_pt:.1f}pt holds {len(rows)} row(s); "
                      f"{PITCH_MIN_ROWS} are needed to measure a rhythm")
    for pt, v in sorted(cands, key=lambda c: -c[1]):
        if v < ANCHOR_RIM_MIN_LEVEL:
            continue
        rows = rows_of(gray, probe, pt, scale)
        if len(rows) >= PITCH_MIN_ROWS:
            return (pt, v, rows), None
    detail = ", ".join(f"{pt:.1f}pt@{v:.1f}" for pt, v in cands[:5]) or "none"
    return None, (f"no card edge found near {expect:.1f}pt "
                  f"(+/-{half:.0f}pt); bright candidates: {detail}")


def pitch_of(rows):
    """Median top-to-top distance between rows of type."""
    tops = [r[0] for r in sorted(rows)]
    if len(tops) < 2:
        return None
    deltas = [tops[i + 1] - tops[i] for i in range(len(tops) - 1)]
    return float(np.median(deltas))


# --------------------------------------------------------------------------
# the shared prober


def card_box(probe, rim_pt):
    return (probe["card_dx"][0], probe["card_dx"][1],
            rim_pt + probe["card_dy"][0], rim_pt + probe["card_dy"][1])


def cta_box(probe, rim_pt):
    if not probe.get("cta_dx"):
        return None
    return (probe["cta_dx"][0], probe["cta_dx"][1],
            rim_pt + probe["cta_dy"][0], rim_pt + probe["cta_dy"][1])


def measure(a, gray, bd, probe, scale, pt_w, pt_h, bands, edge=None,
            platform="android"):
    """Every screen runs the same measurements off the same anchored edge."""
    rows_out = []
    found, why = anchor_edge(gray, probe, scale, platform=platform, edge_pt=edge)
    if found is None:
        fail(why + ". The layout moved — re-derive the table with --derive and "
                       "commit it WITH the layout change, never after it.")
    edge_pt, level, rows = found
    expect = expected_rim(probe, platform)

    box = card_box(probe, edge_pt)
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
    rows_out.append(("card", "residual", residual, mb,
                     (mb[0] <= residual <= mb[1]) if identifiable else None,
                     f"interior {interior:.1f} L, ground {ground_l:.1f} L, "
                     f"sensitivity {sensitivity:.1f} L"))
    rows_out.append(("card", "lift*", interior - ground_l,
                     (float("-inf"), float("inf")), True, "diagnostic only"))

    rim_level = level if level is not None else interior
    rb = bands["rim"]
    delta = edge_pt - expect
    rows_out.append(("card", "rim_excess", rim_level - interior, rb,
                     rb[0] <= rim_level - interior <= rb[1],
                     f"edge {edge_pt:.1f}pt (expected {expect:.1f}, "
                     f"delta {delta:+.1f})"))
    rows_out.append(("card", "rim_level", rim_level, RIM_LEVEL_BAND,
                     RIM_LEVEL_BAND[0] <= rim_level <= RIM_LEVEL_BAND[1],
                     "mock 243.9-249.4"))

    pitch = pitch_of(rows)
    in_band = pitch is not None and PITCH_BAND[0] <= pitch <= PITCH_BAND[1]
    rows_out.append(("row", "pitch_pt", pitch if pitch is not None else 0.0,
                     PITCH_BAND,
                     in_band if PITCH_BLOCKS else None,
                     f"{len(rows)} rows of type"
                     + ("" if PITCH_BLOCKS else " — ADVISORY, not blocking yet")))

    cta = cta_box(probe, edge_pt)
    if cta:
        cta_win = window(a, cta, scale)
        rb90 = float(np.percentile(cta_win[:, :, 0] - cta_win[:, :, 2],
                                   CTA_PERCENTILE))
        rows_out.append(("cta", f"R-B p{CTA_PERCENTILE}", rb90, CTA_BAND,
                         CTA_BAND[0] <= rb90 <= CTA_BAND[1],
                         "#FEB58D reads ~113"))
    return rows_out, edge_pt, rows


# --------------------------------------------------------------------------
# doctoring: the gate must be capable of failing


def doctor_self_test(a, gray, bd, probe, scale, pt_w, pt_h, bands, path,
                     platform="android"):
    """Paint the card flat onto its ground, bury the rim, desaturate the pill and
    cut 4 pt out of the rhythm above EACH of the last two rows — every gated probe
    must go red."""
    clean_rows, edge_pt, rows = measure(a, gray, bd, probe, scale, pt_w, pt_h,
                                        bands, platform=platform)
    clean = [r for r in clean_rows if r[4] is False]

    doctored = a.copy()
    h, w, _ = doctored.shape

    # 1. glass -> ground: the card with no fill at all.
    box = card_box(probe, edge_pt)
    cx0, cx1, cy0, cy1 = px(box, scale)
    doctored[cy0:cy1, cx0:cx1] = ground(bd, box, pt_w, pt_h).mean(axis=(0, 1))

    # 2. rim -> flat: bury EVERY row the search can reach, not a narrower band,
    #    or a render whose edge drifted would keep a bright row and the
    #    self-test would cry wolf at a capture that is fine.
    sx0, sx1 = [int(round(v * scale)) for v in probe["rim_span_x"]]
    expect = expected_rim(probe, platform)
    lo = max(0, int(round((expect - probe["rim_search"]) * scale)))
    hi = min(h, int(round((expect + probe["rim_search"]) * scale)))
    fill = float(np.median(window(a, box, scale).mean(axis=2)))
    doctored[lo:hi, sx0:sx1] = fill

    # 3. cta -> grey: a peach pill repainted as another glass capsule.
    cta = cta_box(probe, edge_pt)
    if cta:
        tx0, tx1, ty0, ty1 = px(cta, scale)
        m = doctored[ty0:ty1, tx0:tx1]
        doctored[ty0:ty1, tx0:tx1] = np.repeat(
            m.mean(axis=2, keepdims=True), 3, axis=2)

    # 4. rhythm -> tight. One cut is NOT enough, and that was measured rather than
    #    assumed: pitch is a median, so shortening a single gap leaves it unchanged
    #    (48.67 -> 48.67) and the probe would report itself unfailable for the wrong
    #    reason. Two cuts take a compliant 43.5 page to 39.5, and sitting below the
    #    frozen edge they leave the rim, the residual (+0.94 L) and the pill alone.
    tops = sorted(r[0] for r in rows)
    strip = int(round(4.0 * scale))
    for top in sorted(tops[2:], reverse=True):
        cut = int(round(top * scale))
        if cut - strip <= 0:
            continue
        doctored = np.vstack([doctored[:cut - strip], doctored[cut:],
                              np.repeat(doctored[-1:], strip, axis=0)])

    with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tf:
        Image.fromarray(doctored.astype(np.uint8)).save(tf.name)
        d_img = load_rgb(tf.name)
        doc = [r for r in measure(d_img, d_img.mean(axis=2), bd, probe, scale,
                                  pt_w, pt_h, bands, edge=edge_pt,
                                  platform=platform)[0]
               if r[4] is False]
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
    # residual is only demanded where the ground lets it see a difference, and the
    # rhythm only while it blocks; where either is not in force the run says so
    # instead of pretending it was tested.
    wanted = {("card", "rim_excess"), ("card", "rim_level")}
    if PITCH_BLOCKS:
        wanted.add(("row", "pitch_pt"))
    if any(m == "residual" and ok is not None for _, m, _, _, ok, _ in clean_rows):
        wanted.add(("card", "residual"))
    if probe.get("cta_dx"):
        wanted.add(("cta", f"R-B p{CTA_PERCENTILE}"))
    missing = wanted - fired
    if missing:
        print("SELF-TEST FAIL: doctoring left "
              + ", ".join(f"{p}/{m}" for p, m in sorted(missing))
              + " green — the gate cannot fail")
        return 1
    notes = []
    if not any(m == "residual" for p, m in wanted):
        notes.append("residual not identifiable on this ground")
    if not PITCH_BLOCKS:
        notes.append("pitch advisory")
    tail = f" ({'; '.join(notes)})" if notes else ""
    print(f"SELF-TEST PASS: {os.path.basename(path)} passes, and every gated "
          f"probe ({', '.join(m for _, m in sorted(wanted))}) can fail{tail}")
    return 0


# --------------------------------------------------------------------------
# --derive: re-calibration as a command


def sensitivity_at(bd, probe, scale, pt_w, pt_h, edge_pt, dy0, height=40.0):
    box = (probe["card_dx"][0], probe["card_dx"][1], edge_pt + dy0, edge_pt + dy0 + height)
    g = float(ground(bd, box, pt_w, pt_h).mean(axis=2).mean())
    return abs(FILL_LUMA - g) * COVERAGE, g


def suggest_cta_dy(a, probe, scale, edge_pt):
    """The peachest 70 pt window above the anchored edge — the pill's own height."""
    if not probe.get("cta_dx"):
        return None
    x0, x1 = [int(round(v * scale)) for v in probe["cta_dx"]]
    rb = (a[:, :, 0] - a[:, :, 2])
    win = int(round(70 * scale))
    best = (None, -1.0)
    for top in range(int(round(max(0.0, edge_pt - 420) * scale)),
                     int(round(max(1, edge_pt - 20) * scale)), int(round(10 * scale))):
        seg = rb[top:min(top + win, a.shape[0]), x0:x1]
        if seg.size == 0:
            continue
        v = float(np.percentile(seg, CTA_PERCENTILE))
        if v > best[1]:
            best = (top / scale + 35.0, v)
    if best[0] is None:
        return None
    return [round(best[0] - edge_pt - 35.0, 1), round(best[0] - edge_pt + 35.0, 1)]


def derive_one(a, gray, bd, name, probe, scale, pt_w, pt_h, platform):
    """One screen's candidate record, found by RHYTHM rather than by memory.

    The census is the whole page, never `rim_y +/- rim_search`: the point of
    re-deriving is that the committed expectation has moved. Brightness alone is
    not enough either — ranking the census by luma picked a closing card 240 pt
    below the one being measured — so a candidate must also carry at least
    `PITCH_MIN_ROWS` rows whose pitch is near the contract's, within the wider
    DERIVE tolerance (an off-rhythm page is exactly the page that needs deriving).
    """
    expect = expected_rim(probe, platform)
    lo, hi = (PITCH_BAND[0] + DERIVE_PITCH_TOL[0], PITCH_BAND[1] + DERIVE_PITCH_TOL[1])
    cands = []
    for pt, v in page_rims(gray, probe["rim_span_x"], scale):
        if v < ANCHOR_RIM_MIN_LEVEL:
            continue
        rows = rows_of(gray, probe, pt, scale)
        pitch = pitch_of(rows)
        if pitch is None or not (lo <= pitch <= hi):
            continue
        cands.append((pt, v, rows, pitch))
    rec = {"screen": name, "about": probe["about"],
           "expected_rim_y": expect,
           "candidates": [[round(pt, 1), round(v, 1), len(r), round(p, 2)]
                          for pt, v, r, p in cands][:6]}
    if not cands:
        rec["rim_y"] = None
        rec["note"] = ("no row of this page carries the contract's rhythm; the "
                       "layout or the type changed more than a probe can follow")
        return rec
    # Nearest the committed expectation wins; with no expectation, brightest.
    if expect is None:
        chosen = max(cands, key=lambda c: c[1])
    else:
        chosen = min(cands, key=lambda c: abs(c[0] - expect))
    pt, v, rows, pitch = chosen
    best = (None, -1.0)
    for dy0 in range(4, int(probe["pitch_dy"][1]) - 40, 10):
        sens, _g = sensitivity_at(bd, probe, scale, pt_w, pt_h, pt, dy0)
        if sens > best[1]:
            best = (dy0, sens)
    rec.update({
        "rim_y": round(pt, 1),
        "rim_level": round(v, 2),
        "moved_by": round(pt - expect, 1) if expect is not None else None,
        "rows": len(rows),
        "pitch_pt": round(pitch, 2),
        "card_dy": [best[0], best[0] + 40] if best[0] is not None else None,
        "sensitivity": round(best[1], 2) if best[0] is not None else None,
        "cta_dy": suggest_cta_dy(a, probe, scale, pt),
        "title_extent_pt": round(rows[0][2], 1),
        "title_run_pt": round(rows[0][3], 1),
    })
    return rec


def derive_report(a, gray, bd, scale, pt_w, pt_h, platform="android", screens=None):
    """Derive the screens named.

    One render holds ONE of these pages, so deriving all three from it would
    happily report the hub's Personalization card as the Profile card's edge —
    which is what the first version did. The caller names the screen; the render
    is the one that page produced.
    """
    return {name: derive_one(a, gray, bd, name, SCREENS[name], scale, pt_w, pt_h,
                             platform)
            for name in (screens or SCREENS)}


def screens_snippet(report, platform):
    """The derive output as the thing it describes: an executable SCREENS entry.

    Re-calibration by transcription is how a table drifts from the layout it
    describes; this is copy-paste instead.
    """
    lines = [f"# derived from a {platform} render; rim_y is per platform — keep "
             f"the other side's value"]
    for name, rec in report.items():
        probe = SCREENS[name]
        other = {k: v for k, v in probe["rim_y"].items() if k != platform}
        shown = {platform: rec["rim_y"], **other}
        lines.append(f'    "{name}": {{  # {probe["about"]}'
                     + (f', moved {rec["moved_by"]:+.1f} pt' if rec.get("moved_by")
                        is not None else '') + "}")
        lines.append(f'        "about": "{probe["about"]}",')
        lines.append(f'        "rim_y": {shown},')
        for key in ("rim_search", "rim_span_x", "pitch_dx", "pitch_dy", "card_dx"):
            lines.append(f'        "{key}": {probe[key]},')
        if rec["rim_y"] is not None and rec["card_dy"]:
            lines.append(f'        "card_dy": {tuple(rec["card_dy"])},  '
                         f'# sens {rec["sensitivity"]} L')
        else:
            lines.append(f'        "card_dy": {tuple(probe["card_dy"])},  '
                         f'# unchanged: nothing in this page is identifiable')
        lines.append(f'        "cta_dx": {probe["cta_dx"]},')
        cta = rec["cta_dy"] if rec["cta_dy"] else probe["cta_dy"]
        lines.append(f'        "cta_dy": {tuple(cta) if cta else None},')
        lines.append("    },")
    lines.append("}")
    return "\n".join(lines)


def mock_advisory(a, gray, bd, probe, scale, pt_w, pt_h, mock_path, geo, platform):
    """Ink comparison against a reference mock at a PROVEN common scale.

    The mock's px/pt is not trusted: the crop and the contract's pxPerPt are read
    from the same file that produced every other number here, and the row pitch is
    reported on both sides so a scale error shows up as a pitch ratio of 1.0 while
    the widths disagree. Advisory only — the mock's canvas is 0.441 against the
    device's 0.4613, a 5.7 % vertical stretch, so its vertical ratios are not
    transferable and its ink HEIGHTS are not gated anywhere.
    """
    crop = geo.get("screenCrop") or [186, 18, 836, 1492]
    m = np.asarray(Image.open(mock_path).convert("RGB")).astype(float)
    mc = m[crop[1]:crop[3], crop[0]:crop[2]]
    gray_m = mc.mean(axis=2)
    m_w_pt = (crop[2] - crop[0]) / float(geo.get("pxPerPt", 1.654))
    m_scale = mc.shape[1] / m_w_pt          # px per pt inside the crop
    found, why = anchor_edge(gray, probe, scale, platform=platform)
    if found is None:
        # The advisory exists to be useful exactly when the table is stale, so
        # fall back to the edge this render actually carries.
        rec = derive_one(a, gray, bd, "", probe, scale, pt_w, pt_h, platform)
        if rec.get("rim_y") is None:
            print(f"mock advisory: render side unanchored ({why})")
            return
        print(f"mock advisory: table is stale ({why}); using the derived edge "
              f"{rec['rim_y']}pt")
        r_rows = rows_of(gray, probe, rec["rim_y"], scale)
    else:
        r_rows = found[2]
    dx0, dx1 = probe["pitch_dx"]
    p0, p1 = probe["pitch_dy"]
    lo, hi = PITCH_BAND[0] + DERIVE_PITCH_TOL[0], PITCH_BAND[1] + DERIVE_PITCH_TOL[1]
    m_cands = []
    for pt, v in page_rims(gray_m, (dx0, dx1), m_scale):
        if v < ANCHOR_RIM_MIN_LEVEL:
            continue
        rows = row_tops(ink_bands(gray_m, (dx0, dx1, pt + p0, pt + p1), m_scale))
        pitch = pitch_of(rows)
        if pitch is not None and lo <= pitch <= hi:
            m_cands.append((pt, rows, pitch))
    if not m_cands:
        print("mock advisory: no card in the mock carries the contract's rhythm")
        return
    # The mock's card is the one whose row count matches the render's, nearest the
    # render's expected edge; on a stretched canvas absolute y means nothing.
    m_edge, m_rows, m_pitch = min(
        m_cands, key=lambda c: (abs(len(c[1]) - len(r_rows)), c[0]))
    print(f"\n--- ink advisory vs {os.path.basename(mock_path)} "
          f"(mock {m_w_pt:.0f}pt @ {m_scale:.2f}px/pt, render {pt_w}pt @ {scale:.2f}) ---")
    print(f"{'row':<5}{'mock extent':>12}{'ours extent':>12}{'ratio':>7}   "
          f"{'mock run':>9}{'ours run':>9}{'ratio':>7}")
    for i in range(min(len(m_rows), len(r_rows))):
        me, oe = m_rows[i][2], r_rows[i][2]
        mr, orr = m_rows[i][3], r_rows[i][3]
        print(f"{i + 1:<5}{me:>12.1f}{oe:>12.1f}{oe / me:>7.2f}   "
              f"{mr:>9.1f}{orr:>9.1f}{orr / mr:>7.2f}")
    rp = pitch_of(r_rows)
    print(f"pitch: mock {m_pitch:.2f}pt, ours {rp:.2f}pt, ratio {rp / m_pitch:.2f}")
    print("advisory: the mock's canvas is stretched 5.7% vertically, so its "
          "heights and y positions are context; its widths and pitch are the "
          "contract")


# --------------------------------------------------------------------------


def print_rows(rows, label, render, pt_w, pt_h, scale, probe):
    print(f"\n=== {label}: {os.path.basename(render)} "
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
    return bad


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
    ap.add_argument("--platform", choices=("android", "ios"), default="android",
                    help="which side's rim_y the table resolves for; the two "
                         "platforms' safe areas put the same card apart")
    ap.add_argument("--label", default="")
    ap.add_argument("--self-test", action="store_true")
    ap.add_argument("--derive", action="store_true",
                    help="print the candidate SCREENS table from this render")
    ap.add_argument("--mock", help="reference mock PNG; prints an advisory ink "
                                   "comparison at a proven common scale")
    args = ap.parse_args()

    bands = {"material": MATERIAL_BAND, "rim": (6.0, RIM_EXCESS_CAP)}
    geo = {}
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
    gray = a.mean(axis=2)
    bd, bd_path = load_backdrop(args.backdrop)
    pt_w, pt_h = point_size(args.render, args.pt, a.shape[1])
    scale = a.shape[1] / pt_w
    # The exact command that reproduces this run's measurement, so a log is enough
    # to fix the page without the machine that made it.
    repro = (f"python3 scripts/check_settings_render.py --render "
             f"{args.render} --screen {args.screen} --platform {args.platform}"
             + (f" --backdrop {args.backdrop}" if args.backdrop else ""))

    if args.derive:
        report = derive_report(a, gray, bd, scale, pt_w, pt_h, args.platform,
                               screens=[args.screen])
        print(json.dumps(report, indent=2))
        print("\n# paste-ready:")
        print(screens_snippet(report, args.platform))
        if args.mock:
            mock_advisory(a, gray, bd, probe, scale, pt_w, pt_h, args.mock, geo,
                          args.platform)
        return 0

    if args.self_test:
        return doctor_self_test(a, gray, bd, probe, scale, pt_w, pt_h, bands,
                                args.render, platform=args.platform)

    # One reporter for "this is not the card": measure() would exit inside fail()
    # with no context, and a stale table is the one failure a reader can fix in a
    # second, given the derived record.
    found, why = anchor_edge(gray, probe, scale, platform=args.platform)
    if found is None:
        print(f"ERROR: {why}")
        print(f"\n# this screen's derive record — {repro}")
        print(json.dumps(derive_one(a, gray, bd, args.screen, probe, scale,
                                    pt_w, pt_h, args.platform), indent=2))
        print("commit the table WITH the layout change, never after it")
        return 2

    rows, edge_pt, type_rows = measure(a, gray, bd, probe, scale, pt_w, pt_h,
                                       bands, platform=args.platform)
    bad = print_rows(rows, args.label or args.screen, args.render, pt_w, pt_h,
                     scale, probe)
    if bad:
        print(f"\n{bad} probe(s) drifted — the mock's numbers are the contract; "
              f"ground read from {os.path.basename(bd_path)}")
        print("candidate card edges on this page (pt @ luma): "
              + ", ".join(f"{p:.1f}@{v:.1f}"
                          for p, v in page_rims(gray, probe["rim_span_x"], scale)
                          if abs(p - (expected_rim(probe, args.platform) or 0)) <= 60))
        print(f"\n# derive record for this screen — {repro} --derive")
        print(json.dumps(derive_one(a, gray, bd, args.screen, probe, scale,
                                    pt_w, pt_h, args.platform), indent=2))
        print("re-derive the table, then commit it WITH the layout change, "
              "never after it")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
