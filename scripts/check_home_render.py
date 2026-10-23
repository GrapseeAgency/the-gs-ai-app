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
while iOS measured -4.64 — a real defect the old +3.0 upper bound was hiding, and
which closed to -3.22 when Poppins replaced SF Pro on that platform.

A5 makes "could not measure" exit 2 as documented. Previously a NaN from an
undersized rim window fell through `if r[key] is not None` (never true for a
float), got compared against the band, and was reported as glass DRIFT with
exit 1 — and a missing file raised an uncaught traceback.

A6 checks the gate against its own anchor: every band must contain the sheet's
own measurement, which is printed every run. A recorded anchor that no longer
matches the image it came from means the sheet moved or a rect slipped, and a
verdict built on either is worthless.

A7 checks the shipped atmosphere against the atmosphere the reference was drawn
from, and A8 checks that the bento is where the page's own margin puts it — a
card can be the right glass at the right size and still hang outside the column
it was laid out in, which is exactly what one did (see card_left_edge).

A9 gates the top hairline's ABSOLUTE level as well as its excess. The excess is
what the eye sees locally, but it is not comparable between captures: the same
252.0 L hairline reads +19.94 on the sheet, +18.96 on Android and +17.03 on iOS,
because the iPhone's safe area puts that card on a bloom 4 L brighter, and the
band for it has to be 7.5 L wide. The absolute number does not move (252.0 /
252.1 / 252.4) because the rim is 90%-opaque white, so A9 gets a 4 L band and is
the check that fails when the rim's alpha or colour moves. Proven: weakening
CardLight's rimStrength from 1f to 0.6f drops its hairline to 244.06 and A9
fails — where the excess, at 10.87, also fails but only because that band happens
to be wide.

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
0.4374 of the frame and iOS's at 0.4742 — a 35pt gap, which is the iPhone 16 Pro's
59pt safe area against the 24dp the Android harness reports, i.e. correct device
behaviour rather than a layout bug — against a card whose whole lit hairline is
3px tall. There is no y that is on both cards' glass, let alone a rim_y that
measures both hairlines. It gets its own rect per platform (`--probe-set`), each
verified with --debug-out rather than assumed.

WHAT EACH PROBE IS GATED ON
---------------------------
Three cues, and not the same number on all three surfaces. The Suggestions row
sits on flat cream, so it is measured against the page beside it. The bento card
sits over the bloom, where "card minus page" measures where the card is as much
as what the glass does, so it is measured against the material law for the ground
actually under it (see BANDS). The pill is gated on both: its edge is the cue, and
its fill is where a platform-specific opacity shows up first.

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
SUGGESTIONS_ANDROID = dict(SUGGESTIONS_RENDER, rim_y=0.7602,
                           interior=[0.66, 0.7920, 0.26, 0.0220],
                           outside=[0.004, 0.7920, 0.022, 0.0220])
# iOS re-measured after Poppins landed: the geometric face is taller than SF Pro
# at the same point size, so every text block grew and the cards below the
# greeting moved down ~50px. The old rect (interior 0.7900, rim_y 0.7707) now
# straddles the card's top edge — which is what A3 purity exists to catch, and
# did: it reported spread 9.29 rather than passing a page sample as glass.
SUGGESTIONS_IOS = {"name": "suggestions",
                   # Re-derived on run 39935068074. The brightest row in the old
                   # window was the DOCK's rim (0.8196, luma 242.8), not this card:
                   # the card is dim glass and its edge is a step, not a peak, so the
                   # edge was found as the largest positive step in the column median
                   # — a detector validated on the previous capture first, where it
                   # returned 0.7926 against the 0.7935 that had been measured by
                   # hand.
                   "interior": [0.66, 0.7887, 0.26, 0.0200],
                   "outside":  [0.004, 0.7887, 0.022, 0.0200],
                   "rim_y": 0.7852}

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
    "edge_y": 0.500,
}
BENTO_ANDROID = {
    # Re-derived after the subtitle shrank: the row moved up 8.7 pt, so the old
    # rect sampled the gap above the card. Measured, not shifted by assumption.
    "name": "bento",
    "interior": [0.62, 0.4303, 0.30, 0.0078],
    "outside":  [0.004, 0.4303, 0.022, 0.0078],
    "rim_y": 0.4272,
    "edge_y": 0.4898,
}
BENTO_IOS = {
    # Re-derived on run 39935068074, the first capture after the greeting subtitle
    # went to the sheet's 12.5/18: the card's lit edge is 6.0 pt higher, at 0.4671.
    "name": "bento",
    "interior": [0.62, 0.4702, 0.30, 0.0094],
    "outside":  [0.004, 0.4702, 0.022, 0.0094],
    "rim_y": 0.4671,
    "edge_y": 0.5279,
}

