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
        versionCode = 17
        versionName = "0.17.0"

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
}
