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

## Stack

- Swift 5.9, SwiftUI (iOS 16.0+)
- MVVM / Clean Architecture (agreed in worklog Task 2)
- SPM-ready — a `Package.swift` can be added alongside when the shared Ktor layer lands
- XCTest for unit tests (target `AppTests`, scheme `GSApp`)

## Roadmap

Next: SwiftData persistence and the first real features (chat + streaming,
memory) per the product blueprint in worklog Task 4.
