// perf-4-b — chat-screen Macrobenchmark harness.
// Standalone com.android.test module targeting :app release (the liveUpdate
// keystore signs every build, so the release target installs on any emulator).
// Run locally:  ./gradlew :benchmark:connectedCheck
// Run on CI:    .github/workflows/android-benchmark.yml (API 34 emulator).
plugins {
    alias(libs.plugins.android.test.apk)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.grapsee.gsai.benchmark"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.benchmark.macro.junit4.MacrobenchmarkRunner"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    // perf-4-b: testBuildType is a val on the AGP 8.13 TestExtension DSL, so
    // the release-only shape is expressed through androidComponents instead:
    // the debug variant is disabled outright and connectedCheck runs the
    // release variant against the :app release target (non-debuggable, as
    // Macrobenchmark requires).
    buildTypes {
        // test modules ship NO default release buildType (run 38709697422:
        // CommonExtensionImpl.release getByName fails) — create it.
        create("release") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

androidComponents {
    beforeVariants(selector().all()) { variant ->
        variant.enable = variant.buildType == "release"
    }
}

dependencies {
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.3")
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
