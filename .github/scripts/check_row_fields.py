#!/usr/bin/env python3
"""Every `<var>.<field>` read on a row object must name a field that row DECLARES.

The bug this exists for, from android-app run 36956809627:

    e: ...DeviceVerificationTest.kt:2060:33 None of the following candidates is
        applicable: fun <T : Comparable<T>> Iterable<T>.min(): T
    e: ...DeviceVerificationTest.kt:2089:39 Unresolved reference 'ttft'

The first was a `List<Pair<Long, Int>>` measured with `.min()`, and Pair is not
Comparable. The second was `r.ttft` on a class whose field is `ttftMs`.

WHAT MAKES THIS WORTH A SCRIPT is that a file-wide check CANNOT catch the second
one. `ttft` is declared -- by FaRow and by TbRow, in the same file. So
"is this field name declared anywhere?" is satisfied and the bug survives. The
name has to be bound to the row class in the scope where it is read, which is why
this walks function by function and finds the locally declared data class.

WATCH IT FAIL FIRST, which is how it was developed: the first version of this
exited 0 on the buggy copy while printing "UNDECLARED: ttft" underneath. A check
whose verdict disagrees with its own output is worse than no check, and that is
worth saying plainly because it is what the operator brief means by a false pass.
"""
import re
import sys



def fields_of(src):
    """Every `data class X(...)` in the file, with its field names.

    The terminator is the closing paren followed by a line that is only
    whitespace and `)`. Requiring a FIXED indent of eight spaces was the first
    version and it silently missed every class declared at the top level of a
    file, so a function reading one of those fields raised KeyError on the real
    tree. The indent is a formatting detail; the terminator is what identifies
    the end of the parameter list.
    """
    out = {}
    for m in re.finditer(r'data class (\w+)\((.*?)^\s*\)', src, re.S | re.M):
        out[m.group(1)] = set(re.findall(r'val (\w+):', m.group(2)))
    return out


def functions(src):
    lines = src.split('\n')
    starts = [i for i, l in enumerate(lines) if re.match(r'    (@Test|fun |private fun )', l)]
    starts.append(len(lines))
    for a, b in zip(starts, starts[1:]):
        yield lines[a].strip(), '\n'.join(lines[a:b])


def main(paths):
    rc = 0
    checked_total = 0
    files_with_rows = 0
    gaps = []

    for path in paths:
        try:
            src = open(path).read()
        except OSError as e:
            print('  cannot read %s: %s' % (path, e))
            return 2
        all_fields = fields_of(src)
        if not all_fields:
            # NOT AN ERROR. Most Kotlin files declare no local data class, and
            # insisting on one would make this script fail on 96 of the 100 files
            # CI hands it. The first version did exactly that and died on
            # Elf64.kt, which is a perfectly good file with no data class in it.
            #
            # Skipping is only safe because of the non-vacuity check at the end:
            # if NO file in the batch yields a checkable function, that is a
            # failure, because then the step passed by examining nothing.
            continue
        files_with_rows += 1

        for name, body in functions(src):
            local = (re.findall(r'data class (\w+)\(', body)
                     or re.findall(r'mutableListOf<(\w+)>', body))
            if len(set(local)) != 1:
                continue
            cls = local[0]
            if cls not in all_fields:
                # REPORTED, not skipped quietly. This function reads fields of a
                # class whose declaration the parser could not bind, so nothing
                # about it is verified -- and a check with an invisible hole in
                # it is the thing this whole script exists to prevent.
                gaps.append('%s: %s (%s)' % (path, name[:44], cls))
                continue
            declared = all_fields[cls]
            loopvars = set(re.findall(r'for \((\w+) in rows\)', body))
            acc = set()
            for v in loopvars:
                acc |= set(re.findall(r'\b%s\.(\w+)' % re.escape(v), body))
            for m in re.finditer(r'rows\.\w+\s*\{\s*(?:it|\w+)\.(\w+)', body):
                acc.add(m.group(1))
            checked_total += 1
            undeclared = {a for a in acc if a not in declared}
            if undeclared:
                print('  %s' % path)
                print('    %-56s %s' % (name[:56], cls))
                print('        reads %s' % ', '.join(sorted(undeclared)))
                print('        %s declares %s' % (cls, ', '.join(sorted(declared))))
                rc = 1

    print('  %d file(s) given, %d declare a local data class, '
          '%d row-typed function(s) checked' % (len(paths), files_with_rows, checked_total))
    if gaps:
        # A gap is a hole in the check, so it is printed as one. It does not fail
        # the step -- these functions are simply unverified, and refusing to run
        # at all over a formatting difference would get the step deleted.
        print('  %d function(s) NOT verified, because their row class could not be'
              ' bound:' % len(gaps))
        for g in gaps:
            print('      %s' % g)

    # NON-VACUITY. An extractor that silently matches nothing produces a clean
    # run and a green step, which is the failure mode this repository keeps
    # paying for. Checked here rather than trusted.
    if checked_total == 0:
        print('  FAIL: no row-typed function was checked. Either the row classes '
              'were renamed or the extractor no longer matches them, and a step '
              'that examines nothing must not be green.')
        return 2
    return rc


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