# The quick-action pills. This probe exists because a previous pass reported the
# pill's lit edge as missing (+6.9 L against the sheet's +14.7) from a window that
# was not on the pill's edge at all — the same self-inflicted reading that made
# the Suggestions rim check unable to fail. Measured properly, the sheet's New
# Chat pill carries +14.7 L, Android's +15.5 and iOS's +19, so FloatingControl is
# right; the probe is what stops that being re-litigated by eye.
PILL_REF = {
    "name": "pill",
    "interior": [0.105, 0.3568, 0.045, 0.0085],   # rows 500..512, left of the label
    "outside":  [0.004, 0.3568, 0.022, 0.0085],
    "rim_y": 0.3540,                               # row 497, the bright hairline
}
PILL_ANDROID = {
    # Re-derived after the greeting subtitle went from 15sp/22 to the sheet's
    # 12.5/18: the quick-action row moved up 7.5 pt, so the old rect sampled page
    # where the pill had been, and A4/A9 failed (rim +1.56, hairline 239.6) rather
    # than certifying nothing — which is what those checks were added for. The edge
    # is the brightest row in x 0.12..0.145, measured at 0.3265.
    "name": "pill",
    "interior": [0.105, 0.3284, 0.045, 0.0110],   # rows 839..867
    "outside":  [0.004, 0.3284, 0.022, 0.0110],
    "rim_y": 0.3265,                               # row 834
}
PILL_IOS = {
    # Re-derived on run 39935068074: 7.0 pt higher with the subtitle change.
    "name": "pill",
    "interior": [0.105, 0.3685, 0.045, 0.0110],
    "outside":  [0.004, 0.3685, 0.022, 0.0110],
    "rim_y": 0.3662,
}

PROBES = {
    "reference": [SUGGESTIONS_REF, BENTO_REF, PILL_REF],
    "android": [SUGGESTIONS_ANDROID, BENTO_ANDROID, PILL_ANDROID],
    "ios": [SUGGESTIONS_IOS, BENTO_IOS, PILL_IOS],
}

# A1/A3 thresholds, each derived from measured data with the margin stated.
PAGE_LUMA_BAND = (235.0, 250.0)   # real 242.20 / 242.49 | black 0.00, white 255.00
SPREAD_MAX = 5.0                  # real 0.21 / 2.36 | boundary-straddling 7.4..41.8

