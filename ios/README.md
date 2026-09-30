# GSApp — iOS (the-gs-ai-app)

Native iOS client for the GS AI platform. Swift + SwiftUI, iOS 16.0+.

## Structure

```
ios/
├── project.yml                          # XcodeGen spec (source of truth)
├── App/
│   ├── Sources/
│   │   ├── GSApp.swift                  # @main SwiftUI entry point
│   │   ├── Theme/DesignSystem.swift     # "Premium intelligent editorial" tokens
│   │   └── Features/Home/HomeView.swift # AI command centre (seed)
│   └── Tests/AppTests/                  # XCTest unit tests
└── .gitignore                           # generated .xcodeproj etc. kept out of git
```

## Workflow (XcodeGen)

The `.xcodeproj` is **generated, never committed or hand-edited**:

```bash
brew install xcodegen
cd ios
xcodegen generate
open GSApp.xcodeproj
```

## CI

GitHub Actions builds/tests this target on **macOS runners** with
`xcodebuild ... CODE_SIGNING_ALLOWED=NO` (signing is also disabled in
`project.yml` so headless CI runs need no certificates or team ID).

## The native engine (`GsFfi.xcframework`)

`project.yml` links `Frameworks/GsFfi.xcframework` into **both** `App` and
`AppTests`. The directory is not in git — the framework is a compiled binary and
this repo carries source only — so it is downloaded from the `ios-native`
workflow before the project is generated:

```bash
# 1. build the framework (produces dist/ios/GsFfi.xcframework)
#    ... or download the GsFfi.xcframework artifact from an ios-native run
# 2. place it where project.yml expects it
mkdir -p ios/Frameworks
cp -R dist/ios/GsFfi.xcframework ios/Frameworks/

# 3. now the normal workflow
cd ios && xcodegen generate && open GSApp.xcodeproj
```

Without it the app still builds and runs — `GsNativeLoader` reports
`libraryMissing` with a reason, which is a supported configuration — but the
engine cannot be used, and the five engine-backed XCTests skip.

### What is and is not in the iOS build

| | |
|---|---|
| framework built, device + simulator slices | yes |
| simulator slice architectures | `arm64`, `x86_64` |
| framework linked into `App` and `AppTests` | yes |
| `libc++` linked | yes (`-lc++ -ObjC++`) |
| `GS_LLAMA_PREBUILT` set | **no** — Android only |

That last row is why `GsNativeLoader.isAvailable` is false in a CI iOS build and
`gs_mobile_backend_available` returns `0`: with no llama.cpp compiled in, that
function is `return 0` by design (`native/cpp/mobile/gs_mobile.cpp:91`). The five
engine-backed tests skip **honestly** on that condition rather than passing.

Closing it means cross-compiling llama.cpp for `aarch64-apple-ios` and
`aarch64-apple-ios-sim`, the same job `android-deps` does for Android.

## Stack

- Swift 5.9, SwiftUI (iOS 16.0+)
- MVVM / Clean Architecture (agreed in worklog Task 2)
- SPM-ready — a `Package.swift` can be added alongside when the shared Ktor layer lands
- XCTest for unit tests (target `AppTests`, scheme `GSApp`)

## Roadmap

Next: SwiftData persistence and the first real features (chat + streaming,
memory) per the product blueprint in worklog Task 4.
