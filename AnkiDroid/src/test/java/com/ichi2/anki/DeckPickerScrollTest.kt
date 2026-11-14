// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.recyclerview.widget.LinearLayoutManager
import anki.decks.SetDeckCollapsedRequest
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.anki.libanki.DeckId
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.idleMainLooper
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@RunWith(ParameterizedRobolectricTestRunner::class)
class DeckPickerScrollTest(
    private val qualifiers: String,
    private val collapsed: Boolean,
) : RobolectricTest() {
    private lateinit var deckIds: List<DeckId>

    @Before
    fun prepareDecks() {
        RuntimeEnvironment.setQualifiers(qualifiers)
        // Leave enough rows below the parent to avoid hitting the end of the list on tablets.
        deckIds = (0 until 60).map { addDeck("Deck ${it.toString().padStart(2, '0')}") }
        val parentId = deckIds[30]
        // Keep the collapsed parent's visible row unchanged when another child is added.
        if (collapsed) addDeck("Deck 30::Existing child")
        col.decks.select(parentId)
        col.decks.setCollapsed(parentId, collapsed, SetDeckCollapsedRequest.Scope.REVIEWER)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
    }

    @Test
    fun `focusing a subdeck before it is loaded preserves the scroll position`() =
        deckPicker(expectedDeckIds = deckIds) {
            val before = scrollTo(position = 28)

            val childId = selectNewSubdeckBeforeRefresh()
            assertEquals(before, scrollPosition, "Focus must not scroll the old list")

            refreshDecks(childId)
            assertEquals(before, scrollPosition, "Loading the new subdeck must preserve the scroll position")
        }

    @Test
    fun `a later refresh does not restore focus after the user scrolls away`() =
        deckPicker(expectedDeckIds = deckIds) {
            scrollTo(position = 28)
            val childId = selectNewSubdeckBeforeRefresh()
            refreshDecks(childId)

            val afterUserScroll = scrollTo(position = 10)
            refreshDecks(childId)

            assertEquals(afterUserScroll, scrollPosition, "A later refresh must not apply stale focus")
        }

    @Test
    fun `revealing the hidden current Default deck does not apply startup focus`() {
        col.decks.select(DEFAULT_DECK_ID)
        deckPicker(expectedDeckIds = deckIds) {
            assertEquals(DEFAULT_DECK_ID, viewModel.focusedDeck)
            assertNull(viewModel.dueTree?.find(DEFAULT_DECK_ID), "The empty Default deck must be hidden")
            settleScrolling()
            val before = scrollTo(position = 28)

            addBasicNote().moveToDeck("Default")
            viewModel.reloadDeckCounts().join()
            awaitVisibleDecks(deckIds + DEFAULT_DECK_ID)
            settleScrolling()

            assertEquals(before, scrollPosition, "Revealing Default must not apply its old focus")
        }
    }

    @Test
    fun `closing search does not focus a deck that became current while hidden`() {
        val otherId = addDeck("Other")
        deckPicker(expectedDeckIds = deckIds + otherId) {
            viewModel.updateDeckFilter("Deck")
            awaitVisibleDecks(deckIds)

            // A sync or another screen can select a deck hidden by the current search.
            col.decks.select(otherId)
            viewModel.reloadDeckCounts().join()
            awaitVisibleDecks(deckIds)
            settleScrolling()
            val before = scrollTo(position = 28)

            viewModel.updateDeckFilter("")
            awaitVisibleDecks(deckIds + otherId)
            settleScrolling()

            assertEquals(before, scrollPosition, "Closing search must not apply focus from the previous query")
        }
    }

    @Test
    fun `a listed deck becoming current is focused after rows are inserted above it`() =
        deckPicker(expectedDeckIds = deckIds) {
            scrollTo(position = 28)

            // A sync can change both the current deck and the rows above it in one refresh.
            col.decks.select(deckIds[31])
            val insertedIds = ('a'..'z').map { addDeck("Deck 30$it") }
            viewModel.reloadDeckCounts().join()
            awaitVisibleDecks(deckIds.take(31) + insertedIds + deckIds.drop(31))
            settleScrolling()

            val position = 31 + insertedIds.size
            val visible = layoutManager.findFirstCompletelyVisibleItemPosition()..layoutManager.findLastCompletelyVisibleItemPosition()
            assertTrue(position in visible, "The current deck at $position must be visible within $visible")
        }

    @Test
    fun `a new subdeck below the viewport receives focus after selection`() = assertNewSubdeckReceivesFocus(selectBeforeReload = true)

    @Test
    fun `a new subdeck below the viewport receives focus after an external selection`() =
        assertNewSubdeckReceivesFocus(selectBeforeReload = false)

    @Test
    fun `a new deck inserted just above the viewport receives focus`() =
        assertNewDeckAboveViewportReceivesFocus(name = "Deck 27a", position = 28)

    @Test
    fun `a new first deck receives focus at the top of the list`() = assertNewDeckAboveViewportReceivesFocus(name = "Deck 0", position = 0)

    private fun assertNewDeckAboveViewportReceivesFocus(
        name: String,
        position: Int,
    ) = deckPicker(expectedDeckIds = deckIds) {
        scrollTo(position)

        val newDeckId = addDeck(name)
        viewModel.selectDeck(newDeckId).join()
        settleScrolling()
        viewModel.reloadDeckCounts().join()
        awaitVisibleDecks(deckIds.toMutableList().apply { add(position, newDeckId) })
        settleScrolling()

        val visible = layoutManager.findFirstCompletelyVisibleItemPosition()..layoutManager.findLastCompletelyVisibleItemPosition()
        assertTrue(position in visible, "The inserted deck at $position must be visible within $visible")
    }

    private fun assertNewSubdeckReceivesFocus(selectBeforeReload: Boolean) {
        val children = (0 until 20).map { addDeck("Deck 30::Child ${it.toString().padStart(2, '0')}") }
        val initialDecks = deckIds.toMutableList().apply { if (!collapsed) addAll(31, children) }
        deckPicker(expectedDeckIds = initialDecks) {
            scrollTo(position = 28)

            val childId = addDeck("Deck 30::Last child", setAsSelected = !selectBeforeReload)
            if (selectBeforeReload) {
                viewModel.selectDeck(childId).join()
                settleScrolling()
            }
            viewModel.reloadDeckCounts().join()
            awaitVisibleDecks(initialDecks.toMutableList().apply { if (!collapsed) add(51, childId) })
            settleScrolling()

            val targetPosition = if (collapsed) 30 else 51
            val visible = layoutManager.findFirstCompletelyVisibleItemPosition()..layoutManager.findLastCompletelyVisibleItemPosition()
            assertTrue(targetPosition in visible, "The focused deck at $targetPosition must be visible within $visible")
        }
    }

    private suspend fun DeckPicker.selectNewSubdeckBeforeRefresh(): DeckId {
        val childId = addDeck("Deck 30::Child")
        viewModel.selectDeck(childId).join()
        // Reproduce creation's focus-before-reload ordering without blocking a backend thread.
        settleScrolling()
        assertNull(viewModel.dueTree?.find(childId), "The new subdeck must not be in the loaded tree yet")
        return childId
    }

    private suspend fun DeckPicker.refreshDecks(childId: DeckId) {
        viewModel.reloadDeckCounts().join()
        val expectedDecks = deckIds.toMutableList().apply { if (!collapsed) add(31, childId) }
        awaitVisibleDecks(expectedDecks)
        settleScrolling()
    }

    private fun settleScrolling() {
        // Focus posts a delayed scroll, which then schedules a layout on the next frame.
        idleMainLooper(100.milliseconds)
    }

    private val DeckPicker.layoutManager: LinearLayoutManager
        get() = deckPickerBinding.decks.layoutManager as LinearLayoutManager

    private val DeckPicker.scrollPosition: ScrollPosition
        get() {
            val position = layoutManager.findFirstVisibleItemPosition()
            val view = assertNotNull(layoutManager.findViewByPosition(position), "The first visible deck must be laid out")
            return ScrollPosition(position, view.top)
        }

    private fun DeckPicker.scrollTo(position: Int): ScrollPosition {
        // A partial row checks that refreshes preserve the pixel offset as well as the row.
        layoutManager.scrollToPositionWithOffset(position, -12)
        advanceRobolectricLooper()
        return scrollPosition.also { assertEquals(position, it.position) }
    }

    private data class ScrollPosition(
        val position: Int,
        val top: Int,
    )

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}, collapsed={1}")
        @JvmStatic
        fun parameters(): List<Array<Any>> =
            listOf("normal", "xlarge").flatMap { qualifiers ->
                listOf(false, true).map { collapsed -> arrayOf(qualifiers, collapsed) }
            }
    }
}
