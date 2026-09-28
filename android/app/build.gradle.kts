plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.grapsee.gsai"
    compileSdk = 35

    // Decision 1: the OCR fallback is an ON-DEMAND dynamic feature, so the base
    // APK pays 0 bytes for Tesseract. 12.3 MB of static libraries plus ~15 MB of
    // traineddata for a segment that is ~1% of installs is the wrong trade in
    // the base APK and a reasonable one on request.
    //
    // This belongs at the `android { }` level. Inside `defaultConfig` it is a
    // SCRIPT COMPILATION error -- "'val' cannot be reassigned" -- which reads
    // like nothing to do with dynamic features at all.
    dynamicFeatures = [":ocr-fallback"]

    defaultConfig {
        applicationId = "com.grapsee.gsai"
        minSdk = 26
        targetSdk = 35
        versionCode = 73
        versionName = "0.68.2"


        // REAL transport origin — applies to EVERY build type (v0.66.0 fix).
        // HISTORY: this was the Android-emulator host-loopback alias (10.0 dot
        // 2 dot, unroutable from any real phone) with the reachable origin only
        // in the `release` block — but the shipped APKs
        // are assembleDebug, so EVERY phone release pointed at a dead emulator
        // address. Sends failed instantly and GS Lite silently answered with
        // local canned replies — the "unnecessary answers" audit finding. The
        // public preview-gateway origin sits on a globally routable ALB
        // (47.239.x.x), complete TLS chain; uploads verified 201 at 200 KB /
        // 400 KB / 1 MB / chunked / PDF / text. The x-session-id header
        // (ServiceLocator) stays: harmless here, required elsewhere.
        buildConfigField(
            "String",
            "BASE_URL",
            "\"https://preview-chat-c945696f-6447-4dfa-b510-971d8b9eb5bf.space-z.ai\""
        )
    }

    /**
     * GS LiveUpdate signing — the committed gs-live.keystore gives EVERY build
     * (debug and release, this machine or any fresh sandbox) the same signature,
     * so in-app updates install straight over the installed app.
     */
    signingConfigs {
        create("liveUpdate") {
            storeFile = file("gs-live.keystore")
            storePassword = "gsai-live-update"
            keyAlias = "gsai"
            keyPassword = "gsai-live-update"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("liveUpdate")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("liveUpdate")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    // Decision 1. ML Kit is the PRIMARY OCR path and is bundled, because
    // ~99% of Android devices have Play Services. The fallback is NOT here: it
    // lives in the :ocr-fallback dynamic feature and is installed on request.
    implementation(libs.mlkit.text.recognition)
    implementation(libs.play.core)
    implementation(libs.play.core.ktx)
    // Availability CHECK only. No Play Services feature module, so nothing here
    // downloads anything or requires Play Services to function.
    implementation(libs.play.services.base)
    // Baseline-profile installer: on API 26-28 devices the merged library
    // profile (Compose/Room/Lifecycle ship one in each AAR) is installed at
    // first run by this artifact; API 29+ installs it at package time.
    // Startup AOT coverage without any hand-tuned rules.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // PHASE 8.2 (Task 8.2-a): source-tile favicon attempt. Coil's
    // SubcomposeAsyncImage fetches icons.duckduckgo.com/ip3/<domain>.ico
    // asynchronously (never the main thread); the monogram tile remains the
    // placeholder AND error fallback. The ONE new dependency of this task.
    implementation(libs.coil.compose)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)

    implementation(libs.kotlinx.coroutines.android)

    // Chat data layer (Task 6-b): Ktor client + kotlinx.serialization + Room
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // JVM unit tests (parser correctness + incrementality). Test-only —
    // nothing here ships in either APK.
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.ktor:ktor-client-mock:2.3.12")
}
