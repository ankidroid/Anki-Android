/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.viewmodel

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.Flag
import com.ichi2.anki.api.AddContentApi
import com.ichi2.anki.browser.CardBrowserLaunchOptions
import com.ichi2.anki.browser.CardBrowserLaunchOptions.*
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState.SearchResults
import com.ichi2.anki.browser.LastDeckIdRepository
import com.ichi2.anki.dialogs.DeckSelectionDialog.Companion.ALL_DECKS_ID
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.CardsOrNotes.*
import com.ichi2.anki.searchForCards
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.Note
import com.ichi2.testutils.OpChangesSubscriber
import com.ichi2.testutils.PureJvmTest
import com.ichi2.testutils.TestClass
import io.mockk.coVerify
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString

@RunWith(AndroidJUnit4::class)
class CardBrowserViewModelTest : PureJvmTest() {

    @Test
    fun `default init`() = runTest {
        viewModel().apply {
            assertThat(searchTerms, equalTo(""))
        }
    }

    @Test
    fun `Card Browser menu init`() = runTest {
        viewModel(intent = SystemContextMenu("Hello")).apply {
            assertThat(searchTerms, equalTo("Hello"))
        }
    }

    @Test
    fun `Deep Link init`() = runTest {
        viewModel(intent = DeepLink("Hello")).apply {
            assertThat(searchTerms, equalTo("Hello"))
        }
    }

    @Test
    fun `JS API init`() = runTest {
        viewModel(intent = SearchQueryJs("Hello", allDecks = true)).apply {
            assertThat(searchTerms, equalTo("Hello"))
            assertThat(deckId, equalTo(ALL_DECKS_ID))
        }
    }

    @Test
    fun `init only performs one search`() = runTest {
        mockkStatic(::searchForCards)

        viewModel().apply {
            // TODO: it's hard to verify the absence of additional searches
            delay(100)
        }

        coVerify(exactly = 1) { searchForCards(allAny(), allAny(), allAny()) }
    }

    @Test
    fun `init with no cards`() = runTest {
        viewModel().apply {
            waitForInit()
            // assertThat(!this.canSelectAll)
            // assertThat(!this.canSelectNone)
            TODO()
        }
    }

    @Test
    @Ignore("Not Implemented")
    fun `opening and closing search`() = runTest {
        TODO()
    }

    @Test
    fun `changing deck performs a search`() = runTest {
        val newDeckId = col.decks.id("Testing").also {
            col.decks.select(it)
        }
        val note = col.newNote(forDeck = true)
        col.addNote(note, newDeckId)
        assertThat(note.cards()[0].did, equalTo(newDeckId))

        viewModel().apply {
            invokeInitialSearch()
            assertThat("initial search", nextSearchRowCount("deck:\"Testing\" "), equalTo(1))

            setDeckId(AddContentApi.DEFAULT_DECK_ID)
            assertThat("default deck", nextSearchRowCount("deck:\"Default\" "), equalTo(0))

            setDeckId(newDeckId)
            assertThat("new deck (Testing)", nextSearchRowCount("deck:\"Testing\" "), equalTo(1))

            selectAllDecks()
            assertThat("all decks", nextSearchRowCount(""), equalTo(1))
        }
    }

    @Test
    fun `filtering by flag`() = runTest {
        addNoteUsingBasicAndReversedModel().setFlag(0, Flag.RED)

        viewModel().apply {
            invokeInitialSearch()
            assertThat("initial search", nextSearchRowCount("deck:\"Default\" "), equalTo(2))

            Timber.v("FLAG_RED")
            setSelectedFlag(Flag.RED)

            assertThat("red flag", nextSearchRowCount("deck:\"Default\" (flag:1)"), equalTo(1))
            assertThat("flagging expands search query", this.searchQueryExpanded)

            // cancel the search and display all decks
            setSearchQueryExpanded(false, numCardsToRender = 2)
            assertThat("search collapsed", nextSearchRowCount("deck:\"Default\" "), equalTo(2))

            // search for "NO_FLAG"
            setSelectedFlag(Flag.NONE)
            assertThat("no flag", nextSearchRowCount("deck:\"Default\" (flag:0)"), equalTo(1))

            // ensure that if the search is collapsed, a change in flag causes a search
            setSearchQueryExpanded(false, numCardsToRender = 2)
            assertThat("search collapsed 2", nextSearchRowCount("deck:\"Default\" "), equalTo(2))

            // Flag logic can be reapplied
            setSelectedFlag(Flag.NONE)
            assertThat("no flag (after search collapse)", nextSearchRowCount("deck:\"Default\" (flag:0)"), equalTo(1))

            setSearchQueryExpanded(false, numCardsToRender = 2)
            assertThat("search collapsed 3", nextSearchRowCount("deck:\"Default\" "), equalTo(2))

            searchTerms = "Hello"
            setSelectedFlag(Flag.NONE)
            assertThat("flag search + user input", nextSearchRowCount("deck:\"Default\" (flag:0 Hello)"), equalTo(0))
        }
    }

