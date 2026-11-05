package com.grapsee.gsai.ui.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.grapsee.gsai.data.local.SavedItemEntity
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.theme.TheGsAiTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Renders the library against the REAL table, not a list handed to the
 * composable — the screen's whole contract is "reads `saved_items` and shows
 * what is in it", and a fixture that bypasses Room would prove nothing about
 * the thing that has been silently storing rows since save-to-library shipped.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryProbeTest {

    @get:Rule val compose = createComposeRule()

    @Before
    fun onlyOnRequest() {
        assumeTrue(System.getProperty("gs.screenshots") == "true")
    }

    private val stamp = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        .withZone(ZoneOffset.UTC)

    private fun hoursAgo(hours: Long): String =
        stamp.format(Instant.now().minusSeconds(hours * 3600))

    private fun seed() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        ServiceLocator.init(context)
        runBlocking {
            val dao = ServiceLocator.db.savedItemDao()
            dao.clear()
            dao.upsert(
                SavedItemEntity(
                    id = "lib-1",
                    kind = "message",
                    title = "Weekly plan for the 9–5",
                    content = "Here is a week that fits around a 9–5: Monday and Thursday are " +
                        "strength days at 06:30, Tuesday is a 25-minute walk at lunch, " +
                        "Wednesday is rest, Friday is mobility for 15 minutes, and the weekend " +
                        "holds one longer session you actually enjoy.",
                    createdAt = hoursAgo(2),
                )
            )
            dao.upsert(
                SavedItemEntity(
                    id = "lib-2",
                    kind = "image",
                    title = "Launch note cover",
                    content = "A soft peach gradient with the lotus mark centred and the " +
                        "headline set in Inter Tight.",
                    createdAt = hoursAgo(5),
                )
            )
            dao.upsert(
                SavedItemEntity(
                    id = "lib-3",
                    kind = "draft",
                    title = "Invoice Jul 2025",
                    content = "Invoice for July 2025 — three days of design work, " +
                        "one workshop, travel excluded.",
                    createdAt = hoursAgo(30),
                )
            )
            dao.upsert(
                SavedItemEntity(
                    id = "lib-4",
                    kind = "message",
                    title = "How to keep sourdough warm",
                    content = "A cold oven with the light on holds about 26°C, which is exactly " +
                        "what a bulk ferment wants in a cold kitchen.",
                    createdAt = hoursAgo(52),
                )
            )
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun libraryListLight() {
        seed()
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                SavedLibraryScreen(onBack = {}, onOpenItem = {})
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().captureRoboImage("build/screenshots/probe-library-light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun libraryListDark() {
        seed()
        compose.setContent {
            TheGsAiTheme(darkTheme = true) {
                SavedLibraryScreen(onBack = {}, onOpenItem = {})
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().captureRoboImage("build/screenshots/probe-library-dark.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun libraryItemLight() {
        seed()
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                LibraryItemScreen(itemId = "lib-1", onBack = {})
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().captureRoboImage("build/screenshots/probe-library-item.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun libraryEmptyLight() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        ServiceLocator.init(context)
        runBlocking { ServiceLocator.db.savedItemDao().clear() }
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                    SavedLibraryScreen(onBack = {}, onOpenItem = {})
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().captureRoboImage("build/screenshots/probe-library-empty.png")
    }
}
