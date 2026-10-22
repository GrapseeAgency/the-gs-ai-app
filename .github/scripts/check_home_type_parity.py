#!/usr/bin/env python3
"""Home's type roles must be the same numbers on both platforms.

WHY A LINT AND NOT A FIX-UP PASS
--------------------------------
This file exists because the last two reviews each listed "N type roles disagree
between platforms" as a defect, someone adjusted a few call sites by hand, and
the next review found a different list. Hand-reconciling a pair of numbers in two
languages is not a fix: nothing stops the next edit moving one side. The only
thing that holds is a check that reads both sources and fails on drift.

HOW IT PAIRS THEM
-----------------
By the literal string each Text() renders. Every Home role that carries a fixed
label ("Nexly", "Mind Guide", "Generate") appears verbatim in both files, so the
pairing is a fact about the sources rather than a naming convention two authors
have to remember. Dynamic copy (the greeting, a conversation preview) is keyed by
its call-site identifier instead, and anything that cannot be resolved at all is
reported as UNPAIRED rather than silently skipped — a checker that quietly
shrinks its own coverage is the defect this repository keeps paying for.

The contract is `shared-contracts/home-type-roles.json`: element -> the one
size/weight/line-height both platforms must use. Run with --derive to print what
the two sources currently say, which is how the table was first written.
"""
import json
import re
import sys

KT = 'android/app/src/main/java/com/grapsee/gsai/ui/home/HomeDashboardScreen.kt'
TOKENS = 'android/app/src/main/java/com/grapsee/gsai/ui/theme/Tokens.kt'
DSWIFT = 'ios/App/Sources/Theme/DesignSystem.swift'
SWIFT = 'ios/App/Sources/Features/Home/HomeDashboardView.swift'
TYPEKT = 'android/app/src/main/java/com/grapsee/gsai/ui/theme/Type.kt'
CONTRACT = 'shared-contracts/home-type-roles.json'

WEIGHT_MAP = {  # Android FontWeight name -> the shared weight name
    'Bold': 'bold', 'SemiBold': 'semibold', 'Medium': 'medium',
    'Normal': 'regular', 'Regular': 'regular', 'Light': 'light',
}
SWIFT_WEIGHTS = {'ultraLight': 'ultralight', 'thin': 'thin', 'light': 'light',
                 'regular': 'regular', 'medium': 'medium', 'semibold': 'semibold',
                 'bold': 'bold', 'heavy': 'heavy', 'black': 'black'}


def android_roles():
    """MaterialTheme.typography roles resolved from Type.kt."""
    src = open(TYPEKT).read()
    out = {}
    for m in re.finditer(r'(\w+) = TextStyle\(([\s\S]{0,240}?)\n    \)[,)]?', src):
        body = m.group(2)
        size = re.search(r'fontSize = (?:(-?[\d.]+)|\((-?[\d.]+)\))\.sp', body)
        line = re.search(r'lineHeight = (?:(-?[\d.]+)|\((-?[\d.]+)\))\.sp', body)
        weight = re.search(r'fontWeight = FontWeight\.(\w+)', body)
        out[m.group(1)] = {
            'size': float(size.group(1) or size.group(2)) if size else None,
            'line': float(line.group(1) or line.group(2)) if line else None,
            'weight': WEIGHT_MAP.get(weight.group(1)) if weight else None,
        }
    return out


def num(s):
    return float(s) if s not in (None, '') else None


def call_at(src, open_idx):
    """The balanced text of the call whose '(' sits at open_idx.

    A regex cannot do this. The first version of this lint matched a Text() block
    with a lazy "up to 200 chars then a close paren" pattern and stopped at the
    ')' of `if (compact) 10.sp else 12.5.sp` — so it silently read the *role's*
    size instead of the call-site
    override and reported Android's "Today's reflection" as 13 when the source
    says 12.5. A checker that mis-parses in the direction of agreement is worse
    than no checker, because it reports a pass.
    """
    depth = 0
    i = open_idx
    in_str = False
    while i < len(src):
        c = src[i]
        if in_str:
            if c == '\\':
                i += 2
                continue
            if c == '"':
                in_str = False
        elif c == '"':
            in_str = True
        elif c == '(':
            depth += 1
        elif c == ')':
            depth -= 1
            if depth == 0:
                return src[open_idx + 1:i]
        i += 1
    return ''


