#!/usr/bin/env python3
"""Account for the app's bitmap artwork: one authored source, one resolution, both platforms.

WHY THIS EXISTS
---------------
Home ships a full-bleed backdrop plus four pieces of card art, and the agent and
welcome pages ship two orbs. They got there by hand: an image was generated,
pasted into `res/drawable-nodpi/`, pasted again into an `.imageset`, and nobody
asked what either copy cost. Three separate defects fell out of that, all of them
found by measuring rather than by trusting the file names:

  1. `gs_home_backdrop.png` was committed three times at 1179x2556 — the exact
     pixel size of a 393x852 pt screen at 3x — so it decoded to 12.1 MB of
     ARGB_8888 on every platform that drew it.
  2. The two orbs were committed at 1920x1920 for a 168 pt draw.
  3. The Android and iOS copies of one asset had drifted to different
     resolutions, so "identical on both platforms" was true of the layout and
     false of the pixels underneath it — and the pixel gate divides its `lift`
     metric by the ground read out of that backdrop file, so a drift there moves
     the measurement and the artwork at the same time.

WHAT IT ASSERTS
---------------
`--check` (the CI mode) fails, naming the number that moved, if:

  * a shipped copy is missing, or is not the target resolution, or its authored
    source is smaller than that target (an upscale from source invents detail);
  * the two platforms' copies of one asset are not byte-identical;
  * the target cannot cover its declared largest on-screen footprint once the
    declared upscale allowance is applied — so under-resolution is a decision
    with a number next to it, never an accident;
  * a shipped raster is referenced by no source file and is not named in
    DEAD_ALLOW_LIST, or a manifest asset is missing from either platform;
  * the referenced set exceeds its screen budget.

`--write` regenerates every shipped copy from its authored source at the target
size, so a pair can be re-derived after a new render instead of hand-pasted.

WHAT IT DELIBERATELY DOES NOT DO
--------------------------------
It does not shrink the authored files under `assets/generated/`: those are the
source of truth and stay at full resolution. It does not touch the iOS
`Contents.json` scale tags either — a scale tag sets an image's intrinsic point
size, not its decoded cost, and every call site here frames its image explicitly.
"""
from __future__ import annotations

import hashlib
import sys
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
GENERATED = ROOT / "assets" / "generated"
ANDROID_RES = ROOT / "android" / "app" / "src" / "main" / "res" / "drawable-nodpi"
IOS_ASSETS = ROOT / "ios" / "App" / "Assets.xcassets"
ANDROID_SRC = ROOT / "android" / "app" / "src" / "main" / "java"
IOS_SRC = ROOT / "ios" / "App" / "Sources"

DENSITY = 3.0          # the harness and every phone-class target render at 3x
BYTES_PER_PIXEL = 4    # ARGB_8888 on Android, and RGBA8 for a CGContext draw on iOS

# Home is the page the pixel gate measures; its art is decoded the moment the
# route appears, whether or not a card is on screen.
HOME_BUDGET_MB = 9.0
# Everything this manifest accounts for, per platform, at 3x. Screens that draw
# more than one orb at a time would need this raised with a reason.
ALL_BUDGET_MB = 14.0

# Dead art that predates this script, named so it cannot grow. Unreferenced
# drawables are stripped from the release APK by `isShrinkResources = true`, so
# the Android entries cost repo bytes only.
DEAD_ALLOW_LIST = {
    "android": {"gs_home_orb", "gs_home_lotus", "gs_video_create",
                "gs_companion_orb", "gs_travel_agent_orb"},
    "ios": set(),
}

# The iOS twins of that art used to ship: actool compiles every .imageset in the
# catalog into Assets.car whether or not code asks for it, so 4.2 MB of PNG
# decoded to 16.6 MB inside the app for nothing. Deleting a PNG is not something
# this workspace's tooling can do, but *moving the imageset out of the catalog*
# is, and the result is the same: XcodeGen never sees it, the art stays in the
# repo at its authored resolution, and restoring one is a `git mv` back.
PARKED_IOS = {
    "GSVideoCreate": "4.2 MB on disk, no reference in any Swift or Kotlin source",
    "GSHomeOrb": "the logo orb is drawn by ReferenceBackdrop, not from this art",
    "GSHomeLotus": "superseded by GSHomeLotusNew, which the reflection card draws",
}
PARKED_DIR = Path("ios/Parked-imagesets")


