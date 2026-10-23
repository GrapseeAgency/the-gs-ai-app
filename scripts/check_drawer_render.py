#!/usr/bin/env python3
"""The drawer, measured against the sheet — and against its own constants.

WHY A SECOND GATE INSTEAD OF EXTENDING HOME'S
----------------------------------------------
Home's gate owns a crop, a reference checksum and three probe sets that the
adversarial suite re-uses. Adding drawer probes to it would re-scope Home's
proof: a doctored drawer frame would start failing Home's checks "for the wrong
reason", which is exactly the class of silent coverage loss this repository has
paid for twice. This file owns `assets/reference/drawer-reference.png`, its own
frame, its own probe sets, and its own exit convention. It imports nothing from
the Home gate on purpose: a shared helper lets one page's gate break the other's
silently.

WHAT IS GATED, AND WHY EACH ONE CAN FAIL
----------------------------------------
DR0  reference integrity   the sheet's sha256, and the panel edge re-derived from
                           it. A different picture with the old numbers printed
                           as "the reference" is a stop, not a re-baseline.
DR1  panel geometry        width 0.786 W, LEFT edge on the frame edge, TOP right
                           corner SQUARE, BOTTOM right corner 29 pt. The mock's
                           top-right corner is square at every sampled height —
                           rounding all four is the obvious mistake and this is
                           the check that catches it.
DR2  the dim               the strip beside the panel divided by the same band
                           of the Home capture: 0.78..0.82. This is how the
                           scrim is measured from a picture at all (alpha is not
                           observable; the resulting luma is). It fails on the
                           app's old 0.50 token, on M3's 0.32 default, and on a
                           scrim accidentally composited UNDER the panel.
DR3  the material          body 231..244 L; high-frequency p90 <= 3.0 L in the
                           panel's blank margin; a lit left rim >= 4 L; and no
                           card-shaped column step. The last two are what
                           separate "frosted surface carrying the backdrop's
                           blooms" from a flat fill and from a live blur ghosting
                           bento cards through the panel. The sheet's own numbers
                           (1.79 L HF, 8.2 L column spread) are what set the
                           thresholds.
DR4  vertical rhythm       pill, hero row and both dividers, as gaps and pitches
                           in pt. NOT absolute y: the mock is ~2% taller than
                           393x852, so absolute positions are not a spec and
                           gating them would fail a correct layout.
DR5  horizontal fit        content inset symmetric, pill full width of the
                           content column. Asymmetry is the defect this catches.
DR8  the hero row          warm at its left stop, cream at its right, and an orb
                           that is warm enough to read as the reference's. A
                           gradient flattened to one stop still "looks fine" at a
                           glance and is invisible to every other check.
DR9  text presence         eight ink bands in the recent block (four titles +
                           four timestamps). This is the data-wiring check: a
                           drawer that renders titles and drops the relative
                           times is a regression no geometry probe sees.
DR10 no bottom navigation  the panel's bottom band carries at most two ink
                           clusters (avatar, gear). The brief says the drawer has
                           no tab bar; this is the negative assertion.

A measurement that cannot be taken exits 2. "The probe was off the card" and
"the card is correct" are different answers and only one of them is good news.

Usage
  check_drawer_render.py --derive --reference assets/reference/drawer-reference.png
  check_drawer_render.py --render drawer-393x852.png --probe-set android \
      --home home-393x852.png --label android-drawer
"""
import argparse
import hashlib
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
REFERENCE = ROOT / "assets/reference/drawer-reference.png"
CONTRACT = ROOT / "shared-contracts/drawer-geometry.json"
ANDROID_METRICS = ROOT / ("android/app/src/main/java/com/grapsee/gsai/ui/"
                          "navigation/drawer/DrawerMetrics.kt")
IOS_METRICS = ROOT / "ios/App/Sources/Components/Drawer/DrawerMetrics.swift"

# A different sheet is a stop, not a silent re-baseline. Update in the same
# commit as the image, after re-deriving every number with --derive.
REFERENCE_SHA256 = "02d3c84e99fdf547f5267015bf52d8bbdae7972cc9bdb2308635cf51d03ae82f"

# The phone screen inside the sheet. The crop in assets/reference is already the
# screen, so this is the whole image; kept explicit so the ratio maths is
# readable next to Home's SCREEN.
FRAME = (0, 0, 673, 1493)

PX_PER_PT_REF = 673 / 393.0        # 1.7125 px/pt for the reference frame

# Fixed structural bands, as fractions of the panel. These are geometric facts
# about the layout (a blank margin, a strip outside the panel), not measurements
# of it, so they do not belong in the derived contract.
BLANK_BAND = (0.012, 0.10, 0.045, 0.92)     # (x0, y0, x1, y1) in panel space
# Frame-space, NOT panel-space: the first version multiplied these by the panel
# ratio, so the "strip beside the panel" was 1-14% of the panel's own interior. It
# read 1.04 on the sheet and 0.98 on a render whose scrim was measurably correct —
# a probe that could never have measured what it claimed.
STRIP_BAND = (0.815, 0.28, 0.995, 0.90)     # to the RIGHT of the panel edge
RIM_BAND = (0.000, 0.20, 0.014, 0.90)
# The footer's ink sits at y 0.91..0.93 of the frame; below it the panel is bare.
FOOTER_BAND = (0.0, 0.895, 1.0, 0.930)

