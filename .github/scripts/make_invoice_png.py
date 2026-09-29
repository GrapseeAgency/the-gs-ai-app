#!/usr/bin/env python3
"""Write a PNG containing text, with no third-party dependency.

Why this exists: the OCR test asserts that a specific string appears in an image
that has to EXIST. The first attempt generated it with ImageMagick:

    convert -size 800x200 xc:white -pointsize 32 \\
      -annotate +20+100 "INVOICE INV-4471 DUE 2026-03-01" /tmp/invoice.png

and the runner does not have ImageMagick:

    NOTE: no ImageMagick on this runner. The OCR test needs it to draw the
    java.lang.AssertionError: fixture image missing at
      /storage/emulated/0/Android/data/com.grapsee.gsai/files/Pictures/invoice.png

so a2 SKIPPED and the legacy OCR test FAILED. A skip is the expensive kind of
failure: the test reports nothing about OCR at all.

So the text is rasterised here instead, in pure Python: zlib and struct are in
the standard library, and a PNG is a well-specified container. The glyphs are a
5x7 bitmap font, scaled up. Nothing about the runner needs to be present.

Usage: make_invoice_png.py <out.png> "<text>"
"""
import struct
import sys
import zlib

# 5x7 bitmap font, one string per glyph, rows top to bottom, '#' = ink.
FONT = {
    "A": ".###.#...##...########...##...##...#",
    "B": "####.#...##...#####.#...##...#####.",
    "C": ".###.#...##....#....#....#...#.###.",
    "D": "####.#...##...##...##...##...#####.",
    "E": "########...#....####.#....#....#####",
    "F": "########...#....####.#....#....#....",
    "G": ".###.#...##....#.####...##...#.###.",
    "H": "#...##...##...########...##...##...#",
    "I": "#####..#....#....#....#....#..#####",
    "J": "..###...#....#....#....#.#..#..##..",
    "K": "#...##..#.#.#..##...#.#..#..#.#...#",
    "L": "#....#....#....#....#....#....#####",
    "M": "#...###.###.#.##...##...##...##...#",
    "N": "#...###..##.#.##..###...##...##...#",
    "O": ".###.#...##...##...##...##...#.###.",
    "P": "####.#...##...#####.#....#....#....",
    "Q": ".###.#...##...##...##.#.##..#..##.#",
    "R": "####.#...##...#####.#.#..#..#.#...#",
    "S": ".#####....#.....###.....#....######.",
    "T": "#####..#....#....#....#....#....#..",
    "U": "#...##...##...##...##...##...#.###.",
    "V": "#...##...##...##...##...#.#.#...#..",
    "W": "#...##...##...##.#.##.#.###.###...#",
    "X": "#...##...#.#.#...#...#.#.##...##...#",
    "Y": "#...##...#.#.#...#....#....#....#..",
    "Z": "#####....#...#...#...#...#....#####",
    "0": ".###.#...##..###.#.###..##...#.###.",
    "1": "..#...##....#....#....#....#..#####",
    "2": ".###.#...#....#...#...#...#....#####",
    "3": "#####...#...#...##.....#...#.###.",
    "4": "...#...##..#.#.#..#..##########...#",
    "5": "#########....####.....#....#..####.",
    "6": "..##..#...#....####.#...##...#.###.",
    "7": "#####....#...#...#...#....#....#..",
    "8": ".###.#...##...#.###.#...##...#.###.",
    "9": ".###.#...##...#.####....#...#..##..",
    "-": "..............#####................",
    " ": "...................................",
    ":": ".....##...##...##.....##...##.....",
}

W, H = 5, 7
SCALE = 6
PAD = 24


def glyph_rows(ch):
    g = FONT.get(ch.upper())
    if g is None:
        return ["." * W] * H
    # The table is written as one run of 5-char rows; split it.
    return [g[i * W:(i + 1) * W] for i in range(H)]


def render(text, scale=SCALE, pad=PAD):
    cols = len(text) * (W + 1) - 1
    w = cols * scale + 2 * pad
    h = H * scale + 2 * pad
    # White page, black ink. Greyscale 8-bit, one byte per pixel.
    px = bytearray(b"\xff" * (w * h))

    def put(x, y):
        if 0 <= x < w and 0 <= y < h:
            px[y * w + x] = 0

    for i, ch in enumerate(text):
        rows = glyph_rows(ch)
        ox = pad + i * (W + 1) * scale
        for ry, row in enumerate(rows):
            for rx, cell in enumerate(row):
                if cell != "#":
                    continue
                for dy in range(scale):
                    for dx in range(scale):
                        put(ox + rx * scale + dx, pad + ry * scale + dy)
    return w, h, px


def write_png(path, w, h, px):
    raw = bytearray()
    for y in range(h):
        raw.append(0)  # filter type 0 (None)
        raw.extend(px[y * w:(y + 1) * w])

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 0, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/invoice.png"
    text = sys.argv[2] if len(sys.argv) > 2 else "INVOICE INV-4471 DUE 2026-03-01"
    missing = sorted({c for c in text.upper() if c not in FONT})
    if missing:
        sys.exit("no glyph for %r -- add it to FONT or the OCR test can never "
                 "see the text it asserts on" % missing)
    w, h, px = render(text)
    write_png(out, w, h, px)
    print("wrote %s  %dx%d  text=%r" % (out, w, h, text))


if __name__ == "__main__":
    main()
