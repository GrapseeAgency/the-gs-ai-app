# GS AI (com.grapsee.gsai) — ProGuard / R8 rules
#
# R8 is ON for release as of v0.77.0 (perf-smooth-5). It was off before, and
# the shipped APK was a debug build on top of that — see the comment on
# buildTypes.release in app/build.gradle.kts for the evidence.
#
# Obfuscation is deliberately NOT enabled. Two reasons, both non-negotiable:
#   1. CrashReporter ships stack traces to the operator and they are read by
#      humans; mangled names would make the only diagnostic channel useless.
#   2. libgs_ffi.so resolves JNI entry points by class+method name, so the
#      names must survive regardless.
# Shrinking and optimization are where the frame time is. Names are not.

-dontobfuscate

# Annotations, signatures and LINE NUMBERS must survive: the crash reporter
# and the baseline-profile pipeline both read them.
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature,SourceFile,LineNumberTable,MethodParameters,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# ---------------------------------------------------------------------------
# JNI — libgs_ffi.so looks these up by symbol name. Removal or rename here is
# an UnsatisfiedLinkError at first inference, not a build failure, so this is
# the one block that must never be "cleaned up".
# ---------------------------------------------------------------------------
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class com.grapsee.gsai.native.** { *; }
-keep class com.grapsee.gsai.GsNativeException { *; }

# Keep every app entry point that is referenced by name from outside Kotlin
# (manifest, Hilt, tests). Cheap: a few hundred classes, and it removes an
# entire class of "works in debug, dies in release" failures.
-keep class com.grapsee.gsai.GSApplication { *; }
-keep class com.grapsee.gsai.MainActivity { *; }
-keep class com.grapsee.gsai.CrashReporter { *; }

# ---------------------------------------------------------------------------
# kotlinx.serialization — the generated `$serializer` companions are reached
# reflectively by the plugin's runtime. The library ships consumer rules, but
# R8 full mode needs these to survive shrinking of the companion itself.
# ---------------------------------------------------------------------------
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.grapsee.gsai.**$$serializer { *; }
-keepclassmembers class com.grapsee.gsai.** {
    *** Companion;
}
-keepclasseswithmembers class com.grapsee.gsai.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------------------------------------------------------------------------
# Room — entities and DAOs are referenced by generated code, and the schema
# verifier reads field names at runtime.
# ---------------------------------------------------------------------------
-keep class com.grapsee.gsai.data.local.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# ---------------------------------------------------------------------------
# Hilt / Dagger — generated components are instantiated by name.
# ---------------------------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ---------------------------------------------------------------------------
# Optional dependencies that R8 warns about but the app never reaches.
# ---------------------------------------------------------------------------
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.codehaus.mojo.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn kotlinx.coroutines.debug.**
-dontwarn java.lang.management.**
-dontwarn javax.naming.**
