package com.grapsee.gsai.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.grapsee.gsai.data.local.ConversationEntity
import com.grapsee.gsai.data.local.MessageEntity
import com.grapsee.gsai.data.local.SavedItemEntity
import com.grapsee.gsai.di.ServiceLocator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The retention controls are only honest if this file can fail: a switch that
 * stores a value but deletes nothing is the exact failure the settings store's
 * contract forbids. These tests seed real Room rows and assert what leaves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataRetentionTest {

    private val now = Instant.parse("2026-10-31T00:00:00Z")

    @Before
    fun reset() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ServiceLocator.init(ctx)
        SettingsStore.init(ctx)
        SettingsStore.updateDataRetentionMonths(12)
        SettingsStore.updateAutoDeleteInactive(false)
        // ServiceLocator is a process singleton and Robolectric may reuse its
        // database file across methods; every test starts from an empty corpus.
        runBlocking {
            val db = ServiceLocator.db
            db.messageDao().deleteAll()
            db.savedItemDao().clear()
            db.conversationDao().clear()
        }
    }

    private fun seedConversation(id: String, updatedAt: String) = runBlocking {
        val db = ServiceLocator.db
        db.conversationDao().upsert(
            ConversationEntity(
                id = id,
                title = id,
                modelId = null,
                pinned = false,
                archived = false,
                updatedAt = updatedAt,
                createdAt = updatedAt,
            )
        )
        db.messageDao().upsert(
            MessageEntity(
                id = "msg-$id",
                conversationId = id,
                role = "user",
                content = "hello from $id",
                createdAt = updatedAt,
            )
        )
        db.savedItemDao().upsert(
            SavedItemEntity(
                id = "saved-$id",
                kind = "message",
                title = "Saved $id",
                content = "kept",
                createdAt = updatedAt,
            )
        )
    }

    @Test
    fun autoDeleteOffKeepsEverything() = runBlocking {
        seedConversation("old", "2025-01-01T00:00:00.000Z")
        seedConversation("new", "2026-10-01T00:00:00.000Z")

        assertEquals(0, DataRetention.purgeExpired(now))
        val db = ServiceLocator.db
        assertNotNull(db.conversationDao().getById("old"))
        assertNotNull(db.conversationDao().getById("new"))
    }

    @Test
    fun autoDeleteRemovesOnlyConversationsOlderThanThePeriod() = runBlocking {
        seedConversation("old", "2025-01-01T00:00:00.000Z")
        seedConversation("new", "2026-10-01T00:00:00.000Z")
        SettingsStore.updateAutoDeleteInactive(true)

        assertEquals(1, DataRetention.purgeExpired(now))
        val db = ServiceLocator.db
        assertNull(db.conversationDao().getById("old"))
        assertNull(db.messageDao().byId("msg-old"))
        assertNotNull(db.conversationDao().getById("new"))
        assertNotNull(db.messageDao().byId("msg-new"))
    }

    @Test
    fun changingThePeriodChangesTheCutoff() = runBlocking {
        seedConversation("eightMonths", "2026-02-01T00:00:00.000Z")
        SettingsStore.updateAutoDeleteInactive(true)
        SettingsStore.updateDataRetentionMonths(6)

        // Six months before 2026-10-31 is 2026-04-30; the February chat is
        // older and leaves, while the same chat survives the twelve-month
        // default the other test uses.
        assertEquals(1, DataRetention.purgeExpired(now))
        assertNull(ServiceLocator.db.conversationDao().getById("eightMonths"))
    }

    @Test
    fun clearHistoryKeepsLibrarySaves() = runBlocking {
        seedConversation("old", "2025-01-01T00:00:00.000Z")
        DataRetention.clearChatHistory()

        val db = ServiceLocator.db
        assertNull(db.conversationDao().getById("old"))
        assertNull(db.messageDao().byId("msg-old"))
        // The library row is the difference between Clear History and Delete
        // Account; it must survive.
        assertEquals(1, db.savedItemDao().observeAll().first().size)
    }

    @Test
    fun deleteAllContentAlsoRemovesLibrarySaves() = runBlocking {
        seedConversation("old", "2025-01-01T00:00:00.000Z")
        DataRetention.deleteAllContent()

        val db = ServiceLocator.db
        assertNull(db.conversationDao().getById("old"))
        assertNull(db.savedItemDao().observeAll().first().firstOrNull())
    }
}
