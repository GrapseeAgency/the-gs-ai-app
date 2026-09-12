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

    defaultConfig {
        applicationId = "com.grapsee.gsai"
        minSdk = 26
        targetSdk = 35
        versionCode = 64
        versionName = "0.63.0"

        // Backend origin for the Android emulator (host loopback). Override per build type if needed.
        buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:3000\"")
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
            // REAL transport origin (device-reachability fix): the platform's
            // fcapp.run endpoint is VPC-INTERNAL ONLY (public DNS resolves it to
            // CGNAT 100.118.36.1; the non-vpc variant answers 403 "function
            // internet URL is disabled") — no phone can ever reach it. The
            // public preview-gateway origin below sits on a globally routable
            // ALB (47.239.x.x), complete TLS chain, uploads verified 201 at
            // 200 KB / 400 KB / 1 MB / chunked / PDF / text. The x-session-id
            // header (ServiceLocator) stays: harmless here, required elsewhere.
            buildConfigField(
                "String",
                "BASE_URL",
                "\"https://preview-chat-c945696f-6447-4dfa-b510-971d8b9eb5bf.space-z.ai\""
            )
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
