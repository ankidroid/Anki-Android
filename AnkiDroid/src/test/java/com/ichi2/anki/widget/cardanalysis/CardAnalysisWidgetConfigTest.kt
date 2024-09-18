/*
 *  Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>
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

package com.ichi2.anki.widget.cardanalysis

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.widget.cardanalysis.CardAnalysisWidgetConfig
import com.ichi2.widget.cardanalysis.CardAnalysisWidgetPreferences
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardAnalysisWidgetConfigTest : RobolectricTest() {

    private lateinit var activity: CardAnalysisWidgetConfig
    private val widgetPreferences = CardAnalysisWidgetPreferences(targetContext)

    /**
     * Sets up the test environment before each test.
     *
     * Initializes the `CardAnalysisWidgetConfig` activity and associated components like
     * `WidgetPreferences`. This setup is executed before each test method.
     */
    @Before
    override fun setUp() {
        super.setUp()
        ensureNonEmptyCollection()

        val intent = Intent(targetContext, CardAnalysisWidgetConfig::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 1)
        }

        activity = startActivityNormallyOpenCollectionWithIntent(CardAnalysisWidgetConfig::class.java, intent)

        // Ensure deckAdapter is initialized
        runBlocking { activity.initTask.join() }
    }

    /**
     * Tests the functionality of saving selected decks to preferences.
     *
     * This test adds a deck to the adapter and verifies if it gets correctly saved to the
     * `WidgetPreferences`.
     */
    @Test
    fun testSaveSelectedDecksToPreferences() {
        // Add decks to adapter
        val deck1 = DeckSelectionDialog.SelectableDeck(1, "Deck 1")
        activity.deckAdapter.addDeck(deck1)

        // Save selected decks
        activity.saveSelectedDecksToPreferencesCardAnalysisWidget()

        // Verify saved decks
        val selectedDeckId = widgetPreferences.getSelectedDeckIdFromPreferences(1)
        assertThat(selectedDeckId, equalTo(deck1.deckId))
    }

    /**
     * Tests the loading of saved preferences into the activity's view.
     *
     * This test saves decks to preferences, then loads them into the activity and checks if the
     * `RecyclerView` displays the correct number of items based on the saved preferences.
     */
    @Test
    fun testLoadSavedPreferences() = runTest {
        // Save decks to preferences
        val deckId = 1L
        widgetPreferences.saveSelectedDeck(1, deckId)

        // Load preferences
        activity.updateViewWithSavedPreferences()

        // Get the RecyclerView and its adapter
        val recyclerView = activity.findViewById<RecyclerView>(R.id.recyclerViewSelectedDecks)
        val adapter = recyclerView.adapter

        // Verify the adapter has the correct item count
        assertThat(adapter?.itemCount, equalTo(1))
    }

    /**
     * Tests the visibility of different views based on the selected decks.
     *
     * This test checks the visibility of the placeholder and configuration container views
     * before and after adding a deck.
     */
    @Test
    fun testUpdateViewVisibility() {
        val noDecksPlaceholder = activity.findViewById<View>(R.id.no_decks_placeholder)
        val widgetConfigContainer = activity.findViewById<View>(R.id.widgetConfigContainer)

        // Initially, no decks should be selected
        activity.updateViewVisibility()
        assertThat(noDecksPlaceholder.visibility, equalTo(View.VISIBLE))
        assertThat(widgetConfigContainer.visibility, equalTo(View.GONE))

        // Add a deck and update view visibility
        val deck = DeckSelectionDialog.SelectableDeck(1, "Deck 1")
        activity.deckAdapter.addDeck(deck)
        activity.updateViewVisibility()

        assertThat(noDecksPlaceholder.visibility, equalTo(View.GONE))
        assertThat(widgetConfigContainer.visibility, equalTo(View.VISIBLE))
    }
}
