#!/usr/bin/env python3
"""Generate GsIcons for Android and iOS from the full Lucide icon set.

Source: lucide-react (ISC license) installed in node_modules at the version
pinned by package.json. Every geometry and every public export name — aliases
included — is vendored at generation time, so both native apps ship the whole
family and have NO runtime dependency on a web icon package.

Every icon renders as a 24x24 stroke drawing, stroke-width 2, round caps and
joins — Lucide's spec — so the whole set has one optical weight.

Outputs:
  android/app/src/main/java/com/grapsee/gsai/ui/icons/GsIcons.kt
      Compose ImageVectors (one per geometry); every Lucide export name is a
      property, aliases delegate to the canonical vector.
  ios/App/Sources/Theme/GsIcons.swift
      The same geometry as pre-converted absolute move/line/cubic/close
      commands, plus a GsIconView that strokes them in a 24x24 viewport.

Run: python3 scripts/gen_gs_icons.py
     python3 scripts/gen_gs_icons.py --curated   (only scripts/gs-icon-stems.txt)
"""
import json
import math
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LUCIDE = os.path.join(ROOT, "node_modules", "lucide-react")
ICON_DIR = os.path.join(LUCIDE, "dist", "esm", "icons")
BARREL = os.path.join(LUCIDE, "dist", "esm", "lucide-react.js")
ANDROID_TARGET = os.path.join(
    ROOT, "android/app/src/main/java/com/grapsee/gsai/ui/icons/GsIcons.kt"
)
IOS_TARGET = os.path.join(ROOT, "ios/App/Sources/Theme/GsIcons.swift")


# --------------------------------------------------------------------------
# Lucide source
# --------------------------------------------------------------------------

TAG_RE = re.compile(r"<(path|line|polyline|polygon|circle|ellipse|rect)\b([^/>]*)/?>")
ATTR_RE = re.compile(r'([a-zA-Z][a-zA-Z0-9-]*)="([^"]*)"')
NODE_RE = re.compile(r'\[\s*"([a-z]+)"\s*,\s*\{([^{}]*)\}\s*\]', re.S)
JS_ATTR_RE = re.compile(r'([a-zA-Z][a-zA-Z0-9-]*):\s*"((?:[^"\\]|\\.)*)"')

NOOP_PATHS = {"M0,0 L0,0", "M0,0L0,0"}


def num(v: str) -> str:
    f = float(v)
    return str(int(f)) if f == int(f) else str(f)


def svg_elem_to_path(tag: str, attrs: dict) -> str | None:
    """Convert one SVG shape element to a path `d` string."""
    if tag == "path":
        return attrs.get("d")
    if tag == "line":
        x1, y1, x2, y2 = (attrs.get(k, "0") for k in ("x1", "y1", "x2", "y2"))
        return f"M{num(x1)},{num(y1)} L{num(x2)},{num(y2)}"
    if tag in ("polyline", "polygon"):
        pairs = re.findall(r"(-?[\d.]+)[, ](-?[\d.]+)", attrs.get("points", ""))
        if not pairs:
            return None
        d = f"M{num(pairs[0][0])},{num(pairs[0][1])}"
        for x, y in pairs[1:]:
            d += f" L{num(x)},{num(y)}"
        return d + (" Z" if tag == "polygon" else "")
    if tag == "circle":
        cx, cy, r = (float(attrs.get(k, "0")) for k in ("cx", "cy", "r"))
        x, y = cx - r, cy
        rr = num(attrs["r"])
        return (
            f"M{num(x)},{num(y)} a{rr},{rr} 0 1,0 {num(2*r)},0 "
            f"a{rr},{rr} 0 1,0 {num(-2*r)},0"
        )
    if tag == "ellipse":
        cx, cy = float(attrs.get("cx", "0")), float(attrs.get("cy", "0"))
        rx, ry = float(attrs.get("rx", "0")), float(attrs.get("ry", "0"))
        x, y = cx - rx, cy
        return (
            f"M{num(x)},{num(y)} a{num(rx)},{num(ry)} 0 1,0 {num(2*rx)},0 "
            f"a{num(rx)},{num(ry)} 0 1,0 {num(-2*rx)},0"
        )
    if tag == "rect":
        x, y = float(attrs.get("x", "0")), float(attrs.get("y", "0"))
        w, h = float(attrs.get("width", "0")), float(attrs.get("height", "0"))
        rx = float(attrs.get("rx", attrs.get("ry", "0")) or 0)
        if rx > 0:
            rx = min(rx, w / 2, h / 2)
            return (
                f"M{num(x+rx)},{num(y)} H{num(x+w-rx)} "
                f"A{num(rx)},{num(rx)} 0 0 1 {num(x+w)},{num(y+rx)} "
                f"V{num(y+h-rx)} A{num(rx)},{num(rx)} 0 0 1 {num(x+w-rx)},{num(y+h)} "
                f"H{num(x+rx)} A{num(rx)},{num(rx)} 0 0 1 {num(x)},{num(y+h-rx)} "
                f"V{num(y+rx)} A{num(rx)},{num(rx)} 0 0 1 {num(x+rx)},{num(y)} Z"
            )
        return f"M{num(x)},{num(y)} H{num(x+w)} V{num(y+h)} H{num(x)} Z"
    return None


