Here are all the generated files.

## Latest release: v0.71.0

    file    GS-AI-App.apk
    size    84104873 bytes
    sha256  d9562145881999941aaced1491e390db11f42c2bbabc69d63c9a191ddaabc1ed

Native chat ships in this build for the first time: `libgs_ffi.so` is present for
all four ABIs (armeabi-v7a, arm64-v8a, x86, x86_64), alongside ML Kit OCR. The
previous mirror APK was 20,913,323 bytes and contained no native library at all, so
the prefer-local toggle shipped inert.

**No test has run on a real arm64 phone.** All runtime evidence is from an x86_64
emulator. See the release notes for the four known limitations: unverified arm64
execution, a failing cancel test, diffusion disabled on mobile, and estimated (not
measured) real-device TTFT.