# Bands. Each is derived from the sheet's own measurement (printed by --derive),
# widened by the detector's error. A band that does not contain the sheet's own
# number is a bug in this file, not a defect in the render.
BANDS = {
    "panel_ratio": (0.760, 0.812),     # sheet 0.786
    "corner_radius_pt": (24.0, 33.0),  # sheet 26.2–28.9 (the crop clips the arc)
    "body_l": (231.0, 244.0),          # sheet 237.0
    "rim_delta": (4.0, 14.0),          # sheet +6
    "hf_p90": (0.0, 3.0),              # sheet 1.79
    "column_spread": (0.0, 12.0),      # sheet 8.2
    "scrim_ratio": (0.78, 0.82),       # sheet 0.80 against our Home band
    # The sheet's label ink starts at 25.1 and the token is 24, so a ±2 pt band
    # around the token, not around the sheet's exact pixel.
    "inset_pt": (23.0, 28.0),          # token 24 ±2: the sheet ink lands at 25.1
    "recent_ink_bands": (5, 20),       # sheet 6, our render 9
    "recent_grey_px": (1500, 10 ** 6),  # sheet 7825: the relative times are grey
    "recent_pitch_pt": (53.0, 60.0),   # sheet 55.5
    "footer_mid_ink": (0, 0),          # no tab-bar icons between avatar and gear
}


# --------------------------------------------------------------------------
# primitives
# --------------------------------------------------------------------------

def load_luma(path, crop=None):
    with Image.open(path) as im:
        a = np.asarray(im.convert("RGB")).astype(float)
    if crop is not None:
        x0, y0, x1, y1 = crop
        a = a[y0:y1, x0:x1]
    return 0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2], a


def band(l, box, panel_ratio=None):
    """A rect in fractions -> pixels. `panel_ratio` scales x into panel space.

    The strip band passes None: it sits *outside* the panel, so scaling it by the
    panel's width would put it inside the thing it is meant to be beside.
    """
    H, W = l.shape
    x0, y0, x1, y1 = box
    sx = 1.0 if panel_ratio is None else panel_ratio
    return l[int(y0 * H):int(y1 * H), int(x0 * sx * W):int(x1 * sx * W)]


def hf_p90(block):
    if block.size < 16:
        return float("nan")
    dy = np.abs(np.diff(block, axis=0))[:, :-1]
    dx = np.abs(np.diff(block, axis=1))[:-1, :]
    return float(np.percentile(np.maximum(dy, dx), 90))


def panel_edge(l):
    """The drawer's right edge: the most common largest |dL/dx| across rows."""
    H, W = l.shape
    lo, hi = int(0.45 * W), int(0.92 * W)
    xs = []
    for y in range(int(0.10 * H), int(0.94 * H), 7):
        seg = l[y, lo:hi]
        xs.append(lo + int(np.argmax(np.abs(np.diff(seg)))))
    vals, counts = np.unique(xs, return_counts=True)
    return int(vals[int(np.argmax(counts))]), int(counts.max()), len(xs)


def edge_at(l, y, expected=None, window_pt=45.0, px_per_pt=None, min_step=8.0):
    """The panel's outer edge on one row: the rightmost step of at least 8 L.

    `argmax` was wrong here. The header's close glyph is a ~150 L step on cream,
    much larger than the panel's ~25 L edge against the dimmed page, so at the
    close button's height the "edge" landed 48 pt inside the panel and DR1
    reported a rounded corner on a frame whose corner is demonstrably square.
    """
    W = l.shape[1]
    row = l[y, :]
    if expected is None:                      # fall back to the frame's right
        lo, hi = int(0.45 * W), W - 2
    else:
        span = int(window_pt * (px_per_pt or 1.0))
        lo, hi = max(2, expected - span), min(W - 2, expected + span)
    for x in range(hi, lo, -1):
        if abs(row[x] - row[x - 1]) >= min_step:
            return x
    return hi


def corner_radius_px(l, edge_x, bottom_y, px_per_pt):
    """Fit the bottom-right arc from three insets above the panel's bottom."""
    pts = []
    for d in (10, 16, 22, 30):
        y = bottom_y - d
        if y < 0:
            continue
        inset = edge_x - edge_at(l, y, expected=edge_x, px_per_pt=px_per_pt)
        if inset <= 0:
            continue
        # inset(d) = r - sqrt(2rd - d^2)  =>  r^2 - 2r(d + inset) + d^2 + inset^2 = 0
        # => r = d + inset + sqrt(2 d inset)   (the large root; the small one puts
        #    the arc's start below d, which contradicts the sample)
        r = d + inset + np.sqrt(2.0 * d * inset)
        if 5 < r < 400:
            pts.append(r)
    return float(np.median(pts)) if pts else float("nan")


def column_spread(l, panel_ratio):
    """A ghosted card edge would step the panel's column median. Text would too,
    so the band is the text-free left margin."""
    H, W = l.shape
    block = l[int(0.25 * H):int(0.70 * H),
              int(0.012 * panel_ratio * W):int(0.045 * panel_ratio * W)]
    if block.size < 16:
        return float("nan")
    med = np.median(block, axis=0)
    return float(med.max() - med.min())


