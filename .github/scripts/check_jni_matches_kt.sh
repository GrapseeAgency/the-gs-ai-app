# check_jni_matches_kt.sh -- every Kotlin `external fun` must have a JNI symbol in
# the .so that is about to be installed.
#
# WHY. A version skew between the app and the native library presents as a
# CASCADE of unrelated failures, not as one. Run 36868936414 installed a .so from
# an older commit than the Kotlin:
#
#   Tests 23/23 completed (16 failed)
#   No implementation found for boolean
#     com.grapsee.gsai.native.GsNative.initTuned(java.lang.String, int, int)
#
# THE (String, int, int) ABOVE IS A VERBATIM QUOTE FROM THAT RUN, NOT THE CURRENT
# SIGNATURE. initTuned now takes seven arguments -- (String, int, int, int, int,
# int, int) -- because the performance levers were added to it. The quote is left
# exactly as the runner printed it, because editing evidence is worse than
# confusing evidence, but a reader who takes it for the current signature will
# conclude the Kotlin and this script disagree when they do not.
#
# It also shows what the gate is FOR and is not for. Android's JNI resolver
# matches a native method by NAME plus a mangled signature that includes the
# argument types, so an arity change between the app and the .so produces
# precisely this error. This gate checks that a JNI symbol EXISTS for each
# Kotlin declaration; it does not compare arities, because the mangled name in the
# .so is what carries the arity and reading it is a different check.
#
# Fifteen of the sixteen said only "no native context; call init(modelPath)
# first", which points at the model rather than at the version. The one line that
# named the cause was in a logcat artifact, after a 25-minute emulator run.
#
# FAIL CLOSED IN THREE NAMED WAYS, because a gate whose own failure is a silent
# abort or a false accusation is worse than no gate:
#
#   nm cannot read the .so          -> named, exit 1. Never "symbols missing": a
#                                     failed read reported as a mismatch accuses
#                                     the code of something it did not check.
#   the .so exports no JNI at all   -> named, exit 1. That is a library built
#                                     without the JNI layer, a different fault.
#   no Kotlin external fun found    -> named, exit 1. Otherwise this passes by
#                                     checking nothing, which is how a gate stops
#                                     gating.
#
# Usage: check_jni_matches_kt.sh PATH_TO_SO PATH_TO_KOTLIN_DIR
set -euo pipefail
SO="$1"; KTDIR="$2"

# READ THE SYMBOLS TO A FILE FIRST. `nm ... | grep ... > x` inside a pipeline
# loses nm's exit status, and this gate's first version read a file nm REJECTED
# ("file format not recognized") and reported EVERY symbol missing -- a gate whose
# own failure mode is a false accusation of the code.
SYMFILE=$(mktemp)
trap 'rm -f "$SYMFILE"' EXIT
if ! nm -D --defined-only "$SO" > "$SYMFILE" 2>/dev/null; then
  echo "FAIL: nm could not read $SO at all, so this check cannot run."
  echo "      A failed read must not be reported as 'symbols are missing'."
  exit 1
fi
# `|| true` AND THEN A COUNT CHECK, never `|| true` alone.
#
# grep exits 1 when it matches nothing, pipefail makes that the substitution's
# status, and `set -e` kills the step -- so the natural spelling of this line
# reports "no symbols" as a silent abort with no message, which is how the check
# was silent on cases 4 and 5. The `|| true` keeps the empty result; the count
# check below turns that empty result into a NAMED failure.
GOT=$(grep -oE 'Java_com_grapsee_gsai_native_[A-Za-z_0-9]+' "$SYMFILE" \
        | sed 's/.*_GsNative_//' | sort -u || true)

WANT=$(grep -rhoE 'external fun [a-zA-Z_0-9]+' "$KTDIR" \
        | sed 's/external fun //' | sort -u || true)

NGOT=$(printf '%s\n' "$GOT" | sed '/^$/d' | wc -l | tr -d ' ')
NWANT=$(printf '%s\n' "$WANT" | sed '/^$/d' | wc -l | tr -d ' ')
echo "  Kotlin external funs: $NWANT    JNI symbols in the .so: $NGOT"

if [ "$NWANT" -eq 0 ]; then
  echo "FAIL: no Kotlin external fun found under $KTDIR, so this check would"
  echo "      pass by checking nothing. The find/grep pattern is wrong."
  exit 1
fi

if [ "$NGOT" -eq 0 ]; then
  echo "FAIL: $SO exports NO JNI symbols at all, while the Kotlin declares $NWANT."
  echo "      That is a library built without the JNI layer, not a mismatch."
  exit 1
fi

MISSING=$(comm -23 <(printf '%s\n' "$WANT") <(printf '%s\n' "$GOT"))
if [ -n "$MISSING" ]; then
  echo "FAIL: the Kotlin CALLS these and the .so DOES NOT DEFINE them:"
  printf '%s\n' "$MISSING" | sed 's/^/      /'
  echo "      The library is OLDER than the app. native_run_id must be a run of"
  echo "      android-native at a commit that added them, or every test that"
  echo "      touches this path fails for a reason that has nothing to do with it."
  exit 1
fi
echo "  every Kotlin external fun has a JNI symbol in the .so"
