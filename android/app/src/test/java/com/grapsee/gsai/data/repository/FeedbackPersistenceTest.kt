package com.grapsee.gsai.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.grapsee.gsai.data.local.ConversationEntity
import com.grapsee.gsai.data.local.MessageEntity
import com.grapsee.gsai.di.ServiceLocator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * WHY THIS TEST EXISTS
 *
 * The verdict on an answer ("Helpful?") is only worth asking if it is DURABLE
 * and REACHES THE MODEL. Three properties, all tested here against the real
 * Room database:
 *
 *   1. it is written on the turn itself, so a reload still shows the thumb the
 *      reader picked — a vote that forgets itself is a lie;
 *   2. the local write happens FIRST, so a tap in a tunnel is not lost while
 *      the POST fails;
 *   3. it is reversible — tapping the chosen thumb again clears the column
 *      instead of leaving a permanent accidental vote.
 *
 * The wire half is deliberately out of scope here (no server in a unit test);
 * what matters is that a failed POST never costs the reader their vote.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeedbackPersistenceTest {

    private lateinit var context: Context

    @Before
    fun reset() {
        context = ApplicationProvider.getApplicationContext()
        ServiceLocator.init(context)
        runBlocking {
            val db = ServiceLocator.db
            db.messageDao().deleteAll()
            db.conversationDao().upsert(
                ConversationEntity(
                    id = "conv-1",
                    title = "Feedback",
                    modelId = null,
                    pinned = false,
                    archived = false,
                    updatedAt = "2026-01-01T00:00:00.000Z",
                    createdAt = "2026-01-01T00:00:00.000Z"
                )
            )
            db.messageDao().insertAll(
                listOf(
                    MessageEntity(
                        id = "local-answer-1",
                        conversationId = "conv-1",
                        role = "assistant",
                        content = "The answer under review.",
                        createdAt = "2026-01-01T00:00:01.000Z"
                    )
                )
            )
        }
    }

    @Test
    fun `a verdict is stored on the turn and survives a reload`() = runBlocking {
        val ok = ServiceLocator.chat.setFeedback("local-answer-1", "down")
        assertEquals("a local-only id needs no server to be accepted", true, ok)
        assertEquals(
            "down",
            ServiceLocator.db.messageDao().byId("local-answer-1")?.feedback
        )
    }

    @Test
    fun `a verdict is reversible`() = runBlocking {
        ServiceLocator.chat.setFeedback("local-answer-1", "up")
        assertEquals("up", ServiceLocator.db.messageDao().byId("local-answer-1")?.feedback)

        ServiceLocator.chat.setFeedback("local-answer-1", null)
        assertNull(
            "withdrawing must clear the column, not leave the old vote behind",
            ServiceLocator.db.messageDao().byId("local-answer-1")?.feedback
        )
    }

    @Test
    fun `a failed sync never costs the reader their vote`() = runBlocking {
        // A server-shaped id with no server reachable: the POST cannot succeed,
        // and the local record must still be there afterwards.
        ServiceLocator.db.messageDao().insertAll(
            listOf(
                MessageEntity(
                    id = "srv-answer-9",
                    conversationId = "conv-1",
                    role = "assistant",
                    content = "Server turn.",
                    createdAt = "2026-01-01T00:00:02.000Z"
                )
            )
        )
        ServiceLocator.chat.setFeedback("srv-answer-9", "up")
        assertEquals(
            "the device is the durable record; the wire may catch up later",
            "up",
            ServiceLocator.db.messageDao().byId("srv-answer-9")?.feedback
        )
    }
}