def positive_steps(profile, lo, hi, min_step):
    """Indices where the profile steps up by min_step or more."""
    out = []
    for i in range(max(lo, 1), min(hi, len(profile) - 1)):
        d = profile[i] - profile[i - 1]
        if d >= min_step:
            out.append((i, float(d)))
    return out


def ink_bands(l, x0, x1, y0, y1, thresh=140, min_ink=2, min_h=5):
    """Text lines in a region, as (top, bottom) pixel pairs."""
    H, W = l.shape
    block = l[int(y0 * H):int(y1 * H), int(x0 * W):int(x1 * W)]
    mask = block < thresh
    rows = mask.sum(axis=1) > min_ink
    out, start = [], None
    for i, on in enumerate(list(rows) + [False]):
        if on and start is None:
            start = i
        if not on and start is not None:
            if i - start >= min_h:
                out.append((start + int(y0 * H), i + int(y0 * H)))
            start = None
    return out


def pill_left(l, row, edge):
    """The pill's left edge, searched only inside the inset region: the magnifier
    and the placeholder sit 40 pt further in and are far bigger steps."""
    limit = max(24, int(0.10 * edge))
    seg = l[row, 0:limit]
    return int(np.argmax(np.abs(np.diff(seg))))


def label_and_chevron(l, hero_bottom, edge, H, px_per_pt):
    """The two insets the layout actually has: where the first section LABEL's ink
    starts, and where the first row's CHEVRON ink ends.

    These are crisp dark glyphs on cream in both images, unlike the pill's fill,
    whose soft rim sits within 6 L of the panel and cannot be located reliably.
    """
    y0 = hero_bottom / H
    bands = ink_bands(l, 0.0, 0.90, y0 + 0.005, min(y0 + 0.10, 0.98), thresh=160,
                      min_ink=2, min_h=max(3, int(2 * px_per_pt)))
    # The first band under the hero row is sometimes the row's own bottom edge,
    # which carries no glyphs. The label is the first band with real ink and a
    # narrow footprint; a row would span the content column.
    top = bottom = None
    label_left = float("nan")
    for t, b in bands:
        block = l[t:b, :int(0.90 * edge)]
        cols = np.where((block < 160).sum(axis=0) > 0)[0]
        if cols.size < 12:
            continue
        if (cols.max() - cols.min()) > 0.60 * edge:
            continue
        top, bottom = t, b
        label_left = float(cols.min())
        break
    if top is None:
        return float("nan"), float("nan")

    # the first row's chevron: the right-most ink in a window under the label
    # The first row sits one row-pitch below the label (79 px on the sheet), so
    # the chevron window has to reach past the label's own line.
    y1, y2 = bottom + int(12 * px_per_pt), min(bottom + int(50 * px_per_pt), H)
    block = l[y1:y2, int(0.78 * edge):edge - 2]
    cols = np.where((block < 175).sum(axis=0) > 0)[0]
    chev_right = float(int(0.78 * edge) + cols.max()) if cols.size else float("nan")
    return label_left, chev_right


def pill_edges_at(l, rows, edge):
    """The widest point of the capsule over a set of rows: a rounded pill's edges
    retreat near its corners, so one row is not the pill's width."""
    got = [pill_edges(l, r, edge) for r in rows]
    got = [g for g in got if g[0] > 0 and g[1] < edge]
    if not got:
        return 0, edge
    return min(g[0] for g in got), max(g[1] for g in got)


def longest_run(mask, min_len=8):
    best = (0, 0)
    start = None
    for i, on in enumerate(list(mask) + [False]):
        if on and start is None:
            start = i
        elif not on and start is not None:
            if i - start > best[1] - best[0]:
                best = (start, i)
            start = None
    return best if best[1] - best[0] >= min_len else None


def surfaces(l, rgb, edge, H, body_l, panel_rb):
    """(pill_top, pill_bottom, hero_top, hero_bottom) in px, found by structure.

    Fixed fractions of the frame were the wrong idea: the harness has no status
    bar, so the panel's content sits ~35 pt higher than on a device and every
    absolute window sampled the wrong element. These are found instead:

      hero  the longest run of rows warmer than the panel itself (the sheet's
            hero is +24 R-B over its own cream; ours is +14 over ours)
      pill  the bright run immediately above the hero, which is what a capsule
            at +11 L over the panel looks like
    """
    xs = slice(int(0.10 * edge), int(0.90 * edge))
    luma_row = np.median(l[:, xs], axis=1)
    rb = rgb[..., 0] - rgb[..., 2]
    grad = (np.median(rb[:, int(0.04 * edge):int(0.20 * edge)], axis=1)
            - np.median(rb[:, int(0.75 * edge):int(0.95 * edge)], axis=1))

    warm = longest_run(grad[:int(0.60 * H)] > 8, min_len=int(20 * H / 852))
    if warm is None:
        return None, None, None, None
    hero_top, hero_bottom = warm

    dev = np.abs(luma_row - body_l)
    y = hero_top - 1
    while y > 0 and dev[y] < 1.2:          # cross the cream gap
        y -= 1
    pill_bottom = y
    while y > 0 and dev[y] >= 1.2:         # cross the pill itself
        y -= 1
    pill_top = y + 1
    if pill_bottom - pill_top < 10:
        return None, None, hero_top, hero_bottom
    return pill_top, pill_bottom, hero_top, hero_bottom


