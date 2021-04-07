package com.ichi2.libanki;

import com.ichi2.anki.RobolectricTest;
import com.ichi2.anki.exception.DeckRenameException;
import com.ichi2.anki.exception.FilteredAncestor;
import com.ichi2.utils.JSONObject;

import org.apache.http.util.Asserts;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import androidx.test.ext.junit.runners.AndroidJUnit4;

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
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.ArgumentMatchers.isNull;

@RunWith(AndroidJUnit4.class)
public class DecksTest extends RobolectricTest {
    // Used in other class to populate decks.
    public static final String[] TEST_DECKS = {
            "scxipjiyozczaaczoawo",
            "cmxieunwoogyxsctnjmv::abcdefgh::ZYXW",
            "cmxieunwoogyxsctnjmv::INSBGDS",
    };

    @Test
    public void duplicateName() {
        mDecks.load("{2: {\"name\": \"A\", \"id\":2}, 3: {\"name\": \"A\", \"id\":3}, 4: {\"name\": \"A::B\", \"id\":4}}", "{}");
        mDecks.checkIntegrity();
        JSONObject deckA = mDecks.byName("A");
        Asserts.notNull(deckA, "A deck with name \"A\" should still exists");
        assertThat("A deck with name \"A\" should have name \"A\"", deckA.getString("name"), is("A"));
        JSONObject deckAPlus = mDecks.byName("A+");
        Asserts.notNull(deckAPlus, "A deck with name \"A+\" should still exists");
    }
    @Test
    public void ensureDeckList() {
        for (String deckName: TEST_DECKS) {
            addDeck(deckName);
        }
        JSONObject brokenDeck = mDecks.byName("cmxieunwoogyxsctnjmv::INSBGDS");
        Asserts.notNull(brokenDeck,"We should get deck with given name");
        // Changing the case. That could exists in an old collection or during sync.
        brokenDeck.put("name", "CMXIEUNWOOGYXSCTNJMV::INSBGDS");
        mDecks.save(brokenDeck);

        mDecks.childMap();
        for (JSONObject deck: mDecks.all()) {
            long did = deck.getLong("id");
            for (JSONObject parent: mDecks.parents(did)) {
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
    public void test_basic() throws FilteredAncestor {
        // we start with a standard col
        assertEquals(1, mDecks.allSortedNames().size());
        // it should have an id of 1
        assertNotNull(mDecks.name(1));
        // create a new col
        long parentId = addDeck("new deck");
        assertNotEquals(parentId, 0);
        assertEquals(2, mDecks.allSortedNames().size());
        // should get the same id
        assertEquals(parentId, (long) addDeck("new deck"));
        // we start with the default col selected
        assertEquals(1, mDecks.selected());
        assertEqualsArrayList(new Long[] {1L}, mDecks.active());
        // we can select a different col
        mDecks.select(parentId);
        assertEquals(parentId, mDecks.selected());
        assertEqualsArrayList(new Long[] {parentId}, mDecks.active());
        // let's create a child
        long childId = addDeck("new deck::child");
        mCol.reset();
        // it should have been added to the active list
        assertEquals(parentId, mDecks.selected());
        assertEqualsArrayList(new Long[] {parentId, childId}, mDecks.active());
        // we can select the child individually too
        mDecks.select(childId);
        assertEquals(childId, mDecks.selected());
        assertEqualsArrayList(new Long[] {childId}, mDecks.active());
        // parents with a different case should be handled correctly
        addDeck("ONE");
        Model m = mCol.getModels().current();
        m.put("did", addDeck("one::two"));
        mCol.getModels().save(m, false);
        Note n = mCol.newNote();
        n.setItem("Front", "abc");
        mCol.addNote(n);

        assertEquals(mDecks.id_dont_create("new deck").longValue(), parentId);
        assertEquals(mDecks.id_dont_create("  New Deck  ").longValue(), parentId);
        assertNull(mDecks.id_dont_create("Not existing deck"));
        assertNull(mDecks.id_dont_create("new deck::not either"));
    }


    @Test
    public void test_remove() throws FilteredAncestor {
        // create a new col, and add a note/card to it
        long deck1 = addDeck("deck1");
        Note note = mCol.newNote();
        note.setItem("Front", "1");
        note.model().put("did", deck1);
        mCol.addNote(note);
        Card c = note.cards().get(0);
        assertEquals(deck1, c.getDid());
        assertEquals(1, mCol.cardCount());
        mDecks.rem(deck1);
        assertEquals(0, mCol.cardCount());
        // if we try to get it, we get the default
        assertEquals("[no deck]", mDecks.name(c.getDid()));
    }


    @Test
    public void test_rename() throws DeckRenameException, FilteredAncestor {
        long id = addDeck("hello::world");
        // should be able to rename into a completely different branch, creating
        // parents as necessary
        mDecks.rename(mDecks.get(id), "foo::bar");
        List<String> names = mDecks.allSortedNames();
        assertTrue(names.contains("foo"));
        assertTrue(names.contains("foo::bar"));
        assertFalse(names.contains("hello::world"));
        // create another col
        id = addDeck("tmp");
         /* TODO: do we want to follow upstream here ?
         // automatically adjusted if a duplicate name
         mDecks.rename(mDecks.get(id), "FOO");
         names =  mDecks.allSortedNames();
         assertThat(names, containsString("FOO+"));
         
          */
        // when renaming, the children should be renamed too
        addDeck("one::two::three");
        id = addDeck("one");
        mDecks.rename(mDecks.get(id), "yo");
        names = mDecks.allSortedNames();
        for (String n : new String[] {"yo", "yo::two", "yo::two::three"}) {
            assertTrue(names.contains(n));
        }
        // over filtered
        long filteredId = addDynamicDeck("filtered");
        Deck filtered = mDecks.get(filteredId);
        long childId = addDeck("child");
        Deck child = mDecks.get(childId);
        assertThrows(DeckRenameException.class, () -> mDecks.rename(child, "filtered::child"));
        assertThrows(DeckRenameException.class, () -> mDecks.rename(child, "FILTERED::child"));
    }

    /* TODO: maybe implement. We don't drag and drop here anyway, so buggy implementation is okay
     @Test public void test_renameForDragAndDrop() throws DeckRenameException {
     // TODO: upstream does not return "default", remove it
     long languages_did = addDeck("Languages");
     long chinese_did = addDeck("Chinese");
     long hsk_did = addDeck("Chinese::HSK");

     // Renaming also renames children
     mDecks.renameForDragAndDrop(chinese_did, languages_did);
     assertEqualsArrayList(new String [] {"Default", "Languages", "Languages::Chinese", "Languages::Chinese::HSK"}, mDecks.allSortedNames());

     // Dragging a col onto itself is a no-op
     mDecks.renameForDragAndDrop(languages_did, languages_did);
     assertEqualsArrayList(new String [] {"Default", "Languages", "Languages::Chinese", "Languages::Chinese::HSK"}, mDecks.allSortedNames());

     // Dragging a col onto its parent is a no-op
     mDecks.renameForDragAndDrop(hsk_did, chinese_did);
     assertEqualsArrayList(new String [] {"Default", "Languages", "Languages::Chinese", "Languages::Chinese::HSK"}, mDecks.allSortedNames());

     // Dragging a col onto a descendant is a no-op
     mDecks.renameForDragAndDrop(languages_did, hsk_did);
     // TODO: real problem to correct, even if we don't have drag and drop
     // assertEqualsArrayList(new String [] {"Default", "Languages", "Languages::Chinese", "Languages::Chinese::HSK"}, mDecks.allSortedNames());

     // Can drag a grandchild onto its grandparent.  It becomes a child
     mDecks.renameForDragAndDrop(hsk_did, languages_did);
     assertEqualsArrayList(new String [] {"Default", "Languages", "Languages::Chinese", "Languages::HSK"}, mDecks.allSortedNames());

     // Can drag a col onto its sibling
     mDecks.renameForDragAndDrop(hsk_did, chinese_did);
     assertEqualsArrayList(new String [] {"Default", "Languages", "Languages::Chinese", "Languages::Chinese::HSK"}, mDecks.allSortedNames());

     // Can drag a col back to the top level
     mDecks.renameForDragAndDrop(chinese_did, null);
     assertEqualsArrayList(new String [] {"Default", "Chinese", "Chinese::HSK", "Languages"}, mDecks.allSortedNames());

     // Dragging a top level col to the top level is a no-op
     mDecks.renameForDragAndDrop(chinese_did, null);
     assertEqualsArrayList(new String [] {"Default", "Chinese", "Chinese::HSK", "Languages"}, mDecks.allSortedNames());

     // mDecks are renamed if necessary«
     long new_hsk_did = addDeck("hsk");
     mDecks.renameForDragAndDrop(new_hsk_did, chinese_did);
     assertEqualsArrayList(new String [] {"Default", "Chinese", "Chinese::HSK", "Chinese::hsk+", "Languages"}, mDecks.allSortedNames());
     mDecks.rem(new_hsk_did);

     }
     */

    @Test
    public void curDeckIsLong() throws FilteredAncestor {
        // Regression for #8092
        long id = addDeck("test");
        mDecks.select(id);
        assertThat("curDeck should be saved as a long. A deck id.", mCol.getConf().get("curDeck") instanceof Long);
    }


    @Test
    public void isDynStd() {
        long filteredId = addDynamicDeck("filtered");
        Deck filtered = mDecks.get(filteredId);
        long deckId = addDeck("deck");
        Deck deck = mDecks.get(deckId);
        assertThat(deck.isStd(), is(Boolean.valueOf(true)));
        assertThat(deck.isDyn(), is(Boolean.valueOf(false)));
        assertThat(filtered.isStd(), is(Boolean.valueOf(false)));
        assertThat(filtered.isDyn(), is(Boolean.valueOf(true)));

        DeckConfig filtered_config = mDecks.confForDid(filteredId);
        DeckConfig deck_config = mDecks.confForDid(deckId);
        assertThat(deck_config.isStd(), is(Boolean.valueOf(true)));
        assertThat(deck_config.isDyn(), is(Boolean.valueOf(false)));
        assertThat(filtered_config.isStd(), is(Boolean.valueOf(false)));
        assertThat(filtered_config.isDyn(), is(Boolean.valueOf(true)));

    }

    @Test
    public void testEnsureParents() throws FilteredAncestor {
        addDeck("test");
        String subsubdeck_name = mDecks._ensureParents("  tESt :: sub :: subdeck");
        assertEquals("test::sub:: subdeck", subsubdeck_name);// Only parents are renamed, not the last deck.
        assertNotNull(mDecks.byName("test::sub"));
        assertNull(mDecks.byName("test::sub:: subdeck"));
        assertNull(mDecks.byName("  test :: sub :: subdeck"));
        assertNull(mDecks.byName("  test :: sub "));

        mDecks.newDyn("filtered");
        assertThrows(FilteredAncestor.class, () -> mDecks._ensureParents("filtered:: sub :: subdeck"));
    }

    @Test
    public void descendantOfFiltered() throws FilteredAncestor {
        long filtered_id = mDecks.newDyn("filtered");
        assertThrows(FilteredAncestor.class,  () -> mDecks.id("filtered::subdeck::subsubdeck"));

        Long subdeck_id = mDecks.id_safe("filtered::subdeck::subsubdeck");
        Deck subdeck = mDecks.get(subdeck_id);
        assertEquals("filtered'::subdeck::subsubdeck", subdeck.getString("name"));
    }

    @Test
    public void testEnsureParentsNotFiltered() throws FilteredAncestor {
        addDeck("test");
        String subsubdeck_name = mDecks._ensureParentsNotFiltered("  tESt :: sub :: subdeck");
        assertEquals("test::sub:: subdeck", subsubdeck_name);// Only parents are renamed, not the last deck.
        assertNotNull(mDecks.byName("test::sub"));
        assertNull(mDecks.byName("test::sub:: subdeck"));
        assertNull(mDecks.byName("  test :: sub :: subdeck"));
        assertNull(mDecks.byName("  test :: sub "));

        mDecks.newDyn("filtered");
        String filtered_subdeck_name = mDecks._ensureParentsNotFiltered("filtered:: sub :: subdeck");
        assertEquals("filtered'::sub:: subdeck", filtered_subdeck_name);// Only parents are renamed, not the last deck.
        assertNotNull(mDecks.byName("filtered'::sub"));
        assertNotNull(mDecks.byName("filtered'"));
        assertNull(mDecks.byName("filtered::sub:: subdeck"));
        assertNull(mDecks.byName("filtered::sub"));
    }

}
