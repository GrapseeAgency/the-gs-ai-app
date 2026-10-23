#!/usr/bin/env python3
"""Prove the drawer gate can fail, on this run's capture.

A green gate that cannot go red has certified nothing. This takes the capture the
job just produced, doctors it in the ways that matter — the ways a plausible but
wrong drawer would look — and requires each one to fail the check that owns it.
The untouched frame must pass.

Each case names the lie and the check, so a future reader can tell what the gate
actually defends rather than what its comments claim.

Usage: test_drawer_render_gate.py --render drawer-393x852.png [--home home-393x852.png]
Exit 0 when every lie is caught and the real frame passes, 1 otherwise.
"""
import argparse
import json
import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import check_drawer_render as gate  # noqa: E402

CONTRACT = json.loads((Path(__file__).resolve().parent.parent /
                       "shared-contracts/drawer-geometry.json").read_text())


def save(array, path):
    Image.fromarray(np.clip(array, 0, 255).astype(np.uint8)).save(path)
    return str(path)


def doctor(img, case):
    """Return a doctored copy of the frame, plus the check that must catch it."""
    a = img.copy()
    H, W = a.shape[:2]
    edge = int(gate.panel_edge(0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2])[0])
    strip_colour = np.median(a[int(0.3 * H):int(0.8 * H), edge + 6:W - 4].reshape(-1, 3), axis=0)

    if case == "narrow":                      # the panel is 20 pt too narrow
        cut = edge - int(20 * W / 393.0)
        a[:, cut:] = strip_colour
        return a, ("panel_ratio",)
    if case == "rounded-top":                 # someone rounded all four corners
        h = int(0.15 * H)                     # the rows DR1 samples: 0.08–0.12 H
        inset = int(30 * W / 393.0)
        a[:h, edge - inset:] = strip_colour
        return a, ("DR1",)
    if case == "square-bottom":               # the arc was flattened
        h = int(40 * W / 393.0)
        a[H - h:, edge - h:] = np.median(a[int(0.5 * H):int(0.6 * H), :edge].reshape(-1, 3), axis=0)
        return a, ("corner_radius_pt",)
    if case == "no-scrim":                    # the dim was dropped
        a[:, edge + 2:] = np.clip(a[:, edge + 2:] / 0.80, 0, 255)
        return a, ("scrim_ratio",)
    if case == "ghosted":                     # the page shows through the panel
        home_band = a[int(0.3 * H):int(0.8 * H), :edge]
        noise = np.random.default_rng(7).normal(0, 12, home_band.shape)
        a[int(0.3 * H):int(0.8 * H), :edge] = np.clip(home_band + noise, 0, 255)
        return a, ("hf_p90", "column_spread")
    if case == "flat-hero":                   # the warm gradient was flattened
        # find the hero the same way the gate does, then desaturate it
        l = 0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2]
        body = float(np.median(l[int(0.30 * H):int(0.45 * H), :int(0.05 * edge)]))
        rb = float(np.median(a[:, int(0.10 * edge):int(0.90 * edge), 0]
                             - a[:, int(0.10 * edge):int(0.90 * edge), 2]))
        _, _, ht, hb = gate.surfaces(l, a, edge, H, body, rb)
        if ht is None:
            return a, ("DR8",)
        grey = np.median(a[ht:hb, :edge].reshape(-1, 3), axis=0)
        a[ht:hb, :edge] = grey
        return a, ("DR8",)
    if case == "no-recents":                  # the chat rows lost their text
        # paint only the CONTENT column: wiping the left margin too would take the
        # rim with it and this case would fail DR3 instead of DR9
        y0, y1 = int(0.45 * H), int(0.92 * H)
        block = a[y0:y1, int(0.10 * edge):int(0.92 * edge)]
        a[y0:y1, int(0.10 * edge):int(0.92 * edge)] = np.median(
            block.reshape(-1, 3), axis=0)
        return a, ("DR9",)
    if case == "tab-bar":                     # a bottom nav crept into the footer
        for k in range(5):
            cx = int(edge * (0.30 + 0.10 * k))
            cy = int(0.91 * H)
            a[cy - 6:cy + 6, cx - 6:cx + 6] = 40
        return a, ("DR10", "footer_mid_ink")
    return a, None


CASES = ["narrow", "rounded-top", "square-bottom", "no-scrim", "ghosted",
         "flat-hero", "no-recents", "tab-bar"]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--render", required=True)
    ap.add_argument("--home")
    args = ap.parse_args()

    src = Path(args.render)
    if not src.is_file():
        print(f"::error::no capture at {src}")
        return 2
    img = np.asarray(Image.open(src).convert("RGB")).astype(float)

    real_problems, _ = gate.check(gate.measure_render(str(src), args.home), CONTRACT, "real")
    if real_problems:
        print("::error::the untouched capture already fails; the adversarial suite "
              "cannot prove anything about a broken frame:")
        for p in real_problems:
            print(f"  - {p}")
        return 1
    print("real frame: passes, as it must for the rest of this to mean anything")

    failures = []
    with tempfile.TemporaryDirectory() as tmp:
        for case in CASES:
            doctored, expected = doctor(img, case)
            path = save(doctored, f"{tmp}/{case}.png")
            problems, _ = gate.check(gate.measure_render(path, args.home), CONTRACT, case)
            caught = [p for p in problems if any(e in p for e in expected)]
            ok = bool(caught)
            label = "/".join(expected)
            print(f"  {'ok  ' if ok else 'MISS'} {case:14s} -> {label:24s} "
                  f"{caught[0] if caught else problems[:1]}")
            if not ok:
                failures.append((case, expected, problems))

    if failures:
        print(f"\n::error::{len(failures)} doctored frame(s) were not caught:")
        for case, expected, problems in failures:
            print(f"  - {case}: expected {'/'.join(expected)}, got {problems[:3]}")
        return 1
    print(f"\nOK: {len(CASES)} lies caught, the real frame passes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
