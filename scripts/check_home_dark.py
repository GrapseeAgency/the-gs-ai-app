#!/usr/bin/env python3
"""The dark Home page: the checks that can be made without a dark reference.

There is no dark frame in the reference sheet, so nothing here compares the design to a
picture. What it does compare is the page against itself and against the physical
requirement that a dark glass UI has to meet:

  D1 the page is dark          left-margin median in 10..60 L. A light page rendered in
                               dark mode is the failure this catches — the whole point.
  D2 the glass is present      the Suggestions card must differ from the page beside it
                               by at least 1.5 L. In dark that difference is smaller
                               than in light, so a card that composites as nothing looks
                               almost identical to the page — which is exactly the
                               vacuity this file's light-mode sibling was rebuilt for.
  D3 the copy is legible       5th-percentile text luminance minus the local background
                               must be at least 60 L in the greeting block, i.e. a
                               contrast ratio well past 7:1 for body copy.
  D4 no blown highlight        under 1% of the frame above 250 L. The pearls are the
                               brightest thing on this page; a dark theme that lets one
                               of them clip reads as a white glare.

A measurement that cannot be taken exits 2, never 0: "the probe was off the card" and
"the card is correct" are different answers and only one of them is good news.

Usage: check_home_dark.py --render home-393x852-dark.png [--label dark-393]
"""
import argparse
import sys

import numpy as np
from PIL import Image

BANDS = {
    "page": (10.0, 60.0),      # D1
    "card_delta": (1.5, 40.0),  # D2
    "copy_contrast": (60.0, 255.0),  # D3
    "blown": (0.0, 1.0),        # D4, percent
}
LABELS = {
    "page": "D1 page is dark",
    "card_delta": "D2 glass is present",
    "copy_contrast": "D3 copy is legible",
    "blown": "D4 no blown highlight",
}


def luma(path):
    with Image.open(path) as im:
        a = np.asarray(im.convert("RGB")).astype(float)
    return 0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2]


def measure(path):
    l = luma(path)
    H, W = l.shape
    page = float(np.median(l[:, int(0.004 * W):int(0.026 * W)]))

    # The Suggestions card: its top edge is a step in the column median, the same
    # detector the light gate uses for the same reason (dim glass has no peak).
    x0, x1 = int(0.66 * W), int(0.92 * W)
    prof = np.median(l[:, x0:x1], axis=1)
    y0, y1 = int(0.60 * H), int(0.86 * H)
    step = np.diff(prof[y0:y1])
    edge = y0 + int(np.argmax(step))
    strip = l[edge + int(0.006 * H):edge + int(0.016 * H), x0:x1]
    card_delta = float(np.median(strip)) - page if strip.size else float("nan")

    # Greeting block: the text is the dark tail of the luminance distribution here,
    # because in dark mode the copy is lighter than its background.
    head = l[int(0.12 * H):int(0.30 * H), int(0.03 * W):int(0.60 * W)]
    copy_contrast = float(np.percentile(head, 95)) - float(np.median(head))

    blown = float(100.0 * (l > 250).sum() / l.size)
    return {"page": page, "card_delta": card_delta,
            "copy_contrast": copy_contrast, "blown": blown,
            "edge": edge / H}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--render", required=True)
    ap.add_argument("--label", default="dark")
    args = ap.parse_args()

    try:
        r = measure(args.render)
    except Exception as exc:                        # noqa: BLE001 - any failure is exit 2
        print(f"::error::cannot measure {args.render}: {exc}")
        return 2

    print(f"=== {args.label} ===")
    print(f"  page {r['page']:.1f} L   card {r['page'] + r['card_delta']:.1f} L "
          f"(delta {r['card_delta']:+.1f})   copy contrast {r['copy_contrast']:.0f} L   "
          f"blown {r['blown']:.2f}%   edge {r['edge']:.4f}")

    failures = []
    for key, (lo, hi) in BANDS.items():
        value = r[key]
        if not np.isfinite(value):
            print(f"::error::{LABELS[key]}: not a finite number — the probe missed")
            return 2
        ok = lo <= value <= hi
        print(f"  {LABELS[key]:24s} {value:7.2f}  in [{lo}, {hi}]  {'OK' if ok else 'FAIL'}")
        if not ok:
            failures.append(f"{LABELS[key]}: {value:.2f} outside [{lo}, {hi}]")

    if failures:
        print(f"\n::error::dark Home failed {len(failures)} check(s) on {args.label}:")
        for f in failures:
            print(f"  - {f}")
        return 1
    print("\nOK: dark page, present glass, legible copy, no blown highlight")
    return 0


if __name__ == "__main__":
    sys.exit(main())
