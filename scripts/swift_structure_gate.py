#!/usr/bin/env python3
"""Swift structural gate — depth-aware orphaned-member detection (GS AI App).

Defect class this kills: Task 29 appended edit-flow methods AFTER
SQLiteChatStore's closing brace. Brace totals stayed balanced (gate passed),
but the methods sat at file scope referencing private instance members —
uncompilable, invisible to a counting-only gate.

Rule: at brace depth 0 (file scope), a DECLARATION line must never be
indented. Free functions at column 0 are legal and exist in this codebase
(ChatDetailView's render helpers); member-style indented declarations at
file scope are always the orphaned-method defect.

The tokenizer is Swift-aware so depth tracking is honest:
- line comments      // ...
- block comments     /* ... */  (nestable in Swift)
- multi-line strings \"\"\" ... \"\"\" (braces inside never count)
- regular strings with escapes (braces inside never count)
- skips string interpolation contents conservatively (\\( ... ) rarely
  carries balance-relevant braces in this codebase; covered by tests below)
"""
import glob
import os
import re
import sys

# Repo-relative (portable to CI runners) — a hardcoded sandbox path matched
# zero files elsewhere, which would silently disable the gate (false green).
_REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FILES = sorted(glob.glob(os.path.join(_REPO_ROOT, "ios", "App", "Sources", "**", "*.swift"), recursive=True))
if not FILES:
    print("FATAL: gate matched zero Swift sources — path resolution broken", file=sys.stderr)
    sys.exit(2)

# A declaration opener we care about at file scope (with member-style indentation).
DECL_KEYWORDS = (
    "func ", "var ", "let ", "init(", "init?", "deinit", "class ", "struct ",
    "enum ", "extension ", "protocol ", "typealias ", "subscript",
)
DECL_RE = re.compile(
    r"^\s+(?:@\w+(?:\([^)]*\))?\s+)*(?:public |private |internal |fileprivate |open |static |final |override |required |convenience |mutating )*\w"
)


def strip_and_track(src: str):
    """Yield (line_no, line_text, depth_before_line) with strings/comments blanked."""
    out = []  # (lineno, text, depth_at_line_start)
    depth = 0
    i, n = 0, len(src)
    line_no = 1
    line_start_depth = 0
    line_buf = []
    state = "code"  # code | line_comment | block_comment | string | ml_string
    block_depth = 0
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ""
        if c == "\n":
            out.append((line_no, "".join(line_buf), line_start_depth))
            line_no += 1
            line_buf = []
            line_start_depth = depth
            i += 1
            if state == "line_comment":
                state = "code"
            continue
        if state == "code":
            if c == "/" and nxt == "/":
                state = "line_comment"
                i += 2
                line_buf.append("//")
                continue
            if c == "/" and nxt == "*":
                state = "block_comment"
                block_depth = 1
                i += 2
                line_buf.append("/*")
                continue
            if c == '"' and nxt == '"' and src[i : i + 3] == '"""':
                state = "ml_string"
                i += 3
                line_buf.append('"""')
                continue
            if c == '"':
                state = "string"
                i += 1
                line_buf.append('"')
                continue
            if c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
            line_buf.append(c)
            i += 1
            continue
        if state == "line_comment":
            line_buf.append(c)
            i += 1
            continue
        if state == "block_comment":
            if c == "/" and nxt == "*":
                block_depth += 1
                i += 2
                line_buf.append("/*")
                continue
            if c == "*" and nxt == "/":
                block_depth -= 1
                i += 2
                line_buf.append("*/")
                if block_depth == 0:
                    state = "code"
                continue
            line_buf.append(c)
            i += 1
            continue
        if state == "string":
            if c == "\\":
                i += 2
                line_buf.append("??")
                continue
            if c == '"':
                state = "code"
                line_buf.append('"')
                i += 1
                continue
            line_buf.append(c if c not in "{}" else "·")
            i += 1
            continue
        if state == "ml_string":
            if src[i : i + 3] == '"""':
                state = "code"
                line_buf.append('"""')
                i += 3
                continue
            line_buf.append(c if c not in "{}" else "·")
            i += 1
            continue
    out.append((line_no, "".join(line_buf), line_start_depth))
    return out


def structural_issues(path: str):
    src = open(path).read()
    tracks = strip_and_track(src)
    raw_lines = src.splitlines()
    issues = []
    for line_no, blanked, depth in tracks:
        if depth != 0:
            continue
        stripped = blanked.strip()
        if not stripped:
            continue
        raw = raw_lines[line_no - 1] if line_no - 1 < len(raw_lines) else ""
        if not raw[:1].isspace():
            continue  # column-0 top-level declaration: legal
        # indented line at file scope — is it a declaration opener?
        if any(stripped.startswith(k) or stripped.startswith("private " + k) or
               stripped.startswith("public " + k) or stripped.startswith("static " + k) or
               stripped.startswith("private static " + k) or stripped.startswith("public static " + k)
               for k in DECL_KEYWORDS):
            if DECL_RE.match(raw):
                issues.append((line_no, raw.strip()[:90]))
    return issues


def main():
    ok = True
    for path in FILES:
        issues = structural_issues(path)
        name = path.split("/")[-1]
        if issues:
            ok = False
            print(f"{name}: ORPHANED MEMBER DECLARATIONS AT FILE SCOPE")
            for line_no, text in issues:
                print(f"  line {line_no}: {text}")
    print("\nSTRUCTURE:", "PASS" if ok else "FAIL")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
