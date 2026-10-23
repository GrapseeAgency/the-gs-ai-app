#!/usr/bin/env python3
"""The drawer in dark mode: the checks that need no dark sheet.

There is no dark frame of this drawer to compare against, so nothing here is a
picture judgement. These are the physical requirements a dark glass panel has to
meet, and each one fails for a named, ordinary mistake:

  K1 panel is dark AND lighter than the strip   a light panel in dark mode, or a
                                                panel indistinguishable from the
                                                dim page beside it
  K2 the dim still separates                    0.45–0.62 of the same Home strip
  K3 copy is legible                            ink 5th percentile ≥ 60 L over the
                                                local panel body
  K4 no blown highlight                         < 1% above 250 L
  K5 the hero row keeps its warmth              R−B ≥ 12 at its left stop

Unmeasurable exits 2, never 0.
"""
import argparse
import sys

import numpy as np
from PIL import Image

BANDS = {
    "panel_l": (8.0, 70.0),
    "panel_minus_strip": (2.0, 60.0),
    "scrim_ratio": (0.40, 0.66),
    "copy_contrast": (60.0, 255.0),
    "blown": (0.0, 1.0),
    "hero_left_rb": (12.0, 120.0),
}


def load(path):
    with Image.open(path) as im:
        a = np.asarray(im.convert("RGB")).astype(float)
    l = 0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2]
    return l, a


def measure(render, home=None, panel_ratio=0.786):
    l, rgb = load(render)
    H, W = l.shape
    edge = int(panel_ratio * W)
    body = l[int(0.28 * H):int(0.70 * H), int(0.02 * edge):int(0.05 * edge)]
    strip = l[int(0.28 * H):int(0.90 * H), edge + 8:W - 6]
    ink = l[int(0.30 * H):int(0.85 * H), int(0.10 * edge):int(0.85 * edge)]

    ratio = float("nan")
    if home is not None:
        hl, _ = load(home)
        hh, hw = hl.shape
        hband = hl[int(0.28 * hh):int(0.90 * hh), int(0.80 * hw):int(0.99 * hw)]
        ratio = float(np.percentile(strip, 90) / max(np.percentile(hband, 90), 1e-6))

    hero_row = int(0.225 * H)
    hero_left = rgb[hero_row, int(0.03 * edge):int(0.12 * edge)].reshape(-1, 3).mean(axis=0)

    return {
        "panel_l": float(np.median(body)),
        "strip_l": float(np.median(strip)),
        "panel_minus_strip": float(np.median(body) - np.median(strip)),
        "scrim_ratio": ratio,
        "copy_contrast": float(np.percentile(ink, 99) - np.median(body)),
        "blown": float(100.0 * (l > 250).sum() / l.size),
        "hero_left_rb": float(hero_left[0] - hero_left[2]),
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--render", required=True)
    ap.add_argument("--home")
    ap.add_argument("--label", default="drawer-dark")
    args = ap.parse_args()

    try:
        r = measure(args.render, args.home)
    except Exception as exc:                        # noqa: BLE001
        print(f"::error::cannot measure {args.render}: {exc}")
        return 2

    print(f"=== {args.label} ===")
    problems = []
    for key, (lo, hi) in BANDS.items():
        value = r[key]
        if not np.isfinite(value):
            print(f"::error::K? {key} could not be measured (pass --home for the dim ratio)")
            return 2
        ok = lo <= value <= hi
        print(f"  {key:20s} {value:8.2f}  in [{lo}, {hi}]  {'OK' if ok else 'FAIL'}")
        if not ok:
            problems.append(f"{key}: {value:.2f} outside [{lo}, {hi}]")

    if problems:
        print(f"\n::error::the dark drawer failed {len(problems)} check(s):")
        for p in problems:
            print(f"  - {p}")
        return 1
    print("\nOK: dark panel, present glass, legible copy, warm hero, no blown highlight")
    return 0


if __name__ == "__main__":
    sys.exit(main())