# A2 and the rim are anchored to each probe's own measured value on the sheet,
# NOT to a tolerance that happens to admit the current builds. The previous
# band's +3.0 upper bound admitted a zero delta, which is why "no glass at all"
# and "correct glass" both printed OK.
#
# THE MATERIAL LAW, AND WHY THE BENTO IS GATED ON IT
# --------------------------------------------------
# Translucent cream over a ground composites to
#
#     interior = A·F + (1 − A)·ground
#
# with A the coverage and F the fill's own luminance. Both were solved off the
# sheet, whose suggestions row sits on flat cream and whose Mind Guide card sits
# on the bloom: two cards, two grounds, one solution — A = 0.45, F = 235.2. (An
# earlier pass solved A = 0.30 / F = 238.6 by treating a card's own composite as
# the "ground"; the render disagreed with those constants and the re-solve is
# what the numbers below are.)
#
# `residual` is the measured card minus what that law predicts for the ground
# actually under it. It replaces `lift` (interior − ground) as the bento's gated
# number because it is position-independent: the card may sit anywhere down the
# page and the law still says what its pixels must be. `lift` did not. iOS's
# iPhone 16 Pro safe area is 59pt against the mock's 20pt and Android's harness
# 24dp, so the same page laid out from the safe area puts the bento ~35pt lower,
# over a ground 5 L brighter, and lift read +0.36 where the sheet reads +2.79.
# The last pass recorded that as a widened band per capture. It was the wrong
# fix: a band per capture is how a gate stops meaning anything, and the
# exemption was bought by giving up the coverage claim.
#
# What the law cannot do is invent its own sensitivity. Where the ground meets
# the fill, coverage stops being identifiable — iOS's bento ground is 234.33
# against a fill of 235.2 — so the script prints `coverage` as "not identifiable"
# there rather than quoting the ±1.7-to-+3.7 range the band would admit. Where it
# is a number, the residual is the glass; where it is not, the rim is what
# carries that card and the residual only says the composite is the right
# brightness.
#
#   probe          sheet            android           ios
#   suggestions  tint  -2.79       -2.86             -3.93
#                rim   +2.58       +1.72             +2.81
#                hair   241.09     238.98            240.91
#   bento        resid  +0.12      +0.78             +0.23
#                rim   +19.94      +19.71            +17.46
#                hair   252.00     252.14            252.14
#                edge   16.76      20.00             20.00
#   pill         resid  +0.16      +0.80             -0.08
#                rim   +11.82      +15.03            +12.91
#                hair   252.43     253.21            253.21
#
# Re-measured on the captures that followed the greeting subtitle change (12.5/18).
# The sheet's edge is printed and not gated because its crop is not rectified; every
# other cell is inside the band its line names, on both platforms.
BANDS = {
    # A9 ("hairline") on every probe: the absolute level of the top edge, which
    # the rim's 90%-white makes ground-independent. Measured sheet / android / ios:
    # suggestions 241.1 / 239.1 / 240.9, bento 252.0 / 252.1 / 252.4,
    # pill 252.4 / 253.2 / 253.6. Each band holds its three with >= 1 L to spare
    # and is 3-4 L wide against the 7-12 L the excess band has to be.
    "suggestions": {"tint": (-5.49, -1.09), "rim": (-1.50, 8.00),
                    "hairline": (238.0, 243.5)},
    # ±2.0 L. The three captures read +0.12 / +0.93 / -0.03 today, and 2 L is the
    # honesty margin on a ground read out of the PNG at the mock's rect: the mock
    # is not rectified (its crop is 5% too wide), and its own page beside the
    # cards agrees with the shipped art to 0.00 L one row up and 5.7 L two rows
    # down. Device captures do not carry that error — the art they draw IS the
    # file being sampled — so the band is set by the weakest anchor, not by the
    # strongest measurement.
    "bento":       {"residual": (-2.00, 2.00), "rim": (15.00, 22.50),
                    "hairline": (250.0, 254.0)},
    # The pill's edge is the cue that makes it read as a floating capsule, and
    # the sheet's is +11.82 L. Its fill is gated too, because that is where a
    # platform-specific opacity announces itself, and it did: iOS's pill measured
    # -6.69 L against the law where the sheet is +0.16 and Android -0.13. Two
    # causes, both found by this number and neither visible in a screenshot —
    # `gsCapsuleGlass` was a second, hand-written recipe that (a) cast its shadow
    # with plain `.shadow`, so the capsule's own silhouette came back through a
    # translucent fill, and (b) tinted with `Aero.surface`, 3.0 L brighter than
    # the `glassSurface` Android paints. The capsule is the shared modifier now.
    # Coverage is coarse here (|F - ground| is 4.0 L on Android's row); absolute
    # luminance is not.
    "pill":        {"residual": (-2.00, 2.00), "rim": (10.00, 22.00),
                    "hairline": (250.5, 255.0)},
}

# The solved constants. Changing either one changes every residual in the file,
# so they live next to the measurements that produced them rather than in a
# config. F is also L(#F2EAE3), the glass fill token both platforms ship: the
# solve and the token agreeing to 0.0 is why the band can be this tight.
MATERIAL_A = 0.45
MATERIAL_F = 235.2


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


