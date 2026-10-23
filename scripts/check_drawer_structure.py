#!/usr/bin/env python3
"""The drawer's structure, asserted where pixels cannot see it.

Three classes of defect are invisible in a capture and expensive in production:

  1. an EAGER list — the drawer looked identical with 4 and 500 chats, but every
     row was composed on every frame;
  2. a per-frame SORT — iOS called `store.activeConversations` from `body`, which
     is O(n log n) plus two allocations, on every drag sample at 120 Hz;
  3. an OFFSCREEN PASS per frame — a `.shadow(radius: 24)` on a full-height
     panel, which is a gaussian blur of ~2.6 Mpx every frame while the panel
     slides.

Each rule below names the mistake it forbids. `--self-test` writes a doctored
copy of each file into a temp directory and proves the rule fires, so this is not
a lint that can only ever pass.

Usage: check_drawer_structure.py [--root .] [--self-test]
"""
import argparse
import re
import sys
from pathlib import Path

ANDROID_DRAWER = "android/app/src/main/java/com/grapsee/gsai/ui/navigation/GsDrawer.kt"
ANDROID_ROWS = "android/app/src/main/java/com/grapsee/gsai/ui/navigation/drawer/DrawerRows.kt"
ANDROID_NAV = "android/app/src/main/java/com/grapsee/gsai/ui/navigation/GsNavHost.kt"
IOS_DRAWER = "ios/App/Sources/Components/AeroDrawer.swift"


def rules():
    """(file, description, predicate, a sample the predicate must reject).

    The sample is the self-test: a rule that cannot reject its own counter-example
    is not a check, and this file cannot know that any other way.
    """
    return [
        # (file, description, predicate(text) -> bool "violates", counter-example)
        (ANDROID_DRAWER,
         "the drawer's list is eager (add LazyColumn)",
         lambda t: "LazyColumn" not in t,
         "Column { }"),
        (ANDROID_DRAWER,
         "the panel scrolls with verticalScroll, which composes every row",
         # One occurrence is the account sheet's own scroll; the panel must not
         # be one, and an eager panel needs exactly this call.
         lambda t: len([ln for ln in t.splitlines()
                        if "verticalScroll" in ln and "import" not in ln]) > 1,
         "A(verticalScroll)\nB(verticalScroll)"),
        (ANDROID_DRAWER,
         "the panel still paints an opaque verticalGradient",
         lambda t: "Brush.verticalGradient" in t and "GlassFrostedPanel" not in t,
         "background(Brush.verticalGradient(x))"),
        (ANDROID_DRAWER,
         "the panel blurs at draw time (Modifier.blur in the drawer)",
         lambda t: re.search(r"Modifier[^\n]*\.blur\(", t) is not None,
         "Modifier.blur(24.dp)"),
        (ANDROID_NAV,
         "the drawer's scrim is not the measured token (scrimColor missing)",
         lambda t: "scrimColor" not in t,
         "ModalNavigationDrawer(drawerState = s)"),
        (IOS_DRAWER,
         "the iOS list is eager (add LazyVStack)",
         lambda t: "LazyVStack" not in t,
         "VStack { }"),
        (IOS_DRAWER,
         "the panel carries a per-frame shadow (remove .shadow)",
         lambda t: re.search(r"\.shadow\(", t) is not None,
         ".shadow(radius: 24)"),
        (IOS_DRAWER,
         "the store is read from body: activeConversations appears more than twice",
         # Comments are not calls: AeroDrawer's docstring names the old pattern
         # on purpose, and counting it would make the rule impossible to satisfy.
         lambda t: len([ln for ln in t.splitlines()
                        if "activeConversations" in ln
                        and not ln.strip().startswith("//")]) > 2,
         "let a = store.activeConversations\nlet b = store.activeConversations\n"
         "let c = store.activeConversations"),
        (IOS_DRAWER,
         "the rows are not resolved once per presentation (refreshRecents missing)",
         lambda t: "refreshRecents()" not in t,
         "var body: some View { EmptyView() }"),
        (ANDROID_DRAWER,
         "the drawer animates forever, so it repaints the page behind it",
         lambda t: "rememberInfiniteTransition" in t or "repeatForever" in t,
         "rememberInfiniteTransition()"),
        (IOS_DRAWER,
         "the drawer animates forever, so it repaints the page behind it",
         lambda t: ".repeatForever" in t,
         ".animation(.easeIn.repeatForever())"),
    ]


def check(root, overrides=None):
    problems = []
    for path, description, violates, _sample in rules():
        p = (overrides or {}).get(path) or (root / path)
        if not p.is_file():
            problems.append(f"{path}: missing — the structure cannot be checked")
            continue
        text = p.read_text()
        if violates(text):
            problems.append(f"{path}: {description}")
    return problems


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=".")
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()
    root = Path(args.root)

    problems = check(root)
    if args.self_test:
        # Each rule must reject its own counter-example. Testing them against one
        # shared doctored file was the first attempt and it proved nothing: a
        # single broken file cannot violate eleven independent rules at once.
        dead = [(desc, sample) for _f, desc, violates, sample in rules() if not violates(sample)]
        if dead:
            print(f"::error::{len(dead)} rule(s) cannot fire on their counter-example:")
            for desc, sample in dead:
                print(f"  - {desc}  (sample: {sample[:40]})")
            return 1
        print(f"self-test: all {len(rules())} rule(s) fire on their own counter-example")

    if problems:
        print(f"::error::the drawer's structure drifted ({len(problems)} problem(s)):")
        for p in problems:
            print(f"  - {p}")
        return 1
    print(f"OK: {len(rules())} structural rule(s) hold")
    return 0


if __name__ == "__main__":
    sys.exit(main())
