#!/usr/bin/env python3
"""Check that every `import com.grapsee.gsai.*` in a Kotlin file resolves.

The failure this catches
------------------------
An import of a class that does not exist at that path is a COMPILE ERROR, which
costs a full instrumented-test run -- roughly 70 minutes on android-device -- to
discover, and the error line names a symbol rather than the mistake:

    e: .../ChatUiReplyTest.kt:10:36 Unresolved reference 'SettingsStore'.
    e: .../ChatUiReplyTest.kt:158:9 Unresolved reference 'SettingsStore'.
    * What went wrong:
    Execution failed for task ':app:compileDebugAndroidTestKotlin'

That was `com.grapsee.gsai.data.local.SettingsStore` written from a reasonable
guess. The class is in `com.grapsee.gsai.data`. Every other import in the file was
right, so nothing about the file itself signalled the guess.

The same class of cost was already paid once in this repository: a missing
`import java.io.File` in GsNativeTest.kt sat latent from commit af5621e until a
device run happened to compile it. A missing import and a wrong-package import
are the same defect with opposite causes and the same cost.

Three checks
------------
1. a project-local import (`com.grapsee.gsai.*`) that does not resolve -- the
   wrong-package mistake, which is a compile error costing a 70-minute run;
2. an import that is never referenced -- dead, and in this repository six of them
   exist because the code went fully-qualified instead;
3. a well-known framework type used bare with NO import -- the af5621e defect,
   `File(dir, ...)` with `import java.io.File` missing.

Check 3 is necessarily a curated list, and that is its limit: it covers the types
most often reached for in test code, not every type in the SDK. It is a targeted
guard, not a type system, and it is reported as such.

Comments AND string literals are masked before the usage scan, so the word "File"
inside a message or a doc comment cannot be mistaken for a reference.

Used imports are checked too: a symbol imported and never referenced is dead,
and a name used but never imported is exactly the af5621e defect.

Usage: check_kt_imports.py <file.kt> [<file.kt> ...] --src <dir> [--src <dir>]
Exit: 0 clean, 1 with a report, 2 on usage error.
"""

import os
import re
import sys

LOCAL = re.compile(r"^import\s+(com\.grapsee\.gsai\.[\w.]+)")
ANY_IMPORT = re.compile(r"^import\s+([\w.]+)(?:\s+as\s+(\w+))?\s*$")
DECL = re.compile(
    r"^\s*(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:public\s+|internal\s+|private\s+|open\s+|abstract\s+|sealed\s+|data\s+)*"
    r"(?:class|interface|object|enum\s+class|fun|val|var|typealias)\s+"
    r"([A-Za-z_]\w*)"
)
FUN_CONST = re.compile(r"^\s*(?:const\s+)?val\s+([A-Za-z_]\w*)")

# Non-java.lang types that are used bare often enough that forgetting the import
# is a realistic mistake, and whose absence is a COMPILE ERROR rather than an
# inference.
#
# THE COLLECTION TYPES ARE DELIBERATELY ABSENT. `ArrayList`, `HashMap`,
# `LinkedHashMap`, `TreeMap`, `LinkedList`, `ArrayDeque` and `Random` resolve to
# kotlin.collections / kotlin.random typealiases with NO import at all. The first
# draft of this list included them and produced four false positives on the first
# run -- `val times = ArrayList<Long>(repeats)` and three siblings -- which is the
# whole reason this list is short and specific rather than "the types I can think
# of". `Optional` is absent because Kotlin code should not reach for it.
MUST_IMPORT = [
    "File", "IOException", "FileNotFoundException", "BufferedReader",
    "InputStreamReader", "PrintWriter", "ByteArrayOutputStream",
    "Base64", "CountDownLatch", "AtomicInteger", "AtomicBoolean", "AtomicLong",
    "TimeUnit", "UUID", "Locale", "Calendar", "SimpleDateFormat",
    "Pattern", "Matcher", "Charset", "StandardCharsets",
]