# A7 — the atmosphere the page draws has to BE the reference's atmosphere, not a
# rendering of it. Every ground the material law divides by is read out of this
# file, so if the file and the sheet disagree, every residual in the report is
# measured against the wrong floor. The left margin is the sample: the page's
# content starts at 20 dp (0.051 of the frame), so x 0.005..0.05 is bare backdrop
# on the sheet and on both devices, with nothing drawn over it.
#
# The right margin is deliberately not used. Measured down 351 four-row samples,
# the left agrees with the sheet to a median 0.11 L (p90 0.93, 2% of rows over
# 2 L) while the right carries a median 0.15 but 10.5% of rows over 2 L and up to
# 20.8 — the mock's own frame bevel and a clipped bokeh, not an artwork defect.
#
# The band is applied to the SHEET only. The same measurement on a capture is
# dominated by something else, and knowing which is the point: iOS's left margin
# sits 1.0-6.0 L below the art across x 8..32 px because its cards really do
# cast the role's 10pt shadow onto the page (the mock's own page shadow measures
# -7.2 L below its card, so iOS is the faithful one), while the Android capture
# tracks the art to 0.3 L — nothing reaches the margin there in that render. Gate
# the capture on it and the next honest fix would be to
# delete a shadow. So: gate the artwork against the reference, print the
# platforms, and name the difference instead of tuning a band around it.
MARGIN_X = (0.002, 0.018)
MARGIN_BAND = (0.0, 0.60)        # median |sheet - art|, measured 0.11
MARGIN_P90_BAND = (0.0, 2.50)    # p90 of the same, measured 0.87

# A8 — where the bento's left edge must land. Home's content column is inset 20
# dp/pt on both platforms (Android's `padding(horizontal = 20.dp)`, iOS's
# `HomeMetrics.margin`), so the card's rim begins at 20.0 pt. The band allows 1.5
# pt either side: half a pixel of rounding at 3x on each edge plus the softness of
# finding a 1 px line by threshold. Applied to the device captures only — the mock
# is not rectified and its own edge reads 17.5 pt, which is a property of the crop
# rather than of a layout bug, so it is printed and not gated.
EDGE_BAND = (18.5, 21.5)


def margin_agreement(frame, ground):
    """|page - backdrop art| down the bare left margin, as (median, p90)."""
    H, W = frame.shape
    AH, AW = ground.shape
    x0, x1 = int(MARGIN_X[0] * W), int(MARGIN_X[1] * W)
    ax0, ax1 = int(MARGIN_X[0] * AW), int(MARGIN_X[1] * AW)
    diffs = []
    for y0 in range(0, H - 3, 4):
        gy0 = int(y0 / H * AH)
        gy1 = max(gy0 + 1, int((y0 + 4) / H * AH))
        a = float(np.median(frame[y0:y0 + 4, x0:x1]))
        b = float(np.median(ground[gy0:gy1, ax0:ax1]))
        diffs.append(abs(a - b))
    d = np.asarray(diffs)
    return float(np.median(d)), float(np.percentile(d, 90)), int(d.size)


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


def card_left_edge(l, edge_y):
    """Where the bento's left rim begins, in points from the frame's left edge.

    A8. A card can be the right glass at the right size and still be in the wrong
    place, and one was: iOS's video art carried a fixed `.frame(width: 200)` which
    is a MINIMUM as well as an ideal, so the card measured 200 pt inside the 186.9
    pt slot the bento gave it and the outer `.frame(width:)` centred the 13 pt
    excess — 6.5 pt of glass and rim hanging outside the page's 20 pt margin on
    each side, with the card's edge at 13.5 pt. Nothing in the material metrics can
    see that; the probe rects still land on glass.

    Sampled below the top hairline, where the left rim is a straight vertical line:
    at the hairline itself the corner radius curves it away. Measured at the
    mid-card row, the jump off the page is 9 L on Android and 11 L on iOS, so 4 L
    is a wide margin for detecting it.
    """
    H, W = l.shape
    y = int(edge_y * H)
    band = l[max(0, y - 1):y + 3, :].mean(axis=0)
    px_per_pt = W / 393.0                  # every frame is treated as 393 pt wide
    lo, hi = int(6.0 * px_per_pt), int(60.0 * px_per_pt)
    page = float(np.median(band[:lo]))
    for x in range(lo, hi):
        if band[x] - page > 4.0:
            return x / px_per_pt
    return None


