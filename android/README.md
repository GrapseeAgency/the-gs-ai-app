# GS AI — Android (native)

Native Android client for **the-gs-ai-app**. Package: `com.grapsee.gsai`.

This module is the project scaffold for the agreed native Android stack
(Kotlin + Jetpack Compose + Hilt). It currently ships a static seed of the
HOME "AI command centre" screen — no networking, storage, or build-time
codegen beyond Hilt/KSP.

## Structure

```
android/
├── settings.gradle.kts            # rootProject "the-gs-ai-app", includes :app
├── build.gradle.kts               # plugin aliases (apply false)
├── gradle.properties              # JVM args, parallel/caching, AndroidX flags
├── gradle/
│   ├── libs.versions.toml         # pinned version catalog (single source of truth)
│   └── wrapper/
│       └── gradle-wrapper.properties  # Gradle 8.9
└── app/
    ├── build.gradle.kts           # com.grapsee.gsai, compileSdk 35, minSdk 26
    ├── proguard-rules.pro         # header only (minify disabled for now)
    └── src/main/
        ├── AndroidManifest.xml    # GSApplication + MainActivity, INTERNET
        ├── java/com/grapsee/gsai/
        │   ├── GSApplication.kt   # @HiltAndroidApp
        │   ├── MainActivity.kt    # @AndroidEntryPoint, Compose host
        │   ├── ui/home/HomeScreen.kt  # static AI command centre seed
        │   └── ui/theme/          # Color.kt / Type.kt / Theme.kt
        └── res/values/            # strings.xml / themes.xml
```

## Stack

- Kotlin **2.0.20** on the JDK **17** toolchain
- Jetpack Compose — BOM **2024.09.03** (Material 3 + material-icons-extended)
- Android Gradle Plugin **8.5.2**, Gradle wrapper **8.9**
- Hilt **2.52** wired via KSP (`2.0.20-1.0.25`), hilt-navigation-compose **1.2.0**
- Navigation Compose **2.8.1**, activity-compose **1.9.2**
- core-ktx **1.13.1**, lifecycle **2.8.6**, coroutines **1.9.0**
- minSdk **26** · targetSdk/compileSdk **35** · versionName **0.1.0**

## Building

The Gradle wrapper binaries (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`)
are intentionally **not committed**. CI (GitHub Actions) bootstraps the
wrapper — e.g. a Gradle install/`gradle/actions/setup-gradle` step followed
by `gradle wrapper --gradle-version 8.9` — and then runs
`./gradlew :app:assembleDebug` on every push.

Local builds need:

- JDK 17+
- Android SDK with platform **35** (plus Build-Tools compatible with AGP 8.5)
- `ANDROID_HOME` set (or `local.properties` pointing at the SDK)

```bash
gradle wrapper --gradle-version 8.9   # one-time local bootstrap
./gradlew :app:assembleDebug
```

## Roadmap notes

- **Room** (SQLite + FTS5 full-text search) arrives with the first
  data-backed feature (chat history / library), per the agreed stack — it is
  deliberately absent from this scaffold.
- Later layers: Ktor networking, WorkManager jobs, Android Keystore-secured
  storage, and the C++ core engine over JNI.