def ternary_size(body, pattern):
    """`fontSize = if (compact) 10.sp else 12.5.sp` -> the non-compact value.

    The contract pins the default font scale, which is what the render gate
    measures and what a phone at .large sizes renders; the compact branch is a
    narrow-screen fallback and is recorded alongside it, not gated.
    """
    m = re.search(pattern + r'\s*=\s*(?:if \(compact\) [\d.]+\.sp else |[\w.]+\? ?[\d.]+ : ?)?(-?[\d.]+)\.sp', body)
    return float(m.group(1)) if m else None


def scan_android(path, roles):
    """(literal, size, weight, line) for every Text() on the page."""
    src = open(path).read()
    found = []
    for m in re.finditer(r'\bText\(', src):
        body = call_at(src, m.end() - 1)
        if 'MaterialTheme.typography.' not in body:
            continue
        # `text = "..."` first: an Android Text() block carries its comment inside
        # the call, and the comment on the Mind Guide subtitle quotes the word
        # "companion." — matching the first quoted string in the body keyed that
        # row as `personal` and quietly dropped the real one.
        lit = (re.search(r'text\s*=\s*"((?:[^"\\]|\\.)*)"', body)
               or re.search(r'^\s*"((?:[^"\\]|\\.)*)"', body))
        role = re.search(r'style = MaterialTheme\.typography\.(\w+)', body)
        if not role:
            continue
        base = dict(roles.get(role.group(1), {'size': None, 'line': None, 'weight': None}))
        copy = re.search(r'typography\.\w+\.copy\(', body)
        args = call_at(body, copy.end() - 1) if copy else ''
        size = ternary_size(args, 'fontSize')
        line = ternary_size(args, 'lineHeight')
        fw = re.search(r'fontWeight = FontWeight\.(\w+)', args)
        if size is not None:
            base['size'] = size
        if line is not None:
            base['line'] = line
        if fw:
            base['weight'] = WEIGHT_MAP.get(fw.group(1), fw.group(1).lower())
        found.append({'key': lit.group(1) if lit else '@dynamic',
                      'role': role.group(1), **base})
    return found


def scan_swift(path):
    src = open(path).read()
    found = []
    helpers = {}
    for m in re.finditer(r'static func (\w+)\(\) -> Font \{ scaledFont\(([\d.]+), '
                         r'\.(\w+), .*?relativeTo: \.(\w+)\) \}', open(
        'ios/App/Sources/Theme/DesignSystem.swift').read()):
        helpers[m.group(1)] = {'size': num(m.group(2)),
                               'weight': SWIFT_WEIGHTS.get(m.group(3), m.group(3))}
    for m in re.finditer(r'\bText\(', src):
        body = call_at(src, m.end() - 1)
        # .font() is a modifier AFTER the Text(...) call, so the search continues
        # past the close paren. The first version looked inside the call and found
        # nothing, which made the whole iOS side of this lint silently empty — a
        # checker that reads zero rows and reports "0 problems" is the exact
        # failure this repository has now been caught shipping three times.
        # Stop at the next Text(): a fixed-size window either misses a .font()
        # parked behind a long comment (260 did) or reaches past this element into
        # the next one's font (900 did, and keyed the "Video" chip as the 16pt
        # headline below it). The modifier chain belongs to the nearest Text.
        nxt = src.find('Text(', m.end())
        tail = src[m.end():(nxt if nxt != -1 else len(src))]
        lit = re.search(r'^\s*"((?:[^"\\]|\\.)*)"', body)
        f = re.search(r'\.font\(\s*Aero\.responsive\(\s*(?:compact \? [\d.]+ : )?([\d.]+)\s*,\s*\.(\w+)', tail)
        h = re.search(r'\.font\(\s*Aero\.(\w+)\(\)', tail)
        if f:
            found.append({'key': lit.group(1) if lit else '@dynamic',
                          'role': 'Aero.responsive', 'size': num(f.group(1)),
                          'weight': SWIFT_WEIGHTS.get(f.group(2), f.group(2)), 'line': None})
        elif h and h.group(1) in helpers:
            found.append({'key': lit.group(1) if lit else '@dynamic',
                          'role': f'Aero.{h.group(1)}()',
                          'size': helpers[h.group(1)]['size'],
                          'weight': helpers[h.group(1)]['weight'], 'line': None})
    return found


def index(rows):
    """Literal-keyed rows only. '@dynamic' is what a Text() whose copy is a
    variable renders as, and collapsing twenty of them onto one key would let the
    contract appear to check something it never looked at."""
    out = {}
    for r in rows:
        if r['key'] == '@dynamic':
            continue
        out.setdefault(r['key'], r)
    return out


