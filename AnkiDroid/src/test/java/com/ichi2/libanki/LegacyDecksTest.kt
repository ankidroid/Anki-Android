/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.libanki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.libanki.backend.exception.DeckRenameException
import com.ichi2.testutils.assertThrows
import com.ichi2.utils.JSONObject
import com.ichi2.utils.KotlinCleanup
import net.ankiweb.rsdroid.RustCleanup
import org.hamcrest.MatcherAssert.*
import org.hamcrest.Matchers
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RustCleanup("Can be removed once we sunset the Java backend")
@KotlinCleanup("IDE-based lint")
@RunWith(AndroidJUnit4::class)
class LegacyDecksTest : RobolectricTest() {
    @Test
    @Throws(DeckRenameException::class)
    fun testEnsureParents() {
        val decks = decks
        decks.id("test")
        val subsubdeck_name = decks._ensureParents("  tESt :: sub :: subdeck")
        assertEquals("test::sub:: subdeck", subsubdeck_name) // Only parents are renamed, not the last deck.
        assertNotNull(decks.byName("test::sub"))
        assertNull(decks.byName("test::sub:: subdeck"))
        assertNull(decks.byName("  test :: sub :: subdeck"))
        assertNull(decks.byName("  test :: sub "))

        decks.newDyn("filtered")
        assertThrows<DeckRenameException> { decks._ensureParents("filtered:: sub :: subdeck") }
    }

    @Test
    @Throws(DeckRenameException::class)
    fun testEnsureParentsNotFiltered() {
        val decks = decks
        decks.id("test")
        val subsubdeck_name = decks._ensureParentsNotFiltered("  tESt :: sub :: subdeck")
        assertEquals("test::sub:: subdeck", subsubdeck_name) // Only parents are renamed, not the last deck.
        assertNotNull(decks.byName("test::sub"))
        assertNull(decks.byName("test::sub:: subdeck"))
        assertNull(decks.byName("  test :: sub :: subdeck"))
        assertNull(decks.byName("  test :: sub "))

        decks.newDyn("filtered")
        val filtered_subdeck_name = decks._ensureParentsNotFiltered("filtered:: sub :: subdeck")
        assertEquals("filtered'::sub:: subdeck", filtered_subdeck_name) // Only parents are renamed, not the last deck.
        assertNotNull(decks.byName("filtered'::sub"))
        assertNotNull(decks.byName("filtered'"))
        assertNull(decks.byName("filtered::sub:: subdeck"))
        assertNull(decks.byName("filtered::sub"))
    }

    @Test
    fun duplicateName() {
        val decks = decks
        decks.load("{\"2\": {\"name\": \"A\", \"id\":2}, \"3\": {\"name\": \"A\", \"id\":3}, \"4\": {\"name\": \"A::B\", \"id\":4}}", "{}")
        decks.checkIntegrity()
        val deckA: JSONObject? = decks.byName("A")
        assertNotNull(deckA, "A deck with name \"A\" should still exists")
        assertThat("A deck with name \"A\" should have name \"A\"", deckA.getString("name"), Matchers.`is`("A"))
        val deckAPlus: JSONObject? = decks.byName("A+")
        assertNotNull(deckAPlus, "A deck with name \"A+\" should still exists")
    }

    @Test
    @Throws(DeckRenameException::class)
    fun descendantOfFiltered() {
        val decks = decks
        decks.newDyn("filtered")
        assertThrows<DeckRenameException> { decks.id("filtered::subdeck::subsubdeck") }

        val subdeck_id = decks.id_safe("filtered::subdeck::subsubdeck")
        val subdeck = decks.get(subdeck_id)
        assertEquals("filtered'::subdeck::subsubdeck", subdeck.getString("name"))
    }

    // following copied from storage:: _setColVars
    protected val decks: Decks
        get() {
            val col = col
            if (col.decks is Decks) {
                return col.decks as Decks
            }
            val decks = Decks(col)

            // following copied from storage:: _setColVars

            val defaultDeck = JSONObject(Decks.DEFAULT_DECK)
            defaultDeck.put("id", 1)
            defaultDeck.put("name", "Default")
            defaultDeck.put("conf", 1)
            defaultDeck.put("mod", col.time.intTime())

            val allDecks = JSONObject()
            allDecks.put("1", defaultDeck)
            val gc = JSONObject(Decks.DEFAULT_CONF)
            gc.put("id", 1)
            val allDeckConfig = JSONObject()
            allDeckConfig.put("1", gc)

            decks.load(Utils.jsonToString(allDecks), Utils.jsonToString(allDeckConfig))

            return decks
        }
}
