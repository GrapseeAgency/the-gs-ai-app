#!/usr/bin/env python3
"""Write a PNG containing text, with a real font when one is available.

The history of this file, because it explains the shape of the code:

  1. ImageMagick `convert -annotate`. The runner does not have ImageMagick, so
     a2 SKIPPED. A skip removes a test rather than failing it.

  2. A hand-rolled 5x7 bitmap font in pure Python. The glyphs were stored as
     35-character strings and ten of them were the wrong length, so slicing them
     into rows shifted the rows and ML Kit read:

         IMJOICE IMYAA71 DUE 2926-03-g1

     Every one of those misreads is a 5x7 letter that looks like another 5x7
     letter when its rows are off by one. Fixed with explicit row lists and a
     check_font() that refuses to render a malformed glyph.

  3. Spacing. scale=6/gap=2 merged strokes ("4471" -> "447"); gap=4 read as a
     space ("4 471"). gap=3 with scale=8 fixed every digit and every dash:

         INYOICE INY-4471 DUE 2026-03-01

     One glyph left: V. A 5x7 V is genuinely ambiguous -- hold the arms vertical
     and it is a U (read as "Y"), converge early and the apex leaves a two-row
     stem, which IS a Y. Five columns is not enough room for a V that is neither.
     Wrong three ways: U-shape, Y-shape, and a bolding pass that turned out to
     be a no-op (`ae4a909`, 25664 ink pixels at bold=1, 2 and 3 alike).

  4. THIS. A real typeface, rendered with Pillow at a large size. A real V is a
     V; the ambiguity was in the blocky font, never in OCR.

Pillow is tried first and the 5x7 font remains as a fallback, so the fixture can
still be produced on a machine with neither. Which path ran is printed, because a
fixture that silently falls back is a fixture nobody can reason about.

Usage: make_invoice_png.py <out.png> ["<text>"] [--show]
"""
import glob
import os
import struct
import sys
import zlib

GW, GH = 5, 7  # the fallback glyph cell

DEFAULT_TEXT = "INVOICE INV-4471 DUE 2026-03-01"

# Large, because recognition is easier the more pixels a glyph has, and because
# a 5x7 V is ambiguous in a way a 64pt V is not.
PIL_SIZE = 64
PIL_PAD = 48