    @Test
    fun `filtering by flag notes mode`() = runTest {
        // from Anki Desktop: if a note has on card which is flagged, show it
        addNoteUsingBasicAndReversedModel().setFlag(index = 0, Flag.RED)
        addNoteUsingBasicAndReversedModel().setFlag(index = 1, Flag.RED)
        addNoteUsingBasicAndReversedModel()

        viewModel(mode = NOTES).apply {
            invokeInitialSearch()
            assertThat("initial search", nextSearchRowCount("deck:\"Default\" "), equalTo(3))

            setSelectedFlag(Flag.RED)
            assertThat(
                "a note matches a flag filter if one card matches",
                nextSearchRowCount("deck:\"Default\" (flag:1)"),
                equalTo(2)
            )
        }
    }

    @Test
    fun `filter by tag cards`() = runTest {
        addBasicNote().setTag("hello")
        addBasicNote()

        viewModel(mode = CARDS).apply {
            assertThat("initial search", nextSearchRowCount("deck:\"Default\" "), equalTo(2))

            this.filterByTags("hello")
            assertThat("tag filter: match note", nextSearchRowCount("deck:\"Default\" ((\"tag:hello\"))"), equalTo(1))

            this.filterByTags("unavailable")
            assertThat("tag filter: no match", nextSearchRowCount("deck:\"Default\" ((\"tag:unavailable\"))"), equalTo(0))
        }
    }

    @Test
    fun `filter by tag notes`() = runTest {
        addBasicAndReversedNote().setTag("hello")
        addBasicAndReversedNote()

        viewModel(mode = NOTES).apply {
            assertThat("initial search", nextSearchRowCount("deck:\"Default\" "), equalTo(2))

            this.filterByTags("hello")
            assertThat("tag filter: match note", nextSearchRowCount("deck:\"Default\" ((\"tag:hello\"))"), equalTo(1))

            this.filterByTags("unavailable")
            assertThat("tag filter: no match", nextSearchRowCount("deck:\"Default\" ((\"tag:unavailable\"))"), equalTo(0))
        }
    }

    @Test
    fun `edit tag selection cards`() = runTest {
        val n1 = addBasicNote().setTag("hello")
        val n2 = addBasicNote()

        val changes = OpChangesSubscriber.createAndSubscribe()

        viewModel().apply {
            assertThat("initial search", nextSearchRowCount("deck:\"Default\" "), equalTo(2))

            selectAll()

            assertThat("no changes before", changes.changes, empty())

            this.editSelectedCardsTags(
                selectedTags = listOf("hi"),
                indeterminateTags = listOf("hello")
            )

            assertThat("changes detected after", changes.changes.size, equalTo(1))

            assertThat(n1.printTags(), equalTo("hello, hi"))
            assertThat(n2.printTags(), equalTo("hi"))

            assertThat("selection remains", selectedRowCount(), equalTo(2))
        }
    }

    @Test
    fun `edit tag selection notes`() = runTest {
        val n1 = addBasicAndReversedNote().setTag("hello")
        val n2 = addBasicAndReversedNote()

        val changes = OpChangesSubscriber.createAndSubscribe()

        viewModel(mode = NOTES).apply {
            assertThat("initial search", nextSearchRowCount("deck:\"Default\" "), equalTo(2))

            selectAll()

            assertThat("no changes before", changes.changes, empty())

            this.editSelectedCardsTags(
                selectedTags = listOf("hi"),
                indeterminateTags = listOf("hello")
            )

            assertThat("changes detected after", changes.changes.size, equalTo(1))

            assertThat(n1.printTags(), equalTo("hello, hi"))
            assertThat(n2.printTags(), equalTo("hi"))

            assertThat("selection remains", selectedRowCount(), equalTo(2))
        }
    }
//
//    @Test
//    fun `marked selection cards`() = runTest {
//        val n1 = addBasicAndReversedNote()
//        val n2 = addBasicAndReversedNote().mark()
//
//        val changes = OpChangesSubscriber.createAndSubscribe()
//
//        viewModel(mode = CARDS).apply {
//            assertThat("initial search", nextSearchRowCount("deck:\"Default\" "), equalTo(4))
//
//            val unmarkedInitially = getCardById(n1.cards()[0].id)
//            toggleRow(unmarkedInitially)
//            selectAll()
//            toggleMark()
//
//            // marking a mixed selection should
//            assertThat("an operation is executed after marking a card", changes.size, equalTo(1))
//            assert(n1.marked)
//            assert(n2.marked)
//
//            // now remove the mark for all
//            toggleMark()
//            assert(!n1.marked)
//            assert(!n2.marked)
//
//            // add the mark from one note using 1 card
//            selectNone()
//            val idToSelect = n1.cards()[0].id
//            val idToVerify = n1.cards()[1].id
//
//            // select one of two cards
//            toggleRow(getCardById(idToSelect))
//
//            // enable the mark
//            toggleMark()
//
//            // check the other card is marked
//            val toVerify = getCardById(idToVerify)
//            toVerify.getBackgroundColor(Test)
//            assertThat("a linked card is marked after a sibling is marked", toVerify.color, equalTo(R.attr.markedColor))
//        }
//    }

