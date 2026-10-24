package com.grapsee.gsai.ui.navigation.drawer

import androidx.compose.ui.graphics.vector.ImageVector
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.navigation.GsRoutes
import java.util.concurrent.TimeUnit

/** One row in "YOUR SPACE". A null route is not allowed — see [DESTINATIONS]. */
data class DrawerDestination(
    val label: String,
    val icon: ImageVector,
    val route: String,
)

/** One row in "RECENT CHATS": title, the relative time under it, and its icon. */
data class DrawerRecent(
    val id: String,
    val title: String,
    val timestamp: String,
    val icon: ImageVector,
)

data class DrawerIdentity(
    val name: String,
    val workspace: String,
    val initials: String,
)

/**
 * A capture needs real rows without a database. This is passed only by the
 * screenshot harnesses; production passes null and reads the store, so no test
 * ever writes to ConversationStore — an earlier harness that seeded the store
 * made Home's own render test fail on its `activeConversations.isEmpty`
 * assertion.
 */
data class DrawerFixture(
    val identity: DrawerIdentity,
    val recents: List<DrawerRecent>,
)

/**
 * The four destinations, and the reason each one is real:
 *
 *  - Explore        `GsRoutes.EXPLORE`, the real Explore screen. This was the one
 *                   line the drawer's plan reserved for the concurrent Explore
 *                   workstream, and it landed as expected: they added the route,
 *                   this row points at it, and nothing else here changed.
 *  - Image Studio   no studio screen exists yet, so the row lands in the
 *                   composer with a seeded prompt — the same `chatPrefill`
 *                   contract Home's Create tile already uses.
 *  - Voice          the existing voice screen.
 *  - Saved creations the existing archive, which is where saved/archived
 *                   conversations live ("Saved responses" in the old drawer).
 */
val DESTINATIONS: List<DrawerDestination> = listOf(
    DrawerDestination("Explore", GsIcons.Compass, GsRoutes.EXPLORE),
    DrawerDestination("Image Studio", GsIcons.Image, GsRoutes.chat(null, IMAGE_STUDIO_PROMPT)),
    DrawerDestination("Voice", GsIcons.Activity, GsRoutes.VOICE),
    DrawerDestination("Saved creations", GsIcons.Bookmark, GsRoutes.CHAT_ARCHIVE),
)

/**
 * The prompt Image Studio seeds. It is a real, sendable draft rather than a
 * placeholder: the composer opens with the text in it and the user decides.
 */
const val IMAGE_STUDIO_PROMPT: String = "Create an image of "

/** How many recents the drawer shows. The sheet shows four. */
const val RECENT_LIMIT: Int = 4

/**
 * "Just now / Yesterday / Yesterday / Mon" — the sheet's four labels, from one
 * pure function so both platforms and the fixtures agree.
 */
fun relativeTime(updatedAtMs: Long, nowMs: Long): String {
    val delta = nowMs - updatedAtMs
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    if (minutes < 60) return "Just now"
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    if (hours < 12) return "$hours h ago"
    val days = TimeUnit.MILLISECONDS.toDays(delta)
    if (days <= 1) return "Yesterday"
    if (days < 7) return DAY_NAMES[((days + 3) % 7).toInt()]
    return "${days / 7}w ago"
}

private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/**
 * The store keeps `updatedAt` as an ISO-8601 string, so the drawer parses it the
 * way the rest of the app does and then uses the sheet's vocabulary. An
 * unparseable stamp reads "Earlier" rather than silently becoming "Just now",
 * which would be a lie about a real conversation.
 */
fun relativeTimeIso(iso: String, now: java.time.OffsetDateTime = java.time.OffsetDateTime.now()): String =
    runCatching {
        val then = java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        relativeTime(then, now.toInstant().toEpochMilli())
    }.getOrElse { "Earlier" }

/**
 * A recent row's icon says something TRUE about the chat, or it says nothing.
 *
 * The first version hashed the title into one of four glyphs to reproduce the
 * sheet's four different icons. Measured, it was not honest and barely varied:
 * three of the sheet's four titles hashed to the SAME glyph, and titles with
 * emoji or CJK hashed differently on Kotlin (UTF-16 units) than on Swift (Unicode
 * scalars), so the same chat drew two different icons on the two platforms. The
 * sheet's glyphs are semantic — a bulb for "ideas", an atom for "quantum physics"
 * — and no hash recovers meaning from copy.
 *
 * So the icon is driven by the one per-chat fact the drawer actually holds:
 * `pinned`. A pinned chat shows a pin; everything else shows a neutral chat glyph.
 * Reproducing the sheet's variety would need chat categories, which is a product
 * feature, not a decoration to fake.
 */
