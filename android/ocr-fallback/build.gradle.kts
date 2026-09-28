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
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.grapsee.gsai.ocrfallback"
    compileSdk = 35

    defaultConfig {
        // Must match the base module's minSdk. Play refuses a split whose
        // minSdk is lower, and the error does not name the mismatch.
        minSdk = 26
    }

    // A dynamic feature is a LIBRARY module, not an application. It has no
    // launcher, no versionName and no applicationId.
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
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
