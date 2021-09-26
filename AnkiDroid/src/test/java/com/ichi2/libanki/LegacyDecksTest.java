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

package com.ichi2.libanki;

import com.ichi2.anki.RobolectricTest;
import com.ichi2.libanki.backend.exception.DeckRenameException;
import com.ichi2.utils.JSONObject;

import net.ankiweb.rsdroid.RustCleanup;

import org.apache.http.util.Asserts;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

@RustCleanup("Can be removed once we sunset the Java backend")
@RunWith(AndroidJUnit4.class)
public class LegacyDecksTest extends RobolectricTest {

    @Test
    public void testEnsureParents() throws DeckRenameException {
        Decks decks = getDecks();
        decks.id("test");
        String subsubdeck_name = decks._ensureParents("  tESt :: sub :: subdeck");
        assertEquals("test::sub:: subdeck", subsubdeck_name);// Only parents are renamed, not the last deck.
        assertNotNull(decks.byName("test::sub"));
        assertNull(decks.byName("test::sub:: subdeck"));
        assertNull(decks.byName("  test :: sub :: subdeck"));
        assertNull(decks.byName("  test :: sub "));

        decks.newDyn("filtered");
        assertThrows(DeckRenameException.class, () -> decks._ensureParents("filtered:: sub :: subdeck"));
    }


    @Test
    public void testEnsureParentsNotFiltered() throws DeckRenameException {
        Decks decks = getDecks();
        decks.id("test");
        String subsubdeck_name = decks._ensureParentsNotFiltered("  tESt :: sub :: subdeck");
        assertEquals("test::sub:: subdeck", subsubdeck_name);// Only parents are renamed, not the last deck.
        assertNotNull(decks.byName("test::sub"));
        assertNull(decks.byName("test::sub:: subdeck"));
        assertNull(decks.byName("  test :: sub :: subdeck"));
        assertNull(decks.byName("  test :: sub "));

        decks.newDyn("filtered");
        String filtered_subdeck_name = decks._ensureParentsNotFiltered("filtered:: sub :: subdeck");
        assertEquals("filtered'::sub:: subdeck", filtered_subdeck_name);// Only parents are renamed, not the last deck.
        assertNotNull(decks.byName("filtered'::sub"));
        assertNotNull(decks.byName("filtered'"));
        assertNull(decks.byName("filtered::sub:: subdeck"));
        assertNull(decks.byName("filtered::sub"));
    }



    @Test
    public void duplicateName() {
        Decks decks = getDecks();
        decks.load("{\"2\": {\"name\": \"A\", \"id\":2}, \"3\": {\"name\": \"A\", \"id\":3}, \"4\": {\"name\": \"A::B\", \"id\":4}}", "{}");
        decks.checkIntegrity();
        JSONObject deckA = decks.byName("A");
        Asserts.notNull(deckA, "A deck with name \"A\" should still exists");
        assertThat("A deck with name \"A\" should have name \"A\"", deckA.getString("name"), is("A"));
        JSONObject deckAPlus = decks.byName("A+");
        Asserts.notNull(deckAPlus, "A deck with name \"A+\" should still exists");
    }

    @Test
    public void descendantOfFiltered() throws DeckRenameException {
        Decks decks = getDecks();
        long filtered_id = decks.newDyn("filtered");
        assertThrows(DeckRenameException.class,  () -> decks.id("filtered::subdeck::subsubdeck"));

        long subdeck_id = decks.id_safe("filtered::subdeck::subsubdeck");
        Deck subdeck = decks.get(subdeck_id);
        assertEquals("filtered'::subdeck::subsubdeck", subdeck.getString("name"));
    }

    protected Decks getDecks() {
        Collection col = getCol();

        if (col.getDecks() instanceof Decks) {
            return (Decks) col.getDecks();
        }

        Decks decks = new Decks(col);

        // following copied from storage:: _setColVars

        JSONObject defaultDeck = new JSONObject(Decks.DEFAULT_DECK);
        defaultDeck.put("id", 1);
        defaultDeck.put("name", "Default");
        defaultDeck.put("conf", 1);
        defaultDeck.put("mod", col.getTime().intTime());

        JSONObject allDecks = new JSONObject();
        allDecks.put("1", defaultDeck);


        JSONObject gc = new JSONObject(Decks.DEFAULT_CONF);
        gc.put("id", 1);
        JSONObject allDeckConfig = new JSONObject();
        allDeckConfig.put("1", gc);

        decks.load(Utils.jsonToString(allDecks), Utils.jsonToString(allDeckConfig));


        return decks;
    }
}
