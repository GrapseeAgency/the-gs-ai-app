#!/usr/bin/env python3
"""check_sheets_render.py — the nine reference pages' measured card gate.

WHY THIS EXISTS
---------------
The reference pages (docs/reference/pending) were proven by CAPTURES ONLY: a
test wrote the PNGs and the workflow asserted they existed and were not empty.
That is existence evidence. It cannot see the drift the third pass found:

  * the inbox and the provider list were ONE card with hairlines while both
    mocks draw every row as its own card with a gap;
  * the Available Integrations tiles had five different heights, so their "+"
    marks did not share a baseline;
  * a page could run taller than its mock and still pass.

The six owner sheets are joined by the three pages ported after them — Model
Selection, Chat History & Search, and Voice & Speech. They are measured by the
same law: a header at the kit's type size, a lit card rim, and the announced
card count inside the page's own window. The cards below the search field and
filter chips are what the gate counts; those controls are not cards.

This gate measures the render the way those defects appear in pixels.

WHAT EACH PROBE IS GATED ON
---------------------------
  title    the page title's ink band in the header: top 9.0-14.0 pt, band
           height 10.0-17.0 pt, and the subtitle's band 16.0-23.0 pt below the
           title. The mock's measured type is 16 pt / 10.5 pt; a page whose
           header shrinks, grows or drifts fails here. On iOS the absolute top
           is relaxed to 0-90 pt (the windowed harness's safe-area inset is not
           the Android capture's); its size and subtitle gap still gate, and the
           rim windows shift by the measured header offset so the inset moves
           the measurement rather than failing it.

  rim      a card's lit top edge, found (not assumed): a row must be >= 247 L
           on at least 80 % of the card span, and its level must be 248-255 L
           with an excess of >= 6 L over the card's own interior 8 pt below.
           The sheets' glass rim reads 253-254 L in the accepted captures.

  rhythm   the card count, and — where the mock draws a list of equal cards —
           the cadence between consecutive card tops. Inbox: seven cards with
           four consecutive 70-78 pt steps (four Today + two Earlier + the
           closing card). Connected Apps: six provider cards in five 78-84 pt
           steps. A fused group card cannot satisfy either.

  count    alerts / security / subscription / help assert their group-card
           count (4 / 4 / 3 / 2). These are the mocks' group counts, not a
           guess. Help's two are Popular Help Topics and Still need help (its
           six categories are tiles); subscription's three are Plan Details,
           Usage and Billing (the gradient Upgrade hero is not a group card).

WHAT THIS GATE DOES NOT CLAIM
-----------------------------
It does not overlay the mock. The mock's phone panel is 393 x ~913 pt, not the
393 x 852 capture, so a pixel diff would be a scaling argument, not a
measurement. The gate locks the card law and the list rhythm the mocks draw;
the mock comparison stays the human review the PNGs in this folder are for.

Modes: default (gate) · `--self-test` (doctor the render; every gated probe must
be able to go red) · `--derive` (print the measured rows from a real render).

Usage:
  check_sheets_render.py --render ref-notifications-inbox.png --screen inbox
  check_sheets_render.py --render ref-connected-apps.png --screen connected
  check_sheets_render.py --render ref-notifications-inbox.png --screen inbox \
      --self-test
Exit: 0 within bands, 1 drift, 2 could not measure.
"""

import argparse
import sys

import numpy as np
from PIL import Image, ImageDraw

# The capture is 393 pt wide at 3x; refuse anything that is not that canvas.
CANVAS_PT = 393.0

# Header ink. The mock's page title is 16 pt; its rendered cap band is 10-17 pt
# depending on descenders ("Connected Apps & Integrations" has a "g").
TITLE_TOP_BAND = (9.0, 14.0)
# iOS's windowed harness applies the simulator's safe-area inset differently
# from the Android capture, so the iOS probe gates the title's SIZE and its
# subtitle gap, not its absolute y; the card rims carry the layout.
TITLE_TOP_BAND_IOS = (0.0, 90.0)
TITLE_HEIGHT_BAND = (10.0, 17.0)
SUBTITLE_GAP_BAND = (16.0, 23.0)
INK_LUMA = 150