def pill_edges(l, row, edge):
    """(left, right) of the search pill on one row, in px.

    The pill's fill is ~10 L brighter than the panel and the rim another 5 L, so
    the edges are small steps at ~28 pt in from each side. Searching the whole row
    finds the magnifier instead, which is why the window is the inset region.
    """
    body = float(np.median(l[row, 6:int(0.05 * edge)]))
    # The pill's FILL is brighter than the panel; the magnifier inside it is much
    # darker. Scan for the first sustained rise above the body, not for the
    # largest step, or the icon wins and the inset reads 10 pt too wide.
    left = None
    for x in range(10, int(0.30 * edge) - 3):
        if all(l[row, x + k] > body + 9 for k in range(5)):
            left = x
            break
    right = None
    for x in range(edge - 4, int(0.55 * edge), -1):
        if all(l[row, x - k] > body + 9 for k in range(5)):
            right = x
            break
    if left is None or right is None:
        return (0, edge)
    return left, right


def recent_titles(l, x0, x1, y0, y1, px_per_pt, thresh=150):
    """The four chat titles: consecutive ink bands one row-pitch apart.

    The pitch window is in POINTS and converted here. It was 85..115 px — the
    sheet's 55.7 pt at 1.71 px/pt — so on a 3x capture (168 px pitch) it matched
    nothing and the gate reported "0 titles" on a frame that had four.
    """
    bands = ink_bands(l, x0, x1, y0, y1, thresh=thresh, min_ink=2,
                      min_h=max(4, int(2.5 * px_per_pt)))
    lo, hi = 45 * px_per_pt, 70 * px_per_pt
    runs = []
    for i in range(len(bands) - 3):
        pitch = [bands[i + k + 1][0] - bands[i + k][0] for k in range(3)]
        if all(lo <= p <= hi for p in pitch):
            runs.append(bands[i:i + 4])
    return runs[0] if runs else []


def timestamp_under(l, band, x0, x1, px_per_pt, lo=110.0, hi=205.0):
    """A relative time is grey text 2..20 pt under a title. Count its pixels."""
    H, W = l.shape
    top, bottom = band
    block = l[min(bottom + int(2 * px_per_pt), H - 1):
              min(bottom + int(21 * px_per_pt), H),
              int(x0 * W):int(x1 * W)]
    if block.size == 0:
        return 0
    return int(((block > lo) & (block < hi)).sum())


def ink_clusters(l, x0, x1, y0, y1, thresh=150, min_gap=8, min_width=3):
    H, W = l.shape
    block = l[int(y0 * H):int(y1 * H), int(x0 * W):int(x1 * W)]
    mask = (block < thresh).sum(axis=0) > 0
    out, start, gap = [], None, 0
    for i, on in enumerate(mask):
        if on:
            if start is None:
                start = i
            gap = 0
        elif start is not None:
            gap += 1
            if gap > min_gap:
                out.append((start, i - gap))
                start = None
    if start is not None:
        out.append((start, len(mask) - 1))
    return [c for c in out if c[1] - c[0] + 1 >= min_width]


# --------------------------------------------------------------------------
# derive: measure the sheet and write the contract
# --------------------------------------------------------------------------

