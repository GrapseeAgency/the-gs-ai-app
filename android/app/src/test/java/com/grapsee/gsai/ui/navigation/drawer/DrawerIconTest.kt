package com.grapsee.gsai.ui.navigation.drawer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The recent row's icon is a claim about the chat, so it has exactly two answers
 * and they must be different.
 *
 * This replaces a hash of the title into four glyphs. Measured, that hash put
 * three of the sheet's four titles on the same glyph, and it disagreed with the
 * Swift implementation for emoji and CJK because Kotlin folds UTF-16 code units
 * while Swift folds Unicode scalars — so one chat drew two different icons on the
 * two platforms. A rule with two inputs can be verified; a hash pretending to know
 * what a chat is about cannot.
 *
 * The test asserts the DECISION, not the vector: comparing ImageVectors in a plain
 * JVM test initialises Compose's vector machinery and throws before any assertion
 * runs (the first version of this file did exactly that).
 */
class DrawerIconTest {

    @Test
    fun pinnedChatsAskForAPin() {
        assertEquals(DrawerIconKey.Pinned, drawerIconKey(pinned = true))
    }

    @Test
    fun unpinnedChatsAskForTheNeutralGlyph() {
        assertEquals(DrawerIconKey.Chat, drawerIconKey(pinned = false))
    }

    @Test
    fun theTwoStatesAreDistinct() {
        assertNotEquals(drawerIconKey(pinned = true), drawerIconKey(pinned = false))
    }
}