# Card rim, measured off the accepted renders (253-254 L level, 13-25 L excess).
RIM_SPAN = (40.0, 353.0)
RIM_FRACTION = 0.80
RIM_LUMA = 247.0
RIM_LEVEL_BAND = (248.0, 255.0)
RIM_EXCESS_MIN = 6.0
RIM_CLUSTER_PX = 2
INTERIOR_DY = 8.0

SCREENS = {
    "inbox": dict(
        stem="ref-notifications-inbox",
        rims=7,
        rim_window=(150.0, 720.0),
        steps=(4, 70.0, 78.0),
        note="4 Today + 2 Earlier + closing card",
        # iOS's kit draws the mock's measured 56 pt two-line row + 5 pt
        # gap; Android's rows are 70-78 apart. Same law (seven separate
        # cards at a steady pitch), platform's own ruler. Measured on run
        # 41019419509's capture: 63.0, 62.3, 62.7, [group header], 62.7, 71.7.
        rims_ios=7,
        rim_window_ios=(150.0, 720.0),
        steps_ios=(4, 62.0, 73.0),
    ),
    "connected": dict(
        stem="ref-connected-apps",
        rims=6,
        rim_window=(150.0, 620.0),
        steps=(5, 78.0, 84.0),
        note="six provider cards",
        # iOS: five uniform 71.3 pt steps on the same capture.
        rims_ios=6,
        rim_window_ios=(150.0, 620.0),
        steps_ios=(5, 69.0, 74.0),
    ),
    "alerts": dict(
        stem="ref-notifications-alerts",
        rims=4,
        rim_window=(40.0, 800.0),
        steps=None,
    ),
    "security": dict(
        stem="ref-account-security",
        rims=4,
        rim_window=(40.0, 800.0),
        steps=None,
        # iOS draws its identity hero as a lit glass card as well as the four
        # group cards — five rims, all real (54/169/429/634/783 pt).
        rims_ios=5,
        rim_window_ios=(40.0, 800.0),
        steps_ios=None,
    ),
    "subscription": dict(
        stem="ref-subscription-usage",
        rims=3,
        rim_window=(140.0, 790.0),
        steps=None,
        # iOS's Billing card sits on the page's brightest field and is a real
        # fourth rim at 741 pt (excess 9.7, just over the floor).
        rims_ios=4,
        rim_window_ios=(140.0, 790.0),
        steps_ios=None,
    ),
    "help": dict(
        stem="ref-help-support",
        rims=2,
        rim_window=(480.0, 800.0),
        steps=None,
        # iOS's page is taller: Popular Help Topics lands at 470 pt, so the
        # Android window would miss it and count one card instead of two.
        rims_ios=2,
        rim_window_ios=(450.0, 820.0),
        steps_ios=None,
    ),
    # The three ported pages measure the same card counts on both platforms
    # (model 3, history 4, voice 2) and their rims fall in the same windows, so
    # no `_ios` override is needed today. `--derive` is how a future platform
    # divergence gets its own ruler, as the six sheets already do.
    "model": dict(
        stem="ref-model",
        rims=3,
        rim_window=(90.0, 820.0),
        steps=None,
        note="AI Model, Reasoning Effort, Model Preferences",
    ),
    "history": dict(
        stem="ref-chat-history",
        rims=4,
        rim_window=(140.0, 800.0),
        steps=None,
        note="totals card, two quick-action rows, new-chat CTA",
    ),
    "voice": dict(
        stem="ref-voice",
        rims=2,
        rim_window=(40.0, 800.0),
        steps=None,
        note="Read aloud hero, Voice group",
    ),
}