@dataclass(frozen=True)
class Art:
    name: str            # android drawable name; the iOS file is the dashed twin
    ios_set: str
    authored: str        # path under assets/generated/
    target: tuple[int, int]
    need: tuple[int, int]   # largest footprint the app can give it, in pixels
    upscale: float          # declared tolerance: target may undershoot need by this
    screens: str
    note: str
    flip_v: bool = False    # the shipped copy is the authored art upside down

    @property
    def android_path(self) -> Path:
        return ANDROID_RES / f"{self.name}.png"

    @property
    def ios_path(self) -> Path:
        return IOS_ASSETS / f"{self.ios_set}.imageset" / f"{self.name.replace('_', '-')}.png"

    @property
    def source(self) -> Path:
        return GENERATED / self.authored


MANIFEST = [
    Art("gs_home_backdrop", "GSHomeBackdrop", "home/gs-home-backdrop.png",
        (590, 1278), (1179, 2556), 2.2, "Home",
        "Full-bleed, drawn 393x852 pt at 3x. Halving the linear resolution costs "
        "a median 0.18 L and never more than 2.8 L on the whole frame — zero "
        "pixels above 3 L — and saves 9.1 MB decoded. 2.2x is the upscale a "
        "430 pt screen applies to it; the gate measures the page at 393 pt, "
        "where it is exactly 2x."),
    Art("gs_home_avatar", "GSHomeAvatar", "home/gs-home-avatar.png",
        (336, 336), (126, 126), 1.0, "Home",
        "Header lockup: a fixed 42 pt frame that no Dynamic Type curve touches."),
    Art("gs_home_mind_orb", "GSHomeMindOrb", "home/gs-home-mind-orb.png",
        (336, 336), (183, 183), 1.0, "Home",
        "Mind Guide orb, 38 dp/pt and 61 pt at the design system's 1.6x chrome cap."),
    Art("gs_home_video_art", "GSHomeVideoArt", "home/gs-home-video-art.png",
        (864, 560), (600, 462), 1.0, "Home",
        "Video card art, in a fixed 200 pt box inside the 214 pt card. Drawn "
        "Crop, not Fit — see the note on VideoCard in HomeDashboardScreen.kt — and "
        "SHIPPED UPSIDE DOWN. The flip is decided by the art region's chroma-map "
        "correlation with the reference card, measured on real renders: 0.372 as "
        "authored, 0.604 flipped. An earlier retraction of this flip used the "
        "card's mean chroma, p90 and luminance, which move by 0.0 between the two "
        "orientations because they do not see arrangement at all; that retraction "
        "was wrong and is recorded in docs/HOME_REFERENCE_STATUS.md.",
        flip_v=True),
    Art("gs_home_lotus_new", "GSHomeLotusNew", "home/gs-home-lotus.png",
        (880, 416), (720, 480), 1.2, "Home",
        "Reflection card: 150x100 dp/pt, 240x160 pt at the chrome cap. The "
        "authored art is 880x416, so 480 px of height would be an upscale from "
        "source rather than a resample; 1.2x is declared, not assumed."),
    Art("gs_companion_orb", "GSCompanionOrb", "gs-companion-orb.png",
        (768, 768), (504, 504), 1.0, "welcome/onboarding/chat",
        "Authored 1920x1920 (14.7 MB decoded) for a 168 pt draw. Android does not "
        "reference it; the copy is kept at the iOS resolution so the pair cannot "
        "drift, and shrinking a drawable Android never loads costs repo bytes only."),
    Art("gs_travel_agent_orb", "GSTravelAgentOrb", "gs-travel-agent-orb.png",
        (768, 768), (492, 492), 1.0, "travel agent",
        "Same story at 164 pt."),
    Art("gs_explore_hero", "GSExploreHero", "explore/gs-explore-hero.png",
        (466, 380), (699, 570), 1.5, "explore",
        "The Explore reference's own iridescent bubbles, cropped from the "
        "authored mock (docs/reference/explore), carry the hero card's right "
        "side. Fixed 233x190 dp frame inside fixed-width cards; need is the "
        "3x draw, target is the reference's own resolution at 2x."),
    Art("gs_explore_col_design", "GSExploreColDesign", "explore/gs-explore-col-design.png",
        (336, 172), (504, 258), 1.5, "explore",
        "Collection scene (Design & Visuals): the reference's lotus band, "
        "cropped from the authored mock. Fixed 168x86 dp tiles."),
    Art("gs_explore_col_work", "GSExploreColWork", "explore/gs-explore-col-work.png",
        (336, 172), (504, 258), 1.5, "explore",
        "Collection scene (Work & Productivity): the reference's misty "
        "pagoda landscape band. Fixed 168x86 dp tiles."),
    Art("gs_explore_col_learning", "GSExploreColLearning", "explore/gs-explore-col-learning.png",
        (336, 172), (504, 258), 1.5, "explore",
        "Collection scene (Learning & Growth): the reference's orange lotus "
        "close-up band. Fixed 168x86 dp tiles."),
    Art("gs_explore_kyoto", "GSExploreKyoto", "explore/gs-explore-kyoto.png",
        (304, 196), (456, 294), 1.5, "explore",
        "Featured journey card: the reference's Kyoto pagoda scene fills the "
        "card's right side under a pearl veil."),
    Art("gs_explore_backdrop", "GSExploreBackdrop", "explore/gs-explore-backdrop.png",
        (590, 1278), (1179, 2556), 2.2, "explore",
        "Explore's own backdrop: the Home pearl world with the reference's "
        "iridescent bubbles composited in from the authored mock (hero "
        "cluster top-right, the giant right-edge bubble, the bottom-left "
        "pearl) — the page reads as luminous as the reference does."),
]