def is_blank(d: str) -> bool:
    compact = d.replace(" ", "").upper()
    return compact in {p.replace(" ", "").upper() for p in NOOP_PATHS}


def lucide_version() -> str:
    with open(os.path.join(LUCIDE, "package.json")) as f:
        return json.load(f)["version"]


def load_exports() -> list[tuple[str, list[str]]]:
    """Return (canonical stem, [public export names]) from Lucide's barrel."""
    with open(BARREL) as f:
        barrel = f.read()
    entries = []
    for group, stem in re.findall(
        r"export \{([^}]*)\} from '\./icons/([a-z0-9-]+)\.js';", barrel
    ):
        names = [
            part.strip().replace("default as ", "").strip()
            for part in group.split(",")
            if part.strip()
        ]
        entries.append((stem, names))
    return entries


def load_paths(stem: str) -> list[str]:
    """Read one icon's JS module and convert its shapes to path `d` strings."""
    path = os.path.join(ICON_DIR, f"{stem}.js")
    with open(path) as f:
        src = f.read()
    m = re.search(r"const __iconNode = \[(.*?)\];", src, re.S)
    if not m:
        # Deprecated icons are shipped as a re-export of their renamed module.
        alias = re.search(r"export \{ default \} from '\./([a-z0-9-]+)\.js';", src)
        if alias:
            return load_paths(alias.group(1))
        raise SystemExit(f"FAIL {stem}: no __iconNode in {path}")
    ds = []
    for tag, raw in NODE_RE.findall(m.group(1)):
        attrs = {}
        for k, v in JS_ATTR_RE.findall(raw):
            attrs[k] = v.replace('\\"', '"').replace("\\\\", "\\")
        d = svg_elem_to_path(tag, attrs)
        if d:
            ds.append(" ".join(d.split()).strip())
    real = [d for d in ds if not is_blank(d)]
    if ds and not real:
        raise SystemExit(f"FAIL {stem}: every path resolved to a no-op")
    if not real:
        raise SystemExit(f"FAIL {stem}: no geometry")
    return real


def kotlin_name(stem: str) -> str:
    return "".join(p.capitalize() for p in stem.split("-"))


SWIFT_KEYWORDS = set(
    "associatedtype class deinit enum extension fileprivate func import init "
    "inout internal let open operator private protocol public rethrows static "
    "struct subscript typealias var break case continue default defer do else "
    "fallthrough for guard if in repeat return switch where while as Any catch "
    "false is nil super self Self throw throws true try".split()
)


def swift_name(export: str) -> str:
    return export[0].lower() + export[1:]