def spec_for(screen, key):
    """The platform's own number for one spec key.

    The LAW is shared — every card has a lit rim, the announced count, and a
    steady cadence where the mock draws equal cards. The RULER is not: the two
    kits draw different row heights, and iOS's page starts lower. These pairs
    were derived from real captures (run 41019419509), not guessed; `--derive`
    reprints them for recalibration.
    """
    spec = SCREENS[screen]
    if PLATFORM == "ios":
        return spec.get(f"{key}_ios", spec[key])
    return spec[key]


def fail(msg):
    print(f"::error::{msg}")
    return 2


def load_rgb(path):
    try:
        image = Image.open(path).convert("RGB")
    except Exception as exc:  # noqa: BLE001 — the reason must reach the log
        raise SystemExit(fail(f"cannot read {path}: {exc}"))
    return np.asarray(image).astype(np.float32)


def scale_of(width, path):
    scale = width / CANVAS_PT
    if abs(scale - round(scale)) > 0.01:
        raise SystemExit(
            fail(f"{path}: width {width}px is not an integer multiple of 393pt")
        )
    return scale


def ink_band(zone, threshold=INK_LUMA, min_px=5):
    """Rows of a 2-D luma window that carry text ink, clustered."""
    rows = np.where((zone < threshold).sum(axis=1) >= min_px)[0]
    groups = []
    for y in rows:
        if groups and y - groups[-1][-1] <= 2:
            groups[-1].append(y)
        else:
            groups.append([y])
    return [(int(gr[0]), int(gr[-1])) for gr in groups]


def rows_of_rims(gray, scale):
    """Every row that is a lit card edge across the sheet's card span."""
    x0, x1 = int(RIM_SPAN[0] * scale), int(RIM_SPAN[1] * scale)
    band = gray[:, x0:x1]
    fraction = (band >= RIM_LUMA).mean(axis=1)
    rows = np.where(fraction >= RIM_FRACTION)[0]
    groups = []
    for y in rows:
        if groups and y - groups[-1][-1] <= RIM_CLUSTER_PX:
            groups[-1].append(y)
        else:
            groups.append([y])
    return x0, x1, [int(round(np.mean(gr))) for gr in groups]


def rim_rows(gray, scale, window):
    x0, x1, candidates = rows_of_rims(gray, scale)
    out = []
    for y in candidates:
        if not (window[0] <= y / scale <= window[1]):
            continue
        level = float(gray[y, x0:x1].max())
        below_y = min(gray.shape[0] - 1, y + int(INTERIOR_DY * scale))
        interior = float(np.median(gray[below_y, x0:x1]))
        excess = level - interior
        if level < RIM_LUMA or excess < RIM_EXCESS_MIN:
            continue
        out.append(dict(y=y / scale, level=level, excess=excess))
    return out


def measure_title(gray, scale):
    # 300 pt, not 380: the inbox's header carries a trailing filter control
    # whose glyph shares the title's rows. Scanning into that column stretched
    # the title band from 12.67 to 19.33 pt and made the iOS capture look like
    # a type-style drift when the control was the only ink below the title.
    x0, x1 = int(45 * scale), int(300 * scale)
    # iOS's windowed harness may start the page under the simulator's safe
    # area; its header window is taller so the title is still found.
    header_pt = 60.0 if PLATFORM == "android" else 140.0
    zone = gray[: int(header_pt * scale), x0:x1]
    bands = ink_band(zone)
    if not bands:
        return None
    top, bottom = bands[0]
    height = (bottom - top + 1) / scale
    subtitle_gap = None
    # A descender or punctuation can be its own 1 pt band; the subtitle is the
    # first band tall enough to be a line of type.
    for band in bands[1:]:
        band_pt = (band[1] - band[0] + 1) / scale
        if band_pt >= 4.0:
            subtitle_gap = (band[0] - top) / scale
            break
    return dict(top=top / scale, height=height, subtitle_gap=subtitle_gap)


