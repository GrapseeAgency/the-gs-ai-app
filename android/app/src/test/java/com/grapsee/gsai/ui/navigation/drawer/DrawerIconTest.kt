package com.grapsee.gsai.ui.navigation.drawer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The row icon, decided by the headline.
 *
 * Before this file the icon was a hash of the title into four glyphs — and the
 * hash put three of the sheet's four titles on the SAME glyph, which no test could
 * see because the capture's fixture hardcoded its icons. Now the decision is a
 * keyword in the headline, the fixtures call this function, and the sheet's own
 * four titles are the test vector.
 *
 * The pure function returns a NAME, so this runs on a bare JVM: comparing
 * ImageVectors would initialise Compose's vector machinery and throw before any
 * assertion ran (the first version of this test did exactly that).
 */
class DrawerIconTest {

    @Test
    fun theSheetsFourTitlesGetFourDifferentIcons() {
        assertEquals("CalendarCheck", drawerIconName("Plan my tomorrow", pinned = false))
        assertEquals("Lightbulb", drawerIconName("Ideas for a brand launch", pinned = false))
        assertEquals("Atom", drawerIconName("Explain quantum physics", pinned = false))
        assertEquals("MapPin", drawerIconName("Weekend in Kyoto", pinned = false))
    }

    @Test
    fun theSheetIsFourDistinctGlyphsNow() {
        val icons = listOf(
            "Plan my tomorrow", "Ideas for a brand launch",
            "Explain quantum physics", "Weekend in Kyoto",
        ).map { drawerIconName(it, pinned = false) }
        assertEquals(4, icons.toSet().size)
    }

    @Test
    fun aPinBeatsTheTopic() {
        assertEquals("Pin", drawerIconName("Weekend in Kyoto", pinned = true))
    }

    @Test
    fun aTitleWithNoTopicIsTheNeutralGlyph() {
        assertEquals("MessageCircle", drawerIconName("asdf qwer", pinned = false))
    }

    @Test
    fun matchingIsOnWholeWordsNotSubstrings() {
        // "planet" contains "plan", "cartoon" contains "cart", "scoreboard"
        // contains "score". None of them is the word, so none of them is a topic.
        assertEquals("MessageCircle", drawerIconName("planet fitness", pinned = false))
        assertEquals("MessageCircle", drawerIconName("cartoon", pinned = false))
        assertEquals("MessageCircle", drawerIconName("scoreboard wiring", pinned = false))
        // …while the words themselves still work.
        assertEquals("CalendarCheck", drawerIconName("plan the week", pinned = false))
        assertEquals("ShoppingBag", drawerIconName("cart", pinned = false))
    }

    @Test
    fun specificTopicsWinOverGeneralOnes() {
        // "explain" is a Learning keyword and "quantum" a Science one; Science is
        // declared first, so the subject wins over the verb.
        assertEquals("Atom", drawerIconName("Explain quantum physics", pinned = false))
        assertEquals("GraduationCap", drawerIconName("Explain this to me", pinned = false))
    }

    @Test
    fun matchingIsCaseInsensitive() {
        assertEquals(drawerIconName("quantum physics", false),
                     drawerIconName("QUANTUM PHYSICS", false))
    }

    @Test
    fun theTwoStatesAreDistinct() {
        assertNotEquals(drawerIconName("Plan my tomorrow", pinned = true),
                        drawerIconName("Plan my tomorrow", pinned = false))
    }

    @Test
    fun aHeadlineInAnotherScriptStillFindsItsTopic() {
        // `\b` does not exist in these languages, so these are matched by
        // containment; before that, every one of them fell through to the neutral
        // glyph no matter what it said.
        assertEquals("MapPin", drawerIconName("京都の週末", pinned = false))
        assertEquals("MapPin", drawerIconName("Путешествие в Токио", pinned = false))
        assertEquals("Atom", drawerIconName("解释量子物理", pinned = false))
        assertEquals("Atom", drawerIconName("физика элементарных частиц", pinned = false))
        assertEquals("CalendarCheck", drawerIconName("योजना बनाओ", pinned = false))
        assertEquals("CalendarCheck", drawerIconName("خطة ليوم غد", pinned = false))
        assertEquals("Utensils", drawerIconName("рецепт борща", pinned = false))
    }

    @Test
    fun accentsAreFoldedSoCaseAndDiacriticsDoNotMatter() {
        assertEquals("Atom", drawerIconName("FÍSICA CUÁNTICA", pinned = false))
        assertEquals("Atom", drawerIconName("física cuántica", pinned = false))
    }

    @Test
    fun everyTopicNamesAnIcon() {
        for (topic in DrawerTopic.entries) {
            assertEquals(topic.name, true, topic.icon.isNotBlank())
        }
    }
}