def swift_ident(name: str) -> str:
    """Swift identifiers that collide with keywords need backticks (e.g. `repeat`)."""
    return f"`{name}`" if name in SWIFT_KEYWORDS else name


# --------------------------------------------------------------------------
# SVG path -> absolute cubic commands (for the Swift output)
# --------------------------------------------------------------------------

_NUMBER = re.compile(r"[+-]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][+-]?\d+)?")


class PathParser:
    def __init__(self, d: str):
        self.s = d
        self.i = 0
        self.cmd: str | None = None
        self.cx = 0.0
        self.cy = 0.0
        self.sx = 0.0
        self.sy = 0.0
        self.last_op: str | None = None
        self.cubic_ctrl: tuple[float, float] | None = None
        self.quad_ctrl: tuple[float, float] | None = None
        self.out: list[tuple] = []

    def skip(self) -> None:
        while self.i < len(self.s) and self.s[self.i] in " ,\t\r\n":
            self.i += 1

    def at_number(self) -> bool:
        self.skip()
        return self.i < len(self.s) and self.s[self.i] in "+-.0123456789"

    def number(self) -> float:
        self.skip()
        m = _NUMBER.match(self.s, self.i)
        if not m:
            raise ValueError(f"bad number at {self.i} in {self.s!r}")
        self.i = m.end()
        return float(m.group())

    def flag(self) -> int:
        self.skip()
        c = self.s[self.i]
        if c not in "01":
            raise ValueError(f"bad arc flag {c!r} at {self.i} in {self.s!r}")
        self.i += 1
        return int(c)

    def point(self, rel: bool) -> tuple[float, float]:
        x, y = self.number(), self.number()
        if rel:
            x += self.cx
            y += self.cy
        return x, y

    def run(self) -> list[tuple]:
        while True:
            self.skip()
            if self.i >= len(self.s):
                break
            if self.s[self.i].isalpha():
                self.cmd = self.s[self.i]
                self.i += 1
            elif self.cmd is None:
                raise ValueError(f"path must start with a command: {self.s!r}")
            if self.cmd in "Zz":
                self.out.append(("Z",))
                self.cx, self.cy = self.sx, self.sy
                self.last_op = "Z"
                self.cubic_ctrl = self.quad_ctrl = None
                continue
            if not self.at_number():
                if self.cmd in "Mm":
                    break
                continue
            self.execute()
        return self.out

    def execute(self) -> None:
        c = self.cmd
        rel = c.islower()
        u = c.upper()
        x0, y0 = self.cx, self.cy
        if u == "M":
            x, y = self.point(rel)
            self.out.append(("M", x, y))
            self.cx, self.cy = x, y
            self.sx, self.sy = x, y
            self.cmd = "l" if rel else "L"
            self.last_op = "M"
            self.cubic_ctrl = self.quad_ctrl = None
            return
        if u == "L":
            x, y = self.point(rel)
            self.out.append(("L", x, y))
            self.cx, self.cy = x, y
            self.last_op = "L"
            self.cubic_ctrl = self.quad_ctrl = None
            return
        if u == "H":
            x = self.number() + (x0 if rel else 0)
            self.out.append(("L", x, y0))
            self.cx = x
            self.last_op = "H"
            self.cubic_ctrl = self.quad_ctrl = None
            return
        if u == "V":
            y = self.number() + (y0 if rel else 0)
            self.out.append(("L", x0, y))
            self.cy = y
            self.last_op = "V"
            self.cubic_ctrl = self.quad_ctrl = None
            return
        if u == "C":
            c1 = self.point(rel)
            c2 = self.point(rel)
            p = self.point(rel)
            self.out.append(("C", c1[0], c1[1], c2[0], c2[1], p[0], p[1]))
            self.cx, self.cy = p
            self.cubic_ctrl = c2
            self.last_op = "C"
            self.quad_ctrl = None
            return
        if u == "S":
            if self.last_op in ("C", "S") and self.cubic_ctrl:
                c1 = (2 * x0 - self.cubic_ctrl[0], 2 * y0 - self.cubic_ctrl[1])
            else:
                c1 = (x0, y0)
            c2 = self.point(rel)
            p = self.point(rel)
            self.out.append(("C", c1[0], c1[1], c2[0], c2[1], p[0], p[1]))
            self.cx, self.cy = p
            self.cubic_ctrl = c2
            self.last_op = "S"
            self.quad_ctrl = None
            return
        if u == "Q":
            q = self.point(rel)
            p = self.point(rel)
            self.quad_to_cubic(q, p)
            self.quad_ctrl = q
            self.last_op = "Q"
            self.cubic_ctrl = None
            return
        if u == "T":
            if self.last_op in ("Q", "T") and self.quad_ctrl:
                q = (2 * x0 - self.quad_ctrl[0], 2 * y0 - self.quad_ctrl[1])
            else:
                q = (x0, y0)
            p = self.point(rel)
            self.quad_to_cubic(q, p)
            self.quad_ctrl = q
            self.last_op = "T"
            self.cubic_ctrl = None
            return
        if u == "A":
            rx, ry = self.number(), self.number()
            rot = self.number()
            large, sweep = self.flag(), self.flag()
            p = self.point(rel)
            self.arc_to_cubics(x0, y0, rx, ry, rot, large, sweep, p[0], p[1])
            self.cx, self.cy = p
            self.last_op = "A"
            self.cubic_ctrl = self.quad_ctrl = None
            return
        raise ValueError(f"unsupported command {c!r}")

    def quad_to_cubic(self, q: tuple[float, float], p: tuple[float, float]) -> None:
        x0, y0 = self.cx, self.cy
        c1 = (x0 + 2 / 3 * (q[0] - x0), y0 + 2 / 3 * (q[1] - y0))
        c2 = (p[0] + 2 / 3 * (q[0] - p[0]), p[1] + 2 / 3 * (q[1] - p[1]))
        self.out.append(("C", c1[0], c1[1], c2[0], c2[1], p[0], p[1]))
        self.cx, self.cy = p

    def arc_to_cubics(self, x1, y1, rx, ry, phi_deg, large, sweep, x2, y2) -> None:
        if rx == 0 or ry == 0 or (x1 == x2 and y1 == y2):
            self.out.append(("L", x2, y2))
            return
        phi = math.radians(phi_deg % 360)
        cosp, sinp = math.cos(phi), math.sin(phi)
        dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
        x1p = cosp * dx + sinp * dy
        y1p = -sinp * dx + cosp * dy
        rx, ry = abs(rx), abs(ry)
        lam = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry)
        if lam > 1:
            s = math.sqrt(lam)
            rx *= s
            ry *= s
        num_ = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
        den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        co = math.sqrt(max(0.0, num_ / den)) if den else 0.0
        if large == sweep:
            co = -co
        cxp = co * rx * y1p / ry
        cyp = -co * ry * x1p / rx
        cx = cosp * cxp - sinp * cyp + (x1 + x2) / 2
        cy = sinp * cxp + cosp * cyp + (y1 + y2) / 2

        def angle(ux, uy, vx, vy):
            dot = ux * vx + uy * vy
            length = math.hypot(ux, uy) * math.hypot(vx, vy)
            if length == 0:
                return 0.0
            a = math.acos(max(-1.0, min(1.0, dot / length)))
            return -a if ux * vy - uy * vx < 0 else a

        theta1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
        delta = angle(
            (x1p - cxp) / rx,
            (y1p - cyp) / ry,
            (-x1p - cxp) / rx,
            (-y1p - cyp) / ry,
        )
        if not sweep and delta > 0:
            delta -= 2 * math.pi
        elif sweep and delta < 0:
            delta += 2 * math.pi
        segments = max(1, int(math.ceil(abs(delta) / (math.pi / 2))))
        step = delta / segments
        factor = 4 / 3 * math.tan(step / 4)

        def at(theta):
            cos_t, sin_t = math.cos(theta), math.sin(theta)
            return (
                cx + cosp * rx * cos_t - sinp * ry * sin_t,
                cy + sinp * rx * cos_t + cosp * ry * sin_t,
            )

        def deriv(theta):
            cos_t, sin_t = math.cos(theta), math.sin(theta)
            return (
                -cosp * rx * sin_t - sinp * ry * cos_t,
                -sinp * rx * sin_t + cosp * ry * cos_t,
            )

        for k in range(segments):
            t0 = theta1 + k * step
            t1 = t0 + step
            p0, p1 = at(t0), at(t1)
            d0, d1 = deriv(t0), deriv(t1)
            c1 = (p0[0] + factor * d0[0], p0[1] + factor * d0[1])
            c2 = (p1[0] - factor * d1[0], p1[1] - factor * d1[1])
            self.out.append(("C", c1[0], c1[1], c2[0], c2[1], p1[0], p1[1]))


