package com.grapsee.gsai.ui.notifications

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.grapsee.gsai.data.notifications.NoticeInbox
import com.grapsee.gsai.ui.theme.TheGsAiTheme
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The inbox's reader contract, proved without a device: newest-first ordering
 * even when the stored file is not, a row that really marks itself read, and
 * "Mark all as read" writing every row.
 *
 * These are the behaviours the six-sheet captures show but cannot fail — a
 * screenshot of the right order stays green if the file happens to be sorted,
 * so the order is asserted against a deliberately unsorted fixture here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class NotificationsInboxInteractionTest {

    @get:Rule val compose = createComposeRule()

    private lateinit var ctx: Context

    @Before
    fun clearInbox() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("gs_notice_inbox", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    /** Writes the store's own JSON shape so the fixture can be out of order. */
    private fun seed(entries: List<NoticeInbox.Entry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("kindId", e.kindId)
                    put("title", e.title)
                    put("body", e.body)
                    put("createdAt", e.createdAtMillis)
                    put("read", e.read)
                }
            )
        }
        ctx.getSharedPreferences("gs_notice_inbox", Context.MODE_PRIVATE)
            .edit().putString("gs.notice.inbox.entries", arr.toString()).commit()
        // Bump the revision the screen reads without touching a row: no entry
        // can carry Long.MIN_VALUE, so this write is a no-op on the data.
        NoticeInbox.markRead(ctx, Long.MIN_VALUE)
    }

    private fun entry(
        id: Long,
        title: String,
        createdAt: Long,
        read: Boolean = false,
    ) = NoticeInbox.Entry(
        id = id,
        kindId = "account-activity",
        title = title,
        body = "Body for $title",
        createdAtMillis = createdAt,
        read = read,
    )

    private fun renderScreen(now: Long = System.currentTimeMillis()) {
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                NotificationsInboxScreen(onBack = {}, onNavigate = {}, now = now)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun rowsRenderNewestFirstEvenWhenTheStoredFileIsOutOfOrder() {
        val now = System.currentTimeMillis()
        seed(
            listOf(
                entry(1, "Older notice", now - 2 * 3_600_000),
                entry(2, "Newer notice", now - 3_600_000),
            )
        )
        renderScreen()

        val olderTop = compose.onNodeWithText("Older notice")
            .fetchSemanticsNode().boundsInRoot.top
        val newerTop = compose.onNodeWithText("Newer notice")
            .fetchSemanticsNode().boundsInRoot.top

        assertTrue(
            "the reader must sort by createdAt, not trust file order " +
                "(newer=$newerTop, older=$olderTop)",
            newerTop < olderTop,
        )
    }

    @Test
    fun openingANoticeMarksThatRowRead() {
        seed(listOf(entry(7, "Account update", System.currentTimeMillis())))
        renderScreen()

        compose.onNodeWithTag("notice-row-7").performClick()
        compose.waitForIdle()

        assertTrue(
            "opening a notice did not flip its stored read flag",
            NoticeInbox.read(ctx).single { it.id == 7L }.read,
        )
    }

    @Test
    fun theInjectedClockDecidesTheTodayEarlierSplit() {
        // 18:00 today, not the wall clock: a 4h-old notice is Today and a
        // 24h-old one is Earlier no matter when this suite runs. The screenshot
        // fixture uses the same fixed clock, so the card count cannot collapse
        // at midnight.
        val fixedNow = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 18)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        seed(
            listOf(
                entry(1, "Earlier notice", fixedNow - 24 * 3_600_000),
                entry(2, "Today notice", fixedNow - 4 * 3_600_000),
            )
        )
        renderScreen(now = fixedNow)

        val earlierHeader = compose.onAllNodesWithText("Earlier")[0]
            .fetchSemanticsNode().boundsInRoot.top
        val todayNotice = compose.onNodeWithText("Today notice")
            .fetchSemanticsNode().boundsInRoot.top
        val earlierNotice = compose.onNodeWithText("Earlier notice")
            .fetchSemanticsNode().boundsInRoot.top

        assertTrue("the 4h notice must be in Today", todayNotice < earlierHeader)
        assertTrue("the 24h notice must be under Earlier", earlierNotice > earlierHeader)
    }

    @Test
    fun markAllAsReadWritesEveryRow() {
        val now = System.currentTimeMillis()
        seed(
            listOf(
                entry(1, "First", now - 60_000),
                entry(2, "Second", now),
            )
        )
        renderScreen()

        compose.onNodeWithTag("notice-mark-all").performClick()
        compose.waitForIdle()

        assertTrue(
            "Mark all as read left a row unread: " +
                NoticeInbox.read(ctx).filterNot { it.read }.map { it.title },
            NoticeInbox.read(ctx).all { it.read },
        )
    }
}
