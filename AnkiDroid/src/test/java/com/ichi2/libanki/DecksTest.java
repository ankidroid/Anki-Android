/*
 *  Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>
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

import org.apache.http.util.Asserts;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.libanki.Decks.CURRENT_DECK;
import static com.ichi2.testutils.AnkiAssert.assertDoesNotThrow;
import static com.ichi2.testutils.AnkiAssert.assertEqualsArrayList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class DecksTest extends RobolectricTest {
    // Used in other class to populate decks.
    public static final String[] TEST_DECKS = {
            "scxipjiyozczaaczoawo",
            "cmxieunwoogyxsctnjmv::abcdefgh::ZYXW",
            "cmxieunwoogyxsctnjmv::INSBGDS",
            "::foobar", // Addition test for issue #11026
            "A::", "::A", "::", "::A::", "::::", "A::B::", "::A::B", "::A::B::" // Additional tests for #11131
    };
    @Test
    public void ensureDeckList() {
        DeckManager decks = getCol().getDecks();
        for (String deckName: TEST_DECKS) {
            addDeck(deckName);
        }
        Deck brokenDeck = decks.byName("cmxieunwoogyxsctnjmv::INSBGDS");
        Asserts.notNull(brokenDeck,"We should get deck with given name");
        // Changing the case. That could exists in an old collection or during sync.
        brokenDeck.put("name", "CMXIEUNWOOGYXSCTNJMV::INSBGDS");
        decks.save(brokenDeck);

        decks.childMap();
        for (JSONObject deck: decks.all()) {
            long did = deck.getLong("id");
            for (JSONObject parent: decks.parents(did)) {
                Asserts.notNull(parent, "Parent should not be null");
            }
        }
    }

    @Test
    public void trim() {
        assertThat(Decks.strip("A\nB C\t D"), is("A\nB C\t D"));
        assertThat(Decks.strip("\n A\n\t"), is("A"));
        assertThat(Decks.strip("Z::\n A\n\t::Y"), is("Z::A::Y"));
    }


    /******************
     ** autogenerated from https://github.com/ankitects/anki/blob/2c73dcb2e547c44d9e02c20a00f3c52419dc277b/pylib/tests/test_cards.py*
     ******************/

    @Test
    public void test_basic() {
        Collection col = getCol();
        DeckManager decks = col.getDecks();
        // we start with a standard col
        assertEquals(1, decks.allSortedNames().size());
        // it should have an id of 1
        assertNotNull(decks.name(1));
        // create a new col
        long parentId = addDeck("new deck");
        assertNotEquals(parentId, 0);
        assertEquals(2, decks.allSortedNames().size());
        // should get the same id
        assertEquals(parentId, (long) addDeck("new deck"));
        // we start with the default col selected
        assertEquals(1, decks.selected());
        assertEqualsArrayList(new Long[] {1L}, decks.active());
        // we can select a different col
        decks.select(parentId);
        assertEquals(parentId, decks.selected());
        assertEqualsArrayList(new Long[] {parentId}, decks.active());
        // let's create a child
        long childId = addDeck("new deck::child");
        col.reset();
        // it should have been added to the active list
        assertEquals(parentId, decks.selected());
        assertEqualsArrayList(new Long[] {parentId, childId}, decks.active());
        // we can select the child individually too
        decks.select(childId);
        assertEquals(childId, decks.selected());
        assertEqualsArrayList(new Long[] {childId}, decks.active());
        // parents with a different case should be handled correctly
        addDeck("ONE");
        Model m = col.getModels().current();
        m.put("did", addDeck("one::two"));
        col.getModels().save(m, false);
        Note n = col.newNote();
        n.setItem("Front", "abc");
        col.addNote(n);

        assertEquals(decks.id_for_name("new deck").longValue(), parentId);
        assertEquals(decks.id_for_name("  New Deck  ").longValue(), parentId);
        assertNull(decks.id_for_name("Not existing deck"));
        assertNull(decks.id_for_name("new deck::not either"));
    }


    @Test
    public void test_remove() {
        Collection col = getCol();
        // create a new col, and add a note/card to it
        long deck1 = addDeck("deck1");
        Note note = col.newNote();
        note.setItem("Front", "1");
        note.model().put("did", deck1);
        col.addNote(note);
        Card c = note.cards().get(0);
        assertEquals(deck1, c.getDid());
        assertEquals(1, col.cardCount());
        col.getDecks().rem(deck1);
        assertEquals(0, col.cardCount());
        // if we try to get it, we get the default
        assertEquals("[no deck]", col.getDecks().name(c.getDid()));
    }


    @Test
    public void test_rename() throws DeckRenameException {
        Collection col = getCol();
        long id = addDeck("hello::world");
        // should be able to rename into a completely different branch, creating
        // parents as necessary
        DeckManager decks = col.getDecks();
        decks.rename(decks.get(id), "foo::bar");
        List<String> names = decks.allSortedNames();
        assertTrue(names.contains("foo"));
        assertTrue(names.contains("foo::bar"));
        assertFalse(names.contains("hello::world"));
        // create another col
        id = addDeck("tmp");
         /* TODO: do we want to follow upstream here ?
         // automatically adjusted if a duplicate name
         decks.rename(decks.get(id), "FOO");
         names =  decks.allSortedNames();
         assertThat(names, containsString("FOO+"));
         
          */
        // when renaming, the children should be renamed too
        addDeck("one::two::three");
        id = addDeck("one");
        col.getDecks().rename(col.getDecks().get(id), "yo");
        names = col.getDecks().allSortedNames();
        for (String n : new String[] {"yo", "yo::two", "yo::two::three"}) {
            assertTrue(names.contains(n));
        }
        // over filtered
        long filteredId = addDynamicDeck("filtered");
        Deck filtered = col.getDecks().get(filteredId);
        long childId = addDeck("child");
        Deck child = col.getDecks().get(childId);
        assertThrows(DeckRenameException.class, () -> col.getDecks().rename(child, "filtered::child"));
        assertThrows(DeckRenameException.class, () -> col.getDecks().rename(child, "FILTERED::child"));
    }

    /* TODO: maybe implement. We don't drag and drop here anyway, so buggy implementation is okay
     @Test public void test_renameForDragAndDrop() throws DeckRenameException {
     // TODO: upstream does not return "default", remove it
     Collection col = getCol();

     long languages_did = addDeck("Languages");
     long chinese_did = addDeck("Chinese");
     long hsk_did = addDeck("Chinese::HSK");

     // Renaming also renames children
     col.getDecks().renameForDragAndDrop(chinese_did, languages_did);
     assertEqualsArrayList(new String [] {"Default", "Languages", "Languages::Chinese", "Languages::Chinese::HSK"}, col.getDecks().allSortedNames());

     // Dragging a col onto itself is a no-op
     col.getDecks().renameForDragAndDrop(languages_did, languages_did);
     assertEqualsArrayList(new String [] {"Default", "Languages", "Languages::Chinese", "Languages::Chinese::HSK"}, col.getDecks().allSortedNames());

     // Dragging a col onto its parent is a no-op
     col.getDecks().renameForDragAndDrop(hsk_did, chinese_did);
     assertEqualsArrayList(new String [] {"Default", "Languages", "Languages::Chinese", "Languages::Chinese::HSK"}, col.getDecks().allSortedNames());

     // Dragging a col onto a descendant is a no-op
     col.getDecks().renameForDragAndDrop(languages_did, hsk_did);
     // TODO: real problem to correct, even if we don't have drag and drop
     // assertEqualsArrayList(new String [] {"Default", "Languages", "Languages::Chinese", "Languages::Chinese::HSK"}, col.getDecks().allSortedNames());

     // Can drag a grandchild onto its grandparent.  It becomes a child
     col.getDecks().renameForDragAndDrop(hsk_did, languages_did);
     assertEqualsArrayList(new String [] {"Default", "Languages", "Languages::Chinese", "Languages::HSK"}, col.getDecks().allSortedNames());

     // Can drag a col onto its sibling
     col.getDecks().renameForDragAndDrop(hsk_did, chinese_did);
     assertEqualsArrayList(new String [] {"Default", "Languages", "Languages::Chinese", "Languages::Chinese::HSK"}, col.getDecks().allSortedNames());

     // Can drag a col back to the top level
     col.getDecks().renameForDragAndDrop(chinese_did, null);
     assertEqualsArrayList(new String [] {"Default", "Chinese", "Chinese::HSK", "Languages"}, col.getDecks().allSortedNames());

     // Dragging a top level col to the top level is a no-op
     col.getDecks().renameForDragAndDrop(chinese_did, null);
     assertEqualsArrayList(new String [] {"Default", "Chinese", "Chinese::HSK", "Languages"}, col.getDecks().allSortedNames());

     // decks are renamed if necessary«
     long new_hsk_did = addDeck("hsk");
     col.getDecks().renameForDragAndDrop(new_hsk_did, chinese_did);
     assertEqualsArrayList(new String [] {"Default", "Chinese", "Chinese::HSK", "Chinese::hsk+", "Languages"}, col.getDecks().allSortedNames());
     col.getDecks().rem(new_hsk_did);

     }
     */

    @Test
    public void curDeckIsLong() {
        // Regression for #8092
        Collection col = getCol();
        DeckManager decks = col.getDecks();
        long id = addDeck("test");
        decks.select(id);
        assertDoesNotThrow("curDeck should be saved as a long. A deck id.", () -> col.get_config_long(CURRENT_DECK));
    }


    @Test
    public void isDynStd() {
        Collection col = getCol();
        DeckManager decks = col.getDecks();
        long filteredId = addDynamicDeck("filtered");
        Deck filtered = decks.get(filteredId);
        long deckId = addDeck("deck");
        Deck deck = decks.get(deckId);
        assertThat(deck.isStd(), is(Boolean.valueOf(true)));
        assertThat(deck.isDyn(), is(Boolean.valueOf(false)));
        assertThat(filtered.isStd(), is(Boolean.valueOf(false)));
        assertThat(filtered.isDyn(), is(Boolean.valueOf(true)));

        DeckConfig filtered_config = decks.confForDid(filteredId);
        DeckConfig deck_config = decks.confForDid(deckId);
        assertThat(deck_config.isStd(), is(Boolean.valueOf(true)));
        assertThat(deck_config.isDyn(), is(Boolean.valueOf(false)));
        assertThat(filtered_config.isStd(), is(Boolean.valueOf(false)));
        assertThat(filtered_config.isDyn(), is(Boolean.valueOf(true)));

    }

    @Test
    public void confForDidReturnsDefaultIfNotFound() {
        // https://github.com/ankitects/anki/commit/94d369db18c2a6ac3b0614498d8abcc7db538633
        DeckManager decks = getCol().getDecks();

        Deck d = decks.all().get(0);
        d.put("conf", 12L);
        decks.save();

        DeckConfig config = decks.confForDid(d.getLong("id"));

        assertThat("If a config is not found, return the default", config.getLong("id"), is(1L));
    }
}
