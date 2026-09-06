# GS AI (com.grapsee.gsai) — ProGuard / R8 rules
#
# Header only for now: release builds currently ship with minification
# disabled (isMinifyEnabled = false in app/build.gradle.kts).
#
# When R8 is enabled, add keep rules here for reflection-dependent code
# (Hilt-generated components, Room entities/DAOs, kotlinx.serialization, Ktor).
