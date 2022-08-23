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

import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.IntroductionActivity
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.CreateDeckDialog.DeckDialogType
import com.ichi2.anki.dialogs.utils.input
import com.ichi2.anki.dialogs.utils.positiveButton
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.DeckManager
import com.ichi2.libanki.backend.exception.DeckRenameException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.*
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CreateDeckDialogTest : RobolectricTest() {
    private var mActivityScenario: ActivityScenario<DeckPicker>? = null
    override fun setUp() {
        super.setUp()
        getPreferences().edit { putBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, true) }
        ensureCollectionLoadIsSynchronous()
        mActivityScenario = ActivityScenario.launch(DeckPicker::class.java)
        val activityScenario: ActivityScenario<DeckPicker>? = mActivityScenario
        activityScenario?.moveToState(Lifecycle.State.STARTED)
    }

    @Test
    fun testCreateFilteredDeckFunction() {
        mActivityScenario!!.onActivity { activity: DeckPicker ->
            val createDeckDialog = CreateDeckDialog(activity, R.string.new_deck, DeckDialogType.FILTERED_DECK, null)
            val isCreated = AtomicReference(false)
            val deckName = "filteredDeck"
            advanceRobolectricLooper()
            createDeckDialog.setOnNewDeckCreated { id: Long ->
                // a deck was created
                try {
                    isCreated.set(true)
                    val decks: DeckManager = activity.col.decks
                    assertThat(id, equalTo(decks.id(deckName)))
                } catch (filteredAncestor: DeckRenameException) {
                    throw RuntimeException(filteredAncestor)
                }
            }
            createDeckDialog.createFilteredDeck(deckName)
            assertThat(isCreated.get() as Boolean, equalTo(true))
        }
    }

    @Test
    @Throws(DeckRenameException::class)
    fun testCreateSubDeckFunction() {
        val deckParentId = col.decks.id("Deck Name")
        mActivityScenario!!.onActivity { activity: DeckPicker ->
            val createDeckDialog = CreateDeckDialog(activity, R.string.new_deck, DeckDialogType.SUB_DECK, deckParentId)
            val isCreated = AtomicReference(false)
            val deckName = "filteredDeck"
            advanceRobolectricLooper()
            createDeckDialog.setOnNewDeckCreated { id: Long ->
                try {
                    isCreated.set(true)
                    val decks: DeckManager = activity.col.decks
                    val deckNameWithParentName = decks.getSubdeckName(deckParentId, deckName)
                    assertThat(id, equalTo(decks.id(deckNameWithParentName!!)))
                } catch (filteredAncestor: DeckRenameException) {
                    throw RuntimeException(filteredAncestor)
                }
            }
            createDeckDialog.createSubDeck(deckParentId, deckName)
            assertThat(isCreated.get(), equalTo(true))
        }
    }

    @Test
    fun testCreateDeckFunction() {
        mActivityScenario!!.onActivity { activity: DeckPicker ->
            val createDeckDialog = CreateDeckDialog(activity, R.string.new_deck, DeckDialogType.DECK, null)
            val isCreated = AtomicReference(false)
            val deckName = "Deck Name"
            advanceRobolectricLooper()
            createDeckDialog.setOnNewDeckCreated { id: Long ->
                // a deck was created
                isCreated.set(true)
                val decks: DeckManager = activity.col.decks
                assertThat(id, equalTo(decks.byName(deckName)!!.getLong("id")))
            }
            createDeckDialog.createDeck(deckName)
            assertThat(isCreated.get(), equalTo(true))
        }
    }

    @Test
    fun testRenameDeckFunction() {
        val deckName = "Deck Name"
        val deckNewName = "New Deck Name"
        mActivityScenario!!.onActivity { activity: DeckPicker ->
            val createDeckDialog = CreateDeckDialog(activity, R.string.new_deck, DeckDialogType.RENAME_DECK, null)
            createDeckDialog.deckName = deckName
            val isCreated = AtomicReference(false)
            advanceRobolectricLooper()
            createDeckDialog.setOnNewDeckCreated { id: Long? ->
                // a deck name was renamed
                isCreated.set(true)
                val decks: DeckManager = activity.col.decks
                assertThat(deckNewName, equalTo(decks.name(id!!)))
            }
            createDeckDialog.renameDeck(deckNewName)
            assertThat(isCreated.get(), equalTo(true))
        }
    }

    @Test
    fun nameMayNotBeZeroLength() {
        testDialog(DeckDialogType.DECK) {
            assertThat("Ok is disabled if zero length input", positiveButton.isEnabled, equalTo(false))
            input = "NotEmpty"
            assertThat("Ok is enabled if not zero length input", positiveButton.isEnabled, equalTo(true))
            input = "A::B"
            assertThat("OK is enabled if fully qualified input provided ('A::B')", positiveButton.isEnabled, equalTo(true))
        }
    }

    @Test
    fun searchDecksIconVisibilityDeckCreationTest() = runTest {
        // await deckpicker
        val deckPicker = suspendCoroutine { coro ->
            mActivityScenario!!.onActivity { deckPicker ->
                coro.resume(deckPicker)
            }
        }

        suspend fun decksCount() = withCol { decks.count() }
        val deckCounter = AtomicInteger(1)

        for (i in 0 until 10) {
            val createDeckDialog = CreateDeckDialog(
                deckPicker,
                R.string.new_deck,
                CreateDeckDialog.DeckDialogType.DECK,
                null
            )
            val did = suspendCoroutine { coro ->
                createDeckDialog.setOnNewDeckCreated { did ->
                    coro.resume(did)
                }
                createDeckDialog.createDeck("Deck$i")
            }
            assertEquals(deckCounter.incrementAndGet(), decksCount())

            assertEquals(deckCounter.get(), decksCount())

            updateSearchDecksIcon(deckPicker)
            assertEquals(
                deckPicker.optionsMenuState?.searchIcon, decksCount() >= 10
            )

            // After the last deck was created, delete a deck
            if (decksCount() >= 10) {
                deckPicker.confirmDeckDeletion(did)
                assertEquals(deckCounter.decrementAndGet(), decksCount())

                assertEquals(deckCounter.get(), decksCount())

                updateSearchDecksIcon(deckPicker)
                assertFalse(deckPicker.optionsMenuState?.searchIcon ?: true)
            }
        }
    }

    private suspend fun updateSearchDecksIcon(deckPicker: DeckPicker) {
        // the icon update requires a call to refreshState() and subsequent menu
        // rebuild; access it directly instead so the test passes
        deckPicker.updateMenuState()
    }

    @Test
    fun searchDecksIconVisibilitySubdeckCreationTest() = runTest {
        val deckPicker =
            suspendCoroutine { coro -> mActivityScenario!!.onActivity { coro.resume(it) } }
        deckPicker.updateMenuState()
        assertEquals(deckPicker.optionsMenuState!!.searchIcon, false)
        // a single top-level deck with lots of subdecks should turn the icon on
        withCol {
            decks.id(deckTreeName(0, 10, "Deck"))
        }
        deckPicker.updateMenuState()
        assertEquals(deckPicker.optionsMenuState!!.searchIcon, true)
    }

    @Test
    fun `Duplicate decks can't be created`() {
        createDeck("deck")
        createDeck("parent::child")
        testDialog(DeckDialogType.DECK) {
            input = "deck"
            assertThat("Cannot create duplicate deck: 'deck'", positiveButton.isEnabled, equalTo(false))
            input = "Deck"
            assertThat("Cannot create duplicate deck: (case insensitive: 'Deck')", positiveButton.isEnabled, equalTo(false))
            input = "Deck2"
            assertThat("Can create deck with new name: 'Deck2'", positiveButton.isEnabled, equalTo(true))
            input = "parent::child"
            assertThat("Can't create fully qualified duplicate deck: 'parent::child'", positiveButton.isEnabled, equalTo(false))
        }
    }

    @Test
    fun `Duplicate subdecks can't be created`() {
        // Subdecks have a 'context' of the parent deck: selecting 'A' and entering 'B' creates 'A::B'
        createDeck("parent::child")
        val parentDeckId = col.decks.byName("parent")!!.getLong("id")
        testDialog(DeckDialogType.SUB_DECK, parentDeckId) {
            input = "parent"
            assertThat("'parent::parent' should be valid", positiveButton.isEnabled, equalTo(true))
            input = "child"
            assertThat("'parent::child' already exists so should be invalid", positiveButton.isEnabled, equalTo(false))
            input = "Child"
            assertThat("'parent::child' already exists so should be invalid (case insensitive)", positiveButton.isEnabled, equalTo(false))
        }
    }

    private fun createDeck(deckName: String) {
        col.decks.id(deckName)
    }

    /**
     * Executes [callback] on the [MaterialDialog] created from [CreateDeckDialog]
     */
    private fun testDialog(deckDialogType: DeckDialogType, parentId: DeckId? = null, callback: (MaterialDialog.() -> Unit)) {
        mActivityScenario!!.onActivity { activity: DeckPicker ->
            val dialog = CreateDeckDialog(activity, R.string.new_deck, deckDialogType, parentId).showDialog()
            callback(dialog)
        }
    }

    private fun deckTreeName(start: Int, end: Int, prefix: String): String {
        return List(end - start + 1) { "${prefix}${it + start}" }
            .joinToString("::")
    }
}
