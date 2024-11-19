/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import android.widget.AdapterView.INVALID_POSITION
import android.widget.Spinner
import androidx.core.content.ContextCompat.getSystemService
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.DeckSpinnerSelection
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.requireAnkiActivity
import com.ichi2.anki.utils.getTimestamp
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.DeckNameId
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.themes.setTransparentStatusBar
import com.ichi2.utils.BundleUtils.getNullableLong

class Statistics :
    PageFragment(R.layout.statistics),
    DeckSelectionDialog.DeckSelectionListener {

    private lateinit var deckSpinnerSelection: DeckSpinnerSelection
    private lateinit var spinner: Spinner

    @Suppress("deprecation", "API35 properly handle edge-to-edge")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        webView.isNestedScrollingEnabled = true

        requireActivity().setTransparentStatusBar()
        spinner = view.findViewById(R.id.deck_selector)
        view.findViewById<AppBarLayout>(R.id.app_bar)
            .addLiftOnScrollListener { _, backgroundColor ->
                activity?.window?.statusBarColor = backgroundColor
            }

        view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
            menu.findItem(R.id.action_export_stats).title = CollectionManager.TR.statisticsSavePdf()
            setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_export_stats) {
                    exportWebViewContentAsPDF()
                }
                true
            }
        }
        deckSpinnerSelection = DeckSpinnerSelection(
            requireAnkiActivity(),
            spinner,
            showAllDecks = false,
            alwaysShowDefault = false,
            showFilteredDecks = false
        )
        if (savedInstanceState == null) {
            requireActivity().launchCatchingTask {
                deckSpinnerSelection.initializeStatsBarDeckSpinner()
                val selectedDeck = withCol { decks.get(decks.selected()) }
                if (selectedDeck == null) return@launchCatchingTask
                select(selectedDeck.id)
                changeDeck(selectedDeck.name)
            }
        } else {
            val savedDeckId = savedInstanceState.getNullableLong(KEY_DECK_ID) ?: return
            requireActivity().launchCatchingTask {
                deckSpinnerSelection.initializeStatsBarDeckSpinner()
                select(savedDeckId)
                savedInstanceState.getString(KEY_DECK_NAME)?.let { changeDeck(it) }
            }
        }
    }

    /** Prepares and initiates a printing task for the content(stats) displayed in the WebView.
     * It uses the Android PrintManager service to create a print job, based on the content of the WebView.
     * The resulting output is a PDF document. **/
    private fun exportWebViewContentAsPDF() {
        val printManager = getSystemService(requireContext(), PrintManager::class.java)
        val currentDateTime = getTimestamp(TimeManager.time)
        val jobName = "${getString(R.string.app_name)}-stats-$currentDateTime"
        val printAdapter = webView.createPrintDocumentAdapter(jobName)
        printManager?.print(
            jobName,
            printAdapter,
            PrintAttributes.Builder().build()
        )
    }

    override fun onDeckSelected(deck: DeckSelectionDialog.SelectableDeck?) {
        if (deck == null) return
        select(deck.deckId)
        changeDeck(deck.name)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val selectedPosition = spinner.selectedItemPosition
        if (selectedPosition != INVALID_POSITION) {
            val selectedDeck = spinner.adapter.getItem(selectedPosition) as DeckNameId
            outState.putLong(KEY_DECK_ID, selectedDeck.id)
            outState.putString(KEY_DECK_NAME, selectedDeck.name)
        }
    }

    private val decksAdapterSequence
        get() = sequence {
            for (i in 0 until spinner.adapter.count) {
                yield(spinner.adapter.getItem(i) as DeckNameId)
            }
        }

    /**
     * Given the [deckId] look in the decks adapter for its position and select it if found.
     */
    private fun select(deckId: DeckId) {
        val itemToSelect = decksAdapterSequence.withIndex().firstOrNull { it.value.id == deckId } ?: return
        spinner.setSelection(itemToSelect.index)
    }

    /**
     * This method is a workaround to change the deck in the webview by finding the text box and
     * replacing the deck name with the selected deck name from the dialog and updating the stats
     *
     * See issue #3394 in the Anki repository
     **/
    private fun changeDeck(selectedDeckName: String) {
        val javascriptCode = """
        var textBox = document.getElementById("statisticsSearchText");
        textBox.value = "deck:\"$selectedDeckName\"";
        textBox.dispatchEvent(new Event("input", { bubbles: true }));
        textBox.dispatchEvent(new Event("change"));
        """.trimIndent()
        webView.evaluateJavascript(javascriptCode, null)
    }

    companion object {
        private const val KEY_DECK_ID = "key_deck_id"
        private const val KEY_DECK_NAME = "key_deck_name"

        /**
         * Note: the title argument is set to null as the [Statistics] fragment is expected to
         * handle the toolbar content(shows a deck selection spinner).
         */
        fun getIntent(context: Context): Intent {
            return getIntent(context, "graphs", null, Statistics::class)
        }
    }
}
