package com.grapsee.gsai.data

import com.grapsee.gsai.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The data-retention contract behind Data Export & Account Deletion.
 *
 * The reference draws a retention period and an auto-delete switch; both are
 * stored in [SettingsStore], and this object is their ONLY reader. That is the
 * rule the settings store's HONESTY CONTRACT asks for: a control is live only
 * when some runtime path obeys it.
 *
 * WHAT IT DELETES: a conversation whose last turn (`updatedAt`) is older than
 * the chosen period, together with its messages. Saved Library items are not
 * conversations and stay. Pinned/archived chats are not exempt — "inactive"
 * is measured by the last turn, and the page's subtitle says so.
 */
object DataRetention {

    /** Same fixed-width UTC shape the repository writes, so lexicographic
     *  comparison in Room is chronological. */
    private val CUTOFF_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            .withZone(ZoneOffset.UTC)

    /** ISO cutoff for `months` months before [now]. */
    fun cutoffIso(months: Int, now: Instant = Instant.now()): String =
        CUTOFF_FORMAT.format(
            now.atZone(ZoneOffset.UTC)
                .minusMonths(months.coerceAtLeast(1).toLong())
        )

    /**
     * Delete conversations untouched for longer than the stored period.
     * Returns how many conversations left the device; zero when the switch is
     * off or nothing is old enough.
     */
    suspend fun purgeExpired(now: Instant = Instant.now()): Int =
        withContext(Dispatchers.IO) {
            if (!SettingsStore.autoDeleteInactive) return@withContext 0
            val db = ServiceLocator.db
            val cutoff = cutoffIso(SettingsStore.dataRetentionMonths, now)
            val ids = db.conversationDao().idsUpdatedBefore(cutoff)
            if (ids.isEmpty()) return@withContext 0
            db.messageDao().deleteForConversations(ids)
            db.conversationDao().deleteUpdatedBefore(cutoff)
            ids.size
        }

    /**
     * Clear Chat History: every conversation and message, plus staged
     * attachment copies. Library saves and the account identity stay — that is
     * the difference between this action and Delete Account.
     */
    suspend fun clearChatHistory() = withContext(Dispatchers.IO) {
        val db = ServiceLocator.db
        db.messageDao().deleteAll()
        db.conversationDao().clear()
        ServiceLocator.attachments.purgeAllStagedFiles()
    }

    /**
     * Delete Account: the whole local corpus, including Library saves and
     * staged files. Identity/session teardown belongs to the caller, which is
     * the only layer that can leave the screen.
     */
    suspend fun deleteAllContent() = withContext(Dispatchers.IO) {
        val db = ServiceLocator.db
        db.messageDao().deleteAll()
        db.savedItemDao().clear()
        db.conversationDao().clear()
        ServiceLocator.attachments.purgeAllStagedFiles()
    }
}
