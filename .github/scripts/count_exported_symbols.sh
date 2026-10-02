#!/usr/bin/env bash
# count_exported_symbols <archive> <prefix>
#
# Counts defined, external symbols in a static archive whose name starts with
# <prefix>, and prints the count on stdout.
#
# WHY THIS EXISTS
#
# Xcode's `nm` cannot read the objects Rust 1.98.1 produces. Raw error, run
# 36377091198:
#
#   nm: error: .../libgs_ffi.a(gs_ffi.gs_ffi.a2d54f7d968067d7-cgu.0.rcgu.o):
#     Unknown attribute kind (105)
#     (Producer: 'LLVM22.1.8-rust-1.98.1-stable'
#      Reader: 'LLVM APPLE_1_2100.1.1.101_0')
#
# Xcode 26.6 ships an LLVM newer than the Rust toolchain's, and it rejects the
# object files rather than degrading. The gs_ffi member is among the rejected
# ones, so the count came back 0 -- which reads exactly like "the library
# exports nothing" and is a completely different claim.
#
# Earlier this same check hid the problem twice before: `nm -gU ... 2>/dev/null`
# turned the failure into a count of 0 with no explanation, and then the
# no-suppression version surfaced a wall of per-member errors that still did not
# say the obvious thing, which is that the reader is the wrong version.
#
# The fix is to use the reader that produced the objects: `llvm-nm` from the
# Rust toolchain itself, via the llvm-tools component. That is the same LLVM
# that emitted the files, so it is guaranteed to read them.
#
# The version mismatch is still reported, loudly, when Apple nm is available and
# disagrees. A toolchain that cannot read its own build output is worth knowing
# about even when a workaround exists, because the next thing to use nm -- a link
# check, a size report, a crash triage -- will hit it again.
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <archive> <prefix>" >&2
  exit 2
fi

archive="$1"
prefix="$2"

if [ ! -f "$archive" ]; then
  echo "no such archive: $archive" >&2
  exit 1
fi

sysroot="$(rustc --print sysroot)"

# rustup ships llvm-nm only with the llvm-tools component. Install it rather than
# discover mid-step that it is missing.
if ! find "$sysroot" -name 'llvm-nm' -type f 2>/dev/null | grep -q .; then
  rustup component add llvm-tools >/dev/null 2>&1 || true
fi
llvm_nm="$(find "$sysroot" -name 'llvm-nm' -type f 2>/dev/null | sed -n '1p')"

if [ -z "$llvm_nm" ]; then
  echo "FAIL: llvm-nm not found under $sysroot and llvm-tools could not be installed." >&2
  echo "      Cannot read $archive: Apple's nm rejects Rust objects from this" >&2
  echo "      toolchain (Unknown attribute kind), and there is no fallback reader." >&2
  exit 1
fi

echo "reader: $llvm_nm" >&2
echo "  rustc : $(rustc --version)" >&2
echo "  llvm-nm: $("$llvm_nm" --version | sed -n '1,2p' | tr '\n' ' ')" >&2

# --defined-only --extern-only: what we mean by "exports".
# Errors are captured, not discarded. If llvm-nm also cannot read the archive,
# that is reported as a failure rather than as a count of zero.
if ! out="$("$llvm_nm" --defined-only --extern-only "$archive" 2>&1)"; then
  echo "FAIL: $llvm_nm could not read $archive" >&2

  # A TOOLCHAIN SKEW READS LIKE A BROKEN ARCHIVE. Say which one it is.
  #
  # ios-native 37020835936, verbatim from the runner:
  #
  #   reader: .../toolchains/stable-aarch64-apple-darwin/.../llvm-nm
  #   rustc : rustc 1.98.1 (48a229cea 2026-09-01)
  #   .../llvm-nm: error: ...libgs_ffi.a(...rcgu.o): Not an int attribute
  #     (Producer: 'LLVM23.1.1-rust-1.99.0-stable' Reader: 'LLVM 22.1.8-rust-1.98.1-stable')
  #
  # "Not an int attribute" names a property of the OBJECT. It is not one. llvm-nm
  # prints the object format version it was produced by and the one it reads, and
  # they differ by a major version. An older reader meeting a newer attribute
  # kind fails exactly this way, so the message that looks like a corrupt
  # archive is a statement about the READER.
  #
  # This is the eleventh time a tool's failure has been reported as a property of
  # the artifact. It is worth detecting by NAME rather than leaving the next
  # reader to work it out, because the error text points at the wrong file.
  PRODUCER=$(printf '%s\n' "$out" | sed -n "s/.*Producer: '\([^']*\)'.*/\1/p" | sed -n '1p')
  READER_V=$(printf '%s\n' "$out" | sed -n "s/.*Reader: '\([^']*\)'.*/\1/p" | sed -n '1p')
  if [ -n "$PRODUCER" ] && [ -n "$READER_V" ] && [ "$PRODUCER" != "$READER_V" ]; then
    echo "" >&2
    echo "      THIS IS TOOLCHAIN SKEW, NOT A MALFORMED ARCHIVE." >&2
    echo "      object produced by: $PRODUCER" >&2
    echo "      read by          : $READER_V   (\"$llvm_nm\")" >&2
    echo "      An LLVM reader older than the object cannot decode its attribute" >&2
    echo "      kinds, and reports them as 'Not an int attribute'. The fix is to" >&2
    echo "      BUILD and READ with the same toolchain -- see the --default-toolchain" >&2
    echo "      pin in ios-native.yml, which is why producer and reader must not be" >&2
    echo "      two independent resolutions of the word 'stable'." >&2
  fi

  printf '%s\n' "$out" | sed -n '1,20p' >&2
  exit 1
fi

count="$(printf '%s\n' "$out" | grep -cE "^[0-9a-fA-F]+ [A-TV-Z] ${prefix}" || true)"
# The stricter pattern above can miss on symbol formatting differences, so also
# count a plain name match and take the larger, reporting both. Under-reporting a
# passing library is the same class of error as over-reporting a broken one.
loose="$(printf '%s\n' "$out" | grep -cE "${prefix}" || true)"
echo "  total defined extern symbols: $(printf '%s\n' "$out" | grep -c . || true)" >&2
if [ "$loose" -gt "$count" ]; then
  echo "  note: strict pattern matched $count, name match $loose" >&2
fi
printf '%s\n' "$loose"