def hairline_luma(l, probe):
    """The top edge's absolute luminance, before anything is subtracted.

    The excess below it is what the eye sees locally, but it moves with the
    ground: the same 252.0 hairline reads +19.94 L on the sheet, +18.96 on Android
    and +17.03 on iOS, purely because the glass under it sits on a bloom 4 L
    brighter where the iPhone's 59 pt safe area puts the card. That is why the rim
    band has to be 7.5 L wide.

    The absolute number does not move, because the rim is 90%-opaque white: 0.90 x
    255 + 0.10 x fill is 252.7 whatever the ground. Measured across the three
    frames the bento hairline is 252.0 / 252.1 / 252.4 — a 0.4 L spread where the
    excess spreads 2.9. So it can be gated to +/- 2 L, and it fails if the rim's
    alpha or its colour moves, which is the thing the wide excess band cannot see.
    """
    H, W = l.shape
    x, _, w, _ = probe["interior"]
    x0, x1 = int((x + 0.02) * W), int((x + w - 0.02) * W)
    top = probe["rim_y"]
    y0, y1 = max(0, int((top - 0.006) * H)), min(H, int((top + 0.018) * H))
    win = l[y0:y1, x0:x1]
    if win.shape[0] < 6:
        return float("nan")
    return float(np.median(win.max(axis=0)))


