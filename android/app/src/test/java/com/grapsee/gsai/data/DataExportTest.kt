package com.grapsee.gsai.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.grapsee.gsai.data.local.ConversationEntity
import com.grapsee.gsai.data.local.MessageEntity
import com.grapsee.gsai.di.ServiceLocator
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The format tiles must change the BYTES, not just the selected chip. These
 * tests write the real files through the same `DataExport.write` the screen
 * calls and read them back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataExportTest {

    private lateinit var context: Context

    @Before
    fun reset() {
        context = ApplicationProvider.getApplicationContext()
        ServiceLocator.init(context)
        SettingsStore.init(context)
        AccountStore.setDisplayName(context, "Arafat")
        AccountStore.setEmail(context, "arafat@grapsee.com")
        runBlocking {
            val db = ServiceLocator.db
            db.messageDao().deleteAll()
            db.savedItemDao().clear()
            db.conversationDao().clear()
            db.conversationDao().upsert(
                ConversationEntity(
                    id = "c1",
                    title = "Retention design",
                    modelId = "pro",
                    pinned = false,
                    archived = false,
                    updatedAt = "2026-10-30T10:00:00.000Z",
                    createdAt = "2026-10-30T09:00:00.000Z",
                )
            )
            db.messageDao().upsert(
                MessageEntity(
                    id = "m1",
                    conversationId = "c1",
                    role = "user",
                    content = "Export this, \"quoted\".",
                    createdAt = "2026-10-30T09:01:00.000Z",
                )
            )
        }
    }

    private fun write(format: DataExport.Format, scope: DataExport.Scope): String = runBlocking {
        val file = File(context.cacheDir, "export-test.${format.extension}")
        file.delete()
        val ok = DataExport.write(context, Uri.fromFile(file), format, scope)
        assertTrue("$format export did not write", ok)
        file.readText()
    }

    @Test
    fun jsonCarriesTheCorpusAndTheProfile() {
        val body = write(DataExport.Format.JSON, DataExport.Scope.COMPLETE)
        val root = JSONObject(body)
        assertEquals("complete", root.getString("scope"))
        assertEquals("Arafat", root.getJSONObject("profile").getString("displayName"))
        assertEquals("Retention design", root.getJSONArray("conversations").getJSONObject(0).getString("title"))
        assertEquals(
            "Export this, \"quoted\".",
            root.getJSONArray("messages").getJSONObject(0).getString("content"),
        )
    }

    @Test
    fun csvCarriesOneRowPerRecordAndEscapesQuotes() {
        val body = write(DataExport.Format.CSV, DataExport.Scope.COMPLETE)
        val lines = body.trim().lines()
        assertEquals(
            "recordType,id,parentId,title,role,createdAt,content,attachments",
            lines.first(),
        )
        assertTrue(lines.any { it.startsWith("\"conversation\",\"c1\"") })
        assertTrue(lines.any { it.contains("\"Export this, \"\"quoted\"\".\"") })
        assertTrue(lines.any { it.startsWith("\"profile\",\"\",\"\",\"email\"") })
    }

    @Test
    fun personalArchiveOmitsChatRows() {
        val body = write(DataExport.Format.JSON, DataExport.Scope.PERSONAL_ARCHIVE)
        val root = JSONObject(body)
        assertEquals(false, root.has("conversations"))
        assertEquals("Arafat", root.getJSONObject("profile").getString("displayName"))
    }
}
