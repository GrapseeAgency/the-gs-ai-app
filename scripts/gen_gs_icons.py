#!/usr/bin/env python3
"""Generate GsIcons.kt — a curated Lucide icon set as Compose ImageVectors.

Source: lucide-static (ISC license) at a pinned version. Each icon is fetched
once at generation time, its SVG geometry is converted to path data, and the
result is committed as Kotlin source. No runtime dependency on the package.

Every icon renders as a 24x24 stroke drawing, stroke-width 2, round caps and
joins — Lucide's spec — so the whole set has one optical weight.
"""
import os
import re
import sys
import urllib.request

VERSION = "0.469.0"
BASE = f"https://cdn.jsdelivr.net/npm/lucide-static@{VERSION}/icons"

# Curated set: every icon the redesigned surfaces (drawer, chat, composer,
# settings, sheets, states) actually renders. Keys are lucide-static file
# stems; Kotlin names are derived as PascalCase under object GsIcons.
ICONS = {
    # navigation / drawer
    "panel-left": "PanelLeft",
    "search": "Search",
    "plus": "Plus",
    "message-circle": "MessageCircle",
    "inbox": "Inbox",
    "chevron-right": "ChevronRight",
    "chevron-down": "ChevronDown",
    "chevron-up": "ChevronUp",
    "chevron-left": "ChevronLeft",
    "user-round": "UserRound",
    "settings": "Settings",
    "bell": "Bell",
    "credit-card": "CreditCard",
    "folder": "Folder",
    "bookmark": "Bookmark",
    "compass": "Compass",
    "square-pen": "SquarePen",
    "layers": "Layers",
    "book-open": "BookOpen",
    "users-round": "UsersRound",
    "audio-lines": "AudioLines",
    "ellipsis-vertical": "EllipsisVertical",
    "history": "History",
    # composer / chat
    "arrow-up": "ArrowUp",
    "mic": "Mic",
    "camera": "Camera",
    "image-plus": "ImagePlus",
    "paperclip": "Paperclip",
    "copy": "Copy",
    "check": "Check",
    "trash-2": "Trash2",
    "archive": "Archive",
    "pin": "Pin",
    "x": "X",
    "refresh-cw": "RefreshCw",
    "share": "Share",
    "globe": "Globe",
    "file-text": "FileText",
    "briefcase": "Briefcase",
    "archive-restore": "ArchiveRestore",
    "lightbulb": "Lightbulb",
    "presentation": "Presentation",
    "graduation-cap": "GraduationCap",
    "link": "Link",
    "hourglass": "Hourglass",
    "folder-plus": "FolderPlus",
    "circle-check": "CircleCheck",
    "phone-off": "PhoneOff",
    "wand": "Wand",
    "network": "Network",
    "mic-off": "MicOff",
    "heart": "Heart",
    "clipboard-list": "ClipboardList",
    "code": "Code",
    "star": "Star",
    "clock": "Clock",
    "text-search": "TextSearch",
    "quote": "Quote",
    "table": "Table",
    "list": "List",
    # modes / sheets
    "zap": "Zap",
    "image": "Image",
    "pen-line": "PenLine",
    "brain": "Brain",
    "languages": "Languages",
    "telescope": "Telescope",
    "sliders-horizontal": "SlidersHorizontal",
    "layout-grid": "LayoutGrid",
    "square": "Square",
    "lock": "Lock",
    "accessibility": "Accessibility",
    "split": "Split",
    "fingerprint": "Fingerprint",
    "mail": "Mail",
    "mail-check": "MailCheck",
    "smartphone": "Smartphone",
    "gauge": "Gauge",
    "eye": "Eye",
    "eye-off": "EyeOff",
    "lightbulb": "Lightbulb",
    "notebook-pen": "NotebookPen",
    "file-text": "FileText",
    "briefcase": "Briefcase",
    "archive-restore": "ArchiveRestore",
    "lightbulb": "Lightbulb",
    "presentation": "Presentation",
    "graduation-cap": "GraduationCap",
    "link": "Link",
    "hourglass": "Hourglass",
    "folder-plus": "FolderPlus",
    "circle-check": "CircleCheck",
    "phone-off": "PhoneOff",
    "wand": "Wand",
    "network": "Network",
    "mic-off": "MicOff",
    "heart": "Heart",
    "clipboard-list": "ClipboardList",
    "file": "File",
    "image-off": "ImageOff",
    "circle-check": "CircleCheck",
    "search-x": "SearchX",
    "pencil": "Pencil",
    # states / system
    "shield": "Shield",
    "info": "Info",
    "log-out": "LogOut",
    "flag": "Flag",
    "palette": "Palette",
    "volume-2": "Volume2",
    "volume-x": "VolumeX",
    "download": "Download",
    "circle-alert": "CircleAlert",
    "wifi-off": "WifiOff",
    "loader-circle": "LoaderCircle",
    "keyboard": "Keyboard",
    "moon": "Moon",
    "sun": "Sun",
}
# RULE ZERO: no sparkle icons in UI chrome — never add one here.


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
        return (f"M{num(x)},{num(y)} a{rr},{rr} 0 1,0 {num(2*r)},0 "
                f"a{rr},{rr} 0 1,0 {num(-2*r)},0")
    if tag == "ellipse":
        cx, cy = float(attrs.get("cx", "0")), float(attrs.get("cy", "0"))
        rx, ry = float(attrs.get("rx", "0")), float(attrs.get("ry", "0"))
        x, y = cx - rx, cy
        return (f"M{num(x)},{num(y)} a{num(rx)},{num(ry)} 0 1,0 {num(2*rx)},0 "
                f"a{num(rx)},{num(ry)} 0 1,0 {num(-2*rx)},0")
    if tag == "rect":
        x, y = float(attrs.get("x", "0")), float(attrs.get("y", "0"))
        w, h = float(attrs.get("width", "0")), float(attrs.get("height", "0"))
        rx = float(attrs.get("rx", attrs.get("ry", "0")) or 0)
        if rx > 0:
            rx = min(rx, w / 2, h / 2)
            return (
                f"M{num(x+rx)},{num(y)} H{num(x+w-rx)} A{num(rx)},{num(rx)} 0 0 1 {num(x+w)},{num(y+rx)} "
                f"V{num(y+h-rx)} A{num(rx)},{num(rx)} 0 0 1 {num(x+w-rx)},{num(y+h)} "
                f"H{num(x+rx)} A{num(rx)},{num(rx)} 0 0 1 {num(x)},{num(y+h-rx)} "
                f"V{num(y+rx)} A{num(rx)},{num(rx)} 0 0 1 {num(x+rx)},{num(y)} Z"
            )
        return f"M{num(x)},{num(y)} H{num(x+w)} V{num(y+h)} H{num(x)} Z"
    return None


