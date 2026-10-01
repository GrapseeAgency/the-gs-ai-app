#!/usr/bin/env bash
# check_no_process_spawn.sh -- the shipped libgs_ffi.so must not be able to run a
# shell, and the process-creating symbols it DOES import must have exactly the one
# caller we already know about.
#
# WHY THIS IS IN CI AND NOT ON THE DEVICE.
#
# The device test (c2_the_shipped_library_imports_no_process_spawning_symbol)
# parses .dynsym and can therefore answer "which symbols does this library
# import". It cannot answer "who CALLS them" -- that needs a disassembler, and
# nm/objdump do not exist on an Android device. So the split is by what each place
# can actually establish:
#
#   on the device   the complete SET of process-creating imports is pinned
#   in CI           each of them has exactly ONE call site, and it is named
#
# MEASURED, on the x86_64 build from run 36877377145:
#
#     $ nm -D --undefined-only libgs_ffi.so | grep -E ' (fork|execlp|waitpid)$'
#          U execlp@LIBC
#          U fork@LIBC
#          U waitpid@LIBC
#
#     $ objdump -d libgs_ffi.so | awk '/call.*<fork@plt>/{print prev} {...}'
#     ggml_print_backtrace
#
# ggml forks `addr2line` to symbolise a stack trace when it is about to abort. One
# call site, on the crash path, not on the image generation path or the chat path,
# and in a library this project neither owns nor wants to lose, because it is what
# makes a crash log worth reading.
#
# WHY "NOT ABSENT" IS NOT THE CLAIM. An earlier version of the device test
# asserted the .so contained no process-spawning name. It imports three, so that
# assertion was false and the test would have stayed red forever. The claim that is
# both true and useful is: the SET is exactly these three, and each has exactly one
# caller.
#
# WHY A BYTE SCAN IS NOT ENOUGH. That version scanned the file's bytes for the
# names and reported `system`, which the library does not import at all -- `nm -D`
# says 0. A byte scan cannot tell a symbol from a string in .rodata, so it accuses
# the library of calling something it never mentions in a symbol table.
#
# USAGE: check_no_process_spawn.sh PATH_TO_SO
set -euo pipefail

SO="${1:-}"
if [ -z "$SO" ] || [ ! -f "$SO" ]; then
  echo "FAIL: usage: check_no_process_spawn.sh PATH_TO_SO  (got '$SO')"
  exit 1
fi

# The process-creating set. Kept as a list because it is a CLAIM about the future:
# a dependency that starts using one of these fails the check by name.
EXPECTED="execlp fork waitpid"

# THE EXPECTED SET COMPARED AS A SET, not by counting matches. A count can be right
# with the wrong membership, which is how "expected 3" passes on {fork, vfork,
# system}.
# THE @VERSION SUFFIX IS STRIPPED, and it has to be.
#
# Android's libc exports these as `fork@LIBC`, and nm prints that suffix:
#
#     $ nm -D --undefined-only libgs_ffi.so | grep -E ' (fork)$'
#     (nothing)
#     $ nm -D --undefined-only libgs_ffi.so | grep -E ' fork@LIBC$'
#     U fork@LIBC
#
# So an anchored pattern against the bare name finds nothing, and this check says
# "no process-creating imports in a library that calls ggml_print_backtrace" --
# which reads as a finding about the LIBRARY and is actually a finding about the
# PATTERN. Same shape as the byte scan's `system`: a real import the check cannot
# see. And the failure was found by running the check against the real .so, not by
# reading it.
#
# The suffix lives in a version table rather than in .dynstr, which is why the
# device test's raw names come out bare.
# nm's OUTPUT IS CHECKED BEFORE IT IS BELIEVED.
#
# On a file nm cannot read it prints "file format not recognized" to stderr and
# exits non-zero. Inside a pipeline that status is lost, the substitution comes
# back empty, and the check then reports:
#
#     FAIL: nm reported NO process-creating imports in a library that calls
#           ggml_print_backtrace.
#
# which is a statement about a LIBRARY, produced by a failure to READ one. Same
# shape as check_jni_matches_kt.sh's first version, which read an unreadable file
# and accused the code of missing every symbol. Both were found by running the
# check against a deliberately bad file rather than by reading it.
NM_OUT=$(mktemp)
trap 'rm -f "$NM_OUT"' EXIT
if ! nm -D --undefined-only "$SO" > "$NM_OUT" 2>/dev/null; then
  echo "FAIL: nm could not read $SO at all, so this check cannot run."
  echo "      A failed READ is not a finding about the library, and must never be"
  echo "      reported as one."
  exit 1