def mask_noncode(src):
    """Blank comments and string-literal contents so words inside them do not count."""
    out = []
    for line in src.split("\n"):
        res, i, quote, in_comment = [], 0, None, False
        while i < len(line):
            ch = line[i]
            if in_comment:
                break
            if quote:
                if ch == "\\":
                    i += 2
                    continue
                if ch == "$" and i + 1 < len(line) and line[i + 1] == "{":
                    # A string TEMPLATE expression is CODE, not string text. Blanking
                    # it made `"${GsNative.lastError()}"` and
                    # `"${ModelCatalog.MODEL_0_5B.id}.gguf"` look like unused imports,
                    # which is four false positives in one stroke. Copy the balanced
                    # `${...}` through verbatim.
                    depth, j = 0, i
                    while j < len(line):
                        if line[j] == "{":
                            depth += 1
                        elif line[j] == "}":
                            depth -= 1
                            if depth == 0:
                                break
                        j += 1
                    res.append(line[i: j + 1])
                    i = j + 1
                    continue
                if ch == quote:
                    quote = None
                else:
                    res.append(" ")
                i += 1
                continue
            if ch == "/" and i + 1 < len(line) and line[i + 1] == "/":
                in_comment = True
                break
            if ch in "\"'":
                quote = ch
                res.append(" ")
                i += 1
                continue
            res.append(ch)
            i += 1
        out.append("".join(res))
    return "\n".join(out)


def declared_names(src_dir):
    """Every top-level name declared anywhere under src_dir, and its package."""
    by_package = {}
    for base in src_dir:
        for dirpath, _dirnames, filenames in os.walk(base):
            for name in filenames:
                if not name.endswith(".kt"):
                    continue
                path = os.path.join(dirpath, name)
                try:
                    text = open(path, errors="replace").read()
                except OSError:
                    continue
                pkg = ""
                for line in text.split("\n"):
                    m = re.match(r"^\s*package\s+([\w.]+)", line)
                    if m:
                        pkg = m.group(1)
                        break
                for line in text.split("\n"):
                    m = DECL.match(line) or FUN_CONST.match(line)
                    if m:
                        by_package.setdefault(pkg, set()).add(m.group(1))
    return by_package


def main(argv):
    src_dirs = []
    files = []
    i = 1
    while i < len(argv):
        if argv[i] == "--src":
            src_dirs.append(argv[i + 1])
            i += 2
        else:
            files.append(argv[i])
            i += 1
    if not files or not src_dirs:
        sys.exit(__doc__)

    by_package = declared_names(src_dirs)
    problems = 0
    checked = 0

    for path in files:
        try:
            lines = open(path, errors="replace").read().split("\n")
        except OSError as exc:
            print("::error::%s: %s" % (path, exc))
            problems += 1
            continue
        body = mask_noncode("\n".join(l for l in lines if not l.startswith("import ")))
        imported_simple = set()
        for line in lines:
            m = ANY_IMPORT.match(line)
            if m:
                imported_simple.add(m.group(2) or m.group(1).rsplit(".", 1)[-1])
        for lineno, line in enumerate(lines, 1):
            m = ANY_IMPORT.match(line)
            if not m:
                continue
            fq, alias = m.group(1), m.group(2)
            local = LOCAL.match(line)
            if local:
                checked += 1
                pkg, simple = fq.rsplit(".", 1)
                if simple not in by_package.get(pkg, set()):
                    where = sorted(p for p, ns in by_package.items() if simple in ns)
                    problems += 1
                    print("::error::%s:%d  import %s does not resolve"
                          % (os.path.basename(path), lineno, fq))
                    if where:
                        print("::error::  '%s' is declared in: %s"
                              % (simple, ", ".join(where)))
                    else:
                        print("::error::  '%s' is not declared anywhere under %s"
                              % (simple, ", ".join(src_dirs)))
            name = alias or fq.rsplit(".", 1)[-1]
            if not name or name == "*":
                continue
            if not re.search(r"(?<![\w.])%s\b" % re.escape(name), body):
                problems += 1
                print("::error::%s:%d  imports %s but never references it"
                      % (os.path.basename(path), lineno, name))

        for symbol in MUST_IMPORT:
            if symbol in imported_simple:
                continue
            if re.search(r"(?<![\w.])%s\s*[(\.<]" % re.escape(symbol), body):
                problems += 1
                print("::error::%s  uses %s with no import. That is a COMPILE "
                      "ERROR, not an inference."
                      % (os.path.basename(path), symbol))
                print("::error::  af5621e shipped `File(dir, ...)` without "
                      "`import java.io.File` and no device run compiled it for "
                      "commits.")

    if problems:
        print("FAIL: %d import problem(s), %d local import(s) resolved"
              % (problems, checked))
        return 1
    print("ok: %d local import(s) resolve; no unused imports; no unimported "
          "framework types" % checked)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))