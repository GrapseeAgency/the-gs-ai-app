#!/usr/bin/env python3
"""The icon catalogue, checked rather than assumed.

The app ships one Lucide family: 1,611 geometries and 5,466 public names, on both
platforms (Android builds Compose vectors, iOS builds stroke paths). Nothing in
this repo verified any of it. A generated file with five thousand entries is
exactly the kind of thing that rots silently: an alias can point at a geometry
that was renamed, the two generators can drift apart by a letter, and a row can
ask for an icon that does not exist — which only shows up as a blank glyph on one
platform at runtime.

What this checks, and why each one is a real failure mode:

  1. COUNTS MATCH THE CLAIM. The generated headers state "1611 geometries and
     5466 public names". If the file and its own documentation disagree, one of
     them is lying about what ships.
  2. NO DUPLICATE NAMES. Two properties with the same name do not compile in
     Kotlin; in Swift the second silently wins. Catching it here names the pair.
  3. EVERY ALIAS RESOLVES to a real geometry. An alias to a vanished name is a
     crash on Kotlin and a compile error on Swift — better to know now.
  4. THE TWO PLATFORMS HAVE THE SAME NAMES. PascalCase on Android, camelCase on
     iOS; anything else is a platform-specific icon, i.e. two design systems.
  5. THE TOPIC TABLE IS IDENTICAL on both sides — same topics, same order, same
     icon names, same keywords in the same order. The drawer's row icon is chosen
     from this table, so a drift means the same chat wears a different icon on the
     two phones.
  6. EVERY TOPIC NAME HAS A RENDER BRANCH. A name in the table with no branch
     falls through to the default glyph and the mapping is decorative again.
  7. EVERY ICON THE APP REFERENCES EXISTS. `GsIcons.Foo` where Foo is gone is a
     compile error on Android and a blank view on iOS.

Usage: check_icon_catalogue.py [--root .] [--self-test]
"""
import argparse
import re
import sys
from pathlib import Path

KOTLIN_ICONS = "android/app/src/main/java/com/grapsee/gsai/ui/icons/GsIcons.kt"
SWIFT_ICONS = "ios/App/Sources/Theme/GsIcons.swift"
KOTLIN_TOPICS = "android/app/src/main/java/com/grapsee/gsai/ui/navigation/drawer/TopicIcons.kt"
SWIFT_TOPICS = "ios/App/Sources/Components/Drawer/DrawerTopicIcons.swift"

K_GEOMETRY = re.compile(r"^\s*public val (\w+): ImageVector by lazy \{", re.M)
K_ALIAS = re.compile(r"^\s*public val (\w+): ImageVector get\(\) = (\w+)\s*$", re.M)
# Swift keywords are declared with backticks (`import`, `repeat`, `subscript`).
# The first version of this parser did not allow them, so it reported the three
# geometries and six aliases as "missing on iOS" — a parser bug wearing the
# costume of a catalogue defect. The catalogue was right.
S_GEOMETRY = re.compile(r"^\s*public static let `?(\w+)`?: GsIconPaths = \.init\(", re.M)
S_ALIAS = re.compile(r"^\s*public static var `?(\w+)`?: GsIconPaths \{ `?(\w+)`? \}", re.M)
CLAIM = re.compile(r"(\d+)\s+geometries and\s+(\d+)\s+public names")


def camel(pascal: str) -> str:
    return pascal[:1].lower() + pascal[1:]


def kotlin_topics(text: str):
    """[(topic, icon, [keywords])] in declaration order."""
    start = text.index("enum class DrawerTopic")
    body = text[start:]
    out = []
    # `Chat("MessageCircle");` has no keyword list, so the comma is optional.
    for match in re.finditer(r"^    (\w+)\(\s*\n?\s*\"([^\"]+)\"\s*,?(.*?)\)[,;]\s*$", body, re.S | re.M):
        topic, icon, rest = match.group(1), match.group(2), match.group(3)
        out.append((topic, icon, re.findall(r"\"([^\"]+)\"", rest)))
    return out