def corner_problems():
    """Every Home glass surface must take a NAMED radius, on both platforms.

    The iOS test asserted `Aero.Radius.card == 20` against a token that is 20:
    it could not fail, while Home shipped corners that token does not describe.
    A name can be checked at the call site; a duplicated literal cannot.
    """
    problems = []
    kt = open(KT).read()
    for i, line in enumerate(kt.split('\n'), 1):
        if '.gsGlass(' in line or '.gsGlassShadow(' in line:
            m = re.search(r'RoundedCornerShape\(([\d.]+)\.dp\)', line)
            if m:
                problems.append(f'{KT}:{i} glass corner is the literal '
                                f'{m.group(1)}.dp, not a GsRadius token')
    sw = open(SWIFT).read()
    for i, line in enumerate(sw.split('\n'), 1):
        if '.gsGlass(' in line or '.gsCapsuleGlass(' in line:
            m = re.search(r'corner:\s*([\d.]+)\s*[,)]', line)
            if m:
                problems.append(f'{SWIFT}:{i} glass corner is the literal '
                                f'{m.group(1)}, not an Aero.Radius token')
    # the two named tokens must be the same number, or "named" only means
    # "drifted in two places at once"
    a = re.search(r'val homeCard: Dp = ([\d.]+)\.dp', open(TOKENS).read())
    b = re.search(r'static let homeCard: CGFloat = ([\d.]+)', open(DSWIFT).read())
    if not a or not b:
        problems.append('the Home card radius token is missing on one platform '
                        '(GsRadius.homeCard / Aero.Radius.homeCard)')
    elif a.group(1) != b.group(1):
        problems.append(f'Home card radius tokens disagree: Android '
                        f'{a.group(1)}dp vs iOS {b.group(1)}pt')
    return problems


def main() -> int:
    derive = '--derive' in sys.argv
    roles = android_roles()
    a = index(scan_android(KT, roles))
    i = index(scan_swift(SWIFT))
    keys = sorted(set(a) | set(i))

    if derive:
        print(f'{"element":34s} {"android size/weight":24s} {"ios size/weight":24s}')
        for k in keys:
            ra, ri = a.get(k), i.get(k)
            fa = f'{ra["size"]}/{ra["weight"]}' if ra else 'UNPAIRED'
            fi = f'{ri["size"]}/{ri["weight"]}' if ri else 'UNPAIRED'
            print(f'{k[:33]:34s} {fa:24s} {fi:24s}')
        return 0

    try:
        contract = json.load(open(CONTRACT))
    except OSError:
        print(f'::error::{CONTRACT} is missing. The lint has no contract to check '
              f'against; regenerate it with --derive and commit it, do not delete it.')
        return 2

    problems = []
    checked = 0
    contract = {k: v for k, v in contract.items() if not k.startswith('_')}
    for key, want in contract.items():
        ra, ri = a.get(key), i.get(key)
        if ra is None:
            problems.append(f'{key}: no Android Text() renders this literal any more')
            continue
        if ri is None:
            problems.append(f'{key}: no iOS Text() renders this literal any more')
            continue
        checked += 1
        for field in ('size', 'weight'):
            if want.get(field) is None:
                continue
            if ra.get(field) != want[field]:
                problems.append(f'{key}: Android {field}={ra.get(field)} but the '
                                f'contract says {want[field]}')
            if ri.get(field) != want[field]:
                problems.append(f'{key}: iOS {field}={ri.get(field)} but the '
                                f'contract says {want[field]}')
    if problems:
        print(f'::error::Home type roles disagree with the contract '
              f'({len(problems)} problem(s) over {checked} role(s)):')
        for p in problems:
            print(f'  - {p}')
        return 1
    # The gap is printed, not hidden. Android's greeting is `text = greeting + ","`
    # and iOS's is `\(greetingWord)`, so neither has a literal to pair on and the
    # contract cannot reach them; a checker whose coverage silently shrinks is the
    # defect this repository keeps being caught shipping.
    only_a = sorted(set(a) - set(i))
    only_i = sorted(set(i) - set(a))
    problems += corner_problems()

    if problems:
        print(f'::error::Home design contract violated ({len(problems)} problem(s)):')
        for pr in problems:
            print(f'  - {pr}')
        return 1
    print(f'ok: {checked} Home type role(s) match the contract on both platforms')
    if only_a or only_i:
        print(f'  note: {len(only_a)} literal(s) only Android renders verbatim '
              f'{only_a[:3]}{"..." if len(only_a) > 3 else ""}, '
              f'{len(only_i)} only iOS {only_i[:3]}{"..." if len(only_i) > 3 else ""} '
              f'— unpairable, so ungated')
    return 0


if __name__ == '__main__':
    sys.exit(main())