    @Test
    @Ignore("Not implemented")
    fun `marked selection notes`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `add from search`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `change column one`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `normal display order`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `reverse display order`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `truncate content`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `search open options`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `one card selected options`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `preview cards`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `preview notes`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `set flag cards`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `set flag notes`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `edit note notes`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `edit note cards`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `edit note multiple selection`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `delete note cards`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `delete note notes`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `card info notes`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `card info cards`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `card info multiselect`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `change deck notes`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `reposition cards`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `reposition notes`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `reschedule cards`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `reset progress cards`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `reset progress notes`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `select all`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `select none`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `save search`() = runTest {
        TODO()
    }

    @Test
    @Ignore("Not implemented")
    fun `load saved search`() = runTest {
        TODO()
    }

    companion object {
        private suspend fun viewModel(
            lastDeckId: DeckId? = null,
            intent: CardBrowserLaunchOptions? = null,
            mode: CardsOrNotes = CARDS
        ): CardBrowserViewModel {
            val lastDeckIdRepository = object : LastDeckIdRepository {
                override var lastDeckId: DeckId? = lastDeckId
            }

            // default is CARDS, do nothing in this case
            if (mode == NOTES) {
                CollectionManager.withCol { mode.saveToCollection() }
            }

            val cache = File(createTempDirectory().pathString)
            return CardBrowserViewModel(lastDeckIdRepository, cache, intent, AnkiDroidApp.sharedPreferencesProvider).apply {
                invokeInitialSearch()
            }
        }
    }

    private fun addBasicNote() = this.addNoteUsingBasicModel("Front", "Back")
    private fun addBasicAndReversedNote() = this.addNoteUsingBasicAndReversedModel()
}

private suspend fun CardBrowserViewModel.getCardById(idToSelect: Long) =
    cardFlow.first().all().single { it.id == idToSelect }

context (TestClass)
private fun Note.mark(): Note {
    if (!this.hasTag(col, "marked")) {
        return setTag("marked")
    }
    return this
}

context (TestClass)
private fun Note.setTag(tag: String): Note {
    addTag(tag)
    col.updateNote(this)
    return this
}

context (TestClass)
@SuppressLint("CheckResult")
private fun Note.setFlag(index: Int, flag: Flag): Note {
    col.setUserFlagForCards(listOf(this.cards()[index].id), flag.code)
    return this
}

private suspend fun CardBrowserViewModel.waitForInit() {
    this.flowOfInitCompleted.first { initCompleted -> initCompleted }
}

internal suspend fun CardBrowserViewModel.invokeInitialSearch() {
    Timber.d("waiting for init")
    waitForInit()
    Timber.d("init completed")
    // For legacy reasons, we need to know the number of cards to render when performing a search
    // This will be removed once we handle #11889
    // numberOfCardsToRenderFlow.emit(1)
    Timber.v("initial search completed")
}

suspend fun CardBrowserViewModel.nextSearchRowCount(search: String? = null): Int = withContext(Dispatchers.IO) {
    Timber.v("waiting for search '$search'")
    // TODO: If this is cancelled, display where it failed
    this@nextSearchRowCount.cardFlow.first {
        if (it !is SearchResults) return@first false
        if (search != null) Timber.v("found search: %s", it.query)
        return@first (search == null || it.query == search)
    }.size()
}

context (TestClass)
fun Note.printTags() = Note(col, this.id).tags.joinToString(", ")

context (TestClass)
private val Note.marked get() = Note(col, this.id).hasTag(col, "marked")

private fun CardBrowserViewModel.filterByTags(vararg tags: String): Job =
    this.filterByTags(tags.toList(), CardStateFilter.ALL_CARDS)