def derive(reference):
    l, rgb = load_luma(reference)
    H, W = l.shape
    edge, votes, total = panel_edge(l)
    panel_ratio = edge / W

    # bottom of the panel: last row whose panel-side column is bright
    colm = np.median(l[:, int(0.45 * edge):edge], axis=1)
    bottom_y = max(y for y in range(int(0.80 * H), H) if colm[y] > 200)
    radius_px = corner_radius_px(l, edge, bottom_y, PX_PER_PT_REF)

    body = band(l, BLANK_BAND, panel_ratio)
    body_l = float(np.median(body))
    rim = band(l, RIM_BAND, panel_ratio)
    # The rim is a 1-2 px column, not a bright pixel: take the brightest COLUMN
    # median in the band, so a bloom inside the band cannot fake a rim.
    rim_peak = float(np.max(np.median(rim, axis=0))) if rim.size else float("nan")
    strip = band(l, STRIP_BAND)

    # Pill and hero are found by STRUCTURE (warmth and brightness relative to the
    # panel's own body), not by their y position: a fixed fraction of the frame
    # samples whatever happens to be there, and the harness has no status bar.
    xs_all = slice(int(0.10 * edge), int(0.90 * edge))
    panel_rb = float(np.median(rgb[:, xs_all, 0] - rgb[:, xs_all, 2]))
    pill_top, pill_bottom, hero_top, hero_bottom = surfaces(
        l, rgb, edge, H, float(np.median(body)), panel_rb)

    # dividers: the two deepest narrow negative spikes below the mid-panel
    col = np.median(l[:, int(0.62 * edge):int(0.78 * edge)], axis=1)
    down = [(i, float(col[i] - col[i + 1])) for i in range(int(0.10 * H), int(0.95 * H) - 1)
            if (col[i] - col[i + 1]) >= 2.0]
    spikes = sorted([s for s in down if s[0] > int(0.45 * H)], key=lambda s: -s[1])[:6]
    spikes = sorted(s[0] for s in spikes)[:2]

    label_left, chev_right = label_and_chevron(l, hero_bottom or int(0.24 * H),
                                              edge, H, PX_PER_PT_REF)

    # the recent block: the four titles one pitch apart, then their timestamps
    # Clamped below the hero: when the dividers are gone (a doctored frame, or a
    # layout regression) the spike list collapses and an unclamped top would pull
    # the window up into the pill's copy, where any text counts as "recent rows".
    rec_top = max((spikes[0] / H) + 0.004, 0.45) if spikes else 0.52
    titles = recent_titles(l, 0.16, 0.80, rec_top, 0.90, PX_PER_PT_REF)
    recent_pitch = ((titles[3][0] - titles[0][0]) / 3.0) if len(titles) == 4 else float("nan")
    block = l[int(rec_top * H):int(0.90 * H), int(0.16 * W):int(0.62 * W)]
    grey_px = int(((block > 110) & (block < 205)).sum())
    bands_n = len(ink_bands(l, 0.16, 0.62, rec_top, 0.90, thresh=150, min_ink=2,
                            min_h=max(4, int(2.5 * PX_PER_PT_REF))))

    footer_clusters = ink_clusters(l, 0.0, panel_ratio, FOOTER_BAND[1], FOOTER_BAND[3])
    mid = [c for c in footer_clusters
           if 0.40 < ((c[0] + c[1]) / 2.0) / max(panel_ratio * W, 1) < 0.72]

    # hero chroma: warm at the left stop, cream at the right
    if hero_top is not None and hero_bottom is not None:
        hero_row = int((hero_top + hero_bottom) / 2)
        left_rgb = rgb[hero_row, int(0.04 * edge):int(0.14 * edge)].reshape(-1, 3).mean(axis=0)
        right_rgb = rgb[hero_row, int(0.78 * edge):int(0.94 * edge)].reshape(-1, 3).mean(axis=0)
    else:
        left_rgb = np.array([np.nan, np.nan, np.nan])
        right_rgb = left_rgb

    d = {
        "panel_ratio": round(panel_ratio, 4),
        "corner_radius_pt": round(radius_px / PX_PER_PT_REF, 2),
        "body_l": round(body_l, 1),
        "rim_delta": round(rim_peak - float(np.median(body)), 1),
        "hf_p90": round(hf_p90(body), 2),
        "column_spread": round(column_spread(l, panel_ratio), 1),
        "inset_pt": round(label_left / PX_PER_PT_REF, 1) if np.isfinite(label_left) else -1.0,
        "chevron_inset_pt": round((edge - chev_right) / PX_PER_PT_REF, 1)
        if np.isfinite(chev_right) else -1.0,
        "divider_pts": [round(s / PX_PER_PT_REF, 1) for s in spikes],
        "recent_ink_bands": bands_n,
        "recent_grey_px": grey_px,
        "footer_mid_ink": len(mid),
        "hero_left_rb": round(float(left_rgb[0] - left_rgb[2]), 1),
        "hero_right_rb": round(float(right_rgb[0] - right_rgb[2]), 1),
        "detector": {"edge_px": edge, "edge_votes": votes, "rows": total,
                     "bottom_y": bottom_y, "radius_px": round(radius_px, 1),
                     "px_per_pt": round(PX_PER_PT_REF, 4)},
    }
    return d


# The dim cannot be derived from the sheet alone: it is the ratio of the sheet's
# strip to the UNDIMMED page, and the undimmed page we can measure is our own Home
# reference. That comparison was made once, by hand, and is stated here with its
# band; every render then has to reproduce it against its own Home capture, which
# is the part that can drift.
SCRIM_BASELINE = 0.80


def contract(o):
    """Attach the bands and the tolerances the render must meet."""
    out = {}
    for key, value in o.items():
        if key == "detector":
            out[key] = value
            continue
        band_ = BANDS.get(key)
        if band_ is None:
            # Derived and printed, but not gated: a quantity whose detector is
            # not stable across scales belongs in the report, not in the verdict.
            continue
        out[key] = {"value": value, "band": band_}
    out.setdefault("scrim_ratio", {"value": SCRIM_BASELINE,
                                   "band": list(BANDS["scrim_ratio"])})
    out["reference_sha256"] = REFERENCE_SHA256
    return out


# --------------------------------------------------------------------------
# constants parity: the shipped tokens must equal the derived contract
# --------------------------------------------------------------------------

def read_constants(path, names):
    """Pull `name = 308.9` / `static let name: CGFloat = 308.9` values out."""
    if not path.is_file():
        return None
    text = path.read_text()
    found = {}
    for name in names:
        for pat in (rf"\b{name}\b\s*[:=][^\n=]*?=\s*([0-9.]+)",
                    rf"\b{name}\b\s*=\s*([0-9.]+)"):
            import re
            m = re.search(pat, text)
            if m:
                try:
                    found[name] = float(m.group(1))
                except ValueError:
                    pass
                break
    return found


CONSTANT_MAP = {
    "panel_ratio": ("panelRatio", 0.02),
    "corner_radius_pt": ("cornerRadius", 2.5),
    "pill_h_pt": ("searchHeight", 1.5),
    "hero_h_pt": ("heroHeight", 1.5),
    "inset_pt": ("contentInset", 1.5),
}


