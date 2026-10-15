// perf-4-b — chat-screen Macrobenchmark harness.
// Standalone com.android.test module targeting :app release (the liveUpdate
// keystore signs every build, so the release target installs on any emulator).
// Run locally:  ./gradlew :benchmark:connectedCheck
// Run on CI:    .github/workflows/android-benchmark.yml (API 34 emulator).
plugins {
    id("com.android.test") version "8.13.2"
}

android {
    namespace = "com.grapsee.gsai.benchmark"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.benchmark.macro.junit4.MacrobenchmarkRunner"
    }

    testBuildType = "release"
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        debug {
        }
        release {
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

dependencies {
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.3")
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