def swift_topics(text: str):
    """[(topic, icon, [keywords])] in the order the enum declares its cases."""
    order = []
    for block in re.finditer(r"^    case ([\w, ]+)$", text, re.M):
        order += [name.strip() for name in block.group(1).split(",") if name.strip()]
    icons = dict(re.findall(r"case \.(\w+): return \"([^\"]+)\"", text))
    keywords = {}
    for match in re.finditer(r"case \.(\w+):\s*\n\s*return \[(.*?)\]", text, re.S):
        keywords[match.group(1)] = re.findall(r"\"([^\"]+)\"", match.group(2))
    out = []
    for topic in order:
        out.append((topic, icons.get(topic, ""), keywords.get(topic, [])))
    return out


def audit(root: Path, overrides=None):
    problems = []
    files = {}
    for rel in (KOTLIN_ICONS, SWIFT_ICONS, KOTLIN_TOPICS, SWIFT_TOPICS):
        p = (overrides or {}).get(rel) or (root / rel)
        if not p.is_file():
            problems.append(f"{rel}: missing — the catalogue cannot be checked")
            return problems, {}
        files[rel] = p.read_text()

    ktext, stext = files[KOTLIN_ICONS], files[SWIFT_ICONS]

    # 1. the files' own claim
    for rel, text in ((KOTLIN_ICONS, ktext), (SWIFT_ICONS, stext)):
        claim = CLAIM.search(text)
        kg, kn = len(K_GEOMETRY.findall(text)), len(K_GEOMETRY.findall(text)) + len(K_ALIAS.findall(text))
        if rel == SWIFT_ICONS:
            kg = len(S_GEOMETRY.findall(text))
            kn = kg + len(S_ALIAS.findall(text))
        if not claim:
            problems.append(f"{rel}: no FULL SET claim to check against")
        else:
            cg, cn = int(claim.group(1)), int(claim.group(2))
            if (cg, cn) != (kg, kn):
                problems.append(f"{rel}: header claims {cg} geometries / {cn} names, file has {kg} / {kn}")

    # 2/3. duplicates and alias targets, per platform
    for rel, geoms, aliases in (
        (KOTLIN_ICONS, K_GEOMETRY.findall(ktext), K_ALIAS.findall(ktext)),
        (SWIFT_ICONS, S_GEOMETRY.findall(stext), S_ALIAS.findall(stext)),
    ):
        names = list(geoms) + [a[0] for a in aliases]
        seen, dupes = set(), []
        for name in names:
            if name in seen:
                dupes.append(name)
            seen.add(name)
        if dupes:
            problems.append(f"{rel}: duplicate name(s) {', '.join(sorted(set(dupes))[:5])}")
        geometry_set = set(geoms)
        dangling = sorted({target for _alias, target in aliases if target not in geometry_set})
        if dangling:
            problems.append(f"{rel}: alias(es) point at a missing geometry: {', '.join(dangling[:5])}")

    # 4. the two platforms must carry the same names
    k_names = {camel(n) for n in K_GEOMETRY.findall(ktext) + [a[0] for a in K_ALIAS.findall(ktext)]}
    s_names = set(S_GEOMETRY.findall(stext) + [a[0] for a in S_ALIAS.findall(stext)])
    only_android = sorted(k_names - s_names)
    only_ios = sorted(s_names - k_names)
    if only_android:
        problems.append(f"{len(only_android)} name(s) exist only on Android, e.g. {', '.join(only_android[:5])}")
    if only_ios:
        problems.append(f"{len(only_ios)} name(s) exist only on iOS, e.g. {', '.join(only_ios[:5])}")

    # 5. the topic table, both sides
    kt_topics = kotlin_topics(files[KOTLIN_TOPICS])
    sw_topics = swift_topics(files[SWIFT_TOPICS])
    if len(kt_topics) != len(sw_topics):
        problems.append(f"topic tables differ in size: {len(kt_topics)} on Android, {len(sw_topics)} on iOS")
    else:
        for (kname, kicon, kkeys), (sname, sicon, skeys) in zip(kt_topics, sw_topics):
            if kname.lower() != sname.lower():
                problems.append(f"topic order differs at {kname} vs {sname}")
            if kicon != sicon:
                problems.append(f"topic {kname}: icon {kicon} on Android, {sicon} on iOS")
            if kkeys != skeys:
                diff = sorted(set(kkeys) ^ set(skeys))
                problems.append(f"topic {kname}: keywords differ ({', '.join(diff[:5])})")
        if [t[0].lower() for t in kt_topics] != [t[0].lower() for t in sw_topics]:
            problems.append("topic precedence order is not identical")

    # 6. every topic icon is drawable on both platforms
    k_render = set(re.findall(r"^\s*\"(\w+)\" -> GsIcons\.\w+", files[KOTLIN_TOPICS], re.M))
    s_render = set(re.findall(r"case \"(\w+)\": return GsIcons\.`?\w+`?", files[SWIFT_TOPICS]))
    for topic, icon, _keys in kt_topics:
        if icon not in k_render:
            problems.append(f"topic {topic}: '{icon}' has no Android render branch")
        if icon not in s_render:
            problems.append(f"topic {topic}: '{icon}' has no iOS render branch")
        if camel(icon) not in s_names:
            problems.append(f"topic {topic}: '{icon}' is not in the iOS catalogue")
        if icon not in {n for n in K_GEOMETRY.findall(ktext)} | {a[0] for a in K_ALIAS.findall(ktext)}:
            problems.append(f"topic {topic}: '{icon}' is not in the Android catalogue")

    # 7. app references resolve (Android; iOS names are reached through the table)
    referenced = set()
    for path in (root / "android/app/src/main").rglob("*.kt"):
        if path.name == "GsIcons.kt":
            continue
        referenced |= set(re.findall(r"GsIcons\.(\w+)", path.read_text()))
    missing = sorted(n for n in referenced if n not in {x for x in K_GEOMETRY.findall(ktext)} | {a[0] for a in K_ALIAS.findall(ktext)})
    if missing:
        problems.append(f"referenced but missing from the catalogue: {', '.join(missing[:8])}")

    summary = {
        "android": (len(K_GEOMETRY.findall(ktext)), len(K_GEOMETRY.findall(ktext)) + len(K_ALIAS.findall(ktext))),
        "ios": (len(S_GEOMETRY.findall(stext)), len(S_GEOMETRY.findall(stext)) + len(S_ALIAS.findall(stext))),
        "topics": len(kt_topics),
        "referenced": len(referenced),
    }
    return problems, summary