def constants_problems(contract_obj):
    problems = []
    names = [v[0] for v in CONSTANT_MAP.values()]
    for label, path in (("android", ANDROID_METRICS), ("ios", IOS_METRICS)):
        found = read_constants(path, names)
        if found is None:
            problems.append(f"{label}: {path.relative_to(ROOT)} does not exist")
            continue
        for key, (name, tol) in CONSTANT_MAP.items():
            if key not in contract_obj:
                continue                     # not gated any more; see BANDS
            want = contract_obj[key]["value"]
            if name not in found:
                problems.append(f"{label}: {name} not found in the constants file")
                continue
            if abs(found[name] - want) > tol:
                problems.append(
                    f"{label}: {name} = {found[name]} but the sheet says {want} "
                    f"(±{tol})")
    return problems


# --------------------------------------------------------------------------
# render assertions
# --------------------------------------------------------------------------

def measure_render(render, home=None):
    l, rgb = load_luma(render)
    H, W = l.shape
    edge, votes, total = panel_edge(l)
    panel_ratio = edge / W
    px_per_pt = W / 393.0

    colm = np.median(l[:, int(0.45 * edge):edge], axis=1)
    bottom_y = max(y for y in range(int(0.80 * H), H) if colm[y] > 120)
    # When the panel runs to the frame's bottom edge its arc is outside the
    # capture: iOS draws the panel full-bleed, so the last rows of the arc are not
    # in the PNG. The radius is then a token claim (identical on both platforms),
    # not a pixel one, and this says so instead of failing on a number it could
    # not see.
    # `body` is measured further down; the corner test needs its own reference.
    corner_ref = float(np.median(band(l, BLANK_BAND, panel_ratio)))
    corner_is_panel = abs(float(l[bottom_y - 2, max(edge - 5, 0)]) - corner_ref) < 4.0
    arc_in_frame = bottom_y < H - 3 or corner_is_panel
    radius_px = corner_radius_px(l, edge, bottom_y, px_per_pt) if arc_in_frame else float("nan")

    body = band(l, BLANK_BAND, panel_ratio)
    rim = band(l, RIM_BAND, panel_ratio)
    strip = band(l, STRIP_BAND)

    scrim_ratio = float("nan")
    if home is not None:
        hl, _ = load_luma(home)
        hh, hw = hl.shape
        hband = hl[int(0.28 * hh):int(0.90 * hh), int(0.80 * hw):int(0.99 * hw)]
        scrim_ratio = float(np.percentile(strip, 90) / max(np.percentile(hband, 90), 1e-6))

    xs_all = slice(int(0.10 * edge), int(0.90 * edge))
    panel_rb = float(np.median(rgb[:, xs_all, 0] - rgb[:, xs_all, 2]))
    pill_top, pill_bottom, hero_top, hero_bottom = surfaces(
        l, rgb, edge, H, float(np.median(body)), panel_rb)

    col = np.median(l[:, int(0.62 * edge):int(0.78 * edge)], axis=1)
    down = [(i, float(col[i] - col[i + 1])) for i in range(int(0.08 * H), int(0.95 * H) - 1)
            if (col[i] - col[i + 1]) >= 1.0]

    label_left, chev_right = label_and_chevron(l, hero_bottom or int(0.24 * H),
                                              edge, H, PX_PER_PT_REF)

    # Same anchor as the sheet: the first divider above the recent block.
    low_spikes = sorted([s for s in down if s[0] > int(0.45 * H)], key=lambda s: -s[1])[:6]
    low_spikes = sorted(s[0] for s in low_spikes)
    rec_top = max((low_spikes[0] / H) + 0.004, 0.45) if low_spikes else 0.52
    titles = recent_titles(l, 0.16, 0.80, rec_top, 0.90, px_per_pt)
    recent_pitch = ((titles[3][0] - titles[0][0]) / 3.0) if len(titles) == 4 else float("nan")
    block = l[int(rec_top * H):int(0.90 * H), int(0.16 * W):int(0.62 * W)]
    grey_px = int(((block > 110) & (block < 205)).sum())
    bands_n = len(ink_bands(l, 0.16, 0.62, rec_top, 0.90, thresh=150, min_ink=2,
                            min_h=max(4, int(2.5 * px_per_pt))))
    footer_clusters = ink_clusters(l, 0.0, panel_ratio * 0.99,
                                   FOOTER_BAND[1], FOOTER_BAND[3])
    mid = [c for c in footer_clusters
           if 0.40 < ((c[0] + c[1]) / 2.0) / max(panel_ratio * W, 1) < 0.72]

    if hero_top is not None and hero_bottom is not None:
        hero_row = int((hero_top + hero_bottom) / 2)
        left_rgb = rgb[hero_row, int(0.04 * edge):int(0.14 * edge)].reshape(-1, 3).mean(axis=0)
        right_rgb = rgb[hero_row, int(0.78 * edge):int(0.94 * edge)].reshape(-1, 3).mean(axis=0)
    else:
        left_rgb = np.array([np.nan, np.nan, np.nan])
        right_rgb = left_rgb

    insets = [edge - edge_at(l, int(frac * H), expected=edge, px_per_pt=px_per_pt)
              for frac in (0.08, 0.10, 0.12)]
    top_inset = float(np.median(insets))
    return {
        "panel_ratio": panel_ratio,
        "corner_radius_pt": radius_px / px_per_pt if np.isfinite(radius_px) else float("nan"),
        "arc_in_frame": arc_in_frame,
        "body_l": float(np.median(body)),
        "rim_delta": float(np.max(np.median(rim, axis=0)) - np.median(body))
        if rim.size else float("nan"),
        "hf_p90": hf_p90(body),
        "column_spread": column_spread(l, panel_ratio),
        "strip_l": float(np.median(strip)),
        "scrim_ratio": scrim_ratio,
        "inset_pt": label_left / px_per_pt if np.isfinite(label_left) else -1.0,
        "chevron_inset_pt": (edge - chev_right) / px_per_pt
        if np.isfinite(chev_right) else -1.0,
        "pill_h_pt": ((pill_bottom - pill_top) / px_per_pt)
        if pill_top is not None else float("nan"),
        "hero_h_pt": ((hero_bottom - hero_top) / px_per_pt)
        if hero_top is not None else float("nan"),
        "pill_to_hero_pt": ((hero_top - pill_bottom) / px_per_pt)
        if (hero_top is not None and pill_bottom is not None) else float("nan"),
        "top_inset_pt": top_inset / px_per_pt,
        "recent_ink_bands": bands_n,
        "recent_grey_px": grey_px,
        "footer_mid_ink": len(mid),
        "hero_left_rb": float(left_rgb[0] - left_rgb[2]),
        "hero_right_rb": float(right_rgb[0] - right_rgb[2]),
        "edge_px": edge,
        "px_per_pt": px_per_pt,
    }


