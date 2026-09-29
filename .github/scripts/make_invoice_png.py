#!/usr/bin/env python3
"""Write a PNG containing text, with no third-party dependency.

Why this exists: the OCR test asserts a specific string appears in an image that
has to EXIST. The first attempt used ImageMagick, which the runner does not have:

    NOTE: no ImageMagick on this runner. The OCR test needs it to draw the
    java.lang.AssertionError: fixture image missing at
      /storage/emulated/0/Android/data/com.grapsee.gsai/files/Pictures/invoice.png

so a2 SKIPPED, which is the expensive kind of failure -- it removes a test rather
than failing it.

THE SECOND ATTEMPT RENDERED A BAD FONT, AND OCR READ IT FAITHFULLY

    wrote /tmp/invoice.png  1158x90  text='INVOICE INV-4471 DUE 2026-03-01'
    ocr -> IMJOICE IMYAA71 DUE 2926-03-g1

The glyphs were stored as 35-character strings and ten of them were the wrong
length, so slicing them into 5x7 rows shifted the rows. N rendered as M, V as Y,
0 as 9, 7 as 1, 4 as A. Every one of those is exactly what a 5x7 N looks like
when its rows are off by one.

So the font is written as explicit row LISTS here, where a mistake is visible in
the source, and `check_font` refuses to run if any glyph is not exactly 7 rows of
5 characters. `selftest` renders the text as ASCII art so the glyphs can be read
without an OCR engine at all.

Usage: make_invoice_png.py <out.png> ["<text>"] [--show]
"""
import struct
import sys
import zlib

GW, GH = 5, 7  # glyph cell

# Each glyph is 7 rows of exactly 5 characters. '#' is ink.
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
    "V": ["#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."],
    "W": ["#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#"],
    "X": ["#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"],
    "Y": ["#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."],
    "Z": ["#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"],
    # Centre bar, not a diagonal slash. A diagonal reads as noise to a text
    # recogniser: run 36594946025 produced "2926" for "2026" with the diagonal.
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
    """Refuse to render a font that is not exactly 7 rows of 5 characters."""
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
        sys.exit("the font is malformed; every glyph must be %d rows of %d "
                 "characters" % (GH, GW))


def render(text, scale=8, pad=32, gap=4):
    """Greyscale 8-bit, black ink on white. Returns (w, h, pixels)."""
    # ML Kit needs clear separation. Measured, not guessed: at scale=6/gap=2 this
    # produced "INVOICE INY-447 DUE 2926-03-01" -- the 1 was lost into the 7 and
    # the V grew a tail from the adjacent hyphen. The glyphs themselves were
    # correct (see --show); they were too small and too close together.
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
            for rx, cellv in enumerate(row):
                if cellv != "#":
                    continue
                for dy in range(scale):
                    base = (pad + ry * scale + dy) * w + ox + rx * scale
                    for dx in range(scale):
                        px[base + dx] = 0
    return w, h, px


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


def selftest(text):
    """Print the glyphs so they can be read without an OCR engine."""
    print("  font self-test -- read these; they are the pixels OCR will see:")
    for ch in text:
        rows = FONT.get(ch) or FONT.get(ch.upper())
        if rows is None:
            continue
        for r in rows:
            print("    " + r.replace("#", "\u2588").replace(".", " "))
        print("    " + " " * GW)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    show = "--show" in sys.argv
    out = args[0] if args else "/tmp/invoice.png"
    text = args[1] if len(args) > 1 else "INVOICE INV-4471 DUE 2026-03-01"
    check_font()
    missing = sorted({c for c in text if c not in FONT and c.upper() not in FONT})
    if missing:
        sys.exit("no glyph for %r -- add it to FONT or the OCR test can never "
                 "see the text it asserts on" % missing)
    if show:
        selftest(text)
    w, h, px = render(text)
    write_png(out, w, h, px)
    dark = sum(1 for v in px if v < 128)
    print("wrote %s  %dx%d  text=%r  ink=%d px (%.1f%%)"
          % (out, w, h, text, dark, 100.0 * dark / (w * h)))


if __name__ == "__main__":
    main()
