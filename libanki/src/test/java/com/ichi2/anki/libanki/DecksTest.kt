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
package com.ichi2.anki.libanki

import android.annotation.SuppressLint
import com.ichi2.anki.libanki.Decks.Companion.CURRENT_DECK
import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import com.ichi2.anki.libanki.testutils.ext.addNote
import com.ichi2.anki.libanki.testutils.ext.newNote
import net.ankiweb.rsdroid.exceptions.BackendDeckIsFilteredException
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DecksTest : InMemoryAnkiTest() {
    @Test
    fun test_remove() {
        // create a new col, and add a note/card to it
        val deck1 = addDeck("deck1")
        val note = col.newNote()
        note.setItem("Front", "1")
        note.notetype.did = deck1
        col.addNote(note)
        val c = note.cards()[0]
        assertEquals(deck1, c.did)
        assertEquals(1, col.cardCount().toLong())
        col.decks.remove(listOf(deck1))
        assertEquals(0, col.cardCount().toLong())
        // if we try to get it, we get the default
        assertEquals("[no deck]", col.decks.name(c.did))
    }

    @Test
    @SuppressLint("CheckResult")
    fun test_rename() {
        var id = addDeck("hello::world")
        // should be able to rename into a completely different branch, creating
        // parents as necessary
        val decks = col.decks
        decks.rename(decks.getLegacy(id)!!, "foo::bar")
        var names: List<String> = decks.allNamesAndIds().map { it.name }
        assertTrue(names.contains("foo"))
        assertTrue(names.contains("foo::bar"))
        assertFalse(names.contains("hello::world"))
        // create another col

        /* TODO: do we want to follow upstream here ?
         // automatically adjusted if a duplicate name
         decks.rename(decks.get(id), "FOO");
         names =  decks.allSortedNames();
         assertThat(names, containsString("FOO+"));
         */

        // when renaming, the children should be renamed too
        addDeck("one::two::three")
        id = addDeck("one")
        col.decks.rename(col.decks.getLegacy(id)!!, "yo")
        names = decks.allNamesAndIds().map { it.name }
        for (n in arrayOf("yo", "yo::two", "yo::two::three")) {
            assertTrue(names.contains(n))
        }
        // over filtered
        val filteredId = addDynamicDeck("filtered")
        col.decks.getLegacy(filteredId)
        val childId = addDeck("child")
        val child = col.decks.getLegacy(childId)!!
        assertThrows(BackendDeckIsFilteredException::class.java) {
            col.decks.rename(
                child,
                "filtered::child",
            )
        }
        assertThrows(BackendDeckIsFilteredException::class.java) {
            col.decks.rename(
                child,
                "FILTERED::child",
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
        addDeck("test", setAsSelected = true)
        assertDoesNotThrow("curDeck should be saved as a long. A deck id.") {
            col.config.get<DeckId>(
                CURRENT_DECK,
            )
        }
    }

    @Test
    fun isDynStd() {
        val decks = col.decks
        val filteredId = addDynamicDeck("filtered")
        val filtered = decks.getLegacy(filteredId)!!
        val deckId = addDeck("deck")
        val deck = decks.getLegacy(deckId)!!
        assertThat(deck.isNormal, equalTo(true))
        assertThat(deck.isFiltered, equalTo(false))
        assertThat(filtered.isNormal, equalTo(false))
        assertThat(filtered.isFiltered, equalTo(true))
    }

    @Test
    fun testCardCount() {
        val decks = col.decks
        var addedNoteCount = 0

        fun addNote(did: DeckId): NoteId {
            val note =
                col.newNote().apply {
                    setItem("Front", (++addedNoteCount).toString())
                    notetype.did = did
                }
            col.addNote(note)
            return note.id
        }

        val parentDid = addDeck("Deck").also { did -> addNote(did) }
        val childDid = addDeck("Deck::Subdeck").also { did -> addNote(did) }

        val noteToMakeDynamic: NoteId
        val deckWithNoChildren =
            addDeck("DeckWithTwo").also { did ->
                addNote(did)
                addNote(did)
                noteToMakeDynamic = addNote(did)
            }
        val filteredDeck = addDynamicDeck("filtered", search = "nid:$noteToMakeDynamic")

        assertThat("all decks", decks.cardCount(parentDid, childDid, deckWithNoChildren, includeSubdecks = false), equalTo(5))
        assertThat("all decks with subdecks", decks.cardCount(parentDid, childDid, deckWithNoChildren, includeSubdecks = true), equalTo(5))

        assertThat("top level decks, no children", decks.cardCount(parentDid, deckWithNoChildren, includeSubdecks = false), equalTo(4))
        assertThat("top level decks, with children", decks.cardCount(parentDid, deckWithNoChildren, includeSubdecks = true), equalTo(5))

        assertThat("parent deck, no children", decks.cardCount(parentDid, includeSubdecks = false), equalTo(1))
        assertThat("parent deck, with children", decks.cardCount(parentDid, includeSubdecks = true), equalTo(2))

        assertThat("single deck, multiple cards, no children", decks.cardCount(deckWithNoChildren, includeSubdecks = false), equalTo(3))
        assertThat("single deck, multiple cards, with children", decks.cardCount(deckWithNoChildren, includeSubdecks = true), equalTo(3))

        assertThat("filtered deck", decks.cardCount(filteredDeck, includeSubdecks = false), equalTo(1))

        assertThat("filtered and home deck", decks.cardCount(deckWithNoChildren, filteredDeck, includeSubdecks = false), equalTo(3))
    }

    @Test
    fun test_decksUsingConfig() {
        val decks = col.decks

        // Create a custom deck config
        val customConfig = decks.addConfigReturningId("Custom Config")
        val customConfigObj = decks.getConfig(customConfig)!!

        // Create multiple decks with different configs
        val deck1 = addDeck("Deck1")
        val deck2 = addDeck("Deck2")
        val deck3 = addDeck("Deck3")
        val deck4 = addDeck("Deck4")

        // Assign custom config to deck1 and deck3
        val deck1Obj = decks.getLegacy(deck1)!!
        deck1Obj.put("conf", customConfig)
        decks.save(deck1Obj)

        val deck3Obj = decks.getLegacy(deck3)!!
        deck3Obj.put("conf", customConfig)
        decks.save(deck3Obj)

        // deck2 and deck4 should use default config (id = 1)
        // Get decks using the custom config
        val decksUsingCustom = decks.decksUsingConfig(customConfigObj)

        // Should return exactly deck1 and deck3
        assertThat(decksUsingCustom.size, equalTo(2))
        assertTrue(decksUsingCustom.contains(deck1))
        assertTrue(decksUsingCustom.contains(deck3))
        assertFalse(decksUsingCustom.contains(deck2))
        assertFalse(decksUsingCustom.contains(deck4))

        // Test with default config
        val defaultConfig = decks.getConfig(1)!!
        val decksUsingDefault = decks.decksUsingConfig(defaultConfig)

        // Should include deck2, deck4, and the default deck
        assertTrue(decksUsingDefault.contains(deck2))
        assertTrue(decksUsingDefault.contains(deck4))
        assertFalse(decksUsingDefault.contains(deck1))
        assertFalse(decksUsingDefault.contains(deck3))
    }

    @Test
    fun test_decksUsingConfig_corruptDeckWithDconf1() {
        val decks = col.decks

        // Create a custom config with id = 1 (simulating corrupt state)
        // This tests the edge case mentioned in @NeedsTest annotation
        val defaultConfig = decks.getConfig(1)!!

        // Create several decks
        val deck1 = addDeck("NormalDeck1")
        val deck2 = addDeck("NormalDeck2")

        // Create a custom config
        val customConfigId = decks.addConfigReturningId("Custom")
        val customConfig = decks.getConfig(customConfigId)!!

        // Assign custom config to deck1
        val deck1Obj = decks.getLegacy(deck1)!!
        deck1Obj.put("conf", customConfigId)
        decks.save(deck1Obj)

        // deck2 keeps default config (dconf = 1)

        // When querying for default config (id = 1), should only get decks actually using it
        val decksWithDefaultConfig = decks.decksUsingConfig(defaultConfig)

        // Should include deck2 and default deck, but NOT deck1
        assertTrue(decksWithDefaultConfig.contains(deck2))
        assertFalse(decksWithDefaultConfig.contains(deck1))

        // When querying for custom config, should only get deck1
        val decksWithCustomConfig = decks.decksUsingConfig(customConfig)
        assertTrue(decksWithCustomConfig.contains(deck1))
        assertFalse(decksWithCustomConfig.contains(deck2))

        // Ensure no deck appears in both lists
        val intersection = decksWithDefaultConfig.intersect(decksWithCustomConfig.toSet())
        assertTrue("No deck should use multiple configs simultaneously", intersection.isEmpty())
    }

    @Test
    fun test_decksUsingConfig_emptyResult() {
        val decks = col.decks
        val unusedConfigId = decks.addConfigReturningId("Unused Config")
        val unusedConfig = decks.getConfig(unusedConfigId)!!
        val result = decks.decksUsingConfig(unusedConfig)
        assertThat(result.size, equalTo(0))
    }

    @Test
    fun test_decksUsingConfig_afterConfigReassignment() {
        val decks = col.decks
        val config1Id = decks.addConfigReturningId("Config1")
        val config1 = decks.getConfig(config1Id)!!
        val config2Id = decks.addConfigReturningId("Config2")
        val config2 = decks.getConfig(config2Id)!!
        val deckId = addDeck("TestDeck")
        val deck = decks.getLegacy(deckId)!!
        deck.put("conf", config1Id)
        decks.save(deck)

        var decksWithConfig1 = decks.decksUsingConfig(config1)
        assertTrue(decksWithConfig1.contains(deckId))

        var decksWithConfig2 = decks.decksUsingConfig(config2)
        assertFalse(decksWithConfig2.contains(deckId))

        deck.put("conf", config2Id)
        decks.save(deck)
        decksWithConfig1 = decks.decksUsingConfig(config1)
        assertFalse(decksWithConfig1.contains(deckId))
        decksWithConfig2 = decks.decksUsingConfig(config2)
        assertTrue(decksWithConfig2.contains(deckId))
    }

    @Test
    fun test_decksUsingConfig_filteredDecksExcluded() {
        val decks = col.decks
        val customConfigId = decks.addConfigReturningId("Custom")
        val customConfig = decks.getConfig(customConfigId)!!
        val normalDeckId = addDeck("NormalDeck")
        val normalDeck = decks.getLegacy(normalDeckId)!!
        normalDeck.put("conf", customConfigId)
        decks.save(normalDeck)
        val filteredDeckId = addDynamicDeck("FilteredDeck")
        val decksWithConfig = decks.decksUsingConfig(customConfig)
        assertTrue(decksWithConfig.contains(normalDeckId))
        assertFalse(decksWithConfig.contains(filteredDeckId))
    }

    @Test
    fun test_parentsByName_noParents() {
        val decks = col.decks

        // Deck without "::" should return empty list
        val result = decks.parentsByName("SingleDeck")

        assertThat(result.size, equalTo(0))
        assertTrue(result.isEmpty())
    }

    @Test
    fun test_parentsByName_singleLevelParent() {
        val decks = col.decks

        // Create parent deck
        val parentId = addDeck("Parent")

        // Query for child that doesn't exist yet
        val childName = "Parent::Child"
        val parents = decks.parentsByName(childName)

        // Should return the parent deck
        assertThat(parents.size, equalTo(1))
        assertEquals(parentId, parents[0].id)
        assertEquals("Parent", parents[0].name)
    }

    @Test
    fun test_parentsByName_multipleLevels() {
        val decks = col.decks

        // Create nested hierarchy: A -> A::B -> A::B::C
        val deckA = addDeck("A")
        val deckAB = addDeck("A::B")

        // Query for A::B::C (which doesn't exist yet)
        val parents = decks.parentsByName("A::B::C")

        // Should return both A and A::B in order
        assertThat(parents.size, equalTo(2))
        assertEquals(deckA, parents[0].id)
        assertEquals("A", parents[0].name)
        assertEquals(deckAB, parents[1].id)
        assertEquals("A::B", parents[1].name)
    }

    @Test
    fun test_parentsByName_deepHierarchy() {
        val decks = col.decks

        // Create deep hierarchy: Level1 -> Level1::Level2 -> Level1::Level2::Level3
        val level1Id = addDeck("Level1")
        val level2Id = addDeck("Level1::Level2")
        val level3Id = addDeck("Level1::Level2::Level3")

        // Query for Level1::Level2::Level3::Level4
        val parents = decks.parentsByName("Level1::Level2::Level3::Level4")

        // Should return all three parent decks in order
        assertThat(parents.size, equalTo(3))
        assertEquals(level1Id, parents[0].id)
        assertEquals("Level1", parents[0].name)
        assertEquals(level2Id, parents[1].id)
        assertEquals("Level1::Level2", parents[1].name)
        assertEquals(level3Id, parents[2].id)
        assertEquals("Level1::Level2::Level3", parents[2].name)
    }

    @Test
    fun test_parentsByName_missingIntermediateParent() {
        val decks = col.decks

        // Create only Level1 and Level1::Level2::Level3 (skip Level1::Level2)
        val level1Id = addDeck("Level1")
        val level3Id = addDeck("Level1::Level2::Level3")

        // Query for Level1::Level2::Level3::Level4
        val parents = decks.parentsByName("Level1::Level2::Level3::Level4")

        // Should return only the parents that exist: Level1 and Level1::Level2::Level3
        // Level1::Level2 doesn't exist so it should be skipped
        assertThat(parents.size, equalTo(3))
        assertEquals(level1Id, parents[0].id)
        assertEquals("Level1", parents[0].name)
        assertEquals(level3Id, parents[2].id)
        assertEquals("Level1::Level2::Level3", parents[2].name)
    }

    @Test
    fun test_parentsByName_noExistingParents() {
        val decks = col.decks

        // Query for a nested deck where no parents exist
        val parents = decks.parentsByName("NonExistent::Parent::Child")

        // Should return empty list since no parent decks exist
        assertThat(parents.size, equalTo(0))
        assertTrue(parents.isEmpty())
    }

    @Test
    fun test_parentsByName_existingDeck() {
        val decks = col.decks

        // Create full hierarchy
        val deckA = addDeck("A")
        val deckAB = addDeck("A::B")
        val deckABC = addDeck("A::B::C")

        // Query for existing deck A::B::C
        val parents = decks.parentsByName("A::B::C")

        // Should return parents A and A::B, but not A::B::C itself
        assertThat(parents.size, equalTo(2))
        assertEquals(deckA, parents[0].id)
        assertEquals(deckAB, parents[1].id)
        assertFalse(parents.any { it.id == deckABC })
    }

    @Test
    fun test_parentsByName_specialCharactersInName() {
        val decks = col.decks

        // Create parent with special characters
        val parentId = addDeck("Parent-Name_123")

        // Query for child
        val parents = decks.parentsByName("Parent-Name_123::Child")

        // Should handle special characters correctly
        assertThat(parents.size, equalTo(1))
        assertEquals(parentId, parents[0].id)
        assertEquals("Parent-Name_123", parents[0].name)
    }

    @Test
    fun test_parentsByName_orderPreserved() {
        val decks = col.decks

        // Create hierarchy in specific order
        val deck1 = addDeck("First")
        val deck2 = addDeck("First::Second")
        val deck3 = addDeck("First::Second::Third")
        val deck4 = addDeck("First::Second::Third::Fourth")

        // Query for deepest child
        val parents = decks.parentsByName("First::Second::Third::Fourth::Fifth")

        // Should return parents in hierarchical order from root to deepest
        assertThat(parents.size, equalTo(4))
        assertEquals(deck1, parents[0].id)
        assertEquals(deck2, parents[1].id)
        assertEquals(deck3, parents[2].id)
        assertEquals(deck4, parents[3].id)
        // Verify names are in correct order
        assertEquals("First", parents[0].name)
        assertEquals("First::Second", parents[1].name)
        assertEquals("First::Second::Third", parents[2].name)
        assertEquals("First::Second::Third::Fourth", parents[3].name)
    }

    @Test
    fun test_parentsByName_emptyStringHandling() {
        val decks = col.decks

        // Edge case: empty string should not contain "::" and return empty list
        val parents = decks.parentsByName("")

        assertThat(parents.size, equalTo(0))
        assertTrue(parents.isEmpty())
    }

    @Test
    fun test_parentsByName_doubleColonOnly() {
        val decks = col.decks

        // Edge case: just "::" should be handled
        val parents = decks.parentsByName("::")

        // Should return empty list (or handle gracefully)
        assertTrue(parents.isEmpty())
    }
}