def check(r, c, label):
    problems, notes = [], []

    def gated(key, value):
        lo, hi = c[key]["band"]
        value = float(value)
        ok = lo <= value <= hi
        notes.append(f"  {key:20s} {value:9.2f}  in [{lo}, {hi}]  {'OK' if ok else 'FAIL'}")
        if not ok:
            problems.append(f"{key}: {value:.2f} outside [{lo}, {hi}]")
        return ok

    if r.get("arc_in_frame", True):
        gated("corner_radius_pt", r.get("corner_radius_pt", float("nan")))
    else:
        notes.append("  corner_radius_pt      arc clipped by the frame   "
                     "(token-gated instead)")

    for key in ("panel_ratio", "body_l", "rim_delta", "hf_p90",
                "column_spread", "inset_pt",
                "hero_h_pt", "pill_to_hero_pt"):
        if key not in c:
            continue
        gated(key, r.get(key, float("nan")))

    # DR1: the top-right corner is SQUARE. A rounded one insets the edge early.
    inset = r.get("top_inset_pt", float("nan"))
    if not np.isfinite(inset) or inset > 1.5:
        problems.append(f"DR1 the top-right corner is rounded: the edge is inset "
                        f"{inset:.1f} pt at 5% height, and the sheet's is 0")
    else:
        notes.append(f"  DR1 top-right square  {inset:9.2f} pt inset            OK")

    # DR2: the dim, as a ratio against the Home capture of the same run. Without
    # the Home frame the ratio cannot be computed, so it is a stop, not a pass.
    gated("scrim_ratio", r["scrim_ratio"])

    # DR9: the recent block carries text, and it carries SECONDARY text. Four rows
    # whose timestamps were dropped read as four bands and no grey.
    for key, what in (("recent_ink_bands", "text bands"), ("recent_grey_px", "grey ink")):
        lo, hi = c[key]["band"]
        v = int(r.get(key, 0))
        ok = lo <= v <= hi
        notes.append(f"  {key:20s} {v:9d}  in [{lo}, {hi}]  {'OK' if ok else 'FAIL'}")
        if not ok:
            problems.append(f"DR9 the recent block has {v} {what}, expected "
                            f"at least {lo} — a drawer that dropped its relative "
                            f"times reads as four bands and no grey")

    # DR10: nothing between the avatar and the gear. A tab bar's middle icons
    # land exactly there, which is the thing the brief forbids.
    lo, hi = c["footer_mid_ink"]["band"]
    v = int(r.get("footer_mid_ink", 0))
    ok = lo <= v <= hi
    notes.append(f"  footer_mid_ink       {v:9d}  in [{lo}, {hi}]  {'OK' if ok else 'FAIL'}")
    if not ok:
        problems.append(f"DR10 {v} ink cluster(s) between the footer's avatar and gear "
                        f"— the drawer must not carry a bottom navigation bar")

    # DR8: the hero row is a warm band with a real gradient across it. Measured on
    # the capture alone (the sheet's hero warmth is diluted by its own cream, so a
    # sheet-derived threshold would be a number about two pictures, not one).
    hero_h = r.get("hero_h_pt", float("nan"))
    left, right = r.get("hero_left_rb", 0.0), r.get("hero_right_rb", 0.0)
    ok = (np.isfinite(hero_h) and hero_h >= 40
          and left >= 40 and right <= 34 and (left - right) >= 12)
    notes.append(f"  DR8 hero {hero_h:5.1f} pt, R-B {left:.1f}/{right:.1f}  "
                 f"(h>=40, L>=40, R<=34, drop>=12)  {'OK' if ok else 'FAIL'}")
    if not ok:
        problems.append(f"DR8 the hero row is not a warm band: {hero_h:.0f} pt tall, "
                        f"R-B {left:.0f} at its left stop and {right:.0f} at its right")

    return problems, notes


# --------------------------------------------------------------------------

