#!/usr/bin/env python3
"""Check that braces balance in a Kotlin/Java/C++/C file.

WHY THIS EXISTS, and why it is more than a novelty.

Three separate attempts to check one Kotlin file's brace balance by hand each
gave a DIFFERENT WRONG ANSWER before the scanner handled every construct Kotlin
has:

  1. Naive `s.count('{')` vs `s.count('}')`. Reported IMBALANCED on a file the
     Android build had compiled, because a JSON spec inside a string literal
     contributes one brace of each and the totals land differently. Fixing it
     would have meant adding a brace, which is the worst possible response to a
     broken check.

  2. A per-LINE scanner. Still wrong, because a Kotlin "..." literal may span
     LINES -- it is not restricted to one -- so a per-line scanner loses the
     in-string state at the newline and reads the rest of the file as code.

  3. A scanner with no `/* ... */` or `'c'` handling. Still wrong.

  4. A scanner that lexed comments, both string forms and char literals, and was
     STILL wrong on a file the build compiles:

         android/app/src/main/java/com/grapsee/gsai/data/remote/AttachmentUploader.kt
         FAIL: depth -1 at end of file, first went NEGATIVE at line 91

     because of a STRING TEMPLATE containing a string literal:

         "filename=\\"${displayName.replace(\\"\\", "")}\\""

     A "${" inside a string is CODE, and the braces and quotes inside it are real.
     Any scanner that treats the whole literal as opaque stops counting at the
     first inner quote and then mis-lexes everything after it.

WHAT IT IS GOOD FOR. It is a cheap pre-commit check on files that are about to be
compiled anyway. It has never caught anything the compiler would not have caught
eventually -- and that is the honest description of it: it does not replace the
compiler, it replaces reading a 1500-line file and counting braces by hand. A
hand-count on a file with JSON in it is a guess wearing the costume of a check.

WHAT IT IS NOT. It does not parse Kotlin. Regex literals and annotations with
nested braces in argument position can still confuse it. Treat a failure as "look
at this", never as "this file is broken".

AND WHEN IT REPORTS A FILE THE COMPILER ACCEPTED, THE SCANNER IS WRONG. Not the
file. That happened once already and cost more than the check has saved so far,
which is exactly why the message says so.

USAGE
    python3 .github/scripts/check_kt_braces.py FILE [FILE ...]
"""

import sys

# Lexer states.
CODE, STR, RAW, CHR = "code", "str", "raw", "chr"


def scan(path):
    """Return (final_depth, note). note is None when the file balances.

    A non-zero final depth means unbalanced. A negative depth means an extra `}`
    closed something never opened, which is the more informative failure and is
    reported separately, with the line it happened on.
    """
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as fh:
            src = fh.read()
    except OSError as exc:
        return None, "cannot read: %s" % exc

    i = 0
    n = len(src)
    line = 1
    depth = 0
    first_negative = None

    # A frame is [state, quote, template_nesting].
    #
    #   state             CODE, STR, RAW or CHR
    #   quote             '"' for a string, '' at file level. For a CODE frame
    #                     pushed by a "${", this is the quote of the string the
    #                     template is inside, so popping returns to scanning it.
    #   template_nesting  brace depth INSIDE a "${...}". Needed because a
    #                     template can contain blocks:
    #
    #                         "${if (x) { a } else { b }}"
    #
    #                     and the "}" that closes the `if` block must not be
    #                     mistaken for the "}" that ends the template. Without
    #                     this, AuthScreen.kt -- which is Compose, and therefore
    #                     full of exactly that -- reported depth 2 while the
    #                     build compiled it.
    stack = [[CODE, "", 0]]

    def top():
        return stack[-1]

    while i < n:
        state = top()[0]
        c = src[i]

        if c == "\n":
            line += 1
            i += 1
            # A "..." literal may span lines, so nothing resets here. A
            # single-quoted char literal may not, and an unterminated one is a
            # syntax error the compiler will report.
            if state == CHR:
                return depth, "unterminated char literal at line %d" % line
            continue

        # ------------------------------------------------------------- inside
        # a string literal, of either form.
        if state in (STR, RAW):
            # A backslash escape. Inside a RAW (triple-double-quoted) literal
            # Kotlin still honours escapes, so this applies to both.
            if state == STR and c == "\\":
                if i + 1 < n and src[i + 1] == "\n":
                    line += 1
                i += 2
                continue
            # "${" is CODE. Push a nested CODE frame that remembers this quote.
            if state == STR and c == "$" and i + 1 < n and src[i + 1] == "{":
                stack.append([CODE, top()[1], 0])
                i += 2
                continue
            if state == STR and c == '"':
                stack.pop()
                i += 1
                continue
            if state == RAW and src.startswith('"""', i):
                stack.pop()
                i += 3
                continue
            i += 1
            continue

        if state == CHR:
            if c == "\\":
                i += 2
                continue
            if c == "'":
                stack.pop()
                i += 1
                continue
            i += 1
            continue

        # ------------------------------------------------------------------- code
        if src.startswith('"""', i):
            stack.append([RAW, '"', 0])
            i += 3
            continue
        if src.startswith("//", i):
            while i < n and src[i] != "\n":
                i += 1
            continue
        if src.startswith("/*", i):
            end = src.find("*/", i + 2)
            if end < 0:
                return depth, "unterminated /* at line %d" % line
            line += src.count("\n", i, end)
            i = end + 2
            continue
        if c == '"':
            stack.append([STR, '"', 0])
            i += 1
            continue
        if c == "'":
            stack.append([CHR, "'", 0])
            i += 1
            continue
        in_template = len(stack) > 1 and top()[0] == CODE and stack[-1][1] != ""
        if c == "{":
            depth += 1
            if in_template:
                stack[-1][2] += 1
            i += 1
            continue
        if c == "}":
            if in_template and stack[-1][2] > 0:
                # A block inside the template. Not the template's own brace.
                stack[-1][2] -= 1
                depth -= 1
                if depth < 0 and first_negative is None:
                    first_negative = line
                i += 1
                continue
            if in_template:
                # The template's own brace: return to scanning the string.
                stack.pop()
                i += 1
                continue
            depth -= 1
            if depth < 0 and first_negative is None:
                first_negative = line
            i += 1
            continue
        i += 1

    if len(stack) > 1:
        return depth, "unterminated literal at end of file (line %d)" % line
    return depth, first_negative


def main(argv):
    paths = argv[1:]
    if not paths:
        sys.stderr.write("usage: check_kt_braces.py FILE [FILE ...]\n")
        return 2

    bad = 0
    for path in paths:
        depth, note = scan(path)
        if depth is None:
            print("FAIL: %s: %s" % (path, note))
            bad += 1
            continue
        if depth == 0 and note is None:
            continue
        bad += 1
        if isinstance(note, int):
            print(
                "FAIL: %s: depth %d at end of file, first NEGATIVE at line %d. "
                "A file the compiler accepted is the reference: if this reports on "
                "one, the SCANNER is wrong, not the file." % (path, depth, note)
            )
        else:
            print("FAIL: %s: depth %d at end of file (%s)" % (path, depth, note))

    print("  checked %d file(s), %d unbalanced" % (len(paths), bad))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))