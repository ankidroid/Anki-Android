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
import com.ichi2.libanki.backend.exception.DeckRenameException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.*
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
    private lateinit var activityScenario: ActivityScenario<DeckPicker>
    override fun setUp() {
        super.setUp()
        getPreferences().edit { putBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, true) }
        ensureCollectionLoadIsSynchronous()
        activityScenario = ActivityScenario.launch(DeckPicker::class.java).apply {
            moveToState(Lifecycle.State.STARTED)
        }
    }

    @Test
    fun testCreateFilteredDeckFunction() {
        val deckName = "filteredDeck"
        ensureExecutionOfScenario(DeckDialogType.FILTERED_DECK) { createDeckDialog, assertionCalled ->
            createDeckDialog.setOnNewDeckCreated { id: Long ->
                // a deck was created
                assertThat(id, equalTo(col.decks.id(col, deckName)))
                assertionCalled()
            }
            createDeckDialog.createFilteredDeck(deckName)
        }
    }

    @Test
    @Throws(DeckRenameException::class)
    fun testCreateSubDeckFunction() {
        val deckParentId = col.decks.id(col, "Deck Name")
        val deckName = "filteredDeck"
        ensureExecutionOfScenario(DeckDialogType.SUB_DECK, deckParentId) { createDeckDialog, assertionCalled ->
            createDeckDialog.setOnNewDeckCreated { id: Long ->
                val deckNameWithParentName = col.decks.getSubdeckName(col, deckParentId, deckName)
                assertThat(id, equalTo(col.decks.id(col, deckNameWithParentName!!)))
                assertionCalled()
            }
            createDeckDialog.createSubDeck(deckParentId, deckName)
        }
    }

    @Test
    fun testCreateDeckFunction() {
        val deckName = "Deck Name"
        ensureExecutionOfScenario(DeckDialogType.DECK) { createDeckDialog, assertionCalled ->
            createDeckDialog.setOnNewDeckCreated { id: Long ->
                // a deck was created
                assertThat(id, equalTo(col.decks.byName(col, deckName)!!.getLong("id")))
                assertionCalled()
            }
            createDeckDialog.createDeck(deckName)
        }
    }

    @Test
    fun testRenameDeckFunction() {
        val deckName = "Deck Name"
        val deckNewName = "New Deck Name"
        ensureExecutionOfScenario(DeckDialogType.RENAME_DECK) { createDeckDialog, assertionCalled ->
            createDeckDialog.deckName = deckName
            createDeckDialog.setOnNewDeckCreated { id: Long? ->
                // a deck name was renamed
                assertThat(deckNewName, equalTo(col.decks.name(col, id!!)))
                assertionCalled()
            }
            createDeckDialog.renameDeck(deckNewName)
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
            activityScenario.onActivity { deckPicker ->
                coro.resume(deckPicker)
            }
        }

        suspend fun decksCount() = withCol { decks.count(col) }
        val deckCounter = AtomicInteger(1)

        for (i in 0 until 10) {
            val createDeckDialog = CreateDeckDialog(
                deckPicker,
                R.string.new_deck,
                DeckDialogType.DECK,
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
                deckPicker.optionsMenuState?.searchIcon,
                decksCount() >= 10
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
            suspendCoroutine { coro -> activityScenario.onActivity { coro.resume(it) } }
        deckPicker.updateMenuState()
        assertEquals(deckPicker.optionsMenuState!!.searchIcon, false)
        // a single top-level deck with lots of subdecks should turn the icon on
        withCol {
            decks.id(col, deckTreeName(0, 10, "Deck"))
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
        val parentDeckId = col.decks.byName(col, "parent")!!.getLong("id")
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
        col.decks.id(col, deckName)
    }

    /**
     * Executes [callback] on the [MaterialDialog] created from [CreateDeckDialog]
     */
    private fun testDialog(deckDialogType: DeckDialogType, parentId: DeckId? = null, callback: (MaterialDialog.() -> Unit)) {
        activityScenario.onActivity { activity: DeckPicker ->
            val dialog = CreateDeckDialog(activity, R.string.new_deck, deckDialogType, parentId).showDialog()
            callback(dialog)
        }
    }

    /**
     * Tests a scenario with a [DeckPicker] hosting a [CreateDeckDialog].
     * The second parameter of the callback ('assertionCalled') must be called for this to pass
     */
    private fun ensureExecutionOfScenario(deckDialogType: DeckDialogType, parentId: DeckId? = null, callback: ((CreateDeckDialog, (() -> Unit)) -> Unit)) {
        activityScenario.onActivity { activity: DeckPicker ->
            val assertionCalled = AtomicReference(false)
            callback(CreateDeckDialog(activity, R.string.new_deck, deckDialogType, parentId)) {
                assertionCalled.set(true)
            }
            assertThat("no call to assertionCalled()", assertionCalled.get(), equalTo(true))
        }
    }

    @Suppress("SameParameterValue")
    private fun deckTreeName(start: Int, end: Int, prefix: String): String {
        return List(end - start + 1) { "${prefix}${it + start}" }
            .joinToString("::")
    }
}