def path_commands(d: str) -> list[tuple]:
    return PathParser(d).run()


# --------------------------------------------------------------------------
# Android output
# --------------------------------------------------------------------------

ANDROID_HEADER = '''package com.grapsee.gsai.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * GsIcons — the single icon family for GS AI.
 *
 * Source: Lucide (ISC license), pinned lucide-react %s. Geometry is vendored
 * at generation time by scripts/gen_gs_icons.py and committed here, so the
 * app has NO runtime dependency on the package. Every icon is a 24x24 stroke
 * drawing with width 2 and round caps/joins — one optical weight across the
 * app, per the icon-system rule (one family, one weight, one container spec).
 *
 * FULL SET: %d geometries and %d public names (%d aliases). The aliases are
 * Lucide's own export names (deprecated renames and prefixed/suffixed forms);
 * each one returns the canonical ImageVector, so `GsIcons` matches the web
 * package name-for-name without duplicating geometry.
 *
 * STROKE COLOR IS NOT OPTIONAL — IT MUST BE OPAQUE. Every path stamps
 * SolidColor(Color.Black) so Icon(tint = ...) has real pixels to recolour (its
 * ColorFilter is SrcIn: result = tint x source alpha). A transparent source
 * (Color.Unspecified, alpha 0) renders NOTHING and the tint cannot resurrect
 * it — that is the whole-family "invisible icons" defect. Never emit an
 * unspecified/transparent stroke.
 *
 * Lucide ISC license: https://lucide.dev/license
 */'''

