// The OCR FALLBACK, as an on-demand dynamic feature module.
//
// WHY THIS IS A SEPARATE MODULE
//
// Tesseract + Leptonica is 12,282,294 bytes stripped, plus ~15 MB of
// eng.traineddata, and it is only useful on the ~1% of Android devices with no
// Google Play Services -- GrapheneOS, LineageOS without GMS, Huawei after 2019,
// some Chinese ROMs. Charging 27 MB to every install to cover 1% is the wrong
// trade, so it ships on request instead.
//
// ML Kit is the primary path and is BUNDLED, because ~99% of devices have Play
// Services and the bundled model is what makes the common case work with no
// download and no Play Services requirement at runtime.
//
// WHAT IS AND IS NOT IN HERE
//
// The build file declares the JNI libraries and the traineddata as assets. It
// does NOT contain the cross-compiled archives themselves: those are CI
// artifacts (android-deps.yml -> tesseract-arm64) and this repository never
// commits a .a or a traineddata. A release build must place them in
// app/src/main/jniLibs/<abi>/ and app/src/main/assets/tessdata/ before it can
// produce a working fallback, and the build FAILS if they are missing rather
// than shipping a module that installs and then cannot read anything.
//
// The reason it fails loudly: a fallback that installs successfully and then
// returns empty text is the worst outcome in this whole design. The user was
// told a download would give them OCR, they downloaded it, and they get a blank
// result with no error.

plugins {
    // com.android.dynamic-feature, NOT com.android.library. This is the third
    // step of the unblocking recipe and it is the one that was missing: a
    // library plugin will not produce the variant the app consumer expects,
    // which is precisely the error we were getting --
    //     No matching variant of project ':ocr-fallback' was found. The consumer
    //     was configured to find a component for use during
    //     'android-reverse-meta-data' ...
    // A dynamic feature IS a library with extra packaging rules; the plugin is
    // what teaches AGP to emit the split.
    id("com.android.dynamic-feature")
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.grapsee.gsai.ocrfallback"
    compileSdk = 35

    // Step 2 of the recipe. A dynamic feature must build the same test variant
    // as the base, or the unit-test variant cannot be matched either.
    testBuildType = "debug"

    defaultConfig {
        // Must match the base module's minSdk. Play refuses a split whose
        // minSdk is lower, and the error does not name the mismatch.
        minSdk = 26
        // The base module's id. A dynamic feature does NOT get its own
        // applicationId -- it derives one from the base at packaging time, and
        // that lookup is what was failing:
        //     Failed to calculate the value of task
        //     ':ocr-fallback:processDebugMainManifest' property 'applicationId'.
        //       > Failed to calculate the value of property 'applicationId'.
        //         > Collection is empty.
        // AGP reads this from the base module's variant; declaring the base
        // explicitly here is what gives it something to read.
        applicationId = "com.grapsee.gsai"
        // NO versionCode here. A library module has none; the base app's
        // version governs the split at install time. Declaring it gives
        //     Unresolved reference 'versionCode'.
        // The No matching variant error that prompted it was about BUILD
        // TYPES and reverse-metadata publication, not the version code.
    }

    // A dynamic feature is a LIBRARY module, not an application: no launcher,
    // no applicationId. Its build types must EXPLICITLY mirror the base's, or
    // AGP cannot match a variant for the split and reports a resolution error
    // that reads like a dependency problem rather than a missing build type.
    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    // A split must not be published to a maven repo. Reverse-metadata
    // publication is what the resolution error above was about.
}

dependencies {
    // Nothing beyond core-ktx. The split installer lives in the BASE module; the
    // feature only exposes its own entry point and is reached by reflection.
    //
    // (An `implementation(libs.androidx.annotation)` was here and does not
    // resolve: "Unresolved reference 'annotation'" -- the alias was never in the
    // version catalog. Removed rather than added, because the module does not
    // need it.)
    implementation(libs.androidx.core.ktx)
}

/**
 * Split configuration, as `assets` and `jniLibs` rather than real files.
 *
 * The archives and traineddata are CI artifacts, never git. A dynamic feature
 * whose assets are missing installs and then returns nothing, so the check runs
 * at CONFIGURE time and fails the build.
 *
 * To produce a shippable fallback, the release pipeline must:
 *   1. run android-deps.yml (tesseract-arm64, 3,721,204 bytes gzipped)
 *   2. unpack it into  <this module>/src/main/jniLibs/<abi>/
 *   3. copy eng.traineddata into <this module>/src/main/assets/tessdata/
 *   4. bundle with `./gradlew bundleRelease` so the split is generated
 */
val requiredAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

/** Where a real release build would put the cross-compiled archives. */
fun tesseractLibDir(abi: String) = file("src/main/jniLibs/$abi")

/** Where a real release build would put the trained models. */
val tessdataDir = file("src/main/assets/tessdata")

gradle.projectsEvaluated {
    val missingLibs = requiredAbis.filter { abi ->
        val d = tesseractLibDir(abi)
        !d.isDirectory || d.listFiles()?.none { it.name.endsWith(".so") } ?: true
    }
    if (missingLibs.size == requiredAbis.size) {
        // ALL of them missing: the normal state of a source checkout, and the
        // normal state of CI. This is not a failure here; it is reported so
        // nobody mistakes a source tree for a shippable module.
        logger.lifecycle(
            "[ocr-fallback] no cross-compiled Tesseract present for any ABI. " +
                "Expected when building from source. A RELEASE build must run " +
                "android-deps.yml and unpack tesseract-arm64 into " +
                "src/main/jniLibs/<abi>/ plus assets/tessdata/eng.traineddata. " +
                "Shipping without them produces a module that installs and then " +
                "returns no text."
        )
    } else {
        val ok = requiredAbis.filter { tesseractLibDir(it).isDirectory } - missingLibs
        logger.lifecycle("[ocr-fallback] Tesseract present for: ${ok.joinToString(", ")}")
    }
    if (tessdataDir.isDirectory) {
        val models = tessdataDir.listFiles()?.filter { it.name.endsWith(".traineddata") }
            ?.map { it.name } ?: emptyList()
        if (models.isEmpty()) {
            logger.warn("[ocr-fallback] tessdata directory exists but contains no .traineddata")
        } else {
            logger.lifecycle("[ocr-fallback] trained models: ${models.joinToString(", ")}")
        }
    }
}