def header_offset(title):
    """How far the iOS page starts below the Android capture's 11 pt.

    iOS's windowed harness can start under the simulator's safe area; the card
    windows move with the header instead of assuming Android's origin. Android
    is always 0, so its measurements are unchanged.
    """
    if PLATFORM != "ios":
        return 0.0
    return max(0.0, min(90.0, title["top"] - 11.0))


def cadence(rims, count, lo, hi):
    ys = [r["y"] for r in rims]
    steps = [b - a for a, b in zip(ys, ys[1:])]
    inside = [s for s in steps if lo <= s <= hi]
    return len(inside) >= count, steps


def check(gray, scale, screen):
    spec = SCREENS[screen]
    rows = []

    title = measure_title(gray, scale)
    if title is None:
        raise SystemExit(fail(f"{screen}: the page title has no ink band"))
    top_band = TITLE_TOP_BAND if PLATFORM == "android" else TITLE_TOP_BAND_IOS
    rows.append(
        (
            "title",
            "top",
            title["top"],
            top_band,
            top_band[0] <= title["top"] <= top_band[1],
        )
    )
    rows.append(
        (
            "title",
            "height",
            title["height"],
            TITLE_HEIGHT_BAND,
            TITLE_HEIGHT_BAND[0] <= title["height"] <= TITLE_HEIGHT_BAND[1],
        )
    )
    if title["subtitle_gap"] is not None:
        gap = title["subtitle_gap"]
        rows.append(
            (
                "title",
                "subtitle_gap",
                gap,
                SUBTITLE_GAP_BAND,
                SUBTITLE_GAP_BAND[0] <= gap <= SUBTITLE_GAP_BAND[1],
            )
        )

    offset = header_offset(title)
    window = spec_for(screen, "rim_window")
    rims = rim_rows(
        gray,
        scale,
        (window[0] + offset, window[1] + offset),
    )
    if not rims:
        raise SystemExit(fail(f"{screen}: no card rim was measurable"))
    for i, rim in enumerate(rims):
        rows.append(
            (
                "rim",
                f"level[{i}]",
                rim["level"],
                RIM_LEVEL_BAND,
                RIM_LEVEL_BAND[0] <= rim["level"] <= RIM_LEVEL_BAND[1],
            )
        )
        rows.append(
            (
                "rim",
                f"excess[{i}]",
                rim["excess"],
                (RIM_EXCESS_MIN, 60.0),
                rim["excess"] >= RIM_EXCESS_MIN,
            )
        )
    rows.append(
        (
            "rim",
            "count",
            len(rims),
            (spec_for(screen, "rims"), spec_for(screen, "rims")),
            len(rims) == spec_for(screen, "rims"),
        )
    )

    steps_spec = spec_for(screen, "steps")
    if steps_spec:
        count, lo, hi = steps_spec
        ok, steps = cadence(rims, count, lo, hi)
        rows.append(("rhythm", f"steps[{count}x{lo:.0f}-{hi:.0f}]", steps, (lo, hi), ok))

    return rows, rims


def doctor_shift(image, scale, points=14.0):
    """Drop the page so the title band leaves its measured place."""
    shift = int(round(points * scale))
    canvas = Image.new("RGB", image.size, (246, 241, 236))
    canvas.paste(image, (0, shift))
    return canvas


def doctor_erase(image, scale, rims):
    """Paint over the first rim: the card count must drop by exactly one."""
    canvas = image.copy()
    draw = ImageDraw.Draw(canvas)
    x0, x1 = RIM_SPAN
    rim = rims[0]
    y = rim["y"]
    y0 = int((y - 1.5) * scale)
    y1 = int((y + 1.5) * scale)
    draw.rectangle(
        [int(x0 * scale), y0, int(x1 * scale), y1],
        fill=(235, 228, 223),
    )
    return canvas


def doctor_fuse(image, scale, rims):
    """Bridge the first two card tops so their two rims become one band."""
    if len(rims) < 2:
        return image
    canvas = image.copy()
    draw = ImageDraw.Draw(canvas)
    x0, x1 = RIM_SPAN
    a = int(rims[0]["y"] * scale)
    b = int(rims[1]["y"] * scale)
    draw.rectangle(
        [int(x0 * scale), a - 1, int(x1 * scale), b + 1],
        fill=(252, 249, 246),
    )
    return canvas