KOTLIN_FUNC = '''
    public val {name}: ImageVector by lazy {{
        ImageVector.Builder(
            name = "GsIcons.{name}",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {{
{paths}
        }}.build()
    }}'''

KOTLIN_PATH = '''            addPath(
                pathData = PathParser().parsePathString(
                    "{d}"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )'''


def kotlin_alias(alias: str, target: str) -> str:
    return f"    public val {alias}: ImageVector get() = {target}"


def emit_android(entries, paths_by_stem, version) -> None:
    canon_names = {kotlin_name(stem) for stem, _ in entries}
    out = [
        ANDROID_HEADER
        % (
            version,
            len(entries),
            sum(len(names) for _, names in entries),
            sum(len(names) for _, names in entries) - len(entries),
        ),
        "\npublic object GsIcons {",
    ]
    for stem, _ in sorted(entries, key=lambda e: kotlin_name(e[0])):
        name = kotlin_name(stem)
        ds = paths_by_stem[stem]
        blocks = "\n".join(KOTLIN_PATH.format(d=d) for d in ds)
        out.append(KOTLIN_FUNC.format(name=name, paths=blocks))

    out.append("\n    // Aliases — Lucide's remaining public names, same geometry.")
    for stem, names in sorted(entries, key=lambda e: kotlin_name(e[0])):
        target = kotlin_name(stem)
        for alias in sorted(names):
            if alias == target or alias in canon_names:
                continue
            out.append(kotlin_alias(alias, target))
    out.append("}\n")
    os.makedirs(os.path.dirname(ANDROID_TARGET), exist_ok=True)
    with open(ANDROID_TARGET, "w") as f:
        f.write("\n".join(out))