ATTR_RE = re.compile(r'([a-zA-Z-]+)="([^"]*)"')
TAG_RE = re.compile(r"<(path|line|polyline|polygon|circle|ellipse|rect)\b([^/>]*)/?>")


def fetch_icon(stem: str) -> list[str] | None:
    url = f"{BASE}/{stem}.svg"
    try:
        with urllib.request.urlopen(url, timeout=30) as r:
            svg = r.read().decode()
    except Exception as e:  # noqa: BLE001
        print(f"FAIL {stem}: {e}", file=sys.stderr)
        return None
    ds = []
    for m in TAG_RE.finditer(svg):
        tag, raw = m.group(1), m.group(2)
        attrs = dict(ATTR_RE.findall(raw))
        d = svg_elem_to_path(tag, attrs)
        if d:
            ds.append(" ".join(d.split()).strip())
    return ds or None


def kotlin_name(stem: str) -> str:
    return "".join(p.capitalize() for p in stem.split("-"))


HEADER = '''package com.grapsee.gsai.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * GsIcons — the single icon family for GS AI (design phase 2).
 *
 * Source: Lucide (ISC license), pinned lucide-static %s. Geometry is vendored
 * at generation time by scripts/gen_gs_icons.py and committed here, so the
 * app has NO runtime dependency on the package. Every icon is a 24x24 stroke
 * drawing with width 2 and round caps/joins — one optical weight across the
 * app, per the icon-system rule (one family, one weight, one container spec).
 *
 * The set is CURATED: only icons rendered by shipped screens are included.
 * Add to scripts/gen_gs_icons.py ICONS and regenerate; never hand-edit paths.
 *
 * Lucide ISC license: https://lucide.dev/license
 */'''

FUNC = '''
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

PATH_BLOCK = '''            addPath(
                pathData = PathParser().parsePathString(
                    "{d}"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )'''


def main() -> int:
    out = [HEADER % VERSION, "\npublic object GsIcons {"]
    missing = []
    for stem in ICONS:
        ds = fetch_icon(stem)
        if ds is None:
            missing.append(stem)
            continue
        paths = "\n".join(PATH_BLOCK.format(d=d) for d in ds)
        out.append(FUNC.format(name=kotlin_name(stem), paths=paths))
    out.append("}\n")
    target = "android/app/src/main/java/com/grapsee/gsai/ui/icons/GsIcons.kt"
    os.makedirs(os.path.dirname(target), exist_ok=True)
    with open(target, "w") as f:
        f.write("\n".join(out))
    print(f"wrote {target}: {len(ICONS) - len(missing)} icons, missing: {missing or 'none'}")
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
