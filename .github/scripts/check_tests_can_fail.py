#!/usr/bin/env python3
r"""Two checks on a test suite, both narrow, both verified by planting defects.

CHECK 1 -- can this test fail at all?          (a @Test with no assertion)
CHECK 2 -- is its assertion load-bearing?      (a condition that cannot be false)

BOTH IN ONE FILE because both answer "can this green mean anything", and two
scripts would let one be deleted and the other kept.

CHECK 2 was written second, after CHECK 1, and its first version reported 22
weak assertions in a suite that has none. Every one was `assertNotNull("message",
value)` -- JUnit's two-argument form, where the FIRST argument is the failure
message. A scanner whose entire output is noise is worse than no scanner, because
it trains you to skim past the line that matters. The fix was to exclude the
two-argument form wholesale, since its first argument is always a string and so
can never be the condition.

CHECK 1's own first version had the mirror-image bug: a negative lookbehind
`(?<![\w.])` in front of `assertReachable`, intended to skip nothing, which
excluded every real occurrence because the call site is always
`thing.assertReachable()`. The defect it was written for was invisible to it.
Found by planting that exact defect and watching the scan pass it.

----------------------------------------------------------------------------
CHECK 1 -- a @Test whose body contains no assertion that can fail cannot fail.

The operator's bar is "a test that does not execute is not a pass". This is the
same bar one level up: a test that EXECUTES and asserts nothing is a green that
proved nothing, and it is indistinguishable from a passing test in a report.

WHAT COUNTS AS AN ASSERTION
  assertXxx(...)                     JUnit
  throw / fail(...) / error(...)     explicit failure
  require(...) / check(...)          Kotlin's own

WHAT DOES NOT COUNT, and this list is the whole point
  println / Log.* / System.out       printing is not asserting
  assertReachable() ALONE            it returns String?; only a wrapper makes it
                                     an assertion. All four sites here wrap it in
                                     assertNotNull, which IS counted -- because a
                                     caller that drops the result gets a silent
                                     no-op and that must be detectable.
  a `catch` that only prints          swallowing an exception and carrying on is
                                     how a test reaches the end of its body alive

VERIFIED FIRST, on a planted defect, because a scan that returns zero is only
meaningful if it can return non-zero.
"""
import re
import sys

ASSERT = re.compile(r'\b(assert[A-Za-z]+|fail|throw|require|check)\s*[\(\s]')
PRINTONLY = re.compile(r'\b(println|print)\s*\(|Log\.[diewv]\s*\(|System\.out')


def strip_noise(t):
    t = re.sub(r'/\*.*?\*[/\s]', ' ', t, flags=re.S)
    return re.sub(r'//[^\n]*', ' ', t)


def tests(src):
    """Yield (name, body) for each @Test function, by brace balance."""
    src = strip_noise(src)
    lines = src.split('\n')
    for i, l in enumerate(lines):
        if '@Test' not in l:
            continue
        # the declaration is the next line that opens a brace
        j = i + 1
        while j < len(lines) and '{' not in lines[j]:
            if 'fun ' in lines[j]:
                break
            j += 1
        if j >= len(lines) or '{' not in lines[j]:
            continue
        name = re.search(r'fun\s+(\w+)', '\n'.join(lines[i:j + 1]))
        depth, body = 0, []
        for k in range(j, len(lines)):
            depth += lines[k].count('{') - lines[k].count('}')
            body.append(lines[k])
            if depth <= 0 and len(body) > 1:
                break
        yield (name.group(1) if name else '(unnamed)'), '\n'.join(body)


# ---------------------------------------------------------------------------
# CHECK 2. The three shapes that recur:
#   1. the single-argument condition is a literal
#   2. both sides of an equality are the same expression
#   3. the subject was constructed on a nearby line from a collection literal,
#      so `assertTrue(n > 0)` is comparing a literal's size with zero.
#
# Shape 3 is reported rather than decided, because knowing whether a value was
# "just constructed" needs judgement and a pattern that guesses it will be wrong.
# ---------------------------------------------------------------------------

LITERAL_COND = re.compile(
    r'\bassert(?:True|False|NotNull|Null)\s*\(\s*(?:true|false|null|\d+|"[^"]{0,30}")\s*\)')
SAME_BOTH_SIDES = re.compile(
    r'\bassert(?:Equals|NotEquals)\s*\(\s*([\w.]+)\s*,\s*\1\s*[,)]')
JUST_BUILT = re.compile(
    r'val\s+(\w+)\s*=\s*(?:listOf|mutableListOf|arrayOf|mapOf|setOf)\s*\(')


def weak_candidates(code):
    """Yield (line_no, shape, text) for every weak-assertion candidate."""
    lines = code.split('\n')
    for i, l in enumerate(lines):
        for pat, shape in ((LITERAL_COND, 'condition is a literal'),
                           (SAME_BOTH_SIDES, 'both sides are the same expression')):
            if pat.search(l):
                yield i + 1, shape, l
        m = JUST_BUILT.search(l)
        if not m:
            continue
        name = m.group(1)
        for j in range(i + 1, min(i + 4, len(lines))):
            for a in re.finditer(r'assert(?:True|False|Equals)\s*\(([^;]*)', lines[j]):
                if not re.search(r'\b%s\b' % re.escape(name), a.group(1)):
                    continue
                trivial = re.search(
                    r'\b%s\b\s*(?:\.size|\.isEmpty|\.isNotEmpty)' % re.escape(name),
                    a.group(1))
                yield j + 1, ('TRIVIAL: size/emptiness of a literal'
                              if trivial else 'review: asserts on a just-built value'), lines[j]


def main(paths):
    bad = 0
    total = 0
    for p in paths:
        src = open(p).read()
        for name, body in tests(src):
            total += 1
            asserts = ASSERT.findall(body)
            # assertReachable() alone is not an assertion: it RETURNS a value.
            bare = re.findall(r'assertReachable\s*\(\s*\)', body)
            wrapped = re.search(r'assert(?:NotNull|True|False)\([^)]*?assertReachable', body, re.S)
            if bare and not wrapped:
                print('  %s: %s calls assertReachable() and never asserts its result'
                      % (p.split('/')[-1], name))
                print('      it returns String? -- null means UNREACHABLE. Dropping it is a'
                      ' silent no-op.')
                bad += 1
            if not asserts:
                swallow = re.findall(
                    r'catch\s*\([^)]*\)\s*\{(.*?)\n\s*\}', body, re.S)
                only_prints = swallow and all(
                    not ASSERT.search(s) for s in swallow)
                print('  %s: %s has NO assertion that can fail%s'
                      % (p.split('/')[-1], name,
                         ' and every catch only prints' if only_prints else ''))
                bad += 1
    print('  %d @Test function(s) examined, %d cannot fail' % (total, bad))

    weak = 0
    for p in paths:
        code = strip_noise(open(p).read())
        for ln, shape, text in weak_candidates(code):
            print('  %s:%d  %s' % (p.split('/')[-1], ln, shape))
            print('      %s' % ' '.join(text.split())[:104])
            weak += 1
    print('  %d weak-assertion candidate(s)' % weak)

    # NON-VACUITY. Handed the wrong paths -- a find pattern that matches nothing,
    # a srcDir that moved -- this script examines nothing and exits 0. A green
    # that examined no tests is exactly the outcome it exists to prevent, and it
    # is why the CI step asserts a non-zero test count BEFORE calling this.
    # The step also fails if the @Test count ever drops to zero, which would mean
    # the extractor stopped matching rather than that the suite is empty.
    return 1 if (bad or weak) else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
