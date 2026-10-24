package com.grapsee.gsai.ui.navigation.drawer

import com.grapsee.gsai.ui.icons.GsIcons
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
 */
class DrawerIconTest {

    @Test
    fun pinnedChatsShowAPin() {
        assertEquals(GsIcons.Pin, iconForChat(pinned = true))
    }

    @Test
    fun unpinnedChatsShowTheNeutralChatGlyph() {
        assertEquals(GsIcons.MessageCircle, iconForChat(pinned = false))
    }

    @Test
    fun theTwoStatesAreVisuallyDistinct() {
        assertNotEquals(iconForChat(pinned = true), iconForChat(pinned = false))
    }
}
