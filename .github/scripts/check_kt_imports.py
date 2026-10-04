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
# Leading modifiers are consumed LOOSELY on purpose: `internal const val`,
# `internal inline fun`, `suspend fun` and `@Composable` all have to be skipped
# before the declared name. A tight list is what made gsHaptic and
# GS_SESSION_HEADER look undeclared.
DECL = re.compile(
    r"^\s*(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:(?:public|internal|private|protected|open|abstract|sealed|data|value|inline|"
    r"suspend|operator|infix|const|lateinit|expect|actual|external|override|tailrec|"
    r"companion|annotation|enum|inner|final|suspend)\s+)*"
    r"(?:class|interface|object|fun|val|var|typealias)\s+"
    r"([A-Za-z_]\w*)"
)
FUN_CONST = re.compile(r"^\s*(?:[a-z]+\s+)*?(?:const\s+)?val\s+([A-Za-z_]\w*)")

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
# Kotlin property-delegate operators. Their imports are consumed by `by` clauses and
# the names never appear in the source, so "imported but not referenced" is the
# wrong question for them.
DELEGATE_OPERATORS = frozenset((
    "getValue", "setValue", "provideDelegate",
    "getValueOf", "provideDelegateOf",
))

MUST_IMPORT = [
    "File", "IOException", "FileNotFoundException", "BufferedReader",
    "InputStreamReader", "PrintWriter", "ByteArrayOutputStream",
    "Base64", "CountDownLatch", "AtomicInteger", "AtomicBoolean", "AtomicLong",
    "TimeUnit", "UUID", "Locale", "Calendar", "SimpleDateFormat",
    "Pattern", "Matcher", "Charset", "StandardCharsets",
]


def mask_noncode(src):
    """Blank comments and string-literal contents so words inside them do not count.

    Block comments need state ACROSS lines. A KDoc like

        /**
         * First call mints and persists a UUID.
         */

    spans three lines, and masking line-by-line blanked only the `/**` line, so
    "UUID" on the middle line was read as code. That produced a false
    "uses UUID with no import" on a file whose real code calls
    `java.util.UUID.randomUUID()` fully qualified.
    """
    out = []
    in_block = False
    for line in src.split("\n"):
        if in_block:
            out.append("")
            if "*/" in line:
                in_block = "/*" in line.split("*/", 1)[1]
            continue
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
            if ch == "/" and i + 1 < len(line):
                if line[i + 1] == "/":
                    in_comment = True
                    break
                if line[i + 1] == "*":
                    # A block comment. If it does not close on this line it stays
                    # open, and the next lines are comment too.
                    rest = line[i + 2:]
                    if "*/" in rest:
                        i = i + 2 + rest.index("*/") + 2
                        continue
                    in_block = True
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
    warnings = 0
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
        # `by` DELEGATION USES AN OPERATOR WHOSE NAME NEVER APPEARS IN THE SOURCE.
        #
        #     var projects by mutableStateOf(emptyList<Project>())
        #
        # compiles only because getValue and setValue are imported, and neither
        # identifier occurs anywhere in the file. 75 false positives across the tree
        # before this: ProjectStore, VoiceScreen, Motion and 72 others. An import a
        # `by` clause consumes IS used, whether or not the name is ever written.
        #
        # This has to run BEFORE the per-import check below, not after it, which is
        # where the first attempt put it -- and a check that runs after the thing it
        # feeds has already been made is a check that does nothing.
        if re.search(r"\bby\s+[A-Za-z_]", body):
            for operator in DELEGATE_OPERATORS:
                imported_simple.discard(operator)

        for lineno, line in enumerate(lines, 1):
            m = ANY_IMPORT.match(line)
            if not m:
                continue
            fq, alias = m.group(1), m.group(2)
            local = LOCAL.match(line)
            if local:
                checked += 1
                segments = fq.split(".")
                # TRY EVERY PACKAGE SPLIT before judging.
                #
                # The first attempt rejected any import with more than two segments
                # after "com.grapsee", to avoid mis-judging a NESTED reference like
                # `ModelStore.Consent`. That also rejected
                # `com.grapsee.gsai.data.local.SettingsStore`, which has six segments
                # and no nesting at all -- so the check stopped catching the exact
                # mistake it was written for. Segment COUNT is not the discriminator;
                # whether a split RESOLVES is.
                #
                #   com.grapsee.gsai.data.local.ModelStore.Consent
                #     k=5 -> package com.grapsee.gsai.data.local declares ModelStore
                #            -> accept; the remainder is assumed to be nested members
                #   com.grapsee.gsai.data.local.SettingsStore
                #     no split resolves, and SettingsStore is declared in
                #     com.grapsee.gsai.data -> FLAG, naming the right package
                resolved = False
                for k in range(2, len(segments)):
                    if segments[k] in by_package.get(".".join(segments[:k]), set()):
                        resolved = True
                        break
                if resolved:
                    continue
                simple = segments[-1]
                # ONLY "DECLARED IN A DIFFERENT PACKAGE" IS A FINDING.
                #
                # The first version also reported "not declared anywhere", which
                # produced 23 false positives on this tree and every one of them was
                # something this check cannot see:
                #
                #   internal const val GS_SESSION_HEADER   a modifier the
                #     declaration regex does not consume
                #   ModelStore.Consent                     a NESTED class; `Consent`
                #     is not a top-level declaration in package data.local
                #   com.grapsee.gsai.BuildConfig           GENERATED BY GRADLE and
                #     not present in any source file at all, by design
                #   gsHaptic, kineticPress, gsContentWidth top-level extensions
                #     whose declarations the same regex misses
                #
                # "Declared nowhere" is not a detectable condition -- it is a
                # statement about the limits of this parser. "Declared HERE but you
                # imported it from THERE" is exactly the mistake that cost
                # android-device 37179305182 a run, and it is decidable.
                where = sorted(p for p, ns in by_package.items() if simple in ns)
                if not where:
                    continue
                problems += 1
                print("::error::%s:%d  import %s does not resolve"
                      % (os.path.basename(path), lineno, fq))
                print("::error::  '%s' is declared in: %s" % (simple, ", ".join(where)))
                print("::error::  this is a COMPILE ERROR and it costs a full "
                      "instrumented run to find out")
            name = alias or fq.rsplit(".", 1)[-1]
            if not name or name == "*":
                continue
            if name in DELEGATE_OPERATORS and re.search(r"\bby\s+[A-Za-z_]", body):
                continue
            # `(?<![\w])` and NOT `(?<![\w.])`: a leading dot must NOT disqualify
            # the reference, because an extension function is CALLED through one.
            # MainActivity.kt imports fillMaxSize and uses it as
            #     Modifier.fillMaxSize()
            # which the stricter lookbehind excluded, so the file was reported as
            # carrying an unused import.
            if not re.search(r"(?<![\w])%s\b" % re.escape(name), body):
                # A WARNING, NOT A FAILURE. An unused import is not a defect: the
                # tree contains a couple of dozen real ones and the build was green
                # with every one of them for months. Failing here would invite
                # `--no-verify` and would take the two checks that DO catch real
                # compile errors -- wrong package, and used-but-never-imported --
                # down with it, because a lint people disable is a lint that
                # protects nothing.
                warnings += 1
                print("::warning::%s:%d  imports %s but never references it"
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
        print("FAIL: %d compile-error-class problem(s), %d local import(s) resolved"
              % (problems, checked))
        return 1
    tail = (", %d unused import(s) reported as warnings" % warnings) if warnings else ""
    print("ok: %d local import(s) resolve; no unimported framework types%s"
          % (checked, tail))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))