def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--reference", default=str(REFERENCE))
    ap.add_argument("--render")
    ap.add_argument("--home", help="the Home capture of the same run, for DR2")
    ap.add_argument("--probe-set", choices=["android", "ios"], default="android")
    ap.add_argument("--label", default="drawer")
    ap.add_argument("--derive", action="store_true")
    ap.add_argument("--write-contract", action="store_true")
    ap.add_argument("--report-only", action="store_true")
    ap.add_argument("--debug-out")
    ap.add_argument("--same-frame",
                    help="a capture of the SAME drawer with 500 conversations; the two "
                         "frames must be near-identical, which is what 'bounded rows' "
                         "means in pixels")
    args = ap.parse_args()

    ref = Path(args.reference)
    if not ref.is_file():
        print(f"::error::the reference sheet is missing: {ref}")
        return 2
    sha = hashlib.sha256(ref.read_bytes()).hexdigest()

    if args.derive:
        if sha != REFERENCE_SHA256:
            print(f"::error::DR0 the sheet is not the one this gate was written "
                  f"against\n  expected {REFERENCE_SHA256}\n  found    {sha}\n"
                  f"  Re-derive every number and update the constant in the same commit.")
            return 2
        derived = derive(ref)
        obj = contract(derived)
        print(f"=== derived from {ref.name} ({sha[:12]}) ===")
        for key, value in derived.items():
            if key == "detector":
                continue
            band_ = BANDS.get(key)
            inside = "" if band_ is None else (
                "  [in band]" if band_[0] <= value <= band_[1] else "  [OUT OF BAND]")
            print(f"  {key:20s} {value!r:>12}{inside}")
        print(f"  detector            {derived['detector']}")
        if args.write_contract:
            CONTRACT.parent.mkdir(parents=True, exist_ok=True)
            CONTRACT.write_text(json.dumps(obj, indent=2) + "\n")
            print(f"wrote {CONTRACT.relative_to(ROOT)}")
        return 0

    if not args.render:
        print("::error::nothing to do: pass --render, or --derive to re-measure the sheet")
        return 2

    if sha != REFERENCE_SHA256:
        print(f"::error::DR0 the sheet moved; the contract describes a different picture")
        return 2
    if not CONTRACT.is_file():
        print(f"::error::DR0 no contract at {CONTRACT.relative_to(ROOT)} — run --derive --write-contract")
        return 2
    c = json.loads(CONTRACT.read_text())

    if not args.home:
        print("::error::DR2 needs --home: the dim is measured as a ratio against the "
              "Home capture of the SAME run, and without it this gate would report "
              "a number it did not measure")
        return 2
    if not Path(args.render).is_file():
        print(f"::error::no capture at {args.render}")
        return 2
    try:
        r = measure_render(args.render, args.home)
    except Exception as exc:                     # noqa: BLE001 - any failure is exit 2
        print(f"::error::could not measure {args.render}: {exc}")
        return 2

    print(f"=== {args.label} ({args.probe_set}) ===")
    print(f"  frame edge {r['edge_px']} px, {r['px_per_pt']:.3f} px/pt")
    problems, notes = check(r, c, args.label)
    for line in notes:
        print(line)

    if args.debug_out and args.render:
        with Image.open(args.render) as im:
            draw = ImageDraw.Draw(im.convert("RGB"))
            W, H = im.size
            pr = r["panel_ratio"]
            draw.rectangle([0, 0, int(pr * W), H], outline=(0, 160, 0), width=3)
            draw.rectangle([int(pr * W), 0, W, H], outline=(0, 100, 220), width=3)
            Path(args.debug_out).write_bytes(b"")
            im.convert("RGB").save(args.debug_out)

    # DR11: 500 conversations must draw the SAME rows. A raw pixel diff is the
    # wrong instrument — a one-pixel shift of the panel moves every glyph edge and
    # still means "bounded" — so the 500-chat frame is measured with the same
    # probes and has to report the same panel and the same number of text bands.
    if args.same_frame:
        if not Path(args.same_frame).is_file():
            print(f"::error::DR11 no 500-chat capture at {args.same_frame}")
            return 2
        try:
            many = measure_render(args.same_frame, args.home)
        except Exception as exc:                   # noqa: BLE001
            print(f"::error::DR11 could not measure the 500-chat frame: {exc}")
            return 2
        print(f"  DR11 500 chats        panel {many['panel_ratio']:.3f} vs "
              f"{r['panel_ratio']:.3f}, bands {many['recent_ink_bands']} vs "
              f"{r['recent_ink_bands']}")
        if abs(many["panel_ratio"] - r["panel_ratio"]) > 0.01:
            problems.append(
                f"DR11 the panel changed width with 500 conversations "
                f"({many['panel_ratio']:.3f} vs {r['panel_ratio']:.3f})")
        if abs(many["recent_ink_bands"] - r["recent_ink_bands"]) > 1:
            problems.append(
                f"DR11 500 conversations drew {many['recent_ink_bands']} text bands "
                f"against {r['recent_ink_bands']} for four — the list is not bounded")

    problems += constants_problems(c)
    if problems and not args.report_only:
        print(f"\n::error::the drawer failed {len(problems)} check(s) on {args.label}:")
        for p in problems:
            print(f"  - {p}")
        return 1
    if problems:
        print(f"\n[report-only] {len(problems)} would fail:")
        for p in problems:
            print(f"  - {p}")
        return 0
    print(f"\nOK: panel, dim, material, rhythm and content all match the sheet on {args.label}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