fi

GOT=$(awk '{print $NF}' "$NM_OUT" \
      | sed 's/@.*$//' \
      | grep -E '^(fork|vfork|clone|waitpid|wait3|wait4|execve|execl|execlp|execv|execvp|execvpe|posix_spawn|posix_spawnp|system|popen)$' \
      | sort -u || true)

echo "  SO:                     $SO"
echo "  process-creating imports: $(printf '%s' "$GOT" | tr '\n' ' ')"

if [ -z "$GOT" ]; then
  echo "FAIL: nm reported NO process-creating imports in a library that calls"
  echo "      ggml_print_backtrace. Either nm's output shape changed, or this is a"
  echo "      different .so than the one that was measured. Both are worth knowing."
  exit 1
fi

EXTRA=$(comm -13 <(printf '%s\n' $EXPECTED | sort -u) <(printf '%s\n' "$GOT"))
if [ -n "$EXTRA" ]; then
  echo "FAIL: the library imports process-creating symbols this check does not"
  echo "      know about. Each one named:"
  printf '%s\n' "$EXTRA" | sed 's/^/      /'
  echo "      A new one is a dependency that spawns a process. On a phone that"
  echo "      means shelling out to something the device does not have."
  exit 1
fi

MISSING=$(comm -23 <(printf '%s\n' $EXPECTED | sort -u) <(printf '%s\n' "$GOT"))
if [ -n "$MISSING" ]; then
  echo "FAIL: expected these process-creating imports and they are ABSENT:"
  printf '%s\n' "$MISSING" | sed 's/^/      /'
  echo "      The .so is not the one this check was calibrated against, or ggml"
  echo "      changed. Read the new one before changing this list."
  exit 1
fi

# AND EACH ONE HAS EXACTLY ONE CALL SITE, AND IT IS ggml_print_backtrace.
#
# ONE objdump pass. The symbol set goes in as a REGEX built here, not as a variable
# iterated inside awk. The first version passed EXPECTED_ARR="$EXPECTED" and did
# `for (e in EXPECTED_ARR)`, which is a SCALAR in awk and fatals:
#
#     awk: cmd. line:4: (FILENAME=- FNR=67) fatal: attempt to use scalar
#     `EXPECTED_ARR' as an array
#
# So the call-site half reported "no PLT call found" -- byte-for-byte the conclusion
# a disassembler that had genuinely found nothing would give, from a script that had
# never disassembled anything. Two of this check's three failures so far have been
# the check failing to look, not the library being wrong.
#
# `grep -B` is not an alternative: the symbol header naming the enclosing function
# precedes a call site by an arbitrary number of lines, so context lines are not a
# bound. The symbol is tracked explicitly instead.
CALLERS=$(objdump -d --no-show-raw-insn "$SO" \
          | awk -v want="^(execlp|fork|waitpid)$" '
              /^[0-9a-f]+ <.*>:/ {
                sym = $2
                gsub(/[<>:]/, "", sym)
                next
              }
              index($0, "call") && match($0, /<[A-Za-z_0-9@]+@plt>/) {
                target = substr($0, RSTART + 1, RLENGTH - 2)
                sub(/@plt$/, "", target)
                if (target ~ want) print target " " sym
              }
            ' \
          | sort -u || true)

echo "  call sites:"
if [ -z "$CALLERS" ]; then
  echo "FAIL: the symbols are imported but objdump found no PLT call to any of"
  echo "      them. The imports are then dead weight rather than a live path,"
  echo "      which is BETTER -- but it means this .so is not the one that was"
  echo "      measured, and the list above should be re-derived."
  exit 1
fi
printf '%s\n' "$CALLERS" | sed 's/^/      /'

BAD=$(printf '%s\n' "$CALLERS" | awk '$2 != "ggml_print_backtrace"')
if [ -n "$BAD" ]; then
  echo "FAIL: a process-creating symbol is called from somewhere OTHER than"
  echo "      ggml_print_backtrace. That is a NEW live path to spawning:"
  printf '%s\n' "$BAD" | sed 's/^/      /'
  exit 1
fi

echo "  every one is called only from ggml_print_backtrace, which forks"
echo "  addr2line to symbolise a crash. No shell is reachable from this library."