/****************************************************************************************
 * Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>                        *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.dialogs

import android.widget.EditText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.afollestad.materialdialogs.DialogAction
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.libanki.DeckManager
import com.ichi2.libanki.backend.exception.DeckRenameException
import org.hamcrest.MatcherAssert
import org.hamcrest.core.Is
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.Collectors
import java.util.stream.IntStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class CreateDeckDialogTest : RobolectricTest() {
    private var mActivityScenario: ActivityScenario<DeckPicker>? = null
    override fun setUp() {
        super.setUp()
        ensureCollectionLoadIsSynchronous()
        mActivityScenario = ActivityScenario.launch(DeckPicker::class.java)
        val activityScenario: ActivityScenario<DeckPicker>? = mActivityScenario
        activityScenario?.moveToState(Lifecycle.State.STARTED)
    }

    @Test
    fun testCreateFilteredDeckFunction() {
        mActivityScenario!!.onActivity { activity: DeckPicker ->
            val createDeckDialog = CreateDeckDialog(activity, R.string.new_deck, CreateDeckDialog.DeckDialogType.FILTERED_DECK, null)
            val isCreated = AtomicReference(false)
            val deckName = "filteredDeck"
            advanceRobolectricLooper()
            createDeckDialog.setOnNewDeckCreated { id: Long ->
                // a deck was created
                try {
                    isCreated.set(true)
                    val decks: DeckManager = activity.col!!.decks
                    MatcherAssert.assertThat(id, Is.`is`(decks.id(deckName)))
                } catch (filteredAncestor: DeckRenameException) {
                    throw RuntimeException(filteredAncestor)
                }
            }
            createDeckDialog.createFilteredDeck(deckName)
            MatcherAssert.assertThat(isCreated.get(), Is.`is`(true))
        }
    }

    @Test
    @Throws(DeckRenameException::class)
    fun testCreateSubDeckFunction() {
        val deckParentId = col.decks.id("Deck Name")
        mActivityScenario!!.onActivity { activity: DeckPicker ->
            val createDeckDialog = CreateDeckDialog(activity, R.string.new_deck, CreateDeckDialog.DeckDialogType.SUB_DECK, deckParentId)
            val isCreated = AtomicReference(false)
            val deckName = "filteredDeck"
            advanceRobolectricLooper()
            createDeckDialog.setOnNewDeckCreated { id: Long ->
                try {
                    isCreated.set(true)
                    val decks: DeckManager = activity.col!!.decks
                    val deckNameWithParentName = decks.getSubdeckName(deckParentId, deckName)
                    MatcherAssert.assertThat(id, Is.`is`(decks.id(deckNameWithParentName!!)))
                } catch (filteredAncestor: DeckRenameException) {
                    throw RuntimeException(filteredAncestor)
                }
            }
            createDeckDialog.createSubDeck(deckParentId, deckName)
            MatcherAssert.assertThat(isCreated.get(), Is.`is`(true))
        }
    }

    @Test
    fun testCreateDeckFunction() {
        mActivityScenario!!.onActivity { activity: DeckPicker ->
            val createDeckDialog = CreateDeckDialog(activity, R.string.new_deck, CreateDeckDialog.DeckDialogType.DECK, null)
            val isCreated = AtomicReference(false)
            val deckName = "Deck Name"
            advanceRobolectricLooper()
            createDeckDialog.setOnNewDeckCreated { id: Long ->
                // a deck was created
                isCreated.set(true)
                val decks: DeckManager? = activity.col?.decks
                MatcherAssert.assertThat(id, Is.`is`(decks?.byName(deckName)!!.getLong("id")))
            }
            createDeckDialog.createDeck(deckName)
            MatcherAssert.assertThat(isCreated.get(), Is.`is`(true))
        }
    }

    @Test
    fun testRenameDeckFunction() {
        val deckName = "Deck Name"
        val deckNewName = "New Deck Name"
        mActivityScenario!!.onActivity { activity: DeckPicker ->
            val createDeckDialog = CreateDeckDialog(activity, R.string.new_deck, CreateDeckDialog.DeckDialogType.RENAME_DECK, null)
            createDeckDialog.deckName = deckName
            val isCreated = AtomicReference(false)
            advanceRobolectricLooper()
            createDeckDialog.setOnNewDeckCreated { id: Long? ->
                // a deck name was renamed
                isCreated.set(true)
                val decks: DeckManager = activity.col!!.decks
                MatcherAssert.assertThat(deckNewName, Is.`is`(decks.name(id!!)))
            }
            createDeckDialog.renameDeck(deckNewName)
            MatcherAssert.assertThat(isCreated.get(), Is.`is`(true))
        }
    }

    @Test
    fun nameMayNotBeZeroLength() {
        mActivityScenario!!.onActivity { activity: DeckPicker? ->
            val createDeckDialog = CreateDeckDialog(activity!!, R.string.new_deck, CreateDeckDialog.DeckDialogType.DECK, null)
            val materialDialog = createDeckDialog.showDialog()
            val actionButton = materialDialog.getActionButton(DialogAction.POSITIVE)
            MatcherAssert.assertThat("Ok is disabled if zero length input", actionButton.isEnabled, Is.`is`(false))
            val editText: EditText? = Objects.requireNonNull(materialDialog.inputEditText)
            editText?.setText("NotEmpty")
            MatcherAssert.assertThat("Ok is enabled if not zero length input", actionButton.isEnabled, Is.`is`(true))
        }
    }

    @Test
    fun searchDecksIconVisibilityDeckCreationTest() {
        mActivityScenario!!.onActivity { deckPicker ->
            val deckCounter = AtomicInteger(1)
            for (i in 0 until 10) {
                val createDeckDialog = CreateDeckDialog(deckPicker, R.string.new_deck, CreateDeckDialog.DeckDialogType.DECK, null)
                createDeckDialog.setOnNewDeckCreated { did ->
                    assertEquals(deckCounter.incrementAndGet(), deckPicker.col.decks.count())

                    deckPicker.updateDeckList()
                    assertEquals(deckCounter.get() - 1, deckPicker.deckCount)

                    assertEquals(deckPicker.mSearchDecksIcon!!.isVisible, deckPicker.deckCount >= 10)

                    // After the last deck was created, delete a deck
                    if (deckPicker.deckCount >= 10) {
                        deckPicker.confirmDeckDeletion(did)
                        assertEquals(deckCounter.decrementAndGet(), deckPicker.col.decks.count())

                        deckPicker.updateDeckList()
                        assertEquals(deckCounter.get() - 1, deckPicker.deckCount)

                        assertFalse(deckPicker.mSearchDecksIcon!!.isVisible)
                    }
                }
                createDeckDialog.createDeck("Deck$i")
            }
        }
    }

    @Test
    fun searchDecksIconVisibilitySubdeckCreationTest() {
        mActivityScenario!!.onActivity { deckPicker ->
            var createDeckDialog = CreateDeckDialog(deckPicker, R.string.new_deck, CreateDeckDialog.DeckDialogType.DECK, null)
            val decks = deckPicker.col.decks
            createDeckDialog.setOnNewDeckCreated {
                assertEquals(11, decks.count())
                deckPicker.updateDeckList()
                assertEquals(10, deckPicker.deckCount)
                assertTrue(deckPicker.mSearchDecksIcon!!.isVisible)

                deckPicker.confirmDeckDeletion(decks.id("Deck0::Deck1"))
                assertEquals(2, decks.count())
                deckPicker.updateDeckList()
                assertEquals(1, deckPicker.deckCount)
                assertFalse(deckPicker.mSearchDecksIcon!!.isVisible)
            }
            createDeckDialog.createDeck(deckTreeName(0, 9, "Deck"))

            createDeckDialog = CreateDeckDialog(deckPicker, R.string.new_deck, CreateDeckDialog.DeckDialogType.DECK, null)
            createDeckDialog.setOnNewDeckCreated {
                assertEquals(13, decks.count())
                deckPicker.updateDeckList()
                assertEquals(12, deckPicker.deckCount)
                assertTrue(deckPicker.mSearchDecksIcon!!.isVisible)

                deckPicker.confirmDeckDeletion(decks.id("Deck0::Deck1"))
                assertEquals(2, decks.count())
                deckPicker.updateDeckList()
                assertEquals(1, deckPicker.deckCount)
                assertFalse(deckPicker.mSearchDecksIcon!!.isVisible)
            }
            createDeckDialog.createDeck(deckTreeName(0, 11, "Deck"))

            createDeckDialog = CreateDeckDialog(deckPicker, R.string.new_deck, CreateDeckDialog.DeckDialogType.DECK, null)
            createDeckDialog.setOnNewDeckCreated {
                assertEquals(7, decks.count())
                deckPicker.updateDeckList()
                assertEquals(6, deckPicker.deckCount)
                assertFalse(deckPicker.mSearchDecksIcon!!.isVisible)
            }
            createDeckDialog.createDeck(deckTreeName(0, 5, "Deck"))

            createDeckDialog = CreateDeckDialog(deckPicker, R.string.new_deck, CreateDeckDialog.DeckDialogType.DECK, null)
            createDeckDialog.setOnNewDeckCreated {
                assertEquals(13, decks.count())
                deckPicker.updateDeckList()
                assertEquals(12, deckPicker.deckCount)
                assertTrue(deckPicker.mSearchDecksIcon!!.isVisible)
            }
            createDeckDialog.createDeck(deckTreeName(6, 11, "Deck"))
        }
    }

    private fun deckTreeName(startInclusive: Int, endInclusive: Int, prefix: String): String {
        return Arrays.stream(IntStream.rangeClosed(startInclusive, endInclusive).toArray())
            .mapToObj { i: Int -> prefix + i }
            .collect(Collectors.joining("::"))
    }
}
