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
package com.ichi2.libanki

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.libanki.Decks.Companion.CURRENT_DECK
import com.ichi2.testutils.AnkiAssert.assertDoesNotThrow
import com.ichi2.testutils.JvmTest
import net.ankiweb.rsdroid.exceptions.BackendDeckIsFilteredException
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DecksTest : JvmTest() {
    @Test
    fun test_remove() {
        val col = col
        // create a new col, and add a note/card to it
        val deck1 = addDeck("deck1")
        val note = col.newNote()
        note.setItem("Front", "1")
        note.notetype.put("did", deck1)
        col.addNote(note)
        val c = note.cards()[0]
        assertEquals(deck1, c.did)
        assertEquals(1, col.cardCount().toLong())
        col.decks.removeDecks(listOf(deck1))
        assertEquals(0, col.cardCount().toLong())
        // if we try to get it, we get the default
        assertEquals("[no deck]", col.decks.name(c.did))
    }

    @Test
    @SuppressLint("CheckResult")
    fun test_rename() {
        val col = col
        var id = addDeck("hello::world")
        // should be able to rename into a completely different branch, creating
        // parents as necessary
        val decks = col.decks
        decks.rename(decks.get(id)!!, "foo::bar")
        var names: List<String> = decks.allNamesAndIds().map { it.name }
        assertTrue(names.contains("foo"))
        assertTrue(names.contains("foo::bar"))
        assertFalse(names.contains("hello::world"))
        // create another col
        /* TODO: do we want to follow upstream here ?
         // automatically adjusted if a duplicate name
         decks.rename(decks.get(id), "FOO");
         names =  decks.allSortedNames();
         assertThat(names, containsString("FOO+"));

          */
        // when renaming, the children should be renamed too
        addDeck("one::two::three")
        id = addDeck("one")
        col.decks.rename(col.decks.get(id)!!, "yo")
        names = decks.allNamesAndIds().map { it.name }
        for (n in arrayOf("yo", "yo::two", "yo::two::three")) {
            assertTrue(names.contains(n))
        }
        // over filtered
        val filteredId = addDynamicDeck("filtered")
        col.decks.get(filteredId)
        val childId = addDeck("child")
        val child = col.decks.get(childId)!!
        assertThrows(BackendDeckIsFilteredException::class.java) {
            col.decks.rename(
                child,
                "filtered::child"
            )
        }
        assertThrows(BackendDeckIsFilteredException::class.java) {
            col.decks.rename(
                child,
                "FILTERED::child"
            )
        }
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
    fun curDeckIsLong() {
        // Regression for #8092
        val col = col
        val decks = col.decks
        val id = addDeck("test")
        decks.select(id)
        assertDoesNotThrow("curDeck should be saved as a long. A deck id.") {
            col.config.get<DeckId>(
                CURRENT_DECK
            )
        }
    }

    @Test
    fun isDynStd() {
        val col = col
        val decks = col.decks
        val filteredId = addDynamicDeck("filtered")
        val filtered = decks.get(filteredId)!!
        val deckId = addDeck("deck")
        val deck = decks.get(deckId)!!
        assertThat(deck.isNormal, equalTo(true))
        assertThat(deck.isFiltered, equalTo(false))
        assertThat(filtered.isNormal, equalTo(false))
        assertThat(filtered.isFiltered, equalTo(true))
    }

    companion object {
        // Used in other class to populate decks.
        @Suppress("SpellCheckingInspection")
        val TEST_DECKS = arrayOf(
            "scxipjiyozczaaczoawo",
            "cmxieunwoogyxsctnjmv::abcdefgh::ZYXW",
            "cmxieunwoogyxsctnjmv::INSBGDS",
            "::foobar", // Addition test for issue #11026
            "A::"
        )
    }
}