SELF_TEST_MUTATIONS = {
    "an alias to a vanished geometry": (
        KOTLIN_ICONS,
        lambda t: t.replace("public val Sparkles: ImageVector by lazy {",
                            "public val LucideSparkles: ImageVector get() = SparklesGone\n    public val Sparkles: ImageVector by lazy {", 1),
    ),
    "a name that exists on only one platform": (
        SWIFT_ICONS,
        lambda t: t.replace("public static let sparkles: GsIconPaths", "public static let sparklesRenamed: GsIconPaths", 1),
    ),
    "the two topic tables disagree": (
        SWIFT_TOPICS,
        lambda t: t.replace('return "Atom"', 'return "Lightbulb"', 1),
    ),
    "a topic with no render branch": (
        KOTLIN_TOPICS,
        lambda t: t.replace('"Atom" -> GsIcons.Atom\n', '', 1),
    ),
}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=".")
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()
    root = Path(args.root)

    problems, summary = audit(root)
    if problems:
        for problem in problems:
            print(f"FAIL {problem}")
        return 1
    print(f"ok: {summary['android'][0]} geometries and {summary['android'][1]} names on both platforms, "
          f"{summary['topics']} topics mirrored, {summary['referenced']} app references resolve")

    if args.self_test:
        dead = []
        for label, (rel, mutate) in SELF_TEST_MUTATIONS.items():
            doc = (root / rel).read_text()
            doctored = mutate(doc)
            if doctored == doc:
                dead.append(f"{label}: the mutation did not apply")
                continue
            found, _ = audit(root, overrides={rel: _write_temp(rel, doctored)})
            if not found:
                dead.append(f"{label}: the check did not fire")
        if dead:
            for item in dead:
                print(f"FAIL {item}")
            return 1
        print(f"self-test: all {len(SELF_TEST_MUTATIONS)} doctored catalogues are caught")

    return 0


_TMP = Path("/tmp/gs-icon-catalogue-selftest")


def _write_temp(rel: str, text: str) -> Path:
    target = _TMP / Path(rel).name
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text)
    return target


if __name__ == "__main__":
    sys.exit(main())