PLATFORM = "android"


def print_rows(rows, label, path, scale):
    print(f"\n{label} [{PLATFORM}] — {path} (scale {scale:g}x)")
    print(f"  {'probe':<10} {'measure':<22} {'value':>10}  {'band':<20} verdict")
    for probe, name, value, band, ok in rows:
        shown = value
        if isinstance(value, float):
            shown = f"{value:.2f}"
        elif isinstance(value, list):
            shown = ",".join(f"{v:.1f}" for v in value[:6])
        verdict = "ok" if ok else "DRIFT"
        print(f"  {probe:<10} {name:<22} {str(shown):>10}  {str(band):<20} {verdict}")
    print("")


def run_gate(image, path, screen):
    scale = scale_of(image.size[0], path)
    gray = np.asarray(image.convert("RGB")).astype(np.float32).mean(axis=2)
    rows, rims = check(gray, scale, screen)
    print_rows(rows, SCREENS[screen].get("note", screen), path, scale)
    return all(row[4] for row in rows), rows, rims, scale


def self_test(image, path, screen):
    ok, rows, rims, scale = run_gate(image, path, screen)
    if not ok:
        print(f"::error::{screen}: the ACCEPTED render already fails its gate")
        return 1
    failures = []

    def doctored_ok(img, label):
        """A doctor may make the measurement impossible; that is a red, not a
        crash, so the self-test can name the probe that stopped measuring."""
        try:
            return run_gate(img, label, screen)[0]
        except SystemExit:
            return False

    shift_points = 14.0 if PLATFORM == "android" else 200.0
    if doctored_ok(
        doctor_shift(image, scale, shift_points), f"{path} (title shifted)"
    ):
        failures.append("title shift")

    if doctored_ok(doctor_erase(image, scale, rims), f"{path} (rims erased)"):
        failures.append("rim erase")

    if spec_for(screen, "steps"):
        if doctored_ok(doctor_fuse(image, scale, rims), f"{path} (cards fused)"):
            failures.append("card fusion")

    if failures:
        print(f"::error::{screen}: self-test could not make these probes fail: {failures}")
        return 1
    probes = ["title", "rim"] + (["rhythm"] if spec_for(screen, "steps") else [])
    print(
        f"{screen}: self-test ok — {', '.join(probes)} all went red on "
        "doctored copies"
    )
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--render", required=True)
    ap.add_argument("--screen", required=True, choices=sorted(SCREENS))
    ap.add_argument("--label", default=None)
    ap.add_argument(
        "--platform",
        default="android",
        choices=["android", "ios"],
        help="labels the measurements; the card law is the same on both",
    )
    ap.add_argument("--self-test", action="store_true")
    ap.add_argument(
        "--derive",
        action="store_true",
        help="print the measured title/rim rows for recalibration, never fail",
    )
    args = ap.parse_args()

    global PLATFORM
    PLATFORM = args.platform

    try:
        image = Image.open(args.render)
    except Exception as exc:  # noqa: BLE001
        raise SystemExit(fail(f"cannot read {args.render}: {exc}"))
    image.load()

    label = args.label or args.screen
    if args.derive:
        scale = scale_of(image.size[0], args.render)
        gray = np.asarray(image.convert("RGB")).astype(np.float32).mean(axis=2)
        rows, _ = check(gray, scale, args.screen)
        print_rows(rows, SCREENS[args.screen].get("note", args.screen), args.render, scale)
        return 0
    if args.self_test:
        return self_test(image, args.render, args.screen)

    ok, _, _, _ = run_gate(image, args.render, args.screen)
    if not ok:
        print(f"::error::{label}: the sheet drifted from its measured card contract")
        return 1
    print(f"{label}: pass")
    return 0


if __name__ == "__main__":
    sys.exit(main())