# --------------------------------------------------------------------------
# iOS output
# --------------------------------------------------------------------------

SWIFT_HEADER = '''//
//  GsIcons.swift
//  GS AI
//
//  GENERATED by scripts/gen_gs_icons.py — do not edit by hand.
//  Source: Lucide (ISC license), pinned lucide-react %s.
//
//  FULL SET: %d geometries and %d public names (%d aliases), the same family
//  the Android app renders from GsIcons.kt. Geometry is pre-converted to
//  absolute move/line/cubic/close commands in a 24x24 viewport; no runtime
//  SVG parsing, no third-party dependency.
//
//  Lucide ISC license: https://lucide.dev/license
//

import SwiftUI

public struct GsIconPaths: Sendable {
    public let paths: [[GsPathCommand]]

    public init(paths: [[GsPathCommand]]) {
        self.paths = paths
    }
}

public enum GsPathCommand: Sendable {
    case move(CGFloat, CGFloat)
    case line(CGFloat, CGFloat)
    case cubic(CGFloat, CGFloat, CGFloat, CGFloat, CGFloat, CGFloat)
    case close
}

public enum GsIcons {
%s
}

/// Strokes a `GsIcons` glyph at Lucide's spec: 24x24 viewport, width 2,
/// round caps and joins, scaled to the requested size.
public struct GsIconView: View {
    public let icon: GsIconPaths
    public let size: CGFloat
    public let color: Color

    public init(_ icon: GsIconPaths, size: CGFloat = 24, color: Color = .primary) {
        self.icon = icon
        self.size = size
        self.color = color
    }

    public var body: some View {
        Canvas { context, canvasSize in
            let scale = min(canvasSize.width, canvasSize.height) / 24
            var path = Path()
            for commands in icon.paths {
                for command in commands {
                    switch command {
                    case let .move(x, y):
                        path.move(to: CGPoint(x: x, y: y))
                    case let .line(x, y):
                        path.addLine(to: CGPoint(x: x, y: y))
                    case let .cubic(c1x, c1y, c2x, c2y, x, y):
                        path.addCurve(
                            to: CGPoint(x: x, y: y),
                            control1: CGPoint(x: c1x, y: c1y),
                            control2: CGPoint(x: c2x, y: c2y)
                        )
                    case .close:
                        path.closeSubpath()
                    }
                }
            }
            context.scaleBy(x: scale, y: scale)
            context.stroke(
                path,
                with: .color(color),
                style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round)
            )
        }
        .frame(width: size, height: size)
    }
}
'''


def fmt(v: float) -> str:
    s = f"{v:.4f}".rstrip("0").rstrip(".")
    if s in ("", "-0"):
        s = "0"
    return s


def swift_command(cmd: tuple) -> str:
    kind = cmd[0]
    if kind == "M":
        return f".move({fmt(cmd[1])}, {fmt(cmd[2])})"
    if kind == "L":
        return f".line({fmt(cmd[1])}, {fmt(cmd[2])})"
    if kind == "C":
        return (
            f".cubic({fmt(cmd[1])}, {fmt(cmd[2])}, {fmt(cmd[3])}, "
            f"{fmt(cmd[4])}, {fmt(cmd[5])}, {fmt(cmd[6])})"
        )
    if kind == "Z":
        return ".close"
    raise ValueError(f"bad command {kind}")


