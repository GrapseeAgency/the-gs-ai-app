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
    // Decision 1: RECONNECTED. The module now uses com.android.dynamic-feature
    // (it was com.android.library, which is why the variant never matched) and
    // the publishing block is deleted rather than narrowed. See the history on
    // this file for the four attempts that preceded it.
    //
    // If the app build breaks here, comment this line out again: a split that
    // will not resolve takes the WHOLE app build down with it.
    //
    // BLOCKED after four attempts on the :ocr-fallback module, every one caught
    // by android-app.yml within about 90 seconds of being pushed:
    //
    //   1. dynamicFeatures inside defaultConfig -> 'val' cannot be reassigned
    //   2. = arrayOf(...) -> actual type is 'Array<String>', but
    //      'MutableSet<String>' was expected
    //   3. = mutableSetOf(...) -> 'val' cannot be reassigned
    //   4. com.android.library requested a version while AGP was already on the
    //      classpath -> Error resolving plugin
    // and then, once it did resolve:
    //
    //     Could not determine the dependencies of task ':app:checkDebugLibraries'.
    //     > Could not resolve project ':ocr-fallback'.
    //       > No matching variant of project ':ocr-fallback' was found. The
    //         consumer was configured to find a component for use during
    //         'android-reverse-meta-data' ... BuildTypeAttr with value 'debug'
    //         but:
    //           - Variant 'debugApiElements' declares a component ...
    //
    // The Kotlin DSL line itself is correct. The module resolves to a variant
    // AGP will not match, and leaving it enabled took the WHOLE app build down,
    // which is worse than not having the module at all.
    //
    // Everything the module needs is in the tree and unused:
    //   android/ocr-fallback/build.gradle.kts
    //   android/app/src/main/java/com/grapsee/gsai/ocr/OcrEngine.kt
    //
    // UNBLOCKING IT: the failure is about reverse-metadata PUBLICATION, and a
    // split must not be a publishable variant. Next thing to try, in order:
    //   a. delete the publishing { singleVariant(...) } block from
    //      android/ocr-fallback/build.gradle.kts entirely rather than narrowing
    //      it to one variant
    //   b. give the module the same testBuildType and build types as the base
    //   c. if it still will not match, build the split with `bundleRelease`
    //      rather than through the debug variant, since reverse-metadata is a
    //      publication concern and a release bundle is the only place a split
    //      actually matters
    //
    // dynamicFeatures is COMMENTED OUT. The module now builds under
    // com.android.dynamic-feature -- the 'No matching variant' error is gone --
    // and the remaining failure is inside the module's manifest task:
    //     Failed to calculate the value of task
    //     ':ocr-fallback:processDebugMainManifest' property 'applicationId'.
    //       > Failed to calculate the value of property 'applicationId'.
    //         > Collection is empty.
    // and declaring applicationId in the module is not the answer:
    //     Line 65:         applicationId = "com.grapsee.gsai"
    //                            ^ Unresolved reference 'applicationId'.
    // A split must not have one, so AGP is looking the id up on the BASE
    // module's variant and not finding it. Attempts 1 and 2 of the 4 allowed
    // are recorded; the app build is worth more than the module, so this is
    // left off until the base-variant lookup is understood.
    //
    // STILL OFF after the fifth attempt. Run 36764251868:
    //
    //   Failed to calculate the value of property 'applicationId'.
    //   > Collection is empty.
    //   at com.android.build.api.variant.impl.DynamicFeatureVariantImpl
    //        $instantiateBaseModuleMetadata$1.transform(DynamicFeatureVariantImpl.kt:244)
    //
    // The manifest and the dependencies were the right two fixes and both STAY --
    // they were genuinely missing, and the module cannot compile without them. The
    // split is wired in now, which the log proves: :ocr-fallback:generateDebugFeature
    // TransitiveDeps is in the task graph. So the split is no longer failing to
    // resolve; it is failing inside AGP's own base-metadata lookup, and every
    // configuration axis I can check from here already agrees:
    //
    //   dynamicFeatures      declared, and effective (its tasks are in the graph)
    //   split manifest       present, with <dist:module>, on-demand, fusing
    //   split dependencies   ML Kit + feature-delivery + gms.tasks, all in the
    //                        catalog and all already used by the base
    //   buildTypes           debug and release on BOTH modules, no flavors anywhere
    //   minSdk               26 on both, which is the other thing Play refuses over
    //   applicationId        set on the base, absent on the split (correct)
    //   versionCode          73 on the base
    //   namespace            com.grapsee.gsai.ocrfallback, a subpackage of the id
    //
    // ================================================================
    // PERMANENTLY OFF, and a version bump is measurably not the fix.
    // ================================================================
    //
    // WHY NOT BUMP AGP. The failure is a `.single()` on an empty collection, so
    // the only question that matters is whether any release changed that line. The
    // sources jar for each version was fetched from Google's Maven and the
    // function read, rather than a changelog sentence being taken on trust:
    //
    //     8.5.2   .single()=1   artifact.elements.map { ModuleMetadata.load(it.single().asFile) })
    //     8.7.3   .single()=1   (identical)
    //     8.9.2   .single()=1   (identical)
    //     8.11.1  .single()=1   (identical)
    //     8.12.3  .single()=1   (identical)
    //     8.13.2  .single()=1   (identical)
    //
    // Byte-identical across six releases. The code that throws is the same code in
    // all of them, so no version fixes THIS failure. A bump could only help if a
    // later AGP registered the producer task differently, which I could not find
    // in the `gradle` artifact and could not settle without six blind 25-minute
    // runs -- so that is not claimed either way.
    //
    // THE EXACT BUG, for anyone with a newer AGP to try:
    //
    //     Failed to calculate the value of property 'applicationId'.
    //     > Collection is empty.
    //     at com.android.build.api.variant.impl.DynamicFeatureVariantImpl
    //          $instantiateBaseModuleMetadata$1.transform(DynamicFeatureVariantImpl.kt:244)
    //
    //     private fun instantiateBaseModuleMetadata(...) = artifact.elements.map {
    //         ModuleMetadata.load(it.single().asFile) }
    //
    // The base module never registers its `write<Variant>BaseModuleMetadata`
    // producer, so the split's compile classpath has no BASE_MODULE_METADATA
    // artifact and `.single()` throws.
    //
    // WHAT IS IN PLACE AND STAYS. The module is no longer a sketch:
    //   * android/ocr-fallback/src/main/AndroidManifest.xml, with <dist:module
    //     dist:instant="false">, <dist:on-demand/>, dist:fusing include="true",
    //     and no applicationId -- it did not exist at all, and its absence was
    //     this same error
    //   * its dependencies, ML Kit and feature-delivery, which it also lacked and
    //     could not have compiled without
    //   * minSdk 26 on both modules, :ocr-fallback in settings.gradle.kts
    // Those are correct and are not reverted by leaving the line off. Only the
    // base's registration is missing, and that is AGP's half.
    //
    // THE OPERATOR'S WORDING, verbatim so it cannot be paraphrased into optimism:
    //
    //     OCR fallback on de-Googled devices requires a manual build with the
    //     module enabled until AGP fixes the metadata producer.
    //
    // THE ENGINEERING CALL, which is separate from the evidence. The fallback is a
    // CONVENIENCE for devices with no Google Play Services -- GrapheneOS,
    // de-Googled LineageOS, Huawei after 2019. It is not the primary path: ML Kit
    // is bundled and is what ~99% of devices use, and a2_ocr_reads_the_fixture
    // passes on the shipping configuration. So leaving the split off costs a
    // feature on ~1% of devices, and enabling it costs an APK that does not build.
    //
    // DO NOT RETRY BY CHANGING THE SPLIT. Five attempts are recorded above and four
    // of them were the split's configuration. The remaining question is the base
    // module's variant not seeing `dynamicFeatures` at all, and the evidence for
    // that is the ABSENCE of the producer task in the graph -- not another error
    // message, which is what four of the five attempts chased.
    // dynamicFeatures += setOf(":ocr-fallback")

    defaultConfig {
        applicationId = "com.grapsee.gsai"

        // THE ROOT CAUSE OF "0 TESTS RAN". This was absent, so the androidTest
        // APK shipped with no instrumentation runner, the runner class could not
        // be found, and discovery returned an empty set. Raw, run 36411916910,
        // which was GREEN end to end and proved nothing:
        //     Starting 0 tests on emulator-5554 - 11
        //     BUILD SUCCESSFUL in 11m 47s
        //     connectedDebugAndroidTest exit: 0
        // A green run in which no test executed is the single worst outcome
        // here, because it is indistinguishable from a passing run at a glance.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        minSdk = 26
        targetSdk = 35
        versionCode = 76
        versionName = "0.71.2"


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

    // ANDROIDTEST. These were ABSENT, so the entire instrumented-test source set
    // did not compile:
    //     .kt:10:12 Unresolved reference 'junit'.
    //     .kt:109:6  Unresolved reference 'Test'.
    //     .kt:111:9  Unresolved reference 'assumeTrue'.
    //     .kt:101:9  Unresolved reference 'assertNotNull'.
    // Raw, run 36409455281, which cost a 5-minute emulator build to find out.
    //
    // It went unnoticed because android-app.yml compiled only the MAIN source
    // set (`:app:compileDebugKotlin`), so an androidTest file that could not
    // compile looked fine until the one job that does compile it ran. The
    // workflow now compiles it too.
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    // Blocker 4: ChatUiReplyTest drives the composer with Compose UI test.
    // The source set carried Espresso + JUnit but NOT the Compose test rule,
    // so a real UI assertion could not compile. One BOM platform line + the
    // junit4 rule, plus the debug manifest for createAndroidComposeRule.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("io.ktor:ktor-client-mock:2.3.12")
}
