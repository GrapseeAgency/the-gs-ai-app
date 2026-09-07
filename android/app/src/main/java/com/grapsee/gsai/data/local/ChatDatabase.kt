package com.grapsee.gsai.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["updatedAt"]),
        Index(value = ["archived", "pinned", "updatedAt"])
    ]
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val modelId: String?,
    val pinned: Boolean,
    val archived: Boolean,
    val updatedAt: String,
    val createdAt: String
)

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["conversationId", "createdAt"])
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val createdAt: String
)

/** Library — content saved from the chat surface ("Save to Library"). */
@Entity(tableName = "saved_items")
data class SavedItemEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val content: String,
    val createdAt: String
)

@Dao
interface ConversationDao {
    @Upsert
    suspend fun upsert(conversation: ConversationEntity)

    @Upsert
    suspend fun upsertAll(conversations: List<ConversationEntity>)

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeRecent(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ConversationEntity?

    @Query("UPDATE conversations SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE conversations SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun setTitle(id: String, title: String)

    /** Inbox view: archived hidden, pins float — same ordering language as the benchmark apps. */
    @Query("SELECT * FROM conversations WHERE archived = 0 ORDER BY pinned DESC, updatedAt DESC")
    fun observeActive(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE archived = 1 ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<ConversationEntity>>

    /** Edge-states pass: chat-scoped search across local titles. */
    @Query("SELECT * FROM conversations WHERE title LIKE '%' || :query || '%' ORDER BY updatedAt DESC LIMIT 20")
    suspend fun searchByTitle(query: String): List<ConversationEntity>

    /** FTS path — full-text index over titles, kept in sync by Room's content triggers. */
    @Query(
        "SELECT conversations.* FROM conversations " +
            "JOIN conversations_fts ON conversations.rowid = conversations_fts.rowid " +
            "WHERE conversations_fts MATCH :matchQuery " +
            "ORDER BY conversations.updatedAt DESC LIMIT 20"
    )
    suspend fun searchByTitleFts(matchQuery: String): List<ConversationEntity>

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM conversations")
    suspend fun clear()
}

@Dao
interface MessageDao {
    @Upsert
    suspend fun insertAll(messages: List<MessageEntity>)

    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun forConversation(conversationId: String): List<MessageEntity>

    /** Newest window first — long conversations open in O(page), never whole. */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentForConversation(conversationId: String, limit: Int): List<MessageEntity>

    /** One older window for scroll-up pagination — keyed strictly before the oldest loaded stamp. */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND createdAt < :beforeInclusive ORDER BY createdAt DESC LIMIT :limit")
    suspend fun beforeForConversation(conversationId: String, beforeInclusive: String, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt DESC LIMIT 1")
    suspend fun lastForConversation(conversationId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): MessageEntity?

    /** Fixed-width UTC stamps make >= lexicographic-safe for thread truncation. */
    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND createdAt >= :fromInclusive")
    suspend fun deleteFrom(conversationId: String, fromInclusive: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteForConversation(conversationId: String)

    /** Edge-states pass: chat-scoped search across local message bodies. */
    @Query("SELECT * FROM messages WHERE content LIKE '%' || :query || '%' ORDER BY createdAt DESC LIMIT 20")
    suspend fun searchContent(query: String): List<MessageEntity>

    /** FTS path — full-text index over message bodies, synced by Room's content triggers. */
    @Query(
        "SELECT messages.* FROM messages " +
            "JOIN messages_fts ON messages.rowid = messages_fts.rowid " +
            "WHERE messages_fts MATCH :matchQuery " +
            "ORDER BY messages.createdAt DESC LIMIT 20"
    )
    suspend fun searchContentFts(matchQuery: String): List<MessageEntity>
}

@Dao
interface SavedItemDao {
    @Upsert
    suspend fun upsert(item: SavedItemEntity)

    @Query("SELECT * FROM saved_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedItemEntity>>

    @Query("DELETE FROM saved_items WHERE id = :id")
    suspend fun delete(id: String)
}

// --- full-text search plumbing -------------------------------------------------

/** FTS shadow of the conversations table — content sync handled by Room triggers. */
@Fts4(contentEntity = ConversationEntity::class)
@Entity(tableName = "conversations_fts")
data class ConversationFtsEntity(val title: String)

/** FTS shadow of the messages table — content sync handled by Room triggers. */
@Fts4(contentEntity = MessageEntity::class)
@Entity(tableName = "messages_fts")
data class MessageFtsEntity(val content: String)

/**
 * Turn raw user input into a safe FTS MATCH expression — each word becomes a
 * quoted token so operators/wildcards in the input can never break the query.
 * Returns null for non-ASCII input (CJK and friends), where the simple
 * tokenizer is useless and the LIKE fallback matches better.
 */
fun ftsMatchQuery(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.any { !it.isLetterOrDigit() && !it.isWhitespace() }) return null
    if (trimmed.any { it.code > 0x7F }) return null
    return trimmed.split(Regex("\\s+"))
        .mapNotNull { token -> token.filter { it.isLetterOrDigit() }.takeIf { it.isNotEmpty() } }
        .joinToString(" ") { "\"$it\"" }
        .ifEmpty { null }
}

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        SavedItemEntity::class,
        ConversationFtsEntity::class,
        MessageFtsEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun savedItemDao(): SavedItemDao

    companion object {
        /** v1 → v2: add the FTS shadows and backfill them from existing rows —
         *  user chats written by earlier builds survive the upgrade untouched. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `conversations_fts` " +
                        "USING FTS4(`title` TEXT NOT NULL, content=`conversations`)"
                )
                db.execSQL("INSERT INTO conversations_fts(conversations_fts) VALUES('rebuild')")
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `messages_fts` " +
                        "USING FTS4(`content` TEXT NOT NULL, content=`messages`)"
                )
                db.execSQL("INSERT INTO messages_fts(messages_fts) VALUES('rebuild')")
            }
        }

        /** v2 → v3: add the Library saved-items table — purely additive, chat
         *  history written by earlier builds survives the upgrade untouched. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_items` (" +
                        "`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, `createdAt` TEXT NOT NULL, PRIMARY KEY(`id`)"
                )
            }
        }

        /** v3 → v4: performance indices — per-conversation ordering (paged reads of
         *  the newest window), inbox filter+sort, recents sort. Additive only;
         *  every earlier chat survives untouched. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_messages_conversationId_createdAt` " +
                        "ON `messages` (`conversationId`, `createdAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_conversations_updatedAt` " +
                        "ON `conversations` (`updatedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_conversations_archived_pinned_updatedAt` " +
                        "ON `conversations` (`archived`, `pinned`, `updatedAt`)"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "gsai-chat.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
    }
}