def md5(path: Path) -> str:
    return hashlib.md5(path.read_bytes()).hexdigest()


def decoded_mb(path: Path) -> float:
    with Image.open(path) as im:
        return im.size[0] * im.size[1] * BYTES_PER_PIXEL / 1e6


def source_names() -> tuple[set[str], set[str]]:
    """Every drawable/imageset name the two platforms' sources mention."""
    android, ios = set(), set()
    for p in list(ANDROID_SRC.rglob("*.kt")) + list(ANDROID_SRC.rglob("*.xml")):
        t = p.read_text(encoding="utf-8", errors="ignore")
        for marker in ("R.drawable.", "android:drawable=", "@drawable/"):
            for chunk in t.split(marker)[1:]:
                token = ""
                for ch in chunk:
                    if ch.isalnum() or ch == "_":
                        token += ch
                    else:
                        break
                if token:
                    android.add(token)
    for p in IOS_SRC.rglob("*.swift"):
        t = p.read_text(encoding="utf-8", errors="ignore")
        for marker in ('Image("', 'imageName: "'):
            for chunk in t.split(marker)[1:]:
                token = chunk.split('"')[0]
                if token:
                    ios.add(token)
    return android, ios


def write(manifest: list[Art]) -> int:
    for a in manifest:
        if not a.source.is_file():
            print(f"FAIL  {a.name}: authored source missing: {a.source}")
            return 1
        with Image.open(a.source) as src:
            img = src.convert("RGBA" if "A" in src.getbands() else "RGB")
            if img.size[0] < a.target[0] or img.size[1] < a.target[1]:
                print(f"FAIL  {a.name}: authored source {img.size} is smaller "
                      f"than target {a.target}")
                return 1
            if img.size != a.target:
                img = img.resize(a.target, Image.LANCZOS)
            if a.flip_v:
                img = img.transpose(Image.FLIP_TOP_BOTTOM)
            for dest in (a.android_path, a.ios_path):
                dest.parent.mkdir(parents=True, exist_ok=True)
                img.save(dest, optimize=True)
        print(f"wrote {a.name} -> {a.target}  (android {md5(a.android_path)[:8]}, "
              f"ios {md5(a.ios_path)[:8]})")
    return 0


def derived(a: Art) -> np.ndarray:
    """What the manifest says the shipped copy should be, as a pixel array."""
    with Image.open(a.source) as src:
        img = src.convert("RGBA" if "A" in src.getbands() else "RGB")
        if img.size != a.target:
            img = img.resize(a.target, Image.LANCZOS)
        if a.flip_v:
            img = img.transpose(Image.FLIP_TOP_BOTTOM)
        return np.asarray(img).astype(float)