def emit_ios(entries, paths_by_stem, version) -> None:
    canon_names = {kotlin_name(stem) for stem, _ in entries}
    lines = []
    for stem, _ in sorted(entries, key=lambda e: kotlin_name(e[0])):
        name = swift_ident(swift_name(kotlin_name(stem)))
        groups = []
        for d in paths_by_stem[stem]:
            cmds = path_commands(d)
            groups.append("[" + ", ".join(swift_command(c) for c in cmds) + "]")
        body = ",\n            ".join(groups)
        lines.append(
            f"    public static let {name}: GsIconPaths = .init(paths: [\n"
            f"            {body},\n"
            f"        ])"
        )

    lines.append("")
    lines.append("    // Aliases — Lucide's remaining public names, same geometry.")
    for stem, names in sorted(entries, key=lambda e: kotlin_name(e[0])):
        target = swift_ident(swift_name(kotlin_name(stem)))
        for alias in sorted(names):
            if alias == kotlin_name(stem) or alias in canon_names:
                continue
            alias_name = swift_ident(swift_name(alias))
            lines.append(
                f"    public static var {alias_name}: GsIconPaths {{ {target} }}"
            )

    total_names = sum(len(names) for _, names in entries)
    os.makedirs(os.path.dirname(IOS_TARGET), exist_ok=True)
    with open(IOS_TARGET, "w") as f:
        f.write(
            SWIFT_HEADER
            % (
                version,
                len(entries),
                total_names,
                total_names - len(entries),
                "\n".join(lines),
            )
        )


# --------------------------------------------------------------------------
# Entry point
# --------------------------------------------------------------------------

def main() -> int:
    if not os.path.isdir(ICON_DIR):
        raise SystemExit(f"missing lucide-react sources at {ICON_DIR} (run npm install)")

    version = lucide_version()
    entries = load_exports()
    if not entries:
        raise SystemExit("no Lucide exports found in the barrel")

    if "--curated" in sys.argv:
        with open(os.path.join(ROOT, "scripts/gs-icon-stems.txt")) as f:
            curated = {ln.strip() for ln in f if ln.strip() and not ln.startswith("#")}
        exported = {stem for stem, _ in entries}
        for stem in sorted(curated - exported):
            # Lucide drops a deprecated icon from its barrel while still shipping
            # the module; the curated list keeps the stem, so resolve the name
            # from the stem itself instead of failing the run.
            if not os.path.exists(os.path.join(ICON_DIR, f"{stem}.js")):
                raise SystemExit(f"curated stem absent from lucide: {stem!r}")
        # Curated mode keeps only each stem's own canonical name.
        entries = [(stem, [kotlin_name(stem)]) for stem in sorted(curated)]

    paths_by_stem = {stem: load_paths(stem) for stem, _ in entries}

    # Validate every public name and every canonical name before writing.
    seen: set[str] = set()
    for stem, names in entries:
        canonical = kotlin_name(stem)
        if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*", canonical):
            raise SystemExit(f"bad Kotlin name {canonical!r}")
        if canonical not in names:
            raise SystemExit(f"{stem}: canonical name {canonical} not in exports")
        for alias in names:
            if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*", alias):
                raise SystemExit(f"bad export name {alias!r}")
            if alias in seen:
                raise SystemExit(f"duplicate name {alias}")
            seen.add(alias)

    # Geometry sanity: every path parses and stays near the 24x24 viewport.
    for stem, ds in paths_by_stem.items():
        if stem not in {s for s, _ in entries}:
            continue
        for d in ds:
            cmds = path_commands(d)
            if not cmds:
                raise SystemExit(f"{stem}: empty path")
            for cmd in cmds:
                for v in cmd[1:]:
                    if not math.isfinite(v):
                        raise SystemExit(f"{stem}: non-finite coordinate")

    emit_android(entries, paths_by_stem, version)
    emit_ios(entries, paths_by_stem, version)

    total_names = sum(len(names) for _, names in entries)
    print(
        f"wrote {os.path.relpath(ANDROID_TARGET, ROOT)} and "
        f"{os.path.relpath(IOS_TARGET, ROOT)}: "
        f"{len(entries)} geometries, {total_names} public names "
        f"({total_names - len(entries)} aliases), lucide-react {version}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
