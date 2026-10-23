package com.grapsee.gsai.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class NexlyPlanProgressStoreTest {
    private val rows = listOf(
        "6:30 – 7:00 AM" to "Wake up + light stretch",
        "7:00 – 7:30 AM" to "Breakfast & coffee",
        "9:00 – 5:00 PM" to "Work shift"
    )

    @Test
    fun savesRestoresAndBuildsTurnContext() {
        val context = RuntimeEnvironment.getApplication()
        val conversationId = "test-${UUID.randomUUID()}"
        val fingerprint = NexlyPlanProgressStore.fingerprint("Tomorrow", rows)
        val done = setOf(
            "6:30 – 7:00 AM|Wake up + light stretch",
            "9:00 – 5:00 PM|Work shift"
        )

        NexlyPlanProgressStore.save(
            context, conversationId, fingerprint, "Tomorrow", rows, done
        )

        assertEquals(done, NexlyPlanProgressStore.load(context, conversationId, fingerprint))
        // A different plan must not restore the old checks.
        assertEquals(
            emptySet<String>(),
            NexlyPlanProgressStore.load(context, conversationId, "different-plan")
        )

        val turnContext = NexlyPlanProgressStore.contextFor(context, conversationId)
        assertTrue(turnContext != null)
        assertTrue(turnContext!!.contains("Completed (2/3)"))
        assertTrue(turnContext.contains("Wake up + light stretch"))
        assertTrue(turnContext.contains("Pending (1)"))
        assertTrue(turnContext.contains("Breakfast & coffee"))
        assertNull(NexlyPlanProgressStore.contextFor(context, "no-such-conversation"))
    }

    @Test
    fun emptyChecklistProducesNoContext() {
        val context = RuntimeEnvironment.getApplication()
        val conversationId = "test-${UUID.randomUUID()}"
        val fingerprint = NexlyPlanProgressStore.fingerprint("Tomorrow", rows)

        NexlyPlanProgressStore.save(
            context, conversationId, fingerprint, "Tomorrow", rows, emptySet()
        )

        assertNull(NexlyPlanProgressStore.contextFor(context, conversationId))
    }

    @Test
    fun preferenceAloneProducesContext() {
        val context = RuntimeEnvironment.getApplication()
        val conversationId = "test-${UUID.randomUUID()}"
        val fingerprint = NexlyPlanProgressStore.fingerprint("Tomorrow", rows)

        NexlyPlanProgressStore.save(
            context, conversationId, fingerprint, "Tomorrow", rows, emptySet(), preference = "up"
        )

        assertEquals(
            "up",
            NexlyPlanProgressStore.loadPreference(context, conversationId, fingerprint)
        )
        val turnContext = NexlyPlanProgressStore.contextFor(context, conversationId)
        assertTrue(turnContext != null)
        assertTrue(turnContext!!.contains("marked helpful"))
    }
}
