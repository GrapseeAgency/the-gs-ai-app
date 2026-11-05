package com.grapsee.gsai.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The personalization page edits ONE list at a time, and that is the whole
 * point of these tests.
 *
 * The wizard's `save()` rewrites all four fields. Reusing it from a settings
 * page would mean that changing an interest silently resets the tone and the
 * pinned tools — the kind of quiet data loss a screenshot cannot show and a
 * reader would blame on the app "forgetting".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfilePreferencesStoreTest {

    private lateinit var ctx: Context

    @Before
    fun reset() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("gs_profile", Context.MODE_PRIVATE).edit().clear().commit()
        OnboardingProfileStore.init(ctx)
    }

    @Test
    fun editingInterestsLeavesTheOtherThreeAnswersAlone() {
        OnboardingProfileStore.save(
            interests = setOf("Writing", "Travel"),
            capabilities = setOf("Draft and write"),
            responseStyle = "Detailed",
            personality = "Playful",
        )

        OnboardingProfileStore.setInterests(setOf("Coding"))

        assertEquals(setOf("Coding"), OnboardingProfileStore.interests)
        assertEquals(setOf("Draft and write"), OnboardingProfileStore.capabilities)
        assertEquals("Detailed", OnboardingProfileStore.responseStyle)
        assertEquals("Playful", OnboardingProfileStore.personality)
    }

    @Test
    fun editingCapabilitiesLeavesTheOtherThreeAnswersAlone() {
        OnboardingProfileStore.save(
            interests = setOf("Design"),
            capabilities = setOf("Analyse images"),
            responseStyle = "Concise",
            personality = "Professional",
        )

        OnboardingProfileStore.setCapabilities(setOf("Write and fix code", "Brainstorm ideas"))

        assertEquals(setOf("Design"), OnboardingProfileStore.interests)
        assertEquals(setOf("Write and fix code", "Brainstorm ideas"), OnboardingProfileStore.capabilities)
        assertEquals("Concise", OnboardingProfileStore.responseStyle)
        assertEquals("Professional", OnboardingProfileStore.personality)
    }

    @Test
    fun theEditSurvivesARestart() {
        OnboardingProfileStore.save(
            interests = setOf("Writing"),
            capabilities = setOf("Draft and write"),
            responseStyle = "Standard",
            personality = "Friendly",
        )
        OnboardingProfileStore.setInterests(setOf("Science", "Data"))

        // A fresh read of the same prefs file — the page's write must be on
        // disk, not only in the in-memory state object.
        OnboardingProfileStore.init(ctx)

        assertEquals(setOf("Science", "Data"), OnboardingProfileStore.interests)
        assertEquals(setOf("Draft and write"), OnboardingProfileStore.capabilities)
    }

    @Test
    fun clearingEverythingStillResetsAllFour() {
        OnboardingProfileStore.save(
            interests = setOf("Writing"),
            capabilities = setOf("Draft and write"),
            responseStyle = "Detailed",
            personality = "Playful",
        )

        OnboardingProfileStore.clear()

        assertEquals(emptySet<String>(), OnboardingProfileStore.interests)
        assertEquals(emptySet<String>(), OnboardingProfileStore.capabilities)
        assertEquals("Standard", OnboardingProfileStore.responseStyle)
        assertEquals("Friendly", OnboardingProfileStore.personality)
    }
}
