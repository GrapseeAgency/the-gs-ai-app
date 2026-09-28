pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "the-gs-ai-app"
include(":app")
// Decision 1: the OCR fallback is an on-demand dynamic feature, not part of the
// base APK. `dynamicFeatures = [":ocr-fallback"]` in the app module points here;
// a module listed in only one of the two places fails to configure, and the
// error names neither.
include(":ocr-fallback")
