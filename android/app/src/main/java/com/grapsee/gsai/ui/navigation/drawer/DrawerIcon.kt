package com.grapsee.gsai.ui.navigation.drawer

import androidx.compose.ui.graphics.vector.ImageVector
import com.grapsee.gsai.ui.icons.GsIcons

/**
 * A recent row's icon says something TRUE about the chat, or it says nothing.
 *
 * The first version hashed the title into one of four glyphs to reproduce the
 * sheet's four different icons. Measured, it was not honest and barely varied:
 * three of the sheet's four titles hashed to the SAME glyph, and titles with
 * emoji or CJK hashed differently on Kotlin (UTF-16 code units) than on Swift
 * (Unicode scalars), so one chat drew two different icons on the two platforms.
 * The sheet's glyphs are semantic — a bulb for "ideas", an atom for "quantum
 * physics" — and no hash recovers meaning from copy.
 *
 * So the icon answers the one per-chat fact the drawer holds: `pinned`.
 *
 * This file holds NOTHING else, on purpose. It used to sit beside `DESTINATIONS`,
 * whose top-level list builds ImageVectors: loading the enum initialised Compose
 * and the unit test threw `ExceptionInInitializerError` before asserting anything.
 */
enum class DrawerIconKey { Pinned, Chat }

/** The decision: pure, no Compose types in the signature, testable on the JVM. */
fun drawerIconKey(pinned: Boolean): DrawerIconKey =
    if (pinned) DrawerIconKey.Pinned else DrawerIconKey.Chat

/** The drawing: a name turned into the vector the row paints. */
fun iconForChat(pinned: Boolean): ImageVector = when (drawerIconKey(pinned)) {
    DrawerIconKey.Pinned -> GsIcons.Pin
    DrawerIconKey.Chat -> GsIcons.MessageCircle
}