# The fallback. Every glyph is 7 rows of exactly 5 characters, checked at import
# time by check_font().
FONT = {
    "A": [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "B": ["####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."],
    "C": [".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."],
    "D": ["####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."],
    "E": ["#####", "#....", "#....", "####.", "#....", "#....", "#####"],
    "F": ["#####", "#....", "#....", "####.", "#....", "#....", "#...."],
    "G": [".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".###."],
    "H": ["#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "I": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"],
    "J": ["..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."],
    "K": ["#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"],
    "L": ["#....", "#....", "#....", "#....", "#....", "#....", "#####"],
    "M": ["#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"],
    "N": ["#...#", "##..#", "#.#.#", "#.#.#", "#..##", "#...#", "#...#"],
    "O": [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "P": ["####.", "#...#", "#...#", "####.", "#....", "#....", "#...."],
    "Q": [".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"],
    "R": ["####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"],
    "S": [".####", "#....", "#....", ".###.", "....#", "....#", "####."],
    "T": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
    "U": ["#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "V": ["#...#", "#...#", "#...#", ".#.#.", ".#.#.", "..#..", "..#.."],
    "W": ["#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#"],
    "X": ["#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"],
    "Y": ["#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."],
    "Z": ["#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"],
    "0": [".###.", "#.#.#", "#.#.#", "#.#.#", "#.#.#", "#.#.#", ".###."],
    "1": ["..#..", ".##..", "..#..", "..#..", "..#..", "..#..", "#####"],
    "2": [".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"],
    "3": ["#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###."],
    "4": ["...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."],
    "5": ["#####", "#....", "####.", "....#", "....#", "#...#", ".###."],
    "6": ["..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."],
    "7": ["#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."],
    "8": [".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."],
    "9": [".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."],
    "-": [".....", ".....", ".....", "#####", ".....", ".....", "....."],
    ".": [".....", ".....", ".....", ".....", ".....", ".##..", ".##.."],
    ":": [".....", ".##..", ".##..", ".....", ".##..", ".##..", "....."],
    "/": ["....#", "....#", "...#.", "..#..", ".#...", "#....", "#...."],
    " ": [".....", ".....", ".....", ".....", ".....", ".....", "....."],
}


def check_font():
    bad = {}
    for ch, rows in FONT.items():
        if len(rows) != GH:
            bad[ch] = "%d rows, expected %d" % (len(rows), GH)
            continue
        for i, r in enumerate(rows):
            if len(r) != GW:
                bad[ch] = "row %d is %d chars, expected %d" % (i, len(r), GW)
                break
    if bad:
        for ch, why in sorted(bad.items()):
            sys.stderr.write("font: %r %s\n" % (ch, why))
        sys.exit("the fallback font is malformed; every glyph must be %d rows "
                 "of %d characters" % (GH, GW))


def find_ttf():
    """A real scalable font, if this machine has one."""
    cands = []
    for pat in ("/usr/share/fonts/**/DejaVuSans.ttf",
                "/usr/share/fonts/**/LiberationSans-Regular.ttf",
                "/usr/share/fonts/**/FreeSans.ttf",
                "/usr/share/fonts/**/*.ttf",
                "/Library/Fonts/**/*.ttf",
                "/System/Library/Fonts/**/*.ttf"):
        cands.extend(glob.glob(pat, recursive=True))
    # Prefer a plain sans face; a symbol or emoji face would be worse than none.
    for c in cands:
        b = os.path.basename(c).lower()
        if any(k in b for k in ("dejavusans.ttf", "liberationsans-regular",
                                "freesans.ttf", "arial", "helvetica")):
            return c
    return cands[0] if cands else None


def render_pillow(text, ttf, size=PIL_SIZE, pad=PIL_PAD):
    from PIL import Image, ImageDraw, ImageFont
    font = ImageFont.truetype(ttf, size)
    # One measuring pass for the exact text box, then one drawing pass. Guessing
    # the size is how a fixture ends up with its last character clipped off the
    # right edge and the OCR reading a truncated word.
    probe = Image.new("L", (10, 10), 255)
    box = ImageDraw.Draw(probe).textbbox((0, 0), text, font=font)
    tw, th = box[2] - box[0], box[3] - box[1]
    w, h = tw + 2 * pad, th + 2 * pad
    img = Image.new("L", (w, h), 255)
    d = ImageDraw.Draw(img)
    d.text((pad - box[0], pad - box[1]), text, font=font, fill=0)
    return w, h, img.tobytes()


def render_bitmap(text, scale=8, pad=32, gap=3):
    check_font()
    cell = GW + gap
    cols = len(text) * cell - gap
    w = cols * scale + 2 * pad
    h = GH * scale + 2 * pad
    px = bytearray(b"\xff" * (w * h))
    for i, ch in enumerate(text):
        rows = FONT.get(ch) or FONT.get(ch.upper())
        if rows is None:
            continue
        ox = pad + i * cell * scale
        for ry, row in enumerate(rows):
            for rx, c in enumerate(row):
                if c != "#":
                    continue
                for dy in range(scale):
                    base = (pad + ry * scale + dy) * w + ox + rx * scale
                    for dx in range(scale):
                        px[base + dx] = 0
    return w, h, bytes(px)


def write_png(path, w, h, px):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        raw.extend(px[y * w:(y + 1) * w])

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 0, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    show = "--show" in sys.argv
    out = args[0] if args else "/tmp/invoice.png"
    text = args[1] if len(args) > 1 else DEFAULT_TEXT

    missing = sorted({c for c in text if c not in FONT and c.upper() not in FONT})
    if missing:
        sys.exit("no glyph for %r in the fallback font; the Pillow path can "
                 "still draw it" % missing)

    ttf = find_ttf()
    used = "fallback 5x7 bitmap"
    if ttf:
        try:
            w, h, px = render_pillow(text, ttf)
            used = "Pillow + %s at %dpt" % (os.path.basename(ttf), PIL_SIZE)
        except Exception as e:                       # noqa: BLE001
            sys.stderr.write("Pillow path failed (%s); using the fallback font\n" % e)
            w, h, px = render_bitmap(text)
    else:
        w, h, px = render_bitmap(text)

    write_png(out, w, h, px)
    dark = sum(1 for v in px if v < 128)
    print("wrote %s  %dx%d  text=%r" % (out, w, h, text))
    print("  renderer : %s" % used)
    print("  ink      : %d px (%.1f%% of the page)" % (dark, 100.0 * dark / (w * h)))
    if used.startswith("fallback"):
        print("  WARNING  : ML Kit misreads the fallback font's V. A real TTF is "
              "needed for the INV-4471 marker to read back exactly.")


if __name__ == "__main__":
    main()