def measure(path, kind, crop=None, debug_out=None, ground=None):
    """Every probe of `kind`'s set, measured on one decoded frame.

    `ground` is the luminance of the backdrop PNG the page draws its atmosphere
    from. Where a probe gates on `residual`, the card's interior is compared with
    what the material law predicts for that ground — the ground actually under
    the glass — which is what makes the number independent of where the card
    happens to sit on the page.
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
        gmed = float(np.median(rect_of(ground, p["interior"]))) if ground is not None else None
        denom = None if gmed is None else MATERIAL_F - gmed
        residual = None if gmed is None else interior - (
            MATERIAL_A * MATERIAL_F + (1 - MATERIAL_A) * gmed)
        # Coverage is only identifiable where the ground is not the fill: as
        # |F - ground| goes to zero the same luminance is produced by every
        # coverage, so the quotient stops being a measurement of the glass.
        coverage = None if not denom or abs(denom) < 1.0 else (interior - gmed) / denom
        got.append({
            "probe": p["name"], "interior": interior, "page_outside": page,
            "interior_minus_page": interior - page, "interior_spread": spread,
            "rim_minus_interior": rim_excess(l, p),
            "hairline_absolute": hairline_luma(l, p),
            "ground": gmed, "material_residual": residual, "coverage": coverage,
            "left_edge_pt": card_left_edge(l, p["edge_y"]) if "edge_y" in p else None,
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
    if r["ground"] is None:
        material = ""
    else:
        cov = "not identifiable" if r["coverage"] is None else f"{r['coverage']:+.2f}"
        material = (f" ground={fmt(r['ground'])} law={fmt(r['material_residual'])}"
                    f" coverage={cov}")
    return (f"  {r['probe']:14s} interior={fmt(r['interior'])} page={fmt(r['page_outside'])} "
            f"Δtint={fmt(r['interior_minus_page'])}{material} spread={fmt(r['interior_spread'])} "
            f"Δrim={fmt(r['rim_minus_interior'])} hair={fmt(r['hairline_absolute'])}"
            + ("" if r["left_edge_pt"] is None else f" edge={fmt(r['left_edge_pt'])}pt"))


# Which number each gated metric reads off a measurement row.
METRICS = {"tint": "interior_minus_page", "rim": "rim_minus_interior",
           "spread": "interior_spread", "residual": "material_residual",
           "hairline": "hairline_absolute"}

# How each metric is labelled in the assertion list.
LABELS = {"tint": "A2 glass tint", "residual": "A2 material law",
          "rim": "A4 rim highlight", "hairline": "A9 hairline level"}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--reference", default="assets/reference/home-reference.png")
    ap.add_argument("--render")
    ap.add_argument("--label", default="render")
    ap.add_argument("--backdrop",
                    default="android/app/src/main/res/drawable-nodpi/gs_home_backdrop.png",
                    help="the page atmosphere PNG the cards sit on, read as the ground "
                         "the material law is evaluated at (both platforms ship this "
                         "file, byte-identically — see scripts/prepare_home_art.py)")
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

    # A7 on the sheet: the art we ship and the atmosphere the mock were drawn
    # from have to be the same picture, or the ground every residual below is
    # measured against is somebody else's lighting.
    drift = []
    med, p90, n = margin_agreement(load_luma(args.reference, SCREEN), ground)
    print(f"\n  A7 atmosphere identity (sheet): |page - shipped art| down the bare "
          f"left margin: median {med:.2f} p90 {p90:.2f} over {n} rows")
    for name, value, band in (("median", med, MARGIN_BAND), ("p90", p90, MARGIN_P90_BAND)):
        if not band[0] <= value <= band[1]:
            drift.append(f"sheet margin {name} {value:.2f} outside [{band[0]}, {band[1]}]")

    ref_edges = [r for r in ref if r["left_edge_pt"] is not None]
    if ref_edges:
        print(f"  A8 bento left edge (sheet): {ref_edges[0]['left_edge_pt']:.1f} pt — printed, "
              f"not gated: the mock is not rectified, so its own x carries the crop")

    # The bands are recorded measurements of this sheet. If the sheet or a probe
    # rect has moved, the band is a lie, and a gate that cannot be right about
    # its own anchor cannot be right about a render. Checked, not assumed.
    for d in drift:
        print(f"::error::{d}")
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
        return 1 if drift else 0
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

    # A7 again on the capture: what the platform actually put on screen, against
    # the file it is supposed to be drawing. A translucent veil over the page —
    # a scrim nobody asked for, a dimming layer left over from a sheet — moves
    # this number and nothing else in the report.
    med, p90, n = margin_agreement(load_luma(args.render), ground)
    print(f"\n  A7 atmosphere identity ({args.label}): median {med:.2f} p90 {p90:.2f} "
          f"over {n} rows — diagnostic only, this is where the platform's card "
          f"shadows land (see MARGIN_X above)")

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
            value = r[METRICS[metric]]
            if value is None:
                print(f"\n::error::{r['probe']}/{metric} could not be computed on "
                      f"{args.render} — no ground was loaded (not a glass verdict)")
                return 2
            checks.append((LABELS[metric], value, band))
        if "edge_y" in next(p for p in PROBES[args.probe_set] if p["name"] == r["probe"]):
            if r["left_edge_pt"] is None:
                print(f"\n::error::{r['probe']} A8: no card edge found within 60 pt of "
                      f"the frame — the bento is not where the page puts it")
                return 2
            checks.append(("A8 edge at margin", r["left_edge_pt"], EDGE_BAND))
        print(f"  [{r['probe']}]")
        for name, value, (lo, hi) in sorted(checks):
            ok = lo <= value <= hi
            if not ok:
                failures.append(f"{r['probe']}/{name}: {value:.2f} outside [{lo}, {hi}]")
            print(f"    {name:18s} {value:8.2f}  in [{lo},{hi}]  {'OK' if ok else 'FAIL'}")

    if failures or drift:
        print(f"\n::error::Home glass failed {len(failures) + len(drift)} check(s) "
              f"on {args.label}:")
        for f in failures + drift:
            print(f"  - {f}")
        if args.debug_out:
            print(f"  (if A3 failed, open {args.debug_out}: the red box must sit "
                  f"entirely on blank card glass)")
        return 1
    print(f"\nOK: {len(got)} surfaces present, each moving its own ground as the "
          f"sheet's does, all probes on clean glass, all hairlines lit")
    return 0


if __name__ == "__main__":
    sys.exit(main())