def check(manifest: list[Art]) -> int:
    problems: list[str] = []
    cost = {"android": {}, "ios": {}}
    live = {"android": set(), "ios": set()}
    used_android, used_ios = source_names()

    for a in manifest:
        for side, path, name in (("android", a.android_path, a.name),
                                 ("ios", a.ios_path, a.ios_set)):
            if not path.is_file():
                problems.append(f"{a.name}: {side} copy missing ({path.name})")
                continue
            with Image.open(path) as im:
                if im.size != a.target:
                    problems.append(
                        f"{a.name}: {side} copy is {im.size[0]}x{im.size[1]}, "
                        f"manifest says {a.target[0]}x{a.target[1]} "
                        f"(run --write)")
                if im.size[0] < a.need[0] / a.upscale or \
                   im.size[1] < a.need[1] / a.upscale:
                    problems.append(
                        f"{a.name}: {side} copy cannot cover its {a.need[0]}x{a.need[1]} px "
                        f"footprint inside the declared {a.upscale}x upscale")
            cost[side][a.name] = decoded_mb(path)
            on_screen = (a.name in used_android) if side == "android" \
                else (a.ios_set in used_ios)
            if on_screen:
                live[side].add(a.name)

        if a.android_path.is_file() and a.ios_path.is_file() and \
                md5(a.android_path) != md5(a.ios_path):
            problems.append(f"{a.name}: the two platforms ship different pixels")
        # The shipped copy has to BE the authored art at the declared resolution.
        # Compared in pixels rather than by hash because PNG encoding is not
        # stable across Pillow releases, and a 1.0 mean-difference tolerance is
        # far tighter than anything a hand edit could introduce while still
        # allowing a re-encode.
        for side, path in (("android", a.android_path), ("ios", a.ios_path)):
            if not path.is_file():
                continue
            with Image.open(path) as im:
                got = np.asarray(im.convert(
                    "RGBA" if "A" in im.getbands() else "RGB")).astype(float)
            want = derived(a)
            if got.shape != want.shape:
                problems.append(f"{a.name}: {side} copy is {got.shape}, manifest "
                                f"derives {want.shape}")
                continue
            diff = float(np.abs(got - want).mean())
            if diff > 1.0:
                problems.append(
                    f"{a.name}: {side} copy is not the authored art at the "
                    f"declared resolution (mean |diff| {diff:.1f} of 255) — "
                    f"re-derive with --write")
        # Card artwork is a high-key render: nothing in it is dark. A candidate
        # lifted out of the design mock would carry the card's COPY with it — the
        # headline is near-black — so this asserts the asset is art and not a
        # screenshot of a card. It is the check that makes "derive the art from the
        # reference" a safe move if it is ever approved, and a guard against a bad
        # crop today.
        if "video" in a.name or "video" in a.ios_set.lower():
            for side, path in (("android", a.android_path), ("ios", a.ios_path)):
                if not path.is_file():
                    continue
                with Image.open(path) as im:
                    arr = np.asarray(im.convert("RGB")).astype(float)
                l = 0.2126 * arr[..., 0] + 0.7152 * arr[..., 1] + 0.0722 * arr[..., 2]
                dark = float((l < 90).mean() * 100.0)
                if dark > 0.25:
                    problems.append(
                        f"{a.name}: {side} art is {dark:.2f}% near-black — card "
                        f"artwork is high-key and carries no copy; this looks like "
                        f"a crop of a rendered card rather than the art")

        if a.name not in used_android and a.ios_set not in used_ios:
            problems.append(
                f"{a.name}: the manifest carries it but neither platform draws "
                f"it — it belongs in DEAD_ALLOW_LIST, not here")

    for side, folder, prefix in (("android", ANDROID_RES, ".png"),
                                 ("ios", IOS_ASSETS, ".imageset")):
        manifest_names = {a.name for a in manifest} if side == "android" \
            else {a.ios_set for a in manifest}
        for entry in sorted(folder.glob(f"*{prefix}")):
            stem = entry.name[: -len(prefix)]
            if stem in manifest_names or stem == "AppIcon":
                continue
            drawn = (stem in used_android) if side == "android" else (stem in used_ios)
            if drawn:
                problems.append(f"{stem}: {side} art drawn by the app but not in "
                                f"the manifest — account for its size and footprint")
            if not drawn and stem not in DEAD_ALLOW_LIST[side]:
                problems.append(
                    f"{stem}: {side} art referenced by no source and not in "
                    f"DEAD_ALLOW_LIST (unreferenced art is only allowed to stay "
                    f"dead by name)")

    # Parked art: out of the catalog, still in the repo. Both halves matter — a
    # name coming back into the catalog would ship 16.6 MB of decoded PNG again,
    # and a missing parked copy would mean the art was lost rather than parked.
    for name, why in PARKED_IOS.items():
        if (IOS_ASSETS / f"{name}.imageset").exists():
            problems.append(
                f"{name}: back in Assets.xcassets — it is parked because {why}; "
                f"actool compiles every imageset into Assets.car whether or not "
                f"code draws it")
        if not (PARKED_DIR / f"{name}.imageset").is_dir():
            problems.append(
                f"{name}: parked copy missing from {PARKED_DIR} — the art is "
                f"gone rather than parked")

    home = {"android": 0.0, "ios": 0.0}
    total = {"android": 0.0, "ios": 0.0}
    for a in manifest:
        for side in ("android", "ios"):
            if a.name in live[side]:
                total[side] += cost[side].get(a.name, 0.0)
                if a.screens == "Home":
                    home[side] += cost[side].get(a.name, 0.0)
    for side in ("android", "ios"):
        if home[side] > HOME_BUDGET_MB:
            problems.append(f"{side}: Home's referenced artwork decodes to "
                            f"{home[side]:.1f} MB, over the {HOME_BUDGET_MB:.1f} MB budget")
        if total[side] > ALL_BUDGET_MB:
            problems.append(f"{side}: referenced artwork decodes to {total[side]:.1f} MB, "
                            f"over the {ALL_BUDGET_MB:.1f} MB budget")

    print(f"{'asset':<21} {'target':>10} {'need px':>10} {'ups':>5}  screens")
    for a in manifest:
        print(f"{a.name:<21} {a.target[0]:>4}x{a.target[1]:<5} "
              f"{a.need[0]:>4}x{a.need[1]:<5} {a.upscale:>4.1f}x  {a.screens}")
    print(f"\nHome artwork, decoded: android {home['android']:.1f} MB / "
          f"ios {home['ios']:.1f} MB (budget {HOME_BUDGET_MB:.1f} MB)")
    print(f"All referenced artwork:  android {total['android']:.1f} MB / "
          f"ios {total['ios']:.1f} MB (budget {ALL_BUDGET_MB:.1f} MB)")
    dead = {s: 0.0 for s in ("android", "ios")}
    for p in ANDROID_RES.glob("*.png"):
        if p.stem in DEAD_ALLOW_LIST["android"]:
            dead["android"] += decoded_mb(p)
    for d in IOS_ASSETS.glob("*.imageset"):
        stem = d.name[: -len(".imageset")]
        if stem in DEAD_ALLOW_LIST["ios"]:
            dead["ios"] += sum(decoded_mb(x) for x in d.glob("*.png"))
    print(f"Unreferenced artwork:    android {dead['android']:.1f} MB / "
          f"ios {dead['ios']:.1f} MB  (Android is stripped from the APK by "
          f"resource shrinking; iOS ships it in Assets.car)")

    for p in problems:
        print(f"::error::{p}")
    if problems:
        print(f"\n{len(problems)} artwork problem(s). Re-derive with "
              f"`python3 scripts/prepare_home_art.py --write`.")
        return 1
    print("\nArtwork accounted for: every shipped copy matches its manifest target, "
          "both platforms ship identical bytes, and the referenced set is inside "
          "budget.")
    return 0


def main() -> int:
    mode = sys.argv[1] if len(sys.argv) > 1 else "--check"
    if mode == "--write":
        return write(MANIFEST)
    if mode == "--check":
        return check(MANIFEST)
    print("usage: prepare_home_art.py [--write|--check]")
    return 2


if __name__ == "__main__":
    sys.exit(main())
