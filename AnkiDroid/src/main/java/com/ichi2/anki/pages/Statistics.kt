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
import android.widget.TextView
import androidx.core.content.ContextCompat.getSystemService
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.time.getTimestamp
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.startDeckSelection
import com.ichi2.anki.withProgress

class Statistics :
    PageFragment(R.layout.statistics),
    DeckSelectionDialog.DeckSelectionListener {
    private lateinit var deckNameView: TextView

    @Suppress("deprecation", "API35 properly handle edge-to-edge")
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        webView.isNestedScrollingEnabled = true

        deckNameView = view.findViewById(R.id.deck_name)
        deckNameView.setOnClickListener { startDeckSelection(all = false, filtered = false) }
        view
            .findViewById<AppBarLayout>(R.id.app_bar)
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
        requireActivity().launchCatchingTask {
            withProgress {
                val deckName =
                    savedInstanceState?.getString(KEY_DECK_NAME, null) ?: withCol { decks.current().name }
                changeDeck(deckName)
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
            PrintAttributes.Builder().build(),
        )
    }

    override fun onDeckSelected(deck: SelectableDeck?) {
        if (deck == null) return
        require(deck is SelectableDeck.Deck)
        changeDeck(deck.name)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_DECK_NAME, deckNameView.text.toString())
    }

    /**
     * Updates the ui with the new selected deck. Doesn't change the backend.
     *
     * This method includes a workaround to change the deck in the webview by finding the text box
     * and replacing the deck name with the selected deck name from the dialog and updating the
     * stats. See issue #3394 in Anki repository.
     **/
    private fun changeDeck(selectedDeckName: String) {
        deckNameView.text = selectedDeckName
        val javascriptCode =
            """
            var textBox = document.getElementById("statisticsSearchText");
            textBox.value = "deck:\"$selectedDeckName\"";
            textBox.dispatchEvent(new Event("input", { bubbles: true }));
            textBox.dispatchEvent(new Event("change"));
            """.trimIndent()
        webView.evaluateJavascript(javascriptCode, null)
    }

    companion object {
        private const val KEY_DECK_NAME = "key_deck_name"

        /**
         * Note: the title argument is set to null as the [Statistics] fragment is expected to
         * handle the toolbar content(shows a deck selection spinner).
         */
        fun getIntent(context: Context): Intent = getIntent(context, "graphs", null, Statistics::class)
    }
}
