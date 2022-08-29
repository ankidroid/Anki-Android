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
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.getActionButton
import com.afollestad.materialdialogs.input.getInputField
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.IntroductionActivity
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.libanki.DeckManager
import com.ichi2.libanki.backend.exception.DeckRenameException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.*
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
            val createDeckDialog = CreateDeckDialog(activity, R.string.new_deck, CreateDeckDialog.DeckDialogType.FILTERED_DECK, null)
            val isCreated = AtomicReference(false)
            val deckName = "filteredDeck"
            advanceRobolectricLooper()
            createDeckDialog.setOnNewDeckCreated { id: Long ->
                // a deck was created
                try {
                    isCreated.set(true)
                    val decks: DeckManager = activity.col.decks
                    MatcherAssert.assertThat(id, equalTo(decks.id(deckName)))
                } catch (filteredAncestor: DeckRenameException) {
                    throw RuntimeException(filteredAncestor)
                }
            }
            createDeckDialog.createFilteredDeck(deckName)
            MatcherAssert.assertThat(isCreated.get() as Boolean, equalTo(true))
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
                    val decks: DeckManager = activity.col.decks
                    val deckNameWithParentName = decks.getSubdeckName(deckParentId, deckName)
                    MatcherAssert.assertThat(id, equalTo(decks.id(deckNameWithParentName!!)))
                } catch (filteredAncestor: DeckRenameException) {
                    throw RuntimeException(filteredAncestor)
                }
            }
            createDeckDialog.createSubDeck(deckParentId, deckName)
            MatcherAssert.assertThat(isCreated.get(), equalTo(true))
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
                val decks: DeckManager = activity.col.decks
                MatcherAssert.assertThat(id, equalTo(decks.byName(deckName)!!.getLong("id")))
            }
            createDeckDialog.createDeck(deckName)
            MatcherAssert.assertThat(isCreated.get(), equalTo(true))
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
                val decks: DeckManager = activity.col.decks
                MatcherAssert.assertThat(deckNewName, equalTo(decks.name(id!!)))
            }
            createDeckDialog.renameDeck(deckNewName)
            MatcherAssert.assertThat(isCreated.get(), equalTo(true))
        }
    }

    @Test
    fun nameMayNotBeZeroLength() {
        mActivityScenario!!.onActivity { activity: DeckPicker? ->
            val createDeckDialog = CreateDeckDialog(activity!!, R.string.new_deck, CreateDeckDialog.DeckDialogType.DECK, null)
            val materialDialog = createDeckDialog.showDialog()
            val actionButton = materialDialog.getActionButton(WhichButton.POSITIVE)
            MatcherAssert.assertThat("Ok is disabled if zero length input", actionButton.isEnabled, equalTo(false))
            val editText: EditText? = Objects.requireNonNull(materialDialog.getInputField())
            editText?.setText("NotEmpty")
            MatcherAssert.assertThat("Ok is enabled if not zero length input", actionButton.isEnabled, equalTo(true))
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

    private fun deckTreeName(start: Int, end: Int, prefix: String): String {
        return List(end - start + 1) { "${prefix}${it + start}" }
            .joinToString("::")
    }
}